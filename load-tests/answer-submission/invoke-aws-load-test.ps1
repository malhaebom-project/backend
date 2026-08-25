[CmdletBinding()]
param(
    [string]$ConfigPath = ".private/load-tests/aws-load-test.psd1",
    [switch]$ConfirmLiveOpenAiCost,
    [switch]$SkipComposeLifecycle
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "invoke-aws-load-test.ps1 requires PowerShell 7 or later."
}
if (-not $ConfirmLiveOpenAiCost) {
    throw "Live OpenAI load testing incurs cost. Re-run with " `
        + "-ConfirmLiveOpenAiCost to continue."
}

$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../..")
)
$resolvedConfigPath = if ([System.IO.Path]::IsPathRooted($ConfigPath)) {
    [System.IO.Path]::GetFullPath($ConfigPath)
} else {
    [System.IO.Path]::GetFullPath(
        (Join-Path $repositoryRoot $ConfigPath)
    )
}
if (-not (Test-Path -LiteralPath $resolvedConfigPath -PathType Leaf)) {
    throw "AWS load-test config not found: $resolvedConfigPath. Copy " `
        + "load-tests/answer-submission/aws-load-test.example.psd1 to " `
        + ".private/load-tests/aws-load-test.psd1 and fill in local values."
}

$config = Import-PowerShellDataFile -LiteralPath $resolvedConfigPath

function Required-Config([string]$name) {
    if (-not $config.ContainsKey($name)) {
        throw "Missing required config value: $name"
    }
    $value = $config[$name]
    if ($value -is [string] -and [string]::IsNullOrWhiteSpace($value)) {
        throw "Config value must not be blank: $name"
    }
    return $value
}

function Config-OrDefault([string]$name, $defaultValue) {
    if ($config.ContainsKey($name)) {
        return $config[$name]
    }
    return $defaultValue
}

function Resolve-Scenarios {
    $rawScenarios = if ($config.ContainsKey("Scenarios")) {
        @($config["Scenarios"])
    } else {
        @(@{
            Name = "default"
            Stages = Config-OrDefault "Stages" @(10, 100, 200, 300)
            ClientMaxRetries = Config-OrDefault "ClientMaxRetries" 0
        })
    }
    if ($rawScenarios.Count -eq 0) {
        throw "Scenarios must contain at least one scenario."
    }

    $names = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    $resolved = foreach ($scenario in $rawScenarios) {
        if ($scenario -isnot [System.Collections.IDictionary]) {
            throw "Each Scenarios entry must be a hashtable."
        }
        foreach ($requiredName in @("Name", "Stages", "ClientMaxRetries")) {
            if (-not $scenario.Contains($requiredName)) {
                throw "Scenario is missing required value: $requiredName"
            }
        }
        $name = [string]$scenario["Name"]
        if ($name -notmatch '^[a-z0-9][a-z0-9-]{0,28}$') {
            throw "Scenario Name must use 1-29 lowercase letters, numbers, " `
                + "or hyphens: $name"
        }
        if (-not $names.Add($name)) {
            throw "Scenario names must be unique: $name"
        }
        $scenarioStages = [int[]]$scenario["Stages"]
        if ($scenarioStages.Count -eq 0 `
            -or @($scenarioStages | Where-Object {
                $_ -lt 1 -or $_ -gt 10000
            }).Count -gt 0) {
            throw "Scenario $name Stages must be between 1 and 10000."
        }
        if (@($scenarioStages | Select-Object -Unique).Count `
            -ne $scenarioStages.Count) {
            throw "Scenario $name Stages must not contain duplicates."
        }
        $scenarioRetries = [int]$scenario["ClientMaxRetries"]
        if ($scenarioRetries -lt 0 -or $scenarioRetries -gt 2) {
            throw "Scenario $name ClientMaxRetries must be between 0 and 2."
        }
        [pscustomobject]@{
            Name = $name
            Stages = $scenarioStages
            ClientMaxRetries = $scenarioRetries
            LogicalSubmissions = ($scenarioStages | Measure-Object -Sum).Sum
            MaximumHttpAttempts =
                ($scenarioStages | Measure-Object -Sum).Sum `
                * ($scenarioRetries + 1)
        }
    }
    return @($resolved)
}

function Assert-Command([string]$name) {
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "Required command not found: $name"
    }
    return $command.Source
}

function Assert-TcpPort([string]$name, [int]$port) {
    if ($port -lt 1 -or $port -gt 65535) {
        throw "$name must be between 1 and 65535."
    }
}

function Wait-HttpEndpoint(
    [string]$name,
    [string]$url,
    [int]$timeoutSeconds
) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($timeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
        } catch {
            # The service can refuse connections while the container starts.
        }
        Start-Sleep -Seconds 1
    }
    throw "$name did not become ready within $timeoutSeconds seconds: $url"
}

function Wait-PrometheusBackendTarget(
    [int]$prometheusPort,
    [int]$timeoutSeconds
) {
    $query = "up%7Bjob%3D%22malhaebom-backend%22%7D"
    $url = "http://127.0.0.1:$prometheusPort/api/v1/query?query=$query"
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($timeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $url -TimeoutSec 3
            $results = @($response.data.result)
            if ($results.Count -gt 0 `
                -and [double]$results[0].value[1] -eq 1.0) {
                return
            }
        } catch {
            # The target can be absent during the first scrape interval.
        }
        Start-Sleep -Seconds 1
    }
    throw "Prometheus did not report the backend target as up within " `
        + "$timeoutSeconds seconds."
}

function Invoke-RemoteCommand(
    [string]$sshExecutable,
    [string[]]$baseArguments,
    [string]$hostName,
    [string]$command
) {
    & $sshExecutable @baseArguments $hostName $command
    if ($LASTEXITCODE -ne 0) {
        throw "Remote command failed with exit code $LASTEXITCODE."
    }
}

function Invoke-FixtureTask(
    [string]$gradleWrapper,
    [string]$action,
    [string]$manifestPath,
    [string]$runId = "",
    [int[]]$stages = @()
) {
    $arguments = @(
        "loadTestFixtures"
        "--no-daemon"
        "-PloadTestAction=$action"
        "-PloadTestManifest=$manifestPath"
    )
    if ($runId) {
        $arguments += "-PloadTestRunId=$runId"
    }
    if ($action -eq "seed") {
        if ($stages.Count -eq 0) {
            throw "Fixture seed requires at least one stage."
        }
        $arguments += "-PloadTestStages=$($stages -join ',')"
    }
    Push-Location $repositoryRoot
    try {
        if ($IsWindows) {
            & $gradleWrapper @arguments
        } else {
            & bash $gradleWrapper @arguments
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Fixture $action failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

$baseUrl = [string](Required-Config "BaseUrl")
$sshHost = [string](Required-Config "SshHost")
$sshIdentityFile = [System.IO.Path]::GetFullPath(
    [string](Required-Config "SshIdentityFile")
)
$remoteProjectDirectory = [string](
    Required-Config "RemoteProjectDirectory"
)
if ($sshHost -notmatch '^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+$') {
    throw "SshHost must use the form user@host."
}
if ($remoteProjectDirectory -notmatch '^/[A-Za-z0-9._/-]+$') {
    throw "RemoteProjectDirectory must be an absolute Linux path without spaces."
}
if (-not (Test-Path -LiteralPath $sshIdentityFile -PathType Leaf)) {
    throw "SSH identity file not found: $sshIdentityFile"
}
if ($IsMacOS) {
    $sshIdentityMode = (& stat -f "%OLp" -- $sshIdentityFile).Trim()
    if ($LASTEXITCODE -ne 0 -or $sshIdentityMode -notmatch '^[0-7]{3,4}$') {
        throw "Could not inspect SSH identity file permissions: " `
            + $sshIdentityFile
    }
    $sshIdentityModeValue = [Convert]::ToInt32($sshIdentityMode, 8)
    if (($sshIdentityModeValue -band 0x3F) -ne 0) {
        throw "SSH identity file permissions are too open. Run: chmod 600 " `
            + "'$sshIdentityFile'"
    }
}
$baseUri = [uri]$baseUrl
if (-not $baseUri.IsAbsoluteUri -or $baseUri.Scheme -notin @("http", "https")) {
    throw "BaseUrl must be an absolute HTTP(S) URL."
}

$resultRootSetting = [string](Config-OrDefault `
    "ResultRoot" "load-tests/results/aws")
