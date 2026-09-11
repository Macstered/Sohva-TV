# Sohva TV 0.1.0-beta.13

Prepared: **11 September 2026**. Android version code: **14**.

This beta adds **Discover**, an independent section for user-installed,
Stremio-compatible addons. Existing Live TV, Movies, Series and Sohva Sport
remain available. No addons, subscriptions, channels or credentials are included.

## New in beta 13

- Catalog poster rows with a focused-title hero, Continue Watching at the top,
  cached browsing and nearby-row preloading. Required-filter catalogs live in
  Discover; plain Search returns separate movie and series rows.
- Movie details, cast, seasons and episode pages, source-provider selection,
  playback and resume. Artwork and synopsis come from your configured providers.
- A per-profile saved Library and catalog ordering/visibility controls.
- Addon installation by URL, a text file with multiple URLs, or temporary phone
  setup. Stremio authorization copies configurations after a preview; supported
  Nuvio JSON files can also be imported. Source accounts are not modified.
- Automatic primary/secondary subtitle selection, a remote-friendly subtitle
  picker, subtitle timing adjustment and shared Sohva subtitle appearance settings.
- Playback startup artwork and status, bottom playback controls, and improved
  Back/focus behavior.
- Discover screens follow Sohva's saved interface language. English and Finnish
  are supported; Spanish, Portuguese, German, Swedish and Italian remain drafts.
  The temporary phone browser page is in English. Addon metadata language is
  configured with the metadata provider, independently of the app's menus.
- A guide instrumentation teardown race is fixed; no guide behavior changed.
- Fresh Discover with no addons correctly finishes loading its empty watch history.

See [Discover setup](ADDONS.md) and the [tester checklist](TESTING.md).

## Update safely

Install over your existing Sohva TV app; **do not uninstall or clear storage**.
You may also use **Settings > About > Check for updates** once this beta is
published. Android build 14 uses the existing production signing identity.
The IPTV database remains at version 26, unchanged from beta 12; Discover uses
separate storage and starts empty. No data is copied from a separate Lab app.

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
