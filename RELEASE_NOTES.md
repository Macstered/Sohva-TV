# Sohva TV 0.1.0-beta.11

Prepared: **8 September 2026**. Android version code: **12**.

Eleventh Sohva TV tester package. On beta 3 or later, fetch it from **Settings >
About > Check for updates**; on earlier betas, install it over the existing
app without uninstalling or clearing data. This is a non-commercial beta, not
a public-launch announcement.

## Changed since beta 10

Answers to the first day of public testers: an M3U address that "does not
load" now says what it found, and the three things the interface-size tester
asked for.

- **Test address.** An M3U source's page has a **Test address** button next
  to Save. It reads the start of the playlist and says how many entries it
  found, or what went wrong, without saving anything.
- **An address that answers with something else.** A sign-in page, a JSON
  error or a bare sentence from a panel used to import as hundreds of
  nameless channels and count as success. It is now refused with "The
  address did not answer with an M3U playlist", and an empty playlist keeps
  the previous channels, films and series instead of replacing them with
  nothing.
- **The app introduces itself by name** on every playlist and guide fetch,
  as it already did for streams. A panel that drops unknown user agents used
  to answer with 403, a web page or nothing.
- **Failures in your own language.** The source row, the health line, the
  guide's empty screen and the diagnostics file say what went wrong in the
  interface's language, with the HTTP code where there is one. A 403 used
  to show as "HTTP error %1$d".
- **Interface size** ends at **Smaller (70 %)**, one step below Small, and
  the picker names each step's percentage.
- **Hidden rows out of the way.** **Show hidden** in Channel management takes
  hidden channels out of the list, and the library manager opens on
  **Enabled** or **All** to match and keeps its filter when a move begins.
  Both now step past the neighbour on screen rather than past one a filter
  hides, which used to look like nothing happened.
- **What changed, in About.** Once the release list has been read, About
  shows what changed in the installed beta, or in the newer one when there
  is one. It is in English whatever the interface language.

## Changed in beta 10

Profiles, watched state, a chosen skip step, subtitle size and colour, four
drafted interface languages, and the corner view. Database version 25.

## Changed in beta 9

The app introduces itself to a provider by name, streams speak HTTP/1.1, and
a playback failure says why on screen and in the diagnostics file.

## Included

- Sohva TV name, couch/play logo, TV launcher banner and accessible wordmarks.
- Sohva Sport name and orange companion branding.
- Live TV, programme guide, movies, series, playback resume and optional
  TMDB / TVmaze / API-Sports integrations using the tester's own credentials.
- Library organisation, custom channel lists, remote button mapping, phone
  setup, interface size, reminders, score ticker, diagnostics file and in-app
  update check.
- Profiles, watched state, skip step, subtitle appearance and the corner view.
- Test address for M3U sources, a Smaller interface size, hidden rows kept
  out of the editors, and what changed on the About page.
- English and Finnish app interface, with Spanish, Portuguese, German and
  Swedish as drafts; English installation and testing guide.

## Update compatibility

Future Sohva TV betas should be installed over this one without uninstalling or
clearing data, or fetched from **Settings > About**. This beta uses Android
build 12; every later distributed build must use a higher build number.

No database change: version 25, as in beta 10. One new preference, whether
the editors list hidden rows, is carried by the backup as `editorsShowHidden`;
a beta 10 reads a beta 11 backup and ignores it. No change to how sources or
credentials are stored.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build gate, the unit-test suite and a full emulator run of
the database and UI tests on the released source, 69 and 200 tests. The
release build is also checked against a device's own dex verifier before
packaging, a gate added after a build that every test passed turned out to be
one ART would refuse to load.

Test address, the hidden-row toggle, the smaller interface and the About
notes were exercised on a Shield by the owner. The four drafted languages
remain drafts awaiting a native reading, and some labels in Spanish,
Portuguese and German are longer than the space allows. These are bounded
results, not a guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control.

## Source publication

The source for this beta is published under `GPL-3.0-only` in this repository,
and the release tag points at the commit it was built from. It produces the
same signed APK when built with the private release identity; the signing key
and credentials are not part of the source.
