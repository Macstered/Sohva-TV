package com.sohva.tv.ui.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale

// Static locals: a theme change recomposes the tree once; nothing reads them per frame (design/01 §2).
private val LocalPalette = staticCompositionLocalOf { Palettes.original }
private val LocalTypography = staticCompositionLocalOf { SohvaTypography.Default }
private val LocalShapes = staticCompositionLocalOf { SohvaShapes() }
private val LocalSpacing = staticCompositionLocalOf { SohvaSpacing() }
private val LocalReducedMotion = staticCompositionLocalOf { false }

/** Colour for text and icons inside a container; focused containers flip it. */
val LocalContentColor = compositionLocalOf { Palettes.original.textPrimary }

/** Style every [com.sohva.tv.ui.design.text.Text] starts from. */
val LocalTextStyle = compositionLocalOf { SohvaTypography.Inherited }

/**
 * Provides the tokens. Applied once at the root; themes switch with a plain `when`, no transition.
 * [reducedMotion] comes from the device tier (plan/07 §5) and the viewer's switch (decision A8).
 */
@Composable
fun SohvaTheme(theme: ColorThemeId, reducedMotion: Boolean, content: @Composable () -> Unit) {
    val palette = Palettes.of(theme)
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalReducedMotion provides reducedMotion,
        LocalContentColor provides palette.textPrimary,
        LocalTextStyle provides SohvaTypography.Inherited,
        content = content,
    )
}

/** Token access: `Sohva.palette.focus`, `Sohva.typography.label`, `Sohva.spacing.md`. */
object Sohva {
    val palette: SohvaPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
    val typography: SohvaTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
    val shapes: SohvaShapes
        @Composable @ReadOnlyComposable get() = LocalShapes.current
    val spacing: SohvaSpacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    /** True in the low tier, at zero animator scale, or when the viewer asks for it. */
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/**
 * Interface size scales density, not font scale: layouts and text shrink together and the
 * viewer's system font scale still applies on top (design/01 §14). The launch screen stays outside.
 */
@Composable
fun InterfaceScaled(scale: InterfaceScale, content: @Composable () -> Unit) {
    val device = LocalDensity.current
    val scaled = Density(device.density * scale.factor, device.fontScale)
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}
