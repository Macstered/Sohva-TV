package com.sohva.tv.addons

import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class AddonMediaTransportTest {
    @Test fun headersStayOnOriginalOriginAcrossRedirectsAndChildRequests() {
        MockWebServer().use { origin -> MockWebServer().use { cdn ->
            origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", cdn.url("/delegated?cdnToken=fixture")))
            cdn.enqueue(MockResponse().setBody("video"))
            val http = AddonMediaTransport.client(origin.url("/video").toString(), mapOf("Authorization" to "Bearer fixture", "X-Api-Key" to "private", "Referer" to "https://example.invalid/private", "User-Agent" to "PrivateUA"))
            http.newCall(Request.Builder().url(origin.url("/video")).header("Range", "bytes=0-99").build()).execute().close()
            assertEquals("Bearer fixture", origin.takeRequest().getHeader("Authorization"))
            val forwarded = cdn.takeRequest()
            for (header in listOf("Authorization", "X-Api-Key", "Referer", "Cookie")) assertNull(forwarded.getHeader(header))
            assertNotEquals("PrivateUA", forwarded.getHeader("User-Agent"))
            assertEquals("bytes=0-99", forwarded.getHeader("Range"))
            origin.enqueue(MockResponse().setBody("segment"))
            http.newCall(Request.Builder().url(origin.url("/segment.ts")).build()).execute().close()
            assertEquals("private", origin.takeRequest().getHeader("X-Api-Key"))
            cdn.enqueue(MockResponse().setBody("segment"))
            http.newCall(Request.Builder().url(cdn.url("/segment.ts")).build()).execute().close()
            assertNull(cdn.takeRequest().getHeader("X-Api-Key"))
        } }
    }
    @Test fun redirectLimitAndHttpsDowngradeAreBlocked() {
        MockWebServer().use { server ->
            repeat(6) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/loop")) }
            val http = AddonMediaTransport.client(server.url("/video").toString())
            try { http.newCall(Request.Builder().url(server.url("/video")).build()).execute().close(); fail("Expected bound") }
            catch (error: IOException) { assertFalse(error.message.orEmpty().contains("http")) }
            assertEquals(6, server.requestCount)
            val secure = AddonMediaTransport.client("https://example.invalid/video", mapOf("Authorization" to "secret"))
            try { secure.newCall(Request.Builder().url(server.url("/video")).build()).execute().close(); fail("Expected downgrade rejection") }
            catch (_: IOException) { }
            assertEquals(6, server.requestCount)
        }
    }
    @Test fun subtitleDownloadHasNoMediaHeadersAndDetectsFormat(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("WEBVTT\n\n00:00:00.000 --> 00:00:03.000\nFixture\n"))
            val data = AddonSubtitleLoader().load(AddonSubtitle("id", "eng", server.url("/opaque").toString()))
            assertEquals("text/vtt", data.mimeType)
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertFalse(data.toString().contains("Fixture"))
        }
    }
    @Test fun subtitleCancellationIsPrompt(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("WEBVTT").setBodyDelay(1, TimeUnit.SECONDS))
            val job = launch { AddonSubtitleLoader().load(AddonSubtitle("id", "eng", server.url("/slow").toString())) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            withTimeout(1000) { job.cancelAndJoin() }
        }
    }
    @Test fun subtitleFormatsBoundsAndArchives() {
        assertEquals("application/x-subrip", AddonSubtitleLoader.decode("1\n00:00:00,000 --> 00:00:03,000\nFixture".toByteArray(), "eng").mimeType)
        assertEquals("text/x-ssa", AddonSubtitleLoader.decode("[Script Info]\n[Events]\n".toByteArray(), "eng").mimeType)
        for (bytes in listOf("<html>Error</html>".toByteArray(), byteArrayOf(80, 75, 3, 4))) {
            try { AddonSubtitleLoader.decode(bytes, "eng"); fail("Expected format rejection") }
            catch (error: AddonException) { assertEquals(AddonFailure.INVALID_RESPONSE, error.failure) }
        }
        try { AddonSubtitleLoader.decode(ByteArray(AddonSubtitleLoader.MAX_BYTES + 1), "eng"); fail("Expected bound") }
        catch (error: AddonException) { assertEquals(AddonFailure.RESPONSE_TOO_LARGE, error.failure) }
    }
}
