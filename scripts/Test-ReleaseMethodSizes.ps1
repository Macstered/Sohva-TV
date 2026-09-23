<#
.SYNOPSIS
Fails when an app method in the release APK is too large for ART to compile ahead of time.

.DESCRIPTION
ART's ahead-of-time compiler leaves out any method longer than 10,000 dex
code units, whatever the compile filter or profile says. Such a method runs in
the interpreter after every cold start until the JIT gets to it, and on a slow
TV box that is the screen people wait for. On 23 September 2026 the release
carried two: PlayerScreenKt.ActivePlayer (11,248 units) and
SettingsScreenKt.SettingsScreen (12,966). Shrinking with R8 brought them to
8,882 and 9,031, close enough to the line that the next few features would
cross it again unnoticed.

Reads every method of com.streammate and com.sohva classes with the SDK's
dexdump, lists the largest, and fails at 95 percent of the limit so that the
composable can be split before it stops compiling, not after.
#>
param(
    [string]$Apk,
    [string]$BuildTools,
    [int]$LimitUnits = 10000,
    [double]$FailAtShare = 0.95
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Apk) { $Apk = Join-Path $root 'app/build/outputs/apk/release/app-release.apk' }
if (-not (Test-Path -LiteralPath $Apk)) { throw "No APK at $Apk" }
$BuildTools = (& (Join-Path $PSScriptRoot 'Resolve-SohvaAndroidTools.ps1') -BuildTools $BuildTools).BuildTools
$dexdump = Join-Path $BuildTools $(if ($env:OS -eq 'Windows_NT') { 'dexdump.exe' } else { 'dexdump' })
if (-not (Test-Path -LiteralPath $dexdump)) { throw "No dexdump in $BuildTools" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$work = Join-Path ([IO.Path]::GetTempPath()) ("sohva-method-sizes-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
try {
    $archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Apk).Path)
    try {
        foreach ($entry in $archive.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' }) {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $work $entry.FullName))
        }
    } finally { $archive.Dispose() }

    $sizes = [Collections.Generic.List[object]]::new()
    $classPattern = [regex]'^\s+#\d+\s+: \(in (L[^;]+;)\)'
    $namePattern = [regex]"^\s+name\s+: '([^']*)'"
    $sizePattern = [regex]'^\s+insns size\s+: (\d+) 16-bit code units'
    foreach ($dex in Get-ChildItem -LiteralPath $work -Filter 'classes*.dex') {
        $dump = Join-Path $work ($dex.BaseName + '.txt')
        & $dexdump -d $dex.FullName > $dump
        if ($LASTEXITCODE -ne 0) { throw "dexdump failed on $($dex.Name)" }
        $class = $null; $method = $null
        foreach ($line in [IO.File]::ReadLines($dump)) {
            $m = $classPattern.Match($line)
            if ($m.Success) { $class = $m.Groups[1].Value; $method = $null; continue }
            if (-not $class -or -not ($class.StartsWith('Lcom/streammate/') -or $class.StartsWith('Lcom/sohva/'))) { continue }
            $m = $namePattern.Match($line)
            if ($m.Success -and -not $method) { $method = $m.Groups[1].Value; continue }
            $m = $sizePattern.Match($line)
            if ($m.Success -and $method) {
                $sizes.Add([pscustomobject]@{ Units = [int]$m.Groups[1].Value; Method = "$($class.TrimStart('L').TrimEnd(';').Replace('/', '.')).$method" })
                $method = $null
            }
        }
    }
} finally {
    Remove-Item -LiteralPath $work -Recurse -Force
}

if ($sizes.Count -eq 0) { throw 'No app methods were read; the check proved nothing.' }
$largest = @($sizes | Sort-Object Units -Descending | Select-Object -First 5)
$failAt = [int]($LimitUnits * $FailAtShare)
Write-Host "Largest app methods (ART compiles up to $LimitUnits code units ahead of time):"
$largest | ForEach-Object { Write-Host ("  {0,6}  {1}" -f $_.Units, $_.Method) }
$over = @($sizes | Where-Object { $_.Units -gt $failAt })
if ($over.Count -gt 0) {
    Write-Host ""
    Write-Host "Too close to ART's limit: split these composables before they stop compiling ahead of time." -ForegroundColor Red
    $over | ForEach-Object { Write-Host ("  {0,6}  {1}" -f $_.Units, $_.Method) -ForegroundColor Red }
    exit 1
}
Write-Host ("All {0} app methods are under {1} code units." -f $sizes.Count, $failAt) -ForegroundColor Green
