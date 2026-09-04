package com.streammate.tv.iptv.xmltv

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object XmlTvTimestampParser {
    private val valuePattern = Regex("""^(\d{8}(?:\d{4}(?:\d{2})?)?)\s*([+-]\d{4}|Z)$""")
    private val minuteFormatter = DateTimeFormatter.ofPattern("uuuuMMddHHmm", Locale.ROOT)
    private val secondFormatter = DateTimeFormatter.ofPattern("uuuuMMddHHmmss", Locale.ROOT)

    fun parse(value: String): Instant {
        val match = valuePattern.matchEntire(value.trim())
            ?: throw IllegalArgumentException("XMLTV timestamp must include an explicit numeric offset")
        val dateTime = match.groupValues[1]
        require(dateTime.length >= 12) { "XMLTV timestamp must include hours and minutes" }
        val localDateTime = LocalDateTime.parse(
            dateTime,
            if (dateTime.length == 14) secondFormatter else minuteFormatter,
        )
        val rawOffset = match.groupValues[2]
        val offset = if (rawOffset == "Z") {
            ZoneOffset.UTC
        } else {
            ZoneOffset.of("${rawOffset.take(3)}:${rawOffset.takeLast(2)}")
        }
        return localDateTime.toInstant(offset)
    }
}
