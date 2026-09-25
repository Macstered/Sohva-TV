package com.sohva.tv.core.player

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.player.PlaybackSettings
import okhttp3.Call

/** Implemented by the Application: the service reads the environment once in onCreate (plan/03 §4.4). */
interface PlayerHost {
    fun playerEnvironment(): PlayerEnvironment
}

/**
 * What the engine needs from the app (spec 30 §4.1): stream resolution (the address never leaves
 * the process's encrypted rows except to the data source), the shared HTTP stack, settings, the
 * "video is playing" signal for bulk work, and the diagnostics log.
 */
interface PlayerEnvironment {
    /** Resolves a live channel by its key, or null when it is gone (PLAY-FR-14 step 2). Off the main thread. */
    suspend fun resolveLive(channelKey: String): ResolvedStream?

    /** The playback client: HTTP/1.1, connect 20 s, read 90 s, redirects followed (PLAY-FR-18). */
    val callFactory: Call.Factory

    /** `Sohva TV/<version> (Android TV <release>)` (PLAY-FR-17). */
    val userAgent: String

    suspend fun settings(): PlaybackSettings

    /** True while video plays: bulk work pauses (plan/03 §4.8 rule 2). */
    fun setPlaybackActive(active: Boolean)

    /** Low-memory boxes cap the Stability buffer (spec 30 §9). */
    val lowMemory: Boolean

    val log: DiagnosticsLog
}

/** A stream ready to open: the real address stays inside the engine. */
data class ResolvedStream(
    val key: String,
    val address: String,
    val sourceId: String,
    val sourceName: String,
    val connectionLimit: Int,
    val title: String,
    val userAgent: String?,
    val referrer: String?,
) {
    override fun toString(): String = "ResolvedStream(key=$key, source=$sourceId)"
}
