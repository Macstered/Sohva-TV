package com.sohva.tv.feature.discover.ui.landing

import com.sohva.tv.feature.discover.ui.components.marked
import com.sohva.tv.feature.discover.ui.components.movieMarkKey
import com.sohva.tv.feature.discover.ui.components.rememberMarks
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.feature.discover.store.CatalogEntry
import com.sohva.tv.feature.discover.ui.components.PosterCard
import com.sohva.tv.feature.discover.ui.components.PosterContent
import com.sohva.tv.feature.discover.ui.components.PosterPlaceholder
import com.sohva.tv.feature.discover.ui.failureText
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** Focus targets of the landing, registered by the rows that own them. */
class LandingFocusTargets {
    private val requesters = HashMap<String, FocusRequester>()

    fun get(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun first(row: String): FocusRequester = get("$row|first")

    fun item(row: String, item: String): FocusRequester = get("$row|item|$item")

    fun showAll(row: String): FocusRequester = get("$row|show-all")
}

/** How a row reports focus and asks for moves (FR-67). */
class RowMoves(val onVertical: (row: Int, down: Boolean) -> Boolean, val toRail: () -> Unit, val focused: (row: String, item: String?) -> Unit)

internal val CARD_WIDTH = 126.dp
internal const val CONTINUE_ROW = "continue"

/** The row's key handling: Down and Up move whole rows, never by spatial search (FR-67). */
private fun Modifier.rowKeys(index: Int, moves: RowMoves): Modifier = onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (e.key) {
        Key.DirectionDown -> moves.onVertical(index, true)
        Key.DirectionUp -> moves.onVertical(index, false)
        else -> false
    }
}

/** Left on a row's first card, placeholder, or an empty shelf's Show all goes to the rail's Home. */
private fun Modifier.leftToRail(first: Boolean, moves: RowMoves): Modifier = if (!first) this else onPreviewKeyEvent { e ->
    (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft).also { if (it) moves.toRail() }
}

/** FR-64: Continue watching, always first; a button while loading, failed or empty. */
@Composable
internal fun ContinueRow(model: LandingModel, targets: LandingFocusTargets, state: LazyListState, moves: RowMoves, open: (ContinueCard) -> Unit, toFirstShelf: () -> Unit) {
    val row by model.continueRow.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().rowKeys(0, moves)) {
        Text(stringResource(R.string.home_continue_watching), style = Sohva.typography.body, color = Sohva.palette.textPrimary, maxLines = 1)
        val cards = (row as? ContinueState.Ready)?.cards.orEmpty()
        if (cards.isEmpty()) {
            val label = when (row) {
                ContinueState.Loading -> R.string.addon_ui_loading_watch_history
                ContinueState.Failed -> R.string.addon_ui_history_unavailable
                is ContinueState.Ready -> R.string.addon_ui_nothing_to_continue_yet
            }
            TvActionButton(
                stringResource(label), toFirstShelf,
                Modifier.padding(8.dp).focusRequester(targets.first(CONTINUE_ROW)).leftToRail(true, moves)
                    .onFocusChanged {
                        if (it.isFocused) {
                            moves.focused(CONTINUE_ROW, null)
                            model.hero.focus.value = LandingFocus.Empty
                        }
                    }.testTag("discover-continue-empty"),
                compact = true,
            )
            return@Column
        }
        LazyRow(state = state, contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("discover-continue")) {
            itemsIndexed(cards, key = { _, c -> c.key }) { i, card ->
                val requester = targets.item(CONTINUE_ROW, card.key)
                val first = targets.first(CONTINUE_ROW)
                PosterCard(
                    PosterContent(card.preview.name, card.preview.poster, progress = card.entry.fraction), CARD_WIDTH, { open(card) },
                    Modifier.focusRequester(requester).then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .leftToRail(i == 0, moves)
                        .onFocusChanged {
                            if (it.isFocused) {
                                moves.focused(CONTINUE_ROW, card.key)
                                model.hero.focus.value = LandingFocus.ContinueCard(card.preview)
                            }
                        }
                        .testTag("discover-continue-${card.entry.identity.mediaId}"),
                )
            }
        }
    }
}

