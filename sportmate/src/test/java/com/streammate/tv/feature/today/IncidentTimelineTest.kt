package com.streammate.tv.feature.today

import com.streammate.tv.core.model.FootballIncident
import com.streammate.tv.core.model.FootballIncidentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentTimelineTest {

    private fun incident(
        minute: Int,
        extra: Int? = null,
        team: String? = HOME,
        id: String = "$minute-$team",
    ) = FootballIncident(
        id = id,
        eventId = "event",
        elapsedMinutes = minute,
        extraMinutes = extra,
        kind = FootballIncidentKind.GOAL,
        detail = "Goal",
        comments = null,
        teamName = team,
        actorName = null,
        relatedName = null,
    )

    @Test
    fun `kick-off sits at the start and the final whistle at the end`() {
        val layout = IncidentTimeline.layout(listOf(incident(0), incident(90)), HOME, AWAY)

        assertEquals(0f, layout.markers.first().position, 0.001f)
        assertEquals(1f, layout.markers.last().position, 0.001f)
        assertEquals(0.5f, layout.halfTimePosition, 0.001f)
    }

    @Test
    fun `stoppage time counts towards the minute`() {
        val layout = IncidentTimeline.layout(listOf(incident(45, extra = 2)), HOME, AWAY)

        // 47 minutes into a 90 minute axis, not 45.
        assertEquals(47f / 90f, layout.markers.single().position, 0.001f)
    }

    @Test
    fun `a match that runs long stretches the axis instead of stacking on the edge`() {
        val layout = IncidentTimeline.layout(
            listOf(incident(90), incident(105), incident(120)),
            HOME,
            AWAY,
        )

        val positions = layout.markers.map(TimelineMarker::position)
        assertEquals(listOf(90f / 120f, 105f / 120f, 1f), positions.map { it })
        assertTrue("half time moves with the axis", layout.halfTimePosition < 0.5f)
    }

    @Test
    fun `markers land on the side of the team that caused them`() {
        val layout = IncidentTimeline.layout(
            listOf(incident(10, team = HOME), incident(20, team = AWAY)),
            HOME,
            AWAY,
        )

        assertEquals(TimelineSide.HOME, layout.markers[0].side)
        assertEquals(TimelineSide.AWAY, layout.markers[1].side)
    }

    @Test
    fun `an incident with no team is not guessed onto a side`() {
        val layout = IncidentTimeline.layout(
            listOf(incident(10, team = null), incident(20, team = "Someone else")),
            HOME,
            AWAY,
        )

        assertEquals(TimelineSide.NEUTRAL, layout.markers[0].side)
        assertEquals(TimelineSide.NEUTRAL, layout.markers[1].side)
    }

    @Test
    fun `markers come back in match order however they arrived`() {
        val layout = IncidentTimeline.layout(
            listOf(incident(70), incident(12), incident(45, extra = 3)),
            HOME,
            AWAY,
        )

        assertEquals(listOf(12, 45, 70), layout.markers.map { it.incident.elapsedMinutes })
    }

    @Test
    fun `an empty match still has a usable axis`() {
        val layout = IncidentTimeline.layout(emptyList(), HOME, AWAY)

        assertTrue(layout.markers.isEmpty())
        assertEquals(0.5f, layout.halfTimePosition, 0.001f)
    }

    private companion object {
        const val HOME = "Home FC"
        const val AWAY = "Away United"
    }
}
