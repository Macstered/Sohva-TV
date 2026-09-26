package com.sohva.tv.feature.sport.hub

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * D§8 "Streams" (SPORT-FR-73): the header with the watchable count, then the channel matches
 * pairing found for this game (spec 60 §4.10), or the empty line.
 */
@Composable
internal fun StreamsPanel(watchable: Int, modifier: Modifier) {
    Column(modifier.fillMaxHeight().background(Sohva.palette.surface, RoundedCornerShape(14.dp)).padding(16.dp).testTag("hub-streams")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(TvIcons.Play), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Sohva.palette.accent))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.streams_title), style = PANEL_TITLE, color = Sohva.palette.accent)
                Text(stringResource(R.string.streams_subtitle), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
            }
            Text(
                stringResource(R.string.streams_available_count, watchable),
                style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = if (watchable > 0) Sohva.palette.focus else Sohva.palette.textMuted,
            )
        }
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Sohva.palette.divider))
        Text(stringResource(R.string.streams_empty), Modifier.testTag("hub-streams-empty"), style = Sohva.typography.label, color = Sohva.palette.textMuted)
    }
}
