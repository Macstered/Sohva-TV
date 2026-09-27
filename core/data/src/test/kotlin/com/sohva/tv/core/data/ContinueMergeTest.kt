package com.sohva.tv.core.data

import com.sohva.tv.core.data.home.ContinueMerge
import com.sohva.tv.core.data.vod.ContinueItem
import com.sohva.tv.core.data.vod.DiscoverResume
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 02 §4.4: one Continue watching row from the library and Discover. */
class ContinueMergeTest {
    private fun film(key: String, at: Long, tmdb: String? = null) =
        ContinueItem("vod:movie:s1:$key", "vod:movie:s1:$key", "Film $key", null, null, null, false, null, null, null, tmdb, 1_000, 10_000, at)

    private fun episode(series: String, key: String, at: Long) =
        ContinueItem("vod:episode:s1:$key", "vod:series:s1:$series", "Series $series", null, null, null, false, 1, 1, null, null, 1_000, 10_000, at)

    private fun addon(type: String, id: String, video: String, at: Long) = ContinueItem(
        "discover:inst:$id:$video", "discover:inst:$type:$id", "Addon $id", null, null, null, false, null, null, null, null, 1_000, 10_000, at,
        DiscoverResume("inst", type, id, video, null, null),
    )

    @Test
    fun theSameFilmFromTheLibraryAndDiscoverShowsOnceAsTheNewerCopy() {
        val merged = ContinueMerge.merge(listOf(film("a", 100, tmdb = "603")), listOf(addon("movie", "tmdb:603", "tmdb:603", 200)), 12)
        assertEquals(listOf("discover:inst:tmdb:603:tmdb:603"), merged.map { it.contentKey })
        val older = ContinueMerge.merge(listOf(film("a", 300, tmdb = "603")), listOf(addon("movie", "tmdb:603", "tmdb:603", 200)), 12)
        assertEquals(listOf("vod:movie:s1:a"), older.map { it.contentKey })
    }

    @Test
    fun traktsImdbIdBridgesALibraryFilmToItsDiscoverCopy() {
        // TRAKT-23: the library copy carries the IMDb id Trakt holds for TMDB 603.
        val bridged = film("a", 100, tmdb = "603").copy(imdbId = "tt0133093")
        val merged = ContinueMerge.merge(listOf(bridged), listOf(addon("movie", "tt0133093", "tt0133093", 200)), 12)
        assertEquals(listOf("discover:inst:tt0133093:tt0133093"), merged.map { it.contentKey })
    }

    @Test
    fun anImdbFilmOnlyMergesWithAnotherImdbAliasAndSeriesNeverMerge() {
        val merged = ContinueMerge.merge(
            listOf(film("a", 100, tmdb = "603"), episode("x", "e1", 50)),
            listOf(addon("movie", "tt0133093", "tt0133093", 200), addon("movie", "tt0133093", "other", 150), addon("series", "tmdb:603", "tmdb:603:1:1", 120)),
            12,
        )
        assertEquals(
            listOf("discover:inst:tt0133093:tt0133093", "discover:inst:tmdb:603:tmdb:603:1:1", "vod:movie:s1:a", "vod:episode:s1:e1"),
            merged.map { it.contentKey },
        )
    }

    @Test
    fun aBridgingEntryJoinsTwoGroupsAndTheRowStopsAtTheLimit() {
        val library = listOf(film("a", 100, tmdb = "7"), film("b", 90, tmdb = "8"))
        val discover = listOf(addon("movie", "tmdb:7", "v", 80))
        assertEquals(listOf("vod:movie:s1:a", "vod:movie:s1:b"), ContinueMerge.merge(library, discover, 12).map { it.contentKey })
        val many = (1..20).map { film("f$it", it.toLong()) }
        assertEquals(12, ContinueMerge.merge(many, emptyList(), 12).size)
        assertEquals("vod:movie:s1:f20", ContinueMerge.merge(many, emptyList(), 12).first().contentKey)
    }

    @Test
    fun tiesGoToTheSmallerKey() {
        val merged = ContinueMerge.merge(listOf(film("b", 100, tmdb = "5")), listOf(addon("movie", "tmdb:5", "a", 100)), 12)
        assertEquals(listOf("discover:inst:tmdb:5:a"), merged.map { it.contentKey })
    }
}
