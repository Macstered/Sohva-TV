# Demo screenshot mode

`demo` is a separately installable Sohva TV build for screenshots and demos.
It renders the production screens against deterministic fictional content and
never reads the production app's database, preferences, credentials, or image
cache.

## Start it

```powershell
.\scripts\start-demo-screenshot-mode.ps1 -Serial <device-serial>
```

The script builds `:app:assembleDemo`, installs
`app/build/outputs/apk/demo/app-demo.apk`, and launches
`com.streammate.tv.demo`. The production package remains
`com.streammate.tv`, so both can stay installed.

`-Serial` is optional when only one Android device is attached. The script
finds ADB through `ANDROID_SDK_ROOT`, `ANDROID_HOME`, or `PATH`; use `-Adb` to
override it explicitly.

Every cold process start restores the fixture snapshot and selects English.
The guide times and match times are anchored to the current hour so the guide
always has live and upcoming content. Automatic guide and catalogue metadata
workers are disabled for this build.

## Fixture ownership

- Fictional Room rows, encrypted source settings, sports results, and match
  events: `app/src/demo/java/com/streammate/tv/demo/StreamMateDemoContentProvider.kt`
- Generated poster and playback art: `app/src/demo/res/drawable-nodpi/`
- Deterministic channel and team marks: `app/src/demo/res/drawable/`
- Demo-only provider registration and app label: `app/src/demo/AndroidManifest.xml`
- Production-safe provider boundary: `app/src/main/java/com/streammate/tv/app/DemoContentProvider.kt`

The demo manifest is the only place that registers a provider. Release and
ordinary debug APKs therefore contain neither the fixture implementation nor
its raster assets.

## Generated artwork record

The raster set was generated in image-generation `create` mode. All prompts
requested full-bleed original artwork with no text, logos, brands, recognisable
people, copyrighted characters, or watermarks:

- `demo_movie_signal.png`: science-fiction drama poster; radio astronomer,
  antenna array, navy-to-amber dawn.
- `demo_movie_lighthouse.png`: mystery-adventure poster; isolated lighthouse,
  storm, teal and pale-gold light.
- `demo_series_harbor.png`: prestige mystery poster; rain-soaked ferry terminal,
  investigator in a yellow coat, cobalt and amber light.
- `demo_series_north.png`: optimistic adventure poster; arctic glass
  observatory and aurora, emerald and violet light.
- `demo_live_football.png`: fictional evening football broadcast; generic
  amber and teal kits, no score graphics, crests, sponsors, or real stadium.

The catalogue deliberately reuses this small art direction set across many
fictional titles. Add another generated file and include it in the provider's
poster URL list when a future screenshot needs more visual variety.
