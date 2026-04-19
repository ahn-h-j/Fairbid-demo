"""1차 룰 기반 이상 탐지 필터.

config.yaml의 임계치를 단순 비교만 한다. 정상이면 빈 리스트를 반환하여
이후 단계(Claude 호출)를 건너뛸 수 있게 한다 — 비용 절감 핵심.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass

from .config import MetricDef
from .prometheus import QueryResult

logger = logging.getLogger(__name__)


@dataclass
class Violation:
    metric: MetricDef
    value: float

    @property
    def key(self) -> str:
        return self.metric.key


def evaluate(
    metrics: list[MetricDef], results: dict[str, QueryResult]
) -> list[Violation]:
    """모든 메트릭을 임계치와 비교해 위반 목록을 반환한다.

    observe_only 메트릭은 임계치 비교에서 제외된다.
    optional 메트릭은 값이 None이어도 경고만 남기고 넘어간다.
    """
    violations: list[Violation] = []

    for metric in metrics:
        result = results.get(metric.key)
        if result is None or result.value is None:
            if not metric.optional:
                logger.warning("metric %s has no value", metric.key)
            continue

        if metric.observe_only:
            continue

        if _exceeds_threshold(result.value, metric.threshold, metric.comparator):
            violations.append(Violation(metric=metric, value=result.value))
            logger.info(
                "VIOLATION %s: value=%.4f threshold=%.4f comparator=%s",
                metric.key, result.value, metric.threshold, metric.comparator,
            )

    return violations


def _exceeds_threshold(value: float, threshold: float, comparator: str) -> bool:
    if comparator == "gt":
        return value > threshold
    if comparator == "lt":
        return value < threshold
    if comparator == "abs_gt":
        return abs(value) > threshold
    logger.warning("unknown comparator: %s", comparator)
    return False
