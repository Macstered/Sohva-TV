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
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
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
 * Spec 60 SPORT-50…52 on the device: Home's Today's sport row counts every game and its card sets
 * the hero and opens Sohva Sport with the hub on that game; Search finds a game by team.
 */
@RunWith(AndroidJUnit4::class)
class SportHomeSearchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val server = MockWebServer()
    private val now = System.currentTimeMillis() / 1000

    /** Seven games, so the row shows six and counts seven; the key is saved before Home's first read. */
    private val seed = object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (!request.target.startsWith("/football/")) return MockResponse.Builder().body("""{"response":[]}""").build()
                    val games = (1..7).map { i ->
                        val (home, away) = if (i == 1) "Northbridge" to "Harbor" else "Home $i" to "Away $i"
                        """{"fixture":{"id":$i,"timestamp":${now + i * 3_600},"status":{"short":"${if (i == 1) "2H" else "NS"}","elapsed":${if (i == 1) 67 else "null"}}},
                           "league":{"id":39,"name":"Premier League"},"teams":{"home":{"name":"$home"},"away":{"name":"$away"}},"goals":{"home":${if (i == 1) 2 else "null"},"away":${if (i == 1) 1 else "null"}}}"""
                    }
                    return MockResponse.Builder().body("""{"errors":[],"response":[${games.joinToString(",")}]}""").build()
                }
            }
            server.start()
            graph.sport.testHosts = { sport -> server.url("/${sport.provider}/") }
            runBlocking { graph.data.serviceKeys.saveApiSports("fictional-sports-key") }
            // Home starts the feed after its first read once per process; later tests start it here.
            graph.sport.feed.start()
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String = runCatching {
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    @Test
    fun homeRowSetsTheHeroAndOpensTodayWithTheGameWaiting() {
        compose.waitUntil(20_000) { exists("home-sport-api-sports:football:1") }
        assertTrue(compose.onAllNodesWithTextExists("7 matches"))
        assertTrue("six cards only", !exists("home-sport-api-sports:football:7"))
        compose.onNodeWithTag("home-sport-api-sports:football:1").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(5_000) { text("home-hero-title") == "Northbridge – Harbor" }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-today") }
        // The hub opens on that game and the request is consumed (SPORT-NAV-04).
        compose.waitUntil(10_000) { exists("match-hub") }
        compose.waitUntil(5_000) { focused("hub-close") }
        assertTrue(compose.onAllNodesWithTextExists("Northbridge"))
        assertEquals(null, graph.sport.pendingGame.value)
    }

    @Test
    fun searchFindsAGameByTeamAndOpensToday() {
        compose.waitUntil(20_000) { exists("home-sport-api-sports:football:1") }
        compose.waitUntil(15_000) { exists(RailItem.SEARCH.tag) }
        compose.onNodeWithTag(RailItem.SEARCH.tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { exists("unified-search-field") }
        compose.onNodeWithTag("unified-search-field").performTextReplacement("harbor")
        compose.waitUntil(10_000) { exists("search-result-sport:api-sports:football:1") }
        assertTrue(compose.onAllNodesWithTextExists("SPORT"))
        compose.onNodeWithTag("search-result-sport:api-sports:football:1").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { exists("screen-today") }
        assertTrue(!focused("search-result-sport:api-sports:football:1"))
    }
}
