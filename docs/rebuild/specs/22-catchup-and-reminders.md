# Catch-up and reminders

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

Two features that hang off the programme guide. **Catch-up** plays a programme that has already
started or finished from the provider's archive, when the playlist says the channel has one:
"Watch from start" for the programme on air, "Watch recording" for a past one. The app builds the
archive address from the channel's catch-up type and template (M3U `catchup` attributes, or an
Xtream panel's `tv_archive`) and the programme's start and stop; nothing is recorded locally.
**Reminders** tell the viewer, a minute before a programme or a Sohva Sport match starts, that it
is about to begin, and offer to switch to it. Because Android TV never shows an app's
notification as a popup, the app presents a due reminder itself — as a dialog over whatever
screen is up, and by bringing itself to the front over another app when the viewer has allowed
"display over other apps" — with the notification only as a fallback in the TV's panel.

## 2. Feature checklist

Catch-up:
- CATCH-01 Catch-up is offered only for channels whose playlist declares it (M3U `catchup` /
  `catchup-type` / `timeshift` attributes; Xtream `tv_archive`) and only for programmes that
  started no longer ago than the channel's catch-up days.
- CATCH-02 "Watch from start" for the programme on air (guide hero and programme actions).
- CATCH-03 "Watch recording" for a finished programme (guide hero and programme actions).
- CATCH-04 OK on an airing or past programme of a catch-up channel in the guide plays it from its
  start.
- CATCH-05 Archive address schemes: `default`, `vod`, `append`, `shift`, `timeshift`, `xtream`,
  `xc`.
- CATCH-06 Template tokens: `{utc}` `{start}` `{utcend}` `{end}` `{lutc}` `{now}` `{timestamp}`
  (each also with a `:format`), `{duration}` and `{duration:N}`, `{offset:N}`, `{Y}` `{m}` `{d}`
  `{H}` `{M}` `{S}`, with an optional leading `$`.
- CATCH-07 Xtream archive address built from the live address: `/timeshift/<user>/<pass>/
  <minutes>/<yyyy-MM-dd:HH-mm>/<stream>.ts`.
- CATCH-08 Formatted times use the channel's catch-up time zone (an Xtream panel's server zone),
  else the TV's zone.
- CATCH-09 Unsafe or unusable templates are refused (non-http(s) result, `{catchup-id}`,
  unknown tokens, bad formats).
- CATCH-10 Catch-up playback shows transport controls (pause, seek) and no channel up/down; its
  title reads "Arkisto · <channel>".
- CATCH-11 Back from catch-up playback returns to the screen it was started from.
- CATCH-12 Catch-up respects the PIN lock, profile restrictions and the source's connection
  limit, and records the channel as recently watched.

Reminders:
- REM-01 "Remind me" / "Reminder set" on a guide programme that has not started (hero and
  programme actions).
- REM-02 "Remind me" / "Reminder set" on a Sohva Sport match card for a scheduled match that has
  not started.
- REM-03 A reminder fires one minute before its start, by an exact alarm, whether the app is
  running or not.
- REM-04 In-app alert over any screen: "<title> starts in a minute" / "starts now", subtitle,
  Watch (or "Open the match card") and Not now; goes away by itself 2 minutes after the start
  (at least 20 s after it appeared).
- REM-05 Several due reminders queue; one alert at a time.
- REM-06 Watch plays the programme's channel (or opens the match card when the match has no
  known channel); Back returns to where the viewer was.
- REM-07 A notification in the TV's panel ("<title> starts now", "Press to watch." / "Press to
  open the match card and choose a channel.") that opens the channel or the match card.
- REM-08 When another app is on screen, Sohva TV comes to the front with the alert if the viewer
  allowed "display over other apps".
- REM-09 The first reminder ever set explains once how reminders can open the app and offers to
  open the TV's setting.
- REM-10 Android 13+: the notification permission is asked when a reminder is set.
- REM-11 Settings > General shows "Reminders can open Sohva TV: Allowed / Not allowed" and opens
  the TV's setting.
- REM-12 Reminders survive leaving the app, a restart of the app, an update and a reboot.
- REM-13 A reminder missed by more than 30 minutes (TV off) is dropped, not fired late; a fired
  reminder is removed.
- REM-14 Reminders stay on the TV: not in backups, nothing sent anywhere.
- REM-15 No reminders in the Lab build.

## 3. Entry points and navigation

Catch-up:
- Guide hero "Watch from start" / "Watch recording", the programme actions dialog's catch-up row,
  and OK on an airing or past programme block ([Live TV guide](20-live-tv-guide.md)
  GUIDE-FR-65/-74/-79). Nowhere else starts catch-up (the player has no archive browsing).
