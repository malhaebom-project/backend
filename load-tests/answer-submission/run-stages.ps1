param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$ManagementUrl = "http://127.0.0.1:9090",
    [Parameter(Mandatory = $true)]
    [string]$Manifest,
    [string]$ResultRoot = "load-tests/results",
    [int[]]$Stages = @(10, 100, 200, 300),
    [ValidateRange(0, 10000)]
    [int]$AssessmentQueueCapacity = 64,
    [ValidateRange(1, 3600)]
    [int]$AssessmentMaxQueueWaitSeconds = 10,
    [ValidateRange(0, 2)]
    [int]$ClientMaxRetries = 0,
    [int]$ProbeP95FloorMillis = 1000,
    [int]$RecoveryTimeoutSeconds = 300,
    [string]$PrometheusRemoteWriteUrl = "",
    [string]$TestId = "",
    [string]$Scenario = "default",
    [switch]$RunK6InDocker,
    [string]$DockerContainer = "",
    [string]$SshHost = "",
    [string]$SshIdentityFile = ""
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "run-stages.ps1 requires PowerShell 7 or later."
}
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$k6Script = Join-Path $scriptRoot "answer-submission.js"
$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $scriptRoot "../..")
)
$observabilityComposeFile = Join-Path $repositoryRoot `
    "docker-compose.observability.yml"
$collectorScript = Join-Path $scriptRoot "collect-metrics.ps1"
$powerShellExecutable = (Get-Process -Id $PID).Path
$manifestPath = [System.IO.Path]::GetFullPath($Manifest)
$resultRootPath = [System.IO.Path]::GetFullPath($ResultRoot)
$failedStages = @()
if ($Stages.Count -eq 0 `
    -or @($Stages | Where-Object { $_ -lt 1 -or $_ -gt 10000 }).Count -gt 0) {
    throw "Stages must contain values between 1 and 10000."
}
if (@($Stages | Select-Object -Unique).Count -ne $Stages.Count) {
    throw "Stages must not contain duplicates."
}
$manifestDocument = Get-Content -LiteralPath $manifestPath -Raw `
    | ConvertFrom-Json
$manifestRunId = [string]$manifestDocument.runId
if ([string]::IsNullOrWhiteSpace($manifestRunId)) {
    throw "Manifest runId is required for k6 metric correlation."
}
$metricTestId = if ([string]::IsNullOrWhiteSpace($TestId)) {
    $manifestRunId
} else {
    $TestId
}
if ($Scenario -notmatch '^[a-z0-9][a-z0-9-]{0,28}$') {
    throw "Scenario must use 1-29 lowercase letters, numbers, or hyphens."
}
if ($PrometheusRemoteWriteUrl) {
    $remoteWriteUri = [uri]$PrometheusRemoteWriteUrl
    if (-not $remoteWriteUri.IsAbsoluteUri `
        -or $remoteWriteUri.Scheme -notin @("http", "https")) {
        throw "PrometheusRemoteWriteUrl must be an absolute HTTP(S) URL."
    }
}

function K6-MetricValue($summary, [string]$name, [string]$field) {
    $metric = $summary.metrics.$name
    if ($null -eq $metric -or $null -eq $metric.values.$field) {
        return $null
    }
    return [double]$metric.values.$field
}

function Actuator-GaugeValue([string]$endpoint, [bool]$optional = $false) {
    try {
        $metric = Invoke-RestMethod -Uri $endpoint -TimeoutSec 3
        return [double]$metric.measurements[0].value
    } catch {
        if ($optional) {
            return 0.0
        }
        throw
    }
}

function Start-ArgumentListProcess(
    [string]$executable,
    [string[]]$arguments
) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $executable
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    foreach ($argument in $arguments) {
        [void]$startInfo.ArgumentList.Add([string]$argument)
    }
    $process = [System.Diagnostics.Process]::Start($startInfo)
    if ($null -eq $process) {
        throw "Failed to start metrics collector process."
    }
    return $process
}

