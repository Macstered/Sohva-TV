# Components

> Every reusable TV component of Sohva TV 0.1.0-beta.23 (build 57, `efab52a`), with anatomy, sizes,
> tokens, states, what it costs per frame and how the rebuild draws it cheaply. Read 24 September
> 2026 from `core/.../feature/common/TvUiComponents.kt`, `FocusScroll.kt`, `FocusRequests.kt`,
> `TickerFlow.kt`, `iptv/.../feature/player/PlayerOverlays.kt`, `PlayerDiagnostics.kt`,
> `ScoreTicker.kt`, `iptv/.../feature/common/ChannelDial.kt`, `iptv/.../feature/settings/SettingsComponents.kt`
> and `app/.../addons/*`. Tokens and the focus rules are in [01-design-system.md](01-design-system.md);
> placements on screens in [03-screen-layouts.md](03-screen-layouts.md) and [screens/](screens/).

## 1. Conventions

- Sizes in dp at Interface size Normal. "Text box" heights use the single-line rule of
  [01 §8](01-design-system.md#8-typography): a one-line text is ≈ 1.17 × its size tall
  (12 sp ≈ 14.1, 14 sp ≈ 16.4, 16 sp ≈ 18.8 dp); line height matters only between lines.
- "Inherited" text values come from tv-material's default text style: 16 sp, 24 sp line height,
  Regular, 0.5 sp letter spacing. A component that does not set a weight or letter spacing gets these.
- "Default spring" = damping 1, stiffness 1,500. "Default fade" = `fadeIn()/fadeOut()`, spring
  stiffness 400.
- Every component lists **Cost** (what beta 23 does per frame) and **Cheap** (the rebuild rule).
  Where the look must not change, the rule says so.

## 2. `StreamMateScreenBackground`

- Signature: `(modifier, contentPadding: PaddingValues? = null, content: @Composable (Modifier) -> Unit)`.
  `null` padding means the safe area 40 dp horizontal, 24 dp vertical. The content lambda receives
  `Modifier.fillMaxSize().padding(padding)` and decides where to apply it.
- Anatomy: `Box(fillMaxSize)` → `Canvas(fillMaxSize, graphicsLayer(Offscreen))` drawing the vertical
  gradient and the two radial washes of [01 §11](01-design-system.md#11-the-screen-background) →
  content.
- Users: every screen except the player, the profile picker (flat `backgroundBottom`) and the
  pre-routing host. Home, Discover and the launch screen pass padding 0.
- **Cost:** one full-screen offscreen layer per screen instance (8.3 MB at 1,920 × 1,080 ARGB),
  re-rendered on size or theme change, blitted every frame.
- **Cheap:** one shared pre-rendered bitmap per (theme, size, interface factor), optionally half
  resolution, drawn as one image; no layer. Same pixels.

## 3. Brand lock-ups

| Component | Text | Colours | Size | Style |
|---|---|---|---|---|
| `SohvaTvBrand(fontSize = 34.sp)` | `brand_sohva_tv` "Sohva TV" (not translatable) | "Sohva " `textPrimary`, "TV" `focus` | 34 sp default; 22 sp on Home and details pages (`headline` size) | Bold, letter spacing −0.4 sp, 1 line, no soft wrap |
| `SohvaSportBrand` | `brand_sohva_sport` "Sohva Sport" | "Sohva " `textPrimary`, "Sport" `accent` | 30 sp | same |

The accent span starts after the last space. Real text, not an image, "keeps the two brand
lock-ups crisp and readable by accessibility services". The wordmark image (`sohva_wordmark.png`)
is used only on the launch screen ([04 §1](04-icons-and-imagery.md)).
**Cost:** one text layout. **Cheap:** as is; measure once.

## 4. Icons (`TvIcons`)

Resource ids exposed from core so feature modules need not import core's `R`. They "replace the
Unicode glyphs the UI used to render as text". 30 filled glyphs (`ic_tv_*`): Home, Back, Aspect,
Audio, Subtitles, Stats, ChevronRight, ChevronDown, Lock, Link, Key, Refresh, Check, Play, Pause,
Save, Settings, Delete, Close, Channels, Target, Guide, Epg, Info, Search, Replay, Forward, Rewind,
Star, StarOutline. Navigation icons (`ic_sohva_nav_*`, `SohvaNavigationIcons`) and all sizing rules
are in [04 §2](04-icons-and-imagery.md#2-icon-system).

Drawn with `Image(painterResource(id), colorFilter = ColorFilter.tint(contentColour))`; decorative
(`clearAndSetSemantics {}`) when a label is beside them.
**Cost:** vector rasterised per size and cached by the platform; tint per draw. **Cheap:** as is.
Glyph leftovers still rendered as text in beta 23 (replace with icons in the rebuild): "⌕" and "+"
field glyphs (Search screen, channel editor), "●" PIN field glyph, "●/○" track-picker radios,
"✓" Trakt watched tick, "●" selected group prefix in the channel editor, "↑ ↓" reorder buttons,
"→" in "Groups →".

## 5. `tvSurfaceColors` — the focus rule as colours

`tvSurfaceColors(focused, selected = false, enabled = true, danger = false, resting = null,
restingContent = null) → TvSurfaceColors(background, content, secondaryContent)`. First match wins:

| Condition | background | content | secondaryContent |
|---|---|---|---|
| `!enabled` | `resting` ?: transparent | `textDisabled` | `textDisabled` |
| `focused && danger` | `danger` | `textPrimary` | `textPrimary` α0.72 |
| `focused` | `textPrimary` | `background` | `background` α0.62 |
| `selected` | `surfaceFocused` | `textPrimary` | `textMuted` |
| otherwise | `resting` ?: transparent | `restingContent` ?: `textMuted` | `textDim` |

Note that a *selected* danger control that is not focused shows no danger colour here; buttons add
it themselves (§7).

## 6. `TvSurface` — the focusable container

Parameters and defaults: `onClick`, `modifier`, `shape = shapes.small (8 dp)`, `selected = false`,
`enabled = true`, `danger = false`, `resting = null`, `restingContent = null`, `focusRing = false`,
`focusScale = 1.04f`, `focusRequester`, `testTag`, `contentPadding = 0`, `contentAlignment =
CenterStart`, `onLongClick = null`, `content: (TvSurfaceColors) -> Unit`.

Modifier order (it matters):

```
modifier → focusRequester → testTag → onFocusChanged → semantics(mergeDescendants, selected)
→ [long-press key handler] → clickable(indication = null, role = Button, enabled)
→ graphicsLayer(scale) → shadow(14 dp if focused, shape, black ambient + spot) → clip(shape)
→ background(animated) → [border 3 dp textPrimary if focusRing && focused] → padding(contentPadding)
```

- `LocalContentColor` is set to `colors.content` for the content.
- Ring mode: colours are resolved with `focused && !focusRing`, so the fill and content keep their
  resting values and only the ring shows focus ("flipping the fill as well would hide the artwork it
  frames and leave dark ink on a dark ground").
- Long press: OK / Enter / NumPadEnter; the first key-down with `repeatCount ≥ 1` fires once and
  the key-up after it is consumed.
- Animations: background colour default spring; scale default spring; content colour is **not**
  animated (text flips on the first frame while the fill is still fading — sohva-sport.md lists this
  as a flaw).
- States: rest / focused / selected / selected+focused (focused wins) / disabled / danger /
  ring-focused.
- **Cost (focused):** a RenderNode transform, an elevation shadow (ambient + spot), a rounded clip,
  and ≈ 10 recompositions while the fill animates. At rest: a rounded clip and one fill.
- **Cheap:** no shadow (except the large cards named in [01 §16.1](01-design-system.md#161-hard-rules)
  rule 6); fill drawn with `drawRoundRect` from a colour read in draw; clip only when the content
  holds an image; scale in a `graphicsLayer {}` lambda; one primitive for every focusable so the
  default indication never appears. In reduced mode fill and scale jump.

## 7. `TvActionButton`

`TvActionButton(label, onClick, modifier, icon = null, testTag, enabled = true, focusRequester,
compact = false, danger = false, selected = false)`. Borderless at rest on the ladder; "on focus it
flips to the off-white fill with near-black content". "`selected` is a state, not focus."

| | Compact | Normal |
|---|---|---|
| Padding (h × v) | 12 × 7 | 18 × 11 |
| Icon | 16 dp + 7 dp gap | 18 dp + 9 dp gap |
| Label | `caption` size 12 sp, Bold | `label` size 14 sp, Bold |
| Height, with icon / without | ≈ 30 / ≈ 28 dp | ≈ 40 / ≈ 38 dp |
| Shape | small (8 dp) | small |

| State | Fill | Content | Scale | Shadow |
|---|---|---|---|---|
| Rest | `surface` | `textPrimary` | 1 | none |
| Focused | `textPrimary` | `background` | 1.03 | 10 dp |
| Selected (unfocused) | `surfaceFocused` | `focus` | 1 | none |
| Danger (unfocused) | `surface` | `danger` | 1 | none |
| Danger focused | `danger` | `textPrimary` | 1.03 | 10 dp |
| Disabled | `surfaceSubtle` | `textDisabled` | 1 | none |

- Width wraps content; callers stretch it with `fillMaxWidth` (options sheets, pickers, library
  manager dialogs). Content is centred.
- Fill **and** content colours animate with the default spring; scale default spring.
- Primary actions that must stand out without being white use `TvSurface` with `resting =
  surfaceRaised` instead (Home "Guide" button 46 dp tall, details actions 48 dp tall, padding 22, icon
  18–20 + 10 + label `body` Bold) — see [screens/home-and-shell.md §4.4](screens/home-and-shell.md)
  and [screens/movies-and-series.md §2](screens/movies-and-series.md).
- **Cost:** as `TvSurface` plus a second animated colour (content) that recomposes the label.
- **Cheap:** same as `TvSurface`; label colour via a colour producer read in draw.

## 8. `TvListRow`

`TvListRow(label, onClick, modifier, icon = null, trailing = null, supporting = null, selected =
false, enabled = true, dense = false, divider = false, labelLines = 1, focusRequester, testTag)`.
"Rows are separated by a hairline rather than by a box each, the selected one is marked by an accent
bar and weight, and the focused one by the fill flip."

Anatomy: `Box(fillMaxWidth, onFocusChanged(hasFocus))` holding
1. hairline (only when `divider && !focused`): full width, 1 dp, inset `md` 12 dp both sides,
   `divider`; drawn at the top edge of the row, over it;
2. `TvSurface(fillMaxWidth, focusScale 1, padding md 12 × sm 8, shape small)` → `Row`:
   - selected: bar 3 × 22 dp, clip small, `focus` (or `background` while focused) + 12 dp;
     otherwise a 15 dp spacer (labels line up);
   - icon 18 dp tinted `content` + 8 dp;
   - `Column(weight 1)`: label (`dense` → `label` 14/19; normal → `body` 16/23), **Bold when
     selected, Medium otherwise**, `labelLines` lines, ellipsis; `supporting` caption 12/16,
     1 line, `secondaryContent`;
   - `trailing`: 8 dp + caption 12 sp Medium, 1 line, `secondaryContent`.
- Colours: `TvSurface` rules with resting transparent → `textMuted` / `textDim`; selected →
  `surfaceFocused` + `textPrimary`; focused → off-white fill with a 14 dp shadow.
- Heights (derived): dense ≈ 34 dp with an icon, normal ≈ 35 dp; both ≈ 38 dp when selected (the
  bar is 22 dp). Settings forces 41 dp on its rail rows by modifier (the surface keeps its own
  height inside, at the top of that box).
  `labelLines = 2` in the catalogue and Discover setup rails.
- Users: guide group rail (dense), Settings rail, catalogue rails, player quick actions, reminder
  dialogs (dense), programme actions dialog (dense), Discover setup rails, subtitle-language list,
  catalog order list, time-zone list.
- **Cost:** a `TvSurface` per row plus a hairline node. **Cheap:** one draw per row (fill, bar,
  hairline in one `drawBehind`), no shadow, no clip.

## 9. `TvUrlField`

`TvUrlField(value, onValueChange, label, modifier, testTag, leadingIcon: String? (glyph),
leadingIconRes: Int?, keyboardType = Uri, visualTransformation = None, editOnClickOnly = false,
compact = false)`. Used for every text entry: addresses, keys, PINs, searches, names.

| | Compact | Normal |
|---|---|---|
| Shape | small (8 dp) | medium (12 dp) |
| Padding | 12 × 8 | 16 × 14 |
| Text size | `label` 14 sp (inherited weight Regular) | `body` 16 sp |
| Glyph size (text glyph) | 22 sp Bold | 18 sp Bold |
| Drawn icon | 18 dp | 18 dp |
| Icon/glyph gap | 8 dp | 12 dp |
| Height (derived, text only) | ≈ 32 dp | ≈ 47 dp |

The display text is a tv-material `Text` (inherits 0.5 sp tracking); the editor is a
`BasicTextField` whose style is only `TextStyle(color, fontSize)`, so typed text has no inherited
tracking or weight — a slight difference between showing and editing a value.

**Inline mode** (`editOnClickOnly = false`): a `BasicTextField` in the box; fill `surface`, text
`textPrimary`, placeholder = `label` in `textMuted`, cursor `focus`, IME action Done (hides the
keyboard). There is no focus look other than the cursor (Search screen flaw; the rebuild gives it
the display-mode focus fill).

**Edit-on-click mode** (every Settings field and most others): the box is a focusable button.
- Display: value (after `visualTransformation`, so passwords show dots) or the label as a hint;
  1 line. Rest: fill `surface`, text `textPrimary`, hint and icon `textMuted`. Focused: fill
  `textPrimary`, text `background`, hint `background` α0.62, icon `background` α0.72, shadow 13 dp.
  No scale, no animation.
- OK / Enter / DPAD centre / tap opens the **edit dialog**: platform `Dialog`; `Box(fillMaxSize)`
  with `scrim` α0.72 → centred `Column(fillMaxWidth 0.62, clip large 18, panel, border 2 dp focus,
  padding 24)`: label as title (`headline` 22 sp, Bold) + 14 dp + the editor box (same shape and
  padding as the field, fill `surface`). Focus goes to the editor and the soft keyboard is shown.
- Enter or IME Done or dismiss: keyboard hidden, dialog closed, focus returned to the display box
  (up to 6 frame attempts, `FOCUS_RESTORE_ATTEMPTS`).
- **Cost:** negligible at rest. The dialog is a separate window (platform dim + 72 % scrim).
- **Cheap:** as is; build the dialog only while editing (beta 23 does).

## 10. `TvTagChip`

A small non-focusable fact ("`4K`, `50 FPS`, `HDR`, `IMDb 7.8`"). "Tinted rather than outlined."
Text `caption` 12/16 Bold, 1 line; padding 8 × 3; clip small; fill = tone colour α0.14 with text in
the tone colour, except LIVE, which is filled.

| Tone | Text | Fill | Used for |
|---|---|---|---|
| `PRIMARY` | `focus` | `focus` α0.14 | quality ("4K UHD"), resolution |
| `ACCENT` | `accent` | `accent` α0.14 | "×N" copies, dynamic range |
| `MUTED` (default) | `textMuted` | `textMuted` α0.14 | frame rate, language, stream tags in the player |
| `RATING` | `rating` | `rating` α0.14 | "TMDB %1$s" |
| `LIVE` | `textPrimary` | `danger` (opaque) | `guide_live` "LIVE" |

Height ≈ 20 dp. **Cost:** clip + fill + text. **Cheap:** `drawRoundRect` behind the text, no clip.

## 11. Settings components

Full values in [screens/settings.md §3–5](screens/settings.md). Summary: `SettingsOverline`
(uppercase overline Bold 1.4, `textDim`, padding start 14 / top 18 / bottom 8); `SettingsRow` (not
focusable; hairline inset 14; min height 74; padding 14 × 10; icon 20 `textDim` + 14; title
`bodyLarge` Bold 1 line; subtitle `label` `textDim` 2 lines; trailing controls after 14 dp);
`SettingsValueRow` (a `TvSurface`, shape medium, transparent at rest, content `textPrimary`, min 74,
value `body` Bold + 6 dp + chevron 18 dp); `SettingsSwitch` (outer `TvSurface` radius 15, padding 5,
selected = checked; track 52 × 30, knob 24 dp, travel 22 dp; OFF track `surfaceRaised`, knob
`textMuted`; ON track `focus`, knob `background`; disabled track `surface`; track colour and knob
offset animate with the default spring); `SettingsGroup`; picker rows. The Discover setup pages
reuse these (`AddonSetupPage` §19).

## 12. Cards and tiles

Cards are defined with their screens; this table points to them and adds the Discover cards, which
no screen extract covers.

| Card | Size | Focus | Defined in |
|---|---|---|---|
| Catalogue poster card | grid cell ≈ 88.7 dp, 2:3, clip medium | 3 dp ring, no scale | [screens/movies-and-series.md §1](screens/movies-and-series.md) |
| Home landscape / poster cards | 186 × ≈153; 124 × ≈229 | ring + 14 dp shadow | [screens/home-and-shell.md §4.6](screens/home-and-shell.md) |
| Home sport card / recent channel card | 244 × 160 / 168 × 104 | fill flip | ″ |
| Guide channel cell / programme cell / channel logo | 232 × 44; timeline cells; 30 dp logo | fill (channel), selection fill (programme) | [screens/guide.md §2](screens/guide.md) |
| Match card / sports channel row | 238 × 224 / 272 × 68 | fill flip + 1.03 + 16 dp shadow / fill flip | [screens/sohva-sport.md §5–6](screens/sohva-sport.md) |
| Details artwork card, version card, episode card, cast member | 104 × 156; 208 wide; 208 wide; 84 wide | ring + 1.05 child layer; fill; ring; not focusable | [screens/movies-and-series.md §2, §4](screens/movies-and-series.md) |
| Search result row | full width × 68 | fill flip | [screens/home-and-shell.md §6](screens/home-and-shell.md) |
| Profile tile | 180 wide, avatar 110 | ring, 1.04 | [screens/home-and-shell.md §5](screens/home-and-shell.md) |

### 12.1 Discover poster card (`AddonPosterCard`)

- `Column(width set by caller: 126 dp landing rows, 112 dp search rows, grid cell ≈ 133 dp)`:
  focus requester, `onFocusChanged`, content description = title, `clickable` (default indication in
  beta 23).
- Art box: `fillMaxWidth`, aspect 2:3, clip medium (12 dp), vertical gradient `surface` → `background`
  under the image. Focused: 3 dp `textPrimary` border, drawn twice in beta 23 (as a modifier and again
  as an overlay box "above the image, inside the measured card bounds").
- No poster or failed load: the title centred, padding 16, `textMuted`, `label` size, up to 4 lines,
  centre-aligned (no initials).
- Image request: 256 × 384 px, inexact precision, crossfade 120 ms, Crop.
- Progress (Trakt or resume): 4 dp `focus` bar at the foot, width × fraction, no track.
- Watched: `TraktWatchedBadge` top end, padding 6: 22 dp circle `focus` with "✓" in `background`,
  caption size.
- Caption (`showCaption`, off on the landing rows): title padding top 7, height 20, `label` size,
  `textPrimary` focused / `textMuted`, 1 line; facts "year · rating" caption `textDim`.
- **Cheap:** one ring, no indication, RGB_565 decode at 256 × 384 px, no crossfade in reduced mode.

### 12.2 Discover episode card

`TvSurface(width 230, focusRing, focusScale 1, shape small)`: box 130 dp tall (≈ 16:9): image
(thumbnail ?: series background, Crop); gradient transparent → `scrim` α0.85; label "S1 · E2 · title"
(`addon_ui_season_short` "S%1$d", `addon_ui_episode_short` "E%1$d") `label` size, 2 lines, padding
10, colour = the surface's content colour (`textMuted` at rest); Trakt progress 4 dp `focus`; watched
badge top end.

### 12.3 Discover cast member

Not a button but focusable: column 92 dp wide, padding 4, border 2 dp (`focus` when focused,
transparent otherwise) with 8 dp corners; avatar 52 dp circle `surface` with initials (`label` size
Black `textMuted`) under the photo (Crop); name `label` SemiBold `textPrimary` 2 lines centred,
padding top 8; character caption `textDim` 1 line, top 2. Row `LazyRow(spacedBy 10, padding 4)` under
a "Cast" heading (`headline` Bold). Series pages show a single line "Cast: a, b" (`series_cast_line`)
instead. The rebuild makes the border a standard 3 dp `textPrimary` ring (owner decision, [01 §17](01-design-system.md#17-lessons-from-the-current-app)).

### 12.4 Discover source card

`TvSurface(fillMaxWidth, resting surface, restingContent textPrimary, default shape small, default
focus scale 1.04, 14 dp shadow)`: column padding 14, spacedBy 6: stream name (inherited 16 sp) 2 lines;
description 5 lines; "Play" (`player_play`) or "Unsupported transport". All texts take the content
colour, so the whole card inverts on focus.

### 12.5 Placeholder tiles and initials

- Initials rule: [04 §3](04-icons-and-imagery.md#placeholder-initials).
- Discover loading shelf: six 126 dp 2:3 tiles; the first is a focusable ring `TvSurface`
  (`addon_ui_loading_named` as its description), the others plain `surface` rounded-medium boxes.
- Player channel tile (`PlayerArtwork`): clip small, `surfaceRaised`, logo `Fit`, or the first two
  characters uppercased, 18 sp Black `focus`.

## 13. Progress bars

| Where | Height × width | Track | Fill | Notes |
|---|---|---|---|---|
| Home hero | 4 × 210 | `surfaceRaised` | `focus` | |
| Home cards | 3 × card | `background` α0.62 (landscape) / content α0.20 (channel) | `focus` | stays cyan on the white focused card |
| Guide hero still | 3 × still | `textPrimary` α0.18 | `focus` | clipped by the 18 dp corners |
| Guide programme cell | 3 at the foot | none | `focus` | drawn after the content |
| Catalogue details | 4 × 220 | `surfaceRaised` | `focus` | "Watched %1$s of %2$s" / "%1$s left" |
| Catalogue episode card | 3 | none | `focus` | |
| Discover poster / episode | 4 | none | `focus` | |
| Player (`PlayerProgressTrack`) | box 13 dp; track 5 dp | `textPrimary` α0.20, clip small | `focus`, clip small | thumb 13 dp circle `textPrimary` at `(width − 13) × fraction` |

**Cost:** 2–3 nodes each with clips. **Cheap:** one `drawBehind` per bar (rects/round rects); the
player thumb moves by drawing, not by re-laying out an offset child every 500 ms.

## 14. Status marks and indicators

- **Live dot:** 8 dp circle `danger` (Home kicker, guide hero); 7 dp inside the live badge; 6 dp in
  hub stream rows.
- **Live badge (match card):** clip 6 dp, `danger` α0.16, padding 8 × 3; dot 7 dp; minute 13 sp
  Black; "LIVE" 12 sp Black `danger`. Pulse: dot alpha 1 → 0.25 → 1 twice, 240 ms legs, re-run when
  the minute changes. Static in reduced mode.
- **Watched badges:** catalogue 26 dp `focus` circle with a 15 dp check in `background`; Trakt/Discover
  22 dp circle with a "✓" character.
- **Buffering indicator** (`PlayerBufferingIndicator`): `Row`, clip large (18 dp), `background` α0.72,
  padding 20 × 14; `Canvas` 24 dp: full circle stroke 3 dp `textPrimary` α0.18 and a 90° arc stroke
  3 dp `focus` rotating 360° every 900 ms (linear, infinite); label `player_buffering` "Buffering…"
  `body` size 16 sp SemiBold `textPrimary`, padding start 14. Shown whenever the player state is
  buffering ("without it a stalled stream and a dead one look identical"). **Cheap:** in reduced mode
  step the arc every 150 ms; never let the infinite animation run at zero duration scale.

## 15. Key hints

Guide only ([screens/guide.md §3](screens/guide.md#3-key-hints)): `Row(padding top 6, spacedBy 18)`;
key chip (clip small, `surface`, padding 6 × 1, caption Bold `textMuted`), 6 dp, label caption
`textDim`. "Only bindings that work are listed." **Cheap:** static text; one draw.

## 16. Dialogs, sheets and pickers

Two kinds exist: **in-screen overlays** (drawn in the screen's tree with a scrim, one focus root —
"a remote has no click outside") and **platform dialogs** (`Dialog`, a separate window with the
framework's dim). Back dismisses all of them.

| Surface | Kind | Size | Fill / shape / border | Padding | Title | Defined in |
|---|---|---|---|---|---|---|
| Options sheet (guide, walls) | overlay, scrim `background` α0.86 | `widthIn(340..420)` | `panel`, large | 24, spacedBy 8 | `headline` Black | guide.md §5, movies-and-series.md §1 |
| Match hub | overlay, scrim `background` α0.94 | 0.92 × 0.88 | radial gradient, 20 dp | 22 | — | sohva-sport.md §8 |
| Track picker / quick actions (player) | overlay, scrim `scrim` α 166/255 | 390–520 × ≤ 520 / 360–480 | `panel` α0.97, large | 20 | 21 sp / 18 sp Black | §18 |
| Text edit (`TvUrlField`) | platform, scrim `scrim` α0.72 | 0.62 wide | `panel`, large, 2 dp `focus` | 24 | `headline` Bold | §9 |
| Single / multi picker | platform | 560 × ≤ 620 | `panel`, medium, 1 dp `outline` | 18 | 18 sp Bold | settings.md §5 |
| Time zone | platform | 640 × ≤ 600 | `surface` | 18 | 18 sp Bold | settings.md §5 |
| Phone setup (QR) | platform | 720 | `panel`, 1 dp `outline` | 20 | 20 sp Bold | settings.md §5 |
| Match picker | platform | 0.72 × 0.86 | `panel`, large | 24 | `title` Bold | movies-and-series.md §5 |
| Programme actions / resume actions | platform | 460 | `surface`, medium | 18, spacedBy 4 | 18 sp Bold | guide.md §6, home-and-shell.md §4.6 |
| Reminder alert | platform | 460 | `surface`, medium | 18, spacedBy 4 | 18 sp Bold | 03 §5.4 |
| Reminder overlay prompt | platform | 520 | `surface`, medium | 18, spacedBy 4 | 18 sp Bold | 03 §5.4 |
| Library-manager menus | platform (default width) | 460 × ≤ 480 | `surface`, 16 dp | 18, spacedBy 5 | — | 03 §5.3 |
| Discover choice (`AddonChoice`) | platform | 480 × ≤ 430 | `panel`, square corners | 24 | 22 sp (inherited Regular) | 03 §4.3 |
| Discover remove-addon confirm | platform (default width) | fill width | `panel`, medium | 24, spacedBy 16 | — | 03 §4.7 |
| Discover subtitle picker | platform, full screen | full | `scrim` α0.88 | 28 | `headline` Bold | 03 §4.9 |
| Discover subtitle sync | platform | ≤ 650, 0.85 wide, top 24 | `panel` α0.96, large | 22 | 16 sp Bold | 03 §4.10 |

- Focus: every dialog requests its first focus with `requestFocusWhenAttached` (§20). Pickers open
  scrolled to and focused on the current value.
- In Original, `surface`-filled dialog cards are 6 % white — nearly transparent over the platform
  dim. The rebuild fills every dialog card with `panel` ([01 §17](01-design-system.md#17-lessons-from-the-current-app)).
- **Cost:** a platform dialog adds a window (its own buffer and a full-screen dim pass). **Cheap:**
  prefer in-screen overlays with an opaque card for the short menus; keep platform dialogs only where
  the IME is involved (text edit) or a document picker returns.

## 17. QR codes

| Where | Drawn size | Bitmap | Content |
|---|---|---|---|
| Settings phone setup | 280 dp, 8 dp white quiet zone | 512 px, ZXing margin 1, black on white | local setup page URL |
| Discover phone setup | 220 dp | 384 px ARGB_8888, ZXing default margin | local pairing URL |
| Stremio import | 200 dp | 384 px | Stremio authorisation URL |
| Trakt sign-in | 168 dp | 320 px | verification URL (with user code shown beside) |

Always black on white, whatever the theme. Beta 23 fills the bitmap with a per-pixel `setPixel`
loop inside `remember` on the main thread (147,456 calls for 384 px). **Cheap:** encode off the main
thread; create a bitmap of one pixel per module (+ margin) and draw it scaled with nearest-neighbour
filtering (`FilterQuality.None`); a few KB instead of 590 KB, same pixels.

## 18. Player overlay pieces

Placement, timing and behaviour are in [03 §3](03-screen-layouts.md#3-player-live-catch-up-and-vod).

| Piece | Anatomy |
|---|---|
| `PlayerChromeScrim` | full-screen vertical gradient: 0 `background` α0.62, 0.16 α0, 0.52 α0, 1 `background` α0.94; faded in/out (default fade) while any chrome is visible |
| Live info wash | behind the live info column only: 0 transparent, 0.42 `playerInfoSurface` α0.16, 1 `backgroundBottom` α0.52 |
| `PlayerIconAction` | `TvSurface` 44 × 44, shape small, rest `surface` / `textPrimary`, focus scale 1 (shadow 14 dp), icon 20 dp centred; content description is the label ("Hide these controls", "Picture: Fit", …); `selected` = on (stats) |
| `PlayerArtwork` channel tile | 64 dp (info bar) or 44 dp (channel list); clip small, `surfaceRaised`; logo `Fit` or two-letter initials 18 sp Black `focus` |
| `PlayerProgressTrack` | §13 |
| Transport button | `TvActionButton` (compact for Back/Picture/Audio/Subtitles/Rewind/Forward; normal for Play/Pause) |
| Channel list row | 66 dp, clip medium, padding 10; number caption Bold end-aligned min 28 + 8; 44 dp tile; name `label` Bold; programme caption |
| Group list row | 52 dp, clip medium, padding 10; name `label` Bold 2 lines; count caption |
| `TrackSelectionOverlay` row | 52 dp, clip small, `surfaceSubtle` (`surfaceRaised` when selected), 3 dp `textPrimary` border when focused, padding 14; radio glyph 13 sp (`focus` when selected or focused, else `textMuted`); label 14 sp (Bold when selected), 2 lines; disabled label `textDisabled` |
| `PlayerQuickActionsOverlay` | normal `TvListRow`s with trailing values and hairlines (divider on all but the first) |
| `ChannelDialOverlay` | text "Channel %1$s" (`dial_channel`) or "No channel %1$d" (`dial_channel_none`); 22 sp Bold `textPrimary` on `panel` α0.94 with medium corners, 1 dp `outline` border, padding 18 × 10 ("the panel colour, not the translucent surface ladder: over a bright picture the ladder's white went unreadable") |
| Seek pill | `seekStepLabel` ("+30 s", "−2 min", U+2212 minus) 22 sp Bold `onScrim`, clip medium, `scrim` α0.6, padding 18 × 8 |
| Stats line (`PlayerStatsOverlay`) | `Row(spacedBy 18)` of readings: optional label `label` size `textDim` + 6 dp + value `label` size Bold `textPrimary`; no panel ("a line rather than a panel") |
| Clock | `headline` size Bold `textPrimary`, device 12/24-hour pattern (`Hm` / `hmma`) in the chosen zone |
| `ScoreTickerOverlay` | `Column(widthIn 300..420, clip medium, panel α0.92, padding 14 × 10, spacedBy 6)`; per match: "home – away" 14 sp Bold `textPrimary` (weight 1, ellipsis) + 12 dp + score or start time 14 sp Black (`danger` if live, else `textMuted`) + (live with score) 8 dp + status 12 sp `textMuted`; empty: `player_score_ticker_empty` 13 sp `textMuted`; max 4 rows (live first, then starting within 3 h) |
| Error / reconnect banner | `Column(bottom-centre, dangerSurface α0.8, padding 20 × 12, centred)`: message in `onDangerSurface` (inherited 16 sp) + normal "Reconnect" button top 8 |

**Cost:** over video every non-transparent UI pixel is blended by the compositor; the scrim is a
full-screen quad. **Cheap:** scrim as two bands; no shadows on player controls; the stats line is
recomposed once per second only while shown; the buffering arc steps in reduced mode.

## 19. Rails and page frames

- **Home rail:** [screens/home-and-shell.md §4.7](screens/home-and-shell.md). Rebuild: animate with a
  transform, not a width.
- **Discover rail** (`RailItem` column): overlays the content at the left edge; width **64 dp**
  collapsed / **218 dp** while it holds focus, switched instantly; fill `background` α0.65 / α0.98;
  padding 8 × 24; `spacedBy 12`. Items: `TvSurface(fillMaxWidth, height 48, restingContent
  textPrimary, default shape small, default scale 1.04, 14 dp shadow)` → `Row(padding h 12,
  spacedBy 14)`: icon 24 dp; label (`label` size 14 sp, inherited Regular, 1 line) only when
  expanded. Order: Home (`home_nav_home`), Library (`settings_section_metadata` "Library"), Search
  (`home_search`), Discover (`addon_title`), Addons & setup (`addon_ui_addons_setup`), spacer, Back to
  home (`addon_back`). Right returns to the last shelf card. **Cheap:** draw the expanded rail as an
  opaque panel (α0.98 → 1, §16.1 rule 4 of 01).
- **`AddonSetupPage`** (Discover setup frame): `Column(fillMaxSize, padding 28)`: header `Row(spacedBy
  20)` of title (`display` 40 sp Black, inherited 24 sp lines, weight 1) and a compact back button
  (Back icon); `Spacer(18)`; content. `AddonSetupNote`: `label` 14/19 `textDim`.
- **Settings frame:** [screens/settings.md §1–2](screens/settings.md).

## 20. Focus and scroll helpers (`FocusRequests.kt`, `FocusScroll.kt`)

- **`FocusRequester.requestFocusWhenAttached(attempts = 30)`**: each attempt waits one frame, then
  calls `requestFocus()` inside `runCatching`; returns true on the first success. 30 frames is
  "roughly half a second". It replaced fixed 80 ms sleeps, which "never landed in UI tests" and lost
  races with lazy-list layout. Use it for every initial focus and every focus return.
- **Bring-into-view policies.** On TV, Compose moves every newly focused child to 30 % of its
  scroll container ("even when the child is already completely visible"), which made sideways moves
  scroll whole pages. `KeepVisibleBringIntoViewSpec` scrolls 0 when the child is fully visible or
  larger than the viewport; otherwise by the smaller distance to bring the leading or trailing edge
  in. Wrappers: `KeepFocusedChildVisibleScrollBehavior { }`, `KeepFocusedChildVisibleColumn`,
  `KeepFocusedChildVisibleLazyColumn(state, …) { inheritedBehavior -> }`; nested horizontal rows opt
  back into the 30 % pivot with `InheritedFocusScrollBehavior(inheritedBehavior) { }`.
- **`Modifier.scrollsToTopWhenFocused(offset, scrollToTop)`**: when focus enters the group, wait one
  frame (so Compose's own bring-into-view runs first) and scroll to the top if `offset() != 0`; it adds
  a `focusGroup`. Used where a brand, breadcrumb or title sits above the first focusable (Home, movie
  and series pages). Requires one of the keep-visible containers.
- **`tickerFlow(periodMillis, emitImmediately = true)`**: emits on a delay running on
  `Dispatchers.Default` so Compose test clocks can go idle; collected on the main thread. Periods in
  use: 60 s (clocks, progress), 30 s (player EPG), 1 s (stats), 500 ms (VOD position, a plain loop).
  Rebuild: align minute tickers to the minute boundary.

## 21. Per-frame cost summary

| Component | Nodes at rest | Extra when focused | Rebuild target |
|---|---|---|---|
| Screen background | 1 offscreen layer | — | 1 bitmap draw, shared |
| `TvSurface` | clip + fill | layer transform, shadow, ~10 recompositions | fill in draw, no shadow, no recomposition |
| `TvActionButton` | clip + fill + text | as above + label recomposition | as above |
| `TvListRow` | surface + hairline | as `TvSurface` | one draw pass |
| Poster cards | clip + gradient + image | 2 borders + indication | 1 ring, RGB_565 image |
| Guide row (7 cells) | ~7 clips + alpha layers | — | 1 canvas |
| Player chrome | full-screen gradient + text | — | 2 bands |
| Match hub | 94 % scrim + gradient panel + gradient rows over the live list | — | opaque panel; list not drawn |

## 22. Lessons from the current app

- The lift must live inside the focus target or lazy rows shift on every press (`TvSurface`,
  `TvActionButton`, artwork card comments).
- Fixed focus delays fail under test clocks and on cold lists; retry on frames instead (§20).
- The TV pivot scroll policy scrolls whole pages on sideways moves; use keep-visible containers for
  vertical screens and restore the pivot for rows.
- Periodic UI tickers written as `while(true) delay()` in `LaunchedEffect` made screens untestable;
  run the delay upstream of `flowOn(Dispatchers.Default)`.
- The dial panel moved from the translucent ladder to `panel` α0.94 because white-on-ladder was
  unreadable over bright video.
- Text glyphs as icons render with unpredictable metrics; several remain (§4) and should become
  vectors.
- Components that hand-roll clip → background → border → `onFocusChanged` drift from the system
  (track picker, channel editor, library manager, cast). Everything focusable goes through one
  primitive in the rebuild.

## 23. Open questions

1. Settings rail rows are forced to 41 dp while the `TvListRow` surface inside measures ≈ 35 dp
   (≈ 38 selected) and sits at the top of that box; confirm on a device whether the gap below the
   focus fill is visible, and pick one height.
2. The platform default dialog width for `Dialog` with default properties (text-edit dialog,
   library-manager menus, Discover confirmations) is not set in code; its value on Android TV is
   unverified.
3. `ChannelDialOverlay` and the stats line are both anchored top-start in the player
   (40, 24) and would overlap if both show; confirm and move one (03 §3.10, §3.14).
4. The ZXing default quiet zone for the Discover QR codes (no margin hint passed) is assumed to be
   the library default of 4 modules — confirm.

## 24. Reference: current code map

- `core/.../feature/common/TvUiComponents.kt` — `StreamMateScreenBackground`, brands, `TvIcons`,
  `TvActionButton`, `TvUrlField`, `tvSurfaceColors`, `TvSurface`, `TvListRow`, `TvTagChip`.
- `core/.../feature/common/FocusRequests.kt`, `FocusScroll.kt`, `TickerFlow.kt` — focus and ticker
  helpers.
- `iptv/.../feature/common/ChannelDial.kt` — dial rules and overlay.
- `iptv/.../feature/player/PlayerOverlays.kt`, `PlayerDiagnostics.kt`, `ScoreTicker.kt`,
  `SeekStepper.kt` — player pieces.
- `iptv/.../feature/settings/SettingsComponents.kt`, `SettingsGroups.kt` — Settings vocabulary.
- `app/.../addons/AddonPosterCard.kt`, `AddonArtwork.kt`, `AddonCast.kt`, `AddonSetupPage.kt`,
  `AddonDiscoverScreen.kt` (`RailItem`), `AddonFilterDiscoverScreen.kt` (`AddonChoice`) — Discover
  pieces.
- `iptv/.../feature/catalogue/CatalogueDetailComponents.kt` — artwork card.
