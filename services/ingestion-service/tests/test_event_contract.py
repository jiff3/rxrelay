import json
from datetime import UTC, datetime
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

from rxrelay_ingestion.models import (
    NormalizationStatus,
    OpenFdaShortageRecord,
    RunStatus,
    RunSummary,
    RxNormResolution,
)
from rxrelay_ingestion.normalizer import normalize_record, run_event


def test_run_completion_event_validates_against_contract() -> None:
    root = Path(__file__).parents[3]
    schema = json.loads((root / "contracts/events/ingestion-event.v1.schema.json").read_text())
    event = run_event(
        "00000000-0000-0000-0000-000000000001",
        "IngestionRunCompleted",
        datetime(2026, 8, 22, tzinfo=UTC),
        RunStatus.SUCCEEDED,
        RunSummary(
            requested=1,
            fetched=1,
            published=1,
            malformed=0,
            normalizationUnresolved=0,
            normalizationAmbiguous=0,
            normalizationErrors=0,
            errors=[],
        ),
    )
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(
        event.model_dump(mode="json", by_alias=True, exclude_none=True)
    )


def test_normalized_observation_validates_against_shared_contract() -> None:
    root = Path(__file__).parents[3]
    fixture = json.loads(
        (Path(__file__).parent / "fixtures/captured_openfda_shortages_2026-08-22.json").read_text()
    )
    raw = fixture["response"]["results"][0]
    source = OpenFdaShortageRecord.model_validate(raw)
    event = normalize_record(
        raw,
        source,
        RxNormResolution(status=NormalizationStatus.UNRESOLVED, query=source.generic_name),
        datetime(2026, 8, 22, tzinfo=UTC),
        "00000000-0000-4000-8000-000000000001",
    )
    schema = json.loads((root / "contracts/events/ingestion-event.v1.schema.json").read_text())
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(
        event.model_dump(mode="json", by_alias=True, exclude_none=True)
    )


def test_all_checked_in_event_schemas_are_valid_draft_2020_12() -> None:
    root = Path(__file__).parents[3]
    for path in sorted((root / "contracts/events").glob("*.schema.json")):
        Draft202012Validator.check_schema(json.loads(path.read_text()))
