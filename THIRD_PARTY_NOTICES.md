# Sohva TV third-party notices

For the **0.1.0-beta.4** tester package and accompanying public source. Sohva
TV's original source is licensed separately under `GPL-3.0-only`. This document
does not relicense third-party material; third-party copyrights, licences,
service terms, logos, and trademarks remain in force.

## Open-source software

The release runtime dependency graph was captured for this beta. Principal
component families include:

| Components | Principal licence | Upstream |
| --- | --- | --- |
| AndroidX / Jetpack, Compose, TV Material, Media3, Room, DataStore, WorkManager | Apache 2.0 | [Android](https://source.android.com/license) |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache 2.0 | [Kotlin](https://github.com/JetBrains/kotlin) |
| JetBrains Compose / AndroidX multiplatform components and annotations | Apache 2.0 | [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) |
| OkHttp and Okio | Apache 2.0 | [OkHttp](https://github.com/square/okhttp), [Okio](https://github.com/square/okio) |
| Coil | Apache 2.0 | [Coil](https://github.com/coil-kt/coil) |
| Guava and its ancillary annotations | Apache 2.0 / component-specific notices | [Guava](https://github.com/google/guava) |
| Accompanist | Apache 2.0 | [Accompanist](https://github.com/google/accompanist) |
| JSpecify annotations | Apache 2.0 | [JSpecify](https://github.com/jspecify/jspecify) |
| AndroidSVG | Apache 2.0 | [AndroidSVG](https://github.com/BigBadaboom/androidsvg) |
| kXML2 | BSD-style | [kXML2](https://central.sonatype.com/artifact/net.sf.kxml/kxml2/2.3.0) |
| XmlPull API in kXML2 | Public domain | [kXML2 artifact](https://central.sonatype.com/artifact/net.sf.kxml/kxml2/2.3.0) |

The complete Apache License 2.0 text is distributed as
[`LICENSE-APACHE-2.0.txt`](LICENSE-APACHE-2.0.txt), and the kXML2 permission
notice as [`LICENSE-KXML2.txt`](LICENSE-KXML2.txt). The APK also retains its
embedded upstream licence files. This summary is not a grant of rights to
upstream content.

## Optional data providers

- **TMDB:** This product uses the TMDB API but is not endorsed or certified by
  TMDB. TMDB is optional and uses your own credential. The unmodified TMDB
  attribution logo appears in the app and is not offered under Sohva TV's GPL
  licence. [TMDB FAQ](https://developer.themoviedb.org/docs/faq).
- **TVmaze:** TVmaze data is used under CC BY-SA; enriched information cards
  identify and link to the source. [TVmaze licensing](https://www.tvmaze.com/api#licensing).
- **API-Sports:** Optional and user-keyed. Coverage, quotas and terms depend
  on your account. API access does not itself grant rights to publish all
  supplied sports data, league/team logos or trademarks.
  [API-Sports terms](https://api-sports.io/terms).

No IPTV playlists, provider credentials, sports-data feed or channel subscription
is included. Use only sources and data you are authorized to access. Sohva TV
claims no ownership of provider data, images or trademarks and does not imply
provider endorsement. Non-commercial use does not override provider terms or
third-party rights.

## ZXing core

QR code generation for the phone setup page. Apache License 2.0.
<https://github.com/zxing/zxing>
