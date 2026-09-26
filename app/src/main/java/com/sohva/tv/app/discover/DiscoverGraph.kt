package com.sohva.tv.app.discover

import com.sohva.tv.app.AppGraph
import com.sohva.tv.feature.discover.DiscoverDispatchers
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.store.DiscoverAccess
import com.sohva.tv.feature.discover.store.DiscoverCipher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Builds Discover's host for the app (spec 50 §6): only where the build allows addons (flags), and
 * only when first used. Access (ADDON-FR-02) is the active profile and no restriction; the restricted
 * set is read once and again only when profiles or their allowed groups change, so the checks
 * around every addon request cost no I/O (§9).
 */
object DiscoverGraph {
    /**
     * Home's Discover part (spec 02 HOME-FR-17, -18): only where addons are allowed and for a
     * profile that may use them; the newest resumable entry per title, decrypted only for these.
     * No addon client, cache or repository is built for it (§9.1).
     */
    suspend fun continueItems(graph: AppGraph, limit: Int): List<com.sohva.tv.core.data.vod.ContinueItem> {
        val host = graph.discover ?: return emptyList()
        val profile = graph.data.profiles.activeId
        if (!host.access.allowed(profile)) return emptyList()
        return withContext(graph.dispatchers.io) { host.progress.continueEntries(profile, limit) }.map { e ->
            val id = e.identity
            val name = e.artwork.name.ifBlank { e.title }
            com.sohva.tv.core.data.vod.ContinueItem(
                contentKey = "discover:${id.installation}:${id.mediaId}:${id.videoId}",
                groupKey = "discover:${id.installation}:${id.mediaType}:${id.mediaId}",
                title = name, year = null, posterUrl = e.artwork.poster, replacementPoster = null, replacePoster = false,
                season = null, episode = null, episodeTitle = null, tmdbId = null,
                positionMs = e.positionMs, durationMs = e.durationMs ?: 0, updatedAt = e.updatedAt,
                discover = com.sohva.tv.core.data.vod.DiscoverResume(
                    id.installation, id.mediaType, id.mediaId, id.videoId,
                    subtitle = e.title.takeIf { it.isNotBlank() && it != name },
                    backdrop = e.artwork.background ?: e.artwork.poster,
                ),
            )
        }
    }

    fun build(graph: AppGraph): DiscoverHost {
        val access = CachedAccess(graph)
        val cipher = object : DiscoverCipher {
            override fun encrypt(plaintext: String): String = graph.data.cipher.encrypt(plaintext)
            override fun decrypt(value: String): String = graph.data.cipher.decrypt(value)
            override fun seal(plaintext: ByteArray, aad: ByteArray): ByteArray = graph.data.cipher.seal(plaintext, aad)
            override fun open(sealed: ByteArray, aad: ByteArray): ByteArray = graph.data.cipher.open(sealed, aad)
        }
        return DiscoverHost(
            graph.app, cipher, { graph.sync.http.client }, graph.clock,
            DiscoverDispatchers(graph.dispatchers.io, graph.dispatchers.ui), access, graph.diagnostics, graph.appScope,
        ) { withContext(graph.dispatchers.io) { graph.data.preferences.playback() } }
    }

    private class CachedAccess(private val graph: AppGraph) : DiscoverAccess {
        private val lock = Mutex()

        @Volatile private var restricted: Set<String>? = null

        init {
            // Profiles, the active one and allowed groups: a change re-reads the restricted set.
            graph.appScope.launch { graph.data.profiles.changes.collect { restricted = null } }
        }

        override fun activeProfile(): String = graph.data.profiles.activeId

        override suspend fun allowed(profile: String): Boolean {
            if (!graph.flags.discover || profile != graph.data.profiles.activeId) return false
            val set = restricted ?: lock.withLock { restricted ?: graph.data.profiles.restrictedIds().also { restricted = it } }
            return profile !in set
        }
    }
}
