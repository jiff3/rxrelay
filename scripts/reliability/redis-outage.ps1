$ErrorActionPreference = 'Stop'
$stopped = $false
try {
    docker compose stop redis
    $stopped = $true
    $response = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/drugs?size=1'
    Write-Output "Authoritative API remained available with $($response.totalElements) drug rows visible."
} finally {
    if ($stopped) { docker compose start redis }
}
