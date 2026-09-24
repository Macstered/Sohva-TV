# Updates, About and diagnostics

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

Visuals: Settings › About in [design/screens/settings.md](../design/screens/settings.md) §6 "About"
(cited as **S§6**), the diagnostics picker in §5 (**S§5**), and the legal screen in
[design/03-screen-layouts.md](../design/03-screen-layouts.md) ("About, privacy and licences").
Identity and the update-feed contract are fixed by [plan/00](../plan/00-product-overview.md) §6;
release procedure in [plan/06](../plan/06-quality-testing-release.md). Redaction rules and privacy
commitments are in [Security and privacy](73-security-privacy.md).

## 1. Summary

Settings › About is where a tester sees which beta is installed, learns that a newer one exists,
downloads it, has it verified against the published checksum and hands it to Android's installer,
now together with its install-time profile so the update starts compiled. It shows what changed
(for the offered beta, or for the installed one), links to the legal screen ("About, privacy and
licences": version, privacy summary, contact, data-provider attributions, open-source notice),
to the public repository for translations, and saves a redacted diagnostics text file that a
tester can send when asked. There is no server of Sohva's own: the update feed is the public
GitHub release list, and diagnostics never leave the TV unless the viewer shares the file.

## 2. Feature checklist

Updates
- ABOUT-01 The UPDATES group shows the installed version: "Installed: 0.1.0-beta.23".
- ABOUT-02 An automatic update check runs at most once per 24 hours, at app start, only in the
  release package `com.streammate.tv`; nothing is downloaded without a press.
- ABOUT-03 **Check for updates** checks now.
- ABOUT-04 One status line names the phase: not checked, checking, newest, available, downloading
  with percent, downloaded and verified, permission needed, or one of four failures (in `danger`).
- ABOUT-05 The newest published release whose stated Android build is above the installed one is
  offered: "Sohva TV X is available." with **Download**.
- ABOUT-06 The download goes to the app's cache with a live percentage and is verified against the
  release's `SHA256SUMS.txt`; a mismatch deletes it; a release without a checksum file is refused.
- ABOUT-07 The release's install-time profile for the device's Android (`.api31.dm` or `.api28.dm`)
  is downloaded, verified the same way and installed together with the APK in one
  PackageInstaller session, so Android compiles the update while installing (`reason=install-dm`).
- ABOUT-08 Without a usable profile, or when the session cannot be used, the APK is handed to the
  system installer screen as before.
- ABOUT-09 When Android needs the "install unknown apps" permission, **Allow installs** opens that
  page for Sohva TV and **Install** tries again.
- ABOUT-10 "What's new in X" shows the offered release's notes, otherwise the installed build's own
  release notes, remembered for offline use (at most 4,000 characters).
- ABOUT-11 Debug, demo and Lab builds never check, download or install; About says public updates
  are disabled (Lab also shows its safety notice as the notes).
- ABOUT-12 Help line: downloads are verified; playlists and settings are kept.

About and legal
- ABOUT-13 **About, privacy and licences** (centred button) opens the legal screen.
- ABOUT-14 Legal screen: brand, title, subtitle, Back; the version and the non-commercial statement.
- ABOUT-15 On-device privacy summary (five paragraphs).
- ABOUT-16 Support and privacy contact with **Send email** (`mailto:` the public address).
- ABOUT-17 TMDB attribution: notice, the unmodified TMDB logo, **Open TMDB**.
- ABOUT-18 TVmaze (CC BY-SA): notice, **Open TVmaze**, **TVmaze licence**.
- ABOUT-19 API-Sports: notice, provider-rights statement, **API-Sports terms**.
- ABOUT-20 Notes on services and connections (HTTP, metadata and sports disclosures).
- ABOUT-21 Discover addons notice.
- ABOUT-22 Open-source notice with **Apache 2.0** licence link.
- ABOUT-23 No-affiliation statement.
- ABOUT-24 The MIT notices of the Nord, Everforest and Kanagawa palettes ship inside the APK
  (`theme-licenses.txt`); beta 23 does not show them on screen.
- ABOUT-25 TRANSLATIONS: **Help translate Sohva TV** opens the public repository.

Diagnostics
- ABOUT-26 DIAGNOSTICS: **Save diagnostics** opens the system "create document" picker with the
  name `sohva-tv-diagnostics-yyyyMMdd-HHmm.txt` (text/plain).
- ABOUT-27 The file holds app, device, Android, locale, time zones, a Display line, SQLite version,
  key settings, sources by name, every refresh state and the app's last 600 event lines,
  including start-up and channel-loading timings.
- ABOUT-28 Addresses, user names, passwords, keys and tokens are removed before a line is kept and
  again before the file is written.
- ABOUT-29 The result shows under the button: "Diagnostics saved. Share the file with the developer
  if asked." or the failure.

## 3. Entry points and navigation

- **About** is the tenth Settings section (`settings_section_about`, Guide icon). First focus on
  entering it: the update action button; in the disabled phase (development builds), the "About,
  privacy and licences" button (S§8).
- Focus order in About: update action button(s) (left to right) → licences button → "Help translate
  Sohva TV" row → "Save diagnostics" button.
- **Legal screen** (`Destination.LegalInformation`): opened only from About. First focus: its Back
  button. Back (key or button) returns to Settings. **Flaw:** Settings is rebuilt at the Playlists
  section, so the viewer loses their place ([App shell](01-app-shell-navigation.md), Settings row of
  the return table). Rebuild: return to About with focus on the licences button.
- **External activities**: Allow installs opens Android's "Install unknown apps" page for this
  package; Install opens Android's install confirmation (session) or the package-installer screen;
  Open TMDB/TVmaze/terms/licence and Help translate open a browser; Send email opens a mail app;
  Save diagnostics opens DocumentsUI. When the viewer returns, the Settings screen is resumed as it
  was (except after the legal screen, above).
- **Other entry points**: none. The app never shows an update prompt outside About (no badge, no
  dialog, no notification). INSTALL.md and TESTING.md send testers to Settings › About.

## 4. Behaviour

### 4.1 Who may update

- ABOUT-FR-01 Public updates are allowed only when the package name is exactly `com.streammate.tv`.
  For `com.streammate.tv.debug`, `.demo` and `.lab` the checker is `Disabled`: every call (check,
  download, install, open permission page) returns without effect; the Lab manifest also removes
  `REQUEST_INSTALL_PACKAGES` and the update file provider.
- ABOUT-FR-02 The demo build never calls the automatic check; in the debug, demo and Lab builds
  About shows `update_disabled_development` "Public updates are disabled in development builds. Install a
  new development APK manually." (English only, `translatable="false"`).
- ABOUT-FR-03 In the Lab build the "installed notes" are `lab_safety_notice` ("Private Lab build
  with separate app data. Automatic IPTV imports, background metadata enrichment, sports polling
  and reminder alarms are disabled. Manual refresh remains available. No public updates are
  offered.", English only).

### 4.2 Checking

- ABOUT-FR-04 Automatic check: when the app's root screen first composes (once per activity
  creation, [App shell](01-app-shell-navigation.md) SHELL-FR-72), if the release package and not the
  demo build, and `now − last_check_epoch_millis ≥ 86,400,000 ms` (24 h), run a check. A fresh
  install (no stored time) checks at the first start.
- ABOUT-FR-05 Manual check: **Check for updates** runs a check at once. A check while a download
  is running is ignored.
- ABOUT-FR-06 A check sets phase CHECKING, then on the IO dispatcher:
  1. `GET https://api.github.com/repos/Macstered/Sohva-TV/releases?per_page=10` with header
     `Accept: application/vnd.github+json`, no authentication (§7.1);
  2. parses the releases (§7.2);
  3. finds the installed build's own release (first non-draft release whose stated build equals
     the installed version code) and converts its body to notes (§4.5);
  4. selects the update (ABOUT-FR-07).
  The time of the attempt is stored whether it succeeded or not (a failed automatic check waits a
  day before the next automatic one; rebuild: store the time only on success and retry at the next
  start).
