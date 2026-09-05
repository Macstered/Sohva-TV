package com.streammate.tv.iptv.repository

import com.streammate.tv.core.network.GuideSource
import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.iptv.m3u.M3uParser
import com.streammate.tv.iptv.xmltv.XmlTvParser
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import com.streammate.tv.core.R as CoreR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideImportServiceTest {
    @Test
    fun `activates a playlist only after the staged import succeeds`() = runBlocking {
        val store = RecordingGuideStore()
        val service = service(
            source = TextGuideSource("#EXTINF:-1 tvg-id=one,One\nhttps://stream.example/one"),
            store = store,
        )

        val summary = service.refreshPlaylist("source-a", "https://provider.example/list.m3u")

        assertEquals(1, summary.channels)
        assertEquals("snapshot-1", store.activePlaylist)
        assertTrue(store.discardedPlaylists.isEmpty())
        assertTrue(store.channels.single().encryptedStreamUrl.startsWith("encrypted:"))
    }

    @Test
    fun `keeps the previous playlist active when a refresh fails`() {
        val store = RecordingGuideStore(activePlaylist = "known-good")
        val service = service(FailingGuideSource(), store)

        assertThrows(GuideImportException::class.java) {
            runBlocking { service.refreshPlaylist("source-a", "https://provider.example/list.m3u") }
        }

        assertEquals("known-good", store.activePlaylist)
        assertEquals(listOf("snapshot-1"), store.discardedPlaylists)
        assertEquals(1, store.refreshFailures.size)
        assertTrue(store.refreshFailures.single().third?.contains("password") == false)
    }

    @Test
    fun `both scope keeps M3U VOD entries out of the live guide`() = runBlocking {
        val playlist = """
            #EXTINF:-1 group-title="TV",Live
            http://provider.example/live/1.ts
            #EXTINF:-1 group-title="Movies",Film
            http://provider.example/movie/2.mkv
        """.trimIndent()
        val store = RecordingGuideStore()
        val source = IptvSourceConfiguration(
            id = "mixed",
            name = "Mixed",
            type = IptvSourceType.M3U,
            importScope = IptvImportScope.BOTH,
            m3uUrl = "http://provider.example/get.m3u",
        )

        val summary = service(TextGuideSource(playlist), store).refreshPlaylist(source)

        assertEquals(1, summary.channels)
        assertEquals("Live", store.channels.single().name)
    }

    @Test
    fun `streams a large playlist into bounded database batches`() = runBlocking {
        val channelCount = 20_000
        val playlist = buildString {
            appendLine("#EXTM3U")
            repeat(channelCount) { index ->
                appendLine("#EXTINF:-1 tvg-id=channel-$index group-title=Group,Channel $index")
                appendLine("http://stream.example/$index")
            }
        }
        val store = RecordingGuideStore()

        val summary = service(TextGuideSource(playlist), store)
            .refreshPlaylist("large-source", "http://provider.example/list.m3u")

        assertEquals(channelCount, summary.channels)
        assertEquals(channelCount, store.channels.size)
        assertEquals(80, store.channelBatchSizes.size)
        assertTrue(store.channelBatchSizes.all { it == 250 })
    }

    @Test
    fun `imports a seven day EPG in bounded database batches`() = runBlocking {
        val channelCount = 40
        val programmesPerChannel = 7 * 48
        val epg = buildString {
            appendLine("<tv>")
            repeat(channelCount) { channel ->
                appendLine("<channel id=\"channel-$channel\"><display-name>Channel $channel</display-name></channel>")
            }
            repeat(channelCount) { channel ->
                repeat(programmesPerChannel) { programme ->
                    val start = EPG_START.plusMinutes(programme * 30L)
                    val stop = start.plusMinutes(30)
                    append("<programme channel=\"channel-$channel\" start=\"")
                    append(start.format(XMLTV_FORMAT))
                    append(" +0000\" stop=\"")
                    append(stop.format(XMLTV_FORMAT))
                    appendLine(" +0000\"><title>Programme $programme</title></programme>")
                }
            }
            appendLine("</tv>")
        }
        val store = RecordingGuideStore()

        val summary = service(TextGuideSource(epg), store)
            .refreshEpg("large-source", "http://provider.example/guide.xml")

        val expectedProgrammes = channelCount * programmesPerChannel
        assertEquals(channelCount, summary.channels)
        assertEquals(expectedProgrammes, summary.programmes)
        assertEquals(expectedProgrammes, store.programmeBatchSizes.sum())
        assertTrue(store.programmeBatchSizes.all { it in 1..2_000 })
        assertEquals("snapshot-1", store.activeEpg)
    }

    @Test
    fun `programmes for channels the playlist does not carry are not written`() = runBlocking {
        val store = RecordingGuideStore().apply { referencedXmltvChannelIds = setOf("one.fi") }
        val epg = buildString {
            appendLine("<tv>")
            appendLine("<channel id=\"one.fi\"><display-name>One</display-name></channel>")
            appendLine("<channel id=\"other.fi\"><display-name>Other</display-name></channel>")
            listOf("one.fi", "other.fi", "one.fi", "other.fi").forEachIndexed { index, channel ->
                val start = EPG_START.plusMinutes(index * 30L)
                append("<programme channel=\"$channel\" start=\"").append(start.format(XMLTV_FORMAT))
                append(" +0000\" stop=\"").append(start.plusMinutes(30).format(XMLTV_FORMAT))
                appendLine(" +0000\"><title>P$index</title></programme>")
            }
            appendLine("</tv>")
        }

        val summary = service(TextGuideSource(epg), store).refreshEpg("source", "http://provider.example/guide.xml")

        // Both channels are kept for manual mapping; only one channel's programmes are written.
        assertEquals(2, summary.channels)
        assertEquals(2, summary.programmes)
        assertEquals(2, store.programmeBatchSizes.sum())
        assertEquals("snapshot-1", store.activeEpg)
    }

    @Test
    fun `programmes long past or far ahead are not written`() = runBlocking {
        val store = RecordingGuideStore()
        val epg = buildString {
            appendLine("<tv>")
            appendLine("<channel id=\"one.fi\"><display-name>One</display-name></channel>")
            listOf(-3L * 24, -1, 0, 2, 7 * 24, 9 * 24).forEach { hours ->
                val start = EPG_START.plusHours(6).plusHours(hours)
                append("<programme channel=\"one.fi\" start=\"").append(start.format(XMLTV_FORMAT))
                append(" +0000\" stop=\"").append(start.plusMinutes(30).format(XMLTV_FORMAT))
                appendLine(" +0000\"><title>At $hours h</title></programme>")
            }
            appendLine("</tv>")
        }

        val summary = service(TextGuideSource(epg), store).refreshEpg("source", "http://provider.example/guide.xml")

        // Three days ago and nine days ahead fall outside the window; the rest stay.
        assertEquals(4, summary.programmes)
    }

    @Test
    fun `keeps the previous guide when the feed matches none of the channels`() {
        val store = RecordingGuideStore().apply {
            activeEpg = "known-good"
            stagedEpgMatch = StagedEpgMatch(matchedProgrammes = 0, mappableChannels = 120)
        }

        val error = assertThrows(GuideImportException::class.java) {
            runBlocking {
                service(TextGuideSource(smallEpg()), store)
                    .refreshEpg("source", "http://provider.example/guide.xml")
            }
        }

        assertEquals(CoreR.string.error_epg_unmatched, error.messageResource)
        assertEquals("known-good", store.activeEpg)
        assertEquals(listOf("snapshot-1"), store.discardedEpgs)
        assertEquals(listOf("epg"), store.refreshFailures.map { it.second })
        assertTrue(store.refreshFailures.single().third!!.contains("120 channels"))
    }

    @Test
    fun `keeps the previous guide when the feed is empty`() {
        val store = RecordingGuideStore().apply { activeEpg = "known-good" }

        val error = assertThrows(GuideImportException::class.java) {
            runBlocking {
                service(TextGuideSource("<tv></tv>"), store)
                    .refreshEpg("source", "http://provider.example/guide.xml")
            }
        }

        assertEquals(CoreR.string.error_epg_empty, error.messageResource)
        assertEquals("known-good", store.activeEpg)
        assertEquals(listOf("snapshot-1"), store.discardedEpgs)
        assertEquals(1, store.refreshFailures.size)
    }

    @Test
    fun `activates a guide for a source whose channels carry no EPG ids`() = runBlocking {
        // Nothing can match yet, so nothing is being protected: the playlist
        // may simply not have been imported, or its channels have no ids.
        val store = RecordingGuideStore().apply {
            stagedEpgMatch = StagedEpgMatch(matchedProgrammes = 0, mappableChannels = 0)
        }

        val summary = service(TextGuideSource(smallEpg()), store)
            .refreshEpg("source", "http://provider.example/guide.xml")

        assertEquals(2, summary.programmes)
        assertEquals("snapshot-1", store.activeEpg)
        assertTrue(store.discardedEpgs.isEmpty())
    }

    private fun smallEpg(): String = buildString {
        appendLine("<tv>")
        appendLine("<channel id=\"one.fi\"><display-name>One</display-name></channel>")
        repeat(2) { index ->
            val start = EPG_START.plusMinutes(index * 30L)
            append("<programme channel=\"one.fi\" start=\"").append(start.format(XMLTV_FORMAT))
            append(" +0000\" stop=\"").append(start.plusMinutes(30).format(XMLTV_FORMAT))
            appendLine(" +0000\"><title>Programme $index</title></programme>")
        }
        appendLine("</tv>")
    }

    @Test
    fun `discards a partially downloaded playlist without replacing the active snapshot`() {
        val playlist = buildString {
            appendLine("#EXTM3U")
            repeat(600) { index ->
                appendLine("#EXTINF:-1,Channel $index")
                appendLine("http://stream.example/$index")
            }
        }
        val failAt = playlist.indexOf("#EXTINF:-1,Channel 320")
        val store = RecordingGuideStore(activePlaylist = "known-good")
        val service = service(InterruptedGuideSource(playlist, failAt), store)

        assertThrows(GuideImportException::class.java) {
            runBlocking { service.refreshPlaylist("source-a", "http://provider.example/list.m3u") }
        }

        assertEquals("known-good", store.activePlaylist)
        assertEquals(listOf(250), store.channelBatchSizes)
        assertEquals(listOf("snapshot-1"), store.discardedPlaylists)
    }

    @Test
    fun `cancelling an import propagates cancellation instead of recording a failure`() {
        // Cancellation is not an import failure. Converting it into a
        // GuideImportException breaks structured concurrency and writes a
        // spurious "refresh failed" state for a refresh the user or the
        // scheduler simply stopped.
        val store = RecordingGuideStore(activePlaylist = "known-good")
        val service = service(CancellingGuideSource(), store)

        val thrown = runCatching {
            runBlocking { service.refreshPlaylist("source-a", "https://provider.example/list.m3u") }
        }.exceptionOrNull()

        assertTrue(
            "expected CancellationException but was ${thrown?.let { it::class.java.name }}",
            thrown is CancellationException,
        )
        assertTrue("cancellation must not be recorded as a refresh failure", store.refreshFailures.isEmpty())
        assertEquals("known-good", store.activePlaylist)
    }

    @Test
    fun `cancelling an epg import propagates cancellation`() {
        val store = RecordingGuideStore().apply { activeEpg = "known-good" }
        val service = service(CancellingGuideSource(), store)

        val thrown = runCatching {
            runBlocking { service.refreshEpg("source-a", "https://provider.example/epg.xml") }
        }.exceptionOrNull()

        assertTrue(
            "expected CancellationException but was ${thrown?.let { it::class.java.name }}",
            thrown is CancellationException,
        )
        assertTrue("cancellation must not be recorded as a refresh failure", store.refreshFailures.isEmpty())
    }

    private fun service(source: GuideSource, store: RecordingGuideStore) = GuideImportService(
        sourceClient = source,
        m3uParser = M3uParser(),
        xmlTvParser = XmlTvParser(),
        store = store,
        secretCipher = PrefixCipher,
        // The feeds below are dated around EPG_START; the clock has to agree
        // or the import's time window would drop every programme.
        clock = { FIXED_NOW_MILLIS },
    )

    private companion object {
        val EPG_START: LocalDateTime = LocalDateTime.of(2026, 8, 24, 0, 0)
        val FIXED_NOW_MILLIS: Long = EPG_START.plusHours(6).toEpochSecond(java.time.ZoneOffset.UTC) * 1000
        val XMLTV_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    }
}

