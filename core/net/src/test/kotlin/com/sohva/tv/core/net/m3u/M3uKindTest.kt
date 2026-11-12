package com.sohva.tv.core.net.m3u

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Live, film or series (spec 10 SRC-FR-57 as changed by the decision "Live or film"): an address
 * that is plainly a live stream stays a channel whatever its group is called, and a group name is
 * a hint only as a whole word.
 */
class M3uKindTest {
    private fun kind(types: String = "", group: String?, address: String, duration: Int? = -1) = M3uText.kindOf(types, group, address, duration)

    @Test
    fun aLiveAddressIsAChannelWhateverItsGroupIsCalled() {
        assertEquals(M3uKind.LIVE, kind(group = "UK Movies", address = "http://h.example/live/u/p/1.ts"))
        assertEquals(M3uKind.LIVE, kind(group = "Aurelia · Movies", address = "http://h.example/stream/ch00002.ts"))
        assertEquals(M3uKind.LIVE, kind(group = "Series HD", address = "http://h.example/hls/1/index.m3u8?token=1"))
        assertEquals(M3uKind.LIVE, kind(group = "Films", address = "http://h.example/live/u/p/77"))
        assertEquals(M3uKind.LIVE, kind(group = "Elokuvat", address = "udp://@239.0.0.1:1234"))
        assertEquals(M3uKind.LIVE, kind(group = "Movies", address = "RTSP://h.example/ch1"))
    }

    @Test
    fun aGroupNameCountsOnlyAsAWholeWord() {
        assertEquals(M3uKind.LIVE, kind(group = "Showtime", address = "http://h.example/1"))
        assertEquals(M3uKind.LIVE, kind(group = "Filmbox", address = "http://h.example/1"))
        assertEquals(M3uKind.LIVE, kind(group = "Devoded", address = "http://h.example/1"))
        assertEquals(M3uKind.MOVIE, kind(group = "Movies", address = "http://h.example/1"))
        assertEquals(M3uKind.MOVIE, kind(group = "VOD | Action", address = "http://h.example/1"))
        assertEquals(M3uKind.MOVIE, kind(group = "FI: Elokuvat", address = "http://h.example/1"))
        assertEquals(M3uKind.SERIES, kind(group = "TV Shows", address = "http://h.example/1"))
        assertEquals(M3uKind.SERIES, kind(group = "Sarjat", address = "http://h.example/1"))
    }

    @Test
    fun declaredTypesPathsExtensionsAndDurationsDecideAsBefore() {
        // A declared type wins even over a live-looking address.
        assertEquals(M3uKind.MOVIE, kind(types = "movie", group = "News", address = "http://h.example/1.ts"))
        assertEquals(M3uKind.SERIES, kind(types = "series", group = null, address = "http://h.example/1.m3u8"))
        assertEquals(M3uKind.MOVIE, kind(group = "Movies", address = "http://h.example/movie/u/p/1.mkv"))
        assertEquals(M3uKind.SERIES, kind(group = null, address = "http://h.example/series/u/p/1.mkv"))
        assertEquals(M3uKind.MOVIE, kind(group = null, address = "http://h.example/x/1.MKV?token=1"))
        assertEquals(M3uKind.MOVIE, kind(group = "News", address = "http://h.example/1", duration = 5400))
        // A film served from a path segment that names it stays a film even with a .ts ending.
        assertEquals(M3uKind.MOVIE, kind(group = null, address = "http://h.example/movie/u/p/1.ts"))
        assertEquals(M3uKind.LIVE, kind(group = "Sports", address = "http://h.example/1"))
    }
}
