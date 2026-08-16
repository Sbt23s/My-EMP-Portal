<#
.SYNOPSIS
    Automatically commits, pushes to GitHub (my-emp main), and deploys to the live server with HTTPS/SSL.

.EXAMPLE
    .\auto-push-deploy.ps1 -Message "Updated Employee Module"
#>
[CmdletBinding()]
param(
    [string]$Message = "Auto commit and push - $(Get-Date -Format 'yyyy-MM-dd HH:mm')",
    [string]$Server = "16.192.105.61",
    [string]$Key = "$env:USERPROFILE\.ssh\hr-portal-key-lf.pem"
)

$ErrorActionPreference = "Continue"

function Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Die($m)  { Write-Host "  !!  $m" -ForegroundColor Red; exit 1 }

Write-Host ""
Step "Starting Auto Push and Deploy Pipeline"
Write-Host ""

# 1. Staging and Committing
Step "Staging and Committing changes"
git add .
$dirty = git status --porcelain
if ($dirty) {
    git commit -m "$Message"
    Ok "Committed with message: $Message"
} else {
    Ok "No new uncommitted changes found. Proceeding with push and deploy."
}

# 2. Pushing to GitHub
Step "Pushing to GitHub (my-emp main)"
git push my-emp main
if ($LASTEXITCODE -ne 0) {
    Die "Git push to GitHub failed."
}
Ok "Pushed successfully to https://github.com/Sbt23s/My-EMP-Portal.git"

# 3. Deploying to Server
if (Test-Path $Key) {
    Step "Deploying to live server ($Server)"
    $remote = @"
set -e
cd ~/hr-portal
echo '--- Resetting uncommitted server changes ---'
git checkout .
echo '--- Pulling latest code from GitHub ---'
git pull
echo '--- Checking SSL certificate for pixoushrportal.pixous.info ---'
if [ ! -f /etc/letsencrypt/live/pixoushrportal.pixous.info/fullchain.pem ]; then
    echo '--- Installing certbot and requesting SSL certificate ---'
    sudo apt-get update -qq && sudo apt-get install -y -qq certbot || true
    sudo docker compose -f docker-compose.prod.yml down || true
    sudo certbot certonly --standalone -d pixoushrportal.pixous.info --non-interactive --agree-tos --email sethubala.pixous@gmail.com || true
fi
echo '--- Removing leftover container conflicts ---'
sudo docker compose -f docker-compose.prod.yml down || true
sudo docker rm -f hrportal-mysql hrportal-backend hrportal-web hrportal-redis || true
echo '--- Rebuilding and starting production services ---'
sudo docker compose -f docker-compose.prod.yml up -d --build
echo '--- Checking recent logs ---'
sudo docker logs --tail 10 hrportal-backend 2>&1 | tail -10
"@
    ssh -i $Key -o StrictHostKeyChecking=accept-new "ubuntu@$Server" $remote
    if ($LASTEXITCODE -eq 0) {
        Ok "Live deployment successful!"
        Write-Host "  Open https://pixoushrportal.pixous.info/login and press Ctrl+Shift+R." -ForegroundColor Cyan
    } else {
        Die "Server deploy failed."
    }
} else {
    Write-Host "  ! Key file not found at $Key - skipped server SSH deploy." -ForegroundColor Yellow
    Write-Host "  GitHub push complete. Run .\deploy.ps1 once your SSH key is available." -ForegroundColor Cyan
}

Write-Host ""
Ok "Auto Push and Deploy Pipeline Finished!"
Write-Host ""
