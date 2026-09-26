package com.sohva.tv.feature.search

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LogoTile
import com.sohva.tv.ui.design.components.SohvaTvBrand
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Search (spec 03 §5): header with Back, the field (focused on entry), the status line and the
 * results, appended group by group so rows on screen never move. Back pops through the window.
 */
@Composable
fun SearchScreen(model: SearchModel) = trace("Search:Screen") {
    val text by model.text.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val field = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    ScreenBackground(Modifier.fillMaxSize().testTag("screen-search")) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
            Header(model, back)
            Spacer(Modifier.height(18.dp))
            SearchField(text, model::setText, field, back)
            StatusLine(state)
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).testTag("search-results"),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(state.results, key = { it.key }) { result -> ResultRow(result) { model.open(result) } }
            }
        }
    }
    LaunchedEffect(Unit) { field.requestFocusWhenAttached() }
}

@Composable
private fun Header(model: SearchModel, back: FocusRequester) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SohvaTvBrand(fontSize = 34.sp)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.search_title), style = Sohva.typography.display.copy(fontSize = 32.sp, fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary, maxLines = 1)
            Text(stringResource(R.string.search_subtitle), style = Sohva.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal), color = Sohva.palette.textMuted, maxLines = 1)
        }
        TvActionButton(stringResource(R.string.action_back), model::leave, Modifier.focusRequester(back).testTag("search-back"), icon = TvIcons.Back)
    }
}

/**
 * The field (SEARCH-FR-01, -53): typing searches as it goes, so the text is edited in place, not in
 * the design system's edit dialog, which would cover the results. A 2 dp `focus` frame shows focus;
 * Up goes to Back, Down to the results.
 */
@Composable
private fun SearchField(text: String, onChange: (String) -> Unit, field: FocusRequester, back: FocusRequester) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hint = stringResource(R.string.search_input_hint)
    val shape = RoundedCornerShape(Sohva.shapes.medium)
    BasicTextField(
        value = text,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(field)
            // The text field takes Up and Down as cursor moves; here they leave it (SEARCH-FR-53).
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> back.requestFocus().let { true }
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) Sohva.palette.focus else Color.Transparent, shape)
            .semantics { contentDescription = hint }
            .testTag("unified-search-field"),
        textStyle = Sohva.typography.body.copy(color = Sohva.palette.textPrimary),
        cursorBrush = SolidColor(Sohva.palette.focus),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌕", style = Sohva.typography.bodyLarge, color = Sohva.palette.textMuted)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text(hint, style = Sohva.typography.body, color = Sohva.palette.textMuted, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

/** SEARCH-FR-40: one line under the field. */
@Composable
private fun StatusLine(state: SearchState) {
    val line = when (state.status) {
        SearchStatus.SEARCHING -> stringResource(R.string.search_loading)
        SearchStatus.HINT -> stringResource(R.string.search_scope_hint)
        SearchStatus.FAILED -> stringResource(R.string.search_failed)
        SearchStatus.NONE -> stringResource(R.string.search_no_results)
        SearchStatus.COUNT -> pluralStringResource(R.plurals.search_result_count, state.results.size, state.results.size)
    }
    Text(line, Modifier.padding(vertical = 9.dp).testTag("search-status"), style = Sohva.typography.caption, color = Sohva.palette.textMuted, maxLines = 1)
}

/** A result (SEARCH-FR-20, -51): 68 dp, `surface` at rest, the focus flip, no scale. */
@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    val style = SurfaceStyle(corner = Sohva.shapes.small, resting = Sohva.palette.surface, focusScale = 1f, padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp))
    val label = stringResource(kindLabel(result.kind))
    TvSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(68.dp).testTag("search-result-${result.key}"), style = style) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            // No picture: the kind label's first letter (SEARCH-FR-21).
            LogoTile(if (result.image.isNullOrBlank()) label.take(1) else result.title, result.image, 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(result.title, style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold), color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    result.subtitle, style = Sohva.typography.caption,
                    color = if (colors.focused) colors.content.copy(alpha = 0.62f) else Sohva.palette.textMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                label, style = Sohva.typography.caption.copy(fontWeight = FontWeight.Black),
                color = if (colors.focused) colors.content.copy(alpha = 0.72f) else Sohva.palette.focus,
            )
        }
    }
}

private fun kindLabel(kind: ResultKind): Int = when (kind) {
    ResultKind.SPORT -> R.string.search_type_sport
    ResultKind.CHANNEL -> R.string.search_type_channel
    ResultKind.PROGRAMME -> R.string.search_type_programme
    ResultKind.MOVIE -> R.string.search_type_movie
    ResultKind.SERIES -> R.string.search_type_series
    ResultKind.EPISODE -> R.string.search_type_episode
}
