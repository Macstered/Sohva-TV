package com.sohva.tv.core.net.metadata

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.metadata.MediaType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Spec 41 §11 "Providers against a mock server". */
class MetadataClientsTest {
    private val server = MockWebServer().apply { start() }
    private val http = MetadataHttp(OkHttpClient())
    private val tmdb = TmdbClient(http, server.url("/3/"))
    private val tvmaze = TvmazeClient(http, server.url("/"))

    @After
    fun stop() = server.close()

    private fun json(body: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    @Test
    fun aV3KeyTravelsAsApiKeyAndATokenAsBearer() = runBlocking {
        json("{}")
        tmdb.check(TmdbCredential.of(" 0123456789abcdef0123456789ABCDEF ")!!)
        val first = server.takeRequest()
        assertEquals("/3/configuration", first.url.encodedPath)
        assertEquals("0123456789abcdef0123456789ABCDEF", first.url.queryParameter("api_key"))
        assertNull(first.headers["Authorization"])
        assertEquals(MetadataHttp.USER_AGENT, first.headers["User-Agent"])
        json("{}")
        tmdb.check(TmdbCredential.of("eyJ-token")!!)
        val second = server.takeRequest()
        assertEquals("Bearer eyJ-token", second.headers["Authorization"])
        assertNull(second.url.queryParameter("api_key"))
    }

    @Test
    fun searchReadsTwelveResultsWithTheirRatingAndPaths() = runBlocking {
        val results = (1..15).joinToString(",") {
            """{"id":$it,"title":"Dune $it","original_title":"Dune","release_date":"2021-09-15","poster_path":"/p$it.jpg","genre_ids":[878,12],"popularity":${100 - it},"vote_average":7.84}"""
        }
        json("""{"results":[$results]}""")
        val found = tmdb.search(MediaType.MOVIE, "Dune", "fi-FI", TmdbCredential.of("tok")!!)
        val request = server.takeRequest()
        assertEquals("/3/search/movie", request.url.encodedPath)
        assertEquals("fi-FI", request.url.queryParameter("language"))
        assertEquals("false", request.url.queryParameter("include_adult"))
        assertEquals(12, found.size)
        val first = found.first()
        assertEquals("1", first.externalId)
        assertEquals(listOf("Dune"), first.alternativeTitles)
        assertEquals(2021, first.year)
        assertEquals("7.8", first.rating)
        assertEquals(listOf(878, 12), first.genreIds)
        assertEquals("https://image.tmdb.org/t/p/w342/p1.jpg", Artwork.url(first.poster, Artwork.POSTER_WALL))
    }

    @Test
    fun multiSearchKeepsOnlyFilmsAndSeries() = runBlocking {
        json("""{"results":[{"id":1,"media_type":"person","name":"Someone"},{"id":2,"media_type":"tv","name":"Northern Line","first_air_date":"2020-01-01"},{"id":3,"media_type":"movie","title":"Heat"}]}""")
        val found = tmdb.search(MediaType.PROGRAMME, "x", "en-US", TmdbCredential.of("tok")!!)
        assertEquals(listOf(MediaType.SERIES, MediaType.MOVIE), found.map { it.type })
        assertEquals("/3/search/multi", server.takeRequest().url.encodedPath)
    }

    @Test
    fun movieDetailsCarryCastSimilarAndRuntime() = runBlocking {
        val cast = (1..12).joinToString(",") { """{"name":"Actor $it","character":"Role $it","profile_path":"/a$it.jpg"}""" }
        val similar = (1..25).joinToString(",") { """{"id":${if (it == 3) 603 else 1000 + it},"title":"Other $it","release_date":"1999-01-01"}""" }
        json("""{"id":603,"title":"The Matrix","release_date":"1999-03-31","runtime":136,"vote_average":8.2,"genres":[{"id":28}],"credits":{"cast":[$cast]},"similar":{"results":[$similar]}}""")
        val m = tmdb.movie("603", "en-US", TmdbCredential.of("tok")!!)!!
        assertEquals("credits,similar", server.takeRequest().url.queryParameter("append_to_response"))
        assertEquals(136, m.runtimeMinutes)
        assertEquals("8.2", m.rating)
        assertEquals(listOf(28), m.genreIds)
        assertEquals(8, m.cast.size)
        assertEquals(20, m.similar.size)
        assertFalse(m.similar.any { it.externalId == "603" })
        assertEquals("https://www.themoviedb.org/movie/603", m.attributionUrl)
        assertTrue(m.detailsLoaded)
    }

    /** Spec 40 VOD-FR-112, -113: the IMDb id for Discover and the seasons aired so far, never an announced one. */
    @Test
    fun seriesDetailsCarryTheImdbIdAndTheSeasonsAired() = runBlocking {
        json("""{"id":1399,"name":"A fictional saga","number_of_seasons":6,"last_episode_to_air":{"season_number":5,"episode_number":10},"external_ids":{"imdb_id":"tt0944947"}}""")
        val s = tmdb.series("1399", "en-US", TmdbCredential.of("tok")!!)!!
        assertEquals("credits,external_ids", server.takeRequest().url.queryParameter("append_to_response"))
        assertEquals("tt0944947", s.imdb)
        assertEquals(5, s.airedSeasons)
        // Nothing aired, no IMDb id: known and empty, not "not asked".
        json("""{"id":2,"name":"Announced"}""")
        val none = tmdb.series("2", "en-US", TmdbCredential.of("tok")!!)!!
        assertEquals("", none.imdb)
        assertEquals(0, none.airedSeasons)
        json("""{"id":603,"title":"The Matrix","imdb_id":"tt0133093"}""")
        assertEquals("tt0133093", tmdb.movie("603", "en-US", TmdbCredential.of("tok")!!)!!.imdb)
        json("""[{"show":{"id":5,"name":"Northern Line","externals":{"imdb":"tt7654321"}}}]""")
        assertEquals("tt7654321", tvmaze.search("Northern Line").single().imdb)
    }

    @Test
    fun aZeroRatingIsNoRating() = runBlocking {
        json("""{"id":1,"name":"Show","vote_average":0,"episode_run_time":[0,42]}""")
        val s = tmdb.series("1", "en-US", TmdbCredential.of("tok")!!)!!
        assertNull(s.rating)
        assertEquals(42, s.runtimeMinutes)
    }

    @Test
    fun tvmazeCleansHtmlAndKeepsHttpsArtworkOnly() = runBlocking {
        json("""[{"show":{"id":5,"name":"Northern Line","summary":"<p>A <b>tense</b>&nbsp;drama &amp; more</p>","premiered":"2019-05-01","image":{"medium":"http://static.tvmaze.com/p.jpg","original":"https://static.tvmaze.com/o.jpg"}}}]""")
        val show = tvmaze.search("Northern Line").single()
        assertEquals("A tense drama & more", show.overview)
        assertNull(show.poster)
        assertEquals("https://static.tvmaze.com/o.jpg", show.backdrop)
        assertEquals("https://www.tvmaze.com/shows/5", show.attributionUrl)
        assertEquals(MetadataHttp.USER_AGENT, server.takeRequest().headers["User-Agent"])
    }

    @Test
    fun tvmazeRetriesA429OnceButTmdbDoesNot() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "1").build())
        json("[]")
        assertEquals(emptyList<MetadataRecord>(), tvmaze.search("x"))
        assertEquals(2, server.requestCount)
        server.enqueue(MockResponse.Builder().code(429).build())
        try {
            tmdb.check(TmdbCredential.of("tok")!!)
            fail("TMDB 429 must be an error")
        } catch (e: AppException) {
            assertEquals(AppError.MetadataHttp("TMDB", 429), e.error)
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun bodiesOverTwoMebibytesAreRefused() = runBlocking {
        json("[\"" + "x".repeat((MetadataHttp.MAX_BODY + 10).toInt()) + "\"]")
        try {
            tvmaze.search("x")
            fail("too large")
        } catch (e: AppException) {
            assertEquals(AppError.MetadataTooLarge("TVmaze"), e.error)
        }
    }

    @Test
    fun httpErrorsNameTheProvider() = runBlocking {
        json("{}", code = 401)
        try {
            tmdb.check(TmdbCredential.of("tok")!!)
            fail("401")
        } catch (e: AppException) {
            assertEquals(AppError.MetadataHttp("TMDB", 401), e.error)
        }
    }
}
