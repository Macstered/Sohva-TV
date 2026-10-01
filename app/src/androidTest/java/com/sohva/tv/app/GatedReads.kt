package com.sohva.tv.app

import com.sohva.tv.core.data.database.LiveSource
import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.data.live.ListSpec
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.guide.GuideProgramme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * The app's live reads with the programme read behind a gate (AGENTS.md §8 "Timing bugs need slow
 * data in tests"): while [open] is false every schedule read waits, so key presses meet a guide
 * whose programmes have not arrived.
 */
class GatedReads(private val real: LiveReads) : LiveReads by real {
    val open = MutableStateFlow(true)
    val scheduleReads = AtomicInteger()
    val waiting = AtomicInteger()

    /** Group and All-channels list reads, the ones GUIDE-FR-120 keeps. */
    val listReads = AtomicInteger()
    val listsOpen = MutableStateFlow(true)
    val listsWaiting = AtomicInteger()

    override suspend fun open(spec: ListSpec): ChannelList {
        if (spec is ListSpec.Group || spec is ListSpec.All) {
            listReads.incrementAndGet()
            listsWaiting.incrementAndGet()
            try {
                listsOpen.first { it }
            } finally {
                listsWaiting.decrementAndGet()
            }
        }
        return real.open(spec)
    }

    override suspend fun schedules(source: LiveSource, epgIds: Collection<String>, windowStart: Long): Map<String, List<GuideProgramme>> {
        scheduleReads.incrementAndGet()
        waiting.incrementAndGet()
        try {
            open.first { it }
        } finally {
            waiting.decrementAndGet()
        }
        return real.schedules(source, epgIds, windowStart)
    }
}
