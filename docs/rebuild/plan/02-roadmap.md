# Roadmap: rebuilding Sohva TV in milestones

> Build order for the rebuild, from an empty repository to a release that replaces beta 23 on
> testers' devices. Every milestone ends with an installable build, green tests, measured budgets
> on the low-end stand-in, and ticked feature IDs in
> [01-feature-inventory.md](01-feature-inventory.md).

## Principles of the order

1. **Foundations before features.** The design system, navigation with focus restoration, the
   data layer, the performance harness and the release pipeline (R8, profiles) exist before the
   first feature, because retrofitting them is what made the old code messy and slow.
2. **Live TV first.** Sources, the guide and live playback are the core of the product; every
   later feature depends on imported data.
3. **Optional features last.** Sohva Sport, Discover and Trakt are self-contained and can be
   reordered by the owner.
4. **Per-profile data from the first table.** Profiles get their screens late (M6), but every
   table that holds viewer data (favourites, recents, progress, library, reminders) carries a
   profile id from M1, so nothing has to be migrated later.
5. **Measure every milestone on the low-end stand-in.** A milestone whose budgets fail is not
   finished, even if every feature works.

## Overview

| # | Milestone | Specs | Size | Owner checkpoint |
|---|---|---|---|---|
| M0 | Foundations | plan/03–07, design/01–04, specs/01 | L | Themed shell and component gallery on the emulator |
| M1 | Sources and import | specs/10, 11, 73, part of 70 | L | Owner-scale fixture imports on the low-end stand-in |
| M2 | Live TV: guide and live playback | specs/20, 30 | XL | **Watch live TV on the new app** |
| M3 | Channels, catch-up, reminders, remote keys | specs/21, 22, 31 | M | |
| M4 | Movies and series | specs/40, 41, 42 | XL | Browse and play a 200,000-film library |
| M5 | Home and Search | specs/02, 03 | L | **IPTV daily-driver candidate** |
| M6 | Profiles and parental controls | specs/04 | M | |
| M7 | Settings completion, backup, updates, diagnostics, languages | specs/70, 71, 72, 74 | M | |
| M8 | Sohva Sport | specs/60 | L | |
| M9 | Discover addons | specs/50 | L | |
| M10 | Trakt | specs/51 | M | |
| M11 | Parity, migration and first release | all | L | **Release to testers above build 57** |

Sizes are relative (M ≈ half of L). Parallel work is possible from M4 on: M8, M9 and M10 depend
only on M0–M2 plus the player.

---

## M0 — Foundations

**Goal:** an empty but complete app skeleton that already meets the lightweight rules.

Scope:
- Repository, Gradle version catalog, module layout and dependency rules from
  [03-architecture.md](03-architecture.md); build types debug/release/demo/lab
  ([05-tech-stack-and-build.md](05-tech-stack-and-build.md)); release with R8 and resource
  shrinking; baseline/startup profile generation and `.dm` output; signing from an ignored local
  file; `versionCode` 58 or higher.
- CI: unit tests, lint for every module, release assemble, gitleaks, public-source audit
  ([06-quality-testing-release.md](06-quality-testing-release.md)).
- Design system ([design/01](../design/01-design-system.md), [design/02](../design/02-components.md)):
  tokens, seven colour themes, typography, spacing, interface scale, the cached screen background,
  and every shared component, shown on a debug-only component gallery screen with screenshot tests
  in all themes.
- App shell ([specs/01](../specs/01-app-shell-navigation.md)): launch screen matching the window
  background, activity, navigation with a back stack that restores focus, the Home rail with
  placeholder destinations, locale switching, all strings imported from `reference/strings/`.
- Data foundations ([04-data-model.md](04-data-model.md)): database with schema export and
  migration-test harness, preferences, Keystore-backed secret store, DI container, dispatchers,
  diagnostics log, error model with plain-language messages.
- Performance harness ([07-performance.md](07-performance.md)): low-end emulator stand-in,
  owner-scale synthetic fixture served on loopback, start-up Macrobenchmark, method-size and
  release-dex verification gates.
- **Spike — the guide grid.** Build a throw-away guide grid over the synthetic fixture (56,000
  channels, 4-hour window) as custom-drawn rows (one Canvas per row with a cached text measurer,
  labels formatted once per data change, off-screen blocks culled, no per-cell layers) and **one
  focusable per row** that holds the selected programme as an index (the viewer still moves block by
  block, as today). Measure a held D-pad press on the stand-in. The old grid spent ~32–39 ms of
  main-thread work per press on 17–29 nodes per row; the spike decides the grid's structure before
  M2. (Idea from the OwnTV study, [reference/owntv-study.md](../reference/owntv-study.md) items 12–13.)

Exit criteria:
- Cold start to the first shell frame within the plan/07 budget on the stand-in; release APK size
  recorded as the baseline (≤ 4 MB at this point).
