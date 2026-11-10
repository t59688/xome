# Deterministic, offline optimization of bundled illustrations. Originals stay in artifacts/.
param(
  [ValidateRange(1, 100)][int]$Quality = 82,
  [switch]$Apply
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backupDir = Join-Path $projectRoot 'artifacts\image-originals'
$previewDir = Join-Path $projectRoot "artifacts\image-optimization\q$Quality"
New-Item -ItemType Directory -Force -Path $backupDir, $previewDir | Out-Null
$encoder = [Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
  Where-Object MimeType -eq 'image/jpeg' | Select-Object -First 1

$assets = @(
  @{ Name = 'img_vault_banner_1794279611736.jpg'; MaxEdge = 960 },
  @{ Name = 'img_launcher_calc_1794279609341.jpg'; MaxEdge = 512 }
)

foreach ($asset in $assets) {
  $target = Join-Path $projectRoot "app\src\main\res\drawable\$($asset.Name)"
  $backup = Join-Path $backupDir $asset.Name
  if (-not (Test-Path -LiteralPath $backup)) {
    Copy-Item -LiteralPath $target -Destination $backup
  }
  $candidate = Join-Path $previewDir $asset.Name
  $sourceImage = [Drawing.Image]::FromFile($backup)
  try {
    $scale = [Math]::Min(1.0, $asset.MaxEdge / [double][Math]::Max($sourceImage.Width, $sourceImage.Height))
    $width = [Math]::Max(1, [int][Math]::Round($sourceImage.Width * $scale))
    $height = [Math]::Max(1, [int][Math]::Round($sourceImage.Height * $scale))
    $bitmap = [Drawing.Bitmap]::new($width, $height)
    try {
      $graphics = [Drawing.Graphics]::FromImage($bitmap)
      try {
        $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($sourceImage, 0, 0, $width, $height)
      } finally { $graphics.Dispose() }
      $parameters = [Drawing.Imaging.EncoderParameters]::new(1)
      try {
        $parameters.Param[0] = [Drawing.Imaging.EncoderParameter]::new(
          [Drawing.Imaging.Encoder]::Quality, [long]$Quality
        )
        $bitmap.Save($candidate, $encoder, $parameters)
      } finally { $parameters.Dispose() }
    } finally { $bitmap.Dispose() }
  } finally { $sourceImage.Dispose() }

  $check = [Drawing.Image]::FromFile($candidate)
  try {
    if ($check.Width -ne $width -or $check.Height -ne $height) {
      throw "Unexpected output dimensions: $candidate"
    }
  } finally { $check.Dispose() }
  $before = (Get-Item -LiteralPath $backup).Length
  $after = (Get-Item -LiteralPath $candidate).Length
  if ($after -ge $before) { throw "Optimization did not reduce size: $candidate" }
  if ($Apply) {
    Write-Host "Replacing $target (original preserved at $backup)"
    Copy-Item -LiteralPath $candidate -Destination $target -Force
  }
  [pscustomobject]@{
    Image = $asset.Name
    Dimensions = "${width}x${height}"
    OriginalBytes = $before
    OptimizedBytes = $after
    SavedPercent = [Math]::Round(100 * (1 - $after / [double]$before), 1)
  }
}
