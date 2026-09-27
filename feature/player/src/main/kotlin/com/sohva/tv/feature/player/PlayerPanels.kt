package com.sohva.tv.feature.player

import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import com.sohva.tv.core.model.player.PlaybackCause
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.LogoTile
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import java.util.Locale

/** One scrim under all chrome, drawn as two bands (spec 30 §9 "Cheap overlays"). */
@Composable
internal fun ChromeScrim() {
    val ground = Sohva.palette.background
    Box(
        Modifier.fillMaxSize().drawBehind {
            val top = size.height * 0.16f
            drawRect(Brush.verticalGradient(listOf(ground.copy(alpha = 0.62f), ground.copy(alpha = 0f)), endY = top), size = size.copy(height = top))
            val bottomStart = size.height * 0.52f
            drawRect(
                Brush.verticalGradient(listOf(ground.copy(alpha = 0f), ground.copy(alpha = 0.94f)), startY = bottomStart, endY = size.height),
                topLeft = androidx.compose.ui.geometry.Offset(0f, bottomStart),
                size = size.copy(height = size.height - bottomStart),
            )
        },
    )
}

/** The dial read-out (spec 30 §5.10), below the info line when both show. */
@Composable
internal fun DialReadout(state: Pair<String?, Int?>, statsOn: Boolean) {
    val text = when {
        state.first != null -> stringResource(R.string.dial_channel, state.first!!)
        state.second != null -> stringResource(R.string.dial_channel_none, state.second!!)
        else -> return
    }
    Text(
        text,
        Modifier
            .padding(start = 40.dp, top = if (statsOn) 64.dp else 24.dp)
            .roundFill(Sohva.palette.panel.copy(alpha = 0.94f), Sohva.shapes.medium)
            .border(1.dp, Sohva.palette.outline, RoundedCornerShape(Sohva.shapes.medium))
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("player-dial"),
        style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold),
        color = Sohva.palette.textPrimary,
    )
}

