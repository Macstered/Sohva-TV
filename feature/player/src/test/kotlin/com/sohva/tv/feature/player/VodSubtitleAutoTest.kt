package com.sohva.tv.feature.player

import com.sohva.tv.core.model.player.SubtitleFormat
import com.sohva.tv.core.model.player.VodLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 30 PLAY-FR-141: a library title looks for an addon subtitle only when the file has none in a
 * preferred language, and never chooses anything otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VodSubtitleAutoTest {
    private class Source : SubtitleSource {
        var asked = 0
        val downloads = mutableListOf<String>()

        override fun subtitles(): Flow<SubtitleResults> = flow {
            asked++
            emit(SubtitleResults(listOf(SubtitleCandidate("k-en", "Fictional subs", "eng", 1), SubtitleCandidate("k-fi", "Fictional subs", "fin", 1))))
        }

        override suspend fun download(key: String): SubtitleDownload {
            downloads += key
            return SubtitleDownload.Ready("1\n00:00:01,000 --> 00:00:02,000\nHei\n", SubtitleFormat.SUBRIP)
        }

        override val showAllLanguages: StateFlow<Boolean> = MutableStateFlow(false)

        override suspend fun setShowAllLanguages(on: Boolean) = Unit
    }

    private fun track(language: String, audio: Boolean = false) = TrackItem(0, 0, language, language, if (audio) 2 else 0, false)

    private fun kotlinx.coroutines.test.TestScope.subtitles(source: Source, tracks: Tracks, prefs: VodLanguages) =
        AddonSubtitles(source, { null }, { tracks }, { prefs }, backgroundScope, Dispatchers.Unconfined, reload = {}, readyAgain = { true })

    @Test
    fun withoutPreferencesNothingIsAsked() = runTest(UnconfinedTestDispatcher()) {
        val source = Source()
        assertFalse(subtitles(source, Tracks(text = emptyList()), VodLanguages()).autoAddonOnly())
        assertEquals(0, source.asked)
    }

    @Test
    fun aFileWithAPreferredSubtitleKeepsItsOwn() = runTest(UnconfinedTestDispatcher()) {
        val source = Source()
        val subs = subtitles(source, Tracks(text = listOf(track("fi"))), VodLanguages(subtitles = "fi"))
        assertFalse(subs.autoAddonOnly())
        assertEquals(0, source.asked)
        assertEquals("nothing is chosen: the player's own choice stands", null, subs.pick.value)
    }

    @Test
    fun finnishAudioNamesAlsoAvoidUnneededAddonSubtitleRequests() = runTest(UnconfinedTestDispatcher()) {
        for ((language, label) in listOf("Finnish" to "Finnish", null to "Finnish", "und" to "Suomi")) {
            val source = Source()
            val tracks = Tracks(audio = listOf(TrackItem(0, 0, label, language, 2, false)))
            val subs = subtitles(source, tracks, VodLanguages(audio = "fi", subtitles = "fi"))
            assertFalse(subs.autoAddonOnly())
            assertEquals(0, source.asked)
            assertEquals(null, subs.pick.value)
        }
    }

    @Test
    fun aFileWithoutOneGetsTheAddonsInThePreferredLanguage() = runTest(UnconfinedTestDispatcher()) {
        val source = Source()
        val subs = subtitles(source, Tracks(text = listOf(track("en"))), VodLanguages(subtitles = "fi"))
        assertTrue(subs.autoAddonOnly())
        assertEquals(listOf("k-fi"), source.downloads)
        assertTrue(subs.pick.value is SubtitlePick.Addon)
    }
}
