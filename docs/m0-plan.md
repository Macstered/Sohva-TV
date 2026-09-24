# M0 plan: foundations

Plan for milestone M0 of [plan/02-roadmap.md](rebuild/plan/02-roadmap.md). Status: **done on
branch `m0-foundations`, 24 September 2026** (owner's go the same day). The exit check is at the
end; decisions are in [decisions.md](decisions.md), measurements in
[performance-log.md](performance-log.md).

M0 inventory IDs: SHELL-01…34 ([plan/01](rebuild/plan/01-feature-inventory.md)). M0 can finish
SHELL-01–07, 10, 20, 21 (placeholder destinations), 33 and 34. The rest need later features; M0
builds their seams (routes, open requests, runtime policy) and they are ticked in their milestones.

## 1. Module layout

The target is plan/03 §4.2 (15 production modules). **M0 creates only the modules it fills**, so no
module is an empty shell that drifts. Each later milestone adds its own.

| Module | M0 content | Later |
|---|---|---|
| `build-logic/` (included build) | Convention plugins: `sohva.jvm`, `sohva.android.library`, `sohva.android.compose`, `sohva.android.app`. They set JDK 17, SDK levels, lint, `allWarningsAsErrors` and the Compose stability file once, so module build files stay a few lines long | – |
| `:core:model` (JVM) | `AppError` + `Outcome` + `LoadState`, `AppDispatchers` interface, `Clock`, `FeatureFlags`, `MemoryTier`, redaction rules, `DiagnosticsLog` interface, route ids | domain types from M1 |
| `:core:data` | Room database `sohva.db` v1 with one `app_meta` table (key/value; later holds the A1 "imported" marker), explicit WAL, schema export; DataStore preferences with narrow per-key flows; the synchronous locale file; `SecretStore` (envelope AES-256-GCM, **new** Keystore alias); ring-buffer `DiagnosticsLog` (600 lines, redacted) | every table from M1 |
| `:ui:design` | Tokens, the 7 palettes (resolved values hard-coded from design/01 §5), typography with a deliberate default text style, spacing, shapes, interface scale, the pre-rendered ground bitmap, the one focusable primitive, shared components, focus helpers, navigation host, `LoadState` views, **all imported strings** (see §4) | contribution interfaces as features arrive |
| `:feature:home` | Home rail with placeholder destinations (Home content itself is M5) | Home, Search (M5) |
| `:app` | `Application`, `MainActivity`, `AppGraph` (lazy), route table, manifest, variants, debug-only gallery screen | – |
| `:spike:guidegrid` | The throw-away guide-grid spike (§6); included only in `debug` and `lab` | deleted or promoted at M2 |
| `:testing` | Fakes (clock, dispatchers, cipher), `ClearStateRule`, `QueryGate`, key-event helpers | fixtures from M1 |
| `:benchmark` | Start-up Macrobenchmark and the profile generator | journeys per milestone |
| `:lint-checks` | Project lint rules: no `Dispatchers.IO/Default` outside `AppDispatchers`; no infinite animations outside `:ui:design` motion tokens; no `LIMIT … OFFSET` in SQL constants; no `clip` + `background` pair on non-image nodes | – |

The dependency table of plan/03 §4.3 is enforced from the first commit by a Gradle check task.

## 2. Build setup

- **Versions:** beta 23's set (Kotlin 2.3.21, AGP 8.13.2, Gradle 8.14.3, Compose BOM 2026.06.00, Room
  2.8.4, …) moved to the latest *stable* of each at the first build, pinned in
  `gradle/libs.versions.toml` with the date. The Gradle wrapper is pinned with its SHA-256. If the
  latest stable AGP is 9.x, I adopt it and record the DSL differences.
- **SDK:** minSdk 23 (A2), target/compile 36, desugaring on, JDK 17 toolchain.
- **Version:** `versionCode 100`, `versionName 0.2.0-beta.1` (A3).
- **Build types:** `debug` (`.debug`), `release`, `lab` (`.lab`, **minified**, debug-signed, not
  debuggable), `demo` (`.demo`, fictional content from a source set, no reflection); plus
  `benchmarkRelease`/`nonMinifiedRelease` from the baseline-profile plugin. Launcher component name
  `com.streammate.tv.app.MainActivity` kept (activity or alias).
