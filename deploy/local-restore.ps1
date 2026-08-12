<#
.SYNOPSIS
    Loads a production database dump into the local MySQL container.

.DESCRIPTION
    Takes one of the .sql.gz backups the server writes before every deployment
    (~/backups/hr-predeploy-*.sql.gz) and restores it into the local database, so
    development runs against the same data as the live portal.

    The live server is never contacted by this script. Copy a dump down first --
    see docs/LOCAL_SETUP.md -- and point this at the file.

    The local database is dropped and recreated. Nothing on the server is touched
    by any part of this.

.PARAMETER Dump
    Path to the dump. Either .sql or .sql.gz.

.PARAMETER Database
    Which local database to load into. Defaults to "hr", which is what the
    backend connects to when no environment variables are set.

.PARAMETER Container
    The local MySQL container name. Defaults to the one docker-compose.yml starts.

.EXAMPLE
    .\deploy\local-restore.ps1 -Dump C:\Users\me\Downloads\hr-predeploy-20260803-041500.sql.gz
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Dump,

    [string]$Database = "hr",

    [string]$Container = "hrportal-mysql-local",

    [string]$RootPassword = "root123"
)

$ErrorActionPreference = "Stop"

function Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Die($m)  { Write-Host "  !!  $m" -ForegroundColor Red; exit 1 }

if (-not (Test-Path $Dump)) { Die "No such file: $Dump" }
$dumpFile = (Resolve-Path $Dump).Path
$sizeMb = [math]::Round((Get-Item $dumpFile).Length / 1MB, 1)
if ((Get-Item $dumpFile).Length -lt 1024) {
    Die "$dumpFile is only $((Get-Item $dumpFile).Length) bytes -- that is not a real dump."
}
Ok "Dump: $dumpFile ($sizeMb MB)"

Step "Checking the local MySQL container"
$running = docker ps --format "{{.Names}}" 2>$null
if ($LASTEXITCODE -ne 0) { Die "Cannot talk to Docker. Is Docker Desktop running?" }
if ($running -notcontains $Container) {
    Die "Container '$Container' is not running. Start it first:  docker compose up -d"
}
# Wait for it to actually accept connections; a freshly started MySQL does not.
for ($i = 1; $i -le 30; $i++) {
    docker exec $Container mysqladmin ping -h 127.0.0.1 -uroot -p"$RootPassword" --silent 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { break }
    if ($i -eq 30) { Die "MySQL did not become ready. Check:  docker logs $Container" }
    Start-Sleep -Seconds 2
}
Ok "MySQL is up"

# A dump this script has already loaded once would otherwise merge into the last
# one, leaving rows from both. Recreating is the only honest way to be sure.
Step "Recreating the '$Database' database"
$sql = "DROP DATABASE IF EXISTS ``$Database``; CREATE DATABASE ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
$sql | docker exec -i $Container mysql -uroot -p"$RootPassword"
if ($LASTEXITCODE -ne 0) { Die "Could not recreate the database." }
Ok "Empty '$Database' ready"

Step "Loading the dump (this takes a while on a large database)"
$isGz = $dumpFile.ToLower().EndsWith(".gz")
if ($isGz) {
    # Decompressed in .NET and streamed straight in, so no temporary copy of the
    # whole database is written to disk.
    $in = [System.IO.File]::OpenRead($dumpFile)
    try {
        $gz = New-Object System.IO.Compression.GZipStream($in, [System.IO.Compression.CompressionMode]::Decompress)
        try {
            $psi = New-Object System.Diagnostics.ProcessStartInfo
            $psi.FileName = "docker"
            $psi.Arguments = "exec -i $Container mysql -uroot -p$RootPassword --max-allowed-packet=256M $Database"
            $psi.RedirectStandardInput = $true
            $psi.UseShellExecute = $false
            $proc = [System.Diagnostics.Process]::Start($psi)
            $gz.CopyTo($proc.StandardInput.BaseStream)
            $proc.StandardInput.Close()
            $proc.WaitForExit()
            if ($proc.ExitCode -ne 0) { Die "mysql exited with $($proc.ExitCode). The dump may be for a different database." }
        } finally { $gz.Dispose() }
    } finally { $in.Dispose() }
} else {
    Get-Content -Raw $dumpFile | docker exec -i $Container mysql -uroot -p"$RootPassword" --max-allowed-packet=256M $Database
    if ($LASTEXITCODE -ne 0) { Die "mysql rejected the dump." }
}
Ok "Dump loaded"

Step "Checking what arrived"
$countSql = "SELECT CONCAT(COUNT(*), ' tables') FROM information_schema.tables WHERE table_schema = '$Database';"
$countSql | docker exec -i $Container mysql -uroot -p"$RootPassword" -N 2>$null | ForEach-Object { Ok $_ }
$userSql = "SELECT CONCAT(COUNT(*), ' employees') FROM ``$Database``.users;"
$userSql | docker exec -i $Container mysql -uroot -p"$RootPassword" -N 2>$null | ForEach-Object { Ok $_ }

Write-Host ""
Write-Host "Done. Now start the backend and the web app:" -ForegroundColor Green
Write-Host "  cd backend; mvn spring-boot:run"
Write-Host "  cd web;     npm run dev"
Write-Host ""
Write-Host "Flyway will bring the restored schema up to date on first start." -ForegroundColor DarkGray
Write-Host "Photos and attachments are NOT in the dump -- see docs/LOCAL_SETUP.md." -ForegroundColor DarkGray
