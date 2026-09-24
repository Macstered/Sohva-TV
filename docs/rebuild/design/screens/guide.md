# Screen layout: Live TV guide

> Code-derived visual and interaction values of the beta 23 guide (public tree `efab52a`,
> folder `.local\beta19-public-source`). Extracted 24 September 2026 by reading every line of
> `iptv/.../feature/guide/*` (GuideScreen, GuideGrid, GuideHero, GuideRail, GuideCommon,
> GuideProgrammeActions, GuideTimeWindow), `feature/common/ChannelDial.kt` and the core theme
> files. Line numbers are orientation for the old tree only. Behaviour requirements are in
> [specs/20-live-tv-guide.md](../../specs/20-live-tv-guide.md); tokens in
> [design/01](../01-design-system.md). Screenshot: `design/screenshots/beta23-synthetic/guide.png`.

## Conventions

- Tokens are `StreamMateThemeTokens.palette / typography / shapes / spacing`. None of the guide
  files uses a literal `Color(0x…)`; the only literal colour is `Color.Transparent`.
- All `Text` is `androidx.tv.material3.Text`. Most call sites copy only `fontSize` and
  `lineHeight` from a typography token and set `fontWeight` themselves, so a token's weight does
  **not** apply unless listed. "Weight not set" means the library default.
- Default tokens used below: `label` 14/19 sp; `caption` 12/16; `overline` 12/16 with letter
  spacing 1.4 sp; `headline` 22/27; `bodyLarge` 18/25. `shapes.small` 8 dp, `medium` 12, `large`
  18. `spacing.sm` 8, `lg` 16, `xl` 24, `safeHorizontal` 40, `safeVertical` 24.
- Palette roles: `surfaceSubtle`, `surface`, `surfaceRaised` are steps 1–3 of a translucent
  "surface ladder"; `surfaceFocused` is the selected-but-unfocused tint; `panel` is the opaque
  overlay panel; `focus` is the cyan focus and primary accent; `danger` is live and destructive;
  `accent` is the orange sport role; `textPrimary / textMuted / textDim` are primary, secondary
  and tertiary text; `background` is the ground and "the ink that shows on a focused fill".

## 0. Frame and skeleton

- `StreamMateScreenBackground` with the default content inset (40 dp horizontal, 24 dp vertical).
  Its backdrop is one Canvas in an offscreen layer: a vertical gradient backgroundTop →
  background → backgroundBottom (stops 0, 0.5, 1) and two radial washes (`focus` α0.10 → 0.02 →
  transparent; `secondaryGlow` α0.12 → 0.02 → transparent), painted once.
- `Box(content)` → `Column(fillMaxSize)`:
  - not loaded → `LoadingGuide()`; library empty → `EmptyGuide(...)`.
  - `GuideSelectionHero` → `GuideHero` then `Spacer(12 dp)` (`GUIDE_HERO_GAP`). No hero and no
    gap while the selection is null.
  - `Row(fillMaxWidth, weight 1f, spacedBy(GUIDE_CONTENT_GAP = 14 dp))`:
    - `if (groupRailVisible) GuideGroupRail(width GUIDE_RAIL_WIDTH = 200 dp, fillMaxHeight)`
    - empty list → `Box(weight 1f, fillMaxHeight, padding 24 dp, Center)` with a message
    - otherwise `Column(fillMaxHeight, weight 1f)`: optional reading notice; `GuideGrid(fillMaxWidth,
      weight 1f)`; `GuideKeyHints()`.
  - `ChannelDialOverlay(align TopEnd, padding(top 12, end 12), zIndex 1)`.
  - `if (optionsVisible) GuideOptionsSheet(zIndex 1)` (drawn above the dial).
- `GuideProgrammeActionsDialog` is a platform `Dialog`.
- Trace sections in release code: `Guide:Screen`, `Guide:Grid`, `Guide:Row`, `Guide:ChannelCell`,
  `Guide:ProgrammeCell`, `Guide:Hero`. Keep equivalents in the rebuild (free when not tracing).

### Derived geometry at 960×540 dp (1920×1080 px, density 2)

Assumes fontScale 1, default tokens, a selection exists, no reading notice; the rail starts closed.

