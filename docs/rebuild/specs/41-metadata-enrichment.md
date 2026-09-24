# Metadata enrichment

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

Layout measurements are already extracted in
[design/screens/settings.md](../design/screens/settings.md) §6 "Library (METADATA)" (Settings
rows) and [design/screens/movies-and-series.md](../design/screens/movies-and-series.md) §5 (match
picker); this spec repeats a measurement only where behaviour depends on it. The walls, details
pages, copy folding and genre destinations that consume this feature are
[Movies and series](40-movies-and-series.md) (cited as VOD-FR-nn); hide/show rules, film-identity
aliases and custom genre groups are [Library organisation](42-library-organization.md) (ORG-FR-nn).

## 1. Summary

Metadata enrichment is optional. With the viewer's own TMDB credential (a v3 API key or a v4 Read
Access Token) and/or the keyless TVmaze switch, Sohva TV looks up the provider's films, series,
episodes and guide programmes and adds what the provider does not give: a clean title, a plot, a
poster and backdrop, year, runtime, rating, cast, similar films and one genre from a fixed
22-genre vocabulary. A background worker enriches the whole VOD catalogue while the app is not
in front, so the walls show real titles and posters and the Genres view can sort titles; on-demand
lookups fill the details pages, the guide hero and the Home hero. Matching is deliberately
conservative (a wrong poster is worse than none), so a "Wrong details?" picker lets the viewer
choose the right record by hand. The same title cleaning produces the **work key** that tells two
playlists' copies of one film apart from different films. The rebuild must keep all of this cheap
on a 2 GB Cortex-A35 box with a 200,000-film catalogue: the old identity pass ran about twenty
regexes per title, and two whole-catalogue background jobs once ran the app out of memory.

## 2. Feature checklist

Settings (Library section)
- META-01 Group "Metadata and images (optional)" with the TMDB attribution logo.
- META-02 Switch "TMDB titles, plots and artwork"; it refuses to turn on without a key ("Enter a TMDB key below first.").
- META-03 TMDB credential field (API key or Read Access Token, masked, up to 2,048 characters), encrypted on the device.
- META-04 "Save key" stores the credential and clears all metadata so everything is looked up again.
- META-05 "Test TMDB" checks the typed credential against TMDB and says whether it works.
- META-06 Switch "TVmaze series information" (no key).
- META-07 Status line under the group for every save, test and clear result.
- META-08 Metadata language: 22 languages, default Finnish for a Finnish interface and English (US) otherwise; a change redoes the library's titles and posters in the background.
- META-09 "Clear metadata cache" (Maintenance group) and buttons opening the TMDB and TVmaze websites.

Background enrichment of the library
- META-10 Every film and series of the active catalogues of enabled sources is looked up once: replacement title, poster (only where the provider has none), TMDB id and primary genre.
- META-11 Replacement title shown on walls, Home and Search; the TMDB poster fills a missing provider poster.
- META-12 One primary genre per title in the 22-genre vocabulary, from TMDB's genre ids (TVmaze gives none); vocabulary versioned so a change revisits every title.
- META-13 Runs only while the app is not in front (so never during playback), 30 s after leaving, in 4-minute runs, and is cancelled when the viewer returns.
- META-14 Durable queue that survives restarts, picks up imports and sweeps titles that left the catalogue.
- META-15 Provider failures retried with exponential backoff (15 min doubling to 24 h); real misses recorded as "no match" and not retried.

On-demand lookups
- META-16 Film page: TMDB details with runtime, rating, up to 8 cast members and up to 20 similar films, in the metadata language.
- META-17 Series page: TMDB series with details (runtime, rating, cast), TVmaze as a fallback.
- META-18 Selected episode: TMDB episode or TVmaze episode by number, 350 ms after the selection rests.
- META-19 Guide hero: programme lookup by title 350 ms after the selection rests (synopsis, year, still, "TMDB x.x" rating chip).
- META-20 Home hero: film, series/episode and live-programme lookups; Trakt titles by TMDB id in the metadata language.
- META-21 A missing library poster is repaired from the details record when a page opens.
- META-22 "Source: TMDB" / "Source: TVmaze" opens the matched record's web page (film page, series page, guide hero).

Matching and keys
- META-23 Provider-title cleaning before searching: language and quality prefixes, bracketed decorations, season/episode markers, trailing years and quality tags.
- META-24 Conservative automatic matching: confidence ≥ 0.92 and a 0.08 lead over the runner-up, with a popularity tie-break for exact titles.
- META-25 Film work key: `tmdb:<id>` once matched, else `name:<cleaned title>:<year>`; the identity of a film across playlists.
- META-26 Film identity pass folds matched copies together (background priority, paged).

Fix a match
- META-27 "Wrong details?" match picker: searches the cleaned provider name on open; manual search; results with poster, title, year and a two-line overview.
- META-28 Choosing a result pins it: it never expires and automatic matching never replaces it; the library title, poster and genre follow.
- META-29 "Undo my choice" (only when pinned) hands the title back to automatic matching.

Caches, limits, attribution
- META-30 Memory cache of 256 lookups plus a database cache: positive 30 days (TMDB) / 24 h (TVmaze), negative 7 days, pinned forever.
- META-31 Identical concurrent lookups share one request; leaving a screen cancels its request.
- META-32 TVmaze 429 handling (one retry after Retry-After, 1–5 s); responses capped at 2 MiB; text cut to 8,000 characters; artwork only over https.
- META-33 Localised errors for HTTP failures, oversized responses, invalid or missing key and a failed save.
- META-34 Attribution: TMDB logo in Settings, TMDB and TVmaze (CC BY-SA) notices and the metadata disclosure on the legal screen ([About](72-updates-about-diagnostics.md)).

## 3. Entry points and navigation

- **Settings › Library** (section `METADATA`, rail label `settings_section_metadata` "Library",
  Info icon; [Settings](70-settings.md)). Choosing the section focuses the TMDB switch. Back
  follows the Settings rules (pane → rail → leave).
- **Match picker**: "Wrong details?" (`match_picker_open`) on the film and series pages
  (VOD-FR-67, VOD-FR-104…106). It is a platform dialog over the page; Back or "Close" closes it.
  Beta 23 lets focus fall where the platform puts it; **rebuild:** focus returns to "Wrong
  details?" before the dialog hides ([Lessons](../plan/08-lessons-learned.md) 4.1).
- **Attribution buttons** open the record URL with the platform URI handler; failure (no browser
  on the TV) is ignored and focus stays.
- No destination of its own. Settings rows for the preferred copy (VOD-FR-32), "Manage groups &
  content", "Groups of your own" ([Library organisation](42-library-organization.md)) and the image
  cache (VOD-FR-108) sit in the same section but are specified elsewhere.

## 4. Behaviour

### 4.1 Settings

- META-FR-01 State: `tmdbEnabled`, `tmdbCredential`, `tvmazeEnabled` from the encrypted settings
  store (§6). "TMDB enabled" is read as `stored switch AND credential not blank`; both switches
  default off, so a fresh install makes **no** metadata request.
