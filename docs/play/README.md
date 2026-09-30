# Google Play submission kit

Prepared 28 September 2026 for Sohva TV `0.2.0-beta.1`, brought up to date on 29 September 2026 for `0.2.0-beta.4` (build 114). Play application ID `fi.luontra.sohvatv` (the GitHub build keeps `com.streammate.tv`),
free, no ads, no in-app purchases, Android TV only. Everything the Play Console asks for is here,
in the order the console asks for it. Policy findings are from Google's public pages read on
28 September 2026 (sources at the end); they are not legal advice.

What the owner does in the console is marked **Owner**. Nothing here has been uploaded.

## 1. The Play build

Play forbids an app from updating itself outside Play (Device and Network Abuse policy), so the
Play build is a separate build type, `play`: the release code, signing and start-up profiles,
with the in-app updater off and `REQUEST_INSTALL_PACKAGES` removed (decision "Play build").
Settings › About says "Updates come from Google Play." The GitHub build is unchanged.

```
./gradlew :app:bundlePlay     # app/build/outputs/bundle/play/app-play.aab (what Play takes)
./gradlew :app:assemblePlay   # the same code as an APK, for testing on a TV
```

Checked on build 114's code (the `play` APK, 29 September 2026):

- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`,
  `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`. No `REQUEST_INSTALL_PACKAGES`, no
  `USE_EXACT_ALARM`, no `QUERY_ALL_PACKAGES`.
- One foreground service type: `mediaPlayback` (the player).
- TV manifest: `LEANBACK_LAUNCHER`, `android:banner`, `leanback` required (TV only),
  touchscreen not required.
- Native libraries (two androidx helpers) for arm64-v8a, armeabi-v7a, x86 and x86_64, all
  16 KB-page aligned (zip and ELF segments), as TV apps need from 1 August 2026.
- targetSdk 36 (TV needs 34+), minSdk 23.

**Version codes.** GitHub and Play share one sequence; every upload is new and higher. Build 114
(0.2.0-beta.4) is on GitHub. The first Play upload should be **115** (`-Psohva.versionCode` is for tests only: set
`versionCode` in `app/build.gradle.kts` and commit, as for every release).

## 2. Application ID and signing

`com.streammate.tv` is taken on Google Play, so the Play build has its own application ID,
**`fi.luontra.sohvatv`** (decision "Play application ID"). The GitHub build keeps
`com.streammate.tv`: its installs and the in-app updater depend on it. The two are separate apps:
Play cannot update a GitHub install, both can be installed side by side, and a viewer moving to the
Play version brings their data with **Settings › Backup** (save in one, restore in the other).

Because Play never updates the GitHub installs, it does not need the existing key.

**Owner**, at the first upload (Setup › App signing):

1. Let Google create and keep the app signing key (the default, "Use Google-generated key").
   The existing key in `.local/streammate-signing/` stays for GitHub only and never goes to Google.
2. Create an **upload key** (a new keystore, for example with Android Studio's Generate Signed
   Bundle, or `keytool -genkeypair -keystore play-upload.jks -alias upload -keyalg RSA -keysize 4096
   -validity 10000`) and keep it in `.local/play-upload/` with a `keystore.properties` beside it
   (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`, as for the release key). `bundlePlay`
   then signs with it; without that file it signs with the release key, which is fine only for
   local testing. Back the upload key up: Google can reset a lost upload key, but it takes days.

The application ID is fixed by the first upload and cannot be changed afterwards.

## 3. Account and app setup

- The organization account needs a verified D-U-N-S number, legal name and address, website, phone
  and email. Play shows the legal name, address, developer email and phone publicly.
- The 12-tester, 14-day closed-test rule applies only to personal accounts created after
  13 November 2023. Organization accounts can publish without it. Still recommended: an
  **internal testing** track first (up to 100 testers, no review wait), then production.
- **Owner:** Create app › name "Sohva TV", default language English (United States) or Finnish
  (the listing below has both), App, Free.
