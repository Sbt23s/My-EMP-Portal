<#
.SYNOPSIS
    Downloads a full backup of the live Pixous database.

.DESCRIPTION
    The hosted database is the only copy of the company's data, and the hosting
    account offers no restore point. This writes one to disk, over the network,
    from wherever you are -- no server access needed, only the database password.

    Run it before applying a migration, before a deployment, and before anything
    described as "fresh". A dump that exists is worth more than any amount of care.

    Nothing on the server is changed. It is a read.

    WHAT IS IN THE FILE: every employee's name, phone number, Aadhaar, bank
    details and salary, and every chat message. Treat it as you would a printed
    payroll -- do not put it in the repository, in a shared drive, or in email.
    It is written outside the project on purpose so it cannot be committed by
    accident.

.PARAMETER OutDir
    Where to write it. Defaults to a "hr-portal-backups" folder in your user
    profile, which is outside the repository.

.PARAMETER Password
    The database password. Prompted for if not given.

.EXAMPLE
    .\deploy\live-dump.ps1
#>
[CmdletBinding()]
param(
    [string]$DbHost = "mysql1002.site4now.net",
    [int]$DbPort = 3306,
    [string]$Database = "db_ab2fe4_ems",
    [string]$User = "ab2fe4_ems",
    [string]$Password,
    [string]$OutDir = (Join-Path $env:USERPROFILE "hr-portal-backups"),
    [string]$Container = "hrportal-mysql-local"
)

$ErrorActionPreference = "Continue"

function Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  !   $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "  !!  $m" -ForegroundColor Red; exit 1 }

if (-not $Password) {
    $secure = Read-Host -Prompt "Database password for $User@$DbHost" -AsSecureString
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
if (-not $Password) { Die "No password given." }

# mysqldump is run inside the local MySQL container, so nothing has to be
# installed on Windows. The container is only the client here; its own database
# is not touched.
Step "Checking the mysqldump client is available"
$running = docker ps --format "{{.Names}}"
if ($LASTEXITCODE -ne 0) { Die "Cannot talk to Docker. Is Docker Desktop running?" }
if ($running -notcontains $Container) {
    Die "Container '$Container' is not running -- it is used as the mysql client. Start it:  docker compose up -d mysql"
}
Ok "Using $Container as the client"

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$out = Join-Path $OutDir "$Database-$stamp.sql"

Step "Dumping $Database from $DbHost (a few minutes on a slow link)"
# --single-transaction so the dump is one consistent moment and no table is locked
# against the live portal while it runs. --skip-lock-tables because a hosting
# account is not granted LOCK TABLES. --no-tablespaces for the same reason.
$args = @(
    "exec", "-i", $Container, "mysqldump",
    "-h", $DbHost, "-P", "$DbPort", "-u", $User, "-p$Password",
    "--single-transaction", "--skip-lock-tables", "--no-tablespaces",
    "--routines", "--events", "--set-gtid-purged=OFF",
    "--default-character-set=utf8mb4",
    $Database
)
# Written straight through, so a large database never has to fit in memory.
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "docker"
$psi.Arguments = ($args | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join " "
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$proc = [System.Diagnostics.Process]::Start($psi)

$fs = [System.IO.File]::Create($out)
try { $proc.StandardOutput.BaseStream.CopyTo($fs) } finally { $fs.Dispose() }
$err = $proc.StandardError.ReadToEnd()
$proc.WaitForExit()

if ($proc.ExitCode -ne 0) {
    Remove-Item $out -ErrorAction SilentlyContinue
    Write-Host $err -ForegroundColor Red
    Die "mysqldump exited with $($proc.ExitCode). Nothing was written."
}

$size = (Get-Item $out).Length
# A dump that failed part-way still leaves a file. The trailer is the only honest
# proof it finished, so a truncated one is deleted rather than kept and trusted.
$tail = Get-Content $out -Tail 3 -ErrorAction SilentlyContinue
if ($size -lt 10KB -or ($tail -join "`n") -notmatch "Dump completed") {
    Remove-Item $out -ErrorAction SilentlyContinue
    Die "The dump is incomplete ($size bytes, no completion marker). Deleted -- do not rely on a partial backup."
}

Ok "$out"
Ok "$([math]::Round($size / 1MB, 2)) MB, $((Select-String -Path $out -Pattern '^CREATE TABLE' -AllMatches).Count) tables"

Write-Host ""
Write-Host "To load it into the local container instead of the live one:" -ForegroundColor DarkGray
Write-Host "  .\deploy\local-restore.ps1 -Dump `"$out`"" -ForegroundColor DarkGray
Write-Host "This file contains employee PII. Keep it off shared drives and out of git." -ForegroundColor Yellow
