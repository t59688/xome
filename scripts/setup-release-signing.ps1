# Creates a dedicated Xome signing key, stores it locally outside Git, and provisions GitHub Actions.
# Safe to rerun: existing signing material is never overwritten.
param([switch]$SkipGitHub)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$signingDir = Join-Path $root '.android-signing'
$keystorePath = Join-Path $signingDir 'xome-release.jks'
$envPath = Join-Path $root '.env.android.local'
New-Item -ItemType Directory -Force -Path $signingDir | Out-Null

function New-StrongPassword {
    $bytes = New-Object byte[] 36
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes).TrimEnd('=') -replace '\+', '-' -replace '/', '_'
}

function Write-PlainUtf8([string]$Path, [string]$Value) {
    [System.IO.File]::WriteAllText($Path, $Value, (New-Object System.Text.UTF8Encoding($false)))
}

if ((Test-Path $keystorePath) -xor (Test-Path $envPath)) {
    throw 'Incomplete local signing configuration. Restore the missing key or env file; never rotate signing keys implicitly.'
}
if (-not (Test-Path $keystorePath)) {
    $keytool = (Get-Command keytool.exe -ErrorAction Stop).Source
    $pass = New-StrongPassword
    $previousPreference = $ErrorActionPreference
    try {
        # keytool writes non-error progress to stderr; handle success by exit code.
        $ErrorActionPreference = 'Continue'
        & $keytool -genkeypair -noprompt -storetype PKCS12 -keystore $keystorePath `
            -alias upload -keyalg RSA -keysize 3072 -validity 10000 `
            -dname 'CN=Xome Android Release, O=Xome, C=US' `
            -storepass $pass -keypass $pass 2>$null | Out-Null
        $keytoolExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($keytoolExit -ne 0 -or -not (Test-Path $keystorePath)) { throw 'keytool failed to create signing key' }
    Write-PlainUtf8 $envPath ("KEYSTORE_PATH=.android-signing/xome-release.jks`nSTORE_PASSWORD=$pass`nKEY_ALIAS=upload`nKEY_PASSWORD=$pass`n")
    Write-Host 'Created dedicated Xome signing key and local ignored environment file.'
} else {
    Write-Host 'Reusing existing Xome signing key (no rotation).'
}

$values = @{}
foreach ($line in [System.IO.File]::ReadAllLines($envPath)) {
    $parts = $line.Split('=', 2)
    if ($parts.Length -eq 2 -and $parts[0] -and -not $parts[0].StartsWith('#')) {
        $values[$parts[0].Trim()] = $parts[1].Trim()
    }
}
foreach ($name in @('STORE_PASSWORD', 'KEY_PASSWORD', 'KEY_ALIAS')) {
    if (-not $values.ContainsKey($name) -or -not $values[$name]) { throw "Missing $name in local signing config" }
}
if ($SkipGitHub) { exit 0 }
$gh = (Get-Command gh.exe -ErrorAction Stop).Source
$secretFile = Join-Path $signingDir 'secrets-tmp.env'
try {
    $keyBase64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($keystorePath))
    Write-PlainUtf8 $secretFile ("XOME_KEYSTORE_BASE64=$keyBase64`nXOME_STORE_PASSWORD=$($values.STORE_PASSWORD)`nXOME_KEY_PASSWORD=$($values.KEY_PASSWORD)`nXOME_KEY_ALIAS=$($values.KEY_ALIAS)`n")
    & $gh secret set -R t59688/xome -f $secretFile
    if ($LASTEXITCODE -ne 0) { throw 'gh secret set failed; local key was retained' }
} finally {
    if (Test-Path $secretFile) { Remove-Item -Force $secretFile }
}
Write-Host 'GitHub Actions signing secrets configured for t59688/xome.'
