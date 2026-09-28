package com.sohva.tv.ui.design.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.tvSurfaceColors
import com.sohva.tv.ui.design.motion.Motion
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** An uppercase section heading: overline Bold, 1.4 sp tracking, `textDim` (settings.md §3). */
@Composable
fun SettingsOverline(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(start = 14.dp, top = 18.dp, bottom = 8.dp),
        style = Sohva.typography.overline,
        color = Sohva.palette.textDim,
        maxLines = 1,
    )
}

/** A group: a hairline inset 14 dp, then its rows. No panel, no outline, no second background. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val divider = Sohva.palette.divider
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val inset = 14.dp.toPx()
                drawLine(divider, Offset(inset, 0f), Offset(size.width - inset, 0f), 1.dp.toPx())
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Not focusable itself; its trailing controls are. Minimum 74 dp. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 74.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, icon, subtitle, Sohva.palette.textPrimary, Sohva.palette.textDim, Modifier.weight(1f))
        Spacer(Modifier.width(14.dp))
        trailing()
    }
}

/**
 * A focusable row that opens a picker or editor: value in Bold, then a chevron. [divider] draws the
 * hairline beta 23 puts between rows of a list, inset like the row's content; the focused fill
 * covers it.
 */
@Composable
fun SettingsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    state: SurfaceState = SurfaceState(),
    divider: Boolean = false,
) {
    val line = Sohva.palette.divider
    val drawn = if (!divider) modifier else modifier.drawBehind {
        val inset = 14.dp.toPx()
        drawLine(line, Offset(inset, 0f), Offset(size.width - inset, 0f), 1.dp.toPx())
    }
    val style = SurfaceStyle(
        corner = Sohva.shapes.medium,
        restingContent = Sohva.palette.textPrimary,
        focusScale = 1f,
        padding = PaddingValues(14.dp, 10.dp),
    )
    TvSurface(onClick, drawn.fillMaxWidth().heightIn(min = 74.dp), state, style) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, icon, subtitle, colors.content, colors.secondaryContent, Modifier.weight(1f))
            Text(value, style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Icon(TvIcons.ChevronRight, size = 18.dp)
        }
    }
}

@Composable
private fun RowText(
    title: String,
    @DrawableRes icon: Int?,
    subtitle: String?,
    titleColor: androidx.compose.ui.graphics.Color,
    subtitleColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, size = 20.dp, tint = Sohva.palette.textDim) else Spacer(Modifier.width(20.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = titleColor, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = Sohva.typography.label, color = subtitleColor, maxLines = 2)
        }
    }
}

/**
 * The Settings switch: an outer surface (radius 15, padding 5) around a 52 × 30 track with a
 * 24 dp knob travelling 22 dp. Off: `surfaceRaised` track, `textMuted` knob; on: `focus` track,
 * `background` knob; disabled track `surface`. Track colour and knob move with the default spring.
 * [keepsFocus] keeps a switch focusable while it is disabled for a moment (an action running).
 */
@Composable
fun SettingsSwitch(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, keepsFocus: Boolean = false) {
    TvSurface(onToggle, modifier, SurfaceState(selected = checked, enabled = enabled, keepsFocus = keepsFocus), SWITCH_STYLE) {
        SwitchTrack(checked, enabled)
    }
}

/**
 * A settings row whose switch is its control. The whole row is the focus target, so D-pad focus
 * search sees a full-width row like the rows above and below it; the switch alone draws the focus
 * look, as [SettingsSwitch] does. A focus target only at the row's far end lost to the next
 * full-width row whenever the pane was wide in dp (a smaller interface size or a lower screen
 * density): Down from the theme skipped Channel numbers for a tester. OK anywhere on the row flips
 * it. [modifier] goes on the focus target (tags and focus requesters of the switch belong there);
 * [containerModifier] on the row around it.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    keepsFocus: Boolean = false,
    containerModifier: Modifier = Modifier,
) {
    val state = SurfaceState(selected = checked, enabled = enabled, keepsFocus = keepsFocus)
    Box(containerModifier) {
        TvSurface(onToggle, modifier.fillMaxWidth(), state, ROW_STYLE) { colors ->
            SettingsRow(title, icon = icon, subtitle = subtitle) {
                // The switch's own look for the row's focus: the same colours a focused SettingsSwitch has.
                val look = tvSurfaceColors(colors.focused, state, SWITCH_STYLE)
                Box(Modifier.drawBehind { drawRoundRect(look.background, cornerRadius = CornerRadius(SWITCH_STYLE.corner.toPx())) }.padding(SWITCH_PADDING)) {
                    SwitchTrack(checked, enabled)
                }
            }
        }
    }
}

private val SWITCH_PADDING = 5.dp
private val SWITCH_STYLE = SurfaceStyle(corner = 15.dp, focusScale = 1f, padding = PaddingValues(SWITCH_PADDING))
private val ROW_STYLE = SurfaceStyle(corner = 0.dp, focusScale = 1f, animateFill = false, quiet = true)

/** The 52 × 30 track and its knob. */
@Composable
private fun SwitchTrack(checked: Boolean, enabled: Boolean) {
    val p = Sohva.palette
    val track by animateColorAsState(
        when {
            !enabled -> p.surface
            checked -> p.focus
            else -> p.surfaceRaised
        },
        Motion.focus(),
        label = "switch-track",
    )
    val knobColor = if (checked) p.background else p.textMuted
    val offset by animateDpAsState(if (checked) 22.dp else 0.dp, Motion.focus(), label = "switch-knob")
    Box(
        Modifier.size(52.dp, 30.dp).drawBehind {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
            val knob = 24.dp.toPx()
            val gap = (size.height - knob) / 2
            drawCircle(knobColor, knob / 2, Offset(gap + offset.toPx() + knob / 2, size.height / 2))
        },
    )
}

/** A choice in a picker dialog: min 48 dp; the selected one is filled and ticked. */
@Composable
fun PickerRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    state: SurfaceState = SurfaceState(),
) {
    val style = SurfaceStyle(
        corner = Sohva.shapes.medium,
        restingContent = Sohva.palette.textPrimary,
        focusScale = 1f,
        padding = PaddingValues(14.dp, 10.dp),
    )
    TvSurface(onClick, modifier.fillMaxWidth().heightIn(min = 48.dp), state, style) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                if (description != null) {
                    Text(description, style = Sohva.typography.label, color = colors.secondaryContent, maxLines = 2)
                }
            }
            if (state.selected) Icon(TvIcons.Check, size = 18.dp)
        }
    }
}
