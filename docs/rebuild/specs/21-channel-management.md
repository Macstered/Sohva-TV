# Channel management

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

Channel management lets the household make a provider's channel line-up its own, one channel at
a time: give a channel a new name, move it to another group, give it its own logo (typed as an
address or sent as a picture from a phone), its own channel number, link it by hand to a channel
of the programme guide feed, hide it from the guide, lock it behind the parental PIN, move it up
or down, put it into the household's own channel lists, or reset it. The per-profile favourites
and recently watched channels, which are set from the guide and by watching, are part of the same
data. Every customisation is stored against the channel's stable id and applies wherever the
channel appears: guide, player, Home, Search, Sohva Sport stream matching and backups. Group-level
organisation (hiding or ordering whole groups, shortcuts, per-group sorting) is the Library
manager's job ([Library organisation](42-library-organization.md)); this spec covers the
channel-level screen and data.

## 2. Feature checklist

- CHAN-01 Channel management screen, opened from the guide's Options > "Edit" (Channels icon) and
  from the Library manager's Live room > "Advanced" (the Library manager itself is reached from
  Settings > Library > "Manage groups & content").
- CHAN-02 Header: "Channel management", subtitle, Back button.
- CHAN-03 Source filter button "Source: All" / "Source: <name>", cycling All → each source → All.
- CHAN-04 Sort button "Sort: Playlist" / "Sort: A–Z".
- CHAN-05 "Show hidden" toggle; the choice is kept and shared with the Library manager (default
  on).
- CHAN-06 Search field "Search channels or groups" (name or group contains the text).
- CHAN-07 Group chips: All and every group of the chosen source; the chosen chip is marked "●".
- CHAN-08 Create a custom channel list: name field and "Create list".
- CHAN-09 Channel list with a count ("N channels"): number, logo or initials, name, group (or
  source), "HIDDEN" marker.
- CHAN-10 Moving focus onto a channel opens it in the editor pane: logo, name, source, original
  name, status message.
