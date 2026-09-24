package com.sohva.tv.core.net.http

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.net.RecordingLog
import com.sohva.tv.core.net.TEST_AGENT
import com.sohva.tv.core.net.expectError
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderHttpTest {
    private val server = MockWebServer()
    private val log = RecordingLog()
    private val http = ProviderHttp(ProviderHttp.client(TEST_AGENT), log)

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun text(url: String, request: ProviderRequest = ProviderRequest.SOURCE, limited: ProviderHttp = http): String =
        runBlocking { limited.get(url, request) { it.readUtf8() } }

    @Test
    fun everyRequestCarriesTheAppAgent() {
        server.enqueue(MockResponse.Builder().body("#EXTM3U").build())
        assertEquals("#EXTM3U", text(server.url("/list.m3u").toString()))
        assertEquals(TEST_AGENT, server.takeRequest().headers["User-Agent"])
        assertEquals("Sohva TV/0.1.0-beta.23 (Android TV 11)", ProviderHttp.userAgent("0.1.0-beta.23", "11"))
    }

    @Test
    fun httpErrorsNameTheirStatusWithoutTheAddress() {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(503).build())
        val url = server.url("/get.php").newBuilder()
            .addQueryParameter("username", "viewer")
            .addQueryParameter("password", "hunter2")
            .build()
            .toString()
        expectError(AppError.HttpStatus(404)) { text(url) }
        expectError(AppError.XtreamHttp(503)) { text(url, ProviderRequest.XTREAM) }
        assertTrue(log.lines.first().endsWith(": HTTP 404"))
        assertFalse(log.lines.any { "hunter2" in it || "viewer" in it })
    }

    @Test
    fun gzipBodiesAreRecognisedByTheirBytes() {
        val gz = Buffer()
        GzipSink(gz).buffer().use { it.writeUtf8("<tv></tv>") }
        server.enqueue(MockResponse.Builder().body(gz).build())
        assertEquals("<tv></tv>", text(server.url("/guide.xml.gz").toString()))
    }

    @Test
    fun runawayBodiesStop() {
        val small = ProviderHttp(ProviderHttp.client(TEST_AGENT), log, maxBodyBytes = 100)
        server.enqueue(MockResponse.Builder().body("x".repeat(1_000)).build())
        server.enqueue(MockResponse.Builder().chunkedBody("x".repeat(1_000), 64).build())
        val bomb = Buffer()
        GzipSink(bomb).buffer().use { it.writeUtf8("0".repeat(10_000)) }
        server.enqueue(MockResponse.Builder().body(bomb).build())
        server.enqueue(MockResponse.Builder().body("x".repeat(1_000)).build())
        val url = server.url("/big").toString()
        expectError(AppError.SourceResponseTooLarge) { text(url, limited = small) }
        expectError(AppError.SourceResponseTooLarge) { text(url, limited = small) }
        expectError(AppError.SourceResponseTooLarge) { text(url, limited = small) }
        expectError(AppError.XtreamResponseTooLarge) { text(url, ProviderRequest.XTREAM, small) }
    }

    @Test
    fun transportFailuresHideHostAndAddress() {
        val url = server.url("/list.m3u").newBuilder().addQueryParameter("password", "hunter2").build().toString()
        server.close()
        try {
            text(url)
        } catch (e: AppException) {
            val detail = (e.error as AppError.TransportFailed).detail.orEmpty()
            for (secret in listOf("hunter2", "127.0.0.1", server.hostName)) assertFalse(detail, detail.contains(secret, ignoreCase = true))
            assertTrue(log.lines.single().endsWith(": no response"))
            return
        }
        throw AssertionError("no failure")
    }

    @Test
    fun unusableAddressesAreMalformed() {
        expectError(AppError.SourceUrlMalformed) { text("not an address") }
    }

    @Test
    fun cancellingStopsABlockedRead() = runBlocking {
        server.enqueue(MockResponse.Builder().body("x".repeat(100_000)).throttleBody(10, 1, TimeUnit.SECONDS).build())
        val url = server.url("/slow").toString()
        val began = System.nanoTime()
        val job = launch(Dispatchers.IO) { http.get(url, ProviderRequest.SOURCE) { it.readUtf8() } }
        delay(300)
        job.cancel()
        withTimeout(5_000) { job.join() }
        assertTrue(job.isCancelled)
        assertTrue(System.nanoTime() - began < TimeUnit.SECONDS.toNanos(5))
    }
}
