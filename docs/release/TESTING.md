# Sohva TV tester checklist

Build: `0.2.0-beta.1` (Android build 103). Use only sources you are allowed to
use. Test at your own pace. When something fails, write it down before you
reset or reinstall anything.

This is the rebuilt app. Everything that worked in beta 23 should still work
and look almost the same. The most useful reports are:

- anything that worked in beta 23 and now fails, looks different or is missing;
- anything that is slow, especially on a low-end TV box: say which device;
- focus that lands somewhere you did not choose, or does not come back after
  Back, a dialog or a data refresh.

## First start over beta 23

- [ ] Update over beta 23 as described in [INSTALL.md](INSTALL.md), without
      uninstalling. Note whether you used the in-app updater or sideloaded.
- [ ] On the first start, note whether **Updating Sohva TV…** appeared and
      roughly how long it stayed.
- [ ] **Settings > About** shows `0.2.0-beta.1`.
- [ ] Your colour theme, interface size, language, time zone and start screen
      are as before.
- [ ] Profiles are all there. For each: favourites, recent channels, locked
      channels and allowed groups are as before. The parental PIN still works.
- [ ] Your playlists are listed and start importing. The guide and the movie
      and series libraries fill in within a few minutes. Note how long a large
      library took.
- [ ] Channel edits (names, numbers, hidden channels, order, guide channel),
      phone logos, channel lists and group rules are as before once the import
      finishes.
- [ ] Resume points of films and episodes are there after the library import.
- [ ] Reminders you set in beta 23 are still set.
- [ ] Sohva Sport follows the same sports and competitions; streams you
      confirmed or rejected keep that choice.
- [ ] Titles you matched by hand keep their artwork and information.
- [ ] Discover shows your addons, catalog order, Library and Continue watching.
- [ ] Trakt is still connected in **Settings > Accounts** for each profile
      that had it.
- [ ] Restart the app and then the whole TV. Everything above is still there.

## Home

- [ ] The top of Home describes the focused card. Continue watching, Today's
      sport and recent channels show what you expect.
- [ ] Press Left to open the rail and Right to go back to the rows. Focus
      never jumps to the rail on its own, also while rows load.
- [ ] Return from playback: focus is back on the card you left, and Continue
      watching shows the new position.
- [ ] A film you have in both Discover and a provider library has one card.

## Live TV and the guide

- [ ] Live TV opens on a group, or on the group of the channel you came from.
      **All channels** is on the group list.
- [ ] Hold Up, Down, Left and Right through the guide, also in your largest
      group. Focus moves one step per press and never skips.
- [ ] Leave the guide and come back within ten minutes: the list appears at
      once.
- [ ] Press Left, choose **Options**, switch playlist: focus lands on the new
      playlist's first channel. Closing the options returns focus to where
      you opened them.
- [ ] Type a channel number in the guide or during playback. The guide or the
      picture moves to that channel after two seconds.
- [ ] Search for a programme in the guide. Matches appear while you type and
      the keyboard stays open.
- [ ] In the player, try the channel list, programme info, previous channel,
      audio and subtitle tracks, and channel up and down.
- [ ] In **Channel management**, rename, renumber, hide and move a channel,
      and set a logo from a phone. **Change EPG channel** now opens a list you
      can search.
- [ ] If a channel fails, note the whole message on screen.

## Catch-up and reminders

- [ ] On a catch-up channel, open a past programme. It plays from the archive
      with its own transport controls. Seek and skip work.
- [ ] A programme older than the provider's archive says it is not available.
- [ ] Set **Remind me** on a guide programme and on a Sohva Sport match. Let one
      fire while you watch another channel and one while another app is on
      screen. Note what appeared and when.

## Movies and series

- [ ] Browse provider groups, genres and your own groups. Hold Down on the
      poster wall: focus moves one row per press and never jumps.
- [ ] Open a title, go Back: focus returns to the same card.
- [ ] Play a film part-way, stop, and resume. Let a film finish: it returns to
      its details page.
- [ ] Play an episode to the end with autoplay on: the next one starts, also
      across seasons.
- [ ] Try **Mark as watched** and **Mark season as watched**.
- [ ] Try **Manage groups & content**: hide, reorder and sort groups.
- [ ] On the Shield with **Match the display to the picture** on, play a film
      to its end. It should not stay paused after the picture mode changes.

## Search

- [ ] Search finds channels, programmes, films, series and episodes.
- [ ] Words now match from their start: "mat" finds "Match", but "atch" does
      not. Tell us if this makes a title hard to find.
- [ ] Accented and unaccented spellings find the same title.

## Discover addons and subtitles

- [ ] Follow [ADDONS.md](ADDONS.md) to add an addon by address, by file, from
      a phone or from a Stremio account. Check the preview before confirming.
- [ ] A plain HTTP address is refused. An addon that needs configuring is
      refused with guidance.
- [ ] If you use an addon that redirects (for example OpenSubtitles Pro), it
      now installs and its subtitles load.
- [ ] Browse rows and Show all, search, open a movie and a series episode,
      pick a source and play.
- [ ] Automatic subtitles follow your preferred languages. Open the subtitle
      picker: D-pad moves stay inside the picker and never reach the player
      controls behind it.
