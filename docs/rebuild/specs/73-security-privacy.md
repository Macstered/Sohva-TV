# Security and privacy

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

This spec owns the on-device key material and ciphers, the secret settings store, redaction, the
URL and network policy, the Android backup rules, the manifest permissions and exported
components, the privacy commitments (what goes to which third party and what never leaves the TV),
and the release-hygiene audits. The encrypted `.smbak` file is in [Backup and restore](71-backup-restore.md);
diagnostics and the legal screen in [Updates, About and diagnostics](72-updates-about-diagnostics.md);
per-feature data flows in their own specs (linked in §4.9).

## 1. Summary

Sohva TV handles secrets that are worth stealing: IPTV addresses with embedded credentials, Xtream
user names and passwords, the viewer's own TMDB and API-Sports keys, Trakt tokens and addon URLs
that often carry a configuration token. All of them are encrypted at rest with a key that the
Android Keystore protects, never printed in logs, errors or diagnostics, and never sent anywhere
except to the service they belong to. The app has no account, no analytics, no advertising, no
crash upload and no server of its own; optional services are called directly from the TV with the
viewer's own credentials. Android cloud backup and device transfer are switched off; the only
portable copy is the password-encrypted `.smbak` the viewer asks for. Before any source or APK is
published, scripted audits and a secret scanner check that nothing private ships.

## 2. Feature checklist

Secrets at rest
- SEC-01 IPTV source lists (addresses, user names, passwords), every stored stream address, the
  parental PIN, the TMDB token, the API-Sports key, Trakt tokens and pending scrobbles, and Discover
  addon URLs and payloads are stored encrypted (AES-256-GCM).
- SEC-02 One Android Keystore AES-256 key per store wraps a random 256-bit software data key
  (envelope encryption): the keystore is used once per process, values are encrypted in software.
- SEC-03 Values written in the older direct-keystore format (`v1:`) still decrypt; nothing needs a
  migration pass.
- SEC-04 The single-source settings of StreamMate's first builds are migrated into the encrypted
  source list on first read and the old file is cleared.
- SEC-05 The parental PIN (4–8 digits) is stored encrypted and compared in constant time.
- SEC-06 A stream address is decrypted only when it is played, inside the playback data source;
  the media session and its notification see only a placeholder id.

Redaction
- SEC-07 Every error text shown on screen, every diagnostics log line and the diagnostics file pass
  through one redactor: URLs keep only scheme, host and port; `key=value` secrets become
  `<redacted>`.
- SEC-08 Objects that hold secrets print `<redacted>` from `toString()` (sources, metadata and
  sports settings, stream requests, addon documents and endpoints).
- SEC-09 The Playlists list shows a source only by the name the viewer gave it; its address and
  credentials appear only on its own page, with the password masked.

Input and network policy
- SEC-10 A source address must be an `http` or `https` URL with a host.
- SEC-11 Keys are validated before storage: TMDB token ≤ 2,048 characters, API-Sports key ≤ 512,
  neither may contain a line break.
- SEC-12 Plain HTTP is permitted for the viewer's own IPTV hosts; the first-party service domains
  (API-Sports, TMDB, TVmaze) can only be reached over HTTPS.
- SEC-13 Only the system's certificate authorities are trusted; no user-added CAs, no pinning.
- SEC-14 Discover addons are HTTPS-only in practice (no UI path accepts HTTP).
- SEC-15 The phone setup page runs only while its dialog is open, on the LAN address, a random port
  and an 8-character one-time token ([Phone setup](11-phone-setup.md)).

Platform
- SEC-16 Android cloud backup and device-to-device transfer are disabled for all app data.
- SEC-17 A minimal permission set, each with a stated reason (§4.8).
- SEC-18 Only the launcher activity and the media-session service are exported by the app; the
  session accepts only this app, trusted system controllers and its own notification.
- SEC-19 The release build is not debuggable; it is profileable by the shell only.

Privacy
- SEC-20 No developer account, analytics, advertising, telemetry, crash upload or first-party
  server.
- SEC-21 Each third party receives only what its feature needs and only when the viewer has turned
  that feature on (§4.9 table).
- SEC-22 The update check sends GitHub nothing about the viewer beyond the request itself.
- SEC-23 Stream, playlist and guide requests identify the app as `Sohva TV/<version> (Android TV
  <release>)` unless the playlist sets its own user agent.
- SEC-24 The in-app privacy summary, the "security note" under source settings, and the published
  privacy policy state these commitments; a security contact is published (SECURITY.md).

Release hygiene
- SEC-25 A public-source content audit runs before every source publication.
- SEC-26 A release-document audit runs over the tester package.
- SEC-27 An APK safety audit checks package, version, label, non-debuggable, the permission
  allowlist, the signing certificate and secret-shaped content in every APK entry.
- SEC-28 Gitleaks scans the full public history, the staged commit and the release package.
- SEC-29 Build-time secrets (the Trakt client id and secret) are injected at build time and never
  appear in the public source.

## 3. Entry points and navigation

There is no security screen. What the viewer meets:
- Settings › Playlists: the security note `settings_security_subtitle` "Credentials are encrypted on
  this device and portable backups are protected with your password" under the status line
  (caption, `textDim`), and masked password fields ([design/screens/settings.md](../design/screens/settings.md) §6).
- Settings › Library and Sohva Sport: masked key fields and the disclosures `metadata_disclosure`,
  `sports_disclosure`; `settings_http_disclosure` for HTTP.
- Settings › About › About, privacy and licences: the "On-device privacy" card and the service
  notes ([Updates, About and diagnostics](72-updates-about-diagnostics.md) §4.7).
