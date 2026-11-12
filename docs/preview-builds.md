# Private preview builds

Previews let the owner test accumulated fixes without publishing a new beta.
They use the existing application ID and signing key. Every frozen preview
reserves its version code, even if it is never installed or published.

The next unreserved distributed APK must use **139 or higher**. Builds 126 through
137 are reserved below; 138 is the public `0.2.0-beta.16`. The committed version metadata is beta.16, build 138;
private previews use Gradle version overrides.

| Date | Version | Android build | Source | Status | Change |
|---|---|---:|---|---|---|
| 2026-10-02 | `0.2.0-beta.15-subtitles.1` | 126 | `f2f396b` | Installed on Shield; viewer testing pending | Black drop shadow for text subtitles with Background: None. |
| 2026-10-02 | `0.2.0-beta.15-dictionary.1` | 127 | `8e69df4` | Installed on Shield; numeric-date issue found; acceptance pending | Reviewed sports identities, translated aliases, category guards and supporting competition context. |
| 2026-10-02 | `0.2.0-beta.15-dates.1` | 128 | `ac237f4` | Installed on Shield; viewer/Elisa acceptance pending | Fixture-compatible numeric dates in either order and refreshed automatic matching cache. |
| 2026-10-02 | `0.2.0-beta.15-home.1` | 129 | `ce2dbbf` | Installed on Shield and Elisa; Home restart works on Shield per owner; Elisa testing pending | Home Live TV and Continue Watching actions after background activity recreation. |
| 2026-10-08 | `0.2.0-beta.15-player.1` | 130 | `3b7b444` | Installed on Shield; viewer testing pending | Channel list highlight/scroll in one press, display mode kept while zapping, newest channel choice, stream-reply diagnostics and HLS retry for plain-address playlists. |
| 2026-10-08 | `0.2.0-beta.15-player.2` | 131 | `db28e90` | Frozen; not installed | Build 130 plus diagnostics on TVs without a file picker: Download folder and Send to phone. |
| 2026-10-11 | `0.2.0-beta.15-player.3` | 132 | `d569a3a` | Installed on Shield; Home correct per owner, info box and channel list not; superseded by 133 | Builds 130-131 plus now/next and Home's recent channels kept current while an import writes. |
| 2026-10-11 | `0.2.0-beta.15-player.4` | 133 | `d6a93cd` | Installed on Shield; superseded by 134 | Builds 130-132 plus the info box reading the new channel at once and channel list titles by rows. |
| 2026-11-01 | `0.2.0-beta.15-player.5` | 134 | `d8c70a5` | Installed on Shield; superseded by 135 | Builds 130-133 plus a live channel opening again when the provider closes a stalled stream, instead of freezing. |
| 2026-11-01 | `0.2.0-beta.15-player.6` | 135 | `414bb09` | Installed on Shield; superseded by 136 | Build 134 plus a small dark card instead of the red bar while playback reconnects. |
| 2026-11-03 | `0.2.0-beta.15-player.7` | 136 | `fd598ca` | Installed on Shield; viewer testing pending | Build 135 plus a live channel that stays buffering opening again by itself. |

The public beta.15 assets remain frozen. No tag, public push or GitHub release
is created for these previews. Shield testing of 127 found a numeric-date regression;
follow-up 128 is ready. Dictionary acceptance on Shield and Elisa remains pending.

## Subtitle preview, build 126

- [APK](../release/0.2.0-beta.15-subtitles.1/sohva-tv-0.2.0-beta.15-subtitles.1.apk)
- [Preview pack with both install profiles and the test checklist](../release/0.2.0-beta.15-subtitles.1/sohva-tv-0.2.0-beta.15-subtitles.1-preview-pack.zip)

APK SHA-256: `f2b128e43bfecca3cbf14c3a2b43f51fa9a87af4bc1cfe5ca45d7e3016222420`.
The local folder also contains checksums and a verification receipt. Do not overwrite it.

Release lint, R8, APK size and method-size gates pass. The package, signing
certificate, manifest, APK safety and matching profile audits pass. On the
1 GB API 30 TV emulator, this exact APK passes ART verification and launches
in 627 ms, remaining alive eight seconds later. Installing the APK with its
Android 9–11 profile succeeds with `reason=install-dm` and `status=speed-profile`.
The subtitle source already passed the native white-on-white regression,
265 player/model unit tests and player debug lint.

