"""Discord Bot 기반 알람/리포트 발송.

Bot은 AiMonitorBot 인스턴스를 주입받아 사용한다.
모든 send_* 메서드는 async이며 (message_id, thread_id) 튜플을 반환한다.
(thread_id는 신규/악화 알람에서만 생성, 나머지는 None).

임베드 빌더는 순수 함수로 분리되어 있어 Bot과 무관하게 테스트 가능.
"""
from __future__ import annotations

import logging
from datetime import datetime
from typing import Optional

import discord

from .analyzer import AnalysisReport
from .rules import Violation
from .state import ActiveAlert

logger = logging.getLogger(__name__)

# Discord embed 색상
COLOR_CRITICAL = 0xE74C3C   # 빨강 — 신규 critical
COLOR_ESCALATED = 0x992D22  # 진한 빨강 — 악화
COLOR_WARNING = 0xF39C12    # 주황 — warning / 지속
COLOR_INFO = 0x3498DB       # 파랑 — info / 주간 리뷰
COLOR_OK = 0x2ECC71         # 초록 — 해소 / 평온한 밤


class DiscordReporter:
    """Bot 인스턴스를 래핑하여 임베드 발송을 담당.

    Bot은 AiMonitorBot (discord.py commands.Bot) — 별도 모듈에서 기동됨.
    """

    def __init__(self, bot):
        self.bot = bot

    # === 1. 신규 이상 알람 (자동 스레드 생성) ===

    async def send_anomaly(
        self, violations: list[Violation], report: AnalysisReport
    ) -> tuple[Optional[str], Optional[str]]:
        is_critical = any(v.metric.severity == "critical" for v in violations)
        embed = discord.Embed(
            title=f"🚨 {report.summary or '메트릭 임계치 초과'}",
            color=COLOR_CRITICAL if is_critical else COLOR_WARNING,
            timestamp=datetime.utcnow(),
        )
        embed.add_field(name="📊 위반 메트릭", value=_build_violation_field(violations)[:1024], inline=False)
        embed.add_field(name="🔍 원인 분석", value=_build_root_cause(report)[:1024], inline=False)
        embed.add_field(name="💡 대응 제안", value=_build_suggestions(report)[:1024], inline=False)
        embed.add_field(name="📎 관련 메트릭", value=_build_context_field(report, violations)[:1024], inline=False)

        thread_name = f"🚨 {report.summary or '알람'}"[:100]
        return await self.bot.send_alert(embed, create_thread=True, thread_name=thread_name)

    # === 2. 악화 알람 (스레드 생성) ===

    async def send_escalation(
        self,
        violations: list[Violation],
        previous: dict[str, ActiveAlert],
        report: AnalysisReport,
    ) -> tuple[Optional[str], Optional[str]]:
        delta_lines = []
        for v in violations:
            prev = previous.get(v.metric.key)
            if prev is None:
                continue
            prev_str = _format_value(prev.last_value, v.metric.unit)
            curr_str = _format_value(v.value, v.metric.unit)
            denom = max(abs(prev.last_value), 1e-9)
            delta_pct = (v.value - prev.last_value) / denom * 100
            arrow = "↑" if v.value > prev.last_value else "↓"
            delta_lines.append(
                f"`{v.metric.severity.upper():<8}` **{v.metric.label}**\n"
                f"     └ {prev_str} → {curr_str} ({arrow} {abs(delta_pct):.0f}%)"
            )

        embed = discord.Embed(
            title=f"📈 [악화] {report.summary or '메트릭 악화'}",
            color=COLOR_ESCALATED,
            timestamp=datetime.utcnow(),
        )
        embed.add_field(
            name="📊 악화 메트릭",
            value=("\n".join(delta_lines) or _build_violation_field(violations))[:1024],
            inline=False,
        )
        embed.add_field(name="🔍 원인 분석", value=_build_root_cause(report)[:1024], inline=False)
        embed.add_field(name="💡 대응 제안", value=_build_suggestions(report)[:1024], inline=False)
        embed.add_field(name="📎 관련 메트릭", value=_build_context_field(report, violations)[:1024], inline=False)

        thread_name = f"📈 [악화] {report.summary or '알람'}"[:100]
        return await self.bot.send_alert(embed, create_thread=True, thread_name=thread_name)

    # === 3. 지속 알람 (Claude 호출 없음, 스레드 없음) ===

    async def send_persistence(
        self, violations: list[Violation], previous: dict[str, ActiveAlert]
    ) -> tuple[Optional[str], Optional[str]]:
        now_ts = int(datetime.utcnow().timestamp())
        lines = []
        for v in violations:
            prev = previous.get(v.metric.key)
            elapsed_min = (now_ts - prev.fired_at) // 60 if prev else 0
            lines.append(
                f"`{v.metric.severity.upper():<8}` **{v.metric.label}**\n"
                f"     └ 측정값 `{_format_value(v.value, v.metric.unit)}` "
                f"(첫 발생 후 {elapsed_min}분 경과)"
            )

        embed = discord.Embed(
            title=f"⏰ [지속 중] {len(violations)}건의 알람이 해소되지 않음",
            description="이 알람은 추가 비용 절감을 위해 AI 분석 없이 발송됩니다.",
            color=COLOR_WARNING,
            timestamp=datetime.utcnow(),
        )
        embed.add_field(name="📊 지속 중인 위반", value="\n".join(lines)[:1024], inline=False)
        return await self.bot.send_alert(embed, create_thread=False)

    # === 4. 해소 알람 (Claude 없음, 스레드 없음) ===

    async def send_recovery(
        self,
        recovered: list[ActiveAlert],
        current_results: dict,
        label_map: dict[str, str] | None = None,
    ) -> tuple[Optional[str], Optional[str]]:
        label_map = label_map or {}
        now_ts = int(datetime.utcnow().timestamp())
        lines = []
        for alert in recovered:
            current_val = current_results.get(alert.rule_key)
            current_str = f"{current_val:.4g}" if current_val is not None else "n/a"
            duration_min = (now_ts - alert.fired_at) // 60
            display_label = label_map.get(alert.rule_key, alert.rule_key)
            lines.append(
                f"`{alert.severity.upper():<8}` **{display_label}**\n"
                f"     └ 정상값 `{current_str}` (지속 {duration_min}분 후 해소)"
            )

        embed = discord.Embed(
            title=f"✅ [해소됨] {len(recovered)}건의 알람이 정상화되었습니다",
            color=COLOR_OK,
            timestamp=datetime.utcnow(),
        )
        embed.add_field(name="정상 복귀", value="\n".join(lines)[:1024], inline=False)
        return await self.bot.send_alert(embed, create_thread=False)

    # === 5. 주간 evolver 리포트 ===

    async def send_evolve_report(self, report) -> tuple[Optional[str], Optional[str]]:
        findings_lines = []
        for f in report.findings[:5]:
            findings_lines.append(f"**{f.label or f.metric}**")
            if f.observation:
                findings_lines.append(f"▸ 관찰 · {f.observation}")
            if f.interpretation:
                findings_lines.append(f"▸ 해석 · {f.interpretation}")
            if f.recommendation:
                findings_lines.append(f"▸ 💡 제안 · {f.recommendation}")
            findings_lines.append("")
        findings_text = "\n".join(findings_lines).strip() or "(조정 제안 없음)"

        stats_lines = [
            f"• 기간 · `{report.period_start} ~ {report.period_end}` ({report.period_days}일)",
            f"• 총 알람 건수 · `{report.total_alerts}건`",
        ]
        if report.noisiest_metric:
            stats_lines.append(f"• 가장 시끄러운 메트릭 · `{report.noisiest_metric}`")
        if report.most_stable_metric:
            stats_lines.append(f"• 가장 안정적 메트릭 · `{report.most_stable_metric}`")

        embed = discord.Embed(
            title=f"🧪 [주간 모니터링 리뷰] {report.headline or '정책 조정 제안'}",
            description="자동 적용되지 않습니다. 검토 후 `monitoring/ai-monitor/config.yaml`을 수동 수정하세요.",
            color=COLOR_INFO,
            timestamp=datetime.utcnow(),
        )
        embed.add_field(name="📊 주간 통계", value="\n".join(stats_lines)[:1024], inline=False)
        embed.add_field(name="🔍 메트릭별 분석 및 제안", value=findings_text[:1024], inline=False)
        if report.cooldown_tuning:
            embed.add_field(name="⏱ 쿨다운 튜닝", value=report.cooldown_tuning[:1024], inline=False)
        if report.summary:
            embed.add_field(name="📝 총평", value=report.summary[:1024], inline=False)
        if report.raw_text:
            embed.add_field(name="⚠️ 파싱 실패 — 원문", value=f"```\n{report.raw_text[:900]}\n```", inline=False)

        return await self.bot.send_alert(embed, create_thread=False)

    # === 6. 야간 브리핑 ===

    async def send_night_report(
        self,
        summary_text: str,
        total_alerts: int,
        period_start: str,
        period_end: str,
    ) -> tuple[Optional[str], Optional[str]]:
        if total_alerts == 0:
            embed = discord.Embed(
                title="🌙 [야간 브리핑] 평온한 밤이었습니다",
                description=f"`{period_start} ~ {period_end}` 사이 이상 탐지 0건.",
                color=COLOR_OK,
            )
            embed.set_footer(text="AI monitor heartbeat")
        else:
            embed = discord.Embed(
                title=f"🌙 [야간 브리핑] {datetime.now().strftime('%Y-%m-%d')}",
                description=summary_text[:4000],
                color=COLOR_WARNING,
            )
            embed.set_footer(text=f"{period_start} ~ {period_end} · 총 {total_alerts}건")
        return await self.bot.send_alert(embed, create_thread=False)


