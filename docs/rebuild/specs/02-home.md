# Home

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

Home is the default start screen and the hub the navigation rail lives on. A fixed hero band
at the top describes whichever card has focus (title, episode, time left, synopsis, artwork),
and up to five horizontal rows scroll underneath it on a fixed focus line: **Continue
watching** (library films and episodes, positions paused on Trakt, and Discover history,
newest first, one card per series), **Watch next** and **Recommended for you** (Trakt),
**Today's sport** (Sohva Sport) and **Recently watched channels**. Every card acts with one
OK press; a held OK on a library Continue watching card offers Resume, Start from beginning,
Mark as watched and Remove. Home shows only what is already stored on the device (it starts
no fetch of its own besides the hero's cached metadata lookups), so it must appear fast, stay
stable while data arrives, and never pull focus away from where the viewer is.

The rail itself (items, widths, focus hand-offs) is specified in
[App shell](01-app-shell-navigation.md) §4.8; measurements for everything on this screen are
in [Home and shell layouts](../design/screens/home-and-shell.md) §4. This spec does not repeat
them except where a number defines behaviour.

## 2. Feature checklist

Structure
- HOME-01 A fixed hero band (46 % of the screen height) over rows that scroll beneath it; the
  band never changes height, so rows never shift when the hero text changes.
- HOME-02 Header: the "Sohva TV" brand at top left and a clock (weekday, date, time) at top
  right, in the interface language, the chosen time zone and the device's 12/24-hour setting,
  updated every minute.
- HOME-03 Rows in a fixed order: Continue watching, Watch next, Today's sport, Recommended for
  you, Recently watched channels. A row with nothing to show is not drawn.
- HOME-04 Vertical movement pulls the focused card's row up to the top of the row area (one
  focus line); horizontal movement keeps the TV pivot inside a row.
- HOME-05 Structure lock: while the viewer is below the first row, arriving data updates the
  cards already shown in place but never adds, removes or reorders cards or rows.
- HOME-06 The navigation rail over the left edge (spec 01 §4.8).

Continue watching
- HOME-07 Library films paused locally (not finished, position > 0), newest first.
- HOME-08 Library episodes paused locally; one card per series, the newest episode standing
  for the series.
- HOME-09 Library copies paused on Trakt (unrestricted profiles with Trakt), merged with the
  local positions; a newer local position of the same copy wins.
- HOME-10 Discover titles paused in Discover (not completed, position > 0), one card per
  addon title (series: newest episode).
- HOME-11 The same film from several providers, or from the library and Discover, shows once
  (matched by TMDB/IMDb ids); the copy actually watched locally stands.
- HOME-12 At most 12 cards, newest first.
- HOME-13 Landscape card: artwork (or initials), progress bar, title, "episode label or year ·
  N min left".
- HOME-14 OK on a library card resumes playback at the saved position, with the title's
  library page and details page placed underneath the player.
- HOME-15 OK on a Discover card opens that Discover title page.
- HOME-16 Hold OK on a library card: actions dialog with Resume, Start from beginning, Mark as
  watched, Remove from Continue watching.
- HOME-17 Row hint "Browse the rows with the D-pad" beside the row title.
- HOME-18 Loading card "Loading Continue Watching…" while the first read is under way; Down is
  held back until it resolves.
- HOME-19 Failure card "Continue Watching unavailable. Select to retry." after a read failed or
  took longer than 5 s; OK retries.

Trakt rows
- HOME-20 Watch next: landscape cards for the next episode of up to 8 recently watched shows
  ("S1 E2 · title").
- HOME-21 Recommended for you: poster cards, up to 20 (10 films and 10 shows interleaved), with
  the year.
- HOME-22 OK on a Trakt card opens the library's own details page when a matching copy exists,
  otherwise the Trakt title page.
- HOME-23 "Trakt history is waiting for its first sync" note at the top centre after
  connecting, until the first sync stores its stamp.

Sport and channels
- HOME-24 Today's sport: up to 6 match cards (status or kick-off, crests, score, competition)
  with the day's total "N matches" as the row hint.
- HOME-25 OK on a match card opens Sohva Sport with that match's card open.
- HOME-26 Recently watched channels: up to 6 channel cards (logo, name, current programme,
  programme progress), most recent first.
- HOME-27 OK on a channel card plays it live; Back from the player goes to the guide on that
  channel.

Hero
- HOME-28 The hero describes the focused card once focus has rested for 180 ms.
- HOME-29 With nothing focused, or the rail focused, the hero shows the idle subject at once:
  the newest Continue watching title with progress, else a recent channel, else Welcome.
