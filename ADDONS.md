# Discover: addons, search and Library

For Sohva TV **0.1.0-beta.13**. Discover is optional and starts empty. It is
separate from your IPTV libraries and is unavailable to restricted profiles.
Install only services and media sources you trust and are authorized to use.

## Add your configurations

Open **Discover > Addons & setup** with the remote. The left rail expands when
focused. You can install a configured addon URL, choose **From file**, or use
the phone setup offered by those screens.

- A URL-list file is UTF-8 text with one configured manifest URL per non-empty
  line, at most 32 entries and 256 KiB. Do not add prose or account passwords on
  separate lines. Select the file on the TV, or open the phone QR page to paste
  the list or choose a local file.
- Review the preview **on the TV**, then confirm installation. Existing matching
  installations are skipped; they are not disabled or replaced. A failing entry
  does not mean that all other entries failed.
- Phone setup needs the phone and TV on the same trusted local network. It uses
  a temporary, expiring HTTP link; keep the QR/link private and close the setup
  screen when finished. There is no developer-hosted setup website.
- **Copy from Stremio** opens Stremio authorization on your phone/browser.
  Authorize there, then review and confirm the copied configurations on the TV.
  Sohva does not change the source account or copy watch history or Library.
- Supported Nuvio addon-list/state JSON files can be read as configuration
  imports. This is experimental compatibility, not a full Nuvio backup restore
  or direct account synchronization. A plain URL list is the most predictable
  alternative.

Configured URLs and list files can contain account tokens. Treat them like
passwords. HTTPS is preferred; accepting HTTP exposes traffic to the network.
Do not include these files or URLs in public issue reports.

## Choose the right addon roles

A metadata/catalog addon such as AIOMetadata supplies browsing and title details.
Playback requires a configured addon that returns compatible streams; subtitle
addons supply optional external subtitles. Installing only a catalog does not
guarantee playable sources. Addons and their services may require their own
accounts or configuration. Sohva does not supply those accounts or media.

The addon manager lets you enable, disable, refresh and remove installations.
Catalog order and visibility are separate, per-profile controls: pick up a
catalog, move with the D-pad, press OK to place it, or Back to cancel. Hidden
catalogs do not appear on Home or in Discover choices.

## Browse, save and play

Home shows available catalog rows; **Show all** opens a grid which loads more as
you browse. **Search** accepts text and returns Movies and Series rows from
eligible installed catalogs. **Discover** exposes catalog-specific filters.
Results and available filters depend on the providers.

Open a movie, or choose a season and episode, then select a source. **Continue
watching** resolves sources again for a saved position. **Add to Library** saves
a movie or whole series; remove it from the same details page or Library.
The Library is local, per-profile, and limited to 1,000 saved titles.

Sohva's primary/secondary subtitle preferences and subtitle appearance apply
during addon playback. Open the bottom subtitle control to choose a track or
turn subtitles off. The subtitle settings can show all languages. Timing offers
earlier/later adjustment, Apply and Reset; it applies to the current track/session
and may briefly rebuffer. Unsupported track types cannot be adjusted.

## Languages, data and limitations

Sohva's interface language controls menus, not provider descriptions. Configure
Finnish or another metadata language in your metadata addon; cached catalogs can
be revalidated as you browse. Not every provider has a translated synopsis.

Discover installations, Library and progress survive an ordinary app restart
and matching-key update. **The existing Sohva `.smbak` backup does not include
Discover data.** Uninstalling or clearing storage removes it. Keep your addon
configurations privately; no cloud synchronization is provided.

Provider delays, quotas and missing sources are outside Sohva's control. Some
addons perform their own watch tracking when asked for subtitles. Read the
[privacy policy](PRIVACY.md), [release limits](RELEASE_NOTES.md) and
[testing guide](TESTING.md) before reporting a problem.
