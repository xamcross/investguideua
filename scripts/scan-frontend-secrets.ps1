#requires -Version 5.1
<#
.SYNOPSIS
    QA1 AC #10 secret scan: fails if any secret leaked into the built Angular bundle.
.DESCRIPTION
    Scans the built frontend output (frontend/dist) for two classes of leak:
      1. Static secret markers that must NEVER ship to a browser (Anthropic key prefix,
         private-key blocks, the secret env var NAMES, mongodb credential URIs).
      2. The concrete secret VALUES from the current environment / .env (if populated) -
         the strongest check: the exact deployed secret must not appear in any bundle file.
    Exits non-zero on the first leak so CI fails the build (SPECIFICATION section 10, AC #10).
    ASCII-only per CLAUDE.md (Windows PowerShell 5.1 decodes BOM-less files as Windows-1252).
.EXAMPLE
    .\scripts\scan-frontend-secrets.ps1
.EXAMPLE
    # Build first, then scan (CI):
    cd frontend; npm ci; npm run build; cd ..; .\scripts\scan-frontend-secrets.ps1
#>
[CmdletBinding()]
param(
    [string] $DistPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $DistPath -or $DistPath -eq '') {
    $DistPath = Join-Path $repoRoot 'frontend\dist'
}

if (-not (Test-Path $DistPath)) {
    throw "Bundle not found at '$DistPath'. Build the frontend first (npm run build) then re-run."
}

# Static markers that have no legitimate reason to appear in a client bundle.
$staticPatterns = @(
    'sk-ant-',                      # Anthropic API key prefix
    'BEGIN PRIVATE KEY',            # PEM private key block
    'BEGIN RSA PRIVATE KEY',
    'ANTHROPIC_API_KEY',            # secret env var names should never be inlined
    'LIQPAY_PRIVATE_KEY',
    'JWT_SECRET',
    'mongodb://[^''"]*:[^''"]*@'    # mongodb URI carrying user:password credentials
)

# Secret env var NAMES whose VALUES (if set) must not appear verbatim in the bundle.
$secretEnvNames = @('ANTHROPIC_API_KEY', 'LIQPAY_PRIVATE_KEY', 'LIQPAY_PUBLIC_KEY', 'JWT_SECRET', 'MONGODB_URI')

# Pull any populated values from a local .env so the scan also catches the real deployed secrets.
$envValues = @{}
$envFile = Join-Path $repoRoot '.env'
if (Test-Path $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*([A-Z0-9_]+)\s*=\s*(.+?)\s*$') {
            $envValues[$matches[1]] = $matches[2]
        }
    }
}
foreach ($name in $secretEnvNames) {
    $fromProcess = [Environment]::GetEnvironmentVariable($name)
    if ($fromProcess) { $envValues[$name] = $fromProcess }
}

$files = Get-ChildItem -LiteralPath $DistPath -Recurse -File |
    Where-Object { $_.Extension -in @('.js', '.mjs', '.css', '.html', '.json', '.txt', '.map') }

$violations = New-Object System.Collections.Generic.List[string]

foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }

    foreach ($pat in $staticPatterns) {
        if ($content -match $pat) {
            $violations.Add(("{0}: matched marker /{1}/" -f $file.FullName, $pat))
        }
    }

    foreach ($name in $secretEnvNames) {
        $val = $envValues[$name]
        # Skip empty and short/placeholder values (e.g. localhost Mongo URI) to avoid false positives.
        if ($val -and $val.Length -ge 12 -and $content.Contains($val)) {
            $violations.Add(("{0}: contains the live value of {1}" -f $file.FullName, $name))
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Host "AC #10 FAILED - secret material found in the frontend bundle:" -ForegroundColor Red
    foreach ($v in $violations) { Write-Host "  - $v" -ForegroundColor Red }
    exit 1
}

Write-Host ("AC #10 OK - scanned {0} bundle file(s); no secrets found." -f $files.Count) -ForegroundColor Green
exit 0
