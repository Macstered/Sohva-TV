package com.streammate.tv.core.network

import com.streammate.tv.core.R as CoreR
import com.streammate.tv.core.error.storedFailureMessage
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GuideSourceClientTest {
    private val userAgent = "Sohva TV/1.0 (Android TV 12)"

    @Test
    fun `introduces the app by name rather than by the HTTP library`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("#EXTM3U\n"))
            val client = GuideSourceClient(OkHttpClient(), userAgent)

            val body = runBlocking {
                client.withSource(server.url("/list.m3u").toString()) { it.readBytes().decodeToString() }
            }

            assertEquals("#EXTM3U\n", body)
            assertEquals(userAgent, server.takeRequest().getHeader("User-Agent"))
        }
    }

    @Test
    fun `an HTTP error carries its code, and is stored with it`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("<html>forbidden</html>"))
            val client = GuideSourceClient(OkHttpClient(), userAgent)

            val error = assertThrows(GuideSourceException::class.java) {
                runBlocking { client.withSource(server.url("/list.m3u").toString()) { it.readBytes() } }
            }

            assertEquals(CoreR.string.error_source_http, error.messageResource)
            assertEquals(listOf(403), error.messageArguments)
            assertEquals("resource:${CoreR.string.error_source_http}\t403", error.storedFailureMessage())
        }
    }
}
