package com.sohva.tv.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.player.AddonLanguages
import com.sohva.tv.core.model.player.SubtitleText
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import java.util.Locale

/** One row of the picker's right column: an embedded track or an addon candidate. */
internal class SubtitleOption(val id: String, val language: String?, val track: TrackItem?, val candidate: SubtitleCandidate?, val number: Int)

/**
 * The options the picker lists (FR-97): embedded tracks first, then the stream's own and each
 * provider's results; only the preferred languages unless [showAll]. Pure, so it is tested alone.
 */
internal object SubtitleOptions {
    fun of(tracks: Tracks, results: SubtitleResults, preferred: List<String>, showAll: Boolean): List<SubtitleOption> {
        val embedded = tracks.text.filter { !it.sideLoaded }.mapIndexed { i, t ->
            SubtitleOption("e${t.group}-${t.index}", AddonLanguages.normalise(t.language), t, null, i + 1)
        }
        val addons = results.candidates.sortedBy { if (it.provider == null) 0 else 1 }.map {
            SubtitleOption("a${it.key}", AddonLanguages.normalise(it.language), null, it, it.number)
        }
        return (embedded + addons).filter { showAll || it.language in preferred }
    }

    /** Languages with options: the preferred ones in their order, then the rest by [name]. */
    fun languages(options: List<SubtitleOption>, preferred: List<String>, name: (String?) -> String): List<String?> {
        val present = options.map { it.language }.distinct()
        return preferred.filter { it in present } + present.filter { it !in preferred }.sortedBy(name)
    }
}

/**
 * The addon subtitle picker (spec 50 FR-97, §5.6): full screen over the paused video. "Back to
 * player" has focus on open; choosing loads and applies, then closes.
 */