$resultRootBase = if ([System.IO.Path]::IsPathRooted($resultRootSetting)) {
    [System.IO.Path]::GetFullPath($resultRootSetting)
} else {
    [System.IO.Path]::GetFullPath(
        (Join-Path $repositoryRoot $resultRootSetting)
    )
}
$dockerContainer = [string](Config-OrDefault `
    "DockerContainer" "backend-was-1")
$backendImage = [string](Config-OrDefault `
    "BackendImage" "malhaebom/backend:latest")
if ($backendImage -notmatch '^[A-Za-z0-9._/:@-]+$') {
    throw "BackendImage contains unsupported characters: $backendImage"
}
$scenarios = Resolve-Scenarios
$assessmentQueueCapacity = [int](Config-OrDefault `
    "AssessmentQueueCapacity" 64)
$assessmentMaxQueueWaitSeconds = [int](Config-OrDefault `
    "AssessmentMaxQueueWaitSeconds" 10)
$recoveryTimeoutSeconds = [int](Config-OrDefault `
    "RecoveryTimeoutSeconds" 300)
$localManagementPort = [int](Config-OrDefault "LocalManagementPort" 19090)
$localPrometheusPort = [int](Config-OrDefault "LocalPrometheusPort" 19091)
$localGrafanaPort = [int](Config-OrDefault "LocalGrafanaPort" 13000)
$readinessTimeoutSeconds = [int](Config-OrDefault `
    "ReadinessTimeoutSeconds" 120)