# === 임베드 빌더 헬퍼 (순수 함수, 테스트 용이) ===

def _build_violation_field(violations: list[Violation]) -> str:
    return "\n".join(
        f"`{v.metric.severity.upper():<8}` **{v.metric.label}**\n"
        f"     └ 측정값 `{_format_value(v.value, v.metric.unit)}` "
        f"(임계치 `{_format_value(v.metric.threshold, v.metric.unit)}`)"
        for v in violations
    )


def _build_root_cause(report: AnalysisReport) -> str:
    lines = []
    if report.diagnosis:
        lines.append(f"**판단** · {report.diagnosis}")
    if report.causal_chain:
        lines.append("")
        lines.append("**인과 체인**")
        arrows = ["①", "②", "③", "④"]
        for i, step in enumerate(report.causal_chain[:4]):
            marker = arrows[i] if i < len(arrows) else f"{i + 1}."
            lines.append(f"{marker} {step}")
    if report.evidence:
        lines.append("")
        lines.append("**근거**")
        for ev in report.evidence[:3]:
            lines.append(f"▸ {ev}")
    if report.raw_text and not lines:
        lines.append(report.raw_text)
    return "\n".join(lines) or "(분석 결과 없음)"


def _build_suggestions(report: AnalysisReport) -> str:
    return "\n".join(
        f"`{i + 1}.` {s}" for i, s in enumerate(report.suggestions)
    ) or "(제안 없음)"


def _build_context_field(report: AnalysisReport, violations: list[Violation]) -> str:
    violation_keys = {v.metric.key for v in violations}
    lines = []
    for k, val in report.related_metrics.items():
        if k in violation_keys:
            continue
        lines.append(f"{k:<26} {_format_value(val, '')}")
    if not lines:
        return "(없음)"
    return "```\n" + "\n".join(lines[:12]) + "\n```"


def _format_value(value: float, unit: str) -> str:
    if unit == "ratio":
        return f"{value * 100:.2f}%"
    if unit == "seconds":
        return f"{value:.3f}s"
    if unit == "per_second":
        return f"{value:.2f}/s"
    if unit == "count":
        return f"{int(value):,}"
    return f"{value:.4g}"
