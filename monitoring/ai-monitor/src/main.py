"""ai-monitor 진입점 (Phase 2 — Bot 통합).

구조:
- discord.py Bot이 asyncio 이벤트 루프의 주인
- 주기 태스크(폴링/야간/주간)는 모두 asyncio.create_task로 Bot과 병행
- 기존 threading 기반 schedule 라이브러리 제거

태스크 3종:
- poll_loop              : 30초마다 이상 탐지 → 4분류 → 발송
- night_report_loop      : 매일 to_time(기본 09:00)에 야간 브리핑
- evolver_loop           : 매주 설정된 요일/시각에 튜닝 제안
"""
from __future__ import annotations

import asyncio
import logging
import signal
from datetime import date, datetime, time as dtime, timedelta
from typing import Optional

import discord

from .analyzer import ClaudeAnalyzer
from .bot.client import AiMonitorBot
from .bot.handler import QuestionHandler
from .bot.tools import ToolExecutor
from .config import Settings, load_settings
from .evolver import Evolver
from .prometheus import PrometheusClient, QueryResult
from .reporter import DiscordReporter
from .rules import evaluate
from .state import StateStore

logger = logging.getLogger("ai-monitor")


async def async_main() -> None:
    settings = load_settings()
    _setup_logging(settings.log_level)

    logger.info("ai-monitor starting up (Bot mode)")

    if not settings.discord_bot_token or settings.discord_channel_id == 0:
        logger.error(
            "DISCORD_BOT_TOKEN or DISCORD_CHANNEL_ID not configured — exit"
        )
        return

    # 공유 리소스
    store = StateStore(settings.state_db_path)
    prom = PrometheusClient(settings.prometheus_url, settings.runtime.prometheus_timeout_seconds)
    analyzer = ClaudeAnalyzer(settings)
    evolver = Evolver(settings, store)
    executor = ToolExecutor(settings.prometheus_url, store)
    question_handler = QuestionHandler(settings, store, executor)

    # Bot 인스턴스 — handler.answer를 on_message에 연결
    # handler.answer 내부에서 asyncio.to_thread로 blocking 분리함.
    async def on_question(thread, question: str) -> str:
        return await question_handler.answer(thread, question)

    bot = AiMonitorBot(
        channel_id=settings.discord_channel_id,
        question_handler=on_question,
    )
    reporter = DiscordReporter(bot)

    # /ask 슬래시 명령 — 스레드 밖에서도 자유 질의
    @bot.tree.command(name="ask", description="AI 모니터링 비서에게 질문")
    async def ask(interaction: discord.Interaction, question: str):
        await interaction.response.defer(thinking=True)
        try:
            answer = await question_handler.answer(None, question)
            for chunk_start in range(0, len(answer), 1900):
                chunk = answer[chunk_start : chunk_start + 1900]
                await interaction.followup.send(chunk)
        except Exception as e:
            logger.exception("/ask failed: %s", e)
            await interaction.followup.send(f"⚠️ 오류: {type(e).__name__}")

    # 주기 태스크 등록 (Bot 로그인 후 on_ready에서 시작)
    @bot.event
    async def on_ready():
        logger.info("Bot logged in as %s", bot.user)
        try:
            synced = await bot.tree.sync()
            logger.info("slash commands synced: %d", len(synced))
        except Exception as e:
            logger.warning("slash sync failed: %s", e)

        # 이미 구동 중이면 재시작 방지
        if getattr(bot, "_periodic_started", False):
            return
        bot._periodic_started = True

        asyncio.create_task(poll_loop(settings, prom, analyzer, reporter, store))
        if settings.runtime.night_report.enabled:
            asyncio.create_task(night_report_loop(settings, prom, analyzer, reporter, store))
        if settings.runtime.evolve.enabled:
            asyncio.create_task(evolver_loop(settings, evolver, reporter, store))

    # 우아한 종료
    def shutdown_handler():
        logger.info("shutdown requested")
        asyncio.create_task(bot.close())

    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, shutdown_handler)
        except NotImplementedError:
            # Windows — signal handler 등록 지원 안 됨
            pass

    try:
        await bot.start(settings.discord_bot_token)
    finally:
        store.close()


# === 주기 태스크 ===

async def poll_loop(
    settings: Settings,
    prom: PrometheusClient,
    analyzer: ClaudeAnalyzer,
    reporter: DiscordReporter,
    store: StateStore,
) -> None:
    interval = settings.runtime.check_interval_seconds
    # 초기 1회 즉시 실행
    await _safe(lambda: _check_anomalies(settings, prom, analyzer, reporter, store))
    while True:
        await asyncio.sleep(interval)
        await _safe(lambda: _check_anomalies(settings, prom, analyzer, reporter, store))


