# Sohva TV 0.1.0-beta.8

Prepared: **7 September 2026**. Android version code: **9**.

Eighth Sohva TV tester package. On beta 3 or later, fetch it from **Settings >
About > Check for updates**; on earlier betas, install it over the existing
app without uninstalling or clearing data. This is a non-commercial beta, not
a public-launch announcement.

## Changed since beta 7

- **Reminders.** A match card and, in the guide, any programme that has not
  started can carry a reminder (OK on the programme, or hold OK on any
  programme, opens its actions: watch, catch-up, remind, favourite). A minute
  before the start the reminder appears inside Sohva TV as a dialog with
  **Watch** and **Not now**, over whatever is on. When Sohva TV is not on
  screen it comes forward with the same dialog, provided you have allowed it
  to **display over other apps** in the TV settings; the first reminder
  explains this once and offers that screen, and Settings > General shows the
  state. Android TV shows no popup for an app that is not on screen, so
  without that grant the reminder waits in the TV's notification panel.
- **Score ticker.** While watching anything, the player's quick actions (and
  a mappable remote button) switch on a small panel of your followed matches:
  the ones on now with their score, then the ones starting within three hours.
  It polls only while shown and never in the background.
- **Very large libraries.** Two background jobs that read a provider's whole
  film catalogue at once ran the app out of memory a few minutes after every
  start with a catalogue of about 200,000 films. They now work in pages, and a
  catalogue that has not changed costs nothing at the next start.
- **Guide with very large channel selections.** All-channels views of tens of
  thousands of channels open as rows first, with programmes filled in for the
  rows on screen, instead of reading every programme up front.
- **Save diagnostics.** Settings > About writes a text file of recent events
  and refresh states, with addresses and keys removed, when you choose it.
- The database is analysed after every import, so its query plans fit the
  data rather than a fresh install.

## Changed in beta 7

Settings were rebuilt: a **General** section (interface language and size, a
full time-zone picker, startup screen, refresh interval); sections General,
Playlists, Playback, Remote buttons, Library, Sohva Sport, Parental controls,
Backup & tools and About; one setting per row with its value on the right and
OK opening the choices; yes/no settings as switches; each playlist as its own
page; the service and connection explanations moved to the privacy page.

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
build 9; every later distributed build must use a higher build number. The
database gains a reminders table, created automatically on first start;
backups keep their format and every setting keeps the value it had.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build gate and the unit-test suite, and a full emulator run
of the database and UI tests on the released source. The memory fix and the
reminder alarm path were verified on a Shield with the owner's own large
catalogue: the app now stays under a quarter of its memory limit through both
background jobs, and a reminder fired into a closed app opened it once the
display-over-other-apps grant was in place. These are bounded results, not a
guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control.

## Source publication

The source for this beta is published under `GPL-3.0-only` in this repository,
and the release tag points at the commit it was built from. It produces the
same signed APK when built with the private release identity; the signing key
and credentials are not part of the source.
