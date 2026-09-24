package com.sohva.tv.ui.design.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * A dialog or picker card: always `panel` (in Original the old `surface` cards were 6 % white and
 * nearly transparent, design/01 §17), optional 1 dp border, rounded corners.
 */
@Composable
fun DialogCard(
    modifier: Modifier = Modifier,
    corner: Dp = Sohva.shapes.medium,
    border: Color? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val framed = if (border == null) modifier else modifier.border(1.dp, border, RoundedCornerShape(corner))
    Column(
        framed.roundFill(Sohva.palette.panel, corner).padding(padding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** A dialog title: 18 sp Bold `textPrimary`, the common size of the platform dialogs. */
@Composable
fun DialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(bottom = 6.dp),
        style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        color = Sohva.palette.textPrimary,
        maxLines = 2,
    )
}

/**
 * The options sheet of the guide and the walls (guide.md §5): an in-screen overlay (one focus
 * root; a remote has no click outside). A `background` α0.86 scrim over the safe content box, a
 * centred `panel` card 340–420 dp wide with large corners, padding 24, a `headline` Black title,
 * an optional `textMuted` subtitle and its actions 8 dp apart. The scrim stays translucent (the
 * dimmed screen is part of the look), so nothing beneath may animate while it is up.
 */
@Composable
fun OptionsSheet(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = Sohva.spacing
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = spacing.safeHorizontal, vertical = spacing.safeVertical)
            .roundFill(Sohva.palette.background.copy(alpha = 0.86f), 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(340.dp, 420.dp)
                .roundFill(Sohva.palette.panel, Sohva.shapes.large)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = Sohva.typography.headline.copy(fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary)
            if (subtitle != null) {
                Text(subtitle, Modifier.padding(bottom = 8.dp), style = Sohva.typography.label, color = Sohva.palette.textMuted)
            }
            content()
        }
    }
}