async def night_report_loop(
    settings: Settings,
    prom: PrometheusClient,
    analyzer: ClaudeAnalyzer,
    reporter: DiscordReporter,
    store: StateStore,
) -> None:
    cfg = settings.runtime.night_report
    while True:
        now = datetime.now()
        target_time = _parse_hhmm(cfg.to_time)
        next_run = datetime.combine(now.date(), dtime(*target_time))
        if next_run <= now:
            next_run += timedelta(days=1)
        delay = (next_run - now).total_seconds()
        logger.info("next night report at %s (%.0fs)", next_run, delay)
        await asyncio.sleep(delay)
        await _safe(lambda: _send_night_report(settings, prom, analyzer, reporter, store))


async def evolver_loop(
    settings: Settings,
    evolver: Evolver,
    reporter: DiscordReporter,
    store: StateStore,
) -> None:
    cfg = settings.runtime.evolve
    weekday_map = {
        "monday": 0, "tuesday": 1, "wednesday": 2, "thursday": 3,
        "friday": 4, "saturday": 5, "sunday": 6,
    }
    target_weekday = weekday_map.get(cfg.day.lower(), 0)
    while True:
        now = datetime.now()
        target_time = _parse_hhmm(cfg.time)
        # 이번 주 또는 다음 주의 target weekday
        days_ahead = (target_weekday - now.weekday()) % 7
        next_run = datetime.combine(
            now.date() + timedelta(days=days_ahead), dtime(*target_time)
        )
        if next_run <= now:
            next_run += timedelta(days=7)
        delay = (next_run - now).total_seconds()
        logger.info("next evolver at %s (%.0fs)", next_run, delay)
        await asyncio.sleep(delay)
        await _safe(lambda: _run_evolver(settings, evolver, reporter, store))


# === 이상 탐지 1 사이클 (기존 로직, async로 전환) ===

async def _check_anomalies(
    settings: Settings,
    prom: PrometheusClient,
    analyzer: ClaudeAnalyzer,
    reporter: DiscordReporter,
    store: StateStore,
) -> None:
    runtime = settings.runtime
    import time as _t

    # 1. 폴링 (blocking → run_in_executor)
    loop = asyncio.get_event_loop()
    results: dict[str, QueryResult] = {}
    for metric in settings.metrics:
        result = await loop.run_in_executor(
            None, lambda m=metric: prom.query(m.key, m.promql)
        )
        results[metric.key] = result

    # 2. 룰 평가
    violations = evaluate(settings.metrics, results)
    violation_map = {v.metric.key: v for v in violations}

    # 3. 활성 알람 조회
    active = store.list_active()
    now_ts = int(_t.time())

    # 4. 분류
    new_violations: list = []
    escalated: list = []
    persisted: list = []

    for v in violations:
        prev = active.get(v.metric.key)
        if prev is None:
            new_violations.append(v)
            continue

        denom = max(abs(prev.last_value), 1e-9)
        change_ratio = abs(v.value - prev.last_value) / denom
        worsened = (
            change_ratio >= runtime.escalation_threshold
            and (
                (v.metric.comparator == "gt" and v.value > prev.last_value)
                or (v.metric.comparator == "lt" and v.value < prev.last_value)
                or (v.metric.comparator == "abs_gt" and abs(v.value) > abs(prev.last_value))
            )
        )

        cooldown_min = runtime.cooldown_for(v.metric.severity)
        elapsed_seconds = now_ts - prev.fired_at

        if worsened:
            escalated.append(v)
        elif elapsed_seconds >= cooldown_min * 60:
            persisted.append(v)

    recovered = [alert for key, alert in active.items() if key not in violation_map]

    # 5. 발송
    if new_violations:
        await _handle_new(new_violations, results, analyzer, reporter, store)
    if escalated:
        await _handle_escalation(escalated, active, results, analyzer, reporter, store)
    if persisted:
        await _handle_persistence(persisted, active, reporter, store)
    if recovered and runtime.send_recovery_notice:
        label_map = {m.key: m.label for m in settings.metrics}
        await _handle_recovery(recovered, results, reporter, store, label_map)

    if not (new_violations or escalated or persisted or recovered):
        logger.debug("no actionable changes — skip")


async def _handle_new(violations, results, analyzer, reporter, store) -> None:
    logger.info("calling Claude for %d new violations", len(violations))
    loop = asyncio.get_event_loop()
    report = await loop.run_in_executor(
        None, lambda: analyzer.analyze(violations, results)
    )
    message_id, thread_id = await reporter.send_anomaly(violations, report)
    if message_id:
        logger.info("anomaly report sent (msg=%s thread=%s)", message_id, thread_id)
    for v in violations:
        store.record_alert(v.metric.key, v.value, v.metric.severity)
        history_id = store.record_history(
            v.metric.key, v.metric.severity, "new", v.value, v.metric.threshold
        )
        _persist_report(store, history_id, report)
        if message_id:
            store.attach_discord_ids(history_id, message_id, thread_id)


