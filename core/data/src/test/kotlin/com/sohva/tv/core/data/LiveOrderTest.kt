package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.channels.ChannelEditStore
import com.sohva.tv.core.data.channels.ChannelListStore
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.live.ChannelEffects
import com.sohva.tv.core.data.live.LiveStore
import com.sohva.tv.core.data.org.ManagerChanges
import com.sohva.tv.core.data.org.OrgManager
import com.sohva.tv.core.data.org.OrgPass
import com.sohva.tv.core.data.org.OrgRules
import com.sohva.tv.core.data.org.RuleChange
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.vod.PreferredCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GUIDE-13 (spec 20 GUIDE-FR-32, spec 42 ORG-FR-19…22): a source's channels in `display_rank`
 * order are its groups in the Live group order, each in its own content order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveOrderTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val rules = OrgRules(db)
    private val pass = OrgPass(db, rules, LibraryPasses(db))
    private val manager = OrgManager(db, rules, Dispatchers.Unconfined)
    private val clock = object : Clock {
        override fun wallMillis(): Long = 1_790_000_000_000L
        override fun monotonicNanos(): Long = 0
    }
    private val live = OrgRoom.LIVE

    @After
    fun close() = db.close()

    /**
     * Playlist order interleaves the groups: News n1, Sport s1, News n2, Sport s2, News n3.
     * Names run against the playlist order so A–Z is visible: n1 "Zeta", n2 "Alpha", n3 "Mu".
     */
    private fun seed() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", 0, 0, 0))
        val news = group("id:news", "News", 0)
        val sport = group("id:sport", "Sport", 1)
        val rows = listOf("n1" to (news to "Zeta"), "s1" to (sport to "Sport One"), "n2" to (news to "Alpha"), "s2" to (sport to "Sport Two"), "n3" to (news to "Mu"))
        db.channelImport().insert(
            rows.mapIndexed { i, (id, v) ->
                val (groupId, name) = v
                ChannelEntity(
                    key = "s:$id", sourceId = "s", groupId = groupId, name = name, sortName = SortNames.of(name), providerName = name,
                    providerGroupId = groupId, providerLogoUrl = null, tvgId = null, epgId = null, logoUrl = null, streamUrlEnc = "enc",
                    userAgent = null, referrer = null, playlistOrder = i, providerNumber = null, number = null,
                    displayRank = ChannelEffects.playlistRank(i), visible = true, catchupType = null, catchupSource = null,
                    catchupDays = null, catchupTz = null, xtreamStreamId = null, contentHash = 1, generation = 1,
                )
            },
        )
        pass.resolveLive("s")
    }

    private fun group(key: String, name: String, order: Int): Long = db.groupImport().insert(
        ContentGroupEntity(sourceId = "s", room = "LIVE", groupKey = key, name = name, providerOrder = order, itemCount = 0, shown = true, position = 0, sortMode = null),
    )

    private fun order(): List<String> = db.openHelper.readableDatabase.query("SELECT key FROM channel ORDER BY display_rank, id").use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0).removePrefix("s:")) }
    }

    private fun apply(changes: List<RuleChange>) {
        (rules.change(changes) as Outcome.Ok).value
        pass.afterChange(changes.map { it.key }, PreferredCopy.NONE)
    }

    private fun managed(key: String) = runBlocking { manager.groups(live, null).single { it.key == key } }

    @Test
    fun groupsComeInTheOrderTheyFirstAppearAndKeepThePlaylistOrderInside() {
        seed()
        assertEquals(listOf("n1", "n2", "n3", "s1", "s2"), order())
    }

    @Test
    fun theGroupOrderRuleMovesWholeBlocks() {
        seed()
        apply(ManagerChanges.groupOrder(live, OrgSort.TITLE_DESC, runBlocking { manager.groups(live, null) }))
        assertEquals(listOf("s1", "s2", "n1", "n2", "n3"), order())
    }

    @Test
    fun aGroupsOwnSortOrdersItsChannelsAndProviderTakesItBack() {
        seed()
        apply(ManagerChanges.groupSort(live, managed("name:news"), OrgSort.TITLE_ASC, emptyList()))
        assertEquals(listOf("n2", "n3", "n1", "s1", "s2"), order())
        apply(ManagerChanges.groupSort(live, managed("name:news"), OrgSort.PROVIDER, emptyList()))
        assertEquals(listOf("n1", "n2", "n3", "s1", "s2"), order())
    }

    @Test
    fun aManualMoveInTheManagerPlacesTheChannel() = runBlocking {
        seed()
        val news = managed("name:news")
        val items = manager.items(live, news, null)
        val moved = listOf(items[2], items[0], items[1])
        apply(ManagerChanges.moveItem(live, news, moved, items[2]))
        assertEquals(listOf("n3", "n1", "n2", "s1", "s2"), order())
    }

    @Test
    fun channelManagementMovesInsideTheGroupOnly() = runBlocking {
        seed()
        val edits = ChannelEditStore(db, Dispatchers.Unconfined, clock)
        // The neighbour in the source's order, like the screen's unfiltered list.
        val next = { rank: Long, id: Long ->
            db.openHelper.readableDatabase.query(
                "SELECT id, key FROM channel WHERE display_rank > $rank OR (display_rank = $rank AND id > $id) ORDER BY display_rank, id LIMIT 1",
            ).use { c -> if (c.moveToFirst()) c.getLong(0) to c.getString(1) else null }
        }
        assertEquals(true, edits.move("s:n1", up = false, onScreen = next))
        assertEquals(listOf("n2", "n1", "n3", "s1", "s2"), order())
        // n3 is the last of News: Down would cross into Sport, so nothing moves.
        assertFalse(edits.move("s:n3", up = false, onScreen = next))
        assertEquals(listOf("n2", "n1", "n3", "s1", "s2"), order())
    }

    /** GUIDE-FR-35: a list keeps its own order until its view is sorted; a member switched off in the view is left out. */
    @Test
    fun aListFollowsItsViewRules() = runBlocking {
        seed()
        val lists = ChannelListStore(db, Dispatchers.Unconfined, clock)
        val store = LiveStore(db, Dispatchers.Unconfined, clock)
        val id = lists.create("Evening")!!
        for (key in listOf("s:n1", "s:s2", "s:n2")) lists.add(id, key)
        val ids = { runBlocking { store.customList(id, "s").ids.toList() } }
        val keyOf = db.openHelper.readableDatabase.query("SELECT id, key FROM channel").use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1).removePrefix("s:")) }
        }
        assertEquals(listOf("n1", "s2", "n2"), ids().map(keyOf::getValue))
        val list = managed(OrgKeys.list(id))
        apply(ManagerChanges.groupSort(live, list, OrgSort.TITLE_ASC, emptyList()))
        assertEquals(listOf("n2", "s2", "n1"), ids().map(keyOf::getValue))
        val sport = manager.items(live, managed(OrgKeys.list(id)), null).single { it.identity == "s:s2" }
        apply(ManagerChanges.toggle(live, managed(OrgKeys.list(id)), sport))
        assertEquals(listOf("n2", "n1"), ids().map(keyOf::getValue))
    }
}
