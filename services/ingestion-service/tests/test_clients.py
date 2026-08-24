import json
from pathlib import Path

import httpx
import pytest
import respx

from rxrelay_ingestion.clients import OpenFdaClient, RetryingHttpClient, RxNormClient
from rxrelay_ingestion.models import NormalizationStatus

FIXTURE = Path(__file__).parent / "fixtures" / "captured_openfda_shortages_2026-08-22.json"


@respx.mock
@pytest.mark.asyncio
async def test_openfda_client_validates_page_and_obeys_limit() -> None:
    response = json.loads(FIXTURE.read_text(encoding="utf-8"))["response"]
    route = respx.get("https://example.test/shortages").mock(
        return_value=httpx.Response(200, json=response)
    )
    async with httpx.AsyncClient() as client:
        http = RetryingHttpClient(client, base_delay=0)
        records = [
            record
            async for record in OpenFdaClient(http, "https://example.test/shortages", None).records(
                1
            )
        ]
    assert len(records) == 1
    assert route.calls[0].request.url.params["limit"] == "1"


@respx.mock
@pytest.mark.asyncio
async def test_openfda_client_paginates_until_requested_limit() -> None:
    response = json.loads(FIXTURE.read_text(encoding="utf-8"))["response"]
    first = {
        **response,
        "meta": {**response["meta"], "results": {"skip": 0, "limit": 100, "total": 101}},
        "results": response["results"] * 100,
    }
    second = {
        **response,
        "meta": {**response["meta"], "results": {"skip": 100, "limit": 1, "total": 101}},
    }
    route = respx.get("https://example.test/shortages").mock(
        side_effect=[httpx.Response(200, json=first), httpx.Response(200, json=second)]
    )
    async with httpx.AsyncClient() as client:
        records = [
            record
            async for record in OpenFdaClient(
                RetryingHttpClient(client), "https://example.test/shortages", None
            ).records(101)
        ]
    assert len(records) == 101
    assert route.calls[1].request.url.params["skip"] == "100"


@respx.mock
@pytest.mark.asyncio
async def test_retries_transient_openfda_failure() -> None:
    response = json.loads(FIXTURE.read_text(encoding="utf-8"))["response"]
    route = respx.get("https://example.test/shortages").mock(
        side_effect=[httpx.Response(503), httpx.Response(200, json=response)]
    )
    async with httpx.AsyncClient() as client:
        http = RetryingHttpClient(client, attempts=2, base_delay=0)
        records = [
            record
            async for record in OpenFdaClient(http, "https://example.test/shortages", None).records(
                1
            )
        ]
    assert len(records) == 1
    assert route.call_count == 2


@respx.mock
@pytest.mark.asyncio
async def test_rxnorm_accepts_only_one_match_and_caches_it() -> None:
    lookup = respx.get("https://rx.example/REST/rxcui.json").mock(
        return_value=httpx.Response(200, json={"idGroup": {"rxnormId": ["123"]}})
    )
    properties = respx.get("https://rx.example/REST/rxcui/123/properties.json").mock(
        return_value=httpx.Response(200, json={"properties": {"name": "Canonical fixture"}})
    )
    async with httpx.AsyncClient() as client:
        resolver = RxNormClient(RetryingHttpClient(client), "https://rx.example", True)
        first = await resolver.resolve(" Fixture Drug ")
        second = await resolver.resolve("fixture drug")
    assert first.status == NormalizationStatus.RESOLVED
    assert first.rx_cui == "123"
    assert first.canonical_name == "Canonical fixture"
    assert second == first
    assert lookup.call_count == 1
    assert properties.call_count == 1


@respx.mock
@pytest.mark.asyncio
async def test_rxnorm_does_not_force_ambiguous_match() -> None:
    respx.get("https://rx.example/REST/rxcui.json").mock(
        return_value=httpx.Response(200, json={"idGroup": {"rxnormId": ["456", "123"]}})
    )
    async with httpx.AsyncClient() as client:
        result = await RxNormClient(RetryingHttpClient(client), "https://rx.example", True).resolve(
            "Ambiguous fixture"
        )
    assert result.status == NormalizationStatus.AMBIGUOUS
    assert result.rx_cui is None
    assert result.candidates == 2


@respx.mock
@pytest.mark.asyncio
async def test_rxnorm_failure_is_recorded_without_forcing_identity() -> None:
    respx.get("https://rx.example/REST/rxcui.json").mock(return_value=httpx.Response(503))
    async with httpx.AsyncClient() as client:
        result = await RxNormClient(
            RetryingHttpClient(client, attempts=1), "https://rx.example", True
        ).resolve("Unavailable fixture")
    assert result.status == NormalizationStatus.ERROR
    assert result.rx_cui is None
