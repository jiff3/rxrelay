# Event contracts

`rxrelay.shortage.observed.v1` carries `ingestion-event.v1.schema.json`. It contains ordered
run lifecycle messages and normalized source observations. All messages use the source name as
their Kafka key so a run is observed in order; this feed is deliberately small enough that
single-key throughput is acceptable.

`drug-availability-changed.v1.schema.json` defines the deterministic domain event created by the
core service only when the persisted state fingerprint changes. Kafka is at least once, so the
database also retains observation IDs and processed event IDs.

`rxrelay.notification.created.v1` carries `notification-created.v1.schema.json`; the notification
row and its outbox event are written in the same PostgreSQL transaction. `rxrelay.availability.dlq.v1`
carries `ingestion-dead-lettered.v1.schema.json`, a bounded wrapper around a rejected value and its
source position. It never includes a stack trace.

Minor schema version 1.1 adds an explicit producer to ingestion and availability events. Topic
major versions remain v1. Every producer validates its typed event before sending or enqueueing it.

Breaking changes require a new schema and topic major version. Superseded pre-persistence
contracts are removed so consumers cannot accidentally implement an inactive shape.

Schema `$id` values use the reserved `.invalid` top-level domain as stable identifiers. They are
not publication URLs and do not imply that RxRelay controls a public schema host.
