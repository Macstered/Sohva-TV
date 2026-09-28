package com.sohva.tv.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.components.homeRowTitle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** Where focus is on Home, for the hand-offs of HOME-FR-47. */
internal enum class HomePlace { NONE, STATUS, WELCOME, CARD, RAIL }

/**
 * Home's focus bookkeeping for one entry: one requester per card (≤ 18 cards, spec 02 §9.6), the
 * status card's and Welcome's, the last card focused (the rail returns to it) and its row.
 */
@Stable
internal class HomeFocus {
    private val cards = HashMap<String, FocusRequester>()
    val status = FocusRequester()
    val welcome = FocusRequester()
    var place by mutableStateOf(HomePlace.NONE)

    /**
     * The viewer has pressed a key on this Home entry. Until then the first focus follows the first
     * row as rows arrive; after it, arriving data never moves focus (spec 02 HOME-FR-93).
     */
    var touched = false
    var lastCard: String? = null
    var lastIndex = 0
    var focusedRow by mutableIntStateOf(0)
    var placed = false

    /** Left was pressed: the rail is the viewer's choice, not a platform focus re-entry (HOME-FR-47). */
    var viaLeft = false

    /**
     * The rail entry Left goes to from a row's first card. Named, not searched: the rail is laid out
     * at its open width, so a card narrower than that (a poster) has no rail item wholly to its left.
     */
    var rail: FocusRequester? = null

    /** Bumped when focus must be placed again (a platform entry before any card could take it). */
    var retarget by mutableIntStateOf(0)

    fun card(key: String): FocusRequester = cards.getOrPut(key) { FocusRequester() }
}

/**
 * The focus line (HOME-FR-81): a focused card's top goes to the top of the row area, so the row's
 * own title scrolls out above and the next row's title shows below. The rows keep the TV pivot.
 */
@OptIn(ExperimentalFoundationApi::class)
private class FocusLine(private val slackPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - slackPx
}

