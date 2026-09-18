package com.streammate.tv.matching

import com.streammate.tv.core.model.TodayEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/** Provider wall-clock labels are not necessarily in the viewer's display timezone. */
internal class ChannelNameSchedule private constructor(
    private val minute: Int?,
    private val zone: ZoneId?,
    private val date: ChannelDate?,
) {
    fun startOffsetMinutes(event: TodayEvent): Long? {
        val startMinute = minute ?: return null
        // A provider clock cannot establish a broadcast instant without a zone.
        // Never invent one from the viewer's settings, AM/PM, language, country
        // tags or broadcaster name, whether or not the channel includes a date.
        val explicitZone = zone ?: return null
        val eventDate = Instant.ofEpochMilli(event.startEpochMillis).atZone(explicitZone).toLocalDate()
        val dates = date?.near(eventDate) ?: (-1L..1L).map(eventDate::plusDays)
        return dates.map { day ->
            val start = day.atTime(LocalTime.of(startMinute / 60, startMinute % 60))
                .atZone(explicitZone).toInstant().toEpochMilli()
            (start - event.startEpochMillis) / 60_000L
        }.minByOrNull(::abs)
    }

    fun dateSupportsMatch(event: TodayEvent, startOffset: Long?): Boolean {
        val explicitDate = date ?: return true
        // A dated, zoned instant is compared directly, including a programme
        // starting just before midnight for a match just after midnight.
        if (zone != null && startOffset != null) return true
        // Without a provider zone, a same-UTC-day label is supporting evidence.
        // A different day remains uncertain; never guess a zone to promote it.
        val eventDate = Instant.ofEpochMilli(event.startEpochMillis).atZone(zone ?: ZoneOffset.UTC).toLocalDate()
        return explicitDate.matches(eventDate)
    }

    private data class ChannelDate(val month: Int, val day: Int, val year: Int?, val ambiguous: Boolean = false) {
        fun matches(date: LocalDate) = !ambiguous && date.monthValue == month && date.dayOfMonth == day &&
            (year == null || year == date.year)

        fun near(date: LocalDate): List<LocalDate> = if (ambiguous) emptyList() else
            (year?.let(::listOf) ?: listOf(date.year - 1, date.year, date.year + 1))
                .mapNotNull { runCatching { LocalDate.of(it, month, day) }.getOrNull() }
    }

    companion object {
        fun parse(name: String): ChannelNameSchedule {
            val time = timePattern.find(name)
            val hour = time?.groupValues?.get(1)?.toInt()
            val meridiem = time?.groupValues?.get(3).orEmpty().uppercase(Locale.ROOT)
            val clockHour = when {
                hour == null -> null
                meridiem.isEmpty() -> hour
                hour !in 1..12 -> null
                else -> hour % 12 + if (meridiem == "P") 12 else 0
            }
            return ChannelNameSchedule(
                minute = clockHour?.let { it * 60 + requireNotNull(time).groupValues[2].toInt() },
                zone = time?.groupValues?.get(4)?.uppercase(Locale.ROOT)?.let(::parseZone),
                date = parseDate(name),
            )
        }

        private fun parseZone(value: String): ZoneId? = when (value) {
            "CET" -> ZoneOffset.ofHours(1)
            "CEST", "EET" -> ZoneOffset.ofHours(2)
            "EEST" -> ZoneOffset.ofHours(3)
            "UTC", "GMT" -> ZoneOffset.UTC
            else -> if (value.startsWith("UTC") || value.startsWith("GMT")) {
                runCatching { ZoneOffset.of(value.substring(3)) }.getOrNull()
            } else null
        }

        private fun parseDate(name: String): ChannelDate? {
            isoDatePattern.find(name)?.let { match ->
                return ChannelDate(match.groupValues[2].toInt(), match.groupValues[3].toInt(), match.groupValues[1].toInt())
            }
            monthFirstPattern.find(name)?.let { match ->
                return ChannelDate(monthNumber(match.groupValues[1]), match.groupValues[2].toInt(), match.groupValues[3].toIntOrNull())
            }
            dayFirstPattern.find(name)?.let { match ->
                return ChannelDate(monthNumber(match.groupValues[2]), match.groupValues[1].toInt(), match.groupValues[3].toIntOrNull())
            }
            numericDatePattern.find(name)?.let { match ->
                val first = match.groupValues[1].toInt()
                val second = match.groupValues[2].toInt()
                val year = match.groupValues[3].toIntOrNull()
                // 18/9 and 9/18 are unambiguous. A country or language tag is
                // not evidence that 9/10 means September 10 or October 9.
                return if (first in 1..12 && second > 12) {
                    ChannelDate(first, second, year)
                } else {
                    ChannelDate(second, first, year, ambiguous = first in 1..12 && second in 1..12 && first != second)
                }
            }
            return null
        }

        private fun monthNumber(name: String) = months.indexOf(name.take(3).lowercase(Locale.ROOT)) + 1
        private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        private const val MONTH = "(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
        private val monthFirstPattern = Regex("""\b$MONTH\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+((?:19|20)\d{2}))?\b""", RegexOption.IGNORE_CASE)
        private val dayFirstPattern = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+$MONTH\.?(?:,?\s+((?:19|20)\d{2}))?\b""", RegexOption.IGNORE_CASE)
        private val isoDatePattern = Regex("""(?<!\d)((?:19|20)\d{2})-(\d{2})-(\d{2})(?!\d)""")
        private val numericDatePattern = Regex("""(?<![\w/])(\d{1,2})/(\d{1,2})(?:/((?:19|20)\d{2}))?(?![\w/])""")
        private val timePattern = Regex("""(?<![\d.])([01]?\d|2[0-3])[:.]([0-5]\d)(?![\d.])\s*(?:([AP])\.?\s*M\.?)?\s*((?:UTC|GMT)(?:[+-]\d{1,2}(?::?\d{2})?)?|CEST|CET|EEST|EET)?\b""", RegexOption.IGNORE_CASE)
    }
}
