package com.sohva.tv.core.data.prefs

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * The interface language below Android 13, read synchronously in `attachBaseContext` before any
 * text is resolved (spec 01 FR-03). From Android 13 the platform's per-app language holds it.
 *
 * Keeps beta 23's file and key, so the chosen language applies on the very first start after the
 * update: the one-time importer runs only after the first frame, too late for this.
 */
class LocaleStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    /**
     * A BCP 47 tag such as `fi`, or null for the system language. When no language was ever
     * chosen the file does not exist, and one `stat` replaces opening preferences, which created
     * the directory and waited for a loader thread: about 74 ms of a cold start on the stand-in.
     */
    fun languageTag(): String? {
        val file = File(File(context.applicationInfo.dataDir, "shared_prefs"), "$FILE_NAME.xml")
        if (!file.exists()) return null
        return prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }
    }

    /** Synchronous: the activity is recreated right after, and must read the new value. */
    fun setLanguageTag(tag: String?): Boolean = prefs.edit().apply {
        if (tag.isNullOrBlank()) remove(KEY) else putString(KEY, tag)
    }.commit()

    companion object {
        const val FILE_NAME: String = "streammate_locale"
        const val KEY: String = "language_tag"
    }
}
