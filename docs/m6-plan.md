# M6 plan: Profiles and parental controls

Scope (plan/02 M6): [specs/04](rebuild/specs/04-profiles-parental.md), with the routing rules of
[specs/01](rebuild/specs/01-app-shell-navigation.md) §4.4–4.5 (SHELL-FR-20…23, -32…34). Branch
`m6-profiles`, from `main` after the M5 merge (26 September 2026).

Inventory: PROF-01…22, HOME-36, SEARCH-18. Later: PROF-23 (the backup, M7).

## Exit criteria (plan/02, spec 04 §11)

- Every per-profile table and preference is isolated in tests; removing a profile removes all of it.
- Restricted profiles never see Discover or Trakt entry points (the rail, Settings › Accounts).
- The clear-state test rule resets profiles, the PIN and the allowed groups.
- Profile switch from the rail: Home's first content frame for the new profile within 1 s on the
  stand-in; a restricted profile's guide opens within the budget of an unrestricted one.

## Where things are stored

| What | Where | Why |
|---|---|---|
| Profiles (id, name, colour), active profile, ask at start, PIN configured flag | DataStore, beta 23's keys and encoding (spec 04 §6) | The start decision (who is watching, the Last-channel start) is made from the one preferences snapshot read before the first frame (spec 04 §9); the database opens after it |
| Last channel | DataStore `last_channel_id[:id]` | Read at start, as above |
| Allowed groups | table `profile_allowed_group(profile_id, room, group_key)` | Joined inside the paged and limited queries (spec 04 §9) |
| Locked channels, favourites, recents, positions | the existing tables with `profile_id` | plan/04 §15.8 |
| The PIN | the secret store (`parental_pin_v1`), encrypted | spec 73; verified off the main thread in constant time |

## Order of work

1. Data: the profile codec and rules (unit), the profile store (add, remove with everything it
   keeps, switch), the PIN (set, verify, change, remove), locks, allowed groups with migration
   8→9.
2. The active profile behind every per-profile read: walls, progress, Home, favourites, recents,
   last channel; Continue watching and the guide's focus restart on a switch.
3. Restrictions in SQL: the guide's rails, lists and dialling, the player's list and zapping, the
   walls and their rails, Search (all five groups), Home's recents and Continue watching; plan
   tests; "This profile's groups are not in the current sources".
4. Gates: one channel starter (group check, then lock) for every play, catch-up and zap; the PIN
   screen; `PinGate`, `ProfileGate`; Settings and the managers behind the PIN for a restricted
   profile.
5. Who is watching: the start-time picker, the rail item and route, the Settings rows (profiles,
   what this profile may see, the group pickers, the note), Parental controls, the lock button in
   Channel management.
6. Device tests (spec 04 §11), the clear-state rule, measurements, inventory, exit.

## Status (26 September 2026)

Built, with tests (unit, query plans, device tests on API 30):
- The household in the start snapshot; the active profile behind every per-profile read and
  write; allowed groups joined in the guide, player, walls, Search, Home and Continue watching
  queries (migration 8→9); locks; the PIN encrypted, checked off the main thread, changed without
  losing the locks.
- Who is watching at start and on the rail; the PIN screen; the gates in front of locked channels
  (guide, Home, Search, reminders, zapping, the Last-channel start), Settings and the managers
  for a restricted profile, and entering an unrestricted profile; the refusal toast.
- Settings › General › Profiles, Settings › Parental controls, Channel management's lock.

Exit measurements (docs/performance-log.md): a switch from the rail shows the new profile's Home in
189 ms (median, budget 1 s); a restricted profile's guide opens its first group in 434 ms and All
channels in 322 ms (screen-open budget 700 ms). The whole device suite (27 classes) is green.

Later: PROF-23 (the backup, M7); the Discover, Trakt and sport parts of PROF-09 and PROF-14.
