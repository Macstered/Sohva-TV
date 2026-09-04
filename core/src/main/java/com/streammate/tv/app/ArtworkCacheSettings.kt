package com.streammate.tv.app

import android.content.Context

/** How much room posters, logos and backdrops may take on disk. */
enum class ArtworkCacheLimit(val bytes: Long, val megabytes: Int) {
    SMALL(100L * 1024 * 1024, 100),
    MEDIUM(250L * 1024 * 1024, 250),
    LARGE(500L * 1024 * 1024, 500),
    ;

    companion object {
        val DEFAULT = MEDIUM

        fun fromStoredValue(value: String?): ArtworkCacheLimit =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * The artwork cache ceiling, stored where it can be read before the app is up.
 *
 * Coil's disk cache fixes its size when the image loader is built, which happens
 * in Application.onCreate - before a DataStore read can be awaited without
 * blocking the very first frame. So this lives in its own small preferences
 * file, read synchronously, exactly as the interface language does.
 *
 * A new ceiling therefore applies from the next start rather than immediately.
 * That is worth stating in the setting itself rather than hiding: the
 * alternative is tearing down a live image loader and its open cache while
 * screens are still drawing from it.
 */
object ArtworkCacheSettings {

    fun limit(context: Context): ArtworkCacheLimit =
        ArtworkCacheLimit.fromStoredValue(
            preferences(context).getString(KEY_LIMIT, null),
        )

    fun setLimit(context: Context, limit: ArtworkCacheLimit) {
        preferences(context).edit().putString(KEY_LIMIT, limit.name).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "streammate_artwork_cache"
    private const val KEY_LIMIT = "limit"
}
