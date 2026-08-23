param(
    [Parameter(Mandatory = $true)]
    [string]$ResultRoot,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [string]$Environment = "local",
    [string]$GitCommit = "",
    [string]$FixtureRunId = "",
    [ValidateRange(1, 10000)]
    [int]$AssessmentLimit = 32,
    [ValidateRange(0, 10000)]
    [int]$AssessmentQueueCapacity = 64,
    [ValidateRange(1, 3600)]
    [int]$AssessmentMaxQueueWaitSeconds = 10,
    [ValidateRange(0, 2)]
    [int]$ClientMaxRetries = 0
)

$ErrorActionPreference = "Stop"
$stages = @(10, 100, 200, 300)
$rows = @()
$immediateAdmissionCapacity = $AssessmentLimit + $AssessmentQueueCapacity
$overloadP95LimitMillis = $AssessmentMaxQueueWaitSeconds * 1000 + 2000

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
    $values = @(Get-Content -LiteralPath $path | ForEach-Object {
        if ($_ -match ("^" + $escaped + "(?:\{.*\})?\s+([-+0-9.eE]+)$")) {
            [double]$Matches[1]
        }
    })
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
                OpenAiQueueSize = 0.0
                QueueFull = 0.0
                QueueTimeout = 0.0
                QueueCancelled = 0.0
                HikariPending = 0.0
                TomcatBusy = 0.0
                TomcatMax = 0.0
            }
            continue
        }
        if ($null -eq $current) {
            continue
        }
        if ($line -match '^malhaebom_answer_assessment_active(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.OpenAiActive = [double]$Matches[1]
        } elseif ($line -match '^malhaebom_answer_assessment_queue_size(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.OpenAiQueueSize = [double]$Matches[1]
        } elseif ($line -match '^malhaebom_answer_assessment_queue_full_total(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.QueueFull = [double]$Matches[1]
        } elseif ($line -match '^malhaebom_answer_assessment_queue_timeout_total(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.QueueTimeout = [double]$Matches[1]
        } elseif ($line -match '^malhaebom_answer_assessment_queue_cancelled_total(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.QueueCancelled = [double]$Matches[1]
        } elseif ($line -match '^hikaricp_connections_pending(?:\{.*\})?\s+([-+0-9.eE]+)$') {
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

function Counter-Delta($snapshots, [string]$property) {
    if ($snapshots.Count -lt 2) {
        return 0
    }
    return [math]::Max(
        0,
        [double]$snapshots[-1].$property - [double]$snapshots[0].$property
    )
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
    if ($null -ne $summary.clientMaxRetries `
        -and [int]$summary.clientMaxRetries -ne $ClientMaxRetries) {
        throw "Summary retry setting does not match -ClientMaxRetries: " `
            + "$summaryPath"
    }
    if ($null -ne $summary.assessmentLimit `
        -and [int]$summary.assessmentLimit -ne $AssessmentLimit) {
        throw "Summary active limit does not match -AssessmentLimit: $summaryPath"
    }
    if ($null -ne $summary.assessmentQueueCapacity `
        -and [int]$summary.assessmentQueueCapacity -ne $AssessmentQueueCapacity) {
        throw "Summary queue capacity does not match report setting: $summaryPath"
    }
    if ($null -ne $summary.assessmentMaxQueueWaitSeconds `
        -and [double]$summary.assessmentMaxQueueWaitSeconds `
            -ne $AssessmentMaxQueueWaitSeconds) {
        throw "Summary queue wait does not match report setting: $summaryPath"
    }

    $snapshots = @(Read-PrometheusSnapshots $prometheusPath)
    $waitingSnapshots = @($snapshots | Where-Object {
        ($_.OpenAiActive -gt 0 -or $_.OpenAiQueueSize -gt 0) `
            -and $_.TomcatMax -gt 0
    })
    $minimumBusyPercent = 0
    if ($waitingSnapshots.Count -gt 0) {
        $minimumBusyPercent = ($waitingSnapshots | ForEach-Object {
            100 * $_.TomcatBusy / $_.TomcatMax
        } | Measure-Object -Minimum).Minimum
    }
    $recovered = $false
    $finalActive = 0
    $finalQueueSize = 0
    $finalHikariPending = 0
    if ($snapshots.Count -gt 0) {
        $last = $snapshots[-1]
        $finalActive = $last.OpenAiActive
        $finalQueueSize = $last.OpenAiQueueSize
        $finalHikariPending = $last.HikariPending
        $recovered = $finalActive -eq 0 `
            -and $finalQueueSize -eq 0 `
            -and $finalHikariPending -eq 0
    }

    $finalOverload = Metric-Value $summary `
        "answer_expected_overload" "count"
    $rawOverload = Metric-Value $summary `
        "answer_raw_expected_overload" "count"
    if ($ClientMaxRetries -eq 0 -and $rawOverload -eq 0) {
        $rawOverload = $finalOverload
    }
    $rows += [pscustomobject]@{
        Stage = $stage
        Attempts = Metric-Value $summary "answer_attempts" "count"
        Classified = Metric-Value $summary "answer_classified" "count"
        RetryAttempts = Metric-Value $summary `
            "answer_retry_attempts" "count"
        RetryRecovered = Metric-Value $summary `
            "answer_retry_recovered" "count"
        Success = Metric-Value $summary "answer_success" "count"
        Overload = $finalOverload
        RawOverload = $rawOverload
        Unexpected = Metric-Value $summary "answer_unexpected_response" "count"
        Mismatch = Metric-Value $summary "answer_response_mismatch" "count"
        SuccessP95 = Metric-Value $summary "answer_success_duration" "p(95)"
        SuccessMax = Metric-Value $summary "answer_success_duration" "max"
        OverloadP95 = Metric-Value $summary "answer_overload_duration" "p(95)"
        FinalSuccessP95 = Metric-Value $summary `
            "answer_final_success_duration" "p(95)"
        FinalOverloadP95 = Metric-Value $summary `
            "answer_final_overload_duration" "p(95)"
        BaselineProbeP95 = Metric-Value $summary `
            "probe_baseline_duration" "p(95)"
        ProbeP95 = Metric-Value $summary "probe_duration" "p(95)"
        BaselineProbeRate = Metric-Value $summary `
            "probe_baseline_success" "rate"
        ProbeRate = Metric-Value $summary "probe_success" "rate"
        OpenAiActive = Prometheus-Max $prometheusPath `
            "malhaebom_answer_assessment_active"
        OpenAiQueueSize = Prometheus-Max $prometheusPath `
            "malhaebom_answer_assessment_queue_size"
        QueueFull = Counter-Delta $snapshots "QueueFull"
        QueueTimeout = Counter-Delta $snapshots "QueueTimeout"
        QueueCancelled = Counter-Delta $snapshots "QueueCancelled"
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
        FinalActive = $finalActive
        FinalQueueSize = $finalQueueSize
        FinalHikariPending = $finalHikariPending
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
    "- OpenAI active 제한: $AssessmentLimit",
    "- 대기열 용량: $AssessmentQueueCapacity",
    "- 최대 대기 시간: $AssessmentMaxQueueWaitSeconds 초",
    "- 즉시 수용 가능량(active + queue): $immediateAdmissionCapacity",
    "- 클라이언트 최대 재시도: $ClientMaxRetries",
    "",
    "| 동시 제출 | HTTP 시도 | 평균 시도 | 재시도 | 재시도 회복 | 최종 200 | 최종 503 | raw 503 | 기타 오류 | 누락 | 응답 혼합 |",
    "| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
)

foreach ($row in $rows) {
    $missing = $row.Stage - $row.Classified
    $averageAttempts = if ($row.Stage -gt 0) {
        [math]::Round($row.Attempts / $row.Stage, 2)
    } else {
        0
    }
    $lines += "| $($row.Stage) | $($row.Attempts) | $averageAttempts | " `
        + "$($row.RetryAttempts) | $($row.RetryRecovered) | " `
        + "$($row.Success) | $($row.Overload) | $($row.RawOverload) | " `
        + "$($row.Unexpected) | $missing | $($row.Mismatch) |"
}

$lines += @(
    "",
    "| 동시 제출 | 성공 HTTP p95(ms) | raw 503 p95(ms) | 최종 성공 p95(ms) | 최종 503 p95(ms) | baseline p95(ms) | 부하 중 probe p95(ms) | probe 성공률 |",
    "| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
)
foreach ($row in $rows) {
    $lines += "| $($row.Stage) | " `
        + "$([math]::Round($row.SuccessP95, 1)) | " `
        + "$([math]::Round($row.OverloadP95, 1)) | " `
        + "$([math]::Round($row.FinalSuccessP95, 1)) | " `
        + "$([math]::Round($row.FinalOverloadP95, 1)) | " `
        + "$([math]::Round($row.BaselineProbeP95, 1)) | " `
        + "$([math]::Round($row.ProbeP95, 1)) | " `
        + "$([math]::Round($row.ProbeRate, 4)) |"
}

$lines += @(
    "",
    "| 동시 제출 | 최대 active | 최대 queue | queue full | queue timeout | queue cancelled | Tomcat 최대 busy / max | Hikari 최대 pending |",
    "| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
)
foreach ($row in $rows) {
    $lines += "| $($row.Stage) | $($row.OpenAiActive) | " `
        + "$($row.OpenAiQueueSize) | $($row.QueueFull) | " `
        + "$($row.QueueTimeout) | $($row.QueueCancelled) | " `
        + "$($row.TomcatBusy) / $($row.TomcatMax) | " `
        + "$($row.HikariPending) |"
}

$lines += @(
    "",
    "| 동시 제출 | Hikari pending 지속(초) | Tomcat 포화 지속(초) | provider/queue 대기 중 Tomcat busy 최저(%) | 종료 active·queue·pending | 복구 |",
    "| ---: | ---: | ---: | ---: | :---: | :---: |"
)
foreach ($row in $rows) {
    $finalResources = "$($row.FinalActive)·$($row.FinalQueueSize)·$($row.FinalHikariPending)"
    $lines += "| $($row.Stage) | $($row.HikariPendingSeconds) | " `
        + "$($row.TomcatSaturatedSeconds) | " `
        + "$([math]::Round($row.WaitingMinimumBusyPercent, 1)) | " `
        + "$finalResources | $($row.Recovered) |"
}

$unexpectedTotal = ($rows | Measure-Object -Property Unexpected -Sum).Sum
$mismatchTotal = ($rows | Measure-Object -Property Mismatch -Sum).Sum
$missingTotal = ($rows | ForEach-Object {
    $_.Stage - $_.Classified
} | Measure-Object -Sum).Sum
$maxOpenAi = ($rows | Measure-Object -Property OpenAiActive -Maximum).Maximum
$maxQueue = ($rows | Measure-Object -Property OpenAiQueueSize -Maximum).Maximum
$queueFullTotal = ($rows | Measure-Object -Property QueueFull -Sum).Sum
$queueTimeoutTotal = ($rows | Measure-Object -Property QueueTimeout -Sum).Sum
$queueCancelledTotal = ($rows | Measure-Object -Property QueueCancelled -Sum).Sum
$allStagesPresent = $rows.Count -eq $stages.Count
$allStagesHaveSuccess = ($rows | Where-Object {
    $_.Success -le 0
}).Count -eq 0
$allWithinActiveLimitSucceeded = ($rows | Where-Object {
    $_.Stage -le $AssessmentLimit -and $_.Success -ne $_.Stage
}).Count -eq 0
$queueBoundaryMostlyAbsorbed = ($rows | Where-Object {
    $_.Stage -eq 100 -and $_.Success -le [math]::Floor($_.Stage / 2)
}).Count -eq 0
$allProbeSucceeded = ($rows | Where-Object {
    $_.ProbeRate -lt 1 -or $_.BaselineProbeRate -lt 1
}).Count -eq 0
$allProbeLatencyPassed = ($rows | Where-Object {
    $limit = [math]::Max(1000, 2 * $_.BaselineProbeP95)
    $_.ProbeP95 -gt $limit
}).Count -eq 0
$overloadObserved = ($rows | Where-Object {
    $_.Stage -ge 200 `
        -and $_.Stage -gt $immediateAdmissionCapacity `
        -and $_.RawOverload -le 0
}).Count -eq 0
$attemptsBounded = ($rows | Where-Object {
    $_.Attempts -lt $_.Stage `
        -or $_.Attempts -gt $_.Stage * ($ClientMaxRetries + 1)
}).Count -eq 0
$retryAccountingPassed = ($rows | Where-Object {
    $_.RetryAttempts -ne ($_.Attempts - $_.Stage) `
        -or $_.RawOverload -ne ($_.RetryAttempts + $_.Overload) `
        -or $_.RetryRecovered -gt $_.RetryAttempts `
        -or $_.RetryRecovered -gt $_.Success
}).Count -eq 0
$logicalAccountingPassed = ($rows | Where-Object {
    $_.Classified -ne $_.Stage `
        -or ($_.Success + $_.Overload + $_.Unexpected) -ne $_.Stage
}).Count -eq 0
$successDeadlinePassed = ($rows | Where-Object {
    $_.Success -gt 0 -and $_.SuccessMax -ge 30000
}).Count -eq 0
$overloadLatencyPassed = ($rows | Where-Object {
    $_.RawOverload -gt 0 -and $_.OverloadP95 -gt $overloadP95LimitMillis
}).Count -eq 0
$hikariPassed = ($rows | Where-Object {
    $_.HikariPendingSeconds -ge 2
}).Count -eq 0
$tomcatPassed = ($rows | Where-Object {
    $_.TomcatSaturatedSeconds -ge 2
}).Count -eq 0
$allRecovered = ($rows | Where-Object { -not $_.Recovered }).Count -eq 0
$logicalTotal = ($rows | Measure-Object -Property Stage -Sum).Sum
$attemptsTotal = ($rows | Measure-Object -Property Attempts -Sum).Sum
$retryAttemptsTotal = ($rows | Measure-Object `
    -Property RetryAttempts -Sum).Sum
$retryRecoveredTotal = ($rows | Measure-Object `
    -Property RetryRecovered -Sum).Sum
$successTotal = ($rows | Measure-Object -Property Success -Sum).Sum
$finalOverloadTotal = ($rows | Measure-Object -Property Overload -Sum).Sum
$rawOverloadTotal = ($rows | Measure-Object -Property RawOverload -Sum).Sum
$finalSuccessRate = if ($logicalTotal -gt 0) {
    [math]::Round(100 * $successTotal / $logicalTotal, 1)
} else {
    0
}
$tomcatReturnedDuringWait = ($rows | Where-Object {
    ($_.OpenAiActive -gt 0 -or $_.OpenAiQueueSize -gt 0) `
        -and $_.WaitingMinimumBusyPercent -ge 25
}).Count -eq 0
$lines += @(
    "",
    "## 자동 판정",
    "",
    "- 10/100/200/300 네 단계 결과 존재: $allStagesPresent",
    "- 예상하지 못한 상태 0건: $($unexpectedTotal -eq 0)",
    "- 누락 응답 0건: $($missingTotal -eq 0)",
    "- 응답 혼합 0건: $($mismatchTotal -eq 0)",
    "- 최종 200 + 최종 503 + 기타 = 논리적 제출: $logicalAccountingPassed",
    "- HTTP 시도 수가 제출당 최대 $($ClientMaxRetries + 1)회: $attemptsBounded",
    "- 재시도·회복 집계 일치: $retryAccountingPassed",
    "- 논리적 제출 / HTTP 시도: $logicalTotal / $attemptsTotal",
    "- 재시도 / 재시도 회복: $retryAttemptsTotal / $retryRecoveredTotal",
    ("- 최종 성공 / 최종 503 / raw 503: " `
        + "$successTotal / $finalOverloadTotal / $rawOverloadTotal"),
    "- raw 503은 provider 호출 수가 아니라 HTTP 과부하 응답 수",
    "- 최종 성공률: $finalSuccessRate%",
    "- 각 단계에서 성공 응답 관찰: $allStagesHaveSuccess",
    "- active 제한 이하 단계는 전부 성공: $allWithinActiveLimitSucceeded",
    "- 100단계는 과반 성공으로 queue 경계를 대부분 흡수: $queueBoundaryMostlyAbsorbed",
    "- OpenAI active $AssessmentLimit 이하: $($maxOpenAi -le $AssessmentLimit) (최대 $maxOpenAi)",
    "- queue size $AssessmentQueueCapacity 이하: $($maxQueue -le $AssessmentQueueCapacity) (최대 $maxQueue)",
    "- queue full / timeout / cancelled: $queueFullTotal / $queueTimeoutTotal / $queueCancelledTotal",
    "- 수용량 $immediateAdmissionCapacity 밖의 200/300단계에서 raw 503 관찰: $overloadObserved",
    "- 성공 응답 30초 이내: $successDeadlinePassed",
    "- raw 503 p95 $overloadP95LimitMillis ms 이하(대기 $AssessmentMaxQueueWaitSeconds 초 + 응답 여유 2초): $overloadLatencyPassed",
    "- probe 성공률 100%: $allProbeSucceeded",
    "- probe p95가 max(baseline x 2, 1초) 이하: $allProbeLatencyPassed",
    "- Hikari pending 2초 미만: $hikariPassed",
    "- Tomcat max 포화 2초 미만: $tomcatPassed",
    "- provider/queue 대기 중 Tomcat busy가 max의 25% 아래로 복귀: $tomcatReturnedDuringWait",
    "- 단계 종료 시 active=0, queue=0, Hikari pending=0: $allRecovered",
    "",
    "HTTP 응답만으로 queue full과 queue timeout을 구분할 수 없다. " `
        + "위 서버 queue 카운터로 원인을 분리한다.",
    "queue는 짧은 burst를 흡수하지만 처리량 자체를 늘리지 않으며, " `
        + "300건 성공이나 제출 시작 기준 25초 deadline 연장을 보장하지 않는다.",
    "",
    "## 결론",
    "",
    "- queue/active 설정 조정 필요 여부:",
    "- 병목과 후속 작업:"
)

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Path $parent -Force | Out-Null
Set-Content -LiteralPath $resolvedOutput -Value $lines -Encoding utf8
