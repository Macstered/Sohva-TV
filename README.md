# Sohva TV

A free, open-source media player for Android TV and Google TV, built for the remote control. This
repository is the from-scratch rebuild of Sohva TV `0.1.0-beta.23` (build 57): the same features,
an almost identical look, and a much lighter footprint so it runs well on low-end TV boxes.

Current beta: [0.2.0-beta.15, build 125](https://github.com/Macstered/Sohva-TV/releases/tag/v0.2.0-beta.15).
See the [release notes](docs/release/RELEASE_NOTES.md) and [installation instructions](docs/release/INSTALL.md).

- [AGENTS.md](AGENTS.md): the rules for everyone who writes code here. Read it first.
- [docs/rebuild/](docs/rebuild/README.md): the rebuild kit (plan, feature specs, design, assets,
  strings). It is the specification.
- [docs/decisions.md](docs/decisions.md): every decision the kit did not settle.
- [docs/performance-log.md](docs/performance-log.md): measurements per milestone.

Status: the rebuild is in beta testing, including live TV, the guide, movies and series, profiles,
Discover, Trakt and configurable Home rows. See the [feature inventory](docs/rebuild/plan/01-feature-inventory.md)
for parity and the [performance log](docs/performance-log.md) for measured limits. Low-end device
validation continues with each change. Build with `./gradlew :app:assembleDebug`; run every local
check, including the Play APK and bundle, with `python tools/check_all.py`.

Licence: GPL-3.0-only for original source; see [LICENSE](LICENSE).
