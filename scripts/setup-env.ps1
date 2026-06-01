#requires -Version 5.1
<#
.SYNOPSIS
    Prepares a local .env so the stack boots: generates JWT_SECRET and seeds dev placeholders
    for not-yet-used secrets (Anthropic / LiqPay).
.DESCRIPTION
    Idempotent and additive: if .env already exists it is kept, and only BLANK required keys are
    filled in (JWT_SECRET is generated; the LLM/payment secrets get clearly-marked dev
    placeholders so the X2 fail-fast guard passes). Use -Force to recreate .env from the example.
    Replace the placeholders with real keys before working on the BE-S (LLM) or BE-P (payments)
    tickets.
.EXAMPLE
    .\scripts\setup-env.ps1
    .\scripts\setup-env.ps1 -Force
#>
[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repoRoot    = Split-Path -Parent $PSScriptRoot
$envPath     = Join-Path $repoRoot '.env'
$examplePath = Join-Path $repoRoot '.env.example'

function Get-EnvValue {
    param([string[]]$Lines, [string]$Key)
    foreach ($l in $Lines) {
        if ($l -match "^\s*$Key\s*=(.*)$") { return $matches[1].Trim() }
    }
    return $null
}

function Set-EnvValue {
    param([string[]]$Lines, [string]$Key, [string]$Value)
    $found = $false
    $out = foreach ($l in $Lines) {
        if ($l -match "^\s*$Key\s*=") { $found = $true; "$Key=$Value" } else { $l }
    }
    if (-not $found) { $out = @($out) + "$Key=$Value" }
    return [string[]]$out
}

if ($Force -or -not (Test-Path $envPath)) {
    Copy-Item -Path $examplePath -Destination $envPath -Force
    Write-Host "Created .env from .env.example" -ForegroundColor Green
}
else {
    Write-Host "Using existing .env (filling blanks only; -Force recreates it)." -ForegroundColor Yellow
}

$lines = [string[]](Get-Content $envPath)

# JWT_SECRET: generate a 48-byte base64 secret if blank (works on PS 5.1 and 7+).
if (-not (Get-EnvValue $lines 'JWT_SECRET')) {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes = New-Object byte[] 48
        $rng.GetBytes($bytes)
        $jwtSecret = [Convert]::ToBase64String($bytes)
    }
    finally {
        $rng.Dispose()
    }
    $lines = Set-EnvValue $lines 'JWT_SECRET' $jwtSecret
    Write-Host "Generated JWT_SECRET (>= 32 bytes)." -ForegroundColor Green
}

# Dev placeholders for not-yet-used secrets so the X2 fail-fast guard passes and the app boots.
$placeholders = [ordered]@{
    'ANTHROPIC_API_KEY'  = 'dev-placeholder-replace-before-BE-S'
    'LIQPAY_PUBLIC_KEY'  = 'dev-placeholder-replace-before-BE-P'
    'LIQPAY_PRIVATE_KEY' = 'dev-placeholder-replace-before-BE-P'
}
$seeded = @()
foreach ($key in $placeholders.Keys) {
    if (-not (Get-EnvValue $lines $key)) {
        $lines = Set-EnvValue $lines $key $placeholders[$key]
        $seeded += $key
    }
}

# Write WITHOUT a BOM: Windows PowerShell 5.1's `-Encoding UTF8` prepends a UTF-8 BOM, which can
# corrupt the first line for Docker Compose's env_file parser. UTF8Encoding($false) = no BOM.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($envPath, $lines, $utf8NoBom)

Write-Host "Wrote .env" -ForegroundColor Green
if ($seeded.Count -gt 0) {
    Write-Host ""
    Write-Host "Seeded DEV PLACEHOLDERS for: $($seeded -join ', ')" -ForegroundColor Yellow
    Write-Host "These let the stack boot for X1-X4. Replace them with real keys before the" -ForegroundColor Yellow
    Write-Host "BE-S (LLM) and BE-P (payments) tickets." -ForegroundColor Yellow
}
