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
 * The M0 guide-grid spike (roadmap M0): variant A (one canvas and one focusable per row) against
 * variant B (beta 23's structure), fully compiled, on real D-pad presses. The main thread's
 * `Choreographer#doFrame` sum divided by the presses is the per-press cost GUIDE-NFR-01 budgets
 * (≤ 11 ms between rows, ≤ 7 ms along a row on the stand-in); frame CPU percentiles come from
 * FrameTimingMetric. Read the variants against each other, never as device figures.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class GuideSpikeBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun canvasBetweenRows() = measure("CANVAS") { betweenRows() }

    @Test
    fun cellsBetweenRows() = measure("CELLS") { betweenRows() }

    @Test
    fun canvasAlongRow() = measure("CANVAS") { alongRow() }

    @Test
    fun cellsAlongRow() = measure("CELLS") { alongRow() }

    private fun measure(variant: String, presses: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()) + SECTIONS.map { TraceSectionMetric(it, TraceSectionMetric.Mode.Sum) },
        compilationMode = CompilationMode.Full(),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(
                Intent().setClassName(TARGET_PACKAGE, "com.sohva.tv.spike.guidegrid.SpikeActivity").putExtra("variant", variant),
            )
            device.wait(Until.hasObject(By.res("spike-grid")), 10_000)
            device.waitForIdle()
        },
    ) { presses() }

    /** 30 single presses down the rows, each allowed to settle, as a viewer browsing. */
    private fun MacrobenchmarkScope.betweenRows() = repeat(PRESSES) {
        device.pressDPadDown()
        device.waitForIdle()
    }

    /** Into the programmes, then 30 single presses back and forth along the row. */
    private fun MacrobenchmarkScope.alongRow() {
        device.pressDPadRight()
        device.waitForIdle()
        repeat(PRESSES) { i ->
            if ((i / 3) % 2 == 0) device.pressDPadRight() else device.pressDPadLeft()
            device.waitForIdle()
        }
    }

    private companion object {
        const val PRESSES = 30

        /** Where the main thread's time per press goes; prefix-matched section names. */
        val SECTIONS = listOf(
            "Choreographer#doFrame",
            "Guide:Row",
            "Guide:Channel",
            "Recomposer:recompose",
            "AndroidOwner:measureAndLayout",
            "Record View#draw()",
            "deliverInputEvent",
            "compose:lazy:prefetch%",
        )
    }
}
