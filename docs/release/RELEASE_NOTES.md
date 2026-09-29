# Sohva TV 0.2.0-beta.4

Android build **113**. Prepared 29 September 2026. Prerelease for testers.

## Changed in 0.2.0-beta.4

- Home: titles your sources have are marked on Trakt cards with a small movie
  or series icon in the top left corner, instead of the larger "In library" tag.
- Movies and series: subtitles from your Discover subtitle addons (for example
  OpenSubtitles) for films and episodes from your playlists. The subtitle list
  is the same one Discover uses. When the file has no subtitle in your
  preferred language, one is looked for while the film already plays. The
  title needs its details from TMDB or TVmaze.

## Muutokset versiossa 0.2.0-beta.4

- Etusivu: lähteistäsi löytyvät nimikkeet merkitään Trakt-korteissa pienellä
  elokuva- tai sarjakuvakkeella vasemmassa yläkulmassa isomman "Kirjastossa"-
  merkinnän sijaan.
- Elokuvat ja sarjat: tekstitykset Discoverin tekstityslisäosista (esimerkiksi
  OpenSubtitles) myös soittolistojesi elokuville ja jaksoille. Tekstityslista on
  sama kuin Discoverissa. Jos tiedostossa ei ole tekstitystä toivomallasi
  kielellä, sitä haetaan elokuvan jo pyöriessä. Nimike tarvitsee tiedot TMDB:stä
  tai TVmazesta.

Updating from beta 23? Read the 0.2.0-beta.1 notes below too.

# Sohva TV 0.2.0-beta.3

Android build **112**. Prepared 29 September 2026. Prerelease for testers.

## Changed in 0.2.0-beta.3

- Settings > Home: choose the order of Home's rows and which rows it shows.
  Each profile has its own. Reorder: select a row, move it with Up and Down,
  press OK to place it; Back cancels. Show / hide: one switch per row. A
  hidden row is not loaded at all, which keeps Home quick on slower boxes.
  Reset to default brings back the usual rows. The layout is in backups.
- Trakt rows on Home: in Settings > Home > Trakt rows, add up to 8 Trakt
  lists. Trending, popular and most anticipated movies and series, and the box
  office, need no Trakt account. Your movie and series watchlists need the
  profile's Trakt account. Each row shows 30 titles; OK opens the title as
  Recommended does. Lists refresh in the background, never while video plays.
- Home: Left from the first card of a poster row (Recommended for you and the
  Trakt rows) opens the side menu again.
- Channel logos: the channel name's letters no longer show around logos
  that do not fill their tile, in the player's channel list and on Home.
- Movies and series: "Find in Discover" on a title's page looks the title up
  in your Discover addons, for example when your sources lack some seasons.
  A series page says when your sources have fewer seasons than have aired.
- Home: coming back from another screen, focus returns to the card you
  left from, not the first row.
- Trakt cards on Home show "In library" when your sources have the title. In
  Settings > Home > Trakt rows, each added list can show only those titles.
- Any public Trakt list on Home: in Settings > Home > Trakt rows, choose
  "Add a Trakt list" and type its trakt.tv address, its number or words from
  its name, or send the address from your phone. The row takes the list's
  own name.
- Trakt smart lists can be added by their address too (app.trakt.tv/lists/smart/...).
- Discover: opening the subtitle list no longer crashes the app when a
  subtitle is offered twice.

## Muutokset versiossa 0.2.0-beta.3

- Asetukset > Etusivu: valitse etusivun rivien järjestys ja se, mitkä rivit
  näytetään. Jokaisella profiililla on omansa. Järjestä: valitse rivi, siirrä
  sitä ylös- ja alas-painikkeilla ja aseta se paikalleen OK-painikkeella;
  Takaisin peruu. Näytä / piilota: yksi kytkin riviä kohden. Piilotettua riviä
  ei ladata lainkaan, mikä pitää etusivun nopeana hitaammissakin bokseissa.
  Palauta oletukset tuo tavalliset rivit takaisin. Asettelu on varmuuskopiossa.
