# Lessons learned from the current app

> What building Sohva TV up to beta 23 taught, each as *what happened* and *the rule for the
> rebuild*. Feature-specific lessons are also in each spec's section 10; this file collects the
> ones that cut across the app. Sources: the private ledger `docs/SOHVA_SPORT_USER_REPORTS.md`,
> release receipts `docs/SOHVA_TV_BETA_*.md`, the performance review
> `docs/EPG_PERFORMANCE_REVIEW_BETA_22.md`, commit history, and the development notes kept
> between sessions.

## 1. Memory and data volume

| # | What happened | Rule |
|---|---|---|
| 1.1 | Two background jobs loaded the whole catalogue (about 200,000 films, 257,000 titles) into memory; the process was killed about four minutes after every start on the Shield's 192 MB heap (7 Sept 2026). | Never load a catalogue whole. Page 2,000 rows at a time in primary-key order; for tables rebuilt from the catalogue use stamp-and-sweep, not clear-and-replace. |
| 1.2 | The guide's "All channels" roster for a 50,000-channel source was one sorted query; SQLite re-executed it for each of 20 CursorWindow refills, 57 s of disk-thread time, finished after the viewer had left, and its result was discarded. | No wide, sorted, tens-of-thousands-row query. Keyset pages along the primary key with join order pinned (`CROSS JOIN`), sort in Kotlin with a comparator proven equal to SQLite's; `LIMIT/OFFSET` over a sorted statement does not help. |
| 1.3 | Room's blocking read loop ignores coroutine cancellation, and only four `arch_disk_io` threads exist; abandoned whole-source reads queued every other query in the app. | Make long reads cancellable by paging; cancel on leaving a screen; observe tables with a cheap invalidation query plus `conflate()` and re-read only what is visible. |
| 1.4 | The sports matcher scanned channel names and programmes whole and hit a real OutOfMemoryError on the Shield (preview 48); paging brought the peak to about 12 MB. | Same rule as 1.1 for every background matcher. |
| 1.5 | Backup export built a giant in-memory index of the catalogue and exhausted memory (fixed beta 17). | Stream backups; never materialise catalogue-sized structures to write a settings file. |
| 1.6 | The image memory cache used a phone-style percentage and held hundreds of posters, pushing native heap above 300 MB on the Shield. | Size image caches for TV walls (8 % of the memory class), decode at display size, `RGB_565` for opaque art. |
| 1.7 | Twenty simultaneous JPEG decodes saturated four cores; frame p90 above 100 ms. | At most two concurrent decodes. |

## 2. Database and SQL

| # | What happened | Rule |
|---|---|---|
| 2.1 | The organisation views evaluate up to 16 correlated sub-queries per row; joined in the wrong order, Home's Continue watching went from under 1 ms to 5.5 s on a 40,000-title library. Room never runs ANALYZE, so the planner guessed. | Pin join order for queries over views (`CROSS JOIN` from the small side), run ANALYZE after every import, and keep a JVM query-plan test (sqlite-jdbc on the exported schema) for every hot query. |
| 2.2 | Compiling after changing an entity but before bumping the version overwrote the shipped version's exported schema; migration tests then failed with misleading messages. | Change entity, bump version and add the migration in one edit, then compile. If migration tests fail oddly, check the exported schema files in git first. |
| 2.3 | Two imports of the same source ran at once (a background "Sync everything" plus a manual "Refresh channels"); activating one snapshot deleted the other's staging rows, the loser activated an empty snapshot, and the playlist vanished from the guide while Settings still showed its count (23 Sept 2026). | One import per source and kind at a time; snapshot activation must never delete a snapshot another import is staging; report counts from what was activated, not what was parsed. |
| 2.4 | Background metadata writes re-emitted flows that the poster wall collected; suspected cause of an intermittent focus bug (Left from the wall not reaching the rail). | UI observes narrow queries; background writes to tables a visible screen observes are batched and rate-limited; focus routing never depends on transient layout info (derive the column from the item index, not from `visibleItemsInfo`). |

