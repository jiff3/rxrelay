from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = "rxrelay-ingestion"
    environment: Literal["development", "test", "production"] = "development"
    manual_ingestion_enabled: bool = True
    openfda_base_url: str = "https://api.fda.gov/drug/shortages.json"
    openfda_api_key: str | None = None
    rxnorm_base_url: str = "https://rxnav.nlm.nih.gov"
    rxnorm_enabled: bool = True
    rxnorm_cache_ttl_seconds: int = Field(default=86400, ge=60, le=604800)
    rxnorm_cache_max_entries: int = Field(default=2000, ge=10, le=10000)
    kafka_bootstrap_servers: str = "localhost:9092"
    shortage_topic: str = "rxrelay.shortage.observed.v1"
    ingestion_max_records: int = Field(default=2000, ge=1, le=5000)
    http_timeout_seconds: float = Field(default=15.0, gt=0, le=60)
    http_retry_attempts: int = Field(default=3, ge=1, le=6)
    http_retry_base_seconds: float = Field(default=0.25, ge=0, le=10)
    http_retry_max_seconds: float = Field(default=4.0, ge=0, le=30)