- The component gallery renders every component in all seven themes; screenshot tests green.
- Focus returns to the originating rail item after Back from every placeholder destination.
- CI green; release dex verifies on the emulator.

## M1 — Sources and import

**Goal:** the viewer can add sources and the app imports them at owner scale without harm.

Scope: source model and encrypted storage ([specs/73](../specs/73-security-privacy.md)); the
Settings shell and its Sources section ([specs/70](../specs/70-settings.md),
[specs/10](../specs/10-sources-and-import.md)): add/edit/delete, enable/disable, import scope,
EPG offset, Test address / Test connection with plain-language results; the phone setup server
and QR flow ([specs/11](../specs/11-phone-setup.md)); M3U parser, streaming XMLTV parser, Xtream
client; import pipeline with atomic snapshots and one import per source and kind at a time;
refresh scheduling; progress and status texts; ANALYZE after imports.

Also: a spike that reads the old installation's database and Keystore-encrypted sources on the
emulator, to prove the migration path chosen in plan/04 before the new schema hardens.

Exit criteria: owner-scale fixture (56,164 channels, 165,600 programmes, 200,000 films) imports on
the stand-in within the plan/07 import budgets with the Java heap under 128 MB; the concurrent
import test (two imports of one source) leaves the source intact; parser tests cover the provider
quirks in specs/10; no credential appears in logs or diagnostics.

## M2 — Live TV: guide and live playback

**Goal:** a viewer can browse the guide and watch live channels as in beta 23.

Scope: the whole of [specs/20](../specs/20-live-tv-guide.md) (hero, rail, grid, windows,
grouping and sorting, options sheet, dialling, find programme, paging, focus rules) built on the
M0 spike's grid; the live part of [specs/30](../specs/30-player.md): Media3 setup, overlays,
channel up/down, previous channel, number dialling, audio and subtitle choice, picture shape,
info panel, reconnection with plain-language causes, subtitle appearance, auto frame rate,
keep-watching-in-a-corner, open in another player, media session.

Exit criteria: guide opens on the first group of a 56,000-channel source and a held D-pad press
meets the key-press budget on the stand-in; no focus jumps when programmes arrive; overlays cost
nothing while hidden; live playback survives a dropped stream with the right message; guide and
player UI tests from the specs green.

**Owner checkpoint:** a build that watches live TV, installed only on the emulator or, with the
owner's explicit go, on a device the owner names.

## M3 — Channels, catch-up, reminders, remote keys

Scope: [specs/21](../specs/21-channel-management.md) (favourites, hide, reorder, custom groups,
own logo by address or phone picture, own number, manual XMLTV mapping, locked channels),
[specs/22](../specs/22-catchup-and-reminders.md) (all catch-up URL schemes, watch from start,
past programmes; reminders with in-app alerts, the overlay-permission prompt, rescheduling after
restart), [specs/31](../specs/31-remote-button-mapping.md) (press/hold mapping for every key).

Exit criteria: catch-up URL builder unit tests cover every scheme; a reminder fires while another
screen of the app is open and after a process restart; channel edits survive re-import; the
channel manager reads in pages (the old one read every channel at once).

## M4 — Movies and series

Scope: [specs/40](../specs/40-movies-and-series.md) (walls, grouping by provider group or
genre, sorting, Unwatched filter, details pages, seasons and episodes, resume, next episode,
watched state and rules, duplicate copies, VOD playback and completion),
[specs/41](../specs/41-metadata-enrichment.md) (TMDB with the viewer's key, TVmaze, the
background enrichment worker, fix a wrong match, 22 metadata languages, attribution),
[specs/42](../specs/42-library-organization.md) (library manager for LIVE, MOVIES and SERIES
rooms, custom groups, show/hide/order rules).

Exit criteria: a 200,000-film wall opens and scrolls within budget on the stand-in; the identity
and metadata passes page through the catalogue in key order with bounded memory and pause during
playback; the organisation-view query-plan tests pass; a finished film returns to its details.

**Owner checkpoint:** browse and play the owner-scale library.

## M5 — Home and Search

Scope: [specs/02](../specs/02-home.md) (fixed hero, focus line, Continue watching from library
progress, Recently watched channels; the Trakt, Discover and sport rows are added by M8–M10) and
[specs/03](../specs/03-search.md) (channels, programmes, films, series, episodes; sport added in
M8).

Exit criteria: Home's first frame and Continue watching within budget from a cold start; moving
along a row redraws only the hero and the focused card; search results arrive group by group.

**Owner checkpoint:** a daily-driver candidate for IPTV-only viewers.

## M6 — Profiles and parental controls

Scope: [specs/04](../specs/04-profiles-parental.md): profiles, Who is watching at start and
from the rail, per-profile data (already keyed since M1), parental PIN, locked channels,
restricted profiles limited to chosen live, film and series groups, PIN before Settings and
before switching away from a restricted profile.

