# Testing strategy

RxRelay uses a test pyramid: many deterministic unit and component tests, fewer tests with real infrastructure, and one narrow browser and Compose path. Live public APIs are opt-in evidence tests, never the only coverage for parsing or business rules.

## Pyramid

| Layer | Scope | Default command |
|---|---|---|
| Java unit/web slice | Status rules, validation, DTO/API behavior, caching, outbox, and failure branches | `./mvnw test` |
| Java database integration | Flyway V1-V5, PostgreSQL constraints, repositories, idempotency, and transaction effects | Included in `./mvnw test` using sequential embedded PostgreSQL |
| Python unit/component | Captured openFDA parsing, normalization, retries, malformed records, RxNorm outcomes, Kafka publishing, and JSON Schema contracts | `cd services/ingestion-service && pytest` |
| React component | Queries, filtering, pagination, details, watchlists, notifications, and API failures | `cd apps/web && npm test` |
| Browser critical flow | Search -> detail -> watchlist -> event notification at a controlled API boundary | `cd apps/web && npm run test:e2e` |
| Kafka reliability | Real single-node embedded KRaft plus PostgreSQL; duplicates, retry, DLQ, and notifications | Opt-in command below |
| Compose smoke | Built containers, health dependencies, gateway reads, and watchlist create/delete | `./scripts/test-integration.ps1` or `./scripts/test-integration.sh` |

The openFDA and RxNorm examples under `services/ingestion-service/tests/fixtures/` are explicitly labeled fixtures captured from the genuine source shape. Tests mock transport and do not depend on uncontrolled FDA availability. IDs, transitions, and malformed payloads used by reliability tests are synthetic and labeled under `scripts/reliability/fixtures/`.

## Contract checks

- Python normalizes a captured openFDA record and validates the resulting event against `contracts/events/shortage-observed.v1.schema.json`.
- Every event schema is self-checked as JSON Schema Draft 2020-12.
- The React client imports types generated from `contracts/openapi/rxrelay-api.v1.yaml`.
- `npm run contract:check` regenerates into an OS temporary directory and fails on any diff from `src/generated/rxrelay-api.ts`.
- CI runs the event-schema and OpenAPI checks before accepting a build.

After deliberately changing an API schema, run `npm run contract:generate` in `apps/web`, review the contract and generated type diff, then run the typecheck and build.

## Commands

The repository-wide checks are `./scripts/verify.sh` or `./scripts/verify.ps1`. The Windows script discovers Java 21 from `JAVA_HOME` or the ignored project-local `.tools/jdk21` directory and uses the ingestion service virtual environment.

The heavier Kafka path is opt-in so an ordinary test run remains suitable for an 8 GB workstation:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\mvnw.cmd -pl services/core-service '-Drxrelay.reliability=true' '-Dtest=KafkaReliabilityPipelineTest' test
```

The genuine-source Java evidence path remains opt-in because public API availability is outside the test's control:

```powershell
.\mvnw.cmd -pl services/core-service '-Drxrelay.live=true' '-Dtest=LivePipelineTest' test
```

The release acceptance run used the canonical FastAPI ingestion endpoint against the live openFDA source instead; its recorded run IDs and counts are in [data-sources.md](data-sources.md).

## Observed release-candidate results

These results were produced locally on 2026-08-24; they are observations, not production claims:

- Java formatting, tests, and static analysis: 40 tests discovered, 37 default-enabled tests passed, three opt-in tests skipped, zero failures/errors, and zero SpotBugs findings. The full reactor completed in 6 minutes 43 seconds.
- Java Kafka reliability integration: 1/1 passed in 134.6 seconds using embedded single-node KRaft and embedded PostgreSQL.
- Python: 18/18 passed in 8.14 seconds after the final schema-identifier audit; Ruff lint/format and strict mypy passed. One upstream Starlette `TestClient` deprecation warning remains and does not originate in RxRelay code.
- React: OpenAPI-generated types matched the checked-in client, ESLint and TypeScript passed, 7/7 Vitest tests passed, and the Vite production build completed.
- Playwright: 1/1 deterministic critical-flow test passed in 18.6 seconds with one worker.
- Docker Compose: all seven services became healthy through `docker compose up --detach --build --wait`; the final smoke checked drugs, events, ingestion status, overview, watchlist list/create/delete, and received the expected 2xx responses.
- Kubernetes: `kubectl kustomize` rendered 10 resources and `kubeconform` 0.7.0 reported 10 valid, zero invalid/error/skipped resources in strict mode.
- GitHub Actions syntax: `actionlint` 1.7.7 completed with no findings.

The Kafka integration test is deliberately separate from the default suite because the embedded broker and PostgreSQL startup dominate runtime and memory. The Playwright test controls the API boundary; the separate Compose, real-ingestion, and browser walkthroughs cover integration with the running backend.
