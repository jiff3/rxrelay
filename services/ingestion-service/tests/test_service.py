import json
from pathlib import Path

import httpx
import pytest
import respx

from rxrelay_ingestion.clients import OpenFdaClient, RetryingHttpClient, RxNormClient
from rxrelay_ingestion.models import IngestionEvent, RunStatus
from rxrelay_ingestion.service import IngestionService

FIXTURE = Path(__file__).parent / "fixtures" / "captured_openfda_shortages_2026-08-22.json"


class MemoryPublisher:
    def __init__(self) -> None:
        self.events: list[IngestionEvent] = []

    async def publish(self, event: IngestionEvent) -> None:
        self.events.append(event)

    async def close(self) -> None:
        pass


@respx.mock
@pytest.mark.asyncio
async def test_malformed_record_does_not_abort_run() -> None:
    captured = json.loads(FIXTURE.read_text(encoding="utf-8"))["response"]
    captured["meta"]["results"]["limit"] = 2
    captured["meta"]["results"]["total"] = 2
    captured["results"].append({"status": "Current"})
    respx.get("https://example.test/shortages").mock(
        return_value=httpx.Response(200, json=captured)
    )
    publisher = MemoryPublisher()
    async with httpx.AsyncClient() as client:
        http = RetryingHttpClient(client, base_delay=0)
        service = IngestionService(
            OpenFdaClient(http, "https://example.test/shortages", None),
            RxNormClient(http, "https://rx.example", False),
            publisher,
        )
        result = await service.run(2)
    assert result.status == RunStatus.PARTIAL
    assert result.summary.fetched == 2
    assert result.summary.published == 1
    assert result.summary.malformed == 1
    assert [event.event_type for event in publisher.events] == [
        "IngestionRunStarted",
        "ShortageObserved",
        "IngestionRunCompleted",
    ]
