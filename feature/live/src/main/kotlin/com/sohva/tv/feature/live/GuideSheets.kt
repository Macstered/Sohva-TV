package com.sohva.tv.feature.live

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.KeyHints
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.OptionsSheet
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The options sheet (GUIDE-FR-93..94, guide.md §5). Focus is placed by [onClosed] before the
 * sheet hides, so Compose never hands it to the hero's Watch.
 */
@Composable
internal fun GuideOptions(model: GuideModel, navigation: GuideNavigation, onClosed: (GuideOverlays.SheetReturn) -> Unit) {
    val source by model.source.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    val close = { onClosed(model.overlays.closeOptions()) }
    OptionsSheet(stringResource(R.string.guide_options_title), Modifier.testTag("guide-options"), subtitle = stringResource(R.string.guide_subtitle)) {
        val full = Modifier.fillMaxWidth()
        TvActionButton(
            stringResource(R.string.guide_filter_source, source?.name.orEmpty()),
            { model.switchSource() },
            full.focusRequester(first).testTag("guide-options-source"),
            icon = TvIcons.Channels,
        )
        TvActionButton(
            stringResource(R.string.guide_sort, stringResource(R.string.guide_sort_playlist)),
            { navigation.notYetAvailable() },
            full,
            icon = TvIcons.Guide,
        )
        TvActionButton(stringResource(R.string.category_edit), { navigation.notYetAvailable() }, full, icon = TvIcons.Check)
        TvActionButton(stringResource(R.string.guide_channels), { navigation.openChannels() }, full.testTag("guide-options-channels"), icon = TvIcons.Channels)
        TvActionButton(stringResource(R.string.guide_settings), {
            model.overlays.closeOptions()
            navigation.openSettings()
        }, full, icon = TvIcons.Settings)
        TvActionButton(stringResource(R.string.action_back), {
            model.overlays.closeOptions()
            navigation.leave()
        }, full, icon = TvIcons.Back)
        TvActionButton(stringResource(R.string.guide_close_options), close, full.testTag("guide-options-close"), icon = TvIcons.Close)
    }
    LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
}

/**
 * The programme actions dialog (GUIDE-FR-79, guide.md §6): Watch, the archive row when the
 * programme can be played from it, Remind me for one that has not started, then Favourite. On
 * close, focus returns to the block it came from.
 */
@Composable
internal fun GuideActionsDialog(model: GuideModel, target: ActionsTarget, actions: RowActions, onDismiss: () -> Unit) {
    val favourites by model.favourites.collectAsStateWithLifecycle()
    val reminders by model.reminders.collectAsStateWithLifecycle()
    val labels by model.labels.collectAsStateWithLifecycle()
    val watch = remember { FocusRequester() }
    val programme = target.programme
    val live = programme.isLive(model.nowState.longValue)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(460.dp).roundFill(Sohva.palette.surface, Sohva.shapes.medium).padding(18.dp).testTag("guide-actions"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(programme.title, style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp), color = Sohva.palette.textPrimary, maxLines = 2)
            Text(
                target.row.name + " · " + labels.guideRange(programme.start, programme.stop),
                style = Sohva.typography.label.copy(fontSize = 13.sp),
                color = Sohva.palette.textMuted,
                maxLines = 1,
            )
            TvListRow(
                stringResource(if (live) R.string.action_watch else R.string.guide_actions_watch_channel),
                {
                    onDismiss()
                    actions.play(target.row)
                },
                Modifier.focusRequester(watch).testTag("guide-actions-watch"),
                icon = TvIcons.Play,
                layout = ListRowLayout(dense = true),
            )
            if (target.row.offersArchive(programme, model.nowState.longValue)) {
                TvListRow(
                    stringResource(if (live) R.string.guide_watch_from_start else R.string.guide_watch_recording),
                    {
                        onDismiss()
                        actions.playArchive(target.row, programme)
                    },
                    Modifier.testTag("guide-actions-archive"),
                    icon = TvIcons.Replay,
                    layout = ListRowLayout(dense = true),
                )
            }
            if (model.remindersOn && programme.start > model.nowState.longValue) {
                val set = model.reminderId(target.row, programme) in reminders
                TvListRow(
                    stringResource(if (set) R.string.guide_reminder_set else R.string.guide_remind),
                    {
                        model.toggleReminder(target.row, programme)
                        onDismiss()
                    },
                    Modifier.testTag("guide-actions-reminder"),
                    icon = TvIcons.Epg,
                    state = SurfaceState(selected = set),
                    layout = ListRowLayout(dense = true),
                )
            }
            val favourite = target.row.key in favourites
            TvListRow(
                stringResource(if (favourite) R.string.guide_favourite else R.string.guide_add_favourite),
                { model.toggleFavourite(target.row.key) },
                Modifier.testTag("guide-actions-favourite"),
                icon = if (favourite) TvIcons.Star else TvIcons.StarOutline,
                state = SurfaceState(selected = favourite),
                layout = ListRowLayout(dense = true),
            )
        }
        LaunchedEffect(Unit) { watch.requestFocusWhenAttached() }
    }
}

/** The dial read-out (guide.md §7): top-right, no animation. */
@Composable
internal fun DialOverlay(state: DialState, modifier: Modifier = Modifier) {
    val text = when {
        state.digits != null -> stringResource(R.string.dial_channel, state.digits)
        state.notFound != null -> stringResource(R.string.dial_channel_none, state.notFound)
        else -> return
    }
    Text(
        text,
        modifier
            .roundFill(Sohva.palette.panel.copy(alpha = 0.94f), Sohva.shapes.medium)
            .border(1.dp, Sohva.palette.outline, RoundedCornerShape(Sohva.shapes.medium))
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("guide-dial"),
        style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold),
        color = Sohva.palette.textPrimary,
        maxLines = 1,
    )
}

/** Only bindings that work (GUIDE-FR-77). */
@Composable
internal fun GuideKeyHints(modifier: Modifier = Modifier) {
    KeyHints(
        listOf(
            "◀ ▶" to stringResource(R.string.guide_hint_time),
            "▲ ▼" to stringResource(R.string.guide_hint_channel),
            "OK" to stringResource(R.string.guide_hint_watch),
            "⏮ ⏭" to stringResource(R.string.guide_hint_day),
            "MENU" to stringResource(R.string.guide_hint_options),
        ),
        modifier,
    )
}

