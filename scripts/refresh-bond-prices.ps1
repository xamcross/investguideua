#requires -Version 5.1
<#
.SYNOPSIS
    Runs the bond price scraper against a backend (feature 009).
.DESCRIPTION
    Drives the same Node + Playwright scraper used in CI, but pointed at a local (or any) backend.
    The shared ingest secret is read from the BOND_INGEST_SECRET environment variable (or .env);
    pass -Secret only to override. The secret is handed to the child Node process via the
    environment, never on the command line, so it does not land in PowerShell history or the
    process list.

    First run downloads Chromium via Playwright (npx playwright install chromium).
    Requires Node.js 20+ on PATH.
.PARAMETER BackendBaseUrl
    Backend base URL. Default http://localhost:8080 (production: https://api.investguideua.com).
.PARAMETER Secret
    Override the ingest secret. If omitted, BOND_INGEST_SECRET from the environment/.env is used.
.PARAMETER DryRun
    Scrape and validate without POSTing (prints a sample record).
.EXAMPLE
    .\scripts\refresh-bond-prices.ps1
.EXAMPLE
    .\scripts\refresh-bond-prices.ps1 -BackendBaseUrl "http://localhost:8080" -DryRun
#>
[CmdletBinding()]
param(
    [string]$BackendBaseUrl = 'http://localhost:8080',
    [string]$Secret,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$scraperDir = Join-Path $repoRoot 'scraper'

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js is not on PATH. Install Node.js 20+ and retry."
}

# Resolve the secret: explicit -Secret wins, else env, else try the repo .env file.
$resolvedSecret = $Secret
if ([string]::IsNullOrWhiteSpace($resolvedSecret)) {
    $resolvedSecret = $env:BOND_INGEST_SECRET
}
if ([string]::IsNullOrWhiteSpace($resolvedSecret)) {
    $envFile = Join-Path $repoRoot '.env'
    if (Test-Path $envFile) {
        $line = Select-String -Path $envFile -Pattern '^\s*BOND_INGEST_SECRET\s*=' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($line) {
            $resolvedSecret = ($line.Line -replace '^\s*BOND_INGEST_SECRET\s*=', '').Trim().Trim('"')
        }
    }
}
if ([string]::IsNullOrWhiteSpace($resolvedSecret) -and -not $DryRun) {
    throw "BOND_INGEST_SECRET is not set. Set it in the environment or .env, or pass -Secret (or use -DryRun)."
}

# Ensure scraper dependencies are present (npm ci if a lockfile exists, else npm install).
$npm = (Get-Command npm.cmd -ErrorAction SilentlyContinue)
if (-not $npm) { $npm = (Get-Command npm -ErrorAction SilentlyContinue) }
if (-not $npm) { throw "npm is not on PATH. Install Node.js (which bundles npm) and retry." }

if (-not (Test-Path (Join-Path $scraperDir 'node_modules'))) {
    Write-Host "Installing scraper dependencies..." -ForegroundColor Yellow
    Push-Location $scraperDir
    try {
        if (Test-Path (Join-Path $scraperDir 'package-lock.json')) {
            & $npm.Source 'ci'
        } else {
            & $npm.Source 'install'
        }
        # No --with-deps on Windows (it is a Linux-only apt step; the CI workflow uses it on Ubuntu).
        & npx playwright install chromium
    } finally {
        Pop-Location
    }
}

Write-Host "Refreshing bond prices: $BackendBaseUrl (dryRun=$($DryRun.IsPresent))" -ForegroundColor Green

# Pass config to the child process via the environment (keeps the secret off the command line).
$env:BACKEND_BASE_URL = $BackendBaseUrl
if (-not [string]::IsNullOrWhiteSpace($resolvedSecret)) {
    $env:BOND_INGEST_SECRET = $resolvedSecret
}
if ($DryRun) { $env:BONDS_DRY_RUN = 'true' } else { $env:BONDS_DRY_RUN = 'false' }

Push-Location $scraperDir
try {
    & node 'scrape-bonds.mjs'
    $exit = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($exit -ne 0) {
    throw "Scraper failed with exit code $exit. Stored prices were not changed."
}
Write-Host "Bond price refresh complete." -ForegroundColor Green
