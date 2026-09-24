# Instructions for building Sohva TV from scratch

These rules are for everyone who writes code for the rebuild: people, Claude, Codex or any
other agent. Read them before the first commit and again before every milestone. When a rule
here and a habit conflict, the rule wins. When a spec and the old app's code conflict, the spec
wins; when a spec is silent, read the old code (read-only) and record the decision.

## 1. The goal in one paragraph

Rebuild Sohva TV so that it has **every feature** of `0.1.0-beta.23` (build 57), **looks almost
identical**, and is **lightweight enough to run well on low-end TV boxes** (Amlogic S905Y4,
4× Cortex-A35, Mali-G31, 2 GB RAM, and 1–2 GB sticks) as well as on the Nvidia Shield. The old
code worked but became messy; the rebuild is clean, small and measured from day one.

## 2. Read in this order

The rebuild kit lives in [docs/rebuild/](docs/rebuild/). Short paths in this file and in the kit
(`plan/…`, `specs/…`, `design/…`, `reference/…`, `assets/…`) are relative to that folder.

1. [README.md](docs/rebuild/README.md) — what is in this kit.
2. [plan/00-product-overview.md](docs/rebuild/plan/00-product-overview.md) — product, principles, identity, non-goals.
3. [plan/07-performance.md](docs/rebuild/plan/07-performance.md) — budgets and rules. They apply to every line.
4. [plan/03-architecture.md](docs/rebuild/plan/03-architecture.md) and [plan/04-data-model.md](docs/rebuild/plan/04-data-model.md).
5. [plan/02-roadmap.md](docs/rebuild/plan/02-roadmap.md) — the milestone you are working on.
6. The specs listed for that milestone, and [design/](docs/rebuild/design/) for anything visible.
7. [plan/08-lessons-learned.md](docs/rebuild/plan/08-lessons-learned.md) — the traps that cost the old app days.

## 3. Sources of truth

| Question | Where the answer is |
|---|---|
| What must the feature do? | `specs/*.md`, section 4 (numbered requirements) |
| How must it look? | `design/01-03`, then the spec's section 5, then `design/screenshots/` |
| What text does it show? | `reference/strings/` (all seven languages; keep the texts, keys may be renamed) |
| Which asset? | `assets/` (drop-in `res/`, see `assets/README.md`) |
| How fast / how big may it be? | `plan/07-performance.md` and the spec's section 9 |
| How is it tested? | The spec's section 11 and `plan/06-quality-testing-release.md` |
| What did the old app do exactly? | The old tree, read-only: `G:\SportMate\.local\sohva-sport-user-reports\.local\beta19-public-source` (commit `efab52a`) |

Record every decision a spec did not settle in `docs/decisions.md` of the new repository (date,
question, decision, why).

### Code reuse policy (owner's rule, 24 September 2026)

**The rebuild does not reuse the old app's code.** Everything is written new against the specs.
The old code is read only to understand behaviour, edge cases and past bugs.

The only exceptions are small, self-contained pieces where reuse clearly cannot affect speed,
memory or start-up, and only when all of these hold:

1. It is pure logic with no Android, database, Compose or threading in it (for example a catch-up
   URL template, a time-zone label formatter, the placeholder-initials rule).
2. It is under about 100 lines, reviewed line by line, and rewritten to the new code style.
3. It comes with unit tests (the old tests may be carried over as test cases).
4. It is listed in `docs/decisions.md` with its old file and the reason reuse was safe.

Never carried over: screens and composables, database entities/DAOs/queries, repositories and
import pipelines, the player, background workers, the dependency container. These are exactly the
places the old app was slow.

Carried over as **data, not code**: the visual assets in `assets/`, the translated texts in
`reference/strings/`, and test fixtures that contain no secrets.

Ideas from other open-source TV apps (see [reference/owntv-study.md](docs/rebuild/reference/owntv-study.md)) are
restated in our own design; their code is not copied either.

## 4. Hard rules: lightweight

These are acceptance criteria, not advice. A change that breaks one is not done.

1. **Main thread = UI only.** No database, network, file, JSON/XML parsing, regex, sorting or
   hashing on the main thread. Use the dispatchers defined in plan/03.
2. **Never hold a whole catalogue.** No query or list that returns every channel, programme,
   film or series. Page in primary-key (keyset) order, 2,000 rows or fewer per page, and do the
   work per page. `LIMIT/OFFSET` over a sorted query is not paging (each page redoes the sort).
3. **No wide sorted reads.** A Room/SQLite result larger than one 2 MiB CursorWindow re-executes
   the whole query for every window. Keep results narrow and small, pin join order with
   `CROSS JOIN` where the planner could scan, and add a query-plan test for every hot query.
4. **Bounded caches.** Every in-memory cache has a size limit and an eviction rule written next
   to it. Image caches are sized from `ActivityManager.memoryClass`.
5. **Decode images at display size.** Posters, logos, crests and backdrops are requested and
   decoded at the size they are drawn. Ask TMDB for the smallest image that fits.
6. **Cheap drawing.** No blur, noise/grain, animated gradients, or per-frame shadows. Static
   backgrounds are pre-rendered images or cached layers drawn once. Prefer
   `background(color, shape)` to `clip` + `background`. One layer per screen region, not per cell.
7. **Small composables.** No composable function body near ART's 10,000 code-unit AOT limit;
   keep files under about 600 lines and composables short, with few parameters (a composable with
   41 parameters once produced dex that ART refused to verify). Pass state objects, not dozens of
   values.
