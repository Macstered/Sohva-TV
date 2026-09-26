package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Settings › Playlists and General's refresh interval on the emulator (spec 10 §11 "Instrumented",
 * spec 70 SET-FR-02…15). Back goes through the window like a remote's.
 */
@RunWith(AndroidJUnit4::class)
class SettingsPlaylistsTest {
    private val clearState = ClearStateRule()
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(clearState).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val server = MockWebServer()

    @Before
    fun startServer() = server.start()

    @After
    fun stopServer() = server.close()

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun await(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(tag) }

    private fun openSettings() {
        await(RailItem.SETTINGS.tag)
        compose.onNodeWithTag(RailItem.SETTINGS.tag).performSemanticsAction(SemanticsActions.OnClick)
        await("source-add-m3u")
        compose.waitForIdle()
    }

    /**
     * OK on a control through its click action: a touch at the node's centre misses controls the
     * pane has not scrolled into view yet, and the handler is the same one the remote's OK runs.
     */
    private fun click(tag: String) {
        await(tag)
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
    }

    /** Opens a field's editor with OK, types, and closes it with Done (SRC-FR-18). */
    private fun type(tag: String, text: String) {
        click(tag)
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
    }

    private fun statusShows(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodes(hasTestTag("settings-status")).fetchSemanticsNodes().any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(text) } == true
        }
    }

    @Test
    fun opensOnPlaylistsWithFocusOnTheFirstControl() {
        openSettings()
        awaitFocus("source-add-m3u")
        statusShows("Add your first IPTV source")
    }

    @Test
    fun aNewPageStartsOnAllPlaylistsAndBackReturnsToTheList() {
        openSettings()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        await("source-page-back")
        awaitFocus("source-page-back")
        compose.onAllNodesWithText("IPTV 1", substring = true).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
        press(KeyEvent.KEYCODE_BACK)
        await("source-add-m3u")
        awaitFocus("source-add-m3u")
    }

    @Test
    fun epgCorrectionStepsByThirtyMinutes() {
        openSettings()
        click("source-add-m3u")
        click("settings-epg-offset-up")
        click("settings-epg-offset-up")
        compose.onNodeWithTag("settings-epg-offset-value").assertTextContains("+1 h")
        repeat(3) { click("settings-epg-offset-down") }
        compose.onNodeWithTag("settings-epg-offset-value").assertTextContains("−30 min")
    }

    @Test
    fun aVodOnlySourceShowsNoLiveOrGuideControls() {
        openSettings()
        click("source-add-m3u")
        click("settings-import-vod")
        await("settings-refresh-catalogue")
        for (gone in listOf("settings-refresh-playlist", "settings-refresh-epg", "settings-xmltv", "settings-epg-offset-up")) {
            assertTrue(gone, compose.onAllNodes(hasTestTag(gone)).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun theXtreamPasswordIsMasked() {
        openSettings()
        click("source-add-xtream")
        type("settings-xtream-password", "hunter2")
        assertTrue(compose.onAllNodesWithText("hunter2", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun testAddressReadsThePlaylistWithTheAppAgentAndDoesNotSave() {
        server.enqueue(MockResponse.Builder().body("#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n#EXTINF:-1,B\nhttp://s.example/b\n").build())
        openSettings()
        click("source-add-m3u")
        type("settings-m3u", server.url("/list.m3u").toString())
        click("settings-test-m3u")
        statusShows("M3U playlist found: 2 entries")
        assertTrue(server.takeRequest().headers["User-Agent"].orEmpty().startsWith("Sohva TV/"))
        assertTrue("testing never saves", runBlocking { graph.data.sources.all() }.isEmpty())
    }

    @Test
    fun aSavedSourcesPageReturnsFocusToItsRow() {
        server.enqueue(MockResponse.Builder().body("#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n").build())
        openSettings()
        click("source-add-m3u")
        type("settings-m3u", server.url("/list.m3u").toString())
        click("settings-save")
        statusShows("Saved.")
        val id = runBlocking { graph.data.sources.all() }.single().id
        press(KeyEvent.KEYCODE_BACK)
        await("source-$id")
        compose.waitForIdle()
        awaitFocus("source-$id")
    }

    /**
     * OK on Save disables the actions while it runs (SRC-FR-19); focus must stay on Save instead
     * of falling to the rail's Playlists entry (AGENTS 5.2; found in the M2 release check).
     */
    @Test
    fun saveKeepsFocusOnTheSaveButton() {
        openSettings()
        click("source-add-m3u")
        type("settings-m3u", "http://192.0.2.10/list.m3u")
        compose.onNodeWithTag("settings-save").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("settings-save")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        statusShows("Saved.")
        compose.waitForIdle()
        awaitFocus("settings-save")
        assertTrue(compose.onAllNodes(hasTestTag("settings-section-sources") and isFocused()).fetchSemanticsNodes().isEmpty())
    }

    /** A stepper's end disables its button; the viewer's focus stays on it (AGENTS 5.2). */
    @Test
    fun steppingToTheLowestLimitKeepsFocusOnMinus() {
        openSettings()
        click("source-add-m3u")
        compose.onNodeWithTag("settings-limit-up").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("settings-limit-up")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("settings-limit-down").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("settings-limit-down")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        compose.onNodeWithTag("settings-limit-down").assertIsNotEnabled()
        awaitFocus("settings-limit-down")
        // Disabled means OK does nothing, not that the limit drops below one.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        awaitFocus("settings-limit-down")
        assertTrue(compose.onAllNodesWithText("Connection limit 1").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun removingASourceAsksFirstAndLeavesNothing() {
        server.enqueue(MockResponse.Builder().body("#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n").build())
        openSettings()
        click("source-add-m3u")
        type("settings-m3u", server.url("/list.m3u").toString())
        click("settings-save")
        statusShows("Saved.")
        click("settings-source-delete")
        await("settings-source-delete-cancel")
        awaitFocus("settings-source-delete-cancel")
        click("settings-source-delete-confirm")
        statusShows("Source deleted")
        await("source-add-m3u")
        assertTrue(runBlocking { graph.data.sources.all() }.isEmpty())
    }

    @Test
    fun theRefreshIntervalPickerPersistsItsChoice() {
        openSettings()
        click("settings-section-general")
        // General opens on the language row (spec 70 SET-FR-14); the refresh interval is further down.
        awaitFocus("settings-language")
        repeat(12) { if (!compose.onAllNodes(hasTestTag("settings-refresh-interval") and isFocused()).fetchSemanticsNodes().isNotEmpty()) press(KeyEvent.KEYCODE_DPAD_DOWN) }
        awaitFocus("settings-refresh-interval")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        await("settings-refresh-interval-4")
        awaitFocus("settings-refresh-interval-24")
        click("settings-refresh-interval-4")
        compose.waitUntil(5_000) { runBlocking { graph.data.preferences.refreshInterval.first() } == RefreshInterval.FOUR_HOURS }
        compose.waitForIdle()
        awaitFocus("settings-refresh-interval")
        compose.onNodeWithTag("settings-refresh-interval").assertTextContains("4 hours", substring = true)
        assertEquals(RefreshInterval.FOUR_HOURS, runBlocking { graph.data.preferences.refreshInterval.first() })
    }

    @Test
    fun theRailSelectsOnOkOnlyAndLeftReturnsToTheSelectedSection() {
        openSettings()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("settings-section-sources")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("settings-section-playback")
        assertTrue("focus alone does not switch", compose.onAllNodes(hasTestTag("source-add-m3u")).fetchSemanticsNodes().isNotEmpty())
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus("source-add-m3u")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("settings-section-sources")
    }
}
