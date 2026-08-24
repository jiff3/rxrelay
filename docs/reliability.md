# Reliability and recovery

## Guarantees

- Input consumption is **at least once**. Kafka offsets and PostgreSQL are not one distributed transaction.
- `processed_events.event_id` is the durable idempotency key. Claim, domain mutations, notifications, audit rows, outbox rows, and final `PROCESSED` state commit or roll back together.
- Duplicate delivery after commit is a no-op. Delivery after a database rollback can be claimed and processed normally.
- Outbound publication is **at least once**. State and outbox insert are atomic, but a publisher can send successfully and fail before recording `published_at`; downstream consumers must deduplicate by event ID.
- Redis is never authoritative. Read/write/eviction failures fall through to PostgreSQL, while event processing continues after logging the cache failure.
- Older upstream `updateDate` observations remain provenance evidence but cannot replace newer current state or create a transition.

## Bounded failure policies

| Path | Policy | Terminal state |
|---|---|---|
| openFDA/RxNorm HTTP | timeout plus bounded exponential retry for connection errors, 429, and 5xx | run `FAILED` or `PARTIAL` |
| ingestion Kafka producer | finite start/send timeouts; discard failed producer so a later run can reconnect | request returns dependency unavailable |
| core Kafka consumer | initial attempt plus two retries, exponential 500 ms to 5 s by default | validated DLQ envelope |
| malformed/invalid event | no retry | DLQ immediately |
| outbox publisher | finite producer/send timeout and exponential 1–60 s delay | `failed_at` after eight attempts by default |

Dead-letter publication is synchronous and bounded. If publishing the DLQ record fails, recovery throws instead of acknowledging and losing the poison record. The event inspection API reports `PROCESSING`, `RETRYING`, `PROCESSED`, or `DEAD_LETTERED`, received/processed times, retry count, safe failure code, and dead-letter topic.

## Reliability Lab

The lab endpoints only exist under the `reliability-lab` Spring profile. They accept explicit synthetic test events and can arm one to three consumer failures. They are omitted from the public OpenAPI contract and must not be enabled on an Internet-facing deployment.

Start the stack and core service with `SPRING_PROFILES_ACTIVE=reliability-lab`, then use:

```powershell
./scripts/reliability/duplicate-event.ps1
./scripts/reliability/consumer-retry.ps1
./scripts/reliability/malformed-event.ps1
./scripts/reliability/redis-outage.ps1
python ./scripts/reliability/upstream-timeout.py
```

The Redis script restarts only the Compose `redis` service in a `finally` block. The upstream-timeout script starts a loopback-only stalled HTTP server and verifies the configured client timeout; it does not alter external services. See `scripts/reliability/README.md` for parameters and expected invariants.

The repeatable broker-backed verification test uses one embedded PostgreSQL instance and one embedded KRaft broker:

```bash
./mvnw -pl services/core-service -Drxrelay.reliability=true -Dtest=KafkaReliabilityPipelineTest test
```

It publishes the same event three times, creates a watched transition three times, injects two recoverable handler failures, sends malformed JSON, consumes both outbound notification and DLQ topics, and prints the observed database counts only after all assertions pass. It is opt-in to keep the normal test suite light on an 8 GB machine.

### Release verification observations

The following were actually observed on 2026-08-24 against the running single-broker Compose stack. All status changes in this section used the explicitly synthetic Reliability Lab fixture; none represents an FDA-reported change.

| Scenario | Actual observation |
|---|---|
| Same event delivered three times | event `11111111-1111-4111-8111-111111111111` increased `processed_events`, observations, and status changes by exactly one each; it created no notification before the drug was watched |
| Watched change delivered three times | event `44444444-4444-4444-8444-444444444444` created one transition and one notification; medication search/detail cache keys present before delivery were absent afterward |
| Handler retry | event `55555555-5555-4555-8555-555555555555` recovered after two armed failures, recorded `retry_count=2`, and ended `PROCESSED` with one transition and one notification |
| Malformed JSON | one deterministic inspector row ended `DEAD_LETTERED` with `retry_count=1`; the DLQ topic gained one record and the safe error named a JSON EOF parse failure |
| Redis outage | with only Compose Redis stopped, `GET /api/v1/drugs?size=1` remained successful and reported 59 authoritative medication rows; the script restarted Redis and waited for health |
| Upstream timeout | the loopback stalled server exhausted two bounded attempts and returned `Bounded timeout recovered as UpstreamError: ReadTimeout`; ingestion readiness remained healthy |

The retry inspector flow was:

```text
RECEIVED
RETRIED (2 recorded retry attempts)
TRANSITION_PERSISTED
NOTIFICATION_CREATED
PROCESSED
```

The Redis outage initially exposed a real defect: `ApplicationConfiguration` implemented the wrong `CachingConfigurer#errorHandler` signature, so Spring used its default throwing handler and returned HTTP 500. The configuration was corrected to override the no-argument method, a focused unit test was added, SpotBugs was rerun, and the same outage script then passed in 7.536 seconds. This is the recovery evidence; the initial failed attempt is not hidden.

The database after the two real ingestions and reliability scenarios contained 208 processed-event rows, 203 observations, 103 status changes, 105 outbox rows, two notifications, and one dead-lettered event. Those are development-database totals, not user or traffic metrics.

## Operator checks

- `GET /actuator/health/readiness` includes PostgreSQL and Kafka. Redis is omitted from readiness because losing cache must not take the authoritative API out of service.
- `/actuator/prometheus` includes event outcomes, retries, dead letters, stale observations, and outbox publication outcomes plus Spring Kafka consumer metrics.
- `GET /api/v1/system/events?state=DEAD_LETTERED` gives bounded, sanitized processing metadata.
- Structured JSON logs carry request IDs and event correlation/event IDs without payloads or secrets.
