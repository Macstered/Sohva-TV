# Quality, testing and release

> How Sohva TV `0.1.0-beta.23` (build 57) is tested, checked and published, and the quality plan
> for the rebuild. Build variants and tools are in
> [05-tech-stack-and-build.md](05-tech-stack-and-build.md); budgets and measurement in
> [07-performance.md](07-performance.md); the updater that consumes releases in
> [specs/72](../specs/72-updates-about-diagnostics.md).

## 1. What this file decides

- The current test inventory, infrastructure, device traps, CI and release gates (§2–§9), so the
  rebuild keeps what worked and knows why each gate exists.
- The versioning rules, the five release assets and the release-body contract that installed builds
  depend on (§7–§8). These must not change.
- The rebuild's quality plan: test pyramid, a small fast device suite, performance tests in CI and
  the animation safety test (§10).

Counts in §2 were taken from the beta 23 tree (`efab52a`) by counting `@Test` annotations per source
set; CI and receipt figures are quoted from the private receipts (`docs/SOHVA_TV_BETA_23.md`,
`docs/PUBLIC_RELEASE_CHECKLIST.md`).

## 2. Current test inventory (beta 23)

### 2.1 By source set

| Source set | Files | `@Test` | Runs where | Notes |
|---|---|---|---|---|
| `app/src/test` | 21 | 83 | JVM, CI `testDebugUnitTest` | |
| `app/src/testDemo` | 1 | 2 | JVM, `testDemoUnitTest` only | not in CI |
| `core/src/test` | 28 | 121 | JVM | includes the sqlite-jdbc query-plan tests |
| `iptv/src/test` | 49 | 296 | JVM | parsers, guide, player logic, catalogue, metadata |
| `sportmate/src/test` | 7 | 66 | JVM | |
| `trakt/src/test` | 4 | 34 | JVM | MockWebServer |
| `addons/src/test` | 35 | 176 | JVM | 4 opt-in live checks skip by default |
| **JVM total** | 145 | **778** (776 in CI) | | receipt: 776 unit tests, 4 skipped, 0 failed |
| `app/src/androidTest` | 69 (3 helpers) | 285 | Android TV emulator, debug build | 10 opt-in (skipped unless an argument is given) |
| `core/src/androidTest` | 11 | 74 | emulator | migrations, DAOs, Keystore |
| `addons/src/androidTest` | 4 | 8 | emulator, run by the Lab harness | Discover's Room databases |
| `app/src/androidTestLab` | 25 | 94 | emulator, Lab build, 21 groups | synthetic addon suite, VOD completion, benchmark seed |
| `app/src/androidTestRelease` | 1 | 4 | disposable emulator, signed release, opt-in | upgrade from an older signed beta |
| `scripts/tests/addon-phone-page.test.cjs` | 1 | 9 | Node `node --test` | runs the exact inline script the TV serves |
| **Total** | | **1,252** | | |

Hosted results on the release commit: Build `35904221418` (unit tests, 9 phone-page tests, lint,
all instrumentation compiled, release and Lab assembled); Instrumentation `35904234203`: "app 295 run
with 10 skipped and none failed, core 74, all 21 Lab groups". The private worktree's full device suite
on the same code: 284 run, none failed, 10 opt-in skips. (The difference between 285 annotated, 284
and 295 reported is not explained by the sources; §13.)

### 2.2 JVM tests by area

| Area | Tests | Main classes |
|---|---|---|
| Import and parsers | 61 | `M3uParserTest` 12, `XmlTvParserTest` 9, `XmlTvTimestampParserTest` 3, `XmlTvParserThroughputTest` 1, `XtreamClientTest` 8, `XtreamM3uCompatibilityTest` 2, `GuideImportServiceTest` 18, `XtreamImportServiceTest` 2, `GuideSourceClientTest` 2, source validators 4 |
| Guide | 36 | `GuideWindowedReadTest` 13, `GuideTimeWindowTest` 8, `GuideGenreAccentTest` 4, `GuideFirstGroupTest` 3, `GuideSourceSelectionTest` 3, `GuideScheduleTest` 3, `ChannelDialTest` 2 |
| Player and playback | 93 | `PlayerFormattingTest` 14, `PlayerScreenTest` 13, `RemoteMappingTest` 13, `AutoFrameRateTest` 7, `CatchupUrlResolverTest` 6, `SeekStepperTest` 6, `StreamMimeTypesTest` 6, buffer, reconnect, subtitle look, PiP, connection limiter |
| Movies, series, metadata | 135 | `CatalogueBrowserStoreTest` 17, `CatalogueBrowseDataSourceTest` 14, `CatalogueCollapseTest` 11, `CatalogueWorkKeyTest` 11, `MetadataMatcherTest` 10, `TmdbGenresTest` 10, `CatalogueCopyPreferenceTest` 10, copy claims, watched rule, artwork cache, library manager models |
| Organisation model | 35 | `LibraryOrganizationTest` 13, `CatalogueCustomGroupTest` 10, `ChannelStreamTagsTest` 7, `LibraryOrganizationEquivalenceTest` 1 (against `ReferenceLibraryOrganization`), views, category keys |
| Query plans (sqlite-jdbc) | 21 | `GuideChannelsQueryPlanTest` 11, `CatalogueHomeQueryPlanTest` 10 |
| Security and diagnostics | 24 | ciphers, source configuration codec, backup cipher, `SecretRedactorTest`, `DiagnosticsLogTest`, `DiagnosticsReportTest` |
| Preferences, profiles, themes, timers | 26 | profiles and restrictions, colour themes, interface scale, refresh interval, reminders schedule, `TickerFlowTest` |
| Shell, updates, scheduling, backup, phone setup | 42 | `AppUpdatesTest` 9, `PhoneSetupProtocolTest` 8, `AppRuntimePolicyTest` 5, `GuideRefreshSchedulerTest` 5, `OrganizationBackupCodecTest` 4, `TranslationParityTest` 2 |
| Home | 14 | `HomeResumeStoreTest` 9, `ArtworkInitialsTest` 5 |
| Sohva Sport | 66 | `EventChannelMatcherTest` 22, `DirectSportsRepositoryTest` 12, `TodaySectionsTest` 11, polling policy, timeline, match cache |
| Discover | 187 | 176 in `addons` (manifest, client, catalogs, search, library, progress, stores, imports, Stremio copy) + 11 in `app` |
| Trakt | 36 | `TraktDeviceAuthorizerTest` 12, `TraktAuthClientTest` 11, identity and API clients, addon identity |

