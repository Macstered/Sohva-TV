# Spec conventions for the Sohva TV rebuild kit

Every file under `specs/`, `design/` and `plan/` follows these rules so the kit
reads as one document set. Written 24 September 2026.

## What the kit is for

Sohva TV is being rebuilt from an empty repository. The rebuilt app must have
**every feature the current app has** and must look **almost identical**. The
current code works but has grown messy, so the kit describes *behaviour and
appearance* precisely enough to rebuild them cleanly, not a copy of today's
structure. Where today's implementation has a lesson (a bug that was fixed, a
performance trap, a device quirk), the spec records the lesson so the rebuild
does not repeat it.

## Lightweight is a requirement, not a later optimisation

The owner, 24 September 2026: **the new app has to be lightweight so it runs well on
lower-end machines as well.** The current app was built on an Nvidia Shield and is slow on
cheap boxes. Every spec therefore states what its feature costs and how it stays cheap, and
the look is to be reproduced with cheap rendering (pre-rendered images, solid fills, static
layers), never with effects a weak GPU cannot afford.

Reference devices:

| Class | Device | CPU / GPU / RAM | Android |
|---|---|---|---|
| Low end (must run well) | Elisa Viihde box, ZTE B866V2F01 | Amlogic S905Y4, 4× in-order Cortex-A35 @ 2 GHz, Mali-G31 MP2 (about a tenth of the Shield's fill rate), 2 GB | 12 (API 31) |
| Low end (must run well) | Xiaomi TV stick class (a tester reported guide slowness) | quad Cortex-A35/A53 class, Mali-G31/450 class, 1–2 GB | 9–11 |
| High end | Nvidia Shield TV (the owner's) | Tegra X1, 3 GB | 11 |

Scale the app must handle on the low-end box: about 56,000 channels in 800 groups, 165,000
programmes, 200,000 films, thousands of series — without being killed and without a
key press ever freezing the screen.

Starting budgets (plan/07-performance.md refines them): release APK ≤ 10 MB (beta 23 is
7.8 MB); Java heap in steady browsing ≤ 64 MB and never above 128 MB during imports; no
whole-catalogue list held in memory; a D-pad press in any grid or list renders its next frame
within one or two vsyncs on the low-end box; nothing but UI work on the main thread; no
per-frame allocations, regexes, sorting or database reads during composition; background work
runs at background priority and yields while video plays; cold start shows Home within 2 s
on the Shield and 4 s on the low-end box.

## Source of truth (read-only)

- Code: the public beta 23 tree, `G:\SportMate\.local\sohva-sport-user-reports\.local\beta19-public-source`
  (Sohva TV `0.1.0-beta.23`, Android build 57, commit `efab52a`, released 23 September 2026).
  Modules: `app`, `core`, `iptv`, `sportmate`, `addons`, `trakt`. Package names start with
  `com.streammate.tv` (legacy name, kept for in-place upgrades) or `com.sohva.tv`.
- Tester documents at that tree's root: `README.md`, `INSTALL.md`, `TESTING.md`,
  `RELEASE_NOTES.md`, `ADDONS.md`, `PRIVACY.md`, `THIRD_PARTY_NOTICES.md`.
- Private history and design notes (read-only):
  `G:\SportMate\.local\sohva-sport-user-reports\docs\` — feature list
  (`SOHVA_TV_FEATURES.md`, written at beta 14), per-beta receipts and EN/FI changelogs
  (`SOHVA_TV_BETA_*.md`), plans, and the shared ledger `SOHVA_SPORT_USER_REPORTS.md`.
- Never modify anything under `G:\SportMate`. Never run Gradle, adb, emulators or devices.

## Output rules

- Write only the files you were assigned, under `G:\SohvaTV-Rebuild\`.
- English, plain and precise. Markdown. No marketing language.
- Numbers matter: timeouts, intervals, limits, page sizes, dp/sp sizes, colours (hex),
  animation durations, default values, retry counts. Copy them exactly from the code.
- Describe behaviour so an engineer who never saw the old code can rebuild it. Short
  code or SQL snippets are fine where exactness matters (a regex, a URL template,
  a file format, a query shape); do not paste whole files.
- Never copy secrets or personal data: no real provider URLs, usernames, passwords,
  API keys, Trakt client credentials, signing material, or private-network addresses.
  Use `https://provider.example`, `192.0.2.10` style placeholders.
- User-visible text: reference the string resource key (for example
  `settings_section_general`) and give the English text. The full string files for all
  seven languages are copied to `reference/strings/` in the kit.
- Reference other kit files by relative path, for example
  `[Player](../specs/30-player.md)`.

## Kit file map (for cross-references)

```
README.md                          start here
AGENTS.md                          rules for whoever builds (people or AI agents)
plan/00-product-overview.md        vision, principles, scope, non-goals, glossary
plan/01-feature-inventory.md       every feature with its ID and spec link
plan/02-roadmap.md                 build order, milestones, acceptance gates
plan/03-architecture.md            modules, layers, DI, navigation, state, threading
plan/04-data-model.md              database schema, views, preferences, files, migrations
plan/05-tech-stack-and-build.md    libraries, versions, Gradle, flavours, signing, R8
plan/06-quality-testing-release.md tests, CI, device matrix, release and update feed
plan/07-performance.md             budgets, slow-box rules, paging, profiling
plan/08-lessons-learned.md         pitfalls from the current app, and what to do instead
plan/09-owner-decisions.md         open decisions with defaults, device checks, suspected bugs
specs/01-app-shell-navigation.md   launch, activity, destinations, Back, focus rules, rail
specs/02-home.md                   Home hero, rows, continue watching, recent channels
specs/03-search.md                 unified search
specs/04-profiles-parental.md      profiles, Who is watching, parental PIN, restrictions
specs/10-sources-and-import.md     M3U, XMLTV, Xtream, import pipeline, refresh, sync
specs/11-phone-setup.md            phone setup server and QR flows
specs/20-live-tv-guide.md          guide grid, hero, rail, windows, dialing, find programme
specs/21-channel-management.md     favourites, hide, reorder, custom groups, logos, numbers
specs/22-catchup-and-reminders.md  catch-up URLs and playback, programme reminders
specs/30-player.md                 live/VOD player, overlays, tracks, subtitles, PiP, reconnect
specs/31-remote-button-mapping.md  remote key mapping during playback
specs/40-movies-and-series.md      VOD browsing, details, seasons/episodes, watched, resume
specs/41-metadata-enrichment.md    TMDB, TVmaze, matching, work keys, fix-a-match
specs/42-library-organization.md   group management, custom groups, what-is-shown rules
specs/50-discover-addons.md        Stremio-compatible addons, Discover, Library
specs/51-trakt.md                  Trakt device auth, scrobble, progress cache, Home rows
specs/60-sohva-sport.md            API-Sports, Today screen, match cards, stream matching, ticker
specs/70-settings.md               the whole Settings tree, every option and default
specs/71-backup-restore.md         encrypted .smbak backup and restore
specs/72-updates-about-diagnostics.md  in-app updater, About, legal, diagnostics file
specs/73-security-privacy.md       keystore encryption, redaction, network policy, privacy
specs/74-localization.md           languages, locale switching, metadata languages, time zone
design/01-design-system.md         colour themes, typography, spacing, shapes, focus, motion
design/02-components.md            reusable TV components with exact anatomy
design/03-screen-layouts.md        index of every screen and overlay, with the remaining layouts
design/screens/*.md                code-derived layouts: guide, home-and-shell, movies-and-series,
                                   settings, sohva-sport
design/04-icons-and-imagery.md     icons, logos, backdrops, artwork rules, asset map
design/screenshots/                reference screenshots of the current app
assets/                            every visual asset the current app ships
reference/strings/                 all string resources, all languages
reference/owntv-study.md           ideas from another open-source TV app (ideas only, no code)
```

## Template for a feature spec (`specs/*.md`)

```
# <Feature name>

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary
What the feature is and why a viewer uses it. 3–6 sentences.

## 2. Feature checklist
A flat list, one line per user-visible capability, each with a stable ID
(`<PREFIX>-01`, `<PREFIX>-02`, ...). This list is copied into plan/01-feature-inventory.md,
so make it complete: if a viewer can see it or do it, it has a line.

## 3. Entry points and navigation
How the viewer arrives, what Back does from every state, where focus lands on entry
and on return, deep links from other features.

## 4. Behaviour
Numbered functional requirements (`<PREFIX>-FR-01` ...), grouped by sub-feature. Each is
testable. Include defaults, limits, ordering and sorting rules, timing, empty/loading/error
states and their exact messages, and D-pad/remote key handling (OK, Back, Menu, long
press, number keys, media keys, channel up/down).

## 5. Screen anatomy
For each screen or overlay: regions, components, focus order, sizes (dp), text styles,
colours by token name, and which screenshot shows it (when one exists). Keep measurements
that define the look; the global tokens live in design/01-design-system.md.

## 6. Data
What is stored (tables/columns, preferences keys and defaults, files, caches, their
lifetimes), what is per profile, what is included in backups.

## 7. External interfaces
HTTP endpoints, request/response fields used, file formats, rate limits, auth, error mapping.

## 8. Edge cases and limits
Large catalogues, provider quirks, network failure, time zones, clock changes,
process death, low memory.

## 9. Lightweight by design
How this feature stays fast and small on the low-end reference box: what it keeps in
memory and the upper bound, what is paged and in which key order, what runs off the main
thread and at what priority, what is cached and for how long, what it draws per frame
(layers, overdraw, image sizes, animations) and how the look is kept with cheap rendering,
what it defers until first use, and what it costs at start-up. Name every place where the
current app was slow or ran out of memory, and the rule that prevents it.

## 10. Lessons from the current app
Bugs that were found and fixed, traps, device quirks, things to keep, things to do
differently. Cite the doc or code file where the lesson is recorded.

## 11. Acceptance tests
The checks that prove the rebuild matches: unit tests, UI (instrumentation) tests, manual
device checks, and at least one performance check on the low-end class (or its emulator
stand-in). Mirror the existing tests' intent where useful.

## 12. Reference: current code map
The beta 23 files that implement this, with one line each. For orientation only.
```

Design files and plan files use headings that fit their subject, but keep the
lightweight, lessons and code-map sections wherever they apply.
