# Sohva TV — product overview

> The product the rebuild must reproduce: Sohva TV `0.1.0-beta.23` (Android build 57, public
> commit `efab52a`, released 23 September 2026), rebuilt from an empty repository with the same
> features, an almost identical look, and a much lighter footprint.

## 1. What Sohva TV is

Sohva TV ("sohva" is Finnish for sofa) is a free, open-source (GPL-3.0-only) media player for
Android TV and Google TV, built for the remote control. It brings the viewer's own IPTV sources —
M3U playlists with XMLTV guides and Xtream Codes accounts — together with a full programme guide,
the provider's movies and series as poster libraries, Stremio-compatible addons (Discover),
optional Trakt sync, and Sohva Sport live scores that are paired with the viewer's own channels.
Everything works with a D-pad and nothing else.

The app supplies no content, playlists, addons, accounts or developer API keys, and it has no
server of its own. Credentials, guide data, matching, playback and caches all live on the TV,
encrypted where they are secret. Optional services (TMDB, TVmaze, API-Sports, Trakt, addons,
GitHub for updates) are called directly from the device with the viewer's own keys or accounts.

## 2. Who it is for

- **The household on the sofa.** Several viewers share one TV, each with a profile of their own;
  children get restricted profiles behind a parental PIN. Finnish and English are complete;
  Spanish, Portuguese, German, Swedish and Italian are drafts.
- **Viewers with big provider libraries.** Real providers deliver tens of thousands of channels,
  hundreds of thousands of programmes and up to about 200,000 films. The app must stay usable at
  that scale.
- **Viewers on cheap boxes.** Operator boxes and TV sticks with slow in-order CPUs, weak GPUs and
  1–2 GB of RAM are common. The current app was tuned on an Nvidia Shield and is slow on them; the
  rebuild must run well there (see §5).
- **Beta testers** who install signed APKs from GitHub releases and update in place through the
  app's own updater.

## 3. Product principles

1. **Remote first.** Every action is reachable with the D-pad, OK, Back and Menu. Focus is always
   visible, never lost, and returns where the viewer left it. Number keys dial channels; channel
   keys zap; long presses open actions.
2. **Bring your own sources.** No bundled content, keys or accounts. The app explains failures in
   plain language instead of codes.
3. **Private by construction.** Secrets are encrypted with the Android Keystore; nothing goes to a
   first-party server; no analytics, no crash upload; diagnostics are saved locally with
   addresses and keys removed; app data is excluded from Android cloud backup.
4. **Lightweight on every box.** Footprint is a requirement, not an optimisation pass: bounded
   memory, paged data, nothing but UI work on the main thread, cheap drawing, small APK
   ([plan/07-performance.md](07-performance.md)).
5. **Scale without surprises.** Paging in key order, streaming parsers, atomic import snapshots;
   the app is never killed for memory by a large library.
6. **The same look.** Dark, calm, high-contrast TV UI with a cyan focus accent (in the Original
   theme), a fixed hero that describes the focused item, and seven colour themes
   ([design/01-design-system.md](../design/01-design-system.md)).
7. **Upgrades in place.** Existing testers update without losing data: same application ID, same
   signing key, same update feed contract.

## 4. Scope — the feature areas

