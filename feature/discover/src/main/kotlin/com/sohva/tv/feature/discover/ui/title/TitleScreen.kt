package com.sohva.tv.feature.discover.ui.title

import com.sohva.tv.ui.design.components.WatchedBadge
import com.sohva.tv.ui.design.components.ProgressBar
import com.sohva.tv.core.model.vod.TitleMark
import com.sohva.tv.feature.discover.ui.components.episodeMarkKey
import com.sohva.tv.feature.discover.ui.components.rememberMarks
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.text.Initials
import com.sohva.tv.feature.discover.protocol.AddonVideo
import com.sohva.tv.feature.discover.protocol.CastMember
import com.sohva.tv.feature.discover.ui.factsLine
import com.sohva.tv.feature.discover.ui.failureText
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * A Discover title (spec 50 §4.10, §5.3): the movie or episode page (details left, sources right) or
 * the series page (header, Library, seasons, episodes). [play] opens a playback by its token.
 */
@Composable
fun TitleScreen(model: TitleModel, back: () -> Unit, play: (String) -> Unit) {
    val s by model.state.collectAsStateWithLifecycle()
    val started by model.started.collectAsStateWithLifecycle()
    LaunchedEffect(started) {
        started?.let {
            model.consumed()
            play(it)
        }
    }
    BackHandler { if (!model.backToSeries()) back() }
    var shownBefore by remember { mutableStateOf(false) }
    LifecycleResumeEffect(model) {
        // Returning from the player: sources and progress again (FR-79).
        if (shownBefore) model.shownAgain()
        shownBefore = true
        onPauseOrDispose { }
    }
    Box(Modifier.fillMaxSize().testTag("discover-title")) {
        val background = (s.page as? TitlePage.Episode)?.video?.thumbnail ?: s.preview.background
        Backdrop(background)
        when (val page = s.page) {
            TitlePage.Series -> SeriesPage(model, s)
            else -> PlayablePage(model, s, (page as? TitlePage.Episode)?.video)
        }
    }
}

@Composable
private fun PlayablePage(model: TitleModel, s: TitleState, episode: AddonVideo?) {
    val primary = remember { FocusRequester() }
    val firstSource = remember { FocusRequester() }
    Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(0.56f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (episode != null) Text(s.preview.name, style = Sohva.typography.body, color = Sohva.palette.textMuted)
            Text(
                episode?.title ?: s.preview.name, Modifier.testTag("discover-title-name"), maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = Sohva.typography.display.copy(fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary,
            )
            if (episode != null) {
                val where = listOfNotNull(episode.season?.let { stringResource(R.string.addon_ui_season, it) }, episode.episode?.let { stringResource(R.string.addon_ui_episode, it) })
                Text(where.joinToString(" · "), style = Sohva.typography.body, color = Sohva.palette.textMuted)
            } else {
                Text(factsLine(s.preview), style = Sohva.typography.body, color = Sohva.palette.textMuted, maxLines = 2)
            }
            val synopsis = if (episode != null) episode.overview ?: stringResource(R.string.addon_ui_no_synopsis_supplied_by_the_addon) else s.preview.description
            synopsis?.let { Text(it, Modifier.padding(top = 4.dp), maxLines = 7, overflow = TextOverflow.Ellipsis, style = Sohva.typography.body, color = Sohva.palette.textMuted) }
            if (s.loading) Text(stringResource(R.string.addon_ui_loading_title_information), style = Sohva.typography.label, color = Sohva.palette.textDim)
            s.failure?.let { Text(stringResource(R.string.addon_ui_full_details_unavailable, failureText(it)), style = Sohva.typography.label, color = Sohva.palette.textDim) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val resume = s.canResume
                TvActionButton(
                    stringResource(if (resume) R.string.home_hero_resume else R.string.addon_find_sources),
                    { if (resume) model.start(fromBeginning = false) else firstSource.requestFocus() },
                    Modifier.focusRequester(primary).testTag("discover-title-primary"), TvIcons.Play, state = SurfaceState(enabled = !s.revoked),
                )
                if (episode == null) LibraryToggle(model, s)
            }
            if (s.progress != null) {
                TvActionButton(stringResource(R.string.home_resume_start_over), { model.start(fromBeginning = true) }, Modifier.testTag("discover-title-beginning"), TvIcons.Replay, compact = true)
            }
            if (s.libraryFull) Text(stringResource(R.string.addon_ui_library_is_full_1_000_titles_remove_a_title_first), style = Sohva.typography.label, color = Sohva.palette.danger)
            if (s.failure != null && !s.revoked) {
                TvActionButton(stringResource(if (episode != null) R.string.addon_ui_retry_episode_details else R.string.addon_ui_retry_details), model::retry, Modifier.testTag("discover-title-retry"), compact = true)
            }
            if (episode == null) CastRow(s.details?.cast.orEmpty())
        }
        SourcesColumn(model, s, firstSource, Modifier.weight(0.44f))
    }
    LaunchedEffect(episode?.id) { primary.requestFocusWhenAttached() }
}

