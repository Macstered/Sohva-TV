package com.sohva.tv.ui.design.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The closed list of motion in design/01 §15. Nothing outside this file may start an infinite
 * animation (a lint rule enforces it); in reduced motion everything jumps (plan/07 §5).
 */
object Motion {
    /** Compose's default spring: no bounce, stiffness 1,500; a colour flip settles in ≈ 0.17 s. */
    @Composable
    @ReadOnlyComposable
    fun <T> focus(): AnimationSpec<T> =
        if (Sohva.reducedMotion) snap() else spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    const val BUFFERING_PERIOD_MS: Int = 900
    const val REDUCED_STEP_MS: Int = 150

    /**
     * Start angle of the buffering arc: 0 → 360° every 900 ms. In
     * reduced motion it moves in 150 ms steps, so the value (and the drawing) changes six times a
     * turn instead of every frame.
     */
    @Composable
    fun bufferingAngle(): State<Float> {
        if (Sohva.reducedMotion) {
            val angle = remember { mutableFloatStateOf(0f) }
            // Animation duration is zero in this mode. The indicator still advances six times
            // a turn, without an infinite transition waking the renderer on every frame.
            LaunchedEffect(Unit) {
                val step = 360f * REDUCED_STEP_MS / BUFFERING_PERIOD_MS
                while (isActive) {
                    delay(REDUCED_STEP_MS.toLong())
                    angle.floatValue = (angle.floatValue + step) % 360f
                }
            }
            return angle
        }
        return rememberInfiniteTransition(label = "buffering").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(BUFFERING_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "buffering-angle",
        )
    }

    const val PULSE_PERIOD_MS: Int = 1_400

    /**
     * The addon loading screen's logo or title (spec 50 §5.5): alpha 0.78 ↔ 1.0 over 1,400 ms,
     * FastOutSlowIn, reversing. Read only in a graphics layer, so it redraws one layer and never
     * recomposes. In reduced motion it stays at 1.0.
     */
    @Composable
    fun loadingPulse(): State<Float> {
        if (Sohva.reducedMotion) return androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
        return rememberInfiniteTransition(label = "loading-pulse").animateFloat(
            initialValue = 0.78f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PULSE_PERIOD_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
            label = "loading-pulse-alpha",
        )
    }
}
