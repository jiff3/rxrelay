import argparse
import asyncio
import json

import httpx

from .clients import OpenFdaClient, RetryingHttpClient, RxNormClient
from .config import Settings
from .publisher import KafkaPublisher
from .service import IngestionService


async def ingest(limit: int, runs: int = 1) -> int:
    settings = Settings()
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(settings.http_timeout_seconds),
        headers={"User-Agent": "RxRelay/1.0 (public medication supply monitor)"},
    ) as client:
        http = RetryingHttpClient(
            client,
            settings.http_retry_attempts,
            settings.http_retry_base_seconds,
            settings.http_retry_max_seconds,
        )
        publisher = KafkaPublisher(settings.kafka_bootstrap_servers, settings.shortage_topic)
        try:
            service = IngestionService(
                OpenFdaClient(http, settings.openfda_base_url, settings.openfda_api_key),
                RxNormClient(
                    http,
                    settings.rxnorm_base_url,
                    settings.rxnorm_enabled,
                    settings.rxnorm_cache_ttl_seconds,
                    settings.rxnorm_cache_max_entries,
                ),
                publisher,
            )
            results = [await service.run(limit) for _ in range(runs)]
            print(
                json.dumps(
                    [result.model_dump(mode="json", by_alias=True) for result in results],
                    indent=2,
                )
            )
            return 1 if any(result.status == "FAILED" for result in results) else 0
        finally:
            await publisher.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Run a bounded real openFDA ingestion")
    parser.add_argument("--limit", type=int, default=100)
    parser.add_argument("--runs", type=int, choices=range(1, 4), default=1)
    args = parser.parse_args()
    raise SystemExit(asyncio.run(ingest(args.limit, args.runs)))


if __name__ == "__main__":
    main()
