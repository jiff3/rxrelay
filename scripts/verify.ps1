$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-Native {
    param([Parameter(Mandatory)][scriptblock]$Command)

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE: $Command"
    }
}

Invoke-Native { docker compose config --quiet }
Invoke-Native { npm --prefix apps/web run lint }
Invoke-Native { npm --prefix apps/web run contract:check }
Invoke-Native { npm --prefix apps/web run typecheck }
Invoke-Native { npm --prefix apps/web test }
Invoke-Native { npm --prefix apps/web run build }

$venvPython = Join-Path $PSScriptRoot '..\services\ingestion-service\.venv\Scripts\python.exe'
if (Test-Path $venvPython) {
    Push-Location services/ingestion-service
    try {
        Invoke-Native { & $venvPython -m ruff check . }
        Invoke-Native { & $venvPython -m ruff format --check . }
        Invoke-Native { & $venvPython -m mypy src }
        Invoke-Native { & $venvPython -m pytest }
    } finally {
        Pop-Location
    }
} else {
    throw 'Python development environment is missing. Run the documented setup command first.'
}

if (-not $env:JAVA_HOME) {
    $localJdk = Get-ChildItem (Join-Path $PSScriptRoot '..\.tools\jdk21') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($localJdk) {
        $env:JAVA_HOME = $localJdk.FullName
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'JAVA_HOME must point to a Java 21 JDK. Docker Compose does not require a host JDK.'
}

Invoke-Native { .\mvnw.cmd spotless:check test spotbugs:check }
