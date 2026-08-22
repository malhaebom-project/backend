param(
    [Parameter(Mandatory = $true)]
    [string]$ManagementUrl,
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,
    [int]$DurationSeconds = 300,
    [int]$IntervalSeconds = 1,
    [string]$StopFile = "",
    [string]$DockerContainer = "",
    [string]$SshHost = "",
    [string]$SshIdentityFile = ""
)

$ErrorActionPreference = "Stop"
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null
$prometheusPath = Join-Path $resolvedOutput "actuator-prometheus.txt"
$dockerPath = Join-Path $resolvedOutput "docker-stats.jsonl"
$dockerLogPath = Join-Path $resolvedOutput "backend-container.log"
$managementEndpoint = $ManagementUrl.TrimEnd('/') + "/actuator/prometheus"
$startedAt = [DateTimeOffset]::UtcNow
$deadline = [DateTimeOffset]::UtcNow.AddSeconds($DurationSeconds)

foreach ($generatedFile in @($prometheusPath, $dockerPath, $dockerLogPath)) {
    if (Test-Path -LiteralPath $generatedFile) {
        Remove-Item -LiteralPath $generatedFile -Force
    }
}

while ([DateTimeOffset]::UtcNow -lt $deadline `
    -and (-not $StopFile -or -not (Test-Path -LiteralPath $StopFile))) {
    $sampleStartedAt = [DateTimeOffset]::UtcNow
    $timestamp = [DateTimeOffset]::UtcNow.ToString("O")
    try {
        $snapshot = Invoke-RestMethod -Uri $managementEndpoint -TimeoutSec 3
        Add-Content -LiteralPath $prometheusPath -Value "# snapshot $timestamp"
        Add-Content -LiteralPath $prometheusPath -Value $snapshot
    } catch {
        Add-Content -LiteralPath $prometheusPath -Value (
            "# snapshot_error $timestamp " + $_.Exception.Message
        )
    }

    if ($DockerContainer) {
        try {
            if ($SshHost) {
                $sshArguments = @("-o", "BatchMode=yes")
                if ($SshIdentityFile) {
                    $sshArguments += @("-i", $SshIdentityFile)
                }
                $remoteCommand = "docker stats --no-stream " `
                    + "--format '{{json .}}' " + $DockerContainer
                $sshArguments += @($SshHost, $remoteCommand)
                $stats = & ssh @sshArguments
            } else {
                $stats = docker stats --no-stream --format "{{json .}}" $DockerContainer
            }
            if ($LASTEXITCODE -ne 0) {
                throw "docker stats failed with exit code $LASTEXITCODE"
            }
            Add-Content -LiteralPath $dockerPath -Value (
                '{"timestamp":"' + $timestamp + '","stats":' + $stats + '}'
            )
        } catch {
            $escapedError = $_.Exception.Message.Replace('"', '\"')
            $errorLine = '{"timestamp":"' + $timestamp `
                + '","error":"' + $escapedError + '"}'
            Add-Content -LiteralPath $dockerPath -Value $errorLine
        }
    }

    $remainingMilliseconds = 1000 * $IntervalSeconds `
        - ([DateTimeOffset]::UtcNow - $sampleStartedAt).TotalMilliseconds
    if ($remainingMilliseconds -gt 0) {
        Start-Sleep -Milliseconds ([int]$remainingMilliseconds)
    }
}

if ($DockerContainer) {
    try {
        $since = $startedAt.ToString("O")
        if ($SshHost) {
            $sshArguments = @("-o", "BatchMode=yes")
            if ($SshIdentityFile) {
                $sshArguments += @("-i", $SshIdentityFile)
            }
            $remoteCommand = "docker logs --since " + $since + " " `
                + $DockerContainer + " 2>&1"
            $sshArguments += @($SshHost, $remoteCommand)
            $logs = & ssh @sshArguments
        } else {
            $logs = & docker logs --since $since $DockerContainer 2>&1
        }
        if ($LASTEXITCODE -ne 0) {
            throw "docker logs failed with exit code $LASTEXITCODE"
        }
        Set-Content -LiteralPath $dockerLogPath -Value $logs -Encoding utf8
    } catch {
        Set-Content -LiteralPath $dockerLogPath -Value (
            "log_collection_error: " + $_.Exception.Message
        ) -Encoding utf8
    }
}
