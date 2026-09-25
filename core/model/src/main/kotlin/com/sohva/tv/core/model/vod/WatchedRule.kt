package com.sohva.tv.core.model.vod

/**
 * When a film or episode counts as watched (spec 40 VOD-FR-90, decided 7 September 2026): at 90 %,
 * or with 3 minutes or less left on anything of 10 minutes or more; never without a duration. The
 * earlier 95 % rule left nearly finished titles in Continue watching for good.
 */
object WatchedRule {
    private const val FRACTION = 0.90
    private const val LONG_MS = 10 * 60_000L
    private const val TAIL_MS = 3 * 60_000L

    /** Positions under this are not saved at all (VOD-FR-89). */
    const val MIN_SAVED_MS: Long = 5_000

    fun isWatched(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        val p = positionMs.coerceIn(0, durationMs)
        return p >= FRACTION * durationMs || (durationMs >= LONG_MS && durationMs - p <= TAIL_MS)
    }
}
