# Feature inventory

> Every user-visible capability of Sohva TV 0.1.0-beta.23, collected from section 2 of each spec
> (generated 24 September 2026). This is the parity checklist for the rebuild: tick an item in the
> new repository's copy when its requirements are implemented and its acceptance tests pass. The
> requirements behind each item are in the linked spec, section 4.

Items describe the **current** app. Where a spec records a defect of beta 23 and prescribes the fixed
behaviour, the spec's requirement wins; open questions for the owner are listed at the end of each
spec's section 10.

## Summary

| Spec | Area | Items | Milestone |
|---|---|---|---|
| [01](../specs/01-app-shell-navigation.md) | App shell and navigation | 34 | M0 |
| [02](../specs/02-home.md) | Home | 36 | M5 |
| [03](../specs/03-search.md) | Search | 19 | M5 |
| [04](../specs/04-profiles-parental.md) | Profiles and parental controls | 23 | M6 |
| [10](../specs/10-sources-and-import.md) | Sources and import | 42 | M1 |
| [11](../specs/11-phone-setup.md) | Phone setup | 14 | M1 |
| [20](../specs/20-live-tv-guide.md) | Live TV guide | 52 | M2 |
| [21](../specs/21-channel-management.md) | Channel management | 30 | M3 |
| [22](../specs/22-catchup-and-reminders.md) | Catch-up and reminders | 27 | M3 |
| [30](../specs/30-player.md) | Player: live, catch-up and VOD playback | 47 | M2 |
| [31](../specs/31-remote-button-mapping.md) | Remote button mapping | 19 | M3 |
| [40](../specs/40-movies-and-series.md) | Movies and series | 58 | M4 |
| [41](../specs/41-metadata-enrichment.md) | Metadata enrichment | 34 | M4 |
| [42](../specs/42-library-organization.md) | Library organisation | 35 | M4 |
| [50](../specs/50-discover-addons.md) | Discover: Stremio-compatible addons, Search and Library | 75 | M9 |
| [51](../specs/51-trakt.md) | Trakt sync | 30 | M10 |
| [60](../specs/60-sohva-sport.md) | Sohva Sport | 57 | M8 |
| [70](../specs/70-settings.md) | Settings | 40 | M7 |
| [71](../specs/71-backup-restore.md) | Encrypted backup and restore | 15 | M7 |
| [72](../specs/72-updates-about-diagnostics.md) | Updates, About and diagnostics | 29 | M7 |
| [73](../specs/73-security-privacy.md) | Security and privacy | 29 | M1 |
| [74](../specs/74-localization.md) | Localisation | 13 | M7 |
| | **Total** | **758** | |

## App shell and navigation

Spec: [specs/01-app-shell-navigation.md](../specs/01-app-shell-navigation.md) · Milestone: M0 · 34 items

