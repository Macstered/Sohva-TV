# Install and set up Sohva TV

For **0.1.0-beta.1**, Android build **2**.

## Before you start

- Use an Android TV / Google TV device with Android 6.0 (API 23) or later and
  a D-pad remote. NVIDIA Shield and an Android TV emulator have been used for
  testing; other devices and codecs need tester feedback. A phone or tablet is
  not the intended interface. Not every format works on every device.
- Have your own authorized M3U playlist or Xtream server details ready. An
  XMLTV address is optional for an M3U programme guide.
- TMDB and API-Sports credentials are optional and must be your own. Neither
  is required to play your IPTV source. API-Sports plan coverage and quotas
  affect the sports information available.
- Read [Privacy](PRIVACY.md). HTTPS sources are preferable. HTTP IPTV sources
  expose credentials and traffic to the network; use only a trusted network.

## Install using your TV

1. Download `sohva-tv-0.1.0-beta.1.apk` from the developer's supplied package
   or the approved release page. Do not use an APK from an unknown mirror.
2. Transfer that file to the TV using a USB drive or your existing trusted
   file-transfer method, then open it with a file manager on the TV.
3. If Android asks, allow **Install unknown apps** for that file manager only.
   The settings path varies by TV; commonly it is under Apps / Special app
   access or Security & restrictions.
4. Select **Install**, or **Update** if the app is already installed. Read any
   security warning; do not disable system-wide protection to get past one.
5. Open **Sohva TV** from the TV's app list. You may turn off the file manager's
   install permission afterwards.
6. Check **Settings > About > About, privacy and licences**. The version must
   be **0.1.0-beta.1**.

If your TV does not offer APK installation, record the TV model, Android
version and exact message and contact the developer. Device installation
policies differ; do not root or modify the device to install this beta.

### Updating Sohva TV later

Install a newer signed Sohva TV beta **over** the existing app. Do not uninstall
or clear app storage first.

For extra safety, use **Settings > Backup > Save backup**, set a strong
password and store the encrypted `.smbak` file somewhere private. This is a
configuration backup, not a complete copy of all cached media or viewing state.
Keep the password yourself; the developer cannot recover it. Never upload the
backup to an issue or include it with the APK.

After updating, confirm that sources, settings, favourites, custom groups and
resume points are still present. A matching-key update is expected to preserve
app data, but this is a beta, not a guarantee against data loss.

Android normally rejects an older build after installing a newer one. If a
rollback is needed, contact the developer. Do not uninstall to force a
downgrade; that removes local data.

### Optional installation from a computer

Use Android SDK Platform Tools only if you already know how to enable and
authorize debugging on your device. In PowerShell, from the APK's directory:

```powershell
adb devices -l
adb -s YOUR_DEVICE_SERIAL install -r .\sohva-tv-0.1.0-beta.1.apk
```

Replace `YOUR_DEVICE_SERIAL` with the exact serial shown by ADB. Always select
the intended device explicitly. Do not use uninstall, clear-data or downgrade
options. Disable debugging again when finished if you no longer need it.

To check file integrity before installation:

```powershell
Get-FileHash .\sohva-tv-0.1.0-beta.1.apk -Algorithm SHA256
```

Compare with the APK entry in `SHA256SUMS.txt` from the same trusted package.
A checksum detects a changed file; it does not make an untrusted download safe.

## First setup

### 1. Add a source

Open **Settings > Playlists**.

- **M3U:** choose **+ Add M3U source**, enter a friendly source name and your
  playlist address. Add an XMLTV programme-guide address if you have one.
- **Xtream:** choose **+ Add Xtream source**, enter a source name, server
  address, username and password supplied by your provider.

Choose **Content to import**: Live TV, VOD only, or TV and VOD. Leave the source
enabled, then choose **Save securely**. For Xtream, **Test connection** checks
your details before import.

Use the source's **Refresh playlist** / **Refresh channels**, **Refresh movies
and series** and **Refresh programme guide** actions as applicable. Wait for
the import result before judging the library. The first download and metadata
matching can take longer than later browsing, especially with large sources.
Avoid repeatedly pressing Refresh while an import is running.

An empty guide or catalogue does not always mean an app fault: check the source
is enabled, its import scope, account access and refresh result. M3U movie and
series recognition also depends on the provider's naming and categorization.

### 2. Optional artwork and title information

Open **Settings > Programme data & artwork**. Enter your TMDB API key or Read
Access Token, enable TMDB and choose **Save metadata settings**. **Test TMDB**
checks the connection. TVmaze can be enabled without a key.

These services receive title information for matching. Artwork and genre groups
fill progressively; an unmatched or missing poster does not prevent playback.
If a title is wrong or has no poster, open its information page, search for the
correct title information again and select the matching result. Do not clear
the whole library just to repair one poster.

### 3. Optional Sohva Sport

Open **Settings > Sohva Sport**, enter your own direct API-Sports key and choose
**Save key**. Open **Choose followed sports and competitions**, select the
sports and competitions you want, and check the sports timezone.

Sports results, schedules and matching depend on provider coverage, the key's
quota and your own TV channels. A sports listing does not include a stream or
grant access to an event. Start with a small selection while testing. Do not
share keys or repeatedly refresh to try to bypass a provider quota.

### 4. Language and basic controls

Under **Settings > Playback & remote**, use **Interface language** to choose
English if necessary; the app restarts to apply the change.

- **D-pad arrows:** move focus. **OK / Select:** open or activate an item.
- **Back:** close the current view or return to the previous screen.
- During playback, use OK to bring up the controls, then navigate with the
  D-pad. Available audio, subtitle and picture options depend on the stream.

Keyboard handling in an emulator depends on its configuration. Use the
emulator's D-pad controls if desktop arrow keys are not forwarded. Clipboard
paste is not guaranteed on every Android TV keyboard.

Next: follow the short [tester checklist](TESTING.md).
