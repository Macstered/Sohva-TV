# Trakt sync

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

Trakt is an optional, per-profile connection to the viewer's own Trakt account. The viewer
connects in **Settings › Accounts** with a device code shown on the TV (no password is typed on
the TV). While connected, movies and episodes played in the VOD player (Movies/Series) and the
Discover addon player are **scrobbled** to Trakt (start, pause, stop), addressed only by TMDB or
IMDb id — never by title — and never for Live TV or catch-up. In the other direction, Sohva
keeps a local cache of the account's paused positions and watched marks, draws progress bars and
watched ticks on library and Discover cards, lets a title paused on another device resume here,
and feeds Home two extra rows: **Watch next** (the next episode of recently watched shows) and
**Recommended for you** (Trakt's picks), with synopses from TMDB in the viewer's metadata
language. Trakt is an **overlay**: local VOD progress and Discover progress stay exactly as they
are, and Trakt is the shared ledger between devices. Restricted profiles are kept out of Trakt.

## 2. Feature checklist

Account
- TRAKT-01 Settings › Accounts section with a Trakt panel (unrestricted profiles only).
- TRAKT-02 Connect Trakt with a device code: QR of the activation address, the address and the code shown on the TV.
- TRAKT-03 Cancel sign-in with the button or Back; leaving the foreground cancels sign-in.
- TRAKT-04 One Trakt account per Sohva profile; the panel names the profile and shows "Connected as <username>".
- TRAKT-05 "Sign in again" when an account exists (replaces the saved sign-in).
- TRAKT-06 "Sign in again" state explained when Trakt no longer accepts the saved sign-in.
- TRAKT-07 Disconnect removes the sign-in and the cached Trakt data from this TV only.
- TRAKT-08 "Not configured" state when the build has no Trakt application credentials.
- TRAKT-09 Error messages for declined, expired, unusable code, rate limit and connection failure.
- TRAKT-10 Tokens refreshed automatically before they expire.
- TRAKT-11 Removing a Sohva profile disconnects its Trakt account.

Scrobbling
- TRAKT-12 Start / pause / stop scrobbles from the VOD player (movies and episodes).
- TRAKT-13 Start / pause / stop scrobbles from the Discover player (movies and episodes).
- TRAKT-14 Nothing is sent for Live TV, catch-up, or titles without a TMDB/IMDb id.
- TRAKT-15 A rebuffer is not a pause (2.5 s settle before a pause is sent).
- TRAKT-16 Scrobbles that cannot be delivered are kept and retried (latest report per title wins).
- TRAKT-17 A playback interrupted by a crash or kill is closed with a pause on the next start.

Read side
- TRAKT-18 Background sync of the account's paused positions and watched marks: at profile start, 3 s after each reported stop, every 15 minutes — only when Trakt reports new activity.
- TRAKT-19 Trakt progress bars and watched ticks on VOD library cards, series episode lists and Continue watching.
- TRAKT-20 Trakt progress bars and watched ticks on Discover movie posters and episode cards.
- TRAKT-21 Continue watching includes library titles paused on Trakt (e.g. on another device).
- TRAKT-22 Resume from a Trakt pause: Discover by fraction; VOD when the runtime is known.
- TRAKT-23 One Continue watching card per movie across Discover and VOD copies, bridged by Trakt ids.
- TRAKT-24 Home "Watch next" row: next unwatched episode of up to 8 recently watched shows.
- TRAKT-25 Home "Recommended for you" row: 10 movies and 10 shows, interleaved, refreshed twice a day.
- TRAKT-26 Home hero for Trakt cards: synopsis and backdrop from TMDB by id in the metadata language; Trakt's text as fallback.
- TRAKT-27 Opening a Trakt card: the library's own page when a copy is matched to the same TMDB record; otherwise a lookup over the Discover addons (IMDb, then TMDB); otherwise "not available".
- TRAKT-28 "Trakt history is waiting for its first sync" notice on Home right after connecting.
- TRAKT-29 Restricted profiles: no Accounts section, no Trakt rows, no notice.
- TRAKT-30 Demo (screenshot) build: a fictional connected account seeded offline, no network.

## 3. Entry points and navigation

- **Settings › Accounts** (`settings_section_accounts` "Accounts", rail icon Link), placed
  between Library (Metadata) and Sport in the settings rail ([Settings](70-settings.md)). The
  section exists only for an unrestricted active profile; if the profile becomes restricted
  while Accounts is selected, Settings falls back to the Sources section. The panel's primary
  button receives focus when the section opens.
- Back in the panel: while sign-in is in progress the **first Back cancels** the sign-in; the
  next Back is ordinary Settings navigation.
- **Home rows** Watch next and Recommended for you, and the first-sync notice
  ([Home](02-home.md)). A card opens:
  - the VOD movie details or series details page when the library has a copy matched to the
    same TMDB id ([Movies and series](40-movies-and-series.md)) — only if Home is still the
    current screen when the lookup finishes;
  - else the **Trakt title screen**, which looks the title up in the Discover addons and shows
    the Discover title page ([Discover](50-discover-addons.md)); Back returns to Home.
- Card overlays everywhere cards show progress (VOD walls, series pages, Continue watching,
  Discover). No other navigation.

## 4. Behaviour

Quoted English UI text comes from `strings_trakt.xml` (kit:
`reference/strings/app/values/strings_trakt.xml`, keys `trakt_*`, `home_watch_next`,
`home_recommended`, `trakt_title_loading`, `trakt_title_unavailable`), the app's `strings.xml`
(`home_trakt_first_sync`) and the IPTV module's `strings.xml` (`series_episode_label`
"S%1$d E%2$d", `settings_section_accounts` is in the IPTV module's `strings_trakt.xml`).

### 4.1 Application credentials

- TRAKT-FR-01 The Trakt client id and client secret are build-time values read from a
  git-ignored local properties file (`.local/trakt/trakt-credentials.properties`, keys
  `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET`, printable ASCII without quotes or backslashes) into
  build config. A build without them compiles; the service reports "not configured", the
  Connect button is disabled and the panel shows `trakt_unconfigured` ("This build has no Trakt
  application credentials, so accounts cannot be connected."). Credentials never appear in the
  repository, logs or diagnostics (the release APK necessarily carries them: the device flow
  needs the secret on the device). The OAuth redirect URI is the device-flow value
  `urn:ietf:wg:oauth:2.0:oob`.

### 4.2 Device sign-in

- TRAKT-FR-02 **Connect** (or Sign in again) starts one attempt, owned by the panel (leaving the
  panel, a profile change, Back, Cancel or ON_STOP cancels it and its HTTP call):
  1. `POST https://auth.trakt.tv/oauth/device/code` `{"client_id": …}` → `device_code`,
     `user_code` (≤ 128), `verification_url`, `expires_in`, `interval`. Validation: the URL must
     be `https`, port 443, host `trakt.tv` or `auth.trakt.tv`, path exactly `/activate`, no
     query, fragment or user info; `expires_in` 1–3,600 s; `interval` 1–300 s and ≤ expiry;
     strings non-blank without control characters. Otherwise INVALID_RESPONSE.
  2. The remaining lifetime is counted from **before** the code request (a slow response or a
     suspended UI never extends it); if nothing remains → EXPIRED without showing a prompt.
  3. Prompt shown: QR of the verification URL (320 × 320 px bitmap at 168 dp), "Approve Sohva TV
     on Trakt" (headline), "Scan the code with your phone, or open the address and enter the
     code.", the address, and the user code (headline).
  4. Poll `POST https://auth.trakt.tv/oauth/device/token`
     `{"client_id","client_secret","code": <device_code>}` on a **monotonic** clock: the first
     poll waits one full interval; then every interval. Responses: 200 → tokens; 400 → pending
     (but a body naming any error other than `authorization_pending` is a configuration error);
     404 → invalid code; 409 → already used; 410 → expired; 418 → denied; 429 → slow down
     (interval = max(interval + 5 s, Retry-After), Retry-After capped at 3,600 s); other → fail.
     Past the deadline → EXPIRED. Network failures end the attempt (never retried as if the
     device code were still unused).
  5. With the tokens, read the identity: `GET https://api.trakt.tv/users/settings` →
     `user.ids.uuid` (opaque, ≤ 256, no whitespace) and `user.username` (≤ 256). A missing or
     malformed uuid fails (never falls back to the username).
  6. Cancellation is checked between every step; the authorizer also accepts an access rule
     checked at each step (beta 23 passes "always allowed" and relies on the panel being
     disposed on a profile change; the rebuild passes the restricted-profile rule, FR-36).
  7. Store tokens and username for the profile, publish the account.
- TRAKT-FR-03 Tokens: `token_type` must be `bearer`; `access_token` and `refresh_token` non-blank
  ≤ 8,192 without control characters; expiry = (`created_at` + `expires_in`) × 1,000 ms with
  `created_at` in 0–253,402,300,799 and `expires_in` 1–31,536,000 s.
- TRAKT-FR-04 Messages (`trakt-message`, textMuted): declined → "Authorization was declined. You
  can try again."; expired → "The code expired. Start sign-in again for a new code."; invalid or
  already used code → "This code is no longer usable. Start sign-in again."; 429 → "Trakt asked us
  to wait. Please try again later."; configuration (401/403, bad credentials) →
  `trakt_unconfigured` text; background → "Sign-in stopped because Sohva left the foreground.
  Start again when ready."; anything else → "The connection could not be completed. Please try
  again."
- TRAKT-FR-05 Only the auth, identity and API clients talk to Trakt; each follows no redirects
  (a redirect never forwards a device code, secret or bearer token), has no automatic retry,
  no cache, no interceptors, and never logs bodies or raw errors. Timeouts: auth and identity
  20 s per call, API 30 s per call. Body limits: auth and identity 64 KiB, API 8 MiB.

### 4.3 Account states (panel)

| State | Row subtitle | Primary button | Extra |
|---|---|---|---|
| Not configured build | "Not connected" | Connect Trakt (disabled) | not-configured note |
| Not connected | "Not connected" (`trakt_not_connected`) | Connect Trakt | help note |
| Signing in | (previous) | Cancel sign-in | pairing block |
| Connected | "Connected as <username>" | Sign in again | Disconnect + note |
| Needs re-authorization | "Trakt no longer accepts the saved sign-in. Sign in again to continue syncing." | Sign in again | Disconnect + note |

- TRAKT-FR-06 Help note (always): "Movies and episodes you play in VOD and Discover are recorded
  to your Trakt account, and your Trakt progress and watched marks show on titles here. Live TV and
  catch-up are not recorded." Disconnect note: "Disconnecting removes the sign-in from this TV.
  Your Trakt history stays as it is."
- TRAKT-FR-07 The panel is keyed by the active profile: switching profile resets it and cancels
  any sign-in. Row title "Sohva profile: <profile name>".

### 4.4 Tokens and refresh

- TRAKT-FR-08 Every API use asks for current tokens (one refresh at a time): when the access
  token expires within **24 hours**, refresh first:
  `POST https://auth.trakt.tv/oauth/token` `{"client_id","client_secret","refresh_token",
  "grant_type":"refresh_token","redirect_uri":"urn:ietf:wg:oauth:2.0:oob"}`. 400 with
  `invalid_grant` → **re-authorization needed**: tokens deleted, account kept with the flag (the
  panel explains), API use stops. Other refresh failures propagate (the caller keeps its work for
  later).
- TRAKT-FR-09 **Rebuild rule:** the refresh margin must be smaller than the token lifetime
  (use min(24 h, lifetime / 2)); with the current fixed 24 h margin, tokens with a lifetime of
  24 h or less would be refreshed on every call.

### 4.5 Disconnect and profile removal

- TRAKT-FR-10 Disconnect deletes, for that profile: tokens and username, pending and in-flight
  scrobbles, the activity stamp, the cached Watch next and Recommended lists, and all its
  `trakt_state` rows; Home rows empty at once. No call is made to Trakt (no token revocation);
  the account's history is untouched.
- TRAKT-FR-11 Removing a Sohva profile disconnects its Trakt account the same way.

### 4.6 Title identity (ids only)

- TRAKT-FR-12 A Trakt item is a movie `{ids}` or an episode `{show ids, season, number}`
  (season ≥ 0, number ≥ 0). Ids: `trakt`, `tmdb`, `imdb`, `tvdb` (at least one). Titles are
  never used to match anything on Trakt.
- TRAKT-FR-13 VOD identity (content key → item), resolved once when VOD playback starts and
  only if the profile has an account:
  - movie `vod:movie:<source>:<id>`: the TMDB id the library's metadata matching assigned
    (`catalogue_metadata_overrides.externalId`, optional `tmdb:` prefix) **if TMDB produced it**
    (the metadata cache knows that id under provider `tmdb`); otherwise one on-demand metadata
    lookup (name + year, [Metadata](41-metadata-enrichment.md)) accepted only when the matching
    provider is TMDB;
  - episode `vod:episode:<source>:<episodeId>`: the episode must be in an active catalogue; the
    series' TMDB id by the same two rules; season and episode numbers from the catalogue;
  - TVmaze ids look like TMDB ids and are **never** accepted; anything else → no item (nothing is
    sent).
  The name + year matching belongs to the library's TMDB enrichment; Trakt itself only ever sees
  the resulting TMDB id.
- TRAKT-FR-14 Discover identity (addon media key → item): media id `tt` + 5–10 digits → IMDb;
  `tmdb:<positive integer>` → TMDB; any other id (for example `kitsu:…`) → nothing sent. Type
  `movie` → movie; `series` → episode numbered by the episode's `season`/`episode` fields, else
  by the last two `:`-separated integers of the video id (`<show>:<season>:<episode>`); other
  types → nothing.

### 4.7 Scrobbling

- TRAKT-FR-15 One scrobbler per player session, driven only by player callbacks on the main
  thread:
  - **begin**(profile, item) when a title is on the player (item may be null → silent);
  - player starts playing → send START (unless the last action was STOP); if a pause was waiting
    to be sent, cancel it instead (a rebuffer, not a pause);
  - player stops playing after a START → wait **2.5 s**, then send PAUSE;
  - end of stream → cancel a waiting pause, send STOP at **100 %**, clear;
  - player released / left → cancel a waiting pause; if anything was sent and it was not STOP,
    send STOP at the current percentage; clear.
  Progress = position / duration × 100, clamped 0–100; while the duration is unknown the last
  known percentage is used (0 at first). The same action is never sent twice in a row.
  Trakt marks a title watched itself when a STOP arrives above its threshold (80 %).
- TRAKT-FR-16 VOD wiring (playback service): when VOD playback is prepared, the item is resolved
  (FR-13) and the scrobbler begins; if already playing, START is sent at once. Every **10 s**
  (the VOD progress interval) the percentage is updated. Live channels and catch-up never begin a
  scrobbler, so nothing is sent for them.
- TRAKT-FR-17 Discover wiring (addon player): begin at start-up (FR-14); play/pause transitions,
  end and release as above.
- TRAKT-FR-18 Delivery: `POST https://api.trakt.tv/scrobble/{start|pause|stop}` with
  `{"movie":{"ids":{…}},"progress":p}` or `{"show":{"ids":{…}},"episode":{"season":s,"number":n},"progress":p}`
  (only the ids present are sent). 201 → done; **409** (Trakt recorded this moments ago) → done;
  401 → re-authorization needed; 403 → configuration; 429 → rate limited (Retry-After kept); 5xx
  → service; other → rejected.
- TRAKT-FR-19 Queue: each new scrobble is appended to the profile's persisted pending list;
  the list is collapsed so only the **latest report per title** remains (in order); delivery goes
  in order and **stops at the first outage** (network, service, rate limit, re-authorization),
  keeping the rest; an item Trakt rejects for good (unknown ids, bad request) is dropped and the
  rest kept for the next attempt. At most **200** pending entries (oldest dropped). A delivered
  STOP requests a sync (FR-22). Scrobbles are skipped entirely when the build is not configured,
  the service is offline (demo) or the profile has no account.
- TRAKT-FR-20 Interrupted playback: when START is sent, the item and percentage are stored as the
  profile's **active** entry (outside the queue); while playing, the stored percentage is updated
  at most every **30 s** — in beta 23 only from the VOD player's 10 s progress tick (the Discover
  player updates it only at play/pause transitions; the rebuild feeds both players' periodic
  progress); any PAUSE or STOP clears it. When the sync loop starts for a profile and an active
  entry exists (the app was killed mid-playback), a PAUSE at the stored percentage is sent first,
  so Trakt stops showing the title as "watching".

### 4.8 Sync (read side)

- TRAKT-FR-21 The sync loop runs for the **active profile** once a profile is chosen and Home's
  first resume read has completed; a profile change cancels it and starts one for the new
  profile. On start: close an interrupted playback (FR-20). Then repeatedly: refresh
  recommendations (FR-26) in parallel with a sync (FR-22; forced when no Watch next list has been
  stored yet); then wait until **15 minutes** pass or a sync is requested (after a delivered
  STOP), and after a request wait **3 s** more. Failures are swallowed until the next cycle.
- TRAKT-FR-22 Sync (one at a time per app): needs a configured build, an account and tokens.
  1. `GET sync/last_activities` → newest of `movies.watched_at`, `movies.paused_at`,
     `episodes.watched_at`, `episodes.paused_at`.
  2. If not forced, a stamp exists and the newest activity ≤ stamp → done (nothing changed).
  3. Fetch in parallel (at most **3 Trakt reads at a time** app-wide): `sync/watched/movies`,
     `sync/watched/shows`, `sync/playback?limit=500`. As soon as the playback list arrives, the
     cache's paused positions are replaced (unwatched rows deleted, all progress reset, paused
     rows written, keeping watched flags/plays and the newer time of existing rows) so bars
     appear before the larger watched lists finish.
  4. Build the full state: one row per watched movie (`movie:<id>`, watched, plays, last watched
     time) and per watched episode (`episode:<showId>:<season>:<number>`), then overlay each
     paused item (progress %, watched and plays kept from an existing row, time = the later of
     pause and last watch). Row id = `tmdb:<n>` when Trakt gave a TMDB id, else `imdb:<tt…>`;
     items with neither are skipped.
  5. If the account is still the same, replace the profile's whole `trakt_state` in one
     transaction (chunks of 500 rows) and save the stamp; announce the new history revision.
  6. Refresh Watch next (FR-25).
  Diagnostics lines: "synced N Trakt titles", "next up: N shows", "recommendations: N titles".
