# Screen layout: launch, app host, Home, profile picker, Search

> Code-derived values from beta 23 (`efab52a`), extracted 24 September 2026 from
> `app/.../app/StreamMateApp.kt`, `MainActivity.kt`, `StreamMateLaunchScreen.kt`,
> `ProfilePickerScreen.kt`, `feature/home/*`, `feature/search/SearchScreen.kt`, the launch
> resources and the core theme. Behaviour: [specs/01](../../specs/01-app-shell-navigation.md),
> [02](../../specs/02-home.md), [03](../../specs/03-search.md), [04](../../specs/04-profiles-parental.md).
> Screenshots: `design/screenshots/beta23-synthetic/home.png`, `home-row-moved.png`.
> All numbers are for Interface size Normal at 960×540 dp.

## 0. Foundations

- **No app-wide rail, scaffold, background or transitions.** The host wraps everything in
  `StreamMateTheme(palette = colorTheme.palette)` and switches destinations with a plain `when`,
  so screen changes are instant. The navigation rail exists only on Home.
- Default tokens (Original palette):
  - Spacing xs 4, sm 8, md 12, lg 16, xl 24, xxl 32; safeHorizontal 40, safeVertical 24.
  - Shapes small 8, medium 12, large 18 (rounded corners).
  - Typography (size/line sp, weight, letter spacing): display 40/44 Black −0.5; title 28/32 Bold
    −0.3; headline 22/27 Bold; bodyLarge 18/25 Normal; body 16/23 Normal; label 14/19 SemiBold;
    caption 12/16 Medium; overline 12/16 Bold +1.4.
  - Palette: background `#05070D`, backgroundTop `#080C15`, backgroundBottom `#04060A`, panel
    `#0A0F1A`; surface ladder of translucent white: surfaceSubtle α0.035, surface α0.06,
    surfaceRaised α0.10, surfaceFocused α0.14; focus `#2DE2E6`, secondaryGlow `#2276D2`, accent
    `#FF8A4C`, danger `#FF3B5C`, textPrimary `#F2F5F9`, textMuted `#93A1B5`, textDim `#5B6981`,
    scrim black. The six other themes replace background, surface, text, focus and secondaryGlow
    and make the surfaces **opaque** (e.g. `surfaceSubtle = lerp(background, surface, 0.5)`).
  - MaterialTheme gets only a colour scheme (primary = focus, background, surface, on* =
    textPrimary). `LocalContentColor` = textPrimary. No custom FontFamily anywhere (system
    sans-serif).
- Home and Search pass token fields one by one (`fontSize = …, lineHeight = …`); weight and
  letter spacing not passed fall back to tv-material's `LocalTextStyle`.
- **TvSurface** (the focusable used almost everywhere): focused → background `textPrimary`,
  content `background`, secondary `background` α0.62; rest → `resting` (default transparent),
  content `restingContent` (default textMuted), secondary textDim. `focusRing = true` keeps the
  fill and draws a 3 dp `textPrimary` border inside the bounds instead. `focusScale` default 1.04
  (spring); background colour spring; focused shadow 14 dp black; default shape small; no press
  indication; `onLongClick` fires on the first key repeat of OK and swallows the release.
- **Interface size** multiplies density, not font scale: Normal 1.0, Compact 0.9, Small 0.8,
  Smaller 0.7 → 960×540, 1066.7×600, 1200×675, 1371.4×771.4 dp at 1080p.

## 1. Activity and window

- Window background `launch_background` for the first two frames, then a flat `#05070D`
  ColorDrawable ("a flat colour costs a tile-based GPU nothing").
- `Theme.StreamMate` (parent `Theme.Material.NoActionBar`): accent `#2DE2E6`, status and
  navigation bars `#05070D`, light status bar off. Landscape, PiP supported.
- Composition: while preferences load, the launch screen with the default palette at device
  density; then `InterfaceScaled { StreamMateApp }` with the saved theme.
- PiP: `onUserLeaveHint` enters picture-in-picture; a close broadcast finishes the activity.

## 2. Launch screen

