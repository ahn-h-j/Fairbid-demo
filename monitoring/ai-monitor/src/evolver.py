"""주간 모니터링 정책 리뷰 (evolver).

역할:
- state.db의 alert_history를 지난 7일치 집계
- 현재 config.yaml의 임계치/cooldown과 함께 Claude에 전달
- 조정 제안을 JSON으로 받아 reporter가 발송할 EvolveReport로 변환

자동 적용은 하지 않는다. 사용자가 config.yaml을 수동 수정한다.
"""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from pathlib import Path

from anthropic import Anthropic

from .config import Settings
from .state import StateStore

logger = logging.getLogger(__name__)

PROMPTS_DIR = Path(__file__).resolve().parent / "prompts"


@dataclass
class EvolveFinding:
    metric: str
    label: str
    observation: str
    interpretation: str
    recommendation: str


@dataclass
class EvolveReport:
    period_days: int
    period_start: str
    period_end: str
    total_alerts: int
    headline: str
    noisiest_metric: str = ""
    most_stable_metric: str = ""
    findings: list[EvolveFinding] = field(default_factory=list)
    cooldown_tuning: str = ""
    summary: str = ""
    raw_text: str = ""


class Evolver:
    def __init__(self, settings: Settings, store: StateStore):
        self.settings = settings
        self.store = store
        self.client = Anthropic(api_key=settings.claude_api_key)
        self.system_prompt = (PROMPTS_DIR / "evolve.md").read_text(encoding="utf-8")

    def run(self, days: int = 7) -> EvolveReport | None:
        """지난 N일 집계 → Claude → EvolveReport. 집계 데이터 없으면 None."""
        aggregate = self.store.aggregate_last_days(days)
        if aggregate["total_alerts"] == 0:
            logger.info("no alerts in last %d days — skip evolve", days)
            return None

        current_config = self._build_current_config_snapshot()
        user_payload = json.dumps(
            {"aggregate": aggregate, "current_config": current_config},
            ensure_ascii=False,
            indent=2,
        )

        try:
            response = self.client.messages.create(
                model=self.settings.runtime.claude_model,
                max_tokens=2048,
                system=self.system_prompt,
                messages=[{"role": "user", "content": user_payload}],
            )
        except Exception as e:
            logger.exception("Evolver Claude call failed: %s", e)
            return EvolveReport(
                period_days=days,
                period_start=aggregate["period_start"],
                period_end=aggregate["period_end"],
                total_alerts=aggregate["total_alerts"],
                headline=f"주간 리뷰 AI 분석 실패 ({type(e).__name__})",
                summary="집계 데이터는 수집됐으나 Claude 호출에 실패했습니다.",
            )

        text = "".join(block.text for block in response.content if hasattr(block, "text"))
        return self._parse(text, aggregate, days)

    def _build_current_config_snapshot(self) -> dict:
        return {
            "metrics": [
                {
                    "key": m.key,
                    "label": m.label,
                    "threshold": m.threshold,
                    "comparator": m.comparator,
                    "severity": m.severity,
                }
                for m in self.settings.metrics
                if not m.observe_only
            ],
            "cooldown_minutes": self.settings.runtime.cooldown_minutes,
            "escalation_threshold": self.settings.runtime.escalation_threshold,
        }

    def _parse(self, text: str, aggregate: dict, days: int) -> EvolveReport:
        try:
            json_text = _extract_json(text)
            data = json.loads(json_text)
            stats = data.get("stats", {})
            findings = [
                EvolveFinding(
                    metric=str(f.get("metric", "")),
                    label=str(f.get("label", "")),
                    observation=str(f.get("observation", "")),
                    interpretation=str(f.get("interpretation", "")),
                    recommendation=str(f.get("recommendation", "")),
                )
                for f in data.get("findings", [])
            ]
            return EvolveReport(
                period_days=days,
                period_start=aggregate["period_start"],
                period_end=aggregate["period_end"],
                total_alerts=aggregate["total_alerts"],
                headline=str(data.get("headline", "")),
                noisiest_metric=str(stats.get("noisiest_metric", "")),
                most_stable_metric=str(stats.get("most_stable_metric", "")),
                findings=findings,
                cooldown_tuning=str(data.get("cooldown_tuning", "")),
                summary=str(data.get("summary", "")),
            )
        except (ValueError, json.JSONDecodeError) as e:
            logger.warning("Failed to parse evolver response: %s\n%s", e, text)
            return EvolveReport(
                period_days=days,
                period_start=aggregate["period_start"],
                period_end=aggregate["period_end"],
                total_alerts=aggregate["total_alerts"],
                headline="주간 리뷰 (AI 응답 파싱 실패)",
                raw_text=text.strip()[:1500],
            )


def _extract_json(text: str) -> str:
    text = text.strip()
    if "```" in text:
        for part in text.split("```"):
            stripped = part.strip()
            if stripped.startswith("json"):
                stripped = stripped[4:].strip()
            if stripped.startswith("{"):
                return stripped
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        return text[start : end + 1]
    raise ValueError("no JSON object found")
