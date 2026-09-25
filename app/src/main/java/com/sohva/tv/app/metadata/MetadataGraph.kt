package com.sohva.tv.app.metadata

import androidx.annotation.VisibleForTesting
import androidx.work.WorkManager
import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.metadata.MetadataSettings
import com.sohva.tv.core.data.prefs.LocaleStore
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.net.metadata.MetadataHttp
import com.sohva.tv.core.net.metadata.MetadataRecord
import com.sohva.tv.core.net.metadata.TmdbClient
import com.sohva.tv.core.net.metadata.TvmazeClient
import com.sohva.tv.core.sync.metadata.Enrichment
import com.sohva.tv.core.sync.metadata.EnrichmentScheduler
import com.sohva.tv.core.sync.metadata.MatchChoices
import com.sohva.tv.core.sync.metadata.MetadataRequest
import com.sohva.tv.core.sync.metadata.MetadataReset
import com.sohva.tv.core.sync.metadata.MetadataService
import com.sohva.tv.core.sync.metadata.VisibleTitle
import java.util.Locale
import kotlinx.coroutines.withContext
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

    /**
     * The service and everything that holds it, built together on first use, so the test hook
     * below replaces all of them at once.
     */
    private class Parts(val service: MetadataService, val enrichment: Enrichment, val choices: MatchChoices, val reset: MetadataReset)

    @Volatile
    private var built: Parts? = null

    private val parts: Parts
        get() = built ?: synchronized(this) { built ?: build().also { built = it } }

    val service: MetadataService get() = parts.service

    val enrichment: Enrichment get() = parts.enrichment

    /** "Wrong details?" choices and their undo (spec 41 §4.14). */
    val choices: MatchChoices get() = parts.choices

    /** Language change and "Clear metadata cache" (spec 41 §4.15). */
    val reset: MetadataReset get() = parts.reset

    private fun build(): Parts {
        val db = graph.data.database
        val service = MetadataService(
            db, settings, TmdbClient(http, endpoints.first), TvmazeClient(http, endpoints.second), graph.clock, graph.dispatchers.io, graph.appScope,
        )
        val enrichment = Enrichment(
            db = db,
            service = service,
            passes = passes,
            clock = graph.clock,
            bulk = graph.dispatchers.bulk,
            log = graph.diagnostics,
            preferredCopy = { graph.data.preferences.preferredCopy() },
            enabledSources = { graph.data.sources.all().filter { it.enabled }.map { it.id } },
            // No lookups while video plays or the viewer is in the app (spec 41 META-FR-63, -64).
            paused = { graph.playbackActive.value || graph.inForeground.value },
            playing = { graph.playbackActive.value },
        )
        val choices = MatchChoices(db, service, passes, graph.clock, graph.dispatchers.io) { graph.data.preferences.preferredCopy() }
        return Parts(service, enrichment, choices, MetadataReset(db, service, passes, graph.dispatchers.bulk))
    }

    /**
     * The memory cache's answer for a screen's first frame, on the main thread: nothing is built or
     * read here. Before the settings have been read once (by a lookup on the io dispatcher), or
     * with metadata off, there is no answer.
     */
    fun cached(request: MetadataRequest): MetadataRecord? {
        if (settings.config.value?.enabled != true) return null
        return built?.service?.cached(request)
    }

    /** A lookup on the io dispatcher; null when metadata is off (spec 41 META-FR-43). */
    suspend fun enrich(request: MetadataRequest): MetadataRecord? = withContext(graph.dispatchers.io) {
        if (!settings.current().enabled) null else service.enrich(request)
    }

    /** The wall's foreground budget (spec 41 Q10), on the bulk dispatcher; nothing with metadata off. */
    suspend fun lookUpVisible(titles: List<VisibleTitle>): Unit = withContext(graph.dispatchers.io) {
        if (settings.current().enabled) enrichment.lookUpVisible(titles)
    }

    /**
     * Device tests answer TMDB and TVmaze from their own server: the next lookup builds the
     * service (and the worker, choices and resets that hold it) on these roots, with empty memory
     * caches. Nothing else calls it.
     */
    @VisibleForTesting
    fun useEndpoints(tmdb: HttpUrl, tvmaze: HttpUrl): Unit = synchronized(this) {
        endpoints = tmdb to tvmaze
        built = null
    }

    val passes: LibraryPasses by lazy { LibraryPasses(graph.data.database) }

    val scheduler: EnrichmentScheduler by lazy { EnrichmentScheduler { WorkManager.getInstance(graph.app) } }
}
