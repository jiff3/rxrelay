#!/usr/bin/env sh
set -eu

cleanup() {
  docker compose down --remove-orphans
}
trap cleanup EXIT INT TERM

docker compose up --detach --build --wait --wait-timeout 300
python3 scripts/smoke/compose-smoke.py --timeout 60