- The parental PIN gate ([Profiles and parental controls](04-profiles-parental.md)).
- The phone setup dialog's note `phone_setup_privacy` "The page lives on this TV only while it is
  open. What you send is saved here and nowhere else." ([Phone setup](11-phone-setup.md)).
- Outside the app: `PRIVACY.md`, `SECURITY.md`, `THIRD_PARTY_NOTICES.md` in the tester package and
  the public repository.

## 4. Behaviour

### 4.1 Key material

- SEC-FR-01 Main keystore key: alias `sportmate.iptv.v1` in `AndroidKeyStore`; AES, 256 bits,
  purposes ENCRYPT | DECRYPT, block mode GCM, padding NONE; no user authentication, no StrongBox
  request, randomised encryption (the provider generates the IV; supplying one is rejected on
  devices). Generated on first use if absent.
- SEC-FR-02 The key handle is cached in memory for the process after the first load (a keystore
  load is a binder round trip; before the cache it happened once per catalogue row). An
  `invalidate()` exists but nothing calls it.
- SEC-FR-03 Main data key: 32 random bytes (`SecureRandom`), stored wrapped in SharedPreferences
  `streammate_secret_envelope`, key `data_key_v2`, as the keystore cipher's `v1:` string of the key's
  lower-case hex. Unwrapped once per process and kept as a `SecretKeySpec`. If no wrapped key
  exists a new one is generated and saved; if the stored one unwraps to a wrong length a new one is
  generated and **overwrites** it (all older `v2:` values then become unreadable); if unwrapping
  throws, every encrypt/decrypt throws.
- SEC-FR-04 Discover has its own pair: keystore alias `sohva.addons.v1`, SharedPreferences
  `sohva_addon_secret_envelope`, key `data_key`, saved with a synchronous `commit()` before any
  ciphertext is written ([Discover](50-discover-addons.md) ADDON-FR-125). The main store saves its
  wrapped key with the asynchronous `apply()`, so a process killed right after the first key was
  generated can lose it while `v2:` values written with it are already on disk. **Rebuild rule:**
  persist a new wrapped key synchronously (commit + fsync) before its first use, in both stores.
- SEC-FR-05 Keystore aliases, preference file names, keys and value formats are part of the
  in-place upgrade contract: a rebuild that keeps the old installation's data (the default choice in
  [plan/04](../plan/04-data-model.md)) must read them unchanged.

### 4.2 Ciphers and value formats

- SEC-FR-06 Keystore cipher (`v1`): `AES/GCM/NoPadding` with the keystore key, provider-chosen
  12-byte IV, 128-bit tag, AAD = UTF-8 `streammate-secret-v1`. Stored as
  `v1:<IV hex>:<ciphertext+tag hex>` (lower-case hex).
- SEC-FR-07 Envelope cipher (`v2`): `AES/GCM/NoPadding` with the software data key, a fresh random
  12-byte IV per value, 128-bit tag, AAD = UTF-8 `streammate-secret-v2`. Stored as
  `v2:<IV hex>:<ciphertext+tag hex>`. Decrypt: a value that does not start with `v2:` goes to the
  keystore cipher (so `v1:` values written before September 2026 still read); a tampered value fails
  the tag check.
- SEC-FR-08 Plaintext is UTF-8. Hex is produced by a lookup table (not `String.format`, which made
  encryption the hottest task of a playlist refresh).
- SEC-FR-09 The Discover cipher is the same envelope construction with its own key (ADDON-FR-125).
- SEC-FR-10 The portable backup uses a different, password-based construction (PBKDF2 + AES-GCM,
  [Backup](71-backup-restore.md) §7.1); it never uses the keystore, so a backup restores on another
  TV.

### 4.3 What is encrypted, and what is not

| Data | Store | Cipher |
|---|---|---|
| IPTV source list (all fields incl. URLs, user names, passwords) | `streammate_secure_sources` › `sources_v1` (codec, [Sources](10-sources-and-import.md) SRC-FR-108) | main envelope |
| Parental PIN | same file › `parental_pin_v1` | main envelope |
| TMDB token | same file › `metadata_tmdb_token_v1` (switches `metadata_tmdb_enabled_v1`, `metadata_tvmaze_enabled_v1` are plain booleans) | main envelope |
| API-Sports key | same file › `sports_api_key_v1` | main envelope |
| Stream address of every channel, film and episode | `encryptedStreamUrl` column of the imported rows | main envelope, one value per row |
| Trakt tokens, account, pending and active scrobbles, cached recommendation/next-up lists | SharedPreferences `trakt_accounts` ([Trakt](51-trakt.md)) | main envelope |
| Discover addon URLs, manifests, Library, progress, response cache | Discover databases and cache files | Discover envelope |
| Legacy StreamMate settings | `sportmate_secure_settings` › `m3u`, `xmltv` (`v1:` values) | keystore cipher; migrated and cleared |
| Everything else: guide, catalogue, metadata, sports caches, watch progress, favourites, profiles, preferences, reminders, logos | Room database, DataStore, files | **not** encrypted by the app (the privacy policy says so) |

### 4.4 Secret settings store

