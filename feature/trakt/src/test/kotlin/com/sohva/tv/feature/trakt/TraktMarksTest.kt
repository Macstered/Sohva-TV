package com.sohva.tv.feature.trakt

import com.sohva.tv.core.data.database.TraktStateEntity
import com.sohva.tv.core.model.vod.TitleMark
import com.sohva.tv.feature.trakt.marks.TraktMarks
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 51 §11 "Overlays": Discover keys for Trakt rows (FR-31). */
class TraktMarksTest {
    @Test
    fun rowsAnswerUnderEveryIdFormTheyHave() {
        val film = TraktStateEntity("p", "movie:tmdb:603", "movie", 603, "tt0133093", null, null, 40.0, false, 0, 5)
        val episode = TraktStateEntity("p", "episode:tmdb:1399:1:2", "episode", 1399, null, 1, 2, 0.0, true, 1, 6)
        val keys = listOf("movie:tt0133093", "movie:tmdb:603", "series:tmdb:1399:1:2", "series:tmdb:1399:1:3", "movie:kitsu:1")
        assertEquals(setOf("tt0133093") to setOf(603L, 1399L), TraktMarks.ids(keys))
        val marks = TraktMarks.marks(keys, listOf(film, episode))
        assertEquals(TitleMark(0.4f, false, 5), marks["movie:tt0133093"])
        assertEquals(TitleMark(0.4f, false, 5), marks["movie:tmdb:603"])
        assertEquals(TitleMark(null, true, 6), marks["series:tmdb:1399:1:2"])
        assertEquals(3, marks.size)
    }
}