- Playback opens the player over the guide (`Player(channel, start, stop)`); Back pops to the
  guide (not the stack reset used after live playback from the guide); the guide then opens at
  now on that channel ([App shell](01-app-shell-navigation.md) SHELL-12).

Reminders:
- Set/remove: the guide (hero, programme actions), the Sohva Sport match card
  ([Sohva Sport](60-sohva-sport.md)). The buttons toggle.
- Alert: over any destination once the start route is applied. Its buttons: Watch → live
  playback with `forGuide = false` (Back returns to the screen under the alert) or the match card
  on Sohva Sport; Not now → dismiss. First focus: Watch. Back dismisses.
- Notification tap → the app opens (or comes forward) and plays the channel / opens the match
  card ([App shell](01-app-shell-navigation.md) SHELL-FR-40/41).
- Overlay prompt: after the first set reminder; "Open TV settings" leaves to the system screen;
  returning re-reads the grant.

## 4. Behaviour

### 4.1 Where catch-up data comes from

- CATCH-FR-01 M3U, per `#EXTINF` entry ([Sources and import](10-sources-and-import.md)):
  - type = attribute `catchup`, else `catchup-type`, else `"timeshift"` when a `timeshift`
    attribute is present; trimmed, lower-cased (root locale); blank = none;
  - days = `catchup-days`, else the `timeshift` attribute's value; integer, clamped to 1..365
    (so `"0"` becomes 1); not a number = none;
  - source (template) = `catchup-source`, non-blank.
  Catch-up attributes on the `#EXTM3U` header line are ignored (only entries count).
- CATCH-FR-02 Xtream, per live stream: when `tv_archive` is `1` or `true`, type = `"xtream"`,
  days = `tv_archive_duration` clamped to 1..365 (missing or not a number = no catch-up); stream
  id = `stream_id`; catch-up time zone = the account's `server_info.timezone`.
- CATCH-FR-03 A channel without days has no catch-up even when it has a type (common M3U entries
  with `catchup="default"` and no `catchup-days` get none).

### 4.2 When catch-up is offered

- CATCH-FR-10 A programme can be played from the archive when all hold, with `now` the current
  time:
  1. the channel's type is non-blank and its days, clamped to at most 365, are > 0;
  2. `programme.start ≤ now` and `programme.start ≥ now − days × 24 h`;
  3. the type is `default`, `append` or `vod` **and** the channel has a non-blank template, or
     the type is `shift`, `timeshift`, `xtream` or `xc`.
  Any other type (`flussonic`, `fs`, …) offers nothing.
- CATCH-FR-11 The programme's start and stop are the guide's times, i.e. after the source's EPG
  offset ([Live TV guide](20-live-tv-guide.md) GUIDE-FR-114): the offset exists to correct a feed's
  times, so the corrected times are what the archive is asked for.
- CATCH-FR-12 Labels: "Watch from start" (`guide_watch_from_start`) while the programme is live,
  "Watch recording" (`guide_watch_recording`) when it has ended. Future programmes never offer
  catch-up.
- CATCH-FR-13 OK on the airing programme of a catch-up channel starts it from the beginning
  (GUIDE-FR-74); live is on the channel column and the Watch buttons.
- CATCH-FR-14 A programme is only reachable while it is stored: the importer keeps programmes
  that ended up to 12 h before the guide import (and up to 8 days ahead), so a 7-day archive can
  in practice be replayed for about the last 12 hours plus the time since the last import (see
  open questions).

### 4.3 Building the archive address

