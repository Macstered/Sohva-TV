package com.sohva.tv.feature.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.player.SubtitleText
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Subtitle sync (spec 50 FR-102, §5.6): the upper screen, the video visible below. The wide
 * control has focus: Left/Right move the draft 0.1 s (1 s held), OK applies; Down reaches Apply.
 * Back returns to the picker, still paused.
 */
@Composable
internal fun SubtitleSyncPanel(model: PlayerModel, subs: AddonSubtitles, modifier: Modifier) {
    val sync by subs.sync.collectAsStateWithLifecycle()
    val control = remember { FocusRequester() }
    val apply = remember { FocusRequester() }
    Column(
        modifier.padding(top = 24.dp).fillMaxWidth(0.85f).widthIn(max = 650.dp)
            .roundFill(Sohva.palette.panel.copy(alpha = 0.96f), Sohva.shapes.large).padding(22.dp).testTag("addon-subtitle-sync"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.addon_ui_subtitle_sync), Modifier.weight(1f), style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
            Text(SubtitleText.label(sync.draftMs), Modifier.testTag("addon-sync-draft"), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
        }
        val style = SurfaceStyle(corner = Sohva.shapes.medium, resting = Sohva.palette.surface, focusScale = 1f, padding = PaddingValues(16.dp, 10.dp))
        TvSurface(
            subs::applyDraft,
            Modifier.fillMaxWidth().focusRequester(control).focusProperties { down = apply }
                .onPreviewKeyEvent { e ->
                    val native = e.nativeKeyEvent
                    if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> subs.nudge(later = false, native.repeatCount).let { true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> subs.nudge(later = true, native.repeatCount).let { true }
                        else -> false
                    }
                }
                .testTag("addon-sync-control"),
            style = style,
        ) { colors ->
            Column {
                SyncTrack(sync.draftMs)
                Row {
                    Text(stringResource(R.string.addon_ui_earlier), Modifier.weight(1f), style = Sohva.typography.caption, color = colors.secondaryContent)
                    Text(stringResource(R.string.addon_ui_later), style = Sohva.typography.caption, color = colors.secondaryContent)
                }
            }
        }
        val status = when {
            sync.applying -> stringResource(R.string.addon_ui_applying_timing)
            sync.failed -> stringResource(R.string.addon_ui_could_not_apply_timing_return_to_playback_and_retry)
            sync.draftMs != sync.appliedMs -> stringResource(R.string.addon_ui_not_applied, SubtitleText.label(sync.appliedMs))
            else -> stringResource(R.string.addon_ui_left_right_0_1_s_hold_1_s_ok_or_apply_to_preview_playback_may_brie)
        }
        Text(status, Modifier.testTag("addon-sync-status"), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.focusProperties { up = control }) {
            TvActionButton(stringResource(R.string.addon_ui_apply), subs::applyDraft, Modifier.focusRequester(apply).testTag("addon-sync-apply"), compact = true)
            TvActionButton(stringResource(R.string.addon_ui_reset_to_zero), subs::resetDraft, Modifier.testTag("addon-sync-reset"), compact = true)
            TvActionButton(stringResource(R.string.player_play) + " / " + stringResource(R.string.player_pause), model.transport::togglePlay, Modifier.testTag("addon-sync-play"), compact = true)
            TvActionButton(stringResource(R.string.addon_ui_back_to_subtitles), subs::closeSync, Modifier.testTag("addon-sync-back"), compact = true)
        }
    }
    BackHandler { subs.closeSync() }
    LaunchedEffect(Unit) { control.requestFocusWhenAttached() }
}

/** The 28 dp track: a 2 dp line inset 8 dp, a 12 dp centre tick, a 6 dp-radius knob at (draft + 60 s) / 120 s. */
@Composable
private fun SyncTrack(draftMs: Long) {
    val line = Sohva.palette.textDim
    val tick = Sohva.palette.textMuted
    val knob = Sohva.palette.focus
    Box(
        Modifier.fillMaxWidth().height(28.dp).drawBehind {
            val inset = 8.dp.toPx()
            val y = size.height / 2
            drawLine(line, Offset(inset, y), Offset(size.width - inset, y), 2.dp.toPx())
            drawLine(tick, Offset(size.width / 2, y - 6.dp.toPx()), Offset(size.width / 2, y + 6.dp.toPx()), 2.dp.toPx())
            val fraction = (draftMs + SubtitleText.MAX_OFFSET_MS).toFloat() / (2 * SubtitleText.MAX_OFFSET_MS)
            drawCircle(knob, 6.dp.toPx(), Offset(inset + (size.width - 2 * inset) * fraction, y))
        },
    )
}
