package com.sohva.tv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.player.SkipLadder
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import java.util.Locale

/**
 * The catch-up transport controls (spec 30 §4.5, §5.5): title, the Back / picture / audio /
 * subtitle row, the progress track, and the position with Rewind, Play/Pause and Forward. No
 * panel; the chrome scrim makes it readable. Composed only while shown.
 */
@Composable
internal fun TransportControls(model: PlayerModel, modifier: Modifier = Modifier) {
    val transport = model.transport
    val progress by transport.progress.collectAsStateWithLifecycle()
    val playing by model.playing.collectAsStateWithLifecycle()
    val shape by model.shape.collectAsStateWithLifecycle()
    val tracks by model.tracks.collectAsStateWithLifecycle()
    val focusPlay by transport.focusPlayRequest.collectAsStateWithLifecycle()
    val play = remember { FocusRequester() }
    val step = SkipLadder.label(transport.step.millis)
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, bottom = 28.dp)
            .onFocusChanged { transport.focused = it.hasFocus }
            .testTag("player-transport"),
    ) {
        Text(
            stringResource(R.string.player_archive_title, playing?.channel?.name.orEmpty()),
            Modifier.fillMaxWidth(0.62f),
            style = Sohva.typography.headline.copy(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black),
            color = Sohva.palette.textPrimary,
            maxLines = 1,
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvActionButton(stringResource(R.string.action_back), transport::hide, Modifier.testTag("player-transport-back"), icon = TvIcons.Back, compact = true)
            TvActionButton(stringResource(R.string.player_picture_mode, shapeLabel(shape)), model::cycleShape, icon = TvIcons.Aspect, compact = true)
            TvActionButton(stringResource(R.string.player_audio_track, audioLabel(tracks)), { model.openPicker(Picker.AUDIO) }, icon = TvIcons.Audio, compact = true)
            TvActionButton(
                stringResource(R.string.player_subtitle_track, subtitleLabel(tracks)),
                { model.openPicker(Picker.SUBTITLES) },
                icon = TvIcons.Subtitles,
                compact = true,
            )
        }
        ProgressTrack(progress.fraction, Modifier.padding(top = 12.dp).fillMaxWidth())
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                clock(progress.position) + " / " + clock(progress.duration),
                Modifier.padding(end = 18.dp).testTag("player-transport-time"),
                style = Sohva.typography.caption.copy(fontWeight = FontWeight.Normal),
                color = Sohva.palette.textMuted,
            )
            TvActionButton(stringResource(R.string.player_rewind, step), { transport.skip(back = true) }, Modifier.testTag("player-transport-rewind"), icon = TvIcons.Rewind, compact = true)
            TvActionButton(
                stringResource(if (progress.playing) R.string.player_pause else R.string.player_play),
                transport::togglePlay,
                Modifier.padding(horizontal = 10.dp).focusRequester(play).testTag("player-transport-play"),
                icon = if (progress.playing) TvIcons.Pause else TvIcons.Play,
            )
            TvActionButton(stringResource(R.string.player_forward, step), { transport.skip(back = false) }, Modifier.testTag("player-transport-forward"), icon = TvIcons.Forward, compact = true)
        }
    }
    LaunchedEffect(focusPlay) { if (focusPlay > 0) play.requestFocusWhenAttached() }
}

/** §5.3: a 5 dp track, the watched part in `focus`, a 13 dp thumb; drawn in one layer. */
@Composable
private fun ProgressTrack(fraction: Float, modifier: Modifier) {
    val track = Sohva.palette.textPrimary.copy(alpha = 0.20f)
    val fill = Sohva.palette.focus
    val thumb = Sohva.palette.textPrimary
    val radius = Sohva.shapes.small
    Box(
        modifier.height(13.dp).drawBehind {
            val h = 5.dp.toPx()
            val top = (size.height - h) / 2
            val corner = CornerRadius(radius.toPx())
            drawRoundRect(track, Offset(0f, top), Size(size.width, h), corner)
            drawRoundRect(fill, Offset(0f, top), Size(size.width * fraction, h), corner)
            val d = 13.dp.toPx()
            drawCircle(thumb, d / 2, Offset((size.width - d) * fraction + d / 2, size.height / 2))
        },
    )
}

/** `mm:ss`, or `h:mm:ss` from one hour (PLAY-FR-47). */
internal fun clock(millis: Long): String {
    val total = (millis / 1_000).coerceAtLeast(0)
    val h = total / 3_600
    val m = total % 3_600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%02d:%02d".format(Locale.ROOT, m, s)
}

/** The last skip's distance, 900 ms, bottom-centre (PLAY-FR-63, §5.11). */
@Composable
internal fun SkipFeedbackLabel(feedback: SkipFeedback, modifier: Modifier = Modifier) {
    Text(
        SkipLadder.label(feedback.millis, signed = true, negative = feedback.back),
        modifier
            .offset(y = (-120).dp)
            .background(Sohva.palette.scrim.copy(alpha = 0.6f), RoundedCornerShape(Sohva.shapes.medium))
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag("player-skip-feedback"),
        style = Sohva.typography.headline.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        color = Sohva.palette.onScrim,
    )
}
