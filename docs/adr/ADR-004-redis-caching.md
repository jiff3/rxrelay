# ADR-004: Redis caching strategy

- Status: Accepted
- Date: 2026-08-22

## Context

Common medication searches and edge abuse protection benefit from shared, low-latency ephemeral state. Neither may become required for data correctness.

## Decision

Use Redis for 60-second medication-search snapshots, five-minute medication-detail snapshots, and gateway token-bucket counters. Cache only serializable DTO snapshots, never managed JPA entities. Clear both core caches after applied ingestion events. A cache error handler treats Redis operations as optional and falls through to PostgreSQL. Configure allkeys-LRU and no persistence in local Compose.

## Consequences

Multiple replicas share cache and rate-limit state. Core cache loss only increases database load, and
rate-limit reset is acceptable. The development gateway fails open when Redis rate limiting is
unavailable so authoritative PostgreSQL APIs remain reachable, and emits a warning without request
identity. An Internet deployment should select fail-open or fail-closed edge behavior from its own
threat model and place a managed perimeter control ahead of this demo topology.
