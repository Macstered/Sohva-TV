package com.streammate.tv.feature.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.streammate.tv.app.StreamMateThemeTokens

/**
 * The artwork a detail page is built on.
 *
 * Full bleed and at full strength, with two scrims over it rather than a dimmed
 * image under them: dimming and then washing multiplies, and the first version
 * of this drew the picture at 62% under two washes, so a fraction of it reached
 * the screen for the full cost of decoding it.
 *
 * The scrims are shaped around where the text goes. Heavy down the left, where
 * the title, the synopsis and the buttons sit; heavy along the foot, where the
 * cast and the similar strip sit; and left comparatively clear through the
 * upper right, which is the only part of the picture anyone actually sees.
 */
@Composable
internal fun CatalogueDetailBackdrop(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    Box(
        modifier = modifier
            .fillMaxSize()
            // The ground under the artwork, and the whole of it when a title
            // has none: a quiet wash rather than an empty rectangle.
            .background(
                Brush.linearGradient(
                    listOf(palette.backgroundTop, palette.background, palette.backgroundBottom),
                ),
            ),
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to palette.background.copy(alpha = 0.97f),
                        0.28f to palette.background.copy(alpha = 0.84f),
                        0.60f to palette.background.copy(alpha = 0.04f),
                        1f to palette.background.copy(alpha = 0.22f),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        0f to palette.background.copy(alpha = 0.42f),
                        0.20f to palette.background.copy(alpha = 0f),
                        0.86f to palette.background.copy(alpha = 0.99f),
                        1f to palette.background,
                    ),
                ),
        )
        content()
    }
}
