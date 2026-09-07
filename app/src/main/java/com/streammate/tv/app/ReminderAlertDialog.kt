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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.streammate.tv.R
import com.streammate.tv.core.database.ReminderEntity
import com.streammate.tv.core.reminders.ReminderSchedule
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvListRow
import com.streammate.tv.feature.common.requestFocusWhenAttached
import kotlinx.coroutines.delay

/**
 * A fired reminder, over whatever is on: watch now, or not. It goes away by
 * itself once the programme is well under way, so a TV nobody is looking at
 * does not keep it up for hours.
 */
@Composable
fun ReminderAlertDialog(
    reminder: ReminderEntity,
    now: Long,
    onWatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(reminder.id) { firstFocus.requestFocusWhenAttached() }
    LaunchedEffect(reminder.id) {
        delay((reminder.startEpochMillis + REMINDER_ALERT_LINGER_MILLIS - System.currentTimeMillis()).coerceAtLeast(REMINDER_ALERT_MIN_MILLIS))
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .background(palette.surface, StreamMateThemeTokens.shapes.medium)
                .padding(18.dp)
                .testTag("reminder-alert"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    if (reminder.startEpochMillis > now) R.string.reminder_alert_title_soon else R.string.reminder_alert_title_now,
                    reminder.title,
                ),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
            reminder.subtitle?.let { subtitle ->
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
                label = stringResource(if (reminder.channelId != null) R.string.reminder_alert_watch else R.string.reminder_alert_open_card),
                icon = TvIcons.Play,
                onClick = onWatch,
                dense = true,
                focusRequester = firstFocus,
                testTag = "reminder-alert-watch",
            )
            TvListRow(
                label = stringResource(R.string.reminder_alert_later),
                onClick = onDismiss,
                dense = true,
                testTag = "reminder-alert-later",
            )
        }
    }
}

/** How long after the start the alert stays up when nobody answers it. */
const val REMINDER_ALERT_LINGER_MILLIS = 2L * 60_000L
/** The least an alert stays up, however late it fired. */
const val REMINDER_ALERT_MIN_MILLIS = 20_000L
