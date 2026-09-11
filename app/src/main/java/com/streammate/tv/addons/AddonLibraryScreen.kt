package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.*
import kotlinx.coroutines.launch

@Composable
internal fun AddonLibraryButton(host: AddonHost, profile: String, installation: InstalledAddon, preview: AddonMedia, details: AddonMedia) {
    val labels = addonStrings()
    if (preview.key.type !in setOf("movie", "series")) return
    val identity = remember(installation.installationId, preview.key) { AddonLibraryIdentity(installation.installationId, preview.key) }
    var saved by remember(identity) { mutableStateOf(false) }
    var busy by remember(identity) { mutableStateOf(true) }
    var checked by remember(identity) { mutableStateOf(false) }
    var failure by remember(identity) { mutableStateOf<AddonFailure?>(null) }
    var retry by remember(identity) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(identity, retry) {
        busy = true
        try { saved = host.library.get(profile, identity) != null; checked = true; failure = null }
        catch (error: AddonException) { failure = error.failure }
        finally { busy = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TvActionButton(if (!checked && !busy) labels(R.string.addon_ui_retry_library) else if (saved) labels(R.string.addon_ui_remove_from_library) else labels(R.string.addon_ui_add_to_library), {
            if (busy) return@TvActionButton
            if (!checked) { retry++; return@TvActionButton }
            busy = true
            scope.launch {
                try {
                    if (saved) host.library.remove(profile, identity)
                    else host.library.add(profile, identity, AddonWatchArtwork.from(details, preview), details.releaseInfo ?: preview.releaseInfo)
                    saved = !saved; failure = null
                } catch (error: AddonException) { failure = error.failure }
                finally { busy = false }
            }
        }, icon = if (saved) TvIcons.Check else SohvaNavigationIcons.Library, enabled = checked || !busy,
            selected = saved, compact = true, testTag = "addon-library-toggle")
        failure?.let { Text(if (it == AddonFailure.RESPONSE_TOO_LARGE) labels(R.string.addon_ui_library_is_full_1_000_titles_remove_a_title_first)
            else stringResource(it.messageResource()), color = StreamMateThemeTokens.palette.danger,
            fontSize = StreamMateThemeTokens.typography.caption.fontSize) }
    }
}

@Composable
internal fun AddonLibraryScreen(host: AddonHost, profile: String, installations: List<InstalledAddon>, onBack: () -> Unit, modifier: Modifier) {
    val labels = addonStrings()
    var titles by remember { mutableStateOf<List<AddonLibraryTitle>>(emptyList()) }
    var type by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<Pair<InstalledAddon, AddonLibraryTitle>?>(null) }
    var unavailable by remember { mutableStateOf<AddonLibraryTitle?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<AddonFailure?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var returning by remember { mutableStateOf<String?>(null) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var focusKey by remember { mutableStateOf<String?>(null) }
    var loadVersion by remember { mutableIntStateOf(0) }
    val grid = rememberLazyGridState()
    val backFocus = remember { FocusRequester() }
    val cardFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val visible = titles.filter { type == "all" || it.identity.media.type == type }
    LaunchedEffect(selected, refresh) {
        if (selected != null) return@LaunchedEffect
        loaded = false
        try {
            titles = host.library.list(profile); failure = null
            val next = titles.filter { type == "all" || it.identity.media.type == type }
            focusKey = returning?.let { key -> next.firstOrNull { it.identity.key(profile) == key } ?: next.getOrNull(previousIndex.coerceAtMost(next.lastIndex)) }?.identity?.key(profile)
        } catch (error: AddonException) { titles = emptyList(); failure = error.failure }
        finally { loaded = true; loadVersion++ }
    }
    selected?.let { (installation, title) ->
        AddonDetailsScreen(host, profile, installation, title.preview(), { selected = null }, modifier)
        return
    }
    LaunchedEffect(loaded, loadVersion) {
        if (!loaded) return@LaunchedEffect
        val index = visible.indexOfFirst { it.identity.key(profile) == focusKey }
        if (index >= 0) { grid.scrollToItem(index); cardFocus.requestFocusWhenAttached() }
        else backFocus.requestFocusWhenAttached()
    }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().padding(28.dp).testTag("addon-library-page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(labels(com.streammate.tv.iptv.R.string.settings_section_metadata), Modifier.weight(1f), fontSize = StreamMateThemeTokens.typography.headline.fontSize)
            TvActionButton(labels(R.string.addon_ui_back_to_catalogs), onBack, compact = true, focusRequester = backFocus, testTag = "addon-library-back")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("all" to labels(com.streammate.tv.iptv.R.string.catalogue_all), "movie" to labels(R.string.home_movies), "series" to labels(R.string.home_series)).forEach { (value, label) ->
                TvActionButton(label, { type = value; returning = null; focusKey = null; scope.launch { grid.scrollToItem(0) } },
                    selected = type == value, compact = true, testTag = "addon-library-$value")
            }
        }
        if (!loaded) Text(labels(R.string.addon_ui_loading_saved_titles))
        failure?.let { Text(stringResource(it.messageResource())); TvActionButton(labels(R.string.addon_ui_retry_library), { refresh++ }) }
        if (loaded && failure == null && visible.isEmpty()) Text(if (titles.isEmpty())
            labels(R.string.addon_ui_your_library_is_empty_choose_add_to_library_on_a_movie_or_series_d)
            else labels(if (type == "movie") R.string.addon_ui_no_saved_type else R.string.addon_ui_no_saved_series), Modifier.testTag("addon-library-empty"))
        LazyVerticalGrid(columns = GridCells.Adaptive(132.dp), state = grid,
            modifier = Modifier.weight(1f).testTag("addon-library-grid"), contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            itemsIndexed(visible, key = { _, item -> item.identity.key(profile) }) { index, item ->
                AddonPosterCard(item.preview(), {
                    previousIndex = index; returning = item.identity.key(profile)
                    val owner = installations.firstOrNull { it.enabled && it.installationId == item.identity.installationId }
                    if (owner == null) unavailable = item else selected = owner to item
                }, Modifier.fillMaxWidth(), focusRequester = if (focusKey == item.identity.key(profile)) cardFocus else null,
                    tag = "addon-library-card")
            }
        }
    }
    unavailable?.let { item ->
        Dialog({ unavailable = null }) {
            val dismiss = remember { FocusRequester() }
            LaunchedEffect(Unit) { dismiss.requestFocusWhenAttached() }
            Column(Modifier.background(StreamMateThemeTokens.palette.panel).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(labels(R.string.addon_ui_this_title_s_addon_is_disabled_or_no_longer_installed_your_saved_t))
                TvActionButton(labels(R.string.picture_in_picture_close), { unavailable = null }, focusRequester = dismiss)
                TvActionButton(labels(R.string.addon_ui_remove_from_library), { scope.launch {
                    try { host.library.remove(profile, item.identity); unavailable = null; refresh++ }
                    catch (error: AddonException) { failure = error.failure; unavailable = null }
                } }, compact = true, testTag = "addon-library-remove-unavailable")
            }
        }
    }
}
