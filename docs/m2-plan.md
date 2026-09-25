# M2 plan: live TV guide and live playback

Milestone M2 of [plan/02-roadmap.md](rebuild/plan/02-roadmap.md): a viewer can browse the guide
and watch live channels as in beta 23. Specs: [20](rebuild/specs/20-live-tv-guide.md) and the
live part of [30](rebuild/specs/30-player.md), with the player's key dispatch from
[31](rebuild/specs/31-remote-button-mapping.md) at its defaults. Inventory: GUIDE-*, the live
PLAY-* items. Branch `m2-live`. Owner's go: 24 September 2026 ("go ahead and start", to run
overnight without pushing).

## Exit criteria (plan/02)

1. The guide opens on the first group of a 56,000-channel source, and a held D-pad press meets the
   key-press budget on the stand-in (GUIDE-NFR-01: ≤ 11 ms between rows, ≤ 7 ms along a row).
2. No focus jumps when programmes arrive (gated-data device tests).
3. Overlays cost nothing while hidden (no recomposition of the player tree over 60 s).
4. Live playback survives a dropped stream with the right message (reconnect banner, counter,
   Reconnect).
5. The guide and player UI tests from the specs are green.

## What waits for a later milestone

The guide and player are built whole, but some buttons need data or screens that later
milestones own. Until then they are **not shown** (never a dead button), and the hook exists:

| Item | Needs | Milestone |
|---|---|---|
| Watch from start / Watch recording, OK on a past block playing catch-up (GUIDE-21, -27, -29; PLAY-02, PLAY-09 in catch-up) | catch-up URLs, spec 22 | M3 |
| Remind me / Reminder set (GUIDE-21, -29, -31) | reminders, spec 22 | M3 |
| Custom channel lists on the rail, Options › Edit (channels) (GUIDE-12, -36) | spec 21 | M3 |
| Remote mapping settings grid (spec 31 §4.7–4.9) | spec 31 Settings part | M3 (M2 ships the resolver and the defaults) |
| Options › Sort and Edit (groups), the Library manager, manual group order, rules (GUIDE-06, -07, -08, -13, -35) | spec 42 | M4 |
| Hero metadata, "Source: TMDB" (GUIDE-22, part of -20, -21) | spec 41 | M4 |
| Restricted profiles, locked channels, PIN (GUIDE-49, PLAY-40) | spec 04 | M6 |
| Settings for time zone, channel numbers, playback, subtitles, frame rate, corner (the preferences are read with their defaults now) | spec 70 | M7 |
| VOD playback, progress, completion (PLAY-03, -19, -34, -35, -46) | spec 40 | M4 |
| Score ticker (PLAY-31) | spec 60 | M8 |

Options › Sort / Edit (groups) and Edit (channels), and Settings, are kept on the sheet (beta 23's
look); the first three open the "arrives in a later version" placeholder the shell already has.

## Design

- **Data** (plan/04 §15): the rail reads `content_group` (room `LIVE`, `shown = 1`) with the
  count; rows are keyset pages of `channel` by `(group_id, display_rank)` or
  `(source_id, visible, display_rank)`, ≤ 200 rows, never joined to programmes; the window read
  is plan/04 §15.5's bounded range for ≤ 80 `epg_id`s; dialling and zapping are indexed look-ups
  (`(source_id, number)`, a ±50 keyset window by `display_rank`). Schema v3 adds
  `favourite_channel` and `recent_channel` (profile `default` until M6). Every hot query gets a
  query-plan test.
- **Guide** (`:feature:live`): a `GuideModel` (ViewModel) holds the source, rail, list, window and
  a paged row window of at most 3 × 200 rows; a `ProgrammeCache` of 240 schedules with identity
  retention; the rows are the M0 spike's canvas rows (one focusable, two draw nodes, the selected
  column read only in draw and key handling).
- **Player engine** (`:core:player`): Media3 in a `MediaSessionService` with its own application
  looper; a resolving data source that swaps the placeholder `sohva://channel/<id>` for the real
  address and adds the provider's headers on every request; per-source connection leases;
  buffer profiles with byte caps; reconnect policy; plain-language error mapping.
- **Player screen** (`:feature:player`): a plain state holder (modes, overlay flags, the remote
  resolver and key dispatch) and one small composable per overlay; nothing composed while hidden.