- **Owner:** Advanced settings › Form factors › add **Android TV** (this is what gets the TV
  review). Mention "Android TV" in the description (done below).

## 4. Store listing

Graphics are in `docs/play/graphics/`, made from the app's own logo art by
`tools/play_graphics.py`:

| Asset | File | Play's rule |
|---|---|---|
| App icon | `icon-512.png` | 512×512, 32-bit PNG |
| Feature graphic | `feature-graphic-1024x500.png` | 1024×500, 24-bit PNG |
| TV banner | `tv-banner-1280x720.png` | 1280×720, 24-bit PNG |
| TV screenshots | `docs/play/screenshots/1-home.png` … `5-settings-home.png` (Home, guide, movies, a film, Settings › Home) | 1920×1080, 24-bit PNG, at least one TV screenshot, no device frames |

The screenshots come from `PlayScreenshotsTest` on the stand-in emulator: fictional channels,
programmes and films, with posters drawn by `tools/play_screenshot_art.py` (abstract shapes and the
film's fictional title). To take them again:

```
python tools/play_screenshot_art.py
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sohva.tv.app.PlayScreenshotsTest
adb -s emulator-5570 pull /data/local/tmp/play-1.png   # … play-5.png, then save as 24-bit PNG
```

Screenshots must show no real channel logos, film posters, team crests, or TMDB, Trakt,
API-Sports or Stremio logos: use the review playlist (§6) or fictional data only.

### English

**App name** (30): `Sohva TV`

**Short description** (80): `IPTV player for Android TV: your own playlists, guide, films, series and sport.`

**Full description:**

```
Sohva TV is a TV player for Android TV and Google TV. Add the IPTV playlists and programme guides you already have, and watch them with a remote-friendly guide, films and series libraries, catch-up and reminders.

Sohva TV provides no channels, films or series. You need your own M3U or Xtream Codes playlist from a provider you are allowed to use, and an XMLTV guide if your provider has one.

Live TV and guide
• A fast programme guide for large playlists, with groups, favourites, channel numbers and search
• Catch-up playback where your provider offers it
• Programme reminders
• Rename, hide, reorder and renumber channels; your own channel lists

Films and series
• Poster walls with genres, search and continue watching
• Resume where you left off, on every copy of the same film

Also
• Profiles with a parental PIN and allowed groups
• Sohva Sport: today's matches and the channels showing them, with your own API-Sports key (optional)
• Film and series details from TMDB with your own key (optional) or TVmaze
• Home your way: choose its rows and their order, and add Trakt charts or public Trakt lists (optional)
• Trakt sync of watch history and progress (optional)
• Discover: add your own Stremio-compatible addons, for example for subtitles (optional; no addons are included)
• Set up playlists from your phone with a QR code
• Encrypted backups; seven themes; English, Finnish, Swedish, German, Spanish, Italian and Portuguese

Privacy
No account, no ads, no analytics. Your playlists, passwords and viewing history stay on your TV, encrypted.

Sohva TV is free and open source (GPL-3.0). It is not affiliated with or endorsed by TMDB, TVmaze, Trakt, API-Sports or Stremio. This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.
```

**Category:** Video Players & Editors. **Tags:** IPTV player, TV guide, video player.
**Contact:** the organization's support email (the app's About uses hello@luontra.fi).
**Privacy policy URL:** `https://github.com/Macstered/Sohva-TV/blob/main/docs/release/PRIVACY.md`
(public, not a PDF; it now says the Play version is updated by Play and never contacts GitHub).

### Suomi

**Sovelluksen nimi:** `Sohva TV`

**Lyhyt kuvaus** (80): `IPTV-soitin Android TV:lle: omat soittolistat, opas, elokuvat, sarjat, urheilu.`

**Koko kuvaus:**

