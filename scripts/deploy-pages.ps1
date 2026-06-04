#requires -Version 5.1
<#
.SYNOPSIS
    Builds and deploys the InvestGuideUA frontend (SEO-prerendered) to Cloudflare Pages.
.DESCRIPTION
    Runs the full SEO build pipeline and uploads the static output to Cloudflare Pages via
    'npx wrangler pages deploy'. Steps:
      1. npm ci                (unless -SkipInstall)
      2. npm run build         (seo:articles -> ng build; prerenders public routes)
      3. npm run seo:generate  (writes robots.txt + sitemap.xml into the build output)
      4. npm run seo:audit     (SEO acceptance gate; aborts the deploy on any violation
                                unless -SkipAudit)
      5. npx wrangler pages deploy dist/investguide-frontend/browser

    Only the static 'browser/' folder is uploaded - the build-time 'server/' bundle is NEVER
    deployed (no production server; see Constitution Principle I). robots.txt/sitemap.xml use
    SEO_SITE_ORIGIN (default https://investguideua.com) for canonical/alternate URLs.

    This file is pure ASCII per CLAUDE.md (the ASCII-only rule applies to Windows-executed
    .ps1/.cmd/.bat scripts).

    Prerequisites:
      - Node + npm on PATH (Node 18+; the repo pins Angular 17.3).
      - A Cloudflare API token for Wrangler. Either:
          * set CLOUDFLARE_API_TOKEN (and CLOUDFLARE_ACCOUNT_ID) in the environment, or
          * run 'npx wrangler login' once beforehand.
    NOTE: This deploys the static SITE only. It does NOT change the Cloudflare Pages project's
    own "Build command" / "Build output directory" settings (those live in the CF dashboard and
    matter only for the Git-integration build path; see docs/DEPLOY-cloud.md Phase 5).
.PARAMETER ProjectName
    Cloudflare Pages project name. Default: investguideua.
.PARAMETER SiteOrigin
    Canonical site origin used for sitemap/robots/canonical URLs. Default:
    https://investguideua.com. Sets the SEO_SITE_ORIGIN environment variable for the build.
.PARAMETER Branch
    Optional Cloudflare Pages deployment branch (passed to wrangler as --branch). Use the
    production branch name to publish a production deployment; omit for a preview deployment.
.PARAMETER SkipInstall
    Skip 'npm ci' (use the already-installed node_modules).
.PARAMETER SkipAudit
    Skip the SEO acceptance audit gate (NOT recommended).
.PARAMETER DryRun
    Build, generate and audit, but do NOT upload to Cloudflare (verify locally).
.EXAMPLE
    .\scripts\deploy-pages.ps1
.EXAMPLE
    .\scripts\deploy-pages.ps1 -Branch main
.EXAMPLE
    .\scripts\deploy-pages.ps1 -DryRun
.EXAMPLE
    .\scripts\deploy-pages.ps1 -ProjectName investguideua -SiteOrigin "https://investguideua.com"
#>
[CmdletBinding()]
param(
    [string]$ProjectName = 'investguideua',
    [string]$SiteOrigin = 'https://investguideua.com',
    [string]$Branch,
    [switch]$SkipInstall,
    [switch]$SkipAudit,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $repoRoot 'frontend'
$outDir   = Join-Path $frontend 'dist/investguide-frontend/browser'

if (-not (Test-Path $frontend)) {
    throw "frontend folder not found at $frontend"
}

# --- Resolve npm + npx ---
# Prefer the .cmd shims over the .ps1 wrappers: invoking npm.ps1 with splatted array args via
# '& $npm @Args' drops the arguments (the wrapper prints usage), so we explicitly pick npm.cmd.
function Resolve-Tool {
    param([string]$Name)
    $cmd = Get-Command "$Name.cmd" -ErrorAction SilentlyContinue
    if (-not $cmd) { $cmd = Get-Command $Name -ErrorAction SilentlyContinue }
    return $cmd
}
$npmCmd = Resolve-Tool -Name 'npm'
if (-not $npmCmd) { throw "npm not found on PATH. Install Node.js (18+) and retry." }
$npm = $npmCmd.Source

$npxCmd = Resolve-Tool -Name 'npx'
if (-not $npxCmd) { throw "npx not found on PATH (ships with npm). Install Node.js (18+) and retry." }
$npx = $npxCmd.Source

# Normalize the canonical origin (strip any trailing slash) and expose it to the build pipeline.
$SiteOrigin = $SiteOrigin.TrimEnd('/')
$env:SEO_SITE_ORIGIN = $SiteOrigin
Write-Host "Site origin (SEO_SITE_ORIGIN): $SiteOrigin" -ForegroundColor Green

# Run npm from the frontend folder. Push/Pop so the caller's working directory is restored on
# exit (including on throw or the -DryRun early return).
Push-Location $frontend
try {

function Invoke-Step {
    # NOTE: the arg parameter must NOT be named $Args - that collides with PowerShell's automatic
    # $Args variable and breaks splatting (the command receives no arguments).
    param([string]$Label, [string[]]$NpmArgs)
    Write-Host ""
    Write-Host ">> $Label" -ForegroundColor Cyan
    & $npm @NpmArgs
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed (exit $LASTEXITCODE)."
    }
}

# --- 1. Install ---
if (-not $SkipInstall) {
    Invoke-Step -Label 'npm ci' -NpmArgs @('ci')
}
else {
    Write-Host "Skipping 'npm ci' (-SkipInstall)." -ForegroundColor Yellow
}

# --- 2. Build (prerender) ---
Invoke-Step -Label 'npm run build (prerender)' -NpmArgs @('run', 'build')

# --- 3. Generate robots.txt + sitemap.xml into the build output ---
Invoke-Step -Label 'npm run seo:generate' -NpmArgs @('run', 'seo:generate')

# --- 4. SEO acceptance gate ---
if (-not $SkipAudit) {
    Invoke-Step -Label 'npm run seo:audit' -NpmArgs @('run', 'seo:audit')
}
else {
    Write-Host "Skipping SEO audit (-SkipAudit). Not recommended." -ForegroundColor Yellow
}

if (-not (Test-Path $outDir)) {
    throw "Build output not found at $outDir. The build did not produce the expected folder."
}

# Belt-and-suspenders: the static 'server/' bundle must never be uploaded (Constitution I).
$serverDir = Join-Path $frontend 'dist/investguide-frontend/server'
if (Test-Path $serverDir) {
    Write-Host "Note: a build-time 'server/' bundle exists; only 'browser/' is uploaded." -ForegroundColor DarkGray
}

if ($DryRun) {
    Write-Host ""
    Write-Host "DryRun: build + generate + audit complete. Skipping Cloudflare upload." -ForegroundColor Yellow
    Write-Host "Output ready at: $outDir" -ForegroundColor Green
    return
}

# --- 5. Deploy the static output to Cloudflare Pages ---
if (-not $env:CLOUDFLARE_API_TOKEN) {
    Write-Host "WARNING: CLOUDFLARE_API_TOKEN is not set. Wrangler will prompt or fail." -ForegroundColor Yellow
    Write-Host "         Set CLOUDFLARE_API_TOKEN (and CLOUDFLARE_ACCOUNT_ID) or run 'npx wrangler login' first." -ForegroundColor Yellow
}

$deployArgs = @('wrangler', 'pages', 'deploy', $outDir, '--project-name', $ProjectName)
if ($Branch) { $deployArgs += @('--branch', $Branch) }

Write-Host ""
Write-Host "Deploying to Cloudflare Pages project '$ProjectName': npx $($deployArgs -join ' ')" -ForegroundColor Cyan
& $npx @deployArgs
if ($LASTEXITCODE -ne 0) {
    throw "wrangler pages deploy failed (exit $LASTEXITCODE)."
}

Write-Host ""
Write-Host "Deployed to Cloudflare Pages." -ForegroundColor Green
Write-Host "  Verify: https://$ProjectName.pages.dev  (and your custom domain once mapped)" -ForegroundColor Cyan
Write-Host "  robots: <origin>/robots.txt   sitemap: <origin>/sitemap.xml" -ForegroundColor Cyan

}
finally {
    Pop-Location
}
