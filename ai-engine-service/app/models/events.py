from datetime import datetime, timezone
from enum import Enum, StrEnum
from uuid import UUID

from pydantic import AliasChoices, BaseModel, ConfigDict, Field, field_validator


class RegenerationScope(str, Enum):
    ALL = "ALL"
    ONLY_EMAIL = "ONLY_EMAIL"
    ONLY_LANDING_PAGE = "ONLY_LANDING_PAGE"


class AiGenerationStatus(str, Enum):
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"


class LanguageCode(StrEnum):
    TR = "TR"
    EN = "EN"


class TemplateCategory(str, Enum):
    CREDENTIAL_HARVESTING = "CREDENTIAL_HARVESTING"
    CLICK_ONLY = "CLICK_ONLY"
    MALWARE_DELIVERY = "MALWARE_DELIVERY"
    OAUTH_CONSENT = "OAUTH_CONSENT"


def normalize_language_code(value: str | LanguageCode | None) -> LanguageCode:
    if isinstance(value, LanguageCode):
        return value
    if value is None:
        return LanguageCode.TR
    normalized = str(value).strip().lower()
    if normalized in {"tr", "tr-tr", "turkish", "turkce", "türkçe"}:
        return LanguageCode.TR
    if normalized in {"en", "en-us", "en-gb", "english", "ingilizce"}:
        return LanguageCode.EN
    return LanguageCode.TR


def normalize_difficulty_level(value: str | None) -> str:
    if value is None:
        return "PROFESSIONAL"
    normalized = str(value).strip().upper()
    return normalized or "PROFESSIONAL"


def normalize_regeneration_scope(value: str | RegenerationScope | None) -> str | RegenerationScope:
    if value is None:
        return RegenerationScope.ALL
    if isinstance(value, str) and not value.strip():
        return RegenerationScope.ALL
    return value


def normalize_optional_text(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip()
    return normalized or None


def normalize_template_category(value: str | TemplateCategory | None) -> TemplateCategory:
    if isinstance(value, TemplateCategory):
        return value
    if value is None:
        return TemplateCategory.CREDENTIAL_HARVESTING
    normalized = str(value).strip().upper()
    if not normalized:
        return TemplateCategory.CREDENTIAL_HARVESTING
    try:
        return TemplateCategory(normalized)
    except ValueError:
        return TemplateCategory.CREDENTIAL_HARVESTING


class AiGenerationRequestEvent(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    template_id: UUID = Field(alias="templateId")
    company_id: UUID = Field(alias="companyId")
    prompt: str | None = None
    target_url: str | None = Field(default=None, alias="targetUrl")
    reference_image_url: str | None = Field(default=None, alias="referenceImageUrl")
    template_category: TemplateCategory = Field(default=TemplateCategory.CREDENTIAL_HARVESTING, alias="templateCategory")
    difficulty_level: str = Field(default="PROFESSIONAL", alias="difficultyLevel")
    regeneration_scope: RegenerationScope = Field(default=RegenerationScope.ALL, alias="regenerationScope")
    existing_mongo_template_id: str | None = Field(default=None, alias="existingMongoTemplateId")
    language_code: LanguageCode = Field(
        default=LanguageCode.TR,
        alias="languageCode",
        validation_alias=AliasChoices("languageCode", "language"),
    )
    provider: str | None = Field(
        default=None,
        alias="provider",
        validation_alias=AliasChoices("provider", "aiProvider", "modelProvider"),
    )
    model_name: str | None = Field(
        default=None,
        alias="model",
        validation_alias=AliasChoices("model", "modelName", "llmModel"),
    )
    allow_fallback_template: bool = Field(default=False, alias="allowFallbackTemplate")

    @field_validator("difficulty_level", mode="before")
    @classmethod
    def normalize_difficulty_level(cls, value: str | None) -> str:
        return normalize_difficulty_level(value)

    @field_validator("regeneration_scope", mode="before")
    @classmethod
    def normalize_regeneration_scope(cls, value: str | RegenerationScope | None) -> str | RegenerationScope:
        return normalize_regeneration_scope(value)

    @field_validator("language_code", mode="before")
    @classmethod
    def normalize_language_code(cls, value: str | LanguageCode | None) -> LanguageCode:
        return normalize_language_code(value)

    @field_validator("template_category", mode="before")
    @classmethod
    def normalize_template_category(cls, value: str | TemplateCategory | None) -> TemplateCategory:
        return normalize_template_category(value)

    @field_validator("provider", "model_name", "reference_image_url", mode="before")
    @classmethod
    def normalize_optional_text(cls, value: str | None) -> str | None:
        return normalize_optional_text(value)


class AiGenerationResponseEvent(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    template_id: UUID = Field(alias="templateId")
    mongo_template_id: str | None = Field(default=None, alias="mongoTemplateId")
    status: AiGenerationStatus
    error_message: str | None = Field(default=None, alias="errorMessage")
    fallback_used: bool = Field(default=False, alias="fallbackUsed")


class ManualGenerateRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    template_id: UUID = Field(alias="templateId")
    company_id: UUID = Field(alias="companyId")
    prompt: str
    target_url: str | None = Field(default=None, alias="targetUrl")
    reference_image_url: str | None = Field(default=None, alias="referenceImageUrl")
    template_category: TemplateCategory = Field(default=TemplateCategory.CREDENTIAL_HARVESTING, alias="templateCategory")
    difficulty_level: str = Field(default="PROFESSIONAL", alias="difficultyLevel")
    regeneration_scope: RegenerationScope = Field(default=RegenerationScope.ALL, alias="regenerationScope")
    existing_mongo_template_id: str | None = Field(default=None, alias="existingMongoTemplateId")
    language_code: LanguageCode = Field(
        default=LanguageCode.TR,
        alias="languageCode",
        validation_alias=AliasChoices("languageCode", "language"),
    )
    provider: str | None = Field(
        default=None,
        alias="provider",
        validation_alias=AliasChoices("provider", "aiProvider", "modelProvider"),
    )
    model_name: str | None = Field(
        default=None,
        alias="model",
        validation_alias=AliasChoices("model", "modelName", "llmModel"),
    )
    allow_fallback_template: bool = Field(default=False, alias="allowFallbackTemplate")

    @field_validator("difficulty_level", mode="before")
    @classmethod
    def normalize_manual_difficulty_level(cls, value: str | None) -> str:
        return normalize_difficulty_level(value)

    @field_validator("regeneration_scope", mode="before")
    @classmethod
    def normalize_manual_regeneration_scope(cls, value: str | RegenerationScope | None) -> str | RegenerationScope:
        return normalize_regeneration_scope(value)

    @field_validator("language_code", mode="before")
    @classmethod
    def normalize_manual_language_code(cls, value: str | LanguageCode | None) -> LanguageCode:
        return normalize_language_code(value)

    @field_validator("template_category", mode="before")
    @classmethod
    def normalize_manual_template_category(cls, value: str | TemplateCategory | None) -> TemplateCategory:
        return normalize_template_category(value)

    @field_validator("provider", "model_name", "reference_image_url", mode="before")
    @classmethod
    def normalize_manual_optional_text(cls, value: str | None) -> str | None:
        return normalize_optional_text(value)


class CloneTemplateRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    template_id: UUID = Field(alias="templateId")
    company_id: UUID = Field(alias="companyId")


class TemplateCreatedResponse(BaseModel):
    template_id: UUID = Field(alias="templateId")
    mongo_template_id: str | None = Field(default=None, alias="mongoTemplateId")
    status: str
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), alias="createdAt")


class ErrorResponse(BaseModel):
    message: str
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
