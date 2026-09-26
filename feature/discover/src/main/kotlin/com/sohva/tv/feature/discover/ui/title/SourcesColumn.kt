package com.sohva.tv.feature.discover.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sohva.tv.feature.discover.data.ProviderState
import com.sohva.tv.feature.discover.protocol.StreamKind
import com.sohva.tv.feature.discover.ui.components.ChooserDialog
import com.sohva.tv.feature.discover.ui.failureText
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.PickerRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The sources column (spec 50 §4.11, §5.4): "Sources" with Refresh, the scraper chooser, then per
 * provider in priority order its name and state and its source cards. Only HTTP sources play.
 */
@Composable
internal fun SourcesColumn(model: TitleModel, s: TitleState, first: FocusRequester, modifier: Modifier) {
    var choosing by remember { mutableStateOf(false) }
    val all = stringResource(R.string.catalogue_all)
    Column(modifier.fillMaxHeight().testTag("discover-sources"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_overline_sources), Modifier.weight(1f), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
            TvActionButton(stringResource(R.string.action_refresh), model::refreshSources, Modifier.testTag("discover-sources-refresh"), TvIcons.Refresh, compact = true)
        }
        val scraperName = s.sources.orEmpty().firstOrNull { it.installation.id == s.scraper }?.installation?.name ?: all
        PickerRow(stringResource(R.string.addon_ui_scraper), { choosing = true }, Modifier.fillMaxWidth().testTag("discover-scraper"), description = scraperName)
        val line = status(s)
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            line?.let { item { Text(it, Modifier.testTag("discover-sources-status"), style = Sohva.typography.label, color = Sohva.palette.textMuted) } }
            var firstCard = true
            for (provider in s.shown) {
                item(key = "p-${provider.installation.id}") {
                    Column {
                        Text(provider.installation.name, style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
                        when (val state = provider.state) {
                            ProviderState.Loading -> Text(stringResource(R.string.addon_loading), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
                            is ProviderState.Failed -> Text(failureText(state.failure), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
                            is ProviderState.Ready -> if (state.streams.isEmpty()) {
                                Text(stringResource(R.string.addon_ui_no_sources_returned), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
                            }
                        }
                    }
                }
                val streams = (provider.state as? ProviderState.Ready)?.streams.orEmpty()
                items(streams.withIndex().toList(), key = { "s-${provider.installation.id}-${it.index}" }) { (i, stream) ->
                    val isFirst = firstCard && i == 0
                    SourceCard(stream.name, stream.description, stream.kind == StreamKind.HTTP, { model.play(provider.installation, stream) },
                        (if (isFirst) Modifier.focusRequester(first) else Modifier).testTag("discover-source-${provider.installation.name}-$i"))
                }
                if (streams.isNotEmpty()) firstCard = false
            }
        }
    }
    if (choosing) {
        val providers = s.sources.orEmpty()
        ChooserDialog(
            stringResource(R.string.addon_ui_scraper), listOf(all) + providers.map { it.installation.name },
            providers.indexOfFirst { it.installation.id == s.scraper } + 1,
            { i -> model.chooseScraper(if (i == 0) null else providers[i - 1].installation.id) }, { choosing = false },
        )
    }
}

@Composable
private fun status(s: TitleState): String? = when {
    s.loading -> stringResource(R.string.addon_ui_loading_sources)
    s.noProviders -> stringResource(R.string.addon_ui_no_enabled_stream_addon_supports_this_title)
    s.noPlayable -> stringResource(R.string.addon_ui_no_playable_source_in_the_current_selection_try_another_scraper_or)
    s.pending != null -> stringResource(R.string.addon_ui_waiting_for_a_playable_source)
    s.sourcesLoading -> stringResource(R.string.addon_ui_finding_sources)
    s.sources == null -> stringResource(R.string.addon_ui_loading_sources)
    else -> null
}

/** A source card (FR-80, §5.4): the name, the description, then Play or Unsupported transport. */
@Composable
private fun SourceCard(name: String, description: String?, playable: Boolean, play: () -> Unit, modifier: Modifier) {
    val style = SurfaceStyle(corner = Sohva.shapes.medium, resting = Sohva.palette.surface, focusScale = 1f, padding = PaddingValues(14.dp))
    TvSurface({ if (playable) play() }, modifier.fillMaxWidth(), style = style) { colors ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = Sohva.typography.body.copy(fontWeight = FontWeight.SemiBold), color = colors.content)
            description?.let { Text(it, maxLines = 5, overflow = TextOverflow.Ellipsis, style = Sohva.typography.caption, color = colors.secondaryContent) }
            Text(
                stringResource(if (playable) R.string.player_play else R.string.addon_ui_unsupported_transport),
                style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold), color = colors.content,
            )
        }
    }
}
