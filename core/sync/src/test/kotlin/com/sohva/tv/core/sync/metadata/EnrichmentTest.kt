package com.sohva.tv.core.sync.metadata

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.metadata.MetadataPreferences
import com.sohva.tv.core.data.metadata.MetadataSettings
import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.metadata.WorkKeys
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.vod.PreferredCopy
import com.sohva.tv.core.net.metadata.MetadataHttp
import com.sohva.tv.core.net.metadata.TmdbClient
import com.sohva.tv.core.net.metadata.TvmazeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 41 §11 "Queue reconciliation" and "Worker": a paged sync, one batch applied, failures backed off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnrichmentTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val query = request.url.queryParameter("query").orEmpty()
                return when {
                    query.startsWith("Heat") -> json("""{"results":[{"id":949,"title":"Heat","release_date":"1995-12-15","poster_path":"/heat.jpg","genre_ids":[80,18]}]}""")
                    query.startsWith("Broken") -> json("{}", 500)
                    else -> json("""{"results":[]}""")
                }
            }
        }
        start()
    }
    private val secrets = object : SecretValues {
        val values = HashMap<String, String>(mapOf(ServiceKeys.TMDB_TOKEN to "token", ServiceKeys.TMDB_ENABLED to "true"))

        override suspend fun read(key: String): Outcome<String?> = Outcome.Ok(values[key])

        override suspend fun write(key: String, value: String?): Outcome<Unit> = Outcome.Ok(Unit)
    }
    private val languages = object : MetadataPreferences {
        override suspend fun metadataLanguage(): String? = "en-US"

        override suspend fun setMetadataLanguage(tag: String) = Unit
    }
    private var now = 1_790_000_000_000L
    private val clock = object : Clock {
        override fun wallMillis(): Long = now
        override fun monotonicNanos(): Long = 0
    }
    private val log = object : DiagnosticsLog {
        override fun info(event: String, message: String) = Unit
        override fun error(event: String, message: String?, error: Throwable?) = Unit
        override fun snapshot(): List<String> = emptyList()
    }
    private val http = MetadataHttp(OkHttpClient())
    private val settings = MetadataSettings(secrets, languages, { "en" }, Dispatchers.IO)
    private val service = MetadataService(
        db, settings, TmdbClient(http, server.url("/3/")), TvmazeClient(http, server.url("/tvmaze/")), clock, Dispatchers.IO,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
    private var paused = false
    private val enrichment = Enrichment(
        db, service, LibraryPasses(db), clock, Dispatchers.IO, log, { PreferredCopy.NONE }, { listOf("s") }, { paused },
    )

    @After
    fun close() {
        server.close()
        db.close()
    }

    private fun json(body: String, code: Int = 200) = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun film(id: String, name: String, year: Int?, poster: String? = null) = MovieEntity(
        key = "vod:movie:s:$id", sourceId = "s", providerId = id, groupId = null, name = name, sortName = SortNames.of(name), year = year,
        rating = null, ratingX10 = null, posterUrl = poster, streamUrlEnc = "e", plot = null, providerOrder = 0, genre = null,
        workKey = WorkKeys.of(name, year), primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
    )

    private fun seed() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "XTREAM", true, 0, 1, "VOD", 0, 0, 0))
        db.movieImport().insert(listOf(film("1", "Heat", 1995), film("2", "Quiet Harbour", 2020), film("3", "Broken Signal", 2001)))
    }

    private fun row(key: String): List<String?> = db.openHelper.readableDatabase.query(
        "SELECT replacement_title, genre, work_key, replacement_poster, replace_poster FROM movie WHERE key = '$key'",
    ).use {
        it.moveToFirst()
        listOf(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getInt(4).toString())
    }

    private fun state(key: String) = db.metadata().queueOf(listOf(key)).single()

    @Test
    fun aRunMatchesMissesAndBacksOffFailures() = runBlocking {
        seed()
        assertEquals(3, enrichment.synchronise())
        assertEquals(RunEnd.DONE, enrichment.run())
        assertEquals(listOf("Heat", "crime", "tmdb:949", "/heat.jpg", "1"), row("vod:movie:s:1"))
        assertEquals("complete", state("vod:movie:s:1").state)
        assertEquals(listOf(null, null, "name:quiet harbour:2020", null, "0"), row("vod:movie:s:2"))
        assertEquals("no_match", state("vod:movie:s:2").state)
        val failed = state("vod:movie:s:3")
        assertEquals("retry", failed.state)
        assertEquals(1, failed.attempts)
        assertEquals(now + 15 * 60_000, failed.nextAttemptAt)
    }

    @Test
    fun aSecondSyncKeepsSettledTitlesAndSweepsTheGoneOnes() = runBlocking {
        seed()
        enrichment.synchronise()
        enrichment.run()
        db.movieImport().delete(listOf(db.openHelper.readableDatabase.query("SELECT id FROM movie WHERE key = 'vod:movie:s:2'").use { it.moveToFirst(); it.getLong(0) }))
        now += 1_000
        assertEquals(0, enrichment.synchronise())
        assertEquals("complete", state("vod:movie:s:1").state)
        assertEquals("retry", state("vod:movie:s:3").state)
        assertEquals(emptyList<Any>(), db.metadata().queueOf(listOf("vod:movie:s:2")))
    }

    @Test
    fun nothingRunsWhileVideoPlaysOrTheAppIsInFront() = runBlocking {
        seed()
        enrichment.synchronise()
        paused = true
        assertEquals(RunEnd.STOPPED, enrichment.run())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun retryDelaysDoubleToADay() {
        assertEquals(listOf(15L, 30, 60, 120, 240, 480, 960, 1440, 1440), (1..9).map { Enrichment.retryDelay(it) / 60_000 })
    }
}
