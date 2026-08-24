"""Paired cold/warm HTTP timings; refuses to run unless the core reports Redis UP."""

from __future__ import annotations

import argparse
import json
import math
import socket
import statistics
import time
import urllib.parse
import urllib.request


def timed_json(url: str) -> tuple[float, object]:
    started = time.perf_counter()
    with urllib.request.urlopen(url, timeout=10) as response:
        value = json.loads(response.read())
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status} from {url}")
    return (time.perf_counter() - started) * 1000, value


def percentile(values: list[float], p: float) -> float:
    return sorted(values)[max(0, math.ceil(len(values) * p) - 1)]


def summary(values: list[float]) -> dict[str, float]:
    return {
        "minMs": round(min(values), 3),
        "p50Ms": round(percentile(values, 0.50), 3),
        "p95Ms": round(percentile(values, 0.95), 3),
        "p99Ms": round(percentile(values, 0.99), 3),
        "meanMs": round(statistics.fmean(values), 3),
        "maxMs": round(max(values), 3),
    }


def require_redis(host: str, port: int) -> None:
    with socket.create_connection((host, port), timeout=2) as connection:
        connection.sendall(b"*1\r\n$4\r\nPING\r\n")
        if connection.recv(64) != b"+PONG\r\n":
            raise SystemExit("refusing benchmark: Redis did not answer PING")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8081")
    parser.add_argument("--repetitions", type=int, default=20)
    parser.add_argument("--redis-host", default="127.0.0.1")
    parser.add_argument("--redis-port", type=int, default=6379)
    args = parser.parse_args()
    if not 5 <= args.repetitions <= 100:
        raise SystemExit("repetitions must be 5-100")

    require_redis(args.redis_host, args.redis_port)

    cold: list[float] = []
    warm: list[float] = []
    # Whitespace-only manufacturer values normalize to the same PostgreSQL query but produce a
    # fresh bounded cache key for each pair. The immediate second request is the paired warm read.
    for index in range(1, args.repetitions + 1):
        query = urllib.parse.urlencode({"manufacturer": " " * index, "size": 20})
        url = f"{args.base_url}/api/v1/drugs?{query}"
        cold_ms, _ = timed_json(url)
        warm_ms, _ = timed_json(url)
        cold.append(cold_ms)
        warm.append(warm_ms)
    print(
        json.dumps(
            {
                "repetitions": args.repetitions,
                "cold": summary(cold),
                "warm": summary(warm),
                "warmFasterPairs": sum(
                    1
                    for cold_value, warm_value in zip(cold, warm, strict=True)
                    if warm_value < cold_value
                ),
                "method": "paired end-to-end requests with fresh cache keys",
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
