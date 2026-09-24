# Search

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

Search is one screen that finds channels, programmes, films, series, episodes and today's
sport matches across every enabled source from a single text field. The viewer types at least
two characters; after a 250 ms pause the app searches each kind in parallel and appends each
kind's results as soon as it is ready, so rows already on screen never move. Every result acts
with one OK: a channel or programme plays the channel live, a film or series opens its page, an
episode plays from where it was left, and a match opens Sohva Sport. A restricted profile finds
only what its allowed groups contain. Discover has its own search and is not part of this
screen ([Discover](50-discover-addons.md)).

Layout measurements: [Home and shell layouts](../design/screens/home-and-shell.md) §6.

## 2. Feature checklist

- SEARCH-01 Search destination on the Home rail (`home_search` "Search").
- SEARCH-02 Header: brand, title "Search", subtitle "Channels, programmes, movies, series,
  episodes and sports", and a Back button.
- SEARCH-03 One text field, focused on entry; typing through the TV's keyboard; at most 80
  characters.
- SEARCH-04 A search runs when the trimmed text has at least 2 characters, 250 ms after the
  last change; a new change abandons the previous search.
- SEARCH-05 Channel results: channels whose shown name contains the text.
- SEARCH-06 Programme results: programmes whose title or subtitle contains the text, on any
  imported date, with channel name and start time.
- SEARCH-07 Film results (up to 40), series results (up to 40), episode results (up to 40,
  matching the episode or the series name).
- SEARCH-08 Sport results: today's matches whose home team, away team or competition contains
  the text.
- SEARCH-09 Matching ignores letter case.
- SEARCH-10 Result kinds load in parallel and each is appended when it is ready; rows already on
  screen never move.
- SEARCH-11 Each result row: thumbnail (logo, poster or the kind's initial), title, subtitle,
  kind label (CHANNEL, PROGRAMME, MOVIE, SERIES, EPISODE, SPORT).
- SEARCH-12 Status line: hint, "Searching…", "N results", "No results found." or "Some search
  results could not be loaded."
- SEARCH-13 OK on a channel or programme result plays the channel live; Back from the player
  goes to the guide on that channel.
- SEARCH-14 OK on a film opens its details page; on a series opens its series page.
- SEARCH-15 OK on an episode plays it from its saved position; when it ends the next episode
  plays (autoplay on) or the series page opens.
- SEARCH-16 OK on a sport result opens Sohva Sport.
- SEARCH-17 Only enabled sources, their active snapshots and what the device's organisation
  rules show (hidden channels, groups and titles never appear).
- SEARCH-18 A restricted profile finds only channels, programmes, films and series in its
  allowed groups.
- SEARCH-19 Leaving Search forgets the text and the results.

## 3. Entry points and navigation

- Entry: the Home rail item Search (spec 01 SHELL-FR-62). No other screen opens Search.
- Focus on entry: the text field (today requested once 80 ms after entry; rebuild: the
  frame-retry focus request of spec 01 SHELL-FR-82).
- Back key or the Back button: pops to Home. While the on-screen keyboard is up, the first Back
  closes the keyboard (platform behaviour).
- Return to Search from a result's screen (Back from details, from the guide after a channel
  result the stack is `[Home, Guide]` so Search is gone): Search is rebuilt empty with the field
  focused (spec 01 §3.4).
