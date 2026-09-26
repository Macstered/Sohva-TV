package com.sohva.tv.ui.design.text

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import com.sohva.tv.core.model.time.TimeStyle
import java.util.Locale

/** The [TimeStyle] of the interface language and the TV's 12/24-hour setting (spec 74 L10N-FR-41). */
object TimeStyles {
    fun of(context: Context, locale: Locale): TimeStyle = TimeStyle(
        locale = locale,
        clockPattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "HHmm" else "hmma"),
        dayPattern = DateFormat.getBestDateTimePattern(locale, "EEEdM"),
    )
}

/** The style for this composition's language; rebuilt only when the language changes. */
@Composable
fun rememberTimeStyle(): TimeStyle {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val context = LocalContext.current
    return remember(locale) { TimeStyles.of(context, locale) }
}
