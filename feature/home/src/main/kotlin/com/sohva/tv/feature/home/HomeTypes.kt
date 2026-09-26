package com.sohva.tv.feature.home

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.home.RecentChannel
import com.sohva.tv.core.data.home.ResumeState
import com.sohva.tv.core.data.vod.ContinueItem
import com.sohva.tv.core.model.sport.SportEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

/** A Today's sport card (HOME-FR-33, spec 60 SPORT-FR-97); the key is the game's id. */
@Immutable
data class SportCard(val key: String, val event: SportEvent)

/** A row of Home (HOME-FR-01), in the fixed order; Trakt rows join with Trakt (M10). */
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

    /** The first 6 of today's games; [total] counts them all (HOME-FR-33, spec 60 SPORT-FR-97). */
    data class Sport(val cards: List<SportCard>, val total: Int) : HomeRow {
        override val key: String = SPORT
    }

    companion object {
        const val CONTINUE: String = "continue-watching"
        const val SPORT: String = "todays-sport"
        const val RECENT: String = "recent-channels"
    }
}

/** What the hero describes (HOME-FR-60); Trakt subjects join with Trakt (M10). */
sealed interface HeroSubject {
    data class Resume(val card: ResumeCard) : HeroSubject

    data class Channel(val card: ChannelCard) : HeroSubject

    /** A focused Today's sport card (spec 60 SPORT-FR-97). */
    data class Sport(val card: SportCard) : HeroSubject

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

    /** Today's games in Today's order; Home starts no request of its own (HOME-FR-33). */
    val sportGames: Flow<List<SportEvent>> get() = flowOf(emptyList())

    /** Sohva Sport with this game's hub open once the day's list holds it (spec 60 §3). */
    fun openSportGame(event: SportEvent) = Unit

    /** The low memory class of plan/07 §2.2: the hero decodes at half size and does not crossfade. */
    val lowMemory: Boolean
}
