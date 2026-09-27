package com.sohva.tv.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAY-44: every playable key has a demo picture, the same one each time. */
class DemoPicturesTest {
    @Test
    fun channelsFilmsAndEpisodesEachHaveTheirKindOfPicture() {
        assertEquals("demo_live_football", DemoPictures.nameFor("s1:c1"))
        val film = DemoPictures.nameFor("vod:movie:s1:42")
        assertTrue(film, film.startsWith("demo_movie_"))
        assertEquals(film, DemoPictures.nameFor("vod:movie:s1:42"))
        assertTrue(DemoPictures.nameFor("vod:episode:s1:e7").startsWith("demo_series_"))
    }
}