- META-FR-02 TMDB switch (`metadata_tmdb_switch` "TMDB titles, plots and artwork", subtitle
  `metadata_description` "Titles, posters and plots for the guide, films and series. Optional; the
  app works without them."). Turning it **on** with a blank credential field does not change the
  switch and sets the status to `metadata_key_required` "Enter a TMDB key below first.". Otherwise
  the new value is saved at once with the current field and TVmaze values, **without** clearing
  the cache (META-FR-05).
- META-FR-03 Credential field (`metadata_tmdb_token` "TMDB API key or Read Access Token", Key
  icon, masked, edit on OK, input cut to 2,048 characters). Editing does not save.
- META-FR-04 "Save key" (`metadata_save_key`, Save icon): saves switch + field + TVmaze and then
  **clears all metadata** (META-FR-80); status `metadata_saved` "Metadata settings saved securely"
  or the error text. Saving does not turn TMDB on. Validation on save: the credential is trimmed;
  longer than 2,048 characters or containing CR/LF → `error_tmdb_key_invalid` "The TMDB API key or
  read access token is invalid"; switch on with a blank credential → `error_tmdb_key_required`
  "TMDB needs an API key or a read access token"; a failed commit → `error_metadata_settings_save`
  "Metadata settings could not be saved". A blank credential removes the stored one.
- META-FR-05 **Rebuild:** "Save key" clears only negative cache entries (a new key may find what
  the old one missed) and only when the credential actually changed; positive entries, library
  titles and pins stay. Beta 23 wipes the whole library enrichment on every press, even with an
  unchanged key, and the worker then re-looks-up the entire catalogue.
- META-FR-06 "Test TMDB" (`metadata_test_tmdb`, enabled when the field is not blank and nothing is
  busy): `GET /3/configuration` with the **typed** credential (not the saved one); success →
  `metadata_tmdb_test_ok` "TMDB connection and credential work"; failure → the localised error,
  e.g. `error_metadata_http` "The TMDB metadata service responded with 401".
- META-FR-07 TVmaze switch (`metadata_tvmaze_switch` "TVmaze series information", Guide icon):
  saves at once without clearing the cache.
- META-FR-08 Status line: one text under the first group, 12 sp, colour `focus` (success and error
  alike), shown once any action has produced a message; it is not cleared by later navigation.
  While an action runs every control of the group is disabled (`busy`). **Rebuild:** errors in
  `danger` ([design/screens/settings.md](../design/screens/settings.md) §9).
- META-FR-09 Metadata language row (`metadata_language_title` "Metadata language", help
  `metadata_language_help` "TMDB titles, plots and artwork in this language; changing it refreshes
  them in the background.", Info icon): value = the language's display name. OK opens a single
  picker (560 dp, [design/screens/settings.md](../design/screens/settings.md) §5) of the 22 tags of
  META-FR-10, labelled with the platform's display name of the tag **in the interface language**,
  first letter capitalised (e.g. "English (United States)", "Suomi (Suomi)"); it opens scrolled to
  and focused on the current value. Choosing a different tag saves it and then runs META-FR-79
  (reset and restart); choosing the current one only closes the picker.
- META-FR-10 Languages (TMDB `language` values), in picker order: `en-US`, `fi-FI`, `sv-SE`,
  `nb-NO`, `da-DK`, `de-DE`, `nl-NL`, `fr-FR`, `es-ES`, `pt-PT`, `pt-BR`, `it-IT`, `pl-PL`,
  `cs-CZ`, `hu-HU`, `ru-RU`, `tr-TR`, `el-GR`, `ar-SA`, `ja-JP`, `ko-KR`, `zh-CN`. Default when
  nothing (or an unsupported value) is stored: `fi-FI` when the stored interface language starts
  with `fi`, else `en-US`. Device-wide; in backups (`metadataLanguage`, unsupported → the default).
- META-FR-11 Maintenance group (`maintenance_title` "Maintenance"): compact buttons "Clear metadata
  cache" (`metadata_clear_cache`; runs META-FR-80; status `metadata_cache_cleared` "Metadata cache
  cleared"), "TMDB" (opens `https://www.themoviedb.org`), "TVmaze" (opens
  `https://www.tvmaze.com`). No confirmation (see open question 6).
- META-FR-12 The metadata language is pushed to the metadata repository whenever the preference
  changes (SHELL-FR-75); lookups that name no language use it.

### 4.2 Providers

- META-FR-13 Two providers, always consulted in this order: **TMDB** (id `tmdb`, attribution
  "TMDB"), then **TVmaze** (id `tvmaze`, attribution "TVmaze"). A provider takes part when it is
  enabled and supports the lookup type: TMDB supports every type; TVmaze every type except MOVIE.
  "Metadata enabled" (used by every screen before looking anything up) = at least one provider
  enabled.
- META-FR-14 TMDB credential: trimmed; if it matches `[A-Fa-f0-9]{32}` it is a v3 **API key** and
  is sent as the query parameter `api_key`; otherwise it is a v4 **Read Access Token** sent as
  `Authorization: Bearer <token>`. Every request carries `Accept: application/json` and
  `User-Agent: SohvaTV/0.1 (Android TV; personal use)`. TVmaze needs no credential.
- META-FR-15 Lookup types: `MOVIE`, `SERIES`, `EPISODE` (series title + season + episode),
  `PROGRAMME` (a guide programme title of unknown kind). Wire values `movie`, `series`, `episode`,
  `programme`.

### 4.3 Lookups: sanitising and keys

- META-FR-16 A lookup is `(type, title, year?, season?, episode?, language?)`. Before anything else
  it is **sanitised**:
  - title = `searchTitle(title)` (4.4), cut to 160 characters;
  - year = the given year, else `yearFromTitle(original title)`; kept only in 1870…2200;
  - season kept only in 0…10,000, episode only in 0…100,000;
  - language = the given one, else the current metadata language; if it does not match
    `[a-z]{2}(?:-[A-Z]{2})?` it becomes `fi-FI` (beta 23's hard default; **rebuild:** `en-US`
    or the Settings default of META-FR-10);
  - the lookup is **refused** (no request, "no match") when `normalizeTitle(title)` is shorter
    than 2 characters.
- META-FR-17 Lookup key = lower-case hex SHA-256 of these fields joined by U+001F: `"4"` (key
  version), type wire value, `normalizeTitle(title)`, year or empty, season or empty, episode or
  empty, language lower-cased. The key is the same for every copy with the same cleaned name and
  year, whichever playlist carries it. By-id lookups (META-FR-45) use the plain key
  `by-id:<TYPE>:<externalId>:<language>` (TYPE is the enum name, e.g. `MOVIE`).
- META-FR-18 **Rebuild:** a version constant covers the normaliser as well as the key layout; when
  the normaliser changes, the version changes and old entries are simply never hit again (they
  expire).

### 4.4 Title cleaning (normative)

Three functions are shared by lookups, matching, work keys, the film page title (VOD-FR-61), the
match picker's first query and Similar resolution (VOD-FR-71). The regular expressions below are
the **definition** of beta 23's behaviour (Kotlin/Java syntax, applied with `replace` = all
non-overlapping matches left to right unless anchored). The rebuild implements them **by hand**
(§9.1) and proves equality with these patterns in tests (§11).

Character notes: `\s` is `[ \t\n\x0B\f\r]`; `(?i)` is ASCII case-insensitive; `•` U+2022, `·`
U+00B7, `–` U+2013, `—` U+2014.

**Step A — decoration groups.** Replace every match of
`\[[^\[\]\r\n]*\]|\([^()\r\n]*\)|\{[^{}\r\n]*\}` with a space (one pass; a bracket group may
contain the other kinds of bracket but not its own; nested same-kind groups lose only the inner
one). If the result contains no letter or digit (Unicode), use instead the original with every
character of `[](){}` replaced by a space (so `[REC]` stays "REC").

**Step B — language prefix** (once, anchored):
`(?i)^\s*(?:[\[(](?:fi|fin|en|eng|sv|swe|da|dan|no|nor|de|ger|fr|fre|es|spa)[\])]|(?:fin|eng|swe|dan|nor|ger|fre|spa)\s*[|•·-])\s*`
→ one space. A bracketed 2/3-letter code, or a 3-letter code followed by optional spaces and one
of `| • · -`.

**Step C — decoration prefix** (once, anchored):
`(?i)^\s*(?:(?:4k|uhd|fhd|hd|hdr(?:10\+?)?|dolby\s*vision|dv|x26[45]|hevc|nc|nordic|multi(?:[- ]?(?:subs?|subtitles?|audio))?|vip|vod|fi|fin|en|eng|sv|swe|da|dan|no|nor|de|ger|fr|fre|es|spa)(?=\s|[|•·:–—-]|$)\s*(?:[|•·:–—-]+\s*)?)+(?=\S)`
→ one space. One or more whole tokens from the list, each ending at a space, one of
`| • · : – — -` or the end, each optionally followed by spaces and a run of those delimiters and
spaces. The removed prefix is the **longest** such prefix that is still followed by a non-space
character, so a title made only of tokens keeps its last token ("4K HD" → "HD"; "4K --" → "-").

**Step D — season/episode markers** (everywhere): `(?i)\b(?:s\d{1,2}e\d{1,3}|k\d{1,2}\s*j\d{1,3})\b`
→ space. `S02E06`, and the Finnish `K2 J6` / `K2J6`; the digit runs must be whole (word
boundaries on both sides).

**Step E — bracketed technical tags** (everywhere):
`(?i)\[(?:multi[- ]?(?:subs?|subtitles?|audio)(?:\s*&\s*(?:subs?|subtitles?|audio))?|imdb(?:\s*(?:top\s*\d+|\d+(?:\.\d+)?))?|4k|uhd|fhd|hd|hdr(?:10\+?)?|dolby\s*vision|only\s+on[^]]+devices?|x26[45]|hevc)\]`
→ space. After Step A no complete bracket group remains, so this step almost never fires; it is
kept for equality.

**Step F — trailing year** (once, at the end): `(?:[\[(](?:19|20)\d{2}[\])]|\s+(?:19|20)\d{2})\s*$`
→ space. A bracketed year 1900–2099 (brackets may be mismatched), or a year preceded by at least
one space, then only spaces to the end. "1917" alone is not preceded by a space and survives.

**Step G — trailing technical suffix** (once, at the end):
`(?i)(?:\s*(?:[|•·]|[-–—])\s*)?(?:4k|uhd|fhd|hd|hdr(?:10\+?)?|dolby\s*vision|x26[45]|hevc|multi(?:[- ]?(?:subs?|subtitles?|audio))?(?:\s*&\s*(?:subs?|subtitles?|audio))?)(?:\s+(?:4k|uhd|fhd|hd|hdr(?:10\+?)?|x26[45]|hevc))*\s*$`
→ space. The **longest** suffix made of an optional delimiter (`| • · - – —` with spaces), one
quality or multi tag, then more quality tags separated by spaces. There is **no** word boundary
before the first tag, so a title whose last word merely ends in `hd`, `uhd`, `4k`, `hevc`,
`x264`… loses those letters (kept for equality; open question 7).

- META-FR-20 `searchTitle(s)` = A, B, C, D, E, F, G; then trim the characters space, `-`, `–`,
  `—`, `:` from both ends; then replace runs of `\s+` with one space. Case and accents are kept.
  This is what is sent to providers and what the film page shows before metadata arrives.
- META-FR-21 `normalizeTitle(s)` = A; Unicode **NFKD**; lower-case (locale-independent); B, C, D,
  E, F, G; replace runs of `[^\p{L}\p{N}]+` with one space; trim; collapse spaces. Because NFKD
  splits accents into separate combining marks that are neither letters nor digits, "Amélie"
  becomes "ame lie" here (the work key removes the marks first, META-FR-59).
- META-FR-22 `yearFromTitle(s)` = the first match of Step F's pattern in the **original** title,
  its digits read as a number; a year elsewhere than at the end is not found ("Dune (2021) 4K" →
  none).
