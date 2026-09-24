package com.sohva.tv.feature.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.player.LiveFigures
import com.sohva.tv.core.model.player.PictureShape
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DrawColorIcon
import com.sohva.tv.ui.design.components.InitialsTile
import com.sohva.tv.ui.design.components.PlayerProgressTrack
import com.sohva.tv.ui.design.components.TagTone
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvTagChip
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The live information box (spec 30 §5.2): identity, the programme on now with its figures, the
 * next programme, and the action row. Composed only while shown; its figures are computed from
 * the 30-second "now" while it is up.
 */
@Composable
internal fun LiveInfoBox(model: PlayerModel, modifier: Modifier = Modifier) {
    val playing by model.playing.collectAsStateWithLifecycle()
    val nowNext by model.nowNext.collectAsStateWithLifecycle()
    val channel = playing ?: return
    val p = Sohva.palette
    val type = Sohva.typography
    val labels = remember(model) { TimeLabels(TimeLabels.zoneOf(model.settings.timeZone), java.util.Locale.ROOT) }
    val now = model.now()
    Column(modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 28.dp).testTag("player-live-box")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsTile(channel.channel.name, Modifier.size(64.dp), fontSize = 18.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(channel.channel.name, style = type.headline.copy(fontWeight = FontWeight.Black), color = p.textPrimary, maxLines = 1)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvTagChip(stringResource(R.string.guide_live), tone = TagTone.LIVE)
                    channel.tags.forEach { TvTagChip(it, tone = TagTone.MUTED) }
                    channel.channel.groupName?.let { Text(it, style = type.label, color = p.textMuted, maxLines = 1) }
                }
            }
        }
        val programme = nowNext.now
        Text(
            programme?.title ?: stringResource(R.string.player_no_current_programme),
            Modifier.fillMaxWidth(0.62f).padding(top = 12.dp),
            style = type.title.copy(fontWeight = FontWeight.Black, fontSize = 28.sp),
            color = p.textPrimary,
            maxLines = 1,
        )
        if (programme != null) {
            val fraction = LiveFigures.fraction(programme.start, programme.stop, now)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                val facts = listOfNotNull(
                    labels.playerTime(programme.start),
                    fraction?.let { stringResource(R.string.player_watched_percent, LiveFigures.percent(it)) },
                    LiveFigures.minutesLeft(programme.stop, now)?.let { minutesLeft(it) },
                ).joinToString("  ·  ")
                Text(facts, Modifier.weight(1f), style = type.body, color = p.textMuted, maxLines = 1)
                Text(labels.playerTime(programme.stop), style = type.body, color = p.textMuted, maxLines = 1)
            }
            PlayerProgressTrack({ fraction ?: 0f }, Modifier.padding(top = 8.dp))
        }
        nowNext.next?.let { next ->
            Text(
                stringResource(R.string.player_next_programme, labels.playerTime(next.start), next.title),
                Modifier.fillMaxWidth(0.62f).padding(top = 8.dp),
                style = type.label,
                color = p.textDim,
                maxLines = 1,
            )
        }
        ActionRow(model)
    }
}

@Composable
private fun minutesLeft(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.player_remaining_minutes, minutes)
    else stringResource(R.string.player_remaining_hours, minutes / 60, minutes % 60)

/** The live action row (PLAY-FR-44); Up/Down from the clean screen focus its first button. */
@Composable
private fun ActionRow(model: PlayerModel) {
    val shape by model.shape.collectAsStateWithLifecycle()
    val stats by model.statsOn.collectAsStateWithLifecycle()
    val tracks by model.tracks.collectAsStateWithLifecycle()
    val request by model.boxFocusRequest.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    val focused = remember { BooleanArray(8) }
    fun onFocus(i: Int, has: Boolean) {
        focused[i] = has
        model.boxFocused = focused.any { it }
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconAction(TvIcons.Back, stringResource(R.string.player_action_back), { model.hideBox() }, Modifier.focusRequester(first), onFocus = { onFocus(0, it) })
        IconAction(TvIcons.Aspect, stringResource(R.string.player_picture_mode, shapeLabel(shape)), { model.cycleShape() }, onFocus = { onFocus(1, it) })
        IconAction(TvIcons.Audio, stringResource(R.string.player_audio_track, audioLabel(tracks)), { model.openPicker(Picker.AUDIO) }, onFocus = { onFocus(2, it) })
        IconAction(TvIcons.Subtitles, stringResource(R.string.player_subtitle_track, subtitleLabel(tracks)), { model.openPicker(Picker.SUBTITLES) }, onFocus = { onFocus(3, it) })
        IconAction(TvIcons.Channels, stringResource(R.string.player_action_channels), { model.channels.openList() }, onFocus = { onFocus(4, it) })
        IconAction(TvIcons.Stats, stringResource(R.string.player_action_stats), { model.toggleStats() }, selected = stats, onFocus = { onFocus(5, it) })
        IconAction(TvIcons.Settings, stringResource(R.string.player_action_quick), { model.openQuickActions() }, onFocus = { onFocus(6, it) })
    }
    LaunchedEffect(request) { if (request > 0) first.requestFocusWhenAttached() }
}

/** A 44 × 44 icon action (spec 30 §5.4); the description is its accessibility label. */
@Composable
private fun IconAction(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onFocus: (Boolean) -> Unit,
) {
    val style = SurfaceStyle(corner = Sohva.shapes.small, resting = Sohva.palette.surface, restingContent = Sohva.palette.textPrimary, focusScale = 1f)
    TvSurface(
        onClick,
        modifier.size(44.dp).onFocusChanged { onFocus(it.isFocused) }.semantics { contentDescription = description },
        SurfaceState(selected = selected),
        style,
    ) { colors ->
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            DrawColorIcon(icon, { colors.content }, size = 20.dp)
        }
    }
}

@Composable
internal fun shapeLabel(shape: PictureShape): String = stringResource(
    when (shape) {
        PictureShape.FIT -> R.string.player_resize_fit
        PictureShape.FILL -> R.string.player_resize_fill
        PictureShape.ZOOM -> R.string.player_resize_zoom
    },
)
