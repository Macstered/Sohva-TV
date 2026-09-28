package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.vod.CustomGroup
import com.sohva.tv.core.model.vod.CustomGroups
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.SettingsSwitchRow
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.text.genreLabel
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * "Groups of your own" in Settings › Library (spec 42 ORG-FR-61): one row per group opening its
 * editor, "None yet" when there is none, and "Add a group", disabled at 24 with a note (ORG-FR-64).
 * Focus returns to the row that opened the editor (a deleted group's: the Add button).
 */
@Composable
internal fun CustomGroupsSection(library: LibrarySettings, below: FocusRequester) {
    val groups by library.customGroups.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CustomGroup?>(null) }
    var open by remember { mutableStateOf(false) }
    var returnTo by remember { mutableStateOf<String?>(null) }
    val rows = remember { HashMap<String, FocusRequester>() }
    val add = remember { FocusRequester() }
    val full = groups.size >= CustomGroups.MAX
    SettingsOverline(stringResource(R.string.custom_group_heading))
    Text(
        stringResource(R.string.custom_group_help), Modifier.padding(horizontal = 14.dp),
        style = Sohva.typography.label.copy(fontWeight = FontWeight.Normal), color = Sohva.palette.textMuted,
    )
    SettingsGroup {
        if (groups.isEmpty()) SettingsRow(stringResource(R.string.custom_group_none), icon = TvIcons.Info)
        for (g in groups) {
            key(g.id) {
                // The map may hold it already: focus return creates it before the row is composed.
                val requester = remember { rows.getOrPut(g.id) { FocusRequester() } }
                SettingsValueRow(
                    title = g.name,
                    value = summary(g),
                    onClick = {
                        editing = g
                        open = true
                    },
                    modifier = Modifier.focusRequester(requester).testTag("custom-group-${g.id}"),
                    divider = true,
                )
            }
        }
        SettingsRow(stringResource(R.string.custom_group_add), subtitle = if (full) stringResource(R.string.custom_group_limit) else null) {
            TvActionButton(
                stringResource(R.string.custom_group_add),
                {
                    if (!full) {
                        editing = null
                        open = true
                    }
                },
                // Down goes to the first Maintenance button, not the one nearest the middle.
                Modifier.focusRequester(add).focusProperties { down = below }.testTag("custom-group-add"),
                state = SurfaceState(enabled = !full),
                compact = true,
            )
        }
    }
    if (open) {
        CustomGroupEditor(library, editing) { closedOn ->
            open = false
            returnTo = closedOn ?: ADD
        }
    }
    LaunchedEffect(returnTo) {
        val target = returnTo ?: return@LaunchedEffect
        // A saved group's row appears once the preferences have been written: wait for it (bounded).
        val placed = target != ADD && withTimeoutOrNull(ROW_WAIT_MS) {
            snapshotFlow { groups.any { it.id == target } }.first { it }
            // The list changes a frame before the row is composed; the row takes this same requester.
            rows.getOrPut(target) { FocusRequester() }.requestFocusWhenAttached()
        } == true
        if (!placed) add.requestFocusWhenAttached()
        returnTo = null
    }
}

private const val ADD = "@add"
private const val ROW_WAIT_MS = 3_000L

/** ORG-FR-62: genres in vocabulary order, then the years, then the rating, joined " · ". */
@Composable
private fun summary(g: CustomGroup): String {
    val genres = g.genres.sortedBy { it.ordinal }.map { stringResource(genreLabel(it)) }
    val years = when {
        g.fromYear != null && g.toYear != null -> "${g.fromYear}-${g.toYear}"
        g.fromYear != null -> "${g.fromYear}-"
        g.toYear != null -> "-${g.toYear}"
        else -> null
    }
    val rating = g.minRating?.let { stringResource(R.string.custom_group_rating_at_least, it) }
    return listOfNotNull(genres.joinToString(", ").takeIf { it.isNotEmpty() }, years, rating).joinToString(" · ")
}

/**
 * The editor (ORG-FR-63, design §5.2): name, a switch per genre present in the library, from and to
 * year, rating at least; Save only when the group would be usable, Delete for a saved group,
 * Close. Back closes without saving. [onClosed] gets the id whose row should take focus.
 */
