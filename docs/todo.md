# Sohva TV to-do

Owner requests recorded on 1 October 2026. Keep low-end TV boxes as the acceptance target.

## Release cadence

Accumulate reviewed and tested changes. Prepare a new beta at an agreed checkpoint,
rather than after every fix. Publishing and household-device installs still require
the owner's explicit instruction for that release or action.

## Deferred features

- [ ] **Two streams on screen:** an in-app PiP window alongside the main stream.
  The current Android PiP keeps playback visible when opening the launcher; it does
  not provide a second stream. Start with Live TV. Provide remote controls to choose
  the second channel, swap streams and close the small window. Play audio from only
  one stream. Create the second player only while needed and release it on close.
  Respect provider connection limits, keep buffers bounded and prefer a lower
  resolution variant for the second stream when available. Avoid software decoding
  for the second stream. Measure concurrent decoder, memory and drawing costs on
  low-end hardware before enabling this feature there.

- [ ] **Movie and series sorting:** offer A–Z, Z–A, Recently added and Most popular
  directly in the library, including all-title and genre views. Existing release-year
  and rating orders do not mean recently added or most popular. Keep focus on the
  chosen title when changing order. Use indexed keyset paging and bounded result
  windows; never load or sort the full catalogue. Recently added needs a stable
  provider-added or first-seen timestamp that survives refreshes. Popularity needs
  an agreed source and bounded cached data, with no catalogue-wide network fetch
  when the viewer changes sort order. Measure any extra import and index costs.

## Accumulating for the next beta

