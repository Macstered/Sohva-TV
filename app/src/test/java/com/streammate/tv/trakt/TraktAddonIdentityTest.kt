package com.streammate.tv.trakt

import com.sohva.tv.addons.AddonMediaKey
import com.sohva.tv.addons.AddonVideo
import com.sohva.tv.addons.AddonWatchIdentity
import com.sohva.tv.trakt.TraktItem
import org.junit.Assert.*
import org.junit.Test

class TraktAddonIdentityTest {
    private fun identity(type: String, id: String, video: String = id) =
        AddonWatchIdentity("install", AddonMediaKey(type, id), AddonMediaKey(type, video))

    @Test fun moviesUseImdbOrTmdbIds() {
        val imdb = TraktAddonIdentity.resolve(identity("movie", "tt0133093")) as TraktItem.Movie
        assertEquals("tt0133093", imdb.ids.imdb); assertNull(imdb.ids.tmdb)
        val tmdb = TraktAddonIdentity.resolve(identity("movie", "tmdb:603")) as TraktItem.Movie
        assertEquals(603L, tmdb.ids.tmdb)
        assertNull(TraktAddonIdentity.resolve(identity("movie", "kitsu:1234")))
        assertNull(TraktAddonIdentity.resolve(identity("channel", "tt0133093")))
    }

    @Test fun episodesTakeNumbersFromTheVideoEntryOrTheStremioVideoId() {
        val fromEntry = TraktAddonIdentity.resolve(identity("series", "tt0944947", "tt0944947:1:2"),
            AddonVideo("tt0944947:1:2", "Two", 3, 4, null)) as TraktItem.Episode
        assertEquals(3, fromEntry.season); assertEquals(4, fromEntry.number); assertEquals("tt0944947", fromEntry.ids.imdb)
        val fromId = TraktAddonIdentity.resolve(identity("series", "tmdb:1399", "tmdb:1399:1:2")) as TraktItem.Episode
        assertEquals(1, fromId.season); assertEquals(2, fromId.number); assertEquals(1399L, fromId.ids.tmdb)
        assertNull(TraktAddonIdentity.resolve(identity("series", "tt0944947", "tt0944947")))
        assertNull(TraktAddonIdentity.resolve(identity("series", "tt0944947", "tt0944947:one:two")))
    }
}
