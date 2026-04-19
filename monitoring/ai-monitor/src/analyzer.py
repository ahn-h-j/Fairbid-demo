"""Claude API를 호출해 이상 메트릭의 인과 분석을 수행한다.

룰 필터에서 위반이 감지된 경우에만 호출 — 정상 시 비용 0.
"""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from pathlib import Path

from anthropic import Anthropic

from .config import Settings
from .prometheus import QueryResult
from .rules import Violation

logger = logging.getLogger(__name__)

PROMPTS_DIR = Path(__file__).resolve().parent / "prompts"


@dataclass
class AnalysisReport:
    summary: str                                    # 한 줄 요약
    diagnosis: str = ""                             # 한 줄 판단
    causal_chain: list[str] = field(default_factory=list)  # 단계별 인과 체인
    evidence: list[str] = field(default_factory=list)      # 정상 메트릭 근거
    suggestions: list[str] = field(default_factory=list)
    related_metrics: dict[str, float] = field(default_factory=dict)
    raw_text: str = ""                              # 파싱 실패 시 원문


class ClaudeAnalyzer:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = Anthropic(api_key=settings.claude_api_key)
        self.system_prompt = (PROMPTS_DIR / "anomaly.md").read_text(encoding="utf-8")

    def analyze(
        self,
        violations: list[Violation],
        all_results: dict[str, QueryResult],
    ) -> AnalysisReport:
        """위반 + 전체 메트릭 컨텍스트를 보내 인과 분석을 받는다."""
        context = self._build_user_message(violations, all_results)

        try:
            response = self.client.messages.create(
                model=self.settings.runtime.claude_model,
                max_tokens=1024,
                system=self.system_prompt,
                messages=[{"role": "user", "content": context}],
            )
        except Exception as e:
            logger.exception("Claude API call failed: %s", e)
            return AnalysisReport(
                summary=f"이상 탐지: {', '.join(v.metric.label for v in violations)}",
                diagnosis=f"AI 분석 실패 ({type(e).__name__})",
                causal_chain=["룰 필터에서 임계치 위반만 감지됨"],
                related_metrics={k: r.value for k, r in all_results.items() if r.value is not None},
            )

        text = "".join(block.text for block in response.content if hasattr(block, "text"))
        return self._parse_response(text, violations, all_results)

    def night_report(
        self,
        aggregate: dict,
        snapshot: dict[str, float | None],
    ) -> str:
        """야간 브리핑 텍스트를 생성한다.

        Args:
            aggregate: state.aggregate_range() 결과 (period/total/by_metric)
            snapshot: 현재 메트릭 값 (출근 시점 현재 상태)
        """
        prompt = (PROMPTS_DIR / "night-report.md").read_text(encoding="utf-8")
        user_msg = json.dumps(
            {"aggregate": aggregate, "current_snapshot": snapshot},
            ensure_ascii=False, indent=2,
        )
        try:
            response = self.client.messages.create(
                model=self.settings.runtime.claude_model,
                max_tokens=768,
                system=prompt,
                messages=[{"role": "user", "content": user_msg}],
            )
            return "".join(b.text for b in response.content if hasattr(b, "text"))
        except Exception as e:
            logger.exception("Night report generation failed: %s", e)
            return f"야간 브리핑 생성 실패: {e}"

    # === internal ===

    def _build_user_message(
        self,
        violations: list[Violation],
        all_results: dict[str, QueryResult],
    ) -> str:
        # 모든 메트릭 값을 첨부하여 인과 추론 컨텍스트로 사용
        metric_snapshot = {
            r.metric_key: r.value for r in all_results.values() if r.value is not None
        }
        violation_list = [
            {
                "key": v.metric.key,
                "label": v.metric.label,
                "value": v.value,
                "threshold": v.metric.threshold,
                "comparator": v.metric.comparator,
                "severity": v.metric.severity,
                "unit": v.metric.unit,
            }
            for v in violations
        ]
        return json.dumps(
            {
                "violations": violation_list,
                "all_metrics": metric_snapshot,
            },
            ensure_ascii=False,
            indent=2,
        )

    def _parse_response(
        self,
        text: str,
        violations: list[Violation],
        all_results: dict[str, QueryResult],
    ) -> AnalysisReport:
        """Claude 응답에서 JSON 블록을 추출한다.

        프롬프트가 JSON을 강제하지만, 코드블록(```json … ```)으로 감쌀 수도 있고
        앞뒤에 설명이 붙을 수도 있어 관대하게 파싱한다.
        """
        related = {k: r.value for k, r in all_results.items() if r.value is not None}
        try:
            json_text = _extract_json(text)
            data = json.loads(json_text)
            return AnalysisReport(
                summary=str(data.get("summary", "")),
                diagnosis=str(data.get("diagnosis", "")),
                causal_chain=[str(s) for s in data.get("causal_chain", [])],
                evidence=[str(s) for s in data.get("evidence", [])],
                suggestions=[str(s) for s in data.get("suggestions", [])],
                related_metrics=related,
            )
        except (ValueError, json.JSONDecodeError) as e:
            logger.warning("Failed to parse Claude response as JSON: %s\n%s", e, text)
            return AnalysisReport(
                summary=f"이상 탐지: {', '.join(v.metric.label for v in violations)}",
                diagnosis="AI 응답 파싱 실패 — 원문 첨부",
                related_metrics=related,
                raw_text=text.strip()[:1500],
            )


def _extract_json(text: str) -> str:
    """텍스트에서 첫 번째 JSON 객체를 추출한다."""
    text = text.strip()
    # ```json ... ``` 형식
    if "```" in text:
        parts = text.split("```")
        for part in parts:
            stripped = part.strip()
            if stripped.startswith("json"):
                stripped = stripped[4:].strip()
            if stripped.startswith("{"):
                return stripped
    # 그냥 { ... }
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        return text[start : end + 1]
    raise ValueError("no JSON object found")