- HOME-30 Kicker line per subject (Continue watching / Live now with a red dot / Live TV /
  Today's sport / Watch next / Recommended for you / Welcome), a large title, a facts line,
  an optional progress bar and an optional two-line synopsis.
- HOME-31 Synopsis and backdrop come from the same sources as the details pages (metadata
  match first, provider text second); a Trakt title's text comes from TMDB in the metadata
  language, never English first.
- HOME-32 Backdrop artwork at the top right, fading into the ground at its bottom and left
  edge, over the bundled Live TV artwork; a match shows its two crests large and faint instead.
- HOME-33 The backdrop crossfades (250 ms) when the subject's artwork changes; the hero text
  changes without animation.

Empty and first run
- HOME-34 With every row empty, the Welcome hero ("Live TV", "Channels and programme guide")
  and a "Guide" button, which takes focus and opens the programme guide.

Profiles
- HOME-35 Every row is the active profile's own; switching profile resets Home to that
  profile's rows, focus and hero.
- HOME-36 A restricted profile sees no Trakt rows, no Discover cards and no first-sync note;
  its recent channels are limited to its allowed groups.

## 3. Entry points and navigation

### 3.1 How the viewer arrives

- Start screen "Home" (default), or the bottom of the stack for the other start screens
  ([App shell](01-app-shell-navigation.md) §4.2).
- Back from any screen opened from Home.
- The player's Home shortcut resets the stack to `[Home]`.
- After a profile switch (rail Who is watching, Settings, start-time picker) Home rebuilds
  for the new profile.

### 3.2 What each card opens

| Card | OK | Hold OK | Back from what opened |
|---|---|---|---|
| Continue watching, library film | Stack becomes `[Home, Catalogue(MOVIES), MovieDetails, VodPlayer(key, saved position)]` (spec 01 SHELL-FR-28) | Actions dialog (4.7) | Details, then Movies, then Home |
| Continue watching, library episode | `[Home, Catalogue(SERIES), SeriesDetails, VodPlayer(key, saved position)]`; if neither film nor series is found, only the player is pushed | Actions dialog | Series page, then Series, then Home |
| Continue watching, Discover | `DiscoverTitle(progress)` ([Discover](50-discover-addons.md)) | nothing | Home |
| Watch next / Recommended | Library `MovieDetails` / `SeriesDetails` pushed directly on Home when a copy with the same TMDB id exists, else `TraktTitle` (spec 01 SHELL-FR-30, [Trakt](51-trakt.md) TRAKT-FR-30) | nothing | Home |
| Today's sport | `Today` with this match's card open (the card opens once the day's events contain it) | nothing | Home |
| Recently watched channel | Play the channel with `forGuide = true` (group check, PIN gate, recent list update; spec 01 SHELL-FR-20) | nothing | The guide focused on that channel (stack `[Home, Guide]`) |
| Welcome "Guide" button | `Guide` | – | Home |
| Loading card | nothing | – | – |
| Failure card | Retry the Continue watching read | – | – |

Every asynchronous route (resume, Trakt lookup) is applied only if Home is still on top when
the lookup returns (SHELL-FR-17).

### 3.3 Back

- Rows focused: the activity finishes (the app closes to the launcher).
- Rail focused: focus returns to the rows (the card last focused), or to the Welcome button on
  an empty Home.
- Actions dialog open: closes the dialog (focus returns to the Home content).

### 3.4 Focus on entry and return

- Home is disposed when another screen is on top, so every arrival is a fresh entry: the
  first card of the first row once that row exists (the loading card while Continue watching
  loads), or the Welcome button on an empty Home. Home does not remember the card last used.
- Initial focus is placed once per entry and per profile; later data never moves focus.

## 4. Behaviour

### 4.1 Row order and visibility

- HOME-FR-01 Row keys and order: `continue-watching`, `watch-next`, `todays-sport`,
  `recommended`, `recent-channels`. There is no setting for order or visibility in beta 23
  (see open questions). A row is drawn only when it has at least one card, except that
  Continue watching is drawn as a single status card while its first read is loading or has
  failed (4.8).
- HOME-FR-02 Row titles (`headline` Bold, `textPrimary`) and hints (`label`, `textDim`):

  | Row | Title | Hint |
  |---|---|---|
  | Continue watching | `home_continue_watching` "Continue watching" | `home_rows_hint` "Browse the rows with the D-pad" (only when it has cards) |
  | Watch next | `home_watch_next` "Watch next" | – |
  | Today's sport | `home_sports_today` "Today’s sport" | plural `home_sports_count` "%d match" / "%d matches", counting **all** of today's events, not only the 6 shown |
  | Recommended | `home_recommended` "Recommended for you" | – |
  | Recent channels | `home_recent_channels` "Recently watched channels" | – |

### 4.2 Continue watching: the library part

- HOME-FR-10 Local positions. One query over the active profile's `playback_progress` rows
  with `completed = 0` and `positionMillis > 0`, reaching each title only through its full key
  and only when: its source is enabled, the row belongs to the source's active catalogue
  snapshot, and the device's organisation rules show it (`organization_visible_movies` /
  `organization_visible_series`, see [Library organisation](42-library-organization.md)).
  Films give title, year and provider poster; episodes give the episode name as the episode
  title, the series name as the card title, the series' provider poster, season and episode
  numbers, and `series:<sourceId>:<seriesId>` as the series key.
- HOME-FR-11 Film copies collapse before the limit: rows are grouped by the film's work key
  (the identity shared by copies of one film across providers,
  [Metadata](41-metadata-enrichment.md)), falling back to the content key; the most recently
  watched row of the group is kept. Episodes have no work key and stand alone. Result ordered
  by last watched time, newest first, **limit 20**.
- HOME-FR-12 Each kept film gains a TMDB id only when the metadata cache confirms a TMDB film
  record for its matched external id (used for cross-ledger de-duplication, 4.4).
- HOME-FR-13 Display title: the provider title with decoration groups removed (the same
  sanitiser the walls use, [Movies and series](40-movies-and-series.md)); if that leaves
  nothing, the raw name. Film subtitle: the year. Episode label: `series_episode_label`
  "S%1$d E%2$d", followed by " · <episode title>" when there is one.
- HOME-FR-14 Trakt part (profiles with a connected Trakt account): library copies paused on
  Trakt (0 < progress < 100), one per Trakt identity (the lowest content key), newest first, at
  most 20 ([Trakt](51-trakt.md) TRAKT-FR-32, FR-34). A Trakt card's position is the episode's
  playlist runtime × percentage when the playlist gives a runtime; films (the query carries no
  runtime for them) and episodes without one show only the bar fraction, no "min left", and
  resume from 0.
- HOME-FR-15 Merge per content key: a local entry for the same copy wins when its last watched
  time ≥ the Trakt row's time (it then carries the Trakt ids); otherwise the Trakt entry
  replaces it, keeping the local watched time if there was one. The merged list is sorted by
  last watched time, newest first.
- HOME-FR-16 One card per series: entries are de-duplicated by group key (the series key for
  episodes, the content key for films), keeping the first, i.e. the newest.

### 4.3 Continue watching: the Discover part

- HOME-FR-17 Only when the package allows addons, the build is not the demo and the active
  profile is not restricted: the profile's Discover watch history (at most 200 entries,
  [Discover](50-discover-addons.md) ADDON-FR-109), keeping entries that are not completed and
  have a resume position > 0.
- HOME-FR-18 A Discover card's title is the addon title's name (artwork name), else the stored
  title; its subtitle is the stored title when it differs from that name (the episode's own
  title); poster = artwork poster; backdrop = artwork background, else poster; fraction =
  position / duration when the duration is known, else 0; "min left" only with a duration.
- HOME-FR-19 Discover group key: `discover:<metadata installation id>:<media type>:<media id>`,
  so episodes of one series share one card.

### 4.4 Continue watching: one row from three ledgers

- HOME-FR-20 Entries are grouped. Each entry's aliases are its group key (`vod:<group key>` or
  the Discover group key) plus, for films only, `movie:tmdb:<n>` (n > 0) and
  `movie:imdb:<ttNNNNN…>` (`tt` followed by 5–10 digits). A library film takes the ids from
  4.2 and the Trakt merge; a Discover film takes them from its addon media id as Trakt reads
  it (`tt…` or `tmdb:<n>`, [Trakt](51-trakt.md) §4.6). An entry joins every group sharing any
  alias; groups it bridges are merged (Trakt can link a Discover IMDb id to a library TMDB id).
  Titles alone never merge; a film and a series never merge.
- HOME-FR-21 The card that stands for a group: among its films that were watched locally
  (library entries with a local watched time, and every Discover entry), the one watched most
  recently; otherwise the entry with the newest update time. Ties go to the smaller key. A newly
  imported provider copy is therefore a fallback, not a new viewing.
