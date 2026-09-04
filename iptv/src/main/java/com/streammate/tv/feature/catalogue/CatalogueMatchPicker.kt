package com.streammate.tv.feature.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.iptv.R
import com.streammate.tv.iptv.metadata.MetadataLookup
import com.streammate.tv.iptv.metadata.MetadataSearchResult
import kotlinx.coroutines.launch

/**
 * Choosing what a title is, when the matcher would not.
 *
 * It refuses anything it is not sure of - two films of the same name, or a
 * year that disagrees with the provider's - and refusing is the right thing
 * for a machine to do. A person looking at the posters and the years settles
 * it at a glance, which is all this is for.
 *
 * The query starts from the name the provider gave, so the common case is one
 * press rather than typing a title on a remote.
 */
@Composable
fun CatalogueMatchPicker(
    initialQuery: String,
    lookup: MetadataLookup,
    pinned: Boolean,
    onSearch: suspend (String) -> List<MetadataSearchResult>,
    onChoose: suspend (MetadataSearchResult) -> Unit,
    onClear: suspend () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<MetadataSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    val runSearch: () -> Unit = {
        scope.launch {
            searching = true
            results = onSearch(query.trim())
            searching = false
            searched = true
        }
    }
    // The name the provider gave is usually the right question, so it is asked
    // on the way in rather than waiting for anyone to press anything.
    LaunchedEffect(Unit) {
        runSearch()
        searchFocus.requestFocusWhenAttached()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.86f)
                .clip(StreamMateThemeTokens.shapes.large)
                .background(palette.panel)
                .padding(24.dp)
                .focusGroup()
                .testTag("match-picker"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.match_picker_title),
                color = palette.textPrimary,
                fontSize = typography.title.fontSize,
                lineHeight = typography.title.lineHeight,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.match_picker_help),
                color = palette.textDim,
                fontSize = typography.label.fontSize,
                lineHeight = typography.label.lineHeight,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvUrlField(
                    value = query,
                    onValueChange = { query = it.take(MAX_MATCH_QUERY_LENGTH) },
                    label = stringResource(R.string.match_picker_query),
                    modifier = Modifier.weight(1f),
                    testTag = "match-picker-query",
                    leadingIconRes = TvIcons.Search,
                    keyboardType = KeyboardType.Text,
                    editOnClickOnly = true,
                    compact = true,
                )
                TvActionButton(
                    label = stringResource(R.string.match_picker_search),
                    icon = TvIcons.Search,
                    onClick = runSearch,
                    compact = true,
                    focusRequester = searchFocus,
                    testTag = "match-picker-search",
                )
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    searching -> PickerNote(stringResource(R.string.match_picker_searching))
                    results.isEmpty() && searched -> PickerNote(
                        stringResource(R.string.match_picker_no_results),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("match-picker-results"),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(results, key = { it.externalId }) { result ->
                            MatchResultRow(
                                result = result,
                                onClick = { scope.launch { onChoose(result) }.invokeOnCompletion { onDismiss() } },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Only where there is a choice to undo. On a title the matcher
                // found on its own there is nothing to let go of.
                if (pinned) {
                    TvActionButton(
                        label = stringResource(R.string.match_picker_clear),
                        icon = TvIcons.Replay,
                        onClick = {
                            scope.launch { onClear() }.invokeOnCompletion { onDismiss() }
                        },
                        compact = true,
                        testTag = "match-picker-clear",
                    )
                }
                TvActionButton(
                    label = stringResource(R.string.match_picker_close),
                    icon = TvIcons.Close,
                    onClick = onDismiss,
                    compact = true,
                    testTag = "match-picker-close",
                )
            }
        }
    }
}

@Composable
private fun MatchResultRow(result: MetadataSearchResult, onClick: () -> Unit) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = StreamMateThemeTokens.shapes.medium,
        contentPadding = PaddingValues(10.dp),
        testTag = "match-result-" + result.externalId,
    ) { colors ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 62.dp)
                    .clip(StreamMateThemeTokens.shapes.small)
                    .background(palette.surfaceRaised),
            ) {
                result.posterUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.title,
                        color = colors.content,
                        fontSize = typography.bodyLarge.fontSize,
                        lineHeight = typography.bodyLarge.lineHeight,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // The year is what separates a remake from what it remade,
                    // so it sits beside the name rather than under it.
                    result.year?.let { year ->
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = year.toString(),
                            color = colors.content.copy(alpha = 0.7f),
                            fontSize = typography.label.fontSize,
                            lineHeight = typography.label.lineHeight,
                        )
                    }
                }
                result.overview?.takeIf(String::isNotBlank)?.let { overview ->
                    Text(
                        text = overview,
                        modifier = Modifier.padding(top = 3.dp),
                        color = colors.content.copy(alpha = 0.7f),
                        fontSize = typography.caption.fontSize,
                        lineHeight = typography.caption.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerNote(text: String) {
    val palette = StreamMateThemeTokens.palette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = palette.textDim,
            fontSize = StreamMateThemeTokens.typography.body.fontSize,
            modifier = Modifier.testTag("match-picker-note"),
        )
    }
}

/** A search box on a remote; longer than this is nobody's real query. */
private const val MAX_MATCH_QUERY_LENGTH = 80