The first linked-checkout candidate failed the source revision audit and was
not frozen. The final standalone checkout records the exact source commit,
`f2f396baf9bdf82a174b95538946bc139142e609`, in the APK. The audit was retained.

Evidence: `harness-out/subtitle-preview-build.log`, `subtitle-preview-dex.log`,
`subtitle-preview-package.log` and `subtitle-preview-profile-install.log`.

Installed on the owner's Shield on 2 October 2026, replacing build 125 with
build 126 using the data-preserving update mode. The Android 9–11 install
profile was accepted: `arm64`, `status=speed-profile`, `reason=install-dm`.
The installed version was read back and confirmed. Viewer testing is pending.
Evidence: `harness-out/subtitle-preview-shield-install.log` and the private
installation receipt in `.local/subtitle-preview-shield-install.json`.

## Sports dictionary preview, build 127

- [APK](../release/0.2.0-beta.15-dictionary.1/sohva-tv-0.2.0-beta.15-dictionary.1.apk)
- [Preview pack with both install profiles, notices, measurements and device checklist](../release/0.2.0-beta.15-dictionary.1/sohva-tv-0.2.0-beta.15-dictionary.1-preview-pack.zip)

Source: `8e69df4c828debc18adae1c88f58c4215f6e847c`.
APK SHA-256: `42cc21e7ec7b88dbd869f831bad0486873d7d6fbf1e28a051f3ba2e209f3037e`.
The 6,320,570-byte signed APK contains the 81,920-byte reviewed dictionary,
revision `10f8a028cbee5c42addfac3d0a858fb4bbf669f741fb0196971f2482bbb12518`.
Its first slice has 43 identities and no asserted competition mappings/rosters.
The full synthetic dictionary is test-only. Pending research proposals are excluded.

All automated correctness gates pass: 764 unit tests, 162 desktop-tool tests,
migration, indexed queries, packaged aliases/categories, saved choices/cache
and gated focus checks. Five interleaved runs per mode on each 1 GB / 2 GB
emulator preserve 49 Available owner-scale results. Median dictionary CPU
cost is within +/-0.4% of the same-session baseline; raw ranges and the slow
outlier are retained in the [performance log](performance-log.md).

Fresh baseline/startup profiles are generated. Release lint/R8/size gates,
identity/signing/manifest/dictionary/safety/profile audits and source/secret
checks pass. This exact APK verifies under ART and launches in 407 ms,
remaining alive eight seconds later. Its Android 9-11 profile installs with
`status=speed-profile`, `reason=install-dm`. Both Android install profiles,
checksums and verification receipts are frozen together; do not overwrite them.
The preview also contains the subtitle drop shadow from build 126.

Evidence: `harness-out/dictionary-preview-release.log`,
`dictionary-preview-apk-audit.log`, `dictionary-preview-dex.log`,
`dictionary-preview-profile-install.log` and `dictionary-preview-package.log`.
The folder includes `verification.json`, `MEASUREMENTS.json`, `PROFILE-INSTALL.json`
and an English/Finnish `PREVIEW.md`. Later documentation commits do not change
the APK source commit recorded above.

Installed on the owner's Shield on 2 October 2026 after explicit instruction,
replacing build 126 with 127 using the data-preserving update mode. The exact
frozen APK and Android 9-11 profile hashes were checked before installation.
Read-back confirms version 0.2.0-beta.15-dictionary.1, build 127, and
`arm64`, `status=speed-profile`, `reason=install-dm`. Viewer testing is pending.
Receipt: `harness-out/dictionary-preview-shield-install.log` and the private
`.local/dictionary-preview-shield-install.json`. The frozen package's
verification file remains its packaging-time record; these later receipts
record the installation. No frozen artifact was changed.

Elisa installation awaits its own instruction. Hardware first-data, key/frame
and playback acceptance remains pending on both devices.
No public push, tag or GitHub release was made. Build 128 is frozen for the date follow-up.

## Numeric-date follow-up, build 128

- [APK](../release/0.2.0-beta.15-dates.1/sohva-tv-0.2.0-beta.15-dates.1.apk)
- [Preview pack with both matching install profiles, measurements and English/Finnish test notes](../release/0.2.0-beta.15-dates.1/sohva-tv-0.2.0-beta.15-dates.1-preview-pack.zip)