- HOME-FR-22 The groups' cards are sorted by update time, newest first, and the row takes the
  first **12**.
- HOME-FR-23 Card keys: `vod:<content key>` and
  `discover:<metadata installation id>:<media id>:<video id>`.

### 4.5 Continue watching: read state

- HOME-FR-24 The row is a retained, per-process projection (no second history store). It is
  created at process start so its first read runs while the launch screen is up, and it starts
  once the container's one-time initialisation has finished.
- HOME-FR-25 States: `LOADING` (no answer yet), `READY` (at least one card), `EMPTY` (both
  sources answered, nothing to show), `FAILED` (a source threw, or did not answer within
  **5 s**). A late answer after the 5 s timeout still replaces `FAILED` through the same
  subscription; there is no polling.
- HOME-FR-26 The first settled read logs `home: cached resume ready: <ms> ms` to the
  diagnostics log. Waiting for it: Trakt's Home rows and sync loop, Sohva Sport's first
  refresh, and the hero's metadata lookups ("settled" = not `LOADING`).
- HOME-FR-27 A change of active profile, or of whether Discover is allowed, restarts the
  projection at `LOADING` for the new profile; Home keys its focus, list and hero state to the
  pair (profile, Discover allowed), so nothing from the previous profile is shown.
- HOME-FR-28 Retry (failure card OK) restarts both reads.

### 4.6 Other rows

- HOME-FR-30 Watch next: the stored list for the active profile, newest first, all of it (the
  Trakt side keeps at most 8 shows, [Trakt](51-trakt.md) TRAKT-FR-25). Cards: landscape,
  artwork fanart else poster, subtitle `series_episode_label` + " · <episode title>".
- HOME-FR-31 Recommended for you: the stored list, all of it (at most 20, TRAKT-FR-26).
  Cards: poster, artwork poster else fanart, subtitle the year.
- HOME-FR-32 Both Trakt lists are read once per profile from the encrypted store and then
  served from memory; Home subscribes only after the first Continue watching read has settled.
  For a restricted profile both rows are empty whatever is stored.
- HOME-FR-33 Today's sport: the first **6** events of the list the Sohva Sport screen holds for
  today (same order, [Sohva Sport](60-sohva-sport.md)); Home starts no sport fetch of its own
  and polling rules are the shell's (SHELL-FR-73). Card key = event id.
- HOME-FR-34 Recently watched channels: the active profile's `recent_channel_ids` (newest
  first, at most 20, written by play, catch-up, zap and PIN unlock with `forGuide`; spec 01
  SHELL-FR-20..23). Home reads only those channels, with their programme current **at the
  moment Home was entered**, among active channels (enabled source, active playlist snapshot,
  visible by the organisation rules, custom name, group, logo and number applied); a restricted
  profile keeps only channels whose group it may see. Channels that no longer resolve drop out
  silently. The list is ordered by recency position and cut to **6**. Card key = channel id.
- HOME-FR-35 The recent channel query is subscribed once per Home entry, not against the
  ticking clock (re-subscribing every minute made the player and guide churn). The card's
  progress bar and the hero's "live" state are computed against the minute clock (4.10), so a
  programme that ends while Home stays open shows a full bar until Home is re-entered.

### 4.7 Actions on a Continue watching card (hold OK)

- HOME-FR-40 A long press (the first key repeat of OK; the release is swallowed) on a
  **library** card opens a dialog; Discover cards have no long press. Title: the card's title
  (18 sp Bold, 2 lines). Subtitle: the episode label (`series_episode_label` + " · episode
  title") or the card subtitle (13 sp `textMuted`, 1 line). Four dense list rows, the first
  focused on open:

  | Row | String | Icon | Effect |
  |---|---|---|---|
  | Resume | `home_resume_continue` "Resume" | Play | Same as OK on the card (resume route with the saved position) |
  | Start from beginning | `home_resume_start_over` "Start from beginning" | Replay | Resume route at position 0 |
  | Mark as watched | `home_resume_mark_watched` "Mark as watched" | Check | Writes the copy's progress as completed (position = duration when known, else 0; last watched = now); the card leaves the row |
  | Remove | `home_resume_remove` "Remove from Continue watching" | Delete | Deletes this copy's progress row for the profile (not marked watched); the card leaves the row |

- HOME-FR-41 Back or dismiss closes the dialog without action. The dialog closes before its
  action runs. Rebuild: move focus back to the card (or its successor in the row) before the
  dialog is removed ([lessons](../plan/08-lessons-learned.md) 4.1).
- HOME-FR-42 Neither action talks to Trakt. Consequence in beta 23 (read from the code, not
  observed): a card that exists only because Trakt has a paused position comes back after
  Remove or Mark as watched, because the Trakt row is still there; removing one copy of a film
  can surface an older paused copy of the same film. See open questions.

### 4.8 Loading, failure, empty and first sync

- HOME-FR-45 While Continue watching is `LOADING` or `FAILED` and has no cards (and the
  structure is not locked), its row shows one status card (test tag `home-resume-status`) with
  `home_resume_loading` "Loading Continue Watching…" or `home_resume_unavailable` "Continue
  Watching unavailable. Select to retry.", padding 24 dp. While loading, Down is consumed on
  that card so focus cannot drop to a row that will then move.
- HOME-FR-46 Home is empty when no status card is shown and every row is empty. Then the hero
  is Welcome and shows the "Guide" button (`home_action_guide` "Guide", guide icon, test tag
  `home-hero-primary`), which takes initial focus and opens `Guide`.
- HOME-FR-47 Hand-offs when the screen changes shape under focus:
  - the focused loading card disappears → focus goes to the first card, or to the Welcome
    button when Home turned out empty;
  - the focused Welcome button disappears because a row arrived → focus goes to the first card;
  - a platform focus re-entry (window focus change, removal of the focused cached card) that
    enters Home from any direction other than "Enter" is cancelled and re-targeted, after
    layout, to the rows (or the Welcome button), never to the rail at the upper left;
  - pressing Left anywhere in the content cancels every pending hand-off, so a rail the viewer
    opened on purpose stays open when data arrives.
- HOME-FR-48 First-sync note: while the active profile has a Trakt account and no activity
  stamp yet, and Continue watching has settled, `home_trakt_first_sync` "Trakt history is
  waiting for its first sync" is shown at the top centre of the hero band (`body` style, 8 dp
  from the top), never for a restricted profile.

### 4.9 Structure lock

- HOME-FR-50 The structure is locked while the row list is scrolled (first visible row index
  > 0) or while the last focused card is in a row other than the current first row. The
  focused row is remembered until another card takes focus (visiting the rail does not clear
  it).