- ABOUT-FR-07 Selection: among non-draft releases that state a build (`[Bb]uild \*\*(\d+)\*\*`,
  first match in the body) and carry an asset ending `.apk` (case-insensitive; the first such asset
  in the list), keep those whose build is greater than the installed version code and take the one
  with the highest build. `prerelease` is ignored (both kinds are offered). The offered update
  carries: version name (tag without a leading `v`), build, body, APK asset, checksum asset
  (`SHA256SUMS.txt`, name compared case-insensitively, may be absent) and the profile asset for the
  device's SDK (ABOUT-FR-13).
- ABOUT-FR-08 Outcomes: an update → AVAILABLE, log `update: available: <version>`; none → UP_TO_DATE,
  log `update: up to date`; any exception (network, HTTP status outside 2xx, JSON) → FAILED with
  reason NETWORK and no update, log `update: check failed` with the redacted cause. When the
  installed release was found, its notes are stored under `installed_notes_<versionCode>` and
  shown from then on, also offline.

### 4.3 Downloading and verifying

- ABOUT-FR-09 **Download** (only in AVAILABLE): if the update has no checksum asset → FAILED
  `NO_CHECKSUMS` without any request. Otherwise phase DOWNLOADING 0 %, then on IO:
  1. fetch `SHA256SUMS.txt` as text; find the digest line for the APK's asset name (§7.4); none →
     CHECKSUM_MISMATCH;
  2. create `cacheDir/updates/` and delete every file in it;
  3. stream the APK (`GET browser_download_url`, redirects followed) into
     `cacheDir/updates/<apk asset name>` with a 64 KiB buffer, updating a SHA-256 digest with the
     same bytes (no second read);
  4. progress = `received × 100 / total` clamped 0–100, where total = `Content-Length` when > 0,
     else the asset's `size` from the release list; the state is updated only when the integer
     percent changes (≤ 101 updates per download);
  5. compare the lower-case hex digest with the published one; mismatch → delete the file,
     CHECKSUM_MISMATCH;
  6. fetch the profile (ABOUT-FR-10);
  7. phase DOWNLOADED with the file and the profile (or none).
  Any other failure (HTTP status, I/O, timeout) → FAILED `NETWORK` with the update kept, so its
  notes stay visible.
- ABOUT-FR-10 Profile: only when the release has the profile asset for this device **and**
  `SHA256SUMS.txt` names it. `GET` it; refuse when `Content-Length` > 1,048,576 bytes or the body
  is larger (a profile is about 9 KB: 8,931 and 8,954 bytes in beta 23); hash it; mismatch or any
  failure → no profile (logged `update: install profile not used`), the update continues without
  it. Saved as `cacheDir/updates/<profile asset name>`.
