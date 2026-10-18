param(
    [Parameter(Mandatory)][string]$VersionName,
    [Parameter(Mandatory)][ValidateRange(100027, 2100000000)][int]$VersionCode,
    [string]$PreviousApk,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
$oldName = $env:MING_VERSION_NAME
$oldCode = $env:MING_VERSION_CODE
try {
    if ($VersionName -notmatch '^\d+\.\d+\.\d+([.-][0-9A-Za-z.-]+)?$') { throw 'Invalid version name' }
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk -and (Test-Path local.properties)) {
        $sdkLine = Get-Content local.properties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) { $sdk = $sdkLine.Substring(8).Replace('\\', '\').Replace('\:', ':') }
    }
    if (-not $sdk -or -not (Test-Path -LiteralPath $sdk)) { throw 'Set ANDROID_HOME to an installed Android SDK' }
    $tools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $tools) { throw 'Android build-tools not installed' }
    $env:MING_VERSION_NAME = $VersionName
    $env:MING_VERSION_CODE = "$VersionCode"
    $buildArgs = @(':androidApp:assembleRelease', '--stacktrace', '--no-daemon', '--max-workers=4')
    if ($Offline) { $buildArgs += '--offline' }
    & .\gradlew.bat @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'Android release build failed' }
    $apk = Join-Path $projectRoot 'androidApp/build/outputs/apk/release/androidApp-release.apk'
    if (-not (Test-Path -LiteralPath $apk)) { throw 'Signed release APK missing' }
    $verifyArgs = @('tools/ci/verify_android_release.py', $apk, '--aapt', (Join-Path $tools.FullName 'aapt.exe'),
        '--apksigner', (Join-Path $tools.FullName 'apksigner.bat'), '--version-name', $VersionName)
    if ($PreviousApk) { $verifyArgs += @('--previous-apk', $PreviousApk) }
    & python @verifyArgs
    if ($LASTEXITCODE -ne 0) { throw 'APK upgrade verification failed' }
    $output = Join-Path $projectRoot 'build/release'
    New-Item -ItemType Directory -Force $output | Out-Null
    $target = Join-Path $output "MingMusic-$VersionName.apk"
    Copy-Item -LiteralPath $apk -Destination $target
    $hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$target.sha256" -Value "$hash  $([IO.Path]::GetFileName($target))" -Encoding ascii
    Write-Output "Verified APK: $target"
    Write-Output "SHA256: $hash"
} finally {
    $env:MING_VERSION_NAME = $oldName
    $env:MING_VERSION_CODE = $oldCode
    Pop-Location
}
