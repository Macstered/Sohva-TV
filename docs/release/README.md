# Sohva TV

Sohva TV is a remote-controlled media player for Android TV and Google TV. It
brings Live TV with a programme guide, catch-up and reminders, your providers'
movie and series libraries, search, Discover addons, optional Trakt sync and
the optional Sohva Sport section together in one app built for the D-pad.

Sohva TV supplies no channels, subscriptions, playlists, provider credentials,
addons or developer API keys. Use only media sources and services you are
allowed to use.

## This release: 0.2.0-beta.2

This tester build is version `0.2.0-beta.2`, Android build 105. It is a beta,
not a stable release.

It is the rebuilt Sohva TV. The app was written again from the start. It has
the same features as `0.1.0-beta.23` (build 57) and looks almost the same. It
was built to be lighter, so that it also runs well on low-end TV boxes with
2 GB of memory or less, not only on the Nvidia Shield.

It is still the same app to Android: the package name `com.streammate.tv` and
the signing key have not changed. It installs over beta 23 as an ordinary
update and keeps your data. On the first start it moves beta 23's data into
its new storage. See [Installation and update](INSTALL.md).

Download it only from the numbered
[GitHub pre-release](https://github.com/Macstered/Sohva-TV/releases/tag/v0.2.0-beta.2),
or through **Settings > About > Check for updates** in beta 23, and check the
SHA-256 value.

## What is in the tester pack

| File | What it is |
| --- | --- |
| `sohva-tv-0.2.0-beta.2.apk` | The signed app |
| `sohva-tv-0.2.0-beta.2.api31.dm` | Install profile for Android 12 and newer (optional) |
| `sohva-tv-0.2.0-beta.2.api28.dm` | Install profile for Android 9 to 11 (optional) |
| `SHA256SUMS.txt` | SHA-256 checksums of the files above and of the documents |
| The nine documents below | Instructions, notes, policy and licences |

The install profiles let Android prepare the app while it installs. The
in-app updater uses them automatically. You do not need them when you install
the APK by hand.

## Documents

- [Installation and update](INSTALL.md)
- [What to test and how to report a problem](TESTING.md)
- [Release notes, in English and Finnish](RELEASE_NOTES.md)
- [Discover addons](ADDONS.md)
- [Privacy policy](PRIVACY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)
- [GNU General Public License version 3](LICENSE)
- [Apache License 2.0](LICENSE-APACHE-2.0.txt)

## Source code

The source is in the public repository
[Macstered/Sohva-TV](https://github.com/Macstered/Sohva-TV). The release tag
`v0.2.0-beta.2` points to the source of this build. A public copy of the source
contains no signing key and no service credentials.

The application ID `com.streammate.tv` and a few internal names from the
app's earlier name are kept on purpose. Changing them would break updates and
access to existing data.

## Privacy and security

There is no developer server, account, analytics, advertising or crash upload.
Credentials and keys stay on the TV, encrypted with an Android Keystore key.
Optional services are contacted directly from the TV, and only when you turn
them on. See the [privacy policy](PRIVACY.md).

Never post playlist addresses, passwords, API keys, addon addresses, backups
or raw logs in a report.

## Contact and support

Send bug reports and private security reports to
[hello@luontra.fi](mailto:hello@luontra.fi). You can support development
through [GitHub Sponsors](https://github.com/sponsors/Macstered).

## Licence

Sohva TV's own source code is licensed under the GNU General Public License
version 3 only (`GPL-3.0-only`). See [LICENSE](LICENSE).

The beta is distributed free of charge and is not run as a commercial service.
GPLv3 still allows commercial use under its terms. Third-party software,
service data, attribution graphics, logos and trademarks stay under their own
terms; see [third-party notices](THIRD_PARTY_NOTICES.md).
