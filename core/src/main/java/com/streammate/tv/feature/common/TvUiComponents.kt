package com.streammate.tv.feature.common

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import java.util.Random
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.streammate.tv.core.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.LocalContentColor
import com.streammate.tv.app.StreamMateThemeTokens

@Composable
fun StreamMateScreenBackground(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val spacing = StreamMateThemeTokens.spacing
    val padding = contentPadding ?: PaddingValues(
        horizontal = spacing.safeHorizontal,
        vertical = spacing.safeVertical,
    )
    val grain = rememberGrainBrush()
    Box(modifier = modifier.fillMaxSize().background(palette.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Ground. Near black, lifted a shade at the top so the frame has a
            // direction without anything on it reading as a separate layer.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to palette.backgroundTop,
                    0.5f to palette.background,
                    1f to palette.backgroundBottom,
                    startY = 0f,
                    endY = h,
                ),
            )

            // Two ambient washes, both off-canvas past the top edge and both
            // weak enough to read as light rather than as shapes.
            drawRect(
                brush = Brush.radialGradient(
                    0f to palette.focus.copy(alpha = 0.10f),
                    0.6f to palette.focus.copy(alpha = 0.02f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.12f, h * -0.10f),
                    radius = w * 0.58f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    0f to palette.secondaryGlow.copy(alpha = 0.12f),
                    0.62f to palette.secondaryGlow.copy(alpha = 0.02f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.92f, h * 0.04f),
                    radius = w * 0.50f,
                ),
            )

            // Dither. Eight-bit gradients this large band visibly on a 4K panel;
            // a tiled noise tile at a few percent alpha breaks the steps up.
            drawRect(brush = grain)
        }
        content(Modifier.fillMaxSize().padding(padding))
    }
}

/**
 * A small tiled noise texture used purely to dither the background gradient.
 * Generated once and repeated, so it costs one 96x96 bitmap per process.
 */
@Composable
private fun rememberGrainBrush(): Brush {
    val shader = remember {
        val size = 96
        val pixels = IntArray(size * size)
        val random = Random(20260829L)
        for (index in pixels.indices) {
            val alpha = random.nextInt(GRAIN_MAX_ALPHA)
            pixels[index] = (alpha shl 24) or 0x00FFFFFF
        }
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated)
    }
    return remember(shader) { ShaderBrush(shader) }
}

private const val GRAIN_MAX_ALPHA = 16

@Composable
fun SohvaTvBrand(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 34.sp,
) {
    val palette = StreamMateThemeTokens.palette
    SohvaWordmark(
        name = stringResource(R.string.brand_sohva_tv),
        accent = palette.focus,
        fontSize = fontSize,
        modifier = modifier,
    )
}

@Composable
fun SohvaSportBrand(modifier: Modifier = Modifier) {
    SohvaWordmark(
        name = stringResource(R.string.brand_sohva_sport),
        accent = StreamMateThemeTokens.palette.accent,
        fontSize = 30.sp,
        modifier = modifier,
    )
}

