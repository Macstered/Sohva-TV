package com.sohva.tv.app.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.sync.update.UpdateNotice
import com.sohva.tv.core.sync.update.UpdateNotices
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import com.sohva.tv.ui.design.theme.UpdateNoticeDimensions
import kotlinx.coroutines.delay

/** Passive Home notice: no focus target, animation, platform permission or playback overlay. */
@Composable
fun UpdateNoticeLayer(notices: UpdateNotices, eligible: Boolean, modifier: Modifier = Modifier) {
    val pending by notices.pending.collectAsStateWithLifecycle()
    // Keep the text while the persisted marker clears pending. Leaving Home drops this small state.
    var visible by remember { mutableStateOf<UpdateNotice?>(null) }
    LaunchedEffect(eligible, pending, visible) {
        if (!eligible) visible = null
        else if (visible == null) visible = pending
    }
    val notice = visible?.takeIf { eligible } ?: return
    UpdateNoticePanel(notice.version, modifier)
    LaunchedEffect(notice.build) {
        // A deferred result is not consumed until the notice has had a drawable frame on Home.
        withFrameNanos { }
        withFrameNanos { }
        notices.shown(notice.build)
        delay(DISPLAY_MS)
        visible = null
    }
}

@Composable
private fun UpdateNoticePanel(version: String, modifier: Modifier) {
    Column(
        modifier.width(UpdateNoticeDimensions.width)
            .roundFill(Sohva.palette.surface, Sohva.shapes.medium)
            .padding(Sohva.spacing.lg)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("update-notice"),
        verticalArrangement = Arrangement.spacedBy(Sohva.spacing.sm),
    ) {
        Text(stringResource(R.string.update_available, version), style = Sohva.typography.body, color = Sohva.palette.focus, maxLines = 2)
        Text(
            stringResource(R.string.update_notice_hint, stringResource(R.string.settings_title), stringResource(R.string.settings_section_about)),
            style = Sohva.typography.label, color = Sohva.palette.textPrimary, maxLines = 3,
        )
    }
}

private const val DISPLAY_MS = 15_000L
