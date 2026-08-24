"""Deterministic API smoke test for an already-running Docker Compose application stack."""

from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
import uuid


def request(
    url: str, method: str = "GET", body: object | None = None, timeout: float = 30
) -> tuple[int, object | None]:
    payload = None if body is None else json.dumps(body).encode()
    headers = {"Accept": "application/json", "X-Request-Id": f"smoke-{uuid.uuid4()}"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    try:
        with urllib.request.urlopen(
            urllib.request.Request(url, data=payload, method=method, headers=headers),
            timeout=timeout,
        ) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else None


def wait_for(url: str, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    last: object = "no response"
    while time.monotonic() < deadline:
        try:
            status, body = request(
                url, timeout=min(30, max(1, deadline - time.monotonic()))
            )
            last = {"status": status, "body": body}
            if status == 200:
                return
        except (OSError, TimeoutError, ValueError) as error:
            last = type(error).__name__
        time.sleep(1)
    raise RuntimeError(f"timed out waiting for {url}: {last}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gateway", default="http://127.0.0.1:8080")
    parser.add_argument("--ingestion", default="http://127.0.0.1:8090")
    parser.add_argument("--timeout", type=float, default=180)
    args = parser.parse_args()

    wait_for(f"{args.gateway}/actuator/health/liveness", args.timeout)
    wait_for(f"{args.ingestion}/health/live", args.timeout)

    checks: dict[str, int] = {}
    for name, url in {
        "overview": f"{args.gateway}/api/v1/overview",
        "drugs": f"{args.gateway}/api/v1/drugs?size=1",
        "watchlists": f"{args.gateway}/api/v1/watchlists?size=1",
        "events": f"{args.gateway}/api/v1/system/events?size=1",
        "ingestion_status": f"{args.ingestion}/api/v1/ingestions/status",
    }.items():
        status, _ = request(url)
        if status != 200:
            raise RuntimeError(f"{name} returned HTTP {status}")
        checks[name] = status

    watchlist_name = f"Compose smoke {uuid.uuid4().hex[:8]}"
    status, created = request(
        f"{args.gateway}/api/v1/watchlists", "POST", {"name": watchlist_name}
    )
    if status != 201 or not isinstance(created, dict) or not created.get("id"):
        raise RuntimeError(f"watchlist creation failed: HTTP {status} {created}")
    delete_status, _ = request(
        f"{args.gateway}/api/v1/watchlists/{created['id']}", "DELETE"
    )
    if delete_status != 204:
        raise RuntimeError(f"watchlist cleanup failed: HTTP {delete_status}")
    checks["watchlist_create"] = status
    checks["watchlist_delete"] = delete_status
    print(json.dumps({"result": "passed", "checks": checks}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
