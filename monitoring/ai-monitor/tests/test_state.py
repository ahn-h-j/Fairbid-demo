"""state.StateStore 단위 테스트 — 이력 + 리포트 저장/조회."""
from __future__ import annotations

from pathlib import Path

import pytest

from src.state import StateStore


@pytest.fixture
def store(tmp_path: Path):
    db_path = tmp_path / "test_state.db"
    s = StateStore(str(db_path))
    yield s
    s.close()


def test_record_history_returns_id(store: StateStore):
    """record_history는 새 row의 id를 반환한다."""
    id1 = store.record_history("cpu_usage", "critical", "new", 0.95, 0.90)
    id2 = store.record_history("cpu_usage", "critical", "recovery", 0.30, None)
    assert id1 == 1
    assert id2 == 2


def test_record_and_get_report(store: StateStore):
    """분석 리포트 저장 후 history_id로 조회 가능."""
    history_id = store.record_history("cpu_usage", "critical", "new", 0.95, 0.90)
    store.record_report(
        history_id=history_id,
        summary="CPU 사용률 95% 감지",
        diagnosis="실제 부하 증가로 판단",
        causal_chain=["트래픽 급증", "GC 빈도 상승", "응답 지연"],
        evidence=["HTTP RPS 평상시 3배", "GC pause 2배 증가"],
        suggestions=["스케일아웃 검토", "GC 튜닝"],
        related_metrics={"http_p95_latency": 1.2, "gc_rate": 3.5},
    )

    retrieved = store.get_report_by_history_id(history_id)
    assert retrieved is not None
    assert retrieved["summary"] == "CPU 사용률 95% 감지"
    assert retrieved["diagnosis"] == "실제 부하 증가로 판단"
    assert retrieved["causal_chain"] == ["트래픽 급증", "GC 빈도 상승", "응답 지연"]
    assert retrieved["evidence"] == ["HTTP RPS 평상시 3배", "GC pause 2배 증가"]
    assert retrieved["suggestions"] == ["스케일아웃 검토", "GC 튜닝"]
    assert retrieved["related_metrics"] == {"http_p95_latency": 1.2, "gc_rate": 3.5}


def test_get_report_missing_returns_none(store: StateStore):
    """존재하지 않는 history_id면 None."""
    assert store.get_report_by_history_id(999) is None


def test_active_alerts_lifecycle(store: StateStore):
    """활성 알람: 없음 → 기록 → 조회 → 삭제."""
    assert store.list_active() == {}

    store.record_alert("cpu_usage", 0.95, "critical")
    active = store.list_active()
    assert "cpu_usage" in active
    assert active["cpu_usage"].last_value == 0.95
    assert active["cpu_usage"].severity == "critical"

    store.clear_alert("cpu_usage")
    assert store.list_active() == {}


def test_record_alert_upsert(store: StateStore):
    """같은 rule_key로 재기록하면 last_value가 갱신된다."""
    store.record_alert("cpu_usage", 0.91, "critical")
    store.record_alert("cpu_usage", 0.98, "critical")
    active = store.list_active()
    assert active["cpu_usage"].last_value == 0.98


def test_aggregate_range_counts_by_kind(store: StateStore):
    """aggregate_range는 메트릭별 kind 건수를 집계한다."""
    import time as _t
    now = int(_t.time())

    store.record_history("cpu_usage", "critical", "new", 0.95, 0.90)
    store.record_history("cpu_usage", "critical", "new", 0.92, 0.90)
    store.record_history("cpu_usage", "critical", "recovery", 0.30, None)

    agg = store.aggregate_range(now - 3600, now + 3600)
    assert agg["total_alerts"] == 3
    metric = agg["by_metric"][0]
    assert metric["counts"]["new"] == 2
    assert metric["counts"]["recovery"] == 1


def test_night_report_sent_flag(store: StateStore):
    """is_night_report_sent_today: 초기 False → mark 이후 True."""
    assert not store.is_night_report_sent_today()
    store.mark_night_report_sent()
    assert store.is_night_report_sent_today()
