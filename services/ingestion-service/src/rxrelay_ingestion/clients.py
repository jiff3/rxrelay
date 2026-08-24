import asyncio
import time
from collections import OrderedDict
from collections.abc import AsyncIterator
from typing import Any

import httpx
from pydantic import ValidationError

from .models import (
    NormalizationStatus,
    OpenFdaPage,
    RxNormPropertiesResponse,
    RxNormResolution,
    RxNormSearchResponse,
)


class UpstreamError(RuntimeError):
    pass


class RetryingHttpClient:
    def __init__(
        self,
        client: httpx.AsyncClient,
        attempts: int = 3,
        base_delay: float = 0.25,
        max_delay: float = 4.0,
    ) -> None:
        self._client = client
        self._attempts = attempts
        self._base_delay = base_delay
        self._max_delay = max_delay

    async def get_json(self, url: str, params: dict[str, str | int]) -> dict[str, Any]:
        for attempt in range(self._attempts):
            try:
                response = await self._client.get(url, params=params)
                if response.status_code == 429 or response.status_code >= 500:
                    response.raise_for_status()
                if response.status_code >= 400:
                    response.raise_for_status()
                value = response.json()
                if not isinstance(value, dict):
                    raise ValueError("response root is not an object")
                return value
            except (httpx.TimeoutException, httpx.NetworkError, httpx.RemoteProtocolError) as exc:
                retryable = True
                last_error: Exception = exc
            except httpx.HTTPStatusError as exc:
                retryable = exc.response.status_code == 429 or exc.response.status_code >= 500
                last_error = exc
            except ValueError as exc:
                retryable = False
                last_error = exc
            if not retryable or attempt + 1 >= self._attempts:
                raise UpstreamError(f"request to {url} failed: {last_error}") from last_error
            delay = min(self._max_delay, self._base_delay * (2**attempt))
            retry_after = getattr(last_error, "response", None)
            if retry_after is not None:
                header = retry_after.headers.get("Retry-After")
                if header and header.isdigit():
                    delay = min(self._max_delay, float(header))
            await asyncio.sleep(delay)
        raise AssertionError("retry loop exhausted")


class OpenFdaClient:
    def __init__(
        self,
        http: RetryingHttpClient,
        base_url: str,
        api_key: str | None,
    ) -> None:
        self._http = http
        self._base_url = base_url
        self._api_key = api_key

    @property
    def source_url(self) -> str:
        return self._base_url

    async def records(self, maximum: int) -> AsyncIterator[dict[str, object]]:
        remaining = maximum
        skip = 0
        while remaining > 0:
            page_size = min(remaining, 100)
            params: dict[str, str | int] = {"limit": page_size, "skip": skip}
            if self._api_key:
                params["api_key"] = self._api_key
            try:
                page = OpenFdaPage.model_validate(await self._http.get_json(self._base_url, params))
            except ValidationError as exc:
                raise UpstreamError(f"openFDA page validation failed: {exc}") from exc
            for record in page.results:
                yield record
            count = len(page.results)
            remaining -= count
            skip += count
            if count == 0 or skip >= page.meta.results.total or count < page_size:
                return


class RxNormClient:
    def __init__(
        self,
        http: RetryingHttpClient,
        base_url: str,
        enabled: bool,
        cache_ttl_seconds: int = 86400,
        cache_max_entries: int = 2000,
    ) -> None:
        self._http = http
        self._base_url = base_url.rstrip("/")
        self._enabled = enabled
        self._cache_ttl = cache_ttl_seconds
        self._cache_max_entries = cache_max_entries
        self._cache: OrderedDict[str, tuple[float, RxNormResolution]] = OrderedDict()
        self._lock = asyncio.Lock()

    async def resolve(self, name: str) -> RxNormResolution:
        query = " ".join(name.split())
        if not self._enabled:
            return RxNormResolution(status=NormalizationStatus.SKIPPED, query=query)
        cache_key = query.casefold()
        async with self._lock:
            cached = self._cache.get(cache_key)
            if cached and time.monotonic() - cached[0] < self._cache_ttl:
                self._cache.move_to_end(cache_key)
                return cached[1]
            if cached:
                del self._cache[cache_key]

        try:
            response = await self._http.get_json(
                f"{self._base_url}/REST/rxcui.json",
                {"name": query, "search": 2, "allsrc": 0},
            )
            search = RxNormSearchResponse.model_validate(response)
            identifiers = sorted(set(search.id_group.rxnorm_id))
            if len(identifiers) == 1:
                rx_cui = identifiers[0]
                properties = await self._http.get_json(
                    f"{self._base_url}/REST/rxcui/{rx_cui}/properties.json", {}
                )
                canonical = RxNormPropertiesResponse.model_validate(properties).properties.name
                result = RxNormResolution(
                    status=NormalizationStatus.RESOLVED,
                    query=query,
                    rx_cui=rx_cui,
                    canonical_name=canonical,
                    candidates=1,
                )
            elif identifiers:
                result = RxNormResolution(
                    status=NormalizationStatus.AMBIGUOUS,
                    query=query,
                    candidates=len(identifiers),
                )
            else:
                result = RxNormResolution(
                    status=NormalizationStatus.UNRESOLVED, query=query, candidates=0
                )
        except (UpstreamError, ValidationError):
            result = RxNormResolution(status=NormalizationStatus.ERROR, query=query)

        async with self._lock:
            self._cache[cache_key] = (time.monotonic(), result)
            self._cache.move_to_end(cache_key)
            while len(self._cache) > self._cache_max_entries:
                self._cache.popitem(last=False)
        return result
