package com.streammate.tv.iptv.xmltv

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XmlTvTimestampParserTest {
    @Test
    fun `converts explicit positive and negative offsets to UTC`() {
        assertEquals(
            Instant.parse("2026-01-15T16:30:00Z"),
            XmlTvTimestampParser.parse("20260115183000 +0200"),
        )
        assertEquals(
            Instant.parse("2026-08-22T11:15:00Z"),
            XmlTvTimestampParser.parse("202608220715 -0400"),
        )
    }

    @Test
    fun `accepts the XMLTV UTC marker`() {
        assertEquals(
            Instant.parse("2026-08-22T19:45:30Z"),
            XmlTvTimestampParser.parse("20260822194530 Z"),
        )
    }

    @Test
    fun `rejects timestamps without a timezone`() {
        assertThrows(IllegalArgumentException::class.java) {
            XmlTvTimestampParser.parse("20260822194530")
        }
    }
}
