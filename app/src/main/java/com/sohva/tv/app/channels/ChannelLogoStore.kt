package com.sohva.tv.app.channels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Phone-sent channel logos (spec 21 CHAN-FR-42): decoded with a power-of-two sample size while the
 * longer side is over twice 256 px, scaled to at most 256 px, and kept as a PNG in the app's own
 * storage under a name that never repeats (image caches key on the address). Earlier files of the
 * channel go first. Runs on the phone page's thread, never the main thread.
 */
class ChannelLogoStore(context: Context) {
    private val dir = File(context.filesDir, DIR)

    /** The stored logo's `file:` address, or null when the bytes are not a picture. */
    fun save(channelKey: String, bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2 * EDGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = minOf(1f, EDGE.toFloat() / max(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) {
            decoded.scale((decoded.width * scale).roundToInt().coerceAtLeast(1), (decoded.height * scale).roundToInt().coerceAtLeast(1))
        } else {
            decoded
        }
        dir.mkdirs()
        val prefix = prefix(channelKey)
        delete(channelKey)
        val file = File(dir, "$prefix-${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (bitmap !== decoded) bitmap.recycle()
        decoded.recycle()
        return file.toURI().toString()
    }

    /** Deletes the channel's phone-sent logos (Reset, a new picture; spec 21 §10 rebuild rule). */
    /**
     * A logo's picture bytes when [url] is a file this store wrote (spec 71 BACKUP-FR-07 step 5),
     * so a backup carries phone logos to another TV; any other address has none.
     */
    fun read(url: String): ByteArray? {
        val file = fileOf(url) ?: return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    /** [url] when it is not a file, or a file of this store that exists (spec 71 BACKUP-FR-18 step 6). */
    fun kept(url: String): String? = when {
        !url.startsWith("file:") -> url
        fileOf(url)?.isFile == true -> url
        else -> null
    }

    private fun fileOf(url: String): File? {
        if (!url.startsWith("file:")) return null
        val file = runCatching { File(java.net.URI(url)) }.getOrNull() ?: return null
        return file.takeIf { it.parentFile?.canonicalPath == dir.canonicalPath }
    }

    fun delete(channelKey: String) {
        val prefix = prefix(channelKey) + "-"
        dir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    /** The first 8 bytes of SHA-256(channel key) in hex. */
    private fun prefix(channelKey: String): String =
        MessageDigest.getInstance("SHA-256").digest(channelKey.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

    companion object {
        const val DIR: String = "channel-logos"
        const val EDGE: Int = 256
        const val MAX_BYTES: Int = 2_000_000
    }
}
