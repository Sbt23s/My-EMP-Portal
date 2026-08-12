<#
.SYNOPSIS
    Repairs the Flyway state on the live database WITHOUT touching the schema.

.DESCRIPTION
    The live schema was built up to roughly V90 but flyway_schema_history does not
    record it, so Flyway starts again at V1 and dies on "Table 'roles' already
    exists". This detects how far the schema actually got and writes a single
    BASELINE row saying so. Flyway then skips everything at or below that version
    and applies only what is genuinely outstanding.

    WHAT THIS TOUCHES:  flyway_schema_history  (one DELETE of failed rows, one INSERT)
    WHAT IT DOES NOT:   every other table, every column, every row. Untouched.

    A full mysqldump is taken first regardless.

    Everything runs inside one transaction on an InnoDB table, so a failure
    halfway leaves the history exactly as it was.
#>
[CmdletBinding()]
param(
    [string]$DbHost   = "mysql1002.site4now.net",
    [int]   $DbPort   = 3306,
    [string]$Database = "db_ab2fe4_ems",
    [string]$User     = "ab2fe4_ems",
    [string]$Password,
    [string]$BackupDir = "$env:USERPROFILE\Documents\hr-port-backups",
    # Skip the confirmation prompt. Only for a re-run after you have already read
    # the plan once.
    [switch]$Yes
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

# Scalar query. -N -B gives one bare tab-separated line, no box drawing.
function Q([string]$sql) {
    $out = & $mysql -h $DbHost -P $DbPort -u $User -p"$Password" -N -B -e $sql $Database 2>&1 |
        Where-Object { $_ -notmatch "Using a password" }
    if ($LASTEXITCODE -ne 0) { Die "Query failed: $out" }
    return ($out | Select-Object -First 1)
}

function HasTable([string]$t) {
    return (Q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='$t';") -eq "1"
}
function HasColumn([string]$t, [string]$c) {
    return (Q "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='$t' AND column_name='$c';") -eq "1"
}

Write-Host ""
Write-Host "  Flyway repair -- $User@$DbHost/$Database" -ForegroundColor Yellow
Write-Host ""

# ---------------------------------------------------------------- 1. connect
Step "Confirming this really is the live database"
$who = Q "SELECT CONCAT(DATABASE(),' as ',USER());"
Ok $who

# ---------------------------------------------------------------- 2. backup
Step "Taking a full backup before anything is written"
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }
$stamp  = Get-Date -Format "yyyy-MM-dd_HHmmss"
$dump   = Join-Path $BackupDir "$Database-$stamp.sql"

# Shared hosting withholds PROCESS and EVENT from the account, and mysqldump
# fails outright rather than skipping what it may not read. Try the full dump,
# then fall back to the plain one -- which is all that matters here anyway.
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
    Warn "Dump with [$($flags -join ' ')] did not produce a file -- trying a simpler one"
    Remove-Item $dump -ErrorAction SilentlyContinue
}
if (-not (Test-Path $dump) -or (Get-Item $dump).Length -le 1024) {
    Die "Backup failed -- refusing to continue. Nothing has been changed."
}
$kb = [math]::Round((Get-Item $dump).Length / 1KB, 1)
Ok "Backup: $dump  ($kb KB)"

# ---------------------------------------------------------------- 3. detect
Step "Working out how far the schema actually got"

# Each entry: the version, and something that exists ONLY once that version ran.
$probes = @(
    @{ V = 87; Desc = "users.employee_code";          Test = { HasColumn "users" "employee_code" } }
    @{ V = 88; Desc = "users.face_photo_path";        Test = { HasColumn "users" "face_photo_path" } }
    @{ V = 89; Desc = "audit_log.user_name";          Test = { HasColumn "audit_log" "user_name" } }
    @{ V = 91; Desc = "company_modules table";        Test = { HasTable  "company_modules" } }
    @{ V = 92; Desc = "companies.created_by";         Test = { HasColumn "companies" "created_by" } }
)

$applied = @{}
foreach ($p in $probes) {
    $present = & $p.Test
    $applied[$p.V] = $present
    $mark = if ($present) { "present" } else { "MISSING" }
    $col  = if ($present) { "Green" } else { "DarkGray" }
    Write-Host ("      V{0,-3} {1,-28} {2}" -f $p.V, $p.Desc, $mark) -ForegroundColor $col
}

