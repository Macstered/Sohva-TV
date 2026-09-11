package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.sohva.tv.addons.AddonException
import com.sohva.tv.addons.AddonFailure
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun AddonSubtitleSync(playback: AddonPlayback, playing: Boolean, onTogglePlayback: () -> Unit, onBack: () -> Unit) {
    val labels = addonStrings()
    val palette = StreamMateThemeTokens.palette
    val type = StreamMateThemeTokens.typography
    var draft by remember { mutableLongStateOf(playback.subtitleDelayMillis) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val adjustmentFocus = remember { FocusRequester() }
    val applyFocus = remember { FocusRequester() }
    fun applyDraft() {
        if (busy || draft == playback.subtitleDelayMillis) return
        busy = true; failed = false
        val requested = draft
        scope.launch {
            try {
                playback.applySubtitleDelay(requested)
                val ready = withTimeoutOrNull(20_000) { snapshotFlow { playback.ready || playback.failed }.first { it } }
                if (ready == null || playback.failed) throw AddonException(AddonFailure.TIMEOUT)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { adjustmentFocus.requestFocusWhenAttached() }
    Box(Modifier.fillMaxSize().testTag("addon-subtitle-sync-panel"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 24.dp).widthIn(max = 650.dp).fillMaxWidth(.85f)
            .clip(StreamMateThemeTokens.shapes.large).background(palette.panel.copy(alpha = .96f)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(labels(R.string.addon_ui_subtitle_sync), Modifier.weight(1f), color = palette.textPrimary, fontSize = type.body.fontSize, fontWeight = FontWeight.Bold)
                Text(AddonSubtitleTiming.label(draft), Modifier.testTag("addon-subtitle-offset"), color = palette.textPrimary,
                    fontSize = type.headline.fontSize, fontWeight = FontWeight.Bold)
            }
            // The wide adjustment surface's geometric Down target can be Back.
            // Make the commit path deterministic, including OK on the adjustment.
            TvSurface(::applyDraft, Modifier.fillMaxWidth().focusProperties { down = applyFocus }.onPreviewKeyEvent { event ->
                if (event.key != Key.DirectionLeft && event.key != Key.DirectionRight) false else {
                    if (event.type == KeyEventType.KeyDown && !busy) draft = AddonSubtitleTiming.step(draft,
                        if (event.key == Key.DirectionRight) 1 else -1, event.nativeKeyEvent.repeatCount >= 8)
                    true
                }
            }.semantics { progressBarRangeInfo = ProgressBarRangeInfo(draft / 1000f, -60f..60f, 1199) },
                focusRequester = adjustmentFocus, focusRing = true, focusScale = 1f, testTag = "addon-subtitle-adjust",
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Column {
                    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                        val left = 8.dp.toPx(); val right = size.width - left; val y = size.height / 2
                        drawLine(palette.textDim, Offset(left, y), Offset(right, y), 2.dp.toPx())
                        drawLine(palette.textMuted, Offset(size.width / 2, y - 6.dp.toPx()), Offset(size.width / 2, y + 6.dp.toPx()), 2.dp.toPx())
                        drawCircle(palette.focus, 6.dp.toPx(), Offset(left + (right - left) * (draft + 60_000) / 120_000f, y))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(labels(R.string.addon_ui_earlier), color = palette.textMuted, fontSize = type.caption.fontSize)
                        Text(labels(R.string.addon_ui_later), color = palette.textMuted, fontSize = type.caption.fontSize)
                    }
                }
            }
            Text(if (busy) labels(R.string.addon_ui_applying_timing) else if (failed) labels(R.string.addon_ui_could_not_apply_timing_return_to_playback_and_retry)
                else labels(R.string.addon_ui_left_right_0_1_s_hold_1_s_ok_or_apply_to_preview_playback_may_brie),
                color = palette.textMuted, fontSize = type.caption.fontSize)
            Row(Modifier.focusProperties { up = adjustmentFocus }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionButton(if (busy) labels(R.string.addon_ui_applying) else labels(R.string.addon_apply_filters), ::applyDraft,
                    focusRequester = applyFocus, compact = true, testTag = "addon-subtitle-apply")
                TvActionButton(labels(R.string.addon_ui_reset_to_zero), { if (!busy) draft = 0 }, compact = true, testTag = "addon-subtitle-reset")
                TvActionButton(if (playing) labels(com.streammate.tv.iptv.R.string.player_pause) else labels(com.streammate.tv.iptv.R.string.player_play), onTogglePlayback, compact = true, testTag = "addon-sync-play-pause")
                TvActionButton(labels(R.string.addon_ui_back_to_subtitles), onBack, compact = true, testTag = "addon-sync-back")
            }
            if (!busy && draft != playback.subtitleDelayMillis) Text(labels(R.string.addon_ui_not_applied, AddonSubtitleTiming.label(playback.subtitleDelayMillis)),
                color = palette.textMuted, fontSize = type.caption.fontSize)
        }
    }
}
