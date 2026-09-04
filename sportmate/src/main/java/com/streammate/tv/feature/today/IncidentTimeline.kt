package com.streammate.tv.feature.today

import com.streammate.tv.core.model.FootballIncident

/** Which side of the timeline axis a marker belongs on. */
enum class TimelineSide { HOME, AWAY, NEUTRAL }

/**
 * One incident placed along the match, where [position] is 0 at kick-off and 1
 * at the final whistle.
 */
data class TimelineMarker(
    val incident: FootballIncident,
    val position: Float,
    val side: TimelineSide,
)

/** The shape of a match: where its markers sit, and where half time falls. */
data class TimelineLayout(
    val markers: List<TimelineMarker>,
    val halfTimePosition: Float,
)

/**
 * Turns a list of incidents into positions along a horizontal axis.
 *
 * Kept out of the composable because it is the part with rules worth pinning
 * down: stoppage time counts towards the minute, a match that runs long
 * stretches the axis rather than pushing markers off the end, and an incident
 * with no team sits on the axis instead of being guessed onto a side.
 */
object IncidentTimeline {

    fun layout(
        incidents: List<FootballIncident>,
        homeTeam: String?,
        awayTeam: String?,
    ): TimelineLayout {
        val minutes = incidents.map(::minuteOf)
        // Extra time and stoppage stretch the axis. Clamping to 90 instead would
        // pile every late goal onto the right-hand edge, which is where they
        // matter most.
        val span = maxOf(REGULATION_MINUTES, minutes.maxOrNull() ?: REGULATION_MINUTES)

        val markers = incidents
            .sortedBy(::minuteOf)
            .map { incident ->
                TimelineMarker(
                    incident = incident,
                    position = (minuteOf(incident).toFloat() / span).coerceIn(0f, 1f),
                    side = sideOf(incident, homeTeam, awayTeam),
                )
            }

        return TimelineLayout(
            markers = markers,
            halfTimePosition = HALF_TIME_MINUTES.toFloat() / span,
        )
    }

    private fun minuteOf(incident: FootballIncident): Int =
        incident.elapsedMinutes + (incident.extraMinutes ?: 0)

    private fun sideOf(
        incident: FootballIncident,
        homeTeam: String?,
        awayTeam: String?,
    ): TimelineSide {
        val team = incident.teamName?.trim()?.takeIf(String::isNotEmpty) ?: return TimelineSide.NEUTRAL
        return when {
            homeTeam != null && team.equals(homeTeam.trim(), ignoreCase = true) -> TimelineSide.HOME
            awayTeam != null && team.equals(awayTeam.trim(), ignoreCase = true) -> TimelineSide.AWAY
            else -> TimelineSide.NEUTRAL
        }
    }

    private const val REGULATION_MINUTES = 90
    private const val HALF_TIME_MINUTES = 45
}
