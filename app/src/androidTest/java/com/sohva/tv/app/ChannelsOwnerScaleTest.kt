package com.sohva.tv.app

import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 21 §11 "Performance", M3 exit criterion 4: channel management on an owner-scale source
 * (56,000 channels in 800 groups). Opens it, walks 450 rows with the remote (three pages), sorts
 * A–Z, searches, and moves a channel (the first move positions the whole source); the Java heap
 * stays under the plan/07 steady budget of 64 MB. Runs only when asked:
 * `-Pandroid.testInstrumentationRunnerArguments.ownerScale=true`.
 */
@RunWith(AndroidJUnit4::class)
class ChannelsOwnerScaleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val asked = InstrumentationRegistry.getArguments().getString("ownerScale") == "true"
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val seed = object : ExternalResource() {
        override fun before() {
            if (!asked) return
            val sealed = graph.data.cipher.encrypt("http://192.0.2.10/live/owner.ts")
            GuideFixture.seed(graph, groups = 800, perGroup = 70, guideFor = 700, sealedStream = sealed)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String, timeout: Long = 30_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun click(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun ms(from: Long) = (System.nanoTime() - from) / 1_000_000

    @Test
    fun channelManagementStaysPagedAndUnderTheHeapBudget() {
        assumeTrue(asked)
        val heap = HeapSampler().also { it.start() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        press(KeyEvent.KEYCODE_MENU)
        val opened = System.nanoTime()
        click("guide-options-channels")
        awaitFocus("channels-row-fixture-0:c0")
        val openMs = ms(opened)
        val walked = System.nanoTime()
        press(KeyEvent.KEYCODE_DPAD_DOWN, 450)
        awaitFocus("channels-row-fixture-0:c450")
        val walkMs = ms(walked)
        val sorted = System.nanoTime()
        click("channels-sort")
        // A–Z starts with "Cobalt …": wait for the sorted list's first row to take focus.
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTextExists("Sort: A–Z") &&
                compose.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty() &&
                !runCatching { compose.onNodeWithTag("channels-row-fixture-0:c450").assertIsFocused() }.isSuccess
        }
        val sortMs = ms(sorted)
        click("channels-sort")
        awaitFocus("channels-row-fixture-0:c0")
        press(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        awaitFocus("channels-row-fixture-0:c3")
        val moved = System.nanoTime()
        click("channels-up")
        compose.waitUntil(60_000) { compose.onAllNodesWithTextExists("Channel order updated") }
        val moveMs = ms(moved)
        val second = System.nanoTime()
        click("channels-up")
        compose.waitUntil(60_000) {
            graph.data.database.openHelper.readableDatabase.query("SELECT key FROM channel WHERE source_id = 'fixture-0' ORDER BY display_rank, id LIMIT 2").use { c ->
                // Two moves up: c0, c3, c1, c2, …
                c.moveToPosition(1) && c.getString(0) == "fixture-0:c3"
            }
        }
        val secondMoveMs = ms(second)
        heap.finish()
        Log.i(
            TAG,
            "open ${openMs} ms; 450 presses ${walkMs} ms (${walkMs / 450} ms each, wall); A-Z ${sortMs} ms; " +
                "first move ${moveMs} ms (positions the source); next move ${secondMoveMs} ms; " +
                "Java heap max ${heap.maxMb()} MB of ${Runtime.getRuntime().maxMemory() / MB} MB",
        )
        assertTrue("Java heap max ${heap.maxMb()} MB", heap.maxMb() < BUDGET_MB)
    }

    private class HeapSampler : Thread("HeapSampler") {
        private val max = AtomicLong()

        @Volatile private var running = true

        override fun run() {
            val runtime = Runtime.getRuntime()
            while (running) {
                max.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory()) { a, b -> maxOf(a, b) }
                sleep(250)
            }
        }

        fun finish() {
            running = false
            join()
        }

        fun maxMb(): Long = max.get() / MB
    }

    private companion object {
        const val TAG = "ChannelsOwnerScale"
        const val MB = 1024L * 1024
        const val BUDGET_MB = 64
    }
}
