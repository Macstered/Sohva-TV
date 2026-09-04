[CmdletBinding()]
param(
    [string]$Serial = 'emulator-5554',
    [string]$AdbPath,
    [switch]$SkipBuild,
    [string[]]$AppTestClass = @()
)

$ErrorActionPreference = 'Stop'

if (-not $AdbPath) {
    $sdkRoot = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -First 1
    $sdkAdb = if ($sdkRoot) { Join-Path $sdkRoot 'platform-tools\adb.exe' } else { $null }
    $pathAdb = Get-Command adb.exe -ErrorAction SilentlyContinue
    $AdbPath = if ($sdkAdb -and (Test-Path -LiteralPath $sdkAdb -PathType Leaf)) {
        $sdkAdb
    } elseif ($pathAdb) {
        $pathAdb.Source
    } else {
        throw 'ADB was not found. Set ANDROID_SDK_ROOT/ANDROID_HOME, put adb on PATH, or supply -AdbPath.'
    }
}

# Never use connectedAndroidTest here: another attached target may be the Shield.
if ($Serial -notmatch '^emulator-[0-9]+$') {
    throw 'This runner accepts local Android emulator serials only.'
}
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw 'ADB was not found. Supply the Android SDK platform-tools adb.exe path with -AdbPath.'
}
$taskRepoRoot = Split-Path -Parent $PSScriptRoot
$taskLogRoot = Join-Path $taskRepoRoot '.local/test-runs/android-tv-emulator'
New-Item -ItemType Directory -Path $taskLogRoot -Force | Out-Null

function Invoke-EmulatorAdb {
    param([string[]]$Arguments)
    $result = & $AdbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed for $Serial : $result"
    }
    $result
}

function Invoke-EmulatorTests {
    param([string]$Component, [string]$Label, [string[]]$Classes = @())
    $arguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
    if ($Classes.Count -gt 0) {
        $arguments += @('-e', 'class', ($Classes -join ','))
    }
    $arguments += $Component
    $logPath = Join-Path $taskLogRoot "$Label-instrumentation.txt"
    Write-Output "Running $Label tests on $Serial. Log: $logPath"
    $result = & $AdbPath @arguments 2>&1 | Tee-Object -FilePath $logPath
    $exitCode = $LASTEXITCODE
    $report = $result -join "`n"
    # ADB can exit with zero even when JUnit fails or the test process crashes.
    if ($exitCode -ne 0 -or $report -notmatch '(?m)^OK \([1-9][0-9]* tests?\)' -or
        $report -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -[12]') {
        $result | Select-Object -Last 45 | Write-Output
        throw "$Label instrumentation did not pass. See $logPath"
    }
    $result | Select-String -Pattern '^Time:', '^OK \(' | ForEach-Object { $_.Line }
}

Push-Location $taskRepoRoot
try {
    $emulatorFlag = (Invoke-EmulatorAdb -Arguments @('shell', 'getprop', 'ro.kernel.qemu') | Out-String).Trim()
    if ($emulatorFlag -ne '1') {
        throw "$Serial is not confirmed to be an Android emulator. No packages were installed."
    }
    $booted = (Invoke-EmulatorAdb -Arguments @('shell', 'getprop', 'sys.boot_completed') | Out-String).Trim()
    if ($booted -ne '1') {
        throw 'Start the Android TV AVD and wait for it to finish booting before running tests.'
    }
    if (-not $SkipBuild) {
        & .\gradlew.bat :core:assembleDebugAndroidTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Android test build failed; nothing was installed.' }
    }
    foreach ($apk in @(
        'core/build/outputs/apk/androidTest/debug/core-debug-androidTest.apk',
        'app/build/outputs/apk/debug/app-debug.apk',
        'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    )) {
        $apkPath = Join-Path $taskRepoRoot $apk
        if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) { throw "Missing APK: $apkPath" }
        Invoke-EmulatorAdb -Arguments @('install', '-r', '-t', $apkPath)
    }
    Invoke-EmulatorTests -Component 'com.streammate.tv.core.test/androidx.test.runner.AndroidJUnitRunner' -Label 'core'
    $appLabel = if ($AppTestClass.Count -eq 0) { 'app' } else { 'app-focused' }
    Invoke-EmulatorTests -Component 'com.streammate.tv.debug.test/androidx.test.runner.AndroidJUnitRunner' -Label $appLabel -Classes $AppTestClass
} finally {
    Pop-Location
}
