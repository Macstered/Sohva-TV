package com.streammate.tv.app

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.streammate.tv.R
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvListRow
import com.streammate.tv.feature.common.requestFocusWhenAttached

/**
 * Shown once, with the first reminder: what a reminder can and cannot do on
 * a TV, and the one setting that lets it open the app over something else.
 */
@Composable
fun ReminderOverlayPromptDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val palette = StreamMateThemeTokens.palette
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenAttached() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(palette.surface, StreamMateThemeTokens.shapes.medium)
                .padding(18.dp)
                .testTag("reminder-overlay-prompt"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.reminder_overlay_title),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                text = stringResource(R.string.reminder_overlay_body),
                color = palette.textMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            TvListRow(
                label = stringResource(R.string.reminder_overlay_open),
                icon = TvIcons.Settings,
                onClick = onOpenSettings,
                dense = true,
                focusRequester = firstFocus,
                testTag = "reminder-overlay-open",
            )
            TvListRow(
                label = stringResource(R.string.reminder_alert_later),
                onClick = onDismiss,
                dense = true,
                testTag = "reminder-overlay-later",
            )
        }
    }
}
