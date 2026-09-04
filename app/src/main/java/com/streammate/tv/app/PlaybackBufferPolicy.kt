package com.streammate.tv.app

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

internal data class PlaybackBufferDurations(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
)

internal object PlaybackBufferPolicy {
    fun durations(profile: PlaybackBufferProfile): PlaybackBufferDurations? = when (profile) {
        PlaybackBufferProfile.DEFAULT -> null
        PlaybackBufferProfile.LOW_LATENCY -> PlaybackBufferDurations(
            minBufferMs = 5_000,
            maxBufferMs = 15_000,
            bufferForPlaybackMs = 1_000,
            bufferForPlaybackAfterRebufferMs = 2_000,
        )
        PlaybackBufferProfile.STABILITY -> PlaybackBufferDurations(
            minBufferMs = 60_000,
            maxBufferMs = 120_000,
            bufferForPlaybackMs = 5_000,
            bufferForPlaybackAfterRebufferMs = 10_000,
        )
    }

    @OptIn(UnstableApi::class)
    fun loadControl(profile: PlaybackBufferProfile): DefaultLoadControl? =
        durations(profile)?.let { durations ->
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    durations.minBufferMs,
                    durations.maxBufferMs,
                    durations.bufferForPlaybackMs,
                    durations.bufferForPlaybackAfterRebufferMs,
                )
                .build()
        }
}
