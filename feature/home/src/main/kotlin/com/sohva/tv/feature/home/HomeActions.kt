package com.sohva.tv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** An action of the hold-OK dialog (HOME-FR-40). */
internal enum class ResumeAction { CONTINUE, START_OVER, WATCHED, REMOVE }

/**
 * Hold OK on a library Continue watching card (spec 02 §4.7): Resume first, then Start from
 * beginning, Mark as watched, Remove. [onClose] runs first with the chosen action (null for Back),
 * so the screen puts focus back on the row before the dialog goes (HOME-FR-41).
 */
@Composable
internal fun ResumeActionsDialog(card: ResumeCard, onClose: (ResumeAction?) -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = { onClose(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(460.dp).roundFill(Sohva.palette.surface, Sohva.shapes.medium).padding(18.dp).testTag("home-resume-actions"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    card.title, Modifier.padding(start = 6.dp), style = Sohva.typography.headline.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = Sohva.palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val subtitle = resumeSubtitle(card.copy(minutesLeft = null))
                if (subtitle.isNotBlank()) {
                    Text(subtitle, Modifier.padding(start = 6.dp), style = Sohva.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal), color = Sohva.palette.textMuted, maxLines = 1)
                }
                val dense = ListRowLayout(dense = true)
                TvListRow(stringResource(R.string.home_resume_continue), { onClose(ResumeAction.CONTINUE) }, Modifier.focusRequester(first).testTag("home-resume-action-continue"), icon = TvIcons.Play, layout = dense)
                TvListRow(stringResource(R.string.home_resume_start_over), { onClose(ResumeAction.START_OVER) }, Modifier.testTag("home-resume-action-start-over"), icon = TvIcons.Replay, layout = dense)
                TvListRow(stringResource(R.string.home_resume_mark_watched), { onClose(ResumeAction.WATCHED) }, Modifier.testTag("home-resume-action-watched"), icon = TvIcons.Check, layout = dense)
                TvListRow(stringResource(R.string.home_resume_remove), { onClose(ResumeAction.REMOVE) }, Modifier.testTag("home-resume-action-remove"), icon = TvIcons.Delete, layout = dense)
            }
        }
        LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
    }
}
