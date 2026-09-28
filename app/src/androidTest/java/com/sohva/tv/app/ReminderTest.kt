package com.sohva.tv.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.app.reminder.ReminderReceiver
import com.sohva.tv.core.model.reminder.Reminder
import com.sohva.tv.core.model.reminder.ReminderIds
import com.sohva.tv.core.model.reminder.ReminderKind
import com.sohva.tv.feature.home.RailItem
import java.io.FileInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Spec 22 §11 "Instrumentation" for reminders. Times are anchored to now (AGENTS §8). */
@RunWith(AndroidJUnit4::class)
class ReminderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val seed = object : ExternalResource() {
        override fun before() {
            GuideFixture.seed(graph, groups = 1, perGroup = 6)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    /** The panel notification needs the permission; granting it keeps the system dialog out of the way. */
    @Before
    fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun reminder(channel: Int, startIn: Long, title: String = "Harbor Routes"): Reminder {
        val now = System.currentTimeMillis()
        val key = "fixture-0:c$channel"
        return Reminder(ReminderIds.programme(key, "p$channel$startIn"), ReminderKind.PROGRAMME, null, key, title, "Northstar ${channel + 1}", now + startIn, now)
    }

    /** Stores [reminders] and runs the reschedule the alarm would: due ones ring. */
    private fun ring(vararg reminders: Reminder) = runBlocking {
        for (r in reminders) graph.data.reminders.toggle(r)
        graph.reminders.reschedule()
    }

    private fun awaitHome() {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
    }

    @Test
    fun remindMeOnAFutureProgrammeStoresItsIdAndPromptsOnce() {
        awaitHome()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        compose.waitUntil(8_000) { compose.onAllNodes(hasContentDescription("1 Northstar 1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // A block that surely starts later: page 90 minutes ahead, then one block on.
        press(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        SystemClock.sleep(500)
        compose.waitForIdle()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { exists("guide-actions-reminder") }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("guide-actions-reminder")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { runBlocking { graph.data.reminders.all() }.size == 1 }
        val stored = runBlocking { graph.data.reminders.all() }.single()
        assertTrue(stored.id, stored.id.startsWith("programme:fixture-0:c0:"))
        assertEquals("Northstar 1", stored.subtitle)
        // The first reminder ever set explains once how reminders can open the app (REM-FR-05).
        compose.waitUntil(5_000) { exists("reminder-overlay-prompt") }
        awaitFocus("reminder-overlay-open")
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("reminder-overlay-prompt") }
        // A second reminder does not ask again.
        runBlocking { graph.reminders.toggle(reminder(1, 60 * 60_000)) }
        SystemClock.sleep(1_000)
        compose.waitForIdle()
        assertFalse(exists("reminder-overlay-prompt"))
    }

    @Test
    fun aDueReminderAlertsAndWatchPlaysTheChannel() {
        awaitHome()
        ring(reminder(2, 30_000))
        compose.waitUntil(5_000) { exists("reminder-alert") }
        assertTrue(compose.onAllNodesWithTextExists("Harbor Routes starts in a minute"))
        awaitFocus("reminder-alert-watch")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-player") && !exists("reminder-alert") }
        // Not a recent channel: playback a reminder started (CHAN-FR-61).
        assertTrue(runBlocking { graph.data.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM recent_channel").use { it.moveToFirst(); it.getInt(0) } } == 0)
        // Back returns to where the viewer was: the first press hides the live box, the next leaves.
        compose.waitUntil(10_000) { !exists("player-live-box") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
    }

    /**
     * REM-08: with "display over other apps" allowed, a reminder that falls due while another app
     * is in front brings Sohva TV forward with its alert. The emulator's own home screen stands in for
     * the other app; the permission is granted through the test's shell and taken back after.
     */
    @Test
    fun aDueReminderBringsTheAppForwardWhenAllowed() {
        val pkg = instrumentation.targetContext.packageName
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()
        shell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        try {
            awaitHome()
            // The TV's home screen in front, as another app would be (a HOME key from the test does not leave the app).
            shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME")
            compose.waitUntil(10_000) { !graph.inForeground.value }
            ring(reminder(2, 30_000))
            compose.waitUntil(10_000) { graph.inForeground.value }
            compose.waitUntil(5_000) { exists("reminder-alert") }
        } finally {
            shell("appops set $pkg SYSTEM_ALERT_WINDOW default")
        }
    }

    @Test
    fun queuedAlertsFollowOneAnotherAndBackDismisses() {
        awaitHome()
        ring(reminder(1, -60_000, "Glass Kitchen"), reminder(2, 20_000, "Silent Weather"))
        compose.waitUntil(5_000) { exists("reminder-alert") }
        // Oldest start first; it has already started.
        assertTrue(compose.onAllNodesWithTextExists("Glass Kitchen starts now"))
        awaitFocus("reminder-alert-watch")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("reminder-alert-later")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Silent Weather starts in a minute") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("reminder-alert") }
        assertTrue(exists(RailItem.LIVE_TV.tag))
        // Fired reminders are removed (REM-FR-13).
        assertTrue(runBlocking { graph.data.reminders.all() }.isEmpty())
    }

    @Test
    fun theAlertGoesAwayByItselfAfterItsLinger() {
        awaitHome()
        // Started 110 s ago: it may stay max(10 s, 20 s) = 20 s.
        ring(reminder(3, -110_000))
        compose.waitUntil(5_000) { exists("reminder-alert") }
        SystemClock.sleep(15_000)
        compose.waitForIdle()
        assertTrue(exists("reminder-alert"))
        compose.waitUntil(15_000) { !exists("reminder-alert") }
    }

    /** The alarm probe: a reminder whose fire time is five seconds away rings through the real alarm and receiver. */
    @Test
    fun theRealAlarmRingsIt() {
        awaitHome()
        runBlocking { graph.reminders.toggle(reminder(4, 65_000)) }
        compose.waitUntil(40_000) { exists("reminder-alert") }
        assertTrue(runBlocking { graph.data.reminders.all() }.isEmpty())
    }

    /** A reboot or an update restores the alarm (REM-FR-14): one alarm clock for the app afterwards. */
    @Test
    fun theReceiverRestoresTheAlarm() {
        awaitHome()
        runBlocking { graph.data.reminders.toggle(reminder(5, 60 * 60_000)) }
        ReminderReceiver().onReceive(instrumentation.targetContext, Intent(Intent.ACTION_BOOT_COMPLETED))
        var alarms = ""
        compose.waitUntil(10_000) {
            // The alarm's tag names the fire action; the package is on a line of its own.
            alarms = shell("dumpsys alarm").lines().filter { "com.streammate.tv.REMINDER_FIRE" in it }.joinToString("\n")
            alarms.isNotEmpty()
        }
        assertTrue(alarms, alarms.isNotEmpty())
        assertEquals(1, runBlocking { graph.reminders.ids.first() }.size)
    }

    private fun shell(command: String): String {
        val fd = instrumentation.uiAutomation.executeShellCommand(command)
        return FileInputStream(fd.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }.also { fd.close() }
    }
}