The address is built at playback time from the channel's decrypted live address, never stored,
never logged (the request's `toString` redacts the live address and the template).

- CATCH-FR-20 Refuse (no address) when `start ≥ stop` or `start > now`.
- CATCH-FR-21 Template by type (type trimmed and lower-cased):
  - `default`, `vod`: the channel's `catchup-source` is the whole template;
  - `append`: live address + `catchup-source` (concatenated as is);
  - `shift`, `timeshift`: live address + (`&` if it contains `?`, else `?`) +
    `utc={utc}&lutc={lutc}`;
  - `xtream`, `xc`: CATCH-FR-30;
  - anything else, or a missing template for the first three: refuse.
- CATCH-FR-22 Rendering a template (every value from `start`, `stop`, `now` floored to whole
  seconds; `duration = max(1, stop − start)` seconds; zone per CATCH-FR-26):
  1. A template containing `{catchup-id}` (any case) is refused.
  2. Time tokens, matched case-insensitively by
     `\$?\{(utc|lutc|utcend|start|now|timestamp|end)(?::([^{}]+))?\}`: `utc`, `start` → start;
     `utcend`, `end` → stop; `lutc`, `now`, `timestamp` → now. Without a format: epoch seconds.
     With a format: each character must be one of `Y m d H M S - _ : / . ` and space; `Y` →
     4-digit year, `m` → 2-digit month, `d` → day, `H` → hour (24 h), `M` → minute, `S` →
     second, all zero-padded, in the zone; the other allowed characters are copied. A blank format
     or any other character refuses the whole address.
  3. `\$?\{duration(?::([^{}]+))?\}` (case-insensitive): `duration / N` (integer division),
     `N` = the number after the colon, 1 when absent or not a number; `N ≤ 0` refuses.
  4. `\$?\{offset:([^{}]+)\}` (case-insensitive): `max(0, now − start) / N`; `N` missing, not a
     number or ≤ 0 refuses.
  5. `{Y}` `{m}` `{d}` `{H}` `{M}` `{S}` (exact case, no `$` form) → the start's fields as in 2.
  6. Any remaining `{…}` or `${…}` refuses the address.
- CATCH-FR-23 The rendered text must parse as an `http` or `https` URL; the normalised form of
  that URL is used (percent-encoding and host normalised). Anything else (`file:`, `rtmp:`, junk)
  is refused.
- CATCH-FR-24 Examples (start 18:30 UTC, stop 20:00 UTC, now 21:00 UTC on 24 Aug 2026; epoch
  seconds 1787596200 / 1787601600 / 1787605200):
  - `append`, template `?start={utc}&end=${end}&duration={duration}` on
    `http://provider.example/live/one.ts` →
    `http://provider.example/live/one.ts?start=1787596200&end=1787601600&duration=5400`;
  - `timeshift` on `https://provider.example/live?id=7` →
    `https://provider.example/live?id=7&utc=1787596200&lutc=1787605200`;
  - `default`, template
    `https://provider.example/{utc:Ymd-H-M}?minutes={duration:60}&offset={offset:60}`, zone
    Europe/Helsinki → `https://provider.example/20260824-21-30?minutes=90&offset=150`.
- CATCH-FR-26 Zone for formatted tokens and the Xtream start: the channel's catch-up time zone
  when it is a valid zone id (Xtream server zone), otherwise the **TV's zone**. The app's own time
  zone setting is not used, although its help text says "Used by the guide, catch-up and Sohva
  Sport" (see open questions).

### 4.4 Xtream archive addresses

- CATCH-FR-30 The live address must be an `http(s)` URL. Its non-blank path segments are
  examined: `live` = the last segment equal to `live` (any case); credentials start after it, or,
  without a `live` segment, three segments before the end. There must be three segments from
  there: user, password, and the stream file.
- CATCH-FR-31 Stream id: the channel's stored Xtream stream id when it matches
  `[A-Za-z0-9._-]{1,128}`, else the stream file's name before its first `.` when that matches;
  otherwise refuse. User and password must be non-blank.
- CATCH-FR-32 Minutes = `ceil((stop − start) / 60,000)`, at least 1. Start =
  `yyyy-MM-dd:HH-mm` in the zone of CATCH-FR-26.
- CATCH-FR-33 Address: same scheme, host and port; path = the segments before `live` (or before
  the credentials), then `timeshift`, user, password, minutes, start, `<streamId>.ts`; no query,
  no fragment; segments encoded as path segments. Examples (zone Europe/Helsinki where noted):
  - `http://provider.example:8080/live/user%40mail/password/1477.m3u8`, id 1477 →
    `http://provider.example:8080/timeshift/user@mail/password/90/2026-08-24:21-30/1477.ts`;
  - `xc` on `http://provider.example/account/password/42` (UTC) →
    `http://provider.example/timeshift/account/password/90/2026-08-24:18-30/42.ts`.

### 4.5 Starting and running catch-up playback

- CATCH-FR-40 Starting: the same gates as live playback — a channel outside the profile's
  groups shows the toast "This profile cannot watch that channel"; a locked channel asks for the
  PIN and continues into catch-up ([App shell](01-app-shell-navigation.md)); the channel is
  recorded as recently watched and remembered as the guide's focus channel; the player opens with
  the programme's start and stop.
- CATCH-FR-41 The playback service re-checks CATCH-FR-10 (type, days, window) against the
  current time, builds the address (§4.3–4.4) with the channel's user agent and referrer headers,
  and takes a connection lease on the source; when the source's limit is used up playback fails
  with "The connection limit for %1$s (%2$d) is already in use"
  (`error_source_connection_limit`).
- CATCH-FR-42 When no address can be built the item is rejected with the untranslated service
  message "Media is no longer available" (see §10 and open questions); how the player shows a
  failed start is in [Player](30-player.md).
- CATCH-FR-43 In the player ([Player](30-player.md)): the media title is
  `"Arkisto · " + channel name` (hard-coded Finnish; rebuild: a string resource, see open
  questions); transport controls are shown (pause, seek by the seek-step setting); channel
  up/down and the next/previous channel shortcuts are off; programme metadata is not looked up;
  remote-key mappings with the scope "Catch-up and films only" (`remote_scope_timeshift`) apply
  ([Remote button mapping](31-remote-button-mapping.md)).
- CATCH-FR-44 The end of an archive stream does nothing special (completion handling exists only
  for films and episodes); Back leaves to the screen below (the guide).

### 4.6 Setting and removing reminders

- REM-FR-01 A reminder record: id, kind, event id, channel id, title, subtitle, start (epoch
  ms), created at. Ids: `programme:<channelId>:<programmeId>` and `event:<eventId>`.
- REM-FR-02 Guide programme (offered only when `programme.start > now`): kind `programme`,
  channel = the row's channel, title = programme title, subtitle = channel's shown name, start =
  programme start (after the EPG offset). Programme ids are derived from XMLTV channel, start,
  stop and title, so a re-import of an unchanged programme keeps the button state.
- REM-FR-03 Sohva Sport match (offered only while the match is scheduled and its start is in
  the future): kind `event`, channel = the first stream matched as available and not rejected by
  the viewer, else none; title = `<home> – <away>` (en dash with spaces), subtitle = competition,
  start = the match's start.
- REM-FR-04 The buttons toggle: pressing on an id that has a reminder removes it; otherwise it
  is stored. The "Reminder set" state is read from the set of stored ids (all profiles share
  reminders).
- REM-FR-05 On setting (not on removing): on Android 13+ ask for `POST_NOTIFICATIONS` when not
  granted; then, when the app may not draw over other apps and `reminder_overlay_asked` is false,
  set it to true and show the overlay prompt (§4.9). The prompt appears at most once per
  installation (the flag is not in backups).
- REM-FR-06 After every set and remove the alarm is rescheduled (REM-FR-10).

### 4.7 The schedule

- REM-FR-10 Constants: lead **60,000 ms** (fire at `start − 1 min`); stale after **30 min**
  past the start. `due(now)` = reminders with `fireAt ≤ now` and `start ≥ now − 30 min`, oldest
  start first. `next(now)` = the smallest `fireAt > now`.
- REM-FR-11 Reschedule(now), in this order in the rebuild:
  1. delete reminders whose start is more than 30 min before now;
  2. if any are due, fire them now (REM-FR-13);
  3. if a next fire time exists, set **one** exact alarm for it (replacing the previous one),
     otherwise cancel the alarm.
  (The old code checks "next" first and returns when there is none, so due reminders are fired
  only when another reminder is still ahead — see §10.)
- REM-FR-12 The alarm is an alarm clock (`setAlarmClock`, exact, fires in Doze) whose operation
  is an explicit broadcast `com.streammate.tv.REMINDER_FIRE` to the reminder receiver
  (request code 0, update-current, immutable); its "show" intent opens the main activity.
  Rebuild: on Android 12+ exact alarms need the "Alarms & reminders" access; declare
  `SCHEDULE_EXACT_ALARM`, check `canScheduleExactAlarms()` before setting, fall back to an
  inexact alarm allowed while idle when it is denied, and never let a `SecurityException` escape
  (see open questions).
- REM-FR-13 Firing (receiver, off the main thread, within the broadcast's `goAsync` window):
  compute `due(now)`; add them to the in-memory ringing queue (ids already queued are not added
  twice); for each, post the notification (REM-FR-20) and delete the record; if at least one fired
  and no activity of the app is started, bring the app forward (REM-FR-30); then reschedule.
- REM-FR-14 Rescheduling also runs: at every app start (on a background thread, failures
  swallowed and logged), after a reboot (`BOOT_COMPLETED`), and — rebuild — after an app update
  (`MY_PACKAGE_REPLACED`) and after a time or zone change (`TIME_SET`, `TIMEZONE_CHANGED`), so an
  update, a force-stop or a clock change never leaves a pending reminder without an alarm until
  the next start.
- REM-FR-15 The Lab build (`com.streammate.tv.lab`) never schedules, fires or shows reminders.

### 4.8 Presenting a due reminder

- REM-FR-20 Notification: channel id `reminders`, name "Match and programme reminders"
  (`reminders_channel_name`), importance high; title "%1$s starts now"
  (`reminder_notification_title`) with the reminder's title; text = subtitle and "Press to watch."
  (`reminder_notification_watch`, when the reminder has a channel) or "Press to open the match
  card and choose a channel." (`reminder_notification_open_card`), joined with " · "; category
  reminder, priority high, auto-cancel; tap opens the main activity with `OPEN_CHANNEL=<id>` or
  `OPEN_EVENT=<id>` (new task; request code distinct per request); notification id = the
  reminder id's hash. Not posted when notifications are off or, on Android 13+, not granted (both
  logged). Android TV shows it only in the notification panel, never as a popup. Rebuild: use the
  app's monochrome small icon, not the launcher icon; cancel the notification when the in-app alert
  is answered.
- REM-FR-21 In-app alert: the first entry of the ringing queue is shown as a dialog over any
  screen once the start route is applied (it waits behind the launch screen). Title "%1$s starts
  in a minute" (`reminder_alert_title_soon`) while the start is still ahead, else "%1$s starts now"
  (`reminder_alert_title_now`); subtitle when present; rows "Watch" (`reminder_alert_watch`, Play
  icon) when the reminder has a channel, else "Open the match card"
  (`reminder_alert_open_card`), and "Not now" (`reminder_alert_later`).
- REM-FR-22 Watch: dismiss, then play the channel live with `forGuide = false` (profile gate,
  PIN gate, not recorded as recent), or open Sohva Sport on the match card. Over the player this
  pushes a new player; Back returns to the previous one.
- REM-FR-23 Not now or Back: dismiss. The alert also dismisses itself after
  `max(start + 2 min − now, 20 s)` measured when it appears.
- REM-FR-24 Dismissing shows the next queued reminder, if any.
- REM-FR-25 The ringing queue lives in memory only: if the process ends before the viewer sees
  it, only the notification remains.
- REM-FR-26 While watching: the alert appears over the playing stream; playback continues under
  it until the viewer chooses.

### 4.9 Reminders while another app is on screen

- REM-FR-30 Bring forward: start the main activity with `NEW_TASK | REORDER_TO_FRONT` and the
  marker extra `com.streammate.tv.REMINDER_ALERT=true`; the alert then shows (the marker is not
  otherwise read). Android refuses background activity starts unless the app may draw over other
  apps; a refused start is logged and the notification is the fallback. The Shield (Android 11)
  proved both: brought forward into a dead process with the grant, blocked without it (beta 8).
- REM-FR-31 "In the foreground" means at least one activity of the app is started (counted with
  activity lifecycle callbacks, so the player and picture in picture count).
- REM-FR-32 Overlay grant: allowed when the API level is below 23, else
  `Settings.canDrawOverlays`. The setting is opened with `ACTION_MANAGE_OVERLAY_PERMISSION` for the
  app's package, else the general list; if neither exists, nothing happens (logged).
- REM-FR-33 Overlay prompt (once, REM-FR-05): title "Let reminders open Sohva TV"
  (`reminder_overlay_title`), body `reminder_overlay_body` ("Inside Sohva TV a reminder always
  appears when the programme is about to start. Android TV shows no popup for an app that is not
  on screen, so to have Sohva TV come forward while something else is on, allow it to display over
  other apps in the TV settings. Without that, the reminder waits in the notification panel of the
  TV."), rows "Open TV settings" (`reminder_overlay_open`, Settings icon, first focus) and "Not
  now". The app draws nothing over other apps itself; the grant only lets it open its own screen.
- REM-FR-34 Settings > General row "Reminders can open Sohva TV" (`reminders_open_title`), help
  `reminders_open_help`, value "Allowed" / "Not allowed" (`reminders_open_allowed` /
  `reminders_open_not_allowed`), re-read whenever the app's lifecycle changes (so returning from
  the TV settings updates it); OK opens the TV setting ([Settings](70-settings.md)).

## 5. Screen anatomy

No screenshots exist for these dialogs. Both are platform dialogs without the default width.

- **Reminder alert**: column 460 dp wide, `surface` fill, `shapes.medium` (12 dp), padding 18,
  spacing 4. Title 18 sp Bold `textPrimary`, 2 lines, ellipsis, start padding 6. Subtitle 13 sp
  `textMuted`, 1 line, padding start 6 / bottom 8. Two dense list rows (`TvListRow`, dense:
  padding 12/8, 18 dp icon; [design/02-components.md](../design/02-components.md)).
- **Overlay prompt**: the same frame, 520 dp wide; body 13 sp with 18 sp line height,
  `textMuted`, padding start 6 / bottom 8; two dense rows.
- **Guide catch-up and reminder controls**: compact hero buttons (Replay icon for catch-up, Epg
  icon for reminders, the reminder button `selected` when set) and dense rows in the programme
  actions dialog ([design/screens/guide.md](../design/screens/guide.md) §1, §6).
- **Match card reminder**: right column of the match card header ([design/screens/sohva-sport.md](../design/screens/sohva-sport.md)).
- **Settings row**: value row with the Info icon ([design/screens/settings.md](../design/screens/settings.md), General).

## 6. Data

| Store | Content | Lifetime | Per profile | Backup |
|---|---|---|---|---|
| `reminders` table (key `id`, index `startEpochMillis`) | `kind`, `eventId`, `channelId`, `title`, `subtitle`, `startEpochMillis`, `createdAtEpochMillis` | until fired, removed, or 30 min past start | no (household) | no |
| preference `reminder_overlay_asked` | boolean, default false | installation | no | no |
| in-memory ringing queue | reminders fired but not yet answered | process | — | — |
| channel catch-up fields in `iptv_channels` | `catchupType`, `catchupSource`, `catchupDays`, `xtreamStreamId`, `catchupTimeZone` | playlist snapshot | no | no (re-imported) |
| alarm | one exact alarm for the next fire time | until replaced/cancelled; lost on update/force-stop | — | — |

The `reminders` table arrived with database version 24 (beta 8). Titles and channel ids are the
only reminder data; nothing leaves the TV ([Security and privacy](73-security-privacy.md),
`PRIVACY.md` "Reminders").

## 7. External interfaces

- **Provider archive**: HTTP(S) GET of the built address with the channel's `User-Agent` and
  `Referer` headers, played by the player like a live stream (HLS/DASH detected by the address).
  Counts against the source's connection limit.
- **Manifest**: permissions `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`,
  `SYSTEM_ALERT_WINDOW` (granted only by the viewer in the TV settings); rebuild adds
  `SCHEDULE_EXACT_ALARM`. Receiver `.app.ReminderReceiver`, not exported, intent filter
  `android.intent.action.BOOT_COMPLETED` (rebuild adds `MY_PACKAGE_REPLACED`, `TIME_SET`,
  `TIMEZONE_CHANGED`); the fire broadcast is explicit (component), action
  `com.streammate.tv.REMINDER_FIRE`. Activity `singleTask`, so a notification tap or a bring-forward
  reaches `onNewIntent` of the running activity.
- **Activity extras**: `com.streammate.tv.OPEN_CHANNEL` (string), `com.streammate.tv.OPEN_EVENT`
  (string), `com.streammate.tv.REMINDER_ALERT` (boolean marker).
- **System screens**: `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with `package:<app id>`, then
  without; the runtime permission dialog for notifications.

## 8. Edge cases and limits

Catch-up:
- **Days**: capped at 365 everywhere; `catchup-days="0"` counts as 1; days missing = no catch-up.
- **Header attributes** (`#EXTM3U catchup=… catchup-source=…`) are ignored; providers that only
  declare catch-up there get none.
- **Unsupported types** (`flussonic`, `fs`, others) and `{catchup-id}` templates are never
  offered or are refused at play time.
- **Watch from start of a live programme**: the stop is in the future, so `{duration}`,
  `{utcend}` and the Xtream minutes include time not yet broadcast; providers accept this for
  "restart" archives.
- **Time zones**: M3U templates with formatted times use the TV's zone; a TV set to another zone
  than the provider's server produces shifted archive requests. Xtream uses the panel's server
  zone; an invalid zone id falls back to the TV's zone.
- **EPG offset**: archive requests use the offset-corrected times (CATCH-FR-11).
- **Programme gone**: a programme cleared by a newer guide import can no longer be selected;
  a playback already started continues.
- **Clock**: all checks use the TV's clock; a wrong clock offers or refuses the wrong programmes.

Reminders:
- **Set in the last minute** (start less than 60 s ahead): due at once; fires immediately in the
  rebuild (the old code fired it only if another reminder was pending, §10).
- **TV off or asleep past the start**: fired late within 30 min of the start ("starts now"),
  dropped after that.
- **Programme changed after setting**: the reminder keeps its stored time and title; a changed
  EPG entry has a new id, so the guide shows "Remind me" for it while the old reminder still
  fires. A match whose kick-off moves keeps the old time.
- **Channel gone or not allowed**: Watch goes through the usual gates (toast, PIN); a channel no
  longer in any playlist fails to play.
- **Opening the app late**: an unanswered queued alert (process still alive) shows on the next
  visit for at least 20 s even hours later (the linger is measured from when it appears). Rebuild:
  drop queued alerts whose start is more than 30 min past.
- **Picture in picture**: the activity counts as started, so the app is not brought forward and
  the alert opens in the small window (not verified; see open questions).
- **Several at once**: queued, oldest start first.
- **Update**: the alarm is dropped by the update until something reschedules (REM-FR-14). The
  ledger records that after each install of builds 54–57 on the Shield "the reminder receiver's
  broadcast started the app's process again" about ten seconds later; the cause is not
  established.

## 9. Lightweight by design

- CATCH-NFR-01 Address building is pure string work (no network, no database beyond the one
  channel read) done once per playback start, off the main thread; the regexes are compiled once.
- CATCH-NFR-02 Catch-up availability is computed when a row's labels are prepared (data change
  or minute tick), never per frame or per key press ([Live TV guide](20-live-tv-guide.md)
  GUIDE-NFR-03).
