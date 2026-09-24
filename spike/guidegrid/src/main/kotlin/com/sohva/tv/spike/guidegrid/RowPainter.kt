package com.sohva.tv.spike.guidegrid

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tracing.trace
import com.sohva.tv.ui.design.theme.ContentColors
import com.sohva.tv.ui.design.theme.SohvaPalette
import com.sohva.tv.ui.design.theme.SohvaTypography

internal val ROW_HEIGHT = 44.dp
internal val CHANNEL_WIDTH = 232.dp
internal val GAP = 6.dp
private val BLOCK_GAP = 4.dp
private val CORNER = 8.dp

/** Draw counts per row index, for the spike's own diagnosis only. */
object SpikeCounters {
    private val draws = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    fun rowDraw(index: Int) {
        draws.merge(index, 1, Int::plus)
    }

    fun drain(): Map<Int, Int> = draws.toSortedMap().also { draws.clear() }
}

/** Colours of the grid, resolved once per palette; "past" alpha is baked in, no alpha layers. */
@Immutable
internal data class GridColors(
    val focusFill: Color,
    val ink: Color,
    val inkSecondary: Color,
    val rowSelected: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textDim: Color,
    val surface: Color,
    val subtle: Color,
    val pastSubtle: Color,
    val pastText: Color,
    val focus: Color,
    val genres: List<Color>,
) {
    companion object {
        fun from(p: SohvaPalette) = GridColors(
            focusFill = p.textPrimary, ink = p.background, inkSecondary = p.background.copy(alpha = 0.62f),
            rowSelected = p.surfaceFocused, textPrimary = p.textPrimary, textMuted = p.textMuted, textDim = p.textDim,
            surface = p.surface, subtle = p.surfaceSubtle, pastSubtle = p.surfaceSubtle.copy(alpha = p.surfaceSubtle.alpha * 0.55f),
            pastText = p.textPrimary.copy(alpha = 0.55f), focus = p.focus,
            genres = listOf(ContentColors.genreFilm, ContentColors.genreSport, ContentColors.genreNews, ContentColors.genreChildren),
        )
    }
}

/** Draws one guide row (channel cell and programme blocks) straight onto the row's canvas. */
internal class RowPainter(private val measurer: TextMeasurer, val colors: GridColors) {
    private val caption = SohvaTypography.Default.caption
    private val title = caption.copy(fontWeight = FontWeight.Bold, lineHeight = 15.sp)
    private val time = caption.copy(lineHeight = 14.sp)
    private val name = SohvaTypography.Default.label.copy(lineHeight = 17.sp)
    private val number = caption.copy(fontWeight = FontWeight.Bold)

    /** The programme blocks of one row, drawn on the timeline's own node (x 0 = the window start). */
    fun DrawScope.drawTimeline(row: SpikeRow, column: Int) = trace("Guide:Row") {
        SpikeCounters.rowDraw(row.index)
        val x0 = 0f
        val perMinute = size.width / SpikeData.WINDOW_MINUTES
        for (i in row.programmes.indices) {
            val p = row.programmes[i]
            if (p.endMinute <= 0 || p.startMinute >= SpikeData.WINDOW_MINUTES) continue
            val left = x0 + p.startMinute.coerceAtLeast(0) * perMinute
            val right = x0 + p.endMinute.coerceAtMost(SpikeData.WINDOW_MINUTES) * perMinute - BLOCK_GAP.toPx()
            if (right - left < 2f) continue
            drawBlock(p, left, right, selected = i == column)
        }
    }

    /** The channel cell: number, logo tile, name and feed line, on its own node. */
    fun DrawScope.drawChannel(row: SpikeRow, column: Int, fill: Color) = trace("Guide:Channel") {
        val w = CHANNEL_WIDTH.toPx()
        if (fill.alpha > 0f) drawRoundRect(fill, size = Size(w, size.height), cornerRadius = CornerRadius(CORNER.toPx()))
        val focused = column == CHANNEL
        val primary = if (focused) colors.ink else if (column >= 0) colors.textPrimary else colors.textMuted
        val secondary = if (focused) colors.inkSecondary else if (column >= 0) colors.textMuted else colors.textDim
        text(row.number, number, 20.dp.toPx(), Offset(4.dp.toPx(), 14.dp.toPx()), secondary)
        val logo = 30.dp.toPx()
        val logoX = 30.dp.toPx()
        drawRoundRect(if (focused) colors.ink.copy(alpha = 0.10f) else colors.surface, Offset(logoX, 7.dp.toPx()), Size(logo, logo), CornerRadius(CORNER.toPx()))
        text(row.initials, number, logo, Offset(logoX + 6.dp.toPx(), 15.dp.toPx()), if (focused) colors.ink else colors.textPrimary)
        val nameX = logoX + logo + 8.dp.toPx()
        val nameWidth = w - nameX - 8.dp.toPx()
        text(row.name, name, nameWidth, Offset(nameX, 4.dp.toPx()), primary)
        text(row.feed, caption, nameWidth, Offset(nameX, 23.dp.toPx()), secondary)
    }

    private fun DrawScope.drawBlock(p: SpikeProgramme, left: Float, right: Float, selected: Boolean) {
        val airing = p.startMinute <= SpikeData.NOW_MINUTE && p.endMinute > SpikeData.NOW_MINUTE
        val past = p.endMinute <= SpikeData.NOW_MINUTE && !selected
        val fill = when {
            selected -> colors.focusFill
            airing -> colors.surface
            past -> colors.pastSubtle
            else -> colors.subtle
        }
        val width = right - left
        drawRoundRect(fill, Offset(left, 0f), Size(width, size.height), CornerRadius(CORNER.toPx()))
        var textX = left + 8.dp.toPx()
        if (!selected) {
            val bar = colors.genres[p.genre].let { if (past) it.copy(alpha = 0.55f) else it }
            drawRoundRect(bar, Offset(left, (size.height - 32.dp.toPx()) / 2), Size(3.dp.toPx(), 32.dp.toPx()), CornerRadius(2.dp.toPx()))
            textX = left + 10.dp.toPx()
        }
        val textWidth = right - textX - 8.dp.toPx()
        if (textWidth > 0f) {
            val titleColor = if (selected) colors.ink else if (past) colors.pastText else colors.textPrimary
            val timeColor = when {
                selected -> colors.inkSecondary
                airing -> colors.focus
                past -> colors.textDim.copy(alpha = 0.55f)
                else -> colors.textDim
            }
            text(p.title, title, textWidth, Offset(textX, 4.dp.toPx()), titleColor)
            text(p.time, time, textWidth, Offset(textX, 22.dp.toPx()), timeColor)
        }
        if (airing) {
            val fraction = (SpikeData.NOW_MINUTE - p.startMinute).toFloat() / (p.endMinute - p.startMinute)
            drawRect(colors.focus, Offset(left, size.height - 3.dp.toPx()), Size(width * fraction, 3.dp.toPx()))
        }
    }

    /** Measured through the shared cache (keyed by text, style and width, never by colour). */
    private fun DrawScope.text(value: String, style: TextStyle, maxWidth: Float, at: Offset, color: Color) {
        if (maxWidth < 1f) return
        val layout = measurer.measure(
            text = value,
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth.toInt()),
        )
        drawText(layout, color, at)
    }
}
