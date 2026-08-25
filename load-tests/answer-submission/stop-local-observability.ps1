[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "stop-local-observability.ps1 requires PowerShell 7 or later."
}

$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../..")
)
$composeFile = Join-Path $repositoryRoot `
    "docker-compose.observability.yml"

Push-Location $repositoryRoot
try {
    & docker compose -f $composeFile down --remove-orphans
    if ($LASTEXITCODE -ne 0) {
        throw "Local observability Compose shutdown failed."
    }
} finally {
    Pop-Location
}

Write-Host "Local load-test observability stack stopped."
Write-Host "Prometheus data volume was retained."
