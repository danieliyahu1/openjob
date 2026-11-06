param(
  [Parameter(Mandatory = $true)][string]$DocxPath,
  [Parameter(Mandatory = $true)][string]$OutDir
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
New-Item -ItemType Directory -Path $OutDir | Out-Null
[System.IO.Compression.ZipFile]::ExtractToDirectory($DocxPath, $OutDir)
Write-Output ("EXTRACTED=" + $OutDir)
