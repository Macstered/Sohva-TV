package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import com.sohva.tv.addons.*
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.SohvaNavigationIcons
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.common.KeepFocusedChildVisibleLazyColumn
import com.streammate.tv.feature.common.InheritedFocusScrollBehavior
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

private class Shelf {
    val mutex = Mutex()
    var titles by mutableStateOf<List<AddonMedia>>(emptyList())
    var loaded by mutableStateOf(false)
    var validated by mutableStateOf(false)
    var failure by mutableStateOf<AddonFailure?>(null)
    var stale by mutableStateOf(false)
    val scroll = LazyListState()
    val firstFocus = FocusRequester()
    val showAllFocus = FocusRequester()
    var lastFocus: FocusRequester? = null
}
private data class DiscoverRow(val installation: InstalledAddon, val catalog: AddonCatalog) {
    val key = "${installation.installationId}:${installation.revision}:${catalog.type.length}:${catalog.type}:${catalog.id}"
}

/** Viewport-only catalogs and a debounced focused-title synopsis; no stream/subtitle prefetch. */
@Composable
internal fun AddonDiscoverScreen(host: AddonHost, preferences: AppPreferences, onBack: () -> Unit, modifier: Modifier,
    initiallyManage: Boolean = false, loadInstallations: suspend () -> List<InstalledAddon> = { host.manager.list(preferences.activeProfileId) }) {
    val labels = addonStrings()
    var manage by remember { mutableStateOf(initiallyManage) }
    var discover by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf(false) }
    var selectedHistory by remember { mutableStateOf<AddonWatchProgress?>(null) }
    var history by remember { mutableStateOf<List<Pair<AddonWatchProgress, AddonMedia>>>(emptyList()) }
    var historyLoaded by remember { mutableStateOf(false) }
    var historyFailure by remember { mutableStateOf(false) }
    var historyVersion by remember { mutableIntStateOf(0) }
    val artworkAttempted = remember { mutableSetOf<Pair<String, AddonMediaKey>>() }
    var installations by remember { mutableStateOf<List<InstalledAddon>>(emptyList()) }
    var catalogOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var hiddenCatalogs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loaded by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<AddonFailure?>(null) }
    var selected by remember { mutableStateOf<Pair<InstalledAddon, AddonMedia>?>(null) }
    var catalog by remember { mutableStateOf<Pair<InstalledAddon, AddonCatalog>?>(null) }
    var returning by remember { mutableStateOf<Pair<String, AddonMediaKey>?>(null) }
    var focused by remember { mutableStateOf<AddonMedia?>(null) }
    var focusedInstallation by remember { mutableStateOf<String?>(null) }
    var titleFocused by remember { mutableStateOf(false) }
    var hero by remember { mutableStateOf<AddonMedia?>(null) }
    var activeRow by remember { mutableIntStateOf(-1) }
    var pendingRow by remember { mutableStateOf<Int?>(-1) }
    var railFocused by remember { mutableStateOf(false) }
    var returnRail by remember { mutableStateOf<String?>(null) }
    var returnCatalog by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Reset failed-attempt cooldowns when installations/configuration/profile change.
    val synopsis = remember(host, preferences.activeProfileId, installations.map { it.installationId to it.revision }) {
        AddonHeroSynopsis(
            cached = { profile, owner, media -> host.browser.cachedDetails(profile, owner, media, freshOnly = true) },
            details = { profile, owner, media -> host.browser.details(profile, owner, media).value },
        )
    }
    val shelves = remember { LinkedHashMap<String, Shelf>(24, 0.75f, true) }
    val railFocus = remember { FocusRequester() }
    val discoverFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val libraryFocus = remember { FocusRequester() }
    val managerFocus = remember { FocusRequester() }
    val returnFocus = remember { FocusRequester() }
    val historyFocus = remember { FocusRequester() }
    val historyScroll = rememberLazyListState()
    val visibleCatalogs = remember(installations, catalogOrder, hiddenCatalogs) { AddonCatalogOrdering.visible(installations, catalogOrder, hiddenCatalogs) }
    val rows = remember(visibleCatalogs) { visibleCatalogs.filter { it.catalog.belongsOnLanding() }.map { DiscoverRow(it.installation, it.catalog) } }
    fun shelfFor(row: DiscoverRow): Shelf = shelves.getOrPut(row.key) { Shelf() }.also { if (shelves.size > 24) shelves.remove(shelves.keys.first()) }
    suspend fun loadShelf(row: DiscoverRow) {
        val shelf = shelfFor(row)
        shelf.mutex.withLock {
            if (shelf.validated || shelf.failure != null) return@withLock
            fun publish(titles: List<AddonMedia>) {
                val index = rows.indexOfFirst { it.key == row.key }
                val wasLoaded = shelf.loaded
                shelf.titles = titles; shelf.loaded = true
                if (wasLoaded && activeRow == index && !railFocused) {
                    val refreshed = titles.firstOrNull { it.key == focused?.key }
                    if (refreshed != null) focused = refreshed
                    else { returning = null; pendingRow = index }
                }
            }
            try {
                val saved = host.browser.cachedCatalog(preferences.activeProfileId, row.installation.installationId, row.catalog.type, row.catalog.id)
                if (saved != null) {
                    publish(saved.value.items)
                    if (!saved.stale) { shelf.validated = true; return@withLock }
                }
                val result = host.browser.catalog(preferences.activeProfileId, row.installation.installationId, row.catalog.type, row.catalog.id, itemLimit = 20)
                publish(result.value.items); shelf.stale = result.stale; shelf.validated = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AddonException) {
                // A revoked/invalid resource must remove the provisional cached preview.
                publish(emptyList()); shelf.failure = error.failure
            }
        }
    }
    LaunchedEffect(manage) {
        if (!manage) try {
            val current = loadInstallations()
            val currentOrder = host.catalogOrder.load(preferences.activeProfileId)
            val currentHidden = host.catalogVisibility.loadHidden(preferences.activeProfileId)
            if (currentHidden != hiddenCatalogs || currentOrder != catalogOrder || current.map { it.installationId to it.revision } != installations.map { it.installationId to it.revision }) {
                shelves.clear(); returning = null; pendingRow = -1; activeRow = -1; focused = null; hero = null
                focusedInstallation = null; titleFocused = false
                artworkAttempted.clear()
                list.scrollToItem(0)
            }
            installations = current; catalogOrder = currentOrder; hiddenCatalogs = currentHidden; loaded = true; failure = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: AddonException) { failure = error.failure }
    }
    if (manage) { AddonManagerScreen(host, preferences, { manage = false; returnRail = "manager" }, modifier, loadInstallations); return }
    if (search) { AddonSearchScreen(host, preferences.activeProfileId, installations, { search = false; returnRail = "search" }, modifier, catalogOrder, hiddenCatalogs); return }
    if (library) { AddonLibraryScreen(host, preferences.activeProfileId, installations, { library = false; returnRail = "library" }, modifier); return }
    if (discover) { AddonFilterDiscoverScreen(host, preferences.activeProfileId, installations, { discover = false; returnRail = "discover" }, modifier, catalogOrder, hiddenCatalogs); return }
    selected?.let { (installation, media) ->
        AddonDetailsScreen(host, preferences.activeProfileId, installation, media, { selected = null }, modifier,
            initialVideo = selectedHistory?.identity?.video); return
    }
    catalog?.let { (installation, value) ->
        AddonCatalogScreen(host, preferences.activeProfileId, installation, value, { catalog = null }, modifier); return
    }
    // Capture the input associated with this effect's key. An obsolete not-ready
    // job must not read newer ready state and overlap the actual ready job.
    // null -> empty also loads history correctly on a fresh installation.
    val historyOwners = installations.takeIf { loaded }
    LaunchedEffect(historyOwners) {
        val owners = historyOwners ?: return@LaunchedEffect
        try {
            host.pendingProgressWrite?.join()
            history = host.progress.recent(preferences.activeProfileId).filter { item -> item.resumePositionMillis > 0 &&
                owners.any { it.enabled && it.installationId == item.identity.metadataInstallationId } }
                .distinctBy { it.identity.metadataInstallationId to it.identity.media }.take(20).map { item ->
                    val cached = host.browser.cachedDetails(preferences.activeProfileId, item.identity.metadataInstallationId, item.identity.media)
                    if (item.artwork?.poster == null && cached?.poster != null) try {
                        host.progress.updateArtwork(preferences.activeProfileId, item.identity, AddonWatchArtwork.from(cached))
                    } catch (_: AddonException) { /* Keep displaying cached artwork if the history write fails. */ }
                    item to AddonMedia(item.identity.media, cached?.name ?: item.artwork?.name ?: item.title, cached?.poster ?: item.artwork?.poster, "poster", cached?.background ?: item.artwork?.background,
                        cached?.description, cached?.releaseInfo, emptyList(), logo = cached?.logo, genres = cached?.genres.orEmpty(), runtime = cached?.runtime, imdbRating = cached?.imdbRating)
                }
            historyFailure = false
            if (selectedHistory != null && history.none { sameHistory(it.first, selectedHistory) }) {
                selectedHistory = null; pendingRow = -1
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: AddonException) { historyFailure = true }
        historyLoaded = true
        historyVersion++
    }
    // Older watch rows predate durable artwork. Repair only missing posters, after
    // rendering history; two workers, twenty entries maximum, once per Discover session.
    // Leaving Home cancels work. No catalog scan, stream lookup or media playback.
    val artworkGeneration = historyVersion
    val historyForArtwork = history
    LaunchedEffect(artworkGeneration) {
        if (artworkGeneration == 0) return@LaunchedEffect
        val pending = ArrayDeque(historyForArtwork.filter { it.second.poster == null })
        coroutineScope { repeat(2) { launch {
            while (pending.isNotEmpty()) {
                val (item, _) = pending.removeFirst()
                val key = item.identity.metadataInstallationId to item.identity.media
                if (!artworkAttempted.add(key)) continue
                try {
                    val details = withTimeoutOrNull(8_000) { host.browser.details(preferences.activeProfileId, key.first, key.second).value } ?: continue
                    if (details.poster == null) continue
                    host.progress.updateArtwork(preferences.activeProfileId, item.identity, AddonWatchArtwork.from(details))
                    val repaired = AddonMedia(item.identity.media, details.name, details.poster, "poster", details.background,
                        details.description, details.releaseInfo, emptyList(), logo = details.logo, genres = details.genres, runtime = details.runtime, imdbRating = details.imdbRating)
                    history = history.map { pair -> if (sameHistory(pair.first, item)) pair.first to repaired else pair }
                    if (activeRow == -1 && focusedInstallation == item.identity.metadataInstallationId && focused?.key == item.identity.media) focused = repaired
                } catch (cancelled: CancellationException) { artworkAttempted.remove(key); throw cancelled }
                catch (_: AddonException) { /* Artwork failure must not block shelves or remove watch progress. */ }
            }
        } } }
    }
    // Active and two upcoming catalogs. Cancels obsolete work on fast navigation;
    // the per-shelf mutex remains a guard against concurrent load attempts.
    // Key each request independently: next -> active is still the same request.
    // One effect keyed by activeRow cancels BOTH jobs on Down, aborting the useful
    // in-flight next row and making its visible row fetch the same page again.
    (activeRow.coerceAtLeast(0)..activeRow.coerceAtLeast(0) + 2).mapNotNull(rows::getOrNull).forEach { row ->
        key("catalog-prefetch", row.key) { LaunchedEffect(row.key) { loadShelf(row) } }
    }
    // Warm just the next row's visible posters at the same decode size as the cards.
    // Two workers, six images maximum; no metadata/source/subtitle prefetch.
    rows.getOrNull(activeRow + 1)?.let { row ->
        val posters = shelfFor(row).titles.take(6).mapNotNull { it.poster }.distinct()
        LaunchedEffect(row.key, posters) {
            val queue = ArrayDeque(posters)
            coroutineScope { repeat(2) { launch {
                while (queue.isNotEmpty()) SingletonImageLoader.get(context).execute(addonPosterRequest(context, queue.removeFirst()))
            } } }
        }
    }
    // Disposed on navigation to details/settings/etc.; lifecycle cancellation also stops
    // requests when the app backgrounds. A late response can never overwrite a new focus.
    LaunchedEffect(focused, focusedInstallation, titleFocused, railFocused, activeRow, synopsis, lifecycle) {
        val preview = focused
        if (preview == null) { delay(120); hero = null; return@LaunchedEffect }
        val owner = focusedInstallation ?: return@LaunchedEffect
        if (!titleFocused || railFocused) return@LaunchedEffect
        // Continue watching already restores cached metadata above. Do not turn its
        // automatic initial focus into an additional network history-repair request.
        if (activeRow < 0) { delay(120); hero = preview; return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                synopsis.resolve(preferences.activeProfileId, owner, preview).collect { hero = it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AddonException) {
                hero = if (error.failure in setOf(AddonFailure.ACCESS_DENIED, AddonFailure.CONFLICT, AddonFailure.NOT_FOUND)) null else preview
            }
        }
    }
    LaunchedEffect(returning, manage, selected, catalog, loaded, returnRail, returnCatalog) {
        if (returnRail != null) {
            (when (returnRail) { "discover" -> discoverFocus; "search" -> searchFocus; "library" -> libraryFocus; else -> managerFocus }).requestFocusWhenAttached()
            returnRail = null; returning = null; selectedHistory = null
        } else if (returnCatalog != null) {
            shelves[returnCatalog]?.showAllFocus?.requestFocusWhenAttached()
            returnCatalog = null
        } else if (selectedHistory != null) historyFocus.requestFocusWhenAttached()
        else if (returning != null) returnFocus.requestFocusWhenAttached()
    }
    LaunchedEffect(pendingRow, rows) {
        pendingRow?.takeIf { it == -1 || it in rows.indices }?.let { list.scrollToItem(it + 1) }
    }
    fun returnToShelf() {
        val shelf = rows.getOrNull(activeRow)?.let { shelves[it.key] }
        scope.launch {
            if (activeRow == -1) historyFocus.requestFocusWhenAttached()
            else if (shelf?.lastFocus?.requestFocusWhenAttached() != true) pendingRow = activeRow
        }
    }
    BackHandler { if (railFocused) returnToShelf() else onBack() }
    BoxWithConstraints(modifier.fillMaxSize().testTag("addon-discover")) {
        AddonBackdrop(hero?.background, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(start = 84.dp, end = 24.dp, top = 24.dp)) {
            AddonHero(hero, Modifier.fillMaxWidth(.66f).height(this@BoxWithConstraints.maxHeight * .35f).testTag("addon-hero"))
            if (!loaded) Text(labels(R.string.addon_ui_loading_saved_addons))
            failure?.let { Text(androidx.compose.ui.res.stringResource(it.messageResource())) }
            if (loaded && rows.isEmpty()) Text(if (visibleCatalogs.isNotEmpty())
                labels(R.string.addon_ui_use_search_for_titles_or_discover_for_filtered_catalogs_on_the_lef) else labels(R.string.addon_ui_no_visible_catalogs_open_addons_setup_to_show_catalogs_or_add_your))
            KeepFocusedChildVisibleLazyColumn(modifier = Modifier.weight(1f).testTag("addon-shelves"), state = list,
                contentPadding = PaddingValues(top = 8.dp, bottom = this@BoxWithConstraints.maxHeight * .65f), verticalArrangement = Arrangement.spacedBy(18.dp)) { inheritedBehavior ->
                item(key = "continue") {
                    LaunchedEffect(pendingRow, historyLoaded) {
                        if (pendingRow == -1 && historyLoaded && historyFocus.requestFocusWhenAttached()) pendingRow = null
                    }
                    Column(Modifier.testTag("addon-continue-row").onPreviewKeyEvent {
                        if (it.key == Key.DirectionDown || it.key == Key.DirectionUp) {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown && rows.isNotEmpty()) { selectedHistory = null; pendingRow = 0 }; true
                        } else false
                    }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(labels(R.string.home_continue_watching), fontSize = StreamMateThemeTokens.typography.body.fontSize)
                        InheritedFocusScrollBehavior(inheritedBehavior) {
                        LazyRow(state = historyScroll, contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(history, key = { _, pair -> pair.first.identity.let { "${it.metadataInstallationId}:${it.media.type.length}:${it.media.type}:${it.media.id}" } }) { index, (item, media) ->
                                AddonPosterCard(media, {
                                    selectedHistory = item; returning = null
                                    selected = installations.first { it.installationId == item.identity.metadataInstallationId } to media
                                }, Modifier.width(126.dp).onFocusChanged {
                                    if (!it.isFocused && focused === media && focusedInstallation == item.identity.metadataInstallationId) titleFocused = false
                                }.onPreviewKeyEvent {
                                    if (index == 0 && it.key == Key.DirectionLeft) { if (it.type == KeyEventType.KeyDown) { pendingRow = null; scope.launch { railFocus.requestFocusWhenAttached() } }; true } else false
                                }, focusRequester = if (sameHistory(item, selectedHistory) || (selectedHistory == null && index == 0)) historyFocus else null,
                                    onFocus = { activeRow = -1; focusedInstallation = item.identity.metadataInstallationId; focused = media; titleFocused = true }, tag = "addon-continue-card",
                                    progress = if (item.durationMillis > 0) item.positionMillis.toFloat() / item.durationMillis else null, showCaption = false)
                            }
                            if (history.isEmpty()) item {
                                TvActionButton(if (!historyLoaded) labels(R.string.addon_ui_loading_watch_history) else if (historyFailure) labels(R.string.addon_ui_history_unavailable) else labels(R.string.addon_ui_nothing_to_continue_yet), { pendingRow = 0 },
                                    focusRequester = historyFocus, modifier = Modifier.onFocusChanged { if (it.isFocused) { activeRow = -1; focused = null } }
                                        .onPreviewKeyEvent { if (it.key == Key.DirectionLeft) { if (it.type == KeyEventType.KeyDown) { pendingRow = null; scope.launch { railFocus.requestFocusWhenAttached() } }; true } else false })
                            }
                        }
                    } }
                }
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    val value = row.catalog
                    val shelf = remember(row.key) {
                        shelfFor(row)
                    }
                    // The keyed active/next effects own loading. Lazy-list precomposition
                    // can be disposed/recreated while scrolling; letting it own the same
                    // request cancels useful work even when the active/next consumer remains.
                    LaunchedEffect(pendingRow, shelf.loaded, shelf.failure) {
                        if (pendingRow == index) {
                            shelf.scroll.scrollToItem(0)
                            // A loading/empty row must remain remotely navigable too. Once its
                            // titles arrive, move off the placeholder only if the user stayed here.
                            if (shelf.firstFocus.requestFocusWhenAttached() && (shelf.loaded || shelf.failure != null)) pendingRow = null
                        }
                    }
                    Column(Modifier.testTag("addon-shelf-$index").onPreviewKeyEvent { event ->
                        if (event.key != Key.DirectionDown && event.key != Key.DirectionUp) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown) {
                            val target = index + if (event.key == Key.DirectionDown) 1 else -1
                            if (target == -1 || target in rows.indices) { returning = null; selectedHistory = null; pendingRow = target }
                        }
                        true // Never let spatial search select an arbitrary off-screen horizontal index.
                    }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(value.name, fontSize = StreamMateThemeTokens.typography.body.fontSize,
                            color = StreamMateThemeTokens.palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (shelf.stale) Text(labels(R.string.addon_ui_showing_saved_titles_provider_unavailable))
                        shelf.failure?.let { Text(androidx.compose.ui.res.stringResource(it.messageResource())) }
                        InheritedFocusScrollBehavior(inheritedBehavior) {
                        LazyRow(state = shelf.scroll, contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(shelf.titles, key = { _, media -> "${media.key.type.length}:${media.key.type}:${media.key.id}" }) { cardIndex, media ->
                                val localFocus = remember(media.key) { FocusRequester() }
                                val cardFocus = if (returning == (row.key to media.key)) returnFocus else if (cardIndex == 0) shelf.firstFocus else localFocus
                                AddonPosterCard(media, onClick = { selectedHistory = null; returning = row.key to media.key; selected = row.installation to media },
                                    modifier = Modifier.width(126.dp).onFocusChanged {
                                        if (it.isFocused) { activeRow = index; focusedInstallation = row.installation.installationId; focused = media; titleFocused = true; shelf.lastFocus = cardFocus }
                                        else if (focused === media && focusedInstallation == row.installation.installationId) titleFocused = false
                                    }.onPreviewKeyEvent {
                                        if (cardIndex == 0 && it.key == Key.DirectionLeft) {
                                            if (it.type == KeyEventType.KeyDown) { pendingRow = null; scope.launch { railFocus.requestFocusWhenAttached() } }
                                            true
                                        } else false
                                    }, tag = "addon-landing-card", focusRequester = cardFocus, showCaption = false)
                            }
                            if (!shelf.loaded && shelf.failure == null) items(6, key = { "loading-$it" }) { placeholder ->
                                if (placeholder == 0) TvSurface({}, Modifier.width(126.dp).aspectRatio(2f / 3f)
                                    .onFocusChanged { if (it.isFocused) { activeRow = index; focused = null; shelf.lastFocus = shelf.firstFocus } }
                                    .semantics { contentDescription = labels(R.string.addon_ui_loading_named, value.name) }
                                    .onPreviewKeyEvent {
                                        if (it.key == Key.DirectionLeft) { if (it.type == KeyEventType.KeyDown) scope.launch { railFocus.requestFocusWhenAttached() }; true } else false
                                    }, focusRequester = shelf.firstFocus, focusRing = true, focusScale = 1f,
                                    testTag = "addon-shelf-loading") { Box(Modifier.fillMaxSize()) }
                                else Box(Modifier.width(126.dp).aspectRatio(2f / 3f).background(StreamMateThemeTokens.palette.surface, StreamMateThemeTokens.shapes.medium))
                            }
                            if (shelf.loaded || shelf.failure != null) item(key = "open") {
                                TvActionButton(labels(R.string.addon_ui_show_all),
                                    { returning = null; selectedHistory = null; returnCatalog = row.key; catalog = row.installation to value },
                                    focusRequester = if (shelf.titles.isEmpty()) shelf.firstFocus else shelf.showAllFocus,
                                    testTag = "addon-show-all",
                                    modifier = Modifier.onFocusChanged { if (it.isFocused) { activeRow = index; titleFocused = false; shelf.lastFocus = if (shelf.titles.isEmpty()) shelf.firstFocus else shelf.showAllFocus } }
                                        .onPreviewKeyEvent {
                                            if (shelf.titles.isEmpty() && it.key == Key.DirectionLeft) {
                                                if (it.type == KeyEventType.KeyDown) scope.launch { railFocus.requestFocusWhenAttached() }; true
                                            } else false
                                        })
                            }
                        }
                        }
                        if (shelf.loaded && shelf.titles.isEmpty() && shelf.failure == null) Text(labels(R.string.addon_ui_no_titles_returned))
                    }
                }
            }
        }
        // Overlay expansion keeps posters still. Merely focusing an icon never opens another screen.
        Column(Modifier.fillMaxHeight().width(if (railFocused) 218.dp else 64.dp)
            .background(StreamMateThemeTokens.palette.background.copy(alpha = if (railFocused) .98f else .65f))
            .onFocusChanged { railFocused = it.hasFocus }.focusGroup().padding(horizontal = 8.dp, vertical = 24.dp)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionRight) {
                    if (event.type == KeyEventType.KeyDown) returnToShelf(); true
                } else false
            }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RailItem(labels(R.string.home_nav_home), SohvaNavigationIcons.AddonHome, railFocused, { returnToShelf() }, railFocus, "addon-rail-discover")
            RailItem(labels(com.streammate.tv.iptv.R.string.settings_section_metadata), SohvaNavigationIcons.Library, railFocused, { pendingRow = null; library = true }, libraryFocus, "addon-library")
            RailItem(labels(R.string.home_search), SohvaNavigationIcons.Search, railFocused, { pendingRow = null; search = true }, searchFocus, "addon-search")
            RailItem(labels(R.string.addon_title), SohvaNavigationIcons.Explore, railFocused, { pendingRow = null; discover = true }, discoverFocus, "addon-filter-discover")
            RailItem(labels(R.string.addon_ui_addons_setup), SohvaNavigationIcons.Addons, railFocused, { pendingRow = null; manage = true }, managerFocus, "addon-manage")
            Spacer(Modifier.weight(1f))
            RailItem(labels(R.string.addon_back), SohvaNavigationIcons.BackToHome, railFocused, onBack, tag = "addon-rail-home")
        }
    }
}

private fun sameHistory(first: AddonWatchProgress, second: AddonWatchProgress?) = second != null &&
    first.identity.metadataInstallationId == second.identity.metadataInstallationId && first.identity.media == second.identity.media && first.identity.video == second.identity.video

/** Only catalogs needing a choice before returning titles belong exclusively in Discover. */
internal fun AddonCatalog.belongsOnLanding() = showInHome && extras.none { it.required && it.name != "skip" }

@Composable
private fun RailItem(label: String, icon: Int, expanded: Boolean, onClick: () -> Unit,
    requester: FocusRequester? = null, tag: String) {
    TvSurface(onClick, Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = label },
        focusRequester = requester, testTag = tag, restingContent = StreamMateThemeTokens.palette.textPrimary) { colors ->
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(colors.content))
            if (expanded) Text(label, color = colors.content, fontSize = StreamMateThemeTokens.typography.label.fontSize, maxLines = 1)
        }
    }
}
