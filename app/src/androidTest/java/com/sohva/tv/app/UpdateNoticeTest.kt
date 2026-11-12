package com.sohva.tv.app

import android.os.Debug
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.app.gallery.GalleryActivity
import com.sohva.tv.app.update.AppUpdateMemory
import com.sohva.tv.app.update.UpdateNoticeLayer
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.update.OfferedUpdate
import com.sohva.tv.core.model.update.ReleaseAsset
import com.sohva.tv.core.model.update.UpdatePhase
import com.sohva.tv.core.model.update.UpdateState
import com.sohva.tv.core.sync.update.UpdateNotices
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.ground.LocalRenderDispatcher
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import com.sohva.tv.ui.design.theme.SohvaTheme
import com.sohva.tv.ui.design.theme.UpdateNoticeDimensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Real remote keys, deferred visibility, persisted marker and paired 1 GB drawing CPU. */
@RunWith(AndroidJUnit4::class)
class UpdateNoticeTest {
    private val compose = createAndroidComposeRule<GalleryActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(compose)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eligible = mutableStateOf(false)
    private val nonce = mutableStateOf(0)
    private var backCount = 0
    private val updates = MutableStateFlow(UpdateState(UpdatePhase.IDLE))
    private val log = object : DiagnosticsLog {
        override fun info(event: String, message: String) = Unit
        override fun error(event: String, message: String?, error: Throwable?) = Unit
        override fun snapshot(): List<String> = emptyList()
    }
    private val memory = AppUpdateMemory(instrumentation.targetContext, Dispatchers.IO)
    private val notices = UpdateNotices(true, 100, memory, scope, log)

    @After fun stop() { runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() } }

    private fun offer(build: Int = 101) {
        updates.value = UpdateState(UpdatePhase.AVAILABLE,
            OfferedUpdate("0.2.0-beta.$build", build, "", ReleaseAsset("sohva.apk", "https://provider.example/app.apk", 1), null, null))
    }
    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)
    private fun render() {
        notices.start(updates)
        compose.activityRule.scenario.onActivity { activity ->
            val graph = (activity.application as SohvaApplication).graph
            activity.setContent {
                CompositionLocalProvider(LocalRenderDispatcher provides graph.dispatchers.ui) {
                    SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = true) {
                        BackHandler { backCount++ }
                        val focus = androidx.compose.runtime.remember { FocusRequester() }
                        ScreenBackground(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize()) {
                                Column(Modifier.padding(Sohva.spacing.safeHorizontal, Sohva.spacing.safeVertical)) {
                                    Text("Home fixture", style = Sohva.typography.headline)
                                    Text("Cycle ${nonce.value}", Modifier.testTag("cycle-${nonce.value}"))
                                    TvActionButton("Watch", {}, Modifier.focusRequester(focus).testTag("fixture-watch"))
                                    TvActionButton("Guide", {}, Modifier.testTag("fixture-guide"))
                                }
                                UpdateNoticeLayer(notices, eligible.value,
                                    Modifier.align(Alignment.TopEnd).padding(top = UpdateNoticeDimensions.top, end = Sohva.spacing.xl))
                            }
                        }
                        LaunchedEffect(Unit) { focus.requestFocusWhenAttached() }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("fixture-watch").assertIsFocused()
    }
    private fun show() {
        compose.runOnUiThread { eligible.value = true }
        compose.waitUntil(5_000) { exists("update-notice") && notices.pending.value == null }
    }

    @Test
    fun aDeferredNoticeDoesNotConsumeTheMarkerOrTakeRemoteFocus() {
        render()
        offer()
        compose.waitUntil(5_000) { notices.pending.value != null }
        assertTrue(!exists("update-notice"))
        assertEquals(0, runBlocking { memory.lastNotifiedBuild() })
        show()
        compose.onNodeWithTag("fixture-watch").assertIsFocused()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.cacheDir, "update-notice-home.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitForIdle()
        compose.onNodeWithTag("fixture-guide").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        assertEquals("The passive notice must not consume Back", 1, backCount)
        assertTrue(exists("update-notice"))
        // Background, playback or a modal blocks visibility; a return cannot repeat this build.
        compose.runOnUiThread { eligible.value = false }
        compose.waitUntil(5_000) { !exists("update-notice") }
        compose.runOnUiThread { eligible.value = true }
        compose.waitForIdle()
        assertTrue(!exists("update-notice"))
        compose.onNodeWithTag("fixture-guide").assertIsFocused()
    }

    @Test
    fun aShownMarkerSurvivesANewMemoryInstanceAndOnlyANewerBuildIsOffered() {
        render()
        offer()
        show()
        compose.waitUntil(5_000) { runBlocking { memory.lastNotifiedBuild() } == 101 }
        val nextMemory = AppUpdateMemory(instrumentation.targetContext, Dispatchers.IO)
        val restarted = UpdateNotices(true, 100, nextMemory, scope, log)
        restarted.start(updates)
        runBlocking { assertEquals(101, nextMemory.lastNotifiedBuild()) }
        offer(102)
        compose.waitUntil(5_000) { restarted.pending.value?.build == 102 }
        assertEquals(102, restarted.pending.value?.build)
        restarted.shown(102)
        compose.waitUntil(5_000) { runBlocking { nextMemory.lastNotifiedBuild() } == 102 }
        assertNull(restarted.pending.value)
    }

    @Test
    fun theNoticeHidesAfterItsDisplayWindow() {
        render()
        offer()
        show()
        compose.mainClock.advanceTimeBy(15_100)
        compose.waitUntil(5_000) { !exists("update-notice") }
        compose.onNodeWithTag("fixture-watch").assertIsFocused()
    }

    @Test
    fun compareNoticeDrawingCpuInPairedCycles() {
        render()
        repeat(6) { sample ->
            for (banner in listOf(false, true)) {
                compose.runOnUiThread { eligible.value = false }
                compose.waitUntil(5_000) { !exists("update-notice") }
                updates.value = UpdateState(UpdatePhase.UP_TO_DATE)
                compose.waitUntil(5_000) { notices.pending.value == null }
                var from = 0L
                compose.runOnUiThread { from = Debug.threadCpuTimeNanos() }
                val cycle = sample * 2 + if (banner) 2 else 1
                compose.runOnUiThread { nonce.value = cycle; eligible.value = true }
                if (banner) offer(1_000 + sample)
                compose.waitUntil(5_000) { exists("cycle-$cycle") && (!banner || (exists("update-notice") && notices.pending.value == null)) }
                compose.waitForIdle()
                var cpu = 0L
                compose.runOnUiThread { cpu = (Debug.threadCpuTimeNanos() - from) / 1_000_000 }
                instrumentation.sendStatus(0, android.os.Bundle().apply {
                    putString("stream", "Update notice sample=$sample banner=$banner mainCpuMs=$cpu\n")
                })
            }
        }
    }
}
