param(
  [Parameter(Mandatory = $true)]
  [string]$InPath,
  [Parameter(Mandatory = $true)]
  [string]$OutPath
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $InPath)) {
  throw "Input not found: $InPath"
}

$word = $null
$doc = $null
try {
  $word = New-Object -ComObject Word.Application
  $word.Visible = $false
  # Open(FileName, ConfirmConversions, ReadOnly)
  $doc = $word.Documents.Open($InPath, $false, $true)

  # wdFormatText = 2
  $wdFormatText = 2
  $null = $doc.SaveAs([ref]$OutPath, [ref]$wdFormatText)
} finally {
  try { if ($doc) { $doc.Close() } } catch {}
  try { if ($word) { $word.Quit() } } catch {}
  try { if ($doc) { [System.Runtime.InteropServices.Marshal]::ReleaseComObject($doc) | Out-Null } } catch {}
  try { if ($word) { [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null } } catch {}
}

Write-Output $OutPath

