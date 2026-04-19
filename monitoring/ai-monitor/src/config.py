"""설정 로딩 모듈.

config.yaml + 환경변수를 합쳐 단일 Settings 객체로 노출한다.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml


@dataclass
class MetricDef:
    """단일 메트릭 정의 (config.yaml의 metrics[] 항목과 1:1 대응)."""

    key: str
    label: str
    promql: str
    threshold: float
    comparator: str  # gt | lt | abs_gt
    severity: str    # critical | warning | info
    unit: str = ""
    observe_only: bool = False  # True면 임계치 비교 안 하고 컨텍스트 메트릭으로만 사용
    optional: bool = False      # True면 메트릭 부재 시 무시 (redis-exporter 등)


@dataclass
class EvolveConfig:
    enabled: bool = True
    day: str = "monday"       # monday/tuesday/.../sunday
    time: str = "09:00"       # 로컬 타임존 (컨테이너 TZ 기준)
    lookback_days: int = 7


@dataclass
class NightReportConfig:
    """야간 브리핑 설정.

    전일 'from' 시각부터 당일 'to' 시각까지의 알람 이력을 집계해서
    'to' 시각에 발송한다. 매일 출근 시각에 밤사이 이상을 브리핑 받는 용도.
    """

    enabled: bool = True
    from_time: str = "19:00"  # 전일 시각 (퇴근 기준)
    to_time: str = "09:00"    # 당일 시각 (출근 기준, 발송 시각과 동일)


@dataclass
class RuntimeConfig:
    check_interval_seconds: int = 30
    # severity → cooldown 분. 알 수 없는 severity는 30분.
    cooldown_minutes: dict[str, int] = field(
        default_factory=lambda: {"critical": 5, "warning": 30, "info": 60}
    )
    escalation_threshold: float = 0.20
    send_recovery_notice: bool = True
    night_report: NightReportConfig = field(default_factory=NightReportConfig)
    evolve: EvolveConfig = field(default_factory=EvolveConfig)
    claude_model: str = "claude-sonnet-4-5-20250929"
    prometheus_timeout_seconds: int = 10

    def cooldown_for(self, severity: str) -> int:
        """severity별 cooldown 분을 반환. 매핑 없으면 30분 fallback."""
        return self.cooldown_minutes.get(severity, 30)


@dataclass
class Settings:
    runtime: RuntimeConfig
    metrics: list[MetricDef]

    # 환경변수에서 주입되는 값들
    prometheus_url: str = "http://prometheus:9090"
    claude_api_key: str = ""
    discord_bot_token: str = ""
    discord_channel_id: int = 0
    state_db_path: str = "/data/state.db"
    log_level: str = "INFO"


def load_settings(config_path: str | Path | None = None) -> Settings:
    """config.yaml + 환경변수에서 설정을 로딩한다."""
    if config_path is None:
        config_path = Path(__file__).resolve().parent.parent / "config.yaml"
    config_path = Path(config_path)

    with config_path.open(encoding="utf-8") as f:
        raw: dict[str, Any] = yaml.safe_load(f)

    runtime_raw = dict(raw.get("runtime", {}))
    if "evolve" in runtime_raw and isinstance(runtime_raw["evolve"], dict):
        runtime_raw["evolve"] = EvolveConfig(**runtime_raw["evolve"])
    if "night_report" in runtime_raw and isinstance(runtime_raw["night_report"], dict):
        runtime_raw["night_report"] = NightReportConfig(**runtime_raw["night_report"])
    runtime = RuntimeConfig(**runtime_raw)
    metrics = [MetricDef(**m) for m in raw.get("metrics", [])]

    channel_id_raw = os.environ.get("DISCORD_CHANNEL_ID", "0")
    try:
        channel_id = int(channel_id_raw)
    except ValueError:
        channel_id = 0

    return Settings(
        runtime=runtime,
        metrics=metrics,
        prometheus_url=os.environ.get("PROMETHEUS_URL", "http://prometheus:9090"),
        claude_api_key=os.environ.get("CLAUDE_API_KEY", ""),
        discord_bot_token=os.environ.get("DISCORD_BOT_TOKEN", ""),
        discord_channel_id=channel_id,
        state_db_path=os.environ.get("STATE_DB_PATH", "/data/state.db"),
        log_level=os.environ.get("LOG_LEVEL", "INFO"),
    )
