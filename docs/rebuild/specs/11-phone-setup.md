# Phone setup

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

Typing a playlist address, a username or a 40-character key with a TV remote is slow and error
prone. Phone setup lets the viewer do it on a phone: the TV serves a small web page on its own
home-network address, shows the address as a QR code, and the phone's browser posts the form back
to the TV. Three flows use it: **sources and keys** from Settings › Playlists, **a channel logo
picture** from Channel management, and **a list of configured addon URLs** from Discover's import
screen. The page exists only while the TV screen that opened it is on screen, answers only
requests that carry the secret token from the QR code, listens only on the TV's local IPv4
address, uses plain HTTP inside the home network (the pages say so), and never logs what is
posted. Nothing is sent to any developer-hosted service.

## 2. Feature checklist

- PHONE-01 "Set up from a phone" button in Settings › Playlists starts the setup page and opens the QR dialog; while it runs the button reads "Close the phone page".
- PHONE-02 QR dialog: title, QR code on a white square, help, the page address in bold, a privacy note, "Close the phone page" (focused).
- PHONE-03 No network address: the dialog says so in red instead of showing a code.
- PHONE-04 Setup page on the phone, in the TV's interface language: an Xtream account form, an M3U playlist form and an Optional keys form (TMDB token, API-Sports key).
- PHONE-05 A source sent from the phone is validated with the Settings rules, saved encrypted, and synced at once.
- PHONE-06 Keys sent from the phone are saved encrypted; a TMDB key also switches TMDB on.
- PHONE-07 The phone page answers every post with a result sentence (saved, keys saved, something missing, could not save).
- PHONE-08 The TV dialog shows "Received from the phone: <name>. Syncing it now."; the playlist list refreshes and its status line reports the receipt.
- PHONE-09 Several sources and keys can be sent in one session.
- PHONE-10 "Logo from phone" in Channel management: a page with one picture chooser; the phone shrinks the picture to at most 512 px and sends it; the TV stores it as that channel's logo (at most 256 px) and closes the page.
- PHONE-11 Addon URLs from a phone (Discover › Import): a one-use, ten-minute session; paste URLs or choose a `.txt` file on the phone; nothing installs until confirmed on the TV.
- PHONE-12 A request without the right token is refused with a page that says to scan the code again.
- PHONE-13 The page closes when its dialog closes (button or Back), when the TV leaves the screen that opened it, and after 15 minutes (sources/logo) or 10 minutes (addons).
- PHONE-14 Only the TV's own local IPv4 address is served; nothing is posted anywhere else; nothing posted is logged.

## 3. Entry points and navigation

| Flow | Where | Starts | Dialog / screen |
|---|---|---|---|
| Sources and keys | Settings › Playlists list page, button `phone_setup_start` "Set up from a phone" (Link icon, compact, `source-add-phone`) | Server in **Sources** mode | QR dialog titled `phone_setup_title` "Set up from a phone" |
| Channel logo | Channel management › a channel's editor, button `channels_logo_from_phone` "Logo from phone" (`channel-editor-logo-phone`) ([21](21-channel-management.md) CHAN-FR-40…43) | Server in **Logo** mode for that channel | QR dialog titled `phone_setup_page_logo_title` "Logo for %1$s" |
| Addon URLs | Discover › Addons & setup › Import › Account & phone › `addon_ui_set_up_from_phone` ([50](50-discover-addons.md) ADDON-FR-37, -43…46) | An addon phone session | Full-pane "Phone setup" page |

- **PHONE-FR-01** While the Sources server runs, the Playlists button reads `phone_setup_stop`
  "Close the phone page" and stops it on OK.
- **PHONE-FR-02** Back or "Close the phone page" in the QR dialog stops the server and closes the
  dialog; focus returns to the button that opened it (rebuild: requested explicitly).
- **PHONE-FR-03** Any change of the app's top destination stops the Sources/Logo server
  (SHELL-FR-16), for example leaving Settings or Channel management, or a reminder opening a
  channel over them. Switching Settings sections cannot happen while the modal dialog is open.
- **PHONE-FR-04** Addon screen: Back returns to Import in the section it came from and closes the
  session; the pending list is unchanged ([50](50-discover-addons.md) §3).
