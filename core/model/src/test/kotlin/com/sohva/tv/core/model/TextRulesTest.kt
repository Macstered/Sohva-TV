package com.sohva.tv.core.model

import com.sohva.tv.core.model.text.Keys
import com.sohva.tv.core.model.text.NameNormalizer
import com.sohva.tv.core.model.text.StableIds
import com.sohva.tv.core.model.text.StreamTags
import com.sohva.tv.core.model.time.XmlTvTime
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextRulesTest {
    @Test
    fun normaliserFoldsMarksCaseAndPunctuation() {
        assertEquals("yle tv1 hd", NameNormalizer.normalize("  Yle TV1 (HD)! "))
        assertEquals("ma ma", NameNormalizer.normalize("Mä–Mä"))
        assertEquals("", NameNormalizer.normalize("Россия"))
    }

    @Test
    fun idsFollowTheCompatibilityContract() {
        val ids = StableIds()
        // SHA-256("abc") is a published test vector.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ids.sha256Hex("abc"))
        assertEquals(ids.sha256Hex("yle.fi|yle tv1"), ids.m3uEntryId("yle.fi", "yle tv1", "http://x"))
        assertEquals(ids.sha256Hex("yle tv1|http://x"), ids.m3uEntryId(null, "yle tv1", "http://x"))
        assertEquals(ids.sha256Hex("yle.fi|1000|2000|News").take(16), ids.programmeKey("yle.fi", 1_000L, 2_000L, "News"))
        assertEquals("id:42", Keys.groupKey("42", "Sports"))
        assertEquals("name:sports hd", Keys.groupKey(null, "  Sports HD "))
        assertEquals("m3u-1:abc", Keys.globalChannelId("m3u-1", "abc"))
        assertNotEquals(Keys.hash64("a"), Keys.hash64("b"))
    }

    @Test
    fun streamTagsAreWholeTokensInKindOrder() {
        assertEquals(listOf("FHD", "HDR", "50 FPS", "FI"), StreamTags.of("Yle TV1 FHD | HDR | 50FPS | FI"))
        assertEquals(listOf("4K", "HDR10+"), StreamTags.of("Movies 4K HDR10+"))
        assertEquals(emptyList<String>(), StreamTags.of("SCIFI Channel"))
        assertEquals(listOf("SE"), StreamTags.of("Kanal SE"))
    }

    @Test
    fun resolutionTokensAreNotAlsoFrameRates() {
        // Beta 23 showed "Sport 720p" as "HD" and "720 FPS".
        assertEquals(listOf("HD"), StreamTags.of("Sport 720p"))
        assertEquals(listOf("SD", "25 FPS"), StreamTags.of("News 576p 25p"))
        assertEquals(listOf("FI", "SE", "DE"), StreamTags.normalizeLanguages(listOf("fin", "SV", "xx", "fi", "GER")))
    }

    @Test
    fun xmltvTimestampsNeedAnOffset() {
        val expected = Instant.parse("2026-09-24T19:00:00Z").toEpochMilli()
        assertEquals(expected, XmlTvTime.parseMillis("20260924190000 +0000"))
        assertEquals(expected, XmlTvTime.parseMillis("202609242200 +0300"))
        assertEquals(expected, XmlTvTime.parseMillis(" 20260924190000Z "))
        assertEquals(expected, XmlTvTime.parseMillis("20260924140000 -0500"))
        assertEquals(Instant.parse("1969-12-31T23:59:59Z").toEpochMilli(), XmlTvTime.parseMillis("19691231235959 +0000"))
        assertNull(XmlTvTime.parseMillis("20260924190000"))
        assertNull(XmlTvTime.parseMillis("20260924 +0000"))
        assertNull(XmlTvTime.parseMillis("20261324190000 +0000"))
        assertNull(XmlTvTime.parseMillis("20260924190000 +00"))
    }
}
