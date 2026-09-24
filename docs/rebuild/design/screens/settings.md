# Screen layout: Settings

> Code-derived values from beta 23 (`efab52a`), extracted 24 September 2026 from
> `iptv/.../feature/settings/` (SettingsScreen, SettingsComponents, SettingsPicker, SettingsRail,
> SettingsGroups, SettingsLabels, TimeZonePicker, AboutSection, RemoteMappingSection,
> ArtworkCache). Behaviour and the full option table: [specs/70](../../specs/70-settings.md).
> Older capture: `design/screenshots/older-builds/2026-09-02-demo/09-settings.png` (pre-restructure).

## 1. Frame

- Screen background with default padding (40/24) → Column:
  - Header Row (bottom-aligned): title "Settings" (display 40/44, Black, −0.5); breadcrumb "›  " +
    section label uppercased (label Bold, letter spacing 1.4, `textDim`, padding start 14 /
    bottom 6, clipped); Back button (compact, Back icon).
  - `Spacer(14)`.
  - Body `Row(spacedBy 26)`: rail `width 214, fillMaxHeight`; pane `weight 1`, `focusRestorer`,
    `focusGroup`, `spacedBy 8`, bottom padding 14.
- The pane is a **non-lazy** scrolling Column: the whole selected section composes at once
  (rebuild: lazy or split sections).
- The screen opens on **Playlists** (SOURCES).

## 2. Rail

`LazyColumn(spacedBy 2, contentPadding vertical 4)`; rows are `TvListRow` at **41 dp** (ten fit a
540 dp screen). Choosing a section needs OK; focus alone does not switch. Row look: focus fill
flip; selected → a 3×22 dp `focus` bar, 12 dp gap, 18 dp icon, label body Bold (Medium when not
selected).

| # | Section | Label (EN) | Icon |
|---|---|---|---|
| 1 | GENERAL | General | Settings |
| 2 | SOURCES | Playlists | Channels |
| 3 | PLAYBACK | Playback | Play |
| 4 | REMOTE | Remote buttons | Aspect |
| 5 | METADATA | Library | Info |
| 6 | ACCOUNTS (only when the Trakt panel is available) | Accounts | Link |
| 7 | SPORT | Sohva Sport | Target |
| 8 | PARENTAL | Parental controls | Lock |
| 9 | BACKUP | Backup & tools | Save |
| 10 | ABOUT | About | Guide |

## 3. Group vocabulary

- **Group**: a 1 dp `divider` hairline inset 14 dp both sides, then a Column padded 14/14 with
  `spacedBy 10`. No fill, outline or clip ("no panel, no outline, no second background").
- **Group heading**: uppercase, `textDim`, overline Bold, 1.4 letter spacing.
- **Overline** (section heading): uppercase, padding start 14 / top 18 / bottom 8.
- Positions from the pane edge: hairline x 14; row focus fill 14 … width−14; icon x 28; title x 62.

## 4. Components

| Constant | Value |
|---|---|
| Row minimum height | 74 dp |
| Row padding | 14 dp |
| Row icon | 20 dp |
| Switch | 52×30 dp track, 24 dp knob, travel 22 dp |
| Rail row | 41 dp |

- **SettingsRow** (not focusable; its controls are): optional hairline; Row min 74, padding
  14/10; icon 20 `textDim` + 14 (or a 34 dp spacer); title bodyLarge Bold (1 line); subtitle label
  `textDim` (2 lines); trailing controls after 14 dp.
- **SettingsValueRow** (focusable, opens a picker/editor): TvSurface min 74, shape medium,
  transparent at rest, focusScale 1, padding 14/10; icon, title, subtitle; value (body Bold,
  1 line) + 6 dp + chevron 18. Focused: fill `textPrimary`, content `background`, secondary
  `background α0.62`.
