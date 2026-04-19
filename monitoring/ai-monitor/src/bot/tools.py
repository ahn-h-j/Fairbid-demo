"""Claude tool_use 도구 정의.

비서 Bot이 질문 받았을 때 Claude가 스스로 호출할 도구들.
각 도구는 (1) Anthropic tool schema + (2) 실행 함수를 제공한다.

도구:
- query_prometheus      : 실시간 PromQL 쿼리
- get_recent_alerts     : 최근 N시간 알람 이력
- get_alert_report      : 특정 알람의 저장된 Claude 분석
- get_metric_range      : 특정 메트릭 시계열 (start/end 범위)
"""
from __future__ import annotations

import logging
import time
from typing import Any

import requests

from ..state import StateStore

logger = logging.getLogger(__name__)


# === Anthropic tool schema ===

TOOL_SCHEMAS: list[dict] = [
    {
        "name": "query_prometheus",
        "description": (
            "Prometheus에 instant PromQL 쿼리를 날려 현재 메트릭 값을 조회한다. "
            "현재 상태 스냅샷이 필요할 때 사용. "
            "예시 쿼리: 'system_cpu_usage{application=\"fairbid\"}' 또는 "
            "'sum(rate(http_server_requests_seconds_count[5m]))'"
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "promql": {"type": "string", "description": "실행할 PromQL 쿼리"},
            },
            "required": ["promql"],
        },
    },
    {
        "name": "get_recent_alerts",
        "description": (
            "최근 N시간 동안 발송된 알람 이력을 시간 역순으로 반환한다. "
            "'어제 저녁 뭐 있었어?' 같은 질문을 받으면 먼저 이 도구로 이벤트 목록을 얻는다. "
            "반환: id, rule_key, severity, kind(new/escalation/persistence/recovery), value, fired_at."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "hours": {
                    "type": "integer",
                    "description": "몇 시간치 이력을 가져올지. 어제 저녁~오늘이면 24, 지난주면 168",
                },
            },
            "required": ["hours"],
        },
    },
    {
        "name": "get_alert_report",
        "description": (
            "특정 알람(history_id)에 대해 당시 Claude가 작성한 원인 분석 리포트를 꺼낸다. "
            "재추론하지 말고 **이 도구로 원본 판단을 인용**해라. "
            "신규/악화 이벤트만 리포트를 갖는다(지속/해소는 None). "
            "반환: summary, diagnosis, causal_chain, evidence, suggestions, related_metrics."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "history_id": {
                    "type": "integer",
                    "description": "get_recent_alerts로 얻은 id",
                },
            },
            "required": ["history_id"],
        },
    },
    {
        "name": "get_metric_range",
        "description": (
            "특정 메트릭의 시간 범위 시계열 데이터를 반환한다 (Prometheus range 쿼리). "
            "'어제 밤에 CPU가 어떻게 변했어?' 같이 추세를 묻는 질문에 사용. "
            "start/end는 unix timestamp, step은 '30s' 또는 '1m' 같은 duration 문자열."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "promql": {"type": "string", "description": "쿼리 대상 PromQL"},
                "start_ts": {"type": "integer", "description": "시작 unix timestamp"},
                "end_ts": {"type": "integer", "description": "끝 unix timestamp"},
                "step": {
                    "type": "string",
                    "description": "샘플 간격 (예: '30s', '1m', '5m')",
                    "default": "1m",
                },
            },
            "required": ["promql", "start_ts", "end_ts"],
        },
    },
]


# === 실행 함수 ===

class ToolExecutor:
    """Claude tool_use 응답을 실제 함수 호출로 디스패치."""

    def __init__(self, prometheus_url: str, store: StateStore, timeout: int = 10):
        self.prometheus_url = prometheus_url.rstrip("/")
        self.store = store
        self.timeout = timeout

    def execute(self, tool_name: str, tool_input: dict[str, Any]) -> Any:
        """도구 실행. Claude가 보낸 tool_use 블록에 대한 응답 생성."""
        try:
            if tool_name == "query_prometheus":
                return self._query_prometheus(tool_input["promql"])
            if tool_name == "get_recent_alerts":
                return self._get_recent_alerts(int(tool_input["hours"]))
            if tool_name == "get_alert_report":
                return self._get_alert_report(int(tool_input["history_id"]))
            if tool_name == "get_metric_range":
                return self._get_metric_range(
                    tool_input["promql"],
                    int(tool_input["start_ts"]),
                    int(tool_input["end_ts"]),
                    tool_input.get("step", "1m"),
                )
            return {"error": f"unknown tool: {tool_name}"}
        except Exception as e:
            logger.exception("tool %s failed: %s", tool_name, e)
            return {"error": f"{type(e).__name__}: {e}"}

    # --- tool impls ---

    def _query_prometheus(self, promql: str) -> dict:
        url = f"{self.prometheus_url}/api/v1/query"
        resp = requests.get(url, params={"query": promql}, timeout=self.timeout)
        resp.raise_for_status()
        data = resp.json()
        if data.get("status") != "success":
            return {"error": "non-success response", "raw": data}
        result = data.get("data", {}).get("result", [])
        # 간결하게 요약 — 시리즈가 많으면 상위 10개만
        return {
            "result_type": data.get("data", {}).get("resultType"),
            "count": len(result),
            "series": [
                {"metric": r.get("metric", {}), "value": r.get("value")}
                for r in result[:10]
            ],
        }

    def _get_recent_alerts(self, hours: int) -> list[dict]:
        hours = max(1, min(hours, 24 * 30))  # 1h ~ 30d
        return self.store.list_recent_history(hours=hours, limit=100)

    def _get_alert_report(self, history_id: int) -> dict | None:
        return self.store.get_report_by_history_id(history_id)

    def _get_metric_range(
        self, promql: str, start_ts: int, end_ts: int, step: str
    ) -> dict:
        url = f"{self.prometheus_url}/api/v1/query_range"
        resp = requests.get(
            url,
            params={"query": promql, "start": start_ts, "end": end_ts, "step": step},
            timeout=self.timeout,
        )
        resp.raise_for_status()
        data = resp.json()
        result = data.get("data", {}).get("result", [])
        if not result:
            return {"count": 0, "values": []}
        # 첫 시리즈만 (여러 시리즈 반환 시 간결성 위해)
        values = result[0].get("values", [])
        # 샘플이 많으면 앞뒤 각 20개만 전달 (Claude 컨텍스트 절약)
        if len(values) > 40:
            values = values[:20] + [("...", "truncated")] + values[-20:]
        return {"count": len(values), "values": values}
