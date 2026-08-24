ALTER TABLE processed_events
    ALTER COLUMN processed_at DROP NOT NULL,
    ADD COLUMN schema_version VARCHAR(16),
    ADD COLUMN producer VARCHAR(100),
    ADD COLUMN correlation_id VARCHAR(128),
    ADD COLUMN processing_state VARCHAR(24) NOT NULL DEFAULT 'PROCESSED',
    ADD COLUMN received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN dead_lettered_at TIMESTAMPTZ,
    ADD COLUMN dead_letter_topic VARCHAR(200),
    ADD COLUMN last_error_code VARCHAR(200),
    ADD COLUMN source_topic VARCHAR(200),
    ADD COLUMN source_partition INTEGER,
    ADD COLUMN source_offset BIGINT,
    ADD CONSTRAINT ck_processed_event_state CHECK (
        processing_state IN ('PROCESSING', 'RETRYING', 'PROCESSED', 'DEAD_LETTERED')
    ),
    ADD CONSTRAINT ck_processed_event_retry_count CHECK (retry_count >= 0);

CREATE INDEX idx_processed_events_state_received
    ON processed_events (processing_state, received_at DESC, event_id);
CREATE INDEX idx_processed_events_correlation
    ON processed_events (correlation_id, received_at DESC);

ALTER TABLE notifications
    ADD COLUMN source_event_id VARCHAR(200),
    ADD COLUMN correlation_id VARCHAR(128);
CREATE UNIQUE INDEX uq_notifications_owner_source_event
    ON notifications (owner_id, source_event_id)
    WHERE source_event_id IS NOT NULL;

ALTER TABLE outbox_events
    ADD COLUMN correlation_id VARCHAR(128),
    ADD COLUMN failed_at TIMESTAMPTZ;
DROP INDEX idx_outbox_pending;
CREATE INDEX idx_outbox_pending
    ON outbox_events (available_at, created_at)
    WHERE published_at IS NULL AND failed_at IS NULL;
CREATE INDEX idx_outbox_failed
    ON outbox_events (failed_at DESC)
    WHERE failed_at IS NOT NULL;
