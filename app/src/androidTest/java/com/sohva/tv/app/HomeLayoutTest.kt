package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 02 §4.14 (M12): Home draws the profile's layout, and Settings › Home edits it with the
 * remote's keys: Reorder (OK, Up/Down, OK; Back cancels), Show / hide and Reset to default. Keys go
 * through the window like a remote's. The default profile's layout; ClearStateRule removes it after.
 */
@RunWith(AndroidJUnit4::class)
class HomeLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val prefs get() = graph.data.preferences
    private val profile get() = graph.data.profiles.activeId

    private val name = org.junit.rules.TestName()

    private val seed = object : ExternalResource() {
        override fun before() {
            LibraryFixture.seed(graph, perGroup = 10)
            GuideFixture.seed(graph, groups = 1, perGroup = 4)
            runBlocking {
                for (id in listOf(0, 1)) {
                    graph.data.progress.save(LibraryFixture.key(id), (10 + id) * MINUTE, 90 * MINUTE)
                    Thread.sleep(5)
                }
                graph.data.live.recordWatched("fixture-0:c1")
                // The layout Home opens with, before the activity starts.
                if (name.methodName in RECENT_FIRST) prefs.setHomeLayout(profile, HomeLayout.decode("recent-channels,continue-watching"))
                // Slow recent channels: the read waits until the test opens the gate.
                if (name.methodName in SLOW_RECENT) graph.homeRecentGate = { gate.first { it } }
            }
            graph.continueFeed.retry()
        }
    }
    // Compose tests replace the window factory; use the app's policy on their controllable clock.
    private val compose = createAndroidComposeRule<MainActivity>(effectContext = graph.motionScale)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(name).around(seed).around(compose)

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun focusedTags() = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }

    /** Every Home tag on screen, for a failure's message. */
    private fun homeTags(): List<String> = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any") { true }, useUnmergedTree = true)
        .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }.filter { it.startsWith("home-") }

    private fun awaitHome(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(15_000, condition)
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("$what; Home shows ${homeTags()}", e)
        }
    }

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

    private fun stored(): HomeLayout = runBlocking { prefs.homeLayout(profile).first() }

    /** The tags of the cards in Home's row [index] (rows are tagged by position). */
    private fun rowHolds(index: Int, prefix: String): Boolean =
        compose.onAllNodes(hasTestTag("home-row-$index"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
            compose.onNodeWithTag("home-row-$index", useUnmergedTree = true).fetchSemanticsNode().let { row ->
                fun tags(n: androidx.compose.ui.semantics.SemanticsNode): List<String> =
                    listOfNotNull(n.config.getOrNull(SemanticsProperties.TestTag)) + n.children.flatMap(::tags)
                tags(row).any { it.startsWith(prefix) }
            }

    /** Home → Settings (it opens on Playlists) → the Home section; its first control takes focus. */
    private fun openSettingsHome() {
        compose.focusRail(RailItem.SETTINGS)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // Settings opens on Playlists, on the first playlist or on Add (SET-FR-05).
        compose.waitUntil(10_000) { exists("settings-section-sources") && focusedTags().any { it != null && (it.startsWith("source-") || it.startsWith("settings-")) } }
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("settings-section-sources")
        walkTo("settings-section-home", KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-home-order")
    }

    /** HOME-FR-87: Home opens in the stored order, first focus on the first row drawn; a hidden row is not drawn. */
    @Test
    fun homeDrawsTheStoredOrderAndLeavesHiddenRowsOut() {
        awaitHome("channels first") { rowHolds(0, "home-channel-") }
        awaitHome("films second") { rowHolds(1, "home-resume-") }
        compose.waitUntil(10_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("home-channel-") == true } }

        runBlocking { prefs.setHomeLayout(profile, HomeLayout.decode("continue-watching,-recent-channels")) }
        compose.waitUntil(10_000) { !exists("home-channel-1") }
        assertTrue(rowHolds(0, "home-resume-"))
    }

    private val gate = kotlinx.coroutines.flow.MutableStateFlow(false)

    private companion object {
        const val MINUTE = 60_000L
        val RECENT_FIRST = setOf("homeDrawsTheStoredOrderAndLeavesHiddenRowsOut", "aRowArrivingFirstTakesTheFirstFocusOnlyBeforeAKeyPress", "aRowArrivingFirstNeverMovesFocusAfterAKeyPress")
        val SLOW_RECENT = setOf("aRowArrivingFirstTakesTheFirstFocusOnlyBeforeAKeyPress", "aRowArrivingFirstNeverMovesFocusAfterAKeyPress")
    }

    @org.junit.After
    fun openTheGate() {
        graph.homeRecentGate = null
    }

    private fun focusedCard(): String? = focusedTags().firstOrNull { it?.startsWith("home-") == true }

    /** The real lazy list must honor reduced motion, including Foundation's internal wrappers. */
    @Test
    fun reducedMotionScrollsToTheNextRowWithoutSpringFrames() {
        awaitHome("films first") { focusedCard()?.startsWith("home-resume-") == true && rowHolds(1, "home-channel-") }
        org.junit.Assume.assumeTrue(graph.deviceTier.current.reducedMotion)
        val range = compose.onNodeWithTag("home-rows").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        val initialPosition = range.value()
        val positions = mutableListOf<Float>()
        compose.mainClock.autoAdvance = false
        try {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            repeat(24) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                positions.add(range.value())
            }
            assertTrue("Home must scroll to the next row", range.value() > initialPosition)
            assertTrue("A reduced-motion row still animated through ${positions.toSet().size} positions: $positions", positions.toSet().size <= 3)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        awaitHome("a channel card") { focusedCard()?.startsWith("home-channel-") == true }
    }

    /** HOME-FR-93, with slow recent channels ordered first: before any key press they take the first focus when they arrive. */
    @Test
    fun aRowArrivingFirstTakesTheFirstFocusOnlyBeforeAKeyPress() {
        awaitHome("films while the channels are slow") { focusedCard()?.startsWith("home-resume-") == true }
        gate.value = true
        awaitHome("channels first, focused") { rowHolds(0, "home-channel-") && focusedCard()?.startsWith("home-channel-") == true }
    }

    /** HOME-FR-93, 4.9: after the viewer's first key press a row arriving above never moves focus. */
    @Test
    fun aRowArrivingFirstNeverMovesFocusAfterAKeyPress() {
        awaitHome("films while the channels are slow") { focusedCard() == "home-resume-vod:${LibraryFixture.key(1)}" }
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus("home-resume-vod:${LibraryFixture.key(0)}")
        gate.value = true
        awaitHome("the channels row arrives") { homeTags().any { it.startsWith("home-channel-") } || compose.onAllNodes(hasTestTag("home-row-1")).fetchSemanticsNodes().isNotEmpty() }
        android.os.SystemClock.sleep(1_000)
        compose.waitForIdle()
        assertEquals("home-resume-vod:${LibraryFixture.key(0)}", focusedCard())
    }

    /** HOME-FR-97 (owner, 28 September 2026): back from another screen, focus is on the card last used, not on the first row. */
    @Test
    fun backFromTheLibraryReturnsToTheRowLeft() {
        awaitHome("films first") { focusedCard()?.startsWith("home-resume-") == true }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitHome("a channel card") { focusedCard()?.startsWith("home-channel-") == true }
        val left = focusedCard()!!
        compose.focusRail(RailItem.MOVIES)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { !exists("home-rows") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("home-rows") }
        awaitFocus(left)
        // Up still reaches the first row from there.
        press(KeyEvent.KEYCODE_DPAD_UP)
        awaitHome("films again") { focusedCard()?.startsWith("home-resume-") == true }
        // A film opened from the second card: Back comes to that card, not the first.
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        val second = focusedCard()!!
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { !exists("home-rows") }
        // OK resumes: the player over the film's page over the Movies wall (spec 01 SHELL-FR-28); Back walks them all.
        repeat(5) {
            if (!exists("home-rows")) press(KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            android.os.SystemClock.sleep(500)
        }
        try {
            compose.waitUntil(10_000) { exists("home-rows") }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            val screens = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
                .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }.filter { it.startsWith("screen-") || it.startsWith("player") }
            throw AssertionError("not back on Home; on $screens", e)
        }
        awaitFocus(second)
    }

    /** HOME-FR-90: Reorder with OK, Up and OK writes once; Home then draws that order. Back cancels a move. */
    @Test
    fun reorderWithTheRemoteAndBackCancels() {
        compose.waitUntil(15_000) { rowHolds(0, "home-resume-") }
        openSettingsHome()
        walkTo("settings-home-row-recent-channels", KeyEvent.KEYCODE_DPAD_DOWN)
        // Pick up, move to the top, then Back: nothing written, the row keeps focus.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_UP, 4)
        awaitFocus("settings-home-row-recent-channels")
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus("settings-home-row-recent-channels")
        assertEquals(HomeLayout.DEFAULT, stored())
        // Again, and place it.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_UP, 4)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { stored().rows.first().id == HomeLayout.RECENT }
        awaitFocus("settings-home-row-recent-channels")
        // Back to Home (Back leaves the pane for the section list, then Settings): Recently watched channels comes first.
        repeat(3) { if (!exists("home-rows")) press(KeyEvent.KEYCODE_BACK) }
        awaitHome("channels first") { rowHolds(0, "home-channel-") }
    }

    /** HOME-FR-90, -87: Show / hide writes at once and Home leaves the row out; Reset brings the default back. */
    @Test
    fun hideARowThenResetToDefault() {
        awaitHome("recent channels") { homeTags().any { it.startsWith("home-channel-") } }
        openSettingsHome()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus("settings-home-visibility")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        walkTo("settings-home-switch-recent-channels", KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { !stored().isShown(HomeLayout.RECENT) }
        awaitFocus("settings-home-switch-recent-channels")
        // Reset to default, on the button row above (Up lands on the button nearest the row's middle).
        val buttons = listOf("settings-home-order", "settings-home-visibility", "settings-home-trakt", "settings-home-reset")
        repeat(10) { if (buttons.none(::focused)) press(KeyEvent.KEYCODE_DPAD_UP) }
        walkTo("settings-home-reset", KeyEvent.KEYCODE_DPAD_RIGHT, limit = 4)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { stored() == HomeLayout.DEFAULT }
        assertEquals(null, runBlocking { prefs.homeLayoutText(profile) })
        assertFalse(focusedTags().isEmpty())
    }
}
