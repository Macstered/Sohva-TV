package com.streammate.tv.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvSurface

/**
 * The vocabulary Settings is drawn from.
 *
 * The screen used to be a stack of filled panels, each with its own heading,
 * help paragraph and cluster of buttons. It reads as one list now: an uppercase
 * overline naming the group, then rows of a fixed height carrying an icon, a
 * title, a line of explanation and whatever control the setting actually needs,
 * separated by hairlines. Nothing has a resting outline; the only filled
 * surface on the screen is whatever has focus.
 */

/** The uppercase label that opens a group of settings. */
@Composable
internal fun SettingsOverline(text: String, modifier: Modifier = Modifier) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = SETTINGS_ROW_PADDING, top = 18.dp, bottom = 8.dp),
        color = palette.textDim,
        fontSize = typography.overline.fontSize,
        lineHeight = typography.overline.lineHeight,
        fontWeight = FontWeight.Bold,
        letterSpacing = typography.overline.letterSpacing,
    )
}

/**
 * One setting.
 *
 * Icon, what it is, what it does, and its current value or control on the far
 * side. The row is not focusable itself - the control inside it is - so a
 * setting with two controls does not become one target that reaches neither.
 * [onClick] turns the whole row into the control for the settings that are a
 * single choice.
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    divider: Boolean = true,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SETTINGS_ROW_PADDING)
                    .height(1.dp)
                    .background(palette.divider),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SETTINGS_ROW_HEIGHT)
                .padding(horizontal = SETTINGS_ROW_PADDING, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = palette.textPrimary,
                    fontSize = typography.bodyLarge.fontSize,
                    lineHeight = typography.bodyLarge.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 2.dp),
                        color = palette.textDim,
                        fontSize = typography.label.fontSize,
                        lineHeight = typography.label.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.let {
                Spacer(Modifier.width(SETTINGS_ROW_PADDING))
                Row(verticalAlignment = Alignment.CenterVertically, content = it)
            }
        }
    }
}

@Composable
private fun SettingsRowIcon(@DrawableRes icon: Int?) {
    val palette = StreamMateThemeTokens.palette
    if (icon == null) {
        Spacer(Modifier.width(SETTINGS_ICON_SIZE + SETTINGS_ROW_PADDING))
        return
    }
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(palette.textDim),
        modifier = Modifier
            .size(SETTINGS_ICON_SIZE)
            .clearAndSetSemantics { },
    )
    Spacer(Modifier.width(SETTINGS_ROW_PADDING))
}

/**
 * A switch.
 *
 * Focus is the same off-white fill as every other control, so the track alone
 * says on or off and never has to double as the focus signal.
 */
@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val palette = StreamMateThemeTokens.palette
    TvSurface(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        shape = RoundedCornerShape(SWITCH_HEIGHT / 2),
        selected = checked,
        enabled = enabled,
        resting = Color.Transparent,
        focusScale = 1f,
        testTag = testTag,
        contentPadding = PaddingValues(5.dp),
        contentAlignment = Alignment.Center,
    ) { colors ->
        val track by animateColorAsState(
            when {
                !enabled -> palette.surface
                checked -> palette.focus
                else -> palette.surfaceRaised
            },
            label = "switch track",
        )
        val knobOffset by animateDpAsState(
            if (checked) SWITCH_WIDTH - SWITCH_HEIGHT else 0.dp,
            label = "switch knob",
        )
        Box(
            modifier = Modifier
                .width(SWITCH_WIDTH)
                .height(SWITCH_HEIGHT)
                .clip(RoundedCornerShape(SWITCH_HEIGHT / 2))
                .background(track),
        ) {
            Box(
                modifier = Modifier
                    .padding(start = knobOffset)
                    .padding(3.dp)
                    .size(SWITCH_HEIGHT - 6.dp)
                    .clip(CircleShape)
                    .background(if (checked) palette.background else colors.content),
            )
        }
    }
}

/**
 * A row whose whole width opens something: a menu, an editor, another screen.
 *
 * The current value sits beside a chevron, so the row says what it is set to
 * without being opened.
 */
@Composable
internal fun SettingsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    @DrawableRes chevron: Int = TvIcons.ChevronRight,
    enabled: Boolean = true,
    divider: Boolean = true,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    testTag: String? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SETTINGS_ROW_PADDING)
                    .height(1.dp)
                    .background(palette.divider),
            )
        }
        TvSurface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = SETTINGS_ROW_HEIGHT),
            shape = StreamMateThemeTokens.shapes.medium,
            enabled = enabled,
            resting = Color.Transparent,
            restingContent = palette.textPrimary,
            focusScale = 1f,
            focusRequester = focusRequester,
            testTag = testTag,
            contentPadding = PaddingValues(horizontal = SETTINGS_ROW_PADDING, vertical = 10.dp),
        ) { colors ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Image(
                        painter = painterResource(it),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.secondaryContent),
                        modifier = Modifier.size(SETTINGS_ICON_SIZE).clearAndSetSemantics { },
                    )
                    Spacer(Modifier.width(SETTINGS_ROW_PADDING))
                } ?: Spacer(Modifier.width(SETTINGS_ICON_SIZE + SETTINGS_ROW_PADDING))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = colors.content,
                        fontSize = typography.bodyLarge.fontSize,
                        lineHeight = typography.bodyLarge.lineHeight,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(top = 2.dp),
                            color = colors.secondaryContent,
                            fontSize = typography.label.fontSize,
                            lineHeight = typography.label.lineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(SETTINGS_ROW_PADDING))
                Text(
                    text = value,
                    color = colors.content,
                    fontSize = typography.body.fontSize,
                    lineHeight = typography.body.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Image(
                    painter = painterResource(chevron),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.content),
                    modifier = Modifier.size(18.dp).clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * One configured source, as a chip.
 *
 * The dot is the source's real state: cyan when it is enabled and its last
 * refresh succeeded, red when a refresh has been failing, and dim when the
 * source is switched off. Nothing about the address or the credentials appears
 * here - only the name the viewer gave it.
 */
@Composable
internal fun SettingsSourceChip(
    label: String,
    status: SettingsSourceStatus,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    testTag: String? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    TvSurface(
        onClick = onClick,
        modifier = modifier.heightIn(min = SETTINGS_CHIP_HEIGHT),
        shape = StreamMateThemeTokens.shapes.medium,
        selected = selected,
        resting = palette.surface,
        restingContent = palette.textPrimary,
        focusScale = 1f,
        focusRequester = focusRequester,
        testTag = testTag,
        contentPadding = PaddingValues(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            SettingsSourceStatus.HEALTHY -> palette.focus
                            SettingsSourceStatus.FAILING -> palette.danger
                            SettingsSourceStatus.DISABLED -> colors.secondaryContent
                        },
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = colors.content,
                fontSize = typography.body.fontSize,
                lineHeight = typography.body.lineHeight,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What the dot on a source chip is saying. */
internal enum class SettingsSourceStatus {
    HEALTHY,
    FAILING,
    DISABLED,
}

internal val SETTINGS_ROW_HEIGHT = 74.dp
internal val SETTINGS_ROW_PADDING = 14.dp
internal val SETTINGS_ICON_SIZE = 20.dp
internal val SETTINGS_CHIP_HEIGHT = 44.dp
internal val SETTINGS_RAIL_ROW_HEIGHT = 52.dp

private val SWITCH_WIDTH = 52.dp
private val SWITCH_HEIGHT = 30.dp
