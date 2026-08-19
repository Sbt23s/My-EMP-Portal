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
    # Message through a file, never through the command line.
    #
    # PowerShell 5.1 rebuilds the argument string when it calls a native exe,
    # and a message containing quotes or newlines comes out the other side as
    # several arguments -- so a perfectly good commit failed with
    # "pathspec did not match any file(s)" and the deploy went on to push and
    # rebuild nothing. -F takes the message verbatim, with nothing to mangle.
    $msgFile = Join-Path ([System.IO.Path]::GetTempPath()) ("ship-msg-" + [guid]::NewGuid().ToString('N') + ".txt")
    [System.IO.File]::WriteAllText($msgFile, $Message, (New-Object System.Text.UTF8Encoding($false)))
    git commit -F $msgFile | Out-Null
    $committed = ($LASTEXITCODE -eq 0)
    Remove-Item $msgFile -Force -ErrorAction SilentlyContinue
    if (-not $committed) { Die "Commit failed. Nothing was pushed or deployed." }
    Ok "committed: $($Message -split "`n" | Select-Object -First 1)"
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
echo "--- clearing leftovers from an interrupted deploy ---"
# Only the renamed corpses, never a healthy container.
#
# This used to force-remove hrportal-backend and hrportal-web outright. That
# turned every deploy into a Create rather than a Recreate, and Docker holds a
# name reservation for a moment after a removal -- so compose raced it and
# failed with "the container name is already in use" on a deploy that was
# otherwise fine. Compose recreates running containers correctly on its own;
# what it cannot clean up is the half-renamed container an interrupted run
# leaves behind, named like 3fa1c2d4e5f6_hrportal-backend. Remove only those.
#
# Exited, created or dead only -- never a running one. A retry once left the
# live backend carrying the renamed form of its own name, and a cleanup that
# matched on the name alone would have force-removed the running service on the
# next deploy -- causing the very Create race this is here to avoid.
for id in $(sudo docker ps -a --filter status=exited --filter status=created --filter status=dead             --format '{{.ID}} {{.Names}}' | grep -E '[0-9a-f]{8,}_hrportal-' | awk '{print $1}'); do
  echo "    removing leftover: $id"
  sudo docker rm -f "$id" >/dev/null 2>&1 || true
done
echo "--- deciding what to rebuild ---"
# Rebuild only the images whose source actually moved.
#
# All three were rebuilt on every deploy: a Maven build, an npm build and a pip
# install, minutes of work to ship a change that often touched none of them. The
# pull just told us exactly what moved, so ask it.
#
# ORIG_HEAD is where we were before the pull. Absent it, rebuild everything -
# guessing wrong in that direction only costs time, the other way ships nothing.
CHANGED=""
if [ -f .git/ORIG_HEAD ]; then
  CHANGED=$(git diff --name-only ORIG_HEAD HEAD 2>/dev/null || echo "")
fi

SERVICES=""
if [ -z "$CHANGED" ]; then
  echo "  cannot tell what changed - rebuilding everything"
  SERVICES="backend web analytics"
else
  echo "$CHANGED" | head -20 | sed "s/^/    /"
  case "$CHANGED" in *backend/*) SERVICES="$SERVICES backend";; esac
  case "$CHANGED" in *web/*) SERVICES="$SERVICES web";; esac
  case "$CHANGED" in *analytics-service/*) SERVICES="$SERVICES analytics";; esac
  # The compose file describes all of them, so a change there touches all of them.
  case "$CHANGED" in *docker-compose.prod.yml*) SERVICES="backend web analytics";; esac
fi

SERVICES=$(echo $SERVICES | xargs)

if [ -z "$SERVICES" ]; then
  echo "  nothing server-side changed - nothing to rebuild"
  exit 0
fi

echo "--- rebuilding: $SERVICES ---"
# One retry, because a name reservation that has not yet expired clears in
# seconds. A deploy whose images built fine should not need a human for that.
if ! sudo docker compose -f docker-compose.prod.yml up -d --build $SERVICES; then
  echo "--- up failed; clearing leftovers and retrying once ---"
  for id in $(sudo docker ps -a --filter status=exited --filter status=created --filter status=dead               --format '{{.ID}} {{.Names}}' | grep -E '[0-9a-f]{8,}_hrportal-' | awk '{print $1}'); do
    sudo docker rm -f "$id" >/dev/null 2>&1 || true
  done
  sleep 5
  sudo docker compose -f docker-compose.prod.yml up -d --build $SERVICES
fi

# Only wait on the backend if it was actually one of them.
case "$SERVICES" in
  *backend*) ;;
  *) echo "backend untouched - done"; exit 0;;
esac
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

# Strip the carriage returns before this leaves Windows.
#
# A here-string in a .ps1 saved with CRLF carries  on every line, and bash
# treats it as part of the last word: "set -e" becomes "set -e" (invalid
# option), "git pull --ff-only" becomes "--ff-only" (unknown option), and
# "do" is a syntax error. The script is correct; it was only ever the line
# endings, and the errors name none of that.
# Send it as base64, not as a command line.
#
# PowerShell re-parses a string before handing it to a native program, and it
# does not leave quotes alone: awk -v n="$n" '$2 ~ n"$"' arrived on the server
# as $2 ~ n$ - quotes gone, regex broken - and the parentheses in a bash case
# statement came apart the same way. Escaping each one is a game of whack-a-mole
# that the next edit restarts.
#
# Base64 is A-Z a-z 0-9 + / = and nothing else, so there is nothing left for
# PowerShell, ssh, or the remote shell to interpret. The script arrives byte for
# byte, whatever is in it. This also carries the CRLF fix along with it, since
# the bytes are stripped before encoding.
$remote  = $remote -replace "`r", ""
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($remote))

ssh -i $Key -o StrictHostKeyChecking=accept-new "ubuntu@$Server" "echo $encoded | base64 -d | bash"
if ($LASTEXITCODE -ne 0) { Die "The deploy failed. Read the output above." }

Step "Checking the live site"
$domain = "https://pixoushrportal.pixous.info"
# Probed with curl.exe rather than Invoke-WebRequest.
#
# The check used -SkipHttpErrorCheck, which only exists in PowerShell 7. On 5.1
# the parameter failed to bind, the call threw before any request went out, and
# both probes reported "unreachable" on every deploy -- including the healthy
# ones, which is worse than no check at all. curl reports a 401 as a result
# rather than an error, so the answer we expect needs no special handling.
$curl = Join-Path $env:SystemRoot "System32\curl.exe"
$site = (& $curl -s -o NUL -w "%{http_code}" --max-time 20 "$domain/")
$login = (& $curl -s -o NUL -w "%{http_code}" --max-time 20 -X POST "$domain/api/auth/login" `
          -H "Content-Type: application/json" -H "Origin: $domain" `
          -d '{\"username\":\"__probe__\",\"password\":\"__probe__\"}')
if (-not $site)  { $site = "unreachable" }
if (-not $login) { $login = "unreachable" }

Write-Host "    site  : $site    (want 200)"
Write-Host "    login : $login   (want 401 - reached the endpoint)"

if ("$site" -eq "200" -and "$login" -eq "401") {
  Write-Host "`nDeployed and healthy.  $domain`n" -ForegroundColor Green
} else {
  Write-Host "`nDeployed, but the checks above are not what they should be.`n" -ForegroundColor Yellow
}
