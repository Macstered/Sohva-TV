package com.sohva.tv.feature.live

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tracing.trace
import com.sohva.tv.core.model.guide.Genre
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.core.model.guide.GuideWindow
import com.sohva.tv.ui.design.theme.ContentColors
import com.sohva.tv.ui.design.theme.SohvaPalette
import com.sohva.tv.ui.design.theme.SohvaTypography

internal val ROW_HEIGHT = 44.dp
internal val ROW_GAP = 4.dp
internal val CHANNEL_WIDTH = 232.dp
internal val TIMELINE_GAP = 6.dp
private val BLOCK_GAP = 4.dp
private val CORNER = 8.dp

/** Colours of the grid, resolved once per theme; "past" alpha is multiplied in, no layers (spec 20 §5.1). */
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
    val focus: Color,
    val logoFocused: Color,
) {
    fun genre(genre: Genre): Color = when (genre) {
        Genre.SPORT -> ContentColors.genreSport
        Genre.NEWS -> ContentColors.genreNews
        Genre.CHILDREN -> ContentColors.genreChildren
        Genre.FILM -> ContentColors.genreFilm
    }

    companion object {
        fun from(p: SohvaPalette) = GridColors(
            focusFill = p.textPrimary, ink = p.background, inkSecondary = p.background.copy(alpha = 0.62f),
            rowSelected = p.surfaceFocused, textPrimary = p.textPrimary, textMuted = p.textMuted, textDim = p.textDim,
            surface = p.surface, subtle = p.surfaceSubtle, focus = p.focus, logoFocused = p.background.copy(alpha = 0.10f),
        )
    }
}

/** Texts the filler shows (GUIDE-FR-57), resolved from resources once per composition. */
@Immutable
internal data class FillerTexts(val noEpg: String, val watch: String)

/**
 * Draws one guide row straight onto its canvas (spec 20 §5.1): the channel cell on one node, the
 * blocks on another. Text goes through the shared cached measurer (keyed by text, style and width,
 * never colour), so a press re-measures nothing (GUIDE-NFR-03).
 */
internal class RowPainter(private val measurer: TextMeasurer, val colors: GridColors, private val filler: FillerTexts) {
    private val caption = SohvaTypography.Default.caption
    private val title = caption.copy(fontWeight = FontWeight.Bold, lineHeight = 15.sp)
    private val time = caption.copy(lineHeight = 14.sp)
    private val name = SohvaTypography.Default.label.copy(fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
    private val nameSmall = name.copy(fontSize = 13.sp, lineHeight = 15.sp)
    private val number = caption.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    private val initials = caption.copy(fontWeight = FontWeight.Black)

    /** The channel cell (guide.md §2 table): [focused] = this row has focus on the channel column. */
    fun DrawScope.drawChannel(row: GuideRowData, state: RowState, fill: Color, logo: ImageBitmap?) = trace("Guide:RowDraw") {
        val w = size.width
        if (fill.alpha > 0f) drawRoundRect(fill, size = Size(w, size.height), cornerRadius = CornerRadius(CORNER.toPx()))
        val focused = state.focused && state.column == GuideModel.CHANNEL
        val onBlock = state.column >= 0
        val primary = if (focused) colors.ink else if (onBlock) colors.textPrimary else colors.textMuted
        val secondary = if (focused) colors.inkSecondary else if (onBlock) colors.textMuted else colors.textDim
        var x = 4.dp.toPx()
        if (row.number != null) {
            text(row.number, number, 20.dp.toPx(), Offset(x, 14.dp.toPx()), secondary, minWidth = true)
            x += 20.dp.toPx() + 6.dp.toPx()
        }
        val tile = 30.dp.toPx()
        val tileY = (size.height - tile) / 2
        drawRoundRect(if (focused) colors.logoFocused else colors.surface, Offset(x, tileY), Size(tile, tile), CornerRadius(CORNER.toPx()))
        if (logo != null) {
            val pad = 3.dp.toPx()
            val box = tile - 2 * pad
            val scale = minOf(box / logo.width, box / logo.height)
            val dw = (logo.width * scale).toInt()
            val dh = (logo.height * scale).toInt()
            drawImage(
                logo,
                dstOffset = IntOffset((x + pad + (box - dw) / 2).toInt(), (tileY + pad + (box - dh) / 2).toInt()),
                dstSize = IntSize(dw, dh),
            )
        } else {
            val layout = measurer.measure(row.initials, initials, maxLines = 1, constraints = Constraints(maxWidth = tile.toInt()))
            drawText(layout, if (focused) colors.ink else colors.textPrimary, Offset(x + (tile - layout.size.width) / 2, tileY + (tile - layout.size.height) / 2))
        }
        x += tile + 8.dp.toPx()
        val nameWidth = w - x - 8.dp.toPx()
        if (nameWidth < 1f) return@trace
        val oneLine = measurer.measure(row.name, name, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = nameWidth.toInt()))
        if (!oneLine.hasVisualOverflow) {
            drawText(oneLine, primary, Offset(x, 4.dp.toPx()))
            text(row.feed, caption.copy(lineHeight = 14.sp), nameWidth, Offset(x, 23.dp.toPx()), secondary)
        } else {
            // Long names take two smaller lines and drop the feed line (guide.md §2).
            val two = measurer.measure(row.name, nameSmall, overflow = TextOverflow.Ellipsis, maxLines = 2, constraints = Constraints(maxWidth = nameWidth.toInt()))
            drawText(two, primary, Offset(x, (size.height - two.size.height) / 2))
        }
    }

