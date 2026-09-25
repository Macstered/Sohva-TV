package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.EpisodeEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SeriesEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.vod.ProgressStore
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 40 §11 "Progress repository": the write rule, shared film positions, marks and forgetting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgressStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = 1_790_000_000_000L
    private val clock = object : Clock {
        override fun wallMillis(): Long = now
        override fun monotonicNanos(): Long = 0
    }
    private var who = "default"
    private val store = ProgressStore(db, Dispatchers.Unconfined, clock) { who }
    private val min = 60_000L

    @After
    fun close() = db.close()

    private fun film(id: String, work: String?) = MovieEntity(
        key = "vod:movie:s:$id", sourceId = "s", providerId = id, groupId = null, name = "Quiet Harbour $id", sortName = SortNames.of("Quiet Harbour $id"),
        year = 2020, rating = null, ratingX10 = null, posterUrl = null, streamUrlEnc = "enc", plot = null, providerOrder = 0, genre = null,
        workKey = work, primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
    )

    private fun seed() {
        db.movieImport().insert(listOf(film("a", "name:quiet harbour:2020"), film("b", "name:quiet harbour:2020"), film("c", null)))
        val series = db.seriesImport().insert(
            listOf(
                SeriesEntity(
                    key = "series:s:9", sourceId = "s", providerId = "9", groupId = null, name = "Northern Line", sortName = "northern line",
                    year = null, rating = null, ratingX10 = null, posterUrl = null, backdropUrl = null, plot = null, providerOrder = 0, genre = null,
                    workKey = null, primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
                ),
            ),
        ).single()
        db.episodeImport().insert(
            (1..3).map { n ->
                EpisodeEntity(
                    key = "vod:episode:s:e$n", seriesId = series, sourceId = "s", providerId = "e$n", season = if (n < 3) 1 else 2, number = n,
                    name = null, streamUrlEnc = "enc", plot = null, durationSeconds = 1_200, thumbnailUrl = null, contentHash = 1, generation = 1,
                )
            },
        )
    }

    @Test
    fun shortPositionsAndMissingDurationsAreNotSaved() = runBlocking {
        seed()
        store.save("vod:movie:s:c", 4_999, 90 * min)
        store.save("vod:movie:s:c", 10 * min, 0)
        store.save("not-a-key", 10 * min, 90 * min)
        assertNull(store.of("vod:movie:s:c"))
        store.save("vod:movie:s:c", 10 * min, 90 * min)
        assertEquals(10 * min, store.of("vod:movie:s:c")!!.resumeMs)
    }

    @Test
    fun aFinishedTitleStoresItsDurationAndResumesFromTheStart() = runBlocking {
        seed()
        store.save("vod:movie:s:c", 88 * min, 90 * min)
        val p = store.of("vod:movie:s:c")!!
        assertTrue(p.completed)
        assertEquals(90 * min, p.positionMs)
        assertEquals(0, p.resumeMs)
    }

    @Test
    fun copiesOfOneFilmShareThePlacePlayedLast() = runBlocking {
        seed()
        store.save("vod:movie:s:a", 20 * min, 90 * min)
        assertEquals(20 * min, store.of("vod:movie:s:b")!!.positionMs)
        now += 1_000
        store.save("vod:movie:s:b", 30 * min, 90 * min)
        assertEquals(30 * min, store.of("vod:movie:s:a")!!.positionMs)
        // Another film never shares.
        assertNull(store.of("vod:movie:s:c"))
    }

    @Test
    fun aFilmFinishedOnOneCopyIsTickedOnTheOther() = runBlocking {
        seed()
        val films = listOf("vod:movie:s:a" to "name:quiet harbour:2020", "vod:movie:s:b" to "name:quiet harbour:2020", "vod:movie:s:c" to null)
        assertEquals(emptySet<String>(), store.watched(films))
        store.save("vod:movie:s:a", 89 * min, 90 * min)
        store.save("vod:movie:s:c", 10 * min, 90 * min)
        assertEquals(setOf("vod:movie:s:a", "vod:movie:s:b"), store.watched(films))
        // A newer, unfinished place on the other copy takes the tick away from both.
        now += 1_000
        store.save("vod:movie:s:b", 20 * min, 90 * min)
        assertEquals(emptySet<String>(), store.watched(films))
    }

    @Test
    fun forgettingAFilmForgetsEveryCopy() = runBlocking {
        seed()
        store.save("vod:movie:s:a", 20 * min, 90 * min)
        store.save("vod:movie:s:b", 25 * min, 90 * min)
        store.forget("vod:movie:s:a")
        assertNull(store.of("vod:movie:s:a"))
        assertNull(store.of("vod:movie:s:b"))
    }

    @Test
    fun markingWatchedUsesTheKnownDuration() = runBlocking {
        seed()
        store.markWatched("vod:movie:s:c", 0)
        assertEquals(0L, store.of("vod:movie:s:c")!!.durationMs)
        store.save("vod:movie:s:c", 10 * min, 90 * min)
        store.markWatched("vod:movie:s:c", 0)
        val p = store.of("vod:movie:s:c")!!
        assertTrue(p.completed)
        assertEquals(90 * min, p.positionMs)
    }

    @Test
    fun positionsBelongToTheirProfile() = runBlocking {
        seed()
        store.save("vod:movie:s:c", 10 * min, 90 * min)
        who = "p1"
        assertNull(store.of("vod:movie:s:c"))
    }

    @Test
    fun aSeasonIsMarkedInOneGoAndReadForItsSeriesOnly() = runBlocking {
        seed()
        store.markSeasonWatched("series:s:9", 1)
        val rows = store.ofSeries("series:s:9")
        assertEquals(setOf("vod:episode:s:e1", "vod:episode:s:e2"), rows.keys)
        assertTrue(rows.values.all { it.completed && it.durationMs == 20 * min })
        store.save("vod:episode:s:e3", 5 * min, 20 * min)
        assertFalse(store.ofSeries("series:s:9")["vod:episode:s:e3"]!!.completed)
    }

    @Test
    fun continueWatchingKeepsOneCardPerFilmAndSeriesNewestFirst() = runBlocking {
        seed()
        db.sources().upsert(SourceEntity("s", "Fixture", "XTREAM", true, 0, 1, "VOD", 0, 0, 0))
        store.save("vod:movie:s:a", 10 * min, 90 * min)
        now += 1_000
        store.save("vod:episode:s:e1", 5 * min, 20 * min)
        now += 1_000
        // Another copy of the same film, played later: one card, for the copy played last.
        store.save("vod:movie:s:b", 20 * min, 90 * min)
        now += 1_000
        store.save("vod:episode:s:e3", 2 * min, 20 * min)
        now += 1_000
        store.markWatched("vod:movie:s:c", 90 * min)
        val feed = store.continueWatching()
        assertEquals(listOf("vod:episode:s:e3", "vod:movie:s:b"), feed.map { it.contentKey })
        // The query collapses before its limit: two episodes of one series and two copies of a film are one row each (spec 02 §8).
        assertEquals(listOf("vod:episode:s:e3"), db.progress().continueEpisodes(who, 1).map { it.contentKey })
        assertEquals(1, db.progress().continueFilms(who, 2).size)
        assertEquals(listOf("series:s:9", "vod:movie:s:b"), feed.map { it.groupKey })
        assertEquals("Northern Line", feed[0].title)
        assertEquals(listOf(2, 3), listOf(feed[0].season, feed[0].episode))
        assertEquals("Quiet Harbour b", feed[1].title)
        // Titles of a disabled source leave the row.
        db.sources().upsert(SourceEntity("s", "Fixture", "XTREAM", false, 0, 1, "VOD", 0, 0, 0))
        assertEquals(emptyList<String>(), store.continueWatching().map { it.contentKey })
    }
}
