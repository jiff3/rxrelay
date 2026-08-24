# Data model

PostgreSQL is authoritative. The schema separates the latest queryable state, immutable observations, normalized identities, run provenance, and delivery bookkeeping.

```text
ingestion_runs 1 ---- * shortage_observations * ---- 1 shortage_records
       |                                                   |
       +---- * processed_events                            +---- * status_changes
       +---- * audit_events                                |
                                                           +---- 1 drug_products
                                                                    |
manufacturers 1 -------------------------- * drug_products * ---- 1 medications
                                                                      |
                                                                      +---- * watchlist_items * ---- 1 watchlists
                                                                      +---- * notifications

outbox_events (independent transactional publication queue)
```

## Tables and invariants

| Table | Role | Important invariants |
|---|---|---|
| `medications` | Stable internal drug concept (the conceptual `drug_concept`) | unique normalized name; optional unique RxCUI; original source name and normalization outcome retained |
| `manufacturers` | Company identity as reported by FDA | one row per normalized source company name |
| `drug_products` | Source product/presentation | unique `(source, source_product_key)`; optional package NDC; links concept and company |
| `shortage_records` | Latest known state of one source record | unique derived `source_record_id`; latest run, source text, normalized status, payload hash, and state fingerprint retained |
| `shortage_therapeutic_categories` | Source-provided category values | unique value per shortage |
| `shortage_observations` | Append-only evidence that a record was seen in a run | unique observation event ID; links the exact run and hashes |
| `ingestion_runs` | Durable run lifecycle and counters | constrained status and non-negative requested count |
| `status_changes` | Append-only meaningful local state transitions | unique deterministic domain event ID; previous and new fingerprints retained |
| `processed_events` | Kafka consumer idempotency and inspection boundary | one row per incoming event ID; processing state, correlation/producer, retries, source location, safe error code, and DLQ timestamps |
| `outbox_events` | Transactional publication of availability and notification events | pending/terminal-failure indexes; correlation, bounded attempt/error metadata, published/failed timestamps |
| `audit_events` | Operational and application audit messages | linked to run/source or aggregate type/ID without storing secrets |
| `watchlists` | Named demo-user monitoring container | case-insensitive unique name per owner |
| `watchlist_items` | Watchlist-to-medication membership | one medication per watchlist; cascade on watchlist removal |
| `notifications` | In-app alerts created for watched transitions | owner-scoped reads; unique source availability event per owner |

## Identity and provenance

The FDA feed does not expose a primary record ID. RxRelay derives one from lower-cased source values expected to survive status edits: package NDC, generic name, company name, and initial posting date. Package NDC alone is not unique in the feed. The resulting identifier is namespaced as `openfda:<package-ndc>:<20-hex-digest>`.

Each observation links to an `ingestion_run` and stores:

- the derived source identifier;
- a SHA-256 hash of the canonicalized full source record;
- a separate meaningful-state fingerprint;
- normalized fields plus the original relevant FDA values;
- the observation and upstream dates.

Contact information is intentionally not persisted because it is not used by the product and can change independently. Full upstream responses are not stored; this keeps provenance useful without turning PostgreSQL into a JSON archive. The source record can be retrieved again using the recorded identity material and dates, subject to FDA retention and corrections.

Medication identity is separate from a product and shortage: several companies, NDC packages, or presentations may share one RxNorm concept. If RxNorm is unresolved or ambiguous, normalized source text forms the stable fallback. Automatic ingestion never merges uncertain identities.

All timestamps are UTC `TIMESTAMPTZ`. UUIDs are application-generated or deterministic where event replay identity matters. Flyway owns schema evolution; V1 establishes the initial model, V2 adds normalized ingestion/provenance/outbox persistence, V3 migrates flat watches into named watchlists while adding API query and unread-notification indexes, V4 adds PostgreSQL trigram indexes for bounded substring search, and V5 adds event state/retry/DLQ fields plus notification and outbox idempotency indexes.
