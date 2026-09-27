package com.sohva.tv.feature.trakt.scrobble

import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.protocol.ScrobbleAction
import com.sohva.tv.feature.trakt.protocol.TraktException
import com.sohva.tv.feature.trakt.protocol.TraktFailure
import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktItem
import com.sohva.tv.feature.trakt.protocol.TraktJson
import com.sohva.tv.feature.trakt.protocol.fields
import com.sohva.tv.feature.trakt.protocol.items
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer

/** One report waiting to go (spec 51 §6 `pending:<profile>`). */
data class PendingScrobble(val item: TraktItem, val action: ScrobbleAction, val progress: Double)

/**
 * The profile's scrobble queue (spec 51 FR-19, -20, §8): persisted, collapsed to the latest
 * report per title, delivered in order and stopped at the first outage; a report Trakt rejects
 * for good is dropped. Kept (bounded) while Trakt wants a new sign-in, and delivered once the
 * same account signs in again. Nothing is sent from a build without credentials, the demo build,
 * a profile without an account or a restricted profile.
 */
class TraktScrobbles(private val host: TraktHost) {
    private val lock = Mutex()
    private val _stops = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Profiles whose STOP was just delivered: the sync loop runs 3 s later (FR-21). */
    val stops: SharedFlow<String> = _stops.asSharedFlow()

    /** The in-flight START's percentage, kept in memory and written on pause or every few minutes (§9 rule). */
    private var activePercent: Pair<String, Double>? = null
    private var activeWrittenAt = 0L

    fun submit(profile: String, item: TraktItem, action: ScrobbleAction, progress: Double): Job =
        host.appScope.launch(host.dispatchers.io) {
            val account = host.account(profile) ?: return@launch
            if (!host.configured || !host.access.allowed(profile)) return@launch
            lock.withLock {
                val list = load(profile).filterNot { TraktItems.key(it.item) == TraktItems.key(item) } + PendingScrobble(item, action, progress)
                save(profile, list.takeLast(MAX_PENDING))
                when (action) {
                    ScrobbleAction.START -> setActive(profile, item, progress)
                    ScrobbleAction.PAUSE, ScrobbleAction.STOP -> clearActive(profile)
                }
            }
            if (!account.reauthorize) deliver(profile)
        }

    /** FR-20: the percentage of a title still playing; written at most every [ACTIVE_WRITE_MS]. */
    fun active(profile: String, progress: Double) {
        activePercent = profile to progress
        val now = host.clock.wallMillis()
        if (now - activeWrittenAt < ACTIVE_WRITE_MS) return
        activeWrittenAt = now
        host.appScope.launch(host.dispatchers.io) {
            lock.withLock {
                val (item, _) = readActive(profile) ?: return@withLock
                writeActive(profile, item, progress)
            }
        }
    }

    /**
     * Sends what is queued, in order (FR-19): delivered and 409 leave the queue; a permanent
     * rejection is dropped; an outage stops delivery and keeps the rest. Also called by each
     * sync cycle (§8 rebuild rule).
     */
    suspend fun deliver(profile: String) {
        if (!host.configured) return
        lock.withLock {
            var list = load(profile)
            var stopDelivered = false
            while (list.isNotEmpty()) {
                val next = list.first()
                try {
                    host.withTokens(profile) { tokens -> host.api.scrobble(tokens.access, next.item, next.action, next.progress) }
                    if (next.action == ScrobbleAction.STOP) stopDelivered = true
                    list = list.drop(1)
                    host.log.info("trakt", "scrobble ${next.action.path}: sent")
                } catch (e: TraktException) {
                    if (e.failure == TraktFailure.REJECTED || e.failure == TraktFailure.INVALID_RESPONSE || e.failure == TraktFailure.NOT_FOUND) {
                        host.log.info("trakt", "scrobble ${next.action.path}: rejected")
                        list = list.drop(1)
                    } else {
                        host.log.info("trakt", "scrobble ${next.action.path}: kept (${e.failure.name.lowercase()})")
                        break
                    }
                }
            }
            save(profile, list)
            if (stopDelivered) _stops.tryEmit(profile)
        }
    }

    /** FR-20: the app was killed while a title played; Trakt is told it paused, before anything else. */
    suspend fun closeInterrupted(profile: String) {
        val interrupted = lock.withLock { withContext(host.dispatchers.io) { readActive(profile) } } ?: return
        submitNow(profile, interrupted.first, ScrobbleAction.PAUSE, interrupted.second)
    }