- TRAKT-FR-23 **Watched lists changed on 3 July 2026** (Trakt API discussion 775): without paging
  parameters `sync/watched/*` return only the first 100 items, and `sync/watched/shows` includes
  seasons only with `extended=progress` (100 shows per page). Beta 23 sends neither, so accounts
  may sync only 100 watched movies and no watched episodes (inferred from Trakt's announcement,
  not yet seen in a live response; Watch next is unaffected). **The rebuild implements the
  planned fix** (docs/HOME_ROWS_AND_TRAKT_LISTS_PLAN.md, phase 1):
  - page with `page=N&limit=L` and read `X-Pagination-Page-Count`; stop at the last page (one page
    when the header is missing); a page cap protects memory and exceeding it fails the call;
  - `sync/watched/movies` 250 per page; `sync/watched/shows?extended=progress` 100 per page; log
    when no show carries seasons;
  - any failed page fails the whole sync (the stored state is never replaced by a partial one);
  - read `last_activities` again after the walk; if it moved, walk once more;
  - store a **sync format version** next to the stamp and force a full resync when it is older
    (accounts synced since July saved a stamp over truncated data); write the new version only
    after a complete save; log page counts.
- TRAKT-FR-24 First-sync notice: while the profile has an account and no activity stamp yet,
  Home shows "Trakt history is waiting for its first sync" (`home_trakt_first_sync`) at the top
  centre of the hero band once Continue watching has settled (never for restricted profiles).

