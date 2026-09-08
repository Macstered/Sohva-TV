package com.streammate.tv.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The panel half of the display line goes through `DisplayManager`, which a
 * JVM test cannot reach, and it is wrapped in a `runCatching` that would hide
 * a failure by quietly dropping it. This asserts the whole line survives on a
 * real Android runtime, so a tester's report actually carries the two numbers
 * that answer "the interface does not fill my television".
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsDisplayTest {

    @Test
    fun theDisplayLineNamesThePanelAndTheAppsOwnSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val summary = displaySummary(context)
        assertTrue(summary, summary.contains("panel "))
        assertTrue(summary, summary.contains(" Hz"))
        assertTrue(summary, summary.contains("app "))
        assertTrue(summary, summary.contains(" dp at "))
        assertTrue(summary, Regex("""panel \d+x\d+""").containsMatchIn(summary))
        assertTrue(summary, Regex("""app \d+x\d+ px""").containsMatchIn(summary))
    }
}