@Composable
internal fun AddonSubtitlePicker(model: PlayerModel, subs: AddonSubtitles) {
    val tracks by model.tracks.collectAsStateWithLifecycle()
    val results by subs.results.collectAsStateWithLifecycle()
    val pick by subs.pick.collectAsStateWithLifecycle()
    val loading by subs.loading.collectAsStateWithLifecycle()
    val message by subs.message.collectAsStateWithLifecycle()
    val showAll by subs.showAll.collectAsStateWithLifecycle()
    val sync by subs.sync.collectAsStateWithLifecycle()
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val unknown = stringResource(R.string.addon_ui_unknown_language)
    val name: (String?) -> String = { code -> code?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale).takeIf { n -> n.isNotBlank() } ?: it } ?: unknown }
    val prefs = model.settings.vodLanguages
    val preferred = remember(prefs) { listOfNotNull(prefs.subtitles, prefs.subtitlesSecond).mapNotNull(AddonLanguages::normalise).distinct() }
    val options = remember(tracks, results, preferred, showAll) { SubtitleOptions.of(tracks, results, preferred, showAll) }
    val languages = remember(options, preferred, locale) { SubtitleOptions.languages(options, preferred, name) }
    var active by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = active?.takeIf { it in languages } ?: languages.firstOrNull()
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        subs.request()
        back.requestFocusWhenAttached()
    }
    Column(
        Modifier.fillMaxSize().roundFill(Sohva.palette.scrim.copy(alpha = 0.88f), 0.dp).padding(28.dp).testTag("addon-subtitle-picker"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.player_quick_subtitles), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
                Text(stringResource(R.string.addon_ui_appearance_follows_sohvas_vod_subtitle_settings), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
            }
            TvActionButton(stringResource(R.string.addon_ui_back_to_player), model::closePicker, Modifier.focusRequester(back).testTag("addon-subtitles-back"), compact = true)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LazyColumn(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text(stringResource(R.string.addon_ui_language), Modifier.padding(bottom = 4.dp), style = Sohva.typography.label, color = Sohva.palette.textMuted)
                }
                item {
                    TvListRow(
                        stringResource(R.string.addon_ui_subtitles_off), { subs.off(); model.closePicker() }, Modifier.testTag("addon-subtitles-off"),
                        state = SurfaceState(selected = pick == SubtitlePick.Off || pick == null), layout = ListRowLayout(dense = true),
                    )
                }
                items(languages, key = { it ?: "" }) { lang ->
                    TvListRow(
                        name(lang), { active = lang }, Modifier.testTag("addon-subtitles-language-${lang ?: "none"}"),
                        trailing = options.count { it.language == lang }.toString(),
                        state = SurfaceState(selected = lang == shown), layout = ListRowLayout(dense = true),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerStatus(results, loading, message, options.isEmpty(), preferred.isEmpty() && !showAll)
                if (shown != null || languages.isNotEmpty()) {
                    Text(name(shown), style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(options.filter { it.language == shown }, key = { it.id }) { option ->
                        OptionCard(option, name(option.language), selected(option, pick)) {
                            val t = option.track
                            val c = option.candidate
                            if (t != null) {
                                subs.chooseEmbedded(t)
                                model.closePicker()
                            } else if (c != null) {
                                subs.choose(c) { model.closePicker() }
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvActionButton(
                stringResource(R.string.addon_ui_sync_value, SubtitleText.label(sync.appliedMs)), subs::openSync, Modifier.testTag("addon-subtitles-sync"),
                compact = true, state = SurfaceState(enabled = subs.timingAvailable),
            )
            TvActionButton(
                stringResource(if (showAll) R.string.addon_ui_primary_secondary_only else R.string.addon_ui_show_all_languages),
                { model.setShowAllLanguages(!showAll) }, Modifier.testTag("addon-subtitles-show-all"), compact = true,
            )
            TvActionButton(stringResource(R.string.action_refresh), { subs.request(refresh = true) }, Modifier.testTag("addon-subtitles-refresh"), compact = true)
            if (pick is SubtitlePick.Embedded) {
                Text(stringResource(R.string.addon_ui_timing_adjustment_is_unavailable_for_this_subtitle_format), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
            }
        }
    }
}

private fun selected(option: SubtitleOption, pick: SubtitlePick?): Boolean = when (pick) {
    is SubtitlePick.Addon -> option.candidate?.key == pick.candidate.key
    is SubtitlePick.Embedded -> option.track?.let { t -> if (pick.group != null) t.group == pick.group && t.index == pick.index else t.selected } == true
    else -> false
}

@Composable
private fun PickerStatus(results: SubtitleResults, loading: Boolean, message: String?, empty: Boolean, noPreferences: Boolean) {
    val lines = buildList {
        if (loading) add(stringResource(R.string.addon_ui_loading_selected_subtitle))
        if (!results.done) add(stringResource(if (results.candidates.isEmpty()) R.string.addon_ui_finding_subtitles else R.string.addon_ui_checking_subtitle_providers))
        addAll(results.errors)
        message?.let(::add)
        if (noPreferences) {
            add(stringResource(R.string.addon_ui_choose_subtitle_languages_in_sohva_settings_or_show_all_languages))
        } else if (empty && results.done) {
            add(stringResource(R.string.addon_ui_no_matching_subtitles_available))
        }
    }
    lines.forEach { Text(it, Modifier.testTag("addon-subtitles-status"), maxLines = 2, overflow = TextOverflow.Ellipsis, style = Sohva.typography.caption, color = Sohva.palette.textMuted) }
}

/** §5.6: provider in bold, "<Language> · <detail>", "✓ Selected" at the right. */
@Composable
private fun OptionCard(option: SubtitleOption, language: String, selected: Boolean, onClick: () -> Unit) {
    val provider = when {
        option.track != null -> stringResource(R.string.addon_ui_embedded)
        option.candidate?.provider == null -> stringResource(R.string.addon_ui_stream_subtitle)
        else -> option.candidate.provider
    }
    val detail = option.track?.label?.takeIf { it.isNotBlank() }
        ?: if (option.track != null) stringResource(R.string.addon_ui_track, option.number) else stringResource(R.string.addon_ui_option, option.number)
    val style = SurfaceStyle(corner = Sohva.shapes.medium, resting = Sohva.palette.surface, focusScale = 1f, padding = PaddingValues(14.dp))
    TvSurface(onClick, Modifier.fillMaxWidth().testTag("addon-subtitle-option-${option.id}"), style = style) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(provider, maxLines = 1, style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = colors.content)
                Text("$language · $detail", maxLines = 1, style = Sohva.typography.caption, color = colors.secondaryContent)
            }
            if (selected) Text(stringResource(R.string.addon_ui_selected), style = Sohva.typography.label, color = colors.content)
        }
    }
}

/** Keeps the picker composed under the sync panel so its state survives (FR-89); only one is drawn. */
@Composable
internal fun AddonSubtitleLayers(model: PlayerModel, subs: AddonSubtitles) {
    val syncOpen by subs.syncOpen.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        if (syncOpen) SubtitleSyncPanel(model, subs, Modifier.align(Alignment.TopCenter)) else AddonSubtitlePicker(model, subs)
    }
}
