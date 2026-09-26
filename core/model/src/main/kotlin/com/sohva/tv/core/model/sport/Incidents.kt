package com.sohva.tv.core.model.sport

/** SPORT-FR-92: goal, card, substitution, VAR, or another event. */
enum class IncidentKind { GOAL, CARD, SUBSTITUTION, VAR, OTHER }

/** One football match event (SPORT-FR-92). [detail] is null where beta 23 wrote a fixed Finnish word. */
data class Incident(
    val id: String,
    val minute: Int,
    val extra: Int,
    val kind: IncidentKind,
    val detail: String?,
    val comments: String?,
    val team: String?,
    val actor: String?,
    val related: String?,
) {
    /** "45+2′", the extra minutes only when there are some. */
    val timeLabel: String get() = if (extra > 0) "$minute+$extra′" else "$minute′"
}

/** Where a match event sits on the timeline band (SPORT-FR-93). */
enum class TimelineSide { HOME, AWAY, NEUTRAL }

data class TimelineMarker(val incident: Incident, val position: Float, val side: TimelineSide)

/**
 * The timeline band (SPORT-FR-93): the axis spans 90 minutes or the latest minute if later,
 * stoppage time counts, markers are ordered by minute; a team that is neither side sits on the axis.
 */
data class IncidentTimeline(val markers: List<TimelineMarker>, val halfTime: Float) {
    companion object {
        fun of(incidents: List<Incident>, home: String, away: String): IncidentTimeline {
            val minutes = incidents.map { it.minute + it.extra }
            val span = maxOf(REGULATION, minutes.maxOrNull() ?: 0).toFloat()
            val homeKey = home.trim().lowercase()
            val awayKey = away.trim().lowercase()
            val markers = incidents.sortedBy { it.minute + it.extra }.map { incident ->
                val team = incident.team?.trim()?.lowercase()
                val side = when (team) {
                    homeKey -> TimelineSide.HOME
                    awayKey -> TimelineSide.AWAY
                    else -> TimelineSide.NEUTRAL
                }
                TimelineMarker(incident, ((incident.minute + incident.extra) / span).coerceIn(0f, 1f), side)
            }
            return IncidentTimeline(markers, HALF / span)
        }

        private const val REGULATION = 90
        private const val HALF = 45f
    }
}
