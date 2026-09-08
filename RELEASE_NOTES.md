# Sohva TV 0.1.0-beta.10

Prepared: **8 September 2026**. Android version code: **11**.

Tenth Sohva TV tester package. On beta 3 or later, fetch it from **Settings >
About > Check for updates**; on earlier betas, install it over the existing
app without uninstalling or clearing data. This is a non-commercial beta, not
a public-launch announcement.

## Changed since beta 9

The largest beta since the settings rebuild: more than one viewer in a
household, and the three things film and series testers asked for.

- **Profiles.** Favourites, continue watching, recent channels and the
  parental lock now belong to a viewer rather than to the device. Add a
  second viewer in **Settings > General > Profiles**, and the app asks who is
  watching when it starts. The first viewer keeps everything already on the
  device; a new one starts empty. Backups carry every profile.
- **Watched, and marked watched.** A film or episode counts as watched at
  ninety per cent, or with three minutes left. **Mark as watched** and
  **Mark season as watched** are on the detail pages, and holding **OK** on a
  Continue watching card offers Resume, Start over, Mark as watched and
  Remove. A tick marks a watched film on the library wall, a watched episode
  on its card, and a season whose episodes are all watched on its pill.
- **A faster skip.** **Settings > Playback > Skip step** sets how far the
  arrows jump, from ten seconds to two minutes. Holding the button climbs the
  ladder, and the size of each jump appears on screen. The rewind and forward
  buttons name the step they will take.
- **Subtitle size and colour.** Size, colour and background are in
  **Settings > Playback**, each following the television's own captioning
  settings until changed. Playback info now names the subtitle format as
  well: a stream carrying image subtitles (PGS, VobSub, DVB) shows pictures
  of text, which no setting can restyle.
- **Four more interface languages, as drafts.** Spanish, Portuguese, German
  and Swedish join English and Finnish in **Settings > General**. They are
  drafted from the English and labelled as drafts, and About links to where
  corrections are welcome. English and Finnish are unchanged.
- **Keep watching in a corner.** Home during playback can shrink the picture
  to a corner over the television's home screen instead of stopping it. It is
  **off until switched on** in **Settings > Playback**, because a television
  home screen need not offer any way to dismiss such a window: to leave it,
  open Sohva TV again for full screen and press Back.

## Changed in beta 9

The app introduces itself to a provider by name, streams speak HTTP/1.1, and
a playback failure says why on screen and in the diagnostics file.

## Changed in beta 8

Reminders presented inside the app, a score ticker in the player, paged
background jobs for very large catalogues, Save diagnostics, and ANALYZE
after imports.

## Included

- Sohva TV name, couch/play logo, TV launcher banner and accessible wordmarks.
- Sohva Sport name and orange companion branding.
- Live TV, programme guide, movies, series, playback resume and optional
  TMDB / TVmaze / API-Sports integrations using the tester's own credentials.
- Library organisation, custom channel lists, remote button mapping, phone
  setup, interface size, reminders, score ticker, diagnostics file and in-app
  update check.
- Profiles, watched state, skip step, subtitle appearance and the corner view.
- English and Finnish app interface, with Spanish, Portuguese, German and
  Swedish as drafts; English installation and testing guide.

## Update compatibility

Future Sohva TV betas should be installed over this one without uninstalling or
clearing data, or fetched from **Settings > About**. This beta uses Android
build 11; every later distributed build must use a higher build number.

**This beta changes the database.** Viewing positions gain the profile they
belong to, and the change is applied automatically on first start; existing
positions stay with the first viewer. Backups written by this beta carry the
profiles and are not readable by earlier ones. No change to how sources or
credentials are stored.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build gate, the unit-test suite and a full emulator run of
the database and UI tests on the released source. The release build is also
checked against a device's own dex verifier before packaging, a gate added
after a build that every test passed turned out to be one ART would refuse to
load.

Profiles and the corner view were exercised on the emulator and on a Shield.
The four new languages are drafts awaiting a native reading, and some labels
in Spanish, Portuguese and German are longer than the space allows. These are
bounded results, not a guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control.

## Source publication

The source for this beta is published under `GPL-3.0-only` in this repository,
and the release tag points at the commit it was built from. It produces the
same signed APK when built with the private release identity; the signing key
and credentials are not part of the source.