- **PHONE-FR-05** Starting the server while it already runs in the same mode does nothing;
  starting it in another mode (for example a different channel's logo) stops the running page
  first and starts a new one with a new port and token.

## 4. Behaviour

### 4.1 Sources/Logo server: lifecycle

- **PHONE-FR-10 Address.** Among network interfaces that are up and not loopback, those whose
  name starts with `wlan` or `eth` are tried first, then the rest; the first **site-local IPv4**
  address (10/8, 172.16/12, 192.168/16) is used. None → state **NoNetwork** and no socket is
  opened. Rebuild: skip interfaces named `tun*`, `ppp*`, `rmnet*` (VPN and mobile), and use the
  same address picker for the addon session (4.6), which in beta 23 takes the first site-local
  IPv4 of any interface.
- **PHONE-FR-11 Socket.** A server socket bound to that address only (never all interfaces) on
  a random free port (port 0), backlog **4**, accept timeout **1,000 ms** (the loop wakes once a
  second to check whether it should stop).
- **PHONE-FR-12 Token.** A new token per start: 8 characters drawn with `SecureRandom` from the
  31-character alphabet `abcdefghjkmnpqrstuvwxyz23456789` (no i, l, o, 0, 1). The page address is
  `http://<ip>:<port>/?t=<token>`, shown as text and as the QR code.
- **PHONE-FR-13 State.** `Stopped` → `NoNetwork` or `Running(url, receivedCount = 0,
  lastSourceName = null, mode)`. Each successful submission increments `receivedCount` and, for a
  source, sets `lastSourceName` to its name. The UI state (`PhoneSetupUiState`) is `running`,
  `noNetwork`, `url`, `qrCode` (image), `receivedCount`, `lastSourceName`; actions `onStart`,
  `onStop`.
- **PHONE-FR-14 Thread.** One daemon thread named `sohva-phone-setup` accepts and handles one
  connection at a time (no pool).
- **PHONE-FR-15 Lifetime.** The loop ends **15 minutes** after the start (a hard cap from start,
  not from the last request, despite the constant's name `IDLE_LIFETIME_MILLIS`), or when
  stopped. On the cap the server stops itself and the state becomes `Stopped`, which silently
  removes the dialog. Rebuild: keep the 15-minute cap; also stop when the app goes to the
  background (activity `ON_STOP`), as the addon session already does (beta 23 keeps the page up
  behind the launcher until the cap); when the cap closes an open dialog, show in Playlists'
  status line that the phone page closed (Q-02).
- **PHONE-FR-16 Stop.** Mark not running, close the server socket (unblocking accept), state
  `Stopped`. Idempotent.

### 4.2 Sources/Logo server: HTTP handling

- **PHONE-FR-20 Reading a request.** Client socket read timeout **5,000 ms**. The stream is read
  as ISO-8859-1 text: the request line must split on spaces into at least method and target, else
  the request is malformed. Method is upper-cased; path = target before `?`; query = the part
  after `?` parsed as a form. Headers: up to **64** lines, until an empty line; name = text before
  the first `:`, trimmed and lower-cased; value = the rest, trimmed. Body: `Content-Length` bytes
  (missing or unparsable = 0), read as characters; a length above the mode's limit makes the
  request malformed **before** the body is read. Limits: Sources mode **16,384**; Logo mode
  **1,500,000** (a percent-encoded data URL). No chunked transfer encoding.
- **PHONE-FR-21 Form decoding.** `a=b&c=d`, empty pairs skipped, name and value URL-decoded as
  UTF-8 (a value that fails to decode is kept raw); a later duplicate name wins.
- **PHONE-FR-22 Routing (beta 23).**

| Condition (in order) | Status | Page |
|---|---|---|
| Malformed request (PHONE-FR-20) | 400 Bad Request | message `phone_setup_page_bad_request` "That request could not be read.", no forms |
| Neither the query's `t` nor the body form's `t` equals the token | 403 Forbidden | `phone_setup_page_forbidden` "This page needs the code from the TV screen. Scan it again.", no forms |
| `GET` (any path) | 200 OK | the page with the forms |
| `POST` (any path) | see PHONE-FR-30 | |
| any other method | 405 Method Not Allowed | `phone_setup_page_bad_request`, no forms |

- **PHONE-FR-23 Response.** `HTTP/1.1 <code> <reason>` (reasons: OK, Bad Request, Forbidden,
  Method Not Allowed, and "Error" for anything else such as 500), then `Content-Type: text/html;
  charset=utf-8`, `Content-Length`, `Cache-Control: no-store`, `Connection: close`; body UTF-8.
  The connection is closed after each response.
- **PHONE-FR-24 Rebuild hardening** (no visible change): serve only `GET /` and `POST /submit`
  (other paths 404, other methods 405); require the `Host` header to equal `<ip>:<port>`
  (defeats DNS rebinding); on `POST` require `Origin`, when present, to equal
  `http://<ip>:<port>`; compare tokens in constant time; cap the request line and each header
  line at 8 KiB and the whole request at 10 s (a slow client must not hold the only thread for
  longer); add `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff` and a
  `Content-Security-Policy` of `default-src 'none'; style-src 'unsafe-inline'; img-src 'self'
  blob: data:; form-action 'self'; frame-ancestors 'none'; base-uri 'none'` plus
  `script-src 'sha256-…'` for the logo page's script. These rules match the addon session (4.6)
  and the two servers should be one component with three modes ([plan/03](../plan/03-architecture.md)
  places it in `:core:net`).

### 4.3 Sources mode: the page and submissions

- **PHONE-FR-30 Submission.** A `POST` body is parsed as a form and turned into a submission
  with the same rules the Settings source page applies:
  - `type` (case-insensitive) must be `xtream`, `m3u`, `keys` or `logo`; anything else is
    invalid. `logo` is accepted only in Logo mode (4.4).
  - `tmdb_token` and `api_sports_key` are trimmed; empty means absent.
  - `keys`: at least one of the two keys must be present, else invalid; no source.
  - `m3u` / `xtream`: `name` trimmed and non-empty, else invalid (a name over 100 characters is
    also refused by the source model, [10](10-sources-and-import.md) SRC-FR-03). New source id
    `m3u-<random UUID>` or `xtream-<random UUID>`. `m3u`: `m3u_url` must pass the address rule and
    `xmltv_url` is optional (blank = none) (SRC-FR-21, -23). `xtream`: `xtream_url`,
    `xtream_username`, `xtream_password` go through the Xtream validator (server trailing
    slashes removed, username trimmed and required, password kept as typed and required)
    (SRC-FR-22). Every other source field takes its default: in use, connection limit 1, import
    TV and VOD, EPG correction 0.
- **PHONE-FR-31 Saving** (in the app container, on the server thread, blocking until done):
  1. a logo → the logo store and the channel (4.4);
  2. a source → added to the encrypted source list, its source-state row upserted, and a full
     sync of that source started at once (channels, then guide, then catalogue; SRC-FR-99);
  3. a TMDB key → the metadata settings are rewritten with TMDB **on** and this key (the TVmaze
     switch is kept); validation: at most 2,048 characters and no line breaks, else the save
     fails;
  4. an API-Sports key → saved (at most 512 characters, no line breaks).
  Beta 23 does not look for an existing source with the same address: sending the same form twice
  (or the phone browser re-posting on reload) creates two sources. Rebuild: within one session, a
  submission identical to one already saved is answered as saved without saving again; and
  answer a successful post with `303 See Other` to `/?t=<token>&done=<n>` so a reload does not
  re-post (Q-03).
- **PHONE-FR-32 Answers** (the page is re-rendered with the forms and a notice above them):

| Outcome | Status | Notice |
|---|---|---|
| Source saved | 200 | `phone_setup_page_saved` "Saved on the TV: %1$s. The TV is syncing it now; you can close this page." |
| Keys only saved | 200 | `phone_setup_page_keys_saved` "Keys saved on the TV." |
| Invalid form | 400 | `phone_setup_page_invalid` "Something is missing or not an address. Check the fields and send again." |
| Saving threw (secret store, the 100-source limit, a key too long) | 500 | `phone_setup_page_failed` "The TV could not save that. Try again from the TV's settings." |

- **PHONE-FR-33 The page** (HTML5, `lang` = the TV's interface language code, viewport
  `width=device-width, initial-scale=1`, UTF-8), every text HTML-escaped (`& < > " '`):
  - `<title>` and `<h1>`: `phone_setup_page_title` "Sohva TV setup".
  - Intro: `phone_setup_page_intro` "Fill in a playlist below and send it to the TV. It is saved
    on the TV and synced straight away."
  - The notice paragraph when there is a message.
  - Form 1 (`method=post action=/submit`, hidden `t` = token, hidden `type=xtream`): `<h2>`
    `phone_setup_page_xtream` "Xtream account"; `phone_setup_page_name` "Name" (`name`,
    required, maxlength 60); `phone_setup_page_xtream_url` "Server address" (`xtream_url`,
    `type=url`, required, `inputmode=url`, `autocapitalize=off`); `phone_setup_page_username`
    "Username" (`xtream_username`, required, `autocapitalize=off`, `autocomplete=off`);
    `phone_setup_page_password` "Password" (`xtream_password`, `type=password`, required,
    `autocomplete=off`); button `phone_setup_page_send` "Send to the TV".
  - Form 2 (`type=m3u`): `phone_setup_page_m3u` "M3U playlist"; Name (as above);
    `phone_setup_page_m3u_url` "Playlist address" (`m3u_url`, `type=url`, required,
    `inputmode=url`, `autocapitalize=off`); `phone_setup_page_xmltv_url` "Guide (XMLTV) address,
    optional" (`xmltv_url`, `type=url`, `inputmode=url`, `autocapitalize=off`); Send.
  - Form 3 (`type=keys`): `phone_setup_page_keys` "Optional keys"; paragraph
    `phone_setup_page_keys_help` "Your own TMDB token for artwork and plots, and API-Sports key
    for Sohva Sport. Either can be left empty."; `phone_setup_page_tmdb` "TMDB API key or Read
    Access Token" (`tmdb_token`, `autocapitalize=off`, `autocomplete=off`, plain text);
    `phone_setup_page_api_sports` "API-Sports key" (`api_sports_key`, same); Send.
  - Footer paragraph `phone_setup_page_privacy` "Nothing you type leaves your home network. The
    page closes with the TV's settings."
  - Style (inline, no external resources): body `font-family: system-ui, sans-serif`, margin 0,
    padding 20px, background `#12151c`, text `#f2f4f8`; h1 1.4rem, margin 0 0 4px; h2 1.1rem,
    margin 22px 0 8px; p `#aab1c0`, line-height 1.4; form background `#1c2130`, radius 12px,
    padding 14px, margin-top 14px; label block, margin 10px 0 4px, `#aab1c0`, .9rem; input full
    width (border-box), padding 12px, radius 8px, 1px border `#2f3648`, background `#0f1218`, text
    `#fff`, 1rem; button margin-top 14px, full width, padding 14px, no border, radius 10px,
    background `#ff8a3d`, text `#1a0d02`, bold, 1rem; `.notice` background `#26304a`, text `#fff`,
    padding 12px, radius 10px. The page does not follow the TV's colour theme.
- **PHONE-FR-34 The TV side after a receipt** (Settings): the source list is re-read from the
  secret store; Playlists' status line reads `phone_setup_received` "Received from the phone:
  %1$s. Syncing it now." for a source, or `phone_setup_received_keys` "Keys received from the
  phone." when only keys came; the open dialog shows the same source sentence (13 sp, `focus`).
  Beta 23 shows nothing in the dialog for keys only — rebuild: show the keys sentence there too.
  Library and Sohva Sport re-read their key state ([70](70-settings.md) SET-FR-32). The dialog
  stays open for more submissions.

### 4.4 Logo mode

- **PHONE-FR-40** Started with the channel's id and display name. The page's intro is
  `phone_setup_page_logo_help` "Choose a picture on this phone. It is shrunk here, sent to the TV
  and kept there as the channel's logo."; it has one form (`id=logoform`, hidden `t`,
  `type=logo`, `channel` = channel id, `image`), `<h2>` "Logo for %1$s" with the channel name, a
  label `phone_setup_page_logo_choose` "Choose a picture" around `<input type=file accept=image/*>`,
  and a hidden notice paragraph for progress.
- **PHONE-FR-41 Phone script** (inline): on file change, load the file into an image; scale so
  the longer side is at most **512 px** (never enlarged; each side at least 1 px), draw on a
  canvas, put `canvas.toDataURL('image/png')` in `image`, show `phone_setup_page_logo_sending`
  "Sending to the TV…" and submit the form at once (no separate Send button). An image the phone
  cannot load shows `phone_setup_page_logo_invalid` "That file is not a picture this phone can
  read." Strings put inside the script are escaped for a single-quoted JavaScript literal
  (`\` → `\\`, `'` → `\'`, `<` → `\x3c`, newline → space).
- **PHONE-FR-42 TV check.** `type=logo` only in Logo mode and only when `channel` equals the
  channel the page was opened for. `image` may be a data URL (`data:image/…;base64,` prefix
  required when it starts with `data:`) or bare base64; it must decode, be **1…1,000,000 bytes**,
  and start with a PNG (`89 50 4E 47`), JPEG (`FF D8 FF`), GIF (`47 49 46 38`) or WebP (`RIFF` +
  `WEBP` at bytes 8–11) signature. Anything else is invalid (400).
- **PHONE-FR-43 Storing** ([21](21-channel-management.md) CHAN-FR-42): the logo store decodes
  bounds, subsamples by powers of two while the longer side exceeds 512 px, scales to at most
  **256 px**, writes a PNG (quality 100) to `files/channel-logos/<first 8 bytes of SHA-256(channel
  id) as hex>-<millis>.png` after deleting that channel's earlier files (a name never reused),
  and sets the `file:` address as the channel's custom logo. (The store itself accepts up to
  2,000,000 bytes; the protocol's 1,000,000-byte limit applies first.)
- **PHONE-FR-44 Answers**: success 200 with `phone_setup_page_logo_saved` "Logo saved on the TV
  for %1$s. You can close this page."; invalid 400 `phone_setup_page_invalid`; store failure 500
  `phone_setup_page_failed`.
- **PHONE-FR-45 TV side**: Channel management sees `receivedCount` rise, sets its status line to
  `channels_logo_received` "Logo received from the phone" and stops the server, which closes the
  dialog; the channel's logo updates.

### 4.5 QR dialog (TV)

- **PHONE-FR-50** Layout ([design/screens/settings.md](../design/screens/settings.md) §5): platform
  dialog, 720 dp wide, `panel` fill, medium shape, 1 dp `outline` border, padding 20, 12 dp
  between items. Title 20 sp Bold. Running: a row (20 dp gap, vertically centred) of the QR image
  (280 dp square, **white** background with 8 dp padding whatever the theme; content description
  `phone_setup_qr_description` "QR code for the setup page") and a column (8 dp gaps):
  `phone_setup_help` "Scan the code with your phone, or type the address into its browser. Both
  must be on the same network as this TV." (13 sp, `textMuted`); the URL (Bold, `textPrimary`,
  `phone-setup-url`); the received sentence when present (13 sp, `focus`,
  `phone-setup-received`); `phone_setup_privacy` "The page lives on this TV only while it is
  open. What you send is saved here and nowhere else." (12 sp, `textMuted`). No network: only
  `phone_setup_no_network` "This TV has no network address to share. Connect it to Wi-Fi or
  Ethernet and try again." in `danger` (`phone-setup-no-network`). Always last: the
  `phone_setup_stop` "Close the phone page" button, focused when the dialog opens
  (`phone-setup-close`). Dialog test tag `phone-setup-dialog`, QR `phone-setup-qr`.
- **PHONE-FR-51 QR code.** ZXing `QRCodeWriter`, `BarcodeFormat.QR_CODE`, default error
  correction (L), hint `MARGIN = 1` module, requested size **512 × 512 px**, dark modules
  `#FF000000` on `#FFFFFFFF`. ZXing centres the largest whole-module scale inside 512 px, so the
  white border is one module plus the rounding remainder. Beta 23 builds it in the app root's
  composition (`remember(phoneSetupState)`) on the **main thread**, again after every receipt
  (the state object changes though the URL does not), as a 512×512 `IntArray` plus an ARGB bitmap
  (2 MiB transient). Rebuild: build once per URL on a background dispatcher, keyed by the URL; the
  dialog shows the code when ready (the address text is shown at once). Cheapest look-identical
  form: a bitmap of one pixel per module (QR version 2–4 for these addresses: 25–33 modules plus
  the margin) drawn scaled to the 264 dp inner square with nearest-neighbour filtering, or the
  modules drawn as rectangles; no 1 MiB bitmap.
- **PHONE-FR-52** A QR that cannot be built (writer error) leaves the address text alone on the
  dialog (beta 23 catches the error and shows no image).

### 4.6 Addon phone session (protocol)

The screen, the import preview and installation belong to [Discover](50-discover-addons.md)
(ADDON-FR-37, -43…46); this is the wire protocol.

- **PHONE-FR-60 Session.** Created when the addon phone screen opens, bound to a site-local (or,
  for tests, loopback) IPv4 address, random port, backlog 4, accept timeout 500 ms; lifetime
  **600,000 ms** (10 minutes; 1…600,000 allowed) enforced both by a timer that closes everything
  and by the serve loop's deadline. Token: 32 bytes from `SecureRandom` as 64 lower-case hex
  characters. Origin = `http://<ip>:<port>`; pairing URL = `<origin>/#<token>` — the token is in
  the **fragment**, which browsers never send in requests or referrers. The session's
  `toString()` never contains the token.
- **PHONE-FR-61 Per connection**: read timeout 5,000 ms and a **10 s** deadline for the whole
  request; headers read byte by byte up to **8,192 bytes** until `CRLF CRLF`, else 400 "Invalid
  request"; every header line must contain `:` and no name may repeat, else 400; `Host` must equal
  `<ip>:<port>` exactly and `Transfer-Encoding` must be absent, else 403 "Request refused".
- **PHONE-FR-62 Routes** (exact request line):
  - `GET / HTTP/1.1` → 200, the page (HTML). No token needed: the page reads it from the
    fragment.
  - `POST /submit HTTP/1.1` → requires the session still active, `Origin` equal to the origin, and
    `Authorization: Bearer <token>` equal in constant time, else 403 "Pairing expired or
    refused"; `Content-Length` 1…262,144 and `Content-Type` (before any `;`) `text/plain`, else
    400 "Use a text list up to 256 KiB" (decided from the headers, before reading the body); the
    body is read within the deadline and must be strict UTF-8 (a leading BOM removed), contain no
    NUL, and have 1…32 non-blank lines, else 400 "Invalid UTF-8 URL list (maximum 32 lines)". The
    first valid body atomically ends the session: 200 "Sent. Review and confirm on your TV.", the
    text is handed to the TV screen once, and the server stops. Invalid bodies do not consume the
    session.
  - Anything else → 405 "Request refused".
- **PHONE-FR-63 Response headers**: `HTTP/1.1 <code> Response` (the reason phrase is literally
  "Response"), `Content-Type: text/html` or `text/plain` with `; charset=utf-8`,
  `Content-Length`, `Cache-Control: no-store`, `Referrer-Policy: no-referrer`,
  `X-Content-Type-Options: nosniff`, `Content-Security-Policy: default-src 'none'; script-src
  'sha256-<base64 SHA-256 of the inline script>'; style-src 'unsafe-inline'; connect-src 'self';
  frame-ancestors 'none'; base-uri 'none'; form-action 'none'`, `Connection: close`.
- **PHONE-FR-64 The page** (English only, fixed in code; texts in [50](50-discover-addons.md)
  ADDON-FR-46): on load the script moves the token from the fragment into memory and replaces the
  address with `/`; a `.txt` file chooser (read on the phone with a fatal UTF-8 decoder, never
  uploaded by choosing), a masked textarea, "Send to TV for review" and "Clear list"; client-side
  limits 262,144 bytes and 1…32 non-blank lines; the POST uses `fetch` with the bearer header; on
  success the token is forgotten and the form removed; `pagehide` forgets the token. Style: body
  17px system-ui, background `#101827`, text `#eee`, max width 640px; form `#1b273a` with 1px
  `#34445d` border, radius 16px; send button `#2859a6`.
- **PHONE-FR-65 TV screen**: QR from the pairing URL (beta 23: 384 px with ZXing's default
  4-module margin, built on the main thread with one `setPixel` per pixel, shown at 220 dp —
  rebuild: the shared QR builder of PHONE-FR-51, off the main thread); on receipt the screen closes
  the session and queues the text for the import preview; `ON_STOP` closes the session. Beta 23
  then shows "No available local connection…" on return, which is wrong — rebuild: show
  `addon_ui_session_ended_return_to_start_a_new_one` "Session ended. Return to start a new one."

### 4.7 Security model

- **PHONE-FR-70 Who can reach it**: only devices that can route to the TV's site-local address
  (the home network). The socket is bound to that one address.
- **PHONE-FR-71 What protects a session**: the token, shown only on the TV screen, required on
  every request (Sources/Logo: query or form field; addons: bearer header from the fragment); a
  new token and port per start; the short lifetime; the page exists only while its TV screen is
  up. Sources/Logo token: 8 of 31 symbols ≈ 39.6 bits, fine for a single-threaded server living
  15 minutes; rebuild may raise it (Q-01).
- **PHONE-FR-72 What it does not protect**: plain HTTP — anyone who can sniff the home network
  sees what is typed (credentials, keys). The TV dialog, the phone pages, `PRIVACY.md` ("Setting up
  from a phone") and `ADDONS.md` say to use a trusted network and keep the QR private.
- **PHONE-FR-73 No logging**: request bodies, tokens and URLs are never written to the log or the
  diagnostics file; submission objects print without secrets (the logo prints its byte count; the
  source configuration redacts credentials; rebuild: the keys too — beta 23's submission data
  class would print the TMDB and API-Sports keys if anything ever logged it).
- **PHONE-FR-74 No outbound effect before confirmation (addons)**: the addon text only queues a
  preview; nothing is fetched or installed until the viewer confirms on the TV. Sources and keys
  from the Sources page are saved and synced at once (the viewer typed them on purpose).
- **PHONE-FR-75 Input bounds**: every size, count and time limit above is enforced on the TV,
  whatever the page's own checks say.

## 5. Screen anatomy

- QR dialog: PHONE-FR-50 (both Settings and Channel management use the same dialog; only the
  title differs). The dialog replaced an inline QR panel that was clipped at the top or bottom of
  the scrolling pane (beta 12).
- Playlists button row: "+ Add M3U source", "+ Add Xtream source", "Set up from a phone" — compact
  action buttons, 10 dp apart ([10](10-sources-and-import.md) §5).
- Addon phone page on the TV: [50](50-discover-addons.md) §5.8 (QR 220 dp beside "Scan with your
  phone", the pairing address, notes).
- Phone pages: PHONE-FR-33 (sources/logo) and PHONE-FR-64 (addons). They must render on a
  320 px wide phone screen without horizontal scrolling.

## 6. Data

- Nothing about a session is persisted: token, port, mode and counters live in memory and die
  with the server.
- What a submission writes: sources → the encrypted source list (`streammate_secure_sources`
  `sources_v1`) and the source-state table; keys → `metadata_tmdb_enabled_v1`,
  `metadata_tmdb_token_v1`, `sports_api_key_v1` ([70](70-settings.md) §6.1); a logo → the file
  in `files/channel-logos/` and the channel preference row's custom logo address
  ([21](21-channel-management.md)); addon text → memory only until the viewer confirms
  ([50](50-discover-addons.md)).
- Backups carry sources and phone-sent logos (as picture bytes); not the keys
  ([71](71-backup-restore.md)).

## 7. External interfaces

| Item | Sources / Logo server | Addon session |
|---|---|---|
| Bind | TV's site-local IPv4, random port, backlog 4 | same (site-local or loopback), backlog 4 |
| Address shown | `http://<ip>:<port>/?t=<8-char token>` | `http://<ip>:<port>/#<64-hex token>` |
| Routes | any path; GET page, POST submit | `GET /`, `POST /submit` only |
| Token check | query `t` or form field `t` (string equality) | `Authorization: Bearer` (constant time) + exact `Host` + `Origin` |
| Request body | `application/x-www-form-urlencoded` | `text/plain`, strict UTF-8 |
| Body limit | 16,384 (Sources), 1,500,000 (Logo) | 1…262,144 bytes, 1…32 lines |
| Timeouts | accept 1 s; read 5 s | accept 0.5 s; read 5 s; request 10 s |
| Lifetime | 15 min from start, or until stopped | 10 min, or first valid submission, or ON_STOP |
| Headers out | Content-Type, Content-Length, Cache-Control no-store, Connection close | + Referrer-Policy, nosniff, CSP with script hash |
| Language | the TV's interface strings | English only |

Form fields (Sources/Logo): `t`, `type` (`xtream` | `m3u` | `keys` | `logo`), `name`,
`xtream_url`, `xtream_username`, `xtream_password`, `m3u_url`, `xmltv_url`, `tmdb_token`,
`api_sports_key`, `channel`, `image`.

Library: ZXing core 3.5.3 (Apache 2.0, listed in `THIRD_PARTY_NOTICES.md`), encoding only.

## 8. Edge cases and limits

- **No LAN address** (Ethernet unplugged, IPv6-only network, carrier-grade NAT 100.64/10 which
  is not site-local): NoNetwork message; the viewer closes the dialog and tries again later.
- **Phone cannot reach the TV** (guest Wi-Fi with client isolation, phone on mobile data, a VPN
  on the phone): the page never loads; the TV shows nothing new. The help text asks for the same
  network. Rebuild: no change beyond the address picker skipping VPN interfaces on the TV.
- **Two interfaces up** (Ethernet and Wi-Fi): the first matching `wlan*`/`eth*` interface's
  address is shown; either works when the phone is on the same network.
- **The TV sleeps or the app goes to the background**: rebuild stops the server (PHONE-FR-15).
- **Browser re-post on reload** duplicates a source in beta 23 (PHONE-FR-31).
- **Oversized or slow requests**: rejected by the limits; one slow client can occupy the single
  thread for up to the 5 s read timeout per read in beta 23 (no overall deadline) — rebuild caps
  the whole request at 10 s.
- **The 101st source**: the store refuses more than 100 sources; the phone gets "could not save".
- **A logo for a channel whose editor was closed**: the server stopped with the screen; the post
  fails to connect.
- **Large photos**: the phone shrinks them; the TV still bounds and subsamples, so a hand-crafted
  1 MB PNG of 20,000 × 20,000 px decodes subsampled, not at full size.
- **Keys that are not keys** (a URL pasted into the TMDB field): saved as typed if under the
  limits; "Test TMDB" in Settings reports the failure ([41](41-metadata-enrichment.md)).

## 9. Lightweight by design

- **Zero cost when closed**: no socket, no thread, no QR bitmap. The server is created on first
  use, not at start (beta 23 constructs the server object in the container but opens nothing).
- **One thread per open page**, blocking I/O with short timeouts; the accept loop wakes once per
  second (0.5 s for addons) — negligible. Requests are handled one at a time; there is no pool.
- **Bounded memory**: Sources bodies ≤ 16 KiB; logo bodies ≤ 1.5 MB (one at a time) plus the
  decoded image ≤ 1 MB and a transient bitmap bounded by the subsampling rule (≤ 512 × 512 × 4 B
  before scaling to 256 px); addon bodies ≤ 256 KiB. Pages are built per request (≈ 6 KB).
- **QR off the main thread** and small (PHONE-FR-51): beta 23 filled a 512 × 512 `IntArray` and a
  1 MiB ARGB bitmap during composition of the app root, and the addon screen made 147,456
  `setPixel` calls on the main thread — both are visible stalls on a Cortex-A35 box.
- **Image work on the phone**: the phone's canvas shrinks photos before sending, so the TV never
  decodes a 12-megapixel picture.
- **Saving off the UI**: submissions are saved on the server thread (never the main thread); the
  sync they start runs in WorkManager at background priority ([10](10-sources-and-import.md)).
- **No start-up cost** and no work while playing.

## 10. Lessons from the current app

1. **Built in beta 3** as "about a hundred lines over a plain ServerSocket, ZXing for the code"
   (commit `5deb107`, `docs/SOHVA_TV_BETA_3.md`); a posted source reuses the Settings validators so
   the two paths cannot drift. Keep one validator.
2. **The QR moved into a dialog in beta 12**: inline in the scrolling pane, the 180 dp square was
   clipped at the bottom, or at the top once focus had scrolled past it (`AboutSection.kt`,
   `docs/SOHVA_TV_BETA_12.md`, TESTING item 26).
3. **Logo from phone** (beta 12): typing a logo address with a remote is the hard way; the phone
   sends a picture from its own library, shrunk on the phone, bounded again on the TV
   (`ChannelLogoStore.kt`). File names never repeat so image caches keyed on the address refresh.
4. **The public test host**: the unit test's example host was a private-network address and the
   public source audit refused it; tests use documentation addresses (`192.0.2.x`) since beta 3.
5. **Two servers, two security levels**: the addon session (beta 13) added exact Host/Origin
   checks, a fragment token, constant-time compare, CSP and a one-use rule; the older sources
   server has none of these. The rebuild uses one hardened server with three modes (PHONE-FR-24).
6. **Main-thread QR generation** in both screens (PHONE-FR-51, -65).
7. **The sources page stays up behind the launcher** until its 15-minute cap; the addon session
   closes on `ON_STOP`. Align on closing (PHONE-FR-15).
8. **Keys from the phone left Settings' key fields stale** ([70](70-settings.md) §8): the
   receiving screen must re-read every store a submission touched.
9. **Phone page language on Android 12 and below** follows the system language, not the chosen
   interface language, because the server resolves strings through the application context,
   which only the activity wraps ([74](74-localization.md) L10N-FR-05). Resolve page strings with
   the interface locale.

### Open questions

- Q-01 Move the Sources/Logo token into the fragment (JavaScript copies it into the forms) and
  lengthen it to 128 bits, like the addon session? The QR grows from about version 3 to version 5
  and stays readable at 280 dp.
- Q-02 New strings for "The phone page closed after 15 minutes" and for a VPN-only network?
- Q-03 Post/Redirect/Get and in-session de-duplication (PHONE-FR-31) — accept as the rebuild
  default?
- Q-04 Translate the addon phone page (beta 23 English only) using the TV's interface language
  like the sources page?

## 11. Acceptance tests

Unit (JVM) — mirror `PhoneSetupProtocolTest` (8 tests):
- A browser's form post parses to method `POST`, path `/submit`, query `t`, lower-cased headers
  (`host` = `192.0.2.5:4321`) and body; `m3u_url` decodes to `http://provider.example/list.m3u`.
- A `Content-Length` of 16,385 and a request line "garbage" are rejected.
- An Xtream form becomes an Xtream source: name trimmed ("Living room"), id prefix `xtream-`,
  username and password kept, no TMDB token.
- An M3U form keeps an absent XMLTV address as none; a keys form carries no source and trims the
  token; an empty API-Sports key is absent.
- Missing name, a non-address M3U URL, `keys` without keys and an unknown type all fail.
- Tokens are 8 characters from the unambiguous alphabet (20 samples).
- A logo for the page's channel becomes its bytes; another channel, the Sources mode, a
  `data:text/plain` payload and non-image bytes are refused.
- The logo limit accepts a body of 16,385 characters that the Sources limit refuses.
Unit (JVM) — mirror `AddonPhoneSessionTest` (6 tests, loopback):
- Wrong token or wrong Origin → 403; right ones → 200 and the text is delivered once; the
  session's `toString()` has no token.
- The page does not contain the token, is `no-store`, uses no external resources, and its CSP
  script hash matches the inline script.
- An invalid list (a NUL) is 400 and does not consume the session.
- Expiry (200 ms lifetime) closes even a half-sent connection.
- A BOM, blank lines, CRLF and exactly 32 entries are accepted.
- Empty, bad UTF-8, NUL and 33-line bodies are 400 without consuming; an oversized
  `Content-Length` is refused from the headers; a valid list then succeeds.
Rebuild additions: Host mismatch → 403 on the Sources server; unknown path → 404; a request that
stays incomplete for 10 s is dropped; the QR builder runs off the main thread (strict mode).

Device (instrumentation) — mirror `PhoneSetupServerTest` (skips when the device has no LAN
address):
- Without the token the page is 403; with it the page contains `name="xtream_url"`; a posted M3U
  form becomes one received source with its XMLTV address; `receivedCount` = 1; a wrong token is
  403 and receives nothing.
- In Logo mode the page has a file input and the channel's id and name; a posted one-pixel PNG
  reaches the app for that channel; `receivedCount` = 1.

UI
- Settings › Playlists › "Set up from a phone" opens the dialog with the QR, the address and the
  focused Close button; Back closes it and stops the server (a request then fails to connect).
- After a posted source the dialog and the status line show "Received from the phone: <name>.
  Syncing it now." and the list contains the source.
- Leaving Settings with the dialog open stops the server.

Manual (device, with a real phone on the same Wi-Fi)
- Scan the QR on the Elisa box and the Shield; send an Xtream account, an M3U list and keys;
  check the sync starts and TMDB turns on.
- Send a logo from a phone photo; the guide shows the new logo.
- Addon list from a phone: file and paste; review and confirm on the TV.

Performance (low-end class)
- Opening the QR dialog: the first frame of the dialog within two vsyncs of the OK press, the QR
  appearing within 100 ms; no main-thread work above 8 ms (trace).

## 12. Reference: current code map

- `app/.../app/PhoneSetupServer.kt` — protocol (parse, forms, submissions, image checks, token),
  server (lifecycle, routing, pages, logo script).
- `app/.../app/StreamMateContainer.kt` — the submission handler (logo store, sources, keys,
  sync).
- `app/.../app/StreamMateApp.kt` — stop on destination change, QR generation (`qrCodeBitmap`),
  state mapping, start/stop wiring for Settings and Channel management.
- `iptv/.../feature/settings/PhoneSetupUiState.kt` — UI state and actions.
- `iptv/.../feature/settings/AboutSection.kt` — `PhoneSetupDialog`.
- `iptv/.../feature/settings/SettingsScreen.kt` — the Playlists button, receipt handling.
- `iptv/.../feature/settings/ChannelEditorScreen.kt` — "Logo from phone", receipt handling.
- `app/.../app/ChannelLogoStore.kt` — logo bounding and files.
- `addons/.../AddonPhoneSession.kt`, `AddonImportText.kt` — addon session server and text rules.
- `app/.../addons/AddonPhoneScreen.kt`, `AddonImportScreen.kt` — addon phone screen and caller.
- Tests: `app/src/test/.../app/PhoneSetupProtocolTest.kt`,
  `app/src/androidTest/.../app/PhoneSetupServerTest.kt`,
  `app/src/androidTest/.../app/ChannelLogoStoreTest.kt`,
  `addons/src/test/.../AddonPhoneSessionTest.kt`.
