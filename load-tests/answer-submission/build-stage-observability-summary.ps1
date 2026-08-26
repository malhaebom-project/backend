param(
    [Parameter(Mandatory = $true)]
    [string]$StageDirectory,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"

function Metric-Value($summary, [string]$name, [string]$field) {
    $metric = $summary.metrics.$name
    if ($null -eq $metric -or $null -eq $metric.values.$field) {
        return 0.0
    }
    return [double]$metric.values.$field
}

function New-Snapshot([DateTimeOffset]$timestamp) {
    return [ordered]@{
        Timestamp = $timestamp
        ProcessCpuPercent = 0.0
        SystemCpuPercent = 0.0
        HeapUsed = 0.0
        HeapMax = 0.0
        HeapPercent = 0.0
        GcPauseSeconds = 0.0
        QueueSize = 0.0
        QueueCapacity = 0.0
        QueueWaitMaxSeconds = 0.0
        AvailableRequests = 0.0
        AvailableTokens = 0.0
        RequestCapacity = 0.0
        TokenCapacity = 0.0
        RateLimitRejected = 0.0
        TomcatBusy = 0.0
        TomcatMax = 0.0
        HikariPending = 0.0
        HikariAcquireMaxSeconds = 0.0
        HikariTimeouts = 0.0
    }
}

function Complete-Snapshot($snapshot) {
    if ($null -eq $snapshot) {
        return
    }
    if ($snapshot.HeapMax -gt 0) {
        $snapshot.HeapPercent = 100 * $snapshot.HeapUsed / $snapshot.HeapMax
    }
}

function Read-Snapshots([string]$path) {
    $snapshots = [System.Collections.Generic.List[object]]::new()
    $current = $null
    if (-not (Test-Path -LiteralPath $path)) {
        return @()
    }

    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^# snapshot (.+)$') {
            Complete-Snapshot $current
            if ($null -ne $current) {
                $snapshots.Add([pscustomobject]$current)
            }
            $current = New-Snapshot ([DateTimeOffset]::Parse($Matches[1]))
            continue
        }
        if ($null -eq $current) {
            continue
        }

        if ($line -match '^process_cpu_usage(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.ProcessCpuPercent = 100 * [double]$Matches[1]
        } elseif ($line -match '^system_cpu_usage(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.SystemCpuPercent = 100 * [double]$Matches[1]
        } elseif ($line -match '^jvm_memory_used_bytes\{.*area="heap".*\}\s+([-+0-9.eE]+)$') {
            $current.HeapUsed += [double]$Matches[1]
        } elseif ($line -match '^jvm_memory_max_bytes\{.*area="heap".*\}\s+([-+0-9.eE]+)$') {
            $current.HeapMax += [double]$Matches[1]
        } elseif ($line -match '^jvm_gc_pause_seconds_sum(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.GcPauseSeconds += [double]$Matches[1]
        } elseif ($line -match '^malhaebom_answer_assessment_queue_size(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.QueueSize = [double]$Matches[1]
        } elseif ($line -match '^malhaebom_answer_assessment_queue_capacity(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.QueueCapacity = [double]$Matches[1]
        } elseif ($line -match '^malhaebom_answer_assessment_queue_wait_seconds_max(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.QueueWaitMaxSeconds = [math]::Max(
                $current.QueueWaitMaxSeconds,
                [double]$Matches[1]
            )
        } elseif ($line -match '^malhaebom_ai_provider_rate_limit_available\{.*provider="openai".*quota="requests".*\}\s+([-+0-9.eE]+)$') {
            $current.AvailableRequests = [double]$Matches[1]
        } elseif ($line -match '^malhaebom_ai_provider_rate_limit_available\{.*provider="openai".*quota="tokens".*\}\s+([-+0-9.eE]+)$') {
            $current.AvailableTokens = [double]$Matches[1]
        } elseif ($line -match '^malhaebom_ai_provider_rate_limit_capacity\{.*provider="openai".*quota="requests".*\}\s+([-+0-9.eE]+)$') {
            $current.RequestCapacity = [double]$Matches[1]
        } elseif ($line -match '^malhaebom_ai_provider_rate_limit_capacity\{.*provider="openai".*quota="tokens".*\}\s+([-+0-9.eE]+)$') {
            $current.TokenCapacity = [double]$Matches[1]
        } elseif ($line -match '^malhaebom_ai_provider_rate_limit_requests_total\{.*provider="openai".*result="rejected".*\}\s+([-+0-9.eE]+)$') {
            $current.RateLimitRejected = [double]$Matches[1]
        } elseif ($line -match '^tomcat_threads_busy_threads\{.*name="http-nio-8080".*\}\s+([-+0-9.eE]+)$') {
            $current.TomcatBusy = [double]$Matches[1]
        } elseif ($line -match '^tomcat_threads_config_max_threads\{.*name="http-nio-8080".*\}\s+([-+0-9.eE]+)$') {
            $current.TomcatMax = [double]$Matches[1]
        } elseif ($line -match '^hikaricp_connections_pending(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.HikariPending = [double]$Matches[1]
        } elseif ($line -match '^hikaricp_connections_acquire_seconds_max(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.HikariAcquireMaxSeconds = [double]$Matches[1]
        } elseif ($line -match '^hikaricp_connections_timeout_total(?:\{.*\})?\s+([-+0-9.eE]+)$') {
            $current.HikariTimeouts = [double]$Matches[1]
        }
    }
    Complete-Snapshot $current
    if ($null -ne $current) {
        $snapshots.Add([pscustomobject]$current)
    }
    return $snapshots.ToArray()
}

