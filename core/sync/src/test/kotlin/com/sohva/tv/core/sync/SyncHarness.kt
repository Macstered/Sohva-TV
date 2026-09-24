package com.sohva.tv.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.data.source.SourceStore
import com.sohva.tv.core.model.concurrent.AppDispatchers
import com.sohva.tv.core.model.concurrent.PauseGate
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.net.http.ProviderHttp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/** A database, a provider server and a runner wired like the app, with real background threads. */
/**
 * [parseThreads] is 1 in the app; a test of the per-kind lock uses 2, so that nothing but the lock
 * keeps two imports of one source apart.
 */
class SyncHarness(parseThreads: Int = 1) : AutoCloseable {
    val server = MockWebServer()
    private val responses = ConcurrentHashMap<String, () -> MockResponse>()
    val requests: MutableList<String> = CopyOnWriteArrayList()

    val db: SohvaDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java).build()

    class TestClock : Clock {
        @Volatile var now: Long = System.currentTimeMillis() / 60_000 * 60_000
        override fun wallMillis(): Long = now
        override fun monotonicNanos(): Long = System.nanoTime()
    }

    val clock = TestClock()

    private val executors = listOf(Executors.newFixedThreadPool(parseThreads), Executors.newSingleThreadExecutor(), Executors.newFixedThreadPool(4))
    private val threads = object : AppDispatchers {
        override val main: CoroutineDispatcher = executors[2].asCoroutineDispatcher()
        override val ui: CoroutineDispatcher = executors[2].asCoroutineDispatcher()
        override val io: CoroutineDispatcher = executors[2].asCoroutineDispatcher()
        override val bulk: CoroutineDispatcher = executors[0].asCoroutineDispatcher()
        override val bulkWrite: CoroutineDispatcher = executors[1].asCoroutineDispatcher()
    }

    val playing = MutableStateFlow(false)
    val log = object : DiagnosticsLog {
        val lines = CopyOnWriteArrayList<String>()
        override fun info(event: String, message: String) {
            lines += "$event $message"
        }
        override fun error(event: String, message: String?, error: Throwable?) {
            lines += "$event $message $error"
        }
        override fun snapshot(): List<String> = lines.toList()
    }

    class MapSecrets : SecretValues {
        val values = ConcurrentHashMap<String, String>()
        override suspend fun read(key: String): Outcome<String?> = Outcome.Ok(values[key])
        override suspend fun write(key: String, value: String?): Outcome<Unit> {
            if (value == null) values.remove(key) else values[key] = value
            return Outcome.Ok(Unit)
        }
    }

    val secrets = MapSecrets()

    val sources = SourceStore(db.sources(), secrets, clock)
    private val scope = CoroutineScope(SupervisorJob() + threads.io)

    val env = ImportEnvironment(
        db = db,
        http = ProviderHttp(ProviderHttp.client("Sohva TV/test (Android TV 11)"), log),
        sealer = StreamSealer { "sealed:$it" },
        clock = clock,
        dispatchers = threads,
        pauseGate = PauseGate(playing, MutableStateFlow(false)),
        log = log,
        names = object : FallbackNames {
            override fun channel(number: Int) = "Channel $number"
            override fun episode(number: Int) = "Episode $number"
        },
    )
    val runner = ImportRunner(env, sources, scope)

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                requests += path
                return responses[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
    }

    fun serve(path: String, body: String, code: Int = 200) {
        responses[path] = { MockResponse.Builder().code(code).body(body).build() }
    }

    fun serve(path: String, response: () -> MockResponse) {
        responses[path] = response
    }

    fun url(path: String): String = server.url(path).toString()

    fun addM3u(id: String = "m3u-1", playlist: String = "/list.m3u", guide: String? = null, scope: ImportScope = ImportScope.BOTH): SourceConfig {
        val config = SourceConfig(
            Source(id, "Home", SourceType.M3U, importScope = scope),
            SourceSecrets(m3uUrl = if (playlist.startsWith("http")) playlist else url(playlist), xmlTvUrl = guide?.let(::url)),
        )
        return (runBlocking { sources.save(config) } as Outcome.Ok).value
    }

    fun addXtream(id: String = "xtream-1", scope: ImportScope = ImportScope.BOTH): SourceConfig {
        val config = SourceConfig(
            Source(id, "Panel", SourceType.XTREAM, importScope = scope),
            SourceSecrets(xtreamBaseUrl = url("/panel"), xtreamUsername = "viewer", xtreamPassword = "secret"),
        )
        return (runBlocking { sources.save(config) } as Outcome.Ok).value
    }

    fun query(sql: String): List<String> {
        val rows = ArrayList<String>()
        db.openHelper.readableDatabase.query(sql).use { cursor ->
            while (cursor.moveToNext()) rows += (0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "null" }
        }
        return rows
    }

    override fun close() {
        server.close()
        db.close()
        executors.forEach { it.shutdownNow() }
    }
}
