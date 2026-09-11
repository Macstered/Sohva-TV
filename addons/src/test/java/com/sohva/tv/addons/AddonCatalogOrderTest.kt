package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AddonCatalogOrderTest {
    private val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
    private val saved = mutableMapOf<String, List<String>>()
    private val persistence = object : AddonCatalogOrderPersistence {
        override suspend fun read(profileKey: String) = saved[profileKey].orEmpty()
        override suspend fun write(profileKey: String, catalogKeys: List<String>) { saved[profileKey] = catalogKeys.toList() }
    }
    private val access = AddonManagementAccess { it == "adult" }
    private val repository = AddonCatalogOrderRepository(store, persistence, access)
    private suspend fun install(config: String) = store.install("adult", AddonEndpoint.parse("https://example.invalid/$config/manifest.json"), manifest())
    private fun manifest(name: String = "Private name") = """{"id":"test.order","version":"1","name":"$name","types":["movie","series"],"resources":["catalog"],"catalogs":[{"id":"private-id","type":"movie","name":"Movies"},{"id":"private-id","type":"series","name":"Series"}]}"""
    private suspend fun entries() = AddonCatalogOrdering.ordered(store.list("adult"), emptyList())

    @Test fun savedOrderReopensWithoutChangingProviderPriority(): Unit = runBlocking {
        install("A"); install("B")
        val providers = store.list("adult").map { it.installationId }
        val reversed = entries().map { it.key }.reversed()
        repository.save("adult", reversed)
        val reopened = AddonCatalogOrderRepository(store, persistence, access)
        assertEquals(reversed, reopened.load("adult"))
        assertEquals(reversed, AddonCatalogOrdering.ordered(store.list("adult"), reopened.load("adult")).map { it.key })
        assertEquals(providers, store.list("adult").map { it.installationId })
        assertTrue(saved.keys.all { it.matches(Regex("[a-f0-9]{64}")) })
        assertTrue(saved.values.flatten().all { it.matches(Regex("[a-f0-9]{64}")) })
        assertFalse(entries().toString().contains("Private"))
    }
    @Test fun keysSeparateCatalogTypeAndInstallationButSurviveRefresh(): Unit = runBlocking {
        val first = install("A"); install("B")
        val before = entries().map { it.key }
        assertEquals(4, before.toSet().size)
        store.refresh("adult", first.installationId, first.revision, manifest("New name"))
        assertEquals(before, entries().map { it.key })
    }
    @Test fun disabledCatalogsKeepTheirPositionsAndNewCatalogsAppend(): Unit = runBlocking {
        val first = install("A")
        val order = entries().map { it.key }.reversed()
        repository.save("adult", order)
        store.setEnabled("adult", first.installationId, false)
        val second = install("B")
        val sorted = AddonCatalogOrdering.ordered(store.list("adult"), repository.load("adult"))
        assertEquals(order, sorted.take(2).map { it.key })
        assertTrue(sorted.take(2).all { !it.installation.enabled })
        assertTrue(sorted.drop(2).all { it.installation.installationId == second.installationId })
        store.remove("adult", first.installationId)
        assertEquals(2, AddonCatalogOrdering.ordered(store.list("adult"), order).size)
    }
    @Test fun staleMissingAndDuplicateCatalogSetsCannotOverwriteOrder(): Unit = runBlocking {
        install("A")
        val order = entries().map { it.key }
        repository.save("adult", order)
        expectFailure(AddonFailure.CONFLICT) { repository.save("adult", order.take(1)) }
        expectFailure(AddonFailure.CONFLICT) { repository.save("adult", listOf(order[0], order[0])) }
        install("B")
        expectFailure(AddonFailure.CONFLICT) { repository.save("adult", order) }
        assertEquals(order, repository.load("adult"))
    }
    @Test fun accessIsCheckedForReadingAndWriting(): Unit = runBlocking {
        install("A")
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.load("child") }
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.save("child", entries().map { it.key }) }
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.load("") }
        assertTrue(saved.isEmpty())
    }
    @Test fun malformedAndDuplicateStoredKeysAreIgnored(): Unit = runBlocking {
        install("A")
        val order = entries().map { it.key }
        repository.save("adult", order)
        saved[saved.keys.single()] = listOf("https://example.invalid/private", order[0], order[0], order[1])
        assertEquals(order, repository.load("adult"))
    }
}
