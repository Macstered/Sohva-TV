package com.streammate.tv.iptv.repository

import com.streammate.tv.core.error.localizedTransportFailure
import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import androidx.annotation.StringRes
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import com.streammate.tv.core.network.GuideSource
import com.streammate.tv.core.database.GuideDao
import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretRedactor
import com.streammate.tv.iptv.m3u.M3uParser
import com.streammate.tv.iptv.m3u.M3uContentKind
import com.streammate.tv.iptv.xmltv.XmlTvParser
import com.streammate.tv.iptv.xmltv.XmlTvRecord

data class ImportSummary(
    val channels: Int = 0,
    val programmes: Int = 0,
)

class GuideImportService(
    private val sourceClient: GuideSource,
    private val m3uParser: M3uParser,
    private val xmlTvParser: XmlTvParser,
    private val store: GuideStore,
    private val secretCipher: SecretCipher,
) {
    suspend fun refreshPlaylist(source: IptvSourceConfiguration): ImportSummary {
        if (source.type != IptvSourceType.M3U) {
            throw LocalizedException(CoreR.string.error_source_not_m3u)
        }
        if (!source.importScope.importsLiveTv) {
            throw LocalizedException(CoreR.string.error_source_no_live_tv)
        }
        val url = source.m3uUrl?.takeIf(String::isNotBlank)
            ?: throw GuideImportException(CoreR.string.error_m3u_url_missing)
        return refreshPlaylist(
            sourceId = source.id,
            url = url,
            includeEntry = { entry ->
                source.importScope == IptvImportScope.LIVE_TV || entry.contentKind == M3uContentKind.LIVE
            },
        )
    }

    suspend fun refreshPlaylist(sourceId: String, url: String): ImportSummary {
        return refreshPlaylist(sourceId, url) { true }
    }

    private suspend fun refreshPlaylist(
        sourceId: String,
        url: String,
        includeEntry: (com.streammate.tv.iptv.m3u.ParsedIptvChannel) -> Boolean,
    ): ImportSummary {
        val snapshotId = store.newSnapshotId()
        store.markRefreshStarted(sourceId, GuideDao.PLAYLIST_KIND)
        return try {
            var channelCount = 0
            val channelBatch = mutableListOf<StoredIptvChannel>()
            sourceClient.withSource(url) { input ->
                for (channel in m3uParser.records(input)) {
                    if (!includeEntry(channel)) continue
                    channelBatch += StoredIptvChannel(
                        id = channel.id,
                        tvgId = channel.tvgId,
                        name = channel.name,
                        normalizedName = channel.normalizedName,
                        groupTitle = channel.groupTitle,
                        logoUrl = channel.logoUrl,
                        encryptedStreamUrl = secretCipher.encrypt(channel.streamUrl),
                        userAgent = channel.userAgent,
                        referrer = channel.referrer,
                        catchupType = channel.catchupType,
                        catchupSource = channel.catchupSource,
                        catchupDays = channel.catchupDays,
                        playlistOrder = channel.playlistOrder,
                    )
                    channelCount += 1
                    if (channelBatch.size >= BATCH_SIZE) {
                        store.insertChannels(sourceId, snapshotId, channelBatch.toList())
                        channelBatch.clear()
                    }
                }
                if (channelBatch.isNotEmpty()) {
                    store.insertChannels(sourceId, snapshotId, channelBatch.toList())
                    channelBatch.clear()
                }
            }
            store.activatePlaylist(sourceId, snapshotId, channelCount)
            ImportSummary(channels = channelCount)
        } catch (cancellation: CancellationException) {
            // Cancellation is not an import failure. Converting it into a
            // GuideImportException broke structured concurrency and recorded a
            // spurious refresh failure for a refresh that was simply stopped.
            // The staged snapshot still has to go, hence NonCancellable.
            withContext(NonCancellable) { store.discardPlaylist(sourceId, snapshotId) }
            throw cancellation
        } catch (error: Throwable) {
            store.discardPlaylist(sourceId, snapshotId)
            val redactedError = SecretRedactor.redact(error.message)
            runCatching { store.markRefreshFailed(sourceId, GuideDao.PLAYLIST_KIND, redactedError) }
            throw localizedTransportFailure(error, ::GuideImportException)
        }
    }

    suspend fun refreshEpg(sourceId: String, url: String): ImportSummary {
        val snapshotId = store.newSnapshotId()
        store.markRefreshStarted(sourceId, GuideDao.EPG_KIND)
        var channelCount = 0
        var programmeCount = 0
        val channelBatch = mutableListOf<StoredXmlTvChannel>()
        val programmeBatch = mutableListOf<StoredProgramme>()
        return try {
            sourceClient.withSource(url) { input ->
                for (record in xmlTvParser.records(input)) {
                    when (record) {
                        is XmlTvRecord.Channel -> {
                            channelBatch += StoredXmlTvChannel(
                                id = record.id,
                                displayName = record.displayName,
                                iconUrl = record.iconUrl,
                            )
                            channelCount += 1
                            if (channelBatch.size >= BATCH_SIZE) {
                                store.insertXmlTvChannels(sourceId, snapshotId, channelBatch.toList())
                                channelBatch.clear()
                            }
                        }
                        is XmlTvRecord.Programme -> {
                            programmeBatch += StoredProgramme(
                                id = record.id,
                                channelId = record.channelId,
                                startEpochMillis = record.startEpochMillis,
                                stopEpochMillis = record.stopEpochMillis,
                                title = record.title,
                                subtitle = record.subtitle,
                                description = record.description,
                                categories = record.categories,
                            )
                            programmeCount += 1
                            if (programmeBatch.size >= BATCH_SIZE) {
                                store.insertProgrammes(sourceId, snapshotId, programmeBatch.toList())
                                programmeBatch.clear()
                            }
                        }
                    }
                }
                if (channelBatch.isNotEmpty()) {
                    store.insertXmlTvChannels(sourceId, snapshotId, channelBatch.toList())
                    channelBatch.clear()
                }
                if (programmeBatch.isNotEmpty()) {
                    store.insertProgrammes(sourceId, snapshotId, programmeBatch.toList())
                    programmeBatch.clear()
                }
            }
            store.activateEpg(sourceId, snapshotId, programmeCount)
            ImportSummary(channels = channelCount, programmes = programmeCount)
        } catch (cancellation: CancellationException) {
            // Cancellation is not an import failure. Converting it into a
            // GuideImportException broke structured concurrency and recorded a
            // spurious refresh failure for a refresh that was simply stopped.
            // The staged snapshot still has to go, hence NonCancellable.
            withContext(NonCancellable) { store.discardEpg(sourceId, snapshotId) }
            throw cancellation
        } catch (error: Throwable) {
            store.discardEpg(sourceId, snapshotId)
            val redactedError = SecretRedactor.redact(error.message)
            runCatching { store.markRefreshFailed(sourceId, GuideDao.EPG_KIND, redactedError) }
            throw localizedTransportFailure(error, ::GuideImportException)
        }
    }

    private companion object {
        const val BATCH_SIZE = 250
    }
}

class GuideImportException(
    @StringRes messageResource: Int,
    messageArguments: List<Any> = emptyList(),
    logMessage: String? = null,
    cause: Throwable? = null,
) : LocalizedException(messageResource, messageArguments, logMessage, cause)
