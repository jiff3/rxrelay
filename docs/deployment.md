# Deployment

## Local Compose deployment

RxRelay's supported local deployment is the root Compose project. It is suitable for an 8 GB development machine and requires no Kubernetes:

```bash
cp .env.example .env                 # PowerShell: Copy-Item .env.example .env
docker compose config --quiet
docker compose up --detach --build --wait --wait-timeout 360
python scripts/smoke/compose-smoke.py --timeout 60
```

The default starts the complete application. PostgreSQL and Kafka retain state in named volumes; Redis is explicitly disposable. The declared memory limits total 2,848 MiB: PostgreSQL 512, Redis 192, Kafka 768, core 640, gateway 384, ingestion 256, and web 96 MiB. This excludes Docker Engine/VM overhead and image-build peaks. Build one image with `docker compose build core-service`, or run only infrastructure for host development with `docker compose up -d --wait postgres redis kafka`.

Images use multi-stage builds. Java runtime containers and ingestion run as dedicated non-root users with percentage-based JVM memory limits. The web runtime is the unprivileged Nginx distribution. Dockerfile and Compose health checks exercise the same liveness/readiness routes used by operations. `.dockerignore` excludes secrets, local tools, dependencies, build output, and runtime data from build contexts.

The final local v1.0.0 rebuild on 2026-08-24 produced these Docker-reported image sizes: core service 459 MB, gateway 375 MB, ingestion service 223 MB, and web 74.1 MB. These are compressed/content-store figures from Docker Desktop, not resident memory. The Java builders use a BuildKit Maven cache and an explicit production-only dependency profile, so embedded PostgreSQL/Kafka test artifacts are not required to compile runtime images.

## Configuration and migrations

Compose reads substitutions from `.env`; it is ignored by Git. `.env.example` contains local-only defaults, not production credentials. In a real environment inject configuration through the platform's secret/configuration facility. Never bake `.env` into an image.

Flyway runs from core-service during startup. Deploy database-compatible application versions sequentially, inspect migration logs, and use additive/backward-compatible migrations when a rolling deployment can overlap versions. Do not edit an applied migration.

## Kubernetes baseline

Render the base without a cluster:

```bash
kubectl kustomize infrastructure/kubernetes
```

The base includes the namespace, ConfigMap, four Deployments and Services, startup/readiness/liveness probes, bounded resources, rolling updates, disabled service-account token mounts, restricted container security contexts, and bounded writable `/tmp` volumes. `secret.example.yaml` is a template and is deliberately excluded from Kustomize.

Before apply:

1. Provide a real `rxrelay-secrets` Secret through the platform secret manager.
2. Override every image with immutable registry tags or digests.
3. Override database, Redis, Kafka, and CORS endpoints.
4. Provide external/managed PostgreSQL, Redis, and Kafka connectivity.
5. Add platform-specific ingress/TLS, network policy, authentication, and backup policy.

Then apply with `kubectl apply -k infrastructure/kubernetes` and watch `kubectl -n rxrelay rollout status deployment/core-service`. The base does not deploy stateful dependencies: managed services or mature operators are preferable for backup, replication, upgrades, and failover. A local kind/k3d test is optional, must use development dependencies, and should run only after the Compose stack is stopped on an 8 GB host.

## CI and releases

`CI` runs Java formatting/tests/static analysis, hash-locked Python lint/type/tests, frontend contract/lint/type/test/build/browser checks, Compose and Kubernetes rendering, four independent image builds, and Trivy dependency/secret/misconfiguration scanning. `Integration` builds the complete Compose stack, waits for health, and exercises cross-service reads plus a watchlist create/delete round trip.

Tags matching `v*` trigger `Release`. Java JARs, the Python wheel, and web assets are always attached as a workflow artifact. All four images are built. Publishing to GHCR occurs only when repository variable `RXRELAY_PUBLISH_IMAGES` equals `true`; it uses the scoped workflow `GITHUB_TOKEN` and requires no invented credential. Version tags follow SemVer; this source state is prepared as `v1.0.0`.