- **Switch**: outer TvSurface radius 15, padding 5 (62×40 dp). Off: track `surfaceRaised`, knob
  `textMuted`; on: outer `surfaceFocused`, track `focus`, knob `background`; focused: outer
  `textPrimary`. Knob and track animate with the default spring.
- **PickerRow**: TvSurface min 48, shape medium, padding 14/10; label body Bold; description label
  (2 lines); selected → `surfaceFocused` fill and an 18 dp tick.
- **TvActionButton**: rest `surface` / `textPrimary`; focused `textPrimary` fill, `background`
  text, scale 1.03, 10 dp shadow; selected `surfaceFocused` + `focus` text; danger `danger` text,
  `danger` fill when focused; disabled `surfaceSubtle` / `textDisabled`; shape small; padding
  compact 12×7 (caption, 16 dp icon), normal 18×11 (label, 18 dp icon).
- **TvUrlField** (compact): shape small, padding 12×8, label text; display state fills
  `textPrimary` when focused, `surface` otherwise; every Settings field is edit-on-click.

## 5. Dialogs

All are platform dialogs (`usePlatformDefaultWidth = false`); Back dismisses.

| Dialog | Width | Max height | Fill | Border | Padding | Title |
|---|---|---|---|---|---|---|
| Single picker | 560 | 620 | `panel`, medium | 1 dp `outline` | 18 | 18 sp Bold |
| Multi picker (with Done) | 560 | 620 | `panel` | 1 dp `outline` | 18 | 18 sp Bold |
| Time zone | 640 | 600 | `surface` | none | 18 | 18 sp Bold |
| Phone setup (QR) | 720 | — | `panel` | 1 dp `outline` | 20 | 20 sp Bold |

- Single picker: list `spacedBy 2`; opens scrolled to and focused on the current value; a choice
  closes it; focus returns to the row that opened it.
- Multi picker: toggles apply immediately; Done (compact, Check) at the end.
- Time zone: search field (max 40); "TV's own (City · UTC±h)" first, "RECENT" (Helsinki,
  Stockholm, Berlin, London, New York, UTC), "ALL ZONES" grouped by region (overline 11 sp Bold
  `textMuted`); rows are dense list rows "City" + "UTC+3" (U+2212 minus). The old list does not
  scroll to the current zone — the rebuild should.
- Phone setup: QR 280 dp (8 dp white quiet zone regardless of theme; bitmap 512 px, ZXing margin
  1, black on white); help 13 sp; the URL Bold; "Received from the phone: %1$s. Syncing it now."
  in `focus`; privacy note 12 sp; "Close the phone page" (focused). No-network message in `danger`.
- Backup, restore and diagnostics use the system document pickers (`.smbak` MIME
  `application/vnd.streammate.backup`, default name `sohva-tv-backup.smbak`; diagnostics
  `sohva-tv-diagnostics-yyyyMMdd-HHmm.txt`).
- No confirmation dialogs: delete source, clear guide data and remove profile act immediately;
  only Remote buttons has a two-step reset. (Rebuild: consider a confirm step for destructive
  actions — owner decision.)

## 6. Sections (defaults are fresh-install values)

### General

| Row | Control | Icon | Default |
|---|---|---|---|
| Interface language ("Restarts the app to apply") | picker | Info | System default |
| Interface size | picker | Aspect | Normal (100 %) |
| Color theme ("Applies immediately") | picker | StarOutline | Original |
| Channel numbers | switch | Channels | On |
| Time zone ("Used by the guide, catch-up and Sohva Sport") | time zone dialog | Epg | Follows the TV's own zone until one is chosen, shown as "TV's own (City · UTC±h)" (the data class default `Europe/Helsinki` is only a fallback; a fixed Helsinki default made a tester six hours away read every programme wrong) |
| Startup screen | picker | Home | Home |
| Playlist and EPG refresh interval | picker | Refresh | 24 hours |
| Reminders can open Sohva TV | opens system setting | Info | Allowed / Not allowed (shown when relevant) |

