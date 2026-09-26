package com.sohva.tv.core.model

import com.sohva.tv.core.model.diagnostics.DeviceFacts
import com.sohva.tv.core.model.diagnostics.DiagnosticsReport
import com.sohva.tv.core.model.diagnostics.RefreshFact
import com.sohva.tv.core.model.diagnostics.SettingsFacts
import com.sohva.tv.core.model.diagnostics.SourceFact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 72 §7.6, ABOUT-FR-28 and the M7 exit criterion "diagnostics contain no secret". */
class DiagnosticsReportTest {
    private val device = DeviceFacts(
        "2026-09-26 11:30:00", "Europe/Helsinki", "0.2.0-beta.1", 100, "com.streammate.tv", "Google Android TV (generic)",
        "11 (API 30)", "fi-FI", "panel 1920x1080 at 60.0 Hz, reported 1920x1080 px; app 1920x1080 px, 960x540 dp at 320 dpi", "3.32.2",
    )
    private val settings = SettingsFacts(null, "normal", "home", 24, "default", "standard", "fi-FI", true, true)

    @Test
    fun theFileHasItsSectionsInOrder() {
        val lines = DiagnosticsReport.render(device, settings, emptyList(), emptyList(), emptyList())
        assertEquals("Sohva TV diagnostics", lines[0])
        assertEquals("App: 0.2.0-beta.1 (build 100) (com.streammate.tv)", lines[2])
        assertTrue("  time zone: TV's own" in lines)
        assertEquals(3, lines.count { it == "  none" })
        assertEquals("Recent events (0)", lines[lines.size - 2])
    }

    @Test
    fun noAddressKeyOrPasswordSurvives() {
        val sources = listOf(SourceFact("http://provider.example:8080/get.php?username=viewer&password=hunter2", "m3u", true, "both", 0))
        val refresh = listOf(
            RefreshFact("Home IPTV", "catalogue", "failed", 0, 2, "2026-09-26 11:00:00", "never", "2026-09-26 11:00:00", "Could not reach http://panel.provider.example/player_api.php?username=viewer&password=hunter2"),
            RefreshFact("Home IPTV", "playlist", "success", 120, 0, "2026-09-26 10:00:00", "2026-09-26 10:00:00", "never", null),
        )
        val events = listOf(
            "2026-09-26 11:00:01 E/http: GET http://192.0.2.10/live/viewer/hunter2/7.ts: HTTP 403",
            "2026-09-26 11:00:02 I/metadata: request api_key=abc123secret authorization: Bearer tok_zzz",
            "2026-09-26 11:00:03 E/http: GET http://viewer:hunter2@provider.example/list.m3u: no response",
        )
        val text = DiagnosticsReport.render(device, settings, sources, refresh, events).joinToString("\n")
        for (secret in listOf("hunter2", "viewer:", "abc123secret", "tok_zzz", "/live/viewer")) assertFalse("$secret in\n$text", text.contains(secret))
        // Kinds in the spec's order within a source.
        assertTrue(text.indexOf("Home IPTV / playlist") < text.indexOf("Home IPTV / catalogue"))
    }
}
