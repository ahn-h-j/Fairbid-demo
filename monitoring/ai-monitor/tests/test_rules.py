"""rules.evaluate 단위 테스트."""
from __future__ import annotations

from src.config import MetricDef
from src.prometheus import QueryResult
from src.rules import evaluate


def _make_metric(**overrides) -> MetricDef:
    base = dict(
        key="test",
        label="Test",
        promql="up",
        threshold=1.0,
        comparator="gt",
        severity="warning",
    )
    base.update(overrides)
    return MetricDef(**base)


def test_gt_violation():
    metric = _make_metric(threshold=0.5, comparator="gt")
    results = {"test": QueryResult(metric_key="test", value=0.7)}
    violations = evaluate([metric], results)
    assert len(violations) == 1
    assert violations[0].value == 0.7


def test_gt_no_violation_at_boundary():
    """경계값(value == threshold)은 위반 아님."""
    metric = _make_metric(threshold=0.5, comparator="gt")
    results = {"test": QueryResult(metric_key="test", value=0.5)}
    assert evaluate([metric], results) == []


def test_lt_violation():
    metric = _make_metric(threshold=10.0, comparator="lt")
    results = {"test": QueryResult(metric_key="test", value=5.0)}
    assert len(evaluate([metric], results)) == 1


def test_abs_gt_violation_negative():
    """abs_gt: 음수도 절댓값으로 비교."""
    metric = _make_metric(threshold=100.0, comparator="abs_gt")
    results = {"test": QueryResult(metric_key="test", value=-150.0)}
    assert len(evaluate([metric], results)) == 1


def test_abs_gt_no_violation():
    metric = _make_metric(threshold=100.0, comparator="abs_gt")
    results = {"test": QueryResult(metric_key="test", value=-50.0)}
    assert evaluate([metric], results) == []


def test_observe_only_skipped():
    """observe_only=True 메트릭은 임계치 비교에서 제외된다."""
    metric = _make_metric(threshold=0.5, comparator="gt", observe_only=True)
    results = {"test": QueryResult(metric_key="test", value=999.0)}
    assert evaluate([metric], results) == []


def test_none_value_skipped():
    """value=None (데이터 없음) 메트릭은 위반에서 제외된다."""
    metric = _make_metric()
    results = {"test": QueryResult(metric_key="test", value=None)}
    assert evaluate([metric], results) == []


def test_multiple_violations():
    m1 = _make_metric(key="a", threshold=0.5, comparator="gt")
    m2 = _make_metric(key="b", threshold=10.0, comparator="lt")
    results = {
        "a": QueryResult(metric_key="a", value=0.9),
        "b": QueryResult(metric_key="b", value=5.0),
    }
    violations = evaluate([m1, m2], results)
    assert {v.key for v in violations} == {"a", "b"}


def test_unknown_comparator_returns_no_violation():
    metric = _make_metric(comparator="weird")
    results = {"test": QueryResult(metric_key="test", value=999.0)}
    assert evaluate([metric], results) == []
