package com.streammate.tv.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.FootballIncident
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.SportsFollowDefaults
import com.streammate.tv.matching.ChannelMatchConfidence
import com.streammate.tv.matching.EventChannelMatch
import com.streammate.tv.matching.EventChannelMatchingRepository
import com.streammate.tv.matching.ManualMatchDecision
import com.streammate.tv.sports.repository.SportsRepository
import com.streammate.tv.sports.repository.SportsEventsSnapshot
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import com.streammate.tv.core.concurrent.parallelResults
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class TodayUiError {
    PARTIAL_DATA,
    SPORTS_UNAVAILABLE,
    DETAILS_UNAVAILABLE,
    MATCH_DECISION_SAVE,
}

data class EventDetailsUiState(
    val incidents: List<FootballIncident> = emptyList(),
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val error: TodayUiError? = null,
    val cacheState: String? = null,
)

data class TodayUiState(
    val events: List<TodayEvent> = emptyList(),
    val isLoading: Boolean = true,
    val error: TodayUiError? = null,
    val cacheState: String? = null,
    val quotaRemaining: Int? = null,
    val providerQuotas: Map<String, Int> = emptyMap(),
    val pollingMinutes: Int = 30,
    val matches: Map<String, List<EventChannelMatch>> = emptyMap(),
    val eventDetails: Map<String, EventDetailsUiState> = emptyMap(),
    val followedSports: Set<SportType> = SportsFollowDefaults.sports,
    val timeZoneId: String = com.streammate.tv.app.AppPreferences.DEFAULT_TIME_ZONE,
)

