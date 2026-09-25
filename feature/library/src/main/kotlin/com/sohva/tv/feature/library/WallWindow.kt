package com.sohva.tv.feature.library

import com.sohva.tv.core.data.vod.WallItem

/**
 * The part of a wall held in memory (spec 40 §9.3 "Window pager"): at most [MAX_ITEMS] entries
 * starting at absolute index [first]. Cards are addressed by absolute index, so a card's column
 * is `index mod columns` and dropping or prepending a page never moves a card between columns.
 * Slots before [first] (dropped pages) and one row after the end (while more may come) are
 * empty tiles that cannot take focus.
 */
data class WallWindow(val first: Int, val items: List<WallItem>, val atEnd: Boolean) {
    val end: Int get() = first + items.size

    fun itemAt(index: Int): WallItem? = items.getOrNull(index - first)

    /** How many slots the grid shows: the loaded range plus one row of empty tiles while the end is unknown. */
    fun slots(columns: Int): Int = end + if (atEnd) 0 else columns

    /** More is wanted after the window when [focused] is within 3 rows of its end (spec 40 §9.3). */
    fun wantsNext(focused: Int, columns: Int): Boolean = !atEnd && focused >= end - PREFETCH_ROWS * columns

    /** The page before the window is wanted when [focused] is within 3 rows of its start. */
    fun wantsPrevious(focused: Int, columns: Int): Boolean = first > 0 && focused < first + PREFETCH_ROWS * columns

    /** [page] read after the window; whole pages fall off the front past [MAX_ITEMS]. */
    fun appended(page: List<WallItem>): WallWindow {
        var from = first
        var all = items + page
        while (all.size > MAX_ITEMS) {
            all = all.drop(PAGE)
            from += PAGE
        }
        return WallWindow(from, all, atEnd = page.size < PAGE)
    }

    /**
     * [page] read before the window (in wall order); whole pages fall off the end past
     * [MAX_ITEMS]. A short page means the wall's start was reached: the window is re-based at 0
     * (rows arrived or left before it since it was read), which the pager accepts over a gap.
     */
    fun prepended(page: List<WallItem>): WallWindow {
        val start = if (page.size < PAGE) 0 else (first - page.size).coerceAtLeast(0)
        var all = page + items
        var atEnd = atEnd
        while (all.size > MAX_ITEMS) {
            all = all.dropLast(PAGE)
            atEnd = false
        }
        return WallWindow(start, all, atEnd)
    }

    companion object {
        const val PAGE: Int = 120
        const val MAX_ITEMS: Int = 5 * PAGE
        const val PREFETCH_ROWS: Int = 3

        val Empty: WallWindow = WallWindow(0, emptyList(), atEnd = true)

        /** The first page of a wall. */
        fun of(page: List<WallItem>): WallWindow = WallWindow(0, page, atEnd = page.size < PAGE)
    }
}
