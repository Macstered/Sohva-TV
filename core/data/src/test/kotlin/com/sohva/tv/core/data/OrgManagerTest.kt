package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.org.GroupRef
import com.sohva.tv.core.data.org.ManagedGroup
import com.sohva.tv.core.data.org.ManagedKind
import com.sohva.tv.core.data.org.ManagerChanges
import com.sohva.tv.core.data.org.OrgManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 42 §11 "Manager model": groups, items and the changes the manager writes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OrgManagerTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val rules = OrgRules(db)
    private val pass = OrgPass(db, rules, LibraryPasses(db))
    private val manager = OrgManager(db, rules, Dispatchers.Unconfined)
    private val movies = OrgRoom.MOVIES

    @After
    fun close() = db.close()

    private fun group(source: String, key: String, name: String) = db.groupImport().insert(
        ContentGroupEntity(sourceId = source, room = "MOVIES", groupKey = key, name = name, providerOrder = 0, itemCount = 0, shown = true, position = 0, sortMode = null),
    )

    private fun film(source: String, id: String, name: String, group: Long, work: String, year: Int? = null) = MovieEntity(
        key = "vod:movie:$source:$id", sourceId = source, providerId = id, groupId = group, name = name, sortName = SortNames.of(name),
        year = year, rating = null, ratingX10 = null, posterUrl = null, streamUrlEnc = "e", plot = null, providerOrder = 0, genre = null,
        workKey = work, primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
    )

    private fun seed() = runBlocking {
        db.sources().upsert(SourceEntity("a", "First", "XTREAM", true, 0, 1, "VOD", 0, 0, 0))
        db.sources().upsert(SourceEntity("b", "Second", "XTREAM", true, 0, 1, "VOD", 0, 0, 0))
        val dramaA = group("a", "id:1", "Drama")
        val dramaB = group("b", "id:9", "Drama")
        val comedy = group("a", "id:2", "Comedy")
        db.movieImport().insert(
            listOf(
                // One film in two copies of Drama A plus one in Drama B: one row, three member keys.
                film("a", "1", "Heat", dramaA, "tmdb:9", 1995), film("a", "2", "Heat 4K", dramaA, "tmdb:9", 1995),
                film("b", "3", "Heat", dramaB, "tmdb:9", 1995), film("a", "4", "Ronin", dramaA, "tmdb:10", 1998),
                film("a", "5", "Airplane", comedy, "tmdb:11", 1980),
            ),
        )
        pass.resolveSource("a", PreferredCopy.NONE)
        pass.resolveSource("b", PreferredCopy.NONE)
    }

    private fun apply(changes: List<RuleChange>): List<RuleChange> {
        val undo = (rules.change(changes) as Outcome.Ok).value
        pass.afterChange(changes.map { it.key }, PreferredCopy.NONE)
        return undo
    }

    private fun drama(scope: String? = null): ManagedGroup = runBlocking { manager.groups(movies, scope).single { it.key == "name:drama" } }

    @Test
    fun theGroupListHasHistoryThenTheGroupsByNameWithIdentityCounts() = runBlocking {
        seed()
        val groups = manager.groups(movies, null)
        assertEquals(listOf("@history", "name:comedy", "name:drama"), groups.map { it.key })
        assertEquals(ManagedKind.SHORTCUT, groups[0].kind)
        // Drama: Heat (three copies, one identity per source group) and Ronin.
        val drama = groups[2]
        assertEquals(3, drama.total)
        assertEquals(listOf(GroupRef("", "name:drama"), GroupRef("a", "id:1"), GroupRef("b", "id:9")), drama.backing)
    }

    @Test
    fun aFilmIsOneRowAndItsToggleNamesEveryCopyInThisGroupOnly() = runBlocking {
        seed()
        val items = manager.items(movies, drama(), null)
        assertEquals(listOf("work:tmdb:9", "work:tmdb:10"), items.map { it.identity })
        val heat = items.first()
        val changes = ManagerChanges.toggle(movies, drama(), heat)
        assertEquals(setOf(RuleKey(movies, "a", "id:1", "work:tmdb:9"), RuleKey(movies, "b", "id:9", "work:tmdb:9")), changes.map { it.key }.toSet())
        apply(changes)
        val after = manager.items(movies, drama(), null)
        assertFalse(after.first { it.identity == "work:tmdb:9" }.enabled)
        assertTrue(after.first { it.identity == "work:tmdb:10" }.enabled)
    }

    @Test
    fun aSourceScopeWritesOnlyThatSourcesKeys() = runBlocking {
        seed()
        val scoped = drama("a")
        assertEquals(listOf(GroupRef("a", "name:drama"), GroupRef("a", "id:1")), scoped.backing)
        apply(ManagerChanges.groupShown(movies, scoped, false))
        assertFalse(drama("a").shown)
        assertTrue(drama("b").shown)
    }

    @Test
    fun aHiddenGroupStaysManageableAndItsItemsSayWhy() = runBlocking {
        seed()
        apply(ManagerChanges.groupShown(movies, drama(), false))
        val group = drama()
        assertFalse(group.shown)
        val items = manager.items(movies, group, null)
        assertTrue(items.all { it.groupHidden && it.blocked && !it.enabled })
        // Undo puts it back.
        val undo = (rules.change(ManagerChanges.groupShown(movies, group, true)) as Outcome.Ok).value
        pass.afterChange(undo.map { it.key }, PreferredCopy.NONE)
        assertTrue(drama().shown)
    }

    @Test
    fun aMoveChangesPlacesAndSortOnlyNeverVisibility() = runBlocking {
        seed()
        val group = drama()
        val items = manager.items(movies, group, null)
        val moved = items.reversed()
        val changes = ManagerChanges.moveItem(movies, group, moved, moved.first())
        assertTrue(changes.all { it.enabled is com.sohva.tv.core.data.org.Field.Keep })
        apply(changes)
        assertEquals(OrgSort.MANUAL, drama().sort)
        assertEquals(listOf("work:tmdb:10", "work:tmdb:9"), manager.items(movies, drama(), null).map { it.identity })
        assertTrue(manager.items(movies, drama(), null).all { it.enabled })
    }

    @Test
    fun manualOnAGroupWithoutPlacesSeedsTheOrderOnScreen() = runBlocking {
        seed()
        val group = drama()
        apply(ManagerChanges.groupSort(movies, group, OrgSort.NEWEST, emptyList()))
        val newest = manager.items(movies, drama(), null)
        assertEquals(listOf("work:tmdb:10", "work:tmdb:9"), newest.map { it.identity })
        apply(ManagerChanges.groupSort(movies, drama(), OrgSort.MANUAL, newest))
        assertEquals(listOf("work:tmdb:10", "work:tmdb:9"), manager.items(movies, drama(), null).map { it.identity })
        // Back to A–Z and to manual again: the places were kept (ORG-FR-24).
        apply(ManagerChanges.groupSort(movies, drama(), OrgSort.TITLE_ASC, emptyList()))
        assertEquals(listOf("work:tmdb:9", "work:tmdb:10"), manager.items(movies, drama(), null).map { it.identity })
        apply(ManagerChanges.groupSort(movies, drama(), OrgSort.MANUAL, manager.items(movies, drama(), null)))
        assertEquals(listOf("work:tmdb:10", "work:tmdb:9"), manager.items(movies, drama(), null).map { it.identity })
    }
}