- ABOUT-FR-11 Nothing outside `cacheDir/updates/` is written. Files stay there after the install
  until the next download clears the folder (rebuild: clear it at start when the installed version
  code is ≥ the file's build).

### 4.4 Installing

- ABOUT-FR-12 **Install** (in DOWNLOADED): if the APK file no longer exists (cache cleared) →
  FAILED `NETWORK` (rebuild: return to AVAILABLE so Download is offered again). On Android 8+
  (API 26), if `canRequestPackageInstalls()` is false → phase NEEDS_PERMISSION (file and profile
  kept).
- ABOUT-FR-13 Profile choice: SDK ≥ 31 → `<apk base name>.api31.dm`; SDK 28–30 →
  `<apk base name>.api28.dm`; below 28 → none (the base name is the APK asset name without `.apk`).
- ABOUT-FR-14 With a profile file and SDK ≥ 28, install through a `PackageInstaller` session:
  - `SessionParams(MODE_FULL_INSTALL)`, `setAppPackageName(own package)`,
    `setSize(apk length + profile length)`;
  - write `base.apk` then `base.dm` (the profile is matched to the APK by name) with a 64 KiB copy
    and `fsync` each;
  - register a receiver (not exported) for action `com.streammate.tv.app.UPDATE_INSTALL_STATUS`,
    matched by `EXTRA_SESSION_ID`;
  - commit with a broadcast `PendingIntent` to that action (`setPackage(own)`, request code = session
    id, `FLAG_UPDATE_CURRENT`, plus `FLAG_MUTABLE` on Android 12+ because the system adds extras);
  - on `STATUS_PENDING_USER_ACTION`, start the system confirmation intent (`EXTRA_INTENT`, new task);
    if there is none or it cannot start → abandon the session, outcome FAILED;
  - `STATUS_SUCCESS` → stop listening (the process is replaced by the new version);
  - `STATUS_FAILURE_ABORTED` (viewer cancelled) → outcome CANCELLED; any other status → FAILED
    (logged `update: install session ended with status N`);
  - CANCELLED → phase DOWNLOADED with the profile kept (Install tries the session again); FAILED →
    phase DOWNLOADED **without** the profile, so the next Install uses ABOUT-FR-15;
  - an exception while setting up the session (logged `update: install session failed`) abandons
    the session and falls through to ABOUT-FR-15 at once (`update: install session unavailable`).
- ABOUT-FR-15 Plain install: `ACTION_VIEW` of
  `content://com.streammate.tv.updates/updates/<apk name>` (FileProvider, authority
  `${applicationId}.updates`, path `cache-path updates/`), type
  `application/vnd.android.package-archive`, flags read-grant and new-task. Failure to start →
  FAILED `INSTALL_BLOCKED`.
- ABOUT-FR-16 **Allow installs** opens `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with
  `package:com.streammate.tv` (Android 8+; failures are swallowed, so on a TV without that page
  nothing happens — rebuild: show where to find it, as INSTALL.md does). **Install** in
  NEEDS_PERMISSION re-runs ABOUT-FR-12 with the kept file and profile. The app does not detect the
  grant by itself; the viewer presses Install.
- ABOUT-FR-17 On Android 6–7 there is no per-app permission; the package installer asks for the
  global "Unknown sources" setting itself.
- ABOUT-FR-18 The confirmation is always Android's own screen; the app never installs silently.
  Data is kept because the application ID and signing key are unchanged
  ([plan/00](../plan/00-product-overview.md) §6).

### 4.5 Release notes

- ABOUT-FR-19 Notes shown: for AVAILABLE, DOWNLOADING, DOWNLOADED, NEEDS_PERMISSION and FAILED with
  an update — the offered release's notes, heading "What's new in <offered version>"; otherwise the
  stored installed notes, heading "What's new in <installed version>"; no notes → no heading.
  Heading `update_notes_for` "What's new in %1$s" is **not translatable** (English in every
  language; TESTING.md item 21 says so).
- ABOUT-FR-20 Body to notes (`releaseNotes`):
  1. split into lines; find the first line matching `^#{1,3}\s+changed\b.*` (case-insensitive,
     trimmed), e.g. "## Changed since beta 9";
  2. if found, the section is the lines after it up to (not including) the next line matching
     `^#{1,2}\s+\S.*`; otherwise the whole body;
  3. per trimmed line: empty → ends a paragraph; `- ` or `* ` → a new block "• " + rest; a line
     starting `#` → a block of the heading text without `#`; a line following a bullet or paragraph
     line → joined with one space (unwrapping); otherwise a new block;
  4. in every block: Markdown links `[text](url)` → `text`; `**` and backticks removed;
  5. blocks joined with `\n`, trimmed; empty → no notes.
- ABOUT-FR-21 Display: first 4,000 characters (`MAX_UPDATE_NOTES_LENGTH`), 12 sp `textMuted`.
  The ten newest release bodies (betas 14–23) are bilingual (`## English` then `## Suomi`) and carry
  no "Changed" heading, so the whole body is shown: the title line, "Android build 57. Prerelease…", "English", the
  bullets, "Suomi", … Beta 23's body is 4,716 characters, so the Finnish half is cut. Rebuild:
  recognise the language sections (`## English`, `## Suomi`) and show the one matching the interface
  language, falling back to English, without changing what old builds read (§7.5).

### 4.6 About page states

| Phase | Status (`update_*`) | Status colour | Action button(s) |
|---|---|---|---|
| DISABLED | `update_disabled_development` | `textPrimary` | none (focus goes to the licences button) |
| IDLE | `update_idle` "Not checked yet." | `textPrimary` | Check for updates (Refresh icon) |
| CHECKING | `update_checking` "Checking for a newer beta…" | `textPrimary` | Check for updates, disabled |
| UP_TO_DATE | `update_up_to_date` "This is the newest beta." | `textPrimary` | Check for updates |
| AVAILABLE | `update_available` "Sohva TV %1$s is available." | `textPrimary` | Download (Save icon) |
| DOWNLOADING | `update_downloading` "Downloading %1$s: %2$d%%" | `textPrimary` | `update_checking_button` "Downloading…", disabled |
| DOWNLOADED | `update_downloaded` "%1$s is downloaded and verified. Install when ready." | `textPrimary` | Install (Play icon) |
| NEEDS_PERMISSION | `update_needs_permission` "Android needs permission for this app to install updates. Allow it, then press Install." | `textPrimary` | Allow installs (Settings icon, takes focus) + Install (Play icon) |
| FAILED NETWORK | `update_failed_network` "Could not reach the release list. Check the connection and try again." | `danger` | Check for updates |
| FAILED NO_CHECKSUMS | `update_failed_no_checksums` "The release has no checksum file, so the download was not started." | `danger` | Check for updates |
| FAILED CHECKSUM_MISMATCH | `update_failed_checksum` "The downloaded file did not match its published checksum and was discarded." | `danger` | Check for updates |
| FAILED INSTALL_BLOCKED | `update_failed_install` "The installer could not be opened." | `danger` | Check for updates |

- NETWORK is also used for download failures and a vanished file, where the text ("release list")
  is misleading. Rebuild: separate `DOWNLOAD_FAILED` and `FILE_MISSING` texts (new keys; owner to
  word them).
