# Remote button mapping

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

While a stream plays with nothing on top of it (the player's "clean screen"), the remote's
buttons are free: the viewer chooses what each of twelve buttons does on a short **press** and
on a **hold**. The six buttons every remote has (Up, Down, Left, Right, OK, Back) and six that
some remotes have (CH+, CH−, Info, Audio, Captions, Menu) each get a press slot and a hold slot;
Back's press is fixed as "dismiss, then leave" so a remote always keeps a way out. Twenty-five
actions are grouped as Channels, Information, Playback, Sound and picture, Leave, plus Nothing;
some apply only to live TV and some only to catch-up and films. The defaults reproduce what the
buttons did before mapping existed and put zap-back and "guide at this channel" on holds that
used to do nothing. The mapping is edited in Settings → Remote buttons, a grid of cells with a
grouped action list, a read-back line and a two-step reset. Everywhere else — menus, the guide,
the player's own overlays — the D-pad keeps its ordinary meaning.

## 2. Feature checklist

- REMOTE-01 Twelve buttons, each with a Press slot and a Hold slot (23 mappable slots).
- REMOTE-02 A press acts on release; a hold acts on the first auto-repeat; a hold never also acts as a press.
- REMOTE-03 Twenty-five actions in five groups, plus Nothing.
- REMOTE-04 Actions marked "Live TV only" or "Catch-up and films only"; mapped where they do not apply, a press shows the player's chrome instead.
- REMOTE-05 Defaults that keep pre-mapping behaviour and add zap-back (Back hold, Left hold) and guide at this channel (Right hold).
- REMOTE-06 Back press fixed: dismiss the top layer, then leave the player.
- REMOTE-07 Back hold mappable, for remotes that pass a long Back press to the app.
- REMOTE-08 CH+, CH−, Info, Audio, Captions and Menu under "If your remote has them".
- REMOTE-09 Mapping applies only on the player's clean screen; overlays, menus and other screens keep their own keys.
- REMOTE-10 Up/Down presses step into an open information box, or into the catch-up/film controls, before the mapping is consulted.
- REMOTE-11 Digits dial a channel on live TV (not mappable).
- REMOTE-12 Keys outside the grid show the chrome and are left to Android.
- REMOTE-13 Settings grid: one row per button, Press and Hold cells naming the assigned action.
- REMOTE-14 Choosing a cell replaces the grid with the grouped action list; the current action is marked and focused; scope suffixes on restricted actions.
- REMOTE-15 A read-back line names the focused cell and its action.
- REMOTE-16 Reset to defaults with a confirmation step.
- REMOTE-17 Focus returns to the edited cell after a choice or Back.
- REMOTE-18 Mappings kept on the device for all profiles, tolerant of unknown entries, included in backups.
- REMOTE-19 One-time migration from the old "Remote channel browser" setting.

## 3. Entry points and navigation

- Settings rail, fourth section **Remote buttons** (`settings_section_remote`, icon Aspect);
  choosing the section (OK) puts focus on the **Up / Press** cell
  ([Settings](70-settings.md), [design/screens/settings.md](../design/screens/settings.md) §2, §8).
- The player's clean screen ([Player](30-player.md) §4.3) is where mappings take effect.

Back in Settings → Remote buttons:

| State | Back does | Focus afterwards |
|---|---|---|
| Action list open (a cell being edited) | Closes the list without changing the slot; the grid returns | The cell that was being edited |
| Grid, reset confirmation showing | Settings' own Back (the confirmation is not dismissed by Back) | Per [Settings](70-settings.md) |
| Grid | Settings' own Back | Per [Settings](70-settings.md) |

## 4. Behaviour

### 4.1 Buttons

- REMOTE-FR-01 The buttons, in grid order, with the Android key codes that count as them:

  | Button | Key codes | Optional | Label key | EN |
  |---|---|---|---|---|
  | UP | `KEYCODE_DPAD_UP` (19) | no | `remote_button_up` | Up |
  | DOWN | `KEYCODE_DPAD_DOWN` (20) | no | `remote_button_down` | Down |
  | LEFT | `KEYCODE_DPAD_LEFT` (21) | no | `remote_button_left` | Left |
  | RIGHT | `KEYCODE_DPAD_RIGHT` (22) | no | `remote_button_right` | Right |
  | OK | `KEYCODE_DPAD_CENTER` (23), `KEYCODE_ENTER` (66), `KEYCODE_NUMPAD_ENTER` (160) | no | `remote_button_ok` | OK |
  | BACK | `KEYCODE_BACK` (4) | no | `remote_button_back` | Back |
  | CHANNEL_UP | `KEYCODE_CHANNEL_UP` (166) | yes | `remote_button_channel_up` | CH+ |
  | CHANNEL_DOWN | `KEYCODE_CHANNEL_DOWN` (167) | yes | `remote_button_channel_down` | CH− (U+2212) |
  | INFO | `KEYCODE_INFO` (165) | yes | `remote_button_info` | Info |
  | AUDIO | `KEYCODE_MEDIA_AUDIO_TRACK` (222) | yes | `remote_button_audio` | Audio |
  | CAPTIONS | `KEYCODE_CAPTIONS` (175) | yes | `remote_button_captions` | Captions |
  | MENU | `KEYCODE_MENU` (82) | yes | `remote_button_menu` | Menu |

- REMOTE-FR-02 A slot is (button, gesture) with gestures PRESS (`remote_column_press` "Press")
  and HOLD (`remote_column_hold` "Hold"). The slot (BACK, PRESS) is **fixed** and never stored,
  mapped, shown as a cell or decoded. Mappable slots: the other 23, in grid order (button order,
  then Press before Hold).
- REMOTE-FR-03 Stored slot name: `<BUTTON>.<GESTURE>` with the enum names above, e.g.
  `CHANNEL_UP.PRESS`.

### 4.2 Press versus hold

- REMOTE-FR-05 The app sets no timing of its own. Android repeats a held key's key-down after
  the platform key-repeat timeout (it follows the long-press timeout, about 400–500 ms by default
  and longer when the viewer raised the accessibility "touch & hold delay"), then about every
  50 ms, and sends one key-up on release. The **first repeat** (repeat count > 0) is the hold.
- REMOTE-FR-06 Resolver (pure logic, no UI; one pending button and a "held" flag):
  1. Key-down, repeat 0, or a key-down of a button other than the pending one: that button
     becomes pending, held = false; nothing fires.
  2. Key-down with repeat > 0 for the pending button, not yet held: held = true; fire
     **Hold(button)**.
  3. Further repeats while held: nothing.
  4. Key-up of the pending button: fire **Press(button)** unless held; clear pending and held.
  5. Key-up of any other button, and any key that is not one of the twelve: nothing.
  6. Reset: clear pending and held (used when an overlay takes the keys).
  `isHolding(key)` is true while a hold has fired for that key and it is still down (its
  release must be swallowed).
- REMOTE-FR-07 Consequences: a press fires on release (about 100 ms later than acting on
  key-down; not felt on a D-pad); a hold fires once however long the key is held; pressing a
  second key while holding the first abandons the first (its release does nothing).
- REMOTE-FR-08 Remotes that send no auto-repeat (some HDMI-CEC and IR remotes) can never produce
  a hold; presses still work. Some remotes keep a long Back for the system, so Back hold may
  never arrive; the help line says so (`remote_back_hold_note`).
- REMOTE-FR-09 (Rebuild requirement.) A hold that opens an overlay or changes the channel owns
  the rest of that key — its remaining repeats and its release — even after focus has moved to
  the overlay, so the release cannot activate the newly focused row. The resolver lives for the
  whole player session (not per channel), so a held channel key zaps exactly once (Q-02, Q-03).

### 4.3 Actions

- REMOTE-FR-10 Actions, in picker order (group order, then action order). Scope: **Any**,
  **Live** (live channels only; label suffix `remote_scope_live` "Live TV only") or
  **Timeshift** (catch-up and VOD only; suffix `remote_scope_timeshift` "Catch-up and films
  only"). "Nothing to act on" makes a press fall back (REMOTE-FR-24).

  | Group (label) | Action | Scope | Label key: EN | Effect in the player ([Player](30-player.md)) | Nothing to act on when |
  |---|---|---|---|---|---|
  | Channels (`remote_group_channels`) | NEXT_CHANNEL | Live | `remote_action_next_channel`: Next channel | Next channel in the playing group's order, wrapping | fewer than 2 channels, or the channel is not in the list |
  | | PREVIOUS_CHANNEL | Live | `remote_action_previous_channel`: Previous channel | Previous in the group, wrapping | same |
  | | SWITCH_TO_PREVIOUS_CHANNEL | Live | `remote_action_switch_to_previous_channel`: Switch to previous channel | Zap-back; again returns | no previous channel, or it is the playing one |
  | | OPEN_CHANNEL_BROWSER | Live | `remote_action_open_channel_browser`: Channel list | Opens the channel list on the playing channel | the list is empty |
  | | OPEN_GROUP_BROWSER | Live | `remote_action_open_group_browser`: Group list | Opens the channel list with the group list open on the shown group | no channels or no groups |
  | Information (`remote_group_information`) | PROGRAMME_INFO | Any | `remote_action_programme_info`: Programme info | Shows the live box (live) or the controls with Play/Pause focused (timeshift) | — |
  | | TOGGLE_STATS | Any | `remote_action_toggle_stats`: Playback info on/off | Toggles the info line and clock | — |
  | | SCORE_TICKER | Any | `remote_action_score_ticker`: Score ticker on/off | Toggles the score ticker | VOD (ticker not offered) |
  | | GUIDE_AT_CHANNEL | Live | `remote_action_guide_at_channel`: Guide at this channel | Leaves to `[Home, Guide]`, guide focused on this channel | — |
  | | QUICK_ACTIONS | Any | `remote_action_quick_actions`: Quick actions | Opens quick actions | — |
  | Playback (`remote_group_playback`) | PLAY_PAUSE | Timeshift | `remote_action_play_pause`: Play / pause | Toggles | — |
  | | SEEK_BACK | Timeshift | `remote_action_seek_back`: Back 10 s | Skips back by the skip step with the ladder | — |
  | | SEEK_FORWARD | Timeshift | `remote_action_seek_forward`: Forward 10 s | Skips forward likewise | — |
  | | RESTART | Timeshift | `remote_action_restart`: Restart | Seeks to 0 | — |
  | | SHOW_CONTROLS | Timeshift | `remote_action_show_controls`: Show controls | Shows the controls, Play/Pause focused | — |
  | Sound and picture (`remote_group_sound_and_picture`) | AUDIO_PICKER | Any | `remote_action_audio_picker`: Audio track | Opens the audio picker | — |
  | | NEXT_AUDIO_TRACK | Any | `remote_action_next_audio_track`: Next audio track | Steps to the next audio track (counts as the viewer's choice) | fewer than 2 audio tracks |
  | | SUBTITLE_PICKER | Any | `remote_action_subtitle_picker`: Subtitles | Opens the subtitle picker | — |
  | | TOGGLE_SUBTITLES | Any | `remote_action_toggle_subtitles`: Subtitles on/off | Off if any is on, else the first track (counts as the viewer's choice) | no subtitle tracks |
  | | CYCLE_PICTURE_SHAPE | Any | `remote_action_cycle_picture_shape`: Picture shape | Fit → Fill → Zoom | — |
  | Leave (`remote_group_leave`) | LEAVE_PLAYER | Any | `remote_action_leave_player`: Leave the player | As Back from the bare picture | — |
  | | GO_HOME | Any | `remote_action_go_home`: Home | Stack `[Home]` | — |
  | | GO_GUIDE | Any | `remote_action_go_guide`: Guide | Stack `[Home, Guide]` | — |
  | | GO_SPORT | Any | `remote_action_go_sport`: Sohva Sport | Stack `[Home, Today]` | — |
  | Nothing (`remote_group_nothing`, no heading shown) | NOTHING | Any | `remote_action_nothing`: Nothing | — | always |

- REMOTE-FR-11 Stored action name: the enum name. Unknown names are dropped on read.
- REMOTE-FR-12 The seek labels say "10 s" whatever the skip step (the transport buttons name the
  real step). The rebuild labels them "Back %1$s" / "Forward %1$s" with the step, as the
  transport buttons do (Q-05).

### 4.4 Defaults

- REMOTE-FR-13 Seventeen slots have a default; the other six are Nothing:

  | Button | Press | Hold |
  |---|---|---|
  | Up | Channel list (live) | Next channel (live) |
  | Down | Channel list (live) | Previous channel (live) |
  | Left | Back 10 s (timeshift) | Switch to previous channel (live) |
  | Right | Forward 10 s (timeshift) | Guide at this channel (live) |
  | OK | Programme info | Quick actions |
  | Back | fixed: "Dismiss, then leave" (`remote_back_fixed`) | Switch to previous channel (live) |
  | CH+ | **Previous** channel (live) | Nothing |
  | CH− | **Next** channel (live) | Nothing |
  | Info | Playback info on/off | Nothing |
  | Audio | Audio track | Nothing |
  | Captions | Subtitles | Nothing |
  | Menu | Quick actions | Nothing |

  CH+ steps to the channel **above** in the list (previous index) and CH− to the one below, as
  before mapping existed (Q-04). Zap-back is on two holds on purpose: Back hold is where viewers
  of other TV apps look for it; Left hold is reachable without lifting the thumb from the ring.
- REMOTE-FR-14 In effect on each screen type with the defaults: live Left/Right presses have no
  live action and show the live box; timeshift Up/Down presses step into the controls
  (REMOTE-FR-22) and Left/Right holds do nothing; CH+/CH− presses in timeshift show the controls.

### 4.5 Where mappings apply

- REMOTE-FR-15 Only on the player's clean screen: the video view has focus and no channel list
  is open. Not while the live box's buttons, the transport controls, a picker or quick actions
  hold focus (those receive the keys as ordinary focus targets), not in the channel/group list
  (which owns every key, [Player](30-player.md) PLAY-FR-52), not on any other screen, and not in
  the Discover player (whose keys are fixed, [Discover](50-discover-addons.md) ADDON-FR-89).
- REMOTE-FR-16 Live = a live channel. Timeshift = catch-up or VOD. An action whose scope does
  not include the current type does nothing as an action (REMOTE-FR-24 decides the fallback).
- REMOTE-FR-17 The Settings grid itself never applies mappings; it is an ordinary focus grid.

### 4.6 Dispatch on the clean screen

For every key event reaching the video view (down with its repeat count, or up):

- REMOTE-FR-18 **Digits** 0–9 / numpad 0–9: live → the first key-down (repeat 0) appends to the
  dial buffer (max 4, PLAY-FR-59); every event is consumed. Timeshift → not consumed.
- REMOTE-FR-19 **Keys outside the grid** (not one of the twelve): on the first key-down (repeat
  0) show the chrome; never consumed (Android handles them; media keys are expected to reach the
  app's media session — unverified, [Player](30-player.md) Q-09).
- REMOTE-FR-20 **Back**:
  1. If the Back **hold** action does not apply to this screen type, reset the resolver and do
     not consume anything: Back travels the system route (the window's Back handler, one layer
     per press, [Player](30-player.md) §3.2), even when held.
  2. Otherwise run the resolver: a Hold performs the Back-hold action and is consumed; a Press
     is **not** consumed (the system delivers Back normally); the first key-down is not consumed
     (so Android tracks the Back key), later repeats are consumed, and the release after a hold is
     consumed (no Back press follows a hold).
  With the default (zap-back, live only): holding Back on live zaps back; holding Back in
  catch-up or VOD is an ordinary Back on release. Mapped to Nothing (which applies everywhere),
  a held Back does nothing at all and the player stays (§8).
- REMOTE-FR-21 **Other grid buttons**: on the first key-down remember whether the live box was
  visible at that moment; then run the resolver:
  - Hold → perform the hold action; consumed. Nothing happens if it does not apply or has
    nothing to act on (no fallback for holds).
  - Press → REMOTE-FR-22, else REMOTE-FR-23, else REMOTE-FR-24; consumed.
  - Anything else (first down, repeats, release after a hold) → consumed, so nothing else moves
    focus on a grid key.
  The chrome is **not** revealed on the key-down of a grid button (a press that opens the
  channel list would otherwise flash the info box first); whatever the press does reveals what
  it needs.
- REMOTE-FR-22 **Up/Down press into open boxes** (before any mapping): if the live box was
  visible at key-down → focus its action row. Else, in timeshift → show the controls and focus
  Play/Pause. Else continue.
- REMOTE-FR-23 **Mapped press**: perform the press slot's action; it counts as done only if it
  applies here and had something to act on.
- REMOTE-FR-24 **Fallback press**: show the chrome (live box or controls); in timeshift an OK
  press also focuses Play/Pause.
- REMOTE-FR-25 Performing an action that opens an overlay or changes focus follows the player's
  rules for that overlay; performing Leave, Home, Guide or Sport leaves the player.
- REMOTE-FR-26 Whenever the channel list is open, every key resets the resolver (a key that went
  down before the list opened cannot complete a gesture afterwards).
- REMOTE-FR-27 After a Back hold or a grid hold, the key-up is swallowed only while the resolver
  says the key is still held (`isHolding`).

### 4.7 Storage, migration, reset, backup

- REMOTE-FR-30 Stored in the app's DataStore as the string set `remote_mappings`, one entry per
  mapped slot: `<SLOT>=<ACTION>`, e.g. `LEFT.HOLD=SWITCH_TO_PREVIOUS_CHANNEL`. Nothing is
  represented by absence; the fixed slot is never written. Not per profile.
- REMOTE-FR-31 Reading: each entry must split on `=` into exactly two parts and its slot on `.`
  into exactly two known names, not the fixed slot, with a known action; anything else is
  dropped (an older build survives a newer build's mapping). A stored **empty** set means every
  slot is Nothing.
- REMOTE-FR-32 When `remote_mappings` has never been written, the mapping is derived from the
  legacy preference `remote_channel_key_mode` (default `DPAD_AND_CHANNEL_KEYS`):
  `DPAD_AND_CHANNEL_KEYS` → the defaults; `CHANNEL_KEYS_ONLY` ("CH+/CH− only") → the defaults
  with Up press and Down press = Nothing. The legacy key is kept (a rollback build still reads
  it) but never shown.
- REMOTE-FR-33 Assigning a slot reads the current mapping (with migration), applies the change
  and writes the whole set; assigning Nothing removes the entry; assigning to the fixed slot is
  ignored.
- REMOTE-FR-34 Reset writes the default set explicitly (17 entries), so the legacy setting no
  longer has a say.
- REMOTE-FR-35 Backup: `remoteMappings` = the encoded entries sorted, as a JSON string array;
  `remoteChannelKeyMode` is required (an unknown value fails the restore with
  `backup_error_remote` "Unknown remote-control setting"). Restore: `remoteMappings` present →
  decoded (unknown entries dropped); absent (backups older than mapping) → the defaults the
  legacy value implies. Both keys are written back ([Backup](71-backup-restore.md)).
- REMOTE-FR-36 Changes apply to the next key press in the player (the player reads the current
  mapping from preferences).

### 4.8 Settings: the grid

- REMOTE-FR-40 One settings group (§5.1): heading `settings_section_remote` "Remote buttons";
  help `remote_mapping_help` "These work while watching. Menus and the channel list keep their
  own buttons."; the column header ("Press", "Hold"); the six standard rows; the optional heading
  `remote_optional_heading` "If your remote has them"; the six optional rows; the read-back line;
  the reset row.
- REMOTE-FR-41 Each row: the button's name, then a Press cell and a Hold cell, each an action
  button labelled with the assigned action's name (without scope suffix). The Back / Press cell
  is plain text `remote_back_fixed` "Dismiss, then leave" and cannot be focused.
- REMOTE-FR-42 Read-back line: after any cell has had focus, `remote_slot_preview`
  "%1$s, %2$s: %3$s" (button, gesture, action), e.g. "Up, Press: Channel list", for the most
  recently focused cell (it keeps the last cell when focus moves to the reset row). Before that,
  `remote_back_hold_note` "Some remotes keep a long press on Back for themselves."
- REMOTE-FR-43 Reset: "Reset to defaults" (`remote_reset`) arms the confirmation, which replaces
  it in place with `remote_reset_confirm` "Reset every button to its default?", **Reset**
  (`remote_reset_confirm_action`, danger style) and **Cancel** (`remote_reset_cancel`). Reset
  writes the defaults and disarms; Cancel disarms. The rebuild moves focus to "Reset to defaults"
  after either (beta 23 leaves focus to fall where the vanished button was).
- REMOTE-FR-44 OK on a cell opens the action list for that slot (REMOTE-FR-45).

### 4.9 Settings: the action list

- REMOTE-FR-45 The grid is replaced in place (same group position) by: heading
  `remote_slot_title` "%1$s, %2$s" (button, gesture; shown upper-cased, e.g. "UP, PRESS"); for
  each group except Nothing its name as a small heading followed by its actions; finally the
  Nothing button without a heading. Each action is an action button labelled "<action>" or
  "<action> · Live TV only" / "<action> · Catch-up and films only". The current action is shown
  selected and takes focus when the list opens. Every action is selectable whatever its scope
  (the plan's "greyed out with the reason" was built as the suffix only).
- REMOTE-FR-46 Choosing an action assigns it and returns to the grid with focus on the edited
  cell. Back returns without changing anything, focus on the edited cell.
- REMOTE-FR-47 The rebuild keeps every cell's focus requester in one map (beta 23 wires the
  Up / Press cell to the section's requester, and its return path asks a different requester that
  is attached to nothing — Q-01).

## 5. Screen anatomy

The section's place in the Settings frame, the rail, the group vocabulary and the component
looks are in [design/screens/settings.md](../design/screens/settings.md) (§3 Group vocabulary,
§4 Components, §6 "Remote buttons"). Values from `RemoteMappingSection.kt`:

### 5.1 Grid

- Group: 1 dp `divider` hairline inset 14 dp, then a column padded 14 (horizontal and vertical)
  with 10 dp between children; no fill.
- Heading: upper-cased, overline 12/16 sp Bold, letter spacing 1.4 sp, `textDim`.
- Help: 13 sp `textMuted`.
- Spacer 6 (plus the group's 10 spacing).
- Column header row (spacing 10): a 110 dp spacer, then "Press" and "Hold", each weight 1 with
  12 dp horizontal padding, 13 sp Bold `textMuted`.
- Button rows (spacing 10, centre-aligned): name 110 dp wide, Bold, `textPrimary` (no size set:
  the theme's default text style; the rebuild uses body 16/23); two cells weight 1 each: compact action buttons (shape small 8, padding 12×7, caption
  12/16 Bold; rest `surface` fill and `textPrimary` text; focused `textPrimary` fill, `background`
  text, scale 1.03, 10 dp shadow). Fixed cell: text `textMuted`, weight 1, horizontal padding 12.
- Before the CH+ row: spacer 6, then "If your remote has them" 13 sp Bold `textMuted`.
- Spacer 4, read-back line 13 sp `textMuted`, spacer 8.
- Reset row (spacing 10, centre-aligned): compact "Reset to defaults"; armed: confirmation text
  `textPrimary`, compact danger "Reset" (`danger` text; `danger` fill when focused), compact
  "Cancel".
- Test tags: `settings-remote-slot-<button>-<gesture>` (enum names lower-case, e.g.
  `settings-remote-slot-channel_up-press`), `settings-remote-reset`,
  `settings-remote-reset-confirm`, `settings-remote-reset-cancel`.

### 5.2 Action list

- Same group frame; heading as above ("UP, PRESS").
- Per group: spacer 4, group name 13 sp Bold `textMuted`, then its actions, each a compact
  action button sized to its label (not full width), stacked with the group's 10 dp spacing.
  Selected (current) look: `surfaceFocused` fill, `focus` text.
- Test tags: `settings-remote-action-<action>` (lower-case enum name, e.g.
  `settings-remote-action-open_channel_browser`).

## 6. Data

| Key | Type | Default | Scope | Backup |
|---|---|---|---|---|
| `remote_mappings` | string set of `SLOT=ACTION` | absent (= derived from the legacy key, normally the defaults) | app-wide | `remoteMappings` (sorted array) |
| `remote_channel_key_mode` (legacy) | `DPAD_AND_CHANNEL_KEYS` / `CHANNEL_KEYS_ONLY` | `DPAD_AND_CHANNEL_KEYS` | app-wide | `remoteChannelKeyMode` (required) |

In memory only: the resolver's pending button and held flag (one per player session).

## 7. External interfaces

- Android key events as delivered to the focused view: action down/up and repeat count; the
  key codes of REMOTE-FR-01. No other input API.
- Backup JSON fields of REMOTE-FR-35.

## 8. Edge cases and limits

- Remotes without auto-repeat: no holds; the functions on holds (zap-back, guide at channel,
  quick actions) remain reachable from the live action row and quick actions.
- Launchers or remotes that consume a long Back: Back hold never arrives; Back press still works.
- Back hold mapped to Nothing: a held Back neither acts nor leaves (the release is swallowed);
  a short Back still works. The rebuild treats Nothing on Back hold as "not mapped" so a held
  Back behaves as a press (Q-06).
- Back hold on live with no previous channel yet: nothing happens, and the release is swallowed.
- Two keys at once: the second abandons the first.
- A key that went down on another screen and is released in the player: release with nothing
  pending, ignored.
- Every slot mapped to Nothing: presses show the chrome; holds do nothing; Back still leaves.
- A mapping restored from a newer build with unknown actions: those slots read as Nothing.
- Up/Down press mappings are never consulted in catch-up and VOD (REMOTE-FR-22): they always go
  to the controls there. Up/Down holds still are.

## 9. Lightweight by design

- The resolver is a few fields and a `when`; no allocation per key event (gestures can be two
  preallocated objects per button or an int code). No coroutine, timer or frame callback is
  involved: timing comes from Android's own key repeats.
- The mapping is decoded once when preferences change and kept as an array indexed by slot
  ordinal (24 entries), not a map looked up by data-class keys on every event.
- The key handler runs on the main thread in well under a millisecond and never reads the
  database; actions that need data (neighbour channels, previous channel) use values the
  player already holds ([Player](30-player.md) §9).
- Settings: the grid is 12 rows × 2 compact buttons and the action list 25 buttons — small,
  non-lazy content in one settings group; nothing is composed for the list until a cell is
  chosen. No animation beyond the buttons' focus scale.
- Keeping this logic in its own small classes (resolver, mapping, dispatcher) out of the player
  composable is part of keeping every composable under the method-size and parameter rules
  ([Player](30-player.md) §9).

## 10. Lessons from the current app

- Before mapping, only "OK held" detected a hold (acting on the first repeat) and every press
  acted on key-down; with holds on every button, presses must wait for the release
  (`docs/REMOTE_BUTTON_MAPPING_PLAN.md`).
- Once presses fired on release, the chrome revealed on key-down flashed before the channel list
  opened, and Up/Down stepped into the box that key-down had just opened: reveal on release, and
  judge "box open" as of the key-down (commits 9026371, 289bfd0).
- A remote must always keep one way out: Back press is fixed (decision with the owner,
  4 September 2026).
- Forward compatibility: unknown entries are dropped rather than failing, and "never written"
  differs from "written empty".
- The plan asked for inapplicable actions to be greyed out; the build shows a scope suffix and
  lets them be chosen, then falls back at run time. Keep the fallback; the suffix is enough.
- `SCORE_TICKER` was added later (commit 2a3d55a) by appending to the enum: new actions must be
  appended, never reordered, since names are what is stored.
- No device test drives the player with real key events; the resolver is unit-tested and the
  dispatch was reviewed only (`REMOTE_BUTTON_MAPPING_PLAN.md`, "Not done"). The rebuild adds
  them (§11).
- Found while writing this spec (code reading, to confirm): the resolver is recreated per channel,
  so a hold that zaps may keep zapping or end with a press on the new channel (Q-02); a hold that
  opens quick actions may leave its release to the newly focused first row (Q-03); focus return
  to the Up / Press cell asks an unattached requester (Q-01); seek action labels say "10 s"
  whatever the step (REMOTE-FR-12).

### Open questions

- Q-01 After editing the Up / Press cell, where does focus land in beta 23 (or does it throw)?
- Q-02 Holding Up (default Next channel) on live: one zap, or repeated zaps as the per-channel
  resolver restarts, possibly ending with the channel list opening? Decide the intended
  behaviour (recommended: one zap per hold; or explicit repeat every N ms for CH keys only).
- Q-03 Holding OK: does the release activate the first quick-actions row in beta 23?
- Q-04 CH+ = previous (upward in the list) is kept from pre-mapping behaviour. Keep, or make
  CH+ go to the next higher channel number as most TV apps do?
- Q-05 Seek action labels with the chosen step ("Back 30 s")?
- Q-06 Back hold mapped to Nothing: keep "held Back does nothing" or treat as a normal Back?
- Q-07 Should timeshift Left/Right holds default to repeating skips ([Player](30-player.md)
  Q-14)? This would need a hold gesture that repeats, which the resolver does not have today.

## 11. Acceptance tests

Unit (JVM):
- Defaults: the table of REMOTE-FR-13 exactly (17 entries; the six optional holds Nothing).
- Zap-back on Back hold and Left hold; guide on Right hold.
- Back press fixed: not mappable, not in the mappable list, `with()` ignores it, decoding
  `BACK.PRESS=GO_HOME` yields nothing.
- Round trip encode/decode equals; Nothing never encoded.
- Unknown slots, actions and malformed entries dropped; empty set = all Nothing.
- Legacy: never stored + `CHANNEL_KEYS_ONLY` → Up/Down press Nothing, holds default; never stored
  + default mode → defaults; anything stored → legacy ignored.
- Scopes: channel actions live only; playback actions timeshift only; sound and picture
  everywhere.
- Resolver: tap = press on release; hold on first repeat, later repeats and release silent;
  Enter and centre are one OK; a second key abandons the first; release with nothing pending
  ignored; keys outside the grid ignored; reset forgets a pending key.
- Dispatcher (pure, with a fake player): live Left press → chrome; timeshift Up press → controls
  focused (mapping not consulted); Back with an inapplicable hold → not consumed at all; Back hold
  applicable → action, release consumed; digit in VOD → not consumed; unknown key → chrome on
  first down, not consumed.

UI (instrumentation):
- Grid shows the defaults; no Back / Press cell node; Menu / Press reads "Quick actions".
- Choosing Home for Up / Press changes only that cell; focus returns to it; reset restores
  "Channel list"; focus lands on "Reset to defaults" after Reset and after Cancel.
- Read-back line follows focus.
- Player driven by real key events (injected at the window, synthetic content): Up press opens
  the channel list; Up hold zaps exactly once and leaves no list open; OK hold opens quick
  actions and its release activates nothing; Back hold on live zaps back and stays in the
  player; Back hold in VOD leaves on release; Left press in VOD skips 10 s; CH+ in VOD shows the
  controls. Every such test is shown to fail on a deliberately broken dispatcher.

Performance:
- 100 key events through the dispatcher allocate nothing (allocation tracker) and each handles in
  under 1 ms on the low-end stand-in.

## 12. Reference: current code map

- `core/.../app/RemoteMapping.kt` — buttons, gestures, slots, actions with group and scope, mappings (defaults, encode/decode, legacy migration), key gesture resolver.
- `iptv/.../feature/player/PlayerScreen.kt` — `handleCleanScreenKey`, `performRemoteAction`, `overlayTakesPress`, `unmappedPress`, the channel-list key branch.
- `iptv/.../feature/settings/RemoteMappingSection.kt` — grid, action list, labels, reset.
- `iptv/.../feature/settings/SettingsScreen.kt` — the REMOTE section item and its preference calls.
- `core/.../app/AppPreferencesRepository.kt` — `remote_mappings`, `remote_channel_key_mode`, `setRemoteMapping`, `resetRemoteMappings`, restore.
- `app/.../app/StreamMateBackupManager.kt` — `remoteMappings`, `remoteChannelKeyMode` in backups.
- `app/.../app/StreamMateApp.kt` — zap-back history and the Home/Guide/Sport/guide-at-channel callbacks.
- `docs/REMOTE_BUTTON_MAPPING_PLAN.md` (private) — the agreed plan and decisions.
- Tests: `core/src/test/.../RemoteMappingTest.kt` (mapping and resolver), `app/src/androidTest/.../RemoteMappingSettingsTest.kt`.
