package com.streammate.tv.feature.player

import com.streammate.tv.app.PlaybackReconnectPolicy

internal fun reconnectMaxAutomaticAttempts(policy: PlaybackReconnectPolicy): Int = when (policy) {
    PlaybackReconnectPolicy.STANDARD -> 3
    PlaybackReconnectPolicy.PERSISTENT -> 8
}

internal fun reconnectDelayMillis(policy: PlaybackReconnectPolicy, attempt: Int): Long {
    require(attempt in 1..reconnectMaxAutomaticAttempts(policy)) {
        "Reconnect attempt is outside the configured policy"
    }
    return when (policy) {
        PlaybackReconnectPolicy.STANDARD -> STANDARD_DELAY_MILLIS * attempt
        PlaybackReconnectPolicy.PERSISTENT -> PERSISTENT_DELAYS_MILLIS[attempt - 1]
    }
}

private const val STANDARD_DELAY_MILLIS = 2_000L
private val PERSISTENT_DELAYS_MILLIS = longArrayOf(
    2_000L,
    4_000L,
    8_000L,
    16_000L,
    30_000L,
    30_000L,
    30_000L,
    30_000L,
)
