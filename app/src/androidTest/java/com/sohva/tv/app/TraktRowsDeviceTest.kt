package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.first
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
 * Spec 02 HOME-FR-94 (M12 phase B): an added Trakt chart is fetched by the loop without an account
 * and drawn on Home in its layout place; Settings › Home adds one with the remote. The fake Trakt's
 * titles are fictional; the default profile has no Trakt account here.
 */
@RunWith(AndroidJUnit4::class)
class TraktRowsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val prefs get() = graph.data.preferences
    private val profile get() = graph.data.profiles.activeId
    private val trakt = MockWebServer()
    private val requests = ConcurrentLinkedQueue<Pair<String, String?>>()
    private val bodies = mapOf(
        "movies/trending" to """[{"watchers":12,"movie":{"title":"A fictional film","year":2026,"ids":{"trakt":1}}},
            {"watchers":4,"movie":{"title":"Another fictional film","year":2025,"ids":{"trakt":2}}}]""",
    )
    private val name = org.junit.rules.TestName()

    private val servers = object : ExternalResource() {
        override fun before() {
            trakt.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath.removePrefix("/")
                    requests += path to request.headers["Authorization"]
                    val body = bodies[path] ?: return MockResponse.Builder().code(404).body("{}").build()
                    return MockResponse.Builder().code(200).body(body).build()
                }
            }
            trakt.start()
            graph.playbackActive.value = true
            graph.trakt!!.useTestServer(trakt.url("/"), TraktCredentials("fictional-client-id", "not-a-real-secret"))
            LibraryFixture.seed(graph, perGroup = 4)
            runBlocking {
                graph.data.progress.save(LibraryFixture.key(0), 10 * MINUTE, 90 * MINUTE)
                if (name.methodName != "settingsAddsATraktRowWithTheRemote") {
                    prefs.setHomeLayout(profile, HomeLayout.DEFAULT.withAdded(HomeLayout.TRAKT_TRENDING_MOVIES).withOrder(listOf(HomeLayout.TRAKT_TRENDING_MOVIES)))
                }
            }
            graph.continueFeed.retry()
        }

        override fun after() {
            graph.traktLoop?.stop()
            graph.playbackActive.value = false
            trakt.close()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(name).around(servers).around(compose)

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun focusedTags() = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(10_000) { focused(tag) }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("$tag not focused; focused: ${focusedTags()}", e)
        }
    }

    private fun walkTo(tag: String, keyCode: Int, limit: Int = 20) {
        repeat(limit) { if (!focused(tag)) press(keyCode) }
        awaitFocus(tag)
    }

    private fun home() {
        compose.waitUntil(15_000) { exists(RailItem.MOVIES.tag) }
        runBlocking { graph.continueFeed.awaitSettled() }
        // The loop as the app starts it after Home's first read, now that playback is over.
        graph.playbackActive.value = false
        graph.traktLoop!!.start(profile)
    }

    private val card = "home-trakt-${HomeLayout.TRAKT_TRENDING_MOVIES}/movie:1::"

    @Test
    fun anAddedChartIsFetchedWithoutAnAccountAndDrawnInItsPlace() {
        home()
        compose.waitUntil(15_000) { exists(card) }
        // Public: read with the app's key alone, never with a sign-in.
        assertEquals(listOf("movies/trending" to null), requests.toList())
        // HOME-FR-93: the row the layout puts first takes the first focus before any key press.
        awaitFocus(card)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus("home-trakt-${HomeLayout.TRAKT_TRENDING_MOVIES}/movie:2::")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitUntil(5_000) { focusedTags().any { it?.startsWith("home-resume-") == true } }
    }

    /** HOME-FR-83: Left from a row's first card opens the rail, also from a narrow poster card (the owner's Shield). */
    @Test
    fun leftFromAPosterRowsFirstCardOpensTheRail() {
        home()
        compose.waitUntil(15_000) { exists(card) }
        awaitFocus(card)
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        val rail = RailItem.entries.map { it.tag }.toSet()
        try {
            compose.waitUntil(5_000) { focusedTags().any { it in rail } }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Left did not reach the rail; focused: ${focusedTags()}", e)
        }
        // Right goes back to the card it came from.
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(card)
    }

    @Test
    fun settingsAddsATraktRowWithTheRemote() {
        home()
        compose.waitUntil(15_000) { exists("home-resume-vod:${LibraryFixture.key(0)}") }
        assertTrue("nothing to fetch yet", requests.isEmpty())
        compose.focusRail(RailItem.SETTINGS)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("settings-section-sources") && focusedTags().any { it != null && (it.startsWith("source-") || it.startsWith("settings-")) } }
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("settings-section-sources")
        walkTo("settings-section-home", KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-home-order")
        walkTo("settings-home-trakt", KeyEvent.KEYCODE_DPAD_RIGHT, limit = 3)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // Without an account only the charts are offered; the watchlists are not.
        compose.waitUntil(5_000) { exists("settings-home-add-${HomeLayout.TRAKT_TRENDING_MOVIES}") }
        assertTrue(!exists("settings-home-add-${HomeLayout.TRAKT_WATCHLIST_MOVIES}"))
        walkTo("settings-home-add-${HomeLayout.TRAKT_TRENDING_MOVIES}", KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { runBlocking { prefs.homeLayout(profile).first() }.added == listOf(HomeLayout.TRAKT_TRENDING_MOVIES) }
        awaitFocus("settings-home-add-${HomeLayout.TRAKT_TRENDING_MOVIES}")
        // The request the save sent brings the list in a few seconds, not at the next 15-minute cycle.
        repeat(3) { if (!exists("home-rows")) press(KeyEvent.KEYCODE_BACK) }
        compose.waitUntil(15_000) { exists(card) }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
