package com.sohva.tv.ui.design.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import com.sohva.tv.ui.design.theme.Sohva
import kotlin.math.floor

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
     * Start angle of the buffering arc: 0 → 360° every 900 ms, the only infinite animation. In
     * reduced motion it moves in 150 ms steps, so the value (and the drawing) changes six times a
     * turn instead of every frame.
     */
    @Composable
    fun bufferingAngle(): State<Float> {
        val steps = BUFFERING_PERIOD_MS / REDUCED_STEP_MS
        val easing = if (Sohva.reducedMotion) Easing { floor(it * steps) / steps } else LinearEasing
        return rememberInfiniteTransition(label = "buffering").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(BUFFERING_PERIOD_MS, easing = easing), RepeatMode.Restart),
            label = "buffering-angle",
        )
    }
}
