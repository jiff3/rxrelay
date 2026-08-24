CREATE TABLE medications (
    id UUID PRIMARY KEY,
    canonical_name VARCHAR(300) NOT NULL,
    normalized_name VARCHAR(300) NOT NULL UNIQUE,
    rx_cui VARCHAR(32) UNIQUE,
    generic_name VARCHAR(300),
    dosage_form VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_medications_canonical_name_lower ON medications (lower(canonical_name));
CREATE INDEX idx_medications_generic_name_lower ON medications (lower(generic_name));

CREATE TABLE shortage_records (
    id UUID PRIMARY KEY,
    source_record_id VARCHAR(500) NOT NULL UNIQUE,
    medication_id UUID NOT NULL REFERENCES medications(id),
    status VARCHAR(32) NOT NULL,
    availability TEXT,
    reason TEXT,
    company VARCHAR(300),
    presentation TEXT,
    source_updated_at TIMESTAMPTZ,
    payload_hash CHAR(64) NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_shortage_medication ON shortage_records (medication_id, source_updated_at DESC);

CREATE TABLE status_changes (
    id UUID PRIMARY KEY,
    shortage_record_id UUID NOT NULL REFERENCES shortage_records(id),
    previous_status VARCHAR(32),
    current_status VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source_event_id VARCHAR(200) NOT NULL UNIQUE
);
CREATE INDEX idx_status_changes_record_time ON status_changes (shortage_record_id, occurred_at DESC);

CREATE TABLE watchlist_entries (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL,
    medication_id UUID NOT NULL REFERENCES medications(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_watchlist_owner_medication UNIQUE (owner_id, medication_id)
);
CREATE INDEX idx_watchlist_medication ON watchlist_entries (medication_id);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL,
    medication_id UUID NOT NULL REFERENCES medications(id),
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notifications_owner_time ON notifications (owner_id, created_at DESC);

CREATE TABLE processed_events (
    event_id VARCHAR(200) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