- [x] Reopen a live channel that stalls (owner, 3 November 2026: build 135 still froze, then
  buffered until a channel change). A provider that stops sending but keeps the connection open
  left the player buffering with no error. A live channel buffering for the profile's rebuffer
  amount plus 10 s (15 s by default) now counts as a dropped connection and opens again from the
  live edge. Regression test `PlayerStreamStallTest` on the emulator. See the
  [decision](decisions.md). Private [preview build 136](preview-builds.md#player-preview-7-build-136)
  is installed on the Shield (3 November 2026); viewer testing pending. The owner saves
  diagnostics at the next freeze.

- [x] A calmer error banner over playback (owner, 1 November 2026: the red bar took a fifth
  to a quarter of the screen). While the app reconnects by itself the banner is one line in a
  small dark card; the detail and Reconnect appear only once the attempts stop. The addon
  "Playback stopped" banner uses the same card. See the [decision](decisions.md). Private
  [preview build 135](preview-builds.md#player-preview-6-build-135) is installed on the Shield
  (1 November 2026); viewer testing pending.

- [x] Reopen a live channel whose stream ends (owner, 1 November 2026: playback often froze
  after a short buffering, the indicator went away and the picture stayed paused). The
  provider closes a stalled live stream; Media3 reads the closed connection as the end of the
  media and stops on the last frame with no error, so the reconnect never ran. A live end now
  counts as a dropped connection: the banner names it, the attempt counter runs, and the
  channel opens again. Regression test `PlayerStreamEndTest` on the emulator. See the
  [decision](decisions.md). In private [preview build 134](preview-builds.md#player-preview-5-build-134)
  and 135, installed on the Shield (1 November 2026); viewer testing pending.

- [x] Keep the newest Live TV choice when sidebar or CH+/CH− reads are slow.
  Cancel the earlier lookup before it changes playback, and commit the channel's
  group position with the channel. Reselecting the playing channel cancels a
  pending choice without reopening the stream. Add small local playback transition
  diagnostics off the main thread. Opening focus reaches the video even if the
  initial info box arrives first. Keep for the next agreed preview; frozen build
  129 and household devices remain unchanged. See the
  [measurement log](performance-log.md#4-october-2026-sidebar-channel-choices-under-slow-reads).

- [x] Move the player channel list's highlight and scroll in the same key press
  (PLAY-FR-54). Up/Down within the rows already on screen no longer waits for a
  database read, and the list centres in the frame that draws the highlight instead
  of one frame later. The owner saw the list catch up a moment after the highlight
  on both household boxes. Group list uses the same scroll. Tested on the emulator on
  6 October 2026; keep for the next agreed preview. See the
  [measurement log](performance-log.md#6-october-2026-channel-list-highlight-and-scroll-in-one-frame).

- [ ] Confirm the reported sidebar channel-switch freeze on the affected user's
  device: **Chromecast with Google TV, Android TV 14, build UTTC.250917.004.14193937**
  (owner, 6 October 2026). Choosing a channel from the player's channel list
  freezes the picture and the channel does not change; choosing it from the Guide
  works. The user updated from beta.11 to beta.15 and says beta.11 worked. It
  happens with every channel, the sound stops too, and no message appears; the
  provider allows one connection (owner, 7 October 2026). Neither of the owner's two
  boxes shows it. The owner is asking for Save diagnostics after reproduction.
  Leading suspect: "Match the display to the picture" (default on). An in-player zap
  clears the tracks, so the frame rate becomes unknown and the display is restored
  to its original mode, then switched again when the new stream's tracks arrive: two
  HDMI mode changes per zap. A mode change can make the system report "audio
  becoming noisy", which pauses the player (frozen picture, no sound, no error). The
  noisy-pause recovery only covers switches marked by `displaySwitching()`, and the
  restore to the original mode is not marked. A Guide start makes one marked switch
  and recovers. To confirm: the diagnostics file's settings show the match-display
  value; or the user turns the setting off and retries the list. Why beta.11 behaved
  differently is not explained: the live switching path and this setting are
  unchanged between beta.11 and beta.15.
  Fix in place for the next preview (7 October 2026): an unknown rate between
  channels leaves the display alone, and every mode request is marked for the
  noisy-pause recovery. Still to confirm on the user's device; see the
  [measurement log](performance-log.md#7-october-2026-display-mode-kept-during-a-channel-change).
  Result (owner, 8 October 2026): turning the setting off on the user's beta.15 did
  not help, so display-mode switching is ruled out as the cause. The change stays
  (it removes two HDMI switches per zap). Next: the user's device needs build 130,
  whose transition records show state, play/pause reason and suppression around
  the freeze; Save diagnostics right after it happens.

- [x] Name an unreadable stream reply in Save diagnostics (playlist, web page, JSON,
  text, empty or unrecognised data; media type; redirect to another host; never the
  content or address), and retry a playlist served at a plain address once as HLS.
  Prompted by "TV" source failures on the owner's Shield, 6-7 October 2026. Tested on
  the emulator on 8 October 2026. See the
  [measurement log](performance-log.md#8-october-2026-naming-unreadable-stream-replies-and-retrying-playlists-as-hls).
  First result on Shield build 130 (8 October 2026, 00:44): a failing "TV" source film
  replies `empty, type text/html, redirected to another host` on all three attempts:
  the provider redirects to another server that returns an empty web reply. Not
  a playlist, so no HLS retry. A live channel from the same source got a spurious
  407 proxy-authentication reply. TiviMate fails the same streams with the same
  account (owner, 8 October 2026): a provider fault, nothing to change in the app.

- [x] Diagnostics on a TV without a file picker (the Chromecast user saw "no app"): save to
  the Download folder, and Send to phone through the phone page on any TV. Tested on the
  emulator on 8 October 2026. See the
  [measurement log](performance-log.md#8-october-2026-diagnostics-without-a-file-picker).

- [x] Keep the player's now/next, the channel list's programme titles and Home's "on now" row
  current while a guide, playlist or metadata import is writing (owner's Shield, build 130).
  Fixed and tested on the emulator on 11 October 2026; to confirm on the Shield. See the
  [measurement log](performance-log.md#11-october-2026-nownext-and-recents-while-an-import-writes).
  Build 132 on the Shield: Home is right; the info box and channel list were not. Follow-up
  the same day: the box reads the new channel at once on a channel change, and the list reads
  titles for the rows around the highlight. See the
  [second entry](performance-log.md#11-october-2026-info-box-and-channel-list-programmes-after-build-132).

- [x] Announce a newer public update once on Home, using the existing automatic
  check. The small notice keeps focus, points to Settings › About, disappears
  after 15 seconds and waits during playback/PiP or a modal. Remember one build
  number on the device so restarts do not repeat it. Implemented and tested on
  the emulator on 2 October 2026; keep for the next agreed preview. Household
  devices remain on frozen build 129.

- [x] Home Live TV and Continue Watching actions keep working after Android recreates
  the background activity. Keep the navigation stack with the retained screen models,
  preserve playback and management restoration rules, and release dropped screen models.
  Regression tests, focus/Back, French language changes, lint and the 1 GB emulator CPU
  comparison pass on 2 October 2026. See the
  [measurement log](performance-log.md#2-october-2026-home-actions-after-background-activity-recreation).
  Private [preview build 129](preview-builds.md#home-navigation-preview-build-129)
  is installed on Shield and Elisa with their matching profiles. The owner reports
  the background/restart scenario working on Shield; Elisa testing is pending.
  Earlier builds stay frozen.

- [x] White subtitles with Background: None remain readable on white scenes using
  a dark drop shadow and a transparent background. Verify with the native renderer
  and measure drawing CPU on the 1 GB TV emulator. Completed on 1 October 2026;
  native regression, player/model unit tests and player lint pass. See the
  [measurement log](performance-log.md#1-october-2026-subtitle-contrast-without-a-background).
  Private [preview build 126](preview-builds.md#subtitle-preview-build-126) is
  installed on the Shield for viewer testing on 2 October 2026.

- [ ] Accept the reviewed sports dictionary on Shield and Elisa. Implementation
  and emulator checks are complete; private [preview build 127](preview-builds.md#sports-dictionary-preview-build-127)
  was installed on Shield with its matching profile. Viewer testing found numeric-date
  matches incorrectly marked Possible; follow-up [build 128](preview-builds.md#numeric-date-follow-up-build-128)
  corrects the date rules and is installed on Shield with its matching profile for viewer testing.
  Follow-up [build 129](preview-builds.md#home-navigation-preview-build-129) includes
  the same sports changes and is installed on both Shield and Elisa for viewer testing.
  Test the devices separately
  with their normal sources and preserve
  their data. The first slice has 43 reviewed identities; other proposals await
  source/category review. See [the integration plan](sport-dictionary-integration.md).