class TodayViewModel(
    private val repository: SportsRepository,
    private val matchingRepository: EventChannelMatchingRepository,
    private val preferencesRepository: AppPreferencesRepository,
    initialZoneId: ZoneId = ZoneId.of("Europe/Helsinki"),
    private val clock: Clock = Clock.systemUTC(),
    private val automaticRefreshAllowed: Boolean = true,
    private val beforeInitialRefresh: suspend () -> Unit = {},
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TodayUiState(isLoading = automaticRefreshAllowed))
    val uiState: StateFlow<TodayUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var favouriteEventIds: Set<String> = emptySet()
    private var zoneId: ZoneId = initialZoneId
    private var followedSports: Set<SportType> = SportsFollowDefaults.sports
    private var followedCompetitionKeys: Set<String> = SportsFollowDefaults.competitionKeys
    private var settingsLoaded = false
    private var autoRefreshEnabled = false
    private var lastLoadedAtEpochMillis: Long? = null

    init {
        viewModelScope.launch {
            beforeInitialRefresh()
            preferencesRepository.preferences.collectLatest { preferences ->
                val nextZone = runCatching { ZoneId.of(preferences.timeZoneId) }.getOrDefault(initialZoneId)
                val feedChanged = !settingsLoaded ||
                    nextZone != zoneId ||
                    preferences.followedSports != followedSports ||
                    preferences.followedCompetitionKeys != followedCompetitionKeys
                favouriteEventIds = preferences.favouriteEventIds
                zoneId = nextZone
                followedSports = preferences.followedSports
                followedCompetitionKeys = preferences.followedCompetitionKeys
                settingsLoaded = true
                mutableUiState.update { current ->
                    current.copy(
                        events = current.events.map { event ->
                            event.copy(isFavourite = event.id in favouriteEventIds)
                        },
                        followedSports = followedSports,
                        timeZoneId = zoneId.id,
                    )
                }
                if (feedChanged && automaticRefreshAllowed) refresh()
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val requestedZone = zoneId
            val requestedSports = followedSports
            val requestedCompetitionKeys = followedCompetitionKeys
            mutableUiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                loadEvents(
                    date = LocalDate.now(clock.withZone(requestedZone)),
                    requestedZone = requestedZone,
                    requestedSports = requestedSports,
                    requestedCompetitionKeys = requestedCompetitionKeys,
                    onPartial = { loaded ->
                        mutableUiState.update { current -> current.copy(
                            events = loaded.events.map { it.copy(isFavourite = it.id in favouriteEventIds) },
                            cacheState = loaded.cacheState, providerQuotas = loaded.providerQuotas,
                            quotaRemaining = loaded.quotaRemaining,
                        ) }
                    },
                )
            }.onSuccess { loaded ->
                // Publish the feed before optional channel matching, which may scan a large line-up.
                val matches = mutableUiState.value.matches.filterKeys { key -> loaded.events.any { it.id == key } }
                mutableUiState.value = TodayUiState(
                    events = withMatchCounts(loaded.events, matches),
                    isLoading = false,
                    error = if (loaded.isPartial) TodayUiError.PARTIAL_DATA else null,
                    cacheState = loaded.cacheState,
                    quotaRemaining = loaded.quotaRemaining,
                    providerQuotas = loaded.providerQuotas,
                    pollingMinutes = TodayPollingPolicy.intervalMinutes(loaded.events, clock.millis()),
                    matches = matches,
                    followedSports = requestedSports,
                    timeZoneId = requestedZone.id,
                    eventDetails = mutableUiState.value.eventDetails
                        .filterKeys { eventId -> loaded.events.any { it.id == eventId } },
                )
                lastLoadedAtEpochMillis = clock.millis()
                scheduleAutoRefresh(loaded.events)
                val refreshedMatches = try { matchingRepository.matchesFor(loaded.events) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { matches }
                mutableUiState.update { it.copy(events = withMatchCounts(loaded.events, refreshedMatches), matches = refreshedMatches) }
            }.onFailure {
                if (it is CancellationException) throw it
                mutableUiState.update { current ->
                    current.copy(
                        isLoading = false,
                        error = TodayUiError.SPORTS_UNAVAILABLE,
                    )
                }
                scheduleAutoRefresh(mutableUiState.value.events)
            }
        }
    }

    private suspend fun loadEvents(
        date: LocalDate,
        requestedZone: ZoneId,
        requestedSports: Set<SportType>,
        requestedCompetitionKeys: Set<String>,
        onPartial: (CombinedEventsSnapshot) -> Unit,
    ): CombinedEventsSnapshot {
        val feeds = requestedSports.mapNotNull { sport ->
            val prefix = "${sport.name}:"
            val ids = requestedCompetitionKeys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.filter(String::isNotBlank).toSet()
            ids.takeIf { it.isNotEmpty() }?.let { sport to it }
        }
        val snapshots = linkedMapOf<SportType, SportsEventsSnapshot>()
        var failures = 0
        var failure: Throwable? = null
        fun combined(): CombinedEventsSnapshot {
            val values = snapshots.values.toList()
            return CombinedEventsSnapshot(
                events = feeds.flatMap { snapshots[it.first]?.events.orEmpty() },
                cacheState = values.map { it.cacheState }.distinct().singleOrNull() ?: if (values.isEmpty()) "hit" else "mixed",
                quotaRemaining = values.mapNotNull { it.quotaRemaining }.minOrNull(),
                providerQuotas = values.mapNotNull { snapshot -> snapshot.quotaRemaining?.let { snapshot.source to it } }.toMap(),
                isPartial = failures > 0,
            )
        }
        feeds.parallelResults { (sport, ids) -> repository.cachedEvents(sport, date, requestedZone, ids) }.collect { (feed, result) ->
            result.getOrNull()?.let { snapshots[feed.first] = it; onPartial(combined()) }
        }
        feeds.parallelResults { (sport, ids) -> repository.events(sport, date, requestedZone, ids) }.collect { (feed, result) ->
            result.fold(
                onSuccess = { snapshots[feed.first] = it; onPartial(combined()) },
                onFailure = { failures++; failure = it },
            )
        }
        if (snapshots.isEmpty() && failure != null) throw failure
        return combined()
    }

    private fun scheduleAutoRefresh(events: List<TodayEvent>) {
        autoRefreshJob?.cancel()
        val minutes = TodayPollingPolicy.intervalMinutes(events, clock.millis())
        mutableUiState.update { it.copy(pollingMinutes = minutes) }
        if (!autoRefreshEnabled) return
        autoRefreshJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            refresh()
        }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        if (!automaticRefreshAllowed) return
        if (autoRefreshEnabled == enabled) return
        autoRefreshEnabled = enabled
        if (!enabled) {
            autoRefreshJob?.cancel()
            autoRefreshJob = null
            return
        }
        val current = mutableUiState.value
        if (current.isLoading) return
        // Coming back after time away means the scores on screen are as old as
        // the absence. Spend one call rather than showing a stale live match.
        val stale = TodayPollingPolicy.shouldRefreshOnResume(
            lastLoadedAtEpochMillis = lastLoadedAtEpochMillis,
            nowEpochMillis = clock.millis(),
            intervalMinutes = current.pollingMinutes,
        )
        if (current.events.isEmpty() || stale) {
            refresh()
        } else {
            scheduleAutoRefresh(current.events)
        }
    }

    fun loadEventDetails(eventId: String, force: Boolean = false) {
        val currentDetails = mutableUiState.value.eventDetails[eventId]
        if (currentDetails?.isLoading == true || (!force && currentDetails?.isLoaded == true)) return
        viewModelScope.launch {
            mutableUiState.update { current ->
                current.copy(
                    eventDetails = current.eventDetails + (
                        eventId to (current.eventDetails[eventId] ?: EventDetailsUiState()).copy(
                            isLoading = true,
                            error = null,
                        )
                    ),
                )
            }
            runCatching { repository.footballIncidents(eventId) }
                .onSuccess { snapshot ->
                    mutableUiState.update { current ->
                        current.copy(
                            quotaRemaining = snapshot.quotaRemaining ?: current.quotaRemaining,
                            providerQuotas = snapshot.quotaRemaining?.let { remaining ->
                                current.providerQuotas + (snapshot.source to remaining)
                            } ?: current.providerQuotas,
                            eventDetails = current.eventDetails + (
                                eventId to EventDetailsUiState(
                                    incidents = snapshot.incidents,
                                    isLoaded = true,
                                    cacheState = snapshot.cacheState,
                                )
                            ),
                        )
                    }
                }
                .onFailure {
                    mutableUiState.update { current ->
                        current.copy(
                            eventDetails = current.eventDetails + (
                                eventId to (current.eventDetails[eventId] ?: EventDetailsUiState()).copy(
                                    isLoading = false,
                                    error = TodayUiError.DETAILS_UNAVAILABLE,
                                )
                            ),
                        )
                    }
                }
        }
    }

    fun setMatchDecision(eventId: String, channelId: String, decision: ManualMatchDecision?) {
        viewModelScope.launch {
            runCatching {
                matchingRepository.setDecision(eventId, channelId, decision)
                matchingRepository.matchesFor(mutableUiState.value.events)
            }.onSuccess { matches ->
                mutableUiState.update { current ->
                    current.copy(
                        events = withMatchCounts(current.events, matches),
                        matches = matches,
                        error = null,
                    )
                }
            }.onFailure {
                mutableUiState.update { current ->
                    current.copy(error = TodayUiError.MATCH_DECISION_SAVE)
                }
            }
        }
    }

    fun toggleFavourite(eventId: String) {
        val favourite = eventId !in favouriteEventIds
        viewModelScope.launch {
            preferencesRepository.setFavourite(eventId, favourite)
        }
    }

    private fun withMatchCounts(
        events: List<TodayEvent>,
        matches: Map<String, List<EventChannelMatch>>,
    ): List<TodayEvent> = TodayEventOrdering.sort(events.map { event ->
        event.copy(
            matchingChannels = matches[event.id].orEmpty()
                .count { it.confidence == ChannelMatchConfidence.AVAILABLE },
            isFavourite = event.id in favouriteEventIds,
        )
    })

    companion object {
        fun factory(
            repository: SportsRepository,
            matchingRepository: EventChannelMatchingRepository,
            preferencesRepository: AppPreferencesRepository,
            automaticRefreshAllowed: Boolean = true,
            beforeInitialRefresh: suspend () -> Unit = {},
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TodayViewModel(repository, matchingRepository, preferencesRepository, automaticRefreshAllowed = automaticRefreshAllowed, beforeInitialRefresh = beforeInitialRefresh)
            }
        }
    }
}

private data class CombinedEventsSnapshot(
    val events: List<TodayEvent>,
    val cacheState: String,
    val quotaRemaining: Int?,
    val providerQuotas: Map<String, Int>,
    val isPartial: Boolean,
)
