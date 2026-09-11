package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class AddonBrowseRepositoryTest {
    private val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
    private val cache = MemoryResponseCache()
    private val access = AddonManagementAccess { it == "adult" }

    @Test fun catalogOnlyMovieResolvesInstalledCompatibleMetadataAndKeepsCanonicalId(): Unit = runBlocking {
        MockWebServer().use { server ->
            val owner = store.install("adult", AddonEndpoint.parse(server.url("/list/manifest.json").toString(), true),
                """{"id":"list","version":"1","name":"List","types":["movie"],"resources":["catalog"],"catalogs":[{"id":"mine","type":"movie","name":"Mine"}]}""")
            val metadata = install(server)
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            assertNull(repository.cachedDetails("adult", owner.installationId, AddonMediaKey("movie", "tmdb:1")))
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"meta":{"id":"tt2","type":"movie","name":"Resolved"}}"""))
            val result = repository.details("adult", owner.installationId, AddonMediaKey("movie", "tmdb:1"))
            assertEquals(AddonMediaKey("movie", "tt2"), result.value.singleVideoKey())
            assertTrue(server.takeRequest().path!!.startsWith("/Config/meta/movie/"))
            assertEquals("Resolved", repository.cachedDetails("adult", owner.installationId, AddonMediaKey("movie", "tmdb:1"))?.name)
            assertEquals(1, server.requestCount)
            store.setEnabled("adult", metadata.installationId, false)
            assertNull(repository.cachedDetails("adult", owner.installationId, AddonMediaKey("movie", "tmdb:1")))
            expectFailure(AddonFailure.UNSUPPORTED_RESOURCE) { repository.details("adult", owner.installationId, AddonMediaKey("movie", "tmdb:1")) }
            expectFailure(AddonFailure.ACCESS_DENIED) { repository.details("child", owner.installationId, AddonMediaKey("movie", "tmdb:1")) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun metadataCannotChangeMovieIntoSeriesOrOutliveItsCatalogInstallation(): Unit = runBlocking {
        MockWebServer().use { server ->
            val owner = install(server)
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            server.enqueue(MockResponse().setBody("""{"meta":{"id":"tt2","type":"series","name":"Wrong type"}}"""))
            expectFailure(AddonFailure.INVALID_RESPONSE) { repository.details("adult", owner.installationId, AddonMediaKey("movie", "tt1")) }
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    runBlocking { store.remove("adult", owner.installationId) }
                    return MockResponse().setBody("""{"meta":{"id":"tt1","type":"movie","name":"Removed"}}""")
                }
            }
            expectFailure(AddonFailure.NOT_FOUND) { repository.details("adult", owner.installationId, AddonMediaKey("movie", "tt1"), refresh = true) }
        }
    }

    @Test fun repeatVisitUsesCacheAndOfflineRefreshRetainsItems(): Unit = runBlocking {
        MockWebServer().use { server ->
            val installed = install(server)
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            server.enqueue(MockResponse().setBody(PAGE).setHeader("Cache-Control", "max-age=300"))
            assertEquals("Fixture", repository.catalog("adult", installed.installationId, "movie", "trending").value.items.single().name)
            assertFalse(repository.catalog("adult", installed.installationId, "movie", "trending").stale)
            assertTrue(repository.catalog("adult", installed.installationId, "movie", "trending").fromCache)
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(503))
            val offline = repository.catalog("adult", installed.installationId, "movie", "trending", refresh = true)
            assertTrue(offline.stale)
            assertTrue(offline.fromCache)
            assertEquals(AddonFailure.HTTP_ERROR, offline.warning)
            assertEquals(1, offline.value.items.size)
        }
    }
    @Test fun noStoreResponseRemovesOldCache(): Unit = runBlocking {
        MockWebServer().use { server ->
            val installed = install(server)
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            server.enqueue(MockResponse().setBody(PAGE))
            repository.catalog("adult", installed.installationId, "movie", "trending")
            assertEquals(1, cache.values.size)
            server.enqueue(MockResponse().setBody(PAGE).setHeader("Cache-Control", "no-store"))
            repository.catalog("adult", installed.installationId, "movie", "trending", refresh = true)
            assertTrue(cache.values.isEmpty())
        }
    }
    @Test fun noCacheMustRevalidateNeverFallsBackToStaleContent(): Unit = runBlocking {
        MockWebServer().use { server ->
            val installed = install(server)
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            server.enqueue(MockResponse().setBody(PAGE).setHeader("Cache-Control", "no-cache, must-revalidate"))
            repository.catalog("adult", installed.installationId, "movie", "trending")
            server.enqueue(MockResponse().setResponseCode(503))
            expectFailure(AddonFailure.HTTP_ERROR) { repository.catalog("adult", installed.installationId, "movie", "trending") }
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun disabledAndWrongProfileCannotReadCachedContent(): Unit = runBlocking {
        MockWebServer().use { server ->
            val installed = install(server)
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            server.enqueue(MockResponse().setBody(PAGE))
            repository.catalog("adult", installed.installationId, "movie", "trending")
            expectFailure(AddonFailure.ACCESS_DENIED) { repository.catalog("child", installed.installationId, "movie", "trending") }
            store.setEnabled("adult", installed.installationId, false)
            expectFailure(AddonFailure.NOT_FOUND) { repository.catalog("adult", installed.installationId, "movie", "trending") }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun removalDuringNetworkRequestCannotReturnOrCacheContent(): Unit = runBlocking {
        MockWebServer().use { server ->
            val installed = install(server)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    runBlocking { store.remove("adult", installed.installationId) }
                    return MockResponse().setBody(PAGE)
                }
            }
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            expectFailure(AddonFailure.NOT_FOUND) { repository.catalog("adult", installed.installationId, "movie", "trending") }
            assertTrue(cache.values.isEmpty())
        }
    }
    @Test fun requiredExtrasAreCheckedBeforeRequestAndCanonicalMetadataKeepsOriginalRequestIdentity(): Unit = runBlocking {
        MockWebServer().use { server ->
            val installed = install(server)
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            expectFailure(AddonFailure.INVALID_REQUEST) { repository.catalog("adult", installed.installationId, "Trakt", "mine") }
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"meta":{"id":"tt2","type":"movie","name":"Canonical","behaviorHints":{"defaultVideoId":"tmdb:1"}}}"""))
            val key = AddonMediaKey("movie", "tmdb:1")
            val metadata = repository.metadata("adult", installed.installationId, key).value
            assertEquals("tt2", metadata.key.id)
            assertEquals("tmdb:1", metadata.defaultVideoId)
            assertEquals("Canonical", repository.metadata("adult", installed.installationId, key).value.name)
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"meta":{"id":"tt2","type":"movie","name":"Direct"}}"""))
            assertEquals("Direct", repository.metadata("adult", installed.installationId, AddonMediaKey("movie", "tt2")).value.name)
            assertEquals(2, server.requestCount)
        }
    }
    private suspend fun install(server: MockWebServer) = store.install("adult", AddonEndpoint.parse(server.url("/Config/manifest.json").toString(), true), aioManifest())
    @Test fun focusedHeroRequiresFreshMetadataWhileHistoryCanRetainAllowedStaleArtwork(): Unit = runBlocking {
        MockWebServer().use { server ->
            val owner = install(server)
            var now = 1000L
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access, clock = { now })
            val key = AddonMediaKey("movie", "tt1")
            server.enqueue(MockResponse().setHeader("Cache-Control", "max-age=1")
                .setBody("""{"meta":{"id":"tt1","type":"movie","name":"Fixture","description":"Suomenkielinen kuvaus"}}"""))
            repository.details("adult", owner.installationId, key)
            assertNotNull(repository.cachedDetails("adult", owner.installationId, key, freshOnly = true))
            now += 2000
            assertNull(repository.cachedDetails("adult", owner.installationId, key, freshOnly = true))
            assertNotNull(repository.cachedDetails("adult", owner.installationId, key))
            assertEquals(1, server.requestCount)
            expectFailure(AddonFailure.ACCESS_DENIED) { repository.cachedDetails("child", owner.installationId, key, freshOnly = true) }
            store.setEnabled("adult", owner.installationId, false)
            expectFailure(AddonFailure.NOT_FOUND) { repository.cachedDetails("adult", owner.installationId, key, freshOnly = true) }
        }
    }
    @Test fun heroCacheCannotOverrideTheCatalogOwnersMetadataPriority(): Unit = runBlocking {
        MockWebServer().use { server ->
            val fallback = install(server)
            val owner = store.install("adult", AddonEndpoint.parse(server.url("/owner/manifest.json").toString(), true), aioManifest())
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            val key = AddonMediaKey("movie", "tt1")
            server.enqueue(MockResponse().setBody("""{"meta":{"id":"tt1","type":"movie","name":"Fallback"}}"""))
            repository.metadata("adult", fallback.installationId, key)
            assertNull(repository.cachedDetails("adult", owner.installationId, key, freshOnly = true))
            assertEquals("Fallback", repository.cachedDetails("adult", owner.installationId, key)?.name)
            server.enqueue(MockResponse().setBody("""{"meta":{"id":"tt1","type":"movie","name":"Owner"}}"""))
            assertEquals("Owner", repository.details("adult", owner.installationId, key).value.name)
            assertEquals("Owner", repository.cachedDetails("adult", owner.installationId, key, freshOnly = true)?.name)
            assertEquals(2, server.requestCount)
        }
    }
    companion object { const val PAGE = """{"metas":[{"id":"tt1","type":"movie","name":"Fixture"}]}""" }
}

internal class MemoryResponseCache : AddonResponseCache {
    val values = mutableMapOf<String, CachedAddonResponse>()
    override suspend fun get(key: String) = values[key]
    override suspend fun put(key: String, response: CachedAddonResponse) { values[key] = response }
    override suspend fun remove(key: String) { values.remove(key) }
}
