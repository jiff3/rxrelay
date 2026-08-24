$ErrorActionPreference = 'Stop'
$fixture = Get-Content (Join-Path $PSScriptRoot 'fixtures\synthetic-shortage-observed.v1.json') -Raw | ConvertFrom-Json
$body = $fixture.event | ConvertTo-Json -Depth 20 -Compress
Invoke-RestMethod -Method Post -ContentType 'application/json' -Body $body -Uri 'http://localhost:8081/api/v1/reliability/events?copies=3'
Write-Output 'Published the same synthetic event three times. Inspect /api/v1/system/events/11111111-1111-4111-8111-111111111111.'
