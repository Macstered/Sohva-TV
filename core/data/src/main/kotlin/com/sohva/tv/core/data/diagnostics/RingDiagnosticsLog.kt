package com.sohva.tv.core.data.diagnostics

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.diagnostics.Redactor
import com.sohva.tv.core.model.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The diagnostics log (plan/03 §4.14): the last [capacity] lines in memory, each redacted before
 * it is stored. Mirrored to logcat through [mirror]; written to a file only when the viewer saves
 * diagnostics. Bounded: the oldest line is dropped when full.
 */
class RingDiagnosticsLog(
    private val clock: Clock,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val mirror: (level: Char, line: String) -> Unit = { _, _ -> },
) : DiagnosticsLog {
    private val lines = ArrayDeque<String>(capacity)

    override fun info(event: String, message: String) = append('I', event, Redactor.redact(message))

    override fun error(event: String, message: String?, error: Throwable?) {
        val detail = listOfNotNull(
            Redactor.redact(message),
            error?.let { "${it.javaClass.simpleName}: ${Redactor.redact(it.message) ?: "-"}" },
        ).joinToString(" | ")
        append('E', event, detail)
    }

    override fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    private fun append(level: Char, event: String, message: String?) {
        val time = TIME.format(Instant.ofEpochMilli(clock.wallMillis()))
        val line = "$time $level $event: ${message ?: "-"}"
        synchronized(lines) {
            if (lines.size == capacity) lines.removeFirst()
            lines.addLast(line)
        }
        mirror(level, line)
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 600
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC)
    }
}
