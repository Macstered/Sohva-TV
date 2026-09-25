package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.MATCHED
import com.sohva.tv.core.data.database.MetadataMatchEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.NO_MATCH
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.data.vod.WallDestination
import com.sohva.tv.core.data.vod.WallReads
import com.sohva.tv.core.data.vod.WallRoom
import com.sohva.tv.core.model.metadata.WorkKeys
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.vod.CopyClaimReader
import com.sohva.tv.core.model.vod.PreferredCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 40 §11 "Folding" and spec 41 §4.12: standing copies, matches back into rows, genre counts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryPassesTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val passes = LibraryPasses(db)
    private val reads = WallReads(db, Dispatchers.Unconfined) { "default" }

    @After
    fun close() = db.close()

    private var action = 0L
    private var classics = 0L

    private fun film(id: String, name: String, group: Long, year: Int? = 1999): MovieEntity {
        val claims = CopyClaimReader.read(name)
        return MovieEntity(
            key = "vod:movie:s:$id", sourceId = "s", providerId = id, groupId = group, name = name, sortName = SortNames.of(name), year = year,
            rating = null, ratingX10 = null, posterUrl = null, streamUrlEnc = "enc", plot = null, providerOrder = 0,
            qualityMask = claims.qualityMask, claimMask = claims.languageMask, pictureRank = claims.pictureRank,
            genre = null, workKey = WorkKeys.of(name, year), primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
        )
    }

    private fun group(key: String, name: String) = db.groupImport().insert(
        ContentGroupEntity(sourceId = "s", room = "MOVIES", groupKey = key, name = name, providerOrder = 0, itemCount = 0, shown = true, position = 0, sortMode = null),
    )

    private fun seed() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "XTREAM", true, 0, 1, "VOD", 0, 0, 0))
        action = group("a", "Action")
        classics = group("c", "Classics")
        db.movieImport().insert(
            listOf(
                film("1", "The Matrix 1999 [MULTI-SUBS] 1080p", action),
                film("2", "FIN | The Matrix (1999) 4K", action),
                film("3", "The Matrix (1999)", classics),
                film("4", "Heat", action, 1995),
            ),
        )
    }

    private fun keys(destination: WallDestination) = runBlocking {
        reads.page(WallRoom.MOVIES, destination, "", null, forward = true, limit = 50).map { it.row.key.substringAfterLast(':') }
    }

    @Test
    fun aFilmStandsOnceOverallAndOnceInEachGroupThatCarriesIt() {
        seed()
        passes.refreshSource("s", PreferredCopy.NONE)
        // Wall order is A–Z by provider title: "fin the matrix…" comes first among the copies.
        assertEquals(listOf("2", "4"), keys(WallDestination.AllGroups))
        assertEquals(listOf("2", "4"), keys(WallDestination.Group("Action", listOf(action))))
        assertEquals(listOf("3"), keys(WallDestination.Group("Classics", listOf(classics))))
        // The group counts count films, not copies.
        assertEquals(listOf(2, 1), runBlocking { reads.groups(WallRoom.MOVIES) }.map { it.count })
    }

    @Test
    fun thePreferenceChoosesTheStandingCopy() {
        seed()
        passes.refreshSource("s", PreferredCopy.FINNISH_SUBTITLES)
        assertEquals(listOf("1", "4"), keys(WallDestination.AllGroups).sortedBy { it })
        passes.refreshSource("s", PreferredCopy.LARGEST_PICTURE)
        assertEquals(listOf("2", "4"), keys(WallDestination.AllGroups).sortedBy { it })
    }

    @Test
    fun aMatchRekeysTheFilmAndGivesItsTitleAndGenre() {
        seed()
        db.runInTransaction {
            passes.apply(MetadataMatchEntity("vod:movie:s:4", "movie", MATCHED, "tmdb", "949", "crime", 2, "Heat (Michael Mann)", "/heat.jpg", true, 1))
            passes.apply(MetadataMatchEntity("vod:movie:s:3", "movie", NO_MATCH, null, null, "drama", 2, "The Matrix (1999)", null, false, 1))
        }
        val heat = db.openHelper.readableDatabase.query("SELECT work_key, replacement_title, genre, replace_poster, replacement_key FROM movie WHERE key = 'vod:movie:s:4'").use {
            it.moveToFirst()
            listOf(it.getString(0), it.getString(1), it.getString(2), it.getInt(3).toString(), it.getString(4))
        }
        assertEquals(listOf("tmdb:949", "Heat (Michael Mann)", "crime", "1", "heat"), heat)
        val miss = db.openHelper.readableDatabase.query("SELECT replacement_title, genre FROM movie WHERE key = 'vod:movie:s:3'").use {
            it.moveToFirst()
            listOf(it.getString(0), it.getString(1))
        }
        assertEquals(listOf(null, null), miss)
        passes.refreshSource("s", PreferredCopy.NONE)
        passes.recountGenres()
        assertEquals(listOf("4"), keys(WallDestination.OfGenre(com.sohva.tv.core.model.vod.Genre.CRIME)))
        val counts = db.library().counts("MOVIES").associate { it.genre to it.titles }
        assertEquals(mapOf("crime" to 1, "" to 1), counts)
    }
}
