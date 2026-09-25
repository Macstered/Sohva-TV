package com.sohva.tv.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The M4 exit measurement (roadmap M4, spec 40 §9.1 and §11 "Performance"): the Movies wall on the
 * owner-scale library (200,000 films in 800 groups, 40,000 in "Blockbusters"), fully compiled.
 * [openBigGroup] times OK on the group row to the first page published (`Library:Load`, budget
 * ≤ 700 ms on the stand-in); [downTheBigGroup] presses Down 30 times through it, crossing pages
 * (main-thread CPU per press, budget ≤ 11 ms), with the heap at the end.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class WallBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun openBigGroup() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(TraceSectionMetric("Library:Load", TraceSectionMetric.Mode.Sum)),
        compilationMode = CompilationMode.Full(),
        iterations = 5,
        setupBlock = { openMovies() },
    ) {
        openBigGroup()
    }

    @Test
    fun downTheBigGroup() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)) +
            SECTIONS.map { TraceSectionMetric(it, TraceSectionMetric.Mode.Sum) },
        compilationMode = CompilationMode.Full(),
        iterations = 5,
        setupBlock = {
            openMovies()
            openBigGroup()
            device.pressDPadRight()
            Thread.sleep(1_000)
            device.waitForIdle()
        },
    ) {
        repeat(PRESSES) {
            device.pressDPadDown()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.openMovies() {
        killProcess()
        seedOwnerFixture()
        pressHome()
        startActivityAndWait()
        focusRailItem("home-movies")
        device.pressDPadCenter()
        check(device.wait(Until.hasObject(By.res(BIG_ROW)), WAIT_MS)) { "Movies did not open" }
        device.waitForIdle()
    }

    /** First entry focuses History; Blockbusters is the next row (A–Z). */
    private fun MacrobenchmarkScope.openBigGroup() {
        check(device.wait(Until.hasObject(By.res("library-row-history").focused(true)), WAIT_MS)) { "History not focused" }
        device.pressDPadDown()
        check(device.wait(Until.hasObject(By.res(BIG_ROW).focused(true)), WAIT_MS)) { "Blockbusters not focused" }
        device.pressDPadCenter()
        check(device.wait(Until.hasObject(By.res(CARD)), WAIT_MS)) { "the group did not open" }
        device.waitForIdle()
    }

    private companion object {
        const val PRESSES = 30
        const val WAIT_MS = 20_000L
        const val BIG_ROW = "library-row-group:0 | blockbusters"
        val CARD: Pattern = Pattern.compile("library-card-vod:movie:owner-vod:.*")

        val SECTIONS = listOf(
            "Choreographer#doFrame",
            "Library:Load",
            "Recomposer:recompose",
            "AndroidOwner:measureAndLayout",
            "deliverInputEvent",
        )
    }
}
