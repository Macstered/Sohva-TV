# Live TV guide

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

Layout, sizes, colours, states and timings of every element are extracted from the code in
[design/screens/guide.md](../design/screens/guide.md) (called **guide.md** below). This spec does
not repeat them; it references them by section and adds the behaviour, the data flow and the
rebuild structure. Where the two disagree, guide.md wins for looks and this file for behaviour.

## 1. Summary

The guide is Sohva TV's electronic programme guide: a hero describing the programme under the
cursor, a half-hour ruler, a fixed channel column and a three-hour programme grid with a red
now-line, over the channels of one playlist ("source") and one group at a time. The viewer moves
block by block with the D-pad, pages through time with Left/Right at the edges or the transport
keys, dials channel numbers, finds programmes by title, starts live or catch-up playback, sets
reminders and favourites, and reaches source switching, group and channel management through one
Options sheet. It must stay usable on a 56,000-channel source with 165,000 programmes on a
four-core Cortex-A35 box, which is why it reads channels first and programmes only for the rows
on screen, opens on a group rather than on every channel, and (in the rebuild) draws each channel
row as one canvas with one focusable per row.

## 2. Feature checklist

- GUIDE-01 Guide screen: hero, day label and half-hour ruler, channel column, 3-hour programme
  grid, key-hint bar (anatomy in guide.md §0–§3).
- GUIDE-02 Opens on the group of the channel it was opened for; otherwise on the first group of
  the chosen source; never on All channels by default.
- GUIDE-03 The chosen source is remembered across launches; fallback order: channel opened for,
  saved source, last watched channel's source, first source.
- GUIDE-04 Options > Source cycles through the sources; a switch lands on the new source's first
  group and its first channel; with one source the button does nothing.
- GUIDE-05 Group rail opened with Left from the channel column: Favourites, All channels,
  Recently watched, custom channel lists, provider groups, each with a count where known.
- GUIDE-06 The rail follows the viewer's manual group order when one is set (All channels always
  first).
- GUIDE-07 The Favourites, Recently watched and custom-list entries disappear when switched off
  in the Library manager.
- GUIDE-08 Groups switched off by a rule, or outside a restricted profile's groups, are not on
  the rail and their channels are never shown.
- GUIDE-09 All channels: every visible channel of the source in guide order.
- GUIDE-10 Favourites: the active profile's favourite channels of the selected source, in guide
  order.
- GUIDE-11 Recently watched: the active profile's last 20 channels of the selected source, most
  recent first.
- GUIDE-12 Custom list: the list's channels of the selected source, in the list's own order.
- GUIDE-13 Channel order within a list follows the organisation rules (provider order by
  default; manual or A–Z per group from the Library manager; positions set in Channel
  management).
- GUIDE-14 Channel cell: number, logo (or two-letter initials), name (wraps to two lines when
  long), feed line (quality/language tags from the name, else group, else source).
- GUIDE-15 Channel numbers can be switched off (Settings > General > Channel numbers).
- GUIDE-16 Programme blocks proportional to running time, clipped to the window and to the next
  programme's start; genre accent bar; airing block with progress strip; finished blocks dimmed.
- GUIDE-17 A row without listings in the window shows "No EPG information / Watch channel";
  before its programmes are read it shows a blank disabled bar.
- GUIDE-18 Day label ("Thu 24.9.") with Now / Today / Tomorrow / Yesterday.
- GUIDE-19 One red now-line across the grid and ruler, moved every minute.
- GUIDE-20 Hero: 16:9 still (backdrop, poster, or channel logo), channel and number caption, live
  dot and progress line; title, LIVE chip, time range, first category, year, TMDB rating chip,
  synopsis.
- GUIDE-21 Hero actions: Watch, Favourite, Remind me / Reminder set, Watch from start / Watch
  recording, Find programme / Close search, Source: TMDB (or TVmaze).
- GUIDE-22 Programme metadata (TMDB or TVmaze) looked up 350 ms after the selection settles,
  only when a metadata service is enabled.
- GUIDE-23 Right on the last block of a row pages +90 min; Left on the first block pages −90 min
  unless the window is at now.
- GUIDE-24 Fast forward / Rewind page ±90 min; Next / Previous page ±1 day.
- GUIDE-25 Paging reaches one day back and seven days ahead; a paged window stays where it is
  while the clock moves; the window at now follows the clock.
- GUIDE-26 Held Left/Right pages continuously; focus waits on the channel during a slow read and
  lands on the adjacent programme when it arrives.
- GUIDE-27 OK on a channel plays it live; OK on an airing or past programme plays catch-up from
  its start when the channel supports it, otherwise live; OK on a future programme opens its
  actions.
- GUIDE-28 OK held on any programme opens its actions once.
- GUIDE-29 Programme actions dialog: Watch / Watch the channel now, Watch from start / Watch
  recording, Remind me / Reminder set, Favourite / Add favourite.
- GUIDE-30 Favourite toggles from the hero and the actions dialog (per profile).
- GUIDE-31 Reminders for future programmes (details in [Catch-up and
  reminders](22-catchup-and-reminders.md)).
- GUIDE-32 Number dialling: overlay "Channel 12", up to 4 digits, commits 2 s after the last
  digit, "No channel 12" for 1.5 s; own numbers first, then list positions; focus moves to the
  channel.
- GUIDE-33 Find programme: a search field on the rail filters the rows by channel name or by a
  programme title in the three hours shown.
- GUIDE-34 Options sheet (Menu, or Options on the rail): Source, Sort, Edit (groups), Edit
  (channels), Settings, Back, Close.
- GUIDE-35 Sort and Edit (groups) open the Library manager at the current group and source;
  back from it the guide reopens with the options open on that group.
- GUIDE-36 Edit (channels) opens [Channel management](21-channel-management.md).
- GUIDE-37 Key-hint bar listing only working bindings.
- GUIDE-38 "Reading the guide…" while the first rows are read.
- GUIDE-39 The previous rows stay on screen while another list is read; a reading notice
  appears above them after 400 ms.
- GUIDE-40 Empty-list messages for Favourites, Recently watched and any other list.
- GUIDE-41 Empty-library card with per-source import health, Sync now and Open settings.
- GUIDE-42 Back from live playback started in the guide returns to the guide at now, on the
  watched channel in its group.
- GUIDE-43 Returning to the guide within 10 minutes shows the last rows without reading them
  again when nothing changed.
- GUIDE-44 Up from the top row reaches the hero's buttons.
- GUIDE-45 Programme data arriving never moves focus.
- GUIDE-46 Times in the chosen time zone (Settings > General > Time zone; the TV's zone until
  one is chosen).
- GUIDE-47 A source's EPG offset (±12 h in 30-min steps) shifts all its programme times.
- GUIDE-48 Duplicate and overlapping provider entries are reduced to one block per start time.
- GUIDE-49 A restricted profile sees only its allowed groups; a change applies while the guide
  is open.
- GUIDE-50 An import finishing while the guide is open refreshes rows and programmes in place.
- GUIDE-51 Named trace sections for the screen, grid, rows and hero.
- GUIDE-52 One accessibility node per cell (per row in the rebuild) that states its text.

## 3. Entry points and navigation

### 3.1 How the viewer arrives

| From | Opened for channel | Notes |
|---|---|---|
| Home rail "Live TV" | the channel last played from the guide in this process (session memory `guideFocusChannelId`), else none | |
| Start screen "Programme guide" | none | stack `[Home, Guide]` ([App shell](01-app-shell-navigation.md)) |
| Start screen "Last channel" | the last channel | stack `[Home, Guide, Player]` |
| Back from a live player started with `forGuide = true` (guide, Home channel card, Search, or any player after a channel change) | the channel playing at Back | stack reset to `[Home, Guide]` |
| Player shortcuts "Guide at this channel" / "Guide" | the playing channel / none | stack reset |
| Sohva Sport "Guide" button | none | pushed |
| Back from the Library manager opened from the guide | none; opens with the options sheet up and the managed group selected | see GUIDE-FR-95 |

