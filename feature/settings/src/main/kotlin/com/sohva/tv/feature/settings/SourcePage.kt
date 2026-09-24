package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.SettingsSwitch
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

private val TEXT_FIELD = FieldInput(keyboard = KeyboardType.Text, compact = true)
private val URL_FIELD = FieldInput(keyboard = KeyboardType.Uri, compact = true)
private val PASSWORD_FIELD = FieldInput(keyboard = KeyboardType.Password, transformation = PasswordVisualTransformation(), compact = true)

/**
 * A source's page (spec 10 §4.3), items directly in the pane in the spec's order. "All playlists"
 * is the first control (SRC-FR-13); every field opens its editor on OK (SRC-FR-18).
 */
@Composable
internal fun SourcePage(state: SettingsState, model: SettingsModel, start: FocusRequester) {
    val page = state.page ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        val overline = if (page.type == SourceType.M3U) R.string.source_type_m3u_overline else R.string.source_type_xtream_overline
        SettingsOverline(stringResource(overline, page.name), Modifier.weight(1f))
        TvActionButton(
            stringResource(R.string.source_back_to_list),
            model::closePage,
            Modifier.focusRequester(start).testTag("source-page-back"),
            icon = TvIcons.Back,
            compact = true,
        )
    }
    if (page.type == SourceType.M3U) M3uAddresses(page, model) else XtreamAccountFields(page, model)
    SourceRows(page, state, model)
    SourceActions(page, state.busy, model)
    val health = healthSummary(state.health.filter { it.sourceId == page.id })
    StatusGroup(listOfNotNull(state.messages[SettingsSection.SOURCES]?.resolve(), health), securityNote = true)
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 14.dp, top = 6.dp),
        style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        color = Sohva.palette.textPrimary,
        maxLines = 1,
    )
}

/** The dialog title of a field: its label without beta 23's leading glyph. */
private fun String.withoutGlyph(): String = dropWhile { !it.isLetter() }

@Composable
private fun M3uAddresses(page: SourceDraft, model: SettingsModel) {
    val m3uLabel = stringResource(R.string.source_m3u_address)
    FieldLabel(m3uLabel)
    TvUrlField(
        page.m3uUrl,
        { value -> model.edit { it.copy(m3uUrl = value) } },
        m3uLabel.withoutGlyph(),
        Modifier.padding(horizontal = 14.dp).fillMaxWidth(0.82f).testTag("settings-m3u"),
        icon = TvIcons.Link,
        input = URL_FIELD,
        hint = "http(s)://provider/playlist.m3u",
    )
    if (page.showsGuideAddress) {
        val guideLabel = stringResource(R.string.source_xmltv_address)
        FieldLabel(guideLabel)
        TvUrlField(
            page.xmlTvUrl,
            { value -> model.edit { it.copy(xmlTvUrl = value) } },
            guideLabel.withoutGlyph(),
            Modifier.padding(horizontal = 14.dp).fillMaxWidth(0.82f).testTag("settings-xmltv"),
            icon = TvIcons.Link,
            input = URL_FIELD,
            hint = "http(s)://provider/epg.xml",
        )
    }
}

@Composable
private fun XtreamAccountFields(page: SourceDraft, model: SettingsModel) {
    val serverLabel = stringResource(R.string.source_xtream_server)
    FieldLabel(serverLabel)
    TvUrlField(
        page.server,
        { value -> model.edit { it.copy(server = value) } },
        serverLabel.withoutGlyph(),
        Modifier.padding(horizontal = 14.dp).fillMaxWidth(0.82f).testTag("settings-xtream-base-url"),
        icon = TvIcons.Link,
        input = URL_FIELD,
        hint = "http(s)://provider:port",
    )
    Row(Modifier.padding(horizontal = 14.dp).fillMaxWidth(0.82f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val user = stringResource(R.string.source_username)
        TvUrlField(page.username, { value -> model.edit { it.copy(username = value) } }, user, Modifier.weight(1f).testTag("settings-xtream-username"), TvIcons.Key, TEXT_FIELD)
        val password = stringResource(R.string.source_password)
        TvUrlField(page.password, { value -> model.edit { it.copy(password = value) } }, password, Modifier.weight(1f).testTag("settings-xtream-password"), TvIcons.Key, PASSWORD_FIELD)
    }
}

@Composable
private fun SourceRows(page: SourceDraft, state: SettingsState, model: SettingsModel) {
    SettingsRow(stringResource(R.string.source_name), subtitle = stringResource(R.string.source_name_help)) {
        TvUrlField(
            page.name,
            // The field takes at most 100 characters (SRC-FR-20 item 2 rebuild rule).
            { value -> model.edit { it.copy(name = value.take(SourceRules.MAX_NAME_LENGTH)) } },
            stringResource(R.string.source_name),
            Modifier.width(320.dp).testTag("settings-source-name"),
            input = TEXT_FIELD,
        )
    }
    SettingsRow(stringResource(R.string.source_enabled_title), subtitle = stringResource(R.string.source_enabled_help)) {
        SettingsSwitch(page.enabled, { model.edit { it.copy(enabled = !it.enabled) } }, Modifier.testTag("settings-source-enabled"))
    }
    SettingsRow(stringResource(R.string.source_connection_limit_title), subtitle = stringResource(R.string.source_connection_limit_help)) {
        val limits = SourceRules.CONNECTION_LIMITS
        TvActionButton(
            "−",
            { model.edit { it.copy(connectionLimit = (it.connectionLimit - 1).coerceIn(limits)) } },
            Modifier.testTag("settings-limit-down"),
            state = SurfaceState(enabled = page.connectionLimit > limits.first, keepsFocus = true),
            compact = true,
        )
        Text(
            stringResource(R.string.source_connection_limit, page.connectionLimit),
            Modifier.padding(horizontal = 10.dp),
            style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold),
            color = Sohva.palette.textPrimary,
        )
        TvActionButton(
            "+",
            { model.edit { it.copy(connectionLimit = (it.connectionLimit + 1).coerceIn(limits)) } },
            Modifier.testTag("settings-limit-up"),
            state = SurfaceState(enabled = page.connectionLimit < limits.last, keepsFocus = true),
            compact = true,
        )
    }
    if (!page.isNew) DeleteRow(page, state, model)
    ScopeRow(page, model)
    if (page.scope.includesLive) EpgOffsetRow(page, model)
}