- **Test streams**: no media tool is on the build machine, so the instrumentation tests make
  their own H.264/AAC clips with the emulator's `MediaCodec` and `MediaMuxer` and serve them
  from an in-test HTTP server (progressive MP4 and HLS over the same clip).

## Order of work

1. `:core:model`: time window, schedule dedupe, genre accent, dial resolution, time labels,
   selection rules, first group and source choice, remote resolver and defaults, reconnect
   policy, skip ladder, MIME from address, display-mode pick, info-line formatting. JVM tests.
2. `:core:data`: schema v3 (favourites, recents) with migration test; guide and player DAOs;
   preferences (`last_guide_source_id`, `show_channel_numbers`, `time_zone`, playback keys);
   query-plan tests.
3. `:feature:live`: the guide screen, rail, hero, options sheet, actions dialog, dial,
   loading/empty states; device tests with gated data.
4. `:core:player`: service, resolving data source, leases, profiles, errors; JVM tests.
5. `:feature:player`: live screen and overlays, key dispatch, zap, dial, lists, pickers, quick
   actions, info line, buffering, banner, corner, external player.
6. `:app`: routes (Guide, Player), Home's "Live TV", the zap orchestration and recents.
7. Emulator playback tests with generated clips; the guide trace test and the playback
   recomposition test on the stand-in; performance log.
8. Exit check.

## Exit check (25 September 2026)

Exit criteria:

1. Guide on the first group of a 56,000-channel source within the key-press budget — **met**
   (docs/performance-log.md, benchmarkRelease with R8 on the API 30 stand-in, owner-scale fixture:
   main-thread CPU 4.78 ms per press between rows (budget 11), 3.14 ms along a row (budget 7);
   beta 23 took 21.0 ms. Java heap 45 MB at owner scale; guide open 669 ms, All channels 418 ms).
2. No focus jumps when programmes arrive — **met** (`GuideTimingTest` with gated reads, held
   paging proven to fail when the old window's programmes are kept; the owner-scale test found
   and now covers a dial to an unread page).
3. Hidden overlays cost nothing — **met** (0 UI frames over 15 s of playback with every overlay
   hidden; a mutant that kept the info line composed drew 5 frames and failed).
4. A dropped stream shows the reconnect banner with its counter, then Reconnect recovers — **met**
   (`PlayerLiveTest`).
5. The guide and player UI tests are green — **met** (the whole API 34 suite, 46 tests, passed on
   25 September after the focus fix; `GuideOwnerScaleTest` runs only with `ownerScale=true`).

Release check (R8 release build on the API 30 stand-in, 25 September): an M3U source added in
Settings imported and filled the guide by itself; OK played the channel through the playback
service (H.264 decoded into the video surface, the media session ran to the clip's end); no R8,
missing-class or Media3 errors in the log. The emulator's screenshot does not show the video
layer (SurfaceFlinger shows it visible and fed); this is the emulator's compositor, not the app.
The check found one M1 bug, fixed with a test: OK on "Save securely" disabled the focused button
and focus fell to the Settings rail (decision "A control disabled under the viewer's focus").

Inventory (ticked in `rebuild/plan/01-feature-inventory.md`, 25 September 2026):

- Done: GUIDE-01…05, 09…11, 14, 16…19, 23…26, 28, 30, 32…34, 37…45, 47, 48, 50…52;
  PLAY-04…08, 11…15, 17, 18, 20…25, 30, 32, 33, 36…39, 41…43, 45; SHELL-13…15; SRC-11
  (the limit refused at playback), SRC-38 (the offset applied on screen).
- Built in M2, finished by a later milestone (see "What waits" above): GUIDE-27 and -29 (catch-up
  and reminder actions, M3), GUIDE-20…22 (metadata, M4), GUIDE-15, -46, PLAY-26…28 (settings
  screens, M7; the preferences are read with their defaults now), PLAY-01 and SHELL-11 (played
  from the guide, a dialled number and the last-channel start; Home cards, Search and Sohva Sport
  open it when those screens exist), PLAY-16 (skip ladder built; used by catch-up and VOD).
- Not in M2: PLAY-29 (corner, deferred to M7 by decision), PLAY-44 (the demo build's still picture; the roadmap names no milestone for it yet), and the
  items in the "What waits" table.