- META-FR-23 Examples (from the beta 23 tests; all must hold in the rebuild):

  | Input | searchTitle | normalizeTitle | yearFromTitle |
  |---|---|---|---|
  | `The Bear (2022) S02E06` | The Bear | the bear | none (not at the end) |
  | `The Bear (2022)` | The Bear | the bear | 2022 |
  | `The Shadow's Edge [Multi-Sub&Audio] [4K] [2025]` | The Shadow's Edge | the shadow s edge | 2025 |
  | `[FIN] Foundation - 4K` | Foundation | foundation | none |
  | `ENG \| Avatar UHD` | Avatar | avatar | none |
  | `21 Bridges [Multi-Subs] [2019] [4K]` | 21 Bridges | 21 bridges | none (`[4K]` is last) |
  | `The Shawshank Redemption [IMDB] [1994]` | The Shawshank Redemption | the shawshank redemption | 1994 |
  | `Finding Nemo [KIDS] (Animated) {Multi Audio} [2003]` | Finding Nemo | finding nemo | 2003 |
  | `[REC]` | REC | rec | none |
  | `4K - NC Avatar: The Last Airbender (2024)` | Avatar: The Last Airbender | avatar the last airbender | 2024 |
  | `NC - DuckTales 2017` | DuckTales | ducktales | 2017 |
  | `NORDIC \| The Bear 2022` | The Bear | the bear | 2022 |
  | `HDR10+ - Dune 2021` | Dune | dune | 2021 |
  | `1917` | 1917 | 1917 | none |

  (In the table `\|` stands for a literal `|`. Beta 23's `MetadataMatcherTest` asserts the
  searchTitle column and some of the other cells; the rest follow from the patterns and are
  confirmed by the reference implementation in the rebuild's tests.)

### 4.5 Automatic matching

- META-FR-24 Candidates from one provider are filtered: the candidate's type must equal the
  lookup type, except that a PROGRAMME lookup accepts MOVIE and SERIES candidates; an EPISODE
  lookup keeps only candidates with the same season and episode.
- META-FR-25 Score of a candidate (0…1): `0.88 × title + 0.08 × year + 0.04 × episode`, where
  - title = the best similarity between `normalizeTitle(lookup title)` and
    `normalizeTitle(t)` over the candidate's matching title and alternative titles (TMDB's
    original title when it differs). Similarity: 0 if either is blank; 1 if equal; otherwise the
    larger of the **Dice coefficient** of the two sets of space-separated tokens
    (`2·|A∩B| / (|A|+|B|)`) and `1 − levenshtein(a, b) / max(len a, len b)` (not below 0);
  - year = 1.0 when the lookup has no year; 0.6 when the candidate has none; 1.0 equal; 0.45 one
    year apart; else 0;
  - episode = 1.0 unless it is an EPISODE lookup, then 1.0 when season and episode match, else 0.
- META-FR-26 Ranking: score descending, then the provider's popularity descending (TMDB search
  `popularity`; missing = lowest), then the provider's own result order.
- META-FR-27 Acceptance (ε = 0.000001): the best is rejected when `best + ε < 0.92`. With a
  runner-up, it is also rejected when `best − runnerUp + ε < 0.08`, unless popularity breaks the
  tie: the best has a title (matching or alternative) whose normalised form **equals** the
  normalised lookup title, both popularities are known and ≥ 0, the best's is higher, and either
  the gap is ≥ 10 or (runner-up > 0 and the ratio is ≥ 1.5).
- META-FR-28 Consequences the tests pin down: "Dune (2021)" matches Dune 2021 over Dune Warriors;
  two identical "Top Gear" results are ambiguous (no match); "Avatar" picks the 2009 film
  (popularity 84) over the 1916 one (2); "Foundation" 20 vs 18 stays ambiguous; "The Matrix" does
  not match "Matrix Revolutions"; an episode with the wrong episode number does not match. An
  exact title with a year two or more apart scores exactly 0.92 and **is accepted** when nothing
  else is close (the thresholds allow it).

### 4.6 The lookup flow and its caches

- META-FR-29 `enrich(lookup)` (the ordinary lookup every screen uses):
  1. sanitise (refused → nothing);
  2. identical lookups already in flight share one result (key `lookup:<lookup key>`);
  3. the memory cache answers if it holds an unexpired entry;
  4. for each provider in order that is enabled and supports the type:
     - a **pinned** cache row answers at once whatever its age (META-FR-76); if its genres were
       written under an older vocabulary version, they are refreshed by fetching the pinned record
       by id first;
     - a positive row written under an older genre vocabulary version counts as missing;
     - an unexpired positive row answers; an unexpired negative row skips this provider;
     - otherwise the provider is searched; a failure (network, HTTP, parse) skips this provider
       **without** writing anything; no acceptable match writes a negative row (7 days) and
       continues with the next provider; a match writes a positive row and answers;
  5. nothing → no metadata.
  Beta 23 also deletes every expired, unpinned cache row at the start of each such lookup
  (**rebuild:** once per worker run and in the daily maintenance, §9.6).
- META-FR-30 Memory cache: LRU of 256 entries keyed by lookup key, each with its expiry; a hit past
  its expiry is dropped. `cached(lookup)` reads only this cache, synchronously, so a details page
  can show known metadata in its first frame.
- META-FR-31 Database cache lifetimes: positive TMDB 30 days, positive TVmaze 24 hours, negative 7
  days (both providers), pinned never. Rows are per (lookup key, provider).
- META-FR-32 Cancellation: a shared request is cancelled (including the HTTP call) when the last
  caller waiting for it is cancelled; a caller leaving does not cancel it while others still wait.
  Screens cancel their lookup when the selection changes or the screen leaves.

### 4.7 What each lookup type fetches

- META-FR-33 **MOVIE / SERIES / PROGRAMME search at TMDB**: `search/movie`, `search/tv` or
  `search/multi` (§7) with the sanitised title (≤ 160), language, `include_adult=false`, `page=1`;
  at most 12 results are parsed. `multi` keeps only results whose `media_type` is `movie` or
  `tv`. Each candidate: id, title (`title` or `name`), original title as an alternative when it
  differs, release year (first 4 characters of `release_date` / `first_air_date`), overview,
  poster (`w500`), backdrop (`w780`), genres from `genre_ids` (META-FR-51), popularity. Search
  results carry **no rating** (see META-FR-42).
- META-FR-34 **SERIES** at TMDB: after the search, the matcher picks among the shows; if one is
  picked, `tv/{id}` with `append_to_response=credits` replaces it in the list with title
  (`name`), overview, poster, backdrop, runtime (first positive value of `episode_run_time`),
  rating, cast and genres (from `genres[].id`); if the details call fails the plain list is used.
  The matcher then runs again on the list (so the details call costs a second request only when a
  match is likely).
