# M10 plan: Trakt

Scope (plan/02 M10): [specs/51](rebuild/specs/51-trakt.md): device-code sign-in per profile,
scrobbling from the VOD and Discover players (never live TV), progress and watched overlay on
cards, resume from another device, Watch next and Recommended for you on Home, disconnect. Branch
`m10-trakt`, from `main` after M9 and the Shield playback fix (27 September 2026).

Inventory: TRAKT-01…30, with the Trakt parts of HOME (Continue watching, Watch next,
Recommended, first-sync notice), PROF-09/-14, ADDON-34/-52, SET-11 (Accounts section).

## Exit criteria (plan/02)

- Matching by TMDB/IMDb id only.
- Sync is incremental and never runs during playback except scrobbles.
- The rows appear independently of slower Home sections.

## Where things live

- `:feature:trakt` (new, plan/03 §3): the auth, identity and API clients (own rules on the shared
  OkHttp base: no redirects, no retry, fixed origins, body limits), device sign-in, the account
  store (the main envelope cipher), the scrobbler and its queue, the sync loop, Watch next and
  Recommended, the Accounts panel, the Trakt title screen.
- `:core:data`: `trakt_state` (main database, migration 10→11) with its indexed overlay queries;
  the VOD merge and Continue watching's Trakt part.
- `:core:player` / `:feature:player`: a scrobble hook the VOD service and the addon session call.
- `:app`: credentials from the ignored `.local/trakt/trakt-credentials.properties` into
  BuildConfig, wiring, Home rows, the beta 23 account import (decision A1), the demo seed.

## Order of work

1. **Protocol**: models, failure mapping, Retry-After, auth (device code, poll, refresh),
   identity, API (scrobble, last activities, playback, paged watched lists, show progress and
   summary, recommendations). JVM tests mirroring §11 "Unit".
2. **Accounts**: encrypted per-profile store, device sign-in with its access rule, the Accounts
   panel with its states, disconnect, profile removal, token refresh.
3. **Scrobbling**: identity mapping (VOD, Discover), the scrobbler state machine, the persisted
   queue (latest per title, stop at first outage, kept while re-authorisation is needed), the
   interrupted-playback pause, VOD and Discover wiring.
4. **Sync**: `trakt_state` and the migration, the loop (profile start, 15 min, 3 s after a STOP;
   suspended while a player is up), incremental per activity, paging, format version, diff writes.
5. **Surfaces**: VOD and Discover overlays, the VOD merge and resume, Continue watching's Trakt
   part, Discover resume by fraction, Home rows and hero, the Trakt title screen, first-sync notice.
6. Demo seed, beta 23 import, measurements, inventory, exit.

## Open questions (spec 51 §10), settled by the owner's rule

The spec's proposal where it makes one, else beta 23 (recorded in docs/decisions.md).
