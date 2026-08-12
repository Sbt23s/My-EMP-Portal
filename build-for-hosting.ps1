<#
.SYNOPSIS
    Builds the web app for Windows hosting and leaves a folder ready to upload.

.DESCRIPTION
    The one thing that has to be right at build time is the backend address. It
    is baked into the bundle by Vite, so a wrong value here cannot be fixed by
    editing files on the server afterwards -- the build has to be repeated.

    Produces web\dist\. Upload everything inside it to wwwroot\.

.PARAMETER ApiUrl
    Where the backend answers, with no trailing slash. For example
    https://pixous-ems-backend.onrender.com

.EXAMPLE
    .\build-for-hosting.ps1 -ApiUrl https://pixous-ems-backend.onrender.com
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApiUrl
)

$ErrorActionPreference = "Stop"

function Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Die($m)  { Write-Host "  !!  $m" -ForegroundColor Red; exit 1 }

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$web  = Join-Path $root "web"

# A trailing slash produces "//api/..." in every request, which some proxies
# reject and all of them log confusingly.
$ApiUrl = $ApiUrl.TrimEnd("/")

if ($ApiUrl -notmatch '^https?://') { Die "ApiUrl must start with http:// or https://" }
if ($ApiUrl -match '^http://' ) {
    Write-Host "  !   That is a plain http address." -ForegroundColor Yellow
    Write-Host "      A page served over https cannot call it -- browsers block mixed content." -ForegroundColor Yellow
    Write-Host "      The camera, microphone and GPS also need https to work at all." -ForegroundColor Yellow
}

Step "Checking the backend is reachable at $ApiUrl"
try {
    $r = Invoke-WebRequest "$ApiUrl/actuator/health" -TimeoutSec 25 -UseBasicParsing
    Ok "Backend answered $($r.StatusCode)"
} catch {
    Write-Host "  !   Could not reach $ApiUrl/actuator/health" -ForegroundColor Yellow
    Write-Host "      Building anyway -- but if this address is wrong, the site will load" -ForegroundColor Yellow
    Write-Host "      and then fail to log in." -ForegroundColor Yellow
}

Step "Installing dependencies"
Set-Location $web
if (-not (Test-Path node_modules)) { npm install } else { Ok "node_modules present" }

Step "Building"
$env:VITE_API_URL = $ApiUrl
npm run build
if ($LASTEXITCODE -ne 0) { Die "The build failed." }

$dist = Join-Path $web "dist"
if (-not (Test-Path (Join-Path $dist "index.html"))) { Die "dist\index.html is missing." }
if (-not (Test-Path (Join-Path $dist "web.config"))) {
    Die "dist\web.config is missing. It should have been copied from web\public\web.config."
}

# Proof the address really is in the bundle. Getting this wrong is the single
# most common way a hosted build fails, and it fails only at login.
$bundle = Get-ChildItem (Join-Path $dist "assets") -Filter "index-*.js" | Select-Object -First 1
if ($bundle -and (Select-String -Path $bundle.FullName -Pattern ([regex]::Escape($ApiUrl)) -Quiet)) {
    Ok "The backend address is baked into the bundle"
} else {
    Write-Host "  !   Could not find $ApiUrl inside the bundle." -ForegroundColor Yellow
}

$size = [math]::Round(((Get-ChildItem $dist -Recurse | Measure-Object Length -Sum).Sum / 1MB), 1)
$count = (Get-ChildItem $dist -Recurse -File).Count

Write-Host ""
Ok "Ready to upload: $dist"
Ok "$count files, $size MB"
Write-Host ""
Write-Host "Next:" -ForegroundColor Green
Write-Host "  1. Upload EVERYTHING inside web\dist\ into wwwroot\ on your hosting"
Write-Host "     (including web.config and the assets\ folder)"
Write-Host "  2. On the backend, set APP_CORS_ALLOWED_ORIGINS to your site's address"
Write-Host "     and restart it, or logging in will be blocked by the browser"
Write-Host ""
Write-Host "Full walkthrough: docs\WINDOWS_HOSTING.md" -ForegroundColor DarkGray
