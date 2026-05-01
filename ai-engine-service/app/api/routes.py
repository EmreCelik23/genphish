import logging
import hmac

from typing import Annotated
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, Request, Response, status

from app.core.config import get_settings
from app.models.events import (
    AiGenerationRequestEvent,
    CloneTemplateRequest,
    ErrorResponse,
    ManualGenerateRequest,
    TemplateCreatedResponse,
)
from app.services.generation_service import GenerationService
from app.services.template_store import TemplateStore


router = APIRouter()
logger = logging.getLogger(__name__)


def get_generation_service() -> GenerationService:
    raise RuntimeError("GenerationService dependency was not injected")


def get_template_store() -> TemplateStore:
    raise RuntimeError("TemplateStore dependency was not injected")


def require_service_auth(request: Request) -> None:
    settings = get_settings()
    if not settings.service_auth_enabled:
        return

    expected_token = settings.service_token.strip()
    if not expected_token:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Service auth is enabled but token is missing",
        )

    authorization = (request.headers.get("Authorization") or "").strip()
    bearer_token = ""
    if authorization.lower().startswith("bearer "):
        bearer_token = authorization[7:].strip()

    header_token = ""
    for header_name in settings.service_token_header_name_list:
        candidate = (request.headers.get(header_name) or "").strip()
        if candidate:
            header_token = candidate
            break
    received_token = bearer_token or header_token
    if not received_token or not hmac.compare_digest(expected_token, received_token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Unauthorized request")


def require_company_scope(request: Request, company_id: UUID) -> None:
    settings = get_settings()
    header_name_list = settings.company_header_name_list
    received_company = ""
    resolved_header_name = header_name_list[0] if header_name_list else settings.company_header
    for header_name in header_name_list:
        candidate = (request.headers.get(header_name) or "").strip()
        if candidate:
            received_company = candidate
            resolved_header_name = header_name
            break
    if not received_company:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Missing required tenant header: {resolved_header_name}",
        )
    if received_company.lower() != str(company_id).lower():
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Tenant header does not match request company scope",
        )


@router.get("/healthz")
async def healthz() -> dict[str, str]:
    return {"status": "ok"}


@router.post(
    "/api/generate",
    responses={500: {"model": ErrorResponse}},
)
async def generate_template(
    request: Request,
    payload: ManualGenerateRequest,
    generation_service: Annotated[GenerationService, Depends(get_generation_service)],
    _: Annotated[None, Depends(require_service_auth)],
) -> TemplateCreatedResponse:
    request_event = AiGenerationRequestEvent.model_validate(payload.model_dump(by_alias=True))
    require_company_scope(request, request_event.company_id)
    try:
        result = await generation_service.generate_and_store(request_event)
    except Exception:
        logger.exception(
            "Manual template generation failed for campaign=%s",
            request_event.template_id,
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Template generation failed",
        )

    return TemplateCreatedResponse(
        mongoTemplateId=result.template_id,
        templateId=request_event.template_id,
        status="SUCCESS",
    )


@router.get("/api/templates/{template_id}")
async def get_template(
    template_id: str,
    _: Annotated[None, Depends(require_service_auth)],
    template_store: Annotated[TemplateStore, Depends(get_template_store)],
) -> Response:
    payload = await template_store.get_raw_json_for_campaign_fallback(template_id)
    if payload is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Template not found")

    return Response(content=payload, media_type="application/json")


@router.post(
    "/api/templates/{template_id}/clone",
    responses={404: {"model": ErrorResponse}},
)
async def clone_template(
    request: Request,
    template_id: str,
    payload: CloneTemplateRequest,
    _: Annotated[None, Depends(require_service_auth)],
    template_store: Annotated[TemplateStore, Depends(get_template_store)],
) -> TemplateCreatedResponse:
    require_company_scope(request, payload.company_id)
    cloned_template_id = await template_store.clone(
        mongo_template_id=template_id,
        template_id=payload.template_id,
        company_id=payload.company_id,
    )
    if cloned_template_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Template not found")

    return TemplateCreatedResponse(
        mongoTemplateId=cloned_template_id,
        templateId=payload.template_id,
        status="SUCCESS",
    )
