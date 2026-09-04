package com.streammate.tv.iptv.xtream

import com.streammate.tv.core.R as CoreR
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamClientTest {
    @Test
    fun `removes repeated series and episode markers from episode card titles`() {
        assertEquals("Pilot", xtreamEpisodeDisplayTitle("Example Series - S01E01 - Pilot", 1, 1))
        assertEquals("Jakso 2", xtreamEpisodeDisplayTitle("Example Series - S01E02", 1, 2))
        assertEquals("A distinct title", xtreamEpisodeDisplayTitle("A distinct title", 1, 3))
    }

    @Test
    fun `authenticates and normalizes live channels`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"user_info":{"auth":1,"username":"viewer","status":"Active","max_connections":"2","active_cons":"1"}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """[{"category_id":"10","category_name":"Sports"}]""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """[{"num":"27","stream_id":42,"name":"Arena HD","category_id":"10","epg_channel_id":"arena.fi","stream_icon":"https://images.example/arena.png","container_extension":"m3u8","tv_archive":1,"tv_archive_duration":"7"}]""",
                ),
            )
            server.start()
            val source = source(server.url("/").toString())

            val channel = XtreamClient(OkHttpClient()).liveChannels(source).single()

            assertEquals("xtream-42", channel.id)
            assertEquals("Arena HD", channel.name)
            assertEquals("Sports", channel.categoryName)
            assertEquals("arena.fi", channel.epgChannelId)
            assertEquals("42", channel.streamId)
            assertEquals(7, channel.archiveDurationDays)
            assertEquals(27, channel.playlistOrder)
            assertEquals(
                server.url("/live/viewer/secret/42.m3u8").toString(),
                channel.streamUrl,
            )
            repeat(3) {
                val request = server.takeRequest()
                assertEquals("viewer", request.requestUrl?.queryParameter("username"))
                assertEquals("secret", request.requestUrl?.queryParameter("password"))
            }
        }
    }

    @Test
    fun `loads movies series and playable episodes`() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) {
                server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"username":"viewer"}}"""))
                when (it) {
                    0 -> {
                        server.enqueue(MockResponse().setBody("""[{"category_id":"20","category_name":"Movies"}]"""))
                        server.enqueue(
                            MockResponse().setBody(
                                """[{"stream_id":71,"name":"Example Film","category_id":"20","stream_icon":"https://images.example/movie.jpg","container_extension":"mkv","year":"2024","rating":"7.5","plot":"Movie plot"}]""",
                            ),
                        )
                    }
                    1 -> {
                        server.enqueue(MockResponse().setBody("""[{"category_id":"30","category_name":"Series"}]"""))
                        server.enqueue(
                            MockResponse().setBody(
                                """[{"series_id":81,"name":"Example Series","category_id":"30","cover":"https://images.example/series.jpg","backdrop_path":["https://images.example/backdrop.jpg"],"year":"2023","rating":"8.1","plot":"Series plot"}]""",
                            ),
                        )
                    }
                }
            }
            server.enqueue(
                MockResponse().setBody(
                    """{"episodes":{"1":[{"id":"91","episode_num":2,"title":"Second episode","container_extension":"mp4","info":{"plot":"Episode plot","duration_secs":"2700","movie_image":"https://images.example/episode.jpg"}}]}}""",
                ),
            )
            server.start()
            val source = source(server.url("/").toString())
            val client = XtreamClient(OkHttpClient())

            val movie = client.movies(source).single()
            val series = client.series(source).single()
            val episode = client.seriesEpisodes(source, series.seriesId).single()

            assertEquals("Example Film", movie.name)
            assertEquals("Movies", movie.categoryName)
            assertEquals(server.url("/movie/viewer/secret/71.mkv").toString(), movie.streamUrl)
            assertEquals("Example Series", series.name)
            assertEquals("https://images.example/backdrop.jpg", series.backdropUrl)
            assertEquals(1, episode.seasonNumber)
            assertEquals(2, episode.episodeNumber)
            assertEquals(2700, episode.durationSeconds)
            assertEquals("https://images.example/episode.jpg", episode.thumbnailUrl)
            assertEquals(server.url("/series/viewer/secret/91.mp4").toString(), episode.streamUrl)
            val requests = List(7) { server.takeRequest() }
            assertEquals("81", requests.last().requestUrl?.queryParameter("series_id"))
        }
    }

    @Test
    fun `builds the provider xmltv endpoint`() {
        MockWebServer().use { server ->
            server.start()

            val url = XtreamClient(OkHttpClient()).xmlTvUrl(source(server.url("/").toString()))

            assertEquals(
                server.url("/xmltv.php?username=viewer&password=secret").toString(),
                url,
            )
        }
    }

    @Test
    fun `loads a large VOD catalogue`() = runBlocking {
        val movieCount = 10_000
        val movies = buildString {
            append('[')
            repeat(movieCount) { index ->
                if (index > 0) append(',')
                append("{\"stream_id\":$index,\"name\":\"Movie $index\",\"category_id\":\"20\"}")
            }
            append(']')
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"username":"viewer"}}"""))
            server.enqueue(MockResponse().setBody("""[{"category_id":"20","category_name":"Movies"}]"""))
            server.enqueue(MockResponse().setBody(movies))
            server.start()

            val catalogue = XtreamClient(OkHttpClient()).movies(source(server.url("/").toString()))

            assertEquals(movieCount, catalogue.size)
            assertEquals("Movie 0", catalogue.first().name)
            assertEquals("Movie 9999", catalogue.last().name)
        }
    }

    @Test
    fun `skips malformed records inside otherwise usable Xtream arrays`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"username":"viewer"}}"""))
            server.enqueue(
                MockResponse().setBody(
                    """[null,"bad",{}, {"category_id":"20","category_name":"Movies"}]""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """[null,"bad",{}, {"stream_id":71,"name":"Usable Film","category_id":"20"}]""",
                ),
            )
            server.start()

            val catalogue = XtreamClient(OkHttpClient()).movies(source(server.url("/").toString()))

            assertEquals(1, catalogue.size)
            assertEquals("Usable Film", catalogue.single().name)
            assertEquals("Movies", catalogue.single().categoryName)
        }
    }

    @Test
    fun `rejects an oversized Xtream response before parsing it`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"user_info":{"auth":1,"username":"viewer","padding":"${"x".repeat(256)}"}}""",
                ),
            )
            server.start()
            val client = XtreamClient(OkHttpClient(), maxResponseBytes = 128)

            val error = assertThrows(XtreamException::class.java) {
                runBlocking { client.authenticate(source(server.url("/").toString())) }
            }

            assertEquals(CoreR.string.error_xtream_response_too_large, error.messageResource)
            assertFalse(error.message.orEmpty().contains("secret"))
        }
    }

    @Test
    fun `rejects unauthenticated account without leaking credentials`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"user_info":{"auth":0}}"""))
            server.start()
            val source = source(server.url("/").toString())
            val client = XtreamClient(OkHttpClient())

            val error = assertThrows(XtreamException::class.java) {
                runBlocking { client.authenticate(source) }
            }

            assertFalse(error.message.orEmpty().contains("secret"))
            assertFalse(error.message.orEmpty().contains("viewer"))
        }
    }

    private fun source(baseUrl: String) = IptvSourceConfiguration(
        id = "xtream-test",
        name = "Xtream Test",
        type = IptvSourceType.XTREAM,
        xtreamBaseUrl = baseUrl,
        xtreamUsername = "viewer",
        xtreamPassword = "secret",
    )
}
