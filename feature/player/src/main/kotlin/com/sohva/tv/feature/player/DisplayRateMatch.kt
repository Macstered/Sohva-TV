package com.sohva.tv.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.player.DisplayModes

/**
 * "Match the display to the picture" (spec 30 PLAY-FR-103..105): at the current physical
 * resolution, the refresh rate that fits the stream's frame rate within 0.5 %, from the selected
 * video format on every track change; nothing qualifies, nothing changes. The previous
 * preference comes back when a known rate fits no mode, the setting is off, or the player leaves
 * (L-11); between channels, while the rate is unknown, the display is left alone.
 */
@Composable
internal fun DisplayRateMatch(model: PlayerModel) {
    if (!model.settings.matchFrameRate) return
    val rate by model.frameRate.collectAsStateWithLifecycle()
    val view = LocalView.current
    val window = remember(view) { view.context.activity()?.window }
    val original = remember(window) { window?.attributes?.preferredDisplayModeId ?: 0 }
    LaunchedEffect(rate) {
        val w = window ?: return@LaunchedEffect
        val display = view.display ?: return@LaunchedEffect
        val current = display.mode
        val modes = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .map { DisplayModes.Mode(it.modeId, it.refreshRate) }
        val id = DisplayModes.target(rate, modes, original, w.attributes.preferredDisplayModeId) ?: return@LaunchedEffect
        // Any request, a return to the original too, can make the TV re-negotiate HDMI and the system
        // pause playback as "audio becoming noisy"; marked first, the player undoes that pause (see PlayerModel).
        model.displaySwitching()
        w.attributes = w.attributes.apply { preferredDisplayModeId = id }
    }
    DisposableEffect(window) {
        onDispose { window?.let { it.attributes = it.attributes.apply { preferredDisplayModeId = original } } }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
