package com.streammate.tv.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvListRow
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.iptv.R

/**
 * What can be done with one programme, from where it sits in the grid: OK on
 * a programme that has not started, or a held OK on any, opens this. The info
 * box above the grid carries the same buttons, but from row two hundred the
 * box is a long way up.
 */
@Composable
internal fun GuideProgrammeActionsDialog(
    selection: GuideSelection,
    now: Long,
    timeZoneId: String,
    favourite: Boolean,
    reminderSet: Boolean,
    canRemind: Boolean,
    canCatchup: Boolean,
    onWatch: () -> Unit,
    onPlayCatchup: () -> Unit,
    onToggleReminder: () -> Unit,
    onToggleFavourite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val programme = selection.programme
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenAttached() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .background(palette.surface, StreamMateThemeTokens.shapes.medium)
                .padding(18.dp)
                .testTag("guide-programme-actions"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = programme?.title ?: selection.channel.name,
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                text = listOfNotNull(
                    selection.channel.name,
                    programme?.let { formatRange(it.startEpochMillis, it.stopEpochMillis, timeZoneId) },
                ).joinToString(" · "),
                color = palette.textMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            val live = programme?.isLive(now) == true
            TvListRow(
                label = stringResource(if (live || programme == null) R.string.action_watch else R.string.guide_actions_watch_channel),
                icon = TvIcons.Play,
                onClick = onWatch,
                dense = true,
                focusRequester = firstFocus,
                testTag = "guide-action-watch",
            )
            if (canCatchup) {
                TvListRow(
                    label = stringResource(if (live) R.string.guide_watch_from_start else R.string.guide_watch_recording),
                    icon = TvIcons.Replay,
                    onClick = onPlayCatchup,
                    dense = true,
                    testTag = "guide-action-catchup",
                )
            }
            if (canRemind) {
                TvListRow(
                    label = stringResource(if (reminderSet) R.string.guide_reminder_set else R.string.guide_remind),
                    icon = TvIcons.Epg,
                    onClick = onToggleReminder,
                    selected = reminderSet,
                    dense = true,
                    testTag = "guide-action-remind",
                )
            }
            TvListRow(
                label = stringResource(if (favourite) R.string.guide_favourite else R.string.guide_add_favourite),
                icon = if (favourite) TvIcons.Star else TvIcons.StarOutline,
                onClick = onToggleFavourite,
                selected = favourite,
                dense = true,
                testTag = "guide-action-favourite",
            )
        }
    }
}
