package com.sohva.tv.app

import android.view.KeyEvent
import android.os.Debug
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Home actions must use the visible stack after background return and activity recreation. */
@RunWith(AndroidJUnit4::class)
class HomeRestoreTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val seed = object : ExternalResource() {
        override fun before() {
            LibraryFixture.seed(graph, perGroup = 4)
            GuideFixture.seed(graph, groups = 1, perGroup = 4)
            runBlocking {
                graph.data.progress.save(LibraryFixture.key(0), 10 * MINUTE, 90 * MINUTE)
                graph.data.live.recordWatched("fixture-0:c1")
            }
            graph.continueFeed.retry()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private val film = "home-resume-vod:${LibraryFixture.key(0)}"
    private fun exists(tag: String): Boolean = compose.onAllNodesWithTagExists(tag)
    private fun focused(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
    private fun awaitFocus(tag: String) = compose.waitUntil(10_000) { focused(tag) }
    private fun press(key: Int) {
        instrumentation.sendKeyDownUpSync(key)
        compose.waitForIdle()
    }

    private fun focusChannel(): String {
        awaitFocus(film)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val tag = compose.onAllNodes(isFocused()).fetchSemanticsNodes().single().config.getOrNull(SemanticsProperties.TestTag)!!
        assertTrue(tag, tag.startsWith("home-channel-"))
        return tag
    }

    private fun returnFromBackground(recreate: Boolean) {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        if (recreate) compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { exists("screen-home") }
    }

    private fun awaitPlayer(action: String) {
        try {
            compose.waitUntil(10_000) { exists("screen-player") }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("$action did not open the visible player; Home still visible=${exists("screen-home")}", failure)
        }
    }

    @Test
    fun aChannelStillOpensAfterAnOrdinaryBackgroundReturn() {
        val channel = focusChannel()
        returnFromBackground(recreate = false)
        awaitFocus(channel)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitPlayer("Channel after background return")
    }

    @Test
    fun aChannelOpensAfterTheBackgroundActivityIsRecreated() {
        val channel = focusChannel()
        returnFromBackground(recreate = true)
        awaitFocus(channel)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitPlayer("Channel after activity recreation")
        // Playback is dropped by the app's existing restore policy, including on recreation.
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { exists("screen-home") && !exists("screen-player") }
        awaitFocus(channel)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitPlayer("Channel after a second recreation")
        repeat(3) { if (!exists("screen-guide")) press(KeyEvent.KEYCODE_BACK) }
        compose.waitUntil(10_000) { exists("screen-guide") }
        assertTrue("guide return lost the chosen channel", graph.guideFocusChannel == "fixture-0:c1")
    }

    @Test
    fun aContinueWatchingFilmAlsoOpensAfterActivityRecreation() {
        awaitFocus(film)
        returnFromBackground(recreate = true)
        awaitFocus(film)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitPlayer("Continue watching after activity recreation")
        repeat(3) { if (!exists("screen-film")) press(KeyEvent.KEYCODE_BACK) }
        compose.waitUntil(10_000) { exists("screen-film") }
    }

    @Test
    fun recreatingHomeKeepsStartupWorkBounded() {
        awaitFocus(film)
        repeat(6) { sample ->
            var started = 0L
            compose.runOnUiThread { started = Debug.threadCpuTimeNanos() }
            returnFromBackground(recreate = true)
            awaitFocus(film)
            var cpuMs = 0L
            compose.runOnUiThread { cpuMs = (Debug.threadCpuTimeNanos() - started) / 1_000_000 }
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("stream", "Home recreation sample=$sample mainCpuMs=$cpuMs\n")
            })
        }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
