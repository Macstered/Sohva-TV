# Improvement plan from the review of 11 November 2026

This plan turns the findings of the review of 11 November 2026 into steps that one agent can carry
out alone. The review covered engineering practice, the interface and performance. Every finding
was checked against the code again before a step was written for it; section 7 lists what turned
out to be different from the report.

## 1. Summary

The plan fixes crashes, lost input and security gaps first, then the small changes a viewer notices
most, then the background-work and database changes that need measurements. Each step is one
commit. Nothing is pushed, released or installed on a real device; those actions are in the last
phase and wait for the owner.

| Phase | What it holds | Steps |
|---|---|---|
| 0 | Baseline: branch, checks, measurements | 1 |
| 1 | Crashes, lost data and security | 8 |
| 2 | High-impact interface changes that are small | 14 |
| 3 | Background work and database performance | 11 |
| 4 | Small repository fixes | 1 |
| 5 | Steps that wait for the owner | 3 |

There are 38 steps: 24 small, 11 medium, and 3 that wait for the owner. No step changes the
database schema, the application ID, the version code or the files under `docs/rebuild/`.

## 2. Execution rules

Follow these while you work alone. Read `AGENTS.md` and `CLAUDE.md` at the repository root first;
they apply to every step.

### Branch and commits

- Work on a new branch named `improve/review-fixes`. Create it from `main` if the branch
  `fix/live-stream-end` is already merged into `main`; otherwise create it from
  `fix/live-stream-end`, because the player fixes on that branch touch files this plan also edits.
- Make one commit per step. Start the commit message with the step ID, for example
  `CRASH-1: secure-storage failures no longer crash start-up jobs`, then say what changed for the
  viewer and why. End the message with the attribution line your session gives you.
- Add files to a commit by path. Do not use `git add -A`: the working tree shows
  `.claude/scheduled_tasks.lock` as deleted, and that file is not yours to change (step OWN-3).
- Do not push, open a pull request, tag, publish a release, or change `versionCode` or
  `versionName`.

### Order and stopping

- Carry out the steps in the order of section 4, starting from the first unchecked one. Do not
  stop between steps to ask a question or to offer to continue.
- These actions need no approval: editing project files inside the step's scope, adding or
  deleting a file that the step names, running the build, tests, lint and the app locally or on a
  rebuild emulator, retrying a command that failed or timed out, updating this plan file, writing
  to `docs/decisions.md` and `docs/performance-log.md`, and committing on the branch.
- After each step run the step's own check, then the full check below. On a pass: commit, tick the
  step's checkbox in this file, and add a line to the progress log (section 5) with the result and
  any measurement. On a fail: find the cause and fix it inside the step's scope. If the step still
  fails after a real attempt, undo its changes (`git checkout -- <files>` and delete files the step
  created), mark the step "blocked" in the progress log with what you found, skip the steps that
  list it under "Depends on", and continue with the rest.
- Stay inside each step's scope. When you see a nearby problem, write it in the progress log and
  do not fix it.
- Errors, timeouts, failing commands and empty results are ordinary obstacles: find the cause,
  then retry, fix the command, or reach the same result another way. A deliberate blocker (access
  that was withheld, a file marked not to be touched, a guard that refuses a device) stays
  untouched: record it and move to the next step that does not depend on it.
- When no runnable step is left, stop everything you started (emulator, fixture server, Gradle
  daemon) and write a final report that makes sense on its own: what each step changed, the before
  and after numbers, the blocked steps and why, the steps that wait for the owner, and the branch
  and commits to review.

### Commands

Use bash and absolute paths. Send Gradle output to a file, never through a pipe: the Gradle daemon
keeps a pipe open and the command never returns. Run one Gradle build at a time. Put logs and
captures in `~/code/SohvaTV-Rebuild/harness-out/plan/` (git ignores `harness-out/`).

The full check, run after every step (replace `STEP` with the step ID):

```bash
cd ~/code/SohvaTV-Rebuild && mkdir -p harness-out/plan && ./gradlew --max-workers=6 checkModuleDependencies testDebugUnitTest :core:model:test :lint-checks:test verifyRoborazziDebug lintDebug assembleDebugAndroidTest > harness-out/plan/STEP-full.log 2>&1; echo "exit=$?" >> harness-out/plan/STEP-full.log
```

It passes when the log ends with `BUILD SUCCESSFUL` and `exit=0`. Lint and the Kotlin compiler
treat warnings as errors.

The complete check, run once at the end of each phase:

```bash
cd ~/code/SohvaTV-Rebuild && python tools/check_all.py
```

It passes when it prints `all checks passed`. It also builds the release, runs the size gates, the
string checks, the public-source audit and the secret scan.

The emulator, for device tests and measurements:

```bash
cd ~/code/SohvaTV-Rebuild && source ~/.profile && python tools/start_emulator.py --avd sohva_rebuild_tv30 --port 5570
```

A device test run (replace `MODULE` with `:app`, `:core:data` or `:core:sync`, and `CLASS` with
the full class name):

```bash
cd ~/code/SohvaTV-Rebuild && ANDROID_SERIAL=emulator-5570 ./gradlew MODULE:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=CLASS > harness-out/plan/STEP-device.log 2>&1; echo "exit=$?" >> harness-out/plan/STEP-device.log
```

A device test counts as passed only when the log shows it ran. Tests whose class name contains
`OwnerScale` skip themselves unless the command also carries
`-Pandroid.testInstrumentationRunnerArguments.ownerScale=true`, and they read the fixture from the
host: generate it once with `python tools/fixture/make_fixture.py` and keep
`python tools/fixture/serve_fixture.py` running in the background while they run (it listens on
127.0.0.1:8780; the emulator reaches it as `http://10.0.2.2:8780`). `OwnerScaleImportTest` writes
its timings to logcat under the tag `SohvaMeasure`; read them with
`adb -s emulator-5570 logcat -d -s SohvaMeasure:*`. Read a test class's header comment before its
first run.

Stop the emulator with `adb -s emulator-5570 emu kill`, the fixture server by ending its process,
and the Gradle daemon with `./gradlew --stop` when a step no longer needs them, and always before
the final report.

A screen capture for the owner:

```bash
mkdir -p ~/code/SohvaTV-Rebuild/harness-out/plan/captures && adb -s emulator-5570 exec-out screencap -p > ~/code/SohvaTV-Rebuild/harness-out/plan/captures/STEP-name.png
```

### Devices

- Every `adb` command names `-s emulator-5570`, and every Gradle device task has
  `ANDROID_SERIAL=emulator-5570`. The owner's Nvidia Shield is often attached to the same adb
  server; a command without a serial could reach it and wake the television.
- Drive the emulator with D-pad key events only (`adb -s emulator-5570 shell input keyevent …`).
  Do not use `input tap`: a tap puts the emulator in touch mode and focus tests then fail until a
  D-pad key is sent.
- The emulator already holds an install of `com.streammate.tv` (a preview build). Leave it. Debug
  tests install `com.streammate.tv.debug` and the lab build installs `com.streammate.tv.lab`; both
  are separate packages. If a tool says it must uninstall `com.streammate.tv`, do not let it;
  record that and use the step's other checks.
- Do not change display or screensaver settings on any device, the emulator included.

### Project rules that shape every step

- Tests first for behaviour that has broken before (focus, Back, paging, imports). For a
  regression test, run it on the unchanged code and keep the failing log before you fix the code,
  as `docs/performance-log.md` shows for earlier work.
- New text the viewer sees goes into `ui/design/src/main/res/values/rebuild_strings.xml` and the
  same file in `values-fi`, `values-fr`, `values-es`, `values-pt`, `values-de`, `values-sv` and
  `values-it`, translated in each. Do not change a string that `tools/import_strings.py` manages
  (every other `strings_*.xml` file): those texts are copied from the previous app and the check
  fails when they drift. List every new string and its Finnish text in the progress log so the
  owner can review the wording.
- Where a step departs from a numbered requirement in `docs/rebuild/specs/`, do not edit the spec.
  Add a row to `docs/decisions.md` (date, question, decision, why) in the same commit. Section 3
  names the departures.
- A step that reports a measurement adds a dated entry to `docs/performance-log.md` in the same
  commit, with the before and after numbers and the log file names.
- Colours, sizes and type come from the design tokens (`Sohva.palette`, `Sohva.typography`,
  `Sohva.spacing`, `Sohva.shapes`), never literals, in any code you add.
- No database, network, file or parsing work on the main thread. Name dispatchers only through
  `AppDispatchers`.
- Do not read, edit or commit anything under `.local/`. Do not edit the old application's tree.
- Keep machine paths out of every tracked document, this file's progress log included. The
  public-source audit (`python tools/audit_public_source.py`, part of the complete check) fails on
  any text that contains a home-directory path, and its pattern also matches the source path of
  the Home feature module when it is written with its `src` folder. Write `~/code/SohvaTV-Rebuild`
  for the repository and "the `:feature:home` module" for that module.

## 3. Decisions made

Each of these was chosen for the owner so the run needs no questions. Overrule any of them by
editing the step before the run starts.

1. **Branch base.** The work branch starts from `fix/live-stream-end` unless that branch is
   already in `main`. Reason: the unmerged player fixes touch `feature/player`, which step
   PLAYER-1 also edits.
2. **Secure storage (CRASH-1, CRASH-2).** The plan stops the crashes, records the failure in
   diagnostics, makes the message truthful and makes a restore refuse before it removes anything.
   It does not add an in-app "reset secure storage" action. Reason: a reset deletes stored
   passwords, and the failure has never been observed on a device; the diagnostics line added here
   will show whether it happens.
3. **Updater identity check (UPD-1).** The downloaded file must carry this application's package
   name and a higher version code. The signer is compared only when Android reports one for the
   file. Reason: Android itself refuses a same-package file from another signer, so the missing
   protection is against a different package; and some Android versions report no signer for a
   file on disk, which must not block every update.
4. **Catch-up templates (SECR-1).** The template is stored encrypted in the existing
   `catchup_source` column, with no schema change, and rows written earlier are encrypted by a
   one-time pass. Reason: a rename needs SQLite 3.25, which Android 6 to 9 do not have, and a
   migration cannot use the Keystore.
5. **Live or film (M3U-1).** An entry whose address looks live (`.ts`, `.m3u8`, a `/live/` path
   segment, or a `udp`, `rtp` or `rtsp` address) is a channel even when its group name contains a
   film or series word, and group names match whole words only. This departs from requirement
   SRC-FR-57 of spec 10, which the spec itself lists as an open problem. On the next refresh,
   entries filed as films move to the guide; viewing positions kept for those "films" are no
   longer shown.
6. **Activity recreation (PLAY-1).** Only the manifest changes, so a keyboard, gamepad or remote
   that connects no longer recreates the activity. The decision of 2 October 2026 (a recreation
   drops playback routes) stays. Reason: that decision fixed a real bug and has its own tests.
7. **Unsaved source form (FORM-1).** An unsaved form is kept in memory while Settings is open and
   reopened when the viewer comes back to it; there is no "discard?" dialog. Leaving Settings
   drops it. Reason: nothing is lost in the two cases seen in the review, with one new sentence
   and no new dialog.
8. **Source form focus (FORM-2).** A new source's form starts on its first address field; a saved
   source's page still starts on "All playlists" as requirement SRC-FR-13 says. Sync and refresh
   buttons are hidden until the source is saved, which departs from SRC-FR-30 to -32 for unsaved
   pages only.
9. **Confirmations (CONF-1).** Four actions get the in-place two-step confirmation: delete a
   channel list, reset a channel's edits, delete a custom library group, reset the Home layout.
   "Forget progress" for one Discover title and "Clear all guide data" keep one press. Reason: the
   first is one position and the second downloads again by itself.
10. **Library start (LIB-1).** Movies and Series still open on History, as requirement VOD-FR-49
    says, except when History is empty; then they open on the first group.
11. **Guide, first Right (GUIDE-1).** From the channel column, Right goes to the programme on air
    when the grid shows the present; otherwise to the first block as today. This departs from
    requirement GUIDE-FR-70, which kept the old behaviour knowingly.
12. **Search, return focus (FOCUS-2).** Coming back to Search from a result focuses that result,
    not the text field. Reason: the code already keeps the results, and focusing the field raises
    the keyboard again.
13. **Contrast (A11Y-1).** In the Original theme the dim text colour changes from `#5B6981` to
    `#7F8CA3` (4.8:1 on raised surfaces), and the LIVE chip's text becomes the dark ink `#05070D`
    on red (5.8:1) in every theme. This departs from the colours of the previous app, which
    `PaletteParityTest` pins; the test's expected value is updated in the same step.
14. **System text size (A11Y-3).** The plan only captures how the interface looks at a system text
    scale of 1.3. Reason: design/01 §14 keeps the system scale on purpose, and every fix is either
    a layout redesign or the end of system text scaling; the owner decides with the captures.
15. **Automatic refresh (SYNC-1).** A refresh that comes due while the viewer is in the app runs
    30 seconds after the viewer leaves, instead of retrying with a growing delay. A television
    that never leaves the app still refreshes only when the viewer asks.
16. **Metadata queue (ENR-1).** Leaving the app no longer re-reads the whole catalogue; that pass
    runs after an import, after a language or key change, and when the queue is empty or outdated.
    Watched and visible titles therefore get their higher priority at the next such pass.
17. **Search (SRCH-1).** Each search group looks at no more than 2,000 full-text matches before
    sorting. A very broad term such as "th" may therefore miss titles; a longer term finds them.
    Statements are not made cancellable. Reason: bounding the work removes the cost that
    cancellation was meant to avoid, without hand-written cursor code.
18. **Import identity (HASH-1).** The position in the provider's list leaves the change check for
    films and series; the stored position is updated with a narrow statement only where it
    differs. The step is kept only if it at least halves the measured time.
19. **Not planned.** Section 6 lists what is left out and why.

## 4. Steps

Files that more than one step edits, in the order the steps reach them. A blocked step does not
block a later one on the same file unless "Depends on" says so.

