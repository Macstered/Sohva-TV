package com.streammate.tv.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Asks the display to run at a rate the stream divides into evenly, and puts it
 * back on the way out.
 *
 * Restoring matters as much as switching. A television left on 24 Hz because
 * that was the last thing watched makes the rest of the interface scroll badly,
 * and the viewer has no idea why - so the preferred mode is released when
 * playback ends, when the rate changes, and when the setting is turned off.
 */
@Composable
internal fun AutoFrameRateEffect(contentFrameRate: Float, enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(contentFrameRate, enabled) {
        val window = context.findActivity()?.window
        val display: Display? = window?.decorView?.display
        val current = display?.mode

        if (window == null || current == null || !enabled) {
            return@DisposableEffect onDispose { }
        }

        // Only modes at the resolution already in use. Switching the panel's
        // resolution to chase a refresh rate would trade a small stutter for a
        // visibly softer picture.
        val sameSize = display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        val target = AutoFrameRate.pick(sameSize.map { it.refreshRate }, contentFrameRate)
        val mode = target?.let { rate -> sameSize.firstOrNull { it.refreshRate == rate } }

        val previous = window.attributes.preferredDisplayModeId
        if (mode != null && mode.modeId != current.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
        }

        onDispose {
            if (window.attributes.preferredDisplayModeId != previous) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = previous }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
