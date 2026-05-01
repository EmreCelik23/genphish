import asyncio
import unittest
from copy import deepcopy
from datetime import datetime, timezone
from uuid import uuid4

from bson import ObjectId

from app.models.events import LanguageCode
from app.models.template import TemplateDocument
from app.services.template_store import TemplateStore


class FakeInsertResult:
    def __init__(self, inserted_id):
        self.inserted_id = inserted_id


class FakeCollection:
    def __init__(self):
        self.docs = {}

    async def insert_one(self, payload):
        await asyncio.sleep(0)
        doc = deepcopy(payload)
        inserted_id = ObjectId()
        doc["_id"] = inserted_id
        self.docs[inserted_id] = doc
        return FakeInsertResult(inserted_id)

    async def update_one(self, query, update, upsert=False):
        del upsert
        await asyncio.sleep(0)
        doc_id = query["_id"]
        if doc_id in self.docs:
            self.docs[doc_id].update(deepcopy(update.get("$set", {})))
        return None

    async def find_one(self, query):
        await asyncio.sleep(0)
        doc_id = query.get("_id")
        if isinstance(doc_id, ObjectId):
            doc = self.docs.get(doc_id)
            return deepcopy(doc) if doc else None
        return None


class FakeFallbackCollection:
    def __init__(self, doc):
        self.doc = doc

    async def find_one(self, query, sort=None):
        del query
        del sort
        await asyncio.sleep(0)
        return deepcopy(self.doc)


class TemplateStoreTests(unittest.IsolatedAsyncioTestCase):
    async def test_create_get_update_and_raw_json(self) -> None:
        collection = FakeCollection()
        store = TemplateStore(collection=collection, fallback_collection=None)
        template_uuid = uuid4()
        company_uuid = uuid4()

        template = TemplateDocument(
            templateId=template_uuid,
            companyId=company_uuid,
            prompt="p",
            targetUrl="https://example.com",
            languageCode=LanguageCode.EN,
            subject="Subject",
            bodyHtml="<p>Body</p>",
            landingPageCode="landing-code",
            llmProvider="stub",
            llmModel="stub-model",
        )

        mongo_id = await store.create(template)
        self.assertTrue(ObjectId.is_valid(mongo_id))

        stored = await store.get(mongo_id)
        self.assertIsNotNone(stored)
        assert stored is not None
        self.assertEqual(stored.template_id, template_uuid)
        self.assertEqual(stored.company_id, company_uuid)

        template.subject = "Updated Subject"
        await store.update(mongo_id, template)
        updated = await store.get(mongo_id)
        self.assertIsNotNone(updated)
        assert updated is not None
        self.assertEqual(updated.subject, "Updated Subject")

        raw_json = await store.get_raw_json_for_campaign_fallback(mongo_id)
        self.assertIn("\"subject\": \"Updated Subject\"", raw_json)
        self.assertIn("\"landingPageCode\": \"landing-code\"", raw_json)

    async def test_clone_returns_none_for_invalid_id(self) -> None:
        collection = FakeCollection()
        store = TemplateStore(collection=collection, fallback_collection=None)

        result = await store.clone("invalid-id", uuid4(), uuid4())
        self.assertIsNone(result)

    async def test_clone_creates_new_document_for_valid_id(self) -> None:
        collection = FakeCollection()
        store = TemplateStore(collection=collection, fallback_collection=None)

        template = TemplateDocument(
            templateId=uuid4(),
            companyId=uuid4(),
            subject="Subject",
            bodyHtml="<p>Body</p>",
            landingPageCode="landing-code",
            llmProvider="stub",
            llmModel="stub-model",
            createdAt=datetime.now(timezone.utc),
            updatedAt=datetime.now(timezone.utc),
        )
        source_id = await store.create(template)
        cloned_id = await store.clone(source_id, uuid4(), uuid4())
        self.assertIsNotNone(cloned_id)
        self.assertTrue(ObjectId.is_valid(cloned_id))
        self.assertNotEqual(source_id, cloned_id)

    async def test_get_static_fallback_parts(self) -> None:
        fallback_doc = {
            "_id": ObjectId(),
            "subject": "Fallback Subject",
            "bodyHtml": "<p>Fallback Body</p>",
            "landingPageCode": "fallback landing",
            "isActive": True,
        }
        store = TemplateStore(
            collection=FakeCollection(),
            fallback_collection=FakeFallbackCollection(fallback_doc),
        )

        parts = await store.get_static_fallback_parts(
            difficulty_level="PROFESSIONAL",
            language_code=LanguageCode.EN,
            prompt="finance invoice",
        )
        self.assertIsNotNone(parts)
        assert parts is not None
        self.assertEqual(parts.subject, "Fallback Subject")
        self.assertEqual(parts.provider, "fallback-db")

    async def test_get_static_fallback_parts_returns_none_when_invalid_shape(self) -> None:
        invalid_doc = {
            "_id": ObjectId(),
            "subject": "",
            "bodyHtml": "<p>Body</p>",
            "landingPageCode": "landing",
            "isActive": True,
        }
        store = TemplateStore(
            collection=FakeCollection(),
            fallback_collection=FakeFallbackCollection(invalid_doc),
        )

        parts = await store.get_static_fallback_parts(
            difficulty_level="AMATEUR",
            language_code="TR",
            prompt=None,
        )
        self.assertIsNone(parts)


if __name__ == "__main__":
    unittest.main()
