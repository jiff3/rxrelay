# Disaster recovery

PostgreSQL is RxRelay's authoritative persistence layer, so a recoverable database backup is justified. Kafka retention, Redis keys, and container filesystems are not backups. Public FDA data can be re-ingested, but local observation history, watchlists, audit events, processed-event markers, notifications, and outbox state cannot be reconstructed fully from the current upstream feed.

## Development backup

With Compose PostgreSQL healthy:

```powershell
pwsh -NoProfile -File scripts/database/backup.ps1
```

The script uses PostgreSQL custom format, `--no-owner`, the configured `POSTGRES_USER`/`POSTGRES_DB` (falling back to local defaults), copies the dump from the container, and places it in ignored `backups/`. Copy important backups to storage outside the workstation; the local folder is convenience, not durable retention.

## Restore and verification

Restore into a new database first:

```powershell
pwsh -NoProfile -File scripts/database/restore.ps1 `
  -BackupFile backups/rxrelay-YYYYMMDD-HHMMSS.dump `
  -Database rxrelay_restore_validation
```

The target must be a simple validated identifier. An existing target is never replaced without `-Force`. Automated development validation performs a fresh dump, restores it into the isolated database, compares `ingestion_runs`, `medications`, and `shortage_records` counts, and drops only the validation database:

```powershell
pwsh -NoProfile -File scripts/database/validate-backup.ps1
```

Counts are a smoke check, not a full integrity proof. For production, use managed PostgreSQL point-in-time recovery/snapshots, encrypted off-site retention, alerting, periodic restore drills in an isolated account/project, access controls, and documented recovery point/time objectives chosen by the operator. After a real restore, verify Flyway history, foreign-key consistency, representative API reads, processed-event uniqueness, and outbox state before reopening writes.
