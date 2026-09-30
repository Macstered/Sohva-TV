# Discover: Stremio-compatible addons, Search and Library

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

Discover lets a viewer browse and play movies and series from **their own** Stremio-compatible
addons (HTTP services that answer the Stremio addon protocol: a manifest plus catalog, meta,
stream and subtitles resources). It is a self-contained section reached from the Home rail. It
works with no IPTV source at all, keeps its own encrypted storage, and never touches the IPTV
database. The viewer installs configured addon URLs (typed, from a text file, sent from a phone,
copied from a Stremio account, or read from a Nuvio JSON export), then browses catalog rows,
opens title pages with cast, seasons and episodes, picks a source and plays it in an addon player
with automatic preferred-language subtitles, subtitle addons and subtitle timing adjustment.
Part-watched titles are remembered per profile (and feed Home's Continue watching), a local
Library bookmarks movies and whole series, and Trakt progress and watched marks (see
[Trakt](51-trakt.md)) are drawn on cards. Restricted profiles never see or reach Discover.

No addon, media, account or credential ships with the app. No torrent, NZB/archive, DRM,
external-app hand-off or addon-script engine is included.

## 2. Feature checklist

Access and independence
- ADDON-01 Discover destination on the Home rail (icon + "Discover"), only for unrestricted profiles.
- ADDON-02 Discover works independently of IPTV: no playlist needed; separate databases, cache, HTTP clients and encryption key.
- ADDON-03 Restricted profiles: no Home entry; direct access shows "Addons are unavailable for restricted profiles."; every addon data operation is denied.
- ADDON-04 Everything is per profile: installations, catalog order, catalog visibility, Library, watch progress.
- ADDON-05 Discover data is excluded from the `.smbak` backup (stated in the UI).

Installing and managing addons (Addons & setup)
- ADDON-06 Install one configured addon URL (`https://` or `stremio://`) from a masked text field.
- ADDON-07 URL normalisation: `stremio://` becomes `https://`; directory/base URLs gain `manifest.json`; AIOMetadata `/stremio/<UUID>` base accepted.
- ADDON-08 Manifests that require configuration are refused with guidance.
- ADDON-09 Installed addons list: name, "N catalogs · Enabled/Disabled", enable switch.
- ADDON-10 Refresh an addon's manifest.
- ADDON-11 Raise an addon's provider priority ("Priority ↑"): source order and metadata fallback order.
- ADDON-12 Remove an addon, with a confirmation dialog focused on Cancel.
- ADDON-13 Reload saved addons.
- ADDON-14 Browse one addon's catalogs (including hidden ones) from its row.
- ADDON-15 The same manifest with different configurations can be installed side by side; installing the exact same URL again reuses the existing installation.

Import
- ADDON-16 Import screen with three methods: Account & phone, From a file, Manual URL.
- ADDON-17 Build a pending list from manual URLs, one masked URL at a time.
- ADDON-18 Choose a UTF-8 text file (one URL per line) with the system document picker.
- ADDON-19 Send a URL list or a `.txt` file from a phone over the local network (QR, one-time session).
- ADDON-20 Copy addon configurations from a Stremio account through Stremio's device-link QR.
- ADDON-21 Read a Nuvio addon-list or state JSON file (URLs only).
- ADDON-22 Preview: every entry is checked against its provider; per-entry status; nothing is installed.
- ADDON-23 Select or skip each new entry; install the selected ones in list order; result list.
- ADDON-24 Existing installations are never changed by an import; duplicates in the list are skipped.

Catalog organisation
- ADDON-25 Per-profile catalog order: pick up, move (Up/Down, Page Up/Down, Home/End, held repeats), OK to place, Back to cancel.
- ADDON-26 Per-profile catalog visibility: Show/hide switch per catalog; hidden catalogs disappear from Discover home, the Discover filter page and Search.
- ADDON-27 New catalogs appear shown and are appended in provider order; order and visibility survive manifest refresh.

Discover home
- ADDON-28 Full-screen backdrop and a hero describing the focused title: logo (or title), facts, synopsis.
- ADDON-29 Hero synopsis in the metadata addon's language, fetched after focus rests, never flashing catalog-language text first.
- ADDON-30 Continue watching as the first row: part-watched Discover titles with progress bars.
- ADDON-31 One shelf per ready-to-browse catalog (no required choice), in the saved order.
- ADDON-32 Saved shelves render at once and revalidate; "Showing saved titles; provider unavailable." when stale.
- ADDON-33 "Show all" at the end of each shelf opens the paged grid.
- ADDON-34 Trakt progress bars and watched ticks on movie posters and episode cards.
- ADDON-35 Collapsible icon rail: Home, Library, Search, Discover (compass), Addons & setup, Back to home.
- ADDON-36 Focus returns to the card, Show all button or rail item the viewer left.
- ADDON-37 Missing Continue-watching artwork is repaired once per Discover visit.

Grids, filters, Search
- ADDON-38 Show all grid with captions (title; year · rating), automatic paging, 1,000-title cap, explicit retry of a failed page.
- ADDON-39 Refresh titles (bypasses the fresh cache).
- ADDON-40 Option filters as choosers (Genre, Year, …; required or optional) and free-text filters with Apply.
- ADDON-41 Discover filter page: choose Type and Catalog across all visible catalogs, then its filters.
- ADDON-42 Search: one query across eligible catalogs; Movies and Series rows; progress, partial failures, stale notice.

Title pages
- ADDON-43 Details from the catalog's addon, falling back to other installed metadata addons.
- ADDON-44 Movie page: title, facts, synopsis, Find sources / Continue watching, Start from beginning, Library toggle, cast portraits.
- ADDON-45 Series page: overview, cast names, season chips (Specials last), episode cards with thumbnails and Trakt bars/ticks.
- ADDON-46 Episode page with its own title, synopsis and sources.
- ADDON-47 Retry details / Retry episode details.

Sources and playback
- ADDON-48 Progressive source list, grouped per provider in priority order, each with loading/failure/empty state.
- ADDON-49 Scraper chooser filters providers; Refresh re-resolves sources.
- ADDON-50 Unsupported transports (torrent, external link, YouTube, NZB, archives, local bridge) are listed as "Unsupported transport" and never started.
- ADDON-51 Continue watching and Start from beginning start the first playable source automatically.
- ADDON-52 Resume from the local position, or from a newer Trakt pause.
- ADDON-53 Playback loading screen: backdrop, pulsing logo or title, stage text, Cancel and Subtitles.
- ADDON-54 Addon player with the shared bottom controls (transport, audio, subtitles, aspect Fit/Zoom/Fill), D-pad seek and media keys.
- ADDON-55 Retry with a freshly resolved source after a failure or a background stop.
- ADDON-56 At the end: movies return to their page; episodes continue to the next one (preference), across seasons.
- ADDON-57 Starting addon playback stops IPTV playback; only one addon player exists at a time.

Subtitles and audio
- ADDON-58 Subtitle picker: language column with counts and "Subtitles off"; option cards for embedded, stream and addon subtitles; "✓ Selected".
- ADDON-59 Subtitle addons are queried with the stream's `videoHash`, `videoSize` and `filename` hints only.
- ADDON-60 Automatic subtitles from the primary/secondary subtitle preferences, suppressed when the preferred audio language is present; 5 s budget with embedded fallback.
- ADDON-61 "Show all languages" toggle (persisted) in the picker and in Addons & setup.
- ADDON-62 Subtitle sync: ±60 s, 0.1 s steps, 1 s when held, Apply, Reset to zero, Play/Pause preview.
- ADDON-63 Downloaded SRT, WebVTT and SSA/ASS subtitles; appearance follows the VOD subtitle settings.
- ADDON-64 Audio track picker.

History and Library
- ADDON-65 Encrypted watch progress per title and episode; complete at 95 % or at the end.
- ADDON-66 Watch history page in Addons & setup with "Forget progress".
- ADDON-67 Discover progress feeds Home's Continue watching ([Home](02-home.md)).
- ADDON-68 Add to / Remove from library on movie and series pages.
- ADDON-69 Library grid with All / Movies / Series filters, newest first, 1,000 titles per profile.
- ADDON-70 A Library title whose addon is disabled or removed explains itself and offers removal.

Settings, text, security
- ADDON-71 Subtitles section in Addons & setup: show-all switch, preferred-language summary, metadata-language guidance.
- ADDON-72 About screen "Discover addons" privacy paragraph.
- ADDON-73 Configured URLs and addon payloads encrypted with a Discover-only key; masked inputs; URLs never in logs, UI labels or saved state.
- ADDON-74 Encrypted response cache for fast revisits and offline fallback.
- ADDON-75 Addon interface in all seven app languages; provider text is never translated by the app.

## 3. Entry points and navigation

Entry points
- Home rail item **Discover** (`home_discover`, "Discover", not translated; icon
  `ic_sohva_nav_discover`). Present only when the package policy allows addons (production and
  debug packages; never the demo package) and the active profile is not restricted. With the
  extra item the Home rail scrolls vertically so Settings stays reachable at small interface
  scales. See [App shell](01-app-shell-navigation.md).
- Home **Continue watching** card of a Discover title opens that title's page directly
  (destination "DiscoverTitle"), on the episode that was playing, where Continue is offered.
- A Trakt **Watch next** / **Recommended** card whose title is not in the IPTV library opens a
  Discover title page after an addon lookup ([Trakt](51-trakt.md), TRAKT-FR-30).
- About shows the Discover privacy paragraph (`about_addons_title`, `about_addons_body`).
- Unified Search ([Search](03-search.md)) does **not** search addons; Discover has its own Search.

Discover is one destination with internal screens. Every internal screen replaces the landing
(the landing composition is left, so its effects stop). Back from each state:

| State | Back does |
|---|---|
| Landing, focus in rows | Leaves Discover to Home. |
| Landing, focus in the rail | Returns focus to the shelf card last focused (or the Continue row); a second Back leaves. |
| Library, Search, Discover filter, Addons & setup (root) | Returns to the landing, focus on that screen's rail item. |
| Show all grid | If the free-text filter form is open and titles are loaded: closes the form. Otherwise returns to the landing, focus on that shelf's **Show all** button. |
| Title page (movie or series) | Returns to where it was opened; the card that opened it is focused. |
| Episode page | Returns to the series page, that episode's season selected and the episode focused. |
| Player, loading | Cancels start-up and returns to the title page. |
| Player, subtitle sync open | Returns to the subtitle picker (playback paused). |
| Player, subtitle or audio picker open | Closes it; resumes play if playback was playing when opened. |
| Player, controls visible or focused | Hides the controls (focus to the video). |
| Player, controls hidden | Leaves the player to the title page. |
| Addons & setup nested pages (Catalogs, Import, Watch history, one addon's catalog list) | Back to Addons & setup. |
| Catalog order in move mode | Cancels the move, restores the original order, focus on the moved catalog. |
| Import sub-screens (Stremio, Phone) | Cancels the attempt and returns to Import in the section it was opened from, focus on its Back button. Phone leaves the pending list unchanged; opening Stremio copy had already cleared it. |

Focus on entry: the first card of Continue watching (or its placeholder button when empty),
once history has loaded. Title pages focus the primary action (movie/episode) or the selected
season chip (series). Profile change while Discover is open disposes all Discover UI state
(keyed by the active profile id) and clears secret inputs.

## 4. Behaviour

Quoted English UI text comes from `strings_addons.xml` (kit:
`reference/strings/app/values/strings_addons.xml`, keys `addon_*` and `addon_ui_*`, the key
usually being the text in snake case) unless another key is named; shared labels ("Home",
"Search", "Library", "Movies", "Series", "Refresh", "Sources", "Subtitles", "Play", "Clear", …)
come from the app and IPTV string files.

### 4.1 Access and profiles

- ADDON-FR-01 Package policy: addons are allowed only in the production (`com.streammate.tv`),
  debug and Lab packages. Elsewhere the feature object is not even constructed.
- ADDON-FR-02 Access check `allowed(profile)` = package allows addons AND `profile` is the
  active profile AND the active profile has no restriction (a restriction is any non-empty
  allowed-group set for Live, Movies or Series, [Profiles](04-profiles-parental.md)).
- ADDON-FR-03 Every repository operation (list, install, refresh, enable, remove, reorder,
  browse, details, sources, subtitles, search, progress, Library, catalog order and visibility,
  Stremio copy, import) checks access **before** and **after** each network or storage step.
  A failed check raises ACCESS_DENIED and discards the result (a late response can never be
  shown or cached for a profile that lost access).
- ADDON-FR-04 Every addon request is also bound to the installation's **revision**: if the
  installation was disabled, removed or changed while the request was in flight, the result is
  dropped with CONFLICT or NOT_FOUND.
- ADDON-FR-05 The Discover screen shows "Working…" (`addon_loading`) until preferences are
  read; for a restricted profile it shows `addon_access_denied` ("Addons are unavailable for
  restricted profiles.") and a **Back to home** button (`addon_back`).

### 4.2 Addon URLs (endpoints)

- ADDON-FR-06 Parsing a configured URL (input trimmed):
  - rejected (INVALID_URL) when longer than 16,384 characters, containing control characters
    or a backslash, not an HTTP(S) URL, containing user info, or containing a `#fragment`;
  - `stremio://…` is rewritten to `https://…`;
  - plain `http://` is rejected with INSECURE_URL. The protocol layer has an explicit opt-in
    flag for HTTP, but **no UI path in beta 23 sets it**, so only HTTPS addons can be installed;
  - if the last path segment is `manifest.json` the URL is used as is;
  - otherwise `manifest.json` is appended when: the path ends in `/`; or the path ends
    `/stremio/<UUID>` (UUID `[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}`,
    AIOMetadata's configured base); or (install and import paths, "base URL allowed") the last
    segment is not `configure` or `install` and does not look like a file name
    (`.*\.[a-zA-Z]{1,8}`). Anything else is INVALID_URL;
  - the configured query string and path are preserved exactly (they carry the configuration).
- ADDON-FR-07 Endpoint identity: the **fingerprint** is SHA-256 (hex) of the full normalised
  URL. Installations are unique per profile + fingerprint. The same manifest id with a different
  configuration is a different installation.
- ADDON-FR-08 Resource URLs are built by replacing the final `manifest.json` segment with
  `<resource>/<type>/<id>` and, when extras exist, one more segment `k1=v1&k2=v2` (keys sorted,
  each value encoded as a URL query value so `/`, `&`, `+` and `=` stay inside the value), then
  appending `.json` to the last segment:

  ```
  https://addon.example/<config>/manifest.json
  → https://addon.example/<config>/catalog/movie/top/genre=Drama&skip=40.json
  → https://addon.example/<config>/meta/series/tt0000001.json
  → https://addon.example/<config>/stream/series/tt0000001:1:2.json
  → https://addon.example/<config>/subtitles/movie/tt0000001/filename=a.mkv&videoHash=0123456789abcdef&videoSize=1234.json
  ```

  Allowed resources: `catalog`, `meta`, `stream`, `subtitles` (`addon_catalog` is accepted by
  the builder but unused). Type ≤ 256 and id ≤ 2,048 characters, neither `.` nor `..`; at most
  32 extras, key 1–128 characters, value ≤ 8,192. Violations fail with INVALID_REQUEST before
  any network call.

### 4.3 Manifest

- ADDON-FR-09 Envelope limits before parsing: body ≤ 2 MiB (characters and UTF-8 bytes) else
  RESPONSE_TOO_LARGE; JSON nesting depth ≤ 64 else INVALID_MANIFEST.
- ADDON-FR-10 Required: `id`, `version`, `name` (non-blank strings ≤ 8,192), `types` (string
  array), `resources` (array ≤ 32). Optional: `idPrefixes` (strings), `catalogs` (array ≤ 512),
  `behaviorHints` (object). Any malformed field makes the whole manifest INVALID_MANIFEST.
  String arrays ≤ 2,048 entries, each a non-blank string ≤ 8,192.
- ADDON-FR-11 A resource is either a string name (inherits the manifest's `types` and
  `idPrefixes`) or an object `{name, types?, idPrefixes?}` (its own types, else the manifest's;
  its own prefixes only — an object resource without `idPrefixes` does **not** inherit the
  manifest's).
- ADDON-FR-12 Catalog: `id` (required), `type` (required), `name` (defaults to id),
  `extra` (array ≤ 32 of `{name, isRequired?, options?, optionsLimit?}`) or, when `extra` is
  absent, legacy `extraSupported` + `extraRequired` name lists; `pageSize` (int 1–10,000);
  `showInHome` (default true). Extra names must be unique within a catalog; `(type, id)` must be
  unique across catalogs. `optionsLimit` is parsed (default 1) but only one value per extra is
  ever sent.
- ADDON-FR-13 `behaviorHints.configurationRequired: true` → install refused with
  CONFIGURATION_REQUIRED ("Configure this addon on its provider's page first, then install the
  configured manifest URL."). `configurable` is parsed and ignored. Other manifest fields
  (description, logo, background, contactEmail, …) are ignored; unknown fields never break
  parsing.
- ADDON-FR-14 Capability check `supports(resource, type, id)`:
  - `catalog`: the manifest has a `catalog` resource AND a catalog with that exact type and id
    (catalog ids are not media ids: `idPrefixes` never filter them);
  - other resources: some resource with that name lists the type AND (no prefixes declared OR
    the id starts with one of them).
  Requests the manifest does not support fail with UNSUPPORTED_RESOURCE without network.
- ADDON-FR-15 Catalog extras validation before any catalog request: every key sent must be a
  declared extra; every required extra must have a non-blank value; `skip` must be a
  non-negative integer. Otherwise INVALID_REQUEST.

### 4.4 Catalog, meta, stream and subtitle responses

- ADDON-FR-16 Catalog (`{"metas":[…]}`): `metas` must be an array of ≤ 1,000 entries (else
  RESPONSE_TOO_LARGE). Each entry needs `type`, `id`, `name`; a malformed entry is skipped, not
  fatal. Entries are de-duplicated by (type, id) and at most `itemLimit` models are built
  (20 for home shelves, 100 for Search, 1,000 for grids). The **raw** array size is kept as
  `receivedCount` for paging.
- ADDON-FR-17 Preview fields used: `poster`, `posterShape` (poster/square/landscape, default
  poster; parsed but every card is drawn 2:3), `background`, `logo`, `description` (≤ 32,768),
  `releaseInfo`, `genres` (first 12, each ≤ 128), `runtime` (≤ 128), `imdbRating` (numeric
  string 0–10, ≤ 16 characters), `behaviorHints.defaultVideoId`. Image fields must be absolute
  HTTP(S) URLs without user info or fragment, else dropped. Text fields must be non-blank and
  within their limit, else dropped. Catalog previews never parse `videos` or cast.
- ADDON-FR-18 Meta (`{"meta":{…}}`): same fields plus `videos` (≤ 10,000; each needs `id`;
  `title` or `name` or the id as its title; `season`, `episode` non-negative ints; `overview`
  ≤ 32,768; `thumbnail`), de-duplicated by video id, and cast (FR-19).
- ADDON-FR-19 Cast, in this order of sources: `app_extras.cast` (AIOMetadata rich objects),
  `credits_cast`, `cast` (objects `{name, photo|profile_path, character}` or plain strings),
  then `links` entries whose `category` is `actor`, `actors` or `cast` (names only; link URLs
  are never followed). At most 240 entries are scanned per source, at most 60 people kept,
  merged by lower-cased name (a later source fills a missing photo or character). Name ≤ 256,
  character ≤ 512.
- ADDON-FR-20 Playability: a title is directly playable ("single video") when its meta has
  `behaviorHints.defaultVideoId` (that id is the video) or its type is `movie` (the title id is
  the video). A series needs an explicit episode video id; ids are opaque and case-sensitive and
  are **never** guessed or rewritten.
- ADDON-FR-21 Stream (`{"streams":[…]}`, ≤ 500): kind is decided per stream:
  - `url` present and safe → **HTTP** (playable);
  - else `infoHash` → TORRENT; else `externalUrl` → EXTERNAL; else an unsafe `url`, `ytId`,
    `nzbUrl`, `rarUrls` or `zipUrls` → UNSUPPORTED; otherwise the entry is ignored.
  A URL is unsafe when it has control characters, a backslash, user info or a fragment, or its
  host is `localhost`, `*.localhost`, `::1`, `127.*` or `0.0.0.0` (Stremio's local streaming
  bridge does not exist in Sohva).
  Fields: `name` (≤ 512, default "Stream"), `description` or else `title` (≤ 8,192),
  `behaviorHints.proxyHeaders.request` (request headers), `behaviorHints.videoHash` (exactly 16
  hex characters), `behaviorHints.videoSize` (non-negative), `behaviorHints.filename` (≤ 1,024,
  no control characters), inline `subtitles` (≤ 1,000). Other hints (`notWebReady`,
  `bingeGroup`, …) are ignored.
- ADDON-FR-22 Stream request headers: ≤ 32 headers, names matching the HTTP token pattern
  ``[!#$%&'*+.^_`|~0-9a-zA-Z-]+`` (≤ 128), unique case-insensitively, not one of `host`,
  `connection`, `content-length`, `transfer-encoding`, `te`, `trailer`, `upgrade`, `keep-alive`,
  `proxy-authorization`, `proxy-authenticate` or `sec-*`; values printable ASCII ≤ 8,192. If any
  header is unsafe, or `behaviorHints`/`proxyHeaders`/`request` is present but not an object,
  **the whole stream is dropped** (silently dropping auth would be wrong).
- ADDON-FR-23 Subtitles (`{"subtitles":[…]}`, ≤ 1,000; also inline in streams): each needs
  `id` (≤ 1,024), `lang` (≤ 128) and a safe `url`; malformed entries are skipped; duplicates by
  (id, lang, url) removed.
- ADDON-FR-24 Metadata identity: a meta response may carry a different canonical id (for
  example an IMDb id for a TMDB request). The response is kept as returned, but caching, UI
  selection, Library and history are keyed by the **original request** key. A meta response
  whose type differs from the requested type is rejected (a movie never becomes a series).

### 4.5 Addon HTTP client

- ADDON-FR-25 One dedicated client for all addon JSON requests (never shared with IPTV: no IPTV
  auth, cookies, interceptors or logging): at most **4 concurrent requests** app-wide (FIFO
  semaphore); connect, read and whole-call timeouts **15 s**; redirects **not followed** (3xx →
  REDIRECT, "This address redirects. Install the provider's final HTTPS manifest URL."); no
  automatic retry on connection failure; header `Accept: application/json`.
- ADDON-FR-26 Body limit 2 MiB: a declared `Content-Length` above it fails at once; otherwise
  the body is read in 8 KiB chunks and fails as soon as it passes the limit (after
  decompression). Timeouts map to TIMEOUT, other I/O to NETWORK, non-2xx to HTTP_ERROR with the
  status code.
- ADDON-FR-27 Cache hints from the response: `max-age` (seconds, if ≥ 0), `no-cache` (treated as
  max-age 0), `no-store`, and `staleAllowed = not must-revalidate and not no-cache`.
- ADDON-FR-28 Cancellation of the caller cancels the HTTP call and frees the permit. Errors
  never carry URLs or bodies (exceptions are rebuilt with only the failure kind and status).

### 4.6 Installation store and management

- ADDON-FR-29 Install: normalise the URL; if an installation with the same fingerprint exists
  for this profile, return it unchanged (enabled state and position untouched); else fetch the
  manifest, refuse CONFIGURATION_REQUIRED, re-check access, and store: new random UUID
  installation id, enabled, position = last + 1, revision 1. At most **128 installations per
  profile** (INVALID_REQUEST beyond).
- ADDON-FR-30 Refresh: re-fetch the manifest of an existing installation; replace the stored
  manifest even if the advertised version is unchanged; the write succeeds only if the revision
  is still the one read before the request (a stale response cannot overwrite a newer change or
  resurrect a removed addon). A refused write shows the generic operation error. A failed
  refresh keeps the last good manifest.
- ADDON-FR-31 Enable/disable, reorder and refresh each **increment the revision**. Remove
  deletes the row. Reorder must name exactly the profile's current set of installations
  (CONFLICT otherwise).
- ADDON-FR-32 Priority: "Priority ↑" swaps the installation with the one above it. Priority
  (installation position) orders: source providers on title pages, subtitle providers,
  metadata fallback candidates, Trakt title lookups, and the default catalog order before the
  viewer customises it. It is independent of catalog order (§4.8).
- ADDON-FR-33 Every management action runs one at a time (a second press while busy is
  ignored); after it the list reloads. A failure shows its message (§4.20) under the header.
  **Reload saved addons** only re-reads the stored list (no network). An addon row's **Catalogs**
  button (enabled for an enabled addon with catalogs) opens a list headed by **Back to addons**
  and the addon's name, with one button "<catalog name> · <Movie|Series|type>" per catalog —
  including hidden and required-choice catalogs — each opening that catalog's grid (§4.17);
  Back returns to the list, then to Addons & setup.

### 4.7 Import (URL lists, file, phone, Stremio, Nuvio)

- ADDON-FR-34 URL-list rules (all methods feed one pending list): UTF-8 only (malformed or
  unmappable input rejected), a leading U+FEFF byte-order mark removed, CRLF/LF/blank lines
  accepted, no NUL, ≤ **256 KiB** (262,144 bytes; a stream is read at most one byte past the
  limit even if its size is unknown), **1–32 non-blank lines**. Each non-blank line is one
  configured URL; line numbers (1-based, counting blank lines) identify entries.
- ADDON-FR-35 Manual URL: "Add URL to list" appends the trimmed field to the pending list if the
  combined list still meets FR-34, else "The list is limited to 32 URLs and 256 KiB." The field
  is masked (password transformation), ≤ 16,384 characters, and cleared after adding.
- ADDON-FR-36 From a file: the system document picker (`OpenDocument`, MIME `text/*` and
  `application/octet-stream`) returns a `content:` URI; the file is read once off the main
  thread; no persistent permission, no file-name query, no copy on disk. The result replaces the
  pending list. No addon is contacted until Preview. No picker available →
  "No document picker is available. Choose a text file on your phone or use Manual URL instead."
- ADDON-FR-37 Phone: "Set up from phone" or "Choose file on phone" opens the phone screen
  (§4.7.1). The received list replaces the pending list and returns to Import.
- ADDON-FR-38 Nuvio JSON: the picker (MIME `application/json`, `text/*`,
  `application/octet-stream`) reads a document that is either a JSON array (Nuvio's local
  `/api/addons`) or an object with an `addons` array (`/api/state`). Only each entry's `url`
  string is taken, in order; at most 32 entries; the file obeys the 256 KiB and depth-64 limits.
  An entry without a usable `url` becomes a placeholder line that fails in preview with
  "Use a configured HTTPS or stremio URL" (positions are kept, nothing is silently dropped).
  Names, descriptions, layout, accounts and history are ignored.
- ADDON-FR-39 Stremio account copy (§4.7.2) produces a URL list of the account's addons
  (`transportUrl` values), which replaces the pending list with
  "Copied N entries. Preview and confirm to install." (plural) or
  "The Stremio account has no addons."
- ADDON-FR-40 Preview ("Preview import", enabled when the pending list is non-blank): the
  pending list is taken out of the field state, then each line is processed **in order, one at
  a time**: parse (base URL allowed) → DUPLICATE_INPUT if an earlier line had the same
  fingerprint → ALREADY_INSTALLED if the profile has it → fetch and parse the manifest →
  CONFIGURATION_REQUIRED is a failure → READY. A failing line never stops the others. All READY
  lines start selected. Statuses and values shown per row "Addon N" (no URL, no manifest name —
  both are untrusted or secret):

  | Status | Subtitle | Value |
  |---|---|---|
  | READY, selected | Ready to install | Include (check icon) |
  | READY, not selected | Not selected for this import | Skip (close icon) |
  | ALREADY_INSTALLED | Already installed — unchanged | Kept (lock) |
  | DUPLICATE_INPUT | Duplicate in this list — skipped | Skipped (lock) |
  | FAILED, configuration required | Configure on the provider's page first | Unavailable |
  | FAILED, bad/insecure URL | Use a configured HTTPS or stremio URL | Unavailable |
  | FAILED, redirect | Use the provider's final manifest URL | Unavailable |
  | FAILED, other | Unavailable provider, invalid response or changed access | Unavailable |
  | INSTALLED (after commit) | Installed | Added |

  OK on a READY row toggles it. Read-only rows are still focusable so a long list can be read
  with the D-pad.
- ADDON-FR-41 Commit ("Install N new addons", enabled when N ≥ 1 selected): installs the
  selected READY entries in list order, reusing the manifest fetched at preview (no second
  request); an entry that meanwhile became installed reports ALREADY_INSTALLED. The preview
  (which holds URLs and manifests in memory) is dropped after commit. "Start another list"
  clears everything. Any unexpected failure during a step shows
  "Import could not be completed. Use a supported UTF-8 file, up to 256 KiB and 32 addons, or try again."
- ADDON-FR-42 Nothing entered on these screens is kept in saved-instance state, preferences or
  logs; leaving Import cancels a running preview and discards pending input.

#### 4.7.1 Phone setup session (protocol: [Phone setup](11-phone-setup.md))

- ADDON-FR-43 Opening the phone screen starts a one-use server on the TV's first site-local
  IPv4 address (first up, non-loopback interface) on a random port. No address →
  "No available local connection. Return and start phone setup again."
- ADDON-FR-44 The QR (384 px bitmap drawn at 220 dp) and the text show
  `http://<ip>:<port>/#<token>`: a 256-bit random token (64 hex) carried in the fragment, so it
  never appears in HTTP requests, referrers or saved URLs. The page moves it to memory and
  removes it from the address bar.
- ADDON-FR-45 Server rules: `GET /` serves the page; `POST /submit` requires exact `Host`,
  `Origin` equal to the TV origin, `Authorization: Bearer <token>` (constant-time compare),
  `Content-Type: text/plain`, `Content-Length` 1–262,144, no `Transfer-Encoding`; headers
  ≤ 8,192 bytes; 5 s socket timeout and 10 s per request; backlog 4; everything else 403/405/400.
  The body must pass FR-34. The first valid submission ends the session ("Sent. Review and
  confirm on your TV."), invalid ones do not consume it. Session lifetime **10 minutes**; leaving
  the screen or the app going to the background (ON_STOP) closes the server and any open
  connection. Responses carry `Cache-Control: no-store`, `Referrer-Policy: no-referrer`,
  `X-Content-Type-Options: nosniff` and a CSP allowing only the page's own inline script by hash.
- ADDON-FR-46 The phone page (English only) offers a local `.txt` file chooser (read on the
  phone, never uploaded until Send) and a masked textarea; Send to TV; Clear list. It warns that
  local HTTP is unencrypted. After receipt the TV closes the session and returns to Import with
  the list queued; "Session ended. Return to start a new one." when the server stopped.

#### 4.7.2 Stremio account copy

- ADDON-FR-47 Nothing starts until **Start Stremio authorization** is pressed. The flow uses
  only fixed official origins `https://link.stremio.com/` and `https://api.strem.io/`, a
  dedicated client (15 s timeouts, no redirects, no retries, ≤ 2 MiB bodies, depth ≤ 64,
  `Accept: application/json`, `Cache-Control: no-store`):
  1. `GET link.stremio.com/api/create?type=Create` → `result.code` and `result.link` (older
     responses have them top level). The code must be 4–128 ASCII letters/digits and the link
     exactly `https://link.stremio.com/<code>` (port 443, no query, fragment or user info);
     anything else fails. The returned remote QR image is never downloaded.
  2. The TV shows its own QR of that link (384 px bitmap at 200 dp) and the link as text.
  3. Every **3 s** (first read 3 s after the link appears):
     `GET link.stremio.com/api/read?type=Read&code=<code>`. A response with `error.code == 101`
     and no (or JSON null) `result` means "still waiting"; `"result": null`, or a result with
     `success: false` and no `authKey`, also means waiting. Any other error, or a missing
     `result`, fails.
  4. On authorization: `POST api.strem.io/api/loginWithToken` `{"type":"LoginWithToken","token":<result.authKey>}`
     → `result.authKey`; then `POST api.strem.io/api/addonCollectionGet`
     `{"type":"AddonCollectionGet","authKey":<authKey>,"update":false}` → `result.addons[].transportUrl`.
  5. Tokens live only in local variables: never stored, logged or sent to an addon. No logout
     or revoke call is made (discarding the token is not a server-side revocation).
- ADDON-FR-48 Limits: the attempt ends after **10 minutes** (TIMEOUT), on Back, when the app
  leaves the foreground (ON_STOP: "Authorization stopped while the app was in the background.
  Start again to get a new QR code."), or on a profile/access change. A fresh attempt always
  creates a new link. The window is marked `FLAG_SECURE` (no screenshots/recents image) while
  this screen is shown, and restored afterwards.
- ADDON-FR-49 Errors: TIMEOUT → "Authorization expired or timed out. Start again when you are
  ready."; more than 32 addons or oversized → "The account list exceeds the import limit. Use a
  smaller URL list instead."; ACCESS_DENIED → "This profile can no longer import addons.";
  anything else → "Stremio authorization could not be completed. Try again or use a URL list."
  Stremio's own error messages are never shown.
- ADDON-FR-50 Copy only: the source account's addons, Library, history and settings are not
  changed; catalog layout and history are not copied.

### 4.8 Catalog order and visibility

- ADDON-FR-51 Catalog identity = SHA-256 over (installation id, catalog type, catalog id)
  (length-prefixed parts, see §6). It survives manifest refresh and priority changes.
- ADDON-FR-52 Ordering: all catalogs of all installations, in installation priority then
  manifest order, stably sorted by the saved order; catalogs not in the saved order keep their
  natural order after the saved ones (new catalogs append). Disabled addons' catalogs keep their
  positions. Only 64-hex keys are read back; malformed or duplicate stored keys are ignored.
- ADDON-FR-53 Saving an order requires exactly the current set of catalog keys (CONFLICT
  otherwise: "Order was not saved. Place again to retry, or cancel and reopen if your addons
  changed."). Writes are serialised and committed synchronously.
- ADDON-FR-54 Visible catalogs = ordered catalogs of **enabled** installations whose key is not
  hidden. Visibility is a separate hidden-key set per profile; toggling one catalog keeps other
  choices, drops keys of catalogs that no longer exist, and rejects unknown keys (CONFLICT →
  "Visibility was not saved. Try again, or reopen this page if your addons changed."). Showing a
  catalog never enables its addon. Hiding hides browsing only: the addon's metadata, stream and
  subtitle capabilities, Continue watching and Library are unaffected. Every catalog may be
  hidden.
- ADDON-FR-55 Organise screen ("Catalogs"): two modes, **Reorder** and **Show / hide**.
  Reorder: OK picks a catalog up (row shows "Moving · N"); Up/Down move one place; Page Up/Down
  move by (visible rows − 1, at least 1); Home/End move to first/last; held keys repeat natively;
  Left/Right are swallowed; OK (first press, not repeats) places and saves **once**; Back cancels
  without writing. Navigation keys never write. The list scrolls only when the moving row would
  leave the viewport, and focus follows the moving row. A failed save keeps the draft for retry.
  Show / hide: one switch per catalog, disabled while saving.
- ADDON-FR-56 Each row's supporting line: "<addon name> · <Movie|Series|raw type> · <state>",
  state = "Addon disabled", "Hidden", "Home" (it appears as a Discover home shelf) or
  "Discover" (it needs a choice, so it is only on the filter page).

### 4.9 Discover home (landing)

- ADDON-FR-57 On entry the landing loads (no network): the profile's installations, catalog
  order and hidden set. It rebuilds its shelves only when any of these changed (ids + revisions,
  order, hidden set); then it clears shelf state and scrolls to the top.
- ADDON-FR-58 Shelves: one per visible catalog that **belongs on the landing**: `showInHome`
  is true AND it has no required extra other than `skip`. Catalogs with optional filters stay
  on the landing (requested without extras). Catalogs with a required choice appear only on
  the Discover filter page. Shelf title = catalog name (no addon/type attribution).
- ADDON-FR-59 Loading ownership: the active row and the **next two** rows are loaded, each by
  its own effect keyed by the row (moving down does not cancel the row that becomes active).
  Before any row is focused, rows 0–2 load. Lazy-list precomposition never owns loading.
  Nothing is fetched for other rows; nothing loads metadata, streams or subtitles.
- ADDON-FR-60 Loading a shelf (per-shelf mutex, skipped when already validated or failed):
  1. Home preview from cache (no network): an entry is usable only if stored ≤ **24 hours**
     ago, not future-dated, and fresh or stale-allowed; at most 20 titles are built. It is shown
     at once; if still fresh the shelf is done.
  2. Network request (`itemLimit` 20); publish; mark stale if the response was a stale fallback
     ("Showing saved titles; provider unavailable.").
  3. On an error the provisional preview is removed and the error message shown in the shelf.
  When a refreshed shelf replaces the one being browsed, the focused title stays focused by
  identity; if it disappeared, focus moves to the shelf's first card.
- ADDON-FR-61 While a shelf loads it shows 6 poster-shaped placeholders (surface colour, medium
  shape); the first is focusable (focus ring, no scale) so the D-pad never gets stuck, with
  content description "Loading <catalog>". A loaded empty shelf says "No titles returned."
  Once loaded or failed, a **Show all** button follows the last card (it is the first focus
  target when the shelf is empty).
- ADDON-FR-62 Shelf state is retained for at most **24 shelves** (least recently used evicted);
  an evicted shelf reloads from the encrypted cache when revisited (no network if fresh).
- ADDON-FR-63 Poster warm-up: the first 6 distinct posters of the row below the active one are
  pre-decoded with 2 workers at the card's decode size. Nothing else is prefetched.
- ADDON-FR-64 Continue watching (always the first row, cannot be moved or hidden): after the
  installations load and any pending progress write finishes, read the profile's history
  (§4.14), keep entries with a resume position > 0 whose metadata installation is enabled,
  distinct by (installation, media key), **first 20**. Each card's artwork and facts come from
  cached metadata (stale allowed, no network), else the saved artwork snapshot. Progress bar =
  position / duration. The row reloads every time the landing is shown again (for example after
  playback). While loading its only item is a button "Loading watch history…"; after a failed
  read "History unavailable"; when empty "Nothing to continue yet". OK on that button moves to
  the first shelf.
- ADDON-FR-65 Artwork repair (once per Discover visit): after the history renders, entries still
  without a poster are repaired: at most 20, **2 workers**, **8 s** per title, one attempt per
  title per visit, through the normal details route (FR-72). A found poster is written into the
  saved progress entry (artwork only; position, completion and order untouched; never creates or
  revives a removed entry). Leaving the landing cancels the repair. Cached metadata found during
  the history read is used the same way without network.
- ADDON-FR-66 Opening a Continue card opens the title page with the saved video pre-selected
  (series: the saved episode's page), where **Continue watching** is offered.
- ADDON-FR-67 Key handling on the landing:
  - Down/Up inside a shelf are intercepted: they move to the next/previous shelf's **first**
    card (the new row scrolls to its start and the list aligns that shelf's heading to the top
    of the shelf area). Spatial search is never used between rows.
  - Up from the first shelf goes to the Continue row. Up on the Continue row does nothing;
    Down goes to the first shelf.
  - Left on a row's first card (or on a loading placeholder, or on Show all of an empty shelf)
    moves focus to the rail's Home item.
  - OK on a card opens the title page; OK on Show all opens the grid.
- ADDON-FR-68 Rail: an overlay column on the left edge (does not move the shelves). Focus
  expands it (labels appear); focusing an item never opens it; OK opens. Right, Back, or OK on
  **Home** return focus to the shelves (last focused card; Continue row when that was active).
  Items, top to bottom: Home (`home_nav_home`, icon `ic_sohva_nav_addon_home`) · Library
  (`settings_section_metadata` "Library", `ic_sohva_nav_library`) · Search (`home_search`,
  `ic_sohva_nav_search`) · Discover (`addon_title` "Discover", compass `ic_sohva_nav_explore`) ·
  Addons & setup (`addon_ui_addons_setup`, puzzle `ic_sohva_nav_addons`) · flexible space ·
  Back to home (`addon_back` "Back to home", `ic_sohva_nav_back_to_home`, leaves Discover).
- ADDON-FR-69 Empty landing texts: no shelves but some visible catalogs →
  "Use Search for titles or Discover for filtered catalogs on the left."; no visible catalogs →
  "No visible catalogs. Open Addons & setup to show catalogs or add your services."; before the
  first read "Loading saved addons…".
- ADDON-FR-70 Hero (landing only). The hero describes the focused card:
  - focus on a loading placeholder or on the empty-history button: after 120 ms the hero clears
    (title "Discover", no facts); focus moving to the rail or a Show all button leaves the hero
    as it was;
  - focus on a Continue card: after 120 ms the hero shows that title from cached data only (no
    network);
  - focus on a shelf card, while the landing is RESUMED and the rail is not focused
    (synopsis resolver): wait **120 ms**; look up fresh cached metadata from the owner addon
    only (≤ **200 ms**); if it has a synopsis, show it and stop. Else, if this title failed
    within the last **30 s**, show the catalog preview text and stop. Else show the title with
    an **empty** synopsis (never the catalog-language text), wait **350 ms** more, then fetch
    details (FR-72) with an **8 s** deadline, **one lookup at a time** across the app; on a
    synopsis show it; otherwise record a failure (at most 64 remembered) and show the catalog
    text. Only the synopsis changes: title, artwork, logo and identity stay the catalog's.
    Moving focus cancels; a late response never overwrites a newer focus; revocation
    (ACCESS_DENIED, CONFLICT, NOT_FOUND) clears the hero.
- ADDON-FR-71 Trakt overlay on cards: movie posters show Trakt's progress bar or watched tick
  for key `movie:<media id>`; series posters show nothing (Trakt state is per episode); episode
  cards use `series:<series id>:<season>:<episode>`. See [Trakt](51-trakt.md).

### 4.10 Title details and metadata routing

- ADDON-FR-72 Details route (used by title pages, the hero, artwork repair and Trakt lookups):
  candidates = enabled installations supporting `meta` for (type, id), the catalog's own addon
  first then priority order, at most **3**. Each candidate gets **15 s**; the whole route
  **20 s**. Access denial, conflict and not-found stop the route; other failures try the next
  candidate. A response of another type is skipped (INVALID_RESPONSE). If every candidate fails
  the last failure is reported. A catalog-only addon (no meta resource) therefore still gets
  details from another installed metadata addon; if none exists a movie is still playable from
  its preview (FR-20).
- ADDON-FR-73 Cached-details lookup (no network) for heroes and history: the owner first, up
  to 3 candidates (1 when fresh data is required); fresh data required for the landing hero,
  stale-allowed data accepted for history artwork and Home's synopsis.
- ADDON-FR-74 The title page starts from the catalog preview and requests details on entry.
  "Retry details" / "Retry episode details" re-request with `refresh` (bypassing the fresh
  cache). A revocation leaves the page usable only for Back.
- ADDON-FR-75 Page type: if the loaded details (or the preview while loading) are single-video
  (FR-20) → **movie page**; else → **series page**. Opened with a saved video (Continue,
  history, Trakt next episode): once details load, the matching episode page opens directly (if
  the id is not in the video list, a page for that id with the series name as title).
- ADDON-FR-76 Series page: seasons = distinct `season` values sorted ascending with no-season
  entries after numbered seasons and **Specials (0) last**; chips read "Season N", "Specials" or
  "Episodes" (no season). The first season is selected unless the viewer chose one. Episodes of
  the selected season appear as cards labelled "S1 · E2 · <title>" (`addon_ui_season_short`,
  `addon_ui_episode_short`). When no seasons exist and loading ended: "Retry episode details".
  "Loading episodes…" while loading.
- ADDON-FR-77 Episode page = playable page for that video id with the episode title, its
  overview (or "No synopsis supplied by the addon."), and "Season N · Episode M" under the
  series name.
- ADDON-FR-78 The video key used for sources is built from the **loaded** details (type +
  default video id, movie id or episode id); watch progress and Library use the **original**
  catalog key (FR-24).

### 4.11 Sources and starting playback

- ADDON-FR-79 Sources are resolved only on a playable page, only after details finished loading
  (the right column says "Loading sources…" until then), and again every time the page is shown
  after playback (signed URLs are never reused). Resolution: every enabled installation that
  supports `stream` for the video emits a LOADING slot immediately (in priority order); each
  request completes independently (subject to the 4-request limit); results are shown grouped
  per provider in priority order regardless of arrival order. Per provider: name,
  "Working…", its error message, or "No sources returned.". No installation supports the
  title → "No enabled stream addon supports this title." While any is loading:
  "Finding sources…". Stream results are never written to the response cache.
- ADDON-FR-80 Source card: name (≤ 2 lines), description (≤ 5 lines), then "Play" or
  "Unsupported transport". Only HTTP sources start playback on OK.
- ADDON-FR-81 Scraper chooser ("Scraper: All" or a provider name) filters the shown providers
  without a new request. "Refresh" (top right of the column) re-resolves; ignored while loading.
- ADDON-FR-82 Primary action: **Continue watching** when a local resume position > 0 exists or
  a Trakt pause newer than the local entry exists; otherwise **Find sources**, which only moves
  focus to the sources column. **Start from beginning** appears when a local resume position
  exists. Continue and Start from beginning wait until **all** providers finished (so provider
  speed never changes the choice), then start the first HTTP source of the highest-priority
  READY provider (respecting the scraper filter); "Waiting for a playable source…" meanwhile;
  none → "No playable source in the current selection. Try another scraper or refresh sources."
  A request is consumed once (returning from playback does not start again).
- ADDON-FR-83 Choosing a source card directly plays it **with resume** (local position, or the
  newer Trakt fraction); Start from beginning plays from 0.
- ADDON-FR-84 Before playback and every 5 s during it the selection is re-validated: profile
  access, the source installation enabled with the same revision, `stream` still supported,
  HTTP kind with a URL, and the title's metadata installation still enabled.

### 4.12 Addon player

- ADDON-FR-85 Creating the player stops the IPTV playback service first and releases any other
  addon player; there is never more than one. Media3 ExoPlayer with decoder fallback enabled,
  seek back/forward increments 10 s, audio focus handling and pause on "becoming noisy".
  Track preferences: preferred audio languages = [primary audio, secondary audio]; preferred
  text languages = [primary subtitle, secondary subtitle] (normalised, FR-96). Text starts
  **disabled** so no unrelated default track flashes during automatic selection.
- ADDON-FR-86 Start-up sequence (the loading screen is shown throughout; stage text in quotes):
  1. "Preparing stream…": access check; begin a progress session; begin a Trakt session;
     choose the start position (FR-88); prepare **paused**. Wait for READY or error, at most
     **45 s** (else TIMEOUT).
  2. "Fetching subtitles…": automatic subtitles (FR-98) with a **5 s** budget; on timeout or
     error use the embedded fallback (FR-99).
  3. "Starting playback…": wait for the first rendered frame (or READY for audio-only
     streams), at most **20 s**.
  4. Re-check access, then play — unless the subtitle or audio picker is open, in which case
     play when it closes.
  A failure at any step ends start-up with the error state (FR-91). Start-up milestones
  ("stream-ready", "subtitles-ready", "first-frame", "playing", ms since start) go to the
  diagnostics log with no titles or URLs.
- ADDON-FR-87 Loading screen buttons: **Cancel** (initially focused; leaves the player) and
  **Subtitles** (pauses and opens the picker; start-up continues underneath). During start-up
  only Back/Cancel act; media keys are swallowed.
- ADDON-FR-88 Start position: not resuming → 0. Resuming → if Trakt has a pause fraction for
  this title/episode that is newer than the local entry (or there is no local entry), start at
  0 and seek to duration × fraction as soon as READY reports a duration; else the local resume
  position (0 when completed).
- ADDON-FR-89 Keys once playing (controls from [Player](30-player.md)): Play/Pause media key
  toggles; Rewind / Fast-forward keys and, while the controls are not focused, D-pad Left/Right
  seek by the playback seek step preference (default 10 s) through the shared seek ladder
  (10 s → 30 s → 60 s → 120 s, one rung per 3 presses in the same direction within 1,200 ms,
  never below the chosen step); OK/Enter/Up/Down with controls hidden show them and focus them.
  Controls hide after **5 s** idle even when focused (addon player opts in to
  `autoHideWhileFocused`); any D-pad
  press restarts the timer; paused, buffering, failed, background-stopped and open pickers keep
  them visible. Controls: title, play/pause, rewind, forward, position/duration, audio (label =
  selected track or "Auto"), subtitles (label = selected language or "Off"), aspect cycling
  Fit → Zoom → Fill. The controls stay composed (only made transparent) while the subtitle
  dialogs are open, so their state and Back behaviour survive.
- ADDON-FR-90 Title shown: movie → title; episode → "<series> · S1 · E2 · <episode title>".
- ADDON-FR-91 Failure states: a player error, a start-up failure or an access loss shows
  "Playback stopped. Retry or choose another source." with **Retry with fresh source**. Going to
  the background (ON_STOP) saves progress and stops the player; on return it shows "Playback
  stopped while the app was in the background." with the same button — nothing restarts
  unattended. Retry re-resolves the same provider's streams and accepts exactly one match with
  the same name and the same `filename` (or, without a filename, the same description), then
  prepares at the current position keeping the subtitle choice and timing. No match or several
  → CONFLICT (the viewer must choose another source). A different provider or quality is never
  chosen silently. "Watch progress could not be saved." appears if a progress write failed.
- ADDON-FR-92 Audio picker (shared track overlay, "Audio"): one row per supported audio track:
  language display name in the interface language (or the track label, or "Audio N"), plus
  "N ch" when known; "No audio tracks" when none. Choosing overrides the audio track.
- ADDON-FR-93 End of stream (first STATE_ENDED only; pause, buffering, errors and background
  stops never advance): progress is saved as completed, Trakt gets STOP at 100 %. Movie → back
  to its page. Episode → if **Continue to the next episode** (`autoPlayNextEpisodeEnabled`,
  default on) is set and a next episode exists (FR-94), that episode's page opens and
  automatically starts its first playable source **from 0**; otherwise back to the episode page.
  If the next episode has no playable source, its page stays with the "No playable source…"
  message.
- ADDON-FR-94 Next episode: none for movies or an unknown current id. Unnumbered current entry →
  the next entry in the provider's list order with a different id. Numbered → the smallest
  (season, episode) greater than the current, among numbered entries of the same kind (specials
  season 0 only advance within specials; regular seasons never enter specials); crosses season
  boundaries; an alternative copy of the same episode is not replayed.
- ADDON-FR-95 Media transport (per stream): a dedicated client, connect 15 s, read 20 s, no
  whole-call deadline (progressive video). GET only. Redirects 301/302/303/307/308 are followed
  manually, at most **5** hops, never from HTTPS to HTTP. The stream's request headers are sent
  **only to the initial origin** (same scheme, host and port), including HLS/DASH child
  requests; `Authorization`, `Cookie` and `Referer` are always stripped, and no header from a
  previous hop is inherited. Transport errors surface as a generic media error (no URL).
  Media3 picks HLS/DASH by URL extension only (extensionless adaptive streams are not
  recognised — known limit).

### 4.13 Subtitles

- ADDON-FR-96 Language normalisation: lower-case, cut at `-`/`_`; empty, `und`, `unknown`,
  `off`, `none` mean no language; ISO 639-2 codes map to 639-1 (explicit table: fin→fi, eng→en,
  swe→sv, dan→da, nor/nob/nno/nb/nn→no, est→et, deu/ger→de, fra/fre→fr, spa→es, ita→it,
  nld/dut→nl, por→pt, pol→pl, ces/cze→cs, ell/gre→el, ron/rum→ro, zho/chi→zh, jpn→ja, kor→ko,
  ara→ar, rus→ru, ukr→uk, tur→tr, hun→hu, hrv→hr, srp→sr, slk/slo→sk, slv→sl, bul→bg, heb→he;
  else the platform's ISO3→ISO1 table; else unchanged). Language names are shown in the
  interface language ("Unknown language" when absent).
- ADDON-FR-97 Picker (opened from the controls or the loading screen; player pauses; focus on
  "Back to player"). On open it requests subtitle results once per playback (FR-100). Options:
  embedded text tracks (provider "Embedded", detail = track label or "Track N"), the stream's
  inline subtitles ("Stream subtitle", "Option N"), and each subtitle addon's results (provider
  name, "Option N"). Visible options = those in the preferred languages, or all when **Show all
  languages** is on. Left column: "Subtitles off", then languages (preferred order first, then
  by display name) with counts; right column: the active language's options. Choosing loads and
  applies it, then closes. Bottom row: "Subtitle sync · +0.000 s" (enabled only for parsed-cue
  tracks), "Show all languages" / "Primary / secondary only" (persisted, global), "Refresh"
  (re-query subtitle addons). Texts: "Finding subtitles…", "Checking subtitle providers…",
  per-provider errors "<provider>: <message>", "Choose subtitle languages in Sohva Settings, or
  show all languages below." (no preferences, show-all off), "No matching subtitles available.",
  "Timing adjustment is unavailable for this subtitle format.", "Loading selected subtitle…".
  Option keys are session-only hashes; no subtitle id or URL is ever shown or put in semantics.
- ADDON-FR-98 Automatic selection, one bounded attempt per playback or retry (not per buffering
  event), cancelled by any manual choice (opening the picker without choosing is not a choice):
  1. no preferred subtitle languages → text stays off;
  2. the primary **audio** preference is available among the audio tracks → text off;
  3. primary subtitle language among embedded tracks → enable embedded text (Media3 picks it);
  4. try primary-language candidates: stream inline subtitles first, then addon results as they
     arrive (each provider result is tried immediately, so a fast provider never waits for slow
     ones); at most 2 download attempts per language, each candidate once;
  5. then each preferred language in order: embedded → enable; else addon candidates;
  6. nothing → text off.
- ADDON-FR-99 Embedded fallback (budget exceeded or error): text on only if a preferred language
  exists among embedded tracks and audio does not suppress it; a manual choice or an already
  loaded addon subtitle is left alone.
- ADDON-FR-100 Subtitle requests go to every enabled installation supporting `subtitles` for the
  video, with only the stream's `videoHash`, `videoSize` and `filename` as extras (never stream
  headers). Results arrive progressively, sorted by priority, and are never cached. Subtitle
  requests are made only after the stream is READY (automatic selection) or when the picker
  opens — never on title pages or Home (some providers use subtitle requests for their own watch
  tracking).
- ADDON-FR-101 Subtitle download: only for an explicit or automatic choice; its own client (media
  transport rules without stream headers, **20 s** call timeout); ≤ **4 MiB** after
  decompression; UTF-8 with the byte-order mark removed; format by content: `WEBVTT` prefix →
  WebVTT; `[Script Info]` prefix and `[Events]` → SSA/ASS; an SRT timing line
  (`(?m)^\d{2,}:\d{2}:\d{2}[,.]\d{3}\s*-->`) → SubRip; anything else (including ZIP archives) is
  rejected. Kept in memory only (never a temp file); merged with the video as a side-loaded
  subtitle source with a seek map; provider access is re-checked after the download.
- ADDON-FR-102 Subtitle sync panel (upper screen, video stays visible): the wide adjustment
  control has focus; Left/Right change the draft by **0.1 s**, by **1 s** when the key repeat
  count ≥ 8 (held); bounds **±60 s**; positive = later. Label format `%+.3f s`. Down goes to
  **Apply** (explicit focus link), Up from the buttons returns. OK on the adjustment or Apply
  applies the draft once (re-prepares at the current position; may briefly buffer; the
  downloaded subtitle is reused). Reset to zero sets the draft to 0 (Apply commits it).
  Play/Pause previews; Back returns to the picker (paused). "Not applied · Current: <x>" when
  draft ≠ applied; "Applying timing…"; "Could not apply timing. Return to playback and retry."
  if the player does not come back within 20 s.
- ADDON-FR-103 Timing applies to the current playback and track only: it survives seeks and a
  fresh-source retry; any other subtitle choice or Off resets it to 0; it is never stored.
  Mechanism: cue timestamps are shifted as they are parsed (before Media3's sample queues, so
  seek filtering uses shifted times); for embedded tracks with a positive delay, extractor seek
  maps read from `delay` earlier in the file so delayed cues are found after a seek. Video and
  audio timestamps are never changed. Only tracks decoded to Media3 cues support timing
  (bitmap and other formats say so).
- ADDON-FR-104 Subtitle appearance (size, colour, background) follows the VOD subtitle settings
  ([Player](30-player.md)); there is no Discover-specific style editor.

### 4.14 Watch progress and history

- ADDON-FR-105 Identity of a progress entry: (profile, metadata installation, title type, title
  id, video type, video id) — the catalog's original keys. Payload (encrypted): title, position,
  duration, updated time, completed flag, artwork snapshot (name, poster, background). Never a
  stream URL, header or subtitle URL.
- ADDON-FR-106 A playback session writes a snapshot: every **5 s** while the player runs, on
  every pause (isPlaying → false), at the end, on a player error, on background stop and on
  release. Snapshots carry an increasing sequence number; a snapshot from an older session or
  with a lower sequence is ignored (a late write can never overwrite newer progress). Starting a
  new playback waits for the previous pending write. Writes run on an app-lifetime background
  scope so the last snapshot survives screen disposal.
- ADDON-FR-107 Rules: position clamped to duration when known; unknown duration keeps the
  previous known duration; completed when the stream ended or position ≥ **95 %** of duration;
  completed titles resume from 0; durations above 7 days are rejected. At most **200 entries per
  profile**; the oldest by update time are pruned.
- ADDON-FR-108 Watch history page (Addons & setup → Watch history): every entry whose metadata
  installation is enabled, newest first: a button with the title (opens the title page on that
  video), "Watched" or "N min S sec", and **Forget progress** (removes the entry). Empty:
  "No addon watch history yet."
- ADDON-FR-109 Home feed: Home observes the profile's ≤ 200 entries and shows those not
  completed with a resume position > 0 ([Home](02-home.md) merges them with VOD and Trakt,
  deduplicates movies by IMDb/TMDB id, one card per series). Home's hero synopsis for such a card
  is read from cached details only (episode overview, else title description).

### 4.15 Library

- ADDON-FR-110 "Add to library" / "Remove from library" (icon Library / check, selected when
  saved) on movie pages (next to the primary action) and series pages (before the season chips);
  only for types `movie` and `series`; episodes are not bookmarked. The state is read on entry
  (button disabled until known; a failed read shows "Retry library"). Saving stores identity
  (installation id + original type + id), artwork snapshot, release info (≤ 256) and the added
  time; re-adding keeps the original added time. At **1,000 titles** a new addition is refused
  ("Library is full (1,000 titles). Remove a title first."); nothing is ever evicted.
- ADDON-FR-111 Library page: header "Library" and "Back to catalogs"; filters All / Movies /
  Series; grid newest first. OK on a title whose installation is enabled opens its page;
  otherwise a dialog: "This title's addon is disabled or no longer installed. Your saved title is
  kept in Library." with **Close** (focused) and **Remove from library**. Empty texts: "Your
  library is empty. Choose Add to library on a movie or series details page." / "No saved
  movies." / "No saved series.". "Loading saved titles…" while loading. On return from a title
  the same title (or the nearest remaining position) is focused, else the Back button.
- ADDON-FR-112 The Library never contacts an addon by itself and is independent of progress,
  catalog visibility and addon enabled state; reinstalling an addon with a different
  configuration does not rebind saved titles.

### 4.16 Search

- ADDON-FR-113 Eligible catalogs: visible catalogs (saved order, enabled, not hidden) that the
  manifest supports, declaring a `search` extra, and with no required extra other than `search`
  and `skip`; at most the first **32** ("Searching the first 32 catalogs in your saved order."
  when more exist). No eligible catalog → "No enabled, visible catalogs support plain text
  search. Catalogs requiring other choices are in Discover." and Search is disabled.
- ADDON-FR-114 Query: trimmed, ≤ **256** characters (longer input is not accepted), blank sends
  nothing and clearing the field clears results. Search runs only when the Search button is
  pressed (typing never searches). Each catalog gets extras `search=<query>` and `skip=0`
  (only if `skip` is declared); **3 workers**, **12 s** per catalog, **30 s** overall; catalogs
  not answered in time count as TIMEOUT. Results are filtered to type `movie` and `series`,
  keyed by (installation, type, id) so overlapping catalogs of one addon merge but different
  addons never do, and capped at **100 per row** ("Showing 100 results. Refine your search for
  more precise matches."). No paging across addons. Results use the response cache (a repeated
  query is instant while fresh). The query never enters diagnostics or history.
- ADDON-FR-115 Status line: "Enter a title, then choose Search." → "Searching catalogs · n / N"
  → "Some catalogs could not be searched. Choose Search to retry." / "Showing saved search
  results; an addon is unavailable." / "Results for “<query>”". Row empty texts: "Search results
  will appear here.", "Searching…", "No results from available catalogs.", "No matching
  movies." / "No matching series.". A new search cancels the previous one; Clear and leaving
  cancel too.
- ADDON-FR-116 Keys: Down from the input row goes to the first non-empty row's first card; Up/Down
  between the Movies and Series rows land on the first card; Up from the Movies row (or an empty
  row) returns to the input. Returning from a title restores the query, both row positions and
  the focused card without searching again.

### 4.17 Show all grid and filters

- ADDON-FR-117 Grid of one catalog: adaptive columns (min 132 dp), cards with captions. First
  page on entry when all required extras have values. Paging is automatic: when the last loaded
  item becomes visible and no request, failure or open filter form exists, request
  `skip = next`. `next = skip + receivedCount` (raw count); paging stops when the catalog has no
  `skip` extra, a page is empty, a page is shorter than a declared `pageSize`, a page adds no new
  titles (provider ignores skip), or **1,000** titles are loaded ("Showing 1,000 titles. Use
  search or filters to narrow this catalog."). One request at a time. A failed page keeps the
  loaded titles and shows **Retry loading titles** (no automatic retry); "Loading more titles…"
  in the footer while paging.
- ADDON-FR-118 Option extras (those with `options`) appear as choosers (230 dp) labelled with the
  filter name (genre → "Genre", year → "Year", language → "Language", search → "Search", others
  capitalised) and the value; the list starts with "Select…" for required extras or "Default"
  (no value) for optional ones. Choosing a value reloads from the first page when all required
  values are set; clearing a required value cancels any request and empties the grid ("Choose
  the required filters above to load titles."). On a catalog's own Show all grid the chooser row
  (with every option extra) appears only when the catalog has a required non-`skip` extra; the
  Discover filter page always shows it.
- ADDON-FR-119 Free-text extras (no options) are edited in a form (one plain, unmasked text field
  per extra, edited on OK, ≤ 1,024 characters, " *" after required names) with **Apply**. The
  form opens automatically when a text extra is required. Only the Discover filter page has a
  **Catalog filters** button that toggles it. Refresh titles is disabled while the form is open.
- ADDON-FR-120 Discover filter page (rail compass): catalogs = all visible catalogs (including
  those needing choices), first selected by default. Choosers: **Type** (160 dp; distinct types,
  "Movie", "Series" or the raw type) and **Catalog** (240 dp; catalogs of that type by name),
  then the catalog's option choosers. Heading "Discover". No visible catalog →
  "No visible catalogs. Show catalogs in Addons & setup." + Back.
- ADDON-FR-121 Chooser dialog: 480 dp wide, ≤ 430 dp tall, panel colour, title, a list of
  options with the current one selected and initially focused; OK chooses and closes; Back
  closes.

### 4.18 Response cache

- ADDON-FR-122 Catalog and meta responses (never streams or subtitles, never manifests) are cached
  per (profile, installation, installation revision, resource, type, id, sorted extras). A
  **fresh** entry is used without network unless `refresh` is requested. TTL = response
  `max-age` (default **300 s** when absent), clamped to 0–86,400 s; `no-store` deletes any entry.
- ADDON-FR-123 On a temporary failure (network, timeout, HTTP 408/425/429/5xx) a stale-allowed
  entry is served and marked stale (no automatic retry). On any other HTTP error (401, 403, 404,
  410, …) the entry is deleted and the error shown (expired configurations are not disguised as
  offline). A cached body that no longer parses is deleted and fetched again.
- ADDON-FR-124 Because the revision is part of the key, enabling/disabling, refreshing or
  re-prioritising an addon invalidates its cached responses (they age out by the file budget).

### 4.19 Encryption and secrecy

- ADDON-FR-125 Discover has its own Android-Keystore key (alias `sohva.addons.v1`) wrapping one
  software data key (envelope encryption, AES-GCM, see [Security](73-security-privacy.md)). The
  wrapped key is written with a **synchronous commit before** any ciphertext that depends on it
  (an asynchronous write lost a new key on process death in testing). Encrypted: installation
  payloads (configured URL + manifest JSON), progress payloads, Library payloads and every cache
  file. Keys in databases are SHA-256 hashes; catalog order/visibility store only hashes.
- ADDON-FR-126 Configured URLs, stream URLs, headers, subtitle URLs, tokens and payloads never
  appear in logs, exceptions, UI text, accessibility labels, saved-instance state or plain
  preferences. Every domain object's `toString()` is redacted. Masked input fields
  (password transformation) for URLs; edit opens only on OK.

### 4.20 Errors and messages

| Failure | Message key | English |
|---|---|---|
| INVALID_URL, INSECURE_URL | `addon_error_url` | Enter a complete HTTPS or stremio:// manifest URL. Plain HTTP is not enabled here. |
| CONFIGURATION_REQUIRED | `addon_error_configuration` | Configure this addon on its provider’s page first, then install the configured manifest URL. |
| INVALID_MANIFEST, INVALID_RESPONSE, RESPONSE_TOO_LARGE | `addon_error_manifest` | The addon returned an invalid or oversized response. |
| NETWORK, TIMEOUT, HTTP_ERROR | `addon_error_network` | The addon could not be reached or refused the request. Saved addons are unchanged; try again later. |
| REDIRECT | `addon_error_redirect` | This address redirects. Install the provider’s final HTTPS manifest URL. |
| ACCESS_DENIED | `addon_access_denied` | Addons are unavailable for restricted profiles. |
| others (UNSUPPORTED_RESOURCE, NOT_FOUND, CONFLICT, STORAGE, INVALID_REQUEST) | `addon_error_operation` | The operation could not be completed. Reload the saved list and try again. |

Catalog-grid stale notice: `addon_cached` "Showing saved content; the addon is currently
unavailable."; empty: `addon_no_results` "No titles match this catalog or filter.". Title page:
"Full details unavailable. <message>" (`addon_ui_full_details_unavailable`).

### 4.21 Localisation

- ADDON-FR-127 All Discover TV screens use the saved interface language through the Activity's
  resources (also inside coroutines/callbacks). `strings_addons.xml` exists in all seven
  languages (en, fi, de, es, it, pt, sv) with plural forms; a unit test enforces key parity and
  identical numbered format arguments. Provider names, catalog names, custom types, filter
  values and metadata are shown exactly as supplied; metadata language is chosen in the metadata
  addon's own configuration (Addons & setup explains this). The phone page is English only.

## 5. Screen anatomy

Tokens are from [Design system](../design/01-design-system.md): typography display 40/44 sp
Black, headline 22/27 Bold, body 16/23, label 14/19 SemiBold, caption 12/16 Medium; shapes small
8 dp, medium 12 dp, large 18 dp. Shared components (TvActionButton, TvSurface, TvListRow,
SettingsRow, SettingsValueRow, SettingsSwitch, SettingsOverline, TvUrlField, bottom transport
controls, track overlay) are in [Components](../design/02-components.md). Reference captures:
`design/screenshots/older-builds/2026-09-10-discover-lab/` (Lab build of 10–11 September 2026:
the landing still showed card captions and a three-item rail then; beta 23 has no captions on
landing shelves and the six-item rail below).

### 5.1 Discover landing (`addon-second-shelf-focus.png`, `addon-saved-row-loading.png`)

- Background: screen background with **backdrop** layer: ground colour; the focused title's
  `background` image, crop, aligned top-end; a horizontal gradient ground α .98 → .75 → .20
  (left to right); a vertical gradient transparent → ground α .35 → ground (top to bottom).
- Content column: padding start **84 dp** (clears the collapsed rail), end 24 dp, top 24 dp.
  - Hero: 66 % of the width, 35 % of the height; logo box ≤ 88 dp tall (logo image 320 × 88 dp,
    fit, start-aligned; its space stays empty while loading; title text only when there is no
    logo or it failed) or title text (display, Black, textPrimary, ≤ 2 lines); spacing 10 dp;
    facts line (body, textMuted, ≤ 2 lines): "Movie|Series  ·  <releaseInfo>  ·  <runtime>  ·
    <genre1> / <genre2>  ·  IMDb <rating>"; synopsis (label size, textMuted, ≤ 3 lines).
  - Status texts (loading/failure/empty) under the hero.
  - Shelf list: fills the rest; content padding top 8 dp, bottom = 65 % of the screen height
    (so the last shelf can align to the top); 18 dp between shelves; focus keeps the focused
    child visible without re-centring.
  - Each shelf: heading (body size, textPrimary, 1 line, ellipsis), optional stale/failure line,
    horizontal row with content padding 8 dp and 12 dp spacing of **126 dp** poster cards, no
    captions.
- Rail (overlay, full height): width **64 dp** collapsed / **218 dp** when it has focus;
  background colour = screen background at α .65 collapsed / .98 expanded; padding 8 dp
  horizontal, 24 dp vertical; items 48 dp tall, full width, 12 dp apart; each item a surface with
  a 24 dp icon tinted with the content colour and, when expanded, the label (label size, 1 line),
  12 dp horizontal padding, 14 dp icon–label gap. Back to home sits at the bottom.

### 5.2 Poster card (shared by landing, grids, Search, Library)

- 2:3 box, medium shape, clipped; resting fill = vertical gradient surface → background.
- Image: `poster`, crop, requested at **256 × 384 px, inexact precision, 120 ms crossfade**.
  No poster or load failure: the title centred (label size, textMuted, ≤ 4 lines, 16 dp padding).
  Nothing is drawn under a loading image (no initials or text flash).
- Focus: a 3 dp textPrimary border drawn **inside** the card over the image; no scale (lazy rows
  never clip a scaled child).
- Progress: a 4 dp bar in the focus colour along the bottom, width = fraction.
- Watched: a 22 dp circle in the focus colour, top-end, 6 dp inset, with "✓" in the background
  colour (caption size).
- Caption (grids, Library, Search): title (label size; textPrimary when focused, else textMuted;
  1 line; 7 dp top padding; 20 dp tall) and "<releaseInfo> · <imdbRating>" (caption, textDim).

### 5.3 Title pages (`addon-movie-cast.png`, `addon-series-cast.png`, `movie-action.png`, `series-action.png`)

- Backdrop as in 5.1 (episode page: episode thumbnail, else series background).
- **Movie / episode page**: row padding 32 dp, 28 dp gap. Left column 56 %: (episode: series
  name in body textMuted) title (display, Black, ≤ 3 lines); facts (movie) or "Season N ·
  Episode M" (episode) 12 dp below; synopsis (body, textMuted, ≤ 7 lines) 16 dp below; loading
  and failure lines; action row (12 dp gap): primary button with Play icon (Continue watching /
  Find sources) and, on movies, the Library toggle; then Start from beginning (Replay icon,
  compact); status lines; Retry details (compact). Movie cast: "Cast" (headline Bold), then a row
  of 92 dp columns 10 dp apart: 52 dp circle (surface) with initials (label, Black, textMuted)
  under the photo (crop), name (label, SemiBold, centred, ≤ 2 lines, 8 dp above), character
  (caption, textDim, 1 line); focus = 2 dp focus-colour border, 8 dp radius; read-only but
  focusable; description "<name> as <character>". Series pages show names only: "Cast: A, B, C"
  (label, textMuted, ≤ 2 lines). Right column 44 %: sources (5.4).
- Initials: split the name on space . - : _ · /; words starting with a letter; two or more words
  → first letters of the first two, upper-case; else the first two characters upper-case.
- **Series page**: padding 32 dp, 12 dp spacing. Header block 42 % of the height × 72 % of the
  width: title (display, Black, ≤ 2 lines), facts, description (label, textMuted, ≤ 4 lines),
  cast names. Then loading/failure lines, then a row: Library toggle + season chips (compact
  buttons, selected state, 10 dp apart). Episode row (16 dp spacing, 8 dp padding): **230 dp**
  cards with focus ring drawn above the artwork and no scale; 130 dp tall image (thumbnail, else series background,
  crop), vertical scrim transparent → scrim α .85, label bottom-start 10 dp (label size, ≤ 2
  lines), Trakt bar and tick as in 5.2.

### 5.4 Sources column

Header row: "Sources" (left) and a compact **Refresh** button with refresh icon (right); the
Scraper chooser full width; then a list (12 dp spacing): status lines, per provider its name and
state, source cards (surface fill, textPrimary content, 14 dp padding, 6 dp line spacing; name ≤
2 lines, description ≤ 5 lines, "Play"/"Unsupported transport").

### 5.5 Player and loading (`addon-playback-loading.png`)

- Video area black at all times (letterbox and before the first frame). When controls are
  visible or a failure is shown: a vertical gradient transparent → transparent → scrim α .95.
  Controls column at the bottom: 40 dp side padding, 28 dp bottom, 8 dp spacing.
- Loading screen: scrim fill; backdrop image full screen (crop); vertical gradient scrim α .15 →
  .35 → .88; centred column 65 % wide, 28 dp spacing: logo (fit, full width, 170 dp tall) or
  title (display, Black, onScrim, centred, ≤ 3 lines) in a box 80–170 dp tall whose opacity
  pulses **0.78 ↔ 1.0 over 1,400 ms (FastOutSlowIn, reverse, infinite)**; stage text (body,
  onScrim, centred). Bottom centre, 28 dp up, 16 dp apart: Cancel, Subtitles (compact).

### 5.6 Subtitle picker and sync (`addon-subtitle-picker.png`, `addon-subtitle-sync.png`)

- Picker: full-screen dialog, scrim α .88; padding 28 dp. Header: "Subtitles" (headline Bold)
  over "Appearance follows Sohva’s VOD subtitle settings." (caption, textMuted); "Back to player"
  compact at the right. Left column 200 dp: "Language" (label, textMuted) and dense list rows
  with counts; right column: language heading and option cards (full width, 14 dp padding,
  provider in body Bold, "<Language> · <detail>" in caption, "✓ Selected" at the right). Bottom
  row of compact buttons (12 dp apart).
- Sync: top-centre panel, 24 dp from the top, ≤ 650 dp and 85 % wide, large shape, panel α .96,
  22 dp padding, 12 dp spacing. Header "Subtitle sync" (body Bold) and the draft offset
  (headline Bold). Adjustment surface (focus ring, 16/10 dp padding) with a 28 dp tall track:
  2 dp textDim line inset 8 dp, 12 dp textMuted centre tick, 6 dp-radius focus-colour knob at
  (draft + 60 s)/120 s; "← Earlier" / "Later →" (caption). Hint line; buttons Apply, Reset to
  zero, Play/Pause, Back to subtitles.

### 5.7 Addons & setup (`addon-settings-installed.png`, `addon-settings-setup.png`, `addon-settings-subtitles.png`)

Padding 28 dp. Header row: "Addons & setup" (display, Black) and "Back to catalogs" (Back icon,
compact). 18 dp gap. Left column 200 dp, list rows 2 dp apart (two-line labels allowed):
Installed addons (Channels icon), Add / import (Settings), Catalogs (Guide → organise screen),
Subtitles (Subtitles), Watch history (Play → history page). Right: scrolling list.
- Installed: overline "Installed addons · N"; per addon a settings row (name; "N catalogs ·
  Enabled|Disabled"; Channels icon; enable switch) and an action row indented 16 dp (8 dp gaps):
  Catalogs, Refresh, Priority ↑, Remove (danger). Then "No addons installed" / "Use Add / import
  to set up your services." when empty; info row "Provider priority" / "Priority changes
  addon/source order. Use Catalogs to arrange or hide browsing shelves separately."; value row
  "Reload saved addons" (refresh icon).
- Add / import: overline "Add your services"; info "Separate addon data" / "Sohva backups do not
  yet include addons, Library or addon watch history. Lab data is not automatically imported.";
  value row "Import addons" · "Open" · "Copy from Stremio, send from your phone, or use a saved
  list."; overline "Manual setup"; row "Configured addon URL" / "Keep private configuration links
  secret. They may contain credentials." (Lock); masked field "Configured HTTPS or stremio://
  manifest URL (private)" (16 dp padding); "Install addon" (field cleared on press).
- Subtitles: overline "Subtitle results"; "Show all languages" with switch and "Off limits
  results to your primary and secondary subtitle languages."; "Preferred languages" / "Primary:
  <X> · Secondary: <Y>" ("not set"); "Universal playback settings" / "Set preferred languages in
  Sohva Settings → VOD audio and subtitles. Addon playback uses the same preferences."; overline
  "Metadata language"; "Titles and synopses" / "Choose these languages in your metadata addon's
  configuration. Sohva's interface language does not translate addon responses."
- Remove dialog: panel, medium shape, 24 dp padding, 16 dp spacing: "Remove <name>?", Cancel
  (focused), "Remove addon" (danger).

### 5.8 Setup sub-pages (Import, Stremio, Phone, Catalogs) (`addon-import-settings-*.png`, `addon-catalog-order.png`, `addon-catalog-visibility.png`)

Common frame: padding 28 dp; header row (20 dp gap): title (display, Black) and a compact back
button with Back icon; 18 dp gap; content. Notes use label size in textDim.
- Import: left column 200 dp (methods or review notes), right column content list and a footer
  separated by a 1 dp divider line: count ("N addons ready for preview", body), note, then
  Preview import + Clear list, or Install N new addons + Start another list.
- Stremio: before start: overline "Copy your addon list", rows "1. Authorize on your phone" and
  "2. Review on this TV"; after start: QR 200 dp beside "Scan with your phone" (headline), the
  link (body) and two notes; always two notes about credential use and the unchanged account;
  the start button ("Start Stremio authorization" / "Waiting for authorization…").
- Phone: warning note, QR 220 dp beside "Scan with your phone" (headline), the pairing address
  (label size) and instructions.
- Catalogs: left column 200 dp: overline "Catalog settings", Reorder (Guide icon) and Show / hide
  (Channels icon) rows, notes; right: list with dividers (Reorder: trailing position number) or
  switches (Show / hide); footer hint.

### 5.9 Grid, Search, Library, Discover filter (`addon-auto-paging.png`, `addon-search-results.png`, `library-grid.png`)

- Grid: padding 28 dp, 12 dp spacing: heading (catalog name or "Discover", headline), chooser
  row, button row (Back to addons, Catalog filters, Refresh titles), status lines, grid (content
  padding 8 dp; 20 dp vertical, 18 dp horizontal spacing; full-width footer row).
- Search: padding 28 × 20 dp, 10 dp spacing: "Search" (headline) + "Back to catalogs"; input row
  (field with search icon, Search, Clear); status (caption, textMuted); two rows headed "Movies"
  and "Series" (body), 16 dp apart, posters **112 dp**, 12 dp spacing.
- Library: padding 28 dp; header "Library" (headline) + "Back to catalogs"; filter buttons
  (compact, selected); grid as above.

## 6. Data

All Discover storage is local, per profile, outside Android backup, and **not** in `.smbak`
backups ([Backup](71-backup-restore.md)). Uninstall or clearing storage removes it.

| Store | Location | Content |
|---|---|---|
| Installations | Room DB `sohva-addons.db` v1, table `addon_installations` | `installationId` (UUID, PK), `profileId`, `endpointFingerprint` (unique with profile), `encryptedPayload` (`{"url","manifest"}`), `enabled`, `position`, `revision`, `updatedAtMillis`. Listed `ORDER BY position, installationId`. |
| Progress | Room DB `sohva-addon-progress.db` v4, table `addon_progress` | `key` (PK, hash of profile, metadata installation, media type/id, video type/id), `profileId`, `encryptedPayload` (version 1 JSON: installation, mediaType, mediaId, videoType, videoId, title, position, duration, updated, completed, artwork{name, poster, background}), `updatedAtMillis`; index (profileId, updatedAtMillis); ≤ 200 rows per profile. Versions 2–3 came from a private Trakt trial build; migrations 1→4, 2→4, 3→4 only drop `addon_projection_receipts` and `addon_progress_display`. |
| Library | Room DB `sohva-addon-library.db` v1, table `addon_library` | `key` (PK, hash of profile, installation, type, id), `profileId`, `encryptedPayload` (version 1 JSON: installation, type, id, name, poster, background, year, added), `addedAtMillis`; index (profileId, addedAtMillis); list query `LIMIT 1001`; ≤ 1,000 per profile, capacity checked in the insert transaction. |
| Response cache | `noBackupFilesDir/addon-responses/<sha256>.cache` | Encrypted JSON `{key, body, stored, expires, staleAllowed}`; budget **32 MiB and 128 files** (least recently read evicted by file time after each write); corrupt or swapped files are misses; temp files `response-*.tmp` removed on the next write. |
| UI preferences | SharedPreferences `sohva_addon_ui` | `all_subtitle_languages` (bool, default false, global), `catalog_order_<hash("catalog-order", profile)>` (JSON array of catalog hashes), `catalog_hidden_<hash("catalog-visibility", profile)>` (sorted JSON array). Written with `commit()`. |
| Key material | Keystore alias `sohva.addons.v1`; SharedPreferences `sohva_addon_secret_envelope` key `data_key` | Wrapped data key, committed synchronously before first use. |

Hashes: SHA-256 hex of the parts joined as `<length>:<part>` each (length-prefixing prevents
separator collisions in opaque ids). Never stored: stream URLs, headers, subtitle URLs or data,
Stremio tokens, search queries (other than inside the hashed cache key and the encrypted cached
response), phone tokens.

In memory (process lifetime): one Discover host (clients, stores, repositories, the active
player reference, the pending progress write) created on first use.

## 7. External interfaces

### 7.1 Stremio addon protocol (as used)

| Request | When | Response fields used |
|---|---|---|
| `GET <base>/manifest.json` | install, import preview, refresh | FR-10–FR-13 |
| `GET <base>/catalog/<type>/<catalogId>[/<extras>].json` | shelves (20 built), grids (1,000), Search (100) | `metas[]` FR-16/17 |
| `GET <base>/meta/<type>/<id>.json` | title pages, hero synopsis, artwork repair, Trakt lookup | `meta` FR-18/19 |
| `GET <base>/stream/<type>/<videoId>.json` | playable pages, retry | `streams[]` FR-21/22 |
| `GET <base>/subtitles/<type>/<videoId>[/<extras>].json` | picker, automatic selection | `subtitles[]` FR-23 |

Extras used: `skip` (paging), `search`, `genre` and any option extra, free-text extras;
subtitles: `videoHash`, `videoSize`, `filename`. Id prefixes gate meta/stream/subtitle requests.
Behaviour hints: manifest `configurationRequired` (refuse), `configurable` (ignored); meta
`defaultVideoId`; stream `proxyHeaders.request`, `videoHash`, `videoSize`, `filename`.
Reference: Stremio addon SDK docs (manifest, meta, stream responses).

### 7.2 Stremio link and account API

See FR-47. Origins fixed in code; test endpoints injectable only inside the module.

### 7.3 Nuvio export

JSON array of `{url, name, description}` (`/api/addons`) or `{ "addons": [...] }`
(`/api/state`) saved as a file on the user's side; only `url` used. Version-dependent; not a
stable API.

### 7.4 Media and subtitle hosts

Arbitrary HTTPS/HTTP hosts returned by addons; rules in FR-95 and FR-101. Artwork hosts are
loaded by the shared image loader.

## 8. Edge cases and limits

- Provider latency: shelves, Search and sources are independent; one slow addon never blocks
  another (search has per-request and overall deadlines; sources show per-provider states).
- Providers that ignore `skip` and repeat pages: paging stops when a page adds nothing new.
- Providers with `no-cache`/`must-revalidate` or zero `max-age` (AIOMetadata as configured by the
  owner returned zero freshness and no stale reuse): every visit refetches; Home previews cannot
  use them. Expected, not a bug.
- 401/403/404/410 from an addon mean an expired or changed configuration: the cached copy is
  removed and the error shown.
- OpenSubtitles Pro's subtitles resource answers with a cross-origin 302: JSON redirects are not
  followed, so that provider's results fail (known limit, needs a trusted-origin policy).
- Extensionless HLS/DASH URLs play only if Media3 recognises them; multi-period DASH with
  side-loaded subtitles is unsupported; ZIP subtitles are rejected.
- Canonical metadata ids differ from catalog ids: never merged; the original key rules.
- An addon removed or disabled while a title page or player is open: requests fail with
  NOT_FOUND/CONFLICT and playback stops within 5 s.
- Profile switch: all Discover UI is disposed; in-flight work for the old profile is discarded.
- Process death during playback: at most the last 5 s of progress is lost; the Trakt start is
  closed by a pause on the next start ([Trakt](51-trakt.md)).
- Background: playback and every authorization/pairing flow stop on ON_STOP; nothing resumes by
  itself.
- Large series (thousands of videos): up to 10,000 videos are parsed; only the selected season
  is drawn.
- Library and history payloads are limited to 128 KiB each; larger rows are treated as storage
  errors.
- Titles that are neither `movie` nor `series` (custom types) browse and play normally but
  cannot be added to the Library or sent to Trakt.
- Opening a Home Continue card whose addon was removed shows the restricted-profile text today
  (lesson 22 in §10): the rebuild shows the Library's "addon is disabled or no longer
  installed" text instead, with Back.

## 9. Lightweight by design

Budgets for the low-end box (Amlogic S905Y4, 2 GB, Mali-G31) and the rules that keep them.

Start-up and idle
- No addon network work before the viewer opens Discover (verified by a device test: zero
  requests at Home). No workers, no schedulers, no initializer in the addon module.
- Home needs only the progress store. **Rebuild rule:** make the progress store a small
  standalone component; do not construct HTTP clients, the installation store, cache or
  repositories at start-up (today one host object creates all of them on Home's first read).
- Room databases open lazily on first query.

Network (all bounded, all cancellable, all foreground)
- 4 concurrent addon JSON requests app-wide; 15 s timeouts; bodies ≤ 2 MiB.
- Landing: active + 2 next shelves only, 20 titles built each; 6 poster warm-ups with 2 workers.
- Hero: 120 ms + 350 ms settle, one metadata lookup at a time, 8 s deadline, 30 s failure
  cool-down; cancelled on focus change and when the landing is not RESUMED.
- Search: 32 catalogs, 3 workers, 12 s / 30 s. Details: 3 candidates, 15 s / 20 s.
- No metadata, stream or subtitle prefetch anywhere. Sources only on playable pages, subtitles
  only after READY or when the picker opens.

Memory upper bounds
- Landing: 24 shelves × 20 previews + 20 history items. Grid: ≤ 1,000 previews (no videos, no
  cast). Search: ≤ 200 hits. Title page: ≤ 10,000 videos, ≤ 60 cast. Sources ≤ 500 per provider,
  subtitles ≤ 1,000 per provider. One downloaded subtitle ≤ 4 MiB in memory.
- **Parsing — rebuild rule:** today each response is read into a byte buffer, turned into a
  String, scanned character by character for depth, copied again to count UTF-8 bytes, then
  parsed into a full JSON tree before models are built (a 2 MiB catalog costs tens of MB of
  transient garbage; the Home preview still builds the whole tree to keep 20 items). The rebuild
  streams the body through a byte-counting, size-capped source into a pull parser (for example
  Android's `android.util.JsonReader` or Moshi's okio `JsonReader`, per
  [Tech stack](../plan/05-tech-stack-and-build.md)): depth and size enforced while reading, only
  the needed fields materialised, elements after `itemLimit` skipped but still counted for
  `receivedCount`. Parsing runs on a background dispatcher, never on the main thread.
- **Cache — rebuild rule:** today every cache file is hex text of an encrypted JSON wrapper
  around the escaped body (about 2× the body size on disk, three to four copies in memory on
  read), and every read also rewrites the file time. The rebuild stores binary AES-GCM
  ciphertext, keeps a small separate "preview" entry (the first 20 parsed items + raw count) for
  landing shelves so a shelf never decrypts and parses a 2 MiB page, records recency in an
  in-memory index instead of touching files, and does not write entries that can never be
  served (`no-cache` without validators, `max-age=0` and not stale-allowed).
- Installation list: today every access check reads all installation rows from Room (decoding
  only changed rows, with a 32-entry / 2,000,000-character decode cache). **Rebuild rule:** keep
  one in-memory snapshot per profile, invalidated by the store's own writes and revision, so the
  several checks around each request cost no I/O.
- Trakt overlay: today each Discover screen (landing, grid, title page, playable page) collects
  its own map built from the profile's **whole** `trakt_state` table on every change. **Rebuild
  rule:** one shared per-profile index, looked up only for visible keys ([Trakt](51-trakt.md) §9).

Rendering (cheap look)
- Poster decode: **rebuild rule:** decode at the card's pixel size (126 dp × density; 252 × 378 px
  at xhdpi 1080p, 189 × 284 px on a 720p box) in RGB_565 (posters need no alpha): about 190 KB
  per poster instead of 390 KB at 256 × 384 ARGB. The shared image loader already limits the
  memory cache to 8 % of the heap and runs 2 decoders at most.
- Backdrop: today decoded at view size (1920 × 1080 ARGB, about 8 MB) with two full-screen
  gradients on top (three full-screen layers per frame). **Rebuild rule:** decode ≤ 960 × 540
  RGB_565 (it sits under heavy scrims), draw the two scrims as one pre-rendered static layer
  (Home's lesson: repainting screen-sized passes per frame was visible on a weak GPU), and only
  change it after focus rests (120 ms).
- Focus: cards use an inset border, never scale; the rail overlays instead of shifting shelves.
- **Focus state — rebuild rule:** today the landing's focused title, active row and focus flags
  are plain state read by the landing composable itself, so every D-pad press recomposes the
  whole landing (Home had exactly this cost on slow boxes and fixed it). The rebuild keeps focus
  in a holder read only by the hero, the loading effects and the Trakt lookup (snapshot flows),
  never by the shelf list.
- Animations: only the loading-screen title pulse (layer alpha on one box) and 120 ms
  crossfades. No per-frame work while idle.
- QR codes: today built pixel by pixel (147,456 `setPixel` calls for 384 px) inside composition.
  **Rebuild rule:** build off the main thread with one `setPixels` call, at the displayed pixel
  size.

During playback
- The 5 s access check + progress snapshot, and the 500 ms position poll for the controls, are
  the only periodic work. Today each 5 s snapshot is an encrypted Room upsert plus a prune query,
  and it wakes Home's resume observer, which decrypts up to 200 history rows again. **Rebuild
  rules:** write only when the position moved ≥ 5 s or the state changed; prune only when a new
  key is inserted; Home's projection does not recompute while a player is in the foreground
  (it catches up when the player closes).
- No catalog, hero, artwork-repair or Trakt sync work runs while video plays (the landing is not
  composed then; the Trakt loop must pause — [Trakt](51-trakt.md) §9).

Where the current app was slow or wasteful, and the rule: duplicate next-row downloads when one
effect owned two rows (fixed: one effect per row); Home building 1,000 models to show 20 (fixed:
20); expired rows waiting for the network (fixed: show the one-day preview); manifest decrypt +
parse on every access check (fixed: byte-identical decode cache; rebuild: snapshot); the items
marked "rebuild rule" above.

## 10. Lessons from the current app

1. **Row prefetch ownership** (SOHVA_TV_ADDONS_OVERNIGHT.md, "Large-catalog stress"): one
   prefetch effect keyed by the active row cancelled the useful next-row request on Down, and
   lazy-list precomposition was a second competing owner. Rule: one keyed effect per row in the
   active+2 window, nothing else loads. The 40-catalog × 1,000-title stress test proves 35
   requests for 32 Down presses and none on the way back.
2. **Stremio link pending state** (same doc, "Morning Shield acceptance"): the live link service
   answers HTTP 200 with `error.code = 101` while waiting; treating any error as fatal killed the
   QR after 3 s. Only that code on `/api/read` with no result is pending; everything else fails
   closed.
3. **Non-transient HTTP errors were served from cache as if offline** (overnight
   "Resilience follow-up"): 401/403/404/410 now invalidate; only network/timeouts/408/425/429/5xx
   may use stale content. A profile revocation during a cache write could return the fresh
   result: access is re-checked after every cache I/O.
4. **Subtitle picker unreachable by remote** (SOHVA_TV_ADDONS_LAB_PLAN.md "Shield subtitle focus
   correction"): a full-screen overlay did not take focus. The picker is a modal focus boundary
   with initial focus on its Back button; async results never steal focus.
5. **Subtitle sync Down went to Back, OK did nothing** (overnight "subtitle-sync remote
   follow-up"): explicit focus links (Down → Apply, Up → adjustment) and OK applies.
6. **Controls stuck after subtitle menus; Back exited instead of hiding** (overnight "two-stage
   Back"): restored focus held controls forever and recreating the controls replayed old
   dismiss requests. Keep controls composed behind dialogs, opt into idle hide while focused,
   Back hides before it exits.
7. **Asynchronous subtitle apply undid a pause or seek** (Lab plan "Phone setup…"): capture
   position and play state only after the last suspension point, then prepare.
8. **Side-loaded subtitles lost earlier cues after re-preparing past the last cue** (overnight
   "Subtitle picker and timing"): use a normal subtitle extractor with a seek map for downloaded
   subtitles; for positive embedded delays widen the extractor seek position.
9. **Automatic subtitles skipped when readiness was slow; labels said Off while cues showed**
   (Lab plan "Automatic subtitle startup"): observe readiness for the screen's lifetime, one
   attempt per playback, label from the actually selected track.
10. **Subtitle wait of 20 s held a ready stream** (commit 63face7, beta 16): budget 5 s, then
    embedded fallback; a fast primary never waits for a slow provider.
11. **Continue and Start from beginning did nothing** (Lab plan "working Continue actions"):
    they must wait for all providers and start the first playable source in priority order.
12. **Catalog-only addons had no movie sources** (Lab plan "catalog-only metadata routing"):
    details fall back to other metadata addons; a movie preview is playable without meta.
13. **Home rule for catalogs flip-flopped**: first all filterable catalogs were hidden from the
    landing, then corrected — only catalogs with a **required** non-`skip` extra leave the
    landing (Lab plan "Owner clarification").
14. **Continue-watching posters vanished when the disposable cache expired** (same section):
    save an artwork snapshot inside the encrypted progress payload; repair missing ones once.
15. **English catalog synopsis flashed before the Finnish metadata** (SOHVA_TV_ADDONS_INTEGRATION.md
    "avoid the English synopsis flash"): show an empty synopsis while the localized one loads;
    use fresh cache first.
16. **Hero title text flashed before the logo** (overnight "Hero title-logo flash"): reserve the
    logo space while it loads; text only when absent or failed, state keyed per title.
17. **New wrapped key lost on process death** (Lab plan "Persistence and first browsing slice"):
    an `apply()`-written wrapped key produced authentication-tag failures after a kill. Commit
    synchronously before any ciphertext. The IPTV envelope store still uses `apply()` — see
    [Security](73-security-privacy.md).
18. **Finished movies left a black screen; Discover episodes stopped on the last frame**
    (PLAYBACK_COMPLETION_FIX.md, beta 23): a one-shot end callback after the progress callbacks,
    numeric next-episode order, specials apart.
19. **Duplicate Continue cards for one movie across Discover and VOD** (commit 22d2d04, beta 16):
    Home merges by IMDb/TMDB aliases ([Home](02-home.md)).
20. **Priority is not catalog order**: an early build moved shelves with provider priority; they
    are separate controls with separate storage.
21. **Revision in the cache key**: any enable/refresh/priority change invalidates that addon's
    cached responses and resets its shelves. Keep it (it prevents serving a revoked
    configuration) but expect the cold reload.
22. **Messages**: the Home Continue path shows "Addons are unavailable for restricted profiles."
    when the title's addon was removed (wrong text); the Library page title reuses the Settings
    "Library" label. Use dedicated strings.
23. **Dead strings**: `addon_refresh_details`, `addon_browse_catalogs`, `addon_back_titles`,
    `addon_load_more`, `addon_source_notice`, `addon_back_sources`, `addon_retry_subtitles`,
    `addon_retry_sources`, `addon_find_subtitles`, `addon_source_subtitles`,
    `addon_no_source_addons`, `addon_no_subtitle_addons`, `addon_http_source`,
    `addon_unsupported_source`, `addon_lab_notice`, `addon_reload`, `addon_empty`, `addon_enable`,
    `addon_disable`, `addon_refresh`, `addon_move_up`, `addon_confirm_remove` are unused in beta
    23; do not carry them over.
24. **Keep**: provider-owned opaque ids, never guessing episode ids; the redacted `toString()`
    of every model; fixed-origin clients; positional import statuses without names or URLs; the
    synthetic addon + generated-media test harness; opt-in live tests that read a local file
    path, never a URL on the command line.

Open questions (for the owner)
- Should HTTP (not HTTPS) addons be installable after an explicit warning? The About text and
  PRIVACY.md describe "explicitly accepted HTTP addon connections", but beta 23 has no UI path
  that allows them.
- Should the rebuild add Discover data to the encrypted `.smbak` backup? Every tester document
  says it is "not yet" included.
- Provider watch tracking through subtitle requests (AIOMetadata): keep today's behaviour
  (automatic requests after READY) or add an opt-in? Recorded as an open policy decision since
  beta 13.
- `posterShape` (square/landscape) is parsed but ignored; should landscape catalogs get
  landscape cards?

## 11. Acceptance tests

Unit (JVM, MockWebServer, in-memory stores; mirror the existing suites)
- Endpoint: normalisation table (stremio://, trailing slash, AIOMetadata UUID base without slash,
  configure/install pages rejected, file-like names rejected, user info/fragment/backslash
  rejected, HTTP needs opt-in); extras encoding keeps `/ & + =` inside values; traversal ids
  rejected; fingerprints equal only for identical URLs.
- Manifest: custom types and page sizes kept; catalog matching ignores idPrefixes; object
  resources do not inherit prefixes; required/unknown extras rejected; legacy extras usable;
  size/depth bounds before parsing; future fields ignored; paging uses returned count, never
  assumes 100.
- Media: malformed tiles skipped but counted; dedup by (type,id); preview never parses videos or
  cast; movie playable without meta, series needs videos; cast merge and bounds; unsafe image
  URLs dropped.
- Sources: HTTP/torrent/external/unsupported classification; unsafe header drops only that
  stream; malformed header containers not silently dropped; local bridge hosts rejected;
  subtitle extras limited to hash/size/filename; counts bounded.
- Client: redirects not followed and not leaked; known and chunked oversize rejected after
  decompression; cancellation frees the permit; timeout bounded.
- Browse/cache: fresh cache hit, stale fallback only for temporary failures, no-store removal,
  no-cache never stale, 401/404/410 invalidate, revision/profile/disabled checks, revocation
  during cache write, one-day Home preview, 20 models with raw count.
- Store: encryption at rest, same manifest different configs coexist, concurrent duplicate
  install → one identity, stale refresh refused, removed cannot be resurrected, exact-set
  reorder.
- Import: preview is non-mutating; statuses and order; selection; commit dedup; partial failure;
  text limits (BOM, CRLF, 32 lines, 256 KiB, malformed UTF-8, NUL); Nuvio array/state,
  placeholder lines.
- Stremio: pending 101 only on read; hostile activation URLs; redirects and oversize; timeout
  after 10 min; access checked between each credential step; tokens never leave the client.
- Phone session: token + origin + host required; invalid list does not consume; expiry closes a
  stalled connection; 32 entries accepted; page has no external resources.
- Order/visibility: survive refresh; disabled keep position; new append; stale sets rejected;
  concurrent toggles preserved; profile scoping.
- Search: eligibility, bounds (32/3/12 s/30 s), progressive partial results, cancellation,
  revocation, 100 per row, provider identity preserved.
- Hero synopsis: fresh cache first emission; no network before 470 ms of rest; no catalog text
  while pending; cancel before network; timeout + cool-down; one lookup at a time; revocation
  propagates.
- Progress: stable identity, no URLs, sequence/session guards, unknown duration, 95 % rule,
  artwork repair keeps order, 200 cap. Library: 1,000 cap without eviction, idempotent add,
  profile/provider separation, corrupt row handling.
- Episode order: unordered metadata, season boundary, specials, duplicates, unnumbered lists,
  movies. Continue source choice: priority beats arrival order, scraper filter, unsupported
  skipped. Subtitle policy: ISO aliases, preferred ordering, audio suppression. Timing: label
  format, steps, bounds. Translations: key and format-argument parity in 7 languages.

Instrumented (emulator, synthetic addon server and generated media: MP4/HLS/DASH colour bars +
tone, MKV with Finnish/English text tracks)
- Home makes zero addon requests until Discover opens; a second visit reuses the cache (1
  request total).
- Restricted profile: no Home entry, direct UI denied, storage denied.
- Landing: first card focus, Left to rail, rail expansion without opening, Right/Back return,
  Down/Up land on first cards, heading alignment, focus restored from details/Show all/rail.
- 40 catalogs × 1,000 titles: opening fetches 1–4 catalogs; 32 Downs fetch exactly rows 0–34
  once; returning over the 24-shelf bound adds zero requests; no meta/stream/subtitle requests.
- Grid: automatic paging, failed page needs Retry, repeated page stops.
- Series episode focus: the rendered ring stays visible over loaded thumbnails, follows Right
  to the next episode, disappears from the previous one, and also appears without artwork.
- Catalog order: held-key move across 32 catalogs with zero intermediate saves, Back cancels,
  failed save retry; visibility persists across process death and hides from landing, filter
  page and Search.
- Import: phone transfer to preview without install, file picker, Nuvio file, long read-only
  review list navigable, leaving clears secrets; Stremio QR cancelled on Back and background.
- Playback: MP4/HLS/DASH decode and seek; resume and start over for movie and episode; network
  failure + fresh-source retry; background stop requires Retry; profile restriction releases the
  player; completion returns movies and advances episodes across seasons; pause never advances.
- Subtitles: primary embedded, secondary embedded, primary addon over secondary embedded, audio
  suppression, slow readiness, picker without choosing, 5 s fallback, Cancel/Back/background
  during lookup never start playback; timing ±2 s/+12 s on SRT/VTT/ASS and embedded with seeks
  and picker reopen, no second download.
- Player navigation: Back hides controls first; idle auto-hide after pickers; sync round trip
  keeps Back on the player.
- Library: add/remove movie and whole series, persistence across process death, missing-addon
  removal without requests.
- Persistence across process death: installations (including disabled), cache, progress, Library.

Manual (device)
- Real addon set on the Shield: AIOMetadata-style catalog + two stream addons + a subtitle addon;
  Finnish metadata; Stremio account copy; phone file transfer.
- **Low-end check (S905Y4 box or the slow-box emulator profile, see
  [Performance](../plan/07-performance.md)):** with a synthetic addon of ≥ 30 shelves (1,000
  titles per response), press Down through 20 shelves and Right through 40 cards: every press
  renders its next frame within two vsyncs (frame timeline / Perfetto), no request is made for
  rows outside active + 2, and the Java heap (meminfo sampling) stays ≤ 64 MB, including with a
  1,000-title Show all grid open. Record the player start-up milestones from the diagnostics log
  and compare them with the Shield: the app's own share (READY → first frame) must not exceed the
  Shield's by more than 1 s.

## 12. Reference: current code map

Addons module `addons/src/main/java/com/sohva/tv/addons/`
- `AddonEndpoint.kt` — URL parsing/normalisation, fingerprint, resource URL builder.
- `AddonManifest.kt` — manifest model, extras validation, paging rule, bounded parser.
- `AddonClient.kt` — dedicated bounded HTTP client (4 permits, 15 s, no redirects).
- `AddonMedia.kt` — catalog/meta models and parser, cast merging, single-video rule.
- `AddonSources.kt` — stream/subtitle parser, header safety, stream kinds.
- `AddonSourceRepository.kt` — progressive per-provider stream/subtitle resolution.
- `AddonBrowseRepository.kt` — catalog/meta with cache, Home preview, details fallback route.
- `AddonResponseCache.kt` — encrypted file cache, cache-key hashing.
- `AddonSearch.kt` — search planning, merging and bounded fan-out.
- `AddonHeroSynopsis.kt` — debounced focused-title synopsis resolver.
- `AddonCatalogOrder.kt`, `AddonCatalogVisibility.kt` — per-profile order and hidden sets.
- `AddonManager.kt` — access interface and install/refresh/enable/remove/reorder.
- `AddonStore.kt` — installation model, store interface, cipher interface.
- `AddonBatchImport.kt`, `AddonImportText.kt` — preview/commit and URL-list rules.
- `StremioCopyClient.kt`, `StremioCopyImport.kt`, `AddonCopyExport.kt` — Stremio account copy, Nuvio JSON, bounded JSON.
- `AddonPhoneSession.kt` — one-use LAN pairing server and its page.
- `AddonPlayback.kt` — playback selection, re-validation and fresh-URL refresh.
- `AddonMediaTransport.kt` — media client (origin-bound headers, redirects), subtitle loader.
- `AddonSubtitlePolicy.kt` — language normalisation and filtering rules.
- `AddonProgress.kt`, `AddonLibrary.kt` — encrypted progress and Library repositories.
- `AddonEpisodeOrder.kt` — next-episode rule. `AddonFailure.kt` — failure kinds.
- `storage/` — Room databases (installations, progress v4 with trial-table migrations, Library), encrypted store.

App `app/src/main/java/com/streammate/tv/addons/` and `app/AddonFeature.kt`
- `AddonFeature.kt` — lazy feature entry gated by package policy.
- `DiscoverAddonFeature.kt` — restricted gate, profile key, Addons & setup container, error mapping.
- `AddonHost.kt` — process-wide wiring of clients, stores, caches, cipher, access policy.
- `AddonDiscoverScreen.kt` — landing, shelves, Continue row, hero, rail, focus rules.
- `AddonCatalogScreen.kt`, `AddonFilterDiscoverScreen.kt` — Show all grid, filters, Discover filter page, chooser dialog.
- `AddonCatalogOrderScreen.kt` — reorder and show/hide.
- `AddonDetailsScreen.kt`, `DiscoverTitleScreen.kt` — title pages, episode pages, history entry.
- `AddonSourcesScreen.kt` — source column and first-playable choice.
- `AddonPlayerScreen.kt`, `AddonPlayback.kt`, `AddonPlaybackLoading.kt` — player screen, Media3 adapter, loading screen.
- `AddonSubtitlePicker.kt`, `AddonSubtitleSync.kt`, `AddonSubtitleTiming.kt`, `AddonSubtitleExtractors.kt`, `AddonSubtitleChoices.kt` — subtitle UI and timing mechanics.
- `AddonSearchScreen.kt`, `AddonLibraryScreen.kt` — Search and Library.
- `AddonManagerSettings.kt`, `AddonImportScreen.kt`, `AddonStremioImportScreen.kt`, `AddonPhoneScreen.kt`, `AddonSetupPage.kt` — setup screens.
- `AddonPosterCard.kt`, `AddonArtwork.kt`, `AddonCast.kt`, `AddonStrings.kt` — cards, backdrop/hero/facts, cast, localized labels.
- Resources: `res/values*/strings_addons.xml`, `strings_addon_entry.xml`, `drawable/ic_sohva_nav_*.xml` (kit: `assets/res/drawable/`, sources `assets/source/navigation-icons/svg/inside-discover/`).
