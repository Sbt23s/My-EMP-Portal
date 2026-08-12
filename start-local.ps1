<#
.SYNOPSIS
    Starts everything the portal needs locally, in the order it needs it.

.DESCRIPTION
    Four pieces, and they have to come up in sequence: the database first, then
    the backend against it, then the Python analytics service, then the web app.

    Deliberate choices worth knowing about:

    * The database container publishes 3307, not 3306 -- this machine already runs
      a MySQL of its own for another project and taking its port would break it.
      Every piece here is told 3307.

    * SMS is off. A local run fires the same scheduled reminders as the server,
      and testing should not text real people or spend Fast2SMS credits.

    * The analytics service runs from source rather than its Docker image. That
      image compiles dlib and pulls in PyTorch -- 4-5 GB and twenty minutes. Face
      recognition and OCR load on first use, so everything else starts in a
      second and those two endpoints report honestly if the libraries are absent.

.PARAMETER LocalDb
    Use the throwaway MySQL container instead of the real database.

    The default is the REAL one. That is deliberate: the portal is worked on
    against the company's actual employees, and a run that quietly used an empty
    local copy looked identical while showing nothing -- which wasted more time
    than the risk it was avoiding. Pass this when you want a database you can
    safely break.

.PARAMETER LiveDb
    Kept so existing habits and older notes still work. The live database is now
    the default, so this switch changes nothing.

.PARAMETER SkipPython
    Leave the analytics service alone. The portal works without it -- the
    dashboard hides the extra cards.

.PARAMETER WithSms
    Allow SMS to actually be sent. Off unless asked for.

.EXAMPLE
    .\start-local.ps1
    Everything against the real company database.

.EXAMPLE
    .\start-local.ps1 -LocalDb
    Everything against the throwaway container instead.
#>
[CmdletBinding()]
param(
    [switch]$LocalDb,
    [switch]$LiveDb,
    [switch]$SkipPython,
    [switch]$WithSms
)

# The real database unless the throwaway one was asked for by name. -LiveDb is
# accepted and ignored: it is what every earlier note and habit says to type.
$LiveDb = -not $LocalDb

# Deliberately Continue, not Stop. docker, npm and pip all write ordinary
# progress to stderr, and Windows PowerShell turns a native command's stderr into
# a terminating error record under Stop -- so the script died on "Container
# hrportal-mysql-local Running", which is success. Every step below checks its own
# outcome explicitly and calls Die when it matters.
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

$DB_PORT = 3307
$BACKEND_PORT = 7060
$WEB_PORT = 5174
$PY_PORT = 8082

