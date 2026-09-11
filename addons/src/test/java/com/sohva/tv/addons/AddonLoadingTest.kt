package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AddonLoadingTest {
    @Test fun repeatedAccessChecksReuseDecodedManifestsButReadEveryCurrentRow(): Unit = runBlocking {
        val persistence = MemoryAddonPersistence(); val decrypts = AtomicInteger()
        val cipher = object : AddonSecretCipher {
            val delegate = TestAddonCipher()
            override fun encrypt(plaintext: String) = delegate.encrypt(plaintext)
            override fun decrypt(ciphertext: String): String { decrypts.incrementAndGet(); return delegate.decrypt(ciphertext) }
        }
        val store = EncryptedAddonStore(persistence, cipher)
        val installed = store.install("adult", AddonEndpoint.parse("https://example.invalid/manifest.json"), MANIFEST)
        decrypts.set(0)
        repeat(100) { assertEquals(installed.revision, store.list("adult").single().revision) }
        assertEquals("Unchanged rows must not decrypt/parse again on every access check", 0, decrypts.get())
        // Changes through ANOTHER store simulate an out-of-band DB update, not a local invalidation.
        val other = EncryptedAddonStore(persistence, cipher)
        other.setEnabled("adult", installed.installationId, false)
        assertFalse(store.list("adult").single().enabled)
        val disabled = store.list("adult").single()
        other.refresh("adult", disabled.installationId, disabled.revision, MANIFEST.replace("Fixture", "Changed"))
        assertEquals("Changed", store.list("adult").single().manifest.name)
        other.remove("adult", installed.installationId)
        assertTrue(store.list("adult").isEmpty())
        assertTrue(store.list("other").isEmpty())
    }

    @Test fun homePreviewParsesOnlyRequestedTitlesButKeepsRawPaginationCount() {
        val page = """{"metas":[null,${(1..1000).joinToString { """{"id":"$it","type":"movie","name":"Title $it"}""" }}]}"""
        expectFailure(AddonFailure.RESPONSE_TOO_LARGE) { AddonMediaParser.catalog(page, 20) }
        val bounded = page.replace("null,", "")
        assertEquals(20, AddonMediaParser.catalog(bounded, 20).items.size)
        assertEquals(1000, AddonMediaParser.catalog(bounded, 20).receivedCount)
        assertEquals(1000, AddonMediaParser.catalog(bounded).items.size)
    }

    @Test fun expiredEligibleCatalogIsAvailableWithoutNetworkAndRevalidates(): Unit = runBlocking {
        MockWebServer().use { server ->
            var now = 1000L
            val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
            val owner = store.install("adult", AddonEndpoint.parse(server.url("/manifest.json").toString(), true), MANIFEST)
            val cache = MemoryResponseCache()
            val repository = AddonBrowseRepository(AddonClient(), store, cache, AddonManagementAccess { it == "adult" }, { now })
            server.enqueue(MockResponse().setHeader("Cache-Control", "max-age=1").setBody(PAGE))
            repository.catalog("adult", owner.installationId, "movie", "catalog")
            now = 3000
            assertTrue(repository.cachedCatalog("adult", owner.installationId, "movie", "catalog")!!.stale)
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setBody(PAGE.replace("Old", "New")))
            assertEquals("New", repository.catalog("adult", owner.installationId, "movie", "catalog", itemLimit = 20).value.items.single().name)
            assertEquals("New", repository.cachedCatalog("adult", owner.installationId, "movie", "catalog")!!.value.items.single().name)
            assertEquals(2, server.requestCount)
            expectFailure(AddonFailure.ACCESS_DENIED) { repository.cachedCatalog("child", owner.installationId, "movie", "catalog") }
            store.setEnabled("adult", owner.installationId, false)
            expectFailure(AddonFailure.NOT_FOUND) { repository.cachedCatalog("adult", owner.installationId, "movie", "catalog") }
        }
    }

    @Test fun forbiddenExpiredTooOldAndInvalidatedSnapshotsAreNeverPreviewed(): Unit = runBlocking {
        MockWebServer().use { server ->
            var now = 1000L
            val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
            val owner = store.install("adult", AddonEndpoint.parse(server.url("/manifest.json").toString(), true), MANIFEST)
            val cache = MemoryResponseCache()
            val repository = AddonBrowseRepository(AddonClient(), store, cache, AddonManagementAccess { true }, { now })
            for (rule in listOf("no-store", "no-cache", "max-age=0, must-revalidate")) {
                server.enqueue(MockResponse().setHeader("Cache-Control", rule).setBody(PAGE))
                repository.catalog("adult", owner.installationId, "movie", "catalog", refresh = true)
                assertNull(repository.cachedCatalog("adult", owner.installationId, "movie", "catalog"))
            }
            server.enqueue(MockResponse().setHeader("Cache-Control", "max-age=1").setBody(PAGE))
            repository.catalog("adult", owner.installationId, "movie", "catalog", refresh = true)
            now = 0
            assertNull(repository.cachedCatalog("adult", owner.installationId, "movie", "catalog"))
            now = 86_402_000L
            assertNull(repository.cachedCatalog("adult", owner.installationId, "movie", "catalog"))
            now = 3000
            server.enqueue(MockResponse().setResponseCode(401))
            expectFailure(AddonFailure.HTTP_ERROR) { repository.catalog("adult", owner.installationId, "movie", "catalog") }
            assertNull(repository.cachedCatalog("adult", owner.installationId, "movie", "catalog"))
        }
    }

    @Test fun savedPreviewRechecksAccessAfterCacheRead(): Unit = runBlocking {
        MockWebServer().use { server ->
            val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
            val owner = store.install("adult", AddonEndpoint.parse(server.url("/manifest.json").toString(), true), MANIFEST)
            val memory = MemoryResponseCache()
            var disableDuringRead = false
            val cache = object : AddonResponseCache by memory {
                override suspend fun get(key: String): CachedAddonResponse? {
                    val saved = memory.get(key)
                    if (disableDuringRead) store.setEnabled("adult", owner.installationId, false)
                    return saved
                }
            }
            val repository = AddonBrowseRepository(AddonClient(), store, cache, AddonManagementAccess { true })
            server.enqueue(MockResponse().setBody(PAGE))
            repository.catalog("adult", owner.installationId, "movie", "catalog")
            disableDuringRead = true
            expectFailure(AddonFailure.NOT_FOUND) { repository.cachedCatalog("adult", owner.installationId, "movie", "catalog") }
            assertEquals(1, server.requestCount)
        }
    }

    private companion object {
        const val MANIFEST = """{"id":"test.loading","version":"1","name":"Fixture","types":["movie"],"resources":["catalog"],"catalogs":[{"id":"catalog","type":"movie","name":"Catalog"}]}"""
        const val PAGE = """{"metas":[{"id":"title","type":"movie","name":"Old"}]}"""
    }
}