- A disabled button cannot hold focus: in CHECKING and DOWNLOADING the focus that sat on the action
  button must stay on a visible item (rebuild: keep the button focusable but inert, or move focus
  to the status line's row and back when the phase changes).

### 4.7 Legal screen

- ABOUT-FR-22 Sections, in order, each a `surface` card (§5.2); texts are string resources (app
  module, all seven languages):
  1. **Sohva TV** (`app_name`): `about_version` "Version %1$s" (PackageInfo `versionName`, "—" when
     blank) and `about_noncommercial` "Sohva TV is being prepared for free, non-commercial
     distribution. The app contains no channels or media and is not an IPTV or sports-content
     provider."
  2. **On-device privacy** (`about_privacy_title`): `about_privacy_intro`, `about_privacy_local`,
     `about_privacy_network`, `about_privacy_cleartext`, `about_privacy_retention` (full English in
     [Security and privacy](73-security-privacy.md) §4.9), blank line between paragraphs.
  3. **Support and privacy contact** (`about_contact_title`): `about_contact_body` "Questions,
     privacy requests and issue reports: hello@luontra.fi"; button `about_contact_email` "Send
     email" → `mailto:hello@luontra.fi`.
  4. **Data sources and rights** (`about_providers_title`): `about_tmdb_notice` "This product uses
     the TMDB API but is not endorsed or certified by TMDB. TMDB is optional and requires the user's
     own credential."; the TMDB logo `file:///android_asset/tmdb_attribution.svg` at 205 × 28 dp,
     10 dp gap; button `about_open_tmdb` "Open TMDB" → `https://www.themoviedb.org`.
  5. **TVmaze · CC BY-SA** (literal title): `about_tvmaze_notice` "TVmaze data is used under CC
     BY-SA. Enriched information cards identify TVmaze as the source and link to the original
     record."; buttons `about_open_tvmaze` "Open TVmaze" → `https://www.tvmaze.com` and
     `about_open_tvmaze_license` "TVmaze licence" → `https://www.tvmaze.com/api#licensing`.
  6. **API-Sports** (literal title): `about_api_sports_notice` + `about_provider_rights`; button
     `about_open_api_sports_terms` "API-Sports terms" → `https://api-sports.io/terms`.
  7. **Notes on services and connections** (`about_service_notes_title`, iptv module):
     `settings_http_disclosure`, `metadata_disclosure`, `sports_disclosure`.
  8. **Discover addons** (`about_addons_title`): `about_addons_body`.
  9. **Open-source software** (`about_open_source_title`): `about_open_source_notice`; button
     `about_open_apache_license` "Apache 2.0" → `https://www.apache.org/licenses/LICENSE-2.0`.
  10. Footer text `about_no_affiliation` "Sohva TV is not an IPTV provider and is not affiliated
      with Google, TMDB, TVmaze, API-Sports, sports leagues or channel owners."
- ABOUT-FR-23 Links open through the platform URI handler; a failure (no browser, no mail app —
  common on TVs) is swallowed and nothing happens. Rebuild: when no activity can open the link, show
  the address as text and a QR code in a small dialog (same component as phone setup).
- ABOUT-FR-24 `about_open_source_notice` still names kXML2, which no longer ships since beta 23, and
  omits libraries the notices list (AndroidSVG, Guava, Accompanist, JSpecify). Rebuild: generate
  the list from the release dependency graph at build time, and add a "Palette licences" entry that
  shows `theme-licenses.txt` (owner decision, [design/01](../design/01-design-system.md) open
  question 1).

### 4.8 Translations

- ABOUT-FR-25 Group TRANSLATIONS (`translate_title`): value row `translate_help_title` "Help
  translate Sohva TV", subtitle `translate_help` "Spanish, Portuguese, German, Swedish and Italian
  are drafts. Corrections are welcome in the public repository.", Info icon, empty value; OK opens
  `https://github.com/Macstered/Sohva-TV` (failure swallowed; ABOUT-FR-23 applies).

### 4.9 Diagnostics log (in memory)

- ABOUT-FR-26 One process-wide log of the newest **600** lines (`CAPACITY`), oldest dropped first,
  guarded by a lock. Each line: `yyyy-MM-dd HH:mm:ss L/tag: message[ · ExceptionClass: cause]`,
  time in the system default zone, `L` = `I`, `W` or `E`; the cause is the redacted exception
  message or "no message". The message and the cause pass through the redactor **before** the line
  is stored. Every line is mirrored to logcat with tag `SohvaTV` (level by `L`, without the time).
- ABOUT-FR-27 The log lives only in memory: it starts empty with each process and is lost when the
  process dies (no crash capture; there is no uncaught-exception handler and no exit-reason reading).
- ABOUT-FR-28 What writes to it in beta 23 (tag: message shape):

| Tag | Message | When |
|---|---|---|
| `startup` | `local state ready: N ms` | container initialisation finished |
| `database` | `analyze: N ms` / `analyze failed` | after an import |
| `home` | `cached resume ready: N ms` | Continue watching's first read |
| `Guide` | `channel rows: N of a source\|group in P pages, T ms[, A attempts]` | a guide roster read of ≥ 2,000 rows or ≥ 250 ms |
| `http` | `GET <redacted URL>: no response` / `…: HTTP <code>` | playlist/guide fetch failure |
| `playlist` / `epg` / `catalogue` | `<sourceId>: N channels[ (xtream)]`, `N programmes for M channels`, `N films, M series (m3u\|xtream)`, or `failed` | import end |
| `refresh` | `start <kinds>, N source(s), immediate=<bool>`, `done, N failed`, `deferred: app in the foreground`, `<kind> <sourceId>: failed` | background refresh |
| `Metadata` | `queue synced: N titles in P pages, Q to look up, T ms` | metadata queue |
| `Identity` | `film identities: N films in P pages, T ms` | film identity pass |
| `reminders` | set/removed/next alarm/fired/brought forward/notification off | reminders |
| `player` | `<channelId>: <detail>, attempt N` | playback reconnect |
| `update` | `available: X`, `up to date`, `check failed`, `install profile not used`, `installing with profile`, `install session unavailable`, `install with profile: <outcome>`, `install session failed`, `install session ended with status N` | updater |
| `diagnostics` | `saved N lines` | after a save |
| `Trakt` | scrobble outcomes with item ids and titles, sync counts | Trakt ([Trakt](51-trakt.md)) |
| `addon-startup` | `<milestone>: N ms` | Discover playback start-up |

  Rebuild rules: keep the counts-and-timings style; never log titles, programme names or item ids
  (Trakt today logs titles: [Trakt](51-trakt.md) §9 rule); cap each stored line at 500 characters.

### 4.10 Saving the diagnostics file

- ABOUT-FR-29 **Save diagnostics** launches `ACTION_CREATE_DOCUMENT` with MIME `text/plain` and
  the name `sohva-tv-diagnostics-` + `yyyyMMdd-HHmm` (local date-time in the device's default zone)
  + `.txt`, e.g. `sohva-tv-diagnostics-20260907-1130.txt`. Cancel does nothing.
- ABOUT-FR-30 With a document: collect (IO) the facts of §7.6, render the text, open the document
  with mode `"wt"` and write it as UTF-8 through a buffered writer; log `diagnostics: saved N lines`.
  Status: `diagnostics_saved` "Diagnostics saved. Share the file with the developer if asked." or
  the error text (`userMessage`: localised, or redacted exception text, or "Unknown error"); a
  `null` output stream → "The chosen location could not be opened" (English, not a resource;
  rebuild: a resource). There is no busy state; pressing twice opens the picker twice.
- ABOUT-FR-31 The file is never sent anywhere by the app.

## 5. Screen anatomy

No screenshot of About or the legal screen exists in the kit.

### 5.1 Settings › About (S§6 "About", group vocabulary S§3)

- **UPDATES group** (heading `update_title` "Updates", uppercase overline style):
  - `update_installed` "Installed: %1$s", 13 sp `textMuted`;
  - status text (body style), `danger` when FAILED, else `textPrimary`; test tag
    `settings-update-status`;
  - 8 dp; a Row (`spacedBy 10`) with the action button(s) of §4.6 (normal size `TvActionButton`,
    18 × 11 padding, label style, 18 dp icon); test tags `settings-update-check`,
    `settings-update-download`, `settings-update-install`, `settings-update-permission`;
  - when notes exist: 8 dp; "What's new in X" 13 sp Bold (`settings-update-notes-title`); notes
    12 sp `textMuted` (`settings-update-notes`);
  - 6 dp; `update_help` "Downloads are verified against the published checksum; playlists and
    settings are kept." 12 sp `textMuted`.
- **Licences button**: a Row centred horizontally with a normal `TvActionButton`
  `settings_about_licenses` "About, privacy and licences" (Info icon), tag
  `settings-about-licenses`.
- **TRANSLATIONS group**: one `SettingsValueRow` (74 dp minimum, Info icon, no value), tag
  `settings-translate-help`.
- **DIAGNOSTICS group** (`diagnostics_title`): a `SettingsRow` titled `diagnostics_save` "Save
  diagnostics", subtitle `diagnostics_help` "Recent events and refresh states as a file, with
  addresses and keys removed.", Save icon; trailing compact button "Save diagnostics" (Save icon,
  tag `settings-diagnostics-save`); status below in `focus`, 12 sp, horizontal padding 14 (tag
  `settings-diagnostics-status`).

### 5.2 Legal screen ([design/03](../design/03-screen-layouts.md))

- Screen background with the default safe padding (40 × 24 dp), one Column:
  - header Row (space between, centred vertically): `SohvaTvBrand` (34 sp) + Column (padding start
    24): `about_title` "About, privacy and licences" 28 sp Black `textPrimary`, `about_subtitle`
    "Public non-commercial release information" 13 sp `textMuted`; normal Back button (Back icon,
    tag `legal-back`, first focus);
  - `LazyColumn` (tag `legal-list`), padding top 18, `spacedBy 12`, content padding bottom 28.
- Section card: full width, `surface` fill, medium shape, padding 20 × 16; title 18 sp Black
  `focus`; body 13 sp / 18 sp line height, `textPrimary` at alpha 0.88, top padding 7, max width
  1,120 dp; actions Column with top padding 12 holding compact buttons (tags `legal-contact-email`,
  `legal-open-tmdb`, `legal-open-tvmaze`, `legal-open-tvmaze-license`, `legal-open-api-sports`,
  `legal-open-apache-license`). TMDB logo 205 × 28 dp then 10 dp before its button; TVmaze buttons in
  a Row `spacedBy 10`.
- Footer: `about_no_affiliation` 12 sp / 17 sp, `textMuted`, full width, padding 8 × 4.
- Text-only cards cannot take focus; the list scrolls only as far as the focused button needs, so
  the last cards may never come fully into view with the D-pad (by code reading). Rebuild: make each
  card a focusable reading block (no visual change except the focus style) so every card is reachable,
  and request initial focus with the frame-retrying helper, not a plain `requestFocus()`.

## 6. Data

| Store | Key | Content | Lifetime |
|---|---|---|---|
| SharedPreferences `streammate_updates` | `last_check_epoch_millis` | time of the last check attempt | until app data is cleared |
| same | `installed_notes_<versionCode>` | plain-text notes of that build's own release | never pruned (one short entry per build seen) |
| `cacheDir/updates/` | `<apk name>`, `<profile name>` | the verified download | until the next download or the system trims the cache |
| memory | diagnostics log | ≤ 600 lines | process |
| document chosen by the viewer | diagnostics file | text | the viewer's |

Nothing here is per profile; nothing is in the `.smbak` backup; Android backup is off for all of it.
The updater state (phase, update, file) lives in memory for the process.

## 7. External interfaces

### 7.1 GitHub release list

- `GET https://api.github.com/repos/Macstered/Sohva-TV/releases?per_page=10`,
  `Accept: application/vnd.github+json`, no token, the shared OkHttp client (connect 20 s, read
  90 s, redirects followed, default OkHttp User-Agent). Anonymous GitHub API access is rate-limited
  per IP address (GitHub documents 60 requests an hour); a 403/429 shows the NETWORK failure.
- The list is newest first (GitHub order); only the 10 most recent releases are seen.
- Fields read per release: `tag_name` (required, else the release is skipped), `name`, `body`,
  `prerelease`, `draft`, and per asset `name`, `browser_download_url` (both required, else the asset
  is skipped), `size`. Unknown fields are ignored.

### 7.2 Assets of a release (beta 23)

`SHA256SUMS.txt`, `sohva-tv-0.1.0-beta.23-tester-pack.zip`, `sohva-tv-0.1.0-beta.23.api28.dm`,
`sohva-tv-0.1.0-beta.23.api31.dm`, `sohva-tv-0.1.0-beta.23.apk`. The ZIP is ignored by the updater.

### 7.3 Install-time profiles

AGP writes them beside the release APK as `baselineProfiles/0/app-release.dm` (Android 12+, the
`.api31.dm` asset) and `baselineProfiles/1/app-release.dm` (Android 9–11, `.api28.dm`). Packaging
refuses a profile whose `primary.profm` differs from the APK's `assets/dexopt/baseline.profm` (and,
for api28, whose `primary.prof` differs from `assets/dexopt/baseline.prof`), copies them under the
release names and lists them in `SHA256SUMS.txt`. A first sideload cannot carry a profile; the
profile path works only for updates made through the app from build 54 on (proven on the Shield on
23 September 2026: installer `com.streammate.tv`, `base.dm` beside the APK, `reason=install-dm`).

