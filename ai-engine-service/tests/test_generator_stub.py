import unittest
from uuid import uuid4

from app.models.events import AiGenerationRequestEvent, TemplateCategory
from app.services.generator import StubContentGenerator


class StubGeneratorTests(unittest.IsolatedAsyncioTestCase):
    async def test_generate_click_only_has_no_landing_page(self) -> None:
        generator = StubContentGenerator()
        request = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            prompt="policy update",
            templateCategory=TemplateCategory.CLICK_ONLY,
            languageCode="EN",
        )

        result = await generator.generate(request)
        self.assertIn("{{phishing_link}}", result.body_html)
        self.assertEqual(result.landing_page_code, "")

    async def test_generate_malware_landing_contains_download_action_and_signature_params(self) -> None:
        generator = StubContentGenerator()
        request = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            prompt="invoice",
            templateCategory=TemplateCategory.MALWARE_DELIVERY,
            languageCode="EN",
        )

        landing = await generator.generate_landing_page(request, "Invoice Subject")
        self.assertIn("/track/download", landing.landing_page_code)
        self.assertIn("exp", landing.landing_page_code)
        self.assertIn("sig", landing.landing_page_code)

    async def test_generate_credential_landing_contains_submit_action_and_signature_params(self) -> None:
        generator = StubContentGenerator()
        request = AiGenerationRequestEvent(
            templateId=uuid4(),
            companyId=uuid4(),
            prompt="signin",
            templateCategory=TemplateCategory.CREDENTIAL_HARVESTING,
            languageCode="TR",
        )

        landing = await generator.generate_landing_page(request, "Subject")
        self.assertIn("/track/submit", landing.landing_page_code)
        self.assertIn("exp", landing.landing_page_code)
        self.assertIn("sig", landing.landing_page_code)


if __name__ == "__main__":
    unittest.main()