- [x] **SHELL-01** Sohva TV appears in the Android TV launcher (Leanback launcher entry, 320×180 dp banner, adaptive icon, label `app_name` "Sohva TV").
- [x] **SHELL-02** Launch picture: the Sohva mark and wordmark on the dark vertical gradient, drawn by the window before the first frame.
- [x] **SHELL-03** A Compose launch screen identical to the launch picture stays up while local state loads, so nothing moves at the hand-over.
- [x] **SHELL-04** The saved colour theme is used from the first app frame (no flash of the default theme) and no text flashes between the launch picture and the first screen.
- [x] **SHELL-05** The whole interface is drawn at the chosen interface size (100, 90, 80 or 70 %); the launch picture stays at device density.
- [x] **SHELL-06** The chosen interface language is applied before any text is resolved (below Android 13 by the app, from Android 13 by the platform's per-app language).
- [ ] **SHELL-07** Start screen setting: Home (default), Programme guide, or Last channel.
- [x] **SHELL-08** Last channel start opens the guide on that channel and plays it; a PIN-locked channel asks for the PIN first; a channel that no longer exists falls back to the guide.
- [ ] **SHELL-09** Who is watching at start when the household has more than one profile and the question is switched on (details in [Profiles](../specs/04-profiles-parental.md)).
- [x] **SHELL-10** A destination stack: every screen returns with Back to the screen it was opened from; Back on Home leaves the app.
- [ ] **SHELL-11** Live playback started from the guide, a Home channel card, a search result or a dialled number returns with Back to the guide, focused on the channel just watched.
- [ ] **SHELL-12** Live playback started from a Sohva Sport match card or a reminder returns to where it started; catch-up playback always returns to the previous screen.
- [x] **SHELL-13** Changing channel inside the player replaces the player, so Back never steps back through the channels zapped.
- [x] **SHELL-14** Zap-back: the player offers the channel watched before the current one (session memory).
- [x] **SHELL-15** Player shortcuts to Home, the guide, Sohva Sport and "guide at this channel" reset the stack to that screen.
- [ ] **SHELL-16** Resuming a film or episode from Home puts its library page and its details page under the player, so Back walks Details, then Movies/Series, then Home.
- [ ] **SHELL-17** A finished film returns to its details page; a finished episode starts the next one (when autoplay is on) or returns to the series page, also when playback began in Search.
- [ ] **SHELL-18** A Trakt card on Home opens the library's own details page when the title is in the library, otherwise the Trakt title page.
- [ ] **SHELL-19** Returning to Movies or Series restores the browse position; returning to Sohva Sport restores the open match card; returning to the guide focuses the channel.
- [x] **SHELL-20** Home navigation rail down the left edge: icons only at rest, widening over the content with labels when it takes focus.
- [x] **SHELL-21** Rail destinations: Live TV, Sohva Sport, Movies, Series, Search, Discover (only when available), Who is watching (only with two or more profiles), Settings, under a Home marker.
- [x] **SHELL-22** Settings sits behind the parental PIN for a restricted profile.
- [x] **SHELL-23** A channel outside a restricted profile's groups is refused with a short toast "This profile cannot watch that channel".
- [x] **SHELL-24** A PIN-locked channel asks for the PIN before live, catch-up and zapped playback.
- [ ] **SHELL-25** A reminder notification opens its channel or its match card, whether the app was closed or already running.
- [ ] **SHELL-26** A fired reminder appears as a dialog over any screen; the first reminder ever set explains, once, how to let reminders open the app; Android 13+ asks for the notification permission when a reminder is first set (details in [Catch-up and reminders](../specs/22-catchup-and-reminders.md)).
- [x] **SHELL-27** Picture in picture (off by default): pressing Home while a stream plays shrinks it to a 16:9 corner with its own Close button; opening the app again restores full screen.
- [ ] **SHELL-28** Background maintenance (metadata matching) pauses while the app is in front and resumes 30 s after it leaves.
- [ ] **SHELL-29** Playlist, guide and catalogue refreshes follow the refresh interval setting.
- [ ] **SHELL-30** Update check once a day at start (release package only).
- [ ] **SHELL-31** The phone setup page closes when the viewer leaves the screen that opened it.
- [ ] **SHELL-32** Sohva Sport is polled only while its screen or the score ticker is visible and the app is in front.
- [x] **SHELL-33** One focus language across the app: the focused control fills with off-white and its content inverts; artwork gets a ring instead; nothing is outlined at rest.
- [x] **SHELL-34** Landscape only; the picture-in-picture resize never restarts the activity.

## Home

Spec: [specs/02-home.md](../specs/02-home.md) · Milestone: M5 · 36 items

**Structure**

- [x] **HOME-01** A fixed hero band (46 % of the screen height) over rows that scroll beneath it; the band never changes height, so rows never shift when the hero text changes.
- [x] **HOME-02** Header: the "Sohva TV" brand at top left and a clock (weekday, date, time) at top right, in the interface language, the chosen time zone and the device's 12/24-hour setting, updated every minute.
- [x] **HOME-03** Rows in a fixed order: Continue watching, Watch next, Today's sport, Recommended for you, Recently watched channels. A row with nothing to show is not drawn.
- [x] **HOME-04** Vertical movement pulls the focused card's row up to the top of the row area (one focus line); horizontal movement keeps the TV pivot inside a row.
- [x] **HOME-05** Structure lock: while the viewer is below the first row, arriving data updates the cards already shown in place but never adds, removes or reorders cards or rows.
- [x] **HOME-06** The navigation rail over the left edge (spec 01 §4.8).

**Continue watching**

- [x] **HOME-07** Library films paused locally (not finished, position > 0), newest first.
- [x] **HOME-08** Library episodes paused locally; one card per series, the newest episode standing for the series.
- [ ] **HOME-09** Library copies paused on Trakt (unrestricted profiles with Trakt), merged with the local positions; a newer local position of the same copy wins.
- [ ] **HOME-10** Discover titles paused in Discover (not completed, position > 0), one card per addon title (series: newest episode).
- [ ] **HOME-11** The same film from several providers, or from the library and Discover, shows once (matched by TMDB/IMDb ids); the copy actually watched locally stands. *(library part done in M5; Discover part in M9.)*
- [x] **HOME-12** At most 12 cards, newest first.
- [x] **HOME-13** Landscape card: artwork (or initials), progress bar, title, "episode label or year · N min left".
- [x] **HOME-14** OK on a library card resumes playback at the saved position, with the title's library page and details page placed underneath the player.
- [ ] **HOME-15** OK on a Discover card opens that Discover title page.
- [x] **HOME-16** Hold OK on a library card: actions dialog with Resume, Start from beginning, Mark as watched, Remove from Continue watching.
- [x] **HOME-17** Row hint "Browse the rows with the D-pad" beside the row title.
- [x] **HOME-18** Loading card "Loading Continue Watching…" while the first read is under way; Down is held back until it resolves.
- [x] **HOME-19** Failure card "Continue Watching unavailable. Select to retry." after a read failed or took longer than 5 s; OK retries.

**Trakt rows**

- [ ] **HOME-20** Watch next: landscape cards for the next episode of up to 8 recently watched shows ("S1 E2 · title").
- [ ] **HOME-21** Recommended for you: poster cards, up to 20 (10 films and 10 shows interleaved), with the year.
- [ ] **HOME-22** OK on a Trakt card opens the library's own details page when a matching copy exists, otherwise the Trakt title page.
- [ ] **HOME-23** "Trakt history is waiting for its first sync" note at the top centre after connecting, until the first sync stores its stamp.

**Sport and channels**

- [ ] **HOME-24** Today's sport: up to 6 match cards (status or kick-off, crests, score, competition) with the day's total "N matches" as the row hint.
- [ ] **HOME-25** OK on a match card opens Sohva Sport with that match's card open.
- [x] **HOME-26** Recently watched channels: up to 6 channel cards (logo, name, current programme, programme progress), most recent first.
- [x] **HOME-27** OK on a channel card plays it live; Back from the player goes to the guide on that channel.

**Hero**

- [x] **HOME-28** The hero describes the focused card once focus has rested for 180 ms.
- [x] **HOME-29** With nothing focused, or the rail focused, the hero shows the idle subject at once: the newest Continue watching title with progress, else a recent channel, else Welcome.
- [x] **HOME-30** Kicker line per subject (Continue watching / Live now with a red dot / Live TV / Today's sport / Watch next / Recommended for you / Welcome), a large title, a facts line, an optional progress bar and an optional two-line synopsis.
- [ ] **HOME-31** Synopsis and backdrop come from the same sources as the details pages (metadata match first, provider text second); a Trakt title's text comes from TMDB in the metadata language, never English first. *(library and channel part done in M5; Trakt part in M10.)*
- [x] **HOME-32** Backdrop artwork at the top right, fading into the ground at its bottom and left edge, over the bundled Live TV artwork; a match shows its two crests large and faint instead.
- [x] **HOME-33** The backdrop crossfades (250 ms) when the subject's artwork changes; the hero text changes without animation.

**Empty and first run**

- [x] **HOME-34** With every row empty, the Welcome hero ("Live TV", "Channels and programme guide") and a "Guide" button, which takes focus and opens the programme guide.

**Profiles**

- [x] **HOME-35** Every row is the active profile's own; switching profile resets Home to that profile's rows, focus and hero.
- [x] **HOME-36** A restricted profile sees no Trakt rows, no Discover cards and no first-sync note; its recent channels are limited to its allowed groups. *(done in M6; Trakt rows and Discover cards do not exist yet, their checks join in M9 and M10.)*

## Search

Spec: [specs/03-search.md](../specs/03-search.md) · Milestone: M5 · 19 items

- [x] **SEARCH-01** Search destination on the Home rail (`home_search` "Search").
- [x] **SEARCH-02** Header: brand, title "Search", subtitle "Channels, programmes, movies, series, episodes and sports", and a Back button.
- [x] **SEARCH-03** One text field, focused on entry; typing through the TV's keyboard; at most 80 characters.
- [x] **SEARCH-04** A search runs when the trimmed text has at least 2 characters, 250 ms after the last change; a new change abandons the previous search.
- [x] **SEARCH-05** Channel results: channels whose shown name contains the text.
- [x] **SEARCH-06** Programme results: programmes whose title or subtitle contains the text, on any imported date, with channel name and start time.
- [x] **SEARCH-07** Film results (up to 40), series results (up to 40), episode results (up to 40, matching the episode or the series name).
- [ ] **SEARCH-08** Sport results: today's matches whose home team, away team or competition contains the text.
- [x] **SEARCH-09** Matching ignores letter case.
- [x] **SEARCH-10** Result kinds load in parallel and each is appended when it is ready; rows already on screen never move.
- [x] **SEARCH-11** Each result row: thumbnail (logo, poster or the kind's initial), title, subtitle, kind label (CHANNEL, PROGRAMME, MOVIE, SERIES, EPISODE, SPORT).
- [x] **SEARCH-12** Status line: hint, "Searching…", "N results", "No results found." or "Some search results could not be loaded."
- [x] **SEARCH-13** OK on a channel or programme result plays the channel live; Back from the player goes to the guide on that channel.
- [x] **SEARCH-14** OK on a film opens its details page; on a series opens its series page.
- [x] **SEARCH-15** OK on an episode plays it from its saved position; when it ends the next episode plays (autoplay on) or the series page opens.
- [ ] **SEARCH-16** OK on a sport result opens Sohva Sport.
- [x] **SEARCH-17** Only enabled sources, their active snapshots and what the device's organisation rules show (hidden channels, groups and titles never appear).
- [x] **SEARCH-18** A restricted profile finds only channels, programmes, films and series in its allowed groups.
- [x] **SEARCH-19** Leaving Search forgets the text and the results.

## Profiles and parental controls

Spec: [specs/04-profiles-parental.md](../specs/04-profiles-parental.md) · Milestone: M6 · 23 items

**Profiles**

- [x] **PROF-01** The implicit first profile "Everyone" (`profile_default_name`) always exists and cannot be removed.
- [x] **PROF-02** Add a profile in Settings › General › Profiles: a name (1–24 characters) and Add profile; up to 6 profiles in total.
- [x] **PROF-03** Each new profile gets the next of six avatar colours automatically.
- [x] **PROF-04** Remove a profile (any but the first) from a picker, immediately, with everything it kept.
- [x] **PROF-05** "Who is watching" row in Settings shows the active profile and opens a picker to switch.
- [x] **PROF-06** "Ask who is watching at start" switch (default on), shown once there is more than one profile.
- [x] **PROF-07** Who is watching at start: a full-screen picker of large tiles, the last active profile focused, when the household has more than one profile and the switch is on.
- [x] **PROF-08** Who is watching on the Home rail (only with two or more profiles), opening the same picker.
- [ ] **PROF-09** Per profile: favourite channels, favourite matches, recent channels, last channel, locked channels, watched positions, allowed groups, Trakt account and its cache, Discover addons, catalog order and visibility, Library and progress. *(channels, locks, groups, positions and the library done in M6; favourite matches M8, Discover M9, Trakt M10.)*

**What this profile may see**

- [x] **PROF-10** "What this profile may see": choose the profile to limit, then Live TV groups, Film groups and Series groups, each "Everything" or "N groups".
- [x] **PROF-11** Multi-choice group pickers ("Groups for <name>"), one row per group across sources, with the sources named; changes apply at once.
- [x] **PROF-12** A restricted profile sees only its groups in the guide, the player's channel list, Movies, Series, Search and Home's recent channels.
- [x] **PROF-13** Playing, catching up or zapping to a channel outside the groups is refused with the toast "This profile cannot watch that channel".
- [ ] **PROF-14** A restricted profile never sees Discover (no rail item, no Continue watching cards, direct access denied) or Trakt (no Accounts section, no Trakt rows, no first-sync note). *(the rail's Discover item and the Accounts section's place done in M6; Discover (M9) and Trakt (M10) add their own checks.)*
- [x] **PROF-15** A note under the group rows says whether the PIN guards the restriction (red when no PIN is set).

**Parental PIN**

- [x] **PROF-16** Set a household PIN of 4–8 digits in Settings › Parental controls; stored encrypted.
- [x] **PROF-17** Remove (or change) the PIN by entering the current one; removing it unlocks every locked channel of every profile.
- [x] **PROF-18** Lock or unlock a channel for the active profile in Channel management (only with a PIN set).
- [x] **PROF-19** A locked channel asks for the PIN before live playback, catch-up, zapping and a last-channel start.
- [x] **PROF-20** With a restricted profile and a PIN in the household, entering any unrestricted profile asks for the PIN (at start, from the rail, from Settings); entering a restricted profile never does.
- [x] **PROF-21** A restricted profile opens Settings only past the PIN.
- [x] **PROF-22** PIN screen: title, what is locked, prompt, masked numeric field, Back and Unlock; a wrong PIN clears the field and says "Incorrect PIN code"; unlimited retries.
- [x] **PROF-23** Profiles, their sets and the PIN travel in the encrypted `.smbak` backup. *(M7, with the backup.)*

## Sources and import

Spec: [specs/10-sources-and-import.md](../specs/10-sources-and-import.md) · Milestone: M1 · 42 items

**Source model and list**

- [x] **SRC-01** Any number of sources (up to 100), each an M3U playlist (+ optional XMLTV guide) or an Xtream Codes account.
- [x] **SRC-02** Settings › Playlists lists every source: name, "M3U"/"Xtream", the last failure reason when a refresh failed, and "In use"/"Off".
- [x] **SRC-03** Empty list message "No playlists yet. Add one below."
- [x] **SRC-04** "+ Add M3U source" and "+ Add Xtream source" open a blank source page with a default name ("IPTV n" / "Xtream n").
- [x] **SRC-05** "Set up from a phone" button on the list (flow in [Phone setup](../specs/11-phone-setup.md)).
- [x] **SRC-06** A source page per source, left with "All playlists" or Back; focus returns to that source's row.

**Source page**

- [x] **SRC-07** M3U playlist address field and, when live TV is imported, an optional XMLTV guide address field.
- [x] **SRC-08** Xtream server field with username and masked password side by side.
- [x] **SRC-09** Source name (1–100 characters).
- [x] **SRC-10** "Source in use" switch: a switched-off source disappears everywhere and is not refreshed, but keeps its data.
- [x] **SRC-11** Connection limit 1–16 (default 1); playback beyond it is refused with a message naming the source.
- [x] **SRC-12** Content to import: Live TV / VOD only / TV and VOD (default TV and VOD).
- [x] **SRC-13** EPG time correction from −12 h to +12 h in 30-minute steps (live-TV sources only).
- [x] **SRC-14** Remove source: deletes the source, its credentials and every row that came from it.
- [x] **SRC-15** Save securely: the configuration is stored encrypted on the device.
- [x] **SRC-16** A newly saved source is synced in the background straight away (channels, guide, films and series).
- [x] **SRC-17** Test address (M3U): reads the start of the playlist and says how many entries it found, or why it could not.
- [x] **SRC-18** Test connection (Xtream): signs in and reports success with the server's connection limit, or why it failed.
- [x] **SRC-19** Sync everything: queues channels, then guide, then films and series for this source in the background.
- [x] **SRC-20** Refresh playlist (M3U) / Refresh channels (Xtream), with the imported channel count.
- [x] **SRC-21** Refresh movies and series, with the imported film and series counts.
- [x] **SRC-22** Refresh programme guide, with the imported programme count.
- [x] **SRC-23** Plain-language validation and failure messages in the interface language; provider detail kept but addresses and credentials redacted.
- [x] **SRC-24** Status line with per-kind health ("Playlist: 56,164 items", "Programme guide: updating", the failure reason), updating live while background work runs.
- [x] **SRC-25** Security note under the status: credentials are encrypted and backups password-protected.

**Refresh and scheduling**

- [x] **SRC-26** Playlist and EPG refresh interval in Settings › General: 1, 2, 4, 10 or 24 hours (default 24 hours).
- [x] **SRC-27** Automatic background refresh: playlists and guides on that interval, films and series every 24 hours, whenever a network is connected.
- [x] **SRC-28** Automatic refresh waits while the app is in use, except a source's very first import.
- [x] **SRC-29** One import of a kind per source at a time; a second request waits and then runs in full.
- [x] **SRC-30** An M3U address of the Xtream `get.php` form is imported through the Xtream API (channels, films, series, episodes; the guide too when no XMLTV address is given).

**Import results the viewer sees**

- [x] **SRC-31** A refresh never replaces working data with nothing: an empty playlist, a web page or error text instead of M3U, an empty guide, or a guide matching none of the channels keeps the previous data and says so.
- [x] **SRC-32** A failed refresh keeps the previous data; the failure reason shows in the status, the source row and the guide's empty state.
- [x] **SRC-33** The guide keeps only programmes of the source's own channels, from 12 hours back to 8 days ahead (rebuild: past kept only for catch-up channels, SRC-FR-83).
- [x] **SRC-34** M3U entries bring names, groups, logos, EPG ids, channel numbers, catch-up settings, per-channel user agent and referrer; live, film and series entries are told apart; series episodes are recognised from "S01E02"/"1x02" names.
- [x] **SRC-35** XMLTV feeds may be gzip-compressed, start with a byte-order mark, use any declared encoding or UTF-16; a malformed programme is skipped, not fatal.
- [x] **SRC-36** Xtream brings live channels with categories, EPG ids, logos, numbers and catch-up archive length; films and series with categories, poster, backdrop, year, rating and plot; episodes are fetched when a series is opened.
- [x] **SRC-37** Stream quality and language markers are read off channel names (4K, FHD, HDR, 50 FPS, FI…) for the guide, player and Sohva Sport.
- [x] **SRC-38** The EPG time correction shifts every programme of the source on screen, immediately after saving.
- [x] **SRC-39** Sources are part of the encrypted backup ([Backup](../specs/71-backup-restore.md)).

**Rebuild additions (owner-adopted ideas, [OwnTV study](../reference/owntv-study.md) items 2–11, 22)**

- [x] **SRC-40** Every import, from any screen or worker, goes through one import runner and survives leaving the screen that started it; the screen shows its progress.
- [x] **SRC-41** Xtream bulk lists that a provider truncates or refuses are fetched category by category instead.
- [ ] **SRC-42** Importing never makes playback stutter or a D-pad press late on the low-end box.

## Phone setup

Spec: [specs/11-phone-setup.md](../specs/11-phone-setup.md) · Milestone: M1 · 14 items

- [x] **PHONE-01** "Set up from a phone" button in Settings › Playlists starts the setup page and opens the QR dialog; while it runs the button reads "Close the phone page".
- [x] **PHONE-02** QR dialog: title, QR code on a white square, help, the page address in bold, a privacy note, "Close the phone page" (focused).
- [x] **PHONE-03** No network address: the dialog says so in red instead of showing a code.
- [x] **PHONE-04** Setup page on the phone, in the TV's interface language: an Xtream account form, an M3U playlist form and an Optional keys form (TMDB token, API-Sports key).
- [x] **PHONE-05** A source sent from the phone is validated with the Settings rules, saved encrypted, and synced at once.
- [x] **PHONE-06** Keys sent from the phone are saved encrypted; a TMDB key also switches TMDB on.
- [x] **PHONE-07** The phone page answers every post with a result sentence (saved, keys saved, something missing, could not save).
- [x] **PHONE-08** The TV dialog shows "Received from the phone: <name>. Syncing it now."; the playlist list refreshes and its status line reports the receipt.
- [x] **PHONE-09** Several sources and keys can be sent in one session.
- [ ] **PHONE-10** "Logo from phone" in Channel management: a page with one picture chooser; the phone shrinks the picture to at most 512 px and sends it; the TV stores it as that channel's logo (at most 256 px) and closes the page.
- [ ] **PHONE-11** Addon URLs from a phone (Discover › Import): a one-use, ten-minute session; paste URLs or choose a `.txt` file on the phone; nothing installs until confirmed on the TV.
- [x] **PHONE-12** A request without the right token is refused with a page that says to scan the code again.
- [x] **PHONE-13** The page closes when its dialog closes (button or Back), when the TV leaves the screen that opened it, and after 15 minutes (sources/logo) or 10 minutes (addons).
- [x] **PHONE-14** Only the TV's own local IPv4 address is served; nothing is posted anywhere else; nothing posted is logged.

## Live TV guide

Spec: [specs/20-live-tv-guide.md](../specs/20-live-tv-guide.md) · Milestone: M2 · 52 items

- [x] **GUIDE-01** Guide screen: hero, day label and half-hour ruler, channel column, 3-hour programme grid, key-hint bar (anatomy in guide.md §0–§3).
- [x] **GUIDE-02** Opens on the group of the channel it was opened for; otherwise on the first group of the chosen source; never on All channels by default.
- [x] **GUIDE-03** The chosen source is remembered across launches; fallback order: channel opened for, saved source, last watched channel's source, first source.
- [x] **GUIDE-04** Options > Source cycles through the sources; a switch lands on the new source's first group and its first channel; with one source the button does nothing.
- [x] **GUIDE-05** Group rail opened with Left from the channel column: Favourites, All channels, Recently watched, custom channel lists, provider groups, each with a count where known.
- [x] **GUIDE-06** The rail follows the viewer's manual group order when one is set (All channels always first).
- [x] **GUIDE-07** The Favourites, Recently watched and custom-list entries disappear when switched off in the Library manager.
- [ ] **GUIDE-08** Groups switched off by a rule, or outside a restricted profile's groups, are not on the rail and their channels are never shown.
- [x] **GUIDE-09** All channels: every visible channel of the source in guide order.
- [x] **GUIDE-10** Favourites: the active profile's favourite channels of the selected source, in guide order.
- [x] **GUIDE-11** Recently watched: the active profile's last 20 channels of the selected source, most recent first.
- [x] **GUIDE-12** Custom list: the list's channels of the selected source, in the list's own order.
- [x] **GUIDE-13** Channel order within a list follows the organisation rules (provider order by default; manual or A–Z per group from the Library manager; positions set in Channel management).
- [x] **GUIDE-14** Channel cell: number, logo (or two-letter initials), name (wraps to two lines when long), feed line (quality/language tags from the name, else group, else source).
- [ ] **GUIDE-15** Channel numbers can be switched off (Settings > General > Channel numbers).
- [x] **GUIDE-16** Programme blocks proportional to running time, clipped to the window and to the next programme's start; genre accent bar; airing block with progress strip; finished blocks dimmed.
- [x] **GUIDE-17** A row without listings in the window shows "No EPG information / Watch channel"; before its programmes are read it shows a blank disabled bar.
- [x] **GUIDE-18** Day label ("Thu 24.9.") with Now / Today / Tomorrow / Yesterday.
- [x] **GUIDE-19** One red now-line across the grid and ruler, moved every minute.
- [x] **GUIDE-20** Hero: 16:9 still (backdrop, poster, or channel logo), channel and number caption, live dot and progress line; title, LIVE chip, time range, first category, year, TMDB rating chip, synopsis.
- [x] **GUIDE-21** Hero actions: Watch, Favourite, Remind me / Reminder set, Watch from start / Watch recording, Find programme / Close search, Source: TMDB (or TVmaze).
- [x] **GUIDE-22** Programme metadata (TMDB or TVmaze) looked up 350 ms after the selection settles, only when a metadata service is enabled.
- [x] **GUIDE-23** Right on the last block of a row pages +90 min; Left on the first block pages −90 min unless the window is at now.
- [x] **GUIDE-24** Fast forward / Rewind page ±90 min; Next / Previous page ±1 day.
- [x] **GUIDE-25** Paging reaches one day back and seven days ahead; a paged window stays where it is while the clock moves; the window at now follows the clock.
- [x] **GUIDE-26** Held Left/Right pages continuously; focus waits on the channel during a slow read and lands on the adjacent programme when it arrives.
- [x] **GUIDE-27** OK on a channel plays it live; OK on an airing or past programme plays catch-up from its start when the channel supports it, otherwise live; OK on a future programme opens its actions.
- [x] **GUIDE-28** OK held on any programme opens its actions once.
- [x] **GUIDE-29** Programme actions dialog: Watch / Watch the channel now, Watch from start / Watch recording, Remind me / Reminder set, Favourite / Add favourite.
- [x] **GUIDE-30** Favourite toggles from the hero and the actions dialog (per profile).
- [x] **GUIDE-31** Reminders for future programmes (details in [Catch-up and reminders](../specs/22-catchup-and-reminders.md)).
- [x] **GUIDE-32** Number dialling: overlay "Channel 12", up to 4 digits, commits 2 s after the last digit, "No channel 12" for 1.5 s; own numbers first, then list positions; focus moves to the channel.
- [x] **GUIDE-33** Find programme: a search field on the rail filters the rows by channel name or by a programme title in the three hours shown.
- [x] **GUIDE-34** Options sheet (Menu, or Options on the rail): Source, Sort, Edit (groups), Edit (channels), Settings, Back, Close.
- [x] **GUIDE-35** Sort and Edit (groups) open the Library manager at the current group and source; back from it the guide reopens with the options open on that group.
- [x] **GUIDE-36** Edit (channels) opens [Channel management](../specs/21-channel-management.md).
- [x] **GUIDE-37** Key-hint bar listing only working bindings.
- [x] **GUIDE-38** "Reading the guide…" while the first rows are read.
- [x] **GUIDE-39** The previous rows stay on screen while another list is read; a reading notice appears above them after 400 ms.
- [x] **GUIDE-40** Empty-list messages for Favourites, Recently watched and any other list.
- [x] **GUIDE-41** Empty-library card with per-source import health, Sync now and Open settings.
- [x] **GUIDE-42** Back from live playback started in the guide returns to the guide at now, on the watched channel in its group.
- [x] **GUIDE-43** Returning to the guide within 10 minutes shows the last rows without reading them again when nothing changed.
- [x] **GUIDE-44** Up from the top row reaches the hero's buttons.
- [x] **GUIDE-45** Programme data arriving never moves focus.
- [ ] **GUIDE-46** Times in the chosen time zone (Settings > General > Time zone; the TV's zone until one is chosen).
- [x] **GUIDE-47** A source's EPG offset (±12 h in 30-min steps) shifts all its programme times.
- [x] **GUIDE-48** Duplicate and overlapping provider entries are reduced to one block per start time.
- [ ] **GUIDE-49** A restricted profile sees only its allowed groups; a change applies while the guide is open.
- [x] **GUIDE-50** An import finishing while the guide is open refreshes rows and programmes in place.
- [x] **GUIDE-51** Named trace sections for the screen, grid, rows and hero.
- [x] **GUIDE-52** One accessibility node per cell (per row in the rebuild) that states its text.

## Channel management

Spec: [specs/21-channel-management.md](../specs/21-channel-management.md) · Milestone: M3 · 30 items

- [x] **CHAN-01** Channel management screen, opened from the guide's Options > "Edit" (Channels icon) and from the Library manager's Live room > "Advanced" (the Library manager itself is reached from Settings > Library > "Manage groups & content").
- [x] **CHAN-02** Header: "Channel management", subtitle, Back button.
- [x] **CHAN-03** Source filter button "Source: All" / "Source: <name>", cycling All → each source → All.
- [x] **CHAN-04** Sort button "Sort: Playlist" / "Sort: A–Z".
- [x] **CHAN-05** "Show hidden" toggle; the choice is kept and shared with the Library manager (default on).
- [x] **CHAN-06** Search field "Search channels or groups" (name or group contains the text).
- [x] **CHAN-07** Group chips: All and every group of the chosen source; the chosen chip is marked "●".
- [x] **CHAN-08** Create a custom channel list: name field and "Create list".
- [x] **CHAN-09** Channel list with a count ("N channels"): number, logo or initials, name, group (or source), "HIDDEN" marker.
- [x] **CHAN-10** Moving focus onto a channel opens it in the editor pane: logo, name, source, original name, status message.
- [x] **CHAN-11** Custom name (blank = the playlist's name).
- [x] **CHAN-12** Custom group (blank = the playlist's group); the channel moves to that group everywhere.
- [x] **CHAN-13** Own logo by address (blank = the playlist's logo).
- [x] **CHAN-14** Own logo from a phone: a QR page on the phone sends a picture, kept on the TV.
- [x] **CHAN-15** Own channel number (digits, blank = the playlist's), with the playlist's number shown beside it.
- [x] **CHAN-16** Programme guide mapping: Automatic (the playlist's `tvg-id`) or a chosen channel of the source's XMLTV feed.
- [x] **CHAN-17** Custom channel lists: pick a list, Add to list / Remove from list, Delete list.
- [x] **CHAN-18** Save stores name, group, logo, number and guide mapping together.
- [x] **CHAN-19** Hide from guide / Show in guide (immediate).
- [x] **CHAN-20** Lock with PIN / Remove PIN lock (immediate, per profile); without a parental PIN the button reads "Configure PIN in Settings" and is disabled.
- [x] **CHAN-21** Move up (↑) / Move down (↓), only while sorted by Playlist.
- [x] **CHAN-22** Reset removes the channel's customisation.
- [x] **CHAN-23** A status line confirms every action.
- [x] **CHAN-24** Favourite channels per profile, toggled from the guide's hero and programme actions; the guide rail's Favourites list.
- [ ] **CHAN-25** Recently watched channels per profile (last 20, most recent first): the guide rail's Recently watched list and Home's recent channels row.
- [x] **CHAN-26** A locked channel asks for the PIN before live, catch-up and zapped playback and at a Last-channel start.
- [ ] **CHAN-27** Customisations apply in the guide, the player's channel list and dial, Home, Search, Sohva Sport stream matching and the stored programme guide.
- [x] **CHAN-28** Custom lists appear on the guide rail (each can be switched off in the Library manager).
- [x] **CHAN-29** Backups carry channel customisations, lists, memberships, phone-sent logos (as picture bytes) and each profile's favourites, recents and (with a PIN) locks.
- [x] **CHAN-30** Removing a source removes its channels' customisations and list memberships.

## Catch-up and reminders

Spec: [specs/22-catchup-and-reminders.md](../specs/22-catchup-and-reminders.md) · Milestone: M3 · 27 items

**Catch-up**

- [x] **CATCH-01** Catch-up is offered only for channels whose playlist declares it (M3U `catchup` / `catchup-type` / `timeshift` attributes; Xtream `tv_archive`) and only for programmes that started no longer ago than the channel's catch-up days.
- [x] **CATCH-02** "Watch from start" for the programme on air (guide hero and programme actions).
- [x] **CATCH-03** "Watch recording" for a finished programme (guide hero and programme actions).
- [x] **CATCH-04** OK on an airing or past programme of a catch-up channel in the guide plays it from its start.
- [x] **CATCH-05** Archive address schemes: `default`, `vod`, `append`, `shift`, `timeshift`, `xtream`, `xc`.
- [x] **CATCH-06** Template tokens: `{utc}` `{start}` `{utcend}` `{end}` `{lutc}` `{now}` `{timestamp}` (each also with a `:format`), `{duration}` and `{duration:N}`, `{offset:N}`, `{Y}` `{m}` `{d}` `{H}` `{M}` `{S}`, with an optional leading `$`.
- [x] **CATCH-07** Xtream archive address built from the live address: `/timeshift/<user>/<pass>/ <minutes>/<yyyy-MM-dd:HH-mm>/<stream>.ts`.
- [x] **CATCH-08** Formatted times use the channel's catch-up time zone (an Xtream panel's server zone), else the TV's zone.
- [x] **CATCH-09** Unsafe or unusable templates are refused (non-http(s) result, `{catchup-id}`, unknown tokens, bad formats).
- [x] **CATCH-10** Catch-up playback shows transport controls (pause, seek) and no channel up/down; its title reads "Arkisto · <channel>".
- [x] **CATCH-11** Back from catch-up playback returns to the screen it was started from.
- [ ] **CATCH-12** Catch-up respects the PIN lock, profile restrictions and the source's connection limit, and records the channel as recently watched.

**Reminders**

- [x] **REM-01** "Remind me" / "Reminder set" on a guide programme that has not started (hero and programme actions).
- [ ] **REM-02** "Remind me" / "Reminder set" on a Sohva Sport match card for a scheduled match that has not started.
- [x] **REM-03** A reminder fires one minute before its start, by an exact alarm, whether the app is running or not.
- [x] **REM-04** In-app alert over any screen: "<title> starts in a minute" / "starts now", subtitle, Watch (or "Open the match card") and Not now; goes away by itself 2 minutes after the start (at least 20 s after it appeared).
- [x] **REM-05** Several due reminders queue; one alert at a time.
- [ ] **REM-06** Watch plays the programme's channel (or opens the match card when the match has no known channel); Back returns to where the viewer was.
- [x] **REM-07** A notification in the TV's panel ("<title> starts now", "Press to watch." / "Press to open the match card and choose a channel.") that opens the channel or the match card.
- [ ] **REM-08** When another app is on screen, Sohva TV comes to the front with the alert if the viewer allowed "display over other apps".
- [x] **REM-09** The first reminder ever set explains once how reminders can open the app and offers to open the TV's setting.
- [x] **REM-10** Android 13+: the notification permission is asked when a reminder is set.
- [x] **REM-11** Settings > General shows "Reminders can open Sohva TV: Allowed / Not allowed" and opens the TV's setting.
- [x] **REM-12** Reminders survive leaving the app, a restart of the app, an update and a reboot.
- [x] **REM-13** A reminder missed by more than 30 minutes (TV off) is dropped, not fired late; a fired reminder is removed.
- [x] **REM-14** Reminders stay on the TV: not in backups, nothing sent anywhere.
- [x] **REM-15** No reminders in the Lab build.

## Player: live, catch-up and VOD playback

Spec: [specs/30-player.md](../specs/30-player.md) · Milestone: M2 · 47 items

- [ ] **PLAY-01** Live channel playback full screen, opened from the guide, Home, Search, Sohva Sport, a reminder or the start screen.
- [x] **PLAY-02** Catch-up playback of a past programme ("Watch from start") with transport controls.
- [x] **PLAY-03** Film and episode (VOD) playback from a resume position.
- [x] **PLAY-04** Black video ground and letterbox bars in every colour theme.
- [x] **PLAY-05** "Connecting to playback service…" screen, and a full-screen message with Back when the service fails.
- [x] **PLAY-06** Live information box: channel logo or initials, name, LIVE tag, stream tags, group, programme title, start time, "% watched", time left, stop time, progress bar, next programme, stream name when it differs.
- [x] **PLAY-07** Live information box hides after 5 s idle, stays while its buttons have focus, and does not reappear by itself when the programme changes or the guide refreshes.
- [x] **PLAY-08** Live action row: hide controls, picture shape, audio, subtitles, channel list, stream details, quick actions, open in another player.
- [x] **PLAY-09** Transport controls for catch-up and VOD: title, Back, picture, audio, subtitles, progress bar, position / duration, rewind, play/pause, forward (buttons name the skip step).
- [x] **PLAY-10** Transport controls hide after 5 s idle unless focused or a track picker is open.
- [x] **PLAY-11** Channel list down the right edge: the playing channel's group, numbers (setting), logos, what is on now.
- [x] **PLAY-12** Group list beside the channel list with channel counts; browsing another group is temporary until a channel is tuned.
- [x] **PLAY-13** Channel up / down within the playing channel's group, wrapping at the ends.
- [x] **PLAY-14** Switch to the previous channel (zap-back), and back again.
- [x] **PLAY-15** Dial a channel by number on live TV, with "Channel 12" read-out and "No channel 12".
- [ ] **PLAY-16** Skip back / forward by the chosen step (10 s, 30 s, 1 min, 2 min); quick repeated presses climb to 2 min (held keys too in the Discover player); the size of each skip shows for a moment.
- [x] **PLAY-17** Audio track picker; step to the next audio track.
- [x] **PLAY-18** Subtitle picker with Off; subtitles on / off toggle.
- [ ] **PLAY-19** VOD audio and subtitle language preferences (primary, secondary); subtitles stay off when the primary audio language is present.
- [x] **PLAY-20** Quick actions menu (hold OK, or Menu): audio, subtitles, picture shape, playback info, score ticker.
- [x] **PLAY-21** Picture shape: Fit, Fill, Zoom.
- [x] **PLAY-22** Playback info line (resolution and frame rate, codecs, bitrate, subtitle format, buffer, dropped frames) with a clock.
- [x] **PLAY-23** Buffering indicator whenever the player buffers.
- [x] **PLAY-24** Automatic reconnection (Standard: 3 tries; Persistent: 8 tries) with a banner naming the cause and the attempt, and a Reconnect button.
- [x] **PLAY-25** Failure causes in plain words (rebuild improvement over beta 23's code-plus-exception text; see PLAY-FR-93).
- [ ] **PLAY-26** Playback buffer profile: Media3 default, Low latency, Stability; applies to the next playback.
- [ ] **PLAY-27** Subtitle size, colour and background, each "Follow the TV" by default.
- [ ] **PLAY-28** Match the display refresh rate to the stream, and restore it afterwards.
- [x] **PLAY-29** Keep watching in a corner (picture in picture) on Home, with a Close button.
- [x] **PLAY-30** Open the live stream in another player app on the TV.
- [ ] **PLAY-31** Sohva Sport score ticker over live and catch-up playback.
- [x] **PLAY-32** Leaving the app stops the stream; returning resumes it.
- [x] **PLAY-33** The screen never sleeps while the player is open.
- [x] **PLAY-34** VOD progress saved every 10 s, on pause, at the end and on leaving; watched rule applied.
- [x] **PLAY-35** A finished film returns to its details; a finished episode continues to the next one (setting) or returns to the series.
- [x] **PLAY-36** Per-source connection limit enforced before a stream opens.
- [x] **PLAY-37** Provider headers (User-Agent, Referer) sent with the stream; Sohva's own user agent otherwise.
- [x] **PLAY-38** HLS, DASH and progressive (MPEG-TS, MP4) streams, container chosen from the address.
- [x] **PLAY-39** Hardware decoding preferred, next decoder tried if one fails.
- [ ] **PLAY-40** A locked channel asks for the parental PIN when zapped to; a channel outside the profile's groups is refused.
- [x] **PLAY-41** Back removes one layer at a time; from the bare picture it leaves to the guide on the channel just watched.
- [x] **PLAY-42** Remote shortcuts out of the player: guide at this channel, Home, Guide, Sohva Sport.
- [x] **PLAY-43** Playback published as a media session (system media controls, voice "pause").
- [ ] **PLAY-44** Demo build shows a still picture instead of a stream.
- [x] **PLAY-45** Playback failures written to the diagnostics log without addresses or credentials.
- [ ] **PLAY-46** Trakt scrobbling of VOD playback ([Trakt](../specs/51-trakt.md)).
- [ ] **PLAY-47** Discover player shares the transport controls, track picker, skip ladder and subtitle look.

## Remote button mapping

Spec: [specs/31-remote-button-mapping.md](../specs/31-remote-button-mapping.md) · Milestone: M3 · 19 items

- [x] **REMOTE-01** Twelve buttons, each with a Press slot and a Hold slot (23 mappable slots).
- [x] **REMOTE-02** A press acts on release; a hold acts on the first auto-repeat; a hold never also acts as a press.
- [x] **REMOTE-03** Twenty-five actions in five groups, plus Nothing.
- [x] **REMOTE-04** Actions marked "Live TV only" or "Catch-up and films only"; mapped where they do not apply, a press shows the player's chrome instead.
- [x] **REMOTE-05** Defaults that keep pre-mapping behaviour and add zap-back (Back hold, Left hold) and guide at this channel (Right hold).
- [x] **REMOTE-06** Back press fixed: dismiss the top layer, then leave the player.
- [x] **REMOTE-07** Back hold mappable, for remotes that pass a long Back press to the app.
- [x] **REMOTE-08** CH+, CH−, Info, Audio, Captions and Menu under "If your remote has them".
- [x] **REMOTE-09** Mapping applies only on the player's clean screen; overlays, menus and other screens keep their own keys.
- [x] **REMOTE-10** Up/Down presses step into an open information box, or into the catch-up/film controls, before the mapping is consulted.
- [x] **REMOTE-11** Digits dial a channel on live TV (not mappable).
- [x] **REMOTE-12** Keys outside the grid show the chrome and are left to Android.
- [x] **REMOTE-13** Settings grid: one row per button, Press and Hold cells naming the assigned action.
- [x] **REMOTE-14** Choosing a cell replaces the grid with the grouped action list; the current action is marked and focused; scope suffixes on restricted actions.
- [x] **REMOTE-15** A read-back line names the focused cell and its action.
- [x] **REMOTE-16** Reset to defaults with a confirmation step.
- [x] **REMOTE-17** Focus returns to the edited cell after a choice or Back.
- [x] **REMOTE-18** Mappings kept on the device for all profiles, tolerant of unknown entries, included in backups.
- [ ] **REMOTE-19** One-time migration from the old "Remote channel browser" setting.

## Movies and series

Spec: [specs/40-movies-and-series.md](../specs/40-movies-and-series.md) · Milestone: M4 · 58 items

**Walls**

- [x] **VOD-01** Two poster walls, Movies and Series, each with a header, a left rail and a poster grid.
- [x] **VOD-02** Header: "Movies"/"Series", the destination's name and title count, a "Loading library…" or failure note while an older wall is still shown, and the last refresh result.
- [x] **VOD-03** History destination: titles this profile played (finished ones included), newest first.
- [x] **VOD-04** Groups view: the provider's groups with film counts, in the library's group order.
- [x] **VOD-05** Genres view: the viewer's own groups, then each of 22 genres that has titles, then Unsorted, with counts.
- [x] **VOD-06** Groups / Genres toggle that returns to the last destination used in each view.
- [x] **VOD-07** Custom groups (genres, year range, minimum rating) as wall destinations.
- [x] **VOD-08** An "all groups" wall when every provider group is hidden.
- [x] **VOD-09** Poster grid with adaptive columns (6 at the default interface size), no sort bar or letter index.
- [x] **VOD-10** Poster card: 2:3 poster, one-line title, "year · rating".
- [x] **VOD-11** Poster fallback: provider poster, then the matched TMDB poster, then the title's initials; a chosen match's poster replaces the provider's.
- [x] **VOD-12** Picture-quality chips read from the provider's title (4K UHD; Dolby Vision, HDR10+ or HDR10).
- [x] **VOD-13** One card per film when several playlists, groups or qualities carry it, marked "×N".
- [x] **VOD-14** "When a film has more than one version" setting chooses the copy a folded card opens (None, Finnish audio, Finnish subtitles, Largest picture).
- [x] **VOD-15** A folded card takes a missing poster, year, rating or genre from its other copies.
- [ ] **VOD-16** Watched tick on film posters, whichever copy was watched (Trakt marks included).
- [x] **VOD-17** Search inside the selected destination by provider title or matched title.
- [x] **VOD-18** The previous wall stays visible (not selectable) until the next one is ready; loading, failure, empty and no-results messages.
- [x] **VOD-19** Wall order follows the library organisation (A–Z by default, per-group sort, manual order); History by recency.
- [x] **VOD-20** Library options sheet: Refresh, Edit (library manager), Back, Close.
- [x] **VOD-21** Refresh imports every enabled VOD source and reports "Imported N movies and M series".
- [x] **VOD-22** Left from the wall's first column returns to the selected rail row, scrolling it into view.
- [x] **VOD-23** Coming back from a title focuses the same card (or its film's standing copy) with both scroll positions kept.
- [x] **VOD-24** Each mode keeps its browse position (destination, view, scroll, focused card) while the app runs.
- [x] **VOD-25** First entry focuses History (else the first group, else Options).
- [ ] **VOD-26** A restricted profile sees only its allowed groups' titles.

**Film page**

- [x] **VOD-27** Film page: full-bleed backdrop, breadcrumb, title, facts (score, year, runtime, quality chips), synopsis.
- [x] **VOD-28** Progress line "Watched X of Y", bar and "Z left" for a partly watched film.
- [x] **VOD-29** Resume or Watch (first focus); Start from beginning when there is a position.
- [x] **VOD-30** Mark as watched / Mark as unwatched.
- [x] **VOD-31** "Source: TMDB" (or TVmaze) opens the metadata provider's page.
- [x] **VOD-32** "Wrong details?" opens the match picker.
- [x] **VOD-33** Versions row: every copy of the film with its source and what its name claims; the chosen copy plays at the film's position.
- [x] **VOD-34** Cast row with photo or initials, name and character.
- [x] **VOD-35** Similar row: TMDB's similar films that the library has, with "checking" and "none" states.
- [x] **VOD-36** Details are looked up when the page opens, and a missing library poster is repaired.

**Series page**

- [x] **VOD-37** Series page: backdrop, breadcrumb, title, facts (score, year, season count, runtime), synopsis, one-line cast.
- [x] **VOD-38** Season buttons with a tick on seasons watched to the end.
- [x] **VOD-39** Episode cards: still, "S1 E2", title, part-watched bar, watched badge, selection rule, duration.
- [x] **VOD-40** Focusing an episode selects it; the buttons, progress line and runtime follow the selection.
- [x] **VOD-41** Continue episode / Watch episode; Start from beginning; Mark as watched / unwatched for the selected episode.
- [x] **VOD-42** Mark season as watched.
- [x] **VOD-43** Episodes fetched from the provider on first open; Refresh episodes on demand.
- [x] **VOD-44** "Loading episodes…" pill; loading, error and empty messages for episodes.
- [x] **VOD-45** Wrong details? and Source for series.
- [x] **VOD-46** OK on an episode card plays that episode from its position.

**Watched state and progress**

- [x] **VOD-47** Positions saved per profile and per copy; the copies of one film share a position.
- [x] **VOD-48** Watched at 90 %, or with 3 minutes left on anything of 10 minutes or more.
- [x] **VOD-49** Continue watching feed for Home: one card per film or series, newest first.
- [x] **VOD-50** Held-OK actions on a Continue watching card: Resume, Start from beginning, Mark as watched, Remove from Continue watching.
- [x] **VOD-51** Next-episode lookup across seasons (for autoplay).
- [ ] **VOD-52** Trakt positions and watched marks overlaid on library progress.

**Match picker**

- [x] **VOD-53** "Choose the right title": searches the provider's name on open, manual search, results with artwork, year and synopsis; choose, Undo my choice, Close.

**Artwork**

- [x] **VOD-54** Artwork disk cache limit 100 / 250 / 500 MB (default 250), applied at the next start; usage and Clear in Settings.
- [x] **VOD-55** Posters decoded at wall size; bounded memory cache; at most two images decoded at once.

**Shared**

- [x] **VOD-56** Genre vocabulary of 22 genres with localized names, plus Unsorted.
- [x] **VOD-57** Title initials placeholder for missing artwork and cast photos.
- [ ] **VOD-58** Catalogue lookups used elsewhere: search (films, series, episodes), playable stream, title by key, next episode.

## Metadata enrichment

Spec: [specs/41-metadata-enrichment.md](../specs/41-metadata-enrichment.md) · Milestone: M4 · 34 items

**Settings (Library section)**

- [x] **META-01** Group "Metadata and images (optional)" with the TMDB attribution logo.
- [x] **META-02** Switch "TMDB titles, plots and artwork"; it refuses to turn on without a key ("Enter a TMDB key below first.").
- [x] **META-03** TMDB credential field (API key or Read Access Token, masked, up to 2,048 characters), encrypted on the device.
- [x] **META-04** "Save key" stores the credential and clears all metadata so everything is looked up again.
- [x] **META-05** "Test TMDB" checks the typed credential against TMDB and says whether it works.
- [x] **META-06** Switch "TVmaze series information" (no key).
- [x] **META-07** Status line under the group for every save, test and clear result.
- [x] **META-08** Metadata language: 22 languages, default Finnish for a Finnish interface and English (US) otherwise; a change redoes the library's titles and posters in the background.
- [x] **META-09** "Clear metadata cache" (Maintenance group) and buttons opening the TMDB and TVmaze websites.

**Background enrichment of the library**

- [x] **META-10** Every film and series of the active catalogues of enabled sources is looked up once: replacement title, poster (only where the provider has none), TMDB id and primary genre.
- [x] **META-11** Replacement title shown on walls, Home and Search; the TMDB poster fills a missing provider poster.
- [x] **META-12** One primary genre per title in the 22-genre vocabulary, from TMDB's genre ids (TVmaze gives none); vocabulary versioned so a change revisits every title.
- [x] **META-13** Runs only while the app is not in front (so never during playback), 30 s after leaving, in 4-minute runs, and is cancelled when the viewer returns.
- [x] **META-14** Durable queue that survives restarts, picks up imports and sweeps titles that left the catalogue.
- [x] **META-15** Provider failures retried with exponential backoff (15 min doubling to 24 h); real misses recorded as "no match" and not retried.

**On-demand lookups**

- [x] **META-16** Film page: TMDB details with runtime, rating, up to 8 cast members and up to 20 similar films, in the metadata language.
- [x] **META-17** Series page: TMDB series with details (runtime, rating, cast), TVmaze as a fallback.
- [x] **META-18** Selected episode: TMDB episode or TVmaze episode by number, 350 ms after the selection rests.
- [x] **META-19** Guide hero: programme lookup by title 350 ms after the selection rests (synopsis, year, still, "TMDB x.x" rating chip).
- [ ] **META-20** Home hero: film, series/episode and live-programme lookups; Trakt titles by TMDB id in the metadata language. *(film, series and programme lookups done in M5; Trakt part in M10.)*
- [x] **META-21** A missing library poster is repaired from the details record when a page opens.
- [x] **META-22** "Source: TMDB" / "Source: TVmaze" opens the matched record's web page (film page, series page, guide hero).

**Matching and keys**

- [x] **META-23** Provider-title cleaning before searching: language and quality prefixes, bracketed decorations, season/episode markers, trailing years and quality tags.
- [x] **META-24** Conservative automatic matching: confidence ≥ 0.92 and a 0.08 lead over the runner-up, with a popularity tie-break for exact titles.
- [x] **META-25** Film work key: `tmdb:<id>` once matched, else `name:<cleaned title>:<year>`; the identity of a film across playlists.
- [x] **META-26** Film identity pass folds matched copies together (background priority, paged).

**Fix a match**

- [x] **META-27** "Wrong details?" match picker: searches the cleaned provider name on open; manual search; results with poster, title, year and a two-line overview.
- [x] **META-28** Choosing a result pins it: it never expires and automatic matching never replaces it; the library title, poster and genre follow.
- [x] **META-29** "Undo my choice" (only when pinned) hands the title back to automatic matching.

**Caches, limits, attribution**

- [x] **META-30** Memory cache of 256 lookups plus a database cache: positive 30 days (TMDB) / 24 h (TVmaze), negative 7 days, pinned forever.
- [x] **META-31** Identical concurrent lookups share one request; leaving a screen cancels its request.
- [x] **META-32** TVmaze 429 handling (one retry after Retry-After, 1–5 s); responses capped at 2 MiB; text cut to 8,000 characters; artwork only over https.
- [x] **META-33** Localised errors for HTTP failures, oversized responses, invalid or missing key and a failed save.
- [x] **META-34** Attribution: TMDB logo in Settings, TMDB and TVmaze (CC BY-SA) notices and the metadata disclosure on the legal screen ([About](../specs/72-updates-about-diagnostics.md)).

## Library organisation

Spec: [specs/42-library-organization.md](../specs/42-library-organization.md) · Milestone: M4 · 35 items

**Rules**

- [x] **ORG-01** Three rooms (Live TV, Movies, Series) with independent rules.
- [x] **ORG-02** Show or hide a provider group, for one source or for the same-named group across all sources.
- [x] **ORG-03** Show or hide an item inside one group; its other groups are unaffected.
- [x] **ORG-04** "Hide everywhere" / "Show everywhere" for an item.
- [x] **ORG-05** Hiding a group keeps its members' own choices; showing it again restores them.
- [x] **ORG-06** Group order: original/provider, A–Z, Z–A, manual.
- [x] **ORG-07** Room default content order — Live: provider, A–Z, Z–A, manual; Movies and Series: A–Z, Z–A, newest release, oldest release, highest rating, manual. Missing year or rating sorts last.
- [x] **ORG-08** Per-group content order overriding the room default; "Use library default"; "Use default in every group…".
- [x] **ORG-09** Manual order of groups and items: Move mode, Move to top, Move to bottom, Move to position.
- [x] **ORG-10** The first manual order starts from the order on screen; switching to an automatic order and back restores the saved manual order.
- [x] **ORG-11** Shortcuts as groups: Favourites and Recently watched (Live), History (Movies, Series) can be hidden and placed.
- [x] **ORG-12** Custom channel lists appear as groups (Live): hide the list's guide entry, hide or order members inside the list only.
- [x] **ORG-13** A film carried by several playlists is one item (film identity); a choice made on it follows every copy.
- [x] **ORG-14** Items of a disabled source are shown as unavailable and no rule can enable them.
- [x] **ORG-15** Old "hidden categories" settings migrate into rules once.
- [ ] **ORG-16** Every surface applies the same rules (guide, player lists and zapping, walls, rail counts, History, Home, Search, sport channel matching).
- [ ] **ORG-17** A restricted profile sees less than the rules allow, never more.

**Library manager**

- [x] **ORG-18** Two panes: groups on the left, the selected group's content on the right.
- [x] **ORG-19** Room buttons (Live TV, Movies, Series), source scope ("All sources" or one source), filter All / Enabled / Disabled.
- [x] **ORG-20** "Search this pane" and Clear.
- [x] **ORG-21** Each group shows enabled / total; shortcuts show "Automatic view".
- [x] **ORG-22** Group menu: Disable/Enable group, Content order, Move, Move to top, Move to bottom, Move to position, Reset this group.
- [x] **ORG-23** Item menu: Hide/Show everywhere, Move, Move to top, Move to bottom, Move to position, with a note when the source or the group is disabled.
- [x] **ORG-24** OK on an item switches it on or off in this group.
- [x] **ORG-25** Select multiple, Select all matching entries, Enable/Disable selected or all matching, with a confirmation naming the count and scope.
- [x] **ORG-26** Undo of the last change.
- [x] **ORG-27** Loading, save-error and load-error states with Retry; a key-help line.
- [x] **ORG-28** The last group and source are remembered per room.
- [x] **ORG-29** "Advanced" (Live) opens Channel management.
- [x] **ORG-30** Opened from the guide's options, the walls' Options › Edit and Settings › Library.
- [x] **ORG-31** The All/Enabled choice is remembered and shared with Channel management's "Show hidden".

**Groups of your own**

- [x] **ORG-32** Up to 24 genre groups for Movies and Series: name, genres, year range, minimum rating.
- [x] **ORG-33** Settings list with a one-line summary per group, "None yet" when empty, "Add a group".
- [x] **ORG-34** Editor dialog: name, a switch per genre present in the library, from year, to year, rating at least; Save only when the group says something; Delete; Close.

**Backup**

- [x] **ORG-35** Rules and the film identities they depend on travel in encrypted backups; groups of your own and the show-hidden choice travel with the preferences.

## Discover: Stremio-compatible addons, Search and Library

Spec: [specs/50-discover-addons.md](../specs/50-discover-addons.md) · Milestone: M9 · 75 items

**Access and independence**

- [ ] **ADDON-01** Discover destination on the Home rail (icon + "Discover"), only for unrestricted profiles.
- [ ] **ADDON-02** Discover works independently of IPTV: no playlist needed; separate databases, cache, HTTP clients and encryption key.
- [ ] **ADDON-03** Restricted profiles: no Home entry; direct access shows "Addons are unavailable for restricted profiles."; every addon data operation is denied.
- [ ] **ADDON-04** Everything is per profile: installations, catalog order, catalog visibility, Library, watch progress.
- [ ] **ADDON-05** Discover data is excluded from the `.smbak` backup (stated in the UI).

**Installing and managing addons (Addons & setup)**

- [ ] **ADDON-06** Install one configured addon URL (`https://` or `stremio://`) from a masked text field.
- [ ] **ADDON-07** URL normalisation: `stremio://` becomes `https://`; directory/base URLs gain `manifest.json`; AIOMetadata `/stremio/<UUID>` base accepted.
- [ ] **ADDON-08** Manifests that require configuration are refused with guidance.
- [ ] **ADDON-09** Installed addons list: name, "N catalogs · Enabled/Disabled", enable switch.
- [ ] **ADDON-10** Refresh an addon's manifest.
- [ ] **ADDON-11** Raise an addon's provider priority ("Priority ↑"): source order and metadata fallback order.
- [ ] **ADDON-12** Remove an addon, with a confirmation dialog focused on Cancel.
- [ ] **ADDON-13** Reload saved addons.
- [ ] **ADDON-14** Browse one addon's catalogs (including hidden ones) from its row.
- [ ] **ADDON-15** The same manifest with different configurations can be installed side by side; installing the exact same URL again reuses the existing installation.

**Import**

- [ ] **ADDON-16** Import screen with three methods: Account & phone, From a file, Manual URL.
- [ ] **ADDON-17** Build a pending list from manual URLs, one masked URL at a time.
- [ ] **ADDON-18** Choose a UTF-8 text file (one URL per line) with the system document picker.
- [ ] **ADDON-19** Send a URL list or a `.txt` file from a phone over the local network (QR, one-time session).
- [ ] **ADDON-20** Copy addon configurations from a Stremio account through Stremio's device-link QR.
- [ ] **ADDON-21** Read a Nuvio addon-list or state JSON file (URLs only).
- [ ] **ADDON-22** Preview: every entry is checked against its provider; per-entry status; nothing is installed.
- [ ] **ADDON-23** Select or skip each new entry; install the selected ones in list order; result list.
- [ ] **ADDON-24** Existing installations are never changed by an import; duplicates in the list are skipped.

**Catalog organisation**

- [ ] **ADDON-25** Per-profile catalog order: pick up, move (Up/Down, Page Up/Down, Home/End, held repeats), OK to place, Back to cancel.
- [ ] **ADDON-26** Per-profile catalog visibility: Show/hide switch per catalog; hidden catalogs disappear from Discover home, the Discover filter page and Search.
- [ ] **ADDON-27** New catalogs appear shown and are appended in provider order; order and visibility survive manifest refresh.

**Discover home**

- [ ] **ADDON-28** Full-screen backdrop and a hero describing the focused title: logo (or title), facts, synopsis.
- [ ] **ADDON-29** Hero synopsis in the metadata addon's language, fetched after focus rests, never flashing catalog-language text first.
- [ ] **ADDON-30** Continue watching as the first row: part-watched Discover titles with progress bars.
- [ ] **ADDON-31** One shelf per ready-to-browse catalog (no required choice), in the saved order.
- [ ] **ADDON-32** Saved shelves render at once and revalidate; "Showing saved titles; provider unavailable." when stale.
- [ ] **ADDON-33** "Show all" at the end of each shelf opens the paged grid.
- [ ] **ADDON-34** Trakt progress bars and watched ticks on movie posters and episode cards.
- [ ] **ADDON-35** Collapsible icon rail: Home, Library, Search, Discover (compass), Addons & setup, Back to home.
- [ ] **ADDON-36** Focus returns to the card, Show all button or rail item the viewer left.
- [ ] **ADDON-37** Missing Continue-watching artwork is repaired once per Discover visit.

**Grids, filters, Search**

- [ ] **ADDON-38** Show all grid with captions (title; year · rating), automatic paging, 1,000-title cap, explicit retry of a failed page.
- [ ] **ADDON-39** Refresh titles (bypasses the fresh cache).
- [ ] **ADDON-40** Option filters as choosers (Genre, Year, …; required or optional) and free-text filters with Apply.
- [ ] **ADDON-41** Discover filter page: choose Type and Catalog across all visible catalogs, then its filters.
- [ ] **ADDON-42** Search: one query across eligible catalogs; Movies and Series rows; progress, partial failures, stale notice.

**Title pages**

- [ ] **ADDON-43** Details from the catalog's addon, falling back to other installed metadata addons.
- [ ] **ADDON-44** Movie page: title, facts, synopsis, Find sources / Continue watching, Start from beginning, Library toggle, cast portraits.
- [ ] **ADDON-45** Series page: overview, cast names, season chips (Specials last), episode cards with thumbnails and Trakt bars/ticks.
- [ ] **ADDON-46** Episode page with its own title, synopsis and sources.
- [ ] **ADDON-47** Retry details / Retry episode details.

**Sources and playback**

- [ ] **ADDON-48** Progressive source list, grouped per provider in priority order, each with loading/failure/empty state.
- [ ] **ADDON-49** Scraper chooser filters providers; Refresh re-resolves sources.
- [ ] **ADDON-50** Unsupported transports (torrent, external link, YouTube, NZB, archives, local bridge) are listed as "Unsupported transport" and never started.
- [ ] **ADDON-51** Continue watching and Start from beginning start the first playable source automatically.
- [ ] **ADDON-52** Resume from the local position, or from a newer Trakt pause.
- [ ] **ADDON-53** Playback loading screen: backdrop, pulsing logo or title, stage text, Cancel and Subtitles.
- [ ] **ADDON-54** Addon player with the shared bottom controls (transport, audio, subtitles, aspect Fit/Zoom/Fill), D-pad seek and media keys.
- [ ] **ADDON-55** Retry with a freshly resolved source after a failure or a background stop.
- [ ] **ADDON-56** At the end: movies return to their page; episodes continue to the next one (preference), across seasons.
- [ ] **ADDON-57** Starting addon playback stops IPTV playback; only one addon player exists at a time.

**Subtitles and audio**

- [ ] **ADDON-58** Subtitle picker: language column with counts and "Subtitles off"; option cards for embedded, stream and addon subtitles; "✓ Selected".
- [ ] **ADDON-59** Subtitle addons are queried with the stream's `videoHash`, `videoSize` and `filename` hints only.
- [ ] **ADDON-60** Automatic subtitles from the primary/secondary subtitle preferences, suppressed when the preferred audio language is present; 5 s budget with embedded fallback.
- [ ] **ADDON-61** "Show all languages" toggle (persisted) in the picker and in Addons & setup.
- [ ] **ADDON-62** Subtitle sync: ±60 s, 0.1 s steps, 1 s when held, Apply, Reset to zero, Play/Pause preview.
- [ ] **ADDON-63** Downloaded SRT, WebVTT and SSA/ASS subtitles; appearance follows the VOD subtitle settings.
- [ ] **ADDON-64** Audio track picker.

**History and Library**

- [ ] **ADDON-65** Encrypted watch progress per title and episode; complete at 95 % or at the end.
- [ ] **ADDON-66** Watch history page in Addons & setup with "Forget progress".
- [ ] **ADDON-67** Discover progress feeds Home's Continue watching ([Home](../specs/02-home.md)).
- [ ] **ADDON-68** Add to / Remove from library on movie and series pages.
- [ ] **ADDON-69** Library grid with All / Movies / Series filters, newest first, 1,000 titles per profile.
- [ ] **ADDON-70** A Library title whose addon is disabled or removed explains itself and offers removal.

**Settings, text, security**

- [ ] **ADDON-71** Subtitles section in Addons & setup: show-all switch, preferred-language summary, metadata-language guidance.
- [ ] **ADDON-72** About screen "Discover addons" privacy paragraph.
- [ ] **ADDON-73** Configured URLs and addon payloads encrypted with a Discover-only key; masked inputs; URLs never in logs, UI labels or saved state.
- [ ] **ADDON-74** Encrypted response cache for fast revisits and offline fallback.
- [ ] **ADDON-75** Addon interface in all seven app languages; provider text is never translated by the app.

## Trakt sync

Spec: [specs/51-trakt.md](../specs/51-trakt.md) · Milestone: M10 · 30 items

**Account**

- [ ] **TRAKT-01** Settings › Accounts section with a Trakt panel (unrestricted profiles only).
- [ ] **TRAKT-02** Connect Trakt with a device code: QR of the activation address, the address and the code shown on the TV.
- [ ] **TRAKT-03** Cancel sign-in with the button or Back; leaving the foreground cancels sign-in.
- [ ] **TRAKT-04** One Trakt account per Sohva profile; the panel names the profile and shows "Connected as <username>".
- [ ] **TRAKT-05** "Sign in again" when an account exists (replaces the saved sign-in).
- [ ] **TRAKT-06** "Sign in again" state explained when Trakt no longer accepts the saved sign-in.
- [ ] **TRAKT-07** Disconnect removes the sign-in and the cached Trakt data from this TV only.
- [ ] **TRAKT-08** "Not configured" state when the build has no Trakt application credentials.
- [ ] **TRAKT-09** Error messages for declined, expired, unusable code, rate limit and connection failure.
- [ ] **TRAKT-10** Tokens refreshed automatically before they expire.
- [ ] **TRAKT-11** Removing a Sohva profile disconnects its Trakt account.

**Scrobbling**

- [ ] **TRAKT-12** Start / pause / stop scrobbles from the VOD player (movies and episodes).
- [ ] **TRAKT-13** Start / pause / stop scrobbles from the Discover player (movies and episodes).
- [ ] **TRAKT-14** Nothing is sent for Live TV, catch-up, or titles without a TMDB/IMDb id.
- [ ] **TRAKT-15** A rebuffer is not a pause (2.5 s settle before a pause is sent).
- [ ] **TRAKT-16** Scrobbles that cannot be delivered are kept and retried (latest report per title wins).
- [ ] **TRAKT-17** A playback interrupted by a crash or kill is closed with a pause on the next start.

**Read side**

- [ ] **TRAKT-18** Background sync of the account's paused positions and watched marks: at profile start, 3 s after each reported stop, every 15 minutes — only when Trakt reports new activity.
- [ ] **TRAKT-19** Trakt progress bars and watched ticks on VOD library cards, series episode lists and Continue watching.
- [ ] **TRAKT-20** Trakt progress bars and watched ticks on Discover movie posters and episode cards.
- [ ] **TRAKT-21** Continue watching includes library titles paused on Trakt (e.g. on another device).
- [ ] **TRAKT-22** Resume from a Trakt pause: Discover by fraction; VOD when the runtime is known.
- [ ] **TRAKT-23** One Continue watching card per movie across Discover and VOD copies, bridged by Trakt ids.
- [ ] **TRAKT-24** Home "Watch next" row: next unwatched episode of up to 8 recently watched shows.
- [ ] **TRAKT-25** Home "Recommended for you" row: 10 movies and 10 shows, interleaved, refreshed twice a day.
- [ ] **TRAKT-26** Home hero for Trakt cards: synopsis and backdrop from TMDB by id in the metadata language; Trakt's text as fallback.
- [ ] **TRAKT-27** Opening a Trakt card: the library's own page when a copy is matched to the same TMDB record; otherwise a lookup over the Discover addons (IMDb, then TMDB); otherwise "not available".
- [ ] **TRAKT-28** "Trakt history is waiting for its first sync" notice on Home right after connecting.
- [ ] **TRAKT-29** Restricted profiles: no Accounts section, no Trakt rows, no notice.
- [ ] **TRAKT-30** Demo (screenshot) build: a fictional connected account seeded offline, no network.

## Sohva Sport

Spec: [specs/60-sohva-sport.md](../specs/60-sohva-sport.md) · Milestone: M8 · 57 items

**Provider and settings**

- [ ] **SPORT-01** The viewer's own API-Sports key: masked field, Save key / Remove key, stored encrypted on the TV.
- [ ] **SPORT-02** The key can also arrive from phone setup ([specs/11](../specs/11-phone-setup.md)).
- [ ] **SPORT-03** Twelve sports: football, ice hockey, AFL, basketball, baseball, handball, rugby, volleyball, American football, MMA, Formula 1, NBA.
- [ ] **SPORT-04** Follow or unfollow each sport (defaults: football, ice hockey, AFL).
- [ ] **SPORT-05** Per-sport competition list from the provider (football: current season only), with search by name or country and "Selected X of Y".
- [ ] **SPORT-06** Follow or unfollow single competitions (defaults: seven football competitions, Liiga, AFL).
- [ ] **SPORT-07** Sports without a competition list (MMA, Formula 1, NBA) say so and show every event of the day.
- [ ] **SPORT-08** Channel country/language priority codes (up to 8, e.g. `ES, EN, UK`) with Save order.
- [ ] **SPORT-09** Settings status line: sports time zone, service state (cache / updated / stale fallback / multiple sources / refreshing / error), remaining API quota per sport, polling interval.
- [ ] **SPORT-10** Sohva Sport uses the app time zone (Settings > General).

**Feed**

- [ ] **SPORT-11** Today's events for all followed sports and competitions, in the app time zone.
- [ ] **SPORT-12** The saved feed is shown at once; the network refresh follows, sport by sport.
- [ ] **SPORT-13** During a provider outage, data up to 24 hours past its freshness is shown instead of an error.
- [ ] **SPORT-14** Automatic refresh every 5 / 10 / 30 minutes only while Sohva Sport or the score ticker is visible and the app is in front.
- [ ] **SPORT-15** One refresh on return when the data is older than the polling interval.
- [ ] **SPORT-16** Manual refresh (header Refresh; Try again on the error card).
- [ ] **SPORT-17** Status mapping per sport (live, upcoming, finished, postponed, cancelled, interrupted, unknown).

**Today screen**

- [ ] **SPORT-18** Header: "SOHVA SPORT" wordmark, weekday, date and time in the app zone (12/24 h per the TV), three icon actions: Refresh, Guide, Settings.
- [ ] **SPORT-19** Filter tabs All, one per followed sport, Watchable, Favourites, each with its event count.
- [ ] **SPORT-20** Sections Live now, Later today, Sports channels now, Finished; empty sections hidden.
- [ ] **SPORT-21** Ordering: live, scheduled, disrupted, finished; then kick-off minute; then competition.
- [ ] **SPORT-22** Match card: competition logo and name, status or live badge, two team marks (crest over initials in the sport's accent), score or kick-off time, AFL goals/behinds, football minute, watch call to action, favourite star.
- [ ] **SPORT-23** Call to action: "WATCH · N CHANNELS", "N POSSIBLE CHANNEL MATCHES" or "No broadcast available".
- [ ] **SPORT-24** Live badge with the minute and a short pulse when the minute changes.
- [ ] **SPORT-25** Sports channels now: up to 8 channels with an Available match, OK plays the channel.
- [ ] **SPORT-26** Initial focus on the first live game, else the first upcoming, else the first finished, else the All tab.
- [ ] **SPORT-27** Loading, error (with Try again) and per-filter empty states.

**Match hub**

- [ ] **SPORT-28** OK on a card opens the match hub over the list; Back or Close closes it.
- [ ] **SPORT-29** Hub header: competition logo and name, status, "start · N watchable streams", both teams with crests, large score and score detail.
- [ ] **SPORT-30** Remind me / Reminder set for a scheduled game that has not started.
- [ ] **SPORT-31** Match events panel (football): timeline band (home above, away below, half-time tick), incident list, Refresh.
- [ ] **SPORT-32** Incident list scrolls with Up/Down and releases focus at either end.
- [ ] **SPORT-33** Match events states: not available for this sport, loading, none yet, error with Try again, cached data warning.
- [ ] **SPORT-34** Streams panel: rows with confidence, source (TV GUIDE / M3U NAME), channel name, programme or detail, stream tag chips, start-offset explanation.
- [ ] **SPORT-35** Watch plays the channel; Back from the player returns to the open hub.
- [ ] **SPORT-36** Confirm a Possible stream, Reject any undecided stream, Restore a decided one; "Confirmed by you".
- [ ] **SPORT-37** Decisions survive refreshes, re-imports and restarts.
- [ ] **SPORT-38** Stream order stays fixed while the hub is open.
- [ ] **SPORT-39** Hub opens from Home, a reminder or a notification directly on the named game.

**Stream pairing**

- [ ] **SPORT-40** Candidates from guide programmes within 120 minutes of kick-off.
- [ ] **SPORT-41** Candidates from M3U channel names that name both teams.
- [ ] **SPORT-42** Channel-name clock times with explicit zones (CET, CEST, EET, EEST, UTC/GMT and offsets), AM/PM, and dates (ISO, month names, `18/9`).
- [ ] **SPORT-43** Team aliases (built-in football aliases plus a stored alias table).
- [ ] **SPORT-44** Confidence Available / Possible / Rejected, with the ordering rules.
- [ ] **SPORT-45** Country/language priority reorders streams within one confidence level.
- [ ] **SPORT-46** Pairing results cached across restarts, recomputed when channels, guide or rules change.
- [ ] **SPORT-47** Hidden channels, disabled sources and hidden groups are never offered.

**Favourites and reminders**

- [ ] **SPORT-48** Favourites filter and star on favourite cards (per profile). Beta 23 has no control to add one (§10).
- [ ] **SPORT-49** A match reminder fires one minute before kick-off and opens its stream, or the hub when no stream was known.

**Elsewhere in the app**

- [ ] **SPORT-50** Home "Today's sport" row: first 6 games, total count, a card opens the hub.
- [ ] **SPORT-51** Home hero for a focused sport card (kicker, teams, competition, minute or start, score, crest backdrop).
- [ ] **SPORT-52** Search results of type SPORT (team or competition names) open Sohva Sport.
- [ ] **SPORT-53** Score ticker over the player: live games then games starting within 3 hours, at most 4 rows.
- [ ] **SPORT-54** Ticker toggled from the player quick menu or a remote button; a player shortcut opens Sohva Sport.
- [ ] **SPORT-55** Followed sports, competitions, priority codes and favourites travel in encrypted backups; the key does not.
- [ ] **SPORT-56** About/legal: API-Sports notice, terms link, key disclosure text.
- [ ] **SPORT-57** Demo flavour substitutes fictional sport data; the Lab variant never refreshes automatically.

## Settings

Spec: [specs/70-settings.md](../specs/70-settings.md) · Milestone: M7 · 40 items

**Screen and navigation**

- [x] **SET-01** Settings opens from the Home rail, the guide (options and empty guide) and Sohva Sport.
- [x] **SET-02** A restricted profile with a PIN set opens Settings only past the PIN ("Settings are locked for this profile").
- [x] **SET-03** Header: "Settings", a breadcrumb "›  SECTION" naming the selected section, and a Back button.
- [x] **SET-04** Section rail: General, Playlists, Playback, Remote buttons, Library, Accounts (unrestricted profiles only), Sohva Sport, Parental controls, Backup & tools, About.
- [x] **SET-05** Settings opens on Playlists with focus on its first control.
- [x] **SET-06** OK on a rail row shows that section and moves focus to its first control; focus alone does not switch.
- [x] **SET-07** Value rows show the current value and a chevron; OK opens a single-choice picker focused on the current value.
- [x] **SET-08** Choosing in a picker applies at once, closes it and returns focus to the row; Back closes it unchanged.
- [x] **SET-09** Switch rows toggle and apply at once.
- [x] **SET-10** Multi-choice picker with Done (profile groups); every toggle applies at once.
- [x] **SET-11** Every section shows its own status line for the results of its actions (new in the rebuild; beta 23 showed them only in Playlists).

**General**

- [x] **SET-20** Interface language: System default, English, Suomi, and five drafts labelled in their own language; the app restarts to apply.
- [x] **SET-21** Interface size: Normal 100 %, Compact 90 %, Small 80 %, Smaller 70 %; applies at once.
- [x] **SET-22** Colour theme: seven themes with a one-line description each; applies at once.
- [x] **SET-23** Channel numbers in the guide on or off.
- [x] **SET-24** Time zone: the TV's own (default) or any zone from a searchable picker with Recent and All zones by region.
- [x] **SET-25** Startup screen: Home, Programme guide or Last channel.
- [x] **SET-26** Playlist and EPG refresh interval: 1, 2, 4, 10 or 24 hours.
- [x] **SET-27** Reminders can open Sohva TV: shows Allowed / Not allowed and opens the TV's "display over other apps" screen.
- [x] **SET-28** Profiles group: Who is watching, ask at start, what a profile may see, add and remove profiles (behaviour in specs/04).

**Playback**

- [x] **SET-30** Playback buffer: Media3 default, Low latency, Stability, each with a description.
- [x] **SET-31** Playback recovery: Standard or Persistent, each with a description.
- [x] **SET-32** Skip step: 10 s, 30 s, 1 min, 2 min.
- [x] **SET-33** Match the display to the picture (auto frame rate) on or off.
- [x] **SET-34** Continue to the next episode on or off.
- [x] **SET-35** Keep watching in a corner (picture in picture) on or off.
- [x] **SET-36** Subtitle size: Follow the TV, Small, Normal, Large, Very large.
- [x] **SET-37** Subtitle colour: Follow the TV, White, Yellow.
- [x] **SET-38** Subtitle background: Follow the TV, None, Shadow, Box.
- [x] **SET-39** VOD audio and subtitles: primary and secondary audio, primary and secondary subtitles, each Automatic or one of eleven languages; picking the partner slot's language clears the partner.

**Library (image cache owned here)**

- [x] **SET-40** Image cache limit 100, 250 or 500 MB, applied from the next start.
- [x] **SET-41** Image cache usage "N MB in use" and Clear image cache (disk and memory).

**Other sections (owned by other specs, listed with their options in 6.1)**

- [x] **SET-50** Playlists: source list, add M3U / Xtream, set up from a phone, source pages ([10](../specs/10-sources-and-import.md), [11](../specs/11-phone-setup.md)).
- [x] **SET-51** Remote buttons: press/hold grid for twelve buttons, reset ([31](../specs/31-remote-button-mapping.md)).
- [x] **SET-52** Library: TMDB and TVmaze switches, TMDB key with Save and Test, metadata language, preferred film version, Manage groups & content, groups of your own, Clear metadata cache, TMDB and TVmaze website buttons ([41](../specs/41-metadata-enrichment.md), [42](../specs/42-library-organization.md)).
- [ ] **SET-53** Accounts: the Trakt panel, hidden for restricted profiles ([51](../specs/51-trakt.md)).
- [ ] **SET-54** Sohva Sport: channel country/language priority, API-Sports key, followed sports and competitions ([60](../specs/60-sohva-sport.md)).
- [x] **SET-55** Parental controls: set, change or remove the household PIN ([04](../specs/04-profiles-parental.md)).
- [x] **SET-56** Backup & tools: encrypted backup save and restore, Clear all guide data ([71](../specs/71-backup-restore.md)).
- [x] **SET-57** About: updates, About privacy and licences, Help translate, Save diagnostics ([72](../specs/72-updates-about-diagnostics.md)).

## Encrypted backup and restore

Spec: [specs/71-backup-restore.md](../specs/71-backup-restore.md) · Milestone: M7 · 15 items

- [x] **BACKUP-01** Settings › Backup & tools shows an "Encrypted backup" group: title, description, password field, **Save backup**, **Restore backup**, and the warning that the password cannot be recovered.
- [x] **BACKUP-02** Password field: masked, edit-on-click, 8–128 characters; both buttons are disabled below 8 characters and while a backup operation runs.
- [x] **BACKUP-03** **Save backup** opens the system "create document" picker with the suggested name `sohva-tv-backup.smbak` and MIME type `application/vnd.streammate.backup`.
- [x] **BACKUP-04** The file is encrypted with a key derived from the password (PBKDF2-HMAC-SHA256, 210,000 iterations, AES-256-GCM); the password is never stored.
- [x] **BACKUP-05** The backup carries: IPTV sources with credentials, the parental PIN, every setting listed in §6.2, profiles and each profile's favourites/recents/last channel/locked channels/ allowed groups, channel customisations (name, group, hidden, order, EPG id, logo, number), custom channel lists and members, organisation rules with the film identities they depend on, and the viewer's own genre groups.
- [x] **BACKUP-06** A logo sent from a phone travels inside the backup as image bytes and is recreated on the target TV.
- [x] **BACKUP-07** The backup never contains Discover data, Trakt, the TMDB token, the TVmaze switch, the API-Sports key, watch progress, reminders, guide/catalogue content, sports match decisions, the interface language, the image-cache size or "Match the display to the picture" (§6.3).
- [x] **BACKUP-08** **Restore backup** opens the system "open document" picker filtered to `application/vnd.streammate.backup` and `application/octet-stream`.
- [x] **BACKUP-09** Restore checks the whole file (envelope, password, structure, limits) before it changes anything; each failure has its own message (§4.6).
- [x] **BACKUP-10** Restore replaces the sources (sources the backup lacks are deleted with all their data), the channel customisations, custom lists, organisation rules, all settings, profiles and the PIN; anything the backup does not carry stays as it was.
- [x] **BACKUP-11** After a restore the Playlists list shows the restored sources and the message "Backup restored. Refresh channels and the programme guide."; nothing syncs by itself and the app does not restart; theme, interface size and the other settings apply at once.
- [x] **BACKUP-12** Every backup written by any build since StreamMate (format versions 1 and 2) restores; settings the older file lacks take the defaults of §6.4 (an old backup without a colour theme restores Original).
- [x] **BACKUP-13** A backup of a TV with a 200,000-film catalogue stays small (tens of kilobytes) and its export never runs the app out of memory (the beta 17 repair, §10).
- [x] **BACKUP-14** The password field is emptied after every save or restore attempt, successful or not.
- [x] **BACKUP-15** Save shows "Encrypted backup saved" or the failure; restore shows the restored message or the failure (the rebuild shows both in the section itself, §5).

## Updates, About and diagnostics

Spec: [specs/72-updates-about-diagnostics.md](../specs/72-updates-about-diagnostics.md) · Milestone: M7 · 29 items

**Updates**

- [x] **ABOUT-01** The UPDATES group shows the installed version: "Installed: 0.1.0-beta.23".
- [x] **ABOUT-02** An automatic update check runs at most once per 24 hours, at app start, only in the release package `com.streammate.tv`; nothing is downloaded without a press.
- [x] **ABOUT-03** **Check for updates** checks now.
- [x] **ABOUT-04** One status line names the phase: not checked, checking, newest, available, downloading with percent, downloaded and verified, permission needed, or one of four failures (in `danger`).
- [x] **ABOUT-05** The newest published release whose stated Android build is above the installed one is offered: "Sohva TV X is available." with **Download**.
- [x] **ABOUT-06** The download goes to the app's cache with a live percentage and is verified against the release's `SHA256SUMS.txt`; a mismatch deletes it; a release without a checksum file is refused.
- [x] **ABOUT-07** The release's install-time profile for the device's Android (`.api31.dm` or `.api28.dm`) is downloaded, verified the same way and installed together with the APK in one PackageInstaller session, so Android compiles the update while installing (`reason=install-dm`).
- [x] **ABOUT-08** Without a usable profile, or when the session cannot be used, the APK is handed to the system installer screen as before.
- [x] **ABOUT-09** When Android needs the "install unknown apps" permission, **Allow installs** opens that page for Sohva TV and **Install** tries again.
- [x] **ABOUT-10** "What's new in X" shows the offered release's notes, otherwise the installed build's own release notes, remembered for offline use (at most 4,000 characters).
- [x] **ABOUT-11** Debug, demo and Lab builds never check, download or install; About says public updates are disabled (Lab also shows its safety notice as the notes).
- [x] **ABOUT-12** Help line: downloads are verified; playlists and settings are kept.

**About and legal**

- [x] **ABOUT-13** **About, privacy and licences** (centred button) opens the legal screen.
- [x] **ABOUT-14** Legal screen: brand, title, subtitle, Back; the version and the non-commercial statement.
- [x] **ABOUT-15** On-device privacy summary (five paragraphs).
- [x] **ABOUT-16** Support and privacy contact with **Send email** (`mailto:` the public address).
- [x] **ABOUT-17** TMDB attribution: notice, the unmodified TMDB logo, **Open TMDB**.
- [x] **ABOUT-18** TVmaze (CC BY-SA): notice, **Open TVmaze**, **TVmaze licence**.
- [x] **ABOUT-19** API-Sports: notice, provider-rights statement, **API-Sports terms**.
- [x] **ABOUT-20** Notes on services and connections (HTTP, metadata and sports disclosures).
- [x] **ABOUT-21** Discover addons notice.
- [x] **ABOUT-22** Open-source notice with **Apache 2.0** licence link.
- [x] **ABOUT-23** No-affiliation statement.
- [x] **ABOUT-24** The MIT notices of the Nord, Everforest and Kanagawa palettes ship inside the APK (`theme-licenses.txt`); beta 23 does not show them on screen.
- [x] **ABOUT-25** TRANSLATIONS: **Help translate Sohva TV** opens the public repository.

**Diagnostics**

- [x] **ABOUT-26** DIAGNOSTICS: **Save diagnostics** opens the system "create document" picker with the name `sohva-tv-diagnostics-yyyyMMdd-HHmm.txt` (text/plain).
- [x] **ABOUT-27** The file holds app, device, Android, locale, time zones, a Display line, SQLite version, key settings, sources by name, every refresh state and the app's last 600 event lines, including start-up and channel-loading timings.
- [x] **ABOUT-28** Addresses, user names, passwords, keys and tokens are removed before a line is kept and again before the file is written.
- [x] **ABOUT-29** The result shows under the button: "Diagnostics saved. Share the file with the developer if asked." or the failure.

## Security and privacy

Spec: [specs/73-security-privacy.md](../specs/73-security-privacy.md) · Milestone: M1 · 29 items

**Secrets at rest**

- [x] **SEC-01** IPTV source lists (addresses, user names, passwords), every stored stream address, the parental PIN, the TMDB token, the API-Sports key, Trakt tokens and pending scrobbles, and Discover addon URLs and payloads are stored encrypted (AES-256-GCM).
- [x] **SEC-02** One Android Keystore AES-256 key per store wraps a random 256-bit software data key (envelope encryption): the keystore is used once per process, values are encrypted in software.
- [x] **SEC-03** Values written in the older direct-keystore format (`v1:`) still decrypt; nothing needs a migration pass.
- [ ] **SEC-04** The single-source settings of StreamMate's first builds are migrated into the encrypted source list on first read and the old file is cleared.
- [ ] **SEC-05** The parental PIN (4–8 digits) is stored encrypted and compared in constant time.
- [ ] **SEC-06** A stream address is decrypted only when it is played, inside the playback data source; the media session and its notification see only a placeholder id.

**Redaction**

- [x] **SEC-07** Every error text shown on screen, every diagnostics log line and the diagnostics file pass through one redactor: URLs keep only scheme, host and port; `key=value` secrets become `<redacted>`.
- [x] **SEC-08** Objects that hold secrets print `<redacted>` from `toString()` (sources, metadata and sports settings, stream requests, addon documents and endpoints).
- [x] **SEC-09** The Playlists list shows a source only by the name the viewer gave it; its address and credentials appear only on its own page, with the password masked.

**Input and network policy**

- [x] **SEC-10** A source address must be an `http` or `https` URL with a host.
- [x] **SEC-11** Keys are validated before storage: TMDB token ≤ 2,048 characters, API-Sports key ≤ 512, neither may contain a line break.
- [x] **SEC-12** Plain HTTP is permitted for the viewer's own IPTV hosts; the first-party service domains (API-Sports, TMDB, TVmaze) can only be reached over HTTPS.
- [x] **SEC-13** Only the system's certificate authorities are trusted; no user-added CAs, no pinning.
- [ ] **SEC-14** Discover addons are HTTPS-only in practice (no UI path accepts HTTP).
- [x] **SEC-15** The phone setup page runs only while its dialog is open, on the LAN address, a random port and an 8-character one-time token ([Phone setup](../specs/11-phone-setup.md)).

**Platform**

- [x] **SEC-16** Android cloud backup and device-to-device transfer are disabled for all app data.
- [ ] **SEC-17** A minimal permission set, each with a stated reason (§4.8).
- [ ] **SEC-18** Only the launcher activity and the media-session service are exported by the app; the session accepts only this app, trusted system controllers and its own notification.
- [ ] **SEC-19** The release build is not debuggable; it is profileable by the shell only.

**Privacy**

- [x] **SEC-20** No developer account, analytics, advertising, telemetry, crash upload or first-party server.
- [ ] **SEC-21** Each third party receives only what its feature needs and only when the viewer has turned that feature on (§4.9 table).
- [x] **SEC-22** The update check sends GitHub nothing about the viewer beyond the request itself.
- [x] **SEC-23** Stream, playlist and guide requests identify the app as `Sohva TV/<version> (Android TV <release>)` unless the playlist sets its own user agent.
- [ ] **SEC-24** The in-app privacy summary, the "security note" under source settings, and the published privacy policy state these commitments; a security contact is published (SECURITY.md).

**Release hygiene**

- [x] **SEC-25** A public-source content audit runs before every source publication.
- [ ] **SEC-26** A release-document audit runs over the tester package.
- [ ] **SEC-27** An APK safety audit checks package, version, label, non-debuggable, the permission allowlist, the signing certificate and secret-shaped content in every APK entry.
- [x] **SEC-28** Gitleaks scans the full public history, the staged commit and the release package.
- [ ] **SEC-29** Build-time secrets (the Trakt client id and secret) are injected at build time and never appear in the public source.

## Localisation

Spec: [specs/74-localization.md](../specs/74-localization.md) · Milestone: M7 · 13 items

- [x] **L10N-01** Seven interface languages: English (fallback), Finnish (complete), Spanish, Portuguese, German, Swedish, Italian (drafts).
- [x] **L10N-02** Interface language picker: System default plus the seven, each named in its own language; drafts carry "(borrador)", "(rascunho)", "(Entwurf)", "(utkast)", "(bozza)".
- [x] **L10N-03** Choosing a language restarts the app's screen in that language; System default follows the TV and falls back to English for other languages.
- [x] **L10N-04** From Android 13 the choice is the platform per-app language and also appears in Android's own per-app language screen.
- [x] **L10N-05** Metadata language for TMDB titles, plots and artwork: 22 languages; Finnish by default with a Finnish interface, else English; changing it refreshes metadata in the background.
- [x] **L10N-06** Language names in pickers (VOD audio/subtitle languages, metadata languages) appear in the interface language.
- [x] **L10N-07** One app time zone (the TV's own by default) for the guide, Home, the player and Sohva Sport.
- [x] **L10N-08** Clocks on Home, Sohva Sport and the player follow the interface language and the TV's 12/24-hour setting.
- [x] **L10N-09** Guide day labels "Now", "Today", "Tomorrow", "Yesterday" beside the date.
- [x] **L10N-10** Counts use plural forms in every language.
- [x] **L10N-11** Error messages, including stored refresh failures, appear in the current interface language.
- [x] **L10N-12** "Help translate Sohva TV" in About opens the public repository.
- [x] **L10N-13** The phone setup page is in the TV's interface language (the addon phone page is English).
