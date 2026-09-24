package com.sohva.tv.core.model.concurrent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Who started a bulk job; decides whether it gives way to the viewer (plan/03 §4.7 rule 2). */
enum class WorkOrigin {
    /** Sync now, first import of a new source, restore: keeps running, paced during playback. */
    VIEWER,

    /** Periodic refresh, metadata, maintenance: waits while video plays or the app is in front. */
    AUTOMATIC,
}

/**
 * Bulk loops call [awaitTurn] between pages. Automatic work suspends while a video plays or the
 * app is in the foreground; viewer-started work continues but leaves at least
 * [VIEWER_PAGE_PAUSE_MS] between pages during playback, so the decoder keeps its cores
 * (plan/07 §4.4 rule 9).
 */
class PauseGate(
    private val playbackActive: StateFlow<Boolean>,
    private val appInForeground: StateFlow<Boolean>,
) {
    suspend fun awaitTurn(origin: WorkOrigin) {
        when (origin) {
            WorkOrigin.VIEWER -> if (playbackActive.value) delay(VIEWER_PAGE_PAUSE_MS)
            WorkOrigin.AUTOMATIC -> combine(playbackActive, appInForeground) { playing, front ->
                !playing && !front
            }.first { it }
        }
    }

    companion object {
        const val VIEWER_PAGE_PAUSE_MS: Long = 200
    }
}
