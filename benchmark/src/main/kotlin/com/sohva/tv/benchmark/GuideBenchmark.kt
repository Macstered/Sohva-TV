package com.sohva.tv.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The M2 exit measurement (roadmap M2, spec 20 GUIDE-NFR-01): the real guide on the owner-scale
 * fixture (56,164 channels in 800 groups), fully compiled, on real D-pad presses. The main thread's
 * `Choreographer#doFrame` sum divided by the presses is the per-press cost (budget ≤ 11 ms between
 * rows, ≤ 7 ms along a row on the stand-in).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class GuideBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun betweenRows() = measure {
        repeat(PRESSES) {
            device.pressDPadDown()
            device.waitForIdle()
        }
    }

    @Test
    fun alongRow() = measure {
        device.pressDPadRight()
        device.waitForIdle()
        repeat(PRESSES) { i ->
            // Three right, three left: stays inside the window, never pages.
            if ((i / 3) % 2 == 0) device.pressDPadRight() else device.pressDPadLeft()
            device.waitForIdle()
        }
    }

    private fun measure(presses: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()) + SECTIONS.map { TraceSectionMetric(it, TraceSectionMetric.Mode.Sum) },
        compilationMode = CompilationMode.Full(),
        iterations = 5,
        setupBlock = {
            // Every iteration starts cold on Home, whatever the last one left on screen.
            killProcess()
            seedOwnerFixture()
            pressHome()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.res("screen-home")), WAIT_MS)) { "Home did not open" }
            focusRailItem("home-live")
            device.pressDPadCenter()
            check(device.wait(Until.hasObject(By.res("guide-row-0")), WAIT_MS)) { "the guide did not open" }
            // Let the first rows' programmes arrive before measuring.
            Thread.sleep(2_000)
            device.waitForIdle()
        },
    ) { presses() }

    private companion object {
        const val PRESSES = 30
        const val WAIT_MS = 15_000L

        val SECTIONS = listOf(
            "Choreographer#doFrame",
            "Guide:Row",
            "Guide:RowDraw",
            "Guide:Hero",
            "Recomposer:recompose",
            "AndroidOwner:measureAndLayout",
            "Record View#draw()",
            "deliverInputEvent",
            "compose:lazy:prefetch%",
        )
    }
}

/** Writes the owner-scale guide into the measured app once (the fixture activity skips a second time). */
fun MacrobenchmarkScope.seedOwnerFixture() {
    startActivityAndWait(Intent().setClassName(TARGET_PACKAGE, "com.sohva.tv.measure.fixture.FixtureActivity"))
    // The first run writes about 220,000 rows; later runs find them and finish at once.
    val deadline = System.currentTimeMillis() + 10 * 60_000L
    while (device.hasObject(By.textStartsWith("fixture-")) && System.currentTimeMillis() < deadline) Thread.sleep(500)
    check(!device.hasObject(By.textStartsWith("fixture-"))) { "the owner-scale fixture did not finish" }
}
