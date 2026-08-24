$ErrorActionPreference = 'Stop'
$eventId = '11111111-1111-4111-8111-111111111111'
$fixture = Get-Content (Join-Path $PSScriptRoot 'fixtures\synthetic-shortage-observed.v1.json') -Raw | ConvertFrom-Json
$body = $fixture.event | ConvertTo-Json -Depth 20 -Compress
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/v1/reliability/events/$eventId/failures?times=2"
Invoke-RestMethod -Method Post -ContentType 'application/json' -Body $body -Uri 'http://localhost:8081/api/v1/reliability/events'
Write-Output 'Injected two transient consumer failures before recovery.'
