[CmdletBinding()]
param(
    [string]$ConfigPath = ".private/load-tests/aws-load-test.psd1",
    [switch]$ConfirmLiveOpenAiCost
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

function Start-SshTunnel(
    [string]$sshExecutable,
    [string[]]$baseArguments,
    [string]$hostName,
    [int]$managementPort,
    [int]$prometheusPort,
    [int]$grafanaPort
) {
    $arguments = @(
        $baseArguments
        "-N"
        "-o", "ExitOnForwardFailure=yes"
        "-o", "ServerAliveInterval=30"
        "-L", "${managementPort}:127.0.0.1:9090"
        "-L", "${prometheusPort}:127.0.0.1:9091"
        "-L", "${grafanaPort}:127.0.0.1:3000"
        $hostName
    )
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $sshExecutable
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    foreach ($argument in $arguments) {
        [void]$startInfo.ArgumentList.Add([string]$argument)
    }
    $process = [System.Diagnostics.Process]::Start($startInfo)
    if ($null -eq $process) {
        throw "Failed to start the SSH tunnel."
    }
    Start-Sleep -Seconds 1
    if ($process.HasExited) {
        throw "SSH tunnel exited immediately with code $($process.ExitCode)."
    }
    return $process
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
    [string]$runId = ""
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
$stages = [int[]](Config-OrDefault "Stages" @(10, 100, 200, 300))
$assessmentLimit = [int](Config-OrDefault "AssessmentLimit" 32)
$assessmentQueueCapacity = [int](Config-OrDefault `
    "AssessmentQueueCapacity" 64)
$assessmentMaxQueueWaitSeconds = [int](Config-OrDefault `
    "AssessmentMaxQueueWaitSeconds" 10)
$clientMaxRetries = [int](Config-OrDefault "ClientMaxRetries" 0)
$recoveryTimeoutSeconds = [int](Config-OrDefault `
    "RecoveryTimeoutSeconds" 300)
$localManagementPort = [int](Config-OrDefault "LocalManagementPort" 19090)
$localPrometheusPort = [int](Config-OrDefault "LocalPrometheusPort" 19091)
$localGrafanaPort = [int](Config-OrDefault "LocalGrafanaPort" 13000)
$readinessTimeoutSeconds = [int](Config-OrDefault `
    "ReadinessTimeoutSeconds" 120)

if ($stages.Count -eq 0 -or @($stages | Where-Object { $_ -lt 1 }).Count -gt 0) {
    throw "Stages must contain positive concurrency values."
}
if ($clientMaxRetries -lt 0 -or $clientMaxRetries -gt 2) {
    throw "ClientMaxRetries must be between 0 and 2."
}
if ($assessmentLimit -lt 1) {
    throw "AssessmentLimit must be greater than 0."
}
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
$localTunnelPorts = @(
    $localManagementPort,
    $localPrometheusPort,
    $localGrafanaPort
)
if (@($localTunnelPorts | Select-Object -Unique).Count -ne 3) {
    throw "Local tunnel ports must be distinct."
}

$sshExecutable = Assert-Command "ssh"
[void](Assert-Command "k6")
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
    "LOADTEST_ASSESSMENT_LIMIT=$assessmentLimit " +
    "LOADTEST_ASSESSMENT_QUEUE_CAPACITY=$assessmentQueueCapacity " +
    "LOADTEST_ASSESSMENT_MAX_QUEUE_WAIT=" +
    "${assessmentMaxQueueWaitSeconds}s " +
    "bash $remoteLoadtestScript"
)
$remoteRestoreCommand = (
    "cd $remoteProjectDirectory && " +
    "bash $remoteRestoreScript"
)
$runId = "aws-" + [DateTimeOffset]::UtcNow.ToString("yyyyMMddTHHmmssZ")
$resultRoot = Join-Path $resultRootBase $runId
$manifestPath = Join-Path $resultRoot "fixture-manifest.json"
$runStagesScript = Join-Path $PSScriptRoot "run-stages.ps1"
$maximumHttpAttempts = (
    ($stages | Measure-Object -Sum).Sum * ($clientMaxRetries + 1)
)

New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
Write-Warning (
    "Starting a live OpenAI load test with up to " +
    "$maximumHttpAttempts answer HTTP attempts."
)
Write-Host "Run ID: $runId"
Write-Host "Results: $resultRoot"

$previousProfile = $env:SPRING_PROFILES_ACTIVE
$previousOpenAiRetries = $env:SPRING_AI_OPENAI_MAX_RETRIES
$tunnelProcess = $null
$fixtureSeedAttempted = $false
$primaryError = $null
$cleanupErrors = [System.Collections.Generic.List[string]]::new()

try {
    $env:SPRING_PROFILES_ACTIVE = "prod"
    $env:SPRING_AI_OPENAI_MAX_RETRIES = "0"

    Write-Host "Starting the remote load-test observability stack..."
    Invoke-RemoteCommand $sshExecutable $sshBaseArguments $sshHost `
        $remoteLoadtestCommand

    Write-Host "Starting SSH tunnels..."
    $tunnelProcess = Start-SshTunnel $sshExecutable $sshBaseArguments `
        $sshHost $localManagementPort $localPrometheusPort $localGrafanaPort

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
    Write-Host "Seeding load-test fixtures..."
    $fixtureSeedAttempted = $true
    Invoke-FixtureTask $gradleWrapper "seed" $manifestPath $runId

    Write-Host "Running staged k6 load test..."
    $runStageParameters = @{
        BaseUrl = $baseUrl
        ManagementUrl = "http://127.0.0.1:$localManagementPort"
        PrometheusRemoteWriteUrl =
            "http://127.0.0.1:$localPrometheusPort/api/v1/write"
        Manifest = $manifestPath
        ResultRoot = $resultRoot
        Stages = $stages
        AssessmentLimit = $assessmentLimit
        AssessmentQueueCapacity = $assessmentQueueCapacity
        AssessmentMaxQueueWaitSeconds = $assessmentMaxQueueWaitSeconds
        ClientMaxRetries = $clientMaxRetries
        RecoveryTimeoutSeconds = $recoveryTimeoutSeconds
        DockerContainer = $dockerContainer
        SshHost = $sshHost
        SshIdentityFile = $sshIdentityFile
    }
    & $runStagesScript @runStageParameters
} catch {
    $primaryError = $_
} finally {
    if ($fixtureSeedAttempted `
        -and (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        try {
            Write-Host "Cleaning up load-test fixtures..."
            Invoke-FixtureTask $gradleWrapper "cleanup" $manifestPath
        } catch {
            $cleanupErrors.Add("Fixture cleanup failed: $($_.Exception.Message)")
        }
    }
    if ($null -ne $tunnelProcess -and -not $tunnelProcess.HasExited) {
        try {
            $tunnelProcess.Kill($true)
            $tunnelProcess.WaitForExit(5000)
        } catch {
            $cleanupErrors.Add("SSH tunnel cleanup failed: $($_.Exception.Message)")
        }
    }
    try {
        Write-Host "Restoring the remote production Compose stack..."
        Invoke-RemoteCommand $sshExecutable $sshBaseArguments $sshHost `
            $remoteRestoreCommand
    } catch {
        $cleanupErrors.Add("Remote Compose restore failed: $($_.Exception.Message)")
    }
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
