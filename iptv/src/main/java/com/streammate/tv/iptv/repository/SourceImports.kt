package com.streammate.tv.iptv.repository

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One import of a kind for a source at a time, whichever screen, worker or
 * service starts it.
 *
 * Activating an import deletes every other snapshot of its kind for the
 * source, including one a second import is still staging. Two imports at
 * once, as "Sync everything" in the background and "Refresh channels" on
 * screen make, could end with the second activating a snapshot the first had
 * already emptied: on 23 September 2026 a playlist whose address had just
 * been changed was reported as 1,826 channels imported and was not in the
 * guide at all. The second import now waits for the first and then runs in
 * full.
 *
 * Not re-entrant: an import that hands over to another of the same kind for
 * the same source (the Xtream guide to the XMLTV one) must lock only once.
 */
object SourceImports {
    const val CATALOGUE_KIND = "catalogue"

    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> oneAtATime(sourceId: String, kind: String, import: suspend () -> T): T =
        locks.computeIfAbsent("$kind\t$sourceId") { Mutex() }.withLock { import() }
}