- CATCH-NFR-03 Storage cost of history: keeping past programmes for every channel is expensive
  (OwnTV measured its 7-day history for every channel at 38.9 s of a 112 s sync on 7,083
  channels). Parity keeps 12 h for all channels; any longer history must be limited to channels
  that have catch-up and to their days (open question), passing large keep-sets through a
  temporary table ([OwnTV study](../reference/owntv-study.md) item 10,
  [Sources and import](10-sources-and-import.md)).
- REM-NFR-01 One alarm at a time, no polling, no WorkManager job, no service. The receiver reads
  the small `reminders` table (tens of rows at most) and finishes within the broadcast window.
- REM-NFR-02 App start: rescheduling runs on a background thread after start-up work and must
  not delay the first frame.
- REM-NFR-03 The reminder id set is observed once by the app shell and passed down; buttons read
  a set lookup, never the database.
- REM-NFR-04 The alert and prompt are composed only while shown; nothing is drawn over other
  apps.

## 10. Lessons from the current app

- Android TV never shows an app's notification as a popup, and a receiver may not start an
  activity from the background without "display over other apps": reminders "never appeared on
  the TV" until beta 8 made the app present them itself (`SOHVA_TV_BETA_8.md`, plan/08 6.3).
- The old rescheduler returns when no future fire time exists **before** firing due reminders,
  so a reminder set less than a minute before its start, or found due at boot or app start, fires
  only when another reminder is still pending; otherwise it is silently deleted 30 minutes after
  the start (`ReminderScheduler.reschedule`, by reading; not reproduced on a device). The rebuild
  order is REM-FR-11.
