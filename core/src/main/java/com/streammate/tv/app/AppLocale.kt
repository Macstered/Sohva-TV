package com.streammate.tv.app

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The language the interface is drawn in, independent of the system language.
 *
 * Two storage paths, because the platform grew its own support half way through
 * the range of versions this app runs on:
 *
 * - API 33 and up, the framework owns the setting. [LocaleManager] persists it,
 *   applies it to every activity, and surfaces it in Android's own per-app
 *   language screen, so writing a second copy here would only let the two drift
 *   apart.
 * - Below 33 there is nothing to delegate to, so the tag is kept in a small
 *   dedicated preferences file and applied in `attachBaseContext`.
 *
 * That file is deliberately not part of [AppPreferences]. Preferences live in
 * DataStore, which is only readable from a coroutine, and the locale has to be
 * resolved synchronously before the first activity is built - blocking on
 * DataStore at that point is how cold starts turn into ANRs.
 */
object AppLocale {

    /** Languages the interface is actually translated into. */
    val SUPPORTED_TAGS: List<String> = listOf("en", "fi")

    /**
     * The chosen language tag, or `null` to follow the system.
     */
    fun stored(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.takeUnless(LocaleList::isEmpty)
                ?.get(0)
                ?.language
                ?.takeIf { it in SUPPORTED_TAGS }
        } else {
            preferences(context).getString(KEY_LANGUAGE_TAG, null)
                ?.takeIf { it in SUPPORTED_TAGS }
        }

    /**
     * Persists [tag] and applies it. Returns true when the caller still has to
     * recreate itself for the change to show, which only the pre-33 path needs -
     * the framework restarts activities by itself.
     */
    fun apply(context: Context, tag: String?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            return false
        }
        preferences(context).edit().apply {
            if (tag == null) remove(KEY_LANGUAGE_TAG) else putString(KEY_LANGUAGE_TAG, tag)
        }.apply()
        return true
    }

    /**
     * Wraps [base] in the stored language, for `Activity.attachBaseContext`.
     *
     * A no-op from API 33 up, where the framework has already applied the
     * locale to the context being handed in.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = stored(base) ?: return base
        val configuration = Configuration(base.resources.configuration)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        } else {
            // LocaleList only arrived in N; on M there is a single locale.
            @Suppress("DEPRECATION")
            configuration.setLocale(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "streammate_locale"
    private const val KEY_LANGUAGE_TAG = "language_tag"
}
