package com.streammate.tv.feature.home

import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.iptv.repository.GuideChannel
import com.streammate.tv.trakt.TraktHomeTitle

internal data class HomeRows(
    val resume: List<HomeResumeEntry>,
    val nextUp: List<TraktHomeTitle>,
    val sports: List<TodayEvent>,
    val recommendations: List<TraktHomeTitle>,
    val channels: List<GuideChannel>,
) {
    val firstKey: String? get() = when {
        resume.isNotEmpty() -> "continue-watching"
        nextUp.isNotEmpty() -> "watch-next"
        sports.isNotEmpty() -> "todays-sport"
        recommendations.isNotEmpty() -> "recommended"
        channels.isNotEmpty() -> "recent-channels"
        else -> null
    }
    fun updated(latest: HomeRows, locked: Boolean) = HomeRows(
        retainHomeOrder(resume, latest.resume, locked, HomeResumeEntry::key),
        retainHomeOrder(nextUp, latest.nextUp, locked, TraktHomeTitle::key),
        retainHomeOrder(sports, latest.sports, locked, TodayEvent::id),
        retainHomeOrder(recommendations, latest.recommendations, locked, TraktHomeTitle::key),
        retainHomeOrder(channels, latest.channels, locked, GuideChannel::id),
    )
}
