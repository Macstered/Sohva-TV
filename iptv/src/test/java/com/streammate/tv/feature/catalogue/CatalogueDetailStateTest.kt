package com.streammate.tv.feature.catalogue

import com.streammate.tv.iptv.repository.WatchingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a detail page offers to do with a title.
 *
 * The whole action row hangs off one question - is there somewhere to pick up
 * from - so it is answered here rather than inside a composable, where the
 * difference between "no progress" and "finished" is easy to get wrong and
 * impossible to check.
 */
class CatalogueDetailStateTest {

    private fun progress(
        positionMillis: Long,
        durationMillis: Long = HOUR_MILLIS,
        completed: Boolean = false,
    ) = WatchingProgress(
        contentKey = "vod:movie:test:1",
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        completed = completed,
        lastWatchedEpochMillis = 0L,
    )

    @Test
    fun aPartWatchedTitleResumesWhereItWasLeft() {
        assertEquals(
            15 * MINUTE_MILLIS,
            catalogueResumePosition(progress(positionMillis = 15 * MINUTE_MILLIS)),
        )
    }

    @Test
    fun aTitleNeverStartedHasNowhereToResumeFrom() {
        assertNull(catalogueResumePosition(null))
        assertNull(catalogueResumePosition(progress(positionMillis = 0L)))
    }

    /**
     * A finished film starts again rather than resuming onto its own credits.
     * `resumePositionMillis` already reports zero for it; this pins the whole
     * decision so the page shows one Watch button rather than a Resume beside a
     * Start-from-beginning that do the same thing.
     */
    @Test
    fun aFinishedTitleOffersNoResume() {
        assertNull(
            catalogueResumePosition(
                progress(positionMillis = HOUR_MILLIS, completed = true),
            ),
        )
    }

    // ---------------------------------------------------------- durations --

    @Test
    fun minutesRoundUpSoASecondInIsNotZero() {
        assertEquals(0, millisToMinutes(0L))
        assertEquals(1, millisToMinutes(1_000L))
        assertEquals(1, millisToMinutes(MINUTE_MILLIS))
        assertEquals(2, millisToMinutes(MINUTE_MILLIS + 1))
        assertEquals(60, millisToMinutes(HOUR_MILLIS))
        assertEquals(119, millisToMinutes(119 * MINUTE_MILLIS))
    }

    @Test
    fun aNegativeDurationCannotProduceNegativeMinutes() {
        assertEquals(0, millisToMinutes(-1L))
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    }
}