- CHAN-11 Custom name (blank = the playlist's name).
- CHAN-12 Custom group (blank = the playlist's group); the channel moves to that group everywhere.
- CHAN-13 Own logo by address (blank = the playlist's logo).
- CHAN-14 Own logo from a phone: a QR page on the phone sends a picture, kept on the TV.
- CHAN-15 Own channel number (digits, blank = the playlist's), with the playlist's number shown
  beside it.
- CHAN-16 Programme guide mapping: Automatic (the playlist's `tvg-id`) or a chosen channel of the
  source's XMLTV feed.
- CHAN-17 Custom channel lists: pick a list, Add to list / Remove from list, Delete list.
- CHAN-18 Save stores name, group, logo, number and guide mapping together.
- CHAN-19 Hide from guide / Show in guide (immediate).
- CHAN-20 Lock with PIN / Remove PIN lock (immediate, per profile); without a parental PIN the
  button reads "Configure PIN in Settings" and is disabled.
- CHAN-21 Move up (↑) / Move down (↓), only while sorted by Playlist.
- CHAN-22 Reset removes the channel's customisation.
- CHAN-23 A status line confirms every action.
- CHAN-24 Favourite channels per profile, toggled from the guide's hero and programme actions;
  the guide rail's Favourites list.
- CHAN-25 Recently watched channels per profile (last 20, most recent first): the guide rail's
  Recently watched list and Home's recent channels row.
- CHAN-26 A locked channel asks for the PIN before live, catch-up and zapped playback and at a
  Last-channel start.
- CHAN-27 Customisations apply in the guide, the player's channel list and dial, Home, Search,
  Sohva Sport stream matching and the stored programme guide.
- CHAN-28 Custom lists appear on the guide rail (each can be switched off in the Library manager).
- CHAN-29 Backups carry channel customisations, lists, memberships, phone-sent logos (as picture
  bytes) and each profile's favourites, recents and (with a PIN) locks.
- CHAN-30 Removing a source removes its channels' customisations and list memberships.

## 3. Entry points and navigation

- From the guide: Options sheet > "Edit" (`guide_channels`, Channels icon) pushes Channel
  management over the guide.
- From the Library manager ("Manage groups & content", `manager_title`, in Settings > Library;
  or the guide's Sort / Edit buttons), Live room, "Advanced" (`manager_advanced`) pushes it over
  the Library manager.
- There is no other entry. The screen does not open on a particular channel.
- Back (through the window) leaves to the previous screen. The phone-logo dialog, when open,
  takes Back first and stops the phone page. Leaving the screen also stops the phone page
  (app-wide rule, [Phone setup](11-phone-setup.md)).
- Focus on entry: the first row of the channel list once the list has rows. On return from a
  pushed screen there is none (the screen pushes nothing but the phone dialog).
- The guide re-reads its rows when it becomes visible again, so changes show at once
  ([Live TV guide](20-live-tv-guide.md) GUIDE-FR-37).

## 4. Behaviour

### 4.1 Channel identity and what a customisation is attached to

- CHAN-FR-01 Every channel has a global id `<sourceId>:<localId>`. For M3U the local id is the
  SHA-256 of `<tvg-id>|<normalised name>` when the entry has a `tvg-id`, else of
  `<normalised name>|<stream URL>`; for Xtream it is `xtream-<stream_id>`
  ([Sources and import](10-sources-and-import.md)). Customisations, favourites, recents, locks,
  list memberships and reminders are keyed by this id and survive re-imports while it is stable.
- CHAN-FR-02 A channel's customisation is one row of `channel_preferences` (§6). A missing row
  means "as the playlist says". Values are trimmed; blank means none.
- CHAN-FR-03 Shown values used everywhere: name = custom name else provider name; group = custom
  group title else provider group; logo = custom logo else provider logo; number = custom number
  else provider number (`tvg-chno` / `channel-number` in M3U, `num` on Xtream, positive only)
  else none; guide id = manual XMLTV id else `tvg-id`.
- CHAN-FR-04 A custom group also sets the channel's organisation group key to
  `name:<lower-cased trimmed title>` so group rules and restricted profiles treat it as a member
  of the new group; a blank custom group returns the provider key.

### 4.2 The channel list

- CHAN-FR-10 The screen lists every channel of every source's active playlist, disabled sources
  included, joined with its preference row.
- CHAN-FR-11 Sources for the Source button: distinct sources in list order (priority descending,
  then name). The button cycles `All → source 1 → … → last source → All` (it does not wrap from
  the last source to the first) and resets the group to All.
- CHAN-FR-12 Groups for the chips: the distinct shown group titles of the chosen source (All =
  every source) in list order; channels without a group appear only under All. A chosen group that
  disappears resets to All.
- CHAN-FR-13 Filters, all combined: the chosen source; hidden channels only when Show hidden is
  on; the chosen group (shown group title equals); the search text (max 100 characters) contained,
  case-insensitively, in the shown name or the shown group title.
- CHAN-FR-14 Order: Playlist = channel manager position (`sortOrder`, channels without one
  last), then provider order, then shown name (lower-cased); A–Z = shown name (lower-cased). With
  Source = All, Playlist order interleaves sources by position and provider order (current
  behaviour; see open questions).
- CHAN-FR-15 The count above the list is the filtered count: "%1$d channel" / "%1$d channels"
  (`channels_count` plural).
- CHAN-FR-16 Show hidden (`channels_show_hidden`) is a toggle button showing its state as
  selected; it writes `editors_show_hidden` (default true), which the Library manager also uses.
- CHAN-FR-17 The selected channel is the focused row; when the selection is absent or filtered
  out, the first row is selected. When the first row of the filtered list changes (any filter or
  sort change) focus is requested on the first row. Rebuild rule: never pull focus away from the
  search or list-name field while the viewer is typing; move it when editing completes (IME Done
  or leaving the field).
- CHAN-FR-18 Moving focus onto a row selects it and clears the status line. OK on a row does the
  same (there is no other row action).

### 4.3 Editing a channel

The editor pane shows the selected channel. Field values reset whenever another channel is
selected or the stored value changes.

- CHAN-FR-20 Custom name: text field "Custom name (blank = original)" (`channels_custom_name`),
  cut to 100 characters.
- CHAN-FR-21 Custom group: "Custom group (blank = original)" (`channels_custom_group`), cut to
  100 characters.
- CHAN-FR-22 Logo address: "Logo address (blank = playlist's)" (`channels_logo_url`), cut to
  500 characters. Any text is stored; the image loader decides whether it loads (a failing logo
  shows initials in the rebuild).
- CHAN-FR-23 Channel number: "Channel number (blank = playlist's)" (`channels_number`), digits
  only, at most 5; stored only when > 0. Below it: "Playlist number: %1$d"
  (`channels_playlist_number`) or "The playlist gives no number"
  (`channels_playlist_number_none`). An own number can repeat another channel's number; dialling
  then takes the first in list order.
- CHAN-FR-24 Programme guide mapping (`channels_epg_mapping`): "Current: %1$s"
  (`channels_current`) with the mapped XMLTV channel's display name (else its id), or "Automatic
  (%1$s)" (`channels_automatic_epg`) with the channel's `tvg-id` or "no tvg-id"
  (`channels_no_tvg_id`). "Change EPG channel" (`channels_change_epg`) steps through the XMLTV
  channels of the channel's source (active EPG snapshot, ordered by display name, else id): from
  Automatic to the first, then each next, and after the last back to Automatic. "Automatic"
  (`channels_automatic`) clears the mapping. The change is kept only when saved.
- CHAN-FR-25 Save (`action_save`, Check icon) writes name, group, guide mapping, logo address and
  number in one step, keeping hidden and position, with `updatedAtEpochMillis = now`. Status:
  "Changes saved" (`channels_saved`).
- CHAN-FR-26 Hide from guide (`channels_hide_from_guide`) / Show in guide
  (`channels_show_in_guide`) writes the hidden flag at once with the channel's stored values
  (unsaved field edits stay in the fields). Status: "Channel hidden from the guide"
  (`channels_hidden`) / "Channel restored to the guide" (`channels_shown`).
- CHAN-FR-27 Effect of hidden: the channel is left out of every visible channel set (guide lists,
  player channel lists, Home, Search, sport matching) unless an organisation rule naming the
  channel itself says shown or hidden, which then wins ([Library
  organisation](42-library-organization.md)); it is not counted on the guide rail; in Channel
  management it is listed (with Show hidden on) with "  ·  HIDDEN" (`channels_hidden_suffix`) in
  the `accent` colour and its name in `textMuted`.
- CHAN-FR-28 Lock: "Lock with PIN" (`channels_lock_with_pin`) / "Remove PIN lock"
  (`channels_remove_pin_lock`) adds or removes the channel in the active profile's locked set at
  once. Status "Channel locked with PIN" (`channels_locked`) / "Channel PIN lock removed"
  (`channels_unlocked`). Without a parental PIN the button reads "Configure PIN in Settings"
  (`channels_configure_pin`) and is disabled. Removing the parental PIN clears every profile's
  locked set ([Profiles and parental](04-profiles-parental.md)).
- CHAN-FR-29 Move up "↑" / Move down "↓": enabled only with Sort = Playlist. The channel moves to
  just before (up) or just after (down) the channel next to it **on screen**; channels that the
  current filters hide and that lie between them keep their relative order (an old version swapped
  with a hidden neighbour and showed no change). Status "Channel order updated"
  (`channels_order_updated`). At the top or bottom of the shown list nothing happens.
- CHAN-FR-30 Effect of a position: in the guide a group whose channels have positions is ordered
  by them (Manual) unless the Library manager set a sort for that group or the whole room.
- CHAN-FR-31 Reset (`action_reset`) deletes the channel's preference row: name, group, logo,
  number, guide mapping, hidden and position return to the playlist's. Favourites, recents, locks
  and list memberships are untouched. Status "Custom channel settings removed"
  (`channels_reset_done`). Rebuild: also delete a phone-sent logo file of that channel (the old
  app leaves it behind).
- CHAN-FR-32 Status line: 12 sp in `focus` at the end of the editor's header row; cleared when a
  list row gains focus.

### 4.4 Own logo from a phone

- CHAN-FR-40 "Logo from phone" (`channels_logo_from_phone`) starts the phone page in logo mode
  for the selected channel (its id and shown name) and shows the phone dialog titled "Logo for
  %1$s" (`phone_setup_page_logo_title`) with the QR code and address, or its no-network state
  (server, token and dialog in [Phone setup](11-phone-setup.md)).
- CHAN-FR-41 The phone page offers one picture input ("Choose a picture",
  `phone_setup_page_logo_choose`; help `phone_setup_page_logo_help`). The phone shrinks the
  picture on a canvas so its longest side is at most **512 px** and posts it as a PNG data URL;
  while sending it shows "Sending to the TV…"; a file the phone cannot read shows "That file is not
  a picture this phone can read." (`phone_setup_page_logo_invalid`).
- CHAN-FR-42 The TV accepts the post only in logo mode, only for the channel the page was opened
  for, with a body of at most 1,500,000 bytes. The picture must be 1..2,000,000 bytes and decode
  as an image; it is decoded with a power-of-two sample size while its longest side is more than
  twice 256 px, scaled so the longest side is at most **256 px**, and written as a PNG (quality
  100) to app storage `files/channel-logos/<prefix>-<millis>.png`, where `<prefix>` is the first 8
  bytes of SHA-256(channel id) in hex; earlier files of the channel are deleted first, and the
  name never repeats an earlier one (image caches key on the address). The `file:` address is
  stored as the channel's custom logo, creating a preference row when the channel has none.
- CHAN-FR-43 On success the phone shows "Logo saved on the TV for %1$s. You can close this
  page." (`phone_setup_page_logo_saved`); the TV shows "Logo received from the phone"
  (`channels_logo_received`) and stops the phone page. A refused picture leaves the old logo.
- CHAN-FR-44 The logo field then holds the `file:` address (the private path is visible in the
  field, and saving keeps it). Whether the rebuild shows a short label there instead is an open
  question (it would be a new string in seven languages).

### 4.5 Custom channel lists

- CHAN-FR-50 Create: the field "New channel list name" (`channels_new_list_hint`, cut to 100) and
  "Create list" (`channels_create_list`). A blank name does nothing; otherwise a list with a new
  UUID, the trimmed name (max 100) and position = number of existing lists is created, the field
  clears, status "Channel list created" (`channels_list_created`).
- CHAN-FR-51 In the editor, "Custom channel lists" (`channels_custom_lists`): with no list, the
  help "Create a custom channel list at the top first." (`channels_create_list_help`); otherwise
  "List: %1$s" (`channels_list`) cycles through the lists (lists ordered by position, then name),
  "Add to list" (`channels_add_to_list`) / "Remove from list" (`channels_remove_from_list`) for
  the selected channel, and "Delete list" (`channels_delete_list`).
- CHAN-FR-52 The list shown when a channel is opened is the first list. Rebuild fix: cycling past
  the last list returns to the first, and every newly selected channel starts on the list last
  shown (in the old code, by reading, cycling past the last list or selecting another channel
  leaves no list chosen and shows the create help although lists exist — not confirmed on a
  device).
- CHAN-FR-53 Add appends the channel at the end of the list (position = current member count);
  status "Channel added to the list" (`channels_added_to_list`). Remove deletes the membership;
  status "Channel removed from the list" (`channels_removed_from_list`). A channel can be in many
  lists and from any source.
- CHAN-FR-54 Delete list removes the list and all its memberships at once, without confirmation;
  status "Channel list deleted" (`channels_list_deleted`).
- CHAN-FR-55 In the guide, each list is a rail entry named after the list; its rows are the
  members of the selected source in the list's order ([Live TV guide](20-live-tv-guide.md)
  GUIDE-FR-35). A list's rail entry can be switched off and placed by hand in the Library manager
  (`@list:<id>` rules).
- CHAN-FR-56 Limits enforced on restore: at most 1,000 lists, 500,000 memberships, 100,000
  channel preference rows; no duplicate list ids or preference channel ids; every membership must
  name a list in the backup.

### 4.6 Favourites and recently watched (per profile)

- CHAN-FR-60 Favourites: a set of channel ids per profile (`favourite_channel_ids`, suffixed
  `:<profileId>` for profiles other than the default). Toggled by the guide's hero "Favourite /
  Add favourite" and the programme actions dialog ([Live TV guide](20-live-tv-guide.md)). No
  limit. Ids of channels that no longer exist stay in the set and are ignored.
- CHAN-FR-61 Recently watched: a list of channel ids per profile (`recent_channel_ids`, same
  suffix rule), joined with U+001F, **most recent first, at most 20**, duplicates removed. A
  channel is recorded — and becomes the profile's `last_channel_id` — when live playback starts
  from the guide, a Home channel card or Search, when the player changes channel (up/down, its
  channel list, a dialled number, the previous channel), when catch-up starts, and when a PIN
  unlock leads to such playback. Playback started from Sohva Sport, a reminder or a notification
  is not recorded.
- CHAN-FR-62 The guide's Favourites and Recently watched rail entries exist only while their
  shortcuts (`@favourites`, `@recent`) are enabled in the Library manager.
- CHAN-FR-63 Removing a profile removes its favourites, recents, last channel and locks.

### 4.7 Locked channels (per profile)

- CHAN-FR-70 A locked set exists per profile (`locked_channel_ids`, suffix rule) and only while a
  parental PIN is configured.
- CHAN-FR-71 Playing a locked channel live, as catch-up, by zapping, or at a Last-channel start
  pushes the PIN screen first; the right PIN continues to the playback, recording the recent
  channel when the playback would have ([App shell](01-app-shell-navigation.md) SHELL-24,
  [Profiles and parental](04-profiles-parental.md)).
- CHAN-FR-72 Locks are only in backups written while a PIN existed, and are restored only when
  the backup carries a PIN.

## 5. Screen anatomy

No current screenshot exists. Values below are from `ChannelEditorScreen.kt`; where the old code
uses literal sizes or shapes they are mapped to the nearest token and the literal is kept in
brackets.

- Frame: the shared screen background with the standard content inset (40 dp horizontal, 24 dp
  vertical) ([design/02-components.md](../design/02-components.md)); the Sohva TV brand mark at
  the top (component `SohvaTvBrand`).
- Header row (space between, centred vertically): title "Channel management"
  (`channels_title`) 32 sp Black; subtitle "Rename, regroup, reorder, hide and link channels to
  the programme guide" (`channels_subtitle`) 13 sp `textMuted`; right: "Back" button
  (`action_back`, Back icon).
- 10 dp; toolbar row (spacedBy 8, centred): Source button (`channels_source` "Source: %1$s",
  All = `guide_filter_all` "All"), Sort button (`channels_sort` "Sort: %1$s" with "Playlist" /
  "A–Z"), Show hidden (selected when on), search field (weight 1, "⌕" leading glyph, text
  keyboard, edit on OK only, label `channels_search_hint`).
- 7 dp; group chips row (horizontal scroll, spacedBy 7): compact buttons "● All" / "All", then
  one per group ("● <group>" when chosen).
- 7 dp; list row (spacedBy 8): field (weight 1, "+" glyph, label `channels_new_list_hint`, edit on
  OK only) and "Create list".
- 10 dp; content row (fill, spacedBy 12):
  - **Channel list pane**, 350 dp wide: `surface`, radius 12 dp (`shapes.medium`), 1 dp border
    `outline` α0.56, padding 8; count 12 sp `textMuted`; 6 dp; a lazy list (spacedBy 4) of rows:
    54 dp high, radius 8 dp (`shapes.small`), fill `surfaceRaised` when selected else
    `surfaceSubtle`, focused = 3 dp `textPrimary` border (no scale, no shadow), padding 8 × 5;
    number (12 sp Bold `textMuted`, min width 22 dp) + 6 dp when the channel has a shown number;
    logo tile 38 dp (`surfaceRaised`, radius 7 dp; image padding 4 dp; initials = first two
    characters upper-cased, Black, `focus` colour) + 8 dp; name 12 sp Bold, 1 line, ellipsis
    (`textMuted` when hidden); line 2: shown group (else source name) plus "  ·  HIDDEN", 12 sp,
    `accent` when hidden else `textMuted`.
  - **Editor pane**, the rest: vertical scroll, `surface`, radius 12 dp, 1 dp border `outline`
    α0.72, padding 16. Without a channel: "Select a channel to edit" (`channels_select`) centred,
    `textMuted`.
    - Header row: logo tile 64 dp; 12 dp; column: shown name 22 sp Black, source name 12 sp
      `focus`, "Original name: %1$s" (`channels_original_name`) 12 sp `textMuted`; status at the
      end, 12 sp `focus`.
    - 12 dp; custom name; 8; custom group; 8; logo address; 8; number field; playlist-number note
      12 sp `textMuted`; 6 dp; "Logo from phone".
    - 10 dp; "Programme guide mapping" 12 sp Bold; "Current: …" 12 sp `textMuted`, 1 line; 6 dp;
      "Change EPG channel", "Automatic" (spacedBy 7).
    - 10 dp; "Custom channel lists" 12 sp Bold; help text or the three list buttons (spacedBy 7).
    - 12 dp; action row (spacedBy 7): Save (Check icon), Hide/Show, Lock, "↑", "↓", Reset.
  At 960 × 540 dp the action row is below the fold; the pane scrolls to the focused control.
- Buttons are the standard `TvActionButton` (non-compact except the chips); fields are the
  standard text field ([design/02-components.md](../design/02-components.md)).
- Focus order: toolbar → chips → list-name row → channel list → editor (left/right between the
  panes by geometric search). Initial focus: the first channel row.

## 6. Data

| Store | Content | Per profile | In backup |
|---|---|---|---|
| `channel_preferences` (key `channelId`; index `sourceId`) | `sourceId`, `customName`, `customGroupTitle`, `customOrganizationGroupKey`, `hidden`, `sortOrder`, `manualXmltvChannelId`, `customLogoUrl`, `channelNumber`, `updatedAtEpochMillis` | no | yes; a `file:` logo of this device is added as `customLogoData` (Base64 PNG) and written back as a file on restore; a `file:` logo from another device without data is dropped |
| `channel_lists` (key `listId`) | `name`, `sortOrder`, `updatedAtEpochMillis` | no | yes |
| `channel_list_members` (key `listId, channelId`; index `channelId`) | `sortOrder` | no | yes |
| `files/channel-logos/*.png` | phone-sent logos, ≤ 256 px | no | as bytes (above) |
| `favourite_channel_ids[:profile]` | string set | yes | yes |
| `recent_channel_ids[:profile]` | ≤ 20 ids, U+001F-joined | yes | yes (≤ 20) |
| `last_channel_id[:profile]` | id | yes | yes |
| `locked_channel_ids[:profile]` | string set; only with a PIN | yes | only with a PIN |
| `editors_show_hidden` | boolean, default true | no | yes |

- Deleting a source removes its preference rows, list memberships of its channels and its sport
  decisions (the logo files stay; rebuild: delete them too).
- Restoring a backup replaces all preference rows, lists and memberships in one transaction.
- The organisation rules that decide group visibility, shortcuts and group sorting are in
  [Library organisation](42-library-organization.md); schema in
  [plan/04-data-model.md](../plan/04-data-model.md).

## 7. External interfaces

- Phone logo page: HTTP form post `type=logo`, `channel=<id>`, `image=<PNG data URL>` to the
  local phone setup server, token-protected ([Phone setup](11-phone-setup.md)).
- Logo addresses (http, https or `file:` of the logo store) are loaded by the image loader
  (cleartext http is allowed only for IPTV sources; [Security and
  privacy](73-security-privacy.md)).
- No other network use.

## 8. Edge cases and limits

- **Unstable provider ids.** An M3U channel whose normalised name changes (and that has no
  `tvg-id`, or whose `tvg-id` changes) gets a new id at the next import: its customisation,
  favourite, recent entry, lock, list memberships and reminders no longer apply. Nothing tells
  the viewer.
- **Manual guide mapping and stored programmes.** The importer stores only programmes of XMLTV
  channels referenced by a channel (`tvg-id` or manual mapping) at import time. A new mapping to
  a channel that nothing referenced shows no programmes until the next guide refresh (see open
  questions).
- **Hidden vs organisation rules.** When the Library manager has a rule naming the channel
  itself, Hide/Show in Channel management changes the stored flag but not what the guide shows.
- **Duplicate numbers** are allowed; dialling picks the first in list order.
- **Numbers above 9,999** can be stored (5 digits) but not dialled (the dial takes 4 digits).
- **Logos:** a picture over 2,000,000 bytes after the phone's shrink, or one that does not
  decode, is refused; an enormous photo is bounded to 256 px, so a stored logo is at most
  256 × 256 × 4 bytes decoded.
- **Disabled sources** are listed and editable; their channels appear nowhere else.
- **Delete list** has no confirmation and no undo.
- **Show hidden off** removes a channel from the list the moment it is hidden (focus then moves
  to the first row).
- **Large libraries:** see §9; at 56,000 channels the old screen is slow to open and slow on
  every key press.

## 9. Lightweight by design

The old screen is the heaviest per-key-press screen in the app at owner scale:

- It observes `observeEditableChannels()`: **every channel of every source**, 18 columns, sorted
  by computed values no index serves, in one Room statement. At 56,000 channels that is the same
  shape as the guide's old roster read (twenty 2 MiB CursorWindow refills, each re-running the
  statement; 57 CPU-seconds on the Shield) and it is re-run on every write to the channel or
  preference tables. The ledger records it as not fixed ("Large-playlist guide: cause and fix").
- It holds all rows as objects, and on **every recomposition** — which every focus move causes,
  because the selected id is read in the screen body — it filters, de-duplicates groups and sorts
  the whole list on the main thread, and builds a list of every filtered id for an effect key.
- One ↑/↓ press rewrites a preference row for **every channel of every source** (it assigns
  `sortOrder = index` over the whole library).
- It observes every custom-list membership of every list, and cycles guide mappings through all
  XMLTV channels of a source one button press at a time (tens of thousands on big feeds).

Rebuild requirements:

- CHAN-NFR-01 Paged list: rows are read in keyset pages of ≤ 200 along the materialised display
  position of the source (or `(sourceId, channelId)` for A–Z, with a name-sort key stored at
  import), with source, group, hidden and search filters in SQL; at most 3 pages resident. The
  count comes from a `COUNT(*)` with the same filters; groups for the chips from the guide rail's
  aggregate read. No query returns every channel.
- CHAN-NFR-02 Search is debounced (250 ms) and runs in SQL (`LIKE` with escapes on the shown name
  and group, or the search index of [Search](03-search.md)); never a scan in Kotlin.
- CHAN-NFR-03 The screen body never reads the selection; a focus move recomposes the two rows
  and the editor pane only. No filtering, sorting or list building on the main thread.
- CHAN-NFR-04 A move writes at most the moved channel's row once positions exist in its source.
  The first move in a source with no positions assigns gapped positions (for example steps of
  1,024 in the current order) to that source only, in pages of ≤ 2,000 rows in background
  transactions; renumbering when gaps run out is paged the same way. Record the scheme in
  `docs/decisions.md`.
- CHAN-NFR-05 List memberships are read for the selected channel and the lists' counts only.
- CHAN-NFR-06 Guide mapping uses a searchable, paged picker of the source's XMLTV channels
  (≤ 200 per page), still offering Automatic and the current value first. (The old cycling button
  stays as the picker's quick step if the owner wants it; see open questions.)
- CHAN-NFR-07 Writes (save, hide, lock, lists) run off the main thread; a write re-reads only the
  visible page (conflated), and the guide re-reads when it is next visible.
- CHAN-NFR-08 Logos in the list are decoded at 38 dp (76 px), the editor's at 64 dp; phone logos
  are already ≤ 256 px. At most two decodes at a time.
- CHAN-NFR-09 Budgets: opening the screen on a 56,000-channel library shows the first rows within
  the plan/07 screen-open budget on the stand-in; a D-pad press in the list meets the key-press
  budget; Java heap stays within the steady budget (64 MB) while scrolling the whole library.
- CHAN-NFR-10 The phone page runs only while its dialog is open and stops on success, on close
  and when the screen is left.

## 10. Lessons from the current app

- The channel manager's unpaged, sorted, whole-library read is the same CursorWindow trap that
  cost the guide 57 s; it was left unfixed at beta 23 (ledger, "Large-playlist guide: cause and
  fix": "The same cursor behaviour remains in … the channel manager's `observeEditableChannels`").
- Reading the selection in the screen body made every D-pad press re-run whole-list work (the
  guide learnt the same lesson in beta 22, ledger "What a key press costs").
- A move that "swapped with a hidden neighbour" showed no change; moves are relative to what is
  on screen (code comment in `ChannelEditorScreen.onMove`).
- A logo address must change when the picture changes, or image caches keep showing the old
  picture (`ChannelLogoStore.save`).
- A phone-sent logo lives only on this TV: a backup must carry its bytes, and a restore on
  another TV must not keep a `file:` address that never loads (`StreamMateBackupManager`,
  `ChannelLogoStore.existingOrNull`).
- Files written for a channel must be deleted with the customisation (the old app never deletes
  logo files on Reset, on clearing a logo, or when a source is removed).
- `SettingsCustomGroups.kt` is not about channels: it edits the film and series genre groups
  ([Library organisation](42-library-organization.md)). The channel-level "custom groups" are the
  custom group title (CHAN-FR-21) and the custom channel lists (§4.5).

### Open questions

1. With Source = All, Playlist order interleaves channels of different sources by position and
   provider order. Should it group by source first (as the guide does)?
2. Should "Delete list" ask for confirmation?
3. A new manual guide mapping shows programmes only after the next guide refresh. Should saving a
   mapping start a guide refresh of that source, or should the importer keep all programmes of
   channels that could be mapped?
4. Keep the one-step "Change EPG channel" button next to a searchable picker, or replace it?
5. Should a channel's customisation follow it when the provider renames it (match by `tvg-id`
   alone when present)? Today it does not.
6. After a phone logo arrives the logo field shows a private `file:` path. Show a label such as
   "Logo from phone" instead (new string)?

## 11. Acceptance tests

Unit (JVM):
- Shown-value rules (CHAN-FR-03/-04): custom beats provider, blank means none, group key follows
  the custom group.
- Move rule (CHAN-FR-29): moving past filtered-out channels lands just before/after the visible
  neighbour and keeps the hidden ones' order; no-op at the ends; disabled in A–Z.
- Logo store: a saved logo is a file of its own; a second save replaces it under a new name; a
  foreign `file:` address is dropped on restore; bytes that are not a picture are refused; a
  4000 × 3000 picture is stored at 256 × 192 (old `ChannelLogoStoreTest` plus the size case).
- Recents: most recent first, no duplicates, cut at 20; per profile keys.
- Restore validation limits (CHAN-FR-56).

Instrumentation (emulator):
- Opening the screen focuses the first channel; the name field, sort, group chip and the EPG
  mapping controls are present (scroll to them); creating a list shows the list controls; hiding
  a channel persists `hidden = true` (old `channelManagementPersistsHiddenPreference`).
- Saving a logo address and number persists both and the list row shows the number (old
  `channelManagementSavesALogoAddressAndANumber`).
- Show hidden off removes a hidden channel from the list and back on restores it; the choice is
  kept (old `channelManagementCanKeepHiddenChannelsOutOfTheList`; clean up the preference in
  `finally`).
- Switching channel keeps a list selected (CHAN-FR-52 fix; must fail on the old behaviour).
- Lock needs a PIN: without one the button is disabled; with one, locking makes guide playback
  ask for the PIN.
- A phone logo post for another channel id is refused; one for the open channel is stored and the
  guide shows it.
- Removing a source removes its preferences and list memberships.

Performance (low-end stand-in, owner-scale fixture of 56,164 channels):
- Open Channel management, scroll from the first to the last channel, search, change source and
  group, move a channel: no CursorWindow warning, no main-thread work over the key-press budget,
  heap within the steady budget, and a move writes ≤ 2,000 rows per transaction.

## 12. Reference: current code map

- `iptv/.../feature/settings/ChannelEditorScreen.kt` — the whole screen: toolbar, chips, list,
  editor, actions.
- `app/.../app/ChannelLogoStore.kt` — phone logo files: bound, write, read for backup, restore
  check.
- `app/.../app/PhoneSetupServer.kt` (logo mode) and `StreamMateContainer.kt` (hands a posted logo
  to the store and the repository).
- `iptv/.../iptv/repository/GuideStore.kt` (`GuideRepository`) — `observeEditableChannels`,
  `observeXmlTvChannelOptions`, `updateChannel`, `setChannelLogo`, `reorderChannels`,
  `resetChannel`, list create/delete/membership, customisation snapshot and restore limits.
- `core/.../database/GuideDao.kt` — editable-channel read, XMLTV options, preference and list
  queries, `clearSource`.
- `core/.../database/GuideEntities.kt` — `ChannelPreferenceEntity`, `CustomChannelListEntity`,
  `CustomChannelListMemberEntity`, `IptvChannelEntity`.
- `core/.../app/AppPreferencesRepository.kt` — favourites, recents, last channel, locks,
  `editors_show_hidden`.
- `app/.../app/StreamMateApp.kt` — entry points, `playChannel` / `playCatchup` / `zapToChannel`
  (recents and locks).
- `app/.../app/StreamMateBackupManager.kt` — customisation and logo bytes in backups.
- `iptv/.../feature/settings/SettingsCustomGroups.kt` — film/series genre groups (not channels).
