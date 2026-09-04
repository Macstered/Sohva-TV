package com.streammate.tv.iptv.playback

import java.util.concurrent.atomic.AtomicBoolean

class SourceConnectionLimiter {
    private val lock = Any()
    private val activeBySource = mutableMapOf<String, Int>()

    fun tryAcquire(sourceId: String, connectionLimit: Int): ConnectionLease? {
        require(connectionLimit > 0) { "Connection limit must be positive" }
        synchronized(lock) {
            val active = activeBySource[sourceId] ?: 0
            if (active >= connectionLimit) return null
            activeBySource[sourceId] = active + 1
        }
        return ConnectionLease { release(sourceId) }
    }

    fun activeConnections(sourceId: String): Int = synchronized(lock) {
        activeBySource[sourceId] ?: 0
    }

    private fun release(sourceId: String) {
        synchronized(lock) {
            val remaining = (activeBySource[sourceId] ?: 1) - 1
            if (remaining <= 0) activeBySource.remove(sourceId) else activeBySource[sourceId] = remaining
        }
    }
}

class ConnectionLease internal constructor(
    private val release: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }
}
