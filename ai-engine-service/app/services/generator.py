import logging
from dataclasses import dataclass
from typing import Any, Protocol

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field

from app.core.config import Settings
from app.models.events import AiGenerationRequestEvent, LanguageCode, TemplateCategory
from app.services.prompt_loader import PromptLoader
from app.services.utils import infer_department_label

try:
    from langchain_anthropic import ChatAnthropic
except Exception:  # pragma: no cover - optional dependency
    ChatAnthropic = None

try:
    from langchain_google_genai import ChatGoogleGenerativeAI
except Exception:  # pragma: no cover - optional dependency
    ChatGoogleGenerativeAI = None


class EmailGenerationOutput(BaseModel):
    subject: str = Field(description="Generated phishing simulation email subject")
    body_html: str = Field(description="Generated phishing simulation email HTML body")


class LandingGenerationOutput(BaseModel):
    landing_page_code: str = Field(description="Generated Next.js TSX page code")


@dataclass(slots=True)
class GeneratedEmailParts:
    subject: str
    body_html: str
    provider: str
    model: str


@dataclass(slots=True)
class GeneratedLandingPageParts:
    landing_page_code: str
    provider: str
    model: str


@dataclass(slots=True)
class GeneratedTemplateParts:
    subject: str
    body_html: str
    landing_page_code: str
    provider: str
    model: str


class ContentGenerator(Protocol):
    async def generate(self, request: AiGenerationRequestEvent) -> GeneratedTemplateParts:
        ...

    async def generate_email(self, request: AiGenerationRequestEvent) -> GeneratedEmailParts:
        ...

    async def generate_landing_page(
        self,
        request: AiGenerationRequestEvent,
        email_subject: str,
    ) -> GeneratedLandingPageParts:
        ...


