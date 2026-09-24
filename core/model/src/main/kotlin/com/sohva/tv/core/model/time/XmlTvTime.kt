package com.sohva.tv.core.model.time

/**
 * XMLTV timestamps (spec 10 SRC-FR-65): `yyyyMMddHHmm` or `yyyyMMddHHmmss`, then a mandatory
 * offset `±hhmm` or `Z`, optionally separated by spaces. A date without a time or a time without
 * an offset is rejected (null), and the programme is skipped rather than the guide. Hand-parsed:
 * the importer calls it twice per programme, hundreds of thousands of times.
 */
object XmlTvTime {
    fun parseMillis(raw: String): Long? {
        val text = raw.trim()
        var i = 0
        while (i < text.length && text[i].isDigit()) i++
        val digits = i
        if (digits != 12 && digits != 14) return null
        while (i < text.length && text[i] == ' ') i++
        if (i >= text.length) return null
        val offsetMinutes = when (text[i]) {
            'Z' -> if (i == text.length - 1) 0 else return null
            '+', '-' -> {
                if (text.length - i != 5 || !text.substring(i + 1).all { it.isDigit() }) return null
                val sign = if (text[i] == '-') -1 else 1
                sign * (num(text, i + 1, 2) * 60 + num(text, i + 3, 2))
            }
            else -> return null
        }
        val year = num(text, 0, 4)
        val month = num(text, 4, 2)
        val day = num(text, 6, 2)
        val hour = num(text, 8, 2)
        val minute = num(text, 10, 2)
        val second = if (digits == 14) num(text, 12, 2) else 0
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 59) return null
        val epochDay = daysFromCivil(year, month, day)
        return ((epochDay * 86_400L + hour * 3_600L + minute * 60L + second) - offsetMinutes * 60L) * 1_000L
    }

    private fun num(text: String, from: Int, count: Int): Int {
        var v = 0
        for (k in from until from + count) v = v * 10 + (text[k] - '0')
        return v
    }

    /** Days since 1970-01-01 for a proleptic Gregorian date (Howard Hinnant's algorithm). */
    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }
}
