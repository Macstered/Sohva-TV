package com.sohva.tv.addons

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AddonSearchTest {
    private val access = AddonManagementAccess { it == "adult" }
    private fun owner(id: String = "one", enabled: Boolean = true, catalogs: String = """
        {"id":"movies","type":"movie","extra":[{"name":"search","isRequired":true},{"name":"skip","isRequired":true}]},
        {"id":"series","type":"series","extra":[{"name":"search"},{"name":"genre"}]},
        {"id":"filtered","type":"movie","extra":[{"name":"search"},{"name":"year","isRequired":true}]},
        {"id":"home","type":"movie"}
    """) = InstalledAddon(id, "adult", AddonEndpoint.parse("https://example.invalid/$id/manifest.json"),
        AddonManifestParser.parse("""{"id":"$id","name":"Synthetic","version":"1","types":["movie","series"],"resources":["catalog"],"catalogs":[$catalogs]}"""),
        enabled, 0, 1, 0)
    private fun media(type: String, id: String) = AddonMedia(AddonMediaKey(type, id), "Synthetic $id", null, "poster", null, null, null, emptyList())
    private fun page(vararg items: AddonMedia) = AddonBrowseResult(AddonCatalogPage(items.toList(), items.size), false)

    @Test fun searchPlansRespectCapabilitiesVisibilityAndOrder() {
        val installed = owner()
        val entries = AddonCatalogOrdering.ordered(listOf(installed), emptyList())
        val order = entries.reversed().map { it.key }
        assertEquals(listOf("series", "movies"), AddonSearch.catalogs(listOf(installed, owner("disabled", false)), order, emptySet()).map { it.catalog.id })
        assertEquals(listOf("series"), AddonSearch.catalogs(listOf(installed), emptyList(), setOf(entries[0].key)).map { it.catalog.id })
    }

    @Test fun emptyAndInvalidQueriesDoNotFetch(): Unit = runBlocking {
        var requests = 0
        val repository = AddonSearchRepository(access, { _, _, _ -> requests++; page() })
        val catalogs = AddonSearch.catalogs(listOf(owner()), emptyList(), emptySet())
        assertTrue(repository.search("adult", catalogs, " \n").toList().isEmpty())
        try { repository.search("adult", catalogs, "x".repeat(257)).toList(); fail("Expected invalid query") }
        catch (error: AddonException) { assertEquals(AddonFailure.INVALID_REQUEST, error.failure) }
        assertEquals(0, requests)
    }

    @Test fun resultsAreProgressivePartialAndUseDeclaredExtras(): Unit = runBlocking {
        val catalogs = AddonSearch.catalogs(listOf(owner()), emptyList(), emptySet())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = AddonSearchRepository(access, { profile, entry, extras ->
            assertEquals("adult", profile); assertEquals("Alien & Ä", extras["search"])
            if (entry.catalog.id == "movies") { assertEquals("0", extras["skip"]); started.complete(Unit); release.await(); throw AddonException(AddonFailure.NETWORK) }
            started.await(); assertFalse(extras.containsKey("skip")); page(media("series", "same"))
        })
        val results = repository.search("adult", catalogs, "  Alien & Ä  ").onEach { if (it.hits.isNotEmpty()) release.complete(Unit) }.toList()
        assertEquals("series", results.first().hits.single().media.key.type)
        assertEquals(AddonFailure.NETWORK, results.last().failure)
    }

    @Test fun cancellationStopsOutstandingSearchWork(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>(); val canceled = CompletableDeferred<Unit>()
        val repository = AddonSearchRepository(access, { _, entry, _ ->
            if (entry.catalog.id == "movies") try { started.complete(Unit); awaitCancellation() }
            finally { canceled.complete(Unit) }
            else { started.await(); page(media("series", "one")) }
        })
        val result = repository.search("adult", AddonSearch.catalogs(listOf(owner()), emptyList(), emptySet()), "title").first()
        assertEquals("series", result.hits.single().media.key.type)
        withTimeout(1000) { canceled.await() }
    }

    @Test fun fanOutConcurrencyAndTimeoutsAreBounded(): Unit = runBlocking {
        val installed = owner(catalogs = (1..40).joinToString(",") { """{"id":"$it","type":"movie","extra":[{"name":"search"}]}""" })
        val active = AtomicInteger(); val peak = AtomicInteger(); val count = AtomicInteger()
        val repository = AddonSearchRepository(access, { _, _, _ ->
            count.incrementAndGet(); val current = active.incrementAndGet(); peak.updateAndGet { maxOf(it, current) }
            try { delay(10); page() } finally { active.decrementAndGet() }
        })
        assertEquals(32, repository.search("adult", AddonSearch.catalogs(listOf(installed), emptyList(), emptySet()), "title").toList().size)
        assertEquals(32, count.get()); assertTrue(peak.get() <= 3); assertEquals(0, active.get())
        val timeout = AddonSearchRepository(access, { _, _, _ -> awaitCancellation() }, requestTimeoutMillis = 100, totalTimeoutMillis = 30)
        assertTrue(timeout.search("adult", AddonSearch.catalogs(listOf(installed), emptyList(), emptySet()), "title").toList()
            .let { it.size == 32 && it.all { item -> item.failure == AddonFailure.TIMEOUT } })
    }

    @Test fun revokedAccessCannotPublishInFlightResults(): Unit = runBlocking {
        var allowed = true
        val repository = AddonSearchRepository(AddonManagementAccess { allowed }, { _, _, _ -> allowed = false; page(media("movie", "private")) })
        val results = mutableListOf<AddonSearchBatch>()
        try { repository.search("adult", AddonSearch.catalogs(listOf(owner()), emptyList(), emptySet()), "title").toList(results); fail("Expected access denial") }
        catch (error: AddonException) { assertEquals(AddonFailure.ACCESS_DENIED, error.failure) }
        assertTrue(results.isEmpty())
    }

    @Test fun mergingPreservesProviderAndMediaTypeIdentityAndBoundsBothRows() {
        val first = owner(); val second = owner("two")
        val original = AddonSearchHit(first, media("movie", "same"))
        val result = AddonSearch.merge(listOf(original), listOf(AddonSearchHit(first, media("movie", "same")),
            AddonSearchHit(first, media("series", "same")), AddonSearchHit(second, media("movie", "same")), AddonSearchHit(first, media("channel", "live"))))
        assertEquals(3, result.size); assertSame(original, result.first())
        val many = listOf("movie", "series").flatMap { type -> (1..120).map { AddonSearchHit(first, media(type, it.toString())) } }
        assertEquals(200, AddonSearch.merge(emptyList(), many).size)
        assertFalse(original.toString().contains("same"))
    }
}
