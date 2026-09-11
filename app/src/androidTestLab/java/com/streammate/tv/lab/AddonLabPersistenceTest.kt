package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.AddonEndpoint
import com.sohva.tv.addons.CachedAddonResponse
import com.sohva.tv.addons.storage.AddonDatabase
import com.sohva.tv.addons.AddonWatchIdentity
import com.sohva.tv.addons.AddonMediaKey
import com.sohva.tv.addons.AddonCatalogOrdering
import kotlinx.coroutines.flow.first
import com.streammate.tv.app.StreamMateApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run seed and verify in SEPARATE instrumentation processes, with a Lab force-stop between. */
@RunWith(AndroidJUnit4::class)
class AddonLabPersistenceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamMateApplication
    private val host get() = AddonHost.get(app, app.container)

    @Test fun seed(): Unit = runBlocking {
        check(app.packageName == "com.streammate.tv.lab")
        removeProbeRows()
        val first = host.store.install(PROFILE, AddonEndpoint.parse("https://example.invalid/PrivateColdRestartProbe/manifest.json"), MANIFEST)
        host.store.setEnabled(PROFILE, first.installationId, false)
        host.cache.put(CACHE_KEY, CachedAddonResponse("Private cached fixture", 100, 200))
        assertEquals(1, host.store.list(PROFILE).size)
        val activeProfile = app.container.preferencesRepository.preferences.first().activeProfileId
        cleanupVisibility(activeProfile)
        val catalogs = host.store.install(activeProfile, AddonEndpoint.parse("https://example.invalid/PrivateCatalogVisibilityProbe/manifest.json"), CATALOG_MANIFEST)
        host.catalogVisibility.setVisible(activeProfile, AddonCatalogOrdering.ordered(listOf(catalogs), emptyList()).first().key, false)
        val session = host.progress.begin(activeProfile, PROGRESS_IDENTITY, "Private progress fixture",
            com.sohva.tv.addons.AddonWatchArtwork("Fixture", "https://example.invalid/poster.jpg"))
        host.progress.save(session, 1, 23_000, 45_000)
        host.library.remove(activeProfile, LIBRARY_IDENTITY)
        host.library.add(activeProfile, LIBRARY_IDENTITY, com.sohva.tv.addons.AddonWatchArtwork("Private library fixture", "https://example.invalid/library-poster.jpg"), "2026")
    }
    @Test fun verifyAfterProcessDeath(): Unit = runBlocking {
        check(app.packageName == "com.streammate.tv.lab")
        assertTrue("Wrapped addon key must be durable", app.getSharedPreferences("sohva_addon_secret_envelope", 0).contains("data_key"))
        assertEquals("Private cached fixture", host.cache.get(CACHE_KEY)?.body)
        val restored = host.store.list(PROFILE).single()
        assertEquals("Cold restart fixture", restored.manifest.name)
        assertFalse(restored.enabled)
        assertEquals("https://example.invalid/PrivateColdRestartProbe/manifest.json", restored.endpoint.exportConfiguredUrl())
        assertTrue(host.store.list("${PROFILE}_other").isEmpty())
        val activeProfile = app.container.preferencesRepository.preferences.first().activeProfileId
        val catalogs = host.store.list(activeProfile).single { it.manifest.id == "test.catalog.visibility.restart" }
        val hidden = host.catalogVisibility.loadHidden(activeProfile)
        val entries = AddonCatalogOrdering.ordered(listOf(catalogs), emptyList())
        assertTrue(entries.first().key in hidden)
        assertFalse(entries.last().key in hidden)
        assertEquals(listOf("shown"), AddonCatalogOrdering.visible(listOf(catalogs), emptyList(), hidden).map { it.catalog.id })
        val progress = host.progress.get(activeProfile, PROGRESS_IDENTITY)!!
        assertEquals(23_000L, progress.resumePositionMillis)
        assertEquals("Private progress fixture", progress.title)
        assertEquals("https://example.invalid/poster.jpg", progress.artwork?.poster)
        val library = host.library.get(activeProfile, LIBRARY_IDENTITY)!!
        assertEquals("Private library fixture", library.artwork.name)
        assertEquals("https://example.invalid/library-poster.jpg", library.artwork.poster)
        assertEquals("series", library.identity.media.type)
    }
    @Test fun cleanupProbe(): Unit = runBlocking {
        check(app.packageName == "com.streammate.tv.lab")
        removeProbeRows()
        host.cache.remove(CACHE_KEY)
        assertTrue(host.store.list(PROFILE).isEmpty())
        val activeProfile = app.container.preferencesRepository.preferences.first().activeProfileId
        cleanupVisibility(activeProfile)
        host.progress.remove(activeProfile, PROGRESS_IDENTITY)
        host.library.remove(activeProfile, LIBRARY_IDENTITY)
    }
    private suspend fun cleanupVisibility(profile: String) {
        host.store.list(profile).filter { it.manifest.id == "test.catalog.visibility.restart" }.forEach { fixture ->
            AddonCatalogOrdering.ordered(listOf(fixture), emptyList()).forEach { host.catalogVisibility.setVisible(profile, it.key, true) }
            host.store.remove(profile, fixture.installationId)
        }
    }
    private suspend fun removeProbeRows() {
        val database = AddonDatabase.open(app)
        try { database.installations().list(PROFILE).forEach { database.installations().remove(PROFILE, it.installationId) } }
        finally { database.close() }
    }
    private companion object {
        const val PROFILE = "__lab_persistence_probe_20260909"
        val CACHE_KEY = "f".repeat(64)
        val PROGRESS_IDENTITY = AddonWatchIdentity("__lab_progress_probe", AddonMediaKey("movie", "fixture-progress"), AddonMediaKey("movie", "fixture-video"))
        val LIBRARY_IDENTITY = com.sohva.tv.addons.AddonLibraryIdentity("__lab_library_probe", AddonMediaKey("series", "fixture-library"))
        const val MANIFEST = """{"id":"test.cold.restart","version":"1","name":"Cold restart fixture","types":["movie"],"resources":["meta"],"catalogs":[]}"""
        const val CATALOG_MANIFEST = """{"id":"test.catalog.visibility.restart","version":"1","name":"Visibility restart fixture","types":["movie"],"resources":["catalog"],"catalogs":[{"id":"hidden","name":"Hidden fixture","type":"movie"},{"id":"shown","name":"Shown fixture","type":"movie"}]}"""
    }
}
