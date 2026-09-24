# Settings

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

Layout measurements, every row's position, colours and dialog sizes are in the code-derived
extract [Settings layouts](../design/screens/settings.md); this spec adds behaviour, storage,
scope, validation, timing and the complete option table, and fixes the flaws the extract lists.
Sections owned elsewhere are listed here with their options and keys, and specified in their own
specs: Playlists → [Sources](10-sources-and-import.md), Remote buttons →
[Remote mapping](31-remote-button-mapping.md), Library → [Metadata](41-metadata-enrichment.md) and
[Library organisation](42-library-organization.md), Accounts → [Trakt](51-trakt.md), Sohva Sport →
[Sohva Sport](60-sohva-sport.md), Profiles and Parental controls →
[Profiles and parental](04-profiles-parental.md), Backup & tools → [Backup](71-backup-restore.md),
About → [Updates, About, diagnostics](72-updates-about-diagnostics.md). The phone QR dialog is in
[Phone setup](11-phone-setup.md); languages, formats and the time zone's effects are in
[Localisation](74-localization.md).

## 1. Summary

Settings is one full-screen destination: a title row, a rail of up to ten sections on the left and
the selected section's rows on the right. Every choice among values is a row that opens a picker
and applies at once; every yes/no choice is a switch that applies at once; only addresses, keys,
passwords and the PIN keep an explicit Save. The viewer uses it to add playlists, change the
language, size and colours of the interface, set the clock the guide uses, tune playback and
subtitles, map remote buttons, manage metadata, profiles, the parental PIN, backups and updates.
Every setting in Settings is shared by the household except the Trakt account (per profile) and
the groups a restricted profile may see (per target profile). A restricted profile reaches
Settings only past the parental PIN.

## 2. Feature checklist

Screen and navigation
- SET-01 Settings opens from the Home rail, the guide (options and empty guide) and Sohva Sport.
- SET-02 A restricted profile with a PIN set opens Settings only past the PIN ("Settings are locked for this profile").
- SET-03 Header: "Settings", a breadcrumb "›  SECTION" naming the selected section, and a Back button.
- SET-04 Section rail: General, Playlists, Playback, Remote buttons, Library, Accounts (unrestricted profiles only), Sohva Sport, Parental controls, Backup & tools, About.
- SET-05 Settings opens on Playlists with focus on its first control.
- SET-06 OK on a rail row shows that section and moves focus to its first control; focus alone does not switch.
- SET-07 Value rows show the current value and a chevron; OK opens a single-choice picker focused on the current value.
- SET-08 Choosing in a picker applies at once, closes it and returns focus to the row; Back closes it unchanged.
- SET-09 Switch rows toggle and apply at once.
- SET-10 Multi-choice picker with Done (profile groups); every toggle applies at once.
- SET-11 Every section shows its own status line for the results of its actions (new in the rebuild; beta 23 showed them only in Playlists).

General
- SET-20 Interface language: System default, English, Suomi, and five drafts labelled in their own language; the app restarts to apply.
- SET-21 Interface size: Normal 100 %, Compact 90 %, Small 80 %, Smaller 70 %; applies at once.
- SET-22 Colour theme: seven themes with a one-line description each; applies at once.
- SET-23 Channel numbers in the guide on or off.
- SET-24 Time zone: the TV's own (default) or any zone from a searchable picker with Recent and All zones by region.
- SET-25 Startup screen: Home, Programme guide or Last channel.
- SET-26 Playlist and EPG refresh interval: 1, 2, 4, 10 or 24 hours.
- SET-27 Reminders can open Sohva TV: shows Allowed / Not allowed and opens the TV's "display over other apps" screen.
- SET-28 Profiles group: Who is watching, ask at start, what a profile may see, add and remove profiles (behaviour in specs/04).

Playback
- SET-30 Playback buffer: Media3 default, Low latency, Stability, each with a description.
- SET-31 Playback recovery: Standard or Persistent, each with a description.
- SET-32 Skip step: 10 s, 30 s, 1 min, 2 min.
- SET-33 Match the display to the picture (auto frame rate) on or off.
- SET-34 Continue to the next episode on or off.
- SET-35 Keep watching in a corner (picture in picture) on or off.
- SET-36 Subtitle size: Follow the TV, Small, Normal, Large, Very large.
- SET-37 Subtitle colour: Follow the TV, White, Yellow.
- SET-38 Subtitle background: Follow the TV, None, Shadow, Box.
- SET-39 VOD audio and subtitles: primary and secondary audio, primary and secondary subtitles, each Automatic or one of eleven languages; picking the partner slot's language clears the partner.

Library (image cache owned here)
- SET-40 Image cache limit 100, 250 or 500 MB, applied from the next start.
- SET-41 Image cache usage "N MB in use" and Clear image cache (disk and memory).

Other sections (owned by other specs, listed with their options in 6.1)
- SET-50 Playlists: source list, add M3U / Xtream, set up from a phone, source pages ([10](10-sources-and-import.md), [11](11-phone-setup.md)).
- SET-51 Remote buttons: press/hold grid for twelve buttons, reset ([31](31-remote-button-mapping.md)).
- SET-52 Library: TMDB and TVmaze switches, TMDB key with Save and Test, metadata language, preferred film version, Manage groups & content, groups of your own, Clear metadata cache, TMDB and TVmaze website buttons ([41](41-metadata-enrichment.md), [42](42-library-organization.md)).
- SET-53 Accounts: the Trakt panel, hidden for restricted profiles ([51](51-trakt.md)).
- SET-54 Sohva Sport: channel country/language priority, API-Sports key, followed sports and competitions ([60](60-sohva-sport.md)).
- SET-55 Parental controls: set, change or remove the household PIN ([04](04-profiles-parental.md)).
- SET-56 Backup & tools: encrypted backup save and restore, Clear all guide data ([71](71-backup-restore.md)).
- SET-57 About: updates, About privacy and licences, Help translate, Save diagnostics ([72](72-updates-about-diagnostics.md)).

## 3. Entry points and navigation

| From | Action | Result |
|---|---|---|
| Home rail | Settings item | `openSettings()` |
| Guide | Options › Settings, or the empty guide's Settings button | `openSettings()` |
| Sohva Sport | Settings action | `openSettings()` |
| Settings PIN gate | Correct PIN | The gate is replaced by Settings ([04](04-profiles-parental.md) PROF-FR-34, SHELL-FR-32) |

- **SET-FR-01** `openSettings()`: when the active profile is restricted (any allowed-group set
  non-empty) and a parental PIN is configured, push `ProfileGate(profileId = null,
  thenSettings = true)` (heading `pin_settings_gate` "Settings are locked for this profile");
  otherwise push `Settings`. A restricted profile without a PIN opens Settings directly (the
  Profiles group then shows the red `profile_content_pin_missing` note).
- **SET-FR-02** Entry focus: Settings opens with the Playlists section selected and focus on
  its first control (the first source row, or "+ Add M3U source" when there is none).
- **SET-FR-03** Back, in order of precedence:
  1. an open picker, time-zone dialog, phone dialog, custom-group editor or text-field editor
     closes (Back never changes a value in a single picker; a multi picker keeps what was
     toggled);
  2. an open source page returns to the source list, focus on that source's row
     ([10](10-sources-and-import.md));
  3. the Remote buttons action chooser returns to the grid ([31](31-remote-button-mapping.md));
  4. the Sohva Sport follow menu: beta 23 has no Back handler for it (Back leaves Settings);
     rebuild: Back closes the follow menu and returns focus to "Choose followed sports and
     competitions";
  5. otherwise Settings is popped, from the rail or the pane alike. Leaving Settings asks Sohva
     Sport to refresh its feed (a key or followed sports may have changed) (SHELL table
     "Back").
  The header's Back button does the same as 5.
- **SET-FR-04** Leaving to a pushed screen and returning (About › "About, privacy and licences",
  Library › "Manage groups & content", the PIN gate of a profile switch): beta 23 disposes the
  Settings state, so the return lands on Playlists with focus on its first control. Rebuild:
  keep the selected section and the focused row across such a round trip (saved per Settings
  instance) and restore focus to the row that opened the pushed screen.
- **SET-FR-05** An interface-language change recreates the activity; beta 23 then restarts at
  the start route (Home, or the chosen startup screen) and the viewer leaves Settings
  (SHELL §8). Rebuild: same unless the owner decides otherwise (Q-01).
- **SET-FR-06** The phone setup server runs only while the screen that started it is on top;
  any change of top destination stops it (SHELL-FR-16, [11](11-phone-setup.md)).

## 4. Behaviour

