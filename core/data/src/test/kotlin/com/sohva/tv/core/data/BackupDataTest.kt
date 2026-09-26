package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.backup.BackupData
import com.sohva.tv.core.data.backup.BackupPayload
import com.sohva.tv.core.data.backup.BackupReader
import com.sohva.tv.core.data.database.ChannelCustomEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.org.LegacyCategories
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 71 §11 "Unit": a beta 23 file's rows go into the rebuild's tables as §4.4 and §4.5 say (film
 * rules by work key, hidden categories as rules, locks only with a PIN, logos after the commit),
 * and what is written back keeps beta 23's dense order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupDataTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val data = BackupData(db)

    @After
    fun close() = db.close()

    private fun fixture(): BackupPayload = BackupReader.read(Buffer().writeUtf8(javaClass.getResource("/backup/beta23-format2.json")!!.readText()))

    private fun q(sql: String): List<String> = db.openHelper.readableDatabase.query(sql).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "null" }) }
    }

    @Test
    fun aBeta23FileFillsTheTables() {
        val restored = data.replace(fixture(), now = 1_000_000L)
        assertEquals(
            listOf("src-1:c42|Northstar HD|My news|name:my news|0|12|null", "src-2:xtream-5|null|null|null|1|null|null"),
            q("SELECT channel_key, custom_name, custom_group_title, custom_group_key, hidden, custom_number, custom_logo_url FROM channel_custom ORDER BY channel_key"),
        )
        // The phone logo comes back from its bytes, the provider's address is settled after the commit.
        assertEquals(setOf("src-1:c42"), restored.logos.keys)
        assertEquals(mapOf("src-2:xtream-5" to "https://logos.provider.example/5.png"), restored.logoAddresses)
        // Beta 23's film identity becomes the work key its alias names; the hidden "Adult" category is
        // already a rule, so only the marker is added (§4.5 step 11).
        val marker = LegacyCategories.MARKER
        assertEquals(
            listOf("LIVE||name:adult||0", "${marker.room.wire}||${marker.groupKey}||1", "MOVIES|||work:tmdb:603|0", "SERIES|src-1|id:4||null").sorted(),
            q("SELECT room, source_id, group_key, item_key, enabled FROM organization_rule").sorted(),
        )
        // Locks count only with a PIN; this file has one.
        assertEquals(listOf("default|src-1:c99"), q("SELECT profile_id, channel_key FROM locked_channel"))
        assertEquals(listOf("src-1:c7", "src-1:c42"), q("SELECT channel_key FROM recent_channel WHERE profile_id = 'default' ORDER BY watched_at DESC"))
        assertEquals(listOf("p1790000000000|LIVE|name:kids", "p1790000000000|MOVIES|id:12"), q("SELECT profile_id, room, group_key FROM profile_allowed_group ORDER BY room"))
        assertEquals(listOf("Evening"), q("SELECT name FROM channel_list"))
        assertEquals(2, q("SELECT * FROM channel_list_member").size)
    }

    @Test
    fun aBackupWithoutAPinRestoresNoLocksAndReplacesWhatWasThere() {
        db.backup().putCustoms(listOf(ChannelCustomEntity("old:c1", "old", "Old", null, null, false, null, null, null, null, 1L)))
        val restored = data.replace(fixture().copy(parentalPin = null), now = 1L)
        assertTrue(q("SELECT * FROM locked_channel").isEmpty())
        assertTrue(q("SELECT * FROM channel_custom WHERE channel_key = 'old:c1'").isEmpty())
        // The removed edit's channel is applied again too, so its playlist values come back.
        assertTrue("old:c1" in restored.channelKeys)
    }

    @Test
    fun whatIsWrittenBackHasADenseOrderAndSelfAliasesForWorkKeys() {
        data.replace(fixture(), now = 1L)
        db.backup().putCustoms(listOf(ChannelCustomEntity("src-1:c7", "src-1", null, null, null, false, 9_000_000L, null, null, null, 1L)))
        val channels = data.channels(setOf("src-1", "src-2")) { null }
        assertEquals(listOf(0, 1), channels.filter { it.sourceId == "src-1" }.sortedBy { it.sortOrder }.map { it.sortOrder })
        assertNull(channels.first { it.channelId == "src-2:xtream-5" }.sortOrder)
        val (_, aliases) = data.organisation()
        assertEquals(listOf("work:tmdb:603" to "work:tmdb:603"), aliases.map { it.alias to it.identity })
    }
}
