[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Ffmpeg)
$ErrorActionPreference = 'Stop'
$addonRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$addonFixtures = Join-Path $addonRoot 'app/build/generated/addonPlaybackFixtures/playback'
New-Item -ItemType Directory -Path $addonFixtures -Force | Out-Null
# Entirely generated color bars and tone; no provider/user/copyrighted media.
& $Ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=320x180:rate=15' -f lavfi -i 'sine=frequency=440:sample_rate=48000' -t 45 -c:v libx264 -preset ultrafast -crf 32 -g 30 -pix_fmt yuv420p -c:a aac -b:a 48k -movflags +faststart (Join-Path $addonFixtures 'fixture.mp4')
if ($LASTEXITCODE -ne 0) { throw 'Synthetic MP4 generation failed.' }
Push-Location $addonFixtures
try {
    & $Ffmpeg -hide_banner -loglevel error -y -i fixture.mp4 -c copy -hls_time 2 -hls_list_size 0 -hls_segment_filename 'segment%03d.ts' fixture.m3u8
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic HLS generation failed.' }
    & $Ffmpeg -hide_banner -loglevel error -y -i fixture.mp4 -c copy -seg_duration 2 -use_template 1 -use_timeline 1 -f dash fixture.mpd
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic DASH generation failed.' }
    # Real embedded text tracks, including a non-default Finnish track, exercise
    # the production Media3 selector rather than just checking a language label.
    $addonFinnishSub = Join-Path $PSScriptRoot 'fixtures/addon-subtitle-fi.srt'
    $addonEnglishSub = Join-Path $PSScriptRoot 'fixtures/addon-subtitle-en.srt'
    & $Ffmpeg -hide_banner -loglevel error -y -i fixture.mp4 -i $addonFinnishSub -i $addonEnglishSub -map 0 -map 1 -map 2 -c copy -metadata:s:a:0 language=eng -metadata:s:s:0 language=fin -metadata:s:s:1 language=eng -disposition:s:0 0 -disposition:s:1 default fixture-embedded.mkv
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic embedded subtitle generation failed.' }
    & $Ffmpeg -hide_banner -loglevel error -y -i fixture.mp4 -i $addonEnglishSub -map 0 -map 1 -c copy -metadata:s:a:0 language=eng -metadata:s:s:0 language=eng -disposition:s:0 0 fixture-secondary.mkv
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic secondary subtitle generation failed.' }
    & $Ffmpeg -hide_banner -loglevel error -y -i fixture.mp4 -i (Join-Path $PSScriptRoot 'fixtures/addon-subtitle-timing.srt') -i (Join-Path $PSScriptRoot 'fixtures/addon-subtitle-commentary.srt') -map 0 -map 1 -map 2 -c copy -metadata:s:a:0 language=eng -metadata:s:s:0 language=fin -metadata:s:s:0 title=Dialogue -metadata:s:s:1 language=fin -metadata:s:s:1 title=Commentary -disposition:s:0 default -disposition:s:1 0 fixture-timing.mkv
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic timing subtitle generation failed.' }
} finally { Pop-Location }
Write-Host 'Generated synthetic MP4/HLS/DASH and embedded-subtitle assets for the Lab test APK only.'
