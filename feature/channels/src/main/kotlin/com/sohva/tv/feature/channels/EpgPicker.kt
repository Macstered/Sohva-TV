package com.sohva.tv.feature.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sohva.tv.core.data.database.EpgChannelOption
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.delay

/**
 * The guide mapping picker (spec 21 CHAN-NFR-06, decision on Q4): Automatic first, then the
 * source's XMLTV channels in pages of 200, searchable, the current one marked. Choosing keeps the
 * change in the editor until Save (CHAN-FR-24). Closing returns focus to "Change EPG channel".
 */
@Composable
internal fun EpgPicker(model: ChannelsModel, current: String?, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf<EpgChannelOption>() }
    var more by remember { mutableStateOf(true) }
    val first = remember { FocusRequester() }
    // A new search starts again from the first page, 250 ms after the last change.
    LaunchedEffect(search) {
        delay(if (search.isEmpty()) 0 else SEARCH_DEBOUNCE_MS)
        val page = model.epgPage(search, null)
        options.clear()
        options += page
        more = page.size >= PAGE
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(560.dp).roundFill(Sohva.palette.surface, Sohva.shapes.medium).padding(18.dp).testTag("channels-epg-picker"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.channels_epg_mapping), style = Sohva.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
            TvUrlField(search, { search = it.take(100) }, stringResource(R.string.channels_search_hint), Modifier.fillMaxWidth(), icon = TvIcons.Search, input = FieldInput(keyboard = KeyboardType.Text))
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item(key = "@automatic") {
                    TvListRow(
                        stringResource(R.string.channels_automatic),
                        {
                            model.chooseEpg(null, null)
                            onDismiss()
                        },
                        Modifier.focusRequester(first).testTag("channels-epg-automatic"),
                        state = SurfaceState(selected = current == null),
                        layout = ListRowLayout(dense = true),
                    )
                }
                items(options, key = { it.epgId }) { option ->
                    TvListRow(
                        option.displayName ?: option.epgId,
                        {
                            model.chooseEpg(option.epgId, option.displayName)
                            onDismiss()
                        },
                        Modifier.testTag("channels-epg-${option.epgId}"),
                        supporting = option.epgId.takeIf { option.displayName != null },
                        state = SurfaceState(selected = option.epgId == current),
                        layout = ListRowLayout(dense = true),
                    )
                }
                if (more && options.isNotEmpty()) {
                    item(key = "@more") {
                        // Reaching the end reads the next page, keyed on the last row shown.
                        LaunchedEffect(options.size) {
                            val next = model.epgPage(search, options.last())
                            options += next
                            more = next.size >= PAGE
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
    }
}

private const val PAGE = 200
private const val SEARCH_DEBOUNCE_MS = 250L
