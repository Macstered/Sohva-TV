# Sports team dictionary research

Build-machine tooling for collecting and reviewing API-Sports identities. Python 3.10+ and its
standard library are sufficient. This does not modify Sohva's current matching rules or APK.

## Collect all sports

The collector supports all twelve API-Sports products. To open the full collector:

```powershell
python tools/sports_dictionary.py collect --key-window --retry-errors
```

This resumes saved data and adds discovery for the other products. The updated window loads
the latest collector code in a fresh process on every Resume, using its keys still in memory. Keep one
collection batch active at a time; the dataset lock rejects concurrent collectors and report
writes. Closing an active window stops its worker after the current request.

| API product | Discovery in full mode |
|---|---|
| Football | `/teams/countries`, then `/teams?country=...` |
| Hockey, basketball, baseball, handball, rugby, volleyball | `/countries`, then `/teams?country=...`, using exact returned names |
| AFL | Complete `/teams` directory |
| American football (NFL/NCAA) | `/leagues`, then `/teams?league=...&season=...` for every advertised accessible year |
| NBA | API v2 `/teams` directory; its IDs stay separate from API-Basketball |
| MMA | `/teams` for gyms; `/categories` and category/gym-filtered `/fighters` for fighter identities |
| Formula 1 | `/teams`, `/competitions`, `/circuits`; `/seasons` and `/rankings/drivers`, then `/drivers?id=...` |

Country-wide collection avoids thousands of repeated league/season requests. Existing historical
hockey requests are retained; country directories and country rosters run first. Season
restrictions supplied by the provider are honoured for NFL/NCAA and Formula 1 as well as hockey.
Never assume that historical access establishes complete current coverage. An enabled adapter
and a successful directory request are reported separately from complete, reviewed coverage.

MMA gyms, fighters, Formula 1 teams, drivers, competitions and circuits have distinct identity
keys. MMA category/gym enumeration may miss unaffiliated, uncategorised fighters. Formula 1
ranking enumeration may miss unranked drivers; those coverage gaps remain explicit.

