package com.sohva.tv.ui.design.ground

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import com.sohva.tv.ui.design.theme.Sohva
import com.sohva.tv.ui.design.theme.SohvaPalette
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Where the ground is rendered: the app's `ui` dispatcher, never the main thread. */
val LocalRenderDispatcher = staticCompositionLocalOf<CoroutineDispatcher> {
    error("Provide LocalRenderDispatcher at the root")
}

/**
 * The static ground of design/01 §11: the vertical gradient and the two radial washes, rendered
 * once per (palette, size) into one bitmap off the main thread, with dithering, at half resolution
 * (≤ 960 × 540, ≈ 2 MB) and drawn scaled with bilinear filtering (§16.1 rule 2). Every screen
 * shares it; beta 23 kept an offscreen layer per screen instead.
 *
 * Bounded cache: the last [CAPACITY] bitmaps (a theme switch keeps the previous one for Back).
 */
object GroundCache {
    private const val CAPACITY = 2
    private const val MAX_WIDTH = 960
    private const val MAX_HEIGHT = 540

    private data class Key(val palette: SohvaPalette, val width: Int, val height: Int)

    private val entries = LinkedHashMap<Key, ImageBitmap>()

    fun peek(palette: SohvaPalette, size: IntSize): ImageBitmap? = synchronized(entries) { entries[key(palette, size)] }

    /** Renders on [dispatcher] if needed. Call before the first frame to avoid a flat first frame. */
    suspend fun prepare(palette: SohvaPalette, size: IntSize, dispatcher: CoroutineDispatcher): ImageBitmap {
        val key = key(palette, size)
        peek(palette, size)?.let { return it }
        val bitmap = withContext(dispatcher) { render(palette, key.width, key.height).asImageBitmap() }
        synchronized(entries) {
            entries.remove(key)
            entries[key] = bitmap
            while (entries.size > CAPACITY) entries.remove(entries.keys.first())
        }
        return bitmap
    }

    private fun key(palette: SohvaPalette, size: IntSize): Key {
        val half = IntSize((size.width / 2).coerceIn(1, MAX_WIDTH), (size.height / 2).coerceIn(1, MAX_HEIGHT))
        return Key(palette, half.width, half.height)
    }

    /** The exact stops of beta 23; both washes fade to transparent black, as `Color.Transparent` did. */
    internal fun render(p: SohvaPalette, w: Int, h: Int): Bitmap {
        val bitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isDither = true }
        val fw = w.toFloat()
        val fh = h.toFloat()
        paint.shader = LinearGradient(
            0f, 0f, 0f, fh,
            intArrayOf(p.backgroundTop.toArgb(), p.background.toArgb(), p.backgroundBottom.toArgb()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, fw, fh, paint)
        paint.shader = wash(p.focus, 0.10f, 0.6f, fw * 0.12f, fh * -0.10f, fw * 0.58f)
        canvas.drawRect(0f, 0f, fw, fh, paint)
        paint.shader = wash(p.secondaryGlow, 0.12f, 0.62f, fw * 0.92f, fh * 0.04f, fw * 0.50f)
        canvas.drawRect(0f, 0f, fw, fh, paint)
        return bitmap
    }

    private fun wash(color: Color, alpha: Float, midStop: Float, x: Float, y: Float, radius: Float) = RadialGradient(
        x, y, radius,
        intArrayOf(color.copy(alpha = alpha).toArgb(), color.copy(alpha = 0.02f).toArgb(), Color.Transparent.toArgb()),
        floatArrayOf(0f, midStop, 1f),
        Shader.TileMode.CLAMP,
    )
}

/**
 * A screen on the shared ground. Until the bitmap is ready (a few milliseconds, off the main
 * thread) the flat `background` colour is drawn, so nothing waits for it.
 */
@Composable
fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val palette = Sohva.palette
    val dispatcher = LocalRenderDispatcher.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var ground by remember(palette) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(palette, size) {
        if (size != IntSize.Zero) ground = GroundCache.prepare(palette, size, dispatcher)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                size = it
                // A cached ground is drawn in this very frame; only a new size or theme waits.
                if (ground == null) ground = GroundCache.peek(palette, it)
            }
            .drawBehind {
                val image = ground
                if (image == null) {
                    drawRect(palette.background)
                } else {
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
            },
        content = content,
    )
}
