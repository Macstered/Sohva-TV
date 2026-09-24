# Encrypted backup and restore

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

Visuals of the section are extracted in [design/screens/settings.md](../design/screens/settings.md)
§6 "Backup & tools" (cited as **S§6**) and §5 "Dialogs" (document pickers, cited as **S§5**). The
Settings frame, rail and focus rules belong to [Settings](70-settings.md); the Maintenance group in
the same section ("Clear all guide data") is SRC-FR-42 in [Sources and import](10-sources-and-import.md).
Key material and the on-device ciphers are in [Security and privacy](73-security-privacy.md).

## 1. Summary

The viewer can save the TV's configuration to one password-encrypted file (`.smbak`) wherever the
system file picker lets them (USB drive, local storage, a cloud provider's document provider) and
restore it on the same or another TV. It carries the IPTV sources with their credentials, the
parental PIN, the settings, profiles and what each profile keeps, channel customisations and
custom channel lists, the library organisation rules and the viewer's own genre groups. It is a
configuration backup, not a data export: guide and catalogue content, watch progress, Discover,
Trakt and API keys are not in it. The owner used it on 16 September 2026 to move a Shield's setup
to the Elisa Viihde box, which is the real use: set up once, carry it to a new box.

## 2. Feature checklist

- BACKUP-01 Settings › Backup & tools shows an "Encrypted backup" group: title, description,
  password field, **Save backup**, **Restore backup**, and the warning that the password cannot be
  recovered.
- BACKUP-02 Password field: masked, edit-on-click, 8–128 characters; both buttons are disabled
  below 8 characters and while a backup operation runs.
- BACKUP-03 **Save backup** opens the system "create document" picker with the suggested name
  `sohva-tv-backup.smbak` and MIME type `application/vnd.streammate.backup`.
- BACKUP-04 The file is encrypted with a key derived from the password (PBKDF2-HMAC-SHA256,
  210,000 iterations, AES-256-GCM); the password is never stored.
- BACKUP-05 The backup carries: IPTV sources with credentials, the parental PIN, every setting
  listed in §6.2, profiles and each profile's favourites/recents/last channel/locked channels/
  allowed groups, channel customisations (name, group, hidden, order, EPG id, logo, number), custom
  channel lists and members, organisation rules with the film identities they depend on, and the
  viewer's own genre groups.
- BACKUP-06 A logo sent from a phone travels inside the backup as image bytes and is recreated on
  the target TV.
- BACKUP-07 The backup never contains Discover data, Trakt, the TMDB token, the TVmaze switch, the
  API-Sports key, watch progress, reminders, guide/catalogue content, sports match decisions, the
  interface language, the image-cache size or "Match the display to the picture" (§6.3).
- BACKUP-08 **Restore backup** opens the system "open document" picker filtered to
  `application/vnd.streammate.backup` and `application/octet-stream`.
- BACKUP-09 Restore checks the whole file (envelope, password, structure, limits) before it changes
  anything; each failure has its own message (§4.6).
- BACKUP-10 Restore replaces the sources (sources the backup lacks are deleted with all their
  data), the channel customisations, custom lists, organisation rules, all settings, profiles and
  the PIN; anything the backup does not carry stays as it was.
- BACKUP-11 After a restore the Playlists list shows the restored sources and the message "Backup
  restored. Refresh channels and the programme guide."; nothing syncs by itself and the app does
  not restart; theme, interface size and the other settings apply at once.
- BACKUP-12 Every backup written by any build since StreamMate (format versions 1 and 2) restores; settings
  the older file lacks take the defaults of §6.4 (an old backup without a colour theme restores
  Original).
- BACKUP-13 A backup of a TV with a 200,000-film catalogue stays small (tens of kilobytes) and its
  export never runs the app out of memory (the beta 17 repair, §10).
- BACKUP-14 The password field is emptied after every save or restore attempt, successful or not.
- BACKUP-15 Save shows "Encrypted backup saved" or the failure; restore shows the restored message
  or the failure (the rebuild shows both in the section itself, §5).

## 3. Entry points and navigation

- **Entry**: Settings rail › "Backup & tools" (`settings_section_backup`, ninth row, Save icon),
  selected with OK. First focus on entering the section: the password field (S§8). Restricted
  profiles reach Settings only past the parental PIN ([Profiles](04-profiles-parental.md)
  PROF-21), so backup and restore are PIN-protected when a PIN exists.
- **Focus order** inside the group: password field → Right → Save backup → Right → Restore backup.
  Down from the row goes to the Maintenance group's "Clear all guide data". Up from the password
  field leaves the pane per Settings' rules.
- **Password field**: OK opens the system keyboard (edit-on-click, `KeyboardType.Password`,
  masked with the platform bullet transformation). Back closes the keyboard and keeps the text.
- **Pickers** are system activities (DocumentsUI). Back inside a picker cancels it: nothing is
  written or read, the password is kept, and focus should return to the button that opened it
  (beta 23 does not request it explicitly; the rebuild must).
- **Back** from the pane returns to the rail (Settings' own rule, [Settings](70-settings.md)).
- **Other entry points**: none in the app. The tester documents send viewers here
  (INSTALL.md "For extra safety, use Settings > Backup & tools > Save backup"; TESTING.md
  items on theme and large-catalogue backups).

## 4. Behaviour

### 4.1 Password

- BACKUP-FR-01 The field keeps at most 128 characters: longer input is cut to its first 128
  (`take(128)` on every change, so a pasted 200-character password is silently shortened; the
  rebuild keeps the 128 limit but shows the counter or refuses the paste).
- BACKUP-FR-02 Save and Restore are enabled only when the field holds ≥ 8 characters and no
  backup operation is running (`busy`). There are no other rules (no complexity, no confirmation
  field). The label is `backup_passphrase` "Backup password (at least 8 characters)".
- BACKUP-FR-03 The encryptor independently refuses a password shorter than 8 characters with
  `error_backup_passphrase_too_short` "The backup password must be at least %1$d characters"
  (argument 8). The decryptor has no length rule.
- BACKUP-FR-04 After the picker returns a document and the operation finishes (success or
  failure) the field is emptied. If the picker is cancelled the field keeps its text.
- BACKUP-FR-05 The password is never written to storage, logs or diagnostics. It is converted to a
  character array for key derivation and the key-derivation spec's copy is cleared after use
  (`PBEKeySpec.clearPassword`). The UI string itself cannot be wiped (immutable); the rebuild keeps
  it only in the field's state and drops it after use.

### 4.2 Saving a backup

- BACKUP-FR-06 **Save backup** launches `ACTION_CREATE_DOCUMENT` (`CreateDocument` contract) with
  MIME `application/vnd.streammate.backup` and suggested title `sohva-tv-backup.smbak`. The viewer
  may rename it. A `null` result (cancel) does nothing.
- BACKUP-FR-07 With a document chosen, the app sets `busy`, then on a background (IO) thread:
  1. loads the source list from the encrypted secret store (decrypted in memory);
  2. reads the current preferences snapshot and, for profile `default` and every named profile,
     that profile's kept data;
  3. reads the parental PIN digits (decrypted) or none;
  4. reads the channel customisation snapshot: every row of `channel_preferences`,
     `custom_channel_lists` and `channel_list_members`, all `organization_rules`, and only the
     `organization_aliases` rows that customised film rules depend on (§4.4);
  5. for each channel preference whose logo is a file this TV's logo store wrote, reads the PNG
     bytes;
  6. builds the JSON payload (§7.2), UTF-8 encodes it, encrypts it (§7.1);
  7. opens the document for writing in truncate mode (`"wt"`), writes the whole envelope, flushes
     and closes.
- BACKUP-FR-08 Success message: `settings_backup_saved` "Encrypted backup saved". Failure message:
  the error's localised text, or the redacted exception message, or `error_unknown` "Unknown
  error". Beta 23 computes counts (sources, customised channels, lists) but shows none of them.
- BACKUP-FR-09 Size limits on save: plaintext over 8,388,608 bytes (8 MiB) fails with
  `error_backup_too_large` "The backup is too large". The source list may hold at most 100 sources
  (codec rule, [Sources and import](10-sources-and-import.md) SRC-FR-108).
- BACKUP-FR-10 A failure after the picker created the document leaves an empty or partial file
  (beta 23 opens the document only after encryption, so an encryption failure leaves the empty
  file the picker created). Rebuild: on failure, try `DocumentsContract.deleteDocument` on the
  URI and say in the message when the file could not be removed.

### 4.3 What goes in, what stays out

- BACKUP-FR-11 Included, exactly: see the payload table (§6.2). Summary: sources (all fields
  including URLs, usernames and passwords), PIN, preferences (the list in §6.2), profiles, each
  profile's kept data, channel preferences with logo bytes, custom lists and members,
  organisation rules and the customised film identities, own genre groups.
- BACKUP-FR-12 Excluded (§6.3): Discover (addons, catalog order and visibility, Library, history,
  progress, subtitle settings), Trakt accounts and caches, TMDB token and switch, TVmaze switch,
  API-Sports key, VOD and catch-up watch progress, reminders, sports caches and confirmed/rejected
  stream pairings, imported channels/programmes/films/series, image cache, interface language,
  image-cache size, update state, and the `auto_frame_rate` switch. The last one is an accident
  (the preference key list is written three times and this key was forgotten,
  [plan/03](../plan/03-architecture.md) §3.3); the rebuild adds it (§6.5).

### 4.4 Organisation data in the backup (the beta 17 repair)

- BACKUP-FR-13 All `organization_rules` rows are included (room, sourceId, groupKey, itemKey,
  enabled, sortMode, position).
- BACKUP-FR-14 Of `organization_aliases` (the film identity index that maps provider items and
  work keys to one identity; it has one row per imported film, 150,000+ on a large catalogue) only
  the alias **families** that a MOVIES item rule needs are included, selected in SQL before any
  row reaches app memory:

  ```sql
  SELECT * FROM organization_aliases WHERE identity IN (
      SELECT itemKey FROM organization_rules WHERE room='MOVIES' AND itemKey!=''
      UNION
      SELECT a.identity FROM organization_aliases a
      INNER JOIN organization_rules r ON r.itemKey=a.alias
      WHERE r.room='MOVIES' AND r.itemKey!=''
  )
  ORDER BY alias
  ```

  The second branch handles older rules that name an alias directly instead of the canonical
  identity. Indices used: `organization_rules(itemKey)`, `organization_aliases` primary key
  `alias` and index `identity`.
- BACKUP-FR-15 The film identities that are not included are rebuilt by the regular
  film-identity pass after the next import: restore clears the identity-completion marker
  (`movie_identity_mark`, a DataStore key wiped with the rest of the preferences, §4.5 step 8), so
  the pass runs again ([Library organisation](42-library-organization.md)).
- BACKUP-FR-16 Export does not modify the database.

### 4.5 Restoring a backup

- BACKUP-FR-17 **Restore backup** launches `ACTION_OPEN_DOCUMENT` (`OpenDocument` contract) with
  the MIME filter `["application/vnd.streammate.backup", "application/octet-stream"]` (files on
  removable media usually carry the generic type). Cancel does nothing.
- BACKUP-FR-18 With a document chosen, the app sets `busy` and on a background thread:
  1. reads the file, refusing it as soon as it passes 10,485,760 bytes (10 MiB) with
     `backup_error_too_large` "The backup is too large";
  2. decrypts it (§7.1) with the typed password;
  3. parses the UTF-8 JSON and validates **everything** (§7.2, §7.3): nothing has been changed
     yet, so any failure leaves the TV exactly as it was;
  4. deletes every current source whose id is not in the backup, with all its data (channels,
     programmes, XMLTV channels, films, series, episodes, playback progress, channel preferences
     and list members of that source, sports stream decisions, import and refresh state, source
     state — the same cascade as removing a source, [Sources](10-sources-and-import.md));
  5. replaces the encrypted source list with the backup's sources and upserts each one's
     `iptv_source_state` row (name, type, enabled, connection limit, priority, EPG offset);
  6. in one database transaction deletes all channel preferences, custom lists and list members
     and inserts the backup's; then, in a second transaction, deletes **all** organisation rules
     and **all** aliases and inserts the backup's (so the uncustomised identity index is empty
     until the identity pass rebuilds it);
     - logos: a preference with `customLogoData` has its bytes decoded, re-bounded (≤ 256 px a
       side, ≤ 2,000,000 input bytes) and written to this TV's logo store under a new file
       address, which becomes `customLogoUrl`; without bytes (or when they do not decode as an
       image), any non-`file:` address is kept as is,
       a `file:` address is kept only if that file exists on this TV, otherwise the logo is
       dropped;
  7. stores the backup's PIN when it has one;
  8. replaces **the whole** preferences store (DataStore `streammate_preferences` is cleared and
     rewritten from the backup, §6.4). `parental_pin_configured` becomes "backup has a PIN"; locked
     channels are written only when the backup has a PIN;
  9. writes each profile's kept data (`profileData`), again locked channels only with a PIN; an
     empty allowed-groups set removes the restriction key for that room;
  10. clears the TV's PIN when the backup has none and the TV had one;
  11. runs the one-time legacy organisation migration if it has not run (hidden-category
      preferences become disabled group rules, marker rule `LIVE` / `@legacy-v1`).
- BACKUP-FR-19 On success the Settings screen reloads the source list, selects the first source
  (or a fresh M3U draft when there is none) and sets `settings_backup_restored` "Backup restored.
  Refresh channels and the programme guide." No import, refresh or sync is started; sources new
  to this TV stay empty until the viewer syncs them or the periodic refresh runs
  ([Sources](10-sources-and-import.md)).
- BACKUP-FR-20 What changes live: the preference flows emit the restored values, so colour theme,
  interface size, channel numbers, subtitle style, remote mappings, time zone and the active
  profile apply immediately (the active profile switches without the "Who is watching" prompt);
  the refresh schedule is re-enqueued when the interval differs; the metadata language becomes
  the default for new lookups (existing enrichment is **not** reset, unlike choosing a language
  in the picker, [Settings](70-settings.md)). Nothing restarts the process or the activity
  (the interface language is not in the backup, so no locale restart).
- BACKUP-FR-21 Merge or replace: every included area is **replaced**, never merged. Data not in
  the backup is **kept**: watch progress of sources that survive, Discover, Trakt, reminders,
  keys, image cache, interface language. Per-profile preference keys of profiles that are not in
  the backup are gone (the store was cleared), while their database rows (watch progress per
  profile) stay orphaned; the rebuild deletes a profile's rows when a restore removes the profile.

### 4.6 Messages

All errors are shown through the same status text as success (§5). Keys and English text:

| Condition | Key | Text |
|---|---|---|
| Saved | `settings_backup_saved` | Encrypted backup saved |
| Restored | `settings_backup_restored` | Backup restored. Refresh channels and the programme guide. |
| Document cannot be opened | `backup_error_open` | Could not open the backup file |
| File or plaintext too large | `backup_error_too_large` / `error_backup_too_large` | The backup is too large |
| Password < 8 on save | `error_backup_passphrase_too_short` | The backup password must be at least %1$d characters |
| Magic mismatch | `error_backup_not_streammate` | This file is not a Sohva TV backup |
| Envelope version ≠ 1 | `error_backup_version_unsupported` | This backup version is not supported |
| Iterations outside 100,000–1,000,000 | `error_backup_key_format` | The backup key format is invalid |
| Salt/IV/ciphertext length wrong | `error_backup_structure` | The backup structure is invalid |
| Bytes after the ciphertext | `error_backup_trailing_data` | The backup has extra data at the end |
| GCM tag fails | `error_backup_wrong_passphrase` | Wrong password, or the backup is damaged |
| `formatVersion` not 1–2 | `backup_error_version` | The backup version is not supported |
| PIN not 4–8 digits | `backup_error_pin` | The parental-control PIN in the backup is invalid |
| > 100,000 channel preferences | `backup_error_too_many_preferences` | Too many channel settings |
| > 1,000 lists | `backup_error_too_many_lists` | Too many channel lists |
| > 500,000 list members | `backup_error_too_many_members` | Too many channel-list members |
| Duplicate channel preference | `backup_error_duplicate_preference` | Duplicate channel setting |
| Preference of a source not in the backup | `backup_error_missing_source` | A channel setting refers to a missing source |
| Duplicate list id | `backup_error_duplicate_list` | Duplicate channel list |
| Member of an unknown list | `backup_error_missing_list` | A channel-list member refers to a missing list |
| Duplicate member | `backup_error_duplicate_member` | Duplicate channel-list member |
| Unknown startup screen | `backup_error_startup` | Unknown startup screen |
| Unknown remote key mode | `backup_error_remote` | Unknown remote-control setting |
| Required field absent | `backup_error_missing_field` | The backup is missing %1$s |
| Required string blank | `backup_error_blank_field` | Backup field %1$s is blank |
| String > 20,000 characters | `backup_error_long_field` | Backup field %1$s is too long |

Several validations in beta 23 raise **untranslated English** text instead ("Invalid followed
sport", "Invalid playlist and EPG refresh interval", "Invalid playback buffer profile", "Invalid
playback seek step", "Invalid subtitle size/colour/background", "Invalid playback reconnect
policy", "Invalid preferred catalogue copy", "Missing organization field", "Invalid organization
preference", "Organization backup is too large", "Duplicate organization preference/alias",
"Invalid organization alias", the source codec's "Invalid IPTV source payload" family, and JSON
parser messages). Rebuild: every restore failure maps to a localised key; the rebuild may reuse
`error_backup_structure` for all structural faults and name the field with
`backup_error_missing_field` / `backup_error_long_field`.

## 5. Screen anatomy

No screenshot of this section exists. Layout per S§6 "Backup & tools", S§3 group vocabulary:

- **Group 1** (no heading row; hairline, padding 14/14, `spacedBy 10`):
  - title `backup_title` "Encrypted backup", 16 sp Bold, `textPrimary`;
  - description `backup_description` "Sources, credentials, channel settings, lists and parental
    controls, protected with your password.", 13 sp `textMuted`;
  - Row (`fillMaxWidth`, `spacedBy 10`, centred vertically): compact `TvUrlField` (weight 1,
    leading Info icon, password keyboard, masked, edit-on-click, label `backup_passphrase`), then
    compact `TvActionButton` `backup_save` "Save backup", then compact `TvActionButton`
    `backup_restore` "Restore backup" (no icons; disabled look `surfaceSubtle`/`textDisabled`);
  - warning `backup_warning` "The password is not stored and cannot be recovered.", 12 sp
    `textMuted`.
- **Group 2** "MAINTENANCE" (`maintenance_title`): help `guide_clear_all_help` (12 sp `textMuted`,
  weight 1) and the danger button `guide_clear_all` "Clear all guide data" (Delete icon, compact),
  status `guide_cache_cleared` in `focus` 12 sp — behaviour in SRC-FR-42.
- **Status (flaw)**: beta 23 writes the backup messages to the Playlists status line, which is
  composed only in the Playlists section, so the viewer **never sees** "Encrypted backup saved",
  the restore message or any backup error while in this section (S§9). Rebuild: a status line
  under the warning in Group 1, 12 sp, `focus` for success and `danger` for failures, with a test
  tag `settings-backup-status`; while `busy`, it reads "Saving the backup…" / "Restoring the
  backup…" (proposed new keys `backup_status_saving`, `backup_status_restoring`; owner to approve
  wording and translations).
