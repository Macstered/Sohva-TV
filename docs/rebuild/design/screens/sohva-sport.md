# Screen layout: Sohva Sport (Today and the match hub)

> Code-derived values from beta 23 (`efab52a`), extracted 24 September 2026 from
> `sportmate/.../feature/today/*` (TodayScreen, IncidentTimeline, TodaySections,
> TodayEventOrdering, TodayPollingPolicy, TodayViewModel). Behaviour:
> [specs/60](../../specs/60-sohva-sport.md). Older captures:
> `design/screenshots/older-builds/2026-09-02-demo/07-sportmate.png`, `08-match-info.png`.

No literal colours; tokens only (`palette.sports.*` for sport accents). No tabular figures. The
only explicit animation spec is the live badge pulse. Numbers at 960×540 dp.

## 1. Structure

- Today in the chosen time zone only; no date selector.
- `StreamMateScreenBackground` (padding 40/24) → unpadded Box (the hub is full-bleed):
  - Column (made unfocusable while the hub is open):
    - Header; `Spacer(20)`; filter tabs `LazyRow(spacedBy 24)`; `Spacer(22)`;
    - scrolling Column (`weight 1`, bottom padding 18; not lazy — each section row is a LazyRow):
      loading / error / empty card, or sections in fixed order, 26 dp apart: **Live now** → **Later
      today** → **Sports channels now** → **Finished**. Empty sections are not drawn.
  - `AnimatedVisibility(hubOpen)` → match hub.

## 2. Header

Row: "SOHVA SPORT" brand (30 sp); 18 dp; clock (`textDim`, body; pattern `EEEEdMMM` + "  ·  " +
`Hm`/`hmma`; 60 s ticker); three icon actions, 10 dp apart: Refresh ("Refresh sports data"), Guide
("Open the TV guide"), Settings ("Open settings"). Icon action: TvSurface 44 dp, shape small, rest
`surfaceSubtle` / `textMuted`, focusScale 1, icon 20 dp; focus flips to the off-white fill.

## 3. Filter tabs

Order: All, Football, Ice hockey, AFL, Basketball, Baseball, Handball, Rugby, Volleyball, American
football, MMA, Formula 1, NBA, Watchable, Favourites — only All, followed sports, Watchable and
Favourites are shown. Watchable has the Play icon, Favourites the StarOutline icon (16 dp).

Tab: clip small, animated background, padding 10/7; label bodyLarge (Bold when selected); 7 dp;
count caption Bold (events for that filter, all statuses); 5 dp; underline 2 dp.

| State | Fill | Label | Count | Underline |
|---|---|---|---|---|
| Focused | `textPrimary` | `background` | `background α0.62` | `background` if selected |
| Selected | transparent | `textPrimary` Bold | `textDim` | `accent` |
| Neither | transparent | `textMuted` | `textDim` | none |

"The orange rule means selected, the fill means focused: never the same signal."

## 4. Sections

Title headline Bold + 12 dp + hint label `textDim`. Live now and Finished: hint "N games",
`LazyRow(spacedBy 18)`. Later today: hint "This evening", spacedBy 14, same full card. Sports
channels now: "Live broadcasts from the guide", spacedBy 14, channel rows.

## 5. Match card (238 × 224 dp, padding 16)

- Modifiers: size, shadow (16 dp when focused, black, shape large — not animated), clip large,
  animated fill (`textPrimary` focused, `surfaceSubtle` at rest), clickable, focusable, scale 1.03
  on focus inside a graphics layer after the clip (so siblings never shift).
- Row 1: competition logo 18 dp (clip small) + 9 dp; competition name uppercased, caption Bold,
  1.4 letter spacing, 1 line; 10 dp; status badge.
- Row 2: home team, centre (score or kick-off: title size, Black; `focus` when live, `background`
  when focused; score detail such as AFL goals/behinds; status label e.g. the minute), away team.
- Team mark: 48 dp circle (`surface`; `background α0.12` when focused) with initials in the sport
  accent (always drawn) and the crest `AsyncImage(Fit, padding 4)` on top; 8 dp; name label Bold,
  17 sp line, 2 lines, centred.
- Sport accents: ice hockey and football use `focus`; others `palette.sports.{australianFootball,
  basketball, baseball, handball, rugby, volleyball, americanFootball, mma, formulaOne, nba}`.
- Row 3: call to action box (height 38, clip small; `background` focused; `surface` when watchable
  or possible; else transparent): "WATCH · N CHANNEL(S)" (`focus`), "N POSSIBLE CHANNEL MATCH(ES)"
  (`textPrimary`), "No broadcast available" (`textDim`); favourite star 18 dp (`accent`).

Status badge (not live): Upcoming / Finished (`textMuted`), Postponed / Interrupted (`accent`),
Cancelled (`danger`), Unknown status; caption Bold, 1.4 spacing.
Live badge: clip 6 dp, `danger α0.16`, padding 8/3; dot 7 dp `danger`; minute 13 sp Black; "LIVE"
12 sp Black `danger`. Pulse: alpha 1 → 0.25 → 1 twice, 240 ms legs (960 ms), rerun when the minute
changes — finite on purpose.

