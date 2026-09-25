package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.channels.ChannelEditStore
import com.sohva.tv.core.data.channels.ChannelFields
import com.sohva.tv.core.data.channels.ChannelListStore
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.live.ChannelEffects
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

/** Spec 21 §11: edits apply at once, count where they show, move past hidden neighbours, and reset. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelEditStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = object : Clock {
        override fun wallMillis(): Long = 1_790_000_000_000L
        override fun monotonicNanos(): Long = 0
    }
    private val edits = ChannelEditStore(db, Dispatchers.Unconfined, clock)
    private val lists = ChannelListStore(db, Dispatchers.Unconfined, clock)

    @After
    fun close() = db.close()

    private var news = 0L

    /** Five News channels c0…c4 in playlist order. */
    private fun seed() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", 0, 0, 0))
        news = db.groupImport().insert(ContentGroupEntity(sourceId = "s", room = "LIVE", groupKey = "name:news", name = "News", providerOrder = 0, itemCount = 5, shown = true, position = 0, sortMode = null))
        db.channelImport().insert(
            (0 until 5).map { i ->
                val name = "Channel $i"
                ChannelEntity(
                    key = "s:c$i", sourceId = "s", groupId = news, name = name, sortName = SortNames.of(name), providerName = name,
                    providerGroupId = news, providerLogoUrl = "https://provider.example/$i.png", tvgId = "t$i", epgId = "t$i",
                    logoUrl = "https://provider.example/$i.png", streamUrlEnc = "enc", userAgent = null, referrer = null, playlistOrder = i,
                    providerNumber = i + 1, number = i + 1, displayRank = ChannelEffects.playlistRank(i), visible = true, catchupType = null,
                    catchupSource = null, catchupDays = null, catchupTz = null, xtreamStreamId = null, contentHash = 1, generation = 1,
                )
            },
        )
    }

    private fun q(sql: String): List<String> = db.openHelper.readableDatabase.query(sql).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "null" }) }
    }

    private fun order(): List<String> = q("SELECT key FROM channel ORDER BY display_rank, id").map { it.removePrefix("s:") }

    @Test
    fun saveAppliesEveryFieldAndMovesTheGroupCount() = runBlocking {
        seed()
        edits.save("s:c1", ChannelFields(" Mine ", "Favourites here", "https://provider.example/mine.png", "42", "manual"))
        assertEquals(
            listOf("Mine|Favourites here|https://provider.example/mine.png|42|manual|1"),
            q("SELECT c.name, g.name, c.logo_url, c.number, c.epg_id, c.visible FROM channel c JOIN content_group g ON g.id = c.group_id WHERE c.key = 's:c1'"),
        )
        assertEquals(listOf("Favourites here|1", "News|4"), q("SELECT name, item_count FROM content_group ORDER BY name"))
        // Blank fields fall back to the playlist; an edit equal to the playlist leaves no row.
        edits.save("s:c1", ChannelFields(" ", null, "", "0", null))
        assertEquals(listOf("Channel 1|News|https://provider.example/1.png|2|t1"), q("SELECT c.name, g.name, c.logo_url, c.number, c.epg_id FROM channel c JOIN content_group g ON g.id = c.group_id WHERE c.key = 's:c1'"))
        assertNull(db.manager().custom("s:c1"))
        assertEquals(listOf("Favourites here|0", "News|5"), q("SELECT name, item_count FROM content_group ORDER BY name"))
    }

    @Test
    fun hideKeepsUnsavedFieldsOutAndResetUndoesEverything() = runBlocking {
        seed()
        edits.save("s:c2", ChannelFields("Renamed", null, null, null, null))
        edits.setHidden("s:c2", true)
        assertEquals(listOf("Renamed|0"), q("SELECT name, visible FROM channel WHERE key = 's:c2'"))
        assertEquals(listOf("News|4"), q("SELECT name, item_count FROM content_group"))
        edits.reset("s:c2")
        assertEquals(listOf("Channel 2|1"), q("SELECT name, visible FROM channel WHERE key = 's:c2'"))
        assertEquals(listOf("News|5"), q("SELECT name, item_count FROM content_group"))
    }

    @Test
    fun aMovePastHiddenNeighboursLandsBeforeTheOneOnScreen() = runBlocking {
        seed()
        // On screen: c0 and c3 (c1, c2 filtered away); c3 moves up past them to just before c0.
        val shown = setOf("s:c0", "s:c3", "s:c4")
        val onScreenAbove: suspend (Long, Long) -> Pair<Long, String>? = { rank, id ->
            q("SELECT id, key FROM channel WHERE display_rank < $rank OR (display_rank = $rank AND id < $id) ORDER BY display_rank DESC, id DESC")
                .map { it.split("|") }.firstOrNull { it[1] in shown }?.let { it[0].toLong() to it[1] }
        }
        assertTrue(edits.move("s:c3", up = true, onScreen = onScreenAbove))
        assertEquals(listOf("c3", "c0", "c1", "c2", "c4"), order())
        // The first move positioned the whole source; later moves write one row.
        assertEquals(listOf("5"), q("SELECT COUNT(*) FROM channel_custom WHERE position IS NOT NULL"))
        // At the top of what is shown nothing happens.
        assertFalse(edits.move("s:c3", up = true, onScreen = onScreenAbove))
        assertEquals(listOf("c3", "c0", "c1", "c2", "c4"), order())
    }

    @Test
    fun listsAddRemoveAndDeleteWithTheirMembers() = runBlocking {
        seed()
        assertNull(lists.create("   "))
        val id = lists.create("Evening")!!
        lists.add(id, "s:c4")
        lists.add(id, "s:c1")
        assertEquals(listOf("s:c4", "s:c1"), lists.memberKeys(id, "s"))
        assertEquals(listOf(id), lists.listsOf("s:c1").first())
        lists.remove(id, "s:c4")
        assertEquals(listOf("s:c1"), lists.memberKeys(id, "s"))
        lists.delete(id)
        assertTrue(lists.lists.first().isEmpty())
        assertEquals(listOf("0"), q("SELECT COUNT(*) FROM channel_list_member"))
    }
}