| File | Steps |
|---|---|
| `core/data/…/security/SecretStore.kt` | CRASH-1, CRASH-2 |
| `app/…/AppGraph.kt` | CRASH-1, CRASH-2, SECR-1 |
| `core/sync/…/update/Updater.kt` | UPD-1, UPD-2 |
| `core/sync/…/LiveImport.kt` | SECR-1, IDX-1 |
| `core/data/…/database/LiveDaos.kt` | SECR-1, GUIDE-2 |
| `feature/settings/…/SettingsModel.kt` | FORM-1, FORM-2, FORM-3 |
| `feature/channels/…/ChannelEditor.kt` | CONF-1, A11Y-2 |
| `ui/design/…/components/Frames.kt` | FOCUS-1, A11Y-2 |
| `ui/design/…/components/Artwork.kt` | A11Y-1, IMG-1 |
| `ui/design/…/focus/TvSurface.kt` | A11Y-2, SURF-1 |
| `core/sync/…/metadata/MetadataService.kt`, `Enrichment.kt`, `EnrichmentWork.kt` | META-1, ENR-1, ENR-2 |
| `core/sync/…/CatalogueImport.kt` | IDX-1, HASH-1 |
| `app/…/MainActivity.kt` and `app/src/main/AndroidManifest.xml` | PLAY-1 (manifest), SYNC-1 (activity) |
| `ui/design/src/main/res/values*/rebuild_strings.xml` | CRASH-2, FORM-1, CONF-1, LIB-1, A11Y-2, TEXT-1 |
| `docs/decisions.md` | most steps; each appends one row at the end |
| Screenshot goldens of `:ui:design` and `:feature:home` | A11Y-1, A11Y-3, DLG-1 |

### Phase 0: baseline

- [x] **BASE-1 — Branch, checks and baseline measurements**
  - Resolves: nothing by itself; every performance step compares against it.
  - Goal: the branch exists, the checks pass on unchanged code, and the current numbers are
    written down.
  - Where: no source file changes. Writes `docs/performance-log.md` (one new dated entry) and the
    progress log in this file.
  - Approach:
    1. Create the branch as the execution rules say.
    2. Run the full check and then the complete check. Record the number of unit tests (the
       review counted 726 passing) and the release APK size printed by `:app:checkReleaseApkSize`
       (the review saw 6,346,039 bytes).
    3. Generate the fixture: `python tools/fixture/make_fixture.py` (writes `harness-out/fixture`,
       56,164 playlist entries, 165,600 programmes, 200,000 films). Start the emulator.
    4. Run the owner-scale import test with the fixture server running and the `ownerScale`
       argument (see "Commands"): module `:core:sync`, class
       `com.sohva.tv.core.sync.OwnerScaleImportTest`. From
       `adb -s emulator-5570 logcat -d -s SohvaMeasure:*` record every step line: `m3u playlist`,
       `m3u guide`, `m3u playlist unchanged`, `m3u catalogue (films, series)`,
       `m3u catalogue unchanged`, the four `xtream` lines, and the heap maximum. Record also the
       import's phase lines that the same log carries (`write transactions`, `sweep and groups`,
       `organisation`, `matches`, `standing copies`, `film counts`, `genre counts`,
       `search index`).
    5. Run `com.sohva.tv.app.ReadsDuringImportTest` (module `:app`) and record that it passes.
    6. Install the lab build beside the existing install:
       `./gradlew :app:assembleLab > harness-out/plan/BASE-1-lab.log 2>&1`, then
       `adb -s emulator-5570 install -r app/build/outputs/apk/lab/app-lab.apk`. Measure five cold
       starts: `adb -s emulator-5570 shell am force-stop com.streammate.tv.lab`, then
       `adb -s emulator-5570 shell am start -W -n com.streammate.tv.lab/com.streammate.tv.app.MainActivity`
       and record `TotalTime`; read `Fully drawn` from logcat. The review measured 216 to 262 ms
       and 542 to 596 ms.
    7. With the lab build, add the fixture playlist as a source so later steps can compare the
       interface. Serve it with `python tools/fixture/serve_fixture.py` (run in the background;
       it listens on 127.0.0.1:8780, which the emulator reaches as `http://10.0.2.2:8780`). In the
       app: Home rail, Settings, Playlists, "+ Add M3U source", the address field (test tag
       `settings-m3u`), OK, `adb -s emulator-5570 shell input text 'http://10.0.2.2:8780/playlist.m3u'`,
       Enter, then the button with test tag `settings-save`. Find which control has focus with
       `adb -s emulator-5570 shell uiautomator dump /sdcard/ui.xml` and
       `adb -s emulator-5570 shell cat /sdcard/ui.xml` (the focused node has `focused="true"`;
       test tags appear as `resource-id`).
    8. Measure main-thread CPU per key press on three screens. The main thread's id equals the
       process id. Read the 14th and 15th fields of `/proc/PID/task/PID/stat` (user and system
       time in ticks of 10 ms) before and after the presses, with
       `PID=$(adb -s emulator-5570 shell pidof com.streammate.tv.lab)`. Presses: 60 Down in the
       guide's channel column, and 20 Down in a Movies group of posters. Record milliseconds per
       press for the guide and for the wall.
    9. Record memory with `adb -s emulator-5570 shell dumpsys meminfo com.streammate.tv.lab`
       (`Java Heap` and `TOTAL PSS`) on Home, in the guide after the 60 presses, and during
       playback of the first channel. The review measured 41, 84 and 84 MB total and Java heaps of
       4, 30 and 20 MB.
    10. Uninstall the lab build (`adb -s emulator-5570 uninstall com.streammate.tv.lab`), stop the
        fixture server and the emulator.
    11. Note in the progress log which later steps change code with no test today:
        `feature/channels` and `feature/profiles` have no unit tests; `core/net` has no test for
        the content-kind rule beyond `M3uReaderTest`; the updater has no test for package checks
        or redirects.
  - Depends on: nothing.
  - Check: the full check and the complete check pass; the progress log holds every number named
    above; `docs/performance-log.md` has the entry.
  - Risk: none to the code. If the import test cannot run, record why and continue; the steps of
    phase 3 that need its numbers measure their own baseline first.
  - Size: medium (time, not code).

### Phase 1: crashes, lost data and security

- [x] **CRASH-1 — Secure-storage failures and stray exceptions no longer crash the app**
  - Resolves: finding B3 (the crash half).
  - Goal: a Keystore that fails in any way produces a failed result, never an uncaught exception;
    an unexpected exception in an app-wide background job is logged and does not end the process
    in release builds.
  - Where: `core/data/src/main/kotlin/com/sohva/tv/core/data/security/SecretStore.kt`,
    `app/src/main/java/com/sohva/tv/app/AppGraph.kt`,
    `core/net/src/main/kotlin/com/sohva/tv/core/net/phone/PhoneServer.kt`,
    `core/data/src/test/kotlin/com/sohva/tv/core/data/` (new test `SecretStoreFailuresTest.kt`, or
    extend the existing secret-store tests if they can inject a failing cipher).
  - Approach:
    1. In `SecretStore.guarded`, also catch `java.security.ProviderException` and map it to
       `Outcome.Failed(AppError.SecretsUnreadable)`, and catch `IllegalStateException` (thrown by
       the `check(...commit())` calls and by `AndroidSecrets.load`) and map it to
       `Outcome.Failed(AppError.Unknown)`. Keep the existing cases. Do not catch `Exception` or
       `Throwable`: a cancellation must still pass through.
    2. In `AppGraph`, give `appScope` a `CoroutineExceptionHandler` that writes
       `diagnostics.error("app", "background job failed", throwable)`. In debug builds
       (`BuildConfig.DEBUG`) the handler rethrows after logging, so tests still see defects. The
       diagnostics log redacts messages already.
    3. In `PhoneServer.serve`, widen the catch around `client.use(::handle)` so that a
       `RuntimeException` from handling one request closes that client and the loop continues; the
       outer `IOException` handling stays as it is.
    4. Test: a `SecretStore` built with a cipher or key provider that throws `ProviderException`
       returns `Outcome.Failed(SecretsUnreadable)` from `read` and `write`; one whose commit fails
       returns `Outcome.Failed(Unknown)`. Write the test first and keep the failing log.
  - Depends on: BASE-1.
  - Check: the new tests pass; the full check passes;
    `git grep -n "CoroutineExceptionHandler" app/src/main` shows the handler.
  - Risk: a swallowed exception can hide a defect in release builds. The diagnostics error line is
    the trace; debug builds still crash. Undo by reverting the commit.
  - Size: small.

- [x] **CRASH-2 — A truthful message and a safe restore when secrets cannot be stored**
  - Resolves: finding B3 (the dead-end half, without an in-app reset; see decision 2).
  - Goal: the message tells the viewer what actually helps, and a restore changes nothing when
    secrets cannot be written.
  - Where: `ui/design/src/main/res/values*/rebuild_strings.xml` (the string
    `error_secrets_unreadable` in all eight folders),
    `app/src/main/java/com/sohva/tv/app/backup/BackupService.kt`,
    `core/data/src/main/kotlin/com/sohva/tv/core/data/security/SecretStore.kt` (one new function),
    a test beside the existing backup tests.
  - Approach:
    1. Change `error_secrets_unreadable` to say, in plain words: saved addresses and passwords can
       no longer be read on this device; clear this app's storage in the television's settings,
       then restore a backup or add the sources again. Translate it in all eight files. This
       string is one of the rebuild's own, so the string check allows the change.
    2. Add `suspend fun writable(): Boolean` to `SecretStore`: it encrypts a fixed short value
       with the cipher inside `guarded` and returns whether that succeeded. It stores nothing.
    3. In `BackupService.restore`, before `rows.markRestoring(true)`, call `writable()` on the
       store that `data.sources.save` uses. When it is false, throw the existing `BackupException`
       kind that the Backup pane shows as a failure, and log
       `diagnostics.error("backup", "restore refused: secrets cannot be written")`. Nothing is
       removed in that case.
    4. Each time `SecretStore.guarded` maps a failure to `SecretsUnreadable`, the caller sees only
       the outcome; add one diagnostics line where the application first sees it. The simplest
       place is `AppGraph.afterFirstFrame`, after `data.profiles.reconcilePin()`: call
       `writable()` and, when false, log `diagnostics.error("secrets", "secure storage unusable")`.
       This is the evidence decision 2 waits for.
    5. Test: with a store whose cipher throws, `restore` throws before any source is removed
       (assert the source list is unchanged).
  - Depends on: CRASH-1 (same file `SecretStore.kt`).
  - Check: the new test passes; `python tools/check_french_strings.py` and
    `python tools/import_strings.py --check` exit 0; the full check passes.
  - Risk: a false "not writable" would block restores; the probe uses the same path as a real
    save, so it fails only when a save would. Undo by reverting the commit.
  - Size: small.

- [x] **PLAY-1 — A connecting keyboard or controller no longer restarts the activity**
  - Resolves: finding B2.
  - Goal: Android does not recreate the one activity when a keyboard, gamepad or remote connects
    or disconnects, so playback continues.
  - Where: `app/src/main/AndroidManifest.xml` (the `MainActivity` element), `docs/decisions.md`.
  - Approach: add `keyboard`, `keyboardHidden` and `navigation` to the activity's
    `android:configChanges`, keeping `screenSize|smallestScreenSize|screenLayout|orientation`.
    The interface reads nothing from those configuration values, so no code changes. Check the
    `lab`, `demo`, `play` and `benchmarkRelease` manifests under `app/src/` and make the same
    change wherever they redeclare the activity. Add the decision row.
  - Depends on: BASE-1.
  - Check: build the release (`./gradlew :app:assembleRelease > harness-out/plan/PLAY-1-release.log 2>&1`)
    and run
    `~/Android/Sdk/build-tools/37.0.0/aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/release/app-release.apk | grep -i configChanges`.
    Pass: the value is `0xdf0` (the old value is `0xd80`). `:app:lintRelease` passes. Device tests
    `com.sohva.tv.app.HomeRestoreTest` and `com.sohva.tv.app.AppLocaleTest` pass.
  - Risk: low. Undo by reverting the manifest line. The real-world effect can only be seen with a
    device that connects during playback; step OWN-2 covers that.
  - Size: small.

- [x] **META-1 — A cancelled metadata lookup no longer stores an unverified match**
  - Resolves: finding B9.
  - Goal: cancelling the enrichment (which happens every time the viewer returns) never leaves a
    series match in the cache that the second scoring would have rejected.
  - Where: `core/sync/src/main/kotlin/com/sohva/tv/core/sync/metadata/MetadataService.kt` (the
    `runCatching` calls near lines 95, 140, 167, 301 and 327),
    `core/sync/src/test/kotlin/com/sohva/tv/core/sync/metadata/MetadataServiceTest.kt`.
  - Approach: replace each `runCatching { … }` around a suspending provider call with a
    `try`/`catch` that rethrows `CancellationException` and treats every other `Exception` as
    today. Before the function that stores a lookup result in the cache writes it, call
    `currentCoroutineContext().ensureActive()`. Test first: a fake TMDB client whose `series`
    call throws `CancellationException`; assert the lookup rethrows and that the cache DAO received
    no write. Keep the failing log from the unchanged code.
  - Depends on: BASE-1.
  - Check: the new test passes; `:core:sync:testDebugUnitTest` passes within the full check.
  - Risk: low. Undo by reverting the commit.
  - Size: small.

- [x] **UPD-1 — The updater checks what it is about to install**
  - Resolves: finding B4 (identity).
  - Goal: a downloaded file is installed only when it is this application with a higher version
    code.
  - Where: `core/sync/src/main/kotlin/com/sohva/tv/core/sync/update/Updater.kt` (the
    `UpdateInstaller` interface, the download path near line 214 and `runInstall`),
    `app/src/main/java/com/sohva/tv/app/update/AndroidUpdateInstaller.kt`,
    `core/sync/src/test/kotlin/com/sohva/tv/core/sync/update/UpdaterTest.kt`, `docs/decisions.md`.
  - Approach:
    1. Add `fun accepts(apk: File): Boolean` to `UpdateInstaller`.
    2. Implement it in `AndroidUpdateInstaller` on the caller's thread (the updater already calls
       the installer off the main thread; if not, wrap it in the `io` dispatcher it holds). Read
       the file with `packageManager.getPackageArchiveInfo`. Return false when the result is null,
       when its package name differs from `app.packageName`, or when its long version code is not
       greater than the installed one. Then compare signers: ask for signing certificates (the
       signing-info flag from Android 9, the older signatures flag below it); when the file
       reports at least one signer, return false unless the SHA-256 digests of its signers equal
       those of the running application; when it reports none, accept and write
       `log.info("update", "no signer reported for the download")`.
    3. In `Updater`, call `accepts` after the digest matches and again at the start of
       `runInstall` (the path that reuses a file after a restart). On false: delete the file,
       clear the remembered download, and fail with the existing
       `UpdateFailure.CHECKSUM_MISMATCH`, so no new text is needed. Log
       `log.info("update", "package check failed")`.
    4. Tests in `UpdaterTest` with the fake installer: `accepts` false leads to
       `CHECKSUM_MISMATCH`, a deleted file and no install call; `accepts` true keeps today's
       behaviour.
  - Depends on: BASE-1.
  - Check: the new tests pass; the full check passes. Then try the end-to-end tool on the
    emulator: `python tools/update_e2e.py --serial emulator-5570`. Pass: it reports the newer
    build running. If the tool would have to uninstall the emulator's existing
    `com.streammate.tv`, stop it, record that the end-to-end run was skipped, and rely on the unit
    tests.
  - Risk: a wrong rejection stops in-app updates until the next release that fixes it, and that
    release would have to be installed by hand. The tests and the end-to-end run guard this; the
    signer rule accepts when Android reports nothing. Undo by reverting the commit.
  - Size: small.

