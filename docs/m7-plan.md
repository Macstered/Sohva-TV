# M7 plan: Settings completion, backup, updates, diagnostics, languages

Scope (plan/02 M7): the rest of [specs/70](rebuild/specs/70-settings.md),
[specs/71](rebuild/specs/71-backup-restore.md), [specs/72](rebuild/specs/72-updates-about-diagnostics.md)
and [specs/74](rebuild/specs/74-localization.md). Branch `m7-settings`, from `main` after the M6
merge (26 September 2026).

Inventory: SET-01…57, BACKUP-01…15, ABOUT-01…29, L10N-01…13; with them PROF-23, CHAN-29, SRC-39,
ORG-35, REMOTE-18, VOD-54, SHELL-27, PLAY-29 (picture in picture: its switch is in the option
table), META-34, SEC-22, SEC-24.

## Exit criteria (plan/02)

- A beta 23 `.smbak` restores into the rebuild.
- The updater installs a test release with its profile (`reason=install-dm`) on the emulator.
- Diagnostics contain no secret.
- Every string present in every language the old app had.

## Order of work

1. **Settings, part A**: General complete (interface language, size, theme, channel numbers,
   time zone with its dialog, startup screen); Playback complete (buffer, recovery, skip step,
   match the display, next episode, picture in picture, subtitle look, VOD languages); the image
   cache rows; per-section status lines and busy state; picture in picture itself.
2. **Backup** (spec 71): the `.smbak` format (versions 1 and 2 read, 2 written), streamed export
   and restore through the document pickers, Backup & tools with Clear all guide data; a beta 23
   file restored in a device test.
3. **Updates, About, diagnostics** (spec 72): the release feed, download with SHA-256 check and
   the `.dm` profile through a PackageInstaller session, the legal screen, Save diagnostics with
   redaction; the updater installing a test release on the emulator.
4. **Languages** (spec 74): the language picker's restart path, the remaining strings audit in all
   seven languages, formats.
5. Measurements, inventory, exit.

## Status

- Part 1 (Settings, part A): done, commit 5141206.
- Part 2 (Backup): done. `.smbak` read and written by an independent test encryptor too; save,
  restore with the question naming removed sources, the restore marker, Clear all guide data
  (programmes only). Device suite `SettingsBackupTest` 5/5 on the API 30 stand-in.
