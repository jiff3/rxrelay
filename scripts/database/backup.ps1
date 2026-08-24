param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\..\backups')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$fileName = "rxrelay-$timestamp.dump"
$destination = Join-Path $resolvedOutput $fileName
$containerPath = "/tmp/$fileName"
$databaseUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'rxrelay' }
$databaseName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'rxrelay' }
$containerIdOutput = @(docker compose ps -q postgres)
$containerId = ($containerIdOutput -join '').Trim()

if ($LASTEXITCODE -ne 0 -or -not $containerId) { throw 'The Compose postgres service is not running.' }

try {
    docker compose exec -T postgres pg_dump --username $databaseUser --dbname $databaseName --format=custom --no-owner --file=$containerPath
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed.' }
    docker cp "${containerId}:${containerPath}" $destination | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'docker cp failed.' }
} finally {
    docker compose exec -T postgres rm -f $containerPath | Out-Null
}

Write-Output $destination
