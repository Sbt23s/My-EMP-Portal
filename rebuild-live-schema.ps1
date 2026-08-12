<#
.SYNOPSIS
    Empties the live database so Flyway can build it from V1, with all its seed data.

.DESCRIPTION
    Baselining told Flyway that V1-V89 were already applied. That was true of the
    tables, but those migrations also carried DATA -- the roles, the permissions,
    the master data and the 'admin' account -- and none of it is here. The portal
    therefore starts but nobody can log in, and grafting the seed on by hand would
    still miss the permission rows that eleven later migrations add.

    Running every migration from the beginning is the only way to end up with the
    schema AND the data the code expects.

    This drops the tables. It is safe here ONLY because the database is empty, and
    it refuses to run if that stops being true: every table is counted first, and
    anything holding rows stops it dead.

    A full backup is taken before anything is dropped, regardless.

    AFTER THIS: start the backend. Flyway applies all 92 migrations to the empty
    schema, and the seeded login is  admin / Test1234@
#>
[CmdletBinding()]
param(
    [string]$DbHost   = "mysql1002.site4now.net",
    [int]   $DbPort   = 3306,
    [string]$Database = "db_ab2fe4_ems",
    [string]$User     = "ab2fe4_ems",
    [string]$Password,
    [string]$BackupDir = "$env:USERPROFILE\Documents\hr-port-backups",
    # Drop even though some tables hold rows. Requires typing the database name.
    [switch]$IHaveDataAndAcceptLosingIt
)

$ErrorActionPreference = "Continue"

function Step($m) { Write-Host "==> $m"   -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  !   $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "  !!  $m" -ForegroundColor Red; exit 1 }

$mysqlDir  = "C:\Program Files\MySQL\MySQL Server 8.0\bin"
$mysql     = Join-Path $mysqlDir "mysql.exe"
$mysqldump = Join-Path $mysqlDir "mysqldump.exe"
if (-not (Test-Path $mysql))     { Die "mysql.exe not found at $mysql" }
if (-not (Test-Path $mysqldump)) { Die "mysqldump.exe not found at $mysqldump" }

if (-not $Password) {
    $secure = Read-Host -Prompt "Database password" -AsSecureString
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
if (-not $Password) { Die "No password given." }

function RunSql([string]$sql, [switch]$Table) {
    $cli = @("-h", $DbHost, "-P", "$DbPort", "-u", $User, "-p$Password", "--connect-timeout=15")
    if ($Table) { $cli += "--table" } else { $cli += @("-N", "-B") }
    $cli += @("-e", $sql, $Database)
    $out = & $mysql @cli 2>&1 | Where-Object { $_ -notmatch "Using a password" }
    return @{ Ok = ($LASTEXITCODE -eq 0); Out = $out }
}

Write-Host ""
Write-Host "  REBUILD -- $User@$DbHost/$Database" -ForegroundColor Yellow
Write-Host ""

# ------------------------------------------------------- 0. backend must be off
Step "Checking nothing is connected"
$listening = @(Get-NetTCPConnection -LocalPort 7060 -State Listen -ErrorAction SilentlyContinue)
if ($listening.Count -gt 0) {
    Die "The backend is running (pid $($listening[0].OwningProcess)). Stop it first (Ctrl+C), then run this again."
}
Ok "Backend is not running"

$probe = RunSql "SELECT 1;"
if (-not $probe.Ok) {
    $probe.Out | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }
    Die "Cannot reach the database."
}