### 4.9 Home rows

- TRAKT-FR-25 **Watch next** (after each successful sync): take the watched shows with a Trakt
  id, sorted by last watched time, first **8**. For each (3 at a time):
  `GET shows/<traktId>/progress/watched?hidden=false&specials=false&count_specials=false` →
  `next_episode` {season, number, title} and `last_watched_at`; no next episode (show finished) →
  the show drops out. Pictures and synopsis come from the previously stored entry when it has a
  poster, else `GET shows/<traktId>?extended=full,images` (first time a show is seen). Each card:
  kind show, ids, title, year, overview, poster, fanart, season, number, episode title, updated =
  later of the two watch times. A show whose calls fail keeps its previous card (if any); a show
  answered with 404 or without a next episode is removed. The list (newest first) is published
  and stored after each show completes. No shows → empty list.
- TRAKT-FR-26 **Recommended for you** (on each loop cycle, only when the stored list is older
  than **12 hours** or missing): `GET recommendations/movies?limit=10&extended=full,images` and
  `GET recommendations/shows?limit=10&extended=full,images` in parallel, interleaved
  movie, show, movie, show… (up to 20).
- TRAKT-FR-27 Title parsing (both rows): object itself or wrapped under `movie`/`show`; needs at
  least one id and a title (≤ 200); `year`; `overview` ≤ 1,000; `images.poster[0]` and
  `images.fanart[0]` (Trakt returns host/path without a scheme: `https://` is prepended when the
  value does not start with `http`).
