package com.streammate.tv.app

import com.streammate.tv.feature.settings.AppUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateUiMappingTest {
    private val update = AvailableUpdate(
        versionName = "0.1.0-beta.11",
        versionCode = 12,
        notes = "# Sohva TV 0.1.0-beta.11\n\nAndroid build **12**.\n\n## Changed since beta 10\n\n- **A faster skip.** Holding climbs\n  the ladder.\n\n## Instructions\n",
        apk = ReleaseAsset("sohva-tv.apk", "https://example.invalid/a.apk", 1L),
        checksums = null,
    )

    @Test
    fun `up to date carries the installed build's own notes and no update`() {
        val ui = AppUpdateState.UpToDate.toUiState("0.1.0-beta.10", "• Profiles.")

        assertEquals(AppUpdateUiState.Phase.UP_TO_DATE, ui.phase)
        assertEquals("0.1.0-beta.10", ui.installedVersionName)
        assertEquals("• Profiles.", ui.installedNotes)
        assertNull(ui.notes)
        assertNull(ui.versionName)
    }

    @Test
    fun `an available update shows what changed in it as plain lines`() {
        val ui = AppUpdateState.Available(update).toUiState("0.1.0-beta.10", "• Profiles.")

        assertEquals(AppUpdateUiState.Phase.AVAILABLE, ui.phase)
        assertEquals("0.1.0-beta.11", ui.versionName)
        assertEquals("• A faster skip. Holding climbs the ladder.", ui.notes)
        assertEquals("• Profiles.", ui.installedNotes)
    }

    @Test
    fun `a failed download still names the update it was for`() {
        val ui = AppUpdateState.Failed(AppUpdateFailure.CHECKSUM_MISMATCH, update).toUiState("0.1.0-beta.10")

        assertEquals(AppUpdateUiState.Phase.FAILED, ui.phase)
        assertEquals(AppUpdateUiState.Failure.CHECKSUM_MISMATCH, ui.failure)
        assertEquals("0.1.0-beta.11", ui.versionName)
        assertNull(ui.installedNotes)
    }
}
