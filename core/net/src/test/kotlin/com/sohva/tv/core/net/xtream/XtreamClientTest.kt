package com.sohva.tv.core.net.xtream

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.source.XtreamAccount
import com.sohva.tv.core.net.RecordingLog
import com.sohva.tv.core.net.TEST_AGENT
import com.sohva.tv.core.net.expectError
import com.sohva.tv.core.net.http.ProviderHttp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XtreamClientTest {
    private val server = MockWebServer()
    private val log = RecordingLog()
    private lateinit var account: XtreamAccount
    private lateinit var client: XtreamClient

    @Before
    fun start() {
        server.start()
        account = XtreamAccount(server.url("/panel/").toString(), "viewer", "pa ss/1")
        client = XtreamClient(ProviderHttp(ProviderHttp.client(TEST_AGENT), log), account)
    }

    @After
    fun stop() = server.close()

    private fun json(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun take(): RecordedRequest = server.takeRequest(1, TimeUnit.SECONDS)!!

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }

    @Test
    fun accountCallAndItsFailures() {
        json("""{"user_info":{"auth":1,"username":"viewer","status":"Active","max_connections":"2","active_cons":0},"server_info":{"timezone":"Europe/Helsinki"}}""")
        assertEquals(XtreamAccountInfo("viewer", "Active", 2, 0, "Europe/Helsinki"), blocking { client.accountInfo() })
        val request = take()
        assertEquals("/panel/player_api.php", request.url.encodedPath)
        assertEquals("pa ss/1", request.url.queryParameter("password"))
        assertEquals(TEST_AGENT, request.headers["User-Agent"])

        json("""{"user_info":{"auth":0}}""")
        expectError(AppError.XtreamAuthFailed) { blocking { client.accountInfo() } }
        json("""{"server_info":{}}""")
        expectError(AppError.XtreamNoUserInfo) { blocking { client.accountInfo() } }
        json("<html>Forbidden</html>")
        expectError(AppError.XtreamResponseInvalid) { blocking { client.accountInfo() } }
        json("", code = 403)
        expectError(AppError.XtreamHttp(403)) { blocking { client.accountInfo() } }
        assertFalse(log.lines.any { "pa ss" in it || "pa+ss" in it || "pa%20ss" in it })
    }

    @Test
    fun liveStreamsWithArchiveNumbersAndSkippedItems() {
        json(
            """[
              {"stream_id":1,"name":"Yle TV1","category_id":"10","container_extension":"TS","epg_channel_id":"yle1.fi",
               "stream_icon":"http://logo.example/1.png","tv_archive":1,"tv_archive_duration":"7","num":"3","extra":{"a":[1,2]}},
              5, {"name":"no id"}, {"stream_id":"3","name":"  "},
              {"stream_id":4.0,"name":"Four","tv_archive":"true","container_extension":"bad ext!"},
              {"stream_id":"5","name":"Five","tv_archive":"1","tv_archive_duration":900,"num":null,"category_id":null}
            ]""",
        )
        val out = ArrayList<XtreamLiveStream>()
        blocking { client.liveStreams { out += it } }
        assertEquals(listOf("1", "4", "5"), out.map { it.streamId })
        assertEquals(XtreamLiveStream("1", "Yle TV1", "10", "ts", "yle1.fi", "http://logo.example/1.png", 7, 3), out[0])
        assertNull("archive without a duration has no catch-up", out[1].catchupDays)
        assertEquals("ts", out[1].extension)
        assertEquals(365, out[2].catchupDays)
        assertEquals("get_live_streams", take().url.queryParameter("action"))
    }

    @Test
    fun filmsAndSeries() {
        json("""[{"stream_id":7,"name":"Film","category_id":2,"container_extension":"mkv","stream_icon":"http://p.example/f.jpg","release_date":"2019-05-01","rating":8.1,"plot":"Plot"}]""")
        json("""[{"series_id":9,"name":"Show","cover":"http://p.example/s.jpg","backdrop_path":["http://p.example/b1.jpg","http://p.example/b2.jpg"],"year":"2021","rating":"7"}]""")
        val films = ArrayList<XtreamFilm>()
        val series = ArrayList<XtreamSeries>()
        blocking {
            client.films { films += it }
            client.series { series += it }
        }
        assertEquals(XtreamFilm("7", "Film", "2", "mkv", "http://p.example/f.jpg", 2019, "8.1", "Plot"), films.single())
        assertEquals(XtreamSeries("9", "Show", null, "http://p.example/s.jpg", "http://p.example/b1.jpg", 2021, "7", null), series.single())
    }

    @Test
    fun episodesSortedWithCleanTitles() {
        json(
            """{"info":{"name":"Show"},"episodes":{
              "2":[{"id":"21","episode_num":1,"title":"Show - S02E01 - Return","info":{"cover_big":"http://p.example/c.jpg","duration_secs":"2700"}}],
              "1":[{"id":"12","episode_num":"2","title":"Show S1 E2","container_extension":"avi"},
                   {"id":"11","episode_num":1,"title":"Pilot","info":{"movie_image":"http://p.example/m.jpg","cover":"http://p.example/x.jpg","plot":"First"}},
                   {"id":"13","title":"no number"}],
              "x":[{"id":"99","episode_num":1}]
            }}""",
        )
        val episodes = blocking { client.episodes("9") }
        assertEquals(listOf("11", "12", "21"), episodes.map { it.id })
        assertEquals(listOf("Pilot", null, "Return"), episodes.map { it.title })
        assertEquals(listOf("http://p.example/m.jpg", null, "http://p.example/c.jpg"), episodes.map { it.imageUrl })
        assertEquals(2700, episodes[2].durationSeconds)
        assertEquals("avi", episodes[1].extension)
        assertEquals("9", take().url.queryParameter("series_id"))

        json("""{"episodes":[[{"id":"1","episode_num":1}]]}""")
        assertEquals(emptyList<XtreamEpisode>(), blocking { client.episodes("9") })
        expectError(AppError.SeriesIdInvalid) { blocking { client.episodes("9&action=x") } }
    }

    @Test
    fun episodeTitleMarkers() {
        assertEquals("Return", EpisodeTitles.clean("Show - S02E01 - Return", 2, 1))
        assertEquals("Finale", EpisodeTitles.clean("show s002 e0010: Finale.", 2, 10))
        assertEquals("Show S02E011", EpisodeTitles.clean("Show S02E011", 2, 1))
        assertNull(EpisodeTitles.clean("Show S01E01", 1, 1))
        assertNull(EpisodeTitles.clean("  ", 1, 1))
    }

    @Test
    fun bulkListFallsBackToCategoriesOnHttp512() {
        json("", code = 512)
        json("""[{"category_id":"1","category_name":"News"},{"category_id":"2","category_name":"Sport"}]""")
        json("""[{"stream_id":1,"name":"A"}]""")
        json("", code = 500)
        json("""[{"stream_id":2,"name":"B"}]""")
        val out = ArrayList<String>()
        blocking { client.liveStreams { out += it.streamId } }
        assertEquals(listOf("1", "2"), out)
        val actions = (1..5).map { take().url.let { u -> u.queryParameter("action") + ":" + u.queryParameter("category_id") } }
        assertEquals(
            listOf("get_live_streams:null", "get_live_categories:null", "get_live_streams:1", "get_live_streams:2", "get_live_streams:2"),
            actions,
        )
    }

    @Test
    fun truncatedBulkListFallsBackAndAFailingCategoryFailsTheImport() {
        json("""[{"stream_id":1,"name":"A"},{"stream_id":2,""")
        json("""[{"category_id":"1","category_name":"News"}]""")
        json("""[{"stream_id":1,"name":"A"},""")
        json("", code = 512)
        val out = ArrayList<String>()
        try {
            blocking { client.liveStreams { out += it.streamId } }
            throw AssertionError("a failing category was accepted")
        } catch (e: AppException) {
            assertEquals(AppError.XtreamResponseInvalid, e.error)
        }
        assertEquals(listOf("1", "1"), out)
    }

    @Test
    fun anAnswerThatIsNotAnArrayIsInvalid() {
        json("""{"user_info":{"auth":0}}""")
        expectError(AppError.XtreamResponseInvalid) { blocking { client.films { } } }
    }

    @Test
    fun listsStreamElementByElement() {
        // 20,000 items sent slowly: the first reaches the sink long before the body ends, so the
        // array is never held whole (spec 10 §11 "never materialises the live array").
        val body = (0 until 20_000).joinToString(",", "[", "]") { """{"stream_id":$it,"name":"Channel $it"}""" }
        server.enqueue(MockResponse.Builder().body(body).throttleBody(64 * 1024, 100, TimeUnit.MILLISECONDS).build())
        val began = System.nanoTime()
        var firstAt = 0L
        var count = 0
        blocking {
            client.liveStreams {
                if (count++ == 0) firstAt = System.nanoTime()
            }
        }
        val total = System.nanoTime() - began
        assertEquals(20_000, count)
        assertTrue("first item at ${(firstAt - began) / 1_000_000} ms of ${total / 1_000_000}", firstAt - began < total / 2)
    }

    @Test
    fun streamAndGuideAddresses() {
        val urls = XtreamUrls(XtreamAccount("http://panel.example:8080/sub", "viewer", "pa ss/1"))
        assertEquals("http://panel.example:8080/sub/live/viewer/pa%20ss%2F1/42.ts", urls.live("42", "ts"))
        assertEquals("http://panel.example:8080/sub/movie/viewer/pa%20ss%2F1/7.mkv", urls.film("7", "mkv"))
        assertEquals("http://panel.example:8080/sub/series/viewer/pa%20ss%2F1/11.mp4", urls.episode("11", "mp4"))
        assertEquals("http://panel.example:8080/sub/xmltv.php?username=viewer&password=pa%20ss%2F1", urls.guide().toString())
        expectError(AppError.SourceUrlMalformed) { XtreamUrls(XtreamAccount("panel", "u", "p")) }
    }
}
