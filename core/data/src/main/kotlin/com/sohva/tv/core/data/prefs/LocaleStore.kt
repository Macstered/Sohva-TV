package com.sohva.tv.core.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * The interface language below Android 13, read synchronously in `attachBaseContext` before any
 * text is resolved (spec 01 FR-03). From Android 13 the platform's per-app language holds it.
 *
 * Keeps beta 23's file and key, so the chosen language applies on the very first start after the
 * update: the one-time importer runs only after the first frame, too late for this.
 */
class LocaleStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** A BCP 47 tag such as `fi`, or null for the system language. */
    fun languageTag(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    /** Synchronous: the activity is recreated right after, and must read the new value. */
    fun setLanguageTag(tag: String?): Boolean = prefs.edit().apply {
        if (tag.isNullOrBlank()) remove(KEY) else putString(KEY, tag)
    }.commit()

    companion object {
        const val FILE_NAME: String = "streammate_locale"
        const val KEY: String = "language_tag"
    }
}
