package com.sohva.tv.app.shell

import com.sohva.tv.ui.design.window.LocalPictureInPicture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntSize
import androidx.tracing.trace
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.navigation.AppRouteCodec
import com.sohva.tv.app.navigation.startRoutes
import com.sohva.tv.app.profile.StartQuestion
import com.sohva.tv.app.reminder.ReminderLayer
import com.sohva.tv.core.model.device.DeviceTier
import com.sohva.tv.core.model.settings.StartSnapshot
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.ground.GroundCache
import com.sohva.tv.ui.design.ground.LocalRenderDispatcher
import com.sohva.tv.ui.design.navigation.NavHost
import com.sohva.tv.ui.design.navigation.rememberBackStack
import com.sohva.tv.ui.design.theme.InterfaceScaled
import com.sohva.tv.ui.design.theme.Palettes
import com.sohva.tv.ui.design.theme.SohvaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the first app frame needs, read once off the main thread (plan/03 §4.9). */
@Immutable
/** [upgraded]: this start brought beta 23's data across, so the one-time notice shows (decision "Upgrade notice"). */
data class StartState(val snapshot: StartSnapshot, val tier: DeviceTier, val upgraded: Boolean = false)

/** Hooks the root calls on the activity. */
interface RootHost {
    /** The app has drawn two frames: the window's launch picture can go (spec 01 SHELL-FR-04). */
    fun onAppDrawn()

    /** Back on the start-time picker: the app closes (decision "Start-time picker"). */
    fun leave()
}

/**
 * The root (spec 01 §4.1): the launch screen until the start state is read, then the app at the
 * saved size and theme, so the first app frame is already in the saved theme (SHELL-FR-06). The
 * root reads only the stack, the theme and the scale (plan/03 §4.6).
 */
@Composable
fun SohvaRoot(graph: AppGraph, host: RootHost, screenSize: IntSize) {
    var start by remember { mutableStateOf<StartState?>(null) }
    var updating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var upgraded = false
        // An upgrade over beta 23 imports its viewer data before the first screen is chosen (plan/04
        // §17): file checks only on every other start; "Updating…" only when it takes over a second.
        withContext(graph.dispatchers.io) {
            val upgrade = com.sohva.tv.app.migration.Beta23Upgrade(graph)
            if (upgrade.pending()) {
                val note = launch { delay(UPDATING_AFTER_MS); updating = true }
                upgraded = upgrade.run() is com.sohva.tv.app.migration.Beta23Upgrade.Result.Done
                note.cancel()
            }
        }
        graph.upgradeSettled.complete(Unit)
        start = withContext(graph.dispatchers.io) {
            // Timing marks: to the diagnostics log (counts and durations only) and as trace sections.
            val t0 = graph.clock.monotonicNanos()
            val snapshot = trace("Startup:Snapshot") { graph.data.preferences.startSnapshot() }
            // Every per-profile read that follows uses the active profile (spec 04 PROF-FR-07).
            graph.data.profiles.seed(snapshot.household)
            val t1 = graph.clock.monotonicNanos()
            val tier = trace("Startup:Tier") { graph.deviceTier.load() }
            val t2 = graph.clock.monotonicNanos()
            // The saved theme's ground is ready before its first frame.
            GroundCache.prepare(Palettes.of(snapshot.theme), screenSize, graph.dispatchers.ui)
            val t3 = graph.clock.monotonicNanos()
            graph.diagnostics.info(
                "startup",
                "snapshot=${ms(t0, t1)} tier=${ms(t1, t2)} ground=${ms(t2, t3)} ms; tier=${tier.memory} reducedMotion=${tier.reducedMotion}",
            )
            StartState(snapshot, tier, upgraded)
        }
    }
    val corner by graph.inPictureInPicture.collectAsState()
    CompositionLocalProvider(LocalRenderDispatcher provides graph.dispatchers.ui, LocalPictureInPicture provides corner) {
        val state = start
        if (state == null) LaunchScreen(updating) else App(graph, state, host)
    }
}

private fun ms(from: Long, to: Long): Long = (to - from) / 1_000_000

private const val UPDATING_AFTER_MS = 1_000L

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun App(graph: AppGraph, start: StartState, host: RootHost) {
    val prefs = graph.data.preferences
    val theme by prefs.theme.collectAsState(start.snapshot.theme)
    val scale by prefs.scale.collectAsState(start.snapshot.scale)
    SohvaTheme(theme, start.tier.reducedMotion) {
        InterfaceScaled(scale) {
            // Test tags double as resource ids, so UiAutomator journeys (profiles, benchmarks) find them.
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                // Asked once per process and kept across an activity recreation (spec 04 PROF-FR-10).
                var answered by rememberSaveable { mutableStateOf(graph.startAnswered || !start.snapshot.household.askNeeded) }
                var lastChannel by rememberSaveable { mutableStateOf(start.snapshot.lastChannel) }
                if (!answered) {
                    StartQuestion(graph, onAnswered = { last ->
                        lastChannel = last
                        answered = true
                    }, onLeave = host::leave)
                } else {
                    val stack = rememberBackStack(AppRouteCodec) { startRoutes(start.snapshot.startupScreen, lastChannel) }
                    CompositionLocalProvider(LocalArtwork provides graph.artwork, com.sohva.tv.feature.discover.ui.components.LocalTitleMarks provides graph.titleMarks) {
                        NavHost(stack) { route -> AppDestination(route, stack, graph) }
                    }
                    // Due reminders and notification taps, over whatever screen is up (spec 22 REM-FR-21).
                    ReminderLayer(graph, stack)
                    // Once, after the import from beta 23; kept closed across an activity recreation.
                    var notice by rememberSaveable { mutableStateOf(start.upgraded) }
                    if (notice) com.sohva.tv.app.migration.UpgradeNotice { notice = false }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        host.onAppDrawn()
    }
}