- HOME-FR-51 While locked, each row keeps the cards it had, in the same order: a card whose key
  is in the new data takes the new values (progress, subtitle, artwork); a card missing from
  the new data stays with its old values; new cards and new rows do not appear; the status
  card is not shown. When the lock releases, the latest data is shown as is.
- HOME-FR-52 Unlocked (the viewer on the first row, list at the top), rows follow the data
  immediately, including reordering Continue watching after a playback.

### 4.10 Clock and minute ticker

- HOME-FR-55 One ticker, first tick 60 s after Home is entered, then every 60 s, updates
  "now". It drives the clock, the channel progress bars, the hero's channel progress and the
  idle hero's "live" state; nothing re-queries the database on it. Rebuild: align the first
  tick to the next minute boundary so the clock changes on the minute.
- HOME-FR-56 Clock text: the platform's best pattern for skeleton `EEEdMMM` in the interface
  locale, then the literal " · ", then the best pattern for `Hm` (24-hour) or `hmma` (12-hour)
  per the device's 24-hour setting; formatted in the chosen time zone (preference
  `time_zone`, else the device zone; an invalid id falls back to the device zone). Example
  (English, 12-hour): "Wed, Sep 23 · 1:08 PM". Test tag `home-clock`.

### 4.11 The hero

- HOME-FR-60 Subjects: Resume (a Continue watching entry), Channel (a recent channel, with a
  live flag), Sport (a match), Trakt (a title, "next" or "recommended"), Welcome.
- HOME-FR-61 Idle subject (nothing focused in the rows, or the rail focused): the first
  Continue watching card with fraction > 0; else the first recent channel that has a current
  programme, else the first recent channel (live = its programme spans now); else Welcome.
- HOME-FR-62 Focus → hero: every card reports its subject when it gains focus. A focused
  subject replaces the hero only after it has rested **180 ms** (a newer focus restarts the
  wait); returning to "nothing focused" or focusing the rail applies the idle subject
  immediately. The loading card and the Welcome button report no subject. Home recomposes only
  when the hero changes, never on each D-pad press (4.13).
- HOME-FR-63 Per subject:

  | Subject | Kicker (uppercased, `focus`) | Title | Facts line (joined by "  ·  ") | Progress bar | Synopsis |
  |---|---|---|---|---|---|
  | Resume | `home_hero_resume` "Continue watching" | card title | episode label or subtitle (year / Discover subtitle); `home_minutes_left` "%d min left" (floor of remaining minutes, at least 1; only when duration known) | when fraction > 0 | 4.12 |
  | Channel, live | red dot + `home_hero_live` "Live now" | programme title, else channel name | channel name; programme subtitle; programme window "HH.mm–HH.mm" (device zone) | programme progress at "now" | none |
  | Channel, not live | `home_live_tv` "Live TV" | as above | as above | programme progress when known | none |
  | Sport, live | red dot + "Live now" | "home – away" (en dash with spaces) | competition; status label, else kick-off label; score | none | none |
  | Sport, other | `home_sports_today` "Today’s sport" | as above | as above | none | none |
  | Trakt next | `home_watch_next` "Watch next" | title | episode label; year | none | 4.12 |
  | Trakt recommended | `home_recommended` "Recommended for you" | title | episode label; year | none | 4.12 |
  | Welcome | `home_hero_welcome` "Welcome" | `home_live_tv` "Live TV" | – | none | `home_live_tv_description` "Channels and programme guide" |

  Blank facts are omitted; with no facts the line is not drawn. Title 2 lines, facts 1 line,
  synopsis 2 lines, all ellipsised. Test tags `home-hero-title`, `home-hero-progress`.
- HOME-FR-64 The live dot is shown for a live channel and for a match whose status is LIVE.

### 4.12 Hero synopsis and backdrop

- HOME-FR-65 Details are looked up per subject key (Resume: card key; Channel: channel id +
  current programme title; Trakt: card key; Sport and Welcome: none), only after Continue
  watching has settled. A new subject clears the previous synopsis at once and starts its own
  lookup; a lookup for a subject that has moved on is cancelled; any failure means "no
  details".
- HOME-FR-66 Library film: metadata match for (film, name, year) when metadata is enabled →
  synopsis = match overview, else the provider plot; backdrop = match backdrop only.
- HOME-FR-67 Library episode: synopsis = the provider's episode plot, else the episode
  metadata match's overview (series name, year, season, episode), else the series match's
  overview, else the provider's series plot; backdrop = the series match's backdrop, else the
  provider's series backdrop.
- HOME-FR-68 Discover entry: synopsis from the addon's **cached** details only (the episode's
  overview, else the title description; no network); no backdrop lookup.
- HOME-FR-69 Trakt title: when it has a TMDB id and TMDB is enabled, the TMDB record by id in
  the metadata language (cached per id, type and language); synopsis = TMDB overview, else
  Trakt's overview; backdrop = TMDB backdrop, else fanart, else poster. Without a TMDB id or
  with TMDB off: Trakt's overview and fanart/poster. The hero waits for the TMDB answer rather
  than showing Trakt's English first ([Trakt](51-trakt.md) TRAKT-FR-29).
- HOME-FR-70 Channel: the current programme is read for the channel; a metadata match for the
  programme title (type programme) gives the backdrop (match backdrop, else match poster). The
  synopsis is looked up but not shown (the channel hero has no synopsis line).
- HOME-FR-71 Metadata lookups use the metadata cache first and may search the enabled
  providers on a miss, the same lookups the details pages make
  ([Metadata](41-metadata-enrichment.md)); no other network request is made by Home.
- HOME-FR-72 The picture shown: the looked-up backdrop, else (Resume only) the card's own
  backdrop (library: the provider poster; Discover: background, else poster), else nothing but
  the bundled floor. A Sport subject draws a `background` α 0.6 veil over the floor and the two
  team crests (150 dp, α 0.55, 56 dp apart, 48 dp side padding, Fit; blank crest = empty box).
- HOME-FR-73 A change of picture crossfades over **250 ms**; while a new picture downloads,
  the bundled floor shows through. Image requests for the hero decode at most 1920×1080 px in
  `RGB_565` (rebuild: art-box size, 9.2).

### 4.13 Focus and keys

- HOME-FR-80 Focus treatment: artwork cards (Continue watching, Watch next, Recommended) keep
  their fill and draw a 3 dp `textPrimary` ring with a 14 dp shadow; sport and channel cards
  fill `textPrimary` and invert their content. No card scales (focus scale 1.0).
