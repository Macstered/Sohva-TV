package com.sohva.tv.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Real Home and image loading: 240 titles, distinct posters/backdrops, no external network. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class PopulatedHomeBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    private var previousIdleWait: Long = 0

    @Before fun useFocusAssertionsForReadiness() {
        val config = Configurator.getInstance()
        previousIdleWait = config.waitForIdleTimeout
        // Implicit accessibility waits insert seconds between presses and defeat a rapid browse.
        // Each press instead waits for its exact focused card, then a bounded draw-settle interval.
        config.waitForIdleTimeout = 0
    }

    @After fun restoreDriverWait() {
        Configurator.getInstance().waitForIdleTimeout = previousIdleWait
    }

    @Test fun defaultColdStart() = startup(populated = false)
    @Test fun populatedColdStart() = startup(populated = true)

    private fun startup(populated: Boolean) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
        compilationMode = CompilationMode.Full(), startupMode = StartupMode.COLD, iterations = 10,
        setupBlock = { seed(populated) },
    ) {
        pressHome()
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.res(RESUME).focused(true)), WAIT_MS)) { "Home did not focus Continue watching" }
        if (populated) check(device.wait(Until.hasObject(By.res(card(0, 1))), WAIT_MS)) { "stored Trakt rows did not arrive" }
    }

    /** Thirty presses in a poster row, including cards outside the initially decoded window. */
    @Test fun alongPosterRow() = browse {
        repeat(30) { press ->
            val column = if (press < 15) press + 2 else 30 - press
            if (press < 15) device.pressDPadRight() else device.pressDPadLeft()
            awaitCard(0, column)
        }
    }

    /** Fourteen presses across all eight rows and back: exercises lazy row disposal and decoding. */
    @Test fun acrossEightRows() = browse {
        for (row in 1 until ROWS) { device.pressDPadDown(); awaitCard(row, 1) }
        for (row in ROWS - 2 downTo 0) { device.pressDPadUp(); awaitCard(row, 1) }
    }

    /** Resting focus lets each distinct hero decode; report heap and CPU for a realistic browse. */
    @Test fun browseWithHeroArtwork() = browse {
        repeat(12) { index ->
            device.pressDPadRight()
            awaitCard(0, index + 2)
            Thread.sleep(600)
        }
    }

    private fun browse(journey: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            TraceSectionMetric("Home:Screen", TraceSectionMetric.Mode.Count)) +
            listOf("Choreographer#doFrame", "Recomposer:recompose", "AndroidOwner:measureAndLayout").map {
                TraceSectionMetric(it, TraceSectionMetric.Mode.Sum)
            },
        compilationMode = CompilationMode.Full(), iterations = 5,
        setupBlock = {
            killProcess()
            seed(populated = true)
            // Stored rows must be read and images decoded afresh in each process.
            killProcess()
            pressHome()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.res(RESUME).focused(true)), WAIT_MS))
            check(device.wait(Until.hasObject(By.res(card(0, 1))), WAIT_MS))
            device.pressDPadDown()
            device.waitForIdle(50)
            // The rail is laid out at its expanded width. Android's accessibility occlusion
            // excludes the first narrow poster under that region, even while its label is hidden.
            // Start at the second card so every measured focus can be asserted directly.
            device.pressDPadRight()
            awaitCard(0, 1)
            Thread.sleep(1_000)
            device.waitForIdle(50)
        },
        measureBlock = journey,
    )

    private fun MacrobenchmarkScope.seed(populated: Boolean) {
        fixture("com.sohva.tv.measure.fixture.FixtureActivity", "", 600_000)
        fixture("com.sohva.tv.app.measure.PopulatedHomeFixtureActivity", "--ez populated $populated", 120_000)
    }

    private fun MacrobenchmarkScope.fixture(activity: String, extras: String, timeoutMs: Long) {
        // These setup activities finish shortly after writing. Macrobenchmark's launch-frame
        // confirmation can race their finish; only the measured MainActivity uses that API.
        val result = device.executeShellCommand("am start -W -n $TARGET_PACKAGE/$activity $extras")
        check("Error" !in result) { result }
        check(device.wait(Until.gone(By.textStartsWith("fixture-")), timeoutMs)) { "$activity did not finish" }
    }

    private fun MacrobenchmarkScope.awaitCard(row: Int, column: Int) {
        check(device.wait(Until.hasObject(By.res(card(row, column)).focused(true)), WAIT_MS)) {
            "Expected row $row card $column; focused ${device.findObject(By.focused(true))?.resourceName}"
        }
        device.waitForIdle(50)
    }

    private companion object {
        const val ROWS = 8
        const val WAIT_MS = 20_000L
        val RESUME: Pattern = Pattern.compile("home-resume-vod:.*")
        fun card(row: Int, column: Int) = "home-trakt-trakt:list:${810_000 + row}/movie:${910_000 + row * 30 + column}::"
    }
}