### 4.1 Frame, rail and pane

- **SET-FR-10** Sections, in rail order, with label key and icon: GENERAL
  `settings_section_general` "General" (Settings icon); SOURCES `settings_section_sources`
  "Playlists" (Channels); PLAYBACK `settings_section_playback` "Playback" (Play); REMOTE
  `settings_section_remote` "Remote buttons" (Aspect); METADATA `settings_section_metadata`
  "Library" (Info); ACCOUNTS `settings_section_accounts` "Accounts" (Link); SPORT
  `settings_section_sport` "Sohva Sport" (Target); PARENTAL `settings_section_parental`
  "Parental controls" (Lock); BACKUP `settings_section_backup` "Backup & tools" (Save); ABOUT
  `settings_section_about` "About" (Guide).
- **SET-FR-11** ACCOUNTS is listed only when the host supplies the Trakt panel, which it does
  only for an unrestricted active profile. If the panel disappears while ACCOUNTS is selected
  (the active profile became restricted), the selection moves to SOURCES.
- **SET-FR-12** The rail row shows the selected state (3×22 dp `focus` bar, bold label); focus
  moving along the rail does not change the section. OK on a row: close any open picker, close
  the source page, select that section, and move focus to the section's first control (SET-FR-14),
  also when the row was already selected.
- **SET-FR-13** The breadcrumb is `›` (U+203A) plus two spaces plus the selected section's label
  upper-cased in the interface locale; one line, clipped.
- **SET-FR-14** First control per section (the focus target of SET-FR-12):
  General → Interface language row; Playlists → first source row, else "+ Add M3U source";
  Playback → Playback buffer row; Remote buttons → Up/Press cell; Library → the TMDB switch;
  Accounts → the focus target the Trakt panel names; Sohva Sport → "Save order" (or, while the
  follow menu is open, the first sport button); Parental controls → the PIN field; Backup &
  tools → the backup password field; About → the update action button, or "About, privacy and
  licences" when updates are disabled in this build.
- **SET-FR-15** Moving Right from the rail enters the pane at the control focused last in that
  pane (focus restorer on the pane). Moving Left from the pane: beta 23 lands on whichever rail
  row is geometrically nearest; rebuild: land on the selected section's rail row.
- **SET-FR-16** Only the selected section is composed. Rebuild: each section is its own small
  component with its own state holder; the pane scrolls so the focused control is fully visible
  (standard TV bring-into-view with the design system's scroll padding).

### 4.2 Rows, pickers and switches

- **SET-FR-20** Value row: title, optional one-line help (max 2 lines), current value (1 line)
  and a chevron. OK opens that row's picker. The value text is always the localised label of the
  stored value; an unknown stored value shows the default's label (every enum reads unknown or
  missing values as its default).
- **SET-FR-21** Single picker (`settings-picker`): title = the row's title; options in a fixed
  order (below); an option may carry a one-line description (max 2 lines). On open the list
  scrolls to the selected option and focuses it (the first option when none matches). The
  selected option shows the `surfaceFocused` fill and an 18 dp tick. OK on an option: close the
  picker, return focus to the row that opened it, then write the value (the write runs off the
  main thread; the row shows the new value when the store emits). Back / outside: close, focus
  back to the row, nothing written. Choosing the value already selected writes nothing
  (rebuild; beta 23 rewrites it and, for the language, restarts).
- **SET-FR-22** Multi picker (`settings-multi-picker`): each OK toggles one option and writes at
  once; a "Done" button (`category_edit_done` "Done", Check icon, compact, right-aligned,
  `settings-multi-picker-done`) closes it; Back also closes and keeps the toggles. First focus:
  the first option, or Done when the list is empty; the empty text replaces the rows.
- **SET-FR-23** Switch row: the switch is the focus target; OK flips it and writes at once. A
  disabled switch (while its section is busy) ignores OK.
- **SET-FR-24** Text fields (keys, codes, names, PIN, backup password) are edit-on-click: focus
  shows the value; OK opens the keyboard; the IME action or Back ends editing and keeps focus on
  the field. Masked fields show dots. Lengths are capped while typing (caps in 6.1).
- **SET-FR-25** One dialog at a time. Opening a picker from a row remembers that row; closing
  always requests focus on it once it is attached again.

### 4.3 Status lines (rebuild fix)

Beta 23 keeps one `status` string for the whole screen and renders it only in Playlists' status
group, so messages written by General, Library (image cache), Sohva Sport, Parental controls and
Backup are never seen, and the last one leaks into Playlists later (a "Parental controls removed"
shows up under the playlists). Library, Backup's maintenance group and About's diagnostics group
have private status texts that do render.

- **SET-FR-30** Every section owns one status state: text plus kind (info or error), cleared when
  another section is selected and when Settings is left. An action writes only its own section's
  status.
- **SET-FR-31** The status line renders directly below the group that contains the control
  whose action set it (so it appears next to the focused control), in the label style, colour
  `focus` for info and `danger` for errors (beta 23 shows errors in `focus` too), padding start
  14 dp and top 4 dp, at most 3 lines. It is a polite live region for accessibility. Test tag
  `settings-status-<section>`; Playlists keeps `settings-status`.