8. **No work during playback that competes with the decoder.** Bulk jobs (imports, metadata,
   matching) run at background priority, yield, and pause while video plays unless the viewer
   started them.
9. **Lazy start-up.** Application and Activity start only what the first screen needs. Database
   open, WorkManager scheduling, metadata and sports refreshes happen after the first frame.
10. **R8 and profiles from the first release.** Release builds are minified and shrunk; baseline
    and startup profiles are generated, not hand-written; releases ship the `.dm` install profiles.
11. **Measure, do not guess.** Performance changes come with a before/after measurement on the
    low-end stand-in (plan/07 describes the emulator harness). Compare CPU time per phase, not
    frame times from a desktop emulator.

## 5. Hard rules: behaviour and look

1. **Parity is tracked.** Every capability has an ID in [plan/01-feature-inventory.md](docs/rebuild/plan/01-feature-inventory.md).
   A milestone is finished when all its IDs are implemented and their acceptance tests pass. Tick
   them in the new repository's copy of the inventory.
2. **Focus is sacred.** Focus is always visible, never lands on something the viewer did not
   choose, and returns to where it was after Back, a dialog, a sheet or a data refresh. Any
   overlay that closes must place focus explicitly *before* it hides (otherwise Compose hands focus
   to the first focusable on screen). Data arriving must never move focus.
3. **Every key has a defined meaning** on every screen: D-pad, OK, long OK, Back, Menu, number
   keys, channel up/down, media keys. Back goes through the window like a remote (tests must send
   it with `sendKeyDownUpSync(KEYCODE_BACK)`, not Compose key input).
4. **Colours, sizes and type come from the design system tokens**, never literals in feature code.
   All seven themes must work on every screen.
5. **All user-visible text is a string resource** present in all seven languages (English and
   Finnish complete; the five drafts may fall back to English only where the old app did).
6. **Plain-language errors.** Every failure the viewer can see has a sentence that says what went
   wrong and what to do, as the specs give them. No raw codes or stack traces on screen.

## 6. Hard rules: security, privacy and identity

1. **No secrets anywhere in the repository**: no real playlist URLs, usernames, passwords, API
   keys, Trakt client secrets, addon URLs with embedded configuration, backups or signing
   material. Test fixtures use `https://provider.example`, `192.0.2.x`, fictional names. Run
   gitleaks and the public-source audit before every push.
2. **Credentials are encrypted** with an Android Keystore key and redacted from every log and
   diagnostics file ([specs/73](docs/rebuild/specs/73-security-privacy.md)).
3. **Cleartext HTTP only for the viewer's IPTV sources.** Everything else is HTTPS.
4. **No first-party server, analytics, ads or crash upload.**
5. **Identity never changes:** application ID `com.streammate.tv`, the existing signing key,
   version codes above 57 and always increasing, and the update-feed release-body contract
   ([specs/72](docs/rebuild/specs/72-updates-about-diagnostics.md)).

## 7. Hard rules: devices, releases and the owner's TV

1. **Never touch the owner's Shield or other household devices without the owner's explicit go**
   for that specific action: no installs, no key events, no settings changes, no clearing data.
2. **Never drive a real TV with blind key events** (`adb shell input keyevent`): stray presses land
   on the launcher and open other apps. Device checks run as instrumentation tests.
3. **Never change device display or screensaver settings**, not even temporarily. Run long
   instrumentation suites in smaller pieces instead.
4. **Use the emulator for tests.** An Android TV emulator at 1920×1080, xhdpi, API 30+ is the
   default test device; a cold-booted emulator (`-no-snapshot-load`) has a correct clock.
5. **Publishing is the owner's decision.** Pushing to the public repository, tagging, creating a
   GitHub release, and any install on the owner's devices each need the owner's explicit
   wording for that release. Never overwrite a published or frozen artifact; every build gets a
   new version code.

## 8. How to work

- **One milestone at a time**, in roadmap order, on its own branch; small commits with messages
  that say what changed for the viewer and why.
- **Write the test first** for behaviour that has broken before (focus, Back, paging, imports,
  playback completion). For a regression test, prove it fails on the broken behaviour before
  trusting it.
- **Timing bugs need slow data in tests.** An emulator with an in-memory database has no gap
  between key press and data; gate or delay the specific query in the test, send real repeated
  key events, and assert states, not throughput.
- **Anchor test dates to "now".** The guide keeps 12 hours of history and 8 days ahead; a fixture
  written for a fixed date silently expires.
- **Clean up persistent state** in tests that change start-up (profiles, language, startup
  screen, last guide source) through the repository in `finally`, and extend the shared
  clear-state rule when a new persisted setting can change the first screen.
- **Room:** change an entity, bump the database version and add the migration in the same edit,
  before compiling (compiling first overwrites the exported schema of the shipped version).
  Every migration has a test.
- **Debug and release differ.** Unit and UI tests run the unminified debug build; verify the
  release dex (install, `cmd package compile -m verify`, check for "failed to verify") before any
  release.
- **Definition of done** for a feature: its requirements implemented; its acceptance tests
  green on the emulator; its section 9 budgets checked on the low-end stand-in; strings in all
  languages; lint clean; no new warnings; inventory IDs ticked; decisions recorded.

## 9. Style

- Kotlin official style; explicit types on public APIs; no wildcard imports.
- Name things after what the viewer sees ("guide", "poster wall", "hero"), not after history
  ("StreamMate", "SportMate" survive only in identifiers that must not change).
- Comments explain *why*, especially where a rule in section 4 forced a design.
- Documentation, commit messages and release notes: plain English (and plain Finnish for
  tester-facing notes), short sentences, no marketing.
