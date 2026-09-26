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
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 60 §11 "UI" for Today on the device, with games anchored to now: the first live game takes
 * focus on entry, a chosen tab moves focus to its first game, a refresh that changes the first game
 * does not move focus, a missing key names itself, and nothing is requested once Today is left.
 */
@RunWith(AndroidJUnit4::class)
class TodayTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val server = MockWebServer()
    private val football = AtomicInteger()
    private val now = System.currentTimeMillis() / 1000

    /** Live Northbridge – Harbor, later Summit – Lumen, finished Pulse – Meridian; [earlier] adds a live game that started first. */
    @Volatile private var earlier = false

    private fun fixture(id: Int, league: Int, start: Long, status: String, home: String, away: String, goals: String = "\"home\":null,\"away\":null") =
        """{"fixture":{"id":$id,"timestamp":$start,"status":{"short":"$status","elapsed":${if (status == "2H") 67 else "null"}}},
            "league":{"id":$league,"name":"League $league"},"teams":{"home":{"name":"$home"},"away":{"name":"$away"}},"goals":{$goals}}"""

    @Before
    fun startServer() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.target.startsWith("/football/")) return MockResponse.Builder().body("""{"response":[]}""").build()
                football.incrementAndGet()
                val games = listOfNotNull(
                    fixture(1, 39, now - 3_600, "2H", "Northbridge", "Harbor", "\"home\":2,\"away\":1"),
                    fixture(2, 39, now + 7_200, "NS", "Summit", "Lumen"),
                    fixture(3, 140, now - 14_400, "FT", "Pulse", "Meridian", "\"home\":0,\"away\":0"),
                    if (earlier) fixture(4, 140, now - 5_000, "2H", "Aurora", "Beacon", "\"home\":1,\"away\":1") else null,
                )
                return MockResponse.Builder().body("""{"errors":[],"response":[${games.joinToString(",")}]}""").addHeader("x-ratelimit-requests-remaining", "95").build()
            }
        }
        server.start()
        graph.sport.testHosts = { sport -> server.url("/${sport.provider}/") }
    }

    @After
    fun stopServer() = server.close()

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        try {
            compose.waitUntil(timeout) { focused(tag) }
        } catch (e: Throwable) {
            val now = compose.onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("waiting for $tag, focused: $now", e)
        }
    }

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun openToday() {
        compose.waitUntil(10_000) { exists(RailItem.SPORT.tag) }
        compose.onNodeWithTag(RailItem.SPORT.tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { exists("screen-today") }
    }

    @Test
    fun theFirstLiveGameIsFocusedAndATabMovesFocusToItsFirstGame() {
        runBlocking { graph.data.serviceKeys.saveApiSports("fictional-sports-key") }
        openToday()
        awaitFocus("today-card-api-sports:football:1")
        assertTrue(exists("today-card-api-sports:football:2"))
        assertTrue(exists("today-card-api-sports:football:3"))
        // Watchable has no game until pairing: choosing it focuses the tab itself; All brings focus back to the live game.
        compose.onNodeWithTag("today-tab-watchable").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { exists("today-empty") }
        awaitFocus("today-tab-watchable")
        compose.onNodeWithTag("today-tab-all").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("today-card-api-sports:football:1")
    }

    @Test
    fun aRefreshThatChangesTheFirstGameDoesNotMoveFocus() {
        runBlocking { graph.data.serviceKeys.saveApiSports("fictional-sports-key") }
        openToday()
        awaitFocus("today-card-api-sports:football:1")
        // Move to the finished game, then let a refresh bring a new first live game.
        compose.onNodeWithTag("today-card-api-sports:football:3").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("today-card-api-sports:football:3")
        earlier = true
        runBlocking { graph.data.database.openHelper.writableDatabase.execSQL("UPDATE sport_feed SET expires_at = 0") }
        compose.onNodeWithTag("today-refresh").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { exists("today-card-api-sports:football:4") }
        compose.waitForIdle()
        assertTrue("focus stays on the game the viewer chose", focused("today-card-api-sports:football:3"))
    }

    @Test
    fun withoutAKeyTheCauseIsNamedAndSettingsIsOnePressAway() {
        openToday()
        compose.waitUntil(10_000) { exists("today-error") }
        compose.waitUntil(5_000) { exists("today-error-cause") }
        assertEquals(0, football.get())
        assertTrue(exists("today-open-settings"))
    }

    @Test
    fun leavingTodayStopsRequests() {
        runBlocking { graph.data.serviceKeys.saveApiSports("fictional-sports-key") }
        openToday()
        awaitFocus("today-card-api-sports:football:1")
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { !exists("screen-today") }
        val requests = football.get()
        // A live game polls every 5 minutes while Today is shown; away from it, nothing asks.
        runBlocking { graph.data.database.openHelper.writableDatabase.execSQL("UPDATE sport_feed SET expires_at = 0") }
        Thread.sleep(2_000)
        assertEquals(requests, football.get())
    }
}
