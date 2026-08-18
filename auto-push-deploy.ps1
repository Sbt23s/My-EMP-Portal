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
    $remote = @'
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
echo '--- Checking Coturn TURN server configuration ---'
if [ ! -f /etc/turn-secret ]; then
    echo '--- Configuring Coturn TURN server for cross-network WebRTC calling ---'
    chmod +x setup-turn.sh 2>/dev/null || true
    ./setup-turn.sh || true
fi
echo '--- Removing leftover container conflicts ---'
sudo docker rm -f hrportal-mysql hrportal-backend hrportal-web hrportal-redis hrportal-analytics 2>/dev/null || true
sudo docker ps -aq | xargs -r sudo docker rm -f 2>/dev/null || true
sudo docker container prune -f 2>/dev/null || true
sudo docker network prune -f 2>/dev/null || true
sudo docker compose -f docker-compose.prod.yml --profile analytics down --volumes --remove-orphans 2>/dev/null || true
sleep 3
echo '--- Rebuilding and starting production services ---'
sudo docker compose -f docker-compose.prod.yml --profile analytics up -d --build
echo '--- Waiting for backend service to become ready ---'
for i in $(seq 1 30); do
    if sudo docker exec hrportal-backend curl -s http://localhost:8080/actuator/health | grep -q 'UP'; then
        echo '--- Backend is UP and Healthy! ---'
        break
    fi
    sleep 2
done
echo '--- Checking recent logs ---'
sudo docker logs --tail 15 hrportal-backend 2>&1 | tail -15
'@
    ssh -o ServerAliveInterval=30 -o ServerAliveCountMax=120 -i $Key -o StrictHostKeyChecking=accept-new "ubuntu@$Server" $remote
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
