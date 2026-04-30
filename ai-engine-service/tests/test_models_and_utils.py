import unittest
from uuid import uuid4

from app.models.events import (
    AiGenerationRequestEvent,
    LanguageCode,
    normalize_language_code,
)
from app.models.template import TemplateDocument
from app.services.generator import RoutingContentGenerator


class ModelsAndUtilsTests(unittest.TestCase):
    def test_language_normalization_aliases(self) -> None:
        self.assertEqual(normalize_language_code("en-us"), LanguageCode.EN)
        self.assertEqual(normalize_language_code("türkçe"), LanguageCode.TR)
        self.assertEqual(normalize_language_code("unknown"), LanguageCode.TR)

    def test_request_event_parses_language_alias(self) -> None:
        event = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            language="english",
        )
        self.assertEqual(event.language_code, LanguageCode.EN)
        dumped = event.model_dump(by_alias=True, mode="json")
        self.assertEqual(dumped["languageCode"], "EN")

    def test_request_event_defaults_to_professional_and_tr(self) -> None:
        event = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
        )
        self.assertEqual(event.difficulty_level, "PROFESSIONAL")
        self.assertEqual(event.language_code, LanguageCode.TR)

    def test_template_to_mongo_serializes_uuid_and_enum_values(self) -> None:
        template_id = uuid4()
        company_id = uuid4()
        template = TemplateDocument(
            templateId=template_id,
            companyId=company_id,
            languageCode=LanguageCode.TR,
            subject="Subject",
            bodyHtml="<p>Hello</p>",
            landingPageCode="export default function Page() { return null; }",
            llmProvider="stub",
            llmModel="stub-generator-v1",
        )
        payload = template.to_mongo()
        self.assertEqual(payload["templateId"], str(template_id))
        self.assertEqual(payload["companyId"], str(company_id))
        self.assertEqual(payload["languageCode"], "TR")

    def test_provider_alias_mapping(self) -> None:
        self.assertEqual(RoutingContentGenerator._normalize_provider("google"), "gemini")
        self.assertEqual(RoutingContentGenerator._normalize_provider("claude"), "anthropic")
        self.assertEqual(RoutingContentGenerator._normalize_provider("chatgpt"), "openai")


if __name__ == "__main__":
    unittest.main()
