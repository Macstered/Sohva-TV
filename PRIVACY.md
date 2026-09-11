# Sohva TV privacy policy

Policy packaged: 3 September 2026, updated 11 September 2026. Applies to Sohva TV 0.1.0-beta.1 through 0.1.0-beta.13.

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

Discover is unavailable to restricted profiles. This is an access boundary,
not an age-rating classification of content supplied by third-party addons.

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

## Diagnostics file

**Save diagnostics** under About writes a text file only when you choose it,
to the location you pick. It holds the app version, device model and Android
version, your settings, the names and refresh states of your sources, and the
app's last few hundred event lines. Addresses, user names, passwords and keys
are removed before the file is written. The app never sends the file anywhere;
sharing it is your choice.

## Stream requests

A request for a stream carries the user agent your playlist sets for that
channel, or, when it sets none, the app's own name and version ("Sohva TV/…").
Your provider sees that name, as it would see any player's; nothing else about
you or your device is added to the request.

## Reminders

A reminder is stored on the TV as the programme or match title, its channel
and start time, and is removed once it has fired or is half an hour past its
start. It uses the TV's alarm service and its notification panel, and nothing
leaves the device. "Display over other apps" is an optional grant, made in the
TV's own settings, that lets a due reminder bring Sohva TV to the front; the
app draws nothing over other apps except its own screen when it opens.

## Setting up from a phone

The optional "Set up from a phone" page is served by the TV itself while its
setup screen is open, using an expiring capability link. It uses plain HTTP on
the local network, not a developer-hosted website. Use a trusted network and
keep the QR/link private. Imported configuration is received by the TV; after
confirmation, the TV contacts configured services to validate or use it.

## Discover addons

Discover connects directly to user-installed addons and their media, artwork
and subtitle hosts. Requests can include search text, filters and title/episode
identifiers, and expose the requester's IP address. Provider behavior is outside
Sohva's control: some addons use subtitle requests for their own watch tracking.
Sohva does not add background subtitle polling or send watch history to the developer.

Configured addon URLs and stored addon payloads are encrypted with device-local
keys protected by Android Keystore. Catalog caches, profile-scoped Library and
viewing progress, catalog order/visibility and subtitle preferences remain local.
**The existing portable Sohva backup does not include Discover data.** Clearing
storage or uninstalling removes it. Keep your addon configuration privately.

Optional Stremio authorization copies addon configurations after confirmation;
it does not modify the source account or persist temporary authorization tokens.
File imports copy configurations only, not external watch history or Library.
Configured URLs and configuration lists may contain credentials and must be
treated as secrets. Addon HTTP connections require explicit acceptance; their
traffic is unencrypted. Prefer HTTPS. No addon or credential is bundled.
