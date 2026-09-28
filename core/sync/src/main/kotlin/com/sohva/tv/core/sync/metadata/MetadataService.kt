package com.sohva.tv.core.sync.metadata

import com.sohva.tv.core.data.database.MetadataCacheEntity
import com.sohva.tv.core.data.database.MetadataPinEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.metadata.MetadataConfig
import com.sohva.tv.core.data.metadata.MetadataSettings
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.metadata.Lookup
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.model.metadata.Matcher
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.core.net.metadata.MetadataProvider
import com.sohva.tv.core.net.metadata.MetadataRecord
import com.sohva.tv.core.net.metadata.TmdbClient
import com.sohva.tv.core.net.metadata.TmdbCredential
import com.sohva.tv.core.net.metadata.TvmazeClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/** What a catalogue lookup settled (spec 41 META-FR-59). */
sealed interface CatalogueOutcome {
    data class Matched(val record: MetadataRecord) : CatalogueOutcome

    /** Every enabled provider that supports the type holds a fresh negative answer: a real miss. */
    data object NoMatch : CatalogueOutcome

    /** A provider failed: nothing was written, try later. */
    data object Retry : CatalogueOutcome
}

/**
 * Every metadata lookup (spec 41 §4.6–4.7): sanitise, share identical requests, answer from the
 * memory cache (256 entries), then per provider in order a pin, a fresh database row, or a search
 * scored by the conservative [Matcher]. A provider failure writes nothing; a miss writes a
 * negative row (7 days); a match a positive one (TMDB 30 days, TVmaze 24 h). Screens call
 * [enrich] and [filmDetails] and ignore failures; the worker calls [catalogue]. Network and
 * parsing run on [io]; nothing here touches the main thread.
 */