- `setAlarmClock` was chosen because "it needs no special permission" (code comment). That holds
  on the Shield's Android 11; on Android 12+ exact alarms need the "Alarms & reminders" access
  according to the platform documentation, the old manifest declares none, and the old project
  never ran Android 12 (ledger 23 Sept: "Not tried: Android 12"). The owner's low-end box runs
  Android 12. Treat as unverified and handle it (REM-FR-12).
- Setting a reminder called the alarm service directly from a UI coroutine without error
  handling; the rebuild catches and logs scheduler failures and still stores the reminder.
- The alert and the notification are independent: answering the alert leaves the notification
  in the panel.
- The catch-up media title "Arkisto · …" is hard-coded Finnish, and the "no address" failure is
  an untranslated English sentence; every visible text must be a string resource
  (AGENTS §5.5–5.6).
- The Settings help text says the time zone setting is used by catch-up; the catch-up code uses
  the channel's server zone or the TV's zone.
- The importer keeps only 12 h of past programmes, so channels with multi-day archives offer far
  less catch-up than their provider allows; the code comment ("catch-up windows are days, the grid
  pages back hours") shows the trade-off was deliberate.
- Test dates must be anchored to "now"; catch-up tests use fixed instants only for the pure
  resolver.

### Open questions

1. Exact alarms on Android 12+: declare `SCHEDULE_EXACT_ALARM` (granted by default on Android
   12–13, denied by default for new installs on Android 14+, changeable by the viewer) and fall
   back to an inexact alarm, or declare `USE_EXACT_ALARM` (meant by Google's policy for alarm and
   calendar apps; Sohva is distributed outside Google Play)? Verify on an API 31 emulator before
   M3.
2. Should M3U formatted catch-up times use the TV's zone (today), UTC, or the app's time zone
   setting (as its help text says)? Check against real provider templates.
3. History for catch-up: keep 12 h for all (parity), or keep catch-up channels' programmes back
   to the guide's 24 h paging limit (or their days)?
4. Texts for a catch-up that cannot be built ("This programme is not available from the
   archive"?) and for the archive title ("Archive · <channel>"?) need owner wording in seven
   languages.
5. A reminder in picture in picture: bring the app to full screen, or show the alert in the
   corner window?
6. OK on an airing programme of a catch-up channel restarts it (CATCH-FR-13). Intended?

## 11. Acceptance tests

Unit (JVM), the old `CatchupUrlResolverTest` cases plus:
- `append`, `timeshift` (with and without `?`), `default` with `{utc:Ymd-H-M}`, `{duration:60}`
  and `{offset:60}` in Europe/Helsinki, `xtream` with an encoded `@` in the user, `xc` inferring
  credentials and id without a `live` segment — exact strings of CATCH-FR-24/-33.
- Refusals: unknown type; `file:` template; `{catchup-id}`; `start ≥ stop`; `start > now`;
  a format with a forbidden character; `{duration:0}`; `{offset:x}`; a leftover `{foo}`; a
  missing template for `default`; an Xtream address with fewer than three credential segments or
  an invalid stream id.
- `${start}` and `{START}` forms; `{M}` (minute) vs `{m}` (month).
- Availability (CATCH-FR-10): each type; days 0/1/365/400; start exactly `now − days`; future
  programmes; blank template.
- M3U attribute parsing (CATCH-FR-01): `catchup` vs `catchup-type` vs `timeshift`,
  `catchup-days="0"`, header attributes ignored; Xtream `tv_archive` `"1"`/`"true"`/`"0"` and a
  missing duration.
- Schedule: fires one minute before; due excludes stale (31 min) and includes 29 min; next is the
  earliest future fire time; a reminder due now with no other pending one **is fired** (fails on
  the old order); stale ones are deleted.
- Ringing queue: a reminder rings once, dismissal removes it, empty rings change nothing (old
  `ReminderAlertsTest`).

Instrumentation (emulator):
- Guide: the catch-up button appears for a `shift` channel with 7 days and calls catch-up with
  the programme's times; OK on a future programme offers Remind me, which stores
  `programme:<channel>:<programme>`.
- Alert dialog: title "starts in a minute" before the start, "starts now" after; Watch plays the
  channel; Not now and Back dismiss; the next queued alert follows; auto-dismiss after the linger.
- First reminder shows the overlay prompt once; a second does not.
- Alarm probe (opt-in, like `ReminderAlarmProbeTest`): a reminder set 75 s ahead rings through the
  real alarm and receiver; with the overlay grant the app comes forward from the background; a
  cold-process probe starts the app from the alarm. Run on API 30 **and API 31+**.
- Reboot/update simulation: sending `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` to the receiver
  restores the alarm (`dumpsys alarm` shows one alarm clock).

Manual (tester checklist, `TESTING.md` item 10): set reminders from the guide and a match card,
answer both prompt choices, let one fire while watching another channel and one while another app
is on screen; note what appeared and when.

Performance: none beyond §9; check that app start with 50 stored reminders adds no main-thread
work.

## 12. Reference: current code map

- `iptv/.../iptv/playback/CatchupUrlResolver.kt` — schemes, token rendering, Xtream builder,
  safety checks.
- `iptv/.../iptv/playback/PlaybackRepository.kt` — `catchupSourceFor`: availability re-check,
  address, headers, connection lease.
- `app/.../app/StreamMatePlaybackService.kt` — reads the catch-up extras, rejects incomplete
  requests, "Arkisto" title.
- `iptv/.../feature/guide/GuideCommon.kt` (`canCatchup`), `GuideScreen.kt`, `GuideHero.kt`,
  `GuideProgrammeActions.kt` — catch-up and reminder offers in the guide.
- `iptv/.../iptv/m3u/M3uParser.kt`, `iptv/.../iptv/xtream/XtreamClient.kt`,
  `XtreamImportService.kt` — catch-up fields from sources.
- `app/.../app/Reminders.kt` — `OpenRequest`, `ReminderRepository`, `ReminderScheduler`,
  `ReminderReceiver`, `ReminderNotifications`, `ReminderAlerts`, `ReminderOverlay`,
  `OpenRequests`.
- `app/.../app/ReminderAlertDialog.kt`, `ReminderOverlayPromptDialog.kt` — the two dialogs.
- `core/.../database/ReminderEntity.kt` — table and DAO; `core/.../reminders/ReminderSchedule.kt`
  — lead, stale and due arithmetic.
- `app/.../app/StreamMateApp.kt` — `toggleReminder`, permission and prompt, alert wiring,
  catch-up navigation, sport reminder record.
- `app/.../app/StreamMateApplication.kt` — reschedule at start; `StreamMateForegroundState.kt` —
  foreground check; `AppRuntimePolicy.kt` — Lab switch.
- `app/src/main/AndroidManifest.xml` — permissions, receiver, activity launch mode.
- Tests: `CatchupUrlResolverTest`, `ReminderScheduleTest`, `ReminderAlertsTest`,
  `RemindersDaoTest`, `ReminderAlertDialogTest`, `ReminderAlarmProbeTest`.