- HOME-FR-81 Down/Up between rows: the focused card's row is scrolled so the card's top edge
  sits at the top of the row area (rows travel under the hero; the focused row's own title
  scrolls out of view above). The row list has 12 dp top and 260 dp bottom slack so the last
  row can reach the focus line.
- HOME-FR-82 Left/Right inside a row: the platform TV pivot (focused card at about 30 % of the
  row); 4 dp slack at both ends so the first and last card's ring and shadow are not clipped.
- HOME-FR-83 Left from the first card of a row enters the rail; Right from any rail item, or
  Back while the rail has focus, returns to the card last focused in the rows (focus
  restorer), or to the Welcome button when Home is empty.
- HOME-FR-84 Up from the first row is not handled by Home in beta 23 (the hero holds no
  focusable except the Welcome button on an empty Home, so the platform's focus search decides).
  Rebuild rule: Up on the first row does nothing; the rail is reached only with Left.
- HOME-FR-85 OK acts on the focused card (3.2). Home handles no Menu, number, channel or media
  key.

## 5. Screen anatomy

Reference screenshot: `design/screenshots/beta23-synthetic/home.png` (idle hero on a film,
Continue watching focused, recent channels below) and `home-row-moved.png`. All sizes at
interface size Normal (960×540 dp); full measurements in
[Home and shell layouts](../design/screens/home-and-shell.md) §4.

- **Ground**: the shared screen background (spec 01 §5.5) with no content padding.
- **Hero backdrop** (full screen, behind everything): art box 66 % × 56 % of the screen at the
  top right (633.6×302.4 dp), the bundled `home_backdrop_live_tv.webp` (1920×1080) as its floor,
  the picture (Crop) above it, faded out by two erasing masks (vertical: clear from 45 % to
  fully erased at 100 %; horizontal: fully erased at 0 % to clear at 40 %). Over the whole
  screen: a horizontal scrim of `background` at α 0.85 (0) → 0.55 (0.4) → 0.05 (0.7) → 0.25 (1)
  and a vertical scrim α 0.5 (0) → 0 (0.3).
- **Content column**: start padding 96 dp (rail 80 + 16), end 24 dp; 840 dp wide.
  - Hero band: 46 % of the height (248.4 dp), padding top 16, bottom 12.
    - Header row at the top: brand `SohvaTvBrand` at `headline` size (22 sp; "Sohva"
      `textPrimary`, "TV" `focus`), test tag `home-brand`; clock `body` in `textMuted`.
    - First-sync note, top centre.
    - Hero panel at the bottom start, 56 % of the band width (470.4 dp): kicker row (live dot
      8 dp `danger` + 8 dp gap; `overline` Bold `focus`), 12 dp, title (`display` 40/44 Black,
      −0.5), 8 dp, facts (`bodyLarge`, `textMuted`), 12 dp, progress track 210×4 dp
      `surfaceRaised` with `focus` fill, 12 dp, synopsis (`bodyLarge`, `textMuted`), and on an
      empty Home 24 dp then the Guide button (46 dp tall, shape medium, rest `surfaceRaised`,
      content `textPrimary`, 22 dp horizontal padding, icon 20 dp + 10 dp + `body` Bold label,
      focus scale 1.0).
  - Row list below: rows spaced 26 dp; row header (title + 12 dp + hint, bottom-aligned),
    12 dp, then the card row with 12 dp gaps.
- **Cards** (shape medium, focus scale 1.0):

  | Card | Size | Anatomy |
  |---|---|---|
  | Continue watching, Watch next | 186 dp wide, 4 dp padding; art 178×102 | Art box `surfaceSubtle` with initials (`headline` size Black, `textMuted`) under the image (Crop); progress bar at the art's foot (track `background` α 0.62) when fraction > 0; 8 dp; title `label` SemiBold 1 line; subtitle `caption` `textDim` 1 line, always laid out (transparent when empty) so the row keeps one baseline |
  | Recommended | 124 dp wide; art 116×178 | Same, poster art |
  | Today's sport | 244×160 dp, padding 12, rest `surfaceSubtle` | Status row (live dot 8 dp + 6 dp; `caption` Bold; `danger` when live, else secondary); teams row: two columns each with a 38 dp crest tile (`content` α 0.10, logo Fit with 3 dp padding, else initials) + 4 dp + name `caption` Bold centred 2 lines, score between them (`bodyLarge` Black, `focus` when live, "–" when none, 8 dp side padding); competition `caption` secondary |
  | Recent channel | 168×104 dp, padding 12, rest `surfaceSubtle` | Logo tile 34 dp (`content` α 0.10, logo Fit 3 dp padding, else initials `caption` Black); name `label` Bold; programme `caption` secondary; 6 dp; progress bar (track `content` α 0.20) |

  Progress bars: 3 dp tall, clip small, `focus` fill (stays cyan on a focused white card).
- **Initials** stand in for missing artwork: split the trimmed title on space . - : _ · /, keep
  words starting with a letter; none → empty; one word → its first two letters uppercased;
  more → first letters of the first two words ("Ben 10" → "BE", not "B1").
- **Status card**: the default focus surface (shape small, transparent at rest, `textMuted`
  content, focus scale 1.04, `textPrimary` fill when focused), text padding 24 dp.
- **Actions dialog**: 460 dp wide column on `surface`, shape medium, padding 18 dp, rows spaced
  4 dp, title and subtitle indented 6 dp; test tags `home-resume-actions`,
  `home-resume-action-continue`, `-start-over`, `-watched`, `-remove`.
- **Test tags**: `home-resume-<key>`, `home-trakt-<key>`, `home-sport-<event id>`,
  `home-channel-<channel id>`, `home-resume-status`, `home-hero-primary`.
- Visible at 960×540: landscape cards 4 and a sliver, posters 6, sport 3, channels 4; the
  expanded rail covers about 144 dp of the first card.

## 6. Data

Home stores nothing of its own. It reads:

| Data | Where | Per profile |
|---|---|---|
| Local positions | `playback_progress` (contentKey, profileId, positionMillis, durationMillis, completed, lastWatchedEpochMillis, workKey) | yes |
| Trakt paused/watched cache | `trakt_state` ([Trakt](51-trakt.md) §6) | yes |
| Discover history | `sohva-addon-progress.db` `addon_progress`, encrypted, ≤ 200 per profile ([Discover](50-discover-addons.md) §6) | yes |
| Watch next / Recommended lists | encrypted Trakt store ([Trakt](51-trakt.md)) | yes |
| Recent channels | preference `recent_channel_ids[:id]`, ids joined by U+001F, newest first, max 20 | yes |
| Today's events | Sohva Sport's in-memory feed ([Sohva Sport](60-sohva-sport.md)) | shared |
| Time zone | preference `time_zone` (absent = device zone) | shared |
| Titles, channels, metadata | catalogue, guide and metadata tables, read by key | shared |

Writes: Mark as watched and Remove write or delete one `playback_progress` row for the active
profile. Home's focus, lock and hero state live only while Home is composed; the Continue
watching projection lives for the process. Nothing of Home is in the `.smbak` backup beyond
the data above that backups already carry ([Backup](71-backup-restore.md)).

## 7. External interfaces

Home makes no HTTP request of its own. Indirectly:
- Hero metadata lookups (TMDB search or by-id, TVmaze) on a cache miss, through
  [Metadata](41-metadata-enrichment.md), only after focus rests 180 ms and Continue watching
  has settled.
- Images (posters, fanart, backdrops, logos, crests) through the shared image loader (spec 01
  SHELL-FR-02); Trakt cards use Trakt's image URLs directly.

## 8. Edge cases and limits

- Large catalogue (200,000 films): every Home query starts from the small side
  (`playback_progress`, `trakt_state`, the ≤ 20 recent channel ids) and reaches titles only by
  full key (9.3).
- A series watched episode after episode fills the local query's 20-row limit before the
  one-card-per-series collapse, so Continue watching can show fewer than 12 cards even when
  more titles are paused. Rebuild: collapse per series inside the query, before the limit.
- A source disabled, a catalogue snapshot replaced, or a group hidden by the organisation rules:
  the affected cards disappear on the next emission (the queries require an enabled source, the
  active snapshot and visibility).
- A title removed from the provider: its progress row remains but the card disappears.
- Trakt-only card without a runtime: bar shown, no "min left", resume starts at 0.
- Clock or time-zone change while Home is open: the clock catches up at the next tick.
- Programme windows in the hero use the device zone and a fixed `HH.mm` pattern while the
  clock uses the chosen zone and the locale pattern (quirk, see 10).
- Process death: the projection is rebuilt at the next start; Home shows the loading card until
  the first read settles.
- Duplicate keys in a row crash a keyed lazy row. Rebuild rule: de-duplicate every row's data
  by its card key before display (beta 23 relies on its sources being unique).
- Restricted profile: Continue watching is **not** narrowed by the profile's allowed groups in
  beta 23 (only the device's rules apply), and a profile that connected Trakt before it was
  restricted still gets Trakt-paused library cards (gap; rebuild rule in 9 and
  [Profiles](04-profiles-parental.md)).

## 9. Lightweight by design

Budgets from [Performance](../plan/07-performance.md): Home's first content frame within 4 s
of a cold start on the low-end box (2 s on the Shield); a D-pad press between cards renders
within one or two vsyncs; Java heap in steady browsing ≤ 64 MB.

### 9.1 Start-up read of the resume row

- Current: the Continue watching projection is touched in `Application.onCreate`, waits for the
  container's initialisation, then runs the library query (two unions, ≤ 20 rows, plus the
  TMDB-confirmation sub-select), the Trakt query (≤ 20 rows) and the Discover history read
  (≤ 200 rows, each **decrypted**) in parallel; it logs `home: cached resume ready`. Trakt
  rows, the Trakt sync, Sohva Sport's first refresh and the hero's lookups wait for it.
- Rules for the rebuild:
  - Keep the ordering: the resume read is the first database work after the launch screen;
    everything optional (Trakt, sport, metadata, update check, scheduling) starts after it
    settles and after Home's first content frame.
  - Keep the 5 s failure state; never block Home's first frame on it (the loading card is the
    first frame).
  - The Discover part reads a small plaintext-free projection (key, update time, fraction,
    completed flag) and decrypts only the ≤ 12 entries that become cards; do not construct any
    addon HTTP client, cache or repository for it ([Discover](50-discover-addons.md) §9).
  - The projection does not recompute while a player is in the foreground; it catches up when
    the player closes (Discover writes a snapshot every 5 s during playback).
  - Observe only what can change the row: `playback_progress` for the profile, catalogue
    activation (snapshot switch), source enable/disable, organisation-rule changes and Trakt
    cache replacement. Today's Room flow re-runs the query on every write to any table it
    touches, including every batch of a catalogue import; debounce invalidations to at most one
    re-read per second and drop identical results.