### 2.3 Device tests by area (`app` 285, `core` 74, `addons` 8)

| Area | Tests | Main classes |
|---|---|---|
| Guide | 56 | `GuideScreenTest` 41, `GuideChannelPagingTest` 10, `GuideHeldKeyPagingTest` 2, `GuideNavigationTraceTest` 1, `GuideChannelReadBenchmarkTest` 1, `GuideImportBenchmarkTest` 1 |
| Movies and series | 81 | `CatalogueRepositoryTest` 13, `CatalogueBrowserV2ScreenTest` 9, `CataloguePinnedMatchTest` 7, `CatalogueReturnFocusTest` 6, genre rail and enrichment 12, `MetadataQueuePagingTest` 1, `MovieIdentityPagingTest` 1, `CatalogueBrowseQueryBenchmarkTest` 1 |
| Settings | 38 | `SettingsScreenTest` 24, `LibraryManagerFocusTest` 6, `LibraryManagerBenchmarkTest` 2, remote mapping, colour theme, parental PIN, `SettingsScreenshotDumpTest` |
| Home | 25 | `HomeResumeNavigationTest` 12, `HomeScreenTest` 7, `FocusScrollMechanismTest` 3, reopen, resume database, backdrop |
| Sohva Sport | 24 | `MatchHubTest` 11, `TodayScreenTest` 4, `TodayPollingTest` 3, persistence 3, channel row 2, `SportsMatchingMemoryTest` 1 (opt-in) |
| Player | 6 | `PlayerChromeOverlayTest` |
| Discover | 17 | `AddonIntegrationTest` 4, `AddonStartupPerformanceTest` 5 (opt-in), `addons` Room tests 8 |
| Shell and platform | 46 | `BackupCustomGroupsTest` 9, `LocalizedExceptionTest` 8, `AppLocaleTest` 5, `LocalizationResourcesTest` 4, reminder probes 3 (opt-in) and dialog 2, phone setup server, logo store, network security, splash, brand, `UpdateSessionInstallerDeviceTest` (opt-in), `ConcurrentPlaylistImportTest` |
| Database and security (`core`) | 74 | `StreamMateDatabaseMigrationTest` 26, DAO and view tests 35, `CustomGroupEncodingTest` 7, `SecretSettingsStoreMigrationTest` 5, `AndroidKeystoreSecretCipherTest` 1 |

## 3. Current test infrastructure

- **Compose UI tests on the Android TV emulator.** `createAndroidComposeRule<MainActivity>()` on the
  debug package (`com.streammate.tv.debug`), 1920×1080 TV profile (`tv_1080p`), API 30 in CI.
  `ClearAppStateRule` (refuses any package but `.debug`) runs before the activity and resets secure
  sources, artwork-cache and locale preferences, the guide repository, colour theme, extra profiles,
  the active profile and profile restrictions. `ComposeWaits` holds bounded waits;
  `requestFocusWhenAttached` retries focus across frames and checks the returned Boolean.
  `timeout_msec = 300000` caps a hung test at five minutes.
- **Local runners.** `scripts/test-android-tv-emulator.ps1` accepts only `emulator-NNNN` serials,
  checks `ro.kernel.qemu = 1` and boot completion, installs the core test APK, the debug app and its
  test APK, runs `am instrument -w -r`, and fails unless the log contains `OK (N tests)` without
  `FAILURES!!!`, `INSTRUMENTATION_FAILED` or a negative status code (adb exits 0 even when JUnit
  fails). `scripts/Invoke-ConnectedTest.ps1` changes nothing on the device: it warns when another
  app holds the foreground or the display sleeps.
- **MockWebServer fixtures.** JVM tests of the Xtream client, guide source client, addon protocol and
  Trakt clients; device tests for Discover (in-process servers with dispatchers returning synthetic
  catalogs, metadata, streams and subtitles). Fixtures use `example.invalid` and fictional data.