## 3. Main thread, rendering and start-up

| # | What happened | Rule |
|---|---|---|
| 3.1 | Every screen repainted its static background (window background, a fill and three gradients; Home three more) four to five times per frame; affordable on the Shield, a large share of a frame on a Mali-G31. | Draw static backgrounds once into a cached layer or pre-rendered bitmap; no full-screen fills under an opaque gradient. |
| 3.2 | The film wall ran title-normalisation regexes on the main thread during every layout pass. | No regex, sorting or string normalisation during composition or layout; precompute keys once, store them, memoise. |
| 3.3 | `ActivePlayer` and `SettingsScreen` exceeded ART's 10,000 code-unit ahead-of-time limit and ran interpreted after every start. | Keep composables small; guard method sizes in the release gate. |
| 3.4 | A 41-parameter composable produced R8 output that ART refused to verify; the release crashed when a stream opened while all debug tests were green. A clean rebuild of the previous commit failed the same way — it had been lucky. | Few parameters per composable (pass state holders); verify the release dex on an emulator before every release. |
| 3.5 | The release build had no minification until beta 23 (39 MB of dex). | R8 and resource shrinking from the first release; keep rules minimal; `-dontobfuscate` is acceptable for readable diagnostics. |
| 3.6 | Sideloaded and in-app updates ran interpreted for up to a day (first guide composition 408 ms uncompiled vs 92 ms compiled; Home 922 vs 218 ms) because the baseline profile was only compiled by the nightly idle job. | Ship `.dm` install profiles with every release and install updates through a PackageInstaller session carrying them (`reason=install-dm`); generate profiles that cover the guide, Home and walls. |
| 3.7 | A guide key press cost ~39 ms of main-thread work (draw 14.5, layout 14.4, composition 10.7) spread over 17–29 nodes per row; two careful rounds took it to ~32 ms. What remained was structural. | Design dense grids as custom-drawn rows from the start; read focus/selection state in the smallest scope (hero and rows, not the screen body); no text relayout on selection; avoid `clip` layers per cell. |
| 3.8 | An accessibility service (a button remapper) cost 30 % of the guide's main-thread time and half of its jank through Compose's semantics walk. | Keep semantics trees small in dense grids (`clearAndSetSemantics` on cells with an explicit text list); when a tester reports jank, ask first which accessibility services are enabled. |

## 4. Focus and navigation

