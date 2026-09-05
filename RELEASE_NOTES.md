# Sohva TV 0.1.0-beta.4

Prepared: **5 September 2026**. Android version code: **5**.

Fourth Sohva TV tester package, and the first one the app can fetch itself:
on beta 3, open **Settings > About > Check for updates**. Earlier betas are
updated by installing this package over them, without uninstalling or clearing
data. This is a non-commercial beta, not a public-launch announcement.

## Changed since beta 3

- In the channel manager, moving down the group list no longer pauses for a
  second or two at each group while its channels fill in on the right. Every
  group's counts are worked out once, off the screen's thread, and a group's
  channels are read only once focus has rested on it for a moment.
- A long channel name in the programme guide, such as a custom name, wraps
  onto a second line instead of being cut short; the channel column is also a
  little wider.

## Included

- Sohva TV name, couch/play logo, TV launcher banner and accessible wordmarks.
- Sohva Sport name and orange companion branding.
- Live TV, programme guide, movies, series, playback resume and optional
  TMDB / TVmaze / API-Sports integrations using the tester's own credentials.
- Library organisation, custom channel lists, remote button mapping, phone
  setup and in-app update check.
- English and Finnish app interface; English installation and testing guide.

## Update compatibility

Future Sohva TV betas should be installed over this one without uninstalling or
clearing data, or fetched from **Settings > About**. This beta uses Android
build 5; every later distributed build must use a higher build number. There is
no database, preference, key or backup-format change since beta 3.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build gate and the unit-test suite, and a full emulator run
of the database and UI tests on the released source, including a new timing
test for the channel manager's group list. These are bounded results, not a
guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control.

## Source publication

The source for this beta is published under `GPL-3.0-only` in this repository,
and the release tag points at the commit it was built from. It produces the
same signed APK when built with the private release identity; the signing key
and credentials are not part of the source.
