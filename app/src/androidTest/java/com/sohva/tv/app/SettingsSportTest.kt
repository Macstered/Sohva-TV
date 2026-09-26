package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 60 §4.1 on the device: channel priority codes, the key, and the follow menu with a
 * competition list from a local server; the menu's Back returns focus to its opener (S§8).
 */
@RunWith(AndroidJUnit4::class)
class SettingsSportTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val server = MockWebServer()
    private val keysSeen = ArrayList<String?>()

    @Before
    fun startServer() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(keysSeen) { keysSeen += request.headers["x-apisports-key"] }
                return when {
                    request.target.startsWith("/football/leagues") -> MockResponse.Builder().body(
                        """{"response":[{"league":{"id":39,"name":"Premier League"},"country":{"name":"England"}},
                           {"league":{"id":140,"name":"La Liga"},"country":{"name":"Spain"}},
                           {"league":{"id":244,"name":"Veikkausliiga"},"country":{"name":"Finland"}}]}""",
                    ).build()
                    else -> MockResponse.Builder().body("""{"response":[]}""").build()
                }
            }
        }
        server.start()
        graph.sport.testHosts = { sport -> server.url("/${sport.provider}/") }
    }

    @After
    fun stopServer() = server.close()

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(8_000) { focused(tag) }
        } catch (e: Throwable) {
            val now = compose.onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("waiting for $tag, focused: $now", e)
        }
    }

    private fun text(tag: String): String = runCatching {
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    private fun type(tag: String, value: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)

    /** Focus [tag] from where focus is now, then OK on it, as the remote does. */
    private fun ok(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private fun openSport() {
        compose.waitUntil(10_000) { exists(RailItem.SETTINGS.tag) }
        click(RailItem.SETTINGS.tag)
        compose.waitUntil(10_000) { exists("settings-section-sources") }
        click("settings-section-sport")
        // Save order is the section's first control (S§8).
        awaitFocus("settings-sport-codes-save")
    }

    /** SPORT-FR-10: known codes only, canonical, distinct; the field shows what was saved. */
    @Test
    fun priorityCodesAreNormalisedAndSaved() {
        openSport()
        type("settings-sport-codes", "es, eng, xx, gbr, ES")
        click("settings-sport-codes-save")
        compose.waitUntil(5_000) { text("settings-sport-message") == "Channel order saved" }
        assertEquals(listOf("ES", "EN", "UK"), runBlocking { graph.data.preferences.sportsPriority.first() })
        assertTrue(text("settings-sport-codes").contains("ES, EN, UK"))
    }

    /** SPORT-FR-02…07: the key opens the menu; the competitions load once; a toggle is stored at once; Back returns focus. */
    @Test
    fun theKeyOpensTheFollowMenuWithTheProvidersCompetitions() {
        openSport()
        assertTrue(exists("settings-sport-needs-key"))
        type("settings-sport-key", "fictional-sports-key")
        click("settings-sport-key-save")
        compose.waitUntil(5_000) { text("settings-sport-message") == "API-Sports key saved securely" }
        compose.waitUntil(5_000) { exists("settings-sport-follow-open") }
        click("settings-sport-follow-open")
        awaitFocus("settings-sport-football")
        compose.waitUntil(10_000) { exists("settings-sport-competitions-count") }
        // Premier League and La Liga are followed by default; the provider lists three.
        assertEquals("Selected 2 of 3 competitions", text("settings-sport-competitions-count"))
        assertEquals("fictional-sports-key", synchronized(keysSeen) { keysSeen.last() })
        click("settings-sport-competition-FOOTBALL:244")
        compose.waitUntil(5_000) { runBlocking { "FOOTBALL:244" in graph.data.preferences.sportFollows.first().competitions } }
        // The defaults were written with the change (SPORT-FR-09).
        assertTrue(runBlocking { "ICE_HOCKEY:16" in graph.data.preferences.sportFollows.first().competitions })
        // Search narrows by name or country.
        type("settings-sport-competitions-search", "spa")
        compose.waitUntil(5_000) { !exists("settings-sport-competition-FOOTBALL:39") }
        assertTrue(exists("settings-sport-competition-FOOTBALL:140"))
        // MMA has no competitions; including it is one press. Focus moves inside the pane, as the remote moves it.
        compose.onNodeWithTag("settings-sport-sports").performScrollToIndex(SportType.MMA.ordinal)
        ok("settings-sport-mma")
        compose.waitUntil(5_000) { exists("settings-sport-no-competitions") }
        ok("settings-sport-toggle")
        compose.waitUntil(5_000) { runBlocking { SportType.MMA in graph.data.preferences.sportFollows.first().sports } }
        // Back closes the menu and returns focus to its opener.
        ok("settings-sport-follow-back")
        awaitFocus("settings-sport-follow-open")
        assertFalse(exists("settings-sport-follow-back"))
        // Removing the key hides the menu again.
        click("settings-sport-key-save")
        compose.waitUntil(5_000) { exists("settings-sport-needs-key") }
        assertFalse(runBlocking { graph.data.serviceKeys.apiSports() != null })
    }
}
