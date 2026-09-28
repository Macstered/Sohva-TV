package com.sohva.tv.feature.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.QrCodeImage
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsSwitchRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Settings › Home › Trakt rows, public lists (spec 02 HOME-FR-99, -100): the lists added so far,
 * each with its switch (off removes it) and "Only titles in my library", then "Add a Trakt list"
 * and "From your phone". Focus returns to the button a dialog was opened from.
 */
@Composable
internal fun ColumnScope.ListsSection(home: HomeLayoutSettings, view: HomeLayoutView, s: HomeLayoutUi) {
    if (!view.lists) return
    val add = remember { FocusRequester() }
    val phone = remember { FocusRequester() }
    SettingsOverline(stringResource(R.string.home_layout_lists))
    val lists = view.layout.added.filter { HomeLayout.traktListId(it) != null }
    lists.forEach { id ->
        val name = view.names[id] ?: stringResource(R.string.trakt_row_list)
        SettingsSwitchRow(name, true, { home.toggleAdded(id) }, Modifier.testTag("settings-home-list-$id"))
        SettingsSwitchRow(
            stringResource(R.string.home_layout_library_only), view.layout.isLibraryOnly(id), { home.toggleLibraryOnly(id) },
            Modifier.testTag("settings-home-library-$id"),
            subtitle = name,
        )
    }
    val full = view.layout.added.size >= HomeLayout.MAX_ADDED
    // A list added from here pushes the buttons down: they stay in view while one has focus (AGENTS.md §5 rule 2).
    val buttons = remember { BringIntoViewRequester() }
    var buttonFocused by remember { mutableStateOf(false) }
    LaunchedEffect(lists.size) {
        if (!buttonFocused) return@LaunchedEffect
        withFrameNanos { }
        buttons.bringIntoView()
    }
    // Coming down the list, "Add a Trakt list" takes focus, not whichever button is nearest the middle.
    Row(
        Modifier
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .bringIntoViewRequester(buttons)
            .onFocusChanged { buttonFocused = it.hasFocus }
            .focusProperties { onEnter = { add.requestFocus() } }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvActionButton(
            stringResource(R.string.home_layout_add_list), home::openListDialog, Modifier.focusRequester(add).testTag("settings-home-add-list"),
            TvIcons.Search, compact = true,
        )
        TvActionButton(
            stringResource(R.string.home_layout_list_phone), home::openPhone, Modifier.focusRequester(phone).testTag("settings-home-list-phone"),
            TvIcons.Link, compact = true,
        )
    }
    if (full) Text(stringResource(R.string.home_layout_trakt_full), Modifier.padding(horizontal = 14.dp), style = Sohva.typography.label, color = Sohva.palette.textDim)
    // Focus goes back to the button a dialog came from (lessons 4.1). A request made while the dialog
    // still holds the window is lost when it goes (seen on the emulator), so it is made again once the
    // dialog has gone, as TvUrlField does after its edit dialog.
    var returnTo by remember { mutableStateOf<FocusRequester?>(null) }
    s.listDialog?.let { dialog ->
        TraktListDialog(
            home, dialog, full,
            choose = { list ->
                returnTo = add
                home.chooseList(list)
            },
        ) {
            returnTo = add
            home.closeListDialog()
        }
    }
    if (s.phoneOpen) {
        ListPhoneDialog(home) {
            returnTo = phone
            home.closePhone()
        }
    }
    val open = s.listDialog != null || s.phoneOpen
    LaunchedEffect(open, returnTo) {
        val target = returnTo ?: return@LaunchedEffect
        if (open) return@LaunchedEffect
        target.requestFocusWhenAttached()
        returnTo = null
    }
}

