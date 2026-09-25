package com.sohva.tv.app.shell

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
import com.sohva.tv.core.data.device.DeviceTierReader
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
import kotlinx.coroutines.withContext

/** What the first app frame needs, read once off the main thread (plan/03 §4.9). */
@Immutable
data class StartState(val snapshot: StartSnapshot, val tier: DeviceTier)

/** Hooks the root calls on the activity. */
interface RootHost {
    /** The app has drawn two frames: the window's launch picture can go (spec 01 SHELL-FR-04). */
    fun onAppDrawn()
}

/**
 * The root (spec 01 §4.1): the launch screen until the start state is read, then the app at the
 * saved size and theme, so the first app frame is already in the saved theme (SHELL-FR-06). The
 * root reads only the stack, the theme and the scale (plan/03 §4.6).
 */
@Composable
fun SohvaRoot(graph: AppGraph, host: RootHost, screenSize: IntSize) {
    var start by remember { mutableStateOf<StartState?>(null) }
    LaunchedEffect(Unit) {
        start = withContext(graph.dispatchers.io) {
            // Timing marks: to the diagnostics log (counts and durations only) and as trace sections.
            val t0 = graph.clock.monotonicNanos()
            val snapshot = trace("Startup:Snapshot") { graph.data.preferences.startSnapshot() }
            val t1 = graph.clock.monotonicNanos()
            val tier = trace("Startup:Tier") { DeviceTier.decide(DeviceTierReader(graph.app).read()) }
            val t2 = graph.clock.monotonicNanos()
            // The saved theme's ground is ready before its first frame.
            GroundCache.prepare(Palettes.of(snapshot.theme), screenSize, graph.dispatchers.ui)
            val t3 = graph.clock.monotonicNanos()
            graph.diagnostics.info(
                "startup",
                "snapshot=${ms(t0, t1)} tier=${ms(t1, t2)} ground=${ms(t2, t3)} ms; tier=${tier.memory} reducedMotion=${tier.reducedMotion}",
            )
            StartState(snapshot, tier)
        }
    }
    CompositionLocalProvider(LocalRenderDispatcher provides graph.dispatchers.ui) {
        val state = start
        if (state == null) LaunchScreen() else App(graph, state, host)
    }
}

private fun ms(from: Long, to: Long): Long = (to - from) / 1_000_000

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
                val stack = rememberBackStack(AppRouteCodec) { startRoutes(start.snapshot.startupScreen, start.snapshot.lastChannel) }
                CompositionLocalProvider(LocalArtwork provides graph.artwork) {
                    NavHost(stack) { route -> AppDestination(route, stack, graph) }
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
