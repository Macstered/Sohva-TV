package com.sohva.tv.core.model.time

import java.util.Locale

/**
 * How times read on screen (spec 74 L10N-FR-41, Q-01): the interface language's own clock, with its
 * separator and the TV's 12/24-hour setting and two-digit hours in 24-hour mode ("21.40" in
 * Finnish, "21:40" or "9:40 PM" in English), and its short weekday with day and month. The patterns
 * come from Android's best patterns for the locale (ui/design `TimeStyles`); built once per locale
 * and setting, never per call.
 */
data class TimeStyle(val locale: Locale, val clockPattern: String, val dayPattern: String) {
    companion object {
        /** A 24-hour style where Android's patterns are not at hand (JVM tests, previews). */
        fun fixed24(locale: Locale): TimeStyle = TimeStyle(locale, "HH:mm", "EEE d.M.")
    }
}
