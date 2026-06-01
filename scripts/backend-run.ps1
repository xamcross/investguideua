#requires -Version 5.1
<#
.SYNOPSIS
    Runs the Spring Boot backend locally (no Docker), loading secrets from .env into the session.
.DESCRIPTION
    Reads .env, exports each KEY=VALUE into the current process environment, then runs the app.
    Requires JDK 21 and Maven on PATH (see README for `winget` install lines).
    Assumes a local MongoDB on mongodb://localhost:27017 unless MONGODB_URI overrides it.
.EXAMPLE
    .\scripts\backend-run.ps1            # mvn spring-boot:run (dev)
    .\scripts\backend-run.ps1 -Jar       # package, then java -jar target\...
#>
[CmdletBinding()]
param(
    [switch]$Jar
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envPath  = Join-Path $repoRoot '.env'
$backend  = Join-Path $repoRoot 'backend'

if (-not (Test-Path $envPath)) {
    throw ".env not found. Run .\scripts\setup-env.ps1 first."
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven (mvn) is not on PATH. Install with: winget install Apache.Maven"
}

# Load .env into the process environment (ignores blank lines and # comments).
Get-Content $envPath | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $idx = $line.IndexOf('=')
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        if ($key) {
            Set-Item -Path "Env:$key" -Value $val
            Write-Verbose "Loaded $key"
        }
    }
}
Write-Host "Loaded environment from .env" -ForegroundColor Green

Set-Location $backend
if ($Jar) {
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Maven package failed." }
    $jarFile = Get-ChildItem -Path (Join-Path $backend 'target') -Filter 'investguide-backend-*.jar' |
               Select-Object -First 1
    Write-Host "Running $($jarFile.Name)" -ForegroundColor Green
    & java -jar $jarFile.FullName
}
else {
    & mvn spring-boot:run
}