class RoutingContentGenerator:
    def __init__(self, settings: Settings, prompt_loader: PromptLoader) -> None:
        self._settings = settings
        self._default_provider = self._normalize_provider(settings.ai_provider)
        self._model_cache: dict[tuple[str, str], Any] = {}
        self._stub_generator = StubContentGenerator()
        self._logger = logging.getLogger(self.__class__.__name__)

        self._email_system_prompt = prompt_loader.load("email_system.txt")
        self._email_user_prompt = prompt_loader.load("email_user.txt")
        self._landing_system_prompt = prompt_loader.load("landing_system.txt")
        self._landing_user_prompt = prompt_loader.load("landing_user.txt")

    async def generate(self, request: AiGenerationRequestEvent) -> GeneratedTemplateParts:
        provider, model_name, chat_model = self._resolve_runtime(request)
        if provider == "stub":
            return await self._stub_generator.generate(request)

        assert chat_model is not None
        email_result = await self._generate_email(chat_model, request)
        if request.template_category in {TemplateCategory.CLICK_ONLY, TemplateCategory.OAUTH_CONSENT}:
            landing_result = LandingGenerationOutput(landing_page_code="")
        else:
            landing_result = await self._generate_landing(chat_model, request, email_result.subject, provider)

        return GeneratedTemplateParts(
            subject=email_result.subject,
            body_html=email_result.body_html,
            landing_page_code=landing_result.landing_page_code,
            provider=provider,
            model=model_name,
        )

    async def generate_email(self, request: AiGenerationRequestEvent) -> GeneratedEmailParts:
        provider, model_name, chat_model = self._resolve_runtime(request)
        if provider == "stub":
            return await self._stub_generator.generate_email(request)

        assert chat_model is not None
        email_result = await self._generate_email(chat_model, request)
        return GeneratedEmailParts(
            subject=email_result.subject,
            body_html=email_result.body_html,
            provider=provider,
            model=model_name,
        )

    async def generate_landing_page(
        self,
        request: AiGenerationRequestEvent,
        email_subject: str,
    ) -> GeneratedLandingPageParts:
        provider, model_name, chat_model = self._resolve_runtime(request)
        if provider == "stub":
            return await self._stub_generator.generate_landing_page(request, email_subject)
        if request.template_category in {TemplateCategory.CLICK_ONLY, TemplateCategory.OAUTH_CONSENT}:
            return GeneratedLandingPageParts(
                landing_page_code="",
                provider=provider,
                model=model_name,
            )

        assert chat_model is not None
        landing_result = await self._generate_landing(chat_model, request, email_subject, provider)
        return GeneratedLandingPageParts(
            landing_page_code=landing_result.landing_page_code,
            provider=provider,
            model=model_name,
        )

    def _resolve_runtime(self, request: AiGenerationRequestEvent) -> tuple[str, str, Any | None]:
        provider = self._normalize_provider(request.provider or self._default_provider)
        model_name = self._resolve_model_name(provider, request.model_name)
        if provider == "stub":
            return provider, model_name, None
        return provider, model_name, self._get_chat_model(provider, model_name)

    async def _generate_email(self, chat_model: Any, request: AiGenerationRequestEvent) -> EmailGenerationOutput:
        email_chain = ChatPromptTemplate.from_messages(
            [
                ("system", self._email_system_prompt),
                ("human", self._email_user_prompt),
            ]
        ) | chat_model.with_structured_output(EmailGenerationOutput)

        department_hint = infer_department_label(request.prompt)
        language_instruction = self._language_instruction(request.language_code)
        language_label = self._language_label(request.language_code)

        return await email_chain.ainvoke(
            {
                "department_hint": department_hint,
                "difficulty_level": request.difficulty_level,
                "prompt": request.prompt or "Corporate phishing awareness scenario",
                "target_url": request.target_url or "N/A",
                "template_category": request.template_category.value,
                "language_instruction": language_instruction,
                "language_label": language_label,
            }
        )

    async def _generate_landing(
        self,
        chat_model: Any,
        request: AiGenerationRequestEvent,
        email_subject: str,
        provider: str,
    ) -> LandingGenerationOutput:
        if request.reference_image_url and self._supports_multimodal(provider):
            try:
                return await self._generate_landing_with_reference_image(
                    chat_model=chat_model,
                    request=request,
                    email_subject=email_subject,
                    provider=provider,
                )
            except Exception:
                self._logger.warning(
                    "Multimodal landing generation failed for provider=%s, falling back to text-only prompt.",
                    provider,
                    exc_info=True,
                )
        elif request.reference_image_url:
            self._logger.warning(
                "Provider=%s does not support multimodal reference-image generation; using text-only prompt.",
                provider,
            )

        landing_chain = ChatPromptTemplate.from_messages(
            [
                ("system", self._landing_system_prompt),
                ("human", self._landing_user_prompt),
            ]
        ) | chat_model.with_structured_output(LandingGenerationOutput)

        language_instruction = self._language_instruction(request.language_code)
        language_label = self._language_label(request.language_code)

        return await landing_chain.ainvoke(
            {
                "difficulty_level": request.difficulty_level,
                "prompt": request.prompt or "Corporate phishing awareness scenario",
                "target_url": request.target_url or "N/A",
                "email_subject": email_subject,
                "template_category": request.template_category.value,
                "landing_action_endpoint": self._resolve_landing_action_endpoint(request.template_category),
                "language_instruction": language_instruction,
                "language_label": language_label,
            }
        )

    async def _generate_landing_with_reference_image(
        self,
        chat_model: Any,
        request: AiGenerationRequestEvent,
        email_subject: str,
        provider: str,
    ) -> LandingGenerationOutput:
        structured_model = chat_model.with_structured_output(LandingGenerationOutput)

        language_instruction = self._language_instruction(request.language_code)
        language_label = self._language_label(request.language_code)
        landing_action_endpoint = self._resolve_landing_action_endpoint(request.template_category)

        user_prompt = self._landing_user_prompt.format(
            difficulty_level=request.difficulty_level,
            prompt=request.prompt or "Corporate phishing awareness scenario",
            target_url=request.target_url or "N/A",
            email_subject=email_subject,
            template_category=request.template_category.value,
            landing_action_endpoint=landing_action_endpoint,
            language_label=language_label,
            language_instruction=language_instruction,
        )

        system_prompt = (
            self._landing_system_prompt
            + "\nWhen a reference image is attached, clone layout and visual hierarchy while keeping the simulation-safe tracking flow."
        )

        messages = [
            SystemMessage(content=system_prompt),
            HumanMessage(
                content=[
                    {"type": "text", "text": user_prompt},
                    self._build_reference_image_content(provider, request.reference_image_url),
                ]
            ),
        ]
        return await structured_model.ainvoke(messages)

    def _get_chat_model(self, provider: str, model_name: str) -> Any:
        cache_key = (provider, model_name)
        cached = self._model_cache.get(cache_key)
        if cached is not None:
            return cached

        if provider == "openai":
            if not self._settings.openai_api_key:
                raise ValueError("OPENAI_API_KEY is required when provider=openai")
            model = ChatOpenAI(
                api_key=self._settings.openai_api_key,
                model=model_name,
                temperature=self._settings.openai_temperature,
            )
        elif provider == "anthropic":
            if ChatAnthropic is None:
                raise ValueError("Provider 'anthropic' requires dependency: langchain-anthropic")
            kwargs: dict[str, Any] = {
                "model": model_name,
                "temperature": self._settings.anthropic_temperature,
            }
            if self._settings.anthropic_api_key:
                kwargs["api_key"] = self._settings.anthropic_api_key
            model = ChatAnthropic(**kwargs)
        elif provider == "gemini":
            if ChatGoogleGenerativeAI is None:
                raise ValueError("Provider 'gemini' requires dependency: langchain-google-genai")
            kwargs = {
                "model": model_name,
                "temperature": self._settings.gemini_temperature,
            }
            if self._settings.google_api_key:
                kwargs["api_key"] = self._settings.google_api_key
            model = ChatGoogleGenerativeAI(**kwargs)
        else:
            raise ValueError(f"Unsupported provider: {provider}")

        self._model_cache[cache_key] = model
        return model

    def _resolve_model_name(self, provider: str, model_override: str | None) -> str:
        if model_override:
            return model_override

        if provider == "openai":
            return self._settings.openai_model
        if provider == "anthropic":
            return self._settings.anthropic_model
        if provider == "gemini":
            return self._settings.gemini_model
        if provider == "stub":
            return "stub-generator-v1"
        raise ValueError(f"Unsupported provider for model resolution: {provider}")

    @staticmethod
    def _normalize_provider(value: str | None) -> str:
        normalized = (value or "stub").strip().lower()
        aliases = {
            "openai": "openai",
            "gpt": "openai",
            "chatgpt": "openai",
            "stub": "stub",
            "anthropic": "anthropic",
            "claude": "anthropic",
            "gemini": "gemini",
            "google": "gemini",
            "google-genai": "gemini",
        }
        return aliases.get(normalized, normalized)

    @staticmethod
    def _language_instruction(language_code: LanguageCode) -> str:
        if language_code == LanguageCode.EN:
            return "Write all user-visible content in English."
        return "Tüm kullanıcıya görünen içeriği Türkçe yaz."

    @staticmethod
    def _language_label(language_code: LanguageCode) -> str:
        return "English" if language_code == LanguageCode.EN else "Turkish"

    @staticmethod
    def _resolve_landing_action_endpoint(category: TemplateCategory) -> str:
        if category == TemplateCategory.MALWARE_DELIVERY:
            return "/track/download"
        return "/track/submit"

    @staticmethod
    def _supports_multimodal(provider: str) -> bool:
        return provider in {"openai", "anthropic", "gemini"}

    @staticmethod
    def _build_reference_image_content(provider: str, image_url: str) -> dict[str, Any]:
        if provider == "anthropic":
            return {
                "type": "image",
                "source_type": "url",
                "url": image_url,
            }
        return {
            "type": "image_url",
            "image_url": {"url": image_url},
        }


