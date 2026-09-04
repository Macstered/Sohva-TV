package com.streammate.tv.feature.player

import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the player says about a stream it is playing.
 *
 * Every reading here is a claim, and the one failure mode that matters is a
 * number appearing where nothing was measured: a zero bitrate reads exactly
 * like a stream delivering nothing, and a percentage that keeps counting past
 * the end of a programme reads like a programme still running.
 */
class PlayerFormattingTest {

    private fun stats(
        width: Int = 1920,
        height: Int = 1080,
        frameRate: Float = 50f,
        videoCodec: String? = "avc",
        videoBitrate: Int = 7_400_000,
        audioCodec: String? = "mp4a-latm",
        audioChannels: Int = 6,
        bufferedAheadMillis: Long = 12_300L,
        droppedFrames: Int? = null,
    ) = PlaybackStats(
        width = width,
        height = height,
        frameRate = frameRate,
        videoCodec = videoCodec,
        videoBitrate = videoBitrate,
        audioCodec = audioCodec,
        audioChannels = audioChannels,
        audioSampleRate = 48_000,
        bufferedAheadMillis = bufferedAheadMillis,
        droppedFrames = droppedFrames,
    )

    private fun values(stats: PlaybackStats) =
        playerStatsValues(stats, bufferLabel = "Buffer", droppedLabel = "Dropped")

    // ------------------------------------------------------- status line --

    @Test
    fun aFullyDescribedStreamReadsAsOneLine() {
        val line = values(stats())

        assertEquals(
            listOf("1920×1080 50p", "AVC · MP4A-LATM 5.1", "7.4 Mb/s", "12.3 s"),
            line.map(PlayerStatValue::value),
        )
        assertEquals(listOf(null, null, null, "Buffer"), line.map(PlayerStatValue::label))
    }

    @Test
    fun anUnmeasuredBitrateIsLeftOutRatherThanPrintedAsZero() {
        val line = values(stats(videoBitrate = Format.NO_VALUE))

        assertTrue(line.none { it.value.contains("Mb/s") })
        assertTrue(line.none { it.value.startsWith("0.0") })
    }

    @Test
    fun aStreamThatNamesNoCodecShowsNoCodecSegment() {
        val line = values(stats(videoCodec = null, audioCodec = null))

        assertEquals(listOf("1920×1080 50p", "7.4 Mb/s", "12.3 s"), line.map(PlayerStatValue::value))
    }

    @Test
    fun halfADescriptionIsBetterThanNone() {
        assertEquals(
            "AVC",
            values(stats(audioCodec = null)).map(PlayerStatValue::value)[1],
        )
        assertEquals(
            "MP4A-LATM 5.1",
            values(stats(videoCodec = null)).map(PlayerStatValue::value)[1],
        )
    }

    @Test
    fun aStreamWithNoDeclaredSizeShowsNoResolution() {
        val line = values(stats(width = 0, height = 0))

        assertTrue(line.none { it.value.contains("×") })
    }

    @Test
    fun aStreamWithNoDeclaredFrameRateStillShowsItsSize() {
        assertEquals("1920×1080", values(stats(frameRate = 0f)).first().value)
    }

    @Test
    fun channelCountsPeopleRecogniseAreNamedAndOthersAreCounted() {
        assertEquals("AVC · MP4A-LATM 2.0", values(stats(audioChannels = 2)).map { it.value }[1])
        assertEquals("AVC · MP4A-LATM 5.1", values(stats(audioChannels = 6)).map { it.value }[1])
        assertEquals("AVC · MP4A-LATM 3 ch", values(stats(audioChannels = 3)).map { it.value }[1])
        assertEquals(
            "AVC · MP4A-LATM",
            values(stats(audioChannels = Format.NO_VALUE)).map { it.value }[1],
        )
    }

    /**
     * A MediaController has no decoder to ask, so it reports null and the
     * reading is left off. Reporting zero would say "none dropped", which is a
     * different and unearned claim.
     */
    @Test
    fun droppedFramesAppearOnlyWhereSomethingCountedThem() {
        assertTrue(values(stats(droppedFrames = null)).none { it.label == "Dropped" })
        assertEquals(
            PlayerStatValue("Dropped", "0"),
            values(stats(droppedFrames = 0)).last(),
        )
        assertEquals(
            PlayerStatValue("Dropped", "7"),
            values(stats(droppedFrames = 7)).last(),
        )
    }

    @Test
    fun theBufferIsAlwaysReportedAndNeverNegative() {
        assertEquals("0.0 s", values(stats(bufferedAheadMillis = -5_000L)).last().value)
        assertEquals("2.5 s", values(stats(bufferedAheadMillis = 2_500L)).last().value)
    }

    // --------------------------------------------------- programme timing --

    @Test
    fun progressRunsFromTheStartOfAProgrammeToItsEnd() {
        assertEquals(0f, playerProgrammeFraction(0L, 100L, 0L)!!, 0.001f)
        assertEquals(0.44f, playerProgrammeFraction(0L, 100L, 44L)!!, 0.001f)
    }

    @Test
    fun aProgrammeNotRunningHasNoProgressToDraw() {
        assertNull("before it starts", playerProgrammeFraction(100L, 200L, 50L))
        assertNull("once it has ended", playerProgrammeFraction(100L, 200L, 200L))
        assertNull("with no length", playerProgrammeFraction(100L, 100L, 100L))
        assertNull("with a stop before its start", playerProgrammeFraction(200L, 100L, 150L))
    }

    @Test
    fun theWatchedPercentageIsWhole() {
        assertEquals(0, playerPercentWatched(0f))
        assertEquals(44, playerPercentWatched(0.446f))
        assertEquals(100, playerPercentWatched(1f))
        assertEquals(100, playerPercentWatched(2f))
        assertEquals(0, playerPercentWatched(-1f))
    }

    @Test
    fun remainingTimeRoundsUpAndStopsAtTheEnd() {
        assertEquals(1, playerRemainingMinutes(stopEpochMillis = 1_000L, nowEpochMillis = 0L))
        assertEquals(75, playerRemainingMinutes(stopEpochMillis = 75 * 60_000L, nowEpochMillis = 0L))
        assertEquals(2, playerRemainingMinutes(stopEpochMillis = 60_001L, nowEpochMillis = 0L))
        assertNull(playerRemainingMinutes(stopEpochMillis = 0L, nowEpochMillis = 0L))
        assertNull(playerRemainingMinutes(stopEpochMillis = 0L, nowEpochMillis = 1_000L))
    }

    @Test
    fun durationsSplitIntoHoursAndMinutes() {
        assertEquals(0 to 45, playerDurationParts(45))
        assertEquals(1 to 15, playerDurationParts(75))
        assertEquals(2 to 0, playerDurationParts(120))
    }
}
