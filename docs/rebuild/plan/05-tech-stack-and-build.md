# Tech stack and build

> The libraries, toolchain, build variants, shrinking, profiles and signing of Sohva TV
> `0.1.0-beta.23` (build 57), and the build the rebuild should use instead. Read with
> [03-architecture.md](03-architecture.md) (modules and dependency rules),
> [06-quality-testing-release.md](06-quality-testing-release.md) (gates and releases) and
> [07-performance.md](07-performance.md) (budgets the build must help meet).

## 1. What this file decides

- Every library and tool the current app uses, its version and why it is there (§2).
- How beta 23 is built: variants, `BuildConfig`, signing, R8, profiles, resources, lint (§3).
- The rebuild's build: the minimum dependency set, what is dropped, R8 and profiles from the first
  release, the APK budget and the version policy (§4).

Everything in §2–§3 is copied from the beta 23 tree
(`G:\SportMate\.local\sohva-sport-user-reports\.local\beta19-public-source`, commit `efab52a`).
§4 is a recommendation; decisions it leaves open are in §7.

## 2. The current stack (beta 23)

### 2.1 Toolchain

| Item | Value | Where |
|---|---|---|
| Gradle | 8.14.3 (`-all` distribution, `distributionSha256Sum` pinned, `validateDistributionUrl=true`) | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.13.2 | `gradle/libs.versions.toml` |
| Kotlin (android + compose compiler plugins) | 2.3.21 | same |
| KSP | 2.3.10 (Room compiler only) | same |
| JDK | 17 (Temurin in CI); `sourceCompatibility`/`targetCompatibility` 17, Kotlin `jvmTarget` 17 in every module | module build files, `.github/workflows/*.yml` |
| Android SDK | platform 36; Build Tools 35.0.0 or newer (README) | README.md |
| `gradle.properties` | `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`, `org.gradle.configuration-cache=true`, `org.gradle.parallel=true`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official` | root |
| Repositories | `google()`, `mavenCentral()` (+ `gradlePluginPortal()` for plugins); `RepositoriesMode.FAIL_ON_PROJECT_REPOS` | `settings.gradle.kts` |
| Editor config | UTF-8, LF, 4 spaces (2 for yml/yaml/json/xml), final newline, trim trailing whitespace except `.md` | `.editorconfig` |

Tools outside Gradle that the gates and harnesses use: Node.js (`node --test` for the phone-page
script), FFmpeg (synthetic playback media for the Lab suite), PowerShell (Windows PowerShell 5.1
locally, `pwsh` in CI), the SDK's `aapt`, `apksigner`, `dexdump` and `adb`, gitleaks, the GitHub
`gh` CLI, and for the private slow-box harness Python with Pillow and Perfetto's
`trace_processor_shell` (v58.2).

### 2.2 SDK levels

`minSdk = 23`, `targetSdk = 36`, `compileSdk = 36` in every module (library modules set
`testOptions.targetSdk = 36`). Core library desugaring is on in every module
(`isCoreLibraryDesugaringEnabled = true`), for `java.time` below API 26.

### 2.3 Runtime libraries

| Catalog key | Version | Declared in | Used for | Notes |
|---|---|---|---|---|
| `androidx-compose-bom` | 2026.06.00 | app, core, iptv, sportmate | Compose UI, foundation, tooling-preview (tooling and ui-test-manifest in `debugImplementation`) | The ledger names Compose UI 1.11.3 at this BOM; strong skipping is the Kotlin 2.x default |
| `androidx-tv-material` | 1.1.0 | app, core, iptv, sportmate | TV components and the inherited text style | Its default text style (0.5 sp tracking, 24 sp lines) silently shapes the look ([design/01](../design/01-design-system.md) §17) |
| `androidx-activity-compose` | 1.13.0 | app, iptv, sportmate | `setContent`, back dispatch | |
| `androidx-core-ktx` | 1.18.0 | app | `FileProvider` for update files, compat helpers | |
| `androidx-lifecycle-runtime-compose` | 2.10.0 | app, iptv, sportmate | lifecycle-aware collection | |
| `androidx-lifecycle-viewmodel-compose` | 2.10.0 | app, sportmate | the one `ViewModel` (`TodayViewModel`) | |
| Media3 `exoplayer`, `exoplayer-hls`, `exoplayer-dash`, `datasource-okhttp`, `session`, `ui` | 1.11.0 | app, iptv | playback of HLS, DASH, progressive/TS; OkHttp data source; `MediaSessionService`; `PlayerView`, `SubtitleView`, `AspectRatioFrameLayout`, `CaptionStyleCompat` | `ui` is used for `PlayerView` (3 uses) and the subtitle view |
| Room `runtime`, `ktx`, `compiler` (KSP) | 2.8.4 | app (runtime), core (main database, schemas exported to `core/schemas`), addons (three databases, `addons/schemas`) | database, DAOs, migrations | Runs in compat mode on the framework `SQLiteCursor` (no driver set), so large results hit the CursorWindow refill trap ([07](07-performance.md) §7) |
| `androidx-datastore-preferences` | 1.2.1 | app, core | preferences | Ships `libdatastore_shared_counter.so` for four ABIs |
| `androidx-work-runtime` | 2.11.2 | app | periodic playlist, guide and catalogue refresh; metadata worker | |
| `okhttp` | 5.3.0 | every module | provider, TMDB/TVmaze, API-Sports, addon, Trakt, update-feed requests; Media3 and Coil network | Provider client: connect 20 s, read 90 s, `Protocol.HTTP_1_1` for one client; brings `PublicSuffixDatabase.list` (42 KB compressed in the APK) |
| `kotlinx-serialization-json` | 1.11.0 | app, iptv, sportmate, addons, trakt (core: tests only) | JSON **tree** parsing (`Json`, `JsonObject`, `jsonPrimitive` …) | No `@Serializable` class and no serialization plugin anywhere; whole responses become trees (the cost [specs/50](../specs/50-discover-addons.md) §9 describes) |
| `kotlinx-coroutines-core` | 1.10.2 | addons, trakt (others transitively) | coroutines | |
| `coil-compose`, `coil-network-okhttp` | 3.4.0 | app, iptv, sportmate | image loading | Singleton built in `StreamMateApplication`: disk cache `catalogue_artwork` (viewer's 100/250/500 MB), memory cache 8 % (`ARTWORK_MEMORY_CACHE_FRACTION = 0.08`), `bitmapFactoryMaxParallelism(2)`, crossfade 140 ms |
| `coil-svg` | 3.4.0 | app, iptv | only `file:///android_asset/tmdb_attribution.svg` (legal screen, library settings) | Brings AndroidSVG |
| `zxing-core` | 3.5.3 | app | QR codes: phone setup, addon phone page, Stremio import, Trakt device sign-in | Encoder only is used |
| `desugar-jdk-libs` | 2.1.5 | every module | `java.time` on API 23–25 | |

