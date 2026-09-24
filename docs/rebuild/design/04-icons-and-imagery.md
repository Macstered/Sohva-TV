# Icons and imagery

> How Sohva TV beta 23 uses its bundled assets and the artwork it loads at run time, and the
> rules the rebuild follows so the same look costs less. Files are in [assets/](../assets/README.md);
> colours and type are in [01-design-system.md](01-design-system.md); per-screen sizes are in
> [03-screen-layouts.md](03-screen-layouts.md).

## 1. Brand

| Element | Asset | Where and how big |
|---|---|---|
| Mark (sofa + TV + play) | `sohva_mark.png` | Launch screen only: 188×188 dp, centred, 74 dp above the screen centre |
| Wordmark ("Sohva" white, "TV" cyan→blue) | `sohva_wordmark.png` | Launch screen only: 273×56 dp, 18 dp below the mark |
| Home title "Sohva TV" | live text, not an image | Top left of Home: "Sohva" in the primary text colour, "TV" in the theme's focus/accent colour (cyan `#2DE2E6`-family in Original) |
| TV launcher banner | `app_banner.png` (320×180 dp at xhdpi) | Android TV launcher row; mark + wordmark on near-black |
| Launcher icon | adaptive `ic_launcher` (foreground PNG on `#05070D`), legacy `ic_launcher.png` | Phone-style launchers, app info, reminder notification large icon |

The launch composition must be pixel-identical between the window background
(`launch_background.xml`, drawn by the system before the process runs) and the first Compose
frame, so the hand-over is invisible. Beta 20 removed a brief flash of the app name at start-up;
keep the launch screen free of text other than the wordmark image.

Colours of the window-background gradient: `#080C15` top → `#05070D` centre → `#04060A` bottom,
the Original theme's background family, because the chosen colour theme is not known before the
process starts. The Compose launch screen that follows draws the standard screen background
(the theme's gradient plus two faint focus-coloured washes, design/01), so with Original the
hand-over is invisible; with another theme the ground shifts to that theme's colours at the
hand-over. The rebuild may read the theme from a small synchronous preference (as the artwork
cache limit and the language already are) and pick a matching window background, to make the
hand-over invisible for every theme.

## 2. Icon system

Two families, both 24×24 dp vectors drawn white and tinted at run time:

1. **Navigation icons** (`ic_sohva_nav_*`, 13 drawings): outline style, 1.75-unit strokes, round
   caps and joins, from the owner's `sohva-tv-icons` set. Used only on navigation surfaces: the
   Home rail (Home/sofa, Live TV, Sohva Sport, Movies, Series, Search, Discover, Settings) and the
   Discover rail (Home/house, Library, Search, Explore/compass, Addons & setup/puzzle, Back to
   Home). The profile entry on the rail has no drawing of its own in the set and keeps its
   existing treatment.
2. **Action and UI icons** (`ic_tv_*`, 30 glyphs): filled Material-style glyphs for actions and
   states — player controls, favourite stars, lock, refresh, save, delete, check, chevrons, info,
   link, key, search, settings, channels, guide/EPG, target, stats, aspect, audio, subtitles.

Rules:

- Tint with theme tokens: content colour when unfocused, the focused-content colour when the
  container is focused (dark on the light focus fill in the Original theme). Never ship coloured
  copies of an icon.
- Sizes in use: 24 dp on rails and buttons; 18 dp inside compact buttons and list rows; 16 dp in
  metadata lines; 20 dp for small status glyphs.
- Icons that sit next to a visible label are decorative for accessibility
  (`clearAndSetSemantics {}`); icon-only controls carry the label as their content description.
  Fewer semantics nodes also cut the per-frame cost when an accessibility service is running.
- The rebuild keeps both families as vector drawables. Vectors are rasterised once per size and
  cached by the platform, so they are cheaper than PNGs at these sizes and scale to any density.

## 3. Artwork loaded at run time

None of this is bundled; it comes from the viewer's sources and services. The rebuild must keep
the same shapes and placements, and load each at the size it is drawn.

| Artwork | Source | Shape on screen | Decode size in beta 23 | Fallback |
|---|---|---|---|---|
| Film/series poster (Movies, Series walls) | Provider VOD (`stream_icon`, `cover`), TMDB | 2:3, rounded card | 192×288 px, inexact precision, **no crossfade** | Surface tile with up to two initials |
| Discover poster | Addon `poster` | 2:3 | 256×384 px, inexact, 120 ms crossfade | Surface tile |
| Home hero art | TMDB backdrop, provider art, addon background, programme art | Full-bleed 16:9 behind the hero, faded into the background | ≤ 1920×1080 px, `RGB_565`; waits 180 ms for focus to settle, crossfades 250 ms | `home_backdrop_live_tv.webp` |
| Home landscape card art | Same sources as the hero | 16:9 card | card size | Initials tile |
| Detail page backdrop | TMDB / provider / addon | Full-bleed behind the details | screen size | Background gradient only |
| Channel logo | `tvg-logo`, Xtream `stream_icon`, viewer's own logo | Square-ish tile: 34 dp on Home, rail and guide sizes in design/03 | tile size | Rounded tile with two initials of the channel name |
| Team crest / competition logo | API-Sports | Contained square: 38 dp on Home sport cards; 150 dp at 55 % opacity as a hero backdrop | tile size | Initials |
| Cast photo | TMDB, addon | 52 dp circle | circle size | Initials in a circle |
| TMDB attribution logo | bundled SVG | 137×18 dp in Settings › Metadata heading; 205×28 dp in Legal | vector | — |
| QR codes | generated on device (ZXing) | 220 dp (addon phone page), 200 dp (Stremio import), 168 dp (Trakt sign-in) | generated at size | — |

### Placeholder initials

When artwork is missing or fails, a tile shows up to two letters. Only words that start with a
letter count, so years, season markers and separators never become initials ("Ben 10" → "BE",
not "B1"); a single word gives its first two letters; two or more words give the first letter of
each of the first two. Separators: space, `.`, `-`, `:`, `_`, `·`, `/`. The current app has two
copies of this rule that differ when no word starts with a letter (Home shows nothing; the
catalogue falls back to the first two characters). The rebuild uses one function: the catalogue
behaviour.

### Image loading budget (current values, and the rebuild's rule)

| Setting | Beta 23 | Why |
|---|---|---|
| Memory cache | 8 % of the app's memory class | A phone-style percentage kept hundreds of posters and pushed the Shield's native heap above 300 MB |
| Disk cache | Viewer's choice: 100 / 250 (default) / 500 MB, in `cacheDir/catalogue_artwork`; applies from the next start | Was a flat 1 GB — too much of a box's storage |
| Parallel decodes | 2 | Twenty simultaneous JPEG decodes saturated four cores and pushed frame p90 above 100 ms |
| Crossfade | 140 ms default; posters none; Discover posters 120 ms; hero 250 ms | Crossfades cost fill rate on a Mali-G31; the wall skips them |

For the rebuild on the low-end class: keep these limits, request `RGB_565` for opaque artwork
(posters, backdrops, hero) — half the memory of ARGB — keep ARGB only for logos and crests that
need transparency, and drop crossfades entirely when the device reports low RAM or the viewer
holds a key (see [plan/07-performance.md](../plan/07-performance.md)).

## 4. Backgrounds and the Home backdrop

- Screens draw the theme's background as a vertical gradient with soft glows (exact stops in
  design/01). In beta 23 these are drawn once into a cached layer; before that, every screen
  repainted its static background four to five times per frame, which the Mali-class GPU could not
  afford. The rebuild renders static backgrounds once per theme and size (a cached layer or a
  pre-rendered bitmap) and never animates them.
