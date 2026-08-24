-- Run with: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/performance/explain.sql
-- Uses whatever bounded development data is currently present; capture dataset counts alongside output.
EXPLAIN (ANALYZE, BUFFERS)
SELECT DISTINCT m.id, m.canonical_name
FROM medications m
LEFT JOIN shortage_records s ON s.medication_id = m.id
WHERE lower(m.canonical_name) LIKE '%a%' AND s.status = 'CURRENT'
ORDER BY m.canonical_name, m.id
LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS)
SELECT sc.*
FROM status_changes sc
JOIN shortage_records sr ON sr.id = sc.shortage_record_id
WHERE sr.medication_id = (SELECT id FROM medications ORDER BY id LIMIT 1)
ORDER BY sc.occurred_at DESC, sc.id
LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM watchlist_items
WHERE watchlist_id = (SELECT id FROM watchlists ORDER BY id LIMIT 1)
ORDER BY created_at DESC, id
LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM notifications
WHERE owner_id = 'local-demo-user' AND is_read = FALSE
ORDER BY created_at DESC, id
LIMIT 20;