    /** The blocks of one row; x 0 = the window start. */
    fun DrawScope.drawTimeline(schedule: RowSchedule?, state: RowState, windowStart: Long, now: Long) = trace("Guide:Row") {
        val end = windowStart + GuideWindow.LENGTH_MS
        val perMs = size.width / GuideWindow.LENGTH_MS
        if (schedule == null) {
            // Not read yet: a blank bar (GUIDE-FR-57).
            drawRoundRect(colors.subtle, size = Size(size.width - BLOCK_GAP.toPx(), size.height), cornerRadius = CornerRadius(CORNER.toPx()))
            return@trace
        }
        val programmes = schedule.programmes
        var drawn = 0
        for (i in programmes.indices) {
            val p = programmes[i]
            val clipEnd = minOf(p.stop, end, programmes.getOrNull(i + 1)?.start ?: Long.MAX_VALUE)
            val clipStart = maxOf(p.start, windowStart)
            if (p.stop <= windowStart || p.start >= end || clipEnd <= clipStart) continue
            val left = (clipStart - windowStart) * perMs
            val right = (clipEnd - windowStart) * perMs - BLOCK_GAP.toPx()
            if (right - left < 1f) continue
            drawn++
            drawBlock(p, schedule.times[i], left, right, i == state.column, now, windowStart, perMs)
        }
        if (drawn == 0) drawFiller(state.column == 0)
    }

    private fun DrawScope.drawFiller(selected: Boolean) {
        val right = size.width - BLOCK_GAP.toPx()
        drawRoundRect(if (selected) colors.focusFill else colors.subtle, size = Size(right, size.height), cornerRadius = CornerRadius(CORNER.toPx()))
        val width = right - 16.dp.toPx()
        text(filler.noEpg, title, width, Offset(8.dp.toPx(), 4.dp.toPx()), if (selected) colors.ink else colors.textPrimary)
        text(filler.watch, time, width, Offset(8.dp.toPx(), 22.dp.toPx()), if (selected) colors.inkSecondary else colors.textDim)
    }

    private fun DrawScope.drawBlock(
        p: GuideProgramme,
        timeLabel: String,
        left: Float,
        right: Float,
        selected: Boolean,
        now: Long,
        windowStart: Long,
        perMs: Float,
    ) {
        val airing = p.isLive(now)
        val past = p.isPast(now) && !selected
        val fade = if (past) PAST_ALPHA else 1f
        val fill = when {
            selected -> colors.focusFill
            airing -> colors.surface
            else -> colors.subtle
        }
        val width = right - left
        drawRoundRect(fill.fade(fade), Offset(left, 0f), Size(width, size.height), CornerRadius(CORNER.toPx()))
        var textX = left + 8.dp.toPx()
        val genre = p.genre
        if (!selected && genre != null) {
            val barHeight = 32.dp.toPx()
            drawRoundRect(colors.genre(genre).fade(fade), Offset(left, (size.height - barHeight) / 2), Size(3.dp.toPx(), barHeight), CornerRadius(2.dp.toPx()))
            textX = left + 10.dp.toPx()
        }
        val textWidth = right - textX - 8.dp.toPx()
        if (textWidth > 0f) {
            val titleColor = if (selected) colors.ink else colors.textPrimary.fade(fade)
            val timeColor = when {
                selected -> colors.inkSecondary
                airing -> colors.focus
                else -> colors.textDim.fade(fade)
            }
            text(p.title, title, textWidth, Offset(textX, 4.dp.toPx()), titleColor)
            text(timeLabel, time, textWidth, Offset(textX, 22.dp.toPx()), timeColor)
        }
        if (airing) {
            // A block clipped at the window's start ends its strip at the now-line (guide.md §12 item 2).
            val nowX = ((now - windowStart) * perMs).coerceIn(left, right)
            drawRect(colors.focus, Offset(left, size.height - 3.dp.toPx()), Size(nowX - left, 3.dp.toPx()))
        }
    }

    private fun DrawScope.text(value: String, style: TextStyle, maxWidth: Float, at: Offset, color: Color, minWidth: Boolean = false) {
        if (maxWidth < 1f || value.isEmpty()) return
        val w = maxWidth.toInt()
        val layout = measurer.measure(
            text = value,
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = if (minWidth) Constraints(minWidth = w, maxWidth = w) else Constraints(maxWidth = w),
        )
        drawText(layout, color, at)
    }

    private fun Color.fade(alpha: Float): Color = if (alpha >= 1f) this else copy(alpha = this.alpha * alpha)

    private companion object {
        const val PAST_ALPHA = 0.55f
    }
}