- **SET-FR-32** A message arriving from outside the screen goes to the section whose control
  started the work and renders below that section's first group: everything the phone page sends
  (sources and keys alike) reports in Playlists, where the phone dialog was opened
  ([11](11-phone-setup.md)). Sections whose displayed values changed underneath (Library's TMDB
  switch and key, Sohva Sport's key field) re-read them from the store; they never keep values
  captured when Settings opened (8).
- **SET-FR-33** Messages per section (English; keys in `reference/strings/`):

| Section | Message | Kind |
|---|---|---|
| General | `source_refresh_schedule_saved` "Refresh interval set to %1$s" (the plural label) | info |
| Playlists | every source, test, sync and phone message ([10](10-sources-and-import.md), [11](11-phone-setup.md)); initial `settings_add_first_source` "Add your first IPTV source" or `settings_sources_loaded` "Sources loaded securely", joined with the health summary by " · " | info/error |
| Library | `metadata_saved` "Metadata settings saved securely", `metadata_key_required` "Enter a TMDB key below first.", `metadata_tmdb_test_ok` "TMDB connection and credential work", `metadata_cache_cleared` "Metadata cache cleared", `artwork_cache_cleared` "Image cache cleared", error messages | info/error |
| Sohva Sport | `sports_key_saved` "API-Sports key saved securely", `sports_key_removed` "API-Sports key removed", `sports_channel_priority_saved` "Channel order saved", errors | info/error |
| Parental controls | `parental_saved` "Parental control PIN saved securely", `parental_removed` "Parental controls removed", `pin_wrong` "Incorrect PIN code", errors | info/error |
| Backup & tools | `settings_backup_saved` "Encrypted backup saved", `settings_backup_restored` "Backup restored. Refresh channels and the programme guide.", `guide_cache_cleared` "Programme guide cache cleared", errors | info/error |
| About | `diagnostics_saved` "Diagnostics saved. Share the file with the developer if asked.", errors; update phases have their own line ([72](72-updates-about-diagnostics.md)) | info/error |

- **SET-FR-34** Busy state is per section (beta 23 has one `busy` flag for the whole screen):
  while a section's long action runs (save metadata, test TMDB, backup save or restore), that
  section's action buttons and switches are disabled; other sections stay usable.

### 4.4 Restricted profiles and the PIN

- **SET-FR-40** The PIN gate in front of Settings is SET-FR-01; the gate screen, attempts and
  messages are [04](04-profiles-parental.md) §4.6.
- **SET-FR-41** Inside Settings a restricted profile (having passed the PIN, or with no PIN set)
  sees every section except Accounts, including the Profiles group where its own restriction can
  be changed; this is intended: whoever knows the PIN administers the household.
- **SET-FR-42** Switching profile from "Who is watching" goes through the same switch function as
  the rail picker (PIN when the target needs it) and Settings stays open underneath
  ([04](04-profiles-parental.md) PROF-FR-15). If the new active profile is restricted, ACCOUNTS
  disappears (SET-FR-11).

### 4.5 General

- **SET-FR-50 Interface language.** Row `interface_language_title` "Interface language", help
  `interface_language_help` "Changes the language of Sohva TV itself. Restarts the app to
  apply.", icon Info, first row of the group (no hairline). Options in this order: System
  default (`interface_language_system`, stored as "no choice"), then `en` "English", `fi`
  "Suomi", `es` "Español (borrador)", `pt` "Português (rascunho)", `de` "Deutsch (Entwurf)",
  `sv` "Svenska (utkast)", `it` "Italiano (bozza)" (keys `interface_language_<tag>`; each name is
  written in its own language, so it reads the same in every interface language). Choosing:
  persist the tag and apply it ([74](74-localization.md) L10N-FR-01…08): from Android 13 the
  platform's per-app locale is set and the platform recreates the activity; below Android 13 the
  tag is written to the small preferences file and the activity is recreated at once. The row's
  value is the stored choice; a stored tag outside the seven reads as System default.
- **SET-FR-51 Interface size.** Row `interface_scale_title` "Interface size", help
  `interface_scale_help` "Draws everything on screen smaller, for a very large screen or a
  projector.", icon Aspect. Options `interface_scale_option` "%1$s (%2$d %%)" with names
  `interface_scale_normal` "Normal" (100), `interface_scale_compact` "Compact" (90),
  `interface_scale_small` "Small" (80), `interface_scale_smaller` "Smaller" (70). Choosing
  applies at once: the whole app is laid out at device density × factor, font scale unchanged
  ([design 01 §14](../design/01-design-system.md)). Focus returns to the row after the re-layout.
- **SET-FR-52 Colour theme.** Row `color_theme_title` "Color theme", help `color_theme_help`
  "Changes the interface colors. Applies immediately.", icon StarOutline. Options with
  descriptions, in this order: Original, Nordic Slate, Cozy Hearth, Cyber Plum, Nord,
  Everforest, Kanagawa (labels and descriptions in [design 01 §3](../design/01-design-system.md);
  the six names after Original are untranslatable). No swatches. Choosing recolours the whole
  app at once and focus returns to the row; the choice survives activity recreation and is used
  for the first app frame after a cold start (the launch screen stays Original).
- **SET-FR-53 Channel numbers.** Switch `channel_numbers_title` "Channel numbers", help
  `channel_numbers_help` "Shows each channel's number in the guide: the playlist's own, or one
  set in Channel management. Off, the guide shows names only.", icon Channels. Applies at once to
  the guide's channel column and the player's channel list ([20](20-live-tv-guide.md),
  [21](21-channel-management.md)). Dialling by number keeps working when numbers are hidden.
- **SET-FR-54 Time zone.** Row `sports_timezone_title` "Time zone", help `sports_timezone_help`
  "Used by the guide, catch-up and Sohva Sport.", icon Epg. Value: following the TV →
  `sports_timezone_device` "TV's own (%1$s)" with the zone label; chosen → the zone label. Zone
  label = city + " · " + offset now, for example "Helsinki · UTC+3". OK opens the time-zone
  dialog (4.6). Effects of the zone are listed in [74](74-localization.md) §4.4.
- **SET-FR-55 Startup screen.** Row `startup_title` "Startup screen" (no help), icon Home.
  Options `startup_home` "Home", `startup_guide` "Programme guide", `startup_last_channel`
  "Last channel" with description `startup_last_channel_help` "The last channel is restored
  only if it is still available." Takes effect at the next cold start (SHELL-FR-10…12).
- **SET-FR-56 Refresh interval.** Row `source_refresh_schedule` "Playlist and EPG refresh
  interval", help `source_refresh_schedule_help` "Updates run in the background when a network
  connection is available.", icon Refresh. Options 1, 2, 4, 10, 24 hours, each labelled by the
  plural `source_refresh_interval_hours` ("1 hour", "2 hours" …). Choosing writes the value and
  shows `source_refresh_schedule_saved` in General's status line. The app root observes the
  value (distinct changes only) and re-enqueues the periodic playlist and EPG work with the new
  period, policy UPDATE; catalogue refresh stays every 24 hours (SHELL-FR-71,
  [10](10-sources-and-import.md)).
- **SET-FR-57 Reminders can open Sohva TV.** Row `reminders_open_title` "Reminders can open
  Sohva TV", help `reminders_open_help` "Lets a due reminder bring Sohva TV forward while
  another app is on. Set in the TV settings under display over other apps.", icon Info. Value
  `reminders_open_allowed` "Allowed" when the system reports the overlay grant (the code also
  treats API < 23 as allowed; the app's minimum is 23), else `reminders_open_not_allowed` "Not allowed". OK opens the system
  screen `ACTION_MANAGE_OVERLAY_PERMISSION` for this package; if that fails, the general overlay
  screen; if both fail, beta 23 only writes a diagnostics warning ("no settings screen for the
  overlay grant") — rebuild: also show an error in General's status line (Q-08). The value is
  re-read whenever the activity's lifecycle state changes, so returning from the TV settings
  updates it. The row is always shown in beta 23 (the host passes a value on every API level).
  Why the grant matters: [Reminders](22-catchup-and-reminders.md).
- **SET-FR-58 Profiles group.** Heading `profiles_title` "Profiles"; rows and behaviour in
  [04](04-profiles-parental.md) PROF-FR-03, -15, -21, -22 (add: name max 24, up to 6 profiles;
  remove: picker of non-default profiles, immediate, no confirmation; the default profile
  cannot be removed). The "What this profile may see" choice is session state only.

### 4.6 Time-zone dialog

- **SET-FR-60 Zone list.** Built once per opening from the platform's available zone ids: keep
  `UTC` and every id that contains `/` and does not start with `Etc/` or `SystemV/`. For each:
  region = the part before the first `/` (`UTC` for `UTC`); city = the last path segment with
  `_` replaced by a space, followed by ", " and the middle segments when there are more than two
  ("America/Argentina/Buenos_Aires" → "Buenos Aires, Argentina"; `UTC` → "UTC"); offset = the
  zone's offset at the moment the dialog opened: "UTC" for zero, else "UTC" + sign + hours, plus
  ":" and two-digit minutes when not whole, sign `+` or `−` (U+2212). Sort by region, then city
  (plain string order).
- **SET-FR-61 Layout** (extract §5): dialog 640 dp wide, max 600 dp tall, `surface` fill, medium
  shape, padding 18, items 8 dp apart: title `sports_timezone_title` (18 sp Bold), search field,
  list. List rows are dense list rows: label = city, trailing = offset; selected rows use the
  list row's selected state.
- **SET-FR-62 Blank query** list order: the TV's own row (`sports_timezone_device` with the
  device zone's label; selected while following the TV); overline `time_zone_recent` "Recent";
  the six former fixed choices Europe/Helsinki, Europe/Stockholm, Europe/Berlin, Europe/London,
  America/New_York, UTC (those present on the device); overline `time_zone_all` "All zones";
  then every zone under an overline per region. Overlines are the text upper-cased, 11 sp Bold
  `textMuted`, padding start 12 / top 10 / bottom 4. Region names are the raw id prefixes
  ("Europe", "America") and are not translated (L10N gap, [74](74-localization.md)).
- **SET-FR-63 Search.** Field `time_zone_search` "Search city or region", compact,
  edit-on-click, max 40 characters. A non-blank trimmed query shows only zones whose city, region
  or id contains it, ignoring case, still grouped by region; the TV's own row and Recent are
  hidden. No match shows an empty list (Q-06).
- **SET-FR-64 Focus.** Beta 23 focuses the TV's own row on open (blank query) or the first
  result (non-blank query) and never scrolls to the chosen zone. Rebuild: on open, scroll to and
  focus the selected row (the TV's own row when following; else the chosen zone's row under
  Recent when it is one of the six, else under its region).
- **SET-FR-65 Choosing.** The TV's own row: remove the stored zone (follow the device from now
  on). Any zone row: store its id. Both close the dialog and return focus to the Time zone row.
  Back closes without a change.

### 4.7 Playback

All rows apply to the live player, catch-up and VOD players, and — where noted — the Discover
player ([50](50-discover-addons.md)); their playback behaviour is specified in
[Player](30-player.md). None can be changed while playing (Settings is not reachable from the
player), so "next playback" is the effective moment for most.

- **SET-FR-70 Group 1** (no heading):
  1. `playback_buffer_title` "Playback buffer", help `playback_buffer_help` "Low latency for
     responsive live streams, Stability for unreliable connections; applies to the next
     playback.", icon Play. Options with descriptions: `playback_buffer_default` "Media3
     default" — `playback_buffer_default_help` "The player's standard buffering.";
     `playback_buffer_low_latency` "Low latency" — "Starts sooner; for responsive live
     streams."; `playback_buffer_stability` "Stability" — "Buffers more; for unreliable
     connections." Durations (min / max / start after / restart after rebuffer): Low latency
     5,000 / 15,000 / 1,000 / 2,000 ms; Stability 60,000 / 120,000 / 5,000 / 10,000 ms; Media3
     default sets nothing. The playback service rebuilds its idle player with the new load
     control at once, or as soon as its item list becomes empty (PLAY-FR-86, -87).
  2. `playback_reconnect_title` "Playback recovery", help `playback_reconnect_help` "Standard
     retries three times; Persistent keeps trying up to eight times with longer waits.", icon
     Refresh. Options `playback_reconnect_standard` "Standard" — "Retries three times, then
     stops." (delays 2, 4, 6 s); `playback_reconnect_persistent` "Persistent" — "Keeps trying up
     to eight times with longer waits." (delays 2, 4, 8, 16, 30, 30, 30, 30 s) (PLAY-FR-90, -91).
  3. `playback_seek_step_title` "Skip step", help `playback_seek_step_help` "How far Left, Right
     or a mapped skip button moves; held, the skip grows up to two minutes.", icon Replay.
     Options `playback_seek_step_10s` "10 seconds", `_30s` "30 seconds", `_1m` "1 minute", `_2m`
     "2 minutes" (10,000 / 30,000 / 60,000 / 120,000 ms). Also used by the Discover player
     (PLAY-FR-60).
  4. Switch `auto_frame_rate_title` "Match the display to the picture", help
     `auto_frame_rate_help` "Switches the TV to a matching refresh rate; the picture may go black
     for a second when it changes.", icon Aspect (PLAY-FR-103…105).
  5. Switch `auto_play_next_episode_title` "Continue to the next episode", help
     `auto_play_next_episode_help` "Starts the next episode when the current one ends.", icon
     Forward. Read at the end of an episode, in provider series and in Discover
     (PLAY-FR-132).
  6. Switch `picture_in_picture_title` "Keep watching in a corner", help
     `picture_in_picture_help` (full text in PLAY-FR-112), icon Aspect. Default off on purpose: a
     TV launcher need not offer a way to close a corner window, which would keep a provider
     connection open. Applies at once: the corner is allowed only while this is on and a player
     is the top destination (PLAY-FR-110).
- **SET-FR-71 Group 2** (no heading): `subtitle_size_title` "Subtitle size" with help
  `subtitle_style_help` "Follow the TV keeps the TV's own caption settings. Picture-based
  subtitles keep their own look.", `subtitle_color_title` "Subtitle colour",
  `subtitle_background_title` "Subtitle background", all with the Subtitles icon. Options:
  size `subtitle_follow_tv` "Follow the TV", `subtitle_size_small` "Small" (×0.8),
  `subtitle_size_normal` "Normal" (×1.0), `subtitle_size_large` "Large" (×1.3),
  `subtitle_size_very_large` "Very large" (×1.6); colour "Follow the TV", `subtitle_color_white`
  "White" (#FFFFFFFF), `subtitle_color_yellow` "Yellow" (#FFFFE14D); background "Follow the TV",
  `subtitle_background_none` "None", `subtitle_background_shadow` "Shadow",
  `subtitle_background_box` "Box" (#CC000000). How the three combine: PLAY-FR-100, -101. Also
  used by the Discover player.
- **SET-FR-72 Group 3** heading `preferred_languages_title` "VOD audio and subtitles"; four rows
  `preferred_audio_primary` "Primary audio", `preferred_audio_secondary` "Secondary audio" (Audio
  icon), `preferred_subtitle_primary` "Primary subtitles", `preferred_subtitle_secondary`
  "Secondary subtitles" (Subtitles icon); the first row carries help `preferred_languages_help`
  "Primary first, then secondary; Automatic keeps the provider's default." Options in this order:
  `language_automatic` "Automatic" (stored as "no value"), then fi `language_finnish`
  "Finnish", en "English", sv "Swedish", da "Danish", no "Norwegian", et "Estonian", de
  "German", fr "French", es "Spanish", it "Italian", nl "Dutch" (language names translated).
  Choosing writes the code (trimmed, lower-case; blank removes the value). If a non-Automatic
  code equals the partner slot's value (primary audio ↔ secondary audio, primary subtitles ↔
  secondary subtitles), the partner is cleared to Automatic. The row shows "Automatic" for a
  missing or unknown code. Applies to the next VOD playback (films, series, Discover); live TV
  and catch-up ignore it (PLAY-FR-75…77).

### 4.8 Image cache (Library section, group 4)

- **SET-FR-80** Row `artwork_cache_title` "Image cache", help `artwork_cache_help` "Room for
  posters, logos and backdrops; a new limit applies at the next start.", icon Save, value
  `artwork_cache_limit` "%1$d MB" of the stored limit. Options 100, 250, 500 MB (104,857,600 /
  262,144,000 / 524,288,000 bytes). Choosing writes the small synchronous preferences file
  (6.2) and nothing else: the image loader's disk cache fixes its size when it is built at
  process start, so the new limit applies from the next start.
- **SET-FR-81** Usage line below the row: `artwork_cache_usage` "%1$s in use" with the size
  formatted as whole MB ("%.0f MB") from 1 MiB, whole kB from 1 KiB, else "%d B" (US number
  format, 1024-based). Beta 23 walks every file under `cacheDir/catalogue_artwork` on the IO
  dispatcher when the section opens; rebuild: read the disk cache's own tracked size (no
  directory walk) off the main thread. Nothing is shown until the value is known.
- **SET-FR-82** "Clear image cache" (`artwork_cache_clear`, Delete icon, compact), enabled when
  the usage is known and above 0 and no clear is running: clears the disk cache **and** the
  memory cache (so the screen does not keep showing artwork the viewer just asked to remove),
  re-reads the usage, and shows `artwork_cache_cleared` "Image cache cleared" in Library's status
  line. Images on screen reload from the network as needed.

### 4.9 Other sections (summary; the owning spec is authoritative)

- **SET-FR-90 Playlists**: list page and source pages, validation, tests, syncs, deletion, the
  status and health summary and the security note `settings_security_subtitle` —
  [10](10-sources-and-import.md) SRC-FR-*; "Set up from a phone" and its dialog —
  [11](11-phone-setup.md).
- **SET-FR-91 Remote buttons**: [31](31-remote-button-mapping.md).
- **SET-FR-92 Library**: TMDB switch (turning it on without a key shows `metadata_key_required`
  and stays off; toggles save immediately), key field + "Save key" (saves and clears the metadata
  cache) + "Test TMDB", TVmaze switch, metadata language (changing it resets derived titles and
  restarts the background enrichment), "When a film has more than one version", "Manage groups
  & content" (opens the Library manager on Live TV), "Groups of your own", image cache (4.8),
  maintenance: "Clear metadata cache", buttons "TMDB" and "TVmaze" that open
  https://www.themoviedb.org and https://www.tvmaze.com in a browser when one exists —
  [41](41-metadata-enrichment.md), [42](42-library-organization.md).
- **SET-FR-93 Accounts**: [51](51-trakt.md).
- **SET-FR-94 Sohva Sport**: [60](60-sohva-sport.md).
- **SET-FR-95 Parental controls**: [04](04-profiles-parental.md) PROF-FR-33.
- **SET-FR-96 Backup & tools**: [71](71-backup-restore.md). "Clear all guide data"
  (`guide_clear_all`, danger, Delete icon) removes every stored programme at once, no
  confirmation (Q-02), and shows `guide_cache_cleared`.
- **SET-FR-97 About**: [72](72-updates-about-diagnostics.md). "Help translate Sohva TV" opens the
  public repository URL (`https://github.com/Macstered/Sohva-TV`) ([74](74-localization.md)).

### 4.10 When a change takes effect (summary)

| Moment | Options |
|---|---|
| At once, whole app | Interface size, colour theme |
| At once, where shown | Channel numbers, time zone, keep watching in a corner, TMDB/TVmaze switches (next enrichment), followed sports/competitions and channel priority (Sohva Sport reloads), remote mapping, profile restrictions |
| Next playback | Buffer, recovery, skip step, match the display, subtitle size/colour/background, VOD languages |
| End of the current episode | Continue to the next episode |
| Rescheduled at once, runs later | Refresh interval |
| Background re-enrichment | Metadata language |
| App restart (activity recreated at once) | Interface language |
| Next process start | Image cache limit |
| Next cold start | Startup screen |

## 5. Screen anatomy

Frame, rail, group vocabulary, row components, switch, picker rows, action buttons, fields,
dialog sizes and per-section layouts: [design/screens/settings.md](../design/screens/settings.md)
§1–§8 (reference canvas 960×540 dp). The rebuild keeps those measurements. Additions and
corrections:

- **Status line** (new for every section, SET-FR-31): Text in the label style (`typography.label`
  size and line height), `focus` or `danger`, start padding 14 dp so it aligns with row titles'
  container, top 4 dp, max 3 lines with ellipsis; no background.
- **Time-zone dialog**: the extract's "Europe/Helsinki (not following the device)" default is
  wrong for beta 23 — a fresh install follows the TV's zone (SET-FR-54, lessons 10.1).
- **Picker rows** keep 48 dp minimum height; up to 22 options (metadata language) fit in the 620
  dp maximum dialog height with scrolling.
- **Dividers**: the first row of each group has no top hairline; later rows have one (1 dp
  `divider`, inset 14 dp).
- **Row test tags** (kept for tests): `settings-interface-language`, `settings-interface-scale`,
  `settings-color-theme`, `settings-channel-numbers`, `settings-time-zone`, `settings-startup`,
  `settings-refresh-interval`, `settings-reminders-open`, `settings-buffer`,
  `settings-reconnect`, `settings-seek-step`, `settings-auto-frame-rate`,
  `settings-auto-next-episode`, `settings-picture-in-picture`, `settings-subtitle-size`,
  `settings-subtitle-color`, `settings-subtitle-background`,
  `settings-language-primary_audio` / `-secondary_audio` / `-primary_subtitle` /
  `-secondary_subtitle`, `settings-artwork-cache`, `settings-artwork-cache-usage`,
  `settings-artwork-cache-clear`; screen `settings-breadcrumb`, `settings-back`,
  `settings-sections`, `settings-section-<section lower-case>`, `settings-list`; pickers
  `settings-picker`, options `settings-<row>-<value>` as in beta 23 (for example
  `settings-interface-language-system`, `settings-interface-scale-smaller`,
  `settings-color-theme-nord`, `settings-refresh-interval-24`, `settings-buffer-low_latency`,
  `settings-seek-step-one_minute`, `settings-artwork-cache-large`); time zone `time-zone-picker`,
  `time-zone-search`, `time-zone-list`, `time-zone-device`, `time-zone-<zone id>`.
- Screenshot: only a pre-restructure capture exists
  (`design/screenshots/older-builds/2026-09-02-demo/09-settings.png`); take new references of
  every section in all seven themes as part of the rebuild's screenshot tests.

## 6. Data

### 6.1 Complete option table

Storage abbreviations: **DS** = the Preferences DataStore file `streammate_preferences`
(`AppPreferencesRepository`); **SEC** = the encrypted SharedPreferences file
`streammate_secure_sources` (values encrypted with the Keystore-wrapped envelope key,
[73](73-security-privacy.md)); **LOC** = SharedPreferences `streammate_locale`; **ART** =
SharedPreferences `streammate_artwork_cache`; **SYS** = the Android system. Scope: **G** global
(household), **P** per profile. Backup: the JSON field in the `.smbak` preferences object
([71](71-backup-restore.md)), "—" = not in backups. Enum values are stored as their names unless
stated.

#### General

| Row (label key → EN) | Control | Values (stored → label) | Default | Storage | Scope | Applies | Backup | Owner |
|---|---|---|---|---|---|---|---|---|
| `interface_language_title` Interface language | picker | absent → System default; `en`, `fi`, `es`, `pt`, `de`, `sv`, `it` → endonyms (drafts marked) | System default | API ≥ 33: platform per-app locale; API < 33: LOC key `language_tag` | G (device) | Activity recreated at once; app restarts at its start route | — | 70, 74 |
| `interface_scale_title` Interface size | picker | `NORMAL` 1.0, `COMPACT` 0.9, `SMALL` 0.8, `SMALLER` 0.7 → "Normal (100 %)" … | `NORMAL` | DS `interface_scale` | G | At once | `interfaceScale` | 70, design 01 |
| `color_theme_title` Color theme | picker + descriptions | `original`, `nordic_slate`, `cozy_hearth`, `cyber_plum`, `nord`, `everforest`, `kanagawa` (lower-case ids) | `original` | DS `color_theme` | G | At once | `colorTheme` | 70, design 01 |
| `channel_numbers_title` Channel numbers | switch | true / false | true | DS `show_channel_numbers` | G | At once | `showChannelNumbers` | 70, 20, 21 |
| `sports_timezone_title` Time zone | dialog | absent → the TV's own zone; an IANA zone id | absent (follow the TV) | DS `time_zone` | G | At once | `timeZoneId`, `timeZoneFollowsDevice` | 70, 74 |
| `startup_title` Startup screen | picker | `HOME`, `GUIDE`, `LAST_CHANNEL` | `HOME` | DS `startup_screen` | G | Next cold start | `startupScreen` | 70, 01 |
| `source_refresh_schedule` Playlist and EPG refresh interval | picker | `ONE_HOUR`, `TWO_HOURS`, `FOUR_HOURS`, `TEN_HOURS`, `TWENTY_FOUR_HOURS` | `TWENTY_FOUR_HOURS` | DS `playlist_epg_refresh_interval` | G | Work rescheduled at once | `playlistEpgRefreshInterval` | 70, 10 |
| `reminders_open_title` Reminders can open Sohva TV | action row | Allowed / Not allowed (read) | Not allowed (API ≥ 23) | SYS "display over other apps" | device | On return from TV settings | — | 70, 22 |
| `profile_active_title` Who is watching | picker | profile ids (`default` = "Everyone") | `default` | DS `active_profile_id` (absent = default) | G | At once | `activeProfileId` | 04 |
| `profile_ask_at_start` Ask who is watching at start (≥ 2 profiles) | switch | true / false | true | DS `ask_profile_at_start` | G | Next start | `askProfileAtStart` | 04 |
| `profile_content_title` What this profile may see (≥ 2 profiles) | picker | profile ids | the active profile | not stored (session) | — | — | — | 04 |
| `profile_content_live` / `_movies` / `_series` Live TV / Film / Series groups | multi picker | organisation group keys; empty = "Everything" | empty | DS `allowed_groups_live` / `_movies` / `_series` (+ `:<profileId>` for non-default profiles) | P (target profile) | At once | `profileData.<id>.allowedLive/Movie/SeriesGroupKeys` | 04 |
| `profile_name_hint` + `profile_add` Add profile | field (max 24) + button | name | — | DS `profiles` (encoded list) | G | At once | `profiles` | 04 |
| `profile_remove` Remove a profile (≥ 2 profiles) | picker | non-default profiles | — | removes the profile and its per-profile keys | G | At once | — | 04 |

#### Playlists ([10](10-sources-and-import.md))

| Row | Control | Values | Default | Storage | Scope | Applies | Backup | Owner |
|---|---|---|---|---|---|---|---|---|
| Source list, "+ Add M3U source", "+ Add Xtream source" | rows, buttons | up to 100 sources | none | SEC `sources_v1` (codec JSON, encrypted) + source state rows in the database | G | On Save securely | `sources` | 10 |
| Source name | field | 1+ characters, trimmed | "IPTV n" / "Xtream n" | SEC `sources_v1` | G | On save | `sources` | 10 |
| M3U address / XMLTV address (optional) / Xtream server, username, password | fields | http(s) URLs, credentials | empty | SEC `sources_v1` | G | On save | `sources` | 10 |
| Source in use | switch | true / false | true | SEC `sources_v1` | G | On save | `sources` | 10 |
| Connection limit | − / value / + | 1–16 | 1 | SEC `sources_v1` | G | On save | `sources` | 10 |
| Content to import | choice | Live TV / VOD only / TV and VOD | TV and VOD | SEC `sources_v1` | G | On save, next sync | `sources` | 10 |
| EPG time correction | − / value / + | −720…+720 min in 30-min steps | 0 | SEC `sources_v1` | G | On save, next guide read | `sources` | 10 |
| Set up from a phone | button → dialog | — | — | — | — | — | — | 11 |

#### Playback

| Row (label key → EN) | Control | Values (stored → label) | Default | Storage | Scope | Applies | Backup | Owner |
|---|---|---|---|---|---|---|---|---|
| `playback_buffer_title` Playback buffer | picker + descriptions | `DEFAULT`, `LOW_LATENCY`, `STABILITY` | `DEFAULT` | DS `playback_buffer_profile` | G | Next playback | `playbackBufferProfile` | 70, 30 |
| `playback_reconnect_title` Playback recovery | picker + descriptions | `STANDARD`, `PERSISTENT` | `STANDARD` | DS `playback_reconnect_policy` | G | Next playback | `playbackReconnectPolicy` | 70, 30 |
| `playback_seek_step_title` Skip step | picker | `TEN_SECONDS`, `THIRTY_SECONDS`, `ONE_MINUTE`, `TWO_MINUTES` | `TEN_SECONDS` | DS `playback_seek_step` | G | Next playback | `playbackSeekStep` | 70, 30 |
| `auto_frame_rate_title` Match the display to the picture | switch | true / false | true | DS `auto_frame_rate` | G | Next playback | — (restore resets it to on) | 70, 30 |
| `auto_play_next_episode_title` Continue to the next episode | switch | true / false | true | DS `auto_play_next_episode` | G | End of the current episode | `autoPlayNextEpisodeEnabled` | 70, 30 |
| `picture_in_picture_title` Keep watching in a corner | switch | true / false | false | DS `picture_in_picture` | G | At once | `pictureInPictureEnabled` | 70, 30 |
| `subtitle_size_title` Subtitle size | picker | `FOLLOW_TV`, `SMALL`, `NORMAL`, `LARGE`, `VERY_LARGE` | `FOLLOW_TV` | DS `subtitle_text_size` | G | Next playback | `subtitleTextSize` | 70, 30 |
| `subtitle_color_title` Subtitle colour | picker | `FOLLOW_TV`, `WHITE`, `YELLOW` | `FOLLOW_TV` | DS `subtitle_text_color` | G | Next playback | `subtitleTextColor` | 70, 30 |
| `subtitle_background_title` Subtitle background | picker | `FOLLOW_TV`, `NONE`, `SHADOW`, `BOX` | `FOLLOW_TV` | DS `subtitle_background` | G | Next playback | `subtitleBackground` | 70, 30 |
| `preferred_audio_primary` Primary audio | picker | absent → Automatic; `fi`, `en`, `sv`, `da`, `no`, `et`, `de`, `fr`, `es`, `it`, `nl` | Automatic | DS `preferred_audio_language` | G | Next VOD playback | `preferredAudioLanguage` | 70, 30 |
| `preferred_audio_secondary` Secondary audio | picker | same | Automatic | DS `secondary_audio_language` | G | Next VOD playback | `secondaryAudioLanguage` | 70, 30 |
| `preferred_subtitle_primary` Primary subtitles | picker | same | Automatic | DS `preferred_subtitle_language` | G | Next VOD playback | `preferredSubtitleLanguage` | 70, 30 |
| `preferred_subtitle_secondary` Secondary subtitles | picker | same | Automatic | DS `secondary_subtitle_language` | G | Next VOD playback | `secondarySubtitleLanguage` | 70, 30 |

#### Remote buttons ([31](31-remote-button-mapping.md))

| Row | Control | Values | Default | Storage | Scope | Applies | Backup | Owner |
|---|---|---|---|---|---|---|---|---|
| Twelve buttons × Press / Hold (23 mappable slots) | grid cells → action chooser | 25 actions + Nothing | the pre-mapping behaviour | DS `remote_mappings` (string set `slot=action`); legacy DS `remote_channel_key_mode` decides defaults until a mapping is stored | G | At once | `remoteMappings`, `remoteChannelKeyMode` | 31 |
| Reset to defaults | two-step confirm | — | — | rewrites `remote_mappings` | G | At once | — | 31 |

#### Library

| Row (label key → EN) | Control | Values | Default | Storage | Scope | Applies | Backup | Owner |
|---|---|---|---|---|---|---|---|---|
| `metadata_tmdb_switch` TMDB titles, plots and artwork | switch | true / false (only true with a key) | false | SEC `metadata_tmdb_enabled_v1` | G | At once (next enrichment) | — | 41 |
| `metadata_tmdb_token` TMDB API key or Read Access Token + Save key + Test TMDB | masked field (max 2,048, no line breaks) + buttons | text | empty | SEC `metadata_tmdb_token_v1` (encrypted) | G | On Save key (clears the metadata cache) | — | 41 |
| `metadata_tvmaze_switch` TVmaze series information | switch | true / false | false | SEC `metadata_tvmaze_enabled_v1` | G | At once | — | 41 |
| `metadata_language_title` Metadata language | picker | 22 TMDB tags ([74](74-localization.md) §4.3) | `fi-FI` when the chosen interface language is Finnish, else `en-US` (derived while unset) | DS `metadata_language` | G | Background re-enrichment | `metadataLanguage` | 41, 74 |
| `preferred_copy_title` When a film has more than one version | picker | `NONE` "Whichever comes first", `FINNISH_AUDIO`, `FINNISH_SUBTITLES`, `LARGEST_PICTURE` | `NONE` | DS `preferred_catalogue_copy` | G | At once | `preferredCatalogueCopy` | 42 |
| `manager_title` Manage groups & content | row → Library manager | — | — | organisation tables ([42](42-library-organization.md)) | G | — | organisation | 42 |
| `custom_group_heading` Groups of your own: list + `custom_group_add` Add a group | rows → editor | up to 24 groups, name ≤ 40, genres, years, minimum rating | none | DS `custom_catalogue_groups` (JSON array) | G | At once | `customCatalogueGroups` | 42 |
| `artwork_cache_title` Image cache | picker | `SMALL` 100 MB, `MEDIUM` 250 MB, `LARGE` 500 MB | `MEDIUM` | ART key `limit` | G (device) | Next process start | — | 70 |
| `artwork_cache_clear` Clear image cache | button | — | — | clears disk + memory image caches | device | At once | — | 70 |
| `metadata_clear_cache` Clear metadata cache; TMDB; TVmaze | buttons | — | — | metadata cache tables | G | At once | — | 41 |

#### Accounts ([51](51-trakt.md))

| Row | Control | Values | Default | Storage | Scope | Applies | Backup | Owner |
|---|---|---|---|---|---|---|---|---|
| Trakt panel (connect by device code, disconnect, status) | panel | connected / not | not connected | SharedPreferences `trakt_accounts` (encrypted tokens) | **P** | At once | — | 51 |

#### Sohva Sport ([60](60-sohva-sport.md))

| Row (label key → EN) | Control | Values | Default | Storage | Scope | Applies | Backup | Owner |
|---|---|---|---|---|---|---|---|---|
| `sports_channel_priority_title` Channel country / language priority + `sports_channel_priority_save` Save order | field (max 80) + button | comma-separated codes, normalised to at most 8 recognised codes | empty (default order) | DS `sports_channel_priority` (comma-joined) | G | On Save order | `sportsChannelPriority` | 60 |
| `sports_api_key` API-Sports API key + Save key / Remove key | masked field (max 512, no line breaks) + button | text | empty | SEC `sports_api_key_v1` (encrypted) | G | On save | — | 60 |
| `sports_follow_open` Choose followed sports and competitions: sport toggles | twelve sport buttons + include/remove | `SportType` names | FOOTBALL, ICE_HOCKEY, AUSTRALIAN_FOOTBALL | DS `followed_sports` | G | At once (feed reloads) | `followedSports` | 60 |
| Competitions list | toggle buttons | competition preference keys | football 2, 3, 39, 78, 135, 140, 848; ice hockey 16; AFL 1 | DS `followed_competitions` | G | At once | `followedCompetitionKeys` | 60 |

#### Parental controls ([04](04-profiles-parental.md))

| Row | Control | Values | Default | Storage | Scope | Applies | Backup | Owner |
|---|---|---|---|---|---|---|---|---|
| `parental_title` PIN field + `parental_enable` Enable / `parental_remove_change` Remove / change PIN | numeric masked field (digits, max 8) + button | 4–8 digits | no PIN | SEC `parental_pin_v1` (encrypted); DS `parental_pin_configured` (mirror, re-synced at start) | G | At once; removing the PIN clears every profile's locked channels | `parentalPin` (top level) | 04 |

#### Backup & tools ([71](71-backup-restore.md))

| Row | Control | Values | Default | Storage | Scope | Applies | Backup | Owner |
|---|---|---|---|---|---|---|---|---|
| `backup_passphrase` Backup password + `backup_save` / `backup_restore` | masked field (min 8, max 128) + buttons → system document pickers | — | — | never stored | — | On the chosen file | — | 71 |
| `guide_clear_all` Clear all guide data | danger button | — | — | deletes stored programmes | G | At once | — | 71, 20 |

#### About ([72](72-updates-about-diagnostics.md))

| Row | Control | Values | Default | Storage | Scope | Applies | Backup | Owner |
|---|---|---|---|---|---|---|---|---|
| Updates (check, download, install, allow installs) | action button | phases | — | SharedPreferences `streammate_updates` | device | — | — | 72 |
| `settings_about_licenses` About, privacy and licences | button → Legal screen | — | — | — | — | — | — | 72 |
| `translate_help_title` Help translate Sohva TV | row → browser | — | — | — | — | — | — | 72, 74 |
| `diagnostics_save` Save diagnostics | button → document picker | — | — | — | — | — | — | 72 |

### 6.2 Stores used by Settings

- **DataStore `streammate_preferences`**: every DS key above. Per-profile keys are named
  `<base>` for the default profile (id `default`) and `<base>:<profileId>` for others
  (`Profiles.keyName`). Read as one flow; the rebuild exposes narrow, distinct flows per
  consumer ([plan/03](../plan/03-architecture.md)).
- **LOC `streammate_locale`** (API < 33 only), key `language_tag`: must be readable
  synchronously in `attachBaseContext`, so it is not in DataStore ([74](74-localization.md)).
- **ART `streammate_artwork_cache`**, key `limit`: read synchronously when the image loader is
  built at process start, so it is not in DataStore.
- **SEC `streammate_secure_sources`**: keys `sources_v1`, `parental_pin_v1`,
  `metadata_tmdb_enabled_v1`, `metadata_tmdb_token_v1`, `metadata_tvmaze_enabled_v1`,
  `sports_api_key_v1` (legacy file `sportmate_secure_settings` read once for migration)
  ([73](73-security-privacy.md)).
- **Other keys in the DataStore that Settings does not show** (for [plan/04](../plan/04-data-model.md)):
  `favourite_event_ids`, `favourite_channel_ids`, `recent_channel_ids` (max 20, separator
  U+001F), `last_channel_id`, `locked_channel_ids` (all per profile); `last_guide_source_id`
  (≤ 128 chars); `editors_show_hidden` (Channel management toggle, default true);
  `hidden_live_categories`, `hidden_movie_categories`, `hidden_series_categories` (legacy
  fallback when no organisation layer exists); `manager_group_<room>`, `manager_source_<room>`
  (≤ 2,048 chars each); `reminder_overlay_asked`; `movie_identity_mark` (≤ 4,096 chars).

### 6.3 Backups

The `.smbak` preferences object carries the backup fields named in 6.1 plus the per-profile
data; restore clears the DataStore and writes them back ([71](71-backup-restore.md)). **Not in
backups** in beta 23: interface language, image cache limit, auto frame rate (a restore
therefore sets it back to on), TMDB/TVmaze switches and key, API-Sports key, Trakt accounts,
update state. Older backups without `timeZoneFollowsDevice` restore the zone they carry as a
chosen zone. Whether the rebuild adds the missing ones is Q-05 (format stays readable both
ways).

### 6.4 Validation summary

- Enumerations: any unknown or missing stored value reads as the default; nothing throws.
- Metadata language: only the 22 tags are accepted on write (an unsupported tag is refused);
  a stored unsupported tag reads as the derived default.
- Time zone: any id the platform's `ZoneId.of` accepts; an id that fails to parse where it is
  used falls back to the device zone. Backup restore refuses ids longer than 100 characters.
- VOD languages: trimmed, lower-cased; blank = Automatic.
- Profile names: trimmed, ≤ 24; at most 6 profiles including the default one.
- Custom groups: name trimmed ≤ 40; a group without genres, years or rating is refused; at most
  24 kept.

## 7. External interfaces

- Android per-app locale (`LocaleManager.setApplicationLocales`, API 33+) and
  `locales_config.xml` ([74](74-localization.md)).
- `Settings.canDrawOverlays` and `ACTION_MANAGE_OVERLAY_PERMISSION` (with
  `package:<applicationId>`, fallback without) for the reminders row; both started with
  `FLAG_ACTIVITY_NEW_TASK`.
- WorkManager unique periodic work for refresh (names and policy in
  [10](10-sources-and-import.md), SHELL-FR-71).
- `CaptioningManager` (read by the player for "Follow the TV" subtitle values).
- System document pickers for backup (`CreateDocument` with MIME
  `application/vnd.streammate.backup`, default name `sohva-tv-backup.smbak`; `OpenDocument` for
  that MIME or `application/octet-stream`) and diagnostics (`text/plain`,
  `sohva-tv-diagnostics-yyyyMMdd-HHmm.txt`) ([71](71-backup-restore.md),
  [72](72-updates-about-diagnostics.md)).
- External browser via the platform URI handler for https://www.themoviedb.org,
  https://www.tvmaze.com and the translations repository; failures are ignored (a TV without a
  browser does nothing). Rebuild: show a status message when no app can open the link.

## 8. Edge cases and limits

- **Device zone changes while running**: when following the TV, beta 23 reads the device zone
  only when the preferences flow re-emits, and no `ACTION_TIMEZONE_CHANGED` receiver exists, so
  a changed TV zone shows up after the next preference change or restart. Rebuild: listen for
  the system time-zone broadcast (and on resume) and re-emit the effective zone.
- **Flash of defaults**: beta 23 collects preferences with an initial `AppPreferences()` whose
  zone default is Helsinki and not following the TV; for a frame Settings (and other screens)
  can show defaults. Rebuild: render Settings from the start snapshot or the first emission,
  never from constructor defaults, and remove the Helsinki constant.
- **Language chosen equals current**: no write, no restart (rebuild).
- **Restricted profile without PIN**: Settings opens freely; the Profiles group warns in
  `danger`.
- **No browser / no overlay screen**: see 7 and SET-FR-57.
- **Large lists in pickers**: profile group pickers can list hundreds of groups (a provider with
  800 live groups); they must be lazy and keyed; the time-zone list has about 600 rows.
- **Process death** while a picker is open: the picker is not restored; Settings reopens at the
  start route like every destination (SHELL §8).
- **Secret store unreadable** (Keystore failure): sources and keys load as empty; the Playlists
  status line says so ([73](73-security-privacy.md)).
- **A phone submission while Settings shows stale keys**: beta 23 reloads only the source list
  when the phone page saves something; the TMDB and API-Sports fields and switches keep the
  values read when Settings opened, so toggling a Library switch afterwards could write the old
  key back over the one sent from the phone (read from the code, not reproduced). Rebuild: the
  Library and Sohva Sport sections observe the secret store's key state and refresh after any
  phone submission.

## 9. Lightweight by design

- **Compose only the selected section**, each as its own small composable and state holder;
  no function near ART's 10,000 code-unit AOT limit (beta 23's `SettingsScreen` composable was
  2,357 lines and ran interpreted after every start, plan/08 3.3). The pane is a lazy list, or a
  plain column only when the section's composed rows are few (General, Playback ≤ 20 rows).
