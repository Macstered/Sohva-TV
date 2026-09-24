# Reference screenshots

All images are 1920×1080 captures from an Android TV emulator (xhdpi, so 1 dp = 2 px; the
layouts are specified in dp at 960×540). Every image shows synthetic or fictional content: no
real provider, channel or account data.

**The code-derived specs win over these pictures.** The `beta23-demo` and `beta23-synthetic` sets
show the current app. The older sets predate later visual changes and are included for screens that have no
current capture; the differences are listed below.

## beta23-demo — current app, every main screen (24 September 2026)

The beta 23 `demo` build (package `com.streammate.tv.demo`, fictional content) on the Android TV
emulator, captured with remote keys and taps. **This is the primary visual reference.** Demo
artefacts to ignore: the same football clip plays for every channel and film (the VOD title reads
"Channel"); audio and subtitle lists are empty; only two channels sit in the guide's first group;
Discover is absent (the demo build hides it); the note "Trakt history is waiting for its first sync"
comes from the demo's fake Trakt account.

| Files | Shows |
|---|---|
| `01`–`07` | Home: Continue watching hero, Watch next, Today's sport, Recommended (posters), Recently watched channels, the rail expanded with labels, the hold-OK actions dialog |
| `10`–`14` | Guide: grid with hero and now-line, group rail open, options sheet, programme actions dialog, empty-library state. (Programme cells show times in the system zone while the ruler uses the chosen zone — suspected bug D12.) |
| `20`–`28` | Player: live chrome, channel list, focused control row, audio picker, subtitle picker, playback info line, quick actions, VOD controls |
| `31`–`33` | Sohva Sport: Today (Live now, Later today), scrolled (Sports channels now, Finished), match hub with events timeline and Streams |
| `40`–`49` | Movies: History wall, a group wall, focused poster, movie details, "Wrong details?" match picker, Library options; Series: wall, group, details, episodes row |
| `50`–`52` | Search: empty with keyboard, no results, results by type |
| `60`–`70` | Settings: Playlists, a source page, General, Playback, Remote buttons, Library (upper and lower), Accounts (Trakt), Sohva Sport, Parental controls, Backup & tools, About |
| `71`–`75` | Settings pickers: interface size, time zone, Who is watching, skip step, colour theme |
| `76`–`79` | About, privacy and licences; phone setup QR dialog; Library manager and its group actions; Channel management |
| `80`–`85` | Settings › General in each non-default colour theme: Nordic Slate, Cozy Hearth, Cyber Plum, Nord, Everforest, Kanagawa (Original is `62`) |

## beta23-synthetic — current app (23 September 2026)

Captured by the slow-box measurement harness on the performance preview that became beta 23,
with an owner-scale synthetic library (56,164 channels, 30,000 films, 1,500 series). Posters and
backdrops are generated noise images, and channel logos are coloured bars or two-letter
initials — the fixture's art, not the app's.

| File | Shows |
|---|---|
| `home.png` | Home: icon rail (the current destination, Home's sofa, drawn as a light tile; then Live TV, Sport, Movies, Series, Search, Discover, Settings), "Sohva TV" title, clock, Continue watching hero with progress bar, landscape cards with the focused one outlined, Recently watched channels row |
| `home-row-moved.png` | Home after moving along the row: the hero follows the focused card |
| `guide.png` | Live TV guide: channel hero with LIVE badge, times, genre, description and Watch / Favourite / Find programme; day label and window; channel rail with numbers and logos; programme grid with the red now-line; key-hint footer |
| `guide-moved.png` | Guide after D-pad movement |
| `movies-wall.png`, `movies-wall-moved.png` | Movies poster wall and group rail, before and after moving |
| `series-wall.png`, `series-wall-moved.png` | Series wall, before and after moving |

> **Not in the repository** (owner's decision, 24 September 2026): the `older-builds/` captures
> below are kept on the build machine in `.local/older-builds/`, not in git.

## older-builds/2026-09-02-demo — demo build, 2 September 2026

Fictional demo content (see `assets/flavors/demo/`). **Differences from beta 23:** the app was
still branded StreamMate (wordmark top left, old launch mark), the Home layout is the pre-beta-14
one (a Guide button in the hero, rows below a large hero), Settings sections were Playlists /
Playback & remote / Programme data / SportMate / Parental controls / Backup / About (beta 23 has
General, Sources, Playback, Remote, Metadata, Accounts, Sport, Parental, Backup, About), the
background still had grain, and there were no colour themes. Guide, player, Sohva Sport and title
pages are close to today's structure.

| File | Shows |
|---|---|
| `01-home.png`, `02-home-todays-sport.png` | Old Home with Continue watching and Today's sport cards |
| `03-movies.png`, `04-series.png` | Poster walls with the group/genre rail, search field and Options |
| `03.1-movie-title-info.png`, `04.1-series-title-info.png` | Movie and series details: backdrop, title, rating, year, synopsis, Watch / Refresh episodes / Wrong details?, seasons and episode cards |
| `05-guide.png` | Guide (same anatomy as today) |
| `06-live-player.png` | Live player overlay: channel logo and name, LIVE badge, programme title, progress, next programme, control row |
| `07-sportmate.png` | Sohva Sport Today: filter chips (All, sports, Watchable, Favourites), Live now and Later today sections, match card |
| `08-match-info.png` | Match card: crests, score, match events timeline, Streams panel with Watch / Reject |
| `09-settings.png` | Old Settings layout (sources form) |

## older-builds/2026-09-10-discover-lab — Discover Lab build, 10–11 September 2026

Lab fixtures ("Synthetic provider", "Catalog 1", "Addon 28"). **Differences from beta 23:** the old
Sohva launch mark (flat cyan sofa) and background grain are still visible; Discover's structure is
otherwise current.

| File | Shows |
|---|---|
| `addon-second-shelf-focus.png`, `addon-auto-paging.png` | Discover home rows and a catalog grid loading more titles |
| `addon-catalog-order.png`, `addon-catalog-visibility.png` | Reordering catalogs and showing/hiding them |
| `addon-settings-installed.png`, `addon-settings-setup.png`, `addon-settings-subtitles.png` | Addons & setup: installed addons, add/import, subtitle preferences |
| `addon-import-settings-main.png`, `-files.png`, `-manual.png`, `-review-bottom.png`, `addon-lab-import-preview.png` | Import addons: account & phone, from a file, manual URL, and the review list |
| `addon-movie-cast.png`, `addon-series-cast.png` | Title pages with sources, cast row, seasons and episodes |
| `addon-playback-loading.png`, `addon-saved-row-loading.png` | Playback loading screen ("Fetching subtitles…") and a loading saved row |
| `addon-search-results.png` | Discover search results by type |
| `addon-subtitle-picker.png`, `addon-subtitle-picker-embedded.png`, `addon-subtitle-sync.png` | Subtitle picker and the timing (sync) dialog over playback |
| `library-grid.png`, `movie-action.png`, `series-action.png` | Library grid with All / Movies / Series filters, and saved-title actions |

## Screens without a current capture

Discover (the demo build hides it; the Lab captures above show its structure), the profile picker
and PIN screens (the demo has one profile and no PIN), reminder dialogs, and the player's error
banner and reconnect message. Their layouts are specified in
[design/03-screen-layouts.md](../03-screen-layouts.md).
