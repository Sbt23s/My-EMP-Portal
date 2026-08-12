<#
.SYNOPSIS
    Copies the local `hr` database -- the complete one, with the 62 employees --
    onto the live database.

.DESCRIPTION
    The live database was loaded from `nobile`, which has the tables but no rows:
    71 tables, zero users, and a flyway_schema_history that never recorded the
    migrations. That is why nobody can log in, and why Flyway kept trying to apply
    V1 over tables that already existed.

    The database that actually has the data is `hr` on this machine:
      74 tables, 62 users, 18 roles, 22 permissions, flyway 92/92 applied.

    This replaces the live contents with it -- structure, data and Flyway history
    together, so the live database ends up in exactly the state the code expects
    and Flyway has nothing left to do.

    ORDER OF WORK
      1. refuse to run if the backend is up
      2. back up the live database as it stands
      3. dump `hr` locally, and check the dump looks right BEFORE touching live
      4. drop the live tables (verified empty first)
      5. load the dump
      6. compare row counts on both sides and report

    Nothing is dropped until the dump exists and has been checked, and nothing is
    dropped at all if a live table turns out to hold rows.
#>
[CmdletBinding()]
param(
    [string]$SourceHost = "localhost",
    [int]   $SourcePort = 3306,
    [string]$SourceDb   = "hr",
    [string]$SourceUser = "root",
    [string]$SourcePass = "root123",

    [string]$DbHost   = "mysql1002.site4now.net",
    [int]   $DbPort   = 3306,
    [string]$Database = "db_ab2fe4_ems",
    [string]$User     = "ab2fe4_ems",
    [string]$Password,

    [string]$BackupDir = "$env:USERPROFILE\Documents\hr-port-backups",
    # Proceed even though live tables hold rows. Requires typing the database name.
    [switch]$OverwriteLiveData
)

$ErrorActionPreference = "Continue"

function Step($m) { Write-Host "==> $m"   -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  !   $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "  !!  $m" -ForegroundColor Red; exit 1 }

$bin       = "C:\Program Files\MySQL\MySQL Server 8.0\bin"
$mysql     = Join-Path $bin "mysql.exe"
$mysqldump = Join-Path $bin "mysqldump.exe"
if (-not (Test-Path $mysql))     { Die "mysql.exe not found at $mysql" }
if (-not (Test-Path $mysqldump)) { Die "mysqldump.exe not found at $mysqldump" }

if (-not $Password) {
    $secure = Read-Host -Prompt "LIVE database password" -AsSecureString
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
if (-not $Password) { Die "No password given." }

function Local1([string]$sql) {
    $o = & $mysql -h $SourceHost -P $SourcePort -u $SourceUser "-p$SourcePass" -N -B -e $sql $SourceDb 2>&1 |
         Where-Object { $_ -notmatch "Using a password" }
    return @{ Ok = ($LASTEXITCODE -eq 0); Out = ($o | Select-Object -First 1); All = $o }
}
function Live1([string]$sql, [switch]$Table) {
    $cli = @("-h", $DbHost, "-P", "$DbPort", "-u", $User, "-p$Password", "--connect-timeout=15")
    if ($Table) { $cli += "--table" } else { $cli += @("-N", "-B") }
    $cli += @("-e", $sql, $Database)
    $o = & $mysql @cli 2>&1 | Where-Object { $_ -notmatch "Using a password" }
    return @{ Ok = ($LASTEXITCODE -eq 0); Out = ($o | Select-Object -First 1); All = $o }
}

Write-Host ""
Write-Host "  COPY  $SourceUser@$SourceHost/$SourceDb  ->  $User@$DbHost/$Database" -ForegroundColor Yellow
Write-Host ""

# ------------------------------------------------------ 1. backend must be off
Step "Checking the backend is stopped"
$listening = @(Get-NetTCPConnection -LocalPort 7060 -State Listen -ErrorAction SilentlyContinue)
if ($listening.Count -gt 0) {
    Die "The backend is running (pid $($listening[0].OwningProcess)). Stop it with Ctrl+C first."
}
Ok "Not running"

# --------------------------------------------------------- 2. check the source
Step "Checking the source database"
$src = Local1 "SELECT CONCAT((SELECT COUNT(*) FROM users),'|',(SELECT COUNT(*) FROM roles),'|',(SELECT COUNT(*) FROM permissions),'|',(SELECT COUNT(*) FROM flyway_schema_history WHERE success=1));"
if (-not $src.Ok) { $src.All | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }; Die "Cannot read $SourceDb." }
$parts = $src.Out -split '\|'
$srcUsers = [int]$parts[0]; $srcRoles = [int]$parts[1]; $srcPerms = [int]$parts[2]; $srcFlyway = [int]$parts[3]
Write-Host "      users=$srcUsers  roles=$srcRoles  permissions=$srcPerms  migrations=$srcFlyway"
if ($srcUsers -eq 0) { Die "$SourceDb has no users. That is the empty one -- check the source." }
if ($srcRoles -eq 0) { Die "$SourceDb has no roles. Refusing to copy an unusable database." }
Ok "Source looks complete"