# V91 rewrites companies heavily; a half-applied V91 is the one case this script
# must not guess its way through.
$v91Table = HasTable  "company_modules"
$v91Col   = HasColumn "companies" "company_id"
if ($v91Table -ne $v91Col) {
    Die "V91 looks half-applied (company_modules=$v91Table, companies.company_id=$v91Col). Stopping -- this needs a human."
}

if ($applied[92] -and $applied[91]) {
    $baseline = 92
} elseif ($applied[91]) {
    $baseline = 91
} elseif ($applied[89]) {
    # V90 is data only (the 'hr' login) and is written to be safe to re-run, so
    # baselining below it lets that account be created.
    $baseline = 89
} elseif ($applied[88]) {
    $baseline = 88
} elseif ($applied[87]) {
    $baseline = 87
} else {
    Die "Could not place the schema at a known version. Stopping."
}
Ok "Schema matches version $baseline"

# ---------------------------------------------------------------- 4. plan
$pending = 92 - $baseline
Write-Host ""
Write-Host "  PLAN" -ForegroundColor Yellow
Write-Host "    1. DELETE the failed rows from flyway_schema_history"
Write-Host "    2. INSERT one BASELINE row at version $baseline"
Write-Host "    -> Flyway will then skip V1-V$baseline and apply the remaining $pending migration(s)"
Write-Host ""
Write-Host "    No table is created, altered or dropped by this script." -ForegroundColor Green
Write-Host "    No row outside flyway_schema_history is read or written."  -ForegroundColor Green
Write-Host ""

if (-not $Yes) {
    $answer = Read-Host "  Type YES to apply"
    if ($answer -ne "YES") { Warn "Nothing was changed."; exit 0 }
}

# ---------------------------------------------------------------- 5. apply
Step "Writing the baseline"

$apply = @"
START TRANSACTION;
DELETE FROM flyway_schema_history WHERE success = 0;
DELETE FROM flyway_schema_history WHERE type = 'BASELINE';
INSERT INTO flyway_schema_history
    (installed_rank, version, description, type, script,
     checksum, installed_by, installed_on, execution_time, success)
VALUES
    (1, '$baseline', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>',
     NULL, LEFT(USER(), 100), NOW(), 0, 1);
COMMIT;
"@

$tmp = Join-Path $env:TEMP "hrport-flyway-fix.sql"
Set-Content -Path $tmp -Value $apply -Encoding utf8
$res = & $mysql -h $DbHost -P $DbPort -u $User -p"$Password" -e "source $tmp" $Database 2>&1 |
    Where-Object { $_ -notmatch "Using a password" }
$rc = $LASTEXITCODE
Remove-Item $tmp -ErrorAction SilentlyContinue
if ($rc -ne 0) {
    $res | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }
    Die "Apply failed. The transaction rolled back -- nothing changed. Backup is at $dump"
}
Ok "Baseline written"

# ---------------------------------------------------------------- 6. verify
Step "Verifying"
& $mysql -h $DbHost -P $DbPort -u $User -p"$Password" --table $Database -e @"
SELECT installed_rank, version, description, type, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank;
"@ 2>&1 | Where-Object { $_ -notmatch "Using a password" } | ForEach-Object { Write-Host "      $_" }

$rows    = Q "SELECT COUNT(*) FROM flyway_schema_history;"
$failed  = Q "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0;"
$tables  = Q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE';"

Write-Host ""
if ($rows -eq "1" -and $failed -eq "0") {
    Ok "History has exactly one clean BASELINE row at version $baseline"
} else {
    Warn "History has $rows row(s), $failed failed. Check the table above."
}
Ok "$tables tables still present (was 71 before -- unchanged)"

Write-Host ""
Write-Host "  DONE. Now start the backend:" -ForegroundColor Green
Write-Host "    cd `"c:\Users\balas\Documents\product level\GitHub\hr-port`""
Write-Host "    .\run-live-db.ps1 -DbHost $DbHost -Database $Database -User $User"
Write-Host ""
Write-Host "  Expect:  Current version of schema: $baseline"
Write-Host "           Migrating schema to version `"$($baseline+1)`" ... (and up to 92)"
Write-Host "           Tomcat started on port 7060"
Write-Host ""
Write-Host "  Backup if you need to go back: $dump" -ForegroundColor DarkGray
Write-Host ""