async def _handle_escalation(violations, previous, results, analyzer, reporter, store) -> None:
    logger.info("calling Claude for %d escalated violations", len(violations))
    loop = asyncio.get_event_loop()
    report = await loop.run_in_executor(
        None, lambda: analyzer.analyze(violations, results)
    )
    message_id, thread_id = await reporter.send_escalation(violations, previous, report)
    if message_id:
        logger.info("escalation report sent (msg=%s thread=%s)", message_id, thread_id)
    for v in violations:
        store.record_alert(v.metric.key, v.value, v.metric.severity)
        history_id = store.record_history(
            v.metric.key, v.metric.severity, "escalation", v.value, v.metric.threshold
        )
        _persist_report(store, history_id, report)
        if message_id:
            store.attach_discord_ids(history_id, message_id, thread_id)


async def _handle_persistence(violations, previous, reporter, store) -> None:
    logger.info("sending persistence notice for %d violations", len(violations))
    message_id, _ = await reporter.send_persistence(violations, previous)
    for v in violations:
        store.record_alert(v.metric.key, v.value, v.metric.severity)
        history_id = store.record_history(
            v.metric.key, v.metric.severity, "persistence", v.value, v.metric.threshold
        )
        if message_id:
            store.attach_discord_ids(history_id, message_id, None)


async def _handle_recovery(recovered, results, reporter, store, label_map) -> None:
    logger.info("sending recovery notice for %d alerts", len(recovered))
    current_values = {k: r.value for k, r in results.items() if r.value is not None}
    message_id, _ = await reporter.send_recovery(recovered, current_values, label_map)
    for alert in recovered:
        current_value = current_values.get(alert.rule_key)
        history_id = store.record_history(
            alert.rule_key, alert.severity, "recovery", current_value, None
        )
        if message_id:
            store.attach_discord_ids(history_id, message_id, None)
        store.clear_alert(alert.rule_key)


def _persist_report(store, history_id: int, report) -> None:
    try:
        store.record_report(
            history_id=history_id,
            summary=report.summary,
            diagnosis=report.diagnosis,
            causal_chain=report.causal_chain,
            evidence=report.evidence,
            suggestions=report.suggestions,
            related_metrics=report.related_metrics,
        )
    except Exception as e:
        logger.warning("failed to persist alert report: %s", e)


async def _run_evolver(settings, evolver, reporter, store) -> None:
    if store.is_evolve_sent_this_week():
        logger.info("evolver already sent this week — skip")
        return

    loop = asyncio.get_event_loop()
    report = await loop.run_in_executor(
        None, lambda: evolver.run(days=settings.runtime.evolve.lookback_days)
    )
    if report is None:
        return
    message_id, _ = await reporter.send_evolve_report(report)
    if message_id:
        store.mark_evolve_sent()


async def _send_night_report(settings, prom, analyzer, reporter, store) -> None:
    if store.is_night_report_sent_today():
        return

    cfg = settings.runtime.night_report
    today = date.today()
    from_h, from_m = _parse_hhmm(cfg.from_time)
    to_h, to_m = _parse_hhmm(cfg.to_time)

    start_dt = datetime.combine(today - timedelta(days=1), dtime(from_h, from_m))
    end_dt = datetime.combine(today, dtime(to_h, to_m))
    start_ts = int(start_dt.timestamp())
    end_ts = int(end_dt.timestamp())

    aggregate = store.aggregate_range(start_ts, end_ts)
    period_start = start_dt.strftime("%Y-%m-%d %H:%M")
    period_end = end_dt.strftime("%Y-%m-%d %H:%M")

    if aggregate["total_alerts"] == 0:
        message_id, _ = await reporter.send_night_report("", 0, period_start, period_end)
        if message_id:
            store.mark_night_report_sent()
        return

    loop = asyncio.get_event_loop()
    snapshot = {}
    for metric in settings.metrics:
        r = await loop.run_in_executor(None, lambda m=metric: prom.query(m.key, m.promql))
        snapshot[metric.key] = r.value

    summary_text = await loop.run_in_executor(
        None, lambda: analyzer.night_report(aggregate, snapshot)
    )
    message_id, _ = await reporter.send_night_report(
        summary_text, aggregate["total_alerts"], period_start, period_end
    )
    if message_id:
        store.mark_night_report_sent()


# === 유틸 ===

async def _safe(coro_fn) -> None:
    try:
        await coro_fn()
    except Exception as e:
        logger.exception("periodic task failed: %s", e)


def _parse_hhmm(text: str) -> tuple[int, int]:
    hh, _, mm = text.partition(":")
    return int(hh), int(mm)


def _setup_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )


def main() -> None:
    asyncio.run(async_main())


if __name__ == "__main__":
    main()
