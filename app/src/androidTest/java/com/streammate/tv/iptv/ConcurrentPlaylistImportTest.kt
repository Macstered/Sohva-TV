package com.streammate.tv.iptv

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.network.GuideSource
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.iptv.m3u.M3uParser
import com.streammate.tv.iptv.repository.GuideImportService
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.repository.GuideStore
import com.streammate.tv.iptv.repository.RoomGuideStore
import com.streammate.tv.iptv.repository.StoredIptvChannel
import com.streammate.tv.iptv.xmltv.XmlTvParser
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two imports of one source's playlist at once, as "Sync everything" in the
 * background and "Refresh channels" on screen make when pressed together.
 *
 * Activating a playlist deletes the source's other snapshots, including one a
 * second import is still staging. On 23 September 2026 a playlist whose
 * address had just been changed was left with an active snapshot and no
 * channels: Settings reported 1,826 imported and the guide did not list the
 * source at all.
 */
@RunWith(AndroidJUnit4::class)
class ConcurrentPlaylistImportTest {
    private lateinit var database: StreamMateDatabase

    @Before
    fun createSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        database.guideDao().upsertSourceState(IptvSourceStateEntity(SOURCE, "TV", "M3U", true, 1, 0, 1))
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun twoImportsOfOneSourceAtOnceLeaveItsChannelsInTheGuide() = runBlocking {
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        val firstStaged = CompletableDeferred<Unit>()
        val secondStaged = CompletableDeferred<Unit>()
        val firstActivated = CompletableDeferred<Unit>()
        val snapshots = AtomicInteger()
        val room = RoomGuideStore(database.guideDao())
        // The order that empties the source: both stage, the first activates,
        // then the second. Each wait is bounded, so an import that is made to
        // wait for the other, as it should be, is not held up for ever.
        val store = object : GuideStore by room {
            override fun newSnapshotId(): String = room.newSnapshotId().also {
                if (snapshots.getAndIncrement() == 0) first.complete(it) else second.complete(it)
            }

            override suspend fun insertChannels(sourceId: String, snapshotId: String, channels: List<StoredIptvChannel>) {
                room.insertChannels(sourceId, snapshotId, channels)
                if (snapshotId == first.await()) firstStaged.complete(Unit) else secondStaged.complete(Unit)
            }

            override suspend fun activatePlaylist(sourceId: String, snapshotId: String, itemCount: Int) {
                if (snapshotId == first.await()) {
                    withTimeoutOrNull(WAIT_MILLIS) { secondStaged.await() }
                    room.activatePlaylist(sourceId, snapshotId, itemCount)
                    firstActivated.complete(Unit)
                } else {
                    withTimeoutOrNull(WAIT_MILLIS) { firstActivated.await() }
                    room.activatePlaylist(sourceId, snapshotId, itemCount)
                }
            }
        }
        val service = GuideImportService(
            sourceClient = playlist(CHANNELS),
            m3uParser = M3uParser(),
            xmlTvParser = XmlTvParser(),
            store = store,
            secretCipher = PlainCipher,
        )

        withTimeout(60_000) {
            coroutineScope {
                val refreshing = async(Dispatchers.Default) { service.refreshPlaylist(SOURCE, "http://provider.invalid/new.m3u") }
                firstStaged.await()
                val refreshingAgain = async(Dispatchers.Default) { service.refreshPlaylist(SOURCE, "http://provider.invalid/new.m3u") }
                assertEquals(CHANNELS, refreshing.await().channels)
                assertEquals(CHANNELS, refreshingAgain.await().channels)
            }
        }

        val rail = GuideRepository(database.guideDao()).observeRail().first()
        assertEquals(
            "The source was left with an active snapshot and no channels",
            CHANNELS,
            rail.filter { it.sourceId == SOURCE }.sumOf { it.channelCount },
        )
    }

    private fun playlist(channels: Int): GuideSource = object : GuideSource {
        override suspend fun <T> withSource(url: String, block: suspend (InputStream) -> T): T =
            buildString {
                appendLine("#EXTM3U")
                repeat(channels) { index ->
                    appendLine("#EXTINF:-1 tvg-id=\"ch$index\" group-title=\"Group ${index % 4}\",Channel $index")
                    appendLine("http://provider.invalid/live/$index.ts")
                }
            }.byteInputStream().use { block(it) }
    }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plainText: String): String = plainText
        override fun decrypt(encoded: String): String = encoded
    }

    private companion object {
        const val SOURCE = "tv"
        const val CHANNELS = 60
        const val WAIT_MILLIS = 3_000L
    }
}
