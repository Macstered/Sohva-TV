package com.streammate.tv.app

import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.iptv.repository.SourceRefreshHealth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {
    private val source = IptvSourceConfiguration(
        id = "m3u-1",
        name = "Home",
        type = IptvSourceType.M3U,
        m3uUrl = "http://user:pw@provider.example/list.m3u",
        importScope = IptvImportScope.BOTH,
    )
    private val info = DiagnosticsInfo(
        generatedAtEpochMillis = 1_757_232_000_000L,
        appVersion = "0.1.0-beta.7 (build 8)",
        packageName = "com.streammate.tv",
        device = "NVIDIA SHIELD Android TV (mdarcy)",
        android = "11 (API 30)",
        locale = "fi-FI",
        deviceTimeZone = "Europe/Helsinki",
        display = "panel 3840x2160 at 60.0 Hz, reported 1920x1080 px; app 1920x1080 px, 960x540 dp at 320 dpi",
        sqliteVersion = "3.32.2",
        preferences = AppPreferences(),
        sources = listOf(source),
        health = listOf(
            SourceRefreshHealth(
                sourceId = "m3u-1", kind = "epg", status = "failed",
                lastAttemptAtEpochMillis = 1_757_231_000_000L, lastSuccessAtEpochMillis = null,
                lastFailureAtEpochMillis = 1_757_231_000_000L,
                lastError = "HTTP 503 from http://provider.example/epg.xml?password=pw",
                itemCount = 0, consecutiveFailures = 3,
            ),
        ),
        log = listOf("2026-09-07 10:00:00 W/epg: m3u-1: failed · IOException: timeout"),
    )

    @Test
    fun `the file names the app, the device, each source and each refresh state`() {
        val text = renderDiagnostics(info)
        assertTrue(text, text.contains("App: 0.1.0-beta.7 (build 8)"))
        assertTrue(text, text.contains("SQLite: 3.32.2"))
        assertTrue(text, text.contains("Home: m3u, in use, imports both"))
        assertTrue(text, text.contains("Home / epg: failed, items 0, failures in a row 3"))
        assertTrue(text, text.contains("Recent events (1)"))
        assertTrue(text, text.contains("W/epg: m3u-1: failed"))
    }

    /**
     * A tester reporting that the interface does not fill the television needs
     * the panel and the app's own size beside each other: a window smaller
     * than the panel is overscan compensation, not a layout fault.
     */
    @Test
    fun `the display line carries both the panel and the app's own size`() {
        val text = renderDiagnostics(info)
        assertTrue(text, text.contains("Display: panel 3840x2160 at 60.0 Hz"))
        assertTrue(text, text.contains("app 1920x1080 px, 960x540 dp at 320 dpi"))
    }

    @Test
    fun `nothing secret survives, addresses included`() {
        val text = renderDiagnostics(info)
        assertFalse(text, text.contains("user:pw"))
        assertFalse(text, text.contains("password=pw"))
        assertFalse(text, text.contains("list.m3u"))
        assertTrue(text, text.contains("provider.example"))
    }
}
