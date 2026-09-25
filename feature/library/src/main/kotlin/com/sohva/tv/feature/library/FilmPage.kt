package com.sohva.tv.feature.library

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.core.data.vod.Progress
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SohvaTvBrand
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The film page (spec 40 §4.10, layout §3): backdrop, breadcrumb, title, facts, synopsis, progress,
 * the action row (first focus on Resume or Watch), then Versions, Cast and Similar. Metadata
 * replaces the provider's text where it has some. Back is the remote key; there is no on-screen Back.
 */
@Composable
fun FilmPage(model: FilmModel) = trace("Library:Film") {
    val page by model.page.collectAsStateWithLifecycle()
    val gone by model.gone.collectAsStateWithLifecycle()
    val metadata by model.metadata.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().testTag("screen-film")) {
        DetailsBackdrop(metadata?.backdropUrl)
        val state = page
        when {
            gone -> Text(
                stringResource(R.string.catalogue_source_disabled),
                Modifier.align(Alignment.Center),
                style = Sohva.typography.bodyLarge,
                color = Sohva.palette.textMuted,
            )
            state != null -> FilmColumn(model, state, metadata)
        }
    }
}

@Composable
private fun FilmColumn(model: FilmModel, page: FilmPageState, metadata: TitleMetadata?) {
    val progress by model.progress.collectAsStateWithLifecycle()
    val versions by model.versions.collectAsStateWithLifecycle()
    val similar by model.similar.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val record = page.record
    // VOD-FR-61: the metadata title, else the background match's, else the cleaned provider name.
    val title = metadata?.title ?: record.replacementTitle ?: page.cleanTitle
    val runtime = metadata?.runtimeMinutes?.let { runtime(it * 60_000L) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 32.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SohvaTvBrand(fontSize = 22.sp)
            Spacer(Modifier.width(24.dp))
            Breadcrumb(listOfNotNull(stringResource(R.string.catalogue_movies), page.breadcrumbGroup, title))
        }
        Spacer(Modifier.height(34.dp))
        Text(
            title,
            Modifier.fillMaxWidth(0.62f).testTag("details-title"),
            style = Sohva.typography.display.copy(fontWeight = FontWeight.Black),
            color = Sohva.palette.textPrimary,
            maxLines = 2,
        )
        FactsRow(
            metadata?.rating ?: record.rating,
            listOfNotNull((metadata?.year ?: record.year)?.toString(), runtime),
            page.quality,
            Modifier.padding(top = 12.dp),
        )
        Text(
            metadata?.overview ?: record.plot?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_details_available),
            Modifier.fillMaxWidth(0.62f).padding(top = 16.dp),
            style = Sohva.typography.body,
            color = Sohva.palette.textMuted,
            maxLines = 4,
        )
        ProgressLine(progress, Modifier.padding(top = 18.dp))
        FilmActions(model, progress, metadata?.sourceName, scroll)
        VersionsRow(versions, model::playVersion)
        CastRow(metadata?.cast.orEmpty())
        SimilarRow(similar, model::openFilm)
    }
}

/**
 * Resume or Watch (first focus), Start from beginning with a position, Mark as watched or
 * unwatched, "Source: …" with metadata, Wrong details? (VOD-FR-64…67). Focusing any of them scrolls the page to the top, one
 * frame after the platform's own bring-into-view (VOD-FR-72).
 */
@Composable
private fun FilmActions(model: FilmModel, progress: Progress?, source: String?, scroll: ScrollState) {
    val primary = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val toTop = Modifier.onFocusChanged { if (it.isFocused) scrollToTop(scope, scroll) }
    val resume = progress?.resumeMs?.takeIf { it > 0 }
    ActionRow {
        DetailsButton(
            stringResource(if (resume != null) R.string.details_resume else R.string.action_watch),
            model::watch,
            toTop.focusRequester(primary).testTag("details-watch"),
            icon = TvIcons.Play,
            primary = true,
        )
        if (resume != null) {
            DetailsButton(stringResource(R.string.details_restart), model::restart, toTop.testTag("details-restart"), icon = TvIcons.Replay)
        }
        DetailsButton(
            stringResource(if (progress?.completed == true) R.string.details_mark_unwatched else R.string.details_mark_watched),
            model::toggleWatched,
            toTop.testTag("details-mark"),
            icon = TvIcons.Check,
        )
        if (source != null) {
            DetailsButton(stringResource(R.string.metadata_source, source), model::openSource, toTop.testTag("details-source"), icon = TvIcons.Info)
        }
        DetailsButton(stringResource(R.string.match_picker_open), model::wrongDetails, toTop.testTag("details-wrong"), icon = TvIcons.Search)
    }
    LaunchedEffect(Unit) { primary.requestFocusWhenAttached() }
}

internal fun scrollToTop(scope: CoroutineScope, scroll: ScrollState) {
    scope.launch {
        withFrameNanos { }
        scroll.scrollTo(0)
    }
}