Exit criteria: every per-profile table and preference is isolated in tests; restricted profiles
never see Discover or Trakt entry points; the clear-state test rule resets profiles.

## M7 — Settings completion, backup, updates, diagnostics, languages

Scope: the rest of [specs/70](../specs/70-settings.md) (General and Playback sections complete,
every option in the table), [specs/71](../specs/71-backup-restore.md) (streamed `.smbak` export
and restore, format-compatible with beta 23 backups),
[specs/72](../specs/72-updates-about-diagnostics.md) (update check against the public releases,
checksum and `.dm` install through a PackageInstaller session, About with "Changed since", legal
screen, Save diagnostics with redaction), [specs/74](../specs/74-localization.md) (all seven
languages complete to the old app's level, time zone choice, formats).

Exit criteria: a beta 23 `.smbak` restores into the rebuild; the updater installs a test release
with its profile (`reason=install-dm`) on the emulator; diagnostics contain no secret; every
string present in every language the old app had.

## M8 — Sohva Sport

Scope: [specs/60](../specs/60-sohva-sport.md): the viewer's API-Sports key, twelve sports and
their competitions, Today screen with Live now / Upcoming, filters, match cards and event
timelines, Remind me, stream pairing with confirm/reject/restore and saved decisions,
country/language ordering, the score ticker during playback, the Home sport row, sport results
in Search.

Exit criteria: paged matching with a bounded heap on the owner-scale guide (the old matcher once
ran out of memory); polling stops when not visible; quota use within the plan's budget.

## M9 — Discover addons

Scope: [specs/50](../specs/50-discover-addons.md): addon install (URL, URL-list file, phone,
Stremio account copy, Nuvio JSON), catalogs and Show all, search, filters, catalog order and
visibility per profile, title pages, source selection, addon player, subtitles and timing,
Library, progress and history (feeding Home's Continue watching).

Exit criteria: addon catalog JSON parsed with bounded memory; poster rows load lazily; Discover
works with no IPTV source configured; restricted profiles cannot reach it.

## M10 — Trakt

Scope: [specs/51](../specs/51-trakt.md): device-code sign-in per profile, scrobbling from the VOD
and Discover players (never live TV), progress and watched overlay on cards, resume from another
device, Watch next and Recommended for you rows on Home, disconnect.

Exit criteria: matching by TMDB/IMDb id only; sync is incremental and never runs during playback
except scrobbles; the rows appear independently of slower Home sections.

## M11 — Parity, migration and first release

Scope:
- Walk [01-feature-inventory.md](01-feature-inventory.md) line by line; every ID implemented and
  tested, or explicitly deferred by the owner.
- The existing-installs path chosen in [04-data-model.md](04-data-model.md), tested as an upgrade
  over a real beta 23 install on the emulator (sources, profiles, favourites, progress, custom
  groups, reminders, Trakt connection, Discover data as applicable).
- Performance pass against every budget in [07-performance.md](07-performance.md) on the
  stand-in, and one supervised check on a low-end box if the owner allows it.
- Release pipeline end to end ([06-quality-testing-release.md](06-quality-testing-release.md)):
  version above 57, tester documents, five release assets (APK, `.api31.dm`, `.api28.dm`,
  `SHA256SUMS.txt`, tester ZIP), the release-body contract for the updater.

Exit criteria: the owner's go to publish. Publishing, tagging and any install on the owner's
devices happen only with the owner's explicit wording for this release.

## Cross-cutting work in every milestone

- **Performance:** measure the milestone's screens on the stand-in, record results in
  `docs/performance-log.md` of the new repository, fix regressions before moving on.
- **Localisation:** new strings land in all seven languages at the old app's completeness.
- **Accessibility:** icon-only controls labelled; decorative icons cleared from semantics; fewer
  semantics nodes in dense grids.
- **Documentation:** decisions in `docs/decisions.md`; feature IDs ticked; the kit updated if a
  spec was wrong.
- **Security:** gitleaks and the source audit on every push; no secret in fixtures.

## Risks and how the plan answers them

| Risk | Answer |
|---|---|
| The guide misses its key-press budget in Compose on the low-end GPU/CPU | M0 spike decides the custom-drawn grid before M2 |
| Existing testers lose data | Migration path chosen before M1's schema hardens; spike in M1; upgrade test in M11 |
| A whole-catalogue job exhausts memory | Hard rule 2 in AGENTS.md; paging tests with owner-scale fixtures from M1 |
| Release build differs from debug (R8) | Release built and dex-verified in CI from M0 |
| Scope creep during the rebuild | Parity first; new features wait until M11 is released |
| Focus regressions | Focus tests with slow data and real key events in every milestone |
