# Data model

> Everything Sohva TV `0.1.0-beta.23` (build 57) stores on the TV — databases, views, encrypted
> stores, preferences, files — with keys, lifetimes, per-profile scope, backup coverage and the hot
> queries; then the clean target schema for the rebuild and the owner's decision on existing
> users' data. The rebuild writes its schema and queries new
> ([AGENTS.md](../../../AGENTS.md) §3); the old schema is data, and its identity rules (§6) are a
> compatibility contract.

Related: [Architecture](03-architecture.md) (modules, threading, start-up),
[Sources and import](../specs/10-sources-and-import.md), [Backup](../specs/71-backup-restore.md),
[Security](../specs/73-security-privacy.md), [Performance](07-performance.md),
[Lessons](08-lessons-learned.md) §1–2.

## 1. Summary

- **Main database** `streammate.db`: Room, **version 29**, **24 tables and 5 views**, all in
  `core`. Unchanged since beta 14 (14 September 2026).
- **Discover databases**: three more Room files (`sohva-addons.db` v1, `sohva-addon-library.db`
  v1, `sohva-addon-progress.db` v4), one table each, payloads encrypted.
- **Preferences**: one DataStore file (`streammate_preferences`, about 45 keys, several per
  profile) plus nine SharedPreferences files (secrets, key envelopes, Trakt, Discover UI, updates,
  locale, artwork cache limit, a legacy secret file).
- **Files**: channel logos from the phone, the image disk cache, the Discover response cache, the
  sports match cache, downloaded updates.
- **Key material**: two Android Keystore AES-256-GCM keys (`sportmate.iptv.v1`, `sohva.addons.v1`),
  each wrapping a software data key (envelope encryption).
- **Target**: one main database with integer row ids, precomputed sort keys and visibility,
  keyset-paging indexes per screen, FTS4 search, EPG only for owned channels, per-profile tables,
  and a separate `discover.db` (§15). **Owner decision** on existing installs in §17; the
  recommendation is a one-time import of user data and secrets on first start, with providers
  re-imported.

## 2. Storage at a glance

| Store | Kind | Location (app data dir) | Owner module | Secret? |
|---|---|---|---|---|
| `streammate.db` | Room/SQLite, v29 | `databases/` | core | Stream URLs encrypted per row |
| `sohva-addons.db` | Room, v1 | `databases/` | addons | Payload encrypted |
| `sohva-addon-library.db` | Room, v1 | `databases/` | addons | Payload encrypted |
| `sohva-addon-progress.db` | Room, v4 | `databases/` | addons | Payload encrypted |
| `streammate_preferences` | DataStore Preferences | `files/datastore/streammate_preferences.preferences_pb` | core | No |
| `streammate_secure_sources` | SharedPreferences | `shared_prefs/` | core | Values encrypted |
| `streammate_secret_envelope` | SharedPreferences | `shared_prefs/` | core | Wrapped data key |
| `sportmate_secure_settings` | SharedPreferences (legacy) | `shared_prefs/` | core | Cleared after migration |
| `sohva_addon_secret_envelope` | SharedPreferences | `shared_prefs/` | app (Discover host) | Wrapped data key |
| `sohva_addon_ui` | SharedPreferences | `shared_prefs/` | app (Discover host) | No |
| `trakt_accounts` | SharedPreferences | `shared_prefs/` | app (Trakt) | Values encrypted |
| `streammate_updates` | SharedPreferences | `shared_prefs/` | app | No |
| `streammate_locale` | SharedPreferences (below Android 13) | `shared_prefs/` | core | No |
| `streammate_artwork_cache` | SharedPreferences | `shared_prefs/` | core | No |
| WorkManager's own database | Room (library) | `androidx.work.workdb`, location managed by the library | WorkManager | No |
| Files | see §11 | `files/`, `no_backup/`, `cache/` | various | Discover cache encrypted |

Android backup is off for every domain (`allowBackup=false`, `backup_rules.xml` and
`data_extraction_rules.xml` exclude root, file, database, sharedpref and external for cloud backup
and device transfer).

## 3. The main database

- File `streammate.db`, class `core/.../core/database/StreamMateDatabase.kt`, `version = 29`,
  `exportSchema = true`, identity hash `aace179637b096c4a8dcdba99d23c78b`.
- Built with `Room.databaseBuilder(…).addMigrations(…28 migrations…).build()`: default journal mode
  (Room's `AUTOMATIC`: WAL, or TRUNCATE on devices reporting low RAM), default executors, no
  destructive fallback.
- DAOs: `GuideDao` (872 lines), `CatalogueDao` (1,124), `MetadataDao`, `SportsCacheDao`,
  `OrganizationDao`, `RemindersDao`, `TraktStateDao`. Hot SQL is kept as constants in
  `GuideChannelQueries.kt`, `CatalogueHomeQueries.kt`, `SportsMatchQueries.kt`,
  `GuideSearchQueries.kt`, `TraktHomeQueries.kt`, `TraktProgressQueries.kt`, `OrganizationViews.kt`
  so JVM tests can run them against the exported schema.
- `analyze()` runs `ANALYZE` after each import activates (Room never does).
- **Snapshots.** Playlist, EPG and catalogue imports write a new snapshot (a random UUID in
  `snapshotId`) beside the active one, then activate it in one transaction by pointing
  `import_state.activeSnapshotId` at it and deleting every other snapshot of that source and kind.
  Readers always join through `import_state`, so they see the old snapshot until activation.

## 4. Tables (version 29)

Types are SQLite affinities as Room declares them. `NN` = NOT NULL. Booleans are INTEGER 0/1.
Times are epoch milliseconds (UTC). "Per profile" says whose data it is.

### 4.1 Sources and import state

**`iptv_source_state`** — the non-secret mirror of each configured source, so SQL can join on it.
PK `sourceId`. No other index. Household. Written from the encrypted source list on every
refresh and edit.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `sourceId` | TEXT | NN | Source id, `[A-Za-z0-9._-]{1,128}`; the legacy single source is `m3u-primary` |
| `name` | TEXT | NN | Display name (≤ 100 chars) |
| `type` | TEXT | NN | `M3U` or `XTREAM` |
| `enabled` | INTEGER | NN | Disabled sources vanish from every screen |
| `connectionLimit` | INTEGER | NN | Concurrent streams allowed, 1–16, default 1 |
| `priority` | INTEGER | NN | Higher first in the guide and in duplicate resolution |
| `updatedAtEpochMillis` | INTEGER | NN | Last write |
| `epgOffsetMinutes` | INTEGER | NN, default 0 | Guide time correction, −720…720 in 30-minute steps; added to programme times at read |

**`import_state`** — which snapshot is live. PK (`sourceId`, `kind`). Household.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `sourceId` | TEXT | NN | Source |
| `kind` | TEXT | NN | `playlist`, `epg` or `catalogue` |
| `activeSnapshotId` | TEXT | NN | UUID of the active snapshot |
| `updatedAtEpochMillis` | INTEGER | NN | Activation time |
| `itemCount` | INTEGER | NN | Rows activated (channels, programmes, or movies + series) |

**`source_refresh_state`** — health line per source and kind. PK (`sourceId`, `kind`). Household.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `sourceId`, `kind` | TEXT | NN | As above |
| `status` | TEXT | NN | `running`, `success`, `failed` |
| `lastAttemptAtEpochMillis` | INTEGER | NN | |
| `lastSuccessAtEpochMillis` | INTEGER | null | |
| `lastFailureAtEpochMillis` | INTEGER | null | |
| `lastError` | TEXT | null | Redacted failure: `resource:<id>\t<args>` or redacted text (see [03](03-architecture.md) §2.8) |
| `itemCount` | INTEGER | NN | Items of the last success |
| `consecutiveFailures` | INTEGER | NN | Reset on success |

### 4.2 Live channels

**`iptv_channels`** — one row per channel per snapshot. PK (`sourceId`, `snapshotId`,
`channelId`). Indices: (`sourceId`, `snapshotId`), (`sourceId`, `tvgId`), (`channelId`) — the last
because launching a channel filters on `channelId` alone. Household.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `sourceId` | TEXT | NN | Source |
| `snapshotId` | TEXT | NN | Playlist snapshot UUID |
| `channelId` | TEXT | NN | Global channel id `<sourceId>:<localId>` (§6) |
| `tvgId` | TEXT | null | `tvg-id` (M3U) or `epg_channel_id` (Xtream) |
| `name` | TEXT | NN | Provider name (fallback `Kanava <n>` for a nameless M3U entry) |
| `normalizedName` | TEXT | NN | Normalised name (§6) |
| `groupTitle` | TEXT | null | Provider group |
| `logoUrl` | TEXT | null | Provider logo |
| `encryptedStreamUrl` | TEXT | NN | Stream URL, envelope-encrypted (§9) |
| `userAgent`, `referrer` | TEXT | null | Per-channel HTTP headers from the playlist |
| `lastSeenEpochMillis` | INTEGER | NN | Import time |
| `playlistOrder` | INTEGER | NN, 2147483647 | Position in the playlist |
| `catchupType`, `catchupSource` | TEXT | null | Catch-up scheme and template |
| `catchupDays` | INTEGER | null | Archive depth in days (M3U values clamped to 1–365) |
| `xtreamStreamId` | TEXT | null | Xtream stream id for catch-up |
| `catchupTimeZone` | TEXT | null | Provider archive time zone |
| `organizationGroupKey`, `organizationNameKey` | TEXT | NN, `''` | Normalised group key (both the same for provider groups) |
| `channelNumber` | INTEGER | null | Playlist's number (`tvg-chno` / Xtream `num`) |

**`channel_preferences`** — the household's edits of one channel. PK `channelId`. Index
`sourceId`. Survives re-imports (keyed by the global channel id, not the snapshot). Up to 100,000
customised channels.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `channelId` | TEXT | NN | Global channel id |
| `sourceId` | TEXT | NN | Source (for cascade delete) |
| `customName` | TEXT | null | Viewer's name |
| `customGroupTitle` | TEXT | null | Viewer's group (moves the channel) |
| `hidden` | INTEGER | NN | Legacy per-channel hide (still honoured by the views) |
| `sortOrder` | INTEGER | null | Viewer's position (overrides playlist order) |
| `manualXmltvChannelId` | TEXT | null | Manual EPG mapping |
| `updatedAtEpochMillis` | INTEGER | NN | |
| `customOrganizationGroupKey` | TEXT | NULL | Normalised key of `customGroupTitle` |
| `customLogoUrl` | TEXT | NULL | Viewer's logo: an address or a `file://` path in `files/channel-logos` |
| `channelNumber` | INTEGER | NULL | Viewer's number (null keeps the playlist's) |

