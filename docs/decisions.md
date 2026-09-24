# Decisions

Every question the kit ([docs/rebuild/](rebuild/README.md)) did not settle, with the answer and why.
Newest last. "Default" means the default from
[plan/09-owner-decisions.md](rebuild/plan/09-owner-decisions.md), followed until the owner says
otherwise.

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-24 | Where does the kit live in the new repository? | `docs/rebuild/`, with `AGENTS.md` and `CLAUDE.md` at the root. Links broken by the move were rewritten mechanically (root `AGENTS.md` → `docs/rebuild/…`; kit links to `../AGENTS.md` → `../../../AGENTS.md`); AGENTS.md §2 says short paths are relative to `docs/rebuild/`. No other kit text changed | Owner's instruction; the kit README suggests it |
| 2026-09-24 | Commit the kit's images (about 112 MB, largest file 6.9 MB) with Git LFS? | Plain git for now; revisit before the first push | Nothing is pushed yet, so moving `design/screenshots/` and `assets/flavors|source/` to LFS or dropping `older-builds/` later only rewrites local history. Owner to choose before the first push |
| 2026-09-24 | Branches | `main` holds the kit import; each milestone works on its own branch (`m0-foundations`, …) and merges to `main` when its exit criteria pass | AGENTS.md §8 |
| 2026-09-24 | A1 Existing testers' data | Default **B**: one-time import from the old database and sources on first start, plus `.smbak` restore. The new database is `sohva.db`; new Keystore aliases differ from the old ones | plan/04 §17 |
| 2026-09-24 | A2 minSdk | Default **23**, platform SQLite | plan/00 §8 |
| 2026-09-24 | A3 First version | Default **versionCode 100, `0.2.0-beta.1`** | Leaves 58–99 for beta 23 hotfixes |
| 2026-09-24 | A4 Search matching | Default **FTS token prefix**, accents not folded | spec 03 |
| 2026-09-24 | A5 Guide history kept | Default: catch-up channels min(catch-up days, 24.5 h), others 3 h | Smaller database, faster imports |
| 2026-09-24 | A6 Backup format | Default **format 3**, reading formats 1–2 | spec 71 |
| 2026-09-24 | A7 Restricted profiles | Default **filter everything by allowed groups, gate managers with the PIN** | spec 04 |
| 2026-09-24 | A8 Reduce motion | Default **automatic, plus a switch in Settings › General** | design/01 §16.3 |
| 2026-09-24 | A9 Network policy | Default **HTTPS-only** for first-party services and addons; privacy text corrected | spec 73 |
| 2026-09-24 | A10 Time format | Default **locale everywhere** | spec 74 |
| 2026-09-24 | Section B behaviour choices | Defaults as listed in plan/09 §B, recorded again here when their milestone implements them | plan/09 |