class StubContentGenerator:
    def __init__(self) -> None:
        self._model_name = "stub-generator-v1"

    async def generate(self, request: AiGenerationRequestEvent) -> GeneratedTemplateParts:
        email_parts = await self.generate_email(request)
        if request.template_category in {TemplateCategory.CLICK_ONLY, TemplateCategory.OAUTH_CONSENT}:
            landing_parts = GeneratedLandingPageParts(
                landing_page_code="",
                provider="stub",
                model=self._model_name,
            )
        else:
            landing_parts = await self.generate_landing_page(request, email_parts.subject)
        return GeneratedTemplateParts(
            subject=email_parts.subject,
            body_html=email_parts.body_html,
            landing_page_code=landing_parts.landing_page_code,
            provider="stub",
            model=self._model_name,
        )

    async def generate_email(self, request: AiGenerationRequestEvent) -> GeneratedEmailParts:
        if request.language_code == LanguageCode.EN:
            subject = f"{request.difficulty_level.title()} Security Verification Notice"
            body_html = (
                "<html><body>"
                "<p>Hello {{name}},</p>"
                "<p>A mandatory verification update is waiting for the {{department}} department.</p>"
                "<p>Please complete the verification step by "
                "<a href=\"{{phishing_link}}\">clicking here</a>.</p>"
                "<p>Thank you,<br/>IT Service Desk</p>"
                "</body></html>"
            )
        else:
            subject = f"{request.difficulty_level.title()} Guvenlik Dogrulama Bildirimi"
            body_html = (
                "<html><body>"
                "<p>Merhaba {{name}},</p>"
                "<p>{{department}} birimi icin zorunlu dogrulama guncellemesi bulunmaktadir.</p>"
                "<p>Lutfen guvenlik dogrulama adimini tamamlamak icin "
                "<a href=\"{{phishing_link}}\">buraya tiklayin</a>.</p>"
                "<p>Tesekkurler,<br/>IT Servis Masasi</p>"
                "</body></html>"
            )

        return GeneratedEmailParts(
            subject=subject,
            body_html=body_html,
            provider="stub",
            model=self._model_name,
        )

    async def generate_landing_page(
        self,
        request: AiGenerationRequestEvent,
        email_subject: str,
    ) -> GeneratedLandingPageParts:
        del email_subject

        if request.template_category == TemplateCategory.MALWARE_DELIVERY:
            if request.language_code == LanguageCode.EN:
                title = "Secure Document Center"
                description = "Your invoice is ready. Click below to download."
                button_text = "Download Invoice"
            else:
                title = "Guvenli Belge Merkezi"
                description = "Faturanız hazır. İndirmek için aşağıya tıklayın."
                button_text = "Faturayi Indir"

            landing_page_code = (
                "'use client';\n"
                "import { usePathname, useSearchParams } from 'next/navigation';\n"
                "\n"
                "export default function DownloadPortalPage() {\n"
                "  const query = useSearchParams();\n"
                "  const pathname = usePathname();\n"
                "  const pathParts = pathname.split('/').filter(Boolean);\n"
                "  const pathCampaignId = pathParts.length > 0 ? pathParts[pathParts.length - 1] : '';\n"
                "  const c = query.get('c') || pathCampaignId;\n"
                "  const e = query.get('e') || '';\n"
                "  const co = query.get('co') || '';\n"
                "  const lang = query.get('lang') || '';\n"
                "  const exp = query.get('exp') || '';\n"
                "  const sig = query.get('sig') || '';\n"
                "  const action = `/track/download?c=${c}&e=${e}&co=${co}${lang ? `&lang=${lang}` : ''}${exp ? `&exp=${encodeURIComponent(exp)}` : ''}${sig ? `&sig=${encodeURIComponent(sig)}` : ''}`;\n"
                "\n"
                "  return (\n"
                "    <main style={{ maxWidth: 460, margin: '64px auto', fontFamily: 'system-ui' }}>\n"
                f"      <h1>{title}</h1>\n"
                f"      <p>{description}</p>\n"
                "      <form method=\"POST\" action={action}>\n"
                f"        <button type=\"submit\">{button_text}</button>\n"
                "      </form>\n"
                "    </main>\n"
                "  );\n"
                "}\n"
            )

            return GeneratedLandingPageParts(
                landing_page_code=landing_page_code,
                provider="stub",
                model=self._model_name,
            )

        if request.language_code == LanguageCode.EN:
            title = "Corporate Sign In"
            email_placeholder = "Email"
            password_placeholder = "Password"
            button_text = "Sign In"
        else:
            title = "Kurumsal Giris"
            email_placeholder = "E-posta"
            password_placeholder = "Sifre"
            button_text = "Giris Yap"

        landing_page_code = (
            "'use client';\n"
            "import { usePathname, useSearchParams } from 'next/navigation';\n"
            "\n"
            "export default function PhishingLandingPage() {\n"
            "  const query = useSearchParams();\n"
            "  const pathname = usePathname();\n"
            "  const pathParts = pathname.split('/').filter(Boolean);\n"
            "  const pathCampaignId = pathParts.length > 0 ? pathParts[pathParts.length - 1] : '';\n"
            "  const c = query.get('c') || pathCampaignId;\n"
            "  const e = query.get('e') || '';\n"
            "  const co = query.get('co') || '';\n"
            "  const lang = query.get('lang') || '';\n"
            "  const exp = query.get('exp') || '';\n"
            "  const sig = query.get('sig') || '';\n"
            "  const action = `/track/submit?c=${c}&e=${e}&co=${co}${lang ? `&lang=${lang}` : ''}${exp ? `&exp=${encodeURIComponent(exp)}` : ''}${sig ? `&sig=${encodeURIComponent(sig)}` : ''}`;\n"
            "\n"
            "  return (\n"
            "    <main style={{ maxWidth: 420, margin: '64px auto', fontFamily: 'system-ui' }}>\n"
            f"      <h1>{title}</h1>\n"
            "      <form method=\"POST\" action={action}>\n"
            f"        <label>{email_placeholder}</label>\n"
            "        <input name=\"username\" type=\"email\" required style={{ width: '100%', marginBottom: 12 }} />\n"
            f"        <label>{password_placeholder}</label>\n"
            "        <input name=\"password\" type=\"password\" required style={{ width: '100%', marginBottom: 12 }} />\n"
            f"        <button type=\"submit\">{button_text}</button>\n"
            "      </form>\n"
            "    </main>\n"
            "  );\n"
            "}\n"
        )

        return GeneratedLandingPageParts(
            landing_page_code=landing_page_code,
            provider="stub",
            model=self._model_name,
        )
