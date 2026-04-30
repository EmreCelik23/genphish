import unittest
from datetime import datetime, timezone
from uuid import uuid4

from app.models.events import AiGenerationRequestEvent, LanguageCode, RegenerationScope
from app.models.template import StoredTemplateView
from app.services.generation_service import GenerationService
from app.services.generator import GeneratedEmailParts, GeneratedLandingPageParts, GeneratedTemplateParts


class FakeGenerator:
    def __init__(self) -> None:
        self.full_calls = 0
        self.email_calls = 0
        self.landing_calls = 0
        self.last_landing_subject = None

    async def generate(self, request: AiGenerationRequestEvent) -> GeneratedTemplateParts:
        self.full_calls += 1
        return GeneratedTemplateParts(
            subject="generated-subject",
            body_html=(
                "<html><body>"
                "<p>Hello {{name}},</p>"
                "<p>{{department}}</p>"
                "<p><a href=\"{{phishing_link}}\">Open</a></p>"
                "</body></html>"
            ),
            landing_page_code="const action='/track/submit?c=1&e=2&co=3';",
            provider="stub",
            model="stub-model",
        )

    async def generate_email(self, request: AiGenerationRequestEvent) -> GeneratedEmailParts:
        self.email_calls += 1
        return GeneratedEmailParts(
            subject="generated-subject",
            body_html=(
                "<html><body>"
                "<p>Hello {{name}},</p>"
                "<p>{{department}}</p>"
                "<p><a href=\"{{phishing_link}}\">Open</a></p>"
                "</body></html>"
            ),
            provider="stub",
            model="stub-model",
        )

    async def generate_landing_page(
        self,
        request: AiGenerationRequestEvent,
        email_subject: str,
    ) -> GeneratedLandingPageParts:
        self.landing_calls += 1
        self.last_landing_subject = email_subject
        return GeneratedLandingPageParts(
            landing_page_code="const action='/track/submit?c=1&e=2&co=3';",
            provider="stub",
            model="stub-model",
        )


class FailingGenerator:
    async def generate(self, request: AiGenerationRequestEvent) -> GeneratedTemplateParts:
        raise RuntimeError("LLM backend unavailable")

    async def generate_email(self, request: AiGenerationRequestEvent) -> GeneratedEmailParts:
        raise RuntimeError("LLM backend unavailable")

    async def generate_landing_page(
        self,
        request: AiGenerationRequestEvent,
        email_subject: str,
    ) -> GeneratedLandingPageParts:
        del email_subject
        raise RuntimeError("LLM backend unavailable")


class NonCompliantGenerator:
    async def generate(self, request: AiGenerationRequestEvent) -> GeneratedTemplateParts:
        return GeneratedTemplateParts(
            subject="",
            body_html="Security notice only",
            landing_page_code="export default function Page() { return null; }",
            provider="stub",
            model="stub-model",
        )

    async def generate_email(self, request: AiGenerationRequestEvent) -> GeneratedEmailParts:
        return GeneratedEmailParts(
            subject="",
            body_html="Security notice only",
            provider="stub",
            model="stub-model",
        )

    async def generate_landing_page(
        self,
        request: AiGenerationRequestEvent,
        email_subject: str,
    ) -> GeneratedLandingPageParts:
        del email_subject
        return GeneratedLandingPageParts(
            landing_page_code="export default function Page() { return null; }",
            provider="stub",
            model="stub-model",
        )


class FakeTemplateStore:
    def __init__(
        self,
        existing: StoredTemplateView | None = None,
        fallback_parts: GeneratedTemplateParts | None = None,
    ) -> None:
        self._existing = existing
        self._fallback_parts = fallback_parts
        self.created_payload = None
        self.updated_payload = None

    async def get(self, template_id: str) -> StoredTemplateView | None:
        return self._existing if self._existing and self._existing.id == template_id else None

    async def create(self, template) -> str:
        self.created_payload = template
        return "new-template-id"

    async def update(self, template_id: str, template) -> str:
        self.updated_payload = (template_id, template)
        return template_id

    async def get_static_fallback_parts(
        self,
        difficulty_level: str,
        language_code: LanguageCode | str,
        prompt: str | None,
    ) -> GeneratedTemplateParts | None:
        del difficulty_level
        del language_code
        del prompt
        return self._fallback_parts


