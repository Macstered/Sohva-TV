package com.streammate.tv.iptv.repository

/**
 * When a film or episode counts as watched. Credits, recaps and next-episode
 * teasers sit in the last tenth of most titles, and a viewer who stopped at
 * nine tenths has seen the story; the earlier 95 % rule left nearly finished
 * titles in Continue watching for good. Decided with the owner on
 * 7 September 2026.
 */
object WatchedRule {
    /** Watched from here on, whatever the length. */
    const val WATCHED_FRACTION = 0.90
    /** For anything long enough to have credits, this close to the end is watched too. */
    const val WATCHED_REMAINING_MILLIS = 3L * 60_000L
    /** Below this a title has no credits worth skipping, so only the fraction counts. */
    const val LONG_FORM_MILLIS = 10L * 60_000L

    fun isWatched(positionMillis: Long, durationMillis: Long): Boolean {
        if (durationMillis <= 0L) return false
        val position = positionMillis.coerceIn(0L, durationMillis)
        if (position >= (durationMillis * WATCHED_FRACTION).toLong()) return true
        return durationMillis >= LONG_FORM_MILLIS && durationMillis - position <= WATCHED_REMAINING_MILLIS
    }
}
