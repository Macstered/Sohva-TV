package com.sohva.tv.app.trakt

import android.os.SystemClock
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.BuildInfo
import com.sohva.tv.feature.trakt.TraktAccess
import com.sohva.tv.feature.trakt.TraktDispatchers
import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.store.AndroidTraktPrefs
import com.sohva.tv.feature.trakt.store.TraktAccountStore
import com.sohva.tv.feature.trakt.store.TraktCipher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Builds Trakt's host for the app (spec 51 §6): the build's credentials, the main envelope cipher
 * for the per-profile records, the shared OkHttp base, and the unrestricted-profile rule (FR-36),
 * read once and again only when profiles change. Built on first use.
 */
object TraktGraph {
    fun build(graph: AppGraph): TraktHost {
        val cipher = object : TraktCipher {
            override fun encrypt(plaintext: String): String = graph.data.cipher.encrypt(plaintext)

            override fun decrypt(value: String): String = graph.data.cipher.decrypt(value)
        }
        return TraktHost(
            credentials = TraktCredentials(BuildInfo.TRAKT_CLIENT_ID, BuildInfo.TRAKT_CLIENT_SECRET),
            http = { graph.sync.http.client },
            store = TraktAccountStore(AndroidTraktPrefs(graph.app), cipher),
            clock = graph.clock,
            dispatchers = TraktDispatchers(graph.dispatchers.io, graph.dispatchers.ui),
            access = UnrestrictedAccess(graph),
            log = graph.diagnostics,
            appScope = graph.appScope,
            offline = graph.flags.demoContent,
            monotonic = { SystemClock.elapsedRealtime() },
            forgetCache = { graph.data.traktState.forget(it) },
        )
    }

    /** FR-36: a restricted profile has no Trakt at all; the set is re-read when profiles change. */
    private class UnrestrictedAccess(private val graph: AppGraph) : TraktAccess {
        private val lock = Mutex()

        @Volatile private var restricted: Set<String>? = null

        init {
            graph.appScope.launch { graph.data.profiles.changes.collect { restricted = null } }
        }

        override suspend fun allowed(profile: String): Boolean {
            if (!graph.flags.trakt) return false
            val set = restricted ?: lock.withLock { restricted ?: graph.data.profiles.restrictedIds().also { restricted = it } }
            return profile !in set
        }
    }
}
