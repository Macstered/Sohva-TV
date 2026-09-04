# Sohva TV beta tester checklist

Build: **0.1.0-beta.2 (3)**. Use only sources you are authorized to access.
Test at your own pace; note failures rather than repeatedly resetting the app.

## Suggested first session

1. **Install / update:** verify the version in Settings > About. If updating,
   confirm that existing sources, settings and favourites are still present.
2. **Setup:** import your playlist/channels and programme guide. If available,
   import movies and series. Note the approximate library size and time taken
   for the initial import separately from normal browsing.
3. **Live TV:** open several channels; use Back, the guide and player controls.
   Try available audio tracks and subtitles. Respect your provider's connection
   limit when using another player or device at the same time.
4. **Movies:** browse several provider and genre groups, move quickly through
   the poster wall, open a title, go Back, and check that focus returns sensibly.
   Start playback, stop partway through, then reopen and resume.
5. **Series:** open a series, choose a season and episode, play part of it,
   stop and resume. If enabled, try continuing to the next episode.
6. **Organization:** try favourites, group visibility/order and a small custom
   group. Check that changes persist when leaving and returning to the screen.
7. **Optional services:** test TMDB title matching/artwork and your selected
   Sohva Sport competitions. Report missing data separately from navigation or
   performance problems.
8. **Network recovery:** if convenient, disconnect only the TV/emulator's
   network briefly during Live TV, then reconnect. Note whether playback
   recovers and whether manual retry was needed. Do not disrupt a shared router.
9. **Restart:** exit and reopen the app; then restart the Android TV device
   completely. Check sources, keys/settings, groups, favourites and resume
   points again. Standby/sleep alone is not a cold-restart test.

For library performance, report the sequence of actions, approximate movie /
series count, whether an import or metadata refresh was running, and whether
the repeated "library not downloaded" message appeared. Do not send the
playlist itself. A short first-load delay and persistent browsing stalls are
different issues.

## Known limitations

- Some posters may remain missing despite having title information; selecting
  the same correct TMDB match again on the title-information page can fill them.
- Initial provider downloads and metadata matching take time and depend on
  provider speed. Artwork coverage, episode details, catch-up and stream formats
  vary by source.
- A movie is assigned to its main genre rather than every reported genre.
- No recording, downloads, local timeshift or multiview. Catch-up needs provider
  support; it is not local recording.
- There is no automatic in-app updater or automatic crash-report upload in
  this beta. Install each newer signed beta manually over the existing app.
- Shield and Android TV emulator testing does not establish compatibility with
  every TV, remote, accessibility service or codec.

## Report a problem

Email [hello@luontra.fi](mailto:hello@luontra.fi). If a public issue tracker is
enabled later, you can use the same template there for non-sensitive reports.

```text
Version: 0.1.0-beta.2 (3)
Device model:
Android / Google TV version:
Fresh install or update:
Source type: M3U or Xtream (no address or login)
Approximate library size, if relevant:
Steps to reproduce:
Expected result:
Actual result / exact error message:
How often it happens:
Approximate date/time and timezone:
Did restarting the app help?:
```

Redact playlist and stream URLs, usernames, passwords, API keys, account IDs
and anything private in screenshots, recordings and error messages. Do not
attach raw logs or an encrypted backup. If diagnostics are needed, the
developer will agree a safe, private way to collect them with you first.

If settings disappear, stop before resetting or reinstalling and report what
happened. If a credential was accidentally posted publicly, remove the post
and revoke or change that credential with its provider.