- **DAO and migration tests.** `core/schemas/` and `addons/schemas/` are exported by Room and added
  to the device test assets; `StreamMateDatabaseMigrationTest` walks the migrations (26 tests).
- **Query-plan tests on the JVM.** `CatalogueHomeQueryPlanTest` and `GuideChannelsQueryPlanTest`
  open the exported schema in `sqlite-jdbc`, fill synthetic rows (5,000 channels and 300,000
  programmes; 50,000 channels), run `EXPLAIN QUERY PLAN` on the production SQL constants and fail on
  a full scan of a title table, a snapshot-prefix-only reach into `vod_series`, a
  `TEMP B-TREE`, or a guide channel read that touches `tv_programmes`; they also time the queries
  (under 1,000 ms for local Home queries, under 100 ms for the Trakt query).
- **Equivalence and mutation tests.** The rewritten organisation ordering is held to a reference
  implementation over 600 randomised rounds; the faster visibility predicate to the canonical one
  over 400 randomised rule sets; each check was shown to fail when its guard was broken on purpose.
- **Benchmarks as tests** (print timings for comparison, assert only generous bounds):
  `GuideChannelReadBenchmarkTest` (50,000 channels on disk, paged vs one cursor: 529 ms vs 4,822 ms),
  `GuideImportBenchmarkTest`, `CatalogueBrowseQueryBenchmarkTest` (< 5,000 ms),
  `LibraryManagerBenchmarkTest`, `XmlTvParserThroughputTest` (< 60 s), `GuideNavigationTraceTest`
  (named trace sections and `GuideNavigation:*` markers for Perfetto).
- **Opt-in stress and probes** (skip unless an instrumentation argument is set):
  `SportsMatchingMemoryTest` (`-e sportsMemoryStress true`: 56,164 channels, 112,328 programmes with
  2,150-character descriptions, heap peak < 128 MiB asserted, main-thread heartbeat), reminder alarm,
  activity and cold probes, `UpdateSessionInstallerDeviceTest` (needs a host script to confirm the
  system dialog; emulator only), `AddonStartupPerformanceTest` (`-e syntheticPlayback true`).
- **Lab.** The `lab` build (`com.streammate.tv.lab`, release code, debug-signed) with
  `src/androidTestLab`; `scripts/Test-SohvaAddonsLab.ps1` builds it, generates FFmpeg media
  (colour bars and tone; MP4, HLS, DASH, MKV with Finnish/English and timing subtitles),
  installs only Lab on an explicit emulator, and runs 21 groups: room, seed, restart after process
  death, ui-access, import, account-import, phone-landing, navigation-settings, cast-details, loading,
  hero-synopsis, localization, catalog-stress, playback, automatic-subtitles, subtitle-timing,
  player-navigation, playback-completion (Discover and VOD), search, library, cleanup.
  `LabBenchmarkLibrarySeed` fills Lab with the owner-scale synthetic library for the slow-box harness
  ([07](07-performance.md) §6).
- **Demo fixtures.** The `demo` build seeds fictional rows, encrypted source settings, sports results
  and match events at every cold start, anchored to the current hour, in English, with generated
  artwork; `docs/demo-screenshot-mode.md` documents it; `testDemo` has 2 tests.
- **Release upgrade suite.** `ReleaseUpgradeTest` seeds synthetic data through an older signed beta's
  APIs, then verifies it after `install -r` of the new one, in separate instrumentation processes;
  refuses to run unless `-e sohvaDisposableReleaseTest true`, the package is `com.streammate.tv` and
  the hardware is `ranchu`/`goldfish`.
- **Language review.** `SettingsScreenshotDumpTest -e language <tag>` writes Settings screenshots in
  one language to app files and clears the language afterwards.

## 4. Device-test traps

Each of these cost a day or shipped a wrong fix ([08-lessons-learned.md](08-lessons-learned.md) §7).

1. **Tests see an empty app.** Instrumentation runs the debug package, whose database is empty; the
   owner's content is in the release package. Reproduce the mechanism with synthetic data at real
   sizes, and prove each regression test fails on the broken code before trusting it (a Home focus
   test once passed on known-broken code).
2. **No gap between key and data.** An in-memory database answers before the next key repeat
   (50 ms); on the Shield a programme read took 150 ms or more. Gate or delay the named query
   (a query callback sleeping 300 ms reproduced the held-key bug), send native key events with one
   down time and increasing repeat counts, and assert states, not throughput.
3. **Leftover state.** A second profile, a chosen language, a colour theme, a restriction, a saved
   guide source (`lastGuideSourceId`) or a configured source changes the next test's first screen.
   Extend the clear-state rule with every setting that changes start-up; clean up in `finally`
   through the repository.
4. **Back inside Compose never reaches `BackHandler`.** Send it through the window:
   `instrumentation.sendKeyDownUpSync(KEYCODE_BACK)`.
