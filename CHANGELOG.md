# Changelog

All notable changes are documented here. RxRelay follows [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-08-24

### Added

- Bounded openFDA shortage ingestion with deterministic RxNorm normalization, provenance, and durable run summaries.
- Spring MVC medication, timeline, watchlist, notification, overview, and event-inspection APIs backed by PostgreSQL.
- At-least-once Kafka processing with persistent idempotency, bounded retry, dead-letter records, and a transactional outbox.
- Fail-soft Redis medication caching and gateway rate limiting.
- Responsive React interface covering the primary user and developer-review flows.
- Multi-stage containers, health-gated Docker Compose, stateless Kubernetes manifests, GitHub Actions, observability, reliability tooling, and backup/restore scripts.
- Deterministic Java, Python, React, Playwright, contract, database, Kafka, and Compose test paths.

This is the first release-ready source state. It does not imply that a GitHub release or container publication exists.