- `launch_background.xml`: top-to-bottom gradient `#080C15` → `#05070D` → `#04060A`; mark
  188×188 dp centred with bottom inset 74 dp; wordmark 273×56 dp centred with top inset 206 dp.
  Mark centre −37 dp, wordmark centre +103 dp from the screen centre.
- Compose: `StreamMateScreenBackground(contentPadding 0)` → centred column: mark 188 dp,
  `Spacer(18)`, wordmark `height 56` (content description "Sohva TV"). The Compose version adds the
  two radial washes the XML lacks, and always uses the default palette.

## 3. Host and screen background

- Before start-up routing resolves: a flat `backgroundBottom` box (no label: a label would flash).
- Global overlays: reminder overlay-permission prompt, reminder alert (after start-up), a system
  Toast "This profile cannot watch that channel".
- Back: `BackHandler(enabled = backStack.size > 1)`. On Home, Back goes to the system unless the
  rail has focus.
- `StreamMateScreenBackground`: one Canvas in an offscreen layer, repainted only on size/theme
  change: (a) vertical gradient backgroundTop / background (0.5) / backgroundBottom; (b) radial
  `focus` α0.10 → α0.02 (0.6) → transparent, centre (0.12w, −0.10h), radius 0.58w; (c) radial
  `secondaryGlow` α0.12 → α0.02 (0.62) → transparent, centre (0.92w, 0.04h), radius 0.50w.
  Default content padding 40/24. The profile picker uses flat `backgroundBottom` instead.

## 4. Home

### 4.1 Tree

- `StreamMateScreenBackground(contentPadding 0)` → root Box with a focus-enter redirect (entering
  by any direction but Enter re-targets the rows, never the upper-left rail) and `focusGroup`.
  1. `HomeHeroBackdrop` (full screen).
  2. Column `padding(start = 80 + 16 = 96, end = 24)` → content 840 dp wide:
     - Hero band `fillMaxHeight(0.46)` = 248.4 dp, `padding(top 16, bottom 12)`: header (top),
       Trakt first-sync note (top centre), hero panel (bottom start).
     - LazyColumn of rows, 291.6 dp.
  3. `HomeRail` with `zIndex 1`, drawn over everything.

### 4.2 Hero backdrop

- Outer Box in an offscreen layer (picture and scrims re-rendered only when contents change).
- Art box `fillMaxWidth(0.66) × fillMaxHeight(0.56)` at top end → 633.6×302.4 dp (x 326.4–960),
  reaching 54 dp below the band. Its own offscreen layer; after drawing, two erasing masks
  (`DstOut`): vertical (transparent at 45 % → black at 100 %) and horizontal (black at 0 →
  transparent at 40 %). The ground shows through; no seam.
- Inside the art box, bottom to top: `home_backdrop_live_tv.webp` (1920×1080, always the floor);
  for sport heroes a `background α0.6` box with the two crests (150 dp, α0.55, 56 dp apart,
  padding 48); `Crossfade(250 ms)` of the hero `AsyncImage` requested at 1920×1080, `RGB_565`,
  Crop.
- Full-screen horizontal scrim `background` α0.85 (0) → 0.55 (0.4) → 0.05 (0.7) → 0.25 (1); vertical
  scrim α0.5 (0) → 0 (0.3).
- Artwork source: hero backdrop, else the resume entry's backdrop (for local VOD this is the
  poster; for Discover `background ?: poster`); movie/episode metadata backdrops; Trakt TMDB
  backdrop, fanart, poster; channel metadata backdrop/poster; none for sport and welcome.

### 4.3 Header and clock

- Row: `SohvaTvBrand` 22 sp ("Sohva" textPrimary, "TV" focus, Bold, letter spacing −0.4); spacer;
  clock `textMuted`, body 16/23 at the top right.
- Clock pattern: best pattern for `EEEdMMM` + `' · '` + `Hm` or `hmma` following the device's
  24-hour setting, in the chosen time zone. Updated by a 60 s ticker that also drives progress bars
  (no re-queries on the tick). Rebuild: align to the minute.
- Trakt note "Trakt history is waiting for its first sync" (body, top centre, padding top 8).

### 4.4 Hero panel

`Column(fillMaxWidth(0.56) = 470.4 dp)` at the band's bottom start:

