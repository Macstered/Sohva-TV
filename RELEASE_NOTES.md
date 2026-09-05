# Sohva TV 0.1.0-beta.5

Prepared: **5 September 2026**. Android version code: **6**.

Fifth Sohva TV tester package. On beta 3 or later, fetch it from **Settings >
About > Check for updates**; on earlier betas, install it over the existing
app without uninstalling or clearing data. This is a non-commercial beta, not
a public-launch announcement.

## Changed since beta 4

- **Interface size** under **Playback & remote**: Normal, Compact or Small
  draws the whole app a step smaller, for a projector or a very large screen.
- A long channel name in the programme guide now actually wraps onto a second
  line; beta 4 announced this but the wrap never triggered.
- The channel list during playback is wider, so long custom names fit next to
  their logos.

## Included

- Sohva TV name, couch/play logo, TV launcher banner and accessible wordmarks.
- Sohva Sport name and orange companion branding.
- Live TV, programme guide, movies, series, playback resume and optional
  TMDB / TVmaze / API-Sports integrations using the tester's own credentials.
- Library organisation, custom channel lists, remote button mapping, phone
  setup, interface size and in-app update check.
- English and Finnish app interface; English installation and testing guide.

## Update compatibility

Future Sohva TV betas should be installed over this one without uninstalling or
clearing data, or fetched from **Settings > About**. This beta uses Android
build 6; every later distributed build must use a higher build number. One new
preference, the interface size, is carried by the backup format with a default
of Normal; nothing else changed since beta 4.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build gate and the unit-test suite, and a full emulator run
of the database and UI tests on the released source. The guide wrap and the
wider channel list were confirmed on a Shield against real playlists. These
are bounded results, not a guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control.

## Source publication

The source for this beta is published under `GPL-3.0-only` in this repository,
and the release tag points at the commit it was built from. It produces the
same signed APK when built with the private release identity; the signing key
and credentials are not part of the source.
