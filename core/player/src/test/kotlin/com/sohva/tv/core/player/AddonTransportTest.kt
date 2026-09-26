package com.sohva.tv.core.player

import java.io.IOException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Spec 50 ADDON-FR-95 on the media transport: redirects followed by hand at most five times, the
 * stream's headers only at their own origin, `Authorization`, `Cookie` and `Referer` never across,
 * Media3's range header on every hop.
 */
class AddonTransportTest {
    private val origin = MockWebServer()
    private val cdn = MockWebServer()
    private val client = AddonTransport.client(OkHttpClient())

    @Before
    fun start() {
        origin.start()
        cdn.start()
    }

    @After
    fun stop() {
        origin.close()
        cdn.close()
    }

    private fun request(path: String) = Request.Builder().url(origin.url(path))
        .header(AddonTransport.MARKER, "1")
        .header(AddonTransport.STREAM_HEADERS, "X-Token,Referer,Cookie")
        .header("X-Token", "t")
        .header("Referer", "https://r.example/")
        .header("Cookie", "c=1")
        .header("Range", "bytes=0-")
        .build()

    @Test
    fun streamHeadersStayAtTheirOrigin() {
        origin.enqueue(MockResponse.Builder().code(302).addHeader("Location", cdn.url("/video.mp4").toString()).build())
        cdn.enqueue(MockResponse.Builder().code(302).addHeader("Location", origin.url("/back.mp4").toString()).build())
        origin.enqueue(MockResponse.Builder().body("video").build())
        client.newCall(request("/start")).execute().use { assertEquals("video", it.body.string()) }
        val first = origin.takeRequest()
        assertEquals("t", first.headers["X-Token"])
        assertNull("the marker is never sent", first.headers[AddonTransport.MARKER])
        val atCdn = cdn.takeRequest()
        assertNull(atCdn.headers["X-Token"])
        assertNull(atCdn.headers["Referer"])
        assertNull(atCdn.headers["Cookie"])
        assertEquals("bytes=0-", atCdn.headers["Range"])
        // Back at the stream's origin, its headers apply again.
        assertEquals("t", origin.takeRequest().headers["X-Token"])
    }

    @Test
    fun atMostFiveHops() {
        repeat(6) { origin.enqueue(MockResponse.Builder().code(302).addHeader("Location", origin.url("/again").toString()).build()) }
        try {
            client.newCall(request("/start")).execute()
            throw AssertionError("expected a failure")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Too many"))
        }
        assertEquals(6, origin.requestCount)
    }

    @Test
    fun unmarkedRequestsTakeTheProviderClient() {
        var provider = 0
        var addon = 0
        val routed = AddonTransport.route(
            { r -> provider++; OkHttpClient().newCall(r) },
            { r -> addon++; OkHttpClient().newCall(r) },
        )
        routed.newCall(Request.Builder().url(origin.url("/x")).build())
        routed.newCall(Request.Builder().url(origin.url("/x")).header(AddonTransport.MARKER, "1").build())
        assertEquals(1, provider)
        assertEquals(1, addon)
    }
}
