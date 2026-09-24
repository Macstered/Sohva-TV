# Player: live, catch-up and VOD playback

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

The player shows one stream full screen: a live channel, a catch-up recording of a past
programme, or a provider film or episode (VOD). It is a Media3 ExoPlayer that lives in a
media session service and is driven from the screen through a `MediaController`; the screen
itself is the picture plus a set of overlays that appear only when asked for: the live
information box, the transport controls (catch-up and VOD), the channel and group lists, the
audio and subtitle pickers, the quick actions menu, the playback info line, the dial read-out,
the score ticker and the error banner. The viewer zaps with the remote (channel up/down within
the playing channel's group, zap-back to the previous channel, number dialling), skips with a
chosen step that grows while held, chooses tracks, picture shape and subtitle look, and can
keep watching in a corner (picture in picture) or hand a live stream to another player app.
Streams drop on IPTV, so the player reconnects on its own with a visible cause and a counter.
What each remote button does on the clean screen is configurable ([Remote button
mapping](31-remote-button-mapping.md)). The Discover (addon) player reuses the controls, the
pickers, the skip ladder and the subtitle look; its own behaviour is in
[Discover](50-discover-addons.md) §4.12–4.14.

## 2. Feature checklist

- PLAY-01 Live channel playback full screen, opened from the guide, Home, Search, Sohva Sport, a reminder or the start screen.
- PLAY-02 Catch-up playback of a past programme ("Watch from start") with transport controls.
- PLAY-03 Film and episode (VOD) playback from a resume position.
- PLAY-04 Black video ground and letterbox bars in every colour theme.
- PLAY-05 "Connecting to playback service…" screen, and a full-screen message with Back when the service fails.
- PLAY-06 Live information box: channel logo or initials, name, LIVE tag, stream tags, group, programme title, start time, "% watched", time left, stop time, progress bar, next programme, stream name when it differs.
- PLAY-07 Live information box hides after 5 s idle, stays while its buttons have focus, and does not reappear by itself when the programme changes or the guide refreshes.
- PLAY-08 Live action row: hide controls, picture shape, audio, subtitles, channel list, stream details, quick actions, open in another player.
- PLAY-09 Transport controls for catch-up and VOD: title, Back, picture, audio, subtitles, progress bar, position / duration, rewind, play/pause, forward (buttons name the skip step).
- PLAY-10 Transport controls hide after 5 s idle unless focused or a track picker is open.
- PLAY-11 Channel list down the right edge: the playing channel's group, numbers (setting), logos, what is on now.
- PLAY-12 Group list beside the channel list with channel counts; browsing another group is temporary until a channel is tuned.
- PLAY-13 Channel up / down within the playing channel's group, wrapping at the ends.
- PLAY-14 Switch to the previous channel (zap-back), and back again.
- PLAY-15 Dial a channel by number on live TV, with "Channel 12" read-out and "No channel 12".
- PLAY-16 Skip back / forward by the chosen step (10 s, 30 s, 1 min, 2 min); quick repeated presses climb to 2 min (held keys too in the Discover player); the size of each skip shows for a moment.
- PLAY-17 Audio track picker; step to the next audio track.
- PLAY-18 Subtitle picker with Off; subtitles on / off toggle.
- PLAY-19 VOD audio and subtitle language preferences (primary, secondary); subtitles stay off when the primary audio language is present.
- PLAY-20 Quick actions menu (hold OK, or Menu): audio, subtitles, picture shape, playback info, score ticker.
- PLAY-21 Picture shape: Fit, Fill, Zoom.
- PLAY-22 Playback info line (resolution and frame rate, codecs, bitrate, subtitle format, buffer, dropped frames) with a clock.
- PLAY-23 Buffering indicator whenever the player buffers.
- PLAY-24 Automatic reconnection (Standard: 3 tries; Persistent: 8 tries) with a banner naming the cause and the attempt, and a Reconnect button.
- PLAY-25 Failure causes in plain words (rebuild improvement over beta 23's code-plus-exception text; see PLAY-FR-93).
- PLAY-26 Playback buffer profile: Media3 default, Low latency, Stability; applies to the next playback.
- PLAY-27 Subtitle size, colour and background, each "Follow the TV" by default.
- PLAY-28 Match the display refresh rate to the stream, and restore it afterwards.
- PLAY-29 Keep watching in a corner (picture in picture) on Home, with a Close button.
- PLAY-30 Open the live stream in another player app on the TV.
- PLAY-31 Sohva Sport score ticker over live and catch-up playback.
- PLAY-32 Leaving the app stops the stream; returning resumes it.
- PLAY-33 The screen never sleeps while the player is open.
- PLAY-34 VOD progress saved every 10 s, on pause, at the end and on leaving; watched rule applied.
- PLAY-35 A finished film returns to its details; a finished episode continues to the next one (setting) or returns to the series.
- PLAY-36 Per-source connection limit enforced before a stream opens.
- PLAY-37 Provider headers (User-Agent, Referer) sent with the stream; Sohva's own user agent otherwise.
- PLAY-38 HLS, DASH and progressive (MPEG-TS, MP4) streams, container chosen from the address.
- PLAY-39 Hardware decoding preferred, next decoder tried if one fails.
- PLAY-40 A locked channel asks for the parental PIN when zapped to; a channel outside the profile's groups is refused.
- PLAY-41 Back removes one layer at a time; from the bare picture it leaves to the guide on the channel just watched.
- PLAY-42 Remote shortcuts out of the player: guide at this channel, Home, Guide, Sohva Sport.
- PLAY-43 Playback published as a media session (system media controls, voice "pause").
- PLAY-44 Demo build shows a still picture instead of a stream.
- PLAY-45 Playback failures written to the diagnostics log without addresses or credentials.
- PLAY-46 Trakt scrobbling of VOD playback ([Trakt](51-trakt.md)).
- PLAY-47 Discover player shares the transport controls, track picker, skip ladder and subtitle look.

## 3. Entry points and navigation

### 3.1 How the player is entered

The shell owns routing ([App shell](01-app-shell-navigation.md) §4.4, SHELL-FR-20 to 31).

| Route | Opened by | Mode |
|---|---|---|
| `Player(channelId, returnToGuide = true)` | Guide, Home recent channels, Search, start screen "Last channel", zap inside the player | Live |
| `Player(channelId, returnToGuide = false)` | Sohva Sport match card, reminder Watch, reminder notification, `OPEN_CHANNEL` intent | Live |
| `Player(channelId, start, stop)` | Guide "Watch from start" on a programme with catch-up ([Catch-up](22-catchup-and-reminders.md)) | Catch-up |
| `VodPlayer(contentKey, resumePositionMillis)` | Film details, episode list, Home Continue watching, Search ([Movies and series](40-movies-and-series.md)) | VOD |
| `PinGate` → `Player` | Any of the above for a channel in the active profile's locked set ([Profiles](04-profiles-parental.md)) | Live / catch-up |

- PLAY-FR-01 The mode is decided once from the route: **timeshift** (transport controls
  shown) when the route carries a VOD content key, or both catch-up bounds; otherwise **live**.
  Catch-up with only one bound is refused by the service ("Catch-up request is incomplete").
  VOD with catch-up bounds is refused ("VOD and catch-up cannot be combined").

### 3.2 Back in every state

Back peels one layer per press, in this precedence (the first that applies wins):

| State | Back does | Focus afterwards |
|---|---|---|
| Connecting, or the service failed (message screen) | Leaves the player | Screen underneath |
| Quick actions open | Closes the menu, reveals the chrome | Transport play/pause (timeshift) or the picture (live) |
| Audio or subtitle picker open | Closes the picker, reveals the chrome | Same as above |
| Group list open (beside the channel list) | Closes the group list only | Channel list keeps its selection |
| Channel list open | Closes the list (group selection reset to the playing channel's group) | Picture |
| Live info box or transport controls visible, or the controls focused | Hides them | Picture |
| Nothing open, live, `returnToGuide` and no catch-up window | Leaves: the stack becomes `[Home, Guide]` with the guide focused on this channel | Guide row of the channel |
| Nothing open, any other case (catch-up, VOD, match card, reminder) | Leaves: pops the player | The screen that opened it |

- PLAY-FR-02 Back never reveals hidden chrome first (Back is the one key that does not reveal
  chrome on its key-down).
- PLAY-FR-03 A **held** Back performs its mapped hold action (default: switch to previous
  channel, live only) when that action applies here; then neither the key-up nor any repeat
  counts as a Back press. When the hold action does not apply (for example catch-up, or mapped to
  Nothing) Back behaves exactly as a press. See REMOTE-FR-20.
- PLAY-FR-04 While the channel or group list is open, Back is taken by the list on its key-down
  (the list, not the window, consumes it), so exactly one layer closes per press.

### 3.3 Home in every state

- PLAY-FR-05 Home (the remote's Home key, handled by Android) while the player is on top and
  "Keep watching in a corner" is on (default off), on Android 8.0+: the picture moves to a
  16:9 corner window and keeps playing; nothing but the picture is drawn there (PLAY-FR-110).
- PLAY-FR-06 Home in any other case (setting off, or the player not on top): the app goes to
  the background, which stops the stream (PLAY-FR-20). Opening the app again resumes it: live at
  the live edge, catch-up and VOD from the position where it stopped.
- PLAY-FR-07 The mapped action **Home** (REMOTE action `GO_HOME`) is different: it stays in the
  app and makes the stack `[Home]`; **Guide** makes it `[Home, Guide]`; **Sohva Sport**
  `[Home, Today]`; **Guide at this channel** makes it `[Home, Guide]` with the guide focused on
  the playing channel. Each leaves the player, which stops the stream.

### 3.4 Focus on entry and return

- PLAY-FR-08 On entry the video view takes focus (it holds the clean-screen key handler).
  Live opens with the information box visible (5 s); timeshift opens with the transport
  controls visible but **not** focused (5 s).
- PLAY-FR-09 Closing any overlay returns focus to the video view (live) or to the transport
  play/pause button (timeshift, after a picker or the quick actions).
- PLAY-FR-10 Leaving the player returns focus per the shell's rules (guide row of the watched
  channel for guide-started live playback; the Watch/episode control that started VOD).

## 4. Behaviour

### 4.1 Session, service and stream resolution

- PLAY-FR-11 Playback runs in one media session service (`MediaSessionService`, foreground
  type `mediaPlayback`, session id `streammate-live-tv`). The screen connects a
  `MediaController` to it with a session token for the service component; while connecting the
  screen shows `player_connecting` "Connecting to playback service…". A controller that cannot be
  built shows the failure's message (redacted, PLAY-FR-94); losing the service shows
  `player_service_disconnected` "Lost connection to the playback service"; a session error
  shows its redacted message or `error_unknown` "Unknown error". These message screens are a full
  `background` fill with the text centred and a Back button top-left (§5.15).
- PLAY-FR-12 The session accepts only controllers from the app's own package, trusted system
  controllers, and the media-notification controller; all others are rejected.
- PLAY-FR-13 The screen never sends a stream address. It sends a media item whose id is the
  channel id (live, catch-up) or the content key (VOD) and whose metadata extras carry
  `com.streammate.tv.extra.CATCHUP_START_EPOCH_MILLIS`, `...CATCHUP_STOP_EPOCH_MILLIS` (both or
  neither) and `com.streammate.tv.extra.VOD_CONTENT` (boolean). A blank id is refused ("A channel
  ID is required").
- PLAY-FR-14 The service resolves the request on its own coroutine scope (main dispatcher),
  after waiting for the app container to be ready and for the buffer preference to be read:
  1. Release the previous stream (save VOD progress, end the Trakt session, release the
     connection lease).
  2. Resolve the source:
     - **Live**: the active channel row; its stored (encrypted) stream address is decrypted;
       headers `User-Agent` and `Referer` from the playlist entry when present.
     - **Catch-up**: the channel must have a catch-up type and catch-up days > 0 (capped at
       365); the programme start must lie within `[now − days, now]`; the address comes from the
       catch-up URL builder ([Catch-up](22-catchup-and-reminders.md)); same headers as live.
     - **VOD**: the playable copy of the content key ([Movies and series](40-movies-and-series.md)),
       no playlist headers.
     Anything missing → "Media is no longer available" and nothing plays.
  3. Acquire a connection lease for the source (PLAY-FR-16).
  4. Replace the item's URI with the placeholder `streammate://channel/<mediaId>`, set the MIME
     type from the real address (PLAY-FR-15), and set the session title: the channel name (live),
     the film/episode title (VOD), or a catch-up title (beta 23 hard-codes the Finnish
     `"Arkisto · <channel>"`; the rebuild uses a string resource, see §10 L-21).
  5. For VOD start the progress loop (PLAY-FR-130).
  Any failure releases the lease and fails the request.
- PLAY-FR-15 Container type from the address path (query and fragment removed, trailing `/`
  trimmed, lower-case): ends with `.m3u8` → HLS; `.mpd` → DASH; `.ism`, `.isml` or `/manifest`
  → Smooth Streaming; anything else → no MIME type (progressive: MPEG-TS, MP4). Without the MIME
  type the extensionless placeholder would always build a progressive source and HLS/DASH could
  never play. Beta 23 bundles no Smooth Streaming module (§10 L-24).
- PLAY-FR-16 Connection limit: each source has a limit (Settings, 1–16, default 1). A lease
  is counted per source id in memory; acquiring at the limit fails with
  `error_source_connection_limit` "The connection limit for %1$s (%2$d) is already in use"
  (source name, limit). A lease is released exactly once (closing twice is harmless). The
  player releases the old stream's lease **before** the next channel acquires one, so zapping
  works with a limit of 1 (the screen stops and clears the old item first, PLAY-FR-23).
- PLAY-FR-17 Every request the data source makes for the placeholder is rewritten to the real
  address with the headers added; a `User-Agent` from the playlist (any casing) wins, otherwise
  `Sohva TV/<versionName> (Android TV <Build.VERSION.RELEASE>)` (unknown parts become `?`). The
  same user agent is used for playlist and guide downloads ([Sources](10-sources-and-import.md)).
- PLAY-FR-18 HTTP for streams: OkHttp, **HTTP/1.1 only** (some IPTV servers and their fronts
  reset HTTP/2 streams part-way), connect timeout 20 s, read timeout 90 s, redirects followed
  (including HTTP↔HTTPS). No call timeout.
- PLAY-FR-19 Player construction: `DefaultRenderersFactory` with decoder fallback enabled (the
  next compatible decoder is tried if the preferred one cannot start; no extension decoders),
  `DefaultMediaSourceFactory` over the resolving data source, the load control of the chosen
  buffer profile (PLAY-FR-86), and `videoChangeFrameRateStrategy = ONLY_IF_SEAMLESS`.

### 4.2 Opening, lifecycle and release

- PLAY-FR-20 Leaving the app (activity `ON_STOP`) stops the stream but keeps the item; coming
  back (`ON_START`) prepares and plays again if an item exists (nothing happens when nothing was
  chosen yet). Reason: a provider counts an open stream against the account's limit whether or
  not anyone watches, and audio continuing after Home reads as an app that refuses to close.
  Picture in picture pauses the activity without stopping it, so the corner keeps playing.
- PLAY-FR-21 Removing the app's task stops the player, clears its items and stops the service.
  Destroying the service saves VOD progress, ends the Trakt session, releases the lease, the
  session and the player.
- PLAY-FR-22 On opening (and whenever the channel, window or content key changes) the screen
  sets the item with the start position (`resumePositionMillis`, never negative; 0 for live and
  catch-up), prepares and plays.
- PLAY-FR-23 On channel change or leaving the player, the screen removes its listener, stops the
  player and clears its items; clearing the items makes the service release the source and its
  lease. Zapping reuses the same controller; only leaving the screen releases the controller.
- PLAY-FR-24 While the player screen is composed and connected, the host view's keep-screen-on
  flag is set; the previous value is restored when it leaves.
- PLAY-FR-25 Demo builds: when a demo picture exists no item is set; the picture fills the screen
  (crop) under the chrome.

### 4.3 Clean-screen keys (summary)

The complete dispatch is in [Remote button mapping](31-remote-button-mapping.md) §4.6. The
player-side facts:

- PLAY-FR-30 The video view holds focus on the clean screen and receives every key through one
  key listener. Presses are decided on key-up, holds on the first auto-repeat.
- PLAY-FR-31 Up/Down **presses** first step into whatever box is open: into the live info box's
  action row if that box was visible **when the key went down**, or (timeshift) into the
  transport controls, focusing play/pause. Only otherwise does the mapping decide.
- PLAY-FR-32 A mapped action that does not apply to this mode (live-only action in timeshift or
  the reverse), or that has nothing to act on (no neighbour channel, no previous channel, a
  track toggle with no tracks), falls back to "reveal the chrome" (and, in timeshift, OK also
  focuses play/pause).
- PLAY-FR-33 Keys outside the mapping grid (media keys, colour keys, Guide) reveal the chrome on
  their first key-down and are otherwise left to Android (media keys then reach the active media
  session; see open question Q-09). Digits are handled by dialling (PLAY-FR-59).

### 4.4 Live information box

- PLAY-FR-40 Content comes from the guide for the playing channel only: a timeline read of that
  one channel for the window `[floor30(now) − 60 min, floor30(now) − 60 min + 7 h]`, observed
  and re-subscribed when the 30-minute bucket changes. "Now" advances every **30 s**. The current programme is the
  one with `start ≤ now < stop`; the next is the first starting at or after the current one's
  stop (or now when there is none).
- PLAY-FR-41 Lines (layout in §5.2): logo tile; channel name; LIVE tag, stream tags read from
  the channel name ([Live TV guide](20-live-tv-guide.md)), group title; programme title or
  `player_no_current_programme` "No programme information"; when a programme is on: start time,
  `player_watched_percent` "%1$d %% watched", time left (`player_remaining_hours` "%1$d h %2$d
  min left" or `player_remaining_minutes` "%1$d min left"), stop time and the progress bar; next
  programme `player_next_programme` "Next %1$s · %2$s" (time, title); the stream name from the
  session when it differs (case-insensitive) from the channel name.
- PLAY-FR-42 Figures: fraction = `(now − start)/(stop − start)` only while `start ≤ now < stop`
  and `stop > start`, else no percentage and an empty bar; percentage = `floor(fraction × 100)`;
  minutes left = `ceil((stop − now)/60 000)`, none once ended; below 60 minutes only minutes,
  otherwise hours and remainder. Times are `HH:mm` in the configured time zone (falling back to
  the device zone when the id is invalid).
- PLAY-FR-43 Visibility is keyed on **interaction only**: it shows on entering the channel and
  on each reveal (any grid press that falls back to chrome, OK press = Programme info, closing a
  picker). It hides after **5 000 ms** without interaction. While any of its action buttons has
  focus it does not time out; Back hides it. Programme rollovers, EPG refreshes, a row briefly
  vanishing during a guide refresh and changed programme ids never reveal it and never restart
  its timer. It is not shown in timeshift or while the channel list is open.
- PLAY-FR-44 Action row, left to right (icon buttons, §5.4): Hide controls (`player_action_back`
  "Hide these controls"), Picture shape (`player_picture_mode` "Picture: %1$s"), Audio
  (`player_audio_track` "Audio: %1$s") → audio picker, Subtitles (`player_subtitle_track`
  "Subtitles: %1$s") → subtitle picker, Channel list (`player_action_channels` "Channel list"),
  Stream details (`player_action_stats` "Stream details", shown selected while the info line
  is on), Quick actions (`player_action_quick` "Quick actions"), and, live only, Open in another
  player (`player_external` "External player"; `player_opening_external` "Opening…" and disabled
  while busy). The descriptions are the buttons' accessibility labels.
- PLAY-FR-45 Up or Down pressed while the box is visible (judged at key-down) moves focus into
  the row: the box becomes visible if it was hiding and focus is requested once the row is
  attached (beta 23 waits a fixed 80 ms; the rebuild requests when attached).

### 4.5 Transport controls (catch-up and VOD)

- PLAY-FR-46 Shown on entry and on every reveal; hidden after **5 000 ms** unless a control has
  focus or a track picker is open (the Discover player opts into hiding even when focused,
  ADDON-FR-89). When they hide on their own, focus goes back to the video view.
- PLAY-FR-47 Contents (§5.5): the title (session title); Back (hides the controls), Picture,
  Audio, Subtitles; the progress bar (position / duration, empty when the duration is unknown);
  "position / duration" (`mm:ss`, or `h:mm:ss` from one hour); Rewind `player_rewind` "Back
  %1$s", Play/Pause (`player_play` "Play" / `player_pause` "Pause"), Forward `player_forward`
  "Forward %1$s", where `%1$s` is the chosen step ("10 s", "30 s", "1 min", "2 min").
- PLAY-FR-48 Position, duration and playing state are polled every **500 ms** while in
  timeshift (beta 23 polls even while the controls are hidden; the rebuild polls only while they
  are visible, §9). An unknown or non-positive duration counts as 0.
- PLAY-FR-49 Entering the controls by remote (Up/Down press, OK press, Show controls, Programme
  info) focuses **Play/Pause**. Every button press restarts the idle timer.

### 4.6 Channel list and group list (live)

- PLAY-FR-50 The channel list shows the channels of the **playing channel's group**: its custom
  group title if the viewer renamed it, else the provider's group title; a channel with no group
  lists the other ungrouped channels. Order, hidden channels and rules are the guide's
  ([Channel management](21-channel-management.md), [Library organization](42-library-organization.md)).
  Each row: number (when "Channel numbers" is on: the channel's own number, else its position in
  this list), logo, name, and the title of the programme on now or "No programme information".
  Beta 23 observes this group query continuously (it also serves channel up/down) and
  re-subscribes it when the **5-minute** bucket of "now" changes, so a now-playing title can lag
  up to 5 minutes.
- PLAY-FR-51 Groups on offer come from the guide's rail: provider order, merged across sources
  by shown title (counts summed), blank or missing titles left out, groups switched off by a rule
  left out; no "All" entry.
- PLAY-FR-52 Keys while the list is open (acted on key-down; the list owns every key and resets
  the press/hold resolver):

  | Key | Channel list active | Group list active |
  |---|---|---|
  | Up / Down | Previous / next row, wrapping | Previous / next group, wrapping |
  | OK / Enter | Tunes the selected channel if it is not the playing one, and closes | Shows that group in the channel list (selection = playing channel if it is that group, else the first row); closes the group list |
  | Right | Opens the group list on the group being shown (if any groups) | — |
  | Left / Back | Closes the list | Closes the group list |
  | Anything else | Not handled | Not handled |

- PLAY-FR-53 Opening always starts on the playing channel's group with the playing channel
  selected. A group picked in the list is shown only while the list is open; closing resets it.
- PLAY-FR-54 Scrolling: the list is scrolled in the same key press that moves the selection, to
  first-visible row `max(0, selected − rowsOnScreen/2)` (integer division), so the highlight
  walks down to the middle before the list moves. `rowsOnScreen` is the laid-out count, or 7
  (channels) / 10 (groups) before the first layout.
- PLAY-FR-55 Opening requires at least one channel (group list: also at least one group); the
  mapped action otherwise falls back to revealing the chrome.

### 4.7 Zapping, previous channel, dialling (live)

- PLAY-FR-56 Next / previous channel step through the list of PLAY-FR-50 around the playing
  channel, wrapping; offered only live, when the playing channel is in the list and the list
  has more than one channel. Remote defaults: Up hold / CH− press = next, Down hold / CH+ press
  = previous (REMOTE §4.4).
- PLAY-FR-57 Every channel change inside the player (step, list, dial, zap-back) goes through
  the shell's zap (SHELL-FR-22): profile group check (refused → toast `profile_content_blocked`
  "This profile cannot watch that channel", nothing changes), locked channel → PIN gate on top
  of the player (the player leaves composition, its stream stops; unlocking replaces gate and
  player with the new channel; Back from the gate returns to the old channel, which reopens),
  otherwise the channel is recorded as recent (front of the profile's recent list, max 20;
  `last_channel_id`) and the player route is replaced. The guide's focus follows the channel.
- PLAY-FR-58 Zap-back (switch to previous channel): the session remembers the channel watched
  before this one (SHELL-FR-24): before any play or zap, the candidate is the playing channel,
  else the persisted last channel, else the remembered one; it becomes "previous" when it
  differs from the channel about to play. Offered only live and when it differs from the playing
  channel; repeating it swaps back. Not persisted beyond `last_channel_id`.
- PLAY-FR-59 Dialling: digit keys 0–9 and numpad 0–9 on live only (in timeshift digits are not
  handled). Each first key-down (not repeats) appends to a buffer of at most **4** digits;
  **2 000 ms** after the last digit the number is looked up and the buffer cleared. The lookup
  uses the guide's All-channels order: a channel whose own number equals the number, else the
  channel at position `number − 1` if that channel has no own number. Found → zap (PLAY-FR-57);
  not found → `dial_channel_none` "No channel %1$d" for **1 500 ms**. While typing the read-out
  shows `dial_channel` "Channel %1$s". The lookup finishes before the buffer is cleared (clearing
  first cancelled the lookup and the channel never changed).

### 4.8 Skip step and hold acceleration (timeshift)

- PLAY-FR-60 Setting "Skip step" (`playback_seek_step_title`): `playback_seek_step_10s` "10
  seconds" (default), "30 seconds", "1 minute", "2 minutes". Help `playback_seek_step_help` "How
  far Left, Right or a mapped skip button moves; held, the skip grows up to two minutes."
- PLAY-FR-61 Ladder: 10 s, 30 s, 60 s, 120 s. A skip in the same direction within **1 200 ms**
  of the previous skip extends the streak; a pause longer than that, or the other direction,
  resets it to 0. Start rung = the first ladder step ≥ the chosen step; rung = start + streak / 3
  (integer), capped at the last; distance = max(chosen step, ladder[rung]). So with 10 s: presses
  1–3 skip 10 s, 4–6 skip 30 s, 7–9 skip 60 s, then 120 s. In the IPTV player acceleration comes
  only from **repeated presses** (clean-screen Left/Right presses, the Rewind/Forward buttons, or
  mapped skip actions): a held clean-screen key is a single hold gesture (one action, repeats
  swallowed), and with the default mapping Left/Right **hold** in catch-up or VOD is "Switch to
  previous channel", which does not apply there — so holding Left in a film skips nothing at all
  (§10 L-29, Q-14). The Discover player seeks on every Left/Right key-down including auto-repeats,
  so holding there climbs the ladder every ~50 ms.
- PLAY-FR-62 Seeking: target = position ± distance, not below 0, and not beyond the duration
  when the duration is known.
- PLAY-FR-63 Feedback: the signed distance ("+30 s", "−2 min" with U+2212) shows for **900 ms**
  after the last skip (§5.11). Labels: whole minutes as "N min", otherwise "N s".
- PLAY-FR-64 Restart (mappable) seeks to 0. Play/pause toggles.

### 4.9 Audio and subtitle tracks

- PLAY-FR-70 Track lists: every supported track of the type, in stream order. Label = the
  distinct non-blank parts of: the track label, the language's display name in the interface
  language, "N ch" for the channel count, joined with " · "; if empty `player_track_number`
  "Track %1$d" (1-based index within its group).
- PLAY-FR-71 Audio picker (`player_select_audio` "Select audio track"): one row per track, the
  selected one marked; no tracks → one disabled row `player_no_audio_tracks` "No audio tracks".
  Subtitle picker (`player_select_subtitles` "Select subtitles"): `player_subtitles_off` "Off"
  first (marked when no text track is selected), then the tracks; no tracks → "Off" plus a
  disabled `player_no_subtitles` "No subtitles". Hint under the title:
  `player_track_picker_hint` "All available tracks are shown in this list". Opens scrolled to and
  focused on the marked row.
- PLAY-FR-72 Choosing a track clears overrides of that type, enables the type and overrides it
  to the chosen track; choosing Off disables text. The choice marks the type as chosen by the
  viewer (automatic preferences stop for this item), closes the picker, reveals the chrome and
  returns focus (PLAY-FR-09).
- PLAY-FR-73 Next audio track (mappable): needs at least two tracks; steps to the one after the
  selected (wrapping). Subtitles on/off (mappable): needs at least one track; off when any is on,
  else the first track.
- PLAY-FR-74 Current labels: audio = the selected track's label or `player_audio_automatic`
  "Automatic"; subtitles = the selected track's label or "Off". Updated on every track change.
- PLAY-FR-75 VOD language preferences (Settings, "VOD audio and subtitles": primary and
  secondary audio, primary and secondary subtitles, each "Automatic" or a language). Applied only
  to VOD, once per item, only after audio tracks are known, never after the viewer chose a track
  of that type:
  1. Audio: the first track matching the primary, else the secondary language; if neither
     exists the stream default stays. Both "Automatic" → nothing is changed.
  2. Subtitles: if the **primary audio language exists among the audio tracks** (and audio was
     not chosen by hand), subtitles are turned **off**. Otherwise the first text track matching
     the primary, else the secondary subtitle language; if a preference is set and neither
     exists, subtitles are turned off.
- PLAY-FR-76 Language matching normalises to the lower-case base code before `-`/`_` and maps
  three-letter codes: `fin`→fi, `eng`→en, `swe`→sv, `dan`→da, `nor`/`nob`/`nno`→no, `est`→et,
  `deu`/`ger`→de, `fra`/`fre`→fr, `spa`→es, `ita`→it, `nld`/`dut`→nl; others keep their base.
- PLAY-FR-77 Live and catch-up get no language preference: the stream's own default tracks and
  Media3's defaults apply.

### 4.10 Quick actions

- PLAY-FR-80 Opened by the mapped Quick actions action (defaults: OK hold, Menu press) or the
  live row's Quick actions button; not shown while a track picker is open. Title
  `player_quick_actions_title` "Quick actions"; rows with their current value on the right:
  1. `player_quick_audio` "Audio track" — value: current audio label → closes the menu, opens
     the audio picker.
  2. `player_quick_subtitles` "Subtitles" — value: current subtitle label → subtitle picker.
  3. `player_quick_picture` "Picture" — value Fit/Fill/Zoom → cycles **in place** (the menu
     stays open: the shape is judged by looking at the picture).
  4. `player_quick_stats` "Playback info" — value `player_quick_stats_on` "Shown" /
     `player_quick_stats_off` "Hidden" → toggles and closes.
  5. `player_quick_ticker` "Score ticker" — Shown/Hidden → toggles and closes; only where the
     ticker is offered (live and catch-up, not VOD).
  The first row takes focus. Back or closing returns focus as in PLAY-FR-09.

### 4.11 Picture shape

- PLAY-FR-81 Three shapes cycled Fit → Fill → Zoom (`player_resize_fit` "Fit",
  `player_resize_fill` "Fill", `player_resize_zoom` "Zoom"): Fit letterboxes, Fill stretches to
  the screen, Zoom crops to fill. Starts at Fit when the player opens and is kept across zaps
  within one player session. Cycled from the live row, the transport controls (which also
  reveals the chrome), quick actions, or the mapped action. (The Discover player cycles
  Fit → Zoom → Fill; the rebuild uses one order everywhere — owner to confirm which, Q-07.)

### 4.12 Playback info line and clock

- PLAY-FR-82 Toggled by the mapped action (default Info press), the live row's Stream details
  button or quick actions; kept across zaps within a session. While on, readings are sampled
  every **1 000 ms** (first sample at once); nothing is sampled while off.
- PLAY-FR-83 Readings, in order, each left out when not measured (Media3's "no value" is never
  printed as zero):
  1. Resolution `W×H` (U+00D7) plus " Np" when a frame rate is declared (`%.0f`), e.g.
     `1920×1080 50p`. From the selected video format, else the video size.
  2. Codecs: video MIME subtype upper-cased, then " · ", then audio MIME subtype upper-cased plus
     channel layout (1 → "1.0", 2 → "2.0", 6 → "5.1", 8 → "7.1", other → "N ch"), e.g.
     `AVC · MP4A-LATM 2.0`. Either half alone when the other is unknown.
  3. Video bitrate `%.1f Mb/s` (bitrate, else average, else peak).
  4. `player_stats_subtitles` "Subtitles" + the selected text track's whole MIME type (tells
     styleable text such as `text/vtt` from pictures such as `application/pgs`).
  5. `player_stats_buffer` "Buffer" + buffered-ahead seconds `%.1f s` (always shown, never
     negative).
  6. `player_stats_dropped` "Dropped" + count, only where the player counted dropped frames (a
     `MediaController` cannot, so beta 23's IPTV player never shows it).
  Numbers use `Locale.ROOT`.
- PLAY-FR-84 While the line is on, a clock shows top-right: the wall time in the configured
  time zone, pattern from the interface locale's best pattern for "Hm" (24-hour system setting)
  or "hmma" (12-hour). It advances with the 30-second "now".

### 4.13 Buffering

- PLAY-FR-85 Whenever the player state is buffering, a centred indicator with a spinning arc and
  `player_buffering` "Buffering…" shows (a stalled stream and a dead one otherwise look
  identical). It disappears on any other state.

### 4.14 Buffer profiles

- PLAY-FR-86 "Playback buffer" (`playback_buffer_title`), default **Media3 default**:

  | Profile | min buffer | max buffer | start after | restart after rebuffer |
  |---|---|---|---|---|
  | Media3 default (`playback_buffer_default`) | Media3's own `DefaultLoadControl` (app sets nothing) | | | |
  | Low latency (`playback_buffer_low_latency`) | 5 000 ms | 15 000 ms | 1 000 ms | 2 000 ms |
  | Stability (`playback_buffer_stability`) | 60 000 ms | 120 000 ms | 5 000 ms | 10 000 ms |

  No profile sets a target buffer size in bytes (see §9 for the rebuild's byte caps).
- PLAY-FR-87 A changed profile applies at once when nothing is loaded (the idle player is
  replaced and the session switched to the new one); otherwise it is remembered and applied as
  soon as the player's item list becomes empty. Help: `playback_buffer_help` "Low latency for
  responsive live streams, Stability for unreliable connections; applies to the next playback."

### 4.15 Errors and reconnection

- PLAY-FR-90 On a player error: the banner text becomes `player_playback_failed` "Playback
  failed: %1$s" with the failure detail (PLAY-FR-94), the attempt counter increases, and the
  diagnostics log gets `player` / `<id>: <detail>, attempt N`.
- PLAY-FR-91 While the attempt is within the policy, after the attempt's delay the error is
  cleared and the player prepares and plays again (same source and lease):

  | Policy (`playback_reconnect_title` "Playback recovery") | Attempts | Delays before attempt 1, 2, … |
  |---|---|---|
  | Standard (default) | 3 | 2 s, 4 s, 6 s (2 s × attempt) |
  | Persistent | 8 | 2, 4, 8, 16, 30, 30, 30, 30 s |

  Banner while retrying: `player_reconnecting` "%1$s · Reconnecting %2$d/%3$d…" (message,
  attempt, maximum); after the last: `player_reconnect_stopped` "%1$s · Automatic reconnection
  stopped". The **Reconnect** button (`player_reconnect`) resets the counter, clears the error,
  prepares and plays.
- PLAY-FR-92 Reaching the ready state clears the error, the external-player error and the
  counter (so a stream that recovers and fails again gets a fresh set of attempts).
- PLAY-FR-93 (Rebuild addition — owner to confirm wording, Q-01.) The banner's first line names
  the cause in words and keeps the technical detail on a second, smaller line and in the
  diagnostics log:

  | Media3 error code (group) | Proposed sentence |
  |---|---|
  | `IO_NETWORK_CONNECTION_FAILED`, `IO_NETWORK_CONNECTION_TIMEOUT` | Cannot reach the provider. Check the network. |
  | `IO_BAD_HTTP_STATUS` 401 / 403 | The provider refused the stream. The account may be in use elsewhere or expired. |
  | `IO_BAD_HTTP_STATUS` 404 / 410 | The provider no longer has this stream. |
  | `IO_BAD_HTTP_STATUS` 5xx | The provider's server failed. |
  | `IO_UNSPECIFIED`, `IO_READ_POSITION_OUT_OF_RANGE` | The connection to the provider broke off. |
  | `IO_INVALID_HTTP_CONTENT_TYPE`, `PARSING_*` | The provider sent something that is not a playable stream. |
  | `DECODER_INIT_FAILED`, `DECODING_FORMAT_UNSUPPORTED`, `DECODING_FORMAT_EXCEEDS_CAPABILITIES` | This TV cannot decode the stream's picture or sound format. |
  | `BEHIND_LIVE_WINDOW` | (no message: seek to the live edge and prepare again, not counted as an attempt) |
  | Connection limit (the app's own) | `error_source_connection_limit` as today |
  | Anything else | The stream stopped playing. |

- PLAY-FR-94 Failure detail (today's text): the Media3 error code name, plus — from the
  innermost cause in the cause chain whose message is non-blank and not equal to the code name —
  " · <ExceptionSimpleName>: <message>", with addresses and credentials removed by the secret
  redactor and the message cut to **140** characters. Example: `ERROR_CODE_IO_BAD_HTTP_STATUS ·
  InvalidResponseCodeException: Response code: 403`.
- PLAY-FR-95 Failures before playback (source gone, connection limit, bad catch-up window) fail
  the add-media request in the service; how Media3 surfaces that to the screen is Q-02 — the
  rebuild must show them in the same banner with the plain sentence.

### 4.16 Subtitle appearance

- PLAY-FR-100 Three settings, each defaulting to **Follow the TV** (the TV's accessibility
  caption style when the TV has one enabled, else Media3's default style):
  - Size: Follow the TV (TV's size, embedded font sizes honoured), Small ×0.8, Normal ×1.0,
    Large ×1.3, Very large ×1.6 of Media3's default fractional size (embedded sizes ignored).
  - Colour: Follow the TV, White `#FFFFFFFF`, Yellow `#FFFFE14D`.
  - Background: Follow the TV, None, Shadow, Box.
- PLAY-FR-101 Style: when colour and background both follow the TV, the TV's style is used whole
  and embedded styles are honoured. Otherwise embedded styles are ignored and the style is
  foreground = chosen colour or the TV's; background: Follow the TV → the TV's background, window
  and edge with the chosen foreground; None → transparent background and window, no edge; Shadow
  → transparent background and window, drop-shadow edge `#FF000000`; Box → background
  `#CC000000`, transparent window, no edge. Picture-based subtitles (PGS, VobSub, DVB) keep their
  own look. Applied when the view is created and whenever the settings change.
- PLAY-FR-102 Help: `subtitle_style_help` "Follow the TV keeps the TV's own caption settings.
  Picture-based subtitles keep their own look."

### 4.17 Match the display to the picture

- PLAY-FR-103 Setting "Match the display to the picture" (`auto_frame_rate_title`), default
  **on**. When the stream's frame rate is known and the setting is on, among the display modes at
  the **current physical resolution** pick a refresh rate `r` whose multiple `m = max(1,
  round(r / fps))` is within **0.5 %** (`|r − fps × m| / fps ≤ 0.005`); prefer the smallest `m`,
  then the smallest error. Nothing qualifies → leave the display alone (50 fps is never shown at
  60 Hz by a switch). 23.976 fits 24 Hz; 25 fits 50 Hz; 59.94 fits 60 Hz.
- PLAY-FR-104 The chosen mode is requested through the window's `preferredDisplayModeId` (only
  if it differs from the current mode). The previous preference is restored when the frame rate
  changes, the setting turns off, or the player leaves. Help: `auto_frame_rate_help` "Switches
  the TV to a matching refresh rate; the picture may go black for a second when it changes."
- PLAY-FR-105 The frame rate must come from the selected video format on every track change
  (beta 23 reads it from the info-line sample, §10 L-20).

### 4.18 Keep watching in a corner (picture in picture)

- PLAY-FR-110 Allowed only while the setting is on (`picture_in_picture_title` "Keep watching in
  a corner", default **off**) and the top destination is a player, on Android 8.0 (API 26)+.
  Home (user-leave hint) enters it with aspect 16:9 and one action, Close
  (`picture_in_picture_close` "Close", system icon `ic_menu_close_clear_cancel`) that sends the
  package-scoped broadcast `com.streammate.tv.action.PICTURE_IN_PICTURE_CLOSE`; Close finishes
  the activity, which stops the stream. Failure to enter is ignored (SHELL-FR-50 to 54).
- PLAY-FR-111 In the corner the player composes the picture only: no scrim, chrome, lists,
  pickers, banners, clock, dial read-out or ticker. Their state is kept and they are drawn again
  when the app returns to full screen. Back then works as usual.
- PLAY-FR-112 Help: `picture_in_picture_help` "Home while watching shrinks the picture to a
  corner over the TV's home screen. Open Sohva TV again to bring it back to full screen, then
  press Back to stop it. Some TV home screens also put a close button on the corner itself."

### 4.19 Open in another player (live only)

- PLAY-FR-115 Offered as the last live action (not for catch-up or VOD). On press (ignored while
  busy): mark busy, stop the stream and clear the item (releasing the lease), wait **150 ms**,
  then resolve the channel's live source (acquiring and immediately releasing a lease) and start
  `ACTION_VIEW` with the real address, type `video/*`, `FLAG_ACTIVITY_NEW_TASK`, and — when the
  channel has headers — the extras `com.android.browser.headers` (a Bundle of header name →
  value) and `headers` (a String array name, value, name, value…). No chooser of its own.
- PLAY-FR-116 Success → the player leaves (as Back from the bare picture). Failure → the banner
  shows `player_external_failed` "Could not open the external player: %1$s" (redacted message;
  channel gone → `external_channel_unavailable` "The channel is no longer available") and the
  live stream is started again.

### 4.20 Score ticker (live and catch-up)

- PLAY-FR-120 Toggled by the mapped Score ticker action or quick actions; the on/off state lives
  for the app session (survives leaving the player). Offered only in the live/catch-up player.
- PLAY-FR-121 Rows: followed matches that are live (by start time), then scheduled ones
  starting within **3 h** (by start time), at most **4**. Empty →
  `player_score_ticker_empty` "No followed matches on or starting soon." Data is the Sohva Sport
  screen's own state; while the ticker is visible in a player, sport data polls as if the sport
  screen were showing (same policy, never faster); turning it on with no events refreshes once
  ([Sohva Sport](60-sohva-sport.md)).
- PLAY-FR-122 Placement: top-right, below the clock when the info line is on (so they never
  overlap), §5.14.

### 4.21 VOD progress, resume and completion

- PLAY-FR-130 For VOD the service looks up the Trakt identity once (only when an account is
  connected for the active profile), begins the Trakt session, then every **10 000 ms** saves
  progress and reports Trakt progress. Progress is also saved when playing stops (pause, buffering
  stall, stop), at the end of the stream, and when the source is released (next item, clearing,
  service destroyed).
- PLAY-FR-131 Saving ignores positions under **5 000 ms** and unknown durations; the position
  is capped at the duration. Watched when position ≥ **90 %** of the duration, or the title is at
  least **10 min** long and at most **3 min** remain; a watched entry stores position = duration
  ([Movies and series](40-movies-and-series.md) owns the rule and Continue watching).
- PLAY-FR-132 End of stream (Media3 ended state, VOD only) calls the completion handler exactly
  once per item: with "Continue to the next episode" on (default on) and a next episode, the
  player is replaced by that episode from 0; otherwise the player is popped and, when the screen
  underneath is not a details page (playback began in Search), the film's or series' details
  page is pushed. A completion arriving after the viewer navigated away changes nothing
  (SHELL-FR-29). Catch-up end of stream does nothing (Q-06).

### 4.22 Audio focus and media session

- PLAY-FR-135 Beta 23's IPTV player requests **no audio focus** and does not pause on "becoming
  noisy"; the Discover player does both. The rebuild sets media audio attributes with focus
  handling and becoming-noisy on both players (Q-05).
- PLAY-FR-136 The session exposes title only (no artwork). System media controls and voice
  commands act on the session's player.

### 4.23 Shared with the Discover player

- PLAY-FR-140 Shared: the transport controls composable (with `autoHideWhileFocused` and
  focus requesters for the audio and subtitle buttons), the track picker overlay, the seek
  stepper and step labels, the subtitle appearance, black video ground, the 40/40/28 dp control
  placement. Not shared: Discover owns its ExoPlayer in-process (no session), its start-up stages,
  subtitle addons and timing, retry with a fresh source, background stop without auto-resume,
  and hard-coded keys (no mapping). The rebuild builds one player core (surface host, controls,
  pickers, skip, subtitle look, buffer policy, audio focus) that both use.

## 5. Screen anatomy

No design extract exists for the player; these values are from `PlayerOverlays.kt`,
`PlayerScreen.kt`, `PlayerDiagnostics.kt`, `ScoreTicker.kt` and `ChannelDial.kt`. Tokens are
the palette roles and type scale of [Design system](../design/01-design-system.md); Original
theme values are given in brackets for orientation. Reference picture:
`design/screenshots/older-builds/2026-09-02-demo/06-live-player.png` (live info box; structure
unchanged in beta 23). Layout is at 960×540 dp.

### 5.0 Layer order (bottom to top)

1. Black full-screen ground (`#000000`, independent of theme).
2. Video surface (PlayerView, `SurfaceView`, controller disabled), full screen, resize mode per
   picture shape.
3. Demo picture (demo builds only).
4. Chrome scrim (only while the live box, the transport controls or the channel list is up).
5. Clock (info line on). 6. Dial read-out. 7. Live information box. 8. Transport controls.
9. Buffering indicator. 10. Info line. 11. Skip feedback. 12. Score ticker. 13. Channel and
group lists. 14. Track picker. 15. Quick actions. 16. Error banner (above everything).

In picture in picture only layers 1–2 exist.

### 5.1 Chrome scrim

Full screen, vertical gradient of `background` [#05070D]: 0 % → α 0.62, 16 % → α 0, 52 % → α 0,
100 % → α 0.94. Fades in/out with Compose's default fade.

### 5.2 Live information box

- Anchored bottom-start, padding start 40, end 40, bottom 28; full width; its own vertical
  gradient behind it: 0 → transparent, 0.42 → `playerInfoSurface` [#252A31] α 0.16, 1.0 →
  `backgroundBottom` [#04060A] α 0.52. No panel, no border.
- Row (centre-aligned): logo tile **64×64**, shape small (8), fill `surfaceRaised`; logo
  `Fit`; without a logo the first two characters of the name upper-cased, 18 sp Black, `focus`
  [#2DE2E6]. Then 16 dp, then a column:
  - Channel name: headline size 22/27 sp, **Black**, `textPrimary` [#F2F5F9], 1 line, ellipsis.
  - Row, top 6, spacing 8: LIVE tag (`guide_live` "LIVE": caption 12/16 Bold `textPrimary` on a
    solid `danger` [#FF3B5C] fill, padding 8×3, shape small); stream tags (caption Bold,
    `textMuted` [#93A1B5] on `textMuted` α 0.14); group title (label 14/19, `textMuted`, 1 line).
- Programme title: width 62 % of the box, top 12; title size 28/32 sp, **Black**, letter spacing
  −0.3 sp, `textPrimary`, 1 line.
- Facts row (full width, top 8): "12:00  ·  24 % watched  ·  46 min left" (separator is two
  spaces, middle dot, two spaces) body 16/23 `textMuted`, weight 1, 1 line; stop time body
  `textMuted` at the right end (over the bar's end).
- Progress track (full width, top 8), §5.3.
- Next line: width 62 %, top 8, label 14/19, `textDim` [#5B6981], 1 line.
- Stream name line (only when different): width 62 %, top 4, caption 12/16, `textDim`, 1 line.
- Action row: top 12, spacing 10, icon buttons §5.4 in the order of PLAY-FR-44.

### 5.3 Progress track

Height 13 (the thumb). Track 5 dp high, shape small, `textPrimary` α 0.20; fill to the fraction
in `focus`, same shape; thumb 13×13 circle `textPrimary`, its left edge at
`(width − 13) × fraction`.

### 5.4 Icon action (live row)

44×44, shape small (8), rest fill `surface` [white α 0.06], icon 20×20 tinted `textPrimary`;
focused: fill `textPrimary`, icon `background`; selected: fill `surfaceFocused` [white α 0.14];
disabled: icon `textDisabled`. No scale on focus. Fill colour animates with the default spring.
Icons: Back, Aspect, Audio, Subtitles, Channels, Stats, Settings (quick actions), Forward
(external player) ([Icons](../design/04-icons-and-imagery.md)).

### 5.5 Transport controls

- Anchored bottom-start, padding 40/40/28, full width, no panel (the scrim makes it readable).
- Title: 62 % width, headline 22/27 sp **Black**, `textPrimary`, 1 line.
- Row top 10, spacing 8: compact action buttons Back (Back icon), "Picture: Fit", "Audio: …",
  "Subtitles: …".
- Progress track, full width, top 12.
- Row top 10, centred: "00:42 / 01:32:10" 12 sp `textMuted`, end padding 18; compact "Back 10 s"
  (Rewind icon); Play/Pause **normal size** with Play/Pause icon and 10 dp horizontal margins;
  compact "Forward 10 s" (Forward icon).
- Action button ([Components](../design/02-components.md)): shape small; compact padding 12×7,
  caption 12/16 Bold, icon 16, gap 7; normal padding 18×11, label 14/19 Bold, icon 18, gap 9;
  rest `surface`/`textPrimary`; focused fill `textPrimary`, text `background`, scale 1.03,
  10 dp black shadow.

### 5.6 Channel list and group list

- Anchored centre-end, full height; a row of [channel pane][group pane]: the group pane, when
  open, takes the right edge and pushes the channel pane left.
- Channel pane: width **330**; horizontal gradient of `background`: α 0 at 0 %, α 0.82 at 22 %,
  α 0.97 at 100 %; padding start 28, end 20, top 24, bottom 20.
  - Header (start 10, bottom 12): `player_channels` "Channels" upper-cased, overline 12/16 Bold
    +1.4 sp, `textDim`, weight 1; right: "Groups →" (`guide_groups` + " →"), caption `textDim`.
  - List: spacing 2, bottom content padding 14. Row **66** high, shape medium (12), padding h 10:
    - Fill: selected while the channel pane is active `textPrimary`; selected while the group
      pane is active `surfaceFocused`; else none.
    - Number (setting on): min width 28, end padding 8, right-aligned, caption Bold, `textDim`
      (`background` α 0.7 on the active selection).
    - Logo tile 44×44 (as §5.2's tile).
    - Column, start 12: name label 14/19 Bold, 1 line (`textPrimary`, or `background` when
      active-selected); now-playing caption 12/16, 1 line (`textDim`, or `background` α 0.62).
- Group pane: width **220**; gradient `background` α 0.76 → α 0.97; padding start 18, end 12, top
  24, bottom 20. Header "GROUPS" (overline Bold, `textDim`, start 10, bottom 12). Rows **52**
  high, shape medium, padding h 10, spacing 2: selected fill `textPrimary` else none; name label
  Bold, up to 2 lines (`background` / `textPrimary`); count caption, start 8 (`background` α 0.62
  / `textDim`).
- The lists do not use focus: the selection is an index painted by the rows.

### 5.7 Track picker

Full-screen `scrim` [#000000] α 0.65 (166/255), panel centred: width 390–520, max height 520,
shape large (18), `panel` [#0A0F1A] α 0.97, padding 20. Header row: title 21 sp Black
`textPrimary`; compact Back button with Back icon (right). Hint 12 sp `textMuted`, top 4, bottom
12. List spacing 8, bottom 4. Row 52 high, shape small, fill `surfaceRaised` when marked else
`surfaceSubtle` [white α 0.035]; focused: 3 dp `textPrimary` border; padding h 14; mark "●" /
"○" 13 sp (`focus` when marked or focused, else `textMuted`); label 14 sp, Bold when marked,
`textPrimary` (`textDisabled` when disabled), up to 2 lines, start 12.

### 5.8 Quick actions

Same scrim; panel width 360–480, shape large, `panel` α 0.97, padding 20, spacing 8; title
18 sp Black `textPrimary`; list rows ([Components](../design/02-components.md) list row:
full-width, padding 12×8, label body, value on the right, hairline divider above every row but
the first, focus fill flip, no scale).

### 5.9 Info line and clock

Info line top-start, padding start 40, top 24; readings spaced 18; each: optional label (label
14/19, `textDim`) + 6 dp + value (label 14/19 Bold, `textPrimary`), 1 line. Clock top-end,
padding end 40, top 24; headline 22/27 Bold `textPrimary`. (Beta 23 draws the dial read-out at
the same top-start spot as the info line; the rebuild moves the dial read-out below the info
line when both show.)

### 5.10 Dial read-out

Top-start, padding start 40, top 24; 22 sp Bold `textPrimary` on `panel` α 0.94, shape medium,
1 dp `outline` border, padding 18×10.

### 5.11 Skip feedback

Bottom-centre, 120 above the bottom; 22 sp Bold `onScrim` (#FFFFFF) on `scrim` α 0.6, shape
medium, padding 18×8.

### 5.12 Buffering indicator

Centred row: fill `background` α 0.72, shape large, padding 20×14; 24×24 ring: full circle
`textPrimary` α 0.18 and a 90° arc in `focus`, both 3 dp strokes, the arc rotating 360° every
**900 ms** linearly; 14 dp gap; label body 16 sp SemiBold `textPrimary`. With animations off the
arc stands still.

### 5.13 Error banner

Bottom-centre, full rectangle (no shape), fill `dangerSurface` [#7A1624] α 0.8, padding 20×12,
centred column: message in `onDangerSurface` (#FFFFFF), default text style; Reconnect action
button (normal size) top 8. (Rebuild: the plain sentence in body, the detail in caption
`onDangerSurface` α 0.72.)

### 5.14 Score ticker

Top-end, padding end 40, top 24 (72 when the info line and clock are on). Panel width 300–420,
shape medium, `panel` α 0.92, padding 14×10, rows spaced 6. Row: "Home – Away" 14 sp Bold
`textPrimary` 1 line weight 1; 12 dp; live → score (or status) 14 sp Black `danger`, then 8 dp
and the status 12 sp `textMuted` when a score exists; scheduled → start label 14 sp Black
`textMuted`. Empty text 13 sp `textMuted`.

### 5.15 Message screen

Full-screen `background`; message centred, `textPrimary`; Back action button (normal) at top-start
with 24 padding.

## 6. Data

| Preference (DataStore key) | Values | Default | Per profile | In backup |
|---|---|---|---|---|
| `playback_buffer_profile` | DEFAULT, LOW_LATENCY, STABILITY | DEFAULT | no | yes |
| `playback_reconnect_policy` | STANDARD, PERSISTENT | STANDARD | no | yes |
| `playback_seek_step` | TEN_SECONDS, THIRTY_SECONDS, ONE_MINUTE, TWO_MINUTES | TEN_SECONDS | no | yes |
| `auto_frame_rate` | boolean | true | no | **no** (a restore turns it back on, §10 L-22) |
| `auto_play_next_episode` | boolean | true | no | yes |
| `picture_in_picture` | boolean | false | no | yes |
| `subtitle_text_size` | FOLLOW_TV, SMALL, NORMAL, LARGE, VERY_LARGE | FOLLOW_TV | no | yes |
| `subtitle_text_color` | FOLLOW_TV, WHITE, YELLOW | FOLLOW_TV | no | yes |
| `subtitle_background` | FOLLOW_TV, NONE, SHADOW, BOX | FOLLOW_TV | no | yes |
| `preferred_audio_language`, `secondary_audio_language`, `preferred_subtitle_language`, `secondary_subtitle_language` | lower-case code; absent = Automatic | absent | no | yes |
| `show_channel_numbers` | boolean | true | no | yes |
| `remote_mappings` | see [Remote mapping](31-remote-button-mapping.md) | defaults | no | yes |
| `recent_channel_ids` (profile-scoped key) | ids joined by U+001F, max 20 | empty | yes | yes |
| `last_channel_id` (profile-scoped key) | id | none | yes | yes |

- Unknown stored enum values read as the default.
- VOD progress: the catalogue progress table per profile ([Movies and series](40-movies-and-series.md)).
- In memory only: connection leases (per source id), the session's previous channel, the
  score-ticker switch, picture shape and info-line state (per player session), stats samples.
- No stream address is ever stored outside the encrypted channel / catalogue rows, and none
  enters the media session.

## 7. External interfaces

- Provider stream requests: `GET <stream address>` over HTTP/1.1 with `User-Agent` (playlist's
  or `Sohva TV/<version> (Android TV <release>)`) and `Referer` when the playlist sets one.
  Range requests as Media3 issues them for seeking in progressive VOD.
- Media session: exported service with the `androidx.media3.session.MediaSessionService` intent
  filter; foreground service type `mediaPlayback`; permissions `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- External player intent: `ACTION_VIEW`, data = stream address, type `video/*`, extras
  `com.android.browser.headers` (Bundle) and `headers` (String[]). The address carries the
  provider's credentials to the chosen app — inherent to the feature; say so in the privacy text
  ([Security and privacy](73-security-privacy.md)).
- PiP close broadcast: `com.streammate.tv.action.PICTURE_IN_PICTURE_CLOSE`, package-scoped,
  immutable pending intent, receiver not exported, registered only while in the corner.
- Display modes: `Display.getSupportedModes()`, `WindowManager.LayoutParams.preferredDisplayModeId`.
- Captioning: `CaptioningManager.isEnabled / userStyle`.

## 8. Edge cases and limits

- Very large providers (56,000 channels, 800 groups): the player never reads the whole channel
  list; the playing channel's group and its neighbours only (§9).
- Channel without a group: the list and channel up/down use the other ungrouped channels.
- No EPG for the channel: the box shows identity, LIVE, and "No programme information"; no bar.
- EPG refresh during playback: contents update; the box stays hidden (PLAY-FR-43).
- Time zone invalid: device zone. Programmes crossing midnight: times as `HH:mm` in the zone.
- Connection limit 1 with a zap: the old lease is released before the new one is taken, so a
  zap never hits the limit; the Discover player stops the IPTV service before it starts. The
  limit counts only this app's streams, not other devices on the same account.
- Stream with no video track (radio): the picture stays black; info line shows audio only.
- Unknown duration (live HLS in timeshift, broken catch-up): bar empty, "00:00 / 00:00", seeking
  not capped by duration.
- Clock changes: "now" is re-read every 30 s; the programme window re-keys by bucket.
- Process death while playing: the route is gone; the app restarts at its start screen (a start
  screen of "Last channel" plays it again).
- Low memory: the video buffer lives in the Java heap (§9); a heap spike elsewhere (sports
  matching, beta 22) has surfaced as an OutOfMemoryError on the playback thread.
- Remotes without Up/Down repeats, without CH keys, without Info/Audio/Captions: every function
  is reachable from the live action row, the transport controls or quick actions.
- Smooth Streaming addresses (`.ism`, `/manifest`) get the SS type but no SS module ships (Q-08).
- A 4K/8K stream on a box whose hardware decoder refuses it falls back to a software decoder
  that cannot keep up (§9 watchdog).

## 9. Lightweight by design

The decoder needs the CPU, memory bandwidth and GPU more than the interface does. On the
Amlogic S905Y4 / Mali-G31 box the player must add nothing to a playing stream but the video
surface.

**Nothing composed while hidden.** With no overlay visible, the composed tree is the black
ground, the video host and the key handler — no chrome composable runs, no gradient, no list, no
hidden-but-transparent layer. Each overlay is composed only while shown and disposed when hidden.
Acceptance: zero recompositions of the player tree over 60 s of playback with overlays hidden
(recomposition counter in a test), and zero Compose frames drawn (the video is a hardware
overlay; the UI thread should be idle).

**Position ticks do not recompose the screen.** Beta 23 polls position, duration and playing
state every 500 ms into state read by the whole 8,856-code-unit `ActivePlayer`, even while the
controls are hidden, and the live 30-second "now" also recomposes the whole screen. Rebuild: poll
only while the transport controls are visible; keep the polled values in a small holder read
only inside the progress bar's draw lambda and the time text (a tick redraws one bar and one
text, nothing else); the live box computes its figures when it opens and ticks every 30 s only
while visible.

**Data only when needed, in bounded windows.** Beta 23 keeps observing the playing group's
channels with their current programme, the guide rail and a 7-hour timeline even when nothing
is shown, and the VOD player runs the same guide queries for a content key that matches nothing.
Rebuild:
- Channel up/down, zap-back and the channel list read a **keyset window of ±50 channels** around
  the playing channel in the group's `(sortOrder, id)` order (id breaks ties; OwnTV study 14),
  prefetched when playback starts and refreshed on the 5-minute bucket; a window edge fetches the
  next page. The channel list pages the same way (page 100) and loads now-playing titles for the
  visible page only, when opened.
- The playing channel's timeline (7 h, one channel) is read when the box opens and on the
  30-minute bucket, not observed continuously while hidden.
- Group counts come from the rail query only when the group list opens.
- Dialling looks the number up with one indexed query (own number, else position in the
  All-channels order), never by loading every channel (beta 23 loads the whole guide per dial).
- The VOD player runs no guide query.
- The unused TMDB lookup of the live programme (beta 23 fetches it after 350 ms and never shows
  it) is not rebuilt.

**Buffer sizes for 1–2 GB boxes.** Media3's `DefaultAllocator` holds the media buffer in
`byte[]` segments on the **Java heap**, and `DefaultLoadControl`'s default target for the video
track alone is 2,000 × 64 KiB = 131,072,000 bytes (audio adds 200 × 64 KiB) — more than the
kit's 64 MB steady-state heap budget on its own, and no beta 23 profile caps it (constants as in
Media3 1.x; re-check them against the version the rebuild pins). The rebuild sets an explicit `targetBufferBytes` in every
profile (starting values, to be confirmed by meminfo sampling during 20 minutes of 1080p50
playback on the stand-in, Q-03): Media3 default durations with **32 MiB**, Low latency
**16 MiB**, Stability **64 MiB**, with `prioritizeTimeOverSizeThresholds = false` (bytes win).
On devices reporting `ActivityManager.isLowRamDevice()` or a memory class under 192 MB, Stability
is capped at 32 MiB as well. Back buffer stays 0.

**Surface.** `SurfaceView` (PlayerView's default) so video is composited by the display
hardware, never `TextureView`; no Compose `graphicsLayer`, alpha, clip or scale on the video
host; the black ground is the only layer under it. The rebuild may replace PlayerView by a bare
`SurfaceView` + Media3 `SubtitleView` in an aspect-ratio frame, which avoids inflating the unused
controller view.

**Cheap overlays.** One scrim, drawn as two bands (top 16 %, bottom 48 %) instead of a
full-screen gradient with a transparent middle; the live box drops its own second gradient.
Panels are solid fills with alpha; no blur; the focus shadow of action buttons follows
[Design system](../design/01-design-system.md)'s low-end rule. Logos are decoded at their drawn
size (64 dp and 44 dp) from the shared image cache. The buffering spinner is the only infinite
animation and stops with "animations off". Fades use the default fade.

**Frame rate from events.** The display-mode match reads the frame rate on track changes, not
from a 1-second sampling loop; the info line's 1-second sampling runs only while it is shown.

**Player thread.** ExoPlayer's playback thread is Media3's own. Adopted from the OwnTV study
(item 23): the player's application looper is a dedicated thread, not the main thread, so player
commands and callbacks never wait behind a UI frame (Q-10 confirms with a measurement). Leaving
the app releases the stream at once (PLAY-FR-20).

**Software-decode watchdog** (OwnTV study 23, rebuild addition, Q-11): when the video decoder
initialised is a software decoder (`c2.android.*`, `OMX.google.*`) and the stream is taller than
1080 lines, stop playback and show "This TV cannot play this stream's picture smoothly (<W×H>
<codec>)." instead of letting a 4K software decode stall the box.

**Background work.** While a stream plays: automatic playlist/guide refresh is deferred (beta 23
already defers it while the app is in the foreground); metadata matching, sports matching scans
and image prefetch pause or run at background priority with yields; the sport data polls only
while the ticker is visible. A viewer-requested sync continues at background priority.

**Small composables and methods.** `ActivePlayer` was 11,233 dex code units unshrunk (8,856
after R8), over ART's 10,000-unit ahead-of-time limit, so the player's main composable ran
interpreted after every start; with 41 parameters and six defaults it carried seven Compose
mask parameters and R8 emitted dex ART refused (`VerifyError: Verifier rejected class
PlayerScreenKt`, the release died on opening a stream). Rules: the player is a plain state
holder class (controller, modes, overlay flags, key dispatch) plus small composables, one per
overlay, each taking the holder or one immutable UI model (≤ 8 parameters); no method above 9,500
code units in the release dex (existing gate: 95 % of 10,000); the release dex is verified on an
emulator before every release ([Quality](../plan/06-quality-testing-release.md)).

**Start-up cost.** Nothing player-related runs at app start except creating the session token;
the service, the controller and Media3 classes load on first playback.

## 10. Lessons from the current app

- L-01 Leaving with Home kept the stream playing (no task removal): providers count open
  streams against the account; stop on `ON_STOP`, resume on `ON_START` (commit 84217c3).
- L-02 The player observed the whole guide (≈ 50,000 rows per opening) to find the group and
  step channels; it now reads one group (0ebf3bf). The rebuild goes further with keyset windows.
- L-03 Two heavy queries were keyed on the minute and re-subscribed every 60 s during playback;
  buckets of 5 min (group list) and 30 min (timeline) fixed it (b2974cb).
- L-04 A tester's 1,700-channel list failed on every channel with `ERROR_CODE_IO_UNSPECIFIED`:
  OkHttp's own user agent was dropped by panels and HTTP/2 streams were reset; Sohva's user agent
  and HTTP/1.1 fixed it, and the error now carries the innermost cause (a8207af).
- L-05 The placeholder URI keeps credentials out of the media session; it also hides the
  container, so the MIME type must be passed explicitly (`StreamMimeTypes.kt`).
- L-06 The live box reappeared on EPG updates; visibility must be keyed on interaction, and a
  test must cover rollovers, row removal and id changes (cf60d20, `PlayerChromeOverlayTest`).
- L-07 With presses decided on release, revealing chrome on key-down flashed the info box before
  the channel list, and Up/Down stepped into a box the key-down itself had opened; reveal on
  release and judge "box open" at key-down (9026371, 289bfd0).
- L-08 Films ignored the skip step and subtitle look because only the live call site passed
  them; the button strings also said "10 s" literally (9cac829). One player configuration
  object, built once from preferences, feeds every mode.
- L-09 Fixed 80 ms sleeps before focus requests lost the race with lazy layout; request when
  attached, with retries across frames (f20ecbf, 7af9ef7). Periodic `while (true) { delay }` loops
  hung Compose tests; use a ticker flow whose delay runs off the frame clock (7af9ef7).
- L-10 A finished film left a black screen; completion must always leave the player (details
  or next episode) and a stale completion must not replace a newer screen
  (`docs/PLAYBACK_COMPLETION_FIX.md`).
- L-11 A display left at 24 Hz made the rest of the interface scroll badly; restore the mode on
  every exit path (daae2d9).
- L-12 The PiP help promised a close button that the Projectivy launcher never shows; name the
  way out that works everywhere (8850ea9).
- L-13 Showing the subtitle MIME type explains why size and colour "do nothing" on picture
  subtitles (8850ea9).
- L-14 A memory spike in background sports matching surfaced as an OutOfMemoryError on the
  playback thread (ledger, 19 September 2026): the player's stability depends on every other
  job's heap discipline.
- L-15 `ActivePlayer` over the AOT limit and the 41-parameter `VerifyError` (§9; commit a78fb57,
  ledger "slow-box" entry of 23 September 2026). All tests ran the unminified debug build, so
  none could see either.
- L-16 Dead code shipped: `PlayerChromeOverlay` (used only by a test), `PlayerInlineAction`,
  `formatPlayerRange`, strings `player_channel_browser_hint`, `player_action_external`,
  `player_action_aspect`, `player_stats_video*`; a TMDB lookup whose display was removed in
  55ad0fd still runs for every programme.
- L-17 Aspect orders differ: IPTV Fit → Fill → Zoom, Discover Fit → Zoom → Fill.
- L-18 Programme times use fixed `HH:mm` while the clock follows the 12/24-hour setting; step
  labels "s"/"min" are not translated.
- L-19 Channel-list numbers fall back to the position in the **group**, dialling to the
  position in the **All** list: a channel without its own number can show "5" in the list and not
  be channel 5 on the keypad (Q-04).
- Found while writing this spec (code reading, not yet confirmed on a device):
  - L-20 The display-mode match reads the frame rate from the info-line sample, which is only
    refreshed while the info line is on; on a fresh player the display switches only after the
    info line has been opened once.
  - L-21 The catch-up session title is hard-coded Finnish, `"Arkisto · <channel>"`, and is
    shown as the title of the catch-up controls in every language. Rebuild: a string resource
    (proposed key `player_catchup_title` "Catch-up · %1$s").
  - L-22 `auto_frame_rate` is missing from the backup; restoring turns the switch back on.
  - L-23 The resolver rewrites only the placeholder scheme and throws for any other address.
    Media3 parses HLS and DASH manifests against the data source's reported (real) address, so
    segment requests carry `http(s)` addresses and would fail with "Playback source is no longer
    available" (Q-08). The rebuild resolves the placeholder only and passes every other request
    through, adding the provider's headers to all of them.
  - L-24 Smooth Streaming is detected but its module is not bundled.
  - L-25 Track selection parameters live on the service's player and carry over between items:
    "subtitles off" (or the VOD rule turning them off) stays off on the next live channel.
    Rebuild: reset text/audio overrides per item unless the viewer's choice is meant to stick
    (Q-12).
  - L-26 The error banner requests no focus; its Reconnect button is reachable only by moving
    focus from other chrome. Rebuild: when automatic attempts stop, focus Reconnect.
  - L-27 The IPTV player takes no audio focus (PLAY-FR-135).
  - L-28 With picture in picture on, opening the external player may trigger the corner (the
    user-leave hint fires as the other app starts) with an already stopped stream; the rebuild
    clears "allowed" before launching.
  - L-29 The skip-step help promises "held, the skip grows", but in the IPTV player a held
    Left/Right is a hold gesture: with the defaults it maps to live-only actions and does nothing
    in catch-up or VOD. Only repeated presses climb the ladder (mapping landed on 4 September,
    commit b2daec9; the ladder on 7 September, 8e4ff51, was wired to presses only).
- Keep: one scrim under all chrome; unboxed live box and controls; the highlight walking to the
  middle before the list scrolls; list scrolling in the same key press as the selection; the
  shape cycling in place in quick actions; "absent rather than zero" in the info line; the
  lease-before-acquire order on zap.

### Open questions

- Q-01 Plain-language cause sentences (PLAY-FR-93): wording, and whether the technical detail
  stays visible or only in diagnostics.
- Q-02 How Media3 1.11 surfaces a failed `onAddMediaItems` (connection limit, channel gone) to
  the controller in beta 23 — full-screen message, banner, or nothing. Verify on the emulator.
- Q-03 Byte caps for the buffer profiles (§9): measure heap during 20 min of 1080p50 and 4K on
  the stand-in; also read `dalvik.vm.heapgrowthlimit` of the Elisa box class.
- Q-04 Channel-list number fallback: position in the group or in the All list?
- Q-05 Audio focus for the IPTV player (recommended; changes behaviour when another app plays
  sound).
- Q-06 Catch-up end of stream: stay (today) or return to the guide?
- Q-07 One picture-shape order for both players: Fit → Fill → Zoom or Fit → Zoom → Fill?
- Q-08 Do HLS/DASH live channels play in beta 23 at all (L-23)? Test with an HLS fixture through
  the service. Bundle Smooth Streaming or drop its detection?
- Q-09 Do Play/Pause and other media keys work in the IPTV player via the session in beta 23?
- Q-10 Dedicated application looper for the player: confirm the gain on the stand-in.
- Q-11 Software-decode watchdog: threshold (height > 1080, or pixels × fps) and message wording.
- Q-12 Should a subtitle/audio choice made on one live channel carry to the next?
- Q-13 Score ticker in VOD: the beta 14 feature list says "over any playback", beta 23 offers it
  only for live and catch-up.
- Q-14 Holding Left/Right in catch-up and VOD: keep beta 23 (hold = mapped hold action, default
  nothing there) or make a timeshift Left/Right hold repeat the skip every repeat like Discover
  (which is what the setting's help text says)? Recommended: default Left/Right hold in timeshift
  = "skip, repeating", keeping zap-back and guide on the holds in live.

## 11. Acceptance tests

Unit (JVM):
- Mode: VOD key or both catch-up bounds → timeshift; neither → live.
- Back precedence: track picker > group list > channel list > chrome > leave; Back never reveals
  chrome.
- Lifecycle: stop on `ON_STOP`, resume on `ON_START` only with media, nothing on other events.
- Reconnect: Standard 2/4/6 s ×3; Persistent 2, 4, 8, 16, 30, 30, 30, 30 s; attempt outside the
  policy rejected.
- Skip ladder: single presses use the step; 3 presses per rung up to 2 min; pause or reversal
  resets; 30 s step starts at 30 s; labels "10 s", "1 min", "−2 min".
- Display-mode pick: 50→50, 23.976→24 (not 48), 25→50 when 25 is missing, 50 not 60, unknown
  rate → none, empty mode list → none.
- Subtitle look: all-follow → TV style; colour only keeps TV background/edge; background only
  keeps TV colour; each background case's colours.
- Info line formatting: full line, unmeasured bitrate absent, no codec segment, half a codec
  description, no size, size without rate, channel layouts, dropped only when counted, buffer
  never negative. Programme fraction, whole percentage, minutes rounded up, hours split.
- MIME from address (query, fragment, trailing slash, case). Buffer profile durations.
- Browser groups from the rail: provider order, merged by title, blanks out, rule-disabled out;
  scroll target (0, 0, 1, 7 for 0, 3, 4, 10 with 7 rows).
- Track language normalisation; primary-audio-present turns VOD subtitles off; foreign single
  audio keeps preferred subtitles.
- Failure detail: innermost informative cause, redacted, ≤ 140 characters; user agent format;
  playlist agent kept under any casing. Plain-language mapping table (rebuild).
- Ticker: live first, then scheduled within 3 h, max 4.
- Connection limiter: limit respected per source, double close harmless, release before acquire
  lets a zap succeed at limit 1.
- VOD completion notifies once, only for VOD, only on ended.

UI (instrumentation, synthetic content):
- Live box: visible on entry, gone after 5 s, stays hidden across rollover / row removal / id
  change, reappears on interaction with updated contents, new channel shows it again; focused row
  does not time out.
- Transport controls: visible on entry, play/pause focused on remote entry, labels show the step.
- Track picker lists every choice, focuses the current one; Off first for subtitles.
- Channel list: opens on the playing channel, Up/Down wrap, Right opens groups, OK on a group
  changes the list, Back closes one layer; highlight walks to the middle first.
- Dial: "Channel 12" read-out, zap after 2 s, "No channel 99" for 1.5 s; digits ignored in VOD.
- Quick actions: Picture cycles without closing; Playback info closes and toggles.
- Back order through every state of §3.2; Back from bare live picture lands on the guide row.
- PiP: in the corner no chrome node exists; full screen again shows them.

Emulator playback (local HTTP fixtures, generated media; never a real provider):
- MPEG-TS, HLS (relative and absolute segment URIs) and DASH live channels play through the
  service with the playlist's `User-Agent` and `Referer` on **every** request (manifest and
  segments), and Sohva's agent when none is given.
- A server that drops the connection: banner counts 1/3…3/3, then "Automatic reconnection
  stopped"; Reconnect recovers.
- Connection limit 1: zapping ten times in a row never shows the limit message.
- VOD: progress row written at 10 s, on pause and on leave; finished film returns to details;
  finished episode continues when the setting is on.
- Leaving the app stops the stream (no bytes requested after `ON_STOP`); returning resumes.

Performance (low-end stand-in, `.local/slowbox` class emulator or the box when the owner allows):
- 60 s of 1080p playback with overlays hidden: zero recompositions of the player tree, main
  thread idle between frames (Perfetto), no player-screen allocation per second.
- Java heap sampled every 5 s over 20 min of 1080p50 playback stays within the plan/07 budget
  with the byte caps.
- Zap (key-up to new `setMediaItem`) ≤ 50 ms on the stand-in with a 56,000-channel library; no
  database read on the main thread.
- Release dex verifies on an emulator; no method above 9,500 code units.

## 12. Reference: current code map

- `iptv/.../feature/player/PlayerScreen.kt` — screen entry, controller connection, `ActivePlayer` (state, key dispatch, reconnect, external player, overlays wiring), track helpers, language normalisation, message screen, dead `PlayerChromeOverlay`.
- `iptv/.../feature/player/PlayerOverlays.kt` — live box, progress track, icon action, clock, track picker, channel/group lists, transport controls, quick actions, layout constants.
- `iptv/.../feature/player/PlayerDiagnostics.kt` — stats collection, buffering indicator, info line, chrome scrim.
- `iptv/.../feature/player/PlayerFormatting.kt` — info-line strings, programme fraction, percentages, minutes left.
- `iptv/.../feature/player/PlayerDialing.kt` — dial lookup composition local.
- `iptv/.../feature/player/PlaybackLifecycle.kt` — stop/resume rule.
- `iptv/.../feature/player/PlaybackReconnectBackoff.kt` — attempts and delays.
- `iptv/.../feature/player/SeekStepper.kt` — skip ladder, step labels, seek-step composition local.
- `iptv/.../feature/player/SubtitleAppearance.kt` — subtitle look and application.
- `iptv/.../feature/player/AutoFrameRate.kt`, `AutoFrameRateEffect.kt` — mode choice and window preference.
- `iptv/.../feature/player/ScoreTicker.kt` — ticker selection and panel.
- `iptv/.../feature/common/ChannelDial.kt` — dial constants, number lookup, read-out.
- `iptv/.../iptv/playback/PlaybackRepository.kt` — live, catch-up and VOD source resolution, request extras, limit exception.
- `iptv/.../iptv/playback/PlaybackHttp.kt` — user agent, failure detail.
- `iptv/.../iptv/playback/SourceConnectionLimiter.kt` — per-source leases.
- `app/.../app/StreamMatePlaybackService.kt` — session service, ExoPlayer, resolving data source, buffer profile swap, VOD progress, Trakt scrobbling.
- `app/.../app/PlaybackBufferPolicy.kt` — profile durations.
- `app/.../app/StreamMimeTypes.kt` — container from address.
- `app/.../app/PictureInPicture.kt`, `MainActivity.kt` — corner state, params, close receiver.
- `app/.../app/ExternalPlayerLauncher.kt` — `ACTION_VIEW` hand-off.
- `app/.../app/StreamMateApp.kt` — player routes, zap/PIN/previous channel, completion, ticker state.
- `app/.../app/StreamMateContainer.kt` — HTTP clients (timeouts, HTTP/1.1).
- `core/.../app/AppPreferencesRepository.kt` — playback preferences and enums.
- `app/.../addons/AddonPlayerScreen.kt`, `AddonPlayback.kt` — Discover player sharing the controls.
- Tests: `PlayerScreenTest`, `PlayerFormattingTest`, `SeekStepperTest`, `AutoFrameRateTest`, `SubtitleLookTest`, `PlaybackLifecycleTest`, `PlaybackReconnectBackoffTest`, `ScoreTickerTest`, `StreamMimeTypesTest`, `PlaybackBufferPolicyTest`, `PictureInPictureStateTest`, `PlayerChromeOverlayTest` (device).
