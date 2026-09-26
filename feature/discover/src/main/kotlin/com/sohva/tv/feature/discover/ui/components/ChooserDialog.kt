package com.sohva.tv.feature.discover.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.DialogTitle
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached

/**
 * A chooser (FR-121): 480 dp wide, at most 430 dp tall, the current option selected and focused;
 * OK chooses and closes, Back closes. Option labels are shown exactly as the provider supplied them.
 */
@Composable
fun ChooserDialog(title: String, options: List<String>, selected: Int, choose: (Int) -> Unit, dismiss: () -> Unit, tag: String = "discover-chooser") {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogCard(Modifier.width(480.dp).heightIn(max = 430.dp).testTag(tag)) {
            DialogTitle(title)
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(options) { i, label ->
                    TvListRow(
                        label, {
                            choose(i)
                            dismiss()
                        },
                        (if (i == selected.coerceAtLeast(0)) Modifier.focusRequester(first) else Modifier).testTag("$tag-$i"),
                        state = SurfaceState(selected = i == selected),
                        layout = ListRowLayout(dense = true),
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
}