1. Kicker row: live dot (8 dp `danger`) + 8 dp; kicker text uppercased, `focus`, overline Bold
   1.4 sp. Kickers: Continue watching / Live now / Live TV / Today's sport / Watch next /
   Recommended for you / Welcome.
2. `Spacer(12)`; title `textPrimary`, display 40/44 Black −0.5, 2 lines (resume title; programme or
   channel; "home – away"; Trakt title; "Live TV" for Welcome).
3. `Spacer(8)`; metadata joined with "  ·  ", `textMuted`, bodyLarge, 1 line (episode label
   "S1 E2 · title", "%d min left", channel name, programme times `HH.mm–HH.mm`, year, competition,
   status, score).
4. Progress: `Spacer(12)`, track 210×4 dp `surfaceRaised`, fill `focus`.
5. Description (resume and Trakt synopsis; Welcome "Channels and programme guide"): `Spacer(12)`,
   `textMuted`, bodyLarge, 2 lines.
6. Welcome only: `Spacer(24)`, "Guide" button (height 46, shape medium, rest `surfaceRaised`,
   content textPrimary, focusScale 1, padding 22; icon 20 + 10 + label body Bold) — primary buttons
   are not white at rest because white means focus.

Hero rules: the focused card's hero applies after 180 ms of rest; with nothing or the rail
focused it returns immediately to the idle hero (first resume with progress, else a recent
channel, else Welcome). Hero text is not animated; only the art crossfades.

### 4.5 Rows

- LazyColumn `focusRestorer`, `focusGroup`, `contentPadding(top 12, bottom 260)` (so the last
  row can be pulled up), `spacedBy 26`. Keys and order: continue-watching, watch-next,
  todays-sport, recommended, recent-channels.
- Vertical pivot: the focused card's rectangle scrolls to the list's top edge (rows travel under
  the hero). Horizontal: the platform's 30 % pivot inside each row.
- While the viewer is below the first row, refreshes keep the order and cards and only update
  values; loading placeholders are suppressed.
- Row header: title `textPrimary`, headline Bold; optional hint `textDim`, label; `Spacer(12)`;
  `LazyRow(spacedBy 12, contentPadding horizontal 4)`.

| Row | Title | Hint |
|---|---|---|
| Continue watching | Continue watching | Browse the rows with the D-pad |
| Watch next | Watch next | — |
| Today's sport | Today's sport | "%d match(es)" |
| Recommended | Recommended for you | — |
| Recent channels | Recently watched channels | — |

### 4.6 Cards (TvSurface, shape medium, focusScale 1)

| Card | Size | Anatomy | Focus |
|---|---|---|---|
| Continue watching / Watch next (landscape) | 186 dp wide; art 178×102; card ≈153 dp tall | art box `surfaceSubtle` with initials (textMuted 22 sp Black) under `AsyncImage(Crop)`; progress bar at the foot (track `background α0.62`); `Spacer(8)`; title label SemiBold; subtitle `textDim` caption (always laid out, α0 when empty) "subtitle · N min left" | 3 dp textPrimary ring + 14 dp shadow; fill unchanged |
| Recommended (poster) | 124 wide; art 116×178; ≈229 tall | same, art `poster ?: fanart` | ring |
| Today's sport | 244×160, padding 12, rest `surfaceSubtle` | status row (live dot + status in `danger`, else secondary; caption Bold); teams row: crest tile 38 dp (`content α0.10`, logo Fit padding 3 or initials) + name caption Bold 2 lines, score bodyLarge Black (`focus` if live), competition caption | fill flip |
| Recent channels | 168×104, padding 12, rest `surfaceSubtle` | logo tile 34 dp; name label Bold; programme caption; progress bar (track `content α0.20`) | fill flip |

Progress bars: 3 dp, clip small, fill `focus` (stays cyan on focused white cards). Limits: sport
and channels 6 cards; resume 12 (one per series). Visible at 960×540: landscape 4 + a sliver,
posters 6, sport 3, channels 4. The expanded rail covers about 144 dp of the first card.

Long-press on a VOD resume card opens an actions dialog (460 dp, `surface` fill, padding 18):
Resume, Start from beginning, Mark as watched, Remove from Continue watching.

