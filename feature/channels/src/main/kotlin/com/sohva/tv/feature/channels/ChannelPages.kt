package com.sohva.tv.feature.channels

import com.sohva.tv.core.data.database.ManagedChannel

/**
 * The filtered channel list as pages of ≤ [PAGE] rows (spec 21 CHAN-NFR-01). Sources follow one
 * another (decision: Source = All lists each source in turn, as the guide does), each paged along
 * its own index. Only [size] and where each page starts are kept for the whole list; at most
 * [RESIDENT] pages of rows are held, the least recently used dropped first.
 */
class ChannelPages(
    private val env: ChannelsEnvironment,
    private val sources: List<String>,
    private val filter: ChannelFilter,
    counts: IntArray,
    private val format: (ManagedChannel) -> ChannelRow,
) {
    /** Where page p starts: a source and the row before it there (null = that source's first row). */
    private class Anchor(val source: Int, val after: ManagedChannel?)

    val size: Int = counts.sum()
    val pageCount: Int get() = (size + PAGE - 1) / PAGE
    private val anchors = arrayListOf(Anchor(0, null))
    private val resident = object : LinkedHashMap<Int, List<ChannelRow>>(RESIDENT + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<ChannelRow>>?): Boolean = size > RESIDENT
    }

    /** The row at [index] when its page is held, else null (the caller asks for the page). */
    fun rowAt(index: Int): ChannelRow? = resident[index / PAGE]?.getOrNull(index % PAGE)

    fun isLoaded(page: Int): Boolean = page in resident

    /** Rows of the held pages, in list order: to find a selection again after a change. */
    fun heldRows(): List<Pair<Int, ChannelRow>> = resident.keys.sorted().flatMap { p -> resident.getValue(p).mapIndexed { i, r -> p * PAGE + i to r } }

    /**
     * Loads [page]: pages are read in order from the last known start, since a page's start is the
     * row after the previous page (keyset). Scrolling with the remote reaches pages one by one.
     */
    suspend fun load(page: Int) {
        if (page < 0 || page >= pageCount || page in resident) return
        while (anchors.size <= page) read(anchors.size - 1)
        read(page)
    }

    /**
     * Reads the held pages again from where they start, after a write (CHAN-NFR-07): only what is
     * on screen is re-read, and the list keeps its place.
     */
    suspend fun reloadHeld() {
        for (page in resident.keys.sorted()) read(page)
    }

    private suspend fun read(page: Int) {
        val anchor = anchors[page]
        val rows = ArrayList<ManagedChannel>(PAGE)
        var source = anchor.source
        var after = anchor.after
        while (rows.size < PAGE && source < sources.size) {
            val chunk = env.page(sources[source], filter, after, PAGE - rows.size)
            rows += chunk
            if (rows.size < PAGE) {
                source++
                after = null
            } else {
                after = chunk.lastOrNull() ?: after
            }
        }
        if (anchors.size == page + 1) anchors += Anchor(source, after)
        resident[page] = rows.map(format)
    }

    companion object {
        const val PAGE: Int = 200
        const val RESIDENT: Int = 3
    }
}
