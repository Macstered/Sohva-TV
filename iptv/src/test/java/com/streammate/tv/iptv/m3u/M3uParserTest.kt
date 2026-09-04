package com.streammate.tv.iptv.m3u

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    private val parser = M3uParser()

    @Test
    fun `parses quoted metadata and VLC headers`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="liiga.1" tvg-name='Liiga HD' tvg-logo="https://img.example/liiga.png" group-title="Sports, Finland",Liiga HD
            #EXTVLCOPT:http-user-agent=Shield TV
            #EXTVLCOPT:http-referrer=https://portal.example/
            https://stream.example/liiga.m3u8
        """.trimIndent()

        val channel = parser.parse(playlist.stream()).single()

        assertEquals("liiga.1", channel.tvgId)
        assertEquals("Liiga HD", channel.name)
        assertEquals("liiga hd", channel.normalizedName)
        assertEquals("Sports, Finland", channel.groupTitle)
        assertEquals("Shield TV", channel.userAgent)
        assertEquals("https://portal.example/", channel.referrer)
    }

    @Test
    fun `URL headers override Kodi metadata and are decoded`() {
        val playlist = """
            #EXTINF:-1,Canal+
            #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=Kodi%20Agent&Referer=https%3A%2F%2Fkodi.example%2F
            https://stream.example/channel|User-Agent=URL%20Agent&Referrer=https%3A%2F%2Furl.example%2F
        """.trimIndent()

        val channel = parser.parse(playlist.stream()).single()

        assertEquals("URL Agent", channel.userAgent)
        assertEquals("https://url.example/", channel.referrer)
        assertEquals("https://stream.example/channel", channel.streamUrl)
    }

    @Test
    fun `supports plain M3U entries with stable IDs and safe diagnostics`() {
        val first = parser.parse("https://stream.example/plain\n".stream()).single()
        val second = parser.parse("https://stream.example/plain\n".stream()).single()

        assertEquals("Kanava 1", first.name)
        assertNull(first.tvgId)
        assertEquals(first.id, second.id)
        assertTrue(first.toString().contains("<redacted>"))
        assertFalse(first.toString().contains(first.streamUrl))
    }

    @Test
    fun `keeps event channels distinct when provider reuses a tvg id`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="events",Manchester United v Liverpool 21:30
            http://stream.example/event-1
            #EXTINF:-1 tvg-id="events",Atletico Madrid v Villarreal 22:00
            http://stream.example/event-2
        """.trimIndent()

        val channels = parser.parse(playlist.stream())

        assertEquals(2, channels.size)
        assertFalse(channels[0].id == channels[1].id)
        assertEquals(listOf(0, 1), channels.map(ParsedIptvChannel::playlistOrder))
    }

    @Test
    fun `parses provider hosted catchup metadata`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news" catchup="append" catchup-days="14" catchup-source="?utc={utc}&lutc={lutc}",News
            http://stream.example/live
        """.trimIndent()

        val channel = parser.parse(playlist.stream()).single()

        assertEquals("append", channel.catchupType)
        assertEquals(14, channel.catchupDays)
        assertEquals("?utc={utc}&lutc={lutc}", channel.catchupSource)
    }

    @Test
    fun `legacy timeshift attribute enables shift catchup`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news" timeshift="7",News
            http://stream.example/live
        """.trimIndent()

        val channel = parser.parse(playlist.stream()).single()

        assertEquals("timeshift", channel.catchupType)
        assertEquals(7, channel.catchupDays)
    }

    @Test
    fun `classifies live movies and series from M3U plus provider hints`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 group-title="Finland",Live channel
            http://provider.example/live/user/pass/1.ts
            #EXTINF:-1 group-title="Movies",Movie title
            http://provider.example/movie/user/pass/2.mkv
            #EXTINF:-1 group-title="Series",Show S01E02 - Episode
            http://provider.example/series/user/pass/3.mkv
        """.trimIndent()

        val entries = parser.parse(playlist.stream())

        assertEquals(
            listOf(M3uContentKind.LIVE, M3uContentKind.MOVIE, M3uContentKind.SERIES),
            entries.map(ParsedIptvChannel::contentKind),
        )
    }

    @Test
    fun `finite EXTINF duration is treated as VOD when other hints are absent`() {
        val entry = parser.parse(
            "#EXTINF:5400,Standalone video\nhttp://provider.example/archive/42.ts".stream(),
        ).single()

        assertEquals(M3uContentKind.MOVIE, entry.contentKind)
    }

    private fun String.stream() = ByteArrayInputStream(toByteArray(Charsets.UTF_8))
}
