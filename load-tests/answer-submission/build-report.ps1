param(
    [Parameter(Mandatory = $true)]
    [string]$ResultRoot,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [string]$Environment = "local",
    [string]$GitCommit = "",
    [string]$FixtureRunId = ""
)

$ErrorActionPreference = "Stop"
$stages = @(10, 100, 200, 300)
$rows = @()

function Metric-Value($summary, [string]$name, [string]$field) {
    $metric = $summary.metrics.$name
    if ($null -eq $metric -or $null -eq $metric.values.$field) {
        return 0
    }
    return [double]$metric.values.$field
}

function Prometheus-Max([string]$path, [string]$metricName) {
    if (-not (Test-Path -LiteralPath $path)) {
        return 0
    }
    $escaped = [regex]::Escape($metricName)
    $values = Get-Content -LiteralPath $path | ForEach-Object {
        if ($_ -match ("^" + $escaped + "(?:\{.*\})?\s+([-+0-9.eE]+)$")) {
            [double]$Matches[1]
        }
    }
    if ($values.Count -eq 0) {
        return 0
    }
    return ($values | Measure-Object -Maximum).Maximum
}

function Read-PrometheusSnapshots([string]$path) {
    $snapshots = [System.Collections.Generic.List[object]]::new()
    $current = $null
    if (-not (Test-Path -LiteralPath $path)) {
        return @()
    }

    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^# snapshot (.+)$') {
            if ($null -ne $current) {
                $snapshots.Add([pscustomobject]$current)
            }
            $current = [ordered]@{
                Timestamp = [DateTimeOffset]::Parse($Matches[1])
                OpenAiActive = 0.0
                HikariPending = 0.0
                TomcatBusy = 0.0
                TomcatMax = 0.0
            }
            continue
        }
        if ($null -eq $current) {
            continue
        }
        if ($line -match `
            '^malhaebom_answer_assessment_active(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.OpenAiActive = [double]$Matches[1]
        } elseif ($line -match `
            '^hikaricp_connections_pending(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.HikariPending = [double]$Matches[1]
        } elseif ($line -match '^tomcat_threads_busy_threads\{.*name="http-nio-8080".*\}\s+([-+0-9.eE]+)$') {
            $current.TomcatBusy = [double]$Matches[1]
        } elseif ($line -match '^tomcat_threads_config_max_threads\{.*name="http-nio-8080".*\}\s+([-+0-9.eE]+)$') {
            $current.TomcatMax = [double]$Matches[1]
        }
    }
    if ($null -ne $current) {
        $snapshots.Add([pscustomobject]$current)
    }
    return $snapshots.ToArray()
}

function Max-SustainedSeconds($snapshots, [scriptblock]$predicate) {
    $startedAt = $null
    $maximum = 0.0
    foreach ($snapshot in $snapshots) {
        if (& $predicate $snapshot) {
            if ($null -eq $startedAt) {
                $startedAt = $snapshot.Timestamp
            }
            $duration = ($snapshot.Timestamp - $startedAt).TotalSeconds
            $maximum = [math]::Max($maximum, $duration)
        } else {
            $startedAt = $null
        }
    }
    return [math]::Round($maximum, 1)
}

