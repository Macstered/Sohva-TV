# Study: OwnTV — ideas for a fast TV app on slow boxes

> The owner asked (24 September 2026) to look at [OwnTV](https://github.com/ahXN00/OwnTV) for ideas
> on structure, **not to use it as such**. Read on 24 September 2026: OwnTV v5.0.2 (released
> 21 September 2026), the TV app repository and its shared core library
> [OwnTV_Core](https://github.com/ahXN00/OwnTV_Core). Both are GPL-3.0; nothing is copied into the
> rebuild — only ideas, restated in our own terms. Where an idea is adopted, the kit file that now
> requires it is named.

## 1. What OwnTV is

A native Android TV IPTV player in Kotlin and Compose for TV (tv-material 1.1.0), bring-your-own
M3U, Xtream and Stalker sources, EPG with catch-up, profiles, recording, multiview, downloads and
26 languages. Room + Paging 3 + FTS4, Koin, OkHttp 5, Coil 3, DataStore; playback through two
engines (libmpv and Media3). minSdk 26. It advertises itself as fast on low-end ARM boxes.

**Its footprint is not our model:** the release APK is **60.6 MB** (native mpv/FFmpeg libraries
for two ABIs, nine bundled font files of about 2 MB). Sohva TV beta 23 is 7.8 MB and the rebuild's
budget is 10 MB. We take OwnTV's runtime techniques, not its dependencies.

## 2. Ideas worth adopting

### Structure

1. **A UI-free core.** Database, parsers, sync, EPG, backup, settings storage and the playback
   engine live in a core library with no Compose in it; the TV UI sits on top. OwnTV splits this
   into separate repositories (shared with a phone app), which forces contributors to fetch the core
   from GitHub Packages with a token — we keep one repository and get the same separation with
   Gradle modules. → [plan/03-architecture.md](../plan/03-architecture.md) (core modules without UI).
2. **One dispatcher entry for imports.** Every place that imports a source (setup, Settings, the
   background worker) funnels through one `sync()` so follow-up work cannot be forgotten by a caller.
   → [specs/10](../specs/10-sources-and-import.md).
3. **Per-source-type syncers with the right update strategy.** Xtream items have stable ids, so
   they are upserted with a content hash and unchanged rows are not rewritten; M3U has no stable ids,
   so it is replaced. Fewer writes means shorter imports and less invalidation of observed queries.
4. **Lazy, prioritised background completion.** Series episodes are fetched when a series is opened,
   not during sync; a large catalogue's remaining pages are drained in the background with the
   category the viewer is looking at served first; expensive probes (such as measuring a provider's
   connection limit) wait for a moment when nothing is playing.

### Parsing and import

5. **Stream everything.** M3U is read line by line; Xtream `player_api` JSON is read with a
   streaming reader (`android.util.JsonReader`), never decoded as one object; XMLTV with a pull
   parser, gzip detected by magic bytes; a malformed element is skipped, not fatal. → specs/10.
6. **Overlap download, parse and write.** Parsing runs on one coroutine and database writes on
   another, fed through a rendezvous channel in chunked transactions, so the network, the parser and
   SQLite work at the same time; cancellable, with progress. → specs/10, plan/07.
7. **Fallback when a provider truncates a bulk list** (HTTP 512 on "all streams"): fetch per
   category instead.
8. **Download to a temporary file and rename only after a clean parse**, so a guide cut off by a
   network drop never replaces a good cached one; stale temporary files are cleaned only when older
   than the cache lifetime (another source may be writing one).

### EPG

9. **Store only the programmes of channels the viewer has.** Public XMLTV feeds carry thousands of
   channels not in the playlist; filter by the playlist's `tvg-id`s plus manual matches while
   parsing (skip a programme before reading its children). If no ids can be determined, store
   everything rather than an empty guide. → specs/10, specs/20.
10. **Keep past programmes only where catch-up can play them.** Other channels keep a short recent
    past. OwnTV measured its old 7-day history for every channel at 38.9 s of a 112 s sync on a
    7,083-channel lineup. Pass large keep-sets through a temporary table (SQLite's 999 bound
    parameter limit on older Android). → specs/10, specs/22.
11. **Indexes for bulk imports:** drop secondary indexes before a large bulk insert, recreate them
    once afterwards in the background; ANALYZE after sync, never on the launch path. → plan/04.

### UI and rendering

12. **Draw a guide row's programmes on one Canvas.** Each channel's programme strip is a single
    drawing: rounded rectangles, a cached text measurer, labels formatted once per data change (not
    per frame), cells culled when off screen, the now-line drawn per row so it reads as one line.
    This is the structure our M0 guide spike tests. → [plan/02-roadmap.md](../plan/02-roadmap.md) M0,
    specs/20.
13. **Row-level focus in the guide.** OwnTV makes the row the focus target (Right selects a row, OK
    steps into its programmes). Sohva keeps its current feel — the viewer moves across programme
    blocks — but implements it the same cheap way: one focusable per row holding a "selected
    programme" index that the row's canvas draws, instead of a focusable node per programme cell.
14. **Bounded windows around the current channel.** Channel up/down and number tuning query the
    neighbours of the tuned channel (a keyset window of ±N ordered by `(sortOrder, id)`, with the id
    as tie-breaker), never the whole channel list. OwnTV tests exactly the tie cases. → specs/30.
15. **Paging with a bounded maximum size** for every long list, and FTS4 tables for search.
    → specs/03, specs/40, plan/04.
16. **Expensive looks are opt-in and tiered by capability.** OwnTV's "Glass" material uses one
    shared, downscaled, blurred copy of the wallpaper and only on Android 12+; otherwise plain
    translucency; off by default. For Sohva: no blur at all; the default look is solid and cheap.
17. **Shadows only where they do not trail.** Focus glow shadows are skipped on small cards in
    scrolling rows (they leave trails and cost fill rate), kept for large cards, and dropped entirely
    when animations are off. → design/01.
18. **A global "animations off" switch** that collapses tweens — plus a test that fails the build if
    any infinite animation could be given a zero duration (OwnTV shipped a crash that way).
    → design/01, plan/06.
19. **Settings composes only the selected section** (OwnTV calls it "a spine and a sheet") and
    restores focus to the exact row after a sub-screen. → specs/70.

### Start-up and profiles

20. **Application start does almost nothing.** Small fire-and-forget IO on an app scope, the player
    engine a lazy singleton, ANALYZE and migrations off the launch path. → plan/03, plan/07.
21. **Baseline and startup profiles recorded by a D-pad-only Macrobenchmark journey** on an Android
    TV emulator (API 33+) that already holds a synthetic catalogue served from the host
    (`http://10.0.2.2:<port>/`), visiting Home, every section's content (not just its empty scaffold)
    and a playback open-and-back, with `includeInStartupProfile = true`, merged into the main source
    set so it ships in the release APK. Their lesson: the first profile recorded the setup wizard
    because the emulator had no catalogue. Regenerate whenever the start-up path changes; a stale
    profile silently stops helping. → plan/05, plan/06, plan/07.
22. **Two HTTP clients:** the provider-facing client pinned to HTTP/1.1 (flaky IPTV panels), the
    image client on HTTP/2 so poster grids multiplex on one connection. → specs/10, plan/05.
23. **Player robustness:** every player command off the UI thread; backgrounding releases the
    stream at once; a watchdog stops software-decoding 4K/8K streams that would stall a weak box, with
    a clear message. → specs/30.

## 3. Ideas not to adopt

| OwnTV choice | Why not for Sohva |
|---|---|
| libmpv/FFmpeg as a second engine | Tens of MB of native code per ABI; the lightweight budget rules it out. Media3 only. |
| Bundled font families | About 2 MB; Sohva uses the system sans-serif. |
| Glass effect, wallpapers, ambient glow | Fill-rate cost on Mali-class GPUs; not part of Sohva's look. |
| Separate core repository via GitHub Packages | Contributor friction (token needed to build); Gradle modules give the same separation. |
| Recording, multiview, downloads, DNS-over-HTTPS | Outside Sohva's scope (see plan/00 non-goals). |
| A hosted TMDB gateway | Sohva has no first-party server; the viewer's own TMDB key is used directly. |

## 4. Where these ideas land in the kit

The adopted items are written into the plan and specs as requirements (look for "OwnTV study" in
[plan/07-performance.md](../plan/07-performance.md) and [plan/03-architecture.md](../plan/03-architecture.md)).
Features OwnTV has and Sohva does not (live preview promoted to full screen without reloading, a
settings search, EPG auto-matching with a review list, "set up from another device") are candidates
for after parity, not part of the rebuild's scope.
