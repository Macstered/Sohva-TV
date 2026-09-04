package com.streammate.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.iptv.R

/**
 * Naming a corner of the library.
 *
 * A group is a handful of genres under a name, narrowed by year or rating where
 * that is what makes it the thing it is - "the children's films", "eighties
 * action". The vocabulary TMDB supplies is broad and impersonal; this is where
 * a household writes its own words over it.
 *
 * Only genres actually present in the library are offered. A picker listing
 * things that would return nothing is a picker that wastes your time.
 */
@Composable
internal fun CustomGroupEditor(
    group: CatalogueCustomGroup?,
    availableGenres: List<CatalogueGenre>,
    onSave: (CatalogueCustomGroup) -> Unit,
    onDelete: ((String) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    var name by remember { mutableStateOf(group?.name.orEmpty()) }
    var genres by remember { mutableStateOf(group?.genres.orEmpty()) }
    var fromYear by remember { mutableStateOf(group?.fromYear?.toString().orEmpty()) }
    var toYear by remember { mutableStateOf(group?.toYear?.toString().orEmpty()) }
    var minRating by remember { mutableStateOf(group?.minRating?.toString().orEmpty()) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocusWhenAttached() }

    val edited = CatalogueCustomGroup(
        id = group?.id ?: "group-" + name.trim().lowercase().replace(NON_ID, "-"),
        name = name,
        genres = genres,
        fromYear = fromYear.trim().toIntOrNull(),
        toYear = toYear.trim().toIntOrNull(),
        minRating = minRating.trim().replace(',', '.').toDoubleOrNull(),
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.66f)
                .fillMaxHeight(0.88f)
                .clip(StreamMateThemeTokens.shapes.large)
                .background(palette.panel)
                .padding(24.dp)
                .testTag("custom-group-editor"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.custom_group_title),
                color = palette.textPrimary,
                fontSize = typography.title.fontSize,
                lineHeight = typography.title.lineHeight,
                fontWeight = FontWeight.Bold,
            )
            TvUrlField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                label = stringResource(R.string.custom_group_name),
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                testTag = "custom-group-name",
                keyboardType = KeyboardType.Text,
                editOnClickOnly = true,
                compact = true,
            )
            SettingsOverline(stringResource(R.string.custom_group_genres))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("custom-group-genres"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(availableGenres, key = CatalogueGenre::wireValue) { genre ->
                    val chosen = genre in genres
                    SettingsRow(
                        title = settingsGenreLabel(genre),
                        icon = if (chosen) TvIcons.Check else null,
                        trailing = {
                            SettingsSwitch(
                                checked = chosen,
                                onCheckedChange = {
                                    genres = if (chosen) genres - genre else genres + genre
                                },
                                testTag = "custom-group-genre-" + genre.wireValue,
                            )
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                YearOrRatingField(
                    value = fromYear,
                    onValueChange = { fromYear = it.filter(Char::isDigit).take(4) },
                    label = stringResource(R.string.custom_group_from_year),
                    testTag = "custom-group-from-year",
                    modifier = Modifier.weight(1f),
                )
                YearOrRatingField(
                    value = toYear,
                    onValueChange = { toYear = it.filter(Char::isDigit).take(4) },
                    label = stringResource(R.string.custom_group_to_year),
                    testTag = "custom-group-to-year",
                    modifier = Modifier.weight(1f),
                )
                YearOrRatingField(
                    value = minRating,
                    onValueChange = { minRating = it.take(4) },
                    label = stringResource(R.string.custom_group_min_rating),
                    testTag = "custom-group-min-rating",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionButton(
                    label = stringResource(R.string.action_save),
                    icon = TvIcons.Check,
                    // A name over nothing would collect the whole library, so
                    // there is nothing to save until it says something.
                    enabled = edited.isUsable,
                    onClick = { onSave(edited); onDismiss() },
                    compact = true,
                    testTag = "custom-group-save",
                )
                onDelete?.let { delete ->
                    TvActionButton(
                        label = stringResource(R.string.action_delete),
                        icon = TvIcons.Delete,
                        danger = true,
                        onClick = { group?.id?.let(delete); onDismiss() },
                        compact = true,
                        testTag = "custom-group-delete",
                    )
                }
                TvActionButton(
                    label = stringResource(R.string.match_picker_close),
                    icon = TvIcons.Close,
                    onClick = onDismiss,
                    compact = true,
                    testTag = "custom-group-close",
                )
            }
        }
    }
}

@Composable
private fun YearOrRatingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    TvUrlField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        testTag = testTag,
        keyboardType = KeyboardType.Number,
        editOnClickOnly = true,
        compact = true,
    )
}

/** One line describing a group, for the row that opens it. */
@Composable
internal fun customGroupSummary(group: CatalogueCustomGroup): String {
    val genreNames = group.genres.sorted().map { settingsGenreLabel(it) }
    val ratingText = group.minRating?.let {
        stringResource(R.string.custom_group_rating_at_least, it)
    }
    val years = when {
        group.fromYear != null && group.toYear != null -> "${group.fromYear}-${group.toYear}"
        group.fromYear != null -> "${group.fromYear}-"
        group.toYear != null -> "-${group.toYear}"
        else -> null
    }
    return listOfNotNull(
        genreNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
        years,
        ratingText,
    ).joinToString(" · ")
}

@Composable
internal fun settingsGenreLabel(genre: CatalogueGenre): String = stringResource(
    when (genre) {
        CatalogueGenre.ACTION -> R.string.genre_action
        CatalogueGenre.ADVENTURE -> R.string.genre_adventure
        CatalogueGenre.ANIMATION -> R.string.genre_animation
        CatalogueGenre.COMEDY -> R.string.genre_comedy
        CatalogueGenre.CRIME -> R.string.genre_crime
        CatalogueGenre.DOCUMENTARY -> R.string.genre_documentary
        CatalogueGenre.DRAMA -> R.string.genre_drama
        CatalogueGenre.FAMILY -> R.string.genre_family
        CatalogueGenre.FANTASY -> R.string.genre_fantasy
        CatalogueGenre.HISTORY -> R.string.genre_history
        CatalogueGenre.HORROR -> R.string.genre_horror
        CatalogueGenre.MUSIC -> R.string.genre_music
        CatalogueGenre.MYSTERY -> R.string.genre_mystery
        CatalogueGenre.NEWS -> R.string.genre_news
        CatalogueGenre.REALITY -> R.string.genre_reality
        CatalogueGenre.ROMANCE -> R.string.genre_romance
        CatalogueGenre.SCIENCE_FICTION -> R.string.genre_science_fiction
        CatalogueGenre.SOAP -> R.string.genre_soap
        CatalogueGenre.TALK -> R.string.genre_talk
        CatalogueGenre.THRILLER -> R.string.genre_thriller
        CatalogueGenre.WAR -> R.string.genre_war
        CatalogueGenre.WESTERN -> R.string.genre_western
    },
)

private val NON_ID = Regex("[^a-z0-9]+")
private const val MAX_NAME = 40