TheSportsDB enrichment now covers all ten team products. It rotates selected sports so football
cannot starve the smaller queues. Basketball leagues feed API-Basketball; only the NBA league
also feeds the NBA namespace. Only the Formula 1 league feeds Formula 1. Fighter/player
enrichment from TheSportsDB remains pending. An explicit empty/null league list is a coverage
warning. Its v2 singleton `{"Message":"No data found"}` response also means an empty league
list, and does not stop collection. A missing team profile remains blocked for coverage review;
it is never counted as a successful detail lookup. A missing or incorrectly typed league list is parked as an unverified coverage gap;
its warning records only recognised field names and types. Three consecutive suspect responses
stop the batch in case the service changed. Ordinary Resume skips those parked requests and can
continue with other leagues. HTTP and explicit service errors remain failures.
At batch start, saved checksums matching a fixed set of exact encodings of the confirmed no-data
response recover earlier deferred league lists without more API calls. Unknown checksums are not
reclassified. Capitalised `Message` diagnostics are recognised, redacted and distinguished from
authentication/rate failures. The provider confirms this envelope in its
[v2 no-result discussion](https://www.thesportsdb.com/forum_topic.php?s=0&t=6253).

API-Sports roster rows with a valid ID but a null/blank name are retained in `record_gaps.csv`,
while usable teams from that response are imported. One shared ID lookup is queued for each
unnamed identity. An ID lookup that still lacks a name is parked; no placeholder name is invented.
Invalid IDs, unexpected name types, redacted names, paging/count mismatches and wrong-ID lookups
still reject the request atomically. Existing verified names are never erased by a blank row.
Research schema 5 preserves teams, typed identities, gap tracking, jobs and quotas and adds
sourced country-code mappings for the translation pass.
Use the window's Recheck checkbox or `--recheck-record-gaps` for one deliberate retry of deferred
leagues, unavailable profiles and unnamed ID lookups. This does not requeue inaccessible seasons.

## Start the pilot

From the repository root:

```powershell
python tools/sports_dictionary.py pilot --key-window --sports football hockey
```

Enter the direct API-Sports key and TheSportsDB Premium key in the masked local fields. The keys
stay in process memory, are sent only in HTTPS headers, and are never put in command arguments,
URLs, files, reports or logs. Close the window to stop; completed imports remain checkpointed.
Within the updated window, Resume reuses the keys still held in memory and starts a fresh hidden
collector process, loading the latest source code. The keys reach that process only through an
anonymous local pipe, never process arguments or environment variables. Closing the window
signals cancellation through the pipe, allowing the current request to finish. Change keys clears that
session and enables the masked fields again; closing the window discards the session entirely.
Windows opened before this update must be replaced once and the masked keys entered again.
Without `--key-window`, an interactive terminal asks for masked keys and refuses an echoing
fallback. RapidAPI keys are not supported.

The pilot selects football teams attached to Finland, Germany, England and World. Hockey uses
the newest two advertised non-future seasons of Liiga, NHL and World Championship. If API-Sports
rejects a season and explicitly reports an allowed year range, inaccessible seasonal jobs stay
`blocked` as coverage gaps. The collector refreshes the league index and queues the newest two
advertised seasons within that range. It does not assume a free plan's years in advance. TheSportsDB
uses corresponding featured leagues where present, followed by individual detail lookups.
Its v2 league list lacks alternate names and external provider IDs; details are necessary.
The entire pilot may need several runs because individual lookup jobs are capped.
When the selected pilot queue finishes, the updated window disables Resume and shows Pilot
complete. Full collection expands the selected products using the keys already in memory.
All sports expands the selection to all twelve products and starts full collection with the same
keys. Omit `--sports` for a pilot of every product; the other country-based products use the same
four featured countries, MMA uses two categories, and Formula 1 uses two advertised seasons.

- API-Sports: at most 12 requests per sport per pilot run, including a fresh `/status` check.
- API-Sports daily cap: 80 attempted requests per sport per UTC day across repeated runs.
- API-Sports requests are spaced at least 7.5 seconds apart (eight/minute), below its documented
  free-tier ten/minute limit. Spacing survives restarts; one collector can use a dataset at a time.
- Stop when the provider reports only 20 requests remaining. Another use of the same key still
  affects quota between checks; response headers and each run's status check reconcile it.
- TheSportsDB: at most 60 requests per pilot run, spaced at 0.8 seconds or more (75/minute).
- Country translations: eight public Unicode CLDR 48.2.0 tables, including French already added
  to Sohva on 30 September. Numeric world regions are excluded. Original spellings are retained.

Both successful and failed network attempts count. Failed imports roll back their rows and child
jobs together. HTTP 429 and API-Sports minute-limit errors inside HTTP 200 responses pause for
at least 65 seconds, honour a longer `Retry-After`, and retry the same pending request. Cooldowns
survive restarts and the window shows when it is waiting. Closing the window cancels the wait.
Three consecutive throttled network attempts, or a wait longer than five minutes, end that
provider's batch with its job still pending. All retries respect the per-run and daily caps.
Unexpected paging, empty rosters, response caps, missing seasons and schema changes appear in
the coverage report.
Other API-Sports errors include a bounded summary of recognised error fields, such as `plan`
or `season`, in both the window and coverage reports. Entered keys, links, email addresses,
long tokens and control characters are removed before displaying or saving that summary.
The original response and unrecognised error fields are never saved.

## Resume and inspect

Run the same pilot command again to resume pending jobs. After correcting a failed key, connection
or plan-access problem, add `--retry-errors` to requeue failed jobs. Resume after the daily reset
at 00:00 UTC if the daily budget was reached. No scheduled task or automatic daily run is created.
For a run stopped by the earlier minute-limit handling, resume with:

```powershell
python tools/sports_dictionary.py pilot --key-window --retry-errors
```

The corrected collector preserves the roster and quota ledger when adding cooldown checkpoints.
Other applications sharing the API-Sports key or IP can still trigger its minute limit; pacing
and temporary-limit recovery both apply.
`--retry-errors` does not retry known blocked seasons. After a subscription or season-access
change, use `--retry-blocked` to recheck those restrictions. Historical rosters help identify
teams but cannot establish complete current-season coverage.

All collected data and reports live in the ignored `.local/team-dictionary/` directory:

| File | Contents |
|---|---|
| `research.sqlite` | Checkpoints, attempted-request ledger, teams, typed identities, sourced names and reviews |
| `coverage.md`, `coverage.json` | Measured counts, pending/error jobs, warnings and successful requests by product |
| `teams.csv` | Canonical provider identities with national/gender evidence when available |
| `country_labels.csv` | Localised country names, territory codes and source URLs |
| `link_candidates.csv` | Exact-name candidates and TheSportsDB `idAPIfootball` evidence |
| `alias_candidates.csv` | Proposed aliases, languages, sources and separate identity/alias status |
| `alias_collisions.csv` | Names used by multiple source identities, including cross-provider duplicates |
| `aliases.proposed.jsonl` | Additional spellings grouped by API-Sports ID, with language, source, identity status and risk flags |
| `alias_review.csv` | Additional spellings to review; shared names and category loss appear first |
| `alias_identity_collisions.csv` | Names shared by distinct API-Sports IDs within a sport, including canonical-name collisions |
| `alias_coverage.csv` | Per-team alias/language coverage, including teams still missing aliases |
| `national_alias_gaps.csv` | Conflicting or missing national flags, category evidence and country-name matches |
| `alias_summary.md`, `alias_summary.json` | Counts and limits of the sourced enrichment pass |
| `entities.csv` | Fighters, drivers, competitions and circuits, with separate IDs and types |
| `entity_alias_candidates.csv` | Sourced names/codes/nicknames and manual review status for those entities |
| `entity_alias_collisions.csv` | Ambiguous aliases within each sport and entity type |

Create the plan or regenerate reports without keys or network:

```powershell
python tools/sports_dictionary.py plan
python tools/sports_dictionary.py report
```

Collect just the public translation layer without keys:

```powershell
python tools/sports_dictionary.py collect --providers cldr
```

After the authenticated pilot has established response shapes and plan access, prepare the full
queue without keys or network:

```powershell
python tools/sports_dictionary.py plan --full
```

Then choose Full collection in an updated pilot window, or open a full collection window:

```powershell
python tools/sports_dictionary.py collect --key-window
```

Full mode allows up to 80 calls per API sport per run, within the persisted 80/day cap, and 1,000
TheSportsDB calls per run at its existing 75/minute pacing. It needs no click between individual
rosters. The window retains its keys for later Resume batches, including after the UTC daily
reset. Resume in the updated window loads subsequent adapter and alias-report fixes without
replacing the window or entering its keys again.

Confirmed seasonal access carries forward into the full queue. Completed identical pilot leaf
requests are reused with their original timestamps and digests; indexes run again because full
collection expands beyond the pilot. The MMA gym directory also runs again to queue gym-filtered
fighter lookups. Team IDs remain deduplicated. This walks advertised partitions and preserves
coverage warnings; it is not a verified global-coverage claim. Each product's actual responses
and account access still need authenticated verification. No daily automation is created.

## Populate aliases and national translations

The first pass uses existing provider names, TheSportsDB alternates/short names and Unicode
country labels in eight languages. It records possible aliases rather than approving them.
Refresh the public country-code mappings and the four UK sporting territories once:

```powershell
python tools/sports_dictionary.py aliases --refresh-translations
```

This makes nine public Unicode requests, without keys or paid-provider quota. Subsequent runs
can use the saved sources offline:

```powershell
python tools/sports_dictionary.py aliases
```

The command prioritises existing pending TheSportsDB profiles when each side has one compatible
identity candidate. Shared candidates, known country/gender conflicts, blocked profiles and
completed requests are not prioritised. This changes request order, not identity review status.
Resume collection in the existing updated key window to fetch those profiles with its normal
pacing and limits. Each completed batch refreshes the alias reports and pending-profile priority.

Examples include Finland → Suomi and Germany → Saksa / Deutschland. Qualified country names
retain their original suffix: Finland U21 → Suomi U21, Finland W → Suomi W, Finland Women →
Suomi Women. These are constructed proposals; the suffix words are not fully translated.
An explicit national flag, identifiable country label/code and compatible metadata are required.
England, Scotland, Wales and Northern Ireland use their own sourced CLDR subdivision labels.
Three Finnish nicknames have federation sources: Huuhkajat, Helmarit and Leijonat, each tied to
one exact sport/category identity. Clubs do not acquire country translations from their location.

Review `alias_summary.md`, `alias_review.csv` and `national_alias_gaps.csv`. Risk flags include
unreviewed identity links, shared names, lost categories, short codes, historical names and
constructed country translations. `aliases.proposed.jsonl` retains multiple language/source
evidence rows for an equal spelling. The collision report compares distinct API-Sports IDs
within a sport, so two providers representing one identity do not themselves count as a collision.
Fighter nicknames and driver codes remain in the separate typed-entity reports.

Further provider profiles, missing national evidence, historical validity and additional
languages/nicknames remain research work. This pass does not claim every possible alias or
change the app's matching rules. All report writes use the dataset lock and atomic file replacement.

The 2 October comparison is saved locally as `alias_pass_delta.md`/`.json`, `aliases_added.csv`,
`aliases_removed.csv` and `teams_added.csv`. It reconstructs the first enrichment pass from a
copy of its frozen backup and verifies all original counts before comparing. The collection
already refreshes proposals at each batch end; an explicit pass verifies the current state and
recomputes profile priority. See the [implementation and device test plan](../../docs/sport-dictionary-integration.md)
for provider-independent identities, staged matching changes and emulator/Shield/Elisa acceptance gates.

## Identity and review rules

The team identity key is `(provider, sport, team ID)`; other entities add their entity type.
Equal numbers across providers, products or entity types do not mean equal identities.
No city/acronym guesses, qualifier stripping or transitive nickname groups.
An exact normalised name produces a review candidate, even when the country agrees.
One consistent, unique TheSportsDB `idAPIfootball` gives a strong direct football cross-reference;
duplicate IDs or contradictory country evidence remain candidates. It never approves aliases.

Country-name translations attach only to an explicitly national team with a verified country-name
base; explicit youth/women/reserve suffixes are retained. A club, Finland Women or Finland U21
does not acquire Finland's unqualified names. GB does not imply England, Scotland, Wales or
Northern Ireland; those identities have separately sourced labels. Former names need date review; this schema
retains collection dates but has no runtime validity-window policy yet.

Review the original source and distinguish men's, women's, age groups, reserves and historical
identities before accepting a link or alias. These commands operate on existing candidates only:

```powershell
python tools/sports_dictionary.py review-link --sport football --api-id 1 --tsdb-id 11 --decision accepted
python tools/sports_dictionary.py review-alias --sport football --api-id 1 --name "Fiction Club" --source "https://provider.example/teams/1" --decision accepted
python tools/sports_dictionary.py review-alias --sport mma --entity-kind fighter --api-id 1 --name "The Fiction" --source "https://provider.example/fighters/1" --decision accepted
python tools/sports_dictionary.py export
```

The examples are fictional. Use IDs and source URLs from the actual report. `export` writes
`dictionary.reviewed.jsonl` and `entities.reviewed.jsonl`, with canonical identities and accepted
aliases only. Rejecting a
link prevents its aliases from exporting. Shared aliases remain separate entries by team ID.
The format is a research interchange proposal, not an app asset. Integration still needs a
reviewed dataset, runtime indexing, cache-version change, matching fixtures and low-end measures.

## Verification

```powershell
python -m unittest discover -s tools -p 'test_*.py'
python tools/audit_public_source.py
```

Tests use fictional teams and reserved source URLs. They cover import rollback, resumed quotas,
HTTP-200 service errors, header-only keys, reflected-key redaction, rejected redirects, rate
limits, omitted pages, identity collisions, national-category separation and reviewed exports.
All-product fixtures exercise discovery, product versions, shared pacing, separate caps,
enrichment fairness, typed exports and schema-2 migration without using real API keys or network.
Alias tests cover qualified translations, national flag/country/gender conflicts, UK sporting
territories, sourced nickname categories, rejected links, API-ID collisions, pending-profile
priority, bounded public-source refreshes and schema-4 migration with preserved quotas.
Query-plan tests require primary-key profile lookups and an identity-first provider-alias join.
On the build host (1 October 2026), 1,000 football alias lookups returned the same 178 evidence
rows before/after pinning the join: 1.953 s CPU before, 0.016 s after. An offline refresh of the
28,484-team snapshot took 5.37 s. These are desktop-tool measurements, not Android budgets.
The HTTP body has a 16 MiB desktop-only cap. The store and reports walk teams in keyset pages of
1,000; no global in-memory team/alias map is built. No provider artwork is downloaded.

## Source documentation and attribution

- [API-Football: collecting every advertised country](https://www.api-football.com/news/post/how-to-get-all-teams-and-their-ids)
- [API-Hockey: teams by league and season](https://www.api-football.com/news/post/ice-hockey-world-championship-2026-guide-to-using-data-with-api-sports)
- [API-Hockey reference: country filters](https://api-sports.io/documentation/hockey/v1)
- [API-AFL reference: complete team directory](https://api-sports.io/documentation/afl/v1)
- [API-Basketball reference](https://api-sports.io/documentation/basketball/v1)
- [API-Baseball reference](https://api-sports.io/documentation/baseball/v1)
- [API-Handball reference](https://api-sports.io/documentation/handball/v1)
- [API-Rugby reference](https://api-sports.io/documentation/rugby/v1)
- [API-Volleyball reference](https://api-sports.io/documentation/volleyball/v1)
- [API-NFL/NCAA reference](https://api-sports.io/documentation/nfl/v1)
- [API-NBA v2 reference](https://api-sports.io/documentation/nba/v2)
- [API-MMA reference: gyms, categories and fighters](https://api-sports.io/documentation/mma/v1)
- [API-Formula-1 reference](https://api-sports.io/documentation/formula-1/v1)
- [API-Sports free-tier minute limits and quota headers](https://www.api-football.com/news/post/how-ratelimit-works)
- [TheSportsDB v2 endpoints and limits](https://www.thesportsdb.com/documentation)
- [TheSportsDB team-detail example, including idAPIfootball](https://www.thesportsdb.com/api/v2/examples/lookup_team.json)
- [TheSportsDB terms and required source attribution](https://www.thesportsdb.com/docs_terms_of_use.php)
- [Unicode CLDR JSON 48.2.0](https://github.com/unicode-org/cldr-json/tree/48.2.0)
- [Unicode country-code mappings](https://github.com/unicode-org/cldr-json/blob/48.2.0/cldr-json/cldr-core/supplemental/codeMappings.json)
- [Unicode sporting-territory labels, release-48](https://github.com/unicode-org/cldr/tree/release-48/common/subdivisions)
- [Unicode-3.0 licence](https://github.com/unicode-org/cldr-json/blob/48.2.0/LICENSE)

Retain TheSportsDB and Unicode attribution/licence notices when a reviewed derivative is shipped.
API-Sports roster availability and entitlement must be recorded before claiming full coverage.