- **No secret decryption on the main thread.** Beta 23 decrypts all sources, the TMDB key and the
  API-Sports key in `remember {}` during the first composition. Rebuild: a section state holder
  loads them on the IO dispatcher when the section is first shown; Playlists shows its rows when
  the load returns (tens of milliseconds).
- **Narrow preference flows**: each section collects only its keys with
  `distinctUntilChanged`; a switch flip recomposes one row, not the screen.
- **Pickers**: lazy, keyed rows; at most 22 options (metadata language) except profile groups
  (lazy, any size) and time zones (lazy, ~600 rows). The zone list and its offset strings are
  built on the default dispatcher when the dialog opens (≈ 600 `ZoneRules` lookups), kept only
  while it is open (< 100 KB), filtered off the main thread per keystroke.
- **Image cache usage** from the disk cache's tracked size, not a recursive directory walk over
  up to 500 MB of files.
- **Theme and interface size** changes recompose and re-lay out the whole tree once; nothing
  reads them per frame (static composition locals). Cached layers keyed by size (the screen
  background) must also key on the scale factor ([design 01 §14](../design/01-design-system.md)).
- **Drawing**: rows are flat fills and text; the only animation is the switch knob spring and
  the focus fill; in reduced-motion/low-RAM mode both jump
  ([design 01 §16.3](../design/01-design-system.md)). Dialogs are opaque `panel`/`surface` with
  no scrim blur.