/** Real text keeps the two brand lock-ups crisp and readable by accessibility services. */
@Composable
private fun SohvaWordmark(
    name: String,
    accent: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildAnnotatedString {
            append(name)
            addStyle(SpanStyle(color = accent), name.lastIndexOf(' ') + 1, name.length)
        },
        modifier = modifier,
        color = StreamMateThemeTokens.palette.textPrimary,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * The app's icon set. Exposed as ids rather than R references so that feature
 * modules do not have to import core's R alongside their own.
 *
 * These replace the Unicode glyphs the UI used to render as text: a glyph is
 * drawn from whatever font the system picks, with unpredictable metrics and no
 * guarantee it exists at all.
 */
object TvIcons {
    val Home: Int = R.drawable.ic_tv_home
    val Back: Int = R.drawable.ic_tv_back
    val Aspect: Int = R.drawable.ic_tv_aspect
    val Audio: Int = R.drawable.ic_tv_audio
    val Subtitles: Int = R.drawable.ic_tv_subtitles
    val Stats: Int = R.drawable.ic_tv_stats
    val ChevronRight: Int = R.drawable.ic_tv_chevron_right
    val ChevronDown: Int = R.drawable.ic_tv_chevron_down
    val Lock: Int = R.drawable.ic_tv_lock
    val Link: Int = R.drawable.ic_tv_link
    val Key: Int = R.drawable.ic_tv_key
    val Refresh: Int = R.drawable.ic_tv_refresh
    val Check: Int = R.drawable.ic_tv_check
    val Play: Int = R.drawable.ic_tv_play
    val Pause: Int = R.drawable.ic_tv_pause
    val Save: Int = R.drawable.ic_tv_save
    val Settings: Int = R.drawable.ic_tv_settings
    val Delete: Int = R.drawable.ic_tv_delete
    val Close: Int = R.drawable.ic_tv_close
    val Channels: Int = R.drawable.ic_tv_channels
    val Target: Int = R.drawable.ic_tv_target
    val Guide: Int = R.drawable.ic_tv_guide
    val Epg: Int = R.drawable.ic_tv_epg
    val Info: Int = R.drawable.ic_tv_info
    val Search: Int = R.drawable.ic_tv_search
    val Replay: Int = R.drawable.ic_tv_replay
    val Forward: Int = R.drawable.ic_tv_forward
    val Rewind: Int = R.drawable.ic_tv_rewind
    val Star: Int = R.drawable.ic_tv_star
    val StarOutline: Int = R.drawable.ic_tv_star_outline
}

/**
 * The app's button. Borderless at rest on a step of the surface ladder; on
 * focus it flips to the off-white fill with near-black content, which is the
 * same thing every other focusable surface does.
 *
 * [selected] is a state, not focus: it stays on the ladder and says so with
 * the accent, so a selected button and the focused one never look alike.
 */
@Composable
fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    testTag: String? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
    danger: Boolean = false,
    selected: Boolean = false,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    var focused by remember { mutableStateOf(false) }
    val colors = tvSurfaceColors(
        focused = focused,
        selected = selected,
        enabled = enabled,
        danger = danger,
        resting = palette.surface,
        restingContent = palette.textPrimary,
    )
    val background by animateColorAsState(
        if (!enabled) palette.surfaceSubtle else colors.background,
        label = "button background",
    )
    val content by animateColorAsState(
        when {
            !enabled -> palette.textDisabled
            !focused && danger -> palette.danger
            !focused && selected -> palette.focus
            else -> colors.content
        },
        label = "button content",
    )
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "button scale")
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    val tagModifier = testTag?.let { Modifier.testTag(it) } ?: Modifier
    val shape = StreamMateThemeTokens.shapes.small
    Box(
        modifier = modifier
            .then(requesterModifier)
            .then(tagModifier)
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) { this.selected = selected }
            // No press or focus wash: this design says "focused" with the
            // fill flip and the lift. The default indication would also draw
            // at the unlifted bounds, since it sits outside the layer.
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            // The lift sits inside the focus target on purpose. A layer
            // applied around it enlarges the bounds the component reports,
            // so a scrollable parent scrolls to fit the newly focused item
            // and every sibling shifts with it.
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 10.dp else 0.dp,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(shape)
            .background(background)
            .padding(
                horizontal = if (compact) 12.dp else 18.dp,
                vertical = if (compact) 7.dp else 11.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { iconRes ->
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(content),
                    modifier = Modifier
                        .size(if (compact) 16.dp else 18.dp)
                        .clearAndSetSemantics { },
                )
                Spacer(Modifier.width(if (compact) 7.dp else 9.dp))
            }
            Text(
                text = label,
                color = content,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) typography.caption.fontSize else typography.label.fontSize,
                lineHeight = if (compact) typography.caption.lineHeight else typography.label.lineHeight,
            )
        }
    }
}

