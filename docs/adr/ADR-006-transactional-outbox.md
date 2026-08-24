# ADR-006: Transactional outbox for core events

- Status: Accepted
- Date: 2026-08-23

## Context

The core service must emit `DrugAvailabilityChanged` and `NotificationCreated` only when their PostgreSQL state commits. Publishing inside the domain transaction creates a dual-write gap: Kafka can succeed while PostgreSQL rolls back, or PostgreSQL can commit while Kafka is unavailable.

## Decision

Write each outbound event to `outbox_events` in the same transaction as its state change. A scheduled publisher locks a bounded batch with `FOR UPDATE SKIP LOCKED`, publishes with finite Kafka timeouts, and records `published_at`. Failures use bounded exponential scheduling and are marked `failed_at` after the configured maximum attempts. Payloads are serialized from Bean-validated typed event models before insertion.

Do not use an outbox for ingestion-service observations. That service owns no authoritative relational transaction: its purpose is to stream a bounded external fetch, and a failed publish makes the manual run fail visibly. Adding a separate database there would increase operational cost without closing a database dual-write gap.

## Consequences

No committed core transition or notification is silently omitted merely because Kafka is temporarily unavailable. Publication is at least once: a send can succeed before the local `published_at` transaction fails, so consumers must use event IDs. Terminal outbox failures are visible in PostgreSQL/metrics and need operator retry or investigation; the publisher never loops infinitely.