| # | What happened | Rule |
|---|---|---|
| 4.1 | When an overlay holding focus left composition, Compose gave focus to the first focusable on screen (the guide hero's Watch button); effects that skipped focus while the overlay was open never ran again. | Every sheet and dialog has an explicit close path: scroll the target into view while the overlay is up, request focus synchronously, then hide the overlay; fall back to the control that opened it. |
| 4.2 | Programme data arriving while the viewer paged moved focus; held Left/Right parked focus on the channel name (betas 17, 21, 22). | Data arrival never moves focus. While a page is pending, consume only the pending direction; keep focus on the same channel; the destination takes focus when it arrives. |
| 4.3 | The Home side menu sometimes opened after leaving with Back and reopening the app (fixed in betas 20 and 21). | Restore navigation state explicitly on start; never infer the rail's state from leftover focus. |
| 4.4 | Reopening the guide's category drawer lost its scroll position (fixed beta 17). | Every list that can be reopened remembers its scroll position and focused item. |

## 5. Imports and provider data

| # | What happened | Rule |
|---|---|---|
| 5.1 | XMLTV feeds starting with a byte-order mark failed with "PI must not start with xml" (beta 17). Compressed feeds and declared encodings also needed care. | Sniff compression by magic bytes, strip a BOM, honour the declared encoding; test with real-world quirks. |
| 5.2 | Provider titles are full of years, quality tags, language prefixes and separators; naive initials read "Ben 10" as "B1". | One normalisation module for titles, with tests from real provider patterns; initials only from words that start with letters. |
| 5.3 | Sports stream matching needed explicit time zones, AM/PM and dates like `18/9` in channel names; both team names outrank a provider clock without a time zone (betas 19–20). | Treat provider clocks without a zone as weak evidence; keep ambiguous matches as "Possible"; persist the viewer's confirm/reject decisions across restarts. |
| 5.4 | The guide importer keeps 12 hours of history and 8 days ahead; a test fixture written for a fixed date started failing a week later. | Anchor synthetic listings to "now". |

## 6. Playback

| # | What happened | Rule |
|---|---|---|
| 6.1 | A finished film left a black screen instead of returning to its details; Discover episodes did not continue (fixed beta 23). | Define completion (threshold and end-of-stream) per content type and test the whole end-to-end: finish → details or next episode. |
| 6.2 | Automatic subtitle choice waited for slow subtitle addons (fixed beta 16 with a 5-second budget). | Every optional network step before playback has a time budget and a fallback. |
| 6.3 | On Android TV a reminder notification is never shown as a popup, and a receiver cannot start an activity from the background unless the viewer granted "display over other apps". | Alert inside the app; ask once for the overlay grant; the notification is only a fallback. |

## 7. Testing on TV devices

| # | What happened | Rule |
|---|---|---|
| 7.1 | Blind `adb input keyevent` on the owner's TV opened another app from the launcher and left it running for an hour; a test run collided with the viewer using the TV. | Never drive a real TV with blind key events; device checks are instrumentation tests; ask before using a device someone may be watching. |
| 7.2 | A test wrapper turned off the screensaver and display timeouts and died before restoring them, leaving the owner's TV never sleeping. | Never change device display settings in tests. Run long suites in pieces. |
| 7.3 | Instrumentation tests run the empty debug package; three "fixes" shipped unverified because real content could not be reproduced, and one test passed on broken code. | Build synthetic harnesses that reproduce the mechanism at realistic sizes; prove every regression test fails without the fix. |
| 7.4 | An in-memory database answers before the next key repeat, so timing bugs never reproduce on the emulator. | Gate or delay the specific query in tests; send real repeated key events; assert states and orderings. |
| 7.5 | Leftover state (a second profile, a chosen language, the last guide source) broke later tests; Back sent inside Compose never reached `BackHandler`; the Compose test clock is virtual. | A shared clear-state rule extended with every start-up-affecting setting; send Back through the window; never assert absence after a `delay` without advancing the clock. |
| 7.6 | The owner has two packages on the Shield (release and debug) and launches the release one; a "still broken" report came from testing hours-old code. | Know which package the viewer runs; install what they will open. |
| 7.7 | The emulator's clock was a week old after a snapshot boot, so the EPG was empty. | Cold-boot emulators (`-no-snapshot-load`) for measurements and guide tests. |

## 8. Release and process

| # | What happened | Rule |
|---|---|---|
| 8.1 | The public Build workflow failed on every push from beta 6 to beta 11 on lint errors nobody ran locally. | Run the CI's own tasks locally before publishing; the last Build must be green for the published commit. |
| 8.2 | A Maven Central HTTP 429 failed a release build once (beta 23). | Retry the failed CI job before suspecting the code. |
| 8.3 | Publishing actions were refused by automation until the owner used explicit wording. | Ask the owner for explicit permission to push and publish, in words, for each release. |
| 8.4 | The updater of old builds reads the release body ("Android build **N**") and needs `SHA256SUMS.txt`; since beta 23 it also installs `.dm` profiles. | Keep the release-body contract and the five assets for every release forever. |
| 8.5 | A tester's "frame around the UI while video fills the screen" was the device's own display-area setting (Amlogic scales only the graphics plane). | Before hunting an app bug for display framing, ask for the device's screen position/size settings. |
| 8.6 | Two agents (Claude and Codex) worked in the same worktree and ledger; state changed while one was paused. | One shared decision log and ledger in the new repo; re-read it and `git log` before resuming work. |