class MetadataService(
    private val db: SohvaDatabase,
    private val settings: MetadataSettings,
    private val tmdb: TmdbClient,
    private val tvmaze: TvmazeClient,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {
    private val dao get() = db.metadata()
    private val memory = object : LinkedHashMap<String, Memo>(MEMORY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Memo>): Boolean = size > MEMORY
    }
    private val inflight = HashMap<String, Shared>()

    private class Memo(val record: MetadataRecord?, val expiresAt: Long)

    /** A request several callers wait for; the last one to leave cancels it (META-FR-32). */
    private class Shared(val deferred: Deferred<MetadataRecord?>, var waiting: Int)

    /** What the memory cache holds for [request], for a page's first frame (META-FR-30). */
    fun cached(request: MetadataRequest): MetadataRecord? {
        val language = settings.config.value?.language ?: return null
        val s = Lookups.sanitise(request, language) ?: return null
        return synchronized(memory) { memory[s.key]?.takeIf { it.expiresAt > clock.wallMillis() }?.record }
    }

    /** The ordinary lookup (META-FR-29); null when nothing is known or metadata is off. */
    suspend fun enrich(request: MetadataRequest): MetadataRecord? {
        val config = settings.current()
        if (!config.enabled) return null
        val s = Lookups.sanitise(request, config.language) ?: return null
        synchronized(memory) { memory[s.key]?.takeIf { it.expiresAt > clock.wallMillis() } }?.let { return it.record }
        return shared("lookup:${s.key}") { resolve(request, s, config).let { (it as? CatalogueOutcome.Matched)?.record } }
    }

    /**
     * A film page's lookup (META-FR-37): the ordinary answer, then, when it came from TMDB and
     * details were not loaded yet, `movie/{id}` with cast and similar titles in place of it.
     */
    suspend fun filmDetails(request: MetadataRequest): MetadataRecord? {
        val found = enrich(request.copy(type = MediaType.MOVIE)) ?: return null
        // A record cached before the IMDb id was kept is fetched again once (spec 40 VOD-FR-112).
        if ((found.detailsLoaded && found.imdb != null) || found.provider != MetadataProvider.TMDB) return found
        val config = settings.current()
        val credential = TmdbCredential.of(config.credential) ?: return found
        val s = Lookups.sanitise(request.copy(type = MediaType.MOVIE), config.language) ?: return found
        return shared("details:${s.key}") {
            val details = runCatching { tmdb.movie(found.externalId, s.language, credential) }.getOrNull() ?: return@shared found
            store(s, MetadataProvider.TMDB, details)
            details
        }
    }

    /**
     * A TMDB record by id in the metadata language (spec 51 FR-29, spec 02 HOME-FR-69), cached per
     * id, type and language; null when metadata or TMDB is off.
     */
    suspend fun tmdbById(type: MediaType, id: String): MetadataRecord? {
        val config = settings.current()
        if (!config.enabled || TmdbCredential.of(config.credential) == null) return null
        return shared("by-id:${type.name}:$id:${config.language.lowercase()}") { byId(MetadataProvider.TMDB, type, id, config) }
    }

    /**
     * Backdrops for matches made before matches kept one (decision "Library card art"): for each
     * TMDB match of [wanted] (content key → type) without a backdrop, the record by id (its cache,
     * else one request) and the backdrop stored with the match. True when one was stored.
     */
    suspend fun fillBackdrops(wanted: Map<String, MediaType>): Boolean {
        if (wanted.isEmpty()) return false
        val matches = withContext(io) { dao.matchesOf(wanted.keys.toList()) }
            .filter { it.status == "matched" && it.provider == MetadataProvider.TMDB.id && it.backdrop == null && !it.externalId.isNullOrBlank() }
        var stored = false
        for (match in matches) {
            val type = wanted[match.contentKey] ?: continue
            val backdrop = tmdbById(type, match.externalId ?: continue)?.backdrop ?: continue
            withContext(io) { dao.setBackdrop(match.contentKey, backdrop) }
            stored = true
        }
        return stored
    }

    /** A series page's lookup with details (META-FR-34 does the details inside the search). */
    suspend fun seriesDetails(request: MetadataRequest): MetadataRecord? {
        val found = enrich(request.copy(type = MediaType.SERIES)) ?: return null
        // A TMDB record cached before the aired seasons and IMDb id were kept: `tv/{id}` once more
        // (spec 40 VOD-FR-112, -113), then it is cached like any other.
        if (found.provider != MetadataProvider.TMDB || found.airedSeasons != null) return found
        val config = settings.current()
        val credential = TmdbCredential.of(config.credential) ?: return found
        val s = Lookups.sanitise(request.copy(type = MediaType.SERIES), config.language) ?: return found
        return shared("details:${s.key}") {
            val details = runCatching { tmdb.series(found.externalId, s.language, credential) }.getOrNull() ?: return@shared found
            store(s, MetadataProvider.TMDB, details)
            details
        }
    }

    /** The selected episode (META-FR-35): the series chosen first, then the episode by number. */
    suspend fun episode(series: MetadataRequest, season: Int, episode: Int): MetadataRecord? =
        enrich(series.copy(type = MediaType.EPISODE, season = season, episode = episode))

    /** A title for the background worker (META-FR-59): the durable cache decides, the memory cache is bypassed. */
    suspend fun catalogue(request: MetadataRequest): CatalogueOutcome {
        val config = settings.current()
        if (!config.enabled) return CatalogueOutcome.NoMatch
        val s = Lookups.sanitise(request, config.language) ?: return CatalogueOutcome.NoMatch
        return resolve(request, s, config)
    }

    /**
     * The match picker's search (META-FR-74): providers in order, the first non-empty list wins,
     * results unscored and unfiltered (a person decides), at most 12.
     */
    suspend fun search(type: MediaType, query: String): List<MetadataRecord> = withContext(io) {
        val config = settings.current()
        val q = query.trim().take(160)
        if (q.isEmpty()) return@withContext emptyList()
        for (provider in providers(config, type)) {
            val found = runCatching { searchProvider(provider, type, q, config) }.getOrDefault(emptyList())
            if (found.isNotEmpty()) return@withContext found.take(12)
        }
        emptyList()
    }

    /**
     * Pins [record] for [contentKey] and every copy of its film identity (META-FR-75, spec 41 Q4):
     * fetched again by id from its own provider in the current language, stored as the answer for
     * the lookup, and returned.
     */
    suspend fun pin(contentKey: String, workKey: String?, request: MetadataRequest, record: MetadataRecord): MetadataRecord = withContext(io) {
        val config = settings.current()
        val full = byId(record.provider, record.type, record.externalId, config) ?: record
        dao.putPin(MetadataPinEntity(contentKey, workKey, record.provider.id, record.externalId, record.type.wire, clock.wallMillis()))
        Lookups.sanitise(request, config.language)?.let { s -> remember(s.key, full, Long.MAX_VALUE) }
        full
    }

    /** "Undo my choice" (META-FR-77): the pin and the lookup's cached answers go. */
    suspend fun unpin(contentKey: String, workKey: String?, request: MetadataRequest): Unit = withContext(io) {
        val config = settings.current()
        db.runInTransaction {
            dao.deletePin(contentKey)
            workKey?.let(dao::deletePinsOfWork)
            Lookups.sanitise(request, config.language)?.let { dao.deleteCacheKey(it.key) }
        }
        val s = Lookups.sanitise(request, config.language) ?: return@withContext
        synchronized(memory) { memory.remove(s.key) }
    }

    /**
     * "Test TMDB" (META-FR-06): `configuration` with the typed credential, never the saved one.
     * An unreadable credential is invalid; a network or HTTP failure is its plain-language error.
     */
    suspend fun testCredential(raw: String): Outcome<Unit> = withContext(io) {
        val credential = TmdbCredential.of(raw.trim()) ?: return@withContext Outcome.Failed(AppError.TmdbKeyInvalid)
        try {
            tmdb.check(credential)
            Outcome.Ok(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppException) {
            Outcome.Failed(e.error)
        }
    }

    suspend fun isPinned(contentKey: String): Boolean = withContext(io) { dao.pin(contentKey) != null }

    /** "Save key" with a changed credential (META-FR-05, rebuild): only the misses are forgotten. */
    suspend fun credentialChanged(): Unit = withContext(io) {
        dao.deleteNegativeCache()
        synchronized(memory) { memory.clear() }
    }

    /** Language change and cache clear empty the memory caches (META-FR-79, -80). */
    fun forgetMemory() {
        synchronized(memory) { memory.clear() }
    }

    // ---- The lookup flow ----------------------------------------------------------------------

    private suspend fun resolve(request: MetadataRequest, s: SanitisedLookup, config: MetadataConfig): CatalogueOutcome = withContext(io) {
        pinned(request, config)?.let { record ->
            remember(s.key, record, Long.MAX_VALUE)
            return@withContext CatalogueOutcome.Matched(record)
        }
        val eligible = providers(config, s.lookup.type)
        if (eligible.isEmpty()) return@withContext CatalogueOutcome.NoMatch
        var misses = 0
        val now = clock.wallMillis()
        for (provider in eligible) {
            val row = dao.cached(s.key, provider.id)
            if (row != null && row.expiresAt > now) {
                if (row.status == NEGATIVE) {
                    misses++
                    continue
                }
                // A positive row written under an older genre vocabulary counts as missing (META-FR-29).
                if (row.genresVersion == Genre.VERSION) {
                    val record = row.payload?.let(RecordCodec::decode)
                    if (record != null) {
                        remember(s.key, record, row.expiresAt)
                        return@withContext CatalogueOutcome.Matched(record)
                    }
                }
            }
            val found = try {
                lookupProvider(provider, s, config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppException) {
                lastFailure = e
                null
            } ?: continue
            if (found is Found.None) {
                dao.putCache(MetadataCacheEntity(s.key, provider.id, NEGATIVE, null, null, Genre.VERSION, false, now, now + NEGATIVE_MS))
                misses++
                continue
            }
            val record = (found as Found.Record).record
            store(s, provider, record)
            return@withContext CatalogueOutcome.Matched(record)
        }
        if (misses == eligible.size) CatalogueOutcome.NoMatch else CatalogueOutcome.Retry
    }

    private sealed interface Found {
        data class Record(val record: MetadataRecord) : Found
        data object None : Found
    }

    private suspend fun lookupProvider(provider: MetadataProvider, s: SanitisedLookup, config: MetadataConfig): Found {
        val lookup = s.lookup
        if (lookup.type == MediaType.EPISODE) {
            val season = lookup.season ?: return Found.None
            val number = lookup.episode ?: return Found.None
            val show = choose(provider, lookup.copy(type = MediaType.SERIES, season = null, episode = null), config, s.language) ?: return Found.None
            val episode = when (provider) {
                MetadataProvider.TMDB -> tmdb.episode(show, season, number, s.language, TmdbCredential.of(config.credential)!!)
                MetadataProvider.TVMAZE -> tvmaze.episode(show, season, number)
            }
            return episode?.let(Found::Record) ?: Found.None
        }
        return choose(provider, lookup, config, s.language)?.let(Found::Record) ?: Found.None
    }

    /** Search, score and pick (META-FR-33, -34); TMDB series get their details and are scored again. */
    private suspend fun choose(provider: MetadataProvider, lookup: Lookup, config: MetadataConfig, language: String): MetadataRecord? {
        val results = searchProvider(provider, lookup.type, lookup.title, config, language)
        val picked = Matcher.choose(lookup, results.map { it.candidate() }) ?: return null
        val record = results.first { it.externalId == picked.candidate.externalId && it.type == picked.candidate.type }
        if (provider != MetadataProvider.TMDB || lookup.type != MediaType.SERIES) return record
        val credential = TmdbCredential.of(config.credential) ?: return record
        val details = runCatching { tmdb.series(record.externalId, language, credential) }.getOrNull() ?: return record
        val list = results.map { if (it.externalId == details.externalId) details else it }
        val again = Matcher.choose(lookup, list.map { it.candidate() }) ?: return null
        return list.first { it.externalId == again.candidate.externalId }
    }

    private suspend fun searchProvider(provider: MetadataProvider, type: MediaType, query: String, config: MetadataConfig, language: String = config.language): List<MetadataRecord> =
        when (provider) {
            MetadataProvider.TMDB -> tmdb.search(type, query, language, TmdbCredential.of(config.credential) ?: return emptyList())
            MetadataProvider.TVMAZE -> tvmaze.search(query)
        }

    /** A pinned record (META-FR-76): by content key, else by any copy of the same film identity. */
    private suspend fun pinned(request: MetadataRequest, config: MetadataConfig): MetadataRecord? {
        val key = request.contentKey ?: return null
        val pin = dao.pin(key) ?: dao.workKeyOf(key)?.let(dao::pinOfWork) ?: return null
        val provider = MetadataProvider.of(pin.provider) ?: return null
        val type = MediaType.entries.firstOrNull { it.wire == pin.mediaType } ?: return null
        return byId(provider, type, pin.externalId, config)
    }

    /** A record by id from its provider, cached 30 days under `by-id:<TYPE>:<provider>:<id>:<language>` (META-FR-45). */
    private suspend fun byId(provider: MetadataProvider, type: MediaType, id: String, config: MetadataConfig): MetadataRecord? {
        val cacheKey = "by-id:${type.name}:${provider.id}:$id:${config.language.lowercase()}"
        val now = clock.wallMillis()
        dao.cached(cacheKey, provider.id)?.takeIf { it.expiresAt > now && it.genresVersion == Genre.VERSION }?.payload?.let(RecordCodec::decode)?.let { return it }
        val record = runCatching {
            when (provider) {
                MetadataProvider.TMDB -> {
                    val credential = TmdbCredential.of(config.credential) ?: return null
                    if (type == MediaType.MOVIE) tmdb.movie(id, config.language, credential) else tmdb.series(id, config.language, credential)
                }
                // TVmaze has no fetch by id here; the search result that was chosen stands.
                MetadataProvider.TVMAZE -> null
            }
        }.getOrNull() ?: return null
        dao.putCache(MetadataCacheEntity(cacheKey, provider.id, POSITIVE, record.externalId, RecordCodec.encode(record), Genre.VERSION, record.detailsLoaded, now, now + TMDB_MS))
        return record
    }

    private fun store(s: SanitisedLookup, provider: MetadataProvider, record: MetadataRecord) {
        val now = clock.wallMillis()
        val expires = now + if (provider == MetadataProvider.TMDB) TMDB_MS else TVMAZE_MS
        dao.putCache(MetadataCacheEntity(s.key, provider.id, POSITIVE, record.externalId, RecordCodec.encode(record), Genre.VERSION, record.detailsLoaded, now, expires))
        remember(s.key, record, expires)
    }

    private fun remember(key: String, record: MetadataRecord?, expiresAt: Long) {
        synchronized(memory) { memory[key] = Memo(record, expiresAt) }
    }

    private fun providers(config: MetadataConfig, type: MediaType): List<MetadataProvider> = buildList {
        if (config.tmdbEnabled && MetadataProvider.TMDB.supports(type)) add(MetadataProvider.TMDB)
        if (config.tvmazeEnabled && MetadataProvider.TVMAZE.supports(type)) add(MetadataProvider.TVMAZE)
    }

    /** The last provider failure, for the worker's "bad key" rule (spec 41 Q8). */
    @Volatile
    var lastFailure: AppException? = null
        private set

    /**
     * One request per key however many callers wait: the request runs in [scope] and is cancelled
     * when its last waiting caller is cancelled (META-FR-32).
     */
    private suspend fun shared(key: String, block: suspend () -> MetadataRecord?): MetadataRecord? {
        val entry = synchronized(inflight) {
            inflight[key]?.also { it.waiting++ } ?: Shared(scope.async(io) { block() }, 1).also { created ->
                inflight[key] = created
                created.deferred.invokeOnCompletion { synchronized(inflight) { if (inflight[key] === created) inflight.remove(key) } }
            }
        }
        return try {
            entry.deferred.await()
        } catch (e: CancellationException) {
            // This caller left; the request stops only when nobody else waits for it.
            synchronized(inflight) {
                entry.waiting--
                if (entry.waiting <= 0) entry.deferred.cancel()
            }
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val MEMORY: Int = 256
        const val POSITIVE: String = "positive"
        const val NEGATIVE: String = "negative"
        const val TMDB_MS: Long = 30L * 24 * 60 * 60 * 1000
        const val TVMAZE_MS: Long = 24L * 60 * 60 * 1000
        const val NEGATIVE_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
