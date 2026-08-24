.PHONY: setup dev infra-up infra-down down lint java-test python-test web-test web-e2e contract quality smoke test-integration performance performance-python cache-benchmark test build ingest ingest-cli backup clean

PYTHON ?= python3
INGESTION_PYTHON ?= services/ingestion-service/.venv/bin/python

setup:
	./mvnw --batch-mode -q -DskipTests dependency:go-offline
	$(PYTHON) -m venv services/ingestion-service/.venv
	$(INGESTION_PYTHON) -m pip install --require-hashes -r services/ingestion-service/requirements-dev.lock
	$(INGESTION_PYTHON) -m pip install --no-deps -e services/ingestion-service
	npm --prefix apps/web ci

dev:
	docker compose up --build

infra-up:
	docker compose up -d --wait postgres redis kafka

infra-down:
	docker compose stop postgres redis kafka

down:
	docker compose down --remove-orphans

java-test:
	./mvnw --batch-mode spotless:check test spotbugs:check

python-test:
	cd services/ingestion-service && .venv/bin/python -m ruff check . && .venv/bin/python -m ruff format --check . && .venv/bin/python -m mypy src && .venv/bin/python -m pytest

web-test:
	npm --prefix apps/web run contract:check
	npm --prefix apps/web run lint
	npm --prefix apps/web run typecheck
	npm --prefix apps/web test
	npm --prefix apps/web run build

web-e2e:
	npm --prefix apps/web run test:e2e

contract:
	npm --prefix apps/web run contract:check
	cd services/ingestion-service && .venv/bin/python -m pytest tests/test_event_contract.py

lint:
	./mvnw --batch-mode spotless:check spotbugs:check -DskipTests
	cd services/ingestion-service && .venv/bin/python -m ruff check . && .venv/bin/python -m ruff format --check . && .venv/bin/python -m mypy src
	npm --prefix apps/web run lint
	npm --prefix apps/web run typecheck

quality:
	./scripts/verify.sh

smoke:
	$(PYTHON) scripts/smoke/compose-smoke.py

test-integration:
	./scripts/test-integration.sh

performance:
	docker run --rm -e BASE_URL=http://host.docker.internal:8081 -e VUS=4 -e DURATION=15s -v "$(CURDIR)/scripts/performance:/scripts:ro" grafana/k6:0.57.0 run /scripts/api-smoke.k6.js

performance-python:
	$(PYTHON) scripts/performance/api_benchmark.py

cache-benchmark:
	$(PYTHON) scripts/performance/redis_cache_benchmark.py

test: java-test python-test web-test

build:
	./mvnw --batch-mode package
	npm --prefix apps/web run build

ingest:
	curl --fail-with-body -X POST "http://localhost:8090/api/v1/ingestions?limit=100"

ingest-cli:
	cd services/ingestion-service && .venv/bin/python -m rxrelay_ingestion.cli --limit 100

backup:
	pwsh -NoProfile -File scripts/database/backup.ps1

clean:
	docker compose down --remove-orphans
	./mvnw --batch-mode clean
