package com.streammate.tv.app

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamMimeTypesTest {

    @Test
    fun `hls playlists resolve to the m3u8 mime type`() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            StreamMimeTypes.fromStreamUrl("http://provider.example/live/user/pass/1234.m3u8"),
        )
    }

    @Test
    fun `query strings and fragments are ignored`() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            StreamMimeTypes.fromStreamUrl("http://provider.example/a.m3u8?token=abc&x=1"),
        )
        assertEquals(
            MimeTypes.APPLICATION_MPD,
            StreamMimeTypes.fromStreamUrl("https://provider.example/b.mpd#start"),
        )
    }

    @Test
    fun `smooth streaming manifests are recognised`() {
        assertEquals(
            MimeTypes.APPLICATION_SS,
            StreamMimeTypes.fromStreamUrl("https://provider.example/x.isml/Manifest"),
        )
        assertEquals(
            MimeTypes.APPLICATION_SS,
            StreamMimeTypes.fromStreamUrl("https://provider.example/x.ism"),
        )
    }

    @Test
    fun `extension matching is case insensitive`() {
        assertEquals(
            MimeTypes.APPLICATION_MPD,
            StreamMimeTypes.fromStreamUrl("https://provider.example/Stream.MPD"),
        )
    }

    @Test
    fun `transport streams keep the progressive fallback`() {
        assertNull(StreamMimeTypes.fromStreamUrl("http://provider.example/live/user/pass/1234.ts"))
        assertNull(StreamMimeTypes.fromStreamUrl("http://provider.example/live/user/pass/1234"))
        assertNull(StreamMimeTypes.fromStreamUrl("http://provider.example/movie/user/pass/9.mkv"))
    }

    @Test
    fun `missing url is tolerated`() {
        assertNull(StreamMimeTypes.fromStreamUrl(null))
        assertNull(StreamMimeTypes.fromStreamUrl(""))
    }
}
