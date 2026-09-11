package com.streammate.tv.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.sohva.tv.addons.AddonCastMember
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.iptv.R

/** VOD's compact series treatment keeps the season and episode controls visible. */
@Composable
internal fun AddonCastNames(cast: List<AddonCastMember>, modifier: Modifier = Modifier) {
    if (cast.isEmpty()) return
    Text(stringResource(R.string.series_cast_line, cast.joinToString { it.name }),
        modifier.testTag("addon-cast-names"), color = StreamMateThemeTokens.palette.textMuted,
        fontSize = StreamMateThemeTokens.typography.label.fontSize,
        lineHeight = StreamMateThemeTokens.typography.label.lineHeight,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** Matches VOD's 52dp circular portraits / 84dp name columns, with remote scrolling. */
@Composable
internal fun AddonMovieCast(cast: List<AddonCastMember>) {
    if (cast.isEmpty()) return
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Column(Modifier.fillMaxWidth().testTag("addon-movie-cast"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.series_cast), color = palette.textPrimary, fontSize = typography.headline.fontSize,
            lineHeight = typography.headline.lineHeight, fontWeight = FontWeight.Bold)
        LazyRow(Modifier.fillMaxWidth().focusGroup().testTag("addon-cast-row"),
            contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(cast) { index, member ->
                var focused by remember { mutableStateOf(false) }
                val description = member.character?.let { stringResource(R.string.details_cast_member, member.name, it) } ?: member.name
                Column(Modifier.width(92.dp)
                    .border(2.dp, if (focused) palette.focus else Color.Transparent, RoundedCornerShape(8.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .testTag("addon-cast-member-$index").focusable().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(palette.surface), contentAlignment = Alignment.Center) {
                        val initials = remember(member.name) {
                            val words = member.name.split(' ', '.', '-', ':', '_', '·', '/').filter { it.firstOrNull()?.isLetter() == true }
                            if (words.size > 1) words.take(2).joinToString("") { it.take(1).uppercase() }
                            else member.name.take(2).uppercase()
                        }
                        Text(initials, color = palette.textMuted, fontSize = typography.label.fontSize, fontWeight = FontWeight.Black)
                        member.photo?.let { photo -> AsyncImage(photo, null, Modifier.fillMaxSize().testTag("addon-cast-photo-$index"), contentScale = ContentScale.Crop) }
                    }
                    Text(member.name, Modifier.padding(top = 8.dp), color = palette.textPrimary, fontSize = typography.label.fontSize,
                        lineHeight = typography.label.lineHeight, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    member.character?.let { Text(it, Modifier.padding(top = 2.dp), color = palette.textDim,
                        fontSize = typography.caption.fontSize, lineHeight = typography.caption.lineHeight,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}
