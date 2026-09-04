package com.streammate.tv.app

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Semantic StreamMate colours. Screens should consume these roles instead of
 * embedding palette values so another TV-friendly palette can be selected at
 * the app theme boundary later.
 */
@Immutable
data class StreamMatePalette(
    /** The ground everything sits on, and the ink that shows on a focused fill. */
    val background: Color,
    /** Secondary ground for rails and the top of the ambient wash. */
    val backgroundTop: Color,
    /** Deepest ground, used for scrims that must bury artwork. */
    val backgroundBottom: Color,
    /** Opaque panel for overlays that sit on video or artwork and must obscure it. */
    val panel: Color,
    /** Surface ladder, step 2. The default resting fill for a control. */
    val surface: Color,
    /** Surface ladder, step 3. An elevated block inside an already-raised one. */
    val surfaceRaised: Color,
    /** Surface ladder, step 1. The quietest fill that is still a surface. */
    val surfaceSubtle: Color,
    /** Selected-but-unfocused tint. Focus itself is the white fill, not this. */
    val surfaceFocused: Color,
    /** Hairline between rows and columns. */
    val divider: Color,
    /** Reserved for the rare border that carries meaning; never a resting outline. */
    val outline: Color,
    /** Focus and primary accent. */
    val focus: Color,
    /** Ambient counter-wash on the background only. */
    val secondaryGlow: Color,
    /** SportMate orange: the sport role. */
    val accent: Color,
    /** Live and destructive. */
    val danger: Color,
    /** Ratings and scores. */
    val rating: Color,
    val textPrimary: Color,
    val textMuted: Color,
    /** Tertiary text: metadata that should recede but stay readable. */
    val textDim: Color,
    /** Content of a control that cannot be focused. */
    val textDisabled: Color,
)

val StreamMateDefaultPalette = StreamMatePalette(
    background = Color(0xFF05070D),
    backgroundTop = Color(0xFF080C15),
    backgroundBottom = Color(0xFF04060A),
    panel = Color(0xFF0A0F1A),
    // One ladder of translucent white replaces the screen-by-screen blues. Each
    // step reads the same over the ground, over artwork and over video.
    surfaceSubtle = Color.White.copy(alpha = 0.035f),
    surface = Color.White.copy(alpha = 0.06f),
    surfaceRaised = Color.White.copy(alpha = 0.10f),
    surfaceFocused = Color.White.copy(alpha = 0.14f),
    divider = Color.White.copy(alpha = 0.07f),
    outline = Color.White.copy(alpha = 0.07f),
    focus = Color(0xFF2DE2E6),
    secondaryGlow = Color(0xFF2276D2),
    accent = Color(0xFFFF8A4C),
    danger = Color(0xFFFF3B5C),
    rating = Color(0xFFFFC857),
    textPrimary = Color(0xFFF2F5F9),
    textMuted = Color(0xFF93A1B5),
    textDim = Color(0xFF5B6981),
    textDisabled = Color(0xFF5B6981),
)

// Legacy aliases retained for source compatibility. Theme-aware UI should read StreamMateThemeTokens.
val StreamMateBackground = StreamMateDefaultPalette.background
val StreamMateSurface = StreamMateDefaultPalette.surface
val StreamMateSurfaceRaised = StreamMateDefaultPalette.surfaceRaised
val StreamMateCyan = StreamMateDefaultPalette.focus
val StreamMateOrange = StreamMateDefaultPalette.accent
val StreamMateRed = StreamMateDefaultPalette.danger
val StreamMateMuted = StreamMateDefaultPalette.textMuted

/**
 * Seven-step type scale for a 10-foot interface. The floor is 12sp; anything
 * smaller is unreadable from a sofa and must not be reintroduced at call sites.
 */
@Immutable
data class StreamMateTypography(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val bodyLarge: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
)

val StreamMateDefaultTypography = StreamMateTypography(
    display = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    title = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headline = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal),
    body = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    label = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    overline = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
)

/** Three corner radii: controls and cells, cards, large panels. */
@Immutable
data class StreamMateShapes(
    val small: CornerBasedShape,
    val medium: CornerBasedShape,
    val large: CornerBasedShape,
)

val StreamMateDefaultShapes = StreamMateShapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
)

/** Spacing scale plus the single TV safe area used by every screen. */
@Immutable
data class StreamMateSpacing(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    val safeHorizontal: Dp,
    val safeVertical: Dp,
)

val StreamMateDefaultSpacing = StreamMateSpacing(
    xs = 4.dp,
    sm = 8.dp,
    md = 12.dp,
    lg = 16.dp,
    xl = 24.dp,
    xxl = 32.dp,
    safeHorizontal = 40.dp,
    safeVertical = 24.dp,
)

private val LocalStreamMatePalette = staticCompositionLocalOf { StreamMateDefaultPalette }
private val LocalStreamMateTypography = staticCompositionLocalOf { StreamMateDefaultTypography }
private val LocalStreamMateShapes = staticCompositionLocalOf { StreamMateDefaultShapes }
private val LocalStreamMateSpacing = staticCompositionLocalOf { StreamMateDefaultSpacing }

object StreamMateThemeTokens {
    val palette: StreamMatePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalStreamMatePalette.current

    val typography: StreamMateTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalStreamMateTypography.current

    val shapes: StreamMateShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalStreamMateShapes.current

    val spacing: StreamMateSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalStreamMateSpacing.current
}

@Composable
fun StreamMateTheme(
    palette: StreamMatePalette = StreamMateDefaultPalette,
    typography: StreamMateTypography = StreamMateDefaultTypography,
    shapes: StreamMateShapes = StreamMateDefaultShapes,
    spacing: StreamMateSpacing = StreamMateDefaultSpacing,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = palette.focus,
        secondary = palette.accent,
        error = palette.danger,
        background = palette.background,
        surface = palette.surface,
        onPrimary = palette.background,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary,
    )
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalStreamMatePalette provides palette,
            LocalStreamMateTypography provides typography,
            LocalStreamMateShapes provides shapes,
            LocalStreamMateSpacing provides spacing,
            LocalContentColor provides colorScheme.onBackground,
            content = content,
        )
    }
}
