package com.streammate.tv.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import android.provider.Settings
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.streammate.tv.R
import com.streammate.tv.core.database.ReminderEntity
import com.streammate.tv.core.database.RemindersDao
import com.streammate.tv.core.diagnostics.DiagnosticsLog
import com.streammate.tv.core.reminders.ReminderSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * What a notification, or anything else outside the app, asked it to open.
 * The activity parses the intent; the app reads the request once it is up.
 */
sealed interface OpenRequest {
    data class Channel(val channelId: String) : OpenRequest
    data class Event(val eventId: String) : OpenRequest

    companion object {
        const val EXTRA_CHANNEL = "com.streammate.tv.OPEN_CHANNEL"
        const val EXTRA_EVENT = "com.streammate.tv.OPEN_EVENT"

        /** The request an intent carries, if any; a channel wins over an event when both are set. */
        fun from(channelId: String?, eventId: String?): OpenRequest? = when {
            !channelId.isNullOrBlank() -> Channel(channelId)
            !eventId.isNullOrBlank() -> Event(eventId)
            else -> null
        }

        fun fromIntent(intent: Intent?): OpenRequest? =
            from(intent?.getStringExtra(EXTRA_CHANNEL), intent?.getStringExtra(EXTRA_EVENT))
    }
}

/** The stored reminders, and the alarm that follows them. */
class ReminderRepository(
    private val context: Context,
    private val dao: RemindersDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The ids with a reminder set, for the buttons that show it. */
    fun observeIds(): Flow<Set<String>> = dao.observeAll().map { list -> list.map { it.id }.toSet() }

    suspend fun set(reminder: ReminderEntity) {
        dao.upsert(reminder)
        DiagnosticsLog.i("reminders", "set ${reminder.id} for ${reminder.startEpochMillis}")
        ReminderScheduler.reschedule(context, dao, clock())
    }

    suspend fun remove(id: String) {
        dao.delete(id)
        DiagnosticsLog.i("reminders", "removed $id")
        ReminderScheduler.reschedule(context, dao, clock())
    }
}

/**
 * One alarm at a time, for the next reminder due. `setAlarmClock` is exact
 * and needs no special permission; WorkManager can be minutes late, which is
 * no good for kick-off.
 */
object ReminderScheduler {
    const val ACTION_FIRE = "com.streammate.tv.REMINDER_FIRE"

    suspend fun reschedule(context: Context, dao: RemindersDao, nowEpochMillis: Long) {
        if (!AppRuntimePolicy.forPackage(context.packageName).remindersAllowed) return
        dao.deleteStartedBefore(ReminderSchedule.staleCutoff(nowEpochMillis))
        val next = ReminderSchedule.nextFireAt(dao.all(), nowEpochMillis)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = fireIntent(context)
        if (next == null) {
            alarms.cancel(operation)
            return
        }
        // Firing now-ish for anything already due keeps a reminder set a few
        // seconds before its time from waiting for the next one.
        val due = ReminderSchedule.due(dao.all(), nowEpochMillis)
        if (due.isNotEmpty()) context.sendBroadcast(Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE))
        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(next, launchIntent(context, null)), operation)
        DiagnosticsLog.i("reminders", "next alarm at $next")
    }

    private fun fireIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun launchIntent(context: Context, request: OpenRequest?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        when (request) {
            is OpenRequest.Channel -> intent.putExtra(OpenRequest.EXTRA_CHANNEL, request.channelId)
            is OpenRequest.Event -> intent.putExtra(OpenRequest.EXTRA_EVENT, request.eventId)
            null -> Unit
        }
        return PendingIntent.getActivity(
            context,
            request.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Fires due reminders as notifications, and puts the alarm back after a reboot. */
class ReminderReceiver : BroadcastReceiver() {
    /**
     * The TV is showing something else: come forward so the alert dialog is
     * seen. The start is refused silently on some Android versions when the
     * app has not been used for a while; the notification stays as fallback.
     */
    private fun bringForward(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(EXTRA_REMINDER_ALERT, true)
        runCatching { context.startActivity(intent) }
            .onSuccess { DiagnosticsLog.i("reminders", "brought the app forward") }
            .onFailure { DiagnosticsLog.w("reminders", "could not bring the app forward", it) }
    }

    companion object {
        const val EXTRA_REMINDER_ALERT = "com.streammate.tv.REMINDER_ALERT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!AppRuntimePolicy.forPackage(context.packageName).remindersAllowed) return
        val action = intent.action
        if (action != ReminderScheduler.ACTION_FIRE && action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as StreamMateApplication).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = container.remindersDao
                val now = System.currentTimeMillis()
                if (action == ReminderScheduler.ACTION_FIRE) {
                    val due = ReminderSchedule.due(dao.all(), now)
                    container.reminderAlerts.ring(due)
                    due.forEach { reminder ->
                        ReminderNotifications.show(context, reminder)
                        dao.delete(reminder.id)
                    }
                    DiagnosticsLog.i("reminders", "fired ${due.size}")
                    if (due.isNotEmpty() && !StreamMateForegroundState.isForeground) bringForward(context)
                }
                ReminderScheduler.reschedule(context, dao, now)
            } catch (error: Throwable) {
                DiagnosticsLog.w("reminders", "firing failed", error)
            } finally {
                pending.finish()
            }
        }
    }
}

