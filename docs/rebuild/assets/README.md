# Sohva TV visual assets

Every visual asset that Sohva TV `0.1.0-beta.23` (Android build 57, public commit `efab52a`)
ships, plus the owner-supplied source files they were made from. Copied on 24 September 2026.

- Open [`preview.html`](preview.html) in a browser for a contact sheet of everything here.
- [`inventory.json`](inventory.json) lists every file with its origin path, byte size, pixel size
  and SHA-256, so any copy can be checked against the original.
- How the assets are used on screen, and the rules for new artwork, are in
  [design/04-icons-and-imagery.md](../design/04-icons-and-imagery.md).

The app bundles **no fonts** (it uses the system sans-serif, Roboto on Android TV) and **no
photos** of its own: posters, backdrops, channel logos and team crests come from the viewer's
sources, TMDB/TVmaze, addons and API-Sports at run time. Everything else it draws (gradients,
cards, focus rings, the guide grid, placeholder initials) is drawn in code; its exact values are in
[design/01-design-system.md](../design/01-design-system.md).

## Folder map

| Folder | What it is | Files | Size |
|---|---|---|---|
| `res/` | Drop-in Android resources exactly as the production APK ships them (the `app` and `core` modules merged into one tree) | 76 | 1.7 MiB |
| `apk-assets/` | Files from the APK's `assets/` folder | 2 | 6 KiB |
| `svg-exports/` | Plain SVG versions of every Android vector drawable, generated for viewing and design tools | 47 | 15 KiB |
| `flavors/demo/` | Art for the `demo` build (fictional content for screenshots) | 9 | 11.9 MiB |
| `flavors/lab/` | Launcher badge art for the `lab` benchmark build | 3 | 2 KiB |
| `source/navigation-icons/` | The owner-supplied navigation icon set (SVG, PNG light/dark at 24/48/96 px, sprite, manifest, README, preview) | 102 | 187 KiB |
| `source/logo/` | The logo master the brand PNGs were cut from (`logo_2.png`, 1254×1254, 14 Sept 2026) | 1 | 1.1 MiB |
| `source/historical-sportmate/` | **Not used.** Early SportMate concept art and logo (23 Aug 2026), kept only for provenance | 5 | 6.5 MiB |

## Production assets and where the app uses them

### Brand

| Asset | Densities (px) | Used by | Notes |
|---|---|---|---|
| `res/drawable-*/sohva_mark.png` | mdpi 188², hdpi 282², xhdpi 376², xxhdpi 564², xxxhdpi 752² | Launch screen (`launch_background.xml` window background and the Compose launch screen, drawn at 188 dp) | Sofa-and-TV mark in blue–cyan gradient on transparent. xhdpi is the size a 1080p TV (320 dpi) uses. |
| `res/drawable-*/sohva_wordmark.png` | mdpi 273×56 … xxxhdpi 1092×224 | Launch screen, 273×56 dp, 18 dp below the mark | "Sohva" white, "TV" cyan-to-blue gradient. On Home the "Sohva TV" title is live text, not this image. |
| `res/drawable-*/app_banner.png` | xhdpi 320×180, xxhdpi 480×270, xxxhdpi 640×360 | `android:banner` in the manifest: the Android TV launcher tile | Mark + wordmark on near-black. |
| `res/mipmap-*/ic_launcher.png` | mdpi 80², hdpi 120², xhdpi 160², xxhdpi 240², xxxhdpi 320² | Legacy launcher icon (API < 26) and the reminder notification's large icon | |
| `res/mipmap-*/sohva_launcher_foreground.png` | mdpi 108² … xxxhdpi 432² | Foreground of the adaptive icon `mipmap-anydpi-v26/ic_launcher.xml` (background colour `#05070D`) | |

### Launch screen

`res/drawable/launch_background.xml` is the window background while the process starts: a
vertical gradient `#080C15` (top) → `#05070D` (centre) → `#04060A` (bottom), the mark 188×188 dp
centred 74 dp above centre, and the wordmark 273×56 dp placed 206 dp below the top of the centred
group. The Compose launch screen repeats the same composition at the same positions, so the hand-over
changes nothing on screen. Colours are in `res/values/colors.xml`; the window theme
(`Theme.StreamMate`, system sans-serif, accent `#2DE2E6`) is in `res/values/themes.xml`.

### Home backdrop

| Asset | Pixels | Used by |
|---|---|---|
| `res/drawable-nodpi/home_backdrop_live_tv.webp` | 1920×1080, 24 KiB | Home screen backdrop behind the hero (teal bokeh on near-black) |
| `home_backdrop_movies/search/series/settings/sportmate.webp` | 384×384 each | **Shipped but not referenced by any code in beta 23.** Leftovers from the Home destinations before the beta 14 Home rework. Do not carry them into the rebuild unless a screen is designed to use them. |

### Icons (Android vector drawables, 24×24 dp, white, tinted at run time)

Navigation icons, `res/drawable/ic_sohva_nav_*.xml` (13 drawings, converted from the owner's set
in `source/navigation-icons/`; 1.75-unit rounded strokes). Mapped in `SohvaNavigationIcons`:

