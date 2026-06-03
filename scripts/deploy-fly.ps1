#requires -Version 5.1
<#
.SYNOPSIS
    Deploys the InvestGuideUA backend to Fly.io (app: investguideua-api).
.DESCRIPTION
    Builds and releases backend/ to Fly using the remote builder (no local Docker
    or Maven required). Reads backend/fly.toml for the app name and VM config.

    Secrets are NOT set here - set them once with 'fly secrets set NAME=value'.
    This script only checks the required, fail-fast ones (MONGODB_URI, JWT_SECRET,
    ANTHROPIC_API_KEY) and then runs 'fly deploy'. MAIL_* and MONO_TOKEN are
    OPTIONAL - the app starts and registers users without them.

    Extra args are forwarded to 'fly deploy' as-is; do not pass a bare '--'
    (PowerShell drops it). Long flags like '--strategy immediate' pass through fine.

    Prerequisites:
      - Fly CLI on PATH (fly / flyctl). Install: https://fly.io/docs/flyctl/install/
      - Logged in:  fly auth login
.PARAMETER LocalBuild
    Build the image with the local Docker daemon instead of the Fly remote builder.
.PARAMETER SkipSecretCheck
    Skip the pre-deploy check that the required Fly secrets are present.
.PARAMETER ExtraArgs
    Extra arguments passed straight through to 'fly deploy'
    (e.g. --strategy immediate, --now).
.EXAMPLE
    .\scripts\deploy-fly.ps1
.EXAMPLE
    .\scripts\deploy-fly.ps1 --strategy immediate
.EXAMPLE
    .\scripts\deploy-fly.ps1 -LocalBuild
#>
[CmdletBinding()]
param(
    [switch]$LocalBuild,
    [switch]$SkipSecretCheck,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ExtraArgs
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backend  = Join-Path $repoRoot 'backend'
$flyToml  = Join-Path $backend 'fly.toml'
$appName  = 'investguideua-api'

# --- Resolve the Fly CLI (fly or flyctl) ---
$flyCmd = Get-Command fly -ErrorAction SilentlyContinue
if (-not $flyCmd) { $flyCmd = Get-Command flyctl -ErrorAction SilentlyContinue }
if (-not $flyCmd) {
    throw "Fly CLI not found on PATH. Install: https://fly.io/docs/flyctl/install/ then run 'fly auth login'."
}
$fly = $flyCmd.Source

if (-not (Test-Path $flyToml)) {
    throw "fly.toml not found at $flyToml"
}

# --- Verify authentication ---
# Wrap in try/catch: under ErrorActionPreference='Stop', a logged-out 'fly' writing to stderr can
# surface as a terminating NativeCommandError before we reach the exit-code check.
try {
    $who = & $fly auth whoami 2>$null
}
catch {
    $who = $null
}
if (-not $who -or $LASTEXITCODE -ne 0) {
    throw "Not logged in to Fly. Run: fly auth login"
}
Write-Host "Fly user: $who" -ForegroundColor Green

# Work from the backend folder so fly.toml + Dockerfile are auto-detected.
Set-Location $backend

# --- Pre-deploy secret sanity check (warn-only; these are the app's fail-fast secrets) ---
if (-not $SkipSecretCheck) {
    $required = @('MONGODB_URI', 'JWT_SECRET', 'ANTHROPIC_API_KEY')
    try {
        $secretsOut = (& $fly secrets list -a $appName 2>$null | Out-String)
        # Exact-name match on the first column (avoid a substring like OLD_MONGODB_URI counting as present).
        $presentNames = @()
        foreach ($line in ($secretsOut -split "`n")) {
            $first = (($line.Trim()) -split '\s+')[0]
            if ($first) { $presentNames += $first }
        }
        $missing = @($required | Where-Object { $presentNames -notcontains $_ })
        if ($missing.Count -gt 0) {
            Write-Host "WARNING: required secrets not set on '$appName': $($missing -join ', ')" -ForegroundColor Yellow
            Write-Host "         The app fails fast at startup without them. Set with: fly secrets set NAME=value" -ForegroundColor Yellow
            Write-Host "         (re-run with -SkipSecretCheck to bypass this check)" -ForegroundColor Yellow
        }
        else {
            Write-Host "Required secrets present (MONGODB_URI, JWT_SECRET, ANTHROPIC_API_KEY)." -ForegroundColor Green
        }
    }
    catch {
        Write-Host "Could not read 'fly secrets list' (continuing): $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# --- Deploy ---
# -a pins the target app explicitly (belt-and-suspenders vs. fly.toml/CWD ambiguity).
$deployArgs = @('deploy', '-a', $appName)
if (-not $LocalBuild) { $deployArgs += '--remote-only' }
if ($ExtraArgs) { $deployArgs += $ExtraArgs }

Write-Host "Deploying '$appName': $fly $($deployArgs -join ' ')" -ForegroundColor Cyan
& $fly @deployArgs
if ($LASTEXITCODE -ne 0) {
    throw "fly deploy failed (exit $LASTEXITCODE)."
}

Write-Host ""
Write-Host "Deployed. Useful follow-ups:" -ForegroundColor Green
Write-Host "  Health: https://$appName.fly.dev/actuator/health" -ForegroundColor Cyan
Write-Host "          (machine scales to zero; the first request may cold-start the JVM ~10-30s)" -ForegroundColor DarkGray
Write-Host "  Logs:   $fly logs -a $appName       (look for mail_delivery_enabled / mail_delivery_disabled)" -ForegroundColor Cyan
Write-Host "  Status: $fly status -a $appName" -ForegroundColor Cyan
