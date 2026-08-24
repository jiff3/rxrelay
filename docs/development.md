# Development

## Prerequisites

- Docker Desktop and Docker Compose v2+
- Java 21 and Maven 3.9+ for host Java development
- Python 3.12+ for host ingestion development
- Node 22 and npm for host web development

Copy `.env.example` to `.env`. The defaults are local-development values only.

## Complete stack

The primary path builds and starts PostgreSQL, Redis, one KRaft broker, core, gateway, ingestion, and the static web application:

```bash
docker compose up --build --wait --wait-timeout 360
python scripts/smoke/compose-smoke.py
```

Use `docker compose logs -f core-service gateway ingestion-service` while developing and `docker compose down` to stop without deleting durable volumes. The Compose services have health-gated dependencies and an aggregate container memory limit of 2,848 MiB; Docker Desktop/Engine itself still needs additional host memory.

## Start dependencies

```bash
docker compose up -d --wait postgres redis kafka
docker compose ps
```

PostgreSQL uses Flyway migrations at core-service startup. The core's Kafka admin configuration creates the four versioned development topics on the single KRaft broker. Stop without deleting state using `docker compose down`; add `--volumes` only when deliberately resetting local data.

## Services on the host

```bash
./mvnw -pl services/core-service spring-boot:run
./mvnw -pl services/gateway spring-boot:run

cd services/ingestion-service
python -m venv .venv
python -m pip install --require-hashes -r requirements-dev.lock
python -m pip install --no-deps -e .
uvicorn rxrelay_ingestion.main:app --reload --port 8090

cd apps/web
npm ci
npm run dev
```

The core API is available directly on port 8081 and through the gateway on port 8080. Runtime OpenAPI is exposed at `http://localhost:8081/v3/api-docs` and Swagger UI at `http://localhost:8081/swagger-ui.html`. The committed contract and endpoint semantics are described in [api.md](api.md).

On Windows use `mvnw.cmd`; on Unix-like hosts use `./mvnw`. The full container route does not require host Java/Python:

```bash
docker compose up --build --wait
```

## Load public data

No source call happens automatically. With the ingestion service and broker ready:

```bash
curl -X POST "http://localhost:8090/api/v1/ingestions?limit=25"
curl "http://localhost:8090/api/v1/ingestions/status"
```

The configured maximum is 2,000 by default, which covers the current feed while enforcing a hard bound. Start with a small limit during development. Set `OPENFDA_API_KEY` for sustained usage and do not commit it.

The canonical seed command performs a real source run and publishes to Kafka:

```bash
cd services/ingestion-service
python -m rxrelay_ingestion.cli --limit 100
```

For an explicit idempotency check, `--runs 2` repeats the same bounded slice in one process (the accepted range is 1–3). Each run creates observation evidence, while unchanged records create no extra status changes or downstream events.

## Checks

```bash
./mvnw spotless:check test spotbugs:check
cd services/ingestion-service && ruff check . && ruff format --check . && mypy src && pytest
cd apps/web && npm run contract:check && npm run lint && npm run typecheck && npm test && npm run build && npm run test:e2e
docker compose config --quiet
```

`scripts/verify.sh` and `scripts/verify.ps1` run the non-browser quality suite. The checked-in OpenAPI client is regenerated and byte-compared during `contract:check`; use `contract:generate` only when intentionally accepting a reviewed API contract change.

The opt-in live integration test starts lightweight embedded PostgreSQL and KRaft, invokes the real Python/openFDA/RxNorm path twice, exercises representative REST resources over HTTP, then queries persistence counts, a sample row, and an actual PostgreSQL query plan:

```bash
mvn -pl services/core-service -Drxrelay.live=true -Dtest=LivePipelineTest test
```

It requires the ingestion service virtual environment at `services/ingestion-service/.venv` and outbound source access. Ordinary tests remain hermetic and use captured or synthetic fixtures.

The hermetic reliability pipeline test uses embedded PostgreSQL plus a single embedded KRaft broker and covers duplicate delivery, retry/recovery, DLQ publication, notification/outbox publication, and resulting row counts:

```bash
mvn -pl services/core-service -Drxrelay.reliability=true -Dtest=KafkaReliabilityPipelineTest test
```

Developer-safe fault scripts and the profile-gated endpoints they call are documented in [reliability.md](reliability.md).

With all app containers running, execute the deterministic cross-service smoke test with `python scripts/smoke/compose-smoke.py`. It verifies health, bounded reads, and a watchlist create/delete round trip without contacting FDA. CI builds and runs this path after language-level tests pass.

Synthetic input belongs only under test directories and must be named as a fixture. Do not add production bootstrap records.

## Manual frontend verification

With the normal local services running, open `http://localhost:5173` and exercise search, detail/source evidence, watchlist mutations, notifications, and event inspection. The browser critical-flow suite uses controlled API fixtures and starts Vite automatically:

```bash
cd apps/web
npx playwright install chromium
npm run test:e2e
```

When Docker is unavailable, an opt-in Windows-friendly harness starts embedded PostgreSQL and one KRaft broker, ingests 10 genuine openFDA records, and serves core directly on port 8080 for up to ten minutes:

```powershell
$env:JAVA_HOME='path-to-java-21'
.\mvnw.cmd -pl services/core-service '-Drxrelay.frontend.live=true' '-Dtest=FrontendLiveStackTest' test
```

Run Vite in another terminal. Create `services/core-service/target/frontend-live-stack.stop` after manual verification so the harness shuts down its processes cleanly. This is a development harness, not a production runtime.

## Health and metrics

| Component | Health | Metrics |
|---|---|---|
| gateway | `:8080/actuator/health` | `:8080/actuator/prometheus` |
| core | `:8081/actuator/health` | `:8081/actuator/prometheus` |
| ingestion | `:8090/health/ready` | `:8090/metrics` |

See [observability.md](observability.md) for health semantics and meter names, [testing.md](testing.md) for the pyramid and opt-in suites, [security.md](security.md) for deployment boundaries, and [performance.md](performance.md) for reproducible benchmark commands.

## Schema and contract changes

Add a new Flyway migration; never edit a migration already used outside your branch. Update `contracts/openapi` with API changes. Additive optional event fields may retain v1; renamed, removed, or semantically changed fields require a new schema and Kafka topic major version.

## Dependency locks

- Maven Wrapper pins Maven 3.9.9; the parent POM and Spring dependency management control Java versions.
- `apps/web/package-lock.json` is authoritative for npm installs; use `npm ci` in automation and containers.
- `requirements.lock` contains hash-locked ingestion runtime dependencies. `requirements-dev.lock` adds test, lint, schema, and type-check packages.

After intentionally changing `pyproject.toml`, install `pip-tools==7.5.0` in the ingestion virtual environment and regenerate both files:

```bash
python -m piptools compile --generate-hashes --strip-extras -o requirements.lock pyproject.toml
python -m piptools compile --extra dev --generate-hashes --strip-extras -o requirements-dev.lock pyproject.toml
```

Review dependency diffs like source changes. `make setup`, `make test`, `make lint`, `make test-integration`, `make ingest`, `make performance`, `make cache-benchmark`, `make backup`, `make down`, and `make clean` provide discoverable shortcuts; the underlying commands remain documented and directly usable.
