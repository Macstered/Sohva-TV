package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AddonSourceRepositoryTest {
    private val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
    private val access = AddonManagementAccess { it == "adult" }
    private val key = AddonMediaKey("series", "tt123:4:opaque")

    @Test fun providersCompleteProgressivelyAndFailuresAreIndependent(): Unit = runBlocking {
        MockWebServer().use { slow -> MockWebServer().use { fast ->
            install(slow)
            val second = install(fast)
            slow.enqueue(MockResponse().setResponseCode(503).setHeadersDelay(250, TimeUnit.MILLISECONDS))
            fast.enqueue(MockResponse().setBody(STREAMS))
            val repository = AddonSourceRepository(AddonClient(), store, access)
            val flow = repository.streams("adult", key)
            assertEquals(0, fast.requestCount)
            val events = flow.toList()
            assertEquals(4, events.size)
            assertTrue(events.take(2).all { it.status == AddonSourceStatus.LOADING })
            assertEquals(second.installationId, events[2].installationId)
            assertEquals(1, events[2].items.size)
            assertEquals(AddonFailure.HTTP_ERROR, events[3].failure)
            assertEquals("tt123:4:opaque.json", fast.takeRequest().requestUrl!!.pathSegments.last())
        } }
    }
    @Test fun disabledIncompatibleAndWrongProfilesDoNotRequest(): Unit = runBlocking {
        MockWebServer().use { server ->
            val installed = install(server)
            val repository = AddonSourceRepository(AddonClient(), store, access)
            expectFailure(AddonFailure.ACCESS_DENIED) { repository.streams("child", key).toList() }
            assertTrue(repository.streams("adult", AddonMediaKey("movie", "tmdb:1")).toList().isEmpty())
            store.setEnabled("adult", installed.installationId, false)
            assertTrue(repository.streams("adult", key).toList().isEmpty())
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun removalAndRestrictionDuringRequestRejectLateResults(): Unit = runBlocking {
        MockWebServer().use { server ->
            val installed = install(server)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    runBlocking { store.remove("adult", installed.installationId) }
                    return MockResponse().setBody(STREAMS)
                }
            }
            val result = AddonSourceRepository(AddonClient(), store, access).streams("adult", key).toList().last()
            assertEquals(AddonFailure.NOT_FOUND, result.failure)
            assertTrue(result.items.isEmpty())
            install(server)
            var allowed = true
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { allowed = false; return MockResponse().setBody(STREAMS) }
            }
            expectFailure(AddonFailure.ACCESS_DENIED) {
                AddonSourceRepository(AddonClient(), store, AddonManagementAccess { allowed }).streams("adult", key).toList()
            }
        }
    }
    @Test fun subtitlesOnlySendProtocolExtrasAndAreNeverResponseCached(): Unit = runBlocking {
        MockWebServer().use { server ->
            install(server)
            val repository = AddonSourceRepository(AddonClient(), store, access)
            repeat(2) {
                server.enqueue(MockResponse().setBody("""{"subtitles":[{"id":"x","lang":"eng","url":"https://example.invalid/s.srt"}]}""").setHeader("Cache-Control", "max-age=300"))
                val result = repository.subtitles("adult", key, mapOf("filename" to "a b.mkv")).toList().last()
                assertEquals(1, result.items.size)
                val request = server.takeRequest()
                assertNull(request.getHeader("Authorization"))
                assertTrue(request.path!!.contains("filename=a%20b.mkv"))
            }
            expectFailure(AddonFailure.INVALID_REQUEST) { repository.subtitles("adult", key, mapOf("Authorization" to "secret")).toList() }
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun cancellationDoesNotBecomeProviderFailure(): Unit = runBlocking {
        MockWebServer().use { server ->
            install(server)
            server.enqueue(MockResponse().setBody(STREAMS).setBodyDelay(1, TimeUnit.SECONDS))
            val results = mutableListOf<AddonSourceResult<AddonStream>>()
            val job = launch { AddonSourceRepository(AddonClient(), store, access).streams("adult", key).collect { results.add(it) } }
            withContext(Dispatchers.IO) { checkNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            job.cancelAndJoin()
            assertTrue(results.all { it.status == AddonSourceStatus.LOADING })
        }
    }
    private suspend fun install(server: MockWebServer) = store.install("adult", AddonEndpoint.parse(server.url("/manifest.json").toString(), true),
        """{"id":"fixture","version":"1","name":"Fixture","types":["movie","series"],"idPrefixes":["tt"],"resources":["stream","subtitles"],"catalogs":[]}""")
    companion object { const val STREAMS = """{"streams":[{"name":"Fixture","url":"https://example.invalid/video"}]}""" }
}
