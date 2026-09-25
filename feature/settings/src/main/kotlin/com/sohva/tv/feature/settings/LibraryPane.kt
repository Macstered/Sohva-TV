package com.sohva.tv.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.data.metadata.MetadataConfig
import com.sohva.tv.core.model.vod.PreferredCopy
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.PickerChoice
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.SettingsSwitch
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.SinglePickerDialog
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import java.util.Locale

private val KEY_FIELD = FieldInput(keyboard = KeyboardType.Password, transformation = PasswordVisualTransformation(), compact = true)

/**
 * Settings › Library (spec 41 §5.1): metadata and images with the TMDB mark, the metadata language
 * and the preferred copy, and Maintenance. The TMDB switch takes focus when the section opens.
 * "Manage groups & content" opens the library manager; groups of your own and the image cache join
 * with their milestones.
 */
@Composable
internal fun LibraryPane(library: LibrarySettings, start: FocusRequester) {
    val state by library.state.collectAsStateWithLifecycle()
    val stored = state.stored ?: return
    val clearCache = remember { FocusRequester() }
    // Back from the manager: the section's start is its row, so Settings puts focus there.
    val fromManager = remember { library.takeManagerReturn() }
    val unused = remember { FocusRequester() }
    MetadataGroup(library, state, stored, if (fromManager) unused else start)
    ChoicesGroup(library, state, stored, managerRow = if (fromManager) start else unused)
    CustomGroupsSection(library, below = clearCache)
    SettingsGroup {
        SettingsOverline(stringResource(R.string.maintenance_title))
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val enabled = SurfaceState(enabled = !state.busy, keepsFocus = true)
            TvActionButton(
                stringResource(R.string.metadata_clear_cache),
                library::clearCache,
                Modifier.focusRequester(clearCache).testTag("settings-metadata-clear-cache"),
                state = enabled,
                compact = true,
            )
            TvActionButton("TMDB", { library.openWeb("https://www.themoviedb.org") }, Modifier.testTag("settings-metadata-open-tmdb"), compact = true)
            TvActionButton("TVmaze", { library.openWeb("https://www.tvmaze.com") }, Modifier.testTag("settings-metadata-open-tvmaze"), compact = true)
        }
    }
}

/** The switches, the key with Save and Test, and the status line (META-FR-02…08). */
@Composable
private fun MetadataGroup(library: LibrarySettings, state: LibrarySettingsState, stored: MetadataSettingsView, start: FocusRequester) {
    val enabled = SurfaceState(enabled = !state.busy, keepsFocus = true)
    SettingsGroup {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SettingsOverline(stringResource(R.string.metadata_title), Modifier.weight(1f))
            Image(painterResource(R.drawable.tmdb_attribution), "TMDB", Modifier.padding(end = 14.dp).size(137.dp, 18.dp))
        }
        SettingsRow(stringResource(R.string.metadata_tmdb_switch), icon = TvIcons.Star, subtitle = stringResource(R.string.metadata_description)) {
            SettingsSwitch(stored.tmdbSwitch, library::toggleTmdb, Modifier.focusRequester(start).testTag("settings-metadata-tmdb-enabled"), enabled = !state.busy, keepsFocus = true)
        }
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TvUrlField(
                state.typed,
                library::type,
                stringResource(R.string.metadata_tmdb_token),
                Modifier.weight(1f).testTag("settings-metadata-tmdb-token"),
                icon = TvIcons.Key,
                input = KEY_FIELD,
                state = enabled,
            )
            TvActionButton(stringResource(R.string.metadata_save_key), library::saveKey, Modifier.testTag("settings-metadata-save"), icon = TvIcons.Save, state = enabled, compact = true)
            TvActionButton(
                stringResource(R.string.metadata_test_tmdb),
                library::testTmdb,
                Modifier.testTag("settings-metadata-test-tmdb"),
                state = SurfaceState(enabled = !state.busy && state.typed.isNotBlank(), keepsFocus = true),
                compact = true,
            )
        }
        SettingsRow(stringResource(R.string.metadata_tvmaze_switch), icon = TvIcons.Guide) {
            SettingsSwitch(stored.tvmaze, library::toggleTvmaze, Modifier.testTag("settings-metadata-tvmaze-enabled"), enabled = !state.busy, keepsFocus = true)
        }
        // A refused key is the standing status until a key is saved (spec 41 Q8); errors in `danger` (META-FR-08).
        val refused = stored.keyRefused && state.status == null
        val status = if (refused) stringResource(R.string.metadata_key_refused) else state.status?.resolve()
        if (status != null) {
            Text(
                status,
                Modifier.padding(horizontal = 14.dp).testTag("settings-metadata-status"),
                style = Sohva.typography.label.copy(fontSize = 12.sp),
                color = if (refused || state.statusIsError) Sohva.palette.danger else Sohva.palette.focus,
            )
        }
    }
}

