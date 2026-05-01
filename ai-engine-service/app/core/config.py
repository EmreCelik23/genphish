from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = Field(default="ai-engine-service", alias="APP_NAME")
    app_env: Literal["local", "dev", "staging", "prod"] = Field(default="local", alias="APP_ENV")
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")

    api_host: str = Field(default="0.0.0.0", alias="API_HOST")
    api_port: int = Field(default=5000, alias="API_PORT")
    service_auth_enabled: bool = Field(default=True, alias="SERVICE_AUTH_ENABLED")
    service_token: str = Field(default="genphish-internal-token", alias="SERVICE_AUTH_TOKEN")
    service_token_header: str = Field(default="X-Service-Token", alias="SERVICE_TOKEN_HEADER")
    service_token_header_aliases: str = Field(default="", alias="SERVICE_TOKEN_HEADER_ALIASES")
    company_header: str = Field(default="X-Company-Id", alias="COMPANY_HEADER")
    company_header_aliases: str = Field(default="", alias="COMPANY_HEADER_ALIASES")
    fail_on_unsafe_defaults: bool = Field(default=True, alias="FAIL_ON_UNSAFE_DEFAULTS")

    mongo_uri: str = Field(
        default="mongodb://localhost:27017",
        validation_alias=AliasChoices("MONGODB_URI", "MONGO_URI"),
    )
    mongo_db_name: str = Field(default="genphish_ai", alias="MONGODB_DB")
    mongo_collection_templates: str = Field(default="templates", alias="MONGODB_COLLECTION_TEMPLATES")
    mongo_collection_fallback_templates: str = Field(
        default="fallback_templates",
        alias="MONGODB_COLLECTION_FALLBACK_TEMPLATES",
    )

    kafka_enabled: bool = Field(default=True, alias="KAFKA_ENABLED")
    kafka_bootstrap_servers: str = Field(
        default="localhost:9092",
        validation_alias=AliasChoices("KAFKA_BOOTSTRAP_SERVERS", "KAFKA_BROKERS"),
    )
    kafka_group_id: str = Field(default="ai-engine-service-group", alias="KAFKA_GROUP_ID")
    kafka_client_id: str = Field(default="ai-engine-service", alias="KAFKA_CLIENT_ID")
    kafka_auto_offset_reset: Literal["earliest", "latest"] = Field(default="earliest", alias="KAFKA_AUTO_OFFSET_RESET")

    topic_ai_generation_requests: str = Field(default="ai_generation_requests", alias="TOPIC_AI_GENERATION_REQUESTS")
    topic_ai_generation_responses: str = Field(default="ai_generation_responses", alias="TOPIC_AI_GENERATION_RESPONSES")

    ai_provider: str = Field(default="stub", alias="AI_PROVIDER")
    openai_api_key: str | None = Field(default=None, alias="OPENAI_API_KEY")
    openai_model: str = Field(default="gpt-4o-mini", alias="OPENAI_MODEL")
    openai_temperature: float = Field(default=0.4, alias="OPENAI_TEMPERATURE")

    anthropic_api_key: str | None = Field(default=None, alias="ANTHROPIC_API_KEY")
    anthropic_model: str = Field(default="claude-3-5-sonnet-latest", alias="ANTHROPIC_MODEL")
    anthropic_temperature: float = Field(default=0.4, alias="ANTHROPIC_TEMPERATURE")

    google_api_key: str | None = Field(default=None, alias="GOOGLE_API_KEY")
    gemini_model: str = Field(default="gemini-1.5-pro", alias="GEMINI_MODEL")
    gemini_temperature: float = Field(default=0.4, alias="GEMINI_TEMPERATURE")
    generation_timeout_seconds: float = Field(default=30.0, alias="GENERATION_TIMEOUT_SECONDS")

    prompt_dir: Path = Field(default=Path("app/prompts"), alias="PROMPT_DIR")

    @property
    def kafka_bootstrap_server_list(self) -> list[str]:
        return [item.strip() for item in self.kafka_bootstrap_servers.split(",") if item.strip()]

    @property
    def service_token_header_name_list(self) -> list[str]:
        return _merge_header_names(self.service_token_header, self.service_token_header_aliases)

    @property
    def company_header_name_list(self) -> list[str]:
        return _merge_header_names(self.company_header, self.company_header_aliases)


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()


def validate_runtime_settings(settings: Settings) -> None:
    if settings.app_env != "prod" or not settings.fail_on_unsafe_defaults:
        return

    failures: list[str] = []
    if not settings.service_auth_enabled:
        failures.append("SERVICE_AUTH_ENABLED must be true in prod.")

    token = (settings.service_token or "").strip()
    if not token:
        failures.append("SERVICE_AUTH_TOKEN cannot be blank in prod.")
    if token in {"genphish-internal-token", "genphish-dev-token"}:
        failures.append("SERVICE_AUTH_TOKEN cannot use default development value.")
    if len(token) < 32:
        failures.append("SERVICE_AUTH_TOKEN must be at least 32 characters in prod.")

    if settings.ai_provider.strip().lower() == "stub":
        failures.append("AI_PROVIDER cannot be 'stub' in prod.")

    if not settings.service_token_header_name_list:
        failures.append("At least one service token header name must be configured.")
    if not settings.company_header_name_list:
        failures.append("At least one company header name must be configured.")

    if failures:
        raise RuntimeError("AI engine production config validation failed: " + " ".join(failures))


def _merge_header_names(primary: str, aliases_csv: str) -> list[str]:
    names = []
    normalized_primary = (primary or "").strip()
    if normalized_primary:
        names.append(normalized_primary)

    aliases = [item.strip() for item in (aliases_csv or "").split(",") if item.strip()]
    names.extend(aliases)
    # preserve order, remove duplicates
    return list(dict.fromkeys(names))