- What results open, and Back from there:

  | Result | OK | Back from there |
  |---|---|---|
  | Channel, programme | Play the channel live with `forGuide = true` (group check with refusal toast, PIN gate for a locked channel, recorded as recent; spec 01 SHELL-FR-20) | The guide focused on that channel (`[Home, Guide]`) |
  | Film | `MovieDetails(film)` for the copy found | Search |
  | Series | `SeriesDetails(series)` | Search |
  | Episode | `VodPlayer(episode key, saved position)` | Search; at the end of playback see SHELL-FR-29 (next episode, or the series page pushed over Search) |
  | Sport | `Today` (the day's list, not the match card) | Search |

## 4. Behaviour

### 4.1 Input

- SEARCH-FR-01 The field accepts any text; each change is cut to its first **80** characters.
  Keyboard type text, IME action Done (hides the keyboard). Placeholder `search_input_hint`
  "Enter at least two characters"; the field's content description is the same text. Leading
  glyph "⌕". Test tag `unified-search-field`.
- SEARCH-FR-02 The search term is the text trimmed of leading and trailing white space. With
  fewer than **2** characters: results are cleared, nothing runs, the status line shows
  `search_scope_hint` "Search covers all downloaded sources."
- SEARCH-FR-03 On every change of the text (and, in beta 23, whenever today's sport list
  changes): the previous search is cancelled, the results are cleared, the status becomes
  "Searching…", and after **250 ms** without a further change the new search starts.

### 4.2 What is searched

- SEARCH-FR-10 Five groups are fetched in parallel, at most **3** at a time, started in the
  order Sport, Channels (channels and programmes together), Films, Series, Episodes. Each
  group's rows are appended to the result list as soon as that group finishes, after the
  groups already shown (so the order of groups is the order they finished; Sport, an in-memory
  filter, is usually first). Within a group the order is the group's own (below).
- SEARCH-FR-11 Channels and programmes (one query, one group): over channels of enabled
  sources in their active playlist snapshot and visible by the organisation rules, with the
  viewer's channel customisations applied (custom name, custom group, custom logo, manual EPG
  id):
  - channel rows: the shown name (custom, else provider) contains the term;
  - programme rows: a programme of that channel's active guide snapshot, matched through the
    manual EPG id or the channel's `tvg-id`, whose title or subtitle contains the term; every
    imported date, past and future; start and stop shifted by the source's EPG offset
    (minutes);
  - ordered channel rows first, then programme rows, each by title (binary text order);
    **at most 80** rows for the two together, then filtered by the profile restriction (4.4).
- SEARCH-FR-12 Films: visible films of enabled sources in the active catalogue snapshot whose
  provider name contains the term, ordered by name, **at most 40**, then restriction-filtered.
- SEARCH-FR-13 Series: the same over visible series, **at most 40**.
- SEARCH-FR-14 Episodes: episodes of visible series of enabled sources in the active snapshot
  whose episode name **or series name** contains the term, ordered by series name, season,
  episode, **at most 40** (so a series name also lists up to 40 of its episodes). Not
  restriction-filtered in beta 23 (gap, 4.4).
- SEARCH-FR-15 Sport: today's events as the Sohva Sport screen holds them (the followed sports
  and competitions, [Sohva Sport](60-sohva-sport.md)) whose home team, away team or competition
  contains the term, case-insensitively; all matches, no limit, in feed order. No network
  request is made for it.
- SEARCH-FR-16 Matching: substring ("contains"). Case-insensitive; in beta 23 the database
  groups fold only ASCII letters (SQLite `NOCASE`), so "ä" does not match "Ä", while the sport
  filter folds all letters. No accent folding, no word stemming, no ranking.
- SEARCH-FR-17 Duplicate copies are not merged: a film carried by two sources appears twice,
  and an episode and its series can both appear.

### 4.3 Result rows

- SEARCH-FR-20 Row content per kind:

  | Kind | Title | Subtitle | Thumbnail | Label |
  |---|---|---|---|---|
  | Channel | shown channel name | custom group, else provider group, else the source name | channel logo (custom, else provider) | `search_type_channel` "CHANNEL" |
  | Programme | programme title | "<channel name> · <start>" with start formatted `d.M. HH.mm` in the device zone | channel logo | `search_type_programme` "PROGRAMME" |
  | Film | provider name | "<category> · <year>" (parts omitted when missing) | provider poster | `search_type_movie` "MOVIE" |
  | Series | provider name | "<category> · <year>" | provider poster | `search_type_series` "SERIES" |
  | Episode | episode name | "<series name> · K<season> J<episode>" (Finnish abbreviations, hard-coded; quirk) | the series' provider poster | `search_type_episode` "EPISODE" |
  | Sport | "<home> – <away>" | "<competition> · <kick-off label>" | competition logo | `search_type_sport` "SPORT" |

- SEARCH-FR-21 A missing or blank thumbnail shows the first letter of the kind label ("C",
  "P", "M", "S", "E", "S") in `focus`, Black.
- SEARCH-FR-22 Row keys (stable across appends): `channel:<channelId>:0`,
  `programme:<channelId>:<start millis>`, `MOVIE|SERIES|EPISODE:<sourceId>:<itemId>`,
  `sport:<eventId>`. Rebuild rule: include the programme id in programme keys so two programmes
  with the same start on one channel cannot collide (a duplicate key crashes a keyed list).

### 4.4 Profiles and visibility

- SEARCH-FR-30 Everything is limited to enabled sources, their active snapshots and the device's
  organisation rules (hidden channels, hidden groups, hidden titles, [Library
  organisation](42-library-organization.md)).