function Step($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  OK   $m" -ForegroundColor Green }
function Note($m) { Write-Host "  ..   $m" -ForegroundColor DarkGray }
function Warn($m) { Write-Host "  !    $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "  !!   $m" -ForegroundColor Red; exit 1 }

function Test-Port([int]$port) {
    try {
        $c = New-Object Net.Sockets.TcpClient
        $c.Connect("127.0.0.1", $port); $c.Close(); return $true
    } catch { return $false }
}

function Test-Http([string]$url) {
    try { Invoke-WebRequest $url -TimeoutSec 8 -UseBasicParsing | Out-Null; return $true }
    catch { return $false }
}

# ---------------------------------------------------------------------------
# Which database everything is about to talk to.
#
# Decided here, once, and applied to both the backend and the analytics service --
# they used to be told separately, which is how one of them ends up reading the
# real payroll while the other reads an empty container.
# ---------------------------------------------------------------------------
$db = @{
    Host     = "localhost"
    Port     = "$DB_PORT"
    Name     = "nobile"
    User     = "root"
    Password = "root123"
    Params   = $null
    PoolMax  = $null
    PoolMin  = $null
    Label    = "local container"
}

if ($LiveDb) {
    # Read from backend\.env rather than from this file: this one is committed.
    $envFile = Join-Path $root "backend\.env"
    if (-not (Test-Path $envFile)) {
        Die ("-LiveDb needs backend\.env with the hosted database's details. " +
             "Copy backend\.env.example to backend\.env and fill in DB_PASSWORD.")
    }
    $vals = @{}
    Get-Content $envFile | ForEach-Object {
        # Split on the FIRST = only. DB_PARAMS contains = signs of its own, and
        # splitting on all of them silently truncated it to "useSSL".
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $vals[$Matches[1]] = $Matches[2].Trim()
        }
    }
    foreach ($k in @("DB_HOST", "DB_NAME", "DB_USER", "DB_PASSWORD")) {
        if (-not $vals[$k]) { Die "backend\.env is missing $k" }
    }
    $db.Host     = $vals["DB_HOST"]
    $db.Port     = if ($vals["DB_PORT"]) { $vals["DB_PORT"] } else { "3306" }
    $db.Name     = $vals["DB_NAME"]
    $db.User     = $vals["DB_USER"]
    $db.Password = $vals["DB_PASSWORD"]
    $db.Params   = $vals["DB_PARAMS"]
    $db.PoolMax  = $vals["DB_POOL_MAX"]
    $db.PoolMin  = $vals["DB_POOL_MIN"]
    $db.Label    = "THE LIVE COMPANY DATABASE"

    Write-Host ""
    Write-Host "  ###############################################################" -ForegroundColor Yellow
    Write-Host "  #  LIVE DATABASE. Every change is real and there is no undo.  #" -ForegroundColor Yellow
    Write-Host "  ###############################################################" -ForegroundColor Yellow
    Write-Host "     $($db.User)@$($db.Host)/$($db.Name)" -ForegroundColor Yellow
    Write-Host "     Back it up first:  .\deploy\live-dump.ps1" -ForegroundColor DarkGray
    Write-Host ""
}

# Applies the choice above to the current process, for whatever is started next.
function Set-DbEnv {
    $env:DB_HOST = $db.Host
    $env:DB_PORT = $db.Port
    $env:DB_NAME = $db.Name
    $env:DB_USER = $db.User
    $env:DB_PASSWORD = $db.Password
    # Cleared rather than left alone: a leftover value from an earlier -LiveDb run
    # in the same shell would otherwise follow a plain run to the local container.
    if ($db.Params)  { $env:DB_PARAMS = $db.Params }   else { Remove-Item Env:DB_PARAMS -ErrorAction SilentlyContinue }
    if ($db.PoolMax) { $env:DB_POOL_MAX = $db.PoolMax } else { Remove-Item Env:DB_POOL_MAX -ErrorAction SilentlyContinue }
    if ($db.PoolMin) { $env:DB_POOL_MIN = $db.PoolMin } else { Remove-Item Env:DB_POOL_MIN -ErrorAction SilentlyContinue }
}

# ---------------------------------------------------------------------------
Step "Docker"
# ---------------------------------------------------------------------------
docker ps --format "{{.Names}}" 2>$null | Out-Null
if (-not $?) {
    Note "The Docker daemon is not answering. Starting Docker Desktop..."
    $dd = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path $dd)) { Die "Docker Desktop is not installed at $dd" }
    Start-Process $dd
    for ($i = 1; $i -le 60; $i++) {
        Start-Sleep -Seconds 5
        docker ps --format "{{.Names}}" 2>$null | Out-Null
        if ($?) { break }
        if ($i -eq 60) { Die "Docker did not come up within five minutes." }
    }
}
Ok "Docker is up"

# ---------------------------------------------------------------------------
Step "Database, Redis and Kafka"
# ---------------------------------------------------------------------------
Set-Location $root
docker compose up -d | Out-Null

