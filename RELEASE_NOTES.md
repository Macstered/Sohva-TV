# Sohva TV 0.1.0-beta.17

Prepared: **16 September 2026**. Android version code: **29**.

This hotfix improves EPG import, guide navigation, the background and backup
export. No addons, subscriptions, channels, credentials
or accounts are included.

## New in beta 17

- **EPG import compatibility.** Fixes the `PI must not start with xml` error
  with XMLTV feeds that begin with a byte-order mark, including compressed
  feeds. The parser also respects the feed's declared text encoding.
- **Guide focus stays in the grid.** Paging right or left waits for the new
  programmes before restoring focus, including on large playlists and empty
  guide pages.
- **Category position is remembered.** Reopening the category drawer preserves
  its scroll position.
- **Smooth background.** Removes the grain texture from the app background.
- **Backup export uses less memory.** Large catalogue indexes no longer
  exhaust memory during settings export. Customized library rules are retained.

See [Discover setup](ADDONS.md) and the [tester checklist](TESTING.md).

## Update safely

Install over your existing Sohva TV app; **do not uninstall or clear storage**.
You may also use **Settings > About > Check for updates** once this beta is
published. Android build 29 uses the existing production signing identity.
The IPTV database stays at version 29 and the Discover progress database at
4; no migration runs. No data is copied from a separate Lab app. Existing Trakt connections are retained; on a new installation,
Trakt is off until you connect an account.

The existing password-protected `.smbak` backup **does not include Discover
addons, Library, watch history, catalog order or visibility**. Keep your own
addon configurations privately. Configured addon URLs can contain credentials;
never post them, phone QR codes, configuration files or raw logs publicly.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK checksum is in the accompanying `SHA256SUMS.txt`. Source is licensed
under `GPL-3.0-only`; the release tag identifies the corresponding source.
The private signing identity is not distributed.

## Compatibility limits

This is a test release, not universal Stremio/Nuvio compatibility. No torrent,
NZB/archive engine, DRM setup, downloads, external-player handoff or arbitrary
addon scripts are included. Some extensionless adaptive streams and cross-origin
subtitle API redirects are unsupported. Codec/HDR/audio support depends on the TV.

Only addon configurations are copied: there is no Stremio/Nuvio account sync,
Library/history import or automatic synchronization. Discover is unavailable to
restricted profiles; it does not classify third-party content by age rating.

Subtitle timing is session/track-specific and may briefly rebuffer on Apply.
One intermittent external-subtitle timing report has not been reproduced; if it
recurs, report the title, provider and subtitle identifier without secret URLs.
Provider subtitle requests may activate watch tracking configured with that
provider. Read [Privacy](PRIVACY.md) before adding services.

Owner testing covered browsing, import, playback, resume, Library persistence,
subtitles and Finnish interface/metadata on Shield. Emulator and release gates
are recorded separately; these checks do not establish compatibility with every
provider, subtitle file or hardware format.
