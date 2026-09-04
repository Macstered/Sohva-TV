package com.streammate.tv.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.R
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.feature.common.SohvaTvBrand
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.CatalogueSearchType
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.repository.VodSeries
import com.streammate.tv.iptv.repository.VodMovie
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SearchResultType {
    CHANNEL,
    PROGRAMME,
    MOVIE,
    SERIES,
    EPISODE,
    SPORT,
}

private data class SearchResultItem(
    val key: String,
    val type: SearchResultType,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val channelId: String? = null,
    val contentKey: String? = null,
    val movie: VodMovie? = null,
    val series: VodSeries? = null,
)

@Composable
fun SearchScreen(
    guideRepository: GuideRepository,
    catalogueRepository: CatalogueRepository,
    sportsEvents: List<TodayEvent>,
    onPlayChannel: (String) -> Unit,
    onPlayVod: (String, Long) -> Unit,
    onOpenMovie: (VodMovie) -> Unit,
    onOpenSeries: (VodSeries) -> Unit,
    onSportMate: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        searchFocus.requestFocus()
    }

    LaunchedEffect(query, sportsEvents) {
        val term = query.trim()
        if (term.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(SEARCH_DEBOUNCE_MILLIS)
        val guideResults = guideRepository.search(term).map { item ->
            SearchResultItem(
                key = "${item.type}:${item.channelId}:${item.startEpochMillis ?: 0}",
                type = if (item.type == "channel") SearchResultType.CHANNEL else SearchResultType.PROGRAMME,
                title = item.title,
                subtitle = listOfNotNull(
                    item.subtitle,
                    item.startEpochMillis?.let(::formatSearchTime),
                ).joinToString(" · "),
                imageUrl = item.logoUrl,
                channelId = item.channelId,
            )
        }
        val catalogueResults = catalogueRepository.search(term).map { item ->
            SearchResultItem(
                key = "${item.type}:${item.sourceId}:${item.itemId}",
                type = when (item.type) {
                    CatalogueSearchType.MOVIE -> SearchResultType.MOVIE
                    CatalogueSearchType.SERIES -> SearchResultType.SERIES
                    CatalogueSearchType.EPISODE -> SearchResultType.EPISODE
                },
                title = item.title,
                subtitle = item.subtitle,
                imageUrl = item.posterUrl,
                contentKey = item.contentKey,
                movie = item.movie,
                series = item.series,
            )
        }
        val sportsResults = sportsEvents.filter { event ->
            event.home.contains(term, ignoreCase = true) ||
                event.away.contains(term, ignoreCase = true) ||
                event.competition.contains(term, ignoreCase = true)
        }.map { event ->
            SearchResultItem(
                key = "sport:${event.id}",
                type = SearchResultType.SPORT,
                title = "${event.home} – ${event.away}",
                subtitle = "${event.competition} · ${event.startLabel}",
                imageUrl = event.competitionLogoUrl,
            )
        }
        results = guideResults + catalogueResults + sportsResults
        searching = false
    }

    StreamMateScreenBackground { modifier ->
        Column(modifier = modifier) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SohvaTvBrand()
                    Column(Modifier.padding(start = 24.dp)) {
                        Text(stringResource(R.string.search_title), fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(
                            stringResource(R.string.search_subtitle),
                            color = palette.textMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
                TvActionButton(label = stringResource(R.string.action_back), icon = TvIcons.Back, onClick = onBack)
            }
            TvUrlField(
                value = query,
                onValueChange = { query = it.take(80) },
                label = stringResource(R.string.search_input_hint),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).focusRequester(searchFocus),
                testTag = "unified-search-field",
                leadingIcon = "⌕",
                keyboardType = KeyboardType.Text,
            )
            Text(
                text = when {
                    searching -> stringResource(R.string.search_loading)
                    query.trim().length < 2 -> stringResource(R.string.search_scope_hint)
                    results.isEmpty() -> stringResource(R.string.search_no_results)
                    else -> pluralStringResource(R.plurals.search_result_count, results.size, results.size)
                },
                color = palette.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 9.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(results, key = SearchResultItem::key) { item ->
                    SearchResultRow(
                        item = item,
                        onClick = {
                            when (item.type) {
                                SearchResultType.CHANNEL, SearchResultType.PROGRAMME ->
                                    item.channelId?.let(onPlayChannel)
                                SearchResultType.MOVIE -> item.movie?.let(onOpenMovie)
                                SearchResultType.EPISODE -> item.contentKey?.let { key ->
                                    scope.launch {
                                        val resume = catalogueRepository.progress(key)?.resumePositionMillis ?: 0L
                                        onPlayVod(key, resume)
                                    }
                                }
                                SearchResultType.SERIES -> item.series?.let(onOpenSeries)
                                SearchResultType.SPORT -> onSportMate()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: SearchResultItem, onClick: () -> Unit) {
    val palette = StreamMateThemeTokens.palette
    var focused by remember(item.key) { mutableStateOf(false) }
    val shape = StreamMateThemeTokens.shapes.small
    // The row is a focusable surface: it flips to the off-white fill and its
    // content inverts with it, rather than gaining an outline.
    val content = if (focused) palette.background else palette.textPrimary
    val secondary = if (focused) palette.background.copy(alpha = 0.62f) else palette.textMuted
    val marker = if (focused) palette.background.copy(alpha = 0.72f) else palette.focus
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .background(if (focused) palette.textPrimary else palette.surface)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(50.dp).background(palette.surfaceRaised, StreamMateThemeTokens.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            if (item.imageUrl.isNullOrBlank()) {
                Text(typeLabel(item.type).take(1), color = palette.focus, fontWeight = FontWeight.Black)
            } else {
                AsyncImage(item.imageUrl, null, Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.title, color = content, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            item.subtitle?.let {
                Text(it, color = secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(typeLabel(item.type), color = marker, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun typeLabel(type: SearchResultType): String = when (type) {
    SearchResultType.CHANNEL -> stringResource(R.string.search_type_channel)
    SearchResultType.PROGRAMME -> stringResource(R.string.search_type_programme)
    SearchResultType.MOVIE -> stringResource(R.string.search_type_movie)
    SearchResultType.SERIES -> stringResource(R.string.search_type_series)
    SearchResultType.EPISODE -> stringResource(R.string.search_type_episode)
    SearchResultType.SPORT -> stringResource(R.string.search_type_sport)
}

private fun formatSearchTime(epochMillis: Long): String = SEARCH_TIME_FORMAT.format(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
)

private const val SEARCH_DEBOUNCE_MILLIS = 250L
private val SEARCH_TIME_FORMAT = DateTimeFormatter.ofPattern("d.M. HH.mm")