- TRAKT-FR-28 Rows are read from the encrypted store once per profile and list, then served from
  memory; Home subscribes only after its first Continue watching read. Card key =
  `<kind>:<trakt|tmdb|imdb id>:<season>:<number>`. Watch next cards are landscape (fanart, else
  poster) with "S1 E2 · <episode title>" (`series_episode_label`); Recommended cards are posters
  (poster, else fanart) with the year. Card and row anatomy: [Home](02-home.md).
- TRAKT-FR-29 Hero for a Trakt card: label "Watch next" (`home_watch_next`) or "Recommended for
  you" (`home_recommended`); facts: episode label, year. Synopsis and backdrop: if the title has
  a TMDB id and TMDB is enabled, the TMDB record **by id** in the metadata language
  (cached per id, type and language, [Metadata](41-metadata-enrichment.md)); synopsis = TMDB
  overview, else Trakt's overview; backdrop = TMDB backdrop, else fanart, else poster. Trakt's
  English text is never shown first and then replaced: the hero waits for the TMDB answer.
- TRAKT-FR-30 Opening a Trakt card:
  1. Library route by TMDB id: movies → the first `vod:movie:` content key mapped to that id →
     movie details; shows → only if the metadata cache confirms TMDB produced that id, the first
     `series:<source>:<seriesId>` key → series details (not a specific episode).
  2. Otherwise the Trakt title screen: "Looking for this title in your sources…" under the title;
     tries keys `(<type>, tt…)` then `(<type>, tmdb:<id>)` (type `movie` or `series`) against each
     enabled addon that supports `meta` for that key, in priority order, using the Discover
     details route; the first success opens the Discover title page (on the named episode when
     season and number match a video); none → "This title is not available from your sources
     right now." with **Back to home**.

### 4.10 Overlays on cards and resume

