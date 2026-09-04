package com.streammate.tv.feature.guide

/**
 * Which slice of time the guide is showing.
 *
 * The grid has always drawn a three-hour window anchored half an hour before
 * now, which answers "what is on" and nothing else. Moving that window is what
 * turns it into a guide: what is on later tonight, what is on tomorrow.
 *
 * The window is an absolute start rather than an offset from now. An offset
 * would look equivalent and is not: when the clock crosses a half hour the
 * anchor moves, so a window someone had paged to tomorrow would slide half an
 * hour under them while they were reading it.
 */
object GuideTimeWindow {

    /** The window that shows what is on, snapped to a half hour before now. */
    fun nowStart(nowEpochMillis: Long): Long = anchor(nowEpochMillis)

    /**
     * [start] moved by [byMillis], clamped to the range worth showing.
     *
     * Backwards stops at a day, which is as far as catch-up reaches on the
     * sources this app talks to. Forwards stops at a week, beyond which XMLTV
     * feeds have nothing to say and the grid would be an empty field.
     */
    fun shifted(start: Long, byMillis: Long, nowEpochMillis: Long): Long {
        val anchor = anchor(nowEpochMillis)
        return (start + byMillis).coerceIn(anchor - DAY_MILLIS, anchor + MAX_FUTURE_MILLIS)
    }

    /** The window holding [targetEpochMillis], clamped like any other move. */
    fun startFor(targetEpochMillis: Long, nowEpochMillis: Long): Long =
        shifted(anchor(targetEpochMillis), 0L, nowEpochMillis)

    /** True when the window is showing now, and so should follow the clock. */
    fun isAtNow(start: Long, nowEpochMillis: Long): Boolean = start == anchor(nowEpochMillis)

    private fun anchor(epochMillis: Long): Long =
        Math.floorDiv(epochMillis, HALF_HOUR_MILLIS) * HALF_HOUR_MILLIS - HALF_HOUR_MILLIS

    const val PAGE_MILLIS = 90 * 60_000L
    const val DAY_MILLIS = 24 * 60 * 60_000L

    private const val HALF_HOUR_MILLIS = 30 * 60_000L
    private const val MAX_FUTURE_MILLIS = 7 * DAY_MILLIS
}
