package com.streammate.tv.iptv.repository

import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.network.GuideSource
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.iptv.m3u.M3uParser
import com.streammate.tv.iptv.xmltv.XmlTvParser
import com.streammate.tv.iptv.xtream.XtreamAccount
import com.streammate.tv.iptv.xtream.XtreamLiveChannel
import com.streammate.tv.iptv.xtream.XtreamSource
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class XtreamImportServiceTest {
    @Test
    fun `imports xtream live channels into an isolated source snapshot`() = runBlocking {
        val store = XtreamRecordingStore()
        val service = service(
            sourceClient = FakeXtreamSource(
                channels = listOf(
                    XtreamLiveChannel(
                        id = "xtream-42",
                        name = "Arena HD",
                        categoryName = "Sports",
                        epgChannelId = "arena.fi",
                        logoUrl = "https://images.example/arena.png",
                        streamUrl = "http://provider.example/live/viewer/password/42.ts",
                        playlistOrder = 12,
                    ),
                ),
            ),
            store = store,
        )

        val summary = service.refreshPlaylist(source())

        assertEquals(1, summary.channels)
        assertEquals("xtream-main", store.activeSourceId)
        assertEquals("snapshot-1", store.activeSnapshotId)
        assertEquals("arena hd", store.channels.single().normalizedName)
        assertEquals("Sports", store.channels.single().groupTitle)
        assertEquals(12, store.channels.single().playlistOrder)
        assertTrue(store.channels.single().encryptedStreamUrl.startsWith("encrypted:"))
    }

    @Test
    fun `vod only Xtream source cannot populate live channels`() {
        val service = service(FakeXtreamSource(emptyList()), XtreamRecordingStore())

        val error = assertThrows(LocalizedException::class.java) {
            runBlocking { service.refreshPlaylist(source().copy(importScope = IptvImportScope.VOD)) }
        }
        assertEquals(CoreR.string.error_source_no_live_tv, error.messageResource)
    }

    private fun service(sourceClient: XtreamSource, store: XtreamRecordingStore): XtreamImportService {
        val guideImporter = GuideImportService(
            sourceClient = UnusedGuideSource,
            m3uParser = M3uParser(),
            xmlTvParser = XmlTvParser(),
            store = store,
            secretCipher = XtreamPrefixCipher,
        )
        return XtreamImportService(sourceClient, store, XtreamPrefixCipher, guideImporter)
    }

    private fun source() = IptvSourceConfiguration(
        id = "xtream-main",
        name = "Xtream",
        type = IptvSourceType.XTREAM,
        xtreamBaseUrl = "http://provider.example",
        xtreamUsername = "viewer",
        xtreamPassword = "password",
    )
}

private class FakeXtreamSource(
    private val channels: List<XtreamLiveChannel>,
) : XtreamSource {
    override suspend fun authenticate(source: IptvSourceConfiguration) =
        XtreamAccount("viewer", "Active", 1, 0)

    override suspend fun liveChannels(source: IptvSourceConfiguration): List<XtreamLiveChannel> = channels

    override fun xmlTvUrl(source: IptvSourceConfiguration): String =
        "http://provider.example/xmltv.php?username=viewer&password=password"
}

private object UnusedGuideSource : GuideSource {
    override suspend fun <T> withSource(url: String, block: suspend (InputStream) -> T): T =
        error("Not used")
}

private object XtreamPrefixCipher : SecretCipher {
    override fun encrypt(plainText: String): String = "encrypted:$plainText"
    override fun decrypt(encoded: String): String = encoded.removePrefix("encrypted:")
}

private class XtreamRecordingStore : GuideStore {
    val channels = mutableListOf<StoredIptvChannel>()
    var activeSourceId: String? = null
    var activeSnapshotId: String? = null

    override fun newSnapshotId(): String = "snapshot-1"

    override suspend fun insertChannels(
        sourceId: String,
        snapshotId: String,
        channels: List<StoredIptvChannel>,
    ) {
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
    ) = Unit

    override suspend fun activatePlaylist(sourceId: String, snapshotId: String, itemCount: Int) {
        activeSourceId = sourceId
        activeSnapshotId = snapshotId
    }

    override suspend fun activateEpg(sourceId: String, snapshotId: String, itemCount: Int) = Unit
    override suspend fun stagedEpgMatch(sourceId: String, snapshotId: String) = StagedEpgMatch(1, 1)
    override suspend fun discardPlaylist(sourceId: String, snapshotId: String) = Unit
    override suspend fun discardEpg(sourceId: String, snapshotId: String) = Unit
    override suspend fun markRefreshStarted(sourceId: String, kind: String) = Unit
    override suspend fun markRefreshFailed(sourceId: String, kind: String, redactedError: String?) = Unit
}