Source: `ac237f4b04124c20f578e746cc6a2817f12ec547`.
APK SHA-256: `1056fd2198c0ac401620c60b09581be2d40110dd9d9932bb66a501838f40fc80`.
The 6,320,570-byte signed APK retains the reviewed dictionary from 127 and
the subtitle shadow from 126. Numeric dates in either order can support a
fixture match without guessing the provider's date convention. Explicit
years/zones, including literal EDT, are respected; clocks only rank name
matches. Wrong dates/years, invalid dates, wrong opponents and single-team
listings remain guarded. Matching rules 5 rescan old automatic cache entries
and retain the viewer's saved Confirm/Reject choices.

All five photo fixtures, 337 affected unit tests and six Android provider/name
tests pass. The regressions were demonstrated failing first. Five full
owner-scale runs on the 1 GB emulator preserve 49 Available results and remain
under the 16 MiB scan-heap bound; the added date-work cost and raw samples
are recorded in [performance-log.md](performance-log.md).

Release lint, module boundaries, APK/method-size gates, source/secret audits,
identity/signature/manifest/dictionary audits and both install-profile checks
pass. The exact APK passes ART verification with no rejection, launches in
596 ms and stays alive eight seconds later. This is a launch smoke check,
not a compiled first-frame budget measurement. Its Android 9–11 profile is
accepted with `status=speed-profile`, `reason=install-dm` on the emulator.

Evidence: `harness-out/numeric-date-preview-release.log`,
`numeric-date-preview-apk-audit.log`, `numeric-date-preview-dex.log`,
`numeric-date-preview-profile-install.log` and `numeric-date-preview-package.log`.
Checksums, notices, measurement/profile receipts and `verification.json` are
frozen together. Installed on Shield on 2 October 2026 after the owner's
explicit instruction, updating 127 to 128 with app data preserved. The exact
frozen APK and Android 9–11 profile hashes were checked before installation.
Read-back confirms build 128, version 0.2.0-beta.15-dates.1 and
`arm64`, `status=speed-profile`, `reason=install-dm`.

The later installation receipt is `harness-out/numeric-date-preview-shield-install.log`
and the private `.local/numeric-date-preview-shield-install.json`. The frozen
package remains its packaging-time snapshot; no frozen artifact was changed.
Shield viewer acceptance is pending. Elisa installation awaits its own
instruction. No public push, tag or release was made; the next unreserved
distributed APK needs 129+.

## Home navigation preview, build 129

Reserved on 2 October 2026 after the owner's Shield installation request.
Private version: `0.2.0-beta.15-home.1`. Includes the Home activity-recreation
fix from `69d8e08`, plus the dictionary, numeric-date and subtitle changes in
build 128. All earlier artifacts remain frozen.

- [APK](../release/0.2.0-beta.15-home.1/sohva-tv-0.2.0-beta.15-home.1.apk)
- [Preview pack with both install profiles and English/Finnish test notes](../release/0.2.0-beta.15-home.1/sohva-tv-0.2.0-beta.15-home.1-preview-pack.zip)

Source: `ce2dbbf1186f4f1ea17add36802ee3eebbcd1c4c`.
APK SHA-256: `c95bb996f9e64c0a71c07112b6adb00a8237640b0f43113c316228730f45fe8c`.
The signed APK is 6,320,570 bytes. The prior 32 affected unit tests and 12 native
checks pass, including focus/Back, repeated launch and French language changes.
The final 1 GB Home recreation CPU median is 148 ms versus 155 ms before, with
overlapping ranges; this does not establish physical-box frame/key acceptance.

Release lint/R8/module/size gates, source and tree/history secret audits,
identity/signature/manifest/dictionary/safety audits and both matching install
profiles pass. The exact APK verifies under ART with 40 dex2oat lines and no
rejection, launches in 631 ms and remains alive eight seconds later. Its Android
9–11 install profile is accepted on the emulator with speed-profile/install-dm.
These are release verification and launch smoke checks, not a first-frame budget.

Installed on Shield on 2 October 2026 at 14:22 UTC after explicit instruction,
updating 128 to 129 with app data preserved. Exact frozen APK/profile hashes
were checked before installation. Read-back confirms the version/code and
`arm64`, `status=speed-profile`, `reason=install-dm`.