| Drawable | Navigation item | Drawing |
|---|---|---|
| `ic_sohva_nav_front_page` | Home (main rail) | Sofa |
| `ic_sohva_nav_live_tv` | Live TV / guide | Television with live dot |
| `ic_sohva_nav_sport` | Sohva Sport | Trophy |
| `ic_sohva_nav_movies` | Movies | Clapperboard |
| `ic_sohva_nav_series` | Series | Stacked episodes |
| `ic_sohva_nav_search` | Search (main rail and inside Discover) | Magnifier |
| `ic_sohva_nav_discover` | Discover | Play with discovery sparkle |
| `ic_sohva_nav_settings` | Settings | Gear |
| `ic_sohva_nav_addon_home` | Discover › Home | House |
| `ic_sohva_nav_library` | Discover › Library | Books on a shelf |
| `ic_sohva_nav_explore` | Discover › Explore (filters) | Compass |
| `ic_sohva_nav_addons` | Discover › Addons & setup | Puzzle piece |
| `ic_sohva_nav_back_to_home` | Discover › Back to Home | House with return arrow |

Action and UI icons, `res/drawable/ic_tv_*.xml` (30 filled Material-style glyphs, `android:tint`
white). Mapped in `TvIcons` (`core/.../feature/common/TvUiComponents.kt`): Home, Back, Aspect,
Audio, Subtitles, Stats, ChevronRight, ChevronDown, Lock, Link, Key, Refresh, Check, Play, Pause,
Save, Settings, Delete, Close, Channels, Target, Guide, Epg, Info, Search, Replay, Forward, Rewind,
Star, StarOutline. Examples of use: player controls (play/pause/rewind/forward/replay, audio,
subtitles, aspect, stats), guide actions (star/star outline for Favourite, search for Find
programme), Settings rail (settings = General, channels = Sources, play = Playback, aspect =
Remote, info = Metadata, link = Accounts, target = Sport, lock = Parental, save = Backup, guide =
About).

Normal and focused icon colours come from the theme at run time; no coloured copies exist.

### APK assets

| File | Used by |
|---|---|
| `apk-assets/tmdb_attribution.svg` | TMDB attribution logo (TMDB's own mark, gradient `#90CEA1` → `#3CBEC9` → `#00B3E5`) shown in Settings › Metadata and in the legal information screen, as TMDB's API terms require. It is TMDB's trademark: use it only for attribution, unmodified. |
| `apk-assets/theme-licenses.txt` | MIT notices for the Nord, Everforest and Kanagawa palettes that three colour themes adapt. Bundled in the APK to satisfy the licence, but **not displayed** by beta 23 (only a code comment and the third-party notices refer to it). Whether the rebuild's legal screen shows it is an open question in [specs/72](../specs/72-updates-about-diagnostics.md). |

## Build-flavour art

- `flavors/demo/res/drawable-nodpi/demo_*.png` — five generated, text-free posters and a
  fictional football frame (1024×1536 posters, 1672×941 frame) for the `demo` screenshot build.
  Their prompts are recorded in the old repo's `docs/demo-screenshot-mode.md` (summarised in
  [design/04](../design/04-icons-and-imagery.md)). They are large (2.3–2.7 MiB each) because they
  never ship in a release APK.
- `flavors/demo/res/drawable/demo_mark_*.xml` — four fictional channel and team marks
  (Meridian, Northstar, Pulse, Summit), 96×96 dp vectors.
- `flavors/lab/res/drawable/lab_badge.xml`, `lab_banner.xml`, `lab_icon.xml` — an amber "LAB"
  badge laid over the normal banner and launcher foreground, so the benchmark build is never
  mistaken for the real app.

## Lightweight notes for the rebuild

- The production image set is 1.7 MiB of a 7.8 MiB APK. Android TV UIs run at xhdpi (1080p at
  320 dpi) on nearly every device, including 4K ones. Ship the brand PNGs at xhdpi and xxxhdpi only
  (drop mdpi/hdpi/xxhdpi: about 0.6 MiB), and store them as lossless WebP (typically a third
  smaller). Keep the launcher mipmaps the platform requires.
- Drop the five unreferenced Home backdrops (83 KiB) and enable resource shrinking so unreferenced
  resources can never ship again.
- Keep icons as vectors: 43 icons cost 24 KiB. Rasterised vector caches are cheap at 24–32 dp.
- Decode `home_backdrop_live_tv.webp` once at screen size and draw it as a static layer; never
  re-decode or scale it per frame (see [plan/07-performance.md](../plan/07-performance.md)).
- The demo art must stay out of release builds (it lives only in the demo source set).

## Provenance and licences

- Brand PNGs, banner and launcher icons: cut from `source/logo/logo_2.png`, the owner's Sohva TV
  logo introduced in beta 14 (14 September 2026).
- Navigation icons: the owner's `sohva-tv-icons` set (see its own `README.md` and `manifest.json`).
- `ic_tv_*` icons: most paths match Google's Material Icons glyphs (Apache 2.0), for example
  `ic_tv_play` is `play_arrow` and `ic_tv_chevron_down` is `expand_more`; a few, such as `ic_tv_epg`,
  are simple custom shapes. Beta 23's third-party notices do not list them separately; the
  rebuild's notices should name Material Icons under Apache 2.0.
- TMDB logo: TMDB's trademark, attribution use only.
- Theme palettes: MIT, see `apk-assets/theme-licenses.txt`.
- Demo art: generated for the project with no text, logos, brands, real people or watermarks.
- `source/historical-sportmate/`: the pre-rebrand SportMate logo and four concept screens. They
  are not Sohva TV assets and must not be used as such.
