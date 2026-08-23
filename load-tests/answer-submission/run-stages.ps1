param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$ManagementUrl = "http://127.0.0.1:9090",
    [Parameter(Mandatory = $true)]
    [string]$Manifest,
    [string]$ResultRoot = "load-tests/results",
    [int[]]$Stages = @(10, 100, 200, 300),
    [ValidateRange(1, 10000)]
    [int]$AssessmentLimit = 48,
    [ValidateRange(0, 2)]
    [int]$ClientMaxRetries = 0,
    [int]$ProbeP95FloorMillis = 1000,
    [int]$RecoveryTimeoutSeconds = 300,
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
$collectorScript = Join-Path $scriptRoot "collect-metrics.ps1"
$powerShellExecutable = (Get-Process -Id $PID).Path
$manifestPath = [System.IO.Path]::GetFullPath($Manifest)
$resultRootPath = [System.IO.Path]::GetFullPath($ResultRoot)
$failedStages = @()

function K6-MetricValue($summary, [string]$name, [string]$field) {
    $metric = $summary.metrics.$name
    if ($null -eq $metric -or $null -eq $metric.values.$field) {
        return $null
    }
    return [double]$metric.values.$field
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
    $collectorProcessParameters = @{
        FilePath = $powerShellExecutable
        ArgumentList = $collectorArguments
        PassThru = $true
    }
    if ($IsWindows) {
        $collectorProcessParameters.WindowStyle = "Hidden"
    }
    $collector = Start-Process @collectorProcessParameters

    $stageExitCode = 0
    $probeP95 = $null
    $baselineProbeP95 = $null
    $probeLimit = $null
    $probeLatencyPassed = $false
    $recoveryStarted = [DateTimeOffset]::UtcNow
    $recoveryTimedOut = $false
    $collectorExitCode = $null
    try {
        $k6Arguments = @(
            "run",
            "--quiet",
            "-e", "BASE_URL=$BaseUrl",
            "-e", ("MANIFEST=" + $manifestPath.Replace('\', '/')),
            "-e", "CONCURRENCY=$stage",
            "-e", "ASSESSMENT_LIMIT=$AssessmentLimit",
            "-e", "CLIENT_MAX_RETRIES=$ClientMaxRetries",
            "-e", ("SUMMARY_PATH=" + $summaryPath.Replace('\', '/')),
            "--out", ("json=" + $rawPath),
            $k6Script
        )
        & k6 @k6Arguments
        $stageExitCode = $LASTEXITCODE

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
            $activeEndpoint = $managementBase `
                + "/actuator/metrics/malhaebom.answer.assessment.active"
            $pendingEndpoint = $managementBase `
                + "/actuator/metrics/hikaricp.connections.pending"
            $active = Invoke-RestMethod -Uri $activeEndpoint -TimeoutSec 3
            $pending = Invoke-RestMethod -Uri $pendingEndpoint -TimeoutSec 3
            $activeValue = [double]$active.measurements[0].value
            $pendingValue = [double]$pending.measurements[0].value
            if ($activeValue -eq 0 -and $pendingValue -eq 0) {
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
        stage = $stage
        clientMaxRetries = $ClientMaxRetries
        probeBaselineP95Millis = $baselineProbeP95
        probeLoadedP95Millis = $probeP95
        probeLimitMillis = $probeLimit
        probeLatencyPassed = $probeLatencyPassed
        metricsCollectorExitCode = $collectorExitCode
        recoveryTimedOut = $recoveryTimedOut
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
