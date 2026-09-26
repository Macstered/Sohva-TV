package com.sohva.tv.core.model.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Times as the guide, Home, the player and Search write them, in the chosen time zone (spec 20
 * GUIDE-FR-110..111, spec 30 PLAY-FR-42) and one [TimeStyle] everywhere (spec 74 L10N-FR-41, Q-01:
 * beta 23 mixed a fixed `HH.mm` with `HH:mm` and the TV's 12-hour clock). Ranges join with an en dash.
 * Formatting happens once per data change, off the main thread where it can.
 */
class TimeLabels(val zone: ZoneId, style: TimeStyle) {
    private val clock = DateTimeFormatter.ofPattern(style.clockPattern, style.locale).withZone(zone)
    private val day = DateTimeFormatter.ofPattern(style.dayPattern, style.locale).withZone(zone)

    fun guideTime(epochMillis: Long): String = clock.format(Instant.ofEpochMilli(epochMillis))

    /** "20.00–21.30" in Finnish, "20:00–21:30" in English. */
    fun guideRange(start: Long, stop: Long): String = guideTime(start) + "–" + guideTime(stop)

    fun playerTime(epochMillis: Long): String = guideTime(epochMillis)

    /** "to 24.9." in Finnish, "Thu, 9/24" in US English (GUIDE-FR-46, the locale's own order). */
    fun dayLabel(epochMillis: Long): String = day.format(Instant.ofEpochMilli(epochMillis))

    /** The relative day of [start] seen from [now] by calendar day in the zone; null beyond ±1 day. */
    fun relativeDay(start: Long, now: Long): RelativeDay? {
        val a = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val b = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (a.toEpochDay() - b.toEpochDay()) {
            0L -> RelativeDay.TODAY
            1L -> RelativeDay.TOMORROW
            -1L -> RelativeDay.YESTERDAY
            else -> null
        }
    }

    enum class RelativeDay { TODAY, TOMORROW, YESTERDAY }

    companion object {
        /** The chosen zone, or the TV's while none is chosen or the id is invalid (GUIDE-FR-110). */
        fun zoneOf(id: String?): ZoneId =
            id?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    }
}
