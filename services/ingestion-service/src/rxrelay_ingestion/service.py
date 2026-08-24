import asyncio
import logging
import uuid
from datetime import UTC, datetime

from pydantic import ValidationError

from .clients import OpenFdaClient, RxNormClient, UpstreamError
from .models import (
    ActiveRun,
    IngestionResult,
    NormalizationStatus,
    OpenFdaShortageRecord,
    RunStatus,
    RunSummary,
)
from .normalizer import normalize_record, run_event, source_resolution
from .publisher import EventPublisher


class IngestionAlreadyRunning(RuntimeError):
    pass


logger = logging.getLogger(__name__)


class IngestionService:
    def __init__(
        self, openfda: OpenFdaClient, rxnorm: RxNormClient, publisher: EventPublisher
    ) -> None:
        self._openfda = openfda
        self._rxnorm = rxnorm
        self._publisher = publisher
        self._lock = asyncio.Lock()
        self.last_run: IngestionResult | None = None
        self.active_run: ActiveRun | None = None

    @property
    def running(self) -> bool:
        return self._lock.locked()

    async def run(self, limit: int) -> IngestionResult:
        if self._lock.locked():
            raise IngestionAlreadyRunning("an ingestion run is already active")
        async with self._lock:
            try:
                return await self._run_locked(limit)
            finally:
                self.active_run = None

    async def _run_locked(self, limit: int) -> IngestionResult:
        run_id = str(uuid.uuid4())
        started = datetime.now(UTC)
        self.active_run = ActiveRun(runId=run_id, requested=limit, startedAt=started)
        logger.info(
            "ingestion run started",
            extra={"correlationId": run_id, "ingestionRunId": run_id, "requested": limit},
        )
        await self._publisher.publish(
            run_event(run_id, "IngestionRunStarted", started, RunStatus.RUNNING)
        )
        fetched = published = malformed = unresolved = ambiguous = normalization_errors = 0
        errors: list[str] = []
        upstream_failed = False
        try:
            async for raw in self._openfda.records(limit):
                fetched += 1
                try:
                    record = OpenFdaShortageRecord.model_validate(raw)
                    resolution = source_resolution(record)
                    if resolution is None:
                        resolution = await self._rxnorm.resolve(record.generic_name)
                    unresolved += int(resolution.status == NormalizationStatus.UNRESOLVED)
                    ambiguous += int(resolution.status == NormalizationStatus.AMBIGUOUS)
                    normalization_errors += int(resolution.status == NormalizationStatus.ERROR)
                    event = normalize_record(raw, record, resolution, started, run_id)
                    await self._publisher.publish(event)
                    published += 1
                except (ValidationError, ValueError) as exc:
                    malformed += 1
                    if len(errors) < 20:
                        errors.append(f"record {fetched}: {str(exc).splitlines()[0]}")
        except UpstreamError as exc:
            upstream_failed = True
            errors.append(str(exc)[:500])

        if upstream_failed and fetched == 0:
            status = RunStatus.FAILED
        elif upstream_failed or malformed > 0 or normalization_errors > 0:
            status = RunStatus.PARTIAL
        else:
            status = RunStatus.SUCCEEDED
        summary = RunSummary(
            requested=limit,
            fetched=fetched,
            published=published,
            malformed=malformed,
            normalizationUnresolved=unresolved,
            normalizationAmbiguous=ambiguous,
            normalizationErrors=normalization_errors,
            errors=errors,
        )
        completed = datetime.now(UTC)
        await self._publisher.publish(
            run_event(run_id, "IngestionRunCompleted", completed, status, summary)
        )
        result = IngestionResult(
            runId=run_id,
            status=status,
            summary=summary,
            startedAt=started,
            completedAt=completed,
        )
        self.last_run = result
        logger.info(
            "ingestion run completed",
            extra={
                "correlationId": run_id,
                "ingestionRunId": run_id,
                "status": status.value,
                "fetched": fetched,
                "published": published,
                "malformed": malformed,
            },
        )
        return result