- TRAKT-FR-31 Discover map (profile): for every `trakt_state` row, state = (fraction, watched,
  updated) where fraction = progress / 100 when 0 < progress < 100, watched = watched flag and
  not in progress. Keys, for each id form the row has (IMDb and `tmdb:<n>`):
  `movie:<id>` and `series:<showId>:<season>:<episode>`. Discover cards look up their own media
  key ([Discover](50-discover-addons.md) ADDON-FR-71).
- TRAKT-FR-32 VOD overlay (library): Trakt rows are joined to library copies through the TMDB id
  in `catalogue_metadata_overrides` (series rows only when `metadata_cache` confirms provider
  `tmdb` for that id), restricted to enabled sources, their active catalogue snapshot and items
  the organisation rules show. The join starts from the small Trakt set (CROSS JOIN order) so
  SQLite never scans the catalogue first:

  ```sql
  FROM trakt_state state
  CROSS JOIN catalogue_metadata_overrides metadata
      ON metadata.externalId = CAST(state.tmdb AS TEXT) AND metadata.contentKey LIKE 'vod:movie:%'
  CROSS JOIN iptv_source_state source ON source.sourceId = <source part of contentKey> AND source.enabled = 1
  CROSS JOIN import_state import ON import.sourceId = source.sourceId AND import.kind = 'catalogue'
  CROSS JOIN organization_visible_movies movie
      ON movie.sourceId = source.sourceId AND movie.snapshotId = import.activeSnapshotId AND movie.movieId = <id part>
  WHERE state.profileId = :profileId AND state.kind = 'movie' AND state.tmdb IS NOT NULL
  ```

  Detail pages and walls use scoped variants that start from the requested content keys (≤ 200
  per query) or one series. Rows with only an IMDb id do not reach the VOD library.
- TRAKT-FR-33 Merge with local VOD progress, per content key: the local position wins when its
  last-watched time ≥ the Trakt row's time; otherwise a Trakt pause becomes a partial position
  (position = runtime × % when a runtime is known — from the playlist for episodes, or from an
  earlier local playback of that copy — else only the bar fraction, with no resume point) and a
  Trakt watched mark becomes "completed". A Trakt pause outranks Trakt's own
  watched mark (a rewatch in progress). Rows neither paused nor watched are ignored.
- TRAKT-FR-34 Continue watching (VOD part, [Home](02-home.md)): library copies paused on Trakt
  (0 < progress < 100), one per Trakt identity (the lowest content key), newest first, at most
  20, joined with the local list: a local entry for the same copy wins when newer (it then gains
  the Trakt ids for de-duplication), otherwise the Trakt entry replaces it. Home merges movies
  across Discover and VOD by IMDb/TMDB aliases and prefers the copy actually watched locally.
- TRAKT-FR-35 Resume: Discover — when resuming and Trakt's pause is newer than the local entry
  (or there is none), the player starts at 0 and seeks to duration × fraction once the duration
  is known; the title page then offers **Continue watching**. VOD — the merged position (FR-33);
  a Trakt-only movie pause without a known runtime shows a bar but starts from 0 (see open
  questions).

### 4.11 Restricted profiles and demo

- TRAKT-FR-36 Restricted profile: no Accounts section, Watch next and Recommended rows empty, no
  first-sync notice, no Discover (so no Discover scrobbles). **Rebuild rule:** a restricted
  profile also runs no sync loop, sends no VOD scrobbles and shows no Trakt overlay — beta 23
  still does these three for a profile that connected before it was restricted (gap).
- TRAKT-FR-37 Demo build: the Trakt service is offline (never calls Trakt; the sync loop idles)
  and a fictional connected account with pauses, watched marks, a Watch next shelf and
  recommendations is seeded for screenshots (demo flavour: [Tech stack and build](../plan/05-tech-stack-and-build.md)).

### 4.12 Failure mapping (API)

| HTTP | Failure | Effect |
|---|---|---|
| 401 | REAUTHORIZE | account flagged, tokens removed, panel explains |
| 403 | CONFIGURATION | treated as a build/credential problem |
| 429 | RATE_LIMITED (+ Retry-After seconds or HTTP date, rounded up) | scrobbles stay queued; sync waits for the next cycle |
| 5xx | SERVICE | as above |
| 404 (show summary/progress) | none | the show is skipped |
| other / unparsable | INVALID_RESPONSE | scrobble dropped (permanent), sync cycle abandoned |
| I/O, timeout | NETWORK | queued / next cycle |

Response parsing runs on a background dispatcher (large histories never block the caller's
thread).

### 4.13 Localisation

- TRAKT-FR-38 Trakt strings (`strings_trakt.xml` in the app and `settings_section_accounts` in
  the IPTV module) exist in English and Finnish only; the other five languages fall back to
  English (`MissingTranslation` suppressed). `trakt_title` "Trakt" is not translatable. Titles,
  episode names and years come from Trakt; synopses from TMDB in the metadata language.

## 5. Screen anatomy

### 5.1 Accounts panel (inside Settings content column)

Column, 12 dp spacing: overline "Trakt"; settings row "Sohva profile: <name>" (Link icon) with
the state subtitle; primary compact button (`trakt-primary`); not-configured note; pairing block
(row, 24 dp gap: QR 168 dp; column, 8 dp spacing: "Approve Sohva TV on Trakt" headline, note,
address in body text, user code in headline); message line (textMuted); help note (body,
textMuted); when connected: Disconnect (compact, danger) and its note. Notes use body size in
textMuted. No screenshot of the current panel exists; Settings layout: [Settings](70-settings.md).

### 5.2 Overlays

- Progress bar: 4 dp tall, focus colour, bottom-start of the artwork, width = fraction.
- Watched tick: 22 dp circle in the focus colour at the artwork's top-end, 6 dp inset, "✓" in the
  background colour (caption size). Same component on Discover posters and episode cards; VOD
  cards use the library's own watched/progress styling ([Movies and series](40-movies-and-series.md)).

### 5.3 Trakt title screen

