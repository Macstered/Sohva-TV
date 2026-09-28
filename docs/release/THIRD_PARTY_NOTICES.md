# Sohva TV third-party notices

For the `0.2.0-beta.2` tester package and the matching public source. Sohva
TV's own source is licensed separately under `GPL-3.0-only`. This document
does not relicense third-party material; third-party copyrights, licences,
service terms, logos and trademarks stay in force.

## Open-source software

The app ships these libraries at run time. Build tools, test libraries and
libraries used only to compile are not part of the app and are not listed.

| Components | Licence | Upstream |
| --- | --- | --- |
| Kotlin standard library, kotlinx.coroutines 1.11.0 | Apache 2.0 | [Kotlin](https://github.com/JetBrains/kotlin), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| AndroidX: Compose runtime, UI and foundation (BOM 2026.09.00), Activity Compose 1.13.0, Core KTX 1.19.1, Lifecycle 2.11.0, Room 2.8.5, DataStore Preferences 1.2.1, WorkManager 2.11.2, ProfileInstaller 1.4.1, Tracing 2.0.3 | Apache 2.0 | [AndroidX](https://github.com/androidx/androidx) |
| AndroidX Media3 1.11.1: ExoPlayer, HLS, DASH, OkHttp data source, session, UI | Apache 2.0 | [Media3](https://github.com/androidx/media) |
| Guava, used by Media3 | Apache 2.0 | [Guava](https://github.com/google/guava) |
| OkHttp 5.5.0 and Okio 3.18.2 | Apache 2.0 | [OkHttp](https://github.com/square/okhttp), [Okio](https://github.com/square/okio) |
| Moshi 1.15.2 (streaming JSON reader) | Apache 2.0 | [Moshi](https://github.com/square/moshi) |
| Coil 3.6.3 and its OkHttp network module | Apache 2.0 | [Coil](https://github.com/coil-kt/coil) |
| ZXing core 3.5.4 (QR codes for phone setup) | Apache 2.0 | [ZXing](https://github.com/zxing/zxing) |
| Android desugared Java library 2.1.5 (`java.time` and other Java APIs on older Android) | GPL 2.0 with the Classpath Exception | [desugar_jdk_libs](https://github.com/google/desugar_jdk_libs) |

The complete Apache License 2.0 text is in
[`LICENSE-APACHE-2.0.txt`](LICENSE-APACHE-2.0.txt) and at
[apache.org](https://www.apache.org/licenses/LICENSE-2.0). The APK also keeps
the licence files its libraries include. Libraries that these components pull
in themselves are covered by their own licences. This summary is not a grant
of rights to upstream content.

Compared with beta 23, the app no longer includes `androidx.tv` TV Material,
Accompanist, AndroidSVG, kotlinx.serialization or kXML2. Programme guides are
read with the XML parser built into Android.

## Colour themes

Nord, Everforest and Kanagawa adapt upstream palettes for TV viewing. Their
attribution and full MIT licence terms ship inside the APK as
`theme-licenses.txt` (in the source at `app/src/main/assets/theme-licenses.txt`)
and are shown in **Settings > About > About, privacy and licences**.

- [Nord](https://github.com/nordtheme/nord), copyright 2016-present Sven Greb.
- [Everforest](https://github.com/sainnhe/everforest), copyright 2019 sainnhe.
- [Kanagawa](https://github.com/rebelot/kanagawa.nvim), copyright 2021 Tommaso
  Laurenzi.

The Original, Nordic Slate, Cozy Hearth and Cyber Plum themes are Sohva TV's
own.

## Optional data providers

- **TMDB:** This product uses the TMDB API but is not endorsed or certified by
  TMDB. TMDB is optional and uses your own key. The unmodified TMDB
  attribution logo appears in the app and is not offered under Sohva TV's GPL
  licence. See [TMDB](https://www.themoviedb.org).
- **TVmaze:** TVmaze data is used under CC BY-SA. Screens that show it name
  TVmaze as the source.
  [TVmaze licensing](https://www.tvmaze.com/api#licensing).
- **API-Sports:** Optional and used with your own key. Coverage, quotas and
  terms depend on your account. API access does not by itself grant rights to
  publish all sports data, league and team logos or trademarks.
  [API-Sports terms](https://api-sports.io/terms).
- **Trakt:** Optional. Sohva TV uses the Trakt API with your own Trakt account
  but is not endorsed or certified by Trakt. [Trakt](https://trakt.tv).

No IPTV playlists, provider credentials, sports-data feed or channel
subscription is included. Use only sources and data you are allowed to use.
Sohva TV claims no ownership of provider data, images or trademarks and does
not imply provider endorsement. Non-commercial use does not override provider
terms or third-party rights.

## Discover and third-party addons

Stremio, Nuvio, OpenSubtitles, AIOMetadata and other addon or service names
identify compatibility or services you set up yourself, not bundled media or
endorsement. No addon server code, provider configuration, artwork catalog or
account is distributed with Sohva TV. Providers keep their rights to their
metadata, images and services; you must follow those services' terms and
obtain any required permission.
