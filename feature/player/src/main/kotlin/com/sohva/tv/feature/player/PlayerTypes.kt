package com.sohva.tv.feature.player

import com.sohva.tv.core.model.profile.ChannelAdmission
import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.core.model.player.PlaybackCause
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.player.RemoteMapping
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.player.PlaybackClient
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/** What the player screen needs from the app (plan/03 §4.6). */
interface PlayerEnvironmentUi {
    val reads: LiveReads
    val client: PlaybackClient
    val clock: Clock
    val locale: Locale
    val format: CoroutineDispatcher

    suspend fun settings(): PlaybackSettings

    /** The remote mapping; a change applies from the next key press (spec 31 REMOTE-FR-36). */
    val remoteMapping: Flow<RemoteMapping>

    /** Front of the profile's recents and the last channel (spec 30 PLAY-FR-57). */
    suspend fun recordWatched(channelKey: String)

    /** The profile's group check, then the lock (spec 01 SHELL-FR-22), before any channel starts. */
    suspend fun admit(channelKey: String): ChannelAdmission

    /** A playback failure for the diagnostics log: ids, codes and redacted text only (PLAY-FR-90, spec 73). */
    fun logFailure(line: String)

    /** The live stream's real address and headers for another player app (PLAY-FR-115), or null when gone. */
    suspend fun externalStream(channelKey: String): ExternalStream?

    /** Sohva Sport's score ticker (PLAY-31), or null where Sohva Sport is not offered. */
    val ticker: ScoreTickerSource? get() = null
}

/** The score ticker's data and switch (spec 30 §4.20, spec 60 SPORT-FR-99), owned by Sohva Sport. */
interface ScoreTickerSource {
    /** On or off for the app session: it survives leaving the player (PLAY-FR-120). */
    val shown: kotlinx.coroutines.flow.StateFlow<Boolean>

    /** The rows (PLAY-FR-121), recomputed when the games change and each minute. */
    val games: Flow<List<com.sohva.tv.core.model.sport.SportEvent>>

    /** The zone start labels use (the app zone). */
    val zoneId: Flow<String>

    fun toggle()

    /** Shown over a resumed player: the games poll as if Sohva Sport were on screen, never faster. */
    fun setVisible(visible: Boolean)
}

/** What another player gets: the address (with the provider's credentials, inherent to the feature) and headers. */
class ExternalStream(val address: String, val headers: Map<String, String>) {
    override fun toString(): String = "ExternalStream(<redacted>)"
}

/** Where the player sends the viewer (spec 30 §3.2–3.3, REMOTE group Leave). */
interface PlayerNavigation {
    /** Back from the bare picture, with the channel playing then. */
    fun leave(channelKey: String)

    fun guideAt(channelKey: String)

    fun home()

    fun guide()

    fun sport()

    /** A channel the profile may not watch: the refusal toast (spec 01 SHELL-FR-34). */
    fun refused()

    /** A locked channel: the PIN over the player; unlocking replaces both (SHELL-FR-22, -23). */
    fun unlock(channelKey: String)

    /** Starts another player app with the stream; returns why it failed, or null (PLAY-FR-115..116). */
    fun openExternal(stream: ExternalStream): Throwable?

    /**
     * A film or an episode played to its end (spec 30 PLAY-FR-132): the next episode, or back to
     * the details page. Called once per item.
     */
    fun finished(contentKey: String)
}

enum class Connection { CONNECTING, READY, FAILED }

/** A film or an episode to play from [startMs] (spec 30 §3.1 `VodPlayer`, spec 40 VOD-FR-64). */
data class VodPlay(val contentKey: String, val startMs: Long)

/** A programme to play from the provider's archive: its guide times, epoch ms (spec 22 CATCH-FR-11). */
@Immutable
data class ArchiveWindow(val start: Long, val stop: Long)

/** The channel on screen, formatted once per zap. */
@Immutable
data class Playing(val channel: LiveChannel, val number: Int?, val tags: List<String>, val initials: String)

/** Now and next for the live box (PLAY-FR-40), read when the box opens and every 30 s while shown. */
@Immutable
data class NowNext(val now: GuideProgramme?, val next: GuideProgramme?)

/** Why the banner shows (PLAY-FR-90..95): Media3's cause, or one of the engine's own refusals. */
@Immutable
sealed interface BannerReason {
    data class Cause(val cause: PlaybackCause, val detail: String?) : BannerReason

    data class ConnectionLimit(val sourceName: String, val limit: Int) : BannerReason

    data object Unavailable : BannerReason

    /** The external player could not be opened; [message] is already redacted. */
    data class ExternalFailed(val message: String) : BannerReason
}

/** The error banner: the reason, the attempt counter while reconnecting, and whether it gave up. */
@Immutable
data class Banner(val reason: BannerReason, val attempt: Int, val max: Int, val stopped: Boolean)

/** One selectable track (PLAY-FR-70): its parts, joined in the interface language by the screen. */
@Immutable
data class TrackItem(val group: Int, val index: Int, val label: String?, val language: String?, val channels: Int, val selected: Boolean)

@Immutable
data class Tracks(val audio: List<TrackItem> = emptyList(), val text: List<TrackItem> = emptyList())

enum class Picker { AUDIO, SUBTITLES }