foreach ($stage in $stages) {
    $stageDirectory = Join-Path $ResultRoot ("stage-" + $stage)
    $summaryPath = Join-Path $stageDirectory "summary.json"
    $prometheusPath = Join-Path $stageDirectory `
        "server-metrics/actuator-prometheus.txt"
    if (-not (Test-Path -LiteralPath $summaryPath)) {
        continue
    }
    $summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
    $snapshots = @(Read-PrometheusSnapshots $prometheusPath)
    $waitingSnapshots = @($snapshots | Where-Object {
        $_.OpenAiActive -gt 0 -and $_.TomcatMax -gt 0
    })
    $minimumBusyPercent = 0
    if ($waitingSnapshots.Count -gt 0) {
        $minimumBusyPercent = ($waitingSnapshots | ForEach-Object {
            100 * $_.TomcatBusy / $_.TomcatMax
        } | Measure-Object -Minimum).Minimum
    }
    $recovered = $false
    if ($snapshots.Count -gt 0) {
        $last = $snapshots[-1]
        $recovered = $last.OpenAiActive -eq 0 `
            -and $last.HikariPending -eq 0
    }
    $rows += [pscustomobject]@{
        Stage = $stage
        Attempts = Metric-Value $summary "answer_attempts" "count"
        Success = Metric-Value $summary "answer_success" "count"
        Overload = Metric-Value $summary "answer_expected_overload" "count"
        Unexpected = Metric-Value $summary "answer_unexpected_response" "count"
        Mismatch = Metric-Value $summary "answer_response_mismatch" "count"
        SuccessP95 = Metric-Value $summary "answer_success_duration" "p(95)"
        SuccessMax = Metric-Value $summary "answer_success_duration" "max"
        OverloadP95 = Metric-Value $summary "answer_overload_duration" "p(95)"
        BaselineProbeP95 = Metric-Value $summary `
            "probe_baseline_duration" "p(95)"
        ProbeP95 = Metric-Value $summary "probe_duration" "p(95)"
        BaselineProbeRate = Metric-Value $summary `
            "probe_baseline_success" "rate"
        ProbeRate = Metric-Value $summary "probe_success" "rate"
        OpenAiActive = Prometheus-Max $prometheusPath `
            "malhaebom_answer_assessment_active"
        TomcatBusy = Prometheus-Max $prometheusPath `
            "tomcat_threads_busy_threads"
        TomcatMax = Prometheus-Max $prometheusPath `
            "tomcat_threads_config_max_threads"
        HikariPending = Prometheus-Max $prometheusPath `
            "hikaricp_connections_pending"
        HikariPendingSeconds = Max-SustainedSeconds $snapshots {
            param($snapshot)
            $snapshot.HikariPending -gt 0
        }
        TomcatSaturatedSeconds = Max-SustainedSeconds $snapshots {
            param($snapshot)
            $snapshot.TomcatMax -gt 0 `
                -and $snapshot.TomcatBusy -ge $snapshot.TomcatMax
        }
        WaitingMinimumBusyPercent = $minimumBusyPercent
        Recovered = $recovered
    }
}

$lines = @(
    "# 답안 제출 비동기 부하 테스트 결과",
    "",
    "- 실행 일시: $([DateTimeOffset]::Now.ToString('O'))",
    "- 실행 환경: $Environment",
    "- Git commit: $GitCommit",
    "- fixture run-id: $FixtureRunId",
    "- OpenAI 동시 제한: 32",
    "",
    "| 동시 제출 | 200 성공 | 예상 503 | 기타 오류 | 누락 | 응답 혼합 | 성공 p95(ms) | 503 p95(ms) | baseline p95(ms) | 부하 중 probe p95(ms) | probe 성공률 | OpenAI 최대 active | Tomcat 최대 busy / max | Hikari 최대 pending |",
    "| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
)

foreach ($row in $rows) {
    $classified = $row.Success + $row.Overload + $row.Unexpected
    $missing = $row.Stage - $classified
    $lines += "| $($row.Stage) | $($row.Success) | $($row.Overload) | " `
        + "$($row.Unexpected) | $missing | $($row.Mismatch) | " `
        + "$([math]::Round($row.SuccessP95, 1)) | " `
        + "$([math]::Round($row.OverloadP95, 1)) | " `
        + "$([math]::Round($row.BaselineProbeP95, 1)) | " `
        + "$([math]::Round($row.ProbeP95, 1)) | " `
        + "$([math]::Round($row.ProbeRate, 4)) | " `
        + "$($row.OpenAiActive) | $($row.TomcatBusy) / $($row.TomcatMax) | " `
        + "$($row.HikariPending) |"
}

$lines += @(
    "",
    "| 동시 제출 | Hikari pending 지속(초) | Tomcat 포화 지속(초) | OpenAI 대기 중 Tomcat busy 최저(%) | 종료 시 active·pending 복구 |",
    "| ---: | ---: | ---: | ---: | :---: |"
)
foreach ($row in $rows) {
    $lines += "| $($row.Stage) | $($row.HikariPendingSeconds) | " `
        + "$($row.TomcatSaturatedSeconds) | " `
        + "$([math]::Round($row.WaitingMinimumBusyPercent, 1)) | " `
        + "$($row.Recovered) |"
}

$unexpectedTotal = ($rows | Measure-Object -Property Unexpected -Sum).Sum
$mismatchTotal = ($rows | Measure-Object -Property Mismatch -Sum).Sum
$missingTotal = ($rows | ForEach-Object {
    $_.Stage - ($_.Success + $_.Overload + $_.Unexpected)
} |
    Measure-Object -Sum).Sum
$maxOpenAi = ($rows | Measure-Object -Property OpenAiActive -Maximum).Maximum
$allStagesPresent = $rows.Count -eq $stages.Count
$allStagesHaveSuccess = ($rows | Where-Object {
    $_.Success -le 0
}).Count -eq 0
$allUnderLimitSucceeded = ($rows | Where-Object {
    $_.Stage -le 32 -and $_.Success -ne $_.Stage
}).Count -eq 0
$allProbeSucceeded = ($rows | Where-Object {
    $_.ProbeRate -lt 1 -or $_.BaselineProbeRate -lt 1
}).Count -eq 0
$allProbeLatencyPassed = ($rows | Where-Object {
    $limit = [math]::Max(1000, 2 * $_.BaselineProbeP95)
    $_.ProbeP95 -gt $limit
}).Count -eq 0
$overloadObserved = ($rows | Where-Object {
    $_.Stage -gt 32 -and $_.Overload -le 0
}).Count -eq 0
$successDeadlinePassed = ($rows | Where-Object {
    $_.Success -gt 0 -and $_.SuccessMax -ge 30000
}).Count -eq 0
$overloadLatencyPassed = ($rows | Where-Object {
    $_.Overload -gt 0 -and $_.OverloadP95 -ge 5000
}).Count -eq 0
$hikariPassed = ($rows | Where-Object {
    $_.HikariPendingSeconds -ge 2
}).Count -eq 0
$tomcatPassed = ($rows | Where-Object {
    $_.TomcatSaturatedSeconds -ge 2
}).Count -eq 0
$allRecovered = ($rows | Where-Object { -not $_.Recovered }).Count -eq 0
$tomcatReturnedDuringWait = ($rows | Where-Object {
    $_.OpenAiActive -gt 0 -and $_.WaitingMinimumBusyPercent -ge 25
}).Count -eq 0
$lines += @(
    "",
    "## 자동 판정",
    "",
    "- 10/100/200/300 네 단계 결과 존재: $allStagesPresent",
    "- 예상하지 못한 상태 0건: $($unexpectedTotal -eq 0)",
    "- 누락 응답 0건: $($missingTotal -eq 0)",
    "- 응답 혼합 0건: $($mismatchTotal -eq 0)",
    "- 각 단계에서 성공 응답 관찰: $allStagesHaveSuccess",
    "- 32건 이하 단계는 전부 성공: $allUnderLimitSucceeded",
    "- OpenAI active 32 이하: $($maxOpenAi -le 32)",
    "- 32건 초과 단계에서 예상 503 관찰: $overloadObserved",
    "- 성공 응답 30초 이내: $successDeadlinePassed",
    "- 예상 503 p95 5초 이내: $overloadLatencyPassed",
    "- probe 성공률 100%: $allProbeSucceeded",
    "- probe p95가 max(baseline x 2, 1초) 이하: $allProbeLatencyPassed",
    "- Hikari pending 2초 미만: $hikariPassed",
    "- Tomcat max 포화 2초 미만: $tomcatPassed",
    "- provider 대기 중 Tomcat busy가 max의 25% 아래로 복귀: $tomcatReturnedDuringWait",
    "- 단계 종료 시 permit·connection 복구: $allRecovered",
    "",
    "## 결론",
    "",
    "- preparation limiter 필요 여부:",
    "- 병목과 후속 작업:"
)

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Path $parent -Force | Out-Null
Set-Content -LiteralPath $resolvedOutput -Value $lines -Encoding utf8
