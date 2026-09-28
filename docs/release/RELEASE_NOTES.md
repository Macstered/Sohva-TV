# Sohva TV 0.2.0-beta.1

Android build **101**. Prepared 27 September 2026. Prerelease for testers.

## Changed in 0.2.0-beta.1

- Rebuilt app. Sohva TV was written again from the start. It has the same
  features as beta 23 and looks almost the same, but it is built to be
  lighter, so that it also runs well on low-end TV boxes.
- Your data stays. Install over beta 23; do not uninstall. On the first start
  the app moves your settings, profiles, favourites, recent channels, locks,
  allowed groups, parental PIN, channel edits and phone logos, channel lists,
  organisation rules, playlists and service keys, resume positions,
  reminders, sport choices, title fixes, Discover addons and progress, and the
  Trakt connection. If this takes more than a second, it says "Updating Sohva
  TV…". The guide and the movie and series libraries then import again from
  your providers, which takes a few minutes. Beta 23's old files are removed
  once everything has come across.
- Trakt: watched history is read page by page to the end, so more than 100
  watched movies and your watched episodes show their ticks.
- Trakt: when Trakt asks the app to wait, it sends nothing until the wait is
  over, and Settings > Accounts says how many minutes are left. Scrobbles are
  sent at least one second apart.
- Discover: addon addresses may redirect up to three times, only to HTTPS
  addresses and never to local network addresses. Addons such as OpenSubtitles
  Pro now install.
- Subtitle picker and subtitle sync: the D-pad no longer moves focus to the
  player controls behind them.
- Shield: with Match the display to the picture on, films and episodes no
  longer stay paused after the picture mode changes, so they reach their end.
- Search matches the start of words ("mat" finds "Match").
- Clear all guide data now removes only programme listings. Channels, films,
  series and watch progress stay.
- A restore that would remove sources names them and asks first. A backup
  saved in this build also restores in beta 23.
- Removing a profile also removes its Discover data and its Trakt connection.
- Change EPG channel opens a list you can search.

## Muutokset versiossa 0.2.0-beta.1

- Sovellus on rakennettu uudelleen. Sohva TV on kirjoitettu alusta asti
  uudestaan. Ominaisuudet ovat samat kuin beta 23:ssa ja ulkoasu lähes sama,
  mutta sovellus on tehty kevyemmäksi, jotta se toimii hyvin myös
  edullisissa TV-bokseissa.
- Tietosi säilyvät. Asenna beta 23:n päälle, älä poista vanhaa versiota.
  Ensimmäisellä käynnistyskerralla sovellus siirtää asetukset, profiilit,
  suosikit, viimeksi katsotut kanavat, lukitukset, sallitut ryhmät,
  lapsilukon PIN-koodin, kanavamuokkaukset ja puhelimesta lähetetyt logot,
  kanavalistat, järjestyssäännöt, soittolistat ja palveluavaimet,
  katselukohdat, muistutukset, urheiluvalinnat, nimikkeiden korjaukset,
  Discover-lisäosat ja niiden katseluhistorian sekä Trakt-yhteyden. Jos
  siirto kestää yli sekunnin, näytöllä lukee "Päivitetään Sohva TV:tä…".
  Ohjelmaopas sekä elokuva- ja sarjakirjastot ladataan sen jälkeen uudelleen
  palveluntarjoajilta. Siihen menee muutama minuutti. Beta 23:n vanhat
  tiedostot poistetaan, kun kaikki on siirretty.
- Trakt: katseluhistoria luetaan sivu kerrallaan loppuun asti. Siksi yli 100
  katsottua elokuvaa ja katsotut jaksot saavat katsottu-merkin.
- Trakt: kun Trakt pyytää odottamaan, sovellus ei lähetä mitään ennen kuin
  odotus on ohi. Asetukset > Tilit kertoo, montako minuuttia on jäljellä.
  Katselutiedot lähetetään vähintään sekunnin välein.
- Discover: lisäosan osoite saa ohjata eteenpäin enintään kolme kertaa, vain
  HTTPS-osoitteisiin eikä koskaan lähiverkon osoitteisiin. Esimerkiksi
  OpenSubtitles Pro asentuu nyt.
- Tekstitysvalikko ja tekstityksen ajoitus: kohdistus ei enää siirry niiden
  takana oleviin toisto-ohjaimiin.
- Shield: kun Sovita näyttö kuvataajuuteen on päällä, elokuvat ja jaksot
  eivät enää jää tauolle kuvatilan vaihtuessa, vaan ne toistuvat loppuun.
- Haku etsii sanojen alusta ("mat" löytää sanan "Match").
- Tyhjennä kaikki opastiedot poistaa nyt vain ohjelmatiedot. Kanavat,
  elokuvat, sarjat ja katselukohdat säilyvät.
- Jos varmuuskopion palautus poistaisi lähteitä, sovellus nimeää ne ja kysyy
  ensin. Tässä versiossa tallennetun varmuuskopion voi palauttaa myös beta
  23:ssa.
- Profiilin poisto poistaa myös sen Discover-tiedot ja Trakt-yhteyden.
- Vaihda EPG-kanava avaa listan, josta voi hakea.

## Update safely

Install over your existing Sohva TV app. **Do not uninstall and do not clear
storage**: that deletes the data this build moves across. You can use
**Settings > About > Check for updates** in beta 23. The package name
`com.streammate.tv` and the signing key are the same as in beta 23. The APK
checksum is in `SHA256SUMS.txt`. The release tag `v0.2.0-beta.1` names the
matching source, licensed under `GPL-3.0-only`.

The encrypted `.smbak` backup still does not include Discover data, Trakt,
service keys or resume positions. Never post addon addresses, phone QR codes,
backups or raw logs.

See [INSTALL.md](INSTALL.md), [TESTING.md](TESTING.md) and
[ADDONS.md](ADDONS.md).
