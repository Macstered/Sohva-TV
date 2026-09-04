# Sohva TV 0.1.0-beta.2

Prepared: **4 September 2026**. Android version code: **3**.

Second numbered Sohva TV tester package. Install it over beta 1 without
uninstalling or clearing data. This is a non-commercial beta, not a public-launch
announcement.

## Changed since beta 1

- The home screen's continue-watching row, and the movie and series history
  views, no longer take seconds to fill on a large library. A query reached the
  series table without its key and walked every series per watched episode; the
  joins are now pinned and a build-time test keeps them that way.
- A programme-guide refresh that brings back nothing usable no longer replaces
  the guide on screen. When the provider's feed is empty, or matches none of a
  source's channels, the previous guide is kept and the source's EPG row in
  Settings reports the failure instead of a success.
- AFL scores show the totals in the score slot, with goals and behinds on a
  line beneath, so the team marks on match cards and the home sport row are no
  longer pushed aside by a long scoreline.
- Backing out of a stream chosen on a Sohva Sport match card returns to that
  match card. It used to land on the programme guide.

## Included

- Sohva TV name, couch/play logo, TV launcher banner and accessible wordmarks.
- Sohva Sport name and orange companion branding.
- Revised movie/series browsing, provider and main-genre groups, poster
  fallback handling and library-organization controls.
- Live TV, programme guide, movies, series, playback resume and optional
  TMDB / TVmaze / API-Sports integrations using the tester's own credentials.
- English and Finnish app interface; English installation and testing guide.

## Update compatibility

Future Sohva TV betas should be installed over this one without uninstalling or
clearing data. This beta uses Android build 3; every later distributed build
must use a higher build number. There is no database, preference, key or
backup-format migration between beta 1 and beta 2.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build gate and the unit-test suite, including new tests for
the pinned home-row queries, the guide-refresh guard and the AFL score format.
The guide and catalogue database tests were also run on a Shield against its own
SQLite, and the build was installed on a Shield over beta 1 with data intact.
These are bounded results, not a guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control:
on 4 September one provider's guide feed went empty for a day, which is the
case the new refresh guard is for.

## Source publication

The source for this beta is published under `GPL-3.0-only` in this repository,
and the release tag points at the commit it was built from. It produces the
same signed APK when built with the private release identity; the signing key
and credentials are not part of the source.
