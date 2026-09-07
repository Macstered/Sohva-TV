package com.streammate.tv.app

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.ReminderEntity
import com.streammate.tv.core.diagnostics.DiagnosticsLog
import com.streammate.tv.core.reminders.ReminderSchedule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real alarm path on a real device: a reminder set a little ahead must
 * ring through AlarmManager and the receiver, and the receiver must bring
 * the app forward when it is not up. Opt in with the instrumentation
 * argument `reminderAlarmProbe=true`: it waits for a real alarm, and it puts
 * the app on the screen.
 */
@RunWith(AndroidJUnit4::class)
class ReminderAlarmProbeTest {

    @Test
    fun aRealAlarmRingsAndBringsTheAppForward() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("reminderAlarmProbe") == "true")
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamMateApplication
        val container = application.container
        val start = System.currentTimeMillis() + ReminderSchedule.LEAD_MILLIS + 15_000L
        val reminder = ReminderEntity(
            id = "programme:probe:1",
            kind = ReminderEntity.KIND_PROGRAMME,
            eventId = null,
            channelId = "probe",
            title = "Probe",
            subtitle = null,
            startEpochMillis = start,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        container.reminderRepository.set(reminder)

        val deadline = SystemClock.elapsedRealtime() + 90_000L
        while (container.reminderAlerts.ringing.value.none { it.id == reminder.id } && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(500)
        }
        val rang = container.reminderAlerts.ringing.value.any { it.id == reminder.id }
        SystemClock.sleep(4_000)
        val forward = StreamMateForegroundState.isForeground
        DiagnosticsLog.i("probe", "alarm rang=$rang, app in foreground afterwards=$forward")
        container.reminderAlerts.dismiss(reminder.id)
        assertTrue("the alarm never rang", rang)
    }

    /**
     * The platform's own path for alarm clocks: an alarm whose operation is
     * the activity, sent by the system. Leaves like the cold probe; read
     * ActivityTaskManager in logcat for START / Displayed / blocked.
     */
    @Test
    fun setsAnAlarmClockThatOpensTheActivity() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("reminderActivityProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alarms = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("probe", "activity-alarm")
        val operation = android.app.PendingIntent.getActivity(
            context, 77, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + 30_000L
        alarms.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(at, operation), operation)
        DiagnosticsLog.i("probe", "activity alarm set for $at")
    }

    /**
     * Sets a reminder and leaves: the process ends with the test, so the
     * alarm has to start it cold. Read the result from logcat (tag SohvaTV).
     */
    @Test
    fun setsAReminderForAColdStart() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("reminderColdProbe") == "true")
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamMateApplication
        application.container.reminderRepository.set(
            ReminderEntity(
                id = "programme:probe:cold",
                kind = ReminderEntity.KIND_PROGRAMME,
                eventId = null,
                channelId = "probe",
                title = "Cold probe",
                subtitle = null,
                startEpochMillis = System.currentTimeMillis() + ReminderSchedule.LEAD_MILLIS + 45_000L,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }
}
