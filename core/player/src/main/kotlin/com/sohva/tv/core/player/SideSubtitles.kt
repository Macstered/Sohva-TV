package com.sohva.tv.core.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * The one side-loaded subtitle (spec 50 ADDON-FR-101): kept in memory only, never a temp file,
 * reached by the player as `sohvasub://<key>`. Bounded to one entry: a new subtitle, Off or the
 * end of the addon playback replaces or drops it (performance rule 4).
 */
object SideSubtitles {
    const val SCHEME: String = "sohvasub"

    /** The id the side-loaded track carries, to find and select it among the tracks. */
    const val TRACK_ID: String = "sohva-addon-subtitle"

    private class Entry(val key: String, val bytes: ByteArray)

    @Volatile
    private var current: Entry? = null
    private var counter = 0L

    /** Stores [bytes] as the current subtitle and returns its key. */
    @Synchronized
    fun put(bytes: ByteArray): String {
        val key = "s${++counter}"
        current = Entry(key, bytes)
        return key
    }

    fun clear() {
        current = null
    }

    fun uri(key: String): Uri = Uri.Builder().scheme(SCHEME).authority(key).build()

    internal fun bytes(uri: Uri): ByteArray? = current?.takeIf { it.key == uri.authority }?.bytes
}

/** Serves `sohvasub://` from memory and everything else from [upstream]. */
@OptIn(UnstableApi::class)
internal class SideSubtitleDataSource(private val upstream: DataSource) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme == SideSubtitles.SCHEME) {
            ByteArrayDataSource(SideSubtitles.bytes(dataSpec.uri) ?: throw IOException("The subtitle is no longer loaded"))
        } else {
            upstream
        }
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active?.read(buffer, offset, length) ?: throw IOException("Not open")

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = active
        active = null
        source?.close()
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = SideSubtitleDataSource(upstream.createDataSource())
    }
}
