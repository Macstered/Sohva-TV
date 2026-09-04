package com.streammate.tv.feature.player

import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.iptv.R

/**
 * What the player can tell us about the stream, for diagnosing a provider that
 * is stuttering or serving a lower rendition than it claims.
 */
internal data class PlaybackStats(
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val videoCodec: String?,
    val videoBitrate: Int,
    val audioCodec: String?,
    val audioChannels: Int,
    val audioSampleRate: Int,
    val bufferedAheadMillis: Long,
    /**
     * Frames the decoder gave up on, where the player can say.
     *
     * A `MediaController` talks to a session over IPC and has no decoder of its
     * own to ask, so this is null there rather than zero: "none dropped" and
     * "nobody counted" are different answers, and only one of them is worth
     * putting on the screen.
     */
    val droppedFrames: Int? = null,
)

internal fun Player.collectPlaybackStats(): PlaybackStats {
    val video = currentTracks.selectedFormat(C.TRACK_TYPE_VIDEO)
    val audio = currentTracks.selectedFormat(C.TRACK_TYPE_AUDIO)
    return PlaybackStats(
        width = video?.width ?: videoSize.width,
        height = video?.height ?: videoSize.height,
        frameRate = video?.frameRate ?: Format.NO_VALUE.toFloat(),
        videoCodec = video?.sampleMimeType?.substringAfterLast('/'),
        videoBitrate = video?.resolveBitrate() ?: Format.NO_VALUE,
        audioCodec = audio?.sampleMimeType?.substringAfterLast('/'),
        audioChannels = audio?.channelCount ?: Format.NO_VALUE,
        audioSampleRate = audio?.sampleRate ?: Format.NO_VALUE,
        bufferedAheadMillis = (bufferedPosition - currentPosition).coerceAtLeast(0L),
    )
}

private fun Tracks.selectedFormat(trackType: Int): Format? = groups
    .firstOrNull { it.type == trackType && it.isSelected }
    ?.let { group ->
        (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat)
    }

// Format.bitrate / averageBitrate / peakBitrate are media3 unstable APIs.
@OptIn(UnstableApi::class)
private fun Format.resolveBitrate(): Int = when {
    bitrate != Format.NO_VALUE -> bitrate
    averageBitrate != Format.NO_VALUE -> averageBitrate
    else -> peakBitrate
}

/**
 * Shown whenever the player is buffering. Without it a stalled stream and a
 * dead one look identical: a black screen.
 */
@Composable
internal fun PlayerBufferingIndicator(
    label: String,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val transition = rememberInfiniteTransition(label = "buffering")
    val sweepStart by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "buffering sweep",
    )
    Row(
        modifier = modifier
            .clip(StreamMateThemeTokens.shapes.large)
            .background(palette.background.copy(alpha = 0.72f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawArc(
                color = palette.textPrimary.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx()),
            )
            drawArc(
                color = palette.focus,
                startAngle = sweepStart,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(start = 14.dp),
            color = palette.textPrimary,
            fontSize = typography.body.fontSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * One line of readings across the top of the picture, toggled with the INFO
 * key.
 *
 * A line rather than a panel: it sits over the video, and a boxed table in the
 * corner of a film is a bigger interruption than the numbers are worth. A value
 * the player could not measure is absent rather than zero.
 */
@Composable
internal fun PlayerStatsOverlay(
    stats: PlaybackStats,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val values = playerStatsValues(
        stats = stats,
        bufferLabel = stringResource(R.string.player_stats_buffer),
        droppedLabel = stringResource(R.string.player_stats_dropped),
    )
    Row(
        modifier = modifier.testTag("player-stats"),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEach { reading ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                reading.label?.let { label ->
                    Text(
                        text = label,
                        color = palette.textDim,
                        fontSize = typography.label.fontSize,
                        lineHeight = typography.label.lineHeight,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = reading.value,
                    color = palette.textPrimary,
                    fontSize = typography.label.fontSize,
                    lineHeight = typography.label.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The wash that keeps chrome readable over a picture.
 *
 * Transparent through the middle, so the film is not covered by a frame drawn
 * around information: near-black along the foot where the programme and the
 * controls sit, and a light darkening along the top for the status line and
 * the clock.
 */
@Composable
internal fun PlayerChromeScrim(modifier: Modifier = Modifier) {
    val palette = StreamMateThemeTokens.palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to palette.background.copy(alpha = 0.62f),
                    0.16f to palette.background.copy(alpha = 0f),
                    0.52f to palette.background.copy(alpha = 0f),
                    1f to palette.background.copy(alpha = 0.94f),
                ),
            ),
    )
}
