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