| Quantity | Value |
|---|---|
| Safe content box | 880 × 492 dp, origin (40, 24) |
| Hero + gap | 136 + 12 = 148 → 344 dp below |
| Key hints | 6 top padding + 18 = 24 → grid is 320 dp tall |
| Grid header | max(20 ruler, 19 date) = 20, + 4 gap → list viewport 296 dp |
| Rows | pitch 48 (44 + 4): **6 full rows** plus the top 8 dp of row 7 |
| Timeline | 880 − 232 − 6 = **642 dp for 180 min** = 3.567 dp/min = **107 dp per 30-min tick** |
| Rail open | grid column 666 dp → timeline 428 dp (2.378 dp/min, 71.3 dp per tick): the rail squeezes the timeline, it does not overlay it |
| Now-line at "now" | window starts 30–60 min before now → 107–214 dp into the timeline |
| Hero still | 136 × 16/9 = 241.8 dp wide; detail column 622.2 dp |

## 1. Hero (`GuideHero`, 136 dp tall)

Purpose: describes the block under the cursor. Left, a 16:9 still with the channel and programme
written across its foot and a cyan progress line; right, title, facts, synopsis and actions.
"Nothing here is invented": facts with no value are left out.

`Row(fillMaxWidth, height GUIDE_HERO_HEIGHT = 136 dp)`: still, `Spacer(spacing.lg 16)`, detail
column `weight(1f)`.

### Still

