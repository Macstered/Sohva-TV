package com.sohva.tv.spike.guidegrid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.ui.design.ground.LocalRenderDispatcher
import com.sohva.tv.ui.design.theme.SohvaTheme
import kotlinx.coroutines.Dispatchers

/**
 * Hosts the spike in release-like builds, so the measurement runs minified code without a
 * debuggable runtime. Started by :benchmark with the extra `variant` = CANVAS or CELLS.
 */
class SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val variant = GridVariant.valueOf(intent.getStringExtra("variant") ?: GridVariant.CANVAS.name)
        setContent {
            // Throw-away code outside the app graph; the real guide gets AppDispatchers.ui.
            @Suppress("SohvaDispatchers")
            CompositionLocalProvider(LocalRenderDispatcher provides Dispatchers.Default) {
                // Standard motion: the spike measures the full focus animation, the costlier case.
                SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = false) { GuideSpikeScreen(variant) }
            }
        }
    }
}