```
Sohva TV on TV-soitin Android TV:lle ja Google TV:lle. Lisää sinulla jo olevat IPTV-soittolistat ja ohjelmaoppaat, ja katso niitä kaukosäätimellä helposti käytettävän oppaan, elokuva- ja sarjakirjastojen, jälkikatselun ja muistutusten avulla.

Sohva TV ei tarjoa kanavia, elokuvia eikä sarjoja. Tarvitset oman M3U- tai Xtream Codes -soittolistan palveluntarjoajalta, jonka palvelua sinulla on oikeus käyttää, sekä XMLTV-oppaan, jos palveluntarjoajallasi on sellainen.

Suorat lähetykset ja opas
• Nopea ohjelmaopas myös suurille soittolistoille: ryhmät, suosikit, kanavanumerot ja haku
• Jälkikatselu, jos palveluntarjoajasi tukee sitä
• Ohjelmamuistutukset
• Kanavien uudelleennimeäminen, piilotus, järjestys ja numerointi; omat kanavalistat

Elokuvat ja sarjat
• Julistenäkymät, lajityypit, haku ja jatka katselua
• Katselu jatkuu kohdasta, johon jäit, saman elokuvan jokaisessa versiossa

Lisäksi
• Profiilit, lapsilukon PIN ja sallitut ryhmät
• Sohva Sport: päivän ottelut ja niitä näyttävät kanavat omalla API-Sports-avaimellasi (valinnainen)
• Elokuvien ja sarjojen tiedot TMDB:stä omalla avaimellasi (valinnainen) tai TVmazesta
• Etusivu omaan makuun: valitse rivit ja niiden järjestys, ja lisää Traktin listauksia tai julkisia Trakt-listoja (valinnainen)
• Katseluhistorian ja -kohtien Trakt-synkronointi (valinnainen)
• Tutustu: lisää omia Stremio-yhteensopivia lisäosia, esimerkiksi tekstityksiä varten (valinnainen; lisäosia ei ole mukana)
• Soittolistojen lisäys puhelimella QR-koodin avulla
• Salatut varmuuskopiot; seitsemän teemaa; suomi, englanti, ruotsi, saksa, espanja, italia ja portugali

Yksityisyys
Ei tiliä, ei mainoksia, ei analytiikkaa. Soittolistasi, salasanasi ja katseluhistoriasi pysyvät salattuina televisiossasi.

Sohva TV on ilmainen ja avointa lähdekoodia (GPL-3.0). Se ei ole TMDB:n, TVmazen, Traktin, API-Sportsin eikä Stremion tekemä tai hyväksymä. This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.
```

## 5. App content (Policy › App content)

**Privacy policy:** the URL above.

**Ads:** No, the app contains no ads.

**App access:** "All or some functionality is restricted" → instructions (§6).

**Content rating (IARC):** answer the questionnaire honestly: the app is a media player that plays
content the user supplies and has unrestricted internet access; it contains no content of its own,
no user-to-user communication, no purchases and no location sharing. Accept the rating it gives
(often 12+/Teen for players).

**Target audience:** 18 and over (keeps the app outside the Families policy). Not appealing to
children: the listing has no child-oriented imagery.

**News app:** No. **Government app:** No. **Financial features:** None. **Health:** No.
**COVID-19 contact tracing:** No.

**Foreground service declaration** (required for targetSdk 34+):
- Type: `mediaPlayback`. Use case: "Media playback".
- Description: "Plays the live TV channel, film or episode the viewer chose, so that playback
  continues while the player is on screen or in picture-in-picture."
- Impact if deferred or interrupted: "The video the viewer is watching stops."
- **Owner:** a short screen recording (YouTube unlisted or Drive link) of: open Live TV, choose a
  channel from the review playlist, the video plays.

**Exact alarms:** the app uses `SCHEDULE_EXACT_ALARM` (granted by the user) for programme
reminders; no declaration. It does not use `USE_EXACT_ALARM`, which Play reserves for alarm-clock
and calendar apps.

### Data safety

Play counts data as collected when it leaves the device for any server, but exempts transfers the
user starts and expects. Sohva TV has no server of its own; it only connects to services the
viewer configures. The conservative answer, which cannot be called incomplete:

- **Does your app collect or share any of the required user data types?** Yes.
- **Is all of the user data collected by your app encrypted in transit?** No. The viewer's own
  IPTV sources may use HTTP; everything else is HTTPS.
