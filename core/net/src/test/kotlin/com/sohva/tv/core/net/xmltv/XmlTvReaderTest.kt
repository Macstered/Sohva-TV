package com.sohva.tv.core.net.xmltv

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.text.StableIds
import com.sohva.tv.core.net.http.BodySources
import java.time.Instant
import okio.Buffer
import okio.BufferedSource
import okio.GzipSink
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class XmlTvReaderTest {
    private val guide = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv generator-info-name="fixture">
          <channel id=" yle1.fi "><display-name>Yle TV1</display-name><display-name>TV1</display-name>
            <icon src="http://logo.example/a.png"/><icon src="http://logo.example/b.png"/></channel>
          <channel><display-name>No id</display-name></channel>
          <programme channel="yle1.fi" start="20260924190000 +0000" stop="20260924200000 +0000">
            <title lang="fi">Uutiset</title><title lang="en">News</title>
            <sub-title>Illan uutiset</sub-title><desc>Päivän tapahtumat.</desc>
            <category>News</category><category></category><category>Current affairs</category>
            <unknown><title>ignored</title></unknown>
          </programme>
          <programme channel="yle1.fi" start="20260924200000" stop="20260924210000 +0000"><title>No offset</title></programme>
          <programme channel="yle1.fi" start="20260924 +0000" stop="20260924210000 +0000"><title>Date only</title></programme>
          <programme channel="yle1.fi" start="20260924210000 +0000" stop="20260924220000 +0000"><desc>No title</desc></programme>
          <programme channel="yle1.fi" start="20260924220000 +0000" stop="20260924230000 +0000"><title>News <b>live</b> tonight</title></programme>
          <channel id="yle2.fi"><display-name>Yle TV2</display-name></channel>
        </tv>
    """.trimIndent()

    private fun records(source: BufferedSource, filter: ProgrammeFilter = ProgrammeFilter { _, _, _ -> true }): List<XmlTvRecord> {
        val reader = XmlTvReader(source, filter)
        return generateSequence { reader.next() }.toList()
    }

    private fun utf8(text: String): BufferedSource = Buffer().writeUtf8(text)

    private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun recordsInDocumentOrderWithTheSpecRules() {
        val list = records(utf8(guide))
        assertEquals(4, list.size)
        assertEquals(XmlTvChannel("yle1.fi", "Yle TV1", "http://logo.example/b.png"), list[0])
        val news = list[1] as XmlTvProgramme
        assertEquals("Uutiset", news.title)
        assertEquals("Illan uutiset", news.subTitle)
        assertEquals("Päivän tapahtumat.", news.description)
        assertEquals("News\u001FCurrent affairs", news.categories)
        assertEquals(millis("2026-09-24T19:00:00Z"), news.startMillis)
        assertEquals(millis("2026-09-24T20:00:00Z"), news.stopMillis)
        assertEquals(StableIds().programmeKey("yle1.fi", news.startMillis, news.stopMillis, "Uutiset"), news.key)
        assertEquals("News  tonight", (list[2] as XmlTvProgramme).title)
        assertEquals(XmlTvChannel("yle2.fi", "Yle TV2", null), list[3])
    }

    @Test
    fun byteOrderMarksAndEncodings() {
        val expected = records(utf8(guide))
        assertEquals(expected, records(Buffer().write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())).writeUtf8(guide)))
        val latin = guide.replace("encoding=\"UTF-8\"", "encoding=\"ISO-8859-1\"")
        assertEquals(expected, records(Buffer().write(latin.toByteArray(Charsets.ISO_8859_1))))
        val le = Buffer().write(byteArrayOf(0xFF.toByte(), 0xFE.toByte())).write(guide.replace("UTF-8", "UTF-16").toByteArray(Charsets.UTF_16LE))
        assertEquals(expected, records(le))
        val be = Buffer().write(byteArrayOf(0xFE.toByte(), 0xFF.toByte())).write(guide.replace("UTF-8", "UTF-16").toByteArray(Charsets.UTF_16BE))
        assertEquals(expected, records(be))
    }

    @Test
    fun gzipWithByteOrderMark() {
        val gz = Buffer()
        GzipSink(gz).buffer().use { it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())).writeUtf8(guide) }
        assertEquals(records(utf8(guide)), records(BodySources.decompressed(gz, Long.MAX_VALUE)))
    }

    @Test
    fun filteredProgrammesAreNeverReadOrHashed() {
        val reader = XmlTvReader(utf8(guide), { channel, start, _ -> channel == "yle1.fi" && start < millis("2026-09-24T21:00:00Z") })
        val list = generateSequence { reader.next() }.toList()
        assertEquals(listOf("Uutiset"), list.filterIsInstance<XmlTvProgramme>().map { it.title })
        assertEquals(1, reader.hashedProgrammes)
        assertEquals("The channel list is kept whole", 2, list.filterIsInstance<XmlTvChannel>().size)
    }

    @Test
    fun longTextsAreCut() {
        val doc = "<tv><programme channel=\"a\" start=\"20260924190000 +0000\" stop=\"20260924200000 +0000\">" +
            "<title>${"t".repeat(600)}</title><desc>${"d".repeat(5_000)}</desc></programme></tv>"
        val p = records(utf8(doc)).single() as XmlTvProgramme
        assertEquals(XmlTvReader.MAX_TITLE_CHARS, p.title.length)
        assertEquals(XmlTvReader.MAX_DESCRIPTION_CHARS, p.description?.length)
        assertNull(p.categories)
    }

    @Test
    fun documentsThatAreNotWellFormedFailTheImport() {
        for (broken in listOf(guide.substring(0, guide.length / 2), "<tv><channel id=\"a\"><display-name>A & B</display-name></channel></tv>")) {
            try {
                records(utf8(broken))
                fail("accepted a broken guide")
            } catch (e: AppException) {
                assertTrue(e.error is AppError.TransportFailed)
            }
        }
    }

    @Test
    fun throughputOfASevenDayGuide() {
        // 400 channels × 7 days × 48 programmes: printed and compared by hand (spec 10 §11).
        val doc = StringBuilder("<tv>")
        val start = millis("2026-09-24T00:00:00Z")
        val format = java.text.SimpleDateFormat("yyyyMMddHHmmss").apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        for (c in 0 until 400) {
            for (p in 0 until 7 * 48) {
                val from = format.format(start + p * 1_800_000L)
                val to = format.format(start + (p + 1) * 1_800_000L)
                doc.append("<programme channel=\"c$c\" start=\"$from +0000\" stop=\"$to +0000\"><title>Programme $p</title><desc>A description of ordinary length for the programme.</desc></programme>")
            }
        }
        doc.append("</tv>")
        val begun = System.nanoTime()
        val count = records(utf8(doc.toString())).size
        println("XMLTV throughput: $count programmes in ${(System.nanoTime() - begun) / 1_000_000} ms")
        assertEquals(400 * 7 * 48, count)
    }
}
