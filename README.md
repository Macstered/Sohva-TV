# Sohva TV

A free, open-source media player for Android TV and Google TV, built for the remote control. This
repository is the from-scratch rebuild of Sohva TV `0.1.0-beta.23` (build 57): the same features,
an almost identical look, and a much lighter footprint so it runs well on low-end TV boxes.

- [AGENTS.md](AGENTS.md): the rules for everyone who writes code here. Read it first.
- [docs/rebuild/](docs/rebuild/README.md): the rebuild kit (plan, feature specs, design, assets,
  strings). It is the specification.
- [docs/decisions.md](docs/decisions.md): every decision the kit did not settle.
- [docs/performance-log.md](docs/performance-log.md): measurements per milestone.

Status: milestone M0 (foundations) done: the shell, the design system and the harness build and
run on an Android TV emulator; features arrive from M1 (sources and import). Build with
`./gradlew :app:assembleDebug`; run every check with `python tools/check_all.py`.

Licence: GPL-3.0-only for original source (the licence file is added with the first code).