### 9.2 Images

- Card images decode at the card's pixel size (Coil sizes from layout constraints): landscape
  art 178×102 dp, posters 116×178 dp, logos 34 dp, crests 38 dp. At the usual 1080p xhdpi
  density that is at most 356×204 px per landscape card. Use `RGB_565` for posters, fanart and
  backdrops; keep ARGB only for logos and crests with transparency
  ([Icons and imagery](../design/04-icons-and-imagery.md)).
- The hero request today decodes up to 1920×1080 `RGB_565` (about 4.1 MB, two during a
  crossfade) for an art box of 633.6×302.4 dp (about 1267×605 px at xhdpi). Rebuild: decode at
  the art box's pixel size (≤ 1280×720 px, about 1.8 MB `RGB_565`); on devices reporting low
  RAM or otherwise in the low memory tier (plan/07 §2.2: memory class under 192 MB or total RAM
  ≤ 2.5 GiB; the Shield's 192 MB class stays in the standard tier), decode at half that.
- The bundled floor `home_backdrop_live_tv.webp` (1920×1080, `drawable-nodpi`) is decoded to a
  full ARGB bitmap (about 8.3 MB) and drawn under every picture. Rebuild: ship it at the art
  box's size, decode `RGB_565`, and draw it only while no fetched picture is on screen.
- At most two decodes in parallel app-wide (spec 01 SHELL-FR-02); card images of rows scrolled
  out of view are cancelled.

### 9.3 Pinned join order of the Home queries

- The organisation views (`organization_visible_movies`, `_series`, `_channels`) evaluate up to
  16 correlated rule sub-queries per row. If the planner starts from a view, it walks the whole
  catalogue: Continue watching went from under 1 ms to about 5.5 s on a 40,000-title library in
  September 2026 ([lessons](../plan/08-lessons-learned.md) 2.1). Room never runs `ANALYZE`, and
  a fresh install has no statistics until its first import.
- Rules: every Home query starts from its small side and pins the order with `CROSS JOIN`:
  `playback_progress` → source state → catalogue import state → the visible view by full key
  (films: `sourceId, snapshotId, movieId`; episodes: `vod_episodes` by `(sourceId, episodeId)`
  first, then the visible series by full key); `trakt_state` → metadata override by external
  id → source → import state → view by full key; recent channels by `channelId IN (≤ 20 ids)`
  through an index, never a scan of the active playlist. Shape of the local film half:

  ```sql
  FROM playback_progress progress
  CROSS JOIN iptv_source_state source ON source.sourceId = progress.sourceId AND source.enabled = 1
  CROSS JOIN import_state state ON state.sourceId = progress.sourceId AND state.kind = 'catalogue'
  CROSS JOIN organization_visible_movies movie
      ON movie.sourceId = progress.sourceId AND movie.snapshotId = state.activeSnapshotId
      AND movie.movieId = progress.itemId
  WHERE progress.contentType = 'movie' AND progress.completed = 0
      AND progress.positionMillis > 0 AND progress.profileId = :profileId
  ```

- Every Home query is a constant with a JVM plan test (sqlite-jdbc on the exported schema, with
  and without `ANALYZE`) that fails when a plan scans a title table or reaches `vod_series` on
  its snapshot prefix alone, and a timing test on a synthetic large library (current tests:
  under 1,000 ms for local queries, under 100 ms for the Trakt query).
- Run `ANALYZE` after each import activates (already done since 7 September 2026); the pinned
  order stays because a fresh install has no statistics.

### 9.4 Per-frame cost: backdrop layers and a cheaper design

- Current: the ground is one cached offscreen layer (spec 01 §5.5); the hero backdrop is a
  second full-screen offscreen layer holding a third (the art box) with two `DstOut` masks, and
  two full-screen scrims. At 1080p each full-screen layer is about 8.3 MB of GPU memory. The
  layer is re-rendered only when its contents change, but during a 250 ms crossfade every
  frame re-renders the whole backdrop layer (floor, two pictures, masks, scrims). Before the
  September 2026 fix Home painted about 8 screen-sized passes per frame; now about 3.
- Rebuild design ("hero plate"):
  - When the hero's picture or its sport crests change, compose the plate **once, off the main
    thread**: floor (only if no picture), picture, the two fades and the two scrims, rendered
    into one bitmap at the art box's size (plus the scrim's left part as a separate small
    static gradient bitmap, or baked into the ground layer, since it does not depend on the
    picture).
  - Per frame Home then draws: the cached ground, one plate bitmap (≈ 1267×605 px), the rows.
    The full-screen scrims disappear as separate passes.
  - Crossfade = two plate bitmaps with animated alpha for 250 ms; on the low-end class snap
    instead (no second bitmap held).
  - The fade must not show a seam against the ground's gradient and washes: bake the fade by
    blending towards the ground's own pixels in that rectangle (render the ground rectangle
    into the plate first), which keeps the look identical to today's erasing masks.
  - Budget on the low-end box: ≤ 3 screen-sized passes per frame on Home, measured on real
    Mali hardware (the emulator's software GPU cannot show overdraw).

### 9.5 Rail animation by transform, not width

- Current: the rail's width animates 80 ↔ 244 dp with a spring and its scrim alpha animates,
  so every frame of the spring re-measures and re-lays-out the rail column (items, labels) and
  repaints a full-height gradient; labels pop in on the first frame.
- Rebuild: lay the rail out once at 244 dp in its own layer and never change a measured size.
  Collapsed and expanded differ only in the draw phase: the scrim is a pre-rendered gradient
  drawn with a horizontal scale transform (origin at the left edge, 80/244 → 1) and an alpha;
  labels fade with a layer alpha; the focused item's fill width animates in `drawBehind`. Use a
  fixed 150 ms tween (snap on the low-end class). The rows under the rail are never re-laid
  out ([App shell](01-app-shell-navigation.md) §9).

### 9.6 Recomposition and main thread

- Every card writes its subject on focus; only the debounce reads it, outside composition, so
  Home recomposes when the hero changes, not on every press (fixed September 2026; a held key
  on a slow box turned per-press recomposition into a queue). Keep this.
- Home today collects the whole preferences object; any preference write (every zap records a
  recent channel) recomposes Home. Rebuild: collect only `recent_channel_ids` and `time_zone`.
- The minute ticker recomposes only the clock and progress bars (state read in the smallest
  scope); progress fractions are computed in the draw phase.
- Row models are built off the main thread (merge, grouping, alias sets, sorting, sanitising
  titles); composition only maps ready models to cards. No regex in composition (the IMDb id
  check and title sanitiser run in the projection).
- Metadata lookups for the hero run on IO, only after the 180 ms rest, only for the subject
  still in focus; the "metadata enabled" check reads cached settings, not the secure store,
  per lookup.
- Memory upper bound: ≤ 12 + 8 + 6 + 20 + 6 cards of small models, the hero plate(s), and the
  image memory cache (8 % of the memory class).

## 10. Lessons from the current app

- Continue watching queried through the organisation views in the wrong order took seconds per
  emission on large libraries; pinned `CROSS JOIN` order plus plan tests fixed it
  (`CatalogueHomeQueries.kt`, `CatalogueHomeQueryPlanTest`, memory note "organisation view
  join-order trap", commit history September 2026).
- Continue watching arrived a moment after Home, so initial focus went to whichever row was
  ready first; initial focus now waits for the first row (`5a08915`).
- The side menu opened by itself after Back-exit and reopen: the platform's default focus
  search picked the upper-left rail after a focused card was replaced, the last cached card was
  removed, or the window re-entered focus. Fixed by the focus-entry redirect and explicit
  hand-offs, with Left cancelling them (`07b6c2e`, `6810b35`, ledger
  `SOHVA_SPORT_USER_REPORTS.md` › "Menu still opens after loading on beta 20", "Feedback after
  beta 19"; `HomeResumeNavigationTest`, `HomeReopenNavigationTest`).
- Children hopping between episodes filled the row with one series; one card per series
  (`12e366a`).
- The same film from two providers, or from the library and Discover, appeared twice; alias
  grouping with "the copy actually watched wins" (`22d2d04`, `HomeResumeStoreTest`).
- A Home sport card first opened the day's list; it now opens the match (`06ca4ec`).
- Trakt's English synopsis flashed before the Finnish TMDB text; the hero now waits for TMDB
  (`cfb1c8e`).
- Home recomposed on every D-pad press through the hero debounce's key (`a176f0d`, ledger ›
  "Slow boxes").
- Static backgrounds were painted 8 times per frame on Home (`67778c2`).
- Reading the whole guide for six recent channel cards cost seconds and tens of megabytes on
  56,000 channels; Home reads only the recent ids (comment in `HomeScreen.kt`).
- The cached resume read was made the first work after launch, before optional Home work
  (`ca940fe`).
- Quirks to fix in the rebuild (current behaviour, not intended):
  - Remove / Mark as watched cannot remove a Trakt-only card (4.7).
  - The local query's 20-row limit counts episodes before the per-series collapse (8).
  - Continue watching ignores the restricted profile's allowed groups (8).
  - The hero's programme window uses the device zone and a fixed `HH.mm` pattern; the clock
    uses the chosen zone and the locale's pattern. Use the chosen zone and the locale's
    pattern everywhere.
  - Recent channel cards keep the programme current at Home entry; after it ends they show a
    full bar until Home is re-entered.
  - The minute ticker is not aligned to the minute.
  - The loading status card uses the default text style (no token), unlike every other Home
    text.

### Open questions

- The Home row editor (order and visibility per profile, Settings › Home) and Trakt list rows
  are planned in `docs/HOME_ROWS_AND_TRAKT_LISTS_PLAN.md` phases 2–4 but are not in beta 23.
  Does the rebuild include them from the start?
- Should Remove / Mark as watched on a Trakt-sourced card also clear the Trakt pause (a Trakt
  API write) or hide the card locally until Trakt's position changes?
- Should Continue watching for a restricted profile be narrowed by its allowed film and series
  groups (recommended), and should a restricted profile's Trakt part be dropped entirely (the
  Trakt spec's rebuild rule says yes)?
- Discover cards have no long-press actions; should they get Remove (forget progress)?

## 11. Acceptance tests

Unit (JVM):
- Merge rules (mirror `HomeResumeStoreTest`): both sources start together and produce one
  ordered row; Discover-only history is not "empty"; switching profile or restricting Discover
  clears the previous row; a read stuck > 5 s is `FAILED` and recovers without polling; locked
  structure keeps order and updates values in place; overnight provider copies keep one card
  using the Discover source actually watched; shared ids bridge formats and prefer the most
  recently used local copy; equal titles alone and film/series id collisions never merge;
  duplicate copies do not consume the 12-card limit.
- Initials (mirror `ArtworkInitialsTest`).
- Query plans and timing (mirror `CatalogueHomeQueryPlanTest`): Continue watching, Trakt
  Continue watching, recent channels; with and without `ANALYZE`; 20-row limits; one row per
  Trakt identity; the per-series collapse happens before the limit (new).
- Hero subject selection: idle rules; 180 ms settle; rail focus → idle immediately (use a test
  clock).

Instrumentation (debug build, state cleared before launch):
- Rail: every destination present, Up/Down order, Right and Back return to the rows, Who is
  watching appears with a second profile (mirror `HomeScreenTest`).
- Focus and data arrival (mirror `HomeResumeNavigationTest`): an immediate Down waits for the
  cached row then focuses its first card; later rows and resume reordering wait until the
  viewer returns to the top; a warm return uses the updated snapshot and a profile change
  clears it; confirmed-empty history focuses the Welcome button; opening the rail while loading
  keeps it open when data arrives; late rows replace the Welcome button without opening the
  rail; replacing the focused card keeps focus in the content; removing the last card hands
  focus to Welcome; a platform focus re-entry lands on content, not the rail; removing another
  card keeps the current card focused.
- Back-exit and reopen three times in one process: content focused each time, rail closed
  (mirror `HomeReopenNavigationTest`).
- A large cached library plus Discover progress reach one retained row (mirror
  `HomeResumeDatabaseTest`).
- The hero keeps a readable ground under its text (mirror `HomeTileBackdropTest`).
- Hold OK on a library card: the dialog opens with Resume focused; each action does what 4.7
  says; Back closes it and focus returns to the row.
- OK on each card type opens the destination in 3.2, and Back returns as stated.

Manual, on a device:
- Cold start with a populated profile: launch picture → loading card → first card focused,
  rail closed, no text flash.
- Move along a row quickly and hold Right: the hero changes only when focus rests; no stutter.
- Trakt connected: first-sync note, then Watch next and Recommended rows; synopsis in the
  metadata language.

Low-end performance (Elisa-class box, or the `.local/slowbox` emulator harness as a relative
stand-in; release build with its install-time profile):
- `home: cached resume ready` ≤ 1 s after process start with the owner-scale fixture (56,164
  channels, 30,000+ films, 1,500 series); Home's first content frame ≤ 4 s cold.
- Held Right across a 12-card row: every press renders within two vsyncs; Home does not
  recompose per press (composition counter or trace markers).
- GPU overdraw on real Mali hardware: ≤ 3 screen-sized passes per frame on Home at rest and
  while the hero crossfades.
- Rail open/close: no layout pass of the rail or rows during the animation (trace markers).
- Java heap while browsing Home ≤ 64 MB; hero bitmaps ≤ 2 at a time and at art-box size.

## 12. Reference: current code map

- `app/src/main/java/com/streammate/tv/feature/home/HomeScreen.kt` — Home screen: rows, hero, backdrop, header and clock, rail, cards, initials, constants.
- `app/src/main/java/com/streammate/tv/feature/home/HomeRows.kt` — the five rows, first-row key, locked update.
- `app/src/main/java/com/streammate/tv/feature/home/HomeResumeState.kt` — Continue watching entries, retained store with 5 s timeout, cross-ledger merge, structure-lock helper.
- `app/src/main/java/com/streammate/tv/feature/home/HomeResumeActions.kt` — hold-OK actions dialog.
- `core/src/main/java/com/streammate/tv/core/database/CatalogueHomeQueries.kt` — Continue watching and history-partition SQL (pinned joins).
- `core/src/main/java/com/streammate/tv/core/database/TraktHomeQueries.kt` — Trakt-paused library copies SQL.
- `iptv/src/main/java/com/streammate/tv/iptv/repository/CatalogueRepository.kt` — `observeContinueWatching` (local + Trakt merge, per-series collapse), `markWatched`, `forgetProgress`, `progress`.
- `iptv/src/main/java/com/streammate/tv/iptv/repository/GuideStore.kt` — `observeGuideChannels` (recent channel cards), `observeTimelineForChannels`.
- `core/src/main/java/com/streammate/tv/core/database/GuideDao.kt` — `observeGuideForChannels` SQL.
- `app/src/main/java/com/streammate/tv/app/StreamMateApp.kt` — Home wiring: resume snapshot, Trakt rows gated on the first read, routes for each card.
- `app/src/main/java/com/streammate/tv/app/StreamMateContainer.kt` — `homeResume` store construction and diagnostics line.
- `app/src/main/java/com/streammate/tv/app/StreamMateApplication.kt` — early touch of the store.
- `app/src/main/res/drawable-nodpi/home_backdrop_live_tv.webp` — bundled hero floor.
- Tests: `HomeResumeStoreTest`, `ArtworkInitialsTest`, `CatalogueHomeQueryPlanTest`, `HomeScreenTest`, `HomeResumeNavigationTest`, `HomeReopenNavigationTest`, `HomeResumeDatabaseTest`, `HomeTileBackdropTest`, `FocusScrollMechanismTest`.