- META-FR-35 **EPISODE** at TMDB: needs season and episode (else no candidates); the show is
  chosen as a SERIES lookup without the numbers; then `tv/{id}/season/{s}/episode/{e}` gives one
  candidate: external id `<showId>:<s>:<e>`, title = episode `name` (else the show's), overview
  (else the show's), poster = the show's, backdrop = the episode still (`w780`) else the show's,
  year = the show's, season/episode from the response (else the requested), runtime, rating,
  attribution `https://www.themoviedb.org/tv/<id>/season/<s>/episode/<e>`. Failure → no
  candidate.
- META-FR-36 **TVmaze** (SERIES, EPISODE, PROGRAMME): `search/shows?q=<title>` (no language
  parameter; TVmaze answers in English); at most 12 shows; candidate = id, `name`, overview =
  `summary` with HTML removed (tags → space; `&nbsp;` → space, `&amp;` → `&`, `&quot;` → `"`,
  `&#39;` → `'`; whitespace collapsed; blank → none), poster = `image.medium`, backdrop =
  `image.original` (https only), year from `premiered`, attribution = the show's `url` (https) else
  `https://www.tvmaze.com/shows/<id>`. No rating, no cast, **no genres**. EPISODE: the show is
  chosen as above, then `shows/{id}/episodebynumber?season=&number=`: external id = the episode
  `id` (else `<showId>:<s>:<e>`), title `name`, overview `summary` (cleaned), backdrop
  `image.original`, season/number, attribution the episode `url`.
- META-FR-37 **Film details** (film page, VOD-FR-60): `enrich(MOVIE)` first; if that answer came
  from TMDB and its cached row is not yet "details loaded", `movie/{id}` with
  `append_to_response=credits,similar` in the lookup's language adds: title (`title`, else
  `original_title`, else the id), original title, overview (kept from the search if missing),
  poster, backdrop, year, runtime (> 0), rating, cast (first 8 of `credits.cast` with `name`,
  `character`, `profile_path` at `w185`), genres, **similar** (first 20 of `similar.results`, the
  film itself excluded: id, title, original title, year, poster) and the attribution URL; the
  cached row is updated in place and marked "details loaded". Identical calls share one request
  (key `details:<lookup key>`). A failure keeps the search answer.
- META-FR-38 Image URLs are `https://image.tmdb.org/t/p/<size><path>` for a path starting with
  `/`; any artwork URL (TMDB or TVmaze) is kept only if it starts with `https://` (any case) and is
  at most 2,048 characters.
- META-FR-39 Text fields are trimmed, cut to 8,000 characters, and blank means absent.

### 4.8 Ratings

- META-FR-40 A rating is TMDB's `vote_average` formatted with one decimal in the US locale
  ("8.2"), and only when above 0; it comes only from the three **details** responses (movie, tv,
  episode). TVmaze gives none.
- META-FR-41 Where it shows: film page score (metadata rating, else the provider's rating string,
  VOD-FR-61); series page score likewise; guide hero chip `guide_rating` "TMDB %1$s" when the
  selection's metadata carries a rating (GUIDE-FR-62).
- META-FR-42 Beta 23 fact: guide and player lookups are PROGRAMME lookups answered from **search**
  results, which carry no rating, so the guide's rating chip **never appears**. **Rebuild:** read
  `vote_average` from TMDB search results too (same formatting, same > 0 rule), so the guide chip
  works; details still override it.

### 4.9 Consumers of on-demand lookups

| Consumer | Lookup | When | Uses |
|---|---|---|---|
| Film page (VOD-FR-60) | film details | on open, when metadata is enabled; memory cache shown in the first frame | title, overview, facts, backdrop, cast, similar, attribution; repairs a missing library poster (META-FR-71) |
| Series page (VOD-FR-73…) | SERIES | on open | title, overview, poster, backdrop, rating, runtime, cast line, attribution; poster repair |
| Selected episode | EPISODE (series name, series year, s, e) | memory cache at once; otherwise 350 ms after the selection rests, cancelled by the next selection | episode runtime, still for the selected card |
| Guide hero (GUIDE-FR-64) | PROGRAMME (programme title, no year) | cleared at once on a new programme id; lookup 350 ms later, cancelled by any change | year, rating chip, synopsis, still (backdrop else poster), "Source" button |
| Player live info | PROGRAMME | 350 ms after the live programme changes, only while the transport controls are hidden | **nothing**: beta 23's overlay ignores it (wasted request during playback; rebuild: remove) |
| Home hero (HOME §4) | MOVIE (name, year); SERIES and EPISODE for an episode; PROGRAMME for a channel's current programme | after focus rests 180 ms ([Home](02-home.md)) | synopsis and backdrop (orders in HOME spec) |
| Home Trakt hero, Trakt title | by id (META-FR-45) | on focus rest | synopsis and backdrop in the metadata language ([Trakt](51-trakt.md) TRAKT-FR-29) |
| Trakt identity ([Trakt](51-trakt.md)) | MOVIE / SERIES when the library has no TMDB id yet | on scrobble | accepted only when the answer came from TMDB |

- META-FR-43 Every consumer first asks "metadata enabled"; nothing is requested when no provider is
  enabled. Errors are silent: the screen shows its non-metadata fallbacks.
- META-FR-44 The "Source: %1$s" button (`metadata_source`, Info icon; film page, series page,
  guide hero) appears only when metadata is shown and opens its attribution URL: the record's
  page (`https://www.themoviedb.org/movie/<id>`, `/tv/<id>`, the episode page, or the TVmaze
  URL), else the provider's home page.
- META-FR-45 **By id** (Trakt titles): only with TMDB enabled. Key `by-id:<TYPE>:<id>:<language>`;
  identical calls shared; answered from a 64-entry in-memory map (no expiry), else an unexpired
  positive cache row (30 days), else `movie/{id}` (as META-FR-37) or `tv/{id}` (as META-FR-34);
  returns title, overview, poster, backdrop, year. Failure → none.

### 4.10 Genres

- META-FR-50 The vocabulary is the 22 genres of VOD-FR-12 (`VERSION = 2`). A title carries at most
  **one** genre, its primary one: the first TMDB genre id in the response's order that the table
  below recognises.
- META-FR-51 TMDB id → genre. Films: 28 action, 12 adventure, 16 animation, 35 comedy, 80 crime,
  99 documentary, 18 drama, 10751 family, 14 fantasy, 36 history, 27 horror, 10402 music, 9648
  mystery, 10749 romance, 878 science_fiction, 53 thriller, 10752 war, 37 western; 10770 "TV
  Movie" is deliberately ignored. Television: 10759 Action & Adventure → action, 16 animation, 35
  comedy, 80 crime, 99 documentary, 18 drama, 10751 family, 10762 Kids → family, 9648 mystery,
  10763 news, 10764 reality, 10765 Sci-Fi & Fantasy → science_fiction, 10766 soap, 10767 talk,
  10768 War & Politics → war, 37 western. PROGRAMME lookups use both tables. Unknown ids are
  ignored.
- META-FR-52 Genres are stored by wire value (`catalogue_genres`, one row per title) and outlive
  the cache rows they came from. A title matched only by TVmaze, or not matched, has no genre and
  shows under **Unsorted** (VOD-FR-04).
- META-FR-53 Bumping `VERSION` makes the queue revisit every title (META-FR-58) and makes cached
  positive rows of an older version count as missing (META-FR-29).

### 4.11 Background enrichment of the catalogue

**Queue.** A durable table `catalogue_metadata_work` holds one row per active film and series
(§6): content key, type, provider title, year, provider poster URL, target vocabulary version,
state (`pending`, `retry`, `complete`, `no_match`), attempt count, next attempt time, update
stamp.

- META-FR-54 **Candidates**: every film and series of the **active** catalogue snapshot of an
  **enabled** source (visibility rules are not consulted), with its provider title, provider year,
  provider poster and the vocabulary version of its current library override (0 when none).
- META-FR-55 **Synchronisation** (one paged pass): stamp = max(now, newest stamp in the table + 1).
  Candidates are read 2,000 at a time — films, then series — in primary-key order; for each page
  the existing queue rows of those keys are read (in chunks of 900 keys, under SQLite's variable
  limit), each row is reconciled and all are written with the stamp. After the last page, rows
  with an older stamp (titles that left every active catalogue) are deleted. Reconciliation, with
  "same lookup" = same type, title, year and target version as the old row:
  - the title's override is at the current vocabulary version and the old row was `no_match` with
    the same lookup → `no_match`;
  - override at the current version → `complete`;
  - the old row was `retry` with the same lookup → `retry`, keeping attempts and next time;
  - otherwise → `pending` (attempts 0).
  The pass logs one line: titles, pages, pending count, milliseconds.
- META-FR-56 Synchronisation runs when the worker was started with "synchronise" (after leaving
  the app, after a catalogue import) or when the queue is empty or holds a row with another target
  version.
- META-FR-57 **Next batch**: up to 60 rows in state `pending` or `retry` whose next attempt time
  has come, ordered by content key (so `series:` keys before `vod:movie:` keys). Page size is
  clamped to 1…200.
- META-FR-58 Vocabulary: rows carry the target version; a version bump makes every row outdated,
  forcing a synchronisation that re-pends everything whose override is older.
- META-FR-59 Per title the worker runs a catalogue lookup: `(type, provider title, provider year)`
  in the current language, through META-FR-29 but bypassing the memory cache (the durable cache
  can tell stale vocabulary). Outcome:
  - **Matched** — a record was found (automatically or pinned);
  - **No match** — no enabled provider supports the type, or **every** enabled supporting provider
    holds an unexpired negative row for this key (a real miss);
  - **Retry** — anything else (a provider failed, so no negative row was written).
- META-FR-60 **Applying a batch** (one transaction per batch of up to 60):
  - Matched: replacement title = the record's title (trimmed, ≤ 160; blank → skip the title);
    genre rows replaced by the record's primary genre (or none); override written with provider
    poster (trimmed, ≤ 2,048), replacement poster (https only), **replace provider poster = the
    provider has no poster and a replacement exists**, replacement title, external id, current
    vocabulary version, time; queue row → `complete`, attempts 0.
  - No match: genres removed; override written with replacement title = the **provider title**,
    no replacement poster, no external id, current version; queue row → `no_match`.
  - Retry: attempts = min(attempts + 1, 16); next attempt = now + delay; queue row → `retry`;
    library rows untouched. Delay = 15 min × 2^(attempts − 1) with the exponent clamped to 0…7,
    capped at 24 h: 15 min, 30 min, 1 h, 2 h, 4 h, 8 h, 16 h, then 24 h.
  Content keys are trimmed and cut to 512 characters; blank keys are skipped.
- META-FR-61 A `no_match` title is never looked up again automatically unless its provider title
  or year changes, the vocabulary version changes, the language changes or the cache is cleared
  (open question 2).

**Worker.**

- META-FR-62 When it runs (beta 23, WorkManager one-time unique work, network connected):
  - **Leaving the app** (the last started activity stops, not for a configuration change):
    enqueue with policy KEEP, "synchronise", initial delay **30 s**.
  - **Returning** (the first activity starts): the unique work is **cancelled**.
  - **After a catalogue import** (manual refresh or the periodic worker): enqueue with REPLACE,
    "synchronise", delay 30 s if the app is in front, else 0.
  - **Continuation**: enqueue with APPEND_OR_REPLACE, no synchronisation, after 1 s (run budget
    used up) or 15 min (provider trouble).
  - The Lab build never schedules it.
  Because a stream stops when the activity stops (PLAY-FR-20) and picture in picture keeps the
  activity started, the worker never runs while video plays.
- META-FR-63 A run: stop at once if the app is in front or no provider is enabled; synchronise if
  asked (META-FR-56); then loop for at most **4 minutes**: take the next batch (empty → done);
  for each title, stop if the app came to the front, wait **225 ms**, look it up; count
  consecutive Retry outcomes and stop the batch after **3**; apply the batch; if the app is in
  front → end; if 3 consecutive retries → continue after 15 min; after 4 minutes (still in the
  background) → continue after 1 s.
- META-FR-64 **Rebuild** ([Architecture](../plan/03-architecture.md) §4.7–4.8): the worker, the synchronisation and the identity pass
  run on the `bulk` dispatcher (one thread, `THREAD_PRIORITY_BACKGROUND`) and wait on the
  `PauseGate` (no work while `playbackActive`, which includes picture in picture) as well as the
  foreground rule. Unique work name `metadata-enrichment`; the legacy names
  `streammate-catalogue-metadata-enrichment` and `-v2` are cancelled once after the first frame.
  Same batch size, spacing, run budget, continuation and backoff.
- META-FR-65 **Rebuild priority** (addition; owner may decline, open question 3): the next batch is
  ordered by a stored priority — titles with playback progress first, then titles visible under
  the organisation rules, then hidden ones — and by content key within a priority.

### 4.12 Film work keys and the identity pass

- META-FR-66 `catalogueWorkKey(title, year, externalId)` (films only; series are never folded):
  1. external id trimmed and not blank → `tmdb:<externalId>`;
  2. **fast path**: the trimmed title is not empty, contains only letters, digits and whitespace,
     its first token (split on `\s+`) is not a provider prefix token when there is more than one
     token, and no token needs full cleaning → identity = title NFKD, combining marks (`\p{Mn}`)
     removed, lower-cased, trimmed, runs of 2+ spaces collapsed → `name:<identity>:<year or empty>`
     (the given year only);
  3. otherwise: bare = `searchTitle(title)` (else the title); plain = bare NFKD with combining
     marks removed; normalized = `normalizeTitle(plain)`; stripped = normalized with every match of
     `(?<![a-z0-9])(?:(?:19|20)\d{2}|\d{3,4}p|4k|uhd|hdr10\+?|hdr|dolby ?vision|dovi|x26[45]|hevc|h ?26[45]|bluray|bdrip|webrip|web ?dl|hdtv|remux)(?![a-z0-9])`
     replaced by a space, trimmed, `\s{2,}` collapsed; identity = stripped, else normalized, else
     the title trimmed and lower-cased; year = the given year, else `yearFromTitle(title)` →
     `name:<identity>:<year or empty>`.
  - Technical tokens (need full cleaning): `4k uhd fhd hd hdr hdr10 hdr10+ dolby vision dv dovi
    hevc x264 x265 h264 h265 bluray bdrip webrip hdtv remux multi subs subtitles audio`; also a
    token matching `(?:s\d{1,2}e\d{1,3}|k\d{1,2}|j\d{1,3})` whole, a 3–4 digit number followed by
    `p`, or a 4-digit number 1900–2099.
  - Provider prefix tokens: `fi fin en eng sv swe da dan no nor de ger fr fre es spa nc nordic vip
    vod` plus the technical tokens.
- META-FR-67 Behaviour the tests pin down: "FIN | The Matrix (1999) 4K", "The Matrix 1999
  [MULTI-SUBS] 1080p", "NORDIC - The Matrix - HDR10", "The Matrix 1999 [MULTI-SUBS] HDR10 1080p"
  and "[FI] The Matrix" (all year 1999) give **one** key; "FIN The Matrix" equals "The Matrix";
  "The Matrix Reloaded" differs; "The Thing" 1982 ≠ 2011; "The Matrix" + 1999 = "The Matrix
  (1999)" without a year; 1999 ≠ 2000 by name alone; the same external id wins over names and
  years; different external ids stay apart; a blank external id falls back to the name; "Amelie" =
  "AMÉLIE"; "[MULTI-SUBS]" ≠ "[4K]" (a name that is all decoration keeps something of itself).
- META-FR-68 Work keys are used for copy folding (VOD-FR-25), the shared film position and Home's
  one-card-per-film rule (HOME-FR-11), and the film identity aliases
  ([Library organisation](42-library-organization.md) ORG-FR-30…): an import registers the alias
  `work:<key>` (external id unknown yet) for each page of films it writes.
- META-FR-69 **Identity pass** (beta 23): triggered by a change in the active catalogue snapshots
  or in the number of library overrides with an external id (settled for 60 s after the last
  change). It is skipped when a stored mark (`movie_identity_mark` = the snapshot list joined by
  `|`, `#`, the matched count; ≤ 4,096 characters) equals the current state. Otherwise it reads
  the active films 2,000 at a time in `(sourceId, snapshotId, movieId)` order with their external
  id, computes each work key and registers each page's copy groups
  (`["work:<key>"] + content keys`) before reading the next page, then stores the mark. It runs on
  its own single thread at `THREAD_PRIORITY_BACKGROUND`, also while the app is in front and while
  video plays. It logs films, pages and milliseconds.
- META-FR-70 **Rebuild**: the identity pass is incremental (§9.3): work keys are computed and
  stored once at import, and after a metadata batch only the films whose external id changed are
  re-keyed; the whole-catalogue pass runs only when the normaliser version changes. It waits on
  the PauseGate.

### 4.13 Poster repair and the library override

- META-FR-71 When a film or series page gets metadata with an https poster and the title's library
  override has no replacement poster yet, the override is (re)written from that metadata with
  "replace provider poster" = the provider has no poster, or the old override already replaced it.
  Complete overrides are never rewritten on a visit (that would re-emit the wall).
- META-FR-72 Every write of a known identity (worker match, poster repair, pin) replaces the
  title's genre rows with the record's primary genre and marks its queue row `complete` at the
  current vocabulary version.

### 4.14 Fix a match (match picker)

- META-FR-73 Opening (VOD-FR-104): the query starts as `searchTitle(provider name)` (else the raw
  name), the search runs immediately and focus goes to "Search". The picker holds the page's
  original lookup (type, provider name, provider year).
- META-FR-74 Search: the query is trimmed and cut to 160 characters (the field takes 80); blank →
  no results. Providers are asked in order (enabled and supporting the type); the **first
  non-empty** list wins; a provider error counts as empty. Results are the provider's own order,
  **unscored and unfiltered** (a person decides), at most 12: external id, type, display title,
  year, overview, https poster. While running: "Searching…"; empty after a search: "Nothing came
  back. Try a shorter name, or the original one."
- META-FR-75 Choosing a result **pins** it: the record is fetched **by id** (never searched or
  scored again) in the metadata language; stored as a positive cache row for the page's lookup
  key with confidence 1.0 and `pinned = 1`; put in the memory cache; the title's library override
  is rewritten with the record (title, poster, **replace provider poster = yes**, external id,
  genre). The page shows the pinned record; the dialog closes when the write finishes (also when
  it failed, silently).
- META-FR-76 A pinned row answers every later lookup of that key at once, never expires, is never
  swept and is never replaced by a search. Its genres are refreshed from the same record when the
  vocabulary version changes.
- META-FR-77 "Undo my choice" (`match_picker_clear`, Replay icon) appears only while the page's
  lookup key has a pinned row (checked on open and whenever the picker opens or closes). It
  deletes every cache row of that key (all providers), the memory entry and the title's genres;
  the page's metadata is cleared and the dialog closes. The library override (title, poster,
  external id) is **not** cleared and the queue row stays `complete` (bug; rebuild: clear the
  override and re-pend the queue row at top priority).
