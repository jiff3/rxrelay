param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$Database = 'rxrelay_restore_validation',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($Database -notmatch '^[A-Za-z][A-Za-z0-9_]{0,62}$') {
    throw 'Database must be a simple PostgreSQL identifier.'
}

$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
$databaseUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'rxrelay' }
$containerIdOutput = @(docker compose ps -q postgres)
$containerId = ($containerIdOutput -join '').Trim()
if ($LASTEXITCODE -ne 0 -or -not $containerId) { throw 'The Compose postgres service is not running.' }

$existsOutput = @(docker compose exec -T postgres psql --username $databaseUser --dbname postgres --tuples-only --no-align --command "SELECT 1 FROM pg_database WHERE datname = '$Database'")
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the restore target database.' }
$exists = ($existsOutput -join '').Trim()
if ($exists -eq '1' -and -not $Force) {
    throw "Database '$Database' already exists. Re-run with -Force only when replacing it is intentional."
}

$containerPath = "/tmp/rxrelay-restore-$([guid]::NewGuid().ToString('N')).dump"
try {
    docker cp $resolvedBackup "${containerId}:${containerPath}"
    if ($LASTEXITCODE -ne 0) { throw 'docker cp failed.' }
    if ($exists -eq '1') {
        docker compose exec -T postgres dropdb --username $databaseUser $Database
        if ($LASTEXITCODE -ne 0) { throw "Could not drop '$Database'." }
    }
    docker compose exec -T postgres createdb --username $databaseUser $Database
    if ($LASTEXITCODE -ne 0) { throw "Could not create '$Database'." }
    docker compose exec -T postgres pg_restore --username $databaseUser --dbname $Database --no-owner --exit-on-error $containerPath
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed.' }
} finally {
    docker compose exec -T postgres rm -f $containerPath | Out-Null
}

Write-Output "Restored '$resolvedBackup' into '$Database'."
