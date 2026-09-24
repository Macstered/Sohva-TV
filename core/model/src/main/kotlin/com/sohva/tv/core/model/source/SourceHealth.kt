package com.sohva.tv.core.model.source

import com.sohva.tv.core.model.error.AppError
import kotlin.math.abs

/** The refresh state of one kind of a source (spec 10 SRC-FR-76…78). */
enum class RefreshState(val id: String) {
    IDLE("idle"),
    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed"),
    ;

    companion object {
        fun fromStored(value: String?): RefreshState = entries.firstOrNull { it.id == value } ?: IDLE
    }
}

/** What Settings shows about one kind of a source: its state, count and failure (SRC-FR-39). */
data class SourceHealth(
    val sourceId: String,
    val kind: RefreshKind,
    val state: RefreshState,
    /** The last failure while [state] is FAILED; null otherwise or when it came from a newer build. */
    val error: AppError?,
    val itemCount: Int,
    val consecutiveFailures: Int,
)

/**
 * The EPG time correction as the source page shows it (spec 10 SRC-FR-16 item 6): `0 min`,
 * `+1 h`, `−30 min`, `+1 h 30 min` — hours and minutes, never "90 min", with U+2212 for minus.
 * Beta 23 did not translate these labels.
 */
object EpgOffsetLabel {
    private const val MINUS = '−'

    fun of(minutes: Int): String {
        if (minutes == 0) return "0 min"
        val sign = if (minutes < 0) MINUS else '+'
        val hours = abs(minutes) / 60
        val rest = abs(minutes) % 60
        return when {
            hours == 0 -> "$sign$rest min"
            rest == 0 -> "$sign$hours h"
            else -> "$sign$hours h $rest min"
        }
    }
}
