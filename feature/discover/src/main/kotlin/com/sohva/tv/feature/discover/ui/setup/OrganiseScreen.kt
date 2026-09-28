package com.sohva.tv.feature.discover.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.store.CatalogEntry
import com.sohva.tv.feature.discover.ui.typeLabel
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsSwitchRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OrganiseMode { REORDER, VISIBILITY }

data class OrganiseState(
    val entries: List<CatalogEntry>? = null,
    val mode: OrganiseMode = OrganiseMode.REORDER,
    /** The picked-up catalog while moving, with the order before the move for Back (FR-55). */
    val moving: String? = null,
    val original: List<CatalogEntry> = emptyList(),
    val saving: Boolean = false,
    val orderFailed: Boolean = false,
    val visibilityFailed: Boolean = false,
    val loadFailed: Boolean = false,
)

/** The catalog organiser's model (spec 50 FR-53…56): moves are drafts; OK saves once, Back restores. */
class OrganiseModel(private val host: DiscoverHost, private val profile: String) : ViewModel() {
    private val _state = MutableStateFlow(OrganiseState())
    val state: StateFlow<OrganiseState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val entries = withContext(host.dispatchers.io) { host.catalogs.ordered(profile, host.manager.list(profile)) }
                _state.update { it.copy(entries = entries, loadFailed = false) }
            } catch (e: AddonException) {
                _state.update { it.copy(loadFailed = true, entries = emptyList()) }
            }
        }
    }

    fun mode(mode: OrganiseMode) = _state.update { if (it.moving != null) it else it.copy(mode = mode) }

    fun pickUp(key: String) = _state.update { it.copy(moving = key, original = it.entries.orEmpty(), orderFailed = false) }

    /** Moves the picked-up catalog by [delta] places, or to [to]; navigation never writes (FR-55). */
    fun move(delta: Int = 0, to: Int? = null) = _state.update { s ->
        val key = s.moving ?: return@update s
        val list = s.entries.orEmpty().toMutableList()
        val from = list.indexOfFirst { it.key == key }
        if (from < 0) return@update s
        val target = (to ?: (from + delta)).coerceIn(0, list.lastIndex)
        if (target == from) return@update s
        list.add(target, list.removeAt(from))
        s.copy(entries = list)
    }

    /** OK: saves the order once; a failed save keeps the draft (and the move) for another try. */
    fun place() {
        val s = _state.value
        if (s.moving == null || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ok = try {
                withContext(host.dispatchers.io) { host.catalogs.saveOrder(profile, host.manager.list(profile), s.entries.orEmpty().map { it.key }) }
                true
            } catch (e: AddonException) {
                false
            }
            _state.update { if (ok) it.copy(saving = false, moving = null, original = emptyList()) else it.copy(saving = false, orderFailed = true) }
            if (ok) host.noteSetupChanged()
        }
    }

    /** Back while moving: the original order, nothing written (FR-55). */
    fun cancelMove() = _state.update { it.copy(entries = it.original.ifEmpty { it.entries }, moving = null, original = emptyList(), orderFailed = false) }

    fun toggle(entry: CatalogEntry) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, visibilityFailed = false) }
        viewModelScope.launch {
            try {
                val entries = withContext(host.dispatchers.io) {
                    val installations = host.manager.list(profile)
                    host.catalogs.setHidden(profile, installations, entry.key, !entry.hidden)
                    host.catalogs.ordered(profile, installations)
                }
                _state.update { it.copy(entries = entries, saving = false) }
                host.noteSetupChanged()
            } catch (e: AddonException) {
                _state.update { it.copy(saving = false, visibilityFailed = true) }
            }
        }
    }
}

/**
 * The catalog organiser (spec 50 FR-55, -56, §5.8): Reorder picks a catalog up with OK, moves it
 * with Up/Down, Page Up/Down and Home/End (held keys repeat), places it with OK and cancels with
 * Back; Show / hide has one switch per catalog. The list scrolls only as the moving row needs.
 */