@Composable
private fun LibraryToggle(model: TitleModel, s: TitleState) {
    val type = s.preview.type
    if (type != "movie" && type != "series") return
    if (s.libraryFailed) {
        TvActionButton(stringResource(R.string.addon_ui_retry_library), model::toggleLibrary, Modifier.testTag("discover-title-library"), compact = true)
        return
    }
    val saved = s.inLibrary == true
    TvActionButton(
        stringResource(if (saved) R.string.addon_ui_remove_from_library else R.string.addon_ui_add_to_library), model::toggleLibrary,
        Modifier.testTag("discover-title-library"), if (saved) TvIcons.Check else com.sohva.tv.ui.design.components.NavIcons.Library,
        state = SurfaceState(selected = saved, enabled = s.inLibrary != null, keepsFocus = true), compact = true,
    )
}

/** §5.3 movie cast: 92 dp columns, a 52 dp circle of initials under the photo, name and character; read-only but focusable. */
@Composable
private fun CastRow(cast: List<CastMember>) {
    if (cast.isEmpty()) return
    Text(stringResource(R.string.series_cast), Modifier.padding(top = 8.dp), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(cast, key = { it.name }) { person ->
            var focused by remember { mutableStateOf(false) }
            Column(
                Modifier.width(92.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .then(if (focused) Modifier.border(2.dp, Sohva.palette.focus, RoundedCornerShape(8.dp)) else Modifier)
                    .semantics { contentDescription = listOfNotNull(person.name, person.character).joinToString(" as ") }
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(52.dp).background(Sohva.palette.surface, CircleShape), contentAlignment = Alignment.Center) {
                    Text(Initials.of(person.name), style = Sohva.typography.label.copy(fontWeight = FontWeight.Black), color = Sohva.palette.textMuted)
                    person.photo?.let { Photo(it) }
                }
                Text(person.name, Modifier.padding(top = 8.dp), maxLines = 2, style = Sohva.typography.label.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center), color = Sohva.palette.textPrimary)
                person.character?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Sohva.typography.caption, color = Sohva.palette.textDim) }
            }
        }
    }
}

@Composable
private fun Photo(url: String) {
    val loader = LocalArtwork.current
    val px = with(LocalDensity.current) { 52.dp.roundToPx() }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { image = loader.load(url, px, px, opaque = true) }
    Box(
        Modifier.size(52.dp).drawWithCache {
            val clip = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) }
            onDrawBehind {
                val bitmap = image ?: return@onDrawBehind
                val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
                val sw = (size.width / scale).toInt()
                val sh = (size.height / scale).toInt()
                drawContext.canvas.save()
                drawContext.canvas.clipPath(clip)
                drawImage(bitmap, srcOffset = IntOffset((bitmap.width - sw) / 2, (bitmap.height - sh) / 2), srcSize = IntSize(sw, sh), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                drawContext.canvas.restore()
            }
        },
    )
}

