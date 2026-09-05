# Sohva TV 0.1.0-beta.3

Prepared: **5 September 2026**. Android version code: **4**.

Third Sohva TV tester package. Install it over beta 1 or beta 2 without
uninstalling or clearing data. This is a non-commercial beta, not a public-launch
announcement.

## Changed since beta 2

- A new source syncs as soon as it is saved, and **Sync everything** on the
  Playlists page refreshes all sources at once.
- The first sync is much faster: a playlist and its guide arrive in a minute or
  two instead of ten. Only the programmes the channels can show are kept.
- Very large Xtream movie and series lists import instead of being refused as
  too big.
- The guide no longer says there are no channels while the first sync is still
  running; it shows each source's progress, and a failed import says why.
- The app can fetch the next beta itself: **Settings > About > Check for
  updates**.
- Playlist details can be typed in from a phone: **Set up from a phone** on
  the Playlists page.
- TMDB titles, plots and artwork can be asked for in any of 22 languages under
  **Programme data & artwork**.
- The remote's D-pad and channel buttons, on a press and on a hold, can be
  mapped to the channel list, programme info, previous channel and other
  actions under **Playback & remote**.
- The guide and the programme info during playback open at once on a large
  library; they used to take ten seconds or more, and the guide could stop
  responding. The player's channel list reads one group at a time, and channel
  up and down stay within the group.
- Moving between groups in the guide keeps the rows and the info box in place
  until the new group is read.
- Saved source addresses and keys are protected by one wrapped data key rather
  than a keystore round trip per address, which is part of the faster sync.

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
build 4; every later distributed build must use a higher build number. Existing
settings, sources and backups carry over; saved addresses are re-protected
with the new data key on first use.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build gate and the unit-test suite, and a full emulator run
of the database and UI tests on the released source. The build was installed
on a Shield over beta 2 with data intact and used against real playlists,
including one with tens of thousands of channels. These are bounded results,
not a guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control.

## Source publication

The source for this beta is published under `GPL-3.0-only` in this repository,
and the release tag points at the commit it was built from. It produces the
same signed APK when built with the private release identity; the signing key
and credentials are not part of the source.