### 7.4 `SHA256SUMS.txt`

One line per file, as written by the packaging script: `<64 lower-case hex>` + two spaces +
`<file name>`, CRLF or LF line ends, UTF-8 without BOM; it names the APK, both profiles and the
tester documents. The reader trims each line, takes the text before the first space as the digest
(lower-cased), the text after it trimmed and without a leading `*` as the name, and accepts a line
only when the name equals the asset name exactly and the digest is 64 hex digits; the first match
wins.

### 7.5 The release-body contract (every future release must keep it)

Installed builds from beta 3 (build 4) to beta 23 (build 57) read the feed with the rules above
and cannot be changed. Every release published to `Macstered/Sohva-TV` must therefore:

1. be **published** (not a draft) and be among the **10 newest** releases of the repository when a
   tester checks (publish in build order; do not create many releases in a burst);
2. carry the line `Android build **N**` near the top of the body, N = the APK's `versionCode`; the
   updater takes the **first** match of `[Bb]uild \*\*(\d+)\*\*` anywhere in the body, so no other
   bolded build number may appear before it (not even "since build **51**");
3. have N strictly greater than every earlier release's N (beta 23 is 57; the rebuild's first
   release is ≥ 58);
4. carry **exactly one** asset whose name ends in `.apk` (the first one is used), signed with the
   permanent release key (certificate SHA-256
   `985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6`) and with application ID
   `com.streammate.tv`;