- SEARCH-FR-31 Restricted profile ([Profiles](04-profiles-parental.md)): channel and programme
  rows are kept only when the channel's organisation group key (the custom group's key when the
  channel was moved to a custom group, else the provider group's) is in the profile's allowed
  live groups; film rows when the film's group key is in its allowed film groups; series rows
  when in its allowed series groups. An empty allowed set for a room means everything in that
  room.
- SEARCH-FR-32 In beta 23 the restriction is applied **after** each query's limit, so a
  restricted profile can get fewer results than exist. Episode results are not restricted (a
  series outside the allowed groups can be found through its episodes and played). Sport
  results are never restricted (they are not library groups). Rebuild rule: apply the
  restriction inside the query, before the limit, and restrict episodes by their series' group.
- SEARCH-FR-33 Playing a channel result still runs the play-time group check and PIN gate (spec
  01 SHELL-FR-20), so a result that slipped through is refused with the toast.

### 4.5 Status line

- SEARCH-FR-40 One line under the field, first matching rule wins:

  | Condition | Text |
  |---|---|
  | a search is running (debounce or fetch) | `search_loading` "Searching…" |
  | trimmed text shorter than 2 | `search_scope_hint` "Search covers all downloaded sources." |
  | at least one group failed | `search_failed` "Some search results could not be loaded." (the results of the other groups stay listed) |
  | no rows | `search_no_results` "No results found." |
  | otherwise | plural `search_result_count` "%d result" / "%d results" (all rows) |

- SEARCH-FR-41 Results appear while "Searching…" is still shown (partial results); the count
  replaces it when every group has finished.

### 4.6 Keys and focus

- SEARCH-FR-50 The field has focus on entry; OK on a result acts (section 3). Movement
  between the field, the Back button and the results is left to the platform's focus search in
  beta 23 (no explicit focus properties); the rebuild states it (SEARCH-FR-53).
- SEARCH-FR-51 Result rows are plain focusable rows: focused fill `textPrimary` with inverted
  content, no scale, no shadow. The list scrolls with the keep-visible policy (spec 01
  SHELL-FR-83).
- SEARCH-FR-52 Appending a group never moves the rows already shown, so a focused result keeps
  its place (rows are keyed). A new search clears the list; the viewer is normally in the field
  while typing.
- SEARCH-FR-53 Rebuild rules: state the focus order explicitly (field → Back button with Right
  or Up from the field → results with Down); give the field a visible focus state (today only
  the cursor shows it).

## 5. Screen anatomy

No reference screenshot yet. Sizes at interface size Normal; the shared background with the
default 40/24 dp safe padding.

- Header row (space between): brand `SohvaTvBrand` 34 sp; 24 dp; a column with the title
  `search_title` "Search" (32 sp Black) and the subtitle `search_subtitle` (13 sp
  `textMuted`); at the far end the Back button (`action_back` "Back", back icon).
- Field, 18 dp below: full width, clip medium, fill `surface`, padding 16/14 dp, text 16 sp
  (`body`), cursor `focus`, leading "⌕" glyph (`bodyLarge` size, `textMuted`, 12 dp gap).
- Status line: 12 sp `textMuted`, 9 dp vertical padding.
- Result list: rows spaced 7 dp, 18 dp bottom padding.
- Result row: full width, 68 dp tall, clip small, fill `surface` (focused `textPrimary`),
  padding 10 dp horizontal / 8 dp vertical; thumbnail box 50 dp (`surfaceRaised`, shape small,
  image Fit); 12 dp; title 14 sp Bold 1 line (`textPrimary`, focused `background`); subtitle
  12 sp 1 line ellipsised (`textMuted`, focused `background` α 0.62); kind label 12 sp Black
  (`focus`, focused `background` α 0.72).

## 6. Data

Search stores nothing. It reads, per query: `organization_visible_channels`,
`iptv_source_state`, `import_state`, `channel_preferences`, `tv_programmes`,
`organization_visible_movies`, `organization_visible_series`, `vod_episodes`, the active
profile's allowed-group sets (preferences `allowed_groups_<room>[:id]`), Sohva Sport's
in-memory events, and on an episode result the profile's `playback_progress` (the copy's own
row or the row shared by the film's work key, the newer wins; completed → position 0). The
text and results live only while the screen is composed.

## 7. External interfaces

None. Search makes no network request; thumbnails load through the shared image loader.

## 8. Edge cases and limits

- Very short terms: 2 characters on 56,000 channels and 165,000 programmes match thousands of
  rows; the limits (80 + 40 + 40 + 40) bound the list, not the work (9).
