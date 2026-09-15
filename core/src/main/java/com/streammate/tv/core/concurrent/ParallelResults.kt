package com.streammate.tv.core.concurrent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Emits each independent result when it finishes, with a bounded number of active requests. */
fun <T, R> Collection<T>.parallelResults(concurrency: Int = 3, fetch: suspend (T) -> R): Flow<Pair<T, Result<R>>> = channelFlow {
    val permits = Semaphore(concurrency)
    for (item in this@parallelResults) launch {
        val result = permits.withPermit {
            try { Result.success(fetch(item)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
        }
        send(item to result)
    }
}
