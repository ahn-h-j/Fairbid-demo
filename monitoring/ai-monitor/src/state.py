"""SQLite 기반 알람 상태 관리.

목적:
- 활성 알람 추적: 어떤 메트릭이 현재 위반 상태인지 + 마지막 발송 시각/값
- severity별 cooldown: 같은 알람 재발송 빈도 제어
- 악화 감지: 직전 발송값 대비 변화량 비교
- 해소 감지: 이전엔 위반이었는데 이번엔 정상이 된 메트릭 식별
- 일일 카운터: 일일 요약 리포트용 anomaly_count 집계
"""
from __future__ import annotations

import sqlite3
import time
from dataclasses import dataclass
from datetime import date
from pathlib import Path


@dataclass
class ActiveAlert:
    """현재 위반 상태인 메트릭의 마지막 알람 정보."""

    rule_key: str
    fired_at: int       # unix timestamp
    last_value: float   # 마지막으로 발송된 시점의 값 (악화 비교용)
    severity: str


class StateStore:
    def __init__(self, db_path: str):
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        # check_same_thread=False: 비서 Bot의 question_handler가 asyncio.to_thread로
        # 별도 스레드에서 store를 호출하기 때문. WAL 모드라 동시 read는 안전하고,
        # write는 짧은 트랜잭션뿐이라 충돌 위험 낮음.
        self.conn = sqlite3.connect(db_path, check_same_thread=False)
        self.conn.execute("PRAGMA journal_mode=WAL")
        self._init_schema()

    def _init_schema(self) -> None:
        # 구버전 테이블 제거 (단순한 dev 마이그레이션)
        self.conn.executescript(
            """
            DROP TABLE IF EXISTS cooldowns;
            DROP TABLE IF EXISTS daily_stats;

            CREATE TABLE IF NOT EXISTS active_alerts (
                rule_key   TEXT PRIMARY KEY,
                fired_at   INTEGER NOT NULL,
                last_value REAL NOT NULL,
                severity   TEXT NOT NULL
            );
            -- 야간 브리핑 발송 여부 (date 기준, 당일 09:00 발송 시점)
            CREATE TABLE IF NOT EXISTS night_reports (
                report_date TEXT PRIMARY KEY,  -- YYYY-MM-DD (발송 당일)
                sent_at     INTEGER NOT NULL
            );
            -- 주간 evolver + 비서 Bot이 읽어가는 전체 발송 이력 (4가지 kind)
            -- discord_message_id / discord_thread_id: Bot이 발송한 메시지 및 그 아래
            -- 자동 생성한 스레드의 ID. 비서가 스레드에서 질문받을 때 thread_id로
            -- 역조회하여 해당 알람 컨텍스트 특정.
            CREATE TABLE IF NOT EXISTS alert_history (
                id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_key             TEXT NOT NULL,
                severity             TEXT NOT NULL,
                kind                 TEXT NOT NULL,
                value                REAL,
                threshold            REAL,
                fired_at             INTEGER NOT NULL,
                discord_message_id   TEXT,
                discord_thread_id    TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_history_time ON alert_history(fired_at);
            CREATE INDEX IF NOT EXISTS idx_history_rule ON alert_history(rule_key);
            CREATE INDEX IF NOT EXISTS idx_history_thread ON alert_history(discord_thread_id);
            -- Claude가 생성한 분석 리포트 (신규/악화 알람에만 기록)
            -- 비서(향후 Discord Bot)가 "그때 왜 튀었는지"를 물을 때
            -- 같은 분석을 재추론하지 않고 이 테이블에서 꺼내 쓴다.
            CREATE TABLE IF NOT EXISTS alert_reports (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                history_id       INTEGER NOT NULL,
                summary          TEXT,
                diagnosis        TEXT,
                causal_chain     TEXT,       -- JSON array
                evidence         TEXT,       -- JSON array
                suggestions      TEXT,       -- JSON array
                related_metrics  TEXT,       -- JSON object
                created_at       INTEGER NOT NULL,
                FOREIGN KEY (history_id) REFERENCES alert_history(id)
            );
            CREATE INDEX IF NOT EXISTS idx_report_history ON alert_reports(history_id);
            -- 주간 evolver 중복 발송 방지용
            CREATE TABLE IF NOT EXISTS evolve_runs (
                run_date TEXT PRIMARY KEY,
                sent_at  INTEGER NOT NULL
            );
            """
        )
        self.conn.commit()

    # === 활성 알람 ===

    def list_active(self) -> dict[str, ActiveAlert]:
        """현재 활성 알람 전체를 dict로 반환."""
        cur = self.conn.execute(
            "SELECT rule_key, fired_at, last_value, severity FROM active_alerts"
        )
        return {
            row[0]: ActiveAlert(
                rule_key=row[0], fired_at=row[1], last_value=row[2], severity=row[3]
            )
            for row in cur.fetchall()
        }

    def record_alert(self, rule_key: str, value: float, severity: str) -> None:
        """알람을 발송했음을 기록 (신규/재발송 모두). fired_at은 현재 시각으로 갱신."""
        self.conn.execute(
            """
            INSERT INTO active_alerts(rule_key, fired_at, last_value, severity)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(rule_key) DO UPDATE SET
                fired_at = excluded.fired_at,
                last_value = excluded.last_value,
                severity = excluded.severity
            """,
            (rule_key, int(time.time()), value, severity),
        )
        self.conn.commit()

    def clear_alert(self, rule_key: str) -> None:
        """위반 해소 시 활성 알람 목록에서 제거."""
        self.conn.execute("DELETE FROM active_alerts WHERE rule_key = ?", (rule_key,))
        self.conn.commit()

    # === 알람 이력 (주간 evolver용) ===

    def record_history(
        self,
        rule_key: str,
        severity: str,
        kind: str,
        value: float | None,
        threshold: float | None,
    ) -> int:
        """모든 알람 발송(신규/악화/지속/해소)을 이력에 기록.

        kind: new | escalation | persistence | recovery

        Returns:
            새로 삽입된 row의 id
            (record_report FK + attach_discord_ids 용)
        """
        cur = self.conn.execute(
            """
            INSERT INTO alert_history(rule_key, severity, kind, value, threshold, fired_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (rule_key, severity, kind, value, threshold, int(time.time())),
        )
        self.conn.commit()
        return cur.lastrowid

    def attach_discord_ids(
        self, history_id: int, message_id: str, thread_id: str | None = None
    ) -> None:
        """알람 발송 후 Discord 메시지/스레드 ID를 이력에 붙인다.

        비서 Bot이 스레드에서 질문 받을 때 thread_id → history_id 역조회.
        """
        self.conn.execute(
            """
            UPDATE alert_history
            SET discord_message_id = ?, discord_thread_id = ?
            WHERE id = ?
            """,
            (message_id, thread_id, history_id),
        )
        self.conn.commit()

    def find_history_by_thread_id(self, thread_id: str) -> dict | None:
        """스레드 ID로 알람 이력 역조회.

        비서 Bot이 '이 스레드가 어떤 알람인지' 파악할 때 사용.
        """
        cur = self.conn.execute(
            """
            SELECT id, rule_key, severity, kind, value, threshold, fired_at
            FROM alert_history
            WHERE discord_thread_id = ?
            """,
            (thread_id,),
        )
        row = cur.fetchone()
        if not row:
            return None
        return {
            "id": row[0],
            "rule_key": row[1],
            "severity": row[2],
            "kind": row[3],
            "value": row[4],
            "threshold": row[5],
            "fired_at": row[6],
        }

    def list_recent_history(self, hours: int, limit: int = 50) -> list[dict]:
        """최근 N시간 이력 반환 (비서 Bot 도구 `get_recent_alerts`용)."""
        since_ts = int(time.time()) - hours * 3600
        cur = self.conn.execute(
            """
            SELECT id, rule_key, severity, kind, value, threshold, fired_at,
                   discord_thread_id
            FROM alert_history
            WHERE fired_at >= ?
            ORDER BY fired_at DESC
            LIMIT ?
            """,
            (since_ts, limit),
        )
        return [
            {
                "id": r[0],
                "rule_key": r[1],
                "severity": r[2],
                "kind": r[3],
                "value": r[4],
                "threshold": r[5],
                "fired_at": r[6],
                "discord_thread_id": r[7],
            }
            for r in cur.fetchall()
        ]

    # === Claude 분석 리포트 (신규/악화 이벤트용) ===

    def record_report(
        self,
        history_id: int,
        summary: str,
        diagnosis: str,
        causal_chain: list[str],
        evidence: list[str],
        suggestions: list[str],
        related_metrics: dict[str, float],
    ) -> None:
        """Claude 분석 결과를 영속화한다.

        비서(Discord Bot)가 과거 이벤트에 대해 질문받을 때 재추론 없이
        이 테이블의 기록을 꺼내 쓴다.
        """
        import json as _json
        self.conn.execute(
            """
            INSERT INTO alert_reports(
                history_id, summary, diagnosis, causal_chain, evidence,
                suggestions, related_metrics, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                history_id,
                summary,
                diagnosis,
                _json.dumps(causal_chain, ensure_ascii=False),
                _json.dumps(evidence, ensure_ascii=False),
                _json.dumps(suggestions, ensure_ascii=False),
                _json.dumps(related_metrics, ensure_ascii=False),
                int(time.time()),
            ),
        )
        self.conn.commit()

    def get_report_by_history_id(self, history_id: int) -> dict | None:
        """history_id에 연결된 분석 리포트 조회. 없으면 None."""
        cur = self.conn.execute(
            """
            SELECT summary, diagnosis, causal_chain, evidence,
                   suggestions, related_metrics, created_at
            FROM alert_reports WHERE history_id = ?
            """,
            (history_id,),
        )
        row = cur.fetchone()
        if not row:
            return None
        import json as _json
        return {
            "summary": row[0],
            "diagnosis": row[1],
            "causal_chain": _json.loads(row[2]) if row[2] else [],
            "evidence": _json.loads(row[3]) if row[3] else [],
            "suggestions": _json.loads(row[4]) if row[4] else [],
            "related_metrics": _json.loads(row[5]) if row[5] else {},
            "created_at": row[6],
        }

    def aggregate_last_days(self, days: int) -> dict:
        """지난 N일 이력을 집계하여 evolver에 전달할 dict를 반환."""
        end_ts = int(time.time())
        start_ts = end_ts - days * 86400
        return self.aggregate_range(start_ts, end_ts)

    def aggregate_range(self, start_ts: int, end_ts: int) -> dict:
        """임의 기간(start_ts ~ end_ts)의 이력을 집계해 dict 반환.

        야간 브리핑과 주간 evolver가 공유하는 집계 로직.
        """
        cur = self.conn.execute(
            """
            SELECT rule_key, severity, kind, COUNT(*), MIN(value), MAX(value)
            FROM alert_history
            WHERE fired_at >= ? AND fired_at < ?
            GROUP BY rule_key, severity, kind
            """,
            (start_ts, end_ts),
        )

        metric_map: dict[str, dict] = {}
        total = 0
        for rule_key, severity, kind, count, vmin, vmax in cur.fetchall():
            total += count
            entry = metric_map.setdefault(
                rule_key,
                {
                    "rule_key": rule_key,
                    "severity": severity,
                    "counts": {"new": 0, "escalation": 0, "persistence": 0, "recovery": 0},
                    "value_min": None,
                    "value_max": None,
                    "avg_duration_minutes": None,
                },
            )
            if kind in entry["counts"]:
                entry["counts"][kind] = count
            if vmin is not None:
                cur_min = entry["value_min"]
                entry["value_min"] = vmin if cur_min is None else min(cur_min, vmin)
            if vmax is not None:
                cur_max = entry["value_max"]
                entry["value_max"] = vmax if cur_max is None else max(cur_max, vmax)

        for rule_key, entry in metric_map.items():
            entry["avg_duration_minutes"] = self._avg_alert_duration(rule_key, start_ts, end_ts)

        from datetime import datetime
        period_seconds = max(end_ts - start_ts, 1)
        return {
            "period_days": round(period_seconds / 86400, 2),
            "period_start": datetime.fromtimestamp(start_ts).isoformat(timespec="minutes"),
            "period_end": datetime.fromtimestamp(end_ts).isoformat(timespec="minutes"),
            "total_alerts": total,
            "by_metric": list(metric_map.values()),
        }

    def _avg_alert_duration(
        self, rule_key: str, start_ts: int, end_ts: int
    ) -> float | None:
        """특정 메트릭의 new → recovery 평균 지속 시간(분).

        같은 rule_key에 대해 new와 recovery가 번갈아 발생한다고 가정하고
        시간순으로 짝지어 지속 시간을 평균낸다. unresolved한 new는 제외.
        """
        cur = self.conn.execute(
            """
            SELECT kind, fired_at FROM alert_history
            WHERE rule_key = ? AND fired_at >= ? AND fired_at < ?
              AND kind IN ('new', 'recovery')
            ORDER BY fired_at ASC
            """,
            (rule_key, start_ts, end_ts),
        )
        rows = cur.fetchall()
        durations: list[int] = []
        current_new_at: int | None = None
        for kind, ts in rows:
            if kind == "new":
                current_new_at = ts
            elif kind == "recovery" and current_new_at is not None:
                durations.append(ts - current_new_at)
                current_new_at = None
        if not durations:
            return None
        return round(sum(durations) / len(durations) / 60, 1)

    # === evolver 중복 방지 ===

    def is_evolve_sent_this_week(self) -> bool:
        """이번 주(월요일 기준)에 이미 evolve 리포트를 발송했는지."""
        from datetime import date as _date, timedelta
        today = _date.today()
        week_start = (today - timedelta(days=today.weekday())).isoformat()
        cur = self.conn.execute(
            "SELECT sent_at FROM evolve_runs WHERE run_date = ?", (week_start,)
        )
        return cur.fetchone() is not None

    def mark_evolve_sent(self) -> None:
        from datetime import date as _date, timedelta
        today = _date.today()
        week_start = (today - timedelta(days=today.weekday())).isoformat()
        self.conn.execute(
            "INSERT OR REPLACE INTO evolve_runs(run_date, sent_at) VALUES (?, ?)",
            (week_start, int(time.time())),
        )
        self.conn.commit()

    # === 야간 브리핑 중복 방지 ===

    def is_night_report_sent_today(self) -> bool:
        today = date.today().isoformat()
        cur = self.conn.execute(
            "SELECT sent_at FROM night_reports WHERE report_date = ?", (today,)
        )
        return cur.fetchone() is not None

    def mark_night_report_sent(self) -> None:
        today = date.today().isoformat()
        self.conn.execute(
            "INSERT OR REPLACE INTO night_reports(report_date, sent_at) VALUES (?, ?)",
            (today, int(time.time())),
        )
        self.conn.commit()

    def close(self) -> None:
        self.conn.close()
