# Resolve SDK tools on Windows and Linux without embedding a developer's paths.
[CmdletBinding()]
param([string]$Adb, [string]$BuildTools)
$ErrorActionPreference = 'Stop'
$sohvaWindows = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
$sohvaExe = if ($sohvaWindows) { '.exe' } else { '' }
$sohvaBatch = if ($sohvaWindows) { '.bat' } else { '' }
$sohvaSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
if (-not $Adb) {
    if ($sohvaSdk) { $Adb = Join-Path $sohvaSdk "platform-tools/adb$sohvaExe" }
    else { $Adb = (Get-Command "adb$sohvaExe" -ErrorAction Stop).Source }
}
$Adb = (Resolve-Path -LiteralPath $Adb).Path
if (-not $BuildTools) {
    if (-not $sohvaSdk) { $sohvaSdk = Split-Path (Split-Path $Adb -Parent) -Parent }
    $sohvaVersions = @(Get-ChildItem -LiteralPath (Join-Path $sohvaSdk 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Name } -Descending)
    if (-not $sohvaVersions) { throw 'No stable Android SDK build-tools found. Pass -BuildTools.' }
    $BuildTools = $sohvaVersions[0].FullName
}
$BuildTools = (Resolve-Path -LiteralPath $BuildTools).Path
$sohvaAapt = Join-Path $BuildTools "aapt$sohvaExe"
$sohvaSigner = Join-Path $BuildTools "apksigner$sohvaBatch"
foreach ($sohvaTool in @($sohvaAapt, $sohvaSigner)) {
    if (-not (Test-Path -LiteralPath $sohvaTool -PathType Leaf)) { throw "Missing SDK tool: $sohvaTool" }
}
[pscustomobject]@{ Adb = $Adb; BuildTools = $BuildTools; Aapt = $sohvaAapt; ApkSigner = $sohvaSigner }