- **Do you provide a way for users to request that their data is deleted?** Yes: everything is on
  the TV; clearing the app's data or removing a profile deletes it (and the privacy policy says how
  to ask about anything else).
- **Data types:**
  - *App activity › Other actions* (watch history and progress sent to Trakt, only when the viewer
    connects Trakt): collected, not shared, optional, purpose "App functionality". Trakt charts and
    public lists on Home are read without an account and send nothing about the viewer; subtitle
    addons get only a title's IMDb id (and season and episode) for library titles.
  - *Personal info › Other info* (playlist login sent to the viewer's own IPTV provider; the
    viewer's TMDB/API-Sports keys sent to those services): collected, not shared, optional,
    purpose "App functionality".
  - Nothing is collected for analytics, advertising, fraud prevention or personalisation; no
    location, contacts, photos, audio, financial, health, messages or device ids.
- **Data shared with third parties:** None (a user-started transfer to a service the user chose is
  not "sharing" in Play's terms).

If Google's reviewer disagrees, the stricter answer is to also declare *App info and performance ›
Diagnostics* as not collected (the diagnostics file is saved by the viewer, never sent).

## 6. Instructions for the reviewer (App access)

The reviewer needs a playlist that contains nothing copyrighted.
`docs/play/review/sohva-review.m3u` has two Blender Foundation open films (CC BY 3.0) and Apple's
HLS test stream. **Owner:** host it over HTTPS. Once this directory is on the public repository,
the address is `https://raw.githubusercontent.com/Macstered/Sohva-TV/main/docs/play/review/sohva-review.m3u`.

Text for the console:

```
Sohva TV plays the viewer's own IPTV playlists and ships no content. To test it:
1. Open the app. In the left rail choose Settings (gear icon) › Playlists › "+ Add M3U source".
2. Enter any name (for example "Review") and the playlist address
   https://raw.githubusercontent.com/Macstered/Sohva-TV/main/docs/play/review/sohva-review.m3u
   and save. No login is needed; the playlist loads at once.
3. Open Live TV from the left rail. The guide lists three channels: two Blender Foundation open
   films (CC BY 3.0) and Apple's HLS test stream. Press OK on one to play it.
Optional features (TMDB, API-Sports, Trakt, Discover addons) need the viewer's own keys or
accounts and are off by default. There is no sign-in and no parental PIN until the viewer sets one.
```

**Owner:** check the menu names against the build you upload before sending.

## 7. Publishing order

1. Build 115 as `play`, signed with the upload key; `./gradlew :app:bundlePlay`.
2. Internal testing track: upload the AAB, add yourself, install on the Shield from Play. It
   installs next to the GitHub version (a separate app); move your data with Settings › Backup.
3. Complete §4–§6, then Production (or Open testing first). TV review status appears under
   Pricing & distribution; Google gives no TV review timeline.
4. Keep the GitHub releases: the updater of GitHub installs keeps reading them.

## 8. Third-party services and trademarks

| Service | What the app does | Finding | Done / to do |
|---|---|---|---|
| TMDB | Viewer's own key; posters, backdrops, details | A free app without ads is non-commercial under TMDB's terms (commercial = fees, selling, revenue). Required: TMDB's logo, less prominent than the app's, and the notice "This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB." No caching over 6 months. | Notice now uses that exact sentence (About, legal screen, THIRD_PARTY_NOTICES, listing); the logo was already on the legal screen. Lookup answers expire in 30 days; the image cache is size-bound. Matches keep TMDB ids and image paths while the title exists (a link, not a copy): **owner to judge**. |
| API-Sports | Viewer's own key; fixtures, team and league crests | Accounts are individual; no reselling; no app registration. API-Sports claims no rights in crests and makes the app responsible for trademark use. | Never ship a key (done). No crests or league names in the store listing or screenshots. In-app crests next to fixtures are descriptive use; **legal check recommended** if a rights holder complains. |
| Trakt | The developer's own client id and secret; users sign in with their own accounts | No restriction on commercial use; branding rules (no Trakt logo in the app's icon or name; no implied endorsement); Trakt data may not be used in apps that promote piracy. Forum reports (not official docs) say API apps now need Trakt VIP (since August 2026), and free Trakt accounts may connect only one "community app" (since 22 July 2026). | **Owner:** check that the Trakt API app and its client id still work, and whether VIP is needed. The secret is injected at build time and can be read from any APK: rotate it if abused. |
| TVmaze | No key; series details | CC BY-SA 4.0: free for any use with credit and a link. | Credit now shows "tvmaze.com". |
| Stremio addons | Viewer adds addons by address; none included | The protocol and SDK are open (MIT); Stremio's name and logo are its trademarks. | Listing says "Stremio-compatible addons", "no addons are included", and "not affiliated with or endorsed by Stremio"; no Stremio logo anywhere. |
| OpenSubtitles | Only through an addon the viewer adds | The addon operator calls OpenSubtitles, not the app. | Nothing to do. Never call their API directly with per-user keys (their rules forbid it). |

**The biggest risk is the IPTV policy itself.** Play's Intellectual Property policy removes apps
that "encourage users to stream… copyrighted works" without permission, and a rights holder's
complaint has removed clean IPTV players before (Perfect Player, 2019). What keeps Sohva TV on the
right side: no preloaded playlists or addon addresses, no links to IPTV sellers, no "free TV"
wording, and screenshots with open or fictional content only. Keep a short appeal statement ready
(the app ships no content; the viewer supplies sources they are allowed to use; open source).

## 9. Still to do before submitting

1. Screenshots: done (five, §4). **Owner:** look them over; drop any you do not like.
2. **Owner:** create the upload key and let Google create the app signing key (§2).
3. **Owner:** Trakt API app status (§8).
4. **Owner:** host the review playlist (§6) and record the foreground-service video (§5).
5. Build 115 as `play` with the upload key.

The two problems seen while preparing this kit were checked again on 29 September 2026 and did not
come back: the guide shows a first playlist whether Live TV was opened before, during or after the
import (`FirstSourceTest`, three orders), and after the playlist address editor closes focus is on the
address field and Down stays in the form (`SettingsPlaylistsTest`, and by hand on the emulator with
the on-screen keyboard). Both tests now guard them.

## Sources (read 28 September 2026)

- Intellectual Property policy: https://support.google.com/googleplay/android-developer/answer/9888072
- Device and Network Abuse (self-updating): https://support.google.com/googleplay/android-developer/answer/9888379
- Sensitive permissions (REQUEST_INSTALL_PACKAGES, exact alarms): https://support.google.com/googleplay/android-developer/answer/12085295, https://support.google.com/googleplay/android-developer/answer/16558241
- Foreground service types: https://support.google.com/googleplay/android-developer/answer/13392821
- Store listing assets: https://support.google.com/googleplay/android-developer/answer/9866151
- TV quality and distribution: https://developer.android.com/docs/quality-guidelines/tv-app-quality, https://developer.android.com/training/tv/publishing/distribute
- 64-bit and 16 KB pages for TV: https://android-developers.googleblog.com/2025/08/64-bit-app-compatibility-for-google-tv-android-tv.html
- Target API: https://support.google.com/googleplay/android-developer/answer/11926878
- Play App Signing: https://support.google.com/googleplay/android-developer/answer/9842756
- Organization accounts and testing: https://support.google.com/googleplay/android-developer/answer/13628312, https://support.google.com/googleplay/android-developer/answer/14151465
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- TMDB: https://www.themoviedb.org/api-terms-of-use, https://developer.themoviedb.org/docs/faq
- API-Sports: https://api-sports.io/terms (read through search extracts; open it in a browser)
- Trakt: https://docs.trakt.tv/docs/create-an-app, https://forums.trakt.tv/t/an-update-to-community-app-connections/117898
- TVmaze: https://www.tvmaze.com/api
- Stremio: https://github.com/Stremio/stremio-addon-sdk, https://www.stremio.com/tos
