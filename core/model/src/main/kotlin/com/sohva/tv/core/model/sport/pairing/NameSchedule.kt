package com.sohva.tv.core.model.sport.pairing

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * A clock and a date read from a channel name (spec 60 SPORT-FR-108…112): "(18/9) 18:45",
 * "Sep 18 2:30 PM", "20:00 CET". No zone is ever guessed from the viewer, so an unzoned clock
 * never becomes an instant; a stated date can still support or contradict the kick-off day.
 */
class NameSchedule private constructor(private val time: LocalTime?, private val zone: ZoneOffset?, private val date: StatedDate?) {
    /** A date as written: [valid] false for "Feb 30" or "0/9"; [ambiguous] for "9/10". */
    private data class StatedDate(val year: Int?, val month: Int, val day: Int, val valid: Boolean, val ambiguous: Boolean)

    /**
     * Minutes from kick-off to the stated instant (SPORT-FR-111), only for a zoned clock: the
     * stated date (in its year, else the kick-off year −1…+1), else the kick-off day in that zone
     * −1…+1; the smallest difference wins. Null without a zone or with an unusable date.
     */
    fun offsetMinutes(kickOff: Long): Long? {
        val time = time ?: return null
        val zone = zone ?: return null
        val kickDate = Instant.ofEpochMilli(kickOff).atOffset(zone).toLocalDate()
        val days: List<LocalDate> = when (val d = date) {
            null -> listOf(kickDate.minusDays(1), kickDate, kickDate.plusDays(1))
            else -> {
                if (!d.valid || d.ambiguous) return null
                (d.year?.let { listOf(it) } ?: listOf(kickDate.year - 1, kickDate.year, kickDate.year + 1)).mapNotNull { y -> dateOrNull(y, d.month, d.day) }
            }
        }
        return days.map { (it.atTime(time).toInstant(zone).toEpochMilli() - kickOff) / MINUTE }.minByOrNull { abs(it) }
    }

