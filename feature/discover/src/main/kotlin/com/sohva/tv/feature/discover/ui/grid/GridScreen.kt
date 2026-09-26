package com.sohva.tv.feature.discover.ui.grid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.feature.discover.protocol.CatalogExtra
import com.sohva.tv.feature.discover.protocol.MetaPreview
import com.sohva.tv.feature.discover.ui.components.ChooserDialog
import com.sohva.tv.feature.discover.ui.components.PosterCard
import com.sohva.tv.feature.discover.ui.components.PosterContent
import com.sohva.tv.feature.discover.ui.failureText
import com.sohva.tv.feature.discover.ui.typeLabel
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.PickerRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** A chooser that is open: its title, labels, the selected index and what OK does. */
private class OpenChooser(val title: String, val options: List<String>, val selected: Int, val choose: (Int) -> Unit)

/**
 * A catalog's Show all grid, and the Discover filter page (spec 50 §4.17, §5.9): heading, choosers,
 * buttons, status lines, then an adaptive grid of captioned posters that pages by itself as its
 * end comes into view.
 */
@Composable
fun GridScreen(model: GridModel, backLabel: Int, back: () -> Unit, open: (owner: String, item: MetaPreview) -> Unit) {
    val s by model.state.collectAsStateWithLifecycle()
    val catalogs by model.catalogs.collectAsStateWithLifecycle()
    var chooser by remember { mutableStateOf<OpenChooser?>(null) }
    val backButton = remember { FocusRequester() }
    val firstCard = remember { FocusRequester() }
    // Back first closes an open form over loaded titles (§3 table).
    BackHandler { if (s.formOpen && s.titles.isNotEmpty()) model.toggleForm() else back() }
    Column(Modifier.fillMaxSize().padding(28.dp).testTag(if (model.filterPage) "discover-filter" else "discover-grid"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (model.filterPage) stringResource(R.string.addon_title) else s.catalog?.name.orEmpty(),
            style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary, maxLines = 1,
        )
        if (model.filterPage && catalogs?.isEmpty() == true) {
            Text(stringResource(R.string.addon_ui_no_visible_catalogs_show_catalogs_in_addons_setup), style = Sohva.typography.body, color = Sohva.palette.textMuted)
            TvActionButton(stringResource(backLabel), back, Modifier.focusRequester(backButton), TvIcons.Back, compact = true)
            LaunchedEffect(Unit) { backButton.requestFocusWhenAttached() }
            return@Column
        }
        Choosers(model, catalogs) { chooser = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvActionButton(stringResource(backLabel), back, Modifier.focusRequester(backButton).testTag("discover-grid-back"), TvIcons.Back, compact = true)
            if (model.filterPage && s.textExtras.isNotEmpty()) {
                TvActionButton(stringResource(R.string.addon_filters), model::toggleForm, Modifier.testTag("discover-grid-filters"), compact = true, state = SurfaceState(selected = s.formOpen))
            }
            TvActionButton(
                stringResource(R.string.addon_refresh_catalog), model::refresh, Modifier.testTag("discover-grid-refresh"), TvIcons.Refresh,
                compact = true, state = SurfaceState(enabled = !s.formOpen),
            )
        }
        if (s.formOpen) TextForm(s.textExtras, s.values, model::applyText)
        statusLine(s)?.let { Text(it, Modifier.testTag("discover-grid-status"), style = Sohva.typography.label, color = Sohva.palette.textMuted) }
        Titles(model, s, firstCard, open)
    }
    chooser?.let { c -> ChooserDialog(c.title, c.options, c.selected, c.choose, { chooser = null }) }
    LaunchedEffect(Unit) { backButton.requestFocusWhenAttached() }
}

@Composable
private fun statusLine(s: GridState): String? = when {
    s.needsChoice -> stringResource(R.string.addon_ui_choose_the_required_filters_above_to_load_titles)
    s.failure != null && s.titles.isEmpty() -> failureText(s.failure)
    s.stale -> stringResource(R.string.addon_cached)
    !s.loading && s.failure == null && s.ended && s.titles.isEmpty() -> stringResource(R.string.addon_no_results)
    else -> null
}

