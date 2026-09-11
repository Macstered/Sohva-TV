package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun AddonSearchScreen(host: AddonHost, profile: String, installations: List<InstalledAddon>,
    onBack: () -> Unit, modifier: Modifier, order: List<String> = emptyList(), hidden: Set<String> = emptySet()) {
    val labels = addonStrings()
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val catalogs = remember(installations, order, hidden) { AddonSearch.catalogs(installations, order, hidden) }
    val targetCount = minOf(catalogs.size, AddonSearch.MAX_CATALOGS)
    var draft by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<AddonSearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var completed by remember { mutableIntStateOf(0) }
    var failures by remember { mutableIntStateOf(0) }
    var stale by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<AddonFailure?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var selected by remember { mutableStateOf<AddonSearchHit?>(null) }
    var returning by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val inputFocus = remember { FocusRequester() }
    val returnFocus = remember { FocusRequester() }
    val firstFocus = remember { List(2) { FocusRequester() } }
    val rowScroll = remember { List(2) { LazyListState() } }
    val list = rememberLazyListState()
    val rows = listOf(hits.filter { it.media.key.type == "movie" }, hits.filter { it.media.key.type == "series" })
    fun moveToRow(index: Int) {
        if (index !in rows.indices || rows[index].isEmpty()) return
        returning = null
        scope.launch { list.scrollToItem(index); rowScroll[index].scrollToItem(0); firstFocus[index].requestFocusWhenAttached() }
    }
    fun clear() {
        job?.cancel(); generation++; submitted = ""; hits = emptyList(); loading = false
        completed = 0; failures = 0; stale = false; failure = null; returning = null
    }
    fun search() {
        val query = draft.trim()
        clear()
        if (query.isEmpty() || targetCount == 0) return
        val version = generation
        submitted = query; loading = true
        job = scope.launch {
            try {
                list.scrollToItem(0); rowScroll.forEach { it.scrollToItem(0) }
                host.search.search(profile, catalogs, query).collect { batch ->
                    if (version != generation) return@collect
                    hits = AddonSearch.merge(hits, batch.hits)
                    completed++
                    if (batch.failure != null) failures++
                    stale = stale || batch.stale
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AddonException) { if (version == generation) { hits = emptyList(); failure = error.failure } }
            catch (_: Exception) { if (version == generation) { hits = emptyList(); failure = AddonFailure.NETWORK } }
            finally { if (version == generation) loading = false }
        }
    }
    selected?.let { hit ->
        AddonDetailsScreen(host, profile, hit.installation, hit.media, { selected = null; returning = hit.key }, modifier)
        return
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(returning) {
        if (returning != null) returnFocus.requestFocusWhenAttached()
        else if (!entered) { entered = true; inputFocus.requestFocusWhenAttached() }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp).testTag("addon-search-page"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(labels(R.string.home_search), Modifier.weight(1f), fontSize = typography.headline.fontSize, color = palette.textPrimary)
            TvActionButton(labels(R.string.addon_ui_back_to_catalogs), onBack, compact = true, testTag = "addon-search-back")
        }
        Row(Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            if (event.key == Key.DirectionDown && rows.any { it.isNotEmpty() }) {
                if (event.type == KeyEventType.KeyDown) moveToRow(rows.indexOfFirst { it.isNotEmpty() })
                true
            } else false
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvUrlField(draft, { value ->
                if (value.length <= AddonSearch.MAX_QUERY_LENGTH) { draft = value; if (value.isBlank()) clear() }
            }, labels(R.string.addon_ui_search_movies_and_series), Modifier.weight(1f).focusRequester(inputFocus),
                testTag = "addon-search-query", leadingIconRes = TvIcons.Search, keyboardType = KeyboardType.Text,
                editOnClickOnly = true, compact = true)
            TvActionButton(labels(R.string.home_search), ::search, enabled = draft.isNotBlank() && targetCount > 0,
                compact = true, testTag = "addon-search-submit")
            TvActionButton(labels(com.streammate.tv.iptv.R.string.manager_clear), { draft = ""; clear(); scope.launch { inputFocus.requestFocusWhenAttached() } },
                enabled = draft.isNotEmpty() || submitted.isNotEmpty(), compact = true, testTag = "addon-search-clear")
        }
        Text(when {
            targetCount == 0 -> labels(R.string.addon_ui_no_enabled_visible_catalogs_support_plain_text_search_catalogs_req)
            submitted.isEmpty() -> labels(R.string.addon_ui_enter_a_title_then_choose_search)
            loading -> labels(R.string.addon_ui_search_progress, completed, targetCount)
            failures > 0 -> labels(R.string.addon_ui_some_catalogs_could_not_be_searched_choose_search_to_retry)
            stale -> labels(R.string.addon_ui_showing_saved_search_results_an_addon_is_unavailable)
            else -> labels(R.string.addon_ui_search_for, submitted)
        }, color = palette.textMuted, fontSize = typography.caption.fontSize, modifier = Modifier.testTag("addon-search-status"))
        failure?.let { Text(stringResource(it.messageResource()), color = palette.textPrimary) }
        if (catalogs.size > targetCount) Text(labels(R.string.addon_ui_search_limit, targetCount), color = palette.textMuted, fontSize = typography.caption.fontSize)
        KeepFocusedChildVisibleLazyColumn(modifier = Modifier.weight(1f).testTag("addon-search-results"), state = list,
            contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { behavior ->
            items(2, key = { it }) { index ->
                val type = if (index == 0) "movie" else "series"
                Column(Modifier.testTag("addon-search-row-$type").onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown) {
                                val target = index + if (event.key == Key.DirectionDown) 1 else -1
                                if (target < 0 || (event.key == Key.DirectionUp && rows[target].isEmpty())) scope.launch { inputFocus.requestFocusWhenAttached() }
                                else moveToRow(target)
                            }
                            true
                        }
                        else -> false
                    }
                }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (index == 0) labels(R.string.home_movies) else labels(R.string.home_series), color = palette.textPrimary, fontSize = typography.body.fontSize)
                    if (rows[index].isEmpty()) Text(if (submitted.isEmpty()) labels(R.string.addon_ui_search_results_will_appear_here)
                        else if (loading) labels(R.string.search_loading) else if (failures > 0 || failure != null) labels(R.string.addon_ui_no_results_from_available_catalogs)
                        else labels(if (index == 0) R.string.addon_ui_no_matching_type else R.string.addon_ui_no_matching_series), color = palette.textMuted, fontSize = typography.caption.fontSize)
                    InheritedFocusScrollBehavior(behavior) {
                        LazyRow(state = rowScroll[index], horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
                            itemsIndexed(rows[index], key = { _, hit -> hit.key }) { position, hit ->
                                AddonPosterCard(hit.media, { selected = hit }, Modifier.width(112.dp),
                                    focusRequester = if (returning == hit.key) returnFocus else if (position == 0) firstFocus[index] else null,
                                    tag = "addon-search-result-$type")
                            }
                        }
                    }
                    if (rows[index].size >= AddonSearch.MAX_ROW_ITEMS) Text(labels(R.string.addon_ui_showing_100_results_refine_your_search_for_more_precise_matches),
                        color = palette.textMuted, fontSize = typography.caption.fontSize)
                }
            }
        }
    }
}