object ReminderNotifications {
    const val CHANNEL_ID = "reminders"

    fun show(context: Context, reminder: ReminderEntity) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            DiagnosticsLog.w("reminders", "notifications are off; ${reminder.id} not shown")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminders_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
            manager.createNotificationChannel(channel)
        }
        val request = reminder.channelId?.let { OpenRequest.Channel(it) }
            ?: reminder.eventId?.let { OpenRequest.Event(it) }
        val text = if (reminder.channelId != null) {
            context.getString(R.string.reminder_notification_watch)
        } else {
            context.getString(R.string.reminder_notification_open_card)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.reminder_notification_title, reminder.title))
            .setContentText(listOfNotNull(reminder.subtitle, text).joinToString(" · "))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(ReminderScheduler.launchIntent(context, request))
            .build()
        // Android 13 made posting a notification a runtime permission; the app
        // asks for it when a reminder is first set, and a viewer may have said no.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            DiagnosticsLog.w("reminders", "notification permission not granted; ${reminder.id} not shown")
            return
        }
        runCatching { manager.notify(reminder.id.hashCode(), notification) }
            .onFailure { DiagnosticsLog.w("reminders", "notify failed", it) }
    }
}

/**
 * The reminders that have just fired and are waiting for the viewer. Android
 * TV never shows an app's notification as a popup, only in its notification
 * panel, so the app presents a fired reminder itself: as a dialog when it is
 * up, and by coming forward with the dialog when it is not.
 */
class ReminderAlerts {
    val ringing = MutableStateFlow<List<ReminderEntity>>(emptyList())

    fun ring(reminders: List<ReminderEntity>) {
        if (reminders.isEmpty()) return
        val known = ringing.value.map { it.id }.toSet()
        ringing.value = ringing.value + reminders.filter { it.id !in known }
    }

    fun dismiss(id: String) {
        ringing.value = ringing.value.filter { it.id != id }
    }
}

/**
 * "Display over other apps", the one grant that lets a due reminder bring the
 * app forward while another app is on. Android TV shows no popup for a
 * closed app's notification, and refuses an activity start from the
 * background, unless the viewer has allowed this in the TV's settings.
 */
object ReminderOverlay {
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** Opens the TV's own screen for the grant; the general list if the app-specific one is missing. */
    fun openSettings(context: Context) {
        val specific = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val general = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (intent in listOf(specific, general)) {
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        DiagnosticsLog.w("reminders", "no settings screen for the overlay grant")
    }
}

/** The requests the activity has received and the app has not yet acted on. */
class OpenRequests {
    val pending = MutableStateFlow<OpenRequest?>(null)
    fun offer(request: OpenRequest?) { if (request != null) pending.value = request }
    fun consume() { pending.value = null }
}
