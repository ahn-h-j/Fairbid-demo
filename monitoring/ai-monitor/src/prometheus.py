"""Prometheus HTTP API 클라이언트.

PromQL 쿼리 실행 및 결과 파싱.
참고: https://prometheus.io/docs/prometheus/latest/querying/api/
"""
from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

import requests

logger = logging.getLogger(__name__)


@dataclass
class QueryResult:
    """단일 PromQL 쿼리의 결과.

    instant 쿼리는 단일 스칼라 값을 기대하지만, vector 결과인 경우
    여러 시리즈가 올 수 있어 첫 시리즈만 보관한다.
    """

    metric_key: str
    value: float | None  # None이면 데이터 없음
    raw: dict[str, Any] | None = None


class PrometheusClient:
    def __init__(self, base_url: str, timeout: int = 10):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def query(self, metric_key: str, promql: str) -> QueryResult:
        """instant 쿼리를 실행한다.

        - vector 결과: 첫 시리즈의 value[1] 사용
        - scalar 결과: value[1] 사용
        - 빈 결과: value=None 반환
        """
        url = f"{self.base_url}/api/v1/query"
        try:
            resp = requests.get(url, params={"query": promql}, timeout=self.timeout)
            resp.raise_for_status()
            data = resp.json()
        except requests.RequestException as e:
            logger.error("Prometheus query failed: %s — %s", metric_key, e)
            return QueryResult(metric_key=metric_key, value=None)

        if data.get("status") != "success":
            logger.warning("Prometheus query non-success: %s — %s", metric_key, data)
            return QueryResult(metric_key=metric_key, value=None)

        result = data.get("data", {}).get("result", [])
        result_type = data.get("data", {}).get("resultType", "")

        value: float | None = None
        if result_type == "vector" and result:
            # vector: [{"metric":{...},"value":[ts, "1.23"]}, ...]
            try:
                raw = result[0]["value"][1]
                # NaN: Prometheus가 0/0 같은 식(트래픽 0인 비율)에서 돌려줌 → 0으로 처리.
                # 메트릭 자체가 부재한 케이스(empty vector)와는 구분된다.
                if raw == "NaN":
                    value = 0.0
                else:
                    value = float(raw)
            except (KeyError, IndexError, ValueError, TypeError):
                value = None
        elif result_type == "scalar" and result:
            try:
                value = float(result[1])
            except (IndexError, ValueError, TypeError):
                value = None

        # Inf 거르기 (NaN은 위에서 0으로 처리됨)
        if value is not None and value in (float("inf"), float("-inf")):
            value = None

        return QueryResult(metric_key=metric_key, value=value, raw=data)

