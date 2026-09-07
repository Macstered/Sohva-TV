package com.streammate.tv.core.reminders

import com.streammate.tv.core.database.ReminderEntity

/**
 * When reminders fire, as arithmetic the scheduler and its tests share.
 *
 * A reminder fires one minute before its start, so the viewer is on the
 * channel for kick-off rather than a minute into it. One that was missed by
 * more than half an hour, a TV that was off, is dropped rather than fired late.
 */
object ReminderSchedule {
    const val LEAD_MILLIS = 60_000L
    const val STALE_AFTER_START_MILLIS = 30L * 60_000L

    fun fireAt(reminder: ReminderEntity): Long = reminder.startEpochMillis - LEAD_MILLIS

    /** Reminders whose time has come and that are not yet stale, oldest first. */
    fun due(reminders: List<ReminderEntity>, nowEpochMillis: Long): List<ReminderEntity> =
        reminders.filter { fireAt(it) <= nowEpochMillis && !isStale(it, nowEpochMillis) }
            .sortedBy(ReminderEntity::startEpochMillis)

    /** The next moment an alarm is needed, or null when nothing is pending. */
    fun nextFireAt(reminders: List<ReminderEntity>, nowEpochMillis: Long): Long? =
        reminders.map(::fireAt).filter { it > nowEpochMillis }.minOrNull()

    fun isStale(reminder: ReminderEntity, nowEpochMillis: Long): Boolean =
        reminder.startEpochMillis < nowEpochMillis - STALE_AFTER_START_MILLIS

    /** Starts older than this can be deleted. */
    fun staleCutoff(nowEpochMillis: Long): Long = nowEpochMillis - STALE_AFTER_START_MILLIS
}