- The background grain texture was removed in beta 17 and must not come back.
- `home_backdrop_live_tv.webp` (1920×1080, 24 KiB) is Home's backdrop when the focused card has
  no art of its own. The other five `home_backdrop_*` files are unused leftovers (see
  assets/README.md).

## 5. Attribution and third-party imagery

- **TMDB:** the unmodified TMDB logo appears in Settings › Metadata and in the legal screen with
  the notice "This product uses the TMDB API but is not endorsed or certified by TMDB". Keep both
  placements.
- **TVmaze:** data is CC BY-SA; the legal screen links to TVmaze and its licence.
- **API-Sports crests and logos:** shown as delivered; the app claims no rights to them and does
  not cache them longer than the image cache does.
- **Provider logos and posters:** the viewer's own sources; never bundled, never uploaded.

## 6. Demo and test art

The `demo` build renders the production screens against fictional content for screenshots. Its
generated art (`assets/flavors/demo/`) was made with prompts that asked for full-bleed original
artwork with no text, logos, brands, recognisable people, copyrighted characters or watermarks:

- `demo_movie_signal.png` — science-fiction drama poster; radio astronomer, antenna array,
  navy-to-amber dawn.
- `demo_movie_lighthouse.png` — mystery-adventure poster; isolated lighthouse, storm, teal and
  pale-gold light.
- `demo_series_harbor.png` — prestige mystery poster; rain-soaked ferry terminal, investigator in
  a yellow coat, cobalt and amber light.
- `demo_series_north.png` — optimistic adventure poster; arctic glass observatory and aurora,
  emerald and violet light.
- `demo_live_football.png` — fictional evening football broadcast; generic amber and teal kits,
  no score graphics, crests, sponsors or real stadium.
- Four vector marks (Meridian, Northstar, Pulse, Summit) stand in for channels and teams.

The rebuild keeps a demo build: it is the only way to capture complete screenshots without real
provider data, and it must never ship its art in release builds. The Lab benchmark build badges
its banner and icon with an amber "LAB" plate (`assets/flavors/lab/`) so it is never mistaken for
the real app.

## 7. Rules for new artwork

1. Dark-first: every image must sit on the near-black background without a visible box.
2. No text baked into images except the wordmark; all text is live and translatable.
3. Deliver vectors for anything flat (icons, marks); raster only for photographic art.
4. Raster art ships as WebP, at xhdpi (the TV density) and xxxhdpi (4K UIs), nothing else.
5. Budget: a new bundled image adds at most 50 KiB to the APK unless the owner approves more.
6. Keep the icon grammar: 24-unit grid, 1.75 strokes, round caps for navigation; filled glyphs for
   actions.
