package com.sohva.tv.core.model.sport

/** The Today rules that do not draw anything (spec 60 §4.5, §4.2, §4.9). */
object TodayRules {
    /** SPORT-FR-49: live, scheduled, disrupted, finished; then kick-off minute; then competition. */
    val ORDER: Comparator<SportEvent> = compareBy<SportEvent>({ it.status.order }, { it.startMinuteOfDay }, { it.competition.lowercase() })

    /**
     * SPORT-FR-26: 5 minutes while a game is live, 10 when a scheduled game starts within two hours,
     * else 30.
     */
    fun pollingMinutes(events: List<SportEvent>, now: Long): Int = when {
        events.any { it.status == EventStatus.LIVE } -> 5
        events.any { it.status == EventStatus.SCHEDULED && it.startMillis in now..(now + TWO_HOURS) } -> 10
        else -> 30
    }

    /** SPORT-FR-99: live games by start, then scheduled ones starting within 3 hours by start; at most 4. */
    fun ticker(events: List<SportEvent>, now: Long): List<SportEvent> {
        val live = events.filter { it.status == EventStatus.LIVE }.sortedBy { it.startMillis }
        val soon = events.filter { it.status == EventStatus.SCHEDULED && it.startMillis in now..(now + THREE_HOURS) }.sortedBy { it.startMillis }
        return (live + soon).take(TICKER_ROWS)
    }

    private const val TWO_HOURS = 2 * 60 * 60_000L
    private const val THREE_HOURS = 3 * 60 * 60_000L
    private const val TICKER_ROWS = 4
}
