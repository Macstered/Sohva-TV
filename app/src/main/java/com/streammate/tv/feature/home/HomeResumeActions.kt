package com.streammate.tv.feature.home

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
import com.streammate.tv.R
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvListRow
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.iptv.repository.ContinueWatchingItem

/**
 * What can be done with a title in Continue watching, from a held OK on its
 * card: pick up, start over, call it watched, or take it off the row.
 */
@Composable
internal fun HomeResumeActionsDialog(
    item: ContinueWatchingItem,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onMarkWatched: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(item.contentKey) { firstFocus.requestFocusWhenAttached() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .background(palette.surface, StreamMateThemeTokens.shapes.medium)
                .padding(18.dp)
                .testTag("home-resume-actions"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
            item.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    color = palette.textMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                )
            }
            TvListRow(
                label = stringResource(R.string.home_resume_continue),
                icon = TvIcons.Play,
                onClick = onResume,
                dense = true,
                focusRequester = firstFocus,
                testTag = "home-resume-action-continue",
            )
            TvListRow(
                label = stringResource(R.string.home_resume_start_over),
                icon = TvIcons.Replay,
                onClick = onStartOver,
                dense = true,
                testTag = "home-resume-action-start-over",
            )
            TvListRow(
                label = stringResource(R.string.home_resume_mark_watched),
                icon = TvIcons.Check,
                onClick = onMarkWatched,
                dense = true,
                testTag = "home-resume-action-watched",
            )
            TvListRow(
                label = stringResource(R.string.home_resume_remove),
                icon = TvIcons.Delete,
                onClick = onRemove,
                dense = true,
                testTag = "home-resume-action-remove",
            )
        }
    }
}
