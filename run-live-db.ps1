<#
.SYNOPSIS
    Runs the backend against the real, live Pixous database.

.DESCRIPTION
    run-local.ps1 starts the backend against the throwaway MySQL container. This
    starts it against the hosted database the portal actually uses, so what you see
    in the browser is the real employees, the real attendance and the real payroll.

    Read that twice before using it. There is no undo: a record deleted here is
    deleted for everybody, and "Fresh Start" would wipe the company. Take a dump
    first if you are about to do anything you might regret --
    .\deploy\live-dump.ps1 does exactly that.

    Redis is used as the cache. It is optional: if there is no Redis on
    REDIS_HOST the backend caches in its own memory instead and says so in the log.

.PARAMETER Password
    The database password. Prompted for if not given, so it does not have to sit
    in your shell history or in this file.

.PARAMETER SkipMigrate
    Start with Flyway disabled. Use this to look at the data without applying any
    pending migration to the live schema.

.EXAMPLE
    .\run-live-db.ps1
#>
[CmdletBinding()]
param(
    [string]$DbHost = "mysql1002.site4now.net",
    [int]$DbPort = 3306,
    [string]$Database = "db_ab2fe4_ems",
    [string]$User = "ab2fe4_ems",
    [string]$Password,
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [switch]$SkipMigrate
)

# Native tools write progress to stderr, and under "Stop" PowerShell 5.1 turns that
# into a terminating error. Failures are checked explicitly instead.
$ErrorActionPreference = "Continue"

function Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  !   $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "  !!  $m" -ForegroundColor Red; exit 1 }

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host ""
Write-Host "  THIS IS THE LIVE DATABASE. Every change is real." -ForegroundColor Yellow
Write-Host "  $User@$DbHost/$Database" -ForegroundColor Yellow
Write-Host ""

# Name and port first, before anything is typed. A host that does not resolve --
# a VPN switching over, the wifi dropping for a moment -- surfaces from the JVM as
# two hundred lines of bean-creation failure with UnknownHostException buried at
# the very bottom, which reads like the application is broken when the only thing
# wrong is the network. Two seconds here says so in one line instead.
Step "Checking $DbHost can be reached"
$ips = @()
try { $ips = @((Resolve-DnsName $DbHost -Type A -ErrorAction Stop | Where-Object { $_.IPAddress }).IPAddress) } catch { }
if ($ips.Count -eq 0) {
    Warn "$DbHost does not resolve. This is DNS, not the database."
    Warn "Check the network -- a VPN turning on or off is the usual cause -- then try again."
    Die "Not starting."
}
Ok "$DbHost -> $($ips -join ', ')"

$sock = New-Object System.Net.Sockets.TcpClient
$open = $false
try { $open = $sock.ConnectAsync($DbHost, $DbPort).Wait(8000) } catch { }
$sock.Close()
if (-not $open) {
    Warn "Port $DbPort on $DbHost did not answer within 8 seconds."
    Warn "The host resolves, so this is a firewall, a VPN, or the server being down."
    Die "Not starting."
}
Ok "Port $DbPort answers"

if (-not $Password) {
    $secure = Read-Host -Prompt "Database password" -AsSecureString
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
if (-not $Password) { Die "No password given." }

Step "Checking the database answers"
# An installed mysql client if there is one, otherwise the one inside the local
# container. Reaching for the container first meant this check could never pass
# without Docker running -- which has nothing to do with the hosted database, and
# reported a connection problem where there was none.
$localMysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if (Test-Path $localMysql) {
    $probe = & $localMysql -h $DbHost -P $DbPort -u $User -p"$Password" -N --connect-timeout=10 `
        -e "SELECT 'ok';" $Database 2>&1
} elseif ((Get-Command docker -ErrorAction SilentlyContinue) -and
          (docker ps --filter "name=hrportal-mysql-local" --format "{{.Names}}" 2>$null)) {
    $probe = docker exec hrportal-mysql-local mysql -h $DbHost -P $DbPort -u $User -p"$Password" -N `
        -e "SELECT 'ok';" $Database 2>&1
} else {
    $probe = "no mysql client available to check with"
}
if ($probe -notmatch "ok") {
    Warn "Could not confirm the connection:"
    $probe | Where-Object { $_ -notmatch "Using a password" } | ForEach-Object { Write-Host "      $_" }
    # The account allows twenty connections in TOTAL. A Workbench window left open
    # or a backend that was closed rather than stopped keeps its share for eight
    # hours, and once twenty have piled up nothing can get in at all.
    if ($probe -match "max_user_connections|Too many connections") {
        Warn "The account is out of connections. Free them with:  .\free-live-connections.ps1"
        Die "Not starting -- the backend would only fail the same way."
    }
    Warn "Starting anyway -- the backend reports the real reason if it cannot connect."
} else {
    Ok "Database reachable"
}

Step "Checking Redis"
$redisUp = $false
try {
    $t = New-Object System.Net.Sockets.TcpClient
    if ($t.ConnectAsync($RedisHost, $RedisPort).Wait(1500)) { $redisUp = $true }
    $t.Close()
} catch { }
if ($redisUp) {
    Ok "Redis on ${RedisHost}:${RedisPort} -- master data and settings will be cached there"
} else {
    Warn "No Redis on ${RedisHost}:${RedisPort}. The backend will cache in its own memory."
    Warn "Start it with:  docker compose up -d redis"
}

$env:DB_HOST = $DbHost
$env:DB_PORT = "$DbPort"
$env:DB_NAME = $Database
$env:DB_USER = $User
$env:DB_PASSWORD = $Password
# Deliberately WITHOUT createDatabaseIfNotExist: this account has no privilege to
# create a database, and asking the server to do it on every connect would be
# refused -- so the app would fail to start against a database that already exists.
$env:DB_PARAMS = "useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true"
# The account allows 20 connections in total. Six leaves room for a second copy
# during a restart, and for a mysql client somebody left open.
$env:DB_POOL_MAX = "6"
$env:DB_POOL_MIN = "1"

$env:REDIS_HOST = $RedisHost
$env:REDIS_PORT = "$RedisPort"

# Automatic restart-on-save is OFF against the live database, and this is not a
# matter of taste. Each restart builds a fresh connection pool, and a pool whose
# context failed to start is not always closed -- so a morning of editing files
# leaves twenty abandoned sessions behind. The account allows twenty in total, and
# the server's interactive_timeout is eight hours, so nothing clears them: the
# portal then cannot reach its own database until somebody kills them by hand.
# That has already happened once. Restart the script instead.
$env:SPRING_DEVTOOLS_RESTART_ENABLED = "false"

# Real phone numbers are in this database. Sending to them by accident while
# testing is not recoverable, so SMS is off and has to be turned on deliberately.
$env:FAST2SMS_ENABLED = "false"
$env:TWILIO_ENABLED = "false"
Ok "SMS is off -- no messages will reach real employees"

if ($SkipMigrate) {
    $env:SPRING_FLYWAY_ENABLED = "false"
    Warn "Flyway is off. The schema is left exactly as it is."
} else {
    $env:SPRING_FLYWAY_ENABLED = "true"
}

Step "Starting the backend on http://localhost:7060"
Set-Location (Join-Path $root "backend")
mvn spring-boot:run
