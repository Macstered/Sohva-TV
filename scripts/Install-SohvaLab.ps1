<#
.SYNOPSIS
Validates a local Lab APK, then optionally installs ONLY that package on an explicit device.
.DESCRIPTION
No production install, uninstall, data clear, downgrade, build or public network request.
Use -VerifyOnly for artifact checks without contacting any device. Physical devices
require the additional -AllowPhysicalDevice switch. Does not launch unless requested.
#>
[CmdletBinding(DefaultParameterSetName = 'Install')]
param(
    [string]$Apk = (Join-Path $PSScriptRoot '../app/build/outputs/apk/lab/app-lab.apk'),
    [string]$BuildTools,
    [string]$Adb,
    [Parameter(Mandatory = $true, ParameterSetName = 'Install')]
    [ValidateNotNullOrEmpty()][string]$Serial,
    [Parameter(Mandatory = $true, ParameterSetName = 'Verify')][switch]$VerifyOnly,
    [Parameter(ParameterSetName = 'Install')][switch]$AllowPhysicalDevice,
    [Parameter(ParameterSetName = 'Install')][switch]$Launch
)
$ErrorActionPreference = 'Stop'
$labTools = & (Join-Path $PSScriptRoot 'Resolve-SohvaAndroidTools.ps1') -Adb $Adb -BuildTools $BuildTools
$Adb = $labTools.Adb
$labApk = (Resolve-Path -LiteralPath $Apk).Path
$labPackage = 'com.streammate.tv.lab'
$labBadging = (& $labTools.Aapt dump badging $labApk 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the APK.' }
if ($labBadging -notmatch "package: name='com\.streammate\.tv\.lab' " -or
    $labBadging -notmatch "versionName='[^']+-lab'" -or
    $labBadging -notmatch "application-label:'Sohva TV Lab'" -or
    $labBadging -match 'application-debuggable') {
    throw 'Refusing APK: expected a non-debuggable Sohva TV Lab package, label and version.'
}
if ($labBadging -match 'android.permission.REQUEST_INSTALL_PACKAGES|android.permission.RECEIVE_BOOT_COMPLETED') {
    throw 'Refusing Lab APK with installer or boot permissions.'
}
$labManifest = (& $labTools.Aapt dump xmltree $labApk AndroidManifest.xml 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the packaged manifest.' }
if ($labManifest -match 'androidx.core.content.FileProvider') { throw 'Lab must not expose the update provider.' }
foreach ($labAuthority in ($labManifest -split "`n" | Select-String 'android:authorities')) {
    if ($labAuthority.Line -notmatch '"com\.streammate\.tv\.lab\.') {
        throw 'Lab contains a provider authority outside its own package.'
    }
}
$labSignature = & $labTools.ApkSigner verify --verbose $labApk 2>&1
if ($LASTEXITCODE -ne 0) { throw "Lab signature verification failed: $labSignature" }
$labHash = (Get-FileHash -LiteralPath $labApk -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "Verified Sohva TV Lab APK. SHA-256: $labHash"
if ($VerifyOnly) { return }
if ($Serial -notmatch '^emulator-[0-9]+$' -and -not $AllowPhysicalDevice) {
    throw 'Physical devices require -AllowPhysicalDevice. No device was changed.'
}
function Invoke-LabAdb([string[]]$Arguments) {
    $labOutput = & $Adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed on $Serial : $labOutput" }
    $labOutput
}
$labDeviceState = (Invoke-LabAdb @('get-state')) -join ''
if ($labDeviceState.Trim() -ne 'device') { throw 'The selected device is not ready.' }
# Android enforces signature continuity. Never fall back to uninstall or -d on failure.
Invoke-LabAdb @('install', '-r', $labApk)
if ($Launch) {
    Invoke-LabAdb @('shell', 'am', 'start', '-n', "$labPackage/com.streammate.tv.app.MainActivity")
}
