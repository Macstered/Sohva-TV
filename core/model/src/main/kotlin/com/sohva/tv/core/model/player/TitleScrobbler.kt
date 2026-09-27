package com.sohva.tv.core.model.player

/**
 * One title's scrobbles (spec 51 FR-15), driven only by player callbacks on the main thread. The
 * players know nothing about Trakt; a build or profile without an account gets no scrobbler.
 */
interface TitleScrobbler {
    /** The player started or stopped playing (a stop may be a rebuffer: the sink waits before a pause). */
    fun playing(isPlaying: Boolean, positionMs: Long, durationMs: Long)

    /** A periodic position while it plays (the players' own progress ticks). */
    fun progress(positionMs: Long, durationMs: Long)

    /** The stream reached its end. */
    fun ended()

    /** The title left the player (next item, Back, service gone). */
    fun release(positionMs: Long, durationMs: Long)
}
