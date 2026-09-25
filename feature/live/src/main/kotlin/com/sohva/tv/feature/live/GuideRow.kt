package com.sohva.tv.feature.live

import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import androidx.compose.animation.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.core.model.guide.GuideRules
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.motion.Motion

internal const val NONE = -2

/** The logo inside its 30 dp tile with 3 dp padding (spec 20 §5.1). */
private val LOGO_BOX = 24.dp

/**
 * One row's selected column and focus, read only in its draw phase and key handler: a press
 * changes two rows' [RowState] and those two rows redraw; nothing recomposes (GUIDE-NFR-02).
 * Column −1 is the channel column, ≥ 0 an index into the row's schedule (0 = the filler when empty).
 */
@Stable
internal class RowState {
    var column by mutableIntStateOf(NONE)
    var focused by mutableStateOf(false)
}

/** A composed row, for focus hand-offs by index (dial, paging, return from the player). */
internal class RowHandle(val requester: FocusRequester, val state: RowState)

/**
 * What the grid remembers between rows, as plain fields (never snapshot state): whether focus
 * travels on the channel column, and the drawn span of the last focused block, in milliseconds
 * from the window start, so Up/Down keep the time (GUIDE-FR-72).
 */
internal class GridMemory {
    var channelMode: Boolean = true
    var span: GuideRules.Span? = null
    var focusedState: RowState? = null
    var focusedIndex: Int = -1
}

/** What a row does on OK and Left (GUIDE-FR-71, -74, -75). */
internal interface RowActions {
    fun play(row: GuideRowData)

    /** The programme from the provider's archive (spec 22 CATCH-02…04). */
    fun playArchive(row: GuideRowData, programme: GuideProgramme)

    fun openActions(row: GuideRowData, column: Int)

    fun openRail()

    /** Up from the top row: the hero's Watch (GUIDE-FR-73, spec 20 §11). */
    fun toHero()
}

