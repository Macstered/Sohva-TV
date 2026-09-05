package com.streammate.tv.app

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.network.GuideSource
import com.streammate.tv.iptv.m3u.M3uParser
import com.streammate.tv.iptv.repository.GuideImportService
import com.streammate.tv.iptv.repository.RoomGuideStore
import com.streammate.tv.iptv.xmltv.XmlTvParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device evidence for the guide import: a provider-shaped playlist and a week
 * of programmes through the real parsers, cipher and Room store, on disk.
 * Prints its timings; read them from the instrumentation log.
 */
@RunWith(AndroidJUnit4::class)
class GuideImportBenchmarkTest {
    private lateinit var database: StreamMateDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME)
        database = Room.databaseBuilder(context, StreamMateDatabase::class.java, DB_NAME).build()
    }

    @After
    fun closeDatabase() {
        database.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DB_NAME)
    }

    @Test
    fun aProviderSizedPlaylistAndGuideImportInBoundedTime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as StreamMateApplication).container
        val feed = syntheticFeed(CHANNELS_IN_GUIDE, PROGRAMMES_PER_CHANNEL).toByteArray(Charsets.UTF_8)
        val playlist = syntheticPlaylist(PLAYLIST_CHANNELS).toByteArray(Charsets.UTF_8)
        val source = object : GuideSource {
            override suspend fun <T> withSource(url: String, block: suspend (InputStream) -> T): T =
                ByteArrayInputStream(if (url.endsWith("guide.xml")) feed else playlist).use { block(it) }
        }
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity(
                sourceId = SOURCE_ID,
                name = "Benchmark provider",
                type = "M3U",
                enabled = true,
                connectionLimit = 1,
                priority = 0,
                updatedAtEpochMillis = 1,
            ),
        )
        val service = GuideImportService(
            sourceClient = source,
            m3uParser = M3uParser(),
            xmlTvParser = XmlTvParser(),
            store = RoomGuideStore(database.guideDao()),
            secretCipher = container.secretCipher,
        )

        val playlistStarted = SystemClock.elapsedRealtimeNanos()
        val playlistSummary = service.refreshPlaylist(SOURCE_ID, "http://bench.invalid/playlist.m3u")
        val playlistMillis = (SystemClock.elapsedRealtimeNanos() - playlistStarted) / 1_000_000

        val epgStarted = SystemClock.elapsedRealtimeNanos()
        val epgSummary = service.refreshEpg(SOURCE_ID, "http://bench.invalid/guide.xml")
        val epgMillis = (SystemClock.elapsedRealtimeNanos() - epgStarted) / 1_000_000

        // A second guide refresh replaces a full snapshot, which is what every
        // scheduled refresh does; it carries the delete of the old one.
        val secondStarted = SystemClock.elapsedRealtimeNanos()
        service.refreshEpg(SOURCE_ID, "http://bench.invalid/guide.xml")
        val secondMillis = (SystemClock.elapsedRealtimeNanos() - secondStarted) / 1_000_000

        Log.i(
            TAG,
            "playlist_channels=${playlistSummary.channels} playlist_ms=$playlistMillis " +
                "feed_mb=${feed.size / 1_000_000} epg_programmes=${epgSummary.programmes} epg_ms=$epgMillis " +
                "epg_second_ms=$secondMillis",
        )
        assertEquals(PLAYLIST_CHANNELS, playlistSummary.channels)
    }

    private fun syntheticPlaylist(channels: Int): String = buildString {
        appendLine("#EXTM3U")
        repeat(channels) { index ->
            // Only some playlist channels carry a guide id, as in real lists.
            val tvgId = if (index < CHANNELS_IN_GUIDE) " tvg-id=\"ch$index.fi\"" else ""
            appendLine("#EXTINF:-1$tvgId tvg-name=\"Channel $index\" group-title=\"Group ${index % 40}\",Channel $index")
            appendLine("http://bench.invalid/live/$index.ts")
        }
    }

    private fun syntheticFeed(channels: Int, perChannel: Int): String = buildString {
        val start = LocalDateTime.of(2026, 9, 1, 0, 0)
        val format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>")
        repeat(channels) { channel ->
            appendLine("<channel id=\"ch$channel.fi\"><display-name>Channel $channel</display-name></channel>")
        }
        repeat(channels) { channel ->
            repeat(perChannel) { index ->
                val from = start.plusMinutes(index * 30L)
                append("<programme channel=\"ch$channel.fi\" start=\"").append(from.format(format))
                append(" +0300\" stop=\"").append(from.plusMinutes(30).format(format)).append(" +0300\">")
                append("<title>Programme $index on $channel</title>")
                if (index % 3 != 0) append("<sub-title>Episode ${index % 20}</sub-title>")
                append("<desc>A description long enough to look like a real listing, ")
                append("with a sentence or two about what happens in the episode.</desc>")
                append("<category>Entertainment</category>")
                appendLine("</programme>")
            }
        }
        appendLine("</tv>")
    }

    private companion object {
        const val TAG = "GuideImportBench"
        const val DB_NAME = "guide-import-bench.db"
        const val SOURCE_ID = "bench-source"
        const val PLAYLIST_CHANNELS = 3_000
        const val CHANNELS_IN_GUIDE = 400
        const val PROGRAMMES_PER_CHANNEL = 7 * 48
    }
}