**`channel_lists`** — custom channel lists. PK `listId`. Household; at most 1,000 lists.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `listId` | TEXT | NN | UUID |
| `name` | TEXT | NN | List name |
| `sortOrder` | INTEGER | NN | Position among lists |
| `updatedAtEpochMillis` | INTEGER | NN | |

**`channel_list_members`** — PK (`listId`, `channelId`). Index `channelId`. At most 500,000 members.
Columns `listId` TEXT NN, `channelId` TEXT NN (global id), `sortOrder` INTEGER NN.

### 4.3 Programme guide

**`xmltv_channels`** — every channel of the XMLTV feed (kept whole so manual mapping can offer any
of them). PK (`sourceId`, `snapshotId`, `xmltvChannelId`). Index (`sourceId`, `snapshotId`).
Columns `displayName` TEXT null, `iconUrl` TEXT null.

**`tv_programmes`** — PK (`sourceId`, `snapshotId`, `programmeId`). Indices (`sourceId`,
`snapshotId`) and (`sourceId`, `xmltvChannelId`, `startEpochMillis`, `stopEpochMillis`). Household.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `sourceId`, `snapshotId` | TEXT | NN | Source and EPG snapshot |
| `programmeId` | TEXT | NN | 16 hex chars of SHA-256 of `channel|start|stop|title` (§6) |
| `xmltvChannelId` | TEXT | NN | XMLTV channel id the programme belongs to |
| `startEpochMillis`, `stopEpochMillis` | INTEGER | NN | Times as the feed gives them (the source offset is added at read) |
| `title` | TEXT | NN | |
| `subtitle`, `description` | TEXT | null | |
| `categories` | TEXT | NN | Categories joined by U+001F |

Import rules today: only programmes whose XMLTV id is referenced by an active channel of the
source (by `tvgId` or manual mapping) are stored, unless no channel references any id; only
programmes that end after now − 12 h and start before now + 8 days; batches of 2,000; an empty or
unmatched feed never replaces the active guide (`error_epg_empty`, `error_epg_unmatched`).

### 4.4 Movies and series

**`vod_movies`** — PK (`sourceId`, `snapshotId`, `movieId`). Indices (`sourceId`, `snapshotId`),
(`normalizedName`), (`categoryKey`), (`sourceId`, `movieId`) (resume lookups skip the snapshot).
Household.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `sourceId`, `snapshotId` | TEXT | NN | Source and catalogue snapshot |
| `movieId` | TEXT | NN | Xtream `stream_id`, or the SHA-256 entry id for M3U (§6) |
| `name` | TEXT | NN | Provider title |
| `normalizedName` | TEXT | NN | Normalised title |
| `categoryName` | TEXT | null | Provider group |
| `categoryKey` | TEXT | null | `lower(trim(categoryName))` (ROOT locale) for indexed group walls |
| `posterUrl` | TEXT | null | Provider poster |
| `encryptedStreamUrl` | TEXT | NN | Encrypted stream URL |
| `year` | INTEGER | null | |
| `rating` | TEXT | null | Provider rating text |
| `plot` | TEXT | null | |
| `organizationGroupKey`, `organizationNameKey` | TEXT | NN, `''` | Normalised group key |

**`vod_series`** — same shape (PK with `seriesId`, plus `backdropUrl`, no stream URL). Indices
(`sourceId`, `snapshotId`), (`normalizedName`), (`categoryKey`).

**`vod_episodes`** — PK (`sourceId`, `seriesId`, `episodeId`); **no snapshot column**. Indices
(`sourceId`, `seriesId`, `seasonNumber`, `episodeNumber`) and (`sourceId`, `episodeId`).
Columns `seasonNumber` INTEGER NN, `episodeNumber` INTEGER NN, `name` TEXT NN,
`encryptedStreamUrl` TEXT NN, `plot` TEXT null, `durationSeconds` INTEGER null, `thumbnailUrl`
TEXT null. M3U episodes arrive with the catalogue import; Xtream episodes are fetched when a series
is opened and replace that series' rows; orphans are deleted when a catalogue snapshot activates.

### 4.5 Viewer data

**`playback_progress`** — resume positions and watched state. PK (`contentKey`, `profileId`).
Indices `sourceId`, `contentType`, `lastWatchedEpochMillis`, `workKey`. **Per profile**.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `contentKey` | TEXT | NN | `vod:movie:<sourceId>:<movieId>` or `vod:episode:<sourceId>:<episodeId>` |
| `sourceId` | TEXT | NN | |
| `contentType` | TEXT | NN | `movie` or `episode` |
| `itemId` | TEXT | NN | Movie or episode id |
| `positionMillis`, `durationMillis` | INTEGER | NN | |
| `completed` | INTEGER | NN | Watched |
| `lastWatchedEpochMillis` | INTEGER | NN | |
| `workKey` | TEXT | null | Film identity, so any copy of a film finds the position; null for episodes and old rows |
| `profileId` | TEXT | NN, `'default'` | Viewer |

**`reminders`** — PK `id`. Index `startEpochMillis`. **Household** (not per profile).

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | TEXT | NN | `event:<eventId>` or `programme:<channelId>:<programmeId>` |
| `kind` | TEXT | NN | `event` or `programme` |
| `eventId`, `channelId` | TEXT | null | What to open |
| `title` | TEXT | NN | |
| `subtitle` | TEXT | null | |
| `startEpochMillis` | INTEGER | NN | Start; the alarm fires 60 s before |
| `createdAtEpochMillis` | INTEGER | NN | |

### 4.6 Metadata

**`metadata_cache`** — TMDB/TVmaze lookups. PK (`lookupKey`, `provider`). Indices
`expiresAtEpochMillis`, `externalId`. Household.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `lookupKey` | TEXT | NN | SHA-256 hex of `4␟mediaType␟normalisedTitle␟year␟season␟episode␟language` (␟ = U+001F) |
| `provider` | TEXT | NN | `tmdb` or `tvmaze` |
| `status` | TEXT | NN | `positive` or `negative` |
| `externalId` | TEXT | null | Provider id |
| `mediaType` | TEXT | NN | |
| `matchedTitle`, `displayTitle`, `overview`, `posterUrl`, `backdropUrl` | TEXT | null | |
| `year`, `seasonNumber`, `episodeNumber`, `runtimeMinutes` | INTEGER | null | |
| `rating` | TEXT | null | |
| `castJson`, `genresJson`, `similarMoviesJson` | TEXT | null | JSON payloads |
| `genresVersion` | INTEGER | NN, 0 | Genre vocabulary version (current 2) |
| `pinned` | INTEGER | NN, 0 | Chosen by hand (fix a match): never expires, never replaced |
| `detailsLoaded` | INTEGER | NN, 0 | Details fetched |
| `attributionName`, `attributionUrl` | TEXT | NN | |
| `confidence` | REAL | NN | ≥ 0.92 and 0.08 above the runner-up to attach; 1.0 when chosen by hand |
| `cachedAtEpochMillis`, `expiresAtEpochMillis` | INTEGER | NN | TMDB 30 days, TVmaze 24 h, negative 7 days |

**`catalogue_metadata_overrides`** — what the library shows for a title. PK `contentKey`
(`vod:movie:…` or `series:<sourceId>:<seriesId>`). Index `externalId`. Columns
`providerPosterUrl` TEXT null, `replacementPosterUrl` TEXT null, `replaceProviderPoster` INTEGER NN,
`replacementTitle` TEXT NN, `externalId` TEXT null (the matched record; recognises two copies of one
film), `genresVersion` INTEGER NN default 0, `updatedAtEpochMillis` INTEGER NN.

**`catalogue_genres`** — PK (`contentKey`, `genre`). Index `genre`. One primary genre per title
since version 22 (wire values of `CatalogueGenre`). Outlives `metadata_cache`.

**`catalogue_metadata_work`** — the enrichment queue. PK `contentKey`. Index (`state`,
`nextAttemptAtEpochMillis`, `contentKey`). Columns `mediaType`, `title` (TEXT NN), `year` INTEGER
null, `providerPosterUrl` TEXT null, `targetGenresVersion` INTEGER NN, `state` TEXT NN (`pending`,
`retry`, `complete`, `no_match`), `attemptCount` INTEGER NN (max 16), `nextAttemptAtEpochMillis`
INTEGER NN (retry 15 min doubling to 24 h), `updatedAtEpochMillis` INTEGER NN (stamp-and-sweep:
rows not touched by a synchronisation pass are deleted).

### 4.7 Library organisation

**`organization_rules`** — show/hide, sort and position rules. PK (`room`, `sourceId`, `groupKey`,
`itemKey`). Index `itemKey`. Household.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `room` | TEXT | NN | `LIVE`, `MOVIES`, `SERIES` |
| `sourceId` | TEXT | NN | Source, or `''` for the combined library |
| `groupKey` | TEXT | NN | Normalised group key, or `''` for an item rule outside a group |
| `itemKey` | TEXT | NN | Channel id / `vod:movie:…` / `series:…` / film identity `work:<key>`, or `''` for a group rule |
| `enabled` | INTEGER | null | 1 shown, 0 hidden, null no opinion |
| `sortMode` | TEXT | null | `PROVIDER`, `TITLE_ASC`, `TITLE_DESC`, `NEWEST`, `OLDEST`, `RATING`, `MANUAL` |
| `position` | INTEGER | null | Manual position |

A marker row (`LIVE`, `''`, `@legacy-v1`, `''`, enabled 1) records that the legacy hidden-category
preferences were migrated into rules.

**`organization_aliases`** — film identity. PK `alias`. Index `identity`. `alias`
`vod:movie:<sourceId>:<movieId>` → `identity` `work:<workKey>`; one row per film copy (about
200,000 at owner scale), written after each catalogue import and by the identity pass.

### 4.8 Sohva Sport

