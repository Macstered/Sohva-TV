# Movies and series

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

Layout measurements for every screen in this spec are already extracted from the code in
[design/screens/movies-and-series.md](../design/screens/movies-and-series.md) (cited below as
"layout §n"). This spec gives behaviour, data and cost; it repeats a measurement only where
behaviour depends on it. Importing catalogues is [Sources and import](10-sources-and-import.md);
TMDB/TVmaze matching, work keys and genres are [Metadata](41-metadata-enrichment.md); hide/show,
sort orders, manual order and the library manager are [Library organisation](42-library-organization.md).

## 1. Summary

Movies and Series are two poster walls over the viewer's provider library (Xtream or M3U
"VOD"). A rail on the left chooses what the wall shows: **History** (what this profile played,
newest first), one of the provider's **groups**, or — in the **Genres** view — one of 22 genres,
a group of the viewer's own (genres, years, minimum rating), or **Unsorted**. The same film
carried by several playlists, groups or qualities is shown **once**, standing on the copy the
household prefers (Finnish audio, Finnish subtitles or the largest picture). A title opens a
**details page** — a film page with resume, versions, cast and similar titles, or a series page
with seasons and episodes — where the viewer plays, resumes, restarts, marks watched and fixes a
wrong match. Positions are kept per profile and shared between copies of one film; they feed
Home's Continue watching. The rebuild must browse a **200,000-film** catalogue on a 2 GB
Cortex-A35 box, which the current wall cannot: it holds each whole partition in memory.

## 2. Feature checklist

