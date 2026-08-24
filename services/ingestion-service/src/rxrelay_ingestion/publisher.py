import asyncio
import json
from typing import Protocol

from aiokafka import AIOKafkaProducer  # type: ignore[import-untyped]

from .models import IngestionEvent


class EventPublisher(Protocol):
    async def publish(self, event: IngestionEvent) -> None: ...

    async def close(self) -> None: ...


class KafkaPublisher:
    def __init__(self, bootstrap_servers: str, topic: str) -> None:
        self._bootstrap_servers = bootstrap_servers
        self._topic = topic
        self._producer: AIOKafkaProducer | None = None

    async def publish(self, event: IngestionEvent) -> None:
        try:
            if self._producer is None:
                self._producer = AIOKafkaProducer(
                    bootstrap_servers=self._bootstrap_servers,
                    acks="all",
                    enable_idempotence=True,
                    request_timeout_ms=10000,
                    retry_backoff_ms=500,
                    value_serializer=lambda value: json.dumps(value).encode(),
                )
                await asyncio.wait_for(self._producer.start(), timeout=15)
            await asyncio.wait_for(
                self._producer.send_and_wait(
                    self._topic,
                    event.model_dump(mode="json", by_alias=True, exclude_none=True),
                    key=event.source.encode(),
                ),
                timeout=15,
            )
        except Exception:
            # A failed AIOKafkaProducer cannot be assumed reusable. Reset it so a later bounded
            # manual run can recover when Kafka returns instead of keeping a poisoned client.
            await self._discard_producer()
            raise

    async def close(self) -> None:
        await self._discard_producer()

    async def _discard_producer(self) -> None:
        if self._producer is not None:
            producer = self._producer
            self._producer = None
            try:
                await asyncio.wait_for(producer.stop(), timeout=5)
            except TimeoutError:
                pass
