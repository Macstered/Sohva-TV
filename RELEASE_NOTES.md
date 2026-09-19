# Sohva TV 0.1.0-beta.22

Prepared: **19 September 2026**. Android version code: **50**.

This beta improves large-playlist guide loading, remote navigation and memory use during playback.
No addons, subscriptions, channels, credentials
or accounts are included.

## New in beta 22

- **Faster large-playlist loading.** Channel lists load in small pages, and
  leaving a list cancels its remaining reads. Large lists also use less
  temporary memory while being ordered.
- **Lower memory use during playback.** Fixed a memory-exhaustion path in
  background sports-channel matching that could make playback stutter or
  crash with large EPGs. Matching now reads small batches and retains only
  the best results, preserving saved decisions and channel visibility.
- **Reliable held-button paging.** Holding Left or Right through guide time
  pages keeps navigation on the current channel while new listings load.
- **Smoother guide navigation.** Focus changes and programme arrivals update
  less of the screen. Guide accessibility descriptions are simpler, reducing
  work when screen readers or button-remapping services are enabled.
- **Better diagnostics.** Large or slow channel reads are recorded as counts
  and timings without playlist contents.

Tested on Shield with a large playlist and sustained playback, plus Android
emulator regression and memory stress tests. Please report results from other
devices, especially lower-memory TV sticks.

See [Discover setup](ADDONS.md) and the [tester checklist](TESTING.md).

## Update safely

Install over your existing Sohva TV app; **do not uninstall or clear storage**.
You may also use **Settings > About > Check for updates** once this beta is
published. Android build 50 uses the existing production signing identity.
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