- META-FR-78 Beta 23 bugs the rebuild fixes:
  - The pin is fetched from **TMDB by id whatever provider produced the result**. With TMDB off
    and TVmaze on, choosing fails silently; with TMDB on but empty, a TVmaze id is fetched as a
    TMDB id (a wrong record). Rebuild: a pin stores `(provider, externalId, type)` and is fetched
    from that provider.
  - The pin lives on the lookup key, which includes the language: after a language change (or
    "Clear metadata cache", or "Save key") the choice is lost and automatic matching runs again.
    Rebuild: pins are their own small table keyed by content key (and applied to every copy with
    the same film identity, open question 4), survive language changes and cache clears, and are
    fetched in the current language.
  - Because the key is shared, a pin made on one copy also answers lookups of every other title
    with the same cleaned name and year (other copies, even other sources) — but only when they
    are looked up again.

### 4.15 Language change, cache clear, key save

- META-FR-79 **Language change** (META-FR-09): the memory caches are emptied, every library
  override and every queue row is deleted (genre rows stay: they do not depend on language), and
  the worker is restarted with REPLACE. Walls show provider titles and posters until the worker
  (in the background) redoes them; cache rows of the old language stay until they expire.
- META-FR-80 **Clear metadata cache** and **Save key**: memory caches emptied; every cache row
  (pinned ones included), every genre row, every override and every queue row deleted in one
  transaction. The worker is not restarted; the next time the app goes to the background the
  empty queue forces a full synchronisation.

### 4.16 Errors

- META-FR-81 HTTP: a non-2xx answer → `error_metadata_http` "The %1$s metadata service responded
  with %2$d" (provider name, code); a body over 2 MiB → `error_metadata_response_too_large` "The
  %1$s metadata response is too large"; network failures pass through as transport errors. Only
  "Test TMDB" and the Settings saves show errors; every other consumer treats a failure as "no
  metadata" (and the worker as Retry).
- META-FR-82 TVmaze answering **429** on the first attempt: wait `Retry-After` seconds (clamped to
  1…5; 2 when absent or unreadable) and try once more; a second 429 is an error. TMDB 429 is an
  error at once (the worker backs off, META-FR-60).

## 5. Screen anatomy

### 5.1 Settings › Library, metadata parts

[design/screens/settings.md](../design/screens/settings.md) §3–§6 (group vocabulary, rows,
switch, picker). Order inside the pane:

1. **Group 1** (no hairline above the first row): a Row (space between) with the group heading
   `metadata_title` ("METADATA AND IMAGES (OPTIONAL)", uppercase overline Bold `textDim`) and the
   TMDB logo asset `tmdb_attribution.svg` at **137 × 18 dp** (content description "TMDB"); the
   TMDB switch row (Star icon, subtitle, no divider, **first focus**); a Row padded 14 dp
   horizontally, `spacedBy 10`, centred: credential field (compact, weight 1, Key icon, masked),
   "Save key" (compact, Save icon), "Test TMDB" (compact); the TVmaze switch row (Guide icon); the
   status text (12 sp, `focus`, padding horizontal 14).
2. **Group 2**: metadata language value row (Info icon); then the preferred-copy row
   ([Movies](40-movies-and-series.md) VOD-FR-32) and "Manage groups & content"
   ([Library organisation](42-library-organization.md)).
3. **Group 3** "Groups of your own" ([Library organisation](42-library-organization.md)).
4. **Group 4** image cache (VOD-FR-108/109).
5. **Group 5** heading "MAINTENANCE"; `Row(spacedBy 10)` of compact buttons "Clear metadata cache",
   "TMDB", "TVmaze".

