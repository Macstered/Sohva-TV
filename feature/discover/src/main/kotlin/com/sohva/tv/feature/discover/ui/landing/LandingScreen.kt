package com.sohva.tv.feature.discover.ui.landing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.feature.discover.protocol.MetaPreview
import com.sohva.tv.feature.discover.store.CatalogEntry
import com.sohva.tv.feature.discover.ui.components.DiscoverRail
import com.sohva.tv.feature.discover.ui.components.RailTarget
import com.sohva.tv.feature.discover.ui.factsLine
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.focus.KeepVisibleBringIntoViewSpec
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Where the landing sends the viewer. */
class LandingActions(
    val openTitle: (owner: String, item: MetaPreview) -> Unit,
    val openContinue: (ContinueCard) -> Unit,
    val openGrid: (CatalogEntry) -> Unit,
    val openPage: (RailTarget) -> Unit,
    val leave: () -> Unit,
)

/**
 * The Discover landing (spec 50 §4.9, §5.1): backdrop, hero, Continue watching, then one shelf per
 * ready catalog, with the rail over the left edge. Down and Up move whole rows to their first card
 * (FR-67); only the active row and the next two load (FR-59), from effects owned here, never by the
 * lazy list's precomposition.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LandingScreen(model: LandingModel, actions: LandingActions, railFocus: RailTarget? = null) {
    val setup by model.setup.collectAsStateWithLifecycle()
    val rebuilt by model.rebuilt.collectAsStateWithLifecycle()
    val shelves = setup?.shelves.orEmpty()
    val targets = remember { LandingFocusTargets() }
    val rail = remember { RailTarget.entries.associateWith { FocusRequester() } }
    val scope = rememberCoroutineScope()
    var active by remember { mutableIntStateOf(0) }
    var railFocused by remember { mutableStateOf(false) }

    fun rowKey(row: Int): String = if (row == 0) CONTINUE_ROW else shelves[row - 1].key

    fun moveTo(row: Int) {
        scope.launch {
            model.list.animateScrollToItem(row)
            // Only a row with a list to scroll (an empty Continue row is a single button).
            val rowState = model.rowState(rowKey(row))
            if (rowState.layoutInfo.totalItemsCount > 0) rowState.scrollToItem(0)
            targets.first(rowKey(row)).requestFocusWhenAttached()
        }
    }

    // Back to the rows from the rail: the card last focused, else the Continue row (FR-68).
    fun backToRows() {
        railFocused = false
        val row = model.lastRow
        val item = model.lastItem
        scope.launch {
            val requester = when {
                row == null -> targets.first(CONTINUE_ROW)
                item == null -> targets.first(row)
                item == SHOW_ALL -> targets.showAll(row)
                else -> targets.item(row, item)
            }
            if (!requester.requestFocusWhenAttached()) targets.first(CONTINUE_ROW).requestFocusWhenAttached()
        }
    }
    val moves = remember(shelves) {
        RowMoves(
            onVertical = { row, down ->
                val target = if (down) row + 1 else row - 1
                if (target in 0..shelves.size) moveTo(target)
                true
            },
            toRail = {
                railFocused = true
                model.shelvesFocused = false
                model.hero.focus.value = LandingFocus.Other
                rail.getValue(RailTarget.HOME).requestFocus()
            },
            focused = { row, item ->
                railFocused = false
                model.lastRow = row
                model.lastItem = item
                model.shelvesFocused = true
                active = if (row == CONTINUE_ROW) 0 else (shelves.indexOfFirst { it.key == row }).coerceAtLeast(0)
            },
        )
    }
    // Back with focus in the rows leaves Discover; the rail's own handler takes it while expanded.
    BackHandler(enabled = !railFocused) { actions.leave() }

    Box(Modifier.fillMaxSize().testTag("discover-landing")) {
        Backdrop(model)
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 84.dp, end = 24.dp, top = 24.dp)) {
            val height = maxHeight
            Column(Modifier.fillMaxSize()) {
                HeroBlock(model, Modifier.fillMaxWidth(0.66f).height(height * 0.35f))
                status(setup)?.let { Text(it, Modifier.padding(vertical = 6.dp).testTag("discover-status"), style = Sohva.typography.label, color = Sohva.palette.textMuted) }
                CompositionLocalProvider(LocalBringIntoViewSpec provides KeepVisibleBringIntoViewSpec) {
                    LazyColumn(
                        state = model.list, modifier = Modifier.fillMaxWidth().weight(1f).testTag("discover-rows"),
                        contentPadding = PaddingValues(top = 8.dp, bottom = height * 0.65f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        item(key = CONTINUE_ROW) {
                            ContinueRow(model, targets, model.rowState(CONTINUE_ROW), moves, actions.openContinue) { if (shelves.isNotEmpty()) moveTo(1) }
                        }
                        itemsIndexed(shelves, key = { _, e -> e.key }) { i, entry ->
                            Shelf(entry, i + 1, model, targets, model.rowState(entry.key), moves, { e, item -> actions.openTitle(e.installation.id, item) }, actions.openGrid)
                        }
                    }
                }
            }
        }
        DiscoverRail(
            rail,
            open = { target -> if (target == RailTarget.LEAVE) actions.leave() else actions.openPage(target) },
            back = ::backToRows,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }

    // FR-59: the active shelf and the next two, each by its own effect keyed by the shelf.
    for (i in active until minOf(active + WINDOW, shelves.size)) {
        val entry = shelves[i]
        key(entry.key) { LaunchedEffect(entry.key, rebuilt) { model.loadShelf(entry) } }
    }
    WarmUp(model, shelves.getOrNull(active + 1))
    LaunchedEffect(Unit) {
        model.loadContinue()
        model.repairArtwork()
    }
    LifecycleResumeEffect(model) {
        model.hero.resumed.value = true
        onPauseOrDispose { model.hero.resumed.value = false }
    }
    LaunchedEffect(rebuilt) { if (rebuilt > 1) model.list.scrollToItem(0) }
    // Entry: the card the viewer left, a rail item a page returned to, else Continue's first card (FR-36).
    val continueLoaded = model.continueRow.collectAsStateWithLifecycle().value !is ContinueState.Loading
    LaunchedEffect(continueLoaded, setup != null) {
        if (!continueLoaded || setup == null) return@LaunchedEffect
        if (railFocus != null) {
            railFocused = true
            rail.getValue(railFocus).requestFocusWhenAttached()
            return@LaunchedEffect
        }
        val row = model.lastRow
        val item = model.lastItem
        if (row != null) {
            val index = if (row == CONTINUE_ROW) 0 else shelves.indexOfFirst { it.key == row } + 1
            if (index >= 0 && (row == CONTINUE_ROW || index > 0)) {
                val requester = when (item) {
                    null -> targets.first(row)
                    SHOW_ALL -> targets.showAll(row)
                    else -> targets.item(row, item)
                }
                if (requester.requestFocusWhenAttached()) return@LaunchedEffect
            }
        }
        targets.first(CONTINUE_ROW).requestFocusWhenAttached()
    }
}

@Composable
private fun status(setup: LandingSetup?): String? = when {
    setup == null -> stringResource(R.string.addon_ui_loading_saved_addons)
    !setup.anyVisible -> stringResource(R.string.addon_ui_no_visible_catalogs_open_addons_setup_to_show_catalogs_or_add_your)
    setup.shelves.isEmpty() -> stringResource(R.string.addon_ui_use_search_for_titles_or_discover_for_filtered_catalogs_on_the_lef)
    else -> null
}

/**
 * The hero (§5.1): the logo (its space kept while it loads; the title only without one or when it
 * fails) or the title, the facts line, and the synopsis the resolver settled on.
 */
