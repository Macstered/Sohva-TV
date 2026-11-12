package com.sohva.tv.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.components.Icon
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** Wide enough for the longest Finnish reconnect line on one row at 1080p. */
private val NOTICE_MAX_WIDTH: Dp = 720.dp

/**
 * A notice over the picture (spec 30 §5.13): a small dark card at the bottom centre, sized to its
 * text. It replaced a full-width red bar that covered a quarter of the screen (owner, 1 November
 * 2026). While the app reconnects by itself ([working]) it is one line with the reconnect icon;
 * the [detail] and the [action] are given only when the viewer has to do something.
 */
@Composable
internal fun PlayerNotice(
    line: String,
    working: Boolean,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val p = Sohva.palette
    val s = Sohva.spacing
    Column(
        modifier
            .padding(bottom = s.safeVertical)
            .widthIn(max = NOTICE_MAX_WIDTH)
            .roundFill(p.background.copy(alpha = 0.8f), Sohva.shapes.large)
            .padding(horizontal = s.xl, vertical = s.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Static marks only: nothing here may draw a frame per vsync over the video (spec 30 §9).
            if (working) {
                Icon(TvIcons.Refresh, size = 20.dp, tint = p.focus)
            } else {
                Box(Modifier.size(s.sm).drawBehind { drawCircle(p.danger) })
            }
            Text(
                line,
                Modifier.padding(start = s.md),
                style = Sohva.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = p.textPrimary,
                maxLines = 2,
            )
        }
        if (detail != null) {
            Text(
                detail,
                Modifier.padding(top = s.xs),
                style = Sohva.typography.caption.copy(textAlign = TextAlign.Center),
                color = p.textMuted,
                maxLines = 2,
            )
        }
        if (action != null) Box(Modifier.padding(top = s.sm)) { action() }
    }
}
