package com.sohva.tv.app.metadata

import androidx.annotation.VisibleForTesting
import androidx.work.WorkManager
import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.metadata.MetadataSettings
import com.sohva.tv.core.data.prefs.LocaleStore
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.net.metadata.MetadataHttp
import com.sohva.tv.core.net.metadata.TmdbClient
import com.sohva.tv.core.net.metadata.TvmazeClient
import com.sohva.tv.core.sync.metadata.Enrichment
import com.sohva.tv.core.sync.metadata.EnrichmentScheduler
import com.sohva.tv.core.sync.metadata.MetadataService
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Metadata's side of the graph (spec 41): settings, the provider clients on their own HTTP client
 * (sharing the provider client's connection pool), the lookup service and the background
 * enrichment. Everything is built on first use, never at start-up (§9.7).
 */
class MetadataGraph(private val graph: AppGraph) {
    private var endpoints: Pair<HttpUrl, HttpUrl> = "https://api.themoviedb.org/3/".toHttpUrl() to "https://api.tvmaze.com/".toHttpUrl()

    val settings: MetadataSettings by lazy {
        MetadataSettings(
            graph.data.secrets,
            graph.data.preferences,
            { LocaleStore(graph.app).languageTag() ?: Locale.getDefault().language },
            graph.dispatchers.io,
        )
    }

    private val http: MetadataHttp by lazy { MetadataHttp(graph.sync.http.client) }

    @Volatile
    private var built: MetadataService? = null

    val service: MetadataService
        get() = built ?: synchronized(this) {
            built ?: MetadataService(
                graph.data.database, settings, TmdbClient(http, endpoints.first), TvmazeClient(http, endpoints.second),
                graph.clock, graph.dispatchers.io, graph.appScope,
            ).also { built = it }
        }

    /**
     * Device tests answer TMDB and TVmaze from their own server: the next lookup builds a service
     * on these roots, with empty memory caches. Nothing else calls it.
     */
    @VisibleForTesting
    fun useEndpoints(tmdb: HttpUrl, tvmaze: HttpUrl): Unit = synchronized(this) {
        endpoints = tmdb to tvmaze
        built = null
    }

    val passes: LibraryPasses by lazy { LibraryPasses(graph.data.database) }

    val enrichment: Enrichment by lazy {
        Enrichment(
            db = graph.data.database,
            service = service,
            passes = passes,
            clock = graph.clock,
            bulk = graph.dispatchers.bulk,
            log = graph.diagnostics,
            preferredCopy = { graph.data.preferences.preferredCopy() },
            enabledSources = { graph.data.sources.all().filter { it.enabled }.map { it.id } },
            // No lookups while video plays or the viewer is in the app (spec 41 META-FR-63, -64).
            paused = { graph.playbackActive.value || graph.inForeground.value },
        )
    }

    val scheduler: EnrichmentScheduler by lazy { EnrichmentScheduler { WorkManager.getInstance(graph.app) } }
}