/** The rows (spec 02 §5): 26 dp apart, a header over each row of cards, 260 dp slack at the end. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeRows(model: HomeModel, rows: List<HomeRow>, focus: HomeFocus, list: LazyListState, now: () -> Long, modifier: Modifier) {
    val pivot = LocalBringIntoViewSpec.current
    val line = FocusLine(with(LocalDensity.current) { 6.dp.toPx() })
    CompositionLocalProvider(LocalBringIntoViewSpec provides line) {
        LazyColumn(
            modifier.testTag("home-rows"),
            state = list,
            contentPadding = PaddingValues(top = 12.dp, bottom = 260.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                CompositionLocalProvider(LocalBringIntoViewSpec provides pivot) {
                    HomeRowView(model, row, index, focus, now)
                }
            }
        }
    }
}

@Composable
private fun HomeRowView(model: HomeModel, row: HomeRow, rowIndex: Int, focus: HomeFocus, now: () -> Long) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            val title = when (row) {
                is HomeRow.Channels -> R.string.home_recent_channels
                is HomeRow.Sport -> R.string.home_sports_today
                is HomeRow.Trakt -> homeRowTitle(row.id)
                else -> R.string.home_continue_watching
            }
            Text(stringResource(title), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary, maxLines = 1)
            if (row is HomeRow.Resume) {
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.home_rows_hint), style = Sohva.typography.label, color = Sohva.palette.textDim, maxLines = 1)
            }
            if (row is HomeRow.Sport) {
                // All of today's games are counted, not only the six shown (HOME-FR-01 table).
                Spacer(Modifier.width(12.dp))
                Text(pluralStringResource(R.plurals.home_sports_count, row.total, row.total), style = Sohva.typography.label, color = Sohva.palette.textDim, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        when (row) {
            is HomeRow.Status -> StatusCard(
                row.failed,
                Modifier
                    .focusRequester(focus.status)
                    .onFocusChanged { if (it.isFocused) focus.place = HomePlace.STATUS }
                    // While loading, Down waits here so focus cannot drop to a row that then moves (HOME-FR-45).
                    .onPreviewKeyEvent { e -> e.type == KeyEventType.KeyDown && ((e.key == Key.DirectionDown && !row.failed) || e.key == Key.DirectionUp) }
                    .testTag("home-resume-status"),
            ) { if (row.failed) model.retryResume() }
            is HomeRow.Resume -> CardRow(row.cards, rowIndex) { index, card ->
                val req = remember { focus.card(card.key) }
                var actions by remember { mutableStateOf(false) }
                ResumeCardView(
                    card,
                    Modifier.card(model, focus, req, card.key, rowIndex, index, HeroSubject.Resume(card)).testTag("home-resume-${card.key}"),
                    onClick = { model.open(card) },
                    // Discover cards have no long press (spec 02 §3.2).
                    onLongClick = { if (!card.isDiscover) actions = true },
                )
                if (actions) {
                    ResumeActionsDialog(card) { action ->
                        // Focus goes back to the card before the dialog goes (HOME-FR-41).
                        req.requestFocus()
                        actions = false
                        when (action) {
                            ResumeAction.CONTINUE -> model.open(card)
                            ResumeAction.START_OVER -> model.open(card, fromStart = true)
                            ResumeAction.WATCHED -> model.markWatched(card)
                            ResumeAction.REMOVE -> model.remove(card)
                            null -> Unit
                        }
                    }
                }
            }
            is HomeRow.Sport -> CardRow(row.cards, rowIndex) { index, card ->
                val req = remember { focus.card(card.key) }
                val zone by model.timeZone.collectAsStateWithLifecycle()
                SportCardView(
                    card, zone,
                    Modifier.card(model, focus, req, card.key, rowIndex, index, HeroSubject.Sport(card)).testTag("home-sport-${card.event.id}"),
                ) { model.open(card) }
            }
            is HomeRow.Trakt -> CardRow(row.cards, rowIndex) { index, card ->
                val req = remember { focus.card(card.key) }
                TraktCardView(
                    card,
                    Modifier.card(model, focus, req, card.key, rowIndex, index, HeroSubject.Trakt(card)).testTag("home-trakt-${card.key}"),
                ) { model.open(card) }
            }
            is HomeRow.Channels -> CardRow(row.cards, rowIndex) { index, card ->
                val req = remember { focus.card(card.key) }
                ChannelCardView(
                    card, now,
                    Modifier.card(model, focus, req, card.key, rowIndex, index, HeroSubject.Channel(card)).testTag("home-channel-${card.channel.id}"),
                ) { model.open(card) }
            }
        }
    }
}

@Composable
private fun <T : Any> CardRow(cards: List<T>, rowIndex: Int, card: @Composable (Int, T) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        // 4 dp at both ends so the first and last card's ring is not clipped (HOME-FR-82).
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        modifier = Modifier.testTag("home-row-$rowIndex"),
    ) {
        itemsIndexed(cards, key = { _, c -> (c as? ResumeCard)?.key ?: (c as? SportCard)?.key ?: (c as? TraktCard)?.key ?: (c as ChannelCard).key }) { index, c -> card(index, c) }
    }
}

/**
 * Focus reports go to the model outside composition (spec 02 §9.6): the hero after its rest, the
 * lock, the card the rail returns to. Up on the first row does nothing (HOME-FR-84).
 */
private fun Modifier.card(model: HomeModel, focus: HomeFocus, req: FocusRequester, key: String, row: Int, index: Int, subject: HeroSubject): Modifier =
    this
        .focusRequester(req)
        .onFocusChanged {
            if (it.isFocused) {
                focus.place = HomePlace.CARD
                focus.lastCard = key
                focus.lastIndex = index
                focus.focusedRow = row
                // Only the viewer's own moves count: on a return the platform focuses the first card
                // before Home places focus, which would overwrite the card to come back to (HOME-FR-97).
                if (focus.placed) {
                    model.returnCard = key
                    model.returnRow = model.rows.value.getOrNull(row)?.key
                }
                model.focus(subject)
            }
        }
        .focusProperties { if (index == 0) focus.rail?.let { left = it } }
        .onPreviewKeyEvent { e -> e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp && row == 0 }
