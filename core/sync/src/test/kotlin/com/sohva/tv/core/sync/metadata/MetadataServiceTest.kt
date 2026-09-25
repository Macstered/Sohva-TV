package com.sohva.tv.core.sync.metadata

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.metadata.MetadataLanguageStore
import com.sohva.tv.core.data.metadata.MetadataSettings
import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.net.metadata.MetadataHttp
import com.sohva.tv.core.net.metadata.TmdbClient
import com.sohva.tv.core.net.metadata.TvmazeClient
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 41 §11: the lookup flow, its caches, request sharing, failures versus misses, and pins. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MetadataServiceTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val calls = AtomicInteger()

    @Volatile
    private var answer: (RecordedRequest) -> MockResponse = { json("""{"results":[]}""") }
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls.incrementAndGet()
                return answer(request)
            }
        }
        start()
    }
    private val secrets = object : SecretValues {
        val values = HashMap<String, String>(mapOf(ServiceKeys.TMDB_TOKEN to "token", ServiceKeys.TMDB_ENABLED to "true"))

        override suspend fun read(key: String): Outcome<String?> = Outcome.Ok(values[key])

        override suspend fun write(key: String, value: String?): Outcome<Unit> {
            if (value == null) values.remove(key) else values[key] = value
            return Outcome.Ok(Unit)
        }
    }
    private val languages = object : MetadataLanguageStore {
        var tag: String? = "en-US"

        override suspend fun metadataLanguage(): String? = tag

        override suspend fun setMetadataLanguage(tag: String) {
            this.tag = tag
        }
    }
    private var now = 1_790_000_000_000L
    private val clock = object : Clock {
        override fun wallMillis(): Long = now
        override fun monotonicNanos(): Long = 0
    }
    private val http = MetadataHttp(OkHttpClient())
    private val settings = MetadataSettings(secrets, languages, { "en" }, Dispatchers.IO)
    private val service = MetadataService(
        db, settings, TmdbClient(http, server.url("/3/")), TvmazeClient(http, server.url("/tvmaze/")), clock, Dispatchers.IO,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    @After
    fun close() {
        server.close()
        db.close()
    }

    private fun json(body: String, code: Int = 200) = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private val dune = """{"results":[{"id":438631,"title":"Dune","release_date":"2021-09-15","genre_ids":[878],"popularity":90},{"id":841,"title":"Dune","release_date":"1984-12-14","popularity":20}]}"""

    @Test
    fun aMatchIsStoredAndAnsweredFromMemoryAfterwards() = runBlocking {
        answer = { json(dune) }
        val request = MetadataRequest(MediaType.MOVIE, "FIN | Dune (2021) 4K")
        assertEquals("438631", service.enrich(request)?.externalId)
        assertEquals(1, calls.get())
        assertEquals("438631", service.cached(request)?.externalId)
        assertEquals("438631", service.enrich(request)?.externalId)
        assertEquals(1, calls.get())
        // The durable row answers a fresh service too.
        assertTrue(service.catalogue(request) is CatalogueOutcome.Matched)
        assertEquals(1, calls.get())
    }

    @Test
    fun aMissIsRememberedButAFailureIsNot() = runBlocking {
        answer = { json("""{"results":[]}""") }
        assertEquals(CatalogueOutcome.NoMatch, service.catalogue(MetadataRequest(MediaType.MOVIE, "Quiet Harbour")))
        assertEquals(CatalogueOutcome.NoMatch, service.catalogue(MetadataRequest(MediaType.MOVIE, "Quiet Harbour")))
        assertEquals(1, calls.get())
        answer = { json("{}", code = 500) }
        assertEquals(CatalogueOutcome.Retry, service.catalogue(MetadataRequest(MediaType.MOVIE, "Silent River")))
        assertEquals(CatalogueOutcome.Retry, service.catalogue(MetadataRequest(MediaType.MOVIE, "Silent River")))
        assertEquals(3, calls.get())
    }

    @Test
    fun identicalLookupsShareOneRequest() = runBlocking {
        answer = {
            Thread.sleep(300)
            json(dune)
        }
        val results = (1..5).map { async(Dispatchers.IO) { service.enrich(MetadataRequest(MediaType.MOVIE, "Dune (2021)")) } }.awaitAll()
        assertEquals(setOf("438631"), results.map { it?.externalId }.toSet())
        assertEquals(1, calls.get())
    }

    @Test
    fun nothingIsRequestedWithMetadataOff() = runBlocking {
        secrets.values.remove(ServiceKeys.TMDB_ENABLED)
        settings.reload()
        assertNull(service.enrich(MetadataRequest(MediaType.MOVIE, "Dune")))
        assertEquals(0, calls.get())
    }

    @Test
    fun aPinAnswersBeforeAnySearchForEveryCopy() = runBlocking {
        answer = { r ->
            if (r.url.encodedPath.endsWith("/movie/841")) json("""{"id":841,"title":"Dune","release_date":"1984-12-14","runtime":137}""") else json(dune)
        }
        db.movieImport().insert(
            listOf(
                com.sohva.tv.core.data.database.MovieEntity(
                    key = "vod:movie:s:1", sourceId = "s", providerId = "1", groupId = null, name = "Dune", sortName = "dune", year = null, rating = null,
                    ratingX10 = null, posterUrl = null, streamUrlEnc = "e", plot = null, providerOrder = 0, genre = null, workKey = "name:dune:",
                    primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
                ),
                com.sohva.tv.core.data.database.MovieEntity(
                    key = "vod:movie:t:2", sourceId = "t", providerId = "2", groupId = null, name = "Dune", sortName = "dune", year = null, rating = null,
                    ratingX10 = null, posterUrl = null, streamUrlEnc = "e", plot = null, providerOrder = 0, genre = null, workKey = "name:dune:",
                    primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
                ),
            ),
        )
        val request = MetadataRequest(MediaType.MOVIE, "Dune", contentKey = "vod:movie:s:1")
        val chosen = service.search(MediaType.MOVIE, "Dune").first { it.externalId == "841" }
        val pinned = service.pin("vod:movie:s:1", "name:dune:", request, chosen)
        assertEquals(137, pinned.runtimeMinutes)
        assertTrue(service.isPinned("vod:movie:s:1"))
        val other = service.catalogue(MetadataRequest(MediaType.MOVIE, "Dune", contentKey = "vod:movie:t:2"))
        assertEquals("841", (other as CatalogueOutcome.Matched).record.externalId)
        service.unpin("vod:movie:s:1", "name:dune:", request)
        assertTrue(!service.isPinned("vod:movie:s:1"))
    }
}
