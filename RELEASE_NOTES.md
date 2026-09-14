# Sohva TV 0.1.0-beta.14

Prepared: **14 September 2026**. Android version code: **20**.

This beta reworks the **Home** screen and adds optional **Trakt** sync.
Existing Live TV, Movies, Series, Sohva Sport and Discover remain available.
No addons, subscriptions, channels, credentials or Trakt accounts are included.

## New in beta 14

- **Home rework.** A fixed hero at the top describes whichever card is focused:
  title, episode, minutes left, synopsis and artwork for movies and episodes;
  the programme, its image and description for a channel; the fixture, score
  and club crests for a match. The rows scroll under it on a fixed focus line.
- **Continue watching in one row.** Movies and episodes from your library,
  positions paused on Trakt, and Discover history, newest first, one card per
  series. Series cards open on the episode you were watching.
- **Trakt sync (optional).** Connect under Settings > Accounts with a code
  shown on the TV. What you play in Movies, Series and Discover is recorded to
  Trakt as it happens; Trakt's own progress and watched marks show on library
  and Discover cards, and a title paused elsewhere resumes here from that
  position. Matching is by TMDB/IMDb identifier only.
- **Watch next and Recommended for you rows** from Trakt: the next unwatched
  episode of the shows you have been watching, and Trakt's picks for you. A
  card opens the title in your library when you have it, otherwise looks it up
  in your Discover addons. Synopses come from TMDB in your interface language.
- **A match card opens its fixture** in Sohva Sport rather than the day's list.
- **New logo** on the launcher banner, the icon and the launch screen, and a
  new Home background.
- Episode labels read S1 E2 in English and K1 J2 in Finnish throughout.

See [Discover setup](ADDONS.md) and the [tester checklist](TESTING.md).

## Update safely

Install over your existing Sohva TV app; **do not uninstall or clear storage**.
You may also use **Settings > About > Check for updates** once this beta is
published. Android build 20 uses the existing production signing identity.
The IPTV database moves from version 26 to 29 and the Discover progress
database from 1 to 4; both migrate in place and keep every position and
setting. No data is copied from a separate Lab app. Trakt is off until you
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
