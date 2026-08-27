[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SshHost,
    [Parameter(Mandatory = $true)]
    [string]$SshIdentityFile,
    [ValidateRange(1, 65535)]
    [int]$ApiPort = 18080,
    [ValidateRange(1, 65535)]
    [int]$ManagementPort = 19090,
    [ValidateRange(1, 65535)]
    [int]$PrometheusPort = 19091,
    [ValidateRange(1, 65535)]
    [int]$GrafanaPort = 13000
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "start-local-observability.ps1 requires PowerShell 7 or later."
}
if (@($ApiPort, $ManagementPort, $PrometheusPort, $GrafanaPort `
        | Select-Object -Unique).Count -ne 4) {
    throw "ApiPort, ManagementPort, PrometheusPort and GrafanaPort must be distinct."
}
if ($SshHost -notmatch '^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+$') {
    throw "SshHost must use the form user@host."
}
$resolvedSshIdentityFile = [System.IO.Path]::GetFullPath($SshIdentityFile)
if (-not (Test-Path -LiteralPath $resolvedSshIdentityFile -PathType Leaf)) {
    throw "SSH identity file not found: $resolvedSshIdentityFile"
}

$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../..")
)
$composeFile = Join-Path $repositoryRoot `
    "docker-compose.observability.yml"
$previousPrometheusPort = $env:LOADTEST_LOCAL_PROMETHEUS_PORT
$previousGrafanaPort = $env:LOADTEST_LOCAL_GRAFANA_PORT
$previousManagementPort = $env:LOADTEST_LOCAL_MANAGEMENT_PORT
$previousApiPort = $env:LOADTEST_LOCAL_API_PORT
$previousSshHost = $env:LOADTEST_SSH_HOST
$previousSshIdentityFile = $env:LOADTEST_SSH_IDENTITY_FILE

try {
    $env:LOADTEST_LOCAL_API_PORT = [string]$ApiPort
    $env:LOADTEST_LOCAL_MANAGEMENT_PORT = [string]$ManagementPort
    $env:LOADTEST_LOCAL_PROMETHEUS_PORT = [string]$PrometheusPort
    $env:LOADTEST_LOCAL_GRAFANA_PORT = [string]$GrafanaPort
    $env:LOADTEST_SSH_HOST = $SshHost
    $env:LOADTEST_SSH_IDENTITY_FILE = $resolvedSshIdentityFile
    Push-Location $repositoryRoot
    try {
        & docker compose -f $composeFile config --quiet
        if ($LASTEXITCODE -ne 0) {
            throw "Local observability Compose validation failed."
        }
        & docker compose -f $composeFile up -d --build
        if ($LASTEXITCODE -ne 0) {
            throw "Local observability Compose startup failed."
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:LOADTEST_LOCAL_API_PORT = $previousApiPort
    $env:LOADTEST_LOCAL_PROMETHEUS_PORT = $previousPrometheusPort
    $env:LOADTEST_LOCAL_GRAFANA_PORT = $previousGrafanaPort
    $env:LOADTEST_LOCAL_MANAGEMENT_PORT = $previousManagementPort
    $env:LOADTEST_SSH_HOST = $previousSshHost
    $env:LOADTEST_SSH_IDENTITY_FILE = $previousSshIdentityFile
}

Write-Host "Local load-test observability stack started."
Write-Host "Backend API: http://127.0.0.1:$ApiPort"
Write-Host "Backend management: http://127.0.0.1:$ManagementPort"
Write-Host "Prometheus: http://127.0.0.1:$PrometheusPort"
Write-Host "Grafana: http://127.0.0.1:$GrafanaPort"
