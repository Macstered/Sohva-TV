# Owner decisions, device checks, and suspected beta 23 bugs

> Collected on 24 September 2026 from the open-question sections of every spec, plan and design
> file (about 200 questions in total). Each decision has a **default** so building can start
> without waiting: the rebuild follows the default until the owner says otherwise, and the answer
> goes into `docs/decisions.md` of the new repository. The full questions, with context, are at the
> end of section 10 of each spec (plans and design files: their "Open questions" section).

## A. Decide before M0/M1 (they shape the foundations)

| # | Decision | Options | Default | Where |
|---|---|---|---|---|
| A1 | Existing testers' data | A keep schema and migrate · **B one-time import from the old database on first start** · C only via `.smbak` · D fresh start | **B**, with `.smbak` restore also supported | [04 §17](04-data-model.md) |
| A2 | minSdk and SQLite floor | 23 (today) · 26 (drops desugaring and old-device traps) · bundle SQLite | **23**, platform SQLite; revisit only if a feature needs more | [00 §8](00-product-overview.md), [04](04-data-model.md), [05](05-tech-stack-and-build.md) |
| A3 | First version of the rebuild | code 58 continuing `0.1.0-beta.24` · code 100 and `0.2.0-beta.1` (room for beta 23 hotfixes) | **code 100, `0.2.0-beta.1`** | [06](06-quality-testing-release.md) |
| A4 | Search matching | FTS token-prefix (fast; "at" no longer finds "Match") · substring kept with bundled SQLite (+1–2 MB) | **FTS prefix**, accents not folded | [03](../specs/03-search.md), [04](04-data-model.md) |
| A5 | Guide history kept | 12 h for all (today) · catch-up channels to min(catch-up days, 24.5 h), others 3 h | **the second** (smaller database, faster imports) | [10](../specs/10-sources-and-import.md), [20](../specs/20-live-tv-guide.md), [22](../specs/22-catchup-and-reminders.md) |
| A6 | Backup contents and format | keep format 2 (beta 23 can read rebuild backups) · format 3 adding auto frame rate, language, image cache, TMDB/API-Sports keys, Discover data | **format 3**, still reading 1–2; Discover data included; keys included (they are encrypted by the backup password) | [71](../specs/71-backup-restore.md), [70](../specs/70-settings.md), [50](../specs/50-discover-addons.md) |
| A7 | Restricted profiles | today's gaps (Continue watching, History, Similar, counts, managers not filtered; managers open without PIN) · filter everything by allowed groups and gate managers with the PIN | **filter and gate** | [04](../specs/04-profiles-parental.md), [40](../specs/40-movies-and-series.md), [42](../specs/42-library-organization.md), [02](../specs/02-home.md) |
| A8 | Reduce motion / low-RAM mode | automatic only · also a switch in Settings › General | **automatic, plus a switch** | [design/01 §16](../design/01-design-system.md), [07](07-performance.md) |
| A9 | Network policy | today (four HTTPS-only service domains; HTTP addons impossible although the privacy text implies they can be accepted) · HTTPS-only for every first-party service and addons, texts corrected | **HTTPS-only**, privacy text corrected | [73](../specs/73-security-privacy.md), [50](../specs/50-discover-addons.md) |
| A10 | Time format | fixed `HH.mm` in guide, Home and Sport (today) · the locale's separator and the TV's 12/24-hour setting everywhere | **locale everywhere** (Finnish still shows `20.00`) | [74](../specs/74-localization.md) |

## B. Behaviour choices that can wait for their milestone

Defaults keep beta 23's behaviour unless marked **(change)**.

**M1 Sources**
- Confirmation before Remove source and Clear all guide data — **(change)** confirm both; narrow
  "Clear all guide data" to programmes or reword it ([10](../specs/10-sources-and-import.md), [70 Q-02](../specs/70-settings.md)).
- xz-compressed guides — gzip only.
- A playlist's own `url-tvg` fills an empty XMLTV field; `#EXTGRP` supplies a missing group — **(change)** yes.
- Start a sync when a source is edited and saved — **(change)** yes.
- Size caps on downloads and descriptions — **(change)** yes (values in spec 10 §9).
- Xtream in-place (hash-diffed) updates — in M1.
- Phone page token in the URL fragment, 128-bit — **(change)** yes ([11 Q-01](../specs/11-phone-setup.md)).

