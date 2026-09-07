package com.streammate.tv.core.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsLogTest {
    @Before
    fun pin() {
        DiagnosticsLog.clear()
        DiagnosticsLog.mirrorToLogcat = false
        DiagnosticsLog.clock = { 1_757_232_000_000L }
    }

    @After
    fun release() {
        DiagnosticsLog.clear()
        DiagnosticsLog.mirrorToLogcat = true
        DiagnosticsLog.clock = System::currentTimeMillis
    }

    @Test
    fun `a line carries its time, level, tag and message`() {
        DiagnosticsLog.i("refresh", "playlist Home: 1200 channels")
        val line = DiagnosticsLog.snapshot().single()
        assertTrue(line, line.endsWith(" I/refresh: playlist Home: 1200 channels"))
        assertTrue(line, Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} ").containsMatchIn(line))
    }

    @Test
    fun `addresses and keys never reach the buffer`() {
        DiagnosticsLog.w(
            "http",
            "GET http://user:secret@provider.example/get.php?username=me&password=pw failed",
            IllegalStateException("token=abcdef refused"),
        )
        val line = DiagnosticsLog.snapshot().single()
        assertFalse(line, line.contains("secret"))
        assertFalse(line, line.contains("password=pw"))
        assertFalse(line, line.contains("abcdef"))
        assertTrue(line, line.contains("provider.example"))
        assertTrue(line, line.contains("IllegalStateException"))
    }

    @Test
    fun `the buffer keeps the newest lines only`() {
        repeat(DiagnosticsLog.CAPACITY + 25) { DiagnosticsLog.i("t", "line $it") }
        val kept = DiagnosticsLog.snapshot()
        assertEquals(DiagnosticsLog.CAPACITY, kept.size)
        assertTrue(kept.first().endsWith("line 25"))
        assertTrue(kept.last().endsWith("line ${DiagnosticsLog.CAPACITY + 24}"))
    }
}
