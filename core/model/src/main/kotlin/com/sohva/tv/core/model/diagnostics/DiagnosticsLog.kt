package com.sohva.tv.core.model.diagnostics

/**
 * The app's only log (plan/03 §4.14): a bounded, redacted ring kept in memory and written to a
 * file only when the viewer saves diagnostics. Lines carry counts and durations, never titles,
 * ids, addresses or secrets.
 */
interface DiagnosticsLog {
    fun info(event: String, message: String)

    fun error(event: String, message: String?, error: Throwable? = null)

    /** Oldest first. */
    fun snapshot(): List<String>
}
