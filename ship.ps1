<#
  Commit, push, and deploy - in that order, stopping at the first thing that
  is not right.

      .\ship.ps1                      commit everything pending, push, deploy
      .\ship.ps1 -Message "..."       with your own commit message
      .\ship.ps1 -NoDeploy            push only
      .\ship.ps1 -DeployOnly          deploy what is already pushed

  Why a script rather than three commands typed by hand: the three have to
  happen in order, and a push that is not followed by a deploy looks exactly
  like a deploy that did not take. This does them together and says which
  step it is on.
#>
param(
  [string]$Message,
  [switch]$NoDeploy,
  [switch]$DeployOnly,
  [string]$Server = "16.192.105.61",
  [string]$Key    = "$env:USERPROFILE\.ssh\hr-portal-key-lf.pem"
)

$ErrorActionPreference = "Stop"
function Step($t) { Write-Host "`n==> $t" -ForegroundColor Cyan }
function Ok($t)   { Write-Host "    $t"   -ForegroundColor Green }
function Die($t)  { Write-Host "`nSTOP: $t`n" -ForegroundColor Red; exit 1 }

Set-Location $PSScriptRoot

if (-not $DeployOnly) {
  Step "Committing"
  $pending = git status --porcelain
  if (-not $pending) {
    Ok "nothing to commit"
  } else {
    $pending | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }

    # Build output is not source. A 90 MB APK in every commit makes the history
    # unusable within a week, and the deploy does not read it.
    git add -A -- . ':!mobile-app/build' ':!mobile-app/.dart_tool' ':!web/dist'
    if (-not $Message) {
      $Message = "Update " + (Get-Date -Format "yyyy-MM-dd HH:mm")
    }
    git commit -m $Message | Out-Null
    Ok "committed: $Message"
  }

  Step "Pushing"
  git push
  if ($LASTEXITCODE -ne 0) { Die "Push failed. Nothing was deployed." }
  Ok "pushed"
}

if ($NoDeploy) {
  Write-Host "`nPushed. Skipping deploy (-NoDeploy).`n" -ForegroundColor Yellow
  exit 0
}

if (-not (Test-Path $Key)) { Die "No SSH key at $Key" }

Step "Deploying to $Server"

# Everything below runs on the server, as one script, so a failure half way
# through does not leave the next command running against a broken state.
#
# The container is removed by name before compose recreates it. Compose renames
# the old one out of the way first and deletes it after; a run interrupted
# between those two steps leaves the rename behind, holding the name, and every
# deploy after that fails with "container name is already in use". Clearing it
# first makes that impossible rather than something to recover from.
$remote = @'
set -e
cd ~/hr-portal 2>/dev/null || cd ~/hr-port
echo "--- pulling ---"
git pull --ff-only
echo "--- clearing any half-recreated containers ---"
for n in hrportal-backend hrportal-web hrportal-analytics; do
  for id in $(sudo docker ps -a --format '{{.ID}} {{.Names}}' | awk -v n="$n" '$2 ~ n"$" {print $1}'); do
    sudo docker rm -f "$id" >/dev/null 2>&1 || true
  done
done
echo "--- building and starting ---"
sudo docker compose -f docker-compose.prod.yml up -d --build backend web analytics
echo "--- waiting for the backend ---"
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost/api/my-modules || true)
  # 401 is the healthy answer: it is up and asking for a token.
  if [ "$code" = "401" ]; then echo "backend up"; break; fi
  if [ "$i" = "60" ]; then
    echo "BACKEND DID NOT COME UP"
    sudo docker compose -f docker-compose.prod.yml logs --tail=60 backend
    exit 1
  fi
  sleep 3
done
'@

ssh -i $Key -o StrictHostKeyChecking=accept-new "ubuntu@$Server" $remote
if ($LASTEXITCODE -ne 0) { Die "The deploy failed. Read the output above." }

Step "Checking the live site"
$domain = "https://pixoushrportal.pixous.info"
try {
  $site = (Invoke-WebRequest -Uri "$domain/" -Method Head -TimeoutSec 20 -SkipHttpErrorCheck).StatusCode
} catch { $site = "unreachable" }
try {
  $login = (Invoke-WebRequest -Uri "$domain/api/auth/login" -Method Post -TimeoutSec 20 `
            -Headers @{ "Content-Type" = "application/json"; "Origin" = $domain } `
            -Body '{"username":"__probe__","password":"__probe__"}' -SkipHttpErrorCheck).StatusCode
} catch { $login = "unreachable" }

Write-Host "    site  : $site    (want 200)"
Write-Host "    login : $login   (want 401 - reached the endpoint)"

if ($site -eq 200 -and $login -eq 401) {
  Write-Host "`nDeployed and healthy.  $domain`n" -ForegroundColor Green
} else {
  Write-Host "`nDeployed, but the checks above are not what they should be.`n" -ForegroundColor Yellow
}
