package com.sohva.tv.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.sohva.tv.core.data.prefs.LocaleStore
import java.util.Locale

/**
 * The interface language (spec 01 SHELL-FR-03, spec 74). From Android 13 the platform's
 * per-app language holds it and applies it before any text resolves; below 13 the app wraps the
 * activity's base context from a small synchronous preferences file.
 */
object AppLocales {
    /** For `attachBaseContext` below Android 13: one small file read, by design on the main thread. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = LocaleStore(base).languageTag() ?: return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /** Sets the language (null = the TV's own) and recreates the activity so every text changes. */
    fun set(activity: Activity, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The platform recreates the activity itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            LocaleStore(activity).setLanguageTag(tag)
            activity.recreate()
        }
    }
}
