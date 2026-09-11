package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.requestFocusWhenAttached

@Composable
internal fun AddonPlaybackLoading(title: String, background: String?, logo: String?, stage: String, onBack: () -> Unit, onSubtitles: () -> Unit,
    subtitleFocus: FocusRequester) {
    val labels = addonStrings()
    val pulse = rememberInfiniteTransition(label = "startup-title")
    val opacity by pulse.animateFloat(.78f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "title-opacity")
    var logoFailed by remember(logo) { mutableStateOf(false) }
    var logoLoaded by remember(logo) { mutableStateOf(false) }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocusWhenAttached() }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("addon-playback-loading")) {
        AsyncImage(background, null, Modifier.fillMaxSize().testTag("addon-loading-backdrop"), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .15f), Color.Black.copy(alpha = .35f), Color.Black.copy(alpha = .88f)))))
        Column(Modifier.align(Alignment.Center).fillMaxWidth(.65f), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 170.dp).graphicsLayer { alpha = opacity }, contentAlignment = Alignment.Center) {
                if (!logoLoaded || logoFailed) Text(title, color = Color.White, textAlign = TextAlign.Center,
                    fontSize = StreamMateThemeTokens.typography.display.fontSize, lineHeight = StreamMateThemeTokens.typography.display.lineHeight,
                    fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (logo != null && !logoFailed) AsyncImage(logo, title, Modifier.fillMaxWidth().height(170.dp).testTag("addon-loading-logo"), contentScale = ContentScale.Fit,
                    onSuccess = { logoLoaded = true }, onError = { logoFailed = true })
            }
            Text(stage, Modifier.testTag("addon-loading-stage"), color = Color.White,
                fontSize = StreamMateThemeTokens.typography.body.fontSize, textAlign = TextAlign.Center)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvActionButton(labels(R.string.addon_cancel), onBack, compact = true, focusRequester = cancelFocus, testTag = "addon-loading-cancel")
            TvActionButton(labels(com.streammate.tv.iptv.R.string.player_quick_subtitles), onSubtitles, compact = true, focusRequester = subtitleFocus, testTag = "player-subtitles")
        }
    }
}
