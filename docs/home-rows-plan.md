# Plan: Home row editor and Trakt list rows

28 September 2026. The owner's plan from before the rebuild (old repo,
`docs/HOME_ROWS_AND_TRAKT_LISTS_PLAN.md`, 23 September) restated for this code base and its rules.
Nothing here is implemented. This is new scope beyond beta 23 parity: decision "Spec 51 open
questions" Q2 (27 September) kept it out; the owner's go on this plan reverses that, and specs 02 and
51 get the new requirements before code (AGENTS §5.1: every capability has an inventory ID).

## What this version already has

- **The Trakt watched fix (old phase 1) shipped** in 0.2.0-beta.1: watched movies and shows are read
  page by page (`TraktApiClient.paged`, `X-Pagination-Page-Count`, 250 films / 100 shows with
  `extended=progress`, capped at 200 pages; a failed page fails the call), and an upgrade from beta
  23 does one full sync. Nothing left to do.
- **Home** has five rows, each with a stable key: `continue-watching`, `watch-next`, `todays-sport`,
  `recommended`, `recent-channels` (`HomeTypes.kt`). The order is fixed in one place,
  `HomeModel.build()`; the structure lock, first-row focus and the row list already work by key, with
  a `when` per row type. Each row's data is a separate flow into one `combine`, so a hidden row can
  simply not be collected.
- **A move control** exists in Discover › Organise (OK picks up, Up/Down and Page Up/Down move, OK
  places, Back cancels, focus follows the row). Its logic is small and generic; only its model is
  Discover-specific.
- **Per-profile settings** use `Profiles.key(base, id)` in DataStore; Trakt accounts and shelves are
  per profile in the encrypted Trakt store.
- **Backups** stay at format 2 while only optional keys are added, so they restore in beta 23 (whose
  reader ignores keys it does not know). Home is not in backups today.
- **The phone page** takes sources, keys, a logo and an addon list; a new kind is about six small
  edits in `core/net/phone` and `PhoneSetup`.
- **Trakt** today reads, with the viewer's token: last activities, playback, watched (paged), show
  progress, show details, recommendations. No watchlist, favourites, calendars, lists, charts or
  search, and no public (token-free) reads.

## Phase A: Home layout, order and visibility (medium)

- **Model** (`core/model`, pure Kotlin, unit-tested): `HomeLayout` = ordered `HomeRowEntry(id,
  visible)`. Ids are strings, never an enum (an unknown enum value makes a backup reader refuse the
  file). Built-in ids are today's row keys. Normalising drops unknown and duplicate ids, keeps the
  stored order and appends any built-in row the stored layout lacks, so a later version can add rows.
  Default = today's order, all shown.
- **Storage:** DataStore key `home_layout` per profile (`Profiles.key`), compact JSON; read with the
  start snapshot's profile so Home's first read does not wait (Home's first read is near its 1 s
  budget on the low-end stand-in; measure before and after, AGENTS §4.11). Removed with the profile.
  In the backup as an optional field of `ProfileKept`.
- **Home:** `HomeModel.build()` follows the layout; rows with no cards still hide themselves. A hidden
  row does no work: recent channels are not read; Watch next and Recommended are not refreshed by
  `TraktSyncLoop` when no profile shows them. The sport feed keeps running, because Search, the
  score ticker and reminders use it too. Restricted profiles still never get Trakt rows. The
  structure lock, first-row focus and Back behave as now (existing tests must pass unchanged, apart
  from the ones that index rows by position).
- **Settings › Home** (new section, names the active profile): one row per Home row with a show/hide
  switch (`SettingsSwitchRow`), Move (OK, Up/Down, OK; Back cancels), and Reset to default. The move
  logic is extracted from Discover's Organise into a shared component in `ui/design`, and Organise
  moves to it.
- **Tests:** normalising, JSON round trip, a new built-in row appended; device tests for the stored
  order, a hidden row absent and never loaded (gated read), first-row focus, move mode with real key
  events, a backup round trip and a beta 23-format restore check; the slow-box Home phase measured.

## Phase B: Trakt rows from the viewer's account and public charts (large)

- **Public reads:** a second request path that sends only `trakt-api-key` and the API version (no
  token), through the same `TraktGate` (pacing, 429 and Retry-After). Public charts then work without
  a connected account.