- **Start-up cost**: none from Settings code. At start only the snapshot keys (theme, scale,
  startup screen, profiles question, active profile, last channel) and the two small synchronous
  files (LOC below API 33, ART) are read.
- **Memory**: Settings state is small (< 1 MB) apart from the competition list, loaded per sport
  on demand (hundreds of entries) and dropped when the follow menu closes, and profile group
  choices, streamed from the database only while that picker is open.
- **No polling**: the reminders grant is re-read on lifecycle changes only; the update check is
  not triggered by opening Settings.

## 10. Lessons from the current app

1. **The time zone defaulted to Helsinki** and a tester six hours away read every programme
   wrong; since beta 6 an absent `time_zone` means the TV's zone, with "TV's own (zone)" first
   in the list (`docs/SOHVA_TV_BETA_6.md`). The Helsinki constant survived as a data-class
   default and still flashes; remove it (8).
2. **Status only in Playlists**: messages from five sections were written and never seen, and
   leaked into Playlists later (extract §9). One status per section (4.3).
3. **Settings restructure** (`docs/SETTINGS_RESTRUCTURE_PLAN.md`, betas 7–8): four ways to pick a
   value, paragraphs under every control and Save buttons for things that could apply at once
   were replaced by value rows, switches and pickers; "apply at once, except credentials"; keep
   every preference key so backups stay compatible. Keep that rule set.
