package com.sohva.tv.core.sync.metadata

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.MetadataQueueEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.metadata.MetadataLanguageStore
import com.sohva.tv.core.data.metadata.MetadataSettings
import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.model.metadata.WorkKeys
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.core.model.vod.PreferredCopy
import com.sohva.tv.core.net.metadata.MetadataHttp
import com.sohva.tv.core.net.metadata.MetadataProvider
import com.sohva.tv.core.net.metadata.MetadataRecord
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 41 §11 "Match picker": a choice is fetched by id and written to every copy; undo gives the title back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MatchChoicesTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/movie/949") -> MockResponse.Builder().addHeader("Content-Type", "application/json")
                    .body("""{"id":949,"title":"Heat","release_date":"1995-12-15","poster_path":"/heat.jpg","genres":[{"id":80}],"runtime":170}""").build()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        start()
    }
    private val secrets = object : SecretValues {
        override suspend fun read(key: String): Outcome<String?> =
            Outcome.Ok(mapOf(ServiceKeys.TMDB_TOKEN to "token", ServiceKeys.TMDB_ENABLED to "true")[key])

        override suspend fun write(key: String, value: String?): Outcome<Unit> = Outcome.Ok(Unit)
    }
    private val languages = object : MetadataLanguageStore {
        override suspend fun metadataLanguage(): String? = "en-US"

        override suspend fun setMetadataLanguage(tag: String) = Unit
    }
    private val clock = object : Clock {
        override fun wallMillis(): Long = 1_790_000_000_000L
        override fun monotonicNanos(): Long = 0
    }
    private val http = MetadataHttp(OkHttpClient())
    private val service = MetadataService(
        db, MetadataSettings(secrets, languages, { "en" }, Dispatchers.IO), TmdbClient(http, server.url("/3/")),
        TvmazeClient(http, server.url("/tvmaze/")), clock, Dispatchers.IO, CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
    private val choices = MatchChoices(db, service, LibraryPasses(db), clock, Dispatchers.IO) { PreferredCopy.NONE }

    @After
    fun close() {
        server.close()
        db.close()
    }

    private fun film(id: String, name: String) = MovieEntity(
        key = "vod:movie:s:$id", sourceId = "s", providerId = id, groupId = null, name = name, sortName = SortNames.of(name), year = 1995,
        rating = null, ratingX10 = null, posterUrl = null, streamUrlEnc = "e", plot = null, providerOrder = 0, genre = null,
        workKey = WorkKeys.of(name, 1995), primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
    )

    private fun seed() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "XTREAM", true, 0, 1, "VOD", 0, 0, 0))
        db.movieImport().insert(listOf(film("1", "FI | Heat (1995)"), film("2", "Heat 4K"), film("3", "Ronin")))
        db.metadata().putQueue(
            listOf("1", "2").map { MetadataQueueEntity("vod:movie:s:$it", "movie", "Heat", 1995, Genre.VERSION, "no_match", 0, 0, 2, 1) },
        )
    }

    private fun row(key: String): List<Any?> = db.openHelper.readableDatabase.query(
        "SELECT replacement_title, work_key, genre, replacement_poster, replace_poster FROM movie WHERE key = '$key'",
    ).use {
        it.moveToFirst()
        listOf(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getInt(4))
    }

    private val target = ChoiceTarget("vod:movie:s:1", MetadataRequest(MediaType.MOVIE, "FI | Heat (1995)", 1995, contentKey = "vod:movie:s:1"))

    @Test
    fun aChoiceIsFetchedByIdAndWrittenToEveryCopy() = runBlocking {
        seed()
        val picked = MetadataRecord(MetadataProvider.TMDB, "949", MediaType.MOVIE, "Heat (search)", year = 1995)
        val chosen = choices.choose(target, picked)
        assertEquals("Heat", chosen.title)
        assertEquals(170, chosen.runtimeMinutes)
        for (key in listOf("vod:movie:s:1", "vod:movie:s:2")) {
            assertEquals(listOf("Heat", "tmdb:949", "crime", "/heat.jpg", 1), row(key))
            assertEquals("complete", db.metadata().queueOf(listOf(key)).single().state)
        }
        assertEquals("name:ronin:1995", row("vod:movie:s:3")[1])
        assertTrue(choices.isPinned(target))
        // The pin follows the new identity, so the other copy's lookup finds it too.
        assertEquals("tmdb:949", db.metadata().pin("vod:movie:s:1")!!.workKey)
        assertEquals("Heat", service.enrich(MetadataRequest(MediaType.MOVIE, "Heat 4K", 1995, contentKey = "vod:movie:s:2"))?.title)
    }

    @Test
    fun undoGivesTheTitleBackToAutomaticMatchingFirst() = runBlocking {
        seed()
        choices.choose(target, MetadataRecord(MetadataProvider.TMDB, "949", MediaType.MOVIE, "Heat", year = 1995))
        choices.undo(target)
        assertFalse(choices.isPinned(target))
        assertEquals(listOf(null, "name:heat:1995", null, null, 0), row("vod:movie:s:1"))
        val queued = db.metadata().queueOf(listOf("vod:movie:s:1")).single()
        assertEquals(listOf("pending", 0), listOf(queued.state, queued.priority))
    }
}