PROFILES group: Who is watching (value = active profile, "Everyone" by default); with more than one
profile: Ask who is watching at start (switch, on), What this profile may see, Live TV groups /
Film groups / Series groups ("Everything" or "N groups", multi picker "Groups for %1$s"), a PIN note
(`danger` "Set a parental PIN so a restricted profile cannot switch away or change this." when
missing); an add row (name field max 24 + "Add profile", up to 6 profiles); Remove a profile.

### Playlists (SOURCES)

List page: overline "SOURCES"; one value row per source (title = name, subtitle "M3U" / "Xtream"
plus " · reason" when failing, value "In use" / "Off", icon Channels or Link); empty "No playlists
yet. Add one below."; buttons "+ Add M3U source", "+ Add Xtream source", "Set up from a phone".

Source page (items are direct pane children, not in a group): overline "M3U · name" / "Xtream ·
name" + "All playlists" (first focus); M3U address field ("http(s)://provider/playlist.m3u", width
0.82, Link icon) and, when live TV is imported, XMLTV address (optional); or Xtream server
("http(s)://provider:port") + username + password (masked) side by side; Source name (field 320 dp;
default "IPTV n" / "Xtream n"); Source in use (switch, on); Connection limit (− / value / +, 1–16,
default 1); Remove source (danger, saved sources only); Content to import (Live TV / VOD only / TV
and VOD, default TV and VOD); EPG time correction (−30 min / value / +30 min, ±12 h); a FlowRow of
full-size buttons (max 3 per row): Save securely, Test address / Test connection, Sync everything,
Refresh playlist / channels, Refresh movies and series, Refresh programme guide.

Status group on both pages: status and health summary in `focus` (label); security note
"Credentials are encrypted on this device and portable backups are protected with your password"
(caption `textDim`).

### Playback

Group 1: Playback buffer (Media3 default / Low latency / Stability), Playback recovery (Standard /
Persistent), Skip step (10 s / 30 s / 1 min / 2 min; default 10 s), Match the display to the picture
(on), Continue to the next episode (on), Keep watching in a corner (off).
Group 2: Subtitle size (Follow the TV / Small / Normal / Large / Very large), colour (Follow the TV /
White / Yellow), background (Follow the TV / None / Shadow / Box).
Group 3 "VOD AUDIO AND SUBTITLES": Primary and secondary audio, primary and secondary subtitles
(Automatic + 11 languages; choosing the paired slot's language clears the pair).

### Remote buttons

Grid in a group: heading "REMOTE BUTTONS"; help "These work while watching. Menus and the channel
list keep their own buttons."; header row (110 dp spacer, "Press", "Hold"); twelve rows (button name
110 dp Bold; two compact cells): Up, Down, Left, Right, OK, Back (Back/Press fixed: "Dismiss, then
leave"), then "If your remote has them": CH+, CH−, Info, Audio, Captions, Menu; a preview line
("Up, Press: Channel list"); Reset to defaults → "Reset every button to its default?" Reset (danger)
/ Cancel. Editing a cell replaces the grid in place with grouped action buttons (Channels,
Information, Playback, Sound and picture, Leave), heading "UP, PRESS"; suffixes "· Live TV only",
"· Catch-up and films only".

### Library (METADATA)

Group 1: heading "METADATA AND IMAGES (OPTIONAL)" with the TMDB logo 137×18; switch "TMDB titles,
plots and artwork" (first focus); key field (masked, max 2,048) + Save key + Test TMDB; switch
"TVmaze series information"; status in `focus`.
Group 2: Metadata language (22 languages, default en-US); When a film has more than one version
(default "Whichever comes first"); Manage groups & content (opens the Library manager).
Group 3 "GROUPS OF YOUR OWN": list of custom groups or "None yet"; Add a group.
Group 4: Image cache (100 / 250 / 500 MB, default 250) with usage "%1$s in use" and Clear image cache.
Group 5 "MAINTENANCE": Clear metadata cache, TMDB, TVmaze (open websites).

### Accounts

The Trakt panel ([specs/51](../../specs/51-trakt.md)); only for unrestricted profiles.

### Sohva Sport

Group 1 "CHANNEL COUNTRY / LANGUAGE PRIORITY": help; codes field ("for example ES, EN, UK", max 80)
+ Save order (first focus).
Group 2 "SOHVA SPORT · DIRECT SPORTS DATA": description and status line; API-Sports key field
(masked, max 512) + Save key / Remove key; without a key "Save an API-Sports key to open sport and
competition selection."; with a key "Choose followed sports and competitions" → heading, Back, a
LazyRow of twelve sport buttons (`selected` = followed), include/remove toggle, competitions:
loading "Loading current leagues and cups…", error + Try again, or "Selected %1$d of %2$d
competitions", search (width 0.62, max 100), a 245 dp list of "Name · Country" buttons (followed
first).

