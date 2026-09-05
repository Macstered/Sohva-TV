# Sohva TV

Sohva TV is a remote-controlled media player for Android TV and Google TV. It
combines Live TV, programme data, provider movie and series catalogues, playback
resume, search, and the optional Sohva Sport section in one D-pad-first app.

Sohva TV supplies no channels, subscriptions, playlists, provider credentials,
or developer API keys. Use only media sources and services you are authorized
to access.

## Download

The current tester build is **0.1.0-beta.5 (build 6)**. It is an early beta, not
a stable release. Download the signed APK only from the explicitly numbered
[GitHub pre-release](https://github.com/Macstered/Sohva-TV/releases/tag/v0.1.0-beta.5)
and verify the published SHA-256 value.

- [Installation and setup](INSTALL.md)
- [Testing and feedback](TESTING.md)
- [Beta 2 release notes](RELEASE_NOTES.md)
- [Privacy policy](PRIVACY.md)
- [Security reporting](SECURITY.md)

The beta 1 tag was created before the source was published, so that release's
automatic source archives contain its original release-document snapshot. The
maintained application source is on `main`; future release tags will point to
the corresponding source commit.

## Build from source

Requirements:

- JDK 17 or newer
- Android SDK platform 36
- Android SDK Build Tools 35.0.0 or newer

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebugAndroidTest assembleRelease --warning-mode=fail
```

On macOS or Linux, use `./gradlew` with the same tasks. A public clone contains
no release signing key. Debug APKs are signed with the normal Android debug key;
`assembleRelease` produces an unsigned verification build.

The source is split into four modules:

- `app` — application shell, Home, navigation, scheduling, and packaging
- `core` — database, models, security, preferences, and shared TV UI
- `iptv` — source clients, guide, catalogue, metadata, settings, and playback
- `sportmate` — sports retrieval, normalization, matching, and Sohva Sport UI

The `com.streammate.tv` application ID and some internal StreamMate class names
are retained deliberately. Changing them would break in-place updates and access
to existing beta data; they are legacy implementation identifiers, not the
current product name.

## Privacy and security

Credentials remain on the Android device and are encrypted with Android
Keystore. Nothing in the app contacts a developer-operated backend. Optional
TMDB and API-Sports features use credentials supplied by the user; TVmaze is
keyless. See the [privacy policy](PRIVACY.md) and
[third-party notices](THIRD_PARTY_NOTICES.md).

Never include real playlist addresses, passwords, API keys, exported backups,
or provider data in source, fixtures, pull requests, or reports. Use reserved
example domains and fictional test data.

## Contributing and support

See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Private bug
or security reports can be sent to
[hello@luontra.fi](mailto:hello@luontra.fi). You can support development through
[GitHub Sponsors](https://github.com/sponsors/Macstered).

## Licence

Sohva TV's original source code is licensed under the GNU General Public
License version 3 only (`GPL-3.0-only`). See [LICENSE](LICENSE).

Macstered currently distributes the beta without charge and does not operate it
as a commercial service. GPLv3 nevertheless permits commercial use subject to
its terms. Third-party software, service data, attribution graphics, logos, and
trademarks remain under their respective terms.
