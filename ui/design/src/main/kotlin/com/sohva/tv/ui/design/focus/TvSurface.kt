package com.sohva.tv.ui.design.focus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.motion.Motion
import com.sohva.tv.ui.design.theme.LocalContentColor
import com.sohva.tv.ui.design.theme.Sohva

/** Resolved colours of a focusable container (design/02 §5), and whether they are the focused ones. */
@Immutable
data class TvSurfaceColors(
    val background: Color,
    val content: Color,
    val secondaryContent: Color,
    val focused: Boolean = false,
)

/**
 * What the control is, as opposed to what it looks like. [showFocused] draws the focused look
 * without holding focus; only the component gallery and screenshot tests use it.
 */
@Immutable
data class SurfaceState(
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val showFocused: Boolean = false,
)

/** How the container is drawn. Colours left null take the defaults of design/02 §5. */
@Immutable
data class SurfaceStyle(
    val corner: Dp = 8.dp,
    val resting: Color? = null,
    val restingContent: Color? = null,
    /** Artwork frames: keep the resting fill and draw a 3 dp ring instead of the fill flip. */
    val focusRing: Boolean = false,
    val focusScale: Float = 1.04f,
    /** The fill flips with the default spring; fields flip at once (design/01 §12). */
    val animateFill: Boolean = true,
    val padding: PaddingValues = PaddingValues(0.dp),
    val contentAlignment: Alignment = Alignment.CenterStart,
)

/** The focus rule as colours; first match wins (design/02 §5). */
@Composable
fun tvSurfaceColors(focused: Boolean, state: SurfaceState, style: SurfaceStyle): TvSurfaceColors {
    val p = Sohva.palette
    val flip = focused && !style.focusRing
    return when {
        !state.enabled -> TvSurfaceColors(style.resting ?: Color.Transparent, p.textDisabled, p.textDisabled)
        flip && state.danger -> TvSurfaceColors(p.danger, p.textPrimary, p.textPrimary.copy(alpha = 0.72f), focused = true)
        flip -> TvSurfaceColors(p.textPrimary, p.background, p.background.copy(alpha = 0.62f), focused = true)
        state.selected -> TvSurfaceColors(p.surfaceFocused, p.textPrimary, p.textMuted)
        else -> TvSurfaceColors(style.resting ?: Color.Transparent, style.restingContent ?: p.textMuted, p.textDim, focused)
    }
}

/**
 * The one focusable primitive (design/02 §22): everything the viewer can focus goes through it,
 * so the default click indication never appears and the focus look stays uniform.
 *
 * Cheap by construction (design/02 §6): the fill is a round rect drawn behind the content from a
 * colour read in the draw phase, no clip, no shadow; the scale lives in a layer lambda *inside*
 * the focus target so the reported bounds never change and lazy rows do not shift. The content
 * colour flips at once (one recomposition per focus change).
 */
@Composable
fun TvSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: SurfaceState = SurfaceState(),
    style: SurfaceStyle = SurfaceStyle(),
    onLongClick: (() -> Unit)? = null,
    content: @Composable (TvSurfaceColors) -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val gesture = remember { LongPressGesture() }
    val focused = hasFocus || state.showFocused
    val colors = tvSurfaceColors(focused, state, style)
    val fill by animateColorAsState(
        targetValue = colors.background,
        animationSpec = if (style.animateFill) Motion.focus() else snap(),
        label = "surface-fill",
    )
    val scale by animateFloatAsState(if (focused) style.focusScale else 1f, Motion.focus(), label = "surface-scale")
    val ring = focused && style.focusRing
    val ringColor = Sohva.palette.textPrimary
    val ringWidth = Sohva.shapes.focusRing

    Box(
        modifier = modifier
            .onFocusChanged { hasFocus = it.isFocused }
            .semantics(mergeDescendants = true) { selected = state.selected }
            .then(if (onLongClick != null) Modifier.longPress(gesture, onLongClick) else Modifier)
            .clickable(interactionSource = null, indication = null, enabled = state.enabled, role = Role.Button, onClick = onClick)
            .then(if (style.focusScale != 1f) Modifier.graphicsLayer { scaleX = scale; scaleY = scale } else Modifier)
            .drawBehind {
                val radius = CornerRadius(style.corner.toPx())
                if (fill.alpha > 0f) drawRoundRect(fill, cornerRadius = radius)
                if (ring) {
                    val w = ringWidth.toPx()
                    drawRoundRect(
                        color = ringColor,
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius((style.corner.toPx() - w / 2).coerceAtLeast(0f)),
                        style = Stroke(w),
                    )
                }
            }
            .padding(style.padding),
        contentAlignment = style.contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) { content(colors) }
    }
}