- Test tags to keep: `settings-backup-passphrase`, `settings-backup-export`,
  `settings-backup-restore`, `settings-clear-guide`, `settings-maintenance-status`.
- Pickers: system UI; nothing of the app is drawn over them (S§5).

## 6. Data

### 6.1 What is stored

Nothing persistent. The `.smbak` file lives where the viewer saved it; the app keeps no copy, no
record of the location, no password and no hash of it. Transient memory only during an operation.

### 6.2 Payload contents (format version 2)

| JSON key | Content | Source on the TV |
|---|---|---|
| `formatVersion` | `2` | constant |
| `exportedAtEpochMillis` | wall-clock time of export | written, never read |
| `sources` | Base64 string of the source codec (MAGIC `0x53544D53`, version 3; ids, names, type, enabled, connection limit, priority, M3U/XMLTV URLs, Xtream base/username/password, import scope, EPG offset) | encrypted secret store; codec = SRC-FR-108 |
| `parentalPin` | the PIN digits (4–8) or `null` | encrypted secret store |
| `preferences` | object, §6.4 | DataStore `streammate_preferences` (active profile's view) |
| `profileData` | object keyed by profile id (`default` plus each named profile) → `favouriteEventIds`, `favouriteChannelIds`, `recentChannelIds`, `lastChannelId`, `lockedChannelIds`, `allowedLiveGroupKeys`, `allowedMovieGroupKeys`, `allowedSeriesGroupKeys` | DataStore per-profile keys |
| `channelPreferences` | array of `{channelId, sourceId, customName, customGroupTitle, hidden, sortOrder, manualXmltvChannelId, updatedAtEpochMillis, customLogoUrl, channelNumber, customLogoData?}` | `channel_preferences`; logo bytes from `files/channel-logos` |
| `channelLists` | array of `{listId, name, sortOrder, updatedAtEpochMillis}` | `custom_channel_lists` |
| `channelListMembers` | array of `{listId, channelId, sortOrder}` | `channel_list_members` |
| `organization` | `{rules:[{room, sourceId, groupKey, itemKey, enabled, sortMode, position}], aliases:[{alias, identity}]}` | `organization_rules`, customised `organization_aliases` (§4.4) |

`preferences` keys written by beta 23 (defaults in §6.4): `timeZoneId`, `timeZoneFollowsDevice`,
`favouriteEventIds`, `favouriteChannelIds`, `recentChannelIds`, `profiles` (array of
`{id, name, color}`), `activeProfileId`, `askProfileAtStart`, `lastChannelId`,
`lastGuideSourceId`, `startupScreen`, `lockedChannelIds`, `remoteChannelKeyMode`,
`remoteMappings` (sorted array of `BUTTON.GESTURE=ACTION` strings, [Remote buttons](31-remote-button-mapping.md)
REMOTE-FR-35), `metadataLanguage`, `interfaceScale`, `colorTheme` (stored value, e.g.
`original`, `nordic_slate`, `cozy_hearth`, `cyber_plum`, `nord`, `everforest`, `kanagawa`),
`followedSports`, `followedCompetitionKeys`, `sportsChannelPriority`,
`playlistEpgRefreshInterval`, `playbackBufferProfile`, `playbackSeekStep`, `subtitleTextSize`,
`subtitleTextColor`, `subtitleBackground`, `playbackReconnectPolicy`,
`autoPlayNextEpisodeEnabled`, `pictureInPictureEnabled`, `editorsShowHidden`,
`showChannelNumbers`, `preferredCatalogueCopy`, `hiddenLiveCategories`, `hiddenMovieCategories`,
`hiddenSeriesCategories`, `preferredAudioLanguage`, `secondaryAudioLanguage`,
`preferredSubtitleLanguage`, `secondarySubtitleLanguage`, `customCatalogueGroups` (array of
`{id, name, genres:[wire values], fromYear?, toYear?, minRating?}`). The favourites, recents, last
channel and locked channels at this level are the **active** profile's; the same data appears
again under `profileData`.

### 6.3 Not in the backup

| Data | Where it lives | Why it matters |
|---|---|---|
| Discover addons, catalog order/visibility, Library, history, progress, subtitle settings | `sohva-addon*.db`, `sohva_addon_ui` | Documented limit in every tester document; [Discover](50-discover-addons.md) |
| Trakt tokens, account, caches | `trakt_accounts`, `trakt_state` | A new TV needs a new sign-in ([Trakt](51-trakt.md)) |
| TMDB token/switch, TVmaze switch, API-Sports key | encrypted secret store | Re-enter on the new TV (phone setup helps) |
| VOD/catch-up watch progress, watched marks | `playback_progress` | Kept on the TV for surviving sources |
| Reminders | reminders table | [Reminders](22-catchup-and-reminders.md) |
| Stream pairing decisions, sports caches | `event_channel_decisions`, `sports_api_cache` | [Sohva Sport](60-sohva-sport.md) |
| Imported content, programme guide, metadata cache, image cache | database, `cache/` | Re-imported by a sync |
| Interface language | `streammate_locale` / system per-app locale | Needs an activity restart; kept out deliberately |
| Image-cache size | `streammate_artwork_cache` | — |
| `auto_frame_rate` ("Match the display to the picture") | DataStore | **Accident**: reset to its default (on) by every restore |
| Other DataStore keys (`reminder_overlay_asked`, `manager_group_*`, `manager_source_*`, `movie_identity_mark`) | DataStore | Wiped by the restore's `clear()` |

### 6.4 Restore defaults and validation per preference

| Key | Required | Absent → | Invalid → |
|---|---|---|---|
| `timeZoneId` | yes, ≤ 100 chars | — | missing/blank/long field error |
| `timeZoneFollowsDevice` | no | `false` (old backups restore the zone they carry) | parse error |
| `favouriteEventIds`, `favouriteChannelIds`, `lockedChannelIds` | yes (arrays of non-blank strings ≤ 20,000) | — | field errors |
| `recentChannelIds` | yes | — | first 20 kept |
| `profiles` | no | none (only the implicit `default`) | ids ≤ 64 chars; names cut to 24; colour clamped 0–5 (absent → 0); duplicates by id dropped; first 6 kept |
| `activeProfileId` | no (≤ 64) | `default` | — |
| `askProfileAtStart` | no | `true` | — |
| `lastChannelId` | no | none | — |
| `lastGuideSourceId` | no (≤ 128) | none | — |
| `startupScreen` | yes: `HOME`, `GUIDE`, `LAST_CHANNEL` | — | `backup_error_startup` |
| `remoteChannelKeyMode` | yes: `DPAD_AND_CHANNEL_KEYS`, `CHANNEL_KEYS_ONLY` | — | `backup_error_remote` |
| `remoteMappings` | no | defaults implied by `remoteChannelKeyMode` (`CHANNEL_KEYS_ONLY` → Up/Down press do nothing) | unknown entries dropped |
| `metadataLanguage` | no | `fi-FI` when the app's own chosen interface language is Finnish, else `en-US` (a TV following the system language gets `en-US`) | unsupported tag → same default |
| `interfaceScale` | no | `NORMAL` | unknown → `NORMAL` (`COMPACT`, `SMALL`, `SMALLER` valid) |
| `colorTheme` | no | `original` | unknown → `original` |
| `followedSports` | no | `FOOTBALL`, `ICE_HOCKEY`, `AUSTRALIAN_FOOTBALL` | unknown sport → restore fails |
| `followedCompetitionKeys` | no | the default competition set ([Sohva Sport](60-sohva-sport.md)) | — |
| `sportsChannelPriority` | no | empty | normalised: known codes only, distinct, first 8 |
| `playlistEpgRefreshInterval` | no | `TWENTY_FOUR_HOURS` (`ONE_HOUR`, `TWO_HOURS`, `FOUR_HOURS`, `TEN_HOURS` valid) | restore fails |
| `playbackBufferProfile` | no | `DEFAULT` (`LOW_LATENCY`, `STABILITY`) | restore fails |
| `playbackSeekStep` | no | `TEN_SECONDS` (`THIRTY_SECONDS`, `ONE_MINUTE`, `TWO_MINUTES`) | restore fails |
| `subtitleTextSize` / `subtitleTextColor` / `subtitleBackground` | no | `FOLLOW_TV` | restore fails |
| `playbackReconnectPolicy` | no | `STANDARD` (`PERSISTENT`) | restore fails |
| `autoPlayNextEpisodeEnabled` | no | `true` | — |
| `pictureInPictureEnabled` | no | `false` | — |
| `editorsShowHidden` | no | `true` | — |
| `showChannelNumbers` | no | `true` | — |
| `preferredCatalogueCopy` | no | `NONE` (`FINNISH_AUDIO`, `FINNISH_SUBTITLES`, `LARGEST_PICTURE`) | restore fails |
| `hidden*Categories` | no | empty | — |
| audio/subtitle language slots | no | none (Automatic) | — |
| `customCatalogueGroups` | no | none | lenient: a group without id or name, or with no rule (no genre, years or rating), is dropped; unknown genres dropped; id/name cut to 20,000; at most 24 stored |
| any other key | — | ignored (for example the retired `catalogueBrowserV2Enabled`, which is also not written back) | — |

Restore writes: `profiles`, `active_profile_id` (removed when `default`), `ask_profile_at_start`,
`time_zone` (removed when following the device), the active profile's favourites/recents/last
channel, `last_guide_source_id`, `startup_screen`, `parental_pin_configured`,
`remote_channel_key_mode`, `remote_mappings`, `metadata_language`, `interface_scale`,
`color_theme`, `auto_frame_rate` (always the default `true`), the playback, subtitle, catalogue,
sports and language keys, `custom_catalogue_groups` (first 24), and locked channels only with a
PIN ([Settings](70-settings.md) holds the key names and defaults).

### 6.5 Rebuild rules for the data

- BACKUP-FR-22 One declarative table of backed-up preferences drives read, write and restore, so a
  new setting cannot be forgotten; a unit test fails when a DataStore key is neither in the table
  nor on an explicit exclusion list.
- BACKUP-FR-23 Add `autoFrameRateEnabled` (optional; absent → keep the TV's current value rather
  than resetting it). Keep `formatVersion` at 2 while only **optional** keys are added: beta 23
  ignores unknown keys and refuses `formatVersion` > 2, so a rebuild backup stays restorable on
  beta 23. Raise it only for a change old readers must refuse (owner decision, §10 open
  questions).
- BACKUP-FR-24 Restore of preferences replaces the backed-up keys and leaves device-local keys
  (`movie_identity_mark` excepted, which must still be cleared) in place instead of clearing the
  whole store.

## 7. External interfaces

### 7.1 The `.smbak` envelope (cipher version 1)

Big-endian 32-bit integers (Java `DataOutputStream`):

| Offset | Size | Field | Value / rule on read |
|---|---|---|---|
| 0 | 4 | magic | `0x534D424B` ("SMBK"); else `error_backup_not_streammate` |
| 4 | 4 | version | `1`; else `error_backup_version_unsupported` |
| 8 | 4 | iterations | written `210000`; accepted 100,000–1,000,000, else `error_backup_key_format` |
| 12 | 4 | salt length | must be exactly 16, else `error_backup_structure` |
| 16 | 16 | salt | `SecureRandom` |
| 32 | 4 | IV length | must be exactly 12, else `error_backup_structure` |
| 36 | 12 | IV | `SecureRandom` |
| 48 | 4 | ciphertext length N | 16 … 10,485,760, else `error_backup_structure` |
| 52 | N | AES-GCM ciphertext followed by the 16-byte tag | — |
| 52+N | — | end of file | any further byte → `error_backup_trailing_data` |

- Key: `PBKDF2WithHmacSHA256(password, salt, iterations, 256 bits)` via `SecretKeyFactory`; the
  password characters are encoded by the platform provider (UTF-8 for this algorithm; confirm with
  a non-ASCII fixture, §10 open questions).
- Cipher: `AES/GCM/NoPadding`, 128-bit tag, the 12-byte IV above, AAD = UTF-8 bytes of
  `streammate-backup-v1`. A failed tag check is reported as a wrong password (the two cannot be
  told apart).
- Limits: whole file ≤ 10,485,760 bytes; plaintext ≤ 8,388,608 bytes on write.
- The rebuild writes exactly this envelope (same magic, version, 210,000 iterations, AAD) so
  backups move freely between beta 23 and the rebuild in both directions.

### 7.2 Payload encoding

- UTF-8 JSON, compact (no whitespace), one object with the keys of §6.2 written in that order.
  Readers must not depend on key order.
- `sources` is standard padded Base64 (RFC 4648) of the source codec bytes; `customLogoData` is
  standard padded Base64 of the PNG file.
- Nullable fields are written as JSON `null`; `sortOrder`, `channelNumber` and `position` are
  numbers or `null`; `enabled` and `sortMode` in rules are `true`/`false`/`null` and a
  `LibrarySort` name or `null`.

Illustrative skeleton (placeholders only):

```json
{"formatVersion":2,"exportedAtEpochMillis":1790000000000,
 "sources":"U1RNUwAAAAM…","parentalPin":null,
 "preferences":{"timeZoneId":"Europe/Helsinki","timeZoneFollowsDevice":true,"startupScreen":"HOME",
   "remoteChannelKeyMode":"DPAD_AND_CHANNEL_KEYS","colorTheme":"original", "…":"…"},
 "profileData":{"default":{"favouriteChannelIds":["src-1:ch-42"], "…":"…"}},
 "channelPreferences":[{"channelId":"src-1:ch-42","sourceId":"src-1","customName":null,
   "customGroupTitle":null,"hidden":false,"sortOrder":null,"manualXmltvChannelId":null,
   "updatedAtEpochMillis":1790000000000,"customLogoUrl":"https://logos.example/42.png","channelNumber":12}],
 "channelLists":[],"channelListMembers":[],
 "organization":{"rules":[{"room":"MOVIES","sourceId":"","groupKey":"","itemKey":"film:kept",
   "enabled":false,"sortMode":null,"position":null}],"aliases":[{"alias":"work:kept","identity":"film:kept"}]}}
```

### 7.3 Payload validation (all before the first write)

- `formatVersion` required, 1…2 (`backup_error_version`); version 1 has no `organization` and
  restores an empty organisation (rules and aliases deleted); version ≥ 2 requires it.
- `sources`: required non-blank string ≤ **20,000 characters** (the generic string limit, which
  also caps the Base64 source list at about 15,000 bytes — see §8), decoded by the source codec
  (versions 1–3; unknown enum names, duplicates, trailing bytes and model-rule violations fail).
- `parentalPin`: `null` or `\d{4,8}`.
- Channel customisation: ≤ 100,000 preferences, ≤ 1,000 lists, ≤ 500,000 members; unique
  `channelId`; every preference's `sourceId` among the backup's sources; unique `listId`; every
  member's list exists; unique (`listId`, `channelId`). Required per preference: `channelId`,
  `sourceId`, `hidden`, `updatedAtEpochMillis`; per list: all four fields; per member: all three.
- Organisation: ≤ 300,000 rules and ≤ 500,000 aliases; room ∈ `LIVE`/`MOVIES`/`SERIES`;
  `sortMode` parses as a library sort or is `null`; `position` ≥ 0 or `null`; `sourceId`,
  `groupKey`, `itemKey` ≤ 2,048 chars (empty allowed); no duplicate (room, sourceId, groupKey,
  itemKey); aliases non-blank, ≤ 2,048 chars, unique `alias`.
- Required strings: present, non-blank, ≤ 20,000 characters; optional strings ≤ 20,000.

### 7.4 Document pickers

- Save: `Intent.ACTION_CREATE_DOCUMENT`, type `application/vnd.streammate.backup`,
  `EXTRA_TITLE` `sohva-tv-backup.smbak`; write with `ContentResolver.openOutputStream(uri, "wt")`.
- Restore: `Intent.ACTION_OPEN_DOCUMENT`, `EXTRA_MIME_TYPES` as in BACKUP-FR-17; read with
  `openInputStream(uri)`; a `null` stream → `backup_error_open`.
- No persistable URI permission is taken; the grant ends with the operation.

## 8. Edge cases and limits

- **Large catalogues**: see §4.4 and §9. The owner's real Shield backup (September 2026, a large
  provider) was 40,184 bytes.
