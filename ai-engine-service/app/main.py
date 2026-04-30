import logging
from pathlib import Path
from contextlib import asynccontextmanager

from fastapi import FastAPI
from motor.motor_asyncio import AsyncIOMotorClient

from app.api import routes
from app.core.config import get_settings
from app.core.logging import configure_logging
from app.services.generation_service import GenerationService
from app.services.generator import RoutingContentGenerator
from app.services.kafka_worker import KafkaWorker
from app.services.prompt_loader import PromptLoader
from app.services.template_store import TemplateStore


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    configure_logging(settings.log_level)
    logger = logging.getLogger("lifespan")

    mongo_client = AsyncIOMotorClient(
        settings.mongo_uri,
        uuidRepresentation="standard",
        tz_aware=True,
    )
    db = mongo_client[settings.mongo_db_name]
    templates_collection = db[settings.mongo_collection_templates]
    fallback_templates_collection = db[settings.mongo_collection_fallback_templates]

    prompt_dir = settings.prompt_dir
    if not prompt_dir.is_absolute():
        project_root = Path(__file__).resolve().parents[1]
        prompt_dir = project_root / prompt_dir

    prompt_loader = PromptLoader(prompt_dir=prompt_dir)
    generator = RoutingContentGenerator(settings, prompt_loader)

    template_store = TemplateStore(
        collection=templates_collection,
        fallback_collection=fallback_templates_collection,
    )
    generation_service = GenerationService(
        generator=generator,
        template_store=template_store,
        generation_timeout_seconds=settings.generation_timeout_seconds,
    )
    kafka_worker = KafkaWorker(settings=settings, generation_service=generation_service) if settings.kafka_enabled else None

    app.state.mongo_client = mongo_client
    app.state.template_store = template_store
    app.state.generation_service = generation_service
    app.state.kafka_worker = kafka_worker

    app.dependency_overrides[routes.get_template_store] = lambda: app.state.template_store
    app.dependency_overrides[routes.get_generation_service] = lambda: app.state.generation_service

    await mongo_client.admin.command("ping")
    logger.info("MongoDB connection established")

    if kafka_worker is not None:
        await kafka_worker.start()
    else:
        logger.warning("Kafka worker disabled via KAFKA_ENABLED=false")
    try:
        yield
    finally:
        app.dependency_overrides.clear()
        if kafka_worker is not None:
            await kafka_worker.stop()
        mongo_client.close()


app = FastAPI(title="GenPhish AI Engine", version="1.0.0", lifespan=lifespan)
app.include_router(routes.router)
