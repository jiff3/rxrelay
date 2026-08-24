param([string]$ValidationDatabase = 'rxrelay_restore_validation')

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$backupFile = @(& (Join-Path $PSScriptRoot 'backup.ps1')) | Select-Object -Last 1
& (Join-Path $PSScriptRoot 'restore.ps1') -BackupFile $backupFile -Database $ValidationDatabase -Force

$countSql = "SELECT 'ingestion_runs=' || count(*) FROM ingestion_runs UNION ALL SELECT 'medications=' || count(*) FROM medications UNION ALL SELECT 'shortage_records=' || count(*) FROM shortage_records ORDER BY 1;"
$databaseUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'rxrelay' }
$databaseName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'rxrelay' }
try {
    $sourceCounts = (docker compose exec -T postgres psql --username $databaseUser --dbname $databaseName --tuples-only --no-align --command $countSql) -join "`n"
    $restoredCounts = (docker compose exec -T postgres psql --username $databaseUser --dbname $ValidationDatabase --tuples-only --no-align --command $countSql) -join "`n"
    if ($sourceCounts -ne $restoredCounts) {
        throw "Backup validation count mismatch.`nSource:`n$sourceCounts`nRestored:`n$restoredCounts"
    }
    Write-Output "Backup validation passed.`n$sourceCounts"
} finally {
    docker compose exec -T postgres dropdb --username $databaseUser --if-exists $ValidationDatabase | Out-Null
}
