# Design system

> Colour themes, tokens, type, spacing, shapes, focus, motion and the rules for drawing all of it
> cheaply. Values are copied from Sohva TV 0.1.0-beta.23 (build 57, public tree `efab52a`), read
> 24 September 2026: `core/.../app/ColorTheme.kt`, `StreamMateTheme.kt`, `StreamMateContentColors.kt`,
> `InterfaceScale.kt`, `core/.../feature/common/TvUiComponents.kt`, `FocusScroll.kt`,
> `FocusRequests.kt`, `core/src/main/assets/theme-licenses.txt`, `app/src/main/res/values/colors.xml`
> and `themes.xml`, plus the call sites named below. Components built from these tokens are in
> [02-components.md](02-components.md); per-screen measurements in [03-screen-layouts.md](03-screen-layouts.md)
> and [screens/](screens/); icons and artwork in [04-icons-and-imagery.md](04-icons-and-imagery.md).
> Reference canvas everywhere: 960 × 540 dp (a 1920 × 1080 panel at density 2, Interface size Normal).

## 1. Principles the tokens encode

1. **One focus signal: the off-white fill.** A focused control fills with `textPrimary` and its
   content inverts to `background`. Nothing else in the app is drawn that way at rest, so "white
   fill means focus" holds on every screen. Primary buttons are therefore *not* white at rest.
2. **Selected is not focused.** Selection is a quiet tint (`surfaceFocused`) plus a marker (accent
   bar, bold weight, cyan text, orange underline, tick). It must never look like the focus fill.
3. **No resting outlines.** Hierarchy is carried by fill steps, type scale and spacing. `outline`
   exists for "the rare border that carries meaning" (dial panel, picker dialogs), never as a
   resting frame for a control.
4. **Rings only where a fill cannot reach.** Artwork, stills and posters show focus with a 3 dp
   `textPrimary` ring drawn inside their bounds; their fill and content stay as they were.
5. **Dark first.** Every theme is a dark theme. Video and letterboxing stay black whatever the theme.
6. **Only colour changes with the theme.** Typography, spacing and shapes are the same in all
   seven themes (`StreamMateTheme` accepts other values but the app never passes any).

## 2. Theme plumbing

- `StreamMateTheme(palette)` provides four **static** composition locals (palette, typography,
  shapes, spacing), read through `StreamMateThemeTokens.palette / typography / shapes / spacing`.
  A theme change therefore recomposes the whole tree once; nothing reads the palette per frame.
- It also wraps tv-material's `MaterialTheme` with a dark colour scheme: `primary = focus`,
  `secondary = accent`, `error = danger`, `background = background`, `surface = surface`,
  `onPrimary = background`, `onBackground = onSurface = textPrimary`; and sets
  `LocalContentColor = textPrimary`. No other Material roles are used.
- The host applies it once: `StreamMateTheme(palette = appPreferences.colorTheme.palette)` around
  the whole app (`StreamMateApp.kt`). Screens switch with a plain `when`; there is no transition.