5. carry `SHA256SUMS.txt` naming that APK by its exact asset name (without it, every installed
   build refuses to download: "The release has no checksum file…");
6. carry both install profiles named `<APK name without .apk>.api31.dm` and `.api28.dm`, each
   named in `SHA256SUMS.txt` and each ≤ 1 MiB, built from that exact APK (builds 54+ use them;
   missing profiles are not an error but the update then runs uncompiled until the nightly dexopt);
7. use tag `v<versionName>`; the version shown to testers is the tag without `v`;
8. keep release notes readable as plain lines: Markdown bullets, `**bold**`, links and headings are
   reduced to words; the first 4,000 characters are shown; an optional `## Changed …` section, if
   present, is shown instead of the whole body.

Checked before every publication by the release scripts (`publish.ps1` refuses notes without the
build line and verifies the draft body and asset count; `verify-publication.ps1` reads the
anonymous feed afterwards) — keep equivalent gates ([plan/06](../plan/06-quality-testing-release.md)).
The rebuild's own updater may read more (language sections, more than 10 releases), but must also
accept releases written to this contract.

### 7.6 The diagnostics file

Plain text, `\n` line ends, UTF-8. Values in `<>` are filled in; the rest is literal:

```
Sohva TV diagnostics
Generated: <yyyy-MM-dd HH:mm:ss> (<system zone id>)
App: <versionName> (build <versionCode>) (<package>)
Device: <Build.MANUFACTURER> <Build.MODEL> (<Build.DEVICE>)
Android: <Build.VERSION.RELEASE> (API <SDK_INT>)
Locale: <default locale tag>; TV time zone: <system zone id>
Display: panel <W>x<H> at <Hz, one decimal> Hz, reported <W>x<H> px; app <W>x<H> px, <Wdp>x<Hdp> dp at <dpi> dpi
SQLite: <sqlite_version()>

Settings
  time zone: <TV's own | zone id>
  interface size: <normal|compact|small|smaller>; startup: <home|guide|last_channel>
  refresh interval: <hours> h; buffer: <default|low_latency|stability>; recovery: <standard|persistent>
  metadata language: <tag>; match display: <true|false>; next episode: <true|false>

Sources (<n>)
  <source name>: <m3u|xtream>, <in use|off>, imports <live_tv|vod|both>, priority <p>

Refresh states (<n>)
  <source name or id> / <playlist|epg|catalogue>: <status>, items <n>, failures in a row <n>
    attempted <time>, succeeded <time|never>, failed <time|never>
    last error: <message in the TV's language>

Recent events (<n>)
  <log line>
```

- Empty lists print `  none`. Times use the system zone, `yyyy-MM-dd HH:mm:ss`. Refresh states are
  sorted by source name (or id), then kind. The stored failure of a refresh state is resolved to
  words in the TV's current language before rendering.
