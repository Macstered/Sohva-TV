package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.sohva.tv.addons.*
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.feature.common.requestFocusWhenAttached
import kotlinx.coroutines.CancellationException

@Composable
internal fun AddonSourcesScreen(host: AddonHost, profileId: String, video: AddonMediaKey, title: String, onBack: () -> Unit,
    modifier: Modifier, identity: AddonWatchIdentity) {
    val labels = addonStrings()
    var playing by remember { mutableStateOf<Pair<AddonPlaybackSelection, Boolean>?>(null) }
    playing?.let { (selection, resume) ->
        AddonPlayerScreen(host, profileId, identity, title, selection, resume, { playing = null }, modifier); return
    }
    BackHandler(onBack = onBack)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TvActionButton(labels(R.string.addon_back_details), onBack)
        Text(title)
        AddonSourceCards(host, profileId, video, identity, { selection, resume -> playing = selection to resume }, Modifier.weight(1f))
    }
}

/** Recreated on return from playback so signed URLs are always resolved afresh. */
@Composable
internal fun AddonSourceCards(host: AddonHost, profileId: String, video: AddonMediaKey, identity: AddonWatchIdentity,
    onPlay: (AddonPlaybackSelection, Boolean) -> Unit, modifier: Modifier, requestFocus: Boolean = true,
    focusRequester: FocusRequester? = null, resume: Boolean = true, autoPlayRequested: Boolean = false,
    onAutoPlayUnavailable: () -> Unit = {}) {
    val labels = addonStrings()
    var streams by remember(video) { mutableStateOf<Map<String, AddonSourceResult<AddonStream>>>(emptyMap()) }
    var failure by remember(video) { mutableStateOf<AddonFailure?>(null) }
    var retry by remember(video) { mutableStateOf(0) }
    var loading by remember(video) { mutableStateOf(true) }
    var providerFilter by remember(video) { mutableStateOf("") }
    val fallbackFocus = remember { FocusRequester() }
    val topFocus = focusRequester ?: fallbackFocus
    LaunchedEffect(video, retry) {
        streams = emptyMap(); failure = null; loading = true; providerFilter = ""
        try {
            host.sources.streams(profileId, video).collect { streams = streams + (it.installationId to it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: AddonException) { failure = error.failure }
        finally { loading = false }
    }
    LaunchedEffect(video) { if (requestFocus) topFocus.requestFocusWhenAttached() }
    LaunchedEffect(autoPlayRequested, loading, providerFilter) {
        if (autoPlayRequested && !loading) {
            // Wait for resolution to finish so response speed does not change provider priority.
            // Never retain signed URLs in watch history or silently retry a different stream.
            val chosen = firstPlayableSource(streams.values, video, providerFilter)
            if (chosen != null) onPlay(chosen, resume) else onAutoPlayUnavailable()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(labels(com.streammate.tv.iptv.R.string.settings_overline_sources))
            TvActionButton(labels(com.streammate.tv.iptv.R.string.action_refresh), { if (!loading) retry++ }, focusRequester = topFocus, compact = true,
                icon = com.streammate.tv.feature.common.TvIcons.Refresh, testTag = "addon-refresh-sources")
        }
        AddonChoice(labels(R.string.addon_ui_scraper), providerFilter, listOf("" to labels(com.streammate.tv.iptv.R.string.catalogue_all)) + streams.values.sortedBy { it.position }.map { it.installationId to it.providerName },
            { providerFilter = it }, Modifier.fillMaxWidth(), tag = "Scraper")
    LazyColumn(Modifier.weight(1f).testTag("addon-sources"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            if (loading) Text(labels(R.string.addon_ui_finding_sources))
            failure?.let { Text(androidx.compose.ui.res.stringResource(it.messageResource())) }
            if (!loading && streams.isEmpty()) Text(labels(R.string.addon_ui_no_enabled_stream_addon_supports_this_title))
        }
        streams.values.sortedBy { it.position }.filter { providerFilter.isEmpty() || it.installationId == providerFilter }.forEach { provider ->
            item(key = "provider-${provider.installationId}") {
                Text(provider.providerName)
                if (provider.status == AddonSourceStatus.LOADING) Text(labels(R.string.addon_loading))
                provider.failure?.let { Text(androidx.compose.ui.res.stringResource(it.messageResource())) }
                if (provider.status == AddonSourceStatus.READY && provider.items.isEmpty()) Text(labels(R.string.addon_ui_no_sources_returned))
            }
            items(provider.items.indices.toList(), key = { "${provider.installationId}-$it" }) { index ->
                val stream = provider.items[index]
                Column(Modifier.testTag("addon-source-item")) {
                TvSurface(onClick = { if (stream.kind == AddonStreamKind.HTTP) onPlay(AddonPlaybackSelection(provider.installationId, provider.revision, video, stream), resume) },
                    resting = com.streammate.tv.app.StreamMateThemeTokens.palette.surface,
                    restingContent = com.streammate.tv.app.StreamMateThemeTokens.palette.textPrimary,
                    modifier = Modifier.fillMaxWidth(), testTag = if (stream.kind == AddonStreamKind.HTTP) "addon-play-source" else "addon-unsupported-source") { colors ->
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stream.name, color = colors.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        stream.description?.let { Text(it, color = colors.content, maxLines = 5, overflow = TextOverflow.Ellipsis) }
                        Text(if (stream.kind == AddonStreamKind.HTTP) labels(com.streammate.tv.iptv.R.string.player_play) else labels(R.string.addon_ui_unsupported_transport), color = colors.content)
                    }
                }
                }
            }
        }
    }
    }
}

/** Same priority and filter as the visible source list; never select unsupported transports. */
internal fun firstPlayableSource(results: Collection<AddonSourceResult<AddonStream>>, video: AddonMediaKey, providerFilter: String): AddonPlaybackSelection? =
    results.sortedBy { it.position }.filter { it.status == AddonSourceStatus.READY && (providerFilter.isEmpty() || it.installationId == providerFilter) }
        .firstNotNullOfOrNull { provider -> provider.items.firstOrNull { it.kind == AddonStreamKind.HTTP }
            ?.let { AddonPlaybackSelection(provider.installationId, provider.revision, video, it) } }
