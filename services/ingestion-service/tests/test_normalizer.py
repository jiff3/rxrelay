import json
from datetime import UTC, datetime
from pathlib import Path

from rxrelay_ingestion.models import (
    NormalizationStatus,
    OpenFdaShortageRecord,
    RxNormResolution,
    SourceStatus,
)
from rxrelay_ingestion.normalizer import normalize_name, normalize_record, source_identity

FIXTURE = Path(__file__).parent / "fixtures" / "captured_openfda_shortages_2026-08-22.json"


def record() -> tuple[dict[str, object], OpenFdaShortageRecord]:
    raw = json.loads(FIXTURE.read_text(encoding="utf-8"))["response"]["results"][0]
    return raw, OpenFdaShortageRecord.model_validate(raw)


def test_name_normalization_is_stable() -> None:
    assert normalize_name("  Dextrose 5% (Injection) ") == "dextrose 5 injection"


def test_same_state_has_stable_identity_and_fingerprint_but_run_scoped_event_id() -> None:
    raw, source = record()
    resolved = RxNormResolution(
        status=NormalizationStatus.RESOLVED,
        query=source.generic_name,
        rx_cui="2645468",
        canonical_name=source.generic_name,
        candidates=1,
    )
    now = datetime(2026, 8, 22, tzinfo=UTC)
    first = normalize_record(raw, source, resolved, now, "00000000-0000-0000-0000-000000000001")
    repeat = normalize_record(raw, source, resolved, now, "00000000-0000-0000-0000-000000000001")
    later = normalize_record(raw, source, resolved, now, "00000000-0000-0000-0000-000000000002")
    assert first.payload is not None and later.payload is not None
    assert source_identity(source) == first.payload.source_record_id
    assert first.payload.state_fingerprint == later.payload.state_fingerprint
    assert first.event_id == repeat.event_id
    assert first.event_id != later.event_id


def test_status_change_changes_fingerprint_not_source_identity() -> None:
    raw, source = record()
    changed_raw = {**raw, "status": "Resolved", "availability": None}
    changed = OpenFdaShortageRecord.model_validate(changed_raw)
    resolution = RxNormResolution(status=NormalizationStatus.UNRESOLVED, query=source.generic_name)
    now = datetime(2026, 8, 22, tzinfo=UTC)
    before = normalize_record(raw, source, resolution, now, "00000000-0000-0000-0000-000000000001")
    after = normalize_record(
        changed_raw, changed, resolution, now, "00000000-0000-0000-0000-000000000002"
    )
    assert before.payload is not None and after.payload is not None
    assert before.payload.source_record_id == after.payload.source_record_id
    assert before.payload.state_fingerprint != after.payload.state_fingerprint
    assert after.payload.normalized_status == SourceStatus.RESOLVED


def test_optional_source_date_is_hashable() -> None:
    raw, _ = record()
    dated_raw = {**raw, "discontinued_date": "08/22/2026"}
    source = OpenFdaShortageRecord.model_validate(dated_raw)
    event = normalize_record(
        dated_raw,
        source,
        RxNormResolution(status=NormalizationStatus.UNRESOLVED, query=source.generic_name),
        datetime(2026, 8, 22, tzinfo=UTC),
        "00000000-0000-0000-0000-000000000001",
    )
    assert event.payload is not None
    assert len(event.payload.state_fingerprint) == 64
