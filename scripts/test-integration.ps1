param([int]$WaitTimeoutSeconds = 300)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

try {
    docker compose up --detach --build --wait --wait-timeout $WaitTimeoutSeconds
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose startup failed.' }
    python scripts/smoke/compose-smoke.py --timeout 60
    if ($LASTEXITCODE -ne 0) { throw 'Compose smoke test failed.' }
} finally {
    docker compose down --remove-orphans
}