    /**
     * A date must match the kick-off day in the stated zone, else UTC; a clock cannot excuse a
     * stale date. A broadcast within the two-hour scan window can bridge a midnight boundary.
     * Provider clocks only rank name matches (owner's 30 September correction to SPORT-FR-115).
     */
    fun dateSupports(kickOff: Long, offsetMinutes: Long?): Boolean {
        val d = date ?: return true
        if (!d.valid || d.ambiguous) return false
        if (offsetMinutes != null && abs(offsetMinutes) <= DATE_BOUNDARY_WINDOW) return true
        val day = Instant.ofEpochMilli(kickOff).atOffset(zone ?: ZoneOffset.UTC).toLocalDate()
        return d.month == day.monthValue && d.day == day.dayOfMonth && (d.year == null || d.year == day.year)
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val DATE_BOUNDARY_WINDOW = 120
        private const val MONTH = "(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
        private val TIME = Regex(
            "(?<![\\d.])([01]?\\d|2[0-3])[:.]([0-5]\\d)(?![\\d.])\\s*(?:([AP])\\.?\\s*M\\.?)?\\s*((?:UTC|GMT)(?:[+-]\\d{1,2}(?::?\\d{2})?)?|CEST|CET|EEST|EET)?\\b",
            RegexOption.IGNORE_CASE,
        )
        private val ISO = Regex("(?<!\\d)((?:19|20)\\d{2})-(\\d{2})-(\\d{2})(?!\\d)")
        // In "30 Sep 19:15", 19 is a clock hour, not the day in "Sep 19".
        private val MONTH_FIRST = Regex("\\b$MONTH\\.?\\s+(\\d{1,2})(?!\\d|[:.]\\d{2})(?:st|nd|rd|th)?(?:,?\\s+((?:19|20)\\d{2}))?\\b", RegexOption.IGNORE_CASE)
        private val DAY_FIRST = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+$MONTH\\.?(?:,?\\s+((?:19|20)\\d{2}))?\\b", RegexOption.IGNORE_CASE)
        private val NUMERIC = Regex("(?<![\\w/])(\\d{1,2})/(\\d{1,2})(?:/((?:19|20)\\d{2}))?(?![\\w/])")
        private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

        /** Reads [name] once per candidate; the result is compared with each game. */
        fun of(name: String): NameSchedule {
            val t = TIME.find(name)
            var time: LocalTime? = null
            var zone: ZoneOffset? = null
            if (t != null) {
                time = clock(t.groupValues[1].toInt(), t.groupValues[2].toInt(), t.groupValues[3])
                if (time != null) zone = zoneOf(t.groupValues[4])
            }
            return NameSchedule(time, zone, dateOf(name))
        }

        /** 12-hour clocks need an hour of 1–12: 12 AM is 0, 12 PM is 12 (SPORT-FR-108). */
        private fun clock(hour: Int, minute: Int, meridiem: String): LocalTime? {
            if (meridiem.isEmpty()) return LocalTime.of(hour, minute)
            if (hour !in 1..12) return null
            val pm = meridiem.equals("P", ignoreCase = true)
            val h = when {
                hour == 12 -> if (pm) 12 else 0
                pm -> hour + 12
                else -> hour
            }
            return LocalTime.of(h, minute)
        }

        /** SPORT-FR-109: fixed offsets; `UTC±h`, `±hh`, `±hhmm`, `±hh:mm` when valid (`+5:30` is not). */
        private fun zoneOf(text: String): ZoneOffset? {
            val upper = text.uppercase()
            return when (upper) {
                "" -> null
                "CET" -> ZoneOffset.ofHours(1)
                "CEST", "EET" -> ZoneOffset.ofHours(2)
                "EEST" -> ZoneOffset.ofHours(3)
                "UTC", "GMT" -> ZoneOffset.UTC
                else -> {
                    val rest = upper.substring(3)
                    val sign = if (rest[0] == '-') -1 else 1
                    val digits = rest.substring(1)
                    val (h, m) = when {
                        ':' in digits -> digits.split(':').let { (a, b) -> if (a.length != 2) return null else a.toInt() to b.toInt() }
                        digits.length <= 2 -> digits.toInt() to 0
                        digits.length == 4 -> digits.substring(0, 2).toInt() to digits.substring(2).toInt()
                        else -> return null
                    }
                    try {
                        ZoneOffset.ofHoursMinutes(sign * h, sign * m)
                    } catch (e: DateTimeException) {
                        null
                    }
                }
            }
        }

        /** SPORT-FR-110: the first form found of ISO, month-first, day-first, numeric. */
        private fun dateOf(name: String): StatedDate? {
            ISO.find(name)?.let { m ->
                val (y, mo, d) = m.destructured
                return stated(y.toInt(), mo.toInt(), d.toInt())
            }
            MONTH_FIRST.find(name)?.let { m -> return stated(m.groupValues[3].toIntOrNull(), month(m.groupValues[1]), m.groupValues[2].toInt()) }
            DAY_FIRST.find(name)?.let { m -> return stated(m.groupValues[3].toIntOrNull(), month(m.groupValues[2]), m.groupValues[1].toInt()) }
            NUMERIC.find(name)?.let { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                val year = m.groupValues[3].toIntOrNull()
                // US order only when it is the one reading that works (9/18); otherwise day first.
                return if (a in 1..12 && b > 12) {
                    stated(year, a, b)
                } else {
                    stated(year, b, a, ambiguous = a in 1..12 && b in 1..12 && a != b)
                }
            }
            return null
        }

        private fun month(name: String): Int = MONTHS.indexOf(name.take(3).lowercase()) + 1

        private fun stated(year: Int?, month: Int, day: Int, ambiguous: Boolean = false): StatedDate {
            // Valid in some year: 29 February is a real date.
            val valid = dateOrNull(year ?: LEAP_YEAR, month, day) != null
            return StatedDate(year, month, day, valid, ambiguous)
        }

        private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? = try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }

        private const val LEAP_YEAR = 2028
    }
}
