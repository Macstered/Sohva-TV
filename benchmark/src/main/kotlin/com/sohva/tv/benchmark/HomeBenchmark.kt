package com.sohva.tv.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
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
 * The M5 exit measurements (roadmap M5, spec 02 §9 and §11 "Low-end performance") on the
 * owner-scale library with twelve paused films and six recent channels, fully compiled.
 * [coldStartToFirstCard]: a cold start until the first Continue watching card holds focus (budget
 * ≤ 4 s on the stand-in). [alongTheRow]: Right and Left along the row, 30 presses (main-thread CPU
 * per press; Home must not recompose per press, spec 02 §9.6), with the heap at the end.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class HomeBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartToFirstCard() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), TraceSectionMetric("Home:Screen", TraceSectionMetric.Mode.Count)),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { seedOwnerFixture() },
    ) {
        pressHome()
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.res(CARD).focused(true)), WAIT_MS)) { "no Continue watching card took focus" }
    }

    @Test
    fun alongTheRow() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)) +
            SECTIONS.map { TraceSectionMetric(it, TraceSectionMetric.Mode.Sum) } +
            TraceSectionMetric("Home:Screen", TraceSectionMetric.Mode.Count),
        compilationMode = CompilationMode.Full(),
        iterations = 5,
        setupBlock = {
            killProcess()
            seedOwnerFixture()
            pressHome()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.res(CARD).focused(true)), WAIT_MS)) { "no Continue watching card took focus" }
            Thread.sleep(1_000)
            device.waitForIdle()
        },
    ) {
        repeat(PRESSES) { i ->
            // Six right, six left: along the twelve cards and back.
            if ((i / 6) % 2 == 0) device.pressDPadRight() else device.pressDPadLeft()
            device.waitForIdle()
        }
    }

    private companion object {
        const val PRESSES = 30
        const val WAIT_MS = 20_000L
        val CARD: Pattern = Pattern.compile("home-resume-vod:.*")
        val SECTIONS = listOf("Choreographer#doFrame", "Recomposer:recompose", "AndroidOwner:measureAndLayout", "deliverInputEvent")
    }
}
