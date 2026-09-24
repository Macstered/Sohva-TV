# App shell and navigation

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

The shell is the one activity, the start-up sequence and the in-memory destination stack
that every screen lives in. It paints a launch picture that is identical to the window
background, loads local state, applies the saved colour theme and interface size from the
first app frame, decides the start screen (Home, the programme guide or the last channel),
asks who is watching when the household has several profiles, and then shows exactly one
full-screen destination at a time. It owns the rules that decide where Back goes after
playback, the parental and profile gates in front of channels and Settings, the Home
navigation rail, reminder hand-offs from notifications, picture in picture, the background
work that must pause while someone is watching, and the focus conventions every screen
follows. A viewer never "uses" the shell directly, but every press of Back, Home or OK on a
rail item goes through it.

## 2. Feature checklist

- SHELL-01 Sohva TV appears in the Android TV launcher (Leanback launcher entry, 320×180 dp
  banner, adaptive icon, label `app_name` "Sohva TV").
- SHELL-02 Launch picture: the Sohva mark and wordmark on the dark vertical gradient, drawn by
  the window before the first frame.
- SHELL-03 A Compose launch screen identical to the launch picture stays up while local state
  loads, so nothing moves at the hand-over.
- SHELL-04 The saved colour theme is used from the first app frame (no flash of the default
  theme) and no text flashes between the launch picture and the first screen.
- SHELL-05 The whole interface is drawn at the chosen interface size (100, 90, 80 or 70 %);
  the launch picture stays at device density.