- Trakt-rivit etusivulla: kohdassa Asetukset > Etusivu > Trakt-rivit voit
  lisätä enintään 8 Trakt-listaa. Trendaavat, suositut ja odotetuimmat
  elokuvat ja sarjat sekä elokuvateatterien kärki eivät vaadi Trakt-tiliä.
  Elokuvien ja sarjojen katselulistat vaativat profiilin Trakt-tilin. Jokaisella
  rivillä on 30 nimikettä; OK avaa nimikkeen kuten Suosituksia sinulle -rivillä.
  Listat päivittyvät taustalla, eivät koskaan videon toiston aikana.
- Etusivu: vasen-painike julistekorttirivin ensimmäisestä kortista
  (Suosituksia sinulle ja Trakt-rivit) avaa taas sivuvalikon.
- Kanavalogot: kanavan nimen kirjaimet eivät enää näy logon ympärillä,
  kun logo ei täytä ruutuaan, soittimen kanavalistassa ja etusivulla.
- Elokuvat ja sarjat: nimikkeen sivun "Etsi Discoverista" hakee nimikettä
  Discover-lisäosistasi, esimerkiksi kun lähteistäsi puuttuu tuotantokausia.
  Sarjan sivu kertoo, jos lähteissäsi on vähemmän tuotantokausia kuin on
  julkaistu.
- Etusivu: kun palaat toiselta näytöltä, kohdistus palaa korttiin, jolta
  lähdit, eikä ensimmäiselle riville.
- Etusivun Trakt-korteissa lukee "Kirjastossa", kun nimike löytyy lähteistäsi.
  Kohdassa Asetukset > Etusivu > Trakt-rivit jokaisen lisätyn listan voi rajata
  näyttämään vain ne.
- Mikä tahansa julkinen Trakt-lista etusivulle: valitse Asetukset > Etusivu >
  Trakt-rivit > "Lisää Trakt-lista" ja kirjoita listan trakt.tv-osoite,
  numero tai sanoja sen nimestä, tai lähetä osoite puhelimesta. Rivi saa
  listan oman nimen.
- Myös Traktin älylistat voi lisätä osoitteella (app.trakt.tv/lists/smart/...).
- Discover: tekstityslistan avaaminen ei enää kaada sovellusta, kun sama
  tekstitys tarjotaan kahdesti.

# Sohva TV 0.2.0-beta.2

Android build **105**. Prepared 28 September 2026. Prerelease for testers.

## Changed in 0.2.0-beta.2

- Settings: switches such as Channel numbers can be reached with the remote at
  every Interface size. With Small or Smaller, or on some TV boxes, Down
  skipped them, for example from Color theme straight to Time zone.
- The same applies to every switch row in Settings and Discover (TMDB, TVmaze,
  Source in use, Ask who is watching at start, custom group genres, addon and
  catalogue switches). OK anywhere on the row flips the switch.

## Muutokset versiossa 0.2.0-beta.2

- Asetukset: kytkimet, kuten Kanavanumerot, voi nyt valita kaukosäätimellä
  kaikilla Käyttöliittymän koko -asetuksilla. Koolla Pieni tai Pienempi sekä
  joissakin TV-bokseissa alas-painike ohitti ne, esimerkiksi Väriteemasta
  suoraan Aikavyöhykkeeseen.
- Sama koskee kaikkia Asetusten ja Tutustu-osion kytkinrivejä (TMDB, TVmaze,
  Lähde käytössä, Kysy käynnistyksessä, kuka katsoo, oman ryhmän lajityypit sekä
  lisäosien ja luetteloiden kytkimet). OK missä tahansa rivillä vaihtaa kytkimen.

# Sohva TV 0.2.0-beta.1


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
checksum is in `SHA256SUMS.txt`. The release tag `v0.2.0-beta.4` names the
matching source, licensed under `GPL-3.0-only`.

The encrypted `.smbak` backup still does not include Discover data, Trakt,
service keys or resume positions. Never post addon addresses, phone QR codes,
backups or raw logs.

See [INSTALL.md](INSTALL.md), [TESTING.md](TESTING.md) and
[ADDONS.md](ADDONS.md).
