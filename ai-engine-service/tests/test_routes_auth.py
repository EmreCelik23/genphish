import os
import unittest
from types import SimpleNamespace
from uuid import uuid4

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api import routes
from app.core.config import get_settings


class FakeGenerationService:
    async def generate_and_store(self, request_event):
        del request_event
        return SimpleNamespace(template_id="mongo-template-1")


class FakeTemplateStore:
    async def get_raw_json_for_campaign_fallback(self, template_id: str):
        if template_id == "missing":
            return None
        return '{"subject":"s","bodyHtml":"b","landingPageCode":"l"}'

    async def clone(self, mongo_template_id, template_id, company_id):
        del template_id
        del company_id
        if mongo_template_id == "missing":
            return None
        return "cloned-template-1"


class RoutesAuthTests(unittest.TestCase):
    def setUp(self) -> None:
        os.environ["SERVICE_AUTH_ENABLED"] = "true"
        os.environ["SERVICE_AUTH_TOKEN"] = "test-service-token"
        os.environ["SERVICE_TOKEN_HEADER"] = "X-Service-Token"
        os.environ["SERVICE_TOKEN_HEADER_ALIASES"] = ""
        os.environ["COMPANY_HEADER"] = "X-Company-Id"
        os.environ["COMPANY_HEADER_ALIASES"] = ""
        get_settings.cache_clear()

        app = FastAPI()
        app.include_router(routes.router)
        app.dependency_overrides[routes.get_generation_service] = lambda: FakeGenerationService()
        app.dependency_overrides[routes.get_template_store] = lambda: FakeTemplateStore()
        self.client = TestClient(app)

    def tearDown(self) -> None:
        get_settings.cache_clear()

    def test_generate_rejects_missing_auth(self) -> None:
        payload = {
            "templateId": str(uuid4()),
            "companyId": str(uuid4()),
            "prompt": "test prompt",
        }

        response = self.client.post("/api/generate", json=payload)
        self.assertEqual(response.status_code, 401)

    def test_generate_rejects_missing_company_header(self) -> None:
        company_id = str(uuid4())
        payload = {
            "templateId": str(uuid4()),
            "companyId": company_id,
            "prompt": "test prompt",
        }

        response = self.client.post(
            "/api/generate",
            json=payload,
            headers={"Authorization": "Bearer test-service-token"},
        )
        self.assertEqual(response.status_code, 403)

    def test_generate_rejects_company_mismatch(self) -> None:
        payload = {
            "templateId": str(uuid4()),
            "companyId": str(uuid4()),
            "prompt": "test prompt",
        }

        response = self.client.post(
            "/api/generate",
            json=payload,
            headers={
                "Authorization": "Bearer test-service-token",
                "X-Company-Id": str(uuid4()),
            },
        )
        self.assertEqual(response.status_code, 403)

    def test_generate_accepts_valid_service_and_company_headers(self) -> None:
        company_id = str(uuid4())
        payload = {
            "templateId": str(uuid4()),
            "companyId": company_id,
            "prompt": "test prompt",
        }

        response = self.client.post(
            "/api/generate",
            json=payload,
            headers={
                "Authorization": "Bearer test-service-token",
                "X-Company-Id": company_id,
            },
        )
        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "SUCCESS")
        self.assertEqual(body["mongoTemplateId"], "mongo-template-1")

    def test_get_template_requires_auth(self) -> None:
        response = self.client.get("/api/templates/abc123")
        self.assertEqual(response.status_code, 401)

    def test_get_template_accepts_service_header_auth(self) -> None:
        response = self.client.get(
            "/api/templates/abc123",
            headers={"X-Service-Token": "test-service-token"},
        )
        self.assertEqual(response.status_code, 200)

    def test_generate_accepts_alias_headers_when_configured(self) -> None:
        os.environ["SERVICE_TOKEN_HEADER_ALIASES"] = "X-Internal-Token"
        os.environ["COMPANY_HEADER_ALIASES"] = "X-Tenant-Id"
        get_settings.cache_clear()

        app = FastAPI()
        app.include_router(routes.router)
        app.dependency_overrides[routes.get_generation_service] = lambda: FakeGenerationService()
        app.dependency_overrides[routes.get_template_store] = lambda: FakeTemplateStore()
        client = TestClient(app)

        company_id = str(uuid4())
        payload = {
            "templateId": str(uuid4()),
            "companyId": company_id,
            "prompt": "test prompt",
        }

        response = client.post(
            "/api/generate",
            json=payload,
            headers={
                "X-Internal-Token": "test-service-token",
                "X-Tenant-Id": company_id,
            },
        )
        self.assertEqual(response.status_code, 200)


if __name__ == "__main__":
    unittest.main()
