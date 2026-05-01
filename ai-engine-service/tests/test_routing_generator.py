import unittest
from unittest.mock import patch
from uuid import uuid4

from app.core.config import Settings
from app.models.events import AiGenerationRequestEvent, LanguageCode, TemplateCategory
from app.services.generator import (
    LandingGenerationOutput,
    RoutingContentGenerator,
)


class StaticPromptLoader:
    def load(self, _: str) -> str:
        return "prompt"


class FakeStructuredModel:
    def __init__(self, landing_code: str = "generated-landing") -> None:
        self.landing_code = landing_code
        self.invocations: list[object] = []

    async def ainvoke(self, payload):
        self.invocations.append(payload)
        return LandingGenerationOutput(landing_page_code=self.landing_code)


class FakeChatModel:
    def __init__(self, structured_model: FakeStructuredModel) -> None:
        self.structured_model = structured_model

    def with_structured_output(self, _: object) -> FakeStructuredModel:
        return self.structured_model


class FakePromptTemplate:
    def __or__(self, other):
        return other


class RoutingGeneratorTests(unittest.IsolatedAsyncioTestCase):
    def _make_settings(self, **kwargs) -> Settings:
        payload = {
            "AI_PROVIDER": "stub",
            "SERVICE_AUTH_TOKEN": "abcdefghijklmnopqrstuvwxyz123456",
            "MONGODB_URI": "mongodb://localhost:27017",
        }
        payload.update(kwargs)
        return Settings(**payload)

    def _make_request(self, **kwargs) -> AiGenerationRequestEvent:
        payload = {
            "templateId": uuid4(),
            "companyId": uuid4(),
            "prompt": "Quarterly policy verification",
            "templateCategory": TemplateCategory.CREDENTIAL_HARVESTING,
            "languageCode": "TR",
        }
        payload.update(kwargs)
        return AiGenerationRequestEvent(**payload)

    async def test_generate_uses_stub_provider_by_default(self) -> None:
        generator = RoutingContentGenerator(self._make_settings(AI_PROVIDER="stub"), StaticPromptLoader())
        request = self._make_request(templateCategory=TemplateCategory.CLICK_ONLY)

        result = await generator.generate(request)

        self.assertEqual(result.provider, "stub")
        self.assertEqual(result.model, "stub-generator-v1")
        self.assertEqual(result.landing_page_code, "")

    async def test_generate_landing_page_short_circuits_for_click_only_non_stub_runtime(self) -> None:
        generator = RoutingContentGenerator(self._make_settings(AI_PROVIDER="stub"), StaticPromptLoader())
        request = self._make_request(templateCategory=TemplateCategory.CLICK_ONLY)

        with patch.object(generator, "_resolve_runtime", return_value=("openai", "gpt-4o-mini", object())):
            landing = await generator.generate_landing_page(request, "subject")

        self.assertEqual(landing.provider, "openai")
        self.assertEqual(landing.model, "gpt-4o-mini")
        self.assertEqual(landing.landing_page_code, "")

    async def test_generate_landing_falls_back_to_text_only_when_multimodal_raises(self) -> None:
        generator = RoutingContentGenerator(self._make_settings(AI_PROVIDER="stub"), StaticPromptLoader())
        request = self._make_request(referenceImageUrl="https://example.com/ref.png")
        structured_model = FakeStructuredModel(landing_code="fallback-landing")
        fake_chat_model = FakeChatModel(structured_model)

        with patch.object(generator, "_generate_landing_with_reference_image", side_effect=RuntimeError("boom")):
            with patch("app.services.generator.ChatPromptTemplate.from_messages", return_value=FakePromptTemplate()):
                result = await generator._generate_landing(
                    chat_model=fake_chat_model,
                    request=request,
                    email_subject="subject",
                    provider="openai",
                )

        self.assertEqual(result.landing_page_code, "fallback-landing")
        self.assertEqual(len(structured_model.invocations), 1)
        payload = structured_model.invocations[0]
        self.assertEqual(payload["landing_action_endpoint"], "/track/submit")

    async def test_generate_landing_with_reference_image_uses_provider_specific_image_block(self) -> None:
        generator = RoutingContentGenerator(self._make_settings(AI_PROVIDER="stub"), StaticPromptLoader())
        request = self._make_request(
            templateCategory=TemplateCategory.MALWARE_DELIVERY,
            referenceImageUrl="https://example.com/ref.png",
            languageCode=LanguageCode.EN,
        )
        structured_model = FakeStructuredModel(landing_code="image-landing")
        fake_chat_model = FakeChatModel(structured_model)

        result = await generator._generate_landing_with_reference_image(
            chat_model=fake_chat_model,
            request=request,
            email_subject="subject",
            provider="anthropic",
        )

        self.assertEqual(result.landing_page_code, "image-landing")
        self.assertEqual(len(structured_model.invocations), 1)
        messages = structured_model.invocations[0]
        image_block = messages[1].content[1]
        self.assertEqual(image_block["type"], "image")
        self.assertEqual(image_block["source_type"], "url")
        self.assertEqual(image_block["url"], "https://example.com/ref.png")

    def test_resolve_model_name_and_runtime_errors(self) -> None:
        generator = RoutingContentGenerator(self._make_settings(AI_PROVIDER="stub"), StaticPromptLoader())
        request = self._make_request(provider="stub")
        provider, model_name, chat_model = generator._resolve_runtime(request)
        self.assertEqual((provider, model_name, chat_model), ("stub", "stub-generator-v1", None))

        self.assertEqual(generator._resolve_model_name("openai", "custom-model"), "custom-model")
        self.assertEqual(generator._resolve_model_name("openai", None), generator._settings.openai_model)
        self.assertEqual(generator._resolve_model_name("anthropic", None), generator._settings.anthropic_model)
        self.assertEqual(generator._resolve_model_name("gemini", None), generator._settings.gemini_model)
        with self.assertRaises(ValueError):
            generator._resolve_model_name("unknown", None)

    def test_get_chat_model_validation_errors(self) -> None:
        generator = RoutingContentGenerator(
            self._make_settings(AI_PROVIDER="stub", OPENAI_API_KEY=None),
            StaticPromptLoader(),
        )

        with self.assertRaises(ValueError):
            generator._get_chat_model("openai", "gpt-4o-mini")
        with self.assertRaises(ValueError):
            generator._get_chat_model("unsupported", "x")


if __name__ == "__main__":
    unittest.main()