The guide holds no saveable state between visits: the window, rail, selected list and focus are
rebuilt every time it is shown (the old app keeps `pinnedWindowStart` in saveable state, but the
screen leaves composition while the player is up, so it is lost; keep that result).

### 3.2 What Back does

Back peels one layer at a time, all through the window's Back dispatcher:

1. Options sheet open → close it (focus rule GUIDE-FR-93).
2. Programme actions dialog open → close it.
3. (Old app only: category edit mode → leave it. Unreachable in production; see GUIDE-FR-97.)
4. Otherwise → leave the guide to the previous destination (normally Home).

The rail and the search field have no Back handling: Back with the rail open leaves the guide.
Rebuild: keep this (a rail Back rule would be new behaviour; recorded as an open question).

### 3.3 Where focus lands

- On entry: the channel cell of the focus row (the dialled row, else the channel the guide was
  opened for, else row 0), after scrolling it into view. Rebuild: the row's selection is the
  channel column.
- On return from the player: the watched channel's row, as above.
- After choosing a rail entry: the first row of the new list (or the opened-for channel if it is
  in it); the rail closes.
- After closing the options sheet: GUIDE-FR-93.
- After closing the actions dialog: the programme it was opened from (rebuild fix of guide.md
  §12 item 6; the old app focuses the first visible programme of that row).
- When the list is empty: nothing in the grid; in the empty-library card, Sync now (or Open
  settings when no source is saved).

## 4. Behaviour

### 4.1 The model the screen works with

- GUIDE-FR-01 A **channel row** carries: channel id (`<sourceId>:<localId>`), source id, source
  name, source priority, shown name (custom name, else provider name), shown group title (custom
  group, else provider group), logo (custom logo, else provider logo), shown number (custom
  number, else provider number, else none), provider order, legacy position (channel manager
  `sortOrder`), organisation group key, catch-up type, catch-up source, catch-up days.
- GUIDE-FR-02 A **programme** carries: id, title, subtitle, description, categories (list),
  start and stop in epoch ms **after** the source's EPG offset is added
  (`start + epgOffsetMinutes × 60,000`).
- GUIDE-FR-03 The **selection** is `(channel row, programme or none)`. It drives the hero and the
  white block; it is set by focus, never by data arriving (GUIDE-FR-60).
- GUIDE-FR-04 A programme is **live** when `start ≤ now < stop`; **past** when `stop ≤ now`;
  **future** when `start > now`. Progress = `(now − start) / max(1, stop − start)` clamped to
  0..1, only while live.

### 4.2 Source and the group the guide opens on

- GUIDE-FR-10 Sources are the enabled sources that have at least one active, non-hidden channel,
  ordered by priority (descending) then name, as the rail read returns them (GUIDE-FR-20).
- GUIDE-FR-11 Source choice on entry, first match wins: the source of the channel the guide was
  opened for (ignored on a return from the Library manager); the saved guide source
  (`last_guide_source_id`, global, not per profile); the source of the active profile's last
  watched channel; the first source. A saved id that no longer exists is skipped.
- GUIDE-FR-12 Every change of the selected source is saved to `last_guide_source_id` (trimmed,
  cut to 128 characters; a blank value removes the key).
- GUIDE-FR-13 The opening group, decided once the source is known and before any row is read:
  the shown group of the opened-for channel when that channel is active and in the selected
  source; otherwise the **first group** (GUIDE-FR-14); when the source has no groups, All
  channels.
- GUIDE-FR-14 First group = the group with the lowest manual position (`group:<name>` in the
  rail positions, GUIDE-FR-23) when the viewer has a manual group order; otherwise the first group
  in rail order (the playlist order of each group's first channel). Unplaced groups come after
  placed ones; ties keep rail order.
- GUIDE-FR-15 A selected group that disappears (hidden by a rule, emptied, other source):
  if the guide chose it itself, the first group is chosen again (rules can arrive after the
  rail); if the viewer chose it, the guide falls back to All channels.
- GUIDE-FR-16 A switched source always starts on its first group and its first channel, also
  when switching back to the source of the channel the guide was opened for. The opened-for
  channel only decides within its own source and only before the first switch.
- GUIDE-FR-17 A selected custom list that is deleted, or a Favourites/Recently watched filter
  whose shortcut is switched off, falls back to All channels.
- GUIDE-FR-18 An opened-for channel with no group title opens on All channels (current
  behaviour; costly on a big source — see §8).

### 4.3 The group rail

- GUIDE-FR-20 The rail is built from one aggregate read over the channel table (no programme
  join): per enabled source, per shown group title and organisation group key, the number of
  active channels whose `channel_preferences.hidden` is not set; ordered by source priority
  (descending), source name, then the group's lowest provider order. It is observed for the
  guide's lifetime.
- GUIDE-FR-21 Rail entries for the selected source, in this order when no manual order exists:
  Favourites (if the `@favourites` shortcut is enabled), All channels, Recently watched (if
  `@recent` is enabled), each custom list whose `@list:<id>` shortcut is enabled (lists ordered by
  their `sortOrder`, then name), then the provider groups.
- GUIDE-FR-22 Groups listed: distinct shown group titles of the selected source, excluding rows
  whose group a rule disables, excluding groups a restricted profile may not see, excluding a
  null title (channels without a group appear only under All channels).
- GUIDE-FR-23 Manual order: when the Live room's group sort is Manual, entries sort by their
  manual position (`favourites`, `recent`, `list:<id>` from shortcut rules; `group:<name>` = the
  lowest position among that group's per-source rules); All channels is pinned first; unplaced
  entries keep their relative order after the placed ones.
- GUIDE-FR-24 Counts (trailing text): Favourites = number of favourite ids of the profile (all
  sources; shown only when > 0); All channels = sum of the counts of every rail row of the source
  that the rules and the profile allow, channels without a group included (only when > 0); a
  group = its rail count; lists and Recently watched show none.
- GUIDE-FR-25 Opening: Left on a channel cell (GUIDE-FR-71). The rail takes focus on the selected
  entry, scrolled into view without animation; when no entry is selected, on the Options button.
  The rail's scroll position is kept by the screen while the rail is closed (reopening shows the
  same viewport; old bug fixed in beta 17).
- GUIDE-FR-26 In the rail: Up/Down move between the Options button, the search field (when
  shown) and the entries; OK on an entry selects it (GUIDE-FR-27); Right anywhere in the rail
  closes it and returns focus to the selected row's channel cell.
- GUIDE-FR-27 Selecting an entry sets the list (All channels, Favourites, Recently watched, a
  list, a group), reads its rows (GUIDE-FR-30), and moves focus to the list's first row (or the
  opened-for channel when it is in the new list). Focus arriving in the grid by any route closes
  the rail.
- GUIDE-FR-28 The rail is composed only while open; opening it narrows the timeline (it does not
  overlay it; guide.md §0).

### 4.4 Channel rows: reading, ordering, filtering

