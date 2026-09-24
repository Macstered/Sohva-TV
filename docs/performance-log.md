# Performance log

Measurements per milestone, as [plan/07 §6.8](rebuild/plan/07-performance.md) describes. One row
per measurement; compare CPU time per phase on the emulator, frame times only on hardware.

| Date | Commit | Variant | Compile state | Device / image | Fixture | Phase | Metric | Value | Beta 23 reference |
|---|---|---|---|---|---|---|---|---|---|
| 2026-09-24 | M0 step 1 | release (debug-signed copy) | verify (`cmd package compile -m verify -f`) | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p | none | cold start, empty shell (`am start -W`) | TotalTime | 181 ms | beta 23 emulator cold launch 562 ms (plan/07 §3.1) |
| 2026-09-24 | M0 step 1 | release | – | – | – | size | APK bytes (unsigned) | 1,125,070 | beta 23: 7,841,731 |
| 2026-09-24 | M0 step 1 | release | – | – | – | size | largest method, code units | 3,342 (library) of 8,777 methods | beta 23 app largest 8,949 |
| 2026-09-24 | c6e40bc+ | benchmarkRelease (R8) | none (`CompilationMode.None`) | emulator sohva_rebuild_tv30, API 30 TV x86 | none | cold start, 10 iterations | time to initial display, median | 375 ms (357–405) | beta 23 emulator cold launch 562 ms |
| 2026-09-24 | c6e40bc+ | benchmarkRelease (R8) | none | same | none | cold start, 10 iterations | time to full display (app drawn), median | 696 ms (659–724) | – |
| 2026-09-24 | c6e40bc+ | benchmarkRelease (R8) | baseline profile required | same | none | cold start, 10 iterations | time to initial display, median | 357 ms (338–698) | – |
| 2026-09-24 | c6e40bc+ | benchmarkRelease (R8) | baseline profile required | same | none | cold start, 10 iterations | time to full display, median | 653 ms (611–1,231) | – |
| 2026-09-24 | c6e40bc+ | debug | JIT | same | none | start state off the main thread | snapshot / tier / ground | 30–62 / 1–3 / 0–15 ms | – |
| 2026-09-24 | c6e40bc+ | – | – | – | – | generated baseline profile | rules (app packages: shell 205, Home 94, design 373, data 65, model 112) | 14,573; startup profile 14,183 | beta 23 hand-written: 45 wildcard rules |
| 2026-09-24 | 6788dfb+ | benchmarkRelease (R8) | full (`CompilationMode.Full`) | emulator sohva_rebuild_tv30 | guide spike, 56,000 rows | 30 presses along a row | main-thread `doFrame` per press: A canvas row / B beta 23 cells | 2.47 / 4.50 ms (budget ≤ 7) | beta 23 guide 21.0 ms (plan/07 §3.2) |
| 2026-09-24 | 6788dfb+ | benchmarkRelease (R8) | full | same | same | 30 presses between rows | main-thread `doFrame` per press: A / B | 10.35 / 10.19 ms (budget ≤ 11) | beta 23 guide 31.7 ms |
| 2026-09-24 | 6788dfb+ | benchmarkRelease (R8) | full | same | same | per press along a row, A / B | recomposition; measure and layout | 0.02 / 0.35 ms; 0.00 / 0.64 ms | – |
| 2026-09-24 | 6788dfb+ | benchmarkRelease (R8) | full | same | same | per press between rows, A (channel cell on its own node) | total; programme drawing; channel drawing; layout; prefetch | 10.29; 1.41; 1.02; 0.82; 0.95 ms | – |
| 2026-09-24 | b7e548e+ (compileSdk 37, Compose 1.12) | benchmarkRelease (R8) | baseline profile required | emulator sohva_rebuild_tv30 | none | cold start, 10 iterations | time to initial / full display, median | 355.8 / 656.1 ms (before: 356.7 / 653.3) | – |
| 2026-09-24 | b7e548e+ (compileSdk 37, Compose 1.12) | benchmarkRelease (R8) | none | same | none | cold start, 10 iterations | time to initial / full display, median | 364.4 / 677.9 ms (before: 375.0 / 696.3) | – |
| 2026-09-24 | b7e548e+ (compileSdk 37, Compose 1.12) | release | – | – | – | size | APK bytes | 2,320,481 (before 2,339,240) | – |
| 2026-09-24 | 98b2905+ | debug (instrumentation, `OwnerScaleImportTest`) | JIT | emulator sohva_rebuild_tv30, API 30 TV x86 | owner-scale M3U (`playlist.m3u`, gzip guide via `url-tvg`) | playlist import, scope TV and VOD | wall time; rows | 15.9 s; 50,546 channels (5,618 in "· Movies"/"· Series" groups go to the catalogue by the substring rule) (budget 60 s) | beta 23 whole fixture incl. 30,000 films ≈ 90 s on the emulator |
| 2026-09-24 | 98b2905+ | same | JIT | same | same | guide import | wall time; programmes kept | 13.8 s; 129,600 of 165,600 (budget 90 s) | beta 23 35 s unfiltered → 12 s filtered for one feed |
| 2026-09-24 | 98b2905+ | same | JIT | same | same | playlist import, nothing changed | wall time; rows written | 4.4 s; 0 | – |
| 2026-09-24 | 98b2905+ | same | JIT | same | owner-scale M3U VOD (`vod.m3u`) | catalogue import | wall time; rows | 60.9 s; 200,000 films, 1,500 series, 18,000 episodes (budget 180 s for 200,000 films) | beta 23: the 200,000-film catalogue ran out of memory (lessons 1.1) |
| 2026-09-24 | 98b2905+ | same | JIT | same | same | catalogue import, nothing changed | wall time | 15.5 s | – |
| 2026-09-24 | 98b2905+ | same | JIT | same | owner-scale Xtream variant | playlist / guide / catalogue | wall time; rows | 13.3 s, 56,164; 15.0 s, 146,004 (10 % catch-up channels keep more past); 36.1 s, 200,000 films + 1,500 series | – |
| 2026-09-24 | 98b2905+ | same | JIT | same | same | catalogue import, nothing changed | wall time | 5.2 s | – |
| 2026-09-24 | 98b2905+ | same | JIT | same | all of the above in sequence (sampled every 250 ms) | whole process | Java heap max | 30 MB of 192 MB (budget 128 MB) | beta 23 was killed every four minutes on the 200,000-film catalogue |
| 2026-09-25 | 4b410a9 | benchmarkRelease (R8) | full (`CompilationMode.Full`) | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p, quiet host | owner-scale guide fixture (56,164 channels in 800 groups, 165,600 programmes; `:measure:fixture`) | 30 presses between rows, real guide, first group | main-thread CPU (Running inside `doFrame`) per press, median of 5 | 4.78 ms (4.53–4.84; budget ≤ 11) | beta 23 guide 31.7 ms `doFrame` per press |
| 2026-09-25 | 4b410a9 | benchmarkRelease (R8) | full | same | same | 30 presses along a row | main-thread CPU per press, median of 5 | 3.14 ms (3.01–3.26; budget ≤ 7) | beta 23 guide 21.0 ms |
| 2026-09-25 | 4b410a9 | benchmarkRelease (R8) | full | same | same | per press between rows / along a row | `doFrame` wall time (includes sleeping on the emulator's software GPU: RenderThread `DrawFrame` ≈ 14 ms, half of it `eglSwapBuffers`) | 10.0 / 8.2 ms | M0 spike A: 10.35 / 2.47 ms |
| 2026-09-25 | 4b410a9 | benchmarkRelease (R8) | full | same | same | per press between rows | row drawing; row cell; hero; recomposition; measure and layout | 0.55; 1.06; 0.29; 0.71; 2.05 ms | – |
| 2026-09-25 | 4b410a9 | benchmarkRelease (R8) | full | same, with the second emulator running device tests | same | per press between rows | main-thread CPU per press, median of 5 | 8.05 ms (4.64–14.81): CPU contention from the other emulator; not a figure to compare | – |
| 2026-09-25 | 92da009 | debug (instrumentation) | JIT | emulator sohva_rebuild_tv30 | test clip (H.264 + AAC, generated on the device) | live playback, overlays hidden, 15 s | UI frames drawn | 0 (a mutant that kept the info line composed drew 5 and failed) | spec 30 §9: zero |
