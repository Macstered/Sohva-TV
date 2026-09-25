package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.prefs.CustomGroupCodec
import com.sohva.tv.core.data.vod.WallDestination
import com.sohva.tv.core.data.vod.WallReads
import com.sohva.tv.core.data.vod.WallRoom
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.vod.CustomGroup
import com.sohva.tv.core.model.vod.CustomGroups
import com.sohva.tv.core.model.vod.Genre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 40 VOD-FR-11 and spec 42 ORG-FR-60…64: groups of your own, stored and shown as a wall. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomGroupWallTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val reads = WallReads(db, Dispatchers.Unconfined) { "default" }

    @After
    fun close() = db.close()

    private fun film(id: Int, name: String, genre: Genre?, year: Int?, tenths: Int?, group: Long) = MovieEntity(
        key = "vod:movie:a:$id", sourceId = "a", providerId = "$id", groupId = group, name = name, sortName = SortNames.of(name),
        year = year, rating = tenths?.let { "${it / 10}.${it % 10}" }, ratingX10 = tenths, posterUrl = null, streamUrlEnc = "enc", plot = null,
        providerOrder = id, genre = genre?.wire, workKey = null, primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
    )

    private fun seed() = runBlocking {
        db.sources().upsert(SourceEntity("a", "First", "XTREAM", true, 0, 1, "VOD", 0, 0, 0))
        val g = db.groupImport().insert(
            ContentGroupEntity(sourceId = "a", room = "MOVIES", groupKey = "id:1", name = "All", providerOrder = 0, itemCount = 6, shown = true, position = 0, sortMode = null),
        )
        db.movieImport().insert(
            listOf(
                film(1, "Zulu", Genre.CRIME, 1995, 81, g),
                film(2, "Alpha", Genre.THRILLER, 2001, 70, g),
                film(3, "Mike", Genre.CRIME, null, 90, g),
                film(4, "Bravo", Genre.DRAMA, 1998, 85, g),
                film(5, "Kilo", Genre.THRILLER, 1990, null, g),
                film(6, "Echo", null, 1996, 88, g),
            ),
        )
    }

    private fun names(group: CustomGroup, forward: Boolean = true, limit: Int = 50): List<String> = runBlocking {
        reads.page(WallRoom.MOVIES, WallDestination.Custom(group), "", null, forward, limit).map { it.row.name }
    }

    @Test
    fun genresMergeInWallOrderAndBoundsFilter() {
        seed()
        val crimeAndThrillers = CustomGroup("g1", "Tense", setOf(Genre.CRIME, Genre.THRILLER), null, null, null)
        assertEquals(listOf("Alpha", "Kilo", "Mike", "Zulu"), names(crimeAndThrillers))
        // A year bound drops the title without a year; the rating bound drops the one without a rating.
        assertEquals(listOf("Zulu"), names(crimeAndThrillers.copy(fromYear = 1991, minRating = 7.5)))
        // Backwards from the end, in the same order.
        assertEquals(listOf("Mike", "Zulu"), names(crimeAndThrillers, forward = false, limit = 2))
    }

    @Test
    fun withoutGenresEveryTitleInTheBoundsIncludingOnesWithoutAGenre() {
        seed()
        assertEquals(listOf("Bravo", "Echo", "Zulu"), names(CustomGroup("g2", "Nineties", emptySet(), 1991, 1999, null)))
    }

    @Test
    fun theListKeepsAtMostTwentyFourAndReplacesById() {
        val one = CustomGroup("a", " Tense ", setOf(Genre.CRIME), null, null, null)
        val saved = CustomGroups.save(emptyList(), one)
        assertEquals("Tense", saved.single().name)
        assertEquals("Calm", CustomGroups.save(saved, one.copy(name = "Calm")).single().name)
        // Unusable (no condition): nothing changes.
        assertEquals(saved, CustomGroups.save(saved, CustomGroup("b", "Empty", emptySet(), null, null, null)))
        val full = (1..24).fold(emptyList<CustomGroup>()) { list, i -> CustomGroups.save(list, one.copy(id = "g$i")) }
        assertEquals(24, CustomGroups.save(full, one.copy(id = "g25")).size)
        assertEquals(23, CustomGroups.delete(full, "g3").size)
        assertEquals(2001, CustomGroups.year("20a01x"))
        assertEquals(7.5, CustomGroups.rating("7,5")!!, 0.0)
    }

    @Test
    fun theStoredArrayKeepsBeta23sShapeAndDropsWhatItCannotUse() {
        val g = CustomGroup("g1", "Tense", setOf(Genre.THRILLER, Genre.CRIME), 1990, null, 7.5)
        assertEquals(listOf(g), CustomGroupCodec.decode(CustomGroupCodec.encode(listOf(g))))
        val stored = """[{"id":"x","name":"Odd","genres":["unknown"]},{"name":"No id","genres":["crime"]},""" +
            """{"id":"k","name":"Kept","genres":["crime","unknown"]}]"""
        assertEquals(listOf("Kept"), CustomGroupCodec.decode(stored).map { it.name })
        assertEquals(emptyList<CustomGroup>(), CustomGroupCodec.decode("not json"))
    }
}
