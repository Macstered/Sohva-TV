package com.sohva.tv.benchmark

import android.view.KeyEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The M3 exit measurement (roadmap M3, spec 21 CHAN-NFR-03): D-pad presses down channel
 * management's list on the owner-scale fixture, fully compiled. Each press moves the selection,
 * so the editor pane follows it; the presses cross a page boundary (200 rows). Per-press cost is
 * main-thread CPU inside `Choreographer#doFrame`, as for the guide (budget ≤ 11 ms).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ChannelsBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun downTheList() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()) + SECTIONS.map { TraceSectionMetric(it, TraceSectionMetric.Mode.Sum) },
        compilationMode = CompilationMode.Full(),
        iterations = 5,
        setupBlock = {
            killProcess()
            seedOwnerFixture()
            pressHome()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.res("home-live")), WAIT_MS)) { "Home did not open" }
            device.pressDPadCenter()
            check(device.wait(Until.hasObject(By.res("guide-row-0")), WAIT_MS)) { "the guide did not open" }
            device.pressKeyCode(KeyEvent.KEYCODE_MENU)
            check(device.wait(Until.hasObject(By.res("guide-options-channels")), WAIT_MS)) { "the options did not open" }
            device.findObject(By.res("guide-options-channels")).click()
            check(device.wait(Until.hasObject(By.res("channels-editor")), WAIT_MS)) { "channel management did not open" }
            // Start at row 185, so the presses cross the first page boundary.
            repeat(START) { device.pressDPadDown() }
            Thread.sleep(2_000)
            device.waitForIdle()
        },
    ) {
        repeat(PRESSES) {
            device.pressDPadDown()
            device.waitForIdle()
        }
    }

    private companion object {
        const val START = 185
        const val PRESSES = 30
        const val WAIT_MS = 15_000L

        val SECTIONS = listOf(
            "Choreographer#doFrame",
            "Recomposer:recompose",
            "AndroidOwner:measureAndLayout",
            "Record View#draw()",
            "deliverInputEvent",
        )
    }
}
