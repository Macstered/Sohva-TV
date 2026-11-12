package com.sohva.tv.core.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.sohva.tv.core.model.player.StreamReply

/**
 * Looks at the stream's own first request (the placeholder, from position 0) as it passes: the
 * reply's media type, whether a redirect changed the host, and its first [StreamReply.PEEK] bytes.
 * Only the classification is kept, in the [registry]; the bytes reach the player unchanged. When
 * the player cannot read the reply, the service names it in the diagnostics, and
 * [PlaylistRetryPolicy] lets it retry a playlist served at a plain address as HLS.
 */
@OptIn(UnstableApi::class)
internal class InspectingDataSource(private val upstream: DataSource, private val registry: StreamRegistry) : DataSource {
    private var head: ByteArray? = null
    private var filled = 0
    private var contentType: String? = null
    private var redirected = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        head = null
        val length = upstream.open(dataSpec)
        if (dataSpec.uri.scheme == StreamRegistry.SCHEME && dataSpec.position == 0L) {
            contentType = StreamReply.mediaType(header("Content-Type"))
            val host = upstream.uri?.host
            redirected = host != null && !host.equals(registry.addressHost(), ignoreCase = true)
            head = ByteArray(StreamReply.PEEK)
            filled = 0
            publish()
        }
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = upstream.read(buffer, offset, length)
        val peek = head
        if (peek != null && n > 0 && filled < peek.size) {
            val take = minOf(n, peek.size - filled)
            System.arraycopy(buffer, offset, peek, filled, take)
            filled += take
            publish()
            // Once full, nothing more is copied for this request.
            if (filled == peek.size) head = null
        }
        return n
    }

    private fun publish() {
        val peek = head ?: return
        registry.recordReply(StreamReply(StreamReply.kindOf(peek, filled), contentType, redirected))
    }

    private fun header(name: String): String? =
        upstream.responseHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        head = null
        upstream.close()
    }

    class Factory(private val upstream: DataSource.Factory, private val registry: StreamRegistry) : DataSource.Factory {
        override fun createDataSource(): DataSource = InspectingDataSource(upstream.createDataSource(), registry)
    }
}

/**
 * The default retry rules, except that the first "no extractor recognises this" failure of a
 * playlist reply waits as a retry instead of failing, while the service switches the item to HLS
 * (which replaces this load). If the switch does not happen, the retry fails as before.
 */
@OptIn(UnstableApi::class)
internal class PlaylistRetryPolicy(private val registry: StreamRegistry) : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (loadErrorInfo.exception is UnrecognizedInputFormatException && registry.claimPlaylistRetry()) return SWITCH_GRACE_MS
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    private companion object {
        /** Far longer than a main-thread hand-off; short enough that a missed switch still fails visibly. */
        const val SWITCH_GRACE_MS: Long = 3_000
    }
}