- **Sources** (a catalogue in `feature/trakt`, each with id, label, path, media type, needs-account,
  page size, cache lifetime):
  - Account: watchlist (films, shows, both), favourites, collection, up next, recently watched,
    top-rated by you, calendar (upcoming episodes, premieres, film releases), and the viewer's own
    and liked lists by name.
  - Public: trending, popular, anticipated, most watched/played/favourited (week, month, year, all
    time), box office (films), streaming charts, community calendars.
- **Rows:** about 30 items each, `extended=full,images`, shown with the existing Trakt card; OK goes
  through the existing Trakt title destination (library copy by TMDB id, else a Discover match, else
  "not in your sources"). A layout id `trakt:<source>` per row.
- **Cache (bounded, AGENTS §4.4):** per profile and row, at most 30 items, at most 8 added rows per
  profile; lifetimes per source (charts hours, account lists refreshed when Trakt's last activities
  move; the parser widens to watchlist, favourites, collection and list stamps). Stored like the two
  shelves today, or in a small Room table if the size grows (then a migration and its test).
  Refreshed after Home's first read, at background priority, never while video plays.
- **Playable titles:** each card checks the library by TMDB id (indexed `metadata_match.external_id`,
  one small query per row); cards in the library get a badge; each row has "Only titles in my
  library". Discover addons are not checked per card (a network call each); that stays at OK.
- **Settings › Home › Add a row:** a picker grouped "Your Trakt" (only with an account) and "Trakt
  charts"; the row joins the end of the layout, shown. Remove for added rows.
- **Risks to check first:** Trakt's API app still valid for the built-in client id (forum reports of
  VIP-only API apps since August 2026); free Trakt accounts are limited to one connected community
  app since July; chart images are third-party artwork (fine in the app, never in store screenshots).
- **Tests:** a parser per endpoint from recorded fixtures (fictional data), the public path's headers,
  cache lifetimes and caps, a hidden row never fetched, a device test drawing a row from fake data.

## Phase C: any public Trakt list by address, number or name (medium)

- "Add a Trakt list" accepts `https://trakt.tv/users/<user>/lists/<list>` (and the `app.trakt.tv`
  form), a numeric list id, or a name (Trakt list search, showing name, owner, size and likes).
- The list's summary is fetched first (name, owner, size, privacy); another person's private list
  cannot be read and says so in plain words. Stored as `trakt:list:<id>` with its name; first 30
  items in the list's own order.
- Typing an address with a remote is slow: the phone page gets a "Trakt list" mode where an address is
  pasted (and the TV search stays).
- **Tests:** the address/id/name parser, summary errors (401, 404, private), a device test adding a
  list from fake data, the phone submission.

## Owner decisions

1. **Scope:** take this in as milestone M12 (reverses decision "Spec 51 open questions" Q2).
   Recommended: yes, phase A first; it is useful on its own and lighter on slow boxes.
2. **Per profile or shared layout?** Recommended: per profile (Trakt accounts and restricted Home are
   per profile already).
3. **Titles you cannot play?** Recommended: show them, badge the ones in your library, per-row
   "library only" filter.
4. **Where to edit?** Recommended: Settings › Home first; a shortcut on Home later if needed.
5. **Adding custom lists:** recommended both the TV search and the phone page.
6. **Home layout in backups?** Recommended: yes, as an optional field (still restores in beta 23).
7. **Limit of added rows:** recommended 8 per profile (memory and refresh cost on slow boxes).

## Order of work

Phase A on its own branch through the emulator suites and the slow-box Home measurement, a Shield
preview, then a beta. Phases B and C after it, each through a Shield preview. Every new text in
seven languages; every capability an inventory ID; decisions recorded as they are made.

## Status (28 September 2026)

- Phase A done on branch `m12-home-rows`: spec 02 §4.14 (HOME-FR-86…93), inventory HOME-37…41 ticked, decisions
  recorded; unit tests (layout, storage, backup, Home rows and reads, Settings holder, Trakt without Watch next) and
  device tests (`HomeLayoutTest`: order, hidden rows, Reorder/Back/Show-hide/Reset with real keys, first focus with a
  slow row); the affected suites and `check_all` green; Home measured before and after (docs/performance-log.md).
