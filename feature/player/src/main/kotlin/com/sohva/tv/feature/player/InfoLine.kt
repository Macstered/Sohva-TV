package com.sohva.tv.feature.player

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.sohva.tv.core.model.player.InfoLine as Readings
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.player.StreamStats
import com.sohva.tv.core.model.player.SubtitleBackground
import com.sohva.tv.core.model.player.SubtitleColor
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

/**
 * The playback info line and clock (spec 30 §4.12, §5.9): sampled every second only while shown;
 * unmeasured readings are left out. A controller cannot count dropped frames, so that reading
 * never shows here (PLAY-FR-83 item 6).
 */
@Composable
internal fun InfoLine(model: PlayerModel) {
    var stats by remember { mutableStateOf(StreamStats()) }
    var now by remember { mutableLongStateOf(model.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            model.controller?.let { stats = sample(it) }
            now = model.now()
            delay(SAMPLE_MS)
        }
    }
    val p = Sohva.palette
    val label = Sohva.typography.label
    Box(Modifier.fillMaxSize().testTag("player-info-line")) {
        Row(Modifier.padding(start = 40.dp, top = 24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val readings = listOfNotNull(
                Readings.resolution(stats)?.let { null to it },
                Readings.codecs(stats)?.let { null to it },
                Readings.bitrate(stats)?.let { null to it },
                stats.subtitleMime?.let { stringResource(R.string.player_stats_subtitles) to it },
                stringResource(R.string.player_stats_buffer) to Readings.buffer(stats),
            )
            for ((name, value) in readings) {
                Row {
                    if (name != null) Text(name, Modifier.padding(end = 6.dp), style = label, color = p.textDim, maxLines = 1)
                    Text(value, style = label.copy(fontWeight = FontWeight.Bold), color = p.textPrimary, maxLines = 1)
                }
            }
        }
        Text(clock(now, model.settings.timeZone), Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 24.dp), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = p.textPrimary)
    }
}

/** The wall time in the chosen zone, 24- or 12-hour from the system setting (PLAY-FR-84). */
@Composable
private fun clock(now: Long, zone: String?): String {
    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hmma")
    val format = SimpleDateFormat(pattern, locale)
    format.timeZone = zone?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
    return format.format(Date(now))
}

@OptIn(UnstableApi::class)
private fun sample(player: Player): StreamStats {
    var width: Int? = null
    var height: Int? = null
    var rate: Float? = null
    var videoMime: String? = null
    var bitrate: Int? = null
    var audioMime: String? = null
    var channels: Int? = null
    var textMime: String? = null
    for (group in player.currentTracks.groups) {
        for (i in 0 until group.length) {
            if (!group.isTrackSelected(i)) continue
            val f = group.getTrackFormat(i)
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    width = f.width.takeIf { it > 0 }
                    height = f.height.takeIf { it > 0 }
                    rate = f.frameRate.takeIf { it > 0f }
                    videoMime = f.sampleMimeType
                    bitrate = listOf(f.bitrate, f.averageBitrate, f.peakBitrate).firstOrNull { it > 0 }
                }
                C.TRACK_TYPE_AUDIO -> {
                    audioMime = f.sampleMimeType
                    channels = f.channelCount.takeIf { it > 0 }
                }
                C.TRACK_TYPE_TEXT -> textMime = f.sampleMimeType ?: f.containerMimeType
            }
        }
    }
    if (width == null) {
        player.videoSize.takeIf { it.width > 0 }?.let {
            width = it.width
            height = it.height
        }
    }
    return StreamStats(width, height, rate, videoMime, audioMime, channels, bitrate, textMime, player.totalBufferedDuration / 1000f)
}

/** Subtitle look (PLAY-FR-100..101): the TV's style while both colour and background follow it. */
@OptIn(UnstableApi::class)
internal object SubtitleLook {
    fun apply(view: SubtitleView, settings: PlaybackSettings) {
        val captioning = view.context.getSystemService(CaptioningManager::class.java)
        val tv = if (captioning != null && captioning.isEnabled) CaptionStyleCompat.createFromCaptionStyle(captioning.userStyle) else CaptionStyleCompat.DEFAULT
        val follow = settings.subtitleColor == SubtitleColor.FOLLOW_TV && settings.subtitleBackground == SubtitleBackground.FOLLOW_TV
        val scale = settings.subtitleSize.scale
        if (scale == null) {
            view.setUserDefaultTextSize()
        } else {
            view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
        }
        view.setApplyEmbeddedFontSizes(scale == null)
        if (follow) {
            view.setApplyEmbeddedStyles(true)
            view.setStyle(tv)
            return
        }
        view.setApplyEmbeddedStyles(false)
        val foreground = settings.subtitleColor.argb?.toInt() ?: tv.foregroundColor
        val style = when (settings.subtitleBackground) {
            SubtitleBackground.FOLLOW_TV -> CaptionStyleCompat(foreground, tv.backgroundColor, tv.windowColor, tv.edgeType, tv.edgeColor, tv.typeface)
            SubtitleBackground.NONE -> CaptionStyleCompat(foreground, AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, AndroidColor.TRANSPARENT, tv.typeface ?: Typeface.DEFAULT)
            SubtitleBackground.SHADOW -> CaptionStyleCompat(foreground, AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, AndroidColor.BLACK, tv.typeface ?: Typeface.DEFAULT)
            SubtitleBackground.BOX -> CaptionStyleCompat(foreground, 0xCC000000.toInt(), AndroidColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, AndroidColor.TRANSPARENT, tv.typeface ?: Typeface.DEFAULT)
        }
        view.setStyle(style)
    }
}

private const val SAMPLE_MS = 1_000L
