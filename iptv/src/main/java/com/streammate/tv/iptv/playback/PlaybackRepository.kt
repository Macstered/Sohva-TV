package com.streammate.tv.iptv.playback

import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.repository.CatalogueRepository

class PlaybackSource(
    val sourceId: String,
    val channelId: String,
    val channelName: String,
    val streamUrl: String,
    val headers: Map<String, String>,
    val connectionLimit: Int,
    private val lease: ConnectionLease,
) : AutoCloseable {
    override fun toString(): String =
        "PlaybackSource(sourceId=$sourceId, channelId=$channelId, channelName=$channelName, " +
            "streamUrl=<redacted>, connectionLimit=$connectionLimit)"

    override fun close() = lease.close()
}

class PlaybackRepository(
    private val guideRepository: GuideRepository,
    private val secretCipher: SecretCipher,
    private val connectionLimiter: SourceConnectionLimiter,
    private val catalogueRepository: CatalogueRepository,
    private val catchupUrlResolver: CatchupUrlResolver = CatchupUrlResolver(),
) {
    suspend fun sourceFor(channelId: String): PlaybackSource? {
        val channel = guideRepository.activeChannel(channelId) ?: return null
        val streamUrl = secretCipher.decrypt(channel.encryptedStreamUrl)
        return playbackSource(
            sourceId = channel.sourceId,
            mediaId = channel.channelId,
            displayName = channel.name,
            streamUrl = streamUrl,
            headers = buildMap {
                channel.userAgent?.let { put("User-Agent", it) }
                channel.referrer?.let { put("Referer", it) }
            },
        )
    }

    suspend fun catchupSourceFor(
        channelId: String,
        programmeStartEpochMillis: Long,
        programmeStopEpochMillis: Long,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): PlaybackSource? {
        val channel = guideRepository.activeChannel(channelId) ?: return null
        val catchupType = channel.catchupType?.takeIf(String::isNotBlank) ?: return null
        val catchupDays = channel.catchupDays?.takeIf { it > 0 }?.coerceAtMost(MAX_CATCHUP_DAYS) ?: return null
        val earliestStart = nowEpochMillis - catchupDays * MILLIS_PER_DAY
        if (programmeStartEpochMillis < earliestStart || programmeStartEpochMillis > nowEpochMillis) return null
        val liveUrl = secretCipher.decrypt(channel.encryptedStreamUrl)
        val streamUrl = catchupUrlResolver.resolve(
            CatchupUrlRequest(
                liveUrl = liveUrl,
                catchupType = catchupType,
                catchupSource = channel.catchupSource,
                xtreamStreamId = channel.xtreamStreamId,
                timeZoneId = channel.catchupTimeZone,
                programmeStartEpochMillis = programmeStartEpochMillis,
                programmeStopEpochMillis = programmeStopEpochMillis,
                nowEpochMillis = nowEpochMillis,
            ),
        ) ?: return null
        return playbackSource(
            sourceId = channel.sourceId,
            mediaId = channel.channelId,
            displayName = channel.name,
            streamUrl = streamUrl,
            headers = buildMap {
                channel.userAgent?.let { put("User-Agent", it) }
                channel.referrer?.let { put("Referer", it) }
            },
        )
    }

    suspend fun vodSourceFor(contentKey: String): PlaybackSource? {
        val playable = catalogueRepository.playable(contentKey) ?: return null
        return playbackSource(
            sourceId = playable.sourceId,
            mediaId = playable.contentKey,
            displayName = playable.title,
            streamUrl = secretCipher.decrypt(playable.encryptedStreamUrl),
            headers = emptyMap(),
        )
    }

    private suspend fun playbackSource(
        sourceId: String,
        mediaId: String,
        displayName: String,
        streamUrl: String,
        headers: Map<String, String>,
    ): PlaybackSource? {
        val sourceState = guideRepository.activeSourceState(sourceId) ?: return null
        val lease = connectionLimiter.tryAcquire(sourceId, sourceState.connectionLimit)
            ?: throw SourceConnectionLimitException(sourceState.name, sourceState.connectionLimit)
        return try {
            PlaybackSource(
                sourceId = sourceId,
                channelId = mediaId,
                channelName = displayName,
                streamUrl = streamUrl,
                headers = headers,
                connectionLimit = sourceState.connectionLimit,
                lease = lease,
            )
        } catch (error: Throwable) {
            lease.close()
            throw error
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val MAX_CATCHUP_DAYS = 365
    }
}

object PlaybackRequestExtras {
    const val CATCHUP_START_EPOCH_MILLIS = "com.streammate.tv.extra.CATCHUP_START_EPOCH_MILLIS"
    const val CATCHUP_STOP_EPOCH_MILLIS = "com.streammate.tv.extra.CATCHUP_STOP_EPOCH_MILLIS"
    const val VOD_CONTENT = "com.streammate.tv.extra.VOD_CONTENT"
}

class SourceConnectionLimitException(sourceName: String, limit: Int) : LocalizedException(
    CoreR.string.error_source_connection_limit,
    listOf(sourceName, limit),
)
