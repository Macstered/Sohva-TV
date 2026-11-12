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
| 2026-09-25 | M2 (after 5937084) | debug (instrumentation, `GuideOwnerScaleTest`) | JIT | emulator sohva_rebuild_tv30 | 56,000 channels in 800 groups, programmes for 700 | open the guide from Home (first group), open All channels, dial 5599, 10 presses down | Java heap max (sampled every 250 ms) | 45 MB of 192 MB (budget 64 MB steady) | beta 23 held a 56,164-row roster: about 45 MB for the roster alone |
| 2026-09-25 | same | same | JIT | same | same | key press on Home → first row focused; OK on All channels → first row focused | wall time | 669 ms; 418 ms (the All-channels page-key walk of 56,000 rows) | beta 23 on All channels about 4 s on the Shield |
| 2026-09-25 | M3 (after bbdecb2) | debug (instrumentation, `ChannelsOwnerScaleTest`) | JIT | emulator sohva_rebuild_tv30 | 56,000 channels in 800 groups, one source | open channel management from the guide; 450 presses down (three pages); A–Z; first move; next move | wall time | open 402 ms; 30.5 s for 450 presses (67 ms each, including the test's per-press wait); A–Z 215 ms; first move 19.0 s (positions all 56,000 channels, 28 transactions of 2,000); next move 61 ms | beta 23 held the whole roster in the screen and recomposed it per focus move |
| 2026-09-25 | same | same | JIT | same | same | the whole run (sampled every 250 ms) | Java heap max | 45 MB of 192 MB (budget 64 MB steady) | – |
| 2026-09-25 | baa0c69+ | benchmarkRelease (R8) | full (`CompilationMode.Full`) | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p, quiet host | owner-scale guide fixture (56,164 channels, one source; `:measure:fixture`) | `ChannelsBenchmark`: 30 presses down channel management's list from row 185 (crosses the first 200-row page; the editor follows each press) | main-thread CPU (Running inside `doFrame`) per press, median of 5 | 3.92 ms (3.76–4.25; budget ≤ 11); `doFrame` wall 11.8 ms per press, recomposition 1.6 ms, measure and layout 3.2 ms | beta 23 recomposed its whole roster on every focus move (spec 21 §8) |
| 2026-09-25 | c1ad9ee+ | benchmarkRelease (R8) | full (`CompilationMode.Full`) | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p, quiet host | owner-scale film library (200,000 films in 800 groups, 40,000 in "0 \| Blockbusters"; `:measure:fixture` `FilmFixture`) plus the guide fixture | `WallBenchmark.openBigGroup`: OK on the 40,000-film group's rail row | `Library:Load` (request to the first page of 120 published), median of 5 | 2.58 ms (2.02–3.20; budget first page drawn ≤ 700 ms) | beta 23 read the whole partition sorted, then re-sorted and folded it in Kotlin (spec 40 §9.2) |
| 2026-09-25 | same | same | full | same | same | `WallBenchmark.downTheBigGroup`: 30 presses down the 40,000 group from its first card (crosses the first page of 120) | main-thread CPU (Running inside `doFrame`) per press, median of 5 | 2.67 ms (2.60–2.80; budget ≤ 11); `doFrame` wall 8.3 ms per press; recomposition 0.5 ms; measure and layout 4.7 ms | beta 23 film browsing 419 ms main-thread CPU for its journey (23 Sept fix) |
| 2026-09-25 | same | same | full | same | same | same | Java heap max (whole app) / RSS anon | 19.6 MB / 65 MB | – |
| 2026-09-25 | m4-library (title cleaner) | JVM unit build | JIT, warm (third of three runs) | desktop JVM | 50,000 provider-like titles ("Silent Harbour n (2019)", "FIN \| Northern Line n", "…1080p", plain) / 50,000 generated messy titles | work key + search title per title | ns per title, hand-written vs beta 23's regexes | 2,714–2,799 vs 16,154–17,161 ns (×6); messy 3,790 vs 20,916 ns (×5.5). Short of spec 41 §9.1's ×10 on the JVM; the device's ICU regexes are slower than the JVM's, to be measured with the identity pass | beta 23 identity pass 3.1 s for 30,000 films on the emulator |
| 2026-09-25 | 00dc870+ | benchmarkRelease (R8) | full | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p | owner-scale film library (as 25 Sept) | `WallBenchmark.openBigGroup` after walls gained replacement titles, the replacement-title search and the per-page copies read (VOD-FR-27) | `Library:Load`, median of 5 | 3.55 ms (2.16–13.73; before 2.58; budget first page drawn ≤ 700 ms) | The copies read is one `work_key IN (…)` lookup of ≤ 120 keys per page on the io dispatcher |
| 2026-09-25 | same | same | full | same | same | `WallBenchmark.downTheBigGroup` (30 presses) | `doFrame` wall per press; recomposition; heap | 8.55 ms (before 8.3); 16.1 ms for the run (before 0.5 ms per press ≈ 15 ms); max heap 20.4 MB (before 19.6) | No change on the main thread: folding happens in the page read |
| 2026-09-25 | a6d0405 (before) / + hero lookup | benchmarkRelease (R8) | full | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p; second emulator idle | owner-scale guide fixture | `GuideBenchmark` before and after the hero's programme lookup (GUIDE-FR-64; metadata off, as in the fixture) | main-thread CPU per press (Running inside `doFrame`, `press_cpu.py`), median of 5 | between rows 5.66 → 6.17 ms (budget ≤ 11); along a row 4.25 → 4.08 ms (budget ≤ 7); `Guide:Hero` 0.32 → 0.37 ms per press | Along-row presses change the programme too and did not rise, so most of the between-rows difference is run-to-run spread. Both runs are above 4b410a9's 4.78 / 3.14 ms: the M3–M4 guide changes since then cost about 0.9 ms per press between rows; still within budget |
| 2026-09-25 | 0c3670a (before M4c) | debug (instrumentation, `OwnerScaleImportTest`) | JIT | emulator sohva_rebuild_tv30, API 30 TV x86 | owner-scale fixture | M3U catalogue; Xtream catalogue into a library that already holds the M3U copy | wall time | 273.3 s; 346.4 s (24 Sept: 60.9 s; 36.1 s) | Not re-measured through M4a/M4b: the wall, claim, work-key and similar-key indexes and the standing-copy pass had quadrupled it |
| 2026-09-25 | 771f439 | same | JIT | same | same | same, with M4c's fifteen title indexes | wall time; time inside write transactions | 381.8 s (367 s in writes); 708.4 s (standing copies 179 s) | Host model (`harness-out/index_cost.py`, exported schemas, 200,000 inserts): 1 index 1.5 s, 7 indexes 43 s, 15 indexes 103 s, 15 in memory 7.2 s: the cost is index pages rewritten per commit |
| 2026-09-25 | 771f439+ | same | JIT | same | same | ten title indexes, catalogue batches of 5,000 rows, standing-copy flips in 5,000-row transactions, playlist batches of 2,000 | wall time | M3U playlist 21.7 s (was 29.5); M3U catalogue 126.9 / 137.0 s (budget 180); Xtream catalogue into a full library 172.0 / 192.0 s; unchanged re-imports 32–35 s and 19–26 s; Java heap max 58–60 MB of 192 (budget 128) | Heap rose from 24 MB with the larger batches |
| 2026-09-25 | 5328b8f+ (GUIDE-13) | debug (instrumentation, `OwnerScaleImportTest`) | JIT | emulator sohva_rebuild_tv30, API 30 TV x86 | owner-scale fixture | channel order from the rules: ranks rewritten by the organisation pass, then written by the import itself | wall time | M3U playlist 33.4 s with the pass rewriting all 50,546 ranks → 21.9 s with the import writing them (before GUIDE-13 21.7 s); unchanged playlist 7.4 s; M3U catalogue 134.5 s; Xtream playlist 22.8 s; Xtream catalogue into the full library 184.0 s; Java heap max 55 MB | Only rules that reorder (group order A–Z, Z–A, Manual; a group sorted A–Z, Z–A or Manual) make the pass rewrite ranks |
| 2026-09-25 | m5-home (Search index) | debug (instrumentation, `OwnerScaleImportTest`) | JIT | emulator sohva_rebuild_tv30, API 30 TV x86 | owner-scale fixture | imports with Search's FTS4 index kept by insert triggers, then by one bulk catch-up per import | wall time | Triggers: M3U playlist 31.5 s, guide 16.4 s, M3U catalogue 202.3 s (over the 180 s budget), Xtream catalogue 257.1 s. Bulk catch-up: M3U playlist 22.5 s, guide 12.4 s, M3U catalogue 139.5 s (index 2.9 s), Xtream playlist 23.2 s, Xtream catalogue into the full library 192.5 s (index 3.2 s); unchanged catalogue 0.2–0.5 s of index work; heap 57 MB | Host model (`harness-out/fts_cost.py`, schema 8, 200,000 films): no index 16.7 s, insert trigger 46.3 s, bulk after 18.1 s |
| 2026-09-25 | m5-home (Search) | same | JIT | same | same library (400,000 films, 106,710 channels, 161,996 programmes, 3,000 series, 18,000 episodes) | each search group, warm, median of 5, terms "ha", "north", "signal kitchen", "zz" | wall time per group | live 2–13 ms, films 2–47 ms (max 55), series 2–3 ms, episodes 1–4 ms (budget 100 ms) | Films on a two-letter prefix are the slowest: the sort over the matches before the limit |
| 2026-09-25 | 76635d6 | benchmarkRelease (R8) | full (`CompilationMode.Full`) | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p, quiet host | owner-scale fixture (guide and film library) plus `HomeFixture`: 12 paused films, 6 recent channels | `HomeBenchmark.coldStartToFirstCard`, 5 cold starts | `home: cached resume ready` (diagnostics log, from process start); time to initial / full display, median | 973 ms (961–1,050; budget ≤ 1 s); 563 / 1,184 ms; first Continue watching card focused well inside the 4 s budget | Trace: the first frame ends about 480 ms after fork, mostly the emulator's software GL start-up and shader compiles; the resume read starts right after it (HOME-FR-26) and Home draws its rows on the next frame |
| 2026-09-25 | same | same | full | same | same | `HomeBenchmark.alongTheRow`: 30 presses along the Continue watching row (six right, six left) | main-thread CPU (Running inside `doFrame`, `harness-out/home_cpu.py`) per press, median of 5; recomposition; `Home:Screen` compositions | 5.24 ms (3.92–6.19; budget ≤ 7); 0.56 ms per press (the focused card and the hero only); 0 (the screen never recomposes per press, spec 02 §9.6) | beta 23 recomposed Home on every press (`a176f0d`) |
| 2026-09-25 | same | same | full | same | same | same | Java heap max / RSS anon | 8.2 MB / 50 MB | – |
| 2026-09-26 | 206455d (M4, main) / m5-home | benchmarkRelease (R8) | baseline profile required; none | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p | none (fresh install) | `StartupBenchmark`, 10 cold starts each, same harness and session | time to initial / full display, median, with profile; without compilation | M4 549 / 954 ms; 591 / 1,046 ms. M5 549 / 1,120 ms; 578 / 1,259 ms (budget: Home drawn ≤ 2 s Shield, ≤ 4 s low-end) | Initial display unchanged by M5; full display +166 ms with the profile: Home's first real frame (Welcome hero, plate, rail) where M4 drew an empty screen. The 356 ms of 24 Sept (M0) grew over M1–M4, not measured per milestone |
| 2026-09-26 | m5-home, profiles regenerated | same | baseline profile required (regenerated on sohva_rebuild_tv34 from `BaselineProfileGenerator`; it last dated from M0 and held almost none of the guide, walls, Home or Search) | same | same | same | time to initial / full display, median | 556 / 1,121 ms (before 549 / 1,120): start-up unchanged; the profile's gain is on the screens after it | – |
| 2026-09-26 | 206455d (M4, main) / m5-home | benchmarkRelease (R8) | full | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p; emulators 5554 and 5572 idle alongside | owner-scale guide fixture; film library | `GuideBenchmark`, `WallBenchmark`, `ChannelsBenchmark` after M5 (the journeys now reach the rail with Left), with M4 run again in the same session as the control | main-thread CPU per press, median of 5 | Guide between rows / along a row: M4 8.33 / 4.86 ms, M5 8.68 / 5.00 ms (budgets 11 / 7). Wall down the big group 3.45 ms; channel management 6.03 ms | All figures are higher than on 25 Sept (guide 6.17 / 4.08) for M4 as much as M5: host conditions, not M5. Home adds no main-thread work while another screen is open |
| 2026-09-26 | m6-profiles | debug (instrumentation, `GuideOwnerScaleTest`) | JIT | emulator sohva_rebuild_tv30, API 30 TV x86 | owner-scale guide (56,000 channels in 800 groups) and a second profile | Who is watching from the rail: OK on the other profile's tile until Home is back with its Continue watching settled; ten switches, each way in turn | wall time | median 189 ms (135–285; budget 1 s, spec 04 §11); the app's own log line `profile: home ready after switch` 41–131 ms | – |
| 2026-09-26 | same | same | JIT | same | same, with a profile limited to 400 of the 800 groups (28,000 channels), in the same process as the unrestricted run | open the guide from Home (first group); open All channels | wall time, restricted / unrestricted | first group 434 / 285 ms (an earlier run 503 / 317); All channels 322 / 364 ms (472 / 365); budget: the screen-open budget, ≤ 700 ms on the low-end class | The restricted run goes first in the class, on a colder process; the filter's own cost shows in All channels, where the restricted list has half the rows and is not slower. Heap max 44 MB |
| 2026-09-26 | m7-settings (backup) | debug (instrumentation, `SettingsBackupTest`) | JIT | emulator sohva_rebuild_tv30, API 30 TV x86 | one source and a theme (1,584-byte payload); beta 23's fixture file (4,284 bytes, two sources, a phone logo, two profiles) | save, open and restore through the answered pickers | wall time per phase (diagnostics log `backup:`) | save: read 36 ms, JSON 9 ms, encrypt 1,393 ms; open: decrypt 1,279–1,337 ms; restore after the open: 173 ms and 313 ms (with the logo and profiles) | Key derivation (210,000 PBKDF2-HMAC-SHA256 iterations, the platform provider) is nearly all of it, above spec 71's 0.2–1 s estimate. It runs once per operation behind the busy text; the iteration count is beta 23's and cannot drop without breaking its files |
| 2026-09-26 | m7-settings (backup at scale) | debug (instrumentation, `BackupOwnerScaleTest`) | JIT | emulator sohva_rebuild_tv30, API 30 TV x86 | 200,000 films in four groups, 300 film rules on work keys | save through Settings' backup service | file size, wall time, Java heap before/after | 53,092 bytes (budget: tens of kilobytes, BACKUP-13); 1,500 ms (read 40, JSON 47, encrypt 1,406); heap 36 → 39 MB of 192 | The export reads the household's rows only; the size follows the rules and their self aliases, not the library |
| 2026-09-26 | m8-sport (pairing) | debug (instrumentation, `PairingOwnerScaleTest`) | JIT | emulator sohva_rebuild_tv30, API 30 TV x86 | 56,164 channels, 112,328 programmes with 2,150-character descriptions, all in the window of 30 games | one paged pairing scan (channels 256, programmes 64 per page) | wall time; pages; candidates | 27.9 s; 220 channel and 1,975 programme pages; 168,492 candidates (first run with regex tokenising: 126.7 s) | beta 23 paged fix 54 s with 220 and 1,975 queries; beta 23 unpaged ran out of memory after 155 s |
| 2026-09-26 | m8-sport (pairing) | same | JIT | same | same | the scan | Java heap peak (sampled every 50 ms), longest main-thread heartbeat gap | 12.0 MB (baseline 3.0 MB; budget 16 MB); 27 ms | beta 23 paged fix 12.03 MiB; unpaged 192 MiB and an OutOfMemoryError |
| 2026-09-26 | m8-sport (pairing) | same | JIT | same | same | cancel after 20 channel pages | queries after the cancel | 0 (budget: at most the page in flight) | – |
| 2026-09-26 | m8-sport (quota) | JVM (`SportsRepositoryTest`) | – | – | one followed sport with a live game | 24 hours of Today on screen, polled every 5 minutes through the cache | requests to the sport's API | 72 (budget 100 per sport per day) | beta 23 at most 72 per day while watched |
| 2026-09-26 | m9-discover (8f22032+) | debug (instrumentation, `DiscoverOwnerScaleTest`) | JIT | emulator sohva_rebuild_tv30, API 30 TV x86 | synthetic addon: 40 catalogs × 1,000 titles per response (≈180 KB each), posters answering 404 | Home, open Discover, 31 Downs through the shelves, 31 Ups, Right to Show all, the 1,000-title grid, 20 Downs in it | requests | Home 0; opening 3 catalogs; 34 catalog requests after 31 Downs, each once; 0 more after returning over the 24-shelf bound; grid: no second page (1,000 cap from the first); 2 hero details lookups (where focus rested); 0 stream/subtitle | beta 23: shelves fetched on composition of every visible row; whole-tree parse per catalog |
| 2026-09-26 | same | same | JIT | same | same | the whole run (sampled every 50 ms) | Java heap peak | 33 MB and 38 MB in two runs (budget 64 MB) | – |
| 2026-09-26 | same | same | JIT | same | same | main-thread longest gap per phase (16 ms heartbeat) | ms | Home cold start 658; first Discover open 209; Downs 158; Ups 99; grid open 82; grid Downs 59 | – |
| 2026-09-26 | same | debug (instrumentation, `DiscoverPlaybackTest`, `DiscoverSubtitleTest`) | JIT | same | 20 s H.264/AAC clip from the test's own server over the emulator's address | addon start-up milestones from the diagnostics log (stream-ready / subtitles-ready / first-frame / playing, ms since start) | ms | no subtitle preferences: 585 / 585 / 588 / 620 and 609 / 611 / 632 / 658; automatic subtitle download: 775 / 1,478 / 1,519 / 1,526 (subtitle stage 703 ms within its 5 s budget) | Shield comparison pending (owner's device, needs the owner's go) |
| 2026-09-27 | 9a08354+ (shield-fixes) | debug (instrumentation via `am instrument`) | JIT | owner's NVIDIA SHIELD Android TV (mdarcy, Android 11), Finnish interface | 20 s H.264/AAC clip from the test's own server on the Shield | addon start-up milestones (stream-ready / subtitles-ready / first-frame / playing, ms) | ms | 621 / 622 / 628 / 631 and 792 / 792 / 801 / 804; app's share READY → first frame 7–9 ms (emulator 3–21 ms): within the spec's 1 s | – |
| 2026-09-27 | same | same | JIT | same | same | end of stream with frame-rate matching on (display to 25 Hz) | result | before the fix the player paused itself ("audio becoming noisy" when the HDMI audio output was removed and re-added about 1 s after the switch), no end in 45 s; with matching off both end tests passed; with the fix (resume after our own switch) the Discover playback, subtitle and library playback suites pass on the Shield (9 + 2 tests) | – |
| 2026-09-27 | m10-trakt (part 6) | debug (instrumentation, `TraktOwnerScaleTest`) | JIT | emulator sohva_rebuild_tv30, API 30 TV x86 | fake Trakt: 1,000 watched films (4 pages of 250), 300 shows × 3 seasons × 20 episodes (3 pages of 100), 500 paused films; 19,500 cache rows | forced full sync with Home up | wall time, app CPU time | 4,156 ms; 5,691 ms CPU (parsing and diff on background threads) | beta 23 without paging read 100 films and no episodes (spec 51 FR-23) |
| 2026-09-27 | same | same | JIT | same | same | the full sync (sampled every 50 ms; 16 ms heartbeat) | Java heap peak; longest main-thread gap | 24–32 MB in two runs (budget 64 MB); 27–31 ms | – |
| 2026-09-27 | same | same | JIT | same | same | sync with nothing new on Trakt | wall time; rows written | 5–8 ms (one `last_activities` call); 0 | beta 23 the same check |
| 2026-09-27 | same | same | JIT | same | same | sync after all 500 pauses moved | wall time, CPU; lists fetched | 1,058 ms / 1,215 ms CPU walking all rows → 923 ms / 980 ms CPU reading only paused rows and the new keys; playback list only | beta 23 fetched all three lists and rewrote the whole table |
| 2026-09-27 | same | same | JIT | same | same | Continue watching read with 500 Trakt-paused rows | wall time | 23–31 ms | – |
| 2026-09-27 | m11-release (59d0f74+) and M5 (249851d) side by side | benchmarkRelease (R8) | with baseline profile; without compilation | emulator sohva_rebuild_tv30, API 30 TV x86, 1080p; the host ran about twice as slow as on 26 Sept (same AVD) | none (fresh install) | `StartupBenchmark`, 10 cold starts each, the two builds one after the other | time to initial / full display, median | M11 1,062 / 2,198 ms with profile, 1,160 / 2,522 ms without; M5 on the same host 1,039 / 2,116 ms and 1,124 / 2,431 ms. The app's own growth since M5: +2–4 %. The absolute figures are not comparable with the 26 Sept rows | M5 26 Sept: 549 / 1,120 ms |
| 2026-09-27 | m11-release | debug (instrumentation, `GuideOwnerScaleTest`, `-e ownerScale true`) | JIT | same emulator and host | owner-scale guide, a second profile | guide open, restricted guide, ten profile switches | wall time; Java heap | first group 386 ms, All channels 370 ms; restricted 605 / 446 ms; switch to Home median 276 ms (195–431); heap max 47 MB of 192 (budget 64) | M6 switch median 189 ms on the faster host |
| 2026-09-27 | m11-release | debug (`ChannelsOwnerScaleTest`) | JIT | same | 56,000 channels, one source | open; 450 presses; A–Z; moves | wall time; heap | open 607 ms; 101 ms per press including the test's wait; A–Z 263 ms; next move 128 ms; heap max 42 MB | M3 67 ms per press on the faster host |
| 2026-09-27 | m11-release | debug (`PairingOwnerScaleTest`) | JIT | same, with two agents reading files on the host at the time | 56,164 channels, 112,328 programmes with long descriptions | one paged pairing scan; cancel | wall time; heap | 74.8 s; peak +8.1 MB (budget 16 MB); main-thread longest gap 69 ms; cancel stops at once | M8 27.9 s on the faster host; heap unchanged. To be repeated on the quiet host and the slow box |

## 28 September 2026: build 103 on the owner's Elisa Viihde box (upgrade over beta 23)

ZTE B866V2F01, Android 12, 32-bit userland (armeabi-v7a), 3 GB, 1080p at 59.94 Hz (two vsyncs = 33 ms).
Installed with `adb install -r` (no install profile: `verify`, the sideload case). No accessibility services.

| Measure | Box | Budget (plan/07) |
|---|---|---|
| Tap → first frame (`Displayed`) | 414 ms | ≤ 1.2 s |
| Tap → Home drawn (`Fully drawn`), including the 1.6 s beta 23 import | 1,946 ms | ≤ 4 s |
| Home's Continue watching read (`home: cached resume ready`, from the launch) | 2,009 ms | – |
| Beta 23 import (settings restore 298 ms) | ≈ 1.6 s | – |
| First catalogue import, 12,199 rows (write transactions 22.8 s), while the viewer started playback | 34 s | – |
| Frames over the whole session (first start uncompiled, imports, the owner's browsing and playback): 4,632 | janky 7.0 %; p50 19, p90 44, p95 69, p99 150 ms | press → frame ≤ 2 vsyncs |
| Total PSS after browsing | 140 MB (Java 41, native 46, graphics 24) | – |

The frame counters cover everything since the first start, so they cannot separate key presses from
loading and the uncompiled first minutes; p90 44 ms is above two vsyncs overall. The owner reports
browsing, playback and the import during playback as fine (SRC-42's observation).

## 28 September 2026: M12 phase A, the Home layout (before and after, same session)

Emulator sohva_rebuild_tv30 (API 30 TV x86, 1080p), cold-booted, `benchmarkRelease` (R8), `CompilationMode.Full`, the
owner-scale fixture plus `HomeFixture` (`HomeBenchmark`, 5 iterations each). Before = `main` at 7da3ba3; after = branch
`m12-home-rows` with the default layout (the layout is read on every Home entry either way).

| Measure | Before | After | Budget |
|---|---|---|---|
| Cold start → first frame (time to initial display), median | 398 ms (342–422) | 391 ms (367–404) | – |
| Cold start → Home drawn, first card focused (time to full display), median | 863 ms (787–931) | 810 ms (770–857) | ≤ 4 s low-end |
| Main-thread CPU per press along Continue watching (Running in `doFrame`), median | 2.05 ms (1.89–2.15) | 2.07 ms (1.87–2.41) | ≤ 8 ms |
| `Home:Screen` compositions during the 30 presses | 0 | 0 | 0 (spec 02 §9.6) |
| `Home:Screen` compositions over a cold start | 6 | 6 | – |

Within the stand-in's noise: HOME-FR-92 holds. A hidden row does no work (unit tests count the reads; device tests with a
gated recent-channel read check the first focus both before and after a key press).

## 28 September 2026: M12 phase B, Trakt rows (before and after, same session)

Same harness as phase A (`HomeBenchmark`, 5 iterations each, `benchmarkRelease`, emulator sohva_rebuild_tv30 cold-booted).
Before = `main` at 9aaf4fc; after = branch `m12b-trakt-rows` at bf2fd26, default layout (no added rows: the benchmark
fixture has no Trakt data, and an added row costs a stored-list read after Continue watching settles, off the main thread).

| Measure | Before | After | Budget |
|---|---|---|---|
| Cold start → first frame, median | 393 ms (372–415) | 389 ms (361–408) | – |
| Cold start → Home drawn, first card focused, median | 848 ms (811–860) | 848 ms (813–868) | ≤ 4 s low-end |
| Main-thread CPU per press along Continue watching, median | 2.02 ms (1.94–2.17) | 2.01 ms (1.95–2.22) | ≤ 8 ms |
| `Home:Screen` compositions during the 30 presses | 0 | 0 | 0 |

No change. Checked by hand on the emulator against the real Trakt: "Trending movies" added in Settings › Home filled
Home's row with 30 titles a few seconds later, read with the app's key and no account.

## 29 September 2026: populated Home on 1 GB, local build 115

`0.2.0-beta.5`, source `0634cef`, local candidate only. Emulator `sohva_rebuild_tv30`, API 30 TV x86,
1920×1080 at xhdpi, cold-booted with the harness's temporary 1,024 MiB RAM override. The guest reports
997,544 KiB total RAM, a 192 MiB memory class, a 256 MiB maximum heap and `isLowRamDevice=false`.
The old player/artwork predicate therefore chose standard limits while startup chose LOW. The shared
cached tier now gives startup, artwork and playback the same conservative decision.

The benchmark fixture contains eight encrypted cached Trakt lists of 30 unique films each, over a
200,000-film library. Half of the 240 cards have indexed library matches. Its 480 fictional local JPEGs
(500×750 posters and 1280×720 backdrops) exercise real decoding without network variance. They are
benchmark-only data. The fixture activity and its Room dependency are absent from shipping variants.

All runs use minified `benchmarkRelease` and `CompilationMode.Full`. There are ten cold process starts
per startup case and five runs per browsing case: 30 horizontal presses, 14 vertical presses across all
eight rows, or 12 horizontal presses with a 600 ms rest for each hero picture. Every press checks the
exact focused card. Host builds were stopped during measurement. Browsing begins at the second card
because accessibility clips the first narrow poster behind the expanded rail's bounds; the separate
Compose tests cover returning from the first card to the rail.

Three measured stages use the same fixture and harness: the old heap-only artwork/player predicate;
the shared tier alone (`9e9f4a7`); and the shared tier plus the reduced-motion composition clock
(`0634cef`). The first is a deliberate control build with the old predicate, not the untouched branch
base. The intermediate result showed that reducing image memory alone did not meet the vertical
navigation CPU target.

| Measure, median of five runs | Old predicate | Shared tier | Final build 115 | Final range |
|---|---|---|---|---|
| Horizontal navigation, main-thread frame CPU per press | 3.21 ms | 3.83 ms | 1.85 ms | 1.60–2.25 ms |
| Vertical navigation, main-thread frame CPU per press | 13.52 ms | 10.00 ms | 3.18 ms | 2.96–3.83 ms |
| Hero journey, main-thread frame CPU per move including its rest | 24.94 ms | 14.07 ms | 4.27 ms | 4.00–4.81 ms |
| Horizontal journey, rendered frames | 114 | 119 | 59 | 59–60 |
| Vertical journey, rendered frames | 154 | 139 | 39 | 33–44 |
| Hero journey, rendered frames | 377 | 191 | 48 | 48–50 |
| Hero journey, peak anonymous RSS | 111.50 MiB | 58.73 MiB | 58.23 MiB | maximum 59.22 MiB |
| Vertical journey, peak anonymous RSS | 101.04 MiB | 65.21 MiB | 56.03 MiB | maximum 56.11 MiB |
| Whole-Home compositions during every browsing run | 0 | 0 | 0 | 0 |

CPU here means scheduler Running time intersected with main-thread `Choreographer#doFrame` slices,
extracted with Perfetto by `tools/home_benchmark_report.py`, consistent with the earlier Home CPU
proxy. Each range is the range of five per-run averages, not a percentile of individual presses. All
five final horizontal and vertical averages meet the 8 ms target. The hero figure includes artwork
settling during the rest and is not immediate key latency. Total main-thread CPU, including input,
prefetch and UIAutomator accessibility queries outside frames, is higher: final medians 10.61, 24.34
and 15.76 ms per press for horizontal, vertical and hero journeys respectively. Software-GPU frame
times on this emulator cannot establish the real boxes' press-to-frame latency.

The final maximum managed Java heap across the five scenarios is 21,478 KiB (20.98 MiB), below the
64 MiB steady-state budget. Anonymous RSS includes native allocations and decoded images; it is a
different measure from managed heap and is not total PSS.

| Cold start, median of ten (ms) | Old predicate | Shared tier | Final build 115 | Final range |
|---|---|---|---|---|
| Default Home, initial display | 949 | 408 | 371 | 354–873 |
| Default Home, full display | 1,645 | 853 | 759 | 743–1,507 |
| Populated Home, initial display | 990 | 435 | 366 | 346–897 |
| Populated Home, full display | 1,738 | 925 | 769 | 685–1,534 |

The default control shifted substantially between runs too, so the apparent twofold startup gain
must not be attributed wholly to these changes. Host/emulator variation is a material part of it.
Within the final run, populated Home adds about 10 ms to median full display relative to default Home;
both cases remain below the 4 s low-end budget. Full compilation isolates UI work; this run does not
measure an uncompiled sideload's first launch or the network cost of refreshing real Trakt lists.

Reduced motion now reaches the actual lifecycle-owned Compose animation clock. A frame-by-frame
Home regression observed 15 scroll positions before the change and at most three after it, while
still proving that the row moved. The zero-scale buffering test also failed before the fix and now
passes: the indicator takes a step every 150 ms without an infinite per-frame animation. The window
factory uses an internal Compose API against the pinned BOM. Any Compose upgrade must rerun this
regression and the populated benchmark on the real app clock; see `docs/decisions.md`.

Validation for this candidate: 692 unit tests in 133 suites, 21 targeted emulator tests (Home, Trakt
rows, shell Back/focus, device-tier wiring and both subtitle players), and all five macrobenchmark
scenarios pass. `tools/check_all.py` passes, including golden screenshots, all configured lint/build
checks, Play APK/AAB artifact gates, public-source and secret audits, and release audits. Both release
and Play APKs passed ART verification and launch smoke checks on the emulator. Release APK size is
6,167,468 bytes; its largest app method is 3,836 code units, below the 9,500 gate.

Accepted local evidence (ignored harness outputs):

- Old predicate: `harness-out/home-populated-before/results`, `report.json` in its parent, and
  `harness-out/home-populated-before-v4.log`. Snapshot APK SHA-256:
  `b6fdccb4177ca27676010eba308990710d836eb053bb71d7452284abe87c10ea`.
- Shared tier: `harness-out/home-populated-after/results`, `report.json` in its parent, and
  `harness-out/home-populated-after.log`. Snapshot APK SHA-256:
  `971183de547a197ab8fb7294baf2341fb30a4e2b5effdd2cb3b9e085c1d094d3`.
- Final: `harness-out/home-populated-clock-final/results`, `report.json` in its parent, and
  `harness-out/home-populated-clock-final.log`. Snapshot APK SHA-256:
  `647d7d7fdd25e7e4680df8f1e9ab0cd74986458fa57a25b425d87f9bd30d447f`.
- Checks: `harness-out/check_all.log`, `harness-out/motion-clock-final-device-tests.log`,
  `harness-out/motion-clock-release-verify.log`, `harness-out/motion-clock-play-verify.log`.
- Shipping release APK SHA-256:
  `76f3fda95b510897c1fdfaa0b901b11a2797c3b3af74f8b3ab966f9775d70104`.

Shield and Elisa validation remains the next owner-approved step. No household device was used for
this work. Check the actual selected tier and reduced-motion setting in diagnostics as well as focus,
artwork and playback. The Elisa device recorded above has 3 GB, so RAM alone may not select LOW on
that box; smoothness on the 1 GB stand-in does not establish its behaviour. The focused build-115
checklist is in `docs/release/TESTING.md`.

## French interface update — 30 September 2026

Build 122 / 0.2.0-beta.12 adds 1,511 translated resources, including French plurals,
Trakt and phone setup. It uses Android resource resolution and one extra entry in
three fixed locale lists. No library, worker, catalogue read, cache or drawing layer
was added. The two long Settings rail labels were shortened after visual review.

- Release APK before the final source-revision record: 6,274,801 bytes, 90,958 bytes
  above published beta.11. The resource table adds 90,948 bytes; compressed dex adds
  107 bytes; install profiles are unchanged. The 8 MB budget and 200 KiB growth
  allowance remain in place, with the baseline refreshed to published beta.11.
- Local gates: 704 unit tests in 136 suites, zero failures or skips; screenshot
  goldens, lint, public-source audit, gitleaks, release and Play gates pass.
- Android TV API 30, 1 GB stand-in (997,544 KiB reported RAM): Room's 21 device
  tests pass. The full app run covered 223 cases, with six assumption-disabled
  optional checks and two failures: a wrong route assumption in the new language
  test, and a sports navigation timeout. The corrected French test and the sports
  retry pass. Final focused runs cover all 19 locale, General, phone and sports
  navigation cases, with zero failures (`harness-out/french-api30-final.log`).
- Android TV API 34, 1 GB: the French-to-system-language round trip through the
  platform per-app locale passes (`harness-out/french-api34-final.log`).
- Captures from instrumentation show Home, General and the language picker in
  French on both APIs. Accents and focus are visible; the last picker option is
  reachable with real D-pad events. Audio and subtitle preferences remain stored.

This is a resource addition, not a measured scrolling or decoder performance
change. Build 122 has not been tested on household devices. The signed release
is checked with `tools/verify_release_dex.py` before packaging.

## Sports provider-name matching — 30 September 2026

Build 123 / 0.2.0-beta.13 corrects `30 Sep 19:15` being read as `Sep 19`.
Both teams and a compatible date now determine name confidence; even explicitly
zoned provider clocks only rank these matches. Available evidence wins over
Possible on the same channel. Real guide timestamps keep their existing window.
Cache version 2 discards old automatic confidence without changing Room decisions.
No query, page size, worker, drawing layer or cache bound changed.

The exact two Tappara–KooKoo names are JVM regressions for an 18:30 kick-off.
The provider-clock/date regressions and the version-1 cache rejection fail before
the fix. The Android country-stream regression also fails on the old app. A
separate regression proves score-only winner selection hides stronger evidence;
both candidate arrival orders pass with confidence first.

Measurements on the cold-booted API 30 / 1 GB stand-in, using the unminified debug
app and the same instrumentation fixtures before and after:

| Phase | Before | After |
| --- | --- | --- |
| 12,000 name candidates, bulk-thread CPU median of five warm samples | 292 ms | 306 ms |
| CPU sample range (first warm-up sample excluded) | 271–311 ms | 285–366 ms |
| Owner-scale scan wall time | 18,034 ms | 17,895 ms |
| Whole-app sampled managed heap peak during scan | 11,273 KiB | 11,512 KiB |
| Main-thread heartbeat longest gap | 20 ms | 21 ms |
| Channel / programme pages | 220 / 1,975 | 220 / 1,975 |
| Candidates / Available results | 168,492 / 49 | 168,492 / 49 |

The CPU stress fixture creates 12,000 names that all mention both teams and keeps
only 120 channel winners, across four date forms. It deliberately stresses date
parsing more than the full catalogue, where most names do not mention these teams.
The median CPU increase is 4.8%. The quality plan's 15% gross-regression threshold
uses a rolling nightly comparison; these two local runs do not establish that gate.
This is a correctness change, not a speed-up claim. Wall time is recorded as context only;
host variation and the debug JIT limit attribution. The full scan uses 56,164
channels and 112,328 programmes with long descriptions; peak managed heap remains
below 16 MiB. Cancellation adds no queries and the second read is a cache hit.

All 714 unit tests in 137 suites pass, with zero failures or skips. The full local
gate passes, including lint, screenshot goldens, secret/public-source audits, R8,
release and Play checks. Six targeted Android checks pass: country-stream parsing,
the CPU stress test, owner-scale memory/cancellation/cache, and three sports UI
tests covering saved decisions, focus, playback return, the sports channel row
and ticker. Neither Shield nor Elisa was used for this candidate.

Local evidence: `harness-out/sport-name-before-tests.log`,
`sport-name-before-android-regression.log`, `sport-name-winner-before.log`,
`sport-name-before-measurements.log`, `sport-name-after-measurements.log`,
`sport-name-after-native.log` and `sport-name-final-check-all-run.log`.
The before app's SHA-256 was
`dc3397a014784399cbd3d9f3b725ca5708fa7e479e6e6847363798d9ac4bee52`;
production code was the French commit, with only measurement tests added.

## 30 September 2026 — stream visibility, guide menu and live previews (build 124)

Three real-key regressions fail before the changes on the cold-booted API 30,
1920 × 1080, xhdpi stand-in with 1 GB RAM. With six matched streams, scrolling
up leaves the focused card's top 59 dp above the viewport. With the next
playlist's list read gated, releasing data steals focus from the guide menu's
Sort button. After explicitly focusing and dismissing the live information box,
CH− replays its old focus request and the next preview remains past eight seconds.

All three pass after the changes. The guide case covers both Menu and the rail's
Options button, navigation while the read is waiting, and return to the first
new channel. The player case covers CH− and CH+, five-second preview timeouts,
and deliberately focused controls staying visible. All 29 related Android tests
pass, including stream decisions and playback return, hub boundaries and event
scrolling, guide paging with delayed programmes, held keys, pickers and channel lists.

The stream list stays lazy. Each attached row has one focus group and a
cancelable full-card visibility request on focus entry. It uses the existing
minimal-scroll policy. The guide adds one existing-state subscription and guards
only pending focus retries. The player consumes one-off focus requests and reuses
its existing timer. No query, cache, polling job or drawing layer is added.

Main-thread CPU was sampled on the same stand-in with the unminified debug app,
one warm-up navigation cycle, then five cycles of five Down and five Up presses.
Each press waits for the resulting layout and scroll to settle. This measures
the entire UI phase, not desktop frame times or key-to-focus latency.

| Phase | Before | After |
| --- | --- | --- |
| Main-thread CPU per ten presses, five samples (ms) | 339, 304, 261, 234, 220 | 417, 326, 295, 300, 322 |
| Median per ten presses | 261 ms | 322 ms |
| Median phase CPU divided by ten presses | 26.1 ms | 32.2 ms |

The initial paired median rises 23.4%: the corrected journey scrolls enough to
show whole cards, whereas the old journey stops at their bottom buttons. A
repeat of the corrected journey during the related test suite measures
317, 261, 258, 271 and 267 ms, median 267 ms. The spread and debug JIT limit
attribution. This is a correctness fix, not a speed-up claim or proof of the
nightly rolling performance gate; it still needs the owner's real-device check.

All 714 unit tests in 137 suites pass without failures or skips. Full local
checks pass: screenshot goldens, lint, all eight languages including French,
public-source and secret audits, R8, release and Play artifact gates. The signed
candidate is beta.14/build 124. Neither Shield nor Elisa was used.

Evidence: `harness-out/focus-before-guide.log`,
`focus-before-stream-player.log`, `focus-regressions-after.log`,
`focus-cpu-before.log`, `focus-cpu-after.log`, `focus-related-native.log`,
`focus-check-all-run.log` and `focus-check-all-detail.log`.
The before debug APK SHA-256 is
`601dde665a5b51cea97981239cd60bec23795e2cff8666094897ba50f6fe17b3`;
the corrected debug APK used for both CPU and related tests is
`1791d0c9fec4c58b6d5703b0c20aea13de1c85facae4e2d60f8be33887a5b9ca`.
Both are build 123 for a matched comparison; the final candidate changes only
the version to 124, release documents and import order beyond those runtime fixes.

## 30 September 2026 — club aliases in sports matching (beta.15/build 125)

The owner's Kiekko-Espoo–TPS Turku screenshot names the provider's second team
TPS. HIFK/IFK Helsinki, HPK/Hameenlinna, Sport/Vaasan Sport and ManU/Manchester
United supplied further examples. Aliases now form explicit bidirectional,
sport-scoped groups. Complete club names written without spaces are also indexed.
The longest club phrase wins; a nickname shared by opponents is ambiguous.
No extra app network calls, database queries or runtime dependencies are added.

Before changing the broader matcher, seven of eight new JVM regressions failed:
missing/reversed aliases, compact names, and false Available matches from an
overlapping club phrase or a shared nickname. The TPS JVM and real Room regression
also failed on the saved beta.14 runtime. A refined Room fixture separately
reproduced HIFK as Possible instead of Available. After correction, the native
fixtures find both guide and channel-name streams for all reported identities.
Cache format 2 is rejected by format 3; stored viewer decisions are separate.

Measurements use the same cold-booted API 30 Android TV stand-in, 1920×1080/xhdpi,
1 GB RAM, the unminified debug app and dates anchored to the emulator clock.
The fixed seed contains 56,164 channels, 112,328 programmes with 2,150-character
descriptions and 30 games. CPU is sampled on the bulk thread around the scan;
it excludes Room executor CPU. These are local paired samples, not proof of the
nightly rolling gate or measured Shield/Elisa performance.

| Measurement | Beta.14 runtime | Corrected runtime |
|---|---:|---:|
| 12,000 short-name candidates, five warm CPU samples (ms) | 185, 185, 184, 197, 192 | 234, 181, 202, 194, 183 |
| Warm CPU median, 120 channel winners | 185 ms | 194 ms |
| Owner-scale scan, bulk-thread CPU | 5,037 ms | 5,155 ms |
| Owner-scale scan, elapsed time | 17,702 ms | 18,306 ms |
| Managed heap peak | 11,197 KiB | 12,149 KiB |
| Longest main-thread heartbeat gap | 21 ms | 21 ms |
| Channel / programme page queries | 220 / 1,975 | 220 / 1,975 |
| Candidates / Available results | 168,492 / 49 | 168,492 / 49 |

The bulk CPU increase is 2.3%; the short-name stress median rises 4.9%. Both are
below the 15% gross-regression guard and the heap stays under 16 MiB. The first
implementation iterated an empty scratch map on every guide word: its scan CPU
was 6,359 ms (+26.2%), so it was rejected. An unindexed word now takes one lookup
and continues immediately. The new ambiguity checks run only at indexed words.
First warm-up samples are excluded from stress medians. Cancellation and the
second-run cache hit still pass; all six native pairing tests pass.

Evidence: `harness-out/tps-alias-before-unit.log`,
`team-alias-before-unit.log`, `team-alias-before-native.log`,
`team-alias-room-before.log`, `team-alias-after-native.log` and
`team-alias-optimized-native.log`. The saved beta.14 debug APK SHA-256 is
`b98b55024bc957efe680b249c57b04ce0774b313f6e8e0f4bb9852695fc38673`;
the measured corrected beta.15 debug APK is
`a2df0a6498c434ad9b2b30731c2f3aba7b1024df8ce87ce691934c9407473ee6`.
Version metadata changes from 124 to 125; fixtures, emulator and measurement
method stay the same. Neither Shield nor Elisa was used for this change.

All 729 unit tests in 139 suites pass with no failures or skips, along with
nine targeted native checks: the six pairing/performance checks and three sports
decision, playback/return and score-overlay checks. Full Gradle gates pass,
including screenshot goldens, lint, R8 and release/Play artifacts. All eight
languages, public-source and secret audits pass. The tester-pack version audit
passes after correcting the remaining stale README version references.
Logs: `team-alias-related-native.log`, `team-alias-check-all-run.log`,
`team-alias-check-all-detail.log` and `team-alias-release-audit.log`.

Final review found a compatibility regression in saved alias rows with a
two-character canonical key. Its new test failed before correction. Retain
nonempty identity keys, and apply the three-character minimum to match variants
only. This changes no built-in group or measured fixture. Evidence:
`team-alias-short-identity-before.log` and `team-alias-final-compatibility.log`.
All unit tests were rerun after the compatibility correction. The final native
repeat is in `team-alias-final-native.log`.

## 1 October 2026: subtitle contrast without a background

White text with Background: None now uses Media3's existing black drop shadow,
with a transparent caption background and window. The existing Shadow option
uses the same style. No runtime dependency, player, drawing layer, background
job or catalogue read is added. Media3 keeps its cached caption layout.

`SubtitleReadabilityTest` draws the actual `SubtitleView` at 1920×1080 onto a
white bitmap. Before the fix its contrast assertion fails: there are zero pixels
with all colour channels below 220. After the fix it passes with 14,277 such
pixels. The saved single-draw image was inspected: both white text lines are
readable and there is no background box. Repeated draws are cleared before
the final image, so accumulated shadow alpha cannot make the assertion pass.

Measurements use the cold-booted API 30 Android TV emulator, xhdpi, with the
temporary 1,024 MiB RAM limit (`MemTotal: 997544 kB`). The text, settings and
renderer fixture are identical before and after. After 50 warm-up draws, seven
samples each measure 100 cached draws using `Debug.threadCpuTimeNanos` on the
UI thread. Bitmap clearing, PNG encoding and pixel analysis are excluded.

| Measurement | Before: no edge | After: black shadow |
|---|---:|---:|
| CPU per cached draw, seven samples (µs) | 85.805, 97.960, 83.292, 62.252, 47.869, 64.480, 77.310 | 202.466, 171.592, 119.464, 90.580, 132.078, 94.405, 81.504 |
| Median CPU per cached draw | 0.077 ms | 0.119 ms |
| Visible dark pixels against white | 0 | 14,277 |

The requested visual edge adds 0.042 ms to the median software caption draw.
This is a caption-renderer comparison, not a measurement of video playback,
GPU frame time or physical-box performance. Style selection still runs when
creating the subtitle view; it adds no per-frame settings work.

Player/model unit tests: 265 passed, zero failures or skips. Player debug lint
reports no issues. The debug app and renderer test compile. The normal Gradle
device task and direct native regression both pass after the fix. Strings and
the public-source audit pass. No new strings or preference values are needed.

Evidence in `harness-out/`: `subtitle-before-native.log`,
`subtitle-before-instrumentation.log`, `subtitle-before-cpu.txt`,
`subtitle-before.png`, `subtitle-after-instrumentation.log`,
`subtitle-after-cpu.txt`, `subtitle-after.png`, `subtitle-after-checks.log`
and `subtitle-after-native.log`.

This change is accumulated for the next agreed beta. Version metadata remains
unchanged; no beta artifact is packaged or published for this fix.

## 2 October 2026: reviewed sports dictionary, private build 127

The production snapshot contains 43 reviewed identities, 43 provider bindings,
261 distinct runtime aliases, 16 collision markers and 217 qualifier guards.
It asserts no competition mappings or memberships. Other collected proposals
remain pending. The reproducible database is 81,920 bytes, content revision
`10f8a028cbee5c42addfac3d0a858fb4bbf669f741fb0196971f2482bbb12518`,
SHA-256 `a4c8a40abed08c103449c6e92e672addb49628dd1266fba827388dc7e882dc31`.
There are no dictionary network requests or Home-startup asset reads.

The separate test-only dictionary represents the complete schema: 40,371
identities, 52,939 aliases and 1,024 membership rows, 13,475,840 bytes on disk.
Only current fixtures are resolved. Alias/guard/membership pages are at most
256 rows; aggregate accounted input is at most 2 MiB / 16,384 phrases.
Overflow falls back as a whole. No permanent participant cache is added.

Before implementation, five runs of source `f2f396b` gave median total pairing
CPU of 5,327 ms on 1 GB and 5,240 ms on 2 GB. The original logs remain saved.
Absolute emulator timings vary across sessions. The final dictionary-cost
comparison below interleaves five runs of each mode within one session per
memory configuration, using the same final debug APK, seed and serial worker.
No build or profile generation runs concurrently with those comparisons.

The API 30, xhdpi, 1920x1080 emulator is cold-booted at each RAM configuration;
its Java heap limit is 192 MiB. The fixture has 56,164 channels and 112,328
programmes with 2,150-character descriptions. Every run visits 168,492
candidates through 220 channel and 1,975 programme pages, preserving exactly
49 Available results. Cancellation and the subsequent zero-query cache hit
are assertions in every run. Reported times are background CPU, not frame time.

| RAM / mode | Total CPU median [range], ms | Lookup CPU median [range], ms | Index CPU median [range], ms | Heap peak median [range], KiB |
|---|---:|---:|---:|---:|
| 1 GB / baseline | 5,889 [5,785-6,070] | 0 | 8 [7-11] | 11,837 [11,445-12,475] |
| 1 GB / aliases | 5,869 [5,834-6,065] | 66 [60-97] | 8 [7-9] | 11,854 [11,315-13,267] |
| 1 GB / competition | 5,891 [5,847-10,425] | 64 [60-78] | 9 [7-10] | 11,772 [10,978-12,728] |
| 2 GB / baseline | 5,141 [5,043-5,441] | 0 | 5 [5-6] | 12,137 [11,301-13,958] |
| 2 GB / aliases | 5,143 [5,018-5,152] | 46 [44-55] | 8 [7-9] | 12,529 [12,107-14,439] |
| 2 GB / competition | 5,136 [5,065-5,338] | 45 [37-47] | 9 [8-9] | 11,945 [11,724-13,114] |

Median total CPU changes against the same-session baseline are -0.340% and
+0.034% on 1 GB, +0.039% and -0.097% on 2 GB. Both dictionary modes meet the
10% comparison gate and the 16 MiB scan-heap bound. The first 1 GB competition
run is a slow outlier, retained above and in the raw data; no sample was removed.
Median wall times for baseline/aliases/competition are 18,839/19,238/18,894 ms
on 1 GB and 16,064/15,836/16,100 ms on 2 GB. The main-thread heartbeat's largest
gap is 24 ms; that counter is not a physical-TV key-response measurement.

The 512-event capacity case accounts 1,304,176 bytes and retains 2,049,976
bytes on 1 GB / 2,082,616 bytes on 2 GB after collection, for the resolved
dictionary, matcher and results. This is not a measurement of all screen state.
Cold copy/checksum/open of the full test asset costs 60 ms CPU / 186 ms wall
on 1 GB and 98 ms CPU / 180 ms wall on 2 GB. At replacement the disk bound is
two 16 MiB database files plus small receipts; the current reviewed slice needs
at most two 81,920-byte files. Five hot Android SQLite query plans use indexed
SEARCH operations, without a catalogue scan or temporary sort.

Packaged-asset fixtures demonstrate added Suomi/Saksa, Huuhkajat/Deutschland,
Finlande/Allemagne, Helmarit, Leijonat and HIFK/HPK matches. The labelled corpus
retains existing positives across all twelve products and produces no new false
Available outcomes in its unrelated, shared, single-team, date or known-category
negatives. Unknown explicit references never acquire an identity by name.
Room 12-to-13 migration preserves cached names and saved Confirm choices.
The serial slow-data focus test sends repeated D-pad and real Back events while
results are gated, then verifies focus remains on the viewer's chosen control.

Latest unit results: 764 tests in 144 suites, zero failures/errors/skips.
Desktop dictionary tooling: 162 tests pass. Native migration, reviewed asset,
full-size lookup, query plans, provider matching and hub/focus checks pass on
the emulator. Fresh baseline/startup profiles are generated on API 34; the
profiling runner's suppressed macrobenchmark assumptions are not acceptance
tests. Debug lint, screenshot goldens and dependency boundaries pass.

Evidence in `harness-out/`: `dictionary-baseline-1gb.json`,
`dictionary-baseline-2gb.json`, `dictionary-measure-1gb.json`,
`dictionary-measure-2gb.json`, `dictionary-measure-summary.json`,
`dictionary-cold-1gb.log`, `dictionary-cold-2gb.log`,
`dictionary-native-1gb.log`, `dictionary-migration-13.log`,
`dictionary-all-gates.log`, `dictionary-final-tests.log`,
`dictionary-python-final.log` and `dictionary-generated-profiles.log`.
Earlier exploratory 2 GB results remain separate with the `-exploratory` suffix.

The final signed APK is 6,320,570 bytes, 136,727 bytes above the recorded size
baseline and below the 8 MB target / 10 MB ceiling. R8, release lint and size
gates pass. There are 39,257 dex methods (9,551 app methods); the largest app
method has 3,826 code units. The exact APK passes ART verification with 40
dex2oat log lines, no rejections, and launches in 407 ms while still alive
eight seconds later. This fresh verify-mode launch is a smoke check; it is
not a compiled first-frame budget measurement. Identity, signing, manifest,
dictionary checksum/schema/credits and matching profile audits pass. Source,
secret and all eight-language resource checks pass.

APK source: `8e69df4c828debc18adae1c88f58c4215f6e847c`.
APK SHA-256: `42cc21e7ec7b88dbd869f831bad0486873d7d6fbf1e28a051f3ba2e209f3037e`.
Release receipts: `dictionary-preview-release.log`,
`dictionary-preview-apk-audit.log` and `dictionary-preview-dex.log`.
The matching Android 9-11 profile installs with `status=speed-profile` and
`reason=install-dm`; its receipt is `dictionary-preview-profile-install.log`.
Private build 127 is frozen with both profiles, notices, measurements and
checksums. See `docs/preview-builds.md`; no public release was created.

The owner authorised installation of the frozen preview on Shield. On
2 October 2026, build 126 was updated to 127 with existing data preserved.
Version read-back and the matching Android 9-11 install profile pass:
`arm64`, `status=speed-profile`, `reason=install-dm`.
Receipt: `harness-out/dictionary-preview-shield-install.log` and the private
`.local/dictionary-preview-shield-install.json`. Viewer testing is pending.
Elisa installation still awaits its own instruction. Both devices' feed,
first-data, frame/key and decoder/playback budgets remain unmeasured here.

## 2 October 2026: numeric dates in the five Shield reports

Build 127 recognised both participants in all five reported fixtures but
rejected `10/2` and `2/10` as ambiguous. The fix checks either valid numeric
reading against this fixture's day, respecting explicit year and zone,
otherwise UTC. Literal EDT is recognised. Clocks still rank name matches;
wrong dates/years, invalid dates, wrong opponents and single-team listings
remain guarded. Only the existing explicit-zone two-hour midnight window
can support an adjacent date. No dictionary approval or catalogue query is added.

Nine new model tests cover the ten visible listings, all 144 month/day
combinations in both orders, several clocks, explicit years and negatives.
Eight fail before the fix, including all five photo tests. The native date
regression separately fails on the old code, reporting Possible in all six
samples. An old-cache fixture is proven red (Possible instead of Available)
before advancing matching rules from 4 to 5. It then passes and retains
the saved Reject choice. Cache format 4 and Room schema 13 do not change.

After the fix, 337 affected model/sports unit tests and six native provider/name
tests pass. Debug lint passes. The native fixtures use the current UTC day;
the photo tests are pure date logic without an expiring repository window.
All parsing remains on the existing background worker and only happens for
participant/decision candidates. No new I/O, API request, dependency, cache,
startup task, main-thread work or playback job is introduced.

The same cold-booted API 30 TV emulator uses 1,024 MiB RAM, 192 MiB Java heap,
1920x1080/xhdpi and the unminified debug build with natural ART/JIT state.
Each name-only sample visits 12,000 matching zoned numeric listings and keeps
120 winners. Sample 0 is warm-up; all raw samples remain saved.

| Name-only path | Warm CPU median [range], ms | Result |
|---|---:|---|
| Before | 291 [279-358] | Incorrect Possible |
| Initial fix | 366 [338-397] | Available |
| Final isolated run | 339 [303-421] | Available |
| Final full six-test suite | 367 [347-397] | Available |

The corrected path computes dates/offsets which were previously skipped.
Repeated date conversion and temporary date lists were removed. The final
isolated median adds 48 ms across 12,000 listings (4 microseconds per listing,
16.5% of this deliberately all-matching name-only case). The full-suite sample
shows variance rather than a lower-cost claim. This is background CPU, not
remote-key latency or frame time.

Five final whole-scan runs use the full synthetic dictionary/context and the
same 56,164-channel, 112,328-programme owner-scale seed as the earlier log.
All visit 168,492 candidates, 220 channel pages and 1,975 programme pages,
retaining exactly 49 Available results. Total CPU is 5,006 [4,912-5,084] ms;
scan CPU is 4,943 [4,843-5,006] ms, lookup 69 [63-78] ms and index 9 [8-12] ms.
Heap peak is 13,168 [11,543-13,900] KiB, below 16 MiB. Cancellation and a
subsequent zero-query cache hit pass in every run. Main-thread heartbeat
gaps are at most 21 ms; this is not physical-TV key-response acceptance.

Earlier comparable whole-scan CPU was 5,891 [5,847-10,425] ms. These full-scan
runs are from separate sessions, so the lower absolute time is not evidence
of a speed improvement. No overall regression is visible in this workload;
the added numeric-date work is explicitly measured above. Physical-box
frame/key/playback acceptance remains pending.

Evidence: `harness-out/numeric-date-before-build.log`,
`numeric-date-cache-before.log`, `numeric-date-after-build.log`,
`numeric-date-after-optimized-build.log`, `numeric-date-before-native.log`,
`numeric-date-after-native.log`, `numeric-date-optimized-native.log`,
`numeric-date-all-native.log`, `numeric-date-owner-scale.log` and the complete
raw/summary `numeric-date-measurements.json`.

Private follow-up build 128 is frozen from source
`ac237f4b04124c20f578e746cc6a2817f12ec547`, APK SHA-256
`1056fd2198c0ac401620c60b09581be2d40110dd9d9932bb66a501838f40fc80`.
APK size is 6,320,570 bytes; 39,259 methods, 9,553 app methods and largest
app method 3,826 code units. Release lint, size/method/module gates and
identity/signing/manifest/dictionary/profile audits pass. The exact APK
verifies under ART with 39 dex2oat lines and no rejection, starts in 596 ms
and remains alive eight seconds later. This is a verify-mode launch smoke
check, not a compiled first-frame measurement. The Android 9–11 profile is
accepted with speed-profile/install-dm on the emulator. Source audit checks
1,207 files with zero findings; tree/history gitleaks scans find no secrets.

Receipts: `numeric-date-preview-release.log`, `numeric-date-preview-apk-audit.log`,
`numeric-date-preview-dex.log`, `numeric-date-preview-profile-install.log` and
`numeric-date-preview-package.log`. Both matching profiles, checksums, notices,
measurements and English/Finnish device notes ship in the private pack.
Build 127 and public beta.15 remain frozen; no household install or public
publication was made for 128. The next unreserved distributed APK needs 129+.

After the owner's explicit installation instruction, Shield was updated from
127 to 128 on 2 October 2026 using the exact frozen APK and Android 9–11
profile, with app data preserved. Version read-back confirms
0.2.0-beta.15-dates.1, build 128; compilation reports
`arm64`, `status=speed-profile`, `reason=install-dm`.
Receipt: `harness-out/numeric-date-preview-shield-install.log` and the private
`.local/numeric-date-preview-shield-install.json`. Frozen package files are
unchanged. Shield viewer acceptance and Elisa installation remain pending.

## 2 October 2026: Home actions after background activity recreation

The owner reports that Home's live channel cards do nothing after reopening
the app on Shield, while the same channels open from Guide. On the API 30 TV
emulator, ordinary background return passes, but background activity recreation
reproduces the failure for both a live channel and a Continue Watching film.
Before the fix, two of three native tests fail with Home still visible after OK.
Home's retained model captures a BackStack object that the recreated UI replaces.

Keep one navigation stack per activity lifecycle with the retained screen models.
On recreation, apply the existing route codec to that same object: drop playback,
reapply management gates and preserve surviving entry keys. A fresh process still
restores saved routes/keys. A new host clears entry models and saved state for
routes dropped during restoration. The holder contains only route ids/keys and
is released with the activity. No new I/O, catalogue query, background task,
dependency, database change or user-visible text is added.

The final checks pass: 32 UI-design/Home unit tests, four Home restoration native
tests, and eight existing native tests covering Home, three launches in one
process and French language selection/reset. The channel regression also checks
the remembered focus, a second recreation, playback dropping under the existing
policy, reopening, and real Back returning to Guide on the chosen channel.
The film regression checks real Back to its details page. Entry-store and saver
unit tests cover model cleanup, stale saved snapshots, fresh-process restoration,
route keys and management policy. Debug app/UI lint, UI golden verification and
module-boundary checks pass; compilation/lint introduce no new warnings.
The existing Gradle deprecation notice remains in the golden check log.

CPU comparison: same cold-booted `sohva_rebuild_tv30`, API 30 x86, 1920x1080 xhdpi,
1,024 MiB RAM and 192 MiB Java heap; debug build with natural ART/JIT behavior.
The fixture contains four fictional live channels and four library films, with
one paused film and one recently watched channel. Each isolated instrumentation
run performs six stop/recreate/resume cycles. Measure main-thread CPU from before
the stop until the same Home film card has focus again; exclude sample 0 as warm-up.
No concurrent Gradle work runs during either isolated measurement.

| Build | All six main-thread CPU samples (ms) | Warm median [range] (ms) |
|---|---|---|
| Before | 239, 185, 167, 155, 123, 137 | 155 [123-185] |
| Final fix | 222, 197, 174, 132, 136, 148 | 148 [132-197] |

These samples show no increase in the measured cycle's median, with overlapping
ranges; they do not prove a speed improvement or physical-TV key/frame timing.
An intermediate identity-only implementation measured 145 [117-191] ms, but is
not the final comparison because the unchanged French regression found that it
skipped Settings restoration. The final implementation preserves that policy.

Evidence: `harness-out/home-restore-before-native.log`, `home-restore-before-cpu.log`,
`home-restore-final-build.log`, `home-restore-final-cpu.log`,
`home-restore-final-native.log`, `home-restore-final-related-native.log` and
`home-restore-final-gates.log`. The isolated CPU test also passes separately.
No new APK is frozen or installed on a household device. Build 128 remains the
owner's current sports preview; release ART/profile and household acceptance
checks belong to the next agreed preview.

Following the owner's installation request, private build 129 is frozen from
`ce2dbbf1186f4f1ea17add36802ee3eebbcd1c4c`, APK SHA-256
`c95bb996f9e64c0a71c07112b6adb00a8237640b0f43113c316228730f45fe8c`.
It is 6,320,570 bytes, with 39,266 methods, 9,560 app methods and largest app
method 3,826 code units. Release lint/R8/module/size and APK/profile safety
checks pass. ART reports 40 dex2oat lines with no rejection; the verified APK
launches in 631 ms and remains alive eight seconds later. This is a verify-mode
launch smoke, not a compiled first-frame measurement. Both install profiles
match the APK; Android 9–11 reports speed-profile/install-dm on the emulator.
The source audit checks 1,210 files with no findings; tree/history secret scans
find no leaks. Measurements, checksums, notices and test notes are frozen with
the APK. Evidence: `harness-out/home-preview-{release,apk-audit,dex,profile-install,package}.log`.

Shield was updated from 128 to 129 at 14:22 UTC on 2 October 2026, using the
exact APK/profile with app data preserved. Version read-back confirms
0.2.0-beta.15-home.1, build 129, and arm64 speed-profile/install-dm.
Receipts: `harness-out/home-preview-shield-install.log` and the private
`.local/home-preview-shield-install.json`. All 14 frozen payload hashes still
match. No device key events, launch, data clearing or settings changes were
performed. Viewer acceptance remains pending; no public publication occurred.

The owner subsequently reports the Home background/restart scenario working
on Shield. On explicit instruction, the same frozen APK and Android 12+ profile
were installed on Elisa at 14:31 UTC on 2 October 2026, preserving app data and
updating build 112 to 129. Device identity is ZTE B866V2F01, Android 12/API 31.
Read-back confirms 0.2.0-beta.15-home.1, build 129, and
`arm`, `status=speed-profile`, `reason=install-dm`. The profile SHA-256 is
`e78e97fd12009d36d3a6ee8cc3a5b24b62ffbb366f8586bb4089d21e57c015c8`.
Receipts: `harness-out/home-preview-elisa-install.log` and the private
`.local/home-preview-elisa-install.json`. Frozen payloads remain unchanged.
Installation confirms package/profile acceptance, not Elisa frame/key/playback
budgets; the owner will test those separately. No launch, device key events,
data clearing or display/inactivity changes were performed.

## 2 October 2026: once-per-build Home update notice

Reuse the existing release checker, which runs after the first frame at process
startup and fetches releases only when the last successful check is at least
24 hours old. The notice adds no HTTP request or periodic background service.
It observes only a changed offered build, keeps one pending version/build pair,
and replaces or clears that pair when the offer changes or is shown. Persist
one highest announced build integer on IO in the existing local updater store.
This marker is shared across profiles on the device and excluded from backup.
Disabled builds and offers no newer than the installed APK never read this marker.

Home shows a static, bounded panel for 15 seconds, after a drawable frame, without
focus targets or a Back handler. Defer it while playback, PiP, an import notice,
or a reminder prompt is active. Only Home subscribes to the small pending flow;
download progress cannot recompose the panel. There is no catalogue read, Room
change, new dependency, notification permission, animation or extra bulk job.
Preferences remain off the main thread. A checked IO commit makes a failed marker
write diagnosable; the process also remembers the shown build during a slow write.

Regression-first evidence: eight new core tests ran against an inert coordinator;
six failed and the two neutral guards passed. The implemented coordinator passes
all eight. In total, 59 core-sync and 21 UI-design unit tests pass. The final native
suite has ten passes and one existing conditional skip (the TV can open the About
link, so its no-browser fallback test does not apply). The four new tests cover
deferred display, retained remote focus and Back, durable memory across a new
memory/controller instance, a later version, automatic dismissal and paired CPU
measurement. Four Home restoration tests and two applicable About tests still pass.
The fixture screenshot was inspected: the notice is readable and the underlying
Watch button keeps visible focus. This is a small test screen, not the real Home
catalogue. Debug app/UI lint, UI golden verification, module boundaries, and string
coverage/format checks in all eight languages pass. No new compiler/lint warnings
remain; the existing Gradle deprecation notice remains.

CPU comparison: cold-booted `sohva_rebuild_tv30`, API 30 x86, 1920x1080 xhdpi,
1,024 MiB RAM and 192 MiB Java heap, debug APK with natural ART/JIT behavior.
Six paired cycles redraw the same small screen with two buttons and a cycle label,
first without a notice, then with a new notice. Measure main-thread CPU through
state delivery, drawing and the after-draw acknowledgement. The marker write runs
on IO. Exclude pair 0 as warm-up. No Gradle work runs during this isolated test.

| Mode | All six main-thread CPU samples (ms) | Warm median [range] (ms) |
|---|---|---|
| Without notice | 36, 21, 18, 20, 18, 18 | 18 [18-21] |
| With notice | 48, 51, 47, 36, 51, 51 | 51 [36-51] |

The difference of warm medians is 33 ms; warm paired differences have a median
of 30 ms [16-33]. This is a one-time cost when a new version is announced, not
per-frame time, real Home startup time or physical-TV key latency. There is no
ongoing animation, and the notice never appears during playback. An intermediate
implementation acknowledged after one frame and measured 21 to 39 ms; it is not
the final result because acknowledgement now waits for the drawable frame.

Evidence: `harness-out/update-notice-before.log`, `update-notice-after-core.log`,
`update-notice-build.log`, `update-notice-final-build.log`,
`update-notice-strings.log`, `update-notice-final-ready.log`,
`update-notice-verified-test-build.log`, `update-notice-final-native.log`,
`update-notice-final-draw-cpu.log` and `update-notice-home.png`.
The first combined build exposed the narrow SharedPreferences lint issue, fixed
before the final lint runs. The public-source audit checks 1,215 files with zero
findings, and the publishable-tree secret scan finds no leaks. Receipts:
`harness-out/update-notice-source-audit.log` and `update-notice-gitleaks.log`.
No new APK is frozen, installed or published.
Household devices remain on preview 129; release ART/profile and physical-device
checks belong to the next agreed preview. No new distributed version code is
reserved by this change.

## 4 October 2026: sidebar channel choices under slow reads

The owner relayed a report of a frozen picture after choosing a channel from the
player's right-side list, while opening the channel from Guide works. The affected
user's exact model, Android version and APK are pending. The baseline live player
matches public beta.15. Six normal and six delayed-response sidebar switches drew
new frames before this change; the reported single-switch freeze is not reproduced.

Regression-first tests gate actual reads and send remote events through the window.
A delayed channel read can finish after a later choice and replace it. Cancelling
only inside play still allows an older list-page lookup to enter play late; the
second gated test proves this. Reviewing that fix found a third timing gap: starting
playback before resolving its group position lets a reselect cancel the position
update, leaving channel keys on the old index. The corrected delayed-position test
fails against that intermediate ordering and passes with the final ordering.

Keep one pending choice through the list/step page, admission, channel formatting
and group-position reads. Check cancellation after suspended reads; prepare the
list and position together and commit playback without another suspension. Resolve
the existing indexed position reads before stopping the current stream, so the old
picture continues during slow reads. Reveal the temporary info box before the
recent-channel write. Reselecting the playing channel cancels the pending choice
without opening a new stream. No new query, catalogue load, cache, schema,
dependency, startup work or decoder/buffer policy is introduced.

Playback transitions now record resolution, state, play/pause and suppression
reasons, and the first frame. These are transition events, not a per-frame timer or
watchdog. Capture elapsed time on the application thread and run existing regex
redaction, time formatting and logcat mirroring on the IO app scope. The existing
600-line ring evicts its oldest line; each line is capped at 500 characters. New
records contain only states, flags and request counters, without channel names,
keys or addresses. Logcat confirms resolution, buffering, ready and first-frame
records in `harness-out/player-switch-diagnostics.log`. There are no uploads.

The existing held-key/zap-back group followed by the channel-key test failed twice
with the initial stream continuing and no second request; that test passed alone.
Holding composition until the initial info box arrives then proves the video key
host is unfocused. Request entry focus once on attachment regardless of initial
chrome, cancelling it if the viewer already chose a control, overlay or stopped
banner. The gated focus test and the same three-test group now pass. This is a
separate entry-focus correction, not proof of the reported provider-specific freeze.

CPU comparison: the same cold-booted `sohva_rebuild_tv30`, API 30 x86,
1920x1080 xhdpi, 1,024 MiB RAM and 192 MiB Java heap. Both isolated runs use
freshly installed debug APKs with natural ART/JIT behavior and no concurrent Gradle
work. Measure main-thread CPU from sending OK through the HTTP request, a new
rendered frame and UI idle. The test clip is a synthetic 320x180 H.264/AAC MP4;
exclude switch 0 as warm-up.

| Source | All six main-thread CPU samples (ms) | Warm median [range] (ms) |
|---|---|---|
| Unchanged player | 114, 105, 86, 99, 76, 78 | 86 [76-105] |
| Final choices, entry focus and diagnostics | 116, 116, 110, 91, 73, 75 | 91 [73-116] |

The warm median rises by 5 ms, with overlapping ranges. This whole switch-to-frame
CPU window is not wall-clock zap latency, single-key CPU, proof of the 50 ms
key-up-to-setMediaItem target, or a physical Chromecast measurement. Earlier
intermediate fixes measured 95 and 88 ms warm medians; neither is the final source.

Evidence: `harness-out/player-switch-baseline-native.log`,
`player-switch-race-before.log`, `player-switch-page-before.log`,
`player-switch-position-valid-before.log`, `player-switch-focus-valid-before.log`,
`player-switch-final-eight.log`, `player-switch-cpu-before.log`,
`player-switch-final-focus-cpu.log` and `player-switch-focus-ready-build.log`.
The final eight new native tests pass,
including the two lookup races, the position race, reselect cancellation and
normal/delayed first-frame checks at connection limit one and gated entry focus. A further isolated CPU
repeat passes. The initial reselect fixture wrongly assumed one HTTP request per
MP4; it now compares counts before/after the reselect and first-frame counts. The
first position fixture sent the previous-channel key while expecting the next;
its corrected default mapping is proved red before green.

The final related suite passes ten existing tests: held-key single-zap, held Back,
channel-key/Guide return, temporary info-box timeout after action focus, parental
PIN admission, track-picker Back, a new picture after buffer-profile recreation,
zero hidden-overlay UI frames in its 15-second window, catch-up focus/pause/skip,
and film resume/completion with focus restored to its details and wall. Together
with the eight new tests, 18 distinct native tests pass. Receipts:
`harness-out/player-switch-final-related-remote.log`,
`player-switch-final-related-focus.log` and
`player-switch-final-related-playback.log`. The previous combined channel-key
failures led to the focus fix; they are not dismissed as flakes.

All 19 core/feature player unit tests pass. Debug app, core-player and feature-player
lint and module boundaries pass, with no new compiler/lint warnings. Strings and
resources are unchanged. The public-source audit checks 1,216 files with zero
findings; the publishable-tree gitleaks scan finds no leaks. Receipts:
`harness-out/player-switch-source-audit.log`, `player-switch-gitleaks.log` and
`player-switch-focus-ready-build.log`.

No new distributed version code is reserved, APK frozen, household install or
public push performed. Build 129 stays frozen. Release ART/profile verification,
release-size checks and physical-device acceptance belong to the next agreed
preview. The actual reported single-switch freeze remains unconfirmed pending
the affected user's exact model/Android/APK and reproduction details. The new
transition records become available on that user's device only after installing
a future build containing this change.

## 6 October 2026: channel list highlight and scroll in one frame

The owner sees the player's right-side channel list move its highlight first and
centre the list a moment later on both household boxes. PLAY-FR-54 requires both in
the same key press. Two causes: every Up/Down launched a page read before publishing
the new selection, even for a row already on screen, and the centring ran in a
launched effect, which on a device is dispatched after the highlight's frame is drawn.

Up/Down within the page on screen now publishes the selection at once, without a
read. Moving to another page still reads it; returning to the page on screen cancels
that stale read, while a now-titles read for the page on screen continues. Both the
channel and group columns request their scroll position from a side effect, which
the lazy list applies in the same frame's measure. No query, cache, dependency or
schema is added; the rows read per page are unchanged (at most 200).

Regression-first tests on `sohva_rebuild_tv30` (API 30 x86, 1920x1080 xhdpi,
1,024 MiB): with every page read held, twelve Down presses must each move the
highlight; the unchanged code fails at the first press
(`harness-out/channel-scroll-before.log`). Compose frames are then stepped one at a
time with the test clock paused, and no frame may show the highlight below the
middle row. That check fails when the scroll is delayed by one frame
(`channel-scroll-detector-check.log`). It does not fail on the old launched effect,
because the test dispatcher runs the effect before layout; the device-only lag is
therefore fixed by construction rather than proven red. A 205-channel group tests a
held read of the second page while the viewer returns to the first; removing the
stale-read cancel fails it (`channel-scroll-stale-mutation.log`).

Main-thread CPU per Down press, key to idle UI, 20 warm presses per run, runs
alternated on the same cold-booted emulator (`channel-scroll-cpu-*.log`):

| Source | Run 1 median [range] (ms) | Run 2 median [range] (ms) |
|---|---|---|
| Unchanged list (with row selection semantics for the test) | 30.3 [10.1-38.5] | 29.4 [17.8-52.5] |
| Highlight without a read, same-frame scroll | 30.6 [20.0-36.3] | 31.8 [19.7-40.6] |

Main-thread CPU is unchanged within noise; the removed read ran on the IO
dispatcher, so the gain is the wait between key and highlight, not main-thread work.
This is not a physical low-end box measurement.

The two new tests, PlayerChannelSwitchTest (8), PlayerRemoteTest (5) and
PlayerControlsTest (5) pass (`channel-scroll-after*.log`). Feature-player and core
model unit tests, feature-player and app debug lint pass with no new warnings
(`channel-scroll-lint-unit.log`). Rows now carry the `selected` semantics property,
which also tells accessibility services which channel is highlighted.

## 7 October 2026: display mode kept during a channel change

A Chromecast with Google TV (Android TV 14) user reports that every channel chosen
from the player's list freezes the picture and stops the sound without a message,
while the Guide works. With "Match the display to the picture" on (the default), an
in-player zap stopped the old stream, the tracks cleared, the unknown rate restored
the original display mode, and the new stream's rate switched it again: two HDMI
re-negotiations per zap. A re-negotiation can make the system report "audio becoming
noisy", which pauses the player. Only switches to a matched mode were marked for the
existing pause recovery, so the pause after the restore stayed.

The mode choice moves to the pure `DisplayModes.target`. An unknown rate requests
nothing; a known rate requests its mode, or the original when nothing fits, only if
it differs from the window's preference. Every request is marked before it is made.
Leaving the player and turning the setting off restore the original as before.

`DisplayModesTest` (4 tests) plays rate sequences such as 25, unknown, 25 fps. With
the helper written to the old rule, three fail
(`harness-out/display-mode-unit-old-rule.log`); with the fix all pass
(`display-mode-unit-after.log`, with core-model and feature-player unit tests and
feature-player and app debug lint). On the cold-booted 1 GiB `sohva_rebuild_tv30`,
PlayerChannelSwitchTest (8), PlayerChannelListScrollTest (3), PlayerRemoteTest (5),
PlayerControlsTest (5), DiscoverPlaybackTest (5) and LibraryPlaybackTest (2) pass
(`display-mode-after-*.log`).

The emulator offers one 60 Hz mode and the test clip is 25 fps, so no mode is
requested there in either version: these runs show nothing broke, not the device
effect. The change removes window-attribute writes during zaps and adds no per-frame,
query or background work, so no CPU comparison was run. Confirmation needs the
affected user on the next preview, or the setting turned off on beta.15.

## 8 October 2026: naming unreadable stream replies and retrying playlists as HLS

The owner's Shield diagnostics show films and live channels from the "TV" source
failing with "none of the available extractors", alongside 502, 503 and spurious
407 replies from the same provider. Beta.15 could not say what the provider sent.

The stream's first request (its placeholder, from position 0) is now classified on
its way to the player: the kind of reply from at most 256 bytes, the Content-Type
media type when it is a plain type/subtype, and whether a redirect changed the host.
Only the classification is kept. An unsupported or malformed container records
`reply: <kind>, type <type>, same host|redirected to another host` through the
existing off-main transition log. A playlist reply at an address that names no type
is retried once as HLS: a load-error policy turns that first failure into a 3 s
retry wait while the service switches the item on the same placeholder and lease.

A first version hid the failure by wrapping the session's player listeners. Kotlin
interface delegation does not forward Java default methods, so every
`Player.Listener` event was dropped; all eight PlayerChannelSwitchTest cases failed
(`harness-out/stream-reply-suite-PlayerChannelSwitchTest.log`). That design was
removed before commit.

Tests: StreamReplyTest (4) and ReplyInspectionTest (4, MockWebServer through the
real OkHttp data source, including a redirect to another host and the one-time
retry claim). PlayerStreamReplyTest (2) on the 1 GiB `sohva_rebuild_tv30` failed
before wiring (`stream-reply-e2e-before.log`) and passes after
(`stream-reply-e2e-v2.log`): a playlist at `/live/0` is fetched again and its
segment requested, the screen never logs the parsing failure, and a web page reply
shows the banner and its `reply: web page, type text/html, same host` line, with no
address in the diagnostics. Without the policy the retry never happens
(`stream-reply-policy-mutation.log`). The final code passes PlayerChannelSwitchTest
(8), PlayerChannelListScrollTest (3), PlayerRemoteTest (5), PlayerControlsTest (5),
PlayerArchiveTest (4), DiscoverPlaybackTest (5), DiscoverSubtitleTest (3),
VodAddonSubtitleTest (1), LibraryPlaybackTest (2) and PlayerStreamReplyTest (2)
(`stream-reply-suite-*.log`), plus unit tests and debug lint
(`stream-reply-lint-v2.log`).

Cost: one 256-byte copy and a classification per stream start, on the loader
thread; no extra request, connection, query, cache or main-thread work. No CPU
comparison was run for this reason. The emulator test segment is not playable, so a
real HLS stream reached this way is still unproven on hardware.

## 8 October 2026: diagnostics without a file picker

Google TV on the Chromecast has no document picker, so Save diagnostics only showed
its no-picker sentence. Without a picker the file now goes to the shared Download
folder (MediaStore, Android 10+), and a "Send to phone" button opens the phone page
in a diagnostics mode on any TV.

Tests: PhoneServerTest (11, one new): the page carries the file name but no
diagnostics; a missing Origin, a wrong token or another Origin get 403; the token
holder gets the exact file as no-store text/plain, twice, and the count rises.
SettingsAboutTest on the 1 GiB `sohva_rebuild_tv30` (`diagnostics-phone-about-test.log`):
with the picker reported missing, the status names a `sohva-tv-diagnostics-yyyyMMdd-HHmm.txt`
that MediaStore finds in Download with no address, user name or password in it
(deleted afterwards); Send to phone shows the address, refuses a wrong token, hands
the redacted file to the right one, says a phone has it, and Back closes the dialog,
stops the server and returns focus to Send to phone. The existing About tests pass;
the link test skips by its assumption, as before, because the emulator has a
browser. PhoneSetupTest (4) passes. SettingsGeneralTest failed once in its time-zone
dialog (focus still on the row) and then passed alone twice and as a whole class
(8/8); it does not use About or the phone page, and is noted as intermittent.
String import check, French coverage, unit tests, debug lint and the debug and test
builds pass (`diagnostics-phone-build.log`).

Cost: nothing until the viewer presses a button. The file is collected and redacted
on the IO dispatcher as before; the phone page runs on the existing daemon thread.
No startup, playback, query or background work is added.

## 11 October 2026: now/next and recents while an import writes

Owner's report on Shield build 130: sometimes the player's channel list does not show
the current programmes, the info box's programme does not update, and Home's "on now"
row keeps old channels and programmes, while the guide is correct at the same moment.

Cause, proved on the emulator: the info box and the channel list looked up their source
with `reads.sources.first()`. The first value of a Room flow registers table observers
through the database's single write connection, so the call waits while an import
holds a write transaction. The guide's own programme reads are plain queries and its
observer is already registered. Home's recent-channel read is also a plain query, but
the "just watched" row is a write and queues behind the import, so the row lacked the
newest channels until the import ended.

`ReadsDuringImportTest` holds a write transaction open on another thread. With the old
lookup the now/next read does not return within 5 s (`harness-out/stale-now-before.log`);
with the one-shot `source(id)` it returns at once, as do the programme read and Home's
read (`stale-now-after.log`). A channel watched during the held write was missing from
Home's row (`stale-home-before.log`); with the pending list it leads the row at once and
is in the table after the write is released. `PendingRecentsTest` (3) covers order,
per-profile keys, a newer watch outliving an older write, and the 20-entry bound.

On the final code, ReadsDuringImportTest (2), PlayerChannelSwitchTest (8), PlayerChannelListScrollTest (3), PlayerRemoteTest (5), PlayerControlsTest (5), HomeTest (6), HomeRestoreTest (4) and HomeLayoutTest (7) pass on the 1 GiB sohva_rebuild_tv30 (harness-out/stale-now-suite-*.log), with core-data, feature-player and feature-home unit tests and debug lint (stale-now-unit.log).

Cost: one plain indexed query per info-box tick or list page in place of a flow
subscription; an in-memory list of at most 20 small entries. No CPU comparison was
run: nothing is added to a frame, to start-up or to playback. Not reproduced: stale
programme text on Home itself, whose read never waited in the test; if it shows again
after this build, Save diagnostics right then.

## 11 October 2026: info box and channel list programmes after build 132

On Shield build 132 the owner reports Home correct, but the info box and the channel
list still not showing current programmes.

Info box: now/next came from a reader that runs every 30 s while the box shows. A
channel change while the box was already showing left that reader running, so the new
channel was not read before the box hid 5 s later; and a slow read for the previous
channel could finish after the change and show its programme. `PlayerNowNextTest`
(2) gives each channel's one-channel read a programme named after it: both cases fail
on build 132's code at "no read for the new channel within 3 s"
(`harness-out/now-next-before.log`) and pass with the reader restarted on every
channel change and results checked against the playing channel (`now-next-after.log`).

Channel list: the titles read covered the whole page (up to 200 channels) in one query
before any title showed. It now covers 12 rows either side of the highlight and reads
on as the highlight nears the range's end. The new case in
`PlayerChannelListScrollTest` asserts every list read asks for at most 30 channels and
that 20 Down presses bring titles for the rows reached; with the whole-page read
restored it fails at "the first read asked for 200 channels"
(`list-titles-mutation.log`).

On the final code PlayerNowNextTest (2), PlayerChannelListScrollTest (4),
PlayerChannelSwitchTest (8), PlayerRemoteTest (5), PlayerControlsTest (5),
PlayerArchiveTest (4) and ReadsDuringImportTest (2) pass on the 1 GiB
`sohva_rebuild_tv30` (`now-next-suite-*.log`), with unit tests and debug lint
(`now-next-unit-lint.log`).

Cost: the list's read shrinks from up to 200 channels to at most 25 per range; the
box makes one extra one-channel read per channel change. No CPU comparison was run;
the read is smaller and runs off the main thread. The list's slowness on the Shield
was inferred from the code, not measured there.

## 11 November 2026: baseline for the review fixes

Before the steps of [improvement-plan.md](improvement-plan.md), on `sohva_rebuild_tv30`
(API 30, 2 GB, four cores), unchanged code at the head of `fix/live-stream-end`.
Logs: `harness-out/plan/BASE-1-*.log`.

Checks: `tools/check_all.py` passes; 726 JVM tests; release APK 6,346,029 bytes.

`OwnerScaleImportTest` (fixture: 56,164 playlist entries, 165,600 programmes, 200,000 films):

| Step | Time | Rows |
|---|---|---|
| M3U playlist | 3,883 ms | 50,546 |
| M3U guide | 2,017 ms | 129,600 |
| M3U playlist, unchanged | 2,237 ms | 50,546 |
| M3U catalogue, first | 32,030 ms | 219,500 |
| M3U catalogue, unchanged | 12,130 ms | 219,500 |
| Xtream playlist | 3,646 ms | 56,164 |
| Xtream guide | 2,131 ms | 146,004 |
| Xtream catalogue, first | 40,117 ms | 201,500 |
| Xtream catalogue, unchanged | 5,297 ms | 201,500 |

Phases of the first M3U catalogue import: write transactions 26,107 ms; sweep and groups
281 ms; organisation 1,257 ms; standing copies 1,933 ms; film counts 164 ms; genre counts
12 ms; search index 1,848 ms (one statement in one transaction). Unchanged: write
transactions 1,576 ms, standing copies 1,894 ms, film counts 163 ms, search index 128 ms.
Java heap maximum 89 MB of 192 MB.

Unified search on the imported database (400,000 films, 106,710 channels), median of the
test's runs: "ha" live 7 ms, films 32 ms, series 0 ms, episodes 2 ms; "north" live 7 ms,
films 25 ms. Every group is far inside the 100 ms budget of spec 03.

`ReadsDuringImportTest` (2) passes. Start-up, memory and frame figures of the same code,
measured with the lab build earlier the same day: cold start to first frame 216 to 262 ms
(five runs), fully drawn 542 to 596 ms; total memory 41 MB on Home, 84 MB in the guide
after 60 rows, 84 MB during playback, with Java heaps of 4, 30 and 20 MB; 60 rows down the
guide drew 121 frames, none janky.

## 11 November 2026: the search index in pages (IDX-1)

`OwnerScaleImportTest` gained a write probe: one tiny transaction every 20 ms on its own
thread, and the longest it waited for the write connection during each step. Same fixture
and emulator as the baseline above. Logs: `harness-out/plan/IDX-1-before-measure.log` and
`IDX-1-after-measure.log`.

| Step | Longest wait before | after | Search-index phase before | after |
|---|---|---|---|---|
| M3U guide (138,600 programmes) | 334 ms | 12 ms | – | – |
| Xtream guide | 448 ms | 18 ms | – | – |
| M3U catalogue, first (200,000 films) | 1,854 ms | 836 ms | 1,858 ms | 756 ms |
| M3U catalogue, unchanged | 226 ms | 238 ms | 130 ms | 0 ms |
| Xtream catalogue, first | 2,180 ms | 679 ms | 2,179 ms | 720 ms |
| Xtream catalogue, unchanged | 403 ms | 402 ms | 248 ms | 0 ms |
| M3U playlist, first | 364 ms | 375 ms | – | – |

Before, the longest wait of a film import was the single catch-up statement. In pages of
2,000 rows the catch-up is also faster as a whole, and an unchanged catalogue no longer
walks the table. What remains (0.7 to 0.8 s in a first film import, 0.4 s in the first
playlist) comes from another finish phase and was not investigated here. Import totals are
unchanged within noise (M3U catalogue 33.6 s before, 33.1 s after). Unified search on the
imported database still answers ("ha": films 33 ms, 40 rows).

## 11 November 2026: the open guide wakes once per import (GUIDE-2)

Counted in `LiveStoreTest` on the store's own signals, before and after. Ten channel writes
during a running playlist import: 10 list signals before, 0 after, then 1 when the import's
status changes to its result. A catalogue import's two status writes and a guide import's
start: 3 programme reloads before, 0 after; a new guide snapshot and a source edit still
give 1 each. Not counted on the emulator with an owner-scale import: the guide must be open
for that, and no device test opens it during an import of that size.


## 11 November 2026: a film that leaves the list no longer rewrites the others (HASH-1)

`OwnerScaleImportTest` on the emulator, 200,000-film M3U catalogue, with a third import of
the same list without its first film. Before, the position in the list was part of each
film's change check, so every later film was rewritten. Logs:
`harness-out/plan/HASH-1-before-measure.log` and `HASH-1-after-measure.log`.

| Import | Before | After |
|---|---|---|
| First (200,000 films, series) | 33.0 s | 33.0 s |
| Unchanged | 12.6 s | 12.6 s |
| First film removed, whole import | 52.3 s | 12.6 s |
| First film removed, write transactions | 48.1 s | 4.2 s |
| First film removed, longest small-write wait | 1,355 ms | 194 ms |

The 4.2 s is 199,999 one-column position updates by row id. The first refresh after this
change rewrites every film and series once, because every stored hash changes.

## 11 November 2026: pictures already in memory are in a cell's first frame (IMG-1)

Main-thread CPU per key press, lab build on the emulator, fixture playlist (56,164 channels)
and film list (200,000 films). Read from the main thread's `stat` (10 ms ticks) before and
after the presses.

| Screen | Presses | Before | After |
|---|---|---|---|
| Guide, channel column (Up and Down) | 240 | 68 ticks, 2.8 ms per press | 68 ticks, 2.8 ms per press |
| Film wall, one group of posters (Up and Down) | 200 | 60 ticks, 3.0 ms per press | 58 ticks, 2.9 ms per press |

The step adds no measurable work per press. The counter is coarse (one tick is 10 ms), so
runs of 20 presses differ by a tick or two; the table uses the sums. On a desktop emulator
these numbers only guard against a regression; they say little about the low-end box.

## 11 November 2026: a focusable creates animation state only when it animates (SURF-1)

Measured as for IMG-1 above, same lab data, before = the build with IMG-1.

| Screen | Presses | Before | After |
|---|---|---|---|
| Guide, channel column (Up and Down) | 240 | 68 ticks, 2.8 ms per press | 64 ticks, 2.7 ms per press |
| Film wall, one group of posters (Up and Down) | 200 | 58 ticks, 2.9 ms per press | 57 ticks, 2.85 ms per press |

Within the counter's noise on the emulator: no regression. The saving is in objects per
focusable (two animation states fewer for rows and cards that neither scale nor fade), which
this counter does not show; it should be checked on the low-end box.
