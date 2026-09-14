# Sohva TV 0.1.0-beta.15

Prepared: **15 September 2026**. Android version code: **22**.

This beta makes every sport that API-Sports offers selectable in **Sohva
Sport**. Live TV, Movies, Series, Home, Trakt and Discover are as in beta 14.
No addons, subscriptions, channels, credentials or accounts are included.

## New in beta 15

- **Four more sports.** American football (the NFL feed, with NFL and college
  competitions), MMA, Formula 1 and the NBA's own feed join football, ice
  hockey, basketball, baseball, AFL, handball, rugby and volleyball under
  Settings > Sohva Sport.
- **Sports without competitions.** MMA, Formula 1 and NBA have no league list
  at the provider; following one shows every event of the day, and Settings
  says so instead of listing zero competitions.
- **Fights, sessions and games on cards.** A fight shows both fighters and the
  weight class. A Formula 1 session shows the Grand Prix, the circuit and the
  session type, with the lap count while a race is on. NBA games show the
  score and skip the summer leagues.

See [Discover setup](ADDONS.md) and the [tester checklist](TESTING.md).

## Update safely

Install over your existing Sohva TV app; **do not uninstall or clear storage**.
You may also use **Settings > About > Check for updates** once this beta is
published. Android build 22 uses the existing production signing identity.
The IPTV database stays at version 29 and the Discover progress database at
4; no migration runs. No data is copied from a separate Lab app. Trakt is off until you
connect an account.

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