private class TextGuideSource(private val text: String) : GuideSource {
    override suspend fun <T> withSource(url: String, block: suspend (InputStream) -> T): T =
        ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)).use { block(it) }
}

private class CancellingGuideSource : GuideSource {
    override suspend fun <T> withSource(url: String, block: suspend (InputStream) -> T): T {
        throw CancellationException("import cancelled")
    }
}

private class FailingGuideSource : GuideSource {
    override suspend fun <T> withSource(url: String, block: suspend (InputStream) -> T): T {
        throw IOException("Network failed for https://viewer:password@provider.example/private/list")
    }
}

private class InterruptedGuideSource(
    private val text: String,
    private val failAt: Int,
) : GuideSource {
    override suspend fun <T> withSource(url: String, block: suspend (InputStream) -> T): T =
        InterruptedInputStream(text.toByteArray(Charsets.UTF_8), failAt).use { block(it) }
}

private class InterruptedInputStream(
    private val content: ByteArray,
    private val failAt: Int,
) : InputStream() {
    private var position = 0

    override fun read(): Int {
        if (position >= failAt) throw IOException("Network connection changed")
        return content[position++].toInt() and 0xff
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (position >= failAt) throw IOException("Network connection changed")
        if (position >= content.size) return -1
        val count = minOf(length, 64, failAt - position, content.size - position)
        content.copyInto(target, offset, position, position + count)
        position += count
        return count
    }
}