@Composable
internal fun GuideRow(
    index: Int,
    row: GuideRowData?,
    model: GuideModel,
    painter: RowPainter,
    memory: GridMemory,
    handles: MutableMap<Int, RowHandle>,
    actions: RowActions,
) {
    val state = remember { RowState() }
    val requester = remember { FocusRequester() }
    DisposableEffect(index) {
        val handle = RowHandle(requester, state)
        handles[index] = handle
        onDispose { if (handles[index] === handle) handles.remove(index) }
    }
    // Only the channel cell's fill animates, and only in the draw phase (GUIDE-NFR-04).
    val channelFill = remember { Animatable(Color.Transparent) }
    val spec = Motion.focus<Color>()
    val colors = painter.colors
    LaunchedEffect(state) {
        snapshotFlow { state.focused to state.column }.collect { (focused, column) ->
            val target = when {
                focused && column == GuideModel.CHANNEL -> colors.focusFill
                column >= 0 -> colors.rowSelected
                else -> Color.Transparent
            }
            channelFill.animateTo(target, spec)
        }
    }
    val base = Modifier.fillMaxWidth().height(ROW_HEIGHT).testTag("guide-row-$index")
    if (row == null) {
        // A row whose page is not read yet: a blank bar, not focusable (focus never lands on a stand-in).
        Box(base) {
            Spacer(
                Modifier.fillMaxSize().padding(start = CHANNEL_WIDTH + TIMELINE_GAP)
                    .drawBehind { with(painter) { drawTimeline(null, state, 0L, 0L) } },
            )
        }
        return
    }
    val longPress = remember { LongOk() }
    // The logo is decoded at its drawn size and read in the draw phase: its arrival redraws the cell only.
    val loader = LocalArtwork.current
    val logoPx = with(LocalDensity.current) { LOGO_BOX.roundToPx() }
    val logo = remember(row.channel.logoUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(row.channel.logoUrl) {
        row.channel.logoUrl?.takeIf { it.isNotBlank() }?.let { logo.value = loader.load(it, logoPx, logoPx) }
    }
    Box(
        base
            .focusRequester(requester)
            .focusProperties { canFocus = true }
            .onFocusChanged { focus -> onFocus(focus.isFocused, index, row, state, model, memory) }
            .onPreviewKeyEvent { handleKey(it, index, row, state, model, memory, actions, longPress) }
            .focusable()
            .clearAndSetSemantics { contentDescription = describe(row, state, model) },
    ) {
        // Two render nodes: the fade redraws only the cell; scrolling moves both without re-recording.
        Spacer(
            Modifier.width(CHANNEL_WIDTH).fillMaxHeight().graphicsLayer()
                .drawBehind { with(painter) { drawChannel(row, state, channelFill.value, logo.value) } },
        )
        Spacer(
            Modifier.fillMaxSize().padding(start = CHANNEL_WIDTH + TIMELINE_GAP).graphicsLayer()
                .drawBehind {
                    with(painter) {
                        drawTimeline(model.programmes.drawnSchedule(row.channel.epgId), state, model.windowStartState.longValue, model.nowState.longValue)
                    }
                },
        )
    }
}

private fun describe(row: GuideRowData, state: RowState, model: GuideModel): String {
    val schedule = model.programmes.schedule(row.channel.epgId)
    val programme = state.column.takeIf { it >= 0 }?.let { schedule?.programmes?.getOrNull(it) }
    val time = state.column.takeIf { it >= 0 }?.let { schedule?.times?.getOrNull(it) }
    return listOfNotNull(row.number, row.name, programme?.title, time).joinToString(" ")
}

private fun onFocus(focused: Boolean, index: Int, row: GuideRowData, state: RowState, model: GuideModel, memory: GridMemory) {
    if (!focused) {
        state.focused = false
        return
    }
    if (memory.focusedState !== state) {
        memory.focusedState?.column = NONE
        if (memory.focusedIndex != index) model.cancelPending()
    }
    memory.focusedState = state
    memory.focusedIndex = index
    state.focused = true
    model.overlays.closeRail()
    if (state.column == NONE) state.column = entryColumn(row, model, memory)
    select(row, state.column, model)
}

/** The column a row takes when focus arrives by Up/Down (GUIDE-FR-72). */
private fun entryColumn(row: GuideRowData, model: GuideModel, memory: GridMemory): Int {
    if (memory.channelMode) return GuideModel.CHANNEL
    val schedule = model.programmes.schedule(row.channel.epgId) ?: return GuideModel.CHANNEL
    val blocks = model.drawnBlocks(schedule.programmes)
    if (blocks.isEmpty()) return 0
    val from = memory.span ?: return blocks.first()
    val start = model.windowStartState.longValue
    val spans = blocks.map { spanOf(schedule, it, start) }
    val hit = GuideRules.verticalTarget(from, spans)
    return if (hit < 0) blocks.first() else blocks[hit]
}

private fun spanOf(schedule: RowSchedule, i: Int, windowStart: Long): GuideRules.Span {
    val p = schedule.programmes[i]
    val end = minOf(p.stop, windowStart + com.sohva.tv.core.model.guide.GuideWindow.LENGTH_MS, schedule.programmes.getOrNull(i + 1)?.start ?: Long.MAX_VALUE)
    return GuideRules.Span((maxOf(p.start, windowStart) - windowStart).toFloat(), (end - windowStart).toFloat())
}

private fun select(row: GuideRowData, column: Int, model: GuideModel) {
    if (column < 0) {
        model.selectChannel(row)
    } else {
        model.select(row, model.programmes.schedule(row.channel.epgId)?.programmes?.getOrNull(column))
    }
}

private fun setColumn(column: Int, row: GuideRowData, state: RowState, model: GuideModel, memory: GridMemory) {
    state.column = column
    memory.channelMode = column == GuideModel.CHANNEL
    if (column >= 0) {
        val schedule = model.programmes.schedule(row.channel.epgId)
        memory.span = if (schedule != null && column < schedule.programmes.size) spanOf(schedule, column, model.windowStartState.longValue) else null
    }
    select(row, column, model)
}

/** Keys inside a row (GUIDE-FR-70..75); Up/Down fall through to focus search, the next row. */
private fun handleKey(
    event: KeyEvent,
    index: Int,
    row: GuideRowData,
    state: RowState,
    model: GuideModel,
    memory: GridMemory,
    actions: RowActions,
    longPress: LongOk,
): Boolean {
    val code = event.key.nativeKeyCode
    if (code == KEYCODE_DPAD_CENTER || code == KEYCODE_ENTER || code == KEYCODE_NUMPAD_ENTER) {
        return longPress.handle(event, onHold = {
            val applies = state.column >= 0 && hasBlocks(row, model)
            if (applies) actions.openActions(row, state.column)
            applies
        }, onPress = { press(row, state, model, actions) })
    }
    if (event.type != KeyEventType.KeyDown) {
        // The release of a key held during a page belongs to the parked row.
        return model.pendingPage != 0 && (event.key == Key.DirectionRight || event.key == Key.DirectionLeft)
    }
    val schedule = model.programmes.schedule(row.channel.epgId)
    return when (event.key) {
        Key.DirectionRight -> {
            if (model.pendingPage > 0) return true
            val column = state.column
            if (schedule == null) return true
            val blocks = model.drawnBlocks(schedule.programmes)
            when {
                column == GuideModel.CHANNEL -> setColumn(if (blocks.isEmpty()) 0 else blocks.first(), row, state, model, memory)
                blocks.isEmpty() || column >= blocks.last() -> {
                    if (model.page(+1, index)) setColumn(GuideModel.CHANNEL, row, state, model, memory)
                }
                else -> setColumn(blocks.first { it > column }, row, state, model, memory)
            }
            true
        }
        Key.DirectionLeft -> {
            if (model.pendingPage < 0) return true
            val column = state.column
            if (column == GuideModel.CHANNEL) {
                model.cancelPending()
                actions.openRail()
                return true
            }
            val blocks = schedule?.let { model.drawnBlocks(it.programmes) }.orEmpty()
            when {
                blocks.isNotEmpty() && column > blocks.first() -> setColumn(blocks.last { it < column }, row, state, model, memory)
                !model.isAtNow() -> {
                    if (model.page(-1, index)) setColumn(GuideModel.CHANNEL, row, state, model, memory)
                }
                else -> setColumn(GuideModel.CHANNEL, row, state, model, memory)
            }
            true
        }
        Key.DirectionDown -> index >= (model.list.value?.size ?: 0) - 1
        Key.DirectionUp -> {
            if (index != 0) return false
            actions.toHero()
            true
        }
        else -> false
    }
}

private fun hasBlocks(row: GuideRowData, model: GuideModel): Boolean =
    model.programmes.schedule(row.channel.epgId)?.let { model.drawnBlocks(it.programmes).isNotEmpty() } == true

/**
 * OK (GUIDE-FR-74): the channel column or the filler play live; a future block opens its actions;
 * an airing or past block plays from its start when the archive has it (so OK restarts the airing
 * programme of a catch-up channel, as beta 23 did), otherwise live.
 */
private fun press(row: GuideRowData, state: RowState, model: GuideModel, actions: RowActions) {
    val column = state.column
    val programme = column.takeIf { it >= 0 }?.let { model.programmes.schedule(row.channel.epgId)?.programmes?.getOrNull(it) }
    when {
        programme == null -> actions.play(row)
        programme.isFuture(model.nowState.longValue) -> actions.openActions(row, column)
        row.offersArchive(programme, model.nowState.longValue) -> actions.playArchive(row, programme)
        else -> actions.play(row)
    }
}

/** Long OK on a block opens its actions once; the release after it is swallowed (GUIDE-FR-75). */
internal class LongOk {
    private var held = false

    /** [onHold] returns whether a hold applies here; if not, the release still counts as a press. */
    fun handle(event: KeyEvent, onHold: () -> Boolean, onPress: () -> Unit): Boolean = when (event.type) {
        KeyEventType.KeyDown -> {
            if (event.nativeKeyEvent.repeatCount == 1 && !held) held = onHold()
            true
        }
        KeyEventType.KeyUp -> {
            if (held) held = false else onPress()
            true
        }
        else -> false
    }
}