- The launch screen is always drawn with the default (Original) palette at device density
  ([screens/home-and-shell.md §1–2](screens/home-and-shell.md#1-activity-and-window)).
- Window (before Compose): `Theme.StreamMate`, parent `android:style/Theme.Material.NoActionBar`;
  `android:fontFamily` = `sans`; `windowBackground` = `@drawable/launch_background`; `colorAccent`
  `#2DE2E6`; status and navigation bar `#05070D`; `windowLightStatusBar` false;
  `windowActionModeOverlay` true. Static colours in `colors.xml`: `sportmate_background #05070D`,
  `sportmate_background_top #080C15`, `sportmate_background_bottom #04060A`, `sportmate_focus
  #2DE2E6`. Two frames after the first Compose frame the window background is replaced by a flat
  `#05070D` `ColorDrawable` so the launch picture is not drawn under every frame.

## 3. The seven colour themes

Preference: DataStore string key `color_theme`, value = stored id; backup JSON field `colorTheme`
(same id). Unknown or missing id → Original. Picker: Settings › General › Color theme
(`color_theme_title` "Color theme", help `color_theme_help` "Changes the interface colors. Applies
immediately."). Plain list, no swatches; applies immediately.

| # | Theme (label key, EN) | Stored id | Description key → EN | Base inputs (background / surface / textPrimary / textMuted / textDim / focus / secondaryGlow) |
|---|---|---|---|---|
| 1 | `color_theme_original` Original | `original` | `…_original_description` "Midnight blue and cyan. The default Sohva look." | hand-set palette, §4 |
| 2 | `color_theme_nordic_slate` Nordic Slate | `nordic_slate` | "Charcoal surfaces with cool blue highlights." | `#12151A` / `#1A202C` / `#F8FAFC` / `#94A3B8` / `#8998AE` / `#4E95D9` / `#3C5C8C` |
| 3 | `color_theme_cozy_hearth` Cozy Hearth | `cozy_hearth` | "Espresso surfaces with warm amber highlights." | `#16120E` / `#231C16` / `#FFFBEB` / `#A8A29E` / `#9C9690` / `#F59E0B` / `#8B542B` |
| 4 | `color_theme_cyber_plum` Cyber Plum | `cyber_plum` | "Deep violet surfaces with soft purple highlights." | `#130E1C` / `#1F162E` / `#FAF5FF` / `#A899B8` / `#9C8DAD` / `#C084FC` / `#653498` |
| 5 | `color_theme_nord` Nord | `nord` | "Blue-grey surfaces with soft ice-blue highlights." | `#151A21` / `#222A35` / `#ECEFF4` / `#A5B1C2` / `#98A5B8` / `#88C0D0` / `#5E81AC` |
| 6 | `color_theme_everforest` Everforest | `everforest` | "Forest-grey surfaces with sage-green highlights and warm cream text." | `#151B18` / `#202B25` / `#E8E3D5` / `#A6B3A5` / `#98A794` / `#A7C080` / `#536D59` |
| 7 | `color_theme_kanagawa` Kanagawa | `kanagawa` | "Deep ink surfaces with dusty blue highlights and parchment text." | `#16161D` / `#232330` / `#DCD7BA` / `#AAA6BD` / `#9C99AF` / `#7E9CD8` / `#4C628A` |

Labels 2–7 are `translatable="false"` (proper names); Original is translated. Enum order =
picker order. Code comment on Cyber Plum: the brighter violet was chosen so it "remains readable in
small labels on raised surfaces".

**Licences.** Nord, Everforest and Kanagawa are "adapted from Nord, Everforest Dark Hard and
Kanagawa Wave. Surfaces are darkened and secondary text lifted for TV viewing; upstream accents
remain." The MIT terms ship as `assets/theme-licenses.txt` with the copyright lines
"Copyright (c) 2016-present Sven Greb" (Nord, `https://github.com/nordtheme/nord`), "Copyright (c)
2019 sainnhe" (Everforest, `https://github.com/sainnhe/everforest`) and "Copyright (c) 2021 Tommaso
Laurenzi" (Kanagawa, `https://github.com/rebelot/kanagawa.nvim`), followed by the MIT licence
text. The rebuild must bundle the same file and keep the mention in THIRD_PARTY_NOTICES. The legal
screen does not display it in beta 23 (see Open questions).

## 4. Palette tokens (Original values and roles)

`StreamMatePalette` in `StreamMateTheme.kt`. Alpha values are shown as the 8-bit ARGB the code
produces (`Color.copy(alpha)` rounds `alpha × 255 + 0.5`).

| Token | Original | Role (code doc) | Where it is used (beta 23) |
|---|---|---|---|
| `background` | `#05070D` | "The ground everything sits on, and the ink that shows on a focused fill." | Middle stop of the ground gradient; content colour on every focused fill; scrims for sheets (α0.86), match hub (α0.94), player chrome, player channel lists, Home rail, Home/details backdrop scrims, Discover backdrop and rail; buffering pill (α0.72); knob of an ON switch; tick inside watched badges; selection bar on a focused list row |
| `backgroundTop` | `#080C15` | "Secondary ground for rails and the top of the ambient wash." | Top stop of the ground gradient; first stop of the catalogue details diagonal gradient |
| `backgroundBottom` | `#04060A` | "Deepest ground, used for scrims that must bury artwork." | Bottom stop of the ground; flat fill of the profile picker and of the host before start-up routing; end stop of the live-player info wash (α0.52) |
| `panel` | `#0A0F1A` | "Opaque panel for overlays that sit on video or artwork and must obscure it." | Options sheets, picker dialogs, phone-setup dialog, match picker, text-edit dialog, Discover choice/confirm dialogs; channel dial (α0.94), score ticker (α0.92), track picker and quick actions (α0.97), subtitle sync (α0.96), series loading pill (α0.94) |
| `surfaceSubtle` | `#FFFFFF` α0.035 = `#09FFFFFF` | Surface ladder step 1, "the quietest fill that is still a surface". | Guide past/future programme cells and loading bars; resting sport/channel/match cards; disabled button fill; track-picker unselected rows; channel-editor rows; poster tile gradient top (catalogue); landscape card art box |
| `surface` | `#FFFFFF` α0.06 = `#0FFFFFFF` | Surface ladder step 2, "the default resting fill for a control". | Resting `TvActionButton`; airing programme cell; key-hint chips; logo tiles; text-field display fill; Search result rows; legal sections; reminder, programme-action, time-zone and library-manager dialogs; hub panels; Discover source cards, cast avatars and loading tiles; player icon actions at rest; disabled switch track |
| `surfaceRaised` | `#FFFFFF` α0.10 = `#1AFFFFFF` | Step 3, "an elevated block inside an already-raised one". | Home "Guide" button and details primary action at rest; progress tracks (Home hero, details); switch OFF track; player channel-logo tile; selected track-picker row; channel-editor selected row and logo tile; Search thumbnail |
| `surfaceFocused` | `#FFFFFF` α0.14 = `#24FFFFFF` | "Selected-but-unfocused tint. Focus itself is the white fill, not this." | Selected `TvSurface`/`TvActionButton`/`TvListRow`; guide channel cell of the selected row; outer switch when ON; selected picker row; player channel row while the groups pane is active; library-manager focused row (divergent, see §12) |
| `divider` | `#FFFFFF` α0.07 = `#12FFFFFF` | "Hairline between rows and columns." | `TvListRow` and Settings hairlines; match-hub timeline axis and divider; Discover import footer rule |
| `outline` | `#FFFFFF` α0.07 = `#12FFFFFF` | "Reserved for the rare border that carries meaning; never a resting outline." | Channel dial border; single/multi picker and phone-setup dialog borders; series loading pill; hub team circle (α0.55 of it); channel-editor panels (α0.56 and α0.72 of it) |
| `focus` | `#2DE2E6` | "Focus and primary accent." | "TV" in the brand; Home kickers; every progress fill; selection bars; text cursor; airing times; Search type labels; watched badges; `PRIMARY` chips; switch ON track; first background wash; buffering arc; hub "MATCH EVENTS" and goal markers; live score on match cards; Settings status lines; legal section titles; player initials |
| `secondaryGlow` | `#2276D2` | "Ambient counter-wash on the background only." | Second background wash only |
| `accent` | `#FF8A4C` | "SportMate orange: the sport role." | "Sport" in the Sohva Sport brand; selected sport-tab underline; favourite star; postponed/interrupted status; card incidents; hub "STREAMS"; `ACCENT` chips (×N copies, HDR); guide relative-day when paged; channel-editor "hidden" suffix; library-manager focus border and ticks (divergent) |
| `danger` | `#FF3B5C` | "Live and destructive." | Live dots, `LIVE` chip fill, now-line, live badge, hub live score, score-ticker live score; danger buttons (text at rest, fill when focused); cancelled status; update/network/PIN errors; failed source health; Discover failure texts |
| `rating` | `#FFC857` | "Ratings and scores." | `RATING` chips ("TMDB 7.8"), star + score on details pages |
| `textPrimary` | `#F2F5F9` | Primary text. | Titles and values; **the focus fill**; focus rings; progress thumb; tracks at α0.18–0.20 |
| `textMuted` | `#93A1B5` | Secondary text. | Metadata, descriptions, resting `TvSurface` content, status lines, hints |
| `textDim` | `#5B6981` | "Tertiary text: metadata that should recede but stay readable." | Overline headings, counts, key-hint labels, Settings icons and subtitles, clock on Sohva Sport, secondary content at rest |
| `textDisabled` | `#5B6981` | "Content of a control that cannot be focused." | Disabled content; breadcrumb separator "›" |
| `scrim` | `#000000` | "Wash over artwork or video. Opacity is chosen by the overlay." | Track picker / quick actions (α 166/255 = `#A6000000`), text-edit dialog (α0.72), seek pill (α0.6), Discover subtitle picker (α0.88), episode-card foot (α0.85), Discover player foot (α0.95), Discover loading gradient |
| `onScrim` | `#FFFFFF` | "Text over a scrim, independent of the app's ordinary surfaces." | Seek feedback, Discover loading and failure texts, fallback player chrome title |
| `dangerSurface` | `#7A1624` | "Error banner over playback, with its own contrasting foreground." | Player error / reconnect banner (α0.8) |
| `onDangerSurface` | `#FFFFFF` | Foreground on `dangerSurface`. | Banner text |
| `playerInfoSurface` | `#252A31` | "Middle stop of the live-player information wash." | Live info wash stop at 0.42 (α0.16) |
| `genres`, `sports`, `profiles` | §7 | "Content identities are separate from the app's primary accent." | §7 |

Legacy aliases exist only for source compatibility (`StreamMateBackground`, `StreamMateSurface`,
`StreamMateSurfaceRaised`, `StreamMateCyan`, `StreamMateOrange`, `StreamMateRed`,
`StreamMateMuted` = Original values). They ignore the theme; the rebuild does not carry them over.

## 5. How the six derived themes are built (`darkPalette()`)

Every theme except Original is `StreamMateDefaultPalette.copy(...)` with these overrides; all other
tokens (accent, danger, rating, scrim, onScrim, dangerSurface, onDangerSurface, genres, sports,
profiles) keep the Original values ("sports, ratings, live/error colours, and black video scrims
retain their existing meaning across themes").

```
background        = background
backgroundTop     = lerp(background, surface, 0.5)
backgroundBottom  = lerp(background, Black, 0.3)
panel             = surface
surfaceSubtle     = lerp(background, surface, 0.5)      // same value as backgroundTop
surface           = surface
surfaceRaised     = lerp(surface, textPrimary, 0.025)
surfaceFocused    = lerp(surface, textPrimary, 0.05)
divider           = textPrimary @ α0.07
outline           = textPrimary @ α0.10                  // Original's outline is α0.07
textPrimary/textMuted/textDim = as given
textDisabled      = textDim @ α0.65                      // Original's is opaque #5B6981
focus, secondaryGlow = as given
playerInfoSurface = surface
```

`lerp` is Compose's `Color` lerp, which **interpolates in Oklab**, not in sRGB. Consequences the
rebuild must reproduce: the derived surfaces are **opaque** (Original's ladder is translucent
white), and `backgroundBottom` is much darker than a naive sRGB mix (Nordic Slate `#07080C` in Oklab
versus `#0D0F12` in sRGB). The rebuild should hard-code the resolved values below rather than
compute them at run time; they were computed with the Oklab formulas and may differ by ±1 in a
channel from a device (Compose packs the Oklab intermediate in half-float precision).

### 5.1 Resolved palettes

ARGB with alpha is written `#AARRGGBB`; everything else is opaque `#RRGGBB`.

| Token | Original | Nordic Slate | Cozy Hearth | Cyber Plum | Nord | Everforest | Kanagawa |
|---|---|---|---|---|---|---|---|
| background | `#05070D` | `#12151A` | `#16120E` | `#130E1C` | `#151A21` | `#151B18` | `#16161D` |
| backgroundTop | `#080C15` | `#161A23` | `#1C1712` | `#191225` | `#1B222B` | `#1A231E` | `#1C1C26` |
| backgroundBottom | `#04060A` | `#07080C` | `#090705` | `#07050D` | `#080C10` | `#080C0A` | `#09090E` |
| panel | `#0A0F1A` | `#1A202C` | `#231C16` | `#1F162E` | `#222A35` | `#202B25` | `#232330` |
| surfaceSubtle | `#09FFFFFF` | `#161A23` | `#1C1712` | `#191225` | `#1B222B` | `#1A231E` | `#1C1C26` |
| surface | `#0FFFFFFF` | `#1A202C` | `#231C16` | `#1F162E` | `#222A35` | `#202B25` | `#232330` |
| surfaceRaised | `#1AFFFFFF` | `#1E2430` | `#28201A` | `#231B33` | `#262E39` | `#242F29` | `#272733` |
| surfaceFocused | `#24FFFFFF` | `#232935` | `#2C251F` | `#281F37` | `#2A323D` | `#28332C` | `#2B2B36` |
| divider | `#12FFFFFF` | `#12F8FAFC` | `#12FFFBEB` | `#12FAF5FF` | `#12ECEFF4` | `#12E8E3D5` | `#12DCD7BA` |
| outline | `#12FFFFFF` | `#1AF8FAFC` | `#1AFFFBEB` | `#1AFAF5FF` | `#1AECEFF4` | `#1AE8E3D5` | `#1ADCD7BA` |
| focus | `#2DE2E6` | `#4E95D9` | `#F59E0B` | `#C084FC` | `#88C0D0` | `#A7C080` | `#7E9CD8` |
| secondaryGlow | `#2276D2` | `#3C5C8C` | `#8B542B` | `#653498` | `#5E81AC` | `#536D59` | `#4C628A` |
| textPrimary | `#F2F5F9` | `#F8FAFC` | `#FFFBEB` | `#FAF5FF` | `#ECEFF4` | `#E8E3D5` | `#DCD7BA` |
| textMuted | `#93A1B5` | `#94A3B8` | `#A8A29E` | `#A899B8` | `#A5B1C2` | `#A6B3A5` | `#AAA6BD` |
| textDim | `#5B6981` | `#8998AE` | `#9C9690` | `#9C8DAD` | `#98A5B8` | `#98A794` | `#9C99AF` |
| textDisabled | `#5B6981` | `#A68998AE` | `#A69C9690` | `#A69C8DAD` | `#A698A5B8` | `#A698A794` | `#A69C99AF` |
| playerInfoSurface | `#252A31` | `#1A202C` | `#231C16` | `#1F162E` | `#222A35` | `#202B25` | `#232330` |
| accent | `#FF8A4C` | same | same | same | same | same | same |
| danger | `#FF3B5C` | same | same | same | same | same | same |
| rating | `#FFC857` | same | same | same | same | same | same |
| scrim / onScrim | `#000000` / `#FFFFFF` | same | same | same | same | same | same |
| dangerSurface / onDangerSurface | `#7A1624` / `#FFFFFF` | same | same | same | same | same | same |

Background washes per theme (§11): `focus` at α0.10 (`#1A…`) and α0.02 (`#05…`), `secondaryGlow`
at α0.12 (`#1F…`) and α0.02.

For cheap drawing on the static ground (§14), Original's translucent ladder composited over
`background #05070D` is: surfaceSubtle ≈ `#0E1016`, surface ≈ `#14161B`, surfaceRaised ≈
`#1E2026`, surfaceFocused ≈ `#282A2F`, divider ≈ `#17191E`; over `panel #0A0F1A`: `#131722`,
`#181D27`, `#232731`, `#2D313A`. Over the gradient's top (`#080C15`) they are 1–3 levels different,
and over artwork or video they are not equivalent at all — see §14 before substituting.

**Behavioural difference between Original and the others:** in Original, cards, chips and buttons
let artwork and video show through (6–14 % white over whatever is below). In the derived themes the
same elements are opaque blocks. Screens were designed on Original; the rebuild keeps this
difference (it is what the themes do today).

## 6. Focus and ink per theme

- Focus fill = `textPrimary` in every theme (off-white, cream or parchment); focused content =
  that theme's `background`; focused secondary content = `background` α0.62 (`#9E` alpha).
- Focused *danger* control: fill `danger`, content `textPrimary`, secondary `textPrimary` α0.72.
- `focus` is never the focus fill. It is the accent that marks selection, progress and the brand.

## 7. Content colours (identical in every theme)

`StreamMateContentColors.kt`. "Stable category identities; a theme can adjust these without
changing the mappings" (none does).

| Group | Token | Hex | Used for |
|---|---|---|---|
| Genres | `film` | `#8E7BFF` | Guide programme accent bar: movie, film, elokuva, cinema, drama, draama |
| | `sport` | `#FF8A4C` | … sport, urheilu, football, jalkapallo, hockey |
| | `news` | `#4CC2FF` | … news, uutis, current affairs, ajankohtais, weather |
| | `children` | `#57D9A3` | … children, kids, lapset, lasten, animation |
| Sports | `australianFootball` | `#E959FF` | Sport accent on match cards (initials in team marks) — [screens/sohva-sport.md §5](screens/sohva-sport.md) |
| | `basketball` | `#FF9A3C` | ″ |
| | `baseball` | `#FF647C` | ″ |
| | `handball` | `#FFC857` | ″ |
| | `rugby` | `#56D68B` | ″ |
| | `volleyball` | `#6C9DFF` | ″ |
| | `americanFootball` | `#B08CFF` | ″ |
| | `mma` | `#FF7A59` | ″ |
| | `formulaOne` | `#FF4D4D` | ″ |
| | `nba` | `#FFB347` | ″ |
| | `substitution` | `#8EA7FF` | Hub incident marker: substitution |
| | `videoReview` | `#E959FF` | Hub incident marker: VAR |
| | (football, ice hockey) | `focus` | These two sports use the theme's `focus` |
| Profiles | index 0 `teal` | `#2EC4B6` | Avatar fill; "the order matches the colour indices already stored in household profiles" |
| | 1 `amber` | `#FF9F1C` | ″ |
| | 2 `red` | `#E71D36` | ″ |
| | 3 `violet` | `#7B61FF` | ″ |
| | 4 `green` | `#4CAF50` | ″ |
| | 5 `pink` | `#F06292` | ″ (`atIndex` clamps to 0..5) |
| | `onAvatar` | `#FFFFFF` | Initial on the avatar |

Subtitle colours (player, not palette): White `#FFFFFFFF`, Yellow `#FFFFE14D`; box background
`#CC000000`; drop-shadow edge `#FF000000` — see [03 §3.16](03-screen-layouts.md#316-subtitles-on-the-picture).

## 8. Typography

**Font.** System sans-serif only (`android:fontFamily` = `sans`; no `FontFamily` anywhere; tv-material's
`Plain` family is `FontFamily.SansSerif`). No bundled fonts (OwnTV study: about 2 MB saved). No
tabular figures.

**Scale** (`StreamMateDefaultTypography`; "the floor is 12sp; anything smaller is unreadable from a
sofa"):

| Token | Size / line (sp) | Weight | Letter spacing | Typical use |
|---|---|---|---|---|
| `display` | 40 / 44 | Black (900) | −0.5 | Screen titles (Movies, Settings), Home hero title, details titles |
| `title` | 28 / 32 | Bold (700) | −0.3 | Live player programme title, match picker title |
| `headline` | 22 / 27 | Bold | not set | Section and row headings, guide hero title, dialog titles |
| `bodyLarge` | 18 / 25 | Normal (400) | not set | Home hero metadata, Settings row titles, sport tabs |
| `body` | 16 / 23 | Normal | not set | Values, clock, normal list rows, facts |
| `label` | 14 / 19 | SemiBold (600) | not set | Card titles, dense rows, button labels |
| `caption` | 12 / 16 | Medium (500) | not set | Secondary lines, compact button labels, chips |
| `overline` | 12 / 16 | Bold | +1.4 | Uppercase group headings |

**The inheritance rule that shapes the real look.** tv-material 1.1.0's `MaterialTheme` provides
`LocalTextStyle` = its `bodyLarge`: **16 sp / 24 sp, Regular, letter spacing 0.5 sp, sans-serif,
`includeFontPadding = false`**. Almost every call site copies only `fontSize` and `lineHeight` from a
token and sets `fontWeight` itself. Therefore:

- a token's weight applies only where the call site passes it;
- letter spacing is **0.5 sp everywhere** except where a call site passes one (display −0.5, title
  −0.3, overline 1.4, brand −0.4);
- a `Text` that sets only `fontSize` gets **line height 24 sp** (a 22 sp "headline" title set that
  way is Regular with 24 sp line spacing — common in Discover).

**Line height versus box height.** Compose applies its default `LineHeightStyle` (alignment
Proportional, trim Both, mode Fixed) whenever `includeFontPadding` is false, which tv-material sets.
Trim Both uses the font's own ascent at the top of the first line and its own descent at the bottom
of the last line, so **a single-line text is as tall as the font (≈ 1.17 × size with Roboto, the
Android TV system sans-serif), whatever its line height**; line height only sets the distance
between lines of multi-line text. Single-line boxes: 12 sp ≈ 14.1 dp, 14 sp ≈ 16.4, 16 sp ≈ 18.8,
18 sp ≈ 21.1, 22 sp ≈ 25.8, 28 sp ≈ 32.8, 40 sp ≈ 46.9. Heights elsewhere in the kit that add line
heights for single-line texts are therefore upper bounds by a few dp (derived, not measured on a
device; the OEM font can differ).

To look identical the rebuild must reproduce these effective values, not the token table alone.
Recommended: define the default text style exactly as above (same `LineHeightStyle`, no font
padding) and give every text a named style whose resolved values are listed in the component and
layout files.

**Literal sizes outside the scale** (in beta 23 call sites; keep for identity, but name them):
10 sp (library-manager row subtitle — below the floor, fix to 12), 11 sp (time-zone region
overline), 13 sp (screen subtitles, dialog subtitles, legal body with 18 sp lines, reminder texts,
score-ticker empty text, hub text, live minute), 14 sp (track-picker labels, score ticker), 15 sp
(hub rows, stream channel name), 17 sp (hub panel headings, team names), 18 sp (dialog titles,
quick-actions title, legal section titles, player initials), 20 sp (phone-dialog title, profile
names), 21 sp (track-picker title), 22 sp (channel dial, seek pill, Home brand, PIN channel name,
channel-editor channel name), 25 sp (library-manager title), 28 sp (legal title), 30 sp (Sohva
Sport brand), 32 sp (Search and channel-editor titles), 34 sp (brand default, "Who is watching?"),
36 sp (hub score), 40 sp (PIN heading), 48 sp (profile initial).

## 9. Spacing and safe area

`StreamMateDefaultSpacing`: `xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 24, `xxl` 32 dp;
`safeHorizontal` 40 dp, `safeVertical` 24 dp — "the single TV safe area used by every screen".
At 960 × 540 the safe content box is 880 × 492 dp at (40, 24). This is slightly inside Android TV's
usual 5 % overscan guidance (48 × 27 dp); the rebuild keeps 40/24 for identity.

Where screens deviate: Home and Discover use padding 0 and their own insets (Home content starts at
96 dp, Discover at 84 dp, both rails overlay the ground); the player places chrome at 40 dp sides,
24 dp top, **28 dp bottom**; Discover sub-pages pad 28 dp (search 28/20); details pages pad 32/24
(catalogue) or 32 (Discover); the match hub is full-bleed.

Recurring literal spacings: 2 (list gaps), 6, 7 (key-hint chip gap, compact icon gap), 9, 10, 14
(Settings row padding, rail header), 18, 20, 22, 26 (Home rows, sport sections), 28, 36.

## 10. Shapes and strokes

`StreamMateDefaultShapes`: rounded corners `small` 8 dp ("controls and cells"), `medium` 12 dp
("cards"), `large` 18 dp ("large panels"). `TvSurface` defaults to `small`.

Literal radii in use: 2 (right corners of the guide genre bar), 6 (live badge), 7 (channel-editor
logo), 8 (Discover cast border, hub event rows), 10 (hub stream rows), 12 (channel-editor panels),
14 (sport state card, hub panels), 15 (switch = half its 30 dp track), 16 (library-manager dialog),
20 (match-hub panel), circles (avatars, dots, knobs, thumbs, team marks, watched badges).

Strokes: focus ring **3 dp** `textPrimary` (`FOCUS_RING_WIDTH`), drawn inside the bounds so nothing
re-measures; hairlines 1 dp; dial and picker borders 1 dp `outline`; text-edit dialog 2 dp `focus`
frame; progress 3–5 dp.

## 11. The screen background

`StreamMateScreenBackground(contentPadding = 40/24 by default)`:

1. Vertical linear gradient over the full screen: stop 0 `backgroundTop`, 0.5 `background`, 1
   `backgroundBottom`.
2. Radial wash: centre (0.12 w, −0.10 h), radius 0.58 w; stops 0 `focus` α0.10, 0.6 `focus`
   α0.02, 1 transparent.
3. Radial wash: centre (0.92 w, 0.04 h), radius 0.50 w; stops 0 `secondaryGlow` α0.12, 0.62
   `secondaryGlow` α0.02, 1 transparent.

Both wash centres sit at or above the top edge, "weak enough to read as light rather than as
shapes". All three are drawn by one `Canvas` inside a `graphicsLayer` with
`CompositingStrategy.Offscreen`, so the picture is recorded once and re-rendered only when the size
or theme changes. Before beta 23 the ground and washes were "four passes over every pixel on every
frame … on the Mali-G31 of an Elisa box, a fair share of a frame before any of the screen is
drawn". The flat fill under the gradient was removed. The grain texture was removed in beta 17 and
must not return. Exceptions: the profile picker and pre-routing host use flat `backgroundBottom`;
the player uses black.

## 12. Focus treatment by surface type

"Default spring" below means Compose's library default: `spring()` with damping ratio 1 (no
bounce) and stiffness 1,500 (`StiffnessMedium`); colour, float and dp animations all use it when no
spec is given. At that stiffness a full colour flip settles in about 0.17 s (≈ 10 frames) and a
4 % scale change in about 0.07 s (derived, not measured).

| Surface | Rest | Focused | Scale | Shadow | Animated |
|---|---|---|---|---|---|
| `TvSurface` (fill mode, default) | `resting` (default transparent), content `restingContent` (default `textMuted`), secondary `textDim` | fill `textPrimary`, content `background`, secondary `background` α0.62 | `focusScale`, default **1.04** | **14 dp** black ambient + spot, not animated | fill: default spring; scale: default spring; content colour: **instant** |
| `TvSurface(focusRing = true)` | as rest | fill and content unchanged; 3 dp `textPrimary` ring inside the shape | `focusScale` (often 1) | 14 dp | ring instant |
| `TvActionButton` | fill `surface`, content `textPrimary` | fill `textPrimary`, content `background` | **1.03** | **10 dp** | fill and content both default spring; scale default spring |
| `TvListRow` | transparent, `textMuted` | fill flip (via `TvSurface`) | 1 | 14 dp | fill spring |
| `TvUrlField` (edit-on-click display) | fill `surface`, text `textPrimary`, hint/icon `textMuted` | fill `textPrimary`, text `background`, hint `background` α0.62, icon `background` α0.72 | 1 | **13 dp** | instant |
| Poster cards (catalogue wall, Discover) | art tile | 3 dp `textPrimary` ring | 1 | none | instant |
| Home landscape / poster cards | art + text | 3 dp ring | 1 | 14 dp | instant |
| Similar-titles artwork card | poster | 3 dp ring | **1.05** on a child layer | none | scale default spring |
| Match card | `surfaceSubtle` | fill flip | **1.03** after the clip | **16 dp**, shape large | fill and scale default spring |
| Guide channel cell | transparent / `surfaceFocused` (row selected) | fill `textPrimary` | 1 | none | fill default spring, read in draw |
| Guide programme cell | ladder by state | **selection-driven** white fill | 1 | none | instant |
| Sport filter tab | transparent | fill `textPrimary` | 1 | none | fill default spring |
| Settings switch | outer transparent; track `surfaceRaised`/`focus` | outer fill `textPrimary` | 1 | 14 dp | track colour and knob offset default spring |
| Player icon action | `surface`, `textPrimary` | fill flip | 1 | 14 dp | fill spring |
| Track-picker row | `surfaceSubtle` / `surfaceRaised` (selected) | 3 dp `textPrimary` border + default indication | 1 | none | instant |
| Channel-editor row | `surfaceSubtle` / `surfaceRaised` | 3 dp `textPrimary` border + default indication | 1 | none | instant |
| Library-manager row (divergent) | `surface` | `surfaceFocused` fill + **2 dp `accent` border** | 1 | none | instant |
| Discover cast member (divergent) | transparent border | **2 dp `focus` border**, 8 dp corners | 1 | none | instant |
| Match-hub events list | — | 3 dp ring around the list | 1 | none | instant |

"Default indication" = Compose foundation's `DefaultDebugIndication`, used wherever a call site
writes `clickable(onClick)` without `indication = null`: a black α0.1 rectangle over the whole node
while focused and α0.3 while pressed. It appears on Discover poster cards, similar-titles artwork
cards, track-picker rows, channel-editor rows, library-manager rows and the inline player action
text. The design system
says "no press or focus wash"; the rebuild uses **no indication anywhere** and relies on the fill
or ring ([screens/movies-and-series.md](screens/movies-and-series.md) already decided this for
posters).

**Why the lift sits inside the focus target.** `TvSurface` and `TvActionButton` apply
`graphicsLayer { scaleX = scaleY = scale }` *after* `onFocusChanged`/`clickable` and before
`shadow → clip → background`. A layer outside the focus target would enlarge the reported bounds,
so a scrolling parent would scroll to fit the lifted item and every sibling would shift.

**Long press.** `TvSurface(onLongClick)`: OK/Enter/NumPadEnter held — fires once on the first key
repeat (`repeatCount ≥ 1`) and swallows the following key-up.

## 13. Selection, disabled and state rules

- **Focus** = off-white fill (or the ring on artwork). **Selected** = `surfaceFocused` fill plus a
  marker, content `textPrimary`, secondary `textMuted`. On `TvActionButton` a selected, unfocused
  button shows its content in `focus`. On `TvListRow` the marker is a 3 × 22 dp bar (`focus`;
  `background` when the row is also focused, "or cyan-on-white swallows it") and Bold instead of
  Medium. Sohva Sport tabs: "the orange rule means selected, the fill means focused: never the same
  signal." The guide programme cell is the one place where *selection* draws the white fill (the
  cell stays white while focus is in the hero or the rail).
- **Danger** controls: content `danger` at rest; fill `danger` with `textPrimary` content when
  focused.
- **Disabled**: `TvSurface` keeps its resting fill (or transparent) with content and secondary
  `textDisabled`; `TvActionButton` uses fill `surfaceSubtle` and content `textDisabled`. A disabled
  control is neither clickable nor focusable (`clickable(enabled = false)`; `textDisabled` is
  documented as "content of a control that cannot be focused"), so D-pad focus skips it. Where a
  row must stay inspectable while inactive, the code keeps it enabled and ignores the click (the
  Discover import review list does this). Disabled switch track: `surface`.
- **Past** guide programmes: whole cell at α0.55 unless selected.
- **Live**: `danger` dot 8 dp (hero, Home) or 7 dp (live badge), `LIVE` chip filled `danger` with
  `textPrimary` text — "the one tone that should pull the eye across a room".

## 14. Interface size

`InterfaceScale` scales **density**, not font scale: the host wraps the app in
`InterfaceScaled`, which provides `Density(device density × factor, device fontScale)`. Layouts and
text shrink together; the viewer's system font scale still applies on top.

| Value (stored name) | Factor | Label (`interface_scale_option` "%1$s (%2$d %%)") | dp canvas at 1080p |
|---|---|---|---|
| `NORMAL` (default) | 1.0 | "Normal (100 %)" | 960 × 540 |
| `COMPACT` | 0.9 | "Compact (90 %)" | 1066.7 × 600 |
| `SMALL` | 0.8 | "Small (80 %)" | 1200 × 675 |
| `SMALLER` | 0.7 | "Smaller (70 %)" — "a tester on a large screen found Small one step short" | 1371.4 × 771.4 |

Preference key `interface_scale` (enum **name**, unlike the theme's lower-case id); backup field
`interfaceScale`; unknown → Normal. The launch screen stays at device density ("scaled with the
interface it would jump"). Every static layer keyed by size (§15) must also be keyed by this
factor.

## 15. Motion

Motion is minimal and functional. Screen changes are instant (no navigation transitions). Hero
text is never animated. Complete list of animations in beta 23:

| Animation | Spec | Where |
|---|---|---|
| Focus fill colour | default spring (§12) | `TvSurface`, `TvActionButton` (+ content colour), guide channel cell, sport tabs, match card, switch track |
| Focus scale 1.03 / 1.04 / 1.05 | default spring | buttons / surfaces / artwork cards, match cards |
| Switch knob travel 22 dp | default dp spring (visibility 0.1 dp) | Settings switch |
| Home rail width 80 ↔ 244 dp and rail scrim α0.70 ↔ 0.97 | default dp / float springs (≈ 0.25 s, relayout every frame) | Home |
| Home hero artwork | `Crossfade(tween(250))` (FastOutSlowIn), after 180 ms of rest | Home |
| Image crossfade | image-loader default 140 ms; Discover posters 120 ms; catalogue posters none | everywhere images load |
| Overlay fade | `fadeIn()` / `fadeOut()` defaults: spring stiffness 400 (`StiffnessMediumLow`), ≈ 0.33 s | player chrome, scrim, channel list, groups pane |
| Match hub | `fadeIn() + slideInHorizontally { w / 5 }` and the reverse (defaults, stiffness 400) | Sohva Sport |
| Live badge pulse | alpha 1 → 0.25 → 1 twice, `tween(240)` legs (960 ms), re-run when the minute changes; finite on purpose | match cards |
| Buffering arc | 90° arc rotating 0 → 360°, `tween(900, LinearEasing)`, infinite restart | player |
| Discover loading title | alpha 0.78 ↔ 1, `tween(1400, FastOutSlowInEasing)`, infinite reverse | Discover playback start |

Not animated: shadows (appear/disappear instantly), rings, text colours in `TvSurface`, guide
programme cells, rail labels, the guide now-line, Discover rail width (64 ↔ 218 dp, instant),
dialogs (platform default window animation), options sheets.

**Rebuild rules:** keep the list above and nothing more; no animation may be infinite except the
buffering indicator (and the Discover title pulse, dropped in reduced mode); every infinite
animation must survive a zero animator-duration scale without crashing (OwnTV shipped that crash —
[reference/owntv-study.md](../reference/owntv-study.md) item 18; add a test).

## 16. Render it cheaply

The look must be the same on a Mali-G31 MP2 (about a tenth of the Shield's fill rate) and a 1–2 GB
stick. Everything below keeps the pixels identical, or changes them only where noted.

### 16.1 Hard rules

1. **No blur, no grain, no noise, no glass**, ever (OwnTV item 16: expensive looks are not part of
   Sohva's look).
2. **Static ground drawn once per (theme, size, interface factor).** Render the gradient and the
   two washes into one bitmap off the main thread when the theme or size changes, with dithering
   on, and draw it as the screen's first draw call. The picture is low-frequency, so it can be
   rendered at half resolution (960 × 540 px, ≈ 2 MB ARGB_8888 instead of 8.3 MB) and drawn scaled
   with bilinear filtering; verify against `design/screenshots/beta23-synthetic/home.png` that no
   banding appears. One bitmap is shared by every screen, instead of an offscreen layer per screen
   instance. The window background stays a flat colour.
3. **At most one full-screen translucent layer over the ground at a time**, and no full-screen
   offscreen (`CompositingStrategy.Offscreen`) layer except the pre-rendered bitmaps above.
4. **Opaque instead of ≥ 0.9 scrims over live content.** Under a 0.94 scrim what lies below
   contributes at most 6 % — a faint ghost of white text, nothing of the dark ground. Draw such an
   overlay with the scrim colour at α1 and stop drawing the content beneath it (keep it composed for
   state, skip its draw). Applies to the match hub (0.94 `background`) and, for the strip it
   covers, the expanded Discover rail (0.98). The only visible change is that ghost disappearing.
   Gradient scrims that only reach 0.94–0.97 at one edge (player chrome, player channel list) stay
   gradients. Keep 0.86 and lower scrims translucent (the dimmed content is part of the look) but
   make sure nothing beneath them animates while they are up.
5. **Draw only where a scrim is not transparent.** The player chrome scrim is transparent from 16 %
   to 52 % of the height; draw it as two bands (top 16 %, bottom 48 %) instead of a full-screen
   gradient. Draw nothing when no chrome is visible (beta 23 already does this).
6. **Shadows only on large focused items.** A black 10–16 dp elevation shadow on a near-black
   ground is almost invisible. Drop it from list rows, rail items, buttons, fields, chips and icon
   actions (visually identical on the ground). Keep it for large focused cards that sit over
   artwork or lighter fills (Home cards, match cards), drawn as a pre-rendered soft rounded-rect
   bitmap (one per card size and radius, nine-patch style) rather than a RenderNode elevation, and
   never animated. At most one shadow per frame (only one item has focus). In reduced mode (§16.3)
   draw no shadows.
7. **No per-cell clip or alpha layers.** Draw rounded fills with `drawRoundRect` (no `clip`) when
   the node holds no image; clip only image nodes. Bake "past" α0.55 into the colours instead of a
   layer. The guide draws a row's programmes on one canvas ([screens/guide.md §12](screens/guide.md),
   OwnTV item 12).
8. **Animated colours are read in draw.** A focus fill animation must not recompose its
   composable 10 times: hold the animated value in state and read it inside `drawBehind` (the guide
   channel cell already does); use a colour producer for animated text colour; apply scale through
   a `graphicsLayer { }` lambda.
9. **Artwork in RGB_565 at its drawn size.** Posters, backdrops, hero art, stills and episode
   thumbnails are opaque: decode RGB_565 (half the memory of ARGB). ARGB_8888 only for logos,
   crests and anything with transparency. Decode at the box size: the Home hero art box is
   633.6 × 302.4 dp = 1,267 × 605 px at density 2, so 1,280 × 720 is enough on a 1080p UI (beta 23
   requests 1,920 × 1,080). Limits and caches: [04-icons-and-imagery.md §3](04-icons-and-imagery.md).
10. **Backdrops pre-composited.** Home hero: compose art × the two erase masks × the scrims over
    the matching region of the ground into one opaque bitmap per artwork change, off the main thread;
    draw it as one image. Details pages (catalogue and Discover): the same for backdrop + scrims.
    Beta 23 keeps two full-screen offscreen layers on Home and re-renders them every crossfade frame.
11. **Translucency itself is not the cost; area is.** Keep Original's translucent ladder on
    controls (blending is cheap on tile-based GPUs; an opaque substitute would change the look over
    artwork). Count full-screen passes instead: target ≤ 2 full-screen passes per frame on a UI
    screen (ground + one scrim/backdrop), ≤ 1 over video.
12. **Text measured once.** Guide cells, clocks and cards format their strings when data changes,
    not per frame; the guide uses a cached text measurer.
13. **QR codes built off the main thread at module resolution** (one pixel per module) and drawn
    scaled with nearest-neighbour filtering, instead of 384 × 384 ARGB bitmaps filled with
    `setPixel` on the main thread ([02 §17](02-components.md)).

### 16.2 Cost of each look element (beta 23 → rebuild)

| Look element | Beta 23 | Rebuild | Visual change |
|---|---|---|---|
| Ground gradient + 2 washes | Offscreen layer per screen, re-rendered on size/theme | One shared pre-rendered bitmap, one draw | none |
| Home hero backdrop | 2 full-screen offscreen layers + DstOut masks + 2 full-screen scrims; bundled floor image always drawn under fetched art | One pre-composited opaque bitmap per artwork | none |
| Details backdrop + scrims | Full-screen image + 2–3 full-screen gradients every redraw | One baked bitmap per artwork | none |
| Match hub | 0.94 scrim over the live list + radial-gradient panel + gradient per stream row | Opaque panel, list not drawn beneath; flat row fills in reduced mode | text ghost gone; flat rows in reduced mode |
| Player chrome scrim | Full-screen gradient with transparent middle | Two bands | none |
| Focus shadows | RenderNode elevation on every focused surface/button/field | Only large cards over art, pre-rendered | none on the ground |
| Focus colour animation | Recomposes the control each frame (~10 frames) | Read in draw | none |
| Guide cells | Clip + alpha layer per cell, ~40 focusable nodes | One canvas per row, one focusable per row | none |
| Discover poster focus | Border drawn twice + default indication | One ring, no indication | focused poster ≈10 % brighter (fix) |
| Image crossfades | 140 / 120 / 250 ms | Same; none in reduced mode | none / cut |
| QR bitmaps | 384² ARGB, `setPixel` loop on main thread | Module-size bitmap, nearest filter | none |

### 16.3 Reduced-motion and low-RAM mode (new in the rebuild)

Beta 23 has no such mode. The rebuild adds one that **keeps every colour, size and shape** and
removes only motion and optional compositing work.

- **On automatically when** the device is in the low memory tier (plan/07 §2.2:
  `ActivityManager.isLowRamDevice()`, memory class **under** 192 MB, or total RAM ≤ 2.5 GiB; the
  Shield's 192 MB class stays in the standard tier), or the system animator duration scale is 0. Whether to also expose a manual switch in Settings › General is an
  owner decision (Open questions).
- **What changes:** focus fills and scales jump to their end values (no springs); `AnimatedVisibility`
  fades and the hub slide become instant show/hide; the Home hero art and all image crossfades cut;
  the live-badge pulse and the Discover title pulse are static at full opacity; the buffering arc
  steps every 150 ms instead of animating every frame; shadows are not drawn; the Home hero decodes
  at 1,280 × 720 and keeps a single bitmap (no second bitmap for a crossfade); the match hub's
  stream rows use flat fills (`surfaceSubtle`) instead of per-row gradients.
- **What never changes:** colours, typography, layout sizes, the ground, rings and fills, which
  element shows focus, and the timing of functional delays (180 ms hero settle, 350 ms metadata,
  250 ms search debounce, dial timeouts).

### 16.4 How to check

On the low-end class (or the `.local/slowbox` emulator stand-in described in plan/07): GPU
rendering profile and a Perfetto trace while holding D-pad Right across a Home row and down the
guide; count full-screen passes per frame with the GPU overdraw overlay (target: ground + at most
one layer blue, nothing red on a static screen); confirm no offscreen layer is created per screen
(`dumpsys gfxinfo` layer list); confirm memory with the hero bitmap budget above.

## 17. Lessons from the current app

- Painting the ground every frame cost the Mali-G31 "a fair share of a frame" — solved in beta 23
  with a cached layer; the rebuild goes further with one shared bitmap.
- The launch picture was left as the window background under every frame — replaced by a flat
  colour two frames after start (MainActivity).
- The grain texture (removed in beta 17) is gone for good.
- The inherited tv-material text style (0.5 sp tracking, 24 sp lines, Regular) silently decides
  much of the real look; the rebuild must set it deliberately.
- Default click indication crept in wherever `clickable` was used without `indication = null`;
  one shared focusable primitive prevents it.
- A few screens invented their own focus look (library manager: orange border and fill; Discover
  cast: cyan border; track picker and channel editor: ring on a flat row). The rebuild should use
  the standard treatments (fill flip for rows; ring only for artwork) — owner decision whether the
  library manager keeps orange.
- Dialogs filled with `surface` (reminder, programme actions, time zone, library manager, legal
  sections as cards) are 6 % white — nearly transparent — in Original, and sit on the platform's
  dialog dim; in the derived themes the same dialogs are opaque. Use `panel` for every dialog card
  in the rebuild (visible change in Original only: the card becomes a solid dark panel).

## 18. Open questions

1. `theme-licenses.txt` is bundled and cited in THIRD_PARTY_NOTICES but no screen shows it; should
   the legal screen open it?
2. Platform dialogs rely on the framework's default window dim (amount not set in code); the exact
   dim on each device is unverified.
3. Whether gradients are dithered in beta 23 (Skia/HWUI default for the Compose brush) is
   unverified; decide the rebuild's dithering by comparing screenshots.
4. Manual "Reduce motion" switch in Settings — owner decision; and the memory-class threshold that
   turns the mode on automatically (would it include the Elisa box?) (§16.3).
5. Library-manager orange focus and the Discover cast cyan border: keep or normalise (§17)?
6. The 10 sp library-manager subtitle and 11 sp time-zone overline break the 12 sp floor; the
   rebuild proposes 12 sp (visible change).
7. Resolved derived colours were computed, not sampled from a device (±1 per channel possible).
8. Single-line text box heights (§8) are derived from Roboto's metrics and Compose's default
   `LineHeightStyle`; confirm with a layout-bounds capture on the Shield and the low-end box. The
   screen extracts in [screens/](screens/) add line heights, so their derived heights may be a few
   dp high.

## 19. Reference: current code map

- `core/.../app/StreamMateTheme.kt` — palette, typography, shapes, spacing, theme provider.
- `core/.../app/ColorTheme.kt` — seven themes, stored ids, `darkPalette()`.
- `core/.../app/StreamMateContentColors.kt` — genre, sport and profile colours.
- `core/.../app/InterfaceScale.kt`; `app/.../app/MainActivity.kt` (`InterfaceScaled`, window
  background swap).
- `core/.../feature/common/TvUiComponents.kt` — background, brand, icons, surfaces, buttons, rows,
  fields, chips.
- `core/src/main/assets/theme-licenses.txt`; `app/src/main/res/values/colors.xml`, `themes.xml`.
- `iptv/.../feature/settings/SettingsLabels.kt` — theme and size picker labels.
- `app/.../app/StreamMateBackupManager.kt` — `interfaceScale` and `colorTheme` in backups.
