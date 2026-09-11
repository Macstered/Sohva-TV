package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.sohva.tv.addons.*
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.feature.common.requestFocusWhenAttached
import kotlinx.coroutines.CancellationException

@Composable
internal fun AddonDetailsScreen(host: AddonHost, profileId: String, installation: InstalledAddon, preview: AddonMedia,
    onBack: () -> Unit, modifier: Modifier, initialVideo: AddonMediaKey? = null) {
    val labels = addonStrings()
    var details by remember { mutableStateOf(preview) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<AddonFailure?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var episode by remember { mutableStateOf<AddonVideo?>(null) }
    var restoreEpisode by remember { mutableStateOf<String?>(null) }
    var initialHandled by remember { mutableStateOf(false) }
    var season by remember { mutableStateOf<Int?>(null) }
    var seasonChosen by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LaunchedEffect(retry) {
        loading = true
        try {
            details = host.browser.details(profileId, installation.installationId, preview.key, refresh = retry > 0).value
            failure = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: AddonException) { failure = error.failure }
        finally { loading = false }
        loaded = failure !in setOf(AddonFailure.ACCESS_DENIED, AddonFailure.CONFLICT, AddonFailure.NOT_FOUND)
        if (loaded && !initialHandled && initialVideo != null && details.singleVideoKey() == null) {
            episode = details.videos.firstOrNull { it.id == initialVideo.id }
                ?: AddonVideo(initialVideo.id, preview.name, null, null, null)
        }
        initialHandled = true
    }
    val single = details.singleVideoKey()
    if (single != null) {
        AddonPlayableDetails(host, profileId, installation, preview, details, if (loaded) single else null,
            details.name, details.description ?: preview.description, loading, failure, { retry++ }, onBack, modifier)
        return
    }
    episode?.let { selected ->
        key(selected.id) {
            AddonPlayableDetails(host, profileId, installation, preview, details, AddonMediaKey(details.key.type, selected.id),
                selected.title, selected.overview, false, null, { retry++ },
                { restoreEpisode = selected.id; episode = null }, modifier, episode = selected)
        }
        return
    }
    BackHandler(onBack = onBack)
    val seasons = details.videos.map { it.season }.distinct().sortedWith(compareBy<Int?> { if (it == 0) Int.MAX_VALUE else it ?: Int.MAX_VALUE - 1 })
    val chosenSeason = if (seasonChosen && season in seasons) season else seasons.firstOrNull()
    val visible = details.videos.filter { it.season == chosenSeason }
    val seasonFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    LaunchedEffect(loaded, restoreEpisode) {
        if (restoreEpisode != null) {
            val target = details.videos.firstOrNull { it.id == restoreEpisode }
            season = target?.season
            seasonChosen = true
            scroll.scrollToItem(details.videos.filter { it.season == target?.season }.indexOfFirst { it.id == restoreEpisode }.coerceAtLeast(0))
            episodeFocus.requestFocusWhenAttached()
        } else if (loaded) seasonFocus.requestFocusWhenAttached()
    }
    BoxWithConstraints(modifier.fillMaxSize().testTag("addon-series-details")) {
        AddonBackdrop(details.background ?: preview.background, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.height(this@BoxWithConstraints.maxHeight * .42f).fillMaxWidth(.72f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(details.name, Modifier.testTag("addon-details-loaded"), fontSize = StreamMateThemeTokens.typography.display.fontSize,
                    lineHeight = StreamMateThemeTokens.typography.display.lineHeight, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                AddonFacts(details)
                details.description?.let { Text(it, Modifier.weight(1f, fill = false), fontSize = StreamMateThemeTokens.typography.label.fontSize,
                    lineHeight = StreamMateThemeTokens.typography.label.lineHeight, color = StreamMateThemeTokens.palette.textMuted, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                AddonCastNames(details.cast)
            }
            if (loading) Text(labels(com.streammate.tv.iptv.R.string.series_loading_episodes))
            failure?.let { Text(androidx.compose.ui.res.stringResource(it.messageResource())) }
            if (seasons.isEmpty() && !loading) TvActionButton(labels(R.string.addon_ui_retry_episode_details), { retry++ }, focusRequester = seasonFocus)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AddonLibraryButton(host, profileId, installation, preview, details)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                    items(seasons) { option ->
                        TvActionButton(if (option == 0) labels(R.string.addon_ui_specials) else option?.let { labels(R.string.addon_ui_season, it) } ?: labels(com.streammate.tv.iptv.R.string.series_episodes), { season = option; seasonChosen = true; restoreEpisode = null },
                            selected = chosenSeason == option, compact = true, focusRequester = if (chosenSeason == option) seasonFocus else null)
                    }
                }
            }
            LazyRow(state = scroll, contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("addon-episodes")) {
                items(visible, key = { it.id }) { item ->
                    TvSurface({ restoreEpisode = item.id; episode = item }, Modifier.width(230.dp), focusRing = true, focusScale = 1f,
                        focusRequester = if (restoreEpisode == item.id) episodeFocus else null, testTag = "addon-episode-card") { colors ->
                        Column {
                            Box(Modifier.fillMaxWidth().height(130.dp)) {
                                AsyncImage(item.thumbnail ?: details.background, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))
                                Text(episodeLabel(item, labels), Modifier.align(Alignment.BottomStart).padding(10.dp), color = colors.content,
                                    fontSize = StreamMateThemeTokens.typography.label.fontSize, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun episodeLabel(video: AddonVideo, labels: AddonStrings) = listOfNotNull(video.season?.let { labels(R.string.addon_ui_season_short, it) }, video.episode?.let { labels(R.string.addon_ui_episode_short, it) }, video.title).joinToString(" · ")

@Composable
private fun AddonPlayableDetails(host: AddonHost, profile: String, installation: InstalledAddon, preview: AddonMedia, details: AddonMedia,
    video: AddonMediaKey?, title: String, synopsis: String?, loading: Boolean, failure: AddonFailure?, refreshDetails: () -> Unit,
    onBack: () -> Unit, modifier: Modifier, episode: AddonVideo? = null) {
    val labels = addonStrings()
    var playing by remember { mutableStateOf<AddonPlaybackSelection?>(null) }
    var resume by remember(video) { mutableStateOf(true) }
    var saved by remember(video) { mutableStateOf<AddonWatchProgress?>(null) }
    var autoPlayRequested by remember(video) { mutableStateOf(false) }
    var playMessage by remember(video) { mutableStateOf<String?>(null) }
    val primaryFocus = remember { FocusRequester() }
    val sourcesFocus = remember { FocusRequester() }
    val identity = video?.let { AddonWatchIdentity(installation.installationId, preview.key, it) }
    LaunchedEffect(video, playing) {
        if (playing == null && identity != null) {
            host.pendingProgressWrite?.join()
            saved = try { host.progress.get(profile, identity) } catch (_: AddonException) { null }
            primaryFocus.requestFocusWhenAttached()
        }
    }
    playing?.let { selection ->
        AddonPlayerScreen(host, profile, checkNotNull(identity), if (episode == null) title else "${details.name} · ${episodeLabel(episode, labels)}",
            selection, resume, { playing = null }, modifier,
            artwork = AddonWatchArtwork.from(details, preview), startupLogo = details.logo ?: preview.logo); return
    }
    BackHandler(onBack = onBack)
    Box(modifier.fillMaxSize().testTag(if (episode == null) "addon-movie-details" else "addon-episode-details")) {
        AddonBackdrop(episode?.thumbnail ?: details.background ?: preview.background, Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            LazyColumn(Modifier.weight(.56f).fillMaxHeight().testTag("addon-details-overview"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    if (episode != null) Text(details.name, color = StreamMateThemeTokens.palette.textMuted, fontSize = StreamMateThemeTokens.typography.body.fontSize)
                    Text(title, Modifier.fillMaxWidth().testTag("addon-details-loaded"), fontSize = StreamMateThemeTokens.typography.display.fontSize,
                        lineHeight = StreamMateThemeTokens.typography.display.lineHeight, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (episode == null) AddonFacts(details, Modifier.padding(top = 12.dp))
                    else Text(listOfNotNull(episode.season?.let { labels(R.string.addon_ui_season, it) }, episode.episode?.let { labels(R.string.addon_ui_episode, it) }).joinToString(" · "),
                        Modifier.padding(top = 12.dp), color = StreamMateThemeTokens.palette.textMuted)
                    Text(synopsis ?: labels(R.string.addon_ui_no_synopsis_supplied_by_the_addon), Modifier.padding(top = 16.dp),
                        fontSize = StreamMateThemeTokens.typography.body.fontSize, lineHeight = StreamMateThemeTokens.typography.body.lineHeight,
                        color = StreamMateThemeTokens.palette.textMuted, maxLines = 7, overflow = TextOverflow.Ellipsis)
                }
                item {
                    if (loading) Text(labels(R.string.addon_ui_loading_title_information))
                    failure?.let { Text(labels(R.string.addon_ui_full_details_unavailable, labels(it.messageResource()))) }
                    if (video != null) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TvActionButton(if (saved?.resumePositionMillis?.let { it > 0 } == true) labels(R.string.home_continue_watching) else labels(R.string.addon_find_sources),
                                { resume = true; playMessage = null
                                    if (saved?.resumePositionMillis?.let { it > 0 } == true) autoPlayRequested = true
                                    else runCatching { sourcesFocus.requestFocus() }
                                }, icon = TvIcons.Play, enabled = !autoPlayRequested,
                                focusRequester = primaryFocus, testTag = "addon-find-sources")
                            if (episode == null) AddonLibraryButton(host, profile, installation, preview, details)
                        }
                        if (saved?.resumePositionMillis?.let { it > 0 } == true) TvActionButton(labels(R.string.home_resume_start_over),
                            { resume = false; playMessage = null; autoPlayRequested = true }, icon = TvIcons.Replay,
                            enabled = !autoPlayRequested, compact = true, testTag = "addon-start-over")
                        if (autoPlayRequested) Text(labels(R.string.addon_ui_waiting_for_a_playable_source))
                        playMessage?.let { Text(it) }
                    }
                    if (video == null && episode == null) AddonLibraryButton(host, profile, installation, preview, details)
                    if (failure != null) TvActionButton(labels(R.string.addon_ui_retry_details), refreshDetails, compact = true)
                }
                if (episode == null && details.cast.isNotEmpty()) item {
                    if (details.key.type == "series") AddonCastNames(details.cast) else AddonMovieCast(details.cast)
                }
            }
            if (video != null && identity != null) key(video) {
                AddonSourceCards(host, profile, video, identity, { selection, shouldResume -> autoPlayRequested = false; resume = shouldResume; playing = selection }, Modifier.weight(.44f).fillMaxHeight(),
                    requestFocus = false, focusRequester = sourcesFocus, resume = resume, autoPlayRequested = autoPlayRequested,
                    onAutoPlayUnavailable = { autoPlayRequested = false; playMessage = labels(R.string.addon_ui_no_playable_source_in_the_current_selection_try_another_scraper_or) })
            } else Text(labels(R.string.addon_ui_loading_sources), Modifier.weight(.44f))
        }
    }
}
