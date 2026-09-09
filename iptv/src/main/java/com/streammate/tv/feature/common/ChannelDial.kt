package com.streammate.tv.feature.common

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.iptv.R

/**
 * Digits typed on the remote pick a channel by the number it shows: its own
 * number when it has one, otherwise its place in the list. The guide and the
 * player share the rule, so a number read in one lands on the same channel in
 * the other.
 */
object ChannelDial {
    const val MAX_DIGITS = 4

    /** How long after the last digit the number is taken as complete. */
    const val TIMEOUT_MILLIS = 2_000L

    /** How long "no channel N" stays on screen. */
    const val MESSAGE_MILLIS = 1_500L

    fun digitOf(key: Key): Int? = when (key) {
        Key.Zero, Key.NumPad0 -> 0
        Key.One, Key.NumPad1 -> 1
        Key.Two, Key.NumPad2 -> 2
        Key.Three, Key.NumPad3 -> 3
        Key.Four, Key.NumPad4 -> 4
        Key.Five, Key.NumPad5 -> 5
        Key.Six, Key.NumPad6 -> 6
        Key.Seven, Key.NumPad7 -> 7
        Key.Eight, Key.NumPad8 -> 8
        Key.Nine, Key.NumPad9 -> 9
        else -> null
    }

    fun digitOf(keyCode: Int): Int? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }

    /**
     * The index of the channel that shows [number] in a list whose entries
     * carry [ownNumbers]: a channel's own number first, then the position of
     * a channel that shows none of its own. Null when nothing shows it.
     */
    fun indexFor(ownNumbers: List<Int?>, number: Int): Int? {
        val own = ownNumbers.indexOf(number)
        if (own >= 0) return own
        val position = number - 1
        return position.takeIf { it in ownNumbers.indices && ownNumbers[it] == null }
    }
}

/** The number being dialled, or the answer when nothing showed it; nothing at all when neither. */
@Composable
fun ChannelDialOverlay(buffer: String, message: String?, modifier: Modifier = Modifier) {
    val text = when {
        buffer.isNotEmpty() -> stringResource(R.string.dial_channel, buffer)
        message != null -> message
        else -> return
    }
    val palette = StreamMateThemeTokens.palette
    Text(
        text = text,
        color = palette.textPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            // The panel colour, not the translucent surface ladder: over a
            // bright picture the ladder's white went unreadable.
            .background(palette.panel.copy(alpha = 0.94f), StreamMateThemeTokens.shapes.medium)
            .border(1.dp, palette.outline, StreamMateThemeTokens.shapes.medium)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("channel-dial"),
    )
}
