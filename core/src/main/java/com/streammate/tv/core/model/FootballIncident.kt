package com.streammate.tv.core.model

enum class FootballIncidentKind {
    GOAL,
    CARD,
    SUBSTITUTION,
    VAR,
    OTHER,
}

data class FootballIncident(
    val id: String,
    val eventId: String,
    val elapsedMinutes: Int,
    val extraMinutes: Int?,
    val kind: FootballIncidentKind,
    val detail: String,
    val comments: String?,
    val teamName: String?,
    val actorName: String?,
    val relatedName: String?,
) {
    val timeLabel: String
        get() = buildString {
            append(elapsedMinutes)
            extraMinutes?.takeIf { it > 0 }?.let { append("+$it") }
            append("′")
        }
}
