package com.sohva.tv.core.model.sport

/** Where the loaded games came from as a whole (SPORT-FR-24): every feed the same, else mixed. */
enum class SportServiceState { CACHE, UPDATED, STALE, MIXED }

/**
 * The feed as Settings' status line describes it (SPORT-FR-12): the zone the day was read in,
 * whether a refresh runs, the last failure, where the games came from, each sport's remaining
 * quota, and the polling interval.
 */
data class SportFeedStatus(
    val zoneId: String,
    val refreshing: Boolean = false,
    val problem: SportsProblem? = null,
    val state: SportServiceState? = null,
    val quotas: Map<SportType, Int> = emptyMap(),
    val pollingMinutes: Int = 30,
)