# ------------------------------------------------------------ 3. back up live
Step "Backing up the live database as it stands"
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }
$stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$liveBackup = Join-Path $BackupDir "$Database-BEFORE-COPY-$stamp.sql"
foreach ($flags in @(
    @("--single-transaction","--routines","--triggers","--events"),
    @("--single-transaction","--triggers","--no-tablespaces"),
    @("--single-transaction","--no-tablespaces","--skip-triggers"))) {
    & $mysqldump -h $DbHost -P $DbPort -u $User -p"$Password" @flags `
        --result-file="$liveBackup" $Database 2>&1 |
        Where-Object { $_ -notmatch "Using a password" } | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
    if ((Test-Path $liveBackup) -and (Get-Item $liveBackup).Length -gt 1024) { break }
    Remove-Item $liveBackup -ErrorAction SilentlyContinue
}
if (-not (Test-Path $liveBackup) -or (Get-Item $liveBackup).Length -le 1024) { Die "Live backup failed -- stopping." }
Ok "Backup: $liveBackup"

# --------------------------------------------------------- 4. dump the source
Step "Dumping $SourceDb"
$srcDump = Join-Path $BackupDir "$SourceDb-source-$stamp.sql"
# --skip-add-locks / --skip-disable-keys: both emit statements the hosting account
#   is not granted, and neither buys anything for a dataset this size.
# --no-tablespaces: needs the PROCESS privilege, which the account does not have.
# --set-gtid-purged=OFF: keeps a GTID header out of a dump going somewhere it
#   means nothing.
& $mysqldump -h $SourceHost -P $SourcePort -u $SourceUser -p"$SourcePass" `
    --single-transaction --default-character-set=utf8mb4 `
    --no-tablespaces --skip-add-locks --skip-disable-keys `
    --skip-triggers --set-gtid-purged=OFF `
    --result-file="$srcDump" $SourceDb 2>&1 |
    Where-Object { $_ -notmatch "Using a password" } | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
if (-not (Test-Path $srcDump) -or (Get-Item $srcDump).Length -le 1024) { Die "Dump of $SourceDb failed -- nothing on live was touched." }

# Check the dump BEFORE anything is dropped. A dump that is missing the data is
# the one way this could go badly, so it is caught here rather than after.
$dumpText   = Get-Content $srcDump -Raw
$dumpTables = ([regex]::Matches($dumpText, "(?m)^CREATE TABLE ")).Count
$dumpInserts= ([regex]::Matches($dumpText, "(?m)^INSERT INTO ")).Count
$hasUsers   = $dumpText -match "INSERT INTO ``users`` VALUES"
$kb = [math]::Round((Get-Item $srcDump).Length/1KB,1)
Write-Host "      $kb KB, $dumpTables CREATE TABLE, $dumpInserts INSERT"
if ($dumpTables -lt 70) { Die "Dump has only $dumpTables tables -- that is not right. Nothing on live was touched." }
if (-not $hasUsers)     { Die "Dump contains no rows for `users` -- refusing to use it." }
Ok "Dump: $srcDump"

# ------------------------------------------- 5. is live safe to overwrite?
Step "Checking the live database is empty"
$liveTablesRes = Live1 "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name;"
if (-not $liveTablesRes.Ok) { $liveTablesRes.All | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }; Die "Cannot read the live database." }
$liveTables = @($liveTablesRes.All | Where-Object { $_ -and $_.Trim() -ne "" })
Write-Host "      $($liveTables.Count) tables on live"

$nonEmpty = @()
if ($liveTables.Count -gt 0) {
    # Real counts. information_schema.table_rows is an estimate for InnoDB and is
    # not good enough to decide whether something may be dropped.
    $union = ($liveTables | ForEach-Object { "SELECT '$_' AS t, COUNT(*) AS n FROM ``$_``" }) -join " UNION ALL "
    $cRes = Live1 "SELECT CONCAT(t,' = ',n) FROM ($union) x WHERE n > 0 AND t <> 'flyway_schema_history' ORDER BY n DESC;"
    if (-not $cRes.Ok) { $cRes.All | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }; Die "Could not count live rows -- refusing to drop." }
    $nonEmpty = @($cRes.All | Where-Object { $_ -match "\S" })
}

if ($nonEmpty.Count -eq 0) {
    Ok "Live holds no data (flyway_schema_history aside). Nothing can be lost."
} else {
    Write-Host ""
    Warn "Live tables that HOLD ROWS:"
    $nonEmpty | ForEach-Object { Write-Host "      $_" -ForegroundColor Yellow }
    Write-Host ""
    if (-not $OverwriteLiveData) {
        Write-Host "  Stopping -- live is not empty after all. Nothing was changed." -ForegroundColor Red
        Write-Host "  Backup of live: $liveBackup"
        Write-Host "  Re-run with -OverwriteLiveData only if you are sure those rows can go." -ForegroundColor DarkGray
        exit 1
    }
    Warn "-OverwriteLiveData was given. Continuing."
}

# ---------------------------------------------------------------- 6. confirm
Write-Host ""
Write-Host "  PLAN" -ForegroundColor Yellow
Write-Host "    1. DROP the $($liveTables.Count) tables on live"
Write-Host "    2. Load $SourceDb into it: $dumpTables tables, $dumpInserts INSERT statements"
Write-Host "    3. Live ends up with $srcUsers users, $srcRoles roles, $srcPerms permissions,"
Write-Host "       and Flyway recorded at $srcFlyway migrations -- nothing left to apply"
Write-Host ""
Write-Host "    Login afterwards:  admin / Test1234@   (and hr / Hr@123)" -ForegroundColor Green
Write-Host "    Backup of live as it is now: $liveBackup" -ForegroundColor DarkGray
Write-Host ""
$answer = Read-Host "  Type the live database name ($Database) to go ahead"
if ($answer -ne $Database) { Warn "Nothing was changed."; exit 0 }

# ------------------------------------------------------------------ 7. drop
Step "Dropping the live tables"
if ($liveTables.Count -gt 0) {
    $dropList = ($liveTables | ForEach-Object { "``$_``" }) -join ", "
    # FOREIGN_KEY_CHECKS is a session setting, so it lapses with this connection
    # and nothing about the server is changed. Without it the drop order matters.
    $dropSql = "SET FOREIGN_KEY_CHECKS = 0;`nDROP TABLE IF EXISTS $dropList;`nSET FOREIGN_KEY_CHECKS = 1;"
    $tmpDrop = Join-Path $env:TEMP "hrport-drop.sql"
    Set-Content -Path $tmpDrop -Value $dropSql -Encoding utf8
    $o = & $mysql -h $DbHost -P $DbPort -u $User -p"$Password" -e "source $tmpDrop" $Database 2>&1 |
         Where-Object { $_ -notmatch "Using a password" }
    $rc = $LASTEXITCODE
    Remove-Item $tmpDrop -ErrorAction SilentlyContinue
    if ($rc -ne 0) { $o | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }; Die "Drop failed. Backup: $liveBackup" }
}
Ok "Dropped"

# ------------------------------------------------------------------ 8. load
Step "Loading $SourceDb into live (a few minutes over the network)"
$loadFile = Join-Path $env:TEMP "hrport-load-$stamp.sql"
# Foreign keys are switched off around the load so the tables can arrive in
# whatever order the dump lists them in.
"SET FOREIGN_KEY_CHECKS = 0;`nSET UNIQUE_CHECKS = 0;`n" | Set-Content -Path $loadFile -Encoding utf8
Get-Content $srcDump -Raw | Add-Content -Path $loadFile -Encoding utf8
"`nSET FOREIGN_KEY_CHECKS = 1;`nSET UNIQUE_CHECKS = 1;`n" | Add-Content -Path $loadFile -Encoding utf8

$loadOut = & $mysql -h $DbHost -P $DbPort -u $User -p"$Password" `
    --default-character-set=utf8mb4 --connect-timeout=30 `
    -e "source $loadFile" $Database 2>&1 | Where-Object { $_ -notmatch "Using a password" }
$rc = $LASTEXITCODE
Remove-Item $loadFile -ErrorAction SilentlyContinue
if ($rc -ne 0) {
    $loadOut | Select-Object -First 25 | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }
    Warn "Load reported errors. The live database is part-loaded."
    Warn "Restore what was there with:"
    Warn "  mysql -h $DbHost -u $User -p $Database < `"$liveBackup`""
    Die "Stopping so you can look."
}
Ok "Loaded"

# ---------------------------------------------------------------- 9. verify
Step "Comparing both sides"
$liveCheck = Live1 "SELECT CONCAT((SELECT COUNT(*) FROM users),'|',(SELECT COUNT(*) FROM roles),'|',(SELECT COUNT(*) FROM permissions),'|',(SELECT COUNT(*) FROM flyway_schema_history WHERE success=1),'|',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'));"
if (-not $liveCheck.Ok) { $liveCheck.All | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }; Die "Could not verify." }
$lp = $liveCheck.Out -split '\|'
$lUsers=[int]$lp[0]; $lRoles=[int]$lp[1]; $lPerms=[int]$lp[2]; $lFly=[int]$lp[3]; $lTabs=[int]$lp[4]

Write-Host ""
Write-Host ("      {0,-16} {1,8} {2,8}" -f "", "source", "live")
Write-Host ("      {0,-16} {1,8} {2,8}" -f "users",       $srcUsers,  $lUsers)
Write-Host ("      {0,-16} {1,8} {2,8}" -f "roles",       $srcRoles,  $lRoles)
Write-Host ("      {0,-16} {1,8} {2,8}" -f "permissions", $srcPerms,  $lPerms)
Write-Host ("      {0,-16} {1,8} {2,8}" -f "migrations",  $srcFlyway, $lFly)
Write-Host ("      {0,-16} {1,8} {2,8}" -f "tables",      $dumpTables,$lTabs)
Write-Host ""

$bad = @()
if ($lUsers -ne $srcUsers) { $bad += "users" }
if ($lRoles -ne $srcRoles) { $bad += "roles" }
if ($lPerms -ne $srcPerms) { $bad += "permissions" }
if ($lFly   -ne $srcFlyway){ $bad += "migrations" }

if ($bad.Count -eq 0) {
    Ok "Both sides match"
    Write-Host ""
    Write-Host "  DONE. Start the backend:" -ForegroundColor Green
    Write-Host "    .\run-live-db.ps1"
    Write-Host ""
    Write-Host "  Flyway should say:  Schema is up to date. No migration necessary."
    Write-Host "  Then log in at http://localhost:5174 with  admin / Test1234@" -ForegroundColor Green
} else {
    Warn "These do not match: $($bad -join ', ')"
    Warn "Look before starting the backend. Backup of the old live: $liveBackup"
}
Write-Host ""
Write-Host "  Files kept:" -ForegroundColor DarkGray
Write-Host "    live before copy : $liveBackup" -ForegroundColor DarkGray
Write-Host "    source dump      : $srcDump" -ForegroundColor DarkGray
Write-Host ""