- Terms longer than 80 characters are cut in the field; the repositories cut again at 80.
- A group that throws marks the search partial; the others still show.
- The sports list changing while Search is open (a Sohva Sport refresh landing) restarts the
  search in beta 23 and clears the list for a moment (quirk; rebuild: re-filter only the Sport
  group).
- Programme times use the device zone and a fixed `d.M. HH.mm` pattern, not the chosen time
  zone or the locale (quirk; rebuild: the chosen zone and the locale's pattern, as in the
  guide).
- A programme result plays the channel live even when the programme is in the past or the
  future (see open questions).
- No sources configured: every group returns nothing; "No results found."
- Process death: Search is not restored (the start screen is).

## 9. Lightweight by design

- Current cost per search (after the 250 ms debounce, every keystroke that survives it): up to
  five queries, three at a time on the database's 4 I/O threads:
  - channels + programmes: `LIKE '%term%'` over every visible channel (each row through the
    live organisation view's 16 rule look-ups) and over every programme row of every enabled
    source's active guide (165,000 on the owner's library), then a sort; the plan test requires
    under 1,000 ms for 5,000 channels and 300,000 programmes on a desktop JVM;
  - films: `LIKE` over every visible film (200,000 on the largest known catalogue, each through
    the film view's rule look-ups) and a sort by name;
  - series, and episodes joined to the series view per episode, with a sort.
  On the in-order Cortex-A35 box these scans can each take seconds. A superseded search cannot
  be stopped mid-statement (Room reads in compat mode; cancelling the coroutine does not stop
  SQLite), so fast typing can queue several full scans and starve every other screen's
  queries ([lessons](../plan/08-lessons-learned.md), CursorWindow re-execution).
- Rules for the rebuild:
  - **Full-text index instead of `LIKE` scans.** Keep one search table per import kind
    (channels, programmes, films, series, episodes) holding the searchable text normalised once
    at import (case-folded with full Unicode folding), the row key, the source id, snapshot id
    and organisation group key. Populate it in the import transaction, page by page, for the
    new snapshot only; drop the old snapshot's rows at activation. Query with `MATCH` plus
    `LIMIT`, join to the visibility rules by key only for the ≤ limit candidates. Android's
    platform SQLite is built with FTS3/FTS4, not FTS5 (verify on API 23 and on the low-end box
    before relying on it): FTS4 gives token-prefix matching ("mat*" finds "Match"), not today's
    substring matching inside a word ("at" finds "Match"). See open questions for the choice.
  - **Bounded results.** Apply visibility and the profile restriction before the limit (pass the
    allowed group keys through a small table, not an `IN` list: pre-3.32 SQLite allows only 999
    bound parameters). Limits: 80 channels + programmes, 40 per catalogue kind; sport unlimited
    (in memory, ≤ a day's feed).
  - **Cancellation.** One search in flight at a time. A new term cancels the previous search's
    statements (framework `SQLiteDatabase` queries with a `CancellationSignal`, which SQLite
    checks between steps) and discards any late result. Each group's query must finish within
    100 ms on the low-end box with the owner-scale fixture; a group over 1 s is abandoned and
    reported as partial.
  - Debounce stays 250 ms; the minimum length stays 2. Consider 3 characters for programme
    matches on the low-end class only if the FTS query still misses the budget.
  - Mapping rows to display models runs off the main thread (today on the default dispatcher);
    date formatting happens once per row there, not in composition.
  - Memory: at most about 200 small row models plus the sport rows; thumbnails decoded at
    50 dp (about 100 px) square, logos ARGB, posters `RGB_565`.
  - Per frame: plain rows (one rectangle, one small image, three texts); no shadows, no scale,
    no animation. The ground is the shared cached layer.
  - Nothing is precomputed or warmed at start-up for Search; the FTS tables are maintained by
    the imports, not by opening Search.

## 10. Lessons from the current app

- Search groups used to wait for each other; since 15 September 2026 they load independently
  and append as they finish, so the first rows appear sooner and never move (`d84e5e9`, shared
  helper `ParallelResults.kt`).
- Programme search used to join programmes broadly; it is now channel-first through the
  `(sourceId, xmltvChannelId, start, stop)` programme index, keeps contains-matching and all
  imported dates, and is guarded by a plan-and-timing test (`GuideSearchQueries.kt`,
  `CatalogueHomeQueryPlanTest.guideSearchKeepsContainsMatchingAndAllDatesWithoutBroadProgrammeJoins`).
- The film and series views must be reached by key, never walked, wherever possible (the
  organisation view join-order trap); Search is the one hot path that still walks them.
- Quirks to fix in the rebuild (current behaviour, not intended):
  - Episode subtitles use hard-coded "K<season> J<episode>" instead of `series_episode_label`
    ("S%1$d E%2$d" in English).
  - Episode results ignore the profile restriction; restriction filtering happens after the
    limit.
  - Programme times ignore the chosen time zone and the locale.
  - ASCII-only case folding in the database groups.
  - The order of groups depends on which finished first.
  - A sport result opens the day's list, while Home's sport card opens the match itself.
  - Initial focus uses a fixed 80 ms delay (the pattern the shell rules forbid).

### Open questions

- Matching semantics for the rebuild: FTS4 token-prefix matching (fast on every API level,
  but "at" no longer finds "Match"), or substring matching kept with a bundled SQLite and FTS5's
  trigram tokenizer (larger APK, about 1–2 MB, against the 10 MB budget)?
- Should matching fold accents (Finnish viewers may not want "a" to find "ä")?
- Should a programme result open the guide at that programme (or catch-up for a past one)
  instead of playing the channel live?
- Should a sport result open its match card, as Home's sport card does?
- Fixed group order (for example channels, programmes, films, series, episodes, sport) with
  placeholders, or today's completion order?

## 11. Acceptance tests

Unit (JVM):
- Term handling: trimmed, < 2 characters runs nothing, cut at 80.
- Group assembly: groups appended in completion order, a failed group marks the search partial
  and keeps the others (mirror `ParallelResultsTest`).
- Restriction: channels, programmes, films, series and episodes outside the allowed groups are
  absent, and the limit counts only allowed rows (new).
- Query plans (mirror the guide search plan test): the FTS queries use their index; no scan of
  `vod_movies`, `vod_series`, `vod_episodes`, `iptv_channels` or `tv_programmes`; timing on a
  synthetic library at owner scale (56,164 channels, 165,600 programmes, 200,000 films, 1,500
  series × 12 episodes).
- Status line priority for each state in 4.5.

Instrumentation (debug build):
- Entry: the field is focused; typing one character shows the scope hint; two characters show
  "Searching…" then results.
- A channel result plays the channel; Back lands in the guide on that channel.
- A film result opens its details; Back returns to Search.
- An episode result plays from its saved position; finishing it with autoplay off opens the
  series page (mirror the playback-completion tests).
- A restricted profile does not see a hidden group's channel, film, series or episode.
- Typing quickly (five characters within 250 ms) runs one search only.

Manual, on a device:
- Search with the on-screen keyboard; Back closes the keyboard, a second Back leaves Search.

Low-end performance (Elisa-class box or the `.local/slowbox` harness; release build):
- Owner-scale fixture: each keystroke's search completes within 300 ms of the debounce; typing
  ten characters quickly leaves no search running afterwards (trace: no `arch_disk_io` activity
  after the last result).
- The guide opened right after a search responds within budget (no queued search statements).

## 12. Reference: current code map

- `app/src/main/java/com/streammate/tv/feature/search/SearchScreen.kt` — screen, debounce, parallel groups, result rows, routing.
- `core/src/main/java/com/streammate/tv/core/database/GuideSearchQueries.kt` — channel and programme search SQL.
- `core/src/main/java/com/streammate/tv/core/database/GuideDao.kt` — `searchGuide`.
- `iptv/src/main/java/com/streammate/tv/iptv/repository/GuideStore.kt` — `search` (limits 2/80/200, restriction filter).
- `core/src/main/java/com/streammate/tv/core/database/CatalogueDao.kt` — `searchMovies`, `searchSeries`, `searchEpisodes`.
- `iptv/src/main/java/com/streammate/tv/iptv/repository/CatalogueRepository.kt` — `search` (limits 2/80, 40 per kind up to 100, restriction filter, subtitles), `progress`.
- `core/src/main/java/com/streammate/tv/core/concurrent/ParallelResults.kt` — bounded parallel groups emitted as they finish.
- `core/src/main/java/com/streammate/tv/core/database/OrganizationViews.kt` — the visibility views searched through.
- `app/src/main/java/com/streammate/tv/app/StreamMateApp.kt` — Search wiring and result routes.
- Tests: `CatalogueHomeQueryPlanTest` (guide search plan and timing), `ParallelResultsTest`, `MultiSourceGuideDatabaseTest` (EPG offset in search).