**`sports_api_cache`** — API-Sports responses. PK `cacheKey`. Index `staleUntilEpochMillis`.
Columns `sport`, `kind` (`competitions`, `events`, `incidents`), `payload` (response JSON, response
cap 4 MiB), `source` (for example `api-sports-football`) — all TEXT NN; `quotaRemaining` INTEGER
null; `fetchedAtEpochMillis`, `expiresAtEpochMillis`, `staleUntilEpochMillis` INTEGER NN. TTLs:
today's events 20 min, past days 24 h, future days 2 h, incidents 2 min, competitions 7 days;
stale copies usable for 24 h more.

**`event_channel_decisions`** — the viewer's confirm/reject of a stream pairing. PK (`eventId`,
`channelId`). Index `eventId`. `decision` TEXT NN (`confirmed`, `rejected`),
`updatedAtEpochMillis` INTEGER NN. Household.

**`team_aliases`** — PK (`sport`, `normalizedCanonicalName`, `normalizedAlias`). Index `sport`.
Read by the matcher and the generation query; no code in beta 23 writes it (the writer
`TeamAliasRepository.addAlias` has no caller).

### 4.9 Trakt

**`trakt_state`** — the profile's Trakt playback and watched lists, a cache replaced wholesale per
sync. PK (`profileId`, `key`). Indices (`profileId`, `tmdb`), (`profileId`, `imdb`). **Per profile**.
`key` is `movie:<tmdb>` / `episode:<showTmdb>:<season>:<number>` (IMDb forms when TMDB is unknown);
`kind` `movie`/`episode`; `tmdb` INTEGER null; `imdb` TEXT null; `season`, `number` INTEGER null;
`progress` REAL NN (percent, 0 when none); `watched` INTEGER NN; `plays` INTEGER NN;
`updatedAtMillis` INTEGER NN. Details: [specs/51](../specs/51-trakt.md).

## 5. Views

All five are Room `@DatabaseView`s (`OrganizationViews.kt`), created by migration 22→23.

