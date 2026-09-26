package com.sohva.tv.app

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
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.Profile
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 50 §11 "Instrumented" on the landing with a synthetic addon (fictional titles, a local
 * server): Home makes no addon request; entry focuses Continue watching; Down and Up land on first
 * cards; only the active shelf and the next two load; Left reaches the rail and Right returns;
 * Show all opens the grid and Back returns to its button; a restricted profile is kept out.
 */
@RunWith(AndroidJUnit4::class)
class DiscoverLandingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val host get() = graph.discover!!
    private val server = MockWebServer()
    private val requests = ArrayList<String>()

    private val catalogs = (1..8).map { "c$it" }

    private fun manifest() = """{"id":"org.example.synthetic","version":"1.0.0","name":"Synthetic provider","types":["movie","series"],
        "resources":["catalog","meta"],
        "catalogs":[${catalogs.joinToString(",") { """{"type":"movie","id":"$it","name":"Catalog ${it.drop(1)}","extra":[{"name":"skip"}]}""" }},
          {"type":"movie","id":"choice","name":"Needs a choice","extra":[{"name":"genre","isRequired":true,"options":["Drama","Comedy"]}]}]}"""

    private fun page(catalog: String, skip: Int) = """{"metas":[${(skip until skip + 20).joinToString(",") { i ->
        """{"type":"movie","id":"$catalog-m$i","name":"Title $i of $catalog","releaseInfo":"2024","description":"Catalog text $i"}"""
    }}]}"""

    private val seed = object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    synchronized(requests) { requests += path }
                    val body = when {
                        path.endsWith("/manifest.json") -> manifest()
                        path.contains("/catalog/movie/") -> {
                            val catalog = path.substringAfter("/catalog/movie/").substringBefore("/").removeSuffix(".json")
                            val skip = Regex("skip=(\\d+)").find(path)?.groupValues?.get(1)?.toInt() ?: 0
                            if (skip >= 40) """{"metas":[]}""" else page(catalog, skip)
                        }
                        path.contains("/meta/movie/") -> {
                            val id = path.substringAfter("/meta/movie/").removeSuffix(".json")
                            """{"meta":{"type":"movie","id":"$id","name":"Details of $id","description":"Localized synopsis"}}"""
                        }
                        else -> return MockResponse.Builder().code(404).build()
                    }
                    return MockResponse.Builder().body(body).addHeader("Cache-Control", "max-age=600").build()
                }
            }
            server.start()
            host.testAllowHttp = true
            runBlocking { host.manager.install(graph.data.profiles.activeId, server.url("/cfg/manifest.json").toString()) }
            synchronized(requests) { requests.clear() }
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

    private fun catalogRequests(): List<String> = synchronized(requests) { requests.filter { it.contains("/catalog/") } }

    private fun openDiscover() {
        compose.waitUntil(15_000) { exists(RailItem.DISCOVER.tag) }
        // Home asks addons nothing (spec 50 §9 "Start-up").
        assertTrue("requests before Discover: ${synchronized(requests) { requests.toList() }}", synchronized(requests) { requests.isEmpty() })
        compose.onNodeWithTag(RailItem.DISCOVER.tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { exists("discover-landing") }
    }

    @Test
    fun rowsMoveByFirstCardsAndOnlyTheWindowLoads() {
        openDiscover()
        // Nothing to continue yet: its button takes focus (§3 "Focus on entry").
        awaitFocus("discover-continue-empty")
        compose.waitUntil(10_000) { exists("discover-card-1-c1-m0") }
        // Before any row is focused, shelves 0–2 load; the rest wait.
        compose.waitUntil(5_000) { catalogRequests().size >= 3 }
        Thread.sleep(500)
        assertEquals(listOf("c1", "c2", "c3"), catalogRequests().map { it.substringAfter("/catalog/movie/").substringBefore(".json") }.sorted())
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("discover-card-1-c1-m0")
        press(KeyEvent.KEYCODE_DPAD_RIGHT, 3)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        // The next shelf's first card, not the card below the focused one.
        awaitFocus("discover-card-2-c2-m0")
        compose.waitUntil(5_000) { catalogRequests().any { it.contains("/c4") } }
        press(KeyEvent.KEYCODE_DPAD_UP)
        awaitFocus("discover-card-1-c1-m0")
        press(KeyEvent.KEYCODE_DPAD_UP)
        awaitFocus("discover-continue-empty")
        // No details, streams or subtitles are asked for while browsing rows (FR-59), except the hero's details.
        assertFalse(synchronized(requests) { requests.any { it.contains("/stream/") || it.contains("/subtitles/") } })
        // The required-choice catalog is not a landing shelf (FR-58).
        assertFalse(catalogRequests().any { it.contains("/choice") })
    }

    @Test
    fun theRailAndShowAllReturnFocusWhereTheyLeft() {
        openDiscover()
        compose.waitUntil(10_000) { exists("discover-card-1-c1-m0") }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("discover-card-1-c1-m0")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("discover-rail-home")
        // Focusing an item never opens it; Right returns to the card.
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("discover-rail-library")
        assertTrue(exists("discover-landing"))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus("discover-card-1-c1-m0")
        // Library, then Back to the landing on its rail item.
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("discover-rail-home")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { exists("discover-library-empty") }
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus("discover-rail-library")
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus("discover-card-1-c1-m0")
        // Show all, after the shelf's 20 cards, opens the grid; Back returns to the Show all button.
        press(KeyEvent.KEYCODE_DPAD_RIGHT, 20)
        awaitFocus("discover-show-all-1")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("discover-grid-card-c1-m0") }
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus("discover-show-all-1")
        // Back from the rows leaves Discover.
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("discover-landing") }
    }

    @Test
    fun theGridPagesUntilTheProviderRunsOut() {
        openDiscover()
        compose.waitUntil(10_000) { exists("discover-card-1-c1-m0") }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("discover-card-1-c1-m0")
        press(KeyEvent.KEYCODE_DPAD_RIGHT, 20)
        awaitFocus("discover-show-all-1")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("discover-grid") }
        try {
            compose.waitUntil(10_000) { exists("discover-grid-card-c1-m0") }
        } catch (e: Throwable) {
            val tags = compose.onNodeWithTag("discover-grid").fetchSemanticsNode().let { root ->
                fun walk(n: androidx.compose.ui.semantics.SemanticsNode, d: Int): List<String> =
                    listOf("  ".repeat(d) + (n.config.getOrNull(SemanticsProperties.TestTag) ?: "-") + " " + n.boundsInRoot) + if (d < 4) n.children.flatMap { walk(it, d + 1) } else emptyList()
                walk(root, 0).take(40).joinToString(" | ")
            }
            throw AssertionError("grid status $tags; requests ${synchronized(requests) { requests.toList() }}", e)
        }
        compose.onNodeWithTag("discover-grid-card-c1-m0").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_DOWN, 12)
        compose.waitUntil(10_000) { catalogRequests().any { it.contains("skip=40") } }
        compose.waitUntil(5_000) { exists("discover-grid-card-c1-m39") }
        Thread.sleep(500)
        // An empty page stops paging: nothing after skip=40.
        assertEquals(1, catalogRequests().count { it.contains("skip=40") })
    }

    @Test
    fun setupListsTheAddonAndARestrictedProfileIsKeptOut() {
        openDiscover()
        compose.waitUntil(10_000) { exists("discover-card-1-c1-m0") }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("discover-card-1-c1-m0")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        press(KeyEvent.KEYCODE_DPAD_DOWN, 4)
        awaitFocus("discover-rail-setup")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { exists("discover-addon-Synthetic provider") }
        // A restricted profile: storage refuses it (FR-03) and the rail has no Discover (FR-01).
        runBlocking {
            graph.data.preferences.editHousehold { it.copy(stored = listOf(Profile("kids", "Kids", 1)), activeId = "kids", askAtStart = false) }
            graph.data.profiles.setAllowed("kids", OrgRoom.LIVE, "g0", true)
            kotlinx.coroutines.delay(500)
            try {
                host.manager.list("kids")
                fail("a restricted profile must be refused")
            } catch (e: AddonException) {
                assertEquals(AddonFailure.ACCESS_DENIED, e.failure)
            }
        }
    }
}
