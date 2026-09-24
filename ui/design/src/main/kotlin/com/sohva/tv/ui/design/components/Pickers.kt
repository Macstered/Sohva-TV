package com.sohva.tv.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.theme.Sohva

/** One choice of a [SinglePickerDialog]. */
@Immutable
data class PickerChoice<T>(val value: T, val label: String, val description: String? = null, val tag: String? = null)

/**
 * The single-choice picker of settings.md §5: a platform dialog, 560 dp wide and at most 620 dp
 * tall, `panel` with a 1 dp `outline`, title 18 sp Bold. It opens scrolled to and focused on the
 * current value; a choice closes it; Back closes it unchanged (spec 70 SET-FR-03). The caller
 * moves focus back to the row that opened it.
 */
@Composable
fun <T> SinglePickerDialog(
    title: String,
    choices: List<PickerChoice<T>>,
    current: T,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
    tag: String = "picker",
) {
    val selected = choices.indexOfFirst { it.value == current }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(560.dp).heightIn(max = 620.dp).testTag(tag), border = Sohva.palette.outline) {
                DialogTitle(title)
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(choices, key = { index, _ -> index }) { index, choice ->
                        val chosen = index == selected
                        PickerRow(
                            label = choice.label,
                            onClick = { onChoose(choice.value) },
                            modifier = (if (chosen) Modifier.focusRequester(focus) else Modifier).testTag(choice.tag ?: "$tag-$index"),
                            description = choice.description,
                            state = SurfaceState(selected = chosen),
                        )
                    }
                }
            }
        }
        // Inside the dialog: it starts with the dialog's own composition, not before its window exists.
        LaunchedEffect(Unit) { focus.requestFocusWhenAttached() }
    }
}