function Read-K6Events([string]$path) {
    $events = @{}
    if (-not (Test-Path -LiteralPath $path)) {
        return $events
    }
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -notlike '*"metric":"loadtest_event"*') {
            continue
        }
        try {
            $point = $line | ConvertFrom-Json
            if ([double]$point.data.value -le 0) {
                continue
            }
            $eventName = [string]$point.data.tags.event
            if ($eventName -and -not $events.ContainsKey($eventName)) {
                $events[$eventName] = [DateTimeOffset]::Parse(
                    [string]$point.data.time
                )
            }
        } catch {
            continue
        }
    }
    return $events
}

function Maximum($items, [string]$property) {
    if ($items.Count -eq 0) { return 0.0 }
    return [double](
        $items | Measure-Object -Property $property -Maximum
    ).Maximum
}

function Minimum($items, [string]$property) {
    if ($items.Count -eq 0) { return 0.0 }
    return [double](
        $items | Measure-Object -Property $property -Minimum
    ).Minimum
}

function Counter-Delta($items, [string]$property) {
    if ($items.Count -lt 2) { return 0.0 }
    return [math]::Max(
        0.0,
        [double]$items[-1].$property - [double]$items[0].$property
    )
}

function Event-Offset($snapshot, [DateTimeOffset]$burstAt) {
    if ($null -eq $snapshot) { return 0.0 }
    return [math]::Round(($snapshot.Timestamp - $burstAt).TotalSeconds, 1)
}

$resolvedStageDirectory = [System.IO.Path]::GetFullPath($StageDirectory)
$summary = Get-Content -LiteralPath (
    Join-Path $resolvedStageDirectory "summary.json"
) -Raw | ConvertFrom-Json
$evaluation = Get-Content -LiteralPath (
    Join-Path $resolvedStageDirectory "stage-evaluation.json"
) -Raw | ConvertFrom-Json
$events = Read-K6Events (
    Join-Path $resolvedStageDirectory "k6-raw.json"
)
$snapshots = @(Read-Snapshots (
    Join-Path $resolvedStageDirectory "server-metrics/actuator-prometheus.txt"
))

$stageStart = $events["stage_start"]
$burstAt = $events["answer_burst_start"]
$measurementEnd = $events["stage_measurement_end"]
if ($null -eq $stageStart -and $snapshots.Count -gt 0) {
    $stageStart = $snapshots[0].Timestamp
}
if ($null -eq $burstAt) {
    $burstAt = $stageStart.AddSeconds(14)
}
if ($null -eq $measurementEnd -and $snapshots.Count -gt 0) {
    $measurementEnd = $snapshots[-1].Timestamp
}

$measurementSnapshots = @($snapshots | Where-Object {
    $_.Timestamp -ge $stageStart -and $_.Timestamp -le $measurementEnd
})
if ($measurementSnapshots.Count -eq 0) {
    $measurementSnapshots = $snapshots
}

$cpuPeak = $measurementSnapshots |
    Sort-Object ProcessCpuPercent -Descending |
    Select-Object -First 1
$queuePeak = $measurementSnapshots |
    Sort-Object QueueSize -Descending |
    Select-Object -First 1
$requestMinimum = $measurementSnapshots |
    Sort-Object AvailableRequests |
    Select-Object -First 1

