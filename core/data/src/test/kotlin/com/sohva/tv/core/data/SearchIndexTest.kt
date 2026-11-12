package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.SearchIndex
import com.sohva.tv.core.data.database.SearchTable
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.model.text.SortNames
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The paged catch-up indexes every new row exactly once, in short transactions with a pause between them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchIndexTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .addCallback(SearchIndex.Callback)
        .build()

    @After
    fun close() = db.close()

    private fun channel(i: Int) = ChannelEntity(
        key = "s:c$i", sourceId = "s", groupId = null, name = "Channel $i", sortName = SortNames.of("Channel $i"), providerName = "Channel $i", providerGroupId = null,
        providerLogoUrl = null, tvgId = null, epgId = null,
        logoUrl = null, streamUrlEnc = "enc", userAgent = null, referrer = null, playlistOrder = i, providerNumber = null,
        number = null, displayRank = i.toLong(), visible = true, catchupType = null, catchupSource = null, catchupDays = null,
        catchupTz = null, xtreamStreamId = null, contentHash = 1, generation = 1,
    )

    private fun count(sql: String): Int = db.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }

    private fun indexed(): Int = count("SELECT COUNT(*) FROM channel_search")

    @Test
    fun newRowsAreIndexedOnceInPagesWithAPauseBetweenThem() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", 60, 0, 0))
        db.channelImport().insert((0 until 250).map(::channel))
        var pauses = 0
        SearchIndex.catchUpPaged(db, SearchTable.CHANNEL, page = 100) { pauses++ }
        assertEquals(250, indexed())
        assertEquals("three pages", 3, pauses)
        assertEquals(1, count("SELECT COUNT(*) FROM channel_search WHERE channel_search MATCH 'Channel' AND rowid = (SELECT id FROM channel WHERE key = 's:c249')"))

        // Nothing new: nothing is written and nothing pauses.
        SearchIndex.catchUpPaged(db, SearchTable.CHANNEL, page = 100) { pauses++ }
        assertEquals(250, indexed())
        assertEquals(3, pauses)

        // A later import's rows, and only those.
        db.channelImport().insert((250 until 260).map(::channel))
        SearchIndex.catchUpPaged(db, SearchTable.CHANNEL, page = 100) { pauses++ }
        assertEquals(260, indexed())
        assertEquals(4, pauses)

        // A deleted row leaves the index by its trigger, and its id is not handed out again.
        db.openHelper.writableDatabase.execSQL("DELETE FROM channel WHERE key = 's:c259'")
        db.channelImport().insert(listOf(channel(300)))
        SearchIndex.catchUpPaged(db, SearchTable.CHANNEL, page = 100) { pauses++ }
        assertEquals(260, indexed())
        assertEquals(260, count("SELECT COUNT(*) FROM channel"))
    }
}
