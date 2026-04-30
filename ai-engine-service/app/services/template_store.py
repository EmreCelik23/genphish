from datetime import datetime, timezone
import json

from bson import ObjectId
from motor.motor_asyncio import AsyncIOMotorCollection

from app.models.events import LanguageCode
from app.models.template import StoredTemplateView, TemplateDocument
from app.services.generator import GeneratedTemplateParts
from app.services.utils import infer_department_code


class TemplateStore:
    def __init__(
        self,
        collection: AsyncIOMotorCollection,
        fallback_collection: AsyncIOMotorCollection | None = None,
    ) -> None:
        self._collection = collection
        self._fallback_collection = fallback_collection

    async def create(self, template: TemplateDocument) -> str:
        payload = template.to_mongo()
        now = datetime.now(timezone.utc)
        payload["createdAt"] = now
        payload["updatedAt"] = now

        result = await self._collection.insert_one(payload)
        return str(result.inserted_id)

    async def update(self, template_id: str, template: TemplateDocument) -> str:
        object_id = ObjectId(template_id)
        payload = template.to_mongo()
        payload.pop("createdAt", None)
        payload["updatedAt"] = datetime.now(timezone.utc)

        await self._collection.update_one({"_id": object_id}, {"$set": payload}, upsert=False)
        return template_id

    async def get(self, template_id: str) -> StoredTemplateView | None:
        if not ObjectId.is_valid(template_id):
            return None

        raw = await self._collection.find_one({"_id": ObjectId(template_id)})
        if not raw:
            return None

        raw["id"] = str(raw["_id"])
        raw.pop("_id", None)
        return StoredTemplateView.model_validate(raw)

    async def get_raw_json_for_campaign_fallback(self, template_id: str) -> str | None:
        stored = await self.get(template_id)
        if not stored:
            return None

        payload = {
            "subject": stored.subject,
            "bodyHtml": stored.body_html,
            "landingPageCode": stored.landing_page_code,
        }
        return json.dumps(payload, ensure_ascii=False)

    async def get_static_fallback_parts(
        self,
        difficulty_level: str,
        language_code: LanguageCode | str,
        prompt: str | None,
    ) -> GeneratedTemplateParts | None:
        if self._fallback_collection is None:
            return None

        normalized_difficulty = (difficulty_level or "AMATEUR").strip().upper()
        if isinstance(language_code, LanguageCode):
            normalized_language = language_code.value
        else:
            normalized_language = str(language_code or LanguageCode.TR.value).strip().upper()
        department_hint = infer_department_code(prompt)

        department_candidates = [department_hint, "GENERAL"]
        query = {
            "isActive": True,
            "$or": [
                {"difficultyLevel": normalized_difficulty},
                {"difficultyLevel": {"$exists": False}},
            ],
            "$and": [
                {
                    "$or": [
                        {"languageCode": normalized_language},
                        {"languageCode": {"$exists": False}},
                    ]
                },
                {
                    "$or": [
                        {"departmentHint": {"$in": department_candidates}},
                        {"departmentHint": {"$exists": False}},
                    ]
                }
            ],
        }

        raw = await self._fallback_collection.find_one(
            query,
            sort=[("priority", -1), ("updatedAt", -1), ("createdAt", -1)],
        )
        if raw is None:
            return None

        subject = raw.get("subject")
        body_html = raw.get("bodyHtml")
        landing_page_code = raw.get("landingPageCode")
        if not all(isinstance(item, str) and item.strip() for item in (subject, body_html, landing_page_code)):
            return None

        model_id = str(raw.get("_id", "static-fallback"))
        return GeneratedTemplateParts(
            subject=subject.strip(),
            body_html=body_html.strip(),
            landing_page_code=landing_page_code.strip(),
            provider="fallback-db",
            model=f"mongo:{model_id}",
        )
