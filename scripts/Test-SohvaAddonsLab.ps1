<#
.SYNOPSIS
Runs synthetic addon persistence and browsing checks on an explicitly named emulator.
.DESCRIPTION
Never targets Shield or the stable package. Installs Lab in place, runs the
standalone Room suite and Lab tests, and verifies persistence in a new process.
No user addon URLs, source imports, data clear, uninstall, push or publication.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [switch]$SkipBuild,
    [string]$Ffmpeg,
    [string]$Adb,
    [string]$BuildTools
)
$ErrorActionPreference = 'Stop'
$addonTools = & (Join-Path $PSScriptRoot 'Resolve-SohvaAndroidTools.ps1') -Adb $Adb -BuildTools $BuildTools
$Adb = $addonTools.Adb
$addonRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if ($Serial -notmatch '^emulator-\d+$') { throw 'This synthetic harness accepts an explicit emulator serial only.' }
$addonEmulator = (& $Adb -s $Serial shell getprop ro.kernel.qemu 2>&1) -join ''
if ($LASTEXITCODE -ne 0 -or $addonEmulator.Trim() -ne '1') { throw 'Target is not an available Android emulator.' }
$addonEvidence = Join-Path $addonRoot '.local/test-runs/addons-lab'
New-Item -ItemType Directory -Path $addonEvidence -Force | Out-Null
Push-Location $addonRoot
try {
    if (-not $SkipBuild) {
        if ($Ffmpeg) { & (Join-Path $PSScriptRoot 'Generate-AddonPlaybackFixtures.ps1') -Ffmpeg $Ffmpeg }
        $addonRequiredFixtures = @('fixture.mp4', 'fixture.m3u8', 'fixture.mpd', 'fixture-embedded.mkv', 'fixture-secondary.mkv', 'fixture-timing.mkv')
        if ($addonRequiredFixtures | Where-Object { -not (Test-Path -LiteralPath (Join-Path 'app/build/generated/addonPlaybackFixtures/playback' $_)) }) {
            throw 'Generate synthetic playback fixtures first, or pass -Ffmpeg with the ffmpeg executable path.'
        }
        $addonGradle = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { './gradlew.bat' } else { './gradlew' }
        & $addonGradle -PsohvaTestBuildType=lab :app:assembleLab :app:assembleLabAndroidTest :addons:assembleDebugAndroidTest --console=plain --warning-mode=fail
        if ($LASTEXITCODE -ne 0) { throw 'Lab test build failed.' }
    }
    & (Join-Path $PSScriptRoot 'Install-SohvaLab.ps1') -Serial $Serial -Adb $Adb -BuildTools $addonTools.BuildTools
    foreach ($addonTest in @(
        @{ Path = 'addons/build/outputs/apk/androidTest/debug/addons-debug-androidTest.apk'; Package = 'com.sohva.tv.addons.test'; Target = 'com.sohva.tv.addons.test' },
        @{ Path = 'app/build/outputs/apk/androidTest/lab/app-lab-androidTest.apk'; Package = 'com.streammate.tv.lab.test'; Target = 'com.streammate.tv.lab' }
    )) {
        $addonAapt = $addonTools.Aapt
        $addonBadging = (& $addonAapt dump badging $addonTest.Path 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0 -or $addonBadging -notmatch ("package: name='" + [regex]::Escape($addonTest.Package) + "'")) {
            throw 'Refusing unexpected test APK package.'
        }
        $addonManifest = (& $addonAapt dump xmltree $addonTest.Path AndroidManifest.xml 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0 -or $addonManifest -notmatch ('android:targetPackage[^\r\n]*="' + [regex]::Escape($addonTest.Target) + '"')) {
            throw 'Refusing unexpected instrumentation target.'
        }
        & $Adb -s $Serial install -r -t $addonTest.Path
        if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
    }
    function Invoke-AddonProbe([string]$Name, [string]$Runner, [string]$TestClass) {
        $addonArguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
        if ($TestClass) { $addonArguments += @('-e', 'class', $TestClass) }
        $addonArguments += $Runner
        $addonOutput = & $Adb @addonArguments 2>&1
        $addonExit = $LASTEXITCODE
        $addonText = $addonOutput -join "`n"
        $addonText | Out-File -LiteralPath (Join-Path $addonEvidence "$Name.txt") -Encoding utf8
        if ($addonExit -ne 0 -or $addonText -notmatch '(?m)^OK \(\d+ tests?\)' -or
            $addonText -match 'FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED') {
            throw "$Name failed; inspect the private evidence file."
        }
        Write-Host "$Name passed."
    }
    Invoke-AddonProbe 'room' 'com.sohva.tv.addons.test/androidx.test.runner.AndroidJUnitRunner' ''
    $addonRunner = 'com.streammate.tv.lab.test/androidx.test.runner.AndroidJUnitRunner'
    try {
        Invoke-AddonProbe 'seed' $addonRunner 'com.streammate.tv.lab.AddonLabPersistenceTest#seed'
        & $Adb -s $Serial shell am force-stop com.streammate.tv.lab
        if ($LASTEXITCODE -ne 0) { throw 'Lab force-stop failed.' }
        $addonPid = (& $Adb -s $Serial shell pidof com.streammate.tv.lab 2>&1) -join ''
        if ($addonPid.Trim()) { throw 'Lab is still running; cannot prove process-death persistence.' }
        Invoke-AddonProbe 'restart' $addonRunner 'com.streammate.tv.lab.AddonLabPersistenceTest#verifyAfterProcessDeath'
        Invoke-AddonProbe 'ui-access' $addonRunner 'com.streammate.tv.lab.AddonLabBrowsingTest,com.streammate.tv.lab.AddonLabAccessTest'
        Invoke-AddonProbe 'import' $addonRunner 'com.streammate.tv.lab.AddonLabImportTest'
        Invoke-AddonProbe 'account-import' $addonRunner 'com.streammate.tv.lab.AddonLabAccountImportTest'
        Invoke-AddonProbe 'phone-landing' $addonRunner 'com.streammate.tv.lab.AddonLabPhoneLandingTest'
        Invoke-AddonProbe 'navigation-settings' $addonRunner 'com.streammate.tv.lab.AddonLabCatalogNavigationTest'
        Invoke-AddonProbe 'cast-details' $addonRunner 'com.streammate.tv.lab.AddonLabCastTest'
        Invoke-AddonProbe 'loading' $addonRunner 'com.streammate.tv.lab.AddonLabLoadingTest'
        Invoke-AddonProbe 'hero-synopsis' $addonRunner 'com.streammate.tv.lab.AddonLabHeroSynopsisTest'
        Invoke-AddonProbe 'localization' $addonRunner 'com.streammate.tv.lab.AddonLabLocalizationTest'
        Invoke-AddonProbe 'catalog-stress' $addonRunner 'com.streammate.tv.lab.AddonLabCatalogStressTest'
        Invoke-AddonProbe 'playback' $addonRunner 'com.streammate.tv.lab.AddonLabPlaybackTest'
        Invoke-AddonProbe 'automatic-subtitles' $addonRunner 'com.streammate.tv.lab.AddonLabAutomaticSubtitleTest'
        Invoke-AddonProbe 'subtitle-timing' $addonRunner 'com.streammate.tv.lab.AddonLabSubtitleTimingTest'
        Invoke-AddonProbe 'player-navigation' $addonRunner 'com.streammate.tv.lab.AddonLabPlayerNavigationTest'
        Invoke-AddonProbe 'search' $addonRunner 'com.streammate.tv.lab.AddonLabSearchTest'
        Invoke-AddonProbe 'library' $addonRunner 'com.streammate.tv.lab.AddonLabLibraryTest'
    } finally {
        Invoke-AddonProbe 'cleanup' $addonRunner 'com.streammate.tv.lab.AddonLabPersistenceTest#cleanupProbe'
    }
    Write-Host 'All synthetic addon Lab gates passed. Stable app and physical devices were not targeted.'
} finally { Pop-Location }
