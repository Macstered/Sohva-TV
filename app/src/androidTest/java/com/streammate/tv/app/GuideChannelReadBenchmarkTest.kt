package com.streammate.tv.app

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.GUIDE_CHANNEL_PAGE_SQL
import com.streammate.tv.core.database.IptvChannelEntity
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.ORGANIZATION_VISIBLE_LIVE_PREDICATE
import com.streammate.tv.core.database.ORGANIZATION_VISIBLE_LIVE_SOURCE_PREDICATE
import com.streammate.tv.core.database.OrganizationChange
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.model.LibraryRoom
import com.streammate.tv.core.model.OrganizationKey
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.repository.OrganizationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device evidence for the guide's channel rows: a provider-sized source, on
 * disk, read the way the guide used to read it and the way it reads it now.
 *
 * The old way is one ordered statement stepped through Android's cursor, which
 * holds two megabytes and re-runs the statement for every refill. It took
 * 57 seconds for one source on the Shield on 18 September 2026 and cannot be
 * shown by a JVM test, where there is no cursor window. Prints its timings;
 * read them from the instrumentation log.
 */
@RunWith(AndroidJUnit4::class)
class GuideChannelReadBenchmarkTest {
    private lateinit var database: StreamMateDatabase

    @Before
    fun createSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME)
        database = Room.databaseBuilder(context, StreamMateDatabase::class.java, DB_NAME).build()
        val dao = database.guideDao()
        dao.upsertSourceState(IptvSourceStateEntity("big", "Provider", "xtream", true, 1, 0, 1))
        (1..CHANNELS).chunked(2_000).forEach { chunk ->
            dao.upsertChannels(chunk.map { index ->
                val group = "Group " + (index % GROUPS).toString().padStart(3, '0')
                IptvChannelEntity(
                    sourceId = "big",
                    snapshotId = "playlist",
                    // Provider ids carry no order, like the hashes and stream numbers real ones are.
                    channelId = "big:" + Integer.toHexString(index * 40_503 % 1_000_003).padStart(6, '0') + index,
                    tvgId = "channel$index.example",
                    name = "Channel number $index HD",
                    normalizedName = "channel number $index hd",
                    groupTitle = group,
                    logoUrl = "http://logos.example.invalid/a/rather/longer/path/to/logo-$index.png",
                    encryptedStreamUrl = "encrypted",
                    userAgent = null,
                    referrer = null,
                    lastSeenEpochMillis = 1,
                    playlistOrder = index,
                    organizationGroupKey = "id:${index % GROUPS}",
                    organizationNameKey = "name:" + group.lowercase(),
                )
            })
        }
        dao.activatePlaylistSnapshot("big", "playlist", CHANNELS, 1)
        // What a viewer with a large provider has usually done: hidden a tenth of the groups.
        OrganizationRepository(database.organizationDao()).change((0 until GROUPS / 10).map { group ->
            OrganizationChange(OrganizationKey(LibraryRoom.LIVE, "big", "id:$group"), enabled = false, changeEnabled = true)
        })
    }

    @After
    fun closeDatabase() {
        database.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DB_NAME)
    }

    @Test
    fun aWholeSourceIsReadInPagesFarSoonerThanThroughOneCursor() = runBlocking {
        val visible = CHANNELS - CHANNELS / 10
        val organization = OrganizationRepository(database.organizationDao())
        val repository = GuideRepository(database.guideDao(), organization = organization)

        repository.observeChannelsForSource("big", null).first() // Warm the page cache for both.
        val pagedStarted = SystemClock.elapsedRealtime()
        val paged = repository.observeChannelsForSource("big", null).first()
        val pagedMillis = SystemClock.elapsedRealtime() - pagedStarted
        assertEquals(visible, paged.size)

        val cursorStarted = SystemClock.elapsedRealtime()
        var rows = 0
        database.openHelper.readableDatabase.query(SimpleSQLiteQuery(ONE_ORDERED_STATEMENT, arrayOf("big"))).use { cursor ->
            val name = cursor.getColumnIndexOrThrow("channelName")
            while (cursor.moveToNext()) {
                cursor.getString(name)
                rows++
            }
        }
        val cursorMillis = SystemClock.elapsedRealtime() - cursorStarted
        assertEquals(visible, rows)

        Log.i(TAG, "$CHANNELS channels, $visible shown: paged and organised $pagedMillis ms; one ordered statement through a cursor $cursorMillis ms")
        println("GUIDE-CHANNEL-READ channels=$CHANNELS shown=$visible pagedMillis=$pagedMillis oneCursorMillis=$cursorMillis")
        assertTrue("paged $pagedMillis ms, one cursor $cursorMillis ms", pagedMillis * 2 < cursorMillis)
    }

    private companion object {
        const val TAG = "GuideChannelBenchmark"
        const val DB_NAME = "guide-channel-read-benchmark.db"
        const val CHANNELS = 50_000
        const val GROUPS = 400

        /** The statement the guide ran until preview 44: every row, in display order, from one cursor. */
        val ONE_ORDERED_STATEMENT: String = GUIDE_CHANNEL_PAGE_SQL
            .replace(ORGANIZATION_VISIBLE_LIVE_SOURCE_PREDICATE, ORGANIZATION_VISIBLE_LIVE_PREDICATE)
            .replace(
                "ORDER BY c.channelId",
                "ORDER BY source_state.priority DESC, source_state.name, COALESCE(preference.sortOrder, 2147483647)," +
                    " c.playlistOrder, COALESCE(NULLIF(preference.customName, ''), c.name)",
            )
            .replace(":sourceId", "?")
            .replace(":groupTitle", "NULL")
            .replace(":afterChannelId", "''")
            .replace(":limit", "-1")
            .also { check("c.playlistOrder," in it && "CASE WHEN c.channelId = ''" !in it) { "the old statement was not put back together" } }
    }
}