5. **The Compose test clock is virtual.** `Thread.sleep` does not let a `delay` elapse; asserting
   that something is absent after a delay passes vacuously. `while (true) { delay() }` loops in
   `LaunchedEffect` kept the clock from ever idling; periodic ticks now come from a flow whose wait
   runs upstream of `flowOn(Dispatchers.Default)`.
6. **Races with layout.** Focus requested after a fixed 80 ms delay raced the lazy row's layout;
   `performScrollToNode` on a list with nothing to scroll never returned and spun the main thread.
7. **Fixture dates expire.** The guide keeps 12 hours of history and 8 days ahead; a fixture written
   for 1 September failed from 8 September. Anchor to "now".
8. **Emulator clock.** A snapshot boot left the clock a week behind and the guide empty; cold-boot
   (`-no-snapshot-load`) for guide tests and measurements.
9. **A sleeping display** stops the activity and turns the rest of a run into identical "No compose
   hierarchies found" failures. Never change a real device's display or screensaver settings (a
   wrapper once died before restoring them and left the owner's TV never sleeping); run long suites
   in pieces. Only the disposable hosted emulator has its screensaver turned off.
10. **Another app in front** cannot be displaced by instrumentation; every query then times out.
11. **Never `connectedAndroidTest` with a TV attached**: it runs on every connected device. Target an
    explicit emulator serial.
12. **Logs**: the emulator scripts' instrumentation logs are UTF-16 (strip NULs before searching);
    adb's exit code does not reflect test failures.
13. **Two installed packages** on the Shield (release and debug); the owner runs the release one.
14. **Stray runners**: stopping a background shell does not stop its child scripts; three runners
    once drove one emulator at once and every measurement was garbage. Check for running
    `compare`/`journey` processes first.
15. **Timing assertions under load**: `GuideChannelReadBenchmarkTest` failed its timing assertion in
    the full suite (paged 2,749 ms vs one cursor 5,052 ms) and passed alone. Keep timing checks as
    ratios or opt-in.
16. **Semantics in tests**: the test API's unmerged `children` still includes nodes under
    `clearAndSetSemantics`; assert `config.isClearingSemantics` instead.

## 5. CI workflows

Both live in `.github/workflows/` with `permissions: contents: read` and a concurrency group per ref
(`cancel-in-progress: true`). Action versions, checked 8 September 2026: `actions/checkout@v5`,
`actions/setup-java@v5` (Temurin 17), `gradle/actions/setup-gradle@v5`, `actions/upload-artifact@v6`
(Node 24 releases replacing the deprecated v4s), `reactivecircus/android-emulator-runner@v2`.
`setup-gradle` stays at v5 deliberately: v6 moves its caching into a component under separate
licence terms, which is the owner's decision.

**Build** (`build.yml`; on push to `main`, pull requests, manual; ubuntu-latest; 45 min):

1. checkout, JDK 17, Gradle setup;
2. `./gradlew testDebugUnitTest`;
3. `node --test scripts/tests/addon-phone-page.test.cjs`;
4. `./gradlew lintDebug` (every module);
5. `./gradlew assembleDebugAndroidTest` (every module's instrumentation compiles);
6. `./gradlew assembleRelease` (unsigned: catches R8, resource and manifest breakage; not uploaded);
7. `./gradlew -PsohvaTestBuildType=lab :app:assembleLab :app:lintLab :app:assembleLabAndroidTest`;
8. upload test and lint reports (always, 14 days).

**Instrumentation** (`instrumentation.yml`; manual and nightly at 02:30 UTC; does not gate pull
requests because emulator runs are slow and flakier; 90 min per job):

- Job `connected` "Android TV emulator (API 30)": remove SDK-managed NDKs to free disk, enable KVM,
  emulator `api-level: 30`, `target: android-tv`, `arch: x86`, `profile: tv_1080p`,
  `disk-size: 2048M`, `disable-animations: true`, options
  `-no-window -gpu swiftshader_indirect -no-snapshot -no-metrics -noaudio -no-boot-anim -camera-back none`;
  the script turns off the disposable emulator's screensaver and screen timeout, then runs
  `./gradlew :core:connectedDebugAndroidTest :app:connectedDebugAndroidTest`; uploads reports.
- Job `addons` "Synthetic addon Lab (API 30)": same emulator, installs FFmpeg if missing, runs
  `pwsh scripts/Test-SohvaAddonsLab.ps1 -Serial <emulator> -Ffmpeg /usr/bin/ffmpeg`, uploads the
  group evidence.

A dispatched Instrumentation run shares the concurrency group with the nightly run and can be
cancelled by it; re-dispatch and read the result.

## 6. Release gates

In the order beta 23 passed them (`docs/SOHVA_TV_BETA_23.md`):

1. **Public source audit** (`scripts/Test-PublicSourceContent.ps1`, 747 files): allow-listed top-level
   entries; forbidden extensions (`.aab`, `.apk`, `.apks`, `.bak`, `.db`, `.jks`, `.keystore`, `.log`,
   `.m3u`, `.m3u8`, `.smbak`, `.sqlite`, `.xmltv`, `.zip`) and names (`keystore.properties`,
   `local.properties`, `secrets.properties`); build and internal paths; handoff and plan files;
   credential-shaped tokens, private keys, credential URLs (reserved `.example`/`.test`/`.invalid`
   hosts allowed in test source sets only), machine paths, private network addresses, personal e-mail.
2. **Release-document audit** (`scripts/Test-PublicReleaseContent.ps1`, 9 files): allowed names and
   extensions (`.dm` since beta 23), no sensitive names, authorisation headers, bearer values,
   credential URLs or parameters, private addresses or host names; every URL host and e-mail on an
   allow-list.
3. **Secret scans** with gitleaks: the release commit, the full public history (55 commits at beta
   23) and the packaged folder.
4. **The Build workflow's own tasks run locally** (776 unit tests, 9 phone-page tests, `lintDebug`
   for every module, every module's instrumentation compiled, `assembleRelease`, release lint, the Lab
   build with its lint and instrumentation). Lint in every module became a gate after the public
   Build had been red from beta 6 to beta 11.
