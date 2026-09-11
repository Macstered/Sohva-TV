package com.streammate.tv.addons

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.streammate.tv.R
import com.streammate.tv.app.primaryLocale

/** The Activity's localized resources, also safe to use from event/coroutine callbacks.
 * Never use the application context or translate provider payloads/protocol identifiers. */
internal class AddonStrings(private val resources: Resources) {
    val locale get() = resources.configuration.primaryLocale()
    operator fun invoke(@StringRes id: Int, vararg arguments: Any): String = resources.getString(id, *arguments)
    fun count(@PluralsRes id: Int, count: Int, vararg arguments: Any): String =
        resources.getQuantityString(id, count, count, *arguments)

    fun mediaType(type: String): String = when (type) {
        "movie" -> invoke(R.string.addon_ui_movie)
        "series" -> invoke(R.string.home_series)
        else -> type
    }

    fun filterName(name: String): String = when (name) {
        "genre" -> invoke(R.string.addon_ui_genre)
        "year" -> invoke(R.string.addon_ui_year)
        "language" -> invoke(com.streammate.tv.iptv.R.string.metadata_language_label)
        "search" -> invoke(R.string.home_search)
        else -> name.replaceFirstChar { it.titlecase(locale) }
    }

    fun languageName(value: String): String =
        addonSubtitleLanguageName(value, locale, invoke(R.string.addon_ui_unknown_language))
}

@Composable
internal fun addonStrings(): AddonStrings {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { AddonStrings(context.resources) }
}
