package com.streammate.tv.core.diagnostics

import android.util.Log
import com.streammate.tv.core.security.SecretRedactor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The app's own log: the last few hundred lines the import services, the
 * refresh worker, the player and the updater wrote, kept in memory so
 * "Save diagnostics" can hand a tester a file that explains what happened.
 *
 * Every line goes through [SecretRedactor] before it is kept, so an address
 * with credentials in it or a key in a message never reaches the buffer, let
 * alone the file. Lines also go to logcat for anyone with a cable.
 */
object DiagnosticsLog {
    const val CAPACITY = 600

    private val lock = Any()
    private val lines = ArrayDeque<String>(CAPACITY)
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Replaceable so a test can pin the timestamps. */
    @Volatile
    var clock: () -> Long = System::currentTimeMillis

    /** Replaceable so a JVM test does not need logcat. */
    @Volatile
    var mirrorToLogcat: Boolean = true

    fun i(tag: String, message: String) = append('I', tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = append('W', tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = append('E', tag, message, error)

    /** The kept lines, oldest first. */
    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    fun clear() = synchronized(lock) { lines.clear() }

    private fun append(level: Char, tag: String, message: String, error: Throwable?) {
        val redacted = SecretRedactor.redact(message).orEmpty()
        val cause = error?.let { " · ${it::class.java.simpleName}: ${SecretRedactor.redact(it.message) ?: "no message"}" }.orEmpty()
        val stamp = timeFormat.format(Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()))
        val line = "$stamp $level/$tag: $redacted$cause"
        synchronized(lock) {
            if (lines.size >= CAPACITY) lines.removeFirst()
            lines.addLast(line)
        }
        if (mirrorToLogcat) {
            runCatching {
                when (level) {
                    'E' -> Log.e(LOGCAT_TAG, "$tag: $redacted$cause")
                    'W' -> Log.w(LOGCAT_TAG, "$tag: $redacted$cause")
                    else -> Log.i(LOGCAT_TAG, "$tag: $redacted$cause")
                }
            }
        }
    }

    private const val LOGCAT_TAG = "SohvaTV"
}
