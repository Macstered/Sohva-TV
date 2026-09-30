package com.sohva.tv.ui.design

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.ui.design.components.BufferingIndicator
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.ground.LocalRenderDispatcher
import com.sohva.tv.ui.design.theme.SohvaTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionAtZeroScaleTest {
    // The system animator scale set to 0 (a common TV setting); OwnTV shipped a crash here.
    @get:Rule
    val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float = 0f
    })

    @Test
    fun bufferingArcSurvivesZeroDurationScale() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalRenderDispatcher provides Dispatchers.Unconfined) {
                SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = false) { BufferingIndicator() }
            }
        }
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText("Buffering…").assertExists()
    }

    @Test
    fun reducedBufferingStillStepsWithZeroAnimationDuration() {
        compose.mainClock.autoAdvance = false
        lateinit var angle: androidx.compose.runtime.State<Float>
        compose.setContent {
            SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = true) {
                angle = com.sohva.tv.ui.design.motion.Motion.bufferingAngle()
            }
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f, angle.value, 0f)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(0f, angle.value, 0f)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(60f, angle.value, 0f)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(120f, angle.value, 0f)
    }
}

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class TvSurfaceKeysTest {
    @get:Rule
    val compose = createComposeRule()

    private var clicks = 0
    private var longClicks = 0
    private val focus = FocusRequester()

    private fun show() {
        compose.setContent {
            SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = true) {
                TvSurface(
                    onClick = { clicks++ },
                    modifier = Modifier.size(100.dp).focusRequester(focus).testTag("surface"),
                    onLongClick = { longClicks++ },
                ) { }
            }
        }
        compose.runOnIdle { focus.requestFocus() }
    }

    @Test
    fun okClicksOnce() {
        show()
        compose.onNodeWithTag("surface").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1 to 0, clicks to longClicks) }
    }

    @Test
    fun heldOkFiresTheLongPressOnceAndNoClick() {
        show()
        compose.onNodeWithTag("surface").performKeyInput {
            keyDown(Key.DirectionCenter)
            // Held past the initial repeat delay: repeats arrive as key-downs with a repeat count.
            advanceEventTime(700)
            keyUp(Key.DirectionCenter)
        }
        compose.runOnIdle { assertEquals(0 to 1, clicks to longClicks) }
    }
}