- **Source list limit**: a source list whose Base64 exceeds 20,000 characters is **written** by
  beta 23 but refused on restore ("Backup field sources is too long"). Typical 1–5 sources are far
  below it; many sources with very long tokenised URLs could reach it. Rebuild: read at least
  what beta 23 reads, lift the limit for `sources` to the codec's own bounds (100 sources), and
  check at export that the file will restore.
- **Another TV**: phone-sent logos are recreated from bytes; logo `file:` addresses of the old TV
  without bytes are dropped; sources restore with their ids, so channel ids in favourites,
  customisations and lists match after the first sync of the same provider. Channels that the
  provider renumbers or renames get new ids and lose their customisation (no remapping).
- **Same TV, fewer sources in the backup**: the missing sources and all their content and watch
  progress are deleted at once, without a confirmation (rebuild: confirm when a restore will
  delete sources, naming them; owner decision on wording).
- **Restore while an import runs**: beta 23 does not stop or wait for imports; an import of a
  removed source can race the cascade delete. Rebuild: take the per-source import locks (or a
  global maintenance lock) for the duration of the restore and cancel imports of removed sources.
- **Process death mid-restore**: steps 4–11 are not one transaction across the secret store,
  database and DataStore; a kill in between leaves a mix. Rebuild: write a `restore-in-progress`
  marker before step 4 and clear it after step 11; on start, if present, say "The last restore did
  not finish. Restore the backup again." (proposed key `backup_restore_incomplete`).