Test tags (keep for tests): `settings-metadata-tmdb-enabled`, `settings-metadata-tmdb-token`,
`settings-metadata-save`, `settings-metadata-test-tmdb`, `settings-metadata-tvmaze-enabled`,
`settings-metadata-status`, `settings-metadata-language`, `settings-metadata-language-<tag>`,
`settings-metadata-clear-cache`.

### 5.2 Match picker

[design/screens/movies-and-series.md](../design/screens/movies-and-series.md) §5. Additional
detail: the panel is a focus group; title `match_picker_title` (title style 28/32 Bold
`textPrimary`); help (label, `textDim`); search row `spacedBy 10`: field (compact, Search icon,
weight 1) + "Search" (compact, Search icon); the result area (weight 1): note text centred (body,
`textDim`) or `LazyColumn(spacedBy 6)` keyed by external id; result row = TvSurface shape medium,
padding 10, focus scale 1.04; thumbnail box 44 × 62 dp, clip small, `surfaceRaised` under the
image (Crop); text column padding start 14: title bodyLarge Bold (1 line, ellipsis) with the year
10 dp to its right (label, content α0.7), overview caption α0.7 (2 lines, top 3 dp); footer
`Row(spacedBy 10)`: "Undo my choice" (Replay icon, only when pinned), "Close" (Close icon). Test
tags `match-picker`, `match-picker-query`, `match-picker-search`, `match-picker-results`,
`match-result-<id>`, `match-picker-note`, `match-picker-clear`, `match-picker-close`.
**Rebuild:** thumbnails decoded at 88 × 124 px from TMDB `w92` (beta 23 decodes `w500` posters
for a 44 dp box).

### 5.3 Attribution button

`TvActionButton` compact with the Info icon and "Source: %1$s" (film and series pages: in the
action row; guide hero: last button of the hero row, test tag `guide-metadata-attribution`).

## 6. Data

| Store | Key | Content | Lifetime | Per profile | Backup |
|---|---|---|---|---|---|
| `metadata_cache` (Room) | (lookupKey, provider) | status `positive`/`negative`, externalId, type, matched title, display title, overview, poster URL, backdrop URL, year, season, episode, runtime, rating (text), `castJson` `[{name, character?, profileUrl?}]`, `genresJson` `["drama"]`, genresVersion (default 0), pinned (0/1), detailsLoaded, `similarMoviesJson` `[{externalId, title, alternativeTitles[], year?, posterUrl?}]`, attribution name and URL, confidence, cachedAt, expiresAt; indexes `expiresAtEpochMillis`, `externalId` | META-FR-31 | no | no |
| `catalogue_metadata_overrides` | contentKey | provider poster URL, replacement poster URL, replace-provider-poster flag, replacement title, externalId (indexed), genresVersion, updatedAt | until a language change, cache clear or key save | no | no |
| `catalogue_genres` | (contentKey, genre) | the primary genre; index `genre` | same, but kept on a language change | no | no |
| `catalogue_metadata_work` | contentKey | type, title, year, provider poster, target version, state, attempts, next attempt, updatedAt; index `(state, nextAttemptAtEpochMillis, contentKey)` | swept by synchronisation | no | no |
| Encrypted settings (SharedPreferences) | `metadata_tmdb_enabled_v1` (bool, false), `metadata_tmdb_token_v1` (Keystore-encrypted string), `metadata_tvmaze_enabled_v1` (bool, false) | | until changed | no | **no** (open question 5) |
| DataStore | `metadata_language` (tag) | META-FR-10 | – | no | yes (`metadataLanguage`) |
| DataStore | `movie_identity_mark` (≤ 4,096 chars) | META-FR-69 | – | no | no |
| Memory | lookup LRU 256; by-id map 64; in-flight maps | | process | – | – |

- Content keys: `vod:movie:<sourceId>:<movieId>`, `series:<sourceId>:<seriesId>` (VOD-FR-23).
- Genre rows and overrides are read by the walls through narrow queries (films: keys `GLOB
  'vod:movie:*'`; series `GLOB 'series:*'`).
- Nothing here is per profile: matching, titles and genres are device-wide.
- **Rebuild data changes** (for [Data model](../plan/04-data-model.md)): store TMDB **image
  paths** (`/abc.jpg`) and the provider, not sized URLs, so each screen asks for its own size
  (§9.5); a `metadata_pins` table `(contentKey PK, provider, externalId, type, pinnedAt)`
  (META-FR-78); a `priority` column on the queue with index `(state, priority,
  nextAttemptAtEpochMillis, contentKey)` (META-FR-65); `metadata_cache` capped (§9.6).

## 7. External interfaces

### 7.1 TMDB (`https://api.themoviedb.org/3`)

| Purpose | Request | Fields used |
|---|---|---|
| Credential check | `GET /configuration` | success only |
| Film search | `GET /search/movie?query=&language=&include_adult=false&page=1` | `results[]`: `id`, `title`, `original_title`, `release_date`, `overview`, `poster_path`, `backdrop_path`, `genre_ids`, `popularity` (+ `vote_average` in the rebuild) |
| Series search | `GET /search/tv?…` (same parameters) | `id`, `name`, `original_name`, `first_air_date`, the rest as above |
| Programme search | `GET /search/multi?…` | as above plus `media_type` (`movie`/`tv`; others skipped) |
| Film details | `GET /movie/{id}?language=&append_to_response=credits,similar` | `id`, `title`, `original_title`, `release_date`, `overview`, `poster_path`, `backdrop_path`, `runtime`, `vote_average`, `genres[].id`, `credits.cast[]{name, character, profile_path}`, `similar.results[]{id, title, original_title, release_date, poster_path}` |
| Series details | `GET /tv/{id}?language=&append_to_response=credits` | `name`, `overview`, `poster_path`, `backdrop_path`, `episode_run_time[]`, `vote_average`, `genres[].id`, `credits.cast[]` |
| Episode | `GET /tv/{id}/season/{s}/episode/{e}?language=` | `name`, `overview`, `still_path`, `season_number`, `episode_number`, `runtime`, `vote_average` |

- Images: `https://image.tmdb.org/t/p/<size><path>`. Beta 23 sizes: posters and similar posters
  `w500`, backdrops and episode stills `w780`, cast photos `w185`. Rebuild sizes: §9.5.
- Auth: META-FR-14. A v3 key travels in the URL, so request URLs must never be logged or put in
  diagnostics ([Security](73-security-privacy.md) redaction).
- Attribution pages: `https://www.themoviedb.org/movie/<id>`, `/tv/<id>`,
  `/tv/<id>/season/<s>/episode/<e>`; home `https://www.themoviedb.org`.

### 7.2 TVmaze (`https://api.tvmaze.com`, no key)

| Purpose | Request | Fields used |
|---|---|---|
| Show search | `GET /search/shows?q=<title>` | `[]{ show{ id, name, summary, premiered, image{medium, original}, url } }` |
| Episode | `GET /shows/{id}/episodebynumber?season=&number=` | `id`, `name`, `summary`, `image.original`, `season`, `number`, `url` |

Home page `https://www.tvmaze.com`; data under CC BY-SA (legal screen).

### 7.3 Transport rules

- Query strings: the title cut to 160 characters. Responses: at most 2 MiB read (the body is
  buffered up to 2 MiB + 1 byte and refused past it); JSON parsed off the main thread, unknown
  keys ignored.
- Beta 23 uses the app's shared OkHttp client (connect 20 s, read 90 s, redirects followed,
  OkHttp's default 5 requests per host). Rebuild: §9.4.
- 429 handling: META-FR-82. No other automatic HTTP retry; the worker's backoff is META-FR-60.
- What is sent (privacy, [Security](73-security-privacy.md)): the cleaned title, year, type,
  season/episode numbers, language and the credential, to the provider's host; plus the device's
  IP address. Nothing else, and nothing to the Sohva TV developer.

## 8. Edge cases and limits

- **No provider enabled**: nothing runs; walls show provider titles and posters; the Genres view
  has only Unsorted.
- **Invalid or revoked TMDB key**: every TMDB call answers 401 → Retry in the worker (backoff up
  to 24 h per title; the batch stops after 3 in a row and continues after 15 min), no metadata on
  screens. Only "Test TMDB" says why. Rebuild: after 3 consecutive 401/403 answers the worker
  stops until the key is saved again, and the Library status line shows
  `error_metadata_http` for TMDB (open question 8).
- **Offline**: WorkManager waits for a network; on-demand lookups fail silently.
- **TVmaze only**: series and programmes get titles, posters, plots, but no genre, rating or
  cast; films are not looked up at all.
- **Huge catalogue**: 257,000 titles at ≤ 4 lookups per second is at least 18 hours of background
  time; nothing is enriched while the TV app is in front.
- **Same name, different films**: "The Thing" 1982 and 2011 differ by year; two films with the
  same name and no year are one key and can share a pin (META-FR-78).
- **Provider year wrong by one**: still matches (0.45 year score); by two or more: matches only if
  the title is exact and unambiguous.
- **Language not offered by TMDB for a record**: TMDB returns its fallback text (often an empty
  overview); the app shows the provider's plot then.
- **Process death mid-run**: the queue is durable; a batch not yet applied is simply looked up
  again (answers come from the cache).
- **Clock changes**: expiry and retry times use wall-clock milliseconds; a clock set back delays
  retries, set forward expires entries early. Harmless.
- **Restore of a backup with another metadata language**: the preference changes but library
  titles stay in the old language (no reset runs). Rebuild: run META-FR-79 when a restore changes
  the language.
- **Accents**: `normalizeTitle` splits accented letters ("ame lie"); lookups of "Amélie" and
  "Amelie" therefore have different keys; work keys do not (META-FR-66).
