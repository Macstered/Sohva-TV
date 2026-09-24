# Tools

Build-machine tooling for the rebuild. Nothing here ships in the app. Python 3, standard library only.

Every tool that touches a device works **only on a rebuild emulator**: its serial is `emulator-NNNN`,
`ro.kernel.qemu` is 1 and its AVD name starts with `sohva_rebuild`. Real TVs (the owner's Shield is
often attached to adb) and other emulators (one holds the old app's installs) are refused. Gradle's
`install*`/`connected*` tasks likewise need `ANDROID_SERIAL=emulator-NNNN`.

| Tool | What it does |
|---|---|
| `start_emulator.py --avd sohva_rebuild_tv30 --port 5570` | Cold-boots a rebuild AVD (`-no-snapshot-load`, correct clock) and waits for boot |
| `verify_release_dex.py --serial emulator-5570` | Installs the release APK (debug-signed copy if unsigned), forces a verify compile, fails on "failed to verify" |

AVDs (created once with `avdmanager`, `tv_1080p`, 1920×1080 xhdpi, 2 GB RAM, 6 GB data):
`sohva_rebuild_tv30` (API 30, the stand-in of plan/07) and `sohva_rebuild_tv34` (API 34, baseline
profile generation, which needs API 33+).