- **Restored profile is restricted**: the active profile can become a restricted one while
  Settings is open; the rebuild returns to Home (or the profile gate) after such a restore.
- **Non-ASCII passwords** (ä, ö are common in Finnish): must derive the same key as beta 23
  (§10 open question).
- **No document picker**: some TV builds ship without DocumentsUI; launching the contract then
  throws `ActivityNotFoundException`, which beta 23 does not catch (by code reading). Rebuild:
  catch it and show "This TV has no file picker…" (proposed key `backup_no_picker`); owner to
  decide whether to offer phone transfer instead.
- **Android 6–7 (API 23–25)**: `PBKDF2WithHmacSHA256` is documented by Android as available from
  API 26; on API 23–25 save and restore are expected to fail with the generic error (not verified
  on a device; minSdk is 23). Rebuild: detect it and show a clear message, or ship a small PBKDF2
  implementation over `Mac("HmacSHA256")` that matches the platform output byte for byte (test
  vector from RFC 7914 §11 plus a beta 23 fixture).
- **Removable media pulled mid-write**: the write fails with an I/O error; the message is the
  redacted exception text. Rebuild maps it to `backup_error_open`-style wording.
- **Clock**: `exportedAtEpochMillis` is informational; a wrong clock has no effect.
- **Low memory**: bounded by the rules in §9; beta 23's export and restore both hold the whole
  JSON tree.

