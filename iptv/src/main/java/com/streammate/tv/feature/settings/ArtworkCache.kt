package com.streammate.tv.feature.settings

import android.content.Context
import coil3.SingletonImageLoader
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the artwork cache is currently costing, and how to reclaim it.
 *
 * Posters, channel logos and backdrops accumulate quietly: a library of a few
 * thousand titles will fill any ceiling it is given, and on a set-top box that
 * competes with recordings and other apps for the same modest disk.
 */
object ArtworkCache {

    suspend fun usageBytes(context: Context): Long = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, ARTWORK_DIRECTORY)
        if (!directory.exists()) {
            0L
        } else {
            directory.walkBottomUp().filter(File::isFile).sumOf(File::length)
        }
    }

    /**
     * Empties both caches. The memory copy goes too - clearing only the disk
     * would leave the screen showing artwork the viewer has just asked to be
     * rid of, and report a size that does not match what is on screen.
     */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context)
        loader.diskCache?.clear()
        loader.memoryCache?.clear()
    }

    /** A size a person can read, rather than a count of bytes. */
    fun formatBytes(bytes: Long): String = when {
        bytes >= MEGABYTE -> String.format(Locale.US, "%.0f MB", bytes / MEGABYTE.toDouble())
        bytes >= KILOBYTE -> String.format(Locale.US, "%.0f kB", bytes / KILOBYTE.toDouble())
        else -> String.format(Locale.US, "%d B", bytes)
    }

    private const val ARTWORK_DIRECTORY = "catalogue_artwork"
    private const val KILOBYTE = 1024L
    private const val MEGABYTE = 1024L * 1024L
}