Notable transitive components (from `THIRD_PARTY_NOTICES.md`): Guava and JSpecify, Accompanist,
AndroidSVG, AndroidX ProfileInstaller (pulled by Compose; it is what installs the baseline profile
on first launch), `androidx.graphics.path` native library (four ABIs, about 10 KB each).

`kxml2` 2.3.0 is a **test** dependency of `iptv` only since beta 23: the XMLTV parser uses the
platform's `Xml.newPullParser()`; the bundled kXML2 copy of the `XmlPullParser` interfaces clashed
with the platform's under R8 and had always been shadowed at run time anyway.

### 2.4 Test libraries

| Catalog key | Version | Used in |
|---|---|---|
| `junit` | 4.13.2 | all JVM and device tests |
| `kotlinx-coroutines-test` | 1.10.2 | JVM tests |
| `androidx-room-testing` | 2.8.4 | migration and DAO tests (JVM and device) |
| `androidx-test-ext-junit` | 1.3.0 | device tests |
| `androidx-test-runner` | 1.7.0 | device tests (runner `androidx.test.runner.AndroidJUnitRunner`) |
| `androidx-test-espresso-core` | 3.7.0 | device tests |
| `androidx-compose-ui-test-junit4` | BOM | Compose UI tests |
| `okhttp-mockwebserver` | 5.3.0 | JVM tests (iptv, addons, trakt), app device tests |
| `sqlite-jdbc` | 3.41.2.2 | core JVM query-plan tests on the exported schema |
| `kxml2` | 2.3.0 | iptv JVM XMLTV parser tests (the platform parser is not on the JVM) |

## 3. The current build

### 3.1 Modules and plugins

`settings.gradle.kts` includes `:app`, `:core`, `:iptv`, `:sportmate`, `:addons`, `:trakt`
(root project name `SohvaTV`). All modules apply `com.android.*` + `org.jetbrains.kotlin.android`;
`app`, `core`, `iptv`, `sportmate` also the Compose compiler plugin; `app`, `core`, `addons` also KSP.
Only `app` has `buildConfig = true`. Every module excludes `/META-INF/{AL2.0,LGPL2.1}` from
packaging (the three pure libraries do not package). `addons` orders `kspReleaseKotlin` after
`kspDebugKotlin` because both write the same exported schema and a combined build once read a
half-written file.

### 3.2 Build types (no product flavours)

| Build type | Application id | Version name suffix | Signing | R8 | Purpose |
|---|---|---|---|---|---|
| `debug` | `com.streammate.tv.debug` | `-debug` | debug key | off | development; every ordinary unit and device test runs against it |
| `demo` | `com.streammate.tv.demo` | `-demo` | debug key (`initWith(debug)`, `matchingFallbacks += debug`) | off | screenshots and demos: label "Sohva TV Demo" (`app_name_demo`), fictional content from `StreamMateDemoContentProvider` registered by manifest meta-data `com.streammate.tv.DEMO_CONTENT_PROVIDER`; generated artwork in `app/src/demo/res`; started by `scripts/start-demo-screenshot-mode.ps1` |
| `release` | `com.streammate.tv` | – | production key when `.local/streammate-signing/keystore.properties` exists, otherwise **unsigned** (CI) | on since beta 23 | the published APK |
| `lab` | `com.streammate.tv.lab` | `-lab` | debug key ("persistent local development identity") | **off** unless `-PsohvaLabMinify=true` | release code (`initWith(release)`, `isDebuggable = false`) as a separately installable package for the synthetic addon suite and the slow-box captures; label "Sohva TV Lab" (`lab_app_name`), own icon and banner; its manifest removes `REQUEST_INSTALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED` and the update `FileProvider`, and disables `ReminderReceiver`; at run time `AppRuntimePolicy` turns off automatic maintenance, automatic sport refresh and reminders for this package |

