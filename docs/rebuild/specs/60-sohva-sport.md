# Sohva Sport

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

Layout, measurements, colours and state visuals of the Today screen and the match hub are already
extracted in [design/screens/sohva-sport.md](../design/screens/sohva-sport.md) (cited below as
**D§n**); the Settings visuals are in [design/screens/settings.md](../design/screens/settings.md)
§6 "Sohva Sport" (cited as **S§6**). This spec covers behaviour, data, the provider interface,
stream pairing and cost. Navigation into and out of the screen is owned by
[specs/01](01-app-shell-navigation.md) (SHELL-12, SHELL-15, SHELL-32, SHELL-FR-73).

## 1. Summary

Sohva Sport shows today's games for the sports and competitions the viewer follows, with live
scores, and pairs each game with the viewer's own IPTV channels that are likely to carry it. Game
data comes straight from API-Sports with the viewer's own key (twelve sports, one API product
each); nothing goes through a Sohva server. A game card opens a **match hub** with football match
events (goals, cards, substitutions, VAR) and a list of candidate streams found in guide
programmes and channel names, each marked Available or Possible, which the viewer can confirm,
reject or restore; decisions are kept. Countries/languages in channel names can be ranked. The
same feed drives the Home "Today's sport" row, sport results in Search, match reminders and the
score ticker over the player. Requests are spent only while someone is looking, because the free
API tier is a small daily allowance.

## 2. Feature checklist

Provider and settings
- SPORT-01 The viewer's own API-Sports key: masked field, Save key / Remove key, stored encrypted on the TV.
- SPORT-02 The key can also arrive from phone setup ([specs/11](11-phone-setup.md)).
- SPORT-03 Twelve sports: football, ice hockey, AFL, basketball, baseball, handball, rugby, volleyball, American football, MMA, Formula 1, NBA.
- SPORT-04 Follow or unfollow each sport (defaults: football, ice hockey, AFL).
- SPORT-05 Per-sport competition list from the provider (football: current season only), with search by name or country and "Selected X of Y".
- SPORT-06 Follow or unfollow single competitions (defaults: seven football competitions, Liiga, AFL).
- SPORT-07 Sports without a competition list (MMA, Formula 1, NBA) say so and show every event of the day.
- SPORT-08 Channel country/language priority codes (up to 8, e.g. `ES, EN, UK`) with Save order.
- SPORT-09 Settings status line: sports time zone, service state (cache / updated / stale fallback / multiple sources / refreshing / error), remaining API quota per sport, polling interval.
- SPORT-10 Sohva Sport uses the app time zone (Settings > General).

Feed
- SPORT-11 Today's events for all followed sports and competitions, in the app time zone.
- SPORT-12 The saved feed is shown at once; the network refresh follows, sport by sport.
- SPORT-13 During a provider outage, data up to 24 hours past its freshness is shown instead of an error.
- SPORT-14 Automatic refresh every 5 / 10 / 30 minutes only while Sohva Sport or the score ticker is visible and the app is in front.
- SPORT-15 One refresh on return when the data is older than the polling interval.
- SPORT-16 Manual refresh (header Refresh; Try again on the error card).
- SPORT-17 Status mapping per sport (live, upcoming, finished, postponed, cancelled, interrupted, unknown).

