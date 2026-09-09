# Sohva TV 0.1.0-beta.12

Prepared: **9 September 2026**. Android version code: **13**.

Twelfth Sohva TV tester package. On beta 3 or later, fetch it from **Settings >
About > Check for updates**; on earlier betas, install it over the existing
app without uninstalling or clearing data. This is a non-commercial beta, not
a public-launch announcement.

## Changed since beta 11

A profile that sees only its own groups, a channel dialled by number, a
channel's own logo and number, Italian, and what the owner's Shield run
found. The first beta with database version 26.

- **What a profile may see.** In **Settings > General > Profiles**, limit a
  profile to chosen groups of live TV, films and series. Its guide, libraries,
  search and playback show only those groups, on top of the device's own
  rules. With a parental PIN set, switching to an unlimited profile, and
  opening Settings from a limited one, ask for the PIN; without a PIN the
  Profiles group says so.
- **Who is watching** in the home page's left menu once there are two
  profiles, to change profile without Settings.
- **Dial a channel.** Type a number on the remote in the guide or during live
  TV; two seconds after the last digit the guide lands on that channel or the
  picture switches to it. The player's channel list shows the numbers.
- **A channel's own logo and number.** Channel management takes a logo
  address, or a picture chosen on the phone page, and a channel number;
  **Settings > General > Channel numbers** shows or hides numbers in the
  guide. Playlist and Xtream numbers are read where a provider supplies them.
- **Phone QR codes** for a source and for a logo open in a box of their own,
  whole at every interface size.
- **Italian** joins the drafted interface languages.
- **Series pages** say **Loading episodes…** in the corner while the provider
  answers, and show the cast as one line of names, so the seasons and the
  episodes stay on screen.
- **Library group names** can be read: the group column is wider and a long
  name takes two lines.
- **Pickers, the QR box and the dial overlay** sit on a solid panel instead
  of a translucent one.
- **Build checks.** Lint errors that had failed the public build since beta 6
  are fixed, and packaging now runs lint and requires the dex verification
  receipt.

## Changed in beta 11

Test address for M3U sources, an address that answers with something else
refused, failures in the interface's language, the Smaller interface size,
hidden rows out of the editors, and what changed on the About page.

## Changed in beta 10

Profiles, watched state, a chosen skip step, subtitle size and colour, four
drafted interface languages, and the corner view. Database version 25.

## Included

- Sohva TV name, couch/play logo, TV launcher banner and accessible wordmarks.
- Sohva Sport name and orange companion branding.
- Live TV, programme guide, movies, series, playback resume and optional
  TMDB / TVmaze / API-Sports integrations using the tester's own credentials.
- Library organisation, custom channel lists, remote button mapping, phone
  setup, interface size, reminders, score ticker, diagnostics file and in-app
  update check.
- Profiles, what a profile may see, watched state, skip step, subtitle
  appearance and the corner view.
- A channel's own logo and number, channel numbers on or off, dialling by
  number, Test address for M3U sources, and what changed on the About page.
- English and Finnish app interface, with Spanish, Portuguese, German,
  Swedish and Italian as drafts; English installation and testing guide.

## Update compatibility

Future Sohva TV betas should be installed over this one without uninstalling or
clearing data, or fetched from **Settings > About**. This beta uses Android
build 13; every later distributed build must use a higher build number.

**Database version 26**, up from 25: a channel's own logo and number are
three new columns, added by a migration that keeps everything already
stored. A beta 11 cannot open a beta 12 database, so do not go back to an
older build without a backup. New preferences, the channel-number switch and
each profile's allowed groups, travel in the backup along with a channel's
own logo bytes; a beta 11 reads a beta 12 backup and ignores what it does
not know, as beta 10 did with beta 11's. No change to how sources or
credentials are stored.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build gate, lint for every module, the unit-test suites
and a full emulator run of the database and UI tests on the released source,
71 and 210 tests. The release build is also checked against a device's
own dex verifier before packaging.

What a profile may see, dialling, the QR boxes, Italian, the home menu's Who
is watching, the series page and the library group column were exercised on
a Shield by the owner on the builds this one was cut from. The five drafted
languages remain drafts awaiting a native reading, and some labels in
Spanish, Portuguese and German are longer than the space allows. These are
bounded results, not a guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control.

## Source publication

The source for this beta is published under `GPL-3.0-only` in this repository,
and the release tag points at the commit it was built from. It produces the
same signed APK when built with the private release identity; the signing key
and credentials are not part of the source.
