package com.sohva.tv.feature.trakt.sync

import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.shelf.TraktShelfKind
import com.sohva.tv.feature.trakt.shelf.TraktShelves
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The sync loop for the active profile (spec 51 FR-21). Started by the app once Home's first
 * resume read has settled; a different profile cancels it and starts another. Each cycle
 * delivers queued scrobbles (§8 rule), refreshes Recommended and syncs, then waits 15 minutes or
 * for a request (a delivered STOP, a new sign-in) plus 3 s. While video plays the loop waits
 * (§9 rule): scrobbles still go out, the history download does not compete with the decoder.
 * Restricted profiles and the demo build idle (FR-36, -37); a profile without an account only
 * refreshes the public charts its Home shows (spec 02 HOME-FR-94).
 */
class TraktSyncLoop(
    private val host: TraktHost,
    private val sync: TraktSync,
    private val shelves: TraktShelves,
    private val playing: StateFlow<Boolean>,
    private val scope: CoroutineScope = host.appScope,
) {
    private var job: Job? = null
    private var profile: String? = null

    @Synchronized
    fun start(profile: String) {
        if (this.profile == profile && job?.isActive == true) return
        job?.cancel()
        this.profile = profile
        job = scope.launch(host.dispatchers.io) { loop(profile) }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        profile = null
    }

    private suspend fun loop(profile: String) {
        if (host.offline) return
        if (host.scrobbles(profile)) host.scrobbles.closeInterrupted(profile)
        var asked = false
        while (true) {
            playing.first { !it }
            if (host.scrobbles(profile)) cycle(profile)
            // Home's added rows (spec 02 HOME-FR-94): charts need no account, so they refresh for every
            // unrestricted profile; a request (a sign-in, a row just added) refetches the watchlists.
            host.rowsShown(profile).takeIf { it.isNotEmpty() }?.let { sync.rows(profile, it, force = asked) }
            asked = withTimeoutOrNull(CYCLE_MS) { merge(host.scrobbles.stops, host.syncRequests).first { it == profile } } != null
            if (asked) delay(AFTER_REQUEST_MS)
        }
    }

    private suspend fun cycle(profile: String) {
        host.scrobbles.deliver(profile)
        // Only the shelves the profile's Home shows (spec 02 HOME-FR-87); the watched state always syncs.
        val shown = host.shelvesShown(profile)
        val next = TraktShelfKind.WATCH_NEXT in shown
        if (!next) shelves.drop(profile, TraktShelfKind.WATCH_NEXT)
        coroutineScope {
            if (TraktShelfKind.RECOMMENDED in shown) launch { sync.recommendations(profile) }
            launch { sync.sync(profile, force = next && shelves.read(profile, TraktShelfKind.WATCH_NEXT) == null, watchNext = next) }
        }
    }

    companion object {
        const val CYCLE_MS: Long = 15L * 60 * 1000
        const val AFTER_REQUEST_MS: Long = 3_000
    }
}
