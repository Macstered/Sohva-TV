# Localisation

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

## 1. Summary

Sohva TV's interface is translated into seven languages: English (the fallback), Finnish (the
owner's language, complete) and five drafts — Spanish, Portuguese, German, Swedish and Italian —
written from the English and labelled as drafts in the language picker until a native speaker has
read them. The viewer can follow the TV's language or pick one in Settings; the app restarts its
screen to apply it, and from Android 13 the choice is also the platform's per-app language.
Separately, titles, plots and artwork come from TMDB in one of 22 metadata languages, and every
clock and programme time is shown in one time zone: the TV's own, or one the viewer picks. This
spec covers language storage and switching, resolution and fallback, metadata languages, the time
zone's effects, every date/time/number format on screen (with today's inconsistencies), plurals,
how the strings are organised and counted, what is missing, and the rules for adding strings.

## 2. Feature checklist

- L10N-01 Seven interface languages: English (fallback), Finnish (complete), Spanish, Portuguese, German, Swedish, Italian (drafts).
- L10N-02 Interface language picker: System default plus the seven, each named in its own language; drafts carry "(borrador)", "(rascunho)", "(Entwurf)", "(utkast)", "(bozza)".
- L10N-03 Choosing a language restarts the app's screen in that language; System default follows the TV and falls back to English for other languages.
- L10N-04 From Android 13 the choice is the platform per-app language and also appears in Android's own per-app language screen.
- L10N-05 Metadata language for TMDB titles, plots and artwork: 22 languages; Finnish by default with a Finnish interface, else English; changing it refreshes metadata in the background.
- L10N-06 Language names in pickers (VOD audio/subtitle languages, metadata languages) appear in the interface language.
- L10N-07 One app time zone (the TV's own by default) for the guide, Home, the player and Sohva Sport.
- L10N-08 Clocks on Home, Sohva Sport and the player follow the interface language and the TV's 12/24-hour setting.
- L10N-09 Guide day labels "Now", "Today", "Tomorrow", "Yesterday" beside the date.
- L10N-10 Counts use plural forms in every language.
- L10N-11 Error messages, including stored refresh failures, appear in the current interface language.
- L10N-12 "Help translate Sohva TV" in About opens the public repository.
- L10N-13 The phone setup page is in the TV's interface language (the addon phone page is English).

## 3. Entry points and navigation

- Settings › General › Interface language ([70](70-settings.md) SET-FR-50).
- Android 13+: Android Settings › Apps › Sohva TV › Language (lists the seven languages from
  `locales_config.xml`); a choice made there is what Sohva TV's picker shows.
- Settings › Library › Metadata language ([70](70-settings.md), [41](41-metadata-enrichment.md)).
- Settings › General › Time zone ([70](70-settings.md) SET-FR-54, §4.6).
- Settings › About › Translations › "Help translate Sohva TV" ([72](72-updates-about-diagnostics.md)).
- After a language change the activity is recreated and the app restarts at its start route
  (SHELL §8); Back behaviour is unchanged.

## 4. Behaviour

### 4.1 Interface language: storage and switching

- **L10N-FR-01 Supported tags**: `en`, `fi`, `es`, `pt`, `de`, `sv`, `it` (this order). Draft
  tags: `es`, `pt`, `de`, `sv`, `it`. Portuguese was written for Brazil but ships as generic `pt`
  (commit `080719d`).
- **L10N-FR-02 Stored choice**: a tag, or none = follow the system.
  - Android 13 (API 33) and later: the platform owns it. Read: the first locale of the app's
    per-app locale list, its language, accepted only when it is one of the seven tags (a platform
    choice of `pt-BR` reads as `pt`). Write: set the per-app locale list to the tag, or to the
    empty list for System default. No copy is kept elsewhere, so the two cannot drift.
  - Below Android 13: SharedPreferences file `streammate_locale`, key `language_tag`; removed for
    System default. A stored value outside the seven reads as none.
  - It is deliberately not in the DataStore: the language must be resolved synchronously before
    the first activity is built, and blocking on DataStore there turns cold starts into ANRs
    (`AppLocale.kt`).
- **L10N-FR-03 Applying below Android 13**: the activity's `attachBaseContext` wraps the base
  context in a configuration whose locale list is the single stored locale, and sets the JVM
  default locale to it (so `Locale.getDefault()` formatting follows the interface). With no
  stored tag the context is left as it is.
- **L10N-FR-04 Switching**: choosing in the picker persists the tag; below 13 the activity is
  recreated at once; from 13 the platform recreates it. Either way the app restarts at the start
  route. Rebuild: choosing the current value does nothing (beta 23 recreates anyway below 13).
- **L10N-FR-05 Application-context strings (rebuild fix)**: beta 23 wraps only the activity, so
  below Android 13 everything resolved through the application context stays in the **system**
  language: the phone setup page, reminder notifications and their channel name, backup error
  messages, the lab build's safety notice. The reference
  boxes (Elisa box API 31, Shield API 30) are all on this path. Rebuild: every non-activity string
  is resolved through one localised resources provider built from the stored tag (and rebuilt when
  it changes), or the Application's base context is wrapped the same way; a device test checks the
  phone page and a reminder notification in Finnish on API 31.
- **L10N-FR-06 Back to System default below 13 (rebuild fix)**: beta 23 leaves the JVM default
  locale at the previously chosen language until the process dies, so day names and other
  default-locale formatting stay in that language while strings follow the system (read from the
  code). Rebuild: on System default, restore the JVM default to the system's first locale.
- **L10N-FR-07 Per-app language screen**: `res/xml/locales_config.xml` lists `en`, `fi`, `es`,
  `pt`, `de`, `sv`, `it` and the manifest's `android:localeConfig` points to it.
- **L10N-FR-08 Demo build** forces English (`AppLocale.apply(context, "en")` when seeding) so its
  screenshots are stable.

### 4.2 Resolution and fallback

- **L10N-FR-10** English is the default resource set (`values/`); every other language is a
  qualifier folder (`values-fi` …). An unsupported system language (French, Norwegian…) shows
  English. (Before beta 1 Finnish was the default set and every untranslated locale fell back to
  Finnish — commit `9e09ded`, 29 August 2026.)
- **L10N-FR-11** A string missing from a translation falls back to English. Beta 23 has exactly
  one such group: the Trakt strings (6.3).
- **L10N-FR-12** Compose text and formatting read the interface locale from the configuration
  (`Locale.current` / the configuration's first locale); helpers must work on API 23, where
  `Configuration.getLocales()` does not exist (beta 23's `primaryLocale()`).

### 4.3 Metadata languages

- **L10N-FR-20 Tags** (TMDB's `language` parameter), in picker order: `en-US`, `fi-FI`, `sv-SE`,
  `nb-NO`, `da-DK`, `de-DE`, `nl-NL`, `fr-FR`, `es-ES`, `pt-PT`, `pt-BR`, `it-IT`, `pl-PL`,
  `cs-CZ`, `hu-HU`, `ru-RU`, `tr-TR`, `el-GR`, `ar-SA`, `ja-JP`, `ko-KR`, `zh-CN` (22, no
  duplicates). Only these are accepted on write; `sv` without region is not supported.
- **L10N-FR-21 Labels**: the platform display name of each tag in the interface locale, first
  letter upper-cased in that locale ("English (United States)", "Portuguese (Brazil)"; in Finnish
  "Englanti (Yhdysvallat)"). The picker keeps the fixed order above (not sorted by label).
- **L10N-FR-22 Default** while nothing is stored: `fi-FI` when the **chosen** interface tag starts
  with `fi`, otherwise `en-US` — so a Finnish TV on System default gets English metadata (Q-02).
  The default is derived at read time and not written; a stored value always wins.
- **L10N-FR-23 Changing** it (to a different tag) stores it, clears derived titles and restarts
  the background enrichment; the app root pushes the value to the metadata client whenever it
  changes (SHELL-FR-75, [41](41-metadata-enrichment.md)). A change of the *derived* default (the
  interface language changed while no metadata language is stored) switches new lookups only;
  already enriched titles stay in the old language until refreshed (read from the code).
- **L10N-FR-24** TVmaze has no language parameter (English data). API-Sports team and
  competition names, EPG titles and descriptions, provider group and channel names are shown as
  delivered; nothing is machine-translated.

### 4.4 Time zone and its effects

The setting itself is [70](70-settings.md) SET-FR-54 and §4.6: key `time_zone` absent = the
TV's own zone (fresh-install default since beta 6), else an IANA id.

- **L10N-FR-30 Uses the app zone**: guide programme times, ruler labels, ranges in the hero and
  options, the day label and its Now/Today/Tomorrow/Yesterday decision; Home header clock and the
  hero's programme window; the player clock and programme ranges; Sohva Sport's day, clock,
  kickoff labels (formatted when fetched, so a zone change triggers a reload of the day, which
  costs an API request) and channel programme times; sport stream matching for channel names
  without an explicit zone ([60](60-sohva-sport.md)).
- **L10N-FR-31 Does not use the app zone** (beta 23): search result times use the **system**
  zone (flaw — rebuild: the app zone); catch-up URLs use the Xtream server's zone from
  `server_info.timezone`, else the device zone (correct: it is the server's clock,
  [22](22-catchup-and-reminders.md)); diagnostics timestamps and the diagnostics file name use the
  system zone (developer-facing, keep); XMLTV parsing uses the offsets in the data plus each
  source's EPG correction ([10](10-sources-and-import.md)); stored times are epoch milliseconds.
- **L10N-FR-32 Fallbacks**: an id that fails to parse where it is used falls back to the device
  zone. The data-class default `Europe/Helsinki` must not exist in the rebuild (it flashes before
  the first preferences emission, [70](70-settings.md) §8).
- **L10N-FR-33 Device zone changes** are not observed in beta 23 (no `ACTION_TIMEZONE_CHANGED`
  receiver). Rebuild: re-read the device zone on that broadcast and on resume.
- **L10N-FR-34 Guide windows** are anchored on UTC half-hours, so in zones with a :15 or :45
  offset (Asia/Kathmandu +5:45, Pacific/Chatham +12:45) the ruler reads :15/:45. Keep; noted so a
  test does not flag it.

### 4.5 Date and time formats on screen

| Where | Pattern (beta 23) | Locale | Zone | 12/24 h |
|---|---|---|---|---|
| Guide programme times, ruler, ranges (`formatTime`, `formatRange`) | `HH.mm`, range joined by `–` (en dash) | JVM default | app | always 24 h |
| Guide day label (`WINDOW_DAY_FORMATTER`) | `EEE d.M.` ("Wed 24.9.", "ke 24.9.") + `guide_window_now` / `_today` / `_tomorrow` / `_yesterday` | JVM default | app | — |
| Home hero programme window | `HH.mm–HH.mm` | JVM default | app | 24 h |
| Home header clock | best pattern for `EEEdMMM` + `' · '` + best pattern for `Hm` or `hmma` | interface | app | TV setting |
| Sohva Sport header clock | best pattern for `EEEEdMMM` + `'  ·  '` + `Hm` / `hmma` | interface | app | TV setting |
| Sohva Sport channel programme times | `HH.mm` | JVM default | app | 24 h |
| Sohva Sport kickoff labels (cached at fetch) | `HH:mm` | `Locale.ROOT` | app at fetch | 24 h |
| Player clock | best pattern for `Hm` / `hmma` | interface | app | TV setting |
| Player programme range | `HH:mm–HH:mm` (a new formatter per call) | JVM default | app | 24 h |
| Player position / duration | `%d:%02d:%02d` or `%02d:%02d` | JVM default | — | — |
| Player info line | ` %.0fp`, `%.1f Mb/s`, `%.1f s` | `Locale.ROOT` | — | — |
| Search result times | `d.M. HH.mm` | JVM default | **system** | 24 h |
| Time-zone picker offsets | `UTC`, `UTC+3`, `UTC−5:30` (U+2212) | — | — | — |
| EPG correction (source page) | `0 min`, `+1 h`, `−30 min`, `+1 h 30 min` (hard-coded English units) | — | — | — |
| Image cache sizes | `%.0f MB`, `%.0f kB`, `%d B` (1024-based) | `Locale.US` | — | — |
| Diagnostics lines / file name | `yyyy-MM-dd HH:mm:ss` / `yyyyMMdd-HHmm` | JVM default | system | — |
| Durations and counts in strings | `catalogue_runtime` "%1$d min", `series_episode_label` "S%1$d E%2$d", `home_minutes_left` plural … | resources | — | — |

- **L10N-FR-40 Inconsistencies to know**: the guide, Home hero, Sohva Sport channel times and
  search always use the Finnish-style `HH.mm` with a dot and 24 hours, while the three clocks
  follow the interface language and the TV's 12/24-hour setting — an English viewer with a
  12-hour TV sees "9:40 PM" in the clock and "21.40–22.30" beside it; the player's range uses a
  colon (`HH:mm`); the day label uses day-month order with dots in every language.
- **L10N-FR-41 Rebuild rule**: one formatting service, created once per (interface locale, zone,
  24-hour setting) and injected; no formatter or `ZoneId` built in composition or per call.
  Default target (Q-01): times use the locale's own separator and the TV's 12/24-hour setting
  with two-digit hours in 24-hour mode (Finnish "21.40", "09.05"; English 24 h "21:40", 12 h
  "9:40 PM"); day labels use the locale's abbreviated weekday and numeric day-month skeleton
  (`EEEdM`). If the owner keeps the fixed `HH.mm`, keep it everywhere including the player range
  and search, so at least the app agrees with itself. Layouts that show times (guide ruler, hero,
  cards) must fit the 12-hour form ("10:30 PM") at every interface size.

### 4.6 Numbers, units and plurals

- **L10N-FR-50 Plurals**: every count shown with a noun is a `<plurals>` resource with `one` and
  `other` in all seven languages (24 in beta 23: `app` 10 — `addon_ui_catalog_count_status`,
  `addon_ui_checked_count`, `addon_ui_copied_count`, `addon_ui_install_count`,
  `addon_ui_preview_count`, `catalogue_imported_movies`, `catalogue_imported_series`,
  `home_minutes_left`, `home_sports_count`, `search_result_count`; `iptv` 10 — `channels_count`,
  `health_success`, `profile_content_count`, `series_season_count`, `source_imported_channels`,
  `source_imported_movies`, `source_imported_programmes`, `source_imported_series`,
  `source_refresh_interval_hours`, `source_test_m3u_ok`; `sportmate` 4 — `today_available_streams`,
  `today_match_count`, `today_possible_channels`, `today_watch_channels`). Android falls back to
  `other` for categories a language defines but the file lacks (Spanish, Italian and Portuguese
  `many` for millions), so one/other is safe for these seven; a future language needing `few`
  or `many` must provide them (the parity rule then compares against that language's CLDR
  categories, not against English).
- **L10N-FR-51 Numbers** in strings use positional placeholders (`%1$d`, `%2$s`); a string with
  two or more arguments never relies on argument order. Numbers formatted in code for technical
  readouts use `Locale.ROOT`; sizes use `Locale.US` (whole numbers, no separators).
- **L10N-FR-52 Units** hard-coded in English in beta 23 ("min", "h" in the EPG correction; "MB",
  "kB", "B"; "UTC") — rebuild: take them from resources (the Finnish "min", "t" for hours)
  except `UTC` and the size units, which are international.

### 4.7 Messages from code without a screen

- **L10N-FR-60** Repositories, clients and validators never build finished sentences. They raise
  a localised failure carrying a string resource and its arguments (an argument may itself be a
  string resource, such as the name of a field); the screen resolves it in the current language.
  Errors from libraries are shown as their message passed through the secret redactor, or
  `error_unknown` "Unknown error" when empty. (Before this, every import failure spoke Finnish
  whatever the interface language — `LocalizedException.kt`.)
- **L10N-FR-61** A failure stored in the database (a source's last refresh error) is encoded as
  `resource:<id>` + tab-separated arguments (`@<id>` for a resource argument) and resolved on
  display; any other stored text is shown as it is. Resource ids are renumbered between builds,
  so beta 23 accepts an id only when its entry name starts with `error_`, else shows nothing.
  Rebuild: store the resource **entry name** (`error_http_status`), not the numeric id, and keep
  the `error_` prefix check as a guard; this also survives the rebuild's key renames when the old
  names are mapped once during import.

### 4.8 Strings shown outside the app's resources

- The phone setup page: interface strings, HTML-escaped; `lang` attribute = the interface
  language ([11](11-phone-setup.md) PHONE-FR-33; below Android 13 see L10N-FR-05).
- The addon phone page: English only, fixed in code ([11](11-phone-setup.md) PHONE-FR-64, Q-03).
- About's "What's new" notes: the release body from the public release list, English whatever
  the interface language ([72](72-updates-about-diagnostics.md)).
- The diagnostics file: English labels by design (developer-facing).
- Time-zone region names ("Europe", "America"): raw id prefixes, not translated (Q-07).
- Content-language heuristics are independent of the interface language: the guide's genre
  accents match English and Finnish category stems (`sport`/`urheilu`, `news`/`uutis`…,
  [20](20-live-tv-guide.md) GUIDE-FR-58); the preferred-copy options name Finnish audio and
  subtitles ([42](42-library-organization.md), Q-06); sport channel tags use country/language
  codes ([60](60-sohva-sport.md)).
- Case-insensitive search folds only ASCII letters in SQL (`NOCASE`), so "ä" does not match "Ä"
  ([03](03-search.md)); the rebuild's search normalisation must fold Finnish and Swedish letters.

### 4.9 Rules for adding strings (rebuild)

- **L10N-FR-80** **Every viewer-facing text is a resource** in the feature module that shows it
  ([plan/03](../plan/03-architecture.md) modules), one `strings.xml` per module and language;
  no text in code except brand, protocol and codec names, which are untranslatable resources
  when shown.
- **L10N-FR-81** **Keys are short and semantic**, prefixed by feature (`settings_`, `guide_`, `phone_`,
  `addon_`), snake_case, naming the meaning, not the words (not
  `addon_ui_use_the_same_trusted_wi_fi_as_this_tv_this_temporary_http_connecti`). Texts imported
  from `reference/strings/` keep their wording in every language even when keys are renamed;
  keep a one-time old→new key map for stored failure names (L10N-FR-61).
- **L10N-FR-82** **All seven languages in the same change**: English and Finnish reviewed; drafts may be
  machine-drafted but must be complete. A language keeps its draft marker in the picker until the
  owner removes it; `translate_help` lists the remaining drafts.
- **L10N-FR-83** **Placeholders are positional** (`%1$s`), full sentences are never concatenated, and word
  order lives in the translation. Counts use `<plurals>` with every category the language needs.
- **L10N-FR-84** **Mark untranslatable** brand names, theme proper names and language endonyms with
  `translatable="false"` and keep them only in `values/`.
- **L10N-FR-85** **Escape and typography**: `\'` for apostrophes, `&amp;` for &, the ellipsis `…`, the en dash
  `–` for ranges, the minus `−` (U+2212) for negative offsets, `·` as the separator with spaces
  around it. HTML contexts (the phone page) escape at render time.
- **L10N-FR-86** **Store keys, not text**: preferences, databases and backups hold ids, enum names or resource
  entry names, never a translated sentence.
- **L10N-FR-87** **Length**: write the English as short as the meaning allows; check German at interface size
  Normal; components ellipsize or wrap by design, never clip.
- **L10N-FR-88** **Tests gate it**: the parity test covers every file; pseudo-locales are enabled in debug
  builds for screenshot runs; a source scan forbids literal text in UI code.
- **L10N-FR-89** **Content descriptions** for icons and QR codes are strings like any other.

## 5. Screen anatomy

- Interface language picker: the Settings single picker ([70](70-settings.md) SET-FR-21), eight
  rows, no descriptions; each label is the language's own name with the draft marker in that
  language: `interface_language_en` "English", `_fi` "Suomi", `_es` "Español (borrador)", `_pt`
  "Português (rascunho)", `_de` "Deutsch (Entwurf)", `_sv` "Svenska (utkast)", `_it` "Italiano
  (bozza)"; `interface_language_system` is translated ("System default", fi "Järjestelmän kieli",
  de "Systemstandard").
- "Help translate Sohva TV": About, group heading `translate_title` "Translations", value row
  `translate_help_title` "Help translate Sohva TV" with `translate_help` "Spanish, Portuguese,
  German, Swedish and Italian are drafts. Corrections are welcome in the public repository.",
  Info icon, opens `https://github.com/Macstered/Sohva-TV` (`settings-translate-help`).
- Text length: German, Portuguese and Spanish labels are often longer than English; beta 10–12
  receipts record labels that overflow in those three, Sohva Sport worst. Every component in
  [design 02](../design/02-components.md) must either ellipsize with a visible "…" or wrap to a
  defined number of lines; buttons never clip mid-word.
- Fonts: the system `sans` family; no font files ship. Metadata in Greek, Cyrillic, Arabic,
  Japanese, Korean or Chinese relies on the TV's system fonts; Arabic titles are right-to-left
  text inside the left-to-right layout (the manifest declares `supportsRtl`, no RTL interface
  language ships).

## 6. Data

### 6.1 Stored values

| What | Where | Default |
|---|---|---|
| Interface language | API ≥ 33: platform per-app locale; API < 33: SharedPreferences `streammate_locale` key `language_tag` | none (follow the system) |
| Metadata language | DataStore `streammate_preferences` key `metadata_language` (backup `metadataLanguage`) | derived (L10N-FR-22) |
| Time zone | DataStore key `time_zone` (backup `timeZoneId`, `timeZoneFollowsDevice`) | absent = TV's own |
| Stored failure messages | source refresh state rows (`resource:<id>…`) | — |

The interface language is not in backups ([70](70-settings.md) §6.3, Q-05 there).

### 6.2 String organisation (beta 23, `reference/strings/`)

Entries = `<string>` + `<plurals>` (no `<string-array>`). Per module and language:

| Module | Files | en | fi | es | pt | de | sv | it |
|---|---|---|---|---|---|---|---|---|
| `app` | `strings.xml` | 119 | 118 | 118 | 118 | 118 | 118 | 118 |
| `app` | `strings_addons.xml` | 298 | 298 | 298 | 298 | 298 | 298 | 298 |
| `app` | `strings_addon_entry.xml` | 1 | – | – | – | – | – | – |
| `app` | `strings_trakt.xml` | 25 | 24 | – | – | – | – | – |
| `core` | `strings.xml` | 56 | 54 | 54 | 54 | 54 | 54 | 54 |
| `iptv` | `strings.xml` | 827 | 819 | 819 | 819 | 819 | 819 | 819 |
| `iptv` | `strings_trakt.xml` | 1 | 1 | – | – | – | – | – |
| `sportmate` | `strings.xml` | 113 | 113 | 113 | 113 | 113 | 113 | 113 |
| `app` demo flavour | `strings.xml` | 1 | – | – | – | – | – | – |
| `app` lab flavour | `strings.xml` | 1 | – | – | – | – | – | – |
| **Total** | | **1,442** | **1,427** | **1,402** | **1,402** | **1,402** | **1,402** | **1,402** |

Grand total 9,879 entries. Plurals: 24 per language (L10N-FR-50), all `one`/`other`. Strings with
format arguments (English): `app` 33, `core` 9, `iptv` 79, `sportmate` 16.

**Untranslatable** (`translatable="false"`, English only, 15): `app` `home_discover`
("Discover", in `strings_addon_entry.xml`), `lab_safety_notice`, `trakt_title`; `core`
`brand_sohva_tv`, `brand_sohva_sport`; `iptv` the six theme names `color_theme_nordic_slate`,
`_cozy_hearth`, `_cyber_plum`, `_nord`, `_everforest`, `_kanagawa`, and
`update_disabled_development`, `update_notes_for`; flavours `app_name_demo`, `lab_app_name`.
The seven `interface_language_*` endonyms are marked translatable but identical in every file;
the rebuild makes them untranslatable and keeps one copy.

### 6.3 Missing translations

- **Trakt strings, 25 per draft language (125 in all)**: `app/strings_trakt.xml` exists in
  English and Finnish only (24 translatable: `trakt_profile`, `trakt_connect`,
  `trakt_reconnect`, `trakt_disconnect`, `trakt_cancel`, `trakt_not_connected`,
  `trakt_connected`, `trakt_reauthorization`, `trakt_waiting`, `trakt_scan`,
  `trakt_qr_description`, `trakt_unconfigured`, `trakt_help`, `trakt_disconnect_help`,
  `trakt_cancelled_background`, `trakt_denied`, `trakt_expired`, `trakt_code_unusable`,
  `trakt_rate_limited`, `trakt_connection_error`, `home_watch_next`, `home_recommended`,
  `trakt_title_loading`, `trakt_title_unavailable`) plus `iptv/strings_trakt.xml`
  `settings_section_accounts`. In Spanish, Portuguese, German, Swedish and Italian the Accounts
  rail label, the Trakt panel and the Home rows "Watch next" and "Recommended" show English. The
  files carry `tools:ignore="MissingTranslation"` and the parity test reads only `strings.xml`,
  so nothing fails.
- **No other key is missing**: every `strings.xml` and `strings_addons.xml` has the full English
  key set, the same plural forms and the same placeholders in every language (checked by
  `TranslationParityTest` and `AddonTranslationsTest`, and recounted for this kit).
- **Identical to English** (not missing, listed so nobody "fixes" them): cognates and names such
  as "Baseball", "Rugby", "Formula 1", "Sohva Sport", "TMDB %1$s", "%1$d MB", "Playlist",
  "Standard", "Normal", "Info" — from 3 (Finnish, `app`) to 53 (German, `iptv`) per file.
- **Text in code, not in resources**: the addon phone page (English); the EPG correction units;
  the image cache size units; "M3U" / "Xtream" source kinds; the "TMDB" and "TVmaze" button
  labels; default source names "IPTV n" / "Xtream n"; time-zone region names; the diagnostics
  file. The rebuild moves every viewer-facing one into resources (brand and protocol names stay
  untranslatable resources).
- **Probably unused keys**: a code search finds about 111 keys no code references (for example
  `metadata_tmdb_enabled` "● TMDB enabled", `auto_frame_rate_on/off`,
  `playback_decoder_fallback_help`, `settings_source_status_*`, `home_*_description`,
  `today_*_section`, older `addon_*` keys). The rebuild imports only keys a spec names.
- **Spelling**: the English copy mixes "Color theme" (`color_theme_title`, `color_theme_help`)
  with British "colour", "programme", "licences" elsewhere (Q-05).

## 7. External interfaces

- `android.app.LocaleManager` (`applicationLocales` get/set, API 33+); `LocaleList`; manifest
  `android:localeConfig="@xml/locales_config"`.
- `Configuration.setLocales` / `setLocale` and `createConfigurationContext` below 33.
- `android.text.format.DateFormat.is24HourFormat(context)` and `getBestDateTimePattern(locale,
  skeleton)` for the clocks.
- TMDB `language` query parameter (the 22 tags) ([41](41-metadata-enrichment.md)).
- The public repository `https://github.com/Macstered/Sohva-TV` for translation corrections.

## 8. Edge cases and limits

- Android 12 and below with a chosen language: application-context strings in the system
  language (L10N-FR-05); switching back to System default keeps the old JVM default locale
  (L10N-FR-06).
- A system language Sohva TV does not ship (French, Norwegian): English interface; the VOD
  language list still offers French and Norwegian as content languages (they are content, not
  interface). Norwegian content is matched as `no` while TMDB uses `nb-NO` — separate lists,
  separate purposes.
- A platform per-app choice with a region (`pt-BR`, `de-AT`) reads as its language.
- Daylight-saving changes: an hour repeats or disappears in the guide ruler on the transition
  night; times are always formatted from epoch in the zone, never by adding hours to a local
  time.
- 12-hour TVs: clocks are wider; the header layouts reserve width for "10:30 PM".
- The TV's font lacks a script (rare on Android TV): metadata titles show missing glyphs; not
  handled.
- Interface size Smaller (70 %) with German: more text fits; Normal (100 %) with German is the
  overflow worst case for screenshot tests.

## 9. Lightweight by design

- **Start-up**: below Android 13 one small SharedPreferences read (one key) in
  `attachBaseContext` — no DataStore, no disk scan; from 13 nothing (the platform applies it).
- **Formatters** are created once per (locale, zone, 24-hour setting) and shared; beta 23 builds
  a `ZoneId` on every `formatTime` call (every programme block, every composition) and a new
  `DateTimeFormatter` on every player range — the rebuild never allocates a formatter, zone or
  pattern in composition or per item. Precompute the ruler's labels once per window.
- **Pickers** that show language names compute them once per interface locale (22 metadata
  names, 11 content names).
- **APK size**: beta 23 declares no locale filter, so library translations for dozens of
  languages ship in the APK. Rebuild: restrict packaged locales to the seven
  (`androidResources.localeFilters`), which also keeps Media3 and AndroidX texts consistent with
  the interface.
- **Parity checks run at build time** (unit tests), never at run time.
- **No run-time translation, no downloadable language packs, no per-string lookups on the main
  thread beyond normal resource reads.**

## 10. Lessons from the current app

1. **Finnish as the default resource set** made every untranslated locale fall back to Finnish;
   English became the default just before beta 1 (commit `9e09ded`, 29 August 2026, which also
   added the picker and `locales_config`).
2. **Sentences built in repositories** froze messages in the language they were typed in
   (Finnish); failures now carry resources and are resolved on screen (`LocalizedException.kt`).
3. **Stored failures by numeric resource id** break when ids are renumbered between builds; beta
   23 guards with the `error_` entry-name check (commit `a6674f9`). Store names.
4. **A drafted translation with a missing `%2$s` crashes the screen that formats it**: the
   parity test (beta 10, commit `080719d`) fails the build on a missing key, an unknown key, a
   different plural form or a different placeholder multiset. Extend it to every string file.
5. **Drafts overflow**: Spanish, Portuguese and German labels overflow their space, Sohva Sport
   worst (beta 10–12 receipts). Test long languages in screenshots.
6. **API 23 has no `Configuration.getLocales()`**: lint failed CI from beta 6 to 11 until a
   helper read the single `locale` field there (commit `ef38f29`).
7. **The locale lives outside DataStore** so it can be read synchronously before the first frame
   (ANR otherwise).
8. **Tests changing the language must restore it**: `wrap()` changes the process-wide default
   locale, and a leftover chosen language broke later device tests (`AppLocaleTest`, plan/08
   7.5).
9. **The guide's clock defaulted to Helsinki** and a tester six hours away read every programme
   wrong (beta 6); an absent zone now means the TV's own.
10. **Channel-name kickoff times** with explicit zones (CET, EET, UTC±n) must not be read in the
    app zone (betas 19–20, [60](60-sohva-sport.md)).

### Open questions

- Q-01 One time format everywhere (locale separator, TV 12/24-hour setting) as L10N-FR-41
  proposes, or keep the fixed `HH.mm` in the guide, Home hero and Sohva Sport?
- Q-02 Should the default metadata language follow the *effective* interface language (Finnish
  on a Finnish TV with System default)?
- Q-03 Translate the Trakt strings and the addon phone page into the five drafts?
- Q-04 Portuguese: keep generic `pt` in Brazilian Portuguese, or ship `pt-BR` (and later
  `pt-PT`)?
- Q-05 English spelling: "Color theme" or "Colour theme" (the rest of the copy is British)?
- Q-06 Should "Finnish audio" / "Finnish subtitles" in the preferred-copy setting become "audio
  / subtitles in <the viewer's language>"?
- Q-07 Translate the time-zone region names (about ten strings × 7)?
- Q-08 When may a draft lose its marker — after one native reviewer, or the owner's call per
  language?

## 11. Acceptance tests

Unit (JVM)
- Parity over **every** string file of every module and language: same keys as English (minus
  untranslatables and an explicit, reviewed list of allowed English fallbacks), same kind
  (string/plural), plural categories per CLDR for that language, same placeholder multiset
  (`%1$s`, `%2$d`, `%1$.1f`, bare `%d`; `%%` ignored) — mirrors `TranslationParityTest` and
  `AddonTranslationsTest`.
- Every supported tag has a translation folder in every module; `locales_config.xml` lists
  exactly the supported tags; the picker offers System default plus each tag.
- Metadata languages: 22 unique tags; `defaultFor("fi")`, `("fi-FI")` → `fi-FI`; `("en")`,
  `("sv")`, `(null)` → `en-US`; `sv` alone unsupported (`MetadataLanguagesTest`).
- Stored failure: encode/resolve round trip by entry name; an unknown name or a non-`error_`
  name resolves to nothing; plain text passes through.
- Formatting service: 24-hour and 12-hour output for `en` and `fi`; the app zone applied; an
  invalid zone id falls back to the device zone; a DST night produces monotonic epoch order.
- No viewer-facing literal strings in UI code (a lint rule or a source scan with an allowlist).

Instrumentation
- `AppLocaleTest` intent: defaults to the system; remembers a supported choice; ignores `ja`;
  below 33 wrapping resolves `error_unknown` as "Tuntematon virhe" for `fi`, from 33 the context
  is returned unchanged; every offered tag can be stored. Each test restores the JVM default.
- Choosing Suomi in Settings recreates the activity and shows "Asetukset"; System default
  returns to the device language.
- API 31 emulator with Suomi chosen and an English system: the phone page, a reminder
  notification and a backup error appear in Finnish (L10N-FR-05); switching back to System
  default shows English weekday names in the guide at once (L10N-FR-06).
- Search results show times in the app zone.
- Screenshot tests of every screen in German and with a pseudo-locale (expanded `en-XA`) at
  interface size Normal: no clipped button text, no overlap.

Manual
- On the Elisa box and the Shield, run through Home, guide, player, Sohva Sport and Settings in
  Finnish and Spanish; with the TV set to 12-hour time, check the clocks and (per Q-01) the
  guide.

Performance (low-end class)
- Scrolling the guide for 30 s allocates no `DateTimeFormatter`, `ZoneId` or pattern objects
  (allocation tracking), and first frames after a language restart meet the cold-start budget
  (Home within 4 s on the low-end box).

## 12. Reference: current code map

- `core/.../app/AppLocale.kt` — supported and draft tags, storage per API level, `wrap`,
  `primaryLocale()`.
- `app/src/main/res/xml/locales_config.xml`; `app/src/main/AndroidManifest.xml`
  (`android:localeConfig`, `supportsRtl`).
- `app/.../app/MainActivity.kt` — `attachBaseContext` wrapping.
- `iptv/.../feature/settings/SettingsLabels.kt` — interface and content language option lists.
- `iptv/.../feature/settings/SettingsScreen.kt` — language picker, metadata language labels.
- `core/.../app/MetadataLanguages.kt` — the 22 tags and the default rule.
- `core/.../app/AppPreferencesRepository.kt` — `metadata_language`, `time_zone`,
  `deviceTimeZoneId()`.
- `core/.../core/error/LocalizedException.kt` — localised failures, `StoredFailureMessage`.
- `iptv/.../feature/guide/GuideCommon.kt`, `GuideGrid.kt` — guide time and day formats.
- `app/.../feature/home/HomeScreen.kt`, `sportmate/.../feature/today/TodayScreen.kt`,
  `iptv/.../feature/player/PlayerOverlays.kt`, `PlayerFormatting.kt`,
  `app/.../feature/search/SearchScreen.kt` — clocks and times.
- `sportmate/.../sports/repository/DirectSportsRepository.kt` — kickoff labels.
- `iptv/.../feature/settings/TimeZonePicker.kt`, `ArtworkCache.kt`, `SettingsLabels.kt`
  (`formatEpgOffset`) — offsets, sizes, units.
- String files: `{app,core,iptv,sportmate}/src/main/res/values*/strings*.xml`,
  `app/src/{demo,lab}/res/values/strings.xml` (copies in `reference/strings/`).
- Tests: `app/src/test/.../app/TranslationParityTest.kt`,
  `app/src/test/.../addons/AddonTranslationsTest.kt`,
  `app/src/androidTest/.../app/AppLocaleTest.kt`, `core/src/test/.../app/MetadataLanguagesTest.kt`.
