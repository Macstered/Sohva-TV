package com.sohva.tv.core.model.reminder

/** A programme or a Sohva Sport match reminder (spec 22 REM-FR-01). Household, not per profile. */
data class Reminder(
    val id: String,
    val kind: ReminderKind,
    val eventId: String?,
    val channelKey: String?,
    val title: String,
    val subtitle: String?,
    val startAt: Long,
    val createdAt: Long,
) {
    val fireAt: Long get() = startAt - ReminderSchedule.LEAD_MS

    override fun toString(): String = "Reminder($id, start=$startAt)"
}

enum class ReminderKind(val stored: String) {
    PROGRAMME("programme"),
    EVENT("event"),
    ;

    companion object {
        fun fromStored(value: String): ReminderKind? = entries.firstOrNull { it.stored == value }
    }
}

object ReminderIds {
    fun programme(channelKey: String, programmeKey: String): String = "programme:$channelKey:$programmeKey"

    fun event(eventId: String): String = "event:$eventId"
}

/**
 * The schedule arithmetic (REM-FR-10, -11). [plan] fires what is due **before** asking for the next
 * alarm: beta 23 returned early when nothing lay ahead, so a reminder due now with no other
 * pending one was never fired (spec 22 §10).
 */
object ReminderSchedule {
    const val LEAD_MS: Long = 60_000
    const val STALE_MS: Long = 30 * 60_000L
    private const val LINGER_AFTER_START_MS = 2 * 60_000L
    private const val MIN_LINGER_MS = 20_000L

    fun isStale(reminder: Reminder, now: Long): Boolean = reminder.startAt < now - STALE_MS

    fun due(reminders: List<Reminder>, now: Long): List<Reminder> =
        reminders.filter { it.fireAt <= now && !isStale(it, now) }.sortedBy { it.startAt }

    fun next(reminders: List<Reminder>, now: Long): Long? = reminders.asSequence().map { it.fireAt }.filter { it > now }.minOrNull()

    /** What one reschedule does: delete [stale], fire [due] (and delete them), then set one alarm at [nextFireAt]. */
    data class Plan(val stale: List<Reminder>, val due: List<Reminder>, val nextFireAt: Long?)

    fun plan(reminders: List<Reminder>, now: Long): Plan {
        val stale = reminders.filter { isStale(it, now) }
        val due = due(reminders, now)
        val left = reminders.filter { it !in stale && it !in due }
        return Plan(stale, due, next(left, now))
    }

    /** How long an alert stays up once shown (REM-FR-23): until two minutes after the start, at least 20 s. */
    fun lingerMs(reminder: Reminder, shownAt: Long): Long = maxOf(reminder.startAt + LINGER_AFTER_START_MS - shownAt, MIN_LINGER_MS)

    /** The alert says "starts in a minute" while the start is still ahead, else "starts now" (REM-FR-21). */
    fun startsSoon(reminder: Reminder, now: Long): Boolean = reminder.startAt > now
}

/**
 * Reminders fired but not answered, oldest start first, one alert at a time (REM-FR-24, -25).
 * Lives in memory only; not thread-safe on its own (the owner confines it to one thread).
 */
class RingingQueue {
    private val queue = ArrayList<Reminder>()

    val current: Reminder? get() = queue.firstOrNull()

    val size: Int get() = queue.size

    /** Adds [reminders] not already queued. Returns true when the first entry changed. */
    fun ring(reminders: List<Reminder>): Boolean {
        val before = current
        for (r in reminders) if (queue.none { it.id == r.id }) queue += r
        queue.sortBy { it.startAt }
        return current != before
    }

    /** Removes [id]; the next queued reminder, if any, becomes [current]. */
    fun dismiss(id: String) {
        queue.removeAll { it.id == id }
    }

    /** Drops queued alerts whose start is more than 30 minutes past (spec 22 §8, rebuild). */
    fun dropStale(now: Long) {
        queue.removeAll { ReminderSchedule.isStale(it, now) }
    }
}
