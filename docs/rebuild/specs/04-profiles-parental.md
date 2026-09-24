# Profiles and parental controls

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

A household can have up to six profiles. Each keeps its own favourites, recent and last
channel, locked channels, watched positions, Trakt account and Discover data, while sources,
settings, theme and library organisation stay shared. When there is more than one profile the
app asks "Who is watching?" at start (switchable) and offers the same picker on the Home rail.
A profile can be limited to chosen groups of live channels, films and series ("What this
profile may see"); such a restricted profile never sees Discover or Trakt. One household
parental PIN (4–8 digits) guards locked channels, switching away from a restricted profile,
and opening Settings from a restricted profile. The first viewer is the implicit profile
"Everyone", so a TV that never adds a profile behaves as if profiles did not exist.

Layouts: [Home and shell layouts](../design/screens/home-and-shell.md) §5 (picker) and
[Settings layouts](../design/screens/settings.md) (Profiles group, Parental section).

## 2. Feature checklist

Profiles
- PROF-01 The implicit first profile "Everyone" (`profile_default_name`) always exists and
  cannot be removed.
- PROF-02 Add a profile in Settings › General › Profiles: a name (1–24 characters) and Add
  profile; up to 6 profiles in total.
- PROF-03 Each new profile gets the next of six avatar colours automatically.
- PROF-04 Remove a profile (any but the first) from a picker, immediately, with everything it
  kept.
- PROF-05 "Who is watching" row in Settings shows the active profile and opens a picker to
  switch.
- PROF-06 "Ask who is watching at start" switch (default on), shown once there is more than
  one profile.
- PROF-07 Who is watching at start: a full-screen picker of large tiles, the last active
  profile focused, when the household has more than one profile and the switch is on.
- PROF-08 Who is watching on the Home rail (only with two or more profiles), opening the same
  picker.
- PROF-09 Per profile: favourite channels, favourite matches, recent channels, last channel,
  locked channels, watched positions, allowed groups, Trakt account and its cache, Discover
  addons, catalog order and visibility, Library and progress.

What this profile may see
- PROF-10 "What this profile may see": choose the profile to limit, then Live TV groups, Film
  groups and Series groups, each "Everything" or "N groups".
- PROF-11 Multi-choice group pickers ("Groups for <name>"), one row per group across sources,
  with the sources named; changes apply at once.
- PROF-12 A restricted profile sees only its groups in the guide, the player's channel list,
  Movies, Series, Search and Home's recent channels.
- PROF-13 Playing, catching up or zapping to a channel outside the groups is refused with the
  toast "This profile cannot watch that channel".
- PROF-14 A restricted profile never sees Discover (no rail item, no Continue watching cards,
  direct access denied) or Trakt (no Accounts section, no Trakt rows, no first-sync note).
- PROF-15 A note under the group rows says whether the PIN guards the restriction (red when no
  PIN is set).

Parental PIN
- PROF-16 Set a household PIN of 4–8 digits in Settings › Parental controls; stored encrypted.
- PROF-17 Remove (or change) the PIN by entering the current one; removing it unlocks every
  locked channel of every profile.
- PROF-18 Lock or unlock a channel for the active profile in Channel management (only with a
  PIN set).
- PROF-19 A locked channel asks for the PIN before live playback, catch-up, zapping and a
  last-channel start.
- PROF-20 With a restricted profile and a PIN in the household, entering any unrestricted
  profile asks for the PIN (at start, from the rail, from Settings); entering a restricted
  profile never does.
- PROF-21 A restricted profile opens Settings only past the PIN.
- PROF-22 PIN screen: title, what is locked, prompt, masked numeric field, Back and Unlock;
  a wrong PIN clears the field and says "Incorrect PIN code"; unlimited retries.
- PROF-23 Profiles, their sets and the PIN travel in the encrypted `.smbak` backup.

## 3. Entry points and navigation

| Entry | Screen | Back | Focus on entry | After |
|---|---|---|---|---|
| App start, household with > 1 profile and "Ask at start" on | Start-time picker over the start stack (not part of the stack) | See spec 01 §3.3 (quirk: falls through to the stack) | The last active profile's tile | Chosen profile active; the start stack shows |
| Start-time picker, choice needs the PIN | Start-time PIN screen, heading `pin_profile_gate` "Switching profile" | Its Back **button** returns to the picker | Not set explicitly (see 4.6) | Unlock: profile active, start stack shows |
| Home rail "Who is watching" | `ProfilePicker` destination | Pops to Home | The active profile's tile | Choice pops the picker; a different profile switches (through `ProfileGate` when needed) |
| Settings › General › Who is watching | Picker dialog in Settings | Closes the dialog | The selected (active) profile | Switch (through `ProfileGate` when needed); Settings stays open |
| A switch that needs the PIN | `ProfileGate(target, thenSettings = false)` | Pops without switching | Not set explicitly | Unlock: target active, gate popped |
| Settings from a restricted profile (PIN set) | `ProfileGate(null, thenSettings = true)`, heading `pin_settings_gate` "Settings are locked for this profile" | Pops | Not set explicitly | Unlock: the gate is replaced by Settings |
| Play of a locked channel | `PinGate(channel, …)`, heading `pin_locked_channel` "Locked channel" | Pops without playing | Not set explicitly | Unlock: the player (spec 01 SHELL-FR-23) |
| Settings › General › Profiles group | rows in Settings ([Settings](70-settings.md)) | Settings' own | – | – |
| Settings › Parental controls | one row in Settings | Settings' own | The PIN field | – |
| Channel management › a channel › Lock with PIN | [Channel management](21-channel-management.md) | – | – | Toggles the lock |

The routing rules (stack operations, `replacePlayer`, `rememberForGuide`, the order "group
check, then lock") are in [App shell](01-app-shell-navigation.md) §4.4–4.5.

## 4. Behaviour

### 4.1 Profiles

- PROF-FR-01 Identity: the first profile has id `default`; every added profile gets id
  `p<current epoch milliseconds>`. The default profile keeps every preference under the bare
  key; other profiles use the same key suffixed `:<id>` (for example `recent_channel_ids:p1726…`),
  so a TV that never adds a profile keeps its data untouched.
- PROF-FR-02 The shown list is always the default profile first (under `profile_default_name`
  "Everyone" unless it was ever named), then the added profiles in the order they were added.
  A display name is the profile's own name, else "Everyone" for the default profile, else its
  id.
- PROF-FR-03 Add (Settings › General › Profiles): a name field (`profile_name_hint` "Name of the
  new profile", text keyboard, edit on OK, input cut to **24** characters) and
  `profile_add` "Add profile" (check icon). The button is enabled when the name is not blank and
  the household has fewer than **6** profiles counting "Everyone". Adding trims the name, cuts
  it to 24 characters, gives colour index = (number of profiles before adding, counting
  "Everyone") mod 6, clears the field, and does not switch to the new profile. Duplicate names
  are allowed. The storage layer refuses a seventh profile even if asked.
- PROF-FR-04 Avatar colours by index (identical in every colour theme): 0 teal `#2EC4B6`,
  1 amber `#FF9F1C`, 2 red `#E71D36`, 3 violet `#7B61FF`, 4 green `#4CAF50`, 5 pink `#F06292`;
  initial in white. "Everyone" is teal. Indices outside 0–5 are clamped.
- PROF-FR-05 There is no rename or recolour in the interface (the repository can rename; no
  screen calls it).
- PROF-FR-06 Remove (row shown with more than one profile): `profile_remove` "Remove a profile",
  help `profile_remove_help` "Takes their favourites, recents, positions and locks with it.",
  delete icon. It opens a picker listing the added profiles (never "Everyone"); choosing one
  removes it at once, without confirmation:
  - its preference sets: favourite matches, favourite channels, recent channels, last channel,
    locked channels, allowed groups for the three rooms;
  - its watched positions (`playback_progress` rows with its id);
  - its Trakt account, the same as Disconnect ([Trakt](51-trakt.md) TRAKT-FR-10/11);
  - if it was the active profile, "Everyone" becomes active (no PIN asked).
  Its Discover data (installations, catalog order and visibility, Library, progress) is **not**
  removed in beta 23 (gap, open question).
- PROF-FR-07 The active profile is stored as `active_profile_id` (absent = `default`). Every
  per-profile read follows it at once: preferences resolve the active profile first; the
  catalogue's positions, Home's Continue watching, Trakt's sync loop and rows, and Discover's
  stores re-read for the new profile.

### 4.2 Who is watching

- PROF-FR-10 The question is needed at start when the stored profile list is not empty (at
  least one added profile) and `ask_profile_at_start` is true (default). It is decided once per
  app start from the first preferences snapshot; a profile added later in Settings never pulls
  the picker over the current screen. Once answered, it is not asked again after an activity
  recreation in the same process (saved state).
- PROF-FR-11 The switch `profile_ask_at_start` "Ask who is watching at start" (help
  `profile_ask_at_start_help` "Shown when the app starts and there is more than one profile.")
  appears only with more than one profile.
- PROF-FR-12 Picker (start-time and rail share the screen): title `profile_picker_title` "Who is
  watching?", one tile per shown profile in list order, the active profile's tile focused
  first (test tags `profile-tile-<id>`). OK on a tile chooses it.
- PROF-FR-13 Start-time choice: if the choice needs the PIN (4.5), the PIN screen replaces the
  picker (heading "Switching profile", the profile's display name); unlocking makes it active
  and dismisses both; its Back button returns to the picker. Otherwise the profile becomes
  active and the picker is dismissed.
- PROF-FR-14 Rail choice: the picker destination is popped first; choosing the active profile
  does nothing more; another profile switches (4.5), pushing `ProfileGate` when needed.
- PROF-FR-15 Settings choice: `profile_active_title` "Who is watching" with help
  `profile_active_help` "Favourites, recent channels, watched positions and locked channels are
  kept per profile." and value = the active profile's display name (star icon); OK opens a
  single-choice picker of the shown profiles; choosing switches (4.5). Settings stays open,
  also when the new profile is restricted (quirk, 10).
- PROF-FR-16 The rail item appears when the shown list has more than one profile (spec 01
  SHELL-FR-61).
- PROF-FR-17 Related start behaviour (spec 01): with start screen Last channel, the channel is
  resolved from the previously active profile before the question is answered.

### 4.3 What this profile may see

- PROF-FR-20 A restriction is three sets of organisation group keys, one per room (live,
  films, series); an empty set means everything in that room. A profile is restricted when any
  set is non-empty. The restriction only narrows what the device's organisation rules already
  show ([Library organisation](42-library-organization.md)); it never widens them. Group keys
  are the normalised group names the organisation rules use, so the same-named group under
  two sources is one choice; a channel moved into a custom group counts under the custom
  group's key.
- PROF-FR-21 Settings rows (shown with more than one profile, after the ask-at-start switch):
  - `profile_content_title` "What this profile may see", help `profile_content_help` "Limit a
    profile to chosen groups of channels, films and series. Everything else stays out of its
    guide, libraries and search.", lock icon, value = the profile being edited. OK opens a
    single-choice picker of the shown profiles; the choice lasts while Settings is open and
    defaults to the active profile (or back to it if the chosen one is removed).
  - `profile_content_live` "Live TV groups", `profile_content_movies` "Film groups",
    `profile_content_series` "Series groups": value `profile_content_all` "Everything" when the
    set is empty, else plural `profile_content_count` "%d group" / "%d groups".
  - When any profile of the household is restricted, a note (13 sp): `profile_content_pin_ready`
    "Switching to an unrestricted profile, and opening Settings from a restricted one, ask for
    the parental PIN." in `textMuted` when a PIN is set, else `profile_content_pin_missing` "Set
    a parental PIN so a restricted profile cannot switch away or change this." in `danger`.
- PROF-FR-22 Group picker: a multi-choice dialog titled `profile_content_picker_title` "Groups
  for %1$s" (the edited profile's name). Options:
  - Live TV: the guide rail's groups across all enabled sources (not narrowed by any
    restriction), one option per group key, labelled with the group title (else
    `profile_content_ungrouped` "Ungrouped") and described by the names of the sources that
    carry it, joined with ", ";
  - Films / Series: the organisation's group rows for that room, one option per group key,
    labelled with the first non-blank group name (else "Ungrouped"), described by the source
    names.
  - Empty: `profile_content_none_yet` "No groups yet. Add a source and let it sync first."
  Toggling an option adds or removes its key and saves at once; removing the last key lifts the
  restriction for that room. Test tags `settings-profile-group-<key>`.
- PROF-FR-23 Where the active profile's restriction applies:

  | Place | Effect |
  |---|---|
  | Guide: source rail, group rail, channel lists, programme cache | only allowed live groups ([Live TV guide](20-live-tv-guide.md)) |
  | Player's channel list, channel up/down, dialled numbers | only allowed channels; a disallowed zap is refused with the toast |
  | Play, catch-up, zap from anywhere (guide, Home, Search, match card, reminder) | `channelAllowed` check first: refused with `profile_content_blocked` "This profile cannot watch that channel" (short toast) |
  | Movies and Series walls and their group rails | only allowed film / series groups ([Movies and series](40-movies-and-series.md)) |
  | Search | channels, programmes, films, series narrowed after each limit; episodes not narrowed (gap) ([Search](03-search.md)) |
  | Home | recent channels narrowed; Watch next, Recommended and the first-sync note hidden; Discover cards excluded; library Continue watching **not** narrowed (gap) ([Home](02-home.md)) |
  | Home rail | Discover hidden |
  | Settings | behind the PIN (4.5); the Accounts (Trakt) section hidden |
  | Discover | denied: "Addons are unavailable for restricted profiles." ([Discover](50-discover-addons.md) ADDON-03) |
  | Trakt | no rows, no Accounts; the rebuild also runs no sync, scrobble or overlay for a restricted profile ([Trakt](51-trakt.md) TRAKT-FR-36) |
  | Sohva Sport | matches are not narrowed; playing a matched channel runs the channel check |
  | Last-channel start | not checked in beta 23 (gap, spec 01 §10) |

- PROF-FR-24 A restricted profile whose allowed groups no longer exist sees empty lists with no
  explanation (known limitation since beta 12; rebuild: say "This profile's groups are not in
  the current sources" with a hint to ask for Settings).

### 4.4 Parental PIN

- PROF-FR-30 Format: 4 to 8 ASCII digits (`\d{4,8}`). Every PIN field accepts digits only and
  cuts input at 8 characters. Storing a PIN of another shape fails with `error_pin_format` "The
  PIN must be 4–8 digits".
- PROF-FR-31 Storage: one PIN for the household, encrypted with the app's keystore-backed
  cipher in the private preferences file `streammate_secure_sources`, key `parental_pin_v1`
  ([Security and privacy](73-security-privacy.md)). The flag `parental_pin_configured` in the
  main preferences mirrors it for fast reads; at start the secure store wins when they
  disagree (spec 01 SHELL-FR-08).
- PROF-FR-32 Verification: decrypt the stored PIN and compare in constant time; no stored PIN
  or a decryption failure means "wrong". There is **no retry limit, delay or lockout** in beta 23.
- PROF-FR-33 Settings › Parental controls (section 8 of the Settings rail, lock icon), one row:
  title `parental_title` "Parental controls" (`textMuted` Bold); a PIN field 330 dp wide
  (compact, edit on OK, numeric password keyboard, masked, key icon) labelled
  `parental_new_pin` "New 4–8 digit PIN" when no PIN exists, else `parental_current_pin`
  "Current PIN for removal or replacement"; then one button:
  - no PIN: `parental_enable` "Enable", enabled with 4–8 digits: stores the PIN, sets the flag,
    clears the field, status `parental_saved` "Parental control PIN saved securely"; a storage
    failure shows its message in the status line;
  - PIN set: `parental_remove_change` "Remove / change PIN" (danger style), enabled with 4–8
    digits: a correct PIN clears the flag, which also **deletes every profile's locked
    channels**, then deletes the stored PIN, clears the field, status `parental_removed`
    "Parental controls removed"; a wrong PIN clears the field, status `pin_wrong` "Incorrect PIN
    code".
  Changing the PIN is Remove then Enable with the new digits, which loses all locks (quirk, 10).
- PROF-FR-34 Where the PIN is required:
  1. playing a channel in the active profile's locked set: live, catch-up, zap (the gate is
     pushed over the player and replaces both on unlock), and the Last-channel start;
  2. entering an unrestricted profile while any profile in the household is restricted and a
     PIN exists (`entryNeedsPin = pinConfigured && anyRestricted && target not restricted`),
     from the start-time picker, the rail picker or Settings; entering a restricted profile
     never needs it;
  3. opening Settings while the active profile is restricted and a PIN exists (rail, guide,
     Sohva Sport — every Settings entry goes through the same function);
  4. removing or changing the PIN.
- PROF-FR-35 Not guarded in beta 23 (read from the code): Channel management and the Library
  manager, reachable from the guide's options and the Movies/Series walls, open for any profile
  without the PIN, so a restricted profile can unlock its own locked channels, see every
  channel's name, and move a channel into an allowed custom group. Rebuild rule (owner to
  confirm): these management screens ask for the PIN from a restricted profile, as Settings
  does.

### 4.5 Locked channels

- PROF-FR-40 Channel management offers, for the selected channel, a button whose label is
  `channels_configure_pin` "Configure PIN in Settings" (disabled) when no PIN exists, else
  `channels_lock_with_pin` "Lock with PIN" or `channels_remove_pin_lock` "Remove PIN lock".
  Toggling writes the active profile's `locked_channel_ids` set and shows `channels_locked`
  "Channel locked with PIN" / `channels_unlocked` "Channel PIN lock removed" ([Channel
  management](21-channel-management.md)). Neither direction asks for the PIN.
- PROF-FR-41 Locks are per profile: a channel locked for one profile plays freely in another.
- PROF-FR-42 A lock counts only while a PIN exists: the start route ignores locks without a PIN,
  removing the PIN deletes every locked set, and a restored backup without a PIN restores no
  locks. The guide and player show no lock marker on locked channels.
- PROF-FR-43 Order at play time (spec 01 SHELL-FR-20): the profile's group check first (toast),
  then the lock (PIN gate).

### 4.6 The PIN screen

- PROF-FR-50 One screen serves the three gates and the start-time switch. Content, top to
  bottom, centred horizontally, 18 dp apart: the brand (start-aligned); the heading (40 sp
  Black): `pin_locked_channel` "Locked channel", `pin_profile_gate` "Switching profile" or
  `pin_settings_gate` "Settings are locked for this profile"; the subject in `focus` (22 sp):
  the channel's name (`generic_channel` "Channel" when unknown) or the profile's display name
  (the target profile, or the active one for the Settings gate); the message (16 sp
  `textMuted`): `pin_prompt` "Enter the PIN to continue", or `pin_not_configured` "Configure a
  PIN in Settings first" when no PIN exists; the field (360 dp, `pin_code` "PIN code", leading
  "●", numeric password keyboard, masked, test tag `parental-pin`); a centred button row 8 dp
  below: `action_back` "Back" (test tag `parental-back`) and, 12 dp after it, `pin_unlock`
  "Unlock" (check icon, test tag `parental-unlock`).
- PROF-FR-51 Unlock is enabled when a PIN exists, the field has at least 4 digits and no check
  is running. Pressing it shows `pin_checking` "Checking…" on the button while verifying.
  Correct: the gate's unlock action runs. Wrong: the field is cleared and the message becomes
  `pin_wrong` "Incorrect PIN code"; the viewer may try again at once, without limit.
- PROF-FR-52 Back (button or key) leaves the gate without unlocking (start-time PIN screen:
  the button returns to the picker; the key falls through, spec 01 quirk).
- PROF-FR-53 Initial focus is not requested explicitly in beta 23. Rebuild rule: focus the PIN
  field on entry (so the numeric keyboard is one OK away) and return focus to the field after a
  wrong PIN.

## 5. Screen anatomy

Sizes at interface size Normal; tokens in [Design system](../design/01-design-system.md). No
reference screenshot of these screens yet.

### 5.1 Who is watching picker

- Flat `backgroundBottom` fill (no gradient ground), content centred.
- Column, 36 dp gaps: title "Who is watching?" 34 sp Bold `textPrimary`; a row of tiles 28 dp
  apart.
- Tile: focus surface 180 dp wide, shape medium, transparent at rest, focus ring (3 dp
  `textPrimary` inside the bounds) with focus scale 1.04 and the 14 dp focus shadow, padding
  16 dp; column 14 dp gaps: avatar circle 110 dp in the profile colour with the display name's
  first character uppercased (48 sp Black, white); name 20 sp Bold `textPrimary`, one line,
  ellipsised, centred, 4 dp side padding.
- Six tiles need 6 × 180 + 5 × 28 = 1,220 dp; the screen is 960 dp at Normal size, so tiles 5
  and 6 do not fit in beta 23 (no scrolling). Rebuild rule: wrap to a second row or scroll the
  row, keeping the focused tile fully visible.

### 5.2 PIN screen

- The shared screen background with the default 40/24 dp safe padding; column as in PROF-FR-50;
  brand `SohvaTvBrand` 34 sp at the start; field style: the non-compact text field (clip
  medium, `surface` fill, padding 16/14 dp, 16 sp text, cursor `focus`); buttons: the standard
  action button ([Components](../design/02-components.md)).

### 5.3 Settings rows

- Profiles group (Settings › General, its last group, after the reminder row): heading `profiles_title`
  "Profiles"; rows in this order: Who is watching; [> 1 profile] Ask who is watching at start;
  [> 1] What this profile may see; [> 1] Live TV groups, Film groups, Series groups; [any
  restricted] PIN note; the add row (name field with weight 1, 12 dp, Add profile button,
  compact; 10 dp vertical padding); [> 1] Remove a profile. Test tags `settings-profile-active`,
  `settings-profile-ask`, `settings-profile-content`, `settings-profile-groups-<room>`,
  `settings-profile-content-pin`, `settings-profile-name`, `settings-profile-add`,
  `settings-profile-remove`, picker options `settings-profile-<id>`,
  `settings-profile-content-<id>`, `settings-profile-remove-<id>`.
- Parental section: one row, 10 dp gaps (PROF-FR-33); test tags `settings-parental-pin`,
  `settings-parental-save`, `settings-parental-clear`.
- Pickers and the multi picker sit on the opaque panel colour (the translucent surface ladder
  read as a mess over the page on the Shield).

## 6. Data

| Key / table | Type, default | Scope | Notes |
|---|---|---|---|
| `profiles` | string: records joined by U+001E, fields by U+001F: id, name (U+001E/U+001F replaced by spaces), colour index | household | decode skips blank ids, cuts names to 24, clamps colours to 0–5, drops duplicate ids; the default profile appears only if it was ever named |
| `active_profile_id` | string, absent = `default` | household | |
| `ask_profile_at_start` | boolean, true | household | |
| `parental_pin_configured` | boolean, false | household | mirror of the secure store |
| `favourite_channel_ids[:id]`, `favourite_event_ids[:id]` | string sets | per profile | [Channel management](21-channel-management.md), [Sohva Sport](60-sohva-sport.md) |
| `recent_channel_ids[:id]` | ids joined by U+001F, newest first, ≤ 20 | per profile | [Home](02-home.md) |
| `last_channel_id[:id]` | string | per profile | start route, zap-back |
| `locked_channel_ids[:id]` | string set | per profile | only meaningful with a PIN |
| `allowed_groups_live[:id]`, `allowed_groups_movies[:id]`, `allowed_groups_series[:id]` | string sets; absent = everything | per profile | removed when set empty |
| `playback_progress` | rows keyed (contentKey, profileId) | per profile | [Movies and series](40-movies-and-series.md) |
| `trakt_state`, Trakt account store | – | per profile | [Trakt](51-trakt.md) |
| Discover databases and preferences | – | per profile | [Discover](50-discover-addons.md) |
| `streammate_secure_sources` › `parental_pin_v1` | encrypted string | household | |

All of the preference keys live in the DataStore file `streammate_preferences`. Shared by the
household: sources, every setting, colour theme, interface size and language, time zone,
organisation rules and custom groups, channel customisations, followed sports, reminders.

Backup ([Backup and restore](71-backup-restore.md)): the encrypted `.smbak` carries `profiles`
(id, name, colour), `activeProfileId`, `askProfileAtStart`, per-profile `profileData`
(favourite matches and channels, recents, last channel, locked channels, allowed groups per
room) and `parentalPin` (the digits, inside the passphrase-encrypted payload). Restore keeps at
most 6 profiles, sets the PIN when the backup has one, and otherwise clears the device's PIN and
restores no locked channels. Watched positions, Trakt and Discover data are not in the backup.

## 7. External interfaces

None. Profiles and the PIN are local. Removing a profile disconnects its Trakt account locally
(no call to Trakt).

## 8. Edge cases and limits

- One profile only: no start-time question, no rail item, no ask/content/remove rows; the PIN
  still guards locked channels.
- All added profiles removed: the question is no longer asked (the stored list is empty).
- The active profile removed: "Everyone" becomes active immediately, without the PIN, even when
  a restriction exists (the remover is already inside Settings).
- A restricted profile switching to another restricted profile: no PIN.
- No PIN but restrictions set: anyone can switch away and open Settings; the red note says so.
- PIN removed: all locks of every profile are deleted; restrictions stay but no longer guard.
- Forgotten PIN on a restricted profile: there is no recovery path in the app; Settings and
  every unrestricted profile stay closed (see open questions).
- A locked channel whose PIN screen is reached with no PIN stored (cannot happen through the
  interface; possible only through inconsistent data): the screen says "Configure a PIN in
  Settings first" and Unlock stays disabled.
- Profile ids are time-based; two profiles added in the same millisecond cannot happen through
  the interface.
- Names with the separator characters U+001E/U+001F are stored with spaces instead.
- Process death: the active profile persists; the start-time question is asked again on the
  next cold start.

## 9. Lightweight by design

- Start-up: profiles, the active profile, the restriction sets and the PIN flag come from the
  one preferences snapshot the shell already reads before the first frame; nothing else is read
  for profiles. The start-time picker composes only six tiles (no images: circles and text) on
  a flat fill, one full-screen pass.
- Home's Continue watching read starts at process start for the last active profile, the likely
  answer; choosing another profile restarts it. Keep this (a correct guess saves a read; a wrong
  one costs one ≤ 20-row query), and cancel the superseded read.
- PIN verification today runs on the UI coroutine (keystore decryption on the main thread) and
  the gate reads the secure store during composition. Rebuild: read the "PIN exists" state
  once into memory at start; verify on a background thread; keep the constant-time comparison.
- Restriction filtering today happens in Kotlin after the query (organised lists, search) or
  inside the organised list pipeline, so a restricted profile still pays for reading rows it
  will drop, and limited queries return fewer rows. Rebuild rule: keep the allowed group keys in
  a small indexed table (`profile_allowed_group(profileId, room, groupKey)`), filled from the
  preference sets, and join it by key inside the paged and limited queries (no `IN` list: older
  SQLite allows only 999 bound parameters and a profile may allow hundreds of groups).
  Unrestricted profiles skip the join entirely.
- A profile switch invalidates every per-profile flow at once. Only the visible screen re-reads
  (Home); other screens re-read when opened. The Trakt loop and Discover stores for the old
  profile stop.
- Preference writes rewrite the whole DataStore file; per-profile sets are small (≤ 20 recents,
  locks and favourites in the tens), so keep large per-profile data (positions, histories) in
  the database, never in preferences.
- Per frame: the picker's tiles use the focus ring; on the low-end class draw the ring without
  the 14 dp shadow (shadows are blurred passes on a Mali-G31). The PIN screen stands on the
  shared cached ground.

## 10. Lessons from the current app

- Profiles were added in beta 10 (8 September 2026) without touching existing data: the default
  profile keeps the bare preference keys and positions were migrated to it (`cd802e2`,
  `docs/SOHVA_TV_BETA_10.md`).
- The start-time question is decided once at start so a profile added later in Settings does not
  pull the picker over the screen (`adaa508`).
- Restrictions were built as a per-profile allow-list narrowing the device rules, not as
  per-profile organisation rules, to keep the organisation views (and their join-order trap)
  untouched (`docs/NEXT_FEATURES_PLAN.md` § 4.2, `f533559`).
- "Leaving a restricted profile should ask for the PIN, or the restriction is decoration": the
  PIN guards entering unrestricted profiles and Settings (plan § 4.2, `ProfileRestrictionTest`).
- After the Shield run the pickers moved to the opaque panel colour, and Who is watching was
  added to the Home rail (plan § 4.2).
- Leftover profiles from one device test put the picker in front of the next test; the shared
  clear-state rule resets the household to one profile ([lessons](../plan/08-lessons-learned.md)
  7.5, `ClearAppStateRule`).
- Known limitation since beta 12: a restricted profile whose groups no longer exist sees an
  empty guide with no reason given (`docs/SOHVA_TV_BETA_12.md`).
- Quirks and gaps to fix in the rebuild (current behaviour, not intended; read from the code):
  - Changing the PIN means removing it, which deletes every profile's locked channels.
  - Channel management and the Library manager are open to a restricted profile without the PIN
    (PROF-FR-35).
  - Library Continue watching and Search episode results ignore the restriction; the
    Last-channel start ignores it too.
  - Settings stays open after switching into a restricted profile from Settings; the rebuild
    returns to Home.
  - The picker cannot show profiles 5 and 6 at Normal size.
  - Removing a profile leaves its Discover data behind.
  - PIN verification on the main thread; no initial focus on the PIN field.
  - No confirmation before removing a profile.

### Open questions

- Should wrong PIN attempts be limited (for example a 30 s pause after 5 wrong tries), or stay
  unlimited as in beta 23?
- Is a PIN recovery path wanted (for example clearing the PIN by restoring a backup from the
  default profile, or a device-level reset of parental data)?
- Should Channel management and the Library manager require the PIN from a restricted profile
  (recommended)?
- Should locked channels stay per profile, or become household-wide like the PIN?
- Should the rebuild add rename and colour choice for profiles (the repository already supports
  rename)?
- Should removing a profile ask for confirmation and also delete its Discover data?
- Should "Remove / change PIN" become two actions, with Change keeping the locks?

## 11. Acceptance tests

Unit (JVM):
- Keys: the default profile keeps the bare key, others get `:<id>` (mirror `ProfilesTest`).
- Encoding round trip; broken records dropped; names cut to 24; colours clamped; duplicates
  dropped; the default shown first, named or not (mirror `ProfilesTest`).
- Restriction: an empty room allows everything, a chosen set allows only its keys; `with`
  replaces one room; the PIN guards unrestricted profiles once a restriction and a PIN exist,
  and never a restricted target (mirror `ProfileRestrictionTest`).
- Add refuses a seventh profile; colour index = count mod 6.
- Removing a profile removes every per-profile key and resets the active profile.
- Removing the PIN deletes every profile's locked set.
- PIN format 4–8 digits; verification is constant-time and false without a stored PIN.

Instrumentation (debug build, state cleared before each test):
- Picker: every profile is a tile, the active one focused first, OK chooses (mirror
  `ProfilePickerScreenTest`).
- PIN screen: the correct PIN unlocks; a wrong one clears the field and shows "Incorrect PIN
  code" (mirror `ParentalPinScreenTest`, extended).
- Settings: a profile is added, switched to and removed; a profile can be limited to chosen
  groups (mirror `SettingsScreenTest.aProfileIsAddedSwitchedToAndRemoved`,
  `aProfileCanBeLimitedToChosenGroups`).
- Guide: a restricted profile sees only its groups; widening restores the rest (mirror
  `GuideScreenTest.aRestrictedProfileSeesOnlyItsGroups`).
- Positions: each profile keeps its own (mirror `CatalogueRepositoryTest.eachProfileKeepsItsOwnPositions`).
- Backup: every profile and what it keeps survives a backup (mirror
  `BackupCustomGroupsTest.everyProfileAndWhatItKeepsSurvivesABackup`); a backup without a PIN
  restores no locks.
- Gates: a restricted profile opening Settings meets the PIN; switching to an unrestricted
  profile meets the PIN; switching to a restricted one does not; a locked channel from the
  guide, from Home and by zapping meets the PIN; a disallowed channel shows the toast.
- Home rail: Who is watching appears with a second profile (mirror `HomeScreenTest`).
- Restricted profile: no Discover rail item, no Trakt rows, no Accounts section.

Manual, on a device:
- Start with two profiles: picker, the last profile focused; choose the other; Home shows its
  own rows.
- Six profiles at every interface size: every tile reachable.

Low-end performance (Elisa-class box or `.local/slowbox`):
- Profile switch from the rail: Home's first content frame for the new profile within 1 s.
- A restricted profile's guide opens within the same budget as an unrestricted one (restriction
  joined in SQL, no whole-list filtering).

