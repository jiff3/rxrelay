# Compose PostgreSQL query plans

Captured 2026-08-24 with `EXPLAIN (ANALYZE, BUFFERS)` against 59 medications, 101 shortage records, 103 status changes, one watchlist with two items, and two unread notifications. Timing differs from the earlier warm diagnostic pass because this capture followed a constrained full-stack restart. Both observations are local evidence, not capacity claims.

## Status-filtered medication search

```text
Limit  (cost=15.36..15.41 rows=20 width=50) (actual time=0.974..0.986 rows=20 loops=1)
  Buffers: shared hit=16
  ->  Sort  (cost=15.36..15.50 rows=54 width=50) (actual time=0.970..0.977 rows=20 loops=1)
        Sort Key: m.canonical_name, m.id
        Sort Method: quicksort  Memory: 28kB
        ->  HashAggregate  (cost=13.38..13.92 rows=54 width=50) (actual time=0.715..0.729 rows=35 loops=1)
              Group Key: m.canonical_name, m.id
              ->  Hash Join  (cost=3.54..13.01 rows=74 width=50) (actual time=0.244..0.622 rows=72 loops=1)
                    Hash Cond: (s.medication_id = m.id)
                    ->  Seq Scan on shortage_records s  (cost=0.00..9.25 rows=79 width=16) (actual time=0.024..0.285 rows=80 loops=1)
                          Filter: ((status)::text = 'CURRENT'::text)
                          Rows Removed by Filter: 21
                    ->  Hash  (cost=2.87..2.87 rows=54 width=50) (actual time=0.167..0.168 rows=52 loops=1)
                          ->  Seq Scan on medications m  (cost=0.00..2.87 rows=54 width=50) (actual time=0.032..0.127 rows=52 loops=1)
                                Filter: (lower((canonical_name)::text) ~~ '%a%'::text)
                                Rows Removed by Filter: 7
Planning Time: 17.862 ms
Execution Time: 1.347 ms
```

## Medication timeline

```text
Limit  (cost=13.99..13.99 rows=2 width=420) (actual time=0.939..0.946 rows=1 loops=1)
  Buffers: shared hit=16
  InitPlan 1 (returns $0)
    ->  Limit  (cost=0.14..0.43 rows=1 width=16) (actual time=0.169..0.171 rows=1 loops=1)
          ->  Index Only Scan using medications_pkey on medications (actual time=0.164..0.165 rows=1 loops=1)
                Heap Fetches: 1
  ->  Sort  (cost=13.56..13.56 rows=2 width=420) (actual time=0.934..0.937 rows=1 loops=1)
        Sort Key: sc.occurred_at DESC, sc.id
        Sort Method: quicksort  Memory: 25kB
        ->  Hash Join  (cost=9.28..13.55 rows=2 width=420) (actual time=0.669..0.716 rows=1 loops=1)
              Hash Cond: (sc.shortage_record_id = sr.id)
              ->  Seq Scan on status_changes sc (actual time=0.024..0.142 rows=103 loops=1)
              ->  Hash (actual time=0.508..0.509 rows=1 loops=1)
                    ->  Seq Scan on shortage_records sr (actual time=0.462..0.484 rows=1 loops=1)
                          Filter: (medication_id = $0)
                          Rows Removed by Filter: 100
Planning Time: 4.570 ms
Execution Time: 2.405 ms
```

## Watchlist membership

```text
Limit  (cost=13.18..13.19 rows=5 width=56) (actual time=0.894..0.899 rows=2 loops=1)
  Buffers: shared hit=4
  InitPlan 1 (returns $0)
    ->  Index Only Scan using watchlists_pkey on watchlists (actual time=0.704..0.705 rows=1 loops=1)
          Heap Fetches: 2
  ->  Sort  (cost=12.72..12.73 rows=5 width=56) (actual time=0.891..0.893 rows=2 loops=1)
        Sort Key: watchlist_items.created_at DESC, watchlist_items.id
        Sort Method: quicksort  Memory: 25kB
        ->  Bitmap Heap Scan on watchlist_items (actual time=0.864..0.867 rows=2 loops=1)
              Recheck Cond: (watchlist_id = $0)
              Heap Blocks: exact=1
              ->  Bitmap Index Scan on idx_watchlist_items_watchlist_time (actual time=0.851..0.851 rows=2 loops=1)
                    Index Cond: (watchlist_id = $0)
Planning Time: 4.452 ms
Execution Time: 1.113 ms
```

## Unread notifications

```text
Limit  (cost=0.14..8.15 rows=1 width=983) (actual time=0.149..0.154 rows=2 loops=1)
  Buffers: shared hit=2
  ->  Index Scan using idx_notifications_owner_unread on notifications (actual time=0.145..0.148 rows=2 loops=1)
        Index Cond: ((owner_id)::text = 'local-demo-user'::text)
Planning Time: 4.430 ms
Execution Time: 0.228 ms
```

Sequential scans are reasonable for these very small relations. The watchlist and unread-notification paths demonstrate the intended selective indexes without disabling the planner.
