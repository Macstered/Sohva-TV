# Sohva TV privacy policy

Updated 29 September 2026. Applies to Sohva TV `0.2.0-beta.1` and later
builds until it is replaced.

Suomenkielinen versio on tämän asiakirjan lopussa:
[Tietosuoja suomeksi](#tietosuoja-suomeksi).

Sohva TV plays media sources that you set up yourself. It contains no
channels or media and does not sell an IPTV subscription.

## What the app does not do

- There is no Sohva TV server. Nothing is sent to the developer.
- No account with the developer, no analytics, no advertising, no telemetry and
  no crash reports.
- Reports reach the developer only when you email them yourself.

## What stays on the TV

The app stores these on the TV: your sources and their logins, your TMDB and
API-Sports keys, Trakt sign-in tokens, Discover addon addresses, the guide and
library caches, profiles, favourites, recent channels, locks and allowed
groups, the parental PIN, channel edits and logos, channel lists, organisation
rules, watch progress, reminders, Sohva Sport choices and settings.

**Credentials are encrypted.** Source addresses and logins, stream addresses,
the parental PIN, the TMDB and API-Sports keys, Trakt tokens and pending
Trakt updates, and Discover addon addresses and data are encrypted with a key
protected by the Android Keystore. Other data, such as caches, profiles and
watch progress, is not separately encrypted by the app.

**Nothing is backed up to Google.** Android's automatic cloud backup and
device-to-device transfer are turned off for all app data. The only portable
copy is the `.smbak` backup you save yourself. It is encrypted with your own
password and is never uploaded anywhere. It does not contain Discover data,
Trakt, the TMDB or API-Sports keys, watch progress or reminders.

Local data stays until you clear it, replace it with a restore, or uninstall
the app. Caches expire or are replaced when they are refreshed. A backup file
stays wherever you saved it until you delete it.

### The update from beta 23

On the first start after updating from beta 23, the app reads beta 23's data
on the TV and moves it into its new storage. Nothing leaves the TV during this
step. When everything has come across, beta 23's old files and keys are
deleted from the TV.

## Who the app contacts, and when

The TV connects directly to each service below, and only when you use the
feature. Each service receives the request and your IP address and handles
them under its own privacy policy. The developer receives no copy.

| Service | When | What it receives |
| --- | --- | --- |
| Your IPTV providers (playlist, guide, Xtream, stream, catch-up and logo hosts) | Importing, refreshing, testing and playing | The requests, including the logins you set up. Streams identify the app as `Sohva TV/<version>` unless your playlist sets its own user agent. |
| [TMDB](https://www.themoviedb.org) | Only when you turn it on with your own key | Titles, years, type, season and episode numbers, language, and TMDB or IMDb ids |
| [TVmaze](https://www.tvmaze.com) | Only when you turn it on | Series titles, show ids, season and episode numbers |
| [API-Sports](https://api-sports.io) | Only with your own key, while Sohva Sport or the score ticker is on screen | The date, your chosen time zone, and competition and season ids |
| [Trakt](https://trakt.tv) | After you connect a profile, or when you add Trakt charts or public lists to Home | See "Trakt" below |
| Discover addons you installed, and their media and subtitle hosts | Browsing and playing in Discover; subtitles for films and episodes from your playlists | Catalog and search requests, filters, title ids and subtitle lookups |
| [Stremio](https://www.stremio.com) | Only when you choose Copy from Stremio account | The sign-in link flow and one read of your addon list |
| [GitHub](https://github.com/Macstered/Sohva-TV) | GitHub version only: update check once a day and when you press Check for updates; downloads only when you press Download. The Google Play version never contacts GitHub; Google Play updates it | The request itself, nothing about you or your sources |

TMDB, TVmaze, API-Sports, Trakt, Stremio, GitHub and Discover addon manifests
are used over HTTPS only. Plain HTTP is allowed only for your own IPTV
sources. Over HTTP, your login, guide and viewing can be seen by others on the
same network. Prefer HTTPS and a network you trust.

## Trakt (optional)

Trakt is off until you connect an account under **Settings > Accounts**. You
approve the TV on Trakt's own page; the TV never sees your Trakt password. The
sign-in tokens are stored encrypted, per Sohva profile.

While connected, the app sends Trakt what you play in the movie and series
libraries and in Discover: the title's TMDB or IMDb id, season and episode,
and how far you are as a percentage, when playback starts, pauses and stops.
Nothing is sent for Live TV or catch-up, or for titles without an id. The app
reads back your playback progress, watched history, recommendations and the
next episodes of shows you watch, and keeps a copy on the TV to draw progress
bars and watched ticks. When TMDB is on, synopses for Trakt titles are asked
from TMDB by id.

Trakt rows on Home are separate from an account. Charts (trending, popular,
most anticipated, box office) and public lists you add under **Settings > Home**
are read from Trakt without signing in, so nothing about you is sent: only the
list asked for. Your watchlist rows use the profile's connected account.

**Disconnect** deletes the tokens and the local copy from this TV. It does not
change your history on Trakt. Removing a Sohva profile also disconnects its
Trakt account. Restricted profiles cannot use Trakt.

## Discover addons (optional)

Discover connects directly to the addons you install and to the hosts they
name. Addons may keep their own logs; some treat a subtitle request as
watching. The app asks for subtitles only after a stream has started or when
you open the subtitle picker, and sends only the file name, size and hash of
the stream.

Subtitle addons can also give subtitles to films and episodes from your own
playlists. Then they receive only the title's IMDb id (with season and
episode), when playback starts without a subtitle in your preferred language
or when you open the subtitle list; never your provider's address, the file's
name or your login.

Addon addresses often carry an account token. They are stored encrypted and
never appear in logs or diagnostics. Redirects are followed only to HTTPS
addresses and never to local network addresses. Copy from Stremio account does
not store the temporary sign-in key and does not change your Stremio account.
Restricted profiles cannot use Discover.

## Setting up from a phone

The optional phone setup pages are served by the TV itself, not by a website.
They work only while their screen is open and use a random one-time link. The
page for sources and channel logos closes after 15 minutes; the page for
Discover addons closes after 10 minutes. They use plain HTTP on your local
network, so use a network you trust and keep the QR code private. What you send
goes only to the TV.

## Diagnostics file

**Settings > About > Save diagnostics** writes a text file only when you choose
it, to a place you pick. It holds the app version, device model, Android
version, language, time zones, screen size, main settings, your sources by the
names you gave them with their refresh states, and the app's recent event
lines. Addresses are cut down to the provider's host name, and user names,
passwords, keys and tokens are removed. The app never sends the file anywhere.
Sharing it is your choice.

## Reminders

A reminder is stored on the TV with the programme or match title, its channel
and start time. It uses the TV's alarm service and notification panel, and
nothing leaves the TV. Allowing "display over other apps" in the TV's own
settings is optional; it only lets a due reminder bring Sohva TV to the front.

## Children and parental controls

Parental controls work only on the TV. They do not create an account or send
anything about a child. Restricted profiles cannot use Discover or Trakt. This
is an access limit, not an age rating of content from providers or addons.
The app is not designed to collect personal information from children.

## Contact and changes

Questions, privacy requests and reports:
[hello@luontra.fi](mailto:hello@luontra.fi). If you email, the developer
receives your address and what you choose to include. Do not include
passwords, playlist addresses, addon addresses or backups.

Downloads from GitHub follow GitHub's own privacy practices, and installs and
updates from Google Play follow Google's. Changes to this
policy are published with the app's release documents.

## Tietosuoja suomeksi

Päivitetty 29.9.2026. Koskee Sohva TV:n versiota `0.2.0-beta.1` ja sitä
uudempia versioita, kunnes tämä seloste korvataan.

Sohva TV toistaa mediaa lähteistä, jotka olet itse lisännyt. Sovelluksessa
ei ole kanavia eikä mediaa, eikä se myy IPTV-tilausta.

### Mitä sovellus ei tee

- Sohva TV:llä ei ole omaa palvelinta. Kehittäjälle ei lähetetä mitään.
- Kehittäjällä ei ole käyttäjätiliä, analytiikkaa, mainontaa, telemetriaa
  eikä kaatumisraportteja.
- Raportit tulevat kehittäjälle vain, jos lähetät ne itse sähköpostilla.

### Mitä televisioon tallennetaan

Sovellus tallentaa televisioon: lähteesi ja niiden kirjautumistiedot, TMDB- ja
API-Sports-avaimesi, Traktin kirjautumistunnisteet, Discover-lisäosien
osoitteet, ohjelmaoppaan ja kirjastojen välimuistit, profiilit, suosikit,
viimeksi katsotut kanavat, lukitukset ja sallitut ryhmät, lapsilukon
PIN-koodin, kanavamuokkaukset ja logot, kanavalistat, järjestyssäännöt,
katselukohdat, muistutukset, Sohva Sportin valinnat ja asetukset.

**Kirjautumistiedot salataan.** Lähteiden osoitteet ja kirjautumistiedot,
striimien osoitteet, lapsilukon PIN-koodi, TMDB- ja API-Sports-avaimet,
Traktin tunnisteet ja lähettämättömät Trakt-päivitykset sekä
Discover-lisäosien osoitteet ja tiedot salataan avaimella, jota Androidin
Keystore suojaa. Muita tietoja, kuten välimuisteja, profiileja ja
katselukohtia, sovellus ei salaa erikseen.

**Mitään ei varmuuskopioida Googlelle.** Androidin automaattinen
pilvivarmuuskopio ja laitteelta toiselle siirto on poistettu käytöstä kaikilta
sovelluksen tiedoilta. Ainoa siirrettävä kopio on `.smbak`-varmuuskopio, jonka
tallennat itse. Se salataan omalla salasanallasi, eikä sitä lähetetä minnekään.
Siinä ei ole Discover-tietoja, Traktia, TMDB- tai API-Sports-avaimia,
katselukohtia eikä muistutuksia.

Tiedot säilyvät televisiossa, kunnes tyhjennät ne, korvaat ne palauttamalla
varmuuskopion tai poistat sovelluksen. Välimuistit vanhenevat tai korvautuvat,
kun ne päivitetään. Varmuuskopiotiedosto säilyy siellä, minne sen tallensit,
kunnes poistat sen.

#### Päivitys beta 23:sta

Kun sovellus käynnistyy ensimmäisen kerran beta 23:n päälle asennettuna, se
lukee beta 23:n tiedot televisiosta ja siirtää ne uuteen tallennuspaikkaan.
Mitään ei lähetetä televisiosta tämän aikana. Kun kaikki on siirretty, beta
23:n vanhat tiedostot ja avaimet poistetaan televisiosta.

### Mihin sovellus ottaa yhteyttä ja milloin

Televisio ottaa yhteyttä suoraan alla oleviin palveluihin, ja vain silloin kun
käytät kyseistä toimintoa. Jokainen palvelu saa pyynnön ja IP-osoitteesi ja
käsittelee ne oman tietosuojakäytäntönsä mukaan. Kehittäjä ei saa niistä
kopiota.

| Palvelu | Milloin | Mitä palvelu saa |
| --- | --- | --- |
| IPTV-palveluntarjoajasi (soittolista, ohjelmaopas, Xtream, striimit, catch-up ja logot) | Tuonti, päivitys, testaus ja toisto | Pyynnöt ja niissä antamasi kirjautumistiedot. Striimipyynnöt tunnistavat sovelluksen nimellä `Sohva TV/<versio>`, ellei soittolista määritä omaa user agentia. |
| [TMDB](https://www.themoviedb.org) | Vain kun otat sen käyttöön omalla avaimellasi | Nimet, vuodet, tyyppi, kausi- ja jaksonumerot, kieli sekä TMDB- tai IMDb-tunnisteet |
| [TVmaze](https://www.tvmaze.com) | Vain kun otat sen käyttöön | Sarjojen nimet, sarjatunnisteet, kausi- ja jaksonumerot |
| [API-Sports](https://api-sports.io) | Vain omalla avaimellasi, kun Sohva Sport tai tulosnauha on näytöllä | Päivämäärä, valitsemasi aikavyöhyke sekä sarja- ja kausitunnisteet |
| [Trakt](https://trakt.tv) | Kun olet yhdistänyt profiilin, tai kun lisäät Traktin listauksia tai julkisia listoja etusivulle | Katso kohta "Trakt" alla |
| Asentamasi Discover-lisäosat sekä niiden media- ja tekstityspalvelimet | Selaus ja toisto Discoverissa; tekstitykset soittolistojesi elokuviin ja jaksoihin | Luettelo- ja hakupyynnöt, suodattimet, nimiketunnisteet ja tekstityshaut |
| [Stremio](https://www.stremio.com) | Vain kun valitset Kopioi Stremio-tililtä | Kirjautumislinkin vaiheet ja lisäosaluettelosi lukeminen kerran |
| [GitHub](https://github.com/Macstered/Sohva-TV) | Vain GitHub-versio: päivitysten tarkistus kerran päivässä ja kun painat Tarkista päivitykset; lataus vain kun painat Lataa. Google Play -versio ei ota yhteyttä GitHubiin; Google Play päivittää sen | Pelkkä pyyntö, ei mitään sinusta tai lähteistäsi |

TMDB:tä, TVmazea, API-Sportsia, Traktia, Stremiota, GitHubia ja
Discover-lisäosien manifesteja käytetään vain HTTPS-yhteydellä. Salaamaton
HTTP on sallittu vain omille IPTV-lähteillesi. HTTP-yhteydellä
kirjautumistietosi, ohjelmaopas ja katselusi voivat näkyä muille samassa
verkossa. Suosi HTTPS:ää ja luotettavaa verkkoa.

### Trakt (valinnainen)

Trakt on pois käytöstä, kunnes yhdistät tilin kohdassa **Asetukset > Tilit**.
Hyväksyt television Traktin omalla sivulla; televisio ei koskaan näe
Trakt-salasanaasi. Kirjautumistunnisteet tallennetaan salattuina,
Sohva-profiileittain.

Kun yhteys on päällä, sovellus kertoo Traktille, mitä toistat elokuva- ja
sarjakirjastoista ja Discoverista: nimikkeen TMDB- tai IMDb-tunnisteen, kauden
ja jakson sekä kohdan prosentteina, kun toisto alkaa, pysähtyy tauolle tai
loppuu. Suorista lähetyksistä, catch-upista ja nimikkeistä, joilla ei ole
tunnistetta, ei lähetetä mitään. Sovellus lukee Traktista keskeneräiset
katselusi, katseluhistorian, suositukset ja seuraamiesi sarjojen seuraavat
jaksot, ja pitää niistä kopion televisiossa edistymispalkkeja ja
katsottu-merkkejä varten. Kun TMDB on käytössä, Trakt-nimikkeiden
juonikuvaukset haetaan TMDB:stä tunnisteen perusteella.

Etusivun Trakt-rivit eivät vaadi tiliä. Listaukset (trendaavat, suositut,
odotetuimmat, elokuvateatterien kärki) ja julkiset listat, jotka lisäät kohdassa
**Asetukset > Etusivu**, luetaan Traktista kirjautumatta, joten sinusta ei
lähetetä mitään: vain pyydetty lista. Katselulistarivit käyttävät profiilin
yhdistettyä tiliä.

**Katkaise yhteys** poistaa tunnisteet ja paikallisen kopion tästä
televisiosta. Se ei muuta historiaasi Traktissa. Sohva-profiilin poistaminen
katkaisee myös sen Trakt-yhteyden. Rajoitetut profiilit eivät voi käyttää
Traktia.

### Discover-lisäosat (valinnainen)

Discover ottaa yhteyttä suoraan asentamiisi lisäosiin ja palvelimiin, jotka ne
ilmoittavat. Lisäosat voivat pitää omia lokejaan; jotkin tulkitsevat
tekstityspyynnön katseluksi. Sovellus hakee tekstityksiä vasta, kun striimi on
alkanut tai kun avaat tekstitysvalikon, ja lähettää vain striimin
tiedostonimen, koon ja tarkistussumman.

Tekstityslisäosat voivat antaa tekstityksiä myös omien soittolistojesi
elokuviin ja jaksoihin. Silloin ne saavat vain nimikkeen IMDb-tunnisteen (sekä
kauden ja jakson), kun toisto alkaa ilman tekstitystä toivomallasi kielellä tai
kun avaat tekstityslistan; eivät koskaan palveluntarjoajasi osoitetta,
tiedoston nimeä tai kirjautumistietojasi.

Lisäosien osoitteissa on usein tilin tunniste. Ne tallennetaan salattuina,
eivätkä ne näy lokeissa tai vianmääritystiedoissa. Uudelleenohjauksia
seurataan vain HTTPS-osoitteisiin eikä koskaan lähiverkon osoitteisiin.
Kopioi Stremio-tililtä ei tallenna väliaikaista kirjautumisavainta eikä muuta
Stremio-tiliäsi. Rajoitetut profiilit eivät voi käyttää Discoveria.

### Asetukset puhelimesta

Valinnaiset puhelinasetussivut tulevat televisiosta itsestään, eivät
verkkosivustolta. Ne toimivat vain, kun niiden näkymä on auki, ja käyttävät
satunnaista kertakäyttöistä linkkiä. Lähteiden ja kanavalogojen sivu sulkeutuu
15 minuutin kuluttua, Discover-lisäosien sivu 10 minuutin kuluttua. Sivut
käyttävät salaamatonta HTTP-yhteyttä kotiverkossasi, joten käytä luotettavaa
verkkoa ja pidä QR-koodi omana tietonasi. Lähettämäsi tiedot menevät vain
televisioon.

### Vianmääritystiedosto

**Asetukset > Tietoja > Tallenna vianmääritystiedot** kirjoittaa
tekstitiedoston vain, kun valitset sen, ja valitsemaasi paikkaan. Tiedostossa
on sovelluksen versio, laitteen malli, Android-versio, kieli, aikavyöhykkeet,
näytön koko, tärkeimmät asetukset, lähteesi antamillasi nimillä ja niiden
päivitysten tila sekä sovelluksen viimeisimmät tapahtumarivit. Osoitteista
jätetään vain palveluntarjoajan palvelimen nimi, ja käyttäjänimet, salasanat,
avaimet ja tunnisteet poistetaan. Sovellus ei lähetä tiedostoa minnekään.
Jakaminen on sinun päätöksesi.

### Muistutukset

Muistutus tallennetaan televisioon ohjelman tai ottelun nimen, kanavan ja
alkamisajan kanssa. Se käyttää television hälytyspalvelua ja
ilmoituspaneelia, eikä mitään lähetetä televisiosta. Television omista
asetuksista annettava lupa näkyä muiden sovellusten päällä on vapaaehtoinen;
sen avulla erääntyvä muistutus voi tuoda Sohva TV:n näytölle.

### Lapset ja lapsilukko

Lapsilukko toimii vain televisiossa. Se ei luo tiliä eikä lähetä mitään
lapsesta. Rajoitetut profiilit eivät voi käyttää Discoveria eikä Traktia.
Kyse on käyttörajoituksesta, ei palveluntarjoajien tai lisäosien sisällön
ikärajaluokittelusta. Sovellusta ei ole tehty keräämään lasten
henkilötietoja.

### Yhteystiedot ja muutokset

Kysymykset, tietosuojapyynnöt ja raportit:
[hello@luontra.fi](mailto:hello@luontra.fi). Jos lähetät sähköpostia,
kehittäjä saa sähköpostiosoitteesi ja sen, mitä päätät kertoa. Älä lähetä
salasanoja, soittolistojen osoitteita, lisäosien osoitteita tai
varmuuskopioita.

GitHubista ladattaessa noudatetaan GitHubin omia tietosuojakäytäntöjä ja
Google Playsta asennettaessa ja päivitettäessä Googlen käytäntöjä.
Tämän selosteen muutokset julkaistaan sovelluksen julkaisuasiakirjojen
mukana.
