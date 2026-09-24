# Architecture

> The current structure of Sohva TV `0.1.0-beta.23` (build 57, public commit `efab52a`) in brief,
> what is messy about it, and the clean, lightweight structure the rebuild uses instead. The
> rebuild writes all of this new (see the code reuse policy in [AGENTS.md](../../../AGENTS.md) §3);
> the old code is described only so its mistakes are recognisable.

Related: [Data model](04-data-model.md) (schema, preferences, files, migration choice),
[Performance](07-performance.md) (budgets), [Tech stack](05-tech-stack-and-build.md) (libraries,
Gradle, R8), [App shell and navigation](../specs/01-app-shell-navigation.md) (the behaviour the
shell must reproduce), [Lessons learned](08-lessons-learned.md).

## 1. What this file decides

1. The module layout: a UI-free core (model, data, network and parsers, sync, playback engine),
   one design-system module, feature UI modules, and a thin app module. Optional features (Sohva
   Sport, Discover, Trakt) are self-contained modules behind feature flags.
2. The dependency rules between modules and how they are enforced.
3. Dependency injection: manual constructor injection through lazy graphs, no reflection.
4. Navigation: a typed, saveable back stack with per-entry state and explicit focus restoration.
5. Screen state: one immutable UI state per screen, produced off the main thread.
6. Threading: named dispatchers; the main thread does UI only; bulk work runs at background
   priority, in bounded pages, and pauses while video plays.
7. Background work: WorkManager policies, alarms, services and receivers.
8. Start-up: what runs before the first frame (almost nothing) and what is deferred.
9. Errors, empty and loading states: one model with stable error codes.
10. Testing seams, package naming, and where every current package goes.

Everything here serves the lightweight requirement in
[_spec-conventions.md](_spec-conventions.md): a low-end box (Amlogic S905Y4, 4× Cortex-A35,
Mali-G31 MP2, 2 GB) must run the app well at owner scale (56,000 channels, 165,000 programmes,
200,000 films).

---

## 2. The current app in brief (beta 23)

### 2.1 Modules and dependencies

Six Gradle modules, 258 Kotlin files and about 55,200 lines in `src/main`:

| Module | Namespace | Kotlin files / lines | Depends on | What it holds |
|---|---|---|---|---|
| `app` | `com.streammate.tv` | 71 / 12,747 | core, iptv, sportmate, addons, trakt | Application, activity, the whole navigation shell, the DI container, workers, playback service, reminders, backup, updater, phone setup server, **the Discover UI and host** (`com.streammate.tv.addons`), **the Trakt service and UI** (`com.streammate.tv.trakt`), Home, Search, Legal |
| `core` | `com.streammate.tv.core` | 50 / 9,711 | – | Room database (all 24 tables and 5 views, DAOs, SQL), DataStore preferences, secret store and ciphers, error model, diagnostics log, domain models **including sport models**, **and Compose UI**: theme, palettes, `TvUiComponents` (929 lines), focus helpers |
| `iptv` | `com.streammate.tv.iptv` | 86 / 25,003 | core | M3U, XMLTV and Xtream parsers and clients, import services, repositories, metadata (TMDB, TVmaze), catch-up, the guide, **the whole player UI**, movies and series, **the whole Settings tree** (including About and updates, profiles, parental PIN, time zone, remote mapping, diagnostics) |
| `sportmate` | `com.streammate.tv.sportmate` | 13 / 4,630 | core | API-Sports repository, stream matcher, the Today screen and its one ViewModel |
| `addons` | `com.sohva.tv.addons` | 32 / 2,499 | – (no Android UI) | Stremio protocol client, manifest parsing, encrypted stores, three Room databases |
| `trakt` | `com.sohva.tv.trakt` | 6 / 654 | – (no Android UI) | Trakt HTTP clients and device authorisation |

```
            app ──────────────┬──────────┬──────────┬─────────┐
             │                │          │          │         │
             ▼                ▼          ▼          ▼         ▼
           iptv ──► core ◄── sportmate  addons    trakt
```

`core` carries Compose and `androidx.tv:tv-material` as dependencies, so nothing in the app is
UI-free. The build uses Kotlin 2.3.21, AGP 8.13.2, Compose BOM 2026.06.00, tv-material 1.1.0,
Room 2.8.4, WorkManager 2.11.2, Media3 1.11.0, OkHttp 5.3.0, Coil 3.4.0, DataStore 1.2.1.

### 2.2 Dependency injection

Manual. `StreamMateApplication.onCreate` builds one `StreamMateContainer`
(`app/.../app/StreamMateContainer.kt`, 248 lines) on the main thread. The container constructs
about 30 objects eagerly: the Room builder, the Keystore key provider and envelope cipher, the
secret store, the DataStore repository, the demo content provider (a PackageManager read and a
reflective `Class.forName`), one OkHttp client (connect 20 s, read 90 s), repositories, four
import services, the metadata repository, the playback repository, the Trakt service, the backup
manager, the update checker, the phone setup server and a derived HTTP/1.1 client for playback.
Only the sports repository, the sports matcher and Home's resume store are `lazy`. A private
`organizationScope` (`SupervisorJob + Dispatchers.IO`) runs a one-time initialisation
(`awaitReady()`): demo seeding, migrating legacy hidden categories into organisation rules (the
first database access, so the database opens and migrates here), loading Trakt accounts, then a
permanent film-identity collector.

Everything else reaches the container directly: workers and the playback service call
`(applicationContext as StreamMateApplication).container`; the Discover host is a process
singleton `AddonHost.get(context, container)` that opens three more Room databases and a second
Keystore key on first use; several screens receive the whole `container`
(`DiscoverTitleScreen(container, …)`, `TraktTitleScreen(container, …)`, `AddonFeature.Screen(container, …)`).

The application also builds Coil's singleton image loader (disk cache `cache/catalogue_artwork`,
100/250/500 MB from a small synchronous preferences file; memory cache 8 % of the memory class; 2
parallel decoders; 140 ms crossfade), touches `container.homeResume` so the Continue watching read
starts (which also opens the Discover progress database), registers the foreground tracker
(initialising WorkManager on the main thread just to cancel a legacy job name), and re-arms the
reminder alarm on an ad-hoc IO scope.

### 2.3 Navigation

`StreamMateApp.kt` (1,118 lines; the root composable alone is 928 lines) owns a private sealed
`Destination` type with 19 entries (`Home`, `Discover`, `DiscoverTitle`, `TraktTitle`, `Today`,
`Guide`, `Settings`, `LegalInformation`, `ChannelEditor`, `LibraryManager`, `Search`,
`Catalogue`, `MovieDetails`, `SeriesDetails`, `ProfilePicker`, `ProfileGate`, `PinGate`,
`Player`, `VodPlayer`) and a back stack `remember { mutableStateOf(listOf(Home)) }`:

- Push unless equal to the top; Back pops while more than one entry remains; a `BackHandler` is
  enabled while the stack has more than one entry.
- Only the top destination is composed. State that must survive leaving a screen is held by hand
  in the root: the guide's focus channel, whether the guide reopens its options after the library
  manager and for which group, the previous channel for zap-back, two long-lived movie and series
  browse sessions, the sport screen's open match (`rememberSaveableStateHolder`, key `today`),
  the score ticker switch.
- The stack is **not saveable**: process death or a language change restarts at the start route.
- Some destinations carry whole domain records (`MovieDetails(VodMovie)`,
  `DiscoverTitle(AddonWatchProgress)`), not ids.
