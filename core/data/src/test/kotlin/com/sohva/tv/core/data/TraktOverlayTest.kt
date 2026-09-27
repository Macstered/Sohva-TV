package com.sohva.tv.core.data

import com.sohva.tv.core.data.database.TraktStateEntity
import com.sohva.tv.core.data.trakt.TraktOverlay
import com.sohva.tv.core.data.vod.ContinueItem
import com.sohva.tv.core.data.vod.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 51 §11 "Overlays": the merge with local progress (FR-33) and Continue watching's join (FR-34). */
class TraktOverlayTest {
    private fun row(progress: Double, watched: Boolean, at: Long) =
        TraktStateEntity("p", "movie:tmdb:603", "movie", 603, null, null, null, progress, watched, if (watched) 1 else 0, at)

    @Test
    fun theNewerSideWinsAndTheLocalPlaceWinsATie() {
        val local = Progress(600_000, 6_000_000, false, 100)
        assertSame(local, TraktOverlay.merge(local, row(50.0, false, 100)))
        assertEquals(Progress(3_000_000, 6_000_000, false, 200), TraktOverlay.merge(local, row(50.0, false, 200)))
        // Neither paused nor watched: ignored.
        assertSame(local, TraktOverlay.merge(local, row(0.0, false, 300)))
        assertNull(TraktOverlay.merge(null, null))
    }

    @Test
    fun aPauseWithoutARuntimeIsOnlyABarAndAWatchedMarkCompletes() {
        val bar = TraktOverlay.merge(null, row(25.0, false, 10))!!
        assertEquals(0L, bar.resumeMs)
        assertEquals(0.25f, bar.fraction, 0.001f)
        assertTrue(TraktOverlay.merge(null, row(0.0, true, 10))!!.completed)
        // A rewatch in progress outranks Trakt's own watched mark; the episode's runtime positions it.
        val rewatch = TraktOverlay.merge(null, row(40.0, true, 10), runtimeMs = 1_000_000)!!
        assertEquals(400_000L, rewatch.positionMs)
        assertEquals(false, rewatch.completed)
    }

    private fun item(key: String, group: String, tmdb: String?, at: Long, season: Int? = null) = ContinueItem(
        key, group, "Title", null, null, null, false, season, null, null, tmdb, 1, 10, at,
    )

    @Test
    fun continueWatchingKeepsTheNewerCardPerIdentity() {
        val local = listOf(item("vod:movie:a:1", "vod:movie:a:1", "603", 50), item("vod:episode:a:9", "series:a:s", null, 40, season = 1))
        val trakt = listOf(item("vod:movie:b:1", "vod:movie:b:1", "603", 60), item("vod:episode:a:8", "series:a:s", null, 40, season = 1))
        val joined = TraktOverlay.join(local, trakt, 20)
        // The film's newer Trakt pause replaces the local card of another copy; the series tie keeps the local card.
        assertEquals(listOf("vod:movie:b:1", "vod:episode:a:9"), joined.map { it.contentKey })
        assertEquals(1, TraktOverlay.join(local, trakt, 1).size)
    }
}