4. **Language and image-cache limit live outside DataStore** because they must be read
   synchronously before the first frame; blocking on DataStore at that point turns cold starts
   into ANRs (`AppLocale.kt`, `ArtworkCacheSettings.kt`, commit `83b7e2f`). The image cache was a
   silent flat 1 GB before beta 11's choice of 100/250/500 MB.
5. **Clearing the image cache clears memory too**, or the screen keeps showing what the viewer
   asked to remove and the usage does not match (`ArtworkCache.kt`).
6. **Keep watching in a corner is off by default**: Projectivy on the Shield offers no way to
   dismiss a corner window, which would keep a provider connection open
   (`SettingsScreenTest.keepingWatchingInACornerIsOffUntilAskedFor`).
7. **Returning from a pushed screen reset Settings to Playlists** (state not saved) — keep the
   section and row (SET-FR-04). **The rail had no focus restorer** — land on the selected row
   (SET-FR-15).
8. **Restore silently reset "Match the display to the picture" to on** because the field is not
   in the backup (read from `StreamMateBackupManager.kt`); decide Q-05.
9. **Phone-sent keys and stale fields** (8): the screen must observe the stores it shows.
10. **Leftover test state** (a chosen language, a second profile) broke later device tests;
    every Settings test resets DataStore, LOC, ART and the secret store (plan/08 7.5).
