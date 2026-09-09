package com.streammate.tv.feature.player

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How the player answers a number typed on the remote, handed down as a
 * composition local rather than as two more parameters of the player's
 * largest composable, whose parameter count already once produced dex the
 * verifier refused.
 */
class PlayerDialing(
    /** Whether the channel list names each channel's number, as the guide does. */
    val showNumbers: Boolean,
    /** The id of the channel that shows [number] in the guide's own order at the given time, or null. */
    val lookup: suspend (number: Int, nowEpochMillis: Long) -> String?,
)

val LocalPlayerDialing = staticCompositionLocalOf { PlayerDialing(showNumbers = true) { _, _ -> null } }