# MySQL is the only one anything waits on. A first-ever start initialises the
# data directory, which takes minutes on Docker Desktop rather than seconds.
#
# Read the health status Docker already computes rather than running mysqladmin
# in a loop: `docker exec` costs tens of seconds on this machine, so polling with
# it took longer than the thing being waited for.
Note "Waiting for MySQL on $DB_PORT (a first-ever start takes a few minutes)"
$ready = $false
for ($i = 1; $i -le 60; $i++) {
    $health = docker inspect hrportal-mysql-local --format "{{.State.Health.Status}}" 2>$null
    if ($health -eq "healthy") { $ready = $true; break }
    if ($health -eq "unhealthy" -and $i -gt 40) { break }
    Start-Sleep -Seconds 5
}
if (-not $ready) {
    # Fatal only when the portal is about to use it. Under -LiveDb the container is
    # wanted for the dump and restore scripts, which use it as a mysql client --
    # useful, but not worth refusing to start the portal over.
    if ($LiveDb) {
        Warn "The local MySQL container is not ready. live-dump.ps1 and local-restore.ps1 need it; the portal does not."
    } else {
        Die "MySQL did not become ready. Check: docker logs hrportal-mysql-local"
    }
} else {
    Ok "MySQL ready on localhost:$DB_PORT (database 'hr', root/root123)"
}

foreach ($svc in @("hrportal-redis-local", "hrportal-kafka-local")) {
    $st = docker inspect $svc --format "{{.State.Status}}" 2>$null
    if ($st -eq "running") { Ok "$svc running" } else { Warn "$svc is '$st'" }
}

# ---------------------------------------------------------------------------
Step "Backend"
# ---------------------------------------------------------------------------
if (Test-Http "http://localhost:$BACKEND_PORT/actuator/health") {
    Ok "Already running on $BACKEND_PORT"
} else {
    if (Test-Port $BACKEND_PORT) {
        Die "Something is on port $BACKEND_PORT but it is not the portal. Stop it first."
    }
    Set-DbEnv
    Note "Database: $($db.Label) -- $($db.User)@$($db.Host)/$($db.Name)"

    # Restart-on-save is off. Each restart builds a fresh connection pool, and a
    # pool whose context failed to start is not always closed -- against the hosted
    # account, which allows twenty connections in total and drops idle ones only
    # after eight hours, a morning of editing left it unable to connect at all.
    # That has happened; it took killing the sessions by hand to recover.
    $env:SPRING_DEVTOOLS_RESTART_ENABLED = "false"

    if ($WithSms) {
        Warn "SMS is ENABLED -- real messages will be sent"
        if ($LiveDb) { Warn "...to the REAL employee phone numbers in the live database." }
    } else {
        $env:FAST2SMS_ENABLED = "false"
        $env:TWILIO_ENABLED = "false"
        Note "SMS off (pass -WithSms to allow it)"
    }

    $log = Join-Path $env:TEMP "hr-backend.log"
    Remove-Item $log -ErrorAction SilentlyContinue
    Set-Location (Join-Path $root "backend")
    Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -NoNewWindow | Out-Null
    Note "Building and starting; Flyway migrations run on the way up"
    Note "Log: $log"

    $up = $false
    for ($i = 1; $i -le 60; $i++) {
        Start-Sleep -Seconds 10
        if (Test-Http "http://localhost:$BACKEND_PORT/actuator/health") { $up = $true; break }
    }
    if (-not $up) {
        Warn "The backend has not answered in ten minutes. Last lines:"
        Get-Content $log -Tail 15 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "     $_" -ForegroundColor DarkGray }
        Die "Backend did not start."
    }
    Ok "Backend healthy on http://localhost:$BACKEND_PORT"
}

