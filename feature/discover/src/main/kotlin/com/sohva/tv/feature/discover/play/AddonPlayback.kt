package com.sohva.tv.feature.discover.play

import com.sohva.tv.feature.discover.protocol.AddonStream
import com.sohva.tv.feature.discover.store.Artwork
import com.sohva.tv.feature.discover.store.Installation
import com.sohva.tv.feature.discover.store.WatchIdentity
import com.sohva.tv.feature.discover.ui.TitleRequest

/**
 * One addon playback (spec 50 §4.12): the chosen stream and its provider, what is watched (the
 * catalog's original keys, FR-24), the title shown and the artwork kept with the progress. It lives
 * in memory only, reached by [token]; its URL and headers never leave the process's memory.
 */
class AddonPlayback(
    val token: String,
    val profile: String,
    val source: Installation,
    val stream: AddonStream,
    val identity: WatchIdentity,
    /** The type the stream was asked for ("movie", "series", …). */
    val videoType: String,
    val title: String,
    /** What progress keeps as the title: the episode's own title, else the title's name (spec 02 HOME-FR-18). */
    val savedTitle: String,
    val artwork: Artwork,
    val logo: String?,
    val startMs: Long,
    /** A newer Trakt pause for this title (M10); the player seeks to it once the duration is known. */
    val traktFraction: Float?,
    /** The next episode's page, which opens and plays from the start at the end (FR-93). */
    val next: TitleRequest?,
    /** A progress session: snapshots from older sessions never overwrite newer ones (FR-106). */
    val session: Long,
) {
    val movie: Boolean get() = videoType == "movie"

    /** Retry with fresh source (FR-91): the same playback and progress session with a newly resolved stream. */
    fun withStream(token: String, stream: AddonStream): AddonPlayback =
        AddonPlayback(token, profile, source, stream, identity, videoType, title, savedTitle, artwork, logo, startMs, traktFraction, next, session)

    override fun toString(): String = "AddonPlayback($token)"
}
