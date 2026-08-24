import logging
import re
import time
import uuid
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Gauge, Histogram, generate_latest
from pythonjsonlogger.json import JsonFormatter

from .clients import OpenFdaClient, RetryingHttpClient, RxNormClient
from .config import Settings
from .models import IngestionResult, ServiceStatus
from .publisher import KafkaPublisher
from .service import IngestionAlreadyRunning, IngestionService

RUNS = Counter("rxrelay_ingestion_runs_total", "Completed ingestion runs", ["outcome"])
DURATION = Histogram("rxrelay_ingestion_duration_seconds", "Ingestion run duration")
RECORDS = Counter(
    "rxrelay_ingestion_records_total", "Records observed during ingestion", ["outcome"]
)
LAST_RUN_RECORDS = Gauge(
    "rxrelay_ingestion_last_run_records", "Record counts from the last completed run", ["outcome"]
)
LAST_RUN_COMPLETED = Gauge(
    "rxrelay_ingestion_last_run_completed_timestamp_seconds",
    "Unix timestamp of the last completed ingestion run",
)
HTTP_REQUESTS = Counter(
    "rxrelay_ingestion_http_requests_total",
    "HTTP requests handled by the ingestion service",
    ["method", "route", "status"],
)
HTTP_DURATION = Histogram(
    "rxrelay_ingestion_http_request_duration_seconds",
    "HTTP request duration for the ingestion service",
    ["method", "route"],
)
SAFE_REQUEST_ID = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


def configure_logging() -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter("%(asctime)s %(levelname)s %(name)s %(message)s"))
    logging.basicConfig(level=logging.INFO, handlers=[handler], force=True)


def create_app(settings: Settings | None = None) -> FastAPI:
    config = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        configure_logging()
        client = httpx.AsyncClient(
            timeout=httpx.Timeout(config.http_timeout_seconds),
            headers={"User-Agent": "RxRelay/1.0 (public medication supply monitor)"},
        )
        resilient_http = RetryingHttpClient(
            client,
            config.http_retry_attempts,
            config.http_retry_base_seconds,
            config.http_retry_max_seconds,
        )
        publisher = KafkaPublisher(config.kafka_bootstrap_servers, config.shortage_topic)
        app.state.http_client = client
        app.state.publisher = publisher
        app.state.ingestion = IngestionService(
            OpenFdaClient(resilient_http, config.openfda_base_url, config.openfda_api_key),
            RxNormClient(
                resilient_http,
                config.rxnorm_base_url,
                config.rxnorm_enabled,
                config.rxnorm_cache_ttl_seconds,
                config.rxnorm_cache_max_entries,
            ),
            publisher,
        )
        yield
        await publisher.close()
        await client.aclose()

    app = FastAPI(
        title="RxRelay Ingestion Service",
        version="1.0.0",
        description="Bounded public openFDA ingestion and RxNorm normalization.",
        lifespan=lifespan,
        docs_url=None if config.environment == "production" else "/docs",
        redoc_url=None if config.environment == "production" else "/redoc",
        openapi_url=None if config.environment == "production" else "/openapi.json",
    )
    app.state.settings = config

    @app.middleware("http")
    async def observe_request(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        supplied = request.headers.get("X-Request-Id", "")
        request_id = supplied if SAFE_REQUEST_ID.fullmatch(supplied) else str(uuid.uuid4())
        started = time.perf_counter()
        status_code = 500
        try:
            response = await call_next(request)
            status_code = response.status_code
            response.headers["X-Request-Id"] = request_id
            response.headers["X-Content-Type-Options"] = "nosniff"
            response.headers["X-Frame-Options"] = "DENY"
            response.headers["Referrer-Policy"] = "no-referrer"
            return response
        finally:
            route = getattr(request.scope.get("route"), "path", "unmatched")
            HTTP_REQUESTS.labels(request.method, route, str(status_code)).inc()
            HTTP_DURATION.labels(request.method, route).observe(time.perf_counter() - started)

    @app.get("/health/live", tags=["health"])
    async def liveness() -> dict[str, str]:
        return {"status": "up"}

    @app.get("/health/ready", response_model=ServiceStatus, tags=["health"])
    async def readiness(request: Request) -> ServiceStatus:
        ingestion: IngestionService = request.app.state.ingestion
        return ServiceStatus(
            status="ready",
            running=ingestion.running,
            activeRun=ingestion.active_run,
            lastRun=ingestion.last_run,
        )

    @app.get("/api/v1/ingestions/status", response_model=ServiceStatus, tags=["ingestion"])
    async def status(request: Request) -> ServiceStatus:
        ingestion: IngestionService = request.app.state.ingestion
        return ServiceStatus(
            status="ready",
            running=ingestion.running,
            activeRun=ingestion.active_run,
            lastRun=ingestion.last_run,
        )

    @app.post("/api/v1/ingestions", response_model=IngestionResult, tags=["ingestion"])
    async def ingest(
        request: Request,
        limit: int = Query(default=100, ge=1, le=config.ingestion_max_records),
    ) -> IngestionResult:
        if not config.manual_ingestion_enabled:
            raise HTTPException(status_code=404, detail="not found")
        ingestion: IngestionService = request.app.state.ingestion
        try:
            with DURATION.time():
                result = await ingestion.run(limit)
            RUNS.labels(outcome=result.status.value.lower()).inc()
            counts = {
                "fetched": result.summary.fetched,
                "published": result.summary.published,
                "malformed": result.summary.malformed,
                "unresolved": result.summary.normalization_unresolved,
                "ambiguous": result.summary.normalization_ambiguous,
                "normalization_error": result.summary.normalization_errors,
            }
            for outcome, count in counts.items():
                RECORDS.labels(outcome=outcome).inc(count)
                LAST_RUN_RECORDS.labels(outcome=outcome).set(count)
            LAST_RUN_COMPLETED.set(result.completed_at.timestamp())
            return result
        except IngestionAlreadyRunning as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        except Exception:
            RUNS.labels(outcome="failure").inc()
            logging.getLogger(__name__).exception("ingestion run failed")
            raise HTTPException(
                status_code=503, detail="ingestion dependency unavailable"
            ) from None

    @app.get("/metrics", include_in_schema=False)
    async def metrics() -> Response:
        return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

    return app


app = create_app()
