# M3 plan: channels, catch-up, reminders, remote keys

Milestone M3 of [plan/02-roadmap.md](rebuild/plan/02-roadmap.md). Specs:
[21](rebuild/specs/21-channel-management.md) (channel management),
[22](rebuild/specs/22-catchup-and-reminders.md) (catch-up and reminders) and
[31](rebuild/specs/31-remote-button-mapping.md) (remote button mapping, the Settings grid and the
full dispatcher). Inventory: CHAN-*, CATCH-*, REM-*, REMOTE-*, and the guide and player items M2
left for M3 (GUIDE-12, -27, -29, -31, -36; PLAY-02, -09, -10, -16 in catch-up). Branch
`m3-channels`, started from `m2-live` on 25 September 2026 (owner: "keep going autonomously",
"push when needed").

## Exit criteria (plan/02)

1. The catch-up address builder's unit tests cover every scheme (spec 22 §11).
2. A reminder fires while another screen of the app is open and after a process restart.
3. Channel edits survive a re-import.
4. The channel manager reads in pages (the old one read every channel at once); at owner scale a
   D-pad press in its list meets the key-press budget and the heap stays under 64 MB.

## What waits for a later milestone

| Item | Needs | Milestone |
|---|---|---|
| PIN lock (CHAN-20, -26; CATCH-12's PIN part; PLAY-40) | the parental PIN and its screen, spec 04 | M6. Until then no PIN exists, so the Lock button reads "Configure PIN in Settings" and is disabled, as beta 23 does without a PIN; the `locked_channel` table and the playback gate hook are built now |
| Library manager entry (CHAN-01's second entry, GUIDE-35) | spec 42 | M4 |
| Sohva Sport reminders (REM-02, the match-card side of REM-06/-07) | spec 60 | M8; the reminder model and alert already carry the `event` kind |
| Channel customisations in backups (CHAN-29), remote mappings in backups (REMOTE-18's backup part) | spec 71 | M7 |
| Home's recent-channels row (part of CHAN-25) | spec 02 Home rows | M5 |
| VOD transport controls (PLAY-03, -09 for films) | spec 40 | M4 (catch-up gets them now) |

## Decisions for the specs' open questions

Recorded in `docs/decisions.md` as they are built:

- Spec 22 Q1, exact alarms: declare `SCHEDULE_EXACT_ALARM`, check `canScheduleExactAlarms()`, fall
  back to `setAndAllowWhileIdle`, never let a `SecurityException` out. Verified on API 30 and 34
  emulators.
- Spec 22 Q2, zone for M3U formatted times: the TV's zone (parity).
- Spec 22 Q3, history: 12 h for every channel (parity).
- Spec 22 Q4, texts: "Archive · %1$s" and "This programme is not available from the archive.",
  new strings in seven languages (the five drafts marked for the owner's review).
- Spec 22 Q6: OK on the airing programme of a catch-up channel starts it from the beginning
  (parity).
- Spec 21 Q1, Q2, Q3, Q5: parity (Source = All interleaves; Delete list has no confirmation; a
  new manual mapping shows programmes after the next guide refresh; customisations follow the
  channel id).
- Spec 21 Q4: "Change EPG channel" opens a searchable, paged picker of the source's XMLTV channels
  with Automatic and the current value first (CHAN-NFR-06); the cycling button is not kept.
- Spec 21 Q6: the logo field shows the stored address as beta 23 does.
- Spec 31 Q-02 one zap per hold; Q-03 a hold's release activates nothing; Q-04 CH+ = previous
  (parity); Q-05 seek labels name the step; Q-06 Nothing on Back hold counts as "not mapped".

## Design

- **Edits survive imports** (CHAN-FR-02, exit 3): `channel_custom` (plan/04 §15.3), keyed by the
  channel key, is never touched by imports. `channel` gains provider columns (`provider_name`,
  `provider_group_id`, `provider_logo_url`) next to the effective ones (`name`, `sort_name`,
  `group_id`, `logo_url`, `number`, `epg_id`, `display_rank`, `visible`). An import writes provider
  values into both; after its diff it re-applies the source's `channel_custom` rows (a small set,
  paged by key) before the group counts are finished. An edit writes `channel_custom` and applies
  that one channel in the same transaction.
- **Positions** (CHAN-NFR-04): a position lives in `channel_custom.position`. The first move in a
  source gives every channel of that source a position (steps of 1,024 in the current order) in
  pages of 2,000 rows on the bulk dispatcher; later moves write one row, renumbering a page only
  when a gap runs out. Channels without a position rank after those with one (beta 23's "last").
- **Custom group** (CHAN-FR-04): moves the channel to the `content_group` with key
  `name:<lower-cased title>` in the source's live room, created when missing; both groups' counts
  are updated.
- **Channel management** (`:feature:channels`): keyset pages of ≤ 200 by `display_rank` (Playlist)
  or `sort_name` (A–Z) with the filters in SQL; count by `COUNT(*)`; chips from `content_group`;
  search debounced 250 ms, `LIKE` with escapes; the body never reads the selection.
- **Catch-up**: the guide asks `CatchupRules` when it prepares a row's labels; the service builds
  the address with `CatchupAddress` (`core:net`) at play time, takes the source's lease, and the
  player shows the transport controls.
- **Reminders**: `reminder` table (household), one alarm, a receiver that fires due reminders
  and reschedules; an in-app alert over any screen; bring-forward with the overlay grant; the
  notification as the fallback.
- **Remote**: the mapping stored as the `remote_mappings` string set, decoded into an array; the
  Settings grid and action list; the player's dispatcher extracted as pure logic with a fake
  player in unit tests.

## Order of work

1. Pure logic with JVM tests: catch-up builder and availability; reminder schedule and ringing
   queue; mapping codec; the dispatcher; the move rule; shown-value rules. (First three done.)
2. `:core:data` schema v4: provider columns, `channel_custom`, `channel_list`,
   `channel_list_member`, `locked_channel`, `reminder`; the migration with its test; DAOs and
   query-plan tests; the import re-applies edits.
3. Remote: preferences, the Settings grid, the full dispatcher in the player; device tests with
   real key events.
4. Catch-up: guide buttons and OK, the service's archive path, transport controls.
5. Reminders: store, alarm, receiver, alert, notification, overlay prompt, Settings row.
6. Channel management screen, phone logo, custom lists on the guide rail.
7. Owner-scale measurement of the channel manager; exit check.
