from datetime import datetime, timezone
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.models.events import LanguageCode


class TemplateDocument(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str | None = Field(default=None, alias="id")
    campaign_id: UUID = Field(alias="campaignId")
    company_id: UUID = Field(alias="companyId")
    prompt: str = ""
    target_url: str = ""
    difficulty_level: str = Field(default="PROFESSIONAL", alias="difficultyLevel")
    language_code: LanguageCode = Field(default=LanguageCode.TR, alias="languageCode")

    subject: str
    body_html: str = Field(alias="bodyHtml")
    landing_page_code: str = Field(alias="landingPageCode")

    llm_provider: str = Field(alias="llmProvider")
    llm_model: str = Field(alias="llmModel")

    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), alias="createdAt")
    updated_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), alias="updatedAt")

    def to_mongo(self) -> dict[str, Any]:
        payload = self.model_dump(by_alias=True, mode="json")
        payload.pop("id", None)
        return payload


class StoredTemplateView(BaseModel):
    id: str = Field(alias="id")
    campaign_id: UUID = Field(alias="campaignId")
    company_id: UUID = Field(alias="companyId")
    language_code: LanguageCode = Field(default=LanguageCode.TR, alias="languageCode")
    subject: str
    body_html: str = Field(alias="bodyHtml")
    landing_page_code: str = Field(alias="landingPageCode")
    created_at: datetime = Field(alias="createdAt")
    updated_at: datetime = Field(alias="updatedAt")
