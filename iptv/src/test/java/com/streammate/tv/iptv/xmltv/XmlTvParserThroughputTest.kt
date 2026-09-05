package com.streammate.tv.iptv.xmltv

import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser's share of a guide import, on a feed shaped like a provider's:
 * many channels, a week of half-hour programmes, descriptions on most.
 *
 * The bound is generous on purpose; the number that matters is the one this
 * test prints, which is compared by hand across changes.
 */
class XmlTvParserThroughputTest {
    @Test
    fun aWeekOfProgrammesParsesInSeconds() {
        val channels = 400
        val perChannel = 7 * 48
        val feed = syntheticFeed(channels, perChannel)
        val bytes = feed.toByteArray(Charsets.UTF_8)
        val parser = XmlTvParser()

        // Warm the JIT once, then measure.
        parser.records(ByteArrayInputStream(bytes)).count()
        val started = System.nanoTime()
        val count = parser.records(ByteArrayInputStream(bytes)).count()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertEquals(channels + channels * perChannel, count)
        println(
            "XmlTvParserThroughput: ${bytes.size / 1_000_000} MB, $count records, " +
                "$elapsedMillis ms, ${count * 1000L / elapsedMillis.coerceAtLeast(1)} records/s",
        )
        assertTrue("parsing took $elapsedMillis ms", elapsedMillis < 60_000)
    }

    private fun syntheticFeed(channels: Int, perChannel: Int): String = buildString {
        val start = LocalDateTime.of(2026, 9, 1, 0, 0)
        val format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>")
        repeat(channels) { channel ->
            appendLine("<channel id=\"ch$channel.fi\"><display-name>Channel $channel</display-name></channel>")
        }
        repeat(channels) { channel ->
            repeat(perChannel) { index ->
                val from = start.plusMinutes(index * 30L)
                append("<programme channel=\"ch$channel.fi\" start=\"").append(from.format(format))
                append(" +0300\" stop=\"").append(from.plusMinutes(30).format(format)).append(" +0300\">")
                append("<title>Programme $index on $channel</title>")
                if (index % 3 != 0) append("<sub-title>Episode ${index % 20}</sub-title>")
                append("<desc>A description long enough to look like a real listing, ")
                append("with a sentence or two about what happens in the episode.</desc>")
                append("<category>Entertainment</category>")
                appendLine("</programme>")
            }
        }
        appendLine("</tv>")
    }
}
