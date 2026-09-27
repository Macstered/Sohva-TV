package com.sohva.tv.feature.home

import com.sohva.tv.ui.design.theme.Sohva
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.ground.ScreenBackground

/**
 * Home (spec 02): the hero band (46 % of the height) with the header and the hero panel, the rows
 * on their focus line below it, the rail over the left edge. Every arrival is a fresh entry: focus
 * goes to the first card once the first row exists, the loading card while it loads, or the
 * Welcome button on an empty Home (§3.4); later data never moves focus except the hand-offs of
 * HOME-FR-47, and Left to the rail cancels them.
 */
@Composable
fun HomeScreen(model: HomeModel, items: List<RailItem>, onOpen: (RailItem) -> Unit, lowMemory: Boolean, modifier: Modifier = Modifier) = trace("Home:Screen") {
    val rows by model.rows.collectAsStateWithLifecycle()
    val hero by model.hero.collectAsStateWithLifecycle()
    val details by model.details.collectAsStateWithLifecycle()
    val zone by model.timeZone.collectAsStateWithLifecycle()
    // Subscribed here, read only in lambdas: the minute tick recomposes the clock and the hero panel, not Home (§9.6).
    val nowState = model.now.collectAsStateWithLifecycle()
    val now = { nowState.value }
    val focus = remember { HomeFocus() }
    val list = rememberLazyListState()
    val railRequesters = remember(items) { items.associateWith { FocusRequester() } }
    val empty by model.empty.collectAsStateWithLifecycle()
    val firstSync by model.firstSync.collectAsStateWithLifecycle()
    // Right or Back from the rail: the card last focused, else Welcome on an empty Home (HOME-FR-83).
    val backToRows: () -> Unit = {
        val target = focus.lastCard?.let(focus::card) ?: focus.welcome.takeIf { empty }
        focus.place = HomePlace.NONE
        if (target == null || !runCatching { target.requestFocus() }.getOrDefault(false)) {
            focus.placed = false
            focus.retarget++
        }
    }
    ScreenBackground(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) focus.viaLeft = e.key == Key.DirectionLeft
                false
            }
            .testTag("screen-home"),
    ) {
        HeroBackdrop(backdrop(hero, details), lowMemory)
        (hero as? HeroSubject.Sport)?.let { SportBackdrop(it.card.event) }
        Column(Modifier.fillMaxSize().padding(start = 96.dp, end = 24.dp)) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.46f).padding(top = 16.dp, bottom = 12.dp)) {
                HomeHeader(now, zone)
                if (firstSync) {
                    // HOME-FR-48: top centre of the hero band, 8 dp from the top.
                    Text(
                        stringResource(R.string.home_trakt_first_sync), Modifier.align(Alignment.TopCenter).padding(top = 8.dp).testTag("home-trakt-first-sync"),
                        style = Sohva.typography.body, color = Sohva.palette.textMuted,
                    )
                }
                HeroPanel(
                    hero, details, now, zone, if (empty) focus.welcome else null, railRequesters.getValue(items.first()), model::openGuide,
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(0.56f),
                )
            }
            HomeRows(model, rows, focus, list, now, Modifier.fillMaxWidth().weight(1f))
        }
        HomeRail(
            items = items,
            requesters = railRequesters,
            onOpen = onOpen,
            onFocused = {
                if (focus.viaLeft) {
                    // The rail: the idle hero at once, and no hand-off takes focus from it (HOME-FR-47, -62).
                    focus.place = HomePlace.RAIL
                    model.focus(null)
                } else {
                    // A platform focus entry (window focus, a removed card) lands on content, never the rail.
                    backToRows()
                }
            },
            onExit = backToRows,
        )
    }
    BackHandler(enabled = focus.place == HomePlace.RAIL, onBack = backToRows)
    HandOffs(rows, empty, focus)
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex > 0 || focus.focusedRow > 0 }.collect(model::setLocked)
    }
}

/** HOME-FR-72: the looked-up backdrop, else a Continue watching card's own art, else the floor. */
private fun backdrop(subject: HeroSubject, details: HeroDetails?): String? =
    details?.backdrop ?: (subject as? HeroSubject.Resume)?.card?.image

/**
 * Initial focus and the hand-offs of HOME-FR-47, run when the rows change: the first placement;
 * the loading card gone; the Welcome button gone because a row arrived; the focused card gone
 * (its successor in the row, else the first card, else Welcome). Nothing while the rail has focus.
 */
@Composable
private fun HandOffs(rows: List<HomeRow>, empty: Boolean, focus: HomeFocus) {
    LaunchedEffect(rows, empty, focus.placed, focus.retarget) {
        if (focus.place == HomePlace.RAIL) return@LaunchedEffect
        val first = rows.firstOrNull()
        val entry: FocusRequester? = when {
            first is HomeRow.Status -> focus.status
            first is HomeRow.Resume -> first.cards.firstOrNull()?.let { focus.card(it.key) }
            first is HomeRow.Channels -> first.cards.firstOrNull()?.let { focus.card(it.key) }
            first is HomeRow.Sport -> first.cards.firstOrNull()?.let { focus.card(it.key) }
            first is HomeRow.Trakt -> first.cards.firstOrNull()?.let { focus.card(it.key) }
            empty -> focus.welcome
            else -> null
        }
        val keys = rows.flatMap { row ->
            when (row) {
                is HomeRow.Resume -> row.cards.map { it.key }
                is HomeRow.Channels -> row.cards.map { it.key }
                is HomeRow.Sport -> row.cards.map { it.key }
                is HomeRow.Trakt -> row.cards.map { it.key }
                is HomeRow.Status -> emptyList()
            }
        }
        val target = when {
            !focus.placed -> entry
            focus.place == HomePlace.STATUS && rows.none { it is HomeRow.Status } -> entry
            focus.place == HomePlace.WELCOME && !empty -> entry
            focus.place == HomePlace.CARD && focus.lastCard !in keys -> successor(rows, focus) ?: entry
            else -> null
        } ?: return@LaunchedEffect
        if (target.requestFocusWhenAttached()) {
            focus.placed = true
            if (target == focus.welcome) focus.place = HomePlace.WELCOME
        }
    }
}

/** The card now at the removed card's place in its row, else the one before it. */
private fun successor(rows: List<HomeRow>, focus: HomeFocus): FocusRequester? {
    val cards = when (val row = rows.getOrNull(focus.focusedRow)) {
        is HomeRow.Resume -> row.cards.map { it.key }
        is HomeRow.Channels -> row.cards.map { it.key }
        is HomeRow.Trakt -> row.cards.map { it.key }
        else -> emptyList()
    }
    val key = cards.getOrNull(focus.lastIndex) ?: cards.lastOrNull() ?: return null
    return focus.card(key)
}
