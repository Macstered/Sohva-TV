package com.sohva.tv.feature.sport.today

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.TodayFilter
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.feature.sport.hub.MatchHubOverlay
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SohvaSportBrand
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.text.rememberTimeStyle
import com.sohva.tv.ui.design.theme.Sohva
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sohva Sport's Today (spec 60 §4.5, design D§1–7): the header, the tabs, and Live now, Later today
 * and Finished. Polling runs only while this screen is resumed (SPORT-FR-27). Focus moves on entry
 * and after a tab is chosen; data arriving never moves it (SPORT-FR-60 rebuild). OK on a card opens
 * the match hub over the list (SPORT-FR-70).
 */
@Composable
fun TodayScreen(model: TodayModel) {
    val view by model.view.collectAsStateWithLifecycle()
    val hub by model.hub.collectAsStateWithLifecycle()
    val hubEvent by model.hubEvent.collectAsStateWithLifecycle()
    // The tab and the open hub survive the player and process recreation (SPORT-FR-47, SPORT-NAV-02).
    var savedTab by rememberSaveable { mutableStateOf<String?>(null) }
    var savedHub by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(model) { model.restore(savedTab, savedHub) }
    LaunchedEffect(view.filter) { savedTab = view.filter.key }
    LaunchedEffect(hub) { savedHub = hub }
    LifecycleResumeEffect(model) {
        model.setVisible(true)
        onPauseOrDispose { model.setVisible(false) }
    }
    val cards = remember { HashMap<String, FocusRequester>() }
    // Entry or a chosen tab is waiting for its first game to focus (SPORT-FR-60).
    var awaitingGame by remember { mutableStateOf(true) }
    val tabs = remember { HashMap<String, FocusRequester>() }
    fun card(id: String) = cards.getOrPut(id) { FocusRequester() }
    fun tab(key: String) = tabs.getOrPut(key) { FocusRequester() }

    // SPORT-NAV-05 rebuild: focus goes back to the game's card (the one that opened the hub), else the
    // first game, else the tab, and is placed before the hub hides (AGENTS.md §5 rule 2).
    fun closeHub() {
        val id = hub
        val target = listOfNotNull(id?.takeIf { view.sections.contains(it) }, view.sections.firstFocus?.id).map(::card) + tab(view.filter.key)
        target.firstOrNull { runCatching { it.requestFocus() }.getOrDefault(false) }
        model.closeHub()
    }
    BackHandler(enabled = hub != null) { closeHub() }
    val style = rememberTimeStyle()
    val labels = remember(view.zoneId, style) { TimeLabels(TimeLabels.zoneOf(view.zoneId), style) }
    val covering = remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // Behind the fully shown hub only the background is drawn: the 94 % scrim hides the rest (§9 "Drawing").
        Column(Modifier.fillMaxSize().background(Sohva.palette.background).drawWithContent { if (!covering.value) drawContent() }.padding(horizontal = 40.dp, vertical = 24.dp).testTag("screen-today")) {
            Header(model, view.zoneId)
            Spacer(Modifier.height(20.dp))
            Tabs(view, model::select, ::tab) { awaitingGame = false }
            view.notice?.let {
                Text(noticeText(it), Modifier.padding(top = 8.dp).testTag("today-notice"), style = Sohva.typography.caption, color = Sohva.palette.accent)
            }
            Spacer(Modifier.height(22.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                Body(view, model, labels, ::card)
            }
        }
        MatchHubOverlay(model, labels, { view.watch[it]?.available ?: 0 }, covering, ::closeHub)
    }
    // A refresh that drops the hub's game closes the hub (SPORT-FR-70).
    LaunchedEffect(hub, hubEvent == null, view.complete, view.loading) {
        if (hub != null && hubEvent == null && view.complete && !view.loading) closeHub()
    }
    val turn by model.focusTurn.collectAsStateWithLifecycle()
    val ready = view.complete || view.anyEvents
    val first = view.sections.firstFocus?.id
    LaunchedEffect(turn) { awaitingGame = true }
    LaunchedEffect(turn, ready, first) {
        if (!ready || !awaitingGame) return@LaunchedEffect
        // A game opened from Home or a reminder: the hub takes focus instead (SPORT-FR-79).
        if (hub != null) {
            awaitingGame = false
            return@LaunchedEffect
        }
        // The first live game, else upcoming, else finished (SPORT-FR-60). With none yet, the selected
        // tab holds focus and the first game takes it when it arrives, unless the viewer has moved on:
        // data never moves focus away from something the viewer chose (rebuild).
        if (first != null) {
            awaitingGame = false
            card(first).requestFocusWhenAttached()
        } else {
            tab(view.filter.key).requestFocusWhenAttached()
        }
    }
}

@Composable
private fun Header(model: TodayModel, zoneId: String) {
    val now by model.now.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SohvaSportBrand()
        Spacer(Modifier.width(18.dp))
        Text(clock(now, zoneId), Modifier.weight(1f).testTag("today-clock"), style = Sohva.typography.body, color = Sohva.palette.textDim)
        IconAction(TvIcons.Refresh, stringResource(R.string.today_refresh_description), "today-refresh", model::refresh)
        Spacer(Modifier.width(10.dp))
        IconAction(TvIcons.Guide, stringResource(R.string.today_guide_description), "today-guide", model::openGuide)
        Spacer(Modifier.width(10.dp))
        IconAction(TvIcons.Settings, stringResource(R.string.today_settings_description), "today-settings", model::openSettings)
    }
}

