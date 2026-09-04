<#
.SYNOPSIS
    Runs the instrumentation suite against a connected device.

.DESCRIPTION
    This wrapper deliberately changes nothing on the device. An earlier version
    disabled the screensaver and the display timeouts so a long run could not be
    interrupted by the television going to sleep, and restored them afterwards.
    That is somebody's living-room set-top box: a script that reaches in and
    rewrites its power settings is the wrong trade even when it puts them back,
    and a crashed run leaves a TV that never sleeps.

    So the sleeping-display failure is handled by reading it correctly instead
    of preventing it. A display that sleeps mid-run stops the activity, and a
    stopped activity has no Compose hierarchy, so every test after that point
    fails with "No compose hierarchies found in the app" or times out waiting
    for a node. That signature - a clean run that turns into a wall of identical
    timeouts partway through - means the television went to sleep, not that the
    code broke. Run the suite in smaller pieces, via -GradleArgs, so each piece
    finishes inside the device's own timeout.

.PARAMETER Serial
    adb serial of the target device. Defaults to the only attached device.

.PARAMETER GradleArgs
    Gradle tasks to run. Defaults to the app and core connected test tasks.
#>
[CmdletBinding()]
param(
    [string] $Serial,
    [string[]] $GradleArgs = @(':core:connectedDebugAndroidTest', ':app:connectedDebugAndroidTest')
)

$ErrorActionPreference = 'Stop'

$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
} else {
    'adb'
}
if (-not (Get-Command $adb -ErrorAction SilentlyContinue)) {
    throw "adb not found. Set ANDROID_HOME or put adb on PATH."
}

$target = if ($Serial) { @('-s', $Serial) } else { @() }

# Instrumentation drives whatever activity it launches, but it cannot take the
# foreground away from an app that already holds it. When something else is in
# front, every Compose query times out waiting for a hierarchy that is not on
# screen, and the run reads as a suite full of broken tests rather than a
# television busy doing something else. Name it up front.
#
# The whole dump is read and matched here rather than piped into grep on the
# device: grep exits at the first match, dumpsys keeps writing, and the run
# opens with a broken-pipe error that has nothing to do with the tests.
$activities = (& $adb @target shell dumpsys activity activities) -join "`n"
if ($activities -match 'mResumedActivity:[^\n]*?\s([A-Za-z0-9_.]+)/') {
    $foreground = $Matches[1]
    $expected = @('com.streammate.tv', 'com.streammate.tv.debug')
    $isLauncher = $foreground -match 'launcher|projengmenu|flauncher|tvlauncher'
    if (-not $isLauncher -and $expected -notcontains $foreground) {
        Write-Warning "$foreground is in the foreground. Instrumentation cannot take the screen from it, so tests will time out rather than fail. Close it before trusting this run."
    } else {
        Write-Host "Foreground: $foreground"
    }
}

$awake = (& $adb @target shell dumpsys power) -match 'mWakefulness=Awake'
if (-not $awake) {
    Write-Warning "The display is asleep. Tests need a resumed activity, so wake the device before running - this script will not do it for you."
}

# Gradle writes progress to stderr as a matter of course. Under 'Stop' that is
# a terminating error the moment the caller pipes this script anywhere, so the
# run dies on output rather than on a result.
$ErrorActionPreference = 'Continue'
& (Join-Path $PSScriptRoot '..\gradlew.bat') @GradleArgs
exit $LASTEXITCODE
