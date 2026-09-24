# Screen layout: Movies and Series walls, details, match picker

> Code-derived values from beta 23 (`efab52a`), extracted 24 September 2026 from
> `iptv/.../feature/catalogue/` (v2 browser, poster, detail components, backdrop, movie and series
> details, match picker, presentation, genre labels, collapse) and `app/.../trakt/TraktTitleScreen.kt`.
> Behaviour: [specs/40](../../specs/40-movies-and-series.md), [41](../../specs/41-metadata-enrichment.md).
> Screenshots: `design/screenshots/beta23-synthetic/movies-wall*.png`, `series-wall*.png`;
> older details pages in `older-builds/2026-09-02-demo/03.1-*.png`, `04.1-*.png`.

Only colours change with the colour theme; typography, spacing and shapes are always the
defaults listed in [home-and-shell.md](home-and-shell.md#0-foundations). Numbers assume Interface
size Normal at 960×540 dp.

## 1. The wall (Movies or Series)

Opens on **History** ("recorded playback, newest first"); if the organisation hides History, on
the first playlist group. A retained session keeps the last state, both scroll positions, the
focused content key and focus-return flags.

```
StreamMateScreenBackground (padding 40/24)
└ Column
  ├ HEADER Row(bottom-aligned, padding bottom 16)
  └ Row(spacedBy 22)
    ├ RAIL Column(width 216, spacedBy 8)
    │  ├ search field (compact, "Search movies" / "Search series")
    │  ├ Row(spacedBy 6){ Groups | Genres }  (compact toggle buttons, 105 dp each)
    │  ├ Options button (Info icon, full width, compact)
    │  └ LazyColumn of destinations (weight 1)
    └ WALL LazyVerticalGrid | message
└ Options sheet (zIndex 1) when open
```

### Header

1. Title "Movies" / "Series": `textPrimary`, display 40/44, Black.
2. Partition label and count "History  ·  124": `textDim`, bodyLarge, padding start 16 / bottom 5.
   Label = History, group name, genre, custom group or "Unsorted".
3. Stale note "Loading library…" or the failure text, caption.
4. Refresh result text, caption.

### Rail

- Destination rows: dense `TvListRow` with divider, 2-line labels, Replay icon for History,
  trailing count; `selected` marks the current partition. OK selects; focus alone does not.
- Order: History, then playlist groups or genre facets (custom groups, then each genre with titles,
  then Unsorted). Manual group sort applies when chosen.
- Genres: Action, Adventure, Animation, Comedy, Crime, Documentary, Drama, Family, Fantasy,
  History, Horror, Music, Mystery, News, Reality, Romance, Science fiction, Soap, Talk show,
  Thriller, War, Western.

### Grid

- `GridCells.Adaptive(min 88 dp)`, `spacedBy(22 horizontal, 30 vertical)`, keyed by content key.
- At 960×540: wall 642 dp wide → **6 columns** of ≈88.7 dp; poster 2:3 ≈88.7×133; card ≈177 dp
  tall (poster + 9 + title 19 + facts 16); row pitch ≈207. At rest 2 full rows (12 posters) plus a
  sliver. Compact/Small/Smaller give about 7/8/9 columns.
- No sort control, letter index or count badges in the grid; order comes from the repository.

### Poster card

- Column: `fillMaxWidth`, focus requester for restoration, `clickable` + `focusable` (disabled while
  the wall is stale).
- Poster box: 2:3, clip medium (12 dp); focused → `border(3 dp, textPrimary)`. No scale,
  elevation or animation.
- Title below (padding top 9): focused `textPrimary`, else `textMuted`; label SemiBold; 1 line.
- Facts: "year · rating", `textDim`, caption.
- Badges: watched tick (movies only) at top start — 26 dp `focus` circle, 15 dp check in
  `background`; chips at top end — "×N" (ACCENT) when several copies fold into one card, then
  quality chips (PRIMARY): "4K UHD", "Dolby Vision" / "HDR10+" / "HDR10".
- No progress bar or rating badge on wall posters.
- The old card used the default click indication (a dark overlay on focus). The rebuild uses no
  indication; the ring is the focus signal.

### Poster image

- Tile: clip medium, vertical gradient `surfaceSubtle` → `background`.
- No URL: initials (textMuted, 22 sp, Black). Otherwise `AsyncImage` requested at **192×288 px**,
  inexact precision, **no crossfade**, Crop.
- Fallback chain: provider poster → on error TMDB replacement poster → initials. (If the
  replacement also fails the old app leaves a blank tile; the rebuild shows initials.)

### Options sheet

Scrim `background α0.86`; panel `widthIn(340..420)`, clip large, `panel`, padding 24, spacedBy 8.
Title "Library options" (headline Black); subtitle "Your provider’s library". Buttons: Refresh
(first focus; "Refreshing…" while busy), Edit / Done (opens the Library manager in production),
Back, Close.

### States

Stale wall stays visible with disabled cards while a new partition or search loads. Messages
(`textMuted`, bodyLarge, centred): "Loading library…", "The library could not be loaded.", "No
titles in this group.", "No matching items found." No spinners or skeletons.

### Focus and keys

- Initial focus: History row, else first group, else Options.
- Left from **column 0** of the wall scrolls the rail to the current partition and focuses it;
  other columns use normal traversal.
- OK on a card opens details and remembers the card; on return, the wall scrolls to that card (or
  the folded film's primary copy) and focuses it; rail and wall scroll positions persist.
- Back: close Options, else leave edit mode, else leave.
- Constants: search debounce 250 ms; search max 80 characters; an empty result is held back
  500 ms when counts say it should not be empty; watched marks read for visible ±12 items (max 200).

## 2. Shared details components

- **Backdrop**: diagonal gradient backgroundTop → background → backgroundBottom; full-strength
  `AsyncImage(Crop)`; scrims: horizontal `background` α0.97 (0) → 0.84 (0.28) → 0.04 (0.60) → 0.22
  (1); vertical α0.42 (0) → 0 (0.20) → 0.99 (0.86) → 1.0 (1). Upper right left clear for the art.
- **Breadcrumb**: "MOVIES › GROUP › TITLE" — uppercase, label Bold, letter spacing 1.4; last crumb
  `textMuted`, others `textDim`; separator "›" `textDisabled` with 8 dp padding; `[…]` and `(…)`
  stripped from categories.
- **Facts**: star 16 dp + score in `rating` (bodyLarge Bold); facts joined "  ·  " `textMuted`;
  quality chips.
- **Progress**: "Watched %1$s of %2$s" + track 220×4 dp (`surfaceRaised`, fill `focus`, padding 14)
  + "%1$s left". Runtime "%1$d h %2$d min" or "%1$d min", minutes rounded up.
- **Action button**: TvSurface height 48, shape medium, rest `surfaceRaised` (primary) or `surface`,
  content textPrimary, focusScale 1, padding 22; icon 18 + 10 + label body Bold. Focus flips the fill.
- **Cast member** (movie page): not focusable; column 84 wide; avatar 52 dp circle `surface` with
  initials under the photo; name label SemiBold 2 lines; character caption `textDim`.
- **Artwork card** (Similar): 104 wide, poster 2:3 (104×156), clip medium, focused 3 dp ring and
  1.05 scale on a child layer; bottom scrim; title caption Bold 2 lines; year.
- **Version card**: TvSurface 208 wide, padding 16/12; source name body Bold; "Selected" chip on the
  current copy; claims (languages + picture quality) caption at α0.72.
- **Section heading**: headline Bold.

## 3. Movie details

```
Backdrop(metadata backdrop)
└ scrolling column padding(32 horizontal, 24 vertical)
  ├ Row: SohvaTvBrand 22 sp, 24 dp, breadcrumb
  ├ Spacer 34
  ├ Title (width 0.62): display 40/44 Black, 2 lines
  ├ Facts (top 12)
  ├ Overview (width 0.62, top 16): textMuted body, 4 lines
  ├ Progress (top 18, when not finished)
  ├ Actions Row(top 18, spacedBy 12; scrolls the page to the top when focused)
  ├ Versions (only with >1 copy): heading + LazyRow(spacedBy 12)
  ├ Cast: heading + LazyRow(spacedBy 18)
  └ Similar (after details load): "Checking your library…" / "None of TMDB’s similar movies are
    available in your library." / LazyRow of artwork cards
```

Buttons: Resume or Watch (primary, first focus); Start from beginning (with a resume position);
Mark as watched / unwatched; "Source: %1$s" (with metadata); "Wrong details?". No poster on the
page; no trailer or favourite button; Back is the remote key (no on-screen Back).

## 4. Series details

Same shell with: breadcrumb weighted; title gap 30; facts year, "N seasons", runtime; overview 3
lines; **cast as one line** "Cast: a, b, c" (label, 2 lines) — cast tiles pushed episodes off screen.

Buttons: Continue episode / Watch episode (primary); Start from beginning; Mark as watched;
Mark season as watched; Refresh episodes; Wrong details?; Source.

- Seasons row (top 26): heading, 16 dp, `LazyRow(spacedBy 8)` of compact buttons "Season N" with a
  check icon when the whole season is watched; `selected` = current season.
- Episodes (heading top 20 / bottom 12): `LazyRow(spacedBy 16)` of episode cards, or a placeholder
  80 dp tall: "Loading episodes…", the error in `danger`, or "No episodes found".
- Loading pill top right: "Loading episodes…" body Bold on `panel α0.94` with 1 dp `outline`,
  shape medium, padding 16/8.
- **Episode card**: 208 wide; still 117 dp (≈16:9), clip medium, `surfaceSubtle`, focused 3 dp ring;
  image fallback thumbnail → (selected card) episode backdrop → series backdrop → poster; bottom
  scrim; "S1 E2" caption Bold `focus`; title label Bold; progress 3 dp `focus` (no track) when
  partly watched; watched badge top end; a 2 dp selection rule under the still (`focus` when
  selected); duration caption `textDim`. Focusing a card selects the episode (metadata loads after
  350 ms).
- Focus: first focus on Watch once episodes exist; Down from a season goes to its first episode;
  Up from episodes returns to the active season.

## 5. Match picker ("Wrong details?")

Platform dialog; panel `fillMaxWidth(0.72) × fillMaxHeight(0.86)`, clip large, `panel`, padding
24, spacedBy 14. Title "Choose the right title" (title 28/32 Bold); help "Pick the one this is.
The year and the artwork tell two films of the same name apart."; search row: field "Search by
name" (max 80) + "Search" button (first focus; the search also runs on open). Results: "Searching…",
"Nothing came back. Try a shorter name, or the original one.", or rows: TvSurface padding 10
(focus scale 1.04), thumbnail 44×62 (clip small), title bodyLarge Bold, year label α0.7, overview
caption 2 lines. Footer: "Undo my choice" (when pinned), "Close".

## 6. Trakt title screen

Looks the title up through enabled addons (IMDb id, then `tmdb:<id>`); if found, shows the Discover
title page. Otherwise: padding 32, title 22 sp, "Looking for this title in your sources…" or "This
title is not available from your sources right now.", and "Back to home".

## 7. Cost notes

- The wall's whole partition list is held in memory; "All groups" has no LIMIT (200,000 films).
  Rebuild: page the wall.
- Details pages draw a full-screen gradient, a full-screen image decoded at screen size, and two
  full-screen scrims on every redraw, none cached. Rebuild: decode the backdrop at screen size in
  RGB_565 and bake scrims into one cached layer.
- The wall draws a gradient under every poster (2× overdraw per poster); no per-card layers.
