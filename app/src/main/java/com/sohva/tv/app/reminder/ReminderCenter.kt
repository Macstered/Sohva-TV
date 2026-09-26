package com.sohva.tv.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.MainActivity
import com.sohva.tv.core.model.reminder.Reminder
import com.sohva.tv.core.model.reminder.ReminderSchedule
import com.sohva.tv.core.model.reminder.RingingQueue
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Reminders at the app level (spec 22 §4.6–4.9): the store, the one alarm, the in-memory ringing
 * queue the shell's alert shows, the notification fallback, and bringing the app forward when
 * another app is on screen and the viewer allowed it. Nothing here runs in the Lab build.
 */
class ReminderCenter(private val graph: AppGraph) {
    private val app = graph.app

    /** Notification texts in the interface language, below Android 13 too (spec 74 L10N-FR-05). */
    private val texts get() = com.sohva.tv.app.AppLocales.texts(app)
    private val store get() = graph.data.reminders
    private val alarm = ReminderAlarm(app) { graph.diagnostics.info("reminders", it) }
    private val queue = RingingQueue()
    private val _current = MutableStateFlow<Reminder?>(null)
    private var linger: Job? = null

    /** The reminder the alert shows now, the oldest start first (REM-FR-21, -24). */
    val current: StateFlow<Reminder?> = _current.asStateFlow()

    private val _prompt = MutableStateFlow(PromptStep.NONE)

    /** The questions after the first reminder: the notification permission, then the overlay prompt (REM-FR-05). */
    val prompt: StateFlow<PromptStep> = _prompt.asStateFlow()

    /** The stored ids, for "Reminder set" in the guide (REM-NFR-03). */
    val ids: Flow<Set<String>> get() = if (graph.flags.reminders) store.ids else emptyFlow()

    /** Adds or removes [reminder] (REM-FR-04) and reschedules (REM-FR-06). True when it was added. */
    suspend fun toggle(reminder: Reminder): Boolean {
        if (!graph.flags.reminders) return false
        val added = store.toggle(reminder)
        reschedule()
        // Only on setting, never on removing (REM-FR-05).
        if (added) _prompt.value = if (needsNotificationPermission()) PromptStep.PERMISSION else overlayStep()
        return added
    }

    /** The permission dialog answered, either way: the overlay prompt may follow. */
    fun afterPermission() {
        graph.appScope.launch { _prompt.value = overlayStep() }
    }

    fun promptDone() {
        _prompt.value = PromptStep.NONE
    }

    /** At most once per installation, and only while the app may not open itself over other apps. */
    private suspend fun overlayStep(): PromptStep {
        val prefs = graph.data.preferences
        if (mayOpenOverOtherApps() || prefs.reminderOverlayAsked()) return PromptStep.NONE
        prefs.setReminderOverlayAsked()
        return PromptStep.OVERLAY
    }

    /**
     * REM-FR-11 in the rebuild's order: delete what is stale, fire what is due, then one alarm for
     * the next. Failures are logged, never thrown at the caller (spec 22 §10).
     */
    suspend fun reschedule() {
        if (!graph.flags.reminders) return
        try {
            val now = graph.clock.wallMillis()
            val plan = ReminderSchedule.plan(store.all(), now)
            store.delete(plan.stale.map { it.id })
            if (plan.due.isNotEmpty()) fire(plan.due)
            alarm.set(plan.nextFireAt)
        } catch (e: Exception) {
            graph.diagnostics.error("reminders", "reschedule failed", e)
        }
    }

    /** REM-FR-13: ring, notify, delete the rows, and bring the app forward when it is not on screen. */
    private suspend fun fire(due: List<Reminder>) {
        val now = graph.clock.wallMillis()
        synchronized(queue) {
            queue.dropStale(now)
            queue.ring(due)
            publish()
        }
        due.forEach(::notify)
        store.delete(due.map { it.id })
        if (!graph.inForeground.value) bringForward()
    }

