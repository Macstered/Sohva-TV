# Sources and import

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

Sohva TV ships no channels: the viewer brings IPTV **sources**. A source is either an **M3U
playlist** with an optional **XMLTV** programme guide address, or an **Xtream Codes** account
(server, username, password). Any number can be added; each has a name, can be switched off
without being deleted, imports live TV, films and series, or both, has a playback connection
limit and an EPG time correction. **Settings › Playlists** lists the sources and opens each on a
page of its own, where the viewer tests the address or the account, saves it encrypted, and
starts a full sync or a separate refresh of channels, guide, or films and series. Every import
streams the provider's data into a staged database snapshot that replaces the previous data in
one step, and only when the new data is usable; a failed or empty refresh keeps what was there and
says why in plain language, in the interface language. Background jobs refresh playlists and guides
on the chosen interval and films and series daily. At the owner's scale (56,164 channels in 800
groups, 165,600 programmes, 30,000–200,000 films, thousands of series) the rebuild must import in
bounded memory, at background priority, without a key press or a playing video noticing.

## 2. Feature checklist

Source model and list
- SRC-01 Any number of sources (up to 100), each an M3U playlist (+ optional XMLTV guide) or an Xtream Codes account.
- SRC-02 Settings › Playlists lists every source: name, "M3U"/"Xtream", the last failure reason when a refresh failed, and "In use"/"Off".
- SRC-03 Empty list message "No playlists yet. Add one below."
- SRC-04 "+ Add M3U source" and "+ Add Xtream source" open a blank source page with a default name ("IPTV n" / "Xtream n").
- SRC-05 "Set up from a phone" button on the list (flow in [Phone setup](11-phone-setup.md)).
- SRC-06 A source page per source, left with "All playlists" or Back; focus returns to that source's row.

Source page
- SRC-07 M3U playlist address field and, when live TV is imported, an optional XMLTV guide address field.
- SRC-08 Xtream server field with username and masked password side by side.
- SRC-09 Source name (1–100 characters).
- SRC-10 "Source in use" switch: a switched-off source disappears everywhere and is not refreshed, but keeps its data.
- SRC-11 Connection limit 1–16 (default 1); playback beyond it is refused with a message naming the source.
- SRC-12 Content to import: Live TV / VOD only / TV and VOD (default TV and VOD).
- SRC-13 EPG time correction from −12 h to +12 h in 30-minute steps (live-TV sources only).
- SRC-14 Remove source: deletes the source, its credentials and every row that came from it.
- SRC-15 Save securely: the configuration is stored encrypted on the device.
- SRC-16 A newly saved source is synced in the background straight away (channels, guide, films and series).
- SRC-17 Test address (M3U): reads the start of the playlist and says how many entries it found, or why it could not.
- SRC-18 Test connection (Xtream): signs in and reports success with the server's connection limit, or why it failed.
- SRC-19 Sync everything: queues channels, then guide, then films and series for this source in the background.
- SRC-20 Refresh playlist (M3U) / Refresh channels (Xtream), with the imported channel count.
- SRC-21 Refresh movies and series, with the imported film and series counts.
- SRC-22 Refresh programme guide, with the imported programme count.
- SRC-23 Plain-language validation and failure messages in the interface language; provider detail kept but addresses and credentials redacted.
- SRC-24 Status line with per-kind health ("Playlist: 56,164 items", "Programme guide: updating", the failure reason), updating live while background work runs.
- SRC-25 Security note under the status: credentials are encrypted and backups password-protected.

Refresh and scheduling
- SRC-26 Playlist and EPG refresh interval in Settings › General: 1, 2, 4, 10 or 24 hours (default 24 hours).
- SRC-27 Automatic background refresh: playlists and guides on that interval, films and series every 24 hours, whenever a network is connected.
- SRC-28 Automatic refresh waits while the app is in use, except a source's very first import.
- SRC-29 One import of a kind per source at a time; a second request waits and then runs in full.
- SRC-30 An M3U address of the Xtream `get.php` form is imported through the Xtream API (channels, films, series, episodes; the guide too when no XMLTV address is given).

Import results the viewer sees
- SRC-31 A refresh never replaces working data with nothing: an empty playlist, a web page or error text instead of M3U, an empty guide, or a guide matching none of the channels keeps the previous data and says so.
- SRC-32 A failed refresh keeps the previous data; the failure reason shows in the status, the source row and the guide's empty state.
- SRC-33 The guide keeps only programmes of the source's own channels, from 12 hours back to 8 days ahead (rebuild: past kept only for catch-up channels, SRC-FR-83).
- SRC-34 M3U entries bring names, groups, logos, EPG ids, channel numbers, catch-up settings, per-channel user agent and referrer; live, film and series entries are told apart; series episodes are recognised from "S01E02"/"1x02" names.
- SRC-35 XMLTV feeds may be gzip-compressed, start with a byte-order mark, use any declared encoding or UTF-16; a malformed programme is skipped, not fatal.
- SRC-36 Xtream brings live channels with categories, EPG ids, logos, numbers and catch-up archive length; films and series with categories, poster, backdrop, year, rating and plot; episodes are fetched when a series is opened.
- SRC-37 Stream quality and language markers are read off channel names (4K, FHD, HDR, 50 FPS, FI…) for the guide, player and Sohva Sport.
- SRC-38 The EPG time correction shifts every programme of the source on screen, immediately after saving.
- SRC-39 Sources are part of the encrypted backup ([Backup](71-backup-restore.md)).

Rebuild additions (owner-adopted ideas, [OwnTV study](../reference/owntv-study.md) items 2–11, 22)
- SRC-40 Every import, from any screen or worker, goes through one import runner and survives leaving the screen that started it; the screen shows its progress.
- SRC-41 Xtream bulk lists that a provider truncates or refuses are fetched category by category instead.
- SRC-42 Importing never makes playback stutter or a D-pad press late on the low-end box.

## 3. Entry points and navigation

- **Settings › Playlists** (rail label `settings_section_sources` "Playlists", icon Channels) is
  the section Settings opens on ([Settings](70-settings.md)). A restricted profile with a parental
  PIN passes the PIN gate before Settings opens ([Profiles](04-profiles-parental.md)). Sources are
  global, not per profile.
- **Guide empty state**: lists each source with its last import result and offers a sync of all
  sources (sync-now without a source id, SRC-FR-99) and a way to Settings
  ([Live TV guide](20-live-tv-guide.md)).
- **Movies / Series options sheet**: a refresh action imports the catalogue of every enabled
  source whose scope includes VOD, one after another, in the foreground ([Movies and
  series](40-movies-and-series.md)); with no such source it answers `catalogue_add_xtream_first`
  "Add an M3U or Xtream source that imports VOD in Settings first"; on success
  `catalogue_imported` "Imported %1$s and %2$s" (film and series plurals).
- **Series page**: opening an Xtream series (or a `get.php` M3U series) fetches its episodes
  (SRC-FR-92). A switched-off source answers `catalogue_source_disabled` "The Xtream source is no
  longer enabled".
- **Phone setup** ([Phone setup](11-phone-setup.md)): a source typed on the phone is validated with
  the same rules (SRC-FR-20…23), saved, and synced (SRC-FR-99). The list's status then reads
  `phone_setup_received` "Received from the phone: %1$s. Syncing it now." (or
  `phone_setup_received_keys` "Keys received from the phone." when only keys came).
- **Backup restore** replaces the source list ([Backup](71-backup-restore.md)): sources missing from
  the backup are deleted with all their data (SRC-FR-41); restored sources are not synced
  automatically.

Focus and Back

| State | Focus on entry | Back |
|---|---|---|
| Playlists list | First source row; "+ Add M3U source" when there are none | Leaves Settings ([Settings](70-settings.md)) |
| Source page | "All playlists" (compact, Back icon) | Closes the page; focus to that source's row, or to the section's first row for a source that was never saved. Unsaved edits are discarded silently |
| After Remove source | beta 23: undefined (the page closes without a focus request) | — |
| Picker or dialog open | The picker's current value | Closes it; focus back to the row that opened it |

**Rebuild rule:** after Remove source, focus the next source row, else the previous one, else
"+ Add M3U source".

## 4. Behaviour

### 4.1 Source model

- **SRC-FR-01** A source has: `id`, `name`, `type` (M3U or XTREAM), `enabled` (default true),
  `connectionLimit` (default 1), `priority` (integer, default 0), `importScope` (default BOTH),
  `epgOffsetMinutes` (default 0), and by type `m3uUrl` + optional `xmlTvUrl`, or `xtreamBaseUrl`
  + `xtreamUsername` + `xtreamPassword`.
- **SRC-FR-02** Ids match `[A-Za-z0-9._-]{1,128}`. New ids are `m3u-<random UUID>` and
  `xtream-<random UUID>`; the type is fixed when the page is created and never changes. A source
  migrated from the single-source settings of the earliest builds has id `m3u-primary` and name
  `IPTV` (SRC-FR-107).
- **SRC-FR-03** The name is trimmed, non-blank and at most 100 characters.
- **SRC-FR-04** Import scope: LIVE_TV imports live channels and the guide; VOD imports films and
  series; BOTH imports all three.
- **SRC-FR-05** Connection limit 1–16. EPG offset −720…+720 minutes in steps of 30.
- **SRC-FR-06** Priority has no control in the interface; every source created in beta 23 has 0.
  Where it is used: the guide orders sources by priority descending then name; the refresh worker
  processes sources by priority descending (list order among equals). Keep the field for backups
  and migration.
- **SRC-FR-07** At most 100 sources; ids are unique. A source's text form (logs, crash reports)
  never contains addresses or credentials (`credentials=<redacted>`).

### 4.2 The Playlists list

- **SRC-FR-08** Overline `settings_overline_sources` "Sources" (drawn uppercase). One value row per
  source in stored order:
  - title: the source name (never its address or credentials);
  - subtitle: "M3U" or "Xtream"; when any refresh kind of the source last failed,
    " · " + that failure's resolved message (SRC-FR-105) of the first failed kind;
  - value: `source_row_on` "In use" or `source_row_off` "Off";
  - icon: Channels for M3U, Link for Xtream.
  OK opens the source page.
- **SRC-FR-09** No sources: a plain row `source_none` "No playlists yet. Add one below." (Info
  icon).
- **SRC-FR-10** A row of compact buttons: `source_add_m3u` "+ Add M3U source" (status becomes
  `source_new_m3u` "New M3U source"), `source_add_xtream` "+ Add Xtream source" (status
  `source_new_xtream` "New Xtream source"), and `phone_setup_start` "Set up from a phone" (Link
  icon; reads `phone_setup_stop` "Close the phone page" while the phone page runs).
- **SRC-FR-11** A new page's name defaults to `IPTV n` (M3U) or `Xtream n` (Xtream), n = number of
  saved sources of that type + 1. These defaults are not translated.
- **SRC-FR-12** The status group (SRC-FR-37) is shown under the list and under the source page.

### 4.3 The source page

Layout, sizes and colours: [Settings layout §6.2 "Playlists (SOURCES)"](../design/screens/settings.md).
Items are direct children of the pane, in this order:

- **SRC-FR-13** Header: overline `source_type_m3u_overline` "M3U · %1$s" or
  `source_type_xtream_overline` "Xtream · %1$s" with the name as typed (uppercased by the overline
  style), and the compact button `source_back_to_list` "All playlists" (first focus).
- **SRC-FR-14** M3U: label `source_m3u_address` "▤   M3U playlist address" (Bold 16 sp) and a URL
  field (placeholder `http(s)://provider/playlist.m3u`, Link icon, 82 % of the pane width). When the
  scope includes live TV: label `source_xmltv_address` "▤   XMLTV programme guide address
  (optional)" and a field (placeholder `http(s)://provider/epg.xml`). With scope VOD only the XMLTV
  field is hidden but its value is kept and still validated (SRC-FR-21).