/** "Add a Trakt list": a field for an address, a number or a name, Search, and what came back. */
@Composable
private fun TraktListDialog(home: HomeLayoutSettings, dialog: ListDialogUi, full: Boolean, choose: (ListChoice) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val search = remember { FocusRequester() }
        Column(
            Modifier
                .fillMaxWidth(0.66f)
                .fillMaxHeight(0.82f)
                .roundFill(Sohva.palette.panel, Sohva.shapes.large)
                .padding(24.dp)
                .focusGroup()
                .testTag("settings-list-dialog"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.home_layout_add_list), style = Sohva.typography.title.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
            Text(stringResource(R.string.home_layout_list_help), style = Sohva.typography.label, color = Sohva.palette.textDim)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TvUrlField(
                    dialog.query, home::setListQuery, stringResource(R.string.home_layout_list_query),
                    Modifier.weight(1f).testTag("settings-list-query"),
                    icon = TvIcons.Search, input = FieldInput(keyboard = KeyboardType.Uri, compact = true),
                )
                TvActionButton(
                    stringResource(R.string.match_picker_search), home::searchLists, Modifier.focusRequester(search).testTag("settings-list-search"),
                    icon = TvIcons.Search, compact = true,
                )
            }
            ListResults(dialog, full, choose, Modifier.weight(1f))
            TvActionButton(stringResource(R.string.match_picker_close), onClose, Modifier.testTag("settings-list-close"), icon = TvIcons.Close, compact = true)
        }
        LaunchedEffect(Unit) { search.requestFocusWhenAttached() }
    }
}

@Composable
private fun ListResults(dialog: ListDialogUi, full: Boolean, choose: (ListChoice) -> Unit, modifier: Modifier) {
    val found = (dialog.finding as? ListFinding.Found)?.lists
    if (found == null) {
        val note = when {
            dialog.searching -> R.string.match_picker_searching
            dialog.finding == ListFinding.Nothing -> R.string.home_layout_list_none
            dialog.finding == ListFinding.Private -> R.string.home_layout_list_private
            dialog.finding == ListFinding.Failed -> R.string.home_layout_list_failed
            else -> null
        }
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            note?.let { Text(stringResource(it), Modifier.testTag("settings-list-note"), style = Sohva.typography.body, color = Sohva.palette.textDim) }
        }
        return
    }
    LazyColumn(modifier.fillMaxWidth().testTag("settings-list-results"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(found, key = { it.id }) { list ->
            TvListRow(
                list.name, { if (!full) choose(list) }, Modifier.testTag("settings-list-result-${list.id}"),
                supporting = stringResource(R.string.home_layout_list_meta, list.owner ?: "Trakt", list.items, list.likes),
            )
        }
    }
}

/** The phone page for Trakt lists (HOME-FR-100): the QR on white, the address, the lists added so far. */
@Composable
private fun ListPhoneDialog(home: HomeLayoutSettings, onClose: () -> Unit) {
    val phone by home.listPhone.collectAsStateWithLifecycle(ListPhone.Closed)
    val close = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(720.dp).testTag("settings-list-phone"), border = Sohva.palette.outline, padding = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.home_layout_list_phone), style = Sohva.typography.title.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
                    when (val p = phone) {
                        is ListPhone.Open -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            p.qr?.let { QrCodeImage(it, stringResource(R.string.phone_setup_qr_description), Modifier.size(200.dp)) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.home_layout_list_phone_help), style = Sohva.typography.label, color = Sohva.palette.textMuted)
                                Text(p.url, Modifier.testTag("settings-list-phone-url"), style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
                                p.added.lastOrNull()?.let {
                                    Text(stringResource(R.string.home_layout_list_added, it), Modifier.testTag("settings-list-phone-added"), style = Sohva.typography.body, color = Sohva.palette.textPrimary)
                                }
                            }
                        }
                        ListPhone.NoNetwork -> Text(stringResource(R.string.phone_setup_no_network), style = Sohva.typography.body, color = Sohva.palette.danger)
                        ListPhone.Closed -> Unit
                    }
                    TvActionButton(stringResource(R.string.phone_setup_stop), onClose, Modifier.focusRequester(close).testTag("settings-list-phone-close"), icon = TvIcons.Close)
                }
            }
        }
        LaunchedEffect(Unit) { close.requestFocusWhenAttached() }
    }
}
