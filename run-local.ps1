<#
.SYNOPSIS
    Starts just the backend, against the real company database.

.DESCRIPTION
    Supplies the environment the backend needs and starts Spring Boot. Nothing
    else -- for the whole stack in one command, use start-local.ps1.

    THE DATABASE IS THE REAL ONE by default, read from backend\.env. Every change
    is a change for everybody and there is no undo; back it up first with
    .\deploy\live-dump.ps1. Pass -LocalDb for the throwaway container instead.

    SMS is switched off. This fires the same scheduled reminders as the server,
    and against the real database those reminders reach real phone numbers.

.PARAMETER LocalDb
    Use the throwaway MySQL container on 3307 instead of the real database.

.PARAMETER DbPort
    Host port the local MySQL container is published on, when -LocalDb is used.
    Matches LOCAL_DB_PORT in docker-compose.yml.

.PARAMETER WithSms
    Allow SMS to actually be sent. Off unless you ask for it.

.EXAMPLE
    .\run-local.ps1
#>
[CmdletBinding()]
param(
    [switch]$LocalDb,
    [int]$DbPort = 3307,
    [switch]$WithSms
)

# Continue, not Stop. docker writes ordinary progress to stderr, and Windows
# PowerShell turns a native command's stderr into a terminating error under Stop --
# which killed this script on messages that meant success. Each step below checks
# its own outcome and calls Die when it matters.
$ErrorActionPreference = "Continue"

function Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Die($m)  { Write-Host "  !!  $m" -ForegroundColor Red; exit 1 }

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# The dev profile carries its own JWT secret, so only the database has to be set.
if ($LocalDb) {
    Step "Checking the local database is up"
    docker ps --format "{{.Names}}" 2>$null | Out-Null
    if (-not $?) { Die "Cannot talk to Docker. Start Docker Desktop, then try again." }
    if ((docker ps --format "{{.Names}}") -notcontains "hrportal-mysql-local") {
        Die "The database is not running. Start it first:  docker compose up -d"
    }
    for ($i = 1; $i -le 30; $i++) {
        docker exec hrportal-mysql-local mysqladmin ping -h 127.0.0.1 -uroot -proot123 --silent 2>$null | Out-Null
        if ($?) { break }
        if ($i -eq 30) { Die "MySQL is running but not accepting connections. docker logs hrportal-mysql-local" }
        Start-Sleep -Seconds 2
    }
    Write-Host "  OK  MySQL on localhost:$DbPort" -ForegroundColor Green

    $env:DB_HOST = "localhost"
    $env:DB_PORT = "$DbPort"
    $env:DB_NAME = "nobile"
    $env:DB_USER = "root"
    $env:DB_PASSWORD = "root123"
    Remove-Item Env:DB_PARAMS, Env:DB_POOL_MAX, Env:DB_POOL_MIN -ErrorAction SilentlyContinue
} else {
    # Read from backend\.env rather than written here: this file is committed and
    # that one is not.
    $envFile = Join-Path $root "backend\.env"
    if (-not (Test-Path $envFile)) {
        Die "backend\.env is missing -- it holds the real database details. See HANDOFF.md, or pass -LocalDb."
    }
    $vals = @{}
    Get-Content $envFile | ForEach-Object {
        # First = only: DB_PARAMS contains = signs of its own, and splitting on all
        # of them silently truncated it to "useSSL".
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { $vals[$Matches[1]] = $Matches[2].Trim() }
    }
    foreach ($k in @("DB_HOST","DB_NAME","DB_USER","DB_PASSWORD")) {
        if (-not $vals[$k]) { Die "backend\.env is missing $k" }
    }
    $env:DB_HOST = $vals["DB_HOST"]
    $env:DB_PORT = $(if ($vals["DB_PORT"]) { $vals["DB_PORT"] } else { "3306" })
    $env:DB_NAME = $vals["DB_NAME"]
    $env:DB_USER = $vals["DB_USER"]
    $env:DB_PASSWORD = $vals["DB_PASSWORD"]
    if ($vals["DB_PARAMS"])   { $env:DB_PARAMS = $vals["DB_PARAMS"] }
    if ($vals["DB_POOL_MAX"]) { $env:DB_POOL_MAX = $vals["DB_POOL_MAX"] }
    if ($vals["DB_POOL_MIN"]) { $env:DB_POOL_MIN = $vals["DB_POOL_MIN"] }

    # Restart-on-save builds a fresh pool each time and does not always close the
    # old one. The account allows twenty connections and the server keeps idle ones
    # for eight hours, so an afternoon of editing once left the portal unable to
    # reach its own database until the sessions were killed by hand.
    $env:SPRING_DEVTOOLS_RESTART_ENABLED = "false"

    Write-Host ""
    Write-Host "  LIVE DATABASE -- every change is real, and there is no undo." -ForegroundColor Yellow
    Write-Host "  $($env:DB_USER)@$($env:DB_HOST)/$($env:DB_NAME)" -ForegroundColor Yellow
    Write-Host "  Back it up first:  .\deploy\live-dump.ps1     (or pass -LocalDb)" -ForegroundColor DarkGray
    Write-Host ""
}

if ($WithSms) {
    Write-Host "  !   SMS is ENABLED -- real messages will be sent." -ForegroundColor Yellow
} else {
    $env:FAST2SMS_ENABLED = "false"
    $env:TWILIO_ENABLED = "false"
    Write-Host "  OK  SMS off (pass -WithSms to allow it)" -ForegroundColor Green
}

Step "Starting the backend on http://localhost:7060"
Write-Host "  Then, in another terminal:  cd web; npm run dev   ->  http://localhost:5174" -ForegroundColor DarkGray
Write-Host ""

Set-Location (Join-Path $root "backend")
mvn spring-boot:run