- **SRC-FR-15** Xtream: label `source_xtream_server` "◆   Xtream server", a URL field (placeholder
  `http(s)://provider:port`), then a row of two equal fields: `source_username` "Username" (Key
  icon, text keyboard) and `source_password` "Password" (Key icon, password keyboard, masked).
- **SRC-FR-16** Rows, each a settings row with title, subtitle and trailing control:
  1. `source_name` "Source name" / `source_name_help` "What this source is called in the app":
     text field 320 dp.
  2. `source_enabled_title` "Source in use" / `source_enabled_help` "Include this source in the
     guide and library": switch.
  3. `source_connection_limit_title` "Connection limit" / `source_connection_limit_help`
     "Simultaneous playback connections to this source": compact "−" (disabled at 1), the text
     `source_connection_limit` "Connection limit %1$d" (body Bold), compact "+" (disabled at 16).
  4. Saved sources only: `source_delete_title` "Remove source" / `source_delete_help` "Deletes its
     channels, guide data and stored credentials": compact danger button `action_delete` "Delete"
     (Delete icon), disabled while an action runs.
  5. `source_import_scope` "Content to import" / `source_import_scope_help` "Limits what refreshes
     bring in from this source.": three compact buttons `source_import_live` "Live TV",
     `source_import_vod` "VOD only", `source_import_both` "TV and VOD"; the current one is drawn
     selected.
  6. When the scope includes live TV, a plain row: `source_epg_offset` "EPG time correction" (13 sp
     `textMuted`), compact "−30 min", the value (14 sp Bold, 74 dp wide), compact "+30 min",
     `source_epg_offset_help` "Shifts this source's guide times; applies after saving." (12 sp
     `textMuted`). The value reads `0 min`, `+1 h`, `−30 min`, `+1 h 30 min` (U+2212 minus; hours
     and minutes, never "90 min"). "−" is disabled at −12 h, "+" at +12 h. The "−30 min"/"+30 min"
     labels are hard-coded, not translated.
- **SRC-FR-17** Action buttons in a flow row (at most 3 per row, spacing 12 horizontal / 10
  vertical, full-size buttons), shown by this matrix:

| Button (EN) | Key | Icon | Shown when |
|---|---|---|---|
| Save securely | `source_save_securely` | Save | always |
| Test address | `source_test_address` | Check | M3U |
| Test connection | `source_test_connection` | Check | Xtream |
| Sync everything | `source_sync_everything` | Refresh | always |
| Refresh playlist | `source_refresh_playlist` | Refresh | M3U and scope includes live TV |
| Refresh channels | `source_refresh_channels` | Refresh | Xtream and scope includes live TV |
| Refresh movies and series | `source_refresh_catalogue` | Play | scope includes VOD |
| Refresh programme guide | `source_refresh_epg` | Epg | scope includes live TV and (Xtream, or the XMLTV field is not blank, or the M3U field contains `get.php`, any case) |

- **SRC-FR-18** Edits live only in the page until an action saves them. Every field is
  edit-on-OK: focus shows the value, OK opens the keyboard, Done returns focus to the field
  ([Components](../design/02-components.md)).
- **SRC-FR-19** While any action runs, every action button and Delete are disabled ("busy").
  **Rebuild:** keep this for the screen's own actions, but background imports (SRC-FR-94) never
  disable the page; their progress shows in the status (SRC-FR-37).

### 4.4 Validation

- **SRC-FR-20** Every action except Delete first validates the whole page, in this order, and
  shows the first failure in the status line without saving anything:
  1. Blank name → `settings_source_name_required` "Enter a source name".
  2. Name over 100 characters → beta 23 shows an untranslated developer message ("Source name is
     too long"). **Rebuild:** a translatable message (new key, all seven languages); better, the
     field accepts at most 100 characters.
  3. M3U address → SRC-FR-21 with label "M3U"; XMLTV address, if not blank → same with label
     "XMLTV".
  4. Xtream: server → SRC-FR-21 with label `error_label_xtream_server` "Xtream server"; then blank
     username → `error_xtream_username_missing` "The Xtream username is missing"; blank password →
     `error_xtream_password_missing` "The Xtream password is missing".
- **SRC-FR-21** Address rule (one policy for every source address, also used by the network client
  and the phone page): trim; parse as a URI; the scheme must be `http` or `https` (any case) and
  the host must be present; otherwise `error_source_url_invalid` "The %1$s address must be a full
  address starting with http:// or https://" with the label. The stored value is the trimmed text,
  not otherwise rewritten.
- **SRC-FR-22** Xtream server: trailing `/` characters are removed; the username is trimmed; the
  password is kept exactly as typed.
- **SRC-FR-23** A blank XMLTV field stores no guide address.

### 4.5 Test address and Test connection

- **SRC-FR-24** **Test address** (M3U) does not save. Status `source_testing_m3u` "Testing the
  playlist address…"; the playlist is requested exactly like an import (SRC-FR-45…50) and parsed
  until **500** entries (`PROBE_LIMIT`) have been read, then the connection is closed. Results:
  - 0 entries → `source_test_m3u_empty` "The address answers, but the playlist is empty";
  - fewer than 500 → plural `source_test_m3u_ok` "M3U playlist found: %1$d entry/entries";
  - 500 → `source_test_m3u_ok_more` "M3U playlist found: at least %1$d entries";
  - failures → the failure's message: `error_playlist_not_m3u` "The address did not answer with an
    M3U playlist.", `error_source_http` "The source responded with HTTP error %1$d",
    `error_transport_failed_detail` "Could not complete the request: %1$s" (redacted detail).
  The probe tests the M3U address even when it is a `get.php` address (SRC-FR-73).
- **SRC-FR-25** **Test connection** (Xtream): status `source_testing_xtream` "Testing Xtream
  connection…", then the account call (SRC-FR-68). Success: `source_connection_ok` "Xtream
  connection works%1$s" where %1$s is `source_server_limit` " · server limit %1$d" when the panel
  reports `max_connections`, else empty. Failures: `error_xtream_auth_failed` "Xtream sign-in
  failed", `error_xtream_no_user_info` "The Xtream server did not return account details",
  `error_xtream_http` "The Xtream server responded with HTTP error %1$d",
  `error_xtream_response_invalid` "The Xtream server response was not valid", transport failures.
- **SRC-FR-26** Beta 23 **saves** the source before testing the Xtream connection. A new source
  tested first is then no longer "new" when Save securely is pressed, so its first sync is never
  started (SRC-FR-28). **Rebuild rule:** testing never saves; and Save securely syncs any source
  that has never completed an import, new or not.
- **SRC-FR-27** The server's `max_connections` is only shown; it does not change the source's
  connection limit (see open questions).

### 4.6 Save, sync and refresh actions

- **SRC-FR-28** **Save securely**: validate; write the source to the encrypted store and its
  row in `iptv_source_state` (SRC-FR-110); if the source was not saved before, queue a sync-now for
  it (SRC-FR-99) and show `source_saved_syncing` "Saved. Syncing channels, guide and catalogue in
  the background."; otherwise `source_saved` "Source saved securely". Saving an existing source
  with a changed address, credentials or scope starts nothing (see open questions).
- **SRC-FR-29** **Sync everything**: validate, save, queue sync-now for this source, show
  `source_sync_started` "Sync started. Channels, then the guide, then movies and series; progress
  shows below." The buttons are free again at once.
- **SRC-FR-30** **Refresh playlist / Refresh channels**: validate, save, status
  `source_refreshing_playlist` "Refreshing playlist…", run the playlist import (M3U, or Xtream for
  an Xtream source or a `get.php` M3U source), then plural `source_imported_channels` "Imported %1$d
  channel(s)" or the failure message.
- **SRC-FR-31** **Refresh movies and series**: validate, save, `source_refreshing_catalogue`
  "Refreshing movies and series…", run the catalogue import, then `source_imported_catalogue`
  "Imported %1$s and %2$s" with plurals `source_imported_movies` "%1$d movie(s)" and
  `source_imported_series` "%1$d series".
- **SRC-FR-32** **Refresh programme guide**: validate, save, `source_refreshing_epg` "Refreshing
  programme guide…", run the guide import — M3U: the XMLTV address if set, else the `get.php`
  derived Xtream guide, else `error_source_url_malformed` "The source address is invalid"; Xtream:
  its `xmltv.php` guide — then plural `source_imported_programmes` "Imported %1$d programme(s)".
- **SRC-FR-33** In beta 23 the three refresh buttons run inside the Settings screen: leaving
  Settings cancels the import, and a manual refresh runs even when the source is switched off.
  **Rebuild rule (SRC-40):** all imports run in the app-level import runner (SRC-FR-94); leaving the
  screen does not cancel them; returning shows their progress.
- **SRC-FR-34** Counts reported to the viewer are the counts **activated**, never merely parsed
  ([lessons 2.3](../plan/08-lessons-learned.md)).
- **SRC-FR-35** **Playlist and EPG refresh interval** (Settings › General, row
  `source_refresh_schedule` "Playlist and EPG refresh interval", subtitle
  `source_refresh_schedule_help` "Updates run in the background when a network connection is
  available.", Refresh icon): single picker with 1, 2, 4, 10, 24 hours (plural
  `source_refresh_interval_hours` "%1$d hour(s)"), default 24 hours. Choosing one stores it,
  reschedules the jobs (SRC-FR-97) and writes `source_refresh_schedule_saved` "Refresh interval set
  to %1$s" to the Playlists status line (invisible from General in beta 23; the rebuild gives every
  section its own status line — [Settings layout §9](../design/screens/settings.md)).
- **SRC-FR-36** Values `ONE_HOUR`, `TWO_HOURS`, `FOUR_HOURS`, `TEN_HOURS`, `TWENTY_FOUR_HOURS` are
  stored by name; an unknown stored value reads as 24 hours.

### 4.7 Status and health

- **SRC-FR-37** Status group (both pages): one text line = the status message and the health
  summary joined by " · " (blank parts dropped), `focus` colour, label style; under it
  `settings_security_subtitle` "Credentials are encrypted on this device and portable backups are
  protected with your password" (caption, `textDim`, 4 dp above).
- **SRC-FR-38** Initial status: `settings_add_first_source` "Add your first IPTV source" with no
  sources, else `settings_sources_loaded` "Sources loaded securely".
- **SRC-FR-39** Health summary of the selected source: one entry per kind that has a refresh-state
  row, in kind-name order (catalogue, epg, playlist), joined " · ". Kind labels: `health_playlist`
  "Playlist", `health_epg` "Programme guide", `health_catalogue` "Movies and series". Per status:
  - `running` → `health_updating` "%1$s: updating";
  - `success` → plural `health_success` "%1$s: %2$d item(s)";
  - `failed` → `health_failed_detail` "%1$s: %2$s" with the stored reason resolved in the current
    language (SRC-FR-105), or `health_failed` "%1$s: error (%2$d)" with the consecutive-failure count
    when no reason resolves.
  The summary observes the refresh-state table, so background progress appears without reopening.
  On the list page beta 23 shows the health of the last selected source (or of a blank new
  source). **Rebuild rule:** the list page shows only the status message; each row carries its own
  failure reason (SRC-FR-08).

### 4.8 Switching off, deleting, clearing

- **SRC-FR-40** **Switched off** (after saving): `iptv_source_state.enabled = 0`. Every read path
  — guide, rail, search, Home rows, film and series walls, continue watching, Sohva Sport channel
  matching, Trakt overlays — joins the source state with `enabled = 1`, so the source vanishes at
  once; the refresh worker skips it; all its rows stay and return when it is switched on.
