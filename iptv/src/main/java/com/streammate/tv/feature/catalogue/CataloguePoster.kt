package com.streammate.tv.feature.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.streammate.tv.app.StreamMateThemeTokens

/** Bounded poster loader for the catalogue wall. */
@Composable
internal fun CataloguePoster(
    url: String?,
    title: String,
    modifier: Modifier,
    onError: (() -> Unit)? = null,
) {
    val palette = StreamMateThemeTokens.palette
    Box(
        modifier = modifier
            .clip(StreamMateThemeTokens.shapes.medium)
            .background(
                Brush.verticalGradient(listOf(palette.surfaceSubtle, palette.background)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = catalogueInitials(title),
                color = palette.textMuted,
                fontSize = StreamMateThemeTokens.typography.headline.fontSize,
                fontWeight = FontWeight.Black,
            )
        } else {
            val context = LocalContext.current
            val request = remember(context, url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .size(POSTER_DECODE_WIDTH_PX, POSTER_DECODE_HEIGHT_PX)
                    .precision(Precision.INEXACT)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { onError?.invoke() },
            )
        }
    }
}

private const val POSTER_DECODE_WIDTH_PX = 192
private const val POSTER_DECODE_HEIGHT_PX = 288