| Area | What it covers | Spec |
|---|---|---|
| App shell | Launch, navigation rail, destinations, Back, focus rules | [specs/01](../specs/01-app-shell-navigation.md) |
| Home | Fixed hero, Continue watching, Watch next, Recommended, Today's sport, recent channels | [specs/02](../specs/02-home.md) |
| Search | One search across channels, programmes, films, series, episodes and sport | [specs/03](../specs/03-search.md) |
| Profiles and parental controls | Profiles, Who is watching, restrictions, PIN | [specs/04](../specs/04-profiles-parental.md) |
| Sources and import | M3U/XMLTV and Xtream sources, testing, import pipeline, refresh | [specs/10](../specs/10-sources-and-import.md) |
| Phone setup | Local web page on the TV, QR codes, typing on a phone | [specs/11](../specs/11-phone-setup.md) |
| Live TV guide | Guide grid, hero, windows, grouping, dialling, find programme | [specs/20](../specs/20-live-tv-guide.md) |
| Channel management | Favourites, hide, reorder, custom groups, logos, numbers | [specs/21](../specs/21-channel-management.md) |
| Catch-up and reminders | Provider catch-up, programme reminders | [specs/22](../specs/22-catchup-and-reminders.md) |
| Player | Live, catch-up and VOD playback, overlays, tracks, subtitles, reconnect, PiP | [specs/30](../specs/30-player.md) |
| Remote button mapping | What each key does during playback | [specs/31](../specs/31-remote-button-mapping.md) |
| Movies and series | Poster walls, details, seasons, episodes, watched state, resume | [specs/40](../specs/40-movies-and-series.md) |
| Metadata | TMDB and TVmaze enrichment, matching, fix a wrong match | [specs/41](../specs/41-metadata-enrichment.md) |
| Library organisation | Groups shown/hidden/ordered, custom groups, per room | [specs/42](../specs/42-library-organization.md) |
| Discover | Stremio-compatible addons, catalogs, title pages, Library, subtitles | [specs/50](../specs/50-discover-addons.md) |
| Trakt | Device sign-in, scrobbling, progress and watched overlay, Home rows | [specs/51](../specs/51-trakt.md) |
| Sohva Sport | API-Sports scores for 12 sports, match cards, stream pairing, ticker | [specs/60](../specs/60-sohva-sport.md) |
| Settings | Every option, defaults and storage | [specs/70](../specs/70-settings.md) |
| Backup | Encrypted `.smbak` export and restore | [specs/71](../specs/71-backup-restore.md) |
| Updates, About, diagnostics | In-app updater, release notes, legal, diagnostics file | [specs/72](../specs/72-updates-about-diagnostics.md) |
| Security and privacy | Keystore encryption, redaction, network policy, privacy commitments | [specs/73](../specs/73-security-privacy.md) |
| Localisation | Seven languages, metadata languages, time zone, formats | [specs/74](../specs/74-localization.md) |

The complete, numbered list of capabilities is [01-feature-inventory.md](01-feature-inventory.md).

## 5. Platform and targets

| Item | Current app (beta 23) | Rebuild |
|---|---|---|
| Platform | Android TV and Google TV, leanback launcher, D-pad only | Same |
| SDK levels | minSdk 23, targetSdk 36, compileSdk 36 | Same unless the owner raises minSdk (see §8) |
| UI toolkit | Jetpack Compose with `androidx.tv:tv-material` | Same family (see [plan/05](05-tech-stack-and-build.md)) |
| Playback | Media3 ExoPlayer (HLS, DASH, progressive/TS) on OkHttp | Same |
| APK | 7.8 MB, R8 on since beta 23 | ≤ 10 MB |
| Reference low-end device | Elisa Viihde box (ZTE B866V2F01): Amlogic S905Y4, 4× Cortex-A35 @ 2 GHz, Mali-G31 MP2, 2 GB, Android 12 | Must run well |
| Other low-end class | Xiaomi TV stick class, 1–2 GB, Android 9–11 | Must run well |
| High end | Nvidia Shield TV, Android 11 | Must run very well |
| Library scale | 56,164 channels in 800 groups, 165,600 programmes, 30,000–200,000 films, 1,500+ series (owner-scale fixture) | Same scale on the low-end box |

## 6. Identity that must not change

- **Product name:** Sohva TV. Launcher label, banner and mark as in [assets/](../assets/README.md).
- **Application ID:** `com.streammate.tv` (legacy name from StreamMate, kept so existing installs
  update in place and keep their data). Demo build `com.streammate.tv.demo`, Lab build
  `com.streammate.tv.lab`, debug `com.streammate.tv.debug`.
- **Signing key:** the permanent release identity kept since StreamMate (public certificate
  SHA-256 `985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6`). Never regenerate
  it; a new key would stop every existing install from updating.
- **Version codes:** strictly increasing; beta 23 is 57, so the rebuild's first build must be 58 or
  higher.
