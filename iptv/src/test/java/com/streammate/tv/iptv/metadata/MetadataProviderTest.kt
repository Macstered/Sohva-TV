package com.streammate.tv.iptv.metadata

import com.streammate.tv.core.security.MetadataSettings
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataProviderTest {
    @Test
    fun tmdbUsesBearerAuthenticationAndParsesMovieArtwork() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"results":[{"id":42,"title":"Dune","original_title":"Dune",
                    "overview":"Arrakis","poster_path":"/poster.jpg",
                    "backdrop_path":"/backdrop.jpg","release_date":"2021-09-15","popularity":74.5}]}
                    """.trimIndent(),
                ),
            )
            val provider = TmdbMetadataProvider(
                httpClient = OkHttpClient(),
                apiRoot = server.url("/3").toString().removeSuffix("/"),
                imageRoot = "https://image.test/t/p",
            )

            val results = provider.search(
                MetadataLookup(MetadataMediaType.MOVIE, "Dune", year = 2021),
                MetadataSettings(tmdbEnabled = true, tmdbReadAccessToken = "secret-token"),
            )

            assertEquals(1, results.size)
            assertEquals("Dune", results.single().displayTitle)
            assertEquals(2021, results.single().year)
            assertEquals(74.5, results.single().providerPopularity)
            assertEquals("https://image.test/t/p/w500/poster.jpg", results.single().posterUrl)
            val request = server.takeRequest()
            assertEquals("Bearer secret-token", request.headers["Authorization"])
            assertTrue(request.requestUrl?.queryParameter("query") == "Dune")
        }
    }

    @Test
    fun tmdbAddsMovieRuntimeRatingCastAndSimilarTitles() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"id":42,"title":"Dune","original_title":"Dune","overview":"Arrakis",
                    "poster_path":"/poster-detail.jpg","backdrop_path":"/backdrop-detail.jpg",
                    "release_date":"2021-09-15","runtime":155,"vote_average":8.2,
                    "credits":{"cast":[
                      {"name":"Timothée Chalamet","character":"Paul","profile_path":"/timothee.jpg"}
                    ]},
                    "similar":{"results":[
                      {"id":43,"title":"Dune: Part Two","original_title":"Dune: Part Two",
                       "release_date":"2024-02-27","poster_path":"/part-two.jpg"},
                      {"id":44,"title":"Arrival","original_title":"Arrival",
                       "release_date":"2016-11-10","poster_path":"/arrival.jpg"}
                    ]}}
                    """.trimIndent(),
                ),
            )
            val provider = TmdbMetadataProvider(
                httpClient = OkHttpClient(),
                apiRoot = server.url("/3").toString().removeSuffix("/"),
                imageRoot = "https://image.test/t/p",
            )

            val result = provider.movieDetails("42", "fi-FI", "secret-token")

            assertEquals(155, result.runtimeMinutes)
            assertEquals("8.2", result.rating)
            assertEquals("Timothée Chalamet", result.cast.single().name)
            assertEquals("Paul", result.cast.single().character)
            assertEquals(listOf("Dune: Part Two", "Arrival"), result.similarMovies.map { it.title })
            assertEquals(2024, result.similarMovies.first().year)
            assertEquals("https://image.test/t/p/w500/part-two.jpg", result.similarMovies.first().posterUrl)
            assertTrue(result.detailsLoaded)
            val request = server.takeRequest()
            assertEquals("/3/movie/42", request.requestUrl?.encodedPath)
            assertEquals("fi-FI", request.requestUrl?.queryParameter("language"))
            assertEquals("credits,similar", request.requestUrl?.queryParameter("append_to_response"))
        }
    }

    @Test
    fun tmdbAlsoAcceptsV3ApiKeyAuthentication() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"results\":[]}"))
            val provider = TmdbMetadataProvider(
                httpClient = OkHttpClient(),
                apiRoot = server.url("/3").toString().removeSuffix("/"),
            )
            val apiKey = "0123456789abcdef0123456789abcdef" // gitleaks:allow -- format-only fixture

            provider.search(
                MetadataLookup(MetadataMediaType.MOVIE, "Dune", year = 2021),
                MetadataSettings(tmdbEnabled = true, tmdbReadAccessToken = apiKey),
            )

            val request = server.takeRequest()
            assertEquals(apiKey, request.requestUrl?.queryParameter("api_key"))
            assertEquals(null, request.headers["Authorization"])
        }
    }

    @Test
    fun tmdbCredentialCheckUsesConfigurationEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            val provider = TmdbMetadataProvider(
                httpClient = OkHttpClient(),
                apiRoot = server.url("/3").toString().removeSuffix("/"),
            )

            provider.verifyCredential("secret-token")

            val request = server.takeRequest()
            assertEquals("/3/configuration", request.requestUrl?.encodedPath)
            assertEquals("Bearer secret-token", request.headers["Authorization"])
        }
    }

    @Test
    fun tmdbAddsSeriesRuntimeRatingAndCastFromTheMatchedTitle() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"results":[{"id":7,"name":"The Bear","original_name":"The Bear",
                    "overview":"A kitchen family","poster_path":"/poster.jpg",
                    "backdrop_path":"/backdrop.jpg","first_air_date":"2022-06-23","popularity":50.0}]}
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"id":7,"name":"The Bear","overview":"A kitchen family",
                    "poster_path":"/poster-detail.jpg","backdrop_path":"/backdrop-detail.jpg",
                    "episode_run_time":[31],"vote_average":8.6,
                    "credits":{"cast":[
                      {"name":"Jeremy Allen White","character":"Carmy","profile_path":"/jeremy.jpg"},
                      {"name":"Ayo Edebiri","character":"Sydney","profile_path":"/ayo.jpg"}
                    ]}}
                    """.trimIndent(),
                ),
            )
            val provider = TmdbMetadataProvider(
                httpClient = OkHttpClient(),
                apiRoot = server.url("/3").toString().removeSuffix("/"),
                imageRoot = "https://image.test/t/p",
            )

            val result = provider.search(
                MetadataLookup(MetadataMediaType.SERIES, "The Bear", year = 2022),
                MetadataSettings(tmdbEnabled = true, tmdbReadAccessToken = "secret-token"),
            ).single()

            assertEquals(31, result.runtimeMinutes)
            assertEquals("8.6", result.rating)
            assertEquals(listOf("Jeremy Allen White", "Ayo Edebiri"), result.cast.map { it.name })
            assertEquals("Carmy", result.cast.first().character)
            assertEquals("https://image.test/t/p/w185/jeremy.jpg", result.cast.first().profileUrl)
            assertEquals("https://image.test/t/p/w500/poster-detail.jpg", result.posterUrl)
            server.takeRequest()
            val detailRequest = server.takeRequest()
            assertEquals("/3/tv/7", detailRequest.requestUrl?.encodedPath)
            assertEquals("credits", detailRequest.requestUrl?.queryParameter("append_to_response"))
        }
    }

    @Test
    fun tvmazeParsesAndSanitizesShowMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    [{"score":0.99,"show":{"id":7,"name":"The Bear",
                    "url":"https://www.tvmaze.com/shows/7/the-bear",
                    "premiered":"2022-06-23","summary":"<p>A chef &amp; a family.</p>",
                    "image":{"medium":"https://static.tvmaze.com/poster.jpg",
                    "original":"https://static.tvmaze.com/original.jpg"}}}]
                    """.trimIndent(),
                ),
            )
            val provider = TvmazeMetadataProvider(
                httpClient = OkHttpClient(),
                apiRoot = server.url("").toString().removeSuffix("/"),
            )

            val results = provider.search(
                MetadataLookup(MetadataMediaType.SERIES, "The Bear", year = 2022),
                MetadataSettings(tvmazeEnabled = true),
            )

            assertEquals(1, results.size)
            assertEquals("A chef & a family.", results.single().overview)
            assertEquals(2022, results.single().year)
            assertEquals("https://static.tvmaze.com/poster.jpg", results.single().posterUrl)
            assertEquals("SohvaTV/0.1 (Android TV; personal use)", server.takeRequest().headers["User-Agent"])
        }
    }
}
