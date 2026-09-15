package com.streammate.tv.iptv.metadata

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Share identical requests; cancelling the final reader also cancels the underlying HTTP call. */
internal class MetadataRequests<T> {
    private class Flight<T>(val result: Deferred<T>, var readers: Int = 0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val flights = mutableMapOf<String, Flight<T>>()

    suspend fun read(key: String, fetch: suspend () -> T): T {
        val flight = mutex.withLock {
            flights.getOrPut(key) { Flight(scope.async(start = CoroutineStart.LAZY) { fetch() }) }
                .also { it.readers++ }
        }
        try {
            return flight.result.await()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (--flight.readers == 0) {
                        if (flights[key] === flight) flights.remove(key)
                        flight.result.cancel()
                    }
                }
            }
        }
    }
}