Today screen
- SPORT-18 Header: "SOHVA SPORT" wordmark, weekday, date and time in the app zone (12/24 h per the TV), three icon actions: Refresh, Guide, Settings.
- SPORT-19 Filter tabs All, one per followed sport, Watchable, Favourites, each with its event count.
- SPORT-20 Sections Live now, Later today, Sports channels now, Finished; empty sections hidden.
- SPORT-21 Ordering: live, scheduled, disrupted, finished; then kick-off minute; then competition.
- SPORT-22 Match card: competition logo and name, status or live badge, two team marks (crest over initials in the sport's accent), score or kick-off time, AFL goals/behinds, football minute, watch call to action, favourite star.
- SPORT-23 Call to action: "WATCH · N CHANNELS", "N POSSIBLE CHANNEL MATCHES" or "No broadcast available".
- SPORT-24 Live badge with the minute and a short pulse when the minute changes.
- SPORT-25 Sports channels now: up to 8 channels with an Available match, OK plays the channel.
- SPORT-26 Initial focus on the first live game, else the first upcoming, else the first finished, else the All tab.
- SPORT-27 Loading, error (with Try again) and per-filter empty states.

Match hub
- SPORT-28 OK on a card opens the match hub over the list; Back or Close closes it.
- SPORT-29 Hub header: competition logo and name, status, "start · N watchable streams", both teams with crests, large score and score detail.
- SPORT-30 Remind me / Reminder set for a scheduled game that has not started.
- SPORT-31 Match events panel (football): timeline band (home above, away below, half-time tick), incident list, Refresh.
- SPORT-32 Incident list scrolls with Up/Down and releases focus at either end.
- SPORT-33 Match events states: not available for this sport, loading, none yet, error with Try again, cached data warning.
- SPORT-34 Streams panel: rows with confidence, source (TV GUIDE / M3U NAME), channel name, programme or detail, stream tag chips, start-offset explanation.
- SPORT-35 Watch plays the channel; Back from the player returns to the open hub.
- SPORT-36 Confirm a Possible stream, Reject any undecided stream, Restore a decided one; "Confirmed by you".
- SPORT-37 Decisions survive refreshes, re-imports and restarts.
- SPORT-38 Stream order stays fixed while the hub is open.
- SPORT-39 Hub opens from Home, a reminder or a notification directly on the named game.

Stream pairing
- SPORT-40 Candidates from guide programmes within 120 minutes of kick-off.
- SPORT-41 Candidates from M3U channel names that name both teams.
- SPORT-42 Channel-name clock times with explicit zones (CET, CEST, EET, EEST, UTC/GMT and offsets), AM/PM, and dates (ISO, month names, `18/9`).
- SPORT-43 Team aliases (built-in football aliases plus a stored alias table).
- SPORT-44 Confidence Available / Possible / Rejected, with the ordering rules.
- SPORT-45 Country/language priority reorders streams within one confidence level.
- SPORT-46 Pairing results cached across restarts, recomputed when channels, guide or rules change.
- SPORT-47 Hidden channels, disabled sources and hidden groups are never offered.

Favourites and reminders
- SPORT-48 Favourites filter and star on favourite cards (per profile). Beta 23 has no control to add one (§10).
- SPORT-49 A match reminder fires one minute before kick-off and opens its stream, or the hub when no stream was known.

Elsewhere in the app
- SPORT-50 Home "Today's sport" row: first 6 games, total count, a card opens the hub.
- SPORT-51 Home hero for a focused sport card (kicker, teams, competition, minute or start, score, crest backdrop).
- SPORT-52 Search results of type SPORT (team or competition names) open Sohva Sport.
- SPORT-53 Score ticker over the player: live games then games starting within 3 hours, at most 4 rows.
- SPORT-54 Ticker toggled from the player quick menu or a remote button; a player shortcut opens Sohva Sport.
- SPORT-55 Followed sports, competitions, priority codes and favourites travel in encrypted backups; the key does not.
- SPORT-56 About/legal: API-Sports notice, terms link, key disclosure text.
- SPORT-57 Demo flavour substitutes fictional sport data; the Lab variant never refreshes automatically.

## 3. Entry points and navigation

| From | Action | Lands on | Focus |
|---|---|---|---|
| Rail | Sohva Sport | `Today` | SPORT-FR-60 rule |
| Home | Sohva Sport destination | `Today` | SPORT-FR-60 rule |
| Home "Today's sport" card | OK | `Today`, hub open on that game once it is in the list | hub rule SPORT-FR-79 |
| Search result of type SPORT | OK | `Today` (the day's list, not the hub) | SPORT-FR-60 rule |
| Reminder alert "Watch" / notification (`OPEN_EVENT`) | event reminder without a channel | `Today`, hub open on the game | hub rule |
| Reminder alert / notification | event reminder with a channel | Player on that channel (not "for the guide") | – |
| Player | Sport shortcut or remote action `GO_SPORT` | Back stack reset to `[Home, Today]` | SPORT-FR-60 rule |
| Guide / Settings | – | Today has a Guide and a Settings icon | – |

- SPORT-NAV-01 Back with the hub open closes the hub only. Back with the hub closed pops the
  stack (Back rules in [specs/01](01-app-shell-navigation.md)).
- SPORT-NAV-02 Playing a channel from Today (sports-channel row or hub Watch) passes
  `forGuide = false`; Back from the player returns to Today with the same filter and the same
  hub open (saved state keyed `today`; SHELL-12). Focus then lands by the hub rule.
- SPORT-NAV-03 Leaving Settings by Back always asks the feed to refresh (a key or follows may
  have changed); the refresh obeys the cache rules, so it costs a request only when a feed is
  stale.
- SPORT-NAV-04 An "open this game" request (Home card, reminder) waits until the game is in the
  loaded list, then opens the hub and is consumed. Current flaw: a request for a game that never
  appears (yesterday's game) stays pending; a later refresh containing it would open the hub
  unexpectedly. Rebuild: drop the request after the first complete load that lacks it.
- SPORT-NAV-05 Hub close: rebuild returns focus to the card that opened the hub (today it is
  left to the framework; D§8).

## 4. Behaviour

### 4.1 Key, followed sports and competitions (Settings › Sohva Sport)

- SPORT-FR-01 The key field accepts at most 512 characters; the save trims it and rejects a key
  longer than 512 or containing a line break with `error_api_sports_key_invalid` ("The
  API-Sports key is invalid"). A failed write shows `error_api_sports_settings_save`.
- SPORT-FR-02 The button reads **Remove key** (`sports_remove_key`) while the field is empty and
  **Save key** (`sports_save_key`) otherwise. Saving blank removes the key, closes the follow
  menu, forgets loaded competition lists and reports `sports_key_removed` ("API-Sports key
  removed"); saving a key reports `sports_key_saved` ("API-Sports key saved securely"). Saving
  does not refresh by itself (SPORT-NAV-03 does on leaving).
- SPORT-FR-03 Without a key the group shows `sports_follow_requires_key` ("Save an API-Sports key
  to open sport and competition selection."). With a key, **Choose followed sports and
  competitions** (`sports_follow_open`) opens the follow menu in place; its Back button
  (`action_back`) closes it.
- SPORT-FR-04 The follow menu: heading `sports_follow_title` ("Followed sports and
  competitions"); a row of the twelve sport buttons in enum order (Football, Ice hockey, AFL,
  Basketball, Baseball, Handball, Rugby, Volleyball, American football, MMA, Formula 1, NBA;
  labels `sports_follow_*`), each drawn *selected* when the sport is followed; OK on a sport
  makes it the sport being edited (default Football) and clears the search. Rebuild: also mark
  which sport is being edited — today nothing shows it except the toggle label.
- SPORT-FR-05 The toggle reads `sports_follow_enable_sport` ("Include sport in Sohva Sport") or
  `sports_follow_disable_sport` ("Remove sport from Sohva Sport") for the sport being edited and
  flips its follow state immediately.
- SPORT-FR-06 Competitions of the sport being edited: for MMA, Formula 1 and NBA the text
  `sports_competitions_none` ("This sport has no competitions to choose from; every event of the
  day is shown."). Otherwise the list is loaded on first view of that sport in this Settings
  visit (kept in memory for the visit): `sports_competitions_loading` ("Loading current leagues
  and cups…"); on failure `sports_competitions_error` ("The competition list could not be
  loaded. Check the API key and connection.") with **Try again** (`action_retry`).
- SPORT-FR-07 Loaded list: `sports_competitions_count` ("Selected %1$d of %2$d competitions"), a
  search field (`sports_competitions_search`, "Search league, cup or country", max 100
  characters) matching name or country, case-insensitive substring; when a query is present,
  `sports_competitions_results` ("%1$d results"). Rows "Name · Country" (country omitted when
  unknown), sorted followed first, then country, then name; each row drawn selected when
  followed; OK toggles it immediately.
- SPORT-FR-08 Competition preference keys are `<SPORT_ENUM_NAME>:<providerLeagueId>`, e.g.
  `FOOTBALL:39`, `ICE_HOCKEY:16`, `AUSTRALIAN_FOOTBALL:1`.
- SPORT-FR-09 Defaults while nothing has been saved: followed sports `FOOTBALL`, `ICE_HOCKEY`,
  `AUSTRALIAN_FOOTBALL`; followed competitions football `2` (UEFA Champions League), `3` (UEFA
  Europa League), `39` (Premier League), `78` (Bundesliga), `135` (Serie A), `140` (La Liga),
  `848` (UEFA Conference League); ice hockey `16` (Liiga); AFL `1`. The first toggle writes the
  defaults plus the change.
- SPORT-FR-10 Priority codes (first group of the section, **Save order** is the section's first
  focus): free text (max 80 characters) split on commas; each piece trimmed and upper-cased,
  kept only when it is a recognised code or synonym (table in SPORT-FR-118), mapped to its
  canonical code, de-duplicated, first 8 kept. The field is rewritten as the saved list joined
  by ", " and the status `sports_channel_priority_saved` ("Channel order saved") is set. Empty
  restores the default order.
- SPORT-FR-11 Every change to followed sports or competitions or to the app time zone triggers a
  feed refresh at once (SPORT-FR-20). Favourite and priority changes only re-present what is
  loaded.
- SPORT-FR-12 Settings status line (`sportMateStatusSummary`), parts joined by " · ":
  `today_subtitle_base` ("Sports timezone: %1$s", the zone id); then one of: "Refreshing…"
  (`today_subtitle_refreshing`, while loading with events shown), the current error message, or
  `today_subtitle_cache` ("Sports service: %1$s") with `cache_hit` "cache" / `cache_miss`
  "updated" / `cache_stale` "stale fallback" / `cache_mixed` "multiple sources"; then, when any
  quota is known, `today_subtitle_quota` ("API quota %1$s") with "LABEL remaining" pairs sorted by
  source and joined by " / " (labels: football F, AFL AFL, hockey H, basketball BK, baseball BB,
  handball HB, rugby R, volleyball V, American football NFL, MMA MMA, Formula 1 F1, NBA NBA,
  other API); last `today_subtitle_polling` ("refresh every %1$d min").
- SPORT-FR-13 Rebuild: every Settings section gets its own status line (S§9); the sport key and
  order messages are written but invisible today.

### 4.2 Loading today's feed

- SPORT-FR-20 A refresh cancels any refresh in progress, then: reads the app zone, followed
  sports and competition keys **as they are at that moment**; computes today's date in that zone;
  sets loading and clears the error.
- SPORT-FR-21 Feeds: one per followed sport. A sport with competitions is included only when at
  least one of its competition keys is followed, and its events are filtered to those ids.
  **Beta 23 bug:** the same rule is applied to MMA, Formula 1 and NBA, which have no competition
  keys, so following them fetches nothing and their tabs stay empty. Rebuild: a followed sport
  without competitions is always a feed, unfiltered (the parser already passes every event).
- SPORT-FR-22 Phase 1 (saved data): every feed's saved payload that is still inside its stale
  window is parsed, at most 3 feeds at a time; each result is published as soon as it is ready
  together with any cached pairing results (SPORT-FR-100), so cards and watch counts appear
  before the network answers.
- SPORT-FR-23 Phase 2 (network): every feed is requested through the cache (SPORT-FR-30), at most
  3 at a time; each success is published as it arrives.
- SPORT-FR-24 Combining: events of all feeds, sorted by SPORT-FR-49 before they are
  published. Cache state is the single state shared by all feeds, else "mixed" ("hit" when
  none). Quota is the minimum of the known remaining counts; per-source quotas are kept for the
  status line.
- SPORT-FR-25 Result: if every feed failed and nothing was loaded, the refresh fails with
  SPORTS_UNAVAILABLE (loading off, events kept as they were). If some feeds failed, the result is
  published with PARTIAL_DATA. On success the new list replaces the old; pairing results and
  loaded match events are kept only for games still present; the polling interval is recomputed
  (SPORT-FR-26); the load time is recorded; then pairing runs (SPORT-FR-100) and its results are
  published.
- SPORT-FR-26 Polling interval: 5 minutes when any game is LIVE; else 10 minutes when a SCHEDULED
  game starts within the next 2 hours (start in `[now, now + 2 h]`); else 30 minutes. Default
  shown before the first load: 30.
- SPORT-FR-27 Automatic refresh is enabled only while (`Today` is the top destination **or** the
  score ticker is visible over a live or VOD player) **and** the activity is at least RESUMED.
  When enabled, the next refresh is scheduled one interval after the last load or failure. When
  disabled, the scheduled refresh is cancelled.
- SPORT-FR-28 On enabling: if a load is running nothing more happens; if no events are loaded,
  or the last successful load is at least one interval old (or never happened), refresh now;
  otherwise schedule the next refresh.
- SPORT-FR-29 Independently of visibility, the feed is loaded once when the app starts (after
  Home's first Continue-watching read; SHELL-FR-73) and on every SPORT-FR-11 change, so Home,
  Search and reminders have today's list. Lab builds never refresh automatically; manual refresh
  still works. The date is recomputed on each refresh, so the list rolls over to the new day at
  the first refresh after midnight.

### 4.3 Cache and quota (per request)

- SPORT-FR-30 Every request is cache-first. Keys: events `"<provider>|events|<yyyy-MM-dd>|<zoneId>"`,
  competitions `"<provider>|competitions|current"` for football and `"…|all"` for the others,
  football match events `"football|incidents|<fixtureId>"`. `<provider>` is `football`,
  `hockey`, `afl`, `basketball`, `baseball`, `handball`, `rugby`, `volleyball`,
  `american-football`, `mma`, `formula-1`, `nba`.
- SPORT-FR-31 Before a request, entries whose stale window has ended are deleted. A fresh entry
  (now before `expiresAt`) is returned as state "hit" without a network call — this includes
  the header Refresh and the hub's match-event Refresh.
- SPORT-FR-32 Freshness: events for today 20 min; for a past date 24 h; for a future date 2 h;
  competitions 7 days; football match events 2 min. Stale window: 24 h after expiry for all.
- SPORT-FR-33 A network success stores the raw payload with source `api-sports-<provider>`, the
  remaining quota (response header `x-ratelimit-requests-remaining`, integer or unknown),
  fetched/expiry/stale times, and returns state "miss".
- SPORT-FR-34 A network failure falls back to a saved entry still inside its stale window, state
  "stale"; otherwise the error is raised (SPORT-FR-130). Cancellation is never turned into a
  fallback.
- SPORT-FR-35 There is no retry and no exponential back-off: a failed refresh waits for the next
  polling tick (5 / 10 / 30 min) or a manual refresh. Quota is displayed, never enforced.
- SPORT-FR-36 Consequence to keep in mind: with a 20-minute freshness, a live game polled every
  5 minutes gets new data from the network at most every 20 minutes (§10, open question 2).

### 4.4 Parsing and normalisation

- SPORT-FR-37 Common to all sports: the JSON root must be an object; a non-empty `errors` value
  (non-empty array or object, or non-blank primitive) fails the request with
  `error_api_sports_service_error`. Items come from the `response` array; an item that lacks a
  required field is skipped silently. Text fields are trimmed, blank means absent, and capped at
  4,000 characters. A field holding an object where a primitive was expected reads as absent.
  Image URLs are kept only when they start with `https://` and are at most 2,048 characters.
- SPORT-FR-38 Start instant: from `date` (ISO offset date-time, else ISO instant) or else
  `timestamp` (epoch seconds as number or string, > 0). Derived: `startEpochMillis`,
  `startMinuteOfDay` (hour × 60 + minute in the requested zone) and `startLabel` ("HH:mm", always
  24-hour, in the requested zone).
- SPORT-FR-39 Score: "`<home> – <away>`" (spaces around U+2013) when both are known; for AFL only
  when LIVE or FINISHED. Status label: only the football elapsed minute while LIVE, as
  "`<minute>′`" (U+2032); every other status label is derived from the status on screen, so it
  follows the interface language.
- SPORT-FR-40 Event fields per sport (ids are stable strings used for decisions, reminders and
  favourites):

| Sport | Event id | Competition (id / name / logo) | Sides | Score | Details |
|---|---|---|---|---|---|
| Football | `api-sports:football:<fixture.id>` | `league.id` / `league.name` / `league.logo` | `teams.home`, `teams.away` (`name`, `logo`) | `goals.home`, `goals.away` | yes; minute from `fixture.status.elapsed`; status `fixture.status.short`; start from `fixture` |
| Ice hockey | `api-sports:hockey:<id>` | `league.*` | `teams.home/away` | `scores.home/away` (int) | no; status `status.short` |
| Basketball, baseball, handball, rugby, volleyball, American football | `api-sports:<provider>:<id or game.id>` | `league.*` | `teams.home/away` | `scores.home/away`: int, or object read as `total`, else `score`, else `points` | no; status `status.short`, else `game.status.short`; start from the item, else `game`, else `game.date` |
| AFL | `api-sports:afl:<startEpochSeconds>:<home.id>:<away.id>` (requires `game.id` > 0) | `league.id`, name always "AFL", no logo | `teams.home/away` (ids required) | `scores.home.score`, `scores.away.score`; detail "`g.b – g.b`" from `goals`/`behinds` when LIVE or FINISHED and all six numbers known | no |
| MMA | `api-sports:mma:<id>` | id "", name "`<event> · <category>`" (either may be missing; "MMA" when both are) | `fighters.first`, `fighters.second` (`name`, `logo`) | none | no |
| Formula 1 | `api-sports:formula-1:<id>` | id "", name "Formula 1 · `<type>`" (type such as Race, Qualifying) | home = `competition.name` (Grand Prix) with `circuit.image`; away = `circuit.name`, else `competition.location.city`, else "" | "`<laps.current> / <laps.total>`" while LIVE and total > 0 | no; status `status` string or `status.short` |
| NBA | `api-sports:nba:<id>` | id "", name "NBA", no logo | `teams.home` and `teams.visitors` (else `away`) | `scores.home` and `scores.visitors` (object → `points`) | no; start `date.start`; status `status.short` string or number; items whose `league` is present and not `standard` are skipped |

- SPORT-FR-41 Competition filter: for sports with competitions, only events whose competition id
  is in the requested set are kept (the whole day is fetched and filtered locally).
- SPORT-FR-42 AFL duplicates: events sharing an id collapse to one, keeping the highest status
  rank FINISHED 4 > LIVE 3 > POSTPONED / CANCELLED / INTERRUPTED 2 > SCHEDULED 1 > UNKNOWN 0.
- SPORT-FR-43 Status maps (codes upper-cased and trimmed; anything else is UNKNOWN):

| Sport | SCHEDULED | LIVE | FINISHED | POSTPONED | CANCELLED | INTERRUPTED |
|---|---|---|---|---|---|---|
| Football | TBD, NS | 1H, HT, 2H, ET, BT, P, LIVE | FT, AET, PEN, AWD, WO | PST | CANC | SUSP, INT, ABD |
| Ice hockey | NS | P1, P2, P3, OT, PT, BT | FT, AOT, AP, AW | POST | CANC | INTR, ABD |
| AFL | NS, TBD | Q1, 1Q, Q2, 2Q, HT, Q3, 3Q, Q4, 4Q, OT, LIVE | FT, AOT, AW | POST, PST | CANC | SUSP, INTR, ABD |
| Formula 1 | SCHEDULED, NS, TBD | LIVE, IN PROGRESS, RUNNING, STARTED | COMPLETED, FINISHED, FT | POSTPONED, PST, DELAYED | CANCELLED, CANCELED, CANC | SUSPENDED, ABANDONED, RED FLAG, INTERRUPTED |
| NBA | 1, NS, SCHEDULED | 2, LIVE, IN PLAY | 3, FT, FINISHED | POST, PST, POSTPONED | CANC, CANCELLED | – |
| Basketball, baseball, handball, rugby, volleyball, American football, MMA | NS, TBD | LIVE, HT, BT, OT, 1H, 2H, 1Q–4Q, S1–S5, or any code starting with Q, P, IN or SET | FT, AOT, AP, AW | POST, PST | CANC | SUSP, INTR, INT, ABD |

  The generic rows are checked in the order SCHEDULED, FINISHED, POSTPONED, CANCELLED,
  INTERRUPTED, LIVE, so `PST` is postponed although it starts with P.
- SPORT-FR-44 Competition catalogue parsing: each item's `league` object (or the item itself);
  id > 0 and name required; country from `country.name` (item or league) or a `country` string;
  `type`; logo. De-duplicated by preference key and sorted by country, then name.

### 4.5 Today screen

- SPORT-FR-45 Header (D§2): the clock label uses the platform's best pattern for `EEEEdMMM` in
  the interface locale, then "  ·  ", then `Hm` or `hmma` per the TV's 24-hour setting, in the app
  zone (the device zone if the id is invalid). It updates every 60 s from a background ticker.
  Rebuild: align the tick to the minute boundary (today it can lag up to 59 s).
- SPORT-FR-46 Header actions (content descriptions `today_refresh_description` "Refresh sports
  data", `today_guide_description` "Open the TV guide", `today_settings_description` "Open
  settings"): Refresh → SPORT-FR-20; Guide → the guide; Settings → Settings (through its PIN
  gate when one applies, [specs/70](70-settings.md)).
- SPORT-FR-47 Filters (D§3): tab list = All, then each followed sport in the fixed order
  Football, Ice hockey, AFL, Basketball, Baseball, Handball, Rugby, Volleyball, American
  football, MMA, Formula 1, NBA, then Watchable, Favourites. Labels `today_filter_*`. Counts are
  computed once per list change over all events of any status. Watchable = events with at least
  one Available stream; Favourites = events marked favourite. OK selects a tab (focus alone
  does not). If the selected sport stops being followed, the selection returns to All. The
  selection survives leaving for the player and process recreation (saved state).
- SPORT-FR-48 Sections from the filtered, sorted events: **Live now** = LIVE; **Later today** =
  SCHEDULED, POSTPONED, INTERRUPTED, UNKNOWN; **Finished** = FINISHED, CANCELLED. Every event is
  in exactly one section. **Sports channels now** sits between Later today and Finished. Hints:
  "%1$d game(s)" (`today_match_count`) for Live now and Finished; `today_section_later_help`
  "This evening" (static, a flaw, D§10) for Later today; `today_sports_channels_help` "Live
  broadcasts from the guide".
- SPORT-FR-49 Sort (`TodayEventOrdering`): status rank LIVE 0, SCHEDULED 1, POSTPONED /
  INTERRUPTED / UNKNOWN 2, FINISHED / CANCELLED 3; then `startMinuteOfDay`; then competition name.
- SPORT-FR-50 Match card content (D§5): competition logo 18 dp when present; competition
  upper-cased; status badge (SPORT-FR-52); home team mark, centre column (score if present, else
  kick-off label; then score detail; then the status label when non-blank), away team mark;
  call to action (SPORT-FR-51); a star when the event is a favourite. Team initials: first letter
  of each of the first two whitespace-separated words, upper-cased; "?" if none.
- SPORT-FR-51 Call to action: Available count > 0 → plural `today_watch_channels` ("WATCH · %1$d
  CHANNEL(S)"); else Possible count > 0 → `today_possible_channels` ("%1$d POSSIBLE CHANNEL
  MATCH(ES)"); else `today_no_broadcast` ("No broadcast available"). Available includes streams
  the viewer confirmed; rejected ones count in neither.
- SPORT-FR-52 Status badge: LIVE draws the live badge (dot, the minute when known, "LIVE"
  from `status_live` upper-cased); others the localized status (`status_scheduled` "Upcoming",
  `status_finished` "Finished", `status_postponed` "Postponed", `status_cancelled` "Cancelled",
  `status_interrupted` "Interrupted", `status_unknown` "Unknown status"). The pulse runs 2 cycles
  of 240 ms down to alpha 0.25 and 240 ms back when the badge appears and whenever the minute
  label changes; never infinite.
- SPORT-FR-53 Sports channels now (`todaySportsChannels`): from the filtered events' pairing
  results, streams whose confidence is Available and which are not rejected; each becomes a row
  (channel, channel name, programme title, programme start, live = its game is LIVE); sorted live
  first, then programme start, then channel name case-insensitive; one row per channel (first
  wins); at most 8. The row shows the programme title, "`HH.mm  ·  channel`" (start in the app
  zone; the fixed "HH.mm" pattern is a flaw — use the locale's time pattern) and "LIVE"
  (`today_channel_live`) when live. OK plays the channel (SPORT-NAV-02). Note: despite the title
  "now", rows of later and finished games are included; keep or narrow is an owner question (§10).
- SPORT-FR-54 States (D§7): loading with no events → `today_loading` "Loading today’s games…" +
  `today_connecting` "Connecting to the sports service"; error with no events → the error
  message + `today_error_help` "Check the network connection and API-Sports settings." + **Try
  again** (`action_retry`, refresh); filtered list empty → `empty_sport` "No selected %1$s games
  were found for today" (sport tab, with the tab label), `empty_watchable` "No game has a channel
  match yet", `empty_favourites` "No favourites have been added yet", `empty_sports` "No selected
  games were found for today" (All). Errors while events are shown (PARTIAL_DATA,
  MATCH_DECISION_SAVE, a failed refresh) appear only in the Settings status line today; the
  rebuild also shows them on the screen as a one-line notice under the tabs.
- SPORT-FR-55 Keys on Today: D-pad moves between header actions, tabs and cards (each section row
  scrolls horizontally, the page scrolls vertically); OK on a tab selects it; OK on a card opens
  the hub; OK on a channel row plays it; Back leaves (hub closed). No Menu, number, channel or
  media key handling.
- SPORT-FR-60 Focus: when the first-focus game (first live, else first upcoming, else first
  finished) changes, focus moves to its card; when there is none, to the All tab. Current flaw:
  this also fires when a refresh changes which game is first, stealing focus mid-browse. Rebuild:
  move focus only on entry and after a tab is selected; on a data refresh keep focus on the same
  game id if it is still shown, else on the nearest card in the same row.

### 4.6 Match hub

- SPORT-FR-70 The hub is an overlay in the same window (not a dialog): while it is open, the
  list behind cannot take focus. It enters and leaves with fade + horizontal slide by a fifth of
  the width (D§8). The game shown is looked up by id in the current list, so scores update live
  while it is open; if a refresh drops the game, the hub closes.
- SPORT-FR-71 Opening the hub on a football game loads its match events unless already loaded
  or loading (SPORT-FR-90). Rebuild: when the loaded events are older than their 2-minute
  freshness, reopening loads again (the cache answers within the window).
- SPORT-FR-72 Header: competition logo 58 dp, competition upper-cased, status badge,
  `today_start_and_streams` "%1$s · %2$s" = start label · `today_available_streams` ("%1$d
  watchable stream(s)"); home team, score (or "–" before a score exists) with score detail, away
  team; on the right **Remind me** / **Reminder set** (SPORT-FR-95) and **Close**
  (`action_close`).
- SPORT-FR-73 Streams panel header: `streams_title` "STREAMS", `streams_subtitle` "Local channel
  matches", `streams_available_count` "%1$d watchable". Empty: `streams_empty` "No matching stream
  was found in the XMLTV guide or M3U channel names."
- SPORT-FR-74 Stream row content: status dot + label — "CONFIRMED BY YOU"
  (`match_confirmed_by_you`) when confirmed, else `match_available` / `match_possible` /
  `match_rejected` upper-cased; source label `match_source_epg` "TV GUIDE" or `match_source_m3u`
  "M3U NAME"; channel name (2 lines); detail: the programme title (guide) or `match_m3u_detail`
  "Match from the M3U channel name"; stream tag chips read from the channel name (SPORT-FR-118;
  resolution PRIMARY tone, dynamic range ACCENT, frame rate and language MUTED); offset line:
  guide — `offset_epg_exact` "Programme starts with the game", `offset_epg_before` "Programme
  starts %1$d min before the game", `offset_epg_after` "Programme starts %1$d min after the
  game"; name — without an explicit time `offset_m3u_teams` "Both teams appear in the channel
  name", else `offset_m3u_exact` "Channel name contains the game’s start time",
  `offset_m3u_before` "Time in the channel name is %1$d min before the game", `offset_m3u_after`
  "Time in the channel name is %1$d min after the game".
- SPORT-FR-75 Row buttons: **Watch** (`action_watch`) when Available; if undecided: **Confirm**
  (`action_confirm`) when Possible, and **Reject** (`action_reject`); if decided: **Restore**
  (`action_restore`). The row's lead control is Watch, else Confirm, else Reject, else Restore.
- SPORT-FR-76 Confirm stores CONFIRMED: the stream becomes Available and counts as watchable.
  Reject stores REJECTED: the stream becomes Rejected, stays listed, sorts after the others on the
  next presentation. Restore deletes the decision: the stream returns to its automatic
  confidence. The row updates as soon as the decision is saved, without re-scanning channels;
  a failed save sets MATCH_DECISION_SAVE ("Could not save the channel-match choice").
- SPORT-FR-77 After Confirm/Reject/Restore, once the new state arrives, focus goes to the same
  row's new lead control (retry for up to 90 frames), never to the list behind.
- SPORT-FR-78 Stream order while open: the order when the hub opened is kept; streams that
  disappear are dropped, new ones are appended at the end; reopening the hub applies the current
  order. Focusing a row scrolls only enough to show the whole row, never re-centres.
- SPORT-FR-79 Focus on open: the first stream row's lead control; if there is none, **Close**;
  retried for up to 90 frames while the hub animates in. Back closes the hub.

### 4.7 Match events (football only)

- SPORT-FR-90 Only football games have details (`detailsAvailable`). Other sports show
  `incidents_unavailable` "Event details are not available for this game." and no Refresh.
  Subtitle: `incidents_football_subtitle` "Goals, cards and substitutions" for football,
  `incidents_other_subtitle` "Match events" otherwise; title `incidents_title` "MATCH EVENTS".
- SPORT-FR-91 Loading: request SPORT-FR-151 through the cache (2 min). While loading with nothing
  shown: `incidents_loading` "Loading match events…". Loaded and empty: `incidents_empty` "No
  events have been reported for this game yet." Error with nothing shown: DETAILS_UNAVAILABLE
  "Match events are currently unavailable" + Try again. Error after an earlier success: the old
  list stays with `showing_cached_data` "%1$s · Showing cached data" in `accent`. Refresh
  (`action_refresh`) forces a reload request (the cache still answers inside 2 min). Match events
  are not polled while the hub is open.
- SPORT-FR-92 Incident parsing: for each item of `response` (index i): id
  `api-sports:football:<fixtureId>:incident:<i>`; minute `time.elapsed` (0 if absent) and extra
  `time.extra`; kind by `type` lower-cased: `goal` GOAL, `card` CARD, `subst` SUBSTITUTION, `var`
  VAR, else OTHER; `detail` (beta 23 falls back to the Finnish word "Tapahtuma"; the rebuild uses
  the localized `incident_other` "Event"); `comments`; `team.name`; `player.name` as actor;
  `assist.name` as related. Time label "`<elapsed>[+<extra>]′`" (extra only when > 0).
- SPORT-FR-93 Timeline band (`IncidentTimeline`): minute = elapsed + extra; span = max(90,
  latest minute); position = minute / span clamped to 0..1; half-time tick at 45 / span; markers
  sorted by minute; side HOME when the incident's team equals the home name (trimmed,
  case-insensitive), AWAY for the away name, else NEUTRAL (on the axis). Colours D§8.
- SPORT-FR-94 Incident list rows: time; main line — substitution "`actor → related`" (or
  "Substitution"), goal "`scorer · assist X`" (`incident_assist`) or the scorer (or "Goal"),
  others the actor or the detail; kind line "`<kind label> · <detail> · <team>`" with
  `incident_goal` "Goal", `incident_card` "Card", `incident_substitution` "Substitution",
  `incident_var` "VAR", `incident_other` "Event". The list takes focus as a whole; Up/Down scroll
  it by 120 dp while there is room and pass through at either end.

### 4.8 Reminders and favourites

- SPORT-FR-95 **Remind me** (`match_remind`) is shown only for a SCHEDULED game whose start is in
  the future; **Reminder set** (`match_reminder_set`, drawn selected) when a reminder with id
  `event:<eventId>` exists. OK toggles. The stored reminder: kind `event`, event id, title
  "`<home> – <away>`", subtitle the competition, the game start, and the channel id of the first
  Available, non-rejected stream in the current order (none if none). It fires one minute before
  kick-off; scheduling, the alert and permissions are in
  [specs/22](22-catchup-and-reminders.md). The first reminder may ask for notification permission
  and, once, explain the "open over other apps" permission.
- SPORT-FR-96 Favourites are a per-profile set of event ids (`favourite_event_ids`). The card
  draws a star, the Favourites tab filters by it. Beta 23 has **no control to add or remove a
  favourite**: the hub's "add to favourites" button was removed on 23 August 2026 (commit
  `2bbf155`) and never replaced, so only a restored backup or the demo data can populate it.
  Rebuild: owner decision (§10, open question 1). Event ids change daily, so a favourite marks a
  single game, not a team.

### 4.9 Home, Search and the score ticker

- SPORT-FR-97 Home "Today's sport" row ([specs/02](02-home.md) draws it): the first 6 events of
  the loaded, sorted list (any status); title `home_sports_today` "Today’s sport", hint
  `home_sports_count` "%1$d match(es)" with the total count; hidden when there are no events.
  Card (244 × 160 dp): live dot + status label or start label, the two teams with 38 dp logos (or
  initials), the score or "–", the competition. OK opens Sohva Sport with the hub on that game.
  Focusing a card sets the Home hero to that game: kicker `home_hero_live` "Live now" when live,
  else "Today’s sport"; title "`home – away`"; metadata competition, then the status label or the
  start label, then the score; backdrop the two crests at 150 dp with a 56 dp gap at alpha 0.55
  over a 60 % `background` wash. Home starts no sport request of its own.
- SPORT-FR-98 Search ([specs/03](03-search.md)): results of type SPORT are loaded events whose
  home, away or competition contains the term (case-insensitive); title "`home – away`",
  subtitle "`competition · start label`", image the competition logo, key `sport:<eventId>`. OK
  opens Sohva Sport (the list, not the hub).
- SPORT-FR-99 Score ticker data (drawing in [specs/30](30-player.md)): LIVE games sorted by start,
  then SCHEDULED games starting within the next 3 hours sorted by start, first 4. Row: "`home –
  away`" and, for live games, the score (or the status label) in `danger` plus the status label
  when a score exists; for upcoming games the start label. Empty: `player_score_ticker_empty` "No
  followed matches on or starting soon." Toggled by the player quick-menu item
  `player_quick_ticker` "Score ticker" (value "Shown"/"Hidden") or the remote action
  `SCORE_TICKER` ("Score ticker on/off"); state is kept for the app session only. Turning it on
  with no events loaded triggers a refresh; while it is shown during playback it keeps the feed
  polling (SPORT-FR-27). The list is recomputed when the feed changes; rebuild: also when a
  minute passes, so the 3-hour window moves.

### 4.10 Stream pairing

Inputs, candidates and triggers

- SPORT-FR-100 Pairing runs for a list of events: (a) after every successful refresh, for all
  loaded events; (b) 250 ms after the pairing inputs settle (see generation), for the loaded
  events, cancelling a run in progress for an older generation; (c) the cached-only read in
  SPORT-FR-22 uses only the result cache. Runs are serialised (one at a time). A run is published
  only if the loaded events (ids and starts) did not change meanwhile; a failure keeps the last
  published results.
- SPORT-FR-101 The **generation** is the ordered list of: per source `id, enabled, EPG offset`;
  per source and kind (playlist, epg) the active snapshot id; per channel preference `channel id,
  custom name, hidden, manual XMLTV id, custom group key`; per LIVE organization rule `source,
  group key, item key, enabled`; per team alias `sport, canonical, alias`. Its SHA-256 fingerprint
  keys the result cache. Any change to those tables re-triggers (b).
- SPORT-FR-102 Events with `startEpochMillis <= 0` are never matched (empty list). Events already
  in the result cache for the current generation are not re-matched.
- SPORT-FR-103 Channel candidates: every live channel that is visible — its source enabled, in
  the active playlist snapshot, and passing the LIVE visibility rules and hidden flag
  ([specs/42](42-library-organization.md)) — contributes one **name candidate** whose text is the
  channel's display name (custom name if set, else the provider name), source M3U_CHANNEL_NAME,
  programme id `m3u-name:<channelId>`.
- SPORT-FR-104 Programme candidates: for the same channels, every programme of the active EPG
  snapshot on the channel's XMLTV id (the manual mapping if set, else the tvg-id) whose start,
  shifted by the source's EPG offset, lies in `[earliest kick-off − 120 min, latest kick-off +
  120 min]` of the events being matched. Text = title + subtitle + description; start/stop
  shifted by the offset. Several channels sharing one XMLTV id each get the programme.
- SPORT-FR-105 Normalisation (`MatchTextNormalizer`): Unicode NFKD, remove combining marks
  (`\p{M}+`), lower-case (root locale), every run of characters outside `[a-z0-9]` becomes one
  space, trim, collapse spaces. "Bayern München" → "bayern munchen"; "Paris Saint-Germain" →
  "paris saint germain".
- SPORT-FR-106 Team variants: the normalised team name plus its aliases (SPORT-FR-107), keeping
  only variants of at least 3 characters. A team is **mentioned** when a variant occurs as whole
  words in the candidate text: the check is on `" " + normalised text + " "` containing
  `" " + variant + " "`.
- SPORT-FR-107 Aliases: built-in (football only) — Manchester United: Man Utd, Man United,
  Manchester Utd; Manchester City: Man City; Tottenham Hotspur: Tottenham, Spurs; Paris
  Saint-Germain: PSG, Paris SG; Inter: Inter Milan, Internazionale; Bayern München: Bayern
  Munich, Bayern — plus rows of the `team_aliases` table for the sport. An alias applies only when
  the provider's team name normalises exactly to the canonical key. Aliases of all sports being
  matched are merged into one map. No screen adds aliases in beta 23 (the repository method is
  unused); keep the table and the method.

Channel-name clock and date (`ChannelNameSchedule`, name candidates only)

- SPORT-FR-108 Time: the first match of
  ```
  (?<![\d.])([01]?\d|2[0-3])[:.]([0-5]\d)(?![\d.])\s*(?:([AP])\.?\s*M\.?)?\s*((?:UTC|GMT)(?:[+-]\d{1,2}(?::?\d{2})?)?|CEST|CET|EEST|EET)?\b
  ```
  (case-insensitive). With AM/PM the hour must be 1–12 (else no time): 12 AM → 0, 12 PM → 12,
  other PM hours + 12. Examples: `18:45`, `21.00`, `2:30 PM`, `9.15 p.m.`, `20:00 CET`,
  `19:00 UTC+2`.
- SPORT-FR-109 Zone: CET = UTC+1; CEST and EET = UTC+2; EEST = UTC+3; UTC, GMT = UTC;
  `UTC±h`, `UTC±hh`, `UTC±hhmm`, `UTC±hh:mm` (same for GMT) = that offset when it is a valid
  offset (`UTC+5:30` is not and gives no zone). Fixed offsets: CET in summer is still +1. No zone
  is ever inferred from the viewer's zone, language, country tag or broadcaster.
- SPORT-FR-110 Date, first form found in this order: ISO `yyyy-MM-dd` (year 19xx/20xx);
  month-first `Sep 18`, `September 18th, 2026` (English month names or 3-letter abbreviations,
  `Sept`, optional dot, optional ordinal, optional year); day-first `18 Sep 2026`; numeric
  `a/b` or `a/b/yyyy` not touching a letter, digit or slash. Numeric order: if `a` is 1–12 and `b`
  > 12 → month a, day b (US `9/18`); otherwise day a, month b (`18/9`, `18/09`), marked
  **ambiguous** when both are 1–12 and differ (`9/10`); equal values (`9/9`) are not ambiguous.
  Regexes:
  ```
  MONTH = (Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)
  month-first  \bMONTH\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+((?:19|20)\d{2}))?\b      (ignore case)
  day-first    \b(\d{1,2})(?:st|nd|rd|th)?\s+MONTH\.?(?:,?\s+((?:19|20)\d{2}))?\b      (ignore case)
  iso          (?<!\d)((?:19|20)\d{2})-(\d{2})-(\d{2})(?!\d)
  numeric      (?<![\w/])(\d{1,2})/(\d{1,2})(?:/((?:19|20)\d{2}))?(?![\w/])
  ```
- SPORT-FR-111 Start offset of a name candidate: only when both a time and a zone were read.
  Candidate days: the stated date (in the stated year, else the event's year −1, 0, +1; none when
  ambiguous or invalid), else the event's date in that zone −1, 0, +1. Offset (minutes) =
  (that day at the stated time in the stated zone − kick-off), the smallest absolute value wins.
  A zoned time with an ambiguous date gives no offset.
- SPORT-FR-112 Date evidence: no date → supports; a zoned time with an offset → supports (the
  instant was compared); otherwise the date must equal the kick-off date in UTC and not be
  ambiguous.

Scoring and confidence (`EventChannelMatcher`)

- SPORT-FR-113 For each candidate and each event: explicit start = guide programme, or a name
  candidate with an offset. Offset = programme start − kick-off (guide), the name offset, or 0.
  Rejections: guide programme with |offset| > 120; no team mentioned (unless fallback); name
  candidate with an explicit time, |offset| > 120 and not both teams (unless fallback); name
  candidate without an explicit time and not both teams (unless fallback).
- SPORT-FR-114 Score = team score (both 70, one 38, none 0) + time score (no explicit start 10;
  |offset| ≤ 15 → 30; ≤ 30 → 25; ≤ 60 → 15; ≤ 120 → 5; else 0).
- SPORT-FR-115 Automatic confidence AVAILABLE when both teams are mentioned **and** the date
  evidence supports (guide programmes always do) **and** (no explicit start or |offset| ≤ 30);
  otherwise POSSIBLE. A single team is never Available. Examples: `[danzDE] (18/9) 18:45
  Football: Monza - Sassuolo` for a 21:45 Helsinki kick-off on 18 September → both teams, no
  zone, date matches → Available (score 80, "Both teams appear in the channel name");
  `Serie A: Monza vs Sassuolo En Espãnol @ Sep 18 2:30 PM :Nbc Sports 05` → Available; the same
  with `9/10` → Possible.
- SPORT-FR-116 Per event and channel only the best candidate is kept: higher score, then smaller
  |offset|, then guide before name, then earlier programme start, then programme id (so page
  order never changes the result). A channel with a saved decision but no automatic candidate is
  still listed through a **fallback** candidate scored as if a team were not required (guide
  window still applies); an automatic candidate replaces a fallback. The decision is then
  applied: CONFIRMED → AVAILABLE, REJECTED → REJECTED, none → automatic confidence.
- SPORT-FR-117 Result order per event: AVAILABLE, POSSIBLE, REJECTED; score descending;
  |offset|; channel name; channel id. Presentation (`EventChannelOrdering`) re-sorts with the
  priority codes: confidence first, then the rank of the channel name's first language tag in
  the priority list (untagged or unlisted last), then score descending, |offset|, channel name.
  Session decisions made since the last scan override stored ones in the presentation.
- SPORT-FR-118 Stream tags (`ChannelStreamTags`), read from the upper-cased channel name split on
  space `| : - _ / ( ) [ ] , .`, whole tokens only, first of each kind, in the fixed order
  resolution, dynamic range, frame rate, language. Resolution: 4K, UHD, 2160P, 4KUHD → 4K; FHD,
  FULLHD, 1080P, 1080 → FHD; HD, 720P, 720 → HD; SD, 576P, 480P → SD. Dynamic range: HDR, HDR10,
  HDR10+, HLG; DV, DOLBYVISION → DOLBY VISION. Frame rate: `(\d{2,3})(?:FPS|HZ|P)` → "N FPS".
  Language/country (also the priority vocabulary): FI (FIN, SUOMI), SE (SV, SWE), NO (NOR), DK
  (DAN), EN (ENG), UK (GB, GBR), US (USA), DE (GER, DEU), EE (EST), RU (RUS), FR (FRA), ES (ESP,
  SPA), IT (ITA), NL (NLD), PL (POL), AR (ARG), AL (ALB, SQ), PT (POR), BR (BRA), TR (TUR), GR
  (GRE), RO (RON), CZ (CZE), HR (HRV), RS (SRB), CA (CAN), AU (AUS). Ordering by priority cannot
  know the real commentary language.

Result cache (`EventChannelMatchCache`)

- SPORT-FR-119 A derived-data file `sports-channel-matches.bin` in the app cache directory holds,
  per event id, an event fingerprint (SHA-256 of id, sport, home, away, kick-off instant, kick-off
  minute of day — so a zone change invalidates), the save time and the automatic results
  (decisions are not stored; they are applied on read). A read returns entries only for the
  current generation, with a matching fingerprint, saved 0–24 h ago. A write after a completed
  scan drops entries of another generation, drops expired ones, adds the new ones, then evicts
  oldest-inserted until at most 512 events and 40,000 results remain; files over 16 MiB are
  neither written nor read. Any unreadable, old-version or corrupt file is a cache miss, never a
  lost decision.
- SPORT-FR-120 A scan whose generation changed while it ran is discarded (not published, not
  cached); the change itself schedules a new run.

## 5. Screen anatomy

- **Today screen**: D§1–§7 (structure, header, tabs, sections, 238 × 224 dp card, sports-channel
  row 272 × 68 dp, states). Screenshot:
  `design/screenshots/older-builds/2026-09-02-demo/07-sportmate.png` (older build).
- **Match hub**: D§8 (panel 92 % × 88 %, header columns 205 dp / centre / 150 dp, events panel
  weight 1.12, streams panel 0.88, timeline band 62 dp, marker 38 dp, incident rows, stream rows,
  chips). Screenshot: `design/screenshots/older-builds/2026-09-02-demo/08-match-info.png`.
- **Ordering and polling summary**: D§9. **Flaws to fix**: D§10.
- **Settings › Sohva Sport**: S§6 "Sohva Sport" (two groups, key field, follow menu, 245 dp
  competition list, search field width 0.62); focus rules S§8 (Save order first; closing the
  follow menu must return focus to its opener).
- **Home sport card and hero**: SPORT-FR-97 sizes; screenshot
  `design/screenshots/older-builds/2026-09-02-demo/02-home-todays-sport.png`; layout in
  [design/screens/home-and-shell.md](../design/screens/home-and-shell.md).
- **Score ticker**: panel drawn by [specs/30](30-player.md): 300–420 dp wide, `panel α0.92`,
  shape medium, padding 14/10, rows 6 dp apart, text 14 sp (status 12 sp), top-right 40 dp from
  the edge and 24 dp from the top (72 dp when playback info is up).
- Sport accents per sport and incident colours: D§5, D§8; tokens in
  [design/01-design-system.md](../design/01-design-system.md) (`palette.sports.*`).

## 6. Data

| Store | Content | Lifetime | Per profile | In backup |
|---|---|---|---|---|
| Encrypted secret store | API-Sports key (Keystore-encrypted, ≤ 512 chars) | until removed | no | **no** |
| Preference `followed_sports` (string set of enum names) | followed sports; absent = defaults | permanent | no | yes (`followedSports`) |
| Preference `followed_competitions` (string set of `SPORT:id`) | followed competitions; absent = defaults | permanent | no | yes (`followedCompetitionKeys`) |
| Preference `sports_channel_priority` (comma-joined codes) | ≤ 8 canonical codes | permanent | no | yes (`sportsChannelPriority`) |
| Preference `favourite_event_ids` (profile-scoped key) | favourite event ids | never pruned | yes | yes (per profile) |
| Preference `time_zone` | app zone (shared with the guide, [specs/74](74-localization.md)) | permanent | no | yes |
| Table `sports_api_cache` (key `cacheKey`; index `staleUntilEpochMillis`) | `sport`, `kind` (events / competitions / incidents), raw `payload`, `source`, `quotaRemaining`, `fetchedAt`, `expiresAt`, `staleUntil` | SPORT-FR-32; deleted after the stale window | no | no |
| Table `team_aliases` (key sport + normalised canonical + normalised alias; index sport) | extra aliases | permanent | no | no |
| Table `event_channel_decisions` (key event id + channel id; index event id) | `decision` ("confirmed" / "rejected"), `updatedAt` | permanent; deleted with the channel's source | no | no |
| Table `reminders` | event reminders `event:<id>` ([specs/22](22-catchup-and-reminders.md)) | until fired or removed | no | see specs/22 |
| File `cache/sports-channel-matches.bin` | pairing results, format version 3 | 24 h per entry | no | no |
| Memory | today's events, pairing results for them, loaded match events, ticker visibility (session) | process | – | – |

Result-cache file format (big-endian `DataOutputStream`, strings as int length + UTF-8 bytes,
each ≤ 64,000 bytes): `int version=3`, `generation`, `int entryCount (0..512)`, then per entry:
`eventId`, `eventKey`, `long savedAt`, `int count`, then per result: `eventId`, `channelId`,
`channelName`, `programmeId`, `programmeTitle`, `long programmeStart`, `long startOffsetMinutes`,
`automaticConfidence` name, `int score`, `source` name, `boolean hasExplicitStartTime`; the file
must end there. Written to `<name>.tmp` then renamed. Bump the version whenever matching rules
change so old results are recomputed (versions 2 and 3 did this in betas 19–20).

Event ids, competition keys and decision rows are the only persistent identities; everything
else is derived and may be dropped at any time. Favourites, decisions and reminders reference
event ids; an AFL id changes when the provider moves the kick-off (§8).

## 7. External interfaces

API-Sports, one host per sport. All requests `GET`, HTTPS only (an endpoint configuration that is
not an `https` root address fails with `error_endpoint_must_be_https`), redirects not followed
(a 3xx is an HTTP error). Headers: `x-apisports-key: <key>`, `Accept: application/json`,
`User-Agent: SohvaTV/0.1 (Android TV; personal use)`. Timeouts: connect 20 s, read 90 s (the app's
shared client). Response body read up to 4 MiB (4,194,304 bytes); larger fails. Read header:
`x-ratelimit-requests-remaining`. No request carries IPTV data.

| Sport | Host | Day listing | Competitions | Details |
|---|---|---|---|---|
| Football | `https://v3.football.api-sports.io` | `/fixtures?date=<yyyy-MM-dd>&timezone=<zone>` | `/leagues?current=true` | `/fixtures/events?fixture=<id>` |
| Ice hockey | `https://v1.hockey.api-sports.io` | `/games?date=…&timezone=…` | `/leagues` | – |
| AFL | `https://v1.afl.api-sports.io` | `/games?date=…&timezone=…` | `/leagues` | – |
| Basketball | `https://v1.basketball.api-sports.io` | `/games?date=…&timezone=…` | `/leagues` | – |
| Baseball | `https://v1.baseball.api-sports.io` | `/games?date=…&timezone=…` | `/leagues` | – |
| Handball | `https://v1.handball.api-sports.io` | `/games?date=…&timezone=…` | `/leagues` | – |
| Rugby | `https://v1.rugby.api-sports.io` | `/games?date=…&timezone=…` | `/leagues` | – |
| Volleyball | `https://v1.volleyball.api-sports.io` | `/games?date=…&timezone=…` | `/leagues` | – |
| American football | `https://v1.american-football.api-sports.io` | `/games?date=…&timezone=…` | `/leagues` | – |
| MMA | `https://v1.mma.api-sports.io` | `/fights?date=…&timezone=…` | none | – |
| Formula 1 | `https://v1.formula-1.api-sports.io` | `/races?date=…&timezone=…` | none | – |
| NBA | `https://v2.nba.api-sports.io` | `/games?date=…&timezone=…` | none | – |

`<zone>` is the app zone's IANA id (e.g. `Europe/Helsinki`). The NFL, MMA, Formula 1 and NBA
shapes were written without access to the provider documentation (behind a bot check, beta 15
receipt) and parse tolerantly; verify them against real payloads (§10).

Error mapping (messages from `core` strings):

| Condition | Message key | English |
|---|---|---|
| No key saved (checked before any network call) | `error_api_sports_key_missing` | Add an API-Sports key in settings |
| HTTP status not 2xx (401, 403, 429, 5xx, 3xx…) | `error_api_sports_http` | API-Sports responded with HTTP %1$d |
| Body over 4 MiB | `error_api_sports_response_too_large` | The API-Sports response is too large |
| Network / TLS / timeout (`IOException`) | `error_api_sports_unavailable` | API-Sports is not available right now |
| `errors` non-empty in a 200 response (the provider is understood to report a bad key, an exhausted daily quota or a bad parameter this way; not verified in this code base, see open question 5) | `error_api_sports_service_error` | API-Sports returned a service error |
| Unparseable JSON or other failure | `error_api_sports_invalid_data` | API-Sports returned invalid data |
| Football details for a non-football id | `error_football_event_id_invalid` | The football match identifier is invalid |
| Sport not offered by a data source (interface default) | `error_sport_unsupported_source` | The selected sport is not supported by this data source |

Screen-level errors (`sportmate` strings): SPORTS_UNAVAILABLE `error_sports_unavailable` "Sports
data is currently unavailable"; PARTIAL_DATA `error_sports_partial` "Some sports data is
currently unavailable"; DETAILS_UNAVAILABLE `error_details_unavailable`; MATCH_DECISION_SAVE
`error_match_decision`. Beta 23 discards the specific cause on Today (a missing key reads as
"Sports data is currently unavailable"). Rebuild: keep the screen-level title and show the cause
line under it, and name the three cases distinctly — no key (with a button to Settings), quota
exhausted (from the `errors` object or remaining = 0; "resets daily"), provider unavailable —
without exposing the key.

Legal texts shown in About ([specs/72](72-updates-about-diagnostics.md)): `about_api_sports_notice`,
`about_open_api_sports_terms`, `sports_disclosure` (key encrypted with Android Keystore, sent only
in an HTTPS header; cached data up to 24 hours old during an outage).

## 8. Edge cases and limits

- **Midnight.** The list holds only the date that was current at the last refresh. A game that
  started at 23:00 and is still live vanishes at the first refresh after midnight, and the
  ticker's 3-hour window never reaches past midnight (open question 4).
- **Zone change.** A new zone is a new cache key per sport (one request each), new start labels
  and new pairing fingerprints (rematch).
- **Clock change.** Freshness uses wall-clock millis. A clock set backwards can make a saved
  entry look fresh for a long time. Rebuild: treat an entry whose fetch time is in the future as
  expired. The result cache already rejects negative ages.
- **Unsupported zone id.** API-Sports accepts only the zones it lists; an id it does not know is
  expected to come back as an `errors` object (service error). Not verified.
- **Event id stability.** AFL ids embed the kick-off second and team ids: a rescheduled AFL game
  gets a new id, orphaning its decisions, reminder and favourite. Other sports use provider ids.
- **Provider quirks.** AFL duplicates (SPORT-FR-42); NBA summer leagues skipped; NFL keeps id,
  status and date inside `game`; basketball scores are objects; hockey and all non-football sports
  carry no elapsed minute (the live badge shows no minute); Formula 1 has no score but laps.
- **Large line-ups.** 56,000 channels and 165,000 programmes: every visible channel name is a
  candidate, so a full scan walks the whole live line-up once (§9).
- **Channel names.** A dotted day.month without a trailing dot (`18.09 20.45`) is read as the
  clock 18:09 (the first time-shaped token wins); `UTC+5:30` gives no zone; ambiguous numeric
  dates never promote; a lone team plus an unzoned clock never creates a candidate.
- **Imports during a scan.** Discarded and redone 250 ms after the inputs settle; the last
  published results stay on screen meanwhile.
- **Restricted profiles.** Pairing uses the global LIVE visibility rules, not the profile's
  allowed groups; playing a stream outside the profile's groups is refused by the player entry
  ([specs/04](04-profiles-parental.md)). Rebuild: filter streams by the active profile's allowed
  live groups so a restricted profile is not offered channels it cannot play.
- **Locked channels.** Watch goes through the normal channel PIN gate.
- **No key / empty follows.** No key: every feed fails before the network, cached data (if any)
  still shows. No followed feed: an empty list, "No selected games were found for today".
- **Process death.** The filter and the open hub are restored from saved state; the view model
  reloads from the cache first.
- **Big payloads.** A day of worldwide football fixtures or the football league catalogue is the
  largest response; the 4 MiB cap bounds it.

## 9. Lightweight by design

**Budgets on the low-end box** (starting values; [plan/07](../plan/07-performance.md) refines):
feed parse and publish ≤ 300 ms per sport off the main thread; heap added by Sohva Sport in steady
state ≤ 4 MB (events + results + match events for today); pairing scan peak ≤ 16 MB and never
more than one scan at a time; no frame of the Today screen or the player waits on sport work.

Feed and parsing
- Rule: parse with a streaming JSON reader that keeps only the fields in SPORT-FR-40 and drops
  items of unfollowed competitions while reading; never build a whole-document tree. Beta 23
  parses the full tree, and on every refresh parses a fresh cached payload twice (phase 1 and
  phase 2 both read it).
- Rule: store the **normalised events of all competitions** for the day (a compact row per event,
  a few hundred bytes) instead of the raw payload (up to 4 MiB of text per sport in beta 23), so a
  cache hit is a small indexed read and toggling a competition still costs no request. Keep the
  raw text only for the competition catalogue, or store it normalised too.
- Rule: at most 3 feeds in flight; all network and parsing on a background dispatcher; the main
  thread only receives immutable lists.
- Rule: sort and group (sections, counts per tab, sports channels) once per list change in the
  view model, never during composition; the tab counts and section lists are precomputed values.

Pairing (the place that ran out of memory)
- Preview 48 (19 September 2026) died with an `OutOfMemoryError` in
  `GuideDao_Impl.channelNameCandidates`: pairing read every channel name and every programme
  (with descriptions) into lists and held them together before matching; heap 192 MiB reached,
  52 CursorWindow-full warnings. On a synthetic 56,164-channel, 112,328-programme guide with
  2,150-character descriptions the old code failed after 155 s; the paged fix completed in 54 s
  with a sampled Java heap peak of 12.03 MiB, zero CursorWindow warnings, 220 channel and 1,975
  programme queries, and the main thread kept ticking (ledger, preview 49).
- Rule: **keyset pages in primary-key order.** Channel page: visible channels with `rowid >
  :after` ordered by `rowid`, **256** per page (join order pinned with `CROSS JOIN`, the LIVE
  visibility predicate inlined after the source/snapshot filter). Programme pages: for that
  channel batch only, programmes in the time window via the
  `(sourceId, xmltvChannelId, start, stop)` index, ordered by `(programme rowid, channel rowid)`,
  cursor on both ids (several channels can share one XMLTV id), **64** per page; the EPG offset is
  applied to the bound, not to the indexed column. Row ids are cursors for one scan only, never
  stored.
- Rule: **stream candidates into an accumulator** that keeps only the best result per event and
  channel; a candidate's text and description are released as soon as it is scored. Bound: at
  most (events × matched channels) small result objects.
- Rule: **one token index per scan.** Build a map from each variant's first word to its variants
  once per scan; tokenise each candidate text once and look up only its words, so the cost per
  candidate does not grow with the number of events (beta 23 does events × variants substring
  searches per candidate, which is why 56k channels take tens of seconds).
- Rule: **cancellation and priority.** Check for cancellation between candidates and yield
  between pages; run on one background thread at `THREAD_PRIORITY_BACKGROUND`; a newer
  generation cancels the running scan; a scan whose generation changed is thrown away; runs are
  serialised by one lock; the 250 ms settle delay batches the several table writes of one import.
- Rule: **bounded result cache** (SPORT-FR-119: 512 events, 40,000 results, 16 MiB, 24 h), so a
  warm start costs one small file read and no channel scan.
- Rule (new): **pair only when someone needs it.** Beta 23 pairs after every feed load, including
  the load at every cold start and after every import, even if Sohva Sport is never opened.
  Only Today (cards, Watchable, sports channels, the hub) and the reminder's channel use the
  results; Home, Search and the ticker do not. Start a scan when Today is shown (the cached
  results are applied at once), and never while the start-up budget is running or while video
  plays full screen; a scan interrupted by playback resumes from scratch later.
- Performance check: the paged scan on the owner-scale synthetic guide on the low-end emulator
  stand-in finishes, keeps the heap peak under 16 MB, and a D-pad press held on the Today screen
  during the scan keeps rendering within the frame budget.

Images
- Crests and competition logos are small transparent PNGs from the provider (HTTPS only). Decode
  at the drawn size, never the source size: card crest 48 dp circle with 4 dp padding (80 px at
  1080p), card competition logo 18 dp (36 px), hub crest 48 dp (96 px), hub competition logo 58 dp
  (116 px), Home card logo 38 dp (76 px), Home hero backdrop crest 150 dp (300 px). ARGB_8888 is
  required for transparency; at these sizes a crest is 25–40 KB decoded, so a full Today screen of
  visible cards stays under about 3 MB. They share the app image loader (disk cache, 8 %
  memory cache, at most 2 decodes at a time; [specs/41](41-metadata-enrichment.md)). Initials are
  always drawn underneath, so a missing crest costs nothing. Rule: no crossfade for crests on the
  low-end class.
- Only visible cards compose (each section is a lazy row); the page itself is a plain column of
  at most four rows.

Drawing
- Focus: one card at a time draws its 16 dp shadow and the 1.03 scale inside a layer after the
  clip (no layout shift). On the low-end class draw the shadow from a pre-rendered image instead
  of a live elevation shadow.
- The live-badge pulse is finite (960 ms) and restarts only when the minute changes; the clock
  recomposes only the header once a minute. No other animation runs while idle.
- Hub: beta 23 draws a 94 % scrim over the still-composed list, a radial-gradient panel and a
  horizontal gradient per stream row. Rule: when the hub is fully shown, stop drawing the list
  behind (an opaque scrim of the same colour is visually indistinguishable at 94 %), use a flat
  or pre-rendered panel fill and flat row fills with a coloured left edge for confidence (D§10).
- Timeline band: at most one marker per incident (a football match has tens), laid out once per
  incident list, not per frame.

Network and quota budget
- Free API-Sports plans allow **100 requests per day per sport API** (project handoff); each sport
  is a separate API product with its own counter, so budgets are per sport.
- Beta 23 cost per followed sport: 1 request per 20 minutes at most while Sohva Sport or the
  ticker is visible (the 5- and 10-minute polls mostly hit the cache) — at most 72 per day if
  watched continuously; +1 per cold start or zone change when the cache is older than 20 minutes;
  competitions 1 per 7 days, only when the follow menu is opened; football match events 1 when a
  game's hub is first opened in a session, plus 1 per Refresh press more than 2 minutes after
  the last fetch. Nothing is requested while the app is in the background.
- Rule: keep the per-sport daily plan under 100 including cold starts: record requests per sport
  per UTC day, show "API quota" from the header, and when the provider reports the quota
  exhausted or `remaining` reaches 0, stop automatic requests for that sport until the next UTC
  day, keep showing the cached list, and say so (new; owner to confirm, open question 2).
- Polling runs only while visible and resumed (SPORT-FR-27) and one catch-up refresh on return;
  never a background job, never a wake-up.
- Start-up: Sohva Sport constructs nothing until first use except the feed load after Home's
  first read; that load is cache-first and must not run on the main thread or delay Home.

## 10. Lessons from the current app

- **Paged matching after a real OOM** (preview 48 → 49, ledger "Memory fix after preview 48";
  [plan/08](../plan/08-lessons-learned.md) 1.4). Whole-list reads of channel names and programmes
  exhausted the 192 MiB heap on the Shield; the fix is §9's paged accumulator. The opt-in stress
  test `SportsMatchingMemoryTest` is the model for the rebuild's check.
- **Provider clocks are not local clocks** (betas 19–20; ledger "Dated provider channel names" and
  "Provider clocks and numeric dates"; plan/08 5.3). A channel-name time was first compared with
  the viewer's zone, so `2:30 PM` was read as 02:30 and `(18/9) 18:45` against a 21:45 Finnish
  kick-off stayed Possible. Rules kept: explicit zones only, AM/PM, English month names, ISO and
  unambiguous numeric dates; no zone from language, country tags or the viewer; both teams outrank
  an unzoned clock; ambiguous `9/10` stays Possible. The result-cache version was bumped each time
  so upgraded installs recompute.
- **Confirm/Reject used to rescan the whole guide** before updating the row (beta 19). Save the
  decision and update in place; keep the automatic result so Restore works; show "Confirmed by
  you" so a manual Available is distinguishable (ledger, first section).
- **Hub focus** (beta 19, MatchHubTest): a dialog window gave a second focus root; the hub is in
  the same window with the list behind unfocusable; focus must always land somewhere (first
  stream's lead control, else Close); after a decision focus stays in the row; the stream list
  must not re-centre (TV pivot) and must keep its order while open.
- **Quota drained while nobody looked** (commit `24d1344`, "stop spending sports quota when nobody
  is looking"): polling now requires the screen (or ticker) to be visible and the app resumed; a
  stale return costs one call.
- **Untestable infinite animations**: an endless live pulse and a `delay` loop clock kept the
  Compose test clock busy forever; the pulse is finite and the clock ticks off the frame clock
  (`TickerFlow`).
- **Sports channel rows were not focusable** at first (a painted row); they are buttons now
  (SportsChannelRowTest).
- **AFL score slot**: "14.11 (95) – 16.9 (105)" pushed team marks off the card; the total goes in
  the score slot, goals/behinds in the detail line (commit `fc947d4`).
- **Glyph icons per sport** from the TV's fonts rendered unpredictably; tabs use names, only
  Watchable and Favourites keep drawable icons.
- **MMA / Formula 1 / NBA feeds are never requested** (SPORT-FR-21) — found while writing this
  spec, not reported by a tester; fix in the rebuild.
- **Favourites cannot be added** (SPORT-FR-96).
- **Hard-coded Finnish** "Tapahtuma" in the incident parser; all words come from resources.
- **Unused strings not to carry over**: `today_headline`, `today_live_section`,
  `today_matches_section`, `today_upcoming_section`, `today_refresh`, `today_guide`,
  `today_settings`, `today_waiting_channel_data`, `empty_football`, `empty_hockey`, `empty_afl`,
  `today_finished_section`, `today_watch_on`.
- **Keep**: cache-first display with 24 h stale fallback; per-sport quota display; one request
  per sport per day-listing; decisions in the database, results in a disposable cache; the
  generation fingerprint; the rule that a single team is never Available.
- **Do differently**: show errors and their cause on the screen (SPORT-FR-54, §7); stop focus
  jumps on refresh (SPORT-FR-60); pair lazily (§9); streaming parse and normalised storage (§9);
  return focus to the opening card after the hub; fixed "HH.mm" and static "This evening".

Open questions (for the owner)
1. Favourites: restore an "Add to favourites" / "Remove from favourites" button in the hub (and
   perhaps a long-press on a card), or drop the Favourites tab and star? And should a favourite
   be a team (lasting) rather than one game?
2. Live freshness vs quota: live games are refreshed from the network at most every 20 minutes
   (the 5-minute poll hits the cache). Should live feeds use a 5-minute freshness (up to 288
   requests per sport per day if watched all day, over the free 100), and should the rebuild
   stop automatic requests when the daily quota is exhausted?
3. Sports channels now lists channels of later and finished games too. Keep, or only games live
   now (and starting within, say, 15 minutes)?
4. Midnight: keep games that started yesterday and are still live (fetch yesterday's feed after
   midnight until they finish), and let the ticker look past midnight?
5. The NFL, MMA, Formula 1 and NBA parsers were never checked against real payloads (and, with
   SPORT-FR-21, MMA/F1/NBA were never fetched). Can the owner capture one real day listing per
   sport with a key so the rebuild's parsers can be tested on them?
6. Should restricted profiles see only streams inside their allowed live groups (§8)?
7. Do the `/fights` (MMA) and `/races` (Formula 1) endpoints accept the `timezone` parameter the
   app sends? Unverified.
8. Should a user be able to add team aliases on the TV (the table and repository method exist but
   no screen)?

## 11. Acceptance tests

Unit (JVM)
- Parser per sport with recorded payloads: football fixture and incidents; hockey (non-HTTPS logo
  dropped); AFL duplicates collapse to the final record, score and goals/behinds detail; basketball
  nested totals; generic half/set/quarter codes are LIVE and `PST` is POSTPONED; NFL nested `game`;
  MMA fighters and card name; Formula 1 word statuses and laps; NBA visitors, numeric status,
  summer league skipped; competition catalogues nested and top-level shapes; caller-selected
  competitions filter; only football asks `current=true`; `errors` object fails with the service
  error (mirrors `DirectSportsRepositoryTest`).
- Cache: fresh hit makes no request; expired entry fetches; network failure returns stale inside
  24 h and raises after; cancellation is not a fallback; the response over 4 MiB fails; no key
  fails before any request; quota header captured.
- Feed combination: MMA/F1/NBA followed without competition keys are requested (regression for
  SPORT-FR-21); partial failure yields PARTIAL_DATA with events; total failure SPORTS_UNAVAILABLE;
  cache state and quota combination.
- Polling policy: 5 / 10 / 30 minutes; polls only when (screen or ticker) and foreground; stale
  return refreshes once; immediate return does not (mirrors `TodayPollingPolicyTest`).
- Sections and ordering: each status under the right heading, cancelled is finished, interrupted
  is not; first focus live → upcoming → finished → none; sports channels only Available, one row
  per channel, only events on screen, live first then earliest, at most 8 (mirrors
  `TodaySectionsTest`, `TodayEventOrderingTest`).
- Timeline: kick-off at 0 and whistle at 1; stoppage counts; long matches stretch the axis; sides;
  no team → neutral; markers ordered; empty match (mirrors `IncidentTimelineTest`).
- Matcher (mirror all 21 cases of `EventChannelMatcherTest`): both teams + start → Available incl.
  aliases; single team → Possible; manual decisions per channel; strongest programme per channel;
  distant/unrelated excluded; name with both teams Available without a local clock; names need
  both teams, unzoned clocks do not reduce confidence; country variants use the explicit zone;
  UTC offsets and summer time across midnight; the exact Monza–Sassuolo reports (`Sep 18 2:30 PM`
  and `(18/9) 18:45`) Available; old or wrong-opponent dates not promoted; AM/PM and 12 AM/PM;
  ambiguous `9/10` stays Possible; stale/invalid numeric dates; zoned numeric dates enforce the
  instant; Restore returns the original confidence; streamed pages preserve winners and fallback
  across page boundaries; guide beats name on equal evidence; words match whole.
- Result cache: restart restores results while decisions stay in the database; changed inputs,
  kick-off, zone, teams and age invalidate; empty results survive restart; corruption is a miss;
  old version recomputed; priority reorders only within a confidence (mirrors
  `EventChannelMatchCacheTest`).
- Stream tags and priority normalisation (mirrors `ChannelStreamTagsTest`).
- Ticker selection: live first, then within 3 hours, never more than 4 (mirrors `ScoreTickerTest`).

UI (instrumentation, TV emulator 1920 × 1080)
- Today: wordmark not clipped; first live card focused on entry; D-pad Right moves across tabs;
  selecting a sport tab filters and moves focus to its first card; a refresh that changes the
  first game does **not** move focus (rebuild behaviour); Back from Settings returns correctly.
- Hub (mirror `MatchHubTest`): Remind me reports the event and shows Reminder set; focus on the
  first stream; focus cannot escape to the list; Back closes; Watch reports the channel; incident
  list scrolls past what fits; nothing watchable still focuses something; confirming keeps focus
  inside the hub on the new Watch; no streams → Close; first stream row not clipped;
  Confirm/Reject/Restore below Available rows by remote without losing place; closing returns
  focus to the opening card (rebuild).
- Sports channel row focusable and OK plays it (mirror `SportsChannelRowTest`).
- Persistence (mirror `SportsMatchPersistenceTest`): restart reuses results without scanning and
  honours saved decisions and hidden channels; cached feed shows channel counts before the network
  completes; decisions do not rescan; programme pages keep shared XMLTV mappings, EPG offsets and
  visibility without duplicates.
- Polling (mirror `TodayPollingTest`): Lab loads nothing automatically but manual refresh works;
  returning after time away refreshes; returning at once costs nothing; no request while the app
  is in the background or the player is up without the ticker.

Manual device checks
- With a real key on the Shield: each followed sport shows today's games; quota appears in
  Settings; a live football game shows the minute and match events; a known channel pairs as
  Available; Confirm/Reject/Restore survive an app restart and a playlist re-import.
- Reminder set from the hub fires a minute before kick-off and opens the stream.

Performance (low-end class or its emulator stand-in, `.local/slowbox` harness)
- Paged pairing on the owner-scale synthetic guide (56,164 channels, 112,328 programmes with
  2,150-character descriptions): completes, Java heap peak < 16 MB, zero CursorWindow-full
  warnings, main-thread heartbeat never stalls, cancellation stops further queries within one
  page, a second run is a cache hit with no channel query (mirror `SportsMatchingMemoryTest`).
- Today screen with 60 games across 4 sections: holding Right on a card row renders every frame
  within the budget of [plan/07](../plan/07-performance.md), including while a feed refresh
  parses.
- Opening and closing the hub 10 times: no frame over two vsyncs after the enter animation, heap
  back to its baseline.

## 12. Reference: current code map

| File (beta 23) | Role |
|---|---|
| `sportmate/.../sports/repository/SportsRepository.kt` | Repository interface, snapshots, default competition ids, backend exception |
| `sportmate/.../sports/repository/DirectSportsRepository.kt` | API-Sports hosts, requests, cache/TTL/stale logic, all parsers and status maps |
| `sportmate/.../feature/today/TodayViewModel.kt` | Feed loading (cache then network), polling, match events, decisions, favourites, pairing publication |
| `sportmate/.../feature/today/TodayScreen.kt` | Today screen, filters, cards, sports-channel rows, match hub, incidents, stream rows, status summary |
| `sportmate/.../feature/today/TodaySections.kt` | Section split, first focus, sports-channel strip |
| `sportmate/.../feature/today/TodayEventOrdering.kt` | Event sort |
| `sportmate/.../feature/today/TodayPollingPolicy.kt` | Interval, poll-when-visible, refresh-on-return |
| `sportmate/.../feature/today/IncidentTimeline.kt` | Timeline positions and sides |
| `sportmate/.../matching/EventChannelMatcher.kt` | Normaliser, scoring, confidence, accumulator |
| `sportmate/.../matching/ChannelNameSchedule.kt` | Clock, zone and date parsing of channel names |
| `sportmate/.../matching/EventChannelMatchingRepository.kt` | Aliases, decisions, paged scan, generation check |
| `sportmate/.../matching/EventChannelMatchCache.kt` | Versioned result-cache file |
| `sportmate/.../matching/EventChannelOrdering.kt` | Priority-code ordering |
| `core/.../core/model/TodayEvent.kt` | SportType, hasCompetitions, status enum, event model |
| `core/.../core/model/FootballIncident.kt` | Incident model and time label |
| `core/.../core/model/SportsFollowSettings.kt` | Competition model, preference keys, follow defaults |
| `core/.../core/model/ChannelStreamTags.kt` | Stream tags and priority vocabulary |
| `core/.../core/database/SportsCacheDao.kt`, `GuideEntities.kt` | `sports_api_cache`, `team_aliases`, `event_channel_decisions` |
| `core/.../core/database/SportsMatchQueries.kt`, `GuideDao.kt` | Paged candidate SQL, generation query, alias/decision queries |
| `core/.../core/security/SecretSettingsStore.kt` | Encrypted API key |
| `core/.../app/AppPreferencesRepository.kt` | Followed sports/competitions, priority, favourites, zone |
| `core/.../feature/common/TickerFlow.kt` | Test-safe periodic ticker |
| `iptv/.../feature/player/ScoreTicker.kt` | Ticker selection and overlay |
| `iptv/.../feature/settings/SettingsScreen.kt` | Settings › Sohva Sport |
| `app/.../app/StreamMateApp.kt` | View-model scope, polling gate, reminders, open requests, ticker state |
| `app/.../app/StreamMateContainer.kt` | Repository wiring, result-cache file, HTTP client |
| `app/.../feature/home/HomeScreen.kt`, `feature/search/SearchScreen.kt` | Home sport row/hero, sport search results |
| `app/src/demo/.../StreamMateDemoContentProvider.kt` | Fictional demo sports repository |
