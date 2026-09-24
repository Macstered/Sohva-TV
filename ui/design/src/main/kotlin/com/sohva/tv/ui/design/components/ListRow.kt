package com.sohva.tv.ui.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.TvSurfaceColors
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** Layout options of a [TvListRow]. */
@Immutable
data class ListRowLayout(
    /** Dense rows use `label` 14/19 instead of `body` 16/23. */
    val dense: Boolean = false,
    /** A hairline at the top edge, hidden while the row is focused. */
    val divider: Boolean = false,
    val labelLines: Int = 1,
)

/**
 * A row in a list: rows are separated by a hairline rather than a box each, the selected one is
 * marked by a 3 × 22 dp accent bar and Bold, the focused one by the fill flip (design/02 §8).
 * Drawn in one pass: fill, bar and hairline, no clip, no shadow.
 */
@Composable
fun TvListRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    supporting: String? = null,
    trailing: String? = null,
    state: SurfaceState = SurfaceState(),
    layout: ListRowLayout = ListRowLayout(),
) {
    val style = SurfaceStyle(corner = Sohva.shapes.small, focusScale = 1f, padding = PaddingValues(12.dp, 8.dp))
    TvSurface(onClick = onClick, modifier = modifier.fillMaxWidth().hairline(layout.divider, Sohva.palette.divider), state = state, style = style) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionMark(state.selected, colors)
            if (icon != null) {
                Icon(icon, size = 18.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                val text = if (layout.dense) Sohva.typography.label else Sohva.typography.body
                val weight = if (state.selected) FontWeight.Bold else FontWeight.Medium
                Text(label, style = text.copy(fontWeight = weight), maxLines = layout.labelLines)
                if (supporting != null) {
                    Text(supporting, style = Sohva.typography.caption, color = colors.secondaryContent, maxLines = 1)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Text(trailing, style = Sohva.typography.caption, color = colors.secondaryContent, maxLines = 1)
            }
        }
    }
}

/** The accent bar of a selected row, or a spacer of the same width so labels line up. */
@Composable
private fun SelectionMark(selected: Boolean, colors: TvSurfaceColors) {
    if (!selected) {
        Spacer(Modifier.width(15.dp))
        return
    }
    // On a focused row the bar turns dark, or cyan on white would swallow it.
    val bar = if (colors.focused) Sohva.palette.background else Sohva.palette.focus
    val corner = Sohva.shapes.small
    Spacer(
        Modifier
            .size(3.dp, 22.dp)
            .drawBehind { drawRoundRect(bar, cornerRadius = CornerRadius(corner.toPx().coerceAtMost(size.width / 2))) },
    )
    Spacer(Modifier.width(12.dp))
}

/** A 1 dp divider inset 12 dp on both sides at the top edge of the row. */
private fun Modifier.hairline(show: Boolean, color: Color): Modifier {
    if (!show) return this
    return drawBehind {
        val inset = 12.dp.toPx()
        drawLine(color, Offset(inset, 0f), Offset(size.width - inset, 0f), strokeWidth = 1.dp.toPx())
    }
}
