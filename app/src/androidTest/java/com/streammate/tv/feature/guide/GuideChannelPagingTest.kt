package com.streammate.tv.feature.guide

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.ChannelPreferenceEntity
import com.streammate.tv.core.database.IptvChannelEntity
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.repository.GuideTimelineChannel
import com.streammate.tv.iptv.repository.OrganizationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The guide's channel rows, read a page at a time through Room as the app
 * reads them. One statement for a whole source cost a 50,000-channel source a
 * minute on the Shield, and could not be stopped once nobody wanted it.
 */
@RunWith(AndroidJUnit4::class)
class GuideChannelPagingTest {
    private lateinit var database: StreamMateDatabase
    private val pageReads = AtomicInteger()
    @Volatile private var beforePage: ((Int) -> Unit)? = null

    @Before
    fun createSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if ("c.channelId > ?" in sql) {
                    val read = pageReads.incrementAndGet()
                    beforePage?.invoke(read)
                }
            }, { it.run() })
            .build()
        val dao = database.guideDao()
        dao.upsertSourceState(IptvSourceStateEntity("test", "Test source", "M3U", true, 1, 0, 1))
        // Ids run one way and the playlist the other, so rows that came back
        // in the order they were read would be exactly backwards.
        dao.upsertChannels((1..CHANNELS).map { index -> channel("playlist", index, "Channel") })
        dao.activatePlaylistSnapshot("test", "playlist", CHANNELS, 1)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun aSourceOfSeveralPagesComesBackWholeInTheOrderTheGuideShows() = runBlocking {
        val channels = GuideRepository(database.guideDao()).observeChannelsForSource("test", null).first()
        assertEquals(CHANNELS, channels.size)
        assertEquals((1..CHANNELS).toList(), channels.map { it.playlistOrder })
        assertEquals("Channel 1", channels.first().name)
        assertTrue(channels.all { it.programmes.isEmpty() && it.groupTitle == "Group ${it.playlistOrder % 5}" })
        assertEquals("Two full pages and the short one that ends the read", 3, pageReads.get())
        // Fifty thousand rows hold one copy of what a source repeats on each.
        assertTrue(channels.all { it.sourceName === channels.first().sourceName })
    }

    @Test
    fun aGroupAndTheRulesAreAppliedToEveryPage() = runBlocking {
        val organization = OrganizationRepository(database.organizationDao())
        organization.change(listOf(com.streammate.tv.core.database.OrganizationChange(
            com.streammate.tv.core.model.OrganizationKey(com.streammate.tv.core.model.LibraryRoom.LIVE, "test", "name:group 3"),
            enabled = false, changeEnabled = true,
        )))
        val repository = GuideRepository(database.guideDao(), organization = organization)
        val source = repository.observeChannelsForSource("test", null).first()
        assertEquals(CHANNELS - CHANNELS / 5, source.size)
        assertTrue(source.none { it.groupTitle == "Group 3" })
        val group = repository.observeChannelsForSource("test", "Group 2").first()
        assertEquals(CHANNELS / 5, group.size)
        assertTrue(group.all { it.groupTitle == "Group 2" })
        assertTrue(repository.observeChannelsForSource("test", "Group 3").first().isEmpty())
    }

    @Test
    fun aWriteBehindTheRowsBringsAFreshRead() = runBlocking {
        val emissions = Channel<List<GuideTimelineChannel>>(Channel.UNLIMITED)
        val collecting = CoroutineScope(Dispatchers.Default).launch {
            GuideRepository(database.guideDao()).observeChannelsForSource("test", null).collect { emissions.send(it) }
        }
        assertEquals(CHANNELS, withTimeout(30_000) { emissions.receive() }.size)
        database.guideDao().upsertChannelPreference(
            ChannelPreferenceEntity(id(7), "test", "Renamed", null, hidden = false, sortOrder = 0, manualXmltvChannelId = null, updatedAtEpochMillis = 2),
        )
        val renamed = withTimeout(30_000) { emissions.receive() }
        assertEquals("The viewer's own position comes first", "Renamed", renamed.first().name)
        assertEquals(CHANNELS, renamed.size)
        database.guideDao().upsertChannelPreference(
            ChannelPreferenceEntity(id(7), "test", "Renamed", null, hidden = true, sortOrder = 0, manualXmltvChannelId = null, updatedAtEpochMillis = 3),
        )
        val hidden = withTimeout(30_000) { emissions.receive() }
        assertEquals(CHANNELS - 1, hidden.size)
        assertTrue(hidden.none { it.id == id(7) })
        collecting.cancelAndJoin()
    }

    @Test
    fun aPlaylistActivatedBetweenTwoPagesIsReadAgainRatherThanSpliced() = runBlocking {
        database.guideDao().upsertChannels((1..CHANNELS).map { index -> channel("next", index, "Next") })
        beforePage = { read ->
            // As the second page is about to be read, the refresh that staged
            // "next" activates it. On this thread and straight through the
            // helper: a suspend call would wait for the executor it is on.
            if (read == 2) {
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE import_state SET activeSnapshotId = 'next' WHERE sourceId = 'test' AND kind = 'playlist'",
                )
            }
        }
        val channels = GuideRepository(database.guideDao()).observeChannelsForSource("test", null).first()
        assertEquals(CHANNELS, channels.size)
        assertTrue("Rows of the old playlist were spliced onto the new", channels.all { it.name.startsWith("Next ") })
        assertEquals((1..CHANNELS).toList(), channels.map { it.playlistOrder })
        assertEquals("One page, the page that showed the splice, then the whole read again", 2 + 3, pageReads.get())
    }

    @Test
    fun aReadNobodyIsWaitingForStopsAtItsNextPage() = runBlocking {
        val firstPageStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        beforePage = { read ->
            if (read == 1) {
                firstPageStarted.countDown()
                check(release.await(30, TimeUnit.SECONDS)) { "Test did not release the first page" }
            }
        }
        val collecting = CoroutineScope(Dispatchers.Default).launch {
            GuideRepository(database.guideDao()).observeChannelsForSource("test", null).collect { }
        }
        assertTrue(firstPageStarted.await(30, TimeUnit.SECONDS))
        // The viewer chose another group while the first page was being read.
        collecting.cancel()
        release.countDown()
        collecting.join()
        Thread.sleep(500)
        assertEquals("The read went on without anyone to read for", 1, pageReads.get())
    }

    // Leaving the guide for a channel and coming back read the whole source
    // again: three seconds of "Loading" on the Shield for 56,000 channels.
    @Test
    fun aReturnToTheGuideIsGivenTheRowsLastReadWithoutReadingThem() = runBlocking {
        val keeping = CoroutineScope(Dispatchers.Default)
        val repository = GuideRepository(database.guideDao(), rosterScope = keeping)
        val first = repository.observeChannelsForSource("test", null).first()
        assertEquals(3, pageReads.get())
        val second = repository.observeChannelsForSource("test", null).first()
        assertEquals("The rows were read again", 3, pageReads.get())
        assertTrue(first === second)
        // Another selection is another read, and takes the place of the first.
        assertEquals(CHANNELS / 5, repository.observeChannelsForSource("test", "Group 2").first().size)
        assertEquals(4, pageReads.get())
        assertEquals(CHANNELS, repository.observeChannelsForSource("test", null).first().size)
        assertEquals(7, pageReads.get())
        keeping.cancel()
    }

    @Test
    fun aRepositoryGivenNowhereToKeepRowsReadsThemEachTime() = runBlocking {
        val repository = GuideRepository(database.guideDao())
        repository.observeChannelsForSource("test", null).first()
        repository.observeChannelsForSource("test", null).first()
        assertEquals(6, pageReads.get())
    }

    @Test
    fun aWriteMadeWhileNobodyWasInTheGuideIsSeenOnReturn() = runBlocking {
        val keeping = CoroutineScope(Dispatchers.Default)
        val repository = GuideRepository(database.guideDao(), rosterScope = keeping)
        assertEquals("Channel 1", repository.observeChannelsForSource("test", null).first().first().name)
        database.guideDao().upsertChannelPreference(
            ChannelPreferenceEntity(id(7), "test", "Renamed", null, hidden = false, sortOrder = 0, manualXmltvChannelId = null, updatedAtEpochMillis = 2),
        )
        // Room tells its observers of a write a moment after it; in the app
        // the moment is the time it takes to get back to the guide.
        val renamed = withTimeout(30_000) {
            var rows = repository.observeChannelsForSource("test", null).first()
            while (rows.first().name != "Renamed") {
                kotlinx.coroutines.delay(50)
                rows = repository.observeChannelsForSource("test", null).first()
            }
            rows
        }
        assertEquals(CHANNELS, renamed.size)
        keeping.cancel()
    }

    @Test
    fun aPlaylistActivatedWhileNobodyWasInTheGuideIsReadOnReturnWhateverRoomHasSaid() = runBlocking {
        val keeping = CoroutineScope(Dispatchers.Default)
        val repository = GuideRepository(database.guideDao(), rosterScope = keeping)
        repository.observeChannelsForSource("test", null).first()
        database.guideDao().upsertChannels((1..CHANNELS).map { index -> channel("next", index, "Next") })
        // Wait out the word of that write, so that what follows is the kept
        // rows' own check and not the count of writes.
        withTimeout(30_000) {
            while (true) {
                val before = pageReads.get()
                repository.observeChannelsForSource("test", null).first()
                if (pageReads.get() == before) break
                kotlinx.coroutines.delay(50)
            }
        }
        // Straight through the helper: Room is told nothing of this one.
        database.openHelper.writableDatabase.execSQL(
            "UPDATE import_state SET activeSnapshotId = 'next' WHERE sourceId = 'test' AND kind = 'playlist'",
        )
        val channels = repository.observeChannelsForSource("test", null).first()
        assertTrue("The old playlist's rows were served", channels.all { it.name.startsWith("Next ") })
        keeping.cancel()
    }

    @Test
    fun theRowsAreLetGoWhenNobodyComesBackForThem() = runBlocking {
        val keeping = CoroutineScope(Dispatchers.Default)
        val repository = GuideRepository(database.guideDao(), rosterScope = keeping, rosterKeptMillis = 200)
        repository.observeChannelsForSource("test", null).first()
        assertEquals(3, pageReads.get())
        Thread.sleep(1_000)
        repository.observeChannelsForSource("test", null).first()
        assertEquals("Tens of megabytes were held for a visit that did not come", 6, pageReads.get())
        keeping.cancel()
    }

    private fun id(index: Int) = "test:" + (CHANNELS - index).toString().padStart(5, '0')

    private fun channel(snapshot: String, index: Int, name: String) = IptvChannelEntity(
        sourceId = "test",
        snapshotId = snapshot,
        channelId = id(index),
        tvgId = null,
        name = "$name $index",
        normalizedName = "${name.lowercase()} $index",
        groupTitle = "Group ${index % 5}",
        logoUrl = null,
        encryptedStreamUrl = "encrypted",
        userAgent = null,
        referrer = null,
        lastSeenEpochMillis = 1,
        playlistOrder = index,
    )

    private companion object {
        /** Two full pages of the repository's two thousand and part of a third. */
        const val CHANNELS = 4_500
    }
}
