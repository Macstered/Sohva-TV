# Sohva TV 0.1.0-beta.9

Prepared: **7 September 2026**. Android version code: **10**.

Ninth Sohva TV tester package. On beta 3 or later, fetch it from **Settings >
About > Check for updates**; on earlier betas, install it over the existing
app without uninstalling or clearing data. This is a non-commercial beta, not
a public-launch announcement.

## Changed since beta 8

A tester's M3U list of about 1,700 channels played in other players but
failed here on every channel with a bare "ERROR_CODE_IO_UNSPECIFIED". Three
changes to how the player talks to a provider:

- **The app introduces itself.** When a playlist sets no user agent for a
  channel, the stream request now carries "Sohva TV/<version>" instead of the
  HTTP library's own name, which some panels refuse. A user agent set in the
  playlist is kept.
- **HTTP/1.1 for streams.** Some servers and the fronts before them reset
  HTTP/2 streams part-way; the players that work with them speak HTTP/1.1, and
  so does Sohva TV now. Other requests are unchanged.
- **Failures say why.** The on-screen message and the diagnostics file now
  carry the cause, such as "Connection reset" or "Response code: 403", with
  addresses and credentials removed.

## Changed in beta 8

Reminders presented inside the app (with the optional display-over-other-apps
grant for when the app is not on screen), a score ticker in the player, paged
background jobs for very large catalogues, a windowed guide read for very
large channel selections, Save diagnostics, and ANALYZE after imports.

## Changed in beta 7

Settings rebuilt: a General section, a full time-zone picker, one setting per
row with pickers and switches, each playlist as its own page.

## Included

- Sohva TV name, couch/play logo, TV launcher banner and accessible wordmarks.
- Sohva Sport name and orange companion branding.
- Live TV, programme guide, movies, series, playback resume and optional
  TMDB / TVmaze / API-Sports integrations using the tester's own credentials.
- Library organisation, custom channel lists, remote button mapping, phone
  setup, interface size, reminders, score ticker, diagnostics file and in-app
  update check.
- English and Finnish app interface; English installation and testing guide.

## Update compatibility

Future Sohva TV betas should be installed over this one without uninstalling or
clearing data, or fetched from **Settings > About**. This beta uses Android
build 10; every later distributed build must use a higher build number. No
preference, database or backup-format change since beta 8.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build gate and the unit-test suite, and a full emulator run
of the database and UI tests on the released source. The playback changes
address the two most likely causes of the tester's failure and were not
reproduced against that tester's provider; the cause now shown on screen is
what to report if a channel still fails. These are bounded results, not a
guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control.

## Source publication

The source for this beta is published under `GPL-3.0-only` in this repository,
and the release tag points at the commit it was built from. It produces the
same signed APK when built with the private release identity; the signing key
and credentials are not part of the source.