Full screen background; column with 32 dp padding, 16 dp spacing: title (headline), status text,
**Back to home** (`addon_back`). Replaced by the Discover title page once found.

## 6. Data

| Store | Content | Scope / lifetime |
|---|---|---|
| SharedPreferences `trakt_accounts`, key `account:<profile>` | encrypted JSON `{access, refresh, expires, username, reauthorize}` (tokens absent after re-authorization is flagged) | per profile, until disconnect |
| `pending:<profile>` | encrypted JSON array of `{kind, tmdb?, imdb?, trakt?, tvdb?, season?, number?, action, progress}` (≤ 200) | until delivered |
| `active:<profile>` | encrypted single in-flight START | cleared by PAUSE/STOP |
| `activity:<profile>` | newest Trakt activity time at the last full sync (long, 0 = never) | — |
| `recommendations:<profile>`, `nextup:<profile>` | encrypted `{at, items:[{kind,title,year?,overview?,poster?,fanart?,trakt?,tmdb?,imdb?,tvdb?,season?,number?,episodeTitle?,updatedAt}]}` | refreshed per FR-25/26 |
| Main database table `trakt_state` (schema 29) | `profileId`, `key`, `kind` (movie/episode), `tmdb`, `imdb`, `season`, `number`, `progress` (%), `watched`, `plays`, `updatedAtMillis`; PK (profileId, key); indices (profileId, tmdb), (profileId, imdb); plus an index on `catalogue_metadata_overrides.externalId` added in the same migration | per profile; replaced wholesale per sync; a cache, never a history of its own |

- Encryption: the app's main envelope cipher (Android Keystore wrapped data key,
  [Security](73-security-privacy.md)). All writes use synchronous `commit()`.
- Not included in `.smbak` backups; Android backup and device transfer are disabled for the
  app. A restored or new device needs a new sign-in.
- **Rebuild rule:** also store the account's stable id (`user.ids.uuid`, fetched today but not
  saved) and, when a sign-in yields a different account, clear the profile's cache, stamp and
  queue before saving (today a different account keeps the previous account's state until Trakt
  reports activity newer than the old stamp).
- Migrations to keep in mind: a private Trakt trial build used main database 27 and progress
  database 2–3; releases drop its leftover tables (`viewing_projection_receipts`,
  `addon_projection_receipts`, `addon_progress_display`) without touching user data
  ([Data model](../plan/04-data-model.md)).

## 7. External interfaces

All requests carry `trakt-api-version: 2`, `trakt-api-key: <client id>`,
`Accept: application/json`; API calls add `Authorization: Bearer <access token>` and
`Content-Type: application/json`.

| Endpoint | Use | Fields read |
|---|---|---|
| `POST auth.trakt.tv/oauth/device/code` | start sign-in | device_code, user_code, verification_url, expires_in, interval |
| `POST auth.trakt.tv/oauth/device/token` | poll | access_token, refresh_token, token_type, created_at, expires_in; `error` on 400 |
| `POST auth.trakt.tv/oauth/token` | refresh | as above; `invalid_grant` |
| `GET api.trakt.tv/users/settings` | identity | user.ids.uuid, user.username |
| `POST api.trakt.tv/scrobble/{start, pause, stop}` | scrobble | action, progress, movie/show/episode titles (logged) |
| `GET api.trakt.tv/sync/last_activities` | change check | movies.watched_at, movies.paused_at, episodes.watched_at, episodes.paused_at |
| `GET api.trakt.tv/sync/playback?limit=500` | paused items | id, type, progress, paused_at, movie/show ids, episode season/number, titles |
| `GET api.trakt.tv/sync/watched/movies` (rebuild: paged, 250) | watched movies | movie.ids, plays, last_watched_at |
| `GET api.trakt.tv/sync/watched/shows` (rebuild: `extended=progress`, paged, 100) | watched episodes | show.ids, title, year, last_watched_at, seasons[].number, episodes[].number, plays, last_watched_at |
| `GET api.trakt.tv/shows/<id>/progress/watched?hidden=false&specials=false&count_specials=false` | Watch next | next_episode.season/number/title, last_watched_at |
| `GET api.trakt.tv/shows/<id>?extended=full,images` | Watch next pictures | ids, title, year, overview, images.poster/fanart |
| `GET api.trakt.tv/recommendations/{movies, shows}?limit=10&extended=full,images` | Recommended | as above |

Times are ISO-8601 instants (unparsable → 0). TMDB by-id lookups for hero synopses go through the
metadata module with the user's own TMDB token ([Metadata](41-metadata-enrichment.md)); nothing
Trakt-related is sent to TMDB beyond the id and language.

Rate limits: Trakt answers 429 with `Retry-After`; the client never retries by itself.
Steady-state cost per connected profile: one `last_activities` call every 15 minutes; after
activity, three list calls plus ≤ 8 progress calls (+ one summary per newly seen show);
recommendations 2 calls per 12 hours; one scrobble per play/pause/stop.

## 8. Edge cases and limits

- Clock changes: sign-in polling uses a monotonic clock (a clock jump cannot extend an expired
  code); token expiry uses wall-clock time.
- Network loss during playback: scrobbles queue (≤ 200, latest per title) and go out with the
  next scrobble or the next start's interrupted-playback pause. **Rebuild rule:** also flush the
  queue at each sync cycle (today the flush function exists but nothing calls it).
- Re-authorization needed: today scrobbles made while tokens are missing are **dropped** (the
  delivery treats "no tokens" as "nothing to send"). **Rebuild rule:** keep them queued (bounded)
  and deliver after the viewer signs in again with the same account.
- Two profiles: each has its own account and cache; only the active profile syncs.
- Titles with only an IMDb id reach Discover cards but not the VOD library (VOD joins by TMDB).
- Custom Discover ids (not `tt…`/`tmdb:`) and VOD titles without a TMDB match are never sent.
- Trakt returns 409 for a duplicate scrobble: treated as success.
- Very large histories: the API body limit is 8 MiB per call today; with paging (FR-23) each page
  stays small.
