package com.streammate.tv.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.bitmapFactoryMaxParallelism
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

class StreamMateApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: StreamMateContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = StreamMateContainer(this)
        if (!container.demoMode) installCatalogueMetadataLifecycle()
    }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(ARTWORK_CACHE_DIRECTORY).toOkioPath())
                // Was a flat gigabyte, which is a lot of a set-top box's storage
                // to spend on posters without ever asking. The ceiling is the
                // viewer's choice now, read here because a disk cache fixes its
                // size when it is built.
                .maxSizeBytes(ArtworkCacheSettings.limit(context).bytes)
                .build()
        }
        // Keep enough decoded artwork for the visible wall and its neighbouring
        // rows. A percentage suitable for phone photo feeds retained hundreds
        // of TV posters and pushed the Shield's native heap above 300 MB while
        // browsing; the disk cache already makes an evicted poster cheap to
        // recover.
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, ARTWORK_MEMORY_CACHE_FRACTION)
                .build()
        }
        // A library change can expose twenty new posters at once. Letting all
        // of their JPEG decoders run together saturated the Shield's four CPU
        // cores, pushed the frame p90 above 100 ms, and made GC the hottest
        // sampled task. Two decoders keep artwork progressive without taking
        // the cores Compose needs to place and focus the new wall.
        .bitmapFactoryMaxParallelism(ARTWORK_DECODE_MAX_PARALLELISM)
        .crossfade(ARTWORK_CROSSFADE_MILLIS)
        .build()

    companion object {
        const val ARTWORK_CACHE_DIRECTORY = "catalogue_artwork"
        const val ARTWORK_MEMORY_CACHE_FRACTION = 0.08
        const val ARTWORK_DECODE_MAX_PARALLELISM = 2
        const val ARTWORK_CROSSFADE_MILLIS = 140
    }
}
