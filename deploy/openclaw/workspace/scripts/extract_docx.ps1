param(
  [Parameter(Mandatory = $true)][string]$DocxPath,
  [Parameter(Mandatory = $true)][string]$OutXml
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($DocxPath)
try {
  $entry = $zip.GetEntry('word/document.xml')
  $reader = New-Object System.IO.StreamReader($entry.Open(), [Text.Encoding]::UTF8)
  try { $xml = $reader.ReadToEnd() } finally { $reader.Close() }
} finally {
  $zip.Dispose()
}
[IO.File]::WriteAllText($OutXml, $xml, [Text.Encoding]::UTF8)
Write-Output ("LEN=" + $xml.Length)