- **SRC-FR-41** **Remove source** acts immediately, without a confirmation (see open questions).
  In one database transaction it deletes, for that source: Sohva Sport event–channel decisions for
  its channels; channel preferences (custom names, groups, hidden, order, numbers, logos, manual
  EPG mappings); custom-list memberships of its channels; channels (every snapshot); XMLTV
  channels; programmes; playback progress; episodes; films; series; import state; refresh state;
  source state. Then it removes the source from the encrypted store. Status `source_deleted`
  "Source deleted"; the page closes. Not deleted in beta 23: programme reminders for its channels,
  logo files saved from the phone, library organisation rules of the source and its film aliases
  ([Library organisation](42-library-organization.md)).
  **Rebuild rules:** Remove source first cancels and waits for any running import of the source
  (all three kinds), so no import can recreate rows after the delete; the leftovers above are
  deleted too.
- **SRC-FR-42** **Clear all guide data** (Settings › Backup & tools, [Settings](70-settings.md)):
  one transaction deleting, for all sources, channels, XMLTV channels, programmes, playback
  progress, episodes, films, series, import state and refresh state. Source configurations, source
  states, channel preferences and custom lists are kept. Help text `guide_clear_all_help` says only
  "Removes every stored programme. The next refresh reads the guide again." — the behaviour is much
  wider (see open questions). Status `guide_cache_cleared` "Programme guide cache cleared".
- **SRC-FR-43** Changing the import scope does not remove data of the kinds no longer imported:
  a source changed to Live TV keeps its films on the walls (see open questions).

### 4.9 Networking

- **SRC-FR-44** Provider requests use one HTTP client: connect timeout 20 s, read timeout 90 s,
  redirects followed including HTTPS→HTTP, write timeout and retries at the library defaults
  (10 s; retry on connection failure), no overall call timeout. **Rebuild rule (OwnTV item 22):**
  the provider-facing client (playlists, guides, Xtream API, streams) speaks **HTTP/1.1 only**;
  images use a separate client with HTTP/2 ([Tech stack](../plan/05-tech-stack-and-build.md)).
- **SRC-FR-45** User agent `Sohva TV/<versionName> (Android TV <Android release>)` (for example
  `Sohva TV/0.1.0-beta.23 (Android TV 11)`) on playlist, guide and stream requests. Beta 23 sends the
  HTTP library's default agent on Xtream `player_api.php` calls. **Rebuild rule:** the same agent on
  every provider request. Panels that drop unknown agents answered 403, a web page or nothing
  (commit a6674f9).
- **SRC-FR-46** Cleartext HTTP is allowed for provider hosts (addresses are typed by the viewer and
  many providers serve plain HTTP); the app's own services are HTTPS-only; only system certificate
  authorities are trusted ([Security](73-security-privacy.md)).
- **SRC-FR-47** Compression: the client asks for gzip transfer encoding and decodes it; in addition
  the body of a playlist or guide is sniffed for the gzip magic bytes `1F 8B` (a `.gz` file served
  as is) and decompressed. xz is **not** supported in beta 23 (see open questions).
- **SRC-FR-48** Failures: no response → `error_transport_failed_detail` "Could not complete the
  request: %1$s" with the exception text redacted of addresses and credentials, or
  `error_transport_failed` "Could not complete the request" when there is no text; non-2xx on a
  playlist or guide → `error_source_http` "The source responded with HTTP error %1$d"; on the Xtream
  API → `error_xtream_http`; an invalid address reaching the client → `error_source_url_invalid`
  with `error_source_label` "Source", or `error_source_url_malformed`.
- **SRC-FR-49** Xtream responses are limited to **1 GiB** (checked against `Content-Length` first,
  then counted while reading) → `error_xtream_response_too_large` "The Xtream server response was
  too large". Playlist and guide downloads have no size limit in beta 23 (see open questions).
- **SRC-FR-50** Diagnostics record `GET <redacted address>: no response` or `: HTTP <code>`
  ([Diagnostics](72-updates-about-diagnostics.md)).

### 4.10 M3U parser

- **SRC-FR-51** Input is read as UTF-8, one line at a time, as a lazy sequence (never the whole
  document). Each line has a leading U+FEFF removed and is trimmed; blank lines are skipped.
- **SRC-FR-52** The first non-blank line must open a playlist: it starts with `#`, or it contains
  `://` and does not start with `<`, `{` or `[`. Otherwise the document is refused with
  `error_playlist_not_m3u` "The address did not answer with an M3U playlist." (the log keeps the
  first 60 characters, redacted). A sign-in page, a JSON error or a bare sentence used to import as
  hundreds of channels named "Kanava 1, 2, 3…" and report success.
- **SRC-FR-53** Directives:
  - `#EXTM3U` exactly: ignored. `#EXTM3U` with attributes (for example `url-tvg=`) is an ordinary
    comment: **the header's guide address is not read** (see open questions).
  - `#EXTINF:` (any case) starts a new pending entry, discarding options seen before it: the text
    after `:` up to the first comma not inside single or double quotes holds the duration and
    attributes; the text after that comma, trimmed, is the display name.
  - `#EXTVLCOPT:` (any case) adds `http-user-agent=` → user agent; `http-referrer=` or
    `http-referer=` → referrer; other options ignored.
  - `#KODIPROP:` (any case) whose key ends with `stream_headers` (any case): value parsed as
    `Key=Value&Key=Value` (values URL-decoded) → `User-Agent`, `Referer`/`Referrer`.
  - `#EXTHTTP:` (any case): a JSON object; `"User-Agent"`, `"Referer"`, `"Referrer"` (any case)
    string values are taken.
  - Any other `#` line is ignored (`#EXTGRP` included, see open questions).
  - Any other line is a stream address and completes the entry; the pending metadata is cleared.
- **SRC-FR-54** Attributes match `([A-Za-z0-9_-]+)=(?:"([^"]*)"|'([^']*)'|([^\s,]+))`; names are
  lowercased; the first non-empty group is the value. Attributes used:

| Attribute | Use |
|---|---|
| `tvg-id` | EPG id (blank → none) |
| `tvg-name` | name when the display name is blank |
| `tvg-logo` | logo address |
| `group-title` | group |
| `tvg-chno`, else `channel-number` | the playlist's channel number, when a whole number > 0 (`12.1` → none) |
| `catchup`, else `catchup-type`, else presence of `timeshift` (→ `timeshift`) | catch-up type, trimmed and lowercased |
| `catchup-days`, else `timeshift` | catch-up days, an integer clamped to 1…365 |
| `catchup-source` | catch-up URL template ([Catch-up](22-catchup-and-reminders.md)) |
| `type`, `media-type`, `content-type`, `tvg-type` | content-kind hints (SRC-FR-57) |

  Every other attribute is ignored (`tvg-shift`, `tvg-language`, `tvg-country`, `radio`,
  `catchup-correction`, …).
- **SRC-FR-55** Duration: the text before the first space or comma after `#EXTINF:`, as an integer
  (`-1` for live).
- **SRC-FR-56** Stream address: split at the first `|`; the part after it is `Key=Value&…` headers
  (URL-decoded, keys lowercased). Precedence: `user-agent` from the address, else
  `#EXTVLCOPT`/`#KODIPROP`/`#EXTHTTP` (the last directive seen wins); referrer from `referer`, then
  `referrer`, then the directives. Other headers (Origin, Cookie…) are dropped. Beta 23 keeps
  `udp://` and other non-HTTP addresses as entries.
- **SRC-FR-57** Content kind (lowercased comparisons; the group normalised as in SRC-FR-60; the
  path is the address before `?`; "occurs" means **substring**, not whole word):
  - SERIES when any of `series`, `show`, `shows`, `sarja`, `sarjat` occurs in the declared type
    hints (the four type attributes joined by spaces) or in the group, or `/<hint>/` occurs in the
    path;
  - else MOVIE when any of `vod`, `movie`, `movies`, `film`, `films`, `elokuva`, `elokuvat` occurs
    the same way, or the path ends with `.avi`, `.m4v`, `.mkv`, `.mov`, `.mp4`, `.webm`, or the
    duration is > 0;
  - else LIVE.
  Substring matching misfiles live groups such as "Showtime" (SERIES) or "Filmbox" (MOVIE); with
  scope TV and VOD those channels leave the guide. Keep the rule for compatibility, but see §8.
- **SRC-FR-58** Name: display name, else `tvg-name`, else `Kanava <n>` with n = 1-based entry
  number. **Rebuild rule:** the fallback is a translatable string (it is Finnish in every language
  today).
- **SRC-FR-59** Entry id: SHA-256 (lowercase hex) of `<tvg-id>|<normalised name>` when a `tvg-id`
  exists, else of `<normalised name>|<stream address>`. Two event channels sharing one `tvg-id` but
  named differently stay distinct; the same name with the same `tvg-id` collapses into one row (the
  later entry wins). Without `tvg-id` a changing address (a rotating token) changes the id, and the
  viewer's edits to that channel are lost at the next import (see §8).
- **SRC-FR-60** Normalised name: Unicode NFKD, combining marks removed, lowercased (root locale),
  every run of characters outside `[a-z0-9]` replaced by one space, trimmed.
- **SRC-FR-61** `playlistOrder` = 0-based entry index; `channelNumber` from SRC-FR-54.
- **SRC-FR-62** An entry's text form never contains its stream address.

### 4.11 XMLTV parser

- **SRC-FR-63** Streaming pull parser over the (possibly decompressed) byte stream, namespaces off,
  DOCTYPE processing off. The parser receives the bytes, not a character reader, so it consumes a
  byte-order mark and detects UTF-8, UTF-16 LE/BE and the declared encoding (for example
  ISO-8859-1). A UTF-8 reader used to expose the BOM as text before `<?xml`, which the parser
  rejected with "PI must not start with xml" (beta 17 hotfix).
- **SRC-FR-64** Records are produced lazily in document order:
  - `<channel id="…">`: id trimmed and required (else the element is skipped); the first
    `<display-name>` text, trimmed; the `src` of the last `<icon>`.
  - `<programme channel start stop>`: all three required, else skipped. `start`/`stop` parsed by
    SRC-FR-65; unparseable → the programme is skipped (one bad timestamp used to discard the whole
    guide). Children: first `<title>`, first `<sub-title>`, first `<desc>` (each trimmed; empty →
    none), every non-empty `<category>`. A programme without a title is dropped. `lang` attributes
    are ignored (the first title wins whatever its language).
  - Every other element is ignored.
- **SRC-FR-65** Timestamps: `^(\d{8}(?:\d{4}(?:\d{2})?)?)\s*([+-]\d{4}|Z)$` after trimming, and at
  least `yyyyMMddHHmm`: `yyyyMMddHHmm` or `yyyyMMddHHmmss`, then a mandatory offset `±hhmm` or `Z`.
  A date without a time or a time without an offset is rejected.
- **SRC-FR-66** Programme id: the first 16 hex characters of SHA-256 of
  `<channel>|<start epoch ms>|<stop epoch ms>|<title>`; one digest instance per parse and a table
  lookup for the hex (a digest and 32 formatter calls per programme were most of the parse time).
- **SRC-FR-67** A document that is not well-formed (truncated download, stray `&`, a child element
  inside `<title>`) fails the whole import with the transport-failure message; the previous guide is
  kept (SRC-FR-80). Categories are stored joined with U+001F.

### 4.12 Xtream client

Endpoints are built on the normalised server address with a trailing `/`; a server address with a
path keeps it (`https://provider.example/panel` → `…/panel/player_api.php`). Path segments and
query values are percent-encoded by the URL builder.

- **SRC-FR-68** Endpoints and actions:

| Call | Request | Fields read |
|---|---|---|
| Account | `player_api.php?username=U&password=P` | `user_info.auth` ("1"/"true" = signed in, else `error_xtream_auth_failed`), `user_info.username`, `status`, `max_connections`, `active_cons`; `server_info.timezone`. Missing object → `error_xtream_no_user_info` |
| Live categories | `…&action=get_live_categories` | array of `category_id`, `category_name` |
| Live streams | `…&action=get_live_streams` | `stream_id`, `name`, `category_id`, `container_extension`, `epg_channel_id`, `stream_icon`, `tv_archive`, `tv_archive_duration`, `num` |
| Film categories | `…&action=get_vod_categories` | as live |
| Films | `…&action=get_vod_streams` | `stream_id`, `name`, `category_id`, `container_extension`, `stream_icon`, `year` or first 4 of `release_date`, `rating`, `plot` |
| Series categories | `…&action=get_series_categories` | as live |
| Series | `…&action=get_series` | `series_id`, `name`, `category_id`, `cover`, `backdrop_path` (first of an array, or a string), `year` or first 4 of `releaseDate`, `rating`, `plot` |
| Series info | `…&action=get_series_info&series_id=ID` (ID must match `[A-Za-z0-9._-]{1,128}`) | `episodes` object: season number (numeric key) → array of `id`, `episode_num`, `container_extension`, `title`, `info.plot`, `info.duration_secs`, `info.movie_image` / `info.cover_big` / `info.cover` (first present) |
| Guide | `xmltv.php?username=U&password=P` | XMLTV (§4.11), fetched through the playlist/guide client |

- **SRC-FR-69** Numbers may arrive as JSON numbers or numeric strings; booleans as `1`/`true` (any
  case). A record missing its id or with a blank name (episode: missing `id` or `episode_num`) is
  skipped; the rest of the array is kept. A response that is not the expected JSON →
  `error_xtream_response_invalid`.
- **SRC-FR-70** Stream addresses (extension = `container_extension` lowercased when it matches
  `[a-z0-9]{1,8}`, else the default):
  - live: `{base}/live/{U}/{P}/{stream_id}.{ext}` (default `ts`);
  - film: `{base}/movie/{U}/{P}/{stream_id}.{ext}` (default `mp4`);
  - episode: `{base}/series/{U}/{P}/{episode id}.{ext}` (default `mp4`).
- **SRC-FR-71** Live channel mapping: id `xtream-<stream_id>`; group = category name looked up by
  `category_id` (the category id is also kept for the group key); EPG id `epg_channel_id` (blank →
  none); logo `stream_icon`; catch-up when `tv_archive` is true: type `xtream`, days =
  `tv_archive_duration` clamped 1…365 (`tv_archive` true without a duration → no catch-up); the
  server time zone from the account call is stored with each channel for catch-up; `playlistOrder`
  = `num`, or the array index when `num` is missing; channel number = `num` when > 0.
  Episode mapping: season from the `episodes` key, number from `episode_num`; the title is the
  provider's `title` trimmed — when it contains this episode's marker (`S`, optional zeros, the
  season, optional spaces, `E`, optional zeros, the episode, not followed by a digit; any case) only
  the text after the marker is kept, with spaces and `- – — : .` trimmed from both ends; a blank
  result or a missing title gives `Jakso <n>` (**rebuild rule:** a translatable fallback).
- **SRC-FR-72** Catch-up address for type `xtream`:
  `{scheme}://{host}:{port}/{path prefix}/timeshift/{U}/{P}/{duration minutes}/{start}/{stream_id}.ts`,
  duration = ceil((stop − start) / 60 s), at least 1; start formatted `yyyy-MM-dd:HH-mm` in the
  channel's server time zone, else the device zone. The full template grammar for M3U catch-up
  (`default`, `append`, `shift`/`timeshift`, `{utc}`, `{lutc}`, `${start:Y-m-d}`, `{duration:60}`,
  `{offset:1}`…) is in [Catch-up](22-catchup-and-reminders.md).

### 4.13 M3U addresses that are Xtream panels

- **SRC-FR-73** An M3U source whose playlist address has a last path segment `get.php` (any case)
  and non-blank `username` and `password` query parameters (URL-decoded, parameter names any case)
  is treated as Xtream for imports: server = `{scheme}://{host}[:{port}]{parent path}`, with the
  same id, name, scope, offset and limit. Used for: the playlist import (always, even when an XMLTV
  address is set); the guide import only when the XMLTV field is blank; the catalogue import
  (always); episodes on the series page. Channels then get Xtream ids, so moving a source between
  the two forms changes its channel ids.
- **SRC-FR-74** **Rebuild rule:** one function decides "how to import this source" (M3U, Xtream,
  or `get.php` → Xtream) and every caller uses it; beta 23 repeats the branching in Settings, the
  worker and the app shell.

### 4.14 Import pipeline (all kinds)

Kinds: `playlist` (live channels), `epg` (guide), `catalogue` (films and series).

- **SRC-FR-75** **One import of a kind per source at a time** (`SourceImports`): a process-wide
  mutex per `(kind, source id)`; a second request waits for the first and then runs in full. Not
  re-entrant: the Xtream guide import hands over to the XMLTV import and locks once. On 23
  September 2026 a background "Sync everything" and a foreground "Refresh channels" of one Xtream
  source raced: the first activation deleted the second's staged rows, the second activated its
  empty snapshot, Settings reported 1,826 channels and the guide had none
  (`ConcurrentPlaylistImportTest`: 0 channels in the rail before the lock, 60 after).
- **SRC-FR-76** Staged snapshot: each import takes a new snapshot id (random UUID), marks the
  refresh started (status `running`, attempt time now, error cleared, previous success time,
  failure time, item count and failure count kept), writes rows tagged with the snapshot id in
  batches, checks its guards, then **activates** in one transaction: `import_state` (active
  snapshot id, time, item count) and refresh state `success` (count, failure count 0, error
  cleared), and deletes the source's other snapshots of that kind. Readers only ever join the
  active snapshot, so they see the old data until the switch and the new data after it.
- **SRC-FR-77** Failure: the staged snapshot is deleted; refresh state `failed` (failure time now,
  attempt time kept, `lastError` = the stored failure message, SRC-FR-105, consecutive failures + 1);
  the error is rethrown in its localized form. The previous active snapshot is untouched.
- **SRC-FR-78** Cancellation is not failure: the staged snapshot is deleted in a non-cancellable
  section and the cancellation propagates without recording a failure. Beta 23 does this for the
  M3U playlist and guide imports only; the Xtream playlist and both catalogue imports record a
  cancelled import as failed. **Rebuild rule:** every import behaves like the guide import.
- **SRC-FR-79** After every activation the database statistics are refreshed (ANALYZE, SRC-L-22).
- **SRC-FR-80** Guards that keep the previous data (the refresh is marked failed with the message):

| Import | Condition | Message |
|---|---|---|
| M3U playlist | no entry parsed | `error_playlist_empty` "The playlist came back empty. The previous channels were kept." |
| M3U playlist / catalogue | first line is not M3U | `error_playlist_not_m3u` |
| Guide | no programme parsed | `error_epg_empty` "The programme guide came back empty. The previous guide was kept." |
| Guide | none kept after filtering, or the source has channels with EPG ids and not one staged programme matches them | `error_epg_unmatched` "The programme guide matches none of this source's channels. The previous guide was kept." |
| M3U catalogue | no entry parsed | `error_catalogue_empty` "The playlist came back empty. The previous movies and series were kept." |

  Beta 23 has **no** empty guard for Xtream live channels, films or series: an empty array
  activates and empties the source. **Rebuild rule:** the same guard for every import (an empty
  answer never replaces a non-empty active snapshot; a source that never had data may activate
  empty and report 0).