States: "Loading Continue Watching…" / "Continue Watching unavailable. Select to retry." (after a
5 s stuck read); empty Home shows the Welcome hero with the Guide button.

### 4.7 Navigation rail

| # | Item | Icon | Condition |
|---|---|---|---|
| — | Home marker (not focusable) | `ic_sohva_nav_front_page` | always, on top |
| 1 | Live TV | live_tv | |
| 2 | Sohva Sport | sport | |
| 3 | Movies | movies | |
| 4 | Series | series | |
| 5 | Search | search | |
| 6 | Discover | discover | addons allowed, not demo, profile not restricted |
| 7 | Who is watching | `TvIcons.Star` | more than one profile |
| 8 | Settings | settings | |

- Width animates 80 ↔ 244 dp (default spring); padding top/bottom 24, start 24, end 8; items
  48 dp tall with 4 dp gaps; icon 24, 14 dp gap to the label; scrolls only when Discover adds an
  item.
- Background: horizontal gradient of `background` at α = 0.70 (collapsed) or 0.97 (expanded),
  0.9× at 72 %, 0 at the edge — the wider, denser rail is its own scrim.
- The rail sits over the content (never beside it); content never re-lays out.
- Home marker: 48 dp row, clip medium, `textPrimary` fill, icon and label in `background` —
  looks like a focused item.
- Items: rest content textMuted; focused fill textPrimary + 14 dp shadow. Labels appear only when
  expanded, instantly.
- Focus: the rail expands while it has focus; focusing it resets the hero; Right from any item
  returns to the rows' restorer; Back returns focus to the rows.

### 4.8 Cost notes (current app)

Two full-screen offscreen layers (~8.3 MB each at 1080p) plus the art layer; the bundled backdrop
always drawn under fetched art; hero bitmaps RGB_565 (~4.1 MB, two during a crossfade); the whole
backdrop layer re-renders on every frame of a crossfade; the rail re-lays out every frame of its
width spring. Rebuild: animate the rail with a transform, not a width; draw the hero backdrop
without per-frame layer re-rendering on low-RAM devices.

## 5. Profile picker

- Flat `backgroundBottom`; centred column, `spacedBy 36`: title "Who is watching?" 34 sp Bold;
  `Row(spacedBy 28)` of tiles, no scroll.
- Tile: TvSurface 180 dp wide, shape medium, `focusRing`, focusScale 1.04, padding 16; column
  `spacedBy 14`: avatar 110 dp circle in the profile colour (teal `#2EC4B6`, amber `#FF9F1C`, red
  `#E71D36`, violet `#7B61FF`, green `#4CAF50`, pink `#F06292`), initial 48 sp Black white; name
  20 sp Bold.
- The active profile's tile gets focus. The default profile is "Everyone". Up to 6 profiles, but
  only 4 fit at 100 % scale — the rebuild must scroll or wrap.

## 6. Search

- Default screen padding. Header: `SohvaTvBrand` 34 sp; "Search" 32 sp Black; subtitle
  "Channels, programmes, movies, series, episodes and sports" 13 sp `textMuted`; Back button.
- Field (`TvUrlField`, top padding 18): `clip(medium)`, `surface`, padding 16/14; text 16 sp;
  cursor `focus`; glyph "⌕"; placeholder "Enter at least two characters". Focus is shown only by
  the cursor (rebuild: give the field a visible focus state).
- Status line 12 sp `textMuted`: Searching… / Search covers all downloaded sources. / Some search
  results could not be loaded. / No results found. / "%d result(s)".
- Results `LazyColumn(spacedBy 7, bottom padding 18)`; row 68 dp, clip small, fill `surface`
  (focused `textPrimary`), padding 10/8; thumbnail 50 dp (`surfaceRaised`, image Fit, or the type
  initial in `focus`); title 14 sp Bold; subtitle 12 sp (programme time `d.M. HH.mm`); type label
  12 sp Black (CHANNEL, PROGRAMME, MOVIE, SERIES, EPISODE, SPORT) in `focus`.
- Timing: focus the field 80 ms after entry; 2 characters minimum; 250 ms debounce; max 80
  characters; results appended group by group so rows already in view never move.
