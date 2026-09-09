package com.streammate.tv.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Logos a phone sent for a channel, kept as small PNG files in the app's own
 * storage and named from a channel's preference by a file address, which the
 * image loader reads like any other.
 *
 * The phone shrinks a picture before sending it and the TV bounds it again
 * here, so a photo chosen by mistake costs at most [MAX_SIDE] pixels a side.
 * A new logo for a channel replaces the old file under a new name, so a
 * cached copy of the old one is never shown for the new address.
 */
class ChannelLogoStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "channel-logos")

    /** Decodes [image], bounds it, writes it for [channelId] and returns the address to keep. */
    fun save(channelId: String, image: ByteArray): String {
        require(image.isNotEmpty() && image.size <= MAX_IMAGE_BYTES) { "image size" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("not an image")
        val scale = min(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        directory.mkdirs()
        val prefix = fileNamePrefix(channelId)
        val previous = directory.listFiles()?.filter { it.name.startsWith(prefix) }.orEmpty()
        previous.forEach { it.delete() }
        // A name no earlier file of this channel had, whatever the clock says:
        // the address is what image caches key on, so a replacement within the
        // same millisecond must not come back under the old one.
        val taken = previous.mapTo(HashSet()) { it.name }
        var stamp = System.currentTimeMillis()
        while ("$prefix-$stamp.png" in taken) stamp++
        val file = File(directory, "$prefix-$stamp.png")
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        return addressOf(file)
    }

    /** Whether [url] names a file this store wrote on this device. */
    fun isLocal(url: String?): Boolean = fileOf(url) != null

    /** The bytes behind a local address, for a backup; null for any other address or a missing file. */
    fun read(url: String?): ByteArray? = fileOf(url)?.takeIf(File::isFile)?.readBytes()

    /**
     * [url] as it should be kept after a restore: an address elsewhere as it
     * is, a file of this store only while it exists, and a file address from
     * another device dropped rather than kept as a picture that never loads.
     */
    fun existingOrNull(url: String?): String? = when {
        url.isNullOrBlank() -> null
        !url.startsWith("file:") -> url
        fileOf(url)?.isFile == true -> url
        else -> null
    }

    /** Removes the file behind [url] when this store wrote it. */
    fun delete(url: String?) {
        fileOf(url)?.delete()
    }

    private fun fileOf(url: String?): File? {
        if (url.isNullOrBlank() || !url.startsWith("file:")) return null
        val file = runCatching { File(URI(url)) }.getOrNull() ?: return null
        return file.takeIf { it.parentFile?.absolutePath == directory.absolutePath }
    }

    private fun addressOf(file: File): String = file.toURI().toString()

    private fun fileNamePrefix(channelId: String): String =
        MessageDigest.getInstance("SHA-256").digest(channelId.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }

    companion object {
        /** The longest side a stored logo keeps; the guide draws them far smaller. */
        const val MAX_SIDE = 256
        /** What the phone may send after shrinking; a decoded photo of any size is bounded by [MAX_SIDE]. */
        const val MAX_IMAGE_BYTES = 2_000_000
    }
}
