package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private class SubtitleOption(val key: String, val language: String, val provider: String, val detail: String,
    val embedded: AddonEmbeddedSubtitle? = null, val subtitle: AddonSubtitle? = null, val result: AddonSourceResult<AddonSubtitle>? = null)

@Composable
internal fun AddonSubtitlePicker(host: AddonHost, playback: AddonPlayback, onBack: () -> Unit, onTiming: () -> Unit, allowTiming: Boolean) {
    val labels = addonStrings()
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val preferences by host.preferences.collectAsStateWithLifecycle(initialValue = null)
    val showAll by host.showAllSubtitleLanguages.collectAsStateWithLifecycle()
    val preferred = AddonSubtitlePolicy.preferred(preferences?.preferredSubtitleLanguage, preferences?.secondarySubtitleLanguage)
    var failure by remember { mutableStateOf<AddonFailure?>(null) }
    var busy by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocusWhenAttached() }
    LaunchedEffect(Unit) { playback.loadSubtitleResults() }
    val options = remember(labels, playback.embeddedSubtitleTracks, playback.selection.stream.subtitles, playback.subtitleResults) {
        buildList {
            playback.embeddedSubtitleTracks.forEachIndexed { index, track ->
                add(SubtitleOption(track.key, track.language, labels(R.string.addon_ui_embedded), track.label ?: labels(R.string.addon_ui_track, index + 1), embedded = track))
            }
            playback.selection.stream.subtitles.forEachIndexed { index, subtitle ->
                add(SubtitleOption(addonSubtitleChoiceKey(subtitle, null), AddonSubtitlePolicy.language(subtitle.language) ?: "und",
                    labels(R.string.addon_ui_stream_subtitle), labels(R.string.addon_ui_option, index + 1), subtitle = subtitle))
            }
            playback.subtitleResults.forEach { result -> result.items.forEachIndexed { index, subtitle ->
                add(SubtitleOption(addonSubtitleChoiceKey(subtitle, result.installationId), AddonSubtitlePolicy.language(subtitle.language) ?: "und",
                    result.providerName, labels(R.string.addon_ui_option, index + 1), subtitle = subtitle, result = result))
            } }
        }.distinctBy { it.key }
    }
    val visible = options.filter { showAll || it.language in preferred }
    val languages = visible.map { it.language }.distinct().sortedWith(compareBy<String> {
        preferred.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
    }.thenBy { labels.languageName(it) })
    val activeLanguage = language?.takeIf { it in languages }
        ?: options.firstOrNull { it.key == playback.selectedSubtitleChoice }?.language?.takeIf { it in languages }
        ?: languages.firstOrNull()
    fun choose(option: SubtitleOption?) {
        if (busy) return
        busy = true; failure = null
        scope.launch {
            try {
                if (option?.embedded != null) playback.selectEmbeddedSubtitle(option.embedded)
                else playback.setSubtitle(option?.subtitle, providerId = option?.result?.installationId) { playback.validateSubtitleProvider(option?.result) }
                onBack()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AddonException) { failure = error.failure }
            finally { busy = false }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .88f))) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(labels(com.streammate.tv.iptv.R.string.player_quick_subtitles), color = palette.textPrimary, fontSize = typography.headline.fontSize, fontWeight = FontWeight.Bold)
                    Text(labels(R.string.addon_ui_appearance_follows_sohvas_vod_subtitle_settings), color = palette.textMuted, fontSize = typography.caption.fontSize)
                }
                TvActionButton(labels(R.string.addon_ui_back_to_player), onBack, compact = true, focusRequester = backFocus)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(labels(com.streammate.tv.iptv.R.string.metadata_language_label), color = palette.textMuted, fontSize = typography.label.fontSize)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(3.dp)) {
                        item { TvListRow(labels(R.string.addon_ui_subtitles_off), { choose(null) }, selected = playback.selectedSubtitle == null, dense = true,
                            enabled = !busy, testTag = "addon-subtitles-off") }
                        items(languages, key = { it }) { code ->
                            TvListRow(labels.languageName(code), { language = code }, selected = code == activeLanguage,
                                trailing = visible.count { it.language == code }.toString(), dense = true, testTag = "addon-subtitle-language-$code")
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(activeLanguage?.let { labels.languageName(it) } ?: labels(R.string.addon_ui_available_subtitles), color = palette.textMuted, fontSize = typography.label.fontSize)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("addon-player-subtitle-list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                        items(visible.filter { it.language == activeLanguage }, key = { it.key }) { option ->
                            val selected = playback.selectedSubtitle != null && playback.selectedSubtitleChoice == option.key
                            TvSurface({ choose(option) }, Modifier.fillMaxWidth(), selected = selected, enabled = !busy,
                                focusScale = 1f, testTag = if (option.embedded == null) "addon-select-subtitle" else "addon-select-embedded-subtitle",
                                contentPadding = PaddingValues(14.dp)) { colors ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(option.provider, color = colors.content, fontSize = typography.body.fontSize,
                                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${labels.languageName(option.language)} · ${option.detail}", color = colors.secondaryContent,
                                            fontSize = typography.caption.fontSize, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (selected) Text(labels(R.string.addon_ui_selected), color = colors.content, fontSize = typography.label.fontSize)
                                }
                            }
                        }
                        if (visible.isEmpty()) item { Text(if (playback.subtitleResultsLoading) labels(R.string.addon_ui_finding_subtitles) else if (preferred.isEmpty() && !showAll)
                            labels(R.string.addon_ui_choose_subtitle_languages_in_sohva_settings_or_show_all_languages) else labels(R.string.addon_ui_no_matching_subtitles_available),
                            color = palette.textMuted, fontSize = typography.body.fontSize) }
                        if (playback.subtitleResultsLoading) item { Text(labels(R.string.addon_ui_checking_subtitle_providers), color = palette.textMuted, fontSize = typography.caption.fontSize) }
                        playback.subtitleResults.filter { it.failure != null }.forEach { result -> item(key = "error-${result.installationId}") {
                            Text("${result.providerName}: ${stringResource(checkNotNull(result.failure).messageResource())}",
                                color = palette.textMuted, fontSize = typography.caption.fontSize, maxLines = 2)
                        } }
                    }
                }
            }
            if (busy) Text(labels(R.string.addon_ui_loading_selected_subtitle), color = palette.textMuted, fontSize = typography.caption.fontSize)
            (failure ?: playback.subtitleResultsFailure)?.let { Text(stringResource(it.messageResource()), color = palette.textPrimary) }
            if (playback.selectedSubtitle != null && !playback.subtitleTimingSupported && playback.ready) Text(
                labels(R.string.addon_ui_timing_adjustment_is_unavailable_for_this_subtitle_format), color = palette.textMuted, fontSize = typography.caption.fontSize)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(labels(R.string.addon_ui_sync_value, AddonSubtitleTiming.label(playback.subtitleDelayMillis)), onTiming,
                    enabled = allowTiming && !busy && playback.subtitleTimingSupported, compact = true, testTag = "addon-subtitle-sync")
                TvActionButton(if (showAll) labels(R.string.addon_ui_primary_secondary_only) else labels(R.string.addon_ui_show_all_languages), {
                    scope.launch { try { host.setShowAllSubtitleLanguages(!showAll) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failure = AddonFailure.STORAGE } }
                }, enabled = !busy, compact = true, testTag = "addon-player-all-languages")
                TvActionButton(labels(com.streammate.tv.iptv.R.string.action_refresh), { scope.launch { playback.loadSubtitleResults(refresh = true) } },
                    enabled = !busy && !playback.subtitleResultsLoading, compact = true, testTag = "addon-subtitles-refresh")
            }
        }
    }
}