@Composable
private fun CustomGroupEditor(library: LibrarySettings, initial: CustomGroup?, onClosed: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var chosen by remember { mutableStateOf(initial?.genres.orEmpty()) }
    var from by remember { mutableStateOf(initial?.fromYear?.toString().orEmpty()) }
    var to by remember { mutableStateOf(initial?.toYear?.toString().orEmpty()) }
    var rating by remember { mutableStateOf(initial?.minRating?.let { String.format(Locale.ROOT, "%.1f", it) }.orEmpty()) }
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    LaunchedEffect(Unit) { genres = library.libraryGenres() }
    // A random id that never changes (ORG-FR-64: beta 23 made it from the name, so names collided).
    val newId = remember { "group-" + UUID.randomUUID() }
    val draft = CustomGroup(
        id = initial?.id ?: newId,
        name = name.trim().take(CustomGroups.NAME_MAX),
        genres = chosen,
        fromYear = CustomGroups.year(from),
        toYear = CustomGroups.year(to),
        minRating = CustomGroups.rating(rating),
    )
    val first = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    Dialog(onDismissRequest = { onClosed(initial?.id) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.fillMaxWidth(0.66f).fillMaxHeight(0.88f).testTag("custom-group-editor"), corner = Sohva.shapes.large, padding = 24.dp) {
                Text(
                    stringResource(R.string.custom_group_title),
                    style = Sohva.typography.display.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
                    color = Sohva.palette.textPrimary,
                )
                TvUrlField(
                    name, { name = it.take(CustomGroups.NAME_MAX) }, stringResource(R.string.custom_group_name),
                    Modifier.fillMaxWidth().focusRequester(first).testTag("custom-group-name"), input = FieldInput(keyboard = KeyboardType.Text, compact = true),
                )
                SettingsOverline(stringResource(R.string.custom_group_genres))
                LazyColumn(Modifier.weight(1f).testTag("custom-group-genres"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(genres, key = { it.wire }) { genre ->
                        val on = genre in chosen
                        SettingsSwitchRow(
                            stringResource(genreLabel(genre)), on, { chosen = if (on) chosen - genre else chosen + genre },
                            Modifier.testTag("custom-group-genre-${genre.wire}"), if (on) TvIcons.Check else null,
                        )
                    }
                }
                // Down from any of the three fields goes to Save, the first button, not the one nearest.
                Row(Modifier.focusProperties { down = save }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val digits = FieldInput(keyboard = KeyboardType.Number, compact = true)
                    TvUrlField(from, { from = it.filter(Char::isDigit).take(4) }, stringResource(R.string.custom_group_from_year), Modifier.weight(1f).testTag("custom-group-from-year"), input = digits)
                    TvUrlField(to, { to = it.filter(Char::isDigit).take(4) }, stringResource(R.string.custom_group_to_year), Modifier.weight(1f).testTag("custom-group-to-year"), input = digits)
                    TvUrlField(
                        rating, { rating = it.take(4) }, stringResource(R.string.custom_group_min_rating), Modifier.weight(1f).testTag("custom-group-min-rating"),
                        input = FieldInput(keyboard = KeyboardType.Decimal, compact = true),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvActionButton(
                        stringResource(R.string.action_save),
                        {
                            if (draft.isUsable) {
                                library.saveCustomGroup(draft)
                                onClosed(draft.id)
                            }
                        },
                        Modifier.focusRequester(save).testTag("custom-group-save"), icon = TvIcons.Check, state = SurfaceState(enabled = draft.isUsable), compact = true,
                    )
                    if (initial != null) {
                        TvActionButton(
                            stringResource(R.string.action_delete),
                            {
                                library.deleteCustomGroup(initial.id)
                                onClosed(null)
                            },
                            Modifier.testTag("custom-group-delete"), state = SurfaceState(danger = true), compact = true,
                        )
                    }
                    TvActionButton(stringResource(R.string.match_picker_close), { onClosed(initial?.id) }, Modifier.testTag("custom-group-close"), compact = true)
                }
            }
        }
        LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
    }
}
