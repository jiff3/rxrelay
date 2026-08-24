from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, field_validator


class SourceStatus(StrEnum):
    CURRENT = "CURRENT"
    RESOLVED = "RESOLVED"
    TO_BE_DISCONTINUED = "TO_BE_DISCONTINUED"
    UNKNOWN = "UNKNOWN"


class NormalizationStatus(StrEnum):
    SOURCE_PROVIDED = "SOURCE_PROVIDED"
    RESOLVED = "RESOLVED"
    UNRESOLVED = "UNRESOLVED"
    AMBIGUOUS = "AMBIGUOUS"
    ERROR = "ERROR"
    SKIPPED = "SKIPPED"


class RunStatus(StrEnum):
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    PARTIAL = "PARTIAL"
    FAILED = "FAILED"


class OpenFdaAnnotations(BaseModel):
    model_config = ConfigDict(extra="allow")

    rxcui: list[str] = Field(default_factory=list)
    package_ndc: list[str] = Field(default_factory=list)
    product_ndc: list[str] = Field(default_factory=list)


class OpenFdaShortageRecord(BaseModel):
    """Typed adapter model for fields documented by the openFDA shortage endpoint."""

    model_config = ConfigDict(extra="allow")

    package_ndc: str
    generic_name: str
    company_name: str
    contact_info: str | None = None
    presentation: str
    update_type: str
    availability: str | None = None
    related_info: str | None = None
    related_info_link: str | None = None
    resolved_note: str | None = None
    shortage_reason: str | None = None
    therapeutic_category: list[str] = Field(default_factory=list)
    dosage_form: str | None = None
    status: str
    update_date: str
    change_date: str | None = None
    discontinued_date: str | None = None
    initial_posting_date: str
    openfda: OpenFdaAnnotations | None = None

    @field_validator("*")
    @classmethod
    def trim_strings(cls, value: object) -> object:
        if isinstance(value, str):
            return " ".join(value.split())
        if isinstance(value, list):
            return [" ".join(item.split()) if isinstance(item, str) else item for item in value]
        return value


class OpenFdaMetaResults(BaseModel):
    skip: int = Field(ge=0)
    limit: int = Field(ge=1)
    total: int = Field(ge=0)


class OpenFdaMeta(BaseModel):
    last_updated: str | None = None
    results: OpenFdaMetaResults


class OpenFdaPage(BaseModel):
    meta: OpenFdaMeta
    results: list[dict[str, object]]


class RxNormIdGroup(BaseModel):
    rxnorm_id: list[str] = Field(alias="rxnormId", default_factory=list)


class RxNormSearchResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id_group: RxNormIdGroup = Field(alias="idGroup", default_factory=RxNormIdGroup)


class RxNormProperties(BaseModel):
    name: str


class RxNormPropertiesResponse(BaseModel):
    properties: RxNormProperties


class RxNormResolution(BaseModel):
    status: NormalizationStatus
    query: str | None = None
    rx_cui: str | None = None
    canonical_name: str | None = None
    candidates: int = 0


class SourceValues(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    package_ndc: str = Field(alias="packageNdc")
    generic_name: str = Field(alias="genericName")
    company_name: str = Field(alias="companyName")
    presentation: str
    update_type: str = Field(alias="updateType")
    availability: str | None
    related_info: str | None = Field(alias="relatedInfo")
    related_info_link: str | None = Field(alias="relatedInfoLink")
    resolved_note: str | None = Field(alias="resolvedNote")
    shortage_reason: str | None = Field(alias="shortageReason")
    therapeutic_categories: list[str] = Field(alias="therapeuticCategories")
    dosage_form: str | None = Field(alias="dosageForm")
    status: str
    update_date: datetime = Field(alias="updateDate")
    change_date: datetime | None = Field(alias="changeDate")
    discontinued_date: datetime | None = Field(alias="discontinuedDate")
    initial_posting_date: datetime = Field(alias="initialPostingDate")


class ShortageObservationPayload(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    source_record_id: str = Field(alias="sourceRecordId")
    source_payload_hash: str = Field(alias="sourcePayloadHash", pattern=r"^[a-f0-9]{64}$")
    state_fingerprint: str = Field(alias="stateFingerprint", pattern=r"^[a-f0-9]{64}$")
    normalized_name: str = Field(alias="normalizedName")
    canonical_name: str = Field(alias="canonicalName")
    rx_cui: str | None = Field(alias="rxCui")
    normalization_status: NormalizationStatus = Field(alias="normalizationStatus")
    normalization_query: str | None = Field(alias="normalizationQuery")
    normalized_status: SourceStatus = Field(alias="normalizedStatus")
    source_values: SourceValues = Field(alias="sourceValues")


class RunSummary(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    requested: int = Field(ge=1)
    fetched: int = Field(ge=0)
    published: int = Field(ge=0)
    malformed: int = Field(ge=0)
    normalization_unresolved: int = Field(alias="normalizationUnresolved", ge=0)
    normalization_ambiguous: int = Field(alias="normalizationAmbiguous", ge=0)
    normalization_errors: int = Field(alias="normalizationErrors", ge=0)
    errors: list[str] = Field(default_factory=list, max_length=20)


class IngestionEvent(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    schema_version: str = Field(alias="schemaVersion", default="1.1")
    event_id: str = Field(alias="eventId")
    event_type: str = Field(alias="eventType")
    occurred_at: datetime = Field(alias="occurredAt")
    correlation_id: str = Field(alias="correlationId")
    producer: str = "rxrelay-ingestion"
    source: str = "openfda"
    ingestion_run_id: str = Field(alias="ingestionRunId")
    payload: ShortageObservationPayload | None = None
    summary: RunSummary | None = None
    run_status: RunStatus | None = Field(alias="runStatus", default=None)


class IngestionResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    run_id: str = Field(alias="runId")
    status: RunStatus
    summary: RunSummary
    started_at: datetime = Field(alias="startedAt")
    completed_at: datetime = Field(alias="completedAt")


class ActiveRun(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    run_id: str = Field(alias="runId")
    requested: int
    started_at: datetime = Field(alias="startedAt")


class ServiceStatus(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    status: str
    running: bool
    active_run: ActiveRun | None = Field(alias="activeRun")
    last_run: IngestionResult | None = Field(alias="lastRun")