## 9. Lightweight by design

The feature runs rarely, but it touched the largest tables of the app and once took the Shield
down. Rules:

- BACKUP-NFR-01 **No catalogue-sized structure, ever.** Beta 16 built JSON objects for the complete
  `organization_aliases` index (150,000+ rows) and filled the 192 MB heap: two ANRs, an empty
  document. The selection of §4.4 runs in SQLite; nothing reads the whole alias table.
- BACKUP-NFR-02 **Streamed export.** Read rows in pages of 500 in primary-key order (channel
  preferences by `channelId`, lists by `listId`, members by (`listId`, `channelId`), rules by
  primary key, the customised aliases by `alias`) inside one read transaction, and write each row
  straight to a streaming JSON writer. No `JsonObject`/`JsonArray` tree. Logo files are read one at
  a time. The JSON bytes go into one growable buffer capped at 8 MiB (the plaintext limit; exceeding
  it aborts before the document is touched). Encryption then feeds the buffer to the cipher in
  64 KiB chunks and writes whatever output it releases straight into the document after the 52-byte
  header (N = plaintext length + 16 is known once the buffer is complete). Android's AES-GCM
  providers may hold the data internally until `doFinal`, so budget the worst case at about three
  times the plaintext (≤ 24 MiB for the 8 MiB limit); a typical backup is well under 1 MiB. A
  chunked AEAD would be strictly streaming but changes the format beta 23 must read, so the bound is
  accepted. Overwrite the plaintext buffer with zeros when done.
