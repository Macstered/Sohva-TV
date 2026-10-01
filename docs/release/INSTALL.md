# Install and update Sohva TV

For Sohva TV `0.2.0-beta.15`, Android build 125.

## Before you start

- You need an Android TV or Google TV device with Android 6.0 or later and a
  D-pad remote. The app has been tested on an Nvidia Shield and on Android TV
  emulators, including one set up like a low-end TV box. Other devices need
  your feedback. A phone or tablet is not the intended screen.
- Have your own M3U playlist or Xtream login ready, or your own Discover
  addons. An XMLTV address for the programme guide is optional.
- TMDB, API-Sports and Trakt are optional. Keys and accounts must be your own.
- Read the [privacy policy](PRIVACY.md). Prefer HTTPS sources. A plain HTTP
  source shows your login and viewing to others on the same network.

## Updating from beta 23

This build uses the same package name (`com.streammate.tv`) and the same
signing key as beta 23. Android therefore installs it over beta 23 as an
update, and your data stays. **Do not uninstall beta 23 and do not clear its
storage first**: that deletes the data this build moves across.

Before you update, you may save a backup in beta 23 under **Settings > Backup
& tools > Save backup**. Keep the file and its password private. A backup
saved in this build can also be restored in beta 23.

### With the in-app updater (recommended)

1. In beta 23, open **Settings > About** and choose **Check for updates**.
2. When `0.2.0-beta.15` is offered, choose **Download**. The app checks the
   file against the published checksum.
3. Choose **Install**. If Android asks, allow Sohva TV to install unknown apps.
   That permission covers Sohva TV only, and you can turn it off afterwards.

The updater also downloads the install profile for your Android version and
installs it with the app, so Android prepares the new version while it
installs.

### By sideloading the APK