if ($assessmentQueueCapacity -lt 0) {
    throw "AssessmentQueueCapacity must be greater than or equal to 0."
}
if ($assessmentMaxQueueWaitSeconds -lt 1) {
    throw "AssessmentMaxQueueWaitSeconds must be greater than 0."
}
if ($recoveryTimeoutSeconds -lt 1 -or $readinessTimeoutSeconds -lt 1) {
    throw "Recovery and readiness timeouts must be greater than 0."
}
Assert-TcpPort "LocalManagementPort" $localManagementPort
Assert-TcpPort "LocalPrometheusPort" $localPrometheusPort
Assert-TcpPort "LocalGrafanaPort" $localGrafanaPort
$localPorts = @(
    $localManagementPort,
    $localPrometheusPort,
    $localGrafanaPort
)
if (@($localPorts | Select-Object -Unique).Count -ne 3) {
    throw "Local management, Prometheus and Grafana ports must be distinct."
}

$sshExecutable = Assert-Command "ssh"
[void](Assert-Command "k6")
[void](Assert-Command "docker")
if (-not $IsWindows) {
    [void](Assert-Command "bash")
}
$gradleWrapper = if ($IsWindows) {
    Join-Path $repositoryRoot "gradlew.bat"
} else {
    Join-Path $repositoryRoot "gradlew"
}
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle Wrapper not found: $gradleWrapper"
}

$previousJavaHome = $env:JAVA_HOME
$javaHome = [string](Config-OrDefault "JavaHome" "")
if ($javaHome) {
    $resolvedJavaHome = [System.IO.Path]::GetFullPath($javaHome)
    if (-not (Test-Path -LiteralPath $resolvedJavaHome -PathType Container)) {
        throw "JavaHome not found: $resolvedJavaHome"
    }
    $env:JAVA_HOME = $resolvedJavaHome
}

$sshBaseArguments = @(
    "-o", "BatchMode=yes"
    "-o", "StrictHostKeyChecking=accept-new"
    "-i", $sshIdentityFile
)
$remoteLoadtestScript = "load-tests/answer-submission/" +
    "start-loadtest-compose.sh"
$remoteRestoreScript = "load-tests/answer-submission/" +
    "restore-prod-compose.sh"
