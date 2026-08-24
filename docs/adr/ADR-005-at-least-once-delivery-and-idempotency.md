# ADR-005: At-least-once delivery and transactional idempotency

- Status: Accepted
- Date: 2026-08-23

## Context

Kafka can redeliver after consumer failure, offset-commit uncertainty, or restart. Exactly-once claims spanning Kafka, PostgreSQL, Redis, and notification state would require tighter coupling and would still not remove the need for stable business identities.

## Decision

Use at-least-once input delivery. Every contract has a globally unique event ID. The core transaction first atomically inserts or reclaims `processed_events`; it then writes domain state, transitions, owner-deduplicated notifications, audit entries, outbox rows, and the final processed marker in the same PostgreSQL transaction. A committed event ID cannot be claimed again. A rolled-back transaction leaves no false success marker.

Record retry/dead-letter metadata in independent short transactions after failed handler transactions. Treat contract failures as non-retryable, bound transient retries, and publish a validated dead-letter envelope after exhaustion. Ignore stale upstream state updates while retaining their observation evidence.

## Consequences

Repeated delivery cannot duplicate a logical transition or notification. Database failure remains safely retryable. Kafka offset and database commit are not atomic, so harmless redelivery remains possible and expected. Event producers must never reuse an ID for different content. Processing-state rows add storage and require retention policy review for a long-running deployment.