- SEC-FR-11 SharedPreferences file `streammate_secure_sources` (private mode). Every public method
  is `@Synchronized`; writes use `commit()` and fail loudly ("Could not persist encrypted IPTV
  sources", `error_metadata_settings_save`, `error_api_sports_settings_save`).
- SEC-FR-12 `loadSources()` decrypts and decodes on every call; a record that cannot be decrypted or
  decoded yields an **empty list** (sources silently vanish; [Sources](10-sources-and-import.md)
  SRC-FR-109 gives the rebuild rule: report it and keep the raw record). With no record it migrates
  the legacy pair `m3u`/`xmltv` into one M3U source with id `m3u-primary` and clears the legacy file.
- SEC-FR-13 TMDB: the token is trimmed; > 2,048 characters or a CR/LF → `error_tmdb_key_invalid` "The
  TMDB API key or read access token is invalid"; enabled with a blank token →
  `error_tmdb_key_required`; a blank token removes the stored one. TMDB counts as enabled only when
  the switch is on **and** a token decrypts.
- SEC-FR-14 API-Sports: trimmed; > 512 characters or a CR/LF → `error_api_sports_key_invalid` "The
  API-Sports key is invalid"; blank removes it.
- SEC-FR-15 PIN: must match `\d{4,8}` (`error_pin_format` "The PIN must be 4–8 digits"); stored
  encrypted; `parental_pin_configured` in the DataStore mirrors its presence for the UI.
  Verification decrypts the stored PIN and compares UTF-8 bytes with `MessageDigest.isEqual`; a
  missing PIN or a decryption failure is "wrong". No retry limit or lockout (owner decision in
  [Profiles](04-profiles-parental.md)).
- SEC-FR-16 The PIN digits leave the store only into the encrypted backup payload
  (`parentalPinForEncryptedBackup`).

### 4.5 Redaction

- SEC-FR-17 `redact(message)`: `null` or blank → `null` (the caller chooses its localised fallback,
  e.g. `error_unknown` "Unknown error"). Otherwise, in order:
  1. every match of `https?://[^\s"'<>]+` (case-insensitive) is replaced: trailing `.`, `,`, `)`,
     `]`, `}` are trimmed before parsing with `java.net.URI`; with a host the result is
     `<scheme lower-case>://<host>[:<port>]/<redacted>`; without a host or on a parse failure,
     `<redacted-url>`. User info, path, query and fragment are all dropped;
  2. every match of `(?i)(api[-_]?key|token|password|username|user|pass)=([^&\s]+)` becomes
     `<name>=<redacted>`.
  Example: `Failed https://viewer:hunter2@provider.example:8443/live/abc?token=topsecret` →
  `Failed https://provider.example:8443/<redacted>`.
- SEC-FR-18 Where it runs: the diagnostics log (message and exception message, before storing), the
  diagnostics file (every line again), `Throwable.userMessage` (every non-localised error shown on
  screen), transport failures wrapped for display, the guide source client's failure detail, the
  player's error text (cut to a maximum length), the M3U "not an M3U document" first-line excerpt
  (first 60 characters), and Xtream client errors.
- SEC-FR-19 Limits (keep in mind, fix where cheap): host names and ports survive; a URL without a
  scheme (`provider.example/live/user/pass/1.ts`) or an Xtream path quoted outside a URL is not
  recognised; only the listed names are caught (`apikey`, `api_key`, `api-key`, `token`, `password`,
  `username`, `user`, `pass`), so `key=`, `auth=`, `access_token=` or a header line
  `Authorization: Bearer …` pass through. Rebuild: add `key`, `auth`, `authorization` (also as a
  header), `access_token`, `refresh_token`, `code`, and the user/password segments of the known Xtream
  path shapes (`/live|movie|series|timeshift/<user>/<pass>/…`), each with a test.
- SEC-FR-20 `toString()` of secret-bearing models prints `<redacted>` or `[redacted]`
  (`IptvSourceConfiguration` → `credentials=<redacted>`, `MetadataSettings`, `SportsApiSettings`,
  playback requests, catch-up URLs, Xtream requests, addon documents, endpoints and imports), so an
  accidental log or crash message does not expose them.

### 4.6 URL and input policy

- SEC-FR-21 `IptvSourceUrlPolicy.normalize(value, label)`: trim; parse with `java.net.URI`; the
  scheme (lower-cased) must be `http` or `https` and the host non-blank, else
  `error_source_url_invalid` "The %1$s address must be a full address starting with http:// or
  https://" (label: "M3U", "XMLTV", or the localised Xtream server label). Returns the trimmed text
  unchanged. Used for M3U, XMLTV and Xtream server addresses and by the guide source client before
  every fetch. Note: `java.net.URI` reports no host for names containing `_`, so such provider hosts
  are refused (rebuild: parse with OkHttp's `HttpUrl`, which accepts them, and keep the scheme rule).
- SEC-FR-22 Source model rules (id `[A-Za-z0-9._-]{1,128}`, name ≤ 100, connection limit 1–16, EPG
  offset ±12 h in 30-minute steps) are enforced in the model's constructor
  ([Sources](10-sources-and-import.md)).

### 4.7 Network security

- SEC-FR-23 Network security configuration (Android 7+):
  - base: `cleartextTrafficPermitted="true"`, trust anchors = system certificates only (user-installed
    CAs are not trusted);
  - domain config `cleartextTrafficPermitted="false"` with `includeSubdomains="true"` for
    `api-sports.io`, `themoviedb.org`, `tmdb.org`, `tvmaze.com`.
  - The manifest also sets `usesCleartextTraffic="true"` (the only rule on Android 6, where the
    configuration file is ignored and user CAs are trusted by default).
- SEC-FR-24 Why cleartext stays allowed: playlist, guide and stream hosts are typed by the viewer and
  often plain HTTP; a blanket ban stops the app working. The allowance is limited in intent to
  those hosts; everything the app contacts on its own behalf uses HTTPS URLs.
- SEC-FR-25 Gap: Trakt (`trakt.tv`), GitHub (`github.com`, `api.github.com`, the
  `githubusercontent.com` download CDN) and Stremio (`strem.io`, `stremio.com`) are HTTPS in code but
  not in the HTTPS-only list. **Rebuild:** add them, and add a test that every hard-coded host is in
  the list (the current `IptvNetworkSecurityTest` checks five hosts).
- SEC-FR-26 The shared HTTP client follows redirects, including HTTPS→HTTP (`followSslRedirects`).
  Rebuild: refuse a redirect from HTTPS to HTTP for first-party services and for the updater; keep
  it for provider hosts (some providers need it).
- SEC-FR-27 No certificate pinning (a retired private backend's pin was removed in August 2026,
  §10). TLS errors surface as redacted transport failures.

### 4.8 Platform: backups, permissions, components

- SEC-FR-28 `allowBackup="false"`; `fullBackupContent` (Android ≤ 11) and `dataExtractionRules`
  (Android 12+, both `cloud-backup` and `device-transfer`) exclude the domains `root`, `file`,
  `database`, `sharedpref` and `external` entirely. Nothing of the app goes to Google backup or a
  device-to-device transfer; keystore-bound data would be unreadable on another device anyway.
- SEC-FR-29 Permissions in the release APK (the APK audit's allowlist, §4.10):

| Permission | Declared by | Why |
|---|---|---|
| `INTERNET` | app (also OkHttp) | Sources, playback, optional services, updates |
| `ACCESS_NETWORK_STATE` | WorkManager, Media3 | Network constraints of background refresh; player connectivity |
| `WAKE_LOCK` | WorkManager, Media3 | Background work and playback wake handling |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | app (also WorkManager) | The media-session playback service (`foregroundServiceType="mediaPlayback"`) |
| `RECEIVE_BOOT_COMPLETED` | app (also WorkManager) | Reschedule reminder alarms and periodic work after a reboot |
| `POST_NOTIFICATIONS` | app | Programme and match reminders (Android 13+ runtime permission) |
| `SYSTEM_ALERT_WINDOW` | app | Optional, granted by the viewer in TV settings: a due reminder may bring the app forward ([Reminders](22-catchup-and-reminders.md)); nothing is drawn over other apps |
| `REQUEST_INSTALL_PACKAGES` | app | Installing a downloaded, verified update; the system still asks ([Updates](72-updates-about-diagnostics.md)) |
| `com.streammate.tv.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX Core | Signature-level permission protecting non-exported runtime receivers on older Android |

  The Lab build removes `REQUEST_INSTALL_PACKAGES` and `RECEIVE_BOOT_COMPLETED`. No location,
  storage, microphone, camera, contacts, accounts or phone permissions: documents go through the
  system picker, so no storage permission is needed.
- SEC-FR-30 Components (merged release manifest):
  - `MainActivity`: exported (launcher, `LEANBACK_LAUNCHER`), `singleTask`, landscape.
  - `StreamMatePlaybackService`: exported (required for `MediaSessionService`); `onConnect` accepts a
    controller only when it is this package, `isTrusted` (system), or the media notification
    controller, otherwise rejects. Media items carry a channel id and extras; the real address is
    resolved from the placeholder scheme `streammate` inside the data source.
  - `ReminderReceiver`: not exported (boot action is delivered by the system).
  - `FileProvider` `${applicationId}.updates`: not exported, grants per URI, path `cache/updates/`.
  - Library components: WorkManager `SystemJobService` (exported, `BIND_JOB_SERVICE`),
    `DiagnosticsReceiver` and ProfileInstaller `ProfileInstallReceiver` (exported, `DUMP`), Media3
    `BluetoothValidationActivity` (exported, `BLUETOOTH_PRIVILEGED`), all others not exported.
- SEC-FR-31 `<profileable android:shell="true">`: lets the shell profiler (Perfetto, simpleperf over
  adb) inspect the release build on a device; the app stays non-debuggable and other apps gain
  nothing. Keep it (performance work on the real release depends on it).
- SEC-FR-32 The Settings list never renders a source's address; the source page shows it only while
  editing (password masked with the platform transformation). Password-type keyboards are requested
  for the Xtream password, keys, PIN and backup password.

### 4.9 Privacy: what goes where

- SEC-FR-33 Nothing is sent to a Sohva/developer server; there is none. No analytics, advertising,
  telemetry, crash reporting or account. Reports reach the developer only when the viewer emails
  them.
- SEC-FR-34 Third parties, and only while the feature is in use:

| Recipient | Feature and trigger | What it receives | Credential | Transport |
|---|---|---|---|---|
| The viewer's IPTV provider hosts (playlist, XMLTV, Xtream API, stream and catch-up hosts, logo hosts) | sync, refresh, test, playback, catch-up | requests including the credentials the viewer configured (in URLs or Xtream parameters); user agent `Sohva TV/<version> (Android TV <release>)` unless the playlist sets `User-Agent` (then that, plus playlist headers such as referrer); IP address | the viewer's | as configured (HTTP or HTTPS) |
| TMDB (`api.themoviedb.org`, `image.tmdb.org`) | only when enabled with the viewer's token | title (≤ max query length), year, type, season/episode, language, TMDB/IMDb ids (Trakt synopses); user agent `SohvaTV/0.1 (Android TV; personal use)`; IP | a 32-hex v3 key as the `api_key` query parameter, otherwise `Authorization: Bearer` | HTTPS only |
| TVmaze (`api.tvmaze.com`, image host) | only when enabled | series title, show id, season/episode numbers; same user agent; IP | none | HTTPS only |
| API-Sports (`v3.football…`, `v1.<sport>…`, `v2.nba.api-sports.io`, logos) | only with the viewer's key, while Today or the ticker is visible ([Sohva Sport](60-sohva-sport.md)) | date, the chosen time zone id, competition/season ids; same user agent; IP | `x-apisports-key` header | HTTPS only |
| Trakt (`api.trakt.tv`, `auth.trakt.tv`) | only after the viewer connects a profile | device-code sign-in; scrobble start/pause/stop with TMDB or IMDb id, season/episode, progress %; reads playback, watched, recommendations, next up ([Trakt](51-trakt.md)) | OAuth tokens; build-time client id/secret | HTTPS |
| Addon hosts the viewer installed, and their media/subtitle hosts | Discover browsing and playback ([Discover](50-discover-addons.md)) | catalog/search requests, title ids, filters, subtitle lookups; IP (some addons use subtitle requests for their own watch tracking) | whatever the configured URL carries | HTTPS for manifests |
| Stremio (`link.stremio.com`, `api.strem.io`) | optional account copy of addon configurations | link-code flow, one addon-collection read; the temporary auth key is not stored | temporary | HTTPS |
| GitHub (`api.github.com`, release asset CDN) | update check once a day and on press; downloads on press | the request (default OkHttp user agent, IP) | none | HTTPS |
| A phone on the LAN | phone setup while the dialog is open | the setup page; sends back what the viewer typed | 8-character token in the URL | HTTP on the LAN |

- SEC-FR-35 Never leaves the TV (except inside the viewer's own `.smbak` or a diagnostics file they
  share): the PIN, profiles and restrictions, favourites, recents, watch progress (except to Trakt
  when connected), reminders, guide and catalogue data, match decisions, logos sent from a phone,
  settings. Nothing is sent for Live TV or catch-up viewing to any service other than the provider.
- SEC-FR-36 In-app privacy summary (legal screen, `app` module strings; keep meaning, translations in
  [reference/strings](../reference/strings/)):
  - `about_privacy_intro` "Sohva TV uses no user account, analytics, advertising or telemetry sent to
    the developer."
  - `about_privacy_local` "IPTV credentials, API keys, viewing history and settings remain on the
    Android TV device. Secrets are protected with Android Keystore, and portable backups are
    encrypted with the user's password."
  - `about_privacy_network` "The app connects to user-configured IPTV services and addons, their media
    hosts, and enabled API-Sports, TMDB and TVmaze services. Those services receive the network
    request and IP address under their own policies."
  - `about_privacy_cleartext` "HTTP is supported for IPTV, explicitly accepted addon connections and
    local phone setup. An unencrypted connection can expose credentials and viewing activity to
    others on the same network."
  - `about_privacy_retention` "Local data remains until the user clears it or uninstalls the app.
    Metadata and sports caches expire automatically."
- SEC-FR-37 Published policy (`PRIVACY.md` in the tester package and repository; the private docs
  hold English and Finnish revisions of 11 September 2026 prepared for beta 13, `PRIVACY_POLICY.md`
  and `PRIVACY_POLICY.fi.md`, older than `PRIVACY.md`) commits to: no developer account/analytics/ads/crash
  reporting/telemetry; Keystore encryption of credentials and keys (caches and viewing state not
  claimed encrypted); backups only on request, password-encrypted, never uploaded; Android backup and
  transfer disabled; data kept until cleared, replaced by a restore or uninstalled; direct
  connections only to configured or enabled services, the developer receives no copy; HTTPS for
  API-Sports, TMDB and TVmaze; HTTP only for the viewer's IPTV with a warning; no automatic reports;
  the update check's content and cadence; diagnostics only on request, local, redacted; the stream
  user agent; reminders local; phone setup local with an expiring link; Trakt's data exchange and
  disconnection; Discover's data exchange and that the backup excludes it; restricted profiles
  cannot use Discover or Trakt; parental controls are local. **Any behaviour change in the rebuild
  that touches one of these requires a policy update in both languages before release.**
- SEC-FR-38 `SECURITY.md`: report credential disclosure, unsafe URL logging, backup weaknesses,
  exported-component issues privately to the public contact; do not send working credentials,
  playlists, backups or device data; the supported line is the latest numbered beta; no guaranteed
  response time.

### 4.10 Release hygiene (practices to keep)

- SEC-FR-39 **Public-source content audit** (`scripts/Test-PublicSourceContent.ps1 -Path <tree>`),
  before every source publication. Fails on: top-level entries outside an allowlist (dirs `.github`,
  `app`, `addons`, `core`, `docs`, `gradle`, `iptv`, `scripts`, `sportmate`, `trakt`; the build files
  and the tester documents); file types `.aab .apk .apks .bak .db .jks .keystore .log .m3u .m3u8
  .smbak .sqlite .xmltv .zip`; names `keystore.properties`, `local.properties`,
  `secrets.properties`; build/generated/internal paths (`build/`, `artifacts`, `assets`, `captures`,
  `distribution`, `.gradle`, `.idea`, `.kotlin`, `.local`, agent folders); internal planning names
  (handoff, implementation plans, private addon lab docs); and, in text files, credential-shaped
  tokens (`gh[pousr]_…`, `github_pat_…`, `AIza…`, `AKIA…`/`ASIA…`, `sk-…`, `xox?-…`), private keys,
  URLs with embedded credentials (except reserved `.example/.test/.invalid` fixtures under test
  source sets), machine-specific Windows paths, private networks (`192.168.x.x`, `*.home.arpa`,
  backup locations) and personal e-mail domains.
- SEC-FR-40 **Release-document audit** (`scripts/Test-PublicReleaseContent.ps1 -Path <package>`): only
  approved file names and extensions; no names containing credential/secret/password/keystore/backup;
  no authorization or `x-apisports-key` headers, bearer tokens, credential URLs or query parameters,
  private/loopback/link-local addresses, `.home.arpa`/`.local` hosts, unapproved URL hosts or contact
  addresses. Run by the packaging script.
- SEC-FR-41 **APK safety audit** (`scripts/Test-PublicApkSafety.ps1 -Path <apk> -Version <name>
  -VersionCode <code>`): package `com.streammate.tv` with the expected version, label "Sohva TV", not
  debuggable; permissions ⊆ the allowlist of §4.8; APK Signature Scheme v2 verified with certificate
  SHA-256 `985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6`; every entry (≤ 64 MB)
  scanned for sensitive names (`.jks .keystore .p12 .pem .env .m3u(8) .xmltv`, credential/secret/
  password/user-data names, backup `.smbak`) and patterns (home paths, private IPv4, credential URLs
  and parameters, private keys, `sk-` and `AIza` keys, JWTs, the checkout path, the local user
  name); local signing passwords and secret-named environment variables are compared **without being
  printed**; the AGP source-revision record must be `$PROJECT_DIR` plus a 40-hex commit.
- SEC-FR-42 **Gitleaks**: full-history scan of the public clone (55 commits, no leaks, at beta 23), a
  scan of the staged release commit, and a scan of the release package; zero findings required, and a
  finding is investigated, never silenced to pass.
- SEC-FR-43 **Clean history**: the public repository receives one squashed commit per beta; private
  development history, signing material, internal plans, device captures and user data never go
  there ([plan/06](../plan/06-quality-testing-release.md), private `PUBLIC_RELEASE_REPOSITORY.md`).
- SEC-FR-44 **Signing**: the permanent release key never leaves its private location; scripts compare
  its passwords without printing them; only the public certificate fingerprint is published.

## 5. Screen anatomy

No screen of its own. Visual details of the places in §3: the security note is caption size in
`textDim` with 4 dp top padding under the Playlists status line; masked fields use the platform
bullet transformation inside `TvUrlField`; the legal privacy card follows the card anatomy in
[Updates, About and diagnostics](72-updates-about-diagnostics.md) §5.2.

## 6. Data

| Item | Location | Notes |
|---|---|---|
| Keystore key `sportmate.iptv.v1` | AndroidKeyStore | never exported; survives app updates; removed on uninstall/clear data |
| Keystore key `sohva.addons.v1` | AndroidKeyStore | Discover |
| Wrapped main data key | `shared_prefs/streammate_secret_envelope.xml` › `data_key_v2` | written with `apply()` today; rebuild: synchronous |
| Wrapped Discover data key | `shared_prefs/sohva_addon_secret_envelope.xml` › `data_key` | `commit()` |
| Secret settings | `shared_prefs/streammate_secure_sources.xml` | keys in §4.3 |
| Legacy secret settings | `shared_prefs/sportmate_secure_settings.xml` | read once, then cleared |
| Trakt | `shared_prefs/trakt_accounts.xml` | encrypted values |
| Encrypted stream addresses | Room tables of channels, films, episodes | `v2:` strings |

Nothing here is included in Android backup. Of these, only the source list and the PIN are in the
`.smbak` (decrypted into the password-encrypted payload).

## 7. External interfaces

- Android Keystore (`KeyStore.getInstance("AndroidKeyStore")`, `KeyGenerator` AES with
  `KeyGenParameterSpec`), JCA `Cipher` `AES/GCM/NoPadding`, `SecureRandom`, `MessageDigest`.
- Network hosts: §4.9 table. HTTPS-only list: §4.7.
- Android backup: disabled (§4.8). Document providers: only through the system picker.
- Value formats: §4.2; backup envelope: [Backup](71-backup-restore.md) §7.1.

## 8. Edge cases and limits

- **Keystore key lost** (clear data, some firmware updates or keystore resets on cheap boxes): the
  wrapped data key cannot be unwrapped; every secret read fails; sources look empty (SEC-FR-12) and
  stream addresses cannot be decrypted. Rebuild: detect "key unusable" once at start, tell the viewer
  plainly, and offer restore from `.smbak` or re-entering sources; never silently generate a new key
  over a wrapped key that failed to unwrap.
- **Wrong-length data key** overwrite (SEC-FR-03) destroys readable data: rebuild treats it as the
  same "key unusable" state.
- **Process killed right after the first key generation**: the `apply()` race (SEC-FR-04).
- **Android 6**: no network security configuration; the HTTPS-only domains are not enforced and user
  CAs are trusted; `PBKDF2WithHmacSHA256` may be missing ([Backup](71-backup-restore.md) §8).
- **Hosts with `_`** are refused by the URL policy (SEC-FR-21).
- **Redaction gaps** (SEC-FR-19); host names and source names appear in diagnostics
  ([Updates, About and diagnostics](72-updates-about-diagnostics.md) §10).
- **Clear text on the LAN**: HTTP providers and the phone setup page expose credentials to anyone on
  the network; the disclosures say so.
- **Secrets in memory**: decrypted strings live on the JVM heap until collected; they are never
  written to disk unencrypted. The rebuild does not cache decrypted provider passwords longer than
  needed except the source list cache of §9.
- **Keyboards**: a TV's on-screen keyboard or a remote-control app may learn typed text; password
  input types ask them not to (platform-dependent).
- **Clipboard**: the app never places secrets on the clipboard.
- **Rooted devices / adb backup**: out of scope; `allowBackup=false` blocks `adb backup`.

## 9. Lightweight by design

- SEC-NFR-01 **Keystore work never on the main thread.** A keystore operation is a binder call into
  the keystore daemon (milliseconds on a Shield, more on a Cortex-A35 box with a software keymaster).
  Beta 23 still does keystore-backed work on the main thread in three places: every film or episode
  selection on a details page asks "is metadata enabled", which reads the secret store and decrypts
  the TMDB token ([Movies and series](40-movies-and-series.md) §9.2 item 4 and §10 item 12); the guide
  does the same on every selection change ([Live TV guide](20-live-tv-guide.md) GUIDE-NFR-22); PIN
  verification runs on the UI coroutine ([Profiles](04-profiles-parental.md)). Earlier, series
  details re-imported episodes with a keystore encryption per row on the main thread (fixed
  29 August 2026, commit `4a58f35`). **Rule:** all secret-store and cipher calls run on the IO
  dispatcher; the UI observes derived flags.
- SEC-NFR-02 **Warm once, cache what is safe.** At start-up, after the first frame, a background task
  loads the keystore key and unwraps the data key once (both kept for the process). Derived,
  non-secret facts are cached and observed, never recomputed per frame: "TMDB enabled",
  "API-Sports configured", "PIN exists", the source list's non-secret fields (names, types, enabled,
  scope — also mirrored in `iptv_source_state`). The decoded source list may be cached in memory
  (invalidated on every write) because the data key that protects it is in memory anyway; no
  decrypted value is ever cached on disk or in UI state that is saved.
- SEC-NFR-03 **Software crypto per value, one cipher per thread.** The envelope made a
  3,000-channel import 9.3 s → 1.3 s on the emulator (September 2026). Still, beta 23 creates a
  `Cipher` per value and encrypts one address per imported row: 56,164 channels + 200,000 films is
  ≈ 256,000 cipher operations per full sync, each with a new IV and ~240-character hex output.
  Rebuild: reuse one `Cipher` instance per worker thread (`ThreadLocal`), re-initialised with a fresh
  IV per value; hex via a lookup table; and prefer deriving Xtream stream addresses from the source's
  encrypted credentials at play time instead of storing one encrypted address per row
  ([Sources](10-sources-and-import.md) SRC-L-13). M3U addresses stay encrypted per row.
- SEC-NFR-04 **Key derivation cost on the low-end box.** The backup's PBKDF2-HMAC-SHA256 at 210,000
  iterations is 420,000 SHA-256 compressions; on a 2 GHz Cortex-A35 expect roughly 0.2–1 s
  (unmeasured; depends on whether the platform uses the ARMv8 SHA-2 instructions). It runs only on
  an explicit save or restore, on IO, with a busy text; the budget is ≤ 1.5 s
  ([Backup](71-backup-restore.md) BACKUP-NFR-05). The keystore key and envelope need no KDF.
- SEC-NFR-05 **Streamed hashing.** SHA-256 of the update APK is computed from the download buffer in
  one pass (64 KiB, no re-read, never the whole APK in memory); the ≤ 1 MiB profile is hashed whole.
  The logo store's file-name prefix hashes only the channel id. The PIN is compared, not hashed.
  One hex helper serves all SHA-256 and cipher encodings (three copies today, [plan/03](../plan/03-architecture.md) §3.3).
- SEC-NFR-06 **Redaction only on cold paths.** Two precompiled regexes plus a `URI` parse per URL,
  run when a log line is written or an error is shown — never per frame or per row. Callers that log
  in loops log counts, not items (the guide logs one line per read of ≥ 2,000 rows or ≥ 250 ms).
- SEC-NFR-07 **No start-up cost from security.** The container must not touch the keystore or
  SharedPreferences on the main thread in `Application.onCreate` ([plan/03](../plan/03-architecture.md)
  §3.4); the audits and scanners are build-time only and cost nothing at run time.
- SEC-NFR-08 **Network config is free.** The platform applies the security configuration per
  connection; no interceptor or custom trust manager is needed or wanted.

## 10. Lessons from the current app

- **Keystore per value was most of a ten-minute import.** Every stream address went through the
  keystore daemon until the envelope cipher (commit `7aebb0d`, 5 September 2026); the key handle was
  memoised in the same period. Keep the envelope; never call the keystore per row.
- **Series details did keystore work on the main thread** (`4a58f35`, 29 August 2026): episodes of a
  cold series appeared after about six seconds instead of nine once moved to IO and memoised. The
  "is metadata enabled" decrypt on details pages and the guide is the same mistake, still present.
- **A private backend shipped in the public APK.** A retired sports gateway's host name, its LAN
  address and a pinned root certificate were still in the APK in late August 2026 (dead code). Removed
  in commit `9e09ded`, which also narrowed cleartext to user sources and made English the default
  locale; the APK audit now scans for private addresses and host names. Never let private endpoints
  into the source tree.
- **Redaction belongs at the display boundary.** Moving it into `userMessage()` (same commit) means a
  library exception with a playlist URL cannot reach the screen because a call site forgot to wrap it;
  re-wrapping into `IllegalStateException` to redact had also flattened localised messages.
- **Durable key before ciphertext.** Discover's store commits its wrapped key synchronously for this
  reason; the main store does not (SEC-FR-04).
- **Silent empty sources.** A decrypt failure looks like "no sources" and the next save overwrites the
  record (SEC-FR-12).
- **HTTPS-only list drifted** as services were added (Trakt, GitHub, Stremio) (SEC-FR-25).
- **Privacy text drift**: PRIVACY.md's header still says "beta.1 through beta.17"; it says addresses
  are removed from diagnostics while host names stay; the Finnish policy in private docs is a beta 13
  revision (11 September 2026); Trakt titles appear in diagnostics ([Trakt](51-trakt.md) §9 rule). Treat the policy as a
  spec: a change in behaviour and a change in the policy ship together.
- **Audits are cheap and routine.** The clean-history snapshot of 4 September 2026 (378 files) and
  the beta 23 source (747 files) passed the content audit and gitleaks with zero findings; the
  release receipts record the same checks for the betas in between. Run them from the rebuild's first
  public commit.
- **CI gates are part of security hygiene**: betas 6–11 shipped while the public Build workflow was
  red on lint nobody ran (8 September 2026); the packaging script now refuses to package without
  `lintDebug`, and publication waits for green hosted runs.

**Open questions (for the owner)**

1. PIN retry limit or back-off (none today)?
2. Should Trakt, GitHub and Stremio join the HTTPS-only list, and should HTTPS→HTTP redirects be
   refused for first-party services? (Recommended: yes to both.)
3. Allow HTTP addons after an explicit warning, as the privacy texts imply, or change the texts to
   "HTTPS only"?
4. Keep provider host names in diagnostics?
5. Android developer verification: the release checklist still has "Register the package and signing
   certificate through the applicable Android developer-verification path before its rollout requires
   it" open; who does it and when?
6. Publish a current Finnish privacy policy alongside `PRIVACY.md`?

## 11. Acceptance tests

Unit (JVM)
- Keystore-style cipher with a software key: round trip without storing plaintext, a fresh nonce per
  encryption, tampered ciphertext rejected (mirrors `SecretCipherTest`).
- Envelope: round trips without touching the keystore after the first call; the same data key on the
  next run; `v1:` values still decrypt; tampering rejected (mirrors `EnvelopeSecretCipherTest`);
  **new:** a wrapped key that fails to unwrap is never replaced; a new key is persisted synchronously
  before the first encryption returns.
- Source codec: round trip of M3U and Xtream sources with `toString` hiding credentials; duplicate
  ids, corrupted payload, version 1/2 migration, out-of-range EPG offsets (mirrors
  `IptvSourceConfigurationCodecTest`).
- Redactor: credentials, paths and query strings removed from URLs with host and port kept; named
  secrets outside URLs; null/blank → null (mirrors `SecretRedactorTest`); **new:** the added names
  and Xtream path shapes of SEC-FR-19.
- URL policy: `http`/`https` with host accepted (including an underscore host in the rebuild), `ftp`,
  scheme-less, host-less and blank refused with the label in the message.
- Key validation: TMDB 2,049 characters or a newline refused; API-Sports 513 refused; blank removes.

Instrumentation
- Real keystore: encrypt/decrypt with the Android Keystore generated IV (mirrors
  `AndroidKeystoreSecretCipherTest`).
- Secret store: legacy single source migrated into the encrypted collection and the legacy file
  cleared; EPG offset persists; PIN encrypted, verified and cleared; TMDB opt-in encrypts the token
  and providers are off by default; the API-Sports key is encrypted and redacted (mirrors
  `SecretSettingsStoreMigrationTest`). Assert that no plaintext secret appears in the raw XML files.
- Network policy: cleartext allowed for arbitrary provider hosts; refused for every first-party host
  (API-Sports hosts, `api.themoviedb.org`, `image.tmdb.org`, `api.tvmaze.com`, and in the rebuild
  Trakt, GitHub and Stremio hosts) (mirrors `IptvNetworkSecurityTest`).
- Media session: a controller from another package is rejected; this app's controller is accepted.
- Backup rules: `adb shell bmgr` / data-extraction dry run shows no app files (manual on the emulator).

Main-thread checks
- StrictMode (debug builds) with disk-read and custom "keystore" detection on the main thread; open a
  film, an episode, the guide and the PIN gate: zero violations.
- Perfetto on the low-end box or the slow-box emulator: no `Keystore`/`Cipher` slices on the main
  thread while browsing details, the guide and Settings.

Release gates (every public source and APK)
- Content audit, release-document audit, APK safety audit and gitleaks (history, staged commit,
  package) all pass with zero findings; the permission list equals §4.8; the signature fingerprint
  matches.

Performance
- Import of the owner-scale fixture: cipher time per 10,000 rows logged; no more than one `Cipher`
  instance per worker thread (allocation tracker).
- PBKDF2 timing on the low-end box (shared with [Backup](71-backup-restore.md) §11).

## 12. Reference: current code map

| File (beta 23) | Role |
|---|---|
| `core/.../core/security/AndroidKeystoreKeyProvider.kt` | Keystore AES-256-GCM key, cached handle |
| `core/.../core/security/SecretCipher.kt` | `SecretCipher` interface; keystore cipher `v1` |
| `core/.../core/security/EnvelopeSecretCipher.kt` | Envelope cipher `v2`, wrapped data key store |
| `core/.../core/security/SecretSettingsStore.kt` | Encrypted sources, PIN, TMDB token, API-Sports key; legacy migration; validation |
| `core/.../core/security/IptvSourceConfigurationCodec.kt` | Binary source-list codec (encrypted as a whole) |
| `core/.../core/security/SecretRedactor.kt` | Redaction rules |
| `core/.../core/security/PortableBackupCipher.kt` | Password-based backup envelope ([Backup](71-backup-restore.md)) |
| `core/.../core/network/IptvSourceUrlPolicy.kt` | Source address policy |
| `core/.../core/error/LocalizedException.kt` | `userMessage` redaction at the display boundary |
| `core/.../core/model/IptvConfiguration.kt` | Source model rules and redacted `toString` |
| `app/src/main/res/xml/network_security_config.xml` | Cleartext base, HTTPS-only first-party domains |
| `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Android backup and transfer exclusions |
| `app/src/main/AndroidManifest.xml` (+ `lab`, `demo` overlays) | Permissions, exported components, `profileable` |
| `app/.../app/StreamMateContainer.kt` | Main cipher wiring, shared HTTP client |
| `app/.../addons/AddonHost.kt` | Discover key and envelope |
| `app/.../app/StreamMatePlaybackService.kt` | Controller policy, placeholder resolution, stream user agent |
| `app/.../trakt/TraktAccountStore.kt` | Encrypted Trakt storage |
| `app/.../app/PhoneSetupServer.kt` | LAN setup page, token |
| `scripts/Test-PublicSourceContent.ps1`, `Test-PublicReleaseContent.ps1`, `Test-PublicApkSafety.ps1` | Release-hygiene audits |
| `PRIVACY.md`, `SECURITY.md`, `THIRD_PARTY_NOTICES.md`; private `docs/PRIVACY_POLICY*.md`, `PUBLIC_RELEASE_CHECKLIST.md`, `PUBLIC_RELEASE_REPOSITORY.md` | Commitments and release policy |