/** One catalog shelf (FR-58…61, §5.1): heading, stale or failure line, posters without captions, Show all. */
@Composable
internal fun Shelf(
    entry: CatalogEntry,
    index: Int,
    model: LandingModel,
    targets: LandingFocusTargets,
    state: LazyListState,
    moves: RowMoves,
    open: (CatalogEntry, com.sohva.tv.feature.discover.protocol.MetaPreview) -> Unit,
    showAll: (CatalogEntry) -> Unit,
) {
    val shelf by remember(entry.key) { model.shelf(entry.key) }.collectAsStateWithLifecycle()
    val row = entry.key
    // FR-60: a refreshed shelf keeps the focused title by identity, else its first card. Only while
    // focus is in this row: data never takes focus from anywhere else (AGENTS.md §5 rule 2).
    LaunchedEffect(shelf.items) {
        if (!model.shelvesFocused || model.lastRow != row || model.lastItem == SHOW_ALL) return@LaunchedEffect
        val item = model.lastItem
        val keep = item?.takeIf { id -> shelf.items.orEmpty().any { "${it.type}:${it.id}" == id } }
        (if (keep != null) targets.item(row, keep) else targets.first(row)).requestFocusWhenAttached(
            stillWanted = { model.shelvesFocused && model.lastRow == row && model.lastItem == item },
        )
    }
    Column(Modifier.fillMaxWidth().rowKeys(index, moves).testTag("discover-shelf-$index")) {
        Text(entry.catalog.name, style = Sohva.typography.body, color = Sohva.palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val note = when {
            shelf.failure != null -> failureText(shelf.failure!!)
            shelf.stale -> stringResource(R.string.addon_ui_showing_saved_titles_provider_unavailable)
            shelf.done && shelf.items.isNullOrEmpty() -> stringResource(R.string.addon_ui_no_titles_returned)
            else -> null
        }
        note?.let { Text(it, style = Sohva.typography.caption, color = Sohva.palette.textMuted, maxLines = 2) }
        val items = shelf.items
        val marks = rememberMarks(remember(items) { items.orEmpty().mapNotNull { movieMarkKey(it.type, it.id) } })
        LazyRow(
            state = state, contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (items == null && !shelf.done) {
                // FR-61: six placeholders; the first takes focus so the D-pad never sticks.
                items(PLACEHOLDERS) { i ->
                    val description = stringResource(R.string.addon_ui_loading_named, entry.catalog.name)
                    PosterPlaceholder(
                        CARD_WIDTH, i == 0, description,
                        if (i == 0) {
                            Modifier.focusRequester(targets.first(row)).leftToRail(true, moves)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        moves.focused(row, null)
                                        model.hero.focus.value = LandingFocus.Empty
                                    }
                                }
                                .testTag("discover-placeholder-$index")
                        } else {
                            Modifier
                        },
                    )
                }
            } else {
                val list = items.orEmpty()
                itemsIndexed(list, key = { _, m -> "${m.type}:${m.id}" }) { i, item ->
                    val id = "${item.type}:${item.id}"
                    PosterCard(
                        PosterContent(item.name, item.poster).marked(movieMarkKey(item.type, item.id)?.let(marks::get)), CARD_WIDTH, { open(entry, item) },
                        Modifier.focusRequester(targets.item(row, id)).then(if (i == 0) Modifier.focusRequester(targets.first(row)) else Modifier)
                            .leftToRail(i == 0, moves)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    moves.focused(row, id)
                                    model.hero.focus.value = LandingFocus.ShelfCard(entry.installation.id, item)
                                }
                            }
                            .testTag("discover-card-$index-${item.id}"),
                    )
                }
                item(key = "show-all") {
                    TvActionButton(
                        stringResource(R.string.addon_ui_show_all), { showAll(entry) },
                        Modifier.focusRequester(targets.showAll(row)).then(if (list.isEmpty()) Modifier.focusRequester(targets.first(row)) else Modifier).leftToRail(list.isEmpty(), moves)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    moves.focused(row, SHOW_ALL)
                                    model.hero.focus.value = LandingFocus.Other
                                }
                            }
                            .testTag("discover-show-all-$index"),
                        compact = true,
                    )
                }
            }
        }
    }
}

internal const val SHOW_ALL = "show-all"
private const val PLACEHOLDERS = 6
