package com.sohva.tv.addons

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class AddonClientTest {
    @Test fun manifestLoadsWithCacheHints(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(aioManifest()).setHeader("Cache-Control", "max-age=120, no-store"))
            val document = AddonClient().manifest(endpoint(server))
            assertEquals("Metadata fixture", AddonManifestParser.parse(document.body).name)
            assertEquals(120, document.maxAgeSeconds)
            assertTrue(document.noStore)
            assertEquals("/Secret/manifest.json", server.takeRequest().path)
            assertFalse(document.toString().contains("Metadata fixture"))
        }
    }
    @Test fun errorsAndRedirectsDoNotExposeUrlsOrFollowAnotherServer(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("credential=Secret"))
            val error = expectFailure(AddonFailure.HTTP_ERROR) { AddonClient().manifest(endpoint(server)) }
            assertEquals(429, error.httpStatus)
            assertFalse(error.toString().contains("Secret"))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.invalid/leak/Secret"))
            expectFailure(AddonFailure.REDIRECT) { AddonClient().manifest(endpoint(server)) }
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun bothKnownAndChunkedOversizedBodiesAreRejected(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(1025)))
            server.enqueue(MockResponse().setChunkedBody("x".repeat(1025), 64))
            val client = AddonClient(maxResponseBytes = 1024)
            repeat(2) { expectFailure(AddonFailure.RESPONSE_TOO_LARGE) { client.manifest(endpoint(server)) } }
        }
    }
    @Test fun unsupportedRequestsNeverReachTheNetwork(): Unit = runBlocking {
        MockWebServer().use { server ->
            val client = AddonClient()
            val manifest = AddonManifestParser.parse(aioManifest())
            expectFailure(AddonFailure.UNSUPPORTED_RESOURCE) { client.resource(endpoint(server), manifest, "stream", "series", "tt1") }
            expectFailure(AddonFailure.INVALID_REQUEST) { client.resource(endpoint(server), manifest, "catalog", "Trakt", "mine") }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun cancellationCancelsTheCallAndReleasesCapacity(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.enqueue(MockResponse().setBody(aioManifest()))
            val client = AddonClient(concurrency = 1)
            val pending = async { client.manifest(endpoint(server)) }
            kotlinx.coroutines.yield()
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            val queued = async { client.manifest(endpoint(server)) }
            kotlinx.coroutines.yield()
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
            pending.cancelAndJoin()
            withTimeout(3000) { queued.await() }
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun slowServerHasBoundedTimeout(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            expectFailure(AddonFailure.TIMEOUT) { AddonClient(timeoutMillis = 150).manifest(endpoint(server)) }
        }
    }
    @Test fun compressedBodiesAreLimitedAfterDecompression(): Unit = runBlocking {
        MockWebServer().use { server ->
            val compressed = java.io.ByteArrayOutputStream()
            java.util.zip.GZIPOutputStream(compressed).use { it.write("x".repeat(4096).toByteArray()) }
            server.enqueue(MockResponse().setHeader("Content-Encoding", "gzip").setBody(okio.Buffer().write(compressed.toByteArray())))
            expectFailure(AddonFailure.RESPONSE_TOO_LARGE) { AddonClient(maxResponseBytes = 1024).manifest(endpoint(server)) }
        }
    }
    private fun endpoint(server: MockWebServer) = AddonEndpoint.parse(server.url("/Secret/manifest.json").toString(), true)
}
