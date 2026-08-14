<#
.SYNOPSIS
    Puts the current GitHub main branch onto the live server.

.DESCRIPTION
    The watcher pushes commits to GitHub on its own. Nothing pulls them onto the
    server, which is deliberate: a change reaching the browsers of sixty-odd
    people should be something somebody decided to do, not something that
    happened five minutes after a file was saved.

    This is that decision, as one command. It pulls on the server, rebuilds the
    backend and the web container, and waits to report what the backend is
    actually connected to -- because "it deployed" and "it works" are not the
    same claim.

    Roughly five to ten minutes, most of it Maven and npm inside the build.

.PARAMETER Server
    The instance address. Defaults to the current one; pass it when the IP
    changes rather than editing this file.

.PARAMETER Key
    The private key for that instance.

.EXAMPLE
    .\deploy.ps1
#>
[CmdletBinding()]
param(
    [string]$Server = "16.192.105.61",
    [string]$Key = "$env:USERPROFILE\.ssh\hr-portal-key-lf.pem",
    # Skips the image rebuild. Only useful when nothing but .env changed --
    # a code change deployed this way would appear to do nothing at all.
    [switch]$NoBuild
)

$ErrorActionPreference = "Continue"

function Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Die($m)  { Write-Host "  !!  $m" -ForegroundColor Red; exit 1 }

if (-not (Test-Path $Key)) { Die "No key at $Key" }

Write-Host ""
Step "Deploying to $Server"
Write-Host ""

# Unpushed work would deploy the previous commit and look like the change
# silently failed, so it is worth one look before spending ten minutes.
Step "Checking local git"
$dirty = git status --porcelain
if ($dirty) {
    Write-Host "  !   Uncommitted changes here. The server deploys what GitHub has," -ForegroundColor Yellow
    Write-Host "      so these will NOT be included:" -ForegroundColor Yellow
    $dirty -split "`n" | Select-Object -First 8 | ForEach-Object { Write-Host "        $_" -ForegroundColor DarkGray }
    $answer = Read-Host "  Continue anyway? (y/N)"
    if ($answer -ne "y") { Die "Stopped. Commit and push, then run this again." }
} else {
    Ok "working tree clean"
}

$build = if ($NoBuild) { "" } else { "--build" }

$remote = @"
set -e
cd ~/hr-portal
echo '--- pulling ---'
git pull
echo '--- starting ---'
sudo docker compose -f docker-compose.prod.yml up -d $build backend web
echo '--- waiting for the backend ---'
sleep 25
echo '--- database it is using ---'
sudo docker exec hrportal-backend env | grep DB_HOST || true
echo '--- recent log ---'
sudo docker logs --tail 12 hrportal-backend 2>&1 | tail -12
"@

Step "Running on the server (this takes a few minutes)"
ssh -i $Key -o StrictHostKeyChecking=accept-new "ubuntu@$Server" $remote

if ($LASTEXITCODE -ne 0) { Die "The deploy command failed. Read the output above." }

Write-Host ""
Ok "Done"
Write-Host ""
Write-Host "  Open http://$Server and press Ctrl+Shift+R." -ForegroundColor Cyan
Write-Host "  A cached page is the usual reason a fresh deploy looks unchanged." -ForegroundColor DarkGray
Write-Host ""
