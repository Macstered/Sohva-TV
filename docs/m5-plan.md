# M5 plan: Home and Search

Scope (plan/02 M5): [specs/02](rebuild/specs/02-home.md) (fixed hero, focus line, Continue
watching from library progress, Recently watched channels; the Trakt, Discover and sport rows
are added by M8–M10) and [specs/03](rebuild/specs/03-search.md) (channels, programmes, films,
series, episodes; sport added in M8). Branch `m5-home`, from `main` after the M4 merge
(25 September 2026).

Inventory: HOME-01…08, -11 (library part), -12…19, -26…30, -31 (library and channel part),
-32…35; SEARCH-01…07, -09…15, -17, -19. Later: HOME-09, -20…23 (Trakt, M8), HOME-10, -15
(Discover, M10), HOME-24, -25, SEARCH-08, -16 (sport, M9), HOME-36, SEARCH-18 (restricted
profiles, M6), VOD-50 and META-20 from M4.

## Exit criteria (plan/02)

- Home's first frame and Continue watching within budget from a cold start (first content frame
  ≤ 4 s on the stand-in; `home: cached resume ready` ≤ 1 s after process start at owner scale).
- Moving along a row redraws only the hero and the focused card (no recomposition per press).
- Search results arrive group by group; each group's query ≤ 100 ms at owner scale on the
  stand-in.

## Decisions taken before starting

- **Search matching** (spec 03 open question 1): the spec's §9 rules require a full-text index,
  and Android's own SQLite has FTS4, not FTS5. Word-prefix matching with FTS4 and the
  `unicode61` tokenizer (Unicode case folding; accents folded, as the walls' search already
  does through `sort_name`): "mat" finds "Match", "at" does not. Beta 23's in-word matching
  would need a bundled SQLite (1–2 MB) and replacing Room's driver.
- The index follows the tables by Room's external-content triggers, so imports keep it current
  without code of their own; existing rows are indexed once by a background rebuild after the
  first frame. Import cost measured at owner scale before the choice is final.

## Order of work

1. Search data: FTS4 tables for films, series, episodes, channels and programmes; the five
   group queries with visibility before the limit; plan tests; owner-scale timing and import
   cost.
2. Search screen: field, debounce, parallel groups appended as they finish, status line,
   result rows, routes; device tests.
3. Home data: the Continue watching projection (states, 5 s failure, per-series collapse inside
   the query), recent channels with the programme at entry, the minute ticker.
4. Home screen: hero band and panel, backdrop, rows on a focus line, structure lock, hero
   subject after 180 ms, actions dialog, Welcome, focus hand-offs; device tests.
5. Measurements on the stand-in, inventory, exit.
