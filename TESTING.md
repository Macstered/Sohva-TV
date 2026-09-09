# Sohva TV beta tester checklist

Build: **0.1.0-beta.12 (13)**. Use only sources you are authorized to access.
Test at your own pace; note failures rather than repeatedly resetting the app.

## Suggested first session

1. **Install / update:** verify the version in Settings > About. If updating,
   confirm that existing sources, settings and favourites are still present.
2. **Setup:** import your playlist/channels and programme guide. If available,
   import movies and series. Note the approximate library size and time taken
   for the initial import separately from normal browsing.
3. **Live TV:** open several channels; use Back, the guide and player controls.
   Try available audio tracks and subtitles. Respect your provider's connection
   limit when using another player or device at the same time. If a channel
   fails, note the whole message on screen: from beta 9 it names the cause,
   such as "Connection reset" or "Response code: 403", with addresses removed.
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
10. **Reminders:** in the guide, press OK on a programme that has not started
    (or hold OK on any programme) and choose **Remind me**; on a Sohva Sport
    match card use **Remind me**. The first reminder explains how a reminder
    can open Sohva TV; try both answers. Then let one fire while watching
    another channel, and one while another app is on screen. Note what
    appeared and when, relative to the programme's start.
11. **Score ticker:** while a followed match is on, open the player's quick
    actions and switch the **Score ticker** on; check it updates and that it
    does not cover the playback information panel.
12. **Very large libraries:** if your provider has tens of thousands of films,
    leave the app in the background for ten minutes after the first import and
    then reopen it; note whether it came back where you left it or restarted.

13. **Profiles:** in **Settings > General > Profiles**, add a second viewer,
    restart the app and choose it at the who-is-watching screen. Check that
    the first viewer's favourites, recent channels and resume points are
    untouched, and that the new one starts empty. Remove the second viewer
    afterwards if you would rather not be asked at every start.
14. **Watched state:** finish a film or episode and check that it is marked
    watched; try **Mark as watched** and **Mark season as watched** by hand,
    and hold **OK** on a Continue watching card. Note anywhere a tick is
    missing or wrong.
15. **Skip step and subtitles:** set a skip step in **Settings > Playback**,
    then hold an arrow during a film and watch the jump grow. Change subtitle
    size and colour; if nothing changes, open **Playback info** and report the
    subtitle format it names.
16. **Keep watching in a corner:** switch it on in **Settings > Playback**,
    press Home during playback, and report whether your television's home
    screen lets you reach or close the corner. Opening Sohva TV again returns
    it to full screen.
17. **Another language:** switch the interface to Spanish, Portuguese, German,
    Swedish or Italian. These are drafts: report wording that reads wrongly
    and any label that overflows its space.
18. **Test address:** on an M3U source's page, press **Test address** before
    saving. Report what it says for a working address, for a mistyped one, and
    for a link your provider has since disabled.
19. **Hidden channels out of the way:** in Channel management, press **Show
    hidden** to take hidden channels out of the list, then reorder with the
    arrows; in the library manager, set the filter to **Enabled** and move a
    group. Hidden rows should stay out of the way through the move.
20. **Smaller interface:** **Settings > General > Interface size** now ends at
    Smaller (70 %). Report whether it reads comfortably from your seat.
21. **What changed:** after **Check for updates**, About should show what
    changed in the installed beta, or in the newer one when there is one. It
    is in English whatever the interface language, heading included.
22. **A channel's own logo and number:** in Channel management, give a channel
    a logo address or press **Logo from phone**, scan the code and choose a
    picture on the phone; give it a channel number and save. The guide and the
    player should show your logo, and the guide your number.
23. **Channel numbers off:** **Settings > General > Channel numbers** hides
    numbers in the guide; report whether the channel column reads better
    without them on your screen.
24. **Dial a channel:** in the guide, or while watching live TV, type a
    channel's number on the remote. Two seconds after the last digit the guide
    should land on that channel, or the picture switch to it; a number no
    channel has should say so and change nothing.
25. **What a profile may see:** with two or more profiles, open **Settings >
    General > Profiles**, choose a profile under **What this profile may see**
    and limit its live TV, film or series groups. That profile's guide,
    libraries and search should show only those groups. Set a parental PIN as
    well: switching to another profile, and opening Settings from the limited
    profile, should then ask for it. With two profiles the home page's left
    menu gains **Who is watching**, for changing profile without Settings.
26. **Phone QR codes:** the source page's QR code and Channel management's
    **Logo from phone** code now open in a box of their own. Report a code
    that is cut off or that moves when you press down on the D-pad.
27. **Opening a series:** the first open of a series fetches its episodes and
    should say **Loading episodes…** in the top corner until they appear. The
    cast is now one line of names under the synopsis, so the seasons and the
    episodes should sit on screen from the start.
28. **Group names in the libraries:** the group column of the film and series
    libraries is wider and a long name may take two lines. Report a group
    whose name still cannot be read, and whether the poster wall beside it
    still looks right on your screen.

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
- Spanish, Portuguese, German, Swedish and Italian are drafted from the
  English and not yet read by a native speaker; some labels overflow the
  space they sit in.
- A tick marks a watched film, episode and completed season, but there is no
  tick yet on a series poster in the library.
- A television home screen need not offer any way to reach or close the corner
  view; open Sohva TV again for full screen, then press Back to stop it.
- Live TV subtitles burned into the picture by the provider, and image
  subtitle tracks in a film, cannot be resized or recoloured.
- No recording, downloads, local timeshift or multiview. Catch-up needs provider
  support; it is not local recording.
- Newer betas are fetched from **Settings > About > Check for updates**; there
  is no automatic crash-report upload. Diagnostics are saved only when you
  choose to.
- Android TV shows no popup for an app that is not on screen. Inside Sohva TV a
  reminder appears as a dialog. For Sohva TV to come forward over another app,
  allow it to **display over other apps** when the first reminder asks
  (Settings > General shows the state). Without that, the reminder waits in the
  TV's notification panel.
- Menus inside a frame while the video fills the whole screen means the
  device's own display settings have shrunk the interface, not the app: look
  under Device Preferences > Display & Sound, or Display, for Screen position,
  Adjust screen size, Display area or Overscan, and set it to full. The picture
  is not affected because such devices scale the interface alone.
- Shield and Android TV emulator testing does not establish compatibility with
  every TV, remote, accessibility service or codec.

## Report a problem

Email [hello@luontra.fi](mailto:hello@luontra.fi). If the developer asks for
diagnostics, **Settings > About > Save diagnostics** writes a text file of
recent events and refresh states with addresses and keys removed; attach
that file rather than logs of your own. If a public issue tracker is
enabled later, you can use the same template there for non-sensitive reports.

```text
Version: 0.1.0-beta.12 (13)
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
