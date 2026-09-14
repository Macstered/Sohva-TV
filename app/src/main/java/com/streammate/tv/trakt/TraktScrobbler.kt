package com.streammate.tv.trakt

import com.sohva.tv.trakt.TraktItem
import com.sohva.tv.trakt.TraktScrobbleAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reports one playback to Trakt the way Trakt expects: start when playing,
 * pause when paused, stop when the title ends or the player is left. Trakt
 * marks a title watched itself when the stop arrives past 80 percent.
 *
 * Main-thread only, like the player callbacks that drive it.
 */
internal class TraktScrobbler(private val service: TraktService, private val scope: CoroutineScope) {
    private var profile: String? = null
    private var item: TraktItem? = null
    private var lastAction: TraktScrobbleAction? = null
    private var lastProgress = 0.0
    private var pendingPause: Job? = null

    /** A new title is on the player. [item] is null when Trakt has nothing to address it by. */
    fun begin(profile: String, item: TraktItem?) {
        this.profile = profile; this.item = item
        lastAction = null; lastProgress = 0.0
    }

    /** The latest known position, so a stop after the player already reset still carries it. */
    fun progress(percent: Double?) {
        percent?.let { lastProgress = it }
        val profile = profile ?: return
        val item = item ?: return
        if (lastAction == TraktScrobbleAction.START && percent != null && System.currentTimeMillis() - lastNoted > NOTE_INTERVAL_MILLIS) {
            lastNoted = System.currentTimeMillis()
            scope.launch { runCatching { service.noteProgress(profile, item, percent) } }
        }
    }
    private var lastNoted = 0L

    fun playing(percent: Double?) {
        progress(percent)
        // Playback resumed before the pause went out: a rebuffer, not a pause.
        if (pendingPause?.isActive == true) { pendingPause?.cancel(); pendingPause = null; return }
        if (lastAction != TraktScrobbleAction.STOP) send(TraktScrobbleAction.START, percent)
    }
    fun paused(percent: Double?) {
        progress(percent)
        if (lastAction != TraktScrobbleAction.START || pendingPause?.isActive == true) return
        pendingPause = scope.launch(Dispatchers.Main.immediate) { delay(PAUSE_SETTLE_MILLIS); pendingPause = null; send(TraktScrobbleAction.PAUSE, null) }
    }
    fun ended() { cancelPendingPause(); if (lastAction != null) send(TraktScrobbleAction.STOP, 100.0); clear() }
    fun release(percent: Double?) { cancelPendingPause(); if (lastAction != null && lastAction != TraktScrobbleAction.STOP) send(TraktScrobbleAction.STOP, percent); clear() }

    private fun cancelPendingPause() { pendingPause?.cancel(); pendingPause = null }

    private fun send(action: TraktScrobbleAction, percent: Double?) {
        val profile = profile ?: return
        val item = item ?: return
        progress(percent)
        if (action == lastAction) return
        lastAction = action
        val value = lastProgress
        scope.launch { runCatching { service.scrobble(profile, item, action, value) } }
    }

    private fun clear() { profile = null; item = null; lastAction = null; lastProgress = 0.0 }

    private companion object { const val PAUSE_SETTLE_MILLIS = 2_500L; const val NOTE_INTERVAL_MILLIS = 30_000L }
}
