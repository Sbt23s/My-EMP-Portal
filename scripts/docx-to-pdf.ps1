# Converts the client-submission .docx files in docs/downloads/ to PDF.
# Uses Microsoft Word (installed at C:\Program Files\Microsoft Office).
$ErrorActionPreference = "Stop"

$outDir = Join-Path (Split-Path $PSScriptRoot -Parent) "docs\downloads"
$files = @(
    "Pixous_HR_Requirements_v1.0.docx",
    "Pixous_HR_Unit_Testing_v1.0.docx",
    "Pixous_HR_API_List_v1.0.docx"
)

$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0

try {
    foreach ($f in $files) {
        $src = Join-Path $outDir $f
        $dst = Join-Path $outDir ($f -replace "\.docx$", ".pdf")
        $doc = $word.Documents.Open($src, $false, $true)  # read-only
        $doc.ExportAsFixedFormat($dst, 17)  # 17 = wdExportFormatPDF
        $doc.Close($false)
        Write-Output "Wrote $dst"
    }
} finally {
    $word.Quit()
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($word) | Out-Null
}
