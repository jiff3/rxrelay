$ErrorActionPreference = 'Stop'
Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/v1/reliability/malformed'
Write-Output 'Published one malformed value. Inspect GET /api/v1/system/events?state=DEAD_LETTERED.'
