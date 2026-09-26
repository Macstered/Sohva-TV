# M9 plan: Discover addons

Scope (plan/02 M9): [specs/50](rebuild/specs/50-discover-addons.md) with its screens in §5 and the
reference captures in `design/screenshots/older-builds/2026-09-10-discover-lab/`. Branch
`m9-discover`, from `main` after the M8 merge (26 September 2026).

Inventory: ADDON-01…75, with the Discover parts of HOME (Continue watching), PROF-09 (per-profile
Discover data) and PLAY (the addon player on the shared engine).

## Exit criteria (plan/02)

- Addon catalog JSON parsed with bounded memory (streaming reader, limits enforced while reading).
- Poster rows load lazily (active row and the next two only; zero requests at Home).
- Discover works with no IPTV source configured.
- Restricted profiles cannot reach it (no rail entry, direct access denied, every operation denied).

## Where things live

- `:feature:discover` (new, plan/03 §3): the addon protocol (endpoints, manifest, catalog/meta,
  streams and subtitles, all parsed from a size-capped stream with Moshi's okio reader), the addon
  client on the shared OkHttp base (4 permits, 15 s, no redirects, 2 MiB), `discover.db`
  (installations, catalog preferences, progress, Library; plan/04 §15.9), the encrypted response
  cache with small preview entries, access and revision checks, the manager, import, search, the
  hero resolver, and every Discover screen.
- `:core:player` / `:feature:player`: addon streams play on the one engine (plan/03 §4.13): a
  media transport with origin-bound headers and manual redirects, side-loaded subtitles with a
  timing shift, the loading screen, the subtitle picker and sync.
- `:core:net`: the phone server gains the addon mode (decision "Phone setup page").
- `:app`: `DiscoverGraph` (built on first use, never in the demo build), the route, Home's
  Continue watching source, the beta 23 Discover import (decision A1).

## Order of work

1. **Protocol and storage**: endpoints, manifest, media and source parsers, the client, the
   response cache, `discover.db` with its stores, access checks, the manager (install, refresh,
   enable, priority, remove), catalog order and visibility. JVM tests mirroring §11 "Unit".
2. **Browsing**: the Discover shell with its rail and access gate, the landing (hero, backdrop,
   Continue watching, shelves loaded active + 2), Show all grids with filters, the Discover filter
   page, Search, Library, Addons & setup (installed list, manual install, catalogs, subtitles,
   watch history). A synthetic addon server for device tests.
3. **Titles and playback**: title, series and episode pages, sources, the addon player on the
   shared engine with the loading screen, resume, retry with a fresh source, completion and next
   episode, progress and Home's Continue watching.
4. **Subtitles**: subtitle requests, the picker, automatic selection, download and parsing, sync.
5. **Import**: URL lists, file, phone, Nuvio JSON, Stremio account copy; beta 23's Discover data.
6. Measurements (zero requests at Home, 40 catalogs × 1,000 titles, heap), inventory, exit.

## Open questions (spec 50 §10), settled by the owner's rule

The spec's proposal where it makes one, else beta 23 (recorded in docs/decisions.md).
