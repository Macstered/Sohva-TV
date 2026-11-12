package com.sohva.tv.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.sohva.tv.core.model.player.StreamReply
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The first reply of a stream is classified for the diagnostics; its bytes reach the player unchanged. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReplyInspectionTest {
    private val server = MockWebServer()
    private val other = MockWebServer()
    private val registry = StreamRegistry("Sohva TV/test (Android TV 11)")
    private val factory = InspectingDataSource.Factory(
        ResolvingDataSource.Factory(OkHttpDataSource.Factory(OkHttpClient()), registry.resolver),
        registry,
    )

    @Before
    fun start() {
        server.start()
        other.start()
    }

    @After
    fun stop() {
        server.close()
        other.close()
    }

    private fun stream(address: String) = ResolvedStream(
        key = "fixture-0:c1", address = address, sourceId = "fixture-0", sourceName = "Fixture",
        connectionLimit = 1, title = "Northstar", userAgent = null, referrer = null,
    )

    private fun readAll(uri: Uri): String {
        val source = factory.createDataSource()
        source.open(DataSpec(uri))
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(7)
        while (true) {
            val n = source.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, n)
        }
        source.close()
        return out.toString(Charsets.UTF_8.name())
    }

    @Test
    fun aPlaylistAtAPlainAddressIsNamedAndPassesThroughUnchanged() {
        val body = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\nseg/0.ts\n"
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl; charset=utf-8").body(body).build())
        val placeholder = registry.register(stream(server.url("/live/user/secret/1").toString()))
        assertEquals(body, readAll(placeholder))
        val reply = registry.lastReply()!!
        assertEquals(StreamReply.Kind.PLAYLIST, reply.kind)
        assertEquals("application/vnd.apple.mpegurl", reply.contentType)
        assertFalse(reply.redirected)
        assertFalse(reply.describe().contains("secret"))
    }

    @Test
    fun aWebPageAfterARedirectToAnotherHostIsNamed() {
        // localhost and 127.0.0.1 are different host names for the same test server.
        other.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/html").body("<html>Service unavailable</html>").build())
        val target = other.url("/page").newBuilder().host(if (other.hostName == "127.0.0.1") "localhost" else "127.0.0.1").build()
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", target.toString()).build())
        val placeholder = registry.register(stream(server.url("/movie/user/secret/7.mkv").toString()))
        readAll(placeholder)
        val reply = registry.lastReply()!!
        assertEquals(StreamReply.Kind.WEB_PAGE, reply.kind)
        assertEquals("text/html", reply.contentType)
        assertTrue(reply.redirected)
    }

    @Test
    fun onlyTheStreamsFirstRequestIsInspectedAndANewStreamForgetsIt() {
        server.enqueue(MockResponse.Builder().body("Max connections reached").build())
        val placeholder = registry.register(stream(server.url("/live/user/secret/2.ts").toString()))
        readAll(placeholder)
        assertEquals(StreamReply.Kind.TEXT, registry.lastReply()!!.kind)
        // A segment or a later range request is the player's own business.
        server.enqueue(MockResponse.Builder().body("<html/>").build())
        readAll(Uri.parse(server.url("/seg/0.ts").toString()))
        assertEquals(StreamReply.Kind.TEXT, registry.lastReply()!!.kind)
        registry.register(stream(server.url("/live/user/secret/3.ts").toString()))
        assertNull(registry.lastReply())
        registry.clear()
        assertNull(registry.lastReply())
    }

    @Test
    fun aPlaylistAtAPlainAddressIsRetriedOnceAndOnlyThen() {
        val asked = ArrayList<String>()
        registry.onPlaylistReply = { asked += it }
        server.enqueue(MockResponse.Builder().body(PLAYLIST).build())
        val placeholder = registry.register(stream(server.url("/live/user/secret/4").toString()))
        assertFalse(registry.claimPlaylistRetry())
        readAll(placeholder)
        assertTrue(registry.claimPlaylistRetry())
        assertFalse(registry.claimPlaylistRetry())
        assertEquals(listOf(placeholder.toString()), asked)
        // An address that already says HLS, or a reply that is no playlist, is not retried.
        server.enqueue(MockResponse.Builder().body(PLAYLIST).build())
        readAll(registry.register(stream(server.url("/live/user/secret/5.m3u8").toString())))
        assertFalse(registry.claimPlaylistRetry())
        server.enqueue(MockResponse.Builder().body("<html/>").build())
        readAll(registry.register(stream(server.url("/live/user/secret/6").toString())))
        assertFalse(registry.claimPlaylistRetry())
        assertEquals(1, asked.size)
    }

    private companion object {
        val PLAYLIST: String = listOf("#EXTM3U", "seg/0.ts", "").joinToString(System.lineSeparator())
    }
}
