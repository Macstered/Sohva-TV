package com.sohva.tv.feature.player

import com.sohva.tv.core.model.player.SubtitleFormat
import com.sohva.tv.core.model.player.VodLanguages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Slow downloads must never overwrite a newer choice (ADDON-FR-98, PLAY-FR-141). */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleSelectionRaceTest {
    private class Source(private val ignoresCancellation: Boolean = false) : SubtitleSource {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<SubtitleDownload>()
        var cancelled = false

        override fun subtitles() = flow { emit(SubtitleResults(listOf(AUTO))) }

        override suspend fun download(key: String): SubtitleDownload {
            if (key == MANUAL.key) return ready("Manual")
            started.complete(Unit)
            return try {
                if (ignoresCancellation) withContext(NonCancellable) { finish.await() } else finish.await()
            } finally {
                cancelled = !finish.isCompleted
            }
        }

        override val showAllLanguages = MutableStateFlow(false)
        override suspend fun setShowAllLanguages(on: Boolean) = Unit
    }

    private fun TestScope.subtitles(source: Source, reload: () -> Unit = {}) = AddonSubtitles(
        source, { null }, { Tracks() }, { VodLanguages(subtitles = "fi") },
        backgroundScope, Dispatchers.Unconfined, reload = reload, readyAgain = { true },
    )

    @Test
    fun offWinsOverASlowLibraryAutomaticDownload() = runTest(UnconfinedTestDispatcher()) {
        val source = Source()
        val subs = subtitles(source)
        val automatic = async { subs.autoAddonOnly() }
        source.started.await()
        subs.off()
        source.finish.complete(ready("Automatic"))
        assertFalse("A stale download must not ask the player to reload", automatic.await())
        assertEquals(SubtitlePick.Off, subs.pick.value)
        assertFalse(subs.timingAvailable)
    }

    @Test
    fun embeddedChoiceCancelsADiscoverAutomaticDownload() = runTest(UnconfinedTestDispatcher()) {
        val source = Source()
        val subs = subtitles(source)
        val automatic = async { subs.auto() }
        source.started.await()
        subs.chooseEmbedded(TrackItem(1, 0, "English", "en", 0, false))
        assertTrue("Stop the unused download immediately", source.cancelled)
        assertFalse(automatic.await())
        assertEquals(SubtitlePick.Embedded("en", 1, 0), subs.pick.value)
        assertFalse(subs.timingAvailable)
    }

    @Test
    fun manualAddonWinsOverASlowAutomaticDownload() = runTest(UnconfinedTestDispatcher()) {
        val source = Source()
        var reloads = 0
        val subs = subtitles(source) { reloads++ }
        val automatic = async { subs.autoAddonOnly() }
        source.started.await()
        subs.choose(MANUAL) {}
        source.finish.complete(ready("Automatic"))
        assertFalse(automatic.await())
        assertEquals(SubtitlePick.Addon(MANUAL, "en"), subs.pick.value)
        assertEquals(1, reloads)
    }

    @Test
    fun offCancelsAnEarlierManualDownloadToo() = runTest(UnconfinedTestDispatcher()) {
        val source = Source()
        var reloads = 0
        var closed = false
        val subs = subtitles(source) { reloads++ }
        subs.choose(AUTO) { closed = true }
        source.started.await()
        subs.off()
        assertTrue(source.cancelled)
        source.finish.complete(ready("Stale"))
        assertEquals(SubtitlePick.Off, subs.pick.value)
        assertEquals(0, reloads)
        assertFalse(closed)
        assertFalse(subs.loading.value)
    }

    @Test
    fun leavingPlaybackCancelsTheAutomaticDownload() = runTest(UnconfinedTestDispatcher()) {
        val source = Source()
        val subs = subtitles(source)
        val automatic = async { subs.autoAddonOnly() }
        source.started.await()
        subs.release()
        assertTrue(source.cancelled)
        assertFalse(automatic.await())
        assertFalse(subs.timingAvailable)
    }

    @Test
    fun offRejectsAResultEvenWhenTheSourceIgnoresCancellation() = runTest(UnconfinedTestDispatcher()) {
        val source = Source(ignoresCancellation = true)
        val subs = subtitles(source)
        val automatic = async { subs.autoAddonOnly() }
        source.started.await()
        subs.off()
        source.finish.complete(ready("Late result"))
        assertFalse(automatic.await())
        assertEquals(SubtitlePick.Off, subs.pick.value)
        assertFalse(subs.timingAvailable)
    }

    @Test
    fun cancellingPlaybackDoesNotContinueDiscoverStartup() = runTest(UnconfinedTestDispatcher()) {
        val source = Source()
        val subs = subtitles(source)
        var continued = false
        val startup = launch { subs.auto(); continued = true }
        source.started.await()
        startup.cancelAndJoin()
        assertTrue(source.cancelled)
        assertFalse(continued)
    }

    private companion object {
        val AUTO = SubtitleCandidate("automatic", "Fictional subtitles", "fi", 1)
        val MANUAL = SubtitleCandidate("manual", "Fictional subtitles", "en", 1)

        fun ready(text: String) = SubtitleDownload.Ready(
            "1\n00:00:01,000 --> 00:00:02,000\n$text\n", SubtitleFormat.SUBRIP,
        )
    }
}
