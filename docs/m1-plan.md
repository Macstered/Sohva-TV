# M1 plan: sources and import

Milestone M1 of [plan/02-roadmap.md](rebuild/plan/02-roadmap.md): the viewer can add sources and the
app imports them at owner scale without harm. Specs: [10](rebuild/specs/10-sources-and-import.md),
[11](rebuild/specs/11-phone-setup.md), [73](rebuild/specs/73-security-privacy.md) and the Settings
shell with its Playlists section from [70](rebuild/specs/70-settings.md). Inventory: SRC-01…42,
PHONE-*, SEC-*. Branch `m1-sources`. Owner's go: 24 September 2026.

## Exit criteria (plan/02)

1. The owner-scale fixture (56,164 channels, 165,600 programmes, 200,000 films) imports on the
   stand-in within the plan/07 import budgets, Java heap under 128 MB.
2. Two concurrent imports of one source leave it intact (device test, proven to fail without the lock).
3. Parser tests cover the provider quirks of spec 10 §11.
4. No credential appears in logs or diagnostics.

## Decisions taken up front (plan/09 §A and §B M1 defaults)

- A5: past programmes kept for catch-up channels back to min(catch-up days, 24.5 h); other
  channels 3 h. Future 8 days.
- Confirm Remove source and Clear all guide data; narrow "Clear all guide data" to the guide.
- gzip only (no xz). `url-tvg` in the playlist header fills an empty XMLTV field; `#EXTGRP`
  supplies a missing group. Saving an edited source starts a sync. Size caps: 1 GiB runaway guard
  on every provider body, lines over 64 KiB skipped, titles ≤ 512 and descriptions ≤ 4,096
  characters stored. Xtream in-place updates in M1. Phone page token 128-bit, in the URL fragment.
- Content kind keeps beta 23's substring rule (compatibility), entry ids keep §6's SHA-256 rule.

## Design

- **Storage** (plan/04 §15): `source` (non-secret fields) with each source's addresses and
  credentials in the secret store under its id; `source_status` with error codes and a generation;
  `content_group`; `channel`, `movie`, `series`, `episode` updated in place by key with a
  `content_hash` (no snapshot copies); `epg_channel` and `programme` by snapshot, activated by
  flipping `source_status.epg_snapshot` and sweeping the old one in chunks.
- **Diff import** for channels and VOD: batches of 250 look up the stored hashes of their keys,
  insert new rows and update changed ones; the keys seen are kept as 64-bit hashes in a primitive
  set (≈ 1.6 MB for 200,000); after a complete, non-empty parse the unseen rows are deleted by
  keyset pages. An empty or failed parse deletes nothing.
- **One import runner** in `:core:sync`: per (source, kind) mutex, playlist → guide → catalogue,
  parser and writer on two background-priority threads joined by a channel of capacity 1, the
  pause gate between batches, progress as a flow, ANALYZE of touched tables once at the end.
- **Modules**: `:core:net` (OkHttp base and provider client, M3U, XMLTV and Xtream parsers, address
  policy, phone setup server), `:core:sync` (runner, importers, WorkManager jobs), `:feature:settings`
  (Settings shell, Playlists, source page, phone setup UI). Moshi's streaming reader for JSON,
  the platform pull parser for XML (kxml2 in JVM tests), ZXing core for the QR code.

## Order of work

1. `:core:model`: source model and rules, address policy, `get.php` derivation, name normaliser
   and ids, stream tags, XMLTV timestamps, M1 error codes with their strings.
2. `:core:net`: HTTP clients, byte caps and gzip sniffing, M3U parser, XMLTV parser with the keep
   filter, Xtream streaming client with the per-category fallback. JVM tests for every quirk.
3. `:core:data`: schema v2 with migration and plan tests; source store; DAOs for the diff, the EPG
   snapshots, status, groups.
4. `:core:sync`: runner, the five importers, guards, scheduler and worker; concurrent-import test.
5. `:feature:settings`: Settings frame, Playlists, source page, status and health; device tests.
6. Phone setup server and QR.
7. Fixture growth (200,000 films, 1,500 series; an Xtream-shaped variant) and the owner-scale
   import measurement on the stand-in, heap sampled.
8. Migration spike (reading a beta 23 install) — needs the owner's input, see below.
9. Exit check.

## Needs from the owner

- The migration spike needs a beta 23 installation the rebuild can read. Reading its encrypted
  sources is only possible inside the same app (same signing key) or from a debug-signed beta 23
  build; this repository cannot build the old app. Asked at the M1 exit.

## Exit check (24 September 2026)

Exit criteria:

1. Owner-scale import on the stand-in within budget, Java heap under 128 MB — **met**
   (docs/performance-log.md: 50,546 channels 15.9 s, guide 13.8 s, 200,000 films 60.9 s,
   Xtream 13.3 s / 15.0 s / 36.1 s; heap max 30 MB).
2. Two concurrent imports of one source stay intact, proven to fail without the lock — **met**
   (`ImportScenarios.twoGuideImportsOfOneSourceOverlap`, JVM and emulator; without the lock the
   active guide kept 3,000 of 12,000 programmes).
3. Parser tests cover spec 10 §11's provider quirks — **met** (`:core:net` tests).
4. No credential in logs or diagnostics — **met** (`noCredentialReachesTheLog`, redaction tests in
   `:core:net` and `:core:model`).

Inventory (the Done list is ticked in `rebuild/plan/01-feature-inventory.md`, 24 September 2026):

- Done: SRC-01…10, 12…37, 40, 41; PHONE-01…09, 12…14; SEC-01…03, 07…13, 15 (128-bit token,
  decision of 24 Sept), 16, 20, 23 (playlists, guides, Xtream; streams in M2), 25, 28.
- Stored in M1, shown or enforced in a later milestone: SRC-11 (limit refused at playback, M2),
  SRC-38 (offset applied on screen, M2), SRC-42 (no stutter during playback, needs the player,
  M2), SRC-39 (backup), PHONE-10 (logo, M3), PHONE-11 (addons, Discover).
- Migration spike (step 8) — **done** on the stand-in emulator with a release build signed by the
  release key (owner's go, 24 Sept). Beta 23 (build 57) was installed, given an M3U source, an
  Xtream source and both service keys through its own phone page, then upgraded in place. The
  rebuild showed both sources with their names, addresses and credentials, synced the M3U at once
  (3 channels, 150 programmes), and none of beta 23's WorkManager jobs ran against missing classes
  (the first try found they did; fixed by cancelling them by worker tag, see decisions.md).
- SEC-04: the first builds' single-source file is migrated (`Beta23SourceImportTest`); **clearing**
  the old files waits for the last importer of plan/04 §17 (decision "Beta 23 import, M1 part"),
  so SEC-04 is ticked when that milestone deletes them.