Receipts: `harness-out/home-preview-release.log`, `home-preview-apk-audit.log`,
`home-preview-dex.log`, `home-preview-profile-install.log`,
`home-preview-package.log`, `home-preview-shield-install.log` and the private
`.local/home-preview-shield-install.json`. The frozen verification JSON remains
its packaging-time snapshot; installation is recorded separately here.
The owner reports the Home background/restart scenario working on Shield.
Installed the same frozen preview on Elisa on 2 October 2026 at 14:31 UTC,
after the owner's explicit instruction. Device identity confirms ZTE B866V2F01,
Android 12/API 31. Data-preserving replacement updates build 112 to 129;
version read-back confirms 0.2.0-beta.15-home.1 and
`arm`, `status=speed-profile`, `reason=install-dm`, using the matching Android 12+
profile. Receipt: `harness-out/home-preview-elisa-install.log` and the private
`.local/home-preview-elisa-install.json`. No frozen artifact was changed.
Elisa viewer testing and broader sports/performance acceptance remain pending.
No public push, tag or release was made.
The next unreserved distributed APK needs 130+.

## Player preview, build 130

Reserved on 8 October 2026 at the owner's request to prepare a preview for the Shield.
Private version: `0.2.0-beta.15-player.1`. Includes, since build 129: the newest
channel choice through slow reads and entry focus (`9042423`), the once-per-build
update notice (`baa7ee4`), the channel list highlight and scroll in one key press
(`6d0a4df`), the display mode kept during in-player channel changes (`47c8590`) and
the stream-reply diagnostics with the plain-address playlist retry (`3b7b444`).
All earlier artifacts remain frozen.

- [APK](../release/0.2.0-beta.15-player.1/sohva-tv-0.2.0-beta.15-player.1.apk)
- [Preview pack with both install profiles and English/Finnish test notes](../release/0.2.0-beta.15-player.1/sohva-tv-0.2.0-beta.15-player.1-preview-pack.zip)

Source: `3b7b444` (full hash in the frozen `verification.json`).
APK SHA-256: `20085e1f644e47a4e6186385238b9f9b1e6d22ce11edd050b58c63cd71366d40`.
The signed APK is 6,337,600 bytes. Release build, lint, gates and module checks pass
(`harness-out/player-preview-release.log`). The exact APK verifies under ART with 39
dex2oat lines and no rejection, launches in 543 ms and is alive eight seconds later
(`player-preview-dex.log`). Its Android 9-11 install profile is accepted on the
emulator with speed-profile/install-dm (`player-preview-profile-install.log`).
Public-source audit (1,223 files, 0 findings) and gitleaks tree and history scans
(268 commits) find nothing (`player-preview-source-audit.log`,
`player-preview-gitleaks.log`). Emulator suites for each change are listed in
docs/performance-log.md (6, 7 and 8 October). Physical-box acceptance is pending,
including whether the display-mode change fixes the Chromecast report.

Installed on Shield on 7 October 2026 at 21:43 UTC (00:43 Helsinki) after explicit instruction,
updating 129 to 130 with app data preserved. Exact frozen APK/profile hashes were
checked before installation. Read-back confirms 0.2.0-beta.15-player.1, build 130, and
`arm64`, `status=speed-profile`, `reason=install-dm`. Receipts:
`harness-out/player-preview-shield-install.log` and the private
`.local/player-preview-shield-install.json`. No other household device was touched.
No public push, tag or release.
Build 131 follows below.

## Player preview 2, build 131

Reserved on 8 October 2026 at the owner's request, so the Chromecast user can send
diagnostics. Private version: `0.2.0-beta.15-player.2`. Build 130 plus `db28e90`:
on a TV without a document picker Save diagnostics writes to the Download folder, and
Send to phone hands the redacted file to a phone through the phone page.

- [APK](../release/0.2.0-beta.15-player.2/sohva-tv-0.2.0-beta.15-player.2.apk)
- [Preview pack with both install profiles and English/Finnish test notes](../release/0.2.0-beta.15-player.2/sohva-tv-0.2.0-beta.15-player.2-preview-pack.zip)

