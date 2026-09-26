package com.sohva.tv.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.ContentColors
import com.sohva.tv.ui.design.theme.Sohva

/** One profile as the picker draws it: the shown name and the avatar colour index (spec 04 §5.1). */
@Immutable
data class PickerTile(val id: String, val name: String, val colorIndex: Int)

/**
 * Who is watching? (spec 04 PROF-FR-12, §5.1): one tile per profile in list order on a flat
 * `backgroundBottom`, [focusedId]'s tile focused first. Six 180 dp tiles do not fit one row at
 * every interface size, so the tiles wrap to as many rows as needed (decision "Spec 04 quirks
 * fixed"). No images: circles and letters, one pass.
 */
@Composable
fun ProfilePickerScreen(tiles: List<PickerTile>, focusedId: String, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    val requesters = remember(tiles) { tiles.associate { it.id to FocusRequester() } }
    Box(modifier.fillMaxSize().background(Sohva.palette.backgroundBottom).testTag("screen-profiles"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(36.dp)) {
            Text(
                stringResource(R.string.profile_picker_title),
                style = Sohva.typography.title.copy(fontSize = 34.sp, lineHeight = 40.sp),
                color = Sohva.palette.textPrimary,
            )
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val perRow = ((maxWidth + TILE_GAP) / (TILE_WIDTH + TILE_GAP)).toInt().coerceIn(1, tiles.size.coerceAtLeast(1))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    for (row in tiles.chunked(perRow)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                            for (tile in row) Tile(tile, requesters.getValue(tile.id)) { onChoose(tile.id) }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(requesters, focusedId) {
        (requesters[focusedId] ?: requesters.values.firstOrNull())?.requestFocusWhenAttached()
    }
}

@Composable
private fun Tile(tile: PickerTile, requester: FocusRequester, onClick: () -> Unit) {
    val style = SurfaceStyle(
        corner = Sohva.shapes.medium,
        resting = Sohva.palette.backgroundBottom.copy(alpha = 0f),
        focusRing = true,
        padding = PaddingValues(16.dp),
        contentAlignment = Alignment.Center,
    )
    TvSurface(onClick, Modifier.width(TILE_WIDTH).focusRequester(requester).testTag("profile-tile-${tile.id}"), style = style) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(110.dp).background(ContentColors.profile(tile.colorIndex), CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    initial(tile.name),
                    style = Sohva.typography.display.copy(fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Black),
                    color = ContentColors.onAvatar,
                )
            }
            Text(
                tile.name,
                style = Sohva.typography.headline.copy(fontSize = 20.sp, lineHeight = 25.sp, textAlign = TextAlign.Center),
                color = Sohva.palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
    }
}

/** The display name's first character, upper-cased (spec 04 §5.1). */
internal fun initial(name: String): String = name.trim().take(1).uppercase()

private val TILE_WIDTH = 180.dp
private val TILE_GAP = 28.dp
