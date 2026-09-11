package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.core.model.movedOrganizationIds
import com.streammate.tv.feature.common.*
import com.streammate.tv.feature.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class CatalogMove(val key: String, val order: List<String>)

@Composable
internal fun AddonCatalogOrderScreen(host: AddonHost, profile: String, installations: List<InstalledAddon>, onBack: () -> Unit, modifier: Modifier,
    loadOrder: suspend () -> List<String> = { host.catalogOrder.load(profile) },
    saveOrder: suspend (List<String>) -> Unit = { host.catalogOrder.save(profile, it) },
    loadHidden: suspend () -> Set<String> = { host.catalogVisibility.loadHidden(profile) },
    saveVisibility: suspend (String, Boolean) -> Set<String> = { key, shown -> host.catalogVisibility.setVisible(profile, key, shown) }) {
    val labels = addonStrings()
    var entries by remember { mutableStateOf<List<AddonCatalogEntry>>(emptyList()) }
    var hidden by remember { mutableStateOf<Set<String>>(emptySet()) }
    var move by remember { mutableStateOf<CatalogMove?>(null) }
    var mode by remember { mutableStateOf("order") }
    var restoreKey by remember { mutableStateOf<String?>(null) }
    var restoreVersion by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var consumeCenterUp by remember { mutableStateOf(false) }
    var consumeBackUp by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
    val backFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val byKey = remember(entries) { entries.associateBy { it.key } }
    val shownEntries = move?.order?.mapNotNull(byKey::get) ?: entries

    LaunchedEffect(Unit) {
        try {
            val order = loadOrder()
            val visibility = loadHidden()
            entries = AddonCatalogOrdering.ordered(installations, order)
            hidden = visibility; loaded = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = labels(R.string.addon_ui_unable_to_load_catalog_settings_reopen_this_page_to_try_again) }
        backFocus.requestFocusWhenAttached()
    }
    fun restore(key: String) { restoreKey = key; restoreVersion++ }
    LaunchedEffect(move, restoreVersion) {
        val target = move?.key ?: restoreKey ?: return@LaunchedEffect
        val index = shownEntries.indexOfFirst { it.key == target }
        if (index >= 0) {
            val visible = scroll.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            if (visible == null || visible.offset < scroll.layoutInfo.viewportStartOffset ||
                visible.offset + visible.size > scroll.layoutInfo.viewportEndOffset) scroll.scrollToItem(index)
            rowFocus.getOrPut(target) { FocusRequester() }.requestFocusWhenAttached()
        }
    }
    fun back() {
        if (busy) return
        val pending = move
        if (pending == null) onBack() else { move = null; failure = null; restore(pending.key) }
    }
    fun place() {
        val pending = move ?: return
        if (busy) return
        if (pending.order == entries.map { it.key }) { move = null; restore(pending.key); return }
        busy = true; failure = null
        scope.launch {
            try {
                saveOrder(pending.order)
                entries = pending.order.mapNotNull(byKey::get)
                move = null; restore(pending.key)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = labels(R.string.addon_ui_order_was_not_saved_place_again_to_retry_or_cancel_and_reopen_if_y) }
            finally { busy = false }
        }
    }
    fun setShown(entry: AddonCatalogEntry, shown: Boolean) {
        if (busy || move != null) return
        busy = true; failure = null
        scope.launch {
            try { hidden = saveVisibility(entry.key, shown) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = labels(R.string.addon_ui_visibility_was_not_saved_try_again_or_reopen_this_page_if_your_add) }
            finally { busy = false; restore(entry.key) }
        }
    }

    BackHandler(onBack = ::back)
    val movingKeys = listOf(Key.DirectionUp, Key.DirectionDown, Key.PageUp, Key.PageDown, Key.MoveHome, Key.MoveEnd)
    AddonSetupPage(labels(R.string.addon_ui_catalogs), ::back, if (move == null) labels(R.string.addon_back_catalogs) else labels(R.string.addon_ui_cancel_move), backFocus,
        "addon-catalog-settings", "addon-order-back", modifier.onPreviewKeyEvent { event ->
            val center = event.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
            if (event.type == KeyEventType.KeyUp) {
                when {
                    center && consumeCenterUp -> { consumeCenterUp = false; true }
                    event.key == Key.Back && consumeBackUp -> { consumeBackUp = false; true }
                    move != null -> event.key in movingKeys || center
                    else -> false
                }
            } else {
                val pending = move ?: return@onPreviewKeyEvent false
                when {
                    event.key == Key.Back -> { back(); consumeBackUp = true; true }
                    event.key in movingKeys -> {
                        if (!busy) {
                            val index = pending.order.indexOf(pending.key)
                            val page = (scroll.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(1)
                            val destination = when (event.key) {
                                Key.DirectionUp -> index - 1
                                Key.DirectionDown -> index + 1
                                Key.PageUp -> index - page
                                Key.PageDown -> index + page
                                Key.MoveHome -> 0
                                else -> pending.order.lastIndex
                            }
                            // Same draft move operation as Sohva's group editor. No disk writes
                            // on navigation/repeat events; only the second OK saves the order.
                            move = pending.copy(order = movedOrganizationIds(pending.order, pending.key, destination))
                        }
                        true
                    }
                    center -> {
                        if (event.nativeKeyEvent.repeatCount == 0) place()
                        consumeCenterUp = true; true
                    }
                    event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> true
                    else -> false
                }
            }
        }) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsOverline(labels(R.string.addon_ui_catalog_settings))
                TvListRow(labels(R.string.addon_ui_reorder), { mode = "order" }, selected = mode == "order", icon = TvIcons.Guide,
                    enabled = !busy && move == null, labelLines = 2, testTag = "addon-order-mode")
                TvListRow(labels(R.string.addon_ui_show_hide), { mode = "visibility" }, selected = mode == "visibility", icon = TvIcons.Channels,
                    enabled = !busy && move == null, labelLines = 2, testTag = "addon-visibility-mode")
                AddonSetupNote(labels(R.string.addon_ui_continue_watching_stays_first_addon_priority_and_saved_progress_ar), Modifier.padding(14.dp))
                if (move != null) AddonSetupNote(labels(R.string.addon_ui_hold_or_to_move_quickly_page_up_down_and_home_end_also_work_with_a), Modifier.padding(horizontal = 14.dp))
                else AddonSetupNote(labels(R.string.addon_ui_hidden_catalogs_keep_their_place_in_the_order_you_can_show_them_ag), Modifier.padding(horizontal = 14.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (!loaded && failure == null) AddonSetupNote(labels(R.string.addon_ui_loading_catalog_settings), Modifier.padding(14.dp))
                failure?.let { Text(it, Modifier.padding(14.dp).testTag("addon-order-error"), color = StreamMateThemeTokens.palette.danger,
                    fontSize = StreamMateThemeTokens.typography.label.fontSize) }
                if (loaded && entries.isEmpty()) SettingsRow(labels(R.string.addon_ui_no_catalogs_installed), subtitle = labels(R.string.addon_ui_add_an_addon_to_organize_its_catalogs), icon = TvIcons.Info)
                LazyColumn(state = scroll, modifier = Modifier.weight(1f).focusGroup().testTag("addon-catalog-order-list"),
                    contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(shownEntries, key = { _, it -> it.key }) { index, entry ->
                        val focus = remember(entry.key) { rowFocus.getOrPut(entry.key) { FocusRequester() } }
                        val shown = entry.key !in hidden
                        val description = "${entry.installation.manifest.name} · ${labels.mediaType(entry.catalog.type)} · ${if (!entry.installation.enabled) labels(R.string.addon_ui_addon_disabled) else if (!shown) labels(R.string.addon_ui_hidden) else if (entry.catalog.belongsOnLanding()) labels(R.string.home_nav_home) else labels(R.string.addon_title)}"
                        if (mode == "order") {
                            TvListRow(entry.catalog.name, {
                                if (!busy) {
                                    if (move == null) { failure = null; move = CatalogMove(entry.key, entries.map { it.key }) }
                                    else place()
                                }
                            }, supporting = description, trailing = if (move?.key == entry.key) labels(R.string.addon_ui_moving_position, index + 1) else "${index + 1}",
                                selected = move?.key == entry.key, icon = TvIcons.Guide, divider = true,
                                focusRequester = focus, testTag = "addon-order-row-${entry.key}")
                        } else {
                            SettingsRow(entry.catalog.name, subtitle = description, icon = TvIcons.Channels) {
                                SettingsSwitch(shown, { setShown(entry, it) }, enabled = !busy,
                                    modifier = Modifier.semantics { contentDescription = labels(R.string.addon_ui_show_named, entry.catalog.name) },
                                    focusRequester = focus, testTag = "addon-visibility-${entry.key}")
                            }
                        }
                    }
                }
                AddonSetupNote(when {
                    busy -> labels(com.streammate.tv.iptv.R.string.manager_saving)
                    move != null -> labels(R.string.addon_ui_move_controls)
                    mode == "visibility" -> labels(R.string.addon_ui_hide_from_home_and_discover_without_disabling_the_addon_s_sources)
                    else -> labels(R.string.addon_ui_select_a_catalog_to_pick_it_up_move_to_its_new_position_then_press)
                }, Modifier.fillMaxWidth().padding(top = 12.dp).testTag(if (move == null) "addon-order-help" else "addon-order-moving"))
            }
        }
    }
}
