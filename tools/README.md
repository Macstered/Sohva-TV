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
| `import_strings.py [--check]` | Imports beta 23's strings unchanged from the kit, grouped by feature; `--check` fails when they drifted |
| `brand_assets.py` | Brand bitmaps as pixel-identical lossless WebP at xhdpi and xxxhdpi |
| `fixture/make_fixture.py` | The owner-scale synthetic fixture (56,164 channels, 165,600 programmes anchored to now) into `harness-out/fixture` |
| `fixture/serve_fixture.py` | Serves it on 127.0.0.1:8780 only (emulator: `http://10.0.2.2:8780`); refuses a taken port. 8765 belongs to the old app's harness |
| `home_benchmark_report.py --input <run-folder> --trace-processor <executable> --output <report.json>` | Summarises populated Home's cold starts, memory, compositions and per-press main-thread CPU from its Perfetto traces |

Gradle side (always with `ANDROID_SERIAL=emulator-NNNN`):

- `:app:generateBaselineProfile` on an API 33+ rebuild emulator (`sohva_rebuild_tv34`, port 5572): writes
  `app/src/main/generated/baselineProfiles/`; review and commit.
- `:benchmark:connectedBenchmarkReleaseAndroidTest` with
  `-Pandroid.testInstrumentationRunnerArguments.class=com.sohva.tv.benchmark.StartupBenchmark` on the
  stand-in (`sohva_rebuild_tv30`, port 5570): cold start without compilation and with the profile.

AVDs (created once with `avdmanager`, `tv_1080p`, 1920×1080 xhdpi, 2 GB RAM, 6 GB data):
`sohva_rebuild_tv30` (API 30, the stand-in of plan/07) and `sohva_rebuild_tv34` (API 34, baseline
profile generation, which needs API 33+).

For a 1 GB Home run, start a stopped rebuild emulator with
`python tools/start_emulator.py --avd sohva_rebuild_tv30 --port 5570 --memory-mb 1024`.
The RAM override is temporary; it does not edit the AVD or any display settings. Run
`:benchmark:connectedBenchmarkReleaseAndroidTest` with
`-Pandroid.testInstrumentationRunnerArguments.class=com.sohva.tv.benchmark.PopulatedHomeBenchmark`.
Keep the host quiet during measurement. Preserve each run's JSON and traces in a separate folder
before running the report tool. Compare main-thread CPU, not software-GPU frame latency.

The benchmark writes only fictional emulator data: an owner-scale library, eight cached Trakt lists
with 30 titles each, library marks, posters and backdrops. Each process decodes the artwork anew
from local JPEGs. It measures image loading and drawing, not internet latency. It checks focus on
every step along a row and across all eight rows, and compares populated and default Home starts.

`check_all.py` and CI build and lint the Play variant as well as the public release. `:app:checkPlayGates`
checks APK and method sizes, the built APK/bundle's Play identity and TV manifest, absence of the
sideload installer permission, and generated profiles. These checks do not need a Play signing key.
