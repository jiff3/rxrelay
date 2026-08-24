# Security baseline

RxRelay processes public medication-supply information. It has no need for, and does not ingest or store, patient-level records or protected health information (PHI). Watchlists belong to one explicitly configured demo identity; they are not patient, pharmacy, or production user accounts.

## Implemented controls

- Configuration is environment based. `.env.example` contains local development defaults only; `.env`, private keys, IDE state, databases, and build outputs are ignored.
- A Trivy 0.66.0 repository secret/misconfiguration scan completed on 2026-08-24 with zero remaining high/critical findings. Its first pass identified a missing explicit `USER` declaration in the otherwise-unprivileged web image; the Dockerfile was corrected and the scan was rerun successfully.
- API DTOs use Bean Validation/Pydantic constraints. Paging and ingestion limits prevent unbounded reads and fetches. Gateway, Tomcat, multipart, and Nginx request bodies are limited to 1 MB by default; HTTP request headers are limited to 16 KB.
- Persistence uses Spring Data/JPA parameters rather than concatenating request input into SQL. Sort fields are mapped through an allow-list.
- Exceptions become bounded, consistent error responses without stack traces. The production Spring profile disables Swagger/OpenAPI endpoints and error details; FastAPI disables documentation endpoints in production.
- CORS defaults to `http://localhost:5173`, permits only required methods/headers, exposes only the request ID, and does not allow credentials. Production deployments must set an explicit HTTPS origin.
- The gateway adds CSP, clickjacking, MIME-sniffing, referrer, and permissions-policy headers. The ingestion service adds the applicable API security headers.
- Correlation IDs are allow-list validated before reflection into response headers and logs.
- Redis-backed rate limiting is bounded per caller key. Its fail-open behavior is an explicit availability tradeoff: Redis failure cannot make PostgreSQL reads unavailable, but upstream/load-balancer enforcement is required for an Internet deployment.
- Reliability Lab controllers are profile-gated and absent unless `reliability-lab` is intentionally activated. Manual ingestion can be disabled and is disabled in the Kubernetes production baseline.
- Containers run as non-root where the selected runtime supports it. Secrets are not baked into images.

## Dependency and static analysis

CI uses locked npm dependencies, Maven/Python lock-compatible manifests, SpotBugs at maximum effort/medium threshold, language linters/typecheckers, and Trivy filesystem scanning. Dependabot watches Maven, pip, npm, and GitHub Actions weekly. `npm audit --audit-level=high --omit=optional` reported zero vulnerabilities locally on 2026-08-24.

The local Trivy vulnerability-database scan was attempted twice. The first 109 MB database transfer reached 77% and hit Trivy’s default timeout; a 15-minute cached retry degraded to an impractical network rate and was stopped. Therefore no local Trivy vulnerability result is claimed. CI remains configured to run the full vulnerability, secret, and misconfiguration scan on GitHub-hosted networking; the completed npm audit, language tests, and Trivy configuration/secret result are separate evidence, not substitutes for that CI gate.

The SpotBugs exclusion file is narrow and documented. It suppresses framework-owned dependency references, Hibernate-reflected fields, a validated response-header flow, and an intentional fail-fast configuration constructor. Mutable lists crossing DTO boundaries were fixed with defensive copies instead of suppressed.

## Deployment limitations

There is no real authentication or authorization. Do not expose the demo deployment to untrusted users or represent it as a multi-user medical system. Before Internet use, add identity, authorization tests, TLS termination, managed secrets, network policy, persistent backup/restore, and an external rate limit/WAF appropriate to the deployment. The local Kafka/PostgreSQL topology is not highly available.

RxRelay presents public informational data and provenance; it must not provide patient-specific decisions, diagnosis, substitution advice, or guarantees of inventory availability.
