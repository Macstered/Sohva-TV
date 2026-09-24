package com.sohva.tv.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

enum class TagTone { PRIMARY, ACCENT, MUTED, RATING, LIVE }

/**
 * A small non-focusable fact ("4K", "50 FPS", "TMDB 7.8"): tinted, not outlined; LIVE is filled
 * (design/02 §10). One round rect behind the text, no clip.
 */
@Composable
fun TvTagChip(text: String, modifier: Modifier = Modifier, tone: TagTone = TagTone.MUTED) {
    val p = Sohva.palette
    val (fill, ink) = when (tone) {
        TagTone.PRIMARY -> p.focus.copy(alpha = 0.14f) to p.focus
        TagTone.ACCENT -> p.accent.copy(alpha = 0.14f) to p.accent
        TagTone.MUTED -> p.textMuted.copy(alpha = 0.14f) to p.textMuted
        TagTone.RATING -> p.rating.copy(alpha = 0.14f) to p.rating
        TagTone.LIVE -> p.danger to p.textPrimary
    }
    Text(
        text = text,
        modifier = modifier.roundFill(fill, Sohva.shapes.small).padding(horizontal = 8.dp, vertical = 3.dp),
        style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold),
        color = ink,
        maxLines = 1,
    )
}

/** Guide key hints: a key chip and its label; only bindings that work are listed (design/02 §15). */
@Composable
fun KeyHints(hints: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        hints.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    key,
                    Modifier.roundFill(Sohva.palette.surface, Sohva.shapes.small).padding(horizontal = 6.dp, vertical = 1.dp),
                    style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = Sohva.palette.textMuted,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Text(label, style = Sohva.typography.caption, color = Sohva.palette.textDim, maxLines = 1)
            }
        }
    }
}

/** A rounded fill drawn behind the node: the cheap alternative to clip + background. */
fun Modifier.roundFill(color: Color, corner: Dp): Modifier =
    drawBehind { drawRoundRect(color, cornerRadius = CornerRadius(corner.toPx())) }