Source: `db28e90` (full hash in the frozen `verification.json`).
APK SHA-256: `ca831047a74d21cda28f113bc90151ecfd0052d91213a9e29333d8bc5292b205`.
The signed APK is 6,345,042 bytes. Release build, lint, gates and module checks pass
(`harness-out/player2-preview-release.log`). The exact APK verifies under ART with 38
dex2oat lines and no rejection, launches in 310 ms and is alive eight seconds later
(`player2-preview-dex.log`). Its Android 9-11 install profile is accepted on the
emulator with speed-profile/install-dm (`player2-preview-profile-install.log`).
Public-source audit (1,224 files, 0 findings) and gitleaks tree and history scans
(274 commits) find nothing. The Chromecast (Android 14) needs the api31 profile.

Not installed on any device. Sharing it with the affected user is the owner's decision.
No public push, tag or release. The next unreserved distributed APK needs 132+.

## Player preview 3, build 132

Reserved on 11 October 2026 at the owner's request. Private version:
`0.2.0-beta.15-player.3`. Builds 130 and 131 plus `d569a3a`: the player's info box
and channel list read their source without waiting for the database's write
connection, and a channel just watched leads Home's row from memory until its row is
written.

- [APK](../release/0.2.0-beta.15-player.3/sohva-tv-0.2.0-beta.15-player.3.apk)
- [Preview pack with both install profiles and English/Finnish test notes](../release/0.2.0-beta.15-player.3/sohva-tv-0.2.0-beta.15-player.3-preview-pack.zip)

Source: `d569a3a` (full hash in the frozen `verification.json`).
APK SHA-256: `999fe069002426e8e56005cd1a4772929f10ac425df211cced8163c50a78698e`.
The signed APK is 6,345,053 bytes. Release build, lint, gates and module checks pass
(`harness-out/player3-preview-release.log`). The exact APK verifies under ART with 40
dex2oat lines and no rejection, launches in 756 ms and is alive eight seconds later
(`player3-preview-dex.log`). Its Android 9-11 install profile is accepted on the
emulator with speed-profile/install-dm (`player3-preview-profile-install.log`).
Public-source audit (1,227 files, 0 findings) and gitleaks tree and history scans
(277 commits) find nothing.

Installed on Shield on 2026-10-11 at 17:43 UTC after explicit instruction, updating
130 to 132 with app data preserved. Exact frozen APK/profile hashes were checked before
installation. Read-back confirms 0.2.0-beta.15-player.3, build 132, and `arm64`,
`status=speed-profile`, `reason=install-dm`. Receipts:
`harness-out/player3-preview-shield-install.log` and the private
`.local/player3-preview-shield-install.json`. No other household device was touched.
Sharing it with the Chromecast user is the owner's decision.
No public push, tag or release. The next unreserved distributed APK needs 133+.

## Player preview 4, build 133

Reserved on 11 October 2026 at the owner's request, after build 132 put Home right
but not the info box and channel list. Private version: `0.2.0-beta.15-player.4`.
Builds 130 to 132 plus `d6a93cd`: a channel change restarts the info box's now/next
reader and late results for another channel are dropped; the channel list reads
programme titles for the rows around the highlight.

- [APK](../release/0.2.0-beta.15-player.4/sohva-tv-0.2.0-beta.15-player.4.apk)
- [Preview pack with both install profiles and English/Finnish test notes](../release/0.2.0-beta.15-player.4/sohva-tv-0.2.0-beta.15-player.4-preview-pack.zip)

Source: `d6a93cd` (full hash in the frozen `verification.json`).
APK SHA-256: `03b6c3697cbb123e8e8f98e0d1c1acfbded7df856b4e26b78ea4b88d18f2e3fc`.
The signed APK is 6,345,048 bytes. Release build, lint, gates and module checks pass
(`harness-out/player4-preview-release.log`). The exact APK verifies under ART with 39
dex2oat lines and no rejection, launches in 663 ms and is alive eight seconds later
(`player4-preview-dex.log`). Its Android 9-11 install profile is accepted on the
emulator with speed-profile/install-dm (`player4-preview-profile-install.log`).
Public-source audit (1,228 files, 0 findings) and gitleaks tree and history scans
(279 commits) find nothing.

Installed on Shield on 2026-10-11 at 18:49 UTC after explicit instruction, updating
132 to 133 with app data preserved. Exact frozen APK/profile hashes were checked before
installation. Read-back confirms 0.2.0-beta.15-player.4, build 133, and `arm64`,
`status=speed-profile`, `reason=install-dm`. Receipts:
`harness-out/player4-preview-shield-install.log` and the private
`.local/player4-preview-shield-install.json`. No other household device was touched.
No public push, tag or release. The next unreserved distributed APK needs 134+.