$remoteLoadtestCommand = (
    "cd $remoteProjectDirectory && " +
    "LOADTEST_BACKEND_IMAGE=$backendImage " +
    "LOADTEST_ASSESSMENT_QUEUE_CAPACITY=$assessmentQueueCapacity " +
    "LOADTEST_ASSESSMENT_MAX_QUEUE_WAIT=" +
    "${assessmentMaxQueueWaitSeconds}s " +
    "bash $remoteLoadtestScript"
)
$remoteRestoreCommand = (
    "cd $remoteProjectDirectory && " +
    "bash $remoteRestoreScript"
)
$batchRunId = "aws-" + [DateTimeOffset]::UtcNow.ToString("yyyyMMddTHHmmssZ")
$resultRoot = Join-Path $resultRootBase $batchRunId
$runStagesScript = Join-Path $PSScriptRoot "run-stages.ps1"
$startLocalObservabilityScript = Join-Path $PSScriptRoot `
    "start-local-observability.ps1"
$logicalSubmissions = ($scenarios | Measure-Object `
    -Property LogicalSubmissions -Sum).Sum
$maximumHttpAttempts = ($scenarios | Measure-Object `
    -Property MaximumHttpAttempts -Sum).Sum

New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
$runPlanPath = Join-Path $resultRoot "run-plan.json"
[pscustomobject]@{
    testId = $batchRunId
    generatedAt = [DateTimeOffset]::UtcNow.ToString("O")
    backendImage = $backendImage
    assessmentQueueCapacity = $assessmentQueueCapacity
    assessmentMaxQueueWaitSeconds = $assessmentMaxQueueWaitSeconds
    logicalSubmissions = $logicalSubmissions
    maximumHttpAttempts = $maximumHttpAttempts
    scenarios = $scenarios
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $runPlanPath `
    -Encoding utf8
Write-Warning (
    "Starting a live OpenAI load test with up to " +
    "$maximumHttpAttempts answer HTTP attempts."
)
Write-Host "Test ID: $batchRunId"
Write-Host "Results: $resultRoot"
Write-Host "Scenarios:"
foreach ($scenario in $scenarios) {
    Write-Host (
        "  - $($scenario.Name): stages=$($scenario.Stages -join ',') " +
        "retries=$($scenario.ClientMaxRetries) " +
        "max-http-attempts=$($scenario.MaximumHttpAttempts)"
    )
}

$previousProfile = $env:SPRING_PROFILES_ACTIVE
$previousOpenAiRetries = $env:SPRING_AI_OPENAI_MAX_RETRIES
$primaryError = $null
$cleanupErrors = [System.Collections.Generic.List[string]]::new()
$pendingManifests = [System.Collections.Generic.List[string]]::new()
$scenarioFailures = [System.Collections.Generic.List[string]]::new()

try {
    $env:SPRING_PROFILES_ACTIVE = "prod"
    $env:SPRING_AI_OPENAI_MAX_RETRIES = "0"

    if ($SkipComposeLifecycle) {
        Write-Host "Using the existing remote load-test WAS."
    } else {
        Write-Host "Starting the remote load-test WAS..."
        Invoke-RemoteCommand $sshExecutable $sshBaseArguments $sshHost `
            $remoteLoadtestCommand
    }

    Write-Host "Starting the local management tunnel, Prometheus and Grafana..."
    & $startLocalObservabilityScript `
        -SshHost $sshHost `
        -SshIdentityFile $sshIdentityFile `
        -ManagementPort $localManagementPort `
        -PrometheusPort $localPrometheusPort `
        -GrafanaPort $localGrafanaPort
    Wait-HttpEndpoint "Backend management endpoint" `
        "http://127.0.0.1:$localManagementPort/actuator/health" `
        $readinessTimeoutSeconds
    Wait-HttpEndpoint "Prometheus" `
        "http://127.0.0.1:$localPrometheusPort/-/ready" `
        $readinessTimeoutSeconds
    Wait-HttpEndpoint "Grafana" `
        "http://127.0.0.1:$localGrafanaPort/api/health" `
        $readinessTimeoutSeconds
    Wait-PrometheusBackendTarget $localPrometheusPort `
        $readinessTimeoutSeconds

    Write-Host "Grafana: http://127.0.0.1:$localGrafanaPort"
    foreach ($scenario in $scenarios) {
        $scenarioName = $scenario.Name
        $scenarioRunId = "$batchRunId-$scenarioName"
        $scenarioRoot = Join-Path $resultRoot $scenarioName
        $manifestPath = Join-Path $scenarioRoot "fixture-manifest.json"
        New-Item -ItemType Directory -Path $scenarioRoot -Force | Out-Null

        Write-Host "[$scenarioName] Seeding load-test fixtures..."
        try {
            Invoke-FixtureTask $gradleWrapper "seed" $manifestPath `
                $scenarioRunId $scenario.Stages
            if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
                throw "Scenario $scenarioName did not create a manifest."
            }
            $pendingManifests.Add($manifestPath)

            Write-Host "[$scenarioName] Running staged k6 load test..."
            $runStageParameters = @{
                BaseUrl = $baseUrl
                ManagementUrl = "http://127.0.0.1:$localManagementPort"
                PrometheusRemoteWriteUrl =
                    "http://127.0.0.1:$localPrometheusPort/api/v1/write"
                Manifest = $manifestPath
                ResultRoot = $scenarioRoot
                Stages = $scenario.Stages
                AssessmentQueueCapacity = $assessmentQueueCapacity
                AssessmentMaxQueueWaitSeconds =
                    $assessmentMaxQueueWaitSeconds
                ClientMaxRetries = $scenario.ClientMaxRetries
                RecoveryTimeoutSeconds = $recoveryTimeoutSeconds
                TestId = $batchRunId
                Scenario = $scenarioName
                DockerContainer = $dockerContainer
                SshHost = $sshHost
                SshIdentityFile = $sshIdentityFile
            }
            try {
                & $runStagesScript @runStageParameters
            } catch {
                $scenarioFailures.Add(
                    "$scenarioName`: $($_.Exception.Message)"
                )
                Write-Warning "Scenario $scenarioName failed; continuing."
            }
        } finally {
            if (Test-Path -LiteralPath $manifestPath -PathType Leaf) {
                Write-Host "[$scenarioName] Cleaning up fixtures..."
                Invoke-FixtureTask $gradleWrapper "cleanup" $manifestPath
                [void]$pendingManifests.Remove($manifestPath)
            }
        }
    }
    if ($scenarioFailures.Count -gt 0) {
        throw "Load-test scenario failures: " +
            ($scenarioFailures -join "; ")
    }
} catch {
    $primaryError = $_
} finally {
    foreach ($pendingManifest in @($pendingManifests)) {
        try {
            Write-Host "Cleaning up pending fixtures: $pendingManifest"
            Invoke-FixtureTask $gradleWrapper "cleanup" $pendingManifest
            [void]$pendingManifests.Remove($pendingManifest)
        } catch {
            $cleanupErrors.Add(
                "Fixture cleanup failed for $pendingManifest`: " +
                $_.Exception.Message
            )
        }
    }
    if (-not $SkipComposeLifecycle) {
        try {
            Write-Host "Restoring the remote production Compose stack..."
            Invoke-RemoteCommand $sshExecutable $sshBaseArguments $sshHost `
                $remoteRestoreCommand
        } catch {
            $cleanupErrors.Add(
                "Remote Compose restore failed: $($_.Exception.Message)"
            )
        }
    } else {
        Write-Host "Leaving the remote load-test WAS unchanged."
    }
    Write-Host "Leaving local Prometheus and Grafana running for result review."
    $env:SPRING_PROFILES_ACTIVE = $previousProfile
    $env:SPRING_AI_OPENAI_MAX_RETRIES = $previousOpenAiRetries
    $env:JAVA_HOME = $previousJavaHome
}

foreach ($cleanupError in $cleanupErrors) {
    Write-Warning $cleanupError
}
if ($null -ne $primaryError) {
    throw $primaryError
}
if ($cleanupErrors.Count -gt 0) {
    throw "The load test finished, but one or more cleanup steps failed."
}

Write-Host "AWS load test completed successfully."
Write-Host "Results: $resultRoot"
