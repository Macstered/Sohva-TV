package com.sohva.tv.feature.trakt

import com.sohva.tv.feature.trakt.protocol.TraktHttp
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The Shield crash of 27 Sept 2026: sign-in started on the main thread read Trakt's TLS answer
 * there (NetworkOnMainThreadException). The body must be read on OkHttp's own thread, whatever
 * thread the caller is on.
 */
class TraktHttpThreadTest {
    @Test
    fun theBodyIsNeverReadOnTheCallersThread() {
        val server = MockWebServer()
        // Larger than a socket buffer, so reading it takes real reads after the headers.
        server.enqueue(MockResponse.Builder().body(Buffer().write(ByteArray(512 * 1024) { 'a'.code.toByte() })).build())
        server.start()
        var readOn: String? = null
        val base = OkHttpClient.Builder().eventListener(
            object : EventListener() {
                override fun responseBodyEnd(call: Call, byteCount: Long) {
                    readOn = Thread.currentThread().name
                }
            },
        ).build()
        val caller = Executors.newSingleThreadExecutor { Thread(it, "caller") }
        try {
            val size = runBlocking {
                withContext(caller.asCoroutineDispatcher()) {
                    TraktHttp(base, 10, 1024L * 1024).call(Request.Builder().url(server.url("/")).build()).body.size
                }
            }
            assertEquals(512L * 1024, size)
            // Coroutine debug mode names the thread "caller @coroutine#n".
            assertFalse("body read on $readOn", readOn.orEmpty().startsWith("caller"))
        } finally {
            caller.shutdown()
            server.close()
        }
    }
}
