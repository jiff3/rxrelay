from datetime import UTC, datetime
from typing import Any

import pytest

from rxrelay_ingestion.models import RunStatus
from rxrelay_ingestion.normalizer import run_event
from rxrelay_ingestion.publisher import KafkaPublisher


class FakeProducer:
    def __init__(self, fail_start: bool) -> None:
        self.fail_start = fail_start
        self.stopped = False
        self.sent = 0

    async def start(self) -> None:
        if self.fail_start:
            raise OSError("broker unavailable")

    async def send_and_wait(self, *_args: object, **_kwargs: object) -> None:
        self.sent += 1

    async def stop(self) -> None:
        self.stopped = True


@pytest.mark.asyncio
async def test_failed_kafka_start_is_discarded_and_later_publish_can_recover(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    created = [FakeProducer(True), FakeProducer(False)]
    producers = created.copy()

    def factory(**_kwargs: Any) -> FakeProducer:
        return producers.pop(0)

    monkeypatch.setattr("rxrelay_ingestion.publisher.AIOKafkaProducer", factory)
    publisher = KafkaPublisher("unavailable:9092", "topic")
    event = run_event(
        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        "IngestionRunStarted",
        datetime(2026, 8, 23, tzinfo=UTC),
        RunStatus.RUNNING,
    )

    with pytest.raises(OSError, match="broker unavailable"):
        await publisher.publish(event)

    await publisher.publish(event)
    await publisher.close()

    assert created[0].stopped is True
    assert created[0].sent == 0
    assert created[1].sent == 1
    assert created[1].stopped is True
    assert not producers
