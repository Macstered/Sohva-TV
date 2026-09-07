package com.streammate.tv.iptv.playback

import java.io.IOException
import java.net.SocketException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHttpTest {
    @Test
    fun `the app names itself when the playlist gives no user agent`() {
        val agent = PlaybackHttp.userAgent("0.1.0-beta.9", "11")
        assertEquals("Sohva TV/0.1.0-beta.9 (Android TV 11)", agent)
        assertEquals(mapOf("User-Agent" to agent), PlaybackHttp.withDefaultUserAgent(emptyMap(), agent))
        assertEquals(
            mapOf("Referer" to "https://example.test/", "User-Agent" to agent),
            PlaybackHttp.withDefaultUserAgent(mapOf("Referer" to "https://example.test/"), agent),
        )
    }

    @Test
    fun `a playlist's own user agent is kept under any casing`() {
        val given = mapOf("user-agent" to "VLC/3.0.20 LibVLC/3.0.20")
        assertEquals(given, PlaybackHttp.withDefaultUserAgent(given, "Sohva TV/x"))
    }

    @Test
    fun `the failure detail carries the innermost cause, redacted`() {
        val error = IOException(
            "Source error",
            IOException("Unable to read http://user:pw@panel.example:8080/live/abc/def/1.ts?token=zz", SocketException("Connection reset")),
        )
        assertEquals(
            "ERROR_CODE_IO_UNSPECIFIED · SocketException: Connection reset",
            PlaybackHttp.failureDetail("ERROR_CODE_IO_UNSPECIFIED", error),
        )
        val noReset = IOException("Unable to read http://user:pw@panel.example:8080/live/abc/def/1.ts?token=zz")
        assertEquals(
            "ERROR_CODE_IO_UNSPECIFIED · IOException: Unable to read http://panel.example:8080/<redacted>",
            PlaybackHttp.failureDetail("ERROR_CODE_IO_UNSPECIFIED", noReset),
        )
    }

    @Test
    fun `a cause with nothing to say leaves the code alone`() {
        assertEquals("ERROR_CODE_IO_UNSPECIFIED", PlaybackHttp.failureDetail("ERROR_CODE_IO_UNSPECIFIED", null))
        assertEquals("ERROR_CODE_IO_UNSPECIFIED", PlaybackHttp.failureDetail("ERROR_CODE_IO_UNSPECIFIED", IOException()))
    }
}
