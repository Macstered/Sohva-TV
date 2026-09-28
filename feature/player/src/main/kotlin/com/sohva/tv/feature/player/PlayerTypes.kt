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

    /** Discover's side of addon playback, where the build has Discover. */
    val addon: AddonPlaybackEnv? get() = null

    /** Sohva Sport's score ticker (PLAY-31), or null where Sohva Sport is not offered. */
    val ticker: ScoreTickerSource? get() = null

    /** The demo build's still picture for a channel or title (PLAY-FR-25), as a drawable id; null plays the stream. */
    fun demoPicture(key: String): Int? = null
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

    /** An addon stream ended (spec 50 FR-93): a movie returns to its page, an episode continues. */
    fun addonFinished(token: String) {}
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
data class TrackItem(
    val group: Int,
    val index: Int,
    val label: String?,
    val language: String?,
    val channels: Int,
    val selected: Boolean,
    /** The addon subtitle side-loaded by Sohva (spec 50 FR-101), not one of the stream's own. */
    val sideLoaded: Boolean = false,
)

@Immutable
data class Tracks(val audio: List<TrackItem> = emptyList(), val text: List<TrackItem> = emptyList())

enum class Picker { AUDIO, SUBTITLES }

/**
 * An addon stream to play (spec 50 §4.12) by the in-memory [token] Discover keeps for it, from
 * [startMs]; the loading screen shows [title] (or [logo]) over [backdrop].
 */
data class AddonPlay(
    val token: String,
    val startMs: Long,
    val title: String,
    val backdrop: String?,
    val logo: String?,
    /** A newer Trakt pause as a fraction, applied once the duration is known (FR-88). */
    val traktFraction: Float? = null,
)

/** What the addon player asks Discover (spec 50 §4.12, §4.14). */
interface AddonPlaybackEnv {
    /** FR-84: the profile, the source addon and the title's metadata addon still allow this playback. */
    suspend fun stillAllowed(token: String): Boolean

    /**
     * FR-106: a progress snapshot; [sequence] grows through the playback. The write runs on an
     * app-lifetime scope so the last one survives the screen; [failed] is called when it did not land.
     */
    fun saveProgress(token: String, positionMs: Long, durationMs: Long?, ended: Boolean, sequence: Long, failed: () -> Unit)

    /** FR-91: the same provider's matching stream again under a new token; null when none or several match. */
    suspend fun freshToken(token: String): String?

    /** Start-up milestones for diagnostics (FR-86): names and milliseconds, never titles or URLs. */
    fun milestone(name: String, sinceStartMs: Long)

    /**
     * FR-100: the stream's inline subtitles at once, then every subtitle addon's results as they
     * arrive. Each collection asks the providers once; nothing is cached.
     */
    fun subtitles(token: String): kotlinx.coroutines.flow.Flow<SubtitleResults>

    /** FR-101: downloads and recognises a candidate, re-checking its provider afterwards. */
    suspend fun downloadSubtitle(token: String, key: String): SubtitleDownload

    /** Trakt scrobbles for this addon playback (spec 51 FR-17); null when nothing is sent. */
    fun scrobbler(token: String): com.sohva.tv.core.model.player.TitleScrobbler? = null

    /** "Show all languages" (FR-97): global and persisted. */
    val showAllLanguages: kotlinx.coroutines.flow.StateFlow<Boolean>

    suspend fun setShowAllLanguages(on: Boolean)
}