foreach ($stage in $Stages) {
    $stageDirectory = Join-Path $resultRootPath ("stage-" + $stage)
    New-Item -ItemType Directory -Path $stageDirectory -Force | Out-Null
    $summaryPath = Join-Path $stageDirectory "summary.json"
    $rawPath = Join-Path $stageDirectory "k6-raw.json"
    $metricsDirectory = Join-Path $stageDirectory "server-metrics"
    $collectorStopFile = Join-Path $stageDirectory `
        ("metrics-collector-" + $PID + ".stop")
    $evaluationPath = Join-Path $stageDirectory "stage-evaluation.json"
    foreach ($generatedFile in @(
        $summaryPath,
        $rawPath,
        $collectorStopFile,
        $evaluationPath,
        (Join-Path $stageDirectory "k6-exit-code.txt")
    )) {
        if (Test-Path -LiteralPath $generatedFile) {
            Remove-Item -LiteralPath $generatedFile -Force
        }
    }

    $collectorArguments = @("-NoProfile")
    if ($IsWindows) {
        $collectorArguments += @("-ExecutionPolicy", "Bypass")
    }
    $collectorArguments += @(
        "-File", $collectorScript,
        "-ManagementUrl", $ManagementUrl,
        "-OutputDirectory", $metricsDirectory,
        "-DurationSeconds", "300",
        "-StopFile", $collectorStopFile
    )
    if ($DockerContainer) {
        $collectorArguments += @("-DockerContainer", $DockerContainer)
    }
    if ($SshHost) {
        $collectorArguments += @("-SshHost", $SshHost)
    }
    if ($SshIdentityFile) {
        $collectorArguments += @("-SshIdentityFile", $SshIdentityFile)
    }
    $collector = Start-ArgumentListProcess $powerShellExecutable `
        $collectorArguments

    $stageExitCode = 0
    $probeP95 = $null
    $baselineProbeP95 = $null
    $probeLimit = $null
    $probeLatencyPassed = $false
    $recoveryStarted = [DateTimeOffset]::UtcNow
    $recoveryTimedOut = $false
    $collectorExitCode = $null
    $lastQueueSizeValue = $null
    $lastPendingValue = $null
    try {
        $k6BaseUrl = if ($RunK6InDocker) {
            "http://management-tunnel:18080"
        } else {
            $BaseUrl
        }
        $k6ManifestPath = if ($RunK6InDocker) {
            "/run/manifest.json"
        } else {
            $manifestPath.Replace('\', '/')
        }
        $k6SummaryPath = if ($RunK6InDocker) {
            "/run-results/summary.json"
        } else {
            $summaryPath.Replace('\', '/')
        }
        $k6RawPath = if ($RunK6InDocker) {
            "/run-results/k6-raw.json"
        } else {
            $rawPath
        }
        $k6RemoteWriteUrl = if ($RunK6InDocker `
            -and $PrometheusRemoteWriteUrl) {
            "http://prometheus:9090/api/v1/write"
        } else {
            $PrometheusRemoteWriteUrl
        }
        $k6Arguments = @(
            "run",
            "--quiet",
            "-e", "BASE_URL=$k6BaseUrl",
            "-e", "MANIFEST=$k6ManifestPath",
            "-e", "CONCURRENCY=$stage",
            "-e", "ASSESSMENT_QUEUE_CAPACITY=$AssessmentQueueCapacity",
            "-e", ("ASSESSMENT_MAX_QUEUE_WAIT_SECONDS=" `
                + $AssessmentMaxQueueWaitSeconds),
            "-e", "CLIENT_MAX_RETRIES=$ClientMaxRetries",
            "-e", "SCENARIO_NAME=$Scenario",
            "-e", "SUMMARY_PATH=$k6SummaryPath",
            "--tag", "testid=$metricTestId",
            "--tag", "load_scenario=$Scenario",
            "--tag", "stage=$stage",
            "--out", ("json=" + $k6RawPath)
        )
        if ($PrometheusRemoteWriteUrl) {
            $k6Arguments += @("--out", "experimental-prometheus-rw")
        }
        $k6Arguments += if ($RunK6InDocker) {
            "/scripts/answer-submission.js"
        } else {
            $k6Script
        }

        $previousRemoteWriteUrl = $env:K6_PROMETHEUS_RW_SERVER_URL
        $previousTrendStats = $env:K6_PROMETHEUS_RW_TREND_STATS
        $previousStaleMarkers = $env:K6_PROMETHEUS_RW_STALE_MARKERS
        try {
            if ($k6RemoteWriteUrl) {
                $env:K6_PROMETHEUS_RW_SERVER_URL =
                    $k6RemoteWriteUrl
                $env:K6_PROMETHEUS_RW_TREND_STATS =
                    "p(95),p(99),avg,max"
                $env:K6_PROMETHEUS_RW_STALE_MARKERS = "true"
            }
            if ($RunK6InDocker) {
                $dockerArguments = @(
                    "compose",
                    "-f", $observabilityComposeFile,
                    "--profile", "runner",
                    "run",
                    "--rm",
                    "--no-deps",
                    "--volume", "${manifestPath}:/run/manifest.json:ro",
                    "--volume", "${stageDirectory}:/run-results"
                )
                if ($k6RemoteWriteUrl) {
                    $dockerArguments += @(
                        "--env", "K6_PROMETHEUS_RW_SERVER_URL=$k6RemoteWriteUrl",
                        "--env", "K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),avg,max",
                        "--env", "K6_PROMETHEUS_RW_STALE_MARKERS=true"
                    )
                }
                $dockerArguments += "k6"
                $dockerArguments += $k6Arguments
                & docker @dockerArguments
            } else {
                & k6 @k6Arguments
            }
            $stageExitCode = $LASTEXITCODE
        } finally {
            $env:K6_PROMETHEUS_RW_SERVER_URL = $previousRemoteWriteUrl
            $env:K6_PROMETHEUS_RW_TREND_STATS = $previousTrendStats
            $env:K6_PROMETHEUS_RW_STALE_MARKERS = $previousStaleMarkers
        }

        if (Test-Path -LiteralPath $summaryPath) {
            $summary = Get-Content -LiteralPath $summaryPath -Raw `
                | ConvertFrom-Json
            $probeP95 = K6-MetricValue $summary "probe_duration" "p(95)"
            $baselineProbeP95 = K6-MetricValue $summary `
                "probe_baseline_duration" "p(95)"
            if ($null -ne $probeP95 -and $null -ne $baselineProbeP95) {
                $probeLimit = [math]::Max(
                    $ProbeP95FloorMillis,
                    2 * $baselineProbeP95
                )
                $probeLatencyPassed = $probeP95 -le $probeLimit
            }
        }
        if (-not $probeLatencyPassed -and $stageExitCode -eq 0) {
            $stageExitCode = 1
        }

        $idleStarted = $null
        while ($true) {
            $managementBase = $ManagementUrl.TrimEnd('/')
            $queueSizeEndpoint = $managementBase `
                + "/actuator/metrics/malhaebom.answer.assessment.queue.size"
            $pendingEndpoint = $managementBase `
                + "/actuator/metrics/hikaricp.connections.pending"
            $lastQueueSizeValue = Actuator-GaugeValue `
                $queueSizeEndpoint $true
            $lastPendingValue = Actuator-GaugeValue $pendingEndpoint
            if ($lastQueueSizeValue -eq 0 `
                -and $lastPendingValue -eq 0) {
                if ($null -eq $idleStarted) {
                    $idleStarted = [DateTimeOffset]::UtcNow
                }
                if (([DateTimeOffset]::UtcNow - $idleStarted).TotalSeconds `
                    -ge 10) {
                    break
                }
            } else {
                $idleStarted = $null
            }
            if (([DateTimeOffset]::UtcNow - $recoveryStarted).TotalSeconds `
                -ge $RecoveryTimeoutSeconds) {
                $recoveryTimedOut = $true
                if ($stageExitCode -eq 0) {
                    $stageExitCode = 1
                }
                break
            }
            Start-Sleep -Seconds 1
        }
    } finally {
        Set-Content -LiteralPath $collectorStopFile -Value "stop" `
            -Encoding ascii
        $collector.WaitForExit()
        $collectorExitCode = $collector.ExitCode
        if ($collectorExitCode -ne 0 -and $stageExitCode -eq 0) {
            $stageExitCode = 1
        }
    }
    Set-Content -LiteralPath (Join-Path $stageDirectory "k6-exit-code.txt") `
        -Value $stageExitCode -Encoding ascii
    [pscustomobject]@{
        testId = $metricTestId
        fixtureRunId = $manifestRunId
        scenario = $Scenario
        stage = $stage
        prometheusRemoteWriteEnabled = [bool]$PrometheusRemoteWriteUrl
        assessmentQueueCapacity = $AssessmentQueueCapacity
        assessmentMaxQueueWaitSeconds = $AssessmentMaxQueueWaitSeconds
        clientMaxRetries = $ClientMaxRetries
        probeBaselineP95Millis = $baselineProbeP95
        probeLoadedP95Millis = $probeP95
        probeLimitMillis = $probeLimit
        probeLatencyPassed = $probeLatencyPassed
        metricsCollectorExitCode = $collectorExitCode
        recoveryTimedOut = $recoveryTimedOut
        recoveredQueueSize = $lastQueueSizeValue
        recoveredHikariPending = $lastPendingValue
        recoveryElapsedSeconds = [math]::Round(
            ([DateTimeOffset]::UtcNow - $recoveryStarted).TotalSeconds,
            1
        )
    } | ConvertTo-Json | Set-Content -LiteralPath $evaluationPath `
        -Encoding utf8

    if ($stageExitCode -ne 0) {
        $failedStages += $stage
    }
    if ($recoveryTimedOut) {
        throw "Stage $stage did not recover within " `
            + "$RecoveryTimeoutSeconds seconds."
    }
}

if ($failedStages.Count -gt 0) {
    throw "k6 threshold failure in stages: $($failedStages -join ', ')"
}