- BACKUP-NFR-03 **Streamed restore.** Read the 52-byte header first and reject bad files before
  reading the body; read the ciphertext into one buffer (≤ 10 MiB) and decrypt (GCM releases
  plaintext only after the tag check, so the plaintext also sits in memory: peak about 2–3 × the file
  size, ≤ 30 MiB worst case, a few hundred kilobytes typically). Parse with a pull parser (`JsonReader`-style), never a tree: small
  sections (sources, PIN, preferences, profile data, genre groups: bounded to a few kilobytes) become
  typed objects; the large arrays (channel preferences, members, rules, aliases) are validated per
  record and inserted in batches of 500 inside **one** database transaction that also performs the
  deletions of BACKUP-FR-18 steps 4 and 6; cross-record rules (duplicates, unknown list, unknown
  source) are enforced by primary keys and a final check query before commit; any failure rolls the
  whole transaction back. Logo bytes are decoded one at a time into a staging folder and moved into
  the logo store only after commit (deleted on rollback). Secret store and preferences are written
  after the commit (BACKUP-FR-18 order), under the `restore-in-progress` marker (§8).
- BACKUP-NFR-04 **Off the main thread, cancellable only before the first write.** Everything runs on
  the IO dispatcher at normal priority (the viewer is waiting); the UI shows the busy text. Parsing
  and inserts yield between batches. Once the transaction starts, the operation is not cancelled by
  leaving the screen (run it in an application-scoped job, not the screen's scope).
- BACKUP-NFR-05 **Key-derivation cost.** 210,000 PBKDF2-HMAC-SHA256 iterations are 420,000 SHA-256
  compressions. On a Cortex-A35 this is estimated at roughly 0.2–1 s depending on whether the
  provider uses the ARMv8 SHA-2 instructions (not measured; §11 has the check). It runs once per
  save and once per restore, off the main thread. Do not raise the written iteration count above what
  the low-end box derives in ≤ 1.5 s; keep accepting 100,000–1,000,000 on read.
- BACKUP-NFR-06 **Restore aftermath is paged.** Restore empties the alias index; the film-identity
  pass that rebuilds it must page the catalogue in key order ([Library organisation](42-library-organization.md),
  [plan/07](../plan/07-performance.md)); it runs after the next import at background priority, never
  as part of the restore.
- BACKUP-NFR-07 **Nothing at start-up.** The backup code, JSON writer and cipher are created on first
  use of the section; opening Settings costs nothing for this feature.
- BACKUP-NFR-08 **Budgets** (targets for the low-end box): save of a typical backup (≤ 100 KB) ≤ 2 s
  including key derivation; restore of the same ≤ 3 s plus the time to delete removed sources; an
  8 MiB synthetic backup (100,000 channel preferences, 500 lists, 100,000 members, 50,000 rules)
  restores without the Java heap passing 64 MB above its level before the restore.

## 10. Lessons from the current app

- **Backup export memory repair (16 September 2026, shipped in beta 17).** Shield code 25 froze
  while saving and left an empty document; two ANRs with the 192 MB heap full, both inside
  `OrganizationBackupCodec.toBackupJson` building objects for the whole `organization_aliases`
  catalogue index. Fix: include all rules and only the alias families customised film rules
  reference, selected in SQL (§4.4); uncustomised identities are rebuilt by the identity pass after
  restore. Schema, encryption and format unchanged; old backups still readable. Validation: seven
  emulator backup/restore tests including 150,000 unrelated aliases produced a backup under 64 KiB,
  left 150,005 aliases intact and restored the five required ones; the Shield then exported a
  40,184-byte backup that the owner restored on the Elisa box (private receipt
  `docs/SOHVA_TV_BACKUP_REPAIR.md`; private build `0.1.0-beta.16-backup.1`, code 27; public in
  beta 17, code 29). Rule for the rebuild: [plan/08](../plan/08-lessons-learned.md) 1.5 — stream,
  never materialise catalogue-sized structures to write a settings file.
- **Messages nobody sees.** All backup outcomes go to a status line that only the Playlists section
  draws (S§9). Keep the messages, show them in the section.
- **A setting forgotten.** `auto_frame_rate` is not in the backup and every restore resets it; the
  preference key list is maintained by hand in three places ([plan/03](../plan/03-architecture.md)
  §3.3). One table drives all three in the rebuild (BACKUP-FR-22).
- **Restore wipes device-local state.** `values.clear()` on the DataStore also removes the reminder
  overlay prompt flag and the Library manager's last location; harmless but surprising (BACKUP-FR-24).
- **Compatibility has held.** Format 1 (written before organisation rules arrived on
  4 September 2026, commit `97c7264`) still restores
  (`versionOneBackupRemainsReadableWithoutOrganizationTables`), a retired preference is ignored and
  not written again (`retiredBrowserSwitchInAnOldBackupIsIgnoredAndNotWrittenAgain`), and a missing
  colour theme restores Original (`colorThemeSurvivesBackupAndOlderBackupsUseTheDefault`). Keep all
  three as permanent regression tests with **fixture files**, not files generated by the code under
  test.
- **Untranslated validation errors** (§4.6) reach the viewer in English.
- **No confirmation** before a restore deletes sources and their progress (S§5 notes the same for
  other destructive actions).
- **The PIN inside the backup.** The PIN travels as digits inside the encrypted payload, so anyone
  with the file and its password learns it; restoring a backup without a PIN removes the TV's PIN.
  Both are intended (the backup is the household's configuration), and the About/INSTALL texts say
  to keep the file private.

**Open questions (for the owner)**

1. Should Discover data (addons, Library, catalog order) join the backup? Every tester document says
   it is not included; [Discover](50-discover-addons.md) asks the same.
2. Should TMDB/API-Sports keys be included (they are the viewer's own and encrypted by the
   password), or stay out as today?
3. Keep writing `formatVersion` 2 (restorable on beta 23) or move to 3 when the rebuild adds new
   data (beta 23 would then refuse rebuild backups)?
4. Confirm the password encoding of Android's `PBKDF2WithHmacSHA256` for non-ASCII passwords: needs
   a fixture `.smbak` saved by beta 23 on a device with a password containing "ä" (owner action; the
   password and file stay private, only a synthetic test backup is committed).
5. Confirmation step before a restore that deletes sources: yes/no, and wording.
6. Behaviour on Android 6–7 where PBKDF2-SHA256 may be missing: own implementation or a clear
   "not supported on this Android version" message?

## 11. Acceptance tests

Unit (JVM)
- Envelope: round trip with an ASCII and a non-ASCII password; wrong password and a flipped last byte
  both give `error_backup_wrong_passphrase`; bad magic, version 2, iterations 99,999 and 1,000,001,
  salt length 15, IV length 13, ciphertext length 15, a trailing byte, and a 10 MiB + 1 file each give
  their own error (mirrors `PortableBackupCipherTest`, extended).
- **Fixture compatibility**: committed synthetic `.smbak` files written by beta 23's code
  (format 1 and format 2, fictional sources on `provider.example`, a logo, two profiles, organisation
  rules, a PIN) decrypt and restore to the expected typed model. Byte-level check of the header.
- Payload: every §6.4 default for absent keys; every "restore fails" enum case; `customCatalogueGroups`
  leniency; `remoteMappings` absent with `CHANNEL_KEYS_ONLY`; `timeZoneFollowsDevice` absent → false;
  profiles truncated to 6, names to 24, colours clamped.
- The preference table test of BACKUP-FR-22 (every DataStore key backed up or explicitly excluded).
- Organisation codec: round trip with hidden state, manual ranks and aliases; malformed rank, unknown
  sort and duplicate keys rejected before restore (mirrors `OrganizationBackupCodecTest`).

Instrumentation (emulator)
- Mirror `BackupCustomGroupsTest`: format-1 backup restores without organisation; sports priority
  order survives; a phone logo and a channel number come back (logo file rebuilt); organisation rules
  and film aliases survive; **150,000 unrelated aliases → backup ≤ 65,536 bytes, all 150,005 aliases
  intact after export, exactly the 5 customised aliases after restore**, and a later import
  re-registers without changing restored rules; every profile and its data survive; own genre groups
  survive; the retired switch is ignored and not rewritten; every colour theme survives and an old
  backup gives Original.
- Validation happens before writes: a backup with one duplicate list member leaves sources,
  preferences, customisations and organisation byte-identical.
- Restore removes a source absent from the backup together with its channels, films and progress;
  a surviving source keeps its progress.
- UI: with a 7-character password both buttons are disabled; with 8 enabled; after a save the status
  "Encrypted backup saved" is visible **in the Backup section** and the field is empty; cancelling the
  picker keeps the password and returns focus to the button.
- Process-death check: kill the process after the database commit of a restore; on relaunch the
  incomplete-restore message appears.

Manual device checks
- Save on the Shield, copy by USB, restore on the low-end box; sources, favourites, custom lists,
  numbers, phone logos, theme and profiles match; guide and films appear after a sync.
- Restore a backup saved by public beta 23 on the rebuild, and a rebuild backup on beta 23.

Performance (low-end box or the `.local/slowbox` emulator stand-in)
- Export and restore with 150,000 aliases and an 8 MiB synthetic payload while sampling
  `dumpsys meminfo` every 250 ms: Java heap growth ≤ 64 MB; no frame of the Settings screen blocked
  (no main-thread slice > 16 ms attributable to the backup in a Perfetto trace).
- Time PBKDF2 at 210,000 iterations on the low-end box (log the milliseconds once in diagnostics as
  `backup: key derivation N ms`); must be ≤ 1.5 s.

## 12. Reference: current code map

| File (beta 23) | Role |
|---|---|
| `app/.../app/StreamMateBackupManager.kt` | Export and restore orchestration, JSON payload encode/decode, validation, logo bytes, restore order |
| `app/.../app/OrganizationBackupCodec.kt` | Organisation rules/aliases to and from JSON |
| `core/.../core/security/PortableBackupCipher.kt` | `.smbak` envelope, PBKDF2 and AES-GCM, limits |
| `core/.../core/security/IptvSourceConfigurationCodec.kt` | Binary codec of the source list carried as `sources` |
| `core/.../core/security/SecretSettingsStore.kt` | Source list and PIN read for export, written on restore |
| `core/.../core/database/OrganizationDao.kt` | `backupSnapshot` (customised film aliases SQL), `restore`, `validateOrganizationSnapshot` |
| `core/.../core/database/GuideDao.kt` | `clearSource` cascade, `replaceChannelCustomization` |
| `iptv/.../iptv/repository/GuideStore.kt` | `channelCustomizationSnapshot`, `restoreChannelCustomization`, `upsertSourceState` |
| `iptv/.../iptv/repository/OrganizationRepository.kt` | `backupSnapshot`, `migrateLegacy` |
| `core/.../app/AppPreferencesRepository.kt` | `restore` (clears and rewrites the DataStore), `restoreProfileData` |
| `app/.../app/ChannelLogoStore.kt` | Logo bytes for export; re-bounded save on restore; `existingOrNull` |
| `iptv/.../feature/settings/SettingsScreen.kt` | Backup section UI, pickers (`BACKUP_MIME_TYPE`, `DEFAULT_BACKUP_FILE_NAME`, `MIN_BACKUP_PASSPHRASE_LENGTH`) |
| `app/.../app/StreamMateApp.kt` | `onExportBackup` / `onRestoreBackup` wiring, `runBackupOperation` |
| `app/src/androidTest/.../app/BackupCustomGroupsTest.kt` | Nine device backup/restore tests |
| `core/src/test/.../security/PortableBackupCipherTest.kt`, `app/src/test/.../OrganizationBackupCodecTest.kt` | Cipher and codec unit tests |
| Private `docs/SOHVA_TV_BACKUP_REPAIR.md` | The beta 17 memory repair receipt |
