package com.sohva.tv.addons

import org.junit.Assert.*
import org.junit.Test

class AddonMediaTest {
    @Test fun moviePreviewsRemainPlayableWithoutMetadataButSeriesNeedExplicitVideos() {
        val movie = AddonMediaParser.catalog("""{"metas":[{"id":"tt1","type":"movie","name":"Fixture","background":"https://example.invalid/art.jpg","genres":["Drama",3],"runtime":"95m","imdbRating":"7.4"}]}""").items.single()
        assertEquals(AddonMediaKey("movie", "tt1"), movie.singleVideoKey())
        assertEquals(listOf("Drama"), movie.genres)
        assertEquals("7.4", movie.imdbRating)
        assertEquals("95m", movie.runtime)
        val series = AddonMediaParser.metadata("""{"meta":{"id":"opaque","type":"series","name":"Fixture","imdbRating":"NaN"}}""")
        assertNull(series.singleVideoKey()); assertNull(series.imdbRating)
    }
    @Test fun catalogPreservesCustomIdentityAndCountsMalformedAndDuplicateTiles() {
        val page = AddonMediaParser.catalog("""{"metas":[
            {"id":"kitsu:ABC","type":"anime.series","name":"Anime","poster":"https://example.invalid/private/poster.jpg","posterShape":"square"},
            {"id":"kitsu:ABC","type":"anime.series","name":"Duplicate"},
            {"id":"missing-type","name":"Broken"},null,
            {"id":"kitsu:ABC","type":"movie","name":"Different type","poster":"file:///private/file"}
        ]}""")
        assertEquals(5, page.receivedCount)
        assertEquals(2, page.items.size)
        assertEquals(AddonMediaKey("anime.series", "kitsu:ABC"), page.items.first().key)
        assertEquals("square", page.items.first().posterShape)
        assertNull(page.items.last().poster)
        assertFalse(page.items.first().toString().contains("private"))
    }
    @Test fun episodesKeepProviderIdsAndSpecialsWithoutGuessing() {
        val media = AddonMediaParser.metadata("""{"meta":{"id":"tvdb:42","type":"series","name":"Fixture","videos":[
            {"id":"provider:Special-A","title":"Special","season":0,"episode":1},
            {"id":"opaque-id","name":"Pilot","season":1,"episode":1,"thumbnail":"https://example.invalid/episode.jpg"},
            {"id":"opaque-id","title":"Duplicate"},{"title":"No identifier"}
        ]}}""")
        assertEquals(listOf("provider:Special-A", "opaque-id"), media.videos.map { it.id })
        assertEquals(0, media.videos.first().season)
        assertEquals("Pilot", media.videos.last().title)
        assertEquals("https://example.invalid/episode.jpg", media.videos.last().thumbnail)
    }
    @Test fun invalidEnvelopeAndExcessiveNestingAreSanitized() {
        expectFailure(AddonFailure.INVALID_RESPONSE) { AddonMediaParser.catalog("{\"private-config\":true}") }
        expectFailure(AddonFailure.INVALID_RESPONSE) { AddonMediaParser.metadata("[".repeat(70) + "]".repeat(70)) }
        expectFailure(AddonFailure.INVALID_RESPONSE) { AddonMediaParser.metadata("""{"meta":null}""") }
    }
    @Test fun catalogsDoNotParseOrPrefetchEmbeddedVideos() {
        val page = AddonMediaParser.catalog("""{"metas":[{"id":"tt1","type":"series","name":"Fixture","videos":"ignored"}]}""")
        assertTrue(page.items.single().videos.isEmpty())
    }
}
