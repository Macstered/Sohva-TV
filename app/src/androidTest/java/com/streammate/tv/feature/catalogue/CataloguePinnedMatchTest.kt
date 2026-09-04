package com.streammate.tv.feature.catalogue

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.core.security.MetadataSettings
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.iptv.metadata.MetadataLookup
import com.streammate.tv.iptv.metadata.MetadataMediaType
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.metadata.MetadataSearchResult
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Choosing a match by hand, for the titles the matcher refuses to guess at.
 *
 * The provider is answered from a canned response rather than the network, so
 * the requests can be counted - which is the point of a pinned match: once one
 * is made, nothing asks again.
 */
@RunWith(AndroidJUnit4::class)
class CataloguePinnedMatchTest {

    private lateinit var database: StreamMateDatabase
    private lateinit var repository: MetadataRepository
    private val requests = AtomicInteger(0)
    private var now = 1_000L

    @Before
    fun createRepository() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        val settingsStore = SecretSettingsStore(context, TestCipher)
        settingsStore.saveMetadataSettings(
            MetadataSettings(tmdbEnabled = true, tmdbReadAccessToken = "token"),
        )
        repository = MetadataRepository(
            dao = database.metadataDao(),
            settingsStore = settingsStore,
            httpClient = OkHttpClient.Builder().addInterceptor(CannedTmdb()).build(),
            clock = { now },
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun choosingAMatchSettlesWhatTheTitleIs() = runBlocking {
        val metadata = repository.pinMatch(
            contentKey = CONTENT_KEY,
            lookup = LOOKUP,
            result = CHOICE,
            providerPosterUrl = null,
        )

        assertEquals("The Matrix", metadata?.title)
        assertEquals(
            listOf(CatalogueGenre.ACTION),
            metadata?.genres,
        )
        // It leaves the unsorted row and joins its own.
        assertEquals(
            listOf("action"),
            database.metadataDao().genres(CONTENT_KEY),
        )
    }

    @Test
    fun nothingAsksTheProviderAgainOnceAMatchIsChosen() = runBlocking {
        repository.pinMatch(CONTENT_KEY, LOOKUP, CHOICE, null)
        val afterPinning = requests.get()

        repeat(3) { assertNotNull(repository.enrich(LOOKUP)) }

        assertEquals("a chosen match was looked up again", afterPinning, requests.get())
    }

    /** It holds for good, not for the thirty days a found match lasts. */
    @Test
    fun aChosenMatchDoesNotGoStale() = runBlocking {
        repository.pinMatch(CONTENT_KEY, LOOKUP, CHOICE, null)

        now += 400L * 24 * 60 * 60 * 1_000
        database.metadataDao().deleteExpired(now)

        assertNotNull("the expiry sweep took a chosen match", repository.enrich(LOOKUP))
    }

    /**
     * A title arrives here precisely because the matcher refused it, and that
     * refusal is recorded. Choosing has to displace it, or the miss keeps being
     * served and the choice appears to do nothing.
     */
    @Test
    fun choosingAMatchDisplacesTheRecordedMiss() = runBlocking {
        // No search will match this, so the matcher records a miss.
        assertNull(repository.enrich(UNMATCHABLE))

        val metadata = repository.pinMatch(CONTENT_KEY, UNMATCHABLE, CHOICE, null)

        assertNotNull(metadata)
        assertEquals("The Matrix", repository.enrich(UNMATCHABLE)?.title)
    }

    @Test
    fun theLibraryKnowsWhichMatchesWereChosen() = runBlocking {
        assertFalse(repository.isMatchPinned(LOOKUP))

        repository.pinMatch(CONTENT_KEY, LOOKUP, CHOICE, null)

        assertTrue(repository.isMatchPinned(LOOKUP))
    }

    @Test
    fun lettingGoOfAChoiceReturnsTheTitleToTheMatcher() = runBlocking {
        repository.pinMatch(CONTENT_KEY, LOOKUP, CHOICE, null)

        repository.clearPinnedMatch(CONTENT_KEY, LOOKUP)

        assertFalse(repository.isMatchPinned(LOOKUP))
        assertEquals(emptyList<String>(), database.metadataDao().genres(CONTENT_KEY))
    }

    @Test
    fun theSearchOffersWhatTheProviderReturned() = runBlocking {
        val results = repository.searchCandidates(LOOKUP)

        assertEquals(listOf("The Matrix", "The Matrix Reloaded"), results.map { it.title })
        assertEquals(listOf(1999, 2003), results.map { it.year })
    }

    /** Answers TMDB's endpoints from fixtures, and counts what was asked. */
    private inner class CannedTmdb : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            requests.incrementAndGet()
            val url = chain.request().url.toString()
            val body = when {
                url.contains("/movie/603") -> MOVIE_DETAILS
                url.contains("/search/movie") && url.contains("Ei+t") -> EMPTY_RESULTS
                url.contains("/search/movie") -> SEARCH_RESULTS
                else -> EMPTY_RESULTS
            }
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private object TestCipher : SecretCipher {
        override fun encrypt(plainText: String): String = "test:$plainText"
        override fun decrypt(encoded: String): String = encoded.removePrefix("test:")
    }

    private companion object {
        const val CONTENT_KEY = "vod:movie:source:1"

        val LOOKUP = MetadataLookup(
            mediaType = MetadataMediaType.MOVIE,
            title = "The Matrix",
            year = 1999,
        )

        /** The case this feature exists for: nothing comes back at all. */
        val UNMATCHABLE = MetadataLookup(
            mediaType = MetadataMediaType.MOVIE,
            title = "Ei tunnistettava nimi",
        )

        val CHOICE = MetadataSearchResult(
            externalId = "603",
            mediaType = MetadataMediaType.MOVIE,
            title = "The Matrix",
            year = 1999,
            overview = null,
            posterUrl = null,
        )

        const val MOVIE_DETAILS = """
            {
              "id": 603,
              "title": "The Matrix",
              "original_title": "The Matrix",
              "overview": "A hacker learns what the world really is.",
              "release_date": "1999-03-30",
              "runtime": 136,
              "vote_average": 8.2,
              "genres": [ { "id": 28, "name": "Action" }, { "id": 878, "name": "Science Fiction" } ]
            }
        """

        const val SEARCH_RESULTS = """
            {
              "results": [
                { "id": 603, "title": "The Matrix", "release_date": "1999-03-30", "popularity": 90.0 },
                { "id": 604, "title": "The Matrix Reloaded", "release_date": "2003-05-15", "popularity": 60.0 }
              ]
            }
        """

        const val EMPTY_RESULTS = """{ "results": [] }"""
    }
}
