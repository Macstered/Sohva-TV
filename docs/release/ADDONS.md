# Discover: addons, search and Library

For Sohva TV `0.2.0-beta.4`.

Discover lets you browse and play movies and series from your own
Stremio-compatible addons. It is optional and works without any IPTV source.
It keeps its own storage, separate from your playlists. Each profile has its
own addons, catalog order, Library and watch progress. Restricted profiles
cannot see or open Discover.

No addon, media, account or credential comes with the app. Install only
services you trust and are allowed to use.

If you updated from beta 23, your addons, catalog order and visibility,
Library and watch progress came across on the first start. You do not need to
add them again.

## Add your addons

Open **Discover** from the Home rail, then **Addons & setup**. There are
several ways to add addons.

### One address

Under **Manual setup**, enter the configured manifest address in **Configured
addon URL** and choose **Install addon**. The field hides what you type.

- The address must be an HTTPS address. A Stremio link, which starts with
  "stremio" instead of "https", is read as the same HTTPS address. An address
  that ends at a folder gets `manifest.json` added.
- **Only HTTPS manifests are accepted.** A plain HTTP address is refused.
- Installing the exact same address again reuses the installed addon. The
  same addon with a different configuration is a separate installation.

### A list of addresses (Import addons)

Choose **Import addons**. You can build a list in three ways:

- **Manual URL:** add addresses one at a time.
- **From a file:** choose a UTF-8 text file with one configured address per
  line, at most 32 lines and 256 KiB. A Nuvio addon-list or state JSON file
  also works; only the addresses are read from it.
- **Account & phone:** send a list from a phone, or copy from a Stremio
  account (see below).

Then choose **Preview import**. Each address is checked against its provider
and shows a status, such as **Ready to install**, **Already installed —
unchanged**, **Duplicate in this list — skipped** or a failure. Nothing is
installed yet. Choose which ready addons to include, then **Install N new
addons**. Addons you already have are never changed by an import, and one
failing address does not stop the others.

### From a phone

**Set up from phone** or **Choose file on phone** shows a QR code and an address
on your home network. Open it on a phone on the same network, paste the list
or choose a `.txt` file on the phone, and send it. The list then waits on the
TV for your preview.

- The page is served by the TV itself, not by a website. It is in English.
- It uses plain HTTP on your local network with a one-use link. Keep the QR
  code and link private, and use a network you trust.
- The session ends after 10 minutes, after one list has been sent, when you
  leave the screen, or when the app goes to the background.

### Copy from a Stremio account

**Copy from Stremio account** shows a QR code for Stremio's own sign-in page.
Approve the TV there on your phone. The TV then reads your account's addon list
and puts it in the import list for you to preview and confirm.

- Only the addon addresses are copied. Your Stremio account, Library, watch
  history and settings are not changed or copied.
- The temporary sign-in key is not stored. The attempt ends after 10 minutes or
  when you leave the screen.
- An account with more than 32 addons is refused; use a smaller list instead.

## Redirects

Some providers answer an address with a redirect to another address. Sohva TV
follows these for addon requests (manifests, catalogs, details, stream and
subtitle lists) only when all of these hold:

- at most **three** redirects in a row;
- every step goes to an **HTTPS** address;
- no step goes to a **local network address**: the TV itself, a private
  address on your home network, or a link-local address.

A longer chain, a step to plain HTTP, or a step to a local address fails with
"This address redirects. Install the provider’s final HTTPS manifest URL." The
installed address stays the one you entered. Beta 23 followed no redirects,
so addons that use them, such as OpenSubtitles Pro, could not be installed.

Video streams follow their own rule: up to five redirects, never from HTTPS
down to HTTP.

## Addons that need configuring first

Some addons must be configured on the provider's own web page before they can
be used. If you enter such an addon's plain address, it is refused with
"Configure this addon on its provider’s page first, then install the
configured manifest URL." Configure it on the provider's page, copy the
configured manifest address it gives you, and install that.

If a provider later answers that the configuration has expired or changed, the
saved copy of its data is removed and the error is shown. Configure it again
and install the new address.

## Addon roles

- A metadata or catalog addon supplies browsing rows and title details.
- A stream addon supplies playable sources. Installing only a catalog does not
  give you anything to play.
- A subtitle addon supplies external subtitles.

In **Addons & setup** each addon can be switched on or off, refreshed, moved up
in priority with **Priority ↑**, or removed. Removing asks first, with focus on
Cancel. **Catalogs** lets you reorder or hide browsing rows for this profile.

## Browse and play

The Discover home shows **Continue watching** and one row per catalog, with
**Show all** at the end of each row. **Search** looks through your catalogs and
shows Movies and Series rows. **Library** holds the movies and series you
saved, up to 1,000 per profile.

Open a title, choose a source, and play. Sources that the app cannot play,
such as torrents, external links or archives, are listed as unsupported and
never started. With autoplay on, a finished episode continues to the next
one, also across seasons.

## Subtitles from addons

- After a stream starts, the app asks your subtitle addons for results. It
  sends only the stream's file name, size and hash, never its address or
  headers. Subtitles are never requested on title pages or on Home, because
  some providers count subtitle requests as watching.
- The app picks a subtitle automatically from your preferred subtitle
  languages in **Settings > Playback**. When your preferred audio language is
  available, subtitles stay off.
- Open the subtitle picker from the player's controls or the loading screen.
  The left column lists languages and **Subtitles off**; the right column lists
  the options. **Show all languages** shows every language. D-pad moves stay
  inside the picker and the sync panel.
- Downloaded SRT, WebVTT and SSA/ASS subtitles are supported, up to 4 MiB.
  Other files, such as ZIP archives, are refused and the next option is tried.
- **Subtitle sync** moves a downloaded subtitle earlier or later by up to 60
  seconds. It applies to the current playback only and may rebuffer briefly.
  Subtitles inside the video itself cannot be adjusted.
- Size, colour and background follow **Settings > Playback**.

## Data, privacy and limits

- Addon addresses often contain an account token. Treat them like passwords.
  They are stored encrypted on the TV and never shown in logs or diagnostics.
  Do not post them, QR codes or configuration files in a report.
- The encrypted `.smbak` backup does **not** include Discover data.
  Uninstalling or clearing storage deletes it. Removing a Sohva profile also
  removes that profile's Discover data.
- No torrent, NZB, archive, DRM, download or external-player support. Some
  adaptive streams without a file extension do not play. Video, HDR and audio
  support depends on your TV.
- The app does not translate addon texts. Choose the metadata language in your
  metadata addon's own configuration.

Read the [privacy policy](PRIVACY.md) and the [tester checklist](TESTING.md)
before you report a problem.
