package com.streammate.tv.feature.today

import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus

object TodayPollingPolicy {
    fun intervalMinutes(events: List<TodayEvent>, nowEpochMillis: Long): Int {
        if (events.any { it.status == TodayEventStatus.LIVE }) return 5
        val upcomingSoon = events.any { event ->
            event.status == TodayEventStatus.SCHEDULED &&
                event.startEpochMillis in nowEpochMillis..(nowEpochMillis + TWO_HOURS_MILLIS)
        }
        return if (upcomingSoon) 10 else 30
    }

    /**
     * Whether the quota should be spent at all right now.
     *
     * API-Sports free tier is a daily allowance, so every call made while
     * nobody is looking is one unavailable later. Playback is covered by
     * [onSportsScreen]: watching a stream is its own destination, so the sports
     * screen is not the thing on screen. [appInForeground] covers the rest -
     * the app left for the launcher, another app taking over, an external
     * player - where the screen is still "on" as far as navigation knows but
     * nobody can see it.
     */
    fun shouldPoll(onSportsScreen: Boolean, appInForeground: Boolean): Boolean =
        onSportsScreen && appInForeground

    /**
     * Whether returning to the screen should cost one call straight away.
     *
     * Not polling in the background means coming back to data as old as the
     * time spent away. For live scores that is worse than the call it saved, so
     * anything older than one polling interval is refreshed on arrival - one
     * request, rather than the many that running throughout would have cost.
     */
    fun shouldRefreshOnResume(
        lastLoadedAtEpochMillis: Long?,
        nowEpochMillis: Long,
        intervalMinutes: Int,
    ): Boolean {
        val loadedAt = lastLoadedAtEpochMillis ?: return true
        return nowEpochMillis - loadedAt >= intervalMinutes * 60_000L
    }

    private const val TWO_HOURS_MILLIS = 2 * 60 * 60 * 1_000L
}