Walls
- VOD-01 Two poster walls, Movies and Series, each with a header, a left rail and a poster grid.
- VOD-02 Header: "Movies"/"Series", the destination's name and title count, a "Loading library…" or failure note while an older wall is still shown, and the last refresh result.
- VOD-03 History destination: titles this profile played (finished ones included), newest first.
- VOD-04 Groups view: the provider's groups with film counts, in the library's group order.
- VOD-05 Genres view: the viewer's own groups, then each of 22 genres that has titles, then Unsorted, with counts.
- VOD-06 Groups / Genres toggle that returns to the last destination used in each view.
- VOD-07 Custom groups (genres, year range, minimum rating) as wall destinations.
- VOD-08 An "all groups" wall when every provider group is hidden.
- VOD-09 Poster grid with adaptive columns (6 at the default interface size), no sort bar or letter index.
- VOD-10 Poster card: 2:3 poster, one-line title, "year · rating".
- VOD-11 Poster fallback: provider poster, then the matched TMDB poster, then the title's initials; a chosen match's poster replaces the provider's.
- VOD-12 Picture-quality chips read from the provider's title (4K UHD; Dolby Vision, HDR10+ or HDR10).
- VOD-13 One card per film when several playlists, groups or qualities carry it, marked "×N".
- VOD-14 "When a film has more than one version" setting chooses the copy a folded card opens (None, Finnish audio, Finnish subtitles, Largest picture).
- VOD-15 A folded card takes a missing poster, year, rating or genre from its other copies.
- VOD-16 Watched tick on film posters, whichever copy was watched (Trakt marks included).
- VOD-17 Search inside the selected destination by provider title or matched title.
- VOD-18 The previous wall stays visible (not selectable) until the next one is ready; loading, failure, empty and no-results messages.
- VOD-19 Wall order follows the library organisation (A–Z by default, per-group sort, manual order); History by recency.
- VOD-20 Library options sheet: Refresh, Edit (library manager), Back, Close.
- VOD-21 Refresh imports every enabled VOD source and reports "Imported N movies and M series".
- VOD-22 Left from the wall's first column returns to the selected rail row, scrolling it into view.
- VOD-23 Coming back from a title focuses the same card (or its film's standing copy) with both scroll positions kept.
- VOD-24 Each mode keeps its browse position (destination, view, scroll, focused card) while the app runs.
- VOD-25 First entry focuses History (else the first group, else Options).
- VOD-26 A restricted profile sees only its allowed groups' titles.

Film page
- VOD-27 Film page: full-bleed backdrop, breadcrumb, title, facts (score, year, runtime, quality chips), synopsis.
- VOD-28 Progress line "Watched X of Y", bar and "Z left" for a partly watched film.
- VOD-29 Resume or Watch (first focus); Start from beginning when there is a position.
- VOD-30 Mark as watched / Mark as unwatched.
- VOD-31 "Source: TMDB" (or TVmaze) opens the metadata provider's page.
- VOD-32 "Wrong details?" opens the match picker.
- VOD-33 Versions row: every copy of the film with its source and what its name claims; the chosen copy plays at the film's position.
- VOD-34 Cast row with photo or initials, name and character.
- VOD-35 Similar row: TMDB's similar films that the library has, with "checking" and "none" states.
- VOD-36 Details are looked up when the page opens, and a missing library poster is repaired.

Series page
- VOD-37 Series page: backdrop, breadcrumb, title, facts (score, year, season count, runtime), synopsis, one-line cast.
- VOD-38 Season buttons with a tick on seasons watched to the end.
- VOD-39 Episode cards: still, "S1 E2", title, part-watched bar, watched badge, selection rule, duration.
- VOD-40 Focusing an episode selects it; the buttons, progress line and runtime follow the selection.
- VOD-41 Continue episode / Watch episode; Start from beginning; Mark as watched / unwatched for the selected episode.
- VOD-42 Mark season as watched.
- VOD-43 Episodes fetched from the provider on first open; Refresh episodes on demand.
- VOD-44 "Loading episodes…" pill; loading, error and empty messages for episodes.
- VOD-45 Wrong details? and Source for series.
- VOD-46 OK on an episode card plays that episode from its position.

Watched state and progress
- VOD-47 Positions saved per profile and per copy; the copies of one film share a position.
- VOD-48 Watched at 90 %, or with 3 minutes left on anything of 10 minutes or more.
- VOD-49 Continue watching feed for Home: one card per film or series, newest first.
- VOD-50 Held-OK actions on a Continue watching card: Resume, Start from beginning, Mark as watched, Remove from Continue watching.
- VOD-51 Next-episode lookup across seasons (for autoplay).
- VOD-52 Trakt positions and watched marks overlaid on library progress.

Match picker
- VOD-53 "Choose the right title": searches the provider's name on open, manual search, results with artwork, year and synopsis; choose, Undo my choice, Close.

Artwork
- VOD-54 Artwork disk cache limit 100 / 250 / 500 MB (default 250), applied at the next start; usage and Clear in Settings.
- VOD-55 Posters decoded at wall size; bounded memory cache; at most two images decoded at once.

Shared
- VOD-56 Genre vocabulary of 22 genres with localized names, plus Unsorted.
- VOD-57 Title initials placeholder for missing artwork and cast photos.
- VOD-58 Catalogue lookups used elsewhere: search (films, series, episodes), playable stream, title by key, next episode.

## 3. Entry points and navigation

Destinations (the shell's stack, [App shell](01-app-shell-navigation.md) §3):

| Destination | Opened from | Back |
|---|---|---|
| `Catalogue(MOVIES)` / `Catalogue(SERIES)` | Home rail and Home menu Movies / Series (SHELL-FR-62); placed under a title resumed from Home (SHELL-FR-28) | Closes the Options sheet if open, else pops (a non-empty search is cleared first) |
| `MovieDetails(film)` | a wall card (the card's standing copy), Search, a Similar card, Home resume (under the player), a Trakt card, a finished film (SHELL-FR-29) | Pops; the match picker, when open, closes first |
| `SeriesDetails(series)` | a wall card, Search, Home resume, a Trakt card, a finished last episode | Pops; the match picker closes first |
| `LibraryManager(room, group)` | Options › Edit | Pops back to the wall; focus on the Options button |
| `VodPlayer(contentKey, position)` | any play action here (SHELL-FR-27) | Pops to the page it came from ([Player](30-player.md)) |

- A wall card opens its details only after the title has been looked up as an active record
  (film by content key, series by source and series id); if the lookup finds nothing (the source
  was disabled or re-imported meanwhile) nothing happens. **Rebuild:** show the toast
  `catalogue_source_disabled` ("The Xtream source is no longer enabled") instead of silence.
- A Similar card pushes another `MovieDetails` on top; Back returns to the previous film page.
- Focus on entry and return: see VOD-FR-49…VOD-FR-58 (wall), VOD-FR-72 (film page),
  VOD-FR-88 (series page). The shell keeps the two browse sessions (one per mode) for the life of
  the app composition (SHELL-FR-14).
- Deep links from other features: Search results (specs/03), Home Continue watching and Trakt
  cards (specs/02, specs/51), autoplay and playback end (specs/30, SHELL-FR-29).

## 4. Behaviour

### 4.1 Modes and destinations

- VOD-FR-01 Two modes, MOVIES and SERIES, share one screen. Differences: only MOVIES folds copies
  (4.4) and shows watched ticks (VOD-FR-37); the title, search label and library room differ.
- VOD-FR-02 A wall request is `(mode, destination, search)`. Destinations ("partitions"):
  `History`; `PlaylistGroup(name)` (name absent = all groups); `Genre(genre)`;
  `CustomGroup(id)`; `Unsorted`. Each is a slice the database can answer directly.
- VOD-FR-03 **Groups view** rail: History (when enabled, VOD-FR-08), then every provider group
  that has at least one visible title. A group is the distinct `categoryKey` =
  `lowercase(trim(categoryName))` over the active snapshots of enabled sources; its label is the
  smallest trimmed spelling (`MIN(TRIM(categoryName))`); its count is the number of distinct
  films (film identity, see 4.4) for movies and the number of titles for series. Groups are
  ordered by the organisation's group sort ([Library organisation](42-library-organization.md)):
  the default ("provider") keeps the query's case-insensitive A–Z order by name (a VOD group has
  no provider position); A–Z / Z–A use the title collator (Finnish, primary strength); manual
  uses the saved positions. With manual group sort the History row is sorted by its own saved
  position too (a row without a position sorts after all positioned rows, keeping its place among
  them). A restricted profile sees only allowed groups (4.15).
- VOD-FR-04 **Genres view** rail: History (when enabled), then the viewer's custom groups in
  their saved order, then each of the 22 genres (VOD-FR-12, fixed enum order) whose count is
  above 0, then Unsorted when its count is above 0. Genre count = distinct films per genre
  (movies) or titles (series); Unsorted = titles with no genre row. Every title carries at most
  one (primary) genre ([Metadata](41-metadata-enrichment.md)), so a title appears under one genre.
  Custom-group counts are not computed for the rail (opening the rail must not run every saved
  filter); a custom row shows a count only while it is selected and its wall is current (the
  wall's size). Genre counts are observed only once the Genres view has been used, and only after
  the first genre wall request has started.
- VOD-FR-05 Rail rows: label up to 2 lines; trailing count (group count, facet count, or — for
  History and custom groups — the wall's size while selected and current); Replay icon on History;
  `selected` marks the current destination. Labels: `catalogue_history` "History", genre keys
  `genre_*`, `catalogue_genre_unsorted` "Unsorted", the custom group's name, the provider group's
  name as written.
- VOD-FR-06 OK on a rail row selects it; focus alone does not. Selecting cancels a pending search
  debounce, remembers the row as the last destination of its view, and starts the wall request.
  Selecting the destination already loading does nothing.
- VOD-FR-07 Toggle **Groups** (`catalogue_grouping_groups`): go to the last Groups destination if
  it still exists; else the first visible group; else all groups (once the group list is known);
  else nothing is selected (idle wall). Toggle **Genres** (`catalogue_grouping_genres`): the last
  Genres destination if it is still among the rows; else the first row; else `Genre(ACTION)` at
  once, without waiting for counts. Switching view scrolls the rail to the top. History keeps
  whichever view is active.
- VOD-FR-08 History can be hidden or positioned by the organisation shortcut rule `@history`
  ([Library organisation](42-library-organization.md)). Hidden while selected → the first group
  (or all groups) once the group list is known; the "first entry" focus flag is re-armed.
- VOD-FR-09 Group list changes: nothing selected in the Groups view → select the first group, or
  all groups when there is none (remembered as chosen automatically). All groups chosen
  automatically and groups appear → the first group. The selected group disappears while others
  remain → the first group. Every group is hidden but the library has groups → all groups.
- VOD-FR-10 Genre rows change while in the Genres view: the selection no longer exists → the first
  row. No rows at all: if the selection was a deleted custom group → the Groups view's first group
  (or all groups); empty genre counts alone never switch view (they can arrive before the first
  indexed genre wall).
- VOD-FR-11 **Custom group**: `id` (stable across renames), `name`, `genres` (set),
  `fromYear`, `toYear` (inclusive), `minRating` (out of 10). Usable only with a non-blank name and
  at least one condition; unusable groups are dropped when read. A title belongs when every set
  condition holds: any listed genre; year inside the bounds (a title without a year fails a year
  bound); rating ≥ minimum, where the **provider's** rating string must start with a digit, a
  comma counts as a decimal point and the leading number is read (`e`/`E` are neutralised so
  "1e5" cannot parse as a large number). A title with no genre never matches a group that names
  genres. At most 24 groups (`MAX_CUSTOM_GROUPS`). Groups are edited in Settings / the library
  manager ([Library organisation](42-library-organization.md), [Settings](70-settings.md)).
  Editing the selected group replaces its query; deleting it follows VOD-FR-10.
- VOD-FR-12 **Genre vocabulary** (`wireValue` stored in the database; `VERSION = 2`, bumped
  whenever the list or its mapping changes): action, adventure, animation, comedy, crime,
  documentary, drama, family, fantasy, history, horror, music, mystery, news, reality, romance,
  science_fiction, soap, talk, thriller, war, western. English labels: Action, Adventure,
  Animation, Comedy, Crime, Documentary, Drama, Family, Fantasy, History, Horror, Music, Mystery,
  News, Reality, Romance, Science fiction, Soap, Talk show, Thriller, War, Western (`genre_*`).
  An unknown stored value is ignored.

### 4.2 Loading states of the wall

- VOD-FR-13 Every wall request carries a serial; a result is applied only if its serial is the
  latest and its destination is still selected. A late result of a cancelled request never
  replaces the current wall.
- VOD-FR-14 While a new request loads, the previous wall stays on screen but its cards are
  neither focusable nor clickable, and the header shows `catalogue_v2_loading` "Loading
  library…" (caption). On failure the header shows the failure note. No spinner, no skeleton.
- VOD-FR-15 Wall area: entries → the grid. No entries and loading → "Loading library…". No
  entries and failed → `catalogue_v2_failed` "The library could not be loaded." (beta 23 shows
  the exception's own message when it has one; **rebuild:** always this text, the detail goes to
  the diagnostics log). Current and empty → `catalogue_v2_empty_group` "No titles in this group."
  without a search, `catalogue_no_results` "No matching items found." with one. Nothing selected →
  nothing. Messages: `textMuted`, bodyLarge, centred.
- VOD-FR-16 Transient empties (imports and metadata writes invalidate queries mid-change): an empty
  result for a provider group whose count says it has titles (no search) is held back 500 ms
  (`TRANSIENT_EMPTY_GRACE_MILLIS`) and published only if it is still the latest request and either
  no non-empty wall of the same request is showing or the catalogue generation has changed. An
  empty **group list** replaces a non-empty one only after 500 ms and only if the generation
  changed; a group list that is empty only because the viewer hid every group is accepted at
  once.
- VOD-FR-17 The catalogue **generation** is an invalidation token: the ordered list of enabled
  sources' `(sourceId, activeSnapshotId, updatedAt, itemCount)` plus the organisation rules and
  film aliases. It is never a loading flag.
- VOD-FR-18 A current wall that changes underneath (metadata worker writes, organisation change,
  import activation) updates in place: it stays selectable, and focus stays on the same card by
  content key. Data arriving never moves focus.

### 4.3 What a wall contains and in which order

- VOD-FR-19 Base set: titles of the **active snapshot** of **enabled** sources, visible under the
  organisation rules, allowed for the profile (4.15), and in the destination: provider group by
  `categoryKey`; all groups = every title; genre = has that genre row; Unsorted = no genre row;
  custom group = VOD-FR-11; History = VOD-FR-93.
- VOD-FR-20 Order (beta 23): the query sorts by provider name, then the organisation orders the
  list ([Library organisation](42-library-organization.md) `orderedItems`): items are grouped by
  provider group, groups follow the group sort (VOD-FR-03), and inside each group the item sort
  applies — default **A–Z by provider title** with the Finnish collator at primary strength; Z–A,
  newest, oldest, rating and manual are the other choices; ties fall back to the title, then the
  identity. History is kept in recency order (no reordering). Consequence: walls that span groups
  (genre, custom, Unsorted, all groups) are ordered group by group, not as one A–Z list. See open
  question 2.
- VOD-FR-21 A wall entry carries only: content key; target (film: source id + movie id; series:
  source id + series id); provider title; provider group; provider poster URL; year (the provider's,
  else a `(19xx|20xx)` year in brackets or parentheses in the title, regex
  `[\[(]((?:19|20)\d{2})[\])]`); rating (the provider's string, shown as written); genres; and the
  metadata override (replacement title, replacement poster, "replace provider poster" flag,
  external id, genres version) — present only when a replacement title exists. No stream URL,
  plot or cast crosses into the wall.
- VOD-FR-22 Display title = the replacement title when non-blank, else the provider title.
- VOD-FR-23 Content keys: film `vod:movie:<sourceId>:<movieId>`, episode
  `vod:episode:<sourceId>:<episodeId>`, series `series:<sourceId>:<seriesId>`; ids match
  `[A-Za-z0-9._-]{1,128}` (a key that does not parse is ignored by every progress write).

### 4.4 Copies of one film: folding, claims and which copy plays

- VOD-FR-24 Films only; series are never folded (every series card counts 1). Folding works on the
  entries of the current wall request, so a film appears in each group that carries a copy, and
  stands there for the copies in that group.
- VOD-FR-25 Copies fold when their **film identity** is equal: `tmdb:<externalId>` once the title
  is matched, else `name:<normalised title>:<year>` ([Metadata](41-metadata-enrichment.md),
  "work key"). A library with no duplicates is left untouched.
- VOD-FR-26 The **standing copy** is the one with the highest preference score (VOD-FR-29); copies
  the preference cannot separate keep wall order (the first one wins). The ranking deliberately
  ignores how complete a copy looks: completeness changes while titles are matched, and a card
  that changed identity mid-browse would take focus with it.
- VOD-FR-27 The folded card takes, in copy order with the standing copy first: the first non-blank
  poster, year and rating; the union of genres; the metadata override with the highest genres
  version (else the one with a replacement poster, else the first); the distinct union of all
  copies' quality chips; and the copy count. Its key is the standing copy's real content key, so
  positions, chosen posters and metadata keyed by content key keep working. A copy → film map is
  kept for focus restoration and for reading per-copy data (watched marks) by film.
- VOD-FR-28 **Claims** read from a copy's name (display and ranking only, never trusted as fact):
  - Languages only where a provider announced them — the claim zones, regex
    `(?i)^(?:\s*\p{L}{2,7}\s*[|•·:–—-]+)+|[\[({][^\])}]*[\])}]|(?:[|•·]|\s[-–—])\s*(?:multi[- ]?\w+\s*)+$`
    (a prefix closed by a delimiter, anything bracketed, or a technical tail). The zones are joined,
    lower-cased and searched for markers bounded by `(?<![a-z0-9])…(?![a-z0-9])`: Finnish `fi|fin|suomi`,
    Swedish `sv|swe`, English `en|eng`, Danish `da|dan`, Norwegian `no|nor`, German `de|ger`,
    French `fr|fre`, Spanish `es|spa`, Nordic `nordic|nc`; subtitled `multi[- ]?(?:subs?|subtitles?)`;
    several audio tracks `multi[- ]?audio`. "Fin del mundo" is not a Finnish copy.
  - Picture from the whole name: the quality chips (VOD-FR-30) plus a resolution
    `(?<![A-Za-z0-9])\d{3,4}[pP](?![A-Za-z0-9])`, lower-cased ("1080p"). `FHD` is not read as 1080p.
  - Examples: "FIN | The Matrix (1999) 4K" → Finnish, 4K UHD; "The Matrix 1999 [MULTI-SUBS] 1080p"
    → Subtitled, 1080p; "NORDIC - The Matrix - HDR10" → Nordic, HDR10; "Apollo 13pm" → no picture.
- VOD-FR-29 **Preference score** (setting `preferred_catalogue_copy`, VOD-FR-32):
  - NONE → 0 for every copy.
  - FINNISH_AUDIO → Finnish 3, several audio tracks 2, Nordic 1, else 0.
  - FINNISH_SUBTITLES → subtitled 3, Finnish 2, Nordic 1, else 0.
  - LARGEST_PICTURE → size × 2 + (1 if any of Dolby Vision, HDR10+, HDR10), size = the largest of
    "4k uhd"/"2160p" 4, "1080p" 3, "720p" 2, "480p" 1, else 0.
- VOD-FR-30 **Quality chips** from a title (upper-cased first): "4K UHD" when `4K` or `UHD` stands
  alone (`(?:^|[^A-Z0-9])(?:4K|UHD)(?:[^A-Z0-9]|$)`); then at most one of "Dolby Vision"
  (`DOLBY VISION` or `DOVI` anywhere), "HDR10+", "HDR10" (`HDR10` anywhere or `HDR` alone). Shown
  on single-copy films and on series too.
- VOD-FR-31 Which copy plays: the card opens the **standing copy's** film page; Watch/Resume there
  plays that copy; the Versions row (VOD-FR-68) plays any other copy at the film's position.
- VOD-FR-32 Setting "When a film has more than one version" (`preferred_copy_title`, help
  `preferred_copy_help` "Which version a film's poster plays when two playlists carry it."):
  `preferred_copy_none` "Whichever comes first" (default), `preferred_copy_finnish_audio`
  "Finnish audio", `preferred_copy_finnish_subtitles` "Finnish subtitles",
  `preferred_copy_largest_picture` "Largest picture". Device-wide, included in backups. The
  Settings control lives in [Settings](70-settings.md).

### 4.5 Poster cards

- VOD-FR-33 Anatomy and sizes: layout §1 "Poster card" and "Poster image". Focus: 3 dp
  `textPrimary` ring on the poster, title turns `textPrimary`; no scale, elevation, animation or
  click indication.
- VOD-FR-34 Poster URL: if the provider poster already failed on this card → the replacement
  poster; no provider poster → the replacement; the override says "replace provider poster" → the
  replacement, else the provider's; otherwise the provider's.
- VOD-FR-35 Poster states: no URL → the title's initials (VOD-FR-38) on the tile ground; loading →
  the tile ground; loaded → the image, cropped; provider image fails → the replacement poster is
  tried once; that fails too → beta 23 leaves an empty tile, **rebuild:** initials.
- VOD-FR-36 Badges on the poster: watched tick at top start (films only); chips at top end, 6 dp
  in, 4 dp apart: "×N" (`catalogue_copy_count`, accent tone) when the card stands for 2+ copies,
  then the quality chips (primary tone).
- VOD-FR-37 **Watched ticks** (film walls only): progress is read for the cards in view ±12, at most
  200 at a time. A card is ticked when the newest progress row among its own content key and its
  film identity is completed (so a film finished on one copy is ticked on the other); Trakt marks
  overlay per [Trakt](51-trakt.md). Series walls read no progress. Cards carry no progress bars and
  no rating badges.
- VOD-FR-38 **Initials**: split the trimmed title on space `.` `-` `:` `_` `·` `/`, keep the words
  whose first character is a letter; none → the first two characters upper-cased; one → its first
  two characters upper-cased; more → the first letters of the first two words. "Ad Astra" → AA,
  "Aladdin" → AL, "2 Guns" → GU, "1917" → 19, blank → "". Shared by posters, similar cards and
  cast avatars.
- VOD-FR-39 Card facts line: `year · rating` (either omitted when missing), `textDim`, caption.

### 4.6 Search inside a destination

- VOD-FR-40 The search field is the first control in the rail (compact, Search icon), labelled
  `catalogue_search_movie` "Search movies" / `catalogue_search_series` "Search series". OK starts
  editing (the D-pad passes over it otherwise). Text is cut to 80 characters.
- VOD-FR-41 Each change: the pending query is cancelled, the wall goes to loading (old wall kept,
  VOD-FR-14); a non-blank text waits 250 ms (`CATALOGUE_V2_SEARCH_DEBOUNCE_MILLIS`) before
  querying; clearing the text queries at once.
- VOD-FR-42 Matching: case-insensitive substring of the provider title **or** the replacement title,
  on the trimmed text, inside the selected destination only. History filters its own list by the
  same rule (beta 23 does not trim there; the rebuild trims everywhere).
- VOD-FR-43 Header count while searching = the wall's size after folding; otherwise the
  destination's count (VOD-FR-03/04; History = the wall's size).
- VOD-FR-44 A new search scrolls the wall to the top and forgets the focused card. Opening a title
  or leaving the screen clears a non-empty search (the wall returns to the whole destination).

### 4.7 Library options sheet

- VOD-FR-45 The Options button (`catalogue_options` "Options", Info icon, full rail width) opens
  the sheet (layout §1 "Options sheet"): title `catalogue_options_title` "Library options",
  subtitle `catalogue_subtitle` "Your provider’s library", buttons Refresh (first focus), Edit,
  Back (`action_back`), Close (`catalogue_close_options`). Back closes the sheet.
- VOD-FR-46 **Refresh** (`action_refresh`; "Refreshing…" `action_refreshing` and disabled while
  running): for every enabled source whose import scope includes VOD: an M3U source with a
  derivable Xtream account imports through Xtream, other M3U sources through the M3U importer, an
  Xtream source through Xtream ([Sources and import](10-sources-and-import.md)); then the metadata
  job is restarted. Success → header text `catalogue_imported` "Imported %1$s and %2$s" with the
  plurals `catalogue_imported_movies` ("%1$d movie(s)") and `catalogue_imported_series`
  ("%1$d series"). No VOD source → `catalogue_add_xtream_first` "Add an M3U or Xtream source that
  imports VOD in Settings first". Failure → the error's plain-language message. The demo build
  restores its library and says `catalogue_demo_refreshed` "Demo library restored". The sheet stays
  open during the refresh.
- VOD-FR-47 **Edit** (`category_edit` "Edit", Check icon): closes the sheet and opens the library
  manager for this room at the selected provider group (`@history` when History is selected, none
  for genre destinations). Coming back focuses the Options button.
- VOD-FR-48 **Back** in the sheet leaves the screen like the Back key; **Close** closes the sheet.
  (Beta 23 also has an in-place "show/hide groups" edit list used only by tests; the rebuild does
  not need it.)

### 4.8 Focus and keys on the wall

- VOD-FR-49 First entry into a mode in the app session: the rail is scrolled to History and History
  is focused; with History hidden, the first group once groups are known; with no groups, the
  Options button.
- VOD-FR-50 **Left-to-rail rule.** Left pressed on a card in the wall's **first column** (wall
  current, not editing) scrolls the rail to the destination row (the selected destination if it is
  in the rail, else History, else the first row) and focuses it; with an empty rail, the Options
  button. Left in any other column moves to the card on the left. The column is derived from the
  card's **absolute index**: `column = index mod columnCount`, where `columnCount` comes from the
  grid's measured width and the 88 dp minimum cell — never from the grid's momentary list of
  visible items (beta 23 reads `visibleItemsInfo`, which is empty before layout and churns during
  scrolls and recompositions; an intermittent "Left does nothing" report is open against it,
  see §10).
- VOD-FR-51 Other keys in the wall: Up/Down/Right use normal grid traversal; Right from the rail
  lands on the nearest wall card (only when the wall is current, since stale cards cannot take
  focus). No special meaning for Menu, number, channel or media keys on these screens (they fall
  through to the shell, which ignores them here).
- VOD-FR-52 OK on a card: remember its key and that focus was on the wall, clear a non-empty
  search, open details (§3).
- VOD-FR-53 Back: sheet open → close it; else leave the screen (clearing a non-empty search). With
  the keyboard open, the platform closes it first.
- VOD-FR-54 Choosing another destination scrolls the wall to the top and forgets the focused card;
  switching view scrolls the rail to the top; a new search scrolls the wall to the top. Focusing a
  rail row clears "return focus to the wall".
- VOD-FR-55 Focusing a card records it as the focused card and marks that focus was on the wall.

### 4.9 Browse session and return focus

- VOD-FR-56 One session per mode lives as long as the app composition: destination and view,
  search text, rail and wall first-visible index and offset, focused card key, and the flags
  "return focus to the wall", "first entry pending" and "return focus to Options". Beta 23 also
  keeps the last wall's full entry list in it. **Rebuild:** the session keeps keys and positions
  only (no entries), and is reset when the active profile changes (beta 23 does not reset it, so a
  new profile can briefly see the previous profile's wall and History until the first query
  answers).
- VOD-FR-57 Return from details (or any screen above): if focus was on the wall, find the saved key
  in the wall; missing → the standing copy the key now folds into, else the first card; scroll it
  into view if it is not visible, wait one frame, focus it. Otherwise restore the rail position; a
  return from the library manager focuses Options (VOD-FR-47).
- VOD-FR-58 Beta 23 re-runs the whole destination's query on every return, with cards disabled
  until it answers. **Rebuild:** when the catalogue generation is unchanged, the retained window
  (§9.3) is shown and focusable in the first frame; otherwise only the pages around the saved card
  are re-read (VOD-FR-18).

### 4.10 Film page

- VOD-FR-59 Opened with the active copy's record: content key, source, movie id, provider name,
  group, poster, year (with the title fallback, VOD-FR-21), rating, plot.
- VOD-FR-60 Metadata: whatever the metadata memory cache holds for `(MOVIE, name, year)` shows in
  the first frame. When metadata is enabled, a details lookup runs ([Metadata](41-metadata-enrichment.md):
  TMDB details with cast and similar titles); its result replaces the cached one, and a missing
  catalogue poster is repaired from it (a no-op when the poster exists).
- VOD-FR-61 Title = metadata title; else the provider name cleaned by the metadata matcher's
  search-title rule; else the raw name. Synopsis = metadata overview, else the provider's plot,
  else `no_details_available` "No additional details are available." (4 lines). Facts: the score
  (metadata rating, else the provider's) with a star; year (metadata, else provider); runtime when
  the metadata has one; quality chips from the provider name. Runtime format: `details_runtime_hours`
  "%1$d h %2$d min" from 60 minutes (so 120 → "2 h 0 min"), else `series_episode_duration_minutes`
  "%1$d min".
- VOD-FR-62 Breadcrumb: `MOVIES › <GROUP> › <TITLE>`, upper-cased; the group is shown with every
  `[…]` and `(…)` part removed and runs of spaces collapsed (regexes `\[[^\[\]]*]|\([^()]*\)` and
  `\s{2,}`), falling back to the raw name when nothing is left (" Movies [Multi-Sub] (4K) " →
  "Movies"; "[4K]" → "[4K]"); a missing group is left out. Computed once per page, not per frame.
- VOD-FR-63 Progress line (layout §2 "Progress") only when a position exists, the film is not
  finished, and position and duration are both above 0: `details_watched_of` "Watched %1$s of
  %2$s", a 220 × 4 dp bar, `details_time_left` "%1$s left"; minutes are rounded up.
- VOD-FR-64 Resume position = the progress row's position when not finished and above 0; else none.
  Primary button: `details_resume` "Resume" with a position, else `action_watch` "Watch"; plays
  this copy from the position (or 0). `details_restart` "Start from beginning" only with a position;
  plays from 0.
- VOD-FR-65 `details_mark_watched` "Mark as watched" / `details_mark_unwatched` "Mark as unwatched"
  by the finished flag; acts per VOD-FR-96.
- VOD-FR-66 `metadata_source` "Source: %1$s" (the provider's attribution name) only when metadata
  exists; OK asks the platform to open the attribution URL; failure (no browser on the TV) is
  ignored.
- VOD-FR-67 `match_picker_open` "Wrong details?" is always present (a wrong match and no match are
  the same question) and opens the match picker (4.14).
- VOD-FR-68 **Versions** (`details_versions`), only when the film has 2 or more copies: every active,
  visible copy whose film identity equals this copy's, ordered by source priority, source name,
  title. Card (layout §2 "Version card"): source name; `details_version_current` "Selected" chip on
  the page's own copy (and a raised resting fill); a claims line — the language labels
  (`copy_language_finnish` Finnish, `_swedish` Swedish, `_english` English, `_danish` Danish,
  `_norwegian` Norwegian, `_german` German, `_french` French, `_spanish` Spanish, `_nordic` Nordic,
  `_subtitled` Subtitled, `_multiple_audio` "Several audio tracks") then the picture claims, joined
  " · ", or the provider's full name when it claims nothing. Content description
  `details_version_play` "Play this version". OK plays that copy at the film's resume position
  (or 0). Beta 23 finds candidates by a name search (at most 200) and keeps those with the same
  identity.
- VOD-FR-69 **Cast** (`series_cast` "Cast") when the metadata has cast: a row of members (not
  focusable): photo over initials, name (2 lines), character (1 line); content description
  `details_cast_member` "%1$s as %2$s".
- VOD-FR-70 **Similar** (`details_similar` "Similar") appears only once the details lookup has
  completed. While resolving: `movie_similar_loading` "Checking your library…"; nothing found:
  `movie_no_similar_available` "None of TMDB’s similar movies are available in your library."
  (both in an 80 dp placeholder, `textDim`); else a row of artwork cards (TMDB title, year, TMDB
  poster else the library poster). OK opens that film's page.
- VOD-FR-71 Similar resolution: take at most 20 of TMDB's similar references; for each, its title
  and alternative titles, cleaned and normalised ([Metadata](41-metadata-enrichment.md)), at least
  2 characters; candidates are active visible films whose normalised provider or replacement title
  equals one of them and whose year equals the reference's (or either is missing); prefer the same
  source as the current film, then the same year; take the first not already used (the current
  film counts as used); stop at 12. Results are cached (24 entries, 5 minutes) per
  `(content key, reference ids, limit)`; a cached answer is re-checked for visibility before use.
- VOD-FR-72 Focus: the primary button on entry. Focusing any button in the action row scrolls the
  page back to the top (one frame after the platform's own bring-into-view, or the header stays
  cut off). The page otherwise scrolls only to keep the focused row visible. There is no
  on-screen Back button.

### 4.11 Series page

- VOD-FR-73 Opened with the active series record (includes the provider backdrop). Episodes come
  from the database, ordered by season then episode, for the active series of an enabled source.
- VOD-FR-74 Loading: true from the first frame. On the database's first answer: no episodes →
  fetch from the provider (VOD-FR-75); episodes → not loading. This happens once per page open;
  later database changes just update the list.
- VOD-FR-75 **Fetch / Refresh episodes** (`series_refresh_episodes`): find the enabled source;
  missing or disabled → error `catalogue_source_disabled` "The Xtream source is no longer enabled".
  M3U with a derivable Xtream account → Xtream `get_series_info` (§7); M3U without → nothing to
  fetch (success, the playlist already named the episodes); Xtream → `get_series_info`. The
  series' stored episodes are replaced in one transaction. The loading state is shown only while
  there are no episodes yet; an error becomes the page's error text (plain-language message).
  **Rebuild:** a second press while a fetch runs is ignored.
- VOD-FR-76 Seasons = distinct season numbers ascending. The selected season starts at 1 and moves
  to the first season when 1 does not exist. The selected episode is the first of the season
  unless the current selection belongs to it. (So the page opens on the lowest season, not on the
  episode in progress — open question 6.)
- VOD-FR-77 Metadata: the series lookup `(SERIES, name, year)` from the memory cache at once, then
  (when enabled) looked up; a missing catalogue poster is repaired. The **selected episode's**
  lookup `(EPISODE, series name, year, S, E)`: cached value at once, else after **350 ms** of
  resting selection a lookup runs (moving on cancels it).
- VOD-FR-78 Title = metadata title, else the provider name (uncleaned). Poster = metadata poster,
  else the provider's; backdrop = metadata backdrop, else the provider's. Synopsis 3 lines (same
  fallbacks as films). Facts: year; `series_season_count` "%1$d season(s)" when seasons exist;
  runtime from the selected episode's metadata, else the episode's duration (seconds rounded up to
  minutes), else the series metadata; score; quality chips from the provider name.
- VOD-FR-79 Cast as one line `series_cast_line` "Cast: %1$s" (names joined ", "), 2 lines at most,
  0.62 of the width (tiles pushed the episodes off screen on the Shield).
- VOD-FR-80 Progress line (VOD-FR-63) for the selected episode.
- VOD-FR-81 Buttons, in order: when an episode is selected — `series_continue_episode` "Continue
  episode" (with a resume position) or `series_watch_episode` "Watch episode" (primary); "Start from
  beginning" (with a position); Mark as watched / unwatched (the episode). When the season has
  episodes — `series_mark_season_watched` "Mark season as watched". Always — "Refresh episodes";
  "Wrong details?". With metadata — "Source: %1$s".
- VOD-FR-82 Seasons row (`series_seasons` "Seasons"): compact buttons `series_season` "Season %1$d",
  Check icon when every episode of the season is finished, `selected` on the current season. OK
  selects the season and then focuses its first episode as soon as that card exists (kept pending
  until a request succeeds). Down from any season button → the first episode of the **current**
  season; Up from any episode card → the current season's button. Both OK and Down scroll the
  first episode into view before requesting focus, even when the previous episode row was scrolled.
- VOD-FR-83 Episodes (`series_episodes` "Episodes"): a row of episode cards, or an 80 dp
  placeholder: `series_loading_episodes` "Loading episodes…" (`textPrimary`) while loading, the
  error (`danger`), else `series_no_episodes` "No episodes found" (`textDim`). An error with
  episodes present appears under the row (`danger`, label).
- VOD-FR-84 Episode card (layout §4): focusing selects the episode; OK plays it from its resume
  position (0 when finished); still = the episode's thumbnail, else (selected card only) the
  episode metadata backdrop, else the series backdrop, else the poster; `series_episode_label`
  "S%1$d E%2$d"; the display title (VOD-FR-85); a 3 dp bar when 0 < fraction < 1; the watched badge
  when finished; a 2 dp rule under the still in `focus` colour when selected; the duration.
- VOD-FR-85 Episode display title: the provider title trimmed; if it contains
  `(?i)S0*<season>\s*E0*<episode>(?!\d)`, the text after the marker trimmed of spaces, `-`, `–`, `—`,
  `:` and `.`; empty or missing → a fallback. Beta 23's fallback is the hard-coded Finnish "Jakso N";
  **rebuild:** a string resource (open question 5). Computed at import and stored, never in
  composition.
- VOD-FR-86 "Loading episodes…" pill (layout §4) pinned to the top-right corner over the page while
  loading, so no scroll can hide it.
- VOD-FR-87 Episode refresh does not run by itself when episodes are stored: new episodes of a
  running series appear after "Refresh episodes" or a catalogue import that drops the series. See
  open question 7.
- VOD-FR-88 Focus: once the first episode exists, the primary button ("Watch episode") takes focus,
  unless the viewer has already picked a season. Beta 23 requests no focus before that (open
  question 8).

- VOD-FR-112 **Rebuild (owner, 28 September 2026):** "Find in Discover" (`details_find_in_discover`) on the
  film and series pages, after the other actions, when Discover is in the build, the profile may use it
  ([Discover](50-discover-addons.md)) and the title's metadata gives an id: IMDb first (TMDB's
  `external_ids`, a film's `imdb_id`, TVmaze's `externals.imdb`), then `tmdb:<id>`. OK opens Trakt's title
  lookup ([Trakt](51-trakt.md) FR-30): the viewer's addons are asked by those ids and the first answer's
  Discover page replaces the lookup, else it says the title is not available. Back returns to this page
  with focus on the button. A record cached before the ids were kept is fetched again once, when its page
  opens.
- VOD-FR-113 **Rebuild:** when TMDB's last aired episode is in a later season than the library has
  seasons (specials not counted), the series page says under its facts "Your sources have %1$d of
  %2$d seasons." (`series_seasons_missing`), or with "Find in Discover may have the rest."
  (`series_seasons_missing_discover`) when VOD-FR-112's button is there. An announced season that has
  not aired does not count. No extra request: the series details call already made asks for it.

### 4.12 Progress, watched state and history

- VOD-FR-89 Positions are written by the players ([Player](30-player.md)) through one call
  `updateProgress(contentKey, position, duration)`: ignored when the position is under 5,000 ms or
  the duration is 0 or less; the position is clamped to the duration; finished per VOD-FR-90;
  a finished row stores position = duration; `lastWatched` = now; films store their film identity
  (work key); the row belongs to the active profile. Episodes store no work key.
- VOD-FR-90 **Watched rule** (`WatchedRule`, decided 7 September 2026): with duration D and
  position P clamped to [0, D]: watched when P ≥ 0.90 × D, or when D ≥ 10 minutes and D − P ≤
  3 minutes; never when D ≤ 0. (A 20-minute episode with 3 minutes left is watched, with 4 it is
  not; a 5-minute short needs 90 %.)
- VOD-FR-91 Reading a film's position: the newer of its own row and the newest row carrying its
  film identity, for the active profile — so a position written on one copy resumes the other, the
  copy played last wins, and different films never share. Trakt's pause or watched mark takes the
  card when it is newer ([Trakt](51-trakt.md)).
- VOD-FR-92 Resume from a progress value: `resumePosition` = 0 when finished, else the position;
  the fraction = position / duration, else Trakt's known fraction, else 0.
- VOD-FR-93 History (films): every film with a progress row for the profile — finished ones
  included — newest first, restricted to active, enabled, visible titles (and allowed groups).
  History (series): every series with any episode progress, ordered by its newest episode.
- VOD-FR-94 Season tick: every episode of the season finished.
- VOD-FR-95 Episode progress on the series page is read for that series only (one query), with
  the Trakt overlay.
- VOD-FR-96 **Mark as watched** sets the title finished at whatever duration is known (0 when never
  played), position = that duration, `lastWatched` = now. **Mark as unwatched** and **Remove from
  Continue watching** delete the row: the title reads as never seen and leaves both Continue
  watching and History (open question 11).
- VOD-FR-97 **Mark season as watched** marks every stored episode of the season watched. Beta 23
  writes them one by one; **rebuild:** one transaction. There is no "unwatch season".
- VOD-FR-98 Removing a profile deletes all of its progress rows ([Profiles](04-profiles-parental.md)).

### 4.13 Feeds for Home and the player

- VOD-FR-99 **Continue watching** (the local part of Home's row, [Home](02-home.md)): films and
  episodes of the active profile with position > 0 and not finished, on active snapshots of enabled
  sources and visible; one row per film identity (else per content key) keeping the most recent;
  newest first; at most **20**. Each item: content key; title = the series name for an episode,
  else the film name, cleaned by the metadata matcher; subtitle = year (films); poster; group key
  = the series key for episodes, else the content key; season, episode and episode title; the TMDB
  id when the film's match exists in the metadata cache. Trakt pauses are merged in (a newer local
  position of the same copy wins, [Trakt](51-trakt.md)); then one card per group key (children
  hopping between episodes do not fill the row with one series).
- VOD-FR-100 **Held-OK actions** on a library Continue watching card (dialog in [Home](02-home.md)):
  `home_resume_continue` "Resume" (resume position), `home_resume_start_over` "Start from
  beginning" (0), `home_resume_mark_watched` "Mark as watched" (VOD-FR-96), `home_resume_remove`
  "Remove from Continue watching" (deletes the row).
- VOD-FR-101 **Next episode**: the next `(season, episode)` after the given episode in the same
  active series, crossing season boundaries; none after the last.
- VOD-FR-102 Other lookups: the playable stream of a film or episode key (decrypted stream URL,
  title, source — [Player](30-player.md), [Security](73-security-privacy.md)); the active film by
  key; the active series; the episode; the series of an episode.
- VOD-FR-103 **Search feed** ([Search](03-search.md)): query trimmed and cut to 80 characters,
  minimum 2 characters; per type at most the requested count (default 40, capped at 100).
  Films and series: case-insensitive substring of the provider name, ordered by name, filtered by
  the profile's allowed groups; subtitle "group · year". Episodes: episode name or series name
  matches, ordered by series name, season, episode; subtitle "<series> · K<s> J<e>" in beta 23
  (hard-coded Finnish; **rebuild:** `series_episode_label`).

### 4.14 Match picker

- VOD-FR-104 Layout §5. Opens from "Wrong details?" with the query = the provider name cleaned by
  the metadata matcher (else the raw name) and **searches at once**; focus goes to the Search button.
  Title `match_picker_title` "Choose the right title"; help `match_picker_help` "Pick the one this
  is. The year and the artwork tell two films of the same name apart."; field `match_picker_query`
  "Search by name" (OK to edit, cut to 80 characters); `match_picker_search` "Search".
- VOD-FR-105 States: `match_picker_searching` "Searching…"; no results `match_picker_no_results`
  "Nothing came back. Try a shorter name, or the original one."; else result rows (thumbnail,
  title, year, 2-line overview). The search runs through the enabled metadata providers
  ([Metadata](41-metadata-enrichment.md)).
- VOD-FR-106 OK on a result pins that match for this title, updates the page's metadata with the
  pinned record, and closes the picker. `match_picker_clear` "Undo my choice" (only when a match is
  pinned) removes the pin and the title's genres, clears the page's metadata, and closes.
  `match_picker_close` "Close" and Back close it. **Rebuild:** focus returns to "Wrong details?"
  before the dialog hides.

### 4.15 Restricted profiles

- VOD-FR-107 A restricted profile has, per room, a set of allowed group keys; an empty set allows
  the whole room ([Profiles](04-profiles-parental.md)). Beta 23 filters: wall entries (every
  destination, History included), the Groups rail, film and series search. Beta 23 does **not**
  filter: genre, Unsorted and group-independent counts; Similar; Versions; episode search; Continue
  watching. **Rebuild:** every read in this spec applies the restriction, inside the query (never
  by loading and filtering in memory), and counts are counted after it (open question 4).

### 4.16 Artwork cache and image loading

- VOD-FR-108 One app-wide image loader, built on first use: disk cache in `cacheDir/catalogue_artwork`
  with the viewer's limit — SMALL 100 MB, MEDIUM 250 MB (default), LARGE 500 MB — read
  synchronously from the preferences file `streammate_artwork_cache`, key `limit` (enum name;
  unknown → default), because a disk cache fixes its size when built; a changed limit applies from
  the next start and the setting says so ([Settings](70-settings.md)).
- VOD-FR-109 Settings shows the cache's use (sum of file sizes under the directory, off the main
  thread, formatted "%.0f MB" from 1 MiB, "%.0f kB" from 1 KiB, else "%d B") and can clear it:
  clearing empties the **disk and the memory** cache, so nothing cleared stays on screen.
- VOD-FR-110 Memory cache: 8 % of the app's memory class. At most **2** images decode in parallel
  (twenty new posters decoding at once saturated the Shield's cores, frame p90 above 100 ms).
- VOD-FR-111 Wall posters are requested at **192 × 288 px**, never cross-faded. Beta 23 uses
  inexact precision (a power-of-two sample can decode up to twice that size) and ARGB_8888 with
  a 140 ms global cross-fade for all other images. **Rebuild:** posters and all opaque artwork
  decoded as RGB_565 at the drawn size (192 × 288 at most for wall posters), no cross-fade anywhere
  on walls, rows or details pages (§9.5).

## 5. Screen anatomy

All measurements: [layout extract](../design/screens/movies-and-series.md). Tokens:
[Design system](../design/01-design-system.md); components (TvListRow, TvActionButton,
TvTagChip, TvUrlField, TvSurface): [Components](../design/02-components.md). Only colours follow
the colour theme; spacing, type and shapes are fixed.

**Wall** (layout §1; screenshots `design/screenshots/beta23-synthetic/movies-wall.png`,
`movies-wall-moved.png`, `series-wall.png`, `series-wall-moved.png`; older
`older-builds/2026-09-02-demo/03-movies.png`, `04-series.png`)
- Regions: screen ground (padding 40/24) → header row (title display 40/44 Black; label and count
  bodyLarge `textDim`; stale/failure note and refresh result, caption) → row of the rail (216 dp,
  8 dp gaps) and the wall (22 dp gap).
- Rail, top to bottom: search field; Groups | Genres toggle (two compact buttons, 6 dp apart,
  `selected` on the active view); Options button; destination list (dense rows with dividers).
- Wall: adaptive grid, 88 dp minimum cell, 22 dp horizontal / 30 dp vertical gaps; 6 columns at
  960 × 540 dp; about 12 posters at rest.
- Focus order: search → toggle → Options → destinations (vertical); rail ↔ wall by Left/Right
  (VOD-FR-50/51). Options sheet above everything (scrim `background` α0.86).

**Film page** (layout §3; older screenshot `03.1-movie-title-info.png`)
- Backdrop layer (full bleed) → scrolling column (32/24 padding): brand + breadcrumb → 34 dp →
  title (0.62 width, 2 lines) → facts → synopsis (4 lines) → progress → action row (12 dp gaps) →
  Versions → Cast → Similar. Section headings: headline Bold, 30 dp above, 14 dp below.
- Focus order: action row (left to right) → Versions row → Similar row (cast is not focusable).

**Series page** (layout §4; older screenshot `04.1-series-title-info.png`)
- As the film page with: 30 dp title gap, 3-line synopsis, cast line, actions, Seasons row (26 dp
  above), Episodes heading (20/12) and row (16 dp gaps); loading pill pinned top-right.
- Focus order: actions → season buttons → episode cards (Down/Up pairs per VOD-FR-82).

**Match picker** (layout §5): dialog 0.72 × 0.86 of the screen.

**Look to keep, cost to drop** (§9): the wall's per-poster gradient under the image, the details
backdrop's four full-screen passes, the default click indication (the old card showed a dark
overlay on focus; the rebuild shows only the ring, as layout §1 records).

## 6. Data

Tables ([Data model](../plan/04-data-model.md) owns the schema; this spec reads them):

| Table | Key | Used here for | Per profile | Backup |
|---|---|---|---|---|
| `vod_movies` | (sourceId, snapshotId, movieId) | films: name, normalised name, group and group key, poster, encrypted stream URL, year, rating, plot, organisation group key | no | no (re-imported) |
| `vod_series` | (sourceId, snapshotId, seriesId) | series: as films plus backdrop | no | no |
| `vod_episodes` | (sourceId, seriesId, episodeId) | season, episode, name, encrypted URL, plot, duration (s), thumbnail | no | no |
| `import_state` / `source_refresh_state` / `iptv_source_state` | sourceId (+kind `catalogue`) | active snapshot, enabled flag, source name and priority, refresh status | no | no (the source settings are, [Backup](71-backup-restore.md)) |
| `catalogue_genres` | (contentKey, genre) | primary genre per title | no | no (re-derived) |
| `catalogue_metadata_overrides` | contentKey | replacement title/poster, replace flag, external id, genres version | no | no |
| `organization_rules`, `organization_aliases` | – | visibility, sorts, positions, `@history`; film identities (alias → identity) | no | yes |
| `playback_progress` | (contentKey, profileId) | position, duration, finished, last watched, content type, item id, work key | **yes** | **no** (open question 12) |
| `metadata_cache` | lookup key + provider | matched records, pinned flag ([Metadata](41-metadata-enrichment.md)) | no | no |
| `trakt_state` | – | overlay ([Trakt](51-trakt.md)) | yes | no |

Existing indexes this spec relies on: `vod_movies (sourceId, snapshotId)`, `(normalizedName)`,
`(categoryKey)`, `(sourceId, movieId)`; `vod_series` the same without the last;
`vod_episodes (sourceId, seriesId, seasonNumber, episodeNumber)`, `(sourceId, episodeId)`;
`playback_progress (sourceId)`, `(contentType)`, `(lastWatchedEpochMillis)`, `(workKey)`;
`catalogue_genres (genre)`; `catalogue_metadata_overrides (externalId)`. Columns and indexes the
rebuild adds for paging are listed in §9.3.

Preferences:
- `preferred_catalogue_copy` (DataStore, device-wide): NONE | FINNISH_AUDIO | FINNISH_SUBTITLES |
  LARGEST_PICTURE, default NONE; unknown → NONE. In backups (`preferredCatalogueCopy`).
- `custom_catalogue_groups` (DataStore, device-wide): JSON array of `{id, name, genres[wire values],
  fromYear?, toYear?, minRating?}`, at most 24; unusable or malformed entries dropped on read. In
  backups (`customCatalogueGroups`).
- `streammate_artwork_cache` / `limit` (SharedPreferences, read before the first frame): SMALL |
  MEDIUM | LARGE, default MEDIUM. Not in backups.
- An old backup field `catalogueBrowserV2Enabled` is ignored on restore and never written.

In-memory caches (bounded):
- Similar results: 24 entries, 5-minute lifetime, LRU.
- Beta 23 copy-derivation cache: 12,000 entries LRU (work keys and claims per content key). The
  rebuild stores these at import (§9.3) and needs no such cache.
- Image memory cache: 8 % of the memory class. Disk: 100/250/500 MB under `cache/catalogue_artwork`.
- Browse sessions: two (one per mode), keys and positions only in the rebuild (VOD-FR-56).

## 7. External interfaces

- **Xtream series info** (episodes; [Sources and import](10-sources-and-import.md) owns the
  client): `GET <base>/player_api.php?username=<u>&password=<p>&action=get_series_info&series_id=<id>`
  with `id` matching `[A-Za-z0-9._-]{1,128}` (else error `error_series_id_invalid`). Used fields:
  `episodes` — an object keyed by season number (a key that is not an integer is skipped) whose
  values are arrays of `{id, episode_num, title, container_extension, info{plot, duration_secs,
  movie_image | cover_big | cover}}`; an episode without `id` or `episode_num` is skipped; the
  extension defaults to `mp4`; the thumbnail is the first non-blank of the three image fields.
  Stream URL template `<base>/series/<u>/<p>/<id>.<ext>` (encrypted before it is stored). Episodes
  are sorted by season, then episode. The body is streamed and capped at 1 GiB. Errors map to the
  localized transport failures of [Sources and import](10-sources-and-import.md).
- **Metadata** (TMDB with the viewer's key, TVmaze): details, cast, similar, search and pinning
  through [Metadata](41-metadata-enrichment.md). Image sizes today: posters `w500`, backdrops and
  episode stills `w780`, cast photos `w185` (`https://image.tmdb.org/t/p/<size>/…`). **Rebuild:**
  ask TMDB for the smallest size that covers the drawn size (for example `w342` for wall posters,
  `w185` for similar cards).
- **Images**: provider poster, backdrop and thumbnail URLs as imported (http allowed only for the
  viewer's IPTV hosts, [Security](73-security-privacy.md)); TMDB over https.
- **Attribution**: the metadata provider's page is opened with the platform's URI handler; nothing
  is sent.
- No HTTP of its own otherwise; walls and pages read only the local database.

## 8. Edge cases and limits

- **200,000 films in one source** (owner's provider): a provider group or genre can hold tens of
  thousands of films; "all groups" holds everything. The rebuild's wall is paged (§9.3); no
  destination is ever read whole.
- **Every group hidden**: the Groups rail shows History only; the wall shows all groups (empty
  when the rules hide every title → "No titles in this group."), header without a label.
- **Imports while browsing**: a new snapshot activates atomically; the wall keeps its last complete
  state through transient empties (VOD-FR-16) and re-reads when the generation changes; the focused
  card keeps focus by key or falls to its standing copy (VOD-FR-57).
- **Background metadata writes while browsing** change titles, posters and genre membership; they
  must never move focus or disable the wall (VOD-FR-18); the rebuild throttles them (§9.3).
- **Source disabled or deleted** while a page is open: plays fail with the player's message; the
  series page's refresh says "The Xtream source is no longer enabled"; opening a card of a vanished
  title does nothing in beta 23 (rebuild: toast, §3).
- **Copies disagreeing on the year** fold only once both are matched to the same TMDB record.
- **Titles with only decoration** (a name made of tags) still get a non-empty identity.
- **Season 0 / specials** appear as "Season 0". Duplicate episode numbers are shown as the provider
  sent them.
- **Episode without a duration**: no runtime fact, no progress line until played.
- **Trakt-only pause without a runtime**: the bar shows, Resume is not offered (position unknown),
  see [Trakt](51-trakt.md) open question.
- **Rating strings** are shown as the provider wrote them ("7.5", "7,5", "8/10").
- **History search** in beta 23 does not trim the text (a trailing space finds nothing).
- **Count formatting**: plain integers without grouping ("History  ·  124", "200000").
- **Process death**: the browse sessions are lost (they live in the composition); Movies opens on
  History again. Progress is in the database.
- **Low memory**: nothing in this feature may hold more than the bounds in §9.10; the image memory
  cache is trimmed by the platform's trim callbacks.
- **Profile switch**: sessions reset (rebuild, VOD-FR-56); progress, History, ticks and Continue
  watching follow the new profile at once.

## 9. Lightweight by design

### 9.1 Budgets (starting values; [Performance](../plan/07-performance.md) refines them)

| Measure | Shield | Low-end box (S905Y4) or its stand-in |
|---|---|---|
| Rail + header + wall chrome after OK on Movies/Series | first frame | first frame |
| First page of cards drawn (any destination, up to 200,000 films) | ≤ 300 ms | ≤ 700 ms |
| Return from a details page, generation unchanged | first frame, focused card | first frame, focused card |
| D-pad move in the wall, held or single | next frame within 1–2 vsyncs, no database read on the main thread | same |
| Search result page after the 250 ms debounce, "all groups" of 200,000 | ≤ 300 ms | ≤ 800 ms, cancellable |
| Java heap held by one wall (entries, keys, state; bitmaps excluded) | ≤ 2 MB | ≤ 2 MB |
| Decoded poster memory | ≤ the memory cache (8 % of memory class) | same |
| Film page: local facts and buttons | first frame | first frame |
| Similar resolution (20 references) | ≤ 300 ms | ≤ 1 s, indexed lookups only |

### 9.2 Where the current app is slow or ran out of memory

1. **Whole destination in memory, no LIMIT.** Every wall query returns the entire partition sorted
   by name; "all groups" returns all 200,000 films; the list is mapped, re-sorted by the
   organisation in Kotlin (with an identity lookup for every id), folded, and kept — also in the
   browse session for the life of the app (layout §7; `CatalogueDao`, `OrganizationRepository.organize`).
2. **CursorWindow refills re-run the sorted query.** A result over one 2 MiB window re-executes the
   whole statement per refill and cannot be cancelled (the guide's roster took 57 s this way);
   every destination larger than a few thousand rows hits it, and every write to an observed table
   restarts it.
3. **Regexes on the main thread during layout.** The watched marks computed each film's work key
   for every film in view on every layout pass: 13 % of the main thread over a browse; film
   browsing 812 → 419 ms after keeping keys per wall (ledger 23 Sept 2026, commit `48a37a3`). Copy
   folding's ICU regexes dominated warm group switching on the Shield (bounded 12,000-entry cache).
   Quality chips, category decoration, episode titles and the film page's title cleaning still run
   regexes in composition.
4. **Main-thread I/O on the details pages.** Every film or episode selection calls "is metadata
   enabled", which reads and Keystore-decrypts the TMDB token on the main thread; the similar and
   versions lookups run the title normaliser (and 200 work keys) on the main thread.
5. **Full-table scans per page open.** Similar runs up to 20 references × several titles of
   `LIKE '%title%'` over the whole film table (each a full scan of 200,000 rows on the disk-I/O
   pool of 4 threads); Versions runs one more; in-destination search is `LIKE '%q%'`.
6. **Counts over the whole catalogue on every write.** The group, genre and Unsorted counts are
   whole-catalogue `COUNT(DISTINCT …)` queries with an alias sub-query per row, re-run whenever
   imports or the metadata worker write.
7. **Organisation-view join order.** Joining `organization_visible_*` from the wrong side scanned the
   catalogue with 16 rule sub-queries per row (Home Continue watching went from < 1 ms to 5.5 s);
   fixed with `CROSS JOIN` pinning and plan tests (`CatalogueHomeQueries.kt`).
8. **Whole-catalogue background jobs OOM** (7 Sept 2026): the film-identity pass and the metadata
   queue rebuild loaded the catalogue whole and killed the process every ~4 minutes on the 192 MB
   heap; now paged 2,000 rows in key order ([Lessons](../plan/08-lessons-learned.md) 1.1). The
   identity pass still costs ~20 regex passes per title (3.1 s for 30,000 films on the emulator,
   a minute or more on the box).
9. **Image memory.** A phone-style memory-cache percentage kept hundreds of posters and pushed the
   Shield's native heap above 300 MB; unbounded parallel decodes saturated the cores
   (`StreamMateApplication`).
10. **Overdraw.** Each poster draws a gradient under an opaque image (2× overdraw); details pages
    draw a full-screen gradient, a screen-size image and two full-screen scrims on every redraw.

### 9.3 Paging the wall (normative)

**Stored keys (computed at import or by the background passes, never while browsing).** Each film
and series row carries (or can reach through an indexed join): `identity` (the film identity of
VOD-FR-25, updated by the identity pass when a match arrives); `titleKey` — the provider title's
collation key (Finnish collator, primary strength) as a BLOB, so SQLite's byte order equals the
collator's order; `yearKey` and `ratingKey` (parsed year and leading rating number, with a
"missing" sentinel that sorts last); `qualityMask` (4K UHD, Dolby Vision, HDR10+, HDR10 bits);
`claimMask` (the eleven language claims) and `pictureRank` (VOD-FR-29); the three preference
scores, or enough to compute them in SQL with a `CASE` on the setting; `searchText` (provider
title and replacement title folded to lower case); `similarKey` (the metadata matcher's normalised
title, indexed, for exact-match Similar and Versions lookups); and the primary `genre` where the
wall needs it for an index-ordered genre slice. Episodes store their display title (VOD-FR-85).
Group, genre and Unsorted counts live in a small counts table (room, destination key, films,
titles), recomputed in the background after an import activation and at most every 30 s during a
metadata pass. The schema and migrations belong to [Data model](../plan/04-data-model.md); these
are its requirements.

**Window pager.**
- Page = 120 entries. At most 5 pages (600 entries) in memory: the page in view and two either
  side. Pages that fall outside are dropped; their slots stay in the grid as empty tiles (not
  focusable) and are re-read when they come back.
- Keyset in both directions over the destination's order tuple ending in the primary key, for
  example `(titleKey, sourceId, itemId)`: next page `WHERE tuple > :last ORDER BY tuple LIMIT 120`,
  previous page `WHERE tuple < :first ORDER BY tuple DESC LIMIT 120`. Never `OFFSET` over a sorted
  query. Every page query is served by an index in that order: no `TEMP B-TREE` in its plan
  (plan test per query shape: provider group, all groups, genre, Unsorted, custom group, History,
  search).
- The grid addresses cards by **absolute index** (the window knows the absolute index of its first
  entry), so the column of a card is `index mod columns` (VOD-FR-50) and prepending a page never
  shifts cards between columns. Real cards are keyed by content key, empty slots by `slot:<index>`.
- Prefetch when the focused row is within 3 rows of either end of the loaded window. A held key
  that outruns the pager stops at the last loaded row until the page lands; it never skips or
  jumps (test with a gated, slow page query and real key repeats).
- Page reads run on the database dispatcher with a `CancellationSignal`; a superseded request
  (new destination, new search, left the screen) is cancelled inside SQLite, not left to finish.
- **Folding per page.** A film row is returned only for its standing copy in the destination:
  `NOT EXISTS (a copy with the same identity in the same destination with a higher score, or an
  equal score and an earlier tuple)` — an indexed lookup per row on `identity`. One follow-up query
  per page, `WHERE identity IN (:the page's identities)` (≤ 120), supplies copy count, quality-mask
  union and the poster/year/rating/genre fill-ins (VOD-FR-27). A folded card therefore sits at its
  standing copy's position, not at its first copy's (open question 3).
- **History** pages by `(lastWatched DESC, contentKey)` starting from `playback_progress` with the
  join order pinned (`CROSS JOIN`) so the organisation views are reached by primary key only.
- **Search** pages through the destination's index in order and filters on `searchText` with
  `instr`, so a common word returns its first page at once and a rare one scans the destination
  once, cancellably. (Unified search, [Search](03-search.md), may use FTS; substring semantics must
  stay the same here.)
- **Restriction and visibility** are part of the page query (allowed group keys through a
  temporary table or an indexed `IN` of at most a few hundred keys); visibility through the
  organisation views reached by primary key, or a stored visibility flag if the plan test shows the
  view walking the table.
- **Invalidation.** The wall observes one cheap signal — the catalogue generation plus a metadata
  version counter the worker bumps at most once every 5 s — and on change re-reads only the pages
  in memory, keeping the focused key; nothing is re-read while a D-pad key is held. An import
  activation re-reads at once (VOD-FR-16 rules apply).
- **Return.** The session stores the destination, the focused card's key, its absolute index and
  tuple, and the scroll offset. With an unchanged generation the pager re-reads the page around the
  tuple and shows it in the first frame at the saved index; after a change it counts the rows before
  the tuple (an index range scan, on the database dispatcher) to re-derive the absolute index while
  the retained window stays visible.
- **Ordering.** A–Z / Z–A use `titleKey` in either direction. Newest, oldest and rating use
  `(yearKey | ratingKey, titleKey, key)` with matching indexes if the plan test needs them; manual
  order reads the few hundred ranked items first (bounded by the number of rules), then the rest
  A–Z. Cross-group ordering: see open question 2.

### 9.4 Off the main thread

Main thread = composition, layout, drawing and input only. Moved off it: every database read and
write (page, counts, progress for the visible range, History, details, similar, versions, episode
lists), settings and Keystore reads ("metadata enabled" is read once and observed, not per page),
every regex and normaliser (all precomputed per §9.3 or computed once per details page on
`Dispatchers.Default`), JSON parsing of `get_series_info`, stream-URL encryption, collation keys.
The film-identity and counts passes run at background priority on their own thread and pause
while video plays ([Metadata](41-metadata-enrichment.md)). Watched ticks read at most 200 keys per
query ([Trakt](51-trakt.md) lesson 8).

### 9.5 Images

- Wall posters: requested at the card's drawn size (192 × 288 px at most; smaller at lower
  densities), RGB_565, no cross-fade, cancelled when the card leaves the composition. At
  960 × 540 dp on a 1080p screen a card is about 177 × 266 px. Visible posters (18) plus two
  prefetched rows (12) at 192 × 288 × 2 bytes ≈ 3.3 MB.
- Similar cards 104 dp (≈ 208 × 312 px), cast avatars 52 dp, episode stills 208 × 117 dp,
  match-picker thumbnails 44 × 62 dp: each decoded at its drawn size, RGB_565 (cast circles too;
  the clip does the rounding).
- Details backdrop: decoded at no more than the screen size, RGB_565 (a 1920 × 1080 ARGB decode is
  8.3 MB, RGB_565 4.1 MB; TMDB `w780` is 0.7 MB), no cross-fade.
- Decode parallelism 2 (VOD-FR-110). Hardware bitmaps vs RGB_565 on Mali-G31: measure (open
  question 9).
- Disk cache as VOD-FR-108; the memory cache is sized from `ActivityManager.memoryClass`.

### 9.6 What a wall draws per frame

- The screen ground is the app's shared, once-painted ground layer ([Design system](../design/01-design-system.md)).
- Per card: one rounded clip with the image (or a solid `surfaceSubtle` fill while it loads or when
  there is none — no gradient under an opaque poster), up to three small chips, the 3 dp ring when
  focused, two single-line texts. No per-card `graphicsLayer`, shadow, scale or animation. Brushes,
  shapes and text styles are hoisted, never allocated per recomposition.
- A focus move recomposes two cards (old and new) and nothing else; the header count and rail do
  not recompose on focus moves.
- Placeholder slots draw one solid rounded rectangle.

### 9.7 Details pages

- Backdrop, ground and scrims live in their own layer, drawn once per image load and theme, so
  scrolling the column and moving focus do not repaint them. The two scrims are combined into one
  pre-rendered low-resolution image per theme (for example 240 × 135, drawn scaled with filtering —
  the gradients are smooth), so the page costs two full-screen passes (image, scrim) instead of
  four; the ground gradient is drawn only when there is no image.
- Similar and episode cards keep their child-layer 1.05 focus scale (similar) and bottom scrims,
  with the scrim a shared pre-rendered drawable; animations obey the global "animations off"
  switch ([Design system](../design/01-design-system.md)).
- Similar and Versions use indexed exact-match lookups (`similarKey`, `identity`) — no `LIKE`
  scans; both run once per page on the database dispatcher, after the page's first frame.
- The page's local data (title, facts, progress, buttons) comes from the record passed in and one
  progress query; metadata and similar arrive later and never move focus.

### 9.8 Series page

- Episodes: one query per series (typically under 500 rows). Progress for that series only. Season
  ticks computed on `Dispatchers.Default` when episodes or progress change, not in composition.
- The selected-episode metadata lookup waits 350 ms of rest and is cancelled by the next selection.

### 9.9 Start-up and deferral

Nothing of this feature runs at app start except the image loader's construction (on first image
request) and reading the artwork limit (one small preferences file). Counts, the identity pass and
metadata work start after the first frame and at background priority. Opening Movies starts four
reads: the destination page, the rail's group list and counts (from the counts table), and the
generation signal; genre counts only when the Genres view is used.

### 9.10 Memory bounds (rebuild)

| Held | Bound |
|---|---|
| Wall window | 600 entries (5 × 120), each a few hundred bytes |
| Rail | the room's groups (a few hundred to ~800 rows) + ≤ 24 custom groups + 23 genre rows |
| Watched marks | ≤ 200 keys |
| Browse sessions | 2 × (keys, tuple, indices) |
| Similar cache | 24 × ≤ 12 small items |
| Episodes | one series (hundreds of rows) |
| Image memory cache | 8 % of memory class |

## 10. Lessons from the current app

1. **The V2 rewrite is the accepted look and behaviour** (docs/VOD_BROWSER_REWRITE_CLOSEOUT.md,
   3 September 2026): database-addressable destinations, a lifecycle-independent store that
   rejects late results and survives transient empties, off-main cancellable folding, retained
   positions. V1's inline A–Z/sort/Unwatched controls were retired on purpose; their strings
   (`catalogue_sort_*`, `catalogue_jump_to`, `catalogue_continue_watching`, …) are unused. Paging was
   left as "measurement-triggered future work" — the owner's 200,000-film catalogue is that
   measurement.
2. **Left from the wall chose Search geometrically** from the top row (closeout); fixed by the
   first-column rule. The remaining intermittent "Left does not reach the rail" report (31 August
   2026, not reproduced, suspected background metadata writes re-laying out the grid) is why the
   rebuild derives the column from the absolute index ([Lessons](../plan/08-lessons-learned.md) 2.4).
   Its test must run with background writes to the observed tables during the key presses.
3. **Grid drift** (commit `b0651d4`): a focus scale on the focusable node itself changed the bounds
   it reported and the grid scrolled 7 px on every Left/Right. Transforms go on a child layer; the
   wall has no scale at all. `CatalogueGridStabilityTest` asserts every non-moving card stays put.
4. **An invisible backdrop** (commit `1c5bdd4`): drawn at 42 % under two multiplying washes, about
   9 % of the image reached the screen for a full decode. Draw the image at full strength; let one
   scrim decide what shows.
5. **Copy folding needs a stable ranking** (CatalogueCollapse): ranking by completeness moved card
   identity (and focus) while titles were matched. Rank only by what is true at load time.
6. **One card per film on Home** (`CatalogueHomeQueries`, commit `12e366a`): group Continue watching
   by work key and by series key.
7. **Watched at nine tenths** (commit `cc27385`): the earlier 95 % rule left nearly finished titles
   in Continue watching for good. The old constants `COMPLETION_FRACTION = 0.95` etc. are still
   declared in `CatalogueRepository` but unused — do not revive them.
8. **Series cast tiles pushed episodes off screen** (commit `9449cff`): one line of names; the
   loading line became a pinned pill.
9. **Rail width** (commit `2a067cc`): 216 dp is the widest rail that keeps six poster columns at the
   default interface size on a 960 dp screen; group names get two lines.
10. **Images** (commits `b2974cb`, `83b7e2f`; `StreamMateApplication`): no memory cache meant
    re-decoding on every scroll-back; a phone-style percentage meant > 300 MB native heap; unbounded
    decode parallelism meant 100 ms frames; a flat 1 GB disk cache was the viewer's disk, not ours.
11. **Streaming import** (commit `8ad18c8`): a 64 MB cap refused a large provider's film list;
    films and series are consumed in 250-row batches as they arrive ([Sources and import](10-sources-and-import.md)).
12. **Main thread** (commit `b2974cb`, ledger 23 Sept 2026): row mapping ran on the collecting
    (main) thread until moved to `Dispatchers.Default`; the wall's work keys ran per layout pass.
    Found in this review and still present in beta 23: Keystore decrypt per details open and per
    episode selection; title normalising for Similar and Versions on the main thread; regexes in
    composition (quality chips per card, category decoration, episode titles).
13. **Whole-library reads** found in this review: Similar's and Versions' `LIKE '%…%'` scans, the
    rail counts' whole-catalogue re-runs, and the per-return re-query of the whole destination
    (VOD-FR-58).
14. **Raw exception text on screen**: a failed wall showed the exception's message; plain-language
    text only ([AGENTS](../../../AGENTS.md) §5.6).
15. **Hard-coded Finnish**: episode fallback "Jakso N" (`xtreamEpisodeDisplayTitle`) and the search
    subtitle "K<s> J<e>" bypass string resources.
16. **Restriction gaps** (VOD-FR-107) and **sessions not reset per profile** (VOD-FR-56).
17. **Keep**: request serials; the 500 ms transient-empty guard; History first; the first-column
    rule; standing-copy mapping on return; cheap cards whose only focus signal is the ring; the watched rule; the
    shared film position; the pinned loading pill; one-line series cast; scoped progress reads
    (≤ 200 keys).

Open questions (for the owner)
1. **Sorting and "Unwatched" on the wall.** The roadmap's M4 scope names "sorting, Unwatched
   filter", but beta 23 has neither on the wall (sorting lives in the library manager; the Unwatched
   filter was retired with V1). Rebuild as beta 23, or bring an Unwatched filter back?
2. **Order of walls that span groups** (genre, custom, Unsorted, all groups, History search): beta 23
   orders them group by group (organisation `orderedItems`). Paging that exactly needs a composite
   key per group sort. Proposal: one order across the destination using the room's default sort.
   Acceptable?
3. **Position of a folded card**: beta 23 places it where its first copy sorts; paged folding places
   it where its standing copy sorts (these differ only when the copies' provider titles sort apart,
   e.g. "FIN | The Matrix" vs "The Matrix"). Acceptable?
4. **Restricted profiles**: should History, Continue watching, Similar and Versions also hide titles
   outside the allowed groups, and should counts count only allowed titles (rebuild default: yes)?
5. **Episode fallback title**: "Jakso N" is Finnish in every language. New string (English
   "Episode %1$d") in all seven languages?
6. **Series page start**: it opens on the lowest season and its first episode even mid-series. Open
   on the season of the newest watched episode instead?
7. **Episode freshness**: stored episodes are never refreshed automatically. Refresh on open when
   older than, say, 24 hours (in the background, list shown meanwhile)?
8. **Series page focus before episodes exist**: beta 23 requests none. Proposal: focus "Refresh
   episodes", then move to "Watch episode" when episodes arrive only if the viewer has not pressed a
   key.
9. **Bitmap config on Mali-G31**: RGB_565 (owner's instruction) vs hardware bitmaps — measure on the
   stand-in and keep the cheaper, or RGB_565 unconditionally?
10. **Up from the wall's top row**: beta 23 leaves it to geometric search (it may land on the rail's
    search field or toggle). Keep, or define (stay put / go to the search field)?
11. **Mark as unwatched** also removes the title from History. Keep, or keep a History entry?
12. **Watched state in backups**: positions and watched marks are not in `.smbak` backups. Keep?
13. **Custom-group minimum rating** compares the provider's rating string, not TMDB's score (the
    field's documentation says "as TMDB scores"). Keep?

## 11. Acceptance tests

Unit (JVM)
- Watched rule: 90 % boundary; 3 minutes left on ≥ 10 minutes (3 yes, 4 no); a 5-minute short needs
  90 %; duration 0 → never (mirror `WatchedRuleTest`).
- Folding: two copies make one card; the card keeps a real copy's key; no-duplicate input returned
  untouched; copies disagreeing on the year fold once matched; fill-in from copies; only duplicate
  groups are ranked; per-copy data found against the film (mirror `CatalogueCollapseTest`).
- Claims and preference: every example in VOD-FR-28/29 (mirror `CatalogueCopyClaimsTest`,
  `CatalogueCopyPreferenceTest`), including "Fin del mundo" and "Apollo 13pm".
- Quality chips, initials, category decoration, episode display title, year-from-title, custom-group
  membership (all conditions, missing year, rating parsing incl. "7,5" and "1e5").
- Store: History as initial destination; first group becomes the initial slice; old wall kept until
  the new one emits; transient empties do not clear rail or wall; a persistent unexplained empty
  cannot clear a stable wall; a new generation confirms a real empty after 500 ms; removed selection
  falls back; view toggle restores each view's last destination; first genre wall does not wait for
  counts; search debounce and cancellation; changing destination cancels a queued search (mirror
  `CatalogueBrowserStoreTest`).
- Pager: keyset forward/backward over ties; window bound 600; absolute indices stable across a
  prepend; column = index mod columns for 6, 7, 8, 9 columns; fold via `NOT EXISTS` equals the
  in-memory fold on a fixture with duplicates across groups; cancellation stops a superseded read.
- Query plans (sqlite-jdbc on the exported schema, as `CatalogueHomeQueryPlanTest`): every page
  shape, counts refresh, History, Continue watching, similar/versions lookups — no `TEMP B-TREE` for
  ordering, organisation views reached by primary key, no scan of a title table for Similar/Versions.

Instrumentation (emulator, AGENTS §8 rules: slow gated reads for timing, Back via
`sendKeyDownUpSync`)
- Left reaches the rail from the first column: on a fresh library; on a library that arrives after
  the screen; after returning from a title; **while a background writer updates metadata and genres
  of the visible titles during the presses**; an off-screen selected group/genre/custom row is
  scrolled into view; empty rail → Options. Left from the second column moves one card (mirror
  `CatalogueReturnFocusTest`, `CatalogueBrowserV2RailFocusTest`).
- Return from details restores the same scrolled card, and the standing copy when the key was folded
  away; return from the library manager focuses Options.
- Grid stability: Left/Right along a row moves no other card (`CatalogueGridStabilityTest`).
- Collapsed wall and copy choice: one poster for two copies with "×2", fill-in, Versions lists both
  with claims, choosing a copy plays that copy (`CatalogueCollapsedWallTest`, `CatalogueCopyChoiceTest`,
  `CatalogueCopyPreferenceWallTest`).
- Genres and custom groups: views never mix; a title only in its primary genre; Unsorted shows
  unenriched titles; an enrichment write moves a title from Unsorted to its genre; a custom group
  sits above the genres and filters; a deleted custom group leaves focus somewhere useful.
- Wall reload stability: a metadata invalidation keeps the last complete wall visible and focus in
  place.
- Film page: Watch/Resume/Start from beginning/Mark as watched round trip (`WatchedMarkTest`);
  progress line; Similar states.
- Series page: seventh season hands focus to its episodes and back (`SeriesSeasonFocusTest`); first
  open fetches episodes; error text; pill visible while scrolled.
- Series episode focus: browse to episode 8, Up to the season buttons, then OK on season 5 → its
  first episode is visible and focused. Down without changing seasons also returns to its first
  episode. Subsequent Right and Up/Down navigation works (`SeriesEpisodeFocusTest`).
- Match picker: query starts from the provider's name; choosing closes and updates; undo only when
  pinned; nothing found says so (`CatalogueMatchPickerTest`).
- Progress repository: persistence, History keeps finished titles, per-profile positions, forget,
  season marking, next episode across seasons, shared film position and last-played copy wins, one
  Continue watching row per film (mirror `CatalogueRepositoryTest`).
- Restricted profile: no title of a disallowed group in any wall, count, Similar, Versions, search or
  Continue watching.
- Profile switch: the other profile's wall and History are never drawn.

Performance (low-end stand-in, [Performance](../plan/07-performance.md); owner-scale fixture with
**200,000 films**, 800 groups including one of 40,000 and "all groups" reachable, real JPEG posters)
- Cold open of Movies on History and on the 40,000-film group: first page within §9.1 budgets;
  Java heap sampled every second stays within §9.1 (no step proportional to the destination size).
- Held Down for 10 s through the 40,000 group: no frame over two vsyncs from database work, heap
  flat, poster decodes ≤ 2 at a time.
- Search "the" and a rare string in "all groups": first page / completion times; superseded
  searches cancelled (verify with a query log).
- Film page with 20 similar references on 200,000 films: similar resolved within budget; main thread
  shows no Keystore or regex slices (Perfetto).
- Details page scroll: render-thread time per frame vs beta 23 (expect about half: two full-screen
  passes instead of four).
- Compare against the beta 23 journey numbers (film browsing 419 ms main-thread CPU after the
  23 Sept fix, `.local/slowbox`), with the fixture raised from 30,000 to 200,000 films.

Manual (owner's Shield, only with the owner's go; never blind key events)
- Browse and play the owner's real catalogue; Left-to-rail over a long session with the metadata
  worker running; the look matches the reference screenshots.

## 12. Reference: current code map

iptv `feature/catalogue/`
- `v2/CatalogueBrowserV2.kt` — the wall screen: header, rail, grid, cards, Options sheet, session,
  focus rules, watched-mark reads, poster URL rule.
- `v2/CatalogueBrowserStore.kt` — lifecycle-free state machine: destinations, serials, transient
  empties, views, search debounce.
- `v2/CatalogueBrowseDataSource.kt` — maps destinations to repository flows; rail snapshot; genre
  facets; History search.
- `v2/CatalogueBrowseDeriver.kt` — off-main folding with the preference and fill-in.
- `v2/CatalogueBrowseModels.kt` — partitions, facets, requests, targets, wall entries.
- `CatalogueCollapse.kt` — films from copies (`catalogueFilms`, `byFilm`).
- `CatalogueCopyClaims.kt`, `CatalogueCopyPreference.kt`, `CatalogueCopyDerivationCache.kt` —
  claims, scoring, bounded cache.
- `CataloguePoster.kt` — poster loader (192 × 288, no cross-fade), watched badge.
- `CatalogueDetailComponents.kt` — breadcrumb, facts, progress, action button, cast, artwork card,
  version card, runtime, resume position, quality chips.
- `CatalogueDetailBackdrop.kt` — backdrop and scrims.
- `MovieDetailsScreen.kt`, `SeriesDetailsScreen.kt` — the two pages.
- `CatalogueMatchPicker.kt` — "Choose the right title".
- `CatalogueGenreLabels.kt`, `CataloguePresentation.kt` (initials, category decoration),
  `CatalogueGrouping.kt`, `CatalogueMode.kt`.

iptv `iptv/repository/`
- `CatalogueRepository.kt` — browse flows, details lookups, similar, versions, progress, watched,
  Continue watching, search, next episode; also `XtreamCatalogueImportService` (import is specs/10).
- `WatchedRule.kt` — the watched thresholds.
- `OrganizationRepository.kt` — organise/restrict/identities (specs/42).
- iptv `iptv/metadata/CatalogueWorkKey.kt` — film identity (specs/41).
- iptv `iptv/xtream/XtreamClient.kt` — `get_series_info`, episode display title.

core
- `core/database/CatalogueDao.kt` — every catalogue query; `CatalogueHomeQueries.kt` — History and
  Continue watching SQL with pinned joins; `GuideEntities.kt` — VOD entities and `catalogueCategoryKey`.
- `core/model/CatalogueGenre.kt`, `CatalogueCustomGroup.kt`, `LibraryOrganization.kt`.
- `core/app/ArtworkCacheSettings.kt`; `core/app/AppPreferencesRepository.kt`
  (`CataloguePreferredCopy`, custom groups).

app
- `app/StreamMateApplication.kt` — image loader (disk cache, 8 % memory, parallelism 2, 140 ms
  cross-fade).
- `app/StreamMateApp.kt` — destinations, sessions, refresh, episode refresh routing, play routing.
- iptv `feature/settings/ArtworkCache.kt` — usage, clear, formatting.
- `feature/home/HomeResumeActions.kt` — held-OK dialog (specs/02).
