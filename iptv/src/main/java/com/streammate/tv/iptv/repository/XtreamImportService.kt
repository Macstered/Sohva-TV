package com.streammate.tv.iptv.repository

import com.streammate.tv.core.error.localizedTransportFailure
import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import androidx.annotation.StringRes
import com.streammate.tv.core.database.GuideDao
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretRedactor
import com.streammate.tv.iptv.m3u.ChannelNameNormalizer
import com.streammate.tv.iptv.xtream.XtreamSource

class XtreamImportService(
    private val client: XtreamSource,
    private val store: GuideStore,
    private val secretCipher: SecretCipher,
    private val guideImportService: GuideImportService,
) {
    suspend fun refreshPlaylist(source: IptvSourceConfiguration): ImportSummary {
        if (source.type != IptvSourceType.XTREAM) {
            throw LocalizedException(CoreR.string.error_source_not_xtream)
        }
        if (!source.importScope.importsLiveTv) {
            throw LocalizedException(CoreR.string.error_source_no_live_tv)
        }
        val snapshotId = store.newSnapshotId()
        store.markRefreshStarted(source.id, GuideDao.PLAYLIST_KIND)
        return try {
            val channels = client.liveChannels(source)
            channels.chunked(BATCH_SIZE).forEach { batch ->
                store.insertChannels(
                    sourceId = source.id,
                    snapshotId = snapshotId,
                    channels = batch.map { channel ->
                        StoredIptvChannel(
                            id = channel.id,
                            tvgId = channel.epgChannelId,
                            name = channel.name,
                            normalizedName = ChannelNameNormalizer.normalize(channel.name),
                            groupTitle = channel.categoryName,
                            providerGroupId = channel.categoryId,
                            logoUrl = channel.logoUrl,
                            encryptedStreamUrl = secretCipher.encrypt(channel.streamUrl),
                            userAgent = null,
                            referrer = null,
                            catchupType = channel.archiveDurationDays?.let { "xtream" },
                            catchupDays = channel.archiveDurationDays,
                            xtreamStreamId = channel.streamId,
                            catchupTimeZone = channel.serverTimeZoneId,
                            playlistOrder = channel.playlistOrder,
                        )
                    },
                )
            }
            store.activatePlaylist(source.id, snapshotId, channels.size)
            ImportSummary(channels = channels.size)
        } catch (error: Throwable) {
            store.discardPlaylist(source.id, snapshotId)
            val redactedError = SecretRedactor.redact(error.message)
            runCatching {
                store.markRefreshFailed(source.id, GuideDao.PLAYLIST_KIND, redactedError)
            }
            throw localizedTransportFailure(error, ::GuideImportException)
        }
    }

    suspend fun refreshEpg(source: IptvSourceConfiguration): ImportSummary {
        if (source.type != IptvSourceType.XTREAM) {
            throw LocalizedException(CoreR.string.error_source_not_xtream)
        }
        if (!source.importScope.importsLiveTv) {
            throw LocalizedException(CoreR.string.error_source_no_live_tv)
        }
        return guideImportService.refreshEpg(source.id, client.xmlTvUrl(source))
    }

    suspend fun authenticate(source: IptvSourceConfiguration) = client.authenticate(source)

    private companion object {
        const val BATCH_SIZE = 250
    }
}