@Composable
fun TvUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    leadingIcon: String? = null,
    /** A drawn leading icon. Preferred over [leadingIcon], which is a glyph. */
    @DrawableRes leadingIconRes: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Uri,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    editOnClickOnly: Boolean = false,
    compact: Boolean = false,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    var displayFocused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(!editOnClickOnly) }
    var restoreDisplayFocus by remember { mutableStateOf(false) }
    val displayFocusRequester = remember { FocusRequester() }
    val editorFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shapes = StreamMateThemeTokens.shapes
    val fieldShape = if (compact) shapes.small else shapes.medium
    val horizontalPadding = if (compact) 12.dp else 16.dp
    val verticalPadding = if (compact) 8.dp else 14.dp
    val fieldFontSize = if (compact) typography.label.fontSize else typography.body.fontSize
    val iconFontSize = if (compact) typography.headline.fontSize else typography.bodyLarge.fontSize
    val iconSpacing = if (compact) 8.dp else 12.dp
    val fieldBackground = if (displayFocused) palette.textPrimary else palette.surface
    val fieldContent = if (displayFocused) palette.background else palette.textPrimary
    val fieldHint = if (displayFocused) palette.background.copy(alpha = 0.62f) else palette.textMuted
    val fieldIcon = if (displayFocused) palette.background.copy(alpha = 0.72f) else palette.textMuted
    val tagModifier = testTag?.let { Modifier.testTag(it) } ?: Modifier
    val completeEditing = {
        keyboardController?.hide()
        if (editOnClickOnly) {
            editing = false
            restoreDisplayFocus = true
        }
    }
    val beginEditing = {
        if (editOnClickOnly && !editing) editing = true
    }
    LaunchedEffect(restoreDisplayFocus) {
        if (restoreDisplayFocus) {
            repeat(FOCUS_RESTORE_ATTEMPTS) {
                withFrameNanos { }
                if (displayFocusRequester.requestFocus()) {
                    restoreDisplayFocus = false
                    return@LaunchedEffect
                }
            }
            restoreDisplayFocus = false
        }
    }
    val editorContent: @Composable (Modifier) -> Unit = { editorModifier ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { completeEditing() }),
            visualTransformation = visualTransformation,
            textStyle = TextStyle(color = palette.textPrimary, fontSize = fieldFontSize),
            cursorBrush = SolidColor(palette.focus),
            modifier = editorModifier.semantics { contentDescription = label },
            decorationBox = { innerField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvFieldLeadingIcon(
                        leadingIcon,
                        leadingIconRes,
                        palette.textMuted,
                        iconFontSize,
                        iconSpacing,
                    )
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(text = label, color = palette.textMuted, fontSize = fieldFontSize)
                        }
                        innerField()
                    }
                }
            },
        )
    }
    Box(
        modifier = modifier
            .then(if (editOnClickOnly && !editing) tagModifier else Modifier)
            .then(if (editOnClickOnly) Modifier.focusRequester(displayFocusRequester) else Modifier)
            .onFocusChanged { if (editOnClickOnly) displayFocused = it.isFocused }
            .then(
                if (editOnClickOnly) {
                    Modifier
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.DirectionCenter)
                            ) {
                                beginEditing()
                                true
                            } else {
                                false
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures { beginEditing() } }
                        .semantics {
                            contentDescription = label
                            role = Role.Button
                            onClick {
                                beginEditing()
                                true
                            }
                        }
                        .focusable()
                } else {
                    Modifier.semantics { contentDescription = label }
                },
            )
            .shadow(
                elevation = if (displayFocused) 13.dp else 0.dp,
                shape = fieldShape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(fieldShape)
            .background(fieldBackground)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        if (editOnClickOnly) {
            val displayValue = visualTransformation.filter(AnnotatedString(value)).text.text
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvFieldLeadingIcon(leadingIcon, leadingIconRes, fieldIcon, iconFontSize, iconSpacing)
                Text(
                    text = displayValue.ifBlank { label },
                    color = if (displayValue.isBlank()) fieldHint else fieldContent,
                    fontSize = fieldFontSize,
                    maxLines = 1,
                )
            }
        } else {
            editorContent(Modifier.fillMaxWidth().then(tagModifier))
        }
    }

    if (editOnClickOnly && editing) {
        Dialog(onDismissRequest = { completeEditing() }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .clip(shapes.large)
                        .background(palette.panel)
                        .border(2.dp, palette.focus, shapes.large)
                        .padding(24.dp),
                ) {
                    Text(
                        text = label,
                        color = palette.textPrimary,
                        fontSize = typography.headline.fontSize,
                        lineHeight = typography.headline.lineHeight,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(fieldShape)
                            .background(palette.surface)
                            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    ) {
                        editorContent(
                            Modifier
                                .fillMaxWidth()
                                .then(tagModifier)
                                .focusRequester(editorFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                        completeEditing()
                                        true
                                    } else {
                                        false
                                    }
                                },
                        )
                    }
                }
            }
            LaunchedEffect(Unit) {
                editorFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }
}


