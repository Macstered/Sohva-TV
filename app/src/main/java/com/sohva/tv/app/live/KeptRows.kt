package com.sohva.tv.app.live

import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.data.live.ListSpec
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.time.Clock
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The guide's last group or All-channels list, kept for [TTL_MS] after it was read (spec 20
 * GUIDE-FR-120..121): an index of page keys, never rows (GUIDE-NFR-10). A write to the tables a
 * list depends on since it was read drops it; favourites, recents and searches are not kept.
 */
class KeptRows(private val reads: () -> LiveReads, private val clock: Clock, private val scope: CoroutineScope) {
    private val writes = AtomicLong()
    private var watching = false

    private class Entry(val list: ChannelList, val at: Long, val writes: Long)

    @Volatile
    private var kept: Entry? = null

    fun keep(list: ChannelList) {
        if (list.spec is ListSpec.Named) return
        watch()
        kept = Entry(list, clock.wallMillis(), writes.get())
    }

    fun get(spec: ListSpec): ChannelList? {
        val entry = kept ?: return null
        val fresh = entry.list.spec == spec && clock.wallMillis() - entry.at < TTL_MS && entry.writes == writes.get()
        if (!fresh && clock.wallMillis() - entry.at >= TTL_MS) kept = null
        return entry.list.takeIf { fresh }
    }

    /** Counts writes from the first keep on; the flow's first emission is the current state, not a write. */
    @Synchronized
    private fun watch() {
        if (watching) return
        watching = true
        scope.launch { reads().changes().drop(1).collect { writes.incrementAndGet() } }
    }

    private companion object {
        const val TTL_MS: Long = 10 * 60 * 1000L
    }
}
