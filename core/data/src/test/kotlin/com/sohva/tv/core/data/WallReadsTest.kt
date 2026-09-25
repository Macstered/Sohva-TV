package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.vod.ProgressStore
import com.sohva.tv.core.data.vod.WallDestination
import com.sohva.tv.core.data.vod.WallItem
import com.sohva.tv.core.data.vod.WallReads
import com.sohva.tv.core.data.vod.WallRoom
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 40 §11 "Pager": keyset both ways over ties, merged groups, search and History. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallReadsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = 1_790_000_000_000L
    private val clock = object : Clock {
        override fun wallMillis(): Long = now
        override fun monotonicNanos(): Long = 0
    }
    private val reads = WallReads(db, Dispatchers.Unconfined) { "default" }
    private val progress = ProgressStore(db, Dispatchers.Unconfined, clock) { "default" }

    @After
    fun close() = db.close()

    private fun group(source: String, name: String, count: Int): Long = db.groupImport().insert(
        ContentGroupEntity(sourceId = source, room = "MOVIES", groupKey = "name:${name.lowercase()}", name = name, providerOrder = 0, itemCount = count, shown = true, position = 0, sortMode = null),
    )

    private fun film(source: String, id: String, name: String, group: Long?, work: String? = null) = MovieEntity(
        key = "vod:movie:$source:$id", sourceId = source, providerId = id, groupId = group, name = name, sortName = SortNames.of(name),
        year = null, rating = null, ratingX10 = null, posterUrl = null, streamUrlEnc = "enc", plot = null, providerOrder = 0, genre = null,
        workKey = work, primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
    )

    private var dramaA = 0L
    private var dramaB = 0L

    private fun seed() = runBlocking {
        db.sources().upsert(SourceEntity("a", "First", "XTREAM", true, 0, 1, "BOTH", 0, 0, 0))
        db.sources().upsert(SourceEntity("b", "Second", "XTREAM", true, 0, 1, "VOD", 0, 0, 0))
        db.sources().upsert(SourceEntity("off", "Off", "XTREAM", false, 0, 1, "VOD", 0, 0, 0))
        dramaA = group("a", "Drama", 3)
        dramaB = group("b", " drama ", 2)
        group("a", "Comedy", 1)
        db.movieImport().insert(
            listOf(
                // Two titles with the same sort name: the id breaks the tie.
                film("a", "1", "Echo", dramaA), film("a", "2", "Echo", dramaA), film("a", "3", "Alpha", dramaA),
                film("b", "4", "Bravo", dramaB), film("b", "5", "Delta", dramaB),
                film("off", "6", "Charlie", null),
            ),
        )
    }

    private fun names(items: List<WallItem>) = items.map { it.row.key.substringAfterLast(':') }

    @Test
    fun groupsMergeByNameAcrossEnabledSources() = runBlocking {
        seed()
        val groups = reads.groups(WallRoom.MOVIES)
        assertEquals(listOf("Comedy", "Drama"), groups.map { it.name })
        assertEquals(5, groups[1].count)
        assertEquals(setOf(dramaA, dramaB), groups[1].groupIds.toSet())
    }

    @Test
    fun aMergedGroupPagesForwardAndBackOverTies() = runBlocking {
        seed()
        val drama = WallDestination.Group("Drama", listOf(dramaA, dramaB))
        val first = reads.page(WallRoom.MOVIES, drama, "", null, forward = true, limit = 2)
        assertEquals(listOf("3", "4"), names(first))
        val second = reads.page(WallRoom.MOVIES, drama, "", first.last(), forward = true, limit = 2)
        assertEquals(listOf("5", "1"), names(second))
        val third = reads.page(WallRoom.MOVIES, drama, "", second.last(), forward = true, limit = 2)
        assertEquals(listOf("2"), names(third))
        val back = reads.page(WallRoom.MOVIES, drama, "", third.first(), forward = false, limit = 3)
        assertEquals(listOf("4", "5", "1"), names(back))
    }

    @Test
    fun allGroupsLeavesDisabledSourcesOutAndSearchFilters() = runBlocking {
        seed()
        val all = reads.page(WallRoom.MOVIES, WallDestination.AllGroups, "", null, forward = true, limit = 10)
        assertEquals(listOf("3", "4", "5", "1", "2"), names(all))
        val echo = reads.page(WallRoom.MOVIES, WallDestination.AllGroups, " ECH ", null, forward = true, limit = 10)
        assertEquals(listOf("1", "2"), names(echo))
    }

    @Test
    fun historyIsNewestFirstAndFoldsCopies() = runBlocking {
        seed()
        db.movieImport().insert(listOf(film("a", "7", "Golf", dramaA, work = "w"), film("b", "8", "Golf", dramaB, work = "w")))
        progress.save("vod:movie:a:3", 60_000, 600_000)
        now += 1
        progress.save("vod:movie:a:7", 60_000, 600_000)
        now += 1
        progress.save("vod:movie:b:8", 90_000, 600_000)
        now += 1
        progress.save("vod:movie:b:4", 60_000, 600_000)
        val history = reads.page(WallRoom.MOVIES, WallDestination.History, "", null, forward = true, limit = 10)
        // Golf once, as the copy played last.
        assertEquals(listOf("4", "8", "3"), names(history))
        val older = reads.page(WallRoom.MOVIES, WallDestination.History, "", history[0], forward = true, limit = 1)
        assertEquals(listOf("8"), names(older))
        val newer = reads.page(WallRoom.MOVIES, WallDestination.History, "", history[2], forward = false, limit = 2)
        assertEquals(listOf("4", "8"), names(newer))
    }

    @Test
    fun aFoldedCardCountsItsCopiesOnThisWallAndFillsIn() = runBlocking {
        seed()
        val sql = db.openHelper.writableDatabase
        // Bravo (b) stands for Delta (b) and Alpha (a): one film in three copies, two of them in Drama b.
        sql.execSQL("UPDATE movie SET work_key = 'tmdb:1', year = 2001, quality_mask = 1 WHERE key = 'vod:movie:b:5'")
        sql.execSQL("UPDATE movie SET work_key = 'tmdb:1', poster_url = 'https://provider.example/p.jpg' WHERE key = 'vod:movie:a:3'")
        sql.execSQL("UPDATE movie SET work_key = 'tmdb:1', replacement_title = 'Harbour', replacement_sort = 'harbour' WHERE key = 'vod:movie:b:4'")
        sql.execSQL("UPDATE movie SET group_primary = 0, primary_copy = 0 WHERE key IN ('vod:movie:b:5', 'vod:movie:a:3')")
        val drama = WallDestination.Group("Drama", listOf(dramaA, dramaB))
        val wall = reads.page(WallRoom.MOVIES, drama, "", null, forward = true, limit = 10)
        val bravo = wall.single { it.row.key == "vod:movie:b:4" }
        assertEquals(3, bravo.copies)
        assertEquals("Harbour", bravo.row.displayTitle)
        assertEquals(2001, bravo.row.year)
        assertEquals("https://provider.example/p.jpg", bravo.row.posterUrl)
        assertEquals(1, bravo.row.qualityMask)
        val onlyB = reads.page(WallRoom.MOVIES, WallDestination.Group("Drama", listOf(dramaB)), "", null, forward = true, limit = 10)
        assertEquals(2, onlyB.single { it.row.key == "vod:movie:b:4" }.copies)
        // Search finds the replacement title as well as the provider's (VOD-FR-42).
        assertEquals(listOf("4"), names(reads.page(WallRoom.MOVIES, drama, "harb", null, forward = true, limit = 10)))
    }
}