- `Box(fillMaxHeight, aspectRatio 16/9, clip(shapes.large), background(surfaceSubtle))`.
- Image: `metadata.backdropUrl`, else `metadata.posterUrl` (a backdrop is preferred: "a poster
  stretched to 16:9 is a crop of somebody's face"). `AsyncImage(Crop, fillMaxSize)`; no size,
  placeholder or error painter.
- No still: centred `ChannelLogo(size 56 dp)` in its unfocused look.
- Scrim: `verticalGradient(0.46 → background α0, 1 → background α0.92)` — starts halfway down.
- Caption block: `Column(align BottomStart, fillMaxWidth, padding(start 14, end 14, bottom 12))`:
  - Row: if live, `Box(8 dp, CircleShape, danger)` + `Spacer(7)`; text
    `[channel.name, "Channel %1$d" (guide_channel_number)].joinToString("  ·  ").uppercase()`, e.g.
    "YLE TV1  ·  CHANNEL 1", `textPrimary`, overline size/line/letterSpacing, Bold, 1 line.
    The number is `channel.channelNumber` else list position + 1; omitted when channel numbers
    are off.
  - `Spacer(4)`; title (`programme.title ?: channel.name`), `textPrimary`, bodyLarge, Bold, 1 line.
- Progress (live only, drawn last, clipped by the 18 dp corners): track `height 3 dp`,
  `textPrimary α0.18`; fill `focus`, width = programme progress at now.

### Detail column (`fillMaxHeight`, no padding)

1. Title: `programme.title ?: channel.name`, `textPrimary`, headline, **Black**, 1 line.
2. `Spacer(6)`.
3. Facts `Row(spacedBy 10)`: `TvTagChip("LIVE", tone LIVE)` if live; text
   `[time range, first non-blank category, metadata.year].joinToString("  ·  ")` in `textMuted`,
   label size, 1 line; `TvTagChip("TMDB %1$s", tone RATING)` if rated. Times `HH.mm–HH.mm`
   (en dash).
4. `Spacer(8)`.
5. Description: `metadata.overview ?: programme.description ?: programme.subtitle ?:
   guide_programme_no_details` ("No programme details are available."), `textMuted`, label,
   maxLines 3, `weight(1f)`. Only about **2 lines fit** at fontScale 1 (43–46 dp left).
6. Buttons `Row(horizontalScroll, spacedBy 8)`, all compact `TvActionButton`:
   - Watch (`action_watch`, Play)
   - Favourite / Add favourite (Star / StarOutline, `selected = favourite`)
   - Reminder set / Remind me (Epg, `selected`) — only when the programme starts after now
   - Watch from start (live) / Watch recording (past), Replay — only when catch-up is possible
   - Find programme / Close search (Search, `selected = searchVisible`)
   - "Source: %1$s" (Info) — only with metadata

Compact `TvActionButton`: padding 12/7 dp, 16 dp icon + 7 dp gap, caption 12/16 Bold,
`shapes.small`, about 30 dp tall. Rest: `surface` fill, `textPrimary`. Focused: fill
`textPrimary`, ink `background`, scale 1.03, shadow 10 dp. Selected: `focus` content on
`surfaceFocused`. `TvTagChip`: caption Bold, padding 8/3, `shapes.small`; LIVE = `danger` fill
with `textPrimary` text; RATING = `rating` text on `rating` α0.14.

Badges: only LIVE (chip and red dot) and the TMDB rating. No HD or catch-up badges.

## 2. Grid (`GuideGrid`)

A day label and half-hour ruler over a fixed channel column and a 3-hour timeline, with one red
now-line across the whole grid. Built as a LazyColumn of rows; each row is
`[channel cell 232 dp | 6 dp | Box(timelineWidth) of ProgrammeCells]`, each cell positioned with
`offset(x)` and `width(w)`. No horizontal laziness.

- `timelineWidth = maxWidth − 232 − 6`; `minuteWidth = timelineWidth / 180`. Nothing is a pixel
  coordinate from a mockup.
- Header, `Spacer(4)`, then `LazyColumn(fillMaxWidth, weight 1f, spacedBy 4, contentPadding
  bottom 4)`, keyed by channel id. No item animations.

### Header and ruler

- Left part, width 232: date `EEE d.M.` (e.g. "Thu 24.9.") in the preference time zone,
  `textPrimary`, label, Bold; `Spacer(6)`; relative day: "Now" at the live window, otherwise
  "Today" / "Tomorrow" / "Yesterday", empty beyond ±1 day; `textDim` at now, `accent` when paged;
  caption.
- `Spacer(6)`; ruler `Box(width timelineWidth, height 20, no semantics)` with six labels
  (`HH.mm`) at 0, 30 … 150 min, **left-aligned at the tick**, `textDim`, label size. No label at
  180. **No tick marks or half-hour rules are drawn.**

### Now-line

Only when now is inside the window. `x = 232 + 6 + minuteWidth × minutes since window start`.
Line `width 2 dp, fillMaxHeight, danger`; head `9 dp circle, danger`, at y 0 over the ruler.
Moves only when `now` changes (every 60 s); not animated. One line for the whole grid "rather
than a stub inside every row".

### Row (44 dp)

- Programmes drawn: `stop > windowStart && start < windowEnd`; clipped to the window and to the
  next programme's start (providers publish overlapping corrections); skipped if empty.
- No minimum width (the old minimum spilled short entries over the next one). Horizontal gap =
  each block's `padding(end 4)`; vertical gap 4. No separator lines.
- A row with no programmes gets a full-width filler: "No EPG information" / "Watch channel" once
  loaded (focusable, pages, plays live); before loading, a blank disabled `surfaceSubtle` bar.

### Channel cell (232 × 44)

Modifiers: `width 232, fillMaxHeight, clip(small), drawBehind(animated background)`, focus
requesters, key handlers, `clickable`, `focusable`, `padding(start 4, end 8)`,
`clearAndSetSemantics { number, name, feed line }`.

Content: number (when shown; `channelNumber ?: index + 1`) `widthIn(min 20)`, caption, Bold,
end-aligned, `Spacer(6)`; `ChannelLogo(30 dp)`, `Spacer(8)`; column: name (SemiBold; label size
with 17 sp line, or 13/15 sp and 2 lines once it would ellipsize — a one-way flip per name) and
feed line (stream tags from the name joined with " · ", else group title, else source name;
caption, 14 sp line; hidden when the name wraps).

| State | Background | Name | Number / feed | Logo tile / initials |
|---|---|---|---|---|
| Focused | `textPrimary` | `background` | `background` α0.62 | `background` α0.10 / `background` |
| Row selected, focus on a programme | `surfaceFocused` | `textPrimary` | `textMuted` | `surface` / `textPrimary` |
| Resting | transparent | `textMuted` | `textDim` | `surface` / `textPrimary` |

Only the background animates (`animateColorAsState`, default spec, read in `drawBehind` so the
cell never recomposes during the fade). No scale, border or shadow.

### ChannelLogo

`Box(size, clip(small), background(focused ? background α0.10 : surface))`. Blank URL: first two
characters of the name, uppercased, Black, caption. Otherwise `AsyncImage(fillMaxSize,
padding 3 dp)`. A URL that fails leaves an empty tile (rebuild: show initials on failure too).

### Programme cell

Modifiers: `zIndex(selected ? 1 : 0)`, focus requester, key handler, `offset(x)`, `width(w)`,
`fillMaxHeight`, `padding(end 4)`, `alpha(past && !selected ? 0.55 : 1)`, `clip(small)`,
`background(selected → textPrimary; airing → surface; else surfaceSubtle)`, progress strip drawn
after content (`focus`, 3 dp high at the foot, width × fraction), `clickable`, `focusable`,
`clearAndSetSemantics { title, time }`.

Genre accent bar (not when selected): `3 × 32 dp` at the start, rounded 2 dp on the right,
colour from the first category matching a keyword:

| Genre colour token | Keywords |
|---|---|
| `genres.sport` | sport, urheilu, football, jalkapallo, hockey |
| `genres.news` | news, uutis, current affairs, ajankohtais, weather |
| `genres.children` | children, kids, lapset, lasten, animation |
| `genres.film` | movie, film, elokuva, cinema, drama, draama |

Text column: padding start 10 (with bar) or 8, end 8, top 4. Title caption, 15 sp line, Bold,
1 line; time `HH.mm–HH.mm` caption, 14 sp line.

| State | Fill | Title | Time | Extras |
|---|---|---|---|---|
| Selected | `textPrimary` | `background` | `background` α0.62 | zIndex 1, no bar, full alpha even if past, strip if live |
| Airing | `surface` | `textPrimary` | `focus` | `focus` progress strip |
| Past | `surfaceSubtle` | `textPrimary` | `textDim` | whole cell α0.55 |
| Future | `surfaceSubtle` | `textPrimary` | `textDim` | — |

The white look is driven by the **selection**, not by focus: focusing sets the selection, and the
cell stays white while focus is in the hero or rail. The fill flips instantly.

Derived at 3.567 dp/min: a block under ~1.1 min draws nothing; text width reaches zero under
~5.6 min.

## 3. Key hints

`Row(fillMaxWidth, padding(top 6), spacedBy 18)`; each item: key chip (`clip(small),
background(surface), padding 6/1`, `textMuted`, caption Bold), `Spacer(6)`, label (`textDim`,
caption). Pairs: "◀ ▶" Later / earlier, "▲ ▼" Channel, "OK" Watch, "⏮ ⏭" Day, "MENU" Options.
Only bindings that work are listed ("a bar of dead keys is worse than none"). The hints sit in the
grid column and move right when the rail opens.

## 4. Group rail (200 dp)

- Composed only when visible; no animation; opening re-scales the timeline.
- Right (KeyDown) anywhere exits to the grid.
- Header "Groups" (`guide_groups`), `padding(start 14, bottom 6)`, `textDim`, overline, Bold.
- Options button (compact, Info icon, `fillMaxWidth, padding(bottom 8)`) — own line because
  Finnish words are long; gives Left a landing spot when no rail item is selected.
- Search field when visible: `TvUrlField("Search channels or programmes", compact)`, max 80
  characters; programme matches debounced 250 ms.
- List: `LazyColumn(weight 1f, spacedBy 2, contentPadding bottom 10)`. Order: Favourites (if
  enabled), All channels, Recently watched (if enabled), custom lists, provider groups; manual order
  sorts by position with All pinned first.
- Item: dense `TvListRow`: label; icon Star for favourites (edit mode: Check/Close); trailing count
  (favourites, all, group) or "Hidden"/"Shown" in edit mode; `selected` row carries the selection
  bar. Dense row: padding 12/8, label text (Bold when selected, else Medium), 18 dp icon; selected
  bar 3×22 dp `focus` (`background` when focused) then 12 dp gap; focus = fill flip + 14 dp shadow.
  Rows are 35 dp (selected 38). About 7 rows visible at 960×540.
- Opening: Left on a channel cell. Focus goes to the selected item (jumps with `scrollToItem`).
  List state is kept at screen level. Closing: Right, or any grid cell gaining focus. No Back
  handler for the rail.

## 5. Options sheet (Menu)

- Scrim `background α0.86` over the safe content box; card `widthIn(340..420)`, `clip(large)`,
  `panel`, `padding(24)`, `spacedBy 8`.
- Title "Guide options" (headline, Black); subtitle "Live TV and programme information" (`textMuted`,
  label, `padding bottom 8`).
- Seven full-width buttons: "Source: %1$s" (first focus), "Sort: Playlist / A–Z" (opens the group
  manager when wired), Edit / Done (Check, `selected` in edit mode), Edit channels (Channels),
  Settings, Back, Close. Non-compact buttons are ~41 dp (padding 18/11, 18 dp icon, label Bold);
  sheet ~453 dp tall.
- Back or Close dismisses. **Focus is placed before the sheet hides**: to the list when it changed
  under the sheet, else the rail Options entry or the selected row's channel cell. No animation.

## 6. Programme actions dialog

Opened by OK on a future programme or OK held on any programme. Platform `Dialog`
(`usePlatformDefaultWidth = false`); card `width 460, background(surface, medium), padding 18,
spacedBy 4`. Title 18 sp Bold (2 lines); subtitle channel · time, 13 sp `textMuted`. Dense rows:
Watch (or "Watch the channel now"), catch-up, reminder, favourite. Watch/catch-up/reminder close
the dialog; favourite toggles in place. On close, focus returns to the row's first visible
programme (rebuild: return to the programme it was opened from).

## 7. Channel dial overlay

Max 4 digits; commits 2,000 ms after the last digit; "No channel %1$d" shows 1,500 ms. Keys 0–9
and numpad. Text "Channel %1$s", 22 sp Bold, on `panel α0.94` with 1 dp `outline` border,
`shapes.medium`, padding 18/10, top-right of the safe area. Resolves provider numbers first, then
list position. Appears and disappears without animation. Works while focus is in the grid.

## 8. Focus and keys

- Initial focus: scroll to the focus row and focus its channel cell (the dialled row, else the
  channel the guide was opened for, else 0). `requestFocusWhenAttached` retries up to 30 frames.
- Channel cell: Left opens the rail; Right is consumed while the row's programmes are unread;
  while a page is pending, the pending direction's repeats are swallowed; OK plays live.
- Programme cell: Right on the last visible block pages +90 min; Left on the first pages −90 min
  unless at now (then Left goes to the channel cell, and again to the rail). OK: future → actions
  dialog; otherwise play (catch-up from the programme start when the channel supports it, else
  live). OK held opens the dialog once and swallows the release.
- Grid container: digits dial; FastForward/Rewind ±90 min; Next/Previous ±1 day; Menu opens the
  options sheet.
- Back closes the sheet, then leaves category edit.
- Paging: one page at a time; focus parks on the channel cell; the window moves; when the
  channel's programmes arrive, the first (forward) or last (back) programme takes focus.

## 9. Time window and timings

- Window 180 min, ticks 30 min, anchor = floor(now / 30 min) × 30 min − 30 min; page 90 min;
  day 24 h; clamp [anchor − 1 day, anchor + 7 days]. The window is an absolute start, not an
  offset.
- Clock: a 60 s ticker (not aligned to the minute) drives the now-line, progress and states.
  Rebuild: align the tick to the minute boundary.
- Metadata lookup 350 ms after the selection settles; search debounce 250 ms; reading notice after
  400 ms; dial 2,000/1,500 ms.
- The only animation is the channel cell background colour.

## 10. States

- Loading: "Reading the guide…" (`textMuted`, padding 28) until the rail and first timeline are read.
- Grid area: "No favourite channels selected yet", "No recently watched channels yet", "No channels
  in the selected source or group".
- Reading notice above the stale grid (stale rows are kept rather than flashing empty).
- Empty library card (`clip(large), surface, padding 28`): "No channels have been imported yet";
  description with or without sources; per source a Playlist and Programme guide health line
  (13 sp, `danger` when failed); buttons "Sync now" (Refresh) and "Open settings"; hint "Syncing runs
  in the background. The guide fills in as soon as channels arrive."

## 11. Data and recomposition architecture (what made it fast enough)

- Rail from the channel table only. The guide opens on a group, never on All channels (56,000
  rows took three seconds on the Shield); a source switch lands on its first group.
- Rows read without the EPG join; paging never reloads them.
- Ordering and filtering in `produceState` on `Dispatchers.Default`, stages remembered separately.
- Programmes requested for visible rows ±30, snapped to 10-row steps, for 4 hours (window ±30 min).
- A retained cache of at most 240 channel schedules; equal lists reuse identity (equal-but-new
  schedules cost 50–108 ms a frame on the Shield).
- A lazy merging overlay list, memoised per index; changing a request cancels the old collector.
- The selection is read only in effects, the hero and row scopes (reading it in the screen body
  recomposed everything on every press).

## 12. Known flaws to fix in the rebuild

1. Programme cell times use the system zone; everything else uses the chosen time zone.
2. The progress strip of a block clipped at the window's left edge does not end at the now-line.
3. The hero synopsis says 3 lines but 2 fit.
4. "Find programme" in the hero toggles a field that lives in the closed rail.
5. The options scrim does not dim the outer safe margins.
6. Closing the actions dialog focuses the first visible programme, not the one it came from.
7. The hero is absent until a selection exists, shifting the layout.
8. A failing logo URL leaves an empty tile.
9. Per-cell rounded clips and alpha layers (about 7 rows × 5–7 cells) — draw rows as one custom
   layer on low-end GPUs.