- **Titles ending in tag letters** (META-FR-20 Step G): "…hd" loses "hd" before searching.
- **Device regex engine**: Android's regex engine is ICU, not the JVM's; `\s`, `\d`, `\b` and
  `(?i)` can differ at the edges, which is why the rebuild's hand-written cleaner is checked on
  the device too (§11).

## 9. Lightweight by design

### 9.1 A hand-written title cleaner (no regex at run time)

The identity pass ran about twenty regex passes per title through the ICU matcher (3.1 s for
30,000 films on the emulator, a minute or more on the low-end box) and had to be moved to a
background-priority thread; the film wall ran the same regexes per layout pass (13 % of the main
thread) until keys were kept per wall (ledger 23 Sept 2026, commit `48a37a3`). The rebuild:

- Implements `searchTitle`, `normalizeTitle`, `yearFromTitle` and `catalogueWorkKey` as **one
  forward scanner per step over a `CharArray`** with a single reusable `StringBuilder`; token sets
  are `HashSet<String>` of ASCII-lower-cased tokens (or a small trie); character tests are
  explicit (`isSpace` = the six `\s` characters; digits `0`–`9`; delimiter sets per step;
  `Character.isLetterOrDigit` for word characters). No `Regex`, `Pattern`, `split(Regex)` or
  `String.format` in these paths.
- Steps C and G are implemented as "longest valid prefix / longest matching suffix" searches over
  a small token grammar (4.4); Step A as a single left-to-right bracket scan; Step F as a
  backwards check of the last characters.
- Uses `java.text.Normalizer` (NFKD) only when the string contains a non-ASCII character;
  ASCII-only titles (most provider rows) skip it.
- Keeps the **fast path** of META-FR-66 (plain words straight to a key) and extends it: a title
  of ASCII letters, digits and single spaces with no token from the prefix/technical sets needs
  one pass and one lower-casing.
- Carries a `NORMALISER_VERSION`; stored work keys and lookup keys are tagged with it.
- Budget (starting value; plan/07 refines): ≤ 5 µs per typical title on the Shield and ≤ 20 µs on
  the low-end box, ≥ 10× faster than beta 23 measured by a JVM micro-benchmark over the test
  corpus, no allocation beyond the result string for ASCII titles.
- Equality with beta 23 is **proved, not assumed**: the regex versions (4.4) live only in test
  code as the reference; property tests compare both on the fixtures, on a grammar-generated
  corpus (random sequences of tokens, delimiters, brackets, years, accents, spaces) and on an
  anonymised title list from an owner-scale catalogue; the same test also runs as an
  instrumentation test on the device's ICU engine. Stored keys from beta 23 (the
  `playback_progress.workKey` column, `work:` aliases) stay valid only if the outputs are equal;
  any accepted difference bumps `NORMALISER_VERSION` and the upgrade recomputes stored keys once,
  paged (the progress table is small; aliases through the identity pass).

### 9.2 Paging the catalogue in key order

Two background jobs that read the catalogue whole (the identity pass and the queue rebuild)
killed the process about four minutes after every start on the Shield's 192 MB heap with a
200,000-film catalogue (7 Sept 2026, commit `d8c8473`; [Lessons](../plan/08-lessons-learned.md)
1.1). Rules:

- Every whole-catalogue read pages **2,000 rows** in primary-key order `(sourceId, snapshotId,
  itemId)` with **keyset** continuation (`WHERE (sourceId, snapshotId, itemId) > (?, ?, ?) ORDER
  BY sourceId, snapshotId, itemId LIMIT 2000`), never `OFFSET` (beta 23 uses `LIMIT/OFFSET`,
  which re-walks every skipped row: about 10 million row visits for 200,000 films).
- Each page is processed and committed in its own short transaction before the next is read;
  nothing catalogue-sized is held (no `groupBy`/`associateBy` over the catalogue, no list of all
  keys).
- Tables rebuilt from the catalogue use stamp-and-sweep (META-FR-55), never clear-and-replace.
- Each pass logs one line (rows, pages, ms) and is verified by sampling the Java heap during a
  full pass on the owner-scale catalogue ([Lessons](../plan/08-lessons-learned.md) 1.1).

### 9.3 Doing less work

- **Incremental identity**: work keys are computed at import as rows are written (spec 40 §9.3
  stores them); after a metadata batch only films whose external id changed get new keys and
  aliases; the whole-catalogue identity pass exists only for a normaliser version bump.
- **Queue kept at import**: the catalogue import writes or updates the queue rows of the pages it
  writes (same stamp-and-sweep), so the post-import synchronisation does not re-read the whole
  catalogue.
- **"Metadata enabled" is observed**, not read per call: beta 23 reads and Keystore-decrypts the
  TMDB token on every guide selection, details open and episode selection, on the calling thread
  (VOD lesson 12). The rebuild decrypts once per change, off the main thread, and exposes a
  `StateFlow<Boolean>`.
- **No lookup that nobody sees**: the player's live-programme lookup is removed (4.9).
- **Settings genre list**: `SELECT DISTINCT genre FROM catalogue_genres` (index `genre`), not an
  observation of every genre row of the library (beta 23 maps the whole table into a
  `Map<contentKey, Set<genre>>` to offer genres in the custom-group editor).
- **Cache sweeps** once per worker run and in the daily maintenance, not a DELETE per lookup.

### 9.4 Network and threads

- On-demand lookups: debounced (350 ms for guide and episode selections, 180 ms Home focus rest),
  cancelled on change (the HTTP call too), shared when identical; parsing on the `ui` dispatcher,
  database on `dbRead`/`dbWrite`; nothing on the main thread except reading the memory cache.
- A dedicated metadata `OkHttpClient` (sharing the app's connection pool) with dispatcher limits
  `maxRequests = 4`, `maxRequestsPerHost = 2`, connect timeout 10 s, read timeout 20 s (beta 23:
  shared client, 5 per host, 90 s read). Image downloads are the image loader's (2 decodes at a
  time, VOD-FR-110).
- Worker: strictly sequential, one lookup (1–2 requests) at a time, 225 ms apart: at most about 4
  lookups per second, well under the providers' limits; on the `bulk` dispatcher at background
  priority; **no work while video plays or the app is in front** (PauseGate + foreground rule).
- Batched writes: one transaction per batch of up to 60 titles, so a visible wall is invalidated
  at most once per batch; the rebuild additionally bumps the metadata version counter the walls
  observe at most once every 5 s (spec 40 §9.3).

### 9.5 Smallest image sizes

Store TMDB paths; build the URL per use with the smallest size that covers the drawn pixels at
1080p (the density at 960 dp is 2.0):

| Use | Drawn size | TMDB size (beta 23) |
|---|---|---|
| Wall poster (replacement) | ≈ 177 × 266 px, ≤ 192 × 288 | `w185` at 720p boxes, else `w342` (`w500`) |
| Similar card | 208 × 312 px | `w342` (`w500`) |
| Match-picker thumbnail | 88 × 124 px | `w92` (`w500`) |
| Cast photo | 104 × 104 px | `w185` (`w185`) |
| Episode still | 416 × 234 px | `w300` (`w780`) |
| Details / Home backdrop, guide hero still | full screen or hero | `w780` (`w780`), decoded RGB_565 at ≤ screen size |

TVmaze offers only `medium` (≈ 210 × 295) and `original`: posters use `medium`, backdrops
`original` decoded at the drawn size. All decodes RGB_565 at drawn size, no cross-fade
([Movies](40-movies-and-series.md) §9.5).

### 9.6 Memory and storage bounds

| Held | Bound |
|---|---|
| Lookup memory cache | 256 entries (each ≤ 8 cast + 20 similar) — about 1–2 MB |
| By-id map | 64 small entries |
| Worker | one batch of 60 rows + the lookup in flight |
| Synchronisation / identity pass | one page of 2,000 narrow rows (+ ≤ 900-key chunks) |
| Match picker | ≤ 12 results |

Storage: `metadata_cache` holds a row per looked-up title and language, about 1 KB each with an
overview (estimate, not measured), so a 257,000-title library approaches 250 MB. Rebuild: cap it
at 50,000 unpinned rows; the daily maintenance deletes expired rows, then the oldest `cachedAt`
beyond the cap; background matches need only the override row, so their cache rows may be the
first to go.

### 9.7 Start-up

Nothing of this feature runs before the first frame: no settings decrypt, no WorkManager call
(initialised on demand), no cache sweep. The metadata-enabled flow is read after the first frame.
The worker starts only when the app leaves the foreground.

### 9.8 Where the current app was slow or ran out of memory

1. Whole-catalogue identity pass and queue rebuild → OOM every ~4 min (fixed by paging, 9.2).
2. Identity pass ~20 ICU regex passes per film, at normal priority on the default pool, competing
   with the UI (fixed by a background thread; the rebuild removes the regexes, 9.1).
3. Work keys recomputed per layout pass on the film wall (fixed per wall; rebuild stores them).
4. Keystore decrypt on the main thread per selection (9.3).
5. A cache DELETE per lookup; full cache wipe on every "Save key" (META-FR-05).
6. OFFSET paging over 200,000 rows (9.2).
7. Settings observing every genre row to list genres (9.3).
8. The player's unused programme lookup during playback (4.9).
9. Match-picker thumbnails decoded from `w500` (5.2).

## 10. Lessons from the current app

1. **Page every whole-catalogue job** (commit `d8c8473`,
   [Lessons](../plan/08-lessons-learned.md) 1.1): 200,000 films killed the process until both jobs
   paged 2,000 rows in key order with stamp-and-sweep; verify with heap sampling on the Shield.
2. **Regex normalisation is expensive on slow cores** (commits `48a37a3`; ledger 23 Sept 2026
   items 3–4): the ledger left "exact hand-written normalisation" as a follow-up and warned that
   stored keys need an equivalence test on the device's ICU engine — hence §9.1.
