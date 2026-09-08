package com.streammate.tv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.iptv.R as IptvR

/** The six profile colours, by a profile's colour index. */
val PROFILE_COLORS: List<Color> = listOf(
    Color(0xFF2EC4B6), Color(0xFFFF9F1C), Color(0xFFE71D36), Color(0xFF7B61FF), Color(0xFF4CAF50), Color(0xFFF06292),
)

/**
 * Who is watching, asked at start once a household has more than one
 * profile: a row of large tiles, the active profile focused first.
 */
@Composable
fun ProfilePickerScreen(
    profiles: List<Profile>,
    activeProfileId: String,
    onChoose: (Profile) -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val defaultName = stringResource(IptvR.string.profile_default_name)
    val shown = remember(profiles, defaultName) { Profiles.withDefault(profiles, defaultName) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenAttached() }
    Box(
        modifier = Modifier.fillMaxSize().background(palette.backgroundBottom),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(36.dp)) {
            Text(
                text = stringResource(IptvR.string.profile_picker_title),
                color = palette.textPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                shown.forEach { profile ->
                    ProfileTile(
                        profile = profile,
                        name = profile.name.ifBlank { defaultName },
                        onClick = { onChoose(profile) },
                        focusRequester = if (profile.id == activeProfileId) firstFocus else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTile(profile: Profile, name: String, onClick: () -> Unit, focusRequester: FocusRequester?) {
    val palette = StreamMateThemeTokens.palette
    TvSurface(
        onClick = onClick,
        modifier = Modifier.width(180.dp),
        shape = StreamMateThemeTokens.shapes.medium,
        resting = Color.Transparent,
        restingContent = palette.textPrimary,
        focusRing = true,
        focusRequester = focusRequester,
        testTag = "profile-tile-${profile.id}",
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        contentAlignment = Alignment.Center,
    ) { _ ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(PROFILE_COLORS[profile.colorIndex.coerceIn(0, PROFILE_COLORS.lastIndex)]),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = name,
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