**M2 Guide and player**
- The two "Edit" buttons in guide Options — relabel the second "Edit channels" **(change)**.
- Back closes the rail or search before leaving the guide — **(change)** yes.
- Channel up/down in the guide pages rows — **(change)** yes.
- OK on an airing programme of a catch-up channel restarts it — keep (the channel column plays live).
- Audio focus for the IPTV player — **(change)** request it.
- Plain-language playback error sentences — owner wording needed ([30 Q-01](../specs/30-player.md)).
- Software-decode watchdog threshold and message — owner wording needed.
- Holding Left/Right in catch-up and VOD — **(change)** repeating skip, as the help text says ([30 Q-14](../specs/30-player.md), [31 Q-07](../specs/31-remote-button-mapping.md)).
- Holding Up zaps once per hold — **(change)** ([31 Q-02](../specs/31-remote-button-mapping.md)).
- CH+ direction — keep (CH+ = previous in the list).

**M3 Channels, catch-up, reminders**
- Exact alarms on Android 12+: `SCHEDULE_EXACT_ALARM` with an inexact fallback ([22 Q-1](../specs/22-catchup-and-reminders.md)).
- M3U catch-up times in the app's time zone setting, as its help text says — **(change)**, check
  against real provider templates first.
- Archive title and "not available from the archive" text — owner wording in seven languages.
- A channel's customisation follows it when the provider renames it (match by `tvg-id`) — **(change)** yes.

**M4 Movies, series, metadata, organisation**
- Unwatched filter on the wall — not in beta 23; stays out.
- Series page opens on the season of the newest watched episode — **(change)** yes.
- Episodes refreshed on open when older than 24 h — **(change)** yes.
- The guide shows the TMDB rating chip (beta 23 never does) — **(change)** yes.
- Re-queue "no match" titles after 30 days — **(change)** yes.
- Small foreground enrichment of the visible wall page — **(change)** yes, paused while a key is held.
- Library manager focus look — **(change)** the standard focus fill, not the orange border.
- "These are different films" to split a wrong merge — later, after parity.

**M5–M6 Home, profiles**
- Home row editor and Trakt list rows (planned, not in beta 23) — after parity.
- PIN attempt limit (for example 30 s pause after 5 wrong tries) — **(change)** yes.
- PIN recovery path — restore a backup from the default profile.
- Rename and colour choice for profiles — **(change)** yes (small).
- Removing a profile asks for confirmation and deletes its Discover data — **(change)** yes.
- "Remove / change PIN" split so changing the PIN keeps the locks — **(change)** yes.
- Profile picker with 5–6 profiles scrolls or wraps (today 5–6 tiles do not fit at 100 %) — **(change)**.

**M7–M10 Settings, updates, Sport, Discover, Trakt**
- Every Settings section shows its own status line — **(change)** (already in spec 70).
- Persistent redacted diagnostics ring and exit reasons — **(change)** yes.
- Release notes in the interface language — **(change)** yes (bodies already have English/Suomi).
- Legal screen shows `theme-licenses.txt` — **(change)** yes.
- Sport favourites: restore an Add/Remove favourite control (removed on 23 August) or drop the tab —
  **(change)** yes, restore
- Live sport refresh every 5 min from the network, stop at the daily quota — **change** yes, 5 minutes, stop at quota
- Sports channels now lists only live games and games starting within 15 min — **(change)** yes.
- Subtitle-request watch tracking in Discover — keep; make it opt-in later.
- Landscape cards for landscape catalogs — later.
- Trakt Disconnect also revokes the token — **(change)** yes.
- VOD films paused only on Trakt seek by fraction once the duration is known — **(change)** yes.

## C. Facts to check on a device (no decision, just measurement)

Only on the emulator unless the owner explicitly allows a device.

1. Do HLS/DASH live channels play in beta 23's IPTV player? (See D1.)
2. The Elisa box's `memoryClass`, `isLowRamDevice`, total RAM, ABIs (32/64-bit), output refresh rate,
   and the main-thread speed factor against the emulator (assumed 3×).
3. Beta 23's per-press main-thread cost on Home and the film wall, and its database size at owner
   scale (never measured).
