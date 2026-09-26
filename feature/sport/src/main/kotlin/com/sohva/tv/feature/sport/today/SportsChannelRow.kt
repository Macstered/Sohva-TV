package com.sohva.tv.feature.sport.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sohva.tv.core.model.sport.pairing.SportsChannel
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * "Sports channels now" (SPORT-25, D§6): up to eight channels with an Available match for the games
 * on screen; OK plays the channel. The time follows the TV's 12/24-hour setting (D§10 rebuild).
 */
@Composable
internal fun SportsChannelSection(channels: List<SportsChannel>, labels: TimeLabels, play: (String) -> Unit) {
    if (channels.isEmpty()) return
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.today_sports_channels), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.today_sports_channels_help), style = Sohva.typography.label, color = Sohva.palette.textDim)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(Modifier.fillMaxWidth().testTag("today-channels"), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
            items(channels, key = { it.channelKey }) { c -> ChannelCard(c, labels) { play(c.channelKey) } }
        }
    }
}

@Composable
private fun ChannelCard(channel: SportsChannel, labels: TimeLabels, onClick: () -> Unit) {
    val style = SurfaceStyle(corner = Sohva.shapes.medium, resting = Sohva.palette.surfaceSubtle, focusScale = 1f, padding = PaddingValues(14.dp))
    TvSurface(onClick, Modifier.size(272.dp, 68.dp).testTag("today-channel-${channel.channelKey}"), style = style) { colors ->
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                channel.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = Sohva.typography.label.copy(fontWeight = FontWeight.SemiBold), color = colors.content,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${labels.guideTime(channel.startMillis)}  ·  ${channel.channelName}", Modifier.weight(1f, fill = false), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = Sohva.typography.caption, color = colors.secondaryContent,
                )
                if (channel.live) {
                    Text(
                        stringResource(R.string.status_live).uppercase(), Modifier.padding(start = 8.dp), maxLines = 1,
                        style = Sohva.typography.caption.copy(fontWeight = FontWeight.Black), color = if (colors.focused) colors.content else Sohva.palette.danger,
                    )
                }
            }
        }
    }
}
