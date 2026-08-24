"""Small dependency-free HTTP benchmark suitable for an 8 GB development machine."""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import math
import statistics
import time
import urllib.error
import urllib.request


def get_json(url: str) -> tuple[float, int, object | None]:
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(url, timeout=10) as response:
            value = json.loads(response.read())
            return (time.perf_counter() - started) * 1000, response.status, value
    except urllib.error.HTTPError as error:
        return (time.perf_counter() - started) * 1000, error.code, None
    except OSError:
        return (time.perf_counter() - started) * 1000, 0, None


def percentile(values: list[float], percentile_value: float) -> float:
    index = max(0, math.ceil(percentile_value * len(values)) - 1)
    return sorted(values)[index]


def benchmark(
    name: str, url: str, requests: int, concurrency: int, warmup: int
) -> dict[str, object]:
    for _ in range(warmup):
        get_json(url)
    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        results = list(executor.map(lambda _: get_json(url), range(requests)))
    elapsed = time.perf_counter() - started
    latencies = [value[0] for value in results]
    errors = sum(1 for _, status, _ in results if status < 200 or status >= 400)
    return {
        "endpoint": name,
        "requests": requests,
        "concurrency": concurrency,
        "p50Ms": round(percentile(latencies, 0.50), 3),
        "p95Ms": round(percentile(latencies, 0.95), 3),
        "p99Ms": round(percentile(latencies, 0.99), 3),
        "meanMs": round(statistics.fmean(latencies), 3),
        "throughputRequestsPerSecond": round(requests / elapsed, 3),
        "errorRate": round(errors / requests, 6),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=4)
    parser.add_argument("--warmup", type=int, default=5)
    args = parser.parse_args()
    if (
        args.requests < 1
        or not 1 <= args.concurrency <= 32
        or not 0 <= args.warmup <= 100
    ):
        raise SystemExit(
            "requests must be positive, concurrency 1-32, and warmup 0-100"
        )

    _, status, page = get_json(f"{args.base_url}/api/v1/drugs?size=1")
    if status != 200 or not isinstance(page, dict):
        raise SystemExit(f"drug discovery failed with HTTP {status}")
    items = page.get("items")
    if not isinstance(items, list) or not items:
        raise SystemExit("benchmark requires at least one persisted medication")
    drug_id = items[0]["id"]
    endpoints = {
        "medication-search": f"{args.base_url}/api/v1/drugs?query=a&size=20&sort=name,asc",
        "drug-detail": f"{args.base_url}/api/v1/drugs/{drug_id}",
        "timeline": f"{args.base_url}/api/v1/drugs/{drug_id}/timeline?size=20",
        "overview": f"{args.base_url}/api/v1/overview",
    }
    results = [
        benchmark(name, url, args.requests, args.concurrency, args.warmup)
        for name, url in endpoints.items()
    ]
    output = {
        "tool": "scripts/performance/api_benchmark.py",
        "baseUrl": args.base_url,
        "results": results,
    }
    print(json.dumps(output, indent=2))
    return 1 if any(result["errorRate"] != 0 for result in results) else 0


if __name__ == "__main__":
    raise SystemExit(main())