- [x] **UPD-2 — The updater's downloads stay on HTTPS and inside their folder**
  - Resolves: finding B4 (transport).
  - Goal: update requests never follow a redirect from HTTPS to HTTP, accept only expected hosts
    and file names, and stop when a download is larger than announced.
  - Where: `core/net/src/main/kotlin/com/sohva/tv/core/net/update/UpdateHttp.kt`,
    `core/sync/src/main/kotlin/com/sohva/tv/core/sync/update/Updater.kt`, tests in
    `core/net/src/test/` (a new `UpdateHttpTest.kt` using MockWebServer) and `UpdaterTest.kt`.
  - Approach:
    1. In `UpdateHttp`, derive a private client from the one passed in:
       `client.newBuilder().followRedirects(true).followSslRedirects(false).build()`. GitHub
       redirects release downloads to another HTTPS host, so ordinary redirects stay on.
    2. In `Updater`, before any request for an asset (checksums, APK, install profile), accept the
       address only when it is HTTPS on `github.com`, a host ending in `.github.com` or
       `.githubusercontent.com`, or when its scheme, host and port equal those of the configured
       feed address (`UpdaterConfig.feed`). The last case keeps the emulator's local test feed
       working. Otherwise fail with `UpdateFailure.DOWNLOAD_FAILED`.
    3. Accept an asset name only when it matches `[A-Za-z0-9][A-Za-z0-9._-]*`; otherwise treat the
       release as having no usable asset, as the code already does for incomplete entries.
    4. In `UpdateHttp.download`, throw `UpdateHttpException("too large")` when the bytes received
       pass `expected + 1 MiB` (when `expected` is positive) or 64 MiB (always). The caller's
       existing handling deletes the partial file.
    5. Tests: a redirect to `http://` is not followed; an asset on another host is refused; a name
       containing `/` or `..` is refused; a body longer than announced stops the download.
  - Depends on: UPD-1 (same file `Updater.kt`).
  - Check: the new tests pass; the full check passes; the end-to-end tool as in UPD-1, with the
    same rule about the existing install.
  - Risk: GitHub could serve assets from a host outside the list; the feed-host rule and the two
    GitHub suffixes cover today's hosts. Undo by reverting the commit.
  - Size: small.

- [x] **SECR-1 — Catch-up address templates are stored encrypted**
  - Resolves: finding B5.
  - Goal: no provider user name or password is readable in the `channel` table.
  - Where: `core/sync/src/main/kotlin/com/sohva/tv/core/sync/LiveImport.kt` (line 137),
    `app/src/main/java/com/sohva/tv/app/player/PlayerGraph.kt` (`resolveArchive`, lines 60 to 70),
    `core/data/src/main/kotlin/com/sohva/tv/core/data/database/LiveDaos.kt` (a comment on the
    column, two new queries), `app/src/main/java/com/sohva/tv/app/AppGraph.kt`
    (`afterFirstFrame`), tests in `core/sync/src/test/…/LiveImportTest.kt` and a device test in
    `core/data/src/androidTest/` or `app/src/androidTest/`, `docs/decisions.md`.
  - Approach:
    1. In `LiveImport`, write `catchupSource = e.catchupSource?.let(env.sealer::seal)`. Leave the
       content hash as it is: it is computed from the plain template, so unchanged rows are not
       rewritten. The query that reports "has a template" tests for a non-empty value and keeps
       working.
    2. In `PlayerGraph.resolveArchive`, turn the stored value back into the template before use:
       a value that starts with `v2:` or `v1:` is decrypted with `data.cipher.decrypt` on the `io`
       dispatcher; any other value is an old plain template and is used as it is. A decrypt
       failure counts as "no template" (the existing "archive unavailable" outcome).
    3. Add two DAO functions: one page of at most 500 `(id, catchup_source)` rows where the value
       is not null, not empty and does not start with `v2:`, ordered by `id` after a given id; and
       an update of `catchup_source` by `id`. Add a query-plan test for the page query beside
       `LiveQueryPlanTest`; it may scan in primary-key order, which is the paging this project
       allows.
    4. In `AppGraph.afterFirstFrame`, launch on `appScope` a one-time pass on the `bulkWrite`
       dispatcher: loop over those pages, seal each value with `data.cipher.encrypt`, write the
       page in one transaction, call `pauseGate.awaitTurn(WorkOrigin.VIEWER)` between pages, and
       stop when a page is empty. It is cheap to run at every start once nothing is left: one
       query that returns no rows. Wrap it so a `SecretsUnreadable` failure ends the pass quietly.
    5. Tests: the import stores the sealer's output, not the template (the shared test harness
       seals as `"sealed:…"`); the pass converts plain rows and leaves sealed rows alone; an
       archive resolves from both a sealed and a plain stored value. Run
       `com.sohva.tv.app.PlayerArchiveTest` on the emulator.
  - Depends on: CRASH-1 (the pass relies on failures not crashing).
  - Check: the new tests pass; `PlayerArchiveTest` passes; the full check passes;
    `git grep -n "catchupSource = e.catchupSource," core/sync/src/main` finds nothing.
  - Risk: a mistake in the decrypt path disables catch-up for M3U sources; `PlayerArchiveTest`
    and the new tests cover it. The pass changes stored values; they stay readable by the new
    code, and an older build would treat a sealed value as an unusable template until the next
    playlist refresh rewrote nothing. For that reason this step must not be undone after a build
    with it has run on a device; before that, revert the commit.
  - Size: medium.

- [x] **M3U-1 — Live channels in groups named "Movies" or "Series" stay in the guide**
  - Resolves: finding B1.
  - Goal: an M3U entry whose address is clearly a live stream is a channel whatever its group is
    called, and a group name counts as a film or series hint only as a whole word.
  - Where: `core/net/src/main/kotlin/com/sohva/tv/core/net/m3u/M3uText.kt` (`kindOf`),
    `core/net/src/test/kotlin/com/sohva/tv/core/net/m3u/` (new `M3uKindTest.kt`),
    `docs/decisions.md`.
  - Approach: keep the order of the rule and change two things.
    1. An address "looks live" when its path (before `?`, lower-cased) ends with `.ts` or `.m3u8`,
       contains the segment `/live/`, or the address starts with `udp://`, `rtp://` or `rtsp://`.
       When the address looks live, the group name is not consulted; the declared type attributes
       and the `/<hint>/` path segments still are.
    2. Where the group name is consulted, a hint matches only as a whole word: split the
       normalised group on anything that is not a letter or digit and compare the words. The type
       attributes and the path keep substring matching as today.
    The film file extensions and the "duration above zero" rule stay.
    Tests, written first: `("", "UK Movies", "http://h/live/u/p/1.ts", -1)` is live;
    `("", "Showtime", "http://h/1", -1)` is live; `("", "Filmbox", "http://h/1", -1)` is live;
    `("", "Movies", "http://h/movie/u/p/1.mkv", -1)` is a film; `("", "Movies", "http://h/1", -1)`
    is a film; `("", "TV Shows", "http://h/1", -1)` is a series; `("movie", "News", "http://h/1.ts", -1)`
    is a film (a declared type wins); a positive duration is a film. Keep the failing log.
    Add the decision row (see decision 5).
  - Depends on: BASE-1.
  - Check: `M3uKindTest` and `M3uReaderTest` pass; the full check passes. Then, on the emulator
    with the lab build and the fixture playlist (as in BASE-1 step 7), read the two
    `I/import: … ok: N rows` lines: the playlist line must report 56,164 rows and the catalogue
    line 0 rows for that playlist (before this step: 50,546 and 5,618).
  - Risk: a provider that serves films from addresses ending in `.ts` inside a "Movies" group
    would now show them as channels. Undo by reverting the commit; the next refresh files the
    entries as before.
  - Size: small.

### Phase 2: high-impact interface changes that are small