1. Download `sohva-tv-0.2.0-beta.15.apk` from the numbered release on
   [GitHub](https://github.com/Macstered/Sohva-TV/releases) or from the tester
   pack you were given. Do not use a copy from any other site.
2. Check its checksum (see below).
3. Copy the file to the TV with a USB drive or your usual trusted method and
   open it with a file manager on the TV.
4. If Android asks, allow **Install unknown apps** for that file manager only.
   The setting is usually under Apps, Special app access, or Security &
   restrictions.
5. Choose **Update** (or **Install**). Read any warning; do not turn off
   system-wide protection to get past one.

### The first start after the update

On the first start, before the first screen, the app reads beta 23's data
once and moves it into its new storage. If this takes longer than a second,
the screen shows **Updating Sohva TV…**. Wait for it; do not force the app to
close.

These come across:

- settings, including the colour theme, interface size, time zone and start
  screen;
- profiles, and for each profile its favourites, recent channels, last
  channel, locked channels and allowed groups;
- the parental PIN;
- channel edits (name, group, hidden, order, guide channel, number, logo),
  including logos sent from a phone;
- your channel lists and your group and library organisation rules;
- your playlists and sources, and the TMDB and API-Sports keys;
- resume positions of films and episodes;
- reminders;
- Sohva Sport choices: followed sports and competitions, and the streams you
  confirmed or rejected for a match;
- your own fixes to title information (a film or series you matched by hand);
- Discover addons, catalog order and visibility, Library and watch progress;
- the Trakt connection of each profile.

These refill from your providers, because they are not copied:

- the programme guide and the movie and series libraries. Every source
  imports again right after the first start. This takes a few minutes, longer
  for very large libraries. The guide fills in as channels arrive.
- artwork and title information that were matched automatically. They fill in
  again over time in the background.
- your Trakt history. The first Trakt sync fetches it again.

Once every part has come across, beta 23's old files are removed from the TV.
If a part could not be read, the old files are kept so that a later build can
try again.

After the update, check **Settings > About**: the installed version must be
`0.2.0-beta.15`. Then check your sources, favourites, profiles and resume points.
This is a beta: a matching-key update is designed to keep your data, but it is
not a guarantee against data loss.

### Going back to beta 23

Android does not install an older build over a newer one. Uninstalling to go
back deletes all app data. If you need to go back, contact the developer
first. A backup saved in this build restores in beta 23, but a backup never
contains Discover data, Trakt, service keys or resume positions.

## Installing for the first time

Follow steps 1 to 5 of "By sideloading the APK" above; Android offers
**Install** instead of **Update**. Open **Sohva TV** from the TV's app list.

If your TV does not allow APK installs, note the TV model, Android version and
the exact message and contact the developer. Do not root or modify the device.

## Check the download

`SHA256SUMS.txt` lists a SHA-256 value for the APK, both install profiles and
the documents. On a Windows computer, in PowerShell, from the download folder:

```powershell
Get-FileHash .\sohva-tv-0.2.0-beta.15.apk -Algorithm SHA256
```

On macOS or Linux:

```sh
shasum -a 256 sohva-tv-0.2.0-beta.15.apk
```

The value must match the APK's line in `SHA256SUMS.txt` from the same release.
A checksum shows that a file was not changed; it does not make a download from
an unknown site safe.

## The install profiles

`sohva-tv-0.2.0-beta.15.api31.dm` (Android 12 and newer) and
`sohva-tv-0.2.0-beta.15.api28.dm` (Android 9 to 11) are optional. They let
Android compile the app while it installs, so the first starts are quicker.

The in-app updater picks the right one and uses it automatically. When you
sideload the APK, you can ignore them: Android then prepares the app later,
while the TV is idle. Android 6 to 8 do not use them.

## Optional installation from a computer

Use Android SDK
[Platform Tools](https://developer.android.com/tools/releases/platform-tools)
only if you already know how to turn on and authorise debugging on your TV. In
PowerShell, from the APK's folder:

```powershell
adb devices -l
adb -s YOUR_DEVICE_SERIAL install -r .\sohva-tv-0.2.0-beta.15.apk
```

Replace `YOUR_DEVICE_SERIAL` with the serial that `adb devices` shows. Always
name the device. Do not use uninstall, clear-data or downgrade options. Turn
debugging off again when you no longer need it.

## First setup on a new installation

### 1. Add a source

Open **Settings > Playlists**.

- **M3U:** choose **+ Add M3U source**, give the source a name and enter your
  playlist address. Add an XMLTV guide address if you have one. **Test
  address** reads the start of the playlist and says how many entries it
  found, or what went wrong.
- **Xtream:** choose **+ Add Xtream source** and enter the server address,
  user name and password from your provider. **Test connection** checks them.
- **From a phone:** choose **Set up from a phone**. The TV shows a QR code and
  an address on your home network. Open it on the phone and type the details
  there. The page is served by the TV itself, only while it is on screen, and
  closes after 15 minutes.

Choose what to import (Live TV, VOD only, or both) and choose **Save
securely**. A new source starts syncing at once: channels, then the guide,
then movies and series. Wait for the result before you judge the library.
Avoid pressing Refresh again while an import runs.

### 2. Optional artwork and title information

Open **Settings > Library**, enter your TMDB key or Read Access Token, turn
TMDB on and save. **Test TMDB** checks the connection. TVmaze needs no key.
**Metadata language** chooses the language of titles and plots.

### 3. Optional Sohva Sport

Open **Settings > Sohva Sport**, enter your own API-Sports key and save it.
Then open **Choose followed sports and competitions**. Start with a small
selection: your key's daily quota limits what the app can fetch.

### 4. Optional Trakt

Open **Settings > Accounts** and choose **Connect Trakt**. Scan the code with a
phone and approve the TV on Trakt's own page. Each profile connects its own
Trakt account. Restricted profiles cannot use Trakt.

### 5. Optional Discover addons

See [Discover addons](ADDONS.md).

### 6. Language and remote

Under **Settings > General** you can change the interface language (the app
restarts), the interface size, the colour theme, the time zone and the start
screen. **Settings > Remote buttons** sets what each button does during
playback.

- **D-pad:** move focus. **OK:** open or choose. **Back:** close or go back.
- During playback, press OK to show the controls.

Next: the [tester checklist](TESTING.md).
