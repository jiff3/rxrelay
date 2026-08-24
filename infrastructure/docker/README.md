# Docker infrastructure

The root Compose file is the executable local topology. Application Dockerfiles live beside their build contexts so changes remain discoverable. This directory holds operational extensions rather than duplicating those definitions.

The `observability` profile adds Prometheus with a small local retention window:

```bash
docker compose -f docker-compose.yml -f infrastructure/docker/compose.observability.yml --profile observability up --build --wait
```