5. **Release dex verification** (`scripts/Test-ReleaseDexVerifies.ps1 -Serial <emulator>`): builds the
   release APK, installs it, clears logcat, runs `cmd package compile -m verify -f com.streammate.tv`,
   requires `Success` and at least one `dex2oat` line, fails on any `failed to verify`, then runs the
   method-size gate and writes a receipt `app-release.apk.dex-verified` naming the APK's SHA-256
   (beta 23: 36 verification lines, no rejections).
6. **Method-size gate** (`scripts/Test-ReleaseMethodSizes.ps1`): `dexdump` of every `classes*.dex`,
   methods of `com.streammate`/`com.sohva` classes, fail above 95 % of ART's 10,000-code-unit
   ahead-of-time limit (beta 23: largest 8,949 of 6,222 methods).
7. **APK safety audit** (`scripts/Test-PublicApkSafety.ps1`, 381 entries): package
   `com.streammate.tv`, version code and name, label "Sohva TV", not debuggable; permissions on an
   allow-list; v2 signature by certificate
   `985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6`; no sensitive-looking entries;
   no home or checkout paths, private addresses, credential URLs, private keys, API-key or JWT shapes,
   local signing passwords or secret environment values in any entry; source-revision metadata only
   `$PROJECT_DIR` and a 40-hex commit.
8. **Launch smoke**: the exact signed APK cold-launches on the emulator (562 ms) and is alive eight
   seconds later.
9. **Packaging** (`scripts/package-sohva-beta.ps1 -Version <x.y.z-beta.n> -VersionCode <n>`):
   refuses an existing version folder; runs `lintDebug` for every module; refuses an APK without a
   matching dex receipt; checks badging (package, version, label, not debuggable) and signature; runs
   the APK audit; requires the nine tester documents, four of which must mention the version; runs
   the document audit; copies the APK and documents; copies and checks both install profiles (§8);
   writes `SHA256SUMS.txt`; audits the folder again; zips it and checks every ZIP entry against its
   file. It uploads, commits and publishes nothing.
10. **Full device suite on the same code** (preview 56: 284 run, none failed).
11. **Freeze**: the assets in `artifacts/buildNN/` with `assets.json` (sizes and digests); a frozen
    artifact is never overwritten; a changed build gets a new version code.
12. **Owner's go** (§9), push, hosted Build and Instrumentation green on the exact commit.
13. **Publish** (private `publish.ps1 -RunIds <build>,<instrumentation>`): rechecks both runs on the
    commit, creates a draft, uploads the five assets, checks their sizes and digests, publishes the
    prerelease, verifies the tag, downloads all five again, and checks that the anonymous update feed
    offers the new build with its checksums and both profiles to installed builds (beta 23: 29–56).
14. **Receipt**: `docs/SOHVA_TV_BETA_N.md`, the checklist and the ledger.

## 7. Versioning

- `versionName` is `0.1.0-beta.N`; the packaging and APK scripts accept only
  `^\d+\.\d+\.\d+-beta\.\d+$`. Private previews carry suffixes (`0.1.0-beta.22-guide.1`,
  `-memory.1`, `-perf.1`) and codes of their own.
- `versionCode` is strictly increasing across **every** distributed APK, betas and previews alike;
  a code is never reused, even for an unpublished frozen build (builds 47 and 50 stayed frozen when
  beta 22 shipped as 51). Beta 1 was code 2; beta 23 is 57; the Shield runs 57 since 23 September
  2026, installed through the in-app updater. **The next distributed build must be above 57.**
- Database versions are separate and recorded in each receipt (beta 23: main database 29, Discover
  progress database 4).