/**
 * Whatever a field puts in front of its text: a drawn icon where the caller has
 * one, and otherwise the glyph it used to pass.
 */
@Composable
private fun TvFieldLeadingIcon(
    glyph: String?,
    @DrawableRes iconRes: Int?,
    tint: Color,
    glyphSize: TextUnit,
    spacing: Dp,
) {
    when {
        iconRes != null -> {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(18.dp).clearAndSetSemantics { },
            )
            Spacer(Modifier.width(spacing))
        }
        glyph != null -> {
            Text(text = glyph, color = tint, fontSize = glyphSize, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(spacing))
        }
    }
}

/**
 * Resolved colours for one focusable surface.
 *
 * The app has exactly one focus treatment: the surface fills with the primary
 * ink colour and its content inverts to the background colour. Unfocused
 * surfaces carry a tint or nothing at all — never an outline. Hierarchy is
 * carried by fill, type scale and spacing.
 */
data class TvSurfaceColors(
    val background: Color,
    val content: Color,
    val secondaryContent: Color,
)

/**
 * The one focus rule, resolved to colours.
 *
 * Focused: the surface fills with the off-white primary ink and its content
 * inverts to the ground colour. Selected but not focused: a quiet tint plus
 * whatever marker the component draws, so the two states never look alike.
 * Resting: a step on the surface ladder, or nothing - never an outline.
 */
@Composable
fun tvSurfaceColors(
    focused: Boolean,
    selected: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    resting: Color? = null,
    restingContent: Color? = null,
): TvSurfaceColors {
    val palette = StreamMateThemeTokens.palette
    return when {
        !enabled -> TvSurfaceColors(
            background = resting ?: Color.Transparent,
            content = palette.textDisabled,
            secondaryContent = palette.textDisabled,
        )
        focused && danger -> TvSurfaceColors(
            background = palette.danger,
            content = palette.textPrimary,
            secondaryContent = palette.textPrimary.copy(alpha = 0.72f),
        )
        focused -> TvSurfaceColors(
            background = palette.textPrimary,
            content = palette.background,
            secondaryContent = palette.background.copy(alpha = 0.62f),
        )
        selected -> TvSurfaceColors(
            background = palette.surfaceFocused,
            content = palette.textPrimary,
            secondaryContent = palette.textMuted,
        )
        else -> TvSurfaceColors(
            background = resting ?: Color.Transparent,
            content = restingContent ?: palette.textMuted,
            secondaryContent = palette.textDim,
        )
    }
}

/**
 * Borderless focusable container. Prefer this over hand-rolling
 * clip -> background -> border -> onFocusChanged at a call site.
 *
 * [focusRing] is for the surfaces the fill flip cannot reach - artwork, a
 * still, a poster - where focus has to be drawn around the content instead of
 * under it. The ring is stroked inside the bounds so it never re-measures.
 */
@Composable
fun TvSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    resting: Color? = null,
    restingContent: Color? = null,
    focusRing: Boolean = false,
    focusScale: Float = 1.04f,
    focusRequester: FocusRequester? = null,
    testTag: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable (colors: TvSurfaceColors) -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    var focused by remember { mutableStateOf(false) }
    // A ringed surface keeps its resting fill and content under focus: the
    // ring is the signal, and flipping the fill as well would hide the
    // artwork it frames and leave dark ink on a dark ground.
    val colors = tvSurfaceColors(focused && !focusRing, selected, enabled, danger, resting, restingContent)
    val resolvedShape = shape ?: StreamMateThemeTokens.shapes.small
    val background by animateColorAsState(colors.background, label = "surface background")
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "surface scale")
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    val tagModifier = testTag?.let { Modifier.testTag(it) } ?: Modifier
    Box(
        modifier = modifier
            .then(requesterModifier)
            .then(tagModifier)
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) { this.selected = selected }
            // No press or focus wash: this design says "focused" with the
            // fill flip and the lift. The default indication would also draw
            // at the unlifted bounds, since it sits outside the layer.
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            // The lift sits inside the focus target on purpose. A layer
            // applied around it enlarges the bounds the component reports,
            // so a scrollable parent scrolls to fit the newly focused item
            // and every sibling shifts with it.
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 14.dp else 0.dp,
                shape = resolvedShape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(resolvedShape)
            .background(background)
            .then(
                if (focusRing && focused) {
                    Modifier.border(FOCUS_RING_WIDTH, palette.textPrimary, resolvedShape)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            content(colors)
        }
    }
}

