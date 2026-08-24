# ADR-003: Kafka for asynchronous status-change processing

- Status: Accepted
- Date: 2026-08-22

## Context

The ingestion adapter must tolerate core-service downtime and should not share its database. Repeated upstream observations must not produce repeated history or alerts.

## Decision

Publish run lifecycle and normalized observations to `rxrelay.shortage.observed.v1`. Use one partition in the small local feed; the handler also tolerates an observation arriving before its run-start marker. Treat delivery as at least once and enforce idempotency with a durable event ID in the core transaction. Persist resulting `DrugAvailabilityChanged` and `NotificationCreated` messages in a transactional outbox before publishing to versioned topics. Send malformed or exhausted records to `rxrelay.availability.dlq.v1`. Use one KRaft broker for local development.

## Consequences

Retrieval, persistence, and downstream publication can recover independently, while event contracts remain inspectable. Unchanged observations are evidence but do not create domain events. Kafka adds memory and operational cost, so local retention and heap are constrained. A production cluster needs replication; the bounded poison-message policy is documented in `docs/reliability.md`.
