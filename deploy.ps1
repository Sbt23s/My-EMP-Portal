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
    [switch]$NoBuild,
    # Answers the uncommitted-changes question up front, for a run with nobody
    # sitting in front of it. The warning is still printed; what is skipped is
    # the waiting, not the telling. Safe because the server deploys the pushed
    # commit either way -- the question only asks whether you meant to leave
    # something behind.
    [switch]$Yes
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
    if ($Yes) {
        Write-Host "  ->  -Yes given, continuing without asking." -ForegroundColor DarkGray
    } else {
        $answer = Read-Host "  Continue anyway? (y/N)"
        if ($answer -ne "y") { Die "Stopped. Commit and push, then run this again." }
    }
} else {
    Ok "working tree clean"
}

# A clean tree still deploys the wrong thing if the commit never reached GitHub:
# the server pulls, finds nothing new, and rebuilds the previous release while
# every check above stays quiet.
Step "Checking the commit is on GitHub"
git fetch --quiet 2>$null
$ahead = (git rev-list --count "@{u}..HEAD" 2>$null)
if ($LASTEXITCODE -eq 0 -and $ahead -and [int]$ahead -gt 0) {
    Die "$ahead commit(s) here are not pushed. Run: git push"
}
Ok "local commit is on GitHub"

$build = if ($NoBuild) { "" } else { "--build" }

# The commit this run is meant to put live. The server is checked against it
# after the pull, because a pull that fetches but does not move -- a dirty tree,
# a diverged branch -- exits 0 and leaves the previous release running. That
# happened twice on 30 Aug 2026: the script said "Done" both times while the
# site kept serving a commit from eleven releases back.
$expected = (git rev-parse HEAD).Trim()

$remote = @"
set -e
cd ~/hr-portal
echo '--- Pulling from GitHub ---'
git fetch --all --quiet
git merge --ff-only origin/main
echo '--- Verifying the server is on the expected commit ---'
actual=`$(git rev-parse HEAD)
if [ "`$actual" != "$expected" ]; then
  echo "STOPPING: server is on `$actual but this deploy expects $expected."
  echo "The pull did not move the tree. Nothing was rebuilt, the live site is untouched."
  echo "Usually a dirty working tree here -- check 'git status' on the server."
  exit 1
fi
echo "on `$actual -- correct"
echo '--- Rebuilding and starting production services ---'
sudo docker compose -f docker-compose.prod.yml --profile analytics up -d $build
echo '--- waiting for the backend ---'
sleep 25
echo '--- database it is using ---'
sudo docker exec hrportal-backend env | grep DB_HOST || true
echo '--- recent log ---'
sudo docker logs --tail 12 hrportal-backend 2>&1 | tail -12
"@

Step "Running on the server (this takes a few minutes)"
ssh -i $Key -o StrictHostKeyChecking=accept-new "ubuntu@$Server" $remote

if ($LASTEXITCODE -ne 0) {
    Die "Deploy failed -- read the output above. The live site still runs the previous release."
}

Write-Host ""
Ok "Done"
Write-Host ""
Write-Host "  Open http://$Server and press Ctrl+Shift+R." -ForegroundColor Cyan
Write-Host "  A cached page is the usual reason a fresh deploy looks unchanged." -ForegroundColor DarkGray
Write-Host ""
