#!/usr/bin/env sh
set -eu
docker compose config --quiet
npm --prefix apps/web run lint
npm --prefix apps/web run contract:check
npm --prefix apps/web test
npm --prefix apps/web run build
if [ -x services/ingestion-service/.venv/bin/python ]; then
  (cd services/ingestion-service && .venv/bin/python -m ruff check . && .venv/bin/python -m ruff format --check . && .venv/bin/python -m mypy src && .venv/bin/python -m pytest)
elif command -v python >/dev/null 2>&1; then
  (cd services/ingestion-service && python -m ruff check . && python -m ruff format --check . && python -m mypy src && python -m pytest)
fi
if [ -x ./mvnw ]; then
  ./mvnw spotless:check test spotbugs:check
elif command -v mvn >/dev/null 2>&1; then
  mvn spotless:check test spotbugs:check
fi
