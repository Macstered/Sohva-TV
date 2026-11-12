package com.sohva.tv.core.model

import com.sohva.tv.core.model.player.StreamReply
import com.sohva.tv.core.model.player.StreamReply.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The first bytes of a reply the player could not read, as the diagnostics name them (PLAY-FR-94). */
class StreamReplyTest {
    private fun kind(text: String): Kind = bytes(text.toByteArray(Charsets.UTF_8))
    private fun bytes(head: ByteArray): Kind = StreamReply.kindOf(head, head.size)

    @Test
    fun namesTheKindOfReply() {
        assertEquals(Kind.EMPTY, bytes(ByteArray(0)))
        assertEquals(Kind.PLAYLIST, kind("#EXTM3U\n#EXT-X-VERSION:3\nseg/0.ts\n"))
        // A byte-order mark, blank lines or spaces before the tag do not hide a playlist.
        assertEquals(Kind.PLAYLIST, bytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "\r\n #EXTM3U".toByteArray()))
        assertEquals(Kind.WEB_PAGE, kind("<!DOCTYPE html><html><body>Service unavailable</body></html>"))
        assertEquals(Kind.WEB_PAGE, kind("<?xml version=\"1.0\"?><error/>"))
        assertEquals(Kind.JSON, kind("{\"user_info\":{\"auth\":0}}"))
        assertEquals(Kind.TEXT, kind("Max connections reached"))
        // MPEG-TS sync bytes and an MP4 box header are data, not text.
        assertEquals(Kind.DATA, bytes(byteArrayOf(0x47, 0x40, 0x00, 0x10, 0x00, 0x00, 0xB0.toByte(), 0x0D)))
        assertEquals(Kind.DATA, bytes(byteArrayOf(0, 0, 0, 0x20) + "ftypisom".toByteArray()))
    }

    @Test
    fun onlyTheFilledPartOfTheBufferCounts() {
        val buffer = ByteArray(StreamReply.PEEK)
        "#EXTM3U".toByteArray().copyInto(buffer)
        assertEquals(Kind.PLAYLIST, StreamReply.kindOf(buffer, 7))
        assertEquals(Kind.EMPTY, StreamReply.kindOf(buffer, 0))
    }

    @Test
    fun keepsOnlyTheMediaTypeOfTheHeader() {
        assertEquals("text/html", StreamReply.mediaType("text/html; charset=UTF-8"))
        assertEquals("application/vnd.apple.mpegurl", StreamReply.mediaType(" Application/VND.Apple.MpegURL "))
        assertNull(StreamReply.mediaType(null))
        assertNull(StreamReply.mediaType(""))
        // Anything that is not a plain type/subtype is not copied into the diagnostics.
        assertNull(StreamReply.mediaType("text/html?token=secret"))
        assertNull(StreamReply.mediaType("x".repeat(100) + "/html"))
    }

    @Test
    fun describesWithoutAddressOrContent() {
        val line = StreamReply(Kind.WEB_PAGE, "text/html", redirected = true).describe()
        assertEquals("reply: web page, type text/html, redirected to another host", line)
        assertEquals("reply: playlist, type none, same host", StreamReply(Kind.PLAYLIST, null, redirected = false).describe())
        assertTrue(StreamReply(Kind.PLAYLIST, null, false).isPlaylist)
        assertFalse(StreamReply(Kind.TEXT, null, false).isPlaylist)
    }
}