- GUIDE-FR-30 Rows are read **without programmes** for every list:
  - a group or All channels: the selected source's active channels in that group (or all), with
    the live visibility rules applied (organisation rules, legacy hidden flag, enabled source,
    active playlist snapshot) and the restricted profile's groups;
  - Favourites, Recently watched, a custom list: the named ids (any source), same visibility,
    read in batches of at most 500 ids (older SQLite's 999-parameter limit);
  then filtered to the selected source.
- GUIDE-FR-31 Paging time, receiving programmes, and moving the selection never re-read or
  reorder the rows.
- GUIDE-FR-32 Order within a group or All channels: the organisation order of the Live room
  (spec [Library organisation](42-library-organization.md)): channels grouped by group title in
  the order the groups first appear (or the group sort), and within a group by the group's sort
  mode — Provider (provider order) by default; Manual (manual position, else the channel
  manager's legacy position) when any channel of the group has a legacy position and no rule
  sets a sort; A–Z / Z–A by a Finnish primary-strength collator. Ties: provider order, then name,
  then id. The input to that ordering is the display order `source priority desc, source name,
  legacy position (nulls last), provider order, shown name, channel id`, names compared by Unicode
  code point.
- GUIDE-FR-33 Favourites: the favourite channels in the order of GUIDE-FR-32.
- GUIDE-FR-34 Recently watched: most recently played first (the profile's recent list, max 20).
- GUIDE-FR-35 Custom list: ordered by the list view's rules (`@list:<id>`): manual position from
  the membership `sortOrder` unless a rule sets another sort; ties as GUIDE-FR-32.
- GUIDE-FR-36 While the rows of a new list are being read, the previous rows stay on screen
  unchanged (not re-filtered) with their programmes; the new rows replace them in one step.
  After 400 ms without the new rows, "Reading the guide…" (`guide_loading`) appears above the
  stale grid (padding 24 dp horizontal, 4 dp vertical) — never in place of the rows.
- GUIDE-FR-37 A write to the channel, source-state, import-state, channel-preference or
  organisation-rule tables re-reads the current list (a burst of writes folds into one re-read
  after the one in progress). Focus stays on the same channel if it is still in the list
  (GUIDE-FR-61).
- GUIDE-FR-38 A read of a source's rows that straddles a playlist activation (snapshot id
  changes between pages) is discarded and read again, up to 3 attempts; the last attempt is
  accepted as is (the activation's own write triggers another read).
- GUIDE-FR-39 Diagnostics: a list read of ≥ 2,000 rows or ≥ 250 ms logs
  `Guide: channel rows: N of a source|group in P pages, T ms` (counts and timings only, never
  names).

### 4.5 The time window and paging

- GUIDE-FR-40 Window length 180 min; anchor = `floor(now / 30 min) × 30 min − 30 min` (epoch
  based); the window at now starts at the anchor. Page = 90 min; day = 24 h.
- GUIDE-FR-41 The window is an absolute start. Moving it: `start + delta` clamped to
  `[anchor − 24 h, anchor + 7 days]`; a move that changes nothing is ignored. When the result
  equals the anchor the window is "at now" and follows the clock again; otherwise it stays pinned
  while the clock runs.
- GUIDE-FR-42 Only one page at a time: while a page is pending, further page requests are
  ignored.
- GUIDE-FR-43 Paging sequence: (1) focus parks on the selected row's channel column; (2) the
  pending direction (+1 forward, −1 back) is recorded; (3) the window moves and the programme
  cache for the old window is dropped; (4) when the selected channel's programmes for the new
  window have arrived — including an empty result — focus goes to the row's first block (forward)
  or last block (back), or to its filler; (5) pending clears. If the focus request fails
  (bounded retry), pending still clears so later pages work. Between (3) and (4) the rows whose
  programmes have not yet arrived show the blank loading bar (GUIDE-FR-57); the owner saw focus
  "briefly highlight the channel name" during a page and accepted it as the deliberate parking
  (ledger, preview 48).
- GUIDE-FR-44 While a page is pending, key events (down, repeat and up) in the pending direction
  are consumed on the parked row; the opposite direction works normally; Left during a forward
  page opens the rail and cancels the pending focus hand-off; moving to another channel cancels
  it too.
- GUIDE-FR-45 After a page the selection keeps its programme when it is still inside the window;
  otherwise it takes the channel's live programme if that is inside the window, otherwise the
  programme covering the window start, otherwise the first programme starting inside the window,
  otherwise none.
- GUIDE-FR-46 Header: the date of the window start (`EEE d.M.` in the time zone of
  GUIDE-FR-110) and a relative label: "Now" (`guide_window_now`) while at now, else "Today" /
  "Tomorrow" / "Yesterday" by calendar day in that zone, empty beyond ±1 day; colour per guide.md
  §2.

### 4.6 Programmes in the grid

- GUIDE-FR-50 Programmes are read only for the rows on or near the screen: the ids from
  `max(0, firstVisible − 30)` snapped down to a multiple of 10, to
  `(firstVisible + visibleCount + 30)` snapped down to a multiple of 10, plus 10, capped at the
  list size (at most about 80 ids for 7 visible rows). Scrolling within a 10-row step does not
  change the request. No request is made before real rows are laid out.
- GUIDE-FR-51 Time range per request: `[windowStart − 30 min, windowStart + 180 min + 30 min)`
  (4 hours). The predicate is on the stored times shifted by the source's EPG offset, written so
  the `(sourceId, xmltvChannelId, start, stop)` index is used.
- GUIDE-FR-52 A programme belongs to a channel when it is in the source's active EPG snapshot and
  its XMLTV channel id equals the channel's manual XMLTV mapping, else its `tvg-id`.
- GUIDE-FR-53 Per channel, programmes with `stop ≤ start` are dropped; programmes sharing a start
  time are reduced to one — the one with the most of (non-blank subtitle, non-blank description,
  number of categories), then the longer, then the smaller id; the result is sorted by start.
- GUIDE-FR-54 Changing the request (rows or time) cancels the previous read; a slow old read can
  never overwrite the current one. Programme reads run only while the screen is at least STARTED.
- GUIDE-FR-55 Programme cache: at most **240** channel schedules for the current window, evicted
  oldest-arrival first; a schedule that arrives equal to the one held keeps the held object, and
  a row whose schedule object did not change keeps its row object (equal-but-new schedules cost
  50–108 ms a frame on the Shield). The cache is emptied when the window, source, list or profile
  restriction changes.
- GUIDE-FR-56 Drawing a row's programmes (geometry per guide.md §2): every programme with
  `stop > windowStart && start < windowEnd`, from `max(start, windowStart)` to
  `min(stop, windowEnd, next programme's start)`; a programme whose clipped span is empty is not
  drawn; no minimum width. Times shown on a block are its real start and stop.
- GUIDE-FR-57 A row with no programme in the window shows the full-width filler: after its
  programmes are read, "No EPG information" (`guide_no_epg`) / "Watch channel"
  (`guide_watch_channel`), focusable, OK plays live, it pages like a block; before they are
  read, a blank, disabled `surfaceSubtle` bar, and Right on the channel cell is consumed (focus
  cannot jump to another row's block).
- GUIDE-FR-58 Genre accent: the first category (lower-cased) containing one of the keywords of
  guide.md §2 in the table's order decides the colour; no match, no bar; never on the selected
  block.

### 4.7 Selection and the hero

- GUIDE-FR-60 Focus on a channel cell selects `(channel, live programme else first loaded
  programme else none)`; focus on a block selects that programme; focus on a filler selects
  `(channel, none)`. Data arriving changes the selection only to keep it valid: when the list
  changes and the selected channel left it, the selection moves to the first row (or the
  opened-for channel when that is in the list) with its live/first programme; focus is not moved
  by this.
- GUIDE-FR-61 Rebuild rule for refreshed programmes: the selected programme is kept by id; if
  its id is gone, the programme covering the old selection's start is selected; the focus stays
  on the same row.
- GUIDE-FR-62 The hero shows the selection (anatomy guide.md §1). Title = programme title, else
  channel name. Facts = time range (`HH.mm–HH.mm`, en dash), first non-blank category, metadata
  year, joined with `  ·  `; LIVE chip when live; "TMDB %1$s" chip (`guide_rating`) when the
  metadata carries a rating. Synopsis = metadata overview, else description, else subtitle, else
  "No programme details are available." (`guide_programme_no_details`). Nothing is invented: a
  missing fact is left out.
- GUIDE-FR-63 Hero still: metadata backdrop, else metadata poster (cropped 16:9), else the
  channel logo (56 dp tile, initials when there is no logo). Caption: live dot when live; channel
  name and "Channel %1$d" (`guide_channel_number`) upper-cased, joined with `  ·  `; the number is
  the shown number, else the list position + 1, and is left out when channel numbers are off.
- GUIDE-FR-64 Metadata: when the programme id of the selection changes, the hero's metadata is
  cleared at once; if a metadata service is enabled (TMDB with a token, or TVmaze), a lookup
  `(type programme, title)` starts 350 ms later and is cancelled by any further change. Lookups
  are served from the metadata cache first (in memory 256 entries; stored positive 30 days for
  TMDB, 24 h for TVmaze; negative 7 days) — rules in [Metadata](41-metadata-enrichment.md). The
  TMDB rating is `vote_average` formatted `%.1f` (US locale), shown only when > 0; TVmaze gives
  no rating.
- GUIDE-FR-65 Hero buttons, left to right, act on the selection at the moment they are pressed:
  - Watch (`action_watch`): play the channel **live** (also on catch-up channels).
  - Favourite (`guide_favourite`, selected) / Add favourite (`guide_add_favourite`): toggle the
    channel in the active profile's favourites.
  - Remind me (`guide_remind`) / Reminder set (`guide_reminder_set`, selected): only when the
    programme starts after now; toggles the reminder ([Catch-up and
    reminders](22-catchup-and-reminders.md)).
  - Watch from start (`guide_watch_from_start`, live) / Watch recording
    (`guide_watch_recording`, past): only when catch-up is possible (CATCH rules in spec 22);
    plays catch-up from the programme's start to its stop.
  - Find programme (`guide_find_programme`) / Close search (`guide_close_search`, selected):
    toggles the search (GUIDE-FR-90).
  - "Source: %1$s" (`metadata_source`) with the provider name: only with metadata; opens the
    provider's page for the title with the system's URI handler (silently nothing when no app
    can open it).
- GUIDE-FR-66 Up from the top row goes to the hero's buttons (natural focus search); Down from
  the hero returns to the selected row (rebuild rule; the old app used the platform's geometric
  search, which lands on the nearest block below).
- GUIDE-FR-67 Rebuild fixes adopted from guide.md §12: the hero area is reserved even before a
  selection exists (item 7); the synopsis is limited to the lines that fit, 2 at fontScale 1
  (item 3).

### 4.8 Keys and focus in the grid

The rebuild implements the row as **one focusable** holding a selected column index: −1 = the
channel column, 0..n−1 = the n drawn blocks (or 0 = the filler). Every rule below is written in
those terms; the old app has one focusable per cell with the same observable result.

- GUIDE-FR-70 Right: from the channel column to block 0 (the leftmost drawn block, which at now
  is often the programme before the live one, as the old grid's geometric focus search chose);
  from block i to i+1; on the last block (or the filler) → page forward (GUIDE-FR-43); at the
  +7-day limit nothing happens and the key is consumed. While the row's programmes are unread,
  Right on the channel column is consumed.
- GUIDE-FR-71 Left: from block i to i−1; from block 0 (or the filler) → page back when the
  window is not at now, otherwise to the channel column; from the channel column → open the rail
  (GUIDE-FR-25).
- GUIDE-FR-72 Up/Down: to the adjacent row. From the channel column to the channel column. From
  a block: among the adjacent row's blocks that overlap the current block's drawn span
  horizontally, the one whose centre is nearest the current block's centre; if none overlaps,
  the block whose centre is nearest; a row without blocks → its filler. The list scrolls to keep
  the focused row visible. (This reproduces the platform's 2D focus search that the old grid
  relied on; confirm against the old app in the M0 spike.)
- GUIDE-FR-73 Up from the first row → hero buttons (GUIDE-FR-66). Down on the last row → nothing.
- GUIDE-FR-74 OK (Center, Enter, NumPad Enter):
  - channel column or filler → play the channel live;
  - a future block → open its actions dialog;
  - an airing or past block → play catch-up from the programme's start to its stop when catch-up
    is possible for it (spec 22), otherwise play live. Consequence kept from the old app: OK on the
    airing programme of a catch-up channel restarts it from the beginning; live is on the
    channel column, the hero's Watch or the dialog's Watch.
- GUIDE-FR-75 OK held on a block (key-down with repeat count ≥ 1): open the actions dialog once;
  further repeats and the release are consumed so no click follows. Not on the channel column or
  the filler.
- GUIDE-FR-76 Container keys while focus is anywhere in the grid (key-down): digits 0–9 and
  numpad 0–9 → dial (GUIDE-FR-80); Fast forward → page +90 min; Rewind → page −90 min (even at
  now); Next → +1 day; Previous → −1 day; Menu → open the options sheet. Other keys (channel
  up/down, play/pause, info) do nothing in the guide.
- GUIDE-FR-77 The key hints show exactly: "◀ ▶" Later / earlier (`guide_hint_time`), "▲ ▼"
  Channel (`guide_hint_channel`), "OK" Watch (`guide_hint_watch`), "⏮ ⏭" Day
  (`guide_hint_day`), "MENU" Options (`guide_hint_options`).
- GUIDE-FR-78 Data arriving (rows, programmes, metadata, counts, clock ticks) never moves focus;
  only key presses and the explicit hand-offs of GUIDE-FR-43/-27/-93 do.

### 4.9 Programme actions dialog

- GUIDE-FR-79 Opened by GUIDE-FR-74 (future) or GUIDE-FR-75. It first sets the selection to that
  programme. Content (anatomy guide.md §6): title (programme title, else channel name, 2 lines),
  subtitle `channel · HH.mm–HH.mm` (time zone of GUIDE-FR-110), then rows:
  - Watch: "Watch" (`action_watch`) when the programme is live or absent, else "Watch the
    channel now" (`guide_actions_watch_channel`); plays live; closes.
  - Catch-up row when possible: "Watch from start" (live) / "Watch recording" (past); closes.
  - Reminder row when the programme starts after now: "Remind me" / "Reminder set"
    (selected); toggles; closes.
  - Favourite row: "Favourite" (selected) / "Add favourite"; toggles in place; stays open.
  First focus: Watch. Back or a tap outside closes. On close, focus returns to the block the
  dialog was opened from.

### 4.10 Number dialling

- GUIDE-FR-80 Digits typed while focus is in the grid append to a buffer of at most 4 digits
  (a fifth digit is dropped). The overlay "Channel %1$s" (`dial_channel`) shows the buffer
  (anatomy guide.md §7).
- GUIDE-FR-81 2,000 ms after the last digit the number is resolved against the **current list**:
  the first row whose shown number equals it; otherwise the row at position `number − 1` when that
  row has no shown number of its own; otherwise not found. Leading zeros are ignored ("007" = 7).
- GUIDE-FR-82 Found: the list scrolls to that row and focus goes to its channel column (it does
  not play). Not found: "No channel %1$d" (`dial_channel_none`) for 1,500 ms. The buffer clears
  either way; a list change clears a pending jump.
- GUIDE-FR-83 The rule "own number first, then position where no own number shows" is shared
  with the player ([Player](30-player.md)); the player resolves against the whole guide order,
  the guide against the list on screen (see open questions).
- GUIDE-FR-84 Rebuild: resolution is an indexed lookup (own number within the selected source's
  current list, then position), never a scan of a whole-source list in memory (§9).

### 4.11 Find programme

- GUIDE-FR-90 The hero's Find programme toggles search mode. When turned on, the rebuild opens
  the rail and puts focus in the search field (fix of guide.md §12 item 4; the old app showed the
  field only inside the closed rail). Turning it off (Close search) clears the query.
- GUIDE-FR-91 The field "Search channels or programmes" (`guide_search_hint`) sits between the
  rail's Options button and its entries; input is cut to 80 characters.
- GUIDE-FR-92 With a non-blank query the current list keeps only rows whose shown name contains
  the query (case-insensitive, Unicode) or that have a programme whose title matches in the
  **visible window** (`windowStart .. windowStart + 180 min`), searched in the selected source
  and group — rows not yet scrolled to included — without reading their schedules. The programme
  match starts 250 ms after the last change, is a SQL `LIKE '%query%'` with `\`, `%` and `_`
  escaped, and returns channel ids only. The match set follows paging (a new window re-runs it)
  and live EPG updates. An empty result shows "No channels in the selected source or group".

### 4.12 The options sheet

- GUIDE-FR-93 Opened by Menu in the grid or the rail's Options button; closes with Back, Close,
  or any action that navigates away. Focus rules: first focus on Source. **Before the sheet
  hides**, focus is placed: on the list's first row when the list changed under the sheet (a
  source switch, a return from the Library manager); otherwise on the rail's Options button when
  the rail is open, else on the selected row. When the owed row is not yet on screen it is
  scrolled into view first and then focused. A list that changes while the sheet is open is
  scrolled to its top under the sheet, so the row is ready.
- GUIDE-FR-94 Buttons (anatomy guide.md §5), all full width:
  1. "Source: %1$s" (`guide_filter_source`) with the selected source's name: cycles to the next
     source (wrapping) — GUIDE-FR-16; with one source nothing happens.
  2. "Sort: Playlist" (`guide_sort` + `guide_sort_playlist`): hides the sheet and opens the
     Library manager for the Live room at the current group and source.
  3. "Edit" (`category_edit`, Check icon): the same as Sort (production behaviour).
  4. "Edit" (`guide_channels`, Channels icon): opens Channel management.
  5. "Settings" (`guide_settings`): opens Settings (behind the parental PIN for a restricted
     profile, [App shell](01-app-shell-navigation.md)).
  6. "Back" (`action_back`): leaves the guide.
  7. "Close" (`guide_close_options`): closes the sheet.
  Buttons 3 and 4 both read "Edit" in English and "Muokkaa" in Finnish (see open questions).
- GUIDE-FR-95 Return from the Library manager: the guide is shown with the sheet already open,
  on the group that was being managed (All channels when All channels was managed), with the
  source chosen by GUIDE-FR-11 minus the opened-for channel; closing the sheet lands on the
  list's first row.
- GUIDE-FR-96 The Sort label always says Playlist; the actual order is whatever the Library
  manager set.
- GUIDE-FR-97 Not carried over: the old screen's own name sort ("Sort: A–Z") and category edit
  mode (hide/show groups with Check/Close icons and "Shown"/"Hidden" trailing text, `hidden_live_categories`)
  are reachable only when the guide is built without a Library manager, which production never
  does. The legacy `hidden_live_categories` preference is migrated into organisation rules
  ([Library organisation](42-library-organization.md)).

### 4.13 Loading, empty and error states

- GUIDE-FR-100 Until the rail is read and either the library is known to be empty or the first
  list's rows are ordered: only "Reading the guide…" (`guide_loading`, `textMuted`, padding 28 dp,
  top-left of the content box). No hero, no grid.
- GUIDE-FR-101 Library empty (the rail read returns nothing): the empty card (guide.md §10):
  title "No channels have been imported yet" (`guide_empty_title`); description
  `guide_empty_sources_description` when enabled sources exist, else `guide_empty_description`;
  per enabled source its name (Bold) and two health lines, Playlist (`health_playlist`) and
  Programme guide (`health_epg`), 13 sp, `danger` when failed:
  - never fetched: "%1$s: not fetched yet" (`health_never`);
  - success: "%1$s: %2$d item(s)" (`health_success` plural);
  - failed: "%1$s: %2$s" (`health_failed_detail`) with the plain-language import error
    ([Sources and import](10-sources-and-import.md)), else "%1$s: error (%2$d)" (`health_failed`,
    consecutive failures);
  - running: "%1$s: updating" (`health_updating`).
  Buttons: "Sync now" (`guide_empty_sync`, Refresh icon, first focus; only with sources) starts a
  background sync of every source; "Open settings" (`guide_open_settings`; first focus when no
  source is saved). With sources, the hint `guide_empty_sync_hint` follows (13 sp).
- GUIDE-FR-102 List empty: in the grid area, centred with padding 24 dp: "Reading the guide…"
  while the list is still being read; "No favourite channels selected yet"
  (`guide_empty_favourites`); "No recently watched channels yet" (`guide_empty_recent`); "No
  channels in the selected source or group" (`guide_empty_filtered`) for anything else,
  including an empty search. The selection becomes none: the old hero disappeared (and the grid
  moved up); the rebuild keeps the hero area reserved and empty (guide.md §12 item 7).
- GUIDE-FR-103 Read failures are not shown in the guide; a failed import shows only through the
  health lines of the empty card and in Settings.

### 4.14 Clock and time zones

- GUIDE-FR-110 Display zone: the time zone chosen in Settings > General (`time_zone`); while none
  is chosen, the TV's zone. Used for the header date and relative day, the ruler labels, the hero
  range, the actions dialog range and (rebuild fix, guide.md §12 item 1) the times written on
  blocks. An invalid zone id falls back to the TV's zone.
- GUIDE-FR-111 Times are `HH.mm` (24-hour, dot separator) in every language; ranges use an en
  dash without spaces.
- GUIDE-FR-112 The clock ticks every 60 s; the rebuild aligns ticks to the minute boundary. A
  tick moves the now-line and progress, reclassifies live/past/future, re-evaluates which buttons
  the hero offers, and moves the window at now across half-hour boundaries.
- GUIDE-FR-113 The window anchor is computed on epoch half hours, so in zones with a :45 offset
  the ruler reads xx.15 / xx.45 (kept; see §8).
- GUIDE-FR-114 Programme times come from XMLTV with their own offsets, converted to epoch at
  import; the source's EPG offset (−12 h..+12 h in 30-min steps, default 0) is added to every
  programme of that source in every guide read, search and reminder.

### 4.15 Rows kept between visits

- GUIDE-FR-120 After the guide is left, the last group/All-channels rows read are kept for
  **10 minutes**. A return within that time to the same source and group gets them without a read
  if nothing was written to the five observed tables since they were read and the source's
  active playlist snapshot is unchanged (asked directly, because an activation replaces every
  row). Any other case reads again. Favourites, Recently watched and lists are not kept.
- GUIDE-FR-121 Rebuild: the observable requirement is that a return within 10 minutes shows the
  rows with no "Reading" state and no full read; how (a kept compact index, or re-reading one
  keyset page) is the implementation's choice within §9's memory bounds.

## 5. Screen anatomy

All regions, sizes, text styles, colours by token and derived geometry are in guide.md §0–§10:
frame and skeleton (§0), hero (§1), grid, header, now-line, row, channel cell, logo and
programme block (§2), key hints (§3), rail (§4), options sheet (§5), actions dialog (§6), dial
overlay (§7), states (§10). Screenshot: `design/screenshots/beta23-synthetic/guide.png` and
`guide-moved.png`.

### 5.1 The row as one canvas (rebuild structure)

Each list item is one fixed-height node (44 dp; pitch 48 dp with the 4 dp gap) that draws the
channel column and the timeline itself and is the only focusable in the row. It reproduces
guide.md §2 exactly:

- Channel column (232 dp): background per state (focused → `textPrimary`; selected row with focus
  on a block → `surfaceFocused`; resting → transparent), rounded `shapes.small`, the only
  animated colour (default colour-animation spec, read only in the draw phase); number, logo
  tile, name and feed line with the colours of guide.md's channel-cell table.
- Gap 6 dp, then the timeline: for each drawn block a rounded rect (`shapes.small`, right inset
  4 dp) filled by state (selected → `textPrimary`; airing → `surface`; else `surfaceSubtle`);
  past and not selected drawn at α0.55 by multiplying the paint colours, not with a layer; the
  genre bar 3 × 32 dp, right corners 2 dp; title (caption size, 15 sp line, Bold, 1 line,
  ellipsis) and time (caption, 14 sp line) at insets start 10/8, end 8, top 4; the progress strip
  3 dp at the foot, `focus`, drawn last.
- Rebuild fix (guide.md §12 item 2): the progress strip of a block clipped at the window's left
  edge ends at the now-line (fraction over the drawn span).
- The now-line (2 dp `danger`, 9 dp head) is one element over the whole grid, not per row.
- Logos: drawn from a bitmap decoded at 30 dp (60 px at xhdpi) inside the tile with 3 dp
  padding; initials (first two characters, upper-cased, Black, caption) when there is no logo
  **or the logo fails** (fix of guide.md §12 item 8).
- Names that do not fit on one line switch, once per name, to 13/15 sp on two lines and drop the
  feed line; measured once per data change, not per frame.

### 5.2 Other fixes adopted

- The options scrim covers the whole screen, safe margins included (guide.md §12 item 5).
- The trace sections `Guide:Screen`, `Guide:Grid`, `Guide:Row`, `Guide:Hero` remain (the canvas
  row replaces `Guide:ChannelCell` / `Guide:ProgrammeCell` with `Guide:RowDraw`).

## 6. Data

Read (never written) by the guide, except the three preference writes below. Tables are defined
in [plan/04-data-model.md](../plan/04-data-model.md).

| Data | Use |
|---|---|
| `iptv_source_state` (enabled, name, priority, `epgOffsetMinutes`) | sources, order, offset |
| `import_state` (active playlist / EPG snapshot per source) | only active snapshots are shown |
| `iptv_channels` (id, tvg-id, name, group, logo, provider number, provider order, catch-up fields, group keys) | rows |
| `channel_preferences` (custom name/group/logo/number, hidden, sortOrder, manual XMLTV id) | shown values, visibility, legacy order |
| `organization_rules` (Live room) | visibility, order, shortcuts, manual rail order |
| `tv_programmes` (id, XMLTV channel, start, stop, title, subtitle, description, categories) | blocks, hero, search |
| `channel_lists`, `channel_list_members` | custom lists (only the selected list's members are observed) |
| `source_refresh_state` | health lines of the empty card |
| `reminders` (ids only) | Remind me / Reminder set state |
| metadata cache | hero enrichment ([Metadata](41-metadata-enrichment.md)) |

Preferences read: `favourite_channel_ids[:profile]`, `recent_channel_ids[:profile]`,
`last_channel_id[:profile]`, `last_guide_source_id`, `show_channel_numbers` (default on),
`time_zone` (absent = TV zone), the active profile's restriction. Written by the guide:
`last_guide_source_id`; favourites (toggle); reminders (through the app, spec 22).

Per profile: favourites, recents, locks and restrictions. Global: the guide source, time zone,
channel numbers, organisation rules. Backups: favourites/recents per profile, the guide source,
time zone and channel-number settings are in the backup ([Backup](71-backup-restore.md));
programmes, the kept rows and caches are not.

In memory: the rail (≤ one row per source and group, about 800), the current list (§9), at most
240 schedules, the selection, the hero's metadata, 256 metadata cache entries (shared).

## 7. External interfaces

The guide makes no network call itself. The hero's metadata goes through the metadata
repository (TMDB `search/multi` and details; TVmaze show search), rules and limits in
[Metadata](41-metadata-enrichment.md). Artwork URLs (logos, TMDB backdrops) are loaded by the
image loader at display size (§9). The "Source: TMDB" button hands `attributionUrl` (an https
link) to the system.

## 8. Edge cases and limits

- **Huge sources.** A 56,164-channel source: never open on All channels by default; All
  channels and an opened-for channel without a group read the whole source (see §9 for how the
  rebuild bounds it).
- **One source.** The Source button does nothing (an old bug cleared the group and left the rows
  on "Reading" until the guide was reopened).
- **No groups.** The guide opens on All channels.
- **Channels without a `tvg-id` and no manual mapping** never get programmes: filler row.
- **Provider corrections.** Two entries with the same start: one kept (GUIDE-FR-53). A later
  entry starting before the previous one ends clips the previous block's drawn end but not its
  real stop (catch-up and hero keep the real times).
- **Very short programmes.** Under about 1.1 min at 1080p nothing is drawn; text disappears
  under about 5.6 min (guide.md §2). Such a block is still a selection stop (rebuild: skip blocks
  narrower than 1 px for key navigation).
- **History limit.** The importer keeps programmes that ended up to 12 h before the import and up
  to 8 days ahead ([Sources and import](10-sources-and-import.md)); paging a full day back shows
  filler rows for the older part.
- **Paging limits.** −24 h and +7 days from the anchor; the key is consumed at the limit.
- **Clock changes.** A manual clock change or zone change takes effect at the next tick; a paged
  window stays pinned to its absolute start. A zone change in Settings applies on return.
- **Unusual zones.** :30 offsets align; :45 offsets show ruler labels at xx.15 / xx.45.
- **Dial limits.** Four digits: numbers 10,000 and above (Xtream `num` values on huge panels,
  or own numbers up to 99,999) cannot be dialled.
- **Restricted profile change** while the guide is open removes rows and groups at once; if the
  selected group vanishes, GUIDE-FR-15 applies.
- **Import while open.** A playlist activation re-reads the list (GUIDE-FR-37/38); an EPG
  activation re-reads the visible programmes (the programme read observes its tables).
- **Rail counts** of Favourites include favourites of other sources; the Favourites list shows
  only the selected source's (kept; see open questions).
- **SQLite `LIKE`** is case-insensitive for ASCII only: "Ä" does not match "ä" in programme
  titles, while channel-name matching is Unicode-aware.
- **Process death** returns to the start screen; nothing of the guide is restored.
- **Low memory.** The kept rows are dropped after 10 minutes (§9); nothing else is retained.

## 9. Lightweight by design

### 9.1 Budget for one D-pad press

`GuideNavigationTraceTest` (600 channels, emulator, debug build) measured beta 22/23 at
**31.7 ms** of main-thread CPU per press between rows and **21.0 ms** along a row (38.9 / 24.8
before the beta 22 round); the Shield spent about 34 ms (composition 13.6, then layout and draw),
spread over 17–29 nodes per row, with two dozen text layouts per press. On the S905Y4 box that is
several frames per press. The remaining cost was structural (ledger, "What a key press costs").

Rebuild requirements, measured with the same kind of trace test on the low-end stand-in
([plan/07-performance.md](../plan/07-performance.md)):

- GUIDE-NFR-01 Main-thread CPU per press **≤ 11 ms between rows and ≤ 7 ms along a row** on the
  emulator trace (one third of beta 23's), and the next frame within the plan/07 key-press budget
  (one or two vsyncs) on the low-end class.
- GUIDE-NFR-02 A press recomposes at most: the hero, and nothing in the grid. The selected
  column index and focus state are snapshot state read **only in the draw phase** of the two
  affected rows; the screen body, the grid and the list's item provider never read the
  selection.
- GUIDE-NFR-03 No text is measured on a press within a row: block labels and channel labels are
  formatted once per data change (programmes arriving, window change, list change, clock tick for
  nothing but progress) and measured through a cached text measurer keyed by
  `(text, style, maxWidth)`, bounded (e.g. 512 entries, LRU). Selection changes only swap paint
  colours; the inset does not change when selected (a 2 dp change used to relayout both texts).
- GUIDE-NFR-04 One draw node per row, no per-block clip or alpha layers, no per-row now-line, no
  shadows. Rounded rectangles and text are drawn directly. Only the channel-column background
  animates.
- GUIDE-NFR-05 The hero is the one place that re-lays out text per press (title, facts,
  synopsis, caption: about 5 texts). Allowed optimisation, to be recorded in `docs/decisions.md`
  if used: while a key is repeating, the hero may coalesce updates to at most one per 100 ms;
  single presses update it in the same frame.
- GUIDE-NFR-06 Semantics: one node per row with the channel number, name and the selected
  block's title and time (the old app's per-cell `clearAndSetSemantics` rule, now per row). A
  button-remapper accessibility service must not add more than 10 % to a press (the old grid lost
  30 % of the main thread to the semantics walk).

### 9.2 Memory bounds and paging

- GUIDE-NFR-10 **No whole-source list in memory.** The old guide held the whole roster of the
  selected list (56,164 `GuideTimelineChannel` objects for All channels, about 45 MB before
  string sharing and still "tens of megabytes" after, kept for 10 minutes). The rebuild keeps for
  the current list only (a) its size and (b) pages of channel rows around the viewport, at most
  **3 pages of 200 rows** resident (the visible rows plus about 250 either side), evicted
  furthest-first.
- GUIDE-NFR-11 Order without sorting in memory: the display order of GUIDE-FR-32 is materialised
  in the database as an integer position per channel and view (the organisation order of the Live
  room, restricted to what is visible), recomputed off the main thread at background priority
  after a playlist activation, an organisation-rule change or a channel edit, in keyset pages of
  ≤ 2,000 channels along the primary key. A group is a contiguous position range (the
  organisation order keeps each group contiguous). Rows are read by keyset
  `(sourceId, position) > (?, ?)` with `LIMIT 200`, join order pinned (`CROSS JOIN` from source
  state and active snapshot), no temporary B-tree (query-plan test). The design lives in
  plan/04; the M0 spike proves it at owner scale.
- GUIDE-NFR-12 Favourites (unbounded set), Recently watched (≤ 20) and custom lists are read by
  id in batches of ≤ 500 and ordered by the same materialised position (lists by membership
  order).
- GUIDE-NFR-13 Programme reads: only the rows of GUIDE-FR-50 and 4 hours; a request is at most
  about 80 channels; the cache holds at most 240 schedules; in a typical feed that is
  1,000–2,500 programmes. The grid read carries no description; the hero reads the selected
  programme's description by primary key (off the main thread, cached for the current window), so
  the programme cache stays under about 1 MB even with 2,000-character descriptions.
- GUIDE-NFR-14 Every read of the list is cancellable between pages and is cancelled when the
  list changes or the screen stops (the old unpaged roster ran 57 s after the viewer had left
  and blocked one of Room's four IO threads).
- GUIDE-NFR-15 Dialling and zapping never materialise a list: a number is resolved with an
  indexed query on the shown number within the list, then by position; the player's channel
  up/down reads a keyset window of ±N neighbours ordered by `(position, channelId)` (OwnTV study
  item 14, [Player](30-player.md)).
- GUIDE-NFR-16 Rail: one aggregate read, ≤ one row per source and group (about 800 at owner
  scale), observed; re-run conflated on writes. If it exceeds 50 ms on the stand-in, materialise
  per-group counts at import (decision for plan/04).
- GUIDE-NFR-17 Strings a source repeats on every channel (source id and name, snapshot id,
  group titles, group keys, catch-up type) are shared, not duplicated per row (about 20 MB of 45
  for 50,000 rows in the old roster).

### 9.3 Threads, timing and caches

- GUIDE-NFR-20 Main thread: layout, draw and key handling only. List ordering and filtering,
  search filtering, stream-tag parsing (`ChannelStreamTags`, regex), dedupe of schedules, label
  formatting and index building run on a background dispatcher and are keyed only on what they
  read (the old pipeline, un-remembered, ran on every press and clock tick).
- GUIDE-NFR-21 Feed-line stream tags are computed when the row is read (or at import) and stored
  on the row, never in composition.
- GUIDE-NFR-22 The metadata-enabled flag is observed once, not read from the secret settings
  store on each selection change (the old guide called `isEnabled()` on the main thread per
  change: a preferences read and a token decrypt).
- GUIDE-NFR-23 Timers: the minute ticker (aligned), the 350 ms metadata delay, the 250 ms search
  debounce, the 400 ms reading notice, the dial's 2,000/1,500 ms. No per-frame timers.
- GUIDE-NFR-24 Images: logos requested and decoded at 30 dp (60 px at xhdpi) and the hero still
  at its drawn size (242 × 136 dp → about 484 × 272 px), whatever size the URL delivers (the old
  app asks TMDB for `w780` backdrops and `w500` posters and lets the loader size them); opaque
  stills decoded RGB_565; at most two decodes at a time (app-wide rule).
- GUIDE-NFR-25 Caches and lifetimes: 240 schedules (current window only); kept list state 10
  minutes after leaving (within GUIDE-NFR-10's bound); metadata 256 in memory; text-measure cache
  bounded (GUIDE-NFR-03); nothing else.
- GUIDE-NFR-26 Start-up: the guide costs nothing until opened, except the rail and source
  reads when it is the start screen. First entry on a cold, compiled process: first frame ≤ 100 ms
  on the Shield (build 51 compiled: 92 ms; uncompiled: 408 ms), so the baseline profile must
  cover the guide (it had no `feature/guide` rules at beta 22).
- GUIDE-NFR-27 Opening the guide on a group of a 56,000-channel source shows rows within 0.5 s on
  the Shield (preview 52: 0.3–0.5 s; build 51 on All channels: about 4 s) and within the plan/07
  budget on the stand-in.

### 9.4 Where the old guide was slow or ran out of memory, and the rule that prevents it

| Old cost | Rule |
|---|---|
| Whole-source sorted roster in one statement: 20 CursorWindow refills, 57 CPU-seconds, result discarded (preview 44) | GUIDE-NFR-10/11/14: keyset pages, no sorter, cancellable |
| Opening on All channels read 56,164 rows (3–4 s) at every entry and every source switch | GUIDE-FR-13/16: open on a group; GUIDE-FR-120 kept rows |
| Equal-but-new schedules recomposed every row, 50–108 ms a frame | GUIDE-FR-55 identity retention |
| Selection read in the screen body recomposed 1,200 lines per press | GUIDE-NFR-02 |
| Channel-cell colour animation read in composition | GUIDE-NFR-04 (draw phase only) |
| Semantics walk with a button remapper: 30 % of the main thread | GUIDE-NFR-06 |
| 17–29 nodes, per-cell clips and alpha layers, ~32 ms a press | §5.1 canvas row, GUIDE-NFR-01..04 |
| Custom-list flows re-created on recomposition: 24 extra reads per 12 presses | subscriptions created once per list selection |
| Organisation ordering allocated 220 MB for 50,000 channels | GUIDE-NFR-11 (ordering materialised in the background, paged) |
| Uncompiled code after an update: first guide frame 408 ms | GUIDE-NFR-26 profiles cover the guide |

## 10. Lessons from the current app

- Open on a group, never on everything by default; keep All channels as an explicit choice
  (ledger "The guide opens on its first group", 19 Sept 2026; `GuideRail.firstGuideGroup`).
- A wide sorted Room read re-executes per 2 MiB CursorWindow and cannot be cancelled; page along
  the primary key with join order pinned, and compare display order with a comparator proven
  equal to SQLite's (code points, id tie-breaker) — `GuideChannelQueries.kt`, ledger
  "Large-playlist guide: cause and fix".
- Data arrival must never move focus; while a page is pending consume only the pending
  direction, keep focus on the channel, and clear the pending flag even when the focus request
  fails (`GuideHeldKeyPagingTest`, ledger "Held-button EPG paging", betas 17/21/22). The held-key
  test only failed on the broken code once the read was gated and real repeated key events were
  sent.
- An overlay that closes must place focus before it hides; otherwise focus falls to the first
  focusable (the hero's Watch). The options sheet fix covered: after a source switch, after the
  Library manager, and without a switch (ledger "Focus after the guide's options", preview 56).
- The one-playlist source button "switched" to the same source and stranded the rows on
  "Reading" (preview 55); a no-op switch must be a no-op.
- Keep the drawer's scroll state outside its conditional composition (beta 17).
- The rows read while the guide is not observing can be stale: count writes through the table
  signal and ask for the active snapshot directly; do not count the flow's first emission as a
  write (the kept-roster test flaked on it, preview 55).
- One import per source and kind at a time; two concurrent imports once left a source with an
  empty active snapshot, so the guide lost a playlist while Settings still showed its count
  (ledger 23 Sept; [Sources and import](10-sources-and-import.md)).
- An accessibility service (button remapper) costs main-thread time through the semantics walk;
  ask testers which services are enabled before chasing jank (ledger preview 45 captures).
- Sideloaded updates run interpreted until profiles are installed: ship `.dm` profiles and cover
  the guide in the baseline profile (ledger "Cold start and first guide entry traced on build 51").
- Two things in the old code that the rebuild corrects: block times use the system zone while
  everything else uses the chosen zone; "Find programme" toggles a field inside a closed rail
  (guide.md §12).
- Anchor test listings to "now": the importer keeps 12 h back and 8 days ahead, so fixtures with
  fixed dates expire (the guide benchmark once did, commit `f65a4a1`).

### Open questions

1. Options has two buttons labelled "Edit" ("Muokkaa") with different icons (groups vs
   channels). Keep, or relabel the second "Edit channels"? (Kept as is until the owner decides.)
2. The Sort button always says "Playlist" although the Library manager may sort A–Z or by hand.
   Should it show the actual group sort?
3. The Favourites rail count counts all sources while the list shows only the selected source's
   favourites. Count per source?
4. Guide dialling resolves against the list on screen; the player resolves against the whole
   guide order across sources. Same channel for the same number in both? (ChannelDial's comment
   claims so; the code does not guarantee it.)
5. Back with the rail or the search open leaves the guide. Should Back close the rail first?
6. OK on the airing programme of a catch-up channel restarts it from the beginning rather than
   joining live. Intended? (Kept; the channel column and Watch play live.)
7. Channel up/down keys do nothing in the guide. Map them to page rows up/down?
8. Resolved 24 September 2026: the time zone follows the TV's zone until one is chosen
   (`AppPreferencesRepository`, `time_zone` absent); design/screens/settings.md was corrected.

## 11. Acceptance tests

Unit (JVM):
- Time window: anchor half an hour before now on a half hour; ±90 min round trip; a paged
  window does not drift when the clock crosses a half hour; the window at now follows the clock;
  −1 day and +7 days limits; `startFor` jumps clamp (old `GuideTimeWindowTest`).
- Windowed reads: no programme request before rows are laid out; 4-hour range; ids = visible ±30
  snapped to 10, clamped at both ends; a scroll within a step requests the same ids (old
  `GuideWindowedReadTest`).
- Programme cache: at most 240; a re-read equal schedule keeps its object; unchanged rows keep
  their object; a new window cannot reuse rows of the old one.
- Schedule dedupe (GUIDE-FR-53) including equal-start corrections and `stop ≤ start`.
- Dial resolution: own number wins, position only where the row has no own number; digits from
  the number row and the keypad only (old `ChannelDialTest`).
- Source choice fallback order (old `GuideSourceSelectionTest`); first group with and without a
  manual order (old `GuideFirstGroupTest`); genre accent keywords (old `GuideGenreAccentTest`).
- Display-order comparator equals SQLite `ORDER BY` including names beyond the BMP (old
  `pagesPutBackInDisplayOrderAreTheOrderedStatementTheyReplaced`).
- Query plans (sqlite-jdbc on the exported schema): the row read never joins programmes; a page
  walks the key without a sorter and costs the same at the start and near the end of 50,000; the
  programme search uses the time index; named-channel reads use no EPG join.

Instrumentation (Android TV emulator, 1920×1080):
- Opens on the first group; no read of All channels happens; All channels can be chosen; Left
  from the grid lands on the selected rail entry; Right closes the rail.
- Source switch lands on the first group's first channel with the rail closed; switching back to
  the opening source does the same; with one source nothing changes and other groups still load.
- Closing the options without a switch returns focus to Options or the channel; after a switch
  or a return from the Library manager, to the list's first channel (fails if focus is on the
  hero's Watch).
- Held Left/Right across four gated (delayed) pages each way: focus waits on the same channel,
  repeats do not start overlapping reads, the destination block takes focus, normal navigation
  works on release (old `GuideHeldKeyPagingTest`; must fail with repeats not consumed).
- Paging waits for the destination programmes before restoring focus, for a small and a
  403-channel source, including empty pages and the Left return at now.
- Programmes arriving late keep focus; favourites show rows while their EPG read is blocked.
- A 3-source, 90,000-programme fixture: reads stay at 4 hours and ≤ 80 channel ids; scrolling far
  down reads only nearby channels; search finds an off-screen programme without loading every
  timeline; a 1,100-id favourites list reads without the EPG join.
- OK on a future programme offers actions; Remind me there calls the reminder toggle with that
  programme; OK held opens actions once without playing.
- Up from the top row focuses the hero's Watch; the day label changes with Next and returns with
  Previous; typing "2" shows the dial and focuses channel 2.
- A restricted profile loses and regains rows live; one semantics node per row states its text.
- Kept rows: a return within 10 minutes reads nothing and shows the same rows; a write while away,
  or an activation Room was not told of, forces a read; rows are released after 10 minutes (old
  `GuideChannelPagingTest`).
- Back is sent through the window (`sendKeyDownUpSync(KEYCODE_BACK)`), never Compose key input.

Performance (low-end stand-in, plan/07):
- Trace test browsing a 600-channel guide: 40 rows down, back up, along a row; GUIDE-NFR-01
  budgets met; compare CPU per phase, not desktop frame times.
- Owner-scale fixture (56,164 channels in 800 groups, 165,600 programmes): open the guide, switch
  source, open All channels, scroll to the end, dial a number: Java heap stays under the plan/07
  steady budget (64 MB) with no CursorWindow warning, and every read stops within one page when
  the list changes.

Manual (device, with the owner's go only): browse the large playlist on the Shield and the
low-end box; check held paging, dial, the options focus hand-off and catch-up OK behaviour.

## 12. Reference: current code map

- `iptv/.../feature/guide/GuideScreen.kt` — screen state, source/group choice, pipelines, effects,
  paging, dial, options sheet, key hints, empty and loading states.
- `iptv/.../feature/guide/GuideGrid.kt` — header, ruler, now-line, rows, channel cell, logo,
  programme cell, key handling at cell level.
- `iptv/.../feature/guide/GuideHero.kt` — hero still and detail with its buttons.
- `iptv/.../feature/guide/GuideRail.kt` — group rail, first-group rule.
- `iptv/.../feature/guide/GuideProgrammeActions.kt` — programme actions dialog.
- `iptv/.../feature/guide/GuideCommon.kt` — constants, live/progress/catch-up predicates,
  formatters, genre accents, programme window ids, retained cache, programme overlay.
- `iptv/.../feature/guide/GuideTimeWindow.kt` — window anchor, paging and clamps.
- `iptv/.../feature/common/ChannelDial.kt` — dial constants, digit keys, number resolution,
  overlay.
- `iptv/.../iptv/repository/GuideStore.kt` (`GuideRepository`) — rail, paged channel reads, kept
  rows, named reads, programme reads, search matches, placement.
- `core/.../database/GuideChannelQueries.kt` — channel fields, paged roster SQL, named-ids SQL,
  programme-match SQL.
- `core/.../database/GuideDao.kt` — rail, timeline and search reads (the rest is import and
  channel management).
- `core/.../database/GuideSearchQueries.kt` — unified search SQL ([Search](03-search.md)).
- `core/.../database/OrganizationViews.kt` — live visibility predicates.
- `core/.../model/LibraryOrganization.kt` — ordering rules.
- Tests: `GuideScreenTest`, `GuideChannelPagingTest`, `GuideHeldKeyPagingTest`,
  `GuideNavigationTraceTest`, `GuideChannelsQueryPlanTest`, and the unit tests named in §11.
