# M8 plan: Sohva Sport

Scope (plan/02 M8): [specs/60](rebuild/specs/60-sohva-sport.md), with the Today and hub layouts in
[design/screens/sohva-sport.md](rebuild/design/screens/sohva-sport.md) and Settings › Sohva Sport in
design/screens/settings.md §6. Branch `m8-sport`, from `main` after the M7 merge (26 September 2026).

Inventory: SPORT-01…57; with them SET-54, and the sport parts of HOME (row and hero), SEARCH (sport
results), PLAY (score ticker), REM (match reminders) and BACKUP (follows, priority, favourites; M7
already carries them under beta 23's keys).

## Exit criteria (plan/02)

- Paged matching with a bounded heap on the owner-scale guide (the old matcher once ran out of
  memory): 56,164 channels, 112,328 programmes with 2,150-character descriptions, Java heap peak
  under 16 MB on the API 30 stand-in.
- Polling stops when not visible.
- Quota use within the plan's budget (free tier: 100 requests per sport API per day).

## Where things live

- `:core:model` `sport/`: sport types, event, status maps, competition keys and defaults, incidents,
  channel-name schedule, the matcher's pure rules, timeline, ordering and sections, ticker
  selection. JVM tests.
- `:core:data`: tables (database 9 → 10) for the normalised day feed per sport, the competition
  catalogue, request bookkeeping and quota, team aliases and stream decisions; the paged candidate
  queries for pairing; the follow preferences (keys beta 23's, already read by the backup).
- `:feature:sport` (new): the API-Sports client with streaming parsers per sport, the feed
  repository (cache-first, stale fallback, quota), the pairing scan and its result cache file, the
  Today screen, the match hub, and the pieces Home, Search and the player receive through the app.
- `:feature:settings`: the Sohva Sport section. `:app`: wiring, routes, reminders, the ticker.

## Order of work

1. **Provider and settings**: model, tables, client and parsers for twelve sports, the feed
   repository with its cache rules and per-sport quota, Settings › Sohva Sport (key, follows,
   competitions, priority codes, status line).
2. **Today**: the feed controller (load on start, polling only while visible and resumed, catch-up
   on return), the screen (header, tabs, sections, cards, states, focus rules), Home's row and
   hero, sport results in Search.
3. **Match hub**: header, reminders, match events (football) with the timeline, favourites.
4. **Stream pairing**: the paged scan with a token index and an accumulator, the result cache,
   decisions (confirm, reject, restore), the streams panel and sports channels row, the score
   ticker; the owner-scale memory check and the quota measurement.
5. Measurements, inventory, exit.

## Open questions (spec 60 §10), settled by the owner's rule

The spec's proposal where it makes one, else beta 23 (recorded in docs/decisions.md).
