package com.sohva.tv.core.model.guide

/**
 * The guide's three-hour window (spec 20 GUIDE-FR-40..42). The window is an absolute start; at
 * "now" it follows the clock, once paged it stays where it is while the clock runs.
 */
class GuideWindow private constructor(private val start: Long, private val pinned: Boolean) {
    /** The window start at [now]: the anchor while at now, else the pinned start. */
    fun startAt(now: Long): Long = if (pinned) start else anchor(now)

    fun endAt(now: Long): Long = startAt(now) + LENGTH_MS

    fun isAtNow(now: Long): Boolean = !pinned || start == anchor(now)

    /**
     * The window moved by [delta], clamped to one day back and seven days ahead of the anchor, or
     * null when the move changes nothing (the key is then consumed, GUIDE-FR-70). A result equal
     * to the anchor follows the clock again.
     */
    fun moved(delta: Long, now: Long): GuideWindow? {
        val anchor = anchor(now)
        val current = startAt(now)
        val target = (current + delta).coerceIn(anchor - DAY_MS, anchor + WEEK_MS)
        if (target == current) return null
        return if (target == anchor) AT_NOW else GuideWindow(target, pinned = true)
    }

    override fun equals(other: Any?): Boolean =
        other is GuideWindow && other.pinned == pinned && (!pinned || other.start == start)

    override fun hashCode(): Int = if (pinned) start.hashCode() else -1

    override fun toString(): String = if (pinned) "GuideWindow($start)" else "GuideWindow(now)"

    companion object {
        const val MINUTE_MS: Long = 60_000
        const val HALF_HOUR_MS: Long = 30 * MINUTE_MS
        const val LENGTH_MINUTES: Int = 180
        const val LENGTH_MS: Long = LENGTH_MINUTES * MINUTE_MS
        const val PAGE_MS: Long = 90 * MINUTE_MS
        const val DAY_MS: Long = 24 * 60 * MINUTE_MS
        const val WEEK_MS: Long = 7 * DAY_MS

        /** Programmes are read half an hour either side of the window (GUIDE-FR-51). */
        const val READ_MARGIN_MS: Long = HALF_HOUR_MS

        val AT_NOW: GuideWindow = GuideWindow(0, pinned = false)

        /** Half an hour before the current epoch half hour (GUIDE-FR-40, -113). */
        fun anchor(now: Long): Long = Math.floorDiv(now, HALF_HOUR_MS) * HALF_HOUR_MS - HALF_HOUR_MS
    }
}
