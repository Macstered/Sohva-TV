# Stremio-compatible addons

Independent Android library for user-installed HTTP addons. Discover's TV UI
lives in `app/src/main/java/com/streammate/tv/addons`. Host/storage initialization
is lazy on explicit Discover entry. This module has no startup initializer,
workers, UI, application permissions or dependency on the IPTV database.

Configured endpoints and stored payloads use device-local encryption. Requests
are bounded and profile-checked; remote identifiers and declared media types are
retained. Catalog cache, Library and viewing progress are independent of IPTV.
The existing portable Sohva backup does not include this module's data.

See [user setup and limitations](../ADDONS.md) and [privacy](../PRIVACY.md).

## Tests

`testDebugUnitTest` covers protocol, storage policies and UI policies. Ordinary
debug instrumentation covers Home integration and restricted-profile access.
`scripts/Test-SohvaAddonsLab.ps1 -Serial <emulator-serial> -Ffmpeg <ffmpeg-path>`
generates synthetic media and runs isolated, non-debuggable Lab tests, including
fresh-process persistence, browsing, media/subtitle timing and navigation. It
does not target physical devices or transfer configurations from another app.

Lab has a separate application ID and debug signing identity. Its tests and
generated media fixtures are not packaged in the regular release. Real provider
smoke tests are opt-in and require the developer's own private configuration;
no credentials are included. Do not enable them in public CI.