## 12. Reference: current code map

- `core/src/main/java/com/streammate/tv/app/Profiles.kt` — profile model, ids, key suffixing, encoding, default-first list, `entryNeedsPin`, `ProfileRestriction`, `ProfileData`.
- `core/src/main/java/com/streammate/tv/app/AppPreferencesRepository.kt` — per-profile keys, add/remove/switch, allowed groups, locks, PIN flag (clears locks), recents.
- `core/src/main/java/com/streammate/tv/app/StreamMateContentColors.kt` — profile avatar colours.
- `app/src/main/java/com/streammate/tv/app/ProfilePickerScreen.kt` — Who is watching tiles.
- `iptv/src/main/java/com/streammate/tv/feature/settings/ParentalPinScreen.kt` — PIN screen (4–8 digits).
- `app/src/main/java/com/streammate/tv/app/StreamMateApp.kt` — start-time question and PIN, `ProfilePicker`, `ProfileGate`, `PinGate`, `switchProfile`, `openSettings`, group check before play, profile removal wiring.
- `iptv/src/main/java/com/streammate/tv/feature/settings/SettingsScreen.kt` — Profiles group rows and pickers, Parental section.
- `core/src/main/java/com/streammate/tv/core/security/SecretSettingsStore.kt` — encrypted PIN store and verification.
- `iptv/src/main/java/com/streammate/tv/iptv/repository/OrganizationRepository.kt` — the active restriction and its narrowing of organised lists.
- `iptv/src/main/java/com/streammate/tv/iptv/repository/GuideStore.kt` — `channelAllowed`, restricted recent channels and search.
- `iptv/src/main/java/com/streammate/tv/feature/settings/ChannelEditorScreen.kt` — lock toggle.
- `app/src/main/java/com/streammate/tv/app/StreamMateBackupManager.kt` — profiles, per-profile data and PIN in backups.
- Tests: `ProfilesTest`, `ProfileRestrictionTest`, `ProfilePickerScreenTest`, `ParentalPinScreenTest`, `SettingsScreenTest`, `GuideScreenTest`, `CatalogueRepositoryTest`, `BackupCustomGroupsTest`, `HomeScreenTest`, `ClearAppStateRule`.