### Parental controls

One row: title, PIN field (330 dp; "New 4–8 digit PIN" or "Current PIN for removal or
replacement"; digits only, max 8, masked), Enable / Remove or change PIN (danger).

### Backup & tools

Group 1: "Encrypted backup" (16 sp Bold), description, password field (min 8, max 128) + Save
backup + Restore backup, warning "The password is not stored and cannot be recovered."
Group 2 "MAINTENANCE": help + Clear all guide data (danger).

### About

UPDATES group: "Installed: %1$s"; status (phases: Not checked yet / Checking for a newer beta… /
This is the newest beta. / Sohva TV %1$s is available. / Downloading %1$s: N% / downloaded and
verified / Android needs permission / failed in `danger`); action (Check for updates, Download,
Install, Allow installs); "What's new in %1$s" + notes (max 4,000 characters); help. Then "About,
privacy and licences" (centred); TRANSLATIONS "Help translate Sohva TV" (opens the GitHub repo);
DIAGNOSTICS "Save diagnostics" + status.

## 7. Colour theme and interface size pickers

Plain pickers without swatches. Themes and descriptions: Original ("Midnight blue and cyan. The
default Sohva look."), Nordic Slate ("Charcoal surfaces with cool blue highlights."), Cozy Hearth
("Espresso surfaces with warm amber highlights."), Cyber Plum ("Deep violet surfaces with soft
purple highlights."), Nord ("Blue-grey surfaces with soft ice-blue highlights."), Everforest
("Forest-grey surfaces with sage-green highlights and warm cream text."), Kanagawa ("Deep ink
surfaces with dusty blue highlights and parchment text."). Applies immediately.
Interface size: "Normal (100 %)", "Compact (90 %)", "Small (80 %)", "Smaller (70 %)".
Languages: System default, English, Suomi, Español (borrador), Português (rascunho), Deutsch
(Entwurf), Svenska (utkast), Italiano (bozza).

## 8. Focus rules

- Choosing a section moves focus to its first row (General → language; Playlists → first source or
  "+ Add M3U source"; Playback → buffer; Remote → Up/Press; Library → TMDB switch; Sport → Save
  order; Parental → PIN field; Backup → password; About → the update action).
- The pane restores its last focused child when entered from the rail; the rail has no restorer
  (rebuild: return to the selected section).
- Pickers return focus to their row; the source page returns focus to its source row.
- Known flaw: closing the sport follow menu sends focus to "Save order" at the top, not the button
  that opened it.

## 9. States and flaws to fix

- No spinners; progress is text in the Playlists status line.
- Errors mostly render in `focus` (same as success). Only failed update, no network, missing PIN and
  danger buttons use `danger`.
- **Several messages are written but never visible** because the status line exists only in
  Playlists: refresh-interval confirmation, parental saved/removed/"Incorrect PIN code", backup
  saved/restored/errors, sport key and order saved, "Image cache cleared". The rebuild gives every
  section its own status line.
- The QR bitmap (512 px ARGB) is built on the main thread when the phone state changes: build it
  off the main thread.
