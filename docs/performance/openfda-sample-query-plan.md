# Representative medication search plan

Captured on 2026-08-22 by `LivePipelineTest` against PostgreSQL 14.22 after a genuine,
bounded openFDA ingestion run. The test ingested three shortage records twice to verify
idempotency before running the query.

```text
Limit  (cost=16.84..16.85 rows=1 width=532) (actual time=0.322..0.325 rows=2 loops=1)
  Buffers: shared hit=16
  ->  Unique  (cost=16.84..16.85 rows=1 width=532) (actual time=0.317..0.320 rows=2 loops=1)
        Buffers: shared hit=16
        ->  Sort  (cost=16.84..16.85 rows=1 width=532) (actual time=0.315..0.318 rows=2 loops=1)
              Sort Key: m.canonical_name, m.id
              Sort Method: quicksort  Memory: 25kB
              Buffers: shared hit=16
              ->  Nested Loop  (cost=0.28..16.83 rows=1 width=532) (actual time=0.136..0.154 rows=2 loops=1)
                    Buffers: shared hit=7
                    ->  Index Scan using idx_shortage_status_medication on shortage_records s  (cost=0.14..8.15 rows=1 width=32) (actual time=0.025..0.037 rows=2 loops=1)
                          Index Cond: ((status)::text = 'CURRENT'::text)
                          Buffers: shared hit=3
                    ->  Index Scan using medications_pkey on medications m  (cost=0.14..8.16 rows=1 width=532) (actual time=0.054..0.054 rows=1 loops=2)
                          Index Cond: (id = s.medication_id)
                          Filter: (lower((canonical_name)::text) ~~ '%a%'::text)
                          Buffers: shared hit=4
Planning:
  Buffers: shared hit=501
Planning Time: 96.468 ms
Execution Time: 0.694 ms
```

The query filtered medication names and current shortage status, joined products and
manufacturers, sorted deterministically, and limited the result to 20 rows. This is an
execution artifact for query-shape review, not a benchmark: the sample is deliberately
small and the timings must not be extrapolated to production workloads. Flyway migration
V4 adds trigram GIN indexes for name/manufacturer substring search; the status, timeline,
watchlist membership, and unread-notification indexes are defined in V3.