11. **The interface-size ladder** gained a 70 % step because a tester on a large screen found
    80 % one step short (`InterfaceScale.kt`, commit `a6674f9`); percentages are in the labels so
    testers can say which step they use.

### Open questions

- Q-01 After an interface-language change, should the rebuild reopen Settings › General with
  focus on the language row instead of the start route?
- Q-02 Confirmation for destructive actions (remove source, clear all guide data, remove
  profile, clear caches): beta 23 has none except the remote-mapping reset.
- Q-03 Back from the pane: leave Settings (beta 23) or go to the rail first?
- Q-04 Expose the reduced-motion / low-RAM mode ([design 01 §16.3](../design/01-design-system.md))
  as a General switch, or keep it automatic only?
- Q-05 Add interface language, image cache limit, auto frame rate and the TMDB/TVmaze/API-Sports
  settings to backups ([71](71-backup-restore.md))?
- Q-06 Show a "no matching zone" message for an empty time-zone search (a new string in seven
  languages)?
- Q-07 Should a restricted profile, once past the PIN, see fewer sections than Accounts-less
  Settings?
- Q-08 New error string when no overlay-permission screen exists on the TV (beta 23 only logs)?

## 11. Acceptance tests

Unit (JVM)
- Every enum's stored-value round trip, and unknown/missing values → default (interface scale,
  colour theme, startup, refresh interval, buffer, recovery, seek step, subtitle size/colour/
  background, preferred copy, artwork limit) — mirrors `InterfaceScaleTest`, `ColorThemeTest`,
  `PlaylistEpgRefreshIntervalTest`, `PlaybackBufferProfileTest`,
  `PlaybackReconnectPolicyTest`, `ArtworkCacheTest`.
