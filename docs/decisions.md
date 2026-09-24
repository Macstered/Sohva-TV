# Decisions

Every question the kit ([docs/rebuild/](rebuild/README.md)) did not settle, with the answer and why.
Newest last. "Default" means the default from
[plan/09-owner-decisions.md](rebuild/plan/09-owner-decisions.md), followed until the owner says
otherwise.

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-24 | Where does the kit live in the new repository? | `docs/rebuild/`, with `AGENTS.md` and `CLAUDE.md` at the root. Links broken by the move were rewritten mechanically (root `AGENTS.md` → `docs/rebuild/…`; kit links to `../AGENTS.md` → `../../../AGENTS.md`); AGENTS.md §2 says short paths are relative to `docs/rebuild/`. No other kit text changed | Owner's instruction; the kit README suggests it |
| 2026-09-24 | Commit the kit's images (about 112 MB, largest file 6.9 MB) with Git LFS? | Plain git for now; revisit before the first push | Nothing is pushed yet, so moving `design/screenshots/` and `assets/flavors|source/` to LFS or dropping `older-builds/` later only rewrites local history. Owner to choose before the first push |
| 2026-09-24 | Branches | `main` holds the kit import; each milestone works on its own branch (`m0-foundations`, …) and merges to `main` when its exit criteria pass | AGENTS.md §8 |
| 2026-09-24 | A1 Existing testers' data | Default **B**: one-time import from the old database and sources on first start, plus `.smbak` restore. The new database is `sohva.db`; new Keystore aliases differ from the old ones | plan/04 §17 |
| 2026-09-24 | A2 minSdk | Default **23**, platform SQLite | plan/00 §8 |
| 2026-09-24 | A3 First version | Default **versionCode 100, `0.2.0-beta.1`** | Leaves 58–99 for beta 23 hotfixes |
| 2026-09-24 | A4 Search matching | Default **FTS token prefix**, accents not folded | spec 03 |
| 2026-09-24 | A5 Guide history kept | Default: catch-up channels min(catch-up days, 24.5 h), others 3 h | Smaller database, faster imports |
| 2026-09-24 | A6 Backup format | Default **format 3**, reading formats 1–2 | spec 71 |
| 2026-09-24 | A7 Restricted profiles | Default **filter everything by allowed groups, gate managers with the PIN** | spec 04 |
| 2026-09-24 | A8 Reduce motion | Default **automatic, plus a switch in Settings › General** | design/01 §16.3 |
| 2026-09-24 | A9 Network policy | Default **HTTPS-only** for first-party services and addons; privacy text corrected | spec 73 |
| 2026-09-24 | A10 Time format | Default **locale everywhere** | spec 74 |
| 2026-09-24 | Section B behaviour choices | Defaults as listed in plan/09 §B, recorded again here when their milestone implements them | plan/09 |
| 2026-09-24 | Is `docs/rebuild/` public, and is it audited? | Kept in the repository and **excluded from the public-source audit and gitleaks path rules**; LFS or dropping `older-builds/` still decided before the first push | Owner's answer. The kit carries the public contact e-mail, old machine paths and certificate hashes by design |
| 2026-09-24 | Tooling downloads | An Android TV emulator image at API 33+ (profile generation) and gitleaks are installed on the build machine | Owner's yes, 24 Sept 2026 |
| 2026-09-24 | M0 plan and gallery scope | [m0-plan.md](m0-plan.md) accepted: M0 builds the shared components; feature-specific pieces join the gallery in their milestones | Owner's "go ahead" |
| 2026-09-24 | Toolchain for M0 | Gradle 9.7.1, AGP 9.4.1 (built-in Kotlin, new DSL), Kotlin 2.4.20, KSP 2.3.12; the other libraries at their latest stable (see `gradle/libs.versions.toml`) | plan/05 §4.2: latest stable at M0 |
| 2026-09-24 | Compose version | BOM **2026.06.01** (Compose 1.11.4) and core-ktx 1.18.0, not 2026.09.00 and 1.19.1: both newer releases require compileSdk 37, which is not installed. compileSdk and targetSdk stay 36 | Keeps plan/05's SDK levels and avoids an unapproved SDK download. Move to Compose 1.12 together with compileSdk 37 as one family change |
| 2026-09-24 | Launcher entry and resource names | The activity is `com.sohva.tv.app.MainActivity`; an `activity-alias` named `com.streammate.tv.app.MainActivity` carries the `LEANBACK_LAUNCHER` filter. Window resources renamed `sportmate_*` → `sohva_*`, `Theme.StreamMate` → `Theme.Sohva` | Launchers remember the component name (plan/03 §4.16); resource names are not identity (AGENTS §9) |
| 2026-09-24 | Device safety in the build | Every `install*`, `uninstall*` and `connected*` task fails unless `ANDROID_SERIAL` starts with `emulator-` | The owner's Shield is attached to adb on this machine; those tasks act on every attached device |
| 2026-09-24 | APK size baseline 1,125,074 → 1,381,337 bytes (+256 KB) | Accepted | The data foundations: Room, DataStore, coroutines and the desugared `java.time` library (needed below API 26, minSdk 23). All are in plan/05 §4.3's minimum set |
| 2026-09-24 | Method-size gate scope | Fails on app methods (`com.sohva.tv.*`, `com.streammate.tv.*`) above 9,500 code units; library methods above it are listed in the report. First one: the desugared `j$.time.chrono` method of 11,852 units | Library code cannot be split, and plan/05 §3.10 gates app methods. Keep `java.time` off hot paths where cheap |
| 2026-09-24 | Secret store names | The rebuild's store: Keystore alias `sohva.secrets.v1`, wrapped key in `sohva_secret_envelope` › `data_key`, values in `sohva_secure_settings`, AADs `sohva-secret-v1`/`-v2`. Same envelope construction as beta 23 (spec 73 SEC-FR-01..07), parameterised by an `EnvelopeSpec` so the importer reads beta 23's store with the same code | A1 = import: new names never collide with the old store the importer reads |
| 2026-09-24 | Locale file | Keeps beta 23's `streammate_locale` › `language_tag` | It is read in `attachBaseContext`, before the importer can run, so the language holds on the first start after the update |
| 2026-09-24 | Preferences file | New DataStore file `sohva_preferences`, beta 23's key names and stored values (`color_theme`, `interface_scale`, `startup_screen`) | A1 = import; same keys keep backups and the importer simple |
| 2026-09-24 | Query-plan tests on two SQLite versions | One `sqlite-jdbc` (3.53.4.0) at M0; the second, older version is added with M1's first hot query | Nothing but `app_meta` exists to plan yet |
| 2026-09-24 | DataStore in JVM tests | JVM tests of `AppPreferences` use an in-memory `DataStore`; persistence is a device test | DataStore's file storage cannot rename its temporary file on a Windows JVM |
| 2026-09-24 | Error codes without a beta 23 sentence | An `AppError` code exists only together with its plain-language string in every language. `storage_full` waits for the milestone that writes its sentence; `secrets_unreadable` shows "Unknown error" until spec 73's wording lands in M1 | AGENTS §5 rules 5–6: no code without a sentence, no invented translations |
| 2026-09-24 | Strings location | All 1,436 beta 23 strings imported unchanged by `tools/import_strings.py` into `:ui:design`, one file per feature group; each group moves to its feature module when that module is created (the script's `DESTINATIONS`) | Roadmap M0 imports all strings; feature modules do not exist yet |
| 2026-09-24 | Keep `androidx.tv:tv-material`? | **Dropped.** Every component is built on Compose `foundation` through one focusable primitive (`TvSurface`), and the inherited text style (16/24 sp, Regular, 0.5 sp tracking, no font padding, trimmed line height) is set deliberately in `SohvaTypography.Inherited` | plan/05 §7 Q4 and design/01 §17: tv-material's only effect on the look was that inherited style; one primitive prevents the default click indication |
| 2026-09-24 | Screenshot tests | Roborazzi on Robolectric (JVM, native graphics, SDK 35), 42 goldens: 6 gallery pages × 7 themes; Original at 1920 × 1080 for comparison with beta 23's screenshots, the others at half size (7.9 MB in total); 0.1 % change threshold. A 2 dp padding change fails verification | plan/06 Q3: runs without an emulator; half size keeps the repository small |
| 2026-09-24 | Focus shadows on buttons, rows, fields, icon actions | Not drawn (design/01 §16.1 rule 6); the pre-rendered shadow for large cards comes with the Home cards (M5) | Invisible on the near-black ground; saves an elevation pass per focus |
| 2026-09-24 | Lint findings on beta 23's texts | Generated string files carry targeted `tools:ignore`: `MissingQuantity` (es/pt/it plurals lack "many"; Android falls back to "other" as in beta 23), `PluralsCandidate` (9 English "%d things" strings; convert with translations in their feature's milestone), `Typos` (German "Pos1" is the Home key's name) | Texts stay exactly as beta 23 had them |
| 2026-09-24 | QR component | Built in M1 with the phone setup, together with the ZXing dependency | No M0 screen shows a QR code; every dependency pays rent (plan/05 §4.1) |
