<#
.SYNOPSIS
    Frees the live MySQL account's connection slots by ending only its own idle
    sessions.

.DESCRIPTION
    The hosting account allows 20 connections IN TOTAL. A crashed backend, or a
    MySQL Workbench window left open, leaves sessions behind that the server keeps
    for interactive_timeout -- eight hours there. Once twenty have piled up nothing
    can connect at all, including the portal itself.

    This grabs one connection and ends the sessions that are doing nothing.

    WHAT IT ENDS:      sessions of this same account whose Command is 'Sleep'
                       (connected, idle, running no query)
    WHAT IT LEAVES:    anything actually executing (Query, Execute, ...), and its
                       own connection

    KILL closes a connection. It does not touch data, and an idle session by
    definition has no transaction in flight to interrupt.
#>
[CmdletBinding()]
param(
    [string]$DbHost   = "mysql1002.site4now.net",
    [int]   $DbPort   = 3306,
    [string]$Database = "db_ab2fe4_ems",
    [string]$User     = "ab2fe4_ems",
    [string]$Password,
    # Also end sessions that are running something. Off by default -- that can
    # interrupt real work.
    [switch]$IncludeBusy,
    # How long to keep retrying the first connection. With the account full, the
    # first attempt usually fails and a slot frees within a minute or two.
    [int]$RetrySeconds = 120
)

$ErrorActionPreference = "Continue"

function Step($m) { Write-Host "==> $m"   -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  !   $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "  !!  $m" -ForegroundColor Red; exit 1 }

$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if (-not (Test-Path $mysql)) { Die "mysql.exe not found at $mysql" }

if (-not $Password) {
    $secure = Read-Host -Prompt "Database password" -AsSecureString
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
if (-not $Password) { Die "No password given." }

function RunSql([string]$sql, [switch]$Table) {
    $cli = @("-h", $DbHost, "-P", "$DbPort", "-u", $User, "-p$Password", "--connect-timeout=10")
    if ($Table) { $cli += "--table" } else { $cli += @("-N", "-B") }
    $cli += @("-e", $sql, $Database)
    $out = & $mysql @cli 2>&1 | Where-Object { $_ -notmatch "Using a password" }
    return @{ Ok = ($LASTEXITCODE -eq 0); Out = $out }
}

Write-Host ""
Write-Host "  Freeing connection slots -- $User@$DbHost" -ForegroundColor Yellow
Write-Host ""

# ------------------------------------------------------------- 0. local tidy
Step "Checking what on THIS machine is holding the account open"
$liveIps = @((Resolve-DnsName $DbHost -Type A -ErrorAction SilentlyContinue |
              Where-Object { $_.IPAddress }).IPAddress)
$holders = @()
foreach ($ip in $liveIps) {
    $holders += @(Get-NetTCPConnection -RemoteAddress $ip -State Established -ErrorAction SilentlyContinue)
}
if ($holders.Count -eq 0) {
    Ok "Nothing here has an open connection -- the sessions are server-side leftovers"
} else {
    $holders | Group-Object OwningProcess | ForEach-Object {
        $pr = Get-Process -Id $_.Name -ErrorAction SilentlyContinue
        $nm = if ($pr) { $pr.ProcessName } else { "<gone>" }
        Write-Host ("      pid {0,-7} x{1,-3} {2}" -f $_.Name, $_.Count, $nm)
    }
    Warn "Close MySQL Workbench and stop any backend before continuing -- otherwise"
    Warn "they reconnect and fill the slots straight back up."
}

# ------------------------------------------------- 1. get in, retrying if full
Step "Getting one connection (the account is full, so this may take a moment)"
$deadline = (Get-Date).AddSeconds($RetrySeconds)
$got = $false
$attempt = 0
while ((Get-Date) -lt $deadline) {
    $attempt++
    $r = RunSql "SELECT CONNECTION_ID();"
    if ($r.Ok) { $got = $true; Ok "In (attempt $attempt)"; break }
    if ($r.Out -match "max_user_connections|Too many connections") {
        Write-Host "      attempt $attempt -- still full, waiting 10s" -ForegroundColor DarkGray
        Start-Sleep -Seconds 10
    } else {
        $r.Out | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }
        Die "Could not connect, and not because the account is full."
    }
}
if (-not $got) {
    Warn "Still full after $RetrySeconds seconds."
    Warn "Close MySQL Workbench completely, stop every backend, then run this again."
    Die "Giving up without changing anything."
}

# --------------------------------------------------------- 2. show what's open
Step "Sessions currently open on this account"
$show = RunSql "SELECT id, host, db, command, time AS idle_seconds, LEFT(IFNULL(state,''),20) AS state FROM information_schema.processlist WHERE user = '$User' ORDER BY command, time DESC;" -Table
$show.Out | ForEach-Object { Write-Host "      $_" }

$filter = if ($IncludeBusy) { "" } else { "AND command = 'Sleep' " }
$idsRaw = RunSql "SELECT id FROM information_schema.processlist WHERE user = '$User' $filter AND id <> CONNECTION_ID();"
$ids = @($idsRaw.Out | Where-Object { $_ -match '^\d+$' })

Write-Host ""
if ($ids.Count -eq 0) {
    Ok "No idle session to end. Nothing to do."
    Write-Host ""
    Write-Host "  If it is still refusing connections, something outside this machine" -ForegroundColor DarkGray
    Write-Host "  is holding them -- a deployed copy of the portal, most likely." -ForegroundColor DarkGray
    exit 0
}
Write-Host "  $($ids.Count) idle session(s) to end: $($ids -join ', ')" -ForegroundColor Yellow
if ($IncludeBusy) { Warn "-IncludeBusy is on: sessions running a query will be ended too." }
Write-Host ""
$answer = Read-Host "  Type YES to end them"
if ($answer -ne "YES") { Warn "Nothing was changed."; exit 0 }

# ----------------------------------------------------------------- 3. kill
Step "Ending them"
# One KILL per statement. A session that timed out on its own between the list
# and now makes KILL error; that is fine and is not worth stopping for.
$killed = 0
foreach ($id in $ids) {
    $k = RunSql "KILL $id;"
    if ($k.Ok) { $killed++ } else { Write-Host "      $id was already gone" -ForegroundColor DarkGray }
}
Ok "$killed session(s) ended"

# ----------------------------------------------------------------- 4. verify
Step "What is left"
$after = RunSql "SELECT id, host, command, time AS idle_seconds FROM information_schema.processlist WHERE user = '$User' ORDER BY time DESC;" -Table
$after.Out | ForEach-Object { Write-Host "      $_" }

$cnt = RunSql "SELECT COUNT(*) FROM information_schema.processlist WHERE user = '$User';"
Write-Host ""
Ok "$($cnt.Out) session(s) now open, out of 20 allowed"
Write-Host ""
Write-Host "  You can start the backend again:" -ForegroundColor Green
Write-Host "    .\run-live-db.ps1 -DbHost $DbHost -Database $Database -User $User"
Write-Host ""
Write-Host "  Keeping it from happening again:" -ForegroundColor DarkGray
Write-Host "    - close MySQL Workbench when you are done with it (its sessions last 8 hours)" -ForegroundColor DarkGray
Write-Host "    - stop the backend with Ctrl+C rather than closing the window" -ForegroundColor DarkGray
Write-Host "    - never run two backends against this database at once" -ForegroundColor DarkGray
Write-Host ""
