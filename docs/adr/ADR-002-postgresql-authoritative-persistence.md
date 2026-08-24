# ADR-002: PostgreSQL as authoritative persistence

- Status: Accepted
- Date: 2026-08-22

## Context

Status transitions, watches, and notifications require relational constraints and atomic updates. Search volume and data size do not justify a separate search engine.

## Decision

Use PostgreSQL for all durable domain and idempotency state, managed by forward-only Flyway migrations. Use case-insensitive SQL matching backed by `pg_trgm` GIN indexes for the initial substring-search experience.

## Consequences

Transactions can atomically apply an event and generate notifications. Search remains operationally simple on an 8 GB development machine. More advanced ranking may later require PostgreSQL full-text or trigram indexes before considering another datastore.
