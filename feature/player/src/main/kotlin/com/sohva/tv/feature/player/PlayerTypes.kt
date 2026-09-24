package com.sohva.tv.feature.player

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.core.model.player.PlaybackCause
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.player.PlaybackClient
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher

/** What the player screen needs from the app (plan/03 §4.6). */
interface PlayerEnvironmentUi {
    val reads: LiveReads
    val client: PlaybackClient
    val clock: Clock
    val locale: Locale
    val format: CoroutineDispatcher

    suspend fun settings(): PlaybackSettings

    /** Front of the profile's recents and the last channel (spec 30 PLAY-FR-57). */
    suspend fun recordWatched(channelKey: String)

    /** The live stream's real address and headers for another player app (PLAY-FR-115), or null when gone. */
    suspend fun externalStream(channelKey: String): ExternalStream?
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

    /** Starts another player app with the stream; returns why it failed, or null (PLAY-FR-115..116). */
    fun openExternal(stream: ExternalStream): Throwable?
}

enum class Connection { CONNECTING, READY, FAILED }

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
