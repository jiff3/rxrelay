# ADR-001: Service boundaries and architecture

- Status: Accepted
- Date: 2026-08-22

## Context

Public data retrieval has different availability, retry, and release characteristics from the user-facing query model. Edge traffic concerns should not leak into either domain.

## Decision

Use four independently runnable components: a Spring Cloud Gateway edge, a Spring MVC transactional core, a Python FastAPI ingestion adapter, and a React web client. Connect ingestion to core asynchronously through a versioned event.

## Consequences

Each service has a narrow reason to change and can fail independently. The tradeoff is operational overhead and duplicated deployment configuration. Four components are the upper bound for the initial system; further splitting requires evidence.
