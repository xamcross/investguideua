# Verifies the i18n feature end to end on Windows.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File scripts\verify-i18n.ps1
# Requires: Node 18+ and npm (frontend), JDK 21 and Maven (backend).

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Write-Host "Repo root: $root"

# ---- Frontend: clean install, build (compiles templates+types), unit tests ----------
Write-Host ""
Write-Host "=== Frontend: clean install (npm ci) ==="
Push-Location "$root\frontend"
# npm ci wipes node_modules and installs exactly from package-lock.json. This avoids the
# 'Cannot find module esbuild' state that a plain 'npm install' can leave on Windows when it
# cannot replace an in-use esbuild binary. If npm ci fails with EACCES on esbuild, close any
# running 'ng serve' / node.exe / editor locking frontend\node_modules and re-run.
npm ci
if ($LASTEXITCODE -ne 0) { throw "npm ci failed (if EACCES on esbuild: close node/editors locking node_modules, then retry)" }

Write-Host ""
Write-Host "=== Frontend: production build (compiles templates + types) ==="
npm run build
if ($LASTEXITCODE -ne 0) { throw "ng build failed" }

Write-Host ""
Write-Host "=== Frontend: unit tests (headless Chrome) ==="
npm run test:ci
if ($LASTEXITCODE -ne 0) { throw "ng test failed" }

Write-Host ""
Write-Host "=== i18n dictionaries: parse + key parity (uk vs en) ==="
node -e "const fs=require('fs');const uk=JSON.parse(fs.readFileSync('public/i18n/uk.json','utf8'));const en=JSON.parse(fs.readFileSync('public/i18n/en.json','utf8'));function k(o,p){let r=[];for(const x in o){const v=o[x];const kp=p?p+'.'+x:x;if(v&&typeof v==='object')r=r.concat(k(v,kp));else r.push(kp);}return r;}const a=k(uk,'').sort(),b=k(en,'').sort();const onlyUk=a.filter(x=>!b.includes(x));const onlyEn=b.filter(x=>!a.includes(x));console.log('uk keys',a.length,'en keys',b.length);if(onlyUk.length||onlyEn.length){console.error('KEY MISMATCH onlyUk',onlyUk,'onlyEn',onlyEn);process.exit(1);}console.log('Key sets match.');"
if ($LASTEXITCODE -ne 0) { throw "i18n key parity check failed" }
Pop-Location

# ---- Backend: full test suite (includes the SearchLanguage / prompt changes) ---------
Write-Host ""
Write-Host "=== Backend: mvn test ==="
Push-Location "$root\backend"
mvn -q test
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "mvn test failed" }
Pop-Location

Write-Host ""
Write-Host "ALL CHECKS PASSED."
