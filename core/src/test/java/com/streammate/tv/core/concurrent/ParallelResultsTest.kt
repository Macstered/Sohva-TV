package com.streammate.tv.core.concurrent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test

class ParallelResultsTest {
    @Test fun readyResultsPassASlowPeerAndCancellationStopsThatPeer(): Unit = runBlocking {
        val slowStarted = CompletableDeferred<Unit>()
        val slowStopped = CompletableDeferred<Unit>()
        val ready = withTimeout(1000) {
            listOf("slow", "fast").parallelResults(2) { item ->
                if (item == "slow") {
                    slowStarted.complete(Unit)
                    try { awaitCancellation() } finally { slowStopped.complete(Unit) }
                } else { slowStarted.await(); "ready" }
            }.first()
        }
        assertEquals("fast", ready.first)
        assertEquals("ready", ready.second.getOrThrow())
        withTimeout(1000) { slowStopped.await() }
    }

    @Test fun concurrencyIsBoundedAndAFailureDoesNotDropOtherResults(): Unit = runBlocking {
        var active = 0
        var maximum = 0
        val results = (1..12).toList().parallelResults(3) { item ->
            active++
            maximum = maxOf(maximum, active)
            try { delay(5); check(item != 4); item } finally { active-- }
        }.toList()
        assertEquals(3, maximum)
        assertEquals(12, results.size)
        assertEquals(11, results.count { it.second.isSuccess })
    }
}
