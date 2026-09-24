package com.sohva.tv.ui.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii (design/01 §10). Components draw rounded fills with `drawRoundRect` instead of
 * clipping (§16.1 rule 7), so the tokens are radii, not `Shape`s.
 */
@Immutable
data class SohvaShapes(
    /** Controls and cells. */
    val small: Dp = 8.dp,
    /** Cards. */
    val medium: Dp = 12.dp,
    /** Large panels. */
    val large: Dp = 18.dp,
    /** Focus ring, drawn inside the bounds so nothing re-measures. */
    val focusRing: Dp = 3.dp,
    val hairline: Dp = 1.dp,
)

/** Spacing steps and the single TV safe area used by every screen (design/01 §9). */
@Immutable
data class SohvaSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val safeHorizontal: Dp = 40.dp,
    val safeVertical: Dp = 24.dp,
)
