#requires -Version 5.1
<#
.SYNOPSIS
    Runs the backend unit tests (no secrets or MongoDB required for the X1-X4 suite).
.EXAMPLE
    .\scripts\backend-test.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backend  = Join-Path $repoRoot 'backend'

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven (mvn) is not on PATH. Install with: winget install Apache.Maven"
}

Set-Location $backend
& mvn -q test
if ($LASTEXITCODE -ne 0) { throw "Tests failed." }
Write-Host "Tests passed." -ForegroundColor Green
