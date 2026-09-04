package com.streammate.tv.iptv.xmltv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmlTvParserTest {
    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="liiga.1">
            <display-name lang="fi">Liiga HD</display-name>
            <icon src="https://img.example/liiga.png" />
          </channel>
          <programme channel="liiga.1" start="20260822190000 +0300" stop="20260822213000 +0300">
            <title lang="fi">Tappara - Ilves</title>
            <sub-title>Runkosarja</sub-title>
            <desc>Tampereen paikallisottelu</desc>
            <category>Sports</category>
            <category>Ice Hockey</category>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun `streams channel and programme records`() {
        val records = XmlTvParser().records(xml.stream()).toList()
        val channel = records[0] as XmlTvRecord.Channel
        val programme = records[1] as XmlTvRecord.Programme

        assertEquals("liiga.1", channel.id)
        assertEquals("Liiga HD", channel.displayName)
        assertEquals("https://img.example/liiga.png", channel.iconUrl)
        assertEquals("Tappara - Ilves", programme.title)
        assertEquals("Runkosarja", programme.subtitle)
        assertEquals(1_787_414_400_000L, programme.startEpochMillis)
        assertEquals(listOf("Sports", "Ice Hockey"), programme.categories)
    }

    @Test
    fun `detects and reads a gzip XMLTV stream`() {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(xml.toByteArray(Charsets.UTF_8)) }

        val records = CompressionAwareInputStream.wrap(
            ByteArrayInputStream(output.toByteArray()),
        ).use { XmlTvParser().records(it).toList() }

        assertEquals(2, records.size)
    }

    @Test
    fun `ignores malformed records while continuing the document`() {
        val malformed = """
            <tv>
              <channel><display-name>Missing ID</display-name></channel>
              <programme channel="liiga.1" start="20260822190000 +0300" stop="20260822200000 +0300">
                <desc>Missing title</desc>
              </programme>
              <channel id="valid"><display-name>Valid</display-name></channel>
            </tv>
        """.trimIndent()

        val records = XmlTvParser().records(malformed.stream()).toList()

        assertEquals(1, records.size)
        assertEquals("valid", (records.single() as XmlTvRecord.Channel).id)
        assertNull((records.single() as XmlTvRecord.Channel).iconUrl)
    }

    @Test
    fun `skips a programme with an unparseable timestamp and keeps the rest`() {
        // Provider feeds routinely carry a few malformed timestamps. Losing one
        // programme is acceptable; losing the whole guide is not.
        val document = """
            <tv>
              <programme channel="a" start="not-a-timestamp" stop="20260822200000 +0300">
                <title>Broken start</title>
              </programme>
              <programme channel="a" start="20260822190000 +0300" stop="20260822-bad">
                <title>Broken stop</title>
              </programme>
              <programme channel="a" start="20260822200000 +0300" stop="20260822210000 +0300">
                <title>Good programme</title>
              </programme>
            </tv>
        """.trimIndent()

        val records = XmlTvParser().records(document.stream()).toList()

        assertEquals(1, records.size)
        assertEquals("Good programme", (records.single() as XmlTvRecord.Programme).title)
    }

    @Test
    fun `a malformed timestamp does not abort the surrounding channels`() {
        val document = """
            <tv>
              <channel id="before"><display-name>Before</display-name></channel>
              <programme channel="before" start="garbage" stop="garbage">
                <title>Broken</title>
              </programme>
              <channel id="after"><display-name>After</display-name></channel>
            </tv>
        """.trimIndent()

        val ids = XmlTvParser().records(document.stream())
            .filterIsInstance<XmlTvRecord.Channel>()
            .map { it.id }
            .toList()

        assertEquals(listOf("before", "after"), ids)
    }

    private fun String.stream() = ByteArrayInputStream(toByteArray(Charsets.UTF_8))
}