- **Update feed:** public GitHub releases of `Macstered/Sohva-TV`. Installed betas read the
  release body line "Android build **N**", download only releases that carry `SHA256SUMS.txt`, and
  show the release's "Changed since" notes; since beta 23 they also install the `.api31.dm` /
  `.api28.dm` profiles. Every future release must keep this contract
  ([specs/72](../specs/72-updates-about-diagnostics.md)).
- **Public contact:** `hello@luontra.fi` (support and privacy), GitHub Sponsors for donations.
- **Licence:** GPL-3.0-only for original source; third-party notices kept.

## 7. Non-goals

The current app deliberately does not do these, and the rebuild does not add them:

- recording, scheduled recording, downloads, local timeshift, multiview;
- DRM circumvention, torrent, NZB/archive engines, arbitrary addon scripts;
- bundled channels, playlists, addons, accounts or developer-owned API keys;
- a first-party backend, analytics, advertising, automatic crash upload;
- Stremio/Nuvio account sync or history import (Discover copies addon configurations only);
- Stalker portal sources — parked: an unfinished, uncommitted Stalker preview exists only in the
  old `G:\SportMate` main checkout and never shipped. Treat it as a future feature, not part of
  the rebuild's parity target.

Two current limits are also kept as limits: catch-up needs provider support, and the encrypted
backup excludes Discover data (addons, Library, history, catalog order).

## 8. Decisions for the owner before building

The complete list, with a default for each, is [09-owner-decisions.md](09-owner-decisions.md). The
four below frame the whole project.

1. **Existing users' data.** Keep the database compatible and migrate in place, import once from
   the old database, import only through `.smbak`, or start fresh. Options and costs:
   [plan/04-data-model.md](04-data-model.md). The recommendation there is the default unless the
   owner chooses otherwise.
2. **minSdk.** Keep 23 (widest device reach, costs little) or raise it (simpler code paths).
   Default: keep 23.
3. **Feature order.** The roadmap ([02-roadmap.md](02-roadmap.md)) ships Live TV first, then VOD,
   then the optional features. The owner may reorder the optional milestones.
4. **Release channel for the rebuild.** Default: continue the same beta line on the same
   repository and update feed, starting above build 57, after the rebuild reaches parity.

## 9. Glossary

| Term | Meaning |
|---|---|
| Source / playlist | One configured IPTV provider entry: an M3U URL (plus optional XMLTV URL) or an Xtream Codes account |
| M3U | Playlist format listing channels (and sometimes films/series) with attributes such as `tvg-id`, `group-title`, `tvg-logo` |
| XMLTV / EPG | Programme guide data (channels and timed programmes) |
| Xtream Codes | Provider API (`player_api.php`) for live, VOD and series catalogues and stream URLs |
| Catch-up | Watching a past or already-started programme from the provider's archive |
| VOD | Provider films and series (the Movies and Series libraries) |
| Room | One of the three organisable libraries: LIVE, MOVIES, SERIES |
| Organisation rules | Per-source rules that show, hide, order and group content in a room |
| Snapshot | One complete import of a source's playlist or guide, switched in atomically |
| Discover | The Stremio-compatible addon browser, independent of IPTV |
| Addon | A Stremio-protocol web service providing catalogs, metadata, streams or subtitles |
| Library (Discover) | Titles a profile saved from Discover |
| Trakt / scrobble | The Trakt.tv service / reporting playback start, pause and stop to it |
| Sohva Sport | The sports section: API-Sports fixtures, scores and events, paired with the viewer's channels |
| Stream pairing | Matching a sports event to channels that are likely to show it, from guide programmes and channel names |
| Profile / restricted profile | A viewer; a restricted one sees only allowed groups and never Discover or Trakt |
| Hero | The fixed top area on Home and the guide that describes the focused item |
| Rail | The left column: Home's navigation icons, the guide's channel column, Settings' section list |
| Wall | A grid of posters (Movies, Series, Discover catalogs) |
| Focus line | The fixed vertical position on Home where the focused row sits while rows scroll under it |
| Low-end box | The reference class in §5 |