4. Do media keys reach the IPTV player through the media session?
5. Platform dialog dim and default width on Android TV; whether gradients are dithered.
6. PBKDF2-SHA256 availability on Android 6–7 (backup), and a beta 23 `.smbak` with a non-ASCII
   password as a compatibility fixture (owner makes it; the file and password stay private).
7. Real API-Sports day listings for NFL, MMA, Formula 1 and NBA (owner's key) to test the parsers.
8. Where first focus lands on the PIN screens.

## D. Suspected bugs in beta 23 found while writing the kit

Found by reading the code; **none is confirmed on a device**. They may be worth checking in the
current app now, independent of the rebuild. The rebuild's specs already require the fixed
behaviour.

| # | Area | Suspected bug | Source |
|---|---|---|---|
| D1 | Player | The playback resolver rewrites only its own placeholder address and throws for every other address, so HLS/DASH segment requests (real `http(s)` URLs) would fail: `.m3u8`/`.mpd` live channels may never play in the IPTV player | [30 L-23, Q-08](../specs/30-player.md) |
| D2 | Reminders | The manifest has no exact-alarm permission, which `setAlarmClock` needs on Android 12+ (the Elisa box) | [22](../specs/22-catchup-and-reminders.md) |
| D3 | Reminders | A reminder due immediately (set less than a minute before the start, or found due at boot or start-up) fires only if another reminder is still pending | [22](../specs/22-catchup-and-reminders.md) |
| D4 | Player | Refresh-rate matching gets a frame rate only while the playback info line is open | [30](../specs/30-player.md) |
| D5 | Player | The catch-up title is the Finnish "Arkisto ·" in every language; the episode fallback "Jakso N" likewise | [30](../specs/30-player.md), [40](../specs/40-movies-and-series.md) |
| D6 | Player | The live/VOD player never requests audio focus | [30](../specs/30-player.md) |
| D7 | Remote | The press/hold resolver is recreated on each channel change, so holding Up may zap several times or end by opening the channel list | [31](../specs/31-remote-button-mapping.md) |
| D8 | Sources | "720p" in a channel name also yields a "720 FPS" tag | [10](../specs/10-sources-and-import.md) |
| D9 | Sources | Test connection saves the source, so the new source's first sync never starts | [10](../specs/10-sources-and-import.md) |
| D10 | Sources | Xtream imports have no guard against an empty answer and send the HTTP library's default user agent | [10](../specs/10-sources-and-import.md) |
| D11 | Sources | A damaged source store looks like "no sources" | [10](../specs/10-sources-and-import.md) |
| D12 | Guide | The guide pages back 24 h but only 12 h of programmes are stored; programme cell times use the system zone, not the chosen one | [10](../specs/10-sources-and-import.md), [design/screens/guide.md §12](../design/screens/guide.md) |
| D13 | Metadata | The guide's TMDB rating chip never appears; "Save key" wipes all library enrichment; a chosen match is lost on a language change; a match chosen from TVmaze is fetched from TMDB | [41](../specs/41-metadata-enrichment.md) |
| D14 | Organisation | Two groups of your own with the same name overwrite each other | [42](../specs/42-library-organization.md) |
| D15 | Movies | Similar and Versions run full-table `LIKE` scans; a Keystore decrypt runs on the main thread each time a details page opens; two strings are hard-coded Finnish | [40](../specs/40-movies-and-series.md) |
| D16 | Sport | MMA, Formula 1 and NBA are never fetched (only sports with a followed competition are); favourites cannot be added since 23 August; live scores come from the network only every 20 min | [60](../specs/60-sohva-sport.md) |
| D17 | Settings | Restoring a backup turns "Match the display to the picture" back on; many status messages are written but never shown outside Playlists | [70](../specs/70-settings.md), [design/screens/settings.md §9](../design/screens/settings.md) |
| D18 | Localisation | On Android 12 and older the phone page, reminder notifications and backup errors ignore the chosen language | [74](../specs/74-localization.md) |
| D19 | Channels | One Up/Down move in Channel management rewrites every channel's row; the channel manager reads every channel at once | [21](../specs/21-channel-management.md) |
| D20 | Trakt | A restricted profile connected earlier still syncs and scrobbles; queued scrobbles are dropped while re-authorisation is needed; signing in as another account inherits the old cache; the sync loop runs during playback | [51](../specs/51-trakt.md) |