/** A 44 dp icon action (D§2): `surfaceSubtle` at rest, the off-white fill on focus. */
@Composable
private fun IconAction(icon: Int, description: String, tag: String, onClick: () -> Unit) {
    val style = SurfaceStyle(corner = Sohva.shapes.small, resting = Sohva.palette.surfaceSubtle, restingContent = Sohva.palette.textMuted, focusScale = 1f, contentAlignment = Alignment.Center)
    TvSurface(onClick, Modifier.size(44.dp).testTag(tag).semantics { contentDescription = description }, style = style) { colors ->
        androidx.compose.foundation.Image(painterResource(icon), null, Modifier.size(20.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.content))
    }
}

/** "Saturday 26 September  ·  21.40" in the app zone and the TV's 12/24-hour setting (SPORT-FR-45). */
@Composable
private fun clock(now: Long, zoneId: String): String {
    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val formatter = remember(locale, zoneId) {
        val day = DateFormat.getBestDateTimePattern(locale, "EEEEdMMM")
        val time = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hmma")
        DateTimeFormatter.ofPattern("$day'  ·  '$time", locale).withZone(TimeLabels.zoneOf(zoneId))
    }
    return formatter.format(Instant.ofEpochMilli(now))
}

@Composable
private fun Body(view: TodayView, model: TodayModel, labels: TimeLabels, card: (String) -> FocusRequester) {
    val sections = view.sections
    when {
        !view.anyEvents && view.failure != null -> ErrorCard(problemText(view.failure), model::refresh, model::openSettings, view.failure == com.sohva.tv.core.model.sport.SportsProblem.KEY_MISSING)
        !view.anyEvents && !view.complete -> MessageCard(stringResource(R.string.today_loading), stringResource(R.string.today_connecting), "today-loading")
        sections.isEmpty -> MessageCard(emptyText(view.filter), null, "today-empty")
        else -> {
            Section(stringResource(R.string.today_section_live), sections.live, view, labels, model::openHub, card, "today-live")
            Section(stringResource(R.string.today_section_later), sections.later, view, labels, model::openHub, card, "today-later")
            SportsChannelSection(view.channels, labels, model::play)
            Section(stringResource(R.string.today_section_finished), sections.finished, view, labels, model::openHub, card, "today-finished")
        }
    }
}

/** A section title with its game count (the static "This evening" hint is a D§10 flaw), then a lazy row of cards. */
@Composable
private fun Section(title: String, events: List<SportEvent>, view: TodayView, labels: TimeLabels, onOpen: (SportEvent) -> Unit, card: (String) -> FocusRequester, tag: String) {
    if (events.isEmpty()) return
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
            Spacer(Modifier.width(12.dp))
            Text(pluralStringResource(R.plurals.today_match_count, events.size, events.size), style = Sohva.typography.label, color = Sohva.palette.textDim)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(Modifier.fillMaxWidth().testTag(tag), horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
            items(events, key = { it.id }) { event ->
                MatchCard(
                    event, labels.guideTime(event.startMillis), view.watch[event.id], event.id in view.favourites, { onOpen(event) },
                    Modifier.focusRequester(card(event.id)).testTag("today-card-${event.id}"),
                )
            }
        }
    }
}

/** A tab (D§3): the fill means focused, the orange rule means selected, never the same signal. */
@Composable
private fun Tabs(view: TodayView, select: (TodayFilter) -> Unit, tab: (String) -> FocusRequester, viewerMoved: () -> Unit) {
    LazyRow(Modifier.fillMaxWidth().testTag("today-tabs"), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        items(view.tabs, key = { it.filter.key }) { t ->
            val selected = t.filter == view.filter
            val style = SurfaceStyle(corner = Sohva.shapes.small, restingContent = if (selected) Sohva.palette.textPrimary else Sohva.palette.textMuted, focusScale = 1f, padding = PaddingValues(10.dp, 7.dp))
            // The selected tab losing focus means the viewer moved (or a game took it): stop waiting.
            val watch = if (selected) Modifier.onFocusChanged { if (!it.isFocused) viewerMoved() } else Modifier
            TvSurface({ select(t.filter) }, watch.focusRequester(tab(t.filter.key)).testTag("today-tab-${t.filter.key}"), style = style) { colors ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Bold is kept at every state so a tab never changes width (D§10).
                        Text(filterLabel(t.filter), style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = colors.content)
                        Spacer(Modifier.width(7.dp))
                        Text("${t.count}", style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold), color = if (colors.focused) colors.secondaryContent else Sohva.palette.textDim)
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.width(24.dp).height(2.dp).background(
                            when {
                                selected && colors.focused -> Sohva.palette.background
                                selected -> Sohva.palette.accent
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(title: String, detail: String?, tag: String) {
    Column(
        Modifier.fillMaxWidth().height(180.dp).background(Sohva.palette.surface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(24.dp).testTag(tag),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
        detail?.let { Text(it, Modifier.padding(top = 6.dp), style = Sohva.typography.label, color = Sohva.palette.textMuted) }
    }
}

/** The error card (SPORT-FR-54) with the cause named (§7 rebuild); without a key, Settings is one press away. */
@Composable
private fun ErrorCard(cause: String, retry: () -> Unit, settings: () -> Unit, needsKey: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(Sohva.palette.surface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(24.dp).testTag("today-error"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.error_sports_unavailable), style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
        Text(cause, Modifier.testTag("today-error-cause"), style = Sohva.typography.label, color = Sohva.palette.danger)
        Text(stringResource(R.string.today_error_help), style = Sohva.typography.label, color = Sohva.palette.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvActionButton(stringResource(R.string.action_retry), retry, Modifier.testTag("today-retry"), TvIcons.Refresh, compact = true)
            if (needsKey) TvActionButton(stringResource(R.string.today_settings_description), settings, Modifier.testTag("today-open-settings"), TvIcons.Settings, compact = true)
        }
    }
}