## Player preview 5, build 134

Built on 1 November 2026 at the owner's request; the first preview from the Linux build machine.
Private version: `0.2.0-beta.15-player.5`. Builds 130 to 133 plus `7aef661`: a live channel whose
stream ends (the provider closed a stalled stream) counts as a dropped connection and opens again
instead of freezing on the last frame, and a pending reconnect no longer opens the stream while
the app is in the background. `bde09e3` and `d8c70a5` change only the build machine setup and
the pre-push tools.

- [APK](../release/0.2.0-beta.15-player.5/sohva-tv-0.2.0-beta.15-player.5.apk)
- [Preview pack with both install profiles and English/Finnish test notes](../release/0.2.0-beta.15-player.5/sohva-tv-0.2.0-beta.15-player.5-preview-pack.zip)

Source: `d8c70a5` (full hash in the frozen `verification.json`).
APK SHA-256: `136fcb5cbb22843357fc957f80a724e03eb164eec5623dde8d1f6fa1b95d7fc5`.
The signed APK is 6,346,047 bytes. Release build, lint, gates and module checks pass
(`harness-out/player5-preview-release.log`). The exact APK verifies under ART with 32
dex2oat lines and no rejection, launches in 563 ms and is alive eight seconds later
(`player5-preview-dex.log`). Its Android 9-11 install profile is accepted on the
emulator with speed-profile/install-dm (`player5-preview-profile-install.log`).
The full pre-push check passes on the same commit; public-source audit (1,230 files,
0 findings) and the gitleaks history scan (283 commits) find nothing.

Installed on Shield on 2026-11-01 at 17:29 UTC after the owner's go ("Yes, you can
install it"), updating 133 to 134 with app data preserved, from the Linux build machine over
network adb. Exact frozen APK/profile hashes were checked before installation. Read-back
confirms 0.2.0-beta.15-player.5, build 134, and `arm64`, `status=speed-profile`,
`reason=install-dm`. Receipts: `harness-out/player5-preview-shield-install.log` and the
private `.local/player5-preview-shield-install.json`. No other household device was touched.
No public push, tag or release. The next unreserved distributed APK needs 135+.

## Player preview 6, build 135

Built on 1 November 2026 at the owner's request ("Build and install"). Private version:
`0.2.0-beta.15-player.6`. Build 134 plus `414bb09`: the error banner over playback is a small dark
card instead of a full-width red bar. While the app reconnects by itself it is one line; the
detail and the Reconnect button appear only once the automatic attempts stop. Discover's "Playback
stopped" banner uses the same card.

- [APK](../release/0.2.0-beta.15-player.6/sohva-tv-0.2.0-beta.15-player.6.apk)
- [Preview pack with both install profiles and English/Finnish test notes](../release/0.2.0-beta.15-player.6/sohva-tv-0.2.0-beta.15-player.6-preview-pack.zip)

Source: `414bb09` (full hash in the frozen `verification.json`).
APK SHA-256: `c0d5d5cf85596a4370178580e2dc2e1a2894736ad636156b3a82a9fea3bf417d`.
The signed APK is 6,346,037 bytes. Release build, lint, gates and module checks pass
(`harness-out/player6-preview-release.log`). The exact APK verifies under ART with 38
dex2oat lines and no rejection, launches in 302 ms and is alive eight seconds later
(`player6-preview-dex.log`). Its Android 9-11 install profile is accepted on the
emulator with speed-profile/install-dm (`player6-preview-profile-install.log`).
The full pre-push check passes on the same commit, including the public-source audit and the
gitleaks history scan. The player suites (live, stream end, stream replies, HLS, channel switch)
and Discover's retry test pass on the emulator.

Installed on Shield on 2026-11-01 at 19:36 UTC after the owner's go ("Build and install"),
updating 134 to 135 with app data preserved, over network adb. Exact frozen APK/profile hashes
were checked before installation. Read-back confirms 0.2.0-beta.15-player.6, build 135, and
`arm64`, `status=speed-profile`, `reason=install-dm`. Receipts:
`harness-out/player6-preview-shield-install.log` and the private
`.local/player6-preview-shield-install.json`. No other household device was touched.
No public push, tag or release. The next unreserved distributed APK needs 136+.

