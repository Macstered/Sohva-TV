# Sohva TV 0.1.0-beta.23

Prepared: **23 September 2026**. Android version code: **57**.

This beta is lighter on slower TV boxes, opens the Live TV guide on a group,
finishes movies and episodes properly, and keeps a refreshed playlist in the
guide. No addons, subscriptions, channels, credentials
or accounts are included.

## New in beta 23

- **Lighter on slower TV boxes.** The app is optimised and less than half its
  former size, static backgrounds are drawn once rather than on every frame,
  film browsing prepares titles in the background, and moving around Home no
  longer redraws the whole page.
- **Updates that arrive ready.** From this beta on, an update installed from
  **Settings > About > Check for updates** brings its startup profile, so
  Android prepares the new version as it installs instead of during a later
  idle period. The update to this beta from an earlier one does not benefit
  yet.
- **The guide opens on a group.** Live TV opens on your playlist's first group,
  or on the group of the channel you came from, instead of reading every
  channel. **All channels** remains on the group list. Returning within ten
  minutes reuses the channel list the guide last read.
- **Switching playlist in the guide** lands on the new playlist's first group
  and its first channel. Closing the guide options returns focus to where they
  were opened.
- **A refreshed playlist stays in the guide.** Refreshing a playlist twice at
  once, for example with **Sync everything** running in the background and
  **Refresh channels**, could leave it without channels in the guide. Imports
  of one playlist now run one at a time. If a playlist is missing from the
  guide on an earlier beta although Settings lists its channels, refresh that
  playlist once.
- **Finished movies and episodes.** A finished movie returns to its details
  page instead of a black screen. With autoplay on, Discover episodes continue
  to the next one, across seasons; the last episode returns to the series
  details.

Tested on Shield with large playlists, playlist refreshes and playback, plus
emulator regression tests and measurements on an emulator set up like a slower
TV box. Please report results from other devices, especially lower-powered TV
boxes. This is the first beta built with Android's R8 optimiser: if something
that worked in beta 22 fails, please report it with diagnostics.

See [Discover setup](ADDONS.md) and the [tester checklist](TESTING.md).

## Update safely

Install over your existing Sohva TV app; **do not uninstall or clear storage**.
You may also use **Settings > About > Check for updates** once this beta is
published. Android build 57 uses the existing production signing identity.
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
