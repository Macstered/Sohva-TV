# Performance log

Measurements per milestone, as [plan/07 §6.8](rebuild/plan/07-performance.md) describes. One row
per measurement; compare CPU time per phase on the emulator, frame times only on hardware.

| Date | Commit | Variant | Compile state | Device / image | Fixture | Phase | Metric | Value | Beta 23 reference |
|---|---|---|---|---|---|---|---|---|---|
| 2026-09-24 | M0 step 1 | release (debug-signed copy) | verify (`cmd package compile -m verify -f`) | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p | none | cold start, empty shell (`am start -W`) | TotalTime | 181 ms | beta 23 emulator cold launch 562 ms (plan/07 §3.1) |
| 2026-09-24 | M0 step 1 | release | – | – | – | size | APK bytes (unsigned) | 1,125,070 | beta 23: 7,841,731 |
| 2026-09-24 | M0 step 1 | release | – | – | – | size | largest method, code units | 3,342 (library) of 8,777 methods | beta 23 app largest 8,949 |
