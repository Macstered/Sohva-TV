package com.streammate.tv.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.sohva.tv.addons.AddonMedia
import com.streammate.tv.app.StreamMateThemeTokens

/** VOD-style poster: focus is an inset artwork border, not a scaled lazy-list child. */
@Composable
internal fun AddonPosterCard(media: AddonMedia, onClick: () -> Unit, modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null, onFocus: () -> Unit = {}, tag: String = "addon-media-card", progress: Float? = null, showCaption: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    var imageFailed by remember(media.poster) { mutableStateOf(false) }
    val context = LocalContext.current
    val request = remember(context, media.poster) { addonPosterRequest(context, media.poster) }
    val palette = StreamMateThemeTokens.palette
    val shape = StreamMateThemeTokens.shapes.medium
    Column(modifier.then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        .semantics { contentDescription = media.name }.testTag(tag).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(Brush.verticalGradient(listOf(palette.surface, palette.background)))
            .then(if (focused) Modifier.border(3.dp, palette.textPrimary, shape) else Modifier)) {
            if (media.poster == null || imageFailed) Text(media.name, Modifier.align(Alignment.Center).padding(16.dp).testTag("addon-poster-fallback"),
                color = palette.textMuted, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                fontSize = StreamMateThemeTokens.typography.label.fontSize)
            AsyncImage(request, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true }, onSuccess = { imageFailed = false })
            progress?.let { fraction -> Box(Modifier.align(androidx.compose.ui.Alignment.BottomStart).fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(palette.focus)) }
            // Paint the border above the image, inside the measured card bounds.
            if (focused) Box(Modifier.fillMaxSize().border(3.dp, palette.textPrimary, shape))
        }
        if (showCaption) {
            Text(media.name, Modifier.padding(top = 7.dp).height(20.dp), color = if (focused) palette.textPrimary else palette.textMuted,
                fontSize = StreamMateThemeTokens.typography.label.fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(media.releaseInfo, media.imdbRating).joinToString(" · "), color = palette.textDim,
                fontSize = StreamMateThemeTokens.typography.caption.fontSize, maxLines = 1)
        }
    }
}

internal fun addonPosterRequest(context: android.content.Context, url: String?): ImageRequest = ImageRequest.Builder(context)
    .data(url).size(256, 384).precision(Precision.INEXACT).crossfade(120).build()
