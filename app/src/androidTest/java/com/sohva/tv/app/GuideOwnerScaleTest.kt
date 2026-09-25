package com.sohva.tv.app

import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Spec 20 §11 "Performance": on an owner-scale source (56,000 channels in 800 groups) open the
 * guide, open All channels, dial a channel near the end: the Java heap stays under the plan/07
 * steady budget of 64 MB. Runs only when asked:
 * `-Pandroid.testInstrumentationRunnerArguments.ownerScale=true`.
 */
@RunWith(AndroidJUnit4::class)
class GuideOwnerScaleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val asked = InstrumentationRegistry.getArguments().getString("ownerScale") == "true"
    private val seed = object : ExternalResource() {
        override fun before() {
            if (!asked) return
            val app = instrumentation.targetContext.applicationContext as SohvaApplication
            val sealed = app.graph.data.cipher.encrypt("http://192.0.2.10/live/owner.ts")
            GuideFixture.seed(app.graph, groups = 800, perGroup = 70, guideFor = 700, sealedStream = sealed)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String, timeout: Long = 15_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    @Test
    fun theHeapStaysUnderBudgetOnAnOwnerScaleSource() {
        assumeTrue(asked)
        val heap = HeapSampler().also { it.start() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        val opened = System.nanoTime()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        val firstGroupMs = (System.nanoTime() - opened) / 1_000_000
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("guide-rail-group:g0")
        press(KeyEvent.KEYCODE_DPAD_UP, 2)
        awaitFocus("guide-rail-all")
        val all = System.nanoTime()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0", 30_000)
        val allMs = (System.nanoTime() - all) / 1_000_000
        // Channel 55,990 by its own number, far down All channels.
        for (d in listOf(5, 5, 9, 9, 0).take(4)) press(KeyEvent.KEYCODE_0 + d)
        awaitFocus("guide-row-5598", 15_000)
        press(KeyEvent.KEYCODE_DPAD_DOWN, 10)
        heap.finish()
        Log.i(TAG, "first group ${firstGroupMs} ms, All channels ${allMs} ms; Java heap max ${heap.maxMb()} MB of ${Runtime.getRuntime().maxMemory() / MB} MB")
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
        const val TAG = "GuideOwnerScale"
        const val MB = 1024L * 1024
        const val BUDGET_MB = 64
    }
}
