package com.sohva.tv.feature.library

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * "Choose the right title" (spec 40 VOD-FR-104…106, spec 41 §5.2): a platform dialog over the
 * page. Focus starts on "Search"; the search has already started. When the picker is finished
 * (a choice, an undo, Close or Back) [onClosed] runs, which puts focus on "Wrong details?" before
 * the dialog goes.
 */
@Composable
internal fun MatchPickerDialog(picker: MatchPicker, onClosed: () -> Unit) {
    val finished by picker.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onClosed() }
    Dialog(onDismissRequest = picker::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val search = remember { FocusRequester() }
        Column(
            Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.86f)
                .roundFill(Sohva.palette.panel, Sohva.shapes.large)
                .padding(24.dp)
                .focusGroup()
                .testTag("match-picker"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.match_picker_title),
                style = Sohva.typography.title.copy(fontWeight = FontWeight.Bold),
                color = Sohva.palette.textPrimary,
            )
            Text(stringResource(R.string.match_picker_help), style = Sohva.typography.label, color = Sohva.palette.textDim)
            SearchRow(picker, search)
            Results(picker, Modifier.weight(1f))
            Footer(picker)
        }
        LaunchedEffect(Unit) { search.requestFocusWhenAttached() }
    }
}

@Composable
private fun SearchRow(picker: MatchPicker, search: FocusRequester) {
    val query by picker.query.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TvUrlField(
            query,
            picker::setQuery,
            stringResource(R.string.match_picker_query),
            Modifier.weight(1f).testTag("match-picker-query"),
            icon = TvIcons.Search,
            input = FieldInput(keyboard = KeyboardType.Text, compact = true),
        )
        TvActionButton(
            stringResource(R.string.match_picker_search),
            picker::search,
            Modifier.focusRequester(search).testTag("match-picker-search"),
            icon = TvIcons.Search,
            compact = true,
        )
    }
}

/** "Searching…", "Nothing came back…", or the rows keyed by external id. */
@Composable
private fun Results(picker: MatchPicker, modifier: Modifier) {
    val results by picker.results.collectAsStateWithLifecycle()
    val found = (results as? PickerResults.Found)?.results
    if (found == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (results == PickerResults.Searching) R.string.match_picker_searching else R.string.match_picker_no_results),
                Modifier.testTag("match-picker-note"),
                style = Sohva.typography.body,
                color = Sohva.palette.textDim,
            )
        }
        return
    }
    LazyColumn(modifier.fillMaxWidth().testTag("match-picker-results"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(found, key = { it.provider + ":" + it.externalId }) { ResultRow(it, picker) }
    }
}

@Composable
private fun ResultRow(result: MatchResult, picker: MatchPicker) {
    val style = SurfaceStyle(corner = Sohva.shapes.medium, focusScale = 1.04f, padding = PaddingValues(10.dp))
    TvSurface(
        onClick = { picker.choose(result) },
        modifier = Modifier.fillMaxWidth().testTag("match-result-${result.externalId}"),
        state = SurfaceState(),
        style = style,
    ) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(result.thumbUrl)
            Column(Modifier.padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        result.title,
                        Modifier.weight(1f, fill = false),
                        style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    result.year?.let {
                        Text(it.toString(), Modifier.padding(start = 10.dp), style = Sohva.typography.label, color = colors.content.copy(alpha = 0.7f))
                    }
                }
                result.overview?.let {
                    Text(
                        it,
                        Modifier.padding(top = 3.dp),
                        style = Sohva.typography.caption,
                        color = colors.content.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 44 × 62 dp on `surfaceRaised`, decoded at that size from TMDB's `w92` (spec 41 §5.2 rebuild). */
@Composable
private fun Thumbnail(url: String?) {
    val loader = LocalArtwork.current
    val density = LocalDensity.current
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url != null) image = loader.load(url, with(density) { 44.dp.roundToPx() }, with(density) { 62.dp.roundToPx() }, opaque = true)
    }
    val ground = Sohva.palette.surfaceRaised
    Box(
        Modifier.size(44.dp, 62.dp).clip(RoundedCornerShape(Sohva.shapes.small)).drawBehind {
            drawRect(ground)
            image?.let { drawCropped(it) }
        },
    )
}

@Composable
private fun Footer(picker: MatchPicker) {
    val pinned by picker.pinned.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (pinned) {
            TvActionButton(stringResource(R.string.match_picker_clear), picker::undo, Modifier.testTag("match-picker-clear"), icon = TvIcons.Replay)
        }
        TvActionButton(stringResource(R.string.match_picker_close), picker::close, Modifier.testTag("match-picker-close"), icon = TvIcons.Close)
    }
}
