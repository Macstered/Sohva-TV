package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.requestFocusWhenAttached

@Composable
internal fun AddonFilterDiscoverScreen(host: AddonHost, profile: String, installations: List<InstalledAddon>, onBack: () -> Unit, modifier: Modifier, order: List<String> = emptyList(), hidden: Set<String> = emptySet()) {
    val labels = addonStrings()
    val catalogs = remember(installations, order, hidden) { AddonCatalogOrdering.visible(installations, order, hidden).map { it.installation to it.catalog } }
    var selected by remember { mutableStateOf(catalogs.firstOrNull()) }
    BackHandler(onBack = onBack)
    val current = selected?.takeIf { it in catalogs } ?: catalogs.firstOrNull()
    if (current == null) { Column(modifier.padding(32.dp)) { Text(labels(R.string.addon_ui_no_visible_catalogs_show_catalogs_in_addons_setup)); TvActionButton(labels(R.string.action_back), onBack) }; return }
    val types = catalogs.map { it.second.type }.distinct()
    val sameType = catalogs.filter { it.second.type == current.second.type }
    key(current.first.installationId, current.second.type, current.second.id) {
        AddonCatalogScreen(host, profile, current.first, current.second, onBack, modifier, discovery = true, chooser = {
            AddonChoice(labels(R.string.addon_ui_type), current.second.type, types.map { it to labels.mediaType(it) }, { type -> selected = catalogs.first { it.second.type == type } }, Modifier.width(160.dp), tag = "Type")
            AddonChoice(labels(R.string.addon_ui_catalog), sameType.indexOf(current).toString(), sameType.mapIndexed { index, pair -> index.toString() to pair.second.name },
                { index -> selected = sameType[index.toInt()] }, Modifier.width(240.dp), tag = "Catalog")
        })
    }
}

@Composable
internal fun AddonChoice(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, tag: String = label) {
    var open by remember { mutableStateOf(false) }
    TvActionButton("$label: ${options.firstOrNull { it.first == selected }?.second ?: selected}", { open = true },
        modifier, compact = true, testTag = "addon-choice-$tag")
    if (open) Dialog({ open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        val scroll = androidx.compose.foundation.lazy.rememberLazyListState(index)
        LaunchedEffect(Unit) { focus.requestFocusWhenAttached() }
        Column(Modifier.width(480.dp).heightIn(max = 430.dp).background(StreamMateThemeTokens.palette.panel).padding(24.dp)) {
            Text(label, fontSize = StreamMateThemeTokens.typography.headline.fontSize)
            LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("addon-choice-options")) {
                itemsIndexed(options) { optionIndex, option ->
                    TvActionButton(option.second, { open = false; onSelect(option.first) }, Modifier.fillMaxWidth(),
                        selected = option.first == selected, focusRequester = if (optionIndex == index) focus else null)
                }
            }
        }
    }
}