/**
 * A row in a sidebar, category list or settings list. Borderless: rows are
 * separated by a hairline rather than by a box each, the selected one is
 * marked by an accent bar and weight, and the focused one by the fill flip.
 *
 * [dense] keeps the label a step down the scale for the narrow sidebars that
 * have to fit a screenful of channels.
 */
@Composable
fun TvListRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailing: String? = null,
    supporting: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    dense: Boolean = false,
    divider: Boolean = false,
    focusRequester: FocusRequester? = null,
    testTag: String? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val spacing = StreamMateThemeTokens.spacing
    val labelStyle = if (dense) typography.label else typography.body
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            // The focusable is inside TvSurface, so ask this wrapper whether
            // focus is anywhere below it rather than on it.
            .onFocusChanged { focused = it.hasFocus },
    ) {
        // The hairline belongs to the row above the gap, and it steps aside
        // when this row lights up so the fill has a clean edge.
        if (divider && !focused) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = spacing.md)
                    .background(palette.divider),
            )
        }
        TvSurface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            selected = selected,
            enabled = enabled,
            focusScale = 1f,
            focusRequester = focusRequester,
            testTag = testTag,
            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
        ) { colors ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (selected) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(22.dp)
                            .clip(StreamMateThemeTokens.shapes.small)
                            // On the focused fill the bar has to invert too,
                            // or cyan-on-white swallows it.
                            .background(if (focused) palette.background else palette.focus),
                    )
                    Spacer(Modifier.width(spacing.md))
                } else {
                    Spacer(Modifier.width(3.dp + spacing.md))
                }
                icon?.let { iconRes ->
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.content),
                        modifier = Modifier.size(18.dp).clearAndSetSemantics { },
                    )
                    Spacer(Modifier.width(spacing.sm))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = label,
                        color = colors.content,
                        fontSize = labelStyle.fontSize,
                        lineHeight = labelStyle.lineHeight,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    supporting?.let {
                        Text(
                            text = it,
                            color = colors.secondaryContent,
                            fontSize = typography.caption.fontSize,
                            lineHeight = typography.caption.lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                trailing?.let {
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        text = it,
                        color = colors.secondaryContent,
                        fontSize = typography.caption.fontSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * How prominent a [TvTagChip] should be. A row of chips is only readable at
 * three metres if one of them is louder than the rest, so the caller says which
 * fact matters - resolution on a stream, a rating on a film - and the others
 * stay quiet.
 */
enum class TvTagTone {
    PRIMARY,
    ACCENT,
    MUTED,

    /** A score or rating. */
    RATING,

    /** Happening now. The one tone that should pull the eye across a room. */
    LIVE,
}

/**
 * A small, non-focusable fact: `4K`, `50 FPS`, `HDR`, `IMDb 7.8`.
 *
 * Tinted rather than outlined. These sit on artwork and in dense rows, and an
 * outline on each one rebuilds exactly the mesh of boxes this design removes.
 */
@Composable
fun TvTagChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: TvTagTone = TvTagTone.MUTED,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val color = when (tone) {
        TvTagTone.PRIMARY -> palette.focus
        TvTagTone.ACCENT -> palette.accent
        TvTagTone.MUTED -> palette.textMuted
        TvTagTone.RATING -> palette.rating
        TvTagTone.LIVE -> palette.danger
    }
    // Live is filled rather than tinted. A tint reads as a quiet fact, and
    // "on now" is the one thing here that should not be quiet.
    val filled = tone == TvTagTone.LIVE
    Text(
        text = label,
        color = if (filled) palette.textPrimary else color,
        fontSize = typography.caption.fontSize,
        lineHeight = typography.caption.lineHeight,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .clip(StreamMateThemeTokens.shapes.small)
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private const val FOCUS_RESTORE_ATTEMPTS = 6

/** The ring drawn around surfaces the fill flip cannot reach. */
private val FOCUS_RING_WIDTH = 3.dp
