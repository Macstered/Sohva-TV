package com.sohva.tv.app

import android.Manifest
import android.os.Build
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.reminder.ReminderIds
import com.sohva.tv.core.model.reminder.ReminderKind
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 60 §11 "Hub" on the device (mirrors beta 23's `MatchHubTest` where streams are not needed):
 * focus opens on Close without streams, cannot escape to the list, Back closes the hub and returns
 * focus to the opening card; match events draw the band and the list scrolls past what fits;
 * Remind me stores the match reminder and shows Reminder set.
 */
@RunWith(AndroidJUnit4::class)
class MatchHubTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val server = MockWebServer()
    private val eventRequests = AtomicInteger()
    private val now = System.currentTimeMillis() / 1000

    private fun fixture(id: Int, start: Long, status: String, home: String, away: String, goals: String) =
        """{"fixture":{"id":$id,"timestamp":$start,"status":{"short":"$status","elapsed":${if (status == "2H") 67 else "null"}}},
            "league":{"id":39,"name":"Premier League"},"teams":{"home":{"name":"$home"},"away":{"name":"$away"}},"goals":{$goals}}"""

    /** Thirty match events alternating sides, enough to overflow the list. */
    private fun incidents(): String = (1..30).joinToString(",", "[", "]") { i ->
        val team = if (i % 2 == 0) "Northbridge" else "Harbor"
        val type = if (i % 5 == 0) "Goal" else "Card"
        """{"time":{"elapsed":${i * 2},"extra":null},"team":{"name":"$team"},"player":{"name":"Player $i"},"assist":{"name":null},"type":"$type","detail":"Detail $i","comments":null}"""
    }

    private val seed = object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val target = request.target
                    if (!target.startsWith("/football/")) return MockResponse.Builder().body("""{"response":[]}""").build()
                    if (target.contains("fixtures/events")) {
                        eventRequests.incrementAndGet()
                        return MockResponse.Builder().body("""{"errors":[],"response":${incidents()}}""").build()
                    }
                    val games = listOf(
                        fixture(1, now - 3_600, "2H", "Northbridge", "Harbor", "\"home\":2,\"away\":1"),
                        fixture(2, now + 7_200, "NS", "Summit", "Lumen", "\"home\":null,\"away\":null"),
                    )
                    return MockResponse.Builder().body("""{"errors":[],"response":[${games.joinToString(",")}]}""").build()
                }
            }
            server.start()
            graph.sport.testHosts = { sport -> server.url("/${sport.provider}/") }
            runBlocking { graph.data.serviceKeys.saveApiSports("fictional-sports-key") }
            // The panel notification needs the permission; granting it keeps the system dialog away.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
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

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun openCard(id: Int) {
        compose.waitUntil(10_000) { exists(RailItem.SPORT.tag) }
        compose.onNodeWithTag(RailItem.SPORT.tag).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocus("today-card-api-sports:football:1")
        if (id != 1) {
            compose.onNodeWithTag("today-card-api-sports:football:$id").performSemanticsAction(SemanticsActions.RequestFocus)
            awaitFocus("today-card-api-sports:football:$id")
        }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { exists("match-hub") }
    }

    @Test
    fun focusStaysInTheHubAndBackReturnsToTheOpeningCard() {
        openCard(1)
        // No streams yet: Close takes focus (SPORT-FR-79).
        awaitFocus("hub-close")
        for (key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP)) {
            press(key)
            val tag = focusedTag()
            assertFalse("focus escaped to $tag", tag == null || tag.startsWith("today-"))
        }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("match-hub") }
        awaitFocus("today-card-api-sports:football:1")
        assertTrue("Back closed only the hub", exists("screen-today"))
    }

    @Test
    fun matchEventsDrawTheBandAndTheListScrollsPastWhatFits() {
        openCard(1)
        compose.waitUntil(10_000) { exists("hub-timeline") }
        assertEquals(1, eventRequests.get())
        compose.onNodeWithTag("hub-incidents").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("hub-incidents")
        repeat(20) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.waitForIdle()
        compose.onNodeWithText("Player 30").assertIsDisplayed()
        // Refresh inside the 2-minute window is answered by the cache (SPORT-FR-91).
        compose.onNodeWithTag("hub-events-refresh").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        assertEquals(1, eventRequests.get())
        // Close by its button: focus goes back to the card before the hub hides.
        compose.onNodeWithTag("hub-close").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { !exists("match-hub") }
        awaitFocus("today-card-api-sports:football:1")
    }

    @Test
    fun remindMeStoresTheMatchReminder() {
        openCard(2)
        compose.waitUntil(5_000) { exists("hub-remind") }
        compose.onNodeWithTag("hub-remind").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { runBlocking { graph.data.reminders.all() }.isNotEmpty() }
        val stored = runBlocking { graph.data.reminders.all() }.single()
        assertEquals(ReminderIds.event("api-sports:football:2"), stored.id)
        assertEquals(ReminderKind.EVENT, stored.kind)
        assertEquals("Summit – Lumen", stored.title)
        assertEquals("Premier League", stored.subtitle)
        assertEquals(now + 7_200, stored.startAt / 1000)
        // The first reminder explains once how reminders can open the app (REM-FR-05); Back leaves it.
        compose.waitUntil(5_000) { exists("reminder-overlay-prompt") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("reminder-overlay-prompt") }
        assertTrue("the hub stays open", exists("match-hub"))
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Reminder set") }
    }
}
