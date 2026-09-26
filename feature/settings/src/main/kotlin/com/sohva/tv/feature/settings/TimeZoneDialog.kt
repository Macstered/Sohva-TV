package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.sohva.tv.core.model.settings.TimeZones
import com.sohva.tv.core.model.settings.ZoneRow
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.DialogTitle
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** One line of the zone list: an overline or a choice (null id = the TV's own zone). */
private sealed interface ZoneLine {
    data class Heading(val text: String) : ZoneLine
    data class Choice(val key: String, val id: String?, val label: String, val trailing: String?) : ZoneLine
}

/**
 * The time-zone dialog (spec 70 §4.6): the TV's own zone, Recent and every zone by region; a
 * search narrows by city, region or id. It opens scrolled to and focused on the selected row
 * (SET-FR-64, rebuild). [onChoose] gets null for the TV's own zone.
 */
@Composable
internal fun TimeZoneDialog(general: GeneralSettings, current: String?, onChoose: (String?) -> Unit, onDismiss: () -> Unit) {
    val rows by produceState<List<ZoneRow>?>(null) { value = general.zones() }
    var query by remember { mutableStateOf("") }
    val device = remember { general.deviceZone() }
    val deviceLabel = stringResource(R.string.sports_timezone_device, device.label)
    val recentTitle = stringResource(R.string.time_zone_recent)
    val allTitle = stringResource(R.string.time_zone_all)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(640.dp).heightIn(max = 600.dp).testTag("settings-time-zone-dialog"), border = Sohva.palette.outline) {
                DialogTitle(stringResource(R.string.sports_timezone_title))
                TvUrlField(
                    value = query,
                    onValueChange = { query = it.take(QUERY_MAX) },
                    label = stringResource(R.string.time_zone_search),
                    modifier = Modifier.testTag("settings-time-zone-search"),
                    input = FieldInput(keyboard = KeyboardType.Text, compact = true),
                )
                val all = rows ?: return@DialogCard
                val lines = remember(all, query) { lines(all, query, deviceLabel, recentTitle, allTitle) }
                val selectedKey = if (query.isBlank()) selectedKey(current, all) else lines.firstOrNull { it is ZoneLine.Choice }?.let { (it as ZoneLine.Choice).key }
                val focus = remember { FocusRequester() }
                val list = rememberLazyListState(initialFirstVisibleItemIndex = lines.indexOfFirst { it is ZoneLine.Choice && it.key == selectedKey }.coerceAtLeast(0))
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(lines, key = { line -> if (line is ZoneLine.Choice) line.key else "h:" + (line as ZoneLine.Heading).text }) { line ->
                        when (line) {
                            is ZoneLine.Heading -> Text(
                                line.text.uppercase(),
                                Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                                style = Sohva.typography.overline.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = Sohva.palette.textMuted,
                            )
                            is ZoneLine.Choice -> TvListRow(
                                label = line.label,
                                onClick = { onChoose(line.id) },
                                modifier = (if (line.key == selectedKey) Modifier.focusRequester(focus) else Modifier).testTag("settings-time-zone-${line.key}"),
                                trailing = line.trailing,
                                state = SurfaceState(selected = query.isBlank() && line.id == current && (line.key == selectedKey)),
                            )
                        }
                    }
                }
                LaunchedEffect(selectedKey, query) { focus.requestFocusWhenAttached() }
            }
        }
    }
}

/** The key of the row that opens selected: the TV's own, the zone under Recent, else under its region. */
private fun selectedKey(current: String?, all: List<ZoneRow>): String = when {
    current == null -> DEVICE
    current in TimeZones.RECENT && all.any { it.id == current } -> "recent:$current"
    else -> "zone:$current"
}

private fun lines(all: List<ZoneRow>, query: String, deviceLabel: String, recent: String, allZones: String): List<ZoneLine> {
    val out = ArrayList<ZoneLine>()
    val shown = TimeZones.filter(all, query)
    if (query.isBlank()) {
        out += ZoneLine.Choice(DEVICE, null, deviceLabel, null)
        out += ZoneLine.Heading(recent)
        val byId = all.associateBy { it.id }
        TimeZones.RECENT.mapNotNull(byId::get).forEach { out += ZoneLine.Choice("recent:${it.id}", it.id, it.city, it.offset) }
        out += ZoneLine.Heading(allZones)
    }
    var region: String? = null
    for (row in shown) {
        if (row.region != region) {
            region = row.region
            out += ZoneLine.Heading(row.region)
        }
        out += ZoneLine.Choice("zone:${row.id}", row.id, row.city, row.offset)
    }
    return out
}

private const val DEVICE = "device"
private const val QUERY_MAX = 40
