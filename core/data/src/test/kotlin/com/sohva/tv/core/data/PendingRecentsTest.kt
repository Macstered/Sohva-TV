package com.sohva.tv.core.data

import com.sohva.tv.core.data.live.PendingRecents
import org.junit.Assert.assertEquals
import org.junit.Test

/** Channels watched while their "recent" write waits for an import (HOME-FR-34). */
class PendingRecentsTest {
    @Test
    fun newestFirstPerProfileAndGoneOnceWritten() {
        val pending = PendingRecents()
        pending.add("p1", "a", 10)
        pending.add("p1", "b", 20)
        pending.add("p2", "c", 30)
        assertEquals(listOf("b", "a"), pending.keys("p1"))
        assertEquals(listOf("c"), pending.keys("p2"))
        pending.written("p1", "a", 10)
        assertEquals(listOf("b"), pending.keys("p1"))
    }

    @Test
    fun aNewerWatchOfTheSameChannelOutlivesTheOlderWrite() {
        val pending = PendingRecents()
        pending.add("p1", "a", 10)
        pending.add("p1", "a", 40)
        pending.written("p1", "a", 10)
        assertEquals(listOf("a"), pending.keys("p1"))
        pending.written("p1", "a", 40)
        assertEquals(emptyList<String>(), pending.keys("p1"))
    }

    @Test
    fun boundedToTwentyWithTheOldestDropped() {
        val pending = PendingRecents()
        for (i in 1..25) pending.add("p1", "c$i", i.toLong())
        val keys = pending.keys("p1")
        assertEquals(20, keys.size)
        assertEquals("c25", keys.first())
        assertEquals("c6", keys.last())
    }
}