- [ ] Adjust **Subtitle sync** on a downloaded subtitle and check that the
      subtitles really move.
- [ ] Add and remove titles in Library. Restart and check that Library and
      progress are kept.

## Trakt

- [ ] Connect: **Settings > Accounts > Connect Trakt**, scan the code, approve
      on the phone.
- [ ] Play a movie and an episode from the libraries and one title in
      Discover for a minute each. Within a minute they appear in your Trakt
      playback progress. Live TV and catch-up are never sent.
- [ ] If your Trakt history has more than 100 watched movies or any watched
      episodes, check that watched ticks and progress show on all of them,
      not only the first 100.
- [ ] Pause a title on another device or on trakt.tv. Continue watching on
      Home shows it, and Continue resumes there.
- [ ] Check the **Watch next** and **Recommended for you** rows. A card opens
      the title's own page, or a lookup through your Discover addons.
- [ ] If Trakt asks the app to wait, the Accounts panel says how many minutes.
      Note if this happens during normal use.
- [ ] **Disconnect** removes the sign-in from this TV only.

## Sohva Sport

- [ ] Choose followed sports and competitions in **Settings > Sohva Sport**.
      Each sport gets a tab.
- [ ] Open a match. Available and Possible streams are listed. **Confirm**,
      **Reject** and **Restore** keep focus in the same row.
- [ ] Back from the match returns focus to its card.
- [ ] Play a matched channel and switch the **Score ticker** on from the
      player's quick actions.
- [ ] When your API-Sports daily quota runs out, the screen says so and keeps
      the saved day. It asks again the next day.

## Profiles and parental PIN

- [ ] Add a profile, restart, and choose it at **Who is watching**. The new
      profile starts empty; the first is unchanged.
- [ ] Limit a profile's groups under **What this profile may see**. Its guide,
      libraries, search and Continue watching show only those groups.
- [ ] Set a PIN. Switching profile and opening Settings from a restricted
      profile ask for it. A wrong PIN keeps focus in the PIN field.
- [ ] Restricted profiles see no Discover and no Trakt.
- [ ] Removing a profile asks first, and also removes its Discover data and
      its Trakt connection.

## Settings, backup and restore

- [ ] Try every colour theme and interface size on a few screens. Text and
      focus must stay readable.
- [ ] Each Settings section now shows its own status line for its actions.
- [ ] **Settings > Backup & tools > Save backup** with a strong password, then
      **Restore backup**. If the restore would remove sources, it names them
      and asks first.
- [ ] **Clear all guide data** removes only programme listings. Channels,
      films, series and watch progress stay.
- [ ] Turn on **Keep watching in a corner** in **Settings > Playback**, press
      Home during playback, and note what your TV does. Reports from Android 12
      and newer are especially welcome.
- [ ] Switch the interface to a draft language (Spanish, Portuguese, German,
      Swedish or Italian). Report wording that reads wrongly or overflows.

## Updates

- [ ] **Settings > About > Check for updates** says the app is up to date.
- [ ] About shows what changed in this build.
- [ ] When a later beta is published, update through the app. If Android asks
      you to allow installs and closes the app, open it again: the download
      is still there and **Install** works without downloading again.

## Report a problem

Email [hello@luontra.fi](mailto:hello@luontra.fi). Use this template:

```text
Version: 0.2.0-beta.1 (100)
Device model:
Android / Google TV version:
Updated from beta 23 (in-app or sideload) or fresh install:
Source type: M3U, Xtream or Discover addon name (no address or login)
Approximate library size, if relevant:
Steps to reproduce:
Expected result:
Actual result / exact message on screen:
How often it happens:
Approximate date, time and time zone:
Did restarting the app help?:
```

If the developer asks for diagnostics, use **Settings > About > Save
diagnostics** soon after the problem, before you close the app. It writes a
text file to a place you choose. The app never sends it anywhere.

The file contains:

- the app version, device model, Android version, language, time zones and
  screen size;
- the main settings;
- your sources by the names you gave them, their type and state, and the
  result of each source's last refreshes;
- the app's last 600 event lines since it started: counts, timings and
  failures.

The file does not contain addresses beyond a provider's host name, user names,
passwords, API keys, tokens or addon addresses; these are removed before each
line is kept and again when the file is written. The log does not list the
titles or programmes you watched. It is kept only in memory, so it is empty
after the app restarts.

Read the file before you send it. Do not send raw system logs, screenshots
that show addresses or logins, or an encrypted backup. If a password or key
was posted publicly by mistake, delete the post and change that password or
key with its provider.

If settings or data disappear after the update, stop before you reset or
reinstall anything, and report what happened.

## Known limitations

- Search matches the start of words, not text inside a word.
- Subtitle timing works only for subtitles downloaded from addons, not for
  subtitles inside the video.
- Discover accepts HTTPS addons only.
- Spanish, Portuguese, German, Swedish and Italian are drafts, including the
  Trakt texts. The addon phone page is English only.
- Initial imports and artwork matching take time and depend on the provider.
- No recording, downloads, local timeshift or multiview. Catch-up needs
  provider support.
- A TV's own screen size or overscan setting can shrink the menus while video
  still fills the screen. Set the TV's display area to full.
- Tests on the Shield and emulators do not prove that every TV, remote or
  codec works.
