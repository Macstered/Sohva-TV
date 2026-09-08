<#
.SYNOPSIS
Builds the release APK and asks a device's own dex verifier to accept it.

.DESCRIPTION
The instrumentation suites all run the debug build, which is never minified,
so nothing in them looks at what R8 emits. A composable with enough
parameters can push R8 into dex that ART refuses to verify, and the app then
dies with a VerifyError the moment that screen is reached. It happened to
PlayerScreenKt.ActivePlayer on 8 September 2026, and the build before it was
merely lucky: the same source, built clean, failed.

Run this before packaging any beta. It installs the release APK on the given
device, forces a verification pass, and fails on any rejection.
#>
param(
    [string]$Serial = "emulator-5580",
    [string]$Adb = "C:/Android/sdk/platform-tools/adb.exe",
    [string]$Package = "com.streammate.tv",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

if (-not $SkipBuild) {
    Write-Host "Building the release APK..."
    & "$root/gradlew.bat" -p $root :app:assembleRelease | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "assembleRelease failed" }
}

$apk = Join-Path $root "app/build/outputs/apk/release/app-release.apk"
if (-not (Test-Path $apk)) { throw "No release APK at $apk" }

Write-Host "Installing on $Serial..."
& $Adb -s $Serial install -r -d $apk | Out-Null
if ($LASTEXITCODE -ne 0) { throw "install failed" }

& $Adb -s $Serial logcat -c
Write-Host "Verifying..."
& $Adb -s $Serial shell cmd package compile -m verify -f $Package | Out-Null
$log = & $Adb -s $Serial logcat -d

$ran = @($log | Select-String -SimpleMatch "dex2oat").Count
if ($ran -eq 0) { throw "No verification pass ran; the oracle proved nothing." }

$bad = @($log | Select-String -SimpleMatch "failed to verify")
if ($bad.Count -gt 0) {
    Write-Host ""
    Write-Host "The dex verifier rejected the release build:" -ForegroundColor Red
    $bad | Select-Object -First 4 | ForEach-Object { Write-Host "  $_" }
    Write-Host ""
    Write-Host "Usually a composable with too many parameters. Compose emits one" -ForegroundColor Yellow
    Write-Host "`$changed mask per ten parameters and one `$default mask per 32," -ForegroundColor Yellow
    Write-Host "and the default-resolution prologue grows with them. Drop unused" -ForegroundColor Yellow
    Write-Host "defaults, or group parameters into a holder, and run this again." -ForegroundColor Yellow
    exit 1
}

Write-Host "Release dex verifies cleanly ($ran verification lines, no rejections)." -ForegroundColor Green
