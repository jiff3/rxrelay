# Observability

RxRelay's local observability design uses application-native health, metrics, and structured logs. PostgreSQL, Redis, and Kafka remain the only mandatory runtime infrastructure; Prometheus is an optional profile.

## Health and readiness

| Component | Endpoint | Meaning |
|---|---|---|
| gateway | `/actuator/health/liveness` and `/actuator/health/readiness` | reactive edge process and dependencies reported by Actuator |
| core service | `/actuator/health/liveness` | JVM/application state only |
| core service | `/actuator/health/readiness` | readiness state, PostgreSQL, and bounded Kafka admin connectivity |
| ingestion | `/health/live` | process is responding |
| ingestion | `/health/ready` | service is initialized and reports active/last ingestion state |

Redis deliberately is not in the core readiness group. It appears in general Actuator health, but a Redis outage must not remove an otherwise-correct PostgreSQL-backed API from service. Kafka readiness uses a bounded admin operation; it does not claim that an external consumer has processed a particular record.

## Metrics

Spring exposes Prometheus text at `/actuator/prometheus`. Important meter families include:

- standard `http.server.requests` latency/status meters;
- datasource/Hikari and JVM/process metrics;
- Spring Kafka producer/consumer metrics;
- Spring cache meters, with Redis cache statistics enabled;
- `rxrelay.kafka.events.processed`, `.duplicate`, `.retry`, `.dead_letter`, and `.stale`;
- `rxrelay.outbox.events{outcome=published|failed}`;
- `rxrelay.cache.errors{operation,cache}` for fail-soft Redis exceptions.

FastAPI exposes `/metrics` with:

- `rxrelay_ingestion_http_requests_total{method,route,status}`;
- `rxrelay_ingestion_http_request_duration_seconds{method,route}`;
- `rxrelay_ingestion_runs_total{outcome}` and `rxrelay_ingestion_duration_seconds`;
- `rxrelay_ingestion_records_total{outcome}`;
- last-run record gauges and completion timestamp.

HTTP route labels use templates such as `/api/v1/ingestions`, with unmatched routes collapsed to `unmatched`, preventing arbitrary IDs from creating unbounded metric cardinality.

## Correlation and logs

Gateway, core, and ingestion accept `X-Request-Id` only when it matches `[A-Za-z0-9._:-]{1,128}`; otherwise they generate a UUID. The gateway propagates the value, core places it in MDC and error responses, and event envelopes carry their own correlation ID. Java console logs use Logstash JSON. Python uses JSON-formatted application logs. Raw payload bodies, credentials, and patient data are not logged.

## Optional Prometheus

```bash
docker compose -f docker-compose.yml -f infrastructure/docker/compose.observability.yml \
  --profile observability up --build --wait
```

Prometheus is available on port 9090, retains at most 24 hours/256 MB by configuration, and is capped at 384 MB memory. Grafana is intentionally omitted: Prometheus plus the application UIs provide sufficient local evidence without making observability mandatory or heavy.

## Alerting boundary

No production alert routes are claimed. Useful future alert inputs are sustained HTTP 5xx rate, Kafka readiness failure, increasing DLQ counters, exhausted outbox rows, and a stale last-ingestion timestamp. Thresholds need operating data and therefore are not invented here.
