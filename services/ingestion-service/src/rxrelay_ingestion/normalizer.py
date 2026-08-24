import hashlib
import json
import re
import uuid
from datetime import UTC, datetime

from .models import (
    IngestionEvent,
    NormalizationStatus,
    OpenFdaShortageRecord,
    RunStatus,
    RunSummary,
    RxNormResolution,
    ShortageObservationPayload,
    SourceStatus,
    SourceValues,
)

EVENT_NAMESPACE = uuid.UUID("a3bb2f7c-5c63-4e65-a68b-3df6a3109647")


def normalize_name(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", value.casefold()).strip()


def stable_json(value: object) -> str:
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=True,
        default=lambda item: item.isoformat() if isinstance(item, datetime) else str(item),
    )


def sha256(value: object) -> str:
    return hashlib.sha256(stable_json(value).encode()).hexdigest()


def parse_source_date(value: str | None, field: str, required: bool = False) -> datetime | None:
    if value is None:
        if required:
            raise ValueError(f"{field} is required")
        return None
    for pattern in ("%m/%d/%Y", "%Y-%m-%d", "%Y%m%d", "%m/%d/%y"):
        try:
            return datetime.strptime(value, pattern).replace(tzinfo=UTC)
        except ValueError:
            continue
    raise ValueError(f"{field} has unsupported date format: {value!r}")


def normalize_status(value: str) -> SourceStatus:
    normalized = normalize_name(value)
    mapping = {
        "current": SourceStatus.CURRENT,
        "resolved": SourceStatus.RESOLVED,
        "to be discontinued": SourceStatus.TO_BE_DISCONTINUED,
    }
    return mapping.get(normalized, SourceStatus.UNKNOWN)


def source_identity(record: OpenFdaShortageRecord) -> str:
    # openFDA exposes no primary key. These source-provided values are stable across state edits.
    material = {
        "packageNdc": record.package_ndc.casefold(),
        "genericName": record.generic_name.casefold(),
        "companyName": record.company_name.casefold(),
        "initialPostingDate": record.initial_posting_date,
    }
    return f"openfda:{record.package_ndc}:{sha256(material)[:20]}"


def source_resolution(record: OpenFdaShortageRecord) -> RxNormResolution | None:
    values = sorted(set(record.openfda.rxcui)) if record.openfda else []
    if len(values) == 1:
        return RxNormResolution(
            status=NormalizationStatus.SOURCE_PROVIDED,
            query=record.generic_name,
            rx_cui=values[0],
            canonical_name=record.generic_name,
            candidates=1,
        )
    if len(values) > 1:
        return RxNormResolution(
            status=NormalizationStatus.AMBIGUOUS,
            query=record.generic_name,
            candidates=len(values),
        )
    return None


def normalize_record(
    raw_record: dict[str, object],
    record: OpenFdaShortageRecord,
    resolution: RxNormResolution,
    observed_at: datetime,
    run_id: str,
) -> IngestionEvent:
    update_date = parse_source_date(record.update_date, "update_date", required=True)
    initial_date = parse_source_date(
        record.initial_posting_date, "initial_posting_date", required=True
    )
    assert update_date is not None and initial_date is not None
    source_values = SourceValues(
        packageNdc=record.package_ndc,
        genericName=record.generic_name,
        companyName=record.company_name,
        presentation=record.presentation,
        updateType=record.update_type,
        availability=record.availability,
        relatedInfo=record.related_info,
        relatedInfoLink=record.related_info_link,
        resolvedNote=record.resolved_note,
        shortageReason=record.shortage_reason,
        therapeuticCategories=sorted(set(record.therapeutic_category)),
        dosageForm=record.dosage_form,
        status=record.status,
        updateDate=update_date,
        changeDate=parse_source_date(record.change_date, "change_date"),
        discontinuedDate=parse_source_date(record.discontinued_date, "discontinued_date"),
        initialPostingDate=initial_date,
    )
    source_record_id = source_identity(record)
    payload_hash = sha256(raw_record)
    # Exclude provenance-only dates and update labels: an FDA "Reverified" timestamp alone is
    # not an availability change. All values below can alter the useful supply representation.
    state_material = {
        "status": source_values.status,
        "availability": source_values.availability,
        "presentation": source_values.presentation,
        "shortageReason": source_values.shortage_reason,
        "resolvedNote": source_values.resolved_note,
        "relatedInfo": source_values.related_info,
        "relatedInfoLink": source_values.related_info_link,
        "discontinuedDate": source_values.discontinued_date,
        "dosageForm": source_values.dosage_form,
    }
    state_fingerprint = sha256(state_material)
    canonical = resolution.canonical_name or record.generic_name
    payload = ShortageObservationPayload(
        sourceRecordId=source_record_id,
        sourcePayloadHash=payload_hash,
        stateFingerprint=state_fingerprint,
        normalizedName=normalize_name(canonical),
        canonicalName=canonical,
        rxCui=resolution.rx_cui,
        normalizationStatus=resolution.status,
        normalizationQuery=resolution.query,
        normalizedStatus=normalize_status(record.status),
        sourceValues=source_values,
    )
    event_id = str(
        uuid.uuid5(EVENT_NAMESPACE, f"observation:{run_id}:{source_record_id}:{payload_hash}")
    )
    return IngestionEvent(
        eventId=event_id,
        eventType="ShortageObserved",
        occurredAt=observed_at,
        correlationId=run_id,
        ingestionRunId=run_id,
        payload=payload,
    )


def run_event(
    run_id: str,
    event_type: str,
    occurred_at: datetime,
    status: RunStatus,
    summary: RunSummary | None = None,
) -> IngestionEvent:
    event_id = str(uuid.uuid5(EVENT_NAMESPACE, f"{event_type}:{run_id}"))
    return IngestionEvent(
        eventId=event_id,
        eventType=event_type,
        occurredAt=occurred_at,
        correlationId=run_id,
        ingestionRunId=run_id,
        runStatus=status,
        summary=summary,
    )