- Discover has its own internal navigation inside `AddonDiscoverScreen`.

The behaviour to reproduce is in [specs/01](../specs/01-app-shell-navigation.md).

### 2.4 State holders and data flow

There is no consistent pattern:

- One ViewModel in the whole app, `TodayViewModel` (sport), created at the app root because Home
  and Search read its events too.
- Hand-written stores: `CatalogueBrowserStore` (VOD walls, a good state machine with request
  identity), `HomeResumeStore` (Continue watching), the guide roster cache inside
  `GuideRepository` (kept 10 minutes).
- Most screens take repositories as parameters and keep their state in `remember`/`mutableStateOf`
  and `LaunchedEffect`s: `GuideScreen` has 29 `mutableStateOf` and 36 `LaunchedEffect`s in one
  896-line composable; `PlayerScreen.kt` has 32 and 20; `SettingsScreen.kt` has 38.
- Many consumers collect the whole `AppPreferences` object (about 45 fields). Every preference
  write (each zap records a recent channel) re-emits it everywhere, including the app root, which
  then recomposes and re-invokes the current screen.
- Room `Flow`s are collected straight into composables; several return whole catalogues sorted
  by name (every movie of a group, every channel for the channel manager).

### 2.5 Coroutines, Flow and threads

- `Dispatchers.IO` is named 65 times and `Dispatchers.Default` 61 times across the modules; six
  ad-hoc `CoroutineScope(SupervisorJob() + …)` instances exist (container, application, reminder
  receiver, Discover host, playback service, metadata request de-duplication).
- One dedicated background-priority thread exists: the film-identity pass
  (`OrganizationRepository.kt`, `THREAD_PRIORITY_BACKGROUND`, "SohvaFilmIdentity"). All other
  bulk work (imports, parsing, encryption of stream URLs, sports matching) shares the default
  pools at normal priority.
- Room uses its default executors (four `arch_disk_io` threads). Its blocking read loop ignores
  cancellation, so abandoned whole-source reads queued every other query
  ([Lessons](08-lessons-learned.md) 1.3).
- Bulk work yields to the viewer only through `StreamMateForegroundState` (activity started
  count): the metadata worker does not run while any activity is started; periodic refresh returns
  `Result.retry()` while the app is in front unless it is viewer-initiated or a first import.
  Nothing pauses because a video is playing.

### 2.6 Background work, services and receivers

| Component | Kind | What it does |
|---|---|---|
| `GuideRefreshWorker` | `CoroutineWorker`, three periodic unique works `streammate-playlist-refresh`, `streammate-epg-refresh`, `streammate-catalogue-refresh` (`ExistingPeriodicWorkPolicy.UPDATE`, network connected, linear backoff 15 min); one-off `sohva-sync-now-<sourceId or all>` (`ExistingWorkPolicy.KEEP`) | Playlist and EPG every 1/2/4/10/24 h (setting, default 24 h), catalogue every 24 h; defers while the app is in front unless immediate or first import; one catalogue retry after 20 s for immediate syncs |
| `CatalogueMetadataWorker` | one-time unique work `streammate-catalogue-metadata-enrichment-v2` (`KEEP` on start, `REPLACE` after a catalogue import, `APPEND_OR_REPLACE` to continue); legacy name cancelled at start | TMDB/TVmaze enrichment: batches of 60, 225 ms between lookups, stops after 4 min or 3 consecutive retries (then continues after 15 min), starts 30 s after the app leaves the foreground, cancelled when it returns |
| `ReminderReceiver` | manifest receiver (`BOOT_COMPLETED`, `com.streammate.tv.REMINDER_FIRE`) | Fires due reminders, re-arms `AlarmManager.setAlarmClock` for the next one (60 s lead), brings the app forward when the overlay grant exists |
| `StreamMatePlaybackService` | `MediaSessionService`, `foregroundServiceType=mediaPlayback` | Owns the IPTV/VOD ExoPlayer, resolves placeholder URIs to decrypted stream URLs with headers, applies buffer profiles, writes VOD progress, drives Trakt scrobbles |
| `AddonPlayback` | in-activity `ExoPlayer` | A **second** player engine used by Discover |
| PiP close receiver, update-install receiver | dynamic receivers | Close the corner window; follow the PackageInstaller session |
| `PhoneSetupServer` and `AddonPhoneSession` | two separate local HTTP servers on a `ServerSocket` | Phone entry of sources, logos and keys; phone entry of addon URLs |
| FileProvider `${applicationId}.updates` | provider | Hands downloaded APKs to the installer |

### 2.7 Demo build, Lab build and runtime policy

- The demo build (`com.streammate.tv.demo`) declares a manifest meta-data key
  `com.streammate.tv.DEMO_CONTENT_PROVIDER`; `DemoContentProvider.load` reads it through the
  PackageManager on every start and instantiates the class by reflection. The provider seeds the
  database and replaces the sports repository.
- `AppRuntimePolicy.forPackage(packageName)`: Lab (`com.streammate.tv.lab`) gets no automatic
  maintenance, no automatic sport refresh and no reminders; public updates only for
  `com.streammate.tv`; addons (Discover) for `com.streammate.tv`, `.debug` and `.lab`, not demo.
- Trakt client credentials come from an ignored local properties file into `BuildConfig`; without
  them the Accounts section says Trakt is not configured.

### 2.8 Error model and diagnostics

- `core/error/LocalizedException.kt`: an `IOException` carrying a string resource id and
  arguments, resolved in the UI (`userMessage(context)`). Library exceptions fall back to their
  redacted message, else `error_unknown` "Unknown error". `localizedTransportFailure` wraps
  transport errors as `error_transport_failed` "Could not complete the request" or
  `error_transport_failed_detail` "Could not complete the request: %1$s" with the redacted detail.
  `StoredFailureMessage` persists a failure as `resource:<id>\t<args>` and refuses ids whose
  entry name does not start with `error_` (ids are renumbered between builds).
- Other failure types live beside it: `AddonFailure`/`AddonException`, `TraktFailure`/
  `TraktException`, `AppUpdateFailure` (and a copy, `AppUpdateUiState.Failure`, in `iptv`),
  `SportsBackendException`, `XtreamException`, `GuideSourceException`, `GuideImportException`.
- `core/diagnostics/DiagnosticsLog.kt`: an in-memory ring of 600 redacted lines mirrored to
  logcat (tag `SohvaTV`). `DiagnosticsReport` writes the redacted diagnostics file to a location
  the tester picks ([specs/72](../specs/72-updates-about-diagnostics.md)).

---

## 3. What is messy, concretely

### 3.1 Giant files and giant functions

22 files exceed 600 lines. The worst are single composables:

