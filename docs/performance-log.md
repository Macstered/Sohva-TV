# Performance log

Measurements per milestone, as [plan/07 §6.8](rebuild/plan/07-performance.md) describes. One row
per measurement; compare CPU time per phase on the emulator, frame times only on hardware.

| Date | Commit | Variant | Compile state | Device / image | Fixture | Phase | Metric | Value | Beta 23 reference |
|---|---|---|---|---|---|---|---|---|---|
| 2026-09-24 | M0 step 1 | release (debug-signed copy) | verify (`cmd package compile -m verify -f`) | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p | none | cold start, empty shell (`am start -W`) | TotalTime | 181 ms | beta 23 emulator cold launch 562 ms (plan/07 §3.1) |
| 2026-09-24 | M0 step 1 | release | – | – | – | size | APK bytes (unsigned) | 1,125,070 | beta 23: 7,841,731 |
| 2026-09-24 | M0 step 1 | release | – | – | – | size | largest method, code units | 3,342 (library) of 8,777 methods | beta 23 app largest 8,949 |
| 2026-09-24 | 067a182+ | benchmarkRelease (R8) | none (`CompilationMode.None`) | emulator sohva_rebuild_tv30, API 30 TV x86 | none | cold start, 10 iterations | time to initial display, median | 375 ms (357–405) | beta 23 emulator cold launch 562 ms |
| 2026-09-24 | 067a182+ | benchmarkRelease (R8) | none | same | none | cold start, 10 iterations | time to full display (app drawn), median | 696 ms (659–724) | – |
| 2026-09-24 | 067a182+ | benchmarkRelease (R8) | baseline profile required | same | none | cold start, 10 iterations | time to initial display, median | 357 ms (338–698) | – |
| 2026-09-24 | 067a182+ | benchmarkRelease (R8) | baseline profile required | same | none | cold start, 10 iterations | time to full display, median | 653 ms (611–1,231) | – |
| 2026-09-24 | 067a182+ | debug | JIT | same | none | start state off the main thread | snapshot / tier / ground | 30–62 / 1–3 / 0–15 ms | – |
| 2026-09-24 | 067a182+ | – | – | – | – | generated baseline profile | rules (app packages: shell 205, Home 94, design 373, data 65, model 112) | 14,573; startup profile 14,183 | beta 23 hand-written: 45 wildcard rules |
| 2026-09-24 | 537cad9+ | benchmarkRelease (R8) | full (`CompilationMode.Full`) | emulator sohva_rebuild_tv30 | guide spike, 56,000 rows | 30 presses along a row | main-thread `doFrame` per press: A canvas row / B beta 23 cells | 2.47 / 4.50 ms (budget ≤ 7) | beta 23 guide 21.0 ms (plan/07 §3.2) |
| 2026-09-24 | 537cad9+ | benchmarkRelease (R8) | full | same | same | 30 presses between rows | main-thread `doFrame` per press: A / B | 10.35 / 10.19 ms (budget ≤ 11) | beta 23 guide 31.7 ms |
| 2026-09-24 | 537cad9+ | benchmarkRelease (R8) | full | same | same | per press along a row, A / B | recomposition; measure and layout | 0.02 / 0.35 ms; 0.00 / 0.64 ms | – |
| 2026-09-24 | 537cad9+ | benchmarkRelease (R8) | full | same | same | per press between rows, A (channel cell on its own node) | total; programme drawing; channel drawing; layout; prefetch | 10.29; 1.41; 1.02; 0.82; 0.95 ms | – |
| 2026-09-24 | 265509f+ (compileSdk 37, Compose 1.12) | benchmarkRelease (R8) | baseline profile required | emulator sohva_rebuild_tv30 | none | cold start, 10 iterations | time to initial / full display, median | 355.8 / 656.1 ms (before: 356.7 / 653.3) | – |
| 2026-09-24 | 265509f+ (compileSdk 37, Compose 1.12) | benchmarkRelease (R8) | none | same | none | cold start, 10 iterations | time to initial / full display, median | 364.4 / 677.9 ms (before: 375.0 / 696.3) | – |
| 2026-09-24 | 265509f+ (compileSdk 37, Compose 1.12) | release | – | – | – | size | APK bytes | 2,320,481 (before 2,339,240) | – |
