package com.sohva.tv.core.data

import com.sohva.tv.core.data.diagnostics.RingDiagnosticsLog
import com.sohva.tv.core.model.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingDiagnosticsLogTest {
    private val clock = object : Clock {
        override fun wallMillis(): Long = 1_790_000_000_000
        override fun monotonicNanos(): Long = 0
    }

    @Test
    fun keepsOnlyTheNewestLines() {
        val log = RingDiagnosticsLog(clock, capacity = 3)
        repeat(5) { log.info("event", "line $it") }
        val lines = log.snapshot()
        assertEquals(3, lines.size)
        assertTrue(lines.first().endsWith("event: line 2"))
        assertTrue(lines.last().endsWith("event: line 4"))
    }

    @Test
    fun redactsMessagesAndExceptionsBeforeStoring() {
        val mirrored = mutableListOf<String>()
        val log = RingDiagnosticsLog(clock) { _, line -> mirrored += line }
        log.info("import", "GET http://viewer:pw@provider.example/get.php?username=a&password=b")
        log.error("import", "failed", IllegalStateException("token=abc at /live/u/p/1.ts"))
        val text = log.snapshot().joinToString("\n")
        assertFalse(text, text.contains("viewer") || text.contains("pw") || text.contains("abc") || text.contains("/u/p/"))
        assertTrue(text.contains("http://provider.example/<redacted>"))
        assertTrue(text.contains("IllegalStateException: token=<redacted>"))
        // Logcat gets the line without its time (ABOUT-FR-26).
        assertEquals(log.snapshot().map { it.substring(20) }, mirrored)
    }

    @Test
    fun linesHaveTheSpecsShapeAndAreCut() {
        val log = RingDiagnosticsLog(clock)
        log.info("home", "cached resume ready: 12 ms")
        log.error("update", "check failed", java.io.IOException())
        log.info("long", "x".repeat(900))
        val lines = log.snapshot()
        assertTrue(lines[0], Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} I/home: cached resume ready: 12 ms$""").matches(lines[0]))
        assertTrue(lines[1], lines[1].endsWith("E/update: check failed · IOException: no message"))
        assertEquals(20 + RingDiagnosticsLog.MAX_LINE, lines[2].length)
    }
}