- Buffer durations per profile; Media3 default sets nothing (`PlaybackBufferPolicyTest`).
- Next episode defaults on; corner defaults off; auto frame rate defaults on
  (`PlaybackPreferenceDefaultsTest`).
- Time zone helpers: city/region/offset formatting (`UTC`, `UTC+3`, `UTC−5:30`, "Buenos Aires,
  Argentina"), filtering by city, region or id, exclusion of `Etc/` and `SystemV/`.
- Language pairing: choosing the partner slot's language clears the partner; Automatic never
  clears.
- Artwork size formatting ("0 B" handling, kB, MB) and limits in bytes.
- Absent `time_zone` → device zone and `followsDevice = true`; restoring a backup without the
  flag keeps the zone as chosen.

UI (instrumentation, Compose)
- Opening Settings lands on Playlists' first control; each rail row's OK moves focus to the
  first control listed in SET-FR-14 (`selectingSettingsSectionMovesFocusIntoItsFirstUsefulControl`).
- Every picker opens focused on the current value, persists a choice, returns focus to its row,
  and Back leaves the value (refresh interval, skip step, subtitle size, buffer, recovery,
  preferred copy, colour theme — `ColorThemeSettingsTest` also checks the screen colour and
  survival across recreation).
- Channel numbers switch hides numbers in the guide; interface size Smaller changes the layout
  density; the interface-language picker lists System default plus the seven tags.
- Each section's status line shows its own message (refresh interval saved in General, "Image
  cache cleared" in Library, "Incorrect PIN code" in Parental, backup errors in Backup) and does
  not appear in another section.
- Time-zone dialog: opens focused on the selected row; searching "helsinki" leaves one result
  focused; choosing a zone updates the row; "TV's own" clears the choice.
- A restricted profile with a PIN reaches Settings only through the gate; Accounts is hidden.
- Returning from About › licences restores About with focus on the licences button.

Manual (device)
- On the Elisa box (API 31) and the Shield (API 30): choose Suomi, confirm the app restarts in
  Finnish; choose System default, confirm it follows the TV language.
- Reminders row reads Not allowed, opens the TV's screen, and reads Allowed after granting.
- Keep watching in a corner off: Home during playback stops playback; on: the corner appears.

Performance (low-end class or its emulator stand-in, [plan/07](../plan/07-performance.md))
- Opening Settings and switching between all ten sections: every OK press renders the new
  section within two vsyncs after the first open; no main-thread disk or Keystore work (strict
  mode clean).
- Opening the time-zone dialog and typing a query: no frame over 32 ms.
- Opening a profile group picker over 800 live groups: first frame within 100 ms, D-pad
  scrolling within one to two vsyncs.

## 12. Reference: current code map

- `iptv/.../feature/settings/SettingsScreen.kt` — the whole screen: state, section bodies,
  pickers dispatch, status, Playlists pages (2,558 lines).
- `iptv/.../feature/settings/SettingsRail.kt` — section enum, labels, icons, rail.
- `iptv/.../feature/settings/SettingsLabels.kt` — picker targets, option lists, labels.
- `iptv/.../feature/settings/SettingsPicker.kt` — switch row, single and multi pickers.
- `iptv/.../feature/settings/SettingsComponents.kt` — rows, switch, overline.
- `iptv/.../feature/settings/SettingsGroups.kt` — group, heading, image cache group.
- `iptv/.../feature/settings/TimeZonePicker.kt` — zone list, labels, dialog.
- `iptv/.../feature/settings/ArtworkCache.kt` — usage, clear, size formatting.
- `iptv/.../feature/settings/AboutSection.kt` — updates section, phone QR dialog.
- `core/.../app/AppPreferencesRepository.kt` — DataStore keys, enums, defaults, restore.
- `core/.../app/InterfaceScale.kt`, `ColorTheme.kt`, `AppLocale.kt`, `ArtworkCacheSettings.kt`,
  `MetadataLanguages.kt`, `Profiles.kt` — value types and small stores.
- `core/.../core/security/SecretSettingsStore.kt` — encrypted sources, keys, PIN.
- `app/.../app/StreamMateApp.kt` — `openSettings()`, gate, wiring, refresh rescheduling,
  reminders grant value.
- `app/.../app/MainActivity.kt` — `attachBaseContext`, `InterfaceScaled`, first-frame theme.
- `app/.../app/PlaybackBufferPolicy.kt`, `StreamMatePlaybackService.kt` — buffer profiles.
- `app/.../app/Reminders.kt` — `ReminderOverlay.allowed/openSettings`.
- `app/.../app/StreamMateApplication.kt` — image loader with the cache limit.
- Tests: `app/src/androidTest/.../feature/settings/SettingsScreenTest.kt`,
  `ColorThemeSettingsTest.kt`, `SettingsScreenshotDumpTest.kt`; `core/src/test/.../app/*Test.kt`;
  `iptv/src/test/.../feature/settings/ArtworkCacheTest.kt`; `app/src/test/.../PlaybackBufferPolicyTest.kt`.
