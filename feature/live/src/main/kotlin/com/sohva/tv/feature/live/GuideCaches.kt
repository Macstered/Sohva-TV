package com.sohva.tv.feature.live

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * The rows of the list on screen, page by page (spec 20 GUIDE-NFR-10): at most [MAX_PAGES] pages of
 * 200 rows are resident, evicted furthest from the viewport first. Each page is a snapshot state,
 * so a page arriving recomposes only the rows it holds.
 */
@Stable
class RowPages(val size: Int, private val pageSize: Int) {
    private val pages: Array<MutableState<List<GuideRowData>?>> =
        Array((size + pageSize - 1) / pageSize) { mutableStateOf(null) }
    private val resident = ArrayList<Int>()

    val pageCount: Int get() = pages.size

    /** The row at [index], or null while its page is not read; reading subscribes to that page. */
    fun row(index: Int): GuideRowData? = pages.getOrNull(index / pageSize)?.value?.getOrNull(index % pageSize)

    /** The same without subscribing: for key handling and effects. */
    fun peek(index: Int): GuideRowData? = row(index)

    fun has(page: Int): Boolean = pages.getOrNull(page)?.value != null

    fun put(page: Int, rows: List<GuideRowData>, around: Int) {
        if (page !in pages.indices) return
        pages[page].value = rows
        resident.remove(page)
        resident += page
        while (resident.size > MAX_PAGES) {
            val furthest = resident.maxBy { kotlin.math.abs(it - around) }
            resident.remove(furthest)
            pages[furthest].value = null
        }
    }

    /** Rows already read in [range], for the programme request. */
    fun loaded(range: IntRange): List<GuideRowData> = range.mapNotNull { peek(it) }

    companion object {
        /** The visible rows plus about 250 either side (GUIDE-NFR-10). */
        const val MAX_PAGES: Int = 3
    }
}

/**
 * The programme cache (GUIDE-FR-55): at most [MAX_SCHEDULES] schedules for the current window,
 * evicted oldest arrival first; a schedule equal to the held one keeps the held object; emptied
 * when the window, source or list changes. Main thread only.
 *
 * Rows read [version] in their draw phase and then look their schedule up, so a batch arriving
 * redraws the rows on screen once and recomposes nothing (GUIDE-NFR-02). One counter rather than a
 * state per channel: a state created inside the draw that reads it is not observed.
 */
class ProgrammeCache {
    val version = mutableIntStateOf(0)
    private val schedules = HashMap<String, RowSchedule>(128)
    private val arrival = ArrayDeque<String>()

    /** For the draw phase: null = not read yet (the blank bar); [RowSchedule.EMPTY] = nothing in the window. */
    fun drawnSchedule(epgId: String?): RowSchedule? {
        version.intValue
        return schedule(epgId)
    }

    fun schedule(epgId: String?): RowSchedule? = if (epgId == null) RowSchedule.EMPTY else schedules[epgId]

    fun isKnown(epgId: String): Boolean = epgId in schedules

    /** One batch; one redraw. */
    fun putAll(batch: Map<String, RowSchedule>) {
        var changed = false
        for ((id, schedule) in batch) {
            val held = schedules[id]
            if (held != null && held == schedule) continue
            if (held == null) arrival.addLast(id)
            schedules[id] = schedule
            changed = true
        }
        while (arrival.size > MAX_SCHEDULES) schedules.remove(arrival.removeFirst())
        if (changed) version.intValue++
    }

    fun clear() {
        if (schedules.isEmpty()) return
        schedules.clear()
        arrival.clear()
        version.intValue++
    }

    companion object {
        const val MAX_SCHEDULES: Int = 240
    }
}