private object PrefixCipher : SecretCipher {
    override fun encrypt(plainText: String): String = "encrypted:$plainText"
    override fun decrypt(encoded: String): String = encoded.removePrefix("encrypted:")
}

private class RecordingGuideStore(
    var activePlaylist: String? = null,
) : GuideStore {
    val channels = mutableListOf<StoredIptvChannel>()
    val channelBatchSizes = mutableListOf<Int>()
    val programmeBatchSizes = mutableListOf<Int>()
    val discardedPlaylists = mutableListOf<String>()
    val refreshFailures = mutableListOf<Triple<String, String, String?>>()
    val discardedEpgs = mutableListOf<String>()
    var activeEpg: String? = null
    /** What the staged guide looks like against the channels; null means every programme matched one channel. */
    var stagedEpgMatch: StagedEpgMatch? = null
    private var nextId = 1

    override fun newSnapshotId(): String = "snapshot-${nextId++}"

    override suspend fun insertChannels(
        sourceId: String,
        snapshotId: String,
        channels: List<StoredIptvChannel>,
    ) {
        channelBatchSizes += channels.size
        this.channels += channels
    }

    override suspend fun insertXmlTvChannels(
        sourceId: String,
        snapshotId: String,
        channels: List<StoredXmlTvChannel>,
    ) = Unit

    override suspend fun insertProgrammes(
        sourceId: String,
        snapshotId: String,
        programmes: List<StoredProgramme>,
    ) {
        programmeBatchSizes += programmes.size
    }

    override suspend fun activatePlaylist(sourceId: String, snapshotId: String, itemCount: Int) {
        activePlaylist = snapshotId
    }

    override suspend fun activateEpg(sourceId: String, snapshotId: String, itemCount: Int) {
        activeEpg = snapshotId
    }

    override suspend fun discardPlaylist(sourceId: String, snapshotId: String) {
        discardedPlaylists += snapshotId
    }

    override suspend fun stagedEpgMatch(sourceId: String, snapshotId: String): StagedEpgMatch =
        stagedEpgMatch ?: StagedEpgMatch(matchedProgrammes = programmeBatchSizes.sum(), mappableChannels = 1)

    /** Empty by default: no playlist imported, so every programme is kept. */
    var referencedXmltvChannelIds: Set<String> = emptySet()

    override suspend fun referencedXmltvChannelIds(sourceId: String): Set<String> = referencedXmltvChannelIds

    override suspend fun discardEpg(sourceId: String, snapshotId: String) {
        discardedEpgs += snapshotId
    }

    override suspend fun markRefreshStarted(sourceId: String, kind: String) = Unit

    override suspend fun markRefreshFailed(sourceId: String, kind: String, redactedError: String?) {
        refreshFailures += Triple(sourceId, kind, redactedError)
    }
}
