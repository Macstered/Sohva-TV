package com.sohva.tv.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
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
    /** The interface languages (spec 74, plus the owner's French extension), in picker order. */
    val SUPPORTED: List<String> = listOf("en", "fi", "es", "pt", "de", "sv", "it", "fr")

    /**
     * The chosen interface language, or null for System default (L10N-FR-02): the platform's per-app
     * language from Android 13, the small file below. Only supported languages count: a platform
     * choice of `pt-BR` reads as `pt`, anything else as none.
     */
    fun chosen(context: Context): String? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            LocaleStore(context).languageTag()
        }
        return tag?.let { Locale.forLanguageTag(it).language }?.takeIf { it in SUPPORTED }
    }

    /**
     * The activity's context below Android 13 (L10N-FR-03, -06): wrapped in the chosen language, and
     * the JVM default locale set to it, so formatting by default follows the interface; on System
     * default the JVM default goes back to the TV's own language, not the one chosen before.
     */
    fun attach(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = chosen(base)
        Locale.setDefault(if (tag == null) systemLocale() else Locale.forLanguageTag(tag))
        return wrap(base)
    }

    /**
     * Texts resolved outside an activity (L10N-FR-05): toasts, notifications, titles made in the
     * background. Below Android 13 the application stays in the TV's language, so they come from a
     * context in the chosen one, made again only when the choice changes.
     */
    fun texts(app: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return app
        val tag = chosen(app)
        cached?.let { (cachedTag, context) -> if (cachedTag == tag) return context }
        return wrap(app).also { cached = tag to it }
    }

    @Volatile private var cached: Pair<String?, Context>? = null

    private fun systemLocale(): Locale = Resources.getSystem().configuration.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) it.locales[0] else @Suppress("DEPRECATION") it.locale
    }

    /** For `attachBaseContext` below Android 13: one small file read, by design on the main thread. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = chosen(base) ?: return base
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