| File | Lines | The problem |
|---|---|---|
| `iptv/.../feature/settings/SettingsScreen.kt` | 2,558 | One `SettingsScreen` composable of **2,357 lines** with 28 parameters (5 import services, 3 repositories, 20 callbacks and slots). Exceeded ART's 10,000 code-unit AOT limit and ran interpreted |
| `sportmate/.../feature/today/TodayScreen.kt` | 2,167 | 39 composables in one file; UI, formatting and localisation mapping mixed |
| `iptv/.../feature/player/PlayerScreen.kt` | 1,817 | `ActivePlayer` is **1,103 lines with 40 parameters**; exceeded the AOT limit; a 41-parameter sibling produced dex ART refused to verify ([Lessons](08-lessons-learned.md) 3.3–3.4) |
| `app/.../feature/home/HomeScreen.kt` | 1,734 | `HomeScreen` 358 lines, 25 parameters, takes four repositories and calls the Discover host from a lambda |
| `iptv/.../feature/guide/GuideScreen.kt` | 1,355 | `GuideScreen` 896 lines: 29 state variables, 36 effects, data loading, paging, focus routing and dialogs in one function |
| `app/.../app/StreamMateApp.kt` | 1,118 | The root composable (928 lines) is also the router, the playback orchestrator, the reminder presenter and the feature wiring |
| Others | 700–1,251 | `CatalogueRepository` 1,251, `PlayerOverlays` 1,223, `CatalogueDao` 1,124, `GuideStore` 1,025, `MetadataRepository` 997, `DirectSportsRepository` 989, `CatalogueBrowserV2` 938, `TvUiComponents` 929, `GuideDao` 872, `AppPreferencesRepository` 863, `StreamMateDatabase` 858, `ChannelEditorScreen` 738, `GuideEntities` 730, `GuideGrid` 725 |

### 3.2 Cross-module leaks

1. **`core` is not a core.** It contains Compose UI (`TvUiComponents`, theme, palettes, focus
   scrolling), app-level classes in package `com.streammate.tv.app` (preferences, profiles,
   locale, remote mapping, themes) and sport models (`TodayEvent`, `FootballIncident`,
   `SportsFollowSettings`). Nothing can be tested or reused without Compose on the classpath.
2. **Split packages.** `com.streammate.tv.app` exists in both `core` and `app`;
   `com.streammate.tv.feature.common` in `core`, `iptv` and `app`; `com.streammate.tv.core.network`
   in `core` (`IptvSourceUrlPolicy`) and `iptv` (`GuideSourceClient`). A package name says nothing
   about where a class lives.
3. **Optional features are wired into the core database.** Sport tables (`sports_api_cache`,
   `team_aliases`, `event_channel_decisions`) and SQL, and Trakt tables and SQL
   (`trakt_state`, `TraktHomeQueries`, `TraktProgressQueries`) live in `core/database`.
4. **The optional modules are not optional.** `addons` and `trakt` are only protocol clients and
   stores; their host, service, UI, scrobbler and account store live in `app` under
   `com.streammate.tv.addons` and `com.streammate.tv.trakt` (36 files). Removing a feature means
   editing the shell.
5. **`iptv` owns the whole Settings tree**, including general, profiles, parental PIN, About,
   updates and diagnostics, so it cannot know about sport, Discover or Trakt and receives them as
   lambdas and composable slots (`loadSportsCompetitions`, `accountsContent`). The update failure
   enum exists twice (`AppUpdateFailure` in `app`, `AppUpdateUiState.Failure` in `iptv`).
6. **The IPTV player knows about sport.** `iptv/.../feature/player/ScoreTicker.kt` draws
   `TodayEvent`s from `core`; `PlayerScreen` takes `scoreTickerEvents` and three navigation
   callbacks (`onGoHome`, `onGoGuide`, `onGoSport`).
7. **Home and Search depend on the sport screen's ViewModel** (`TodayViewModel` is created at the
   root so `HomeScreen(sportsEvents = todayUiState.events)` and `SearchScreen` can read it).
8. **Service locator.** Workers, the playback service and the Discover host reach into the
   application's container; screens receive the container itself.

### 3.3 Duplicated patterns

| Pattern | Where | Cost |
|---|---|---|
| Seven `OkHttpClient.Builder()` | container, `AddonClient`, `AddonMediaTransport`, `StremioCopyClient`, `TraktApiClient`, `TraktAuthClient`, `TraktIdentityClient` | Each has its own connection pool and dispatcher threads; timeouts and user agents differ by accident |
| Two player engines | `StreamMatePlaybackService` (MediaSession) for IPTV/VOD; `AddonPlayback` (in-activity ExoPlayer) for Discover | Track selection, subtitles, reconnect and completion implemented twice; the 6.1 completion bug existed in both |
| Two local HTTP servers | `PhoneSetupServer` (app), `AddonPhoneSession` (addons) | Two token schemes, two lifetimes, two security reviews |
| Two envelope ciphers and Keystore keys | main (`sportmate.iptv.v1`, `streammate_secret_envelope`) and Discover (`sohva.addons.v1`, `sohva_addon_secret_envelope`); hex encoding copied in both cipher classes; SHA-256 hex copied in three places | More key material to migrate and keep valid |
| Four Room databases | `streammate.db`, `sohva-addons.db`, `sohva-addon-library.db`, `sohva-addon-progress.db` | Four connection pools and page caches for three tiny Discover tables |
| Movie and series queries | about 20 near-identical query pairs in `CatalogueDao` (1,124 lines) | Every fix is made twice |
| Preference plumbing | 13 `fromStoredValue`/`fromStored` enum parsers; the list of preference keys written three times (read mapping, `restore()`, backup JSON encode/decode) | A new setting is easy to forget in the backup; `autoFrameRateEnabled` is in fact not backed up |
| Failure types | eight exception/failure families (§2.8) | Each screen maps failures to text its own way |
| Clock labels | `rememberClockLabel` (Home), `rememberTodayClockLabel` (Today) | Two formatters of the same header clock |
| Ad-hoc scopes | six `CoroutineScope(SupervisorJob() + …)` | Work outlives its owner; no single place to pause or cancel |

### 3.4 Start-up and threading problems

- The container, the image loader factory, WorkManager initialisation and lifecycle registration
  run on the main thread in `Application.onCreate`; two PackageManager reads happen there.
- The first app frame waits for `awaitReady()`, whose first step opens the database and runs any
  pending Room migration, then reads the whole preferences object.
- The Discover databases and the second Keystore key open during start-up because Home's resume
  store reads Discover progress.
- ANALYZE runs after imports (good), but on a fresh install nothing has statistics until the first
  import finishes ([Lessons](08-lessons-learned.md) 2.1).
- Bulk work runs on shared pools at normal priority and does not pause during playback.

---

## 4. Target architecture

### 4.1 Principles

1. **UI-free core.** Everything that is not drawing lives in modules without Compose. It is
   testable on the JVM or with an in-memory database, and it is where the lightweight rules are
   enforced.
2. **Features own their slice.** A feature module holds its UI and its feature logic; it reads
   and writes data only through interfaces from the core. Optional features plug into Home,
   Search, Settings and the player through small contribution interfaces, never through the shell.
3. **Nothing heavy is created until it is used.** Graphs are lazy; optional features that are off
   are never constructed; the database opens off the main thread.
4. **Small files, small functions, few parameters.** Files under about 600 lines, composables
   well under ART's AOT limit, at most about 8 parameters per composable (pass state objects).
5. **Data in pages, never whole.** See [AGENTS.md](../../../AGENTS.md) §4 rules 2–3; the paging indexes
   are defined in [04-data-model.md](04-data-model.md).

### 4.2 Modules

Fifteen production modules plus two for tests and profiles. Module names are Gradle paths;
Kotlin namespaces are under `com.sohva.tv` (see 4.16).

