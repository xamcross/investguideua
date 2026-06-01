#requires -Version 5.1
<#
.SYNOPSIS
    Builds and starts the full local stack (Mongo + backend + SPA) via Docker Compose.
.DESCRIPTION
    Ensures a .env exists (runs setup-env.ps1 if not), then `docker compose up --build`.
    Requires Docker Desktop (Linux containers / WSL2 backend).
.EXAMPLE
    .\scripts\dev-up.ps1
    .\scripts\dev-up.ps1 -Detached
#>
[CmdletBinding()]
param(
    [switch]$Detached
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is not on PATH. Install Docker Desktop and ensure it is running."
}

if (-not (Test-Path (Join-Path $repoRoot '.env'))) {
    Write-Host "No .env found - generating one..." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'setup-env.ps1')
}

$composeArgs = @('compose', 'up', '--build')
if ($Detached) { $composeArgs += '-d' }

Write-Host "Starting stack: docker $($composeArgs -join ' ')" -ForegroundColor Green
& docker @composeArgs

if ($Detached) {
    Write-Host ""
    Write-Host "SPA:     http://localhost:8081" -ForegroundColor Cyan
    Write-Host "API:     http://localhost:8080/api/v1/ping" -ForegroundColor Cyan
    Write-Host "Health:  http://localhost:8080/actuator/health" -ForegroundColor Cyan
    Write-Host "Stop with: docker compose down"
}