# ---------------------------------------------------------------------------
Step "Analytics service (Python)"
# ---------------------------------------------------------------------------
if ($SkipPython) {
    Note "Skipped. The portal works without it; the dashboard hides the extra cards."
} elseif (Test-Http "http://localhost:$PY_PORT/") {
    Ok "Already running on $PY_PORT"
} else {
    $py = (Get-Command python -ErrorAction SilentlyContinue)
    if (-not $py) {
        Warn "Python is not on PATH -- skipping. The portal works without it."
    } else {
        Note "Installing the light dependencies (not dlib or PyTorch)"
        python -m pip install --quiet --disable-pip-version-check `
            fastapi uvicorn mysql-connector-python pydantic python-dotenv python-multipart | Out-Null

        # The same database the backend was just given. Told separately once, which
        # is how the dashboard's extra cards came from a different set of employees
        # than the rest of the page.
        Set-DbEnv

        $pylog = Join-Path $env:TEMP "hr-analytics.log"
        Remove-Item $pylog -ErrorAction SilentlyContinue
        Set-Location (Join-Path $root "analytics-service")
        Start-Process -FilePath "python" `
            -ArgumentList "-m","uvicorn","main:app","--host","127.0.0.1","--port","$PY_PORT" `
            -RedirectStandardOutput $pylog -RedirectStandardError "$pylog.err" `
            -NoNewWindow | Out-Null

        $up = $false
        for ($i = 1; $i -le 12; $i++) {
            Start-Sleep -Seconds 5
            if (Test-Http "http://localhost:$PY_PORT/") { $up = $true; break }
        }
        if ($up) {
            Ok "Analytics healthy on http://localhost:$PY_PORT"
        } else {
            Warn "It did not answer. The portal still works without it. Log: $pylog.err"
        }
    }
}

# ---------------------------------------------------------------------------
Step "Web app"
# ---------------------------------------------------------------------------
if (Test-Http "http://localhost:$WEB_PORT/") {
    Ok "Already running on $WEB_PORT"
} else {
    Set-Location (Join-Path $root "web")
    if (-not (Test-Path node_modules)) {
        Note "Installing npm dependencies (first run only)"
        npm install --silent | Out-Null
    }
    $weblog = Join-Path $env:TEMP "hr-web.log"
    Remove-Item $weblog -ErrorAction SilentlyContinue
    Start-Process -FilePath "cmd" -ArgumentList "/c","npm run dev > `"$weblog`" 2>&1" -NoNewWindow | Out-Null

    $up = $false
    for ($i = 1; $i -le 20; $i++) {
        Start-Sleep -Seconds 5
        if (Test-Http "http://localhost:$WEB_PORT/") { $up = $true; break }
    }
    if ($up) {
        Ok "Web app on http://localhost:$WEB_PORT"
    } else {
        Warn "It did not answer. Log: $weblog"
    }
}

# ---------------------------------------------------------------------------
Set-Location $root
Write-Host ""
Write-Host "---------------------------------------------" -ForegroundColor DarkGray
Write-Host " Everything is up" -ForegroundColor Green
Write-Host "---------------------------------------------" -ForegroundColor DarkGray
Write-Host ("  {0,-10} {1}" -f "Portal",    "http://localhost:$WEB_PORT")
Write-Host ("  {0,-10} {1}" -f "Backend",   "http://localhost:$BACKEND_PORT")
Write-Host ("  {0,-10} {1}" -f "Analytics", "http://localhost:$PY_PORT")
if ($LiveDb) {
    Write-Host ("  {0,-10} {1}" -f "Database", "$($db.Host)/$($db.Name)  -- LIVE, real data") -ForegroundColor Yellow
} else {
    Write-Host ("  {0,-10} {1}" -f "Database", "localhost:$DB_PORT  (hr / root / root123)")
}
Write-Host ("  {0,-10} {1}" -f "Kafka",     "localhost:9092")
Write-Host ""
if ($LiveDb) {
    Write-Host "  Back up before anything risky:  .\deploy\live-dump.ps1" -ForegroundColor DarkGray
} else {
    Write-Host "  Run against the real database:  .\start-local.ps1 -LiveDb" -ForegroundColor DarkGray
    Write-Host "  Load a dump locally:            .\deploy\local-restore.ps1 -Dump <file.sql.gz>" -ForegroundColor DarkGray
}
Write-Host "  Stop the containers:            docker compose down" -ForegroundColor DarkGray
Write-Host ""