@Composable
private fun HeroBlock(model: LandingModel, modifier: Modifier) {
    val hero by model.hero.hero.collectAsStateWithLifecycle()
    val preview = hero.preview
    Column(modifier.testTag("discover-hero"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.weight(1f))
        Logo(preview)
        if (preview != null) {
            Text(factsLine(preview), maxLines = 2, style = Sohva.typography.body, color = Sohva.palette.textMuted)
            hero.synopsis?.let {
                Text(it, Modifier.testTag("discover-hero-synopsis"), maxLines = 3, overflow = TextOverflow.Ellipsis, style = Sohva.typography.label, color = Sohva.palette.textMuted)
            }
        }
    }
}

@Composable
private fun Logo(preview: MetaPreview?) {
    val loader = LocalArtwork.current
    val density = LocalDensity.current
    val url = preview?.logo
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(url == null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        image = loader.load(url, with(density) { 320.dp.roundToPx() }, with(density) { 88.dp.roundToPx() }, opaque = false)
        failed = image == null
    }
    val title = preview?.name ?: stringResource(R.string.addon_title)
    if (failed) {
        Text(
            title, Modifier.testTag("discover-hero-title"), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = Sohva.typography.display.copy(fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary,
        )
    } else {
        // The logo's space stays empty while it loads: no title flashes first (lesson 16).
        Box(
            Modifier.size(320.dp, 88.dp).drawBehind {
                val bitmap = image ?: return@drawBehind
                val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
                drawImage(bitmap, dstOffset = IntOffset(0, ((size.height - bitmap.height * scale) / 2).toInt()), dstSize = IntSize((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt()))
            },
        )
    }
}

/**
 * The backdrop (§5.1, §9 "Backdrop"): the focused title's background decoded at 960 × 540 or less
 * as RGB_565 under two scrims built once per size, changed only after focus rests (the hero does).
 */
@Composable
private fun Backdrop(model: LandingModel) {
    val hero by model.hero.hero.collectAsStateWithLifecycle()
    val url = hero.preview?.background
    val loader = LocalArtwork.current
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { image = url?.let { loader.load(it, BACKDROP_W, BACKDROP_H, opaque = true) } }
    val ground = Sohva.palette.background
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val across = Brush.horizontalGradient(0f to ground.copy(alpha = 0.98f), 0.5f to ground.copy(alpha = 0.75f), 1f to ground.copy(alpha = 0.20f))
            val down = Brush.verticalGradient(0f to ground.copy(alpha = 0f), 0.6f to ground.copy(alpha = 0.35f), 1f to ground)
            onDrawBehind {
                drawRect(ground)
                image?.let { bitmap ->
                    // Cropped to fill, aligned top-end.
                    val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
                    val w = (bitmap.width * scale).toInt()
                    val h = (bitmap.height * scale).toInt()
                    drawImage(bitmap, dstOffset = IntOffset((size.width - w).toInt(), 0), dstSize = IntSize(w, h))
                }
                drawRect(across)
                drawRect(down)
            }
        },
    )
}

/** FR-63: the first six distinct posters of the row below the active one, decoded ahead, two at a time. */
@Composable
private fun WarmUp(model: LandingModel, next: CatalogEntry?) {
    val loader = LocalArtwork.current
    val px = with(LocalDensity.current) { CARD_WIDTH.roundToPx() }
    val items = next?.let { remember(it.key) { model.shelf(it.key) }.collectAsStateWithLifecycle().value.items }
    LaunchedEffect(next?.key, items != null) {
        val posters = items.orEmpty().mapNotNull { it.poster }.distinct().take(WARM_POSTERS)
        val workers = Semaphore(2)
        posters.forEach { url -> launch { workers.withPermit { loader.load(url, px, px * 3 / 2, opaque = true) } } }
    }
}

private const val WINDOW = 3
private const val WARM_POSTERS = 6
private const val BACKDROP_W = 960
private const val BACKDROP_H = 540
