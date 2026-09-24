package com.sohva.tv.core.sync

import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.RefreshKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueAndXtreamTest {
    private val h = SyncHarness()

    @After
    fun close() = h.close()

    private fun sync(id: String, vararg kinds: RefreshKind) = runBlocking { h.runner.sync(id, kinds.toSet()).join() }

    private fun status(kind: String) = h.query("SELECT status, error_code, item_count FROM source_status WHERE kind = '$kind'").single()

    @Test
    fun m3uCatalogueSplitsFilmsSeriesAndEpisodes() {
        h.addM3u(scope = ImportScope.VOD)
        h.serve(
            "/list.m3u",
            """
            #EXTM3U
            #EXTINF:-1 group-title="Movies" tvg-logo="http://p.example/f.jpg",The Film (2019)
            http://s.example/film.mkv
            #EXTINF:-1 group-title="Series" tvg-logo="http://p.example/s1.jpg",Show S01E02 - Second
            http://s.example/s1e2.mkv
            #EXTINF:-1 group-title="Series",Show S01E01
            http://s.example/s1e1.mkv
            #EXTINF:-1 group-title="Series",Other 2x03
            http://s.example/o.mkv
            #EXTINF:-1 group-title="Live",Live Channel
            http://s.example/live.ts
            """.trimIndent(),
        )
        sync("m3u-1", RefreshKind.CATALOGUE)
        assertEquals("Two films and two series", "success|null|4", status("catalogue"))
        assertEquals(listOf("The Film (2019)|2019|Movies", "Live Channel|null|Live"),
            h.query("SELECT m.name, m.year, g.name FROM movie m JOIN content_group g ON g.id = m.group_id ORDER BY m.provider_order"))
        assertEquals(listOf("Show|http://p.example/s1.jpg", "Other|null"), h.query("SELECT name, poster_url FROM series ORDER BY provider_order"))
        assertEquals(listOf("Show|1|1|S01E01", "Show|1|2|Second", "Other|2|3|S02E03"),
            h.query("SELECT s.name, e.season, e.number, e.name FROM episode e JOIN series s ON s.id = e.series_id ORDER BY s.provider_order, e.season, e.number"))

        h.serve("/list.m3u", "#EXTM3U\n#EXTINF:-1 group-title=\"Movies\",The Film (2019)\nhttp://s.example/film.mkv\n")
        sync("m3u-1", RefreshKind.CATALOGUE)
        assertEquals("success|null|1", status("catalogue"))
        assertEquals(listOf("0", "0"), listOf(h.query("SELECT COUNT(*) FROM series").single(), h.query("SELECT COUNT(*) FROM episode").single()))
        h.serve("/list.m3u", "#EXTM3U\n")
        sync("m3u-1", RefreshKind.CATALOGUE)
        assertEquals("failed|catalogue_empty|1", status("catalogue"))
    }

    private fun xtreamPanel(live: String, films: String = "[]", series: String = "[]") {
        h.serve("/panel/player_api.php") {
            val action = h.requests.last().substringAfter("action=", "").substringBefore('&')
            val body = when (action) {
                "" -> """{"user_info":{"auth":1},"server_info":{"timezone":"Europe/Helsinki"}}"""
                "get_live_categories" -> """[{"category_id":"1","category_name":"News"}]"""
                "get_vod_categories" -> """[{"category_id":"5","category_name":"Films"}]"""
                "get_series_categories" -> """[{"category_id":"9","category_name":"Shows"}]"""
                "get_live_streams" -> live
                "get_vod_streams" -> films
                "get_series" -> series
                else -> "[]"
            }
            mockwebserver3.MockResponse.Builder().body(body).build()
        }
    }

    @Test
    fun xtreamLiveUpdatesInPlaceAndRefusesAnEmptyAnswer() {
        h.addXtream()
        xtreamPanel("""[{"stream_id":1,"name":"One","category_id":"1","epg_channel_id":"one.fi","tv_archive":1,"tv_archive_duration":3,"num":5},
            {"stream_id":2,"name":"Two","category_id":"1"}]""")
        sync("xtream-1", RefreshKind.PLAYLIST)
        assertEquals("success|null|2", status("playlist"))
        assertEquals(listOf("xtream-1:xtream-1|One|xtream|3|Europe/Helsinki|5|News", "xtream-1:xtream-2|Two|null|null|Europe/Helsinki|null|News"),
            h.query("SELECT c.key, c.name, c.catchup_type, c.catchup_days, c.catchup_tz, c.number, g.name FROM channel c JOIN content_group g ON g.id = c.group_id ORDER BY c.key"))
        val sealed = h.query("SELECT stream_url_enc FROM channel WHERE name = 'One'").single()
        assertTrue(sealed, sealed.endsWith("/panel/live/viewer/secret/1.ts"))

        sync("xtream-1", RefreshKind.PLAYLIST)
        assertEquals(listOf("1", "1"), h.query("SELECT generation FROM channel"))

        xtreamPanel("[]")
        sync("xtream-1", RefreshKind.PLAYLIST)
        assertEquals("failed|playlist_empty|2", status("playlist"))
        assertEquals(2, h.query("SELECT COUNT(*) FROM channel").single().toInt())
    }

    @Test
    fun xtreamCatalogueAndItsGuideAddress() {
        h.addXtream()
        xtreamPanel(
            live = "[]",
            films = """[{"stream_id":7,"name":"Film","category_id":"5","container_extension":"mkv","rating":"7,5","year":"2020"}]""",
            series = """[{"series_id":9,"name":"Show","category_id":"9","backdrop_path":["http://p.example/b.jpg"]}]""",
        )
        h.serve("/panel/xmltv.php", "<tv></tv>")
        sync("xtream-1", RefreshKind.CATALOGUE, RefreshKind.EPG)
        assertEquals("success|null|2", status("catalogue"))
        assertEquals(listOf("Film|75|2020|Films"), h.query("SELECT m.name, m.rating_x10, m.year, g.name FROM movie m JOIN content_group g ON g.id = m.group_id"))
        assertEquals(listOf("Show|http://p.example/b.jpg"), h.query("SELECT name, backdrop_url FROM series"))
        val guide = h.requests.single { it.startsWith("/panel/xmltv.php?") }
        assertTrue(guide, "username=viewer" in guide && guide.endsWith("=secret"))
    }

    @Test
    fun theXmltvFieldOverridesAnXtreamGuide() {
        val getPhp = h.server.url("/panel/get.php").newBuilder()
            .addQueryParameter("username", "viewer")
            .addQueryParameter("password", "secret")
            .build()
        h.addM3u(playlist = getPhp.toString(), guide = "/own-guide.xml")
        xtreamPanel("""[{"stream_id":1,"name":"One","epg_channel_id":"one.fi"}]""")
        h.serve("/own-guide.xml", "<tv></tv>")
        sync("m3u-1", RefreshKind.PLAYLIST, RefreshKind.EPG)
        assertEquals("get.php is imported through the Xtream API", listOf("m3u-1:xtream-1"), h.query("SELECT key FROM channel"))
        assertTrue("/own-guide.xml" in h.requests)
        assertTrue(h.requests.none { it.startsWith("/panel/xmltv.php") })
    }
}