/** The metadata language (META-FR-09) and the preferred copy (spec 40 VOD-FR-32), each with its picker. */
@Composable
private fun ChoicesGroup(library: LibrarySettings, state: LibrarySettingsState, stored: MetadataSettingsView, managerRow: FocusRequester) {
    var picking by remember { mutableStateOf<Picking?>(null) }
    // Set only when a picker closes, so focus returns to its row and never lands there unasked.
    var returnTo by remember { mutableStateOf<Picking?>(null) }
    val languageRow = remember { FocusRequester() }
    val copyRow = remember { FocusRequester() }
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    SettingsGroup {
        SettingsValueRow(
            title = stringResource(R.string.metadata_language_title),
            value = languageName(stored.language, locale),
            onClick = { picking = Picking.LANGUAGE },
            modifier = Modifier.focusRequester(languageRow).testTag("settings-metadata-language"),
            icon = TvIcons.Info,
            subtitle = stringResource(R.string.metadata_language_help),
        )
        SettingsValueRow(
            title = stringResource(R.string.preferred_copy_title),
            value = stringResource(copyLabel(state.preferredCopy)),
            onClick = { picking = Picking.COPY },
            modifier = Modifier.focusRequester(copyRow).testTag("settings-preferred-copy"),
            icon = TvIcons.Channels,
            subtitle = stringResource(R.string.preferred_copy_help),
            divider = true,
        )
        SettingsValueRow(
            title = stringResource(R.string.manager_title),
            value = "",
            onClick = library::openManager,
            modifier = Modifier.focusRequester(managerRow).testTag("settings-manage-groups"),
            icon = TvIcons.Settings,
            subtitle = stringResource(R.string.manager_row_help),
        )
    }
    val close = { which: Picking ->
        picking = null
        returnTo = which
    }
    when (picking) {
        Picking.LANGUAGE -> SinglePickerDialog(
            title = stringResource(R.string.metadata_language_title),
            choices = MetadataConfig.LANGUAGES.map { PickerChoice(it, languageName(it, locale), tag = "settings-metadata-language-$it") },
            current = stored.language,
            onChoose = {
                // Choosing the current language only closes the picker (META-FR-09).
                if (it != stored.language) library.setLanguage(it)
                close(Picking.LANGUAGE)
            },
            onDismiss = { close(Picking.LANGUAGE) },
        )
        Picking.COPY -> SinglePickerDialog(
            title = stringResource(R.string.preferred_copy_title),
            choices = PreferredCopy.entries.map { PickerChoice(it, stringResource(copyLabel(it)), tag = "settings-preferred-copy-${it.name.lowercase(Locale.ROOT)}") },
            current = state.preferredCopy,
            onChoose = {
                library.setPreferredCopy(it)
                close(Picking.COPY)
            },
            onDismiss = { close(Picking.COPY) },
        )
        null -> Unit
    }
    LaunchedEffect(returnTo) {
        when (returnTo) {
            Picking.LANGUAGE -> languageRow.requestFocusWhenAttached()
            Picking.COPY -> copyRow.requestFocusWhenAttached()
            null -> return@LaunchedEffect
        }
        returnTo = null
    }
}

private enum class Picking { LANGUAGE, COPY }

/** The platform's name of [tag] in the interface language, first letter capitalised ("English (United States)"). */
private fun languageName(tag: String, locale: Locale): String =
    Locale.forLanguageTag(tag).getDisplayName(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

private fun copyLabel(copy: PreferredCopy): Int = when (copy) {
    PreferredCopy.NONE -> R.string.preferred_copy_none
    PreferredCopy.FINNISH_AUDIO -> R.string.preferred_copy_finnish_audio
    PreferredCopy.FINNISH_SUBTITLES -> R.string.preferred_copy_finnish_subtitles
    PreferredCopy.LARGEST_PICTURE -> R.string.preferred_copy_largest_picture
}
