package com.sohva.tv.app.artwork

import com.sohva.tv.core.data.prefs.ArtworkCacheStore
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.ImageLoader
import coil3.bitmapFactoryMaxParallelism
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.bitmapConfig
import coil3.size.Precision
import coil3.toBitmap
import com.sohva.tv.app.AppGraph
import com.sohva.tv.ui.design.components.ArtworkLoader
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath

/**
 * The one image loader (plan/03 §4.12), built on first use: memory cache 8 % of the memory class,
 * disk cache `cache/catalogue_artwork` at the Settings limit read when it is built (spec 70 SET-FR-80), at most
 * two decodes at a time, every request decoded at the size it is drawn (AGENTS.md §4 rule 5).
 */
class CoilArtwork(private val graph: AppGraph) : ArtworkLoader {
    private val loader: ImageLoader by lazy {
        val app = graph.app
        ImageLoader.Builder(app)
            .memoryCache { MemoryCache.Builder().maxSizePercent(app, MEMORY_FRACTION).build() }
            .diskCache { DiskCache.Builder().directory(app.cacheDir.resolve(DISK_DIRECTORY).toOkioPath()).maxSizeBytes(ArtworkCacheStore(app).limit().bytes).build() }
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.player.callFactory })) }
            .bitmapFactoryMaxParallelism(2)
            .build()
    }

    override suspend fun load(url: String, widthPx: Int, heightPx: Int, opaque: Boolean): ImageBitmap? = withContext(graph.dispatchers.io) {
        val request = ImageRequest.Builder(graph.app)
            .data(url)
            .size(widthPx, heightPx)
            .precision(Precision.INEXACT)
            .bitmapConfig(if (opaque) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
            .build()
        (loader.execute(request) as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()
    }

    /** The disk cache's own tracked size: no directory walk (spec 70 SET-FR-81). */
    suspend fun usage(): Long = withContext(graph.dispatchers.io) { loader.diskCache?.size ?: 0L }

    /** Disk and memory, so the screen does not keep what the viewer asked to remove (SET-FR-82). */
    suspend fun clear() {
        withContext(graph.dispatchers.io) {
            loader.diskCache?.clear()
            loader.memoryCache?.clear()
        }
    }

    private companion object {
        const val MEMORY_FRACTION = 0.08
        const val DISK_DIRECTORY = "catalogue_artwork"
    }
}
