package com.sohva.tv.core.net.m3u

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.text.StableIds
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class M3uReaderTest {
    private fun reader(text: String): M3uReader = M3uReader(Buffer().writeUtf8(text))

    private fun entries(text: String): List<M3uEntry> {
        val reader = reader(text)
        return generateSequence { reader.next() }.toList()
    }

    private fun single(text: String): M3uEntry = entries(text).single()

    private fun refused(text: String) {
        try {
            entries(text)
            fail("accepted: $text")
        } catch (e: AppException) {
            assertEquals(AppError.PlaylistNotM3u, e.error)
        }
    }

    @Test
    fun quotedAttributesKeepTheirCommas() {
        val e = single(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="yle1.fi" tvg-name="Yle, TV1" tvg-logo='http://logo.example/1.png' group-title="News, Finland",Yle TV1 HD
            http://provider.example/live/1.ts
            """.trimIndent(),
        )
        assertEquals("Yle TV1 HD", e.name)
        assertEquals("yle1.fi", e.tvgId)
        assertEquals("News, Finland", e.group)
        assertEquals("http://logo.example/1.png", e.logoUrl)
        assertEquals(-1, e.durationSeconds)
        assertEquals(M3uKind.LIVE, e.kind)
        assertEquals(0, e.index)
    }

    @Test
    fun headersFromDirectivesAndTheAddress() {
        val list = entries(
            """
            #EXTM3U
            #EXTINF:-1,Vlc
            #EXTVLCOPT:http-user-agent=VLC Agent
            #EXTVLCOPT:http-referrer=http://ref.example/
            http://provider.example/1.ts
            #EXTINF:-1,Kodi
            #KODIPROP:inputstream.adaptive.STREAM_HEADERS=User-Agent=Kodi%20Agent&Referer=http%3A%2F%2Fkodi.example
            http://provider.example/2.ts|User-Agent=Url%20Agent
            #EXTINF:-1,Json
            #EXTHTTP:{"user-agent":"Json Agent","Referrer":"http://json.example"}
            http://provider.example/3.ts|referrer=http%3A%2F%2Furl.example&Cookie=x
            """.trimIndent(),
        )
        assertEquals(listOf("VLC Agent", "Url Agent", "Json Agent"), list.map { it.userAgent })
        assertEquals(listOf("http://ref.example/", "http://kodi.example", "http://url.example"), list.map { it.referrer })
        assertEquals("http://provider.example/2.ts", list[1].streamUrl)
    }

    @Test
    fun optionsBeforeExtinfAreDiscardedAndBrokenJsonIgnored() {
        val e = single("#EXTM3U\n#EXTVLCOPT:http-user-agent=Stale\n#EXTINF:-1,A\n#EXTHTTP:{not json\nhttp://provider.example/a")
        assertNull(e.userAgent)
    }

    @Test
    fun addressOnlyEntriesGetStableIdsAndNoName() {
        val text = "http://provider.example/a.ts\nudp://239.0.0.1:1234\n"
        val first = entries(text)
        assertEquals(first, entries(text))
        assertEquals(listOf(null, null), first.map { it.name })
        assertEquals(listOf("kanava 1", "kanava 2"), first.map { it.normalizedName })
        assertEquals(StableIds().m3uEntryId(null, "kanava 1", "http://provider.example/a.ts"), first[0].id)
        assertEquals("udp://239.0.0.1:1234", first[1].streamUrl)
    }

    @Test
    fun eventChannelsSharingATvgIdStayDistinct() {
        val list = entries(
            """
            #EXTINF:-1 tvg-id="event.fi",Event 1
            http://provider.example/1
            #EXTINF:-1 tvg-id="event.fi",Event 2
            http://provider.example/2
            #EXTINF:-1 tvg-id="event.fi",Event 1
            http://provider.example/3
            """.trimIndent(),
        )
        assertNotEquals(list[0].id, list[1].id)
        assertEquals(list[0].id, list[2].id)
    }

    @Test
    fun nameFallsBackToTvgName() {
        assertEquals("Tvg Name", single("#EXTINF:-1 tvg-name=\"Tvg Name\",\nhttp://provider.example/1").name)
    }

    @Test
    fun catchupAttributes() {
        val list = entries(
            """
            #EXTINF:-1 catchup="Shift" catchup-days="7" catchup-source="?utc={utc}",A
            http://provider.example/1
            #EXTINF:-1 timeshift="3",B
            http://provider.example/2
            #EXTINF:-1 catchup-type="default" catchup-days="500",C
            http://provider.example/3
            #EXTINF:-1 catchup-days="x",D
            http://provider.example/4
            """.trimIndent(),
        )
        assertEquals(listOf("shift", "timeshift", "default", null), list.map { it.catchupType })
        assertEquals(listOf(7, 3, 365, null), list.map { it.catchupDays })
        assertEquals("?utc={utc}", list[0].catchupSource)
    }

    @Test
    fun contentKindKeepsBeta23SubstringRule() {
        fun kind(extinf: String, url: String = "http://provider.example/x"): M3uKind = single("#EXTINF:$extinf\n$url").kind
        assertEquals(M3uKind.MOVIE, kind("-1 group-title=\"Movies FI\",A"))
        assertEquals(M3uKind.SERIES, kind("-1 group-title=\"Sarjat\",A"))
        assertEquals(M3uKind.SERIES, kind("-1 tvg-type=\"Series\",A"))
        assertEquals(M3uKind.SERIES, kind("-1,A", "http://provider.example/series/u/p/1.mkv"))
        assertEquals(M3uKind.MOVIE, kind("-1,A", "http://provider.example/x/1.MKV?token=1"))
        assertEquals(M3uKind.MOVIE, kind("5400,A"))
        // Compatibility, not a wish: substring matching misfiles these live groups (spec 10 §8).
        assertEquals(M3uKind.SERIES, kind("-1 group-title=\"Showtime\",A"))
        assertEquals(M3uKind.MOVIE, kind("-1 group-title=\"Filmbox\",A"))
        assertEquals(M3uKind.LIVE, kind("-1 group-title=\"Sports\",A"))
    }

    @Test
    fun channelNumbersAreWholeNumbersOnly() {
        fun number(attrs: String): Int? = single("#EXTINF:-1 $attrs,A\nhttp://provider.example/1").channelNumber
        assertEquals(12, number("tvg-chno=\"12\""))
        assertNull(number("tvg-chno=\"12.1\""))
        assertNull(number("tvg-chno=\"0\""))
        assertEquals(7, number("channel-number=\"7\""))
        assertNull(number("tvg-chno=\"x\" channel-number=\"7\""))
    }

    @Test
    fun documentsThatAreNotPlaylistsAreRefused() {
        refused("<!DOCTYPE html><html><body>Sign in</body></html>\nhttp://x.example/1")
        refused("{\"user_info\":{\"auth\":0}}")
        refused("[1,2]")
        refused("Invalid credentials.\n#EXTM3U")
        val reader = reader("Access denied, token=hunter2")
        try {
            reader.next()
        } catch (_: AppException) {
        }
        assertFalse(reader.refusedOpening.orEmpty().contains("hunter2"))
    }

    @Test
    fun acceptedOpenings() {
        assertEquals(emptyList<M3uEntry>(), entries("#EXTM3U\n"))
        assertEquals(emptyList<M3uEntry>(), entries(""))
        val reader = reader("﻿#EXTM3U url-tvg=\"http://epg.example/a.xml.gz,http://epg.example/b.xml\" x-tvg-url=\"http://epg.example/c.xml\"\r\n#EXTINF:-1,A\r\nhttp://provider.example/1\r\n")
        assertEquals("A", reader.next()?.name)
        assertEquals("http://epg.example/a.xml.gz", reader.headerGuideUrl)
        assertEquals("udp://239.0.0.1:1234", single("udp://239.0.0.1:1234").streamUrl)
        assertEquals("x-tvg-url wins when url-tvg is absent", "http://epg.example/c.xml",
            reader("#EXTM3U x-tvg-url=\"http://epg.example/c.xml\"").also { it.next() }.headerGuideUrl)
    }

    @Test
    fun extGroupSuppliesOnlyAMissingGroup() {
        val list = entries(
            """
            #EXTINF:-1,A
            #EXTGRP:Extras
            http://provider.example/1
            #EXTINF:-1 group-title="Titled",B
            #EXTGRP:Extras
            http://provider.example/2
            """.trimIndent(),
        )
        assertEquals(listOf("Extras", "Titled"), list.map { it.group })
    }

    @Test
    fun overlongLinesAreSkippedAndAnOverlongOpeningIsRefused() {
        val junk = "x".repeat(70_000)
        val list = entries("#EXTM3U\n#EXTINF:-1,A\nhttp://provider.example/1\n$junk\n#EXTINF:-1,B\nhttp://provider.example/2\n")
        assertEquals(listOf("A", "B"), list.map { it.name })
        assertEquals(0, entries("#EXTM3U\n$junk").size)
        refused("<html>$junk\n#EXTM3U")
        refused("$junk\n#EXTM3U")
    }

    @Test
    fun longNamesAreCut() {
        assertEquals(M3uReader.MAX_NAME_CHARS, single("#EXTINF:-1,${"n".repeat(1_000)}\nhttp://provider.example/1").name?.length)
    }

    @Test
    fun textFormNeverShowsTheAddress() {
        val e = single("#EXTINF:-1,A\nhttp://provider.example/live/viewer/hunter2/1.ts")
        assertFalse(e.toString().contains("hunter2"))
    }
}
