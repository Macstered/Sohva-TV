package com.sohva.tv.core.sync

import com.sohva.tv.core.model.concurrent.AppDispatchers
import com.sohva.tv.core.model.concurrent.WorkOrigin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Download-and-parse on the bulk thread, writes on the bulk writer thread, joined by a channel of
 * one batch (spec 10 SRC-L-08): the network, the parser and SQLite work at the same time, and at
 * most three batches exist (one being parsed, one queued, one being written). When the queue is
 * full the parser blocks, which is the back-pressure.
 *
 * [produce] hands batches to its `send` from blocking reader code; if the writer fails or the
 * import is cancelled, `send` throws so the parser stops at once.
 */
internal suspend fun <B : Any> pipeline(
    dispatchers: AppDispatchers,
    produce: suspend (send: (B) -> Unit) -> Unit,
    consume: suspend (B) -> Unit,
): Unit = coroutineScope {
    val queue = Channel<B>(capacity = 1)
    launch(dispatchers.bulkWrite) {
        try {
            for (batch in queue) consume(batch)
        } catch (e: Throwable) {
            // Wakes a parser blocked on a full queue.
            queue.cancel(CancellationException("the writer stopped", e))
            throw e
        }
    }
    try {
        withContext(dispatchers.bulk) {
            produce { batch ->
                val sent = queue.trySendBlocking(batch)
                if (sent.isFailure) throw sent.exceptionOrNull() ?: CancellationException("the import stopped")
            }
        }
        queue.close()
    } catch (e: Throwable) {
        queue.cancel(CancellationException("the parser stopped", e))
        throw e
    }
}

/** Progress of one running import, observed by Settings and the guide (spec 10 SRC-FR-94). */
data class ImportProgress(val sourceId: String, val kind: String, val rows: Int)

/** One import's identity while it runs. */
internal class ImportJob(
    val sourceId: String,
    val origin: WorkOrigin,
    /** This import's number: the guide's snapshot and the rows' generation. */
    val generation: Long,
    private val report: (Int) -> Unit,
) {
    var rows: Int = 0
        private set

    fun advance(count: Int) {
        rows += count
        report(rows)
    }
}