    /** Not now, Back, Watch, or the alert's own timeout (REM-FR-22…24); the next queued one follows. */
    fun dismiss(id: String) {
        synchronized(queue) {
            queue.dismiss(id)
            publish()
        }
        // Answering the alert clears its notification too (spec 22 §10, rebuild).
        NotificationManagerCompat.from(app).cancel(notificationId(id))
    }

    /**
     * Shows the queue's first entry. The alert goes away by itself `max(start + 2 min − now, 20 s)`
     * after it appears (REM-FR-23); timed here on the wall clock, not in the dialog's composition.
     */
    private fun publish() {
        val shown = queue.current
        if (shown == _current.value) return
        _current.value = shown
        linger?.cancel()
        if (shown == null) return
        val ms = ReminderSchedule.lingerMs(shown, graph.clock.wallMillis())
        linger = graph.appScope.launch {
            delay(ms)
            dismiss(shown.id)
        }
    }

    /** Test hook: forgets the ringing queue and any prompt between device tests (ClearStateRule). */
    fun resetForTests() {
        synchronized(queue) {
            while (queue.current != null) queue.dismiss(queue.current!!.id)
            publish()
        }
        _prompt.value = PromptStep.NONE
    }

    /** Android 13+: the notification permission is needed for the panel fallback (REM-FR-05). */
    fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Whether a due reminder may bring the app forward (REM-FR-32). */
    fun mayOpenOverOtherApps(): Boolean = Settings.canDrawOverlays(app)

    /** Opens the TV's "display over other apps" setting for this app, else the general list (REM-FR-32). */
    /** Returns false when the TV has no such screen (spec 70 SET-FR-57, Q-08). */
    fun openOverlaySettings(): Boolean {
        val specific = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${app.packageName}".toUri())
        val general = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        // Tried in turn; a TV without the screen throws, which needs no package query to find out.
        for (intent in listOf(specific, general)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(intent) }.onSuccess { return true }
        }
        graph.diagnostics.info("reminders", "no overlay settings screen on this TV")
        return false
    }

    private fun bringForward() {
        if (!mayOpenOverOtherApps()) {
            graph.diagnostics.info("reminders", "not brought forward: display over other apps not allowed")
            return
        }
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(EXTRA_ALERT, true)
        runCatching { app.startActivity(intent) }.onFailure { graph.diagnostics.info("reminders", "bring forward refused: ${it.javaClass.simpleName}") }
    }

    /** REM-FR-20: the TV's panel only (Android TV never pops it up); skipped when notifications are off. */
    private fun notify(reminder: Reminder) {
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled() || needsNotificationPermission()) {
            graph.diagnostics.info("reminders", "notification not posted: notifications are off")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, texts.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_HIGH)
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val action = if (reminder.channelKey != null) {
            open.putExtra(EXTRA_OPEN_CHANNEL, reminder.channelKey)
            R.string.reminder_notification_watch
        } else {
            open.putExtra(EXTRA_OPEN_EVENT, reminder.eventId)
            R.string.reminder_notification_open_card
        }
        val tap = PendingIntent.getActivity(app, notificationId(reminder.id), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = listOfNotNull(reminder.subtitle?.takeIf { it.isNotBlank() }, texts.getString(action)).joinToString(" · ")
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_tv_epg)
            .setContentTitle(texts.getString(R.string.reminder_notification_title, reminder.title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            manager.notify(notificationId(reminder.id), notification)
        } catch (e: SecurityException) {
            graph.diagnostics.info("reminders", "notification refused: ${e.javaClass.simpleName}")
        }
    }

    private fun notificationId(id: String): Int = id.hashCode()

    companion object {
        const val CHANNEL: String = "reminders"
        const val EXTRA_OPEN_CHANNEL: String = "com.streammate.tv.OPEN_CHANNEL"
        const val EXTRA_OPEN_EVENT: String = "com.streammate.tv.OPEN_EVENT"
        const val EXTRA_ALERT: String = "com.streammate.tv.REMINDER_ALERT"
    }
}
