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
import com.sohva.tv.core.model.player.DisplayRate

/**
 * "Match the display to the picture" (spec 30 PLAY-FR-103..105): at the current physical
 * resolution, the refresh rate that fits the stream's frame rate within 0.5 %, from the selected
 * video format on every track change; nothing qualifies, nothing changes. The previous
 * preference comes back when the rate changes, the setting is off, or the player leaves (L-11).
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
        val candidates = display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        val chosen = rate?.let { r -> DisplayRate.pick(r, candidates.map { it.refreshRate }) }
        val id = chosen?.let { c -> candidates.first { it.refreshRate == c }.modeId } ?: original
        if (w.attributes.preferredDisplayModeId != id) {
            w.attributes = w.attributes.apply { preferredDisplayModeId = id }
            // Even a request for the mode already shown can make the TV re-negotiate HDMI (see PlayerModel).
            if (id != 0) model.displaySwitching()
        }
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
