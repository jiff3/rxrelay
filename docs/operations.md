# Operations

## Startup and shutdown

Start the full local topology with `docker compose up -d --build --wait --wait-timeout 360`. The extended finite window accommodates cold Spring/Kafka startup on the 8 GB target; it does not mask an unhealthy service. `docker compose ps` should show every service healthy. Stop it with `docker compose down`; named PostgreSQL/Kafka volumes remain. Use `docker compose down --volumes` only for an intentional development reset because it deletes local persisted state.

For host application development, start only dependencies with `docker compose up -d --wait postgres redis kafka`. Application processes can then use the `.env.example` host endpoints.

## Health and diagnostics

| Component | Liveness/readiness | Notes |
|---|---|---|
| web | `http://localhost:5173/healthz` | Static server only |
| gateway | `http://localhost:8080/actuator/health/liveness`, `/readiness` | Core routing and rate-limit edge |
| core | `http://localhost:8081/actuator/health/liveness`, `/readiness` | PostgreSQL/Kafka status; Redis is fail-soft |
| ingestion | `http://localhost:8090/health/live`, `/health/ready` | Producer readiness and run state |

Use `docker compose logs -f --tail 200 core-service gateway ingestion-service` for correlated application logs and `docker compose logs kafka postgres redis` for dependencies. Inspect resource use with `docker stats --no-stream`. Prometheus is optional:

```bash
docker compose -f docker-compose.yml -f infrastructure/docker/compose.observability.yml --profile observability up -d --build --wait
```

## Common failures

- PostgreSQL: inspect `pg_isready`, core migration output, URL/user/password, and available disk. Do not bypass a failed Flyway migration by editing its history table.
- Kafka: check broker health and advertised listener selection. Containers use `kafka:29092`; host processes use `localhost:9092`. Producer/consumer retries are bounded and terminal failures are visible through the event API and DLQ.
- Redis: search/detail caching and rate limiting degrade independently. Core continues from PostgreSQL; gateway readiness reports its edge dependency. Restore Redis service rather than treating cached contents as durable data.
- Ingestion: inspect `/api/v1/ingestions/status`, bounded error summaries, external connectivity, API-key limits, and Kafka readiness. No ingestion is scheduled implicitly.
- Startup ordering: use `docker compose up --wait`; do not replace health dependencies with arbitrary sleeps.

## Database maintenance

Core startup is the migration command. Before a release containing migrations, create and verify a backup. `scripts/database/backup.ps1` creates a custom-format `pg_dump` under ignored `backups/`. `restore.ps1` refuses to replace an existing database unless `-Force` is explicit and validates database identifiers. `validate-backup.ps1` restores into an isolated `rxrelay_restore_validation` database, compares key table counts, and then removes only that scratch database.

Exact commands and recovery boundaries are in [disaster-recovery.md](disaster-recovery.md).