- **Display line**: the panel part comes from `DisplayManager.getDisplay(DEFAULT_DISPLAY)`
  (`mode.physicalWidth/Height`, `mode.refreshRate`, `getRealMetrics`); if unavailable only the app
  part is printed. The app part is the app's `displayMetrics` and `configuration.screenWidthDp/
  screenHeightDp`. Purpose: a panel mode larger than the reported size is normal (the Shield drives
  3840 × 2160 and reports 1920 × 1080); an app window smaller than the reported size means something
  outside the app insets it. It **cannot** show device graphics-plane scaling (a TV's own "screen
  position/size" setting scales the UI plane while video on its own plane fills the panel; the app is
  told it has the full screen).
- Sources show only name, type, enabled, import scope and priority — never addresses or
  credentials. After rendering, **every line** passes through the redactor again.
- Size: bounded by counts (header ~15 lines, ≤ 100 sources, ≤ 3 refresh kinds × 3 lines per source,
  ≤ 600 events); estimated 10–60 KB (not measured).

## 8. Edge cases and limits

- **Offline TV at start**: the automatic check fails and About shows the NETWORK failure in `danger`
  until the next check; nothing else in the app changes.
- **Leaving Settings during a download**: the download runs in the root screen's coroutine scope; if
  that scope is cancelled (activity recreated, e.g. by an interface-language change) the blocking
  HTTP read continues, and after it the state is never updated, so the phase stays DOWNLOADING and
  further checks are ignored until the process restarts (by code reading). Rebuild: run check and
  download in an application-scoped job with cancellation-safe state transitions and a real cancel.
- **Rate limit or GitHub outage**: NETWORK failure; the next automatic check is a day later.
- **More than 10 newer releases** (a tester returning after months): the newest is still in the first
  10, so it is offered; the installed build's own notes are no longer found (the last stored copy is
  shown).
- **Release without the build line** (happened with beta 13; corrected after publication): no
  installed build can see it. **Release without checksums**: NO_CHECKSUMS for everyone.
- **Profile mismatch or missing** on a device: silent fallback to a plain install (logged).
- **Session install refused** (some TV firmware blocks session installs from apps): FAILED outcome →
  the next Install uses the package-installer screen.
- **Cache cleared between download and install**: FAILED NETWORK today (see ABOUT-FR-12).
- **Downgrade**: never offered (strictly greater build only).
- **Storage full**: the download fails with an I/O error → NETWORK.
- **No browser or mail app** on the TV: links do nothing (ABOUT-FR-23).
- **No document picker**: `ActivityNotFoundException` from the diagnostics launcher is not caught in
  beta 23 (by code reading). Rebuild: catch it and say so.
- **Diagnostics after a crash**: the in-memory log is empty in the new process; the file cannot
  explain a crash (open question 3).
- **Clock changes**: a clock set back by more than a day delays the next automatic check until the
  stored time is passed again (the difference is negative); rebuild: treat a negative difference
  as due.

## 9. Lightweight by design

- ABOUT-NFR-01 **Nothing at start-up beyond one conditional request.** The automatic check reads one
  preference and, at most once a day, makes one HTTPS request of about 90 KB (91,526 bytes for the
  ten releases on 23 September 2026) on the IO dispatcher at background priority, after the first
  frame. Beta 23 constructs the checker eagerly in the container
  on the main thread, which opens `streammate_updates` and makes two `PackageManager` binder calls
  during start-up; the rebuild reads version name and code from `BuildConfig` constants and opens
  the preferences lazily, off the main thread.
- ABOUT-NFR-02 **Streamed download and hashing.** The APK (7.8 MB in beta 23) is never held in
  memory: 64 KiB buffer, the digest updated from the same buffer, one pass. Only the profile (≤ 1 MiB,
  about 9 KB) is read whole. Progress state changes ≤ 101 times per download.
- ABOUT-NFR-03 **Recompose only About.** Beta 23 collects the updater state in the root composable,
  so each percent step recomposes the whole app shell and `releaseNotes` (regexes over the body) runs
  inside that mapping on the main thread at every recomposition. Rebuild: the state is collected only
  by the About section; notes are converted once per release body on a background thread and cached
  with the update.
- ABOUT-NFR-04 **The legal screen is text.** It is a lazy list of ten cards with no images except
  the TMDB logo. Beta 23 decodes the SVG through Coil + AndroidSVG at runtime (here and in Settings ›
  Library). Rebuild: ship the logo as a VectorDrawable converted from the unmodified SVG (identical
  appearance, no SVG decoder dependency, [design/04](../design/04-icons-and-imagery.md)), and keep the
  screen free of animations beyond the standard focus style.
- ABOUT-NFR-05 **Diagnostics cost nothing until saved.** Appending a line costs one redaction (two
  precompiled regexes, `URI` parse per URL found) and a lock; it runs on the caller's thread, so no
  caller may log from a per-frame or per-row path (the `Guide` line logs only reads of ≥ 2,000 rows or
  ≥ 250 ms). The 600-line buffer holds at most ~600 × 500 characters ≈ 0.6 MB with the rebuild cap.
  Rendering (≤ ~1,500 lines, one regex pass per line) runs on a background thread (beta 23 renders on
  the calling main-thread coroutine after collecting on IO).
- ABOUT-NFR-06 **One HTTP client.** Updates use the app's shared client (no own pool or threads)
  ([plan/03](../plan/03-architecture.md) §3.3 counts seven clients today).
- ABOUT-NFR-07 **Install time is the fast path for the first start.** Carrying the `.dm` profile is the
  lightweight feature here: on the Shield an uncompiled update took 922 ms for Home's first composition
  against 218 ms compiled, and 408 ms against 92 ms for the first guide frame; on the low-end box the
  gap is larger. Every release must carry both profiles (§7.5), and the baseline profile must cover
  start-up, Home, guide and player ([plan/07](../plan/07-performance.md)).

## 10. Lessons from the current app

- **The release body is an API.** Beta 13's release first lacked `Android build **14**`, so no
  installed build could see it until the notes were corrected (private
  `docs/PUBLIC_RELEASE_CHECKLIST.md`). Since then the packaging and publish scripts refuse a body
  without the line ([plan/08](../plan/08-lessons-learned.md) 8.4).
- **Sideloaded and in-app updates ran interpreted for a day.** `ACTION_VIEW` cannot carry a profile;
  ProfileInstaller only copies the embedded profile at first launch and nothing compiles it until the
  idle dexopt job. The session installer with `base.dm` (commit `1a0f3f0`, 23 September 2026) fixed it
  for updates made from build 54 on; proven through the app on the Shield the same evening
  ([plan/08](../plan/08-lessons-learned.md) 3.6).
- **Checksums before install.** Download → verify → only then offer Install; a mismatch deletes the
  file. Keep it; also verify the profile, and never pass an unverified profile.
- **Stuck phases.** A download cancelled with its UI scope leaves DOWNLOADING forever (§8); NETWORK
  text used for non-network failures.
- **English-only strings**: `update_notes_for`, `update_disabled_development`, `lab_safety_notice`;
  the release notes themselves are bilingual and cut at 4,000 characters.
- **Diagnostics were added for a real report** (7–8 September 2026, commits `b5902cc`, `f65a4a1`): a
  tester's UI sat in a frame while video filled the panel. The Display line proved the app had the
  full window; the cause was the device's graphics-plane scaling, which the file cannot show. Keep the
  line; do not chase an app fix for that report.
- **Diagnostics lacked guide timings** when the owner sent one on 18 September 2026 (ledger entry):
  it had the start-up and Home readiness lines but nothing about the guide. The `Guide` channel-rows
  line (count, pages, milliseconds, attempts) was added the same day with the paged roster read
  (commit `31ae7f5`), so a saved file tells a slow read from a slow screen. Keep counts and timings;
  add frame summaries only if cheap.
- **Privacy wording vs behaviour.** PRIVACY.md says addresses are removed; the redactor keeps scheme,
  host and port (`https://provider.example:8443/<redacted>`), so provider host names appear in
  `http` lines, and source names appear as typed. Either say "host names are kept" in the privacy
  text or drop hosts in the file (open question 2). PRIVACY.md's header still says it applies to
  "beta.1 through beta.17".
- **Legal texts drift.** The open-source notice names a library that left in beta 23 and omits
  others; palette licences are shipped but not shown ([assets/README.md](../assets/README.md) says
  the legal screen shows them — it does not).
- **Return from the legal screen lands on Playlists** (§3).

**Open questions (for the owner)**

1. Should About show a small "update available" hint elsewhere (Home or the Settings rail)? Beta 23
   shows it only inside About.
