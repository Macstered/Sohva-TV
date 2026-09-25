package com.sohva.tv.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sohva.tv.app.MainActivity
import com.sohva.tv.app.SohvaApplication
import kotlinx.coroutines.launch

/**
 * The one alarm of spec 22 REM-FR-12: an alarm clock for the next fire time (exact, fires in
 * Doze), replaced on every reschedule. On Android 12+ exact alarms need "Alarms & reminders"; when
 * that is denied it falls back to an inexact alarm allowed while idle, and a refusal never escapes.
 */
class ReminderAlarm(private val context: Context, private val log: (String) -> Unit) {
    private val manager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    private fun fire(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_FIRE).setClass(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets the alarm for [at], or cancels it when nothing is ahead. */
    fun set(at: Long?) {
        val operation = fire()
        if (at == null) {
            manager.cancel(operation)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                inexact(at, operation)
            } else {
                val show = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
            }
        } catch (e: SecurityException) {
            log("exact alarm refused: ${e.javaClass.simpleName}")
            inexact(at, operation)
        }
    }

    private fun inexact(at: Long, operation: PendingIntent) {
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } catch (e: SecurityException) {
            log("alarm refused: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        /** Explicit (component-addressed) fire broadcast, beta 23's action name (spec 22 §7). */
        const val ACTION_FIRE: String = "com.streammate.tv.REMINDER_FIRE"
    }
}

/**
 * Fires due reminders and sets the next alarm (REM-FR-13, -14): on the alarm, after a reboot, an
 * update of the app, and a clock or zone change. The work runs off the main thread inside the
 * broadcast's `goAsync` window; the table holds tens of rows at most.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Only the alarm and the system's own broadcasts reschedule; anything else is ignored.
        if (intent.action !in ACTIONS) return
        val graph = (context.applicationContext as SohvaApplication).graph
        if (!graph.flags.reminders) return
        // Null outside a real broadcast (a direct call in a test).
        val pending: PendingResult? = goAsync()
        graph.appScope.launch {
            try {
                graph.reminders.reschedule()
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            ReminderAlarm.ACTION_FIRE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
