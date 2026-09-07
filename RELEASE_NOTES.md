# Sohva TV 0.1.0-beta.7

Prepared: **7 September 2026**. Android version code: **8**.

Seventh Sohva TV tester package. On beta 3 or later, fetch it from **Settings >
About > Check for updates**; on earlier betas, install it over the existing
app without uninstalling or clearing data. This is a non-commercial beta, not
a public-launch announcement.

## Changed since beta 6

Settings were rebuilt after testers found them scattered and cluttered.

- A new **General** section holds the interface language and size, the time
  zone, the startup screen and the refresh interval. The time zone opens a
  picker over every zone the TV knows, by region, with a search field; the
  TV's own zone is the first choice.
- Sections are now General, Playlists, Playback, Remote buttons, Library,
  Sohva Sport, Parental controls, Backup & tools and About. Sohva Sport keeps
  only sport; Library holds TMDB, TVmaze, the metadata language, film copies,
  your own groups, the image cache and **Manage groups & content**; "Clear
  all guide data" sits under Backup & tools.
- Every setting is one row: what it is, one line of help, and its value on
  the right. OK opens a list of choices with the current one marked. Yes/no
  settings are switches and apply at once; only addresses and keys keep a
  Save button.
- The Playlists page lists your sources. Opening one shows its details and
  actions alone; **All playlists** or Back returns to the list.
- The keystore, HTTPS and licence explanations moved from the settings pages
  to the privacy page, under "Notes on services".

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
build 8; every later distributed build must use a higher build number. No
preference, database or backup-format change since beta 6: this is a screen
change, and every setting keeps the value it had.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build gate and the unit-test suite, and a full emulator run
of the database and UI tests on the released source, with the settings tests
rewritten for the new rows and pickers. Each settings page was reviewed from
emulator screenshots and on a Shield. These are bounded results, not a
guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control.

## Source publication

The source for this beta is published under `GPL-3.0-only` in this repository,
and the release tag points at the commit it was built from. It produces the
same signed APK when built with the private release identity; the signing key
and credentials are not part of the source.
