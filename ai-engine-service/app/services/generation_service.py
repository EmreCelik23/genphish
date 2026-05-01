import asyncio
from dataclasses import dataclass
from datetime import datetime, timezone
import logging

from app.models.events import AiGenerationRequestEvent, LanguageCode, RegenerationScope, TemplateCategory
from app.models.template import TemplateDocument
from app.services.generator import ContentGenerator, GeneratedTemplateParts
from app.services.template_store import TemplateStore


@dataclass(frozen=True)
class GenerationResult:
    template_id: str
    fallback_used: bool


class GenerationService:
    def __init__(
        self,
        generator: ContentGenerator,
        template_store: TemplateStore,
        generation_timeout_seconds: float = 15.0,
    ) -> None:
        self._generator = generator
        self._template_store = template_store
        self._generation_timeout_seconds = generation_timeout_seconds
        self._logger = logging.getLogger(self.__class__.__name__)

    async def generate_and_store(self, request: AiGenerationRequestEvent) -> GenerationResult:
        existing = None
        if request.existing_mongo_template_id:
            existing = await self._template_store.get(request.existing_mongo_template_id)

        parts, fallback_used = await self._resolve_generation_with_circuit_breaker(request, existing)
        parts = self._enforce_template_contract(request, existing, parts)

        template = TemplateDocument(
            templateId=request.template_id,
            companyId=request.company_id,
            prompt=request.prompt or "",
            targetUrl=request.target_url or "",
            referenceImageUrl=request.reference_image_url,
            templateCategory=request.template_category,
            difficultyLevel=request.difficulty_level,
            languageCode=request.language_code,
            subject=parts.subject,
            bodyHtml=parts.body_html,
            landingPageCode=parts.landing_page_code,
            llmProvider=parts.provider,
            llmModel=parts.model,
            updatedAt=datetime.now(timezone.utc),
        )

        if existing and request.existing_mongo_template_id:
            template_id = await self._template_store.update(request.existing_mongo_template_id, template)
            return GenerationResult(template_id=template_id, fallback_used=fallback_used)
        template_id = await self._template_store.create(template)
        return GenerationResult(template_id=template_id, fallback_used=fallback_used)

    async def _resolve_generation_with_circuit_breaker(
        self,
        request: AiGenerationRequestEvent,
        existing,
    ) -> tuple[GeneratedTemplateParts, bool]:
        should_allow_fallback = request.allow_fallback_template
        try:
            parts = await asyncio.wait_for(
                self._resolve_generation_parts(request, existing),
                timeout=self._generation_timeout_seconds,
            )
            return parts, False
        except asyncio.TimeoutError as exc:
            self._logger.warning(
                "AI generation timeout after %.1fs for campaign=%s.",
                self._generation_timeout_seconds,
                request.template_id,
            )
            if not should_allow_fallback:
                raise RuntimeError("AI generation timed out and fallback template is disabled.") from exc
        except Exception as exc:
            self._logger.exception(
                "AI generation error for campaign=%s.",
                request.template_id,
            )
            if not should_allow_fallback:
                raise RuntimeError("AI generation failed and fallback template is disabled.") from exc

        fallback_parts = await self._resolve_fallback_parts(request, existing)
        return fallback_parts, True

    async def _resolve_generation_parts(
        self,
        request: AiGenerationRequestEvent,
        existing,
    ) -> GeneratedTemplateParts:
        scope = request.regeneration_scope or RegenerationScope.ALL

        if scope == RegenerationScope.ALL or not existing:
            return await self._generator.generate(request)

        if scope == RegenerationScope.ONLY_EMAIL:
            generated_email = await self._generator.generate_email(request)
            return GeneratedTemplateParts(
                subject=generated_email.subject,
                body_html=generated_email.body_html,
                landing_page_code=existing.landing_page_code,
                provider=generated_email.provider,
                model=generated_email.model,
            )

        if scope == RegenerationScope.ONLY_LANDING_PAGE:
            generated_landing = await self._generator.generate_landing_page(
                request=request,
                email_subject=existing.subject,
            )
            return GeneratedTemplateParts(
                subject=existing.subject,
                body_html=existing.body_html,
                landing_page_code=generated_landing.landing_page_code,
                provider=generated_landing.provider,
                model=generated_landing.model,
            )

        return await self._generator.generate(request)

    async def _resolve_fallback_parts(
        self,
        request: AiGenerationRequestEvent,
        existing,
    ) -> GeneratedTemplateParts:
        fallback = await self._template_store.get_static_fallback_parts(
            difficulty_level=request.difficulty_level,
            language_code=request.language_code,
            prompt=request.prompt,
        )
        if fallback is None:
            fallback = self._default_fallback_parts(request)

        scope = request.regeneration_scope or RegenerationScope.ALL
        if not existing or scope == RegenerationScope.ALL:
            return fallback

        if scope == RegenerationScope.ONLY_EMAIL:
            return GeneratedTemplateParts(
                subject=fallback.subject,
                body_html=fallback.body_html,
                landing_page_code=existing.landing_page_code,
                provider=fallback.provider,
                model=fallback.model,
            )

        if scope == RegenerationScope.ONLY_LANDING_PAGE:
            return GeneratedTemplateParts(
                subject=existing.subject,
                body_html=existing.body_html,
                landing_page_code=fallback.landing_page_code,
                provider=fallback.provider,
                model=fallback.model,
            )

        return fallback

    @staticmethod
    def _default_fallback_parts(request: AiGenerationRequestEvent) -> GeneratedTemplateParts:
        is_english = request.language_code == LanguageCode.EN
        category = request.template_category or TemplateCategory.CREDENTIAL_HARVESTING

        if category == TemplateCategory.CLICK_ONLY:
            if is_english:
                subject = f"{request.difficulty_level.title()} Mandatory Policy Update"
                body_html = (
                    "<html><body>"
                    "<p>Hello {{name}},</p>"
                    "<p>The {{department}} department has published an urgent policy update.</p>"
                    "<p>Please <a href=\"{{phishing_link}}\">review the update immediately</a>.</p>"
                    "<p>Compliance Team</p>"
                    "</body></html>"
                )
            else:
                subject = f"{request.difficulty_level.title()} Acil Politika Güncellemesi"
                body_html = (
                    "<html><body>"
                    "<p>Merhaba {{name}},</p>"
                    "<p>{{department}} birimi için acil bir politika güncellemesi yayınlandı.</p>"
                    "<p>Lütfen <a href=\"{{phishing_link}}\">güncellemeyi hemen inceleyin</a>.</p>"
                    "<p>Uyum Ekibi</p>"
                    "</body></html>"
                )
            landing_page_code = ""
        elif category == TemplateCategory.MALWARE_DELIVERY:
            if is_english:
                subject = f"{request.difficulty_level.title()} Invoice Requires Download"
                body_html = (
                    "<html><body>"
                    "<p>Hello {{name}},</p>"
                    "<p>A finance document is waiting for the {{department}} department.</p>"
                    "<p>Please <a href=\"{{phishing_link}}\">open the document portal</a> and download the file.</p>"
                    "<p>Finance Operations</p>"
                    "</body></html>"
                )
                page_title = "Document Center"
                page_description = "A new invoice is available. Click below to download."
                button_text = "Download Invoice"
            else:
                subject = f"{request.difficulty_level.title()} Fatura İndirme Bildirimi"
                body_html = (
                    "<html><body>"
                    "<p>Merhaba {{name}},</p>"
                    "<p>{{department}} birimi için bir finans belgesi hazırlandı.</p>"
                    "<p>Lütfen <a href=\"{{phishing_link}}\">belge portalına gidip</a> dosyayı indirin.</p>"
                    "<p>Finans Operasyonları</p>"
                    "</body></html>"
                )
                page_title = "Belge Merkezi"
                page_description = "Yeni bir fatura hazırlandı. İndirmek için aşağıya tıklayın."
                button_text = "Faturayı İndir"

            landing_page_code = (
                "'use client';\n"
                "import { usePathname, useSearchParams } from 'next/navigation';\n"
                "\n"
                "export default function DownloadPage() {\n"
                "  const qp = useSearchParams();\n"
                "  const pathname = usePathname();\n"
                "  const pathParts = pathname.split('/').filter(Boolean);\n"
                "  const pathCampaignId = pathParts.length > 0 ? pathParts[pathParts.length - 1] : '';\n"
                "  const c = qp.get('c') || pathCampaignId;\n"
                "  const e = qp.get('e') || '';\n"
                "  const co = qp.get('co') || '';\n"
                "  const lang = qp.get('lang') || '';\n"
                "  const exp = qp.get('exp') || '';\n"
                "  const sig = qp.get('sig') || '';\n"
                "  const action = `/track/download?c=${c}&e=${e}&co=${co}${lang ? `&lang=${lang}` : ''}${exp ? `&exp=${encodeURIComponent(exp)}` : ''}${sig ? `&sig=${encodeURIComponent(sig)}` : ''}`;\n"
                "\n"
                "  return (\n"
                "    <main style={{ maxWidth: 520, margin: '56px auto', fontFamily: 'system-ui' }}>\n"
                f"      <h1>{page_title}</h1>\n"
                f"      <p>{page_description}</p>\n"
                "      <form method=\"POST\" action={action}>\n"
                f"        <button type=\"submit\">{button_text}</button>\n"
                "      </form>\n"
                "    </main>\n"
                "  );\n"
                "}\n"
            )
        elif category == TemplateCategory.OAUTH_CONSENT:
            if is_english:
                subject = f"{request.difficulty_level.title()} Sign In With Microsoft"
                body_html = (
                    "<html><body>"
                    "<p>Hello {{name}},</p>"
                    "<p>The {{department}} department requests access approval for an internal app.</p>"
                    "<p>Please continue with your Microsoft account here: "
                    "<a href=\"{{phishing_link}}\">Grant access</a>.</p>"
                    "<p>IT Service Desk</p>"
                    "</body></html>"
                )
            else:
                subject = f"{request.difficulty_level.title()} Microsoft ile Giriş Doğrulaması"
                body_html = (
                    "<html><body>"
                    "<p>Merhaba {{name}},</p>"
                    "<p>{{department}} birimi iç uygulama erişimi için onay talep ediyor.</p>"
                    "<p>Lütfen Microsoft hesabınızla devam edin: "
                    "<a href=\"{{phishing_link}}\">Erişime izin ver</a>.</p>"
                    "<p>IT Servis Masası</p>"
                    "</body></html>"
                )
            landing_page_code = ""
        else:
            if is_english:
                subject = f"{request.difficulty_level.title()} Security Verification"
                body_html = (
                    "<html><body>"
                    "<p>Hello {{name}},</p>"
                    "<p>A verification step is pending for the {{department}} department.</p>"
                    "<p>To complete the process, please "
                    "<a href=\"{{phishing_link}}\">click here</a>.</p>"
                    "<p>IT Service Desk</p>"
                    "</body></html>"
                )
                page_title = "Corporate Account Sign In"
                email_placeholder = "Email"
                pass_placeholder = "Password"
                button_text = "Sign In"
            else:
                subject = f"{request.difficulty_level.title()} Güvenlik Kontrolü"
                body_html = (
                    "<html><body>"
                    "<p>Merhaba {{name}},</p>"
                    "<p>{{department}} birimi için hesap doğrulama adımı beklemektedir.</p>"
                    "<p>Lütfen işlemi tamamlamak için "
                    "<a href=\"{{phishing_link}}\">buraya tıklayın</a>.</p>"
                    "<p>IT Servis Masası</p>"
                    "</body></html>"
                )
                page_title = "Kurumsal Hesap Girişi"
                email_placeholder = "E-posta"
                pass_placeholder = "Şifre"
                button_text = "Giriş Yap"

            landing_page_code = (
                "'use client';\n"
                "import { usePathname, useSearchParams } from 'next/navigation';\n"
                "\n"
                "export default function LoginPage() {\n"
                "  const qp = useSearchParams();\n"
                "  const pathname = usePathname();\n"
                "  const pathParts = pathname.split('/').filter(Boolean);\n"
                "  const pathCampaignId = pathParts.length > 0 ? pathParts[pathParts.length - 1] : '';\n"
                "  const c = qp.get('c') || pathCampaignId;\n"
                "  const e = qp.get('e') || '';\n"
                "  const co = qp.get('co') || '';\n"
                "  const lang = qp.get('lang') || '';\n"
                "  const exp = qp.get('exp') || '';\n"
                "  const sig = qp.get('sig') || '';\n"
                "  const action = `/track/submit?c=${c}&e=${e}&co=${co}${lang ? `&lang=${lang}` : ''}${exp ? `&exp=${encodeURIComponent(exp)}` : ''}${sig ? `&sig=${encodeURIComponent(sig)}` : ''}`;\n"
                "\n"
                "  return (\n"
                "    <main style={{ maxWidth: 420, margin: '48px auto', fontFamily: 'system-ui' }}>\n"
                f"      <h1>{page_title}</h1>\n"
                "      <form method=\"POST\" action={action}>\n"
                f"        <input name=\"username\" type=\"email\" placeholder=\"{email_placeholder}\" required style={{ width: '100%', marginBottom: 12 }} />\n"
                f"        <input name=\"password\" type=\"password\" placeholder=\"{pass_placeholder}\" required style={{ width: '100%', marginBottom: 12 }} />\n"
                f"        <button type=\"submit\">{button_text}</button>\n"
                "      </form>\n"
                "    </main>\n"
                "  );\n"
                "}\n"
            )
        return GeneratedTemplateParts(
            subject=subject,
            body_html=body_html,
            landing_page_code=landing_page_code,
            provider="fallback-builtin",
            model="fallback-v1",
        )

    def _enforce_template_contract(
        self,
        request: AiGenerationRequestEvent,
        existing,
        parts: GeneratedTemplateParts,
    ) -> GeneratedTemplateParts:
        fallback = self._default_fallback_parts(request)

        scope = request.regeneration_scope or RegenerationScope.ALL
        enforce_email = existing is None or scope in {RegenerationScope.ALL, RegenerationScope.ONLY_EMAIL}
        enforce_landing = existing is None or scope in {RegenerationScope.ALL, RegenerationScope.ONLY_LANDING_PAGE}

        subject = (parts.subject or "").strip() or fallback.subject
        body_html = parts.body_html
        landing_page_code = parts.landing_page_code

        if enforce_email:
            body_html = self._enforce_email_body_contract(request, parts.body_html, fallback.body_html)
        if enforce_landing:
            landing_page_code = self._enforce_landing_contract(request, parts.landing_page_code, fallback.landing_page_code)

        return GeneratedTemplateParts(
            subject=subject,
            body_html=body_html,
            landing_page_code=landing_page_code,
            provider=parts.provider,
            model=parts.model,
        )

    def _enforce_email_body_contract(
        self,
        request: AiGenerationRequestEvent,
        body_html: str,
        fallback_body_html: str,
    ) -> str:
        body = (body_html or "").strip()
        if not body:
            return fallback_body_html

        lower = body.lower()
        if "<html" not in lower:
            body = f"<html><body>{body}</body></html>"

        is_english = request.language_code == LanguageCode.EN
        if "{{name}}" not in body:
            greeting = "<p>Hello {{name}},</p>" if is_english else "<p>Merhaba {{name}},</p>"
            body = self._insert_after_body_open(body, greeting)

        if "{{department}}" not in body:
            hidden_department = "<span style=\"display:none\">{{department}}</span>"
            body = self._insert_before_body_close(body, hidden_department)

        if "{{phishing_link}}" not in body:
            cta = (
                "<p>For verification please <a href=\"{{phishing_link}}\">review details</a>.</p>"
                if is_english
                else "<p>Doğrulama için lütfen <a href=\"{{phishing_link}}\">detayları inceleyin</a>.</p>"
            )
            body = self._insert_before_body_close(body, cta)

        return body

    @staticmethod
    def _enforce_landing_contract(
        request: AiGenerationRequestEvent,
        landing_page_code: str,
        fallback_landing_page_code: str,
    ) -> str:
        landing = (landing_page_code or "").strip()
        category = request.template_category or TemplateCategory.CREDENTIAL_HARVESTING
        if category in {TemplateCategory.CLICK_ONLY, TemplateCategory.OAUTH_CONSENT}:
            return landing

        if not landing:
            return fallback_landing_page_code

        required_endpoint = "/track/download" if category == TemplateCategory.MALWARE_DELIVERY else "/track/submit"
        required_tokens = (required_endpoint, "c=", "e=", "co=", "exp=", "sig=")
        if any(token not in landing for token in required_tokens):
            return fallback_landing_page_code
        return landing

    @staticmethod
    def _insert_before_body_close(html: str, snippet: str) -> str:
        lower = html.lower()
        close_tag = "</body>"
        idx = lower.rfind(close_tag)
        if idx >= 0:
            return html[:idx] + snippet + html[idx:]
        return html + snippet

    @staticmethod
    def _insert_after_body_open(html: str, snippet: str) -> str:
        lower = html.lower()
        open_idx = lower.find("<body")
        if open_idx < 0:
            return snippet + html

        tag_end_idx = lower.find(">", open_idx)
        if tag_end_idx < 0:
            return snippet + html

        insert_at = tag_end_idx + 1
        return html[:insert_at] + snippet + html[insert_at:]