- **SRC-FR-81** Preconditions, each a localized failure: wrong type (`error_source_not_m3u` "This
  is not an M3U source", `error_source_not_xtream` "This is not an Xtream source"); scope without
  live TV for playlist/guide (`error_source_no_live_tv` "This source does not provide live TV");
  scope without VOD for catalogue (`error_source_no_vod` "This source does not provide movies or
  series"); M3U address missing (`error_m3u_url_missing` "The M3U source address is missing"); Xtream
  server, username or password missing (`error_xtream_url_missing`, `error_xtream_username_missing`,
  `error_xtream_password_missing`) or invalid (`error_xtream_url_invalid`).

### 4.15 Playlist import

- **SRC-FR-82** Playlist import, rows written in batches of **250**:
  - M3U: the playlist is streamed through the parser; **scope LIVE_TV keeps every entry, scope
    BOTH keeps only entries classified LIVE** (films and series go to the catalogue import). The
    guard counts every parsed entry, so a BOTH-scoped playlist holding only VOD entries activates
    0 channels.
  - Xtream: account call, live categories, live streams (SRC-FR-68). Beta 23 reads the whole
    live-stream array into memory before writing (see §9).
  - Each channel row stores: global channel id `<source id>:<entry id>`, EPG id, name, normalised
    name, group, group key (`name:<lowercased trimmed group>`; Xtream `id:<category_id>`), logo,
    **encrypted** stream address ([Security](73-security-privacy.md)), user agent, referrer,
    last-seen time, playlist order, catch-up type, source and days, channel number; Xtream rows also
    the provider stream id and the server time zone.
  - Channel preferences (favourites, hidden, custom name, group, order, logo, number, manual EPG
    id) are keyed by global channel id and survive every re-import whose ids are stable
    ([Channel management](21-channel-management.md)).

### 4.16 Guide import

- **SRC-FR-83** Which programmes are written: the set of EPG ids the source's **active** channels
  answer to (the manual mapping if set, else the `tvg-id`) is read first. If that set is empty (no
  playlist imported yet, or no channel carries an id) every programme is kept. Otherwise a
  programme is written only if its channel id is in the set. Time window, measured from the import's
  start: programmes that **ended more than 12 hours ago** or **start more than 8 days ahead** are
  skipped. The XMLTV channel list is kept whole (the manual-mapping picker offers every channel).
  **Rebuild rule (OwnTV item 10, owner-adopted):** past programmes are kept **only for channels with
  catch-up**, back to the shorter of the channel's catch-up days and the guide's backward reach
  (24 h, [Live TV guide](20-live-tv-guide.md)); other channels keep only a short recent past (see
  open questions for the numbers). Beta 23 keeps 12 h for every channel while the guide pages back
  24 h, so the first 12 hours back show empty rows.
- **SRC-FR-84** Batches: XMLTV channels 250 per write (upsert); programmes **2,000** per write as
  plain inserts that ignore a duplicate primary key (the first copy in the feed wins).
- **SRC-FR-85** Before activation the staged snapshot is matched against the active channels
  (SRC-FR-80): matched programmes = staged programmes whose channel id is an active channel's
  manual mapping or `tvg-id`; mappable channels = active channels with such an id.
- **SRC-FR-86** An Xtream source's guide is the same import on its `xmltv.php` address (SRC-FR-68).
- **SRC-FR-87** The EPG offset is **not** applied at import: stored times are the feed's; every read
  adds `epgOffsetMinutes × 60,000` ms ([Live TV guide](20-live-tv-guide.md)). The keep window
  ignores the offset.
- **SRC-FR-88** There is no pruning between imports: data ages until the next guide import.
- **SRC-FR-89** A manual EPG mapping made later to an id outside the stored set shows no programmes
  until the next guide import. **Rebuild rule:** saving such a mapping queues a guide refresh of
  that source.

### 4.17 Catalogue import

- **SRC-FR-90** M3U: entries streamed; **scope BOTH skips LIVE entries; scope VOD keeps every
  entry** (live-looking ones become films). An entry whose name matches
  `(?i)^(.+?)\s+[Ss](\d{1,3})\s*[Ee](\d{1,4})(?:\s*[-–—:.]\s*(.*))?$` or
  `(?i)^(.+?)\s+(\d{1,3})x(\d{1,4})(?:\s*[-–—:.]\s*(.*))?$` is an **episode**; every other entry is a
  **film** (an entry classified SERIES without such a name becomes a film).
  - Film: id = entry id, name, normalised name, category = group, poster = logo, encrypted address,
    year = first `(19|20)\d\d` not adjacent to other digits; rating and plot none. Batches of 250.
  - Series: id = SHA-256 of `<series name>|<group>`; name, normalised name, category, poster = the
    first episode's logo, year from the series name.
  - Episode: id = entry id, season, episode, name = the text after the marker, else
    `S%02dE%02d`; encrypted address; thumbnail = logo.
  - Beta 23 keeps every series and all its episodes in memory until the playlist ends, then writes
    series in batches of 250 and each series' episodes with a delete-and-insert per series (see §9).
- **SRC-FR-91** Xtream: account call, film categories, films streamed and written in chunks of 250
  as they arrive (a 56,835-channel provider's film list passed a 64 MB whole-body cap and was
  refused, commit 8ad18c8), then series categories and series the same way. Item count = films +
  series.
- **SRC-FR-92** Episodes of Xtream series are **not** imported with the catalogue: opening a series
  page calls `get_series_info` and replaces that series' episodes (sorted by season, episode).
  Plain M3U series get their episodes from the playlist.
- **SRC-FR-93** Activation: import state `catalogue`; refresh state; delete the source's inactive
  film and series snapshots and every episode whose series is not in the active snapshot. Film
  identity aliases are registered for library organisation ([Library
  organisation](42-library-organization.md)); after the catalogue step the metadata worker restarts
  ([Metadata](41-metadata-enrichment.md)). Episodes have no snapshot column: episodes written by a
  failed M3U catalogue import stay behind (see §10).

### 4.18 The import runner (rebuild)

- **SRC-FR-94** **One entry for imports (OwnTV item 2).** Settings actions, the phone page, the
  guide's empty state, the library options sheet, the series page and the background worker all call
  one `sync(source, kinds)` on an application-scoped runner. It resolves how to import the source
  (SRC-FR-74), takes the per-kind locks, runs the kinds in the order playlist → guide → catalogue,
  runs the follow-up work (ANALYZE, metadata worker restart, sports-match invalidation) exactly
  once, and publishes progress (kind, rows so far, phase) that Settings and the guide observe. No
  caller runs an importer directly.
- **SRC-FR-95** **Update strategy per source type (OwnTV item 3).** M3U has no stable ids, so M3U
  channels, M3U films and series, and all programmes are **replaced** through staged snapshots
  (SRC-FR-76). Xtream items have stable ids (`stream_id`, `series_id`), so Xtream channels, films
  and series are **updated in place**: each row carries a content hash of the fields stored; an
  unchanged row is not written; new and changed rows are upserted in batches; the ids seen are
  collected in a temporary table; only after the whole list was read successfully and was not empty
  are the rows not seen deleted. A failure part-way leaves a complete, partly refreshed set and
  deletes nothing. The UI re-reads once, when the import finishes (SRC-L-23). Plan/04 decides the
  table shape (for example, Xtream sources keep one permanent snapshot id so read queries stay the
  same for both types).
- **SRC-FR-96** **Per-category fallback (OwnTV item 7).** When `get_live_streams`,
  `get_vod_streams` or `get_series` answers HTTP 5xx (providers use 512 for a truncated bulk list),
  or its body ends before the JSON array closes, the runner reads the category list and fetches
  `…&action=<same>&category_id=<id>` for each category in category order, merging the results. If a
  category fails after one retry the import fails and keeps the previous data.

### 4.19 Scheduling

- **SRC-FR-97** `schedule(interval)` runs at every app start and whenever the interval preference
  changes; not in demo mode and not in the Lab package. It enqueues three unique periodic jobs
  (policy UPDATE, so a changed interval keeps the running iteration): `streammate-playlist-refresh`
  and `streammate-epg-refresh` every *interval* hours, `streammate-catalogue-refresh` every 24
  hours; constraint: network connected; linear backoff of 15 minutes per attempt; input
  `refresh_kind` = the kind.
- **SRC-FR-98** Worker: loads the enabled sources (optionally one id), highest priority first. A
  non-immediate run in the Lab package does nothing. **Deferral:** when the app is in the
  foreground (at least one started activity), the run is not a sync-now, and every live-TV source
  has completed a playlist import at least once, the run returns *retry* (the 15-minute backoff).
  Otherwise, for each kind in order and each source: upsert its source state, run the import by
  type, scope and `get.php` rule; count failures. During a sync-now, a failed catalogue step is
  retried once after **20 s** (providers often refuse the request that follows a playlist and a
  guide). After the catalogue kind the metadata worker restarts. Result: success when nothing
  failed or the run was a sync-now; otherwise retry.
- **SRC-FR-99** Sync-now: one-time unique job `sohva-sync-now-<source id | all>`, policy KEEP (a
  second request while one is queued or running is dropped), network connected, kinds playlist →
  guide → catalogue (all sources' playlists, then all guides, then all catalogues), immediate.
  Started by: Save securely of a new source, Sync everything, a source received from the phone
  page, the guide empty state's sync (all sources).
- **SRC-FR-100** **Rebuild rules:** one periodic job runs playlist then guide per source (beta 23
  runs them as two independent jobs, so a guide can be filtered against the previous cycle's
  channels); automatic imports never start while a video plays and wait until playback stops
  (SRC-L-25); a sync-now for a source whose sync is already running is merged into it, not
  dropped silently — the status says it is already running.

### 4.20 Connection limit at playback

- **SRC-FR-101** An in-memory counter per source. Opening a live channel, catch-up programme, film
  or episode takes a lease with the source's saved limit; when the source already has that many
  open leases the request fails with `error_source_connection_limit` "The connection limit for
  %1$s (%2$d) is already in use" (source name, limit). Closing a lease is idempotent and frees a
  slot. Imports, tests and probes are not counted. Details of when a player holds and releases its
  lease: [Player](30-player.md).

### 4.21 Stream tags from channel names

- **SRC-FR-102** The name is uppercased (root locale) and split on space `| : - _ / ( ) [ ] , .`.
  For each kind the first token that matches wins; a name with no known marker yields no tags; a
  language is only ever a whole token ("FI" inside "SCIFI" is not Finnish).

| Kind | Tokens → label |
|---|---|
| Resolution | 4K, UHD, 2160P, 4KUHD → 4K; FHD, FULLHD, 1080P, 1080 → FHD; HD, 720P, 720 → HD; SD, 576P, 480P → SD |
| Dynamic range | HDR → HDR; HDR10 → HDR10; HDR10+ → HDR10+; DV, DOLBYVISION → DOLBY VISION; HLG → HLG |
| Frame rate | a whole token matching `(\d{2,3})(?:FPS\|HZ\|P)` → "<n> FPS" |
| Language | FI/FIN/SUOMI → FI; SE/SV/SWE → SE; NO/NOR → NO; DK/DAN → DK; EN/ENG → EN; UK/GB/GBR → UK; US/USA → US; DE/GER/DEU → DE; EE/EST → EE; RU/RUS → RU; FR/FRA → FR; ES/ESP/SPA → ES; IT/ITA → IT; NL/NLD → NL; PL/POL → PL; AR/ARG → AR; AL/ALB/SQ → AL; PT/POR → PT; BR/BRA → BR; TR/TUR → TR; GR/GRE → GR; RO/RON → RO; CZ/CZE → CZ; HR/HRV → HR; RS/SRB → RS; CA/CAN → CA; AU/AUS → AU |

  Tags are ordered resolution, dynamic range, frame rate, language. Each kind scans the tokens
  independently, so in beta 23 `720P`, `576P` and `480P` match both the resolution table and the
  frame-rate pattern: "Sport 720p" reads "HD · 720 FPS" (`1080P` and `2160P` have four digits and
  escape it). **Rebuild rule:** a token claimed by the resolution table is not read as a frame
  rate; `50P`, `50FPS`, `60HZ` still read as frame rates. The same language table normalises the
  Sohva Sport channel priority list (at most 8 distinct codes, unknown codes dropped; [Sohva
  Sport](60-sohva-sport.md)).
- **SRC-FR-103** **Rebuild rule:** tags are computed once per channel at import and stored (a
  compact column), never in composition (beta 23 reads them per guide row and per player overlay on
  first composition).

### 4.22 Error messages and stored failures

- **SRC-FR-104** Every import, client and validator failure is a localized exception: a string
  resource plus arguments, resolved by the screen in the current interface language. An argument
  may itself be a string resource (the field label). Anything else shown on screen is the
  exception's text with addresses and credentials redacted, or `error_unknown` "Unknown error".
  A failure that is already localized passes through a layer unchanged (wrapping it again once put
  "resource:2131558691" on screen); a transport failure is wrapped as
  `error_transport_failed_detail` with the redacted detail.
- **SRC-FR-105** The refresh state stores the failure as text: `resource:<id>` followed by
  tab-separated arguments (`@<id>` for a resource argument; tabs and newlines in arguments replaced
  by spaces). Health rows, the guide and the diagnostics file resolve it in the language of the
  moment; an id whose resource entry name does not start with `error_` resolves to nothing (ids are
  renumbered between builds). **Rebuild rule:** store the resource **name** (stable across builds),
  not its numeric id, and keep the `error_` check. Storing words froze failures in one language;
  storing the id alone showed "HTTP error %1$d".

### 4.23 Stored configuration

- **SRC-FR-106** The source list is one record in private shared preferences `streammate_secure_sources`,
  key `sources_v1`: the codec output (SRC-FR-108) encrypted by the app's secret cipher
  ([Security](73-security-privacy.md)). Writes are synchronous (`commit`); a failed write throws
  ("Could not persist encrypted IPTV sources"). Load, save, upsert and delete are serialised.
- **SRC-FR-107** First load with no `sources_v1` but a legacy `sportmate_secure_settings` file with
  encrypted `m3u` and `xmltv` values: a single source `m3u-primary` named `IPTV` is created from
  them, saved, and the legacy file cleared.
- **SRC-FR-108** Codec (all integers big-endian 32-bit, booleans one byte, strings = byte length
  0…1,048,576 + UTF-8 bytes, nullable strings = boolean present + string), Base64 (standard, padded):
  `MAGIC 0x53544D53`, `VERSION 3`, `count 0…100`, then per source: id, name, type (`M3U`/`XTREAM`),
  enabled, connectionLimit, priority, m3uUrl?, xmlTvUrl?, xtreamBaseUrl?, xtreamUsername?,
  xtreamPassword?, importScope (`LIVE_TV`/`VOD`/`BOTH`, version ≥ 2), epgOffsetMinutes (version ≥
  3). Decoding accepts versions 1–3 (version 1 → scope BOTH; versions 1–2 → offset 0), applies the
  model rules (SRC-FR-02…05), and rejects duplicate ids, unknown enum names and trailing bytes.
- **SRC-FR-109** Beta 23 returns an **empty list** when the stored record cannot be decrypted or
  decoded, so a damaged store looks like "no sources" and the next save overwrites it. **Rebuild
  rule:** report the failure, keep the raw record until the viewer restores a backup or removes it
  explicitly.
- **SRC-FR-110** `iptv_source_state` mirrors the non-secret fields the database needs (id, name,
  type, enabled, connection limit, priority, EPG offset, update time); it is upserted on every save,
  by the worker before each import, and on backup restore.

## 5. Screen anatomy

The Playlists list and the source page are measured in [Settings layout §6.2
"Playlists (SOURCES)"](../design/screens/settings.md) and use the Settings group vocabulary,
`SettingsValueRow`, `SettingsRow`, switch, `TvActionButton` and edit-on-click `TvUrlField`
described there (§3–§4) and in [Components](../design/02-components.md). What this spec adds:

- Order of items on the source page: SRC-FR-13…17. Address labels are Bold 16 sp text rows (not
  settings rows); the address fields use 82 % of the pane width; the name field is 320 dp.
- The EPG correction row is a bare row (not a settings row): label 13 sp `textMuted`, value 14 sp
  Bold `textPrimary` in a 74 dp box, help 12 sp `textMuted` filling the rest.
- The status group sits last on both pages (SRC-FR-37). No spinner anywhere: progress is text.
- Colours: the status line and every message, success or failure, are `focus`; only danger
  buttons use `danger` ([Settings layout §9](../design/screens/settings.md)).
- No screenshot of the beta 23 Playlists page exists in the kit; the pre-restructure capture is
  `design/screenshots/older-builds/2026-09-02-demo/09-settings.png`.
- Test tags used by the current tests (keep them): `source-<id>`, `source-add-m3u`,
  `source-add-xtream`, `source-add-phone`, `source-page-back`, `settings-m3u`, `settings-xmltv`,
  `settings-xtream-base-url`, `settings-xtream-username`, `settings-xtream-password`,
  `settings-source-name`, `settings-source-enabled`, `settings-limit-down`, `settings-limit-up`,
  `settings-source-delete`, `settings-import-live_tv`, `settings-import-vod`,
  `settings-import-both`, `settings-epg-offset-down`, `settings-epg-offset-up`,
  `settings-epg-offset-value`, `settings-save`, `settings-test-m3u`, `settings-test-xtream`,
  `settings-sync-everything`, `settings-refresh-playlist`, `settings-refresh-catalogue`,
  `settings-refresh-epg`, `settings-status`, `settings-refresh-interval-<hours>`.

## 6. Data

Secure storage (not in the database)
- `streammate_secure_sources` / `sources_v1`: the encrypted source list (SRC-FR-106…108). Global,
  not per profile. Included in the encrypted `.smbak` backup ([Backup](71-backup-restore.md)).
- Legacy `sportmate_secure_settings` (`m3u`, `xmltv`): read once and cleared (SRC-FR-107).

Preferences
- Playlist and EPG refresh interval, stored by enum name, default `TWENTY_FOUR_HOURS`
  ([Settings](70-settings.md)).

Database tables written by this feature (full schema and migrations: [Data model](../plan/04-data-model.md))

| Table | Key | Written by | Notes |
|---|---|---|---|
| `iptv_source_state` | sourceId | save, worker, restore | name, type, enabled, connectionLimit, priority, updatedAt, epgOffsetMinutes |
| `import_state` | (sourceId, kind) | activation | activeSnapshotId, updatedAt, itemCount |
| `source_refresh_state` | (sourceId, kind) | every import | status `running`/`success`/`failed`, lastAttempt, lastSuccess, lastFailure, lastError (SRC-FR-105), itemCount, consecutiveFailures |
| `iptv_channels` | (sourceId, snapshotId, channelId) | playlist import | columns in SRC-FR-82; indexes (sourceId, snapshotId), (sourceId, tvgId), (channelId) |
| `xmltv_channels` | (sourceId, snapshotId, xmltvChannelId) | guide import | displayName, iconUrl (stored, never read in beta 23); index (sourceId, snapshotId) |
| `tv_programmes` | (sourceId, snapshotId, programmeId) | guide import | xmltvChannelId, start, stop, title, subtitle, description, categories (U+001F-joined); indexes (sourceId, snapshotId), (sourceId, xmltvChannelId, start, stop) |
| `vod_movies` | (sourceId, snapshotId, movieId) | catalogue import | name, normalisedName, category, categoryKey (trimmed lowercased), poster, encrypted address, year, rating, plot, group keys; indexes (sourceId, snapshotId), normalizedName, categoryKey, (sourceId, movieId) |
| `vod_series` | (sourceId, snapshotId, seriesId) | catalogue import | as films plus backdrop; indexes (sourceId, snapshotId), normalizedName, categoryKey |
| `vod_episodes` | (sourceId, seriesId, episodeId) | M3U catalogue import, series page | season, episode, name, encrypted address, plot, duration, thumbnail; no snapshot column |

- Lifetimes: rows live until replaced by the next activation of their kind, the source is
  removed, or Clear all guide data. Programmes are not pruned between imports.
- Per profile: nothing here is per profile. Playback progress is per profile
  ([Movies and series](40-movies-and-series.md)) and Remove source deletes it for every profile.
- Backups contain the source list and channel customisations, not imported rows; a restored install
  imports again.

## 7. External interfaces

Provider HTTP (all GET, SRC-FR-44…49)

| What | Address | Parser | Limit |
|---|---|---|---|
| M3U playlist | the viewer's address | §4.10 | none in beta 23 |
| XMLTV guide | the viewer's address, or `{xtream}/xmltv.php?username=U&password=P` | §4.11 | none in beta 23 |
| Xtream API | `{xtream}/player_api.php?username=U&password=P[&action=…][&series_id=…][&category_id=…]` | JSON arrays streamed element by element | 1 GiB |
| Streams, catch-up | SRC-FR-70, SRC-FR-72 | — | — |

Headers: `User-Agent: Sohva TV/<version> (Android TV <release>)` (SRC-FR-45); `Accept-Encoding:
gzip` by the HTTP library. No authentication headers: Xtream credentials travel in the query
string and path, as the protocol requires, which is why every log line and message is redacted.

Error mapping

| Situation | Message key |
|---|---|
| Address not http(s) or no host | `error_source_url_invalid` (label) |
| Address unusable at request time | `error_source_url_malformed` |
| Connect/read failure, TLS failure, reset | `error_transport_failed_detail` / `error_transport_failed` |
| Playlist/guide HTTP status not 2xx | `error_source_http` (code) |
| Xtream HTTP status not 2xx | `error_xtream_http` (code) |
| Xtream body over 1 GiB | `error_xtream_response_too_large` |
| Xtream body not the expected JSON | `error_xtream_response_invalid` |
| Xtream `auth` false | `error_xtream_auth_failed` |
| Xtream without `user_info` | `error_xtream_no_user_info` |
| Series id not `[A-Za-z0-9._-]{1,128}` | meant to be `error_series_id_invalid` "The series identifier is invalid"; beta 23 raises a plain exception whose text is the resource number, shown as "Could not complete the request: 2131…". Rebuild: the localized message |
| Not an M3U document | `error_playlist_not_m3u` |
| Guards | SRC-FR-80 |
| Playback over the limit | `error_source_connection_limit` |

## 8. Edge cases and limits

- **Huge playlists and catalogues**: 56,164 channels, 200,000 films — every path must stream and
  batch (§9). A provider whose film list exceeded a 64 MB body cap was refused until the cap became a
  1 GiB runaway guard.
- **Sign-in pages, JSON errors, empty documents**: refused or guarded (SRC-FR-52, SRC-FR-80).
- **Truncated downloads**: the parse fails or the guard trips; the staged snapshot is discarded;
  nothing replaces good data.
- **Duplicate programmes**: exact duplicates collapse on the primary key (first wins); corrected
  variants with other ids for the same channel and start are resolved at read time by keeping the
  richer one ([Live TV guide](20-live-tv-guide.md)).
- **Feeds without offsets** (`20260922190000` alone) or date-only stamps: programme skipped.
- **Programme with nested markup in `<title>`**: fails the whole guide import in beta 23.
  **Rebuild rule:** skip the element and continue.
- **Very long lines**: beta 23 has no line-length cap. **Rebuild rule:** a line longer than 64 KiB is
  skipped (a single-line HTML page or binary junk must not allocate unbounded text).
- **Content kind by substring** (SRC-FR-57): a live group whose name contains `show`, `film`,
  `movie`, `vod`, `series` or the Finnish words is classified as VOD; with scope TV and VOD its
  channels leave the guide and appear as films. Whole-word matching would fix it but changes which
  entries existing sources import (see open questions).
- **Channels without `tvg-id` and rotating stream tokens**: new ids each import; customisations lost.
  Known limitation of the id rule (SRC-FR-59); keep for compatibility unless plan/04 migrates ids.
- **Xtream `tv_archive` without a duration**: no catch-up. **An `episodes` value that is not an
  object** (for example an array): beta 23 reads only the object form and shows no episodes.
- **Xtream server time zone missing or invalid**: catch-up uses the device zone.
- **Self-signed provider certificates**: refused (system CAs only); message carries the TLS detail.
- **HTTPS → HTTP redirects**: followed.
- **Clock changes**: the keep window uses the wall clock at import start; a clock far off (a box
  without NTP, an emulator booted from a week-old snapshot) keeps the wrong week and the guide looks
  empty. The next import after the clock is right repairs it.
- **Process death mid-import**: the staged snapshot stays in the table (orphan rows); the next
  activation of that kind deletes it. The refresh state stays `running` until the next import of that
  kind. **Rebuild rule:** at start-up, a `running` state older than the process marks itself failed
  ("interrupted") and orphan staged snapshots are swept in the background (SRC-L-17).
- **Deleting a source while its import runs**: beta 23 can recreate rows after the delete
  (SRC-FR-41 rule).
- **Two sources with the same XMLTV address**: downloaded and stored once per source.
- **Low memory**: imports run in the app process; the heap budget during imports is 128 MB for the
  whole app ([conventions](../plan/_spec-conventions.md)).
- **No network**: WorkManager holds automatic and sync-now jobs until connected; Settings' manual
  refreshes and tests fail at once with the transport message.

## 9. Lightweight by design

Owner-scale fixture for every measurement: 56,164 channels in 800 groups, 165,600 programmes
(descriptions of realistic length, plus a stress variant with 2,150-character descriptions),
30,000 and 200,000 films, 1,500 series of 12 episodes (the `.local/slowbox` fixture shape).
Budgets (starting values, refined in [Performance](../plan/07-performance.md)): Java heap for the
whole app ≤ **128 MB** during any import, the import's own working set ≤ **16 MB**; no D-pad press
later than two vsyncs and no dropped video frame attributable to the import on the low-end box;
proposed import times on the low-end box — 56,164 channels ≤ 60 s, 165,600 programmes ≤ 90 s,
200,000 films ≤ 180 s (see open questions). For scale: beta 23 imported the whole fixture with
30,000 films in about 90 s on the emulator; filtering the guide took one feed from 134,000
programmes in 35 s to the 54,000 that mattered in 12 s (commit 00f9c4d).

### 9.1 Where it runs

- **SRC-L-01** Imports run on the application-scoped runner (SRC-FR-94) on two dedicated threads —
  one network + parse, one database writer — at `THREAD_PRIORITY_BACKGROUND`. Never on the main
  thread, never on the shared IO pool at default priority (beta 23 runs them on `Dispatchers.IO` or
  WorkManager's default threads; the Xtream episode fetch was once called from a `LaunchedEffect`
  on the main thread, per-row encryption included).
- **SRC-L-02** Nothing at start-up: scheduling the periodic jobs is a WorkManager call with UPDATE
  policy; no import, no ANALYZE and no source decryption beyond reading the list when a screen needs
  it. The first sync of a new install starts only when the viewer saves a source.

### 9.2 Bounded-memory streaming

- **SRC-L-03** **Stream everything (OwnTV item 5).** M3U: line by line from the (decompressed)
  socket stream with a bounded line length (§8). XMLTV: pull parser on the byte stream, gzip sniffed
  by magic bytes. Xtream JSON: a streaming token reader (`android.util.JsonReader` or equivalent)
  that builds one item at a time; never decode a whole array or object tree. Beta 23 streams films
  and series but collects **all live streams** into a list (56,164 objects plus their address
  strings) before writing, and decodes each array element into a JSON tree first.
- **SRC-L-04** Upper bounds held in memory by an import: one batch being parsed, at most one batch
  queued, one batch being written (§9.3); the EPG-id filter set (≤ 56,164 ids — store 64-bit hashes
  in a primitive hash set, about 1 MB, rather than 56,164 strings); category name maps (≤ a few
  thousand entries); for an M3U catalogue, series headers only (id → name, group, poster, year;
  thousands, not hundreds of thousands). Nothing proportional to the number of films, programmes or
  episodes.
- **SRC-L-05** M3U catalogue rebuild rule: episodes are written in batches of 250 as they are
  parsed into the staged snapshot (episodes get a snapshot column, or a staging table); series rows
  are written at the end from the in-memory headers. Beta 23 holds every series **and every episode**
  until the playlist ends.
- **SRC-L-06** Whole-catalogue follow-ups page: film identity registration after an M3U catalogue
  import reads the snapshot in pages of 2,000 in primary-key order (beta 23 reads all films of the
  snapshot at once — the same trap that killed the process every four minutes on a 200,000-film
  catalogue, [lessons 1.1](../plan/08-lessons-learned.md)).
- **SRC-L-07** Test address stops after 500 entries and closes the connection; it never downloads a
  whole large playlist.

### 9.3 Batches, transactions and overlap

- **SRC-L-08** **Overlap download, parse and write (OwnTV item 6).** The parse thread fills batches
  and hands them to the writer through a channel of capacity 1; the parser blocks when the channel
  is full (natural back-pressure), so network, parser and SQLite work at the same time and memory
  stays at three batches. Beta 23 writes each batch inline, stalling the socket while SQLite works
  (and the Xtream path runs the writes under `runBlocking` inside the body read).
- **SRC-L-09** One transaction per batch. Batch sizes: channels, XMLTV channels, films, series,
  episodes **250** rows; programmes **≤ 1,000 rows or ≤ 2 MB of text, whichever first** (beta 23 uses
  2,000 rows regardless of size: with 2,150-character descriptions that is 4.3 million characters,
  4–9 MB of strings per batch depending on string compression). Tune so a batch commits within about 100 ms on the low-end box — short transactions keep
  the player's progress writes and other writers from waiting.
- **SRC-L-10** Staging writes are plain inserts (conflict ignore) into a snapshot nobody reads;
  upserts only where the update strategy needs them (SRC-FR-95). Plain inserts were several times
  cheaper than upserts for programmes.

### 9.4 Store only what is needed

- **SRC-L-11** **Filter while parsing (OwnTV item 9).** The parser receives the keep rule (EPG-id
  set + time window + catch-up rule) and decides on the `<programme>` start tag's `channel`, `start`
  and `stop` attributes; a programme that will not be kept is skipped without reading its children
  or hashing it. Beta 23 builds every programme (strings, categories, SHA-256) and discards most of
  them afterwards. If no ids can be determined, keep everything rather than an empty guide.
- **SRC-L-12** **Past only for catch-up channels (OwnTV item 10)** — SRC-FR-83. OwnTV measured a
  7-day history for every channel at 38.9 s of a 112 s sync on 7,083 channels. Large keep-sets go to
  SQL through a temporary table, never as bound parameters (SQLite's 999-parameter limit on older
  Android).
- **SRC-L-13** Do not store what is never read: the XMLTV channel icon (beta 23 stores it and never
  uses it); for Xtream, store `stream_id` + extension per row and build the stream address at play
  time from the source's encrypted credentials, instead of an encrypted full address per row (today
  `version:IV hex:ciphertext hex`, about 240 characters for a 90-character address, and one AES-GCM
  operation with a new cipher instance per channel, film and episode — 56,164 + 200,000 cipher calls
  per full sync). M3U addresses are arbitrary and stay encrypted per row
  ([Security](73-security-privacy.md) decides the cipher; reuse one cipher instance per thread with a
  fresh IV per value rather than creating one per call).
- **SRC-L-14** Programme id as a 64-bit integer hash instead of a 16-character hex string (smaller
  row and key); compute stream tags (SRC-FR-103), normalised names and group keys once at import.
- **SRC-L-15** Only changed Xtream rows are written (SRC-FR-95), so a daily refresh of an unchanged
  200,000-film catalogue writes almost nothing and invalidates almost nothing.

### 9.5 Activation and cleanup without long locks

- **SRC-L-16** Activation only switches the active snapshot id and the refresh state (a tiny
  transaction). The previous snapshot's rows are deleted **afterwards**, in the background, in
  batches of about 5,000 rows per transaction. Beta 23 deletes the previous 56,164 channels or
  165,600 programmes inside the activation transaction, so the write lock is held for the whole
  delete (not yet measured on the low-end box).
- **SRC-L-17** Cleanup deletes only snapshots that are neither active nor registered as staging
  (each import registers its snapshot id when it starts and removes it when it ends); stale
  registrations from a killed process are swept at start-up (§8). This makes "never
  delete another import's staging rows" a data rule, not only a lock rule.

### 9.6 Indexes for bulk inserts (OwnTV item 11)

- **SRC-L-18** No redundant indexes: `(sourceId, snapshotId)` duplicates the primary-key prefix on
  `iptv_channels`, `xmltv_channels`, `tv_programmes`, `vod_movies` and `vod_series` — each costs one
  extra B-tree insert per row for nothing.
- **SRC-L-19** Programmes: make the read key the primary key — `(sourceId, snapshotId,
  xmltvChannelId, startEpochMillis, programmeId)` — so the guide's time-window read is a key-range
  scan within the active snapshot and the table has no secondary index (beta 23: primary key plus two
  secondary indexes, and the lookup index lacks the snapshot id). Plan/04 confirms the shape
  (a `WITHOUT ROWID` table is a candidate).
- **SRC-L-20** When a bulk table is empty (fresh install, after Clear all guide data), drop its
  secondary indexes before the first import and recreate them once after activation, in the
  background. Never drop an index that another source's live rows are being read through.
- **SRC-L-21** Write in the order of the primary key where the input allows (XMLTV feeds are
  usually grouped by channel and ordered by time), so inserts append to the B-tree.

### 9.7 Statistics

- **SRC-L-22** ANALYZE after every activation (Room never runs it; the planner guessed join orders
  and Continue watching once took 5.5 s, [lessons 2.1](../plan/08-lessons-learned.md)), but: only
  the tables the import touched (`ANALYZE <table>`), with `PRAGMA analysis_limit=400` where SQLite is
  3.32 or newer (Android 12+), off the launch path, not while a video plays (deferred to the next
  idle moment). Log its duration. Beta 23 runs a full-database ANALYZE after every import.

### 9.8 Never disturbing playback or the UI

- **SRC-L-23** **UI observers wake once per import, not per batch.** Screens re-read bulk tables
  when the source's generation (`import_state`) changes, through a narrow observed query, never by
  observing the bulk tables themselves; staged rows and cleanup deletes do not wake them. Room
  re-runs a flow for every write to a table it reads, so beta 23 screens re-queried during imports;
  the guide roster folds those bursts with a `SELECT 0` invalidation query and `conflate()`.
- **SRC-L-24** Force WAL journal mode explicitly: Room's automatic mode falls back to a rollback
  journal on devices that report low RAM (typically 1 GB devices), where a writer blocks every
  reader.
- **SRC-L-25** **Expensive work waits for idle playback (OwnTV item 4).** Automatic imports and
  ANALYZE never start while a player is active; an import already running when playback starts
  continues at background priority with its writer pausing at least 200 ms between batches
  (starting value) until playback stops. Probes such as an Xtream account check never run in the
  background while something plays.
- **SRC-L-26** Automatic refresh also yields to a viewer browsing the app (beta 23 rule, SRC-FR-98):
  a Shield trace caught the periodic playlist refresh encrypting thousands of addresses while the
  viewer switched film groups.
- **SRC-L-27** Settings never composes or measures more than the visible source rows; progress
  text updates at most twice a second.

### 9.9 Places the current app was slow or ran out of memory, and the rule

| Where | What happened | Rule |
|---|---|---|
| Xtream film list | refused at a 64 MB whole-body cap on a 56,835-channel provider | stream element by element; 1 GiB only as a runaway guard (SRC-L-03) |
| Guide import | 134,000 programmes in 35 s, most for channels nobody had | filter while parsing (SRC-L-11) |
| Guide parser | one digest and 32 formatter calls per programme | one digest per parse, hex by table (SRC-FR-66); skip before hashing (SRC-L-11) |
| Film identity / metadata queue | loaded the 200,000-film catalogue whole; process killed every ~4 minutes | page 2,000 in key order (SRC-L-06) |
| Periodic playlist refresh | per-row encryption competing with the viewer switching groups | yield to the foreground; no per-row encryption for Xtream (SRC-L-13, SRC-L-26) |
| Activation | the whole previous snapshot deleted inside the write transaction | switch, then sweep in batches (SRC-L-16) |
| M3U catalogue | every series and episode held until the playlist ends; all films of the snapshot read at once for identity aliases | stream episodes, page follow-ups (SRC-L-05, SRC-L-06) |
| Xtream live channels | the whole live-stream array held before writing | streaming reader, batches (SRC-L-03) |
| Planner | no statistics after imports | ANALYZE per touched table after activation (SRC-L-22) |
| Guide roster | observers re-ran per import batch | generation-based re-reads (SRC-L-23) |

## 10. Lessons from the current app

1. **Two imports of one source must never overlap** (23 Sept 2026, commit e300dfd, ledger
   `SOHVA_SPORT_USER_REPORTS.md` "one import of a kind per source"): lock per source and kind
   (SRC-FR-75), counts from what was activated (SRC-FR-34), and cleanup that cannot touch a staging
   snapshot (SRC-L-17). The remedy on older builds was one more import with nothing else importing.
2. **Keep the working guide/playlist when the answer is empty or wrong** (commits b9d1341, a6674f9):
   a provider outage on 4 Sept 2026 emptied the guide of two playlists and reported success; a
   sign-in page imported as hundreds of "Kanava n" channels. Guards SRC-FR-52, SRC-FR-80 — and the
   rebuild extends them to Xtream (beta 23 has none there).
3. **Introduce the app by name** (a6674f9): panels drop the HTTP library's agent. Beta 23 still sends
   it on Xtream API calls; the rebuild does not (SRC-FR-45).
4. **BOM, compression and encodings** (beta 17 hotfix): give the XML parser bytes, not a UTF-8
   reader; sniff gzip by magic bytes; test UTF-16 and ISO-8859-1 feeds.
5. **One bad programme must not discard the guide** (d1401cc): skip malformed timestamps and records.
   The rebuild also skips malformed elements instead of failing the document where the parser can
   recover.
6. **Cancellation is not failure** (d1401cc): catching `Throwable` recorded a stopped refresh as
   failed; staged rows must still be discarded under a non-cancellable section. Three of five
   importers still have the bug (SRC-FR-78).
7. **Failures must speak the interface language and survive builds** (a6674f9, 87af198): messages
   travel as resources; stored failures keep resource and arguments; "error (1)" told testers
   nothing (SRC-FR-104…105). Store resource names, not ids.
8. **Sync a new source immediately** (9c7e118): two testers read an empty guide as a failed sync
   when nothing had been imported. Keep; and fix the Test-connection path that defeats it
   (SRC-FR-26).
9. **A catalogue request right after playlist and guide is often refused** (87af198): one retry after
   20 s during a sync-now.
10. **Stream the catalogue** (8ad18c8) and **write only the guide the channels can show** (00f9c4d).
11. **ANALYZE after imports** (07b1eda): without statistics a fresh install planned every query blind.
12. **Settings-screen-scoped imports are cancelled by navigation** and **"Sync everything" frees the
    buttons while its background import runs** — the combination produced lesson 1. One runner for
    every import (SRC-FR-94).
13. **Hard-coded Finnish fallbacks** "Kanava n" and "Jakso n" and the untranslated "−30 min"/"+30 min"
    labels and "IPTV n"/"Xtream n" defaults: translate them.
14. **Episodes outside the snapshot model**: an M3U catalogue import that fails after writing episodes
    leaves them behind; a snapshot column or staging table fixes it (SRC-L-05).
15. **Small bugs found by reading the beta 23 code for this spec** (not yet reported by testers): `720p`/`576p`/`480p`
    also read as "720 FPS" etc. (SRC-FR-102); an invalid series id surfaces as a resource number
    (§7); `Test connection` saves the source and so defeats the first sync (SRC-FR-26); a damaged
    source store reads as "no sources" (SRC-FR-109). Each needs a regression test that fails on the
    beta 23 behaviour.
16. **Keep**: staged snapshots with atomic activation; per-source programme storage; the `get.php`
    Xtream detection; the 12 h / 8 day window as the baseline for the new catch-up rule; test
    buttons that explain failures; the security note beside the credential fields; encrypted source
    storage with a versioned codec.

Open questions (for the owner)
- **xz-compressed guides**: not supported in beta 23 (only gzip). Add xz (a decompressor library of
  roughly 100 KB) or keep gzip only?
- **`url-tvg` / `x-tvg-url` in the `#EXTM3U` header and `#EXTGRP` lines** are ignored today. Should
  a playlist's own guide address fill an empty XMLTV field, and `#EXTGRP` supply a missing group?
- **Past-programme numbers** for SRC-FR-83: proposed — catch-up channels keep back to
  min(catch-up days, 24 h + 30 min guide reach); other channels keep programmes that ended within the
  last 3 hours (two guide pages). Beta 23 keeps 12 h for all.
- **Changing an existing source** (address, credentials, scope): start a sync on save? Beta 23 does
  not; the viewer must press Sync everything.
- **Changing the scope**: remove the rows of kinds no longer imported (films after switching to Live
  TV, channels after switching to VOD only)? Beta 23 keeps them visible.
- **Remove source** has no confirmation step (design/screens/settings.md §5 raises the same
  question). Add one?
- **Clear all guide data** deletes channels, films, series and watch progress, while its text says
  "Removes every stored programme". Narrow the action to the guide, or reword it?
- **Connection limit from the provider**: should Test connection offer to set the limit to the
  server's `max_connections`?
- **Import time budgets** (§9: 60 s / 90 s / 180 s on the low-end box) — confirm in plan/07.
- **Size limits**: cap playlist/guide downloads (beta 23 has none) and programme descriptions or
  plots (no cap; the stress fixture uses 2,150 characters)?
- **Xtream in-place updates** (SRC-FR-95) in M1, or replace-everything first and in-place later?
- **Shared guide downloads**: when several sources use one XMLTV address, download once per sync to a
  temporary file (renamed only after a clean parse, stale temporary files removed only when older
  than the refresh interval — OwnTV item 8) and parse it per source?
- **Priority**: expose a source order control, or keep ordering by name?
- **Content kind by whole words**: classify M3U entries by whole-word hints so live groups such as
  "Showtime" or "Filmbox" stay in the guide? It changes what existing sources import.
- **Category-first catalogue loading** (OwnTV item 4: serve the category the viewer is looking at
  first while the rest drains) — in scope for the rebuild?

## 11. Acceptance tests

Unit (JVM; MockWebServer for HTTP; kxml2 as the pull parser in JVM tests)
- Address policy: http/https any case accepted and trimmed; missing scheme, other schemes, missing
  host, spaces rejected with the labelled message; Xtream trailing slashes removed, username trimmed,
  password untouched; blank XMLTV → none.
- Model and codec: round trip of M3U and Xtream sources; `toString` never contains credentials;
  duplicate ids, unknown enums, corrupted Base64, trailing bytes rejected; version 1 → BOTH, versions
  1–2 → offset 0; offsets outside ±720 or off the 30-minute step rejected; 101 sources rejected.
- M3U parser: quoted attributes with commas inside quotes; `#EXTVLCOPT` agent and referrer;
  address `|` headers override `#KODIPROP` and are URL-decoded; `#EXTHTTP` JSON headers; an
  address-only playlist gets a stable id and the fallback name; event channels sharing a `tvg-id` stay
  distinct; `catchup`/`catchup-days`/`catchup-source`; legacy `timeshift`; live/movie/series
  classification by group, path and type hints; positive duration → movie; `tvg-chno`/
  `channel-number` whole numbers only; sign-in page, JSON error and bare sentence refused; header
  only, BOM + decorated header, bare `udp://` address accepted. New: line-length cap; translated
  fallback name.
- XMLTV parser: records streamed; BOM before the declaration; gzip + BOM; declared ISO-8859-1;
  UTF-16 LE and BE with BOM; channel without id and programme without title skipped; bad
  timestamps skipped without stopping later channels; timestamp formats (minutes, seconds, `Z`,
  `±hhmm`, missing offset rejected, date-only rejected). New: nested markup in a title skipped;
  filter-before-children (a programme for an unknown channel is never hashed — count digests).
- Throughput (printed, compared by hand): 400 channels × 7 days × 48 programmes parses in seconds.
- Xtream client: account parse and auth failure without leaking credentials; live channels with
  categories, archive days, `num`, server zone; films and series streamed in chunks; episodes sorted,
  titles cleaned, thumbnails; `xmltv.php` address; malformed array items skipped; oversize response
  rejected before parsing. New: every request carries the app agent; per-category fallback on HTTP
  512 and on a truncated array; streaming reader never materialises the live array (memory
  assertion); an invalid series id gives the localized `error_series_id_invalid`.
- `get.php` derivation: base with port and path; parameters in any case and URL-encoded; missing
  username or password → not derived.
- Import services (fake store): activation only after success; failure keeps the previous
  snapshot; a partial download is discarded; scope BOTH keeps VOD entries out of the live import;
  20,000 channels in 80 batches of 250; seven-day guide in batches within the limit; programmes for
  unknown channels and outside the window not written; empty feed and unmatched feed keep the
  previous guide; channels without EPG ids accept any guide; empty playlist and web page keep the
  previous channels; HTTP failure stored with its code; probe stops at 500 without storing;
  cancellation propagates without a failure record — **for all five importers**; after-import hook
  runs once after activation. New: Xtream empty-answer guard; past kept only for catch-up channels;
  in-place Xtream update writes nothing for an unchanged list and deletes unseen items only after a
  complete read.
- Scheduler: interval → playlist and guide period; catalogue stays 24 h; deferral only for
  automatic runs in the foreground when no first import is pending; `ALL` = playlist, guide,
  catalogue in order. New: no automatic start while playback is active.
- Connection limiter: acquire up to the limit, refuse beyond, idempotent release, per-source counts.
- Stream tags: resolution, HDR, frame rate and language tokens; no language inside a word; priority
  normalisation keeps 8 distinct codes. New: "Sport 720p" yields only "HD" (fails on beta 23).
- Stored failures: resource and arguments round trip; resource arguments; an id from another build
  or a non-`error_` entry resolves to nothing. New: names instead of ids.

Instrumented (emulator)
- `ConcurrentPlaylistImportTest` against a real database: two imports of one source at once leave
  all its channels in the rail (must fail with the lock removed).
- Settings: adding an Xtream source shows a masked password editor; Test address reads a local
  playlist and the server sees the `Sohva TV/` agent; a VOD-only M3U source shows Refresh movies and
  series but no live or guide controls; EPG correction steps by 30 minutes; fields need OK before
  editing and keep focus after Done; the refresh-interval picker persists its choice; the source page
  returns focus to its row. New: Test connection does not save; Remove source deletes every row of
  the source and cannot be undone by a running import; a switched-off source vanishes from the guide
  and returns when switched on.
- Import runner: an import started from Settings continues after leaving Settings and its progress
  shows when returning.

Manual and performance (low-end class: S905Y4 box or the `.local/slowbox` emulator stand-in)
- Import the owner-scale fixture (56,164 channels / 800 groups, 165,600 programmes, 200,000 films,
  1,500 × 12 episodes) from the local fixture server: sample the Java heap every 5 s — never above
  128 MB, import working set ≤ 16 MB; no `CursorWindow: Window is full` warning; times within the
  plan/07 budgets.
- While that import runs: play a live channel for its whole duration — no dropped-frame burst in the
  player's statistics; browse the guide with held D-pad presses — no press later than two vsyncs;
  a Perfetto trace shows the import threads at background priority and no import work on the main
  thread.
- Repeat the Xtream catalogue refresh with an unchanged provider list: almost no rows written, the
  film wall does not re-read until the import finishes.
- Kill the process mid-import: after restart the previous data is intact, the refresh state reads
  interrupted, and orphan staged rows disappear in the background.
- Real providers (the owner's, never recorded in the kit): an M3U with XMLTV, an Xtream account, a
  `get.php` address; a gzip guide; a guide with a BOM.

## 12. Reference: current code map

- `core/src/main/java/com/streammate/tv/core/model/IptvConfiguration.kt` — source model, scope, limits, legacy single-source form.
- `core/.../core/model/ChannelStreamTags.kt` — quality/language markers from channel names; sport priority normalisation.
- `core/.../core/network/IptvSourceUrlPolicy.kt` — the http(s)+host address rule.
- `core/.../core/security/IptvSourceConfigurationCodec.kt` — versioned binary codec of the source list.
- `core/.../core/security/SecretSettingsStore.kt` — encrypted source list in shared preferences; legacy migration.
- `core/.../core/error/LocalizedException.kt` — localized failures, transport wrapping, stored failure text.
- `core/.../core/database/GuideDao.kt`, `GuideEntities.kt`, `CatalogueDao.kt` — tables, snapshot activation, clear source / clear all, staged match query.
- `iptv/src/main/java/com/streammate/tv/core/network/GuideSourceClient.kt` — playlist/guide GET with the app agent, error mapping.
- `iptv/.../iptv/xmltv/CompressionAwareInputStream.kt` — gzip sniffing by magic bytes.
- `iptv/.../iptv/m3u/M3uParser.kt` — M3U directives, attributes, ids, classification, name normaliser.
- `iptv/.../iptv/xmltv/XmlTvParser.kt`, `XmlTvTimestampParser.kt` — streaming XMLTV records, timestamps.
- `iptv/.../iptv/xtream/XtreamClient.kt` — Xtream API, stream addresses, streaming arrays, 1 GiB guard.
- `iptv/.../iptv/xtream/XtreamM3uCompatibility.kt` — `get.php` → Xtream derivation.
- `iptv/.../iptv/repository/GuideImportService.kt` — M3U playlist and XMLTV guide imports, probe, guards.
- `iptv/.../iptv/repository/GuideStore.kt` — staging/activation store, refresh states, source state, health.
- `iptv/.../iptv/repository/XtreamImportService.kt` — Xtream live channels and guide hand-over.
- `iptv/.../iptv/repository/M3uCatalogueImportService.kt` — M3U films, series and episodes.
- `iptv/.../iptv/repository/CatalogueRepository.kt` — `XtreamCatalogueImportService` (films, series, episodes on demand).
- `iptv/.../iptv/repository/SourceImports.kt` — one import per source and kind.
- `iptv/.../iptv/playback/SourceConnectionLimiter.kt` — playback leases per source.
- `iptv/.../feature/settings/SettingsScreen.kt` — Playlists list, source page, actions, status and health.
- `iptv/.../feature/settings/IptvConfigurationValidator.kt`, `XtreamConfigurationValidator.kt` — form validation.
- `iptv/.../feature/settings/SettingsLabels.kt` — new ids, row subtitles, probe summary, offset format.
- `app/src/main/java/com/streammate/tv/app/GuideRefreshScheduler.kt` — periodic and sync-now WorkManager jobs, worker.
- `app/.../app/StreamMateContainer.kt` — HTTP client settings, user agent, ANALYZE hook wiring.
- `app/src/main/res/xml/network_security_config.xml` — cleartext for providers, HTTPS for the app's services.
