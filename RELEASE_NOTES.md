# Sohva TV 0.1.0-beta.21

Prepared: **18 September 2026**. Android version code: **43**.

This beta improves Live TV guide loading and fixes the remaining startup-menu issue.
No addons, subscriptions, channels, credentials
or accounts are included.

## New in beta 21

- **Channels first.** Live TV channels become available before programme
  information finishes loading, including groups, favourites and recent lists.
- **Smaller EPG reads.** Programme information loads in four-hour windows for
  visible and nearby channels. Browsing requests further batches while the
  channel list remains available. Provider XMLTV imports are unchanged.
- **Stable guide focus.** Programme arrivals keep focus in place. Opening the
  category drawer during a time-page read no longer pulls focus back to the grid.
- **Reliable Home focus after reopening.** Fixed the remaining case where the
  side menu could open as Home finished loading after exiting with Back.

These fixes were confirmed by the reporter and on the owner's Shield preview.

See [Discover setup](ADDONS.md) and the [tester checklist](TESTING.md).

## Update safely

Install over your existing Sohva TV app; **do not uninstall or clear storage**.
You may also use **Settings > About > Check for updates** once this beta is
published. Android build 43 uses the existing production signing identity.
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