/** §5.3 series page: header, cast names, Library and season chips, then the season's episode cards. */
@Composable
private fun SeriesPage(model: TitleModel, s: TitleState) {
    val chips = remember { HashMap<Int?, FocusRequester>() }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.42f), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)) {
            Text(s.preview.name, Modifier.testTag("discover-title-name"), maxLines = 2, overflow = TextOverflow.Ellipsis, style = Sohva.typography.display.copy(fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary)
            Text(factsLine(s.preview), maxLines = 2, style = Sohva.typography.body, color = Sohva.palette.textMuted)
            s.preview.description?.let { Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis, style = Sohva.typography.label, color = Sohva.palette.textMuted) }
            val names = s.details?.cast.orEmpty().map { it.name }
            if (names.isNotEmpty()) Text("${stringResource(R.string.series_cast)}: ${names.joinToString(", ")}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = Sohva.typography.label, color = Sohva.palette.textMuted)
        }
        if (s.loading) Text(stringResource(R.string.series_loading_episodes), style = Sohva.typography.label, color = Sohva.palette.textDim)
        s.failure?.let { Text(stringResource(R.string.addon_ui_full_details_unavailable, failureText(it)), style = Sohva.typography.label, color = Sohva.palette.textDim) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            item { LibraryToggle(model, s) }
            items(s.seasons, key = { it ?: -1 }) { season ->
                val label = when (season) {
                    null -> stringResource(R.string.series_episodes)
                    0 -> stringResource(R.string.addon_ui_specials)
                    else -> stringResource(R.string.addon_ui_season, season)
                }
                val chip = remember(season) { chips.getOrPut(season) { FocusRequester() } }
                TvActionButton(label, { model.chooseSeason(season) }, Modifier.focusRequester(chip).testTag("discover-season-${season ?: "none"}"),
                    compact = true, state = SurfaceState(selected = season == s.season))
            }
        }
        if (!s.loading && s.seasons.isEmpty()) {
            TvActionButton(stringResource(R.string.addon_ui_retry_episode_details), model::retry, Modifier.testTag("discover-title-retry"), compact = true)
        }
        val marks = rememberMarks(remember(s.episodes) { s.episodes.mapNotNull { episodeMarkKey(s.preview.id, it.season, it.episode) } })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
            items(s.episodes, key = { it.id }) { video ->
                EpisodeCard(video, s.preview.background, episodeMarkKey(s.preview.id, video.season, video.episode)?.let(marks::get)) { model.openEpisode(video) }
            }
        }
    }
    LaunchedEffect(s.season, s.seasons.size) { chips[s.season]?.requestFocusWhenAttached() }
}

/** §5.3 episode card: 230 dp, a 130 dp image (the thumbnail, else the series background) under a scrim, "S1 · E2 · title". */
@Composable
private fun EpisodeCard(video: AddonVideo, fallback: String?, mark: TitleMark?, open: () -> Unit) {
    val style = SurfaceStyle(corner = Sohva.shapes.medium, resting = Sohva.palette.surface, focusRing = true, focusScale = 1f, padding = PaddingValues(0.dp))
    val label = listOfNotNull(video.season?.let { "S$it" }, video.episode?.let { "E$it" }, video.title).joinToString(" · ")
    TvSurface(open, Modifier.width(230.dp).height(130.dp).testTag("discover-episode-${video.id}"), style = style) {
        Box(Modifier.fillMaxSize()) {
            (video.thumbnail ?: fallback)?.let { Still(it) }
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)))) })
            Text(label, Modifier.align(Alignment.BottomStart).padding(10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = Sohva.typography.label, color = androidx.compose.ui.graphics.Color.White)
            // The Trakt bar or tick (spec 51 section 5.2).
            mark?.fraction?.let { f -> ProgressBar({ f }, Modifier.align(Alignment.BottomStart).testTag("discover-progress"), height = 4.dp, track = Sohva.palette.background.copy(alpha = 0f)) }
            if (mark != null && mark.watched && mark.fraction == null) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp).testTag("discover-watched"), size = 22.dp)
        }
    }
}

@Composable
private fun Still(url: String) {
    val loader = LocalArtwork.current
    val density = LocalDensity.current
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { image = loader.load(url, with(density) { 230.dp.roundToPx() }, with(density) { 130.dp.roundToPx() }, opaque = true) }
    Box(Modifier.fillMaxSize().drawBehind { image?.let { cropped(it) } })
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.cropped(bitmap: ImageBitmap) {
    val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
    val sw = (size.width / scale).toInt()
    val sh = (size.height / scale).toInt()
    drawImage(bitmap, srcOffset = IntOffset((bitmap.width - sw) / 2, (bitmap.height - sh) / 2), srcSize = IntSize(sw, sh), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
}

/** The title's backdrop (§5.3 as §5.1): 960 × 540 at most, RGB_565, under one cached scrim. */
@Composable
private fun Backdrop(url: String?) {
    val loader = LocalArtwork.current
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { image = url?.let { loader.load(it, 960, 540, opaque = true) } }
    val ground = Sohva.palette.background
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val across = Brush.horizontalGradient(0f to ground.copy(alpha = 0.98f), 0.5f to ground.copy(alpha = 0.75f), 1f to ground.copy(alpha = 0.20f))
            val down = Brush.verticalGradient(0f to ground.copy(alpha = 0f), 0.6f to ground.copy(alpha = 0.35f), 1f to ground)
            onDrawBehind {
                drawRect(ground)
                image?.let { cropped(it) }
                drawRect(across)
                drawRect(down)
            }
        },
    )
}