- **Release:** R8 + resource shrinking; `proguard-rules.pro` = `-dontobfuscate` +
  `-keepattributes SourceFile,LineNumberTable`; the seven locales only; `dependenciesInfo` off;
  `<profileable android:shell="true"/>`.
- **Signing:** `.local/streammate-signing/keystore.properties` (ignored); unsigned release when it
  is absent. M0 never needs the real key; you copy it in by hand when a signed build is wanted.
- **Gates (Gradle tasks + scripts, runnable locally and in CI):** APK size (≤ 4 MB at M0, +200 KB
  against `build/size-baseline.txt`), method size (≤ 9,500 dex code units via `dexdump`), release dex
  verification on the emulator (`cmd package compile -m verify -f`, search for "failed to verify"),
  module dependency check, string parity across the seven languages, public-source audit, gitleaks.
- **CI:** a GitHub Actions workflow (plan/06 §5), written now but only run once pushed:
  unit tests, lint per module and variant, release and lab assemble, the gates above.

## 3. Design system and component gallery

- **tv-material:** proposed **dropped**. Every component is built on `foundation` through one
  focusable primitive (design/02 §22), and the text default (the inherited 0.5 sp tracking) is set
  on purpose. I measure the APK and method count with and without it once the primitives exist and
  record the result.
- **Ground:** one dithered 960 × 540 bitmap per (theme, screen size, interface scale), rendered off
  the main thread and drawn once per frame; window background swapped to flat `#05070D` two frames
  after the first frame. Checked for banding against `beta23-synthetic/home.png`.
- **Components in M0:** the shared ones of design/02: brand, icons (vectors), surface, action
  button, list row, URL field and edit dialog, tag chip, settings rows and switch, card frames and
  placeholders with initials, progress bars, status marks (live dot, badge, watched, buffering),
  key hints, dialog/sheet/picker frames, QR code, the Home rail, focus and scroll helpers.
  Feature-specific pieces (player overlay parts, match card, Discover cards) join the gallery in
  their milestones.
- **Gallery:** a debug-only screen listing every component in every state (rest, focused, selected,
  selected+focused, disabled, danger, danger+focused, ring-focused), switchable across the seven
  themes and four interface scales.
- **Screenshot tests:** Roborazzi on the JVM (Robolectric), one golden per component group × theme,
  so they run without an emulator. Plus a side-by-side HTML page (gallery capture next to the matching
  `beta23-demo` screenshot) for your visual check.
- **Reduced motion:** tier detection (plan/07 §2.2) off the main thread; motion tokens that jump in
  the low tier; the zero-animator-scale test for the buffering arc.

## 4. App shell

- Launch layer-list and Compose launch screen (design values of spec 01 §5); the start snapshot
  (theme, scale, start screen, profiles question) read on `io`; the first frame composed at the saved
  theme and scale, with no flash.
- **Navigation:** a hand-written stack of about 200 lines on `SaveableStateHolder` + a per-entry
  `ViewModelStore`, with Parcelize route descriptors carrying ids only, saved across process death.
  Every screen exposes a `FocusAnchor`; return focus uses the 30-frame retry and falls back to the
  opener, never to the first focusable. Back goes through `OnBackPressedDispatcher`. Navigation 3 is
  not used unless you want it compared (plan/03 §4.5 allows either).
- **Home rail:** 80 ↔ 244 dp in its own layer, 150 ms tween (snap in the low tier), test tags of
  spec 01 FR-60–67, placeholder destinations for every item.
- **Locale:** `attachBaseContext` below API 33, the per-app locale from 33, `locales_config.xml`.
- **Strings:** a script imports `reference/strings/` into `:ui:design` as one file per feature
  (`strings_guide.xml`, `strings_player.xml`, …), all seven languages, texts unchanged. When a
  feature module is created, its file moves there. The parity check runs on every build.
- **Tests (instrumentation, emulator only):** launch mark/brand, launcher label EN/FI, three
  launches in one process, rail order and keys, Back from every placeholder returns focus to its rail
  item (real `KEYCODE_BACK`), theme from the first frame. JVM tests for the stack and start route.

## 5. Data foundations

