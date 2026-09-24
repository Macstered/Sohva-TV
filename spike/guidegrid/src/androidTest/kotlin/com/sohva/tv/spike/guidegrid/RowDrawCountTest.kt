package com.sohva.tv.spike.guidegrid

import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.ui.design.ground.LocalRenderDispatcher
import com.sohva.tv.ui.design.theme.SohvaTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Diagnosis for the spike: which rows re-record their drawing on a press between rows. */
@RunWith(AndroidJUnit4::class)
class RowDrawCountTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun drawsPerPress() {
        compose.setContent {
            @Suppress("InjectDispatcher")
            CompositionLocalProvider(LocalRenderDispatcher provides Dispatchers.Default) {
                SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = false) { GuideSpikeScreen(GridVariant.CANVAS) }
            }
        }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(3) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); compose.waitForIdle() }
        SpikeCounters.drain()
        repeat(10) { press ->
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.waitForIdle()
            Log.i("SpikeDraws", "press $press: ${SpikeCounters.drain()}")
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.waitForIdle()
        SpikeCounters.drain()
        repeat(5) { press ->
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.waitForIdle()
            Log.i("SpikeDraws", "right $press: ${SpikeCounters.drain()}")
        }
    }
}
