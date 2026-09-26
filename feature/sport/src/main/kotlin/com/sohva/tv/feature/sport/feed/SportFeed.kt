package com.sohva.tv.feature.sport.feed

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportFeedStatus
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.SportServiceState
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.SportsException
import com.sohva.tv.core.model.sport.SportsProblem
import com.sohva.tv.core.model.sport.TodayRules
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.feature.sport.provider.CacheState
import com.sohva.tv.feature.sport.provider.SportDay
import com.sohva.tv.feature.sport.provider.SportDays
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Today's games as every screen sees them (spec 60 §4.2). */
data class FeedState(
    /** Sorted by [TodayRules.ORDER]. */
    val events: List<SportEvent> = emptyList(),
    val zoneId: String = ZoneId.systemDefault().id,
    val loading: Boolean = false,
    /** The last refresh failed as a whole; the list shown is the one before (SPORT-FR-25). */
    val failure: SportsProblem? = null,
    /** Some sports failed; [cause] says why one did (SPORT-FR-25, §7 rebuild). */
    val partial: Boolean = false,
    val cause: SportsProblem? = null,
    val service: SportServiceState? = null,
    val quotas: Map<SportType, Int> = emptyMap(),
    val pollingMinutes: Int = 30,
    val loadedAt: Long = 0,
    /** At least one refresh has finished, so an empty list means no games. */
    val complete: Boolean = false,
) {
    val status: SportFeedStatus
        get() = SportFeedStatus(zoneId, loading && events.isNotEmpty(), failure, service, quotas, pollingMinutes)
}

/**
 * Today's feed for the whole app (spec 60 §4.2): loaded once after start and on every change of
 * the follows or the zone, polled only while Today or the score ticker is on screen and the app is
 * in front (SPORT-FR-27). A refresh shows the saved days first, then asks the network, at most
 * three sports at a time; the list is replaced only by a refresh that finished.
 */
class SportFeed(
    private val repository: SportDays,
    private val follows: Flow<SportFollows>,
    /** The chosen zone, else the TV's own (the app zone). */
    private val zone: Flow<String?>,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val log: DiagnosticsLog,
    /** False in the Lab build: nothing refreshes by itself (SPORT-FR-29). */
    private val automatic: Boolean,
) {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var pollJob: Job? = null

    /** The last load or failure: the next poll comes one interval after it (SPORT-FR-27). */
    @Volatile private var attemptedAt = 0L
    private var visible = false
    private var started = false
    private var watchJob: Job? = null

    /** After Home's first read (SPORT-FR-29): one load, then a new one whenever follows or the zone change. */
    fun start() {
        if (started || !automatic) return
        started = true
        refresh()
        watchJob = scope.launch {
            combine(follows, zone) { f, z -> f to z }.distinctUntilChanged().drop(1).collect { refresh() }
        }
    }

    /** Tests start from an empty feed: the list, its jobs and its visibility are forgotten. */
    fun forgetForTests() {
        refreshJob?.cancel()
        pollJob?.cancel()
        watchJob?.cancel()
        started = false
        visible = false
        attemptedAt = 0L
        _state.value = FeedState()
    }

    /** SPORT-FR-20: cancels a refresh in progress and starts over. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch { load() }
    }

    /**
     * Today or the ticker is on screen with the app in front (SPORT-FR-27, -28): refresh now when the
     * list is empty or older than the interval, else wait for the interval; stop when hidden.
     */
    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        pollJob?.cancel()
        pollJob = null
        if (!visible || !automatic) return
        pollJob = scope.launch {
            // On return: a load already running is enough; an empty or old list refreshes now.
            val s = _state.value
            val stale = s.events.isEmpty() || s.loadedAt == 0L || clock.wallMillis() - s.loadedAt >= s.pollingMinutes * MINUTE
            if (refreshJob?.isActive != true && stale) refresh()
            while (true) {
                refreshJob?.join()
                // A failure waits a whole interval too (SPORT-FR-35: no retry, no back-off).
                val wait = attemptedAt + _state.value.pollingMinutes * MINUTE - clock.wallMillis()
                if (wait > 0) delay(wait)
                if (refreshJob?.isActive != true) refresh()
            }
        }
    }

    private suspend fun load() {
        val zoneId = TimeLabels.zoneOf(zone.first())
        val f = follows.first()
        val date = Instant.ofEpochMilli(clock.wallMillis()).atZone(zoneId).toLocalDate()
        _state.update { it.copy(loading = true, failure = null, zoneId = zoneId.id) }
        val feeds = f.feeds
        if (feeds.isEmpty()) {
            publish(emptyList(), emptyList(), zoneId, complete = true)
            return
        }
        // Phase 1: the saved days, shown before the network answers (SPORT-FR-22).
        val saved = inParallel(feeds) { repository.savedDay(it, date, zoneId, f.competitionIds(it)) }.filterNotNull()
        if (saved.isNotEmpty() && _state.value.events.isEmpty()) {
            _state.update { it.copy(events = saved.flatMap { d -> d.events }.sortedWith(TodayRules.ORDER)) }
        }
        // Phase 2: the network through the cache (SPORT-FR-23).
        val failures = ArrayList<SportsProblem>()
        val days = inParallel(feeds) { sport ->
            try {
                day(sport, date, zoneId, f)
            } catch (e: SportsException) {
                synchronized(failures) { failures += e.problem }
                null
            }
        }.filterNotNull()
        if (days.isEmpty()) {
            // SPORT-FR-25: nothing loaded, the list stays as it was.
            val cause = failures.firstOrNull() ?: SportsProblem.UNAVAILABLE
            log.info("sport", "refresh failed: ${cause.name.lowercase()}")
            attemptedAt = clock.wallMillis()
            _state.update { it.copy(loading = false, failure = cause, cause = cause, quotas = repository.quotas(), complete = true) }
            return
        }
        publish(days, failures, zoneId, complete = true)
    }

    private suspend fun day(sport: SportType, date: LocalDate, zone: ZoneId, f: SportFollows): SportDay =
        repository.day(sport, date, zone, f.competitionIds(sport))

    private suspend fun publish(days: List<SportDay>, failures: List<SportsProblem>, zoneId: ZoneId, complete: Boolean) {
        val events = days.flatMap { it.events }.sortedWith(TodayRules.ORDER)
        val states = days.map { it.state }.toSet()
        val service = when {
            days.isEmpty() -> null
            states.size > 1 -> SportServiceState.MIXED
            else -> when (states.single()) {
                CacheState.HIT -> SportServiceState.CACHE
                CacheState.MISS -> SportServiceState.UPDATED
                CacheState.STALE -> SportServiceState.STALE
            }
        }
        val now = clock.wallMillis()
        attemptedAt = now
        _state.value = FeedState(
            events = events, zoneId = zoneId.id, loading = false, failure = null, partial = failures.isNotEmpty(),
            cause = failures.firstOrNull(), service = service, quotas = repository.quotas(),
            pollingMinutes = TodayRules.pollingMinutes(events, now), loadedAt = now, complete = complete,
        )
    }

    /** At most three sports in flight (§9 rule). */
    private suspend fun <T> inParallel(sports: List<SportType>, work: suspend (SportType) -> T): List<T> = coroutineScope {
        val permits = Semaphore(PARALLEL)
        sports.map { sport -> async { permits.withPermit { work(sport) } } }.awaitAll()
    }

    private companion object {
        const val MINUTE = 60_000L
        const val PARALLEL = 3
    }
}
