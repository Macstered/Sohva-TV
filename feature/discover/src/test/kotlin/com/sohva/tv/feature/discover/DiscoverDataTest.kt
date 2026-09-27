package com.sohva.tv.feature.discover

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.feature.discover.cache.ResponseCache
import com.sohva.tv.feature.discover.data.AddonBrowser
import com.sohva.tv.feature.discover.data.AddonManager
import com.sohva.tv.feature.discover.net.AddonClient
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.store.Artwork
import com.sohva.tv.feature.discover.store.CatalogPrefs
import com.sohva.tv.feature.discover.store.DiscoverDatabase
import com.sohva.tv.feature.discover.store.InstallationStore
import com.sohva.tv.feature.discover.store.LibraryStore
import com.sohva.tv.feature.discover.store.LibraryTitle
import com.sohva.tv.feature.discover.store.ProgressStore
import com.sohva.tv.feature.discover.store.WatchIdentity
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 50 §11 "Unit" for the client, store, cache and browsing: a real `discover.db` in memory,
 * a fake addon server, the software cipher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiscoverDataTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DiscoverDatabase::class.java).allowMainThreadQueries().build()
    private val server = MockWebServer()
    private val cipher = FakeCipher()
    private val clock = FakeClock()
    private val access = FakeAccess()
    private val cacheDir: File = Files.createTempDirectory("addon-cache").toFile()
    private val store = InstallationStore(db.installations(), cipher, clock)
    private val client = AddonClient(OkHttpClient())
    private val manager = AddonManager(access, store, client, Dispatchers.Unconfined) { true }
    private val cache = ResponseCache(cacheDir, cipher, clock)
    private val browser = AddonBrowser(access, store, client, cache, clock, Dispatchers.Unconfined)

    /** Answers by path; [status] and [cacheControl] per path can be changed by a test. */
    private val answers = HashMap<String, () -> MockResponse>()
    private val requests = ArrayList<String>()

    private fun manifest(name: String = "Example", configRequired: Boolean = false) = """
        {"id":"org.example","version":"1","name":"$name","types":["movie","series"],"resources":["catalog","meta","stream"],
         "catalogs":[{"type":"movie","id":"top","name":"Top","extra":[{"name":"skip"},{"name":"genre","options":["Drama"]}]},{"type":"series","id":"new","name":"New"}],
         "behaviorHints":{"configurationRequired":$configRequired}}
    """.trimIndent()

    private fun ok(body: String, cacheControl: String? = "max-age=600") = MockResponse.Builder().body(body).apply { cacheControl?.let { addHeader("Cache-Control", it) } }.build()

    private val catalog = """{"metas":[${(1..30).joinToString(",") { """{"type":"movie","id":"tt$it","name":"Movie $it"}""" }}]}"""

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                synchronized(requests) { requests += path }
                return answers[path]?.invoke() ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        answers["/cfg/manifest.json"] = { ok(manifest()) }
        answers["/cfg/catalog/movie/top.json"] = { ok(catalog) }
        answers["/cfg/meta/movie/tt1.json"] = { ok("""{"meta":{"type":"movie","id":"tt1","name":"Movie 1","description":"Details"}}""") }
    }

    @After
    fun stop() {
        server.close()
        db.close()
        cacheDir.deleteRecursively()
    }

    private fun url(path: String = "/cfg/manifest.json") = server.url(path).toString()

    private fun failure(block: suspend () -> Unit): AddonFailure = runBlocking {
        try {
            block()
            fail("expected a failure")
            error("unreachable")
        } catch (e: AddonException) {
            e.failure
        }
    }

    // ---- Client (FR-25…28) ----

    @Test
    fun theClientFollowsOnlyTrustedRedirectsAndRefusesOversizeBodies() = runBlocking {
        answers["/moved"] = { MockResponse.Builder().code(302).addHeader("Location", url("/cfg/manifest.json")).build() }
        answers["/away"] = { MockResponse.Builder().code(302).addHeader("Location", "http://192.0.2.9/cfg/manifest.json").build() }
        answers["/loop"] = { MockResponse.Builder().code(302).addHeader("Location", url("/loop")).build() }
        answers["/big"] = { ok("x".repeat((2 * 1024 * 1024) + 1)) }
        answers["/chunked"] = { MockResponse.Builder().chunkedBody("y".repeat((2 * 1024 * 1024) + 10), 8_192).build() }
        // Decision "Addon redirects": followed within the rule, by hand.
        client.get(server.url("/moved"))
        assertEquals(listOf("/moved", "/cfg/manifest.json"), requests)
        requests.clear()
        // Plain HTTP to another host is refused before any request goes there.
        assertEquals(AddonFailure.REDIRECT, failure { client.get(server.url("/away")) })
        assertEquals(listOf("/away"), requests)
        requests.clear()
        // At most three hops.
        assertEquals(AddonFailure.REDIRECT, failure { client.get(server.url("/loop")) })
        assertEquals(4, requests.size)
        assertEquals(AddonFailure.RESPONSE_TOO_LARGE, failure { client.get(server.url("/big")) })
        assertEquals(AddonFailure.RESPONSE_TOO_LARGE, failure { client.get(server.url("/chunked")) })
        assertEquals(AddonFailure.HTTP_ERROR, failure { client.get(server.url("/missing")) })
        answers["/slow"] = { ok("{}").newBuilder().headersDelay(20, TimeUnit.SECONDS).build() }
        val started = System.nanoTime()
        assertEquals(AddonFailure.TIMEOUT, failure { client.get(server.url("/slow")) })
        assertTrue((System.nanoTime() - started) / 1_000_000 < 17_000)
        // A message never carries the URL.
        assertEquals("HTTP_ERROR 404", runCatching { client.get(server.url("/missing?secret=1")) }.exceptionOrNull()!!.message)
    }

    // ---- Store and manager (FR-29…33) ----

    @Test
    fun installStoresEncryptedAndDeduplicates() = runBlocking {
        val a = manager.install("p1", url("/cfg/"))
        assertEquals("Example", a.name)
        assertEquals(a.id, manager.install("p1", url("/cfg/manifest.json")).id)
        assertEquals(1, requests.count { it == "/cfg/manifest.json" })
        // The same manifest with another configuration is another installation.
        answers["/other/manifest.json"] = { ok(manifest()) }
        val b = manager.install("p1", url("/other"))
        assertFalse(a.id == b.id)
        // At rest: no URL, no manifest name in the row.
        val row = db.installations().of("p1").first()
        assertFalse(row.payload.contains("cfg") || row.payload.contains("Example"))
        // Concurrent installs of one URL keep one identity.
        answers["/race/manifest.json"] = { ok(manifest()) }
        val ids = (1..4).map { async(Dispatchers.IO) { manager.install("p1", url("/race/")).id } }.awaitAll()
        assertEquals(1, ids.toSet().size)
        answers["/conf/manifest.json"] = { ok(manifest(configRequired = true)) }
        assertEquals(AddonFailure.CONFIGURATION_REQUIRED, failure { manager.install("p1", url("/conf/")) })
    }

    @Test
    fun revisionsGuardRefreshAndRemoval() = runBlocking {
        val a = manager.install("p1", url("/cfg/"))
        answers["/cfg/manifest.json"] = { ok(manifest(name = "Renamed")) }
        val refreshed = manager.refresh("p1", a.id)
        assertEquals("Renamed", refreshed.name)
        assertEquals(2L, refreshed.revision)
        // A write bound to the old revision is refused; a removed addon is not resurrected.
        assertEquals(AddonFailure.CONFLICT, failure { store.update(a, enabled = false) })
        manager.remove("p1", a.id)
        assertEquals(AddonFailure.CONFLICT, failure { store.update(refreshed, enabled = false) })
        assertTrue(store.list("p1").isEmpty())
        // A failed refresh keeps the last good manifest.
        val c = manager.install("p1", url("/cfg/"))
        answers["/cfg/manifest.json"] = { MockResponse.Builder().code(500).build() }
        assertEquals(AddonFailure.HTTP_ERROR, failure { manager.refresh("p1", c.id) })
        assertEquals("Renamed", store.find("p1", c.id)!!.name)
    }

    @Test
    fun priorityAndExactReorder() = runBlocking {
        for (n in listOf("a", "b", "c")) answers["/$n/manifest.json"] = { ok(manifest(name = n)) }
        val (a, b, c) = listOf("a", "b", "c").map { manager.install("p1", url("/$it/")) }
        manager.raise("p1", c.id)
        assertEquals(listOf("a", "c", "b"), store.list("p1").map { it.name })
        assertEquals(AddonFailure.CONFLICT, failure { manager.reorder("p1", listOf(a.id, b.id)) })
        manager.reorder("p1", listOf(b.id, c.id, a.id))
        assertEquals(listOf("b", "c", "a"), store.list("p1").map { it.name })
    }

    @Test
    fun restrictedOrInactiveProfilesAreDenied() = runBlocking {
        access.restricted += "p1"
        assertEquals(AddonFailure.ACCESS_DENIED, failure { manager.install("p1", url("/cfg/")) })
        assertEquals(AddonFailure.ACCESS_DENIED, failure { manager.list("p2") })
        assertTrue(requests.isEmpty())
    }

    // ---- Catalog order and visibility (FR-51…54) ----

    @Test
    fun orderAndVisibilitySurviveRefreshAndRejectStaleSets() = runBlocking {
        val prefs = CatalogPrefs(db.catalogs())
        answers["/b/manifest.json"] = { ok(manifest(name = "B")) }
        val a = manager.install("p1", url("/cfg/"))
        val b = manager.install("p1", url("/b/"))
        val keys = prefs.ordered("p1", store.list("p1")).map { it.key }
        assertEquals(4, keys.size)
        prefs.saveOrder("p1", store.list("p1"), keys.reversed())
        prefs.setHidden("p1", store.list("p1"), keys[0], true)
        manager.refresh("p1", a.id)
        val after = prefs.ordered("p1", store.list("p1"))
        assertEquals(keys.reversed(), after.map { it.key })
        assertTrue(after.last().hidden)
        assertEquals(AddonFailure.CONFLICT, failure { prefs.saveOrder("p1", store.list("p1"), keys.drop(1)) })
        assertEquals(AddonFailure.CONFLICT, failure { prefs.setHidden("p1", store.list("p1"), "f".repeat(64), true) })
        // A disabled addon keeps its catalogs' places but they are not visible.
        manager.setEnabled("p1", b.id, false)
        assertEquals(keys.reversed(), prefs.ordered("p1", store.list("p1")).map { it.key })
        assertEquals(1, prefs.visible("p1", store.list("p1")).size)
        // A new catalog appends after the saved ones.
        answers["/c/manifest.json"] = { ok(manifest(name = "C")) }
        manager.install("p1", url("/c/"))
        assertEquals(keys.reversed(), prefs.ordered("p1", store.list("p1")).map { it.key }.take(4))
        // Another profile has its own order.
        access.active = "p2"
        assertTrue(prefs.ordered("p2", store.list("p2")).isEmpty())
    }

    // ---- Browsing and the cache (FR-60, -72, -122…124) ----

    @Test
    fun freshCacheStaleFallbackAndInvalidation() = runBlocking {
        val a = manager.install("p1", url("/cfg/"))
        val top = a.manifest.catalog("movie", "top")!!
        val first = browser.catalog("p1", a, top, emptyMap(), 20)
        assertEquals(20, first.value.items.size)
        assertEquals(30, first.value.receivedCount)
        browser.catalog("p1", a, top, emptyMap(), 20)
        assertEquals("fresh: one request", 1, requests.count { it == "/cfg/catalog/movie/top.json" })
        // The shelf preview is read without network.
        assertEquals(20, browser.shelfPreview("p1", a, top)!!.page.items.size)
        // Expired + network down: the saved page stands in, marked stale.
        clock.now += 700_000
        answers["/cfg/catalog/movie/top.json"] = { MockResponse.Builder().code(503).build() }
        val stale = browser.catalog("p1", a, top, emptyMap(), 20)
        assertTrue(stale.stale)
        // A 404 means an expired configuration: the saved copy is removed and the error shown.
        answers["/cfg/catalog/movie/top.json"] = { MockResponse.Builder().code(404).build() }
        assertEquals(AddonFailure.HTTP_ERROR, failure { browser.catalog("p1", a, top, emptyMap(), 20) })
        answers["/cfg/catalog/movie/top.json"] = { MockResponse.Builder().code(503).build() }
        assertEquals(AddonFailure.HTTP_ERROR, failure { browser.catalog("p1", a, top, emptyMap(), 20) })
        // A one-day-old preview is no longer offered.
        clock.now += 25 * 60 * 60_000L
        assertNull(browser.shelfPreview("p1", a, top))
    }

    @Test
    fun noStoreAndNoCacheAreNeverServedStale() = runBlocking {
        val a = manager.install("p1", url("/cfg/"))
        val top = a.manifest.catalog("movie", "top")!!
        answers["/cfg/catalog/movie/top.json"] = { ok(catalog, "no-cache") }
        browser.catalog("p1", a, top, emptyMap(), 20)
        answers["/cfg/catalog/movie/top.json"] = { MockResponse.Builder().code(503).build() }
        assertEquals(AddonFailure.HTTP_ERROR, failure { browser.catalog("p1", a, top, emptyMap(), 20) })
        assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aRevisionChangeOrRevokedAccessDropsAnswers() = runBlocking {
        val a = manager.install("p1", url("/cfg/"))
        val top = a.manifest.catalog("movie", "top")!!
        manager.setEnabled("p1", a.id, false)
        assertEquals(AddonFailure.CONFLICT, failure { browser.catalog("p1", a, top, emptyMap(), 20) })
        val enabled = manager.setEnabled("p1", a.id, true)
        access.restricted += "p1"
        assertEquals(AddonFailure.ACCESS_DENIED, failure { browser.catalog("p1", enabled, top, emptyMap(), 20) })
        access.restricted.clear()
        assertEquals(AddonFailure.INVALID_REQUEST, failure { browser.catalog("p1", enabled, top, mapOf("year" to "1"), 20) })
    }

    @Test
    fun theDetailsRouteFallsBackToAnotherMetadataAddon() = runBlocking {
        answers["/catalogonly/manifest.json"] = {
            ok("""{"id":"c","version":"1","name":"CatalogOnly","types":["movie"],"resources":["catalog"],"catalogs":[{"type":"movie","id":"top"}]}""")
        }
        val owner = manager.install("p1", url("/catalogonly/"))
        manager.install("p1", url("/cfg/"))
        val details = browser.details("p1", owner.id, "movie", "tt1")
        assertEquals("Details", details.value.preview.description)
        assertEquals("Details", browser.cachedDetails("p1", owner.id, "movie", "tt1", freshOnly = false)!!.preview.description)
        // A meta answer of another type is rejected.
        answers["/cfg/meta/series/tt1.json"] = { ok("""{"meta":{"type":"movie","id":"tt1","name":"M"}}""") }
        assertEquals(AddonFailure.INVALID_RESPONSE, failure { browser.details("p1", null, "series", "tt1") })
    }

    // ---- Progress and Library (FR-105…112) ----

    @Test
    fun progressGuardsSequencesAndCompletes() = runBlocking {
        val progress = ProgressStore(db.progress(), cipher, clock)
        val id = WatchIdentity("inst", "movie", "tt1", "movie", "tt1")
        val art = Artwork("Movie", "https://img.example/p.jpg", null)
        assertTrue(progress.save("p1", id, "Movie", art, 60_000, 6_000_000, ended = false, session = 2, sequence = 1))
        assertFalse("older session", progress.save("p1", id, "Movie", art, 10, 6_000_000, ended = false, session = 1, sequence = 9))
        assertFalse("lower sequence", progress.save("p1", id, "Movie", art, 10, 6_000_000, ended = false, session = 2, sequence = 1))
        // An unknown duration keeps the known one; 95 % completes; a completed title resumes at 0.
        progress.save("p1", id, "Movie", art, 5_800_000, null, ended = false, session = 2, sequence = 2)
        val done = progress.get("p1", id)!!
        assertEquals(6_000_000L, done.durationMs)
        assertTrue(done.completed)
        assertEquals(0L, done.resumeMs)
        // At rest: no title in the payload.
        assertFalse(db.progress().recent("p1", 10).single().payload.contains("Movie"))
        // 200 per profile, the oldest pruned.
        for (i in 1..205) {
            clock.now += 1
            progress.save("p1", WatchIdentity("inst", "movie", "m$i", "movie", "m$i"), "M", art, 1, 10, ended = false, session = 3, sequence = i.toLong())
        }
        assertEquals(200, progress.recent("p1").size)
        assertEquals(AddonFailure.INVALID_REQUEST, failure { progress.save("p1", id, "M", art, 1, 8L * 24 * 60 * 60_000, false, 9, 1) })
    }

    @Test
    fun theLibraryIsCappedWithoutEviction() = runBlocking {
        val library = LibraryStore(db.library(), cipher, clock)
        fun title(i: Int) = LibraryTitle("inst", "movie", "tt$i", "Movie $i", null, null, "2020", 0)
        for (i in 1..1_000) assertTrue(library.add("p1", title(i)))
        assertFalse(library.add("p1", title(1_001)))
        val first = library.titles("p1").last()
        clock.now += 1_000
        assertTrue("re-adding keeps its place", library.add("p1", title(1)))
        assertEquals(first.addedAt, library.titles("p1").first { it.id == "tt1" }.addedAt)
        assertEquals(1_000, library.titles("p1").size)
        assertFalse(library.contains("p2", "inst", "movie", "tt1"))
    }
}