- Tags are `v<versionName>` on the exact released source commit; releases are GitHub prereleases.
- Rebuild: the first distributed rebuild APK needs a code above 57 (open question 2 proposes starting
  at 100 so the old line can still ship hotfixes in 58–99). The version name follows the same
  pattern; `0.2.0-beta.1` is one option (owner's decision).

## 8. Release assets and the release-body contract

**Five assets since beta 23** (before: APK, `SHA256SUMS.txt`, tester ZIP):

| Asset | Content |
|---|---|
| `sohva-tv-<version>.apk` | the signed, dex-verified release APK |
| `sohva-tv-<version>.api31.dm` | install-time profile for Android 12 and later (AGP `baselineProfiles/0/app-release.dm`); must carry the APK's `assets/dexopt/baseline.profm` |
| `sohva-tv-<version>.api28.dm` | install-time profile for Android 9–11 (`baselineProfiles/1/`); its `primary.prof` must equal the APK's `baseline.prof` |
| `SHA256SUMS.txt` | `<sha256>  <file name>` lines, lower-case hex, sorted by name, UTF-8 without BOM, naming the APK, both profiles and the nine documents |
| `sohva-tv-<version>-tester-pack.zip` | exactly the APK, both profiles, `SHA256SUMS.txt` and the nine documents: README.md, INSTALL.md, ADDONS.md, TESTING.md, RELEASE_NOTES.md, PRIVACY.md, THIRD_PARTY_NOTICES.md, LICENSE, LICENSE-APACHE-2.0.txt |

**Release-body contract** (read by every installed build from 29 on; details in
[specs/72](../specs/72-updates-about-diagnostics.md)):

- The body contains `Android build **N**`; the updater parses `[Bb]uild \*\*(\d+)\*\*`. A release
  without it is never offered.
- The updater reads the newest ten releases of the public repository, ignores drafts, takes the
  first `.apk` asset, and offers the highest stated build above the installed one.
- `SHA256SUMS.txt` must be an asset; without it nothing is downloaded. The APK's digest is checked.
- Builds 54 and later look for `<apk base name>.api31.dm` (API 31+) or `.api28.dm` (API 28–30), use it
  only when `SHA256SUMS.txt` names it with a matching digest, and install APK and profile in one
  `PackageInstaller` session; any failure falls back to installing the APK alone.
- About shows the section under a heading `#`–`###` starting with "Changed" (case-insensitive) up to
  the next `#`/`##` heading, or the whole body; bold marks, links and backticks are reduced to text.
- English and Finnish notes; prerelease; tag on the exact source.

After publishing, verify the live feed (a beta 13 body missed its build line and had to be
corrected; a valid tag and APK alone are not enough). Every future release keeps this contract.

## 9. Publication is the owner's decision

- Pushing to the public repository, tagging, creating a release and installing on any of the owner's
  devices each need the owner's explicit wording for that release, given in the conversation
  ("You may push beta 23 and publish the release" was what the automated permission check
  accepted; "Go ahead with beta 23" was not enough). Ask for that wording up front.
- The public repository (`Macstered/Sohva-TV`) receives one squashed commit per beta; private
  history, plans, handoffs, captures, signing material and user data never go there.
- A source-only fix (lint, tests) may go public between betas; unreleased features never do.
- The Build workflow must be green on the commit being published before the release is created, and
  Instrumentation is dispatched and read, not left to the nightly run.

## 10. The rebuild's quality plan

### 10.1 Test pyramid

| Layer | Runs on | Covers | Share and speed |
|---|---|---|---|
| Pure JVM unit tests | `:core:model`, `:core:net` parsers, `:core:sync` logic, screen models with fakes and test dispatchers | normalisation, catch-up URLs, rule precedence, parsers with provider quirks, import state machines, paging cursors, reducers, formatting | most tests; whole suite < 3 min in CI |
| JVM SQL tests (`sqlite-jdbc`) | exported schema | query plans of every hot query constant (with and without `ANALYZE`, two SQLite versions), keyset paging correctness, comparator equal to SQLite's `ORDER BY` | every hot query has one |
| Device database tests | emulator | migrations (every step; and from beta 23's schema 29 if [04](04-data-model.md) chooses in-place migration), DAOs on Android's SQLite, WAL, Keystore | |
| Device UI tests | Android TV emulator, debug build | each spec's §11: focus, Back, every key, overlays closing focus-first, data never moving focus, slow data through `QueryGate` | the fast suite (§10.3) plus the full suite |
| Black-box journeys | `:benchmark`, UiAutomator, minified `benchmarkRelease` | every screen reachable by D-pad on shrunk code; no R8 crash | a few minutes |
| Performance tests | emulator stand-in | §10.4 and [07](07-performance.md) | counters on every push, timings nightly |
| Manual checks | Shield (owner's go), testers | real content, real remotes, real GPUs | per milestone and release |

Contract tests with recorded, secret-free inputs: the update feed (release JSON with and without
profiles, missing build line, drafts), a synthetic beta 23 `.smbak` restoring into the rebuild,
provider quirks (BOM, gzip by magic bytes, declared encodings, truncated lists).

### 10.2 Seams and fixtures

The seams of [03-architecture.md](03-architecture.md) §4.15 (clock, dispatchers, HTTP, secrets,
database, `QueryGate`, playback engine, schedulers, feature flags, `ClearStateRule`). Fixtures:
one owner-scale generator shared by tests and the harness ([07](07-performance.md) §6.1), a small
fixture for UI tests, synthetic media from FFmpeg, all anchored to "now", all with reserved domains
and documentation addresses (`https://provider.example`, `192.0.2.x`). `QueryGate` and other test
seams exist only in debug and Lab builds.

### 10.3 A small, fast device suite

- A tagged subset (`@FastDeviceSuite`) of at most 150 tests that finishes in **10 minutes** on one
  hosted API 30 TV emulator: shell start and Back, focus return from each destination, guide open on
  a group with held-key paging through a gated query, Home first frame and rows, wall paging and
  return focus, player overlays and zapping on a fake engine, adding a source, profiles picker,
  reminder dialog, one migration.
- Runs on every pull request and push to `main`. The full suite runs nightly and before every
  release, sharded across emulators (`-e numShards`/`-e shardIndex`) to stay under 30 minutes.
- Rules: no `Thread.sleep`; every wait bounded and named; every test self-contained through the
  clear-state rule; a test that flakes twice is fixed or quarantined with an issue the same week.

### 10.4 Performance tests in CI

Deterministic counters gate every push (they do not depend on runner speed):

- release APK size, dex size and app method count against the baseline ([05](05-tech-stack-and-build.md) §4.8);
- the method-size gate and dex verification of the minified Lab build on the emulator;
- query-plan tests; zero `CursorWindow` "Window is full" lines in the logcat of the device suite;
- per-press recomposition counts: a guide press recomposes the hero and nothing in the grid, a wall
  press two cards, a Home press the focused card and (after rest) the hero; the player tree
  recomposes zero times over 60 s of playback with overlays hidden;
- semantics nodes: one per guide row;
- the opt-in memory tests run nightly with their bounds asserted (pairing heap < 16 MB, imports
  < 128 MB, walls ≤ 2 MB of entries).

Timings run nightly on the emulator stand-in and gate on gross regressions only: Macrobenchmark
start-up (cold, with the profile and with `CompilationMode.None`), frame timing and named trace
sections on the guide, wall and Home journeys, owner-scale import durations. Compare with the median
of the last five `main` runs; fail when a metric is more than 15 % worse on two consecutive nights.
Results go to the job summary and, at milestones, to `docs/performance-log.md`. Before merging a change
to a hot path, the author runs the local slow-box comparison ([07](07-performance.md) §6.2), as
[AGENTS.md](../../../AGENTS.md) §4 rule 11 requires.

### 10.5 The no-zero-duration-infinite-animation test

OwnTV shipped a crash when an infinite animation could be given a zero duration
([reference/owntv-study.md](../reference/owntv-study.md) item 18), and the rebuild's reduced-motion
mode sets tween durations to zero ([design/01](../design/01-design-system.md) §16.3), so the risk is
built in. Three checks:

1. **One door.** Infinite animations (buffering indicator, live-badge pulse, Discover loading title
   pulse, any future one) are declared only through the motion API of `:ui:design`; a custom lint
   check bans `infiniteRepeatable` and `rememberInfiniteTransition` elsewhere.
2. **JVM token test.** For every infinite motion token under every mode (normal, reduced motion,
   system animator scale 0, low-RAM tier), the resolved spec is either a positive duration or
   `Static` (the animation is not started and the element draws its end state, or steps on a timer
   as the reduced buffering arc does every 150 ms). A resolved zero duration that still repeats fails
   the build.
3. **Device test.** Each component that owns an infinite animation is composed in reduced mode and
   with a `MotionDurationScale` of 0; with the test clock's auto-advance off, 20 frames are advanced;
   the test fails on any exception or if the composition keeps requesting frames (a busy loop).

### 10.6 Static and release gates in CI

On every push: lint for every module and shipped variant, the custom lint checks and module
dependency check ([05](05-tech-stack-and-build.md) §4.11), `TranslationParityTest`-style string
completeness for all seven languages, gitleaks on the tree, the public source audit script, the
size and method gates. Before a release, locally: everything in §6, with the gitleaks history scan
and the signed APK. Both workflows stay identical in the private and public repositories.

### 10.7 Device matrix

| Device | Use |
|---|---|
| Android TV emulator API 30, x86, `tv_1080p` (hosted and local) | default UI tests, as today |
| Android TV or Google TV emulator API 33/34, x86_64, 1080p | profile generation, Macrobenchmark, Android 12+ behaviour (the Elisa box runs 12), the `.api31.dm` path |
| Android TV emulator API 28 | the `.api28.dm` path, oldest profile-capable release |
| Oldest TV image at `minSdk` | start and playback smoke |
| Slow-box stand-in (the API 30/31 emulator with the owner-scale fixture) | budgets of [07](07-performance.md) |
| Nvidia Shield (owner's) | manual acceptance of each milestone checkpoint, only with the owner's go for each action; instrumentation or the owner at the remote, never blind key events |
| Elisa box, Xiaomi-class sticks | testers' reports and Save diagnostics; never touched without the owner's go |

### 10.8 Release pipeline for the rebuild

Keep §6–§9 unchanged in substance, automated end to end: version bump and tester documents → CI
green (Build, fast suite, counters) → nightly full suite and timings green → local gates (dex
verification of the signed APK, method sizes, APK audit, source and document audits, gitleaks
history, packaging with profiles) → freeze with `assets.json` → the owner's explicit go → push →
hosted runs green on the exact commit → publish script → feed check with both profiles → receipt.
The five assets and the release-body contract are unchanged forever.

## 11. Lightweight by design

- Test code and seams never ship: `QueryGate`, fake engines and seeding hooks live in debug/Lab
  source sets; the Lab keep file covers only its seams.
- Performance is guarded by counters that cost nothing in the release (trace sections are free when
  no trace records; recomposition counters exist in tests only).
- Black-box journeys test the shrunk build without keep rules for tests.
- The profile the release ships is generated from the same journeys the tests run, so test coverage
  and compiled coverage grow together.

## 12. Lessons from the current app

| What happened | Rule |
|---|---|
| The public Build failed on lint from beta 6 to beta 11; nothing local ran lint; nobody read the red runs | Run CI's own tasks locally before packaging; packaging runs lint; publish only on a green Build |
| The release died with a `VerifyError` on opening a stream while every debug test was green (8 Sept 2026) | Verify the release dex on an emulator; package only the APK the verifier accepted (receipt by SHA-256) |
| Build 50 was frozen, then hosted compilation found a core device test still calling a removed method; local compilation had covered only app instrumentation | Compile every module's instrumentation (`assembleDebugAndroidTest`) |
| A Maven Central HTTP 429 failed the first hosted build of beta 23 | Re-run the failed job before suspecting the code |
| A nightly run cancelled the dispatched one and a flaky untouched test failed | Re-dispatch; fix or quarantine flaky tests quickly |
| Three fixes shipped unverified because tests could not show real content; tests passed on broken code | Synthetic harnesses at real size; prove the test fails first |
| A beta 13 release body lacked `Android build **14**`; the Build of beta 12 finished after the release was created | Verify the live feed after publishing; Build green before creating the release |
| The first beta 23 publication stopped: Windows PowerShell 5.1 passed `ConvertFrom-Json`'s array as one element | Enumerate JSON arrays explicitly in release scripts; dry-run the guard |
| Publication was refused until the owner used explicit wording | Ask for "push and publish" wording up front |
| Blind key events on the owner's TV opened another app; a wrapper left the TV never sleeping | Instrumentation only; never change device settings; ask before using a device someone may watch |

## 13. Open questions

1. The hosted run reported "app 295 run with 10 skipped" while the tree has 285 `@Test` methods in
   `app/src/androidTest` and the private run reported 284; which runner counting explains it?
2. The rebuild's first version code (58, or 100 to leave room for old-line hotfixes) and version
   name (`0.2.0-beta.1` or continuing `0.1.0-beta.24`).
3. Screenshot tests for the component gallery in seven themes (roadmap M0): a JVM tool (Compose
   preview screenshot testing, if stable at M0) or emulator `captureToImage` goldens with a
   tolerance?
4. Can hosted runners run the fast device suite on every pull request within an acceptable time, or
   should pull requests gate on counters and JVM tests only?
5. Should gitleaks run in the public CI (the CLI pinned by version and checksum) or stay a local gate?
6. Should `SHA256SUMS.txt` also list the tester ZIP (it is created after the checksum file today)?

## 14. Reference: current code map

- `.github/workflows/build.yml`, `.github/workflows/instrumentation.yml` — CI.
- `scripts/test-android-tv-emulator.ps1`, `scripts/Invoke-ConnectedTest.ps1` — local device runs.
- `scripts/Test-ReleaseDexVerifies.ps1`, `scripts/Test-ReleaseMethodSizes.ps1` — dex and method-size gates.
- `scripts/Test-PublicApkSafety.ps1`, `scripts/Test-PublicReleaseContent.ps1`, `scripts/Test-PublicSourceContent.ps1` — audits.
- `scripts/package-sohva-beta.ps1` — packaging, profiles, checksums, tester ZIP.
- `scripts/Test-SohvaAddonsLab.ps1`, `scripts/Install-SohvaLab.ps1`, `scripts/Generate-AddonPlaybackFixtures.ps1`, `scripts/fixtures/*.srt` — Lab harness.
- `scripts/start-demo-screenshot-mode.ps1`, `docs/demo-screenshot-mode.md` — demo build.
- `scripts/tests/addon-phone-page.test.cjs` — Node tests of the phone page script.
- `app/src/androidTest/java/com/streammate/tv/testing/ClearAppStateRule.kt`, `ComposeWaits.kt` — device test infrastructure.
- `core/src/test/java/com/streammate/tv/core/database/CatalogueHomeQueryPlanTest.kt`, `GuideChannelsQueryPlanTest.kt` — plan tests.
- `app/src/androidTest/java/com/streammate/tv/feature/guide/GuideNavigationTraceTest.kt`, `GuideHeldKeyPagingTest.kt` — trace test, gated held-key test.
- `app/src/androidTestLab/java/com/streammate/tv/lab/LabBenchmarkLibrarySeed.kt` — owner-scale seed.
- `app/src/androidTestRelease/java/com/streammate/tv/release/ReleaseUpgradeTest.kt` — upgrade suite.
- `app/src/main/java/com/streammate/tv/app/AppUpdates.kt` — the release-body contract as code.
- Private: `docs/PUBLIC_RELEASE_CHECKLIST.md`, `docs/PUBLIC_RELEASE_REPOSITORY.md`, `docs/SOHVA_TV_BETA_21..23.md`, `.local/beta23-code57-release/publish.ps1`.
