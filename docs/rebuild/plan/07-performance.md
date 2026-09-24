# Performance

> Budgets per device class, the rules that meet them, the low-RAM mode, how to measure, and every
> performance incident of the current app with its cause. Every line of the rebuild is written
> against this file ([AGENTS.md](../../../AGENTS.md) §4). The starting budgets are in
> [_spec-conventions.md](_spec-conventions.md); this file refines them and reconciles the specs'
> section 9 ("Lightweight by design").

## 1. What this file decides

- How the app tells a low-end device from a high-end one, and what changes (§2, §5).
- Measurable budgets for size, start-up, screens, key presses, memory, database and imports (§3).
- The rules that meet them, collected from every spec and reconciled where specs disagreed (§4).
- The measurement method: the slow-box emulator stand-in, traces, Macrobenchmark, memory sampling (§6).
- What went wrong before and why (§7).

Each budget says whether it is a **measured** reference from the current app or a **proposed**
target. Proposed targets are starting values; a milestone may tighten them with a measurement, and
may loosen one only with the owner's agreement recorded in `docs/decisions.md`.

## 2. Device classes

### 2.1 Reference devices

| Class | Device | CPU / GPU / RAM | Android | Known heap |
|---|---|---|---|---|
| High end | Nvidia Shield TV (the owner's) | Tegra X1, out-of-order Cortex-A57, 3 GB | 11 | 192 MB Java heap limit |
| Low end, must run well | Elisa Viihde box, ZTE B866V2F01 | Amlogic S905Y4, 4× in-order Cortex-A35 @ 2 GHz, Mali-G31 MP2 (about a tenth of the Shield's fill rate), 2 GB | 12 (API 31) | unknown (§10) |
| Low end, must run well | Xiaomi TV stick class (a tester reported guide slowness) | quad Cortex-A35/A53 class, Mali-G31/450 class, 1–2 GB | 9–11 | unknown |
| Measurement stand-in | Android TV emulator, API 30, x86, 1080p | desktop cores several times faster than the box's; software GPU (SwiftShader) | 11 | 192 MiB (201,326,592 bytes) |

The emulator's frame times say nothing about the box: its cores are faster and its GPU is emulated
on the CPU. It is used for **relative CPU time per phase** and for counters (§6.2).

### 2.2 The memory tier

The app decides once at start, off the main thread, and logs the inputs in Save diagnostics:

- **Low tier** when any of: `ActivityManager.isLowRamDevice()`; `memoryClass < 192` (MB);
  `ActivityManager.MemoryInfo.totalMem` ≤ 2.5 GiB (proposed; puts 2 GB boxes in the low tier and the
  3 GB Shield in the standard tier).
- **Standard tier** otherwise.
- **Reduced motion** is on in the low tier and whenever the system animator duration scale is 0
  (motion only; memory settings stay standard on a standard-tier device).

Diagnostics record `isLowRamDevice`, `memoryClass`, `largeMemoryClass`, `totalMem`,
`Build.SUPPORTED_ABIS`, the animator scale, the display mode's refresh rate and the chosen tier, so
testers' files calibrate the thresholds without anyone touching a device.

## 3. Budgets

Vsync: 16.7 ms at 60 Hz, 20 ms at 50 Hz (European TV modes). "Two vsyncs" is 33 ms or 40 ms.
Emulator figures are main-thread CPU on a release-like build unless stated.

### 3.1 Size and start-up

| Measure | Shield | Low-end box / stick | Emulator stand-in | Basis |
|---|---|---|---|---|
| Release APK | ≤ 8 MB target, 10 MB ceiling | same | – | measured: beta 23 7,841,731 bytes, beta 22 18,016,927 bytes; M0 ≤ 4 MB ([02](02-roadmap.md)) |
| Cold start → first frame (launch screen = window colour) | ≤ 500 ms | ≤ 1.2 s | `am start` ≤ 400 ms, compiled | measured: build 51 first frame 712 ms uncompiled, 293 ms compiled (Shield); beta 23 emulator cold launch 562 ms; proposed targets |
| Cold start → Home content drawn (`reportFullyDrawn`: hero and first row) | ≤ 2 s | ≤ 4 s | start-up phase ≤ 150 ms compiled, ≤ 250 ms freshly installed (`verify`) | conventions; measured beta 23 harness: 206 ms compiled, 380 ms fresh (R8) |
| Home's first composition, compiled | ≤ 200 ms | ≤ 600 ms | – | measured: build 51 Shield 218 ms compiled, 922 ms uncompiled; box proposed |
| App methods JIT-compiled in the first 20 s after an update | near zero (profile installed with the update) | same | same | measured: 6,200 ms of JIT in 20 s uncompiled vs 304 ms compiled (Shield) |

### 3.2 Screens and key presses

| Measure | Shield | Low-end box / stick | Emulator stand-in | Basis |
|---|---|---|---|---|
| D-pad press → next frame (guide, walls, Home, Settings, Today, Discover), single or held | ≤ 2 vsyncs | ≤ 2 vsyncs | see the next rows | conventions |
| Guide press, main-thread CPU | – | – | ≤ 11 ms between rows, ≤ 7 ms along a row | spec 20 GUIDE-NFR-01; measured beta 22/23: 31.7 and 21.0 ms (from 38.9/24.8); Shield ≈ 34 ms |
| Wall press / Home press, main-thread CPU | – | – | ≤ 8 ms each | proposed; beta 23 never measured per press (measure once at M0, §10) |
| Frames over 2 vsyncs caused by database or background work while a key is held | 0 | 0 | 0 (counter) | specs 10, 40 |
| First guide frame after OK, compiled | ≤ 100 ms | ≤ 300 ms | – | spec 20 GUIDE-NFR-26; measured build 51 compiled 92 ms, uncompiled 408 ms |
| Guide rows of the opening group visible (56,000-channel source) | ≤ 0.5 s | ≤ 1.5 s | – | spec 20 GUIDE-NFR-27; measured preview 52 0.3–0.5 s (build 51 on All channels ≈ 4 s) |
| Guide rail aggregate read | – | – | ≤ 50 ms (else materialise counts) | spec 20 GUIDE-NFR-16 |
| **Screen-open budget** (any screen: chrome in the first frame after OK; first page of data drawn) | ≤ 300 ms | ≤ 700 ms | – | spec 40 §9.1; used by specs 21, 50, 60 |
| Wall first page (any destination up to 200,000 films) | ≤ 300 ms | ≤ 700 ms | – | spec 40 §9.1 |
| Wall search page after the 250 ms debounce | ≤ 300 ms | ≤ 800 ms, cancellable | – | spec 40 §9.1 |
| Similar (20 references) | ≤ 300 ms | ≤ 1 s | – | spec 40 §9.1 |
| Unified search, each result group | – | ≤ 100 ms; abandoned and shown as partial at 1 s | – | spec 03 §9 |
| Return from details / Back to a list | first frame, focused item restored | same | – | specs 01, 40 |
| Player with overlays hidden | 0 Compose frames, 0 recompositions in 60 s | same | counter | spec 30 §9 |
| Sport feed parse and publish, per sport | – | ≤ 300 ms off the main thread | – | spec 60 §9 |

Emulator proxies assume the box's in-order cores need about **3×** the emulator's main-thread time
(so 11 ms on the emulator ≈ 33 ms on the box). The factor is an assumption to calibrate (§10).

### 3.3 Memory

| Measure | Standard tier | Low tier | Basis |
|---|---|---|---|
| Java heap, steady browsing (any screen, any library size) | ≤ 64 MB | ≤ 64 MB | conventions |
| Java heap, during any import | ≤ 128 MB | ≤ min(128 MB, 75 % of `memoryClass`) | conventions, spec 10 §9 (low-tier cap proposed) |
| An import's own working set | ≤ 16 MB | ≤ 16 MB | spec 10 §9 |
| Java heap during playback | ≤ 64 MB + the buffer profile's `targetBufferBytes` (32 MiB default, 16 MiB low latency, 64 MiB stability) | Stability capped at 32 MiB | spec 30 §9 (Media3 buffers live on the Java heap) |
| Stream pairing scan peak | ≤ 16 MB | ≤ 16 MB | spec 60 §9; measured preview 49: 12.03 MiB |
| Sohva Sport steady state | ≤ 4 MB | ≤ 4 MB | spec 60 §9 |
| One poster wall (entries, keys, state; bitmaps excluded) | ≤ 2 MB | ≤ 2 MB | spec 40 §9.1 |
| Decoded image memory cache | 8 % of `memoryClass` (≈ 15 MB at 192) | same fraction | specs 01, 40; measured cause of the 300 MB native heap (§7) |
| Bitmaps outside the cache | ground ≈ 2 MB (960 × 540 ARGB); Home hero plate ≈ 1.5 MB (≈ 1,267 × 605 `RGB_565`), two during a 250 ms crossfade; details backdrop ≤ 4.1 MB (1,920 × 1,080 `RGB_565`) | ground 2 MB, one hero plate at half size, no second bitmap | design/01 §16, spec 02 §9.2, spec 40 §9.5 |
| Total PSS, steady browsing | recorded at M0; ceiling = first measurement + 20 % | same | proposed (no beta 23 figure exists) |

Since Android 8 bitmap pixels live in native memory, so image budgets count against PSS, not the
Java heap. Heap numbers are read with `dumpsys meminfo` sampling or an in-test sampler (§6.6).

### 3.4 Database and imports

Owner scale: 56,164 channels in 800 groups, 165,600 programmes, 30,000–200,000 films, 1,500 series
of 12 episodes (plus a stress variant: programmes with 2,150-character descriptions).

| Measure | Shield | Low-end box / stick | Emulator stand-in | Basis |
|---|---|---|---|---|
| 56,164 channels, import to activation | ≤ 20 s | ≤ 60 s | recorded per build | spec 10 proposal (box); Shield proposed |
| 165,600 programmes kept (from a larger feed) | ≤ 30 s | ≤ 90 s | recorded | spec 10; measured 5 Sept 2026: 134,000 programmes 35 s → the 54,000 that mattered 12 s |
| 200,000 films | ≤ 60 s | ≤ 180 s | recorded | spec 10 |
| 30,000 films; 1,500 series / 18,000 episodes | ≤ 15 s; ≤ 10 s | ≤ 45 s; ≤ 30 s | whole 30,000-film fixture: beta 23 ≈ 90 s | proposed; measured beta 23 seed ≈ 90 s |
| Re-import of an unchanged Xtream catalogue | ≤ 10 % of the first import | same | – | proposed (SRC-L-15 writes only changed rows) |
| One write transaction (batch, activation, cleanup batch) | – | ≤ 100 ms | – | spec 10 SRC-L-09, SRC-L-16 |
| D-pad presses and video during an import | no press later than 2 vsyncs, no dropped video frame attributable to the import | same | counters | spec 10 §9 |
| Database file after import and checkpoint, realistic descriptions, 200,000 films | ≤ 250 MB | same | recorded at M1 | proposed; beta 23's size was never recorded (§10) |

The database estimate behind the 250 MB: channels about 20 MB, kept programmes about 80 MB, films
about 70 MB, series and episodes about 6 MB, search tables 20–40 MB. It is an estimate for sizing,
not a measurement. The 2,150-character stress variant is exempt from the size budget but not from
the heap and time budgets.

### 3.5 Rendering, background work and network

| Measure | Budget | Basis |
|---|---|---|
| Full-screen passes per frame on a UI screen | ≤ 2 (ground + one scrim or backdrop); Home ≤ 3 only during its 250 ms crossfade on the standard tier | design/01 §16.1, spec 02 §9.4 (reconciled, §4.12) |
| Full-screen passes over video | ≤ 1 | design/01 §16.1 |
| Offscreen layers per screen | none except pre-rendered bitmaps | design/01 §16.1 |
| Shadows per frame | ≤ 1, pre-rendered; none in the low tier | design/01 §16.1 rule 6 |
| Concurrent image decodes, app-wide | ≤ 2 | measured cause: 20 at once pushed frame p90 above 100 ms |
| Bulk work priority | `THREAD_PRIORITY_BACKGROUND`, pauses while video plays unless viewer-started | plan/03 §4.7 |
| API-Sports requests | ≤ 100 per sport per UTC day including cold starts | spec 60 §9 |
| Addon requests | ≤ 4 concurrent, 15 s timeouts, bodies ≤ 2 MiB | spec 50 §9 |
| Trakt | one small request per 15 minutes; nothing during playback except scrobbles | spec 51 §9 |

## 4. Rules that meet the budgets

Collected from the specs' section 9; the spec named is where each rule is specified in full.

### 4.1 Start-up

1. `Application.onCreate` builds lazy holders only: no disk, database, DataStore, PackageManager,
   WorkManager, Keystore, network or image-loader construction on the main thread before the first
   frame (specs 01, plan/03 §4.9; OwnTV item 20).
2. The launch screen matches the window background; the window background becomes a flat colour
   after two frames (spec 01, design/01 §16.1).
3. The first database work is Home's resume read; Trakt, sport, metadata, update check, scheduling,
   reminder re-arm and legacy-work cancellation start after Home's first content frame (spec 02
   §9.1, plan/03 §4.9).
4. Optional features (Sohva Sport, Discover, Trakt, the updater) are constructed on first use;
   Discover's progress store is standalone and decrypts only the ≤ 12 entries that become cards
   (specs 02, 50, 51, 60).
5. Migrations change schema only; data rewrites are resumable background jobs; `ANALYZE` never runs
   at launch (plan/03 §4.9, spec 10 SRC-L-22; OwnTV item 11).
6. Generated baseline and startup profiles ship as `.dm` files and are installed with updates
   ([05](05-tech-stack-and-build.md) §4.9; OwnTV item 21).

### 4.2 Main thread and recomposition

1. Main thread = composition, layout, drawing, input. No database, network, file, parsing, regex,
   sorting, hashing, encryption or Keystore reads (AGENTS §4 rule 1; specs 04, 20, 40).
2. Hot state (focused key, selection, clock minute, playback position) lives in small holders read
   in the smallest scope: a guide press recomposes the hero and nothing in the grid; a wall press two
   cards; a Home press nothing until the 180 ms rest changes the hero (specs 02 §9.6, 20 GUIDE-NFR-02,
   40 §9.6, 50).
3. Collect narrow flows: never the whole preferences object (every zap writes a recent channel);
   the root reads only the stack, the theme and the scale (specs 01, 02).
4. Formatting, sorting, grouping, stream-tag parsing and title normalisation happen once per data
   change on a background dispatcher, never in composition (specs 02, 20 GUIDE-NFR-20/21, 40 §9.4, 60).
5. Colour and focus animations are read in the draw phase (spec 20 GUIDE-NFR-04, design/01 §16.2).
6. Text is measured once: labels formatted per data change, a bounded text-measure cache
   (512 entries, LRU) for the guide, no inset change on selection (spec 20 GUIDE-NFR-03).
7. Observers wake once per import, through a cheap generation signal, never per batch; background
   writes to observed tables are batched and rate-limited (spec 10 SRC-L-23, plan/03 §4.7 rule 6,
   spec 40 §9.3: metadata version bumped at most every 5 s; spec 02: at most one re-read per second).
8. Settings composes only the selected section and the visible rows (spec 10 SRC-L-27; OwnTV item 19).
9. The player composes nothing while overlays are hidden; position ticks redraw one bar and one text
   (spec 30 §9).

### 4.3 Database reads

1. **No whole-catalogue read.** Keyset pages along an index in display or primary-key order,
   `WHERE key > :last ORDER BY key LIMIT n`, never `LIMIT/OFFSET` over a sorted statement (AGENTS §4
   rule 2; OwnTV item 15).
2. **One CursorWindow per read.** A result larger than 2 MiB re-executes the statement for every
   refill and cannot be cancelled; page sizes keep each read inside one window (AGENTS §4 rule 3).
3. **Page sizes** (reads into memory): guide rows 200, ≤ 3 pages resident (spec 20); wall entries
   120, ≤ 5 pages (spec 40); channel manager 200, ≤ 3 pages (spec 21); pairing channels 256 and
   programmes 64 (spec 60); Trakt ≤ 250 films / 100 shows per page (spec 51); favourites and lists
   by id in batches ≤ 500 (spec 20); bulk passes ≤ 2,000 (plan/03 §4.7).
4. **Pinned join order**: queries over the organisation views start from their small side with
   `CROSS JOIN` and reach the views by full key; every hot query is a constant with a JVM plan test
   (no scan of a title table, no `TEMP B-TREE`), with and without `ANALYZE` (specs 02 §9.3, 51).
5. **Statistics**: `ANALYZE` of the touched tables after each activation, `PRAGMA analysis_limit=400`
   where SQLite allows, off the launch path, never while video plays (spec 10 SRC-L-22).
6. **Order without sorting in memory**: display positions materialised per channel and view in
   background pages (spec 20 GUIDE-NFR-11); stored sort keys for walls (collation keys as BLOBs, year,
   rating, quality and claim masks) computed at import (spec 40 §9.3).
7. **Full-text search** tables maintained by the imports, queried with `MATCH` and `LIMIT`; limits 80
   channels + programmes, 40 per catalogue kind (spec 03).
8. **Cancellation**: one search in flight; a new term or leaving the screen cancels the statement
   through a `CancellationSignal` (specs 03, 40).
9. **Allowed-group restrictions** join a small indexed table, never an `IN` list of hundreds of
   parameters (SQLite before 3.32 allows 999) (specs 04, 40).
10. **Counts** live in a small counts table recomputed after activation and at most every 30 s during
    a metadata pass, not whole-catalogue `COUNT(DISTINCT …)` per write (spec 40 §9.3).
11. **WAL explicitly** (Room's automatic mode falls back to a rollback journal on low-RAM devices)
    (spec 10 SRC-L-24, plan/03 §4.7 rule 5).

### 4.4 Imports and other bulk work

1. **Stream everything**: M3U line by line with a bounded line length, XMLTV with the platform pull
   parser, gzip sniffed by magic bytes, JSON with a streaming reader; never a whole tree (spec 10
   SRC-L-03; OwnTV item 5).
2. **Overlap** download + parse and database writes on two background-priority threads with a channel
   of capacity 1 (three batches in memory at most) (SRC-L-08; OwnTV item 6).
3. **Batch sizes**: 250 rows for channels, films, series, episodes; programmes ≤ 1,000 rows or ≤ 2 MB
   of text; one transaction per batch, tuned to ≤ 100 ms on the box (SRC-L-09).
4. **Store only what is needed**: programmes for the playlist's channels only, decided on the start
   tag; past programmes only where catch-up can play them; no unused columns; Xtream stream ids
   instead of encrypted full addresses; programme ids as 64-bit hashes; stream tags and keys computed
   once at import (SRC-L-11..14; OwnTV items 9–10).
5. **Write only changes** for sources with stable ids (SRC-L-15).
6. **Activate then sweep**: activation switches ids in a tiny transaction; the old snapshot is deleted
   afterwards in batches; staging snapshots are registered so cleanup never deletes another import's
   rows (SRC-L-16/17).
7. **Indexes**: no redundant ones; the programme read key is the primary key; secondary indexes
   dropped before a first bulk load into an empty table and rebuilt once afterwards
   (SRC-L-18..21; OwnTV item 11).
8. **One import per source and kind at a time**, whatever started it (plan/03 §4.7 rule 4).
9. **Yield**: automatic work waits while video plays or the viewer browses; a viewer-started import
   continues at background priority with at least 200 ms between batches during playback
   (SRC-L-25/26; OwnTV item 4).
10. **Whole-catalogue follow-ups page** (identity, metadata queue, counts, FTS, sports pairing) in key
    order with bounded accumulators, and pause during playback (specs 40, 41, 60).
11. **Backups stream**; nothing catalogue-sized is materialised to write a settings file
    ([08](08-lessons-learned.md) 1.5).

### 4.5 Memory bounds held by screens and caches

| Holder | Bound | Spec |
|---|---|---|
| Guide rows | 3 pages × 200 rows; kept 10 minutes after leaving within the same bound | 20 GUIDE-NFR-10, 25 |
| Guide schedules | 240 channels (current window), grid rows without descriptions (< 1 MB) | 20 GUIDE-NFR-13 |
| Guide text-measure cache | 512 entries LRU | 20 GUIDE-NFR-03 |
| Guide metadata | 256 entries | 20 GUIDE-NFR-25 |
| Player channel window | ±50 channels; channel list page 100 | 30 §9 |
| Wall | 600 entries (5 × 120); watched marks ≤ 200 keys; similar cache 24 × ≤ 12 | 40 §9.10 |
| Home | ≤ 12 + 8 + 6 + 20 + 6 cards, hero plate(s) | 02 §9.6 |
| Search | about 200 small rows + sport rows | 03 §9 |
| Channel manager | 3 pages × 200 | 21 CHAN-NFR-01 |
| Discover | landing 24 shelves × 20; grid ≤ 1,000; search ≤ 200; title page ≤ 10,000 videos, ≤ 60 cast; one subtitle ≤ 4 MiB | 50 §9 |
| Sport | results cache 512 events, 40,000 results, 16 MiB, 24 h | 60 §9 |
| Import | three batches; EPG-id filter as 64-bit hashes (≈ 1 MB for 56,164 ids); series headers only | 10 SRC-L-04 |
| Diagnostics log | 600 lines ring buffer | plan/03 §4.14 |
| Image memory cache | 8 % of `memoryClass` | 01, 40 |

### 4.6 Images

1. Request and decode at the drawn pixel size: logos 30–64 dp, posters ≤ 192 × 288 px on walls,
   Discover posters 252 × 378 px at 1080p, crests at their dp size, hero art ≤ the art box
   (≈ 1,267 × 605 px), details backdrop ≤ screen size (Discover ≤ 960 × 540) (specs 02, 20, 40, 50, 60).
2. `RGB_565` for opaque art (posters, stills, backdrops, cast circles); ARGB only for logos and crests
   with transparency (design/04, spec 40).
3. At most two decodes app-wide; requests of rows scrolled away are cancelled (specs 01, 02, 40).
4. Crossfades: 140 ms default, none on wall posters and details backdrops, 120 ms Discover, 250 ms
   hero; none in the low tier or while a key is held (design/04 §3).
5. Memory cache 8 % of `memoryClass`; disk cache 100/250/500 MB chosen by the viewer (default 250).
6. QR codes built off the main thread at module resolution with one `setPixels`, drawn scaled with
   nearest-neighbour filtering (design/01 §16.1 rule 13; specs 50, 51).
7. Initials are always drawn under a logo or crest, so a missing image costs nothing (spec 60).

### 4.7 Drawing

1. No blur, grain, noise or glass (design/01 §16.1; OwnTV item 16).
2. The static ground is one shared pre-rendered bitmap (960 × 540, bilinear, dithered), drawn once per
   frame as the first call; no window background under content (design/01 §16.1 rule 2).
3. Home's hero is one pre-composited "plate" bitmap per artwork change, built off the main thread;
   details backdrops likewise; scrims baked or drawn as pre-rendered low-resolution images
   (spec 02 §9.4, spec 40 §9.7, spec 50).
4. Opaque panels instead of ≥ 0.9 scrims, and content beneath them not drawn (design/01 rule 4;
   spec 60 hub).
5. The player chrome scrim is two bands (top 16 %, bottom 48 %) (spec 30).
6. No per-cell clip or alpha layers; `drawRoundRect` for fills without images; "past" alpha baked into
   colours; the guide draws a row's programmes on one canvas with one focusable per row (spec 20
   GUIDE-NFR-04, roadmap M0 spike; OwnTV items 12–13).
7. Shadows only on large focused cards, pre-rendered, never animated (design/01 rule 6; OwnTV item 17).
8. The rail is laid out once at full width in its own layer; collapse and expand change only draw
   transforms and alpha, 150 ms tween, snap in the low tier (specs 01, 02 §9.5).
9. Every infinite animation stops or steps in reduced motion; the zero-duration test guards it
   ([06](06-quality-testing-release.md) §10.5; OwnTV item 18).

### 4.8 Playback comes first

1. Bulk work pauses while video plays unless viewer-started (plan/03 §4.7 rule 2).
2. The player reads bounded windows (±50 channels, the playing channel's 7-hour timeline when its box
   opens), never the guide; the VOD player runs no guide query (spec 30).
3. Explicit buffer byte targets (§3.3), back buffer 0; `SurfaceView`, no layer, alpha or clip on the
   video host (spec 30).
4. Display-mode matching from track events, not a sampling loop (spec 30).
5. The player's application looper is a dedicated thread; leaving the app releases the stream
   (spec 30; OwnTV item 23).
6. A software decoder asked for more than 1080 lines stops with a plain message instead of stalling
   the box (spec 30).
7. Home's resume projection does not recompute during playback; Discover writes progress only when
   the position moved ≥ 5 s; Trakt's loop is suspended except scrobbles; sport polls only while its
   ticker or screen is visible (specs 02, 50, 51, 60).

### 4.9 Network

1. One OkHttp base (shared pool and dispatcher); the provider client HTTP/1.1 only; the image and API
   client HTTP/2 (plan/03 §4.12; OwnTV item 22).
2. Every optional step before playback has a time budget and a fallback (subtitles: 5 s)
   ([08](08-lessons-learned.md) 6.2).
3. Bodies are streamed through byte-counting caps (addon 2 MiB; a 1 GiB runaway guard for provider
   lists) (specs 10, 50).
4. No prefetch of metadata, streams or subtitles in Discover; no sport polling in the background
   (specs 50, 60).

### 4.10 Code shape and compilation

1. R8 and resource shrinking from the first release; profiles generated and shipped
   ([05](05-tech-stack-and-build.md) §4.6–4.9).
2. No method above 9,500 dex code units (95 % of ART's ahead-of-time limit); composables short,
   ≤ 8 parameters, state holders instead of long parameter lists (specs 01, 30; plan/03 §4.6).

### 4.11 Accessibility semantics

Dense grids expose one semantics node per row or cell (`clearAndSetSemantics` innermost, with the
text stated on the node); decorative icons carry none. A button-remapper accessibility service must
add at most 10 % to a guide press (spec 20 GUIDE-NFR-06; specs 01).

### 4.12 Conflicts between the specs, and how they are resolved

| Topic | What the specs said | Resolution |
|---|---|---|
| Low-RAM threshold | spec 02 "memory class ≤ 192 MB"; design/01 §16.3 "at or below a threshold (proposed 192 MB)"; spec 30 "under 192 MB" | The Shield's limit is 192 MB, so "at or below" would put the high-end reference in the low tier. Use **`< 192` MB**, plus `isLowRamDevice` and `totalMem` ≤ 2.5 GiB (§2.2). Spec 02 and design/01 were corrected to this rule on 24 September 2026 |
| Full-screen passes on Home | spec 02 "≤ 3"; design/01 "≤ 2 on a UI screen" | ≤ 2 steady on every screen (ground + plate); ≤ 3 only during Home's crossfade on the standard tier |
| Page sizes vs cleanup batches | AGENTS "≤ 2,000 rows per page"; spec 10 cleanup "about 5,000 rows per transaction" | Reads into memory ≤ 2,000 rows (UI windows smaller as specified). Writes and deletes are sized by time, ≤ 100 ms per transaction on the box, starting at 250 inserted rows, programmes ≤ 1,000 rows/2 MB, 5,000 deleted rows |
| Heap during playback | conventions 64 MB steady; spec 30: Media3's default video buffer alone is 131 MB of Java heap | The 64 MB steady budget excludes the playback buffer; during playback the ceiling is 64 MB + the profile's byte target |
| Import heap on 1 GB sticks | 128 MB may exceed a small `memoryClass` | Low tier: min(128 MB, 75 % of `memoryClass`) |
| Hero decode in the low tier | design/01 §16.3 keeps 1,280 × 720 in reduced mode; spec 02 halves it on low RAM | Standard tier ≤ the art box's pixel size (≤ 1,280 × 720); low tier half of that, single bitmap, no crossfade; confirmed by a screenshot comparison on the box (§10) |
| Key-press budget | conventions "one or two vsyncs"; spec 20 emulator CPU figures | Device budget in vsyncs; emulator proxy in CPU ms with the calibration factor of §3.2 |
| Paging idiom | an older note paged background jobs with `LIMIT/OFFSET` in key order | Keyset always (`WHERE key > :last`), as AGENTS §4 rule 2 requires |
| Kept guide list | beta 23 kept the whole roster for 10 minutes | Keep the bounded window (≤ 600 rows) for 10 minutes, nothing more |

### 4.13 OwnTV ideas adopted here

Items of [reference/owntv-study.md](../reference/owntv-study.md): 5 stream everything (§4.4.1);
6 overlapped download, parse and write (§4.4.2); 9 guide filtered while parsing and
10 past programmes only for catch-up (§4.4.4); 11 index handling and `ANALYZE` after sync (§4.3.5,
§4.4.7); 12 one canvas per guide row (§4.7.6); 15 bounded paging and FTS (§4.3); 16 no expensive
looks (§4.7.1); 17 shadows only where they do not trail (§4.7.7); 18 animations off and the
zero-duration test (§4.7.9); 19 Settings composes only its section (§4.2.8); 20 an idle
`Application.onCreate` (§4.1.1); 21 D-pad Macrobenchmark profiles over a synthetic catalogue (§6.5,
[05](05-tech-stack-and-build.md) §4.9); 22 two HTTP clients (§4.9.1).

## 5. Low-RAM and reduced-motion mode

Nothing in the look changes: colours, typography, layout sizes, the ground, rings and fills, which
element shows focus, and the functional delays (180 ms hero settle, 350 ms metadata, 250 ms search
debounce, dial timeouts 2,000/1,500 ms) stay the same in every tier ([design/01](../design/01-design-system.md)
§16.3). What changes:

| Element | Standard tier | Low tier (and reduced motion where marked *) |
|---|---|---|
| Image memory cache | 8 % of `memoryClass` | same fraction (smaller in bytes) |
| Image crossfades | 140 / 120 / 250 ms, none on wall posters | none* |
| Home hero | art box size ≤ 1,280 × 720 `RGB_565`, two bitmaps during a 250 ms crossfade | half size, one bitmap, cut* |
| Focus fills and scales | short tweens | jump to the end value* |
| Rail expand/collapse | 150 ms tween | snap* |
| `AnimatedVisibility` fades, the match hub slide | as designed | instant show/hide* |
| Live-badge pulse, Discover title pulse | animate | static at full opacity* |
| Buffering arc | animates | steps every 150 ms* |
| Shadows | one pre-rendered shadow on the focused large card | none |
| Match hub stream rows | per-row gradients | flat `surfaceSubtle` fills |
| Player Stability buffer profile | 64 MiB | 32 MiB |
| Guide hero while a key repeats | updates every press (optionally coalesced to one per 100 ms) | coalesced to one per 100 ms |
| Import heap ceiling | 128 MB | min(128 MB, 75 % of `memoryClass`) |

Whether the viewer also gets a manual "Reduce motion" switch in Settings › General is the owner's
decision ([design/01](../design/01-design-system.md) §18 question 4).

## 6. Measurement method

### 6.1 The owner-scale synthetic fixture

A generator in the new repository (test tooling, never shipped), modelled on the private
`.local/slowbox/make_fixture.py` of the old repository:

- 56,164 live channels in 800 groups (country × kind names, 65 % with logos); a guide of 1,800
  channels × 92 programmes of 45 minutes = 165,600 programmes, **anchored to the time it runs**
  (regenerate if more than about two days old: the importer keeps 12 hours of history); 30,000 films
  in 60 genres and a 200,000-film variant with one 40,000-film group; 1,500 series of 12 episodes;
  a stress variant with 2,150-character descriptions.
- Real JPEG posters and backdrops and PNG logos (40 images served under as many addresses as the
  playlist names, so every address is a separate download and decode).
- Fictional names only; no provider, channel, programme or stream address.
- Served by a small threading HTTP/1.1 server bound to the host's **loopback interface only**; the
  emulator reaches it through its fixed alias for the host loopback. Streams for player journeys are
  FFmpeg-generated (colour bars and tone), as the Lab suite does.
- An Xtream-shaped variant (JSON `player_api` endpoints) and a synthetic addon, API-Sports and Trakt
  responder exist for the journeys that need them.

### 6.2 The slow-box stand-in

The approach of the private harness (`.local/slowbox`: `make_fixture.py`, `serve_fixture.py`,
`LabBenchmarkLibrarySeed`, `journey.sh`, `compare.sh`, `analyze.py`, `smoke.sh`), rebuilt in the new
repository:

1. A **cold-booted** Android TV emulator (API 30 today; see §10 for API 31), one run at a time. Before
   starting, confirm no earlier runner is still sending keys (stopping a background shell once left
   its child scripts driving the emulator, and every number was garbage).
2. **Reseed before every run**: clear the Lab package's data, install the seeding build and import the
   fixture through the app's own importers, then install the build under test over it with its data
   kept. A run that strays (into the player, say) would otherwise change what the next run starts from.
3. **Set the compile state** per run: `verify` (freshly installed, the worst case), `speed-profile`
   (what a profile-carrying install gives), `speed` (fully compiled).
4. **Drive a scripted journey** on the emulator only (cold start, Home, the guide, the film wall, a
   series shelf, and for new work the screen being changed), writing each phase's start on the boot
   clock the trace uses.
5. **Trace** with Perfetto: a 128 MiB ring buffer plus 4 MiB, 240 s, written to file every second and
   flushed every 2 s; ftrace `sched_switch`/`sched_waking`; atrace `gfx`, `view`, `am`, `dalvik`,
   `input`, `database` and the app's own sections.
6. **Compare CPU time per phase** by thread: main, `RenderThread`, `Jit thread pool`, all others; plus
   frames over 16.7/33.3 ms, start-up numbers and process JIT and GC totals. Emulator frame times are
   not the box's; CPU time is comparable between builds. Beta 23's runs repeated to about 5–8 %.
7. **Smoke the shrunk build**: visit every screen and search the log for
   `FATAL EXCEPTION|ClassNotFoundException|NoSuchMethodError|NoSuchFieldError|VerifyError|AbstractMethodError|IncompatibleClassChangeError`.

The reference numbers of beta 23 in this harness (main-thread CPU, fully compiled, before → after the
September 2026 changes): start-up 308 → 206 ms, Home 524 → 423, guide browsing 573 → 499, film
browsing 849 → 419, series browsing 232 → 176; freshly installed start-up 536 → 380 ms with R8.

### 6.3 Reading traces

- **SQL labels are on process tracks.** With the `database` category, Android's `SQLiteConnection`
  emits async slices `prepare <sql>`, `executeForCursorWindow <sql>` and `execute <sql>` (names cut at
  256 characters) on **process** tracks: join `slice` to `process_track`, not `thread_track`
  (an 18 September 2026 analysis joined thread tracks only and wrongly concluded there were no
  labels). One `prepare` is one query; several `executeForCursorWindow` slices with no `prepare`
  between them are one cursor refilling its window.
- The logcat line `CursorWindow: Window is full: requested allocation N bytes` names the result shape:
  on Android 11 N = 12 × columns (252 = the old guide's 21-column row).
- **Named sections**: screens, grids, rows, cells and heroes open `android.os.Trace` sections
  (`Guide:Screen`, `Guide:Row`, `Guide:ProgrammeCell`, `Guide:Hero` in the old app; free when nothing
  records); trace tests write markers around scripted presses (`GuideNavigation:vertical`,
  `GuideNavigation:horizontal`). Sum main-thread **self time per slice name** inside the markers and
  divide by the presses; ignore the test harness's `IdleResource.Compose-Espresso link` slices.
  Composition tracing (`runtime-tracing`) is enabled in Lab and benchmark builds so composables are
  named ([05](05-tech-stack-and-build.md) §4.3).
- **Accessibility**: before diagnosing jank, read (never change) `enabled_accessibility_services`;
  a remapper's cost appears as main-thread slices `checkForSemanticsChanges`,
  `sendAccessibilitySemanticsStructureChangeEvents` and similar, outside `doFrame`.
- **Compile state**: before reading any start-up or first-screen capture, check how long ago the
  build was installed and look at `Jit thread pool` activity; `dumpsys package` saying
  `speed-profile, reason=install` meant *no* profile when the profile was empty at install.
- **Threads**: JIT and GC run on their own threads; a concurrent GC's total time is not a pause (the
  logged pauses were 0.066–0.302 ms against totals of 100–532 ms). A process caught at fork is named
  `zygote64` in the trace: select by pid.
- Trace processor: the old analysis used Perfetto `trace_processor_shell` v58.2 with SQL files.

### 6.4 simpleperf

For attribution inside a phase: `simpleperf record` with `-e cpu-clock` on the emulator; profile the
**unshrunk** release-like build for names (R8 merges classes and renames lambdas even without
obfuscation), and the shrunk build for totals.

### 6.5 Macrobenchmark

`:benchmark` ([05](05-tech-stack-and-build.md) §4.9) runs, over the seeded fixture and D-pad-only
journeys:

- start-up, `StartupMode.COLD`, `StartupTimingMetric` (time to initial display, time to full display
  through `reportFullyDrawn` when Home's hero and first row are drawn), with
  `CompilationMode.Partial(BaselineProfileMode.Require)` and `CompilationMode.None`;
- guide, wall and Home journeys with `FrameTimingMetric` and `TraceSectionMetric` for the named
  sections;
- at least 10 iterations; on the emulator the benchmark library's emulator error is suppressed and
  results are read relatively, never as device figures.

### 6.6 Memory

- In tests: a sampler thread records the Java heap in use every 250 ms (as `SportsMatchingMemoryTest`
  did) and a main-thread heartbeat counter proves the UI kept running; the test asserts the peak.
- On a device or the stand-in: `dumpsys meminfo <package>` every 30 s for five minutes after a fresh
  start and during imports and background passes; the app's own diagnostics lines report pages,
  rows and milliseconds for large reads and passes (counts and timings only).
- Count `CursorWindow` "Window is full" lines; any is a failure.

### 6.7 Real devices and testers

- **Shield**: only with the owner's explicit go for each action; read-only captures (a trace config
  started by script, the app's log, reset frame counters) with no key events; a cold start by asking
  the system to reclaim the process, never by clearing data. Compile on the device only with the
  owner's go.
- **Elisa box and sticks**: not touched unless the owner allows it; testers send Save diagnostics,
  which carry the tier inputs (§2.2), timing marks for start-up, first Home frame, first guide rows and
  imports, and large-read lines.
- **Overdraw** cannot be measured on the software-GPU emulator (the overdraw overlay aborted apps
  there); count full-screen passes by construction in review, and on real Mali hardware with the
  owner's go (GPU overdraw overlay, `dumpsys gfxinfo` layer list).

### 6.8 Recording results

`docs/performance-log.md` in the new repository: date, commit, variant, compile state, device or
emulator image, fixture version, phase, metric, value and the beta 23 reference where one exists.
Every milestone adds a row per budget it touches ([02](02-roadmap.md) "Cross-cutting work").

## 7. Past performance incidents

Chronological. Sources: the private ledger `docs/SOHVA_SPORT_USER_REPORTS.md` (18–23 September 2026),
`docs/EPG_PERFORMANCE_REVIEW_BETA_22.md`, `docs/SOHVA_TV_BACKUP_REPAIR.md`, release receipts, commit
messages, and the specs' section 9 tables.

| # | Date | Where | What was seen | Cause | Rule |
|---|---|---|---|---|---|
| 1 | 29 Aug 2026 | channel launch, resume | every channel launch scanned the channel table; resume lookups not served by any index | no index on the filtered columns (the key prefix could not serve them) | index every hot lookup; plan tests (§4.3.4) |
| 2 | 29 Aug 2026 | guide, walls | the guide's filter-and-sort over every channel ran on every recomposition; the wall's readiness check scanned the category on every press; row mapping ran on the main thread | derived state not keyed on inputs; mapping on the collector's thread | derive once per data change off the main thread (§4.2.4) |
| 3 | 29 Aug 2026 | player | the two heaviest queries were torn down and re-subscribed every 60 s | keyed on the minute | coarse time buckets; bounded windows (§4.8.2) |
| 4 | 29 Aug 2026 | walls | posters decoded again whenever they scrolled back | no memory cache | bounded memory cache (§4.6.5) |
| 5 | 30 Aug 2026 | storage | artwork disk cache a flat 1 GB | nobody chose the bound | every cache has a stated bound; viewer picks 100/250/500 MB |
| 6 | 3 Sept 2026 | Home | Continue watching from under 1 ms to about 5.5 s on a 40,000-title library | a rewrite let SQLite start from an organisation view (16 rule sub-queries per row); no statistics | `CROSS JOIN` pinning, plan tests, `ANALYZE` (§4.3.4–5) |
| 7 | fixed 4 Sept 2026 | walls | Shield native heap above 300 MB while browsing; a library change started 20 JPEG decodes at once, frame p90 above 100 ms, GC the hottest task | phone-style memory-cache percentage; unbounded decoders | 8 % of `memoryClass`; 2 decoders (§4.6) |
| 8 | 5 Sept 2026 | guide import | 134,000 programmes took 35 s | stored programmes for channels nobody had and outside the window; upserts; a `Formatter` per hashed byte | filter while parsing, plain inserts, cheap hashing: the 54,000 that mattered in 12 s (§4.4.4) |
| 9 | until 5 Sept 2026 | player | a CursorWindow filled every five minutes on the Shield | the player observed the whole guide and re-subscribed as its "now" bucket moved | the player reads one group, bounded windows (§4.8.2) |
| 10 | 7 Sept 2026 | background jobs | process killed about four minutes after every start (200,000 films, 257,000 titles, 192 MB heap), 100 % CPU on the database and default threads | film-identity pass and metadata-queue rebuild loaded the catalogue whole | page 2,000 rows in key order; stamp-and-sweep; afterwards under 50 MB (§4.4.10) |
| 11 | 7 Sept 2026 | every query | planner without statistics on synced installs | Room never runs `ANALYZE` | `ANALYZE` after each activation (§4.3.5) |
| 12 | 15 Sept 2026 | Home, start-up (beta 16) | optional Home work, start-up reads and bulk response processing competed with the first frame; a slow subtitle provider held automatic selection for 20 s | work ordered on the UI thread; no time budget | resume read first, optional work after; 5 s subtitle budget (§4.1.3, §4.9.2) |
| 13 | 16 Sept 2026 | backup export | saving a backup froze the app, two ANRs, heap full | the export built JSON objects for the whole `organization_aliases` index | select only what the rules reference, in SQL; stream (§4.4.11) |
| 14 | 18 Sept 2026 | guide (reporter, Xiaomi stick) | up to 5 s opening Live TV with three playlists and about 90,000 programmes; frames of 700–1,081 ms | channels waited for a programme join; the log also showed the guide being JIT-compiled at first use (its share proven only later, #24) | channels first, programmes in 4-hour windows for visible rows (≤ 80 channels, 240 cached) |
| 15 | 18 Sept 2026 | guide | 24 extra database reads per 12 focus moves; a linear lookup over 56,000 channels on every focus change | custom-list flows re-created on recomposition; lookups not indexed | subscriptions created once per selection; indexes built off the main thread |
| 16 | 18 Sept 2026 | guide (Shield trace) | a database worker at 57 CPU-seconds, 19 CursorWindow warnings, heap 174–178 MB of 192; the result discarded | the whole-source roster (about 50,000 rows, 21 columns, sorted by computed values) re-executed for each of 20 window fills | keyset pages of 2,000 along the primary key with pinned joins, sorting in Kotlin: 56,164 rows in 29 pages in 4.2 s, heap 13–66 MB (§4.3) |
| 17 | 18 Sept 2026 | guide ordering | 220 MB allocated to order 50,000 channels | rebuilt key lists and lower-cased group names per channel | work each group's rule out once: 9 MB, same output (held to the old implementation over 600 randomised rounds) |
| 18 | 18 Sept 2026 | guide rows | about 45 MB for 50,000 rows | strings repeated per channel | share repeated values (about 20 MB saved) |
| 19 | 18 Sept 2026 | guide (Shield) | 39.0 % janky frames, p95 57 ms; without the service 21.9 %, p95 32 ms, missed vsyncs 174 → 23 | an accessibility service (Button Mapper) made Compose walk and diff the semantics tree: 30 % of the main thread | one semantics node per cell or row; ask testers about services first (§4.11) |
| 20 | 18 Sept 2026 | guide key press | ≈ 39 ms main-thread per press on the emulator (draw 14.5, layout 14.4, composition 10.7), ≈ 34 ms on the Shield | the screen body read the selection (1,200 lines recomposed per press), a colour animation in composition, text relaid out on a 2 dp inset change | selection in the smallest scope, draw-phase animation, no relayout: 31.7 ms; the rest is structural, hence the one-canvas row (§4.7.6) |
| 21 | 18 Sept 2026 | guide (spec 20 table) | 50–108 ms frames when programmes arrived | equal-but-new schedule objects recomposed every row | keep identity of unchanged rows |
| 22 | 19 Sept 2026 | guide held keys | held Left/Right stalled or parked focus on the channel name | a pending page consumed or leaked the wrong direction; never reproduced on an in-memory database | consume only the pending direction; gated-query tests ([06](06-quality-testing-release.md) §4) |
| 23 | 19 Sept 2026 | Sohva Sport pairing | `OutOfMemoryError` on the Shield (preview 48), 52 CursorWindow warnings, playback stutter | channel names and every programme with descriptions read whole and held together | pages of 256 channels / 64 programmes, streaming accumulator: 155 s to OOM → 54 s at 12.03 MiB peak, 0 warnings (§4.3.3) |
| 24 | 19 Sept 2026 | start-up, first guide entry | frames of 712 and 922 ms at start, 408 ms on the first guide frame; 6,200 ms of JIT in 20 s | updates ran interpreted until the nightly idle job; the hand-written profile had no guide rules | `.dm` profiles with every update; generated profiles: 293 / 218 / 92 ms compiled (§4.1.6) |
| 25 | 19 Sept 2026 | guide entry | about 4 s on "Loading" at every entry (2.9–3.3 s reading 56,164 rows); a 178 ms shader compile on the first guide frame after an update | the guide opened on All channels and kept nothing | open on a group (0.3–0.5 s); keep the bounded window 10 minutes |
| 26 | 19 Sept 2026 | reporter's pre-beta-22 log | frames of 734–1,159 ms at start and first guide entry, 658 ms in one frame's draw interval | first-use interpretation and rendering; GC and JIT were on their own threads (pauses ≤ 0.302 ms) | read threads and pauses correctly before blaming GC or the database (§6.3) |
| 27 | 23 Sept 2026 | whole app on slow boxes | release never shrunk: 39.2 MB of dex; `ActivePlayer` and `SettingsScreen` over ART's 10,000-unit AOT limit | `isMinifyEnabled = false` since the first commit | R8 and the method-size gate: 8.8 MB of dex, start-up 536 → 380 ms fresh |
| 28 | 23 Sept 2026 | every screen | static backgrounds painted 4–5 times per frame (Home about 8) | launch picture kept as window background; fill, gradient and two washes under opaque content; Home's picture and scrims per frame | ground once, flat window colour: render-thread CPU −30–50 %, janky frames over the journey 80–90 % → 34–37 % on the emulator (§4.7) |
| 29 | 23 Sept 2026 | film wall | 13 % of the main thread over a browse | title-normalisation regexes for watched marks on every layout pass | keys kept per wall: film browsing 812 → 419 ms (§4.2.4) |
| 30 | 23 Sept 2026 | film identity pass | about 20 regex passes per title (3.1 s for 30,000 films on the emulator; a minute or more on the box) at normal priority | ICU matcher per call; default pool | background-priority thread; rewrite cheaper with an equivalence test (§4.4.10) |
| 31 | 23 Sept 2026 | Home | recomposed on every D-pad press | the hero debounce read focus in composition | read focus outside composition (§4.2.2) |
| 32 | 23 Sept 2026 | guide source switch | 3.8 s reading 57,644 rows at every switch | switching landed on All channels | land on the new source's first group |
| 33 | undated (spec 10) | periodic refresh | a Shield trace caught the playlist refresh encrypting thousands of addresses while the viewer switched film groups | per-row encryption; automatic work while browsing | yield to the foreground; no per-row encryption for Xtream (§4.4.4, §4.4.9) |
| 34 | undated (spec 10) | Xtream film list | refused at a 64 MB whole-body cap on a 56,835-channel provider | whole-body reads | stream element by element (§4.4.1) |
| 35 | undated (test header) | channel manager | a tester on a slow Google TV dongle waited 1–2 s per group move | whole-library read and main-thread filtering per recomposition | paged channel manager (spec 21) |

Known and not fixed in beta 23 (each has a rebuild rule in its spec): the channel manager reads every
channel of every source in one statement and rewrites every channel's order on each move (spec 21);
unified search scans with `LIKE '%term%'` and cannot be cancelled (spec 03); activation deletes the
previous snapshot inside its write transaction (spec 10); per-row AES-GCM with a new cipher per
value, 56,164 + 200,000 calls per full sync (spec 10); the Discover landing recomposes on every press
and parses whole JSON trees (spec 50); the Trakt loop may sync during playback and rewrites its cache
wholesale (spec 51); sports pairing runs after every feed load even if Sohva Sport is never opened
(spec 60); QR bitmaps are filled pixel by pixel in composition (147,456 `setPixel` calls for 384 px)
(specs 50, 51).

## 8. Lightweight by design

The measurement itself must not cost the viewer anything:

- trace sections are free when no trace records; composition tracing exists only in Lab and
  benchmark builds;
- diagnostics timing lines are counts and durations only (no titles, ids or addresses) and are
  written to the bounded in-memory log, not to a file, until the viewer saves diagnostics;
- the tier decision runs once, off the main thread;
- the fixture generator, servers, seeding hooks and `QueryGate` never ship in the release;
- `<profileable android:shell="true"/>` makes the release traceable without making it debuggable.

## 9. Lessons from the current app

| Lesson | Rule |
|---|---|
| The first UI fixes for the guide were made from code reading and moved nothing; bucketing main-thread self time by slice name found both the accessibility walk and the whole-screen recomposition | Measure before optimising; attribute by self time per named slice |
| A trace analysis concluded "no SQL labels" because it joined thread tracks | SQL slices are on process tracks (§6.3) |
| Start-up captures of a freshly updated build measured the interpreter | Check install age and JIT activity first; ship install profiles |
| Concurrent GC totals were read as pauses; JIT on its own thread was read as a blocked main thread | Read pause, not total; check the thread |
| Emulator frame times were taken for device frame times | Compare CPU per phase and counters on the emulator; frames only on hardware |
| Three runners drove one emulator at once | One run at a time; check for leftover runners |
| A run that strayed changed the state of every later run | Reseed before every run |
| Whole-catalogue failures appeared only with the real catalogue installed, never in tests | Owner-scale synthetic fixtures in tests from M1 |
| An accessibility service can be the whole story on a TV box | Ask which services are enabled before hunting jank |
| A frame around the UI was the device's display setting, not the app | Ask about display area settings first ([08](08-lessons-learned.md) 8.5) |

## 10. Open questions

1. The emulator-to-box factor for main-thread CPU (3× assumed in §3.2). Calibrate once on a low-end
   device with the owner's permission, or from a tester's trace.
2. The Elisa box's `memoryClass`, `isLowRamDevice`, `totalMem`, ABIs and output refresh rate (50 or
   60 Hz); likewise for the stick class. They decide the tier thresholds of §2.2.
3. Should the stand-in move to an API 31 (Android 12) TV image to match the Elisa box, keeping API 30
   for comparison with the old numbers?
4. Per-press main-thread CPU of beta 23 on Home and the film wall was never measured; measure it at
   M0 with the rebuild's trace tests to anchor the 8 ms targets.
5. Beta 23's database size at owner scale was never recorded; measure it on the stand-in to check the
   250 MB estimate.
6. The total PSS ceiling (§3.3) after the first M0 measurement.
7. The import times on the box (spec 10's proposal) and the Shield figures proposed here.
8. Does the half-size hero decode in the low tier look acceptable on the box's 1080p output?
9. Are the hosted runners' timings stable enough for a 15 % nightly regression gate
   ([06](06-quality-testing-release.md) §10.4)?
10. A manual "Reduce motion" switch (design/01 question 4).

## 11. Reference: current code map

- Private harness (`G:\SportMate\.local\sohva-sport-user-reports\.local\slowbox`, read-only):
  `make_fixture.py` (fixture), `serve_fixture.py` (loopback server), `journey.sh` (scripted pass under
  Perfetto or simpleperf), `compare.sh` (reseed and compare builds and compile modes), `analyze.py`
  (CPU per phase), `overdraw.sh`/`overdraw_count.py` (overdraw screenshots), `smoke.sh` (shrunk-build
  crash pass).
- `app/src/androidTestLab/java/com/streammate/tv/lab/LabBenchmarkLibrarySeed.kt` — owner-scale seed through the app's importers.
- `app/src/androidTest/java/com/streammate/tv/feature/guide/GuideNavigationTraceTest.kt` — scripted guide presses with trace markers.
- `app/src/androidTest/java/com/streammate/tv/feature/today/SportsMatchingMemoryTest.kt` — heap sampler, heartbeat, stress fixture.
- `app/src/androidTest/java/com/streammate/tv/app/GuideChannelReadBenchmarkTest.kt`, `GuideImportBenchmarkTest.kt` — device evidence for the roster read and the guide import.
- `app/src/androidTest/java/com/streammate/tv/feature/catalogue/v2/CatalogueBrowseQueryBenchmarkTest.kt`, `feature/settings/LibraryManagerBenchmarkTest.kt` — wall and channel-manager timings.
- `iptv/src/test/java/com/streammate/tv/iptv/xmltv/XmlTvParserThroughputTest.kt` — parser throughput.
- `core/src/test/java/com/streammate/tv/core/database/CatalogueHomeQueryPlanTest.kt`, `GuideChannelsQueryPlanTest.kt` — plan tests.
- `app/src/main/java/com/streammate/tv/app/StreamMateApplication.kt` — image loader bounds.
- `iptv/src/main/java/com/streammate/tv/iptv/repository/GuideRepository.kt` — paged roster, kept rows, large-read log line.
- `iptv/src/main/java/com/streammate/tv/iptv/repository/OrganizationRepository.kt` — the background-priority dispatcher of the identity pass.
- `app/src/main/baseline-prof.txt`, `scripts/Test-ReleaseMethodSizes.ps1` — profile and AOT gate.
