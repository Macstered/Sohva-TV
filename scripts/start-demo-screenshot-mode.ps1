param(
    [string]$Serial,
    [string]$Adb
)

$ErrorActionPreference = "Stop"
$sdkRoot = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    Select-Object -First 1
if (-not $Adb) {
    $sdkAdb = if ($sdkRoot) { Join-Path $sdkRoot 'platform-tools\adb.exe' } else { $null }
    $pathAdb = Get-Command adb.exe -ErrorAction SilentlyContinue
    $Adb = if ($sdkAdb -and (Test-Path -LiteralPath $sdkAdb -PathType Leaf)) {
        $sdkAdb
    } elseif ($pathAdb) {
        $pathAdb.Source
    } else {
        throw 'ADB was not found. Set ANDROID_SDK_ROOT/ANDROID_HOME, put adb on PATH, or supply -Adb.'
    }
}
$target = if ($Serial) { @('-s', $Serial) } else { @() }
$workspace = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $workspace "app\build\outputs\apk\demo\app-demo.apk"

Push-Location $workspace
try {
    & ".\gradlew.bat" ":app:assembleDemo"
    if ($LASTEXITCODE -ne 0) { throw "Demo build failed" }

    & $Adb @target install -r $apk
    if ($LASTEXITCODE -ne 0) { throw "Demo install failed" }

    & $Adb @target shell am force-stop com.streammate.tv.demo
    & $Adb @target shell monkey `
        -p com.streammate.tv.demo `
        -c android.intent.category.LEANBACK_LAUNCHER 1
    if ($LASTEXITCODE -ne 0) { throw "Demo launch failed" }
} finally {
    Pop-Location
}
