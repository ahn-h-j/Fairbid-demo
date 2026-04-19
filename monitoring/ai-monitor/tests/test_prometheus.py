"""Prometheus 클라이언트 응답 파싱 테스트."""
from __future__ import annotations

from unittest.mock import patch

from src.prometheus import PrometheusClient


def _mock_response(json_payload: dict, status: int = 200):
    class MockResp:
        status_code = status

        def json(self):
            return json_payload

        def raise_for_status(self):
            if status >= 400:
                raise RuntimeError(f"HTTP {status}")

    return MockResp()


def test_parse_vector_result():
    payload = {
        "status": "success",
        "data": {
            "resultType": "vector",
            "result": [{"metric": {}, "value": [1700000000, "0.42"]}],
        },
    }
    with patch("src.prometheus.requests.get", return_value=_mock_response(payload)):
        client = PrometheusClient("http://prom")
        result = client.query("test", "up")
    assert result.value == 0.42


def test_parse_empty_result():
    payload = {"status": "success", "data": {"resultType": "vector", "result": []}}
    with patch("src.prometheus.requests.get", return_value=_mock_response(payload)):
        client = PrometheusClient("http://prom")
        result = client.query("test", "up")
    assert result.value is None


def test_parse_nan_result():
    """0/0 같은 식의 NaN은 0으로 변환된다 (트래픽 0인 비율 = 0%로 해석)."""
    payload = {
        "status": "success",
        "data": {
            "resultType": "vector",
            "result": [{"metric": {}, "value": [1700000000, "NaN"]}],
        },
    }
    with patch("src.prometheus.requests.get", return_value=_mock_response(payload)):
        client = PrometheusClient("http://prom")
        result = client.query("test", "up")
    assert result.value == 0.0


def test_query_failure_returns_none():
    """HTTP 에러 시 value=None을 반환해야 한다 (스크립트가 죽지 않도록)."""
    import requests as req

    def raise_error(*args, **kwargs):
        raise req.ConnectionError("boom")

    with patch("src.prometheus.requests.get", side_effect=raise_error):
        client = PrometheusClient("http://prom")
        result = client.query("test", "up")
    assert result.value is None
