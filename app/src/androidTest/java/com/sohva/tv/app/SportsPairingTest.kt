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
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
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
 * Spec 60 §11 "Hub" (streams) and "Sports channel row" on the device: pairing finds a guide
 * programme naming both teams (Available) and one naming a single team (Possible); the hub opens
 * on the first stream's Watch; Confirm, Restore and Reject keep focus on the same row's new lead
 * control and never reorder the rows; decisions are saved; Watch plays and Back returns to the
 * open hub; the sports channel row plays its channel.
 */
@RunWith(AndroidJUnit4::class)
class SportsPairingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val server = MockWebServer()
    private val now = System.currentTimeMillis()
    private val kickOff = (now - 60 * 60_000L) / 60_000 * 60_000

    private val seed = object : ExternalResource() {
        override fun before() {
            // No fixture schedule: its "Harbor Routes" would add rows naming one team.
            GuideFixture.seed(graph, groups = 1, perGroup = 6, withGuide = false)
            graph.data.database.sourceStatus().upsert(
                SourceStatusEntity("fixture-0", "epg", "success", now, now, null, null, null, 2, 0, 1, 1, 110 * 60_000L),
            )
            graph.data.database.guideImport().insertProgrammes(
                listOf(
                    programme("e0-0", kickOff - 5 * 60_000L, "Football: Northbridge v Harbor", "sport-1"),
                    programme("e0-1", kickOff - 30 * 60_000L, "Harbor build-up", "sport-2"),
                ),
            )
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (!request.target.startsWith("/football/") || request.target.contains("events")) {
                        return MockResponse.Builder().body("""{"response":[]}""").build()
                    }
                    val game = """{"fixture":{"id":1,"timestamp":${kickOff / 1000},"status":{"short":"2H","elapsed":67}},
                        "league":{"id":39,"name":"Premier League"},"teams":{"home":{"name":"Northbridge"},"away":{"name":"Harbor"}},"goals":{"home":2,"away":1}}"""
                    return MockResponse.Builder().body("""{"errors":[],"response":[$game]}""").build()
                }
            }
            server.start()
            graph.sport.testHosts = { sport -> server.url("/${sport.provider}/") }
            runBlocking { graph.data.serviceKeys.saveApiSports("fictional-sports-key") }
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun programme(epg: String, start: Long, title: String, key: String) = ProgrammeEntity(
        sourceId = "fixture-0", snapshot = 1, epgId = epg, startAt = start, stopAt = start + 110 * 60_000L,
        title = title, subtitle = null, description = null, categories = null, programmeKey = key,
    )

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

    private fun status(channel: String): String =
        compose.onNodeWithTag("hub-stream-status-$channel").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    private fun rowOrder(): List<String> = listOf(A, B).sortedBy { compose.onNodeWithTag("hub-stream-$it").fetchSemanticsNode().boundsInRoot.top }

    private fun openToday() {
        compose.waitUntil(10_000) { exists(RailItem.SPORT.tag) }
        compose.onNodeWithTag(RailItem.SPORT.tag).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocus("today-card-api-sports:football:1")
        // Pairing runs once Today is shown; the card then offers the channel.
        compose.waitUntil(20_000) { compose.onAllNodesWithTextExists("WATCH · 1 CHANNEL") }
    }

    @Test
    fun decisionsKeepFocusAndOrderAndWatchReturnsToTheHub() {
        openToday()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { exists("match-hub") }
        // SPORT-FR-79: the first stream's lead control.
        awaitFocus("hub-stream-watch-$A")
        assertEquals(listOf(A, B), rowOrder())
        assertEquals("POSSIBLE", status(B))

        compose.onNodeWithTag("hub-stream-confirm-$B").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { status(B) == "CONFIRMED BY YOU" }
        awaitFocus("hub-stream-watch-$B")
        assertEquals(listOf(A, B), rowOrder())
        assertEquals("confirmed", runBlocking { graph.data.sportDao.decisions(listOf(GAME)) }.single().decision)

        compose.onNodeWithTag("hub-stream-restore-$B").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { status(B) == "POSSIBLE" }
        awaitFocus("hub-stream-confirm-$B")
        assertTrue(runBlocking { graph.data.sportDao.decisions(listOf(GAME)) }.isEmpty())

        compose.onNodeWithTag("hub-stream-reject-$A").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { status(A) == "REJECTED" }
        awaitFocus("hub-stream-restore-$A")
        assertEquals(listOf(A, B), rowOrder())
        compose.onNodeWithTag("hub-stream-restore-$A").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("hub-stream-watch-$A")

        // Watch plays; Back returns to Today with the hub open (SPORT-NAV-02).
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-player") }
        // Back peels the player's layers (its info box first), then leaves.
        repeat(3) {
            if (!exists("match-hub")) press(KeyEvent.KEYCODE_BACK)
            runCatching { compose.waitUntil(3_000) { exists("match-hub") } }
        }
        compose.waitUntil(10_000) { exists("match-hub") }
        awaitFocus("hub-stream-watch-$A")
    }

    @Test
    fun theSportsChannelRowPlaysItsChannel() {
        openToday()
        compose.waitUntil(5_000) { exists("today-channel-$A") }
        assertTrue("a possible stream is not a sports channel", !exists("today-channel-$B"))
        compose.onNodeWithTag("today-channel-$A").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("today-channel-$A")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-player") }
    }

    /** Spec 30 §4.20: quick actions turn the ticker on; it lists the live game with its score. */
    @Test
    fun theScoreTickerShowsTheLiveGameOverThePlayer() {
        openToday()
        compose.onNodeWithTag("today-channel-$A").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("today-channel-$A")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-player") }
        // The entry box goes by itself; then the clean picture's keys decide (as the player's own tests do).
        awaitFocus("player-video", 15_000)
        press(KeyEvent.KEYCODE_MENU)
        compose.waitUntil(5_000) { exists("player-quick-ticker") }
        compose.onNodeWithTag("player-quick-ticker").performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { exists("player-ticker") }
        assertTrue(compose.onAllNodesWithTextExists("Northbridge – Harbor"))
        assertTrue(compose.onAllNodesWithTextExists("2 – 1"))
    }

    private companion object {
        const val GAME = "api-sports:football:1"
        const val A = "fixture-0:c0"
        const val B = "fixture-0:c1"
    }
}