| View | Shape | Used by |
|---|---|---|
| `organization_memberships` | `UNION ALL` of three selects: active channels of enabled sources (with the viewer's custom group), active movies (with their film identity from `organization_aliases`), active series; columns `room`, `sourceId`, `itemKey`, `identity`, `groupKey`, `nameKey`, `legacyHidden` | No main-code query in beta 23 (tests only) |
| `organization_eligible_items` | `organization_memberships` filtered by the rule predicate below | No main-code query in beta 23 (tests only) |
| `organization_visible_movies` | `SELECT m.* FROM vod_movies m LEFT JOIN organization_aliases a ON a.alias = 'vod:movie:'‖source‖':'‖movieId WHERE <predicate>` | Movie walls, Continue watching, Trakt rows |
| `organization_visible_series` | `SELECT m.* FROM vod_series m WHERE <predicate>` | Series walls, Continue watching |
| `organization_visible_channels` | `SELECT m.* FROM iptv_channels m LEFT JOIN channel_preferences p ON p.channelId = m.channelId WHERE <predicate>` | Guide timeline, guide search |

The predicate is three `COALESCE(…) = 1` tests, each over correlated
`SELECT r.enabled FROM organization_rules r WHERE …` look-ups, **16 per row**:

1. Item rule outside a group: source-specific then combined (`sourceId = ''`), by identity then by
   item key (4 look-ups); default `1 − legacyHidden`.
2. Group rule (`itemKey = ''`): source then combined, by group key then name key (4); default 1.
3. Item rule inside its group: source then combined × group/name key × identity/item key (8);
   default 1.

The views do not filter by enabled source or active snapshot; queries join those themselves.
Joined in the wrong order they evaluate the 16 look-ups for every row of a large table
([Lessons](08-lessons-learned.md) 2.1). Two inlined forms exist for hot reads:
`ORGANIZATION_VISIBLE_LIVE_PREDICATE` (the same predicate over `c`/`preference`, applied after
narrower filters) and `ORGANIZATION_VISIBLE_LIVE_SOURCE_PREDICATE` (skips the channel look-ups
when no channel-level rule exists; 360 → 130 ms for 50,000 channels on a desktop).

## 6. Keys and identities (the compatibility contract)

These strings appear in the database, the preferences, backups (`.smbak`), reminders and Trakt
mapping. Any path that keeps existing users' data (options B and C in §17) must reproduce them
exactly; they are pure logic under 100 lines and may be re-implemented with the old tests as cases
([AGENTS.md](../../../AGENTS.md) §3).

| Identity | Rule |
|---|---|
| Source id | `[A-Za-z0-9._-]{1,128}`; legacy single source `m3u-primary` |
| Name normalisation (`ChannelNameNormalizer`) | Unicode NFKD → remove `\p{M}+` → `lowercase(Locale.ROOT)` → replace `[^a-z0-9]+` with a space → trim → collapse whitespace. Non-Latin letters become spaces |
| M3U channel local id | lowercase hex SHA-256 (UTF-8) of `"<tvgId>|<normalisedName>"` when `tvg-id` is present, else `"<normalisedName>|<streamUrl>"`; nameless entries are named `Kanava <index>` |
| Xtream channel local id | `xtream-<stream_id>` |
| Global channel id | `<sourceId>:<localId>` |
| M3U VOD entry id | the same SHA-256 rule as channels; M3U series id `stableId("<seriesName>|<groupTitle>")` (SHA-256 hex) |
| Xtream VOD ids | `stream_id` for movies, `series_id` for series, provider episode id for episodes |
| Content keys | `vod:movie:<sourceId>:<movieId>`, `vod:episode:<sourceId>:<episodeId>`, `series:<sourceId>:<seriesId>` |
| Film identity | `work:<workKey>`; `catalogueWorkKey` gives `tmdb:<id>` when the title has a TMDB match, else `name:<cleaned title>:<year or empty>` (title cleanup by the metadata matcher, `CatalogueWorkKey.kt`, `MetadataMatcher.kt`). The `tmdb:` form is stable; the `name:` form depends on the cleanup rules, so an import must re-key film rules through their alias family rather than assume the rebuild derives the same string |
| Programme id | first 16 hex chars of SHA-256 of `"<xmltvChannelId>|<startMillis>|<stopMillis>|<title>"` |
| Reminder id | `event:<eventId>` or `programme:<channelId>:<programmeId>` |
| Organisation group key | `id:<providerGroupId>` when a provider id is given, else `name:` + `trim(title).lowercase(Locale.ROOT)` (empty title → `name:`); reserved keys `@groups`, `@history`, `@recent`, `@favourites`, `@legacy-v1` (`core/model/LibraryOrganization.kt`). Also used by restricted profiles' allowed groups |
| Sport competition key | `<SPORT>:<id>`, for example `FOOTBALL:39` |
| Profile id | `default` for the first viewer, `p<epochMillis>` for others |
| Snapshot id | random UUID (never referenced outside the database) |

## 7. Schema history and migrations

Exported schemas (Room JSON, one file per version):

- `core/schemas/com.streammate.tv.core.database.StreamMateDatabase/1.json` … `29.json`
- `addons/schemas/com.sohva.tv.addons.storage.AddonDatabase/1.json`
- `addons/schemas/com.sohva.tv.addons.storage.AddonLibraryDatabase/1.json`
- `addons/schemas/com.sohva.tv.addons.storage.AddonProgressDatabase/1.json` … `4.json`

| Step | Change | Added (private commit, date) |
|---|---|---|
| 1→2 | `team_aliases`, `event_channel_decisions` | `34e9c5b`, 24 Aug 2026 |
| 2→3 | Multi-source: channels, XMLTV channels, programmes and import state rebuilt with `sourceId` (legacy rows get `m3u-primary`, channel ids prefixed `m3u-primary:`); `iptv_source_state`, `source_refresh_state` created; decisions' channel ids prefixed | `6679bc7`, 24 Aug |
| 3→4 | `channel_preferences`, `channel_lists`, `channel_list_members` | `94c4501`, 24 Aug |
| 4→5 | Catch-up columns and `xtreamStreamId` on channels | `772995f`, 25 Aug |
| 5→6 | `vod_movies`, `vod_series`, `vod_episodes`, `playback_progress` | `feabf03`, 25 Aug |
| 6→7 | `metadata_cache` | `05aca64`, 25 Aug |
| 7→8 | `sports_api_cache` | `0240b1e`, 25 Aug |
| 8→9 | `iptv_channels.playlistOrder` (backfilled from rowid) | `f7d1836`, 25 Aug |
| 9→10 | `iptv_source_state.epgOffsetMinutes` | `e136625`, 28 Aug |
| 10→11 | Episode thumbnails; runtime, rating, cast in the metadata cache | `6a03e21`, 28 Aug |
| 11→12 | `catalogue_metadata_overrides` | `30580dd`, 28 Aug |
| 12→13 | `detailsLoaded`, `similarMoviesJson` | `147d2be`, 29 Aug |
| 13→14 | Indexes on `iptv_channels(channelId)`, `vod_movies(sourceId, movieId)`, `vod_episodes(sourceId, episodeId)` | `b2974cb`, 29 Aug |
| 14→15 | `genresJson`, `catalogue_genres` | `97c7264`, 4 Sept (versions 15–23 in one commit) |
| 15→16 | `genresVersion` on overrides and cache | |
| 16→17 | `metadata_cache.pinned` | |
| 17→18 | `catalogue_metadata_overrides.externalId` | |
| 18→19 | `playback_progress.workKey` + index | |
| 19→20 | `catalogue_metadata_work` + index | |
| 20→21 | `categoryKey` on movies and series (+ indexes, backfilled with `LOWER(TRIM())`); `playback_progress(contentType)` index | |
| 21→22 | One primary genre per title (keeps `MIN(genre)`); genre version 1 → 2 | |
| 22→23 | `organization_rules`, `organization_aliases`, group/name keys on channels, movies and series (backfilled in Kotlin because SQLite `LOWER` is ASCII-only), `customOrganizationGroupKey`, the five views | |
| 23→24 | `reminders` | `d10fcd4`, 7 Sept (beta 8) |
| 24→25 | `playback_progress` rebuilt with PK (`contentKey`, `profileId`) | `cd802e2`, 7 Sept (beta 10) |
| 25→26 | Viewer's logo and number; playlist number | `9fc17a8`, 8 Sept (beta 12) |
| 26→28, 27→28 | Drop `viewing_projection_receipts` (27 came only from a private Trakt trial build, codes 16–18) | `08e0452`, 14 Sept |
| 28→29 | `trakt_state` + indexes; index on `catalogue_metadata_overrides(externalId)` | `da4dec8`, 14 Sept (beta 14) |

Public betas: beta 3 shipped schema 23, beta 8 → 24, beta 10 → 25, beta 12 and 13 → 26, beta 14
onward → 29 (with Discover progress v4). Testers may still run any of these.

Discover: `sohva-addons.db` and `sohva-addon-library.db` have only version 1;
`sohva-addon-progress.db` versions 2 and 3 came from the private Trakt trial build; migrations
1→4, 2→4 and 3→4 only drop `addon_projection_receipts` and `addon_progress_display`.

## 8. Discover databases

Full details in [specs/50](../specs/50-discover-addons.md) §6. **All per profile.**

| Database | Table | Columns and keys | Limits |
|---|---|---|---|
| `sohva-addons.db` v1 | `addon_installations` | `installationId` TEXT PK (UUID), `profileId` TEXT, `endpointFingerprint` TEXT, `encryptedPayload` TEXT (`{"url","manifest"}`), `enabled` INTEGER, `position` INTEGER, `revision` INTEGER, `updatedAtMillis` INTEGER; unique index (`profileId`, `endpointFingerprint`) | 128 addons per profile |
| `sohva-addon-library.db` v1 | `addon_library` | `key` TEXT PK (hash of profile, installation, type, id), `profileId`, `encryptedPayload`, `addedAtMillis`; index (`profileId`, `addedAtMillis`) | 1,000 titles per profile (checked in the insert transaction; list `LIMIT 1001`) |
| `sohva-addon-progress.db` v4 | `addon_progress` | `key` TEXT PK (hash of profile, metadata installation, media type/id, video type/id), `profileId`, `encryptedPayload`, `updatedAtMillis`; index (`profileId`, `updatedAtMillis`) | 200 rows per profile (pruned on every write) |

Hash keys are lowercase hex SHA-256 of the parts written as `<length>:<value>` concatenated
(`addonCacheKey`).

## 9. Encrypted stores and key material

| Item | Where | Format |
|---|---|---|
| Keystore key (main) | Android Keystore alias `sportmate.iptv.v1` | AES-256, GCM, no padding, created on first use |
| Main data key | `streammate_secret_envelope` › `data_key_v2` | 32 random bytes as hex, encrypted with the Keystore key in the `v1` format below; written with `apply()` |
| `v1` values | anywhere (older rows) | `v1:<iv hex>:<ciphertext hex>`, AES/GCM with the Keystore key, AAD `streammate-secret-v1`, IV from the provider |
| `v2` values | everything written since the envelope | `v2:<iv hex>:<ciphertext hex>`, AES/GCM with the software data key, 12-byte random IV, 128-bit tag, AAD `streammate-secret-v2` |
| Keystore key (Discover) | alias `sohva.addons.v1` | Same scheme |
| Discover data key | `sohva_addon_secret_envelope` › `data_key` | Written with `commit()` before any ciphertext that uses it |
| Encrypted sources | `streammate_secure_sources` › `sources_v1` | Cipher text of a Base64 binary list: magic `0x53544D53`, version 3 (1 legacy, 2 added import scope, 3 added EPG offset), count, then per source id, name, type, enabled, connection limit, priority, M3U URL, XMLTV URL, Xtream base URL, username, password (nullable strings), import scope, EPG offset; at most 100 sources |
| Parental PIN | `streammate_secure_sources` › `parental_pin_v1` | Encrypted 4–8 digits; compared in constant time |
| TMDB | `metadata_tmdb_enabled_v1` (boolean), `metadata_tmdb_token_v1` (encrypted, ≤ 2,048 chars), `metadata_tvmaze_enabled_v1` (boolean) | Same file |
| API-Sports key | `sports_api_key_v1` (encrypted, ≤ 512 chars) | Same file |
| Legacy secrets | `sportmate_secure_settings` › `m3u`, `xmltv` | Migrated into one source `m3u-primary`, then the file is cleared |
| Trakt | `trakt_accounts` › `account:<profile>` (encrypted JSON: tokens, username, re-authorisation flag), `pending:<profile>` (encrypted, ≤ 200 undelivered scrobbles), `active:<profile>` (encrypted current scrobble), `activity:<profile>` (long), `recommendations:<profile>`, `nextup:<profile>` (cached Home rows) | Main cipher |
| Stream URLs | `encryptedStreamUrl` columns | Main cipher |
| Discover payloads and response cache | Discover databases and `no_backup/addon-responses` | Discover cipher |

Same application ID and signing key keep the same Linux UID, so an updated app (including the
rebuild) can use both Keystore keys and both wrapped data keys. Clearing app data destroys them
together with everything they protect.

## 10. Preferences

### 10.1 DataStore `streammate_preferences`

Keys marked `[:id]` are per profile: the default profile uses the bare key, others append
`:<profileId>`. Types: s = string, b = boolean, set = string set.

| Key | Type | Default when absent | Meaning |
|---|---|---|---|
| `time_zone` | s | absent = follow the TV's zone | Zone for guide, catch-up and sport times |
| `profiles` | s | no added profiles | Records separated by U+001E, fields by U+001F: `id`, `name` (≤ 24), `colorIndex` (0–5); at most 6 profiles in all |
| `active_profile_id` | s | `default` | Active viewer |
| `ask_profile_at_start` | b | true | Ask who is watching at start |
| `allowed_groups_live[:id]`, `allowed_groups_movies[:id]`, `allowed_groups_series[:id]` | set | absent = unrestricted | Restricted profile's allowed group keys |
| `favourite_channel_ids[:id]` | set | empty | Favourite channels (global ids) |
| `favourite_event_ids[:id]` | set | empty | Followed sport events |
| `recent_channel_ids[:id]` | s | empty | Ids joined by U+001F, newest first, at most 20 |
| `last_channel_id[:id]` | s | none | Last channel watched |
| `locked_channel_ids[:id]` | set | empty | Locked channels (dropped for everyone when the PIN is removed) |
| `last_guide_source_id` | s | none | Guide's last source (≤ 128) |
| `startup_screen` | s | `HOME` | `HOME`, `GUIDE`, `LAST_CHANNEL` |
| `parental_pin_configured` | b | false | Mirrors the secure store; reconciled at start |
| `remote_channel_key_mode` | s | `DPAD_AND_CHANNEL_KEYS` | Legacy; `CHANNEL_KEYS_ONLY` also possible; used to derive mappings |
| `remote_mappings` | set | built-in defaults | `BUTTON.GESTURE=ACTION` entries (non-default, non-fixed slots only) |
| `metadata_language` | s | `fi-FI` if the interface is Finnish, else `en-US` | One of 22 TMDB tags |
| `interface_scale` | s | `NORMAL` | `NORMAL` 1.0, `COMPACT` 0.9, `SMALL` 0.8, `SMALLER` 0.7 |
| `color_theme` | s | `original` | `original`, `nordic_slate`, `cozy_hearth`, `cyber_plum`, `nord`, `everforest`, `kanagawa` |
| `auto_frame_rate` | b | true | |
| `auto_play_next_episode` | b | true | |
| `picture_in_picture` | b | false | |
| `editors_show_hidden` | b | true | Editors list hidden items |
| `show_channel_numbers` | b | true | |
| `followed_sports` | set | `FOOTBALL`, `ICE_HOCKEY`, `AUSTRALIAN_FOOTBALL` | Of 12 `SportType`s |
| `followed_competitions` | set | `FOOTBALL:2`, `:3`, `:39`, `:78`, `:135`, `:140`, `:848`, `ICE_HOCKEY:16`, `AUSTRALIAN_FOOTBALL:1` | Competition keys |
| `sports_channel_priority` | s | empty | Comma-separated language codes, normalised, at most 8 |
| `playlist_epg_refresh_interval` | s | `TWENTY_FOUR_HOURS` | `ONE_HOUR`, `TWO_HOURS`, `FOUR_HOURS`, `TEN_HOURS`, `TWENTY_FOUR_HOURS` |
| `playback_buffer_profile` | s | `DEFAULT` | `DEFAULT`, `LOW_LATENCY`, `STABILITY` |
| `playback_seek_step` | s | `TEN_SECONDS` | `TEN_SECONDS`, `THIRTY_SECONDS`, `ONE_MINUTE`, `TWO_MINUTES` |
| `subtitle_text_size` | s | `FOLLOW_TV` | `SMALL` 0.8, `NORMAL` 1.0, `LARGE` 1.3, `VERY_LARGE` 1.6 |
| `subtitle_text_color` | s | `FOLLOW_TV` | `WHITE` `#FFFFFFFF`, `YELLOW` `#FFFFE14D` |
| `subtitle_background` | s | `FOLLOW_TV` | `NONE`, `SHADOW`, `BOX` |
| `playback_reconnect_policy` | s | `STANDARD` | `STANDARD` (3 attempts after 2, 4, 6 s), `PERSISTENT` (8 attempts) |
| `hidden_live_categories`, `hidden_movie_categories`, `hidden_series_categories` | set | empty | Legacy; migrated once into organisation rules |
| `preferred_audio_language`, `secondary_audio_language`, `preferred_subtitle_language`, `secondary_subtitle_language` | s | none | Lower-case language codes |
| `custom_catalogue_groups` | s | `[]` | JSON array of `{id, name, genres[], fromYear?, toYear?, minRating?}`; at most 24 groups, names ≤ 40 chars; unreadable entries dropped |
| `preferred_catalogue_copy` | s | `NONE` | `NONE`, `FINNISH_AUDIO`, `FINNISH_SUBTITLES`, `LARGEST_PICTURE` |
| `manager_group_<ROOM>`, `manager_source_<ROOM>` | s | none | Library manager's last position per room (≤ 2,048 chars) |
| `reminder_overlay_asked` | b | false | The overlay-permission prompt was shown |
| `movie_identity_mark` | s | none | Catalogue state the film-identity pass last completed for (≤ 4,096) |

Every edit rewrites the whole file, and the whole object is re-emitted to every collector
([03](03-architecture.md) §2.4).

### 10.2 Small synchronous preference files

| File | Key | Values | Why synchronous |
|---|---|---|---|
| `streammate_locale` | `language_tag` | `en`, `fi`, `es`, `pt`, `de`, `sv`, `it`; absent = follow the system | Applied in `attachBaseContext` below Android 13; from 13 the platform `LocaleManager` holds it |
| `streammate_artwork_cache` | `limit` | `SMALL` 100 MB, `MEDIUM` 250 MB (default), `LARGE` 500 MB | Coil's disk cache fixes its size when built |
| `streammate_updates` | `last_check_epoch_millis`, `installed_notes_<versionCode>` | Long; release notes text | Daily check throttle; "Changed since" notes |
| `sohva_addon_ui` | `all_subtitle_languages` (b, false, global); `catalog_order_<hash>`, `catalog_hidden_<hash>` (JSON arrays, per profile) | | Discover UI state |

## 11. Files on disk

| Path | Content | Limit and lifetime |
|---|---|---|
| `files/channel-logos/<prefix>-<millis>.png` | Logos sent from the phone | Decoded and scaled to at most 256 px a side, input ≤ 2,000,000 bytes; the previous file of the channel is deleted on replace; carried in backups as bytes |
| `cache/catalogue_artwork/` | Coil disk cache | 100/250/500 MB; the system may clear it; "Clear artwork cache" in Settings |
| `no_backup/addon-responses/<sha256>.cache` | Encrypted Discover responses | 32 MiB and 128 files, least recently read evicted; temp files `response-*.tmp` |
| `cache/sports-channel-matches.bin` | Stream pairing results: binary, version 3, input generation fingerprint | Entries older than 24 h dropped; whole file invalid when the generation changes |
| `cache/updates/` | Downloaded APK and `.dm` profile | Cleared before each download; a checksum mismatch deletes the file; profile ≤ 1 MiB |
| `databases/`, `files/datastore/`, `shared_prefs/` | §2 | |

## 12. Per-profile data and backup contents

### 12.1 What belongs to a profile

| Data | Where | Removed with the profile? |
|---|---|---|
| Favourite channels, favourite events, recent channels, last channel, locked channels, allowed groups | DataStore keys `…:<profileId>` | Yes |
| Resume positions and watched state | `playback_progress` | Yes (`deleteProgressForProfile`) |
| Trakt account, pending scrobbles, cached rows | `trakt_accounts` | Yes (disconnect) |
| Trakt state | `trakt_state` | Yes (disconnect) |
| Discover addons, library, progress, catalogue order and visibility | Discover databases, `sohva_addon_ui` | **No code removes them** in beta 23 (open question §19) |

Household (shared): sources and credentials, channel edits, custom lists, organisation rules and
identities, reminders, sport decisions and cache, metadata, every setting in §10.1 without
`[:id]`, the parental PIN.

### 12.2 Encrypted backup (`.smbak`)

Container: magic `0x534D424B`, version 1, PBKDF2WithHmacSHA256 210,000 iterations (100,000–
1,000,000 accepted on read), 16-byte salt, 12-byte IV, AES-256-GCM with 128-bit tag, AAD
`streammate-backup-v1`, passphrase ≥ 8 chars, at most 10 MiB encrypted / 8 MiB plain. Payload JSON
`formatVersion` 2 (1 accepted):

- **In it:** `sources` (the encoded source list, credentials included), `parentalPin`,
  `preferences` (the §10.1 settings except `auto_frame_rate`, the per-profile sets of the active
  profile, profiles and active profile), `profileData` per profile (favourites, recents, last
  channel, locked channels, allowed groups), `channelPreferences` (with logo bytes for phone logos
  as `customLogoData`), `channelLists`, `channelListMembers`, `organization` (all rules, and only
  the complete alias family of each film a MOVIES rule names — the full alias table would exhaust
  the heap as JSON).
- **Not in it:** resume positions and watched state, reminders, sport decisions, TMDB token and
  flags, API-Sports key, Trakt, every Discover store, metadata matches and manual fixes, the
  interface language, the artwork cache limit, `auto_frame_rate`, imported catalogues and guides.

Details: [specs/71](../specs/71-backup-restore.md).

## 13. Lifetimes and cleanup

| Data | Lifetime | Cleanup today |
|---|---|---|
| Playlist, EPG, catalogue snapshots | Until the next activation of the same source and kind | Activation deletes every other snapshot of that source and kind (including another import's staging — the race of [Lessons](08-lessons-learned.md) 2.3) |
| Programmes | now − 12 h … now + 8 days at import | Only at the next EPG import |
| Xtream episodes | Until the series is reopened or the catalogue activates | Replaced per series; orphans deleted on activation |
| A source's data | Until the source is deleted | `clearSource` deletes decisions for its channels, channel edits, list members, channels, XMLTV channels, programmes, progress (all profiles), episodes, movies, series, import and refresh state, source state. Organisation rules, metadata overrides, genres and reminders are **not** removed |
| `metadata_cache` | TMDB 30 d, TVmaze 24 h, negative 7 d; pinned rows forever | Expired unpinned rows deleted at the start of each enrichment lookup |
| Overrides, genres | Forever | Never swept |
| Metadata queue | Until its title leaves the active catalogues | Stamp-and-sweep on synchronisation |
| `sports_api_cache` | TTL + 24 h stale | Deleted on each sports fetch |
| Reminders | Until 30 min after start | Deleted on reschedule (start, boot, fire) |
| `trakt_state` | Replaced per sync | Wholesale per profile |
| Discover progress / library | 200 / 1,000 per profile | Pruned on write / refused when full |
| Recent channels | 20 per profile | Trimmed on write |
| Diagnostics log | 600 lines, memory only | Ring buffer |

## 14. Hot queries and their indexes (beta 23)

| Screen / job | Query shape | Index used | Known cost |
|---|---|---|---|
| Guide roster | `GUIDE_CHANNEL_PAGE_SQL`: `iptv_source_state CROSS JOIN import_state CROSS JOIN iptv_channels LEFT JOIN channel_preferences`, `channelId > :after ORDER BY channelId LIMIT 2000`, source predicate | PK (`sourceId`, `snapshotId`, `channelId`) | Keyset pages; the whole source (56,000 rows) is then held in memory (string-pooled) and sorted in Kotlin by `GuideRosterRow.DISPLAY_ORDER` (priority, source name, viewer position, playlist order, name, id — code-point order to match SQLite `BINARY`); kept 10 min. Before paging: one sorted query, 20 CursorWindow refills, 57 s ([Lessons](08-lessons-learned.md) 1.2) |
| Guide rows on screen | `observeGuideTimelineForChannels`: `organization_visible_channels` + `LEFT JOIN tv_programmes … startEpochMillis < :to − offset AND stopEpochMillis > :from − offset WHERE channelId IN (:ids)`, ordered by display order then start | `iptv_channels(channelId)`, programme index (`sourceId`, `xmltvChannelId`, `startEpochMillis`, `stopEpochMillis`) | Observed Flow; the view's 16 look-ups run only for the selected ids; `stop > from` is not seekable, so the scan starts at the channel's first programme |
| Guide rail | `observeGuideRail`: `GROUP BY sourceId, groupTitle, groupKey` over active channels | Snapshot index | One pass over every active channel per emission |
| Favourites / recents / custom lists | `GUIDE_CHANNELS_FOR_IDS_SQL`: `channelId IN (:ids)` with the inlined predicate, sorted | `iptv_channels(channelId)` | Bounded by the id list |
| Find programme | `GUIDE_PROGRAMME_MATCHES_SQL`: `EXISTS (… p.title LIKE :pattern ESCAPE '\')` per channel of the group | Programme index for the channel, then title scan | Proportional to the group's programmes |
| Search (live) | `GUIDE_SEARCH_SQL`: channel names `LIKE '%q%'` over the channel view, `UNION ALL` programme title/subtitle `LIKE '%q%'`, `ORDER BY resultType, title LIMIT 200` | None for `LIKE '%…%'` | Scans every active channel and programme; queries 2–80 chars |
| Search (VOD) | `searchMovies`/`searchSeries`/`searchEpisodes` `LIKE`, `ORDER BY name LIMIT :limit` | None | Full scans of the visible views |
| Home Continue watching | `CONTINUE_WATCHING_SQL`: from `playback_progress` (profile), `CROSS JOIN` source, import state and the visible view by full key; `GROUP BY COALESCE(workKey, contentKey) … LIMIT 20` | PK look-ups | < 1 ms when the join order is pinned; 5.5 s on 40,000 titles when the planner started from the view ([Lessons](08-lessons-learned.md) 2.1). Plan tests: `CatalogueHomeQueryPlanTest` |
| Trakt Home rows and overlays | `TRAKT_CONTINUE_WATCHING_SQL` etc.: from `trakt_state` (profile) `CROSS JOIN catalogue_metadata_overrides ON externalId`, then the visible view by key | `trakt_state(profileId, tmdb)`, overrides(`externalId`) | Index added in 28→29 for exactly this |
| Movie/series walls | `observeMovieCards[ByCategory|InGenre|InCustomGroup|Matching]`: card columns + `GROUP_CONCAT` genre sub-query per row, over the visible view, `ORDER BY name`, **no LIMIT** | `categoryKey` or `catalogue_genres(genre)` for the filter | "All groups" returns every visible film (200,000 rows, a sorter, many CursorWindows); rules are then applied again in Kotlin (`organize`) |
| Genre rail counts | `CROSS JOIN catalogue_genres` + counts | `catalogue_genres(genre)` | `CROSS JOIN` keeps the genre table first |
| Channel manager | `observeEditableChannels`: every channel of every source, sorted | Snapshot index | Whole-catalogue sorted read (the one still unpaged; [Lessons](08-lessons-learned.md) 1.2) |
| Metadata queue | `WHERE state IN (pending, retry) AND nextAttemptAtEpochMillis <= now … LIMIT` | (`state`, `nextAttemptAtEpochMillis`, `contentKey`) | Bounded pages of ≤ 200 |
| Metadata queue seeding | `movieMetadataCandidates`/`seriesMetadataCandidates`: `ORDER BY sourceId, snapshotId, id LIMIT :limit OFFSET :offset` | PK | OFFSET re-walks every earlier row: quadratic over 200,000 titles |
| Film identity pass | Pages of 2,000 in key order on its own background thread | PK | Regex normalisation per title |
| Sports matching | `SPORTS_CHANNEL_CANDIDATES_PAGE_SQL` (`c.rowid > :after ORDER BY rowid LIMIT 256`), then `SPORTS_PROGRAMME_CANDIDATES_PAGE_SQL` for those rowids (`INDEXED BY` the programme index, keyset on (`p.rowid`, `c.rowid`), `LIMIT 64`) | rowid; programme index | Paged since preview 49 after an OutOfMemoryError; peak about 12 MB |
| Sports matching generation | `SPORTS_MATCH_GENERATION_QUERY`: `UNION ALL` of one string per source state, playlist/EPG import state, **every `channel_preferences` row**, every LIVE rule and every team alias, `ORDER BY generation`; fingerprinted; also observed as a Flow | none (sorts strings) | Grows with channel edits (up to 100,000 rows, sorted on every check and every emission) |

Every hot query that touches a view pins its join order with `CROSS JOIN`; JVM plan tests
(sqlite-jdbc 3.41.2.2 on the exported schema): `CatalogueHomeQueryPlanTest`,
`GuideChannelsQueryPlanTest`, `OrganizationViewsTest`.

---

## 15. Clean target schema

### 15.1 Principles

1. **SQL floor = the platform SQLite of minSdk 23 (3.8.10).** Not available there: row-value
   comparisons `(a, b) > (?, ?)` (3.15), the `UPSERT` clause (3.24), window functions (3.25),
   generated columns (3.31), `PRAGMA optimize` (3.18), FTS5 (not guaranteed). Available: partial
   indexes (3.8.0), FTS4, `WITHOUT ROWID` (3.8.2), `INDEXED BY`, `CROSS JOIN` ordering. Keyset
   predicates are written `sort > ? OR (sort = ? AND id > ?)`. Raising minSdk to 26 (3.18) or
   bundling SQLite (the AndroidX bundled driver, a native library per ABI, to be measured against
   the 10 MB APK budget) would lift this; neither is assumed.
2. **Integer row ids for large tables** (`id INTEGER PRIMARY KEY`), with the §6 text keys kept as
   `UNIQUE` columns because preferences, backups, reminders and user tables reference them.
3. **No snapshot copies for channels and VOD.** Imports stage the parsed rows in an unindexed
   `TEMP` table, then apply a diff in chunked transactions: insert new keys, update rows whose
   `content_hash` changed, delete keys missing from a complete, validated parse. Rows keep their ids,
   unchanged rows are not rewritten (fewer writes, fewer invalidations — OwnTV study item 3), and
   the database never holds two copies of a 200,000-film catalogue. The EPG keeps snapshots
   (§15.5) because programmes have no stable identity.
4. **Precompute at write, not at read.** Sort keys (Unicode-aware, computed in Kotlin: SQLite's
   `LOWER` is ASCII-only), effective names/groups/numbers/logos, visibility and positions from the
   organisation rules, the primary genre, the film identity and "primary copy" flag are columns,
   refreshed by `bulk` jobs after imports, rule edits and metadata batches. No correlated rule
   look-ups at read time; no views.
5. **Keyset paging everywhere.** Each list query's index starts with its equality filters and ends
   with the sort key and `id`; screen pages ≤ 200 rows (one CursorWindow), bulk pages ≤ 2,000.
6. **Per-profile from the first table** ([02-roadmap.md](02-roadmap.md) principle 4): every table
   of viewer data has `profile_id`; per-profile sets leave DataStore.
7. **One main database** (IPTV, user data, metadata, Sohva Sport, Trakt — the last two join IPTV
   tables) and **one `discover.db`** for Discover (never joined).
8. **Pragmas:** `journal_mode=WAL` set explicitly, `synchronous=NORMAL`, default page cache;
   `wal_checkpoint(TRUNCATE)` after large imports; `foreign_keys` off (cascades are explicit and
   chunked so a source delete never holds one long write lock).

### 15.2 Sources and import state

| Table | Columns | Keys and indexes |
|---|---|---|
| `source` | `id` TEXT, `name`, `type` (`M3U`/`XTREAM`), `enabled`, `priority`, `connection_limit` (1–16), `import_scope` (`LIVE_TV`/`VOD`/`BOTH`), `epg_offset_minutes`, `created_at`, `updated_at` | PK `id`. The only copy of non-secret source fields; URLs and credentials live in the secret store keyed by `id` (no mirror table, no second source of truth) |
| `source_status` | `source_id`, `kind` (`playlist`/`epg`/`catalogue`), `status`, `last_attempt_at`, `last_success_at`, `last_failure_at`, `error_code`, `error_args` (JSON), `item_count`, `consecutive_failures`, `generation` (import counter), `epg_snapshot` (active EPG snapshot), `epg_max_duration_ms` | PK (`source_id`, `kind`). Errors as stable codes ([03](03-architecture.md) §4.11) |
| `content_group` | `id` INTEGER, `source_id`, `room` (`LIVE`/`MOVIES`/`SERIES`), `group_key`, `name`, `provider_order`, `item_count`, `shown` (resolved), `position` (resolved), `sort_mode` (resolved) | PK `id`; UNIQUE (`source_id`, `room`, `group_key`); index (`room`, `source_id`, `position`). Rails read this small table, never `GROUP BY` over channels |

### 15.3 Live

| Table | Columns | Keys and indexes |
|---|---|---|
| `channel` | `id` INTEGER, `key` TEXT (§6 global id), `source_id`, `group_id`, `name`, `sort_name`, `tvg_id`, `epg_id` (effective: manual mapping else `tvg_id`), `logo_url` (effective), `stream_url_enc`, `user_agent`, `referrer`, `playlist_order`, `provider_number`, `number` (effective), `display_rank` (resolved order within the source), `visible` (resolved), `catchup_type`, `catchup_source`, `catchup_days`, `catchup_tz`, `xtream_stream_id`, `content_hash`, `generation` | PK `id`; UNIQUE `key`; indexes (`group_id`, `display_rank`), (`source_id`, `visible`, `display_rank`), (`source_id`, `display_rank`), (`source_id`, `number`), (`source_id`, `epg_id`) |
| `channel_custom` | `channel_key` TEXT, `source_id`, `custom_name`, `custom_group_title`, `custom_group_key`, `hidden`, `position`, `manual_epg_id`, `custom_logo_url`, `custom_number`, `updated_at` | PK `channel_key`; index `source_id`. Household. Never touched by imports; its effects are copied into `channel`'s effective columns on import and on edit |
| `channel_list` | `id` TEXT, `name`, `sort_order`, `updated_at` | PK `id` |
| `channel_list_member` | `list_id`, `channel_key`, `sort_order` | PK (`list_id`, `channel_key`); indexes (`list_id`, `sort_order`), `channel_key` |

`display_rank` reproduces today's order (source priority and name are per source; then the
viewer's position, playlist order, name by code point, key) as a sparse integer (steps of 1,024) so a
move renumbers one gap, not the source. The guide therefore pages in display order straight from
an index, and no 56,000-row roster is sorted or held in memory.

### 15.4 Movies and series

| Table | Columns | Keys and indexes |
|---|---|---|
| `movie` | `id` INTEGER, `key` TEXT (`vod:movie:…`), `source_id`, `provider_id`, `group_id`, `name`, `sort_name`, `year`, `rating` (text as given), `rating_x10` INTEGER (parsed for sorting), `poster_url`, `stream_url_enc`, `plot`, `provider_order`, `genre` (primary), `work_key`, `primary_copy` (the copy that stands for its film on walls, from `preferred_catalogue_copy`), `visible`, `item_position` (manual), `content_hash`, `generation` | PK `id`; UNIQUE `key`; `work_key`; partial indexes `WHERE visible = 1 AND primary_copy = 1`: (`group_id`, `sort_name`, `id`), (`sort_name`, `id`), (`genre`, `sort_name`, `id`), (`group_id`, `provider_order`, `id`), (`group_id`, `year`, `id`), (`group_id`, `rating_x10`, `id`) |
| `series` | Same without `stream_url_enc`, plus `backdrop_url` | Same set of indexes |
| `episode` | `id` INTEGER, `key` TEXT (`vod:episode:…`), `series_id`, `source_id`, `provider_id`, `season`, `number`, `name`, `stream_url_enc`, `plot`, `duration_s`, `thumbnail_url` | PK `id`; UNIQUE `key`; (`series_id`, `season`, `number`) |

Titles descending use the title index backwards; `MANUAL` order reads positioned items first by
(`group_id`, `item_position`) — a small set — then the provider order.

### 15.5 Programme guide

| Table | Columns | Keys and indexes |
|---|---|---|
| `epg_channel` | `source_id`, `snapshot`, `epg_id`, `display_name`, `icon_url` | PK (`source_id`, `snapshot`, `epg_id`) — kept whole for manual mapping, as today |
| `programme` | `id` INTEGER, `source_id`, `snapshot` INTEGER, `epg_id`, `start_at`, `stop_at`, `title`, `subtitle`, `description`, `categories`, `programme_key` (§6 16-hex id, for reminders) | PK `id`; index (`source_id`, `snapshot`, `epg_id`, `start_at`) |

- **Window query:** `epg_id IN (visible rows) AND start_at < :to AND start_at >= :from − epg_max_duration_ms AND stop_at > :from` — a bounded, seekable range per channel.
- **Owned channels only:** a programme is stored only when its `epg_id` belongs to a channel of the
  source (by `tvg_id` or manual mapping); if no channel of the source has any id, everything is
  stored rather than an empty guide (today's rule; OwnTV item 9). Keep-sets larger than SQLite's
  999-variable limit go through a `TEMP` table.
- **Past only where catch-up can play it** (OwnTV item 10): channels without catch-up keep
  programmes ending after the import time; catch-up channels keep the past up to their archive
  depth, capped as §19 decides (today: 12 h for every channel). Future: 8 days, as today.
- **Swap:** stage the parse unindexed, insert into `programme` in chunks sorted by
  (`epg_id`, `start_at`) (index-friendly appends), flip `source_status.epg_snapshot` in one small
  transaction, then delete the previous snapshot in chunks on `bulk`. An import deletes only its own
  abandoned staging or the snapshot it replaced — never another import's (lesson 2.3).

### 15.6 Search

FTS4 tables maintained by the import pipeline in the same chunks as their content (no triggers):
`channel_fts(name)`, `movie_fts(name)`, `series_fts(name)`, `episode_fts(name)`,
`programme_fts(title, subtitle)`, each with `docid` = the content row id and the `unicode61`
tokenizer (availability on the API 23 emulator is verified in M1). Queries use token-prefix matches
(`MATCH 'term*'`), `LIMIT 50` per kind, then look up rows by id. This replaces every
`LIKE '%q%'` scan. Token-prefix matching does not find a word's inside ("ball" does not find
"Football"); whether that is acceptable is an owner/spec decision (§19).

### 15.7 Metadata and organisation

| Table | Columns | Keys and indexes |
|---|---|---|
| `metadata_match` | `content_key`, `media_type`, `provider`, `external_id`, `status` (`matched`/`no_match`), `confidence`, `pinned`, `genre`, `genres_version`, `replacement_title`, `replacement_poster_url`, `replace_provider_poster`, `updated_at` | PK `content_key`; index `external_id`. Replaces overrides + genres; survives re-imports because it is keyed by content key; the import copies `genre` and the effective title/poster into `movie`/`series` |
| `metadata_cache` | `lookup_key`, `provider`, `status`, `external_id`, `payload` (JSON: titles, overview, images, cast, similar), `details_loaded`, `pinned`, `cached_at`, `expires_at` | PK (`lookup_key`, `provider`); index `expires_at` |
| `metadata_queue` | `content_key`, `media_type`, `title`, `year`, `state`, `attempts`, `next_attempt_at`, `updated_at` | PK `content_key`; (`state`, `next_attempt_at`, `content_key`) |
| `organization_rule` | `room`, `source_id`, `group_key`, `item_key`, `shown` (null = no opinion), `sort_mode`, `position` | PK (`room`, `source_id`, `group_key`, `item_key`). The source of truth and the backup format; resolved into `content_group` and the items' `visible`/`item_position` by a `bulk` pass using the precedence of §5 |

Film identity is the `work_key` column (no alias table); rules about a film use `work:<key>`.

### 15.8 Viewer data (all per profile)

| Table | Columns | Keys and indexes |
|---|---|---|
| `profile` | `id` (`default`, `p<millis>`), `name` (≤ 24), `color_index` (0–5), `position`, `last_channel_key`, `created_at` | PK `id`; at most 6 |
| `favourite_channel` | `profile_id`, `channel_key`, `added_at` | PK (`profile_id`, `channel_key`); (`profile_id`, `added_at`) |
| `recent_channel` | `profile_id`, `channel_key`, `watched_at` | PK (`profile_id`, `channel_key`); (`profile_id`, `watched_at`); trimmed to 20 on write |
| `locked_channel` | `profile_id`, `channel_key` | PK |
| `favourite_event` | `profile_id`, `event_id` | PK |
| `profile_allowed_group` | `profile_id`, `room`, `group_key` | PK |
| `watch_progress` | `profile_id`, `content_key`, `source_id`, `content_type`, `item_id`, `work_key`, `position_ms`, `duration_ms`, `completed`, `updated_at` | PK (`profile_id`, `content_key`); (`profile_id`, `completed`, `updated_at`); (`profile_id`, `work_key`) |
| `reminder` | `id` (§6), `kind`, `event_id`, `channel_key`, `title`, `subtitle`, `start_at`, `created_at` | PK `id`; `start_at`. Household, as today |
| `trakt_state` | as today, snake_case | as today |
| `sports_cache`, `event_channel_decision`, `team_alias` | as today, with `channel_key` | as today |

Removing a profile deletes its rows in every table here and in `discover.db`.

### 15.9 `discover.db`

One file with `addon_installation`, `addon_library`, `addon_progress` (shapes and limits of §8)
and `addon_catalog_pref` (`profile_id`, `catalog_key`, `position`, `hidden`) replacing the JSON
strings in `sohva_addon_ui`. One cipher (the main envelope) instead of a second Keystore key, if the
owner accepts re-encrypting the Discover payloads (§17).

### 15.10 Preferences, secrets and files (target)

- DataStore `preferences` holds household scalars only (the §10.1 keys without `[:id]`, minus the
  legacy ones); consumers read single keys through narrow flows.
- One secret store (SharedPreferences, envelope cipher, one Keystore key) for source URLs and
  credentials, the parental PIN, the TMDB token, the API-Sports key, Trakt tokens and pending
  scrobbles.
- Synchronous small files stay for the locale (below Android 13) and the artwork cache limit.
- Files as in §11.

### 15.11 Screens and their indexes

| Screen | Query | Index |
|---|---|---|
| Guide rail | `content_group WHERE source_id = ? AND room = 'LIVE' AND shown = 1 ORDER BY position, provider_order` | (`room`, `source_id`, `position`) |
| Guide rows, one group | `channel WHERE group_id = ? AND visible = 1 AND display_rank > ? ORDER BY display_rank LIMIT 200` | (`group_id`, `display_rank`) |
| Guide rows, all channels of a source | `channel WHERE source_id = ? AND visible = 1 AND display_rank > ? …` | (`source_id`, `visible`, `display_rank`) |
| Favourites, recents, custom lists | page the membership table by its order, then channels by `key` | membership indexes, UNIQUE `key` |
| Guide time window | §15.5 window query for the ≤ 30 channels on screen | (`source_id`, `snapshot`, `epg_id`, `start_at`) |
| Channel up/down | `display_rank > :current … LIMIT n` / `< … DESC` (neighbour window, OwnTV item 14) | (`source_id`, `visible`, `display_rank`) |
| Number dialling | `channel WHERE source_id = ? AND number = ?` | (`source_id`, `number`) |
| Channel manager | pages of `channel WHERE source_id = ? ORDER BY display_rank` (hidden included) | (`source_id`, `display_rank`) |
| Movie/series wall, group, by title | `WHERE group_id = ? AND visible = 1 AND primary_copy = 1 AND (sort_name > ? OR (sort_name = ? AND id > ?)) ORDER BY sort_name, id LIMIT 120` | partial (`group_id`, `sort_name`, `id`) |
| Wall, all groups | same without `group_id` | partial (`sort_name`, `id`) |
| Wall, genre | same with `genre = ?` | partial (`genre`, `sort_name`, `id`) |
| Wall, other sorts | provider order, year, rating | the matching partial index |
| Series page | `episode WHERE series_id = ? ORDER BY season, number` | (`series_id`, `season`, `number`) |
| Home Continue watching | `watch_progress WHERE profile_id = ? AND completed = 0 ORDER BY updated_at DESC LIMIT 40`, then items by key, collapse by `work_key`, keep 20 | (`profile_id`, `completed`, `updated_at`) |
| Search | FTS `MATCH … LIMIT 50` per kind | FTS4 |
| Sports matching | channels in `id` pages of 256; programmes for those channels in the match window | PK; programme index |
| Metadata worker | queue pages | (`state`, `next_attempt_at`, `content_key`) |
| Identity and rule passes | `id > ? ORDER BY id LIMIT 2000` | PK |

Every row of this table gets a JVM query-plan test on the exported schema that asserts no
`SCAN TABLE` of a large table and no `USE TEMP B-TREE FOR ORDER BY`.

### 15.12 Bulk imports, indexes and statistics

- **Fresh table** (first import on a new install, or the only source): insert with the secondary
  indexes dropped, then create them once, sorted, on `bulk`.
- **Live table with other sources' data**: never drop indexes (other screens need them); stage
  unindexed, apply the diff in chunks sorted by the most expensive index key.
- **ANALYZE** the touched tables after an import, on `bulk`, never at launch. Until statistics
  exist, hot queries do not depend on them (pinned join order with `CROSS JOIN`, plan tests).
- **Change detection:** the sports matcher's input generation becomes one counter per source
  (`source_status.generation`) plus a counter bumped by channel-edit and rule writes — no string
  union over every channel edit.

---

## 16. Lightweight by design

- **Heap:** no table is read whole; screen pages ≤ 200 rows, bulk pages ≤ 2,000; the guide no
  longer holds a sorted 56,000-row roster (§15.3); walls never load a whole group (§15.11).
- **Disk and write volume:** diff imports rewrite only changed rows; one copy of the catalogue
  instead of two during an import; the EPG stores owned channels only and past only for catch-up.
- **CPU at read:** no correlated rule look-ups, no `GROUP BY` rails, no `LIKE '%…%'` scans, no
  Kotlin re-sorting of query results; formatting and normalisation happen once at write.
- **Invalidation:** unchanged rows are not rewritten, so observers of visible tables are not woken
  by imports that changed nothing; bulk writes commit per page.
- **Start-up:** opening the database runs no data migration; ANALYZE and backfills are `bulk` jobs
  ([03](03-architecture.md) §4.9).
- **Current slow or out-of-memory places and their rule:** whole-catalogue metadata and identity
  jobs (OOM, [Lessons](08-lessons-learned.md) 1.1 → key-order pages); the guide roster (57 s,
  1.2 → `display_rank` keyset); the sports matcher (OOM, 1.4 → id pages); backup export (1.5 →
  streamed); Continue watching planned from the view (5.5 s, 2.1 → no views, plan tests); the
  unpaged channel manager and "all groups" wall (→ §15.11); `OFFSET` seeding (→ keyset).

## 17. OWNER DECISION: existing users' data

Testers run schemas 23–29 (§7) and update in place through the app's updater. The rebuild
installs over them with the same application ID and signing key, so it sees the old data
directory, both Keystore keys and both wrapped data keys. Four options:

### Option A — keep the schema compatible and migrate in place

The rebuild keeps `streammate.db` and adds Room migrations 29→30 onward (and re-implements 23→29
for older betas, since old code is not reused).

- **Cost:** either the new code keeps the v29 table shapes (text composite keys, snapshot copies,
  the 16-look-up views — the structure this rebuild exists to leave), or migration 29→30 rewrites
  every large table (200,000 films, 165,000 programmes) inside Room's open, on the launch path, for
  minutes on the low-end box. Six historical versions to test.
- **Benefit:** no separate importer; every row, including caches, survives.

### Option B — one-time import from the old install on first start (recommended)

The rebuild creates its own database (`sohva.db`) and, when `streammate.db` or the old preference
files exist and no "imported" marker does, runs an importer that reads the old stores directly
(plain `SQLiteDatabase`, read-only; the Keystore keys and envelopes as in §9), writes the new
tables, and deletes the old files after success.

| Old data | Action |
|---|---|
| Sources, parental PIN, TMDB token and flags, API-Sports key (`streammate_secure_sources`, `v1`/`v2` values) | Decrypt and store in the new secret store |
| DataStore settings and per-profile keys, profiles | Map to the new preferences and profile tables |
| Locale (below Android 13), artwork cache limit | Keep the files as they are |
| `channel_preferences`, `channel_lists`, `channel_list_members` (with phone logos in `files/channel-logos`) | Copy (keys by §6) |
| `organization_rules`, film aliases referenced by rules | Copy; a rule naming a film identity keeps its alias family (the copy keys) and is re-keyed to the new `work_key` of those copies after the first catalogue import |
| `playback_progress` | Copy to `watch_progress` |
| `reminders`, `event_channel_decisions`, `team_aliases` | Copy |
| `catalogue_metadata_overrides`, `catalogue_genres`, pinned `metadata_cache` rows | Copy into `metadata_match` (re-deriving 200,000 matches at 225 ms per lookup is at least 12.5 hours of background enrichment, plus the TMDB traffic) |
| `trakt_accounts`, `trakt_state` | Copy the account (tokens re-encrypted); `trakt_state` is re-synced |
| Discover databases, `sohva_addon_ui` | Decrypt with the Discover key, copy into `discover.db` |
| Channels, programmes, movies, series, episodes, unpinned metadata cache, sports cache | **Not copied**: a viewer-started sync of every source runs after the first frame, through the normal import pipeline |

- **Cost:** an importer of a few hundred lines kept for a few releases, fixtures of real old
  databases for schemas 23, 24, 25, 26 and 29 (read by column presence, `PRAGMA table_info`, not by
  version number), and a first start where user data is imported before routing (a one-time step
  that needs a short "updating" state if it takes longer than about a second — new strings, owner
  to approve) and the guide and libraries refill from the providers over the following minutes.
- **Benefit:** the new schema is clean from day one; nothing heavy runs inside Room's open;
  credentials, favourites, positions, reminders, Trakt and Discover survive; the old catalogue files
  (the largest part of the data) are freed at once.
- **Failure path:** if an old store cannot be read (corrupt file, key missing), import what can be
  read, keep a diagnostics line, show one plain sentence in Settings › Sources, and continue as a
  fresh start for the rest. The old files are deleted only after a successful import.

### Option C — import only through `.smbak`

Testers export a backup in beta 23, update, and restore it in the rebuild. The rebuild ignores and
deletes the old files.

- **Cost:** a manual step every tester must do *before* updating (the updater installs in place, so
  a tester who forgets loses everything); the backup lacks resume positions, reminders, sport
  decisions, TMDB and API-Sports keys, Trakt, all Discover data, metadata fixes, the language and
  artwork settings and `auto_frame_rate` (§12.2).
- **Benefit:** no importer beyond restore, which M7 builds anyway ("a beta 23 `.smbak` restores
  into the rebuild", [02-roadmap.md](02-roadmap.md)).

### Option D — fresh start

Delete the old files at first start.

- **Cost:** every tester re-enters sources and keys and loses everything; contradicts product
  principle 7 ([00-product-overview.md](00-product-overview.md) §3).
- **Benefit:** no compatibility code at all.

### Recommendation

**Option B**, with Option C also supported (restore of beta 23 backups is in scope for M7 anyway)
and Option D as the automatic fallback for whatever cannot be read. Prove it in M1 with the spike
the roadmap already plans (read a real old installation's database and Keystore-encrypted sources
on the emulator), and test it in M11 as an upgrade over real beta 23 installs.

Whatever the owner chooses except D, the identity rules of §6 must be reproduced exactly, because
backups, favourites, recents, locks, reminders, progress and organisation rules all refer to them.

## 18. Lessons from the current app

| Lesson | Source | Rule in the target |
|---|---|---|
| Compiling after an entity change but before the version bump overwrote the shipped schema JSON | [Lessons](08-lessons-learned.md) 2.2; memory note "Room schema export trap" | Change entity, bump version, add migration and test in one edit |
| Views with 16 correlated look-ups per row; join order decided 1 ms vs 5.5 s | 2.1 | No views; resolved columns; pinned joins; plan tests |
| Activation deleted another import's staging rows; a playlist vanished | 2.3 | One import per source and kind; deletes scoped to the import's own snapshot |
| Sorted wide reads re-executed per CursorWindow (57 s) | 1.2 | `display_rank` keyset pages ≤ 200 rows |
| SQLite `LOWER` is ASCII-only; Finnish group keys needed Kotlin backfills | `MIGRATION_22_23` comment | Sort and group keys computed in Kotlin at write |
| Resume looked up (`sourceId`, `movieId`) but the PK had `snapshotId` between them — every launch was a scan until indexes were added (13→14) | `GuideEntities.kt` comments | Integer ids and `UNIQUE key`; plan tests for every look-up |
| `OFFSET` pages over 200,000 titles | `CatalogueDao.movieMetadataCandidates` | Keyset only |
| A second profile's data had to be retrofitted (24→25) | `MIGRATION_24_25` | `profile_id` in every viewer table from the start |
| A private trial build left schema 27 and progress DB 2–3 behind | `MIGRATION_27_28`, `AddonProgressDatabase` | Never ship a schema version outside the release line; test upgrades from every published schema |
| Encrypting each value through the Keystore made imports take minutes | `EnvelopeSecretCipher.kt` | Envelope encryption with a software data key |
| DataStore sets for favourites rewrite the whole file on each toggle | §10.1 | Per-profile tables |

## 19. Open questions for the owner and the specs

1. **Existing users' data (§17):** B (recommended), A, C or D.
2. **EPG past for catch-up channels:** keep today's 12 h for every catch-up channel (parity), or
   keep each channel's archive depth (`catchupDays`, up to the provider's limit) so catch-up can
   reach further back? The guide can already page back one day (`GuideTimeWindow`).
3. **EPG past for channels without catch-up:** keep nothing before the import time (target) —
   the guide then shows no past blocks on those channels before the next import accumulates them.
   Confirm with [specs/20](../specs/20-live-tv-guide.md).
4. **Search semantics:** FTS token-prefix matching (fast) versus today's substring `LIKE '%q%'`
   (finds word insides, scans everything). Confirm with [specs/03](../specs/03-search.md).
5. **Discover data on profile removal:** beta 23 keeps it; the target deletes it. Confirm.
6. **Source removal:** beta 23 keeps the source's organisation rules, metadata matches and
   reminders; the target deletes rules and reminders of the source and keeps metadata matches for
   30 days. Confirm.
7. **`team_aliases`:** no writer exists in beta 23. Keep the table (and add the editor the matcher
   was built for), or drop it.
8. **One Keystore key:** re-encrypt Discover payloads under the main envelope during the import
   (one key to keep valid), or keep the second key.
9. **SQLite floor:** stay on the platform SQLite of API 23 (default), raise minSdk to 26, or bundle
   SQLite (measure the APK cost first).
10. **Reminders per profile:** today they are household-wide. Keep (default) or scope to profiles.
11. **Backup gaps:** should the rebuild's `.smbak` add `auto_frame_rate`, the TMDB token and the
    API-Sports key (all missing today)? The format version would go to 3 while still reading 1–2.

## 20. Reference: current code map

- `core/src/main/java/com/streammate/tv/core/database/StreamMateDatabase.kt` — database, version 29, migrations 1→29.
- `core/src/main/java/com/streammate/tv/core/database/GuideEntities.kt` — most entities and query row types, `GuideRosterRow.DISPLAY_ORDER`.
- `core/src/main/java/com/streammate/tv/core/database/ReminderEntity.kt`, `TraktState.kt` — reminders and Trakt state.
- `core/src/main/java/com/streammate/tv/core/database/OrganizationViews.kt`, `OrganizationDao.kt` — views, rule predicates, rules and aliases.
- `core/src/main/java/com/streammate/tv/core/database/GuideDao.kt`, `CatalogueDao.kt`, `MetadataDao.kt`, `SportsCacheDao.kt` — DAOs.
- `core/src/main/java/com/streammate/tv/core/database/GuideChannelQueries.kt`, `CatalogueHomeQueries.kt`, `SportsMatchQueries.kt`, `GuideSearchQueries.kt`, `TraktHomeQueries.kt`, `TraktProgressQueries.kt` — hot SQL.
- `core/schemas/…StreamMateDatabase/1.json`–`29.json`, `addons/schemas/…` — exported schemas.
- `core/src/main/java/com/streammate/tv/app/AppPreferencesRepository.kt`, `Profiles.kt`, `AppLocale.kt`, `ArtworkCacheSettings.kt` — preferences.
- `core/src/main/java/com/streammate/tv/core/security/*` — ciphers, Keystore key, secret store, source codec, backup cipher.
- `addons/src/main/java/com/sohva/tv/addons/storage/*` — Discover databases and encrypted store.
- `app/src/main/java/com/streammate/tv/addons/AddonHost.kt` — Discover key, UI preferences, response cache.
- `app/src/main/java/com/streammate/tv/trakt/TraktAccountStore.kt` — Trakt accounts.
- `app/src/main/java/com/streammate/tv/app/StreamMateBackupManager.kt`, `OrganizationBackupCodec.kt`, `ChannelLogoStore.kt` — backup and logo files.
- `iptv/src/main/java/com/streammate/tv/iptv/m3u/M3uParser.kt`, `…/repository/GuideStore.kt`, `…/repository/M3uCatalogueImportService.kt`, `…/xmltv/XmlTvParser.kt` — identity rules.
- `iptv/src/main/java/com/streammate/tv/iptv/repository/GuideImportService.kt` — EPG filtering and retention.
- `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` — Android backup exclusions.
