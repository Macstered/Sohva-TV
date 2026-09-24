package com.sohva.tv.spike.guidegrid

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.ContentColors
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Variant B, the baseline: beta 23's structure. Every programme is a focusable composable with an
 * offset, a width, an alpha layer, a clip, an animated background and two texts; focus moves
 * natively between cells. Kept only to measure A against.
 */
@Composable
fun CellGrid(firstRow: FocusRequester, modifier: Modifier = Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(SpikeData.CHANNELS, key = { it }) { index ->
            val row = remember(index) { SpikeData.row(index) }
            Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
                ChannelCellB(row, if (index == 0) firstRow else null)
                Spacer(Modifier.width(6.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val perMinute = maxWidth / SpikeData.WINDOW_MINUTES
                    row.programmes.forEach { p ->
                        if (p.endMinute > 0 && p.startMinute < SpikeData.WINDOW_MINUTES) {
                            val start = p.startMinute.coerceAtLeast(0)
                            val end = p.endMinute.coerceAtMost(SpikeData.WINDOW_MINUTES)
                            ProgrammeCellB(p, Modifier.offset(x = perMinute * start).width(perMinute * (end - start)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCellB(row: SpikeRow, requester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    val p = Sohva.palette
    val fill by animateColorAsState(if (focused) p.textPrimary else Color.Transparent, label = "channel")
    val ink = if (focused) p.background else p.textMuted
    Row(
        Modifier
            .width(232.dp)
            .fillMaxHeight()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.number, Modifier.width(20.dp), style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold), color = ink)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(p.surface), contentAlignment = Alignment.Center) {
            Text(row.initials, style = Sohva.typography.caption.copy(fontWeight = FontWeight.Black), color = p.textPrimary)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(row.name, style = Sohva.typography.label.copy(lineHeight = 17.sp), color = ink, maxLines = 1)
            Text(row.feed, style = Sohva.typography.caption, color = if (focused) p.background.copy(alpha = 0.62f) else p.textDim, maxLines = 1)
        }
    }
}

@Composable
private fun ProgrammeCellB(p: SpikeProgramme, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val palette = Sohva.palette
    val airing = p.startMinute <= SpikeData.NOW_MINUTE && p.endMinute > SpikeData.NOW_MINUTE
    val past = p.endMinute <= SpikeData.NOW_MINUTE
    val fill = when {
        focused -> palette.textPrimary
        airing -> palette.surface
        else -> palette.surfaceSubtle
    }
    Box(
        modifier
            .fillMaxHeight()
            .padding(end = 4.dp)
            .alpha(if (past && !focused) 0.55f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    ) {
        if (!focused) {
            val genres = listOf(ContentColors.genreFilm, ContentColors.genreSport, ContentColors.genreNews, ContentColors.genreChildren)
            Box(Modifier.align(Alignment.CenterStart).size(3.dp, 32.dp).background(genres[p.genre]))
        }
        Column(Modifier.padding(start = if (focused) 8.dp else 10.dp, end = 8.dp, top = 4.dp)) {
            val ink = if (focused) palette.background else palette.textPrimary
            Text(p.title, style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold, lineHeight = 15.sp), color = ink, maxLines = 1)
            Text(p.time, style = Sohva.typography.caption.copy(lineHeight = 14.sp), color = if (focused) palette.background.copy(alpha = 0.62f) else palette.textDim, maxLines = 1)
        }
    }
}