`AppRuntimePolicy.forPackage` also allows public updates only for `com.streammate.tv` and Discover
for `com.streammate.tv`, `.debug` and `.lab` (not demo). Lab stays unshrunk because its suites reach
into app classes that R8 would remove or inline.

### 3.3 Test build type switch and source sets

- `-PsohvaTestBuildType=debug|lab|release` (default `debug`) selects `testBuildType`; `lab` moves the
  `androidTest` root to `src/androidTestLab`, `release` to `src/androidTestRelease` (opt-in,
  disposable emulator only, needs the local signing identity).
- `-PsohvaPlaybackFixtures=true` (debug) or the Lab build adds
  `build/generated/addonPlaybackFixtures` (FFmpeg-generated media) to the test assets.
- `testInstrumentationRunnerArguments["timeout_msec"] = "300000"`: AGP's default is a year, so one
  hung test used to stall the whole suite.
- `core` and `addons` add their `schemas/` folder to `androidTest` assets for migration tests.
- `afterEvaluate` adds `-keep class j$.** { *; }` to the **test** APK's L8 task when the test build
  type is `lab` or `release`: AGP 8.13.2 trims the non-debuggable test APK's desugared library
  independently, and its partial `j$.time` classes shadowed the app's and crashed
  `Application.onCreate` before any test ran.
- `addons` passes two opt-in system properties to JVM tests: `sohva.addon.liveInput` (a local file
  path, never a URL) and `sohva.stremio.linkProbe`.

### 3.4 `BuildConfig` and Trakt credentials