$logicalRequests = [double]$summary.concurrency
$attempts = Metric-Value $summary "answer_attempts" "count"
$success = Metric-Value $summary "answer_success" "count"
$retryAttempts = Metric-Value $summary "answer_retry_attempts" "count"
$retryRecovered = Metric-Value $summary "answer_retry_recovered" "count"
$requestCapacity = Maximum $measurementSnapshots "RequestCapacity"
$tokenCapacity = Maximum $measurementSnapshots "TokenCapacity"
$queueCapacity = Maximum $measurementSnapshots "QueueCapacity"
if ($queueCapacity -le 0) {
    $queueCapacity = [double]$summary.assessmentQueueCapacity
}

$summaryValues = [ordered]@{
    logical_requests = $logicalRequests
    attempts = $attempts
    attempts_per_request = if ($logicalRequests -gt 0) {
        $attempts / $logicalRequests
    } else { 0.0 }
    success_rate_percent = if ($logicalRequests -gt 0) {
        100 * $success / $logicalRequests
    } else { 0.0 }
    retry_attempts = $retryAttempts
    retry_recovered = $retryRecovered
    final_overload = Metric-Value $summary `
        "answer_expected_overload" "count"
    final_success_p95_ms = Metric-Value $summary `
        "answer_final_success_duration" "p(95)"
    loaded_probe_p95_ms = Metric-Value $summary `
        "probe_duration" "p(95)"
    cpu_peak_percent = [double]$cpuPeak.ProcessCpuPercent
    cpu_peak_offset_seconds = Event-Offset $cpuPeak $burstAt
    cpu_peak_request_available = [double]$cpuPeak.AvailableRequests
    cpu_peak_token_available = [double]$cpuPeak.AvailableTokens
    cpu_peak_queue_size = [double]$cpuPeak.QueueSize
    cpu_peak_tomcat_busy = [double]$cpuPeak.TomcatBusy
    cpu_peak_hikari_pending = [double]$cpuPeak.HikariPending
    heap_peak_percent = Maximum $measurementSnapshots "HeapPercent"
    tomcat_busy_peak = Maximum $measurementSnapshots "TomcatBusy"
    tomcat_capacity = Maximum $measurementSnapshots "TomcatMax"
    hikari_pending_peak = Maximum $measurementSnapshots "HikariPending"
    hikari_acquire_max_seconds = Maximum $measurementSnapshots `
        "HikariAcquireMaxSeconds"
    hikari_timeouts = Counter-Delta $measurementSnapshots "HikariTimeouts"
    gc_pause_seconds = Counter-Delta $measurementSnapshots "GcPauseSeconds"
    queue_peak = Maximum $measurementSnapshots "QueueSize"
    queue_capacity = $queueCapacity
    queue_wait_max_seconds = Maximum $measurementSnapshots `
        "QueueWaitMaxSeconds"
    request_available_min = Minimum $measurementSnapshots `
        "AvailableRequests"
    request_capacity = $requestCapacity
    token_available_min = Minimum $measurementSnapshots "AvailableTokens"
    token_capacity = $tokenCapacity
    rate_limit_rejected = Counter-Delta $measurementSnapshots `
        "RateLimitRejected"
    recovery_seconds = [double]$evaluation.recoveryElapsedSeconds
}

$eventOffsets = [ordered]@{}
foreach ($name in @(
    "stage_start",
    "loaded_probe_start",
    "answer_burst_start",
    "stage_measurement_end"
)) {
    if ($events.ContainsKey($name)) {
        $eventOffsets[$name] = [math]::Round(
            ($events[$name] - $burstAt).TotalSeconds,
            1
        )
    }
}
$eventOffsets["request_bucket_min"] = Event-Offset $requestMinimum $burstAt
$eventOffsets["queue_peak"] = Event-Offset $queuePeak $burstAt
$eventOffsets["cpu_peak"] = Event-Offset $cpuPeak $burstAt
$eventOffsets["recovery_complete"] = [math]::Round(
    ($measurementEnd - $burstAt).TotalSeconds `
        + [double]$evaluation.recoveryElapsedSeconds,
    1
)

[pscustomobject]@{
    testId = [string]$evaluation.testId
    scenario = [string]$evaluation.scenario
    stage = [int]$evaluation.stage
    generatedAt = [DateTimeOffset]::UtcNow.ToString("O")
    summary = [pscustomobject]$summaryValues
    events = [pscustomobject]$eventOffsets
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputPath `
    -Encoding utf8