- Process death mid-playback: FR-20. Process death mid-sync: the stamp is written only after a
  complete state replace, so the next start repeats the sync.
- Home shows Trakt rows only when non-empty; a finished show leaves Watch next on the next sync.

## 9. Lightweight by design

- Start-up: reading the encrypted account records (a few hundred bytes per profile) during
  app initialisation; no Trakt network call until Home's Continue watching has been read. No
  work at all for profiles without an account.
- Steady state: one tiny request every 15 minutes. **Rebuild rules:**
  - **No sync while video plays.** Today the loop keeps its 15-minute timer during playback and
    may download the full watched history mid-film. The rebuild suspends the loop while any player
    is in the foreground (scrobbles still go out) and runs one check when playback ends (the
    STOP-triggered sync already does this).
  - Compare the four activity timestamps separately and fetch only what changed (paused →
    playback list only; watched → the affected watched list).
  - Write the cache as a diff (changed rows only) instead of delete-all + insert-all: every
    wholesale replace invalidates all observers, which re-run the VOD overlay joins and rebuild
    every Discover map.
  - Publish Watch next once per sync (today every show's result re-encrypts and commits the whole
    list, up to 8 commits per sync).
  - Keep the "active" in-flight percentage in memory and persist it on pause or every few minutes
    rather than a synchronous commit every 30 s.
- Memory: the playback list (≤ 500 items) and watched lists are parsed off the main thread;
  with paging each page is bounded (≤ 250 movies / 100 shows per page), and rows are written in
  chunks of 500. The `trakt_state` table holds one small row per watched movie/episode (a
  heavy account can reach tens of thousands of rows): **rebuild rule** — never build a map of the
  whole table per screen (today each Discover screen does, on every change); keep one shared
  per-profile index (or query only the visible keys, as the VOD walls already do with ≤ 200 keys).
- VOD overlay queries start from the small Trakt set (CROSS JOIN order) and are indexed
  (`trakt_state` (profileId, tmdb/imdb), `catalogue_metadata_overrides.externalId`); the scoped
  wall/detail queries start from the requested keys. Never join the organisation views in a way
  that lets SQLite start from the catalogue ([lessons](../plan/08-lessons-learned.md), the
  organisation-view join-order trap).
- Home images: Trakt cards use Trakt's mirrored TMDB images directly, decoded at card size by the
  shared image loader; the hero's TMDB lookup runs only after focus has rested
  ([Home](02-home.md)).
- QR bitmap: built once per prompt; **rebuild rule** — off the main thread with one `setPixels`
  call at the displayed size (today 102,400 `setPixel` calls inside composition).
- Diagnostics: scrobble outcomes are logged with the item ids and the title Trakt echoes back, so
  a saved diagnostics file lists what the viewer watched. **Rebuild rule:** log action, outcome
  and counts only (no titles or ids); viewing history does not belong in a file meant for bug
  reports ([Diagnostics](72-updates-about-diagnostics.md), [Privacy](73-security-privacy.md)).

## 10. Lessons from the current app

1. **Overlay, not a shared viewing store** (docs/SOHVA_TV_TRAKT_OVERLAY.md, decided 14 September
   2026): an earlier shared-store design (branch `codex/trakt-progress`) is parked. Local VOD and
   Discover progress stay the source of truth for their own ledgers; Trakt is a thin cache beside
   them. Keep this.
2. **Rebuffers produced pause/start pairs** (commit c1fa887): a 2.5 s settle before PAUSE, cancelled
   when playback resumes.
3. **A killed app left Trakt showing "watching"** (commit 1e59fa3): remember the in-flight START
   outside the queue; pause it at the next start.
4. **409 on scrobble is success**, not an error.
5. **TVmaze ids look like TMDB ids** (TraktVodIdentity, TraktLibraryLookup): accept a series id
   only when TMDB produced it; never match by title on Trakt.
6. **Trakt's July 2026 watched-endpoint change** (docs/HOME_ROWS_AND_TRAKT_LISTS_PLAN.md phase 1):
   paging, `extended=progress`, re-check of last activities, sync format version for a forced
   resync (FR-23).
7. **Continue watching first, optional rows later** (commits ca940fe, d84e5e9, beta 16): Trakt
   rows and the sync loop wait for Home's cached resume read; each Home section arrives
   independently.
8. **Scoped progress reads and indexed overlays** (commit 4084542): wall/detail overlays query only
   the visible content keys (≤ 200) and a single series.
9. **Account identity**: the uuid is verified at sign-in but not stored; a different account after
   "Sign in again" inherits the old cache (FR data rule in §6).
10. **Pending scrobbles**: dropped while re-authorization is needed; never flushed except by a new
    scrobble (§8 rules).
11. **Restricted profiles**: hidden in the UI but the loop, VOD scrobbles and overlays still run for
    a profile that connected earlier (FR-36 rule).
12. **Refresh margin vs lifetime** (FR-09).
13. **Finnish-only translation**: the Trakt panel is English in five of the seven languages; the
    rebuild should translate `strings_trakt.xml` like every other string file.
14. **Keep**: device code with QR of Trakt's own page (no password on the TV); fixed origins, no
    redirects; tokens encrypted per profile; latest-per-title queue that stops at the first outage;
    last-activities gate; TMDB-by-id synopses in the viewer's language with no English flash.

Open questions (for the owner)
- VOD movies paused only on Trakt resume from 0 when the playlist has no runtime (only the bar
  shows). Should the VOD player seek by fraction once the duration is known, as Discover does?
- Trakt list rows (watchlist, favourites, charts, custom lists) and a Home row editor are planned
  in docs/HOME_ROWS_AND_TRAKT_LISTS_PLAN.md phases 2–4; they are not in beta 23 and not in this
  spec's scope unless the owner adds them.
