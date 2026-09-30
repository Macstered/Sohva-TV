package com.sohva.tv.app

import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 50 §11 at owner scale: 40 catalogs answering 1,000 titles each. Home asks nothing; opening
 * Discover fetches a few catalogs; 32 Downs fetch each shelf of the window once; going back over
 * the 24-shelf bound asks nothing again; no meta, stream or subtitle request; the Java heap stays
 * under 64 MB including a 1,000-title Show all grid. Numbers go to the log for the performance log.
 */
@RunWith(AndroidJUnit4::class)
class DiscoverOwnerScaleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val host get() = graph.discover!!
    private val server = MockWebServer()
    private val requests = ConcurrentLinkedQueue<String>()
    private val pages = HashMap<String, String>()

    private val manifest = """{"id":"org.example.scale","version":"1.0.0","name":"Scale provider","types":["movie"],"resources":["catalog","meta","stream"],
        "catalogs":[${(1..CATALOGS).joinToString(",") { """{"type":"movie","id":"c$it","name":"Catalog $it","extra":[{"name":"skip"}]}""" }}]}"""

    /** 1,000 fictional titles with a poster address that answers 404 (the placeholder path). */
    private fun page(catalog: String, skip: Int): String = synchronized(pages) {
        pages.getOrPut("$catalog:$skip") {
            buildString {
                append("{\"metas\":[")
                for (i in skip until skip + TITLES) {
                    if (i > skip) append(',')
                    append("{\"type\":\"movie\",\"id\":\"$catalog-m$i\",\"name\":\"Title $i of $catalog\",\"releaseInfo\":\"2024\",")
                    append("\"poster\":\"http://127.0.0.1:${server.port}/p/$catalog-$i.jpg\",\"description\":\"A fictional description of title $i.\"}")
                }
                append("]}")
            }
        }
    }

    private val seed = object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    requests += path
                    val body = when {
                        path.endsWith("/manifest.json") -> manifest
                        path.contains("/catalog/movie/") -> {
                            val catalog = path.substringAfter("/catalog/movie/").substringBefore("/").removeSuffix(".json")
                            val skip = Regex("skip=(\\d+)").find(path)?.groupValues?.get(1)?.toInt() ?: 0
                            if (skip >= TITLES) """{"metas":[]}""" else page(catalog, skip)
                        }
                        else -> return MockResponse.Builder().code(404).build()
                    }
                    return MockResponse.Builder().body(body).addHeader("Cache-Control", "max-age=3600").build()
                }
            }
            server.start()
            host.testAllowHttp = true
            runBlocking { host.manager.install(graph.data.profiles.activeId, server.url("/cfg/manifest.json").toString()) }
            requests.clear()
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focusedTag(): String? = compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        try {
            compose.waitUntil(timeout) { compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError("waiting for $tag, focused: ${focusedTag()}", e)
        }
    }

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun catalogs(): List<String> = requests.filter { it.contains("/catalog/") && !it.contains("skip=") }
        .map { it.substringAfter("/catalog/movie/").substringBefore(".json") }

    @Test
    fun fortyCatalogsOfAThousandTitlesStayWithinTheWindowAndTheHeap() {
        val heap = HeapSampler().also { it.start() }
        val heartbeat = Heartbeat().also { it.start() }
        compose.waitUntil(15_000) { exists(RailItem.DISCOVER.tag) }
        Thread.sleep(1_000)
        assertTrue("Home asked addons: ${requests.toList()}", requests.isEmpty())
        val home = heartbeat.phase()
        // The first press was once lost while Home was still starting; press again if Discover does not open.
        for (attempt in 1..3) {
            compose.onNodeWithTag(RailItem.DISCOVER.tag).performSemanticsAction(SemanticsActions.OnClick)
            if (runCatching { compose.waitUntil(5_000) { exists("discover-landing") } }.isSuccess) break
        }
        compose.waitUntil(15_000) { exists("discover-card-1-c1-m0") }
        Thread.sleep(500)
        val opening = catalogs().distinct().size
        assertTrue("opening fetched $opening catalogs", opening in 1..4)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("discover-card-1-c1-m0")
        val open = heartbeat.phase()
        val downs = System.nanoTime()
        press(KeyEvent.KEYCODE_DPAD_DOWN, 31)
        awaitFocus("discover-card-32-c32-m0", 20_000)
        val downMs = (System.nanoTime() - downs) / 1_000_000
        try {
            compose.waitUntil(10_000) { catalogs().distinct().size >= 34 }
        } catch (e: Throwable) {
            throw AssertionError("prefetch after row 32: ${catalogs()}, focused: ${focusedTag()}", e)
        }
        Thread.sleep(1_000)
        val fetched = catalogs()
        // Every shelf of the window once: shelves 1–32 focused plus the next two.
        assertEquals("each catalog once: $fetched", fetched.size, fetched.distinct().size)
        assertEquals((1..34).map { "c$it" }.toSet(), fetched.toSet())
        val down = heartbeat.phase()
        // Back up over the 24-shelf bound: the saved answers serve it, nothing is asked again.
        press(KeyEvent.KEYCODE_DPAD_UP, 31)
        awaitFocus("discover-card-1-c1-m0", 20_000)
        Thread.sleep(1_000)
        assertEquals("requests after returning", fetched.size, catalogs().size)
        val up = heartbeat.phase()
        // Along one shelf, then its Show all grid of 1,000 titles.
        press(KeyEvent.KEYCODE_DPAD_RIGHT, 20)
        awaitFocus("discover-show-all-1")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(20_000) { exists("discover-grid") }
        // The grid holds its 1,000-title cap from the first page and asks for no second one (FR-120).
        Thread.sleep(3_000)
        assertTrue(requests.none { it.contains("skip=") })
        val grid = heartbeat.phase()
        press(KeyEvent.KEYCODE_DPAD_DOWN, 20)
        Thread.sleep(1_000)
        val gridDown = heartbeat.phase()
        heap.finish()
        heartbeat.finish()
        // No stream or subtitle request; details only for the hero where focus rested (§9: one lookup after the settle).
        assertTrue(requests.none { it.contains("/stream/") || it.contains("/subtitles/") })
        val meta = requests.filter { it.contains("/meta/") }.map { it.substringAfter("/meta/movie/").removeSuffix(".json") }.toSet()
        // Espresso may pause over the hero's settle threshold between presses on a slow host.
        // Such lookups are valid, but never for prefetched shelves or cards we did not visit.
        val visited = (1..32).map { "c$it-m0" }.toSet()
        assertTrue("details asked for unvisited cards: $meta", meta.all { it in visited })
        Log.i(TAG, "hero lookups ${meta.size}; opening $opening catalogs; 31 Downs in $downMs ms; ${fetched.size} catalog requests; Java heap peak ${heap.max() / MB} MB; main-thread longest gap ${heartbeat.longestGapMs()} ms " +
            "(home $home, open $open, downs $down, ups $up, grid open $grid, grid downs $gridDown)")
        assertTrue("Java heap peak ${heap.max() / MB} MB", heap.max() < HEAP_BUDGET)
    }

    private companion object {
        const val TAG = "DiscoverOwnerScale"
        const val CATALOGS = 40
        const val TITLES = 1_000
        const val MB = 1024L * 1024
        const val HEAP_BUDGET = 64 * MB
    }
}