    private suspend fun submitNow(profile: String, item: TraktItem, action: ScrobbleAction, progress: Double) {
        withContext(host.dispatchers.io) {
            lock.withLock {
                val list = load(profile).filterNot { TraktItems.key(it.item) == TraktItems.key(item) } + PendingScrobble(item, action, progress)
                save(profile, list.takeLast(MAX_PENDING))
                clearActive(profile)
            }
        }
        deliver(profile)
    }

    // ---- Persistence (encrypted, §6) ----

    fun load(profile: String): List<PendingScrobble> {
        val text = host.store.secret("pending:$profile") ?: return emptyList()
        return runCatching {
            val out = ArrayList<PendingScrobble>()
            TraktJson.parse(Buffer().writeUtf8(text)) { j -> j.items { readEntry(j)?.let(out::add) } }
            out
        }.getOrDefault(emptyList())
    }

    private fun save(profile: String, list: List<PendingScrobble>) {
        host.store.putSecret("pending:$profile", if (list.isEmpty()) null else TraktJson.write { beginArray(); list.forEach { writeEntry(it.item, it.action, it.progress) }; endArray() })
    }

    private fun setActive(profile: String, item: TraktItem, progress: Double) {
        activePercent = profile to progress
        activeWrittenAt = host.clock.wallMillis()
        writeActive(profile, item, progress)
    }

    private fun writeActive(profile: String, item: TraktItem, progress: Double) =
        host.store.putSecret("active:$profile", TraktJson.write { writeEntry(item, ScrobbleAction.START, progress) })

    private fun clearActive(profile: String) {
        if (activePercent?.first == profile) activePercent = null
        host.store.putSecret("active:$profile", null)
    }

    private fun readActive(profile: String): Pair<TraktItem, Double>? {
        val text = host.store.secret("active:$profile") ?: return null
        val entry = runCatching { TraktJson.parse(Buffer().writeUtf8(text)) { j -> readEntry(j) } }.getOrNull() ?: return null
        val percent = activePercent?.takeIf { it.first == profile }?.second ?: entry.progress
        return entry.item to percent
    }

    private fun JsonWriter.writeEntry(item: TraktItem, action: ScrobbleAction, progress: Double) {
        beginObject()
        val ids: TraktIds = when (item) {
            is TraktItem.Movie -> {
                name("kind").value("movie")
                item.ids
            }
            is TraktItem.Episode -> {
                name("kind").value("episode").name("season").value(item.season.toLong()).name("number").value(item.number.toLong())
                item.show
            }
        }
        ids.trakt?.let { name("trakt").value(it) }
        ids.tmdb?.let { name("tmdb").value(it) }
        ids.imdb?.let { name("imdb").value(it) }
        ids.tvdb?.let { name("tvdb").value(it) }
        name("action").value(action.path).name("progress").value(progress)
        endObject()
    }

    private fun readEntry(j: JsonReader): PendingScrobble? {
        var kind: String? = null
        var trakt: Long? = null
        var tmdb: Long? = null
        var imdb: String? = null
        var tvdb: Long? = null
        var season: Int? = null
        var number: Int? = null
        var action: String? = null
        var progress = 0.0
        j.fields { f ->
            when (f) {
                "kind" -> kind = j.nextString()
                "trakt" -> trakt = j.nextLong()
                "tmdb" -> tmdb = j.nextLong()
                "imdb" -> imdb = j.nextString()
                "tvdb" -> tvdb = j.nextLong()
                "season" -> season = j.nextInt()
                "number" -> number = j.nextInt()
                "action" -> action = j.nextString()
                "progress" -> progress = j.nextDouble()
                else -> j.skipValue()
            }
        }
        val ids = TraktIds(trakt, tmdb, imdb, tvdb).takeIf { it.any } ?: return null
        val item = when (kind) {
            "movie" -> TraktItem.Movie(ids)
            "episode" -> TraktItem.Episode(ids, season ?: return null, number ?: return null)
            else -> return null
        }
        val a = ScrobbleAction.entries.firstOrNull { it.path == action } ?: return null
        return PendingScrobble(item, a, progress)
    }

    companion object {
        const val MAX_PENDING: Int = 200
        const val ACTIVE_WRITE_MS: Long = 3L * 60 * 1000
    }
}