# ----------------------------------------------------------------- 1. backup
Step "Backing up before anything is dropped"
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }
$stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$dump  = Join-Path $BackupDir "$Database-BEFORE-REBUILD-$stamp.sql"
$attempts = @(
    @("--single-transaction","--routines","--triggers","--events"),
    @("--single-transaction","--triggers","--no-tablespaces"),
    @("--single-transaction","--no-tablespaces","--skip-triggers")
)
foreach ($flags in $attempts) {
    & $mysqldump -h $DbHost -P $DbPort -u $User -p"$Password" @flags `
        --result-file="$dump" $Database 2>&1 |
        Where-Object { $_ -notmatch "Using a password" } | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
    if ((Test-Path $dump) -and (Get-Item $dump).Length -gt 1024) { break }
    Remove-Item $dump -ErrorAction SilentlyContinue
}
if (-not (Test-Path $dump) -or (Get-Item $dump).Length -le 1024) {
    Die "Backup failed -- refusing to drop anything."
}
Ok "Backup: $dump  ($([math]::Round((Get-Item $dump).Length/1KB,1)) KB)"

# --------------------------------------------- 2. count rows in EVERY table
Step "Counting rows in every table -- this decides whether it is safe"

$tablesRes = RunSql "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name;"
$tables = @($tablesRes.Out | Where-Object { $_ -and $_.Trim() -ne "" })
if ($tables.Count -eq 0) {
    Ok "The schema has no tables at all -- nothing to drop"
} else {
    Write-Host "      $($tables.Count) tables" -ForegroundColor DarkGray
}

# information_schema.table_rows is an estimate for InnoDB and cannot be trusted
# for a decision like this, so every table is counted for real.
$union = ($tables | ForEach-Object { "SELECT '$_' AS t, COUNT(*) AS n FROM ``$_``" }) -join " UNION ALL "
$nonEmpty = @()
if ($tables.Count -gt 0) {
    $countsRes = RunSql "SELECT t, n FROM ($union) x WHERE n > 0 ORDER BY n DESC;"
    if (-not $countsRes.Ok) {
        $countsRes.Out | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }
        Die "Could not count rows -- refusing to drop anything."
    }
    $nonEmpty = @($countsRes.Out | Where-Object { $_ -match "\S" })
}

if ($nonEmpty.Count -eq 0) {
    Ok "Every table is empty. Nothing can be lost."
} else {
    Write-Host ""
    Warn "These tables HOLD ROWS:"
    $nonEmpty | ForEach-Object { Write-Host "      $_" -ForegroundColor Yellow }
    Write-Host ""
    if (-not $IHaveDataAndAcceptLosingIt) {
        Write-Host "  Stopping. This database is not empty after all." -ForegroundColor Red
        Write-Host "  Nothing has been changed. The backup is at:" -ForegroundColor Red
        Write-Host "    $dump"
        Write-Host ""
        Write-Host "  Look at what is in those tables before deciding. If you are sure," -ForegroundColor DarkGray
        Write-Host "  re-run with  -IHaveDataAndAcceptLosingIt" -ForegroundColor DarkGray
        exit 1
    }
    Warn "-IHaveDataAndAcceptLosingIt was given. Continuing."
}

# ------------------------------------------------------------- 3. confirm
Write-Host ""
Write-Host "  PLAN" -ForegroundColor Yellow
Write-Host "    1. DROP all $($tables.Count) tables in $Database"
Write-Host "    2. You start the backend; Flyway applies all 92 migrations to the empty schema"
Write-Host "    3. That recreates the schema AND the seed data:"
Write-Host "         roles, permissions, master data, holidays, designations"
Write-Host "         login:  admin / Test1234@   (also hr / Hr@123)"
Write-Host ""
Write-Host "    Only tables in '$Database' are touched. No other database, no server settings." -ForegroundColor DarkGray
Write-Host "    Backup: $dump" -ForegroundColor DarkGray
Write-Host ""
$answer = Read-Host "  Type the database name ($Database) to go ahead"
if ($answer -ne $Database) { Warn "Nothing was changed."; exit 0 }

# ---------------------------------------------------------------- 4. drop
Step "Dropping"
# Foreign keys make the order matter; switching the checks off for the duration
# means they can go in any order. The setting is per-session, so it dies with
# this connection and nothing about the server changes.
$dropList = ($tables | ForEach-Object { "``$_``" }) -join ", "
$sql = @"
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS $dropList;
SET FOREIGN_KEY_CHECKS = 1;
"@
$tmp = Join-Path $env:TEMP "hrport-rebuild.sql"
Set-Content -Path $tmp -Value $sql -Encoding utf8
$res = & $mysql -h $DbHost -P $DbPort -u $User -p"$Password" -e "source $tmp" $Database 2>&1 |
    Where-Object { $_ -notmatch "Using a password" }
$rc = $LASTEXITCODE
Remove-Item $tmp -ErrorAction SilentlyContinue
if ($rc -ne 0) {
    $res | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }
    Die "Drop failed. Backup is at $dump"
}
Ok "Dropped"

# --------------------------------------------------------------- 5. verify
Step "Verifying the schema is empty"
$left = RunSql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';"
if ($left.Out -eq "0") { Ok "0 tables remain -- Flyway will build from V1" }
else { Warn "$($left.Out) table(s) still there. Run this again." }

Write-Host ""
Write-Host "  NEXT: start the backend and let Flyway do the rest." -ForegroundColor Green
Write-Host "    .\run-live-db.ps1"
Write-Host ""
Write-Host "  It applies 92 migrations, so give it a few minutes. Expect:"
Write-Host "    Successfully applied 92 migrations to schema ``$Database``"
Write-Host ""
Write-Host "  Then log in at http://localhost:5174 with  admin / Test1234@" -ForegroundColor Green
Write-Host ""
Write-Host "  Backup if you need to go back: $dump" -ForegroundColor DarkGray
Write-Host ""
