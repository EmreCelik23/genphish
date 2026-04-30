import logging

from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException, Response, status

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


@router.get("/healthz")
async def healthz() -> dict[str, str]:
    return {"status": "ok"}


@router.post(
    "/api/generate",
    responses={500: {"model": ErrorResponse}},
)
async def generate_template(
    payload: ManualGenerateRequest,
    generation_service: Annotated[GenerationService, Depends(get_generation_service)],
) -> TemplateCreatedResponse:
    request_event = AiGenerationRequestEvent.model_validate(payload.model_dump(by_alias=True))
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
    template_id: str,
    payload: CloneTemplateRequest,
    template_store: Annotated[TemplateStore, Depends(get_template_store)],
) -> TemplateCreatedResponse:
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
