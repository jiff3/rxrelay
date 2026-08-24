CREATE TABLE watchlists (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_watchlists_owner_name ON watchlists (owner_id, lower(name));
CREATE INDEX idx_watchlists_owner_time ON watchlists (owner_id, created_at DESC, id);

INSERT INTO watchlists (id, owner_id, name, created_at, updated_at)
SELECT (
    substr(md5(owner_id), 1, 8) || '-' || substr(md5(owner_id), 9, 4) || '-' ||
    substr(md5(owner_id), 13, 4) || '-' || substr(md5(owner_id), 17, 4) || '-' ||
    substr(md5(owner_id), 21, 12)
)::uuid, owner_id, 'My watchlist', min(created_at), max(created_at)
FROM watchlist_entries
GROUP BY owner_id;

CREATE TABLE watchlist_items (
    id UUID PRIMARY KEY,
    watchlist_id UUID NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    medication_id UUID NOT NULL REFERENCES medications(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_watchlist_item UNIQUE (watchlist_id, medication_id)
);
CREATE INDEX idx_watchlist_items_medication ON watchlist_items (medication_id, watchlist_id);
CREATE INDEX idx_watchlist_items_watchlist_time ON watchlist_items (watchlist_id, created_at DESC, id);

INSERT INTO watchlist_items (id, watchlist_id, medication_id, created_at)
SELECT e.id, w.id, e.medication_id, e.created_at
FROM watchlist_entries e
JOIN watchlists w ON w.owner_id = e.owner_id;

DROP TABLE watchlist_entries;

ALTER TABLE audit_events
    ADD COLUMN aggregate_type VARCHAR(64),
    ADD COLUMN aggregate_id VARCHAR(100);
CREATE INDEX idx_audit_events_aggregate_time
    ON audit_events (aggregate_type, aggregate_id, occurred_at DESC);

CREATE INDEX idx_shortage_status_medication
    ON shortage_records (status, medication_id, source_updated_at DESC);
CREATE INDEX idx_drug_products_manufacturer_medication
    ON drug_products (manufacturer_id, medication_id);
CREATE INDEX idx_notifications_owner_all_time
    ON notifications (owner_id, is_read, created_at DESC, id);
CREATE INDEX idx_notifications_owner_unread
    ON notifications (owner_id, created_at DESC, id) WHERE is_read = FALSE;
CREATE INDEX idx_processed_events_time ON processed_events (processed_at DESC);
