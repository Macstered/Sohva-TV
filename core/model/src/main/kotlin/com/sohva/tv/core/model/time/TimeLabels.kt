package com.sohva.tv.core.model.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Times as the guide and player write them, in the chosen time zone (spec 20 GUIDE-FR-110..111,
 * spec 30 PLAY-FR-42): the guide uses `HH.mm` in every language with an en dash for ranges, the
 * player `HH:mm`. Formatting happens once per data change, off the main thread where it can.
 */
class TimeLabels(val zone: ZoneId, locale: Locale) {
    private val guideClock = DateTimeFormatter.ofPattern("HH.mm", Locale.ROOT).withZone(zone)
    private val playerClock = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone)
    private val day = DateTimeFormatter.ofPattern("EEE d.M.", locale).withZone(zone)

    fun guideTime(epochMillis: Long): String = guideClock.format(Instant.ofEpochMilli(epochMillis))

    /** "20.00–21.30". */
    fun guideRange(start: Long, stop: Long): String = guideTime(start) + "–" + guideTime(stop)

    fun playerTime(epochMillis: Long): String = playerClock.format(Instant.ofEpochMilli(epochMillis))

    /** "Thu 24.9." (GUIDE-FR-46). */
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
