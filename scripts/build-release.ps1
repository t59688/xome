# Build signed release APK/AAB and stage as xome-<VERSION>-release.*
# Usage:
#   .\scripts\build-release.ps1            # apk (default)
#   .\scripts\build-release.ps1 apk
#   .\scripts\build-release.ps1 aab
#   .\scripts\build-release.ps1 all

param(
  [ValidateSet('apk', 'aab', 'all')]
  [string]$Format = 'apk',
  [switch]$SkipLint,
  [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $projectRoot

$envFile = Join-Path $projectRoot '.env.android.local'
if (-not (Test-Path $envFile)) {
  throw "Missing .env.android.local. Generate signing first (.android-signing + env)."
}

Get-Content $envFile | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith('#')) { return }
  $parts = $line.Split('=', 2)
  if ($parts.Length -ne 2) { return }
  Set-Item -Path "Env:$($parts[0].Trim())" -Value $parts[1].Trim()
}

foreach ($name in @('KEYSTORE_PATH', 'STORE_PASSWORD', 'KEY_PASSWORD')) {
  if (-not [string]::IsNullOrWhiteSpace((Get-Item "Env:$name" -ErrorAction SilentlyContinue).Value)) { continue }
  throw "Missing $name in .env.android.local"
}
if (-not [System.IO.Path]::IsPathRooted($env:KEYSTORE_PATH)) {
  $env:KEYSTORE_PATH = Join-Path $projectRoot $env:KEYSTORE_PATH
}
if (-not (Test-Path $env:KEYSTORE_PATH)) {
  throw "Keystore not found: $env:KEYSTORE_PATH"
}
if ([string]::IsNullOrWhiteSpace($env:KEY_ALIAS)) {
  $env:KEY_ALIAS = 'upload'
}

$version = (Get-Content (Join-Path $projectRoot 'VERSION') -Raw).Trim()
if (-not $version) { throw 'VERSION file is empty.' }
$env:VERSION_NAME = $version

$gradlew = Join-Path $projectRoot 'gradlew.bat'
Write-Host "Building Xome $version ($Format)..."
$gradleArgs = @('--no-daemon', '--stacktrace')
if ($Offline) { $gradleArgs += '--offline' }
if ($SkipLint) {
  Write-Warning 'Local diagnostic build: Android release lint is skipped; CI release still runs it.'
  $gradleArgs += @('-x', ':app:lintVitalAnalyzeRelease', '-x', ':app:lintVitalReportRelease', '-x', ':app:lintVitalRelease')
}
if ($Format -eq 'apk' -or $Format -eq 'all') {
  & $gradlew ':app:assembleRelease' '-PsplitApks=true' @gradleArgs
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
if ($Format -eq 'aab' -or $Format -eq 'all') {
  & $gradlew ':app:bundleRelease' '-PsplitApks=false' @gradleArgs
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$outDir = Join-Path $projectRoot 'artifacts\android'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Copy-NamedArtifact {
  param(
    [string]$SearchRoot,
    [string]$Filter,
    [string]$DestName
  )
  $src = Get-ChildItem -Path $SearchRoot -Filter $Filter -Recurse -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $src) { throw "Build output not found under $SearchRoot ($Filter)" }
  $dest = Join-Path $outDir $DestName
  Copy-Item -Force $src.FullName $dest
  Write-Host "-> $dest"
}

if ($Format -eq 'apk' -or $Format -eq 'all') {
  foreach ($abi in @('universal', 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')) {
    $src = Join-Path $projectRoot "app\build\outputs\apk\release\app-$abi-release.apk"
    if (-not (Test-Path -LiteralPath $src)) { throw "Build output not found: $src" }
    $destName = if ($abi -eq 'universal') { "xome-$version-release.apk" } else { "xome-$version-$abi-release.apk" }
    $dest = Join-Path $outDir $destName
    Write-Host "-> $dest"
    Copy-Item -LiteralPath $src -Destination $dest -Force
  }
}
if ($Format -eq 'aab' -or $Format -eq 'all') {
  Copy-NamedArtifact `
    -SearchRoot (Join-Path $projectRoot 'app\build\outputs\bundle\release') `
    -Filter '*.aab' `
    -DestName "xome-$version-release.aab"
}

Write-Host "Done."