/** The error banner (spec 30 §5.13): the cause in words, the detail smaller, the Reconnect button. */
@Composable
internal fun ErrorBanner(model: PlayerModel, banner: Banner, modifier: Modifier = Modifier) {
    val on = Sohva.palette.onDangerSurface
    val reconnect = remember { FocusRequester() }
    val (sentence, detail) = when (val r = banner.reason) {
        is BannerReason.Cause -> causeText(r.cause) to r.detail
        is BannerReason.ConnectionLimit -> stringResource(R.string.error_source_connection_limit, r.sourceName, r.limit) to null
        BannerReason.Unavailable -> stringResource(R.string.external_channel_unavailable) to null
        is BannerReason.ExternalFailed -> stringResource(R.string.player_external_failed, r.message) to null
    }
    val line = when {
        banner.reason !is BannerReason.Cause -> sentence
        banner.stopped -> stringResource(R.string.player_reconnect_stopped, sentence)
        else -> stringResource(R.string.player_reconnecting, sentence, banner.attempt, banner.max)
    }
    Column(
        modifier.fillMaxWidth().roundFill(Sohva.palette.dangerSurface.copy(alpha = 0.8f), 0.dp).padding(horizontal = 20.dp, vertical = 12.dp).testTag("player-banner"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(line, style = Sohva.typography.body, color = on)
        if (detail != null) Text(detail, style = Sohva.typography.caption, color = on.copy(alpha = 0.72f), maxLines = 2)
        if (banner.reason is BannerReason.Cause) {
            TvActionButton(stringResource(R.string.player_reconnect), { model.reconnect() }, Modifier.padding(top = 8.dp).focusRequester(reconnect).testTag("player-reconnect"))
        }
    }
    // When the automatic attempts stop, Reconnect takes focus (spec 30 L-26).
    LaunchedEffect(banner.stopped) { if (banner.stopped && banner.reason is BannerReason.Cause) reconnect.requestFocusWhenAttached() }
}

@Composable
private fun causeText(cause: PlaybackCause): String = stringResource(
    when (cause) {
        PlaybackCause.NETWORK -> R.string.player_cause_network
        PlaybackCause.REFUSED -> R.string.player_cause_refused
        PlaybackCause.GONE -> R.string.player_cause_gone
        PlaybackCause.SERVER -> R.string.player_cause_server
        PlaybackCause.BROKE_OFF -> R.string.player_cause_broke_off
        PlaybackCause.NOT_A_STREAM -> R.string.player_cause_not_a_stream
        PlaybackCause.DECODER -> R.string.player_cause_decoder
        PlaybackCause.NO_LONGER_AVAILABLE -> R.string.external_channel_unavailable
        PlaybackCause.CONNECTION_LIMIT, PlaybackCause.TOO_HEAVY, PlaybackCause.OTHER -> R.string.player_cause_other
    },
)

/** The audio or subtitle picker (spec 30 §5.7), opened scrolled to and focused on the marked row. */
@Composable
internal fun TrackPicker(model: PlayerModel, which: Picker) {
    val tracks by model.tracks.collectAsStateWithLifecycle()
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val marked = remember { FocusRequester() }
    val items = if (which == Picker.AUDIO) tracks.audio else tracks.text
    val type = if (which == Picker.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
    Box(Modifier.fillMaxSize().roundFill(Sohva.palette.scrim.copy(alpha = 0.65f), 0.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(390.dp, 520.dp).heightIn(max = 520.dp).roundFill(Sohva.palette.panel.copy(alpha = 0.97f), Sohva.shapes.large).padding(20.dp).testTag("player-picker")
                .keepFocusInside(),
        ) {
            Text(
                stringResource(if (which == Picker.AUDIO) R.string.player_select_audio else R.string.player_select_subtitles),
                style = Sohva.typography.headline.copy(fontWeight = FontWeight.Black, fontSize = 21.sp),
                color = Sohva.palette.textPrimary,
            )
            Text(stringResource(R.string.player_track_picker_hint), Modifier.padding(top = 4.dp, bottom = 12.dp), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (which == Picker.SUBTITLES) {
                    item {
                        val off = items.none { it.selected }
                        PickerItem(stringResource(R.string.player_subtitles_off), off, if (off) Modifier.focusRequester(marked) else Modifier) { model.chooseTrack(null, type) }
                    }
                }
                itemsIndexed(items) { i, item ->
                    PickerItem(trackLabel(item, i, locale), item.selected, if (item.selected) Modifier.focusRequester(marked) else Modifier) { model.chooseTrack(item, type) }
                }
                if (items.isEmpty()) {
                    item {
                        val none = stringResource(if (which == Picker.AUDIO) R.string.player_no_audio_tracks else R.string.player_no_subtitles)
                        TvListRow(none, {}, state = SurfaceState(enabled = false), layout = ListRowLayout(dense = true))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { marked.requestFocusWhenAttached() }
}

@Composable
private fun PickerItem(label: String, marked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    TvListRow(
        (if (marked) "●  " else "○  ") + label,
        onClick,
        modifier,
        state = SurfaceState(selected = marked),
        layout = ListRowLayout(dense = true, labelLines = 2),
    )
}

/** Distinct non-blank parts of label, language name and "N ch", else "Track N" (PLAY-FR-70). */
@Composable
internal fun trackLabel(item: TrackItem, index: Int, locale: Locale): String {
    val language = item.language?.takeIf { it.isNotBlank() && it != "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale) }
    val parts = listOfNotNull(item.label?.takeIf { it.isNotBlank() }, language?.takeIf { it.isNotBlank() }, item.channels.takeIf { it > 0 }?.let { "$it ch" }).distinct()
    return if (parts.isEmpty()) stringResource(R.string.player_track_number, index + 1) else parts.joinToString(" · ")
}

@Composable
internal fun audioLabel(tracks: Tracks): String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val at = tracks.audio.indexOfFirst { it.selected }
    return if (at < 0) stringResource(R.string.player_audio_automatic) else trackLabel(tracks.audio[at], at, locale)
}

@Composable
internal fun subtitleLabel(tracks: Tracks): String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val at = tracks.text.indexOfFirst { it.selected }
    return if (at < 0) stringResource(R.string.player_subtitles_off) else trackLabel(tracks.text[at], at, locale)
}

/** Quick actions (PLAY-FR-80, spec 30 §5.8); Picture cycles in place. */
@Composable
internal fun QuickActions(model: PlayerModel) {
    val tracks by model.tracks.collectAsStateWithLifecycle()
    val shape by model.shape.collectAsStateWithLifecycle()
    val stats by model.statsOn.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().roundFill(Sohva.palette.scrim.copy(alpha = 0.65f), 0.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(360.dp, 480.dp).roundFill(Sohva.palette.panel.copy(alpha = 0.97f), Sohva.shapes.large).padding(20.dp).testTag("player-quick"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.player_quick_actions_title), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Black, fontSize = 18.sp), color = Sohva.palette.textPrimary)
            TvListRow(stringResource(R.string.player_quick_audio), { model.openPicker(Picker.AUDIO) }, Modifier.focusRequester(first), trailing = audioLabel(tracks))
            TvListRow(stringResource(R.string.player_quick_subtitles), { model.openPicker(Picker.SUBTITLES) }, trailing = subtitleLabel(tracks), layout = ListRowLayout(divider = true))
            TvListRow(stringResource(R.string.player_quick_picture), { model.cycleShape() }, Modifier.testTag("player-quick-picture"), trailing = shapeLabel(shape), layout = ListRowLayout(divider = true))
            TvListRow(
                stringResource(R.string.player_quick_stats),
                {
                    model.toggleStats()
                    model.closeQuickActions()
                },
                Modifier.testTag("player-quick-stats"),
                trailing = stringResource(if (stats) R.string.player_quick_stats_on else R.string.player_quick_stats_off),
                layout = ListRowLayout(divider = true),
            )
            model.ticker?.let { ticker ->
                val on by ticker.shown.collectAsStateWithLifecycle()
                TvListRow(
                    stringResource(R.string.player_quick_ticker),
                    {
                        model.toggleTicker()
                        model.closeQuickActions()
                    },
                    Modifier.testTag("player-quick-ticker"),
                    trailing = stringResource(if (on) R.string.player_quick_stats_on else R.string.player_quick_stats_off),
                    layout = ListRowLayout(divider = true),
                )
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
}

/** The channel list and group list down the right edge (spec 30 §5.6); selection is painted, not focused. */
@Composable
internal fun ChannelListPanel(model: PlayerModel, modifier: Modifier = Modifier) {
    val view by model.channels.view.collectAsStateWithLifecycle()
    val groups by model.channels.groups.collectAsStateWithLifecycle()
    Row(modifier.testTag("player-channels")) {
        view?.let { ChannelColumn(it, model.settings.showChannelNumbers, groups == null) }
        groups?.let { GroupColumn(it) }
    }
}

@Composable
private fun ChannelColumn(view: ChannelListView, numbers: Boolean, active: Boolean) {
    val p = Sohva.palette
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    Column(
        Modifier.width(330.dp).fillMaxHeight().drawBehind {
            drawRect(Brush.horizontalGradient(0f to p.background.copy(alpha = 0f), 0.22f to p.background.copy(alpha = 0.82f), 1f to p.background.copy(alpha = 0.97f)))
        }.padding(start = 28.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, bottom = 12.dp)) {
            Text(stringResource(R.string.player_channels).uppercase(), Modifier.weight(1f), style = Sohva.typography.overline.copy(fontWeight = FontWeight.Bold), color = p.textDim)
            Text(stringResource(R.string.guide_groups) + " →", style = Sohva.typography.caption, color = p.textDim)
        }
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(view.rows, key = { _, row -> row.key }) { i, row ->
                val index = view.firstIndex + i
                val selected = index == view.selected
                val fill = when {
                    selected && active -> p.textPrimary
                    selected -> p.surfaceFocused
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
                val ink = selected && active
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 66.dp).roundFill(fill, Sohva.shapes.medium).padding(horizontal = 10.dp).testTag("player-channel-$index"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (numbers) {
                        Text(
                            (row.number ?: (index + 1)).toString(),
                            Modifier.widthIn(min = 28.dp).padding(end = 8.dp),
                            style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = if (ink) p.background.copy(alpha = 0.7f) else p.textDim,
                        )
                    }
                    LogoTile(row.name, row.logoUrl, 44.dp, fontSize = 14.sp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(row.name, style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold), color = if (ink) p.background else p.textPrimary, maxLines = 1)
                        Text(
                            view.nowTitles[row.key] ?: stringResource(R.string.player_no_current_programme),
                            style = Sohva.typography.caption,
                            color = if (ink) p.background.copy(alpha = 0.62f) else p.textDim,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    // The highlight walks to the middle before the list moves, in the same key press (PLAY-FR-54).
    LaunchedEffect(view.selected, view.firstIndex) {
        val rows = state.layoutInfo.visibleItemsInfo.size.takeIf { it > 0 } ?: 7
        state.scrollToItem(maxOf(0, view.selected - view.firstIndex - rows / 2))
    }
}

@Composable
private fun GroupColumn(view: GroupListView) {
    val p = Sohva.palette
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    Column(
        Modifier.width(220.dp).fillMaxHeight().drawBehind {
            drawRect(Brush.horizontalGradient(0f to p.background.copy(alpha = 0.76f), 1f to p.background.copy(alpha = 0.97f)))
        }.padding(start = 18.dp, end = 12.dp, top = 24.dp, bottom = 20.dp),
    ) {
        Text(stringResource(R.string.guide_groups).uppercase(), Modifier.padding(start = 10.dp, bottom = 12.dp), style = Sohva.typography.overline.copy(fontWeight = FontWeight.Bold), color = p.textDim)
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(view.groups, key = { _, g -> g.id }) { i, g ->
                val selected = i == view.selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).roundFill(if (selected) p.textPrimary else androidx.compose.ui.graphics.Color.Transparent, Sohva.shapes.medium).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(g.name, Modifier.weight(1f), style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold), color = if (selected) p.background else p.textPrimary, maxLines = 2)
                    Text(g.itemCount.toString(), Modifier.padding(start = 8.dp), style = Sohva.typography.caption, color = if (selected) p.background.copy(alpha = 0.62f) else p.textDim)
                }
            }
        }
    }
    LaunchedEffect(view.selected) {
        val rows = state.layoutInfo.visibleItemsInfo.size.takeIf { it > 0 } ?: 10
        state.scrollToItem(maxOf(0, view.selected - rows / 2))
    }
}

/**
 * A panel over the picture keeps the D-pad inside it (AGENTS.md §5 rule 2): a move past its edge
 * goes nowhere instead of to the transport controls behind it.
 */
internal fun Modifier.keepFocusInside(): Modifier =
    focusProperties { onExit = { if (requestedFocusDirection in KEY_MOVES) cancelFocusChange() } }.focusGroup()

/** Nothing outside may take focus while a panel is up: not by the D-pad, not when the focused row goes away. */
internal fun Modifier.refuseFocus(refuse: Boolean): Modifier =
    if (refuse) focusProperties { onEnter = { cancelFocusChange() } }.focusGroup() else this

private val KEY_MOVES = setOf(FocusDirection.Left, FocusDirection.Right, FocusDirection.Up, FocusDirection.Down, FocusDirection.Next, FocusDirection.Previous)
