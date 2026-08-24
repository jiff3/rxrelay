# Performance evidence

> These measurements are bounded local engineering checks, not production capacity claims. Dataset size, Docker networking, JVM warm-up, background load, source corrections, and hardware materially affect every value. Do not extrapolate them.

## Environment

Measured 2026-08-24 on Windows 11 Home 10.0.26200 (build UBR 9168), an Intel Core i5-13420H with 12 logical processors, 7.64 GiB physical RAM, CPU only, and Docker Desktop 29.6.2 with 3.91 GB assigned to its Linux VM. The complete seven-service Compose topology was running: web, gateway, core service, ingestion service, PostgreSQL 16, Redis 7, and one Kafka 3 KRaft broker.

The measured database contained 59 medication concepts and 101 shortage records: 58/100 came from the canonical live openFDA run and 1/1 was the explicitly synthetic Reliability Lab fixture. The database also held 203 observations, 103 meaningful status changes, two notifications, and one watchlist at the time of final count capture. This is a small functional dataset.

## Canonical k6 read mix

Tool: pinned `grafana/k6:0.57.0` container (reported k6 v0.57.0, Go 1.23.6). Target: the core service published at port 8081. The benchmark bypasses gateway rate limiting so its finite token budget does not redefine a read-throughput measurement.

Exact command:

```powershell
docker run --rm `
  -e BASE_URL=http://host.docker.internal:8081 `
  -e VUS=4 -e DURATION=15s `
  -v "${PWD}/scripts/performance:/scripts:ro" `
  grafana/k6:0.57.0 run /scripts/api-smoke.k6.js
```

Each iteration requested medication search, one discovered drug detail, its timeline, and the demo user’s watchlists, then paused 50 ms. One setup discovery request preceded 198 complete iterations.

| Quantity | Actual observation |
|---|---:|
| Duration | 15.3 s wall time (15 s configured load) |
| Virtual users | 4 |
| HTTP requests | 793 |
| Complete iterations | 198 |
| Checks | 793 passed, 0 failed |
| Throughput | 51.7796 requests/s |
| HTTP duration p50 | 49.017 ms |
| HTTP duration p95 | 188.247 ms |
| HTTP duration p99 | 269.715 ms |
| Mean | 63.700 ms |
| Maximum | 410.660 ms |
| Observed HTTP error rate | 0 / 793 (0%) |

The raw k6 summary is checked in at [`performance/k6-summary.json`](performance/k6-summary.json). It contains the complete metric output rather than only the selected values above.

## Redis paired cold/warm comparison

Exact command:

```powershell
python scripts/performance/redis_cache_benchmark.py `
  --base-url http://localhost:8081 --redis-host localhost --redis-port 6379 `
  --repetitions 60
```

The script verifies Redis with a direct RESP `PING`. For each pair it uses a distinct whitespace manufacturer value to create a cold search-cache key, then repeats the same normalized query for the warm value. It reports all repetitions rather than selecting one request.

| Path | min ms | p50 ms | p95 ms | p99 ms | mean ms | max ms |
|---|---:|---:|---:|---:|---:|---:|
| Cold | 4.254 | 18.628 | 59.881 | 72.446 | 22.557 | 72.446 |
| Warm | 3.818 | 7.011 | 37.113 | 55.677 | 12.669 | 55.677 |

Warm was faster in 41 of 60 paired observations. The mean and percentiles improved, but 19 individual warm requests did not; scheduling, serialization, TCP, JVM, and desktop noise are material at this scale. The result demonstrates a real Redis-backed code path, not a guaranteed per-request speedup.

## PostgreSQL query analysis

The exact command was:

```powershell
Get-Content scripts/performance/explain.sql | docker compose exec -T postgres `
  psql -U rxrelay -d rxrelay -v ON_ERROR_STOP=1
```

An initial warm diagnostic against 59 medications/101 shortages observed 0.272 ms search, 0.132 ms timeline, 0.157 ms watchlist, and 0.032 ms unread-notification execution times. The preserved post-restart capture observed:

- medication search used sequential scans and a hash join, then sorted/uniqued the bounded result; execution was 1.347 ms (planning 17.862 ms)
- timeline used sequential scans on the tiny transition relations plus the medication primary key; execution was 2.405 ms (planning 4.570 ms)
- watchlist membership used `idx_watchlist_items_watchlist_time`; execution was 1.113 ms (planning 4.452 ms)
- unread notifications used `idx_notifications_owner_unread`; execution was 0.228 ms (planning 4.430 ms)

PostgreSQL correctly prefers sequential scans for several relations this small; forcing an index would not prove an optimization. Flyway provides trigram GIN indexes for substring identity/manufacturer searches plus status, timeline, membership, unread-notification, event, and outbox indexes. The complete current plans are preserved in [compose-query-plans.md](performance/compose-query-plans.md); the earlier three-record plan is retained separately and labeled as historical evidence.

## Resource and disk observations

With all seven containers healthy, observed container memory was approximately: web 12 MB, ingestion 61 MB, gateway 214 MB, core 410 MB, PostgreSQL 85 MB, Redis 6 MB, and Kafka 449 MB. Values are point-in-time observations, not limits. Compose declares about 2.85 GB in total service memory limits; Docker Desktop itself was assigned 3.91 GB.

Final application image sizes are recorded after the release rebuild in [deployment.md](deployment.md). The stack uses no Elasticsearch, Spark, ZooKeeper, ML runtime, model, or bulk dataset. Build caches can be safely pruned independently of named data volumes.

## Rerunning

Run the canonical k6 script with `make performance` only if the Make target and local k6 method match your platform; otherwise use the exact container command above. Always capture date, machine, topology, dataset counts, VUs, duration, all requests, errors, and raw summary. Never replace an unavailable measurement with an estimate.
