package com.sohva.tv.core.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.sohva.tv.core.model.player.StreamContainer
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Per-source connection leases (spec 30 PLAY-FR-16): counted in memory; a lease is released
 * exactly once; the player releases the old stream's lease before the next channel takes one, so
 * zapping works with a limit of 1.
 */
class ConnectionLeases {
    private val inUse = HashMap<String, Int>()

    inner class Lease internal constructor(val sourceId: String) {
        private var released = false

        fun release() {
            synchronized(inUse) {
                if (released) return
                released = true
                val left = (inUse[sourceId] ?: 1) - 1
                if (left <= 0) inUse.remove(sourceId) else inUse[sourceId] = left
            }
        }
    }

    /** A lease, or null when [limit] streams of [sourceId] are already open. */
    fun acquire(sourceId: String, limit: Int): Lease? = synchronized(inUse) {
        val used = inUse[sourceId] ?: 0
        if (used >= limit.coerceAtLeast(1)) return null
        inUse[sourceId] = used + 1
        Lease(sourceId)
    }

    fun inUse(sourceId: String): Int = synchronized(inUse) { inUse[sourceId] ?: 0 }
}

/**
 * The playing stream behind its placeholder (spec 30 PLAY-FR-13..17, L-23, L-05): the session and
 * the screen only ever see `sohva://channel/<key>`; the data source swaps in the real address and
 * adds the provider's headers to every request, manifest and segments alike.
 */
@OptIn(UnstableApi::class)
class StreamRegistry(private val defaultAgent: String) {
    @Volatile
    private var current: Entry? = null

    private class Entry(val placeholder: String, val address: String, val headers: Map<String, String>, val origin: String?)

    fun register(stream: ResolvedStream): Uri {
        val placeholder = placeholder(stream.key)
        val origin = if (stream.addon) stream.address.toHttpUrlOrNull()?.let(AddonTransport::origin) else null
        current = Entry(placeholder.toString(), stream.address, headers(stream), origin)
        return placeholder
    }

    fun clear() {
        current = null
    }

    /** For [ResolvingDataSource]: the placeholder becomes the address; every request gets the headers. */
    val resolver: ResolvingDataSource.Resolver = ResolvingDataSource.Resolver { spec -> resolve(spec) }

    private fun resolve(spec: DataSpec): DataSpec {
        val entry = current
        val uri = spec.uri
        if (uri.scheme == SCHEME) {
            if (entry == null || entry.placeholder != uri.toString()) throw IOException("The stream is no longer open")
            val resolved = spec.withUri(entry.address.toUri())
            return if (entry.origin != null) addon(resolved, entry) else resolved.withAdditionalHeaders(entry.headers)
        }
        if (entry?.origin != null) return addon(spec, entry)
        return spec.withAdditionalHeaders(entry?.headers ?: mapOf(USER_AGENT to defaultAgent))
    }

    /**
     * An addon stream's request (ADDON-FR-95), manifest or segment: marked for the addon transport,
     * with the stream's headers only when it goes to the stream's own origin.
     */
    private fun addon(spec: DataSpec, entry: Entry): DataSpec {
        val sameOrigin = spec.uri.toString().toHttpUrlOrNull()?.let(AddonTransport::origin) == entry.origin
        val headers = buildMap {
            put(AddonTransport.MARKER, "1")
            put(AddonTransport.STREAM_HEADERS, entry.headers.keys.joinToString(","))
            put(USER_AGENT, defaultAgent)
            if (sameOrigin) putAll(entry.headers)
        }
        return spec.withAdditionalHeaders(headers)
    }

    private fun headers(stream: ResolvedStream): Map<String, String> = stream.addonHeaders ?: buildMap {
        // A playlist's User-Agent wins under any casing (PLAY-FR-17).
        put(USER_AGENT, stream.userAgent?.takeIf { it.isNotBlank() } ?: defaultAgent)
        stream.referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
    }

    companion object {
        const val SCHEME: String = "sohva"
        private const val USER_AGENT = "User-Agent"

        fun placeholder(key: String): Uri = Uri.Builder().scheme(SCHEME).authority("channel").appendPath(key).build()

        /** The MIME type from the real address: the extensionless placeholder hides it (PLAY-FR-15). */
        fun mimeType(address: String): String? = when (StreamContainer.of(address)) {
            StreamContainer.HLS -> MimeTypes.APPLICATION_M3U8
            StreamContainer.DASH -> MimeTypes.APPLICATION_MPD
            StreamContainer.SMOOTH -> MimeTypes.APPLICATION_SS
            StreamContainer.PROGRESSIVE -> null
        }
    }
}
