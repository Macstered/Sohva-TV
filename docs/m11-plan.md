# M11 plan: parity, migration and first release

Roadmap: [plan/02 M11](rebuild/plan/02-roadmap.md). Branch `m11-release`. Exit: the owner's go to
publish; publishing, tagging and any install on the owner's devices happen only with the owner's
explicit wording for this release.

## Where M11 starts (27 September 2026)

- Inventory: 719 items ticked, 39 open (SHELL 10, SEC 10, PLAY 6, GUIDE 4, VOD 2, and one each of
  SRC, REMOTE, REM, PHONE, ORG, CHAN, CATCH). Most are built but never formally verified.
- Beta 23 import (decision A1, option B): sources and service keys (M1), hidden categories (M4),
  Discover (M9) and Trakt (M10) come across. The rest of plan/04 §17's table does not yet.
- The beta 23 release APK (build 57) is in the ignored `.local/beta23/` for the real upgrade test.
- Release pieces from M0 onward: R8 and profiles, the size and method gates, the updater and its
  feed contract (spec 72), the audits in `tools/check_all.py`.

## Order of work

1. **Beta 23 import, the rest of §17.** Read the old stores read-only by column presence
   (`PRAGMA table_info`), write the new tables, one marker per part in `app_meta`:
   - DataStore `streammate_preferences` → the rebuild's preferences and profiles (per-profile keys,
     favourites, recents, last channel, locks, allowed groups);
   - `channel_preferences`, `channel_lists`, `channel_list_members` and `files/channel-logos`;
   - `organization_rules` (film identities re-keyed after the first catalogue import);
   - `playback_progress` → `watch_progress`;
   - `reminders`, `event_channel_decisions`, `team_aliases`;
   - `catalogue_metadata_overrides`, `catalogue_genres`, pinned `metadata_cache` → `metadata_match`;
   - the parental PIN and the remaining secrets; the locale and artwork-cache files;
   - then, after every part has succeeded, delete the old files (§17 failure path: keep them when a
     part failed, say so once in Settings › Playlists).
   Fixtures are old databases built from beta 23's exported schemas with fictional rows (no
   secrets). A first start that takes longer than about a second shows a short "updating" state.
2. **Inventory walk.** Each open ID proven by a test or fixed; SEC items with the release audits.
3. **Release pipeline** ([plan/06](rebuild/plan/06-quality-testing-release.md)): version 100
   `0.2.0-beta.1` (decision A3), the five assets (APK, `.api31.dm`, `.api28.dm`, `SHA256SUMS.txt`,
   tester ZIP), tester notes in English and Finnish, the release-body contract, the release-document
   and APK safety audits (SEC-26, -27), release dex verification.
4. **Performance pass** against every budget in plan/07 on the stand-in; the slow box with the
   owner.
5. **Upgrade test over a real beta 23 install** on the emulator: install build 57, give it data,
   install the rebuild over it with the release key, check every part of §17 came across.
6. Full suites, inventory, the owner's go.

## Owner questions

- The "updating" first-start state needs new strings (plan/04 §17): default a centred line
  "Updating Sohva TV…" in the seven languages, shown only past one second.