| Module | Android? Compose? | Holds | Must not hold |
|---|---|---|---|
| `:core:model` | JVM only, no Compose | Domain types and ids (source, channel, programme, movie, series, episode, content keys), time and time-zone helpers, title normalisation, catch-up URL templates, remote mapping model, feature flag definitions, `AppError` codes, `Clock`, redaction rules | Android classes, I/O |
| `:core:net` | Android library, no Compose | The shared OkHttp base and derived clients, M3U streaming parser, XMLTV pull parser, Xtream streaming JSON client, TMDB and TVmaze clients, update-feed client, the one local phone-setup HTTP server | Database, UI |
| `:core:data` | Android library, no Compose | The main Room database (every table and view, including the optional features' tables that join IPTV data), DAOs and SQL constants, preferences (DataStore), the secret store (Keystore), file stores (logos, caches), backup codec, read repositories that return pages and narrow flows, the one-time importer from the old install if chosen ([04](04-data-model.md) §17) | UI, network |
| `:core:sync` | Android library, no Compose | The import pipeline (one entry point per source and kind), refresh policy, metadata enrichment, film identity, retention, FTS rebuild, ANALYZE, WorkManager workers and the worker factory, reminder alarms, the update check and installer session | UI |
| `:core:player` | Android library, no Compose | The one playback engine: `MediaSessionService`, ExoPlayer setup, data-source resolution, reconnect policy, track choice, buffer profiles, frame-rate matching, progress writing, the "video is playing" signal, a scrobble hook interface | UI, feature knowledge |
| `:ui:design` | Compose | Tokens, the seven colour themes, typography, spacing, the cached screen background, shared TV components, focus and scroll primitives, the navigation host and route types, message/empty/loading components, contribution interfaces for Home rows and Settings sections | Data access |
| `:feature:home` | Compose | Home (hero, rows, rail) and Search | Other features' code |
| `:feature:live` | Compose | Guide, channel management, dialling, find programme, catch-up and reminder UI | |
| `:feature:player` | Compose | The player screen and overlays for live, catch-up, VOD and addon streams | |
| `:feature:vod` | Compose | Movie and series walls, details, seasons and episodes, fix a match | |
| `:feature:settings` | Compose | Settings shell and sections, sources, phone setup UI, library manager, profiles and parental PIN, backup, About and updates, legal, diagnostics | |
| `:feature:sport` | Compose + its own logic | API-Sports client and cache use, matcher, Today screen, match cards, score ticker contribution, Home row and Search contributions | |
| `:feature:discover` | Compose + its own logic | Addon protocol client, its own database `discover.db`, Discover screens, addon stream resolution handed to `:feature:player` | |
| `:feature:trakt` | Compose + its own logic | Trakt clients, device sign-in, sync, scrobbler (implements the player's hook), Home rows and Settings panel | |
| `:app` | Compose | `Application`, `MainActivity`, the `AppGraph`, the navigation table (route → screen), feature flag resolution, manifest components, the demo and Lab variants | Business logic |
| `:testing` (test only) | – | Fakes (clock, HTTP transport, player engine, cipher, scheduler), fixture builders, the slow-query gate, the synthetic owner-scale catalogue | |
| `:benchmark` (test only) | – | Macrobenchmark start-up and journeys, baseline and startup profile generation ([07](07-performance.md)) | |

```
                         :app
      ┌───────────┬────────┼─────────┬──────────┬───────────┬─────────┬──────────┐
      ▼           ▼        ▼         ▼          ▼           ▼         ▼          ▼
 :feature:home :feature:live :feature:vod :feature:player :feature:settings :feature:sport :feature:discover :feature:trakt
      └───────────┴────────┴─────┬───┴──────────┴───────────┴─────────┴──────────┘
                                 ▼
          :ui:design   :core:sync ──► :core:data ──► :core:model
               │           │   └────► :core:net ───► :core:model
               └──► :core:model        :core:player ──► :core:data
```

Why this split and not fewer modules: the five core modules match the five kinds of work that
must never touch the UI thread, and a JVM-only `:core:model` keeps the pure logic (normalisation,
ids, catch-up URLs, rule precedence) in fast JVM tests. Why not more: every screen family that
shares state stays in one module (the guide and channel management share channel paging; Home and
Search share the contribution model). Module count has no runtime cost; R8 merges everything.

### 4.3 Dependency rules

| From | May depend on |
|---|---|
| `:core:model` | Kotlin stdlib, `kotlinx-coroutines-core` |
| `:core:net` | `:core:model`, OkHttp |
| `:core:data` | `:core:model`, Room, DataStore |
| `:core:sync` | `:core:model`, `:core:net`, `:core:data`, WorkManager |
| `:core:player` | `:core:model`, `:core:data`, Media3 |
| `:ui:design` | `:core:model`, Compose, tv-material, Coil |
| any `:feature:*` | `:ui:design`, `:core:model`, `:core:data`; `:core:sync` only through its public trigger interfaces; `:core:player` only `:feature:player`; `:core:net` only the three optional features (their own clients) |
| `:app` | everything |

Rules:

1. **No feature depends on another feature.** Cross-feature use goes through interfaces declared
   in `:ui:design` or `:core:model` and implemented in the providing feature, registered by `:app`:
   `HomeRowContributor` (sport, Trakt, Discover rows), `SearchContributor` (sport results),
   `SettingsSectionContributor` (Sohva Sport, Discover, Accounts sections), `PlayerOverlayContributor`
   (score ticker), `PlaybackObserver` (Trakt scrobbles), `ContinueWatchingSource` (Discover progress).
2. **Only `:app` knows every module.** It builds the graph and the route table.
3. **Each module owns one package root** (4.16); no split packages.
4. **Compose appears only in `:ui:design`, `:feature:*` and `:app`.**
5. Enforced by the Gradle build (dependencies declared with `implementation`, never `api`, except
   `:core:model` exported from the core modules) and by a CI check that fails when a module's
   declared project dependencies are not in the table above.

### 4.4 Dependency injection

**Recommendation: manual constructor injection through lazy graphs.** The app has on the order
of 60–80 bindings, one process and one activity; a DI framework would add build time and generated
code for little gain. No reflection anywhere (the demo provider becomes a build-type source set that
supplies an implementation to the graph). If the graph outgrows hand wiring, a compile-time,
reflection-free generator (for example kotlin-inject) is the fallback; Hilt/Dagger are not used
(annotation processing, larger method count, start-up component creation).

```kotlin
class AppGraph(private val app: Application, val flags: FeatureFlags) {
    val dispatchers: AppDispatchers by lazy { AppDispatchers.create() }
    val data: DataGraph by lazy { DataGraph(app, dispatchers) }          // opens nothing until used
    val net: NetGraph by lazy { NetGraph(app, dispatchers) }
    val sync: SyncGraph by lazy { SyncGraph(app, data, net, dispatchers) }
    val player: PlayerGraph by lazy { PlayerGraph(app, data, dispatchers) }
    val sport: SportGraph? by lazy { if (flags.sport.available) SportGraph(data, net, dispatchers) else null }
    val discover: DiscoverGraph? by lazy { if (flags.discover.available) DiscoverGraph(app, data, net, dispatchers) else null }
    val trakt: TraktGraph? by lazy { if (flags.trakt.available) TraktGraph(app, data, net, dispatchers) else null }
}
```

Rules:

- Constructors take exactly what they use, as interfaces; nobody receives a graph or a `Context`
  it does not need. Screens receive a screen model, never repositories or graphs.
- Graph properties are `lazy` (synchronized); the first touch of `data` or `net` happens off the
  main thread (the start-up sequence in 4.9 guarantees this; a debug-build `StrictMode` policy
  and a test catch violations).
- Workers get their dependencies from a `WorkerFactory` built from the graph
  (`Configuration.Provider` on the `Application`), not from a cast of `applicationContext`.
- The playback service reads the graph once in `onCreate` through a narrow `PlayerGraph`.
- Screen models are created by per-feature factories and scoped to the back-stack entry (4.5).

### 4.5 Navigation and focus restoration

The current behaviour (one stack, push/pop, only the top composed, no transition animation) is
kept ([specs/01](../specs/01-app-shell-navigation.md) §4.3). The implementation changes:

- **Typed, saveable routes.** Routes are a sealed type of small `@Serializable` descriptors that
  carry ids only (never domain records). The stack is saved with `rememberSaveable`, so process
  death and a language change restore the stack instead of restarting at the start route (a quirk
  listed in specs/01 §10). Playback routes restore to the screen underneath, not to the player.
- **Per-entry state.** Each entry gets a `SaveableStateHolder` key and its own `ViewModelStore`, so
  its screen model and scroll state survive while the entry is in the stack and are cleared when it
  is popped. Entries below the top keep only their screen model's small state (ids, focus anchors,
  a page window), never catalogue-sized lists; the stack is bounded in practice by the route rules
  (about 6 entries).
- **Focus memory.** Every screen model exposes a `FocusAnchor` (the key of the focused item and,
  for lists, the key of the first visible item). On return, the screen restores scroll to the
  anchor and requests focus on that key once the item is composed (the frame-retry helper from
  specs/01 §10: up to 30 frames), falling back to the control that opened the child screen, never
  to the first focusable on screen.
- **Overlays close focus-first.** Sheets and dialogs are part of the screen's UI state, not routes.
  Closing one moves focus to its target (scrolled into view while the overlay is still up) and only
  then hides it ([Lessons](08-lessons-learned.md) 4.1).
- **Data never moves focus.** Screen models keep the focused key stable across data refreshes;
  lists use stable keys; the focus column in grids is derived from the item index, never from
  `visibleItemsInfo` ([Lessons](08-lessons-learned.md) 2.4, 4.2).
- **Back goes through the window** (`OnBackPressedDispatcher`), so instrumentation tests can send
  `KEYCODE_BACK` like a remote.
- **Library choice (decide in M0).** A hand-written stack of about 200 lines on top of
  `SaveableStateHolder` and `ViewModelStore` is enough. AndroidX Navigation 3 (which also keeps the
  back stack as a list owned by the app) is acceptable if it adds less than about 200 KB to the
  release APK after R8 and its focus behaviour passes the specs/01 tests. Navigation-Compose with a
  graph DSL is not used.

### 4.6 Screen state: one immutable UI state per screen

- Each screen has one screen model (a `ViewModel` subclass, entry-scoped) exposing
  `StateFlow<ScreenState>` where `ScreenState` is an `@Immutable` data class. Lists inside it are
  immutable (`kotlinx.collections.immutable` or read-only wrappers created once) and bounded: a
  page window of keys plus the rows on screen, never a whole catalogue.
- The screen model receives events (`onKey`, `onFocus(key)`, `onSelect(key)`), reduces them on
  `Dispatchers.Main.immediate` only when the reduction is trivial; loading, mapping, sorting and
  formatting of data run on the dispatchers in 4.7 and deliver a finished state.
- **Split hot and cold state.** Fast-changing values (the focused key, the clock minute, playback
  position) live in separate small `StateFlow`s read in the smallest composable scope (the hero,
  the focused row), so a D-pad press recomposes the hero and one row, not the screen
  ([Lessons](08-lessons-learned.md) 3.7).
- **Narrow data flows.** Screen models observe narrow queries and specific preference keys
  (`preferences.map { it.showChannelNumbers }.distinctUntilChanged()`), never the whole
  preferences object. The app root reads only the stack, the theme and the interface scale.
- Formatting (times, labels, initials, localised status text) is done once per data change in the
  screen model, not during composition.
- Composables are split so that none exceeds about 150 lines and none takes more than about 8
  parameters; a CI gate on the release dex fails any method above 95 % of ART's 10,000 code-unit
  limit ([Lessons](08-lessons-learned.md) 3.3).

### 4.7 Threading and dispatchers

`AppDispatchers` (in `:core:model` as an interface, created in `:app`) is the only place
dispatchers are named. Direct use of `Dispatchers.IO`/`Default` outside it is a lint error.

| Name | Implementation | Thread priority | Used for |
|---|---|---|---|
| `main` | `Dispatchers.Main.immediate` | UI | Composition, focus, trivial state reduction. Nothing else: no database, network, file, parsing, regex, sorting, hashing, encryption |
| `ui` | `Dispatchers.Default.limitedParallelism(2)` | default | Preparing visible data: mapping a page of rows, sorting a page, formatting labels. Two threads leave cores for the render thread and the decoder |
| `io` | `Dispatchers.IO.limitedParallelism(8)` | default | Network calls, small file and preference reads, Keystore unwrap |
| `dbRead` | Room query executor: 3 threads | default | Screen queries (paged, one CursorWindow each) |
| `dbWrite` | Room transaction executor: 1 thread | default | Small user writes (favourite, recent channel, progress) |
| `bulk` | one dedicated thread, `THREAD_PRIORITY_BACKGROUND`, as a coroutine dispatcher | background | Imports (parse and write), EPG retention, metadata enrichment, film identity, sports matching, FTS rebuild, ANALYZE, backup export/restore |

Rules:

1. **Bulk work is paged and cooperative.** Every bulk loop processes one page (at most 2,000 rows,
   or the smaller size plan/07 sets), commits it in its own short transaction, then calls
   `pauseGate.await()` before the next page.
2. **Bulk work pauses while video plays.** `:core:player` publishes `playbackActive: StateFlow<Boolean>`
   (true while a stream is on screen, including picture in picture). `PauseGate` suspends bulk
   loops while it is true, unless the job was started by the viewer (Sync now, first import of a
   new source, restore); viewer-started jobs keep running at background priority with the page
   pause only. Automatic maintenance also waits while the app is in the foreground, as today.
3. **Cancellation is real.** Long reads are pages, so leaving a screen cancels between pages;
   nothing reads a whole source in one statement ([Lessons](08-lessons-learned.md) 1.2–1.3).
4. **One writer at a time for bulk.** One import per (source, kind) at a time through a keyed mutex
   in `:core:sync`, whatever triggered it (worker, Settings, phone setup) — the single entry point
   of the OwnTV study item 2 ([Lessons](08-lessons-learned.md) 2.3).
5. **WAL journal mode is set explicitly** on the main database, so screen reads never wait for an
   import's write transaction. Room's `AUTOMATIC` mode falls back to `TRUNCATE` on devices that
   report `isLowRamDevice`, which includes some 1 GB sticks.
6. **Observed tables are written in batches.** Background writes to tables a visible screen
   observes are batched and rate-limited (at most one invalidation per page), and screens observe
   a cheap invalidation signal plus `conflate()`, then re-read only the visible window
   ([Lessons](08-lessons-learned.md) 1.3, 2.4).

### 4.8 Background work and WorkManager policies

WorkManager is initialised on demand (`Configuration.Provider`, the default initializer removed
from the manifest), so it costs nothing before the first frame. Workers are few and each calls
one entry point in `:core:sync`.

| Work | Type and unique name | Policy | Constraints | Runs |
|---|---|---|---|---|
| Playlist refresh | periodic `sync-playlist` | `UPDATE` | network connected | every 1/2/4/10/24 h (setting `playlist_epg_refresh_interval`, default 24 h); retries after 15 min (linear) while the app is in front |
| EPG refresh | periodic `sync-epg` | `UPDATE` | network connected | same interval as playlist |
| Catalogue refresh | periodic `sync-catalogue` | `UPDATE` | network connected | every 24 h |
| Sync now | one-time `sync-now-<sourceId or all>` | `KEEP` | network connected | playlist, then EPG, then catalogue; viewer-started, never deferred; one catalogue retry after 20 s |
| Metadata enrichment | one-time `metadata-enrichment` | `KEEP` on leaving the app, `REPLACE` after a catalogue import, `APPEND_OR_REPLACE` to continue | network connected | only while no activity is started and no video plays; batch 60, 225 ms between lookups, 4 min per run, continue after 1 s, back off 15 min after 3 consecutive provider retries |
| Maintenance | periodic `maintenance`, 24 h | `KEEP` | storage not low | Cache sweeps and retention ([04](04-data-model.md) §13), stale reminders, ANALYZE when an import marked it pending |
| Legacy names | – | cancelled once after the first frame | – | `streammate-playlist-refresh`, `streammate-epg-refresh`, `streammate-catalogue-refresh`, `streammate-catalogue-metadata-enrichment`, `streammate-catalogue-metadata-enrichment-v2` |

Not WorkManager: reminders use `AlarmManager.setAlarmClock` and a manifest receiver (exact time,
survives Doze; re-armed after boot and at start); the daily update check runs in-app after the
first frame; Sohva Sport polls only while its screen, its Home row or the score ticker is visible;
Trakt sync runs in-app for the active profile and never during playback except scrobbles.

### 4.9 Start-up sequence

| Phase | Thread | Allowed | Not allowed |
|---|---|---|---|
| `Application.onCreate` | main | Create `AppGraph` (lazy holders only), install the diagnostics log, register the foreground tracker (no WorkManager call) | Database open, DataStore read, PackageManager, WorkManager, Keystore, network, image loader construction |
| `attachBaseContext` | main | Below Android 13: read the one small locale preference file (as today, `streammate_locale`) | Anything else |
| `MainActivity.onCreate` | main | `setContent` with the launch screen matching the window background (specs/01 §5.1–5.2) | Waiting for anything |
| Start snapshot | `io` | Read the preferences snapshot needed for the first frame (theme, interface scale, start screen, profiles question, active profile, last channel); open the main database (a Room open is milliseconds when no migration runs) | Data backfills, ANALYZE, imports, reading Discover or Trakt stores |
| First frame | main | Home (or the start route) with its skeleton; Home's rows fill as their paged reads return | – |
| After first frame (`reportFullyDrawn` + one idle frame) | `io` / `bulk` | Schedule periodic work, cancel legacy work names, re-arm reminders, update check if due (once a day), start the Trakt loop for the active profile if connected, one-time importer steps (if the owner chose that path), pending ANALYZE when the app is idle | Anything that competes with the viewer's first key presses at normal priority |

Rules:

- **Migrations stay off the launch path.** Room migrations change schema only (O(schema), never
  O(rows)); any data rewrite is a resumable background job with a "pending" marker, and screens work
  with the old shape until it finishes.
- **ANALYZE never runs at launch.** It runs after an import and in maintenance, on `bulk`.
- **Optional features cost nothing until used.** Sport, Discover and Trakt graphs are constructed
  on first use (their Home row contributions ask them lazily after Home's own rows are drawn).
- Budgets: Home within 2 s (Shield) and 4 s (low-end box) from a cold start
  ([07](07-performance.md)).

### 4.10 Feature flags for optional features

`FeatureFlags` (in `:core:model`) is resolved once by `:app` from the build variant and the
package name, then refined at runtime:

| Feature | Build-time availability | Runtime condition | Restricted profile | Cost when off |
|---|---|---|---|---|
| Sohva Sport | all builds | Active when an API-Sports key is stored; automatic refresh off in Lab | Allowed (as today) | Graph not built; no polling; tables empty |
| Discover | `com.streammate.tv`, `.debug`, `.lab`; not demo | Always available where built | Never shown | Graph not built; `discover.db` never opened |
| Trakt | builds that carry Trakt client credentials | Per profile, when connected | Never shown | Graph not built; no sync loop |
| Reminders | not Lab | – | – | Receiver disabled in Lab |
| Public updates | `com.streammate.tv` only | – | – | Update client not built |
| Demo content | demo build only | – | – | Absent from other builds (source set, no reflection) |

Flags decide whether a graph exists, whether contributions register, and which routes exist.
Screens never check package names.

### 4.11 Error, empty and loading states

One model for everything the viewer can see go wrong:

- **`AppError`** (sealed, `:core:model`) with a **stable code string** and typed arguments, for
  example `transport_failed(detail)`, `http_status(code)`, `epg_empty`, `epg_unmatched`,
  `source_no_vod`, `connection_limit(sourceName, limit)`, `tmdb_key_invalid`, `api_quota_exhausted`,
  `trakt_reauthorize`, `storage_full`, `unknown`. Codes are persisted in refresh states and the
  diagnostics file instead of resource ids (the old app had to guard against ids renumbered between
  builds). Each code maps to exactly one string resource in the UI layer, with the texts the specs
  give (for example `error_epg_empty` "The programme guide came back empty. The previous guide was
  kept.").
- Boundaries convert: network clients and parsers throw typed failures; `:core:sync` and
  repositories return `Outcome<T>` (`Ok`/`Failed(AppError)`); library exceptions are redacted and
  mapped to `transport_failed` or `unknown`. Cancellation is never an error.
- **`LoadState<T>`** for every screen section: `Loading`, `Ready(T)`, `Empty(reason)`,
  `Failed(AppError, retry)`. One set of shared components in `:ui:design` draws them, with the
  exact texts from the feature specs. A section that fails does not blank the rest of the screen
  (Home's rows appear independently).
- Plain language only: no raw codes, stack traces or resource names on screen
  ([AGENTS.md](../../../AGENTS.md) §5 rule 6).

### 4.12 Network clients and images

- One `OkHttpClient` base (shared connection pool and dispatcher) in `:core:net`; every other client
  is `base.newBuilder()` so pools and threads are shared:
  - provider client: HTTP/1.1 only (IPTV panels reset HTTP/2 streams), connect 20 s, read 90 s,
    redirects followed (current values);
  - playback client: HTTP/1.1, used by the Media3 data source;
  - API/image client: HTTP/2 allowed (poster grids multiplex; OwnTV study item 22);
  - addon and Trakt clients: redirects off, as today.
- One user agent (`PlaybackHttp.userAgent` shape, version and Android release) computed once,
  lazily.
- Coil 3 singleton built lazily: disk cache `cache/catalogue_artwork` (100/250/500 MB, setting,
  default 250 MB), memory cache 8 % of the memory class, at most 2 parallel decodes, requests sized
  to the drawn size, `RGB_565` for opaque art ([Lessons](08-lessons-learned.md) 1.6–1.7).

### 4.13 Playback engine

One engine in `:core:player` for every stream (live, catch-up, VOD, Discover): one ExoPlayer in a
`MediaSessionService`, reached through a `PlaybackEngine` interface that `:feature:player` uses
and tests fake. Player commands are issued off the main thread where Media3 allows; leaving the app
releases the stream; bulk work is paused through `playbackActive`. Discover resolves an addon
stream into a `PlaybackRequest` and hands it to the same engine, so completion, reconnect, tracks
and subtitles exist once. Details: [specs/30](../specs/30-player.md).

### 4.14 Diagnostics and logging

- `DiagnosticsLog` interface in `:core:model`, a bounded ring buffer (600 lines, as today)
  implemented in `:core:data`; every line redacted before it is stored; no file written unless the
  viewer saves diagnostics.
- Named timing marks for start-up, first Home frame, first guide rows and imports go to the log
  and to `Trace` sections (for Perfetto and the benchmark module).
- No crash upload, no analytics ([AGENTS.md](../../../AGENTS.md) §6).

### 4.15 Testing seams

| Seam | Interface | Test double |
|---|---|---|
| Time | `Clock` (wall and monotonic) | Virtual clock; fixtures anchored to "now" ([Lessons](08-lessons-learned.md) 5.4) |
| Dispatchers | `AppDispatchers` | Test dispatchers; the `bulk` pause gate controllable |
| HTTP | OkHttp with an injectable base | MockWebServer or a loopback fixture server with the synthetic owner-scale catalogue |
| Secrets | `SecretCipher`, `KeyProvider` | JVM fake; real Keystore only in instrumentation |
| Database | DAOs and repositories | In-memory Room; JVM query-plan tests with sqlite-jdbc on the exported schema for every hot query (as `CatalogueHomeQueryPlanTest` does today) |
| Slow data | `QueryGate` (debug and test builds) | Delays a named query so timing bugs reproduce ([Lessons](08-lessons-learned.md) 7.4) |
| Playback | `PlaybackEngine` | Fake engine with scripted states (buffering, error, ended) |
| Scheduling | `WorkScheduler`, `AlarmScheduler` | Recording fakes; WorkManager test helpers for workers |
| Feature flags | `FeatureFlags` | Any combination per test |
| Persistent state | `ClearStateRule` | Resets profiles, language, start screen, last guide source in `finally` ([Lessons](08-lessons-learned.md) 7.5) |

### 4.16 Package naming and identity

- **Application ID stays `com.streammate.tv`**, signed with the existing key; variants keep their
  suffixes (`.debug`, `.demo`, `.lab`) ([00-product-overview.md](00-product-overview.md) §6).
- **Namespaces are new**: `com.sohva.tv.core.model`, `com.sohva.tv.core.net`,
  `com.sohva.tv.core.data`, `com.sohva.tv.core.sync`, `com.sohva.tv.core.player`,
  `com.sohva.tv.ui.design`, `com.sohva.tv.feature.<name>`, `com.sohva.tv.app`. Each module owns
  exactly one root.
- **Component names that the system or launchers remember** must stay reachable:
  - Keep the launcher activity's component name `com.streammate.tv.app.MainActivity` (either the
    class keeps that name, or an `<activity-alias android:name="com.streammate.tv.app.MainActivity">`
    targets the new activity). TV launchers store favourites and pinned apps by component name.
  - Alarms set by beta 23 target `com.streammate.tv.app.ReminderReceiver`; the rebuild re-arms its
    own alarm at first start, so the old one may be lost harmlessly — but keep the action string
    `com.streammate.tv.REMINDER_FIRE` and the open-request extras (`com.streammate.tv.OPEN_CHANNEL`,
    `com.streammate.tv.OPEN_EVENT`) so notifications still posted by the old build open correctly.
  - WorkManager rows of the old build name worker classes that no longer exist; cancel the legacy
    unique names after the first frame (4.8).
  - The FileProvider authority stays `${applicationId}.updates`.
- Data paths (database file names, preference file names, Keystore aliases) are decided by the
  existing-users option in [04-data-model.md](04-data-model.md) §17.

### 4.17 Mapping: current packages to target modules

| Current module › package | Content | Target |
|---|---|---|
| `app` › `com.streammate.tv.app` | `MainActivity`, `StreamMateApplication`, launch screen, `PictureInPicture`, `OpenRequests`, `InterfaceScaled` | `:app` |
| | `StreamMateApp` (router, playback orchestration, reminder presenter) | `:app` (route table) + `:ui:design` (navigation host) + screen models in the features |
| | `StreamMateContainer`, `AppRuntimePolicy`, `AddonFeature` | `:app` (`AppGraph`), `:core:model` (`FeatureFlags`) |
| | `GuideRefreshScheduler`/`Worker`, `CatalogueMetadataScheduler`/`Worker`, `StreamMateForegroundState` | `:core:sync` |
| | `StreamMatePlaybackService`, `PlaybackBufferPolicy`, `StreamMimeTypes`, `ExternalPlayerLauncher` | `:core:player` |
| | `Reminders` (scheduler, receiver), `ReminderAlertDialog`, `ReminderOverlayPromptDialog` | `:core:sync` (alarms, receiver), `:feature:live` (dialogs) |
| | `StreamMateBackupManager`, `OrganizationBackupCodec` | `:core:data` (backup codec and streams), `:feature:settings` (UI) |
| | `PhoneSetupServer` | `:core:net` (the one local server), `:feature:settings` (UI) |
| | `AppUpdateChecker`, `AppUpdates`, `UpdateSessionInstaller` | `:core:net` (feed client), `:core:sync` (download and installer session), `:feature:settings` (UI) |
| | `DiagnosticsReport` | `:core:data` |
| | `ChannelLogoStore` | `:core:data` |
| | `DemoContentProvider` | demo source set of `:app` |
| | `ProfilePickerScreen` | `:feature:settings` (profiles), shown by `:app` at start |
| `app` › `…feature.home`, `…feature.search` | Home, resume store, Search | `:feature:home` |
| `app` › `…feature.legal` | Legal information | `:feature:settings` |
| `app` › `…feature.common` | navigation icons | `:ui:design` |
| `app` › `com.streammate.tv.addons` | Discover screens, host, addon player, subtitles | `:feature:discover` (player part merged into `:core:player`/`:feature:player`) |
| `app` › `com.streammate.tv.trakt` | Trakt service, account store, scrobbler, settings panel, title screen | `:feature:trakt` |
| `core` › `com.streammate.tv.app` | `AppPreferencesRepository`, `Profiles`, `AppLocale`, `ArtworkCacheSettings`, `MetadataLanguages`, `InterfaceScale` | `:core:data` (stores) + `:core:model` (value types) |
| | `ColorTheme`, `StreamMateTheme`, `StreamMateContentColors` | `:ui:design` |
| | `RemoteMapping` | `:core:model` |
| `core` › `…core.database` | database, entities, DAOs, SQL | `:core:data` |
| `core` › `…core.security` | ciphers, Keystore, secret store, codec, redactor | `:core:data` (store, ciphers), `:core:model` (redaction rules) |
| `core` › `…core.error`, `…core.diagnostics`, `…core.concurrent`, `…core.reminders` | error model, log, helpers, reminder timing | `:core:model` |
| `core` › `…core.model` | domain models | `:core:model` (sport models to `:feature:sport`) |
| `core` › `…core.network` | `IptvSourceUrlPolicy` | `:core:net` |
| `core` › `…feature.common` | `TvUiComponents`, `FocusScroll`, `FocusRequests`, `TickerFlow` | `:ui:design` |
| `iptv` › `…core.network` | `GuideSourceClient` | `:core:net` |
| `iptv` › `…iptv.m3u`, `…iptv.xmltv`, `…iptv.xtream` | parsers and clients | `:core:net` |
| `iptv` › `…iptv.repository` | import services, stores, repositories | `:core:sync` (imports), `:core:data` (repositories) |
| `iptv` › `…iptv.metadata` | TMDB, TVmaze, matcher, work keys, repository | `:core:net` (clients), `:core:model` (matching and work keys), `:core:sync` (enrichment), `:core:data` (cache) |
| `iptv` › `…iptv.playback` | playback repository, catch-up resolver, connection limiter | `:core:player` (+ catch-up templates in `:core:model`) |
| `iptv` › `…feature.guide`, `…feature.common` (`ChannelDial`) | guide, dialling | `:feature:live` |
| `iptv` › `…feature.player` | player screen, overlays, diagnostics overlay | `:feature:player` (score ticker to `:feature:sport` as a contribution) |
| `iptv` › `…feature.catalogue`, `…feature.catalogue.v2` | walls, details, match picker | `:feature:vod` |
| `iptv` › `…feature.settings` | Settings tree | `:feature:settings`; `ChannelEditorScreen` to `:feature:live`; `RemoteMappingSection`, `ParentalPinScreen`, `AboutSection`, `TimeZonePicker`, `LibraryManagerScreen` stay in `:feature:settings` |
| `sportmate` › `…feature.today`, `…matching`, `…sports.repository` | sport | `:feature:sport` (tables stay in `:core:data`, see [04](04-data-model.md) §15) |
| `addons` › `com.sohva.tv.addons`, `….storage` | protocol, stores, databases | `:feature:discover` |
| `trakt` › `com.sohva.tv.trakt` | clients, device auth | `:feature:trakt` |

---

## 5. Lightweight by design

- **Start-up:** nothing but lazy holders on the main thread before the first frame; database,
  preferences, WorkManager, Keystore and the image loader are touched off the main thread or on
  first use; optional features are not constructed until used (4.9, 4.10).
- **Memory:** one shared OkHttp pool; one playback engine; two databases instead of four; no
  graph holds a list larger than a page; screen models below the top of the stack keep ids and
  anchors only; image caches sized from the memory class.
- **Threads:** a fixed, small set of dispatchers (4.7); background priority for all bulk work;
  bulk pauses during playback; the main thread never waits for I/O.
- **Recomposition:** narrow flows per consumer, hot state read in the smallest scope, formatting
  outside composition, stable keys; the root recomposes only for stack, theme and scale changes.
- **Code shape:** small files and composables keep every hot method under ART's AOT limit so the
  baseline profile can compile it; R8 and install profiles from the first release
  ([AGENTS.md](../../../AGENTS.md) §4 rule 10).
- **Measured, not assumed:** each rule above has a check in plan/07 (start-up Macrobenchmark,
  key-press traces, heap sampling during imports) on the low-end stand-in.

## 6. Lessons from the current app

| Lesson | Evidence | Rule here |
|---|---|---|
| A 2,357-line and a 1,103-line composable ran interpreted; a 41-parameter composable broke release dex verification | [Lessons](08-lessons-learned.md) 3.3–3.4; `SettingsScreen.kt`, `PlayerScreen.kt` | 4.6 size limits and the dex gate |
| The whole preferences object recomposed the root on every zap | specs/01 §9 | 4.6 narrow flows |
| Room's blocking reads ignored cancellation and starved four IO threads | [Lessons](08-lessons-learned.md) 1.3 | 4.7 rules 1–3 |
| Two imports of one source raced and emptied the guide | [Lessons](08-lessons-learned.md) 2.3; ledger 23 Sept 2026 | 4.7 rule 4, one entry point |
| Metadata writes re-emitted flows a visible wall collected (suspected Left-to-rail focus bug) | [Lessons](08-lessons-learned.md) 2.4 | 4.7 rule 6, 4.5 data never moves focus |
| The film-identity pass on the default pool competed with the screen; on its own background thread it gave way | `OrganizationRepository.kt` | `bulk` dispatcher for all bulk work |
| Optional features lived in the shell and the core database | §3.2 | 4.2–4.3 modules and rules |
| A launch-time database open ran migrations before the first frame | §3.4 | 4.9 migrations off the launch path |
| Resource ids stored in refresh states resolved to the wrong text after a rebuild | `LocalizedException.kt` (`StoredFailureMessage`) | 4.11 stable error codes |
| Two player engines meant fixing completion twice | [Lessons](08-lessons-learned.md) 6.1 | 4.13 one engine |

## 7. Open questions

1. Navigation 3 or a hand-written stack (4.5): decide in M0 by APK size after R8 and the specs/01
   focus tests.
2. Whether Discover's addon player needs anything the MediaSession engine cannot give it (the
   current code chose an in-activity ExoPlayer; the reason is not recorded in the code). Confirm
   before M9.
3. Whether Sohva Sport polling should also stop on the Home row when the row is off screen (today
   it follows the sport screen, the ticker and Home).
4. Whether restricted profiles may see Sohva Sport (today they can; the specs for profiles and
   sport should confirm).
5. The exact `bulk` page sizes and pause timings belong to plan/07 after measurement on the
   stand-in; this file only fixes that they exist.

## 8. Reference: current code map

- `app/src/main/java/com/streammate/tv/app/StreamMateApplication.kt` — process start, image loader, lifecycle and reminder wiring.
- `app/src/main/java/com/streammate/tv/app/StreamMateContainer.kt` — the manual DI container.
- `app/src/main/java/com/streammate/tv/app/StreamMateApp.kt` — `Destination` back stack, router, playback orchestration.
- `app/src/main/java/com/streammate/tv/app/MainActivity.kt` — activity, launch screen, interface scaling, PiP.
- `app/src/main/java/com/streammate/tv/app/StreamMateForegroundState.kt` — foreground tracking for deferrable work.
- `app/src/main/java/com/streammate/tv/app/GuideRefreshScheduler.kt` — refresh worker and schedules.
- `app/src/main/java/com/streammate/tv/app/CatalogueMetadataScheduler.kt` — metadata worker.
- `app/src/main/java/com/streammate/tv/app/Reminders.kt` — alarm scheduling and receiver.
- `app/src/main/java/com/streammate/tv/app/StreamMatePlaybackService.kt` — MediaSession playback service.
- `app/src/main/java/com/streammate/tv/app/DemoContentProvider.kt` — reflective demo seam.
- `app/src/main/java/com/streammate/tv/app/AppRuntimePolicy.kt`, `AddonFeature.kt` — package-based feature policy.
- `app/src/main/java/com/streammate/tv/addons/AddonHost.kt`, `AddonPlayback.kt` — Discover host singleton and second player.
- `core/src/main/java/com/streammate/tv/core/error/LocalizedException.kt` — error model.
- `core/src/main/java/com/streammate/tv/core/diagnostics/DiagnosticsLog.kt` — redacted in-memory log.
- `iptv/src/main/java/com/streammate/tv/iptv/repository/OrganizationRepository.kt` — the one background-priority dispatcher.
- `iptv/src/main/java/com/streammate/tv/feature/catalogue/v2/CatalogueBrowserStore.kt` — the best existing state holder.
- `sportmate/src/main/java/com/streammate/tv/feature/today/TodayViewModel.kt` — the only ViewModel.
- `*/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml` — modules and versions.