## Player preview 7, build 136

Built on 3 November 2026 at the owner's request ("Build and install") after build 135 still froze
and then kept buffering until a channel change. Private version: `0.2.0-beta.15-player.7`. Build
135 plus `fd598ca`: a live channel still buffering after the buffer profile's rebuffer amount plus
10 s (15 s by default) counts as a dropped connection and opens again from the live edge.

- [APK](../release/0.2.0-beta.15-player.7/sohva-tv-0.2.0-beta.15-player.7.apk)
- [Preview pack with both install profiles and English/Finnish test notes](../release/0.2.0-beta.15-player.7/sohva-tv-0.2.0-beta.15-player.7-preview-pack.zip)

Source: `fd598ca` (full hash in the frozen `verification.json`).
APK SHA-256: `f8b3bf07fb965dbaf198586f09e41df91b371b72fcf1d4a0b5d88cb4c4dcc370`.
The signed APK is 6,346,042 bytes. Release build, lint, gates and module checks pass
(`harness-out/player7-preview-release.log`). The exact APK verifies under ART with 37
dex2oat lines and no rejection, launches in 315 ms and is alive eight seconds later
(`player7-preview-dex.log`). Its Android 9-11 install profile is accepted on the
emulator with speed-profile/install-dm (`player7-preview-profile-install.log`).
The full pre-push check passes on the same commit, including the public-source audit and the
gitleaks history scan. `PlayerStreamStallTest` failed before the fix and passes with it; the
player, catch-up, library and Discover playback suites pass on the emulator.

Installed on Shield on 2026-11-03 at 21:00 UTC after the owner's go ("Build and install"),
updating 135 to 136 with app data preserved, over network adb (reconnected for this install).
Exact frozen APK/profile hashes were checked before installation. Read-back confirms
0.2.0-beta.15-player.7, build 136, and `arm64`, `status=speed-profile`, `reason=install-dm`.
Receipts: `harness-out/player7-preview-shield-install.log` and the private
`.local/player7-preview-shield-install.json`. No other household device was touched.
No public push, tag or release. The next unreserved distributed APK needs 137+.

## Review preview 1, build 137

Built on 11 November 2026 at the owner's request ("Push improve/review-fixes then install on the
Shield"; build 137 chosen when asked). Private version: `0.2.0-beta.15-review.1`. Build 136 plus
the 35 steps of [improvement-plan.md](improvement-plan.md) on branch `improve/review-fixes`.

- [APK](../release/0.2.0-beta.15-review.1/sohva-tv-0.2.0-beta.15-review.1.apk)
- [Preview pack with both install profiles and English/Finnish test notes](../release/0.2.0-beta.15-review.1/sohva-tv-0.2.0-beta.15-review.1-preview-pack.zip)

Source: `9f3f194` (full hash in the frozen `verification.json`).
APK SHA-256: `4192c97c4cb2d33afd2e4ea408a50a6ff19525d3edb5e37dd7089691daefc097`.
The signed APK is 6,367,488 bytes. Release build, lint, gates and module checks pass
(`harness-out/review1-preview-release.log`). The exact APK verifies under ART with 39
dex2oat lines and no rejection, launches in 279 ms and is alive eight seconds later
(`review1-preview-dex.log`). Its Android 9-11 install profile is accepted on the
emulator with speed-profile/install-dm as an update of 136 with data kept
(`review1-preview-profile-install.log`). The full pre-push check passed on the last code
commit (`a728a8f`); the two later commits change documents only. The APK identity and safety
audits pass; the release-document audit does not apply to a private preview name.

Installed on Shield on 2026-11-11 at 20:52 UTC, updating 136 to 137 with app data preserved,
over network adb (reconnected for this install; the Shield was awake and was not woken or
driven). Frozen hashes were checked before installation. Read-back confirms
0.2.0-beta.15-review.1, build 137, and `arm64`, `status=speed-profile`, `reason=install-dm`.
Receipt: `harness-out/review1-preview-shield-install.log`. No other household device was touched.
Branch pushed to the private repository only; no tag or release.

After this build has run, do not go back to 136: catch-up templates are stored sealed
(decision "SECR-1"). The first film and series refresh rewrites every row once (HASH-1).
The next unreserved distributed APK needs 138+.