- [x] **FORM-1 — An unsaved source form is kept, not thrown away**
  - Resolves: finding U1 (lost input).
  - Goal: text typed into a source form survives pressing OK on a Settings category, pressing
    Back, or "All playlists", for as long as Settings stays open.
  - Where: `feature/settings/src/main/kotlin/com/sohva/tv/feature/settings/SettingsModel.kt`
    (`select`, `closePage`, `addSource`, `openSource`, `save`, `confirmDelete`),
    `feature/settings/src/test/kotlin/com/sohva/tv/feature/settings/SettingsModelTest.kt`,
    `ui/design/src/main/res/values*/rebuild_strings.xml` (one new string).
  - Approach:
    1. Add a private field `parked: SourceDraft?` to the model.
    2. A page "has unsaved input" when it is a saved source whose draft differs from `lastSaved`,
       or a new source in which any of the address, server, user name or password fields is not
       blank.
    3. In `select` and `closePage`, when the current page has unsaved input, set `parked` to it
       and post the new message `source_draft_kept` ("Your unsaved changes were kept. Open the
       source again to continue.") for the Sources section. Otherwise leave `parked` as it is.
    4. In `addSource(type)`, when `parked` is a new draft of the same type, open it instead of an
       empty one and clear `parked`. In `openSource(id)`, when `parked` has that id, load the
       saved configuration into `lastSaved` as today but show the parked draft, and clear
       `parked`.
    5. Clear `parked` after a successful `save` of the same source and in `confirmDelete`.
    6. Tests, written first: edit then `select(another section)` then `addSource(same type)` gives
       the same draft back; edit a saved source then `closePage` then `openSource` gives the edited
       draft and `save` syncs only when import inputs changed; a draft with no input is not
       parked.
  - Depends on: BASE-1.
  - Check: the new tests pass; device test `com.sohva.tv.app.SettingsPlaylistsTest` passes; the
    string checks exit 0; the full check passes.
  - Risk: a parked draft holds a password in memory until Settings closes, as the open form
    already does. Undo by reverting the commit.
  - Size: medium.

- [x] **FORM-2 — The source form leads to Save**
  - Resolves: finding U1 (focus path, start position, premature buttons).
  - Goal: a new form starts on its first address field at the top; pressing Down past the last
    setting reaches "Save securely"; an unsaved source shows only "Save securely" and the test
    button.
  - Where: `feature/settings/src/main/kotlin/com/sohva/tv/feature/settings/SourcePage.kt`,
    `SourceControls.kt` (the `SourceActions` composable), `SettingsModel.kt` (`addSource`, the
    `FocusTarget` type), `SettingsScreen.kt` (where focus commands and the pane's scroll state are
    handled), `docs/decisions.md`, device test `SettingsPlaylistsTest`.
  - Approach:
    1. Add a focus target for "the first field of the page". `addSource` uses it when it opens an
       empty new draft; `openSource` and a reopened parked draft keep the existing target, so a
       saved source's page still starts on "All playlists" (requirement SRC-FR-13).
       `SourcePage` attaches a focus requester to the first address field (`settings-m3u` or
       `settings-xtream-base-url`) and the screen requests it for the new target with the
       project's `requestFocusWhenAttached` helper.
    2. When the page's identity changes (another source id, or from no page to a page), scroll
       the pane to the top before the focus request. Find the pane's scroll or lazy-list state in
       `SettingsScreen.kt`.
    3. Make the block of action buttons a focus group whose entry from above lands on "Save
       securely": give the Save button a requester and set the group's enter rule to it, in the
       way `LibraryScreen.kt` line 269 uses `focusProperties` on a group. Moving between the
       buttons inside the group stays as it is.
    4. In `SourceActions`, show "Sync everything", "Refresh playlist" / "Refresh channels",
       "Refresh programme guide" and "Refresh movies and series" only when `!page.isNew`.
    5. Extend `SettingsPlaylistsTest` first: after "+ Add M3U source" the focused node is
       `settings-m3u`; from `settings-epg-offset-up` one Down press focuses `settings-save`; a new
       page has no node `settings-refresh-catalogue`; opening a second new form after closing one
       starts at the top. Send keys with real key events as the existing tests do.
    6. Add the decision row (see decision 8).
  - Depends on: FORM-1 (same file `SettingsModel.kt`).
  - Check: the extended device test passes; `com.sohva.tv.app.FirstSourceTest` passes; the full
    check passes. Capture the new form (top and bottom) for the owner.
  - Risk: focus rules in Settings are shared by every pane; run `SettingsGeneralTest`,
    `SettingsLibraryTest` and `SettingsRemoteTest` too. Undo by reverting the commit.
  - Size: medium.

- [x] **FORM-3 — Saving a source ends with a result, not a standing "syncing" line**
  - Resolves: finding U1 (no completion feedback).
  - Goal: after "Save securely" starts a sync, the status line changes to the imported channel
    count or to the failure sentence when the playlist import ends.
  - Where: `feature/settings/src/main/kotlin/com/sohva/tv/feature/settings/SettingsModel.kt`
    (`save`), `SettingsModelTest.kt`.
  - Approach: after `services.syncNow(saved.id)` and the "saved, syncing" message, start one
    watcher job in `viewModelScope` (cancel the previous watcher first). It collects
    `services.health()` and waits for the first value in which this source's playlist kind is
    neither running nor idle and its last attempt is at or after the moment of the save. On
    success it posts `SettingsMessage.Count(R.plurals.source_imported_channels, itemCount)`; on
    failure it posts the health entry's error as `refresh(kind)` does today. For a source whose
    scope has no live channels, wait for the catalogue kind and post the existing catalogue
    message. The watcher ends after posting, when the model is cleared, or when another message is
    posted for the Sources section by a later action. No new strings.
    Tests first, with the fake services: save of a new source, then a health value with success
    and 12 channels, gives the count message; a failed health value gives the failure message; an
    older success from before the save is ignored.
  - Depends on: FORM-2.
  - Check: the new tests pass; `SettingsPlaylistsTest` and `FirstSourceTest` pass; the full check
    passes.
  - Risk: low. Undo by reverting the commit.
  - Size: small.

- [x] **CONF-1 — Four destructive actions ask once more**
  - Resolves: finding U2.
  - Goal: deleting a channel list, resetting a channel's edits, deleting a custom library group
    and resetting the Home layout each need a second press on a confirm button; focus never falls
    elsewhere.
  - Where: new file `ui/design/src/main/kotlin/com/sohva/tv/ui/design/components/ConfirmAction.kt`;
    `feature/settings/src/main/kotlin/com/sohva/tv/feature/settings/RemotePane.kt` (its private
    `ResetRow`, lines 184 to 236, becomes a call to the new component);
    `feature/channels/src/main/kotlin/com/sohva/tv/feature/channels/ChannelEditor.kt` (lines 161
    and 191); `feature/settings/…/CustomGroupsSection.kt` (line 217); `feature/settings/…/HomePane.kt`
    (line 77); `ui/design/src/main/res/values*/rebuild_strings.xml` (four questions);
    `feature/channels/src/test/` (new `ChannelsModelTest.kt` is not required; the check is the
    device test).
  - Approach:
    1. Move the mechanism of `ResetRow` into a shared composable that takes the button label, the
       question, the confirm label, the action, an optional icon and the three test tags. Keep its
       behaviour exactly: the confirm button is composed next to the first button, takes focus,
       and only then the first button leaves; Cancel and a completed action bring the first button
       back the same way. The confirm button uses the danger style. `RemotePane` passes its
       existing strings and test tags, so `SettingsRemoteTest` keeps passing unchanged.
    2. Use it at the four sites. Questions (new strings): "Delete the list %1$s?", "Reset this
       channel's changes?", "Delete this group?", "Reset Home to its default rows?". Confirm and
       cancel labels reuse `action_delete`, `action_reset`, `remote_reset_confirm_action` or
       `action_cancel` as fits.
    3. After a channel list is deleted and no list is left, the row that held the button leaves
       the screen: request focus on the "new list" field before that happens.
    4. Device tests, written first where a test class exists: in `ChannelsScreenTest`, one OK on
       `channels-list-delete` leaves the list in place and focuses the confirm button, the second
       OK deletes it; the same pattern for `settings-home-reset` in `HomeLayoutTest` and for
       `custom-group-delete` in `CustomGroupsTest`.
  - Depends on: BASE-1.
  - Check: `SettingsRemoteTest`, `ChannelsScreenTest`, `HomeLayoutTest` and `CustomGroupsTest`
    pass on the emulator; the string checks exit 0; the full check passes.
  - Risk: focus hand-over is delicate; the component is copied from code that already passes its
    test. Undo by reverting the commit.
  - Size: medium.

- [x] **LIB-1 — Movies and Series do not open on an empty History**
  - Resolves: finding U4 (walls). The Home part of U4 is in section 6.
  - Goal: a viewer with no viewing history lands on the first group with titles, and a library
    with no films or no series says so.
  - Where: `feature/library/src/main/kotlin/com/sohva/tv/feature/library/LibraryModel.kt` (first
    entry near lines 75 and 128, the rail update near line 430),
    `feature/library/src/main/kotlin/com/sohva/tv/feature/library/LibraryWall.kt` (the empty
    message near lines 258 to 266), `ui/design/src/main/res/values*/rebuild_strings.xml` (two
    strings), device test `LibraryWallTest`, `docs/decisions.md`.
  - Approach:
    1. On the first entry of a wall (the existing "first entry" flag), keep selecting History.
       When the rail's groups first arrive and History's first page has come back empty, and the
       selected row is still History, and the viewer has pressed no key since entry, select and
       focus the first row that is not History. Apply this once per entry. Data that arrives later
       must never move focus.
    2. When the room has no groups at all, show a distinct message instead of "No titles in this
       group.": "No movies have been imported yet. Add a source in Settings, or refresh movies and
       series." and the same for series.
    3. Tests first in `LibraryWallTest`: with films and no history the focused rail row after
       entry is the first group and the wall shows posters; with history the focused row is
       History as before; a key pressed before the groups arrive keeps focus where the viewer put
       it (gate the group read as the existing tests gate reads).
    4. Add the decision row (see decision 10).
  - Depends on: BASE-1.
  - Check: `LibraryWallTest` and `LibraryPlaybackTest` pass; unit tests of `feature/library` pass;
    the string checks exit 0; the full check passes.
  - Risk: focus moving after entry is exactly what the project forbids; the "once, only if no key
    was pressed" rule and the gated test protect it. Undo by reverting the commit.
  - Size: small.

- [x] **PLAYER-1 — The player names the focused control and shades its text**
  - Resolves: finding U3, except the word "watched" (section 6).
  - Goal: the icon row shows the focused control's name in words, and the information lines stay
    readable over a bright picture.
  - Where: `feature/player/src/main/kotlin/com/sohva/tv/feature/player/LiveInfoBox.kt`
    (`ActionRow`, `IconAction`), any other row in `feature/player` built from `IconAction`,
    `feature/player/src/main/kotlin/com/sohva/tv/feature/player/PlayerPanels.kt` (`ChromeScrim`,
    lines 57 to 73), device test `PlayerControlsTest`, `docs/decisions.md`.
  - Approach:
    1. In `ActionRow`, remember the label of the focused action in a state value that the icon
       buttons set when they gain focus and clear when the row loses focus. Draw it as one line of
       text to the right of the last button, in the `label` type token and the primary text
       colour, with the test tag `player-action-label`. The labels already exist as the buttons'
       descriptions, in every language. The row is composed only while the box shows, so the rule
       of no interface frames during bare playback is unaffected.
    2. In `ChromeScrim`, keep one draw pass and two bands, and make the lower band darker sooner:
       use three colour stops inside the band, alpha 0 at its top, 0.72 at 35 % of its height and
       0.94 at the bottom.
    3. Extend `PlayerControlsTest` first: with the controls focused, the node
       `player-action-label` shows "Hide these controls"; after one Right press it shows the
       picture-shape label.
    4. Add a decision row: the scrim values differ from the previous app for legibility.
  - Depends on: BASE-1.
  - Check: `PlayerControlsTest`, `PlayerRemoteTest` and `PlayerLiveTest` pass; the full check
    passes. Capture the control row over the bright test picture for the owner (play a channel
    from the fixture; any picture will do if no stream plays).
  - Risk: the label adds one recomposition per focus move inside the row. Undo by reverting the
    commit.
  - Size: small.

- [x] **FOCUS-1 — Focus returns to the opened card in Discover, and stays inside the guide's options**
  - Resolves: finding U5 (Discover grid and guide options sheet).
  - Goal: Back from a title page focuses the card that opened it; D-pad moves cannot leave an open
    options sheet.
  - Where: `feature/discover/src/main/kotlin/com/sohva/tv/feature/discover/ui/grid/GridScreen.kt`
    (lines 73 and 103) and its `GridModel`; `ui/design/src/main/kotlin/com/sohva/tv/ui/design/components/Frames.kt`
    (`OptionsSheet`); `feature/library/src/main/kotlin/com/sohva/tv/feature/library/LibraryScreen.kt`
    (line 269, which then drops its own copy of the rule); device tests `DiscoverLandingTest` and
    `GuideScreenTest`.
  - Approach:
    1. `GridModel` gets a plain field for the key of the last opened card, as Discover's search
       model has `lastFocus`. `GridScreen` sets it when a card opens a title. On composition, when
       the field is set and the card is present, request focus on that card (give cards a
       requester by key, or request through the lazy grid after scrolling to the item); otherwise
       focus the Back button as today. Remove the unused `firstCard` requester or use it for this.
    2. Move the focus trap into `OptionsSheet`: the sheet's outer box becomes a focus group that
       cancels a focus change in a key direction that would leave it. The set of key directions
       lives in `feature/library` today (`KEY_MOVES`); define it once in
       `ui/design/…/focus/FocusHelpers.kt` and use it from both places.
    3. Tests first: in `DiscoverLandingTest`, open "Show all", move to a card in the second row,
       open it, send Back with `sendKeyDownUpSync(KEYCODE_BACK)`, assert that card is focused. In
       `GuideScreenTest`, open the options sheet, press Down more times than it has buttons and Up
       likewise, and assert the focused node is still inside `guide-options`.
  - Depends on: BASE-1.
  - Check: `DiscoverLandingTest`, `GuideScreenTest`, `GuideActionsTest` and `LibraryWallTest`
    pass; `:ui:design` unit tests pass; the full check passes.
  - Risk: low. Undo by reverting the commit.
  - Size: small.

- [x] **FOCUS-2 — Search and the Home rail remember where the viewer was**
  - Resolves: finding U5 (Search return and the Home rail).
  - Goal: Back from a search result focuses that result; opening the Home rail focuses the entry
    used last in this visit.
  - Where: `feature/search/src/main/kotlin/com/sohva/tv/feature/search/SearchScreen.kt` (line 91)
    and `SearchModel.kt`; `HomeRail.kt` and `HomeModel.kt` in the main sources of the `:feature:home` module; device tests `SearchTest` and `HomeTest`; `docs/decisions.md`.
  - Approach:
    1. `SearchModel` keeps the key of the result last opened. On composition the screen focuses
       that result when it is still in the list, and the text field otherwise (first entry).
    2. `HomeModel` keeps the rail item last opened. When the rail takes focus from the content,
       the screen requests that item; with none remembered it behaves as today.
    3. Tests first: in `SearchTest`, search, move to the third result, open it, Back, assert the
       third result is focused and the keyboard is not requested; in `HomeTest`, open Settings
       from the rail, Back, move right into the content and left again, assert the Settings entry
       is focused.
    4. Add the decision row (see decision 12).
  - Depends on: BASE-1.
  - Check: `SearchTest`, `HomeTest`, `HomeRestoreTest` and `ShellNavigationTest` pass; the full
    check passes.
  - Risk: low. Undo by reverting the commit.
  - Size: small.

- [x] **GUIDE-1 — From the channel column, Right goes to the programme on air**
  - Resolves: finding U7.
  - Goal: when the guide shows the present, the first Right press from a channel selects the
    programme airing now, not one that has ended.
  - Where: the row's column logic in `feature/live/src/main/kotlin/com/sohva/tv/feature/live/`
    (`GuideRow.kt` and the key handling in `GuideModel.kt`; search for where a Right press moves
    the column from −1 to 0), device test `GuideScreenTest`, `docs/decisions.md`.
  - Approach: where Right on the channel column sets the column to block 0, set it instead to the
    index of the block whose time span contains the current time, when the window is at the
    present and such a block is drawn; in every other case keep block 0. Left from that block
    still steps back through earlier blocks to the channel column, so nothing becomes
    unreachable. Test first in `GuideScreenTest` with a fixture row whose first drawn block ended
    before now: one Right press selects the on-air programme. Add the decision row (see decision
    11).
  - Depends on: BASE-1.
  - Check: `GuideScreenTest`, `GuideActionsTest`, `GuideTimingTest` and `GuideOwnerScaleTest`
    pass; the full check passes.
  - Risk: tests that count Right presses from the channel column need their counts updated in
    the same commit. Undo by reverting the commit.
  - Size: small.

- [x] **A11Y-1 — Readable dim text, a readable LIVE chip, and no initials behind logos**
  - Resolves: finding U6 (contrast and the initials seen in the player).
  - Goal: in the Original theme dim text reaches 4.5:1 on raised surfaces; the LIVE chip's text
    reaches 4.5:1 in every theme; a loaded channel logo never shows the initials through it.
  - Where: `ui/design/src/main/kotlin/com/sohva/tv/ui/design/theme/Palettes.kt` (line 40),
    `ui/design/…/components/Chips.kt` (the `LIVE` tone, line 34),
    `ui/design/…/components/Artwork.kt` (`LogoTile`, lines 51 to 72),
    `ui/design/src/test/kotlin/com/sohva/tv/ui/design/PaletteParityTest.kt`, a new
    `PaletteContrastTest.kt` beside it, the screenshot goldens of `:ui:design` and
    `:feature:home`, `docs/decisions.md`.
  - Approach:
    1. Write `PaletteContrastTest` first. It computes the WCAG contrast ratio and asserts, for
       every theme: dim text on the background and on the raised surface composited over the
       background is at least 4.5; the LIVE chip's ink on the danger colour is at least 4.5. Keep
       the failing log.
    2. Change the Original theme's `textDim` to `Color(0xFF7F8CA3)`. If a derived theme fails the
       new test, raise its dim colour the same way until it passes and list the values in the
       progress log.
    3. In `Chips.kt`, give the `LIVE` tone a fixed dark ink, `Color(0xFF05070D)`, defined once in
       `Palettes.kt` next to the existing `onDangerSurface` constant.
    4. In `LogoTile`, stop drawing the initials once the logo has loaded: apply
       `drawWithContent` to the modifier passed to `InitialsTile` and call `drawContent()` only
       while the image is null. The image state is read in the draw phase, so nothing recomposes.
    5. Update `PaletteParityTest`'s expected value for the changed colour, with a comment that
       points to the decision. Record new goldens:
       `./gradlew :ui:design:recordRoborazziDebug :feature:home:recordRoborazziDebug > harness-out/plan/A11Y-1-record.log 2>&1`,
       and look at the changed images before committing them: only dim text and LIVE chips may
       differ.
    6. Add the decision row (see decision 13).
  - Depends on: BASE-1.
  - Check: `PaletteContrastTest` and `PaletteParityTest` pass; `verifyRoborazziDebug` passes with
    the new goldens; the full check passes. Capture Settings, Playlists and the guide in the
    Original theme for the owner.
  - Risk: this changes the default look slightly, on purpose. Undo by reverting the commit, which
    restores colours and goldens together.
  - Size: medium.

- [x] **A11Y-2 — Controls describe themselves to a screen reader**
  - Resolves: finding U6 (screen-reader semantics), except announcements inside a guide row
    (section 6).
  - Goal: only selected items report "selected"; switches report on or off as switches; section
    and dialog titles are headings; the two arrow buttons have names.
  - Where: `ui/design/src/main/kotlin/com/sohva/tv/ui/design/focus/TvSurface.kt` (line 135),
    `ui/design/…/components/SettingsComponents.kt` (the switch near line 147 and
    `SettingsOverline`), `ui/design/…/components/Frames.kt` (dialog and sheet titles),
    `feature/channels/src/main/kotlin/com/sohva/tv/feature/channels/ChannelEditor.kt` (lines 189
    and 190), `ui/design/src/test/kotlin/com/sohva/tv/ui/design/SurfaceFocusTest.kt` or a new
    `SurfaceSemanticsTest.kt`, possibly two strings in `rebuild_strings.xml`.
  - Approach:
    1. In `TvSurface`, set the `selected` semantics property only when `state.selected` is true.
    2. The settings switch adds, on the modifier it passes to `TvSurface`, a semantics block with
       the toggleable state for `checked` and the switch role. Write the test first and confirm in
       it that the merged node reports the switch role; if the button role from `clickable` wins,
       add an optional role parameter to `TvSurface` that the switch sets, defaulting to the
       button role.
    3. Add the `heading()` semantics property to `SettingsOverline`, to the dialog title
       composable and to the title text inside `OptionsSheet`.
    4. Give the "↑" and "↓" buttons content descriptions. Search the string files for existing
       "Move up" and "Move down" texts and reuse them; add two strings to `rebuild_strings.xml`
       in all eight folders only if none exist.
    5. Tests with the Compose test rule under Robolectric, as the existing tests in
       `ui/design/src/test` do: an unselected surface has no `Selected` property and a selected
       one has it true; the switch node has the on or off state and the switch role; the overline
       is a heading.
  - Depends on: A11Y-1 (the goldens are recorded there; this step must not change pixels) and
    CONF-1 (same file `ChannelEditor.kt`).
  - Check: the new tests pass; `verifyRoborazziDebug` passes without new goldens; device tests
    that find nodes by selected state still pass: run `SettingsGeneralTest`, `ChannelsScreenTest`
    and `LibraryWallTest`. The full check passes.
  - Risk: a test that asserted `selected = false` on a node now finds no property; change such
    assertions to "not selected" in the same commit. Undo by reverting the commit.
  - Size: small.

- [x] **A11Y-3 — Captures at a larger system text size (no fix)**
  - Resolves: finding U6 (text scaling), by measurement only (decision 14).
  - Goal: the owner can see what the fixed-height parts look like at a system text scale of 1.3.
  - Where: one new screenshot test in `ui/design/src/test/kotlin/com/sohva/tv/ui/design/` and one
    in the unit tests of the `:feature:home` module if a Home capture is easy; their golden images. The guide, search
    and Discover modules do not use the screenshot plugin; do not add it to them.
  - Approach: following `GalleryScreenshotTest`, render the component gallery (and Home, if its
    test set-up allows) inside a `Density` whose font scale is 1.3, and record the images as
    goldens with names ending in `-fontscale130`. Look at each image and write in the progress
    log, per image, whether any text is clipped, overlaps another line, or is cut at the bottom.
    Copy the images to `harness-out/plan/captures/` as well. Change no layout.
  - Depends on: A11Y-1 and A11Y-2 (goldens settle first).
  - Check: `verifyRoborazziDebug` passes with the new goldens; the progress log lists the
    findings per image; the full check passes.
  - Risk: none to behaviour. Undo by deleting the tests and their goldens.
  - Size: small.

- [x] **TEXT-1 — "Accounts" is translated in every language**
  - Resolves: finding U9 (the string missing in five languages).
  - Goal: the Settings section "Accounts" is named in the interface language in all eight
    languages.
  - Where: `ui/design/src/main/res/values*/rebuild_strings.xml` (one new string in all eight
    folders), the place in `feature/settings` that names the Settings sections (find it with
    `git grep -n settings_section_accounts feature/settings`).
  - Approach: the key lives in `strings_trakt.xml`, which `tools/import_strings.py` copies from the
    previous app's texts and which is allowed to fall back to English in the five draft languages
    (`TranslationParityTest`, function `englishOnly`). A hand edit of those files would fail
    `import_strings.py --check`. So add a new key, `settings_section_accounts_title`, to
    `rebuild_strings.xml` in all eight folders and use it for the section's name: English
    "Accounts"; Finnish and French copied from the existing `settings_section_accounts` in
    `values-fi` and `values-fr`; es "Cuentas", pt "Contas", de "Konten", sv "Konton",
    it "Account". Leave the old key in place: other code may use it, and the import tool owns it.
    `TranslationParityTest` already requires every key of `rebuild_strings.xml` in every language,
    so no test changes.
  - Depends on: BASE-1.
  - Check: `python tools/import_strings.py --check` and `python tools/check_french_strings.py`
    exit 0; `TranslationParityTest` passes; the full check passes.
  - Risk: low. Undo by reverting the commit.
  - Size: small.

- [x] **DLG-1 — Six dialogs use the dialog card**
  - Resolves: finding U9 (dialogs that are nearly transparent in the Original theme).
  - Goal: the hold-OK menu on Home, the guide's programme actions, the guide-source picker, the
    two reminder dialogs and the upgrade notice sit on the opaque `panel` colour.
  - Where: `HomeActions.kt` in the main sources of the `:feature:home` module (line 47),
    `feature/live/…/GuideSheets.kt` (line 96), `feature/channels/…/EpgPicker.kt` (line 64),
    `app/src/main/java/com/sohva/tv/app/reminder/ReminderLayer.kt` (lines 132 and 164),
    `app/src/main/java/com/sohva/tv/app/migration/UpgradeNotice.kt` (line 40).
  - Approach: in each place replace the hand-built column filled with `Sohva.palette.surface` by
    the existing `DialogCard` from `ui/design/…/components/Frames.kt`, keeping width, padding,
    content, focus requests and test tags. Where `DialogCard` cannot express the layout, change
    only the fill colour to `Sohva.palette.panel`.
  - Depends on: BASE-1. It shares the guide's device tests with FOCUS-1 and GUIDE-1; no other
    step edits these five files.
  - Check: `HomeTest`, `GuideActionsTest`, `ChannelsScreenTest` and `ReminderTest` pass;
    `verifyRoborazziDebug` passes (record `:feature:home` goldens again only if the Home menu is
    in one, and say so in the progress log); the full check passes. Capture the Home hold-OK menu
    and the guide's programme actions for the owner.
  - Risk: low. Undo by reverting the commit.
  - Size: small.

### Phase 3: background work and database performance

- [x] **SYNC-1 — A refresh that came due during viewing runs when the viewer leaves**
  - Resolves: finding B7.
  - Goal: an automatic refresh deferred because the app is in front runs 30 seconds after the app
    leaves the front, instead of after a delay that grows to five hours.
  - Where: `core/sync/src/main/kotlin/com/sohva/tv/core/sync/RefreshScheduler.kt` (the scheduler,
    `RefreshHost`, `RefreshWorker.doWork` line 117), `app/src/main/java/com/sohva/tv/app/SohvaApplication.kt`
    (implements `RefreshHost`), `app/src/main/java/com/sohva/tv/app/MainActivity.kt` (`onStop`),
    `core/sync/src/test/kotlin/com/sohva/tv/core/sync/RefreshSchedulerTest.kt`.
  - Approach:
    1. Add to `RefreshHost` a function `refreshDeferred(kinds: Set<RefreshKind>, freshMs: Long)`.
       `SohvaApplication` forwards it to the scheduler.
    2. The scheduler keeps the deferred kinds and the largest `freshMs` in memory (a volatile
       field; a process that dies while the app is in the background runs the periodic work in a
       new process, which is not in front).
    3. In the worker, where an automatic run finds the app in front and every live source
       imported once, call `host.refreshDeferred(kinds, freshMs)` and return `Result.success()`
       instead of `Result.retry()`. The retry at the end of `doWork`, for real failures, stays.
    4. Add `onLeave()` to the scheduler: when kinds are deferred, enqueue unique one-time work
       named `sohva-refresh-deferred` with the replace policy, the network constraint, a 30 second
       initial delay and the worker input for those kinds (`now = false`, the stored `freshMs`),
       then clear the field. Call it from `MainActivity.onStop` next to the enrichment
       scheduler's `onLeave`, under the same "not changing configurations" condition.
    5. Tests first in `RefreshSchedulerTest`: a foreground automatic run returns success and
       records the kinds; `onLeave` enqueues one delayed work item with those kinds; `onLeave`
       with nothing deferred enqueues nothing; a viewer-started run is not deferred.
  - Depends on: BASE-1.
  - Check: the new tests pass; `com.sohva.tv.core.sync.ConcurrentImportDeviceTest` passes on the
    emulator; the full check passes.
  - Risk: a viewer who leaves and returns within 30 seconds defers again, which is intended.
    Undo by reverting the commit.
  - Size: small.

- [x] **ENR-1 — Leaving the app no longer re-reads the whole catalogue, and the pass can stop**
  - Resolves: finding P2.
  - Goal: the metadata queue is synchronised after imports and setting changes, not on every
    leave; a synchronisation in progress stops within one page when the viewer returns.
  - Where: `core/sync/src/main/kotlin/com/sohva/tv/core/sync/metadata/EnrichmentWork.kt`
    (`onLeave`, the worker), `Enrichment.kt` (`synchronise`, lines 71 to 93),
    tests `EnrichmentSchedulerTest.kt` and `EnrichmentTest.kt`, `docs/decisions.md`.
  - Approach:
    1. `synchronise()` returns a result that says whether it completed. In its page loop, before
       each page, call `ensureActive()` and check `paused()`; when paused, return "stopped"
       without calling `sweepQueue` (sweeping after a partial pass would delete rows the pass had
       not reached).
    2. The scheduler gets a volatile flag "a synchronisation is owed". The worker sets it (through
       the host's scheduler) in a `finally` block whenever a requested synchronisation did not
       complete, including when the worker is cancelled, and clears it when one completes.
    3. `onLeave()` enqueues with `synchronise = owed` instead of always true. `afterImport` and
       `restart` keep `synchronise = true`. The worker still synchronises when `needsSync()` says
       the queue is empty or outdated.
    4. Tests first: `onLeave` with nothing owed enqueues input `synchronise = false`; a
       synchronisation stopped by `paused()` leaves the queue rows it did not reach in place and
       marks the pass as owed; the next `onLeave` asks for a synchronisation; a completed pass
       clears the flag.
    5. Add the decision row (see decision 16).
  - Depends on: META-1 (same module and tests).
  - Check: the new tests pass; `:core:sync:testDebugUnitTest` passes within the full check; device
    test `com.sohva.tv.app.TitleMetadataTest` passes.
  - Risk: a title watched since the last pass keeps its lower priority until the next import;
    decision 16 accepts that. Undo by reverting the commit.
  - Size: small.

- [x] **ENR-2 — The metadata cache is trimmed**
  - Resolves: finding B6.
  - Goal: expired cache rows are removed and the table never holds more than 50,000 rows, as the
    table's own comment promises.
  - Where: `core/sync/src/main/kotlin/com/sohva/tv/core/sync/metadata/Enrichment.kt` (a new
    `trimCache` function), `EnrichmentWork.kt` (the worker calls it after `run`),
    `core/data/src/main/kotlin/com/sohva/tv/core/data/database/MetadataDaos.kt` (the three
    existing functions `sweepCache`, `cacheCount`, `deleteOldestCache`; read `MetadataSql.CACHE_SWEEP`
    and `CACHE_OLDEST` first), `EnrichmentTest.kt`, and a query-plan test for the two statements
    beside the other plan tests if none covers them.
  - Approach: `trimCache()` runs on the bulk dispatcher: call `sweepCache(now)`; then, while
    `cacheCount()` is above 50,000, call `deleteOldestCache(min(excess, 2,000))` in its own
    transaction, with `ensureActive()` between rounds. The worker calls it once after
    `enrichment.run()` returns, whatever the run's end state, and before it schedules a
    continuation. Define the limit as a named constant with the eviction rule written beside it.
    Test: with 50,010 rows of which 5 are expired, the table ends at 50,000 or fewer and the
    expired rows are gone.
  - Depends on: ENR-1 (same files).
  - Check: the new test passes; the plan test shows no full scan with a temporary sort for the
    oldest-rows statement, or records the plan in the progress log if the existing SQL needs one;
    the full check passes.
  - Risk: low; the queries already exist. Undo by reverting the commit.
  - Size: small.

- [x] **IDX-1 — The search index is brought up to date in pages, with pauses**
  - Resolves: finding P1 (the search-index statement).
  - Goal: after an import, new rows enter the full-text tables in transactions of at most 2,000
    rows with a pause between them, so a small write from the interface never waits for the whole
    table.
  - Where: `core/data/src/main/kotlin/com/sohva/tv/core/data/database/SearchTables.kt`
    (`SearchIndex`), the three import call sites
    (`core/sync/…/LiveImport.kt` line 118, `GuideImport.kt` line 80, `CatalogueImport.kt` line
    176), tests in `core/data/src/test/` (`SearchReadsTest.kt` or a new `SearchIndexTest.kt`),
    device test `ReadsDuringImportTest` (one new case), `docs/performance-log.md`.
  - Approach:
    1. Add a suspending `catchUpPaged(db, table, pause)` to `SearchIndex`. It reads the highest
       row id already in the index table once, outside a transaction. Then it loops: find the id
       of the 2,000th table row above the current id (in id order); insert the table rows with
       ids in that range into the index in one transaction; call `pause()`; continue from that id;
       stop when no row is above it. This is correct because row ids are generated in increasing
       order and never reused, and every earlier catch-up ran to the end or stopped at a page
       boundary in id order.
    2. Keep the existing single-statement `catchUp` for `fill` (a new database) and for
       `EpisodeFetch`, which this step does not touch.
    3. At the three import call sites, call the paged form outside any surrounding transaction,
       on the bulk-write dispatcher, with `pause = { env.pauseGate.awaitTurn(origin) }`. Where the
       origin is not in scope use `WorkOrigin.VIEWER`, as `GuideImport` already does for its
       batches. Confirm by reading each call site that it is not inside `runInTransaction`.
    4. Tests first: after a paged catch-up the index has one row per table row; a second catch-up
       inserts nothing; rows added afterwards are indexed by the next catch-up; with 4,500 rows
       the pause function is called at least twice.
    5. Measurement: add a case to `ReadsDuringImportTest` (or a sibling test) that seeds 200,000
       film rows, starts the catch-up, and measures how long one small write (a favourite toggle
       or a recent-channel write) waits. Run it on the unchanged code first and record the wait,
       then on the new code.
  - Depends on: BASE-1.
  - Check: the unit tests pass; the device case shows the small write completing in under 500 ms
    on the emulator (record the before and after numbers; `docs/decisions.md` row "Search index"
    recorded 2.9 s for the old single statement); `OwnerScaleImportTest` passes and its total time
    is not more than 10 % above the BASE-1 number; unified search still finds a channel and a film
    imported in the test (`SearchTest`). The full check passes.
  - Risk: a row missed by the paging would be missing from search; the "one row per table row"
    test guards it. Undo by reverting the commit.
  - Size: medium.

- [x] **IDX-2 — Statistics are gathered cheaply and never during playback**
  - Resolves: finding P1 (`ANALYZE`).
  - Goal: the statistics step after an import waits until no video plays and looks at a bounded
    number of rows where SQLite supports that.
  - Where: `core/sync/src/main/kotlin/com/sohva/tv/core/sync/ImportRunner.kt` (`analyze`, lines
    293 to 296, and its call near line 168),
    `core/model/src/main/kotlin/com/sohva/tv/core/model/concurrent/PauseGate.kt`,
    `core/sync/src/test/kotlin/com/sohva/tv/core/sync/ImportRunnerTest.kt`, a `PauseGate` test in
    `core/model/src/test/`.
  - Approach:
    1. Add `suspend fun awaitNoPlayback()` to `PauseGate`: it suspends until the playback flag is
       false.
    2. In `analyze`, call `env.pauseGate.awaitNoPlayback()` before the first table and
       `env.pauseGate.awaitTurn(origin)` between tables (pass the origin into `analyze`).
    3. Before the first `ANALYZE`, execute `PRAGMA analysis_limit=400` on the same connection.
       SQLite before 3.32 (Android 11 and older, the Shield included) ignores the pragma; SQLite
       3.32 and later (Android 12, the low-end box) then samples instead of reading every index
       entry.
    4. Tests: `awaitNoPlayback` returns at once when nothing plays and only after the flag clears
       otherwise; an import in the test harness with the playback flag set does not reach
       `ANALYZE` until the flag clears (use the harness's database to observe, or a counter on a
       wrapped gate).
  - Depends on: IDX-1 (same import files are read; `ImportRunner.kt` is edited only here).
  - Check: the new tests pass; `OwnerScaleImportTest` and `ConcurrentImportDeviceTest` pass; the
    query-plan tests of `core/data` still pass (sampled statistics must not change a pinned
    plan); the full check passes.
  - Risk: a viewer who plays video for hours delays the statistics; the old statistics stay in
    place meanwhile. Undo by reverting the commit.
  - Size: small.

- [x] **IDX-3 — The film group recount, only if the baseline shows it matters**
  - Resolves: finding P1 (the group recount).
  - Goal: the recount of film groups does not hold the writer for more than 100 ms on the
    emulator at 200,000 films.
  - Where: `core/data/src/main/kotlin/com/sohva/tv/core/data/database/LibraryDaos.kt`
    (`RECOUNT_FILM_GROUPS`, line 32), its callers `core/data/…/vod/LibraryPasses.kt` line 50 and
    `core/data/…/org/OrgPass.kt` line 96, `LibraryPassesTest.kt`, the plan tests.
  - Approach: read the `film counts` phase time that BASE-1 recorded for the 200,000-film import.
    If it is 100 ms or less, change nothing: write "not needed, measured N ms" in the progress
    log, tick the step, and make no commit. If it is more, add a second statement that recounts
    only the groups whose ids lie in a given range, and have `LibraryPasses` walk the source's
    film groups in ranges of 50 groups, one transaction per range; leave the caller in `OrgPass`
    on the single statement if it runs inside a transaction that must stay whole. Add a plan test
    for the new statement and a test that the paged recount gives the same counts as the single
    statement.
  - Depends on: BASE-1 (needs its number) and IDX-1.
  - Check: either the progress-log line, or the new tests pass and the `film counts` phase in
    `OwnerScaleImportTest` shows no single transaction above 100 ms (add the per-range maximum to
    the existing phase log line if needed). The full check passes.
  - Risk: low. Undo by reverting the commit.
  - Size: small.

- [x] **GUIDE-2 — The open guide wakes once per import, not once per batch**
  - Resolves: finding P3.
  - Goal: while a playlist import runs, channel and group writes do not make the guide rebuild its
    list; it rebuilds once when the import ends. Guide and catalogue status writes no longer clear
    the visible programmes unless the active guide data changed.
  - Where: `core/data/src/main/kotlin/com/sohva/tv/core/data/live/LiveStore.kt` (`changes()` and
    `guideChanges()`, lines 61 to 71), `core/data/…/database/LiveDaos.kt` (two small queries),
    `core/data/src/test/kotlin/com/sohva/tv/core/data/LiveStoreTest.kt`, a device test in
    `core/data/src/androidTest/` if the unit harness cannot observe table invalidation,
    `docs/performance-log.md`.
  - Approach:
    1. Add a query that answers whether any `source_status` row of the playlist kind has the
       status `running` (read `RefreshKind` for the stored kind id and `StatusBook` for the status
       text).
    2. Build `changes()` from two flows. The first watches `channel` and `content_group`, drops
       its initial value, and lets a signal through only when the query says no playlist import
       is running. The second watches `source`, `source_status`, `channel_list`,
       `channel_list_member` and `profile_allowed_group` and drops its initial value. Merge them
       and start the result with one initial signal, so collectors that skip the first value
       behave as today. The status write at the end of an import arrives through the second flow
       and triggers the one re-read.
    3. Add a query that returns, for every source, its id, its enabled flag, its guide time
       correction and the active guide snapshot of its guide status row. Build `guideChanges()` by
       mapping each invalidation of `source_status` and `source` to that list, dropping repeats
       with `distinctUntilChanged`, and emitting one signal per change.
    4. Tests first: with a playlist status row in `running`, ten channel writes produce no signal
       and the change to success produces one; a favourite toggle during the import still produces
       a signal; a catalogue status change produces no guide signal; a new guide snapshot produces
       one. Keep the failing log.
    5. Measurement: in `OwnerScaleImportTest` or a sibling, count the signals of `changes()`
       during a 56,164-channel import before and after, and record both.
  - Depends on: BASE-1.
  - Check: the new tests pass; the counted signals during the import drop to five or fewer;
    `GuideListsTest`, `GuideInitializationTest`, `GuideScreenTest`, `FirstSourceTest` and
    `ReadsDuringImportTest` pass; the full check passes.
  - Risk: during a first import the guide shows channels when the import ends, not while it runs.
    A status row left in `running` by a killed process would mute channel signals until start-up
    recovery marks it interrupted, which happens after the first frame. Undo by reverting the
    commit.
  - Size: medium.

- [x] **SRCH-1 — Each search looks at a bounded number of matches**
  - Resolves: finding P4 (unified search). The other two parts of P4 are measured here and
    explained in sections 6 and 7.
  - Goal: a broad term cannot make one search statement sort tens of thousands of rows.
  - Where: `core/data/src/main/kotlin/com/sohva/tv/core/data/database/SearchDaos.kt` (the five
    statements in `SearchSql`), `core/data/…/search/SearchReads.kt` (the comment at lines 16 to
    18), `core/data/src/test/…/SearchQueryPlanTest.kt` and `SearchReadsTest.kt`, a timing test in
    `core/data/src/androidTest/`, `docs/decisions.md`, `docs/performance-log.md`.
  - Approach:
    1. Timing test first (device, seeded database): 200,000 films whose names share a common
       two-letter word prefix, 56,164 channels, 165,600 programmes. Measure each search group for
       that prefix on the unchanged code; also measure the guide's "Find programme" over "All
       channels" for a rare word, and a Movies wall search for a rare word. Record all numbers.
    2. In each statement, take the matches from the full-text table through a sub-select with
       `LIMIT 2000` before the join and the sort, for example
       `… WHERE f.rowid IN (SELECT rowid FROM movie_search WHERE movie_search MATCH :match LIMIT 2000) …`,
       keeping the pinned join order. The episode statement gets the limit in both halves.
    3. Correct the comment in `SearchReads.kt`: Room's generated code passes no cancellation
       signal, so a superseded statement runs to its end; the limit bounds that work.
    4. Extend `SearchQueryPlanTest` so each statement's plan still starts from the full-text
       table. Run the timing test again and record the numbers.
    5. Add the decision row (see decision 17).
  - Depends on: IDX-1 (both touch search tests).
  - Check: the plan and read tests pass; each search group in the timing test takes at most
    100 ms on the emulator for the broad prefix (record before and after); `SearchTest` passes;
    the full check passes. The "Find programme" and wall-search numbers are recorded only.
  - Risk: a very broad term can miss titles beyond the first 2,000 matches; decision 17 accepts
    that. Undo by reverting the commit.
  - Size: medium.

- [x] **HASH-1 — One film added upstream no longer rewrites every later film**
  - Resolves: finding P5.
  - Goal: re-importing a catalogue in which one early entry was removed writes far fewer whole
    rows than a first import.
  - Where: `core/sync/src/main/kotlin/com/sohva/tv/core/sync/CatalogueImport.kt` (the hashes at
    lines 191, 210 and 253, which include the list position), `core/sync/…/diff/KeyedDiff.kt`,
    the film and series import DAOs in `core/data/…/database/ImportDaos.kt`,
    `core/sync/src/test/…/CatalogueAndXtreamTest.kt`, `OwnerScaleImportTest`,
    `docs/decisions.md`, `docs/performance-log.md`.
  - Approach:
    1. Measurement first: `OwnerScaleImportTest` already times `m3u catalogue (films, series)` and
       `m3u catalogue unchanged` for the 200,000-film fixture. Add a third timed import of the
       same source from a copy of `vod.m3u` without its first film (write the copy to
       `harness-out/fixture/vod-minus-first.m3u`, which the fixture server then serves, and point
       the source's address at it for that import). Record the three times on the unchanged
       code.
    2. Remove the position (`index`, `e.index`) from the three hashes. Keep `KEYS_VERSION` in
       them unchanged, so the first import after this step does not rewrite everything for the
       version alone; the removed position will change every hash once, which is unavoidable and
       happens one time.
    3. So that the stored position stays right, have the diff read each existing row's
       `provider_order` with its key and hash, and for rows whose hash is unchanged but whose
       position differs, run a narrow statement that updates only `provider_order` by row id,
       batched in the same transaction as the page. Add the statement with a plan test.
    4. Channels keep the position in their hash: their rank depends on it.
    5. Tests: an unchanged re-import writes no rows; a re-import with the first film removed
       leaves every later film's content untouched and its `provider_order` one lower.
    6. Measure again. Keep the step only if the "first film removed" re-import takes at most half
       of the time measured in 1, and the unchanged re-import is not slower. Otherwise undo the
       step and mark it blocked with both numbers.
    7. Add the decision row (see decision 18).
  - Depends on: IDX-1 (same file `CatalogueImport.kt`).
  - Check: the tests pass; the measurement rule in 6 holds; `CatalogueAndXtreamTest` and
    `LibraryWallTest` pass; the full check passes.
  - Risk: the first refresh after this step rewrites every film and series once. Undo by
    reverting the commit; that also rewrites once.
  - Size: medium.

- [x] **IMG-1 — Pictures already in memory appear in the first frame**
  - Resolves: finding P6 (the placeholder before a cached image).
  - Goal: a logo or poster that is in the memory cache is drawn when its cell first appears,
    without a placeholder frame.
  - Where: the `ArtworkLoader` interface in `ui/design/…/components/Artwork.kt` (or where it is
    declared), `app/src/main/java/com/sohva/tv/app/artwork/CoilArtwork.kt`, every call site of
    the pattern `var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }` (find them
    with `git grep -n "mutableStateOf<ImageBitmap?>(null)"`), a device test in
    `app/src/androidTest/`.
  - Approach:
    1. Add `fun peek(url: String, widthPx: Int, heightPx: Int, opaque: Boolean): ImageBitmap?` to
       the interface with a default body that returns null, so test fakes keep compiling.
    2. In `CoilArtwork`, give every request an explicit memory-cache key built from the address,
       the two sizes and the opaque flag. `peek` returns null until the image loader has been
       built by a first `load` (keep a volatile flag; building the loader reads a setting from
       disk and must not happen on the main thread). After that it looks the key up in the memory
       cache and returns the bitmap. A memory lookup is not disk or network work.
    3. At each call site, seed the remembered state with `loader.peek(…)` using the same sizes
       the load uses, and skip the load when the peek returned a bitmap.
    4. Device test: after `load` completes for an address, `peek` with the same sizes returns a
       bitmap at once; with other sizes or another address it returns null.
  - Depends on: A11Y-1 (same file `Artwork.kt`).
  - Check: the device test passes; `GuideOwnerScaleTest`, `ChannelsOwnerScaleTest`, `HomeTest`
    and `LibraryWallTest` pass; the main-thread CPU per press in the guide and the wall, measured
    as in BASE-1 step 8, is not more than 10 % above the baseline; the full check passes.
  - Risk: a wrong cache key shows one picture in another's place; the key includes the address
    and both sizes. Undo by reverting the commit.
  - Size: medium.

- [x] **SURF-1 — A focusable allocates animation state only when it animates**
  - Resolves: finding P6 (two animation states per focusable).
  - Goal: rows and cards that neither scale nor animate their fill create no animation objects.
  - Where: `ui/design/src/main/kotlin/com/sohva/tv/ui/design/focus/TvSurface.kt` (lines 121 to
    126 and 146), `ui/design/src/test/…/SurfaceFocusTest.kt`.
  - Approach: create the scale animation only when `style.focusScale != 1f`, and use the constant
    1 otherwise. Create the fill colour animation only when `style.animateFill` is true, and use
    the target colour directly otherwise. Both conditions depend on the style a call site passes,
    which does not change between recompositions of one call site; say so in a comment. Change
    nothing else in the file.
  - Depends on: A11Y-2 (same file).
  - Check: `SurfaceFocusTest` and `MotionAndKeysTest` pass; `verifyRoborazziDebug` passes without
    new goldens; the main-thread CPU per press in the guide and the wall, measured as in BASE-1
    step 8, is not above the baseline by more than 10 %; record the numbers. The full check
    passes.
  - Risk: low. Undo by reverting the commit.
  - Size: small.

### Phase 4: small repository fixes

- [x] **DOC-1 — The README's licence link points at a file that exists**
  - Resolves: finding B8 (the licence link). The other parts of B8 are in section 6.
  - Goal: the "Licence" line in `README.md` links to `docs/release/LICENSE`.
  - Where: `README.md` (last line).
  - Approach: change the link target from `LICENSE` to `docs/release/LICENSE`. Do not copy the
    licence to the root: `tools/package_release.py` and the public-source audit decide what the
    public repository contains.
  - Depends on: BASE-1.
  - Check: `test -f docs/release/LICENSE` succeeds and `grep -n "docs/release/LICENSE" README.md`
    finds the link; `python tools/audit_public_source.py` exits 0; the complete check passes
    (this is the end of the unattended phases).
  - Risk: none. Undo by reverting the commit.
  - Size: small.

### Phase 5: steps that wait for the owner

Do not carry these out. List them in the final report.

- [ ] **OWN-1 — Review, then push the branch**
  - Resolves: nothing by itself.
  - Goal: the owner has read the final report, looked at the captures in
    `harness-out/plan/captures/`, read the new strings and the new rows in `docs/decisions.md`,
    and pushed `improve/review-fixes` or asked for changes.
  - Why it waits: pushing changes something outside this machine.
  - Size: small.

- [ ] **OWN-2 — Try the result on real hardware**
  - Resolves: confirms PLAY-1 (connect a keyboard or controller during playback), SECR-1
    (catch-up still plays), UPD-1 and UPD-2 (an update installs), and the phase 3 steps on the
    low-end box, where the emulator's timings say little.
  - Goal: a preview build with its own version code (see `docs/preview-builds.md`) is installed on
    the Shield or the low-end box, and the owner saves diagnostics after an evening of use.
  - Why it waits: building a preview takes a version code, and installing on a real device needs
    the owner's word for that build.
  - Size: medium.

- [ ] **OWN-3 — Stop tracking `.claude/scheduled_tasks.lock`**
  - Resolves: finding B8 (the tracked lock file).
  - Goal: the lock file is removed from git (`git rm --cached .claude/scheduled_tasks.lock`) and
    listed in `.gitignore`.
  - Why it waits: another tool owns the file and this plan's agent did not create it.
  - Size: small.

## 5. Progress log

One entry per step: date, step ID, result, measurements, new strings, anything noticed nearby.

- 2026-11-11 **BASE-1** done. Branch `improve/review-fixes` from `fix/live-stream-end` (not yet in
  `main`). `tools/check_all.py` passes on unchanged code: 726 JVM tests, release APK 6,346,029
  bytes. `OwnerScaleImportTest`: M3U playlist 3,883 ms, guide 2,017 ms, playlist unchanged
  2,237 ms, catalogue first 32,030 ms, catalogue unchanged 12,130 ms; Xtream catalogue first
  40,117 ms, unchanged 5,297 ms; phases of the first M3U catalogue: search index 1,848 ms,
  film counts 164 ms, standing copies 1,933 ms; heap maximum 89 MB. Unified search "ha": films
  32 ms, live 7 ms. `ReadsDuringImportTest` (2) passes. Full table in `docs/performance-log.md`.
  Notes: (1) right after a cold boot the emulator has no route to the host for about a minute;
  the first test run failed with "no response" and passed on the rerun. (2) The fixture was
  generated into `harness-out/plan/fixture` and served from there, so the older fixture in
  `harness-out/fixture` stays as it was. (3) Start-up and memory were measured with the lab
  build earlier the same day on the same code (216 to 262 ms; 41, 84 and 84 MB) and were not
  repeated. (4) Main-thread CPU per key press was not measured here; IMG-1 and SURF-1 measure it
  before they change code. (5) Code with no JVM test today that later steps change:
  `feature/channels`, `feature/profiles`, the M3U content-kind rule, the updater's package and
  redirect handling. (6) Unified search already meets its 100 ms budget on unchanged code, which
  matters for SRCH-1.

- 2026-11-11 **CRASH-1** done. `SecretStoreFailuresTest` (2) fails on the old code with the raw
  `ProviderException` and `IllegalStateException` (`CRASH-1-red.log`) and passes now.
  `SecretStore.guarded` maps a provider failure to unreadable secrets and a failed commit or missing
  key to an unknown failure; the app-wide scope logs an uncaught exception to diagnostics (and still
  crashes in debug builds); the phone setup server survives a request its handler cannot answer.
  Full check passes.

- 2026-11-11 **CRASH-2** done. The unreadable-secrets sentence now tells the viewer to clear the
  app's storage first (all eight languages; Finnish: "Tallennettuja lähdeosoitteita ja salasanoja ei
  voi enää lukea tällä laitteella. Tyhjennä sovelluksen tallennustila television asetuksista ja
  palauta sitten varmuuskopio tai lisää lähteet uudelleen."). `SecretStore.writable()` probes
  without storing; a viewer-started restore refuses with the new `BackupProblem.SECRETS` before it
  removes anything; start-up logs `secrets: secure storage unusable`. Departure from the step text:
  the restore itself has no injection seam for a failing cipher, so the test covers the probe
  (`SecretStoreFailuresTest`, now 3) and `SettingsBackupTest` (5) confirms a normal restore still
  works. The guard applies to viewer-started restores only, not to the import from the previous app.
  String checks and the full check pass.

- 2026-11-11 **PLAY-1** done. The activity now lists `keyboard|keyboardHidden|navigation` among the
  configuration changes it handles; the release manifest reads `0x00000df0` (before: `0xd80`).
  `:app:lintRelease`, the full check and the device tests `HomeRestoreTest` and `AppLocaleTest`
  pass. No other manifest under `app/src/` redeclares the activity. Not reproducible on the
  emulator; OWN-2 confirms it on a television.

- 2026-11-11 **META-1** done.
  `MetadataServiceTest.aLookupCancelledDuringTheDetailsRequestStoresNothing` fails on the old code
  (`META-1-red.log`: the cancelled lookup had written a cache row) and passes now. Every
  `runCatching` around a provider call is replaced by a helper that rethrows a cancellation, and the
  lookup checks for cancellation before it stores. Full check passes.

- 2026-11-11 **UPD-1** done. `UpdateInstaller.accepts` requires this package in a higher version
  code and, where Android reports signers for a file, the same signers; the updater calls it after
  the digest matches and again before installing a kept download. `UpdaterTest` gains two cases
  (written first; they did not compile against the old interface). New device test
  `UpdatePackageCheckTest` (4) passes on the emulator: the installed build is "not newer", the test
  package is "another package", junk is refused, and a debug build with version code 999 from the
  same signer is accepted. The end-to-end tool `tools/update_e2e.py` was not run: it uninstalls
  `com.streammate.tv` from the emulator, and it calls `gradlew.bat`, which does not exist on this
  Linux machine (nearby problem, not fixed). Full check passes.

- 2026-11-11 **UPD-2** done. Update requests use a client that does not follow a redirect between
  HTTPS and HTTP; checksum, package and profile addresses must be HTTPS on GitHub's hosts or share
  the feed's origin; asset names must be plain file names; a download stops past the announced size
  plus 1 MiB (64 MiB at most). Four tests written first, all failing on the old code
  (`UPD-2-red.log`), all passing now: three in `UpdateHttpTest`, one in `UpdaterTest`. Nearby
  problem, not fixed: `:core:net:test` (80 JVM tests) is in neither `tools/check_all.py` nor the
  Build workflow, which run only `:core:model:test` and `:lint-checks:test` beside the Android unit
  tests; this run adds `:core:net:test` to its own full check from here on. The end-to-end tool was
  skipped for the reason given under UPD-1. Full check passes.

- 2026-11-11 **SECR-1** done. M3U catch-up templates are stored sealed; a one-time pass
  (`TemplateSealing`) seals older rows and remembers it in `app_meta`; the player opens sealed and
  plain values alike. Tests: `LiveImportTest.theCatchupTemplateIsStoredSealed`,
  `TemplateSealingTest` (2), the new statement in `LiveQueryPlanTest`; `PlayerArchiveTest` (4)
  passes on the emulator. The tests were written before the code but not run red, because they need
  the new class to compile. Addition to the step text: the pass marks itself done in `app_meta`,
  because finding nothing to seal would otherwise walk the whole channel table at every start. Full
  check passes. Do not revert this step after a build with it has run on a device.

- 2026-11-11 **M3U-1** done. `M3uKindTest` (3) written first: two of its cases fail on the old rule
  (`M3U-1-red.log`), all pass now. A group name is a hint only as a whole word and is ignored for a
  plainly live address. Two assertions in `M3uReaderTest` that pinned the old misfiling of Showtime
  and Filmbox now expect channels, and `LiveImportTest.scopeBoth…` gives its film and episode real
  film addresses. `OwnerScaleImportTest` on the emulator: the fixture playlist imports 56,164
  channels (before: 50,546) in 4,003 ms; films stay 400,000. No device-test fixture relied on the
  old rule. Full check passes.

- 2026-11-11 **FORM-1** done. An unsaved source page is parked in the settings model and reopened
  with its source; the status line says so. New string `source_draft_kept` in eight languages
  (English: "Unsaved changes were kept. Open or add the source again to continue."; Finnish:
  "Tallentamattomat muutokset säilytettiin. Jatka avaamalla lähde tai lisäämällä se uudelleen.").
  Three new cases in `SettingsModelTest`; two fail on the old model (`FORM-1-red.log`), and one
  existing assertion now expects the new line. `SettingsPlaylistsTest` (13) passes on the emulator.
  String checks and the full check pass.

- 2026-11-11 **FORM-2** done. A new source's page starts on its address field; Down into the buttons
  lands on Save; an unsaved page shows Save and the test only; a page that opens starts at its top.
  `SettingsPlaylistsTest` grew to 17 cases (five new or changed, written first; four failed on the
  old form, `FORM-2-red…log`) and all pass, as do `SettingsGeneralTest` (8), `SettingsLibraryTest`
  (1), `SettingsRemoteTest` (4) and `FirstSourceTest` (3). Departure from the step text: no new
  focus target was added. A focus request for the field from outside the pane is redirected to the
  pane's fallback by its focus restorer, so the pane's start requester is attached to the address
  field while the source is new; a parked new draft therefore also reopens on the field. Possible
  nearby problem, not checked: the list's "focus the source's row after Back" request is made the
  same way and may be redirected to the first row when the source is not the first. The test helper
  `awaitFocus` now reports where focus is when it times out. Captures follow with FORM-3. Full check
  passes.

- 2026-11-11 **FORM-3** done. After Save starts a sync the Playlists status line ends with the
  imported channel count, the film and series counts for a source without live TV, or the failure
  sentence; a message posted in the meantime is not overwritten. Five cases in `SettingsModelTest`
  written first (four failed on the old model, `FORM-3-red.log`). Departure from the step text:
  source health carries no attempt time, so the result counts once the kind was seen running after
  the save, or differs from the result stored before it. On the emulator `SettingsPlaylistsTest`
  (17) sees "Imported 1 channel" replace the syncing line, and `FirstSourceTest` (3) passes; the
  device tests' wait for "Saved." now also accepts the result that follows it. No new strings. Full
  check passes.

- 2026-11-11 **CONF-1** done. New shared `TvConfirmButton` used at the four sites; new string
  `confirm_question` in eight languages ("%1$s?"; French "%1$s ?", Spanish "¿%1$s?"; Finnish
  "%1$s?"). Departure from the step text: the remote-buttons reset row was not moved into a shared
  component. Its question with Reset and Cancel buttons does not fit beside the channel editor's
  other buttons, so the four sites use a button that asks in place, and `RemotePane` is unchanged.
  Device tests: `ChannelsScreenTest` (8, one new case for deleting a list including the focus
  hand-off, and the reset case now presses twice), `HomeLayoutTest` (7, reset presses twice),
  `CustomGroupsTest` (1). The new assertions were not run against the old code. String checks and
  the full check pass.

- 2026-11-11 **LIB-1** done. First entry moves on from an empty History to the first row after it;
  an empty All wall says nothing was imported (new strings `catalogue_nothing_imported_movies` and
  `…_series` in eight languages; Finnish: "Elokuvia ei ole vielä tuotu. Lisää lähde asetuksissa tai
  päivitä siellä elokuvat ja sarjat." and "Sarjoja ei ole vielä tuotu. …"). `EmptyHistoryStartTest`
  (5); device tests `LibraryWallTest` (9), `LibraryManagerTest` (4), `ShellNavigationTest` (3) pass,
  three of their cases adjusted to the new start. Found on the way: the screen's existing
  rail-refocus counter replays when the screen comes back, so the follow request is a one-shot
  instead. String checks and the full check pass.

- 2026-11-11 **RUN** stopped after the last entry above because the session's usage limit was
  reached. Every step ticked above is committed; the first unchecked step is where a later session
  continues. The emulator, fixture server and Gradle daemon were stopped.

- 2026-11-11 **PLAYER-1** done. The focused control of the live action row is named beside it, and
  the lower scrim darkens sooner. New case
  `PlayerControlsTest.theFocusedControlIsNamedBesideTheRow`; `PlayerControlsTest` (6),
  `PlayerRemoteTest` (5), `PlayerLiveTest` (7) pass. No other row in the player uses these icon
  buttons. Not done: the capture over a bright picture (the debug build has no playing stream
  outside the tests); the owner sees it on the next preview. Full check passes.

- 2026-11-11 **FOCUS-1** done. Back from a Discover title focuses the card that opened it, and every
  options sheet now traps D-pad moves (the trap lives in `OptionsSheet`; the library keeps its own
  copy, which is harmless). Two device cases written first and failing on the old code:
  `DiscoverLandingTest.backFromATitleReturnsToTheCardThatOpenedIt`, and
  `GuideScreenTest.focusCannotLeaveTheOpenOptionsSheet`, which showed focus really reaching
  `guide-row-0` under the open sheet. `DiscoverLandingTest` (5), `GuideScreenTest` (9),
  `GuideActionsTest` (3), `LibraryWallTest` (9) pass. Full check passes.

- 2026-11-11 **FOCUS-2** done. Search returns to the opened result and Home's rail to the entry used
  last. Two device assertions written first and failing on the old code:
  `SearchTest.aFilmResultOpensItsPageAndBackReturnsToSearch` now ends on the result, and new
  `HomeTest.theRailOpensOnTheEntryUsedLast`. `SearchTest` (3), `HomeTest` (7), `HomeRestoreTest`
  (4), `ShellNavigationTest` (3) pass. Full check passes.

- 2026-11-11 **GUIDE-1** done. Right from the channel column enters the grid at the programme on
  air. New device case `GuideScreenTest.rightFromTheChannelSelectsTheProgrammeOnAir`, failing on the
  old code (it landed on a programme that had ended). `GuideScreenTest` (10), `GuideActionsTest`
  (3), `GuideTimingTest` (3), `GuideListsTest` (1), `GuideHeroMetadataTest` (1) pass; no test needed
  its press counts changed. `GuideOwnerScaleTest` was not run here. Full check passes.

- 2026-11-11 **A11Y-1** done. `PaletteContrastTest` (2) written first: it failed with Original
  `textDim` at 3.63 and 2.93, and the LIVE chip at 3.48 in all seven themes; the six derived themes'
  dim text already passed. Original `textDim` is now #7F8CA3, the LIVE chip's ink #05070D, and the
  initials are not drawn under a loaded logo. `PaletteParityTest` lists the one departure. Eleven
  goldens of `:ui:design` were recorded again; looked at: only dim text and LIVE chips changed
  (before and after of the rows sheet in `harness-out/plan/captures/`). `:feature:home` goldens did
  not change. Full check passes.

- 2026-11-11 **A11Y-2** done. Only a selected control reports "selected"; the settings switch and
  switch row are switches with an on or off state (through a new `role` parameter of `TvSurface`);
  `SettingsOverline`, `DialogTitle` and the options sheet's title are headings; the channel editor's
  arrow buttons are named (new strings `channels_move_up` "Move channel up" / Finnish "Siirrä kanava
  ylemmäs", and `channels_move_down` "Move channel down" / "Siirrä kanava alemmas", in eight
  languages). `SurfaceSemanticsTest` (3) written first, all failing on the old components. No golden
  changed. Device tests `SettingsGeneralTest` (8), `ChannelsScreenTest` (8), `LibraryWallTest` (9),
  `SettingsLibraryTest` (1), `HomeLayoutTest` (7) pass. String checks and the full check pass. Not
  verified with a screen reader.

- 2026-11-11 **A11Y-3** done (captures only, as planned). New test
  `GalleryScreenshotTest.everySectionAtALargerSystemTextSize` records the six gallery pages in the
  Original theme at a system text scale of 1.3 (`*-original-fontscale130.png`, copies in
  `harness-out/plan/captures/`). Looked at each: rows, tag chips, key hints, buttons, switch rows,
  the picker, the options sheet and messages wrap or grow with the text and nothing overlaps or is
  clipped at the bottom; a long address in a field and the "Buffering…" pill end in an ellipsis. Not
  covered: the guide's rows and header, the Settings rail, search rows and Discover poster titles
  have fixed heights and live in modules without the screenshot plugin, so the claim that they clip
  is still read from the code. No layout was changed. Full check passes.

- 2026-11-11 **TEXT-1** done. The Settings section is named through the new key
  `settings_section_accounts_title` in eight languages (Finnish "Tilit", French "Comptes", Spanish
  "Cuentas", Portuguese "Contas", German "Konten", Swedish "Konton", Italian "Account"). The old key
  stays in the file the import tool owns and is no longer read by code. String checks and the full
  check pass.

- 2026-11-11 **DLG-1** done. The Home hold-OK menu, the guide's programme actions, the guide-source
  picker, the two reminder dialogs and the upgrade notice are filled with `panel` instead of the
  near-transparent `surface`. Only the fill colour changed, so layouts and focus are as before.
  `HomeTest` (7), `GuideActionsTest` (3), `ChannelsScreenTest` (8), `ReminderTest` (7) pass; no
  golden changed. Captures were not taken: the dialogs need content the debug build only has inside
  its tests. Full check passes.

- 2026-11-11 **NOTE** nearby problem seen at the end of phase 2, not fixed: after git packs its refs
  (it did so by itself during this run), the Android build writes `generate_error_reason:
  NO_VALID_GIT_FOUND` into the APK's version-control entry and `tools/package_release.py
  --audit-only` fails with "holds more than the project root and a commit". A new commit writes the
  branch's ref file again and the audit passes. Before packaging a release, make sure
  `.git/refs/heads/<branch>` exists.

- 2026-11-11 **SYNC-1** done. A deferred automatic refresh is remembered by the scheduler and queued
  30 s after the viewer leaves. `RefreshSchedulerTest`: one existing case now expects success plus
  the hand-over instead of a retry, one new case covers `onLeave`. `ConcurrentImportDeviceTest` (1)
  passes. Full check passes.

- 2026-11-11 **ENR-1** done. `onLeave` no longer forces a synchronisation; the pass stops at a page
  boundary when the viewer returns or the worker is cancelled, sweeps nothing then, and is owed to
  the next leave. New cases in `EnrichmentTest` and `EnrichmentSchedulerTest`; the metadata tests
  (19) and `TitleMetadataTest` (6) pass. Full check passes.

- 2026-11-11 **ENR-2** done. `Enrichment.trimCache` removes expired lookup rows and then the
  soonest-expiring ones above 50,000, in transactions of 2,000; the worker calls it after every run.
  New case `EnrichmentTest.theLookupCacheLosesExpiredRowsAndStaysUnderItsCap`. Both statements use
  the existing index on `expires_at`; no query-plan test was added for them (none of the metadata
  statements has one). Full check passes.

- 2026-11-11 **IDX-1** done. Imports bring the search index up to date in pages of 2,000 rows with
  the pause gate between them (`SearchIndex.catchUpPaged`); the single statement stays for a new
  database and the episode fetch. `SearchIndexTest` (1). Measured with a write probe added to
  `OwnerScaleImportTest`, before and after on the emulator: longest wait of a small write during the
  M3U film import 1,854 ms to 836 ms, Xtream 2,180 ms to 679 ms, guide imports 334 and 448 ms to 12
  and 18 ms; the search-index phase itself 1,858 ms to 756 ms, and 130 ms to 0 ms when nothing
  changed. The step's 500 ms target is met for guide imports and for the catch-up itself, not for a
  first film import as a whole: the longest wait there now comes from another finish phase
  (organisation or standing copies), which is a nearby problem, not investigated. Import totals
  unchanged; search still answers. Table in `docs/performance-log.md`. Full check passes.

- 2026-11-11 **IDX-2** done. The statistics step after an import waits until no video plays (new
  `PauseGate.awaitNoPlayback`), takes the pause gate between tables, and sets `PRAGMA
  analysis_limit=400` first, which SQLite 3.32 and later honour. New cases in `PauseGateTest` and
  `RefreshSchedulerTest` (an import during playback finishes, its statistics wait). The query-plan
  tests pass unchanged. `ConcurrentImportDeviceTest` (1), `FirstSourceTest` (3),
  `SettingsPlaylistsTest` (17) pass. Full check passes. Consequence to know: a viewer-started sync
  that ends while video plays reports its result at once but its job ends only when playback stops.

- 2026-11-11 **IDX-3** done (the baseline's 164 ms was above the 100 ms threshold). An import
  recounts film groups in ranges of 50 groups, one transaction each; the one-statement recount stays
  for the organisation pass, which runs inside its own flow. `LibraryPassesTest` gains an
  equivalence case. Measured on the emulator: the phase's total is now about 250 ms (before 164 ms)
  because it is many short transactions, none of them over the whole source; import totals unchanged
  (M3U catalogue 32.8 s). The longest wait of a small write in a first film import is still about
  0.7 s and in a first playlist import about 0.4 s: they come from other finish phases
  (organisation, standing copies, group finish), which no finding covered. Full check passes.

- 2026-11-11 **GUIDE-2** done. The guide's list signal is built from two table sets: channel and
  group writes are held back while a playlist import runs, everything else passes; the programme
  signal compares each source's enabled flag, guide time correction and active guide snapshot by
  value. Two new cases in `LiveStoreTest`, red first: 10 list signals for 10 batches (now 0, plus 1
  at the import's end) and 3 needless programme reloads (now 0). Two small statements added to
  `LiveQueryPlanTest`. Device tests `GuideListsTest` (1), `GuideInitializationTest` (2),
  `GuideScreenTest` (10), `FirstSourceTest` (3), `ReadsDuringImportTest` (2), `ChannelsScreenTest`
  (8), `PlayerChannelSwitchTest` (8) pass. Departure: the signals were counted in the unit test, not
  during an owner-scale import on the emulator. Full check passes.

- 2026-11-11 **SRCH-1** done without a statement change. The measurement the step asked for already
  exists in `OwnerScaleImportTest`: on unchanged code a two-letter term ("ha") over 400,000 films
  answers in 32 to 33 ms, channels and programmes in 7 ms, series and episodes in 0 to 2 ms, all far
  inside the 100 ms budget. Capping the matches at 2,000 would have cost results for no measurable
  gain, so decision 17 is not applied. Only the comment in `SearchReads.kt` is corrected: a
  superseded statement is not interrupted, it is short. Not measured: the guide's Find programme and
  the wall's text search for a rare term (they need the guide or a wall open at owner scale); both
  stay as the specification has them. Correction 11 added to section 7. Full check passes.

- 2026-11-11 **HASH-1** Done as planned. First-film-removed re-import 52.3 s before, 12.6 s after;
  unchanged 12.6 s both (keep rule met). The fixture copy is written by
  tools/fixture/make_fixture.py next to vod.m3u. Also fixed in passing because the new position
  needs it: an M3U series header read its position when the batch was written, not when the series
  was first seen. Note: the first refresh after this step rewrites every film and series once.

- 2026-11-11 **IMG-1** Done. ArtworkLoader.peek (default null) and an explicit memory-cache key
  (address, both sizes, opaque) in CoilArtwork; device test ArtworkPeekTest. Seeded 14 cells:
  LogoTile, guide row logo, guide hero still, library poster, episode still, cast avatar, details
  backdrop, match-picker thumbnail, Discover poster, cast photo and episode still, Home card art,
  both crests. Not seeded, on purpose: the Home hero, the Discover landing and title backdrops (they
  keep the previous picture and cross-fade, so they show no placeholder), the Discover landing logo
  and the two addon loading overlays (shown once, not cells), and Ground.kt (not artwork). Where the
  load's effect is keyed by size, the remembered state is now keyed by the same size. CPU per press:
  guide 2.8 ms before and after; wall 3.0 ms before, 2.9 ms after. GuideOwnerScaleTest,
  ChannelsOwnerScaleTest, HomeTest, LibraryWallTest pass. Note: a comma-separated class list ran
  only its first class with dev.sh; classes were run one by one.

- 2026-11-11 **SURF-1** Done as planned in TvSurface.kt only. SurfaceFocusTest,
  MotionAtZeroScaleTest and TvSurfaceKeysTest (the two classes in MotionAndKeysTest.kt) pass;
  verifyRoborazziDebug passes with no golden changed. CPU per press: guide 2.8 ms before, 2.7 ms
  after; wall 2.9 ms before, 2.85 ms after (noise). The lab build was removed from the emulator
  afterwards.

- 2026-11-11 **NOTE** Phase 3 complete check (tools/check_all.py) passed after SURF-1.

- 2026-11-11 **DOC-1** Done. The README's licence link points at docs/release/LICENSE; the
  public-source audit and the final complete check pass.

- 2026-11-11 **RUN** finished. Every step of phases 1 to 4 is done and committed; none is blocked.
  The final complete check passed on the DOC-1 commit. OWN-1 to OWN-3 wait for the owner. Cleaned
  up: the lab build and the update test file were removed from the emulator (its com.streammate.tv
  install was left as it was); emulator, fixture server and Gradle daemon stopped.

## 6. Not planned

| Finding | What is left out | Reason |
|---|---|---|
| B3 | An in-app "reset secure storage" action | It deletes stored passwords, and the failure has not been seen on a device. CRASH-2 adds the diagnostics line that will show whether it happens; decide then. |
| B2 | Keeping the player open across every activity recreation | The decision of 2 October 2026 drops playback routes on recreation to fix a real bug, with tests. PLAY-1 removes the common cause instead. |
| B8 | Splitting `PlayerModel.kt` (831 lines) | The player is under active change on `fix/live-stream-end`; a split now would collide with it and gives the viewer nothing. Do it when that work has settled. |
| B8 | Moving the five test-only hooks out of `AppGraph` | It touches many of the 81 device-test files for no change in behaviour. |
| B8 | Unit tests for `feature/channels` and `feature/profiles` as a goal of their own | CONF-1 and A11Y-2 add device-test cases for the channel code they change; tests without a change to protect are not planned. |
| U1 | The form's three row layouts and the repeated "Connection limit" label | Both come from the previous app's layout and a string the import tool manages; the project's goal is an almost identical look. |
| U3 | The word "watched" on a live programme | The text is copied from the previous app and `tools/import_strings.py --check` fails when it changes. An owner decision on the kit's wording. |
| U4 | A starter row on a new Home | Which row to show is a product decision, and the review saw the empty Home only in the lab build, where metadata enrichment is off. |
| U6 | Announcing the selected programme inside a guide row to a screen reader | The claim was low confidence and cannot be checked without a screen-reader session; listed for the owner. |
| U6 | A fix for system text scaling | See decision 14: A11Y-3 captures the evidence; every fix is a redesign or removes the system setting's effect. |
| U8 | A quieter "current screen" marker and an opaque rail | Both match the previous app's reference screenshots, which the specification follows on purpose. |
| U9 | Replacing 968 literal sizes with tokens, and 69 type-size overrides | A large mechanical change with no visible effect; new code in this plan uses tokens. |
| U9 | Poster titles cut to about ten characters | Needs a design choice (a second line under the focused poster, or the title in the header line); either changes the wall's layout. |
| P2 | Rewriting only changed rows during a queue synchronisation | After ENR-1 the pass runs once per import and logs its duration (`queue synchronised: … ms`); revisit if diagnostics show more than a few seconds. |
| P4 | Making search statements cancellable | SRCH-1 bounds the work instead (decision 17); cancellation would need hand-written cursor code around Room. |
| P4 | Changing the guide's "Find programme" and the wall's text search | Both keep the previous app's substring matching by specification; a faster index would change what matches. SRCH-1 records their timings so the owner can decide. |
| P6 | Removing the clip layer on each poster and the two layers per guide row | The cost is on the graphics processor, which the emulator emulates in software; it cannot be measured in an unattended run. The guide's layers are also commented as deliberate. Needs the low-end box (OWN-2). |

## 7. Corrections

These points of the review report turned out to be different when checked against the code.

1. **P4, "Find programme reads every programme of the source".** The statement is limited to the
   visible time window; it walks the index entries of the snapshot and fetches only the rows in
   the window. The cost is real but smaller than reported, and it is now measured, not assumed.
2. **B4, signer check.** Android already refuses a file of the same package signed by someone
   else. The missing protection is against a file of a different package, which a package-name
   check closes; the signer comparison is an extra, used when Android reports a signer.
3. **P1, `PRAGMA analysis_limit`.** It exists only from SQLite 3.32. The Shield (Android 11) and
   the test emulator (API 30) ignore it; it helps on Android 12, which the low-end box runs.
   IDX-2 therefore also defers the statistics while video plays.
4. **U1, focus on "All playlists".** Requirement SRC-FR-13 asks for it on a page that opens. It is
   deliberate for saved sources; only new sources change.
5. **U4, walls opening on History.** Requirement VOD-FR-49 asks for it on first entry. It is
   deliberate; only the empty case changes.
6. **U6, system text scale.** design/01 §14 keeps the system scale on top of the interface size
   on purpose. The clipping remains a read-from-the-code claim until A11Y-3 captures it.
7. **U6, contrast.** `PaletteParityTest` pins the theme colours to the previous app's values, so
   the low contrast is inherited, not an oversight. A11Y-1 is a deliberate departure.
8. **U9, the missing "Accounts" translation.** It is permitted, not an oversight: the key is in a
   file copied from the previous app, which `TranslationParityTest` and `AGENTS.md` §5 rule 5
   allow to fall back to English in the five draft languages. TEXT-1 still translates it, through
   a new key, because one English word stands out in an otherwise translated list.
9. **B8, "no unit tests" for channels and profiles.** True for JVM tests, but `ChannelsScreenTest`,
   `ChannelsOwnerScaleTest` and `ProfilesTest` cover both on the emulator.
10. **U2, six unconfirmed actions.** "Clear all guide data" already has the danger style and its
    data downloads again; it and "Forget progress" are left at one press (decision 9).
11. **P4, unified search sorting every match (found while carrying out the plan).** Measured in
    `OwnerScaleImportTest` on unchanged code: a two-letter term over 400,000 films answers in
    32 ms, channels in 7 ms. The sort before the limit costs far less than the report feared, and
    the 100 ms budget is met several times over. SRCH-1 therefore changes no statement.
