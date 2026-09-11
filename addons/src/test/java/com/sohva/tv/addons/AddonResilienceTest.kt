package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AddonResilienceTest {
    private val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
    private val cache = MemoryResponseCache()
    private val access = AddonManagementAccess { it == "adult" }
    private suspend fun install(server: MockWebServer) = store.install("adult",
        AddonEndpoint.parse(server.url("/Configured/manifest.json").toString(), true), aioManifest())

    @Test fun expiredConfigurationIsNotDisguisedAsOfflineAndCannotReuseCachedPage(): Unit = runBlocking {
        for (status in listOf(401, 403, 404, 410)) MockWebServer().use { server ->
            val addon = install(server)
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            server.enqueue(MockResponse().setBody(AddonBrowseRepositoryTest.PAGE).setHeader("Cache-Control", "max-age=300"))
            repository.catalog("adult", addon.installationId, "movie", "trending")
            server.enqueue(MockResponse().setResponseCode(status))
            expectFailure(AddonFailure.HTTP_ERROR) { repository.catalog("adult", addon.installationId, "movie", "trending", refresh = true) }
            server.enqueue(MockResponse().setResponseCode(status))
            expectFailure(AddonFailure.HTTP_ERROR) { repository.catalog("adult", addon.installationId, "movie", "trending") }
            assertEquals(3, server.requestCount)
            assertTrue(cache.values.isEmpty())
        }
    }

    @Test fun rateLimitAndTemporaryOutagesRetainAllowedCacheWithoutRetryStorm(): Unit = runBlocking {
        for (status in listOf(408, 425, 429, 500, 502, 503, 504)) MockWebServer().use { server ->
            val addon = install(server)
            val repository = AddonBrowseRepository(AddonClient(), store, cache, access)
            server.enqueue(MockResponse().setBody(AddonBrowseRepositoryTest.PAGE))
            repository.catalog("adult", addon.installationId, "movie", "trending")
            server.enqueue(MockResponse().setResponseCode(status).setHeader("Retry-After", "60"))
            val result = repository.catalog("adult", addon.installationId, "movie", "trending", refresh = true)
            assertTrue(result.stale && result.fromCache)
            assertEquals(AddonFailure.HTTP_ERROR, result.warning)
            assertEquals(1, result.value.items.size)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun profileRevocationDuringCacheWriteCannotReturnFreshContent(): Unit = runBlocking {
        MockWebServer().use { server ->
            val addon = install(server)
            var allowed = true
            val revokingCache = object : AddonResponseCache {
                override suspend fun get(key: String): CachedAddonResponse? = null
                override suspend fun put(key: String, response: CachedAddonResponse) { allowed = false }
                override suspend fun remove(key: String) = Unit
            }
            val repository = AddonBrowseRepository(AddonClient(), store, revokingCache, AddonManagementAccess { allowed })
            server.enqueue(MockResponse().setBody(AddonBrowseRepositoryTest.PAGE))
            expectFailure(AddonFailure.ACCESS_DENIED) { repository.catalog("adult", addon.installationId, "movie", "trending") }
        }
    }
}
