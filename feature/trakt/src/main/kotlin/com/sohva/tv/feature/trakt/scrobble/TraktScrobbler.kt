package com.sohva.tv.feature.trakt.scrobble

import com.sohva.tv.core.model.player.TitleScrobbler
import com.sohva.tv.feature.trakt.protocol.ScrobbleAction
import com.sohva.tv.feature.trakt.protocol.TraktItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One title on the player (spec 51 FR-15): START when it plays (unless the last report was STOP),
 * PAUSE 2.5 s after it stops (a rebuffer that resumes sooner cancels it), STOP at 100 % at its end,
 * STOP at the current percentage when it leaves after anything was sent. The same action is never
 * sent twice in a row. [item] may arrive after playback began (the VOD identity is resolved off the
 * main thread); a START then goes out at once if it is playing.
 */
class TraktScrobbler(
    private val profile: String,
    private val sink: (String, TraktItem, ScrobbleAction, Double) -> Unit,
    private val active: (String, Double) -> Unit,
    private val scope: CoroutineScope,
) : TitleScrobbler {
    private var item: TraktItem? = null
    private var last: ScrobbleAction? = null
    private var pause: Job? = null
    private var isPlaying = false
    private var percent = 0.0
    private var released = false

    /** The title's Trakt item, once known; null means this title is never sent. */
    fun begin(resolved: TraktItem?) {
        if (released) return
        item = resolved
        if (resolved != null && isPlaying) send(ScrobbleAction.START)
    }

    override fun playing(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        track(positionMs, durationMs)
        this.isPlaying = isPlaying
        if (isPlaying) {
            // Playing again before the settle ran out: it was a rebuffer, not a pause.
            if (pause?.isActive == true) {
                pause?.cancel()
                return
            }
            if (last != ScrobbleAction.START && last != ScrobbleAction.STOP) send(ScrobbleAction.START)
        } else if (last == ScrobbleAction.START) {
            pause?.cancel()
            pause = scope.launch {
                delay(SETTLE_MS)
                send(ScrobbleAction.PAUSE)
            }
        }
    }

    override fun progress(positionMs: Long, durationMs: Long) {
        track(positionMs, durationMs)
        if (last == ScrobbleAction.START) active(profile, percent)
    }

    override fun ended() {
        pause?.cancel()
        percent = 100.0
        send(ScrobbleAction.STOP)
        clear()
    }

    override fun release(positionMs: Long, durationMs: Long) {
        pause?.cancel()
        track(positionMs, durationMs)
        if (last != null && last != ScrobbleAction.STOP) send(ScrobbleAction.STOP)
        clear()
        released = true
    }

    /** position / duration × 100, clamped; the last known value while the duration is unknown. */
    private fun track(positionMs: Long, durationMs: Long) {
        if (durationMs > 0) percent = (positionMs.toDouble() / durationMs * 100).coerceIn(0.0, 100.0)
    }

    private fun send(action: ScrobbleAction) {
        val i = item ?: return
        if (last == action) return
        last = action
        sink(profile, i, action, percent)
    }

    private fun clear() {
        item = null
        isPlaying = false
    }

    companion object {
        const val SETTLE_MS: Long = 2_500
    }
}
