package com.sohva.tv.ui.design.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.focus.SurfaceState

/**
 * A button for something that cannot be undone: the first OK turns it into its own question in
 * the danger style ("Delete list?"), the second OK acts, and moving focus away withdraws the
 * question. The control itself never leaves the screen, so focus has nowhere to fall
 * (AGENTS.md §5 rule 2), and the row it sits in keeps its layout: a question with its own Delete
 * and Cancel buttons does not fit beside five other buttons.
 */
@Composable
fun TvConfirmButton(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    state: SurfaceState = SurfaceState(),
    compact: Boolean = false,
) {
    var asking by remember { mutableStateOf(false) }
    TvActionButton(
        label = if (asking) stringResource(R.string.confirm_question, label) else label,
        onClick = {
            if (asking) {
                asking = false
                onConfirm()
            } else {
                asking = true
            }
        },
        modifier = modifier.onFocusChanged { if (!it.hasFocus) asking = false },
        icon = icon,
        state = if (asking) state.copy(danger = true) else state,
        compact = compact,
    )
}
