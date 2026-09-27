package com.sohva.tv.core.model.vod

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Trakt's mark on one title or episode (spec 51 FR-31): a progress fraction while paused
 * (0 < progress < 100), or watched when not in progress, and when Trakt last changed it.
 */
data class TitleMark(val fraction: Float?, val watched: Boolean, val updatedAt: Long)

/**
 * Trakt's marks for Discover cards and resume (spec 50 ADDON-FR-71, spec 51 FR-31, FR-35).
 * Keys are `movie:<media id>` and `series:<series id>:<season>:<episode>`, the ids as the
 * catalog gives them (`tt…` or `tmdb:<n>`). Looked up only for the keys on screen (§9 rule).
 */
interface TitleMarks {
    /** Moves whenever the marks may have changed; screens look their keys up again. */
    val revision: StateFlow<Long>

    /** The marks of [keys] for the active profile; keys without a mark are absent. */
    suspend fun marks(keys: Collection<String>): Map<String, TitleMark>

    companion object {
        /** No Trakt: nothing is ever marked. */
        val NONE: TitleMarks = object : TitleMarks {
            override val revision: StateFlow<Long> = MutableStateFlow(0L)

            override suspend fun marks(keys: Collection<String>): Map<String, TitleMark> = emptyMap()
        }
    }
}
