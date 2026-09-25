package com.sohva.tv.feature.home

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.home.RecentChannel
import com.sohva.tv.core.data.home.ResumeState
import com.sohva.tv.core.data.vod.ContinueItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A Continue watching card (spec 02 HOME-FR-13), built off the main thread. */
@Immutable
data class ResumeCard(
    val key: String,
    val item: ContinueItem,
    val title: String,
    val image: String?,
    val fraction: Float,
    /** Floor of the minutes left, at least 1; null without a known duration. */
    val minutesLeft: Int?,
) {
    val isEpisode: Boolean get() = item.season != null
}

/** A Recently watched channels card (HOME-FR-26). */
@Immutable
data class ChannelCard(val key: String, val channel: RecentChannel)

/** A row of Home (HOME-FR-01), in the fixed order; Trakt and sport rows join in M8 and M9. */
sealed interface HomeRow {
    val key: String

    data class Resume(val cards: List<ResumeCard>) : HomeRow {
        override val key: String = CONTINUE
    }

    /** Continue watching while its first read loads or after it failed, with no cards (HOME-FR-45). */
    data class Status(val failed: Boolean) : HomeRow {
        override val key: String = CONTINUE
    }

    data class Channels(val cards: List<ChannelCard>) : HomeRow {
        override val key: String = RECENT
    }

    companion object {
        const val CONTINUE: String = "continue-watching"
        const val RECENT: String = "recent-channels"
    }
}

/** What the hero describes (HOME-FR-60); sport and Trakt subjects join with their milestones. */
sealed interface HeroSubject {
    data class Resume(val card: ResumeCard) : HeroSubject

    data class Channel(val card: ChannelCard) : HeroSubject

    data object Welcome : HeroSubject
}

/** The hero's synopsis and picture for a subject (HOME-FR-65…72); either may be missing. */
@Immutable
data class HeroDetails(val synopsis: String?, val backdrop: String?)

/** What Home needs from the app (plan/03 §4.6). */
interface HomeEnvironment {
    /** The process's Continue watching projection (HOME-FR-24). */
    val resume: StateFlow<ResumeState>

    fun retryResume()

    /** The recent channels with their programmes at [now], once per Home entry (HOME-FR-35). */
    suspend fun recentChannels(now: Long): List<RecentChannel>

    /** The chosen time zone id, or null for the TV's (HOME-FR-56). */
    val timeZone: Flow<String?>

    fun now(): Long

    /** Synopsis and backdrop, looked up only after Continue watching has settled (HOME-FR-65). */
    suspend fun heroDetails(subject: HeroSubject): HeroDetails?

    /** The resume route (spec 01 SHELL-FR-28): the title's pages under the player. */
    fun resume(card: ResumeCard, fromStart: Boolean)

    suspend fun markWatched(card: ResumeCard)

    suspend fun remove(card: ResumeCard)

    /** Live, with Back to the guide on that channel (SHELL-FR-20). */
    fun playChannel(card: ChannelCard)

    fun openGuide()

    /** The low memory class of plan/07 §2.2: the hero decodes at half size and does not crossfade. */
    val lowMemory: Boolean
}
