package com.streammate.tv.addons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.sohva.tv.addons.AddonCatalog
import com.sohva.tv.addons.AddonException
import com.sohva.tv.addons.AddonFailure
import com.sohva.tv.addons.AddonMedia
import com.sohva.tv.addons.InstalledAddon
import com.streammate.tv.R
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.feature.common.SohvaTvBrand
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.requestFocusWhenAttached
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun AddonCatalogScreen(
    host: AddonHost, profileId: String, installation: InstalledAddon, catalog: AddonCatalog,
    onBack: () -> Unit, modifier: Modifier, discovery: Boolean = false, chooser: @Composable () -> Unit = {},
) {
    val labels = addonStrings()
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var media by remember { mutableStateOf<List<AddonMedia>>(emptyList()) }
    var nextSkip by remember { mutableStateOf<Int?>(null) }
    var extras by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var draft by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val optionExtras = remember(catalog) { catalog.extras.filter { it.name != "skip" && it.options.isNotEmpty() } }
    val textExtras = remember(catalog) { catalog.extras.filter { it.name != "skip" && it.options.isEmpty() } }
    var filters by remember { mutableStateOf(textExtras.any { it.required }) }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var stale by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<AddonFailure?>(null) }
    var selected by remember { mutableStateOf<AddonMedia?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableStateOf(0) }
    var returningKey by remember { mutableStateOf<com.sohva.tv.addons.AddonMediaKey?>(null) }
    var failedSkip by remember { mutableStateOf<Int?>(null) }
    val returnFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    fun complete(values: Map<String, String>) = catalog.extras.none { it.required && it.name != "skip" && values[it.name].isNullOrBlank() }

    fun load(skip: Int = 0, refresh: Boolean = false, newExtras: Map<String, String> = extras) {
        if (!complete(newExtras)) return
        activeJob?.cancel()
        val requestGeneration = ++generation
        loading = true
        failure = null
        failedSkip = null
        extras = newExtras
        draft = newExtras
        activeJob = scope.launch {
            try {
                val values = newExtras.toMutableMap()
                if (catalog.extras.any { it.name == "skip" }) values["skip"] = skip.toString()
                val result = host.browser.catalog(profileId, installation.installationId, catalog.type, catalog.id, values, refresh)
                if (generation != requestGeneration) return@launch
                val previous = if (skip == 0) emptyList() else media
                val merged = (previous + result.value.items).distinctBy { it.key }
                // Bound a browsing session and stop providers which ignore skip and repeat pages.
                nextSkip = if ((skip > 0 && merged.size == previous.size) || merged.size >= 1000) null
                    else catalog.nextSkip(skip, result.value.receivedCount)
                media = merged.take(1000)
                extras = newExtras
                stale = result.stale
                loaded = true
                filters = false
                if (skip == 0) gridState.scrollToItem(0)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AddonException) { if (generation == requestGeneration) { failure = error.failure; failedSkip = skip } }
            finally { if (generation == requestGeneration) loading = false }
        }
    }
    LaunchedEffect(Unit) {
        if (complete(extras)) load()
        backFocus.requestFocusWhenAttached()
    }
    selected?.let { item ->
        AddonDetailsScreen(host, profileId, installation, item, { selected = null; returningKey = item.key }, modifier)
        return
    }
    LaunchedEffect(returningKey) {
        if (returningKey != null) returnFocus.requestFocusWhenAttached()
    }
    // Observe the visible last row, not a focusable Load more footer. One request
    // at a time; errors require an explicit retry rather than a tight retry loop.
    LaunchedEffect(gridState) {
        snapshotFlow {
            nextSkip?.takeIf { !loading && failure == null && !filters && media.isNotEmpty() &&
                (gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1) >= media.lastIndex }
        }.distinctUntilChanged().collect { skip -> if (skip != null) load(skip) }
    }
    BackHandler { if (filters && loaded) filters = false else onBack() }
    Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (discovery) labels(R.string.addon_title) else catalog.name, fontSize = StreamMateThemeTokens.typography.headline.fontSize)
        if (discovery || catalog.extras.any { it.required && it.name != "skip" }) LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("addon-catalog-choices")) {
            if (discovery) item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { chooser() } }
            items(optionExtras, key = { it.name }) { extra ->
                AddonChoice(labels.filterName(extra.name), draft[extra.name].orEmpty(),
                    (if (extra.required) listOf("" to labels(R.string.addon_ui_select)) else listOf("" to labels(R.string.addon_ui_default))) + extra.options.map { it to it }, { value ->
                        val next = draft.toMutableMap().apply { if (value.isEmpty()) remove(extra.name) else put(extra.name, value) }
                        draft = next
                        if (complete(next)) load(newExtras = next)
                        else {
                            activeJob?.cancel(); generation++; loading = false; loaded = false
                            media = emptyList(); nextSkip = null; failure = null; extras = next
                        }
                    }, Modifier.width(230.dp), tag = extra.name.replaceFirstChar(Char::uppercase))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvActionButton(stringResource(R.string.addon_back_catalogs), onBack, focusRequester = backFocus)
            if (discovery && textExtras.isNotEmpty()) {
                TvActionButton(stringResource(R.string.addon_filters), { draft = extras; filters = !filters })
            }
            TvActionButton(stringResource(R.string.addon_refresh_catalog), { load(refresh = true) }, enabled = !loading && !filters && complete(extras))
        }
        if (loading && media.isEmpty()) Text(stringResource(R.string.addon_loading))
        if (stale) Text(stringResource(R.string.addon_cached))
        failure?.takeIf { media.isEmpty() }?.let { Text(stringResource(it.messageResource())) }
        if (filters) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("addon-text-filters")) {
                items(textExtras, key = { it.name }) { extra ->
                    TvUrlField(draft[extra.name].orEmpty(), { value ->
                        if (value.length <= 1024) draft = draft.toMutableMap().apply {
                            if (value.isBlank()) remove(extra.name) else put(extra.name, value)
                        }
                    }, labels.filterName(extra.name) + if (extra.required) " *" else "", keyboardType = KeyboardType.Text, editOnClickOnly = true)
                }
                item {
                    TvActionButton(stringResource(R.string.addon_apply_filters), { load(newExtras = draft, refresh = false) },
                        enabled = !loading && complete(draft))
                }
            }
        } else {
            if (!complete(draft)) Text(labels(R.string.addon_ui_choose_the_required_filters_above_to_load_titles))
            if (loaded && media.isEmpty()) Text(stringResource(R.string.addon_no_results))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp), state = gridState, modifier = Modifier.fillMaxSize().testTag("addon-catalog-grid"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(media, key = { "${it.key.type.length}:${it.key.type}${it.key.id}" }) { item ->
                    AddonPosterCard(item, { returningKey = null; selected = item },
                        modifier = Modifier.fillMaxWidth(), focusRequester = if (returningKey == item.key) returnFocus else null)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (loading && media.isNotEmpty()) Text(labels(R.string.addon_ui_loading_more_titles), Modifier.testTag("addon-page-loading"))
                    if (failure != null && failedSkip != null && media.isNotEmpty()) TvActionButton(labels(R.string.addon_ui_retry_loading_titles), { load(checkNotNull(failedSkip)) }, testTag = "addon-page-retry")
                    if (media.size >= 1000) Text(stringResource(R.string.addon_catalog_limit))
                }
            }
        }
    }
}
