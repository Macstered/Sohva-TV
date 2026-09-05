# Sohva TV privacy policy

Policy packaged: 3 September 2026, updated 5 September 2026. Applies to Sohva TV 0.1.0-beta.1, 0.1.0-beta.2 and 0.1.0-beta.3.

Sohva TV plays media sources configured by the user. It contains no channels
or media and does not provide an IPTV subscription. There is no
developer-operated account, analytics, advertising, crash-reporting or
telemetry service in the app.

## On-device information

The app stores source addresses and credentials, user-supplied metadata and
sports API credentials, guide and catalogue caches, viewing progress, recent
channels, favourites, custom groups/lists, parental-control settings and
optional event-to-channel matching decisions on the Android TV device.

Credentials and API keys are encrypted using Android Keystore. Ordinary caches
and viewing state are not claimed to be separately encrypted by the app.
Portable backups are created only at the user's request and encrypted with the
user's backup password. Backups are not uploaded to a developer service.
Android automatic backup and device transfer are disabled for app data.

Local information remains until cleared, replaced by a restore or removed by
uninstalling the app. Provider caches expire or are replaced under their
refresh policies. An exported backup remains wherever you saved it until you
delete it separately.

## Direct network connections

The device connects to the IPTV M3U, XMLTV or Xtream sources you configure;
API-Sports if you supply a key; TMDB if you enable it and supply a credential;
TVmaze if enabled; and image hosts/CDNs referenced by those services.

Metadata requests can include titles, year, content type, season and episode
numbers. Providers receive the device's requests and IP address and process
them under their own policies. The Sohva TV developer does not receive a copy.

API-Sports, TMDB and TVmaze use HTTPS. HTTP is supported for user-configured
IPTV compatibility. With HTTP, credentials, programme data and viewing traffic
may be visible to parties on the network. Prefer HTTPS and trusted networks.

## Support and downloads

Reports are not sent automatically. If you email the developer, your email
address and the information you choose to include are received for handling
that report. Do not include credentials, playlist URLs or exported backups.
Public GitHub reports, if enabled, are visible to other people; downloading
from GitHub is subject to GitHub's own privacy practices.

## Children and parental controls

Parental controls restrict playback locally. They do not create a child
profile or transmit information about a child. The app is not designed to
collect personal information from children.

## Contact and changes

Questions, privacy requests and issue reports:
[hello@luontra.fi](mailto:hello@luontra.fi).
Material changes will update the policy distributed with the app package.

## Update check

From beta 3 on, the app asks GitHub for Sohva TV's public release list when
**Check for updates** is chosen and, at most once a day, while the app is
open. The request carries no account, playlist or device information beyond
what any web request carries. A download happens only when you choose it, is
checked against the published checksum, and is installed only when you choose
**Install**. GitHub's own privacy practices apply to that request.

## Setting up from a phone

The optional "Set up from a phone" page is served by the TV itself, on the home network only, while the settings screen shows it, and only to a browser that opened it through the code on the TV screen. What is typed there is saved on the TV and sent nowhere else. The page uses plain HTTP inside the home network.
