# Sohva TV 0.1.0-beta.1

Prepared: **3 September 2026**. Android version code: **2**.

First uniquely numbered Sohva TV tester package. This is a non-commercial beta,
not a public-launch announcement.

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
clearing data. This beta uses Android build 2; every later distributed build
must use a higher build number.

Release certificate SHA-256:

```text
985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6
```

The APK's own checksum is in the accompanying `SHA256SUMS.txt`.

## Testing context

The beta passed its build and lint gate, 741 unit-test executions and a fresh
full run of **163 Android TV emulator UI tests**. Playlist, TMDB and API-Sports
setup, TV/movie/series playback and resume, network recovery, and persistence
after a cold restart have also been tested on Shield. These are bounded results,
not a guarantee for every device or source.

See [known limitations and the feedback checklist](TESTING.md). A missing
poster can still require selecting the correct TMDB match again. Provider
availability, quotas and stream compatibility remain outside the app's control.