- Should Disconnect also revoke the token at Trakt (`/oauth/revoke`)? Beta 23 only forgets it
  locally and says so.

## 11. Acceptance tests

Unit (JVM, MockWebServer)
- Auth client: device code sends only the client id and accepts only the official activation
  page; polling statuses 400/404/409/410/418/429 map correctly and a named 400 error is not
  endless pending; tokens parsed and never logged; `invalid_grant` → re-authorization, not
  retried; redirects never forward codes or credentials; oversized responses rejected; header
  injection sanitised; cancellation stops a request; Retry-After seconds and HTTP dates rounded
  up; poll schedule waits, slows down and expires on a monotonic clock.
- Authorizer: pending/slow-down wait before retrying; terminal statuses distinct and never read
  identity; deadline stops polling; a slow code response does not extend expiry and the first
  poll still waits a full interval; a code expired in transit shows no prompt; cancellation
  interrupts an in-flight poll; access changes stop before the next request, during token
  exchange and during identity verification; identity failure never returns tokens; network
  failure is not retried.
- Identity client: uses only the access token and client id; uuid required (no username
  fallback); no redirects; rate limits and auth failures sanitised; oversize rejected.
- API client: scrobble bodies for movie and episode; failure mapping; playback/watched parsing of
  ids and times; recommendations and show progress parsing incl. image scheme; parsing off the
  caller thread for large histories. **New:** paging walks all pages, missing header = one page,
  `limit` and `extended=progress` sent, a failing page fails the call, page cap enforced; forced
  resync after a sync-format bump.
- Identity mapping: movies by IMDb or TMDB id, custom ids ignored; episodes from the video entry
  or the `<show>:<s>:<e>` id; VOD ids accepted only when TMDB produced them.
- Scrobbler: start/pause settle/rebuffer cancel/stop at end/stop on release; no duplicate
  actions; queue collapse and stop-at-first-outage; permanent rejection dropped; 409 success.
- Merge rules: local newer wins; Trakt pause → partial position with and without runtime;
  watched → completed; pause outranks watched.

Instrumented (emulator)
- Accounts section hidden for a restricted profile; panel states (not configured, connected,
  re-authorization) render; Back cancels a pending sign-in (fake auth); ON_STOP cancels.
- With a fake Trakt server: sync writes `trakt_state`; a movie wall and a series page show bars
  and ticks; a Discover poster and episode card show them; Continue watching gains a Trakt-paused
  library title; a Discover title resumes at the Trakt fraction.
- Home: Watch next and Recommended rows render from stored lists; a card with a library copy opens
  its details page; a card without one opens the Discover lookup and then the title page.
- Playback: VOD and Discover playback send start → pause (after 2.5 s) → stop; a quick
  pause/resume sends no pause; Live TV sends nothing; killing the app mid-playback leads to a pause
  on the next start.

Manual (device, real account)
- Connect on the Shield by phone; play a VOD movie, a VOD episode and a Discover title for a minute
  each; they appear in the account's playback progress within a minute of leaving the player.
- Pause on trakt.tv or another device; Home and the title page show it; Continue resumes there.
- **Low-end check:** on the S905Y4 box with an account holding ≥ 2,000 watched episodes, run a
  forced sync while browsing the Movies wall: no D-pad press takes more than two vsyncs, the Java
  heap stays ≤ 64 MB, and no Trakt request is made while a video is playing (except scrobbles),
  verified with the diagnostics log and a network capture.

## 12. Reference: current code map

- `trakt/src/main/java/com/sohva/tv/trakt/TraktAuth.kt` — failures, credentials, tokens, device code, poll results, monotonic poll schedule.
- `trakt/.../TraktAuthClient.kt` — device code, polling, refresh; Retry-After parsing.
- `trakt/.../TraktDeviceAuthorizer.kt` — one caller-owned sign-in attempt with access checks.
- `trakt/.../TraktIdentityClient.kt` — `users/settings` identity (uuid + username).
- `trakt/.../TraktApiClient.kt` — scrobble, playback, watched lists, recommendations, show summary/progress, last activities.
- `trakt/.../TraktModels.kt` — ids, items, scrobble actions, list models, activities.
- `app/src/main/java/com/streammate/tv/trakt/TraktService.kt` — per-profile accounts, tokens, queue, sync loop, Home lists, Discover state map.
- `app/.../trakt/TraktAccountStore.kt` — encrypted per-profile preferences (tokens, queue, active, stamp, lists); Home title and card-state models.
- `app/.../trakt/TraktScrobbler.kt` — player-driven start/pause/stop state machine.
- `app/.../trakt/TraktVodIdentity.kt`, `TraktAddonIdentity.kt` — content key → Trakt item.
- `app/.../trakt/TraktLibraryLookup.kt` — Trakt title → library movie/series by TMDB id.
- `app/.../trakt/TraktSettingsPanel.kt` — Settings › Accounts panel.
- `app/.../trakt/TraktTitleScreen.kt` — Discover lookup for a Trakt title.
- `core/src/main/java/com/streammate/tv/core/database/TraktState.kt` — `trakt_state` entity, DAO, playback-first and wholesale replace.
- `core/.../database/TraktHomeQueries.kt`, `TraktProgressQueries.kt` — Continue watching and overlay SQL.
- Callers: `app/.../app/StreamMatePlaybackService.kt` (VOD scrobbling), `app/.../addons/AddonPlayback.kt` (Discover scrobbling and resume), `app/.../app/StreamMateApp.kt` (sync loop, Home rows, card routing, Accounts wiring, profile removal), `iptv/.../repository/CatalogueRepository.kt` (VOD overlay merge), `app/.../feature/home/HomeScreen.kt`, `HomeResumeState.kt` (rows, hero, alias merge), `app/src/demo/.../DemoTraktSeed.kt`.
- Resources: `app/src/main/res/values{,-fi}/strings_trakt.xml`, `iptv/src/main/res/values{,-fi}/strings_trakt.xml`.