2. Diagnostics: keep provider host names (useful for "which source failed") or reduce them to the
   source name?
3. Keep a small persistent diagnostics ring (for example the last 200 lines in `noBackupFilesDir`,
   redacted) so a crash or a freeze can be explained after a restart, and record
   `ApplicationExitInfo` reasons on Android 11+? Today nothing survives the process.
4. Show release notes in the interface language (needs a body convention with `## English` /
   `## Suomi` sections, which the current bodies already have)?
5. Should the legal screen show `theme-licenses.txt` and a generated dependency list?

## 11. Acceptance tests

Unit (JVM)
- Feed parsing with captured JSON: assets, stated build, drafts skipped, releases without a build line
  or without an APK never offered, older/equal builds not offered, the highest build wins, prerelease
  offered (mirrors `AppUpdatesTest`).
- Profile choice: SDK 30 → `.api28.dm`, SDK 31 and 35 → `.api31.dm`, SDK 27 → none; a release without
  profiles is still an update.
- Checksum lines: two-space and `*name` forms, CRLF, upper-case digest, wrong length, other file names,
  first match wins.
- Notes: the "Changed since" section as plain lines (links, bold, backticks, wrapping, bullets); a body
  without the heading shown whole; blank body → none; the beta 23 body (committed fixture) gives the
  expected first 4,000 characters.
- UI mapping: disabled build explains itself; up to date carries installed notes; available shows its
  notes; failed download keeps the update's name and notes (mirrors `AppUpdateUiMappingTest`).
- Check timing: due after 24 h, not before; a negative difference is due (rebuild rule); the time is
  stored on success only (rebuild rule).
- Diagnostics log: line format with a pinned clock; addresses and keys never reach the buffer; only
  the newest 600 lines kept; a 2,000-character message stored at 500 (mirrors `DiagnosticsLogTest`).
- Diagnostics file: names app, device, each source and each refresh state; Display line carries panel
  and app sizes; nothing secret survives (`user:pw@`, `password=`, playlist path) while the host
  remains (mirrors `DiagnosticsReportTest`).

Instrumentation
- Development packages cannot check, download or launch the installer (mirror
  `LabUpdateIsolationTest`).
- The Display line has both parts on a real display (mirror `DiagnosticsDisplayTest`).
- Opt-in, emulator only: a session with APK and `.dm` is accepted and installed (mirror
  `UpdateSessionInstallerDeviceTest`; a host script taps the confirmation), and
  `dumpsys package` shows `reason=install-dm`.
- A fake feed server (MockWebServer) drives every phase of §4.6 including NO_CHECKSUMS,
  CHECKSUM_MISMATCH (one flipped byte), NEEDS_PERMISSION and a profile that fails its checksum
  (update still installs without it). Focus stays on a visible control through CHECKING and
  DOWNLOADING.
- Legal screen: every card reachable with the D-pad; Back returns to About with focus on the licences
  button; each link button with no handler shows the address instead of doing nothing.
- Save diagnostics writes a file whose name matches `sohva-tv-diagnostics-\d{8}-\d{4}\.txt`.

Release gate (every release)
- Anonymous feed read after publication: the release is visible, states the intended build, names the
  APK and both profiles in `SHA256SUMS.txt`, and the downloaded assets' SHA-256 match the frozen files.

Manual device checks
- Update the Shield and the low-end box from the previous public build through Settings › About; the
  confirmation is Android's; afterwards `dumpsys package com.streammate.tv` shows `base.dm` and
  `reason=install-dm`, and data is intact.

Performance (low-end box or the slow-box emulator)
- Cold start with a due check: no main-thread slice from the updater in a Perfetto trace; the request
  starts after the first frame.
- During a download in About, frame times of the About screen stay within budget and no other screen
  recomposes (Layout Inspector recomposition counts).
- First Home composition after an in-app update is within 1.5× of the same build after
  `cmd package compile -m speed-profile` (proves the profile was used).

## 12. Reference: current code map

| File (beta 23) | Role |
|---|---|
| `app/.../app/AppUpdateChecker.kt` | States, daily/manual check, download and verification, profile fetch, install, permission page |
| `app/.../app/AppUpdates.kt` | Feed URL, release parsing, build-line regex, update selection, profile choice, notes conversion, checksum lookup |
| `app/.../app/UpdateSessionInstaller.kt` | PackageInstaller session with `base.apk` + `base.dm`, status receiver |
| `app/src/main/res/xml/update_paths.xml` | FileProvider path `cache-path updates/` |
| `app/src/main/AndroidManifest.xml` | FileProvider `${applicationId}.updates`, `REQUEST_INSTALL_PACKAGES` |
| `app/.../app/AppRuntimePolicy.kt` | Which packages may update (`com.streammate.tv` only), Lab flags |
| `app/.../app/StreamMateApp.kt` | Automatic check, state collection, `toUiState` mapping, About wiring, diagnostics wiring |
| `iptv/.../feature/settings/AppUpdateUiState.kt` | Phases, failures and actions the About section understands |
| `iptv/.../feature/settings/AboutSection.kt` | UPDATES group, `MAX_UPDATE_NOTES_LENGTH` (also the phone-setup dialog) |
| `iptv/.../feature/settings/SettingsScreen.kt` | About section layout, translations URL, diagnostics picker and file name |
| `app/.../feature/legal/LegalInformationScreen.kt` | Legal screen, texts, links, TMDB logo |
| `iptv/src/main/assets/tmdb_attribution.svg`, `core/src/main/assets/theme-licenses.txt` | TMDB logo; palette MIT notices |
| `app/.../app/DiagnosticsReport.kt` | Diagnostics facts, rendering, Display line, writing |
| `core/.../core/diagnostics/DiagnosticsLog.kt` | 600-line redacted in-memory log, logcat mirror |
| `core/.../core/security/SecretRedactor.kt` | Redaction used by the log and the file |
| `scripts/package-sohva-beta.ps1`, private `.local/beta23-code57-release/publish.ps1`, `verify-publication.ps1` | Profiles, checksums and release-body gates |
| Tests: `AppUpdatesTest`, `AppUpdateUiMappingTest`, `DiagnosticsReportTest`, `DiagnosticsLogTest`, `LabUpdateIsolationTest`, `UpdateSessionInstallerDeviceTest`, `DiagnosticsDisplayTest` | Existing coverage to mirror |