class GenerationServiceTests(unittest.IsolatedAsyncioTestCase):
    async def test_create_when_existing_template_is_missing(self) -> None:
        store = FakeTemplateStore(existing=None)
        generator = FakeGenerator()
        service = GenerationService(generator=generator, template_store=store)
        request = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            prompt="IT sifre yenileme",
            targetUrl="https://example.com",
            difficultyLevel="AMATEUR",
            regenerationScope=RegenerationScope.ALL,
        )

        result = await service.generate_and_store(request)

        self.assertEqual(result.template_id, "new-template-id")
        self.assertFalse(result.fallback_used)
        self.assertIsNotNone(store.created_payload)
        self.assertIsNone(store.updated_payload)
        self.assertEqual(generator.full_calls, 1)
        self.assertEqual(generator.email_calls, 0)
        self.assertEqual(generator.landing_calls, 0)

    async def test_only_email_scope_keeps_existing_landing_page(self) -> None:
        existing_id = "507f1f77bcf86cd799439011"
        existing = StoredTemplateView(
            id=existing_id,
            templateId=uuid4(),
            companyId=uuid4(),
            subject="existing-subject",
            bodyHtml="<p>existing-body</p>",
            landingPageCode="existing-landing",
            createdAt=datetime.now(timezone.utc),
            updatedAt=datetime.now(timezone.utc),
        )
        store = FakeTemplateStore(existing=existing)
        generator = FakeGenerator()
        service = GenerationService(generator=generator, template_store=store)
        request = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            prompt="Finans odakli senaryo",
            difficultyLevel="PROFESSIONAL",
            regenerationScope=RegenerationScope.ONLY_EMAIL,
            existingMongoTemplateId=existing_id,
        )

        result = await service.generate_and_store(request)

        self.assertEqual(result.template_id, existing_id)
        self.assertFalse(result.fallback_used)
        self.assertIsNone(store.created_payload)
        self.assertIsNotNone(store.updated_payload)
        _, updated_template = store.updated_payload
        self.assertEqual(updated_template.subject, "generated-subject")
        self.assertIn("{{name}}", updated_template.body_html)
        self.assertIn("{{department}}", updated_template.body_html)
        self.assertIn("{{phishing_link}}", updated_template.body_html)
        self.assertEqual(updated_template.landing_page_code, "existing-landing")
        self.assertEqual(generator.full_calls, 0)
        self.assertEqual(generator.email_calls, 1)
        self.assertEqual(generator.landing_calls, 0)

    async def test_only_landing_scope_keeps_existing_email(self) -> None:
        existing_id = "507f1f77bcf86cd799439012"
        existing = StoredTemplateView(
            id=existing_id,
            templateId=uuid4(),
            companyId=uuid4(),
            subject="existing-subject",
            bodyHtml="<p>existing-body</p>",
            landingPageCode="existing-landing",
            createdAt=datetime.now(timezone.utc),
            updatedAt=datetime.now(timezone.utc),
        )
        store = FakeTemplateStore(existing=existing)
        generator = FakeGenerator()
        service = GenerationService(generator=generator, template_store=store)
        request = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            prompt="IK odakli senaryo",
            difficultyLevel="AMATEUR",
            regenerationScope=RegenerationScope.ONLY_LANDING_PAGE,
            existingMongoTemplateId=existing_id,
        )

        result = await service.generate_and_store(request)

        self.assertEqual(result.template_id, existing_id)
        self.assertFalse(result.fallback_used)
        self.assertIsNone(store.created_payload)
        self.assertIsNotNone(store.updated_payload)
        _, updated_template = store.updated_payload
        self.assertEqual(updated_template.subject, "existing-subject")
        self.assertEqual(updated_template.body_html, "<p>existing-body</p>")
        self.assertEqual(updated_template.landing_page_code, "const action='/track/submit?c=1&e=2&co=3';")
        self.assertEqual(generator.full_calls, 0)
        self.assertEqual(generator.email_calls, 0)
        self.assertEqual(generator.landing_calls, 1)
        self.assertEqual(generator.last_landing_subject, "existing-subject")

    async def test_circuit_breaker_uses_db_fallback_on_generator_error(self) -> None:
        fallback = GeneratedTemplateParts(
            subject="fallback-subject",
            body_html="<p>fallback-body</p>",
            landing_page_code="fallback-landing",
            provider="fallback-db",
            model="mongo:fallback-1",
        )
        store = FakeTemplateStore(existing=None, fallback_parts=fallback)
        service = GenerationService(generator=FailingGenerator(), template_store=store)
        request = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            prompt="IT reset senaryosu",
            difficultyLevel="AMATEUR",
            regenerationScope=RegenerationScope.ALL,
            allowFallbackTemplate=True,
        )

        result = await service.generate_and_store(request)

        self.assertEqual(result.template_id, "new-template-id")
        self.assertTrue(result.fallback_used)
        self.assertIsNotNone(store.created_payload)
        self.assertEqual(store.created_payload.subject, "fallback-subject")
        self.assertIn("fallback-body", store.created_payload.body_html)
        self.assertIn("{{name}}", store.created_payload.body_html)
        self.assertIn("{{department}}", store.created_payload.body_html)
        self.assertIn("{{phishing_link}}", store.created_payload.body_html)
        self.assertIn("/track/submit", store.created_payload.landing_page_code)

    async def test_circuit_breaker_uses_builtin_fallback_when_db_fallback_missing(self) -> None:
        store = FakeTemplateStore(existing=None, fallback_parts=None)
        service = GenerationService(generator=FailingGenerator(), template_store=store)
        request = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            prompt="IT reset senaryosu",
            difficultyLevel="AMATEUR",
            regenerationScope=RegenerationScope.ALL,
            languageCode="EN",
            allowFallbackTemplate=True,
        )

        result = await service.generate_and_store(request)

        self.assertEqual(result.template_id, "new-template-id")
        self.assertTrue(result.fallback_used)
        self.assertIsNotNone(store.created_payload)
        self.assertIn("usePathname", store.created_payload.landing_page_code)
        self.assertIn("lang ? `&lang=${lang}` : ''", store.created_payload.landing_page_code)

    async def test_enforces_template_contract_for_non_compliant_llm_output(self) -> None:
        store = FakeTemplateStore(existing=None, fallback_parts=None)
        service = GenerationService(generator=NonCompliantGenerator(), template_store=store)
        request = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            prompt="Basit senaryo",
            difficultyLevel="AMATEUR",
            regenerationScope=RegenerationScope.ALL,
            languageCode="EN",
        )

        result = await service.generate_and_store(request)

        self.assertEqual(result.template_id, "new-template-id")
        self.assertFalse(result.fallback_used)
        self.assertIsNotNone(store.created_payload)
        self.assertEqual(store.created_payload.subject, "Amateur Security Verification")
        self.assertIn("{{name}}", store.created_payload.body_html)
        self.assertIn("{{department}}", store.created_payload.body_html)
        self.assertIn("{{phishing_link}}", store.created_payload.body_html)
        self.assertIn("/track/submit", store.created_payload.landing_page_code)

    async def test_generation_raises_when_fallback_disabled(self) -> None:
        store = FakeTemplateStore(existing=None, fallback_parts=None)
        service = GenerationService(generator=FailingGenerator(), template_store=store)
        request = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            prompt="IT reset senaryosu",
            difficultyLevel="AMATEUR",
            regenerationScope=RegenerationScope.ALL,
            allowFallbackTemplate=False,
        )

        with self.assertRaises(RuntimeError):
            await service.generate_and_store(request)


if __name__ == "__main__":
    unittest.main()
