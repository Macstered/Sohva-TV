# M4 plan: movies and series, metadata, library organisation

Milestone M4 of [plan/02-roadmap.md](rebuild/plan/02-roadmap.md). Specs:
[40](rebuild/specs/40-movies-and-series.md) (walls, details pages, progress, VOD playback),
[41](rebuild/specs/41-metadata-enrichment.md) (TMDB and TVmaze, the background worker, the match
picker) and [42](rebuild/specs/42-library-organization.md) (rules and the library manager).
Inventory: VOD-*, META-*, ORG-*, and the items earlier milestones left for M4 (GUIDE-06…08, -13,
-20…22, -35; PLAY-03, -09, -16 for films, -19, -34, -35, -46; CHAN-01's second entry). Branch
`m4-library`, started from `main` after the M3 merge on 25 September 2026.

## Exit criteria (plan/02)

1. A 200,000-film wall opens and scrolls within budget on the stand-in (spec 40 §9.1: first page
   ≤ 700 ms, D-pad within 1–2 vsyncs, no database read on the main thread, wall heap ≤ 2 MB).
2. The identity and metadata passes page through the catalogue in key order with bounded memory
   and pause during playback.
3. The organisation-view query-plan tests pass (plan/04 §15.11: every wall shape, History,
   Continue watching, Similar, Versions, counts; no `TEMP B-TREE`, no scan of a title table).
4. A finished film returns to its details page.

Owner checkpoint after M4: browse and play the owner-scale library.

## Three stages

The milestone is three specs of about 1,000 lines each. It is built in three stages on the same
branch, each ending green, so the walls can be looked at before metadata and rules exist.

| Stage | Content | Inventory |
|---|---|---|
| M4a Walls and pages | stored wall keys, the window pager, the wall screen, film and series pages, episodes, progress and the watched rule, VOD playback and completion, History, the Continue watching feed, artwork loading | VOD-01…58 except the metadata parts; PLAY-03, -09, -16, -19, -34, -35, -46 |
| M4b Metadata | TMDB and TVmaze clients, the title cleaner, the matcher, the durable queue and background worker, the film identity pass, details/cast/similar, the match picker, Settings rows | META-01…34, GUIDE-20…22, VOD-31, -32, -35, -36, -45, -53 |
| M4c Organisation | `organization_rule`, the resolver into stored `visible`/`item_position`/`content_group` columns, group and item sorts, the library manager, groups of your own | ORG-01…35, GUIDE-06…08, -13, -35, VOD-07, -19 |

## What waits for a later milestone

| Item | Needs | Milestone |
|---|---|---|
| Restricted profiles (VOD-26, ORG-17, VOD-FR-107) | profiles and allowed groups, spec 04 | M6. Every page query takes an "allowed groups" hook from the start, empty until M6 |
| Trakt overlay on ticks, positions and Continue watching (VOD-52) | spec 51 | M8 |
| Home's Continue watching row and its held-OK dialog (VOD-49/50 on screen) | spec 02 | M5; the feed and the actions are built and tested now |
| Unified Search (VOD-58's search feed on screen) | spec 03 | M5; the FTS tables arrive with it |
| Backups of rules, groups of your own and the copy preference (ORG-35) | spec 71 | M7 |
| Artwork cache limit and usage in Settings (VOD-54's Settings part) | spec 70 | M7; the limit is read and applied now |

## Decisions for the specs' open questions

Recorded in `docs/decisions.md` as they are built. Where the spec proposes a rebuild default, M4
takes it and the owner can overrule it at the checkpoint:

- Spec 40 Q1 (sorting, Unwatched on the wall): as beta 23, no wall sort bar and no Unwatched filter.
- Spec 40 Q2 (walls that span groups): one order across the destination by the room's default sort,
  as the spec proposes (paging group by group would need a composite key per group sort).
- Spec 40 Q3 (folded card position): at the standing copy's place.
- Spec 40 Q4 (restricted profiles): every read restricted, counts after the restriction (M6 hook).
- Spec 40 Q5 (episode fallback): a string, "Episode %1$d", in all seven languages.
- Spec 40 Q6 (series page start): as beta 23, the lowest season.
- Spec 40 Q7 (episode freshness): as beta 23, refresh on demand only.
- Spec 40 Q8 (focus before episodes): focus "Refresh episodes", then "Watch episode" when the
  episodes arrive if no key was pressed.
- Spec 40 Q9 (bitmap config): RGB_565 unconditionally (the owner's instruction); hardware bitmaps
  measured later only if RGB_565 costs frames.
- Spec 40 Q10 (Up from the top row): left to the platform, as beta 23.
- Spec 40 Q11, Q12, Q13: as beta 23.
- Spec 41 and 42 questions: decided when their stage starts.

## Design

- **Stored wall keys** (spec 40 §9.3, plan/04 §15.4): `movie`/`series` gain `quality_mask`,
  `claim_mask`, `picture_rank`, `search_text`, `similar_key`, `year_key`, `rating_key`; the import
  computes them (pure functions in `core:model`, unit-tested). `sort_name` stays the title key: it
  is already the one Unicode-aware sort form (decision "`sort_name`", 24 September), and SQLite's
  byte order on it is the wall's A–Z.
- **Partial indexes** `WHERE visible = 1 AND primary_copy = 1` as plan/04 §15.4 lists them.
- **Folding**: `primary_copy` is a stored flag the identity pass sets per `work_key`, using the copy
  preference. Until metadata exists (M4b) the work key is `name:<cleaned title>:<year>`.
- **Pager** (`feature:library`): pages of 120, window of 5 pages, absolute indices, keyset both ways,
  cancellable reads, prefetch 3 rows from either end.
- **Progress**: `watch_progress` (plan/04 §15.8) with `profile_id` from the first row; one write call
  from the player; the watched rule in `core:model`.
- **Images**: Coil 3 (already in the build) with a disk cache under `cache/catalogue_artwork` at the
  viewer's limit, a memory cache of 8 % of the memory class, 2 decodes at once, RGB_565, no
  cross-fade, requests at the drawn size.

## Order of work

1. Pure logic with JVM tests: the watched rule, copy claims and the preference score, quality chips,
   the year from a title, episode display titles, the breadcrumb group, custom-group membership,
   the genre vocabulary, the rating key.
2. Schema v5: the stored wall keys, the partial indexes, `watch_progress`; the migration with its
   test; the import computes the keys; query-plan tests for every wall shape.
3. The pager and the wall screen (Movies and Series), the rail with groups and History.
4. Film page, series page, episodes from `get_series_info`, progress, the VOD player path.
5. Owner-scale measurement of the walls (200,000 films), then stage M4b.
