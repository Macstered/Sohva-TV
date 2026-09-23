package com.sohva.tv.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddonEpisodeOrderTest {
    private fun video(id: String, season: Int?, episode: Int?) = AddonVideo(id, id, season, episode, null)
    private fun series(vararg videos: AddonVideo, type: String = "series") =
        AddonMedia(AddonMediaKey(type, "title"), "Title", null, "poster", null, null, null, videos.toList())

    @Test fun unorderedMetadataAdvancesNumericallyWithoutGuessingIds() {
        val media = series(video("opaque-ten", 1, 10), video("opaque-two", 1, 2), video("opaque-one", 1, 1))
        assertEquals("opaque-two", media.nextEpisode("opaque-one")?.id)
        assertEquals("opaque-ten", media.nextEpisode("opaque-two")?.id)
    }

    @Test fun crossesSeasonBoundaryAndStopsAtLastEpisode() {
        val media = series(video("next-season", 2, 1), video("finale", 1, 12))
        assertEquals("next-season", media.nextEpisode("finale")?.id)
        assertNull(media.nextEpisode("next-season"))
    }

    @Test fun specialsDoNotJoinRegularEpisodeAutoplay() {
        val media = series(video("special2", 0, 2), video("pilot", 1, 1), video("special1", 0, 1))
        assertEquals("special2", media.nextEpisode("special1")?.id)
        assertNull(media.nextEpisode("special2"))
        assertNull(media.nextEpisode("pilot"))
    }

    @Test fun alternativeCopyOfSameEpisodeIsNotPlayedAgain() {
        val media = series(video("one", 1, 1), video("alternative-one", 1, 1), video("two", 1, 2))
        assertEquals("two", media.nextEpisode("one")?.id)
    }

    @Test fun unnumberedListsKeepProviderOrder() {
        val media = series(video("z", null, null), video("a", null, null), type = "custom-series")
        assertEquals("a", media.nextEpisode("z")?.id)
        assertNull(media.nextEpisode("a"))
    }

    @Test fun missingMetadataAndMoviesNeverGuessANextEpisode() {
        assertNull(series(video("a", 1, 1)).nextEpisode("unknown"))
        assertNull(series().nextEpisode("a"))
        assertNull(series(video("a", 1, 1), video("b", 1, 2), type = "movie").nextEpisode("a"))
    }
}
