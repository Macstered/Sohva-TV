package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AddonCatalogVisibilityTest {
    private val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
    private val saved = mutableMapOf<String, Set<String>>()
    private var allowed = true
    private var failWrite = false
    private var afterRead: () -> Unit = {}
    private val persistence = object : AddonCatalogVisibilityPersistence {
        override suspend fun read(profileKey: String): Set<String> {
            delay(1)
            return saved[profileKey].orEmpty().also { afterRead() }
        }
        override suspend fun write(profileKey: String, hidden: Set<String>) {
            if (failWrite) throw IllegalStateException("Private failure")
            saved[profileKey] = hidden.toSet()
        }
    }
    private val access = AddonManagementAccess { allowed && it in setOf("adult", "other") }
    private val repository = AddonCatalogVisibilityRepository(store, persistence, access)
    private suspend fun install(config: String, profile: String = "adult") = store.install(profile,
        AddonEndpoint.parse("https://example.invalid/$config/manifest.json"), MANIFEST)
    private suspend fun entries(profile: String = "adult") = AddonCatalogOrdering.ordered(store.list(profile), emptyList())

    @Test fun hidingOneCatalogKeepsOrderAddonCapabilitiesAndOtherCatalogs(): Unit = runBlocking {
        install("A"); install("B")
        val entries = entries()
        val order = entries.map { it.key }.reversed()
        val before = store.list("adult").map { Triple(it.installationId, it.enabled, it.revision) }
        assertTrue(repository.loadHidden("adult").isEmpty())
        val hidden = repository.setVisible("adult", entries[0].key, false)
        assertEquals(setOf(entries[0].key), hidden)
        assertEquals(order.filterNot { it in hidden }, AddonCatalogOrdering.visible(store.list("adult"), order, hidden).map { it.key })
        assertEquals(order, AddonCatalogOrdering.ordered(store.list("adult"), order).map { it.key })
        assertEquals(before, store.list("adult").map { Triple(it.installationId, it.enabled, it.revision) })
        assertTrue(store.list("adult").all { it.manifest.supports("stream", "movie", "tt123") })
        assertTrue(repository.setVisible("adult", entries[0].key, true).isEmpty())
    }

    @Test fun hiddenStateReopensAndSurvivesRefreshWhileNewCatalogsAreVisible(): Unit = runBlocking {
        val first = install("A")
        val key = entries().first().key
        repository.setVisible("adult", key, false)
        store.refresh("adult", first.installationId, first.revision, MANIFEST.replace("Private catalog", "Renamed catalog"))
        install("B")
        val reopened = AddonCatalogVisibilityRepository(store, persistence, access)
        assertEquals(setOf(key), reopened.loadHidden("adult"))
        assertEquals(3, AddonCatalogOrdering.visible(store.list("adult"), emptyList(), reopened.loadHidden("adult")).size)
        assertTrue(saved.keys.all { it.matches(Regex("[a-f0-9]{64}")) })
        assertTrue(saved.values.flatten().all { it.matches(Regex("[a-f0-9]{64}")) })
    }

    @Test fun choicesAreProfileScopedAndShowingNeverEnablesADisabledAddon(): Unit = runBlocking {
        val installed = install("A"); install("A", "other")
        val key = entries().first().key
        repository.setVisible("adult", key, false)
        assertTrue(repository.loadHidden("other").isEmpty())
        store.setEnabled("adult", installed.installationId, false)
        val hidden = repository.setVisible("adult", key, true)
        assertTrue(AddonCatalogOrdering.visible(store.list("adult"), emptyList(), hidden).isEmpty())
        assertFalse(store.list("adult").single().enabled)
    }

    @Test fun removedAndMalformedIdentitiesCannotChangeVisibility(): Unit = runBlocking {
        val installed = install("A")
        val key = entries().first().key
        expectFailure(AddonFailure.CONFLICT) { repository.setVisible("adult", "private-url", false) }
        repository.setVisible("adult", key, false)
        store.remove("adult", installed.installationId)
        expectFailure(AddonFailure.CONFLICT) { repository.setVisible("adult", key, true) }
        assertEquals(setOf(key), repository.loadHidden("adult"))
    }

    @Test fun concurrentChangesPreserveEachOtherAndCanHideEveryCatalog(): Unit = runBlocking {
        install("A")
        val keys = entries().map { it.key }
        keys.map { key -> async { repository.setVisible("adult", key, false) } }.awaitAll()
        assertEquals(keys.toSet(), repository.loadHidden("adult"))
        assertTrue(AddonCatalogOrdering.visible(store.list("adult"), emptyList(), repository.loadHidden("adult")).isEmpty())
        repository.setVisible("adult", keys[0], true)
        assertEquals(setOf(keys[1]), repository.loadHidden("adult"))
    }

    @Test fun failedWritesAreSanitizedAndLeavePreviousChoiceIntact(): Unit = runBlocking {
        install("A")
        val key = entries().first().key
        failWrite = true
        val error = expectFailure(AddonFailure.STORAGE) { repository.setVisible("adult", key, false) }
        assertFalse(error.toString().contains("Private"))
        assertNull(error.cause)
        assertTrue(repository.loadHidden("adult").isEmpty())
    }

    @Test fun accessIsCheckedBeforeAndAfterStorageSuspension(): Unit = runBlocking {
        install("A")
        val key = entries().first().key
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.loadHidden("child") }
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.setVisible("child", key, false) }
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.loadHidden("") }
        afterRead = { allowed = false }
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.loadHidden("adult") }
        allowed = true
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.setVisible("adult", key, false) }
        assertTrue(saved.isEmpty())
    }

    @Test fun malformedStoredKeysAreIgnoredAndRemovedOnNextWrite(): Unit = runBlocking {
        install("A")
        val key = entries().first().key
        repository.setVisible("adult", key, false)
        saved[saved.keys.single()] = setOf(key, "Private URL")
        assertEquals(setOf(key), repository.loadHidden("adult"))
        repository.setVisible("adult", key, true)
        assertTrue(saved.values.single().isEmpty())
    }

    companion object {
        private const val MANIFEST = """{"id":"test.visibility","version":"1","name":"Private provider","types":["movie","series"],"resources":["catalog","meta","stream","subtitles"],"catalogs":[{"id":"shared-id","type":"movie","name":"Private catalog"},{"id":"shared-id","type":"series","name":"Series"}]}"""
    }
}
