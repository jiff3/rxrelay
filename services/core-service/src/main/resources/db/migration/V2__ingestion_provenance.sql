CREATE TABLE ingestion_runs (
    id UUID PRIMARY KEY,
    source VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_count INTEGER,
    fetched_count INTEGER,
    published_count INTEGER,
    malformed_count INTEGER,
    normalization_unresolved_count INTEGER,
    normalization_ambiguous_count INTEGER,
    normalization_error_count INTEGER,
    error_summary TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_ingestion_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_ingestion_run_counts CHECK (
        requested_count IS NULL OR requested_count >= 0
    )
);
CREATE INDEX idx_ingestion_runs_started ON ingestion_runs (started_at DESC);

CREATE TABLE manufacturers (
    id UUID PRIMARY KEY,
    source_name VARCHAR(300) NOT NULL,
    normalized_name VARCHAR(300) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE drug_products (
    id UUID PRIMARY KEY,
    medication_id UUID NOT NULL REFERENCES medications(id),
    manufacturer_id UUID REFERENCES manufacturers(id),
    source VARCHAR(50) NOT NULL,
    source_product_key VARCHAR(500) NOT NULL,
    package_ndc VARCHAR(32),
    dosage_form VARCHAR(200),
    presentation TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_drug_product_source_key UNIQUE (source, source_product_key)
);
CREATE INDEX idx_drug_products_medication ON drug_products (medication_id);
CREATE INDEX idx_drug_products_ndc ON drug_products (package_ndc);

ALTER TABLE medications
    ADD COLUMN source_name VARCHAR(300),
    ADD COLUMN normalization_status VARCHAR(32) NOT NULL DEFAULT 'UNRESOLVED',
    ADD CONSTRAINT ck_medication_normalization_status CHECK (
        normalization_status IN ('SOURCE_PROVIDED', 'RESOLVED', 'UNRESOLVED', 'AMBIGUOUS', 'ERROR', 'SKIPPED')
    );

ALTER TABLE shortage_records
    ADD COLUMN source VARCHAR(50) NOT NULL DEFAULT 'openfda',
    ADD COLUMN drug_product_id UUID REFERENCES drug_products(id),
    ADD COLUMN latest_ingestion_run_id UUID REFERENCES ingestion_runs(id),
    ADD COLUMN source_status VARCHAR(100),
    ADD COLUMN source_update_type VARCHAR(100),
    ADD COLUMN normalized_status VARCHAR(32),
    ADD COLUMN state_fingerprint VARCHAR(64),
    ADD COLUMN resolved_note TEXT,
    ADD COLUMN related_info TEXT,
    ADD COLUMN related_info_link TEXT,
    ADD COLUMN initial_posting_at TIMESTAMPTZ,
    ADD COLUMN source_change_at TIMESTAMPTZ,
    ADD COLUMN discontinued_at TIMESTAMPTZ,
    ADD COLUMN first_seen_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_shortage_normalized_status CHECK (
        normalized_status IS NULL OR normalized_status IN ('CURRENT', 'RESOLVED', 'TO_BE_DISCONTINUED', 'UNKNOWN')
    );
ALTER TABLE shortage_records
    ALTER COLUMN payload_hash TYPE VARCHAR(64);
CREATE INDEX idx_shortage_source_status ON shortage_records (source, normalized_status);
CREATE INDEX idx_shortage_latest_run ON shortage_records (latest_ingestion_run_id);

CREATE TABLE shortage_therapeutic_categories (
    shortage_record_id UUID NOT NULL REFERENCES shortage_records(id) ON DELETE CASCADE,
    category VARCHAR(200) NOT NULL,
    PRIMARY KEY (shortage_record_id, category)
);

CREATE TABLE shortage_observations (
    id UUID PRIMARY KEY,
    observation_event_id VARCHAR(200) NOT NULL UNIQUE,
    shortage_record_id UUID NOT NULL REFERENCES shortage_records(id),
    ingestion_run_id UUID NOT NULL REFERENCES ingestion_runs(id),
    source_payload_hash VARCHAR(64) NOT NULL,
    state_fingerprint VARCHAR(64) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_shortage_observations_record_time ON shortage_observations (shortage_record_id, observed_at DESC);
CREATE INDEX idx_shortage_observations_run ON shortage_observations (ingestion_run_id);

ALTER TABLE status_changes
    ADD COLUMN ingestion_run_id UUID REFERENCES ingestion_runs(id),
    ADD COLUMN previous_state_fingerprint VARCHAR(64),
    ADD COLUMN new_state_fingerprint VARCHAR(64),
    ADD COLUMN event_type VARCHAR(64) NOT NULL DEFAULT 'DrugAvailabilityChanged';

ALTER TABLE processed_events
    ADD COLUMN event_type VARCHAR(64),
    ADD COLUMN ingestion_run_id UUID REFERENCES ingestion_runs(id),
    ADD COLUMN source_record_id VARCHAR(500);
CREATE INDEX idx_processed_events_run ON processed_events (ingestion_run_id);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    ingestion_run_id UUID REFERENCES ingestion_runs(id),
    source_record_id VARCHAR(500),
    message TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_events_run_time ON audit_events (ingestion_run_id, occurred_at DESC);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    aggregate_key VARCHAR(500) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0)
);
CREATE INDEX idx_outbox_pending ON outbox_events (available_at, created_at) WHERE published_at IS NULL;