3. **Distinguish a miss from a failure** (`MetadataRepository.enrichCatalogue`): an outage must
   not mark thousands of titles "no match"; only a fresh negative row from every eligible
   provider is a miss.
4. **A durable queue, not a scan per batch** (`catalogue_metadata_work`): reading every film to
   find the next sixty was quadratic on a large library.
5. **One genre per title** (`MetadataDao.replaceGenres`): rail counts stay honest and a title sits
   in one genre row; an existing test (`CatalogueCardDaoTest`) that expected two genres
   contradicts this policy (GROUP_CONTENT_MANAGEMENT_IMPLEMENTATION, "Test-suite caveats").
6. **Fold TMDB's two genre lists** (`TmdbGenres`): film and TV lists overlap without matching;
   Kids is Family; TV Movie is not a genre.
7. **Share and cancel requests** (commit `6da5f32`): obsolete guide lookups kept running and
   duplicated; the last reader leaving now cancels the HTTP call.
8. **Choose the language, key the cache by it** (commit `0adeba4`): the lookup key carries the
   language, so a change needs no cache wipe — but it does lose pins (META-FR-78).
9. **Conservative matching plus a human picker** (`MetadataMatcher`, `CatalogueMatchPicker`):
   keep the thresholds; do not "improve" recall at the cost of wrong posters.
10. **Beta 23 bugs to fix**: pin fetched from TMDB for TVmaze results; pins lost on language
    change, cache clear or key save; "Undo my choice" leaves the chosen title and poster on the
    wall; the guide rating chip never shows (search results carry no rating); the player looks up
    programme metadata it never shows; "Save key" wipes the whole library enrichment; the
    Settings status shows errors in the success colour; a no-match title is never retried;
    a restored language does not re-enrich; the `fi-FI` hard default in the repository.
11. **Worker only in the background** keeps browsing smooth ([Architecture](../plan/03-architecture.md)
    §2.5), and incidentally avoids the suspected trigger of "Left does not reach the rail"
    (background writes while browsing, [Lessons](../plan/08-lessons-learned.md) 2.4); keep it, and
    batch writes anyway.
12. **TVmaze CC BY-SA**: every screen that shows TVmaze data must say so; the Home hero and wall
    titles from TVmaze carry no attribution in beta 23 (open question 9).

Open questions (for the owner)

1. **Rating chip in the guide**: rebuild reads `vote_average` from search results so "TMDB 7.4"
   appears in the guide hero (beta 23 never shows it). Confirm.
2. **Retrying "no match"**: re-queue `no_match` titles after 30 days (TMDB adds records daily)?
3. **Worker priority** (META-FR-65): watched titles first, hidden titles last. Accept?
4. **Scope of a pin**: this copy only, every copy of the same film identity, or (as beta 23, by
   accident) every title with the same cleaned name and year?
5. **TMDB credential in backups**: today neither the key nor the switches are backed up, so a
   restore needs the key typed again. Include them in the encrypted backup?
6. **Confirmation for "Clear metadata cache"**, which also loses pins (rebuild keeps pins)?
7. **Trailing-tag letters**: keep Step G's missing word boundary ("…hd" stripped) for key
   equality, or fix it and bump `NORMALISER_VERSION`?
8. **Bad key feedback**: stop the worker and show a Library status after repeated 401/403?
9. **TVmaze attribution** on Home hero and walls: add a small "TVmaze" credit, or drop TVmaze
   titles there?
10. **Foreground enrichment of what is on screen**: beta 23 never enriches while the app is in
    front, so a fresh library shows provider titles for the whole first session. Allow a small
    foreground budget (e.g. the visible wall page, one lookup per second, paused while a key is
    held)?

## 11. Acceptance tests

Unit (JVM)
- Title cleaning: every row of META-FR-23; `[REC]` fallback; nested brackets; "4K HD" keeps "HD";
  "FIN The Matrix" fast-path fallback; `K2 J6` removal; year only at the end.
- **Equivalence** (§9.1): the hand-written cleaner equals the reference regexes (test-only code)
  on the fixtures, on ≥ 100,000 grammar-generated strings with a fixed seed, and on the anonymised
  owner-scale title list; the same suite runs as an instrumentation test on the device.
- Work keys: every case of META-FR-67 (mirror `CatalogueWorkKeyTest`).
- Matcher: the cases of META-FR-28 (mirror `MetadataMatcherTest`), the ε boundary, the popularity
  gap (≥ 10) and ratio (≥ 1.5) rules, alternative titles.
- Genres: first recognised id wins; combined TV ids; Kids → Family; 10770 ignored; unknown ids
  ignored; a war series and a war film land in the same genre (mirror `TmdbGenresTest`).
- Queue reconciliation: settled → complete; unchanged retry kept with attempts and time; changed
  title → pending; obsolete swept; no_match stays distinguishable; retry delays 15 min, 30 min,
  …, 24 h cap (mirror `CatalogueMetadataWorkQueueTest`).
- Enrichment result: provider failure → Retry, fresh negatives from every eligible provider → No
  match, no eligible provider → No match.
- Providers against a mock server (mirror `MetadataProviderTest`): Bearer vs `api_key` (32 hex);
  `/3/configuration` for the check; movie details with `credits,similar` and language; series
  details with `credits`; TVmaze HTML cleaning and User-Agent; 2 MiB cap; TVmaze 429 retried once
  with Retry-After clamped; TMDB 429 not retried; https-only images.
- Request sharing and cancellation: two identical readers make one call; the last reader leaving
  cancels the HTTP call (mirror `MetadataCancellationTest`).
- Pins (rebuild): a pin survives a language change, a cache clear and a key save; it is fetched
  from its own provider; undo clears the override and re-pends the title.
- Rating: formatting "%.1f" US locale, > 0 only; search results carry it in the rebuild.

Instrumentation / database
- Paged synchronisation covers every title across pages and sweeps the ones that left (mirror
  `MetadataQueuePagingTest`), with a page size of 3 to force several pages.
- Worker: does nothing in the foreground or while `playbackActive`; stops after 3 consecutive
  retries and schedules +15 min; continues after 4 minutes; applies one transaction per batch.
- Match picker (mirror `CatalogueMatchPickerTest`): the query starts from the cleaned provider
  name and runs on open; choosing settles and closes; Undo only when pinned; undo returns the
  title to matching; an empty search says so; focus returns to "Wrong details?".
- Settings: TMDB switch refuses without a key; Save/Test statuses; language picker opens on the
  current value and a change resets and restarts enrichment.

Manual device checks
- With a real TMDB key: a fresh 1,000-title library leaves the app for 5 minutes → titles,
  posters and genres appear; returning cancels the worker at once (no network in a capture while
  browsing).
- Change the metadata language → titles come back in the new language after a background run;
  pinned choices survive (rebuild).

Performance (low-end box or its emulator stand-in)
- Identity pass over a synthetic 200,000-film catalogue: Java heap stays under the plan/07 budget
  (sample every 30 s), completes at background priority, and the guide's D-pad frame times during
  the pass stay within budget (Perfetto).
- Cleaner micro-benchmark: ≥ 10× beta 23's regex pipeline on the corpus.

## 12. Reference: current code map

- `iptv/.../iptv/metadata/MetadataRepository.kt` — lookups, caches, pins, queue sync and apply,
  language reset, by-id details.
- `iptv/.../iptv/metadata/MetadataMatcher.kt` — cleaning regexes, scoring, thresholds.
- `iptv/.../iptv/metadata/CatalogueWorkKey.kt` — film work key and its fast path.
- `iptv/.../iptv/metadata/TmdbMetadataProvider.kt` — TMDB search, details, auth, image URLs.
- `iptv/.../iptv/metadata/TvmazeMetadataProvider.kt` — TVmaze search, episode, HTML cleaning.
- `iptv/.../iptv/metadata/TmdbGenres.kt` — genre id folding.
- `iptv/.../iptv/metadata/MetadataHttp.kt` — HTTP call, 429 retry, 2 MiB cap, JSON helpers.
- `iptv/.../iptv/metadata/MetadataRequests.kt` — shared, cancellable in-flight requests.
- `iptv/.../iptv/metadata/MetadataModels.kt` — lookup, candidate, result and queue models.
- `core/.../database/MetadataDao.kt`, `GuideEntities.kt` (entities) — cache, overrides, genres,
  queue.
- `core/.../model/CatalogueGenre.kt` — the vocabulary and `VERSION`.
- `core/.../app/MetadataLanguages.kt` — the 22 tags and the default rule.
- `core/.../security/SecretSettingsStore.kt` — encrypted TMDB/TVmaze settings.
- `app/.../app/CatalogueMetadataScheduler.kt` — the worker and its scheduling.
- `app/.../app/StreamMateForegroundState.kt` — foreground tracking that starts/cancels it.
- `iptv/.../repository/OrganizationRepository.kt` — identity pass (`movieIdentityUpdates`).
- `iptv/.../feature/catalogue/CatalogueMatchPicker.kt` — the picker dialog.
- `iptv/.../feature/settings/SettingsScreen.kt` (section `METADATA`) — the Library settings.
- Consumers: `MovieDetailsScreen.kt`, `SeriesDetailsScreen.kt`, `GuideScreen.kt`/`GuideHero.kt`,
  `PlayerScreen.kt`, `app/.../home/HomeScreen.kt`, `app/.../trakt/TraktVodIdentity.kt`.
- Tests: `MetadataMatcherTest`, `CatalogueWorkKeyTest`, `TmdbGenresTest`, `MetadataProviderTest`,
  `CatalogueMetadataWorkQueueTest`, `MetadataCancellationTest`, `MetadataQueuePagingTest`,
  `CatalogueMatchPickerTest`, `MetadataLanguagesTest`.