`app` defines `BuildConfig.TRAKT_CLIENT_ID` and `BuildConfig.TRAKT_CLIENT_SECRET` from
`.local/trakt/trakt-credentials.properties` (git-ignored with the whole `.local/` tree). Each value
must consist of printable ASCII 33–126 without `"` or `\`, or the build fails. Without the file both
are empty strings, the build still compiles, and Settings › Accounts says Trakt is not configured.
CI and public clones therefore build a Trakt-less app; only the owner's local release build carries
the credentials. The redirect URI is fixed for device pairing. No value is ever written to this kit.

### 3.5 Signing and identity

- `signingConfigs.release` reads `storeFile`, `storePassword`, `keyAlias`, `keyPassword` from
  `.local/streammate-signing/keystore.properties` (ignored); each must be non-blank. When the file is
  absent, `release` has no signing config and produces an unsigned APK (enough for CI to catch R8,
  resource and manifest breakage); assigning an empty config would fail `validateSigningRelease`.
- Certificate SHA-256 (public): `985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6`,
  APK Signature Scheme v2. Packaging and the APK audit refuse any other certificate.
- **Never regenerate or rotate this key.** Every existing install updates only from APKs signed by
  it; a new key means uninstalling and losing local data. The keystore has an off-device backup
  (release checklist).
- `dependenciesInfo { includeInApk = false }`: the Play dependency block is encrypted differently on
  every build, so leaving it out keeps sideloaded APKs byte-reproducible.

### 3.6 R8 and keep rules

Release: `isMinifyEnabled = true` with `proguard-android-optimize.txt` and `app/proguard-rules.pro`,
which holds only:

```
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
```

Rationale in the file: the project is public, renaming protects nothing and would make testers'
diagnostics, stack traces and system traces unreadable; line numbers are kept, and because R8 still
merges classes the release's mapping file is frozen with each build for retracing. The app reads
JSON by hand and uses no reflection, so no app keep rules are needed; libraries bring their own.

Before beta 23 the release was never minified (`isMinifyEnabled = false` since the first commit):
39.2 MB of dex in six files and Compose's source-information paths shipped. R8 brought it to
8.8 MB in two files (`classes.dex` 8,270,440 bytes, `classes2.dex` 577,052 bytes) and the APK from
about 18.0 MB (beta 22: 18,016,927 bytes) to 7.8 MB (beta 23: 7,841,731 bytes).

`isShrinkResources` is not set, so unused resources are not removed; the packaged APK shows
shortened resource paths (`res/Qv.png`).

### 3.7 Baseline profile and install-time profiles

- `app/src/main/baseline-prof.txt` is **hand-written**: 45 wildcard rules (26 until preview 54 added
  19 for start-up, Home, the guide, the player, shared components and the guide's repository and
  DAO). Beta 22 had no `feature/guide` rule at all. There is no Macrobenchmark or profile-generator
  module.
- AGP compiles it (merged with the libraries' profiles) into `assets/dexopt/baseline.prof`
  (7,779 bytes) and `baseline.profm` (886 bytes), which ProfileInstaller copies on first launch.
  Parsing the compiled profile showed 42,177 → 47,360 hot methods after the 19 rules were added.
- AGP also writes install-time profiles: `app/build/outputs/apk/release/baselineProfiles/0/app-release.dm`
  (Android 12 and later) and `.../1/app-release.dm` (Android 9–11, older format). Packaging copies
  them as `sohva-tv-<version>.api31.dm` and `.api28.dm` after checking that both carry the APK's
  `assets/dexopt/baseline.profm` and that the older one's `primary.prof` equals the APK's
  `baseline.prof` byte for byte. Beta 23's are 8,954 and 8,931 bytes.
- Since build 54 the in-app updater installs through a `PackageInstaller` session holding
  `base.apk` + `base.dm` (Android 9+), which gives `reason=install-dm`: the update is compiled while
  it installs. `ACTION_VIEW` (the older path, and the fallback) cannot carry a profile, and a first
  sideload cannot either (except `adb install-multiple`). Proven on the Shield on 23 September 2026.

### 3.8 Manifests

Main manifest permissions: `INTERNET`, `REQUEST_INSTALL_PACKAGES`, `POST_NOTIFICATIONS`,
`RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`; `android.software.leanback` required, touchscreen not required;
`<profileable android:shell="true"/>` so the shell profiler (Perfetto, simpleperf) can inspect the
real release build without making it debuggable. Library manifests are empty. The APK audit's
permission allow-list additionally accepts `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and
`com.streammate.tv.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which libraries merge in.

### 3.9 Lint

`lint { abortOnError = true; checkReleaseBuilds = true }` in `app`, `lint.abortOnError = true` in
every library. No lint baseline file and no custom checks. CI runs `lintDebug` (every module) and
`:app:lintLab`; packaging runs `lintDebug` again and refuses to package on an error. No Kotlin
`allWarningsAsErrors`; the documented local command adds `--warning-mode=fail` for Gradle warnings.

### 3.10 What the beta 23 APK contains

381 entries, 7,841,731 bytes:

| Part | Compressed | Uncompressed |
|---|---|---|
| `classes*.dex` (2 files) | 4,101,116 | 8,847,492 |
| `res/` | 1,792,318 | 1,913,781 (largest PNGs 336, 213, 208, 135, 104 KB) |
| `resources.arsc` (stored) | 1,643,048 | 1,643,048 |
| `META-INF/` | 59,450 | 147,935 |
| `assets/PublicSuffixDatabase.list` | 42,484 | 132,737 |
| `lib/` (2 small libraries × 4 ABIs) | 73,584 | 73,584 |
| `assets/dexopt/` | 8,665 | 8,665 |
| rest (manifest, Kotlin metadata, `tmdb_attribution.svg`, `theme-licenses.txt`) | < 20,000 | |

The largest app method after R8 was 8,949 code units (of 6,222 app methods), under the 9,500 gate.

## 4. The recommended lightweight build for the rebuild

### 4.1 Principles

1. **Every dependency pays rent.** A library is added only when writing the same thing would be
   larger, slower or riskier, and its R8-shrunk cost is recorded in `docs/decisions.md`.
2. **The build proves the lightweight rules.** R8, resource shrinking, profiles, the method-size
   gate, the APK-size gate and dex verification exist from the first commit (M0), not as a late
   optimisation ([AGENTS.md](../../../AGENTS.md) §4 rule 10).
3. **Release-like code is what gets measured.** Performance captures and profile generation run on
   minified, debug-signed variants, never on the debug build.
4. **Identity never changes**: application id `com.streammate.tv`, the existing key, codes above 57.

### 4.2 Toolchain and SDK levels

- Start from the versions in §2 and move each to its latest **stable** release at M0; record the
  chosen set in `gradle/libs.versions.toml` with a date. Keep Kotlin, the Compose compiler plugin
  (same version as Kotlin) and KSP aligned; use the Compose BOM.
- JDK 17 toolchain (`kotlin { jvmToolchain(17) }`), Java/Kotlin target 17.
- `minSdk 23` unless the owner raises it ([00-product-overview.md](00-product-overview.md) §8).
  What minSdk decides here: below 26 `java.time` needs desugaring (keep `desugar_jdk_libs`); below
  24 there is no profile-guided compilation, so API 23 devices always run the JIT; install-time
  profile delivery works from API 28. `targetSdk 36`, `compileSdk 36` (or the current platform at M0).
- `gradle.properties`: keep configuration cache, parallel and non-transitive R classes; size `-Xmx`
  for the CI runner (the old packaging script ran lint locally with
  `-Xmx4096m --max-workers=2 --no-parallel`).

### 4.3 Minimum dependency set

**Runtime (ships in the APK)**

| Need | Library | Decision and why |
|---|---|---|
| Language | Kotlin stdlib, `kotlinx-coroutines-android` | keep |
| UI | Compose `runtime`, `ui`, `foundation` via the BOM; `ui-tooling-preview` in `:ui:design` only | keep |
| TV components | `androidx.tv:tv-material` | **keep only if used.** Decide in M0 with the component gallery: if every shared component is built on `foundation` (one shared focusable primitive, [design/01](../design/01-design-system.md) §17), drop it and set the text style deliberately |
| Activity, lifecycle | `activity-compose`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `core-ktx` | keep (entry-scoped screen models, [03](03-architecture.md) §4.6) |
| Navigation | hand-written stack or AndroidX Navigation 3 | decided in M0 ([03](03-architecture.md) §4.5): Navigation 3 only if it adds < 200 KB after R8 and passes the focus tests |
| Route descriptors | `kotlin-parcelize` plugin (no runtime library) for a hand-written stack; `kotlinx-serialization-core` + plugin only if Navigation 3 needs serializable keys | never the JSON runtime for routes |
| Playback | Media3 `exoplayer`, `exoplayer-hls`, `exoplayer-dash`, `datasource-okhttp`, `session`, `ui` | keep; use `ui` for `SubtitleView`/`AspectRatioFrameLayout`/`CaptionStyleCompat` only, not `PlayerView` (no controller inflation, and with resource shrinking its layouts and translations drop out) |
| Database | Room `runtime` + `compiler` (KSP), schema export on | keep for schema export, migrations and migration tests; hot paged reads must be cancellable (verify that the pinned Room passes a `CancellationSignal` for suspend queries, otherwise run them through `SupportSQLiteDatabase.query(query, signal)` behind a small helper) |
| Preferences | `datastore-preferences` | keep; large per-profile data stays in the database |
| Scheduling | `work-runtime` | keep, initialised on demand ([03](03-architecture.md) §4.8) |
| HTTP | OkHttp | keep: one base client, provider client HTTP/1.1, image/API client HTTP/2 (OwnTV item 22) |
| Images | Coil 3 `coil-compose` + `coil-network-okhttp` | keep: sized requests, `RGB_565`, memory cache from `memoryClass`, 2 decoders |
| QR codes | ZXing `core` | keep (encoder only; R8 strips the rest); build bitmaps at module resolution off the main thread |
| JSON | **Moshi's streaming `JsonReader`/`JsonWriter` only** (no adapters, no reflection, no codegen; reads straight from the OkHttp `BufferedSource` through a byte-counting cap) | replaces `kotlinx-serialization-json`; runs in JVM unit tests, which `android.util.JsonReader` does not (platform stubs). If the owner prefers zero added libraries, `android.util.JsonReader` is the fallback and parser tests move to the device |
| XML | platform `XmlPullParser` (`Xml.newPullParser()`) | no library; `kxml2` as a JVM test dependency only |
| Profiles | `androidx.profileinstaller` | explicit dependency (today transitive) so its version is pinned |
| `java.time` below API 26 | `desugar_jdk_libs` | keep while minSdk < 26 |

**Build and test only (never in the APK)**: `androidx.baselineprofile` Gradle plugin;
`androidx.benchmark:benchmark-macro-junit4` and `androidx.test.uiautomator` in `:benchmark`;
Compose `runtime-tracing` in the `lab`/benchmark variants only (named composables in Perfetto; the
old build had none, [07](07-performance.md) §6.3); JUnit 4, `kotlinx-coroutines-test`, Room testing,
AndroidX test runner/ext/Espresso, Compose `ui-test-junit4`, OkHttp MockWebServer, `sqlite-jdbc`
(two versions, §4.11), `kxml2`.

### 4.4 What to drop, and why

| Dropped | Why | Replacement |
|---|---|---|
| `kotlinx-serialization-json` | Tree parsing materialises whole responses (a 2 MiB addon catalog cost tens of MB of garbage; Xtream live lists and API-Sports feeds were parsed whole) | streaming pull reader (§4.3) |
| `coil-svg` (+ AndroidSVG) | One asset: the TMDB attribution logo | a VectorDrawable converted from the unmodified SVG (open question 3 confirms TMDB's terms allow the format change) |
| `PlayerView` usage | Inflates an unused controller; its resources stay in the APK | bare `SurfaceView` + `SubtitleView` ([specs/30](../specs/30-player.md) §9) |
| `kxml2` at run time | Already gone in beta 23; duplicated platform classes | platform parser |
| Hand-written `baseline-prof.txt` | Missed the guide for weeks; stale silently | generated profiles (§4.9) |
| Reflection-based demo provider (manifest meta-data read through the PackageManager at every start) | Start-up cost, R8 keep risk | the `demo` source set supplies an implementation to the graph ([03](03-architecture.md) §4.4) |

Not added, deliberately: Hilt/Dagger/Koin (manual lazy graphs), Navigation-Compose, Paging 3 (the
specs define keyset window pagers with absolute indexes; Room's paging integration pages with
`LIMIT/OFFSET`, which [AGENTS.md](../../../AGENTS.md) §4 rule 2 forbids), Retrofit, Gson,
`kotlinx-collections-immutable` (declare read-only collections stable in the Compose stability file
instead, §4.10), Timber (the redacted `DiagnosticsLog`), any analytics, crash or ads SDK (non-goal),
libmpv/FFmpeg, bundled fonts, Lottie ([reference/owntv-study.md](../reference/owntv-study.md) §3).

### 4.5 Build types and variants

Keep the four build types and their suffixes, labels and purposes (§3.2), with three changes:

1. **Lab is minified by default** (same R8 configuration as release, debug-signed,
   `isDebuggable = false`). Lab instrumentation talks to the app through the test seams of
   [03](03-architecture.md) §4.15 (exposed by a Lab-only source set with its own small keep file),
   not through internal classes, so captures measure release-equivalent code without the
   `-PsohvaLabMinify` switch.
2. **Demo** keeps its fictional content but gets it from a `demo` source-set implementation bound in
   the graph; no reflection, no PackageManager read at start.
3. The `androidx.baselineprofile` plugin adds its own `benchmarkRelease` and `nonMinifiedRelease`
   variants (minified/unminified release code, debug-signed, profileable). They are generated, never
   distributed, and exist only for `:benchmark`.

Keep the manifest overlays: Lab removes the installer permission, boot receiver and update provider
and disables reminders; demo registers nothing but its label. Keep `<profileable android:shell="true"/>`
in the main manifest.

### 4.6 R8 from the first release

- `release`, `lab` and `benchmarkRelease`: `isMinifyEnabled = true`, `isShrinkResources = true`,
  `proguard-android-optimize.txt` + `proguard-rules.pro` containing `-dontobfuscate` and
  `-keepattributes SourceFile,LineNumberTable` and nothing else by default.
- Any further rule needs a comment naming the failure it prevents and the test that fails without it.
  No reflection in app code; no `@Keep` on app classes (Lab's test-seam keep file is the exception).
- Keep `mapping.txt` with every frozen build (R8 class merging moves frames between classes).
- The release dex is verified on an emulator (`cmd package compile -m verify -f`) and the
  method-size gate runs on it before every release ([06](06-quality-testing-release.md) §6); CI also
  runs both on every push to main from M0.
- Minified code is exercised by black-box UiAutomator journeys from `:benchmark` (they need no app
  classes), which is what the old `smoke.sh` pass did by hand: every screen visited, logcat checked
  for `ClassNotFoundException`, `NoSuchMethodError`, `NoSuchFieldError`, `VerifyError`,
  `AbstractMethodError`, `IncompatibleClassChangeError`.

### 4.7 Resources, locales, ABIs

- Resource shrinking on (above); `vectorDrawables.useSupportLibrary` only if a vector needs it.
- Package only the seven interface languages (`en`, `fi`, `es`, `pt`, `de`, `sv`, `it`, matching
  `locales_config.xml`) with AGP's locale filter (`resourceConfigurations`, or its successor
  `androidResources.localeFilters` in the AGP in use). Libraries such as Media3 UI carry many more
  translations; beta 23's `resources.arsc` is 1.6 MB uncompressed.
- Ship bitmaps as WebP (lossy for photographs, lossless for flat art) at the sizes they are drawn
  ([design/04](../design/04-icons-and-imagery.md)); beta 23's largest PNGs are 100–336 KB each.
- Do not filter ABIs: the native libraries are about 74 KB for all four ABIs, and the x86/x86_64 ones
  are needed by the emulator for the benchmark and Lab variants. (A 2 GB box may run a 32-bit
  userland; `armeabi-v7a` must stay.)

### 4.8 APK budget and size gate

| Milestone | Release APK (R8, resources shrunk) |
|---|---|
| M0 skeleton | ≤ 4 MB ([02-roadmap.md](02-roadmap.md)) |
| Parity target (M11) | ≤ 8 MB (beta 23: 7.8 MB, with resources not shrunk) |
| Hard ceiling, any build | 10 MB ([_spec-conventions.md](_spec-conventions.md)) |

CI builds the release APK on every push and records its size, the dex size and the app method count
in the job summary. The build fails when the APK exceeds 10 MB or grows more than 200 KB over the
committed baseline (`build/size-baseline.txt`, updated only together with a `docs/decisions.md`
entry that names the cause). `apkanalyzer` or a ZIP listing is enough; no plugin is needed.

### 4.9 Baseline and startup profiles, generated

- **Module**: `:benchmark` (`com.android.test`, targets `:app`), with the `androidx.baselineprofile`
  plugin on `:app` and `:benchmark`. `automaticGenerationDuringBuild = false`: profiles are generated
  on purpose, reviewed and committed (the plugin writes `baseline-prof.txt` and `startup-prof.txt`
  under the app's `generated/baselineProfiles` source folder).
- **Device**: an Android TV (or Google TV) emulator image at **API 33 or later**, x86_64, 1920×1080,
  cold-booted (`-no-snapshot-load`, or the guide's clock is wrong). Non-rooted profile collection
  needs API 33+, and the TV images are user builds.
- **Content first**: before collecting, the benchmark variant is seeded with the owner-scale synthetic
  catalogue served from the host's loopback interface
  ([07-performance.md](07-performance.md) §6.1), through the app's own importers, by a Lab/benchmark-
  only seeding step. OwnTV's first profile recorded its setup wizard because its emulator had no
  catalogue ([reference/owntv-study.md](../reference/owntv-study.md) item 21).
- **Journeys, D-pad only** (UiAutomator key presses; waits on `testTag`s exposed with
  `testTagsAsResourceId`, set in the root):
  1. *Start-up* (`includeInStartupProfile = true`): cold start → Home's first content frame → one
     Right along the first row.
  2. *Browse*: Home rows down and along; the rail; Live TV → the opening group → 40 rows down, along a
     row, a time page forward and back, the options sheet, Back; Movies → a 40,000-title group → 10
     rows down → details → Back; Series → a series → season → episode list → Back; Search with a
     typed term; Settings sections; a channel played (synthetic stream) → overlays → channel up/down →
     Back; Discover with a synthetic addon; Sohva Sport with a synthetic feed.
- **Output**: AGP merges the profiles into `assets/dexopt/baseline.prof(m)`, uses the startup profile
  for R8's dex layout (start-up classes in the primary dex), and writes the two `.dm` files that
  every release ships ([06](06-quality-testing-release.md) §8).
- **Checks**: a JVM test parses the committed `baseline-prof.txt` and fails when a hot package
  (start-up, Home, guide, walls, details, player, shared components, the paged repositories) has no
  rule; a Macrobenchmark start-up comparison, `CompilationMode.None` against
  `Partial(BaselineProfileMode.Require)`, must show the profile helping; packaging checks that both
  `.dm` files belong to the APK (as `package-sohva-beta.ps1` does).
- **Regenerate** at every milestone, after any change to the start-up path or a hot screen, after
  Compose/Media3/Room upgrades, and before every release. A nightly job regenerates and posts the
  per-package rule counts, so a shrinking profile is visible.

### 4.10 Compose compiler configuration

- A stability configuration file lists the immutable domain types of `:core:model` (a JVM module
  compiled without the Compose plugin, so the compiler cannot infer their stability) and
  `kotlin.collections.List`/`Map`/`Set` (read-only by convention in screen state).
- Compose compiler reports and metrics are generated in a CI job (not every build) and archived, so a
  hot composable turning non-skippable is visible in review.

### 4.11 Lint and static checks

- `abortOnError = true`, `checkReleaseBuilds = true`, `warningsAsErrors` for the chosen categories,
  lint on every module and variant that ships (`lintRelease`, `lintLab`), Kotlin
  `allWarningsAsErrors = true` from the first commit (easier than cleaning up later).
- A small `:lint-checks` module (build-time only) with the project rules that plain lint cannot see:
  no `Dispatchers.IO`/`Default` outside `AppDispatchers` ([03](03-architecture.md) §4.7); no
  `infiniteRepeatable`/`rememberInfiniteTransition` outside the motion tokens of `:ui:design`
  ([06](06-quality-testing-release.md) §10.5); no `LIMIT … OFFSET` in SQL constants; no
  `Modifier.clip` + `background` pair on non-image nodes ([design/01](../design/01-design-system.md)
  §16.1 rule 7).
- The module dependency check of [03](03-architecture.md) §4.3.
- JVM query-plan tests run on two `sqlite-jdbc` versions: one matching the SQLite of the oldest
  supported Android release in the device matrix and one current (check Android's platform SQLite
  version table), because the planner changed between them.

### 4.12 Signing, credentials and identity in the new repository

- The same mechanisms, same file names, under the new repository's ignored `.local/`:
  `.local/streammate-signing/keystore.properties` (four keys) and
  `.local/trakt/trakt-credentials.properties` (two keys, the character check of §3.4). Copy the
  keystore itself by hand from its existing secure location; never commit it, never paste its values
  into a prompt, script or log.
- CI builds unsigned release APKs and Trakt-less builds. Add a `BuildConfig.TRAKT_CONFIGURED` flag
  and make packaging refuse a public release in which it is false, so a release never ships Trakt
  disabled by accident.
- Lab and demo use the debug key. The debug keystore is per machine; installing a Lab build from
  another machine over an existing one fails signature continuity. Keep one development keystore in
  `.local/` if more than one machine builds Lab.

### 4.13 Version policy

- **Pins**: every version in `libs.versions.toml`; the Gradle wrapper with its SHA-256; GitHub actions
  by major (checkout@v5, setup-java@v5, upload-artifact@v6, setup-gradle@v5,
  android-emulator-runner@v2 as today; `setup-gradle@v6` changes the licence terms of its caching
  component and is the owner's decision).
- **Stable only** in the release classpath; alpha/beta only in `:benchmark` or test tooling, and only
  where no stable exists.
- **One family per change** (for example Compose BOM, or Media3), each with: unit and device suites
  green, release dex verified, method-size gate, APK size delta recorded, start-up and key-press
  measurements on the stand-in no worse than 5 % ([07](07-performance.md)), notices updated.
- **Cadence**: review once a month and at each milestone; security fixes at once; nothing is bumped
  between the release freeze and publication.
- A registry failure (Maven Central HTTP 429 during beta 23's hosted build, for the Compose group
  mapping artifact only the shrunk release resolves) is retried before the code is suspected.
- App versioning (version codes above 57, names) is in [06](06-quality-testing-release.md) §7.

## 5. Lightweight by design

What the build contributes to the budgets of [07-performance.md](07-performance.md):

- **Size**: R8 plus resource and locale shrinking from M0; no JSON tree library, no SVG library, no
  second player engine, no bundled fonts; WebP at drawn size; the size gate stops creep.
- **Start-up and first frames**: generated baseline and startup profiles covering every hot screen,
  shipped as `.dm` files and installed with updates, so updates run compiled at once (uncompiled,
  the first guide frame was 408 ms against 92 ms compiled on the Shield); startup-profile dex layout.
- **Ahead-of-time compilation**: the method-size gate (95 % of ART's 10,000 code units) keeps every
  hot method compilable; small composables with ≤ 8 parameters keep dex verifiable.
- **No reflection** anywhere, so R8 can remove and inline freely and no keep rule pins dead code.
- **Measurement variants** (`lab`, `benchmarkRelease`) are release code, so what is measured is what
  ships.

## 6. Lessons from the current app

| What happened | Rule |
|---|---|
| The release was never minified until 23 Sept 2026: 39.2 MB of dex, and `ActivePlayer` (11,248 code units) and `SettingsScreen` (12,966) past ART's 10,000-unit ahead-of-time limit, so they ran interpreted after every start (script header, `Test-ReleaseMethodSizes.ps1`) | R8 from the first release; method-size gate at 95 % |
| A 41-parameter composable with six defaults produced dex that ART refused (`VerifyError: Verifier rejected class …PlayerScreenKt`) while 747 unit and 261 device tests were green; the previous commit rebuilt clean failed the same way (8 Sept 2026) | Few parameters; verify the release dex on an emulator before every release |
| kXML2 bundled a copy of platform interfaces; R8 broke on it | Never ship a library that duplicates platform classes |
| The hand-written baseline profile had no guide rules; sideloaded and in-app updates ran interpreted for about a day (Home's first composition 922 ms vs 218 ms compiled) | Generated profiles; `.dm` files in every release; session installs |
| AGP trimmed the non-debuggable test APK's desugared library and crashed `Application.onCreate` in Lab/release instrumentation | Keep the `j$` keep rule for the test APK's L8 task while minSdk < 26, or drop desugaring with minSdk 26 |
| Two variants exported the same Room schema concurrently | Order schema-exporting tasks, or export from one variant |
| The artwork disk cache was a flat 1 GB, chosen by nobody (30 Aug 2026) | Every cache has a stated bound; the viewer picks 100/250/500 MB |
| The image memory cache used a phone-style percentage (Shield native heap above 300 MB) and unbounded decoders saturated four cores (frame p90 > 100 ms) (4 Sept 2026) | 8 % of the memory class, 2 decoders ([07](07-performance.md)) |
| Resource shrinking was never enabled | On from M0 |
| The public Build failed on lint from beta 6 to beta 11 while nobody ran lint locally | Lint every module in CI and in packaging ([06](06-quality-testing-release.md)) |
| A Maven Central HTTP 429 failed a hosted build | Retry the failed job first |
| `dependenciesInfo` made sideloaded APKs differ on every build | Keep `includeInApk = false` |

## 7. Open questions

1. Navigation 3 or a hand-written stack, and therefore Parcelize or `kotlinx-serialization-core`
   route keys ([03](03-architecture.md) §7 question 1).
2. Moshi's streaming reader (JVM-testable, one small library) or `android.util.JsonReader` (no
   library, device-only parser tests). This file recommends Moshi's reader.
3. May the TMDB attribution logo ship as a VectorDrawable converted from the unmodified SVG under
   TMDB's logo terms? If not, keep an SVG decoder or a PNG supplied by TMDB.
4. Keep `tv-material` at all? Decided by the M0 component gallery (size and whether any component is
   actually used).
5. minSdk 23 or 26 ([00](00-product-overview.md) §8): 26 removes desugaring and the L8 test-APK trap;
   23 keeps devices that cannot use profiles anyway.
6. Does the Elisa box (and the Xiaomi stick class) run a 32-bit userland? It decides which ABI the
   ART compiler and native libraries use on the reference device; the diagnostics file should record
   `Build.SUPPORTED_ABIS`.
7. Should Gradle dependency verification (`verification-metadata.xml`) be enabled for supply-chain
   protection, given the release is built on the owner's machine?
8. The benchmark and profile-generation emulator image (Android TV or Google TV, API 33 or 34) and
   whether GitHub-hosted runners can run it within the job limits.

## 8. Reference: current code map

- `gradle/libs.versions.toml` — every version and catalog key.
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties` — modules, plugins, Gradle settings.
- `app/build.gradle.kts` — build types, test build type switch, `BuildConfig` Trakt fields, signing, R8, lint, L8 workaround.
- `core|iptv|sportmate|addons|trakt/build.gradle.kts` — library modules, KSP schema export, opt-in test properties.
- `app/proguard-rules.pro` — the two R8 rules and why.
- `app/src/main/baseline-prof.txt` — the hand-written profile (45 rules).
- `app/src/main/AndroidManifest.xml`, `app/src/demo/AndroidManifest.xml`, `app/src/lab/AndroidManifest.xml` — permissions, `profileable`, variant overlays.
- `app/src/main/java/com/streammate/tv/app/StreamMateApplication.kt` — the Coil loader (disk, memory, decoders, crossfade).
- `app/src/main/java/com/streammate/tv/app/AppRuntimePolicy.kt` — per-package feature policy (Lab, demo).
- `app/src/main/java/com/streammate/tv/app/DemoContentProvider.kt`, `app/src/demo/java/com/streammate/tv/demo/*` — the reflective demo seam and fixtures.
- `app/src/main/java/com/streammate/tv/app/UpdateSessionInstaller.kt`, `AppUpdates.kt`, `AppUpdateChecker.kt` — session install with `.dm` profiles.
- `iptv/src/main/java/com/streammate/tv/iptv/xmltv/XmlTvParser.kt` — platform pull parser.
- `scripts/Test-ReleaseDexVerifies.ps1`, `scripts/Test-ReleaseMethodSizes.ps1`, `scripts/package-sohva-beta.ps1` — dex verification, method-size gate, profile checks in packaging.
- `THIRD_PARTY_NOTICES.md` — the runtime dependency families and their licences.
