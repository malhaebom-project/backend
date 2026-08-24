[CmdletBinding()]
param(
    [string]$ConfigPath = ".private/load-tests/aws-load-test.psd1",
    [switch]$ConfirmLiveOpenAiCost
)

$ErrorActionPreference = "Stop"

$orchestrator = Join-Path $PSScriptRoot "invoke-aws-load-test.ps1"
& $orchestrator `
    -ConfigPath $ConfigPath `
    -ConfirmLiveOpenAiCost:$ConfirmLiveOpenAiCost `
    -SkipComposeLifecycle
