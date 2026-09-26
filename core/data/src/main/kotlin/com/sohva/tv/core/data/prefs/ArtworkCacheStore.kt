package com.sohva.tv.core.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.sohva.tv.core.model.settings.ArtworkCacheLimit

/**
 * The image cache limit (spec 70 SET-FR-80) in beta 23's small file and key: read synchronously
 * when the image loader is built, off the main thread, so it is not in DataStore (spec 70 §6.2).
 */
class ArtworkCacheStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    fun limit(): ArtworkCacheLimit = ArtworkCacheLimit.fromStored(prefs.getString(KEY, null))

    fun setLimit(limit: ArtworkCacheLimit) {
        prefs.edit().putString(KEY, limit.name).apply()
    }

    companion object {
        const val FILE_NAME: String = "streammate_artwork_cache"
        const val KEY: String = "limit"
    }
}
