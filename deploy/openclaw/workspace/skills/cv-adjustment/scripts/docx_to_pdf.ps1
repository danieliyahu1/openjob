# Convert a .docx to PDF using Word automation (Windows).
# Usage:
#   powershell -File docx_to_pdf.ps1 -In "C:\path\resume.docx" -Out "C:\path\resume.pdf"
#
# Requires Microsoft Word installed. Prints the page count so you can sanity-check
# that the tailored CV fits the intended length.
param(
    [Parameter(Mandatory = $true)][string]$In,
    [Parameter(Mandatory = $true)][string]$Out
)

$word = New-Object -ComObject Word.Application
$word.Visible = $false
try {
    $doc = $word.Documents.Open($In, $false, $true)
    $pages = $doc.ComputeStatistics(2)  # 2 = wdStatisticPages
    $doc.SaveAs([ref]$Out, [ref]17)     # 17 = wdFormatPDF
    $doc.Close($false)
    Write-Output "PAGES: $pages"
    Write-Output "PDF: $Out"
}
finally {
    $word.Quit()
}
