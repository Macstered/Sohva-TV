# Screen layouts

> Index of every screen and overlay of Sohva TV 0.1.0-beta.23 (build 57, `efab52a`), with full
> layout sections for the player overlays, Discover and the smaller screens that have no extract of
> their own. Read 24 September 2026 from `iptv/.../feature/player/*` (PlayerScreen, PlayerOverlays,
> PlayerDiagnostics, ScoreTicker, SeekStepper, SubtitleAppearance, PlaybackReconnectBackoff),
> `iptv/.../feature/common/ChannelDial.kt`, `app/.../addons/*Screen.kt` and helpers,
> `iptv/.../feature/settings/ParentalPinScreen.kt`, `ChannelEditorScreen.kt`,
> `LibraryManagerScreen.kt`, `app/.../feature/legal/LegalInformationScreen.kt`,
> `app/.../app/ReminderAlertDialog.kt`, `ReminderOverlayPromptDialog.kt`,
> `app/.../trakt/TraktSettingsPanel.kt`. Tokens: [01-design-system.md](01-design-system.md);
> components: [02-components.md](02-components.md). Behaviour lives in the specs named per section.

## 1. Conventions

- Reference canvas 960 × 540 dp (1080p, density 2, Interface size Normal). Other sizes scale by
  density ([01 §14](01-design-system.md#14-interface-size)).
- Heights marked "≈" are derived with the single-line text rule of
  [01 §8](01-design-system.md#8-typography) (a one-line text is ≈ 1.17 × its size); they are not
  device measurements.
- Colours are token names; "inherited" text is 16 sp Regular with 0.5 sp tracking.
- "Unverified" marks anything read from code but not seen on a device or screenshot.

## 2. Index

| Screen / overlay | Where | Summary |
|---|---|---|
| Launch screen | [screens/home-and-shell.md §2](screens/home-and-shell.md#2-launch-screen) | Window gradient `#080C15 → #05070D → #04060A` with the 188 dp mark and 273 × 56 dp wordmark; the Compose frame adds the two washes. Always the Original palette. |
| App host, window, back stack | [screens/home-and-shell.md §1, §3](screens/home-and-shell.md#3-host-and-screen-background) | No app-wide scaffold or transitions; destinations switch with a plain `when`. Global reminder overlays sit above everything. |
| Home | [screens/home-and-shell.md §4](screens/home-and-shell.md#4-home) | Faded backdrop art box (0.66 × 0.56 of the screen), hero band 46 % tall, rows below, an overlay rail 80 ↔ 244 dp. |
| Profile picker ("Who is watching?") | [screens/home-and-shell.md §5](screens/home-and-shell.md#5-profile-picker) | Flat ground, 34 sp title, 180 dp tiles with 110 dp colour avatars, ring focus. |
| Search | [screens/home-and-shell.md §6](screens/home-and-shell.md#6-search) | Field, status line and a 68 dp result list grouped by type; 250 ms debounce. |
| Live TV guide | [screens/guide.md](screens/guide.md) | 136 dp hero, 232 dp channel column and a 3-hour timeline, 200 dp group rail, options sheet, programme actions dialog, channel dial. |
| Movies and Series walls | [screens/movies-and-series.md §1](screens/movies-and-series.md#1-the-wall-movies-or-series) | 216 dp rail of groups/genres and a six-column 2:3 poster grid; options sheet. |
| Movie / series details, match picker, Trakt title | [screens/movies-and-series.md §2–6](screens/movies-and-series.md#2-shared-details-components) | Full-bleed backdrop with scrims, 40 sp title, facts, actions, versions, cast, seasons and episode cards; "Wrong details?" dialog. |
| Settings (all sections, pickers, QR dialog) | [screens/settings.md](screens/settings.md) | 214 dp section rail, one-list pane of 74 dp rows with hairlines; picker, time-zone and phone-setup dialogs. |
| Sohva Sport (Today and match hub) | [screens/sohva-sport.md](screens/sohva-sport.md) | Filter tabs, sections of 238 × 224 match cards, sports-channel rows; full-screen hub overlay. |
| Player: live, catch-up, VOD | §3 here | Picture plus chrome: info bar or transport controls, channel list, pickers, info line, ticker, dial, banners. |
| Discover (addons) | §4 here | Landing rows, catalog grid, title pages, search, library, addons & setup, import, subtitle picker and timing, playback loading, player. |
| PIN screens | §5.1 here | Locked channel, profile switch and settings gates. |
| Channel editor | §5.2 here | Two panels: 350 dp channel list and an editor pane. |
| Library manager | §5.3 here | Groups column 290 dp and a content column; menu dialogs. |
| Reminder dialogs | §5.4 here | Alert (460 dp) and first-time overlay prompt (520 dp). |
| Legal screen | §5.5 here | Stacked `surface` sections with links. |
| Trakt panel | §5.6 here | Settings › Accounts: status, connect, QR pairing. |

## 3. Player (live, catch-up and VOD)

Behaviour: [specs/30-player.md](../specs/30-player.md) and
[specs/31-remote-button-mapping.md](../specs/31-remote-button-mapping.md). The same screen serves
live channels (chrome = **live info bar**) and catch-up or films (chrome = **transport controls**):
transport controls show when a VOD content key is set or when both catch-up start and stop are set.
Older capture of the live chrome: `design/screenshots/older-builds/2026-09-02-demo/06-live-player.png`.

### 3.1 Tree and z-order (bottom to top)

`Box(fillMaxSize, background Color.Black)` — "video and letterboxing stay black independently of the
interface theme":

1. `AndroidView(PlayerView)`: `useController = false`, focusable (it owns the remote keys while no
   chrome has focus), resize mode from §3.9, subtitle style from §3.16.
2. In picture-in-picture nothing else is drawn (§3.17).
3. Demo only: a full-screen preview image (Crop) instead of video.
4. Chrome scrim (§3.3), faded in while the info bar, the transport controls or the channel list is
   visible.
5. Clock (top end), only while playback info is on (§3.10).
6. Channel dial (top start, §3.14).
7. Live info bar (bottom start, §3.4) — live only.
8. Transport controls (bottom start, §3.5) — catch-up and VOD only.
9. Buffering indicator (centre, §3.11).
10. Playback info line (top start, §3.10).
11. Seek feedback pill (bottom centre, §3.15).
12. Score ticker (top end, §3.13).
13. Channel list and groups pane (centre end, §3.7).
14. Track picker (full screen, §3.8).
15. Quick actions (full screen, §3.9; only when no track picker is open).
16. Error / reconnect banner (bottom centre, §3.12).

Chrome insets: **40 dp left and right, 24 dp top, 28 dp bottom** (not the 24 dp safe bottom).

### 3.2 Before playback

While the media controller connects or after it fails: `PlayerMessage` — full-screen `background`
fill, the message centred (inherited 16 sp `textPrimary`): `player_connecting` "Connecting to
playback service…" or the (redacted) error; a normal "Back" button at top start with padding 24.
Back leaves.

### 3.3 Chrome scrim

Full-screen vertical gradient: 0 → `background` α0.62; 0.16 → α0; 0.52 → α0; 1 → `background`
α0.94. "Transparent through the middle … near-black along the foot where the programme and the
controls sit, and a light darkening along the top." Default fade in and out. Not drawn when no chrome
is visible, "so an untouched player is the picture and nothing else".

### 3.4 Live info bar (`LiveProgrammeInfoOverlay`)

Placement: bottom start, padding 40 / 40 / 28; full width 880 dp. Column with its own wash:
vertical gradient 0 transparent → 0.42 `playerInfoSurface` α0.16 → 1 `backgroundBottom` α0.52.

| # | Row | Content and style | Height ≈ |
|---|---|---|---|
| 1 | Channel | channel tile 64 dp (§ [02 §18](02-components.md#18-player-overlay-pieces)), 16 dp, column: channel name `headline` 22 sp **Black** `textPrimary`, 1 line; row (top 6, spacedBy 8): `LIVE` chip (`guide_live`), one `MUTED` chip per stream tag read from the channel name (e.g. "HD", "50 FPS"), group title `label` size `textMuted` 1 line | 64 |
| 2 | Programme title | top 12; `title` 28 sp **Black**, letter spacing −0.3, 1 line, width 0.62 (545.6 dp); "No programme information" (`player_no_current_programme`) when no EPG | 45 |
| 3 | Facts | top 8; left (weight 1): start time `HH:mm` · "%1$d %% watched" · "%1$d h %2$d min left" / "%1$d min left" joined "  ·  ", `body` 16 sp `textMuted`; right: stop time `HH:mm`, same style, "over the right end of the bar, which is the point on it that the time refers to" | 27 |
| 4 | Progress | top 8; `PlayerProgressTrack` full width (track 5 dp, thumb 13 dp) | 21 |
| 5 | Next | top 8; "Next %1$s · %2$s" (`player_next_programme`), `label` size `textDim`, width 0.62 | 24 |
| 6 | Stream name | top 4; caption `textDim`, width 0.62; only when it differs from the channel name | 18 |
| 7 | Actions | top 12; focus group, `spacedBy 10`: 44 dp icon actions — Back ("Hide these controls"), Aspect ("Picture: %1$s"), Audio ("Audio: %1$s"), Subtitles ("Subtitles: %1$s"), Channels ("Channel list"), Stats ("Stream details", `selected` while on), Settings ("Quick actions"), and Forward ("External player" / "Opening…", disabled while busy) when an external player is available | 56 |

Total ≈ 237 dp (≈ 255 with the stream line): the bar spans from ≈ 275 dp to 512 dp, inside the
scrim's dark foot (which starts at 52 % = 281 dp). Rows 3–4 appear only when a current programme
exists; the percentage only while it is running.

Visibility: shown on tune and on every interaction, hidden after `PLAYER_CONTROLS_TIMEOUT_MILLIS`
= **5,000 ms** unless the action row holds focus ("a box somebody is working their way along is not
idle"); programme rollovers and EPG refreshes do not re-show it. Up/Down while it is open move focus
into the action row (after an 80 ms delay). Disabled while the channel list is open. Times use
`HH:mm` in the chosen time zone (note: the guide uses `HH.mm`).

### 3.5 Catch-up and VOD transport controls (`BottomTransportControls`)

Placement: bottom start, padding 40 / 40 / 28. "Unboxed, like the live overlay": no panel of its own.

| # | Row | Content | Height ≈ |
|---|---|---|---|
| 1 | Title | channel name (catch-up) or title; `headline` 22 sp Black `textPrimary`, 1 line, width 0.62 | 26 |
| 2 | Buttons | top 10, `spacedBy 8`, compact: "Back" (Back icon), "Picture: %1$s", "Audio: %1$s", "Subtitles: %1$s" | 40 |
| 3 | Progress | top 12; `PlayerProgressTrack` = position / duration | 25 |
| 4 | Transport | top 10, centred: "`mm:ss / mm:ss`" (or `h:mm:ss`) 12 sp `textMuted` + 18 dp; compact "Back %1$s" (Rewind icon); normal "Play"/"Pause" (Play/Pause icon, padding h 10, focus target); compact "Forward %1$s" (Forward icon). %1$s = the skip step ("10 s", "2 min") | 50 |

Total ≈ 141 dp (top at ≈ 371 dp). Visible on entry; auto-hides after 5,000 ms when nothing in it
has focus (Discover's player hides even while focused); held visible while a track picker is open.
OK or Up/Down on the clean picture shows it and focuses Play/Pause after 80 ms. Position refresh
500 ms. The skip size grows while a direction is repeated: ladder 10 s → 30 s → 1 min → 2 min, one
rung per 3 presses within 1,200 ms of each other, never below the chosen step.

### 3.6 Programme info key

The "programme info" and "show controls" remote actions (OK press in the default mapping) re-show
the live info bar (and focus the transport controls on catch-up/VOD); an unmapped press also shows
the chrome. There is no separate programme-details panel in beta 23: programme
metadata is looked up 350 ms after a programme starts, but the info bar does not display it
(unverified intent; see Open questions).

### 3.7 Channel list (`ChannelBrowserOverlay`)

Placement: centre end, full height; default fade. A **key-driven** list: the player view keeps
focus and moves a selection index, and the caller scrolls the list in the same key press "or the
highlight paint[s] on its new row a frame or two before the list caught up".

- **Channel pane** 330 dp wide ("wide enough for a long custom name … next to a logo"), full
  height; horizontal gradient `background` α0 → α0.82 at 0.22 → α0.97 at 1; padding start 28, end
  20, top 24, bottom 20. Header row (padding start 10, bottom 12): "CHANNELS" (`player_channels`
  uppercased) overline Bold `textDim`, weight 1; "Groups →" caption `textDim`. `LazyColumn(spacedBy 2,
  bottom padding 14)` keyed by channel id.
- **Row** 66 dp, clip medium, padding h 10: number (when channel numbers are on; `channelNumber` else
  position + 1) caption Bold, end-aligned, min width 28 + 8 dp; 44 dp channel tile; 12 dp; name
  `label` Bold 1 line; current programme caption 1 line (or "No programme information").

  | Row state | Fill | Name | Number / programme |
  |---|---|---|---|
  | Selected, list active | `textPrimary` | `background` | `background` α0.7 / α0.62 |
  | Selected, groups pane active | `surfaceFocused` | `textPrimary` | `textDim` |
  | Other | transparent | `textPrimary` | `textDim` |

- **Groups pane** (Right opens it; it appears to the right of the channel pane, pushing the list
  left): 220 dp wide, horizontal gradient `background` α0.76 → α0.97, padding start 18, end 12, top
  24, bottom 20; header "GROUPS" overline Bold `textDim` (padding start 10, bottom 12); rows 52 dp,
  clip medium, padding h 10, spacedBy 2: name `label` Bold 2 lines; count caption (padding start 8).
  The selected row is filled `textPrimary` with `background` text (count α0.62); others transparent,
  `textPrimary` / `textDim`.
- Visible rows at 960 × 540: ≈ 6.9 channel rows and ≈ 8.7 group rows (the code assumes 7 and 10
  until the list has laid out once).
- Scrolling: the first visible row = max(0, selected − rowsOnScreen / 2), so the highlight walks to
  mid-screen and then the list carries it.
- Keys: Up/Down move (wrapping at the ends); OK tunes the selected channel (if different) and
  closes; Right opens groups; Left or Back closes. In groups: Up/Down, OK switches the list to that
  group (selection = current channel if it is that group, else 0) and closes the pane, Left/Back
  closes the pane. The list always opens on the playing channel's group; a group chosen inside is
  temporary until a channel is tuned from it.

### 3.8 Audio and subtitle pickers (`TrackSelectionOverlay`)

Full-screen `scrim` at α 166/255 (≈ 0.65), card centred: `widthIn(390..520)`, `heightIn(max 520)`,
clip large, `panel` α0.97, padding 20.

- Header row (space between): title 21 sp Black `textPrimary` — "Select audio track" /
  "Select subtitles"; compact "Back" button (Back icon).
- Hint `player_track_picker_hint` "All available tracks are shown in this list", 12 sp `textMuted`,
  padding top 4, bottom 12.
- `LazyColumn(spacedBy 8, bottom padding 4)` of 52 dp rows ([02 §18](02-components.md#18-player-overlay-pieces));
  ≈ 7 rows fit. Subtitles start with "Off" (`player_subtitles_off`). Empty lists: a disabled,
  selected "No audio tracks" or a disabled "No subtitles" row.
- Track labels: format label · display language · "N ch", de-duplicated and joined " · ", or
  "Track %1$d".
- Opens scrolled to and focused on the selected track; choosing closes it and returns focus to the
  transport controls (VOD) or the picture (live). Back closes.

### 3.9 Picture shape and quick actions

- Picture shapes cycle **Fit → Fill → Zoom** (`player_resize_fit/fill/zoom`), from the Aspect action,
  the quick-actions row or a mapped key; the label shows as "Picture: %1$s". (Discover's player cycles
  Fit → Zoom → Fill — inconsistent, fix in the rebuild.)
- **Quick actions** ("the menu behind a long press on OK"; OK held or Menu in the default mapping,
  and the Settings icon in the info bar): full-screen
  `scrim` α 166/255; card `widthIn(360..480)`, clip large, `panel` α0.97, padding 20, `spacedBy 8`;
  title "Quick actions" 18 sp Black; normal `TvListRow`s with trailing values and hairlines: "Audio
  track" (current), "Subtitles" (current), "Picture" (Fit/Fill/Zoom — cycles in place: "shape is
  judged by looking at the picture"), "Playback info" (Shown/Hidden), "Score ticker" (Shown/Hidden;
  only where the ticker is available). First row focused; Back closes. Height ≈ 275 dp with five rows.

### 3.10 Playback info panel (stats line and clock)

Toggled by the Stats action, the quick-actions row or a mapped key (Info press in the default
mapping — specs/31).
- **Line** at top start (40, 24): readings separated by 18 dp, sampled every 1 s:
  resolution "1920×1080 50p", codecs "H264 · AAC 5.1", bitrate "%.1f Mb/s", "Subtitles" + MIME type,
  "Buffer" + "%.1f s", "Dropped" + count (only where the player can count). Labels `label` size
  `textDim`, values `label` size Bold `textPrimary`. Unmeasured values are left out, never shown as 0.
- **Clock** at top end (40, 24): `headline` size Bold `textPrimary`, `Hm` or `hmma` by the device's
  24-hour setting, chosen time zone.

### 3.11 Buffering

`PlayerBufferingIndicator` centred on the screen whenever the player reports buffering ([02 §14](02-components.md#14-status-marks-and-indicators)):
pill 18 dp corners, `background` α0.72, padding 20 × 14, 24 dp arc + "Buffering…". ≈ 52 dp tall.

### 3.12 Error and reconnect message

Bottom centre, flush with the bottom edge (no inset, no corner radius): `dangerSurface` α0.8,
padding 20 × 12, centred column: message in `onDangerSurface` (inherited 16 sp) and a normal
"Reconnect" button (top 8). Messages: `player_reconnecting` "%1$s · Reconnecting %2$d/%3$d…"
(attempt of 3 with Standard recovery, 8 with Persistent), `player_reconnect_stopped` "%1$s ·
Automatic reconnection stopped", `player_external_failed` "Could not open the external player:
%1$s". Delays: Standard 2 s × attempt; Persistent 2, 4, 8, 16, 30, 30, 30, 30 s. ≈ 89 dp tall. It
overlaps the info bar or transport controls when both show (unverified on device).

### 3.13 Score ticker

`ScoreTickerOverlay` at top end, padding end 40 and top **24 dp**, or **72 dp** while the playback
info clock is shown ("below the clock … so the two never overlap"). Panel `widthIn(300..420)`,
clip medium, `panel` α0.92, padding 14 × 10, rows spacedBy 6; at most 4 matches (live, then those
starting within 3 h). Toggled from quick actions or a mapped key; reads the sport screen's state and
costs no extra request.

### 3.14 Channel dial

`ChannelDialOverlay` at **top start** (40, 24) in the player (top end in the guide). Digits 0–9 and
numpad (live only): up to 4 digits; commits 2,000 ms after the last digit; "No channel %1$d" for
1,500 ms. Resolves the channel's own number first, then list position, across the whole guide in its
own order. No animation.

### 3.15 Seek feedback

After each skip: "+30 s" / "−2 min" pill, bottom centre, 120 dp above the bottom; 22 sp Bold
`onScrim` on `scrim` α0.6, clip medium, padding 18 × 8; shown for 900 ms.

### 3.16 Subtitles on the picture

Media3's `SubtitleView` inside `PlayerView`. Viewer choices (Settings › Playback): size Follow the TV
/ Small ×0.8 / Normal ×1 / Large ×1.3 / Very large ×1.6 of `SubtitleView.DEFAULT_TEXT_SIZE_FRACTION`
(embedded sizes ignored unless following the TV); colour Follow the TV / White `#FFFFFFFF` / Yellow
`#FFFFE14D`; background Follow the TV / None / Shadow (drop shadow edge `#FF000000`) / Box
(`#CC000000`). When every choice follows the TV, the system caption style is used whole. Bitmap
subtitles keep their own colours.

### 3.17 Picture-in-picture

In PiP only the picture is drawn; the chrome returns with full screen.

### 3.18 Timings

| Constant | Value |
|---|---|
| Chrome idle timeout | 5,000 ms |
| Focus hand-off to chrome | 80 ms |
| Seek feedback | 900 ms |
| Position refresh | 500 ms |
| Stats sample | 1,000 ms |
| EPG refresh tick | 30,000 ms; EPG read window: 60 min back, 7 h span, bucketed to 30 min |
| Programme metadata lookup | 350 ms after the programme changes |
| Dial | 2,000 ms commit, 1,500 ms message, 4 digits |
| External player release | 150 ms |

### 3.19 Cost notes and flaws

- Over video every visible chrome pixel is composited over the video layer. Draw the scrim as two
  bands (top 16 %, bottom 48 %); the live info wash adds a second translucent layer over the same
  area — fold it into the bottom band in the rebuild.
- Two fades run at once when the chrome appears (scrim and bar), each ≈ 0.33 s; in reduced mode both
  are instant.
- The buffering arc forces a frame every vsync over the video; step it in reduced mode.
- Flaws: dial and stats line share the top-start corner; the error banner can overlap the bottom
  chrome; the fallback `PlayerChromeOverlay` exists only in tests (do not carry it over); picture-shape
  order differs from Discover's player; guide and player time formats differ (`HH.mm` / `HH:mm`).

## 4. Discover (addons)

Behaviour: [specs/50-discover-addons.md](../specs/50-discover-addons.md). Opened from Home's rail
(only when addons are allowed and the profile is unrestricted; otherwise `addon_access_denied` "Addons are unavailable for restricted profiles." and a
"Back to home" button). Older captures: `design/screenshots/older-builds/2026-09-10-discover-lab/*`.
Every Discover page sits on `StreamMateScreenBackground(contentPadding = 0)`; sub-pages are drawn
**instead of** the landing page (not on top of it), and Back returns to the page that opened them
with focus restored.

### 4.1 Frame, backdrop and rail

- **Backdrop** (`AddonBackdrop`, full screen, landing and title pages): fill `background`; the
  focused title's background image, Crop, aligned top end, image-loader default crossfade (140 ms);
  horizontal gradient `background` α0.98 → α0.75 → α0.2 (stops 0, 0.5, 1); vertical gradient
  transparent → `background` α0.35 → `background` (0, 0.5, 1). No image → the ground only.
- **Rail** ([02 §19](02-components.md#19-rails-and-page-frames)): left edge, over the content; 64 dp
  / 218 dp wide while focused; `background` α0.65 / α0.98; six items 48 dp tall. Entered with Left
  from the first card of any row (or from an empty row's button); Right or Back returns to the last
  focused card. Focusing an icon never opens anything; OK does.

### 4.2 Home rows (landing)

```
Box
├ AddonBackdrop(hero.background)
├ Column(padding start 84, end 24, top 24)
│ ├ AddonHero  width 0.66 (562 dp) × height 0.35 of the screen (189 dp)
│ ├ status texts (loading / failure / empty), inherited 16 sp
│ └ LazyColumn(weight 1; contentPadding top 8, bottom 0.65 × height = 351 dp; spacedBy 18)
│   ├ "Continue watching" row
│   └ one row per landing catalog
└ Rail (64 / 218 dp)
```

- **Hero** (`AddonHero`, spacedBy 10): logo box up to 88 dp tall — the title's logo image 320 × 88
  (Fit, start-aligned), or while there is no logo (or it failed) the title in `display` 40 sp Black
  `textPrimary`, 2 lines; the logo's space stays empty while it loads ("rather than flashing a
  plain-text title"). Facts (`AddonFacts`): type ("Movie"/"Series"), release, runtime, up to two
  genres joined " / ", "IMDb x" — joined "  ·  ", `body` 16 sp `textMuted`, 2 lines. Description
  `label` 14/19 `textMuted`, 3 lines. Idle title "Discover" (`addon_title`).
- **Hero timing:** focusing a card sets the hero after the cached or fetched details arrive
  ("debounced focused-title synopsis"); Continue-watching cards apply after 120 ms without a network
  call; when no title is focused (an empty row's button) the hero clears after 120 ms; while the rail
  has focus the hero keeps its last title.
- **Row** = `Column(spacedBy 8)`: title (catalog name or "Continue watching") `body` 16 sp
  `textPrimary`, 1 line; optional "Showing saved titles; provider unavailable." and failure texts;
  `LazyRow(contentPadding 8, spacedBy 12)` of poster cards **126 dp wide (126 × 189)** without
  captions; ≈ 6 cards visible; a row block is ≈ 232 dp tall.
- Continue-watching cards show a 4 dp progress bar; landing cards show Trakt progress and a watched
  tick. An empty history shows one normal button: "Loading watch history…" / "History unavailable" /
  "Nothing to continue yet".
- Loading rows show six 126 × 189 placeholders (§ [02 §12.5](02-components.md#125-placeholder-tiles-and-initials)).
  Loaded rows end with a normal "Show all" button that opens the catalog grid.
- Up/Down move a whole row: the list scrolls that row to the top of the viewport and focus lands on
  its **first** card (vertical spatial search is disabled so an off-screen index can never be picked).
  Empty: "No visible catalogs. Open Addons & setup to show catalogs or add your services." or "Use
  Search for titles or Discover for filtered catalogs on the left."
- Loading: the active row and the next two request their catalog (20 items); the next row's first six
  posters are pre-decoded by two workers; at most 24 rows are kept (LRU).

### 4.3 Catalog grid (Show all, Discover filters)

`Column(fillMaxSize, padding 28, spacedBy 12)`:
1. Title: catalog name, or "Discover" for the filter page — `headline` size 22 sp (inherited Regular).
2. Choices row (only for catalogs with required choices, and on the Discover filter page):
   `LazyRow(spacedBy 12)` of `AddonChoice` compact buttons "Label: value" — Type 160 dp and Catalog
   240 dp wide (filter page), each option-type filter 230 dp ("Select…" when required and unset,
   "Default" otherwise).
3. Buttons `Row(spacedBy 12)`, normal size: "Back to addons" (first focus), "Catalog filters" (filter
   page with text filters), "Refresh titles" (disabled while loading).
4. Messages: "Working…", "Showing saved content; the addon is currently unavailable.", error text,
   "Choose the required filters above to load titles.", "No titles match this catalog or filter."
5. Grid: `GridCells.Adaptive(132 dp)`, content padding 8, spacing **20 vertical, 18 horizontal**;
   at 960 dp → **6 columns of ≈ 133 dp**; poster cards with captions (poster 133 × 200, card ≈ 241 dp
   tall, pitch ≈ 261 dp); ≈ 1.5 rows visible below ≈ 116 dp of header. Footer spanning the grid:
   "Loading more titles…", "Retry loading titles", or "Showing 1,000 titles. Use search or filters to
   narrow this catalog."
6. Text filters (instead of the grid while open): `LazyColumn(spacedBy 8)` of edit-on-click fields
   (label + " *" when required, max 1,024 characters) and an "Apply" button.

Paging: the next page loads when the last card is laid out; one request at a time; stops at 1,000
titles or when a page adds nothing. **`AddonChoice` dialog:** platform dialog, 480 × ≤ 430 dp, `panel`,
square corners, padding 24; title 22 sp; `LazyColumn(spacedBy 8)` of full-width normal buttons, the
current option `selected` and focused.

### 4.4 Title pages

**Movie and episode page** (`AddonPlayableDetails`): backdrop (episode thumbnail, else the title's
background); `Row(padding 32, spacedBy 28)`:

- Left, weight 0.56 (≈ 486 dp), `LazyColumn(spacedBy 16)`:
  - episode pages: series name `body` `textMuted`;
  - title `display` 40/44 Black, 3 lines;
  - facts (top 12) or "Season %1$d · Episode %1$d" `textMuted`;
  - synopsis (top 16) `body` 16/23 `textMuted`, 7 lines, or "No synopsis supplied by the addon.";
  - "Loading title information…", "Full details unavailable. %1$s";
  - actions `Column(spacedBy 10)`: `Row(spacedBy 12)` of the primary normal button with Play icon —
    "Continue watching" (resume known) or "Find sources" (moves focus to the sources) — and, on
    movies, the compact library toggle ("Add to library" / "Remove from library" / "Retry library";
    Library or Check icon; `selected` when saved; failure text in `danger` caption); compact "Start
    from beginning" (Replay icon) when a resume point exists; "Waiting for a playable source…";
    compact "Retry details";
  - cast: movies the 52 dp portrait row, series the one-line "Cast: …".
- Right, weight 0.44 (≈ 382 dp), **sources** (`AddonSourceCards`, `spacedBy 12`): header row "Sources"
  + compact "Refresh" (Refresh icon); full-width compact "Scraper: All" choice; list (`spacedBy 12`)
  grouped by provider in addon priority order: provider name, "Working…", error, "No sources
  returned.", then source cards ([02 §12.4](02-components.md#124-discover-source-card)). "Finding
  sources…" while loading. Continue waits for every provider, then plays the first HTTP source in
  priority order.

**Series page:** backdrop; `Column(padding 32, spacedBy 12)`: header column **42 % of the height ×
72 % of the width** (spacedBy 10): name `display` Black 2 lines, facts, description `label` 14/19
`textMuted` 4 lines, "Cast: …" line; "Loading episodes…"; "Retry episode details"; `Row(spacedBy 12)`
of the compact library toggle and `LazyRow(spacedBy 10, padding 4)` of compact season buttons
("Season %1$d", "Specials" for 0, "Episodes" when unnumbered; `selected` = shown season; first focus);
episodes `LazyRow(padding 8, spacedBy 16)` of 230 × 130 dp episode cards
([02 §12.2](02-components.md#122-discover-episode-card)). OK on an episode opens its episode page;
Back returns focus to that card. Next episode autoplays when the setting is on.

### 4.5 Search

`Column(padding 28 × 20, spacedBy 10)`:
- Header row: "Search" 22 sp `textPrimary` (weight 1) + compact "Back to catalogs".
- Field row (`spacedBy 12`): compact edit-on-click `TvUrlField` "Search movies and series" with the
  Search icon (weight 1, first focus, max 256 characters); compact "Search" (runs the search; not
  as-you-type); compact "Clear".
- Status caption `textMuted`: "Enter a title, then choose Search.", "Searching catalogs · %1$d /
  %2$d", partial-failure and saved-results notes, "Searching the first %1$d catalogs in your saved
  order." (32 catalogs max).
- Results `LazyColumn(padding 4, spacedBy 16)`: two rows, "Movies" and "Series" (16 sp
  `textPrimary`), each a `LazyRow(spacedBy 12, padding 4)` of **112 dp** poster cards with captions
  (up to 100 per row, then "Showing 100 results…"). Down from the field goes to the first non-empty
  row; Up from Movies returns to the field.

### 4.6 Library

`Column(padding 28, spacedBy 12)`: header "Library" 22 sp + compact "Back to catalogs" (first focus
when nothing to restore); compact filter buttons All / Movies / Series (`selected`); "Loading saved
titles…", error + "Retry library", empty texts; grid as §4.3 (Adaptive 132, padding 8, spacing
20/18) of captioned poster cards. A title whose addon is gone opens a platform dialog (`panel`,
padding 24, spacedBy 12): message, normal "Close" (focus), compact "Remove from library".

### 4.7 Addons & setup

`AddonManagerSettings`: `Column(padding 28)`:
- Header: "Addons & setup" `display` 40 sp Black (weight 1) + compact "Back to catalogs" (Back icon,
  first focus); `Spacer(18)`.
- `Row(weight 1, spacedBy 24)`: left column **200 dp** (`spacedBy 2`) of normal `TvListRow`s
  (2-line labels): Installed addons (Channels icon), Add / import (Settings), Catalogs (Guide; opens the
  catalog page), Subtitles (Subtitles), Watch history (Play). Right: `LazyColumn(weight 1, bottom
  padding 24)` built from the Settings vocabulary ([screens/settings.md §3–4](screens/settings.md)):
  - **Installed:** overline "Installed addons · N"; per addon a `SettingsRow` (name; "N catalogs ·
    Enabled/Disabled"; Channels icon; trailing switch) and a row of compact buttons (padding start 16,
    bottom 14, spacedBy 8): Catalogs, Refresh, Priority (moves it up; disabled for the first), Remove
    (danger). Then info rows and "Reload saved addons".
  - **Add / import:** overline; info row about separate addon data; value row "Import addons" →
    "Open"; overline "Manual setup"; info row (Lock); masked edit-on-click URL field (full width,
    padding 16, max 16,384 characters); normal "Install".
  - **Subtitles:** overline; switch row "Show all languages"; info rows for preferred languages and
    where to set them; metadata-language note.
- Busy "Working…" `textMuted`; failures `danger`.
- Remove confirmation: platform dialog, `panel`, medium corners, padding 24, spacedBy 16: "Remove
  %1$s?" text, normal "Cancel" (focus), danger "Remove addon".
- **Catalog page** (`AddonCatalogOrderScreen`, `AddonSetupPage` "Catalogs"): `Row(spacedBy 24)`;
  left 200 dp (`spacedBy 6`): overline "Catalog settings", rows "Reorder" (Guide) and "Show / hide"
  (Channels) with `selected` = mode, notes; right: list of divider `TvListRow`s (supporting "Addon ·
  Movie · Home/Discover/Hidden/Addon disabled", trailing position or "Moving · N", `selected` while
  moving) or, in Show / hide, `SettingsRow` + switch per catalog; help note (top 12). Move mode: OK
  picks up, Up/Down/Page Up/Page Down/Home/End move without saving, the second OK saves, Back cancels.
- **Watch history** and **an addon's catalogs** are plain lists (padding 28, spacedBy 12) of normal
  buttons with inherited-style texts — the only unstyled pages left in Discover (rebuild: use the
  setup-page frame and list rows).

### 4.8 Import, phone and Stremio pages

- **Import** (`AddonSetupPage` "Import addons" / "Review import" / "Import result", back "Back to
  addons"): `Row(spacedBy 24)`; left 200 dp (`spacedBy 4`): overline "Import methods", rows "Account &
  phone" (Link), "From a file" (Save), "Manual URL" (Key), note "Up to 32 addons per list…"; review:
  overline and notes. Right: busy/message notes; `LazyColumn` of value rows per method (Copy from
  Stremio account → Open; Set up from phone → Open; Choose text file → Browse; Choose file on phone →
  Open QR; Nuvio JSON → Browse) or the manual field (masked, edit-on-click, padding 14) with compact
  "Add URL to list" (Check); review rows "Addon N" with values Include / Skip / Added / Kept /
  Unavailable and a Check / Close / Lock chevron (addon names and URLs are never shown). Footer: 1 dp
  `divider` rule; count (16 sp) and a note; compact "Preview import" (Check) + "Clear list", or
  "Install N" + "Start another list".
- **Phone setup** (`AddonSetupPage` "Phone setup", back "Back to import"): scrolling column
  (`spacedBy 16`): notes; `Row(spacedBy 28)`: QR **220 dp** + column (`spacedBy 16`): "Scan with your
  phone" 22 sp, the local pairing address (`label` size; a private-network URL such as
  `http://192.0.2.10:port/…`), a note. The session exists only while the page is in the foreground.
- **Stremio account** (`AddonSetupPage` "Stremio account"): before: overline and two info rows;
  while waiting: `Row(padding v 12, spacedBy 28)`: QR **200 dp** + column (`spacedBy 12`): "Scan with
  your phone" 22 sp, the authorisation address (`body` size), two notes; then two fixed notes, the
  message, `Spacer(12)` and a compact button "Start Stremio authorization" / "Waiting for
  authorization…" (Link icon). The window is marked `FLAG_SECURE` while this page is open.

### 4.9 Subtitle picker (Discover player)

Platform dialog, full screen, `scrim` α0.88; `Column(padding 28, spacedBy 12)`:
- Header: "Subtitles" `headline` Bold `textPrimary` over "Appearance follows Sohva's VOD subtitle
  settings." caption `textMuted`; compact "Back to player" (first focus).
- `Row(weight 1, spacedBy 24)`: left **200 dp**: "Language" `label` `textMuted`; `LazyColumn(spacedBy
  6, padding 3)` of dense `TvListRow`s — "Subtitles off" (`selected` when none), then languages
  (preferred first, then by name; trailing count; `selected` = shown language). Right (weight 1):
  heading (language name or "Available subtitles") `label` `textMuted`; `LazyColumn(spacedBy 8,
  padding 4)` of `TvSurface` rows (full width, scale 1, padding 14, `selected` when current): provider
  `body` Bold 1 line; "Language · detail" caption 2 lines; "✓ Selected" `label` on the current one.
  "Finding subtitles…", provider errors (caption), "Checking subtitle providers…" while loading.
- Footer: loading and error texts; "Timing adjustment is unavailable for this subtitle format.";
  `Row(spacedBy 12)` of compact buttons "Subtitle sync · +0.000 s", "Show all languages" / "Primary /
  secondary only", "Refresh".

### 4.10 Subtitle timing dialog

Platform dialog without a scrim box; panel at top centre: padding top 24, `widthIn(max 650)`, 0.85 of
the width, clip large, `panel` α0.96, padding 22, `spacedBy 12` — the picture stays visible below.
- Header row: "Subtitle sync" `body` Bold + offset "%+.3f s" `headline` Bold.
- Adjustment surface (`TvSurface`, ring focus, scale 1, padding 16 × 10; first focus): canvas 28 dp
  tall — a 2 dp `textDim` line from 8 dp to width − 8 dp, a centre tick 12 dp tall (2 dp,
  `textMuted`), a 12 dp `focus` knob positioned over −60 s … +60 s; below, "← Earlier" and "Later →"
  captions `textMuted`.
- Help caption "Left / right: 0.1 s · Hold: 1 s · OK or Apply to preview. Playback may briefly
  buffer." (or "Applying timing…", or the failure text).
- Compact buttons (`spacedBy 10`): "Apply", "Reset to zero", "Play"/"Pause", "Back to subtitles".
  "Not applied · Current: %1$s" caption while the draft differs.
- Left/Right step 100 ms; 1 s once the key has repeated 8 times; limit ±60 s; applying re-prepares
  the media once (timeout 20 s). Down from the surface always goes to Apply.

### 4.11 Playback loading

`AddonPlaybackLoading`: full screen `scrim` (black); the title's background image (Crop); gradient
`scrim` α0.15 → α0.35 → α0.88; centred column 0.65 wide, `spacedBy 28`: a box 80–170 dp tall pulsing
alpha 0.78 ↔ 1 (1,400 ms) holding the title's logo (170 dp tall, Fit) or, until it loads, the title in
`display` 40 sp Black `onScrim`, centred, 3 lines; stage text `body` `onScrim` centred: "Preparing
stream…" → "Fetching subtitles…" → "Starting playback…". Bottom centre (padding bottom 28,
`spacedBy 16`): compact "Cancel" (focus) and compact "Subtitles". Limits: stream ready 45 s,
subtitles budget 5 s (then embedded fallback), first frame 20 s. Screenshot:
`older-builds/2026-09-10-discover-lab/addon-playback-loading.png`.

### 4.12 Discover player

Black box and `PlayerView` as §3.1; while controls, a failure or a background stop are visible, a
full-screen gradient transparent → transparent → `scrim` α0.95 (stops 0, 0.5, 1). Controls column at
bottom start (40 / 40 / 28, `spacedBy 8`): failure text `onScrim` ("Playback stopped. Retry or choose
another source." or the background-stop text) + compact "Retry with fresh source"; "Watch progress
could not be saved."; `BottomTransportControls` (§3.5, auto-hide even while focused; held while
paused, buffering, failed or a picker is open). While a subtitle dialog is open the controls stay
composed at alpha 0. Audio picker: `TrackSelectionOverlay` titled "Audio" inside a platform dialog.
Left/Right on the clean picture seek; OK/Up/Down show the controls.

### 4.13 Cost notes and flaws

- The backdrop is a full-screen image plus two full-screen gradients on every redraw; bake them into
  one bitmap per title (01 §16.1 rule 10). Backdrop images load at screen size with a 140 ms
  crossfade.
- Landing rows: poster decode 256 × 384 px each; decode RGB_565; skip crossfades in reduced mode.
- The expanded rail at α0.98 should be opaque and stop the rows beneath from drawing through it.
- Discover poster focus draws the ring twice and the default indication; one ring only.
- Texts that set only a font size (titles "Search", "Library", catalog titles) are Regular with 0.5 sp
  tracking; the rebuild keeps that look or normalises it to `headline` Bold (owner decision).
- The history and per-addon catalog lists are unstyled.

## 5. Other screens

### 5.1 PIN screens (`ParentalPinScreen`)

Used for three gates with the same layout: a locked channel (heading `pin_locked_channel` "Locked
channel", name = channel), switching profile (`pin_profile_gate` "Switching profile", name = profile or
"Everyone") and locked Settings (`pin_settings_gate` "Settings are locked for this profile").
`StreamMateScreenBackground` (safe area) → `Column(spacedBy 18, centred horizontally)`:
1. `SohvaTvBrand` 34 sp, aligned start.
2. Heading 40 sp Black (`textPrimary` via content colour).
3. Name 22 sp `focus`.
4. Message 16 sp `textMuted`: "Enter the PIN to continue", "Configure a PIN in Settings first" or
   "Incorrect PIN code".
5. `TvUrlField` inline (not edit-on-click), normal size, width 360 dp, glyph "●", number-password
   keyboard, masked, digits only, max 8.
6. `Row(padding top 8, centred)`: normal "Back" (Back icon) + 12 dp + normal "Unlock" / "Checking…"
   (Check icon; enabled with a configured PIN and ≥ 4 digits).
Wrong PIN clears the field. Initial focus is not requested explicitly (unverified where it lands).
Spec: [specs/04-profiles-parental.md](../specs/04-profiles-parental.md).

### 5.2 Channel editor (`ChannelEditorScreen`)

Spec: [specs/21-channel-management.md](../specs/21-channel-management.md). On the ground with the
safe area; `Column`:
1. `SohvaTvBrand` 34 sp.
2. Row (space between): title "Channel management" 32 sp Black over "Rename, regroup, reorder, hide
   and link channels to the programme guide" 13 sp `textMuted`; normal "Back" (Back icon).
3. `Spacer(10)`; toolbar `Row(spacedBy 8)` of normal buttons "Source: %1$s" (cycles sources and
   All), "Sort: Playlist / A–Z", "Show hidden" (`selected`, shared with the library manager), and an
   edit-on-click search field (glyph "⌕", weight 1).
4. `Spacer(7)`; horizontally scrolling `Row(spacedBy 7)` of compact group buttons, the selected one
   prefixed "● ".
5. `Spacer(7)`; `Row(spacedBy 8)`: edit-on-click "new list" field (glyph "+", weight 1) + normal
   "Create list".
6. `Spacer(10)`; body `Row(spacedBy 12)`:
   - **Channel list panel** 350 dp wide: clip 12 dp, `surface`, 1 dp `outline` α0.56 border, padding 8;
     "N channels" 12 sp `textMuted`; 6 dp; `LazyColumn(spacedBy 4)` of **54 dp** rows: clip 8 dp,
     `surfaceSubtle` (`surfaceRaised` when selected), 3 dp `textPrimary` border when focused (plus
     default indication), padding 8 × 5; number 12 sp Bold `textMuted` min 22 + 6 dp; logo 38 dp
     (`surfaceRaised`, 7 dp corners, image padding 4 or initials Black `focus`); 8 dp; name 12 sp Bold
     (`textMuted` when hidden) over "group or source" 12 sp (`accent` + "  ·  hidden" suffix when
     hidden). **Focusing a row selects it.**
   - **Editor pane** (weight 1, scrolls): clip 12 dp, `surface`, 1 dp `outline` α0.72, padding 16;
     "Select a channel to edit" centred when none. Header: logo 64 dp + 12 dp + name 22 sp Black,
     source 12 sp `focus`, "Original name" 12 sp `textMuted`, status 12 sp `focus`; 12 dp; four
     full-width edit-on-click fields (custom name, custom group, logo URL, number) 8 dp apart; the
     playlist-number note 12 sp; "Logo from phone" and its QR dialog; "EPG mapping" 12 sp Bold, current
     mapping 12 sp `textMuted`, "Change EPG" / "Automatic"; "Custom lists" 12 sp Bold and list
     buttons (list, add/remove, delete); bottom `Row(spacedBy 7)` of normal buttons Save, Show in
     guide / Hide from guide, PIN lock / unlock (or "Configure PIN"), "↑", "↓" (only in playlist sort),
     Reset.

### 5.3 Library manager (`LibraryManagerScreen`)

Spec: [specs/42-library-organization.md](../specs/42-library-organization.md). On the ground with the
safe area; `Column`:
1. Row (space between): "Manage groups & content" 25 sp Bold + normal "Back".
2. `Row(spacedBy 6)` of compact buttons: rooms Live TV / Movies / Series (`selected`), source (cycles
   All sources and each source), filter (All / Enabled / Disabled).
3. `Spacer(6)`; `Row(spacedBy 6)`: edit-on-click search (normal size, weight 1), compact Clear, Group
   sort, Default sort, Bulk, and Advanced (Live TV only).
4. `Spacer(8)`; `Row(weight 1, spacedBy 12)`: **groups** `LazyColumn` 290 dp wide (`spacedBy 4`) and
   **content** column (weight 1): header row (group name Bold, inherited 16 sp; compact "Item sort"),
   empty/automatic help (padding 16, `textMuted`), `LazyColumn(spacedBy 4)`.
5. Footer `Row(padding top 7)`: help 12 sp `textMuted` ("Groups: OK for actions, Right for content ·
   Content: OK enable/disable, Right for actions", or loading/saving/move texts) + compact Retry /
   Undo.

`ManagerRow`: **57 dp**, 8 dp corners, fill `surface` (focused `surfaceFocused`), **2 dp `accent`
border when focused** (plus default indication), padding h 10, `spacedBy 9`: mark "●" (selected in
multi-select), "✓" (enabled, `accent`) or "—" (`textMuted`); optional image 40 dp; title 14 sp
(`textPrimary`, `textMuted` when disabled) over subtitle **10 sp** `textMuted` ("enabled / total",
source name or "automatic view"). Menus (group, item, sorts, bulk, position, confirmation): platform
dialog with default width, card 460 × ≤ 480 dp, `surface`, 16 dp corners, padding 18, scrolling
`Column(spacedBy 5)` of full-width compact buttons. This screen diverges from the system (orange focus,
10 sp text); the rebuild aligns it (owner decision).

### 5.4 Reminder dialogs

Spec: [specs/22-catchup-and-reminders.md](../specs/22-catchup-and-reminders.md). Both are platform
dialogs over whatever is on screen, card `surface` with medium corners, padding 18, `spacedBy 4`:
- **Alert** (`ReminderAlertDialog`, 460 dp): title "%1$s starts in a minute" / "%1$s starts now"
  18 sp Bold `textPrimary`, 2 lines, padding start 6; optional subtitle 13 sp `textMuted`, 1 line,
  padding start 6 / bottom 8; dense rows "Watch" (Play icon; "Open the match card" for sport
  reminders; first focus) and "Not now". Closes itself at start + 2 min, never sooner than 20 s after
  it appeared.
- **Overlay prompt** (`ReminderOverlayPromptDialog`, 520 dp, shown once with the first reminder):
  title "Let reminders open Sohva TV" 18 sp Bold; body 13/18 sp `textMuted` (padding start 6, bottom 8);
  dense rows "Open TV settings" (Settings icon, first focus) and "Not now".
Android TV shows no heads-up popup for notifications, so these in-app dialogs are the visible alert.

### 5.5 Legal screen (`LegalInformationScreen`)

Spec: [specs/72-updates-about-diagnostics.md](../specs/72-updates-about-diagnostics.md). Ground with
the safe area; `Column`:
- Header row (space between): `SohvaTvBrand` 34 sp + column (padding start 24): "About, privacy and
  licences" 28 sp Black `textPrimary` over "Public non-commercial release information" 13 sp
  `textMuted`; normal "Back" (Back icon, first focus).
- `LazyColumn(padding top 18, spacedBy 12, bottom padding 28)` of sections: full width, `surface` with
  medium corners (no clip), padding 20 × 16; title 18 sp Black `focus`; body 13/18 sp `textPrimary`
  α0.88, max width 1,120 dp, padding top 7; actions column (top 12) of compact buttons.
  Sections in order: app name (version, non-commercial note); privacy (five paragraphs); contact
  (compact button opening a `mailto:` support address — [specs/72](../specs/72-updates-about-diagnostics.md));
  data providers (TMDB notice, TMDB logo 205 × 28 dp, 10 dp, "Open TMDB"); "TVmaze · CC BY-SA" (two
  link buttons); "API-Sports" (notice, rights, terms link); service notes (HTTP, metadata and sports
  disclosures); addons; open source (Apache licence link). Footer: no-affiliation note 12/17 sp
  `textMuted`, padding 8 × 4.
- Initial focus uses a plain `requestFocus()` (not the frame-retrying helper) — rebuild: use the
  helper.

### 5.6 Trakt panel (Settings › Accounts)

Spec: [specs/51-trakt.md](../specs/51-trakt.md). Only for unrestricted profiles, inside the Settings
pane: `Column(spacedBy 12)`:
1. `SettingsOverline` "Trakt".
2. `SettingsRow` "Sohva profile: %1$s" (Link icon) with subtitle "Connected as %1$s", "Not
   connected" or the re-authorisation text.
3. Compact primary button: "Connect Trakt" / "Sign in again" / "Cancel sign-in" (first focus;
   disabled when the build has no Trakt client configured, with a note).
4. While pairing: `Row(spacedBy 24)`: QR **168 dp** + column (`spacedBy 8`): "Approve Sohva TV on
   Trakt" `headline` size, "Scan the code with your phone, or open the address and enter the code."
   (16 sp `textMuted`), the verification address (inherited 16 sp), the user code `headline` size.
5. Message (`textMuted`), help note, and when connected a compact danger "Disconnect" with its note.
Back cancels a pairing in progress first; leaving the app cancels it with a message.

## 6. Open questions

1. Programme metadata is fetched for the live info bar (350 ms after a programme starts) but not
   displayed; was a metadata row intended there, or should the lookup go?
2. The channel dial and the stats line both sit at the player's top start (40, 24); the error banner
   sits under the bottom chrome. Confirm on a device and choose new positions.
3. Where does initial focus land on the PIN screens (no explicit request in code)?
4. The subtitle timing dialog has no scrim box but is a platform dialog; whether the framework dims
   the picture behind it (making timing hard to judge) is unverified.
5. Heights derived here assume Roboto metrics (01 §8); confirm on the low-end box's system font.
6. The Discover catalog page's back button reads "Back to addons" even when opened from a landing row
   ("Show all"); keep the text or change it?
7. Should the unstyled Discover pages (watch history, an addon's catalog list) be restyled in the
   rebuild, or kept for identity?

## 7. Reference: current code map

- `iptv/.../feature/player/PlayerScreen.kt` — player tree, keys, timers, pickers, banners.
- `iptv/.../feature/player/PlayerOverlays.kt` — info bar, channel list, transport, track picker, quick
  actions.
- `iptv/.../feature/player/PlayerDiagnostics.kt`, `PlayerFormatting.kt` — buffering, stats line,
  chrome scrim, value formatting.
- `iptv/.../feature/player/ScoreTicker.kt`, `SeekStepper.kt`, `SubtitleAppearance.kt`,
  `PlaybackReconnectBackoff.kt`.
- `iptv/.../feature/common/ChannelDial.kt` — dial.
- `app/.../addons/AddonDiscoverScreen.kt` (landing, rail), `AddonArtwork.kt` (backdrop, hero, facts),
  `AddonCatalogScreen.kt`, `AddonFilterDiscoverScreen.kt`, `AddonDetailsScreen.kt`,
  `AddonSourcesScreen.kt`, `AddonSearchScreen.kt`, `AddonLibraryScreen.kt`,
  `DiscoverAddonFeature.kt` + `AddonManagerSettings.kt` (Addons & setup), `AddonCatalogOrderScreen.kt`,
  `AddonImportScreen.kt`, `AddonPhoneScreen.kt`, `AddonStremioImportScreen.kt`,
  `AddonSubtitlePicker.kt`, `AddonSubtitleSync.kt`, `AddonSubtitleTiming.kt`,
  `AddonPlaybackLoading.kt`, `AddonPlayerScreen.kt`, `DiscoverTitleScreen.kt`.
- `iptv/.../feature/settings/ParentalPinScreen.kt`, `ChannelEditorScreen.kt`,
  `LibraryManagerScreen.kt`; `app/.../feature/legal/LegalInformationScreen.kt`;
  `app/.../app/ReminderAlertDialog.kt`, `ReminderOverlayPromptDialog.kt`;
  `app/.../trakt/TraktSettingsPanel.kt`.
