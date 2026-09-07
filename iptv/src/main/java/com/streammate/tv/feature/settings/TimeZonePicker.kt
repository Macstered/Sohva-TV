package com.streammate.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.app.deviceTimeZoneId
import com.streammate.tv.feature.common.TvListRow
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.iptv.R
import java.time.Instant
import java.time.ZoneId

/** One zone as the picker lists it: its id, the region it sits under, its city and the offset right now. */
internal data class TimeZoneChoice(val id: String, val region: String, val city: String, val offset: String)

/** The zones that were the only choices before the picker; still listed first as "Recent". */
internal val RECENT_TIME_ZONE_IDS = listOf(
    "Europe/Helsinki", "Europe/Stockholm", "Europe/Berlin", "Europe/London", "America/New_York", "UTC",
)

/** Every region/city zone the device knows, plus UTC, sorted by region then city. */
internal fun timeZoneChoices(now: Instant = Instant.now(), ids: Set<String> = ZoneId.getAvailableZoneIds()): List<TimeZoneChoice> =
    ids.asSequence()
        .filter { it == "UTC" || (it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/")) }
        .map { id -> TimeZoneChoice(id, id.substringBefore('/', "UTC"), timeZoneCity(id), timeZoneOffset(id, now)) }
        .sortedWith(compareBy({ it.region }, { it.city }))
        .toList()

/** "Europe/Helsinki" reads as "Helsinki"; "America/Argentina/Buenos_Aires" as "Buenos Aires, Argentina". */
internal fun timeZoneCity(id: String): String {
    if (!id.contains('/')) return id
    val parts = id.split('/').drop(1).map { it.replace('_', ' ') }
    return if (parts.size == 1) parts[0] else parts.last() + ", " + parts.dropLast(1).joinToString(", ")
}

/** "UTC+3", "UTC−5:30" or "UTC" for the zone's offset at [now]. */
internal fun timeZoneOffset(id: String, now: Instant = Instant.now()): String {
    val seconds = runCatching { ZoneId.of(id).rules.getOffset(now).totalSeconds }.getOrDefault(0)
    if (seconds == 0) return "UTC"
    val sign = if (seconds < 0) "−" else "+"
    val hours = Math.abs(seconds) / 3600
    val minutes = Math.abs(seconds) % 3600 / 60
    return if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:" + minutes.toString().padStart(2, '0')
}

/** The label the settings row shows for the zone in use. */
internal fun timeZoneLabel(id: String): String = timeZoneCity(id) + " · " + timeZoneOffset(id)

/** [choices] whose city, region or id contains [query], case-insensitively; all of them for a blank query. */
internal fun filterTimeZones(choices: List<TimeZoneChoice>, query: String): List<TimeZoneChoice> {
    val q = query.trim()
    if (q.isEmpty()) return choices
    return choices.filter { it.city.contains(q, true) || it.region.contains(q, true) || it.id.contains(q, true) }
}

/**
 * A full time-zone picker: the TV's own zone first, the handful of zones the
 * app used to offer under "Recent", then every zone by region, with a search
 * field for the rest. Selecting closes it.
 */
@Composable
internal fun TimeZonePickerDialog(
    currentZoneId: String,
    followsDevice: Boolean,
    onFollowDevice: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val choices = remember { timeZoneChoices() }
    var query by remember { mutableStateOf("") }
    val shown = remember(choices, query) { filterTimeZones(choices, query) }
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenAttached() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .heightIn(max = 600.dp)
                .background(palette.surface, StreamMateThemeTokens.shapes.medium)
                .padding(18.dp)
                .testTag("time-zone-picker"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.sports_timezone_title),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            TvUrlField(
                value = query,
                onValueChange = { query = it.take(40) },
                label = stringResource(R.string.time_zone_search),
                keyboardType = KeyboardType.Text,
                editOnClickOnly = true,
                compact = true,
                testTag = "time-zone-search",
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().testTag("time-zone-list")) {
                if (query.isBlank()) {
                    item(key = "@device") {
                        TvListRow(
                            label = stringResource(R.string.sports_timezone_device, timeZoneLabel(deviceTimeZoneId())),
                            onClick = onFollowDevice,
                            selected = followsDevice,
                            dense = true,
                            focusRequester = firstFocus,
                            testTag = "time-zone-device",
                        )
                    }
                    item(key = "@recent") { PickerOverline(stringResource(R.string.time_zone_recent)) }
                    val recent = RECENT_TIME_ZONE_IDS.mapNotNull { id -> choices.firstOrNull { it.id == id } }
                    recent.forEach { choice ->
                        item(key = "recent:" + choice.id) {
                            ZoneRow(choice, selected = !followsDevice && currentZoneId == choice.id, onClick = { onSelect(choice.id) })
                        }
                    }
                    item(key = "@all") { PickerOverline(stringResource(R.string.time_zone_all)) }
                }
                var lastRegion: String? = null
                shown.forEachIndexed { index, choice ->
                    if (choice.region != lastRegion) {
                        lastRegion = choice.region
                        item(key = "region:" + choice.region) { PickerOverline(choice.region) }
                    }
                    item(key = choice.id) {
                        ZoneRow(
                            choice,
                            selected = !followsDevice && currentZoneId == choice.id,
                            onClick = { onSelect(choice.id) },
                            focusRequester = if (query.isNotBlank() && index == 0) firstFocus else null,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ZoneRow(choice: TimeZoneChoice, selected: Boolean, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    TvListRow(
        label = choice.city,
        trailing = choice.offset,
        onClick = onClick,
        selected = selected,
        dense = true,
        focusRequester = focusRequester,
        testTag = "time-zone-" + choice.id,
    )
}

@Composable
private fun PickerOverline(text: String) {
    Text(
        text = text.uppercase(),
        color = StreamMateThemeTokens.palette.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
    )
}