`AppGraph` of lazy holders; nothing on the main thread before the first frame (a debug `StrictMode`
policy fails the build's tests if it does); `AppDispatchers` with the `bulk` background-priority
thread and `PauseGate`; the error model with its plain-language strings; the migration-test harness
(exported schemas in device-test assets) and the JVM query-plan test harness on `sqlite-jdbc` (two
SQLite versions), so M1's first tables arrive with their tests.

## 6. Performance harness and the guide-grid spike

- **Harness** (`tools/`, never shipped): the owner-scale fixture generator (56,164 channels in 800
  groups, 165,600 programmes anchored to "now", films and series, fictional names), a loopback-only
  HTTP server, emulator scripts (cold boot with `-no-snapshot-load`, one run at a time, compile-state
  switch), a Perfetto config and an analysis script that sums main-thread CPU per slice per phase.
  M0 only needs the channel and programme part; films come in M1.
- **Start-up Macrobenchmark:** cold start, `CompilationMode.None` against
  `Partial(BaselineProfileMode.Require)`.
- **Spike:** a guide over 56,000 in-memory channels and a 4-hour window. Variant A: one `Canvas` per
  row with a cached text measurer, labels formatted once per data change, off-screen blocks culled,
  **one focusable per row** holding the selected programme index. Variant B: a per-cell
  composable row like the old grid, as the baseline. Measured with trace markers on held D-pad
  presses in a lab (minified) build. The target is ≤ 11 ms main-thread CPU between rows and ≤ 7 ms along a row on the stand-in. The
  result and the chosen structure go into the performance log and decisions.

## 7. Order of work (small commits on `m0-foundations`)

1. Gradle skeleton, `build-logic`, catalog, `:app` that launches; release with R8; size, method and
   dex gates.
2. `:core:model`, `:core:data` foundations with their tests.
3. `:ui:design` tokens, themes, ground, primitives; gallery and screenshot tests.
4. Shell: launch screen, stack, rail, locale, strings import; focus tests.
5. Harness: fixture, server, emulator scripts, start-up benchmark, first performance-log rows.
6. Guide-grid spike and its measurement.
7. CI workflow, `:lint-checks`, public-source audit, gitleaks.
8. Exit check against plan/02 M0; inventory IDs ticked; merge to `main` (locally, no push).

## 8. Needs from the owner

See the reply that accompanied this plan; answers are recorded in [decisions.md](decisions.md).

## 9. Exit check (plan/02 M0)

| Exit criterion | Result |
|---|---|
| Cold start to the first shell frame within the plan/07 budget on the stand-in | Time to initial display 357 ms with the baseline profile, 375 ms without (budget on the emulator: 400 ms) |
| Release APK size recorded as the baseline, ≤ 4 MB | 2,339,240 bytes (`config/size-baseline.txt`) |
| The gallery renders every component in all seven themes; screenshot tests green | 6 pages × 7 themes, 42 Roborazzi goldens verified; on-device gallery in debug builds |
| Focus returns to the originating rail item after Back from every placeholder destination | Device test with real Back key events, green on API 30 and 34; proven to fail without the fix |
| CI green; release dex verifies on the emulator | Local run of the Build workflow's checks green (`tools/check_all.py`); hosted CI not run (nothing pushed); release dex verifies (34 dex2oat lines, no rejections) |

Also delivered: data foundations with 19 + 13 JVM and 6 device tests; the start-up benchmark and
the first generated baseline profile (14,573 rules); the owner-scale fixture and loopback server;
the guide-grid spike and its decision (one canvas and one focusable per row); the project lint
rules, module rules, public-source audit and secret scan.

Inventory: SHELL-01–06, 10, 20, 21, 33, 34 ticked. The other SHELL items need later milestones
(player routing M2, profiles and PIN M6, reminders M3, background work M1/M4, updates M7).

Carried into M1 and M2:

- The between-row guide cost (10.3 ms of an 11 ms budget) is mostly framework work; attribute it
  with Perfetto in M2 (needs `trace_processor`, not installed) and decide whether the guide's
  scroll should snap.
- Start the start snapshot read at process start, in parallel with activity creation (up to
  60 ms on the stand-in's debug build; measure on release).
- compileSdk 37 would unlock Compose 1.12, core-ktx 1.19 and lifecycle 2.11 (all pinned one step
  back); it needs the API 37 SDK platform downloaded.
- Hosted CI has never run: the first push will be its first run (Roborazzi goldens recorded on
  Windows may need a tolerance review on Linux).