/** Type and Catalog on the filter page (FR-120), then the catalog's option choosers (FR-118). */
@Composable
private fun Choosers(model: GridModel, catalogs: List<com.sohva.tv.feature.discover.store.CatalogEntry>?, show: (OpenChooser) -> Unit) {
    val s by model.state.collectAsStateWithLifecycle()
    val catalog = s.catalog ?: return
    // On a catalog's own grid the choosers appear only when a choice is required (FR-118).
    if (!model.filterPage && catalog.requiredChoices.isEmpty()) return
    val typeTitle = stringResource(R.string.addon_ui_type)
    val catalogTitle = stringResource(R.string.addon_ui_catalog)
    val select = stringResource(R.string.addon_ui_select)
    val default = stringResource(R.string.addon_ui_default)
    val types = catalogs.orEmpty().map { it.catalog.type }.distinct()
    val typeLabels = types.map { typeLabel(it) }
    val ofType = catalogs.orEmpty().filter { it.catalog.type == catalog.type }
    val names = mapOf("genre" to stringResource(R.string.addon_ui_genre), "year" to stringResource(R.string.addon_ui_year), "search" to stringResource(R.string.home_search))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (model.filterPage) {
            item {
                Chooser(typeTitle, typeLabel(catalog.type), 160.dp, "discover-choose-type") {
                    show(OpenChooser(typeTitle, typeLabels, types.indexOf(catalog.type)) { i -> catalogs.orEmpty().firstOrNull { it.catalog.type == types[i] }?.let(model::choose) })
                }
            }
            item {
                Chooser(catalogTitle, catalog.name, 240.dp, "discover-choose-catalog") {
                    show(OpenChooser(catalogTitle, ofType.map { it.catalog.name }, ofType.indexOfFirst { it.key == s.entry?.key }) { i -> model.choose(ofType[i]) })
                }
            }
        }
        items(s.choices, key = { it.name }) { extra: CatalogExtra ->
            val title = names[extra.name] ?: extra.name.replaceFirstChar { it.uppercase() }
            val none = if (extra.required) select else default
            val value = s.values[extra.name]
            Chooser(title, value ?: none, 230.dp, "discover-choose-${extra.name}") {
                val options = listOf(none) + extra.options
                show(OpenChooser(title, options, value?.let { extra.options.indexOf(it) + 1 } ?: 0) { i -> model.setValue(extra.name, if (i == 0) null else extra.options[i - 1]) })
            }
        }
    }
}

@Composable
private fun Chooser(title: String, value: String, width: Dp, tag: String, onClick: () -> Unit) {
    PickerRow(title, onClick, Modifier.width(width).testTag(tag), description = value)
}

/** FR-119: one plain text field per free-text extra, edited on OK, " *" after required names, then Apply. */
@Composable
private fun TextForm(extras: List<CatalogExtra>, values: Map<String, String>, apply: (Map<String, String>) -> Unit) {
    val draft = remember(extras) { mutableStateMapOf<String, String>().apply { extras.forEach { e -> put(e.name, values[e.name].orEmpty()) } } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("discover-grid-form")) {
        extras.forEach { e ->
            val label = e.name.replaceFirstChar { it.uppercase() } + if (e.required) " *" else ""
            TvUrlField(draft[e.name].orEmpty(), { draft[e.name] = it.take(1_024) }, label, Modifier.fillMaxWidth().testTag("discover-grid-field-${e.name}"), input = FieldInput(keyboard = androidx.compose.ui.text.input.KeyboardType.Text))
        }
        TvActionButton(stringResource(R.string.addon_apply_filters), { apply(draft.toMap()) }, Modifier.testTag("discover-grid-apply"), compact = true)
    }
}

@Composable
private fun Titles(model: GridModel, s: GridState, firstCard: FocusRequester, open: (String, MetaPreview) -> Unit) {
    val grid = rememberLazyGridState()
    val owner = s.entry?.installation?.id ?: return
    LazyVerticalGrid(
        GridCells.Adaptive(132.dp), Modifier.fillMaxWidth().testTag("discover-grid-titles"), grid, PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(s.titles, key = { _, m -> "${m.type}:${m.id}" }) { i, m ->
            val caption = listOfNotNull(m.releaseInfo, m.imdbRating).joinToString(" · ")
            PosterCard(
                PosterContent(m.name, m.poster, caption = caption), 132.dp, { open(owner, m) },
                (if (i == 0) Modifier.focusRequester(firstCard) else Modifier).testTag("discover-grid-card-${m.id}"),
            )
        }
        // No key: a keyed footer would stay the scroll anchor and push the first titles out of view as they arrive.
        item(span = { GridItemSpan(maxLineSpan) }) {
            when {
                s.loading && s.titles.isNotEmpty() -> Text(stringResource(R.string.addon_ui_loading_more_titles), style = Sohva.typography.label, color = Sohva.palette.textMuted)
                s.failure != null && s.titles.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(failureText(s.failure), style = Sohva.typography.label, color = Sohva.palette.textMuted)
                    TvActionButton(stringResource(R.string.addon_ui_retry_loading_titles), model::retry, Modifier.testTag("discover-grid-retry"), TvIcons.Refresh, compact = true)
                }
                s.limited -> Text(stringResource(R.string.addon_catalog_limit), style = Sohva.typography.label, color = Sohva.palette.textMuted)
                s.failure != null -> TvActionButton(stringResource(R.string.addon_ui_retry_loading_titles), model::retry, Modifier.testTag("discover-grid-retry"), TvIcons.Refresh, compact = true)
            }
        }
    }
    // FR-117: the next page when the last loaded title comes into view.
    LaunchedEffect(grid, s.titles.size) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .filter { it >= s.titles.size - 1 && s.titles.isNotEmpty() }
            .collect { model.nearEnd() }
    }
}
