import asyncio
import json
import logging
from contextlib import suppress

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

from app.core.config import Settings
from app.models.events import AiGenerationRequestEvent, AiGenerationResponseEvent, AiGenerationStatus
from app.services.generation_service import GenerationService


class KafkaWorker:
    def __init__(
        self,
        settings: Settings,
        generation_service: GenerationService,
    ) -> None:
        self._settings = settings
        self._generation_service = generation_service
        self._logger = logging.getLogger(self.__class__.__name__)

        self._consumer: AIOKafkaConsumer | None = None
        self._producer: AIOKafkaProducer | None = None
        self._task: asyncio.Task | None = None
        self._running = False

    async def start(self) -> None:
        if self._running:
            return

        consumer = AIOKafkaConsumer(
            self._settings.topic_ai_generation_requests,
            bootstrap_servers=self._settings.kafka_bootstrap_server_list,
            group_id=self._settings.kafka_group_id,
            client_id=f"{self._settings.kafka_client_id}-consumer",
            auto_offset_reset=self._settings.kafka_auto_offset_reset,
            enable_auto_commit=True,
            value_deserializer=lambda b: json.loads(b.decode("utf-8")),
        )

        producer = AIOKafkaProducer(
            bootstrap_servers=self._settings.kafka_bootstrap_server_list,
            client_id=f"{self._settings.kafka_client_id}-producer",
            value_serializer=lambda payload: json.dumps(payload).encode("utf-8"),
            key_serializer=lambda key: key.encode("utf-8") if key else None,
            acks="all",
        )

        consumer_started = False
        producer_started = False
        try:
            await consumer.start()
            consumer_started = True
            await producer.start()
            producer_started = True
        except Exception:
            if producer_started:
                with suppress(Exception):
                    await producer.stop()
            if consumer_started:
                with suppress(Exception):
                    await consumer.stop()
            self._logger.exception("Failed to start Kafka worker components")
            raise

        self._consumer = consumer
        self._producer = producer

        self._running = True
        self._task = asyncio.create_task(self._consume_loop(), name="ai-kafka-consumer-loop")
        self._logger.info("Kafka worker started")

    async def stop(self) -> None:
        self._running = False

        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
            finally:
                self._task = None

        if self._consumer:
            await self._consumer.stop()
            self._consumer = None

        if self._producer:
            await self._producer.stop()
            self._producer = None

        self._logger.info("Kafka worker stopped")

    async def _consume_loop(self) -> None:
        assert self._consumer is not None

        try:
            async for message in self._consumer:
                try:
                    await self._handle_message(message.value)
                except Exception:
                    self._logger.exception("Unexpected error while handling Kafka message; continuing")
                if not self._running:
                    break
        except asyncio.CancelledError:
            raise
        except Exception:
            self._logger.exception("Unexpected error in Kafka consume loop")

    async def _handle_message(self, payload: dict) -> None:
        self._logger.info("Received AI generation request payload")

        try:
            request = AiGenerationRequestEvent.model_validate(payload)
        except Exception as exc:
            self._logger.error("Invalid request payload: %s", exc)
            return

        generation_succeeded = False
        template_id: str | None = None
        try:
            template_id = await self._generation_service.generate_and_store(request)
            generation_succeeded = True
            response = AiGenerationResponseEvent(
                campaignId=request.campaign_id,
                mongoTemplateId=template_id,
                status=AiGenerationStatus.SUCCESS,
                errorMessage=None,
            )
        except Exception as exc:
            self._logger.exception("AI generation failed for campaign=%s", request.campaign_id)
            response = AiGenerationResponseEvent(
                campaignId=request.campaign_id,
                mongoTemplateId=None,
                status=AiGenerationStatus.FAILED,
                errorMessage=str(exc),
            )

        try:
            await self._publish_response(response)
        except Exception:
            self._logger.exception(
                "Failed to publish AI generation response for campaign=%s",
                request.campaign_id,
            )
            return

        if generation_succeeded and template_id is not None:
            self._logger.info(
                "AI generation success for campaign=%s template_id=%s",
                request.campaign_id,
                template_id,
            )

    async def _publish_response(self, response: AiGenerationResponseEvent) -> None:
        assert self._producer is not None

        await self._producer.send_and_wait(
            topic=self._settings.topic_ai_generation_responses,
            key=str(response.campaign_id),
            value=response.model_dump(by_alias=True, mode="json"),
        )
