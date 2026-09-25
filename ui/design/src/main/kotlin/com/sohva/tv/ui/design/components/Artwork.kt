package com.sohva.tv.ui.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Loads artwork decoded at the size it is drawn (AGENTS.md §4 rule 5). The app provides one
 * backed by its image loader; previews and tests get none, which draws the initials.
 */
fun interface ArtworkLoader {
    /**
     * The image at [widthPx] × [heightPx] or smaller, or null when it cannot be had. [opaque]
     * artwork (posters, backdrops, stills) decodes as RGB_565, half the memory of ARGB_8888 (spec
     * 40 §9.5); logos keep their transparency.
     */
    suspend fun load(url: String, widthPx: Int, heightPx: Int, opaque: Boolean): ImageBitmap?

    companion object {
        val None: ArtworkLoader = ArtworkLoader { _, _, _, _ -> null }
    }
}

val LocalArtwork = staticCompositionLocalOf { ArtworkLoader.None }

/**
 * A channel logo on its tile (design/04 §3): the initials under it, the logo drawn over them once
 * it has loaded, fitted inside [padding]. A logo that fails leaves the initials (guide.md §12 item 8).
 * The image is read in the draw phase, so its arrival redraws the tile and recomposes nothing.
 */
@Composable
fun LogoTile(name: String, url: String?, size: Dp, modifier: Modifier = Modifier, padding: Dp = 3.dp, fontSize: TextUnit = 18.sp) {
    val loader = LocalArtwork.current
    val px = with(LocalDensity.current) { (size - padding * 2).roundToPx() }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, px) {
        if (!url.isNullOrBlank()) image = loader.load(url, px, px, opaque = false)
    }
    Box(modifier) {
        InitialsTile(name, Modifier.size(size), fontSize = fontSize)
        Box(
            Modifier.size(size).padding(padding).drawBehind {
                val bitmap = image ?: return@drawBehind
                drawFitted(bitmap, this.size.width, this.size.height)
            },
        )
    }
}

/** Draws [bitmap] fitted and centred in a [w] × [h] box. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFitted(bitmap: ImageBitmap, w: Float, h: Float, at: Offset = Offset.Zero) {
    val scale = minOf(w / bitmap.width, h / bitmap.height)
    val dw = (bitmap.width * scale).toInt()
    val dh = (bitmap.height * scale).toInt()
    drawImage(bitmap, dstOffset = IntOffset((at.x + (w - dw) / 2).toInt(), (at.y + (h - dh) / 2).toInt()), dstSize = IntSize(dw, dh))
}