@Composable
fun OrganiseScreen(model: OrganiseModel, back: () -> Unit) {
    val s by model.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rows = remember { HashMap<String, FocusRequester>() }
    val backButton = remember { FocusRequester() }
    fun row(key: String) = rows.getOrPut(key) { FocusRequester() }
    BackHandler {
        val moved = s.moving
        if (moved != null) {
            model.cancelMove()
            scope.launch { row(moved).requestFocusWhenAttached() }
        } else {
            back()
        }
    }
    Column(Modifier.fillMaxSize().padding(28.dp).testTag("discover-organise")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.addon_ui_catalogs), style = Sohva.typography.display.copy(fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary)
            TvActionButton(stringResource(R.string.addon_back_catalogs), back, Modifier.focusRequester(backButton), TvIcons.Back, compact = true)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsOverline(stringResource(R.string.addon_ui_catalog_settings))
                TvListRow(stringResource(R.string.addon_ui_reorder), { model.mode(OrganiseMode.REORDER) }, Modifier.testTag("discover-organise-reorder"), TvIcons.Guide,
                    state = SurfaceState(selected = s.mode == OrganiseMode.REORDER), layout = ListRowLayout(dense = true))
                TvListRow(stringResource(R.string.addon_ui_show_hide), { model.mode(OrganiseMode.VISIBILITY) }, Modifier.testTag("discover-organise-visibility"), TvIcons.Channels,
                    state = SurfaceState(selected = s.mode == OrganiseMode.VISIBILITY), layout = ListRowLayout(dense = true))
                Text(
                    stringResource(if (s.mode == OrganiseMode.REORDER) R.string.addon_ui_continue_watching_stays_first_addon_priority_and_saved_progress_ar else R.string.addon_ui_hidden_catalogs_keep_their_place_in_the_order_you_can_show_them_ag),
                    Modifier.padding(top = 10.dp), style = Sohva.typography.label, color = Sohva.palette.textDim,
                )
            }
            Column(Modifier.weight(1f)) {
                val entries = s.entries
                when {
                    entries == null -> Text(stringResource(R.string.addon_ui_loading_catalog_settings), style = Sohva.typography.body, color = Sohva.palette.textMuted)
                    s.loadFailed -> Text(stringResource(R.string.addon_ui_unable_to_load_catalog_settings_reopen_this_page_to_try_again), style = Sohva.typography.body, color = Sohva.palette.textMuted)
                    entries.isEmpty() -> Text(stringResource(R.string.addon_ui_add_an_addon_to_organize_its_catalogs), style = Sohva.typography.body, color = Sohva.palette.textMuted)
                    else -> {
                        if (s.orderFailed) Text(stringResource(R.string.addon_ui_order_was_not_saved_place_again_to_retry_or_cancel_and_reopen_if_y), style = Sohva.typography.label, color = Sohva.palette.danger)
                        if (s.visibilityFailed) Text(stringResource(R.string.addon_ui_visibility_was_not_saved_try_again_or_reopen_this_page_if_your_add), style = Sohva.typography.label, color = Sohva.palette.danger)
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("discover-organise-list"), state = list) {
                            itemsIndexed(entries, key = { _, e -> e.key }) { i, e ->
                                Entry(model, s, e, i, Modifier.focusRequester(row(e.key)), visibleRows = { list.layoutInfo.visibleItemsInfo.size })
                            }
                        }
                        Text(
                            stringResource(if (s.moving != null) R.string.addon_ui_move_controls else R.string.addon_ui_select_a_catalog_to_pick_it_up_move_to_its_new_position_then_press),
                            Modifier.padding(top = 8.dp), style = Sohva.typography.label, color = Sohva.palette.textDim,
                        )
                    }
                }
            }
        }
    }
    // Focus follows the moving row, scrolling only when it would leave the view (FR-55).
    LaunchedEffect(s.moving, s.entries) {
        val key = s.moving ?: return@LaunchedEffect
        val index = s.entries.orEmpty().indexOfFirst { it.key == key }
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.isNotEmpty() && (index < visible.first().index || index > visible.last().index)) list.scrollToItem(if (index < visible.first().index) index else (index - visible.size + 1).coerceAtLeast(0))
        row(key).requestFocusWhenAttached()
    }
    LaunchedEffect(Unit) { backButton.requestFocusWhenAttached() }
}

@Composable
private fun Entry(model: OrganiseModel, s: OrganiseState, e: CatalogEntry, index: Int, modifier: Modifier, visibleRows: () -> Int) {
    val state = when {
        !e.installation.enabled -> stringResource(R.string.addon_ui_addon_disabled)
        e.hidden -> stringResource(R.string.addon_ui_hidden)
        e.catalog.onLanding -> stringResource(R.string.home_nav_home)
        else -> stringResource(R.string.addon_title)
    }
    val supporting = "${e.installation.name} · ${typeLabel(e.catalog.type)} · $state"
    if (s.mode == OrganiseMode.VISIBILITY) {
        SettingsSwitchRow(
            e.catalog.name, !e.hidden, { model.toggle(e) }, Modifier.testTag("discover-organise-switch-${e.catalog.id}"),
            subtitle = supporting, enabled = !s.saving, containerModifier = modifier.testTag("discover-organise-row-${e.catalog.id}"),
        )
        return
    }
    val moving = s.moving == e.key
    val keys = if (!moving) modifier else modifier.onPreviewKeyEvent { k ->
        if (k.type != KeyEventType.KeyDown) return@onPreviewKeyEvent k.key == Key.DirectionCenter || k.key == Key.Enter
        val page = (visibleRows() - 1).coerceAtLeast(1)
        when (k.key) {
            Key.DirectionUp -> model.move(-1)
            Key.DirectionDown -> model.move(1)
            Key.PageUp -> model.move(-page)
            Key.PageDown -> model.move(page)
            Key.MoveHome -> model.move(to = 0)
            Key.MoveEnd -> model.move(to = Int.MAX_VALUE)
            Key.DirectionLeft, Key.DirectionRight -> Unit
            // OK places on its first press only, never on a repeat.
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (k.nativeKeyEvent.repeatCount == 0) model.place()
            else -> return@onPreviewKeyEvent false
        }
        true
    }
    TvListRow(
        e.catalog.name, { if (s.moving == null) model.pickUp(e.key) },
        keys.testTag("discover-organise-row-${e.catalog.id}"),
        supporting = if (moving) stringResource(R.string.addon_ui_moving_position, index + 1) else supporting,
        trailing = "${index + 1}",
        state = SurfaceState(selected = moving),
        layout = ListRowLayout(divider = true),
    )
}
