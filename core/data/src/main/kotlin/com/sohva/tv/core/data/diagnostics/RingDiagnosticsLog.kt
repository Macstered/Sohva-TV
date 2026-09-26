package com.sohva.tv.core.data.diagnostics

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.diagnostics.Redactor
import com.sohva.tv.core.model.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The diagnostics log (plan/03 §4.14, spec 72 ABOUT-FR-26, -27): the last [capacity] lines in
 * memory, each redacted before it is stored and cut to 500 characters, as
 * `yyyy-MM-dd HH:mm:ss L/tag: message · Exception: cause` in the TV's zone. Mirrored to logcat
 * through [mirror] without the time; written to a file only when the viewer saves diagnostics.
 * Bounded: the oldest line is dropped when full (≈ 0.3 MB at most, ABOUT-NFR-05).
 */
class RingDiagnosticsLog(
    private val clock: Clock,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val mirror: (level: Char, line: String) -> Unit = { _, _ -> },
) : DiagnosticsLog {
    private val lines = ArrayDeque<String>(capacity)

    override fun info(event: String, message: String) = append('I', event, Redactor.redact(message))

    override fun error(event: String, message: String?, error: Throwable?) {
        val cause = error?.let { "${it.javaClass.simpleName}: ${Redactor.redact(it.message) ?: "no message"}" }
        append('E', event, listOfNotNull(Redactor.redact(message), cause).joinToString(" · "))
    }

    override fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    private fun append(level: Char, event: String, message: String?) {
        val body = "$level/$event: ${message ?: "-"}".take(MAX_LINE)
        val line = "${TIME.format(Instant.ofEpochMilli(clock.wallMillis()).atZone(ZoneId.systemDefault()))} $body"
        synchronized(lines) {
            if (lines.size == capacity) lines.removeFirst()
            lines.addLast(line)
        }
        mirror(level, body)
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 600
        const val MAX_LINE: Int = 500
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