- SHELL-06 The chosen interface language is applied before any text is resolved (below
  Android 13 by the app, from Android 13 by the platform's per-app language).
- SHELL-07 Start screen setting: Home (default), Programme guide, or Last channel.
- SHELL-08 Last channel start opens the guide on that channel and plays it; a PIN-locked
  channel asks for the PIN first; a channel that no longer exists falls back to the guide.
- SHELL-09 Who is watching at start when the household has more than one profile and the
  question is switched on (details in [Profiles](../specs/04-profiles-parental.md)).
- SHELL-10 A destination stack: every screen returns with Back to the screen it was opened
  from; Back on Home leaves the app.
- SHELL-11 Live playback started from the guide, a Home channel card, a search result or a
  dialled number returns with Back to the guide, focused on the channel just watched.
- SHELL-12 Live playback started from a Sohva Sport match card or a reminder returns to where
  it started; catch-up playback always returns to the previous screen.
- SHELL-13 Changing channel inside the player replaces the player, so Back never steps back
  through the channels zapped.
- SHELL-14 Zap-back: the player offers the channel watched before the current one (session
  memory).
- SHELL-15 Player shortcuts to Home, the guide, Sohva Sport and "guide at this channel" reset
  the stack to that screen.
- SHELL-16 Resuming a film or episode from Home puts its library page and its details page
  under the player, so Back walks Details, then Movies/Series, then Home.
- SHELL-17 A finished film returns to its details page; a finished episode starts the next one
  (when autoplay is on) or returns to the series page, also when playback began in Search.
- SHELL-18 A Trakt card on Home opens the library's own details page when the title is in the
  library, otherwise the Trakt title page.
- SHELL-19 Returning to Movies or Series restores the browse position; returning to Sohva
  Sport restores the open match card; returning to the guide focuses the channel.
- SHELL-20 Home navigation rail down the left edge: icons only at rest, widening over the
  content with labels when it takes focus.
- SHELL-21 Rail destinations: Live TV, Sohva Sport, Movies, Series, Search, Discover (only when
  available), Who is watching (only with two or more profiles), Settings, under a Home marker.
- SHELL-22 Settings sits behind the parental PIN for a restricted profile.
- SHELL-23 A channel outside a restricted profile's groups is refused with a short toast
  "This profile cannot watch that channel".
- SHELL-24 A PIN-locked channel asks for the PIN before live, catch-up and zapped playback.
- SHELL-25 A reminder notification opens its channel or its match card, whether the app was
  closed or already running.
- SHELL-26 A fired reminder appears as a dialog over any screen; the first reminder ever set
  explains, once, how to let reminders open the app; Android 13+ asks for the notification
  permission when a reminder is first set (details in
  [Catch-up and reminders](../specs/22-catchup-and-reminders.md)).
- SHELL-27 Picture in picture (off by default): pressing Home while a stream plays shrinks it
  to a 16:9 corner with its own Close button; opening the app again restores full screen.
- SHELL-28 Background maintenance (metadata matching) pauses while the app is in front and
  resumes 30 s after it leaves.
- SHELL-29 Playlist, guide and catalogue refreshes follow the refresh interval setting.
- SHELL-30 Update check once a day at start (release package only).
- SHELL-31 The phone setup page closes when the viewer leaves the screen that opened it.
- SHELL-32 Sohva Sport is polled only while its screen or the score ticker is visible and the
  app is in front.
- SHELL-33 One focus language across the app: the focused control fills with off-white and its
  content inverts; artwork gets a ring instead; nothing is outlined at rest.
- SHELL-34 Landscape only; the picture-in-picture resize never restarts the activity.

## 3. Entry points and navigation

### 3.1 How the app is entered

| Entry | What arrives | Result |
|---|---|---|
| Launcher tile (`MAIN` + `LEANBACK_LAUNCHER`) | no extras | Start sequence (4.1), then the start screen (4.2). |
| Reminder notification tapped | extra `com.streammate.tv.OPEN_CHANNEL` or `com.streammate.tv.OPEN_EVENT` | Start sequence if cold; then the channel plays (not "for the guide") or Sohva Sport opens on the match card. |
| Reminder fired while another app is in front | extra `com.streammate.tv.REMINDER_ALERT` (marker only), flags `NEW_TASK` + `REORDER_TO_FRONT` | The app comes forward; the ringing reminder dialog shows over whatever screen was up. Refused silently by Android when the overlay grant is missing. |
| Launcher tile while in picture in picture | no extras | The activity returns to full screen with the same player. |
| Anything else | – | There are no URI/deep-link intent filters. |

The activity is `singleTask`, so a second launch or a notification delivers through
`onNewIntent` to the running instance.

### 3.2 Destinations

Only the destination on top of the stack is composed; everything below it is disposed.

| Destination | Parameters | Opened from | Spec |
|---|---|---|---|
| `Home` | – | start; player "Home" shortcut | [Home](../specs/02-home.md) |
| `Guide` | – | rail Live TV; start (Guide / Last channel); player "Guide"; Sohva Sport "Guide"; Home welcome button | [Live TV guide](../specs/20-live-tv-guide.md) |
| `Today` (Sohva Sport) | – | rail Sohva Sport; Home sport card; Search sport result; match reminder; player "Sport" | [Sohva Sport](../specs/60-sohva-sport.md) |
| `Catalogue(mode)` | MOVIES or SERIES | rail Movies / Series; placed under a title resumed from Home | [Movies and series](../specs/40-movies-and-series.md) |
| `MovieDetails(movie)` | the film | a film in the catalogue, a search result, a similar title, Home resume (under the player), a Trakt card, a finished film | [Movies and series](../specs/40-movies-and-series.md) |
| `SeriesDetails(series)` | the series | a series in the catalogue, a search result, Home resume, a Trakt card, a finished last episode | [Movies and series](../specs/40-movies-and-series.md) |
| `Search` | – | rail Search | [Search](../specs/03-search.md) |
| `Discover` | – | rail Discover | [Discover](../specs/50-discover-addons.md) |
| `DiscoverTitle(progress)` | a Discover history entry | Home Continue watching (Discover card) | [Discover](../specs/50-discover-addons.md) |
| `TraktTitle(title)` | a Trakt title | Home Watch next / Recommended when the library has no match | [Trakt](../specs/51-trakt.md) |
| `Settings` | – | rail Settings; guide; Sohva Sport; after the Settings PIN gate | [Settings](../specs/70-settings.md) |
| `LegalInformation` | – | Settings › About | [About](../specs/72-updates-about-diagnostics.md) |
| `LibraryManager(room, group?, source?)` | room, optional group and source | guide options (live), catalogue (films / series), Settings (live) | [Library organisation](../specs/42-library-organization.md) |
| `ChannelEditor` | – | guide ("Channels"), library manager ("Advanced") | [Channel management](../specs/21-channel-management.md) |
| `ProfilePicker` | – | rail Who is watching | [Profiles](../specs/04-profiles-parental.md) |
| `ProfileGate(profileId?, thenSettings)` | target profile, or none for Settings | a switch that needs the PIN; Settings from a restricted profile | [Profiles](../specs/04-profiles-parental.md) |
| `PinGate(channelId, channelName, replacePlayer, rememberForGuide, catchupStart?, catchupStop?)` | the locked channel and what to play after | any play of a locked channel | [Profiles](../specs/04-profiles-parental.md) |
| `Player(channelId, catchupStart?, catchupStop?, returnToGuide)` | channel, optional catch-up window, Back rule | live or catch-up playback | [Player](../specs/30-player.md) |
| `VodPlayer(contentKey, resumePositionMillis)` | film/episode key, start position | film or episode playback | [Player](../specs/30-player.md) |

Before the first destination is shown there are three pre-stack states: the launch screen,
a plain themed ground while the start route is decided, and (when asked) the start-time
Who is watching picker with its PIN screen.

### 3.3 What Back does

| Where | Back |
|---|---|
| Any destination while the stack has more than one entry | Pops to the destination below. Leaving Settings this way also asks Sohva Sport to refresh its feed (an API key or followed sports may have changed). |
| Home, rail focused | Focus returns to the rows (the card last focused); the app stays open. |
| Home, rows focused | The activity finishes (the app closes to the launcher). |
| Live `Player`, not catch-up, `returnToGuide = true` | The stack becomes `[Home, Guide]` and the guide focuses the channel that was playing. |
| Live `Player` with `returnToGuide = false`, or any catch-up | Pops (back to the match card, the reminder's origin, or the guide the catch-up was chosen in). |
| `VodPlayer` | Pops; when the title was resumed from Home that is its details page. |
| `PinGate`, `ProfileGate`, `ProfilePicker` | Pops without unlocking or switching. |
| Start-time picker / its PIN screen | Not part of the stack. The PIN screen's own Back button returns to the picker. The Back key falls through to the shell: with a one-entry start stack the app closes; with a Guide or Last-channel start stack it silently pops the hidden destination underneath (quirk, see 10). |
| Screen-level states (guide options, Settings source page, dialogs, Discover's internal pages, text editors) | Handled by the screen first; the shell only sees Back when the screen does not consume it. |

### 3.4 Where focus lands on entry and on return

| Destination | Entry | Return from a screen above |
|---|---|---|
| Home | First card of the first row once that row has loaded (the loading card while it loads), or the welcome button on an empty Home | Same as entry: Home is rebuilt, scrolled to the top, first card focused (it does not remember the card) |
| Guide | The channel the guide was opened for (last played, zapped or "guide at this channel"), in its group | Same channel |
| Movies / Series | Restored from that mode's browse session (partition, scroll, focused title) | Restored |
| Sohva Sport | The day's list, or the match card named by a request | The match card that was open (saved state keyed `today`) |
| Search | The empty text field | Empty field again (query and results are not kept) |
| Settings | First control of the Sources section | Rebuilt at the Sources section |
| Who is watching | The active profile's tile | – |
| PIN / profile gate | Not set explicitly (see [Profiles](../specs/04-profiles-parental.md)) | – |

## 4. Behaviour

### 4.1 Start-up sequence

- SHELL-FR-01 Process start (`Application.onCreate`): build the dependency container; touch
  Home's cached Continue watching store so its first read starts during the launch screen;
  unless this is the demo build, cancel the legacy metadata job and register the
  foreground tracker (4.9); re-arm the reminder alarm on a background thread
  (`ReminderScheduler.reschedule`, errors ignored).
- SHELL-FR-02 Image loader (built on first image request, app-wide singleton): disk cache
  directory `cacheDir/catalogue_artwork`, size from the Artwork cache setting (100, 250 or
  500 MB, default 250 MB), read synchronously from the small preferences file
  `streammate_artwork_cache` key `limit` because the disk cache fixes its size when built (a
  new limit applies from the next start); memory cache 8 % of the app's memory class; at most
  2 bitmaps decoded in parallel; 140 ms crossfade on every loaded image.
- SHELL-FR-03 Activity creation: below Android 13 the stored interface language (preferences
  file `streammate_locale`, key `language_tag`) wraps the base context in
  `attachBaseContext`; from Android 13 the platform applies the per-app locale. The launch
  intent's open request is recorded (4.6). The content is set immediately.
- SHELL-FR-04 The window background is the launch picture (5.1). After the app has drawn two
  frames the window background is replaced by a flat `#05070D` fill, so the picture is not
  repainted under every later frame.
- SHELL-FR-05 Until the container's one-time initialisation has finished and the first
  preferences snapshot has been read, the Compose launch screen (5.2) is shown, in the
  default palette and at device density. Initialisation (background thread): seed demo
  content (demo build only), migrate legacy hidden-category preferences into organisation
  rules once, load the Trakt accounts, log `startup: local state ready: <ms> ms`, then start
  the film-identity update collector.
- SHELL-FR-06 Then the app composes at the chosen interface size (the device density times the
  scale factor, font scale unchanged) with the colour theme from that first preferences
  snapshot, so the first app frame is already in the saved theme.
- SHELL-FR-07 Until the start route is decided (4.2) the app draws only a full-screen fill in
  the theme's `backgroundBottom`. No app name or other text is drawn in this state.
- SHELL-FR-08 Start route decision, once per composition of the app root, on the first
  preferences snapshot:
  1. Reconcile the `parental_pin_configured` flag with the secure store (the store wins).
  2. Locked channels count only when a PIN exists.
  3. The Who is watching question is needed when the household has at least one added
     profile and `ask_profile_at_start` is on (default on). It is decided here only, so a
     profile added later in Settings never pulls the picker over the current screen.
  4. The stack is computed from the start screen setting (4.2), then marked applied.
- SHELL-FR-09 After the route is applied: pending open requests are handled (4.6), the
  ringing reminder dialog may show, and the Who is watching picker (when needed) is drawn on
  top of the chosen stack until answered.

### 4.2 Start screen

- SHELL-FR-10 Setting `startup_screen`: `HOME` (default), `GUIDE`, `LAST_CHANNEL`. Labels
  `startup_home` "Home", `startup_guide` "Programme guide", `startup_last_channel` "Last
  channel"; help `startup_last_channel_help` "The last channel is restored only if it is
  still available." (picker in Settings › General, see
  [Settings](../specs/70-settings.md)).
- SHELL-FR-11 `HOME` gives `[Home]`. `GUIDE` gives `[Home, Guide]`.
- SHELL-FR-12 `LAST_CHANNEL` reads the active profile's `last_channel_id` and looks it up among
  active channels (enabled source, active playlist snapshot). Missing: `[Home, Guide]`.
  Locked (in the active profile's locked set and a PIN exists): `[Home, Guide,
  PinGate(channel, name, replacePlayer = false)]`. Otherwise `[Home, Guide, Player(channel)]`.
  In both found cases the guide will focus that channel when shown. The restricted-profile
  group check is not applied on this path (see 10).

### 4.3 Stack rules

- SHELL-FR-13 The stack is an in-memory list that starts as `[Home]`. Opening a destination
  pushes it unless it equals the destination already on top (a double OK opens once). Back
  pops while more than one entry remains.
- SHELL-FR-14 Only the top destination is composed. Everything else keeps no UI state except
  what the shell holds on purpose: the Movies and Series browse sessions (one per mode, for
  the life of the app composition), the Sohva Sport screen's saved state (key `today`), the
  channel the guide should focus, whether the guide should reopen its options after the
  library manager (and which group), the channel watched before the current one, and whether
  the score ticker is shown.
- SHELL-FR-15 Destinations change instantly. There is no enter, exit or cross-fade
  animation between destinations.
- SHELL-FR-16 Every change of top destination stops the phone setup server: the phone page
  lives only while the screen that started it (Settings for a source, Channel management for
  a logo) is on top ([Phone setup](../specs/11-phone-setup.md)).
- SHELL-FR-17 Asynchronous navigations (resuming from Home, opening a Trakt title, finishing
  playback) apply only if the screen that asked is still on top when the lookup returns; a
  viewer who moved on is never pulled back.

### 4.4 Playback routing

- SHELL-FR-20 Play a channel (`forGuide` defaults to true):
  1. When `forGuide`, remember the channel as the guide's focus.
  2. Remember the previous channel (SHELL-FR-24).
  3. If the active profile may not see the channel's group, show the refusal toast and stop.
  4. If the channel is in the active profile's locked set, push `PinGate(channel, name or
     "Channel" (`generic_channel`), replacePlayer = false, rememberForGuide = forGuide)`.
  5. Otherwise, when `forGuide`, record it as the most recent channel (moves it to the front
     of `recent_channel_ids`, capped at 20, and sets `last_channel_id`), then push
     `Player(channel, returnToGuide = forGuide)`.

  Callers with `forGuide = true`: the guide, Home's recent channel cards, Search channel and
  programme results. Callers with `forGuide = false`: a Sohva Sport match card, the reminder
  dialog's Watch, a reminder notification. Neither the toast nor the lock is skipped for any
  caller.
- SHELL-FR-21 Play catch-up (from the guide): guide focus = channel; previous channel
  remembered; the same group check; a locked channel pushes `PinGate` carrying the catch-up
  window; otherwise the channel is recorded as recent and `Player(channel, start, stop)` is
  pushed.
- SHELL-FR-22 Zap (any channel change inside the live player: channel up/down, dialled
  number, the player's channel list, zap-back): guide focus = channel; previous channel
  remembered; the group check; locked pushes `PinGate(..., replacePlayer = true)` on top of
  the player; otherwise the channel is recorded as recent and the top `Player` is replaced by
  `Player(channel)` (so `returnToGuide` becomes true, see 10).
- SHELL-FR-23 Unlocking a `PinGate`: when `rememberForGuide`, set guide focus and record the
  channel as recent; build `Player(channel, catch-up window, returnToGuide =
  rememberForGuide)`; with `replacePlayer` the gate and the player under it are both replaced,
  otherwise only the gate.
- SHELL-FR-24 Previous channel (zap-back): before any play, catch-up or zap, the candidate is
  the channel currently playing, else the persisted last channel, else the existing
  previous channel; it becomes the previous channel if it differs from the one about to play.
  Kept for the session only. The player offers zap-back only when a previous channel exists
  and differs from the current one.
- SHELL-FR-25 Back from a live player: `returnToGuide` and no catch-up window → guide focus =
  channel and the stack becomes `[Home, Guide]`; otherwise pop.
- SHELL-FR-26 Player shortcuts: Home → `[Home]`; Guide → `[Home, Guide]`; Sport → `[Home,
  Today]`; guide at this channel → guide focus = current channel, `[Home, Guide]`.
- SHELL-FR-27 Play a film or episode from anywhere but Home: push `VodPlayer(contentKey,
  resume)`.
- SHELL-FR-28 Resume from Home: look the key up as a film; if found the route is
  `Catalogue(MOVIES), MovieDetails(film)`; else look up the series the episode belongs to,
  route `Catalogue(SERIES), SeriesDetails(series)`; else no route. If Home is still on top,
  push the route and then `VodPlayer(contentKey, resume)`.
- SHELL-FR-29 Playback end (film or episode): when "Play the next episode automatically" is
  on (default on), look up the next episode (errors mean none). With a next episode the
  finished player is replaced by `VodPlayer(next, 0)`. Without one the player is popped and,
  if the screen now on top is not a film or series details page (playback began in Search),
  the film's details page or the episode's series page is pushed when it can be found. Only
  applied while the finished player is still on top.
- SHELL-FR-30 A Trakt card: resolve a library copy by the title's TMDB id (films: the first
  `vod:movie:` content key with that id; series: only when a TMDB-produced match exists, the
  first `series:` key). Found: push `MovieDetails` / `SeriesDetails` directly on Home (no
  catalogue underneath). Not found: push `TraktTitle`. Only while Home is on top.
- SHELL-FR-31 A catalogue entry: a film opens `MovieDetails` from its active copy; a series
  opens `SeriesDetails(source, series)`.

### 4.5 Gates in front of Settings, profiles and channels

- SHELL-FR-32 Open Settings: when the active profile is restricted and a PIN exists, push
  `ProfileGate(profileId = null, thenSettings = true)`; unlocking replaces the gate with
  `Settings`. Otherwise push `Settings`.
- SHELL-FR-33 Switch profile (from Settings or the rail picker): when the target needs the PIN
  (rule in [Profiles](../specs/04-profiles-parental.md)), push `ProfileGate(target, false)`;
  unlocking sets the active profile and pops the gate. Otherwise the active profile changes at
  once.
- SHELL-FR-34 Refusal toast: `Toast.LENGTH_SHORT` with `profile_content_blocked` "This
  profile cannot watch that channel". Shown by play, catch-up and zap.

### 4.6 Open requests and app-wide dialogs

- SHELL-FR-40 An intent carries at most one open request: a non-blank
  `com.streammate.tv.OPEN_CHANNEL` wins over a non-blank `com.streammate.tv.OPEN_EVENT`;
  blank or missing values are no request.
- SHELL-FR-41 Requests from `onCreate` and `onNewIntent` go into a single pending slot (the
  newest replaces an unhandled older one). The slot is consumed once the start route is
  applied: a channel plays with `forGuide = false`; an event opens Sohva Sport on that
  match's card (the card opens once the day's events contain it).
- SHELL-FR-42 The first ringing reminder is shown as a dialog over any screen once the start
  route is applied, including over the start-time picker. Watch dismisses it and plays its
  channel (`forGuide = false`) or, for a match without a single known channel, opens the
  match card. Not now dismisses it. The queue, the auto-dismissal and the texts are in
  [Catch-up and reminders](../specs/22-catchup-and-reminders.md).
- SHELL-FR-43 Setting a reminder (from the guide or a match card) toggles it. When it is being
  set: on Android 13+ ask for `POST_NOTIFICATIONS` if not granted; then, if the app may not
  draw over other apps and the preference `reminder_overlay_asked` is false, set it and show
  the one-time overlay prompt (Open TV settings / dismiss).
- SHELL-FR-44 Whether a reminder may open the app over another app is re-read on every
  lifecycle change, so returning from the TV's settings updates Settings' status row.

### 4.7 Picture in picture

- SHELL-FR-50 Allowed only while the setting "Keep watching in a corner" is on (default off)
  and the top destination is `Player` or `VodPlayer`, on Android 8.0 (API 26) and later.
- SHELL-FR-51 The Home button (the activity's user-leave hint) enters picture in picture with
  aspect ratio 16:9 and one action, Close (`picture_in_picture_close` "Close", icon
  `android.R.drawable.ic_menu_close_clear_cancel`), which sends the package-scoped broadcast
  `com.streammate.tv.action.PICTURE_IN_PICTURE_CLOSE`. A failure to enter is ignored.
- SHELL-FR-52 While in the corner a non-exported receiver listens for that broadcast; Close
  finishes the activity, which stops the stream and releases the provider connection. The
  receiver is removed when the corner closes or the activity is destroyed.
- SHELL-FR-53 While in the corner the player draws only the picture ([Player](../specs/30-player.md)).
- SHELL-FR-54 Opening the app from the launcher returns the corner to full screen with the same
  playback. The activity is not recreated by the resize (config changes `screenSize`,
  `smallestScreenSize`, `screenLayout`, `orientation` are handled in place).

### 4.8 Home navigation rail

The rail is drawn only on Home, over the rows at the left edge. Its measurements are in 5.4;
the Home rows, hero and focus hand-offs are in [Home](../specs/02-home.md).

- SHELL-FR-60 Items, top to bottom: the Home marker, then Live TV (`home_live_tv`), Sohva Sport
  (`home_sportmate`), Movies (`home_movies`), Series (`home_series`), Search (`home_search`),
  Discover (`home_discover`, "Discover", not translated), Who is watching
  (`profile_active_title`), Settings (`home_settings`). Test tags `home-nav-home`,
  `home-live`, `home-sportmate`, `home-movies`, `home-series`, `home-search`,
  `home-discover`, `home-profiles`, `home-settings`.
- SHELL-FR-61 Discover is present only when the package allows addons (release, debug and Lab
  packages), the build is not the demo, and the active profile is not restricted. Who is
  watching is present only when the household has more than one profile, counting the
  default one.
- SHELL-FR-62 OK on an item: Live TV → `Guide`; Sohva Sport → `Today`; Movies / Series →
  `Catalogue`; Search → `Search`; Discover → `Discover`; Who is watching → `ProfilePicker`;
  Settings → open Settings (SHELL-FR-32).
- SHELL-FR-63 The Home marker is not focusable and is always drawn as the current page.
  Up from the first item therefore stays on it; Down walks the items in order.
- SHELL-FR-64 Right from any item returns focus to the rows, to the card last focused there
  (or the welcome button on an empty Home); the rail is drawn over the rows and widens, so the
  way out is stated rather than found by geometry. Back while the rail has focus does the same.
- SHELL-FR-65 While any item has focus the rail is expanded: it widens from 80 dp to 244 dp over
  the content (never pushing it), its scrim deepens, and every item shows its label. When focus
  leaves the rail it collapses to icons. Rail focus also returns the Home hero to its idle
  state.
- SHELL-FR-66 When Discover is present the rail scrolls vertically, so Settings stays reachable
  at the smaller interface sizes.
- SHELL-FR-67 Accessibility: the rail's content description is `home_navigation`
  "Navigation"; every item (and the marker, `home_nav_home` "Home") carries its label as its
  content description even while collapsed.

### 4.9 Background work the shell wires

- SHELL-FR-70 Foreground tracker: counts started activities. The first start pauses the
  catalogue metadata job (cancels unique work `streammate-catalogue-metadata-enrichment-v2`
  and the legacy `streammate-catalogue-metadata-enrichment`). The last stop (not a
  configuration change) schedules the job again with a 30 s initial delay, network required,
  policy KEEP. The same flag tells a firing reminder whether to bring the app forward.
  Not installed in the demo build; the job never runs in Lab.
- SHELL-FR-71 Refresh schedule (not in the demo build): at start and whenever the refresh
  interval changes, periodic unique work is (re)enqueued with policy UPDATE for playlists and
  guides every 1, 2, 4, 10 or 24 hours (setting, default 24) and for catalogues every 24 hours;
  network required; while the app is in front a run backs off linearly by 15 minutes
  ([Sources and import](../specs/10-sources-and-import.md)).
- SHELL-FR-72 Update check: once per 24 hours from app start, only in the release package
  (`com.streammate.tv`) and never in the demo build
  ([Updates](../specs/72-updates-about-diagnostics.md)).
- SHELL-FR-73 Sohva Sport polling is on only while `Today` is on top, or the score ticker is
  shown over a player, and the activity is resumed. Turning the ticker on with no events
  loaded triggers one refresh. Sohva Sport's first refresh waits for Home's cached Continue
  watching read ([Sohva Sport](../specs/60-sohva-sport.md)).
- SHELL-FR-74 Trakt sync runs for the active profile once the profile question is answered and
  the cached Continue watching read has finished; a profile change restarts it for the new
  profile ([Trakt](../specs/51-trakt.md)).
- SHELL-FR-75 The metadata language preference is pushed to the metadata repository whenever it
  changes ([Metadata](../specs/41-metadata-enrichment.md)).

### 4.10 App-wide focus rules

These rules are shared by every screen; the component anatomy is in
[Components](../design/02-components.md).

- SHELL-FR-80 One focus treatment. A focused surface fills with `textPrimary` and its content
  turns `background` (secondary content `background` at 62 % alpha). A selected-but-unfocused
  surface uses `surfaceFocused` and an accent marker. At rest a surface is a step of the
  surface ladder or transparent, never outlined. Surfaces whose content is artwork keep their
  fill and draw a 3 dp `textPrimary` ring inside their bounds instead. A danger control fills
  with `danger` when focused.
- SHELL-FR-81 Focus lift: a focused surface may scale (default 1.04, buttons 1.03, most rows
  and Home cards 1.0) and casts a black shadow (14 dp surfaces, 10 dp buttons, 13 dp display
  fields). The scale is applied inside the focus target so the bounds a scrolling parent sees
  never change. No ripple or focus wash is drawn.
- SHELL-FR-82 Initial focus is requested by retrying once per frame, up to 30 frames (about
  half a second), until the target is attached and accepts focus; the request reports
  success. A fixed sleep before `requestFocus` is never used.
- SHELL-FR-83 Vertically scrolling screens use a keep-visible scroll policy: moving focus to a
  control that is already fully visible does not scroll; otherwise the smallest scroll that
  shows it. Horizontal rails inside such screens keep the platform's TV pivot policy (focused
  child moved to about 30 % of the rail).
- SHELL-FR-84 A screen with a non-focusable header above its first control (Home's brand,
  details pages' titles) scrolls back to the very top when focus re-enters that top block,
  one frame after focus arrives, so the header is never left cut off.
- SHELL-FR-85 Rebuild rule: every screen and overlay states its initial focus and the control
  that receives focus when it closes; focus is moved before an overlay is removed. Nothing
  relies on the platform's default focus search.

### 4.11 Keys

- SHELL-FR-90 The activity overrides no key callback. Back goes through the back dispatcher:
  the shell's handler is enabled whenever the stack has more than one entry, and handlers
  registered by the current screen take precedence.
- SHELL-FR-91 Home (leave hint) enters picture in picture when allowed; otherwise the app goes
  to the background and the player stops by its own lifecycle rules.
- SHELL-FR-92 Channel up/down, number keys, media keys, Menu and remapped buttons are handled by
  the guide and the player ([Live TV guide](../specs/20-live-tv-guide.md),
  [Player](../specs/30-player.md), [Remote mapping](../specs/31-remote-button-mapping.md)).

### 4.12 Runtime policy and build variants

- SHELL-FR-95 Behaviour switches come from the package name, never from a restorable
  preference: Lab is `com.streammate.tv.lab`; public updates only in `com.streammate.tv`;
  addons (Discover) in `com.streammate.tv`, `com.streammate.tv.debug` and Lab; automatic
  maintenance, automatic Sohva Sport refresh and reminders everywhere except Lab.
- SHELL-FR-96 Demo mode is present when the manifest names a demo content provider. It seeds
  fictional content at every start, hides Discover, runs Trakt offline, skips the update
  check, the refresh schedule and the metadata lifecycle, and "refresh catalogue" reseeds.

Build variants (one paragraph; [Tech stack and build](../plan/05-tech-stack-and-build.md) has
the rest): application id `com.streammate.tv` (kept for in-place upgrades), minSdk 23,
target/compile SDK 36. Build types: `release` (R8 on), `debug` (`.debug` suffix, "-debug"),
`demo` (`.demo`, label "Sohva TV Demo", fictional content provider registered in its own
manifest), `lab` (`.lab`, label/icon/banner "Sohva TV Lab", debug-signed, no install
permission, no boot receiver, reminder receiver disabled, no update file provider, unshrunk
unless asked). All four can be installed side by side.

## 5. Screen anatomy

No reference screenshot of these states is in `design/screenshots/` yet. Global tokens are in
[Design system](../design/01-design-system.md).

### 5.1 Launch picture (window background)

- Theme `Theme.StreamMate`, parent `Theme.Material.NoActionBar`: font family `sans`,
  `colorAccent` `#2DE2E6`, status and navigation bar `#05070D`, light status bar off, window
  background = the layer list below.
- Layer 1: rectangle with a linear gradient at 270° (top to bottom) `#080C15` → `#05070D` →
  `#04060A`.
- Layer 2: `sohva_mark` bitmap 188×188 dp, gravity centre, bottom inset 74 dp.
- Layer 3: `sohva_wordmark` bitmap 273×56 dp, gravity centre, top inset 206 dp.
- The insets reproduce a centred column of mark (188), 18 dp gap and wordmark (56): the
  column is 262 dp tall, the mark's centre 37 dp above the screen centre, the wordmark's
  103 dp below.
- Bitmaps: `sohva_mark` 188/282/376/564/752 px and `sohva_wordmark` 273×56 / 409×84 /
  546×112 / 819×168 / 1092×224 px for mdpi to xxxhdpi (assets in
  [Icons and imagery](../design/04-icons-and-imagery.md)).

### 5.2 Compose launch screen

- The shared screen background (5.5) with no content padding, default palette, device
  density (not interface-scaled).
- A centred column: mark image 188 dp square (tag `launch-mark`), 18 dp spacer, wordmark image
  56 dp tall (tag `launch-brand`, content description `brand_sohva_tv` "Sohva TV"), column
  tagged `launch-splash`.
- Difference from 5.1: the Compose screen adds the two faint ambient washes of 5.5; the
  window picture has only the gradient.

### 5.3 Start-route ground

A full-screen fill in the active theme's `backgroundBottom` (Original theme `#04060A`). No
text, no logo.

### 5.4 Home navigation rail

- Width 80 dp collapsed, 244 dp expanded; full height; drawn above the rows (z-index 1).
- Background: horizontal gradient `background` at alpha s (x = 0) → `background` at alpha
  0.9 × s (x = 72 %) → transparent (x = 100 %), where s = 0.70 collapsed and 0.97 expanded.
  Width and s animate with the platform default spring.
- Padding: top 24 dp, bottom 24 dp, start 24 dp, end 8 dp. Items spaced 4 dp; an 8 dp spacer
  under the Home marker.
- Item: 48 dp tall, full rail width, shape medium (12 dp), horizontal padding 12 dp, icon
  24 dp, label 14 dp to its right (label style: 14 sp, line height 19 sp, SemiBold, one line,
  ellipsis). At rest transparent with `textMuted` icon and label; focused: `textPrimary` fill,
  `background` content; no scale.
- Home marker: same geometry, always `textPrimary` fill with `background`-tinted
  `ic_sohva_nav_front_page` icon and Bold label `home_nav_home` "Home" when expanded.
- Icons: `ic_sohva_nav_live_tv`, `ic_sohva_nav_sport`, `ic_sohva_nav_movies`,
  `ic_sohva_nav_series`, `ic_sohva_nav_search`, `ic_sohva_nav_discover`, core `ic_tv_star` for
  Who is watching, `ic_sohva_nav_settings` (24×24 viewport vectors, 1.75 stroke, tinted at
  draw time; see [Icons and imagery](../design/04-icons-and-imagery.md)). The rail icons
  `ic_sohva_nav_addon_home`, `_library`, `_explore`, `_addons`, `_back_to_home` belong to
  Discover's own rail.

### 5.5 Shared screen background

Used by every full screen except the profile picker (which is a flat `backgroundBottom`).

- One canvas painted into its own offscreen layer and redrawn only when the size or theme
  changes:
  - vertical gradient `backgroundTop` (0) → `background` (0.5) → `backgroundBottom` (1);
  - radial wash `focus` at alpha 0.10 (0) → 0.02 (0.6) → transparent (1), centre (12 % w,
    −10 % h), radius 58 % of the width;
  - radial wash `secondaryGlow` at alpha 0.12 (0) → 0.02 (0.62) → transparent (1), centre
    (92 % w, 4 % h), radius 50 % of the width.
- Default content padding: 40 dp horizontal, 24 dp vertical (the TV safe area). Home and the
  launch screen use none.

### 5.6 App-wide dialogs

- Ringing reminder: 460 dp wide column on `surface`, shape medium, padding 18 dp (anatomy in
  [Catch-up and reminders](../specs/22-catchup-and-reminders.md)).
- Overlay prompt: same frame; title `reminder_overlay_title` "Let reminders open Sohva TV",
  body `reminder_overlay_body`, action `reminder_overlay_open` "Open TV settings".
- Refusal toast: the platform toast.

## 6. Data

- The stack and the session memory of 4.3 are in memory only. The stack is not saved: after
  process death or an activity recreation (language change) the start route is computed
  again. Saved across recreation: whether the start-time profile question was answered, and
  the score ticker switch.
- Preferences read or written by the shell (DataStore file `streammate_preferences`; keys with
  `[:id]` are per profile, the default profile uses the bare key, see
  [Profiles](../specs/04-profiles-parental.md)):

  | Key | Type, default | Use |
  |---|---|---|
  | `startup_screen` | `HOME` / `GUIDE` / `LAST_CHANNEL`, default `HOME` | start route |
  | `last_channel_id[:id]` | string | Last-channel start, zap-back fallback |
  | `recent_channel_ids[:id]` | ids joined by U+001F, newest first, max 20 | Home recent row |
  | `locked_channel_ids[:id]` | string set | PIN gate |
  | `parental_pin_configured` | boolean, false | gates; reconciled with the secure store at start |
  | `profiles`, `active_profile_id`, `ask_profile_at_start` (true) | – | profile question |
  | `interface_scale` | `NORMAL`/`COMPACT`/`SMALL`/`SMALLER` = 1.0/0.9/0.8/0.7, default `NORMAL` | density |
  | `color_theme` | stored id, default `original` | palette |
  | `picture_in_picture` | boolean, false | 4.7 |
  | `playlist_epg_refresh_interval` | hours 1/2/4/10/24, default 24 | 4.9 |
  | `metadata_language` | tag | 4.9 |
  | `reminder_overlay_asked` | boolean, false | one-time prompt |

- Small preference files read synchronously before the first frame: `streammate_locale`
  (`language_tag`, below Android 13 only) and `streammate_artwork_cache` (`limit`:
  `SMALL`/`MEDIUM`/`LARGE`, default `MEDIUM`).
- Files: image disk cache `cache/catalogue_artwork` (100/250/500 MB).
- Android backup is off (`allowBackup=false`, every domain excluded from cloud backup and device
  transfer). The app's own encrypted backup carries the start screen, interface size, theme and
  PiP switch ([Backup and restore](../specs/71-backup-restore.md)); the stack and session
  memory are never backed up.

## 7. External interfaces

### 7.1 Manifest

- Permissions: `INTERNET`, `REQUEST_INSTALL_PACKAGES` (in-app updates),
  `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` (re-arm reminders), `SYSTEM_ALERT_WINDOW`
  (optional grant so a reminder may bring the app forward), `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- Features: `android.software.leanback` required; `android.hardware.touchscreen` not required.
- Application: name `.app.StreamMateApplication`, `allowBackup=false`, banner `app_banner`
  (320×180 / 480×270 / 640×360 px at xhdpi / xxhdpi / xxxhdpi), icon `ic_launcher` (adaptive:
  background `#05070D`, foreground `sohva_launcher_foreground`), label `app_name`,
  `localeConfig` (en, fi, es, pt, de, sv, it), network security config
  ([Security and privacy](../specs/73-security-privacy.md)), `supportsRtl`, theme
  `Theme.StreamMate`, `usesCleartextTraffic=true`, `<profileable android:shell="true">` for
  release profiling.
- Activity `.app.MainActivity`: exported, `launchMode=singleTask`,
  `screenOrientation=landscape`, `supportsPictureInPicture=true`, `configChanges=
  screenSize|smallestScreenSize|screenLayout|orientation`; one intent filter `MAIN` +
  `LEANBACK_LAUNCHER` (no `LAUNCHER` category).
- Receiver `.app.ReminderReceiver`: not exported, `BOOT_COMPLETED`.
- Service `.app.StreamMatePlaybackService`: exported for media session binding,
  `foregroundServiceType=mediaPlayback`, action `androidx.media3.session.MediaSessionService`.
- Provider `androidx.core.content.FileProvider`, authority `${applicationId}.updates`, not
  exported, grants URI permissions, paths: cache `updates/`.

### 7.2 Intents and broadcasts

| Name | Kind | Meaning |
|---|---|---|
| `com.streammate.tv.OPEN_CHANNEL` | activity extra (string) | play this channel id |
| `com.streammate.tv.OPEN_EVENT` | activity extra (string) | open this Sohva Sport event's card |
| `com.streammate.tv.REMINDER_ALERT` | activity extra (boolean) | marker on the bring-forward start; not parsed |
| `com.streammate.tv.action.PICTURE_IN_PICTURE_CLOSE` | broadcast, `setPackage(own)`, receiver not exported | close the corner |
| `com.streammate.tv.REMINDER_FIRE` | broadcast to the reminder receiver | owned by [Catch-up and reminders](../specs/22-catchup-and-reminders.md) |

### 7.3 WorkManager names

`streammate-catalogue-metadata-enrichment-v2` (legacy `streammate-catalogue-metadata-enrichment`
is only cancelled), `streammate-playlist-refresh`, `streammate-epg-refresh`,
`streammate-catalogue-refresh`, and one-off `sohva-sync-now-<sourceId or all>`.

## 8. Edge cases and limits

- Screen size: a 1080p TV at the usual xhdpi density is 960×540 dp; at the 70 % interface size
  the app lays out 1371×771 dp. Every screen must work across that range.
- Process death, low-memory kill or a language change restarts at the start route; the
  viewer loses the screen they were on (a language change from Settings lands on Home or the
  chosen start screen).
- An open request that arrives before the start route is applied waits; one that arrives
  while the start-time profile question is up navigates underneath it, and the picker stays on
  top until answered.
- Last-channel start is resolved before the start-time profile question is answered, from
  the previously active profile's last channel and locked set.
- A reminder's channel refused by the active profile's groups shows the toast instead of
  playing.
- Picture in picture below Android 8.0: never offered. On launchers that give no way to reach
  a corner window (Projectivy on the Shield) the only exit is to open the app again, then Back;
  this is why the setting ships off.
- Lab: no reminders (receiver disabled), no public updates, no automatic maintenance or sport
  refresh. Demo: see SHELL-FR-96.
- Back on Home finishes the activity; the process usually survives, so the next launch is warm
  and Home reuses its cached Continue watching snapshot.
- Two quick OKs on a rail item open the destination once (SHELL-FR-13).

## 9. Lightweight by design

Budgets are in [Performance](../plan/07-performance.md); the shell's share is start-up, the
per-frame cost of what every screen stands on, and not waking the device when nobody watches.

- Start-up work, current app (on the main thread in `Application.onCreate`): constructing
  about 30 container objects eagerly (Room builder, keystore wrapper, OkHttp client with 20 s
  connect / 90 s read timeouts, repositories, import services, Trakt service, update checker,
  phone setup server), two PackageManager reads (demo meta-data and the version name for the
  user agent), WorkManager initialisation just to cancel a legacy job, and lifecycle
  registration. In the first composition: the Sohva Sport view model, reminder and preference
  collectors, session token. Rules for the rebuild:
  - `Application.onCreate` only wires lazy providers; no disk, PackageManager or WorkManager
    call on the main thread before the first Home frame.
  - Preferences are read once, off the main thread, and the start route is computed off the
    main thread; the launch screen covers exactly that.
  - Scheduling (refresh work, metadata job cancel, reminder re-arm, update check) runs on a
    background thread after Home's first frame.
  - Sohva Sport, Trakt, Discover and the update checker are constructed on first use.
- Per-frame cost: the launch bitmaps stayed the window background for the whole session and
  every screen repainted a flat fill, a gradient and two radial washes under its own opaque
  content (four or five full-screen passes per frame; the Elisa box's Mali-G31 has about a
  tenth of the Shield's fill rate). Now: flat window colour after two frames, and the ground
  painted once into an offscreen layer (5.5), about 5 → 2 full-screen passes per frame outside
  Home. Keep: no window background under content, one cached layer for any static ground, no
  destination transition animations.
- Rail animation: width (spring) and scrim alpha animate on every focus entry and exit, which
  re-lays-out the rail column and repaints a full-height gradient every frame of the spring.
  Rebuild: draw the rail in its own layer, animate with a fixed 150 ms tween (or snap on the
  low-end class), keep the scrim a cached gradient bitmap or a solid fill, and never re-lay-out
  the rows.
- Root recomposition: the app root reads the whole preferences object and several flows
  (reminders, Sohva Sport state, open requests), so any preference write (every zap records a
  recent channel) recomposes the root and re-invokes the current screen. Rebuild: narrow,
  distinct flows per consumer; the root reads only the stack and the theme.
- Memory: only the top destination is composed; destinations carry small descriptors (ids, a
  domain record at most). Image memory cache 8 % of the memory class (about 15 MB on a 192 MB
  heap), two parallel decoders (four let a burst of posters saturate the Shield's cores and
  pushed frame p90 above 100 ms), disk cache 250 MB by default. The rebuild must not hold any
  list larger than a screenful in the shell.
- Background priority: metadata matching never runs while the app is in front; refresh work
  backs off while the app is in front; Sohva Sport polls only when visible.
- Code shape: the current root composable is one 1,100-line function; with R8 off, two
  composables exceeded ART's 10,000 code-unit ahead-of-time limit and ran interpreted. Keep
  every composable small, keep R8 on, fail the release build when a method passes 95 % of the
  limit, and ship the baseline profile with in-app updates
  ([Updates](../specs/72-updates-about-diagnostics.md)).
- Semantics: an enabled accessibility service (on TV boxes usually a button remapper) makes
  Compose walk the semantics tree about ten times a second; the rail keeps one node per item
  (icons cleared of semantics). Keep semantics trees minimal everywhere.

## 10. Lessons from the current app

- Window background drawn under every frame: fixed by a flat colour after two frames and cached
  ground layers (commit `67778c2`, ledger `SOHVA_SPORT_USER_REPORTS.md` › "Slow boxes").
- The launch screen was inside the interface scaling and shrank at smaller sizes (`fffbcea`);
  the window picture and the Compose launch screen must be the same picture at the same places
  (`05af9bd`, `launch_background.xml` comment).
- A plain "Sohva TV" label flashed between the launch picture and Home on every reopen; the
  interim state is now an empty themed ground (`88ff0b6`, ledger › "Owner acceptance and
  startup text flash").
- The first app frame uses the saved theme because the first preferences snapshot is awaited
  before composing the app (`MainActivity.kt`, beta 18).
- Start-up reads and bulk processing moved off the UI thread (`8296cc9`); cached Continue
  watching first, optional Home work after it (`ca940fe`).
- Back from a stream chosen on a match card used to land on the guide; the player now records
  whether the guide had a part in starting it, and the Sohva Sport screen keeps its open card
  in saved state (`917db70`, `StreamMateNavigationTest`).
- A finished film stayed on a black player; completion now returns to details and supplies a
  details page when playback began in Search; a stale completion never replaces a screen the
  viewer moved to (`d28b2ed`, `docs/PLAYBACK_COMPLETION_FIX.md`).
- Picture in picture had no way out on TV launchers; the corner carries Close and the setting
  ships off (`docs/NEXT_FEATURES_PLAN.md` › 5.1, `PictureInPictureStateTest`).
- Android TV shows no popup for another app's notification, and blocks background activity
  starts without the overlay grant; reminders are presented in-app and bring the app forward
  when allowed (`Reminders.kt`, memory note "Android TV reminders show no popup").
- Focus requests after a fixed 80 ms sleep lost the race with layout (and never landed under a
  test clock); the frame-retry helper with 30 attempts replaced them (`7af9ef7`, `58a9164`).
- A focus lift applied around the focus target changed the reported bounds, so lists scrolled
  a pixel on every sideways press; the lift sits inside (`0aa2a2a`, `TvFocusScaleStabilityTest`).
- The TV pivot scroll policy scrolled whole vertical screens on sideways moves; vertical
  containers use the keep-visible policy (`FocusScroll.kt`, `FocusScrollMechanismTest`).
- Every sideloaded update ran interpreted until the nightly dexopt (Home's first composition
  922 ms vs 218 ms compiled on the Shield); updates now carry the install-time profile
  (ledger › "Cold start and first guide entry", › "Updates that arrive compiled").
- Quirks to fix in the rebuild (current behaviour, not intended):
  - Zapping inside a player started from a match card replaces it with `returnToGuide =
    true`, so Back then goes to the guide instead of the match card.
  - The Last-channel start ignores the restricted profile's groups and is resolved before the
    start-time profile question.
  - The Back key on the start-time picker or its PIN screen falls through to the shell's
    stack (closing the app or popping a hidden start destination).
  - The flat window colour is always the Original theme's `#05070D`, whatever theme is chosen.
  - The back stack is not saved, so a language change restarts at the start screen.
  - `LAUNCHER` category is absent; the app appears only in TV launchers.

### Open questions

- On Android 12+ the platform may move a root launcher task to the back instead of finishing
  it on Back; the current tests only prove finishing on Android 11. Decide whether the rebuild
  finishes the activity explicitly.
- Should the rebuild keep a Back-restorable stack across a language change and process death,
  or keep today's restart-at-start-screen behaviour?

## 11. Acceptance tests

Unit (JVM):
- Start route: HOME → `[Home]`; GUIDE → `[Home, Guide]`; LAST_CHANNEL with an active, a locked
  (PIN set), a locked-without-PIN and a missing channel.
- Back rule after playback: live + guide → guide; catch-up → previous; match card → previous
  (mirror `StreamMateNavigationTest`).
- Open request parsing: channel wins, blanks ignored (mirror `OpenRequestTest`).
- Picture in picture: never allowed outside a player, off by default (mirror
  `PictureInPictureStateTest`).
- Runtime policy per package (mirror `AppRuntimePolicyTest`).
- Stack operations: push de-duplication, zap replaces the top player, `PinGate` with
  `replacePlayer`, resume-from-Home route, playback end with next episode / without and with a
  details page below / stale completion ignored.

Instrumentation (debug build, state reset before launch as `ClearAppStateRule` does):
- Launch screen draws the mark and the wordmark with content description "Sohva TV" (mirror
  `SplashScreenTest`); launcher label "Sohva TV" in English and Finnish (`SohvaBrandTest`).
- Three launcher launches in one process: Home content focused, Live TV opens the guide, Back
  returns to Home with content focused, Back again destroys the activity (mirror
  `HomeReopenNavigationTest`).
- Rail: every destination present, the marker present, Up/Down walk in order, Right and Back
  return to the rows, Who is watching appears with a second profile (mirror `HomeScreenTest`).
- Focus scroll policy and focus lift stability (mirror `FocusScrollMechanismTest`,
  `TvFocusScaleStabilityTest`).
- A locked channel as last channel starts behind the PIN gate; a reminder open request while
  running plays the channel and Back returns to the previous screen.

Manual, on a device:
- Cold start from the launcher: launch picture → launch screen → first screen with nothing
  moving, no text flash, the saved theme from the first frame.
- Picture in picture on: Home during playback → 16:9 corner; Close stops playback; reopening
  returns to full screen. With the setting off, Home stops playback.
- Back after live from the guide, from a match card, and after catch-up.

Low-end performance (Elisa-class box, or the `.local/slowbox` emulator harness as a relative
stand-in), release build installed with its install-time profile:
- Cold start to Home's first content frame within 4 s on the low-end box and 2 s on the
  Shield; record `startup: local state ready` and `home: cached resume ready` from the
  diagnostics log.
- Perfetto: `Application.onCreate` does no disk, PackageManager or WorkManager work on the main
  thread; no main-thread slice over 16 ms between the first frame and Home's first content.
- GPU overdraw on real hardware (not the SwiftShader emulator, where `debug.hwui.overdraw`
  aborts): at most 2 full-screen passes on screens other than Home and 3 on Home.
- Rail expansion on the low-end box: each focus entry/exit renders within one or two vsyncs and
  the rows are not re-laid out (layout inspector or trace markers).
- Release gate: no app method above 95 % of ART's 10,000 code-unit limit.

## 12. Reference: current code map

- `app/src/main/java/com/streammate/tv/app/MainActivity.kt` — activity, PiP entry and close receiver, locale wrap, window background swap, launch screen vs app, interface density.
- `app/src/main/java/com/streammate/tv/app/StreamMateApplication.kt` — container creation, early resume read, reminder re-arm, image loader configuration.
- `app/src/main/java/com/streammate/tv/app/StreamMateContainer.kt` — dependency container, one-time initialisation, Home resume store wiring.
- `app/src/main/java/com/streammate/tv/app/StreamMateApp.kt` — destination model, stack, start route, playback routing, gates, open requests, global dialogs, background effects.
- `app/src/main/java/com/streammate/tv/app/StreamMateLaunchScreen.kt` — Compose launch screen.
- `app/src/main/res/drawable/launch_background.xml`, `res/values/themes.xml`, `res/values/colors.xml` — window launch picture and theme.
- `app/src/main/AndroidManifest.xml` (+ `src/demo`, `src/lab` manifests) — components and variants.
- `app/src/main/java/com/streammate/tv/app/PictureInPicture.kt` — PiP state, params and Close action.
- `app/src/main/java/com/streammate/tv/app/Reminders.kt` — open requests, reminder scheduler, receiver, alerts, overlay grant.
- `app/src/main/java/com/streammate/tv/app/StreamMateForegroundState.kt` — started-activity counter pausing the metadata job.
- `app/src/main/java/com/streammate/tv/app/AppRuntimePolicy.kt`, `AddonFeature.kt` — package-based policy and the Discover entry point.
- `app/src/main/java/com/streammate/tv/feature/home/HomeScreen.kt` (`HomeRail`) — the navigation rail.
- `app/src/main/java/com/streammate/tv/feature/common/SohvaNavigationIcons.kt` — rail icon map.
- `core/src/main/java/com/streammate/tv/feature/common/FocusRequests.kt` — frame-retry focus request.
- `core/src/main/java/com/streammate/tv/feature/common/FocusScroll.kt` — keep-visible scroll policy and scroll-to-top on focus.
- `core/src/main/java/com/streammate/tv/feature/common/TvUiComponents.kt` — screen background, brand, focus surfaces, buttons, fields.
- `core/src/main/java/com/streammate/tv/app/InterfaceScale.kt`, `AppLocale.kt`, `ArtworkCacheSettings.kt` — interface size, language, artwork cache limit.
- Tests: `StreamMateNavigationTest`, `OpenRequestTest`, `PictureInPictureStateTest`, `AppRuntimePolicyTest`, `SplashScreenTest`, `SohvaBrandTest`, `HomeReopenNavigationTest`, `HomeScreenTest`, `FocusScrollMechanismTest`, `TvFocusScaleStabilityTest`.
