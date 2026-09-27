package com.sohva.tv.feature.trakt.protocol

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one door every Trakt request goes through (decision "Trakt request pacing"), shared by the
 * sign-in, identity and API clients of the app:
 * - after any rate-limit answer (429), nothing is sent to Trakt until its Retry-After has passed
 *   (60 s when it gives none, at most an hour); a request in that time fails at once as
 *   RATE_LIMITED with the seconds left, without reaching the network;
 * - writes (POST) leave at least [WRITE_GAP_MS] apart, Trakt's "1 call per second" for writes;
 * - every request sent is counted, for the diagnostics log (counts only).
 * Time is monotonic, so a clock change never lifts or extends a wait.
 */
class TraktGate(private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val blockedUntil = AtomicLong(0)
    private val writes = Mutex()
    private var lastWrite = Long.MIN_VALUE / 2
    private val sent = AtomicLong(0)

    /** Requests sent since the process started. */
    val requests: Long get() = sent.get()

    /** Seconds still to wait, 0 when requests may go. */
    fun waitSeconds(): Long {
        val left = blockedUntil.get() - monotonicMs()
        return if (left <= 0) 0 else (left + 999) / 1_000
    }

    /** Before a request: refuses during a wait; spaces writes. */
    suspend fun enter(write: Boolean) {
        waitSeconds().takeIf { it > 0 }?.let { throw TraktException(TraktFailure.RATE_LIMITED, it) }
        if (write) {
            writes.withLock {
                val gap = lastWrite + WRITE_GAP_MS - monotonicMs()
                if (gap > 0) delay(gap)
                // A 429 may have arrived while this write waited its turn.
                waitSeconds().takeIf { it > 0 }?.let { throw TraktException(TraktFailure.RATE_LIMITED, it) }
                lastWrite = monotonicMs()
            }
        }
        sent.incrementAndGet()
    }

    /** A 429: every Trakt request waits for [retryAfterSeconds] (default [DEFAULT_WAIT_S]). */
    fun limited(retryAfterSeconds: Long?) {
        val seconds = (retryAfterSeconds?.takeIf { it > 0 } ?: DEFAULT_WAIT_S).coerceAtMost(TraktHttp.MAX_RETRY_AFTER)
        val until = monotonicMs() + seconds * 1_000
        blockedUntil.accumulateAndGet(until) { a, b -> maxOf(a, b) }
    }

    companion object {
        const val WRITE_GAP_MS: Long = 1_000
        const val DEFAULT_WAIT_S: Long = 60
    }
}