## 6. Sports channel row (272 × 68 dp)

TvSurface shape medium, rest `surfaceSubtle`, focusScale 1, padding 14. Programme title label
SemiBold; "HH.mm  ·  Channel" caption; "LIVE" in `danger` when live. Only AVAILABLE, not rejected
matches; live first, then start, then name; one row per channel; max 8. OK plays the channel.

## 7. States

Card 180 dp tall, clip 14, `surface`: "Loading today’s games…" / "Connecting to the sports service";
error text + "Check the network connection and API-Sports settings." + "Try again"; empty: "No
selected %1$s games were found for today", "No game has a channel match yet", "No favourites have
been added yet", "No selected games were found for today". Errors while events exist show only in
Settings' status line (rebuild: show them on the screen too).

## 8. Match hub (overlay)

- Enter/exit: fade + slide by a fifth of the width. Not a dialog: one focus root ("a remote has
  no click outside").
- Scrim `background α0.94` full screen. Panel `fillMaxWidth 0.92 × fillMaxHeight 0.88`, clip 20,
  radial gradient `surfaceSubtle` → `surfaceRaised` → `background` (radius 1050 px), padding 22.
- **Header**: left 205 dp (competition logo 58 dp, name 13 sp Black `focus`, status badge, "start ·
  N watchable stream(s)" 12 sp); centre home team, score 36 sp Black (`danger` when live; "–" when
  not started) with detail 14 sp, away team — team: 56 dp circle with 1 dp `outline α0.55` border,
  logo 48 dp, name 17 sp Black; right 150 dp: "Remind me" / "Reminder set" (scheduled and in the
  future) and "Close" (fallback focus).
- Body: Row(spacedBy 16): **Match events** panel (weight 1.12) and **Streams** panel (weight 0.88),
  each clip 14, `surface`, padding 16.
- Match events: Target icon 22 `focus`; "MATCH EVENTS" 17 sp Black `focus`; subtitle "Goals, cards
  and substitutions" (football) / "Match events"; Refresh; divider (10 / 1 dp / 10). Timeline band
  62 dp: axis 2 dp `divider`, half-time tick at 45/span; markers (9 dp dot + time caption Black,
  38 dp wide) above for home, below for away; span = max(90, latest minute). Incident colours:
  goal `focus`, card `accent`, substitution `sports.substitution`, VAR `sports.videoReview`, other
  `textMuted`. List (focusable as a whole, 3 dp ring when focused; Up/Down scroll 120 dp and pass
  through at the ends): rows clip 8, `surfaceSubtle`, padding 12/7; time 15 sp Black; main line
  14 sp SemiBold ("actor → related", "scorer · assist X"); kind line 12 sp.
- Streams: Play icon `accent`; "STREAMS" 17 sp Black `accent`; "Local channel matches"; "N
  watchable" (`focus` when > 0). Rows (clip 10, horizontal gradient by confidence: AVAILABLE
  `surfaceSubtle` → `focus α0.14`, POSSIBLE → `accent α0.14`, REJECTED `surface` → `background`;
  padding 12/9): status dot 6 dp + "AVAILABLE / POSSIBLE / REJECTED / CONFIRMED BY YOU" 12 sp Black;
  source "TV GUIDE" / "M3U NAME"; channel name 15 sp Bold 2 lines; detail (programme title or "Match
  from the M3U channel name"); stream tag chips (resolution PRIMARY, dynamic range ACCENT, frame
  rate and language MUTED); offset explanation ("Programme starts 5 min before the game", "Both
  teams appear in the channel name", …); buttons Watch (available), Confirm (possible, undecided),
  Reject (undecided), Restore (decided). The list scrolls only as much as needed (no re-centring);
  order fixed while the hub is open.
- Focus: first stream row's lead control, else Close (retry up to 90 frames while it animates in);
  after Confirm/Reject/Restore, the row's new lead control; Back closes the hub. (Rebuild: return
  focus to the card that opened the hub.)

## 9. Ordering and polling

- Sort: LIVE, then SCHEDULED, then POSTPONED/INTERRUPTED/UNKNOWN, then FINISHED/CANCELLED; then
  start minute; then competition. First focus: first live, else first upcoming, else first finished.
- Polling: 5 min when anything is live; 10 min when a scheduled match starts within 2 h; else 30
  min. Only while the Today screen or the score ticker is visible and the app is resumed; one
  refresh on return if stale. Channel matching recomputes 250 ms after inputs settle. Cached feeds
  first, then live feeds, sport by sport.
- No local match clock: the minute changes only on refresh.

## 10. Flaws to fix in the rebuild

Later today uses the full card although the design comment says upcoming matches fit a row; live
score colour differs between card (`focus`) and hub (`danger`); the live minute is hard to read on a
focused card and appears twice; text colours switch before fills finish animating; tabs change width
when they turn Bold; the channel time is a fixed "HH.mm"; the hint "This evening" is static; unused
strings (`today_headline`, `empty_hockey`, …) should not be carried over. Cost: the hub adds a
94 % full-screen scrim over the still-drawn list, a radial gradient panel and a gradient per stream
row rebuilt on recomposition — on low-end GPUs use an opaque panel and flat row fills.
