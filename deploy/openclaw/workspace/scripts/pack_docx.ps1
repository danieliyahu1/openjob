param(
  [Parameter(Mandatory = $true)][string]$SourceDir,
  [Parameter(Mandatory = $true)][string]$OutDocx
)
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

# Resolve to a full path: a relative -SourceDir would otherwise make the
# entry-name substring below drop the wrong number of characters.
$root = (Resolve-Path -LiteralPath $SourceDir).ProviderPath.TrimEnd('\', '/')
if (-not (Test-Path -LiteralPath $root -PathType Container)) {
  throw "pack_docx: not a directory: $root"
}

$outFull = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutDocx)
if (Test-Path -LiteralPath $outFull) { Remove-Item -LiteralPath $outFull -Force }

$files = Get-ChildItem -LiteralPath $root -Recurse -File | ForEach-Object {
  $_.FullName.Substring($root.Length + 1).Replace('\', '/')
}

# [Content_Types].xml must be the first entry in an OPC package
$ordered = @()
$ct = $files | Where-Object { $_ -eq '[Content_Types].xml' }
if ($ct) { $ordered += $ct }
$ordered += $files | Where-Object { $_ -ne '[Content_Types].xml' }

$fs = [System.IO.File]::Open($outFull, [System.IO.FileMode]::CreateNew)
$zip = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create)
try {
  foreach ($rel in $ordered) {
    $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
    $es = $entry.Open()
    $bytes = [System.IO.File]::ReadAllBytes((Join-Path $root $rel))
    $es.Write($bytes, 0, $bytes.Length)
    $es.Close()
  }
} finally {
  $zip.Dispose()
  $fs.Dispose()
}
Write-Output ("PACKED=" + $outFull + " entries=" + $ordered.Count)
