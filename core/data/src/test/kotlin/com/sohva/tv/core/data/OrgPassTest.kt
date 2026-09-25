package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ChannelCustomEntity
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.org.Field
import com.sohva.tv.core.data.org.OrgPass
import com.sohva.tv.core.data.org.OrgRules
import com.sohva.tv.core.data.org.RuleChange
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.core.model.org.RuleKey
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.vod.PreferredCopy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 42 §9.1 and §11: rules resolved into the stored group and item columns, partial writes and Undo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OrgPassTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val rules = OrgRules(db)
    private val pass = OrgPass(db, rules, LibraryPasses(db))
    private var drama = 0L
    private var crime = 0L
    private var news = 0L

    @After
    fun close() = db.close()

    private fun group(room: String, key: String, name: String) = db.groupImport().insert(
        ContentGroupEntity(sourceId = "s", room = room, groupKey = key, name = name, providerOrder = 0, itemCount = 0, shown = true, position = 0, sortMode = null),
    )

    private fun film(id: String, group: Long, work: String) = MovieEntity(
        key = "vod:movie:s:$id", sourceId = "s", providerId = id, groupId = group, name = "Film $id", sortName = SortNames.of("Film $id"),
        year = null, rating = null, ratingX10 = null, posterUrl = null, streamUrlEnc = "e", plot = null, providerOrder = 0, genre = null,
        workKey = work, primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
    )

    private fun seed() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "XTREAM", true, 0, 1, "BOTH", 0, 0, 0))
        drama = group("MOVIES", "id:1", "Drama")
        crime = group("MOVIES", "id:2", "Crime")
        news = group("LIVE", "name:news", "News")
        db.movieImport().insert(listOf(film("1", drama, "tmdb:9"), film("2", crime, "tmdb:9"), film("3", drama, "tmdb:10")))
        db.channelImport().insert(
            listOf("c1", "c2").mapIndexed { i, id ->
                ChannelEntity(
                    key = "s:$id", sourceId = "s", groupId = news, name = id, sortName = id, providerName = id, providerGroupId = news,
                    providerLogoUrl = null, tvgId = null, epgId = null, logoUrl = null, streamUrlEnc = "e", userAgent = null, referrer = null,
                    playlistOrder = i, providerNumber = null, number = null, displayRank = i.toLong(), visible = true, catchupType = null,
                    catchupSource = null, catchupDays = null, catchupTz = null, xtreamStreamId = null, contentHash = 1, generation = 1,
                )
            },
        )
        pass.resolveSource("s", PreferredCopy.NONE)
    }

    private fun visible(key: String): Boolean =
        db.openHelper.readableDatabase.query("SELECT visible FROM movie WHERE key = '$key'").use { it.moveToFirst(); it.getInt(0) == 1 }

    private fun change(vararg changes: RuleChange): List<RuleChange> {
        val undo = (rules.change(changes.toList()) as Outcome.Ok).value
        pass.afterChange(changes.map { it.key }, PreferredCopy.NONE)
        return undo
    }

    @Test
    fun aHiddenGroupHidesItsFilmsAndLeavesTheRail() {
        seed()
        change(RuleChange(RuleKey(OrgRoom.MOVIES, "", "name:drama", ""), enabled = Field.Set(false)))
        assertEquals(listOf(false, true, false), listOf("1", "2", "3").map { visible("vod:movie:s:$it") })
        val drama = db.organization().groups("MOVIES").single { it.groupKey == "id:1" }
        assertEquals(false, drama.shown)
    }

    @Test
    fun hideEverywhereHidesEveryCopyAndUndoPutsItBack() {
        seed()
        val undo = change(RuleChange(RuleKey(OrgRoom.MOVIES, "", "", "work:tmdb:9"), enabled = Field.Set(false)))
        assertEquals(listOf(false, false, true), listOf("1", "2", "3").map { visible("vod:movie:s:$it") })
        change(*undo.toTypedArray())
        assertEquals(listOf(true, true, true), listOf("1", "2", "3").map { visible("vod:movie:s:$it") })
        assertEquals(0, db.organization().ruleCount())
    }

    @Test
    fun partialWritesKeepTheOtherFields() {
        seed()
        val key = RuleKey(OrgRoom.MOVIES, "s", "id:1", "work:tmdb:10")
        change(RuleChange(key, position = Field.Set(2048)))
        change(RuleChange(key, enabled = Field.Set(false)))
        assertEquals(listOf(key), rules.of(OrgRoom.MOVIES).map { it.key })
        assertEquals(2048L, rules.of(OrgRoom.MOVIES).single().value.position)
        assertEquals(false, visible("vod:movie:s:3"))
        val position = db.openHelper.readableDatabase.query("SELECT item_position FROM movie WHERE key = 'vod:movie:s:3'").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(2048, position)
    }

    @Test
    fun aGroupSortIsStoredOnTheGroupAndTheRoomDefaultOnTheRest() {
        seed()
        change(RuleChange(RuleKey(OrgRoom.MOVIES, "", "", ""), sort = Field.Set(OrgSort.NEWEST)), RuleChange(RuleKey(OrgRoom.MOVIES, "s", "id:2", ""), sort = Field.Set(OrgSort.RATING)))
        val sorts = db.organization().groups("MOVIES").associate { it.groupKey to it.sortMode }
        assertEquals(mapOf("id:1" to "NEWEST", "id:2" to "RATING"), sorts)
    }

    @Test
    fun aChannelsOwnHiddenFlagHidesItUntilARuleShowsIt() {
        seed()
        db.channelEdits().putCustom(ChannelCustomEntity("s:c1", "s", null, null, null, true, null, null, null, null, 0))
        pass.resolveSource("s", PreferredCopy.NONE)
        val visible = { key: String -> db.openHelper.readableDatabase.query("SELECT visible FROM channel WHERE key = '$key'").use { it.moveToFirst(); it.getInt(0) == 1 } }
        assertEquals(listOf(false, true), listOf(visible("s:c1"), visible("s:c2")))
        change(RuleChange(RuleKey(OrgRoom.LIVE, "", "", "s:c1"), enabled = Field.Set(true)))
        assertTrue(visible("s:c1"))
    }

    @Test
    fun invalidChangesAreRefusedWhole() {
        seed()
        val bad = rules.change(
            listOf(
                RuleChange(RuleKey(OrgRoom.MOVIES, "", "id:1", ""), enabled = Field.Set(false)),
                RuleChange(RuleKey(OrgRoom.MOVIES, "", "id:2", ""), position = Field.Set(-1)),
            ),
        )
        assertTrue(bad is Outcome.Failed)
        assertEquals(0, db.organization().ruleCount())
    }
}
