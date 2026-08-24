# Core REST API

The core service publishes a versioned Spring MVC API under `/api/v1`. The checked-in contract is [`contracts/openapi/rxrelay-api.v1.yaml`](../contracts/openapi/rxrelay-api.v1.yaml); a runtime document is available at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

## Resource model

- `/overview` returns persisted medication/shortage counts, the configured demo user's unread count, recent meaningful changes, recently updated medication identities, and the latest ingestion run. It does not estimate population-wide or inventory metrics.
- `/drugs` searches normalized identities using optional `query`, `status`, and `manufacturer` filters. Allowed sorts are `name` and `updatedAt`; `id` is always an implicit final tie-breaker.
- `/drugs/{id}/shortages` exposes source-provided fields separately from RxRelay status and observation timestamps.
- `/drugs/{id}/timeline` returns meaningful fingerprint changes, not every observation.
- `/watchlists` and nested `/items` are scoped to the configured `local-demo-user`. Watchlists can be created and deleted; nested medication items can be added and removed. They are local monitoring resources, not patient or pharmacy accounts.
- `/notifications` supports bounded pages and an `unreadOnly` filter. Marking a notification read is idempotent.
- `/system/ingestion-runs` returns sanitized counters and dates. `/system/events` provides a bounded processing-state filter, `/system/events/{eventId}` exposes idempotency/retry/DLQ metadata, and `/system/events/{eventId}/flow` returns only lifecycle steps supported by durable records. None exposes event payloads, database messages, or stack traces.

All collection endpoints cap `size` at 50. Validation errors, conflicts, missing resources, and unexpected failures share one error envelope with a correlation ID. The gateway and core both propagate or create `X-Request-Id`.

## Demo identity and security boundary

The application intentionally has one configurable demo identity (`DEMO_USER_ID`, default `local-demo-user`). Client headers cannot select another owner. This gives the domain a future authentication seam without presenting a spoofable header as identity or implying real healthcare accounts. Internet deployment requires a real authentication and authorization design.

## Cache behavior

Search snapshots expire after 60 seconds and detail snapshots after five minutes. A meaningful ingestion change clears both caches. Cache values are DTO snapshots rather than managed JPA entities. Redis errors are logged in bounded form and the request continues through PostgreSQL; cache state never determines correctness.
